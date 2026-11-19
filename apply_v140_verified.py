import os, re, shutil

print("==========================================================")
print("  NEXOFILES v1.4.0 - PARCHE QUIRÚRGICO VERIFICADO")
print("==========================================================")

ROOT = "app/src/main/java/com/example"

def read_f(rel):
    p = os.path.join(ROOT, rel)
    with open(p, "r", encoding="utf-8") as f:
        return f.read()

def write_f(rel, txt):
    p = os.path.join(ROOT, rel)
    with open(p, "w", encoding="utf-8") as f:
        f.write(txt)

def ensure_imports(code, imports):
    m = re.search(r"^package\s+[\w\.]+\s*\n", code, re.M)
    if not m:
        return code
    add = ""
    for imp in imports:
        if imp not in code:
            add += f"import {imp}\n"
    if not add:
        return code
    return code[:m.end()] + add + code[m.end():]

def find_balanced(text, open_idx, open_ch="{", close_ch="}"):
    depth = 0
    in_str = False
    esc = False
    for i in range(open_idx, len(text)):
        ch = text[i]
        if esc:
            esc = False
            continue
        if ch == "\\":
            esc = True
            continue
        if ch == '"':
            in_str = not in_str
            continue
        if not in_str:
            if ch == open_ch:
                depth += 1
            elif ch == close_ch:
                depth -= 1
                if depth == 0:
                    return i + 1
    return -1

report = []

# ------------------------------------------------------------------
# 1. REESCRIBIR NexoMasterBridge.kt (Navegador Web + Icono Globo +
#    Thumbnails Vault en RAM + Carpetas Vault + Cloud-Only Placeholders)
# ------------------------------------------------------------------
bridge_kt = r"""package com.example

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
import com.example.data.repo.VaultRepository
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

data class NexoCloudPlaceholderItem(
    val id: String,
    val originalName: String,
    val sizeBytes: Long,
    val mimeType: String,
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
                        sizeBytes = o.optLong("size", 0L),
                        mimeType = o.optString("mime", "application/octet-stream"),
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
                put("size", item.sizeBytes)
                put("mime", item.mimeType)
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
                sizeBytes = item.sizeBytes,
                mimeType = item.mimeType,
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
                val decRes = repo.decryptToCacheAndOpen(item)
                val tmp = decRes.getOrNull() ?: return@runCatching null
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
report.append("[OK] 1. NexoMasterBridge.kt: Navegador Web + Icono Globo + Thumbnails Vault + Carpetas + Cloud Placeholders.")

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
    new_card = r"""@Composable
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
    report.append("[OK] 2. NexoSettingsModules.kt: Selector de Temas EVA convertido a ruleta infinita de 1 línea + Dropdown.")
else:
    report.append("[ERROR] 2. No se encontró NexoEvaThemeSelectorCard en NexoSettingsModules.kt")

# ------------------------------------------------------------------
# 3. QUICK SETTINGS: Añadir Selector de Temas EVA de 1 línea debajo de HUD PROFILE
# ------------------------------------------------------------------
qs_patched = False
for dirpath, _, files in os.walk(ROOT):
    for fn in files:
        if not fn.endswith(".kt"): continue
        fullp = os.path.join(dirpath, fn)
        with open(fullp, "r", encoding="utf-8") as f:
            c = f.read()
        if "strings.quickSettingsTitle" in c and "strings.appThemeTitle" in c and "NexoEvaThemeSelectorCard(compact = true)" not in c:
            c = ensure_imports(c, ["com.example.ui.components.NexoEvaThemeSelectorCard"])
            # Insertar justo antes de strings.hiddenFilesTitle
            idx_h = c.find("strings.hiddenFilesTitle")
            if idx_h != -1:
                # Buscar el inicio del bloque Row/Surface/BichromaticCard que contiene hiddenFilesTitle
                anc = max(c.rfind("BichromaticCard(", max(0, idx_h - 400), idx_h),
                          c.rfind("Surface(", max(0, idx_h - 400), idx_h),
                          c.rfind("Row(", max(0, idx_h - 400), idx_h))
                if anc != -1:
                    inject = 'NexoEvaThemeSelectorCard(compact = true)\nSpacer(modifier = Modifier.height(6.dp))\n'
                    c = c[:anc] + inject + c[anc:]
                    with open(fullp, "w", encoding="utf-8") as f:
                        f.write(c)
                    qs_patched = True
                    report.append(f"[OK] 3. Quick Settings ({fn}): Selector de Temas EVA de 1 línea agregado.")
                    break
    if qs_patched:
        break
if not qs_patched:
    report.append("[AVISO] 3. No se encontró Quick Settings con strings.appThemeTitle.")

# ------------------------------------------------------------------
# 4. BOTÓN NAVEGADOR WEB A LA PAR DE LA LUPA EN HomeScreen.kt + OVERLAY EN MainActivity.kt
# ------------------------------------------------------------------
home_rel = "ui/screens/HomeScreen.kt"
h_code = read_f(home_rel)
h_code = ensure_imports(h_code, ["com.example.NexoNavBridge", "com.example.NexoGlobeIcon"])
if "NexoNavBridge.showWebBrowser = true" not in h_code:
    # Buscar el IconButton del buscador en la barra superior de HomeScreen (cerca de greetingHello)
    g_idx = h_code.find("strings.greetingHello")
    if g_idx != -1:
        search_area = h_code[g_idx:g_idx + 2500]
        # Buscar IconButton con NexoIcons.Search o Icons.Rounded.Search o onSearchClick
        sm = re.search(r"IconButton\s*\([^\)]*\)\s*\{[^\}]{0,350}(?:Search|search)", search_area)
        if sm:
            ins_at = g_idx + sm.start()
            web_icon_btn = """IconButton(
              onClick = { NexoNavBridge.showWebBrowser = true },
              modifier = Modifier
                .size(40.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp)),
            ) {
              NexoGlobeIcon(tint = MaterialTheme.colorScheme.primary, size = 20.dp)
            }
            Spacer(modifier = Modifier.width(6.dp))
            """
            h_code = h_code[:ins_at] + web_icon_btn + h_code[ins_at:]
            write_f(home_rel, h_code)
            report.append("[OK] 4a. HomeScreen.kt: Botón Web Browser (🌐) colocado a la par del buscador superior.")
        else:
            # Fallback: buscar Box/Surface clickable de búsqueda cerca de greetingHello
            sm2 = re.search(r"(?:Surface|Box)\s*\([^\{]{0,250}clickable[^\)]*\)\s*\{[^\}]{0,350}Search", search_area)
            if sm2:
                ins_at = g_idx + sm2.start()
                web_icon_btn = """IconButton(
                  onClick = { NexoNavBridge.showWebBrowser = true },
                  modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp)),
                ) {
                  NexoGlobeIcon(tint = MaterialTheme.colorScheme.primary, size = 20.dp)
                }
                Spacer(modifier = Modifier.width(6.dp))
                """
                h_code = h_code[:ins_at] + web_icon_btn + h_code[ins_at:]
                write_f(home_rel, h_code)
                report.append("[OK] 4a. HomeScreen.kt: Botón Web Browser (🌐) colocado junto a la lupa (fallback Surface).")
            else:
                report.append("[AVISO] 4a. No se localizó la lupa junto a greetingHello en HomeScreen.kt.")

# Conectar diálogo del Navegador Web en MainActivity.kt + arreglar BackHandler y HorizontalPager
ma_rel = "MainActivity.kt"
ma_code = read_f(ma_rel)
ma_code = ensure_imports(ma_code, ["com.example.NexoNavBridge", "com.example.NexoSimpleWebBrowserDialog"])

# A) Inyectar NexoSimpleWebBrowserDialog(...) en el root de MainActivity (checando con paréntesis '(' para no confundirse con el import!)
if "NexoSimpleWebBrowserDialog(" not in ma_code:
    m_modal = re.search(r"ModalNavigationDrawer\s*\(", ma_code)
    if m_modal:
        overlay = """if (NexoNavBridge.showWebBrowser) {
    NexoSimpleWebBrowserDialog(onDismiss = { NexoNavBridge.showWebBrowser = false })
  }
  """
        ma_code = ma_code[:m_modal.start()] + overlay + ma_code[m_modal.start():]
        report.append("[OK] 4b. MainActivity.kt: Diálogo NexoSimpleWebBrowserDialog conectado en el contenedor raíz.")
    else:
        report.append("[ERROR] 4b. No se encontró ModalNavigationDrawer en MainActivity.kt")

# B) Arreglar Gesto Atrás en MainActivity.kt (L777-797): priorizar navigateUp() y bloquear swipe del pager en subcarpetas
old_bh = re.search(r"BackHandler\s*\(\s*enabled\s*=\s*\(activeTabId\s*!=\s*\"HOME\"[^\)]*\)\s*\{", ma_code)
if old_bh:
    bh_open = ma_code.find("{", old_bh.start())
    bh_end = find_balanced(ma_code, bh_open)
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
    ma_code = ma_code[:old_bh.start()] + new_bh + ma_code[bh_end:]
    report.append("[OK] 4c. MainActivity.kt: BackHandler corregido para subir carpeta por carpeta antes de ir a HOME.")

# Bloquear arrastre lateral del HorizontalPager cuando estás dentro de subcarpetas en FILES (evita que el gesto atrás salte a HOME)
if "userScrollEnabled" not in ma_code:
    ma_code = re.sub(
        r"(HorizontalPager\s*\(\s*state\s*=\s*pagerState\s*,)",
        r'\1\n                  userScrollEnabled = !(activeTabId == "FILES" && explorerState.currentPath.trimEnd(\'/\') != "/storage/emulated/0"),',
        ma_code,
        count=1
    )
    report.append("[OK] 4d. MainActivity.kt: HorizontalPager protegido contra saltos accidentales a HOME en subcarpetas.")

write_f(ma_rel, ma_code)

