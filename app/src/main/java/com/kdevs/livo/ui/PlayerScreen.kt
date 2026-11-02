package com.kdevs.livo.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.kdevs.livo.data.Channel
import kotlinx.coroutines.delay
import java.util.Locale

private fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun PlayerScreen(
    channel: Channel, isFav: Boolean,
    onBack: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit, onFav: () -> Unit, onFailed: () -> Unit,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val failed by rememberUpdatedState(onFailed)
    val exo = remember { ExoPlayer.Builder(ctx).build() }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var audio by remember { mutableStateOf<List<Tracks.Group>>(emptyList()) }
    var overlay by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    val root = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }
    // true while the on-screen controls are being driven with the D-pad / remote
    var navMode by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)
    // First Back press closes the controls; the next one leaves the player.
    BackHandler(enabled = navMode) { navMode = false; overlay = false }

    // Full-screen: hide system bars while watching.
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) error = false
            }
            override fun onPlayerError(e: PlaybackException) { error = true; failed() }
            override fun onTracksChanged(tracks: Tracks) {
                audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener); exo.release() }
    }

    // Stop playback whenever the app leaves the foreground (Home, screen off, another app),
    // and restart at the live edge when the user comes back.
    DisposableEffect(exo) {
        val owner = ctx.findActivity() as? LifecycleOwner
        var stopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    stopped = true
                    exo.playWhenReady = false
                    exo.stop()
                }
                Lifecycle.Event.ON_START -> if (stopped) {
                    stopped = false
                    error = false; buffering = true
                    exo.seekToDefaultPosition()
                    exo.prepare()
                    exo.playWhenReady = true
                }
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(channel.url) {
        error = false; buffering = true; audio = emptyList(); overlay = true
        exo.setMediaItem(MediaItem.fromUri(channel.url))
        exo.prepare(); exo.playWhenReady = true
    }
    LaunchedEffect(overlay, tick, error) { if (overlay && !error) { delay(4000); overlay = false } }
    LaunchedEffect(overlay) { if (!overlay) navMode = false }
    LaunchedEffect(navMode) {
        if (navMode) { delay(60); runCatching { nextFocus.requestFocus() } }
        else runCatching { root.requestFocus() }
    }
    // The audio button can appear/disappear when a channel's tracks load; keep focus on a live button.
    LaunchedEffect(audio.size) {
        if (navMode) { delay(60); runCatching { nextFocus.requestFocus() } }
    }

    fun cycleAudio() {
        if (audio.size < 2) return
        val cur = audio.indexOfFirst { it.isSelected }
        val next = audio[(cur + 1) % audio.size]
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(next.mediaTrackGroup, 0)).build()
        tick++
    }
    val audioLabel = audio.firstOrNull { it.isSelected }?.let { g ->
        val f = g.getTrackFormat(0)
        f.label ?: f.language?.let { Locale.forLanguageTag(it).displayLanguage } ?: "Audio"
    } ?: "Audio"

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(root)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false
                else {
                    if (overlay) tick++ // keep the controls on screen while the user is navigating
                    when (e.key) {
                        // CH+/CH- always switch channel.
                        Key.ChannelUp -> { onNext(); true }
                        Key.ChannelDown -> { onPrev(); true }
                        // Up/Down switch channel only while the controls are closed;
                        // with the controls open they move focus between the buttons.
                        Key.DirectionUp -> if (navMode) false else { onNext(); true }
                        Key.DirectionDown -> if (navMode) false else { onPrev(); true }
                        // OK / Left / Right open the controls; once open, the focused button handles them.
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionLeft, Key.DirectionRight ->
                            if (!navMode) { navMode = true; overlay = true; tick++; true } else false
                        else -> false
                    }
                }
            }
            .focusable()
            .pointerInput(Unit) { detectTapGestures { overlay = !overlay; tick++ } },
    ) {
        AndroidView(
            factory = { c -> PlayerView(c).apply { player = exo; useController = false; keepScreenOn = true } },
            modifier = Modifier.fillMaxSize(),
        )
        if (buffering && !error) CircularProgressIndicator(Modifier.align(Alignment.Center), color = LivoColors.Gold)
        if (error) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("This channel isn't available right now", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("It's hidden from your list until the next refresh. Use Next to keep watching.", color = Color(0xFFCFD6D7), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        androidx.compose.animation.AnimatedVisibility(overlay, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopStart)
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillButton("Back", Icons.Rounded.ArrowBack, false, onBack)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveDot(); Spacer(Modifier.width(6.dp))
                            Text("LIVE", color = LivoColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(channel.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            "${channel.category} · ${channel.languages.joinToString(", ").ifBlank { "Other" }}",
                            color = Color(0xFFCFD6D7),
                            fontSize = 12.sp
                        )
                    }
                    FavButton(isFav, { onFav(); tick++ })
                }
                Row(
                    Modifier.align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                ) {
                    PillButton("Previous", Icons.Rounded.SkipPrevious, false, { onPrev(); tick++ })
                    PillButton("Next", Icons.Rounded.SkipNext, true, { onNext(); tick++ }, Modifier.focusRequester(nextFocus))
                    if (audio.size > 1) PillButton(audioLabel, Icons.Rounded.Translate, false, { cycleAudio() })
                }
            }
        }
    }
}