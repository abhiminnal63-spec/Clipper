package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class ClipSuggestion(
    val clipId: String = "",
    val title: String = "",
    val startSeconds: Long = 0L,
    val endSeconds: Long = 30L,
    val hookScore: Long = 80L,
    val rationale: String = "",
    val hashtags: List<String> = emptyList()
) {
    val durationSeconds: Long
        get() = (endSeconds - startSeconds).coerceAtLeast(1L)

    fun toMap(): Map<String, Any> = mapOf(
        "clipId" to clipId,
        "title" to title.take(120),
        "startSeconds" to startSeconds,
        "endSeconds" to endSeconds,
        "hookScore" to hookScore.coerceIn(0L, 100L),
        "rationale" to rationale.take(500),
        "hashtags" to hashtags.take(8)
    )
}

@IgnoreExtraProperties
data class ClipProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sourceType: String = SOURCE_YOUTUBE,
    val videoId: String = "",
    val sourceUrl: String = "",
    val title: String = "",
    val channelName: String = "",
    val thumbnailUrl: String = "",
    val durationSeconds: Long = 0L,
    val aspectRatio: String = ASPECT_9_16,
    val status: String = STATUS_PREVIEW,
    val strategyNotes: String = "",
    val clips: List<ClipSuggestion> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "sourceType" to sourceType,
        "videoId" to videoId.take(64),
        "sourceUrl" to sourceUrl.take(500),
        "title" to title.ifBlank { "Untitled Video" }.take(200),
        "channelName" to channelName.take(120),
        "thumbnailUrl" to thumbnailUrl.take(500),
        "durationSeconds" to durationSeconds.coerceIn(0L, 86400L),
        "aspectRatio" to aspectRatio,
        "status" to status,
        "strategyNotes" to strategyNotes.take(8000),
        "clips" to clips.take(10).map { it.toMap() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        const val SOURCE_YOUTUBE = "youtube"
        const val SOURCE_LOCAL = "local"
        const val ASPECT_9_16 = "9:16"
        const val ASPECT_16_9 = "16:9"
        const val ASPECT_1_1 = "1:1"
        const val STATUS_PREVIEW = "preview"
        const val STATUS_ANALYZED = "analyzed"
        const val STATUS_CLIPPED = "clipped"
    }
}