# Normalizar navigateUp() en MainExplorerViewModel.kt
vm_rel = "ui/viewmodel/MainExplorerViewModel.kt"
vm_code = read_f(vm_rel)
vm_code = vm_code.replace(
    'val current = _uiState.value.currentPath\n    val root = container.localFiles.rootStoragePath\n    if (current == root || current == "/") return false',
    'val current = _uiState.value.currentPath.trimEnd(\'/\')\n    val root = container.localFiles.rootStoragePath.trimEnd(\'/\')\n    if (current.isEmpty() || current == root) return false'
)
write_f(vm_rel, vm_code)

# ------------------------------------------------------------------
# 5. VISOR DE FOTOS (ImageViewerScreen.kt) Y VIDEO (VideoPlayerScreen.kt) INMERSIVOS
# ------------------------------------------------------------------
iv_rel = "ui/screens/ImageViewerScreen.kt"
iv_code = read_f(iv_rel)
iv_code = ensure_imports(iv_code, [
    "com.example.NexoNavBridge",
    "androidx.compose.runtime.DisposableEffect",
    "androidx.compose.animation.AnimatedVisibility"
])
if "var showControls by remember" not in iv_code:
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
    # Envolver el primer Surface (barra superior) y el último Surface (barra inferior de zoom/rotación) con AnimatedVisibility(visible = showControls)
    col_idx = iv_code.find("Column(\n    modifier = Modifier\n      .fillMaxSize()")
    if col_idx != -1:
        # Encontrar primer Surface después de Column
        s1 = iv_code.find("Surface(", col_idx)
        if s1 != -1:
            p1 = iv_code.find("(", s1)
            p1_end = find_balanced(iv_code, p1, "(", ")")
            b1 = iv_code.find("{", p1_end)
            b1_end = find_balanced(iv_code, b1, "{", "}")
            if b1_end != -1:
                top_bar = iv_code[s1:b1_end]
                iv_code = iv_code[:s1] + f"AnimatedVisibility(visible = showControls) {{\n{top_bar}\n}}" + iv_code[b1_end:]
        # Encontrar último Surface (barra inferior con 100%)
        s_last = iv_code.rfind("Surface(")
        if s_last != -1 and s_last > col_idx + 200:
            p2 = iv_code.find("(", s_last)
            p2_end = find_balanced(iv_code, p2, "(", ")")
            b2 = iv_code.find("{", p2_end)
            b2_end = find_balanced(iv_code, b2, "{", "}")
            if b2_end != -1:
                bot_bar = iv_code[s_last:b2_end]
                iv_code = iv_code[:s_last] + f"AnimatedVisibility(visible = showControls) {{\n{bot_bar}\n}}" + iv_code[b2_end:]
    # Conectar toque simple (onTap) en detectTapGestures para alternar showControls
    if "detectTapGestures(" in iv_code:
        if "onTap" not in iv_code:
            iv_code = iv_code.replace("detectTapGestures(", "detectTapGestures(onTap = { showControls = !showControls }, ")
        else:
            iv_code = re.sub(r"onTap\s*=\s*\{", "onTap = { showControls = !showControls; ", iv_code, count=1)
    write_f(iv_rel, iv_code)
    report.append("[OK] 5a. ImageViewerScreen.kt: Toque en pantalla oculta/muestra barras superior, inferior y navegación.")

vp_rel = "ui/screens/VideoPlayerScreen.kt"
vp_code = read_f(vp_rel)
vp_code = ensure_imports(vp_code, [
    "com.example.NexoNavBridge",
    "androidx.compose.animation.AnimatedVisibility"
])
if "NexoNavBridge.mediaImmersiveMode = !showControlsOverlay" not in vp_code:
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
    # Asegurar que PlayerView responda al toque para mostrar/ocultar barras
    vp_code = re.sub(
        r"(PlayerView\s*\([^\)]*\)\s*\.apply\s*\{)",
        r"\1\n          useController = false\n          setOnClickListener { showControlsOverlay = !showControlsOverlay }",
        vp_code
    )
    # Envolver barra superior (primer Surface después de Box/Column) y barra inferior (último Surface) con AnimatedVisibility(visible = showControlsOverlay)
    first_s = vp_code.find("Surface(", vp_code.find("AndroidView") - 1500 if vp_code.find("AndroidView") > 1500 else 0)
    # Mejor: buscar todos los Surface( después de DisposableEffect(exoPlayer)
    disp_end = vp_code.find("DisposableEffect(exoPlayer)")
    if disp_end != -1:
        surfs = [m.start() for m in re.finditer(r"\bSurface\s*\(", vp_code) if m.start() > disp_end]
        # Envolver primero y último en orden inverso para no mover índices
        targets_s = []
        if len(surfs) >= 2:
            targets_s = [surfs[-1], surfs[0]]
        elif len(surfs) == 1:
            targets_s = [surfs[0]]
        for s_pos in targets_s:
            if "showControlsOverlay" in vp_code[max(0, s_pos-80):s_pos]:
                continue
            p_open = vp_code.find("(", s_pos)
            p_end = find_balanced(vp_code, p_open, "(", ")")
            b_open = vp_code.find("{", p_end)
            b_end = find_balanced(vp_code, b_open, "{", "}")
            if b_end != -1:
                blk = vp_code[s_pos:b_end]
                vp_code = vp_code[:s_pos] + f"AnimatedVisibility(visible = showControlsOverlay) {{\n{blk}\n}}" + vp_code[b_end:]
    write_f(vp_rel, vp_code)
    report.append("[OK] 5b. VideoPlayerScreen.kt: Toque en video + auto-ocultamiento (3.5s) para las barras de reproducción.")

