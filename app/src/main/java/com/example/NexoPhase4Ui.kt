package com.example

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlin.math.sin

object NexoPlayerPrefs {
    private const val P = "nexo_player_prefs_v3"
    var continuousFolderPlayback by mutableStateOf(true)
    private var loaded = false
    fun init(c: Context) {
        if (loaded) return; loaded = true
        continuousFolderPlayback = c.getSharedPreferences(P, 0).getBoolean("cont_play", true)
    }
    fun setContinuous(c: Context, v: Boolean) {
        continuousFolderPlayback = v
        c.getSharedPreferences(P, 0).edit().putBoolean("cont_play", v).apply()
    }
}

object NexoOtaPromptPrefs {
    private const val P = "nexo_ota_prompt_v3"
    fun isSnoozed(c: Context): Boolean =
        System.currentTimeMillis() < c.getSharedPreferences(P, 0).getLong("snooze_until", 0L)
    fun snoozeDays(c: Context, days: Int) {
        val until = System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
        c.getSharedPreferences(P, 0).edit().putLong("snooze_until", until).apply()
    }
}

@Composable
fun NexoCrystalGlassBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color(0x99121622),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
fun NexoVideoGearSettingsDialog(
    context: Context,
    isEs: Boolean,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) { NexoPlayerPrefs.init(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEs) "Ajustes de reproducción" else "Playback settings", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(if (isEs) "Reproducción continua" else "Continuous playback", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(if (isEs) "Reproducir siguiente video de la carpeta automáticamente" else "Auto-play next video in folder", fontSize = 11.sp)
                }
                Switch(
                    checked = NexoPlayerPrefs.continuousFolderPlayback,
                    onCheckedChange = { NexoPlayerPrefs.setContinuous(context, it) }
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (isEs) "Listo" else "Done") } }
    )
}

@Composable
fun NexoOtaIosUpdateDialog(
    versionTag: String,
    summaryNotes: String,
    isEs: Boolean,
    onInstallNow: () -> Unit,
    onSnooze3Days: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEs) "Actualización disponible ($versionTag)" else "Update Available ($versionTag)", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(summaryNotes.ifBlank { if (isEs) "Mejoras de diseño compacto, Bóveda y Google Drive." else "Compact UI, Vault & Google Drive improvements." }, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(onClick = onInstallNow) { Text(if (isEs) "Actualizar" else "Update") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSnooze3Days) { Text(if (isEs) "En 3 días" else "In 3 days", fontSize = 11.sp) }
                TextButton(onClick = onDismiss) { Text(if (isEs) "Rechazar" else "Decline", fontSize = 11.sp) }
            }
        }
    )
}

@Composable
fun NexoPs3XmbDownloadOverlay(
    progress: Float,
    statusText: String,
    isEs: Boolean,
) {
    val inf = rememberInfiniteTransition(label = "xmb")
    val phase by inf.animateFloat(
        initialValue = 0f, targetValue = 6.283f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "wave"
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A0F24), Color(0xFF1A1038), Color(0xFF090B16))))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            listOf(Color(0x554FC3F7) to 0.50f, Color(0x44BA68C8) to 0.56f, Color(0x33FF4081) to 0.62f).forEachIndexed { idx, (col, base) ->
                val p = Path()
                p.moveTo(0f, h * base)
                var x = 0f
                while (x <= w) {
                    val y = h * base + sin((x / w) * 5f + phase + idx) * 28f
                    p.lineTo(x, y)
                    x += 24f
                }
                drawPath(p, col, style = Stroke(width = 4.dp.toPx()))
            }
        }
        Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Mascota Mecha Chibi original cargando energía
            Canvas(Modifier.size(110.dp)) {
                val cx = size.width / 2f; val cy = size.height / 2f
                drawCircle(Color(0x337C4DFF), radius = size.minDimension * (0.44f + 0.04f * sin(phase)), center = Offset(cx, cy))
                drawRoundRect(Color(0xFF651FFF), Offset(cx - 28.dp.toPx(), cy - 26.dp.toPx()), Size(56.dp.toPx(), 48.dp.toPx()), CornerRadius(14.dp.toPx()))
                drawRoundRect(Color(0xFF00E676), Offset(cx - 18.dp.toPx(), cy - 10.dp.toPx()), Size(12.dp.toPx(), 7.dp.toPx()), CornerRadius(3.dp.toPx()))
                drawRoundRect(Color(0xFF00E676), Offset(cx + 6.dp.toPx(), cy - 10.dp.toPx()), Size(12.dp.toPx(), 7.dp.toPx()), CornerRadius(3.dp.toPx()))
                drawLine(Color(0xFFFFAB40), Offset(cx, cy - 26.dp.toPx()), Offset(cx, cy - 42.dp.toPx()), 4.dp.toPx(), StrokeCap.Round)
            }
            Spacer(Modifier.height(14.dp))
            Text(if (isEs) "CARGANDO ACTUALIZACIÓN..." else "CHARGING UPDATE...", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), color = Color(0xFF00E676), trackColor = Color.White.copy(alpha = 0.2f))
            Spacer(Modifier.height(8.dp))
            Text("${(progress * 100).toInt().coerceIn(0, 100)}%  ·  $statusText", color = Color(0xFFB3E5FC), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}
