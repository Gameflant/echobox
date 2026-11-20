package com.feringsapp.echobox.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.feringsapp.echobox.core.library.LrcParser
import com.feringsapp.echobox.core.library.LyricLine
import com.feringsapp.echobox.designsystem.EchoGlass
import com.feringsapp.echobox.designsystem.EchoSlider
import com.feringsapp.echobox.designsystem.LocalAccentColor
import com.feringsapp.echobox.designsystem.LocalGlassIntensity
import com.feringsapp.echobox.designsystem.LocalGlassMode
import com.feringsapp.echobox.media.PlaybackController
import com.feringsapp.echobox.media.PlaybackUiState
import kotlinx.coroutines.flow.MutableStateFlow
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

val LocalPlaybackController = staticCompositionLocalOf<PlaybackController?> { null }

private val DarkTop = Color(0xFF171A21)
private val DarkBottom = Color(0xFF0B0D11)

@Composable
fun PlayerScreen(
    onBack: () -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val controller = LocalPlaybackController.current
    val empty = remember { MutableStateFlow(PlaybackUiState()) }
    val state by (controller?.state ?: empty).collectAsState()
    val accent = LocalAccentColor.current

    val background = remember(accent) {
        Brush.verticalGradient(
            colors = listOf(
                accent.copy(alpha = 0.32f).compositeOver(DarkTop),
                DarkTop,
                DarkBottom,
            ),
        )
    }
    val coverBrush = remember(accent) {
        Brush.linearGradient(
            listOf(
                accent.copy(alpha = 0.75f),
                accent.copy(alpha = 0.32f),
                Color(0xFF2A2F3A),
            ),
        )
    }

    // 只有背景与封面进入 backdrop，玻璃控件作为兄弟层读取，避免自身被记录后递归
    val backdrop = rememberLayerBackdrop { drawContent() }
    val mode = LocalGlassMode.current
    val intensity = LocalGlassIntensity.current
    val glassStyle = remember(mode, intensity) { EchoGlass.style(mode, intensity, isDark = true) }
    val glassMaterial = remember(mode, intensity) { EchoGlass.material(mode, intensity, isDark = true) }

    var showLyrics by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val dismissThreshold = with(density) { 160.dp.toPx() }

    val lines = remember(state.lyric) { LrcParser.parse(state.lyric) }
    val currentLine = remember(lines, state.positionMs) { LrcParser.indexAt(lines, state.positionMs) }

    val duration = state.durationMs.coerceAtLeast(1L)
    var dragging by remember { mutableFloatStateOf(-1f) }
    val position = if (dragging >= 0f) dragging else state.positionMs.toFloat()

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { translationY = dragOffset }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffset > dismissThreshold) onBack()
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                ) { _, dragAmount ->
                    if (dragAmount > 0 || dragOffset > 0f) {
                        dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                    }
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(background)
                .layerBackdrop(backdrop),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = 110.dp, bottom = 260.dp, start = 44.dp, end = 44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(coverBrush),
                ) {
                    if (state.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = state.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.14f)),
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.08f)),
                    )
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    onClick = onBack,
                    backdrop = backdrop,
                    style = glassStyle,
                    material = glassMaterial,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Back,
                        contentDescription = "返回",
                        tint = Color.White,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.title.ifBlank { "未在播放" },
                        style = MiuixTheme.textStyles.title3,
                        color = Color.White,
                    )
                    Text(
                        text = state.artist.ifBlank { "EchoBox" },
                        style = MiuixTheme.textStyles.footnote2,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
                GlassIconButton(
                    onClick = onOpenQueue,
                    backdrop = backdrop,
                    style = glassStyle,
                    material = glassMaterial,
                ) {
                    Canvas(Modifier.size(18.dp)) {
                        repeat(3) { i ->
                            val y = size.height * (0.2f + i * 0.3f)
                            drawLine(
                                color = Color.White,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                }
                GlassIconButton(
                    onClick = { showLyrics = !showLyrics },
                    backdrop = backdrop,
                    style = glassStyle,
                    material = glassMaterial,
                ) {
                    Text(
                        text = "词",
                        style = MiuixTheme.textStyles.body2,
                        color = Color.White,
                    )
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { showLyrics = !showLyrics },
                contentAlignment = Alignment.Center,
            ) {
                if (showLyrics) {
                    LyricsView(lines = lines, currentIndex = currentLine)
                }
            }

            EchoSlider(
                value = position.coerceIn(0f, duration.toFloat()),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    if (dragging >= 0f) controller?.seekTo(dragging.toLong())
                    dragging = -1f
                },
                valueRange = 0f..duration.toFloat(),
                trackColor = accent,
                inactiveColor = Color.White.copy(alpha = 0.18f),
                thumbSize = 16.dp,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatMs(position.toLong()),
                    style = MiuixTheme.textStyles.footnote2,
                    color = Color.White.copy(alpha = 0.55f),
                )
                Text(
                    text = formatMs(state.durationMs),
                    style = MiuixTheme.textStyles.footnote2,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 30.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIconButton(
                    icon = { tint ->
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = "循环模式",
                            tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) tint.copy(alpha = 0.55f) else accent,
                        )
                    },
                    onClick = { controller?.cycleRepeat() },
                )
                PlayerIconButton(
                    enabled = state.queueSize > 1,
                    icon = { tint ->
                        Icon(
                            imageVector = MiuixIcons.ChevronBackward,
                            contentDescription = "上一首",
                            tint = tint,
                        )
                    },
                    onClick = { controller?.previous() },
                )
                GlassIconButton(
                    onClick = { controller?.toggle() },
                    backdrop = backdrop,
                    style = glassStyle,
                    material = glassMaterial,
                    size = 72.dp,
                    shape = top.yukonga.miuix.kmp.material.GlassShape(36.dp),
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) MiuixIcons.Pause else MiuixIcons.Play,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
                PlayerIconButton(
                    enabled = state.queueSize > 1,
                    icon = { tint ->
                        Icon(
                            imageVector = MiuixIcons.ChevronForward,
                            contentDescription = "下一首",
                            tint = tint,
                        )
                    },
                    onClick = { controller?.next() },
                )
                PlayerIconButton(
                    icon = { tint ->
                        Icon(
                            imageVector = if (isFavorite) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                            contentDescription = if (isFavorite) "取消收藏" else "收藏",
                            tint = if (isFavorite) accent else tint,
                        )
                    },
                    onClick = onToggleFavorite,
                )
            }
        }
    }
}

@Composable
private fun PlayerIconButton(
    enabled: Boolean = true,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
) {
    val tint = if (enabled) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.28f)
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon(tint)
    }
}

@Composable
private fun LyricsView(lines: List<LyricLine>, currentIndex: Int) {
    if (lines.isEmpty()) {
        Text(
            text = "暂无歌词",
            style = MiuixTheme.textStyles.body2,
            color = Color.White.copy(alpha = 0.45f),
        )
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val active = index == currentIndex
            Text(
                text = line.text,
                style = if (active) MiuixTheme.textStyles.title3 else MiuixTheme.textStyles.body2,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) Color.White else Color.White.copy(alpha = 0.38f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

private fun Color.compositeOver(background: Color): Color {
    val alpha = this.alpha
    if (alpha >= 1f) return this
    val r = this.red * alpha + background.red * (1 - alpha)
    val g = this.green * alpha + background.green * (1 - alpha)
    val b = this.blue * alpha + background.blue * (1 - alpha)
    return Color(r, g, b, 1f)
}