# ------------------------------------------------------------------
# 6. EXPLORADOR EN MODO HORIZONTAL COMPACTO (FilesScreen.kt)
# ------------------------------------------------------------------
fs_rel = "ui/screens/FilesScreen.kt"
fs_code = read_f(fs_rel)
fs_code = ensure_imports(fs_code, [
    "com.example.NexoNavBridge",
    "android.content.res.Configuration",
    "androidx.compose.ui.platform.LocalConfiguration"
])

# Arreglar BackHandler de FilesScreen para que normalice el path
fs_code = fs_code.replace(
    'uiState.currentPath.isNotBlank() && uiState.currentPath != "/storage/emulated/0"',
    'uiState.currentPath.trimEnd(\'/\').isNotBlank() && uiState.currentPath.trimEnd(\'/\') != "/storage/emulated/0"'
)

# Colapsar la cabecera superior en modo Landscape dejando una barra ultra-compacta de 1 línea con botón [▼ Controles]
if "isLandscapeExplorer" not in fs_code:
    fs_code = fs_code.replace(
        'var folderFilterQuery by remember { mutableStateOf("") }',
        """var folderFilterQuery by remember { mutableStateOf("") }
  val isLandscapeExplorer = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
  var expandLandscapeHeader by remember { mutableStateOf(false) }"""
    )
    # Envolver el bloque desde el primer elemento dentro del Column principal del Scaffold hasta justo antes del clipboard banner ("// Clipboard banner")
    clip_marker = "// Clipboard banner"
    c_idx = fs_code.find(clip_marker)
    scaf_idx = fs_code.find("Scaffold(")
    if c_idx != -1 and scaf_idx != -1:
        # Buscar el Column( del contenido del Scaffold
        col_m = re.search(r"Column\s*\([^\)]*padding\s*\(\s*innerPadding\s*\)[^\)]*\)\s*\{", fs_code[scaf_idx:c_idx], re.S)
        if not col_m:
            col_m = re.search(r"innerPadding\s*->\s*Column\s*\([^\)]*\)\s*\{", fs_code[scaf_idx:c_idx], re.S)
        if col_m:
            header_start = scaf_idx + col_m.end()
            header_body = fs_code[header_start:c_idx]
            compact_landscape = f"""
      if (isLandscapeExplorer) {{
        Row(
          modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {{
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {{
            if (uiState.currentPath.trimEnd('/') != "/storage/emulated/0") {{
              TextButton(onClick = onNavigateUp, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {{
                Text("←", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
              }}
            }}
            Text(
              text = uiState.currentPath.substringAfterLast('/').ifBlank {{ "Almacenamiento" }},
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1
            )
          }}
          TextButton(
            onClick = {{ expandLandscapeHeader = !expandLandscapeHeader }},
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
          ) {{
            Text(
              text = if (expandLandscapeHeader) "▲ Ocultar barra" else "▼ Buscar / Ordenar",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.secondary
            )
          }}
        }}
      }}
      if (!isLandscapeExplorer || expandLandscapeHeader) {{
{header_body}
      }}
"""
            fs_code = fs_code[:header_start] + compact_landscape + fs_code[c_idx:]
            write_f(fs_rel, fs_code)
            report.append("[OK] 6. FilesScreen.kt: Barra superior colapsada a 36.dp en modo horizontal + BackHandler normalizado.")
        else:
            report.append("[AVISO] 6. No se halló el Column de innerPadding en FilesScreen.kt.")
    else:
        report.append("[AVISO] 6. No se halló marcador Clipboard banner en FilesScreen.kt.")

# ------------------------------------------------------------------
# 7. AJUSTES ESTILO MIUI (LIMPIO Y SUBPANTALLAS) + MASTER CODE EN VAULT + QUITAR BOTÓN WEB ROTO
# ------------------------------------------------------------------
st_rel = "ui/screens/SettingsScreen.kt"
st_code = read_f(st_rel)

# A) Quitar botón roto del navegador web dentro de Reset Default Apps
st_code = re.sub(
    r"\s*androidx\.compose\.material3\.OutlinedButton\s*\(\s*onClick\s*=\s*\{\s*NexoNavBridge\.showWebBrowser\s*=\s*true\s*\}[^\}]*\}[^\}]*\}\s*",
    "\n",
    st_code,
    flags=re.S
)

# B) Empezar con todos los paneles cerrados (lista limpia tipo MIUI) y BackHandler que regrese a la lista antes de salir
st_code = st_code.replace('var expandedPanel by remember { mutableStateOf("DISPLAY") }', 'var expandedPanel by remember { mutableStateOf("") }')
st_code = st_code.replace(
    'BackHandler(onBack = onBack)',
    'BackHandler { if (expandedPanel.isNotEmpty()) expandedPanel = "" else onBack() }'
)

