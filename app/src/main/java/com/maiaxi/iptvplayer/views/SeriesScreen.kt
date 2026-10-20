package com.maiaxi.iptvplayer.views

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maiaxi.iptvplayer.R
import com.maiaxi.iptvplayer.models.Episode
import com.maiaxi.iptvplayer.models.MediaItem
import com.maiaxi.iptvplayer.models.SeriesInfo
import com.maiaxi.iptvplayer.models.item
import com.maiaxi.iptvplayer.services.AppError
import com.maiaxi.iptvplayer.services.LibraryStore
import com.maiaxi.iptvplayer.services.userMessage
import kotlinx.coroutines.CancellationException

/** Cover and details on the left; seasons (selected on focus) and episodes on the right. */
@Composable
fun SeriesScreen(
    series: MediaItem,
    library: LibraryStore,
    onPlay: (item: MediaItem, queue: List<MediaItem>) -> Unit,
) {
    val context = LocalContext.current
    var info by remember { mutableStateOf<SeriesInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var season by rememberSaveable { mutableIntStateOf(-1) }
    val playFocus = remember { FocusRequester() }
    // → from the buttons enters the seasons, or the episodes, instead of the nearest row.
    val contentFocus = remember { FocusRequester() }
    var lastPlayed by rememberSaveable { mutableStateOf<String?>(null) }
    val lastPlayedFocus = remember { FocusRequester() }

    LaunchedEffect(series.id) {
        try {
            val loaded = library.seriesInfo(series) ?: throw AppError(R.string.error_series)
            info = loaded
            if (season !in loaded.episodesBySeason) season = loaded.episodesBySeason.keys.minOrNull() ?: 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.userMessage(context)
        }
    }
    // Back from the player lands on the episode that was played.
    LaunchedEffect(info != null) {
        if (info == null) return@LaunchedEffect
        if (lastPlayed == null || !lastPlayedFocus.focusAfterLayout()) playFocus.focusAfterLayout()
    }

    val seasons = info?.episodesBySeason?.keys?.sorted().orEmpty()
    val episodes = info?.episodesBySeason?.get(season).orEmpty()

    fun play(episode: Episode) {
        val queue = episodes.map { it.item(series, context.getString(R.string.episode_title, it.season, it.number, it.title)) }
        lastPlayed = episode.id
        onPlay(queue[episodes.indexOf(episode)], queue)
    }

    Row(Modifier.fillMaxSize().padding(horizontal = SafeHorizontal, vertical = SafeVertical)) {
        Details(
            series, info, library, playFocus, contentFocus,
            canPlay = episodes.isNotEmpty(),
            onPlay = { episodes.firstOrNull()?.let(::play) },
        )
        Spacer(Modifier.width(32.dp))
        Box(Modifier.weight(1f).fillMaxSize()) {
            when {
                info != null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.episodes), style = MaterialTheme.typography.titleLarge)
                    if (seasons.size > 1) SeasonPicker(seasons, season, contentFocus, onSelect = { season = it })
                    if (episodes.isEmpty()) {
                        Text(
                            stringResource(R.string.no_episodes),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    EpisodeList(
                        episodes,
                        fallbackImage = info?.backdrop ?: series.logo,
                        firstFocus = if (seasons.size > 1) null else contentFocus,
                        restore = lastPlayed to lastPlayedFocus,
                        onPlay = ::play,
                    )
                }
                error != null -> Message(stringResource(R.string.couldnt_load_series), error)
                else -> Message(stringResource(R.string.loading))
            }
        }
    }
}

@Composable
private fun Details(
    series: MediaItem,
    info: SeriesInfo?,
    library: LibraryStore,
    playFocus: FocusRequester,
    contentFocus: FocusRequester,
    canPlay: Boolean,
    onPlay: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(300.dp)) {
        Box(Modifier.size(width = 150.dp, height = 225.dp).clip(RoundedCornerShape(12.dp))) {
            val cover = series.logo ?: info?.cover
            Monogram(series.name)
            if (cover != null) {
                AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(series.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val facts = listOfNotNull(
            series.rating?.let { "★ $it" },
            info?.releaseDate?.take(4)?.ifEmpty { null },
            info?.genre,
        )
        if (facts.isNotEmpty()) {
            Text(
                facts.joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        info?.plot?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            Button(onClick = onPlay, enabled = canPlay, modifier = Modifier.focusRequester(playFocus)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.play))
            }
            val favorite = library.isFavorite(series)
            OutlinedButton(
                onClick = { library.toggleFavorite(series) },
                modifier = Modifier.focusProperties { if (canPlay) right = contentFocus },
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (favorite) Color(0xFFFFD54F) else LocalContentColor.current,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (favorite) R.string.in_favorites else R.string.favorite))
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonPicker(seasons: List<Int>, selected: Int, entry: FocusRequester, onSelect: (Int) -> Unit) {
    // Padding leaves room for the focused chip to grow without being clipped.
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        modifier = Modifier.focusRestorer(),
    ) {
        items(seasons) { season ->
            FilterChip(
                selected = season == selected,
                onClick = { onSelect(season) },
                modifier = Modifier
                    .then(if (season == selected) Modifier.focusRequester(entry) else Modifier)
                    .onFocusChanged { if (it.isFocused) onSelect(season) },
            ) { Text(stringResource(R.string.season_n, season)) }
        }
    }
}

@Composable
private fun EpisodeList(
    episodes: List<Episode>,
    fallbackImage: String?,
    firstFocus: FocusRequester?,
    restore: Pair<String?, FocusRequester>,
    onPlay: (Episode) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier.fillMaxWidth().focusRestorer(),
    ) {
        items(episodes, key = { it.id }) { episode ->
            ListItem(
                selected = false,
                onClick = { onPlay(episode) },
                leadingContent = {
                    Box(Modifier.size(width = 112.dp, height = 63.dp).clip(RoundedCornerShape(6.dp))) {
                        Monogram("${episode.number}")
                        (episode.image ?: fallbackImage)?.let {
                            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                },
                headlineContent = {
                    Text("${episode.number}. ${episode.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = episode.plot?.ifEmpty { null }?.let {
                    { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                },
                trailingContent = episode.duration?.ifEmpty { null }?.let {
                    { Text(it, style = MaterialTheme.typography.labelMedium) }
                },
                modifier = Modifier
                    .height(88.dp)
                    .then(if (firstFocus != null && episode == episodes.first()) Modifier.focusRequester(firstFocus) else Modifier)
                    .then(if (episode.id == restore.first) Modifier.focusRequester(restore.second) else Modifier),
            )
        }
    }
}
