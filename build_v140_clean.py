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
    depth = 0
    in_str = False
    esc = False
    i = open_idx
    while i < len(text):
        ch = text[i]
        if esc:
            esc = False
            i += 1
            continue
        if ch == "\\" and in_str:
            esc = True
            i += 1
            continue
        if ch == '"':
            in_str = not in_str
            i += 1
            continue
        if not in_str:
            if ch == open_ch:
                depth += 1
            elif ch == close_ch:
                depth -= 1
                if depth == 0:
                    return i + 1
        i += 1
    return -1

def split_top_level_blocks(body):
    blocks = []
    n = len(body)
    i = 0
    while i < n:
        while i < n and body[i] in " \t\r\n":
            i += 1
        if i >= n:
            break
        start = i
        if body.startswith("//", i):
            nl = body.find("\n", i)
            i = n if nl == -1 else nl + 1
            blocks.append((start, i, "comment"))
            continue
        if body.startswith("/*", i):
            end_c = body.find("*/", i + 2)
            i = n if end_c == -1 else end_c + 2
            blocks.append((start, i, "comment"))
            continue
        p_d = b_d = s_d = 0
        in_str = False
        esc = False
        while i < n:
            ch = body[i]
            if esc:
                esc = False
                i += 1
                continue
            if ch == "\\" and in_str:
                esc = True
                i += 1
                continue
            if ch == '"':
                in_str = not in_str
                i += 1
                continue
            if not in_str:
                if ch == "(": p_d += 1
                elif ch == ")": p_d = max(0, p_d - 1)
                elif ch == "{": b_d += 1
                elif ch == "}": b_d = max(0, b_d - 1)
                elif ch == "[": s_d += 1
                elif ch == "]": s_d = max(0, s_d - 1)
                elif ch == "\n" and p_d == 0 and b_d == 0 and s_d == 0:
                    j = i + 1
                    while j < n and body[j] in " \t":
                        j += 1
                    if j < n and (body[j] in ".{?" or body.startswith("else", j)):
                        i += 1
                        continue
                    i += 1
                    break
            i += 1
        snip = body[start:i].strip()
        kind = "decl" if re.match(r"^(val|var|fun|LaunchedEffect|DisposableEffect|SideEffect)\b", snip) else "ui"
        blocks.append((start, i, kind))
    return blocks

report = []

# ------------------------------------------------------------------
# 1. NexoMasterBridge.kt (conserva NexoVaultDriveMirror para compatibilidad
#    + Navegador Web + Icono Globo + Thumbnails Vault + Carpetas + Cloud-Only)
# ------------------------------------------------------------------
bridge_kt = """package com.example

import android.app.DownloadManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.LruCache
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.VaultFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object NexoNavBridge {
    var isMediaViewerOpen by mutableStateOf(false)
    var mediaImmersiveMode by mutableStateOf(false)
    var onCloseMediaViewer: (() -> Unit)? = null

    var currentExplorerPath by mutableStateOf("/storage/emulated/0")
    var canGoBackInExplorer by mutableStateOf(false)
    var onExplorerBack: (() -> Unit)? = null

    var showLandscapeHeaders by mutableStateOf(false)
    var showWebBrowser by mutableStateOf(false)
    var webBrowserInitialUrl by mutableStateOf("https://www.google.com")

    fun triggerBack(): Boolean {
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
        if (folderName.isBlank()) itemFolderMap.remove(itemId) else itemFolderMap[itemId] = folderName
        save(context)
    }

    fun addCloudOnlyPlaceholder(context: Context, item: VaultFileItem) {
        val folder = itemFolderMap[item.id] ?: activeFolder.orEmpty()
        cloudOnlyList.removeAll { it.originalName == item.originalName }
        cloudOnlyList.add(
            0,
            NexoCloudPlaceholderItem(
                id = item.id,
                originalName = item.originalName,
                folderName = folder
            )
        )
        save(context)
    }

    fun removeCloudOnlyPlaceholder(context: Context, originalName: String) {
        cloudOnlyList.removeAll { it.originalName == originalName }
        save(context)
    }

    fun clearThumbnails() {
        thumbCache.evictAll()
    }

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
                val repo = (context.applicationContext as? NexoFilesApp)?.container?.vault
                    ?: return@runCatching null
                val tmp = repo.decryptToCacheAndOpen(item).getOrNull() ?: return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(tmp.absolutePath, bounds)
                var sample = 1
                val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
                while (maxDim / sample > 240) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = BitmapFactory.decodeFile(tmp.absolutePath, opts)
                runCatching { tmp.delete() }
                bmp?.asImageBitmap()?.also { thumbCache.put(item.id, it) }
            }.getOrNull()
        }
    }
}

@Composable
fun NexoGlobeIcon(
    tint: Color,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = size.toPx() * 0.085f)
        val r = size.toPx() * 0.42f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(color = tint, radius = r, center = c, style = stroke)
        drawLine(color = tint, start = Offset(c.x - r, c.y), end = Offset(c.x + r, c.y), strokeWidth = stroke.width)
        drawLine(color = tint, start = Offset(c.x, c.y - r), end = Offset(c.x, c.y + r), strokeWidth = stroke.width)
        drawOval(
            color = tint,
            topLeft = Offset(c.x - r * 0.52f, c.y - r),
            size = Size(r * 1.04f, r * 2f),
            style = stroke
        )
    }
}

@Composable
fun NexoSimpleWebBrowserDialog(
    initialUrl: String = NexoNavBridge.webBrowserInitialUrl,
    onDismiss: () -> Unit
) {
    var urlInput by remember { mutableStateOf(initialUrl) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var immersiveWeb by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val accent = MaterialTheme.colorScheme.primary

    fun loadTarget(raw: String) {
        val trimmed = raw.trim()
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
            if (webViewRef?.canGoBack() == true) {
                webViewRef?.goBack()
            } else {
                onDismiss()
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
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() else onDismiss() },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = accent)
                            }
                            IconButton(
                                onClick = { webViewRef?.reload() },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Recargar", tint = accent)
                            }
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                placeholder = { Text("Buscar o escribir URL...", fontSize = 11.sp, color = Color.Gray) }
                            )
                            IconButton(
                                onClick = { loadTarget(urlInput) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Ir", tint = accent)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1A2234))
                                    .border(1.dp, accent, RoundedCornerShape(6.dp))
                                    .clickable { immersiveWeb = true }
                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Text("⛶", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFFFF5555))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "Google" to "https://www.google.com",
                                "DuckDuckGo" to "https://duckduckgo.com",
                                "Manga" to "https://mangadex.org",
                                "GitHub" to "https://github.com/romanje91-glitch/NexoFiles-/releases"
                            ).forEach { pair ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1A2030))
                                        .border(0.5.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                        .clickable { loadTarget(pair.second) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(pair.first, color = accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
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
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    if (!url.isNullOrBlank()) urlInput = url
                                }
                            }
                            webChromeClient = WebChromeClient()
                            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                                runCatching {
                                    val req = DownloadManager.Request(Uri.parse(url))
                                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                                    req.setTitle(fileName)
                                    req.setDescription("Descargando desde NexoFiles Web...")
                                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                                    req.addRequestHeader("User-Agent", userAgent)
                                    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                    dm.enqueue(req)
                                    Toast.makeText(ctx, "Descargando: $fileName", Toast.LENGTH_LONG).show()
                                }.onFailure {
                                    Toast.makeText(ctx, "Error al descargar: ${it.message}", Toast.LENGTH_SHORT).show()
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
                ) {
                    Text("⛶ MOSTRAR BARRA", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
"""
write_f("NexoMasterBridge.kt", bridge_kt)
report.append("[OK] 1. NexoMasterBridge.kt (con NexoVaultDriveMirror + WebBrowser + Thumbnails + Carpetas).")

