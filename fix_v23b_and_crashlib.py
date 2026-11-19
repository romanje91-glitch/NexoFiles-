import os, re

ROOT = "app/src/main/java/com/example"

def read_f(rel):
    with open(os.path.join(ROOT, rel), "r", encoding="utf-8") as f:
        return f.read()

def write_f(rel, txt):
    with open(os.path.join(ROOT, rel), "w", encoding="utf-8") as f:
        f.write(txt)

def ensure_imports(code, imports):
    m = re.search(r"^package\s+[\w\.]+\s*\n", code, re.M)
    if not m:
        return code
    add = "".join(f"import {imp}\n" for imp in imports if f"import {imp}" not in code)
    return code if not add else code[:m.end()] + add + code[m.end():]

def find_balanced(text, open_idx, open_ch="{", close_ch="}"):
    if open_idx < 0 or open_idx >= len(text) or text[open_idx] != open_ch:
        return -1
    depth, in_str, esc = 0, False, False
    for i in range(open_idx, len(text)):
        ch = text[i]
        if esc: esc = False; continue
        if ch == "\\" and in_str: esc = True; continue
        if ch == '"': in_str = not in_str; continue
        if not in_str:
            if ch == open_ch: depth += 1
            elif ch == close_ch:
                depth -= 1
                if depth == 0: return i + 1
    return -1

# ------------------------------------------------------------------
# A. FIX Toast en hilo IO en NexoSecurityAndTorrent.kt
# ------------------------------------------------------------------
st_rel = "NexoSecurityAndTorrent.kt"
st_code = read_f(st_rel)
old_t = "Toast.makeText(context, msg, Toast.LENGTH_LONG).show()"
new_t = "android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }"
if old_t in st_code:
    st_code = st_code.replace(old_t, new_t)
    write_f(st_rel, st_code)
    print("[OK] 2. NexoSecurityAndTorrent.kt: Toast movido al hilo principal.")

# ------------------------------------------------------------------
# B. FIX VideoPlayerScreen.kt (limpiar inyección anterior y re-inyectar en alcance correcto)
# ------------------------------------------------------------------
vp_rel = "ui/screens/VideoPlayerScreen.kt"
vp_code = read_f(vp_rel)

# Limpiar bloque mal ubicado tras errorMsg
vp_code = re.sub(
    r'var errorMsg by remember\(filePath\) \{ mutableStateOf<String\?>\(null\) \}\n  var showVlcTrackDialog[\s\S]*?Toast\.makeText\(context, "Subtítulos cargados", Toast\.LENGTH_SHORT\)\.show\(\)\n    \}\n  \}',
    'var errorMsg by remember(filePath) { mutableStateOf<String?>(null) }',
    vp_code
)
# Limpiar bloque vlc_dialog mal ubicado al final del archivo
vp_code = re.sub(
    r'\n  LaunchedEffect\(subtitleScale, subtitleColorIdx, playerViewRef\)[\s\S]*?TextButton\(onClick = \{ showVlcTrackDialog = false \}\) \{ Text\("Cerrar"\) \}\n      \}\n    \)\n  \}\n',
    '\n',
    vp_code
)

# Re-inyectar variables de estado y subPickerLauncher DESPUÉS de declarar exoPlayer (justo antes de DisposableEffect)
disp_idx = vp_code.find("DisposableEffect(")
if disp_idx != -1 and "showVlcTrackDialog" not in vp_code:
    vlc_state_block = """  var showVlcTrackDialog by remember { mutableStateOf(false) }
  var subtitleScale by remember { mutableStateOf(1.0f) }
  var subtitleColorIdx by remember { mutableStateOf(0) }
  var loadedSubName by remember { mutableStateOf<String?>(null) }
  var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

  val subPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { subUri ->
    if (subUri != null) {
      val pos = exoPlayer.currentPosition
      val wasPlaying = exoPlayer.isPlaying
      val mime = if (subUri.toString().endsWith(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
      val subCfg = MediaItem.SubtitleConfiguration.Builder(subUri)
        .setMimeType(mime)
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build()
      val newItem = MediaItem.Builder()
        .setUri(Uri.fromFile(videoFile))
        .setSubtitleConfigurations(listOf(subCfg))
        .build()
      exoPlayer.setMediaItem(newItem, pos)
      exoPlayer.prepare()
      exoPlayer.playWhenReady = wasPlaying
      loadedSubName = subUri.lastPathSegment ?: "Subtítulo externo"
      Toast.makeText(context, "Subtítulos cargados", Toast.LENGTH_SHORT).show()
    }
  }

  """
    vp_code = vp_code[:disp_idx] + vlc_state_block + vp_code[disp_idx:]

