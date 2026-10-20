package com.maiaxi.iptvplayer.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maiaxi.iptvplayer.R
import com.maiaxi.iptvplayer.views.SafeHorizontal
import com.maiaxi.iptvplayer.views.SafeVertical
import com.maiaxi.iptvplayer.views.focusAfterLayout
import java.util.Locale

private class TrackOption(val label: String, val selected: Boolean, val select: () -> Unit)

/** "Audio and subtitles": a panel on the right listing the stream's tracks, applied as overrides. */
@Composable
fun TrackPanel(player: Player, tracks: Tracks) {
    val context = LocalContext.current
    val audio = options(player, tracks, C.TRACK_TYPE_AUDIO) { format, n -> audioLabel(format, context.getString(R.string.track_n, n)) }
    val text = options(player, tracks, C.TRACK_TYPE_TEXT) { format, n -> textLabel(format, context.getString(R.string.track_n, n)) }
    val textOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) || text.none { it.selected }
    val subtitles = listOf(
        TrackOption(stringResource(R.string.off), textOff) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        },
    ) + text.map { TrackOption(it.label, it.selected && !textOff, it.select) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.focusAfterLayout() }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = SafeVertical),
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
                .background(Color(0xE6101418))
                .padding(end = SafeHorizontal - 20.dp),
        ) {
            item { Header(stringResource(R.string.audio_and_subtitles), MaterialTheme.typography.titleLarge) }
            if (audio.isNotEmpty()) {
                item { Header(stringResource(R.string.audio)) }
                audio.forEachIndexed { i, option ->
                    item { OptionRow(option, if (i == 0) Modifier.focusRequester(firstFocus) else Modifier) }
                }
            }
            item { Header(stringResource(R.string.subtitles)) }
            subtitles.forEachIndexed { i, option ->
                item { OptionRow(option, if (audio.isEmpty() && i == 0) Modifier.focusRequester(firstFocus) else Modifier) }
            }
        }
    }
}

@Composable
private fun Header(title: String, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge) {
    Text(title, style = style, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.padding(top = 14.dp, bottom = 6.dp, start = 12.dp))
}

@Composable
private fun OptionRow(option: TrackOption, modifier: Modifier) {
    DenseListItem(
        selected = option.selected,
        onClick = option.select,
        headlineContent = { Text(option.label, maxLines = 1) },
        trailingContent = if (option.selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
        modifier = modifier,
    )
}

/** One option per playable track of [type]. Selecting one overrides the automatic choice. */
private fun options(player: Player, tracks: Tracks, type: Int, label: (Format, Int) -> String): List<TrackOption> {
    var n = 0
    return tracks.groups.filter { it.type == type }.flatMap { group ->
        // Include tracks that exceed the decoder's declared capabilities: ExoPlayer plays them
        // anyway, and some boxes (Xiaomi TV Box S) declare their only audio track that way.
        (0 until group.length).filter { group.isTrackSupported(it, true) }.map { i ->
            n++
            TrackOption(label(group.getTrackFormat(i), n), group.isTrackSelected(i)) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(type, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                    .build()
            }
        }
    }
}

private fun language(format: Format): String? =
    format.language?.takeIf { it != "und" }?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()) }
        ?.replaceFirstChar { it.titlecase() }

private fun textLabel(format: Format, fallback: String): String =
    listOfNotNull(format.label, language(format)).distinct().joinToString(" · ").ifEmpty { fallback }

/** "English · AC3 5.1", so the user can tell apart tracks that only differ in codec or channels. */
private fun audioLabel(format: Format, fallback: String): String {
    val codec = format.sampleMimeType?.substringAfter('/')?.uppercase()?.replace("MP4A-LATM", "AAC")?.replace("MP4A", "AAC")
    val channels = when (format.channelCount) {
        Format.NO_VALUE, 2 -> null
        1 -> "mono"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${format.channelCount}ch"
    }
    val tech = listOfNotNull(codec, channels).joinToString(" ").ifEmpty { null }
    return listOfNotNull(textLabel(format, fallback), tech).joinToString(" · ")
}