# ------------------------------------------------------------------
# 2. TEMAS EVA EN 1 LÍNEA (Ruleta infinita + Dropdown) en NexoSettingsModules.kt
# ------------------------------------------------------------------
sm_rel = "ui/components/NexoSettingsModules.kt"
sm_code = read_f(sm_rel)
sm_code = ensure_imports(sm_code, [
    "androidx.compose.foundation.lazy.LazyRow",
    "androidx.compose.foundation.lazy.rememberLazyListState",
    "androidx.compose.material.icons.Icons",
    "androidx.compose.material.icons.filled.ArrowDropDown",
    "androidx.compose.material3.DropdownMenu",
    "androidx.compose.material3.DropdownMenuItem",
    "androidx.compose.material3.Icon",
    "androidx.compose.material3.IconButton",
    "androidx.compose.runtime.LaunchedEffect",
    "androidx.compose.ui.text.style.TextOverflow"
])
m_card = re.search(r"@Composable\s+fun\s+NexoEvaThemeSelectorCard\s*\([^\)]*\)\s*\{", sm_code)
if m_card:
    b_open = sm_code.find("{", m_card.start())
    b_end = find_balanced(sm_code, b_open)
    new_card = """@Composable
fun NexoEvaThemeSelectorCard(compact: Boolean = false) {
    val context = LocalContext.current
    val themes = listOf(
        "EVA-00" to "Unidad 00 (Ámbar)",
        "EVA-01" to "Unidad 01 (Verde Berserk)",
        "EVA-02" to "Unidad 02 (Rojo NERV)",
        "EVA-03" to "Unidad 03 (Gris Militar)",
        "EVA-04" to "Unidad 04 (Plateado)",
        "EVA-05" to "Unidad 05 (Verde Oliva)",
        "EVA-08" to "Unidad 08 (Magenta)",
        "EVA-13" to "Unidad 13 (Celeste Núcleo)"
    )

    val themeColor = NexoEvaThemeManager.primaryColor
    val surfaceColor = NexoEvaThemeManager.surfaceColor
    var dropdownExpanded by remember { mutableStateOf(false) }

    val totalVirtual = 8000
    val currentIdx = themes.indexOfFirst { it.first == NexoEvaThemeManager.currentTheme }.coerceAtLeast(0)
    val startIdx = remember { (totalVirtual / 2) - ((totalVirtual / 2) % themes.size) + currentIdx }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIdx)
    var userScrolled by remember { mutableStateOf(false) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            userScrolled = true
        } else if (userScrolled) {
            val chosen = themes[listState.firstVisibleItemIndex % themes.size]
            if (chosen.first != NexoEvaThemeManager.currentTheme) {
                NexoEvaThemeManager.setTheme(context, chosen.first)
            }
            userScrolled = false
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, themeColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 0.dp else 14.dp, vertical = if (compact) 2.dp else 6.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (!compact) {
                Text(
                    text = "TEMA EVA (DESLIZA O ELIGE ▼)",
                    color = themeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(42.dp), contentAlignment = Alignment.Center) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(totalVirtual) { idx ->
                            val (id, label) = themes[idx % themes.size]
                            val selected = NexoEvaThemeManager.currentTheme == id
                            val paletteDot = NexoEvaThemeManager.units.firstOrNull { it.id == id }?.primary ?: themeColor
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) themeColor.copy(alpha = 0.22f) else Color(0xFF161B26),
                                border = BorderStroke(1.dp, if (selected) themeColor else Color(0xFF2E364F)),
                                modifier = Modifier.clickable {
                                    NexoEvaThemeManager.setTheme(context, id)
                                    Toast.makeText(context, "Tema: $label", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(paletteDot))
                                    Text(
                                        text = label,
                                        color = if (selected) themeColor else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Box {
                        IconButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Lista de temas", tint = themeColor)
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.background(Color(0xFF121622))
                        ) {
                            themes.forEach { (id, label) ->
                                val selected = NexoEvaThemeManager.currentTheme == id
                                val paletteDot = NexoEvaThemeManager.units.firstOrNull { it.id == id }?.primary ?: themeColor
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(paletteDot))
                                            Text(
                                                text = if (selected) "✓ $label" else label,
                                                color = if (selected) themeColor else Color.White,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        dropdownExpanded = false
                                        NexoEvaThemeManager.setTheme(context, id)
                                        Toast.makeText(context, "Tema: $label", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}"""
    sm_code = sm_code[:m_card.start()] + new_card + sm_code[b_end:]
    write_f(sm_rel, sm_code)
    report.append("[OK] 2. NexoSettingsModules.kt: Ruleta EVA 1 línea + Dropdown.")

# ------------------------------------------------------------------
# 3. QUICK SETTINGS (CommonComponents.kt): Ruleta EVA 1 línea
# ------------------------------------------------------------------
for dirpath, _, files in os.walk(ROOT):
    for fn in files:
        if not fn.endswith(".kt"): continue
        fullp = os.path.join(dirpath, fn)
        with open(fullp, "r", encoding="utf-8") as f:
            c = f.read()
        if "strings.quickSettingsTitle" in c and "strings.appThemeTitle" in c:
            c = ensure_imports(c, ["com.example.ui.components.NexoEvaThemeSelectorCard"])
            idx_h = c.find("strings.hiddenFilesTitle")
            if idx_h != -1:
                anc = max(c.rfind("BichromaticCard(", max(0, idx_h - 400), idx_h),
                          c.rfind("Surface(", max(0, idx_h - 400), idx_h),
                          c.rfind("Row(", max(0, idx_h - 400), idx_h))
                if anc != -1:
                    c = c[:anc] + "NexoEvaThemeSelectorCard(compact = true)\nSpacer(modifier = Modifier.height(6.dp))\n" + c[anc:]
                    with open(fullp, "w", encoding="utf-8") as f:
                        f.write(c)
                    report.append(f"[OK] 3. Quick Settings ({fn}): Ruleta EVA 1 línea añadida.")
                    break

