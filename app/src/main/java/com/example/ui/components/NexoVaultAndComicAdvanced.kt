package com.example.ui.components

import android.os.Handler
import android.os.Looper
import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

enum class ComicReadingMode(val label: String) {
    WEBTOON("Webtoon (Tira continua)"),
    WEBTOON_GAPS("Tira larga con espacios"),
    RTL("Manga (Derecha a Izquierda)"),
    LTR("Cómic (Izquierda a Derecha)"),
    VERTICAL_PAGED("Paginado Vertical")
}

object NexoComicViewerState {
    var activeComicFile by mutableStateOf<File?>(null)
    fun open(file: File) { activeComicFile = file }
    fun close() { activeComicFile = null }
    fun isSupportedComicOrWebp(file: File): Boolean {
        val n = file.name.lowercase()
        return n.endsWith(".cbz") || n.endsWith(".cbr") || n.endsWith(".webp")
    }
}

@Composable
fun NexoComicViewerHost() {
    val file = NexoComicViewerState.activeComicFile
    if (file != null) {
        NexoAdvancedComicReaderDialog(comicFile = file, onDismiss = { NexoComicViewerState.close() })
    }
}

@Composable
fun NexoAdvancedComicReaderDialog(comicFile: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pages by remember { mutableStateOf<List<File>>(emptyList()) }
    var currentPage by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Ajustes del lector (3 pestañas como la app de referencia)
    var readingMode by remember { mutableStateOf(ComicReadingMode.WEBTOON) }
    var autoWebtoon by remember { mutableStateOf(true) }
    var sidePaddingDp by remember { mutableIntStateOf(0) }
    var bgColorType by remember { mutableIntStateOf(0) } // 0=Negro, 1=Gris, 2=Blanco
    var showPageNumber by remember { mutableStateOf(true) }
    var brightness by remember { mutableFloatStateOf(0f) } // -100f..100f
    var grayscale by remember { mutableStateOf(false) }
    var inverted by remember { mutableStateOf(false) }

    var showHud by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var settingsTab by remember { mutableIntStateOf(0) }
    var showWebtoonToast by remember { mutableStateOf(false) }

    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val listState = rememberLazyListState()

    LaunchedEffect(comicFile.absolutePath) {
        isLoading = true
        errorMsg = null
        withContext(Dispatchers.IO) {
            try {
                val lower = comicFile.name.lowercase()
                val extracted = mutableListOf<File>()
                if (lower.endsWith(".webp") || lower.endsWith(".jpg") || lower.endsWith(".png")) {
                    val parent = comicFile.parentFile
                    val siblings = parent?.listFiles()?.filter {
                        !it.isDirectory && it.name.lowercase().let { n ->
                            n.endsWith(".webp") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                        }
                    }?.sortedBy { it.name.lowercase() } ?: listOf(comicFile)
                    extracted.addAll(siblings)
                    val startIdx = siblings.indexOfFirst { it.absolutePath == comicFile.absolutePath }
                    if (startIdx >= 0) currentPage = startIdx
                } else {
                    val outDir = File(context.cacheDir, "nexo_comic_${comicFile.nameWithoutExtension}").apply {
                        if (exists()) deleteRecursively()
                        mkdirs()
                    }
                    ZipFile(comicFile).use { zip ->
                        zip.entries().asSequence()
                            .filter { !it.isDirectory && it.name.lowercase().let { n -> n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") } }
                            .sortedBy { it.name.lowercase() }
                            .forEachIndexed { idx, entry ->
                                val ext = entry.name.substringAfterLast('.', "jpg")
                                val pageFile = File(outDir, "page_" + idx.toString().padStart(4, '0') + ".$ext")
                                zip.getInputStream(entry).use { input ->
                                    pageFile.outputStream().use { output -> input.copyTo(output) }
                                }
                                extracted.add(pageFile)
                            }
                    }
                }
                pages = extracted
                if (extracted.isEmpty()) {
                    errorMsg = "No se encontraron imágenes (.webp/.jpg/.png)."
                } else if (autoWebtoon) {
                    val firstOpt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(extracted.first().absolutePath, firstOpt)
                    if (firstOpt.outHeight > firstOpt.outWidth * 1.6f) {
                        readingMode = ComicReadingMode.WEBTOON
                        showWebtoonToast = true
                    }
                }
            } catch (_: Throwable) {
                errorMsg = "No se pudo abrir el archivo de cómic/manga."
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(showWebtoonToast) {
        if (showWebtoonToast) {
            delay(2500)
            showWebtoonToast = false
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (readingMode == ComicReadingMode.WEBTOON || readingMode == ComicReadingMode.WEBTOON_GAPS) {
            currentPage = listState.firstVisibleItemIndex
        }
    }

    val bgColor = when (bgColorType) {
        1 -> Color(0xFF22252A)
        2 -> Color(0xFFF5F5F5)
        else -> Color.Black
    }

    val colorFilter = remember(brightness, grayscale, inverted) {
        if (brightness == 0f && !grayscale && !inverted) null
        else {
            val cm = ColorMatrix()
            if (grayscale) cm.setToSaturation(0f)
            if (inverted || brightness != 0f) {
                val mul = if (inverted) -1f else 1f
                val add = (if (inverted) 255f else 0f) + brightness
                val m = ColorMatrix(
                    floatArrayOf(
                        mul, 0f, 0f, 0f, add,
                        0f, mul, 0f, 0f, add,
                        0f, 0f, mul, 0f, add,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.timesAssign(m)
            }
            ColorFilter.colorMatrix(cm)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val themeColor = NexoEvaThemeManager.primaryColor
        val surfaceColor = NexoEvaThemeManager.surfaceColor

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                        if (zoom > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showHud = !showHud },
                        onDoubleTap = {
                            if (zoom > 1.2f) {
                                zoom = 1f; offsetX = 0f; offsetY = 0f
                            } else {
                                zoom = 2.5f
                            }
                        }
                    )
                }
        ) {
            when {
                isLoading -> CircularProgressIndicator(color = themeColor, modifier = Modifier.align(Alignment.Center))
                errorMsg != null -> Text(errorMsg!!, color = Color(0xFFFFB703), fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
                pages.isNotEmpty() -> {
                    val padMod = Modifier.padding(horizontal = sidePaddingDp.dp)
                    if (readingMode == ComicReadingMode.WEBTOON || readingMode == ComicReadingMode.WEBTOON_GAPS) {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(if (readingMode == ComicReadingMode.WEBTOON_GAPS) 10.dp else 0.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .then(padMod)
                                .graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = offsetX, translationY = offsetY)
                        ) {
                            itemsIndexed(pages, key = { idx, f -> "$idx-${f.name}" }) { _, pageFile ->
                                val bmp = remember(pageFile.absolutePath) { BitmapFactory.decodeFile(pageFile.absolutePath) }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.FillWidth,
                                        colorFilter = colorFilter,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    } else if (readingMode == ComicReadingMode.VERTICAL_PAGED) {
                        val pagerState = rememberPagerState(initialPage = currentPage, pageCount = { pages.size })
                        LaunchedEffect(pagerState.currentPage) { currentPage = pagerState.currentPage }
                        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize().then(padMod)) { pageIdx ->
                            val bmp = remember(pages[pageIdx].absolutePath) { BitmapFactory.decodeFile(pages[pageIdx].absolutePath) }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    colorFilter = colorFilter,
                                    modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = offsetX, translationY = offsetY)
                                )
                            }
                        }
                    } else {
                        val pagerState = rememberPagerState(initialPage = currentPage, pageCount = { pages.size })
                        LaunchedEffect(pagerState.currentPage) { currentPage = pagerState.currentPage }
                        HorizontalPager(
                            state = pagerState,
                            reverseLayout = (readingMode == ComicReadingMode.RTL),
                            modifier = Modifier.fillMaxSize().then(padMod)
                        ) { pageIdx ->
                            val bmp = remember(pages[pageIdx].absolutePath) { BitmapFactory.decodeFile(pages[pageIdx].absolutePath) }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    colorFilter = colorFilter,
                                    modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = offsetX, translationY = offsetY)
                                )
                            }
                        }
                    }
                }
            }

            // Aviso flotante "Reading webtoon style" como en tu captura
            AnimatedVisibility(
                visible = showWebtoonToast,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 52.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xDD1C1F26),
                    border = BorderStroke(1.dp, themeColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Reading webtoon style",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Contador inferior discreto "17 / 23"
            if (showPageNumber && pages.isNotEmpty()) {
                Text(
                    text = "${currentPage + 1} / ${pages.size}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .background(Color(0x88000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            // HUD Flotante Superior e Inferior (se muestra/oculta con un toque)
            AnimatedVisibility(
                visible = showHud,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    // Barra superior
                    Surface(color = surfaceColor.copy(alpha = 0.94f), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("← SALIR", color = themeColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(comicFile.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(readingMode.label, color = themeColor, fontSize = 10.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val act = context as? Activity
                                        if (act != null) {
                                            act.requestedOrientation = if (act.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                            else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("ROTAR", color = Color.White, fontSize = 10.sp)
                                }
                                Button(
                                    onClick = { showSettingsSheet = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("⚙ AJUSTES", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // Barra inferior con Seekbar deslizable
                    if (pages.size > 1) {
                        Surface(color = surfaceColor.copy(alpha = 0.94f), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${currentPage + 1}", color = themeColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Slider(
                                        value = currentPage.toFloat(),
                                        onValueChange = { newVal ->
                                            val target = newVal.toInt().coerceIn(0, pages.lastIndex)
                                            currentPage = target
                                            scope.launch { listState.scrollToItem(target) }
                                        },
                                        valueRange = 0f..(pages.lastIndex.coerceAtLeast(1)).toFloat(),
                                        colors = SliderDefaults.colors(thumbColor = themeColor, activeTrackColor = themeColor),
                                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                                    )
                                    Text("${pages.size}", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Panel de Ajustes con las 3 pestañas (Reading mode | General | Custom filter)
            if (showSettingsSheet) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showSettingsSheet = false },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        color = surfaceColor,
                        border = BorderStroke(1.dp, themeColor.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Reading mode", "General", "Custom filter").forEachIndexed { idx, tabTitle ->
                                    val active = settingsTab == idx
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (active) themeColor.copy(alpha = 0.22f) else Color(0xFF151922),
                                        border = BorderStroke(1.dp, if (active) themeColor else Color.Transparent),
                                        modifier = Modifier.weight(1f).clickable { settingsTab = idx }
                                    ) {
                                        Text(
                                            text = tabTitle,
                                            color = if (active) themeColor else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
                                        )
                                    }
                                }
                            }

                            when (settingsTab) {
                                0 -> {
                                    Text("ESTILO DE LECTURA", color = themeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    ComicReadingMode.values().forEach { mode ->
                                        val selected = readingMode == mode
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (selected) themeColor.copy(alpha = 0.18f) else Color(0xFF12161F),
                                            border = BorderStroke(1.dp, if (selected) themeColor else Color(0xFF283044)),
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                readingMode = mode
                                                if (mode == ComicReadingMode.WEBTOON) showWebtoonToast = true
                                            }
                                        ) {
                                            Text(
                                                text = mode.label,
                                                color = if (selected) themeColor else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Side padding (${sidePaddingDp}dp)", color = Color.White, fontSize = 12.sp)
                                        Slider(
                                            value = sidePaddingDp.toFloat(),
                                            onValueChange = { sidePaddingDp = it.toInt() },
                                            valueRange = 0f..48f,
                                            modifier = Modifier.width(160.dp)
                                        )
                                    }
                                }
                                1 -> {
                                    Text("COLOR DE FONDO", color = themeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("Negro", "Gris", "Blanco").forEachIndexed { idx, name ->
                                            OutlinedButton(
                                                onClick = { bgColorType = idx },
                                                border = BorderStroke(1.dp, if (bgColorType == idx) themeColor else Color.Gray),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(name, color = if (bgColorType == idx) themeColor else Color.White, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Mostrar número de página", color = Color.White, fontSize = 12.sp)
                                        Switch(checked = showPageNumber, onCheckedChange = { showPageNumber = it })
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Detectar Webtoon automáticamente", color = Color.White, fontSize = 12.sp)
                                        Switch(checked = autoWebtoon, onCheckedChange = { autoWebtoon = it })
                                    }
                                }
                                2 -> {
                                    Text("FILTROS DE IMAGEN", color = themeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Brillo", color = Color.White, fontSize = 12.sp)
                                        Slider(
                                            value = brightness,
                                            onValueChange = { brightness = it },
                                            valueRange = -100f..100f,
                                            modifier = Modifier.width(180.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Escala de grises (Manga B/N)", color = Color.White, fontSize = 12.sp)
                                        Switch(checked = grayscale, onCheckedChange = { grayscale = it })
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Invertir colores", color = Color.White, fontSize = 12.sp)
                                        Switch(checked = inverted, onCheckedChange = { inverted = it })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 2. BUSCADOR WEB INTEGRADO (ESTILO ES FILE EXPLORER) ---
object NexoWebBrowserState {
    var isOpen by mutableStateOf(false)
    var initialQuery by mutableStateOf("https://www.google.com")
    fun open(queryOrUrl: String = "https://www.google.com") {
        initialQuery = queryOrUrl
        isOpen = true
    }
    fun close() { isOpen = false }
}

@Composable
fun NexoWebBrowserHost() {
    if (!NexoWebBrowserState.isOpen) return
    val themeColor = NexoEvaThemeManager.primaryColor
    val surfaceColor = NexoEvaThemeManager.surfaceColor
    var inputUrl by remember { mutableStateOf(NexoWebBrowserState.initialQuery) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Dialog(onDismissRequest = { NexoWebBrowserState.close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier.fillMaxSize().background(surfaceColor)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextButton(onClick = {
                    if (webViewRef?.canGoBack() == true) webViewRef?.goBack() else NexoWebBrowserState.close()
                }) {
                    Text("←", color = themeColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    singleLine = true,
                    placeholder = { Text("Buscar en Google o escribir URL...", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val q = inputUrl.trim()
                        val target = if (q.startsWith("http://") || q.startsWith("https://")) q
                        else if (q.contains(".") && !q.contains(" ")) "https://$q"
                        else "https://www.google.com/search?q=${Uri.encode(q)}"
                        webViewRef?.loadUrl(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("IR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                TextButton(onClick = { NexoWebBrowserState.close() }) {
                    Text("✕", color = Color(0xFFFF4D6D), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (!url.isNullOrBlank()) inputUrl = url
                            }
                        }
                        webChromeClient = WebChromeClient()
                        loadUrl(NexoWebBrowserState.initialQuery)
                        webViewRef = this
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}

// --- 3. MOTOR OTA DE ACTUALIZACIONES DIRECTAS DESDE LA APP (v1.1.0) ---
object NexoOtaUpdater {
    const val CURRENT_VERSION_NAME = "1.1.0"
    const val CURRENT_VERSION_CODE = 2
    private const val PREFS = "nexo_ota_prefs"
    private const val KEY_URL = "ota_apk_url"
    // URL configurable por el usuario (GitHub Releases / Drive directo / Servidor propio)
    const val DEFAULT_OTA_APK_URL = "https://github.com/romanje91-glitch/NexoFiles/releases/latest/download/NexoFiles.apk"

    fun getApkUrl(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URL, DEFAULT_OTA_APK_URL) ?: DEFAULT_OTA_APK_URL
    }

    fun setApkUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_URL, url.trim()).apply()
    }

    fun downloadAndInstallUpdate(context: Context, customUrl: String? = null, onStatus: (String) -> Unit) {
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
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
            Toast.makeText(context, "Error instalando APK: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}

// --- 4. BÓVEDA SEGURA Y SINCRONIZACIÓN AUTOMÁTICA ---
object NexoSecureVaultManager {
    private const val PREFS = "nexo_secure_vault_prefs"
    private const val KEY_GHOSTS = "secure_ghosts"
    const val INTERNAL_MASTER_KEY = "NEXO_HERMETIC_AUTO_KEY_2026"
    val cloudOnlyItems = mutableStateListOf<CloudOnlyVaultGhost>()
    var autoBackupOnImport by mutableStateOf(true)

    fun load(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        autoBackupOnImport = sp.getBoolean("auto_backup", true)
        val raw = sp.getString(KEY_GHOSTS, "[]") ?: "[]"
        cloudOnlyItems.clear()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                cloudOnlyItems.add(CloudOnlyVaultGhost(o.getString("id"), o.getString("name"), o.optLong("sizeBytes", 0L)))
            }
        } catch (_: Throwable) {}
    }

    private fun save(context: Context) {
        val arr = JSONArray()
        cloudOnlyItems.forEach { g ->
            arr.put(JSONObject().apply {
                put("id", g.id)
                put("name", g.name)
                put("sizeBytes", g.sizeBytes)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_GHOSTS, arr.toString()).apply()
    }

    fun markEncryptedAndHiddenInDrive(context: Context, id: String, name: String, sizeBytes: Long) {
        if (cloudOnlyItems.none { it.id == id || it.name == name }) {
            cloudOnlyItems.add(CloudOnlyVaultGhost(id, name, sizeBytes))
            save(context)
        }
    }

    fun removeGhost(context: Context, idOrName: String) {
        cloudOnlyItems.removeAll { it.id == idOrName || it.name == idOrName }
        save(context)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
    }
}

@Composable
fun NexoSecureVaultSummaryPanel(
    localCount: Int,
    localBytes: Long,
    cloudCount: Int,
    onOpenDriveBackupModal: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(localCount, localBytes, cloudCount) {
        NexoSecureVaultManager.load(context)
    }

    val themeColor = NexoEvaThemeManager.primaryColor
    val surfaceColor = NexoEvaThemeManager.surfaceColor

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, themeColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("BÓVEDA HÍBRIDA (SYNC AUTOMÁTICO)", color = themeColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(
                    text = "AUTO-SYNC DRIVE: ${if (NexoSecureVaultManager.autoBackupOnImport) "ACTIVO" else "PAUSADO"}",
                    color = if (NexoSecureVaultManager.autoBackupOnImport) themeColor else Color(0xFFFFB703),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        NexoSecureVaultManager.autoBackupOnImport = !NexoSecureVaultManager.autoBackupOnImport
                    }
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF1A2130), modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("LOCAL", color = Color(0xFFB0B8C8), fontSize = 10.sp)
                        Text("$localCount archivos (${NexoSecureVaultManager.formatBytes(localBytes)})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1A2130),
                    modifier = Modifier.weight(1f).clickable { onOpenDriveBackupModal() }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("GOOGLE DRIVE (OCULTO)", color = themeColor, fontSize = 10.sp)
                        val totalCloud = maxOf(cloudCount, com.example.NexoVaultExtraStore.cloudOnlyList.size)
                        Text("$totalCloud respaldos", color = themeColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
