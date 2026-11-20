package com.feringsapp.echobox.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feringsapp.echobox.core.model.Track
import com.feringsapp.echobox.designsystem.CoverBox
import com.feringsapp.echobox.designsystem.EchoGlass
import com.feringsapp.echobox.designsystem.LocalAccentColor
import com.feringsapp.echobox.designsystem.LocalGlassIntensity
import com.feringsapp.echobox.designsystem.LocalGlassMode
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(
    onTrackClick: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val repo = LocalTrackRepository.current
    val coverBase = LocalCoverBase.current
    val tracks by repo.observeLatest(20).collectAsState(initial = emptyList())

    EchoScreen(
        title = "主页",
        modifier = modifier,
        actions = {
            IconButton(onClick = onSearch) {
                Icon(imageVector = MiuixIcons.Search, contentDescription = "搜索")
            }
        },
    ) {
        item { SearchBarStub(onClick = onSearch) }
        item {
            SectionHeader("最近上传", "共 ${tracks.size} 首")
            Card(modifier = Modifier.fillMaxWidth()) {
                if (tracks.isEmpty()) {
                    BasicComponent(
                        title = "还没有音乐",
                        summary = "去设置里检查网络源，然后点「同步曲库」",
                    )
                } else {
                    tracks.take(12).forEach { track ->
                        SongRow(
                            track = track,
                            coverUrl = if (track.cover.isBlank()) "" else coverBase + track.cover,
                            onClick = { onTrackClick(track.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(
    onTrackClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onLocalMusic: () -> Unit = {},
    onFavorites: () -> Unit = {},
    onRecent: () -> Unit = {},
    onDownloads: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val repo = LocalTrackRepository.current
    val historyRepo = LocalHistoryRepository.current
    val coverBase = LocalCoverBase.current

    val tracks by repo.observeLatest(200).collectAsState(initial = emptyList())
    val history by historyRepo.observeRecent(30).collectAsState(initial = emptyList())
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val recent = remember(history, byId) {
        history.mapNotNull { byId[it.trackId] }.distinctBy { it.id }.take(8)
    }
    val albums = remember(tracks) {
        tracks.groupBy { it.series?.name ?: it.game }
            .map { (name, list) -> name to list }
            .sortedByDescending { it.second.size }
            .take(6)
    }

    EchoScreen(title = "音乐", modifier = modifier) {
        item {
            QuickEntryRow(
                entries = listOf(
                    Triple(MiuixIcons.Folder, "本地音乐", onLocalMusic),
                    Triple(MiuixIcons.Favorites, "我的收藏", onFavorites),
                    Triple(MiuixIcons.Playlist, "最近播放", onRecent),
                    Triple(MiuixIcons.Download, "下载列表", onDownloads),
                ),
            )
        }
        if (albums.isNotEmpty()) {
            item {
                SectionHeader("歌单")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(albums) { (name, list) ->
                        AlbumCard(
                            name = name,
                            count = list.size,
                            coverUrl = list.firstOrNull { it.cover.isNotBlank() }?.let { coverBase + it.cover }.orEmpty(),
                            modifier = Modifier.width(150.dp),
                            onClick = { onAlbumClick(name) },
                        )
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item {
                SectionHeader("最近播放", "全部", onRecent)
                Card(modifier = Modifier.fillMaxWidth()) {
                    recent.forEach { track ->
                        SongRow(
                            track = track,
                            coverUrl = if (track.cover.isBlank()) "" else coverBase + track.cover,
                            onClick = { onTrackClick(track.id) },
                        )
                    }
                }
            }
        }
        item {
            SectionHeader("全部歌曲", "${tracks.size} 首")
            Card(modifier = Modifier.fillMaxWidth()) {
                if (tracks.isEmpty()) {
                    BasicComponent(title = "曲库是空的", summary = "去发现页看看，或检查网络源")
                } else {
                    tracks.take(50).forEach { track ->
                        SongRow(
                            track = track,
                            coverUrl = if (track.cover.isBlank()) "" else coverBase + track.cover,
                            onClick = { onTrackClick(track.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(
    onTrackClick: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val repo = LocalTrackRepository.current
    val coverBase = LocalCoverBase.current

    val tracks by repo.observeLatest(200).collectAsState(initial = emptyList())
    val hottest by repo.observeHottest(50).collectAsState(initial = emptyList())

    val albums = remember(tracks) {
        tracks.groupBy { it.series?.name ?: it.game }
            .map { (name, list) -> name to list }
            .sortedByDescending { it.second.size }
    }
    val tags = remember(tracks) {
        tracks.flatMap { it.tags }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(10).map { it.key }
    }

    EchoScreen(title = "发现", modifier = modifier) {
        item {
            SearchBarStub(onClick = onSearch)
        }
        if (albums.isNotEmpty()) {
            item {
                SectionHeader("推荐专辑")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(albums) { (name, list) ->
                        AlbumCard(
                            name = name,
                            count = list.size,
                            coverUrl = list.firstOrNull { it.cover.isNotBlank() }?.let { coverBase + it.cover }.orEmpty(),
                            modifier = Modifier.width(190.dp),
                            onClick = { onAlbumClick(name) },
                        )
                    }
                }
            }
        }
        if (tags.isNotEmpty()) {
            item {
                SectionHeader("热门标签")
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { tag ->
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = tag,
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
        item {
            SectionHeader("最新上传", "共 ${tracks.size} 首")
            Card(modifier = Modifier.fillMaxWidth()) {
                if (tracks.isEmpty()) {
                    BasicComponent(
                        title = "暂无内容",
                        summary = "检查网络源，或在设置里重新同步",
                    )
                } else {
                    tracks.take(15).forEach { track ->
                        SongRow(
                            track = track,
                            coverUrl = if (track.cover.isBlank()) "" else coverBase + track.cover,
                            onClick = { onTrackClick(track.id) },
                        )
                    }
                }
            }
        }
        item {
            SectionHeader("热门")
            Card(modifier = Modifier.fillMaxWidth()) {
                hottest.take(10).forEach { track ->
                    SongRow(
                        track = track,
                        coverUrl = if (track.cover.isBlank()) "" else coverBase + track.cover,
                        onClick = { onTrackClick(track.id) },
                    )
                }
            }
        }
    }
}

/** 收藏 / 最近播放共用的简单列表页。 */
@Composable
fun TrackListScreen(
    title: String,
    tracks: List<Track>,
    emptyTitle: String,
    emptySummary: String,
    onBack: () -> Unit,
    onTrackClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coverBase = LocalCoverBase.current
    EchoScreen(
        title = title,
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
            }
        },
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                if (tracks.isEmpty()) {
                    BasicComponent(title = emptyTitle, summary = emptySummary)
                } else {
                    tracks.forEach { track ->
                        SongRow(
                            track = track,
                            coverUrl = if (track.cover.isBlank()) "" else coverBase + track.cover,
                            onClick = { onTrackClick(track.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onAppearance: () -> Unit = {},
    onUpload: () -> Unit = {},
    onNetwork: () -> Unit = {},
    onStorage: () -> Unit = {},
    onTranscode: () -> Unit = {},
    onLicenses: () -> Unit = {},
    onUpdate: () -> Unit = {},
    onSubmissions: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val graph = LocalCoreGraph.current
    val scope = rememberCoroutineScope()
    var syncState by remember { mutableStateOf("未同步") }

    EchoScreen(title = "设置", modifier = modifier) {
        item {
            EchoGroup {
                SettingRow("外观", "颜色、玻璃、动效", onAppearance)
                SettingRow("上传作品", "选择音频并填写曲目信息", onUpload)
                SettingRow("我的投稿", "查看审核状态", onSubmissions)
                SettingRow("同步曲库", syncState, onClick = {
                    scope.launch {
                        syncState = "同步中…"
                        syncState = when (val r = graph.indexSyncer.sync(forceFull = true)) {
                            is com.feringsapp.echobox.core.sync.SyncResult.Updated -> "已更新 ${r.fetched} 首 · ${r.mode}"
                            is com.feringsapp.echobox.core.sync.SyncResult.NotModified -> "已是最新"
                            is com.feringsapp.echobox.core.sync.SyncResult.Failed -> "失败：${r.reason}"
                        }
                    }
                })
            }
        }
        item {
            EchoGroup {
                SettingRow("网络", "选择直连或镜像源", onNetwork)
                SettingRow("检查更新", "从 GitHub 获取最新版本", onUpdate)
                SettingRow("存储与缓存", "", onStorage)
                SettingRow("转码预设", "", onTranscode)
            }
        }
        item {
            EchoGroup(title = "关于") {
                SettingRow("EchoBox", "0.0.4 · Apache-2.0")
                SettingRow("开源许可", "", onLicenses)
            }
        }
    }
}

@Composable
internal fun EchoScreen(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    floatingAction: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            GlassTopAppBar(
                title = title,
                largeTitle = title,
                backdrop = backdrop,
                style = glassStyle,
                material = glassMaterial,
                scrollBehavior = scrollBehavior,
                navigationIcon = navigationIcon,
                actions = actions,
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
                        top = padding.calculateTopPadding() + 4.dp,
                        bottom = 130.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
            floatingAction?.invoke(this)
        }
    }
}

@Composable
private fun QuickEntryRow(entries: List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>>) {
    val accent = LocalAccentColor.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        entries.forEach { (icon, label, onClick) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String = "", onTrailing: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onBackground,
        )
        if (trailing.isNotBlank()) {
            Text(
                text = trailing,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = if (onTrailing != null) {
                    Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onTrailing)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                } else {
                    Modifier.padding(horizontal = 8.dp)
                },
            )
        }
    }
}

@Composable
private fun AlbumCard(
    name: String,
    count: Int,
    coverUrl: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        CoverBox(
            url = coverUrl,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            corner = 16.dp,
        )
        Text(
            text = name,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            text = "$count 首",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 2.dp, top = 2.dp),
        )
    }
}

@Composable
private fun SearchBarStub(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiuixIcons.Search,
            contentDescription = "搜索",
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "搜索曲名 / 作者 / 系列 / 标签",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
internal fun SongRow(track: Track, coverUrl: String, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    BasicComponent(
        title = track.title,
        summary = listOf(track.author, track.series?.name).filter { !it.isNullOrBlank() }.joinToString(" · "),
        startAction = {
            CoverBox(
                url = coverUrl,
                modifier = Modifier.size(44.dp),
                corner = 10.dp,
            )
        },
        endActions = {
            Text(
                text = formatDuration(track.duration),
                style = MiuixTheme.textStyles.footnote2,
                color = colors.onSurfaceVariantSummary,
            )
        },
        onClick = onClick,
    )
}

@Composable
internal fun EchoGroup(
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
private fun SettingRow(title: String, summary: String, onClick: () -> Unit = {}) {
    BasicComponent(title = title, summary = summary, onClick = onClick)
}

internal fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "--:--"
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
