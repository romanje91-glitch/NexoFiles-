package com.example.data.repo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import androidx.documentfile.provider.DocumentFile
import com.example.data.db.NexoDao
import com.example.data.db.SenderHistoryEntity
import com.example.data.model.SenderStagedFile
import com.example.util.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class SenderServerState(
  val isServing: Boolean = false,
  val mode: String = "IDLE",
  val serverUrl: String = "",
  val port: Int = 8282,
  val networkLabel: String = "Red Wi-Fi local",
  val stagedFiles: List<SenderStagedFile> = emptyList(),
  val lastEvent: String = "",
)

class SenderRepository(
  private val context: Context,
  private val dao: NexoDao,
) {

  private val scope = CoroutineScope(Dispatchers.IO)
  private var serverSocket: ServerSocket? = null
  private var acceptJob: Job? = null

  private val _state = MutableStateFlow(SenderServerState(networkLabel = detectNetworkLabel()))
  val state: StateFlow<SenderServerState> = _state.asStateFlow()

  fun observeHistory(): Flow<List<SenderHistoryEntity>> = dao.observeSenderHistory()

  suspend fun clearHistory() {
    dao.clearSenderHistory()
  }

  suspend fun stageUris(uris: List<Uri>): List<SenderStagedFile> = withContext(Dispatchers.IO) {
    val stagingDir = File(context.cacheDir, "sender_staging").apply { if (!exists()) mkdirs() }
    val staged = mutableListOf<SenderStagedFile>()
    for (uri in uris) {
      runCatching {
        val doc = DocumentFile.fromSingleUri(context, uri)
        val name = doc?.name ?: "archivo_${System.currentTimeMillis()}"
        val dest = File(stagingDir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
          FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        if (dest.exists()) {
          staged.add(SenderStagedFile(name = dest.name, path = dest.absolutePath, sizeBytes = dest.length()))
        }
      }
    }
    val combined = (_state.value.stagedFiles + staged).distinctBy { it.path }
    _state.value = _state.value.copy(stagedFiles = combined)
    combined
  }

  fun stageLocalPaths(paths: List<String>) {
    val files = paths.mapNotNull { p ->
      val f = File(p)
      if (f.exists() && f.isFile) SenderStagedFile(f.name, f.absolutePath, f.length()) else null
    }
    _state.value = _state.value.copy(
      stagedFiles = (_state.value.stagedFiles + files).distinctBy { it.path },
    )
  }

  fun clearStagedFiles() {
    _state.value = _state.value.copy(stagedFiles = emptyList())
  }

  fun startSenderServer(
    mode: String,
    saveDirPath: String,
    nickname: String,
  ) {
    stopSenderServer()
    val netLabel = detectNetworkLabel()
    acceptJob = scope.launch {
      runCatching {
        val sock = try {
          ServerSocket(8282)
        } catch (_: Exception) {
          ServerSocket(0)
        }
        serverSocket = sock
        val port = sock.localPort
        val ip = detectLocalIpv4Address() ?: "127.0.0.1"
        val url = if (mode == "SHARE_APP") "http://$ip:$port/share" else "http://$ip:$port"
        _state.value = _state.value.copy(
          isServing = true,
          mode = mode,
          serverUrl = url,
          port = port,
          networkLabel = netLabel,
          lastEvent = when (mode) {
            "SEND" -> "Compartiendo ${_state.value.stagedFiles.size} archivo(s) como $nickname"
            "RECEIVE" -> "Esperando archivos entrantes hacia $saveDirPath"
            "SHARE_APP" -> "Servidor web listo en $url"
            else -> "Activo"
          },
        )
        val saveDir = File(saveDirPath).apply { if (!exists()) mkdirs() }
        while (isActive && !sock.isClosed) {
          val client = runCatching { sock.accept() }.getOrNull() ?: break
          launch { handleClient(client, saveDir, nickname) }
        }
      }.onFailure { err ->
        _state.value = _state.value.copy(
          isServing = false,
          lastEvent = "Error Sender: ${err.localizedMessage}",
        )
      }
    }
  }

  fun stopSenderServer() {
    runCatching { serverSocket?.close() }
    serverSocket = null
    acceptJob?.cancel()
    acceptJob = null
    _state.value = _state.value.copy(
      isServing = false,
      mode = "IDLE",
      serverUrl = "",
    )
  }

  fun detectNetworkLabel(): String {
    return runCatching {
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      val active = cm?.activeNetwork
      val caps = cm?.getNetworkCapabilities(active)
      val wifiMgr = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
      @Suppress("DEPRECATION")
      val rawSsid = wifiMgr?.connectionInfo?.ssid?.trim('"')
        ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
      when {
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> {
          if (rawSsid != null) "Wi-Fi: $rawSsid" else "Red Wi-Fi activa (${detectLocalIpv4Address() ?: "LAN"})"
        }
        else -> "Hotspot / Red local (${detectLocalIpv4Address() ?: "Sin IP LAN"})"
      }
    }.getOrDefault("Red Wi-Fi / Hotspot local")
  }

  fun detectLocalIpv4Address(): String? {
    return runCatching {
      val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
      for (intf in interfaces) {
        if (!intf.isUp || intf.isLoopback) continue
        val addrs = intf.inetAddresses
        while (addrs.hasMoreElements()) {
          val addr = addrs.nextElement()
          if (!addr.isLoopbackAddress && addr is Inet4Address) return addr.hostAddress
        }
      }
      null
    }.getOrNull()
  }

  private suspend fun handleClient(socket: Socket, saveDir: File, nickname: String) {
    socket.use { s ->
      val input = BufferedInputStream(s.getInputStream())
      val output = BufferedOutputStream(s.getOutputStream())
      val reqLine = readLine(input) ?: return
      val parts = reqLine.split(" ")
      if (parts.size < 2) return
      val method = parts[0]
      val rawPath = parts[1].substringBefore('?')
      val headers = HashMap<String, String>()
      while (true) {
        val line = readLine(input) ?: break
        if (line.isEmpty()) break
        val idx = line.indexOf(':')
        if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
      }

      val decoded = URLDecoder.decode(rawPath, "UTF-8")
      if (method == "POST" && decoded == "/upload") {
        handleIncomingUpload(input, headers, saveDir, output, nickname)
        return
      }

      when {
        decoded == "/app.apk" -> {
          val apkFile = runCatching { File(context.applicationInfo.sourceDir) }.getOrNull()
          if (apkFile != null && apkFile.exists()) {
            sendFile(output, apkFile, "NexoFiles.apk")
            dao.insertSenderHistory(
              SenderHistoryEntity(
                fileName = "NexoFiles.apk",
                direction = "SENT",
                peerName = s.inetAddress.hostAddress ?: "Dispositivo remoto",
                filePath = apkFile.absolutePath,
                sizeBytes = apkFile.length(),
              )
            )
            _state.value = _state.value.copy(lastEvent = "APK de NexoFiles enviado")
          } else {
            writeHtml(output, 404, "<h2>APK no disponible</h2>")
          }
        }
        decoded.startsWith("/file/") -> {
          val idx = decoded.removePrefix("/file/").toIntOrNull()
          val item = idx?.let { _state.value.stagedFiles.getOrNull(it) }
          val file = item?.let { File(it.path) }
          if (file != null && file.exists()) {
            sendFile(output, file, file.name)
            dao.insertSenderHistory(
              SenderHistoryEntity(
                fileName = file.name,
                direction = "SENT",
                peerName = s.inetAddress.hostAddress ?: "Dispositivo remoto",
                filePath = file.absolutePath,
                sizeBytes = file.length(),
              )
            )
            _state.value = _state.value.copy(lastEvent = "Enviado: ${file.name}")
          } else {
            writeHtml(output, 404, "<h2>Archivo no encontrado</h2>")
          }
        }
        else -> {
          writeHtml(output, 200, buildSenderHtml(nickname))
        }
      }
    }
  }

  private suspend fun handleIncomingUpload(
    input: InputStream,
    headers: Map<String, String>,
    saveDir: File,
    output: BufferedOutputStream,
    nickname: String,
  ) {
    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
    val contentType = headers["content-type"].orEmpty()
    var savedName: String? = null
    if (contentType.contains("multipart/form-data") && contentLength in 1..(100 * 1024 * 1024)) {
      val boundary = contentType.substringAfter("boundary=", "").trim()
      if (boundary.isNotEmpty()) {
        val rawBody = ByteArray(contentLength)
        var readTotal = 0
        while (readTotal < contentLength) {
          val n = input.read(rawBody, readTotal, contentLength - readTotal)
          if (n <= 0) break
          readTotal += n
        }
        val bodyStr = String(rawBody, StandardCharsets.ISO_8859_1)
        val headerEndIdx = bodyStr.indexOf("\r\n\r\n")
        if (headerEndIdx > 0) {
          val partHeaders = bodyStr.substring(0, headerEndIdx)
          val filename = Regex("filename=\"([^\"]+)\"").find(partHeaders)?.groupValues?.getOrNull(1)
            ?.substringAfterLast('/')?.substringAfterLast('\\')
            ?: "recibido_${System.currentTimeMillis()}"
          val contentStart = headerEndIdx + 4
          val endBoundaryMarker = "\r\n--$boundary"
          val contentEnd = bodyStr.lastIndexOf(endBoundaryMarker)
          if (contentEnd > contentStart) {
            if (!saveDir.exists()) saveDir.mkdirs()
            val outFile = File(saveDir, filename)
            FileOutputStream(outFile).use { fos ->
              fos.write(rawBody, contentStart, contentEnd - contentStart)
            }
            savedName = outFile.name
            dao.insertSenderHistory(
              SenderHistoryEntity(
                fileName = outFile.name,
                direction = "RECEIVED",
                peerName = nickname,
                filePath = outFile.absolutePath,
                sizeBytes = outFile.length(),
              )
            )
            _state.value = _state.value.copy(lastEvent = "Recibido: ${outFile.name}")
          }
        }
      }
    }
    val html = if (savedName != null) {
      buildSenderHtml(nickname, notice = "Recibido correctamente: $savedName")
    } else {
      buildSenderHtml(nickname, notice = "No se pudo recibir el archivo")
    }
    writeHtml(output, 200, html)
  }

  private fun sendFile(output: BufferedOutputStream, file: File, downloadName: String) {
    val mime = FormatUtils.getMimeType(downloadName)
    val safeName = downloadName.replace("\"", "_")
    val header = "HTTP/1.1 200 OK\r\n" +
      "Content-Type: $mime\r\n" +
      "Content-Length: ${file.length()}\r\n" +
      "Content-Disposition: attachment; filename=\"$safeName\"\r\n" +
      "Connection: close\r\n\r\n"
    output.write(header.toByteArray(StandardCharsets.UTF_8))
    FileInputStream(file).use { fis ->
      val buf = ByteArray(16 * 1024)
      var n = fis.read(buf)
      while (n != -1) {
        output.write(buf, 0, n)
        n = fis.read(buf)
      }
    }
    output.flush()
  }

  private fun writeHtml(output: BufferedOutputStream, status: Int, html: String) {
    val bytes = html.toByteArray(StandardCharsets.UTF_8)
    val statusText = if (status == 200) "OK" else "Not Found"
    val header = "HTTP/1.1 $status $statusText\r\n" +
      "Content-Type: text/html; charset=utf-8\r\n" +
      "Content-Length: ${bytes.size}\r\n" +
      "Connection: close\r\n\r\n"
    output.write(header.toByteArray(StandardCharsets.UTF_8))
    output.write(bytes)
    output.flush()
  }

  private fun readLine(input: InputStream): String? {
    val baos = ByteArrayOutputStream()
    var prev = -1
    while (true) {
      val b = input.read()
      if (b == -1) return if (baos.size() == 0) null else String(baos.toByteArray(), StandardCharsets.UTF_8)
      if (b == '\n'.code) {
        val bytes = baos.toByteArray()
        val len = if (prev == '\r'.code && bytes.isNotEmpty()) bytes.size - 1 else bytes.size
        return String(bytes, 0, len, StandardCharsets.UTF_8)
      }
      baos.write(b)
      prev = b
    }
  }

  private fun buildSenderHtml(nickname: String, notice: String? = null): String {
    val stagedRows = _state.value.stagedFiles.mapIndexed { idx, file ->
      "<li><span>${file.name.escapeHtml()} (${FormatUtils.formatBytes(file.sizeBytes)})</span>" +
        "<a class='btn' href='/file/$idx'>DESCARGAR</a></li>"
    }.joinToString("")
    val noticeHtml = notice?.let { "<div class='notice'>${it.escapeHtml()}</div>" }.orEmpty()
    return """
      <!DOCTYPE html>
      <html lang="es">
      <head>
        <meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width,initial-scale=1"/>
        <title>NERV // SENDER · ${nickname.escapeHtml()}</title>
        <style>
          body { background:#08060E; color:#F3EFFF; font-family:ui-monospace,Menlo,monospace; margin:0; padding:20px; }
          .card { max-width:680px; margin:0 auto 16px; background:#120E21; border:1px solid #7C3AED; clip-path:polygon(10px 0,100% 0,100% calc(100% - 10px),calc(100% - 10px) 100%,0 100%,0 10px); padding:18px; }
          h1 { margin:0 0 6px; font-size:18px; color:#22C55E; text-transform:uppercase; letter-spacing:1px; }
          .btn { display:inline-block; background:#22C55E; color:#08060E; text-decoration:none; padding:8px 14px; font-weight:800; font-size:12px; border:none; cursor:pointer; text-transform:uppercase; }
          ul { list-style:none; padding:0; margin:12px 0 0; }
          li { display:flex; justify-content:space-between; align-items:center; padding:10px 0; border-bottom:1px solid #332559; gap:10px; }
          .notice { background:#161129; border:1px solid #22C55E; padding:10px 14px; margin-bottom:12px; color:#22C55E; }
        </style>
      </head>
      <body>
        <div class="card">
          <h1>NEXOFILES SENDER // ${nickname.escapeHtml()}</h1>
          <p style="color:#AEA1CF;margin-top:0">TRANSFERENCIA LOCAL DIRECTA SIN CABLES NI INTERNET</p>
          $noticeHtml
          <a class="btn" href="/app.apk">DESCARGAR APK NEXOFILES</a>
        </div>
        <div class="card">
          <h2 style="margin-top:0;font-size:15px;color:#A78BFA">ARCHIVOS COMPARTIDOS (${_state.value.stagedFiles.size})</h2>
          ${if (stagedRows.isEmpty()) "<p style='color:#AEA1CF'>Ningún archivo preparado aún.</p>" else "<ul>$stagedRows</ul>"}
        </div>
        <div class="card">
          <h2 style="margin-top:0;font-size:15px;color:#A78BFA">ENVIAR ARCHIVO A ${nickname.escapeHtml()}</h2>
          <form method="post" action="/upload" enctype="multipart/form-data">
            <input type="file" multiple name="file" required style="color:#AEA1CF;margin-bottom:10px"/><br/>
            <button class="btn" type="submit">SUBIR A NEXOFILES</button>
          </form>
        </div>
      </body>
      </html>
    """.trimIndent()
  }

  private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
