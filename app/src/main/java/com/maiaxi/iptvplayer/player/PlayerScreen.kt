package com.maiaxi.iptvplayer.player

import android.net.Uri
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maiaxi.iptvplayer.R
import com.maiaxi.iptvplayer.app.MainActivity
import com.maiaxi.iptvplayer.models.ContentKind
import com.maiaxi.iptvplayer.models.EpgEntry
import com.maiaxi.iptvplayer.models.MediaItem
import com.maiaxi.iptvplayer.services.Http
import com.maiaxi.iptvplayer.services.LibraryStore
import com.maiaxi.iptvplayer.views.SafeHorizontal
import com.maiaxi.iptvplayer.views.SafeVertical
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import androidx.media3.common.MediaItem as ExoMediaItem

private const val MAX_RETRIES = 3
private const val RETRY_DELAY_MS = 2000L
private const val OVERLAY_MS = 4000L
private const val EPG_REFRESH_MS = 5 * 60 * 1000L
private val MANIFEST_ERRORS = setOf(
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
)
private val TRACK_KEYS = setOf(KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_CAPTIONS, KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK)

/**
 * Plays [item]. Live channels have no controller: ↑/↓ and CH+/CH− switch channel within [queue],
 * OK shows the channel overlay and → opens audio and subtitles. Movies and episodes use
 * PlayerView's controller. Menu opens audio and subtitles for both.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(item: MediaItem, queue: List<MediaItem>, library: LibraryStore, recent: MediaItem? = null) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? MainActivity
    val scope = rememberCoroutineScope()
    val playable = remember(queue) { queue.filter { it.isPlayable } }
    var current by remember { mutableStateOf(item) }
    var error by remember { mutableStateOf<String?>(null) }
    var overlayShownAt by remember { mutableIntStateOf(0) }
    var overlayVisible by remember { mutableStateOf(false) }
    var retries by remember { mutableIntStateOf(0) }
    var retryJob by remember { mutableStateOf<Job?>(null) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var controllerVisible by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var tracksOpen by remember { mutableStateOf(false) }
    var reconnecting by remember { mutableStateOf(false) }
    var hlsFallback by remember { mutableStateOf(false) }
    var hlsTried by remember { mutableStateOf(false) }
    val live = current.kind == ContentKind.LIVE
    val index = playable.indexOfFirst { it.id == current.id }

    val player = remember {
        // Falls back to another decoder when the preferred one fails to start, which TV chips do
        // with some AC3/EAC3 and MPEG-2 streams.
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderers).build().apply { playWhenReady = true }
    }

    fun load(target: MediaItem) {
        retryJob?.cancel()
        player.setMediaSource(mediaSource(target, forceHls = hlsFallback))
        player.prepare()
        player.play()
    }

    fun switchTo(target: MediaItem) {
        current = target
        error = null
        retries = 0
        reconnecting = false
        hlsFallback = false
        hlsTried = false
        overlayShownAt++
    }

    fun step(delta: Int) {
        if (playable.isEmpty() || index < 0) return
        switchTo(playable[(index + delta + playable.size) % playable.size])
    }

    LaunchedEffect(current) {
        library.markRecent(recent ?: current)
        load(current)
    }

    var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    LaunchedEffect(current) {
        epg = emptyList()
        if (current.kind != ContentKind.LIVE) return@LaunchedEffect
        while (true) {
            epg = library.epg(current)
            delay(EPG_REFRESH_MS)
        }
    }

    LaunchedEffect(overlayShownAt) {
        overlayVisible = true
        delay(OVERLAY_MS)
        overlayVisible = false
    }

    val currentState by rememberUpdatedState(current)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    retries = 0
                    reconnecting = false
                }
                val item = currentState
                if (state == Player.STATE_ENDED && item.kind == ContentKind.SERIES) {
                    val i = playable.indexOfFirst { it.id == item.id }
                    if (i >= 0 && i + 1 < playable.size) switchTo(playable[i + 1])
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                val item = currentState
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                    player.prepare()
                    return
                }
                // The type is guessed from the path, and some panels redirect `.ts` addresses to
                // HLS playlists, so try HLS once before giving up on the container.
                if (e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED && !hlsTried) {
                    hlsTried = true
                    hlsFallback = true
                    load(item)
                    return
                }
                // Not a playlist either. Panels also answer with junk while the previous channel's
                // connection still counts against the account's limit, so retry as the original type.
                if (hlsFallback && e.errorCode in MANIFEST_ERRORS) hlsFallback = false
                when {
                    // Live streams drop all the time; reconnect quietly a few times before giving up.
                    item.kind == ContentKind.LIVE && retries < MAX_RETRIES -> {
                        retries++
                        reconnecting = true
                        retryJob = scope.launch {
                            delay(RETRY_DELAY_MS)
                            load(item)
                        }
                    }
                    else -> {
                        reconnecting = false
                        error = context.getString(R.string.playback_error, e.errorCodeName)
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Stop when the app goes to the background; live channels rejoin the live edge on return.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_START -> {
                    if (currentState.kind == ContentKind.LIVE) player.seekToDefaultPosition()
                    player.play()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Back hides the controller before it closes the player. Back arrives through the
    // OnBackInvokedDispatcher on API 33+, not as a key event, so keyHandler can't see it.
    BackHandler(enabled = controllerVisible) { playerView?.hideController() }
    BackHandler(enabled = tracksOpen) { tracksOpen = false }

    val keyHandler by rememberUpdatedState { event: KeyEvent ->
        when {
            // The panel is ordinary Compose UI, so the D-pad moves focus within it.
            tracksOpen -> false
            event.keyCode in TRACK_KEYS || (live && event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    playerView?.hideController()
                    tracksOpen = true
                }
                true
            }
            live -> handleLiveKey(event, onStep = ::step, onInfo = { overlayShownAt++ })
            else -> false
        }
    }
    DisposableEffect(activity) {
        activity?.keyHandler = { keyHandler(it) }
        onDispose { activity?.keyHandler = null }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).apply {
                    this.player = player
                    keepScreenOn = true
                    isFocusable = true
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { controllerVisible = it == android.view.View.VISIBLE },
                    )
                    playerView = this
                }
            },
            update = { view ->
                view.useController = !live
                if (!live && !tracksOpen) view.requestFocus()
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (live) {
            AnimatedVisibility(
                visible = overlayVisible || error != null || reconnecting,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart),
            ) {
                ChannelOverlay(
                    number = index + 1,
                    item = current,
                    category = library.categoryName(current),
                    epg = epg,
                    clock = library.clock,
                    reconnecting = reconnecting,
                )
            }
        }

        if (tracksOpen) TrackPanel(player, tracks)

        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    }
}

private fun handleLiveKey(event: KeyEvent, onStep: (Int) -> Unit, onInfo: () -> Unit): Boolean {
    val action: (() -> Unit)? = when (event.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_DOWN -> { { onStep(-1) } }
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_UP -> { { onStep(1) } }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO -> onInfo
        else -> null
    }
    if (action == null) return false
    if (event.action == KeyEvent.ACTION_DOWN) action()
    return true
}

@OptIn(UnstableApi::class)
private fun mediaSource(item: MediaItem, forceHls: Boolean) = run {
    val url = item.streamUrl.orEmpty()
    val dataSource = OkHttpDataSource.Factory(Http.streamClient)
        .setUserAgent(item.userAgent)
        .setDefaultRequestProperties(item.referrer?.let { mapOf("Referer" to it) } ?: emptyMap())
    val builder = ExoMediaItem.Builder().setUri(url)
    // `.ts` streams are detected as progressive, but HLS needs the hint when the path says so.
    if (forceHls || Uri.parse(url).path.orEmpty().lowercase().endsWith(".m3u8")) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
    DefaultMediaSourceFactory(dataSource).createMediaSource(builder.build())
}

@Composable
private fun ChannelOverlay(
    number: Int,
    item: MediaItem,
    category: String?,
    epg: List<EpgEntry>,
    clock: Long,
    reconnecting: Boolean,
) {
    val now = epg.firstOrNull { it.start <= clock && it.end > clock }
    val next = now?.let { n -> epg.firstOrNull { it.start >= n.end } }
    val timeFormat = DateFormat.getTimeFormat(LocalContext.current)
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(horizontal = SafeHorizontal, vertical = SafeVertical)
            .padding(top = 48.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (number > 0) {
                Text(
                    "$number",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                category?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                }
                if (reconnecting) {
                    Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyLarge, color = Color.White)
                }
                if (now != null) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            now.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${timeFormat.format(Date(now.start))} – ${timeFormat.format(Date(now.end))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                    Box(
                        Modifier
                            .padding(vertical = 6.dp)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(50)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(now.progress(clock))
                                .height(4.dp)
                                .background(Color.White, RoundedCornerShape(50)),
                        )
                    }
                    next?.let {
                        Text(
                            stringResource(R.string.up_next, it.title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
