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
 * 可用：Blue = 实色主色 + 白字；Light = 实色白 + 主色字。
 * 不可用：白底 + 灰字，保持不透明，避免压在表单上透出文字。
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
    val disabledText = MiuixTheme.colorScheme.onSurfaceVariantSummary

    val fill = when {
        !enabled -> Color.White
        variant == ActionBarVariant.Blue -> accent
        else -> Color.White
    }
    val textColor = when {
        !enabled -> disabledText
        contentColor != null -> contentColor
        variant == ActionBarVariant.Blue -> Color.White
        else -> accent
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(if (variant == ActionBarVariant.Blue && enabled) 10.dp else 8.dp, shape)
            .clip(shape)
            .background(fill)
            .then(
                if (enabled && variant == ActionBarVariant.Blue) {
                    Modifier
                } else {
                    Modifier.border(0.8.dp, Color(0x14000000), shape)
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            style = MiuixTheme.textStyles.body1,
        )
    }
}
