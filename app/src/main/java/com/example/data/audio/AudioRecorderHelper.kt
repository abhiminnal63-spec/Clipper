package com.example.data.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): Result<File> {
        stopAndRelease()
        val file = File(context.cacheDir, "clipper_voice_${System.currentTimeMillis()}.m4a")
        return try {
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(16_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            outputFile = file
            Result.success(file)
        } catch (e: Exception) {
            file.delete()
            stopAndRelease()
            Result.failure(e)
        }
    }

    fun stopRecording(): File? {
        val recorded = outputFile
        try {
            recorder?.stop()
        } catch (_: Exception) {
            recorded?.delete()
            stopAndRelease()
            return null
        }
        stopAndRelease()
        return if (recorded != null && recorded.exists() && recorded.length() > 0L) {
            recorded
        } else {
            recorded?.delete()
            null
        }
    }

    fun currentAmplitudeNormalized(): Float {
        val amp = try {
            recorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
        return (amp / 32767f).coerceIn(0f, 1f)
    }

    fun stopAndRelease() {
        runCatching { recorder?.release() }
        recorder = null
    }
}
