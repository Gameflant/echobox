package com.feringsapp.echobox.feature.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.feringsapp.echobox.core.db.SubmissionEntity
import com.feringsapp.echobox.core.repo.SubmissionDraft
import com.feringsapp.echobox.core.repo.SubmissionRepository
import com.feringsapp.echobox.designsystem.EchoGlass
import com.feringsapp.echobox.designsystem.LocalGlassIntensity
import com.feringsapp.echobox.designsystem.LocalGlassMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.security.MessageDigest

val LocalSubmissionRepository = staticCompositionLocalOf<SubmissionRepository?> { null }

@Composable
fun UploadScreen(onBack: () -> Unit = {}, modifier: Modifier = Modifier) {
    val repository = LocalSubmissionRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val scrollBehavior = MiuixScrollBehavior()
    val glassMode = LocalGlassMode.current
    val glassIntensity = LocalGlassIntensity.current
    val glassStyle = remember(glassMode, glassIntensity) { EchoGlass.style(glassMode, glassIntensity) }
    val glassMaterial = remember(glassMode, glassIntensity) { EchoGlass.material(glassMode, glassIntensity) }
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    var audioName by remember { mutableStateOf("") }
    var audioSize by remember { mutableStateOf(0L) }
    var durationSec by remember { mutableStateOf(0) }
    var sha256 by remember { mutableStateOf("") }
    var coverName by remember { mutableStateOf("") }

    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var series by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var license by remember { mutableStateOf("CC0") }
    var authorizedRepost by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val drafts by (repository?.observeAll() ?: remember { kotlinx.coroutines.flow.flowOf(emptyList<SubmissionEntity>()) })
        .collectAsState(initial = emptyList())

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            message = ""
            withContext(Dispatchers.IO) {
                runCatching {
                    val target = File(context.cacheDir, "upload").apply { mkdirs() }
                    val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "audio" } ?: "audio"
                    val file = File(target, name)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    file
                }.onSuccess { file ->
                    audioName = file?.name.orEmpty()
                    audioSize = file?.length() ?: 0L
                    sha256 = file?.let { digestOf(it) }.orEmpty()
                }.onFailure { message = it.message.orEmpty() }
            }
            busy = false
        }
    }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coverName = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    }

    val valid = audioName.isNotBlank() && title.isNotBlank() && author.isNotBlank()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            GlassTopAppBar(
                title = "上传作品",
                backdrop = backdrop,
                style = glassStyle,
                material = glassMaterial,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
            ) {
                LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = padding.calculateTopPadding(),
                    bottom = 120.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    UploadGroup(title = "文件") {
                        BasicComponent(
                            title = audioName.ifBlank { "选择音频" },
                            summary = if (audioName.isBlank()) {
                                "mp3 / m4a / flac / wav / ogg"
                            } else {
                                "${audioSize / 1024} KB" + if (durationSec > 0) " · ${durationSec}s" else ""
                            },
                            onClick = { audioPicker.launch(arrayOf("audio/*")) },
                        )
                        BasicComponent(
                            title = coverName.ifBlank { "选择封面" },
                            summary = "可选 · jpg / png / webp",
                            onClick = { coverPicker.launch(arrayOf("image/*")) },
                        )
                        if (sha256.isNotBlank()) {
                            BasicComponent(title = "SHA-256", summary = sha256.take(32) + "…")
                        }
                    }
                }
                item {
                    UploadGroup(title = "曲目信息") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            TextField(
                                value = title,
                                onValueChange = { title = it },
                                label = "曲名",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            TextField(
                                value = author,
                                onValueChange = { author = it },
                                label = "作者",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            TextField(
                                value = series,
                                onValueChange = { series = it },
                                label = "系列（可选）",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            TextField(
                                value = tags,
                                onValueChange = { tags = it },
                                label = "标签，用逗号分隔",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item {
                    UploadGroup(title = "授权") {
                        BasicComponent(
                            title = "授权转载",
                            summary = if (authorizedRepost) "已选择" else "原作者明确同意转载",
                            onClick = { authorizedRepost = !authorizedRepost },
                        )
                        BasicComponent(
                            title = "自制",
                            summary = if (!authorizedRepost) "已选择" else "",
                            onClick = { authorizedRepost = false },
                        )
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            TextField(
                                value = license,
                                onValueChange = { license = it },
                                label = "资源协议",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                if (message.isNotBlank()) {
                    item {
                        UploadGroup(title = "状态") {
                            BasicComponent(title = message, summary = "")
                        }
                    }
                }
                if (drafts.isNotEmpty()) {
                    item {
                        UploadGroup(title = "我的投稿") {
                            drafts.forEach { draft ->
                                BasicComponent(
                                    title = draft.audioName ?: draft.recordId.take(8),
                                    summary = when (draft.state) {
                                        "DRAFT" -> "草稿 · 未提交"
                                        "PENDING" -> "待审核"
                                        "REJECTED" -> "已退回 · ${draft.reason.orEmpty()}"
                                        else -> draft.state
                                    },
                                )
                            }
                        }
                    }
                }
                }
            }

            com.feringsapp.echobox.designsystem.GlassActionBar(
                text = when {
                    busy -> "正在处理…"
                    valid -> "保存投稿草稿"
                    else -> "请先补全音频、曲名、作者"
                },
                enabled = valid && !busy,
                onClick = {
                    val repo = repository
                    if (repo != null) scope.launch {
                        busy = true
                        runCatching {
                            repo.saveDraft(
                                SubmissionDraft(
                                    title = title.trim(),
                                    author = author.trim(),
                                    tags = tags.split(',', '，').map { it.trim() }.filter { it.isNotBlank() },
                                    series = series.trim(),
                                    authorization = if (authorizedRepost) "authorized_repost" else "self_made",
                                    resourceLicense = license.trim(),
                                    audioName = audioName,
                                    coverName = coverName,
                                    durationSec = durationSec,
                                    sha256 = sha256,
                                ),
                            )
                        }.onSuccess {
                            message = "已保存草稿"
                        }.onFailure {
                            message = it.message.orEmpty()
                        }
                        busy = false
                    }
                },
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun UploadGroup(title: String, content: @Composable () -> Unit) {
    Column {
        SmallTitle(
            text = title,
            insideMargin = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

private fun digestOf(file: File): String = runCatching {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            md.update(buffer, 0, read)
        }
    }
    md.digest().joinToString("") { "%02x".format(it) }
}.getOrDefault("")
