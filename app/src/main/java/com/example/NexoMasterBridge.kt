package com.example

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.util.LruCache
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.data.model.VaultFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object NexoNavBridge {
    var activePagerTabId by mutableStateOf("HOME")
    var isInMainPager by mutableStateOf(true)
    var isMediaViewerOpen by mutableStateOf(false)
    var mediaImmersiveMode by mutableStateOf(false)
    var onCloseMediaViewer: (() -> Unit)? = null

    var currentExplorerPath by mutableStateOf("/storage/emulated/0")
    var canGoBackInExplorer by mutableStateOf(false)
    var onExplorerBack: (() -> Unit)? = null

    var showLandscapeHeaders by mutableStateOf(false)
    var showWebBrowser by mutableStateOf(false)
    var webBrowserInitialUrl by mutableStateOf("https://www.google.com")

    var showTorrentManager by mutableStateOf(false)
    var pendingMagnetUri by mutableStateOf("")

    var linkedGoogleEmail by mutableStateOf("")
    var onVaultFolderMoved: ((List<VaultFileItem>, String) -> Unit)? = null

    fun openTorrentWithMagnet(magnet: String = "") {
        pendingMagnetUri = magnet
        showTorrentManager = true
    }

    fun triggerBack(): Boolean {
        if (showTorrentManager) {
            showTorrentManager = false
            return true
        }
        if (showWebBrowser) {
            showWebBrowser = false
            return true
        }
        if (isMediaViewerOpen) {
            onCloseMediaViewer?.invoke()
            isMediaViewerOpen = false
            mediaImmersiveMode = false
            return true
        }
        if (canGoBackInExplorer && onExplorerBack != null) {
            onExplorerBack?.invoke()
            return true
        }
        return false
    }
}

object NexoVaultDriveMirror {
    fun ensureMirroredBackups(context: Context, passphrase: String = "NEXO_HERMETIC_AUTO_KEY_2026"): List<File> {
        val extDir = File(context.getExternalFilesDir(null), "NexoVault_Encrypted")
        if (!extDir.exists()) extDir.mkdirs()
        return extDir.listFiles()?.filter { it.isFile && it.name.endsWith(".nexo_vault") } ?: emptyList()
    }
}

data class NexoCloudPlaceholderItem(
    val id: String,
    val originalName: String,
    val folderName: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

object NexoVaultExtraStore {
    private const val PREFS = "nexo_vault_extra_store_v140"
    private const val KEY_FOLDERS = "vault_folders_json"
    private const val KEY_MAP = "vault_item_folder_map_json"
    private const val KEY_CLOUD_ONLY = "vault_cloud_only_json"

    val folders = mutableStateListOf<String>()
    val itemFolderMap = mutableStateMapOf<String, String>()
    val driveBackupFolderMap = mutableStateMapOf<String, String>()
    val cloudOnlyList = mutableStateListOf<NexoCloudPlaceholderItem>()
    var activeFolder by mutableStateOf<String?>(null)
    private var initialized = false

    private val thumbCache = LruCache<String, ImageBitmap>(48)

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        runCatching {
            val arr = JSONArray(sp.getString(KEY_FOLDERS, "[]") ?: "[]")
            folders.clear()
            for (i in 0 until arr.length()) {
                val n = arr.optString(i)
                if (n.isNotBlank()) folders.add(n)
            }
        }
        runCatching {
            val obj = JSONObject(sp.getString(KEY_MAP, "{}") ?: "{}")
            itemFolderMap.clear()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                itemFolderMap[k] = obj.optString(k, "")
            }
        }
        runCatching {
            val arr = JSONArray(sp.getString(KEY_CLOUD_ONLY, "[]") ?: "[]")
            cloudOnlyList.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                cloudOnlyList.add(
                    NexoCloudPlaceholderItem(
                        id = o.optString("id"),
                        originalName = o.optString("name"),
                        folderName = o.optString("folder", ""),
                        addedAt = o.optLong("addedAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private fun save(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fArr = JSONArray()
        folders.forEach { fArr.put(it) }
        val mObj = JSONObject()
        itemFolderMap.forEach { (k, v) -> mObj.put(k, v) }
        val cArr = JSONArray()
        cloudOnlyList.forEach { item ->
            cArr.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.originalName)
                put("folder", item.folderName)
                put("addedAt", item.addedAt)
            })
        }
        sp.edit()
            .putString(KEY_FOLDERS, fArr.toString())
            .putString(KEY_MAP, mObj.toString())
            .putString(KEY_CLOUD_ONLY, cArr.toString())
            .apply()
    }

    fun createFolder(context: Context, name: String) {
        val clean = name.trim()
        if (clean.isBlank() || folders.contains(clean)) return
        folders.add(clean)
        save(context)
    }

    fun assignToFolder(context: Context, itemId: String, folderName: String) {
        if (folderName.isBlank()) itemFolderMap.remove(itemId) else {
            if (!folders.contains(folderName)) folders.add(folderName)
            itemFolderMap[itemId] = folderName
        }
        save(context)
    }

    fun addCloudOnlyPlaceholder(context: Context, item: VaultFileItem) {
        val folder = itemFolderMap[item.id] ?: activeFolder.orEmpty()
        cloudOnlyList.removeAll { it.originalName == item.originalName }
        cloudOnlyList.add(0, NexoCloudPlaceholderItem(item.id, item.originalName, folder))
        save(context)
    }

    fun removeCloudOnlyPlaceholder(context: Context, originalName: String) {
        cloudOnlyList.removeAll { it.originalName == originalName }
        save(context)
    }

    fun clearThumbnails() { thumbCache.evictAll() }

    fun isImageName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp")
    }

    suspend fun loadThumbnail(context: Context, item: VaultFileItem): ImageBitmap? {
        if (!isImageName(item.originalName)) return null
        thumbCache.get(item.id)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val repo = (context.applicationContext as? NexoFilesApp)?.container?.vault ?: return@runCatching null
                val tmp = repo.decryptToCacheAndOpen(item).getOrNull() ?: return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(tmp.absolutePath, bounds)
                var sample = 1
                val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
                while (maxDim / sample > 240) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = BitmapFactory.decodeFile(tmp.absolutePath, opts)
                bmp?.asImageBitmap()?.also { thumbCache.put(item.id, it) }
            }.getOrNull()
        }
    }
}

