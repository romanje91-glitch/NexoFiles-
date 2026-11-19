package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// ------------------------------------------------------------------
// SEGURIDAD MASTER PIN + PASSPHRASE ÚNICO + GOOGLE AUTHENTICATOR TOTP
// ------------------------------------------------------------------
object NexoVaultSecurityStore {
    private const val PREFS = "nexo_vault_security_v150"
    private const val KEY_PIN_HASH = "master_pin_sha256"
    private const val KEY_PASSPHRASE = "vault_backup_passphrase"
    private const val KEY_TOTP_SECRET = "google_auth_totp_secret"
    private const val KEY_TOTP_VERIFIED = "google_auth_totp_verified"
    private const val DEFAULT_PASS = "NEXO_HERMETIC_AUTO_KEY_2026"
    private const val BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private fun sha256(raw: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hasLocalPin(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PIN_HASH, null).isNullOrBlank()

    fun savePinAndSyncPassphrase(context: Context, rawPin: String) {
        val clean = rawPin.trim()
        if (clean.length < 4) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PIN_HASH, sha256(clean))
            .putString(KEY_PASSPHRASE, clean)
            .apply()
    }

    fun verifyPin(context: Context, candidate: String, fallbackVerify: (String) -> Boolean): Boolean {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PIN_HASH, null)
        if (!stored.isNullOrBlank()) {
            return stored == sha256(candidate.trim())
        }
        val ok = fallbackVerify(candidate)
        if (ok && candidate.trim().length >= 4) {
            savePinAndSyncPassphrase(context, candidate.trim())
        }
        return ok
    }

    fun getBackupPassphrase(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PASSPHRASE, null)
        return saved?.takeIf { it.length >= 4 } ?: DEFAULT_PASS
    }

    fun saveCustomPassphrase(context: Context, pass: String) {
        if (pass.trim().length >= 4) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_PASSPHRASE, pass.trim())
                .apply()
        }
    }

    fun getOrCreateTotpSecret(context: Context): String {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.getString(KEY_TOTP_SECRET, null)?.takeIf { it.length >= 16 }?.let { return it }
        val rng = SecureRandom()
        val sb = StringBuilder(20)
        repeat(20) { sb.append(BASE32_CHARS[rng.nextInt(BASE32_CHARS.length)]) }
        val secret = sb.toString()
        sp.edit().putString(KEY_TOTP_SECRET, secret).apply()
        return secret
    }

    fun isTotpVerified(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TOTP_VERIFIED, false)

    fun markTotpVerified(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_TOTP_VERIFIED, true)
            .apply()
    }

    private fun base32Decode(encoded: String): ByteArray {
        val clean = encoded.uppercase().replace("=", "").replace(" ", "")
        val out = ArrayList<Byte>()
        var buffer = 0
        var bitsLeft = 0
        for (c in clean) {
            val v = BASE32_CHARS.indexOf(c)
            if (v < 0) continue
            buffer = (buffer shl 5) or v
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }

    private fun generateTotpAtStep(secretBase32: String, step: Long): String {
        val key = base32Decode(secretBase32)
        val data = ByteBuffer.allocate(8).putLong(step).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        val otp = binary % 1_000_000
        return "%06d".format(otp)
    }

    fun verifyTotpCode(context: Context, code: String): Boolean {
        val clean = code.trim()
        if (clean.length != 6 || clean.any { !it.isDigit() }) return false
        val secret = getOrCreateTotpSecret(context)
        val currentStep = System.currentTimeMillis() / 1000L / 30L
        for (delta in -1L..1L) {
            if (generateTotpAtStep(secret, currentStep + delta) == clean) return true
        }
        return false
    }

    fun buildOtpAuthUri(context: Context, accountEmail: String): String {
        val email = accountEmail.ifBlank { "usuario@nexofiles" }
        val secret = getOrCreateTotpSecret(context)
        return "otpauth://totp/NexoFiles:${Uri.encode(email)}?secret=$secret&issuer=NexoFiles&digits=6&period=30"
    }
}

