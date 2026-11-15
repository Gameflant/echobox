package com.feringsapp.echobox.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feringsapp.echobox.core.library.MediaStorage
import com.feringsapp.echobox.core.model.Track
import com.feringsapp.echobox.core.repo.DownloadRepository
import com.feringsapp.echobox.designsystem.CoverBox
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 本地音乐：只显示已经下载完成的曲目。 */
@Composable
fun LocalMusicScreen(
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadRepo = LocalDownloadRepository.current
    val trackRepo = LocalTrackRepository.current
    val coverBase = LocalCoverBase.current
    val scope = rememberCoroutineScope()

    val downloads by downloadRepo.observeAll().collectAsState(initial = emptyList())
    val tracks by trackRepo.observeLatest(500).collectAsState(initial = emptyList())
    val byId = tracks.associateBy { it.id }

    val localTracks = downloads
        .filter { it.done && it.file != null }
        .mapNotNull { item -> byId[item.trackId]?.let { track -> track to item } }

    val totalBytes = localTracks.sumOf { it.second.file?.length() ?: 0L }

    EchoScreen(
        title = "本地音乐",
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
            }
        },
        actions = {
            if (localTracks.isNotEmpty()) {
                IconButton(
                    onClick = { onPlay(localTracks.map { it.first }, 0) },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Play,
                        contentDescription = "全部播放",
                    )
                }
            }
        },
    ) {
        if (localTracks.isEmpty()) {
            item {
                SubCard {
                    BasicComponent(
                        title = "还没有下载的曲目",
                        summary = "在曲目详情页点「下载」，下载完成后会出现在这里",
                    )
                }
            }
        } else {
            item {
                SubCard(title = "已下载 · ${localTracks.size} 首 · ${MediaStorage.format(totalBytes)}") {
                    localTracks.forEachIndexed { index, (track, item) ->
                        LocalRow(
                            track = track,
                            item = item,
                            coverUrl = if (track.cover.isBlank()) "" else coverBase + track.cover,
                            onClick = { onPlay(localTracks.map { it.first }, index) },
                            onDelete = {
                                scope.launch {
                                    downloadRepo.delete(track.id, track.file)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalRow(
    track: Track,
    item: DownloadRepository.Item,
    coverUrl: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    BasicComponent(
        title = track.title,
        summary = buildString {
            append(track.author)
            append(" · ")
            append(track.format.uppercase())
            append(" · ")
            append(MediaStorage.format(item.file?.length() ?: 0L))
        },
        startAction = {
            CoverBox(
                url = coverUrl,
                modifier = Modifier.size(44.dp),
                corner = 10.dp,
            )
        },
        endActions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDuration(track.duration),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Box(
                    Modifier
                        .size(36.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = "删除",
                        )
                    }
                }
            }
        },
        onClick = onClick,
    )
}
