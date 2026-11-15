package com.feringsapp.echobox.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import com.feringsapp.echobox.core.model.Authorization
import com.feringsapp.echobox.core.model.Track
import com.feringsapp.echobox.designsystem.ActionBarVariant
import com.feringsapp.echobox.designsystem.CoverBox
import com.feringsapp.echobox.designsystem.EchoGlass
import com.feringsapp.echobox.designsystem.GlassActionBar
import com.feringsapp.echobox.designsystem.LocalAccentColor
import com.feringsapp.echobox.designsystem.LocalGlassIntensity
import com.feringsapp.echobox.designsystem.LocalGlassMode
import com.feringsapp.echobox.feature.player.LocalPlaybackController
import com.feringsapp.echobox.media.PlaybackUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TrackDetailScreen(
    trackId: String,
    onBack: () -> Unit,
    onPlay: (Track) -> Unit = {},
    onOpenLocal: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val repo = LocalTrackRepository.current
    val libraryStore = LocalLibraryStore.current
    val downloadRepo = LocalDownloadRepository.current
    val playback = LocalPlaybackController.current
    val coverBase = LocalCoverBase.current
    val accent = LocalAccentColor.current
    val scope = rememberCoroutineScope()

    val track by repo.observeTrack(trackId).collectAsState(initial = null)
    val favorites by libraryStore.favorites.collectAsState(initial = emptySet())
    val download by downloadRepo.observe(trackId).collectAsState(initial = null)
    val playbackState by (playback?.state ?: remember { MutableStateFlow(PlaybackUiState()) })
        .collectAsState()

    var error by remember { mutableStateOf("") }

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

    val playingThis = playbackState.trackId == trackId && playbackState.hasTrack
    val downloaded = download?.done == true

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            GlassTopAppBar(
                title = "曲目",
                backdrop = backdrop,
                style = glassStyle,
                material = glassMaterial,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { libraryStore.toggleFavorite(trackId) } }) {
                        Icon(
                            imageVector = if (trackId in favorites) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                            contentDescription = if (trackId in favorites) "取消收藏" else "收藏",
                        )
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
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = 140.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        CoverBox(
                            url = track?.cover?.takeIf { it.isNotBlank() }?.let { coverBase + it }.orEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                            corner = 16.dp,
                        )
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            BasicComponent(
                                title = track?.title ?: "曲目不存在",
                                summary = track?.author.orEmpty(),
                            )
                            track?.let { t ->
                                BasicComponent(title = "授权", summary = Authorization.label(t.authorization))
                                if (t.resourceLicense.isNotBlank()) {
                                    BasicComponent(title = "资源协议", summary = t.resourceLicense)
                                }
                                t.series?.let { series ->
                                    val position = if (series.index != null && series.total != null) {
                                        "${series.index}/${series.total}"
                                    } else {
                                        ""
                                    }
                                    BasicComponent(
                                        title = "系列",
                                        summary = listOf(series.name, position).filter { it.isNotBlank() }.joinToString(" · "),
                                    )
                                }
                                BasicComponent(
                                    title = "音频",
                                    summary = listOf(t.format.uppercase(), formatDuration(t.duration)).joinToString(" · "),
                                )
                                if (t.tags.isNotEmpty()) {
                                    BasicComponent(title = "标签", summary = t.tags.joinToString(" / "))
                                }
                                BasicComponent(
                                    title = "播放方式",
                                    summary = if (downloaded) "本地文件" else "在线流媒体",
                                )
                            }
                        }
                    }
                    if (error.isNotBlank()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                BasicComponent(title = "下载失败", summary = error)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassActionBar(
                    text = when {
                        !playingThis -> if (downloaded) "播放（本地）" else "播放"
                        playbackState.isPlaying -> "暂停"
                        else -> "继续播放"
                    },
                    onClick = {
                        if (playingThis) {
                            playback?.toggle()
                        } else {
                            track?.let(onPlay)
                        }
                    },
                    enabled = track != null,
                    variant = ActionBarVariant.Blue,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White.copy(alpha = 0.94f))
                        .then(
                            Modifier
                                .clip(RoundedCornerShape(26.dp)),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    DownloadButton(
                        downloaded = downloaded,
                        running = download?.running == true,
                        accent = accent,
                        onClick = {
                            if (downloaded) {
                                onOpenLocal()
                            } else if (download?.running != true) {
                                val target = track
                                if (target != null) {
                                    error = ""
                                    // 任务挂在仓库级 scope，退出页面不会中断
                                    downloadRepo.start(target)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadButton(
    downloaded: Boolean,
    running: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        when {
            downloaded -> Icon(
                imageVector = MiuixIcons.FavoritesFill,
                contentDescription = "已下载",
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
            running -> Text(
                text = "…",
                style = MiuixTheme.textStyles.body2,
                color = accent,
            )
            else -> Icon(
                imageVector = MiuixIcons.Download,
                contentDescription = "下载",
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
