package com.feringsapp.echobox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 细轨道滑块：Miuix Slider 的轨道固定为 HyperOS 粗条，播放进度与 RGB 调色需要更细的
 * 视觉，因此这里只保留 Miuix 的配色，轨道与滑块自绘。
 */
@Composable
fun EchoSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    trackColor: Color = MiuixTheme.colorScheme.primary,
    inactiveColor: Color = MiuixTheme.colorScheme.outline,
    trackHeight: Dp = 6.dp,
    thumbSize: Dp = 18.dp,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    var widthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableFloatStateOf(0f) }

    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    fun fractionOf(v: Float) = ((v - valueRange.start) / span).coerceIn(0f, 1f)
    fun valueAt(x: Float): Float {
        if (widthPx <= 0f) return value
        val fraction = (x / widthPx).coerceIn(0f, 1f)
        return valueRange.start + fraction * span
    }

    fun emit(x: Float, finished: Boolean) {
        val v = valueAt(x)
        dragging = fractionOf(v)
        onValueChange(v)
        if (finished) onValueChangeFinished?.invoke()
    }

    val fraction = if (dragging > 0f && dragging != fractionOf(value)) dragging else fractionOf(value)
    val thumbPx = with(density) { thumbSize.toPx() }

    Box(
        modifier = modifier
            .height(thumbSize + 12.dp)
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(enabled, valueRange, widthPx) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset -> emit(offset.x, true) }
            }
            .pointerInput(enabled, valueRange, widthPx) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset -> dragging = fractionOf(valueAt(offset.x)) },
                    onDragEnd = {
                        onValueChange(valueAt(dragging * widthPx))
                        onValueChangeFinished?.invoke()
                    },
                ) { change, _ ->
                    dragging = (change.position.x / widthPx).coerceIn(0f, 1f)
                    change.consume()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(inactiveColor),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(trackHeight)
                .clip(CircleShape)
                .background(trackColor),
        )
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset((fraction * widthPx - thumbPx / 2f).toInt(), 0) }
                .width(thumbSize)
                .height(thumbSize)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
