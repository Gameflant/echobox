package com.feringsapp.echobox.feature.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.feringsapp.echobox.core.library.MediaStorage
import com.feringsapp.echobox.core.model.Track
import com.feringsapp.echobox.core.repo.DownloadRepository
import com.feringsapp.echobox.designsystem.LocalAccentColor
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 下载列表：进行中 / 已暂停 / 失败 / 已完成。
 * 进行中可暂停，暂停后可继续（续传会重新解析当前选中的源，所以换源后自动生效）。
 */
@Composable
fun DownloadListScreen(
    onBack: () -> Unit,
    onOpenTrack: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadRepo = LocalDownloadRepository.current
    val trackRepo = LocalTrackRepository.current
    val scope = rememberCoroutineScope()

    val downloads by downloadRepo.observeAll().collectAsState(initial = emptyList())
    val tracks by trackRepo.observeLatest(500).collectAsState(initial = emptyList())
    val byId = remember(tracks) { tracks.associateBy { it.id } }

    val activeRows = downloads.filter { it.running || it.paused }
    val failed = downloads.filter { it.failed }
    val done = downloads.filter { it.done }
    val totalBytes = done.sumOf { it.file?.length() ?: 0L }

    EchoScreen(
        title = "下载列表",
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
            }
        },
    ) {
        if (downloads.isEmpty()) {
            item {
                SubCard {
                    BasicComponent(
                        title = "没有下载任务",
                        summary = "在曲目详情页点右侧的下载按钮",
                    )
                }
            }
        }
        if (activeRows.isNotEmpty()) {
            item {
                SubCard(title = "进行中 · ${activeRows.size}") {
                    activeRows.forEach { item ->
                        val track = byId[item.trackId]
                        DownloadRow(
                            item = item,
                            track = track,
                            onStart = { track?.let { downloadRepo.start(it) } },
                            onPause = {
                                if (track != null) scope.launch { downloadRepo.pause(track.id, track.file) }
                            },
                            onDelete = {
                                if (track != null) scope.launch { downloadRepo.delete(track.id, track.file) }
                            },
                            onClick = {},
                        )
                    }
                }
            }
        }
        if (failed.isNotEmpty()) {
            item {
                SubCard(title = "失败 · ${failed.size}") {
                    failed.forEach { item ->
                        val track = byId[item.trackId]
                        DownloadRow(
                            item = item,
                            track = track,
                            onStart = { track?.let { downloadRepo.start(it) } },
                            onPause = {},
                            onDelete = {
                                if (track != null) scope.launch { downloadRepo.delete(track.id, track.file) }
                            },
                            onClick = {},
                        )
                    }
                }
            }
        }
        if (done.isNotEmpty()) {
            item {
                SubCard(title = "已完成 · ${done.size} · ${MediaStorage.format(totalBytes)}") {
                    done.forEach { item ->
                        val track = byId[item.trackId]
                        DownloadRow(
                            item = item,
                            track = track,
                            onStart = {},
                            onPause = {},
                            onDelete = {
                                if (track != null) scope.launch { downloadRepo.delete(track.id, track.file) }
                            },
                            onClick = { onOpenTrack(item.trackId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadRepository.Item,
    track: Track?,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val accent = LocalAccentColor.current
    val status = when {
        item.failed -> item.error ?: "下载失败"
        item.paused -> buildString {
            append("已暂停 · ")
            append(MediaStorage.format(item.bytesDone))
            if (item.bytesTotal > 0) {
                append(" / ")
                append(MediaStorage.format(item.bytesTotal))
                append(" · ")
                append("${(item.progress * 100).toInt()}%")
            }
        }
        item.running -> buildString {
            append("下载中 · ")
            append(MediaStorage.format(item.bytesDone))
            if (item.bytesTotal > 0) {
                append(" / ")
                append(MediaStorage.format(item.bytesTotal))
                append(" · ")
                append("${(item.progress * 100).toInt()}%")
            }
        }
        item.done -> "已下载 · ${MediaStorage.format(item.file?.length() ?: 0L)}"
        else -> item.state
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (item.done) Modifier.clickable(onClick = onClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: item.trackId,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = status,
                    style = MiuixTheme.textStyles.footnote2,
                    color = if (item.failed) Color(0xFFE94634) else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            when {
                item.running -> {
                    IconButton(onClick = onPause) {
                        PauseMark(color = MiuixTheme.colorScheme.onSurface)
                    }
                }
                item.paused -> {
                    IconButton(onClick = onStart) {
                        PlayMark(color = accent)
                    }
                }
                item.failed -> {
                    IconButton(onClick = onStart) {
                        Icon(imageVector = MiuixIcons.Refresh, contentDescription = "重试")
                    }
                }
                else -> {
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = MiuixIcons.Delete, contentDescription = "删除")
                    }
                }
            }
        }
        if (item.active) {
            Spacer(Modifier.height(8.dp))
            val fraction = if (item.progress > 0f) item.progress else 0.02f
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        }
    }
}

@Composable
private fun PauseMark(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val barWidth = size.width * 0.28f
        val gap = size.width * 0.16f
        val left = (size.width - barWidth * 2 - gap) / 2f
        drawRect(color, Offset(left, 0f), Size(barWidth, size.height))
        drawRect(color, Offset(left + barWidth + gap, 0f), Size(barWidth, size.height))
    }
}

@Composable
private fun PlayMark(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.18f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(size.width * 0.18f, size.height)
            close()
        }
        drawPath(path, color)
    }
}
