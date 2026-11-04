package com.kdevs.livo.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kdevs.livo.Aspect
import com.kdevs.livo.AppSettings
import com.kdevs.livo.BufferSize
import com.kdevs.livo.Quality
import com.kdevs.livo.R
import com.kdevs.livo.data.Channel
import com.kdevs.livo.data.DEFAULT_USER_AGENT
import com.kdevs.livo.data.originOf
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

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channel: Channel, isFav: Boolean, settings: AppSettings,
    onBack: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit, onTune: (Int) -> Boolean, onFav: () -> Unit,
    onFailed: () -> Unit, onPlaying: () -> Unit,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val failed by rememberUpdatedState(onFailed)
    val played by rememberUpdatedState(onPlaying)
    val retryFirst by rememberUpdatedState(settings.retryBeforeHiding)
    val tune by rememberUpdatedState(onTune)

    // Number keys: type a channel number, it opens after a short pause (or press OK to go right away).
    var typed by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }
    fun commitTyped() {
        val n = typed.toIntOrNull()
        typed = ""
        if (n != null && n > 0 && !tune(n)) toast = "No channel $n"
    }
    LaunchedEffect(typed) {
        if (typed.isNotEmpty()) { delay(1800); commitTyped() }
    }
    LaunchedEffect(toast) {
        if (toast != null) { delay(1500); toast = null }
    }
    val http = remember {
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setUserAgent(DEFAULT_USER_AGENT)
    }
    val exo = remember {
        // min buffer, max buffer, buffer to start playing, buffer to resume after a stall (ms)
        val b = when (settings.buffer) {
            BufferSize.Low -> intArrayOf(10_000, 30_000, 1_500, 3_000)
            BufferSize.Medium -> intArrayOf(15_000, 50_000, 2_500, 5_000)
            BufferSize.High -> intArrayOf(30_000, 90_000, 5_000, 10_000)
        }
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(b[0], b[1], b[2], b[3]).build())
            .build()
    }
    val retries = remember { intArrayOf(0) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(true) }
    var audio by remember { mutableStateOf<List<Tracks.Group>>(emptyList()) }
    var overlay by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    val root = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
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
                if (state == Player.STATE_READY) { error = false; retries[0] = 0; played() }
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { playing = playWhenReady }
            override fun onPlayerError(e: PlaybackException) {
                // One quiet retry first: a network blip must not hide a good channel.
                if (retryFirst && retries[0] < 1) {
                    retries[0]++
                    buffering = true
                    exo.seekToDefaultPosition()
                    exo.prepare()
                    exo.playWhenReady = true
                } else {
                    error = true; failed()
                }
            }
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

    // Video quality cap from Settings (Auto = no cap).
    LaunchedEffect(settings.quality) {
        val b = exo.trackSelectionParameters.buildUpon()
        when (settings.quality) {
            Quality.Auto -> b.clearVideoSizeConstraints()
            Quality.P1080 -> b.setMaxVideoSize(1920, 1080)
            Quality.P720 -> b.setMaxVideoSize(1280, 720)
            Quality.P480 -> b.setMaxVideoSize(854, 480)
        }
        exo.trackSelectionParameters = b.build()
    }

    LaunchedEffect(channel.id) {
        error = false; buffering = true; audio = emptyList(); overlay = true; retries[0] = 0
        // Some streams only play with the headers their playlist entry names.
        http.setUserAgent(channel.userAgent ?: DEFAULT_USER_AGENT)
        val headers = HashMap<String, String>()
        channel.referrer?.let { ref ->
            headers["Referer"] = ref
            originOf(ref)?.let { headers["Origin"] = it }
        }
        http.setDefaultRequestProperties(headers)
        exo.setMediaItem(MediaItem.fromUri(channel.url))
        exo.prepare(); exo.playWhenReady = true
    }
    LaunchedEffect(overlay, tick, error, playing) { if (overlay && !error && playing) { delay(4000); overlay = false } }
    LaunchedEffect(error) { if (error) overlay = true }
    LaunchedEffect(overlay) { if (!overlay) navMode = false }
    LaunchedEffect(navMode) {
        if (navMode) { delay(60); runCatching { playFocus.requestFocus() } }
        else runCatching { root.requestFocus() }
    }

    fun togglePlay() {
        if (exo.playWhenReady) exo.playWhenReady = false
        else { exo.seekToDefaultPosition(); exo.playWhenReady = true }
        tick++
    }

    fun cycleAudio() {
        if (audio.size < 2) return
        val cur = audio.indexOfFirst { it.isSelected }
        val next = audio[(cur + 1) % audio.size]
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(next.mediaTrackGroup, 0)).build()
        tick++
    }
    val fallbackLabel = channel.languages.firstOrNull() ?: stringResource(R.string.audio)
    val audioLabel = audio.firstOrNull { it.isSelected }?.let { g ->
        val f = g.getTrackFormat(0)
        f.label ?: f.language?.let { Locale.forLanguageTag(it).displayLanguage }
    } ?: fallbackLabel

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(root)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false
                else {
                    if (overlay) tick++ // keep the controls on screen while the user is navigating
                    val digit = digitOf(e.key)
                    if (digit != null) {
                        if (typed.length < 4) typed += digit
                        toast = null
                        return@onPreviewKeyEvent true
                    }
                    if (typed.isNotEmpty() && (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter)) {
                        commitTyped()
                        return@onPreviewKeyEvent true
                    }
                    when (e.key) {
                        // CH+/CH- always switch channel.
                        Key.ChannelUp -> { onNext(); true }
                        Key.ChannelDown -> { onPrev(); true }
                        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { togglePlay(); true }
                        // With the controls closed, any D-pad key opens them.
                        // Once open, Up/Down/Left/Right move between the buttons and OK presses one.
                        Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
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
            update = { v ->
                v.resizeMode = when (settings.aspect) {
                    Aspect.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    Aspect.Fill -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    Aspect.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (buffering && !error) CircularProgressIndicator(Modifier.align(Alignment.Center), color = LivoColors.Gold)
        (typed.ifEmpty { toast })?.let { shown ->
            Text(
                shown, color = LivoColors.Gold, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
        if (error) {
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 24.dp).padding(bottom = 200.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.player_error_title), color = LivoColors.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.player_error_body), color = LivoColors.InkSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        androidx.compose.animation.AnimatedVisibility(overlay, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                // Top: back, channel, language, favorite
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopStart)
                        .background(Brush.verticalGradient(listOf(LivoColors.Scrim, Color.Transparent)))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RoundButton(Icons.Rounded.ArrowBack, stringResource(R.string.back), onBack, size = 44.dp)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveDot(); Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.live), color = LivoColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(channel.name, color = LivoColors.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    PillButton(audioLabel, Icons.Rounded.Translate, false, { cycleAudio() })
                    RoundButton(
                        if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        stringResource(R.string.cd_favorite), { onFav(); tick++ }, size = 44.dp, primary = isFav,
                    )
                }
                // Center: previous, play/pause, next
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    RoundButton(Icons.Rounded.SkipPrevious, stringResource(R.string.previous), { onPrev(); tick++ }, size = 56.dp)
                    RoundButton(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (playing) R.string.pause else R.string.play), { togglePlay() },
                        Modifier.focusRequester(playFocus), size = 76.dp, primary = true,
                    )
                    RoundButton(Icons.Rounded.SkipNext, stringResource(R.string.next), { onNext(); tick++ }, size = 56.dp)
                }
            }
        }
    }
}

private fun digitOf(k: Key): Char? = when (k) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}