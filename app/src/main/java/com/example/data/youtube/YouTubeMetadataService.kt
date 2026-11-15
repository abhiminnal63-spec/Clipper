package com.example.data.youtube

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class YouTubeVideosListResponse(
    val items: List<YouTubeVideoItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class YouTubeVideoItem(
    val id: String = "",
    val snippet: YouTubeSnippet? = null,
    val contentDetails: YouTubeContentDetails? = null,
    val statistics: YouTubeStatistics? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeSnippet(
    val title: String = "",
    val description: String = "",
    val channelTitle: String = "",
    val publishedAt: String = "",
    val tags: List<String>? = null,
    val thumbnails: YouTubeThumbnails? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeThumbnails(
    val high: YouTubeThumbnail? = null,
    val maxres: YouTubeThumbnail? = null,
    val medium: YouTubeThumbnail? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeThumbnail(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0
)

@JsonClass(generateAdapter = true)
data class YouTubeContentDetails(
    val duration: String = ""
)

@JsonClass(generateAdapter = true)
data class YouTubeStatistics(
    val viewCount: String? = null,
    val likeCount: String? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeOEmbedResponse(
    val title: String = "",
    @Json(name = "author_name") val authorName: String = "",
    @Json(name = "thumbnail_url") val thumbnailUrl: String = ""
)

interface YouTubeDataApi {
    @GET("youtube/v3/videos")
    suspend fun getVideoDetails(
        @Query("part") part: String = "snippet,contentDetails,statistics",
        @Query("id") videoId: String,
        @Query("key") apiKey: String
    ): YouTubeVideosListResponse

    @GET("oembed")
    suspend fun getOEmbed(
        @Query("url") watchUrl: String,
        @Query("format") format: String = "json"
    ): YouTubeOEmbedResponse
}

data class YouTubeVideoMetadata(
    val videoId: String,
    val title: String,
    val channelName: String,
    val description: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val viewCount: Long?,
    val tags: List<String>,
    val usedDataApiKey: Boolean
)

class YouTubeMetadataRepository {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val dataApi: YouTubeDataApi = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(YouTubeDataApi::class.java)

    private val oEmbedApi: YouTubeDataApi = Retrofit.Builder()
        .baseUrl("https://www.youtube.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(YouTubeDataApi::class.java)

    suspend fun fetchPublicMetadata(videoId: String): Result<YouTubeVideoMetadata> =
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.YOUTUBE_API_KEY
            val hasConfiguredKey = apiKey.isNotBlank() && apiKey != "MY_YOUTUBE_API_KEY"

            if (hasConfiguredKey) {
                try {
                    val response = dataApi.getVideoDetails(videoId = videoId, apiKey = apiKey)
                    val item = response.items.firstOrNull()
                        ?: return@withContext Result.failure(
                            IllegalStateException("Video not found or is private/unavailable on YouTube.")
                        )
                    val snippet = item.snippet
                    val thumb = snippet?.thumbnails?.maxres?.url
                        ?: snippet?.thumbnails?.high?.url
                        ?: snippet?.thumbnails?.medium?.url
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                    return@withContext Result.success(
                        YouTubeVideoMetadata(
                            videoId = videoId,
                            title = snippet?.title.orEmpty().ifBlank { "YouTube Video ($videoId)" },
                            channelName = snippet?.channelTitle.orEmpty(),
                            description = snippet?.description.orEmpty(),
                            thumbnailUrl = thumb,
                            durationSeconds = parseIso8601DurationSeconds(item.contentDetails?.duration.orEmpty()),
                            viewCount = item.statistics?.viewCount?.toLongOrNull(),
                            tags = snippet?.tags.orEmpty().take(12),
                            usedDataApiKey = true
                        )
                    )
                } catch (_: Exception) {
                    // Fall through to official public YouTube oEmbed metadata endpoint
                }
            }

            try {
                val watchUrl = "https://www.youtube.com/watch?v=$videoId"
                val oembed = oEmbedApi.getOEmbed(watchUrl = watchUrl)
                Result.success(
                    YouTubeVideoMetadata(
                        videoId = videoId,
                        title = oembed.title.ifBlank { "YouTube Video ($videoId)" },
                        channelName = oembed.authorName,
                        description = "",
                        thumbnailUrl = oembed.thumbnailUrl.ifBlank {
                            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                        },
                        durationSeconds = 0L,
                        viewCount = null,
                        tags = emptyList(),
                        usedDataApiKey = false
                    )
                )
            } catch (e: Exception) {
                Result.failure(
                    IllegalStateException(
                        "Unable to retrieve public YouTube metadata for $videoId. Check network connectivity or video privacy.",
                        e
                    )
                )
            }
        }

    companion object {
        private val DURATION_REGEX = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")

        fun parseIso8601DurationSeconds(isoDuration: String): Long {
            val match = DURATION_REGEX.matchEntire(isoDuration.trim()) ?: return 0L
            val hours = match.groupValues[1].toLongOrNull() ?: 0L
            val minutes = match.groupValues[2].toLongOrNull() ?: 0L
            val seconds = match.groupValues[3].toLongOrNull() ?: 0L
            return hours * 3600L + minutes * 60L + seconds
        }
    }
}