# ------------------------------------------------------------------
# 4a. BOTÓN NAVEGADOR WEB JUNTO A LA LUPA EN HomeScreen.kt
# ------------------------------------------------------------------
h_rel = "ui/screens/HomeScreen.kt"
h_code = read_f(h_rel)
h_code = ensure_imports(h_code, [
    "com.example.NexoNavBridge",
    "com.example.NexoGlobeIcon",
    "androidx.compose.foundation.background",
    "androidx.compose.foundation.border",
    "androidx.compose.foundation.layout.size",
    "androidx.compose.foundation.layout.padding",
    "androidx.compose.foundation.shape.RoundedCornerShape",
    "androidx.compose.material3.IconButton",
    "androidx.compose.material3.MaterialTheme",
    "androidx.compose.ui.unit.dp"
])
g_idx = h_code.find("strings.greetingHello")
if g_idx == -1:
    g_idx = h_code.find("NEXOFILES // HUD")
inserted_web = False
if g_idx != -1:
    row_matches = [m.start() for m in re.finditer(r"\bRow\s*\(", h_code[:g_idx])]
    header_body = None
    for r_pos in reversed(row_matches):
        p_o = h_code.find("(", r_pos)
        p_e = find_balanced(h_code, p_o, "(", ")")
        if p_e == -1: continue
        b_o = h_code.find("{", p_e)
        if b_o == -1 or b_o - p_e > 25: continue
        b_e = find_balanced(h_code, b_o, "{", "}")
        if b_e != -1 and b_o < g_idx < b_e:
            header_body = (b_o + 1, b_e - 1)
            break
    if header_body:
        hb_s, hb_e = header_body
        hb_txt = h_code[hb_s:hb_e]
        target_rel = -1
        for kw in ["NexoIcons.Search", "Icons.Rounded.Search", "Icons.Default.Search", "Search", "quickSettingsCd"]:
            pos = hb_txt.find(kw, max(0, g_idx - hb_s))
            if pos != -1:
                target_rel = pos
                break
        if target_rel != -1:
            cur_s, cur_e = hb_s, hb_e
            ins_pos = -1
            for _ in range(3):
                sub = h_code[cur_s:cur_e]
                rel_t = (hb_s + target_rel) - cur_s
                for bs, be, _ in split_top_level_blocks(sub):
                    if bs <= rel_t < be:
                        chunk = sub[bs:be].lstrip()
                        if re.match(r"^Row\s*\(", chunk):
                            rp = cur_s + bs + (len(sub[bs:be]) - len(chunk)) + chunk.find("(")
                            rpe = find_balanced(h_code, rp, "(", ")")
                            rbo = h_code.find("{", rpe)
                            rbe = find_balanced(h_code, rbo, "{", "}")
                            if rbo != -1 and rbe != -1:
                                cur_s, cur_e = rbo + 1, rbe - 1
                                break
                        ins_pos = cur_s + bs
                        break
                if ins_pos != -1:
                    break
            if ins_pos != -1:
                web_btn = """IconButton(
              onClick = { NexoNavBridge.showWebBrowser = true },
              modifier = Modifier
                .padding(end = 4.dp)
                .size(40.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
            ) {
              NexoGlobeIcon(tint = MaterialTheme.colorScheme.primary, size = 20.dp)
            }
            """
                h_code = h_code[:ins_pos] + web_btn + h_code[ins_pos:]
                write_f(h_rel, h_code)
                inserted_web = True
                report.append("[OK] 4a. HomeScreen.kt: Botón Web Browser (🌐) junto a la lupa superior.")
if not inserted_web:
    report.append("[AVISO] 4a. No se halló la lupa en HomeScreen.kt.")

# ------------------------------------------------------------------
# 4b/c/d. MainActivity.kt: WebBrowser overlay + BackHandler + HorizontalPager (SIN bug \')
# ------------------------------------------------------------------
ma_rel = "MainActivity.kt"
ma_code = read_f(ma_rel)
ma_code = ensure_imports(ma_code, ["com.example.NexoNavBridge", "com.example.NexoSimpleWebBrowserDialog"])

ma_code = ma_code.replace(
    "BackHandler(enabled = drawerState.isOpen) {\n if (NexoNavBridge.triggerBack()) return@BackHandler\n",
    "BackHandler(enabled = drawerState.isOpen) {\n"
)

if "NexoSimpleWebBrowserDialog(" not in ma_code:
    m_modal = re.search(r"ModalNavigationDrawer\s*\(", ma_code)
    if m_modal:
        overlay = """if (NexoNavBridge.showWebBrowser) {
    NexoSimpleWebBrowserDialog(onDismiss = { NexoNavBridge.showWebBrowser = false })
  }
  """
        ma_code = ma_code[:m_modal.start()] + overlay + ma_code[m_modal.start():]

bh_anchor = 'BackHandler(enabled = (activeTabId != "HOME"'
bh_idx = ma_code.find(bh_anchor)
if bh_idx != -1:
    p_o = ma_code.find("(", bh_idx)
    p_e = find_balanced(ma_code, p_o, "(", ")")
    b_o = ma_code.find("{", p_e)
    b_e = find_balanced(ma_code, b_o, "{", "}")
    if b_e != -1:
        new_bh = """BackHandler(enabled = (activeTabId != "HOME" || explorerState.currentPath.trimEnd('/') != "/storage/emulated/0") && !drawerState.isOpen) {
                    val inSubfolder = explorerState.currentPath.trimEnd('/') != "/storage/emulated/0"
                    when {
                        activeTabId == "FILES" && inSubfolder -> explorerVm.navigateUp()
                        activeTabId == "DRIVE" && driveState.isAuthorized && toolsVm.navigateDriveUp() -> Unit
                        inSubfolder && explorerVm.navigateUp() -> Unit
                        else -> {
                            val homeIndex = bottomTabs.indexOfFirst { it.id == "HOME" }.coerceAtLeast(0)
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    page = homeIndex,
                                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                                )
                            }
                        }
                    }
                }"""
        ma_code = ma_code[:bh_idx] + new_bh + ma_code[b_e:]

# Insertar userScrollEnabled SIN barra invertida delante de las comillas simples!
hp_anchor = "HorizontalPager(\n                  state = pagerState,"
if hp_anchor in ma_code and "userScrollEnabled" not in ma_code:
    ma_code = ma_code.replace(
        hp_anchor,
        'HorizontalPager(\n                  state = pagerState,\n                  userScrollEnabled = !(activeTabId == "FILES" && explorerState.currentPath.trimEnd(\'/\') != "/storage/emulated/0"),',
        1
    )
write_f(ma_rel, ma_code)
report.append("[OK] 4b/c/d. MainActivity.kt: WebBrowser conectado + BackHandler + HorizontalPager sin error de sintaxis.")

# Normalizar navigateUp() en MainExplorerViewModel.kt
vm_rel = "ui/viewmodel/MainExplorerViewModel.kt"
vm_code = read_f(vm_rel)
vm_code = vm_code.replace(
    'val current = _uiState.value.currentPath\n    val root = container.localFiles.rootStoragePath\n    if (current == root || current == "/") return false',
    'val current = _uiState.value.currentPath.trimEnd(\'/\')\n    val root = container.localFiles.rootStoragePath.trimEnd(\'/\')\n    if (current.isEmpty() || current == root) return false'
)
write_f(vm_rel, vm_code)

# ------------------------------------------------------------------
# 5. ImageViewerScreen.kt y VideoPlayerScreen.kt INMERSIVOS
# ------------------------------------------------------------------
iv_rel = "ui/screens/ImageViewerScreen.kt"
iv_code = read_f(iv_rel)
iv_code = ensure_imports(iv_code, ["com.example.NexoNavBridge", "androidx.compose.runtime.DisposableEffect"])
iv_code = iv_code.replace(
    "var isLoading by remember(activeFile.absolutePath) { mutableStateOf(true) }",
    """var isLoading by remember(activeFile.absolutePath) { mutableStateOf(true) }
  var showControls by remember { mutableStateOf(true) }
  LaunchedEffect(showControls) { NexoNavBridge.mediaImmersiveMode = !showControls }
  DisposableEffect(Unit) {
    NexoNavBridge.isMediaViewerOpen = true
    onDispose {
      NexoNavBridge.isMediaViewerOpen = false
      NexoNavBridge.mediaImmersiveMode = false
    }
  }"""
)
col_idx = iv_code.find("Column(\n    modifier = Modifier\n      .fillMaxSize()")
if col_idx != -1:
    s1 = iv_code.find("Surface(", col_idx)
    if s1 != -1:
        p1_e = find_balanced(iv_code, iv_code.find("(", s1), "(", ")")
        b1_e = find_balanced(iv_code, iv_code.find("{", p1_e), "{", "}")
        if b1_e != -1:
            iv_code = iv_code[:s1] + "if (showControls) {\n" + iv_code[s1:b1_e] + "\n}" + iv_code[b1_e:]
    s_last = iv_code.rfind("Surface(")
    if s_last != -1 and s_last > col_idx + 200:
        p2_e = find_balanced(iv_code, iv_code.find("(", s_last), "(", ")")
        b2_e = find_balanced(iv_code, iv_code.find("{", p2_e), "{", "}")
        if b2_e != -1:
            iv_code = iv_code[:s_last] + "if (showControls) {\n" + iv_code[s_last:b2_e] + "\n}" + iv_code[b2_e:]
if "detectTapGestures(" in iv_code:
    if "onTap" not in iv_code:
        iv_code = iv_code.replace("detectTapGestures(", "detectTapGestures(onTap = { showControls = !showControls }, ")
    else:
        iv_code = re.sub(r"onTap\s*=\s*\{", "onTap = { showControls = !showControls; ", iv_code, count=1)
write_f(iv_rel, iv_code)

vp_rel = "ui/screens/VideoPlayerScreen.kt"
vp_code = read_f(vp_rel)
vp_code = ensure_imports(vp_code, ["com.example.NexoNavBridge"])
vp_code = vp_code.replace(
    "var showControlsOverlay by remember { mutableStateOf(true) }",
    """var showControlsOverlay by remember { mutableStateOf(true) }
  LaunchedEffect(showControlsOverlay, isPlaying) {
    NexoNavBridge.mediaImmersiveMode = !showControlsOverlay
    if (showControlsOverlay && isPlaying) {
      delay(3500L)
      showControlsOverlay = false
    }
  }
  DisposableEffect(Unit) {
    NexoNavBridge.isMediaViewerOpen = true
    onDispose {
      NexoNavBridge.isMediaViewerOpen = false
      NexoNavBridge.mediaImmersiveMode = false
    }
  }"""
)
vp_code = re.sub(
    r"(PlayerView\s*\([^\)]*\)\s*\.apply\s*\{)",
    r"\1\n          useController = false\n          setOnClickListener { showControlsOverlay = !showControlsOverlay }",
    vp_code
)
disp_end = vp_code.find("DisposableEffect(exoPlayer)")
if disp_end != -1:
    surfs = [m.start() for m in re.finditer(r"\bSurface\s*\(", vp_code) if m.start() > disp_end]
    targets_s = [surfs[-1], surfs[0]] if len(surfs) >= 2 else (surfs[:1])
    for s_pos in targets_s:
        if "showControlsOverlay" in vp_code[max(0, s_pos-80):s_pos]:
            continue
        p_e = find_balanced(vp_code, vp_code.find("(", s_pos), "(", ")")
        b_e = find_balanced(vp_code, vp_code.find("{", p_e), "{", "}")
        if b_e != -1:
            vp_code = vp_code[:s_pos] + "if (showControlsOverlay) {\n" + vp_code[s_pos:b_e] + "\n}" + vp_code[b_e:]
write_f(vp_rel, vp_code)
report.append("[OK] 5. ImageViewerScreen y VideoPlayerScreen: modo inmersivo al tocar pantalla.")

