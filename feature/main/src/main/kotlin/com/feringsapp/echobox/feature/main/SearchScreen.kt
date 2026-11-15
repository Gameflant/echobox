package com.feringsapp.echobox.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import com.feringsapp.echobox.core.model.Track
import com.feringsapp.echobox.designsystem.EchoGlass
import com.feringsapp.echobox.designsystem.LocalGlassIntensity
import com.feringsapp.echobox.designsystem.LocalGlassMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onTrackClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val repo = LocalTrackRepository.current
    val libraryStore = LocalLibraryStore.current
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

    var query by remember { mutableStateOf("") }
    val history by libraryStore.searchHistory.collectAsState(initial = emptyList())

    // 空查询不展示全部曲目，只给历史与提示
    val results by remember(query) {
        if (query.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else repo.search(query)
    }.collectAsState(initial = emptyList())

    // 停止输入 600ms 后记一次历史，输入过程中不写盘
    LaunchedEffect(query) {
        if (query.isBlank()) return@LaunchedEffect
        delay(600)
        libraryStore.addSearch(query)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            GlassTopAppBar(
                title = "搜索",
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
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "曲名 / 作者 / 系列 / 标签",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (query.isBlank() && history.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        history.forEach { item ->
                            Text(
                                text = item,
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier
                                    .background(
                                        color = MiuixTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(50),
                                    )
                                    .clickable { query = item }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (query.isBlank()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                BasicComponent(
                                    title = "输入关键词开始搜索",
                                    summary = "曲名、作者、系列、标签都能搜",
                                )
                            }
                        }
                    } else if (results.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                BasicComponent(
                                    title = "没有找到「$query」",
                                    summary = "换个关键词试试",
                                )
                            }
                        }
                    } else {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                results.forEach { track ->
                                    SearchRow(track = track, onClick = { onTrackClick(track.id) })
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
private fun SearchRow(track: Track, onClick: () -> Unit) {
    BasicComponent(
        title = track.title,
        summary = listOf(track.author, track.series?.name.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" · "),
        endActions = {
            Text(
                text = formatDuration(track.duration),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        },
        onClick = onClick,
    )
}
