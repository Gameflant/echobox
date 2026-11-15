package com.feringsapp.echobox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * 封面：地址为空或加载失败时退回主色渐变占位，不留白块。
 */
@Composable
fun CoverBox(
    url: String,
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp,
) {
    val accent = LocalAccentColor.current
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.35f)),
                ),
            ),
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
            )
        }
    }
}