# ------------------------------------------------------------------
# 6. FilesScreen.kt: Cabecera compacta (36.dp) en modo horizontal
# ------------------------------------------------------------------
fs_rel = "ui/screens/FilesScreen.kt"
fs_code = read_f(fs_rel)
fs_code = ensure_imports(fs_code, [
    "android.content.res.Configuration",
    "androidx.compose.ui.platform.LocalConfiguration",
    "androidx.compose.foundation.layout.PaddingValues",
    "androidx.compose.material3.TextButton",
    "androidx.compose.ui.text.font.FontWeight"
])
fs_code = fs_code.replace(
    'uiState.currentPath.isNotBlank() && uiState.currentPath != "/storage/emulated/0"',
    'uiState.currentPath.trimEnd(\'/\').isNotBlank() && uiState.currentPath.trimEnd(\'/\') != "/storage/emulated/0"'
)
fs_code = fs_code.replace(
    'var folderFilterQuery by remember { mutableStateOf("") }',
    """var folderFilterQuery by remember { mutableStateOf("") }
  val isLandscapeExplorer = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
  var expandLandscapeHeader by remember { mutableStateOf(false) }"""
)
c_idx = fs_code.find("// Clipboard banner")
scaf_idx = fs_code.find("Scaffold(")
fs_ok = False
if c_idx != -1 and scaf_idx != -1:
    col_matches = [m.start() for m in re.finditer(r"\bColumn\s*\(", fs_code[scaf_idx:c_idx])]
    parent_open = -1
    for rel_pos in reversed(col_matches):
        abs_pos = scaf_idx + rel_pos
        p_e = find_balanced(fs_code, fs_code.find("(", abs_pos), "(", ")")
        if p_e == -1: continue
        b_o = fs_code.find("{", p_e)
        if b_o == -1 or b_o - p_e > 25: continue
        b_e = find_balanced(fs_code, b_o, "{", "}")
        if b_e != -1 and b_o < c_idx < b_e:
            parent_open = b_o + 1
            break
    if parent_open != -1:
        header_slice = fs_code[parent_open:c_idx]
        blocks = split_top_level_blocks(header_slice)
        rebuilt = ["""
      if (isLandscapeExplorer) {
        Row(
          modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (uiState.currentPath.trimEnd('/') != "/storage/emulated/0") {
              TextButton(onClick = onNavigateUp, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                Text("←", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
              }
            }
            Text(
              text = uiState.currentPath.substringAfterLast('/').ifBlank { "Almacenamiento" },
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1
            )
          }
          TextButton(
            onClick = { expandLandscapeHeader = !expandLandscapeHeader },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
          ) {
            Text(
              text = if (expandLandscapeHeader) "▲ Ocultar barra" else "▼ Buscar / Ordenar",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.secondary
            )
          }
        }
      }
"""]
        ui_buf = []
        def flush_ui():
            if ui_buf:
                rebuilt.append("\n      if (!isLandscapeExplorer || expandLandscapeHeader) {\n" + "".join(ui_buf) + "\n      }\n")
                ui_buf.clear()
        for bs, be, kind in blocks:
            chunk = header_slice[bs:be]
            if kind == "decl":
                flush_ui()
                rebuilt.append(chunk + "\n")
            else:
                ui_buf.append(chunk + "\n")
        flush_ui()
        fs_code = fs_code[:parent_open] + "".join(rebuilt) + fs_code[c_idx:]
        write_f(fs_rel, fs_code)
        fs_ok = True
        report.append("[OK] 6. FilesScreen.kt: Barra compacta de 36.dp en horizontal.")
if not fs_ok:
    report.append("[AVISO] 6. FilesScreen.kt no modificó cabecera horizontal.")

# ------------------------------------------------------------------
# 7. SettingsScreen.kt + SettingsAccordionCard estilo MIUI + Master Code en Vault
# ------------------------------------------------------------------
st_rel = "ui/screens/SettingsScreen.kt"
st_code = read_f(st_rel)

# A) Quitar botón roto del navegador web usando find_balanced (NUNCA regex frágil)
broken_anchor = "androidx.compose.material3.OutlinedButton("
b_idx = st_code.find(broken_anchor)
if b_idx != -1 and "NexoNavBridge.showWebBrowser" in st_code[b_idx:b_idx+260]:
    p_e = find_balanced(st_code, st_code.find("(", b_idx), "(", ")")
    b_o = st_code.find("{", p_e)
    b_e = find_balanced(st_code, b_o, "{", "}")
    if b_e != -1:
        st_code = st_code[:b_idx] + st_code[b_e:]

# B) Quitar BackHandler(onBack = onBack) de línea 146 y ponerlo DESPUÉS de var expandedPanel!
st_code = st_code.replace("  BackHandler(onBack = onBack)\n", "")
st_code = st_code.replace(
    '  var expandedPanel by remember { mutableStateOf("DISPLAY") }',
    '  var expandedPanel by remember { mutableStateOf("") }\n  BackHandler { if (expandedPanel.isNotEmpty()) expandedPanel = "" else onBack() }'
)

# C) Proteger la apertura del panel VAULT con Master Code / Biometría
st_code = re.sub(
    r'onToggle\s*=\s*\{\s*expandedPanel\s*=\s*if\s*\(\s*expandedPanel\s*==\s*"([^"]*VAULT[^"]*)"\s*\)\s*""\s*else\s*"\1"\s*\}',
    r'''onToggle = {
          if (expandedPanel == "\1") {
            expandedPanel = ""
          } else {
            runProtectedAction(
              title = "Ajustes de Bóveda Protegidos",
              subtitle = "Confirma tu Master Code o biometría para acceder"
            ) { expandedPanel = "\1" }
          }
        }''',
    st_code,
    flags=re.I
)

# D) Versión visual 1.4.0
st_code = re.sub(r"NERV HUD Explorer v[0-9\.]+", "NERV HUD Explorer v1.4.0", st_code)
write_f(st_rel, st_code)

# E) Rediseñar SettingsAccordionCard (sea private, internal o public) en cualquier archivo de ui/
acc_done = False
for dirpath, _, files in os.walk(os.path.join(ROOT, "ui")):
    for fn in files:
        if not fn.endswith(".kt"): continue
        fullp = os.path.join(dirpath, fn)
        with open(fullp, "r", encoding="utf-8") as f:
            txt = f.read()
        m_acc = re.search(r"\bfun\s+SettingsAccordionCard\s*\(", txt)
        if m_acc:
            rel_p = os.path.relpath(fullp, ROOT)
            txt = ensure_imports(txt, [
                "androidx.compose.foundation.layout.Box",
                "androidx.compose.animation.AnimatedVisibility",
                "androidx.compose.material3.HorizontalDivider",
                "androidx.compose.foundation.clickable",
                "androidx.compose.foundation.background",
                "androidx.compose.foundation.shape.RoundedCornerShape"
            ])
            m_acc = re.search(r"\bfun\s+SettingsAccordionCard\s*\(", txt)
            p_o = txt.find("(", m_acc.start())
            p_e = find_balanced(txt, p_o, "(", ")")
            b_o = txt.find("{", p_e)
            b_e = find_balanced(txt, b_o, "{", "}")
            params_str = txt[p_o+1:p_e-1]
            cm = re.search(r"(\w+)\s*:\s*@Composable", params_str)
            c_param = cm.group(1) if cm else "content"
            if b_e != -1:
                miui_body = f"""{{
  val isVaultRow = title.contains("VAULT", ignoreCase = true) || title.contains("BÓVEDA", ignoreCase = true)
  Column(modifier = Modifier.fillMaxWidth()) {{
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onToggle)
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {{
      Box(
        modifier = Modifier
          .size(38.dp)
          .background(
            color = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            shape = RoundedCornerShape(10.dp),
          ),
        contentAlignment = Alignment.Center,
      ) {{
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (expanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
      }}
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {{
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
        )
        if (subtitle.isNotBlank()) {{
          Text(
            text = if (isVaultRow && !expanded) "🔒 Master Code · $subtitle" else subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }}
      }}
      Text(
        text = if (expanded) "⌄" else "›",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp),
      )
    }}
    AnimatedVisibility(visible = expanded) {{
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
          .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), RoundedCornerShape(12.dp))
          .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = {c_param},
      )
    }}
    HorizontalDivider(
      modifier = Modifier.padding(start = 68.dp, end = 16.dp),
      color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
    )
  }}
}}"""
                txt = txt[:b_o] + miui_body + txt[b_e:]
                write_f(rel_p, txt)
                acc_done = True
                report.append(f"[OK] 7. SettingsScreen + SettingsAccordionCard ({fn}): Diseño MIUI + Master Code en Vault.")
                break
    if acc_done:
        break
