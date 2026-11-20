package com.example.data.repo

import android.os.Environment
import com.example.util.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class WebServerState(
  val isRunning: Boolean = false,
  val url: String = "",
  val port: Int = 8080,
  val rootPath: String = "/storage/emulated/0",
  val lastEvent: String = "SERVIDOR DETENIDO",
  val transferredCount: Int = 0,
)

class LocalHttpServer {

  private val scope = CoroutineScope(Dispatchers.IO)
  private var serverSocket: ServerSocket? = null
  private var acceptJob: Job? = null

  private val _state = MutableStateFlow(WebServerState())
  val state: StateFlow<WebServerState> = _state.asStateFlow()

  fun start(
    port: Int = 8080,
    rootDirPath: String = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0",
  ) {
    if (_state.value.isRunning) return
    acceptJob = scope.launch {
      runCatching {
        val socket = try {
          ServerSocket(port)
        } catch (_: Exception) {
          ServerSocket(0)
        }
        serverSocket = socket
        val actualPort = socket.localPort
        val ip = detectLocalIpv4Address() ?: "127.0.0.1"
        val url = "http://$ip:$actualPort"
        _state.value = WebServerState(
          isRunning = true,
          url = url,
          port = actualPort,
          rootPath = rootDirPath,
          lastEvent = "ESCUCHANDO EN $url",
          transferredCount = 0,
        )
        while (isActive && !socket.isClosed) {
          val client = runCatching { socket.accept() }.getOrNull() ?: break
          launch { handleClient(client, File(rootDirPath)) }
        }
      }.onFailure { err ->
        _state.value = _state.value.copy(
          isRunning = false,
          lastEvent = "ERROR DE ARRANQUE: ${err.localizedMessage}",
        )
      }
    }
  }

  fun stop() {
    runCatching { serverSocket?.close() }
    serverSocket = null
    acceptJob?.cancel()
    acceptJob = null
    _state.value = _state.value.copy(
      isRunning = false,
      lastEvent = "SERVIDOR DETENIDO",
    )
  }

  fun detectLocalIpv4Address(): String? {
    return runCatching {
      val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
      for (intf in interfaces) {
        if (!intf.isUp || intf.isLoopback) continue
        val addrs = intf.inetAddresses
        while (addrs.hasMoreElements()) {
          val addr = addrs.nextElement()
          if (!addr.isLoopbackAddress && addr is Inet4Address) {
            return addr.hostAddress
          }
        }
      }
      null
    }.getOrNull()
  }

