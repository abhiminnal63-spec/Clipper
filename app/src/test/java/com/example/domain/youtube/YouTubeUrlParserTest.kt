package com.example.domain.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeUrlParserTest {

    @Test
    fun parse_standardWatchUrl_extractsVideoId() {
        val result = YouTubeUrlParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s")
        assertTrue(result is YouTubeUrlValidationResult.Valid)
        val valid = result as YouTubeUrlValidationResult.Valid
        assertEquals("dQw4w9WgXcQ", valid.videoId)
        assertEquals(YouTubeSourceKind.WATCH, valid.sourceKind)
    }

    @Test
    fun parse_shortLink_extractsVideoId() {
        val result = YouTubeUrlParser.parse("https://youtu.be/jNQXAC9IVRw?si=abc123")
        assertTrue(result is YouTubeUrlValidationResult.Valid)
        val valid = result as YouTubeUrlValidationResult.Valid
        assertEquals("jNQXAC9IVRw", valid.videoId)
        assertEquals(YouTubeSourceKind.SHORT_LINK, valid.sourceKind)
    }

    @Test
    fun parse_shortsUrl_extractsVideoId() {
        val result = YouTubeUrlParser.parse("youtube.com/shorts/9bZkp7q19f0")
        assertTrue(result is YouTubeUrlValidationResult.Valid)
        val valid = result as YouTubeUrlValidationResult.Valid
        assertEquals("9bZkp7q19f0", valid.videoId)
        assertEquals(YouTubeSourceKind.SHORTS, valid.sourceKind)
    }

    @Test
    fun parse_invalidVideoIdLength_returnsInvalid() {
        val result = YouTubeUrlParser.parse("https://www.youtube.com/watch?v=short123")
        assertTrue(result is YouTubeUrlValidationResult.Invalid)
    }

    @Test
    fun parse_nonYoutubeDomain_returnsInvalid() {
        val result = YouTubeUrlParser.parse("https://vimeo.com/123456789")
        assertTrue(result is YouTubeUrlValidationResult.Invalid)
    }
}