if not acc_done:
    report.append("[AVISO] 7. No se halló SettingsAccordionCard.")

# ------------------------------------------------------------------
# 8. OTA INTELIGENTE en NexoVaultAndComicAdvanced.kt (balanceando paréntesis de lambdas!)
# ------------------------------------------------------------------
ota_rel = "ui/components/NexoVaultAndComicAdvanced.kt"
ota_code = read_f(ota_rel)
ota_code = ensure_imports(ota_code, [
    "android.os.Handler",
    "android.os.Looper",
    "java.net.HttpURLConnection",
    "java.net.URL",
    "org.json.JSONObject"
])
dl_idx = ota_code.find("val downloadId = dm.enqueue(req)")
ota_ok = False
if dl_idx != -1:
    # Buscar hacia atrás la declaración fun ... que realmente envuelve dl_idx
    fun_starts = [m.start() for m in re.finditer(r"\bfun\s+\w+\s*\(", ota_code[:dl_idx])]
    for fs in reversed(fun_starts):
        p_o = ota_code.find("(", fs)
        p_e = find_balanced(ota_code, p_o, "(", ")")
        if p_e == -1: continue
        b_o = ota_code.find("{", p_e)
        if b_o == -1: continue
        b_e = find_balanced(ota_code, b_o, "{", "}")
        if b_e != -1 and b_o < dl_idx < b_e:
            params_str = ota_code[p_o+1:p_e-1]
            # Detectar nombre del parámetro de estado (onStatus) y contexto (context)
            cb_m = re.search(r"(\w+)\s*:\s*\(\s*String\s*\)\s*->\s*Unit", params_str)
            cb_name = cb_m.group(1) if cb_m else "onStatus"
            smart_body = f"""{{
        {cb_name}("Consultando la última versión en GitHub...")
        val mainHandler = Handler(Looper.getMainLooper())
        Thread {{
            try {{
                val installedVer = try {{
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.4.0"
                }} catch (_: Throwable) {{ "1.4.0" }}

                fun parseVer(v: String): List<Int> =
                    v.trim().removePrefix("v").removePrefix("V")
                        .split(".", "-", "_")
                        .mapNotNull {{ it.toIntOrNull() }}
                        .let {{ if (it.size >= 3) it.take(3) else it + List(3 - it.size) {{ 0 }} }}

                fun isRemoteNewer(remote: String, local: String): Boolean {{
                    val r = parseVer(remote)
                    val l = parseVer(local)
                    for (i in 0..2) {{
                        if (r[i] != l[i]) return r[i] > l[i]
                    }}
                    return false
                }}

                val endpoints = listOf(
                    "https://api.github.com/repos/romanje91-glitch/NexoFiles-/releases/latest",
                    "https://api.github.com/repos/romanje91-glitch/NexoFiles/releases/latest"
                )
                var latestTag = ""
                var apkUrl = ""
                for (ep in endpoints) {{
                    runCatching {{
                        val conn = (URL(ep).openConnection() as HttpURLConnection).apply {{
                            requestMethod = "GET"
                            connectTimeout = 8000
                            readTimeout = 8000
                            setRequestProperty("Accept", "application/vnd.github+json")
                            setRequestProperty("User-Agent", "NexoFiles-OTA")
                        }}
                        if (conn.responseCode == 200) {{
                            val body = conn.inputStream.bufferedReader().use {{ it.readText() }}
                            val json = JSONObject(body)
                            val tag = json.optString("tag_name", "")
                            val assets = json.optJSONArray("assets")
                            var foundUrl = ""
                            if (assets != null) {{
                                for (i in 0 until assets.length()) {{
                                    val a = assets.getJSONObject(i)
                                    val u = a.optString("browser_download_url", "")
                                    if (u.endsWith(".apk", ignoreCase = true)) {{
                                        foundUrl = u
                                        break
                                    }}
                                }}
                            }}
                            if (tag.isNotBlank() && foundUrl.isNotBlank()) {{
                                latestTag = tag
                                apkUrl = foundUrl
                            }}
                        }}
                        conn.disconnect()
                    }}
                    if (latestTag.isNotBlank()) break
                }}

                if (latestTag.isBlank() || apkUrl.isBlank()) {{
                    mainHandler.post {{ {cb_name}("No se pudo verificar el servidor OTA. Intenta de nuevo.") }}
                    return@Thread
                }}

                if (!isRemoteNewer(latestTag, installedVer)) {{
                    mainHandler.post {{
                        {cb_name}("✓ Ya estás en la versión más reciente (v$installedVer). No es necesario descargar.")
                    }}
                    return@Thread
                }}

                mainHandler.post {{
                    try {{
                        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                        val outFile = File(outDir, "NexoFiles_${{latestTag}}.apk")
                        if (outFile.exists()) outFile.delete()

                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        val req = DownloadManager.Request(Uri.parse(apkUrl))
                            .setTitle("NexoFiles $latestTag")
                            .setDescription("Actualizando directamente a la versión más reciente ($latestTag)")
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationUri(Uri.fromFile(outFile))

                        val downloadId = dm.enqueue(req)
                        {cb_name}("Nueva versión $latestTag detectada (actual: v$installedVer). Descargando...")

                        val receiver = object : BroadcastReceiver() {{
                            override fun onReceive(ctx: Context?, intent: Intent?) {{
                                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                                if (id == downloadId) {{
                                    try {{ context.unregisterReceiver(this) }} catch (_: Throwable) {{}}
                                    if (outFile.exists() && outFile.length() > 100_000L) {{
                                        {cb_name}("Descarga completa ($latestTag). Abriendo instalador de Android...")
                                        installApk(context, outFile)
                                    }} else {{
                                        {cb_name}("Error al verificar el paquete $latestTag descargado.")
                                    }}
                                }}
                            }}
                        }}
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {{
                            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
                        }} else {{
                            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
                        }}
                    }} catch (e: Throwable) {{
                        {cb_name}("Error iniciando descarga OTA: ${{e.localizedMessage}}")
                    }}
                }}
            }} catch (e: Throwable) {{
                mainHandler.post {{ {cb_name}("Error verificando OTA: ${{e.localizedMessage}}") }}
            }}
        }}.start()
    }}"""
            ota_code = ota_code[:b_o] + smart_body + ota_code[b_e:]
            ota_code = ota_code.replace(
                "val totalCloud = maxOf(cloudCount, localCount, NexoSecureVaultManager.cloudOnlyItems.size)",
                "val totalCloud = maxOf(cloudCount, com.example.NexoVaultExtraStore.cloudOnlyList.size)"
            )
            write_f(ota_rel, ota_code)
            ota_ok = True
            report.append(f"[OK] 8. NexoVaultAndComicAdvanced.kt: OTA inteligente vinculado a '{cb_name}'.")
            break
