package com.feringsapp.echobox.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 玻璃的采样底：两个大色块 + 竖向渐变，避免模糊层采到透明像素出现色块。 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.background, colors.surfaceVariant))),
    ) {
        Box(
            Modifier
                .size(300.dp)
                .offset(x = (-70).dp, y = 90.dp)
                .blur(110.dp)
                .background(colors.primary.copy(alpha = 0.38f), CircleShape),
        )
        Box(
            Modifier
                .size(260.dp)
                .offset(x = 210.dp, y = 420.dp)
                .blur(120.dp)
                .background(colors.tertiaryContainer.copy(alpha = 0.5f), CircleShape),
        )
    }
}
