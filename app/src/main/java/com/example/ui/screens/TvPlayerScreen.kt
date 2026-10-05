package com.example.ui.screens

import android.app.Activity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.window.Dialog
import androidx.media3.common.C
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ui.viewmodel.IptvViewModel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ui.theme.TvAccentGold
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBorder
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvSurfaceHighlight
import com.example.ui.theme.TvTextMuted
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ResizeModeChoice(val label: String, val mode: Int) {
    FIT("تناسب الشاشة", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("ملء كامل", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("تكبير (Zoom)", AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
}

@OptIn(UnstableApi::class)
@Composable
fun TvPlayerScreen(
    title: String,
    streamUrl: String,
    isLive: Boolean = false,
    channelNumber: Int? = null,
    epgInfo: String? = null,
    frequencyInfo: String? = null,
    viewModel: IptvViewModel? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Keep TV screen awake during video playback
    val activity = context as? Activity
    androidx.compose.runtime.DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val currentLiveChannel by (viewModel?.selectedLiveChannel ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val epgMap by (viewModel?.epgMap ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap())).collectAsState()
    val liveChannelEpg = currentLiveChannel?.let { epgMap[it.streamId] }
    val liveProgramTitle = liveChannelEpg?.currentProgram?.title

    val currentTitle = if (isLive && currentLiveChannel != null) currentLiveChannel!!.name else title
    val currentChannelNumber = if (isLive && currentLiveChannel != null) currentLiveChannel!!.num else channelNumber
    val currentEpgInfo = if (isLive && currentLiveChannel != null) (liveProgramTitle ?: currentLiveChannel!!.epgChannelId ?: "البث الحي المباشر") else epgInfo
    val resolvedStreamUrl = if (isLive && currentLiveChannel != null) {
        viewModel?.getLiveStreamUrl(currentLiveChannel!!) ?: streamUrl
    } else {
        streamUrl
    }

    // Double-click OK detection state (for VOD play/pause)
    var lastOkPressTime by remember { mutableLongStateOf(0L) }
    val doubleClickThreshold = 400L

    var isBuffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var showBottomDetails by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    // Live TV Receiver-style states
    var liveOverlayVisible by remember { mutableStateOf(true) }
    var numberInputBuffer by remember { mutableStateOf("") }
    var autoHideJob by remember { mutableStateOf<Job?>(null) }
    var numberTuneJob by remember { mutableStateOf<Job?>(null) }

    fun showLiveReceiverBanner() {
        liveOverlayVisible = true
        autoHideJob?.cancel()
        autoHideJob = coroutineScope.launch {
            delay(3500)
            liveOverlayVisible = false
        }
    }

    LaunchedEffect(Unit) {
        if (isLive) {
            showLiveReceiverBanner()
        }
    }

    // Playback Speed (0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x) for Movies
    val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    var currentSpeedIndex by remember { mutableIntStateOf(2) } // default 1.0f

    // Resize Mode
    var currentResizeModeIndex by remember { mutableIntStateOf(0) }
    val resizeModes = listOf(
        ResizeModeChoice.FIT,
        ResizeModeChoice.FILL,
        ResizeModeChoice.ZOOM
    )

    // Current Time for Live OSD Clock (only runs when overlays are visible to avoid unnecessary recompositions)
    var currentTimeString by remember { mutableStateOf("") }
    LaunchedEffect(showControls || showBottomDetails) {
        if (showControls || showBottomDetails) {
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            while (true) {
                currentTimeString = timeFormat.format(Date())
                delay(1000)
            }
        }
    }

    // Auto-hide controls & bottom details for VOD (appear only on movement)
    LaunchedEffect(showControls, isLive) {
        if (!isLive && showControls) {
            delay(3500)
            showControls = false
        }
    }

    LaunchedEffect(showBottomDetails, isLive) {
        if (!isLive && showBottomDetails) {
            delay(3500)
            showBottomDetails = false
        }
    }

    // 27. Audio & Subtitles Dialog State
    var showTrackDialog by remember { mutableStateOf(false) }
    var selectedAudioTrack by remember { mutableStateOf<String?>(null) }
    var selectedSubtitleTrack by remember { mutableStateOf<String?>("Off") }

    // 28. Sleep Timer State
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var sleepTimerRemainingSec by remember { mutableIntStateOf(0) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sleepTimerMinutes) {
        if (sleepTimerMinutes > 0) {
            sleepTimerRemainingSec = sleepTimerMinutes * 60
            while (sleepTimerRemainingSec > 0) {
                delay(1000)
                sleepTimerRemainingSec--
            }
            onBack()
        }
    }

    // 29. Catch-up / Timeshift State
    var showTimeshiftDialog by remember { mutableStateOf(false) }
    var timeshiftOffsetHours by remember { mutableIntStateOf(0) }

    var dynamicVideoInfo by remember { mutableStateOf<String?>(frequencyInfo) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var activeStreamUrl by remember { mutableStateOf(resolvedStreamUrl) }
    var fallbackAttempted by remember(activeStreamUrl) { mutableStateOf(false) }
    var retryCount by remember { mutableIntStateOf(0) }

    val currentUserAgentSetting by (viewModel?.currentUserAgent ?: kotlinx.coroutines.flow.MutableStateFlow("default")).collectAsState()
    val activeSession by (viewModel?.activeAccount ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val resolvedUserAgent = remember(activeSession, currentUserAgentSetting) {
        val chosen = activeSession?.userAgent?.takeIf { it.isNotBlank() } ?: currentUserAgentSetting
        when (chosen.lowercase(Locale.ROOT)) {
            "vlc" -> "VLC/3.0.18 LibVLC/3.0.18"
            "exoplayer" -> "MluonaIPTV/1.0 (Android TV; ExoPlayer)"
            "default", "" -> "MluonaIPTV/1.0 (Android TV; Mobile)"
            else -> chosen
        }
    }

    val vodResumePrefs = remember { context.getSharedPreferences("vod_resume_pos", android.content.Context.MODE_PRIVATE) }
    val resumeKey = remember(activeStreamUrl, activeSession) {
        val accId = activeSession?.id ?: "global"
        val urlKey = (activeStreamUrl.hashCode() and 0x7FFFFFFF).toString()
        "pos_${accId}_$urlKey"
    }
    var resumeAttempted by remember(activeStreamUrl) { mutableStateOf(false) }

    LaunchedEffect(resolvedStreamUrl) {
        if (resolvedStreamUrl.isNotBlank() && resolvedStreamUrl != activeStreamUrl) {
            activeStreamUrl = resolvedStreamUrl
            dynamicVideoInfo = frequencyInfo
            fallbackAttempted = false
            retryCount = 0
            resumeAttempted = false
            playerError = null
            isBuffering = true
            showBottomDetails = true
            showControls = true
            if (isLive) {
                showLiveReceiverBanner()
            }
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15000,
                /* maxBufferMs = */ 50000,
                /* bufferForPlaybackMs = */ 1500,
                /* bufferForPlaybackAfterRebufferMs = */ 3000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(5000, false)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(resolvedUserAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)
            .setKeepPostFor302Redirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to resolvedUserAgent,
                    "Accept" to "*/*",
                    "Connection" to "keep-alive"
                )
            )
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
                val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()
                setAudioAttributes(audioAttributes, true)
            }
    }

    var wasPlayingBeforeStop by remember { mutableStateOf(false) }

    // Stop playback when activity moves to background or stops, resume only if was playing
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                wasPlayingBeforeStop = exoPlayer.playWhenReady
                exoPlayer.pause()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START && wasPlayingBeforeStop) {
                if (isLive) {
                    exoPlayer.seekToDefaultPosition()
                }
                exoPlayer.play()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(activeStreamUrl) {
        if (activeStreamUrl.isNotBlank()) {
            isBuffering = true
            playerError = null
            exoPlayer.stop()
            val mediaItem = MediaItem.fromUri(activeStreamUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    // Update playback speed when changed
    LaunchedEffect(currentSpeedIndex) {
        val speed = speedOptions[currentSpeedIndex]
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    // Update aspect ratio / resize mode when changed
    LaunchedEffect(currentResizeModeIndex, playerViewRef) {
        playerViewRef?.resizeMode = resizeModes[currentResizeModeIndex].mode
    }

    val currentAudioType by (viewModel?.audioType ?: kotlinx.coroutines.flow.MutableStateFlow("auto")).collectAsState()
    LaunchedEffect(currentAudioType, exoPlayer) {
        val params = exoPlayer.trackSelectionParameters.buildUpon()
        when (currentAudioType) {
            "stereo" -> {
                params.setMaxAudioChannelCount(2)
            }
            "surround" -> {
                params.setMaxAudioChannelCount(8)
                params.setPreferredAudioMimeTypes("audio/eac3", "audio/ac3", "audio/ac4", "audio/true-hd", "audio/vnd.dts")
            }
            else -> {
                params.setMaxAudioChannelCount(Int.MAX_VALUE)
            }
        }
        exoPlayer.trackSelectionParameters = params.build()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    playerError = null
                    if (!isLive && !resumeAttempted && durationMs > 0) {
                        resumeAttempted = true
                        val savedPos = vodResumePrefs.getLong(resumeKey, 0L)
                        if (savedPos >= 30_000L && savedPos <= (durationMs * 0.95)) {
                            exoPlayer.seekTo(savedPos)
                        }
                    }
                } else if (state == Player.STATE_ENDED) {
                    if (!isLive) {
                        vodResumePrefs.edit().remove(resumeKey).apply()
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val qualityLabel = when {
                        videoSize.height >= 2160 -> "4K UHD (${videoSize.width}x${videoSize.height})"
                        videoSize.height >= 1080 -> "1080p FHD (${videoSize.width}x${videoSize.height})"
                        videoSize.height >= 720 -> "720p HD (${videoSize.width}x${videoSize.height})"
                        else -> "${videoSize.width}x${videoSize.height} SD"
                    }
                    dynamicVideoInfo = qualityLabel
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                if (!fallbackAttempted && isLive) {
                    fallbackAttempted = true
                    if (activeStreamUrl.endsWith(".m3u8", ignoreCase = true)) {
                        activeStreamUrl = activeStreamUrl.replace(".m3u8", ".ts")
                        return
                    } else if (activeStreamUrl.endsWith(".ts", ignoreCase = true)) {
                        activeStreamUrl = activeStreamUrl.replace(".ts", ".m3u8")
                        return
                    }
                }

                if (isLive && retryCount < 3) {
                    retryCount++
                    coroutineScope.launch {
                        delay(2000L * retryCount)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                    return
                }

                var cur: Throwable? = error
                var httpCode: Int? = null
                while (cur != null) {
                    if (cur is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                        httpCode = cur.responseCode
                        break
                    }
                    cur = cur.cause
                }

                playerError = when (httpCode) {
                    401, 403 -> "الحساب غير مصرّح أو تجاوزت عدد الاتصالات المسموحة"
                    404 -> "القناة غير متوفرة حالياً"
                    else -> "تعذر التشغيل، تحقق من الإنترنت"
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            playerViewRef?.player = null
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    // Periodic time update and resume position save (every 10s for VOD)
    LaunchedEffect(isPlaying, isLive) {
        if (!isLive && isPlaying) {
            var saveCounter = 0
            while (isPlaying) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
                saveCounter++
                if (saveCounter >= 10) {
                    saveCounter = 0
                    if (currentPositionMs >= 10_000L && durationMs > 0 && currentPositionMs <= (durationMs * 0.95)) {
                        vodResumePrefs.edit().putLong(resumeKey, currentPositionMs).apply()
                    }
                }
                delay(1000)
            }
        }
    }

    // Handle Remote Back key directly: close channel and exit player immediately
    BackHandler {
        if (isLive && numberInputBuffer.isNotEmpty()) {
            numberTuneJob?.cancel()
            numberInputBuffer = ""
        } else {
            onBack()
        }
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val keyCode = keyEvent.nativeKeyEvent.keyCode

                    if (isLive) {
                        // Live TV: Receiver Behavior
                        when (keyCode) {
                            // Back button: clear number buffer or return
                            KeyEvent.KEYCODE_BACK,
                            KeyEvent.KEYCODE_ESCAPE,
                            KeyEvent.KEYCODE_WINDOW -> {
                                if (numberInputBuffer.isNotEmpty()) {
                                    numberTuneJob?.cancel()
                                    numberInputBuffer = ""
                                    true
                                } else {
                                    onBack()
                                    true
                                }
                            }

                            // OK / DPAD_CENTER: Tune immediately if buffer exists, or toggle receiver banner
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                if (numberInputBuffer.isNotEmpty()) {
                                    numberTuneJob?.cancel()
                                    val num = numberInputBuffer.toIntOrNull()
                                    if (num != null && viewModel != null) {
                                        viewModel.playChannelByNumber(num)
                                    }
                                    numberInputBuffer = ""
                                    showLiveReceiverBanner()
                                } else {
                                    if (liveOverlayVisible) {
                                        liveOverlayVisible = false
                                    } else {
                                        showLiveReceiverBanner()
                                    }
                                }
                                true
                            }

                            // CH+ / Page Up / D-pad Up: Next channel
                            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_UP -> {
                                if (viewModel != null) {
                                    viewModel.playNextLiveChannel()
                                    showLiveReceiverBanner()
                                }
                                true
                            }

                            // CH- / Page Down / D-pad Down: Previous channel
                            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (viewModel != null) {
                                    viewModel.playPreviousLiveChannel()
                                    showLiveReceiverBanner()
                                }
                                true
                            }

                            // Number keys: 0 to 9 on TV remote
                            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4,
                            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9,
                            KeyEvent.KEYCODE_NUMPAD_0, KeyEvent.KEYCODE_NUMPAD_1, KeyEvent.KEYCODE_NUMPAD_2, KeyEvent.KEYCODE_NUMPAD_3,
                            KeyEvent.KEYCODE_NUMPAD_4, KeyEvent.KEYCODE_NUMPAD_5, KeyEvent.KEYCODE_NUMPAD_6, KeyEvent.KEYCODE_NUMPAD_7,
                            KeyEvent.KEYCODE_NUMPAD_8, KeyEvent.KEYCODE_NUMPAD_9 -> {
                                val digit = when (keyCode) {
                                    in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> (keyCode - KeyEvent.KEYCODE_0).toString()
                                    in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> (keyCode - KeyEvent.KEYCODE_NUMPAD_0).toString()
                                    else -> ""
                                }
                                if (digit.isNotEmpty() && numberInputBuffer.length < 5) {
                                    numberInputBuffer += digit
                                    liveOverlayVisible = true
                                    numberTuneJob?.cancel()
                                    numberTuneJob = coroutineScope.launch {
                                        delay(1200)
                                        val targetNum = numberInputBuffer.toIntOrNull()
                                        if (targetNum != null && viewModel != null) {
                                            viewModel.playChannelByNumber(targetNum)
                                        }
                                        numberInputBuffer = ""
                                        showLiveReceiverBanner()
                                    }
                                }
                                true
                            }

                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                exoPlayer.play()
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                exoPlayer.pause()
                                true
                            }
                            else -> false
                        }
                    } else {
                        // VOD / Movies / Series
                        when (keyCode) {
                            KeyEvent.KEYCODE_BACK,
                            KeyEvent.KEYCODE_ESCAPE,
                            KeyEvent.KEYCODE_WINDOW -> {
                                onBack()
                                true
                            }

                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                val now = System.currentTimeMillis()
                                if (now - lastOkPressTime < doubleClickThreshold) {
                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    lastOkPressTime = 0L
                                } else {
                                    lastOkPressTime = now
                                    showBottomDetails = !showBottomDetails
                                    showControls = true
                                }
                                true
                            }

                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0L)
                                exoPlayer.seekTo(newPos)
                                showControls = true
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(durationMs)
                                exoPlayer.seekTo(newPos)
                                showControls = true
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                exoPlayer.play()
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                exoPlayer.pause()
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                exoPlayer.seekTo((exoPlayer.currentPosition + 15000).coerceAtMost(durationMs))
                                showControls = true
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                exoPlayer.seekTo((exoPlayer.currentPosition - 15000).coerceAtLeast(0L))
                                showControls = true
                                true
                            }
                            else -> false
                        }
                    }
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (isLive) {
                    if (liveOverlayVisible) {
                        liveOverlayVisible = false
                    } else {
                        showLiveReceiverBanner()
                    }
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastOkPressTime < doubleClickThreshold) {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        lastOkPressTime = 0L
                    } else {
                        lastOkPressTime = now
                        showControls = !showControls
                        if (showControls) showBottomDetails = true
                    }
                }
            }
            .testTag("tv_player_screen")
    ) {
        // Video View with PlayerView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = resizeModes[currentResizeModeIndex].mode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    playerViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering Indicator - only appears on movement/interaction or initial tune
        if (isBuffering && playerError == null && (liveOverlayVisible || showControls)) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF00E676),
                    modifier = Modifier.size(42.dp),
                    strokeWidth = 3.dp
                )
            }
        }

        // Pause Indicator - appears only on movement/interaction
        if (!isPlaying && !isBuffering && playerError == null && (liveOverlayVisible || showControls)) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "توقف",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Error message overlay
        if (playerError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = playerError ?: "",
                        color = Color(0xFFFF6B6B),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    var isRetryFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .onFocusChanged { isRetryFocused = it.isFocused }
                            .focusable()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isRetryFocused) Color(0xFF00E676) else Color(0xFF00E676).copy(alpha = 0.85f))
                            .border(
                                width = if (isRetryFocused) 2.dp else 0.dp,
                                color = if (isRetryFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                playerError = null
                                isBuffering = true
                                val mediaItem = MediaItem.fromUri(activeStreamUrl)
                                exoPlayer.setMediaItem(mediaItem)
                                exoPlayer.prepare()
                                exoPlayer.play()
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "إعادة المحاولة",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // =========================================================================
        // LIVE TV OVERLAY (RECEIVER STYLE: Simple, top right back, center bottom bar)
        // =========================================================================
        if (isLive) {
            // 1. Top Right Back Button ("وخيار الرجوع في اعلى اليمين")
            AnimatedVisibility(
                visible = liveOverlayVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = 24.dp)
            ) {
                val backSource = remember { MutableInteractionSource() }
                val isBackFocused by backSource.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isBackFocused) Color(0xFF00E676) else Color.Black.copy(alpha = 0.55f))
                        .border(
                            width = 1.dp,
                            color = if (isBackFocused) Color.White else Color.White.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable(interactionSource = backSource, indication = null) {
                            onBack()
                        }
                        .focusable(interactionSource = backSource)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("btn_live_back_top_right"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "رجوع",
                            color = if (isBackFocused) Color.Black else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = if (isBackFocused) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 2. Channel Number Input Indicator (when typing digits on remote: e.g. "12")
            if (numberInputBuffer.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 28.dp, top = 24.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .border(1.5.dp, Color(0xFF00E676), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "CH: $numberInputBuffer -",
                        color = Color(0xFF00E676),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // 3. Small semi-transparent center bottom bar containing channel number and name merged
            // ("بالنسبة للشريط السفلي للمشغل ادمج معه رقم قناة")
            val liveChannelsList by (viewModel?.liveChannels ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsState()
            val computedIndex = remember(currentLiveChannel, liveChannelsList) {
                val idx = liveChannelsList.indexOfFirst { it.streamId == currentLiveChannel?.streamId }
                if (idx >= 0) idx + 1 else null
            }
            val displayChannelNum = currentChannelNumber ?: computedIndex

            AnimatedVisibility(
                visible = liveOverlayVisible,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
            ) {
                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.60f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .testTag("live_receiver_channel_bar")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (displayChannelNum != null && displayChannelNum > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF00E676).copy(alpha = 0.22f))
                                    .border(1.dp, Color(0xFF00E676).copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$displayChannelNum",
                                    color = Color(0xFF00E676),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        Text(
                            text = currentTitle,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!liveProgramTitle.isNullOrBlank()) {
                            Text(
                                text = "•",
                                color = Color(0xFF00E676),
                                fontSize = 16.sp
                            )
                            Text(
                                text = liveProgramTitle,
                                color = Color(0xFFA5D6A7),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!dynamicVideoInfo.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = dynamicVideoInfo ?: "",
                                    color = Color(0xFFE0E0E0),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // VOD / MOVIES / SERIES OVERLAYS (Full controls, seekbar, speed, etc.)
        // =========================================================================
        if (!isLive) {
            // Top Header Bar: Back button (Top Left) & Aspect Ratio/Zoom (Top Right)
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.88f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top Left: Back Button + Title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val backSource = remember { MutableInteractionSource() }
                            val isBackFocused by backSource.collectIsFocusedAsState()

                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isBackFocused) TvAccentGold else TvSurface.copy(alpha = 0.85f))
                                    .border(1.dp, if (isBackFocused) TvAccentGold else TvBorder, CircleShape)
                                    .clickable(
                                        interactionSource = backSource,
                                        indication = null
                                    ) { onBack() }
                                    .focusable(interactionSource = backSource)
                                    .testTag("btn_player_back"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "رجوع",
                                    tint = if (isBackFocused) TvBackground else TvTextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Text(
                                text = currentTitle,
                                color = TvTextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Top Right: Zoom & Speed selector
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val speedSource = remember { MutableInteractionSource() }
                            val isSpeedFocused by speedSource.collectIsFocusedAsState()

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSpeedFocused) TvAccentGold else TvSurface.copy(alpha = 0.85f))
                                    .border(1.dp, if (isSpeedFocused) TvAccentGold else TvBorder, RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = speedSource,
                                        indication = null
                                    ) {
                                        currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
                                    }
                                    .focusable(interactionSource = speedSource)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("btn_player_speed"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "سرعة التشغيل",
                                        tint = if (isSpeedFocused) TvBackground else TvAccentGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${speedOptions[currentSpeedIndex]}x",
                                        color = if (isSpeedFocused) TvBackground else TvTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            val zoomSource = remember { MutableInteractionSource() }
                            val isZoomFocused by zoomSource.collectIsFocusedAsState()

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isZoomFocused) TvAccentGold else TvSurface.copy(alpha = 0.85f))
                                    .border(1.dp, if (isZoomFocused) TvAccentGold else TvBorder, RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = zoomSource,
                                        indication = null
                                    ) {
                                        currentResizeModeIndex = (currentResizeModeIndex + 1) % resizeModes.size
                                    }
                                    .focusable(interactionSource = zoomSource)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("btn_player_zoom"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AspectRatio,
                                        contentDescription = "تكبير الشاشة",
                                        tint = if (isZoomFocused) TvBackground else TvAccentGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = resizeModes[currentResizeModeIndex].label,
                                        color = if (isZoomFocused) TvBackground else TvTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 27. Audio & Subtitles Track Button
                            val tracksSource = remember { MutableInteractionSource() }
                            val isTracksFocused by tracksSource.collectIsFocusedAsState()

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isTracksFocused) TvAccentGold else TvSurface.copy(alpha = 0.85f))
                                    .border(1.dp, if (isTracksFocused) TvAccentGold else TvBorder, RoundedCornerShape(8.dp))
                                    .clickable(interactionSource = tracksSource, indication = null) {
                                        showTrackDialog = true
                                    }
                                    .focusable(interactionSource = tracksSource)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("btn_player_tracks"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = "الترجمة والصوت",
                                        tint = if (isTracksFocused) TvBackground else TvAccentGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "الترجمة/الصوت",
                                        color = if (isTracksFocused) TvBackground else TvTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 28. Sleep Timer Button
                            val timerSource = remember { MutableInteractionSource() }
                            val isTimerFocused by timerSource.collectIsFocusedAsState()

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isTimerFocused) TvAccentGold else if (sleepTimerMinutes > 0) Color(0xFF0F3826) else TvSurface.copy(alpha = 0.85f))
                                    .border(1.dp, if (isTimerFocused) TvAccentGold else if (sleepTimerMinutes > 0) Color(0xFF00E676) else TvBorder, RoundedCornerShape(8.dp))
                                    .clickable(interactionSource = timerSource, indication = null) {
                                        showSleepTimerDialog = true
                                    }
                                    .focusable(interactionSource = timerSource)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("btn_player_sleep_timer"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = "مؤقت النوم",
                                        tint = if (isTimerFocused) TvBackground else if (sleepTimerMinutes > 0) Color(0xFF00E676) else TvAccentGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (sleepTimerMinutes > 0) "${sleepTimerRemainingSec / 60}m" else "المؤقت",
                                        color = if (isTimerFocused) TvBackground else TvTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 29. Timeshift / Catch-up Button
                            if (isLive) {
                                val timeshiftSource = remember { MutableInteractionSource() }
                                val isTimeshiftFocused by timeshiftSource.collectIsFocusedAsState()

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isTimeshiftFocused) TvAccentGold else if (timeshiftOffsetHours > 0) Color(0xFF0F3826) else TvSurface.copy(alpha = 0.85f))
                                        .border(1.dp, if (isTimeshiftFocused) TvAccentGold else if (timeshiftOffsetHours > 0) Color(0xFF00E676) else TvBorder, RoundedCornerShape(8.dp))
                                        .clickable(interactionSource = timeshiftSource, indication = null) {
                                            showTimeshiftDialog = true
                                        }
                                        .focusable(interactionSource = timeshiftSource)
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("btn_player_timeshift"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = "إعادة البث Timeshift",
                                            tint = if (isTimeshiftFocused) TvBackground else if (timeshiftOffsetHours > 0) Color(0xFF00E676) else TvAccentGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (timeshiftOffsetHours > 0) "-${timeshiftOffsetHours}h" else "إعادة البث",
                                            color = if (isTimeshiftFocused) TvBackground else TvTextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Compact Dynamic Bottom Controls Bar for VOD
            AnimatedVisibility(
                visible = showBottomDetails,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xE60F141D))
                            .border(1.dp, Color(0xFF263345), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (durationMs > 0) {
                                var sliderPosition by remember { mutableFloatStateOf(0f) }
                                var isDragging by remember { mutableStateOf(false) }

                                val currentSec = (if (isDragging) (sliderPosition * durationMs / 1000).toLong() else currentPositionMs / 1000)
                                val totalSec = durationMs / 1000

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = formatDuration(currentSec),
                                        color = TvAccentGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Slider(
                                        value = if (isDragging) sliderPosition else (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                                        onValueChange = {
                                            isDragging = true
                                            sliderPosition = it
                                        },
                                        onValueChangeFinished = {
                                            isDragging = false
                                            val targetMs = (sliderPosition * durationMs).toLong()
                                            exoPlayer.seekTo(targetMs)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(18.dp),
                                        colors = SliderDefaults.colors(
                                            thumbColor = TvAccentGold,
                                            activeTrackColor = TvAccentGold,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                        )
                                    )

                                    Text(
                                        text = formatDuration(totalSec),
                                        color = TvTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = currentTitle,
                                    color = TvTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    PlayerControlIconButton(
                                        icon = Icons.Default.Replay10,
                                        contentDescription = "رجوع 10 ثواني",
                                        buttonSize = 32,
                                        iconSize = 18,
                                        onClick = {
                                            val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0L)
                                            exoPlayer.seekTo(newPos)
                                        }
                                    )

                                    val playBtnSource = remember { MutableInteractionSource() }
                                    val isPlayBtnFocused by playBtnSource.collectIsFocusedAsState()

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isPlayBtnFocused) Color(0xFFFFD54F) else TvAccentGold)
                                            .border(2.dp, if (isPlayBtnFocused) Color.White else Color.Transparent, CircleShape)
                                            .clickable(interactionSource = playBtnSource, indication = null) {
                                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                            }
                                            .focusable(interactionSource = playBtnSource)
                                            .testTag("btn_player_toggle_play"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                                            tint = TvBackground,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    PlayerControlIconButton(
                                        icon = Icons.Default.Forward10,
                                        contentDescription = "تقديم 10 ثواني",
                                        buttonSize = 32,
                                        iconSize = 18,
                                        onClick = {
                                            val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(durationMs)
                                            exoPlayer.seekTo(newPos)
                                        }
                                    )
                                }

                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DynamicAudioVisualizer(isPlaying = isPlaying)
                                }
                            }
                        }
                    }
                }
            }

            // 27. Audio & Subtitles Dialog
            if (showTrackDialog) {
                Dialog(onDismissRequest = { showTrackDialog = false }) {
                    Box(
                        modifier = Modifier
                            .width(460.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(TvSurface)
                            .border(1.dp, TvBorder, RoundedCornerShape(16.dp))
                            .padding(24.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("إعدادات الصوت والترجمة", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                            // Audio Track Selection
                            Text("المسار الصوتي (Audio Track):", color = TvAccentGold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            val audioTracks = remember(exoPlayer) {
                                val list = mutableListOf<String>()
                                for (group in exoPlayer.currentTracks.groups) {
                                    if (group.type == C.TRACK_TYPE_AUDIO) {
                                        for (i in 0 until group.length) {
                                            val f = group.getTrackFormat(i)
                                            list.add(f.language ?: f.label ?: "صوت ${list.size + 1}")
                                        }
                                    }
                                }
                                if (list.isEmpty()) listOf("الصوت الافتراضي") else list
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                audioTracks.forEach { track ->
                                    val isSel = selectedAudioTrack == track || (selectedAudioTrack == null && track == audioTracks.first())
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) Color(0xFF00E676).copy(alpha = 0.2f) else TvSurfaceHighlight)
                                            .border(1.dp, if (isSel) Color(0xFF00E676) else TvBorder, RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedAudioTrack = track
                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                                    .setPreferredAudioLanguage(track)
                                                    .build()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(track, color = if (isSel) Color(0xFF00E676) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Subtitle Track Selection
                            Text("الترجمة النصية (Subtitles):", color = TvAccentGold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            val subtitleTracks = remember(exoPlayer) {
                                val list = mutableListOf("إيقاف الترجمة")
                                for (group in exoPlayer.currentTracks.groups) {
                                    if (group.type == C.TRACK_TYPE_TEXT) {
                                        for (i in 0 until group.length) {
                                            val f = group.getTrackFormat(i)
                                            list.add(f.language ?: f.label ?: "ترجمة ${list.size}")
                                        }
                                    }
                                }
                                list
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                subtitleTracks.forEach { sub ->
                                    val isSel = selectedSubtitleTrack == sub
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) Color(0xFF00E676).copy(alpha = 0.2f) else TvSurfaceHighlight)
                                            .border(1.dp, if (isSel) Color(0xFF00E676) else TvBorder, RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedSubtitleTrack = sub
                                                if (sub == "إيقاف الترجمة") {
                                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                        .build()
                                                } else {
                                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                        .setPreferredTextLanguage(sub)
                                                        .build()
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(sub, color = if (isSel) Color(0xFF00E676) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TvSurfaceHighlight)
                                    .clickable { showTrackDialog = false }
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("تم وحفظ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 28. Sleep Timer Dialog
            if (showSleepTimerDialog) {
                Dialog(onDismissRequest = { showSleepTimerDialog = false }) {
                    Box(
                        modifier = Modifier
                            .width(420.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(TvSurface)
                            .border(1.dp, TvBorder, RoundedCornerShape(16.dp))
                            .padding(24.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("مؤقت النوم (Sleep Timer)", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("سيتم إيقاف التشغيل والرجوع للشاشة الرئيسية تلقائياً بعد:", color = TvTextSecondary, fontSize = 13.sp)

                            val timerOptions = listOf(0 to "إيقاف المؤقت", 15 to "15 دقيقة", 30 to "30 دقيقة", 60 to "60 دقيقة", 90 to "90 دقيقة", 120 to "120 دقيقة")
                            timerOptions.forEach { (mins, label) ->
                                val isSel = sleepTimerMinutes == mins
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) Color(0xFF00E676).copy(alpha = 0.2f) else TvSurfaceHighlight)
                                        .border(1.dp, if (isSel) Color(0xFF00E676) else TvBorder, RoundedCornerShape(8.dp))
                                        .clickable {
                                            sleepTimerMinutes = mins
                                            showSleepTimerDialog = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, color = if (isSel) Color.White else TvTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        if (isSel) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 29. Catch-up / Timeshift Dialog
            if (showTimeshiftDialog) {
                Dialog(onDismissRequest = { showTimeshiftDialog = false }) {
                    Box(
                        modifier = Modifier
                            .width(420.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(TvSurface)
                            .border(1.dp, TvBorder, RoundedCornerShape(16.dp))
                            .padding(24.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("خدمة إعادة البث (Timeshift / Catch-up)", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("اختر نقطة البث السابقة للرجوع إليها:", color = TvTextSecondary, fontSize = 13.sp)

                            val timeshiftOptions = listOf(
                                0 to "البث الحي المباشر (Live)",
                                1 to "الرجوع ساعة للوراء (-1h)",
                                2 to "الرجوع ساعتين للوراء (-2h)",
                                4 to "الرجوع 4 ساعات للوراء (-4h)"
                            )

                            timeshiftOptions.forEach { (hours, label) ->
                                val isSel = timeshiftOffsetHours == hours
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) Color(0xFF00E676).copy(alpha = 0.2f) else TvSurfaceHighlight)
                                        .border(1.dp, if (isSel) Color(0xFF00E676) else TvBorder, RoundedCornerShape(8.dp))
                                        .clickable {
                                            timeshiftOffsetHours = hours
                                            showTimeshiftDialog = false
                                            if (hours > 0) {
                                                val currentCh = currentLiveChannel
                                                val session = activeSession
                                                if (currentCh != null && session != null && session.type == com.example.data.model.AccountType.XTREAM) {
                                                    val server = session.serverUrl.trimEnd('/')
                                                    val startEpoch = (System.currentTimeMillis() / 1000) - (hours * 3600L)
                                                    val timeshiftUrl = "$server/timeshift/${session.username}/${session.password}/60/$startEpoch/${currentCh.streamId}.ts"
                                                    activeStreamUrl = timeshiftUrl
                                                } else {
                                                    val seekTarget = (exoPlayer.currentPosition - hours * 3600_000L).coerceAtLeast(0L)
                                                    exoPlayer.seekTo(seekTarget)
                                                }
                                            } else {
                                                activeStreamUrl = resolvedStreamUrl
                                                exoPlayer.seekToDefaultPosition()
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, color = if (isSel) Color.White else TvTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        if (isSel) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicAudioVisualizer(isPlaying: Boolean, tint: Color = TvAccentGold) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h3"
    )

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        modifier = Modifier.height(16.dp)
    ) {
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(if (isPlaying) h1.dp else 4.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(tint)
        )
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(if (isPlaying) h2.dp else 7.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(tint)
        )
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(if (isPlaying) h3.dp else 5.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(tint)
        )
    }
}

@Composable
private fun PlayerControlIconButton(
    icon: ImageVector,
    contentDescription: String,
    buttonSize: Int = 34,
    iconSize: Int = 18,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .size(buttonSize.dp)
            .clip(CircleShape)
            .background(if (isFocused) TvAccentGold else TvSurface.copy(alpha = 0.85f))
            .border(1.dp, if (isFocused) Color.White else TvBorder, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) TvBackground else TvTextPrimary,
            modifier = Modifier.size(iconSize.dp)
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }
}
