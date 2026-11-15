package com.example.domain.youtube

import java.net.URI

sealed interface YouTubeUrlValidationResult {
    data object Empty : YouTubeUrlValidationResult
    data class Valid(
        val videoId: String,
        val sourceKind: YouTubeSourceKind,
        val normalizedWatchUrl: String,
        val thumbnailUrl: String
    ) : YouTubeUrlValidationResult
    data class Invalid(val reason: String) : YouTubeUrlValidationResult
}

enum class YouTubeSourceKind(val label: String) {
    WATCH("youtube.com/watch"),
    SHORT_LINK("youtu.be"),
    SHORTS("youtube.com/shorts"),
    LIVE("youtube.com/live"),
    EMBED("youtube.com/embed")
}

object YouTubeUrlParser {
    private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

    private val ALLOWED_YOUTUBE_HOSTS = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtube-nocookie.com",
        "www.youtube-nocookie.com"
    )

    private val ALLOWED_SHORT_HOSTS = setOf(
        "youtu.be",
        "www.youtu.be"
    )

    fun isValidVideoId(id: String): Boolean = VIDEO_ID_REGEX.matches(id)

    fun parse(rawInput: String): YouTubeUrlValidationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return YouTubeUrlValidationResult.Empty

        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val uri = try {
            URI(withScheme)
        } catch (_: Exception) {
            return YouTubeUrlValidationResult.Invalid("Enter a valid YouTube URL (watch, youtu.be, or Shorts).")
        }

        val host = uri.host?.lowercase()
            ?: return YouTubeUrlValidationResult.Invalid("Could not recognize a valid YouTube host.")

        val pathSegments = uri.path
            ?.split('/')
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        val extracted: Pair<String, YouTubeSourceKind>? = when {
            host in ALLOWED_SHORT_HOSTS -> {
                val id = pathSegments.firstOrNull().orEmpty()
                if (id.isNotEmpty()) id to YouTubeSourceKind.SHORT_LINK else null
            }
            host in ALLOWED_YOUTUBE_HOSTS -> {
                val first = pathSegments.firstOrNull()?.lowercase()
                when (first) {
                    "watch" -> {
                        val vParam = extractQueryParam(uri.rawQuery, "v")
                        if (!vParam.isNullOrEmpty()) vParam to YouTubeSourceKind.WATCH else null
                    }
                    "shorts" -> {
                        val id = pathSegments.getOrNull(1).orEmpty()
                        if (id.isNotEmpty()) id to YouTubeSourceKind.SHORTS else null
                    }
                    "live" -> {
                        val id = pathSegments.getOrNull(1).orEmpty()
                        if (id.isNotEmpty()) id to YouTubeSourceKind.LIVE else null
                    }
                    "embed" -> {
                        val id = pathSegments.getOrNull(1).orEmpty()
                        if (id.isNotEmpty()) id to YouTubeSourceKind.EMBED else null
                    }
                    else -> {
                        // Also support root URLs with ?v= query param
                        val vParam = extractQueryParam(uri.rawQuery, "v")
                        if (!vParam.isNullOrEmpty()) vParam to YouTubeSourceKind.WATCH else null
                    }
                }
            }
            else -> {
                return YouTubeUrlValidationResult.Invalid(
                    "Only official YouTube links (youtube.com/watch, youtu.be, youtube.com/shorts) are supported."
                )
            }
        }

        val (candidateId, kind) = extracted
            ?: return YouTubeUrlValidationResult.Invalid(
                "Missing YouTube video ID. Paste a youtube.com/watch?v=..., youtu.be/..., or Shorts link."
            )

        if (!isValidVideoId(candidateId)) {
            return YouTubeUrlValidationResult.Invalid(
                "Video ID \"$candidateId\" must be exactly 11 valid characters."
            )
        }

        return YouTubeUrlValidationResult.Valid(
            videoId = candidateId,
            sourceKind = kind,
            normalizedWatchUrl = "https://www.youtube.com/watch?v=$candidateId",
            thumbnailUrl = "https://i.ytimg.com/vi/$candidateId/hqdefault.jpg"
        )
    }

    private fun extractQueryParam(rawQuery: String?, key: String): String? {
        if (rawQuery.isNullOrBlank()) return null
        return rawQuery.split('&')
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) null
                else pair.substring(0, idx) to pair.substring(idx + 1)
            }
            .firstOrNull { (k, _) -> k == key }
            ?.second
    }
}
