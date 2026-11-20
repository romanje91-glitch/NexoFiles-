package com.example
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import com.example.ui.components.NexoIcons
import kotlin.math.*

object NexoFeatureFlags {
    private const val P = "nexo_feature_flags_v3"
    var vaultEnabled by mutableStateOf(true)
    var driveEnabled by mutableStateOf(true)
    private var loaded = false
    fun init(c: Context) { if (loaded) return; loaded = true; val s = c.getSharedPreferences(P, 0); vaultEnabled = s.getBoolean("v", true); driveEnabled = s.getBoolean("d", true) }
    fun setVault(c: Context, v: Boolean) { vaultEnabled = v; c.getSharedPreferences(P, 0).edit().putBoolean("v", v).apply() }
    fun setDrive(c: Context, v: Boolean) { driveEnabled = v; c.getSharedPreferences(P, 0).edit().putBoolean("d", v).apply() }
}

object NexoVaultDeepLink { var pendingOpenVault by mutableStateOf(false) }

@Composable
fun NexoCloudStatusIcon(isBackedUpInCloud: Boolean, tint: Color, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val st = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (isBackedUpInCloud) {
            val p = Path().apply {
                moveTo(w*0.25f, h*0.72f); lineTo(w*0.76f, h*0.72f)
                cubicTo(w*0.92f, h*0.72f, w*0.94f, h*0.48f, w*0.78f, h*0.45f)
                cubicTo(w*0.76f, h*0.22f, w*0.44f, h*0.16f, w*0.36f, h*0.36f)
                cubicTo(w*0.16f, h*0.36f, w*0.10f, h*0.72f, w*0.25f, h*0.72f); close()
            }
            drawPath(p, tint, style = st)
            drawLine(tint, Offset(w*0.52f, h*0.42f), Offset(w*0.52f, h*0.62f), 1.6.dp.toPx(), StrokeCap.Round)
            drawLine(tint, Offset(w*0.43f, h*0.54f), Offset(w*0.52f, h*0.63f), 1.6.dp.toPx(), StrokeCap.Round)
            drawLine(tint, Offset(w*0.61f, h*0.54f), Offset(w*0.52f, h*0.63f), 1.6.dp.toPx(), StrokeCap.Round)
        } else {
            drawRoundRect(tint, Offset(w*0.25f, h*0.10f), Size(w*0.50f, h*0.80f), CornerRadius(3.dp.toPx()), style = st)
            drawCircle(tint, 1.4.dp.toPx(), Offset(w*0.50f, h*0.78f))
        }
    }
}

@Composable
fun NexoVaultCloudActionDialog(
    fileName: String, isBackedUpInCloud: Boolean, isEs: Boolean,
    onUploadToCloud: () -> Unit, onRestoreToDevice: () -> Unit,
    onDeleteLocalOnly: () -> Unit, onDeleteCloudOnly: () -> Unit, onDeleteBoth: () -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NexoCloudStatusIcon(isBackedUpInCloud, MaterialTheme.colorScheme.primary)
            Text(if (isEs) "Estado y acciones" else "Status & Actions", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }},
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(fileName, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2)
            Text(if (isBackedUpInCloud) (if (isEs) "En nube y teléfono" else "Cloud & Device") else (if (isEs) "Solo en teléfono" else "Device only"), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            if (!isBackedUpInCloud) Button(onClick = { onUploadToCloud(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(if (isEs) "Subir a la nube" else "Upload to cloud", fontSize = 12.sp) }
            OutlinedButton(onClick = { onRestoreToDevice(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(if (isEs) "Restaurar al teléfono" else "Restore to device", fontSize = 12.sp) }
            OutlinedButton(onClick = { onDeleteLocalOnly(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(if (isEs) "Eliminar solo del teléfono" else "Delete local only", fontSize = 12.sp) }
            OutlinedButton(onClick = { onDeleteCloudOnly(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(if (isEs) "Eliminar solo de la nube" else "Delete cloud only", fontSize = 12.sp) }
            Button(onClick = { onDeleteBoth(); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text(if (isEs) "Eliminar de ambos" else "Delete both", fontSize = 12.sp) }
        }},
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (isEs) "Cerrar" else "Close") } }
    )
}

@Composable
fun NexoAtFieldVaultGate(isEs: Boolean, isDriveAuthorized: Boolean, onActivateVault: () -> Unit, onConnectDrive: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(170.dp).clickable { onActivateVault(); if (!isDriveAuthorized) onConnectDrive() }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width/2f; val cy = size.height/2f
                listOf(0.46f to Color(0xFFFF6D00), 0.34f to Color(0xFFFF9100), 0.22f to Color(0xFFFFAB40)).forEachIndexed { idx, (f, col) ->
                    val r = size.minDimension * f; val p = Path()
                    for (k in 0..7) { val a = Math.toRadians(k*45.0 - 22.5); val x = cx + (r*cos(a)).toFloat(); val y = cy + (r*sin(a)).toFloat(); if (k==0) p.moveTo(x,y) else p.lineTo(x,y) }
                    p.close(); drawPath(p, col, style = Stroke((3.2f - idx*0.6f).dp.toPx()))
                }
            }
            Text(if (isEs) "ACTIVAR" else "ACTIVATE", color = Color(0xFFFFAB40), fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(if (isEs) "BÓVEDA DESACTIVADA" else "VAULT DISABLED", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(if (isEs) "Toca el escudo para activar la Bóveda Privada." else "Tap the field to enable Private Vault.", fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}
