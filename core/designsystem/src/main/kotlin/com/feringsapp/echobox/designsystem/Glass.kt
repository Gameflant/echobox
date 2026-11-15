package com.feringsapp.echobox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.GlassMaterials
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 液态玻璃表面，直接使用 Miuix 的 glassPanel：
 * 背景采样、模糊、颜色混合、边缘高光全部由 miuix-glass / miuix-material 完成。
 * 没有 backdrop、关闭模糊、或运行时无 RuntimeShader 时退化为半透明纯色。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    radius: Dp = EchoRadius.card,
    fallbackAlpha: Float = 0.96f,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    val enabled = LocalGlassEnabled.current && backdrop != null && isRuntimeShaderSupported()
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f

    val surface = if (enabled) {
        Modifier.glassPanel(
            backdrop = backdrop,
            shape = GlassShape(radius),
            style = GlassDefaults.style(isDark),
            material = GlassMaterials.puredThinGlass(isDark),
            stroke = GlassEdgeStrokes.forTheme(isDark),
            shading = false,
        )
    } else {
        Modifier
            .clip(RoundedCornerShape(radius))
            .background(MiuixTheme.colorScheme.surface.copy(alpha = fallbackAlpha))
    }

    Box(modifier.then(surface)) { content() }
}
