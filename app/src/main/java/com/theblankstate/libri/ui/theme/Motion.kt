package com.theblankstate.libri.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

object MotionTokens {
    const val PressedScale = 0.96f
    const val LiftedScale = 1.02f

    fun <T> springDefault(): SpringSpec<T> = spring(
        dampingRatio = 0.8f,
        stiffness = 380f
    )

    fun <T> springBouncy(): SpringSpec<T> = spring(
        dampingRatio = 0.6f,
        stiffness = 300f
    )

    fun <T> springGentle(): SpringSpec<T> = spring(
        dampingRatio = 0.9f,
        stiffness = 200f
    )
}
