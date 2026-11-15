package com.example.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.data.youtube.YouTubeVideoMetadata
import com.example.domain.model.ClipProject
import com.example.domain.model.ClipSuggestion
import com.example.domain.video.LocalVideoInfo
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class ClipperAiRepository {

    private val clipDiscoverySchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "One-sentence hook summary of the video"),
            "clips" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Punchy vertical Short title"),
                        "startSeconds" to Schema.integer(description = "Clip start time in seconds"),
                        "endSeconds" to Schema.integer(description = "Clip end time in seconds"),
                        "hookScore" to Schema.integer(description = "Viral hook potential from 1 to 100"),
                        "rationale" to Schema.string(description = "Why this segment works as a vertical Short"),
                        "hashtags" to Schema.array(
                            items = Schema.string(),
                            description = "3 to 5 relevant Shorts hashtags"
                        )
                    )
                ),
                description = "Top 3 to 5 high-retention Short clip segments"
            )
        )
    )

    // Gemini 4 Argon for video understanding & clip discovery
    private val videoAnalysisModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_VIDEO_UNDERSTANDING,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = clipDiscoverySchema
            }
        )
    }

    // Gemini 3.5 Transcribe for microphone audio transcription
    private val transcriptionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_AUDIO_TRANSCRIBE
        )
    }

    // Gemini 4 Carbon with ThinkingLevel.HIGH (no maxOutputTokens set) for complex strategy queries
    private val highThinkingStrategyModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_HIGH_THINKING,
            generationConfig = generationConfig {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                }
            }
        )
    }

    suspend fun analyzeYouTubeVideo(
        metadata: YouTubeVideoMetadata,
        aspectRatio: String
    ): Result<List<ClipSuggestion>> {
        return try {
            val thumbnailBitmap = fetchBitmapSafely(metadata.thumbnailUrl)
            val durationHint = if (metadata.durationSeconds > 0L) {
                "${metadata.durationSeconds} seconds"
            } else {
                "unknown exact duration (suggest realistic 20-55 second segments starting from 0s)"
            }
            val tagsText = if (metadata.tags.isNotEmpty()) {
                metadata.tags.joinToString(", ")
            } else {
                "none"
            }

            val promptText = """
                You are Clipper's vertical Shorts editor using Gemini video understanding.
                Analyze this YouTube video metadata and visual thumbnail to identify 3 to 4 high-retention $aspectRatio Shorts clip candidates.
                Video ID: ${metadata.videoId}
                Title: ${metadata.title}
                Channel: ${metadata.channelName}
                Duration: $durationHint
                Tags: $tagsText
                Description snippet: ${metadata.description.take(1200)}

                Rules:
                - Every clip must have endSeconds > startSeconds and duration between 15 and 60 seconds.
                - If duration is known (${metadata.durationSeconds}s), keep all timestamps strictly within 0..${metadata.durationSeconds}.
                - Make titles punchy and curiosity-driven.
            """.trimIndent()

            val inputContent = content {
                if (thumbnailBitmap != null) {
                    image(thumbnailBitmap)
                }
                text(promptText)
            }

            val response = withTimeout(TIMEOUT_MS) {
                videoAnalysisModel.generateContent(inputContent)
            }
            val jsonText = response.text
                ?: return Result.failure(IllegalStateException("Gemini returned an empty analysis response."))

            Result.success(parseClipSuggestions(jsonText, metadata.durationSeconds))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "YouTube video analysis timed out", e)
            Result.failure(IllegalStateException("Video analysis timed out after 60s. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "YouTube video analysis failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Video analysis failed.", e))
        }
    }

    suspend fun analyzeLocalVideo(
        localVideo: LocalVideoInfo,
        aspectRatio: String
    ): Result<List<ClipSuggestion>> {
        return try {
            val durationSec = localVideo.durationSeconds.coerceAtLeast(15L)
            val promptText = """
                You are Clipper's vertical Shorts editor. Inspect these evenly-sampled keyframes from a local user-authorized video file.
                File name: ${localVideo.displayName}
                Resolution: ${localVideo.width}x${localVideo.height}
                Total duration: $durationSec seconds
                Target Short aspect ratio: $aspectRatio

                Identify 3 to 4 best Short clip segments based on visual pacing, scene transitions, and framing.
                Keep startSeconds and endSeconds strictly between 0 and $durationSec, with endSeconds > startSeconds.
            """.trimIndent()

            val inputContent = content {
                localVideo.keyframes.take(6).forEach { frame ->
                    image(frame)
                }
                text(promptText)
            }

            val response = withTimeout(TIMEOUT_MS) {
                videoAnalysisModel.generateContent(inputContent)
            }
            val jsonText = response.text
                ?: return Result.failure(IllegalStateException("Gemini returned an empty local video analysis."))

            Result.success(parseClipSuggestions(jsonText, durationSec))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Local video analysis timed out", e)
            Result.failure(IllegalStateException("Local video analysis timed out after 60s. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Local video analysis failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Local video analysis failed.", e))
        }
    }

    suspend fun transcribeAudio(
        audioBytes: ByteArray,
        mimeType: String = "audio/mp4"
    ): Result<String> {
        if (audioBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Recorded audio is empty."))
        }
        if (audioBytes.size > MAX_INLINE_BYTES) {
            return Result.failure(IllegalArgumentException("Audio clip exceeds 20MB inline limit."))
        }
        return try {
            val input = content {
                inlineData(audioBytes, mimeType)
                text("Transcribe the spoken audio accurately into clean plain text with proper punctuation. Output only the transcript.")
            }
            val response = withTimeout(TIMEOUT_MS) {
                transcriptionModel.generateContent(input)
            }
            val transcript = response.text?.trim().orEmpty()
            if (transcript.isBlank()) {
                Result.failure(IllegalStateException("No speech detected in the recording."))
            } else {
                Result.success(transcript)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Audio transcription timed out", e)
            Result.failure(IllegalStateException("Audio transcription timed out. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Audio transcription failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Audio transcription failed.", e))
        }
    }

    fun streamDeepStrategy(
        project: ClipProject,
        userQuestion: String
    ): Flow<String> = flow {
        val clipsSummary = if (project.clips.isEmpty()) {
            "No clip timestamps generated yet."
        } else {
            project.clips.joinToString("\n") { clip ->
                "- ${clip.title} (${clip.startSeconds}s–${clip.endSeconds}s, score ${clip.hookScore}/100): ${clip.rationale}"
            }
        }

        val prompt = """
            You are an expert short-form video strategist.
            Project Title: ${project.title}
            Channel / Source: ${project.channelName.ifBlank { project.sourceType }}
            Target Aspect Ratio: ${project.aspectRatio}
            Duration: ${project.durationSeconds}s
            Candidate Clips:
            $clipsSummary

            Creator Request:
            $userQuestion

            Provide actionable, structured recommendations covering:
            1. First 3-second visual & spoken hook script
            2. Beat-by-beat pacing & pattern interrupts
            3. Caption style, title framing, and retention optimization
        """.trimIndent()

        val accumulated = StringBuilder()
        withTimeout(STRATEGY_TIMEOUT_MS) {
            highThinkingStrategyModel.generateContentStream(prompt).collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                if (chunkText.isNotEmpty()) {
                    accumulated.append(chunkText)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Deep strategy model returned an empty response.")
        }
    }

    private fun parseClipSuggestions(jsonString: String, maxDurationSeconds: Long): List<ClipSuggestion> {
        val root = JSONObject(jsonString)
        val clipsArray = root.optJSONArray("clips") ?: return emptyList()
        val parsed = mutableListOf<ClipSuggestion>()
        for (i in 0 until clipsArray.length().coerceAtMost(10)) {
            val obj = clipsArray.optJSONObject(i) ?: continue
            var start = obj.optLong("startSeconds", 0L).coerceAtLeast(0L)
            var end = obj.optLong("endSeconds", start + 30L)
            if (maxDurationSeconds > 5L) {
                start = start.coerceIn(0L, (maxDurationSeconds - 2L).coerceAtLeast(0L))
                end = end.coerceIn(start + 1L, maxDurationSeconds)
            } else if (end <= start) {
                end = start + 30L
            }
            val hashtagsArr = obj.optJSONArray("hashtags")
            val tags = buildList {
                if (hashtagsArr != null) {
                    for (t in 0 until hashtagsArr.length().coerceAtMost(6)) {
                        val tag = hashtagsArr.optString(t).trim()
                        if (tag.isNotBlank()) add(if (tag.startsWith("#")) tag else "#$tag")
                    }
                }
            }
            parsed.add(
                ClipSuggestion(
                    clipId = "clip_${UUID.randomUUID().toString().take(8)}",
                    title = obj.optString("title", "Highlight Clip ${i + 1}").take(120),
                    startSeconds = start,
                    endSeconds = end,
                    hookScore = obj.optLong("hookScore", 82L).coerceIn(1L, 100L),
                    rationale = obj.optString("rationale", "").take(500),
                    hashtags = tags
                )
            )
        }
        return parsed
    }

    private suspend fun fetchBitmapSafely(urlString: String): Bitmap? = withContext(Dispatchers.IO) {
        if (urlString.isBlank()) return@withContext null
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
            }
            connection.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val TAG = "ClipperAiRepo"
        const val MODEL_VIDEO_UNDERSTANDING = "gemini-4-argon"
        const val MODEL_AUDIO_TRANSCRIBE = "gemini-3.5-transcribe"
        const val MODEL_HIGH_THINKING = "gemini-4-carbon"
        const val TIMEOUT_MS = 60_000L
        const val STRATEGY_TIMEOUT_MS = 60_000L
        const val MAX_INLINE_BYTES = 18 * 1024 * 1024
    }
}
