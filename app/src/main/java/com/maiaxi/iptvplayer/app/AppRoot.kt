package com.maiaxi.iptvplayer.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maiaxi.iptvplayer.models.MediaItem
import com.maiaxi.iptvplayer.models.Source
import com.maiaxi.iptvplayer.player.PlayerScreen
import com.maiaxi.iptvplayer.services.LibraryStore
import com.maiaxi.iptvplayer.services.Updater
import com.maiaxi.iptvplayer.views.AppTheme
import com.maiaxi.iptvplayer.views.BrowseScreen
import com.maiaxi.iptvplayer.views.SeriesScreen
import com.maiaxi.iptvplayer.views.SourceFormScreen

sealed interface Screen {
    data object Browse : Screen
    data class SourceForm(val source: Source?) : Screen
    data class Series(val item: MediaItem) : Screen
    /** `recent` is what goes in Recents: the series, for an episode. */
    data class Player(val item: MediaItem, val queue: List<MediaItem>, val recent: MediaItem? = null) : Screen
}

@Composable
fun AppRoot(library: LibraryStore = viewModel(), updater: Updater = viewModel()) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Browse) }
    // Keeps each screen's selection, scroll and last-opened item while another screen is open.
    val saveableState = rememberSaveableStateHolder()

    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    AppTheme {
        if (library.sources.isEmpty()) {
            SourceFormScreen(library, source = null, welcome = true, onDone = {})
            return@AppTheme
        }
        when (val screen = stack.last()) {
            Screen.Browse -> saveableState.SaveableStateProvider("browse") {
                BrowseScreen(
                    library,
                    updater,
                    onOpen = { item, queue ->
                        if (item.isPlayable) {
                            stack += Screen.Player(item, queue)
                        } else {
                            stack += Screen.Series(item)
                        }
                    },
                    onAddSource = { stack += Screen.SourceForm(null) },
                    onEditSource = { stack += Screen.SourceForm(it) },
                )
            }
            is Screen.SourceForm -> SourceFormScreen(
                library,
                source = screen.source,
                welcome = false,
                onDone = { stack.removeAt(stack.lastIndex) },
            )
            is Screen.Series -> saveableState.SaveableStateProvider("series:${screen.item.id}") {
                SeriesScreen(screen.item, library, onPlay = { item, queue ->
                    stack += Screen.Player(item, queue, recent = screen.item)
                })
            }
            is Screen.Player -> PlayerScreen(screen.item, screen.queue, library, screen.recent)
        }
    }
}

