package com.maiaxi.iptvplayer.views

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.maiaxi.iptvplayer.R
import com.maiaxi.iptvplayer.models.ContentKind
import com.maiaxi.iptvplayer.models.MediaItem
import com.maiaxi.iptvplayer.models.Source
import com.maiaxi.iptvplayer.services.CategoryFilter
import com.maiaxi.iptvplayer.services.LibraryStore
import com.maiaxi.iptvplayer.services.SidebarItem
import com.maiaxi.iptvplayer.services.Updater

private enum class BrowseTab(@param:StringRes val title: Int, val sidebar: SidebarItem?) {
    LIVE(R.string.live_tv, SidebarItem.Kind(ContentKind.LIVE)),
    MOVIES(R.string.movies, SidebarItem.Kind(ContentKind.MOVIE)),
    SERIES(R.string.series, SidebarItem.Kind(ContentKind.SERIES)),
    FAVORITES(R.string.favorites, SidebarItem.Favorites),
    RECENTS(R.string.recents, SidebarItem.Recents),
    SOURCES(R.string.sources, null),
}

@Composable
fun BrowseScreen(
    library: LibraryStore,
    updater: Updater,
    onOpen: (MediaItem, List<MediaItem>) -> Unit,
    onAddSource: () -> Unit,
    onEditSource: (Source) -> Unit,
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastOpened by rememberSaveable { mutableStateOf<String?>(null) }
    // Not saveable: true each time the screen comes back, so focus returns to the card just opened.
    var restorePending by remember { mutableStateOf(lastOpened != null) }
    val tab = BrowseTab.entries[tabIndex]
    val tabFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (!restorePending || tab.sidebar == null) tabFocus.focusAfterLayout()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = SafeHorizontal, vertical = SafeVertical)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TabRow(selectedTabIndex = tabIndex, modifier = Modifier.focusRestorer(tabFocus)) {
                BrowseTab.entries.forEachIndexed { index, t ->
                    Tab(
                        selected = index == tabIndex,
                        onFocus = { tabIndex = index },
                        modifier = if (index == tabIndex) Modifier.focusRequester(tabFocus) else Modifier,
                    ) {
                        Text(
                            stringResource(t.title),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            (updater.state as? Updater.State.Available)?.let {
                Button(onClick = updater::install, modifier = Modifier.padding(end = 16.dp)) {
                    Text(stringResource(R.string.update_to, it.version))
                }
            }
            Text(
                if (library.isLoading) stringResource(R.string.loading) else library.selectedSource?.name.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(20.dp))

        val sidebar = tab.sidebar
        if (sidebar == null) {
            SourcesPane(library, updater, onAddSource, onEditSource)
        } else {
            LibraryPane(
                library,
                sidebar,
                restoreId = if (restorePending) lastOpened else null,
                onRestored = { restorePending = false },
                fallbackFocus = tabFocus,
                onOpen = { item, queue ->
                    lastOpened = item.id
                    onOpen(item, queue)
                },
            )
        }
    }
}

private class CategoryEntry(val id: String, val name: String, val count: Int)

@Composable
private fun LibraryPane(
    library: LibraryStore,
    sidebar: SidebarItem,
    restoreId: String?,
    onRestored: () -> Unit,
    fallbackFocus: FocusRequester,
    onOpen: (MediaItem, List<MediaItem>) -> Unit,
) {
    var filter by rememberSaveable(sidebar, library.selectedSourceId) { mutableStateOf(CategoryFilter.ALL) }
    var search by rememberSaveable(sidebar, library.selectedSourceId) { mutableStateOf("") }
    val data = library.library
    val items = remember(data, library.favorites, library.recents, sidebar, filter, search) {
        library.items(sidebar, filter, search)
    }
    val restoreFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (restoreId != null) {
            if (!restoreFocus.focusAfterLayout()) fallbackFocus.focusAfterLayout()
            onRestored()
        }
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvTextField(
                search,
                { search = it },
                placeholder = stringResource(R.string.search),
                leadingIcon = Icons.Filled.Search,
                imeAction = ImeAction.Search,
            )
            CategoryList(library, sidebar, filter, onSelect = { filter = it }, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.width(24.dp))
        Box(Modifier.weight(1f).fillMaxSize()) {
            when {
                items.isNotEmpty() -> ItemGrid(library, items, sidebar, filter, search, restoreId, restoreFocus, onOpen)
                library.isLoading && data == null -> Message(stringResource(R.string.loading_library))
                library.loadError != null && data == null -> Message(
                    stringResource(R.string.couldnt_load),
                    library.loadError,
                    action = stringResource(R.string.try_again) to library::refresh,
                )
                search.isNotBlank() -> Message(stringResource(R.string.no_results, search.trim()))
                sidebar == SidebarItem.Favorites ->
                    Message(stringResource(R.string.no_favorites), stringResource(R.string.no_favorites_hint))
                sidebar == SidebarItem.Recents ->
                    Message(stringResource(R.string.nothing_watched), stringResource(R.string.nothing_watched_hint))
                else -> Message(stringResource(R.string.no_content))
            }
        }
    }
}

@Composable
private fun CategoryList(
    library: LibraryStore,
    sidebar: SidebarItem,
    filter: String,
    onSelect: (String) -> Unit,
    modifier: Modifier,
) {
    val data = library.library
    val all = stringResource(R.string.all)
    val kindTitles = ContentKind.entries.associateWith { stringResource(it.title) }
    val entries = remember(data, library.favorites, library.recents, sidebar, all) {
        when (sidebar) {
            is SidebarItem.Kind -> listOf(CategoryEntry(CategoryFilter.ALL, all, library.count(sidebar.kind))) +
                library.categories(sidebar.kind).map { CategoryEntry(it.id, it.name, library.count(it)) }
            else -> listOf(CategoryEntry(CategoryFilter.ALL, all, library.items(sidebar, CategoryFilter.ALL).size)) +
                ContentKind.entries.map { CategoryEntry(it.key, kindTitles.getValue(it), library.items(sidebar, it.key).size) }
        }
    }
    LazyColumn(
        modifier = modifier.focusRestorer(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            DenseListItem(
                selected = entry.id == filter,
                onClick = { onSelect(entry.id) },
                headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingContent = { Text("${entry.count}", style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.onFocusChanged { if (it.isFocused) onSelect(entry.id) },
            )
        }
    }
}

@Composable
private fun ItemGrid(
    library: LibraryStore,
    items: List<MediaItem>,
    sidebar: SidebarItem,
    filter: String,
    search: String,
    restoreId: String?,
    restoreFocus: FocusRequester,
    onOpen: (MediaItem, List<MediaItem>) -> Unit,
) {
    val state = rememberSaveable(sidebar, filter, search, library.selectedSourceId, saver = LazyGridState.Saver) { LazyGridState() }
    val allLive = items.all { it.kind == ContentKind.LIVE }
    val hasGuide = library.guide.isNotEmpty()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (allLive) 130.dp else 100.dp),
        state = state,
        // Room for the focused card to grow without being clipped.
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize().focusRestorer(),
    ) {
        // No keys: some panels list the same stream twice, and duplicate keys crash lazy layouts.
        items(items) { item ->
            MediaCard(
                item = item,
                isFavorite = item.id in library.favorites,
                onClick = { onOpen(item, items) },
                onLongClick = { library.toggleFavorite(item) },
                modifier = if (item.id == restoreId) Modifier.focusRequester(restoreFocus) else Modifier,
                guide = if (hasGuide && item.kind == ContentKind.LIVE) GuideLine(library.currentProgram(item), library.clock) else null,
            )
        }
    }
}

@Composable
fun Message(title: String, detail: String? = null, action: Pair<String, () -> Unit>? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.7f),
            )
        }
        if (action != null) {
            Button(onClick = action.second, modifier = Modifier.padding(top = 8.dp)) { Text(action.first) }
        }
    }
}
