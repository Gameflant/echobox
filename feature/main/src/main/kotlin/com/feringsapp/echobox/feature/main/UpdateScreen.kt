package com.feringsapp.echobox.feature.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.feringsapp.echobox.core.repo.UpdateRepository
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

private sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Found(val info: UpdateRepository.ReleaseInfo) : UpdateUi
    data class Downloading(val done: Long, val total: Long) : UpdateUi
    data class Downloaded(val info: UpdateRepository.ReleaseInfo) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

@Composable
fun UpdateScreen(onBack: () -> Unit = {}, modifier: Modifier = Modifier) {
    val graph = LocalCoreGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }
    val currentVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }

    fun check() {
        if (ui is UpdateUi.Checking || ui is UpdateUi.Downloading) return
        ui = UpdateUi.Checking
        scope.launch {
            graph.updateRepository.check()
                .onSuccess { info ->
                    ui = when {
                        info == null -> UpdateUi.Failed("没有找到发布版本")
                        !graph.updateRepository.isNewer(info) -> UpdateUi.UpToDate
                        else -> UpdateUi.Found(info)
                    }
                }
                .onFailure { ui = UpdateUi.Failed(it.message ?: "检查失败") }
        }
    }

    fun download(info: UpdateRepository.ReleaseInfo) {
        ui = UpdateUi.Downloading(0, info.apkSize)
        scope.launch {
            graph.updateRepository.download(info) { done, total ->
                ui = UpdateUi.Downloading(done, total)
            }.onSuccess { file ->
                ui = UpdateUi.Downloaded(info)
                runCatching { graph.updateRepository.install(file) }
            }.onFailure {
                ui = UpdateUi.Failed(it.message ?: "下载失败")
            }
        }
    }

    EchoScreen(
        title = "检查更新",
        modifier = modifier,
        navigationIcon = {
            top.yukonga.miuix.kmp.basic.IconButton(onClick = onBack) {
                top.yukonga.miuix.kmp.basic.Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
            }
        },
    ) {
        item {
            EchoGroup(title = "当前版本") {
                BasicComponent(title = "EchoBox $currentVersion", summary = "GitHub Releases")
                BasicComponent(
                    title = when (ui) {
                        is UpdateUi.Checking -> "正在检查…"
                        else -> "检查更新"
                    },
                    summary = "从 GitHub 读取最新发布",
                    onClick = ::check,
                )
            }
        }
        when (val state = ui) {
            is UpdateUi.UpToDate -> item {
                EchoGroup(title = "结果") { BasicComponent(title = "已是最新版本", summary = "不用更新") }
            }
            is UpdateUi.Failed -> item {
                EchoGroup(title = "结果") { BasicComponent(title = "失败", summary = state.message) }
            }
            is UpdateUi.Found -> item {
                EchoGroup(title = "新版本 ${state.info.version}") {
                    if (state.info.notes.isNotBlank()) {
                        BasicComponent(title = "更新说明", summary = state.info.notes.take(300))
                    }
                    BasicComponent(
                        title = "立即下载",
                        summary = if (state.info.apkSize > 0) "${state.info.apkSize / 1024 / 1024} MB" else "APK",
                        onClick = { download(state.info) },
                    )
                    BasicComponent(
                        title = "在浏览器中打开",
                        summary = "查看完整发布页",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(state.info.htmlUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    )
                }
            }
            is UpdateUi.Downloading -> item {
                val pct = if (state.total > 0) (state.done * 100 / state.total).toInt() else 0
                EchoGroup(title = "下载中") {
                    BasicComponent(
                        title = "$pct%",
                        summary = "${state.done / 1024} KB / ${state.total / 1024} KB",
                    )
                }
            }
            is UpdateUi.Downloaded -> item {
                EchoGroup(title = "下载完成") {
                    BasicComponent(
                        title = "安装 ${state.info.version}",
                        summary = "如未弹出安装器，请允许安装未知来源应用",
                        onClick = {
                            scope.launch {
                                val dir = java.io.File(context.filesDir, "update")
                                val file = dir.listFiles()?.firstOrNull { it.name.endsWith(".apk") }
                                if (file != null) runCatching { graph.updateRepository.install(file) }
                            }
                        },
                    )
                }
            }
            else -> Unit
        }
    }
}