// ------------------------------------------------------------------
// PORTAPAPELES GLOBAL (LOCAL <-> BÓVEDA)
// ------------------------------------------------------------------
enum class NexoClipMode { COPY, MOVE }
enum class NexoClipOrigin { LOCAL, VAULT }

data class NexoClipEntry(
    val origin: NexoClipOrigin,
    val pathOrId: String,
    val name: String,
    val isDirectory: Boolean = false,
    val vaultItem: VaultFileItem? = null
)

object NexoGlobalClipboard {
    val entries = mutableStateListOf<NexoClipEntry>()
    var mode by mutableStateOf(NexoClipMode.COPY)
    var isBusy by mutableStateOf(false)

    fun hasItems(): Boolean = entries.isNotEmpty()

    fun stageLocal(paths: List<String>, clipMode: NexoClipMode) {
        val valid = paths.distinct().map { File(it) }.filter { it.exists() }
        if (valid.isEmpty()) return
        entries.clear()
        mode = clipMode
        valid.forEach { f -> entries.add(NexoClipEntry(NexoClipOrigin.LOCAL, f.absolutePath, f.name, f.isDirectory)) }
    }

    fun stageVault(items: List<VaultFileItem>, clipMode: NexoClipMode) {
        if (items.isEmpty()) return
        entries.clear()
        mode = clipMode
        items.forEach { item -> entries.add(NexoClipEntry(NexoClipOrigin.VAULT, item.id, item.originalName, false, item)) }
    }

    fun clear() { entries.clear() }
}

object NexoGlobalFileOps {
    fun uniqueDest(dir: File, name: String): File {
        var cand = File(dir, name)
        if (!cand.exists()) return cand
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var idx = 1
        while (cand.exists()) {
            cand = File(dir, "${base}_$idx$ext")
            idx++
        }
        return cand
    }

    fun copyOrMoveLocal(src: File, destDir: File, isMove: Boolean): Boolean {
        if (!src.exists() || !destDir.exists()) return false
        if (src.absolutePath == destDir.absolutePath) return false
        if (src.isDirectory && destDir.absolutePath.startsWith(src.absolutePath + File.separator)) return false
        if (isMove && src.parentFile?.absolutePath == destDir.absolutePath) return true
        val target = uniqueDest(destDir, src.name)
        if (isMove && src.renameTo(target)) return true
        val copied = runCatching {
            if (src.isDirectory) src.copyRecursively(target, overwrite = false)
            else { src.copyTo(target, overwrite = false); true }
        }.getOrDefault(false)
        if (copied && isMove) runCatching { src.deleteRecursively() }
        return copied
    }
}

