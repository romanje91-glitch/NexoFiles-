package com.example

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
