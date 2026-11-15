package com.feringsapp.echobox.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feringsapp.echobox.designsystem.LocalAccentColor
import com.feringsapp.echobox.feature.player.LocalPlaybackController
import com.feringsapp.echobox.media.PlaybackUiState
import com.feringsapp.echobox.media.QueueItem
import kotlinx.coroutines.flow.MutableStateFlow
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 播放列表：直接读 MediaSession 里的真实队列，点谁播谁、可以单删、可以清空。
 */
@Composable
fun QueueScreen(
    onBack: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller = LocalPlaybackController.current
    val fallback = remember { MutableStateFlow(PlaybackUiState()) }
    val state by (controller?.state ?: fallback).collectAsState()
    val accent = LocalAccentColor.current

    // 队列长短、当前项、曲目 id 任一变化都重新取快照
    val queue: List<QueueItem> = remember(state.queueSize, state.currentIndex, state.trackId) {
        controller?.queueItems().orEmpty()
    }

    EchoScreen(
        title = "播放列表",
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
            }
        },
        actions = {
            if (queue.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(imageVector = MiuixIcons.Delete, contentDescription = "清空")
                }
            }
        },
    ) {
        if (queue.isEmpty()) {
            item {
                SubCard {
                    BasicComponent(
                        title = "队列是空的",
                        summary = "从专辑页或曲目详情页开始播放",
                    )
                }
            }
        } else {
            item {
                SubCard(title = "${queue.size} 首 · 当前第 ${state.currentIndex + 1} 首") {
                    queue.forEachIndexed { index, item ->
                        val isCurrent = index == state.currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { controller?.jumpTo(index) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (isCurrent) accent else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (isCurrent) "▶" else "${index + 1}",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = if (isCurrent) Color.White else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            ) {
                                Text(
                                    text = item.title.ifBlank { "未知曲目" },
                                    style = MiuixTheme.textStyles.body1,
                                    color = if (isCurrent) accent else MiuixTheme.colorScheme.onSurface,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else null,
                                )
                                if (item.artist.isNotBlank()) {
                                    Text(
                                        text = item.artist,
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                            IconButton(onClick = { controller?.removeAt(index) }) {
                                Icon(imageVector = MiuixIcons.Delete, contentDescription = "从队列移除")
                            }
                        }
                    }
                }
            }
        }
    }
}