  private fun handleClient(socket: Socket, rootDir: File) {
    socket.use { s ->
      val input = BufferedInputStream(s.getInputStream())
      val output = BufferedOutputStream(s.getOutputStream())
      val requestLine = readLine(input) ?: return
      val parts = requestLine.split(" ")
      if (parts.size < 2) return
      val method = parts[0]
      val rawTarget = parts[1]

      val headers = HashMap<String, String>()
      while (true) {
        val line = readLine(input) ?: break
        if (line.isEmpty()) break
        val idx = line.indexOf(':')
        if (idx > 0) {
          headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
      }

      val pathOnly = rawTarget.substringBefore('?')
      val query = rawTarget.substringAfter('?', "")
      val decodedPath = URLDecoder.decode(pathOnly, "UTF-8")

      if (method == "POST" && decodedPath == "/upload") {
        handleUpload(input, headers, query, rootDir, output)
        return
      }

      val relPath = decodedPath.removePrefix("/browse").removePrefix("/download").trimStart('/')
      val targetFile = if (relPath.isEmpty()) rootDir else File(rootDir, relPath)
      val rootCanonical = rootDir.canonicalPath
      if (!targetFile.canonicalPath.startsWith(rootCanonical)) {
        writeTextResponse(output, 403, "text/plain", "ACCESO DENEGADO")
        return
      }

      if (decodedPath.startsWith("/download") && targetFile.exists() && targetFile.isFile) {
        sendFile(output, targetFile)
        _state.value = _state.value.copy(
          lastEvent = "DESCARGADO EN PC: ${targetFile.name}",
          transferredCount = _state.value.transferredCount + 1,
        )
        return
      }

      if (targetFile.exists() && targetFile.isDirectory) {
        val html = buildDirectoryHtml(targetFile, rootDir)
        writeTextResponse(output, 200, "text/html; charset=utf-8", html)
      } else if (targetFile.exists() && targetFile.isFile) {
        sendFile(output, targetFile)
      } else {
        writeTextResponse(output, 404, "text/html; charset=utf-8", "<h2>ARCHIVO NO ENCONTRADO</h2>")
      }
    }
  }

  private fun handleUpload(
    input: InputStream,
    headers: Map<String, String>,
    query: String,
    rootDir: File,
    output: BufferedOutputStream,
  ) {
    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
    val contentType = headers["content-type"].orEmpty()
    val dirParam = query.split("&").firstOrNull { it.startsWith("dir=") }?.substringAfter("dir=")?.let {
      URLDecoder.decode(it, "UTF-8")
    }.orEmpty().trimStart('/')
    val destDir = if (dirParam.isEmpty()) rootDir else File(rootDir, dirParam)
    if (!destDir.canonicalPath.startsWith(rootDir.canonicalPath)) {
      writeTextResponse(output, 403, "text/plain", "RUTA INVÁLIDA")
      return
    }

    if (contentType.contains("multipart/form-data") && contentLength in 1..(50 * 1024 * 1024)) {
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
            ?: "subido_${System.currentTimeMillis()}"
          val contentStart = headerEndIdx + 4
          val endBoundaryMarker = "\r\n--$boundary"
          val contentEnd = bodyStr.lastIndexOf(endBoundaryMarker)
          if (contentEnd > contentStart) {
            val outFile = File(destDir, filename)
            FileOutputStream(outFile).use { fos ->
              fos.write(rawBody, contentStart, contentEnd - contentStart)
            }
            _state.value = _state.value.copy(
              lastEvent = "SUBIDO DESDE PC: $filename",
              transferredCount = _state.value.transferredCount + 1,
            )
          }
        }
      }
    }

