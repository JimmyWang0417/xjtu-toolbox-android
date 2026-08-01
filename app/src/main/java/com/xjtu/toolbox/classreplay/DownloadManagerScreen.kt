package com.xjtu.toolbox.classreplay

import android.content.Intent
import android.os.Environment
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.xjtu.toolbox.lms.LmsDownloadRecord
import com.xjtu.toolbox.lms.LmsDownloadStore

private const val TAG = "DownloadManagerScreen"

/**
 * 下载管理页面
 */
@Composable
fun DownloadManagerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val downloadManager = remember { DownloadManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    var allTasks by remember { mutableStateOf<List<DownloadTaskEntity>>(emptyList()) }
    var lmsDownloads by remember { mutableStateOf<List<LmsDownloadRecord>>(emptyList()) }
    var stats by remember { mutableStateOf<DownloadManager.DownloadStats?>(null) }

    // 多选批量管理。两份选中集合，因为本页有两套彼此独立的数据源：
    // 回放视频任务在 Room（按 id），文件类下载在 MediaStore（按 uri）。
    var isCleanupMode by remember { mutableStateOf(false) }
    val selectedTaskIds = remember { mutableStateListOf<Long>() }
    val selectedFileUris = remember { mutableStateListOf<String>() }
    
    // 显示下载目录信息弹窗
    val showDirInfo = remember { mutableStateOf(false) }

    // 加载任务列表
    fun loadTasks() {
        scope.launch {
            allTasks = withContext(Dispatchers.IO) {
                downloadManager.dao.getAll()
            }
            stats = withContext(Dispatchers.IO) {
                downloadManager.getDownloadStats()
            }
            lmsDownloads = LmsDownloadStore.getAll(context)
        }
    }

    LaunchedEffect(Unit) {
        loadTasks()
    }

    // 监听下载进度
    LaunchedEffect(downloadManager) {
        downloadManager.progressFlow.collect { _ ->
            loadTasks()
        }
    }

    // 退出批量模式时清空两类选择
    DisposableEffect(isCleanupMode) {
        onDispose {
            if (!isCleanupMode) {
                selectedTaskIds.clear()
                selectedFileUris.clear()
            }
        }
    }

    Scaffold(
        topBar = {
            if (isCleanupMode) {
                SmallTopAppBar(
                    title = "批量管理",
                    color = MiuixTheme.colorScheme.surfaceVariant,
                    navigationIcon = {
                        IconButton(onClick = { isCleanupMode = false }) {
                            Icon(Icons.Default.Close, contentDescription = "取消清理")
                        }
                    },
                    actions = {
                        // 全选覆盖两类数据源：回放任务（Room）+ 文件类下载（MediaStore）
                        IconButton(onClick = {
                            val allSelected = selectedTaskIds.size == allTasks.size &&
                                selectedFileUris.size == lmsDownloads.size &&
                                (allTasks.isNotEmpty() || lmsDownloads.isNotEmpty())
                            selectedTaskIds.clear()
                            selectedFileUris.clear()
                            if (!allSelected) {
                                selectedTaskIds.addAll(allTasks.map { it.id })
                                selectedFileUris.addAll(lmsDownloads.map { it.uri })
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "全选/取消")
                        }
                    }
                )
            } else {
                SmallTopAppBar(
                    title = "下载管理",
                    color = MiuixTheme.colorScheme.surfaceVariant,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        // 信息按钮 - 显示下载目录
                        IconButton(onClick = { showDirInfo.value = true }) {
                            Icon(Icons.Default.Info, contentDescription = "下载目录信息")
                        }
                        // 有任何可管理的条目就显示入口。条件不能只看回放任务，
                        // 否则只下载过文件的用户进不去批量模式。
                        if (allTasks.isNotEmpty() || lmsDownloads.isNotEmpty()) {
                            IconButton(onClick = { isCleanupMode = true }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "批量管理")
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            val selectedTotal = selectedTaskIds.size + selectedFileUris.size
            if (isCleanupMode && selectedTotal > 0) {
                var deleteFiles by remember { mutableStateOf(true) }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .background(MiuixTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左边：已选数量（两类合计）
                        Text(
                            "已选 $selectedTotal 项",
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                        )
                        // 中间：同时删除文件（整个区域可点击切换）。
                        // 只对回放任务有意义——文件类下载本身就是文件，删记录必然连文件一起删。
                        if (selectedTaskIds.isNotEmpty()) {
                            Row(
                                modifier = Modifier.clickable { deleteFiles = !deleteFiles },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    state = if (deleteFiles) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off,
                                    onClick = { deleteFiles = !deleteFiles },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "同时删除文件",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                        // 右边：删除选中按钮
                        Button(
                            onClick = {
                                scope.launch {
                                    // 回放任务：Room + 本地文件
                                    selectedTaskIds.toList().forEach { id ->
                                        downloadManager.deleteTask(id, deleteFile = deleteFiles)
                                    }
                                    // 文件类下载：MediaStore + 记录
                                    withContext(Dispatchers.IO) {
                                        selectedFileUris.toList().forEach { uri ->
                                            runCatching {
                                                context.contentResolver.delete(android.net.Uri.parse(uri), null, null)
                                            }
                                            LmsDownloadStore.remove(context, uri)
                                        }
                                    }
                                    selectedTaskIds.clear()
                                    selectedFileUris.clear()
                                    loadTasks()
                                }
                            }
                        ) {
                            Text("删除选中")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (allTasks.isEmpty() && lmsDownloads.isEmpty()) {
                // 空状态
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "暂无下载内容",
                        fontSize = 15.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "思源课件和课堂回放会统一显示在这里",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // 文件类下载（成绩单 / 思源课件 / 资料站 / 其他）。
                    // 批量管理模式下也要显示，否则这些文件无法批量删除。
                    if (lmsDownloads.isNotEmpty()) {
                        val groupedDownloads = lmsDownloads.groupBy { it.category }
                        val orderedGroups = listOf(
                            LmsDownloadStore.CATEGORY_TRANSCRIPT to "电子成绩单",
                            LmsDownloadStore.CATEGORY_LMS to "思源文件",
                            LmsDownloadStore.CATEGORY_ZYXF to "仲英学辅资料",
                            LmsDownloadStore.CATEGORY_OTHER to "其他文件"
                        )
                        orderedGroups.forEach { (category, title) ->
                            val records = groupedDownloads[category].orEmpty()
                            if (records.isNotEmpty()) {
                                item(key = "${category}_title") {
                                    DownloadSectionHeader(title, records.size)
                                }
                                items(records, key = { "${category}_${it.uri}" }) { record ->
                                    LmsDownloadCard(
                                        record = record,
                                        isCleanupMode = isCleanupMode,
                                        isSelected = record.uri in selectedFileUris,
                                        onToggleSelect = {
                                            if (record.uri in selectedFileUris) {
                                                selectedFileUris.remove(record.uri)
                                            } else {
                                                selectedFileUris.add(record.uri)
                                            }
                                        },
                                        onOpen = {
                                            runCatching {
                                                // 类型交给系统：用记录里的 mimeType，空则 */* 弹选择器
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(
                                                        android.net.Uri.parse(record.uri),
                                                        record.mimeType.ifBlank { "*/*" }
                                                    )
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(intent, "打开文件"))
                                            }.onFailure {
                                                android.widget.Toast.makeText(context, "文件已被移动或删除", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onDelete = {
                                            runCatching { context.contentResolver.delete(android.net.Uri.parse(record.uri), null, null) }
                                            LmsDownloadStore.remove(context, record.uri)
                                            loadTasks()
                                        }
                                    )
                                }
                            }
                        }
                        if (allTasks.isNotEmpty()) {
                            item(key = "replay_title") {
                                DownloadSectionHeader("课堂回放", allTasks.size)
                            }
                        }
                    }

                    // 全局控制栏
                    if (stats != null && stats!!.activeCount > 0 && !isCleanupMode) {
                        item(key = "global_controls") {
                            GlobalControls(
                                stats = stats!!,
                                downloadManager = downloadManager,
                                onRefresh = { loadTasks() }
                            )
                        }
                    }

                    // 任务列表
                    items(allTasks, key = { it.id }) { task ->
                        DownloadTaskCard(
                            task = task,
                            isCleanupMode = isCleanupMode,
                            isSelected = selectedTaskIds.contains(task.id),
                            onToggleSelection = {
                                if (selectedTaskIds.contains(task.id)) {
                                    selectedTaskIds.remove(task.id)
                                } else {
                                    selectedTaskIds.add(task.id)
                                }
                            },
                            onPause = {
                                scope.launch {
                                    downloadManager.pauseDownload(task.id)
                                    loadTasks()
                                }
                            },
                            onResume = {
                                downloadManager.resumeDownload(task.id)
                                loadTasks()
                            },
                            onCancel = {
                                scope.launch {
                                    downloadManager.cancelDownload(task.id)
                                    loadTasks()
                                }
                            },
                            onPlay = {
                                try {
                                     val file = File(task.filePath)
                                     if (file.exists()) {
                                         // 类型按实际扩展名交给系统判定，查不到给 */* 弹选择器。
                                         // 不写死具体类型，避免遇到未覆盖的格式时无应用可选。
                                         val mime = android.webkit.MimeTypeMap.getSingleton()
                                             .getMimeTypeFromExtension(file.extension.lowercase())
                                             ?: "*/*"
                                         // 使用 file:// URI 直接播放（Android 10 及以下）
                                         // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE 权限或使用 FileProvider
                                         // 这里尝试直接使用 file URI，失败则提示用户
                                         val uri = android.net.Uri.fromFile(file)
                                         val intent = Intent(Intent.ACTION_VIEW).apply {
                                             setDataAndType(uri, mime)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        try {
                                            context.startActivity(Intent.createChooser(intent, "选择播放器"))
                                        } catch (e: Exception) {
                                            // file:// URI 被禁止，尝试 FileProvider
                                            try {
                                                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    file
                                                )
                                                 val contentIntent = Intent(Intent.ACTION_VIEW).apply {
                                                     setDataAndType(contentUri, mime)
                                                     addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                     addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                 }
                                                 context.startActivity(Intent.createChooser(contentIntent, "打开文件"))
                                            } catch (e2: Exception) {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "无法启动播放器，请使用系统文件管理器查看",
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            "视频文件不存在",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Play video error", e)
                                    android.widget.Toast.makeText(
                                        context,
                                        "无法播放视频: ${e.message}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }

        // 必须在 Scaffold 的 content 内：OverlayBottomSheet 靠 Scaffold 提供的
        // LocalDialogStates 宿主渲染，写在 Scaffold 外面会静默不显示。
    // 下载目录信息弹窗
    DownloadDirInfoDialog(show = showDirInfo)
        }
    }
    
}

@Composable
private fun DownloadSectionHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MiuixTheme.textStyles.subtitle,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        ) {
            Text(
                "$count",
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun LmsDownloadCard(
    record: LmsDownloadRecord,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    isCleanupMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
) {
    Card(
        // 批量管理模式下点击=选中，而不是打开文件
        onClick = if (isCleanupMode) onToggleSelect else onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCleanupMode) {
                Checkbox(
                    state = if (isSelected) {
                        androidx.compose.ui.state.ToggleableState.On
                    } else {
                        androidx.compose.ui.state.ToggleableState.Off
                    },
                    onClick = { onToggleSelect() },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MiuixTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when (record.category) {
                            LmsDownloadStore.CATEGORY_TRANSCRIPT -> Icons.Default.PictureAsPdf
                            else -> Icons.Default.Description
                        },
                        null,
                        Modifier.size(22.dp),
                        tint = if (record.category == LmsDownloadStore.CATEGORY_TRANSCRIPT) Color(0xFFE53935)
                        else MiuixTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(record.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${record.categoryLabel()} · ${formatTimestamp(record.savedAt)}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "删除文件")
            }
        }
    }
}

private fun LmsDownloadRecord.categoryLabel(): String = when (category) {
    LmsDownloadStore.CATEGORY_TRANSCRIPT -> "成绩单"
    LmsDownloadStore.CATEGORY_LMS -> "思源文件"
    else -> "已下载"
}

/**
 * 全局控制栏
 */
@Composable
private fun GlobalControls(
    stats: DownloadManager.DownloadStats,
    downloadManager: DownloadManager,
    onRefresh: () -> Unit
) {
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            // 统计信息
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "下载中: ${stats.downloadingCount}",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "活跃任务: ${stats.activeCount}",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }

            Spacer(Modifier.height(12.dp))

            // 全局操作按钮
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    text = "暂停全部",
                    onClick = {
                        scope.launch {
                            downloadManager.pauseAll()
                            delay(300)
                            onRefresh()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "继续全部",
                    onClick = {
                        downloadManager.resumeAll()
                        scope.launch {
                            delay(500)
                            onRefresh()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "取消全部",
                    onClick = {
                        scope.launch {
                            downloadManager.cancelAll()
                            delay(300)
                            onRefresh()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 单个下载任务卡片（紧凑设计）
 */
@Composable
private fun DownloadTaskCard(
    task: DownloadTaskEntity,
    isCleanupMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onCancel: () -> Unit = {},
    onPlay: () -> Unit = {}
) {
    val cardModifier = if (isCleanupMode) {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onToggleSelection() }
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    }
    Card(
        modifier = cardModifier,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
            color = if (isCleanupMode && isSelected)
                MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MiuixTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 清理模式显示Checkbox
            if (isCleanupMode) {
                Box(
                    modifier = Modifier
                        .size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckBox,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .border(
                                    2.dp,
                                    MiuixTheme.colorScheme.outline,
                                    RoundedCornerShape(4.dp)
                                )
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }

            // 状态图标
            Icon(
                when (task.status) {
                    "downloading" -> Icons.Default.Download
                    "completed" -> Icons.Default.CheckCircle
                    "failed" -> Icons.Default.Error
                    "paused" -> Icons.Default.PauseCircle
                    else -> Icons.Default.Download
                },
                contentDescription = null,
                tint = when (task.status) {
                    "downloading" -> MiuixTheme.colorScheme.primary
                    "completed" -> Color(0xFF4CAF50)
                    "failed" -> MiuixTheme.colorScheme.error
                    else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(10.dp))

            // 中间信息区
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        task.activityTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // 视频源标签。这张表只存课程回放视频；文件类下载走 LmsDownloadStore，
                    // 在本页的独立分区显示。
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            if (task.cameraType == "instructor") "教师" else "屏幕",
                            Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            fontSize = 10.sp,
                            color = MiuixTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatTimestamp(task.createTime),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    if (task.status == "downloading" || task.status == "paused") {
                        Text(
                            "${(task.progress * 100).toInt()}%",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        // 显示下载速度
                        if (task.status == "downloading" && task.downloadSpeed > 0) {
                            Text(
                                "${formatFileSize(task.downloadSpeed)}/s",
                                fontSize = 10.sp,
                                color = MiuixTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                    } else if (task.status == "completed") {
                        Text(
                            formatFileSize(task.downloadedSize),
                            fontSize = 11.sp,
                            color = Color(0xFF4CAF50)
                        )
                    } else if (task.status == "failed") {
                        Text(
                            "下载失败",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.error
                        )
                    }
                }

                // 错误信息（仅失败时显示）
                if (task.status == "failed" && !task.errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        task.errorMessage,
                        fontSize = 10.sp,
                        color = MiuixTheme.colorScheme.error.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 进度条（仅下载中显示）
                if (task.status == "downloading" || task.status == "paused") {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = task.progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 清理模式下隐藏右侧操作按钮
            if (!isCleanupMode) {
                Spacer(Modifier.width(8.dp))

                // 右侧操作按钮
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when (task.status) {
                        "downloading" -> {
                            IconButton(
                                onClick = onPause,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = "暂停", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "取消", modifier = Modifier.size(18.dp), tint = MiuixTheme.colorScheme.error)
                            }
                        }
                        "paused", "pending" -> {
                            IconButton(
                                onClick = onResume,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "继续", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "取消", modifier = Modifier.size(18.dp), tint = MiuixTheme.colorScheme.error)
                            }
                        }
                        "completed" -> {
                            IconButton(
                                onClick = onPlay,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "播放", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 格式化文件大小
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}

/**
 * 格式化时间戳
 */
private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * 下载目录信息弹窗
 */
@Composable
private fun DownloadDirInfoDialog(show: MutableState<Boolean>) {
    BackHandler(enabled = show.value) { show.value = false }
    OverlayBottomSheet(
        show = show.value,
        title = "下载目录信息",
        onDismissRequest = { show.value = false }
    ) {
        Column(
            modifier = Modifier.overScrollVertical().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "保存位置",
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold
            )
            Text(
                "文件：${LmsDownloadStore.publicDisplayPath()}\n" +
                    "回放视频：${DownloadManager.getInstance(LocalContext.current).videoDirPath}",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "提示",
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold
            )
            Text(
                "• 成绩单、思源课件、资料站文件统一保存到公共下载目录，系统文件管理器可见，卸载 App 不会丢",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Text(
                "• 课堂回放视频保存在应用专属目录（无需存储权限），支持外部播放器播放；卸载 App 会一并清除",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Text(
                "• 删除记录仅删除数据库记录，不删除视频文件",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Text(
                "• 清理模式下可勾选\"同时删除文件\"彻底删除",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { show.value = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("知道了")
        }
        Spacer(Modifier.height(16.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
