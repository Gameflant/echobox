package com.feringsapp.echobox.feature.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.feringsapp.echobox.core.model.Mirror
import com.feringsapp.echobox.core.network.ProbeRegistry
import com.feringsapp.echobox.core.network.ProbeResult
import com.feringsapp.echobox.designsystem.LocalAccentColor
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 网络源：单选，点哪个用哪个。每个源显示真实下载测速（512KB 采样）。
 */
@Composable
fun NetworkScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val graph = LocalCoreGraph.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccentColor.current

    var mirrors by remember { mutableStateOf<List<Mirror>>(emptyList()) }
    var selectedId by remember { mutableStateOf("direct") }
    val probe by ProbeRegistry.results.collectAsState()
    val probing by ProbeRegistry.running.collectAsState()

    suspend fun refresh() {
        mirrors = graph.mirrorResolver.mirrors()
        selectedId = graph.mirrorResolver.selectedId()
    }

    LaunchedEffect(Unit) {
        refresh()
        if (ProbeRegistry.results.value.isEmpty()) {
            runCatching { graph.mirrorProbe.probeAll() }
        }
    }

    EchoScreen(
        title = "网络",
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
            }
        },
    ) {
        item {
            SubCard(title = "当前源") {
                BasicComponent(
                    title = "在线播放与同步都走这个源",
                    summary = "失败时不会自动切换，手动换一个再试",
                )
            }
        }
        item {
            SubCard(title = "官方源") {
                mirrors.filter { it.assetTemplate.isNotBlank() || it.id == "jsdelivr" }.forEach { mirror ->
                    NetworkRow(
                        mirror = mirror,
                        result = probe[mirror.id],
                        selected = mirror.id == selectedId,
                        accent = accent,
                        onClick = {
                            scope.launch {
                                graph.mirrorResolver.select(mirror.id)
                                selectedId = mirror.id
                            }
                        },
                    )
                }
            }
        }
        item {
            SubCard(title = "测速") {
                BasicComponent(
                    title = if (probing) "正在测速…" else "重新测速",
                    summary = "每个源真实下载 512KB，3.5 秒超时判失败",
                    onClick = {
                        if (probing) return@BasicComponent
                        scope.launch {
                            runCatching { graph.mirrorProbe.probeAll() }
                            refresh()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NetworkRow(
    mirror: Mirror,
    result: ProbeResult?,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val (speedText, speedColor) = when {
        result == null -> "未测速" to MiuixTheme.colorScheme.onSurfaceVariantSummary
        result.timedOut -> "timeout" to Color(0xFFE94634)
        result.ok -> "%.0f KB/s".format(result.speedKbps ?: 0.0) to Color(0xFF12A594)
        else -> (result.error ?: "失败") to Color(0xFFE94634)
    }
    BasicComponent(
        title = mirror.name,
        summary = buildString {
            append(mirror.description)
            if (mirror.assetTemplate.isBlank()) append(" · 不提供音频")
        },
        endActions = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = speedText,
                    style = MiuixTheme.textStyles.footnote2,
                    color = speedColor,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                if (selected) {
                    Text(
                        text = "使用中",
                        style = MiuixTheme.textStyles.footnote2,
                        color = accent,
                    )
                }
            }
        },
        onClick = onClick,
    )
}