    val redirectTarget = if (dirParam.isEmpty()) "/" else "/browse/${URLEncoder.encode(dirParam, "UTF-8").replace("+", "%20")}"
    val resp = "HTTP/1.1 303 See Other\r\nLocation: $redirectTarget\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
    output.write(resp.toByteArray(StandardCharsets.UTF_8))
    output.flush()
  }

  private fun sendFile(output: BufferedOutputStream, file: File) {
    val mime = FormatUtils.getMimeType(file.name)
    val safeName = file.name.replace("\"", "_")
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

  private fun writeTextResponse(output: BufferedOutputStream, status: Int, contentType: String, body: String) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    val statusText = when (status) {
      200 -> "OK"
      403 -> "Forbidden"
      404 -> "Not Found"
      else -> "Error"
    }
    val header = "HTTP/1.1 $status $statusText\r\n" +
      "Content-Type: $contentType\r\n" +
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
      if (b == -1) {
        return if (baos.size() == 0) null else String(baos.toByteArray(), StandardCharsets.UTF_8)
      }
      if (b == '\n'.code) {
        val bytes = baos.toByteArray()
        val len = if (prev == '\r'.code && bytes.isNotEmpty()) bytes.size - 1 else bytes.size
        return String(bytes, 0, len, StandardCharsets.UTF_8)
      }
      baos.write(b)
      prev = b
    }
  }

  private fun buildDirectoryHtml(dir: File, rootDir: File): String {
    val relPath = dir.canonicalPath.removePrefix(rootDir.canonicalPath).trimStart(File.separatorChar)
    val encodedRel = URLEncoder.encode(relPath, "UTF-8")
    val children = (dir.listFiles()?.toList() ?: emptyList())
      .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })

    val rows = StringBuilder()
    if (relPath.isNotEmpty()) {
      val parentRel = relPath.substringBeforeLast(File.separatorChar, "")
      val parentHref = if (parentRel.isEmpty()) "/" else "/browse/${parentRel.encodeUrlPath()}"
      rows.append("<tr><td colspan='3'><a href='$parentHref'>[..] CARPETA SUPERIOR</a></td></tr>")
    }
    for (child in children) {
      val childRel = if (relPath.isEmpty()) child.name else "$relPath/${child.name}"
      val encodedChild = childRel.encodeUrlPath()
      val href = if (child.isDirectory) "/browse/$encodedChild" else "/download/$encodedChild"
      val sizeLabel = if (child.isDirectory) "DIR" else FormatUtils.formatBytes(child.length())
      val tag = if (child.isDirectory) "<span class='tag dir'>DIR</span>" else "<span class='tag file'>BIN</span>"
      rows.append(
        "<tr><td>$tag <a href='$href'>${child.name.escapeHtml()}</a></td>" +
          "<td class='mono'>$sizeLabel</td>" +
          "<td>${if (child.isDirectory) "<a class='link-btn' href='$href'>ABRIR</a>" else "<a class='cta' href='$href'>DESCARGAR</a>"}</td></tr>"
      )
    }

    return """
      <!DOCTYPE html>
      <html lang="es">
      <head>
        <meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width,initial-scale=1"/>
        <title>NEXOFILES // MAGI HTTP BRIDGE</title>
        <style>
          body { background:#08060E; color:#F3EFFF; font-family:ui-monospace,SFMono-Regular,Menlo,monospace; margin:0; padding:20px; font-variant-numeric:tabular-nums; }
          .wrap { max-width:940px; margin:0 auto; }
          .card { background:#120E21; border:1px solid #7C3AED; clip-path:polygon(12px 0,100% 0,100% calc(100% - 12px),calc(100% - 12px) 100%,0 100%,0 12px); padding:18px; margin-bottom:18px; }
          h1 { margin:0 0 6px; font-size:18px; letter-spacing:1.5px; text-transform:uppercase; color:#22C55E; }
          .sub { color:#F97316; font-size:11px; letter-spacing:1.2px; text-transform:uppercase; margin-bottom:8px; }
          .path { color:#AEA1CF; font-size:12px; border-left:2px solid #22C55E; padding-left:8px; word-break:break-all; }
          table { width:100%; border-collapse:collapse; font-size:13px; }
          th, td { text-align:left; padding:10px 8px; border-bottom:1px solid #332559; }
          th { color:#AEA1CF; font-size:11px; text-transform:uppercase; letter-spacing:1px; }
          a { color:#F3EFFF; text-decoration:none; }
          a:hover { color:#22C55E; }
          .cta { display:inline-block; background:#22C55E; color:#08060E; padding:5px 11px; font-weight:700; font-size:11px; text-transform:uppercase; letter-spacing:0.8px; }
          .link-btn { display:inline-block; background:#161129; color:#A78BFA; border:1px solid #7C3AED; padding:4px 10px; font-weight:700; font-size:11px; text-transform:uppercase; }
          .tag { display:inline-block; font-size:10px; padding:2px 5px; margin-right:6px; font-weight:700; }
          .tag.dir { background:#7C3AED; color:#F3EFFF; }
          .tag.file { background:#161129; color:#22C55E; border:1px solid #22C55E; }
          .mono { color:#AEA1CF; font-size:12px; }
          input[type=file] { color:#AEA1CF; background:#161129; border:1px solid #332559; padding:8px; }
          button { background:#22C55E; color:#08060E; border:none; padding:10px 18px; font-weight:800; text-transform:uppercase; letter-spacing:0.9px; cursor:pointer; }
        </style>
      </head>
      <body>
        <div class="wrap">
          <div class="card">
            <div class="sub">EVA-01 // TELEMETRÍA HTTP WI-FI</div>
            <h1>NEXOFILES · PUENTE WEB LAN</h1>
            <div class="path">/${relPath.escapeHtml()}</div>
            <form style="margin-top:14px;display:flex;gap:10px;flex-wrap:wrap;align-items:center;" method="post" action="/upload?dir=$encodedRel" enctype="multipart/form-data">
              <input type="file" multiple name="file" required />
              <button type="submit">SUBIR AL DISPOSITIVO</button>
            </form>
          </div>
          <div class="card">
            <table>
              <thead><tr><th>OBJETO</th><th>TAMAÑO</th><th>ACCIÓN</th></tr></thead>
              <tbody>$rows</tbody>
            </table>
          </div>
        </div>
      </body>
      </html>
    """.trimIndent()
  }

  private fun String.encodeUrlPath(): String = split("/").joinToString("/") {
    URLEncoder.encode(it, "UTF-8").replace("+", "%20")
  }

  private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
