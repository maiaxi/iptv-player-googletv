package com.maiaxi.iptvplayer.services

import android.app.Application
import android.content.Context
import android.util.Xml
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maiaxi.iptvplayer.R
import com.maiaxi.iptvplayer.models.ContentKind
import com.maiaxi.iptvplayer.models.EpgEntry
import com.maiaxi.iptvplayer.models.LibraryData
import com.maiaxi.iptvplayer.models.MediaCategory
import com.maiaxi.iptvplayer.models.MediaItem
import com.maiaxi.iptvplayer.models.SeriesInfo
import com.maiaxi.iptvplayer.models.Source
import com.maiaxi.iptvplayer.models.SourceKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException
import java.text.Normalizer
import java.util.concurrent.TimeUnit

/** Lowercased without accents, so "acao" finds "Ação", like the Swift app's diacritic-insensitive search. */
fun String.folded(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(combiningMarks, "").lowercase()

private val combiningMarks = Regex("\\p{Mn}+")

object CategoryFilter {
    const val ALL = "all"
}

sealed interface SidebarItem {
    data class Kind(val kind: ContentKind) : SidebarItem
    data object Favorites : SidebarItem
    data object Recents : SidebarItem
}

class LibraryStore(app: Application) : AndroidViewModel(app) {
    private val storage = Storage(app)
    private val prefs = app.getSharedPreferences("library", Context.MODE_PRIVATE)

    var sources by mutableStateOf(storage.loadSources())
        private set
    var selectedSourceId by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)

    var favorites by mutableStateOf(prefs.getStringSet("favorites", null).orEmpty().toSet())
        private set
    var recents by mutableStateOf(prefs.getString("recents", null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty())
        private set

    /** The library and its lookup maps change together, so Compose sees them as one state. */
    private class Index(val data: LibraryData?) {
        val items = data?.items.orEmpty()
        val byKind = items.groupBy { it.kind }
        val byCategory = items.groupBy { it.categoryId }
        val byId = items.associateBy { it.id }
        /** Folded names, built on the first search rather than for every library load. */
        val searchKeys by lazy { items.associate { it.id to it.name.folded() } }
    }

    private var index by mutableStateOf(Index(null))
    private var loadJob: Job? = null

    /** Programme guide keyed by lowercased XMLTV channel id. */
    var guide by mutableStateOf<Map<String, List<EpgEntry>>>(emptyMap())
        private set
    /** Ticks every 30 s so "now playing" and progress bars stay current. */
    var clock by mutableLongStateOf(System.currentTimeMillis())
        private set
    private var guideJob: Job? = null
    private var guideKey: String? = null
    private var guideLoadedAt = 0L

    val library: LibraryData? get() = index.data
    val selectedSource: Source? get() = sources.firstOrNull { it.id == selectedSourceId }

    init {
        (prefs.getString("selectedSource", null) ?: sources.firstOrNull()?.id)?.let(::select)
        viewModelScope.launch {
            while (true) {
                delay(CLOCK_TICK)
                clock = System.currentTimeMillis()
                if (clock - guideLoadedAt > GUIDE_RELOAD) loadGuide(force = true)
            }
        }
    }

    // Sources

    fun select(sourceId: String) {
        if (sources.none { it.id == sourceId }) return
        selectedSourceId = sourceId
        prefs.edit { putString("selectedSource", sourceId) }
        apply(Index(null))
        load(force = false)
    }

    fun add(source: Source) {
        sources = sources + source
        storage.saveSources(sources)
        select(source.id)
    }

    fun update(source: Source) {
        if (sources.none { it.id == source.id }) return
        sources = sources.map { if (it.id == source.id) source else it }
        storage.saveSources(sources)
        storage.removeLibrary(source.id)
        if (source.id == selectedSourceId) load(force = true)
    }

    fun remove(source: Source) {
        sources = sources.filter { it.id != source.id }
        storage.saveSources(sources)
        storage.removeLibrary(source.id)
        if (selectedSourceId == source.id) {
            loadJob?.cancel()
            selectedSourceId = null
            isLoading = false
            apply(Index(null))
            sources.firstOrNull()?.let { select(it.id) }
        }
    }

    fun refresh() {
        seriesInfoCache.clear()
        guideKey = null
        load(force = true)
    }

    private fun apply(newIndex: Index) {
        index = newIndex
        loadGuide(force = false)
    }

    private fun load(force: Boolean) {
        val source = selectedSource ?: return
        loadJob?.cancel()
        loadError = null
        isLoading = true
        loadJob = viewModelScope.launch {
            if (!force) {
                val cached = withContext(Dispatchers.IO) { storage.loadLibrary(source.id) }
                if (cached != null) {
                    apply(withContext(Dispatchers.Default) { Index(cached) })
                    if (System.currentTimeMillis() - cached.fetchedAt < CACHE_MAX_AGE) {
                        isLoading = false
                        return@launch
                    }
                }
            }
            try {
                val data = fetch(source)
                apply(withContext(Dispatchers.Default) { Index(data) })
                launch(Dispatchers.IO + NonCancellable) { storage.saveLibrary(data, source.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadError = e.userMessage(getApplication())
            }
            isLoading = false
        }
    }

    private suspend fun fetch(source: Source): LibraryData {
        val context: Context = getApplication()
        return when (val kind = source.kind) {
            is SourceKind.Xtream ->
                XtreamClient(context, kind.server, kind.username, kind.password, source.id).loadLibrary()
            is SourceKind.M3u -> {
                val url = kind.url.toHttpUrlOrNull() ?: throw AppError(R.string.error_playlist_address)
                val client = Http.client.newBuilder().readTimeout(90, TimeUnit.SECONDS).build()
                val response = client.fetch(Request.Builder().url(url).build())
                if (response.code !in 200..299) throw AppError(R.string.error_playlist_download, response.code)
                val text = response.body
                if ("#EXTINF" !in text && "://" !in text) throw AppError(R.string.error_not_m3u)
                withContext(Dispatchers.Default) {
                    M3uParser.parse(text, source.id, context.getString(R.string.uncategorized))
                }
            }
        }
    }

    // Programme guide

    private fun loadGuide(force: Boolean) {
        val source = selectedSource
        val data = library
        val url = data?.epgUrl
        if (source == null || url == null) {
            guideJob?.cancel()
            guide = emptyMap()
            guideKey = null
            return
        }
        val key = "${source.id}|$url"
        if (!force && key == guideKey) return
        if (key != guideKey) guide = emptyMap()
        guideKey = key
        guideLoadedAt = System.currentTimeMillis()
        val channels = data.items.mapNotNullTo(HashSet()) { it.epgId }
        if (channels.isEmpty()) return

        guideJob?.cancel()
        guideJob = viewModelScope.launch {
            guide = withContext(Dispatchers.IO) { fetchGuide(url, source.id, channels, useCache = !force) } ?: return@launch
        }
    }

    private suspend fun fetchGuide(url: String, sourceId: String, channels: Set<String>, useCache: Boolean): Map<String, List<EpgEntry>>? {
        val file = storage.guideFile(sourceId)
        val now = System.currentTimeMillis()
        if (!useCache || !file.exists() || now - file.lastModified() > GUIDE_CACHE_AGE) {
            val httpUrl = url.toHttpUrlOrNull() ?: return null
            val client = Http.client.newBuilder().readTimeout(120, TimeUnit.SECONDS).build()
            val downloaded = try {
                client.download(Request.Builder().url(httpUrl).build(), file)
            } catch (e: IOException) {
                false
            }
            // An old guide is better than none while the panel is down.
            if (!downloaded && !file.exists()) return null
        }
        return file.inputStream().use {
            XmltvParser.parse(it, Xml.newPullParser(), channels, from = now - HOUR, to = now + 24 * HOUR)
        }
    }

    fun schedule(item: MediaItem): List<EpgEntry> = item.epgId?.let { guide[it.lowercase()] }.orEmpty()

    fun currentProgram(item: MediaItem): EpgEntry? {
        if (item.kind != ContentKind.LIVE) return null
        return schedule(item).firstOrNull { it.start <= clock && it.end > clock }
    }

    /** The guide's upcoming programmes, or the panel's short EPG when the guide has none. */
    suspend fun epg(item: MediaItem): List<EpgEntry> {
        val now = System.currentTimeMillis()
        val fromGuide = schedule(item).filter { it.end > now }
        if (fromGuide.isNotEmpty()) return fromGuide
        val client = xtreamClient ?: return emptyList()
        val id = item.xtreamId ?: return emptyList()
        if (item.kind != ContentKind.LIVE) return emptyList()
        return try {
            client.shortEpg(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Queries

    fun count(kind: ContentKind): Int = index.byKind[kind]?.size ?: 0

    fun categories(kind: ContentKind): List<MediaCategory> =
        library?.categories.orEmpty().filter { it.kind == kind && index.byCategory.containsKey(it.id) }

    fun count(category: MediaCategory): Int = index.byCategory[category.id]?.size ?: 0

    /**
     * `filter` is `CategoryFilter.ALL`, a category id (for a kind) or a kind's key (for
     * favorites/recents). `search` ignores case and accents.
     */
    fun items(sidebar: SidebarItem, filter: String, search: String = ""): List<MediaItem> {
        val base = when (sidebar) {
            is SidebarItem.Kind ->
                if (filter == CategoryFilter.ALL) index.byKind[sidebar.kind].orEmpty()
                else index.byCategory[filter].orEmpty()
            SidebarItem.Favorites ->
                ContentKind.entries.flatMap { kind -> index.byKind[kind].orEmpty().filter { it.id in favorites } }
            SidebarItem.Recents -> recents.mapNotNull { index.byId[it] }
        }
        val kind = ContentKind.entries.firstOrNull { it.key == filter }
        val filtered = if (sidebar !is SidebarItem.Kind && kind != null) base.filter { it.kind == kind } else base
        val query = search.trim().folded()
        if (query.isEmpty()) return filtered
        val keys = index.searchKeys
        return filtered.filter { keys[it.id]?.contains(query) == true }
    }

    fun categoryName(item: MediaItem): String? = library?.categories?.firstOrNull { it.id == item.categoryId }?.name

    // Favorites & recents

    fun isFavorite(item: MediaItem): Boolean = item.id in favorites

    fun toggleFavorite(item: MediaItem) {
        favorites = if (item.id in favorites) favorites - item.id else favorites + item.id
        prefs.edit { putStringSet("favorites", favorites) }
    }

    fun markRecent(item: MediaItem) {
        recents = (listOf(item.id) + recents.filter { it != item.id }).take(MAX_RECENTS)
        prefs.edit { putString("recents", recents.joinToString("\n")) }
    }

    // Xtream extras

    private val xtreamClient: XtreamClient?
        get() {
            val source = selectedSource ?: return null
            val kind = source.kind as? SourceKind.Xtream ?: return null
            return XtreamClient(getApplication(), kind.server, kind.username, kind.password, source.id)
        }

    /** Kept for the session, so coming back from the player doesn't refetch the episodes. */
    private val seriesInfoCache = mutableMapOf<String, SeriesInfo>()

    suspend fun seriesInfo(item: MediaItem): SeriesInfo? {
        seriesInfoCache[item.id]?.let { return it }
        val client = xtreamClient ?: return null
        val id = item.xtreamId ?: return null
        return client.seriesInfo(id).also { seriesInfoCache[item.id] = it }
    }

    private companion object {
        const val HOUR = 3600 * 1000L
        const val CACHE_MAX_AGE = 12 * HOUR
        const val GUIDE_CACHE_AGE = 6 * HOUR
        const val GUIDE_RELOAD = 3 * HOUR
        const val CLOCK_TICK = 30_000L
        const val MAX_RECENTS = 50
    }
}
