package com.feringsapp.echobox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class ActionBarVariant {
    /** 蓝底白字，主操作用。 */
    Blue,

    /** 白底主色字，次级操作用。 */
    Light,
}

/**
 * 悬浮主操作条：内容再长也不会把它挤出屏幕。
 * Blue = 实色主色 + 白字；Light = 白色半透明 + 主色字。
 */
@Composable
fun GlassActionBar(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ActionBarVariant = ActionBarVariant.Light,
    contentColor: Color? = null,
) {
    val shape = RoundedCornerShape(26.dp)
    val accent = LocalAccentColor.current
    val alpha = if (enabled) 1f else 0.45f
    val fill = when (variant) {
        ActionBarVariant.Blue -> accent
        ActionBarVariant.Light -> Color.White.copy(alpha = 0.94f)
    }
    val textColor = contentColor ?: when (variant) {
        ActionBarVariant.Blue -> Color.White
        ActionBarVariant.Light -> accent
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(if (variant == ActionBarVariant.Blue) 10.dp else 8.dp, shape)
            .clip(shape)
            .background(fill.copy(alpha = fill.alpha * alpha))
            .then(
                if (variant == ActionBarVariant.Light) {
                    Modifier.border(0.8.dp, Color(0x14000000), shape)
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor.copy(alpha = alpha),
            style = MiuixTheme.textStyles.body1,
        )
    }
}
