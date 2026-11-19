package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NexoCrashReportItem(
    val fileName: String,
    val timestampLabel: String,
    val summary: String,
    val fullText: String,
    val isRead: Boolean
)

object NexoCrashLibrary {
    private const val PREFS = "nexo_crash_library_prefs"
    private const val KEY_READ_SET = "read_report_files"
    private var installed = false

    private fun reportsDir(context: Context): File {
        val base = context.getExternalFilesDir("crash_reports") ?: File(context.filesDir, "crash_reports")
        if (!base.exists()) base.mkdirs()
        return base
    }

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appCtx = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { saveCrash(appCtx, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun saveCrash(context: Context, thread: Thread, throwable: Throwable) {
        val now = Date()
        val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(now)
        val humanStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val causeLine = "${throwable.javaClass.simpleName}: ${throwable.message ?: "Sin detalle"}"
        val content = buildString {
            appendLine("============================================================")
            appendLine("NEXOFILES // INDIVIDUAL CRASH REPORT")
            appendLine("Fecha/Hora : $humanStamp")
            appendLine("Versión    : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Hilo       : ${thread.name}")
            appendLine("Causa      : $causeLine")
            appendLine("------------------------------------------------------------")
            append(sw.toString())
        }
        File(reportsDir(context), "crash_$fileStamp.txt").writeText(content, Charsets.UTF_8)
    }

    fun listReports(context: Context): List<NexoCrashReportItem> {
        val readSet = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_READ_SET, emptySet()) ?: emptySet()
        val files = reportsDir(context).listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        return files.map { f ->
            val txt = runCatching { f.readText(Charsets.UTF_8) }.getOrDefault("")
            val lines = txt.lines()
            val ts = lines.firstOrNull { it.startsWith("Fecha/Hora") }?.substringAfter(":")?.trim()
                ?: SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(f.lastModified()))
            val cause = lines.firstOrNull { it.startsWith("Causa") }?.substringAfter(":")?.trim()
                ?: f.name
            NexoCrashReportItem(f.name, ts, cause, txt, f.name in readSet)
        }
    }

    fun setRead(context: Context, fileName: String, read: Boolean) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cur = (sp.getStringSet(KEY_READ_SET, emptySet()) ?: emptySet()).toMutableSet()
        if (read) cur.add(fileName) else cur.remove(fileName)
        sp.edit().putStringSet(KEY_READ_SET, cur).apply()
    }

    fun deleteReport(context: Context, fileName: String) {
        File(reportsDir(context), fileName).delete()
        setRead(context, fileName, false)
    }

    fun buildCombinedReport(context: Context): String {
        val all = listReports(context)
        if (all.isEmpty()) return "NexoFiles ${BuildConfig.VERSION_NAME}: Sin reportes de error registrados."
        return all.joinToString("\n\n") { it.fullText }
    }
}

@Composable
fun NexoCrashLibraryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var reports by remember { mutableStateOf(NexoCrashLibrary.listReports(context)) }
    var selectedReport by remember { mutableStateOf<NexoCrashReportItem?>(null) }
    val accent = MaterialTheme.colorScheme.primary

    fun reload() { reports = NexoCrashLibrary.listReports(context) }

    fun shareText(title: String, body: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0D14))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    val unread = reports.count { !it.isRead }
                    Text("📋 LIBRERÍA DE REPORTES", color = accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("${reports.size} archivo(s) .txt · $unread sin leer", color = Color.Gray, fontSize = 11.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFFFF6666))
                }
            }

            if (selectedReport != null) {
                val rep = selectedReport!!
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { selectedReport = null }, modifier = Modifier.weight(1f)) { Text("← Lista", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText(rep.fileName, rep.fullText))
                            Toast.makeText(context, "Reporte copiado", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Copiar", fontSize = 11.sp) }
                    Button(
                        onClick = { shareText("NexoFiles Crash ${rep.timestampLabel}", rep.fullText) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Enviar", fontSize = 11.sp) }
                }
                Text("${rep.fileName} · ${rep.timestampLabel}", color = accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF131826),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    Text(
                        text = rep.fullText,
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState())
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { shareText("NexoFiles Reporte General", NexoCrashLibrary.buildCombinedReport(context)) },
                        enabled = reports.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("📤 Reporte general", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = {
                            reports.filter { it.isRead }.forEach { NexoCrashLibrary.deleteReport(context, it.fileName) }
                            reload()
                        },
                        enabled = reports.any { it.isRead },
                        modifier = Modifier.weight(1f)
                    ) { Text("🗑 Borrar leídos", fontSize = 11.sp) }
                }

                if (reports.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No hay reportes de error registrados.\nCada fallo futuro se guardará en su propio archivo .txt.", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(reports, key = { it.fileName }) { rep ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF131826),
                                border = BorderStroke(1.dp, if (rep.isRead) Color(0xFF2B344A) else accent),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    NexoCrashLibrary.setRead(context, rep.fileName, true)
                                    reload()
                                    selectedReport = rep.copy(isRead = true)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (rep.isRead) "✓ LEÍDO" else "● NUEVO",
                                                color = if (rep.isRead) Color.Gray else Color(0xFFFF5555),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(rep.timestampLabel, color = accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        Text(rep.summary, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(
                                        onClick = { NexoCrashLibrary.deleteReport(context, rep.fileName); reload() },
                                        modifier = Modifier.size(30.dp)
                                    ) { Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = Color(0xFFFF6666)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