@Composable
fun NexoGlobalClipboardBar(
    activeTabId: String,
    isInMainPager: Boolean,
    currentLocalPath: String,
    isVaultUnlocked: Boolean,
    onPasteLocal: () -> Unit,
    onPasteVault: () -> Unit,
    onGoFiles: () -> Unit,
    onGoVault: () -> Unit,
    onClear: () -> Unit
) {
    if (!NexoGlobalClipboard.hasItems()) return
    val count = NexoGlobalClipboard.entries.size
    val isMove = NexoGlobalClipboard.mode == NexoClipMode.MOVE
    val fromVault = NexoGlobalClipboard.entries.firstOrNull()?.origin == NexoClipOrigin.VAULT
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Surface(
        color = Color(0xFF121624),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.85f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = (if (isMove) "✂️ CORTAR" else "📋 COPIAR") + " ($count) · " + (if (fromVault) "BÓVEDA" else "LOCAL"),
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val destHint = when {
                    isInMainPager && activeTabId == "FILES" -> "Pegar en: " + currentLocalPath.substringAfterLast('/').ifBlank { "Almacenamiento" }
                    isInMainPager && activeTabId == "VAULT" && isVaultUnlocked ->
                        "Pegar en: Bóveda" + (NexoVaultExtraStore.activeFolder?.let { " / $it" } ?: " (Raíz)")
                    else -> "Ve a Archivos o Bóveda para pegar"
                }
                Text(
                    text = if (NexoGlobalClipboard.isBusy) "Procesando archivos..." else destHint,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (NexoGlobalClipboard.isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = accent)
            } else {
                when {
                    isInMainPager && activeTabId == "FILES" -> {
                        Button(onClick = onPasteLocal, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), shape = RoundedCornerShape(8.dp)) {
                            Text("PEGAR AQUÍ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    isInMainPager && activeTabId == "VAULT" && isVaultUnlocked -> {
                        Button(onClick = onPasteVault, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), shape = RoundedCornerShape(8.dp)) {
                            Text("PEGAR AQUÍ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {
                        OutlinedButton(onClick = onGoFiles, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), shape = RoundedCornerShape(8.dp)) { Text("📁", fontSize = 12.sp) }
                        OutlinedButton(onClick = onGoVault, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), shape = RoundedCornerShape(8.dp)) { Text("🔒", fontSize = 12.sp, color = secondary) }
                    }
                }
                IconButton(onClick = onClear, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = Color(0xFFFF6666))
                }
            }
        }
    }
}

@Composable
fun NexoGlobeIcon(tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = size.toPx() * 0.085f)
        val r = size.toPx() * 0.42f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(color = tint, radius = r, center = c, style = stroke)
        drawLine(color = tint, start = Offset(c.x - r, c.y), end = Offset(c.x + r, c.y), strokeWidth = stroke.width)
        drawLine(color = tint, start = Offset(c.x, c.y - r), end = Offset(c.x, c.y + r), strokeWidth = stroke.width)
        drawOval(color = tint, topLeft = Offset(c.x - r * 0.52f, c.y - r), size = Size(r * 1.04f, r * 2f), style = stroke)
    }
}

// ------------------------------------------------------------------
// MONITOR DE DESCARGAS + NAVEGADOR WEB ESTILO CHROME
// ------------------------------------------------------------------
data class NexoWebDownloadEntry(
    val downloadId: Long,
    val title: String,
    val url: String,
    val statusText: String = "En cola...",
    val progress: Float = 0f,
    val bytesSoFar: Long = 0L,
    val totalBytes: Long = 0L,
    val localFile: File? = null
)

object NexoWebDownloadMonitor {
    val entries = mutableStateListOf<NexoWebDownloadEntry>()

