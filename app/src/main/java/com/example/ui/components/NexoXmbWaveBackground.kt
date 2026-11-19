package com.example.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun NexoXmbWaveBackground(modifier: Modifier = Modifier) {
    val palette = NexoEvaThemeManager.activePalette
    val primary = palette.primary
    val secondary = palette.secondary
    val transition = rememberInfiniteTransition(label = "nexo_xmb_wave")

    val phase1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )
    val phase2 by transition.animateFloat(
        initialValue = (2.0 * PI).toFloat(),
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 23000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 19000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val steps = 44

        fun drawRibbon(
            baseYRatio: Float,
            amp: Float,
            freq: Float,
            phase: Float,
            ribbonThickness: Float,
            color: Color,
            fillAlpha: Float,
            strokeAlpha: Float
        ) {
            val topPath = Path()
            val fillPath = Path()
            val baseY = h * baseYRatio
            for (i in 0..steps) {
                val frac = i.toFloat() / steps
                val x = w * frac
                val y = baseY + sin(frac * freq * PI.toFloat() + phase) * amp +
                    sin(frac * (freq * 0.55f) * PI.toFloat() - phase * 0.7f) * (amp * 0.45f)
                if (i == 0) {
                    topPath.moveTo(x, y)
                    fillPath.moveTo(x, y)
                } else {
                    topPath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            for (i in steps downTo 0) {
                val frac = i.toFloat() / steps
                val x = w * frac
                val y = baseY + sin(frac * freq * PI.toFloat() + phase) * amp +
                    sin(frac * (freq * 0.55f) * PI.toFloat() - phase * 0.7f) * (amp * 0.45f) +
                    ribbonThickness * (0.65f + 0.35f * sin(frac * PI.toFloat() + phase * 0.5f))
                fillPath.lineTo(x, y)
            }
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = fillAlpha), color.copy(alpha = fillAlpha * 0.15f), Color.Transparent),
                    startY = baseY - amp,
                    endY = baseY + amp + ribbonThickness * 1.4f
                )
            )
            drawPath(
                path = topPath,
                color = color.copy(alpha = strokeAlpha),
                style = Stroke(width = 1.6f)
            )
        }

        // Tres ondas translúcidas muy sutiles estilo PS3 XMB
        drawRibbon(0.36f, h * 0.032f, 2.0f, phase1, h * 0.065f, primary, 0.055f, 0.11f)
        drawRibbon(0.42f, h * 0.042f, 1.6f, phase2 + 1.2f, h * 0.080f, secondary, 0.045f, 0.09f)
        drawRibbon(0.48f, h * 0.026f, 2.5f, phase1 * 1.3f + 2.6f, h * 0.050f, Color.White, 0.032f, 0.07f)

        // Partículas luminosas diminutas flotando sobre la onda
        for (p in 0 until 14) {
            val seed = p * 0.37f
            val px = ((drift + seed) % 1f) * w
            val waveY = h * 0.42f + sin((px / w) * 2.0f * PI.toFloat() + phase1) * (h * 0.04f)
            val py = waveY + sin(phase2 + p * 1.7f) * (h * 0.035f)
            val radius = if (p % 3 == 0) 2.6f else 1.5f
            val pColor = if (p % 2 == 0) primary else Color.White
            drawCircle(
                color = pColor.copy(alpha = 0.16f),
                radius = radius,
                center = Offset(px, py)
            )
        }
    }
}

object NexoDynamicIconManager {
    private val aliasMap = mapOf(
        "EVA-00" to "com.example.LauncherEva00",
        "EVA-01" to "com.example.LauncherEva01",
        "EVA-02" to "com.example.LauncherEva02",
        "EVA-03" to "com.example.LauncherEva03",
        "EVA-04" to "com.example.LauncherEva04",
        "EVA-05" to "com.example.LauncherEva05",
        "EVA-08" to "com.example.LauncherEva08",
        "EVA-13" to "com.example.LauncherEva13"
    )

    fun applyThemeIcon(context: Context, themeId: String) {
        val targetClass = aliasMap[themeId] ?: aliasMap["EVA-01"]!!
        runCatching {
            val pm = context.packageManager
            val pkg = context.packageName
            val targetComp = ComponentName(pkg, targetClass)
            if (pm.getComponentEnabledSetting(targetComp) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return
            }
            // Activar primero el nuevo alias y luego desactivar los demás sin cerrar la app
            pm.setComponentEnabledSetting(
                targetComp,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            aliasMap.values.forEach { cls ->
                if (cls != targetClass) {
                    pm.setComponentEnabledSetting(
                        ComponentName(pkg, cls),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        }
    }
}
