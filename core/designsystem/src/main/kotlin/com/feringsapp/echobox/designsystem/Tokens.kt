package com.feringsapp.echobox.designsystem

import androidx.compose.ui.unit.dp

/** 圆角统一由这里管理，UI 里禁止写魔法数字。 */
object EchoRadius {
    val chip = 20.dp
    val card = 24.dp
    val panel = 28.dp
    val nav = 26.dp
    val sheet = 28.dp
}

object EchoSpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screen = 20.dp
}

/** 模糊半径以 dp 计（miuix-blur 内部按 density 换算）。 */
object EchoBlur {
    const val bar = 16f
    const val panel = 24f
    const val player = 48f
}

object EchoMotion {
    const val damping = 0.82f
    const val stiffness = 380f
}
