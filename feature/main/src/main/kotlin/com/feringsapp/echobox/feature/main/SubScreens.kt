package com.feringsapp.echobox.feature.main

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feringsapp.echobox.core.library.AudioTranscoder
import com.feringsapp.echobox.core.library.MediaStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

/** 二级页统一的分组卡片：标题左缩进与卡片内容对齐到 16dp。 */
@Composable
internal fun SubCard(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        if (title != null) {
            SmallTitle(
                text = title,
                insideMargin = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
    }
}

@Composable
fun StorageScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val storage = LocalMediaStorage.current
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf(MediaStorage.Usage(0, 0, 0)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    suspend fun refresh() {
        usage = storage.usage()
    }

    LaunchedEffect(Unit) { refresh() }

    EchoScreen(
        title = "存储",
        modifier = modifier,
        navigationIcon = { BackButton(onBack) },
    ) {
        item {
            SubCard(title = "占用") {
                BasicComponent(
                    title = "示例曲库",
                    summary = MediaStorage.format(usage.demoBytes),
                )
                BasicComponent(
                    title = "转码输出",
                    summary = MediaStorage.format(usage.transcodeBytes),
                )
                BasicComponent(
                    title = "应用缓存",
                    summary = MediaStorage.format(usage.cacheBytes),
                )
                BasicComponent(
                    title = "合计",
                    summary = MediaStorage.format(usage.totalBytes),
                )
            }
        }
        item {
            SubCard(title = "清理") {
                BasicComponent(
                    title = if (busy) "正在清理…" else "清理应用缓存",
                    summary = "封面、日志、临时文件",
                    onClick = {
                        if (busy) return@BasicComponent
                        scope.launch {
                            busy = true
                            val freed = runCatching { storage.clearCache() }.getOrDefault(0L)
                            refresh()
                            message = "已释放 ${MediaStorage.format(freed)}"
                            busy = false
                        }
                    },
                )
                BasicComponent(
                    title = if (busy) "正在清理…" else "清空转码输出",
                    summary = MediaStorage.format(usage.transcodeBytes),
                    onClick = {
                        if (busy) return@BasicComponent
                        scope.launch {
                            busy = true
                            val freed = runCatching { storage.clearTranscode() }.getOrDefault(0L)
                            refresh()
                            message = "已释放 ${MediaStorage.format(freed)}"
                            busy = false
                        }
                    },
                )
                if (message.isNotBlank()) {
                    BasicComponent(title = message, summary = "")
                }
            }
        }
    }
}

@Composable
fun TranscodeScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val transcoder = LocalAudioTranscoder.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceFile by remember { mutableStateOf<File?>(null) }
    var preset by remember { mutableStateOf(AudioTranscoder.Preset.ALL.first()) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var result by remember { mutableStateOf<AudioTranscoder.Result?>(null) }
    var error by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            result = null
            error = ""
            sourceFile = withContext(Dispatchers.IO) {
                runCatching {
                    val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "input" } ?: "input"
                    val target = File(context.cacheDir, "transcode_input").apply { mkdirs() }
                        .resolve(name)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target
                }.getOrElse {
                    error = it.message.orEmpty()
                    null
                }
            }
        }
    }

    fun start() {
        val file = sourceFile ?: return
        if (running) return
        scope.launch {
            running = true
            progress = 0f
            error = ""
            result = null
            runCatching { transcoder.transcode(file, preset) { progress = it } }
                .onSuccess { result = it }
                .onFailure { error = it.message.orEmpty() }
            running = false
        }
    }

    EchoScreen(
        title = "转码",
        modifier = modifier,
        navigationIcon = { BackButton(onBack) },
        floatingAction = {
            com.feringsapp.echobox.designsystem.GlassActionBar(
                text = when {
                    running -> "正在转码 ${(progress * 100).toInt()}%"
                    result != null -> "再转一次"
                    else -> "开始转码"
                },
                enabled = sourceFile != null && !running,
                onClick = { start() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        },
    ) {
        item {
            SubCard(title = "源文件") {
                BasicComponent(
                    title = sourceFile?.name ?: "选择音频文件",
                    summary = sourceFile?.let {
                        listOf(
                            MediaStorage.format(it.length()),
                            "${transcoder.durationMsOf(it) / 1000}s",
                        ).joinToString(" · ")
                    } ?: "支持 mp3 / m4a / flac / wav / ogg",
                    onClick = { if (!running) picker.launch(arrayOf("audio/*")) },
                )
            }
        }
        item {
            SubCard(title = "预设") {
                AudioTranscoder.Preset.ALL.forEach { item ->
                    BasicComponent(
                        title = item.label,
                        summary = item.extension.uppercase(),
                        endActions = {
                            if (item == preset) {
                                Text(
                                    text = "已选",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.primary,
                                )
                            }
                        },
                        onClick = { if (!running) preset = item },
                    )
                }
            }
        }
        item {
            SubCard(title = "状态") {
                BasicComponent(
                    title = when {
                        running -> "正在转码 ${(progress * 100).toInt()}%"
                        result != null -> "完成 · ${result?.output?.name}"
                        else -> "等待开始"
                    },
                    summary = result?.let {
                        "${MediaStorage.format(it.output.length())} · 保存在应用私有目录"
                    } ?: "输出到 files/transcode，可在存储页清理",
                )
                if (error.isNotBlank()) {
                    BasicComponent(title = "失败", summary = error)
                }
            }
        }
        item {
            SubCard(title = "能力") {
                BasicComponent(title = "ffmpeg-kit-audio", summary = "8.1.7 · LGPL-3.0")
                BasicComponent(title = "编码器", summary = "AAC / FLAC / PCM / Opus")
                BasicComponent(title = "解码", summary = "mp3 / flac / ogg / m4a / wav / opus")
            }
        }
    }
}

@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    EchoScreen(
        title = "开源许可",
        modifier = modifier,
        navigationIcon = { BackButton(onBack) },
    ) {
        item {
            SubCard {
                BasicComponent(title = "EchoBox", summary = "GPL-3.0-only", onClick = {})
                BasicComponent(title = "Miuix", summary = "Apache-2.0 · 组件库", onClick = {})
                BasicComponent(title = "Media3", summary = "Apache-2.0 · 播放与后台服务", onClick = {})
                BasicComponent(title = "ffmpeg-kit", summary = "LGPL-3.0 · 音频转码", onClick = {})
                BasicComponent(title = "Ktor / Coil / Room", summary = "Apache-2.0", onClick = {})
            }
        }
    }
}
