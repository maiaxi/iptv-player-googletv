package com.maiaxi.iptvplayer.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maiaxi.iptvplayer.R
import com.maiaxi.iptvplayer.models.ContentKind
import com.maiaxi.iptvplayer.models.EpgEntry
import com.maiaxi.iptvplayer.models.MediaItem

private val cardShape = RoundedCornerShape(12.dp)

/** OK opens the item and holding OK toggles the favorite. */
@Composable
fun MediaCard(
    item: MediaItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    guide: GuideLine? = null,
) {
    val live = item.kind == ContentKind.LIVE
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.fillMaxWidth().aspectRatio(if (live) 16f / 9f else 2f / 3f),
            shape = CardDefaults.shape(cardShape),
            colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            scale = CardDefaults.scale(focusedScale = 1.06f),
            border = CardDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), shape = cardShape),
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                Artwork(item, live)
                if (isFavorite) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .padding(5.dp)
                            .size(16.dp),
                    )
                }
                item.rating?.let { rating ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                        Text(
                            " $rating",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }
        Text(
            item.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            minLines = if (live) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
        guide?.let { NowPlayingLine(it.program, it.clock) }
    }
}

/** What a live card shows under its name while a guide is loaded. */
class GuideLine(val program: EpgEntry?, val clock: Long)

/** "What's on now" under a channel name: programme title plus a thin progress bar. */
@Composable
fun NowPlayingLine(program: EpgEntry?, clock: Long, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        Text(
            program?.title ?: stringResource(R.string.no_information),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (program == null) 0.5f else 1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Reserve the bar's height even without a programme, so grid rows stay aligned.
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (program == null) 0f else 0.15f), RoundedCornerShape(50)),
        ) {
            if (program != null) {
                Box(
                    Modifier
                        .fillMaxWidth(program.progress(clock))
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                )
            }
        }
    }
}

@Composable
private fun Artwork(item: MediaItem, live: Boolean) {
    var failed by remember(item.logo) { mutableStateOf(false) }
    if (item.logo == null || failed) {
        Monogram(item.name)
    } else {
        AsyncImage(
            model = item.logo,
            contentDescription = null,
            contentScale = if (live) ContentScale.Fit else ContentScale.Crop,
            onError = { failed = true },
            modifier = Modifier.fillMaxSize().padding(if (live) 12.dp else 0.dp),
        )
    }
}

/** Placeholder artwork: a soft gradient derived from the name, with initials. */
@Composable
fun Monogram(name: String, modifier: Modifier = Modifier) {
    val hue = ((name.hashCode() and 0xFFFF) % 360).toFloat()
    val initials = name.replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .split(' ').filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.substring(0, it.offsetByCodePoints(0, 1)) }
        .uppercase()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color.hsv(hue, 0.45f, 0.55f), Color.hsv((hue + 29) % 360, 0.55f, 0.35f))),
        ),
    ) {
        Text(initials, color = Color.White.copy(alpha = 0.9f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