# C) Proteger apertura de "VAULT" con Master Code / Biometría (runProtectedAction)
st_code = re.sub(
    r'onToggle\s*=\s*\{\s*expandedPanel\s*=\s*if\s*\(\s*expandedPanel\s*==\s*"VAULT"\s*\)\s*""\s*else\s*"VAULT"\s*\}',
    '''onToggle = {
          if (expandedPanel == "VAULT") {
            expandedPanel = ""
          } else {
            runProtectedAction(
              title = "Ajustes de Bóveda Protegidos",
              subtitle = "Confirma tu Master Code o biometría para modificar la seguridad de la Bóveda"
            ) { expandedPanel = "VAULT" }
          }
        }''',
    st_code
)

# D) Hacer que cada SettingsAccordionCard actúe como fila limpia MIUI cuando expandedPanel=="" y como subpantalla enfocada al abrirse
# Además actualizar versión visual a v1.4.0
st_code = re.sub(r"NERV HUD Explorer v[0-9\.]+", "NERV HUD Explorer v1.4.0", st_code)
write_f(st_rel, st_code)

# Rediseñar SettingsAccordionCard (en SettingsSubScreens.kt o SettingsScreen.kt) al estilo MIUI limpio
acc_patched = False
for cand in ["ui/screens/SettingsSubScreens.kt", "ui/screens/SettingsScreen.kt"]:
    c_txt = read_f(cand)
    m_acc = re.search(r"@Composable\s+(?:internal\s+)?fun\s+SettingsAccordionCard\s*\(", c_txt)
    if m_acc:
        p_open = c_txt.find("(", m_acc.start())
        p_end = find_balanced(c_txt, p_open, "(", ")")
        b_open = c_txt.find("{", p_end)
        b_end = find_balanced(c_txt, b_open, "{", "}")
        sig = c_txt[m_acc.start():b_open]
        if b_end != -1:
            # Extraer nombre del parámetro de contenido (suele ser content)
            new_acc_body = """{
  val isVaultRow = title.contains("VAULT", ignoreCase = true) || title.contains("BÓVEDA", ignoreCase = true)
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onToggle)
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .background(
            color = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            shape = RoundedCornerShape(10.dp),
          ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (expanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
      }
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
        )
        if (subtitle.isNotBlank()) {
          Text(
            text = if (isVaultRow && !expanded) "🔒 Master Code · $subtitle" else subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }
      }
      Text(
        text = if (expanded) "⌄" else "›",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp),
      )
    }
    AnimatedVisibility(visible = expanded) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
          .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
          .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
      )
    }
    HorizontalDivider(
      modifier = Modifier.padding(start = 68.dp, end = 16.dp),
      color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
    )
  }
}"""
            c_txt = ensure_imports(c_txt, [
                "androidx.compose.foundation.layout.Box",
                "androidx.compose.animation.AnimatedVisibility",
                "androidx.compose.material3.HorizontalDivider"
            ])
            # Recalcular posición tras imports
            m_acc2 = re.search(r"@Composable\s+(?:internal\s+)?fun\s+SettingsAccordionCard\s*\(", c_txt)
            p_open2 = c_txt.find("(", m_acc2.start())
            p_end2 = find_balanced(c_txt, p_open2, "(", ")")
            b_open2 = c_txt.find("{", p_end2)
            b_end2 = find_balanced(c_txt, b_open2, "{", "}")
            c_txt = c_txt[:b_open2] + new_acc_body + c_txt[b_end2:]
            write_f(cand, c_txt)
            acc_patched = True
            report.append(f"[OK] 7. SettingsScreen + {os.path.basename(cand)}: Diseño limpio estilo MIUI + candado Master Code en Vault + botón Web roto eliminado.")
            break
if not acc_patched:
    report.append("[AVISO] 7. SettingsScreen actualizado pero no se encontró SettingsAccordionCard.")

# ------------------------------------------------------------------
# 8. ACTUALIZADOR OTA INTELIGENTE (Compara versión + salta directo a la más reciente)
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

