package com.feringsapp.echobox.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.feringsapp.echobox.core.settings.AppearancePalette
import com.feringsapp.echobox.core.settings.GlassMode
import com.feringsapp.echobox.designsystem.EchoGlass
import com.feringsapp.echobox.designsystem.LocalAppearance
import com.feringsapp.echobox.designsystem.LocalAppearanceStore
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import com.feringsapp.echobox.designsystem.EchoSlider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appearance = LocalAppearance.current
    val store = LocalAppearanceStore.current
    val scope = rememberCoroutineScope()

    EchoScreen(
        title = "外观",
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
            }
        },
    ) {
        item {
            SubCard(title = "背景颜色") {
                ColorSection(
                    options = AppearancePalette.backgrounds,
                    current = appearance.background,
                    onPick = { scope.launch { store.setBackground(it) } },
                )
            }
        }
        item {
            SubCard(title = "强调色") {
                ColorSection(
                    options = AppearancePalette.accents,
                    current = appearance.accent,
                    onPick = { scope.launch { store.setAccent(it) } },
                )
            }
        }
        item {
            SubCard(title = "玻璃") {
                var previewIntensity by remember { mutableFloatStateOf(appearance.glassIntensity) }
                GlassModeRow(
                    title = "毛玻璃",
                    summary = "只有背景模糊，无折射与高光",
                    selected = appearance.glassMode == GlassMode.FROSTED,
                    onClick = { scope.launch { store.setGlassMode(GlassMode.FROSTED) } },
                )
                GlassModeRow(
                    title = "液态玻璃",
                    summary = "折射、高光与边缘亮带",
                    selected = appearance.glassMode == GlassMode.LIQUID,
                    onClick = { scope.launch { store.setGlassMode(GlassMode.LIQUID) } },
                )
                GlassPreview(
                    intensity = previewIntensity,
                    mode = appearance.glassMode,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                SliderRow(
                    title = "玻璃强度",
                    value = appearance.glassIntensity,
                    valueRange = 0.4f..1.6f,
                    format = { "%.2f×".format(it) },
                    onPreview = { previewIntensity = it },
                    onCommit = {
                        previewIntensity = it
                        scope.launch { store.setGlassIntensity(it) }
                    },
                )
            }
        }
        item {
            SubCard(title = "动效") {
                SliderRow(
                    title = "动画速度",
                    value = appearance.motionScale,
                    valueRange = 0.5f..2f,
                    format = { "%.2f×".format(it) },
                    onCommit = { scope.launch { store.setMotionScale(it) } },
                )
                Text(
                    text = "数值越大，页面切换越慢",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun ColorSection(
    options: List<Pair<String, Long>>,
    current: Long,
    onPick: (Long) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            options.forEach { (_, argb) ->
                val color = Color(argb)
                val selected = argb == current
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable { onPick(argb) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Text(
                            text = "✓",
                            style = MiuixTheme.textStyles.footnote2,
                            color = if (color.luminance() > 0.6f) Color.Black else Color.White,
                        )
                    }
                }
            }
        }

        var expanded by remember { mutableStateOf(false) }
        BasicComponent(
            title = "自定义 RGB",
            summary = "#%06X".format(current and 0xFFFFFF),
            endActions = {
                Icon(
                    imageVector = if (expanded) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            },
            onClick = { expanded = !expanded },
        )
        if (expanded) {
            RgbEditor(argb = current, onCommit = onPick)
        }
    }
}

@Composable
private fun RgbEditor(argb: Long, onCommit: (Long) -> Unit) {
    val red = ((argb shr 16) and 0xFF).toInt()
    val green = ((argb shr 8) and 0xFF).toInt()
    val blue = (argb and 0xFF).toInt()
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        ChannelSlider("R", red, Color(0xFFFF5A5A)) { onCommit(composeArgb(it, green, blue)) }
        ChannelSlider("G", green, Color(0xFF2E9E5B)) { onCommit(composeArgb(red, it, blue)) }
        ChannelSlider("B", blue, Color(0xFF3482FF)) { onCommit(composeArgb(red, green, it)) }
    }
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Int,
    trackColor: Color,
    onCommit: (Int) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(16.dp),
        )
        EchoSlider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            trackColor = trackColor,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = local.toInt().toString(),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(34.dp),
        )
    }
}

@Composable
private fun GlassModeRow(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        endActions = {
            if (selected) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = "已选中",
                    tint = MiuixTheme.colorScheme.primary,
                )
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onCommit: (Float) -> Unit,
    onPreview: ((Float) -> Unit)? = null,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, style = MiuixTheme.textStyles.body2)
            Text(
                text = format(local),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        EchoSlider(
            value = local,
            onValueChange = {
                local = it
                onPreview?.invoke(it)
            },
            onValueChangeFinished = { onCommit(local) },
            valueRange = valueRange,
        )
    }
}

/** 外观页专用预览：彩色渐变打底，叠玻璃层，拖动时实时反映强度。 */
@Composable
private fun GlassPreview(intensity: Float, mode: GlassMode, modifier: Modifier = Modifier) {
    val k = intensity.coerceIn(0.4f, 1.6f)
    val blurDp = EchoGlass.blurRadiusFor(k) * if (mode == GlassMode.FROSTED) 1.6f else 1f
    val tint = if (mode == GlassMode.FROSTED) {
        (0.28f + 0.36f * k).coerceIn(0.26f, 0.86f)
    } else {
        (0.06f + 0.10f * k).coerceIn(0.05f, 0.22f)
    }
    val edge = (0.12f + 0.35f * k).coerceIn(0.1f, 0.7f)
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(shape),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .blur(blurDp, BlurredEdgeTreatment.Unbounded)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFF8A3D), Color(0xFF7B61FF), Color(0xFF2ED0C5)),
                    ),
                ),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(Color.White.copy(alpha = tint)),
        )
        Box(
            Modifier
                .matchParentSize()
                .border(1.dp, Color.White.copy(alpha = edge), shape),
        )
    }
}

private fun composeArgb(r: Int, g: Int, b: Int): Long =
    0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
