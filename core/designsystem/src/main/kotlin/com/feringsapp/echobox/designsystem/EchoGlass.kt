package com.feringsapp.echobox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.feringsapp.echobox.core.settings.GlassMode
import top.yukonga.miuix.kmp.material.GlassColorBlendMode
import top.yukonga.miuix.kmp.material.GlassColorLayer
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassMaterials
import top.yukonga.miuix.kmp.material.GlassRefract
import top.yukonga.miuix.kmp.material.GlassStyle

/**
 * 两种玻璃质感共用一套 Miuix Glass 管线：
 * - 液态：保留折射（ior）、高光、边缘亮带；
 * - 毛玻璃：折射归零、高光关闭，只留大半径模糊和高白 tint。
 */
object EchoGlass {

    /** 强度 0.4..1.6 映射到 4..44dp，端点差异明显，1.0 附近过渡平滑。 */
    fun blurRadiusFor(k: Float): androidx.compose.ui.unit.Dp {
        val clamped = k.coerceIn(0.4f, 1.6f)
        return (4f + 40f * (clamped - 0.4f) / 1.2f).dp
    }

    fun style(mode: GlassMode, intensity: Float, isDark: Boolean = false): GlassStyle {
        val base = GlassDefaults.style(isDark)
        val k = intensity.coerceIn(0.2f, 2f)
        return when (mode) {
            GlassMode.LIQUID -> base.copy(
                blur = base.blur.copy(small = base.blur.small * k, big = base.blur.big * k),
                edge = base.edge.copy(
                    width = base.edge.width * k,
                    thickness = base.edge.thickness * k,
                ),
                inner = base.inner.copy(
                    tint = Color(0xFFDCEBFF),
                    tintStrength = 0.35f,
                    colorWhite = 0.1f,
                ),
                background = base.background.copy(saturation = 0.12f, brightness = 0.02f),
            )

            GlassMode.FROSTED -> base.copy(
                refract = GlassRefract(ior = 1.0f),
                reflect = base.reflect.copy(strength = 0f),
                edge = base.edge.copy(width = 0f, thickness = 0f),
                light = base.light.copy(intensity = 0f, oppositeIntensity = 0f),
                inner = base.inner.copy(
                    tint = Color.White,
                    tintStrength = (0.55f * k).coerceIn(0f, 0.9f),
                ),
                blur = base.blur.copy(small = 80f * k, big = 900f * k),
            )
        }
    }

    fun material(mode: GlassMode, intensity: Float, isDark: Boolean = false): GlassMaterial {
        val base = GlassMaterials.puredThinGlass(isDark)
        val k = intensity.coerceIn(0.2f, 2f)
        return when (mode) {
            GlassMode.LIQUID -> base.copy(
                blurRadius = blurRadiusFor(k),
                first = GlassColorLayer(Color(0x1A000000), GlassColorBlendMode.PlusDarker),
                second = GlassColorLayer(Color(0xB3FFFFFF), GlassColorBlendMode.SoftLight),
                third = GlassColorLayer(Color(0x80D8EAFF), GlassColorBlendMode.HardLight),
            )
            GlassMode.FROSTED -> base.copy(
                blurRadius = blurRadiusFor(k) * 1.6f,
                first = GlassColorLayer(Color(0x0F000000), GlassColorBlendMode.PlusDarker),
                second = GlassColorLayer(Color(0xBFFFFFFF), GlassColorBlendMode.SoftLight),
                third = GlassColorLayer(Color(0x99FFFFFF), GlassColorBlendMode.HardLight),
            )
        }
    }
}