# Localizar la función que contiene "val downloadId = dm.enqueue(req)"
dl_idx = ota_code.find("val downloadId = dm.enqueue(req)")
if dl_idx != -1:
    # Buscar la declaración `fun ...(` anterior a dl_idx
    fun_matches = list(re.finditer(r"fun\s+(\w+)\s*\(([^\)]*)\)\s*\{", ota_code[:dl_idx]))
    if fun_matches:
        fm = fun_matches[-1]
        fn_sig = fm.group(0)
        b_open = ota_code.find("{", fm.start())
        b_end = find_balanced(ota_code, b_open)
        smart_ota_body = """{
        onStatus("Consultando la última versión en GitHub...")
        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val installedVer = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.4.0"
                } catch (_: Throwable) { "1.4.0" }

                fun parseVer(v: String): List<Int> =
                    v.trim().removePrefix("v").removePrefix("V")
                        .split(".", "-", "_")
                        .mapNotNull { it.toIntOrNull() }
                        .let { if (it.size >= 3) it.take(3) else it + List(3 - it.size) { 0 } }

                fun isRemoteNewer(remote: String, local: String): Boolean {
                    val r = parseVer(remote)
                    val l = parseVer(local)
                    for (i in 0..2) {
                        if (r[i] != l[i]) return r[i] > l[i]
                    }
                    return false
                }

                val endpoints = listOf(
                    "https://api.github.com/repos/romanje91-glitch/NexoFiles-/releases/latest",
                    "https://api.github.com/repos/romanje91-glitch/NexoFiles/releases/latest"
                )
                var latestTag = ""
                var apkUrl = ""
                for (ep in endpoints) {
                    runCatching {
                        val conn = (URL(ep).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 8000
                            readTimeout = 8000
                            setRequestProperty("Accept", "application/vnd.github+json")
                            setRequestProperty("User-Agent", "NexoFiles-OTA")
                        }
                        if (conn.responseCode == 200) {
                            val body = conn.inputStream.bufferedReader().use { it.readText() }
                            val json = JSONObject(body)
                            val tag = json.optString("tag_name", "")
                            val assets = json.optJSONArray("assets")
                            var foundUrl = ""
                            if (assets != null) {
                                for (i in 0 until assets.length()) {
                                    val a = assets.getJSONObject(i)
                                    val u = a.optString("browser_download_url", "")
                                    if (u.endsWith(".apk", ignoreCase = true)) {
                                        foundUrl = u
                                        break
                                    }
                                }
                            }
                            if (tag.isNotBlank() && foundUrl.isNotBlank()) {
                                latestTag = tag
                                apkUrl = foundUrl
                            }
                        }
                        conn.disconnect()
                    }
                    if (latestTag.isNotBlank()) break
                }

                if (latestTag.isBlank() || apkUrl.isBlank()) {
                    mainHandler.post { onStatus("No se pudo verificar el servidor OTA. Intenta de nuevo.") }
                    return@Thread
                }

                if (!isRemoteNewer(latestTag, installedVer)) {
                    mainHandler.post {
                        onStatus("✓ Ya estás en la versión más reciente (v$installedVer). No es necesario descargar.")
                    }
                    return@Thread
                }

                mainHandler.post {
                    try {
                        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                        val outFile = File(outDir, "NexoFiles_${latestTag}.apk")
                        if (outFile.exists()) outFile.delete()

                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        val req = DownloadManager.Request(Uri.parse(apkUrl))
                            .setTitle("NexoFiles $latestTag")
                            .setDescription("Actualizando directamente a la versión más reciente ($latestTag)")
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationUri(Uri.fromFile(outFile))

                        val downloadId = dm.enqueue(req)
                        onStatus("Nueva versión $latestTag detectada (actual: v$installedVer). Descargando...")

                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(ctx: Context?, intent: Intent?) {
                                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                                if (id == downloadId) {
                                    try { context.unregisterReceiver(this) } catch (_: Throwable) {}
                                    if (outFile.exists() && outFile.length() > 100_000L) {
                                        onStatus("Descarga completa ($latestTag). Abriendo instalador de Android...")
                                        installApk(context, outFile)
                                    } else {
                                        onStatus("Error al verificar el paquete $latestTag descargado.")
                                    }
                                }
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
                        } else {
                            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
                        }
                    } catch (e: Throwable) {
                        onStatus("Error iniciando descarga OTA: ${e.localizedMessage}")
                    }
                }
            } catch (e: Throwable) {
                mainHandler.post { onStatus("Error verificando OTA: ${e.localizedMessage}") }
            }
        }.start()
    }"""
        ota_code = ota_code[:b_open] + smart_ota_body + ota_code[b_end:]
        # Corregir el contador engañoso maxOf(cloudCount, localCount, ...) para que muestre el conteo real de Drive + cloudOnly
        ota_code = ota_code.replace(
            "val totalCloud = maxOf(cloudCount, localCount, NexoSecureVaultManager.cloudOnlyItems.size)",
            "val totalCloud = maxOf(cloudCount, com.example.NexoVaultExtraStore.cloudOnlyList.size)"
        )
        write_f(ota_rel, ota_code)
        report.append("[OK] 8. NexoVaultAndComicAdvanced.kt: OTA inteligente (solo descarga si hay versión mayor y salta directo a la última).")
    else:
        report.append("[ERROR] 8. No se encontró firma de función OTA.")
else:
    report.append("[ERROR] 8. No se encontró DownloadManager en NexoVaultAndComicAdvanced.kt.")

# ------------------------------------------------------------------
# 9. BÓVEDA (ToolsAndNetworkViewModel.kt + VaultScreen.kt):
#    Passphrase automático + Fix 401 amigable + Thumbnails + Carpetas + Cloud-Only Delete
# ------------------------------------------------------------------
tn_rel = "ui/viewmodel/ToolsAndNetworkViewModel.kt"
tn_code = read_f(tn_rel)

