package com.maiaxi.iptvplayer.services

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.maiaxi.iptvplayer.R
import com.maiaxi.iptvplayer.models.AccountInfo
import com.maiaxi.iptvplayer.models.ContentKind
import com.maiaxi.iptvplayer.models.EpgEntry
import com.maiaxi.iptvplayer.models.Episode
import com.maiaxi.iptvplayer.models.LibraryData
import com.maiaxi.iptvplayer.models.MediaCategory
import com.maiaxi.iptvplayer.models.MediaItem
import com.maiaxi.iptvplayer.models.SeriesInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException

/**
 * Client for the Xtream Codes `player_api.php` API.
 * Responses are loosely typed (numbers arrive as strings and vice versa), so they're read leniently.
 */
class XtreamClient(
    private val context: Context,
    server: String,
    private val username: String,
    private val password: String,
    private val sourceId: String,
) {
    private val base = server.trimEnd('/')

    // Library

    suspend fun loadLibrary(): LibraryData {
        // Sequential on purpose: many panels answer 503 to concurrent requests from one account.
        val account = accountInfo()
        val liveCats = categories("get_live_categories", ContentKind.LIVE)
        val live = list("get_live_streams")
        // Movies and series are optional: plenty of accounts are live-only, and one failing
        // shouldn't hide the channels.
        val vodCats = optional { categories("get_vod_categories", ContentKind.MOVIE) }
        val vod = optional { list("get_vod_streams") }
        val seriesCats = optional { categories("get_series_categories", ContentKind.SERIES) }
        val series = optional { list("get_series") }

        val ext = account.outputFormat
        val items = mutableListOf<MediaItem>()
        for (s in live) {
            val id = long(s.opt("stream_id")) ?: continue
            items += MediaItem(
                id = "$sourceId:live:$id",
                name = string(s.opt("name")) ?: context.getString(R.string.channel_n, id),
                logo = url(s.opt("stream_icon")),
                categoryId = "live:${string(s.opt("category_id")).orEmpty()}",
                kind = ContentKind.LIVE,
                streamUrl = streamUrl("live", id, ext),
                xtreamId = id,
                rating = null,
                epgId = string(s.opt("epg_channel_id")),
            )
        }
        for (s in vod) {
            val id = long(s.opt("stream_id")) ?: continue
            items += MediaItem(
                id = "$sourceId:movie:$id",
                name = string(s.opt("name")) ?: context.getString(R.string.movie_n, id),
                logo = url(s.opt("stream_icon")),
                categoryId = "movie:${string(s.opt("category_id")).orEmpty()}",
                kind = ContentKind.MOVIE,
                streamUrl = streamUrl("movie", id, string(s.opt("container_extension")) ?: "mp4"),
                xtreamId = id,
                rating = rating(s.opt("rating")),
            )
        }
        for (s in series) {
            val id = long(s.opt("series_id")) ?: continue
            items += MediaItem(
                id = "$sourceId:series:$id",
                name = string(s.opt("name")) ?: context.getString(R.string.series_n, id),
                logo = url(s.opt("cover")),
                categoryId = "series:${string(s.opt("category_id")).orEmpty()}",
                kind = ContentKind.SERIES,
                streamUrl = null,
                xtreamId = id,
                rating = rating(s.opt("rating")),
            )
        }

        return LibraryData(
            items = items,
            categories = liveCats + vodCats + seriesCats,
            account = account,
            fetchedAt = System.currentTimeMillis(),
            epgUrl = xmltvUrl()?.toString(),
        )
    }

    private fun xmltvUrl(): HttpUrl? = "$base/xmltv.php".toHttpUrlOrNull()?.newBuilder()
        ?.addQueryParameter("username", username)
        ?.addQueryParameter("password", password)
        ?.build()

    suspend fun accountInfo(): AccountInfo {
        val user = (request(null) as? JSONObject)?.optJSONObject("user_info")
            ?: throw AppError(R.string.error_invalid_response)
        if (long(user.opt("auth")) == 0L) throw AppError(R.string.error_credentials)
        val formats = (user.opt("allowed_output_formats") as? JSONArray)
            ?.let { arr -> (0 until arr.length()).mapNotNull { string(arr.opt(it)) } }
            .orEmpty()
        return AccountInfo(
            expiresAt = long(user.opt("exp_date"))?.let { it * 1000 },
            maxConnections = long(user.opt("max_connections"))?.toInt(),
            status = string(user.opt("status")),
            outputFormat = if (formats.isEmpty() || "ts" in formats) "ts" else formats[0],
        )
    }

    // Details

    suspend fun seriesInfo(seriesId: Long): SeriesInfo {
        val root = request("get_series_info", mapOf("series_id" to "$seriesId")) as? JSONObject
            ?: throw AppError(R.string.error_series)
        val info = root.optJSONObject("info") ?: JSONObject()
        // `episodes` is usually an object keyed by season number, occasionally an array of arrays.
        val groups: List<JSONArray> = when (val episodes = root.opt("episodes")) {
            is JSONObject -> episodes.keys().asSequence().mapNotNull { episodes.opt(it) as? JSONArray }.toList()
            is JSONArray -> (0 until episodes.length()).mapNotNull { episodes.opt(it) as? JSONArray }
            else -> emptyList()
        }
        val seasons = mutableMapOf<Int, MutableList<Episode>>()
        for (group in groups) {
            for (e in objects(group)) {
                val id = string(e.opt("id")) ?: continue
                val season = long(e.opt("season"))?.toInt() ?: 1
                val epInfo = e.optJSONObject("info") ?: JSONObject()
                val ext = string(e.opt("container_extension")) ?: "mp4"
                seasons.getOrPut(season) { mutableListOf() } += Episode(
                    id = id,
                    season = season,
                    number = long(e.opt("episode_num"))?.toInt() ?: 0,
                    title = string(e.opt("title")) ?: context.getString(R.string.episode),
                    plot = string(epInfo.opt("plot")),
                    image = url(epInfo.opt("movie_image")),
                    duration = string(epInfo.opt("duration")),
                    streamUrl = "$base/series/${enc(username)}/${enc(password)}/$id.$ext",
                )
            }
        }
        val backdrop = (info.opt("backdrop_path") as? JSONArray)?.let { if (it.length() > 0) url(it.opt(0)) else null }
        return SeriesInfo(
            plot = string(info.opt("plot")),
            cast = string(info.opt("cast")),
            genre = string(info.opt("genre")),
            releaseDate = string(info.opt("releaseDate")) ?: string(info.opt("release_date")),
            cover = url(info.opt("cover")),
            backdrop = backdrop,
            episodesBySeason = seasons.mapValues { (_, eps) -> eps.sortedBy { it.number } },
        )
    }

    /** The next few programmes from the panel, for channels the XMLTV guide doesn't cover. */
    suspend fun shortEpg(streamId: Long): List<EpgEntry> {
        val root = request("get_short_epg", mapOf("stream_id" to "$streamId", "limit" to "6")) as? JSONObject
        val listings = root?.opt("epg_listings") as? JSONArray ?: return emptyList()
        val now = System.currentTimeMillis()
        return objects(listings).mapNotNull { l ->
            val start = long(l.opt("start_timestamp")) ?: return@mapNotNull null
            val end = long(l.opt("stop_timestamp")) ?: return@mapNotNull null
            EpgEntry(base64(l.opt("title")).orEmpty(), base64(l.opt("description")), start * 1000, end * 1000)
        }.filter { it.end > now }
    }

    // Networking

    private fun enc(s: String): String = Uri.encode(s, "!$&'()*+,;=:@")

    private fun streamUrl(type: String, id: Long, ext: String) = "$base/$type/${enc(username)}/${enc(password)}/$id.$ext"

    private suspend fun request(action: String?, params: Map<String, String> = emptyMap()): Any? {
        val url = "$base/player_api.php".toHttpUrlOrNull()?.newBuilder()?.apply {
            addQueryParameter("username", username)
            addQueryParameter("password", password)
            if (action != null) addQueryParameter("action", action)
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }?.build() ?: throw AppError(R.string.error_invalid_server)
        val request = Request.Builder().url(url).build()

        // Panels rate-limit aggressively (503/429, sometimes an empty 200), so back off and retry.
        val delays = listOf(1000L, 2000L, 4000L)
        var attempt = 0
        while (true) {
            val response = Http.client.fetch(request)
            val status = response.code
            val transient = status in setOf(429, 502, 503, 504) || (status == 200 && response.body.isEmpty())
            if (transient && attempt < delays.size) {
                delay(delays[attempt])
                attempt++
                continue
            }
            if (status !in 200..299) {
                throw if (status == 503 || status == 429) AppError(R.string.error_server_busy, status)
                else AppError(R.string.error_server_status, status)
            }
            return try {
                JSONTokener(response.body).nextValue()
            } catch (e: JSONException) {
                throw AppError(R.string.error_invalid_data)
            }
        }
    }

    private suspend fun categories(action: String, kind: ContentKind): List<MediaCategory> {
        val arr = request(action) as? JSONArray ?: return emptyList()
        return objects(arr).mapNotNull { c ->
            val id = string(c.opt("category_id")) ?: return@mapNotNull null
            MediaCategory("${kind.key}:$id", string(c.opt("category_name")) ?: id, kind)
        }
    }

    private suspend fun list(action: String): List<JSONObject> = (request(action) as? JSONArray)?.let(::objects).orEmpty()

    private fun objects(arr: JSONArray): List<JSONObject> = (0 until arr.length()).mapNotNull { arr.opt(it) as? JSONObject }

    private suspend fun <T> optional(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    // Lenient field readers

    private fun string(v: Any?): String? = when (v) {
        is String -> v.ifEmpty { null }
        is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        is Number -> v.toString()
        is Boolean -> if (v) "1" else "0"
        else -> null
    }

    private fun long(v: Any?): Long? = when (v) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: v.toDoubleOrNull()?.toLong()
        is Boolean -> if (v) 1 else 0
        else -> null
    }

    private fun url(v: Any?): String? = string(v)?.trim()?.ifEmpty { null }

    private fun rating(v: Any?): String? {
        val d = string(v)?.toDoubleOrNull() ?: return null
        return if (d > 0) String.format(java.util.Locale.ROOT, "%.1f", d) else null
    }

    private fun base64(v: Any?): String? {
        val s = string(v) ?: return null
        return try {
            val bytes = Base64.decode(s, Base64.DEFAULT)
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: IllegalArgumentException) {
            s
        } catch (e: CharacterCodingException) {
            s
        }
    }
}
