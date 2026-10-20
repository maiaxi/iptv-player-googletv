package com.maiaxi.iptvplayer.models

import androidx.annotation.StringRes
import com.maiaxi.iptvplayer.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.util.UUID

@Serializable
enum class ContentKind(@param:StringRes val title: Int) {
    @SerialName("live") LIVE(R.string.live_tv),
    @SerialName("movie") MOVIE(R.string.movies),
    @SerialName("series") SERIES(R.string.series);

    val key: String get() = name.lowercase()
}

@Serializable
data class MediaItem(
    /** Unique across sources: "<sourceId>:<kind>:<nativeId>" */
    val id: String,
    val name: String,
    val logo: String?,
    val categoryId: String,
    val kind: ContentKind,
    /** null for Xtream series, which need their episode list loaded first. */
    val streamUrl: String?,
    val xtreamId: Long?,
    val rating: String?,
    val userAgent: String? = null,
    val referrer: String? = null,
    /** XMLTV channel id (`tvg-id` / `epg_channel_id`). */
    val epgId: String? = null,
) {
    val isPlayable: Boolean get() = streamUrl != null
}

@Serializable
data class MediaCategory(val id: String, val name: String, val kind: ContentKind)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val plot: String?,
    val image: String?,
    val duration: String?,
    val streamUrl: String,
)

/** A playable item for this episode, so it can join the player's queue. */
fun Episode.item(series: MediaItem, name: String) = MediaItem(
    id = "${series.id}:ep:$id",
    name = name,
    logo = series.logo,
    categoryId = series.categoryId,
    kind = ContentKind.SERIES,
    streamUrl = streamUrl,
    xtreamId = null,
    rating = null,
)

data class SeriesInfo(
    val plot: String?,
    val cast: String?,
    val genre: String?,
    val releaseDate: String?,
    val cover: String?,
    val backdrop: String?,
    val episodesBySeason: Map<Int, List<Episode>>,
)

/** A programme in the guide. Times are epoch milliseconds. */
data class EpgEntry(val title: String, val description: String?, val start: Long, val end: Long) {
    fun progress(at: Long): Float {
        val total = end - start
        if (total <= 0) return 0f
        return ((at - start).toFloat() / total).coerceIn(0f, 1f)
    }
}

@Serializable
data class AccountInfo(
    val expiresAt: Long?,
    val maxConnections: Int?,
    val status: String?,
    val outputFormat: String,
)

@Serializable
sealed interface SourceKind {
    @Serializable
    @SerialName("m3u")
    data class M3u(val url: String) : SourceKind

    @Serializable
    @SerialName("xtream")
    data class Xtream(val server: String, val username: String, val password: String) : SourceKind
}

@Serializable
data class Source(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: SourceKind,
) {
    val subtitle: String
        get() = when (kind) {
            is SourceKind.M3u -> host(kind.url) ?: kind.url
            is SourceKind.Xtream -> "${kind.username} @ ${host(kind.server) ?: kind.server}"
        }

    private fun host(address: String): String? = runCatching { URI(address).host }.getOrNull()
}

/** Everything loaded from one source, cached on disk between launches. */
@Serializable
data class LibraryData(
    val items: List<MediaItem>,
    val categories: List<MediaCategory>,
    val account: AccountInfo?,
    val fetchedAt: Long,
    /** XMLTV guide for the whole source. */
    val epgUrl: String? = null,
)