# Insertar vlc_dialog justo antes de la llave de cierre de fun VideoPlayerScreen(...)
fn_m = re.search(r"fun\s+VideoPlayerScreen\s*\(", vp_code)
if fn_m:
    pe = find_balanced(vp_code, vp_code.find("(", fn_m.start()), "(", ")")
    bo = vp_code.find("{", pe)
    be = find_balanced(vp_code, bo, "{", "}")
    if be != -1 and "LaunchedEffect(subtitleScale" not in vp_code:
        vlc_ui_block = """
  LaunchedEffect(subtitleScale, subtitleColorIdx, playerViewRef) {
    val pv = playerViewRef ?: return@LaunchedEffect
    val fg = when (subtitleColorIdx) {
      1 -> AndroidColor.YELLOW
      2 -> AndroidColor.CYAN
      else -> AndroidColor.WHITE
    }
    pv.subtitleView?.setStyle(
      CaptionStyleCompat(fg, AndroidColor.argb(150, 0, 0, 0), AndroidColor.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, AndroidColor.BLACK, null)
    )
    pv.subtitleView?.setFractionalTextSize(0.0533f * subtitleScale)
  }

  if (showControlsOverlay) {
    Box(modifier = Modifier.fillMaxSize().padding(top = 56.dp, end = 12.dp), contentAlignment = Alignment.TopEnd) {
      OutlinedButton(onClick = { showVlcTrackDialog = true }, shape = RoundedCornerShape(8.dp)) {
        Text("CC / AUDIO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
      }
    }
  }

  if (showVlcTrackDialog) {
    AlertDialog(
      onDismissRequest = { showVlcTrackDialog = false },
      title = { Text("Subtítulos y Audio (VLC)") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { subPickerLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
          ) { Text("📂 Cargar subtítulo (.srt / .vtt)") }
          loadedSubName?.let { Text("Activo: $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary) }
          Text("Tamaño de subtítulos: ${(subtitleScale * 100).toInt()}%", fontSize = 12.sp)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Pequeño" to 0.75f, "Normal" to 1.0f, "Grande" to 1.35f).forEach { (lbl, sc) ->
              OutlinedButton(onClick = { subtitleScale = sc }, modifier = Modifier.weight(1f)) { Text(lbl, fontSize = 10.sp) }
            }
          }
          Text("Color de subtítulos:", fontSize = 12.sp)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Blanco" to 0, "Amarillo" to 1, "Cian" to 2).forEach { (lbl, idx) ->
              OutlinedButton(onClick = { subtitleColorIdx = idx }, modifier = Modifier.weight(1f)) { Text(lbl, fontSize = 10.sp) }
            }
          }
          OutlinedButton(
            onClick = {
              val curOff = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
              exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !curOff)
                .build()
              Toast.makeText(context, if (curOff) "Subtítulos activados" else "Subtítulos ocultos", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
          ) { Text("Activar / Ocultar subtítulos") }
        }
      },
      confirmButton = {
        TextButton(onClick = { showVlcTrackDialog = false }) { Text("Cerrar") }
      }
    )
  }
"""
        vp_code = vp_code[:be-1] + vlc_ui_block + vp_code[be-1:]

write_f(vp_rel, vp_code)
print("[OK] 3. VideoPlayerScreen.kt: Error de compilación corregido.")

# ------------------------------------------------------------------
# C. CREAR NexoCrashLibrary.kt (Reportes individuales .txt + leído/no leído + reporte general)
# ------------------------------------------------------------------
crash_lib_kt = r"""package com.example

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
"""
write_f("NexoCrashLibrary.kt", crash_lib_kt)
print("[OK] 4. NexoCrashLibrary.kt: Librería de reportes individuales .txt creada.")

# Conectar NexoCrashLibrary en MainActivity.kt (instalación al iniciar) y SettingsScreen.kt (diálogo)
ma_rel = "MainActivity.kt"
ma_code = read_f(ma_rel)
ma_code = ensure_imports(ma_code, ["com.example.NexoCrashLibrary"])
if "NexoCrashLibrary.install(" not in ma_code:
    ma_code = ma_code.replace(
        "super.onCreate(savedInstanceState)",
        "super.onCreate(savedInstanceState)\n    NexoCrashLibrary.install(applicationContext)",
        1
    )
    write_f(ma_rel, ma_code)

ss_rel = "ui/screens/SettingsScreen.kt"
ss_code = read_f(ss_rel)
ss_code = ensure_imports(ss_code, ["com.example.NexoCrashLibraryDialog"])
ss_code = ss_code.replace(
    "CrashDiagnosticsDialog(\n      onDismiss = { showCrashDiagnosticsDialog = false },\n    )",
    "NexoCrashLibraryDialog(\n      onDismiss = { showCrashDiagnosticsDialog = false },\n    )"
)
write_f(ss_rel, ss_code)
print("[OK] 5. NexoCrashLibrary conectada en MainActivity y SettingsScreen.")

# ------------------------------------------------------------------
# D. HOME SCREEN: Texto "version 2.3b" en minúscula junto a NERV
# ------------------------------------------------------------------
hs_rel = "ui/screens/HomeScreen.kt"
hs_code = read_f(hs_rel)
hs_code = ensure_imports(hs_code, ["com.example.BuildConfig", "androidx.compose.ui.unit.sp"])
old_nerv = """          Text(
            text = "NERV",
            style = MonoTnumStyle,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
          )"""
new_nerv = """          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "NERV",
              style = MonoTnumStyle,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
            )
            Text(
              text = "version ${BuildConfig.VERSION_NAME}",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
          }"""
if old_nerv in hs_code:
    hs_code = hs_code.replace(old_nerv, new_nerv, 1)
    write_f(hs_rel, hs_code)
    print("[OK] 6. HomeScreen.kt: 'version 2.3b' añadido junto a NERV.")
