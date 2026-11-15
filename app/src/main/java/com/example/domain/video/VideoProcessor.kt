package com.example.domain.video

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

data class LocalVideoInfo(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val keyframes: List<Bitmap>
) {
    val durationSeconds: Long
        get() = (durationMs / 1000L).coerceAtLeast(0L)
}

data class ClipExportResult(
    val outputFile: File,
    val startMs: Long,
    val endMs: Long,
    val aspectRatio: String,
    val sizeBytes: Long
)

interface VideoProcessor {
    val capabilitiesLabel: String

    suspend fun inspectLocalVideo(uri: Uri, maxKeyframes: Int = 6): Result<LocalVideoInfo>

    suspend fun trimLocalClip(
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        aspectRatio: String,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<ClipExportResult>
}