# A) Passphrase automático (no exigir escribir 4 caracteres si está vacío)
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
# Si la función restore usa passphrase, aplicar default automático también
tn_code = re.sub(
    r"fun\s+(restore\w*DriveBackup\w*)\s*\(([^,]+),\s*passphrase:\s*String\)\s*\{",
    r'fun \1(\2, rawPassphrase: String) {\n    val passphrase = rawPassphrase.trim().ifBlank { effectivePassphrase }',
    tn_code
)
# Mensaje claro en lugar de JSON 401 crudo cuando caduca el token de Google Drive
tn_code = tn_code.replace(
    'driveStatusMessage = "Error Drive: ${err.localizedMessage}",',
    'driveStatusMessage = if ((err.localizedMessage ?: "").contains("401") || (err.localizedMessage ?: "").contains("UNAUTHENTICATED")) "Sesión de Google Drive expirada. Abre la pestaña Google Drive y toca Reconectar para renovar el permiso." else "Error Drive: ${err.localizedMessage}",'
)
write_f(tn_rel, tn_code)
report.append("[OK] 9a. ToolsAndNetworkViewModel.kt: Passphrase automático (sin pedir clave) + manejo limpio de token 401.")

# B) VaultScreen.kt: Thumbnails en RAM, Carpetas protegidas (+ CARPETA), Eliminar solo local -> Cloud Placeholder
vs_rel = "ui/screens/VaultScreen.kt"
vs_code = read_f(vs_rel)
vs_code = ensure_imports(vs_code, [
    "com.example.NexoVaultExtraStore",
    "com.example.NexoCloudPlaceholderItem",
    "androidx.compose.foundation.Image",
    "androidx.compose.foundation.clickable",
    "androidx.compose.ui.draw.clip",
    "androidx.compose.ui.graphics.ImageBitmap",
    "androidx.compose.ui.layout.ContentScale"
])