    fun enqueue(context: Context, url: String, userAgent: String? = null, contentDisp: String? = null, mime: String? = null) {
        if (url.startsWith("data:image/", ignoreCase = true)) {
            runCatching {
                val base64Part = url.substringAfter("base64,", "")
                val ext = if (url.contains("png")) "png" else if (url.contains("webp")) "webp" else "jpg"
                val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                val outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val f = File(outDir, "img_${System.currentTimeMillis()}.$ext")
                f.writeBytes(bytes)
                entries.add(0, NexoWebDownloadEntry(System.currentTimeMillis(), f.name, "inline-image", "Completado", 1f, f.length(), f.length(), f))
                Toast.makeText(context, "Imagen guardada: ${f.name}", Toast.LENGTH_LONG).show()
            }.onFailure { Toast.makeText(context, "Error guardando imagen: ${it.message}", Toast.LENGTH_SHORT).show() }
            return
        }
        runCatching {
            val fileName = URLUtil.guessFileName(url, contentDisp, mime).ifBlank { "archivo_${System.currentTimeMillis()}" }
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription("Descargando desde NexoFiles Web")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            if (!userAgent.isNullOrBlank()) req.addRequestHeader("User-Agent", userAgent)
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(req)
            val destFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            entries.add(0, NexoWebDownloadEntry(id, fileName, url, "Descargando...", 0f, 0L, 0L, destFile))
            Toast.makeText(context, "⬇ Descargando: $fileName", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Error iniciando descarga: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun refresh(context: Context) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        for (i in entries.indices) {
            val e = entries[i]
            if (e.url == "inline-image") continue
            runCatching {
                val c = dm.query(DownloadManager.Query().setFilterById(e.downloadId))
                c?.use { cur ->
                    if (cur.moveToFirst()) {
                        val st = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val soFar = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val prog = if (total > 0L) (soFar.toFloat() / total.toFloat()).coerceIn(0f, 1f) else if (st == DownloadManager.STATUS_SUCCESSFUL) 1f else 0f
                        val label = when (st) {
                            DownloadManager.STATUS_RUNNING -> "Descargando (${(prog * 100).toInt()}%)"
                            DownloadManager.STATUS_PENDING -> "Pendiente..."
                            DownloadManager.STATUS_PAUSED -> "Pausado"
                            DownloadManager.STATUS_SUCCESSFUL -> "Completado ✓"
                            DownloadManager.STATUS_FAILED -> "Falló"
                            else -> e.statusText
                        }
                        entries[i] = e.copy(statusText = label, progress = prog, bytesSoFar = soFar, totalBytes = total)
                    }
                }
            }
        }
    }
}

@Composable
fun NexoSimpleWebBrowserDialog(
    initialUrl: String = NexoNavBridge.webBrowserInitialUrl,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf(initialUrl) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var immersiveWeb by remember { mutableStateOf(false) }
    var showDownloadsPanel by remember { mutableStateOf(false) }
    var longPressTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // (typeLabel, extraUrl)
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val accent = MaterialTheme.colorScheme.primary

    LaunchedEffect(showDownloadsPanel) {
        while (showDownloadsPanel) {
            NexoWebDownloadMonitor.refresh(context)
            delay(800L)
        }
    }

    fun loadTarget(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.startsWith("magnet:?", ignoreCase = true)) {
            onDismiss()
            NexoNavBridge.openTorrentWithMagnet(trimmed)
            return
        }
        val finalUrl = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://www.google.com/search?q=${Uri.encode(trimmed)}"
        }
        urlInput = finalUrl
        currentUrl = finalUrl
        webViewRef?.loadUrl(finalUrl)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BackHandler(enabled = true) {
            when {
                showDownloadsPanel -> showDownloadsPanel = false
                webViewRef?.canGoBack() == true -> webViewRef?.goBack()
                else -> onDismiss()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090B10))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = !immersiveWeb) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111520))
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() else onDismiss() },
                                modifier = Modifier.size(32.dp)
                            ) { Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = accent) }
                            IconButton(
                                onClick = { webViewRef?.reload() },
                                modifier = Modifier.size(32.dp)
                            ) { Icon(Icons.Default.Refresh, contentDescription = "Recargar", tint = accent) }
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                                placeholder = { Text("URL o buscar...", fontSize = 11.sp, color = Color.Gray) }
                            )
                            IconButton(onClick = { loadTarget(urlInput) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Search, contentDescription = "Ir", tint = accent)
                            }
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (showDownloadsPanel) accent.copy(alpha = 0.25f) else Color(0xFF1A2234))
                                    .border(1.dp, accent, RoundedCornerShape(6.dp))
                                    .clickable { showDownloadsPanel = !showDownloadsPanel }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text("⬇${if (NexoWebDownloadMonitor.entries.isNotEmpty()) NexoWebDownloadMonitor.entries.size else ""}", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1A2234))
                                    .border(1.dp, accent, RoundedCornerShape(6.dp))
                                    .clickable { immersiveWeb = true }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) { Text("⛶", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFFFF5555))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "Google" to "https://www.google.com",
                                "DuckDuckGo" to "https://duckduckgo.com",
                                "Manga" to "https://mangadex.org",
                                "⬇ Descargar URL" to "__DL_CURRENT__"
                            ).forEach { (lbl, link) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1A2030))
                                        .border(0.5.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                        .clickable {
                                            if (link == "__DL_CURRENT__") {
                                                NexoWebDownloadMonitor.enqueue(context, urlInput)
                                                showDownloadsPanel = true
                                            } else loadTarget(link)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(lbl, color = accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }

                if (showDownloadsPanel) {
                    Surface(
                        color = Color(0xFF111622),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("⬇ MONITOR DE DESCARGAS", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                TextButton(onClick = { showDownloadsPanel = false }, contentPadding = PaddingValues(4.dp)) { Text("Ocultar", fontSize = 11.sp) }
                            }
                            if (NexoWebDownloadMonitor.entries.isEmpty()) {
                                Text("Sin descargas recientes. Mantén presionada una imagen o toca cualquier enlace descargable.", color = Color.Gray, fontSize = 11.sp)
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(NexoWebDownloadMonitor.entries, key = { it.downloadId }) { entry ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF182032),
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                entry.localFile?.takeIf { it.exists() }?.let { f ->
                                                    runCatching {
                                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                                        val i = Intent(Intent.ACTION_VIEW).apply {
                                                            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(i)
                                                    }
                                                }
                                            }
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(entry.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                LinearProgressIndicator(progress = { entry.progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = accent)
                                                Text(entry.statusText, color = accent, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    if (!url.isNullOrBlank() && url.startsWith("magnet:?", ignoreCase = true)) {
                                        onDismiss()
                                        NexoNavBridge.openTorrentWithMagnet(url)
                                        return true
                                    }
                                    return false
                                }
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    if (!url.isNullOrBlank()) urlInput = url
                                }
                            }
                            webChromeClient = WebChromeClient()
                            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                                if (url.startsWith("magnet:?", ignoreCase = true)) {
                                    onDismiss()
                                    NexoNavBridge.openTorrentWithMagnet(url)
                                } else {
                                    NexoWebDownloadMonitor.enqueue(ctx, url, userAgent, contentDisposition, mimetype)
                                    showDownloadsPanel = true
                                }
                            }
                            setOnLongClickListener { v ->
                                val wv = v as? WebView ?: return@setOnLongClickListener false
                                val hit = wv.hitTestResult
                                val extra = hit.extra ?: return@setOnLongClickListener false
                                when (hit.type) {
                                    WebView.HitTestResult.IMAGE_TYPE,
                                    WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                                        longPressTarget = "IMAGEN" to extra
                                        true
                                    }
                                    WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                                        longPressTarget = "ENLACE" to extra
                                        true
                                    }
                                    else -> false
                                }
                            }
                            loadUrl(currentUrl)
                            webViewRef = this
                        }
                    }
                )
            }

            if (immersiveWeb) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .border(1.dp, accent, RoundedCornerShape(20.dp))
                        .clickable { immersiveWeb = false }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) { Text("⛶ MOSTRAR BARRA", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }

            longPressTarget?.let { (kind, targetUrl) ->
                AlertDialog(
                    onDismissRequest = { longPressTarget = null },
                    title = { Text(if (kind == "IMAGEN") "Opciones de imagen" else "Opciones de enlace") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(targetUrl, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.Gray)
                            OutlinedButton(
                                onClick = {
                                    NexoWebDownloadMonitor.enqueue(context, targetUrl)
                                    showDownloadsPanel = true
                                    longPressTarget = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (kind == "IMAGEN") "⬇ Descargar imagen" else "⬇ Descargar archivo") }
                            OutlinedButton(
                                onClick = {
                                    loadTarget(targetUrl)
                                    longPressTarget = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (kind == "IMAGEN") "🖼 Abrir imagen" else "🌐 Abrir enlace") }
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("URL", targetUrl))
                                    Toast.makeText(context, "Dirección copiada", Toast.LENGTH_SHORT).show()
                                    longPressTarget = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (kind == "IMAGEN") "📋 Copiar dirección de imagen" else "📋 Copiar enlace") }
                            OutlinedButton(
                                onClick = {
                                    val sh = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, targetUrl)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(Intent.createChooser(sh, "Compartir"))
                                    longPressTarget = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("↗ Compartir") }
                            if (targetUrl.startsWith("magnet:?", ignoreCase = true) || targetUrl.endsWith(".torrent", ignoreCase = true)) {
                                Button(
                                    onClick = {
                                        longPressTarget = null
                                        onDismiss()
                                        NexoNavBridge.openTorrentWithMagnet(targetUrl)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("🧲 Descargar con NexoTorrent") }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { longPressTarget = null }) { Text("Cerrar") }
                    }
                )
            }
        }
    }
}