@Composable
fun NexoQrBitmapImage(content: String, sizePx: Int = 460, modifier: Modifier = Modifier) {
    val bmp = remember(content) {
        runCatching {
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    b.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
                }
            }
            b.asImageBitmap()
        }.getOrNull()
    }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = "QR Google Authenticator",
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .padding(8.dp)
        )
    }
}

@Composable
fun NexoAuthenticatorSetupDialog(
    accountEmail: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val secret = remember { NexoVaultSecurityStore.getOrCreateTotpSecret(context) }
    val uri = remember(accountEmail) { NexoVaultSecurityStore.buildOtpAuthUri(context, accountEmail) }
    var testCode by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Google Authenticator (2FA)") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Cuenta vinculada: ${accountEmail.ifBlank { "Conecta Google Drive primero" }}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                NexoQrBitmapImage(content = uri, modifier = Modifier.size(185.dp))
                Text(
                    text = "Clave manual: $secret",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("NexoFiles TOTP", secret))
                        Toast.makeText(context, "Clave copiada · Agrégala en Google Authenticator", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("COPIAR CLAVE PARA AUTHENTICATOR") }

                OutlinedTextField(
                    value = testCode,
                    onValueChange = { if (it.length <= 6) testCode = it.filter { c -> c.isDigit() } },
                    label = { Text("Código de 6 dígitos para confirmar") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                statusMsg?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (NexoVaultSecurityStore.verifyTotpCode(context, testCode)) {
                    NexoVaultSecurityStore.markTotpVerified(context)
                    Toast.makeText(context, "✓ Google Authenticator vinculado correctamente", Toast.LENGTH_LONG).show()
                    onDismiss()
                } else {
                    statusMsg = "Código incorrecto. Verifica la hora y el código de 6 dígitos."
                }
            }) { Text("Verificar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun NexoMasterPinChangeDialog(
    hasExistingPin: Boolean,
    linkedEmail: String,
    onVerifyExistingPin: (String) -> Boolean,
    onSaveNewPin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var recoveryMode by remember { mutableStateOf(false) }
    var oldPin by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when {
                    !hasExistingPin -> "Configurar Master PIN"
                    recoveryMode -> "Recuperar Master PIN (2FA)"
                    else -> "Cambiar Master PIN"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Este Master PIN protege los ajustes de Bóveda y se usa automáticamente como Passphrase para el respaldo en Google Drive.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (hasExistingPin && !recoveryMode) {
                    OutlinedTextField(
                        value = oldPin,
                        onValueChange = { oldPin = it.filter { c -> c.isDigit() }.take(12) },
                        label = { Text("Master PIN actual (obligatorio)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        onClick = {
                            recoveryMode = true
                            errorMsg = null
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("¿Olvidaste tu PIN? Validar con Google Authenticator", fontSize = 12.sp)
                    }
                }

                if (hasExistingPin && recoveryMode) {
                    Text(
                        text = "Cuenta Google vinculada: ${linkedEmail.ifBlank { "Sin cuenta conectada en Drive" }}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = totpCode,
                        onValueChange = { totpCode = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("Código de 6 dígitos (Google Authenticator)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!NexoVaultSecurityStore.isTotpVerified(context)) {
                        Text(
                            text = "Vincula Google Authenticator desde '3. Vault Security & Backup' antes de perder el PIN.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    TextButton(
                        onClick = { recoveryMode = false; errorMsg = null },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("← Volver a ingresar PIN actual", fontSize = 12.sp)
                    }
                }

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter { c -> c.isDigit() }.take(12) },
                    label = { Text("Nuevo Master PIN (mín. 4 dígitos)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter { c -> c.isDigit() }.take(12) },
                    label = { Text("Confirmar nuevo Master PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (newPin.length < 4) {
                    errorMsg = "El nuevo PIN debe tener al menos 4 dígitos."
                    return@Button
                }
                if (newPin != confirmPin) {
                    errorMsg = "La confirmación no coincide."
                    return@Button
                }
                if (hasExistingPin) {
                    if (recoveryMode) {
                        if (linkedEmail.isBlank()) {
                            errorMsg = "Debes tener tu cuenta de Google conectada en Drive."
                            return@Button
                        }
                        if (!NexoVaultSecurityStore.verifyTotpCode(context, totpCode)) {
                            errorMsg = "Código de Google Authenticator inválido o expirado."
                            return@Button
                        }
                    } else {
                        if (!NexoVaultSecurityStore.verifyPin(context, oldPin, onVerifyExistingPin)) {
                            errorMsg = "El Master PIN actual es incorrecto."
                            return@Button
                        }
                    }
                }
                NexoVaultSecurityStore.savePinAndSyncPassphrase(context, newPin)
                onSaveNewPin(newPin)
                Toast.makeText(context, "✓ Master PIN y Passphrase de respaldo actualizados", Toast.LENGTH_LONG).show()
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ------------------------------------------------------------------
// CLIENTE BITTORRENT INTEGRADO (libtorrent4j - ESTILO µTORRENT)
// ------------------------------------------------------------------
data class NexoTorrentTask(
    val id: String,
    val name: String,
    val sourceUri: String,
    val saveDir: String,
    val progress: Float = 0f,
    val downloadRateBps: Long = 0L,
    val uploadRateBps: Long = 0L,
    val totalBytes: Long = 0L,
    val doneBytes: Long = 0L,
    val numPeers: Int = 0,
    val numSeeds: Int = 0,
    val statusText: String = "Iniciando...",
    val isPaused: Boolean = false
)

object NexoTorrentEngine {
    val tasks = mutableStateListOf<NexoTorrentTask>()
    var engineStatus by mutableStateOf("Listo")
    private var sessionObj: Any? = null
    private val handleMap = mutableMapOf<String, Any>()
    private var started = false

    fun defaultSaveDir(): File {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: File("/storage/emulated/0/Download")
        return File(base, "NexoTorrents").apply { if (!exists()) mkdirs() }
    }

    private fun ensureStarted(): Boolean {
        if (started && sessionObj != null) return true
        return runCatching {
            val smClass = Class.forName("org.libtorrent4j.SessionManager")
            val sm = smClass.getDeclaredConstructor().newInstance()
            smClass.getMethod("start").invoke(sm)
            sessionObj = sm
            started = true
            engineStatus = "Motor libtorrent DHT activo"
            true
        }.getOrElse {
            engineStatus = "Motor nativo no disponible: ${it.localizedMessage}"
            false
        }
    }

    suspend fun addMagnet(magnetUri: String, onDone: (String) -> Unit) = withContext(Dispatchers.IO) {
        val clean = magnetUri.trim()
        if (!clean.startsWith("magnet:?", ignoreCase = true)) {
            onDone("El enlace debe comenzar con magnet:?")
            return@withContext
        }
        if (!ensureStarted()) {
            onDone(engineStatus)
            return@withContext
        }
        val id = "mag_${System.currentTimeMillis()}"
        val displayName = Uri.parse(clean).getQueryParameter("dn") ?: "Magnet_${id.takeLast(4)}"
        val saveDir = defaultSaveDir()
        withContext(Dispatchers.Main) {
            tasks.add(0, NexoTorrentTask(id, displayName, clean, saveDir.absolutePath, statusText = "Resolviendo metadatos DHT..."))
        }
        runCatching {
            val sm = sessionObj!!
            val smClass = sm.javaClass
            val fetchMethod = smClass.methods.firstOrNull {
                it.name == "fetchMagnet" && it.parameterTypes.size == 3
            } ?: smClass.methods.firstOrNull {
                it.name == "fetchMagnet" && it.parameterTypes.size == 2
            }
            val metaBytes = if (fetchMethod != null && fetchMethod.parameterTypes.size == 3) {
                fetchMethod.invoke(sm, clean, 35, saveDir) as? ByteArray
            } else if (fetchMethod != null) {
                fetchMethod.invoke(sm, clean, 35) as? ByteArray
            } else null

            if (metaBytes == null || metaBytes.isEmpty()) {
                throw IllegalStateException("Sin respuesta de peers/DHT para este magnet")
            }
            val tiClass = Class.forName("org.libtorrent4j.TorrentInfo")
            val ti = tiClass.getConstructor(ByteArray::class.java).newInstance(metaBytes)
            startFromTorrentInfo(id, ti, clean, saveDir)
            onDone("Descarga torrent iniciada: ${tiClass.getMethod("name").invoke(ti)}")
        }.onFailure { err ->
            withContext(Dispatchers.Main) {
                val idx = tasks.indexOfFirst { it.id == id }
                if (idx >= 0) tasks[idx] = tasks[idx].copy(statusText = "Error: ${err.localizedMessage}")
            }
            onDone("Error Magnet: ${err.localizedMessage}")
        }
    }

    suspend fun addTorrentFile(context: Context, uri: Uri, onDone: (String) -> Unit) = withContext(Dispatchers.IO) {
        if (!ensureStarted()) {
            onDone(engineStatus)
            return@withContext
        }
        val id = "tor_${System.currentTimeMillis()}"
        val saveDir = defaultSaveDir()
        val tmpFile = File(context.cacheDir, "$id.torrent")
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { inp ->
                tmpFile.outputStream().use { out -> inp.copyTo(out) }
            } ?: throw IllegalStateException("No se pudo leer el archivo .torrent")

            val tiClass = Class.forName("org.libtorrent4j.TorrentInfo")
            val ti = tiClass.getConstructor(File::class.java).newInstance(tmpFile)
            val tName = tiClass.getMethod("name").invoke(ti)?.toString() ?: "Torrent"
            withContext(Dispatchers.Main) {
                tasks.add(0, NexoTorrentTask(id, tName, uri.toString(), saveDir.absolutePath, statusText = "Conectando trackers..."))
            }
            startFromTorrentInfo(id, ti, uri.toString(), saveDir)
            onDone("Torrent añadido: $tName")
        }.onFailure { err ->
            onDone("Error .torrent: ${err.localizedMessage}")
        }
    }

    private suspend fun startFromTorrentInfo(id: String, ti: Any, sourceUri: String, saveDir: File) {
        val sm = sessionObj ?: return
        val smClass = sm.javaClass
        val tiClass = ti.javaClass
        val dlMethod = smClass.methods.firstOrNull {
            it.name == "download" && it.parameterTypes.size == 2 && it.parameterTypes[0].isAssignableFrom(tiClass) && it.parameterTypes[1] == File::class.java
        } ?: throw IllegalStateException("Método download no disponible")
        dlMethod.invoke(sm, ti, saveDir)

        val infoHash = tiClass.getMethod("infoHash").invoke(ti)
        val findMethod = smClass.methods.firstOrNull { it.name == "find" && it.parameterTypes.size == 1 }
        val handle = findMethod?.invoke(sm, infoHash)
        if (handle != null) handleMap[id] = handle

        val name = tiClass.getMethod("name").invoke(ti)?.toString() ?: "Torrent"
        val total = (tiClass.getMethod("totalSize").invoke(ti) as? Number)?.toLong() ?: 0L
        withContext(Dispatchers.Main) {
            val idx = tasks.indexOfFirst { it.id == id }
            if (idx >= 0) {
                tasks[idx] = tasks[idx].copy(name = name, totalBytes = total, statusText = "Descargando...")
            } else {
                tasks.add(0, NexoTorrentTask(id, name, sourceUri, saveDir.absolutePath, totalBytes = total, statusText = "Descargando..."))
            }
        }
    }

    fun refreshStats() {
        for (i in tasks.indices) {
            val t = tasks[i]
            val h = handleMap[t.id] ?: continue
            runCatching {
                val st = h.javaClass.getMethod("status").invoke(h) ?: return@runCatching
                val stCls = st.javaClass
                val prog = (stCls.getMethod("progress").invoke(st) as? Number)?.toFloat() ?: t.progress
                val dRate = (stCls.getMethod("downloadRate").invoke(st) as? Number)?.toLong() ?: 0L
                val uRate = (stCls.getMethod("uploadRate").invoke(st) as? Number)?.toLong() ?: 0L
                val done = (stCls.getMethod("totalDone").invoke(st) as? Number)?.toLong() ?: t.doneBytes
                val wanted = (stCls.getMethod("totalWanted").invoke(st) as? Number)?.toLong() ?: t.totalBytes
                val peers = (stCls.getMethod("numPeers").invoke(st) as? Number)?.toInt() ?: 0
                val seeds = (stCls.getMethod("numSeeds").invoke(st) as? Number)?.toInt() ?: 0
                val stateStr = stCls.getMethod("state").invoke(st)?.toString() ?: "ACTIVE"
                val label = when {
                    t.isPaused -> "Pausado"
                    prog >= 0.999f -> "Completado (Seeding)"
                    else -> stateStr.lowercase().replaceFirstChar { it.uppercase() }
                }
                tasks[i] = t.copy(
                    progress = prog.coerceIn(0f, 1f),
                    downloadRateBps = dRate,
                    uploadRateBps = uRate,
                    doneBytes = done,
                    totalBytes = if (wanted > 0L) wanted else t.totalBytes,
                    numPeers = peers,
                    numSeeds = seeds,
                    statusText = label
                )
            }
        }
    }

    fun togglePause(id: String) {
        val idx = tasks.indexOfFirst { it.id == id }
        if (idx < 0) return
        val t = tasks[idx]
        val h = handleMap[id] ?: return
        runCatching {
            if (t.isPaused) {
                h.javaClass.getMethod("resume").invoke(h)
                tasks[idx] = t.copy(isPaused = false, statusText = "Descargando...")
            } else {
                h.javaClass.getMethod("pause").invoke(h)
                tasks[idx] = t.copy(isPaused = true, statusText = "Pausado")
            }
        }
    }

    suspend fun addLocalTorrentFile(filePath: String, onDone: (String) -> Unit) = withContext(Dispatchers.IO) {
        val f = File(filePath)
        if (!f.exists()) { onDone("Archivo .torrent no encontrado"); return@withContext }
        if (!ensureStarted()) { onDone(engineStatus); return@withContext }
        val id = "tor_${System.currentTimeMillis()}"
        val saveDir = defaultSaveDir()
        runCatching {
            val tiClass = Class.forName("org.libtorrent4j.TorrentInfo")
            val ti = tiClass.getConstructor(File::class.java).newInstance(f)
            val tName = tiClass.getMethod("name").invoke(ti)?.toString() ?: f.nameWithoutExtension
            withContext(Dispatchers.Main) {
                tasks.add(0, NexoTorrentTask(id, tName, filePath, saveDir.absolutePath, statusText = "Conectando trackers..."))
            }
            startFromTorrentInfo(id, ti, filePath, saveDir)
            onDone("Torrent iniciado: $tName")
        }.onFailure { err -> onDone("Error .torrent: ${err.localizedMessage}") }
    }

    fun removeTask(id: String, deleteFiles: Boolean = false) {
        val h = handleMap.remove(id)
        val sm = sessionObj
        if (h != null && sm != null) {
            runCatching {
                val rm = sm.javaClass.methods.firstOrNull { it.name == "remove" && it.parameterTypes.size == 1 }
                rm?.invoke(sm, h)
            }
        }
        val target = tasks.firstOrNull { it.id == id }
        if (deleteFiles && target != null) {
            runCatching { File(target.saveDir, target.name).deleteRecursively() }
        }
        tasks.removeAll { it.id == id }
    }
}

private fun fmtSpeed(bps: Long): String = when {
    bps >= 1024 * 1024 -> "%.1f MB/s".format(bps / (1024f * 1024f))
    bps >= 1024 -> "${bps / 1024} KB/s"
    else -> "$bps B/s"
}

private fun fmtSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

@Composable
fun NexoTorrentManagerDialog(
    initialMagnet: String = "",
    initialTorrentPath: String = "",
    onOpenSaveFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var magnetInput by remember(initialMagnet) { mutableStateOf(initialMagnet) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Todos, 1=Descargando, 2=Completados
    var pendingDeleteTask by remember { mutableStateOf<NexoTorrentTask?>(null) }
    var deleteWithFiles by remember { mutableStateOf(false) }

    LaunchedEffect(initialTorrentPath) {
        if (initialTorrentPath.isNotBlank()) {
            NexoTorrentEngine.addLocalTorrentFile(initialTorrentPath) { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
    val accent = MaterialTheme.colorScheme.primary

    val torrentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                NexoTorrentEngine.addTorrentFile(context, uri) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            NexoTorrentEngine.refreshStats()
            delay(1000L)
        }
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
                    Text("🧲 NEXOTORRENT", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(NexoTorrentEngine.engineStatus + " · Download/NexoTorrents", color = Color.Gray, fontSize = 11.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFFFF6666))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = magnetInput,
                    onValueChange = { magnetInput = it },
                    placeholder = { Text("Pega enlace magnet:?xt=...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp)
                )
                Button(
                    onClick = {
                        val link = magnetInput.trim()
                        if (link.isNotBlank()) {
                            magnetInput = ""
                            scope.launch {
                                NexoTorrentEngine.addMagnet(link) { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) { Text("DESCARGAR", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { torrentPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) { Text("📂 ABRIR ARCHIVO .TORRENT", fontSize = 11.sp) }
                OutlinedButton(
                    onClick = {
                        onOpenSaveFolder(NexoTorrentEngine.defaultSaveDir().absolutePath)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("📁 VER DESCARGAS", fontSize = 11.sp) }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Todos", "Descargando", "Completados").forEachIndexed { idx, label ->
                    val active = selectedTab == idx
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (active) accent.copy(alpha = 0.22f) else Color(0xFF131826),
                        border = BorderStroke(1.dp, if (active) accent else Color(0xFF2B344A)),
                        modifier = Modifier.weight(1f).clickable { selectedTab = idx }
                    ) {
                        Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text(label, color = if (active) accent else Color.White, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
            val visibleTasks = NexoTorrentEngine.tasks.filter {
                when (selectedTab) {
                    1 -> it.progress < 0.999f
                    2 -> it.progress >= 0.999f
                    else -> true
                }
            }
            if (visibleTasks.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Sin torrents activos.\nPega un enlace magnet:? o abre un archivo .torrent.",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleTasks, key = { it.id }) { task ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF131826),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = task.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${(task.progress * 100).toInt()}%",
                                        color = accent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { task.progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = accent,
                                    trackColor = Color(0xFF232B3E)
                                )
                                Text(
                                    text = "${task.statusText} · ↓ ${fmtSpeed(task.downloadRateBps)} · ↑ ${fmtSpeed(task.uploadRateBps)} · Peers: ${task.numPeers} (${task.numSeeds})",
                                    color = Color(0xFFB4BCD0),
                                    fontSize = 11.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${fmtSize(task.doneBytes)} / ${fmtSize(task.totalBytes)}", color = Color.Gray, fontSize = 11.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = { NexoTorrentEngine.togglePause(task.id) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                        ) { Text(if (task.isPaused) "▶ Reanudar" else "⏸ Pausar", fontSize = 11.sp) }
                                        IconButton(onClick = { deleteWithFiles = false; pendingDeleteTask = task }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Quitar", tint = Color(0xFFFF5555))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

            pendingDeleteTask?.let { taskToDel ->
                AlertDialog(
                    onDismissRequest = { pendingDeleteTask = null },
                    title = { Text("¿Eliminar torrent?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(taskToDel.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Eliminar también archivos descargados", fontSize = 12.sp)
                                Switch(checked = deleteWithFiles, onCheckedChange = { deleteWithFiles = it })
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            NexoTorrentEngine.removeTask(taskToDel.id, deleteWithFiles)
                            pendingDeleteTask = null
                        }) { Text("Sí, eliminar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteTask = null }) { Text("Cancelar") }
                    }
                )
            }
    }
}