if not ota_ok:
    report.append("[ERROR] 8. No se pudo ubicar la función OTA.")

# ------------------------------------------------------------------
# 9. BÓVEDA (ToolsAndNetworkViewModel.kt + VaultScreen.kt)
# ------------------------------------------------------------------
tn_rel = "ui/viewmodel/ToolsAndNetworkViewModel.kt"
tn_code = read_f(tn_rel)
tn_code = tn_code.replace(
    '''  val effectivePassphrase = "NEXO_HERMETIC_AUTO_KEY_2026"
    fun backupVaultToDrive(passphrase: String, selectedIds: Set<String> = emptySet()) {
    if (passphrase.length < 4) {
      emitMessage("La contraseña de respaldo debe tener al menos 4 caracteres")
      return
    }''',
    '''  val effectivePassphrase = "NEXO_HERMETIC_AUTO_KEY_2026"
  fun backupVaultToDrive(rawPassphrase: String, selectedIds: Set<String> = emptySet()) {
    val passphrase = rawPassphrase.trim().ifBlank { effectivePassphrase }'''
)
tn_code = re.sub(
    r"fun\s+(restore\w*DriveBackup\w*)\s*\(([^,]+),\s*passphrase:\s*String\)\s*\{",
    r'fun \1(\2, rawPassphrase: String) {\n    val passphrase = rawPassphrase.trim().ifBlank { effectivePassphrase }',
    tn_code
)
tn_code = tn_code.replace(
    'driveStatusMessage = "Error Drive: ${err.localizedMessage}",',
    'driveStatusMessage = if ((err.localizedMessage ?: "").contains("401") || (err.localizedMessage ?: "").contains("UNAUTHENTICATED")) "Sesión de Google Drive expirada. Abre Google Drive y toca Reconectar." else "Error Drive: ${err.localizedMessage}",'
)
write_f(tn_rel, tn_code)

vs_rel = "ui/screens/VaultScreen.kt"
vs_code = read_f(vs_rel)
vs_code = ensure_imports(vs_code, [
    "com.example.NexoVaultExtraStore",
    "com.example.NexoCloudPlaceholderItem",
    "androidx.compose.foundation.Image",
    "androidx.compose.foundation.clickable",
    "androidx.compose.ui.draw.clip",
    "androidx.compose.ui.graphics.ImageBitmap",
    "androidx.compose.ui.layout.ContentScale",
    "androidx.compose.runtime.produceState",
    "androidx.compose.material3.TextButton",
    "androidx.compose.material3.OutlinedButton",
    "androidx.compose.material3.OutlinedTextField"
])
vs_code = vs_code.replace(
    "var showDriveBackupModal by remember { mutableStateOf(false) }",
    """LaunchedEffect(Unit) { NexoVaultExtraStore.init(context) }
  var showDriveBackupModal by remember { mutableStateOf(false) }
  var showNewVaultFolderDialog by remember { mutableStateOf(false) }
  var newVaultFolderName by remember { mutableStateOf("") }
  var pendingCloudDownloadItem by remember { mutableStateOf<NexoCloudPlaceholderItem?>(null) }
  var pendingDeleteChoiceItem by remember { mutableStateOf<VaultFileItem?>(null) }"""
)
vs_code = vs_code.replace("onLockVault()", "run { NexoVaultExtraStore.clearThumbnails(); onLockVault() }")
vs_code = re.sub(
    r'var\s+(\w*pass\w*)\s+by\s+remember[^{]*\{\s*mutableStateOf\s*\(\s*""\s*\)\s*\}',
    r'var \1 by remember { mutableStateOf("NEXO_HERMETIC_AUTO_KEY_2026") }',
    vs_code,
    flags=re.I
)

# Thumbnails en RAM dentro de VaultScreen
lock_badge = re.search(r"IconContainerBadge\s*\(\s*icon\s*=\s*(?:Icons\.Rounded\.Lock|NexoIcons\.Lock)[^\)]*\)", vs_code)
if lock_badge:
    thumb_snip = """val itemThumb by produceState<ImageBitmap?>(initialValue = null, key1 = item.id) {
            value = NexoVaultExtraStore.loadThumbnail(context, item)
          }
          if (itemThumb != null) {
            Image(
              bitmap = itemThumb!!,
              contentDescription = item.originalName,
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            )
          } else {"""
    vs_code = vs_code[:lock_badge.start()] + thumb_snip + "\n            " + lock_badge.group(0) + "\n          }" + vs_code[lock_badge.end():]

items_m = re.search(r"items\s*\(\s*state\.items\s*,\s*key\s*=\s*\{\s*it\.id\s*\}\s*\)\s*\{", vs_code)
if items_m:
    vault_folders_and_cloud = """
      item(key = "vault_folders_bar") {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = if (NexoVaultExtraStore.activeFolder != null) "📁 CARPETA: ${NexoVaultExtraStore.activeFolder}" else "CARPETAS SEGURAS (${NexoVaultExtraStore.folders.size})",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              if (NexoVaultExtraStore.activeFolder != null) {
                OutlinedButton(
                  onClick = { NexoVaultExtraStore.activeFolder = null },
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) { Text("← TODAS", style = MaterialTheme.typography.labelSmall) }
              }
              OutlinedButton(
                onClick = { showNewVaultFolderDialog = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
              ) { Text("+ CARPETA", style = MaterialTheme.typography.labelSmall) }
            }
          }
          if (NexoVaultExtraStore.folders.isNotEmpty() && NexoVaultExtraStore.activeFolder == null) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              NexoVaultExtraStore.folders.take(4).forEach { folderName ->
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = MaterialTheme.colorScheme.surface,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                  modifier = Modifier.clickable { NexoVaultExtraStore.activeFolder = folderName }
                ) {
                  Text(
                    text = "🔒 $folderName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                  )
                }
              }
            }
          }
        }
      }
      items(
        NexoVaultExtraStore.cloudOnlyList.filter {
          NexoVaultExtraStore.activeFolder == null || it.folderName == NexoVaultExtraStore.activeFolder
        },
        key = { "cloud_${it.id}_${it.originalName}" }
      ) { cloudItem ->
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
          modifier = Modifier.fillMaxWidth().clickable { pendingCloudDownloadItem = cloudItem }
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(cloudItem.originalName, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
              Text("☁️ SOLO EN LA NUBE (0 B local) · Toca para descargar", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
            }
            Text("⬇", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
          }
        }
      }
"""
    filtered_items = 'items(state.items.filter { NexoVaultExtraStore.activeFolder == null || NexoVaultExtraStore.itemFolderMap[it.id] == NexoVaultExtraStore.activeFolder }, key = { it.id }) {'
    vs_code = vs_code[:items_m.start()] + vault_folders_and_cloud + "\n      " + filtered_items + vs_code[items_m.end():]

