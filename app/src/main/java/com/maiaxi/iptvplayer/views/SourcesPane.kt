package com.maiaxi.iptvplayer.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maiaxi.iptvplayer.R
import com.maiaxi.iptvplayer.models.Source
import com.maiaxi.iptvplayer.services.LibraryStore
import com.maiaxi.iptvplayer.services.Updater
import java.text.DateFormat
import java.util.Date

/** OK selects a source and holding OK edits it. */
@Composable
fun SourcesPane(library: LibraryStore, updater: Updater, onAdd: () -> Unit, onEdit: (Source) -> Unit) {
    Row {
        LazyColumn(
            modifier = Modifier.width(560.dp).fillMaxHeight().focusRestorer(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(library.sources, key = { it.id }) { source ->
                val selected = source.id == library.selectedSourceId
                ListItem(
                    selected = selected,
                    onClick = { library.select(source.id) },
                    onLongClick = { onEdit(source) },
                    headlineContent = { Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(source.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = if (selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
        Spacer(Modifier.width(40.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 8.dp)) {
            IconButton(Icons.Filled.Add, stringResource(R.string.add_source), onAdd)
            IconButton(Icons.Filled.Refresh, stringResource(R.string.refresh_library), library::refresh)
            Text(
                statusLine(library),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.hold_to_edit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            UpdateSection(updater)
        }
    }
}

@Composable
private fun statusLine(library: LibraryStore): String {
    if (library.isLoading) return stringResource(R.string.loading)
    library.loadError?.let { return it }
    val expires = library.library?.account?.expiresAt
        ?: return library.selectedSource?.subtitle.orEmpty()
    return stringResource(R.string.expires, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expires)))
}

@Composable
private fun UpdateSection(updater: Updater) {
    val state = updater.state
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 24.dp)) {
        Text(
            stringResource(R.string.version_n, updater.currentVersion),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state) {
            is Updater.State.Available ->
                IconButton(Icons.Filled.Refresh, stringResource(R.string.update_to, state.version), updater::install)
            Updater.State.Downloading, Updater.State.Checking -> Text(
                stringResource(if (state == Updater.State.Downloading) R.string.update_downloading else R.string.checking),
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> OutlinedButton(onClick = { updater.check() }) { Text(stringResource(R.string.check_for_updates)) }
        }
        val message = when (state) {
            Updater.State.UpToDate -> stringResource(R.string.update_up_to_date)
            is Updater.State.Failed -> state.message
            else -> null
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
