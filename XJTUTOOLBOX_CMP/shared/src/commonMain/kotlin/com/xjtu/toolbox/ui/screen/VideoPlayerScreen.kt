package com.xjtu.toolbox.ui.screen

import androidx.compose.runtime.*
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.*
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.xjtu.toolbox.ui.miuix.*
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xjtu.toolbox.LocalNavigation
import com.xjtu.toolbox.classreplay.*
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.ui.components.ErrorState
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.util.Logger
import kotlinx.coroutines.launch

private const val TAG = "VideoPlayer"

/** 画面模式 */
enum class DisplayMode(val label: String) {
    SINGLE("单画面"), DUAL("双画面")
}

/** 单画面时选哪个视频源 */
enum class VideoSource(val label: String) {
    INSTRUCTOR("教师直播"), ENCODER("电脑屏幕")
}

/** 音频选择 */
enum class AudioSource(val label: String) {
    INSTRUCTOR("教师音频"), ENCODER("电脑音频"), BOTH("双音轨"), MUTE("静音")
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    else "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

/**
 * 视频播放器 (KuiklyUI 跨平台版)
 *
 * 加载回放数据 → 显示视频信息 + 控制面板。
 * 实际视频渲染需通过 expect/actual 桥接各平台原生播放器。
 */
@Composable
fun VideoPlayerScreen() {
    val nav = LocalNavigation.current
    val activityId = nav.routeArgs["activityId"] as? Int ?: 0
    val scope = rememberCoroutineScope()

    var replayDetail by remember { mutableStateOf<ReplayDetail?>(null) }
    var instructorUrl by remember { mutableStateOf<String?>(null) }
    var encoderUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // 播放控制状态
    var displayMode by remember { mutableStateOf(DisplayMode.SINGLE) }
    var videoSource by remember { mutableStateOf(VideoSource.ENCODER) }
    var audioSource by remember { mutableStateOf(AudioSource.ENCODER) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    // TODO: 数据加载需要 ClassLogin 实例，此处演示结构
    // 实际集成时应从 CredentialStore/SessionManager 获取已登录的 ClassLogin
    LaunchedEffect(activityId) {
        if (activityId == 0) {
            errorMsg = "无效的活动 ID"
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        errorMsg = null
        try {
            // TODO: 获取 ClassLogin 实例
            // val login = SessionManager.getClassLogin() ?: throw Exception("未登录课程平台")
            // val detail = fetchReplayDetail(login, activityId)
            // replayDetail = detail
            // if (detail == null || detail.replayVideos.isEmpty()) { errorMsg = "未找到回放视频"; return@LaunchedEffect }
            // val instrVid = detail.replayVideos.find { it.cameraType == "instructor" }
            // val encVid = detail.replayVideos.find { it.cameraType == "encoder" }
            // instructorUrl = instrVid?.let { resolveVideoUrl(login, it.url) }
            // encoderUrl = encVid?.let { resolveVideoUrl(login, it.url) }
            errorMsg = "需要先登录课程平台（TronClass）"
        } catch (e: Exception) {
            Logger.e(TAG, "load replay error", e)
            errorMsg = "加载失败: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    val hasInstructor = instructorUrl != null
    val hasEncoder = encoderUrl != null
    val hasBoth = hasInstructor && hasEncoder

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = replayDetail?.title ?: "视频播放",
                navigationIcon = { BackButton { nav.goBack() } }
            )
        }
    ) { padding ->
        when {
            isLoading -> LoadingState(message = "加载回放...", modifier = Modifier.fillMaxSize().padding(padding))
            errorMsg != null -> ErrorState(
                message = errorMsg ?: "",
                onRetry = {
                    // TODO: retry logic
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            else -> {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    // ── 视频区域 ──
                    Box(
                        Modifier.fillMaxWidth().weight(1f).background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        // TODO: expect/actual PlatformVideoPlayer composable
                        // PlatformVideoPlayer(instructorUrl, encoderUrl, displayMode, videoSource, ...)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MiuixText("视频播放区域", color = Color.White, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            if (hasInstructor || hasEncoder) {
                                MiuixText(
                                    buildString {
                                        if (hasInstructor) append("教师: ✓  ")
                                        if (hasEncoder) append("屏幕: ✓")
                                    },
                                    color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp
                                )
                            } else {
                                MiuixText("等待视频流...", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                            }
                        }
                    }

                    // ── 进度条 ──
                    if (duration > 0) {
                        MiuixLinearProgressIndicator(
                            progress = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            MiuixText(formatTime(currentPosition), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Spacer(Modifier.weight(1f))
                            MiuixText(formatTime(duration), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }

                    // ── 控制栏 ──
                    VideoControlBar(
                        isPlaying = isPlaying,
                        playbackSpeed = playbackSpeed,
                        displayMode = displayMode,
                        videoSource = videoSource,
                        audioSource = audioSource,
                        hasBoth = hasBoth,
                        onPlayPause = {
                            isPlaying = !isPlaying
                            // TODO: player.play/pause
                        },
                        onSeekBack = {
                            currentPosition = (currentPosition - 10_000).coerceAtLeast(0)
                            // TODO: player.seekTo
                        },
                        onSeekForward = {
                            currentPosition = (currentPosition + 10_000).coerceAtMost(duration)
                            // TODO: player.seekTo
                        },
                        onSpeedChange = { playbackSpeed = it },
                        onDisplayModeChange = { displayMode = it },
                        onVideoSourceChange = { videoSource = it },
                        onAudioSourceChange = { audioSource = it }
                    )

                    // ── 视频信息 ──
                    replayDetail?.let { detail ->
                        VideoInfoPanel(detail)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoControlBar(
    isPlaying: Boolean,
    playbackSpeed: Float,
    displayMode: DisplayMode,
    videoSource: VideoSource,
    audioSource: AudioSource,
    hasBoth: Boolean,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onDisplayModeChange: (DisplayMode) -> Unit,
    onVideoSourceChange: (VideoSource) -> Unit,
    onAudioSourceChange: (AudioSource) -> Unit
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSourceMenu by remember { mutableStateOf(false) }

    MiuixSurface {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            // 播放控制行
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiuixTextButton(text = "< 10s", onClick = onSeekBack)
                Spacer(Modifier.width(16.dp))
                MiuixButton(onClick = onPlayPause) {
                    MiuixText(if (isPlaying) "暂停" else "播放")
                }
                Spacer(Modifier.width(16.dp))
                MiuixTextButton(text = "10s >", onClick = onSeekForward)
            }

            // 选项行
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 倍速切换
                MiuixTextButton(text = "${playbackSpeed}x", onClick = {
                    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
                    val idx = speeds.indexOf(playbackSpeed)
                    val next = speeds[(idx + 1) % speeds.size]
                    onSpeedChange(next)
                })

                // 画面模式
                if (hasBoth) {
                    MiuixTextButton(text = displayMode.label, onClick = {
                        onDisplayModeChange(if (displayMode == DisplayMode.SINGLE) DisplayMode.DUAL else DisplayMode.SINGLE)
                    })
                }

                // 视频源切换（单画面时）
                if (hasBoth && displayMode == DisplayMode.SINGLE) {
                    MiuixTextButton(text = videoSource.label, onClick = {
                        onVideoSourceChange(if (videoSource == VideoSource.INSTRUCTOR) VideoSource.ENCODER else VideoSource.INSTRUCTOR)
                    })
                }

                // 音频源循环切换
                if (hasBoth) {
                    MiuixTextButton(text = audioSource.label, onClick = {
                        val sources = AudioSource.entries
                        val idx = sources.indexOf(audioSource)
                        onAudioSourceChange(sources[(idx + 1) % sources.size])
                    })
                }
            }

            // 倍速/源 展开面板
            if (showSpeedMenu) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f).forEach { speed ->
                        MiuixSurface(
                            modifier = Modifier.clickable { onSpeedChange(speed); showSpeedMenu = false },
                            color = if (speed == playbackSpeed) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant
                        ) {
                            MiuixText(
                                "${speed}x", fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                color = if (speed == playbackSpeed) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoInfoPanel(detail: ReplayDetail) {
    MiuixCard(
        modifier = Modifier.fillMaxWidth().padding(12.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MiuixText(detail.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (detail.instructorNames.isNotEmpty()) {
                MiuixText("教师: ${detail.instructorNames.joinToString(", ")}", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            detail.roomName?.let {
                MiuixText("教室: $it", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            if (detail.startTime.isNotEmpty()) {
                MiuixText("时间: ${detail.startTime} ~ ${detail.endTime}", fontSize = 12.sp, color = MiuixTheme.colorScheme.outline)
            }
            MiuixText("视频源: ${detail.replayVideos.joinToString(", ") { it.label }}", fontSize = 12.sp, color = MiuixTheme.colorScheme.outline)
        }
    }
}