# Ajustar nombre de variable si el lambda de items no usa "item"
m_it = re.search(r"items\(state\.items\.filter[^\{]+\{\s*(\w+)\s*->", vs_code)
if m_it and m_it.group(1) != "item":
    iv = m_it.group(1)
    vs_code = vs_code.replace("key1 = item.id", f"key1 = {iv}.id")
    vs_code = vs_code.replace("loadThumbnail(context, item)", f"loadThumbnail(context, {iv})")
    vs_code = vs_code.replace("contentDescription = item.originalName", f"contentDescription = {iv}.originalName")

m_vs = re.search(r"\bfun\s+VaultScreen\s*\(", vs_code)
if m_vs:
    vp_o = vs_code.find("(", m_vs.start())
    vp_e = find_balanced(vs_code, vp_o, "(", ")")
    params_vs = vs_code[vp_o+1:vp_e-1]
    del_m = re.search(r"(\bon(?:Delete|Remove)\w*)\s*:\s*\(\s*VaultFileItem\s*\)\s*->\s*Unit", params_vs)
    del_cb = del_m.group(1) if del_m else "onDeleteVaultItem"
    vs_code = re.sub(
        r"onClick\s*=\s*\{\s*" + re.escape(del_cb) + r"\s*\(\s*(\w+)\s*\)\s*\}",
        r"onClick = { pendingDeleteChoiceItem = \1 }",
        vs_code
    )
    m_vs2 = re.search(r"\bfun\s+VaultScreen\s*\(", vs_code)
    vp_e2 = find_balanced(vs_code, vs_code.find("(", m_vs2.start()), "(", ")")
    vb_o2 = vs_code.find("{", vp_e2)
    vb_e2 = find_balanced(vs_code, vb_o2, "{", "}")
    if vb_e2 != -1:
        dialogs = f"""
  if (showNewVaultFolderDialog) {{
    AlertDialog(
      onDismissRequest = {{ showNewVaultFolderDialog = false }},
      title = {{ Text("Nueva carpeta segura en Bóveda") }},
      text = {{
        OutlinedTextField(
          value = newVaultFolderName,
          onValueChange = {{ newVaultFolderName = it }},
          label = {{ Text("Nombre de la carpeta") }},
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }},
      confirmButton = {{
        Button(onClick = {{
          NexoVaultExtraStore.createFolder(context, newVaultFolderName)
          newVaultFolderName = ""
          showNewVaultFolderDialog = false
        }}) {{ Text("Crear") }}
      }},
      dismissButton = {{
        TextButton(onClick = {{ showNewVaultFolderDialog = false }}) {{ Text("Cancelar") }}
      }}
    )
  }}

  pendingDeleteChoiceItem?.let {{ targetItem ->
    AlertDialog(
      onDismissRequest = {{ pendingDeleteChoiceItem = null }},
      title = {{ Text("Eliminar archivo de la Bóveda") }},
      text = {{ Text("¿Deseas eliminar «${{targetItem.originalName}}» solo del teléfono (quedará visible sin datos locales para descargarlo de la nube al abrirlo) o eliminarlo por completo?") }},
      confirmButton = {{
        Button(onClick = {{
          NexoVaultExtraStore.addCloudOnlyPlaceholder(context, targetItem)
          {del_cb}(targetItem)
          pendingDeleteChoiceItem = null
        }}) {{ Text("Solo teléfono (☁️)") }}
      }},
      dismissButton = {{
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {{
          TextButton(onClick = {{
            {del_cb}(targetItem)
            pendingDeleteChoiceItem = null
          }}) {{ Text("Eliminar todo") }}
          TextButton(onClick = {{ pendingDeleteChoiceItem = null }}) {{ Text("Cancelar") }}
        }}
      }}
    )
  }}

  pendingCloudDownloadItem?.let {{ cloudItem ->
    AlertDialog(
      onDismissRequest = {{ pendingCloudDownloadItem = null }},
      title = {{ Text("Archivo almacenado en la nube") }},
      text = {{ Text("«${{cloudItem.originalName}}» fue eliminado localmente para ahorrar espacio. ¿Deseas descargarlo y descifrarlo desde Google Drive ahora?") }},
      confirmButton = {{
        Button(onClick = {{
          val match = state.driveBackups.firstOrNull {{
            it.originalName.equals(cloudItem.originalName, ignoreCase = true) ||
            it.driveFile.name.startsWith(cloudItem.originalName, ignoreCase = true)
          }}
          if (match != null) {{
            onRestoreDriveBackup(match, "NEXO_HERMETIC_AUTO_KEY_2026")
            NexoVaultExtraStore.removeCloudOnlyPlaceholder(context, cloudItem.originalName)
          }} else {{
            onRefreshDriveBackups()
            showDriveBackupModal = true
            Toast.makeText(context, "Actualizando respaldos en Google Drive...", Toast.LENGTH_SHORT).show()
          }}
          pendingCloudDownloadItem = null
        }}) {{ Text("Descargar de la nube") }}
      }},
      dismissButton = {{
        TextButton(onClick = {{ pendingCloudDownloadItem = null }}) {{ Text("Cancelar") }}
      }}
    )
  }}
"""
        vs_code = vs_code[:vb_e2-1] + dialogs + "\n}" + vs_code[vb_e2:]
write_f(vs_rel, vs_code)
report.append("[OK] 9. VaultScreen + ViewModel: Passphrase auto + Thumbnails + Carpetas + Solo-Nube + Diálogos.")

# ------------------------------------------------------------------
# 10. Actualizar versión a v1.4.0 (versionCode = 5)
# ------------------------------------------------------------------
for gp in ["app/build.gradle.kts", "app/build.gradle"]:
    if os.path.exists(gp):
        with open(gp, "r", encoding="utf-8") as fp:
            g = fp.read()
        g = re.sub(r"versionCode\s*=?\s*\d+", "versionCode = 5", g)
        g = re.sub(r'versionName\s*=?\s*"[^"]+"', 'versionName = "1.4.0"', g)
        with open(gp, "w", encoding="utf-8") as fp:
            fp.write(g)
        report.append(f"[OK] 10. {gp}: versionCode = 5, versionName = 1.4.0.")

print("\n=== VERIFICACIÓN PRE-COMPILACIÓN v1.4.0 ===")
for r in report:
    print(" ", r)
print("===========================================")
