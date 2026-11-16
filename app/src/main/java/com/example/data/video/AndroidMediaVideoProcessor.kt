package com.example.data.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.graphics.scale
import com.example.domain.video.ClipExportResult
import com.example.domain.video.LocalVideoInfo
import com.example.domain.video.VideoProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

class AndroidMediaVideoProcessor(
    private val context: Context
) : VideoProcessor {

    override val capabilitiesLabel: String =
        "Android MediaExtractor + MediaMuxer (On-Device Local Processing)"

    override suspend fun inspectLocalVideo(uri: Uri, maxKeyframes: Int): Result<LocalVideoInfo> =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 0L
                val rawWidth = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                )?.toIntOrNull() ?: 1080
                val rawHeight = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                )?.toIntOrNull() ?: 1920
                val rotation = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                )?.toIntOrNull() ?: 0

                val (width, height) = if (rotation == 90 || rotation == 270) {
                    rawHeight to rawWidth
                } else {
                    rawWidth to rawHeight
                }

                val frames = mutableListOf<Bitmap>()
                if (durationMs > 0L && maxKeyframes > 0) {
                    val count = maxKeyframes.coerceIn(1, 8)
                    val stepUs = (durationMs * 1000L) / (count + 1)
                    for (i in 1..count) {
                        val timeUs = stepUs * i
                        val frame = retriever.getFrameAtTime(
                            timeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                        if (frame != null) {
                            frames.add(scaleBitmapForPreviewAndAi(frame, maxDim = 512))
                        }
                    }
                }

                Result.success(
                    LocalVideoInfo(
                        uri = uri,
                        displayName = resolveDisplayName(uri),
                        durationMs = durationMs,
                        width = width,
                        height = height,
                        keyframes = frames
                    )
                )
            } catch (e: Exception) {
                Result.failure(
                    IllegalStateException("Unable to read local video metadata: ${e.localizedMessage}", e)
                )
            } finally {
                runCatching { retriever.release() }
            }
        }

    override suspend fun trimLocalClip(
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        aspectRatio: String,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Result<ClipExportResult> = withContext(Dispatchers.IO) {
        require(endMs > startMs) { "Clip end time must be greater than start time." }
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, sourceUri, null)
            val trackCount = extractor.trackCount
            if (trackCount == 0) {
                return@withContext Result.failure(IllegalStateException("No media tracks found in selected file."))
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackIndexMap = mutableMapOf<Int, Int>()
            var maxBufferSize = 1024 * 1024

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val dstIndex = muxer.addTrack(format)
                    trackIndexMap[i] = dstIndex
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        val trackBuf = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        if (trackBuf > maxBufferSize) maxBufferSize = trackBuf
                    }
                }
            }

            if (trackIndexMap.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Unsupported media format for MP4 clipping."))
            }

            // Preserve orientation metadata if present
            val retriever = MediaMetadataRetriever()
            val rotation = try {
                retriever.setDataSource(context, sourceUri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0
            } catch (_: Exception) {
                0
            } finally {
                runCatching { retriever.release() }
            }
            if (rotation in setOf(0, 90, 180, 270)) {
                muxer.setOrientationHint(rotation)
            }

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            val clipDurationUs = (endUs - startUs).coerceAtLeast(1L)

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            muxer.start()

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            onProgress(0.05f)

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break

                val sourceTrackIndex = extractor.sampleTrackIndex
                val dstTrackIndex = trackIndexMap[sourceTrackIndex]
                if (dstTrackIndex != null && sampleTimeUs >= 0) {
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = sampleTimeUs
                    val sampleFlags = extractor.sampleFlags
                    var codecFlags = 0
                    if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0
                    ) {
                        codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                    }
                    bufferInfo.flags = codecFlags
                    muxer.writeSampleData(dstTrackIndex, buffer, bufferInfo)

                    if (sampleTimeUs >= startUs) {
                        val fraction = ((sampleTimeUs - startUs).toFloat() / clipDurationUs.toFloat())
                            .coerceIn(0.05f, 0.98f)
                        onProgress(fraction)
                    }
                }
                extractor.advance()
            }

            muxer.stop()
            onProgress(1f)

            Result.success(
                ClipExportResult(
                    outputFile = outputFile,
                    startMs = startMs,
                    endMs = endMs,
                    aspectRatio = aspectRatio,
                    sizeBytes = outputFile.length()
                )
            )
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(
                IllegalStateException("Local clip export failed: ${e.localizedMessage}", e)
            )
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx) ?: "Local Video"
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Local Video"
    }

    private fun scaleBitmapForPreviewAndAi(source: Bitmap, maxDim: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDim && height <= maxDim) return source
        val scale = maxDim.toFloat() / maxOf(width, height).toFloat()
        val newW = (width * scale).toInt().coerceAtLeast(1)
        val newH = (height * scale).toInt().coerceAtLeast(1)
        val scaled = source.scale(newW, newH)
        if (scaled != source) source.recycle()
        return scaled
    }
}