# Inicializar NexoVaultExtraStore y estados de Carpetas + Cloud Placeholder
if "NexoVaultExtraStore.init(context)" not in vs_code:
    vs_code = vs_code.replace(
        "var showDriveBackupModal by remember { mutableStateOf(false) }",
        """LaunchedEffect(Unit) { NexoVaultExtraStore.init(context) }
  var showDriveBackupModal by remember { mutableStateOf(false) }
  var showNewVaultFolderDialog by remember { mutableStateOf(false) }
  var newVaultFolderName by remember { mutableStateOf("") }
  var pendingCloudDownloadItem by remember { mutableStateOf<NexoCloudPlaceholderItem?>(null) }
  var pendingDeleteChoiceItem by remember { mutableStateOf<VaultFileItem?>(null) }"""
    )
    # Limpiar thumbnails de RAM al bloquear la Bóveda
    vs_code = vs_code.replace("onLockVault()", "{ NexoVaultExtraStore.clearThumbnails(); onLockVault() }")

    # Hacer que el botón BACK UP SELECTED no requiera escribir passphrase (pre-rellenar o usar default automático)
    vs_code = re.sub(
        r'var\s+(\w*pass\w*)\s+by\s+remember[^{]*\{\s*mutableStateOf\s*\(\s*""\s*\)\s*\}',
        r'var \1 by remember { mutableStateOf("NEXO_HERMETIC_AUTO_KEY_2026") }',
        vs_code,
        flags=re.I
    )

    # Reemplazar el icono de candado estático de cada VaultFileItem por Thumbnail real (cuando es imagen)
    # Buscar dentro del bloque items(state.items...) el Icon de Lock o IconContainerBadge del elemento
    thumb_box_snippet = """val itemThumb by produceState<ImageBitmap?>(initialValue = null, key1 = item.id) {
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
    # Insertar antes de IconContainerBadge(icon = Icons.Rounded.Lock o NexoIcons.Lock dentro de items
    lock_badge = re.search(r"IconContainerBadge\s*\(\s*icon\s*=\s*(?:Icons\.Rounded\.Lock|NexoIcons\.Lock)[^\)]*\)", vs_code)
    if lock_badge:
        vs_code = ensure_imports(vs_code, ["androidx.compose.runtime.produceState"])
        vs_code = vs_code[:lock_badge.start()] + thumb_box_snippet + "\n            " + lock_badge.group(0) + "\n          }" + vs_code[lock_badge.end():]
        report.append("[OK] 9b. VaultScreen.kt: Thumbnails en RAM activados para imágenes en la Bóveda.")

    # Insertar barra de Carpetas aseguradas + elementos Solo-Nube en la lista de VaultScreen
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
        # Filtrar state.items según activeFolder
        filtered_items = 'items(state.items.filter { NexoVaultExtraStore.activeFolder == null || NexoVaultExtraStore.itemFolderMap[it.id] == NexoVaultExtraStore.activeFolder }, key = { it.id }) {'
        vs_code = vs_code[:items_m.start()] + vault_folders_and_cloud + "\n      " + filtered_items + vs_code[items_m.end():]
        report.append("[OK] 9c. VaultScreen.kt: Carpetas aseguradas (+ CARPETA) y archivos Solo-Nube integrados.")

    # Interceptar onDeleteVaultItem(item) para preguntar: Eliminar solo del teléfono (dejar en nube) vs Definitivo
    vs_code = re.sub(
        r"onClick\s*=\s*\{\s*onDeleteVaultItem\s*\(\s*(\w+)\s*\)\s*\}",
        r"onClick = { pendingDeleteChoiceItem = \1 }",
        vs_code
    )

    # Añadir los 3 diálogos al final de VaultScreen (Nueva Carpeta, Confirmar Eliminación Local vs Total, Descargar de la Nube)
    last_brace = vs_code.rfind("}")
    vault_dialogs = """
  if (showNewVaultFolderDialog) {
    AlertDialog(
      onDismissRequest = { showNewVaultFolderDialog = false },
      title = { Text("Nueva carpeta segura en Bóveda") },
      text = {
        OutlinedTextField(
          value = newVaultFolderName,
          onValueChange = { newVaultFolderName = it },
          label = { Text("Nombre de la carpeta") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        Button(onClick = {
          NexoVaultExtraStore.createFolder(context, newVaultFolderName)
          newVaultFolderName = ""
          showNewVaultFolderDialog = false
        }) { Text("Crear") }
      },
      dismissButton = {
        TextButton(onClick = { showNewVaultFolderDialog = false }) { Text("Cancelar") }
      }
    )
  }

  pendingDeleteChoiceItem?.let { targetItem ->
    AlertDialog(
      onDismissRequest = { pendingDeleteChoiceItem = null },
      title = { Text("Eliminar archivo de la Bóveda") },
      text = { Text("¿Deseas eliminar «${targetItem.originalName}» solo del teléfono (manteniendo el acceso para descargarlo de la nube cuando lo abras) o eliminarlo por completo?") },
      confirmButton = {
        Button(onClick = {
          NexoVaultExtraStore.addCloudOnlyPlaceholder(context, targetItem)
          onDeleteVaultItem(targetItem)
          pendingDeleteChoiceItem = null
        }) { Text("Solo del teléfono (☁️)") }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          TextButton(onClick = {
            onDeleteVaultItem(targetItem)
            pendingDeleteChoiceItem = null
          }) { Text("Eliminar todo") }
          TextButton(onClick = { pendingDeleteChoiceItem = null }) { Text("Cancelar") }
        }
      }
    )
  }

  pendingCloudDownloadItem?.let { cloudItem ->
    AlertDialog(
      onDismissRequest = { pendingCloudDownloadItem = null },
      title = { Text("Archivo almacenado en la nube") },
      text = { Text("«${cloudItem.originalName}» fue eliminado localmente para ahorrar espacio. ¿Deseas descargarlo y descifrarlo desde Google Drive ahora?") },
      confirmButton = {
        Button(onClick = {
          val match = state.driveBackups.firstOrNull {
            it.originalName.equals(cloudItem.originalName, ignoreCase = true) ||
            it.driveFile.name.startsWith(cloudItem.originalName, ignoreCase = true)
          }
          if (match != null) {
            onRestoreDriveBackup(match, "NEXO_HERMETIC_AUTO_KEY_2026")
            NexoVaultExtraStore.removeCloudOnlyPlaceholder(context, cloudItem.originalName)
          } else {
            onRefreshDriveBackups()
            showDriveBackupModal = true
            Toast.makeText(context, "Actualizando lista de respaldos en Google Drive...", Toast.LENGTH_SHORT).show()
          }
          pendingCloudDownloadItem = null
        }) { Text("Descargar de la nube") }
      },
      dismissButton = {
        TextButton(onClick = { pendingCloudDownloadItem = null }) { Text("Cancelar") }
      }
    )
  }
"""
    # Insertar antes del cierre final de fun VaultScreen(...)
    # Buscar el cierre real de la función VaultScreen
    vs_fun = re.search(r"@Composable\s+fun\s+VaultScreen\s*\(", vs_code)
    if vs_fun:
        vp_open = vs_code.find("(", vs_fun.start())
        vp_end = find_balanced(vs_code, vp_open, "(", ")")
        vb_open = vs_code.find("{", vp_end)
        vb_end = find_balanced(vs_code, vb_open, "{", "}")
        if vb_end != -1:
            vs_code = vs_code[:vb_end-1] + vault_dialogs + "\n}" + vs_code[vb_end:]
            report.append("[OK] 9d. VaultScreen.kt: Diálogos de Carpeta Segura, Eliminar Solo Local y Descargar de la Nube listos.")

    write_f(vs_rel, vs_code)

# ------------------------------------------------------------------
# 10. ACTUALIZAR VERSIÓN A v1.4.0 (versionCode = 5)
# ------------------------------------------------------------------
for gp in ["app/build.gradle.kts", "app/build.gradle"]:
    if os.path.exists(gp):
        with open(gp, "r", encoding="utf-8") as fp:
            g = fp.read()
        g = re.sub(r"versionCode\s*=?\s*\d+", "versionCode = 5", g)
        g = re.sub(r'versionName\s*=?\s*"[^"]+"', 'versionName = "1.4.0"', g)
        with open(gp, "w", encoding="utf-8") as fp:
            fp.write(g)
        report.append(f"[OK] 10. {gp} actualizado a versionCode = 5, versionName = 1.4.0.")

print("\n=== RESUMEN DE VERIFICACIÓN (PRE-COMPILACIÓN) ===")
for r in report:
    print(" ", r)
print("=================================================")
