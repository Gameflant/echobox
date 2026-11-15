package com.feringsapp.echobox.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feringsapp.echobox.core.model.Track
import com.feringsapp.echobox.designsystem.ActionBarVariant
import com.feringsapp.echobox.designsystem.CoverBox
import com.feringsapp.echobox.designsystem.GlassActionBar
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 专辑页：大封面 + 曲目列表 + 播放全部 / 随机播放。
 */
@Composable
fun AlbumScreen(
    album: String,
    tracks: List<Track>,
    coverBase: String,
    onBack: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cover = remember(tracks) {
        tracks.firstOrNull { it.cover.isNotBlank() }?.cover.orEmpty()
    }

    EchoScreen(
        title = "专辑",
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
            }
        },
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            ) {
                CoverBox(
                    url = if (cover.isBlank()) "" else coverBase + cover,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    corner = 20.dp,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = album,
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${tracks.size} 首",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlassActionBar(
                        text = "播放全部",
                        onClick = onPlayAll,
                        modifier = Modifier.weight(1f),
                        variant = ActionBarVariant.Blue,
                    )
                    GlassActionBar(
                        text = "随机",
                        onClick = onShuffle,
                        modifier = Modifier.weight(0.6f),
                        variant = ActionBarVariant.Light,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                if (tracks.isEmpty()) {
                    BasicComponent(title = "这张专辑没有曲目", summary = "检查索引或网络源")
                } else {
                    tracks.forEachIndexed { index, track ->
                        SongRow(
                            track = track,
                            coverUrl = if (track.cover.isBlank()) "" else coverBase + track.cover,
                            onClick = { onPlayAt(index) },
                        )
                    }
                }
            }
        }
    }
}
