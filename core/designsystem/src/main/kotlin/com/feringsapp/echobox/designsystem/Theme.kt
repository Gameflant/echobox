package com.feringsapp.echobox.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.feringsapp.echobox.core.settings.Appearance
import com.feringsapp.echobox.core.settings.AppearanceStore
import com.feringsapp.echobox.core.settings.GlassMode
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** 是否允许玻璃效果（设置项 + 低端机降级都会改它）。 */
val LocalGlassEnabled = staticCompositionLocalOf { true }

/** 当前页面用于采样的背景图层，null 时玻璃退化为纯色。 */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** 当前强调色，底栏图标、选中态统一取这里。 */
val LocalAccentColor = staticCompositionLocalOf { Color(0xFF3482FF) }

/** 当前背景色。 */
val LocalBackgroundColor = staticCompositionLocalOf { Color.White }

/** 当前外观设置，设置页用来显示选中项。 */
val LocalAppearance = staticCompositionLocalOf { Appearance() }

/** 外观设置的写入入口。 */
val LocalAppearanceStore = staticCompositionLocalOf<AppearanceStore> { error("AppearanceStore 未提供") }

/** 当前玻璃质感模式。 */
val LocalGlassMode = staticCompositionLocalOf { GlassMode.LIQUID }

/** 当前玻璃强度倍数。 */
val LocalGlassIntensity = staticCompositionLocalOf { 1f }

/** 当前动画时长倍数，越大越慢。 */
val LocalMotionScale = staticCompositionLocalOf { 1f }

@Composable
fun EchoBoxTheme(
    appearance: Appearance = Appearance(),
    glassEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val accent = Color(appearance.accent)
    val background = Color(appearance.background)
    val card = if (background == Color.White) Color(0xFFF7F7F7) else Color.White
    val media = if (background == Color.White) Color(0xFFEDEDED) else Color(0xFFF2F2F2)
    val colors = remember(appearance) {
        lightColorScheme(
            primary = accent,
            primaryVariant = accent,
            primaryContainer = accent,
            tertiaryContainer = accent.copy(alpha = 0.12f),
            onTertiaryContainer = accent,
            background = background,
            surface = background,
            surfaceContainer = card,
            surfaceContainerHigh = card,
            surfaceVariant = media,
        )
    }
    CompositionLocalProvider(
        LocalGlassEnabled provides glassEnabled,
        LocalAccentColor provides accent,
        LocalBackgroundColor provides background,
        LocalAppearance provides appearance,
        LocalGlassMode provides appearance.glassMode,
        LocalGlassIntensity provides appearance.glassIntensity,
        LocalMotionScale provides appearance.motionScale,
    ) {
        MiuixTheme(colors = colors, content = content)
    }
}
