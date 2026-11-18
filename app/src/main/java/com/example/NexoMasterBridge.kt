package com.example

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

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
    fun getMirrorDir(context: Context): File {
        val extDir = File(context.getExternalFilesDir(null), "NexoVault_Encrypted")
        if (!extDir.exists()) extDir.mkdirs()
        return extDir
    }

    fun ensureMirroredBackups(context: Context, passphrase: String = "nexo_default_sync"): List<File> {
        val mirrorDir = getMirrorDir(context)
        val candidates = listOf(
            File(context.filesDir, "vault"),
            File(context.filesDir, "nexo_vault"),
            File(context.filesDir, "private_vault"),
            File(context.getExternalFilesDir(null), "vault"),
            context.filesDir
        )
        for (vd in candidates) {
            if (vd.exists() && vd.isDirectory) {
                vd.listFiles()?.forEach { src ->
                    if (src.isFile && !src.name.endsWith(".meta") && !src.name.endsWith(".xml") && !src.name.endsWith(".db") && !src.name.endsWith(".txt") && src.length() > 0) {
                        val targetName = if (src.name.endsWith(".nexo_vault")) src.name else "${src.name}.nexo_vault"
                        val target = File(mirrorDir, targetName)
                        if (!target.exists()) {
                            runCatching { encryptFileToMirror(src, target, passphrase) }
                        }
                    }
                }
            }
        }
        return mirrorDir.listFiles()?.filter { it.isFile && it.name.endsWith(".nexo_vault") }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun encryptFileToMirror(source: File, dest: File, passphrase: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val spec = PBEKeySpec(passphrase.ifBlank { "nexo_default_sync" }.toCharArray(), salt, 10000, 256)
        val secret = SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(source.readBytes())
        dest.outputStream().use { out ->
            out.write("NEXOVAULT1".toByteArray())
            out.write(salt)
            out.write(iv)
            out.write(encrypted)
        }
    }
}

@Composable
fun NexoEvaSingleLineSelector(
    themes: List<String>,
    selectedTheme: String,
    accentColor: Color = Color(0xFF00FF66),
    onSelect: (String) -> Unit
) {
    if (themes.isEmpty()) return
    var dropdownExpanded by remember { mutableStateOf(false) }
    val totalVirtual = 6000
    val currentIdx = themes.indexOf(selectedTheme).coerceAtLeast(0)
    val startIdx = remember { (totalVirtual / 2) - ((totalVirtual / 2) % themes.size) + currentIdx }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIdx)
    var userScrolled by remember { mutableStateOf(false) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            userScrolled = true
        } else if (userScrolled) {
            val chosen = themes[listState.firstVisibleItemIndex % themes.size]
            if (chosen != selectedTheme) {
                onSelect(chosen)
            }
            userScrolled = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF10141D))
            .border(1.dp, accentColor.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            LazyRow(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(totalVirtual) { idx ->
                    val item = themes[idx % themes.size]
                    val isSel = item == selectedTheme
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) accentColor.copy(alpha = 0.22f) else Color(0xFF161B26))
                            .border(
                                width = if (isSel) 1.5.dp else 0.5.dp,
                                color = if (isSel) accentColor else Color.Gray.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onSelect(item) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isSel) "● $item" else item,
                            color = if (isSel) accentColor else Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = { dropdownExpanded = true },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Lista de temas",
                    tint = accentColor
                )
            }
        }

        DropdownMenu(
            expanded = dropdownExpanded,
            onDismissRequest = { dropdownExpanded = false },
            modifier = Modifier
                .background(Color(0xFF121622))
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        ) {
            themes.forEach { themeName ->
                val isSel = themeName == selectedTheme
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (isSel) "● $themeName" else themeName,
                            color = if (isSel) accentColor else Color.White,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        dropdownExpanded = false
                        onSelect(themeName)
                    }
                )
            }
        }
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
    val accent = Color(0xFF00FF66)

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
                                placeholder = { Text("URL o búsqueda...", fontSize = 11.sp, color = Color.Gray) }
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
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
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
                                "MangaDex" to "https://mangadex.org",
                                "GitHub Release" to "https://github.com/romanje91-glitch/NexoFiles-/releases"
                            ).forEach { pair ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1A2030))
                                        .border(0.5.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
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
                                    if (!url.isNullOrBlank()) {
                                        urlInput = url
                                    }
                                }
                            }
                            webChromeClient = WebChromeClient()
                            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                                runCatching {
                                    val req = DownloadManager.Request(Uri.parse(url))
                                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                                    req.setTitle(fileName)
                                    req.setDescription("Descargando archivo...")
                                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                                    req.addRequestHeader("User-Agent", userAgent)
                                    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                    dm.enqueue(req)
                                    Toast.makeText(ctx, "Descargando: $fileName", Toast.LENGTH_LONG).show()
                                }.onFailure {
                                    Toast.makeText(ctx, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
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
                        .background(Color.Black.copy(alpha = 0.7f))
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
