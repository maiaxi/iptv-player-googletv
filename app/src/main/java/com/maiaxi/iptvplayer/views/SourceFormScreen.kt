package com.maiaxi.iptvplayer.views

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.maiaxi.iptvplayer.R
import com.maiaxi.iptvplayer.models.Source
import com.maiaxi.iptvplayer.models.SourceDraft
import com.maiaxi.iptvplayer.services.LibraryStore
import com.maiaxi.iptvplayer.services.RemoteSetupServer
import com.maiaxi.iptvplayer.services.userMessage
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException

/** Adds or edits a source. With `welcome`, it's the first screen, shown while there are no sources. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SourceFormScreen(library: LibraryStore, source: Source?, welcome: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember(source) { mutableStateOf(source?.let(SourceDraft::from) ?: SourceDraft()) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    // Coming down from the last field, land on Add rather than whichever button is closest.
    val toSave = if (draft.isValid) saveFocus else FocusRequester.Default

    var receivedFromPhone by remember { mutableStateOf(false) }
    val remote = remember {
        val main = Handler(Looper.getMainLooper())
        RemoteSetupServer(context.applicationContext) { received ->
            main.post {
                draft = received
                error = null
                receivedFromPhone = true
            }
        }
    }
    var remoteAddress by remember { mutableStateOf<String?>(null) }
    DisposableEffect(remote) {
        remoteAddress = try {
            remote.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
            remote.address
        } catch (e: IOException) {
            null
        }
        onDispose { remote.stop() }
    }

    LaunchedEffect(Unit) { firstFocus.focusAfterLayout() }
    LaunchedEffect(receivedFromPhone, draft) { if (receivedFromPhone && draft.isValid) saveFocus.focusAfterLayout() }

    fun save() {
        error = null
        checking = true
        scope.launch {
            try {
                draft.save(source, library, context)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.userMessage(context)
            } finally {
                checking = false
            }
        }
    }

    Row(Modifier.fillMaxSize().padding(horizontal = SafeHorizontal, vertical = SafeVertical)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .width(560.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val title = when {
                    welcome -> R.string.welcome
                    source == null -> R.string.add_source
                    else -> R.string.edit_source
                }
                Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
                if (welcome) Hint(stringResource(R.string.welcome_message))
                Hint(stringResource(R.string.stored_on_tv))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((mode, label) in listOf(
                    SourceDraft.Mode.XTREAM to stringResource(R.string.xtream_codes),
                    SourceDraft.Mode.M3U to stringResource(R.string.m3u_playlist),
                )) {
                    val selected = draft.mode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { draft = draft.copy(mode = mode) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                        modifier = if (selected) Modifier.focusRequester(firstFocus) else Modifier,
                    ) { Text(label) }
                }
            }

            val namePlaceholder = if (draft.mode == SourceDraft.Mode.XTREAM) R.string.my_tv else R.string.playlist
            TvTextField(draft.name, { draft = draft.copy(name = it) }, label = stringResource(R.string.name), placeholder = stringResource(namePlaceholder))

            when (draft.mode) {
                SourceDraft.Mode.XTREAM -> {
                    TvTextField(
                        draft.server, { draft = draft.copy(server = it) }, label = stringResource(R.string.server),
                        placeholder = "http://server.com:8080", keyboardType = KeyboardType.Uri,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TvTextField(
                            draft.username, { draft = draft.copy(username = it) }, label = stringResource(R.string.username),
                            down = toSave, modifier = Modifier.weight(1f),
                        )
                        TvTextField(
                            draft.password, { draft = draft.copy(password = it) }, label = stringResource(R.string.password),
                            keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, password = true,
                            down = toSave, modifier = Modifier.weight(1f),
                        )
                    }
                    Hint(stringResource(R.string.details_from_provider))
                }
                SourceDraft.Mode.M3U -> {
                    TvTextField(
                        draft.m3uAddress, { draft = draft.copy(m3uAddress = it) }, label = stringResource(R.string.address),
                        placeholder = "https://…", keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done,
                        down = toSave,
                    )
                    Hint(stringResource(R.string.xtream_conversion_hint))
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
            if (receivedFromPhone && error == null) {
                Text(stringResource(R.string.remote_received), color = MaterialTheme.colorScheme.primary)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = ::save, enabled = draft.isValid && !checking, modifier = Modifier.focusRequester(saveFocus)) {
                    Text(stringResource(if (source == null) R.string.add else R.string.save))
                }
                if (!welcome) {
                    OutlinedButton(onClick = onDone) { Text(stringResource(R.string.cancel)) }
                }
                if (source != null) {
                    // Asks for confirmation by changing the label on the first press.
                    OutlinedButton(onClick = {
                        if (confirmRemove) {
                            library.remove(source)
                            onDone()
                        } else {
                            confirmRemove = true
                        }
                    }) {
                        Text(
                            if (confirmRemove) stringResource(R.string.remove_confirm, source.name)
                            else stringResource(R.string.remove),
                        )
                    }
                }
                if (checking) {
                    Text(
                        stringResource(R.string.checking),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 10.dp),
                    )
                }
            }
        }
        remoteAddress?.let {
            Spacer(Modifier.width(40.dp))
            PhonePanel(it)
        }
    }
}

@Composable
private fun PhonePanel(address: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(220.dp).padding(top = 8.dp)) {
        Text(stringResource(R.string.remote_title), style = MaterialTheme.typography.titleMedium)
        Hint(stringResource(R.string.remote_hint))
        QrCode(address, Modifier.size(168.dp).clip(RoundedCornerShape(8.dp)))
        Text(address.removePrefix("http://"), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
