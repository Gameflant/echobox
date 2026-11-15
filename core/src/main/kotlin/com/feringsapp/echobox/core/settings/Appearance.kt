package com.feringsapp.echobox.core.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 玻璃质感：毛玻璃只有模糊，液态玻璃有折射、高光与边缘。 */
enum class GlassMode { FROSTED, LIQUID }

data class Appearance(
    /** ARGB，默认纯白。 */
    val background: Long = 0xFFFFFFFF,
    /** ARGB，默认澎湃蓝 #3482FF。 */
    val accent: Long = 0xFF3482FF,
    val glassMode: GlassMode = GlassMode.LIQUID,
    /** 玻璃强度倍数，0.4 到 1.6。 */
    val glassIntensity: Float = 1f,
    /** 动画时长倍数，0.5 到 2.0，越大越慢。 */
    val motionScale: Float = 1f,
)

object AppearancePalette {
    val backgrounds = listOf(
        "纯白" to 0xFFFFFFFF,
        "浅灰" to 0xFFF7F7F7,
        "暖白" to 0xFFFDFBF7,
        "雾蓝" to 0xFFF3F6FB,
    )

    val accents = listOf(
        "澎湃蓝" to 0xFF3482FF,
        "紫" to 0xFF7A5AF8,
        "青" to 0xFF12A594,
        "橙" to 0xFFF08A24,
        "红" to 0xFFE94634,
    )
}

private val Context.appearanceStore by preferencesDataStore(name = "appearance")

class AppearanceStore(private val context: Context) {
    private val keyBackground = longPreferencesKey("background")
    private val keyAccent = longPreferencesKey("accent")
    private val keyGlassMode = intPreferencesKey("glass_mode")
    private val keyGlassIntensity = floatPreferencesKey("glass_intensity")
    private val keyMotionScale = floatPreferencesKey("motion_scale")

    val flow: Flow<Appearance> = context.appearanceStore.data.map { prefs ->
        Appearance(
            background = prefs[keyBackground] ?: 0xFFFFFFFF,
            accent = prefs[keyAccent] ?: 0xFF3482FF,
            glassMode = if ((prefs[keyGlassMode] ?: 1) == 1) GlassMode.LIQUID else GlassMode.FROSTED,
            glassIntensity = prefs[keyGlassIntensity] ?: 1f,
            motionScale = prefs[keyMotionScale] ?: 1f,
        )
    }

    suspend fun setBackground(argb: Long) {
        context.appearanceStore.edit { it[keyBackground] = argb }
    }

    suspend fun setAccent(argb: Long) {
        context.appearanceStore.edit { it[keyAccent] = argb }
    }

    suspend fun setGlassMode(mode: GlassMode) {
        context.appearanceStore.edit { it[keyGlassMode] = if (mode == GlassMode.LIQUID) 1 else 0 }
    }

    suspend fun setGlassIntensity(value: Float) {
        context.appearanceStore.edit { it[keyGlassIntensity] = value }
    }

    suspend fun setMotionScale(value: Float) {
        context.appearanceStore.edit { it[keyMotionScale] = value }
    }
}
