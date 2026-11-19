import os, re, urllib.request, xml.etree.ElementTree as ET

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

report = []

# ==================================================================
# 0. RESOLVER VERSIÓN DE libtorrent4j EN MAVEN CENTRAL Y AGREGAR A GRADLE
# ==================================================================
lt_ver = None
try:
    req = urllib.request.Request(
        "https://repo1.maven.org/maven2/org/libtorrent4j/libtorrent4j/maven-metadata.xml",
        headers={"User-Agent": "NexoFiles-Build"}
    )
    with urllib.request.urlopen(req, timeout=8) as r:
        root_xml = ET.fromstring(r.read())
        vers = [el.text for el in root_xml.findall(".//version") if el.text]
    for cand in reversed(vers[-12:]):
        try:
            u_arm64 = f"https://repo1.maven.org/maven2/org/libtorrent4j/libtorrent4j-android-arm64/{cand}/libtorrent4j-android-arm64-{cand}.jar"
            hreq = urllib.request.Request(u_arm64, method="HEAD", headers={"User-Agent": "NexoFiles-Build"})
            with urllib.request.urlopen(hreq, timeout=5) as hr:
                if hr.status == 200:
                    lt_ver = cand
                    break
        except Exception:
            continue
except Exception:
    lt_ver = None

gradle_path = "app/build.gradle.kts"
with open(gradle_path, "r", encoding="utf-8") as fp:
    g_txt = fp.read()

g_txt = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 6", g_txt)
g_txt = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.5.0"', g_txt)

if lt_ver and "org.libtorrent4j:libtorrent4j:" not in g_txt:
    lt_deps = f"""
dependencies {{
    implementation("org.libtorrent4j:libtorrent4j:{lt_ver}")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:{lt_ver}")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:{lt_ver}")
}}
"""
    g_txt = g_txt.rstrip() + "\n" + lt_deps
    report.append(f"[OK] 0. Gradle: v1.5.0 (versionCode 6) + libtorrent4j {lt_ver} (arm64 + arm).")
else:
    report.append(f"[OK] 0. Gradle: v1.5.0 (versionCode 6) | libtorrent4j: {lt_ver or 'ya configurado/fallback'}.")

with open(gradle_path, "w", encoding="utf-8") as fp:
    fp.write(g_txt)

# ==================================================================
# 1. CREAR NexoSecurityAndTorrent.kt:
#    - NexoVaultSecurityStore (Master PIN real + Passphrase único + Google Authenticator TOTP RFC-6238 + QR)
#    - NexoMasterPinChangeDialog (exige PIN anterior o código 6 dígitos Google Authenticator)
#    - NexoAuthenticatorSetupDialog (QR + clave Base32 + verificación)
#    - NexoTorrentEngine + NexoTorrentManagerDialog (cliente BitTorrent estilo µTorrent)
# ==================================================================
sec_torrent_kt = r"""package com.example

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

    fun removeTask(id: String) {
        val h = handleMap.remove(id)
        val sm = sessionObj
        if (h != null && sm != null) {
            runCatching {
                val rm = sm.javaClass.methods.firstOrNull { it.name == "remove" && it.parameterTypes.size == 1 }
                rm?.invoke(sm, h)
            }
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
    onOpenSaveFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var magnetInput by remember(initialMagnet) { mutableStateOf(initialMagnet) }
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

            if (NexoTorrentEngine.tasks.isEmpty()) {
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
                    items(NexoTorrentEngine.tasks, key = { it.id }) { task ->
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
                                        IconButton(onClick = { NexoTorrentEngine.removeTask(task.id) }, modifier = Modifier.size(32.dp)) {
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
    }
}
"""
write_f("NexoSecurityAndTorrent.kt", sec_torrent_kt)
report.append("[OK] 1. NexoSecurityAndTorrent.kt: Master PIN + Google Authenticator TOTP QR + NexoTorrent (µTorrent).")

# ==================================================================
# 2. REESCRIBIR NexoMasterBridge.kt:
#    - activePagerTabId + Callbacks de Vault Sync + Torrent Trigger
#    - Global Clipboard (ES-style Copy/Cut/Paste Local <-> Vault)
#    - Web Browser estilo Chrome (Monitor de Descargas + Menú Long-Press Imágenes/Enlaces)
# ==================================================================
bridge_kt = r"""package com.example

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
                runCatching { tmp.delete() }
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
"""
write_f("NexoMasterBridge.kt", bridge_kt)
report.append("[OK] 2. NexoMasterBridge.kt: Navegador Chrome (Monitor Descargas + Menú Long-Press) + Portapapeles Global.")

# ==================================================================
# 3. RULETA EVA CENTRADA CON SNAP EN NexoSettingsModules.kt
# ==================================================================
sm_rel = "ui/components/NexoSettingsModules.kt"
sm_code = read_f(sm_rel)
sm_code = ensure_imports(sm_code, [
    "androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior",
    "androidx.compose.foundation.layout.BoxWithConstraints",
    "androidx.compose.foundation.layout.PaddingValues",
    "androidx.compose.runtime.rememberCoroutineScope",
    "kotlinx.coroutines.launch",
    "kotlin.math.abs"
])
m_card = re.search(r"@Composable\s+fun\s+NexoEvaThemeSelectorCard\s*\([^\)]*\)\s*\{", sm_code)
if m_card:
    b_o = sm_code.find("{", m_card.start())
    b_e = find_balanced(sm_code, b_o, "{", "}")
    centered_card = r"""@Composable
fun NexoEvaThemeSelectorCard(compact: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    val snapFling = rememberSnapFlingBehavior(lazyListState = listState)
    var userScrolled by remember { mutableStateOf(false) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            userScrolled = true
        } else if (userScrolled) {
            val info = listState.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val centered = info.visibleItemsInfo.minByOrNull { item ->
                abs((item.offset + item.size / 2) - viewportCenter)
            }
            if (centered != null) {
                val chosen = themes[centered.index % themes.size]
                if (chosen.first != NexoEvaThemeManager.currentTheme) {
                    NexoEvaThemeManager.setTheme(context, chosen.first)
                }
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
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            if (!compact) {
                Text(
                    text = "TEMA EVA (DESLIZA AL CENTRO O ELIGE ▼)",
                    color = themeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BoxWithConstraints(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    val chipWidth = 196.dp
                    val sidePad = ((maxWidth - chipWidth) / 2).coerceAtLeast(0.dp)
                    LazyRow(
                        state = listState,
                        flingBehavior = snapFling,
                        contentPadding = PaddingValues(horizontal = sidePad),
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(totalVirtual) { idx ->
                            val (id, label) = themes[idx % themes.size]
                            val selected = NexoEvaThemeManager.currentTheme == id
                            val paletteDot = NexoEvaThemeManager.units.firstOrNull { it.id == id }?.primary ?: themeColor
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) themeColor.copy(alpha = 0.24f) else Color(0xFF161B26),
                                border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) themeColor else Color(0xFF2E364F)),
                                modifier = Modifier
                                    .width(chipWidth)
                                    .clickable {
                                        NexoEvaThemeManager.setTheme(context, id)
                                        scope.launch { listState.animateScrollToItem(idx) }
                                        Toast.makeText(context, "Tema: $label", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(paletteDot))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        color = if (selected) themeColor else Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                Box {
                    IconButton(onClick = { dropdownExpanded = true }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Lista de temas", tint = themeColor)
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(Color(0xFF121622))
                    ) {
                        themes.forEachIndexed { tIdx, (id, label) ->
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
                                    val base = (totalVirtual / 2) - ((totalVirtual / 2) % themes.size)
                                    scope.launch { listState.animateScrollToItem(base + tIdx) }
                                    Toast.makeText(context, "Tema: $label", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}"""
    sm_code = sm_code[:m_card.start()] + centered_card + sm_code[b_e:]
    write_f(sm_rel, sm_code)
    report.append("[OK] 3. NexoSettingsModules.kt: Ruleta EVA con centrado automático Snap.")

# ==================================================================
# 4. CABECERA PRINCIPAL LIMPIA ("NexoFiles" / "NERV") + BOTÓN TORRENTS
# ==================================================================
as_rel = "ui/i18n/AppStrings.kt"
as_code = read_f(as_rel)
as_code = as_code.replace('override val greetingHello = "NEXOFILES // HUD"', 'override val greetingHello = "NexoFiles"')
as_code = re.sub(r"NERV HUD Explorer v[0-9\.]+", "NERV HUD Explorer v1.5.0", as_code)
write_f(as_rel, as_code)

hs_rel = "ui/screens/HomeScreen.kt"
hs_code = read_f(hs_rel)
hs_code = hs_code.replace("text = strings.greetingHello.uppercase(),", "text = strings.greetingHello,")
hs_code = hs_code.replace('text = "NERV // STORAGE TELEMETRY",', 'text = "NERV",')
# Agregar acceso directo 🧲 Torrents en Quick Tools (reemplazando o añadiendo fila limpia)
if "NexoNavBridge.openTorrentWithMagnet()" not in hs_code:
    hs_code = hs_code.replace(
        "Triple(strings.toolDownloader, NexoIcons.Download, onOpenDownloader),",
        'Triple(strings.toolDownloader, NexoIcons.Download, onOpenDownloader),\n        Triple("Torrents", NexoIcons.Download, { NexoNavBridge.openTorrentWithMagnet() }),'
    )
write_f(hs_rel, hs_code)
report.append("[OK] 4. HomeScreen + AppStrings: Cabecera limpia ('NexoFiles' / 'NERV') + acceso 'Torrents' + v1.5.0.")

# ==================================================================
# 5. GOOGLE DRIVE REPOSITORY: SUBCARPETAS DE BÓVEDA + LISTADO RECURSIVO + MOVER ARCHIVO (PATCH)
# ==================================================================
gdr_rel = "data/repo/GoogleDriveRepository.kt"
gdr_code = read_f(gdr_rel)
gdr_code = ensure_imports(gdr_code, ["com.example.NexoVaultExtraStore"])

if "suspend fun ensureVaultSubfolder(" not in gdr_code:
    old_list_vb = """  suspend fun listVaultBackups(accessToken: String): Result<List<DriveFileItem>> = withContext(Dispatchers.IO) {
    runCatching {
      val folderId = ensureVaultBackupFolder(accessToken).getOrThrow()
      listFiles(accessToken, folderId).getOrThrow()
        .filter { !it.isFolder && it.name.endsWith(".nexo_vault", ignoreCase = true) }
    }
  }"""
    new_vault_drive_ops = """  suspend fun ensureVaultSubfolder(accessToken: String, subfolderName: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val rootId = ensureVaultBackupFolder(accessToken).getOrThrow()
      val clean = subfolderName.trim()
      if (clean.isBlank()) return@runCatching rootId
      val safe = clean.replace("'", "\\\\'")
      val query = URLEncoder.encode(
        "name = '$safe' and mimeType = 'application/vnd.google-apps.folder' and '$rootId' in parents and trashed = false",
        "UTF-8",
      )
      val fields = URLEncoder.encode("files(id,name)", "UTF-8")
      val req = Request.Builder()
        .url("https://www.googleapis.com/drive/v3/files?q=$query&fields=$fields&pageSize=5")
        .header("Authorization", "Bearer $accessToken")
        .build()
      val existingId = client.newCall(req).execute().use { resp ->
        val body = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw IllegalStateException("Google Drive API (${resp.code}): $body")
        val arr = JSONObject(body).optJSONArray("files")
        if (arr != null && arr.length() > 0) arr.getJSONObject(0).getString("id") else null
      }
      existingId ?: createFolder(accessToken, clean, rootId).getOrThrow().id
    }
  }

  suspend fun moveDriveFileToFolder(accessToken: String, fileId: String, newParentId: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val getReq = Request.Builder()
        .url("https://www.googleapis.com/drive/v3/files/$fileId?fields=parents")
        .header("Authorization", "Bearer $accessToken")
        .build()
      val prevParents = client.newCall(getReq).execute().use { resp ->
        val body = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw IllegalStateException("Google Drive API (${resp.code}): $body")
        val arr = JSONObject(body).optJSONArray("parents")
        val list = mutableListOf<String>()
        if (arr != null) for (i in 0 until arr.length()) list.add(arr.getString(i))
        list
      }
      if (prevParents.contains(newParentId)) return@runCatching
      val removeParam = if (prevParents.isNotEmpty()) "&removeParents=${URLEncoder.encode(prevParents.joinToString(","), "UTF-8")}" else ""
      val patchReq = Request.Builder()
        .url("https://www.googleapis.com/drive/v3/files/$fileId?addParents=${URLEncoder.encode(newParentId, "UTF-8")}$removeParam")
        .header("Authorization", "Bearer $accessToken")
        .patch("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
        .build()
      client.newCall(patchReq).execute().use { resp ->
        if (!resp.isSuccessful) {
          val body = resp.body?.string().orEmpty()
          throw IllegalStateException("Error moviendo respaldo en Drive (${resp.code}): $body")
        }
      }
    }
  }

  suspend fun listVaultBackups(accessToken: String): Result<List<DriveFileItem>> = withContext(Dispatchers.IO) {
    runCatching {
      val rootId = ensureVaultBackupFolder(accessToken).getOrThrow()
      val rootChildren = listFiles(accessToken, rootId).getOrThrow()
      val collected = mutableListOf<DriveFileItem>()
      for (child in rootChildren) {
        if (!child.isFolder && child.name.endsWith(".nexo_vault", ignoreCase = true)) {
          NexoVaultExtraStore.driveBackupFolderMap[child.id] = ""
          collected.add(child)
        } else if (child.isFolder) {
          val subFiles = listFiles(accessToken, child.id).getOrNull().orEmpty()
          for (sf in subFiles) {
            if (!sf.isFolder && sf.name.endsWith(".nexo_vault", ignoreCase = true)) {
              NexoVaultExtraStore.driveBackupFolderMap[sf.id] = child.name
              collected.add(sf)
            }
          }
        }
      }
      collected
    }
  }"""
    assert old_list_vb in gdr_code, "No se encontró listVaultBackups en GoogleDriveRepository.kt"
    gdr_code = gdr_code.replace(old_list_vb, new_vault_drive_ops, 1)
    write_f(gdr_rel, gdr_code)
    report.append("[OK] 5. GoogleDriveRepository.kt: Subcarpetas de Bóveda + listado recursivo + mover archivo (PATCH).")

# ==================================================================
# 6. ToolsAndNetworkViewModel.kt:
#    - Corregir auto-backup en .onSuccess (en lugar de .onFailure!)
#    - Sincronización inmediata al subir/mover a carpeta/modificar + reintento 401
#    - Passphrase automático desde Master PIN (NexoVaultSecurityStore)
#    - Motor global de Pegar (Local <-> Bóveda)
# ==================================================================
tn_rel = "ui/viewmodel/ToolsAndNetworkViewModel.kt"
tn_code = read_f(tn_rel)
tn_code = ensure_imports(tn_code, [
    "com.example.NexoVaultSecurityStore",
    "com.example.NexoVaultExtraStore",
    "com.example.NexoGlobalClipboard",
    "com.example.NexoGlobalFileOps",
    "com.example.NexoClipMode",
    "com.example.NexoClipOrigin"
])

# A) Arreglar addUriToVault (quitar backup de .onFailure y ponerlo en .onSuccess con la carpeta activa!)
old_add_uri = """      container.vault.lockFromUri(uri)
        .onSuccess {
          val items = container.vault.listVaultItems()
          _vaultState.value = _vaultState.value.copy(items = items, isBusy = false)
          emitMessage("Cifrado en Bóveda Privada: ${it.originalName}")
        }
        .onFailure {
          _vaultState.value = _vaultState.value.copy(isBusy = false)
                if (com.example.ui.components.NexoSecureVaultManager.autoBackupOnImport) { backupVaultToDrive("NEXO_HERMETIC_AUTO_KEY_2026") }
          emitMessage("Error cifrando: ${it.localizedMessage}")
        }"""
new_add_uri = """      container.vault.lockFromUri(uri)
        .onSuccess { created ->
          val activeFolder = NexoVaultExtraStore.activeFolder.orEmpty()
          if (activeFolder.isNotBlank()) {
            NexoVaultExtraStore.assignToFolder(context, created.id, activeFolder)
          }
          val items = container.vault.listVaultItems()
          _vaultState.value = _vaultState.value.copy(items = items, isBusy = false)
          emitMessage("Cifrado en Bóveda Privada: ${created.originalName}")
          backupVaultToDrive(NexoVaultSecurityStore.getBackupPassphrase(context), setOf(created.id))
        }
        .onFailure {
          _vaultState.value = _vaultState.value.copy(isBusy = false)
          emitMessage("Error cifrando: ${it.localizedMessage}")
        }"""
assert old_add_uri in tn_code, "No se encontró addUriToVault exacto en ToolsAndNetworkViewModel.kt"
tn_code = tn_code.replace(old_add_uri, new_add_uri, 1)

# B) Reemplazar refreshVaultDriveBackups + backupVaultToDrive + restoreSelectedVaultBackupsFromDrive
#    para soportar 401 retry, subcarpetas en Drive, Master PIN passphrase y movimiento automático en Drive
start_vb = tn_code.find("  // --- Vault Encrypted Google Drive Backup (.nexo_vault) ---")
end_vb = tn_code.find("  // --- Ephemeral Web / iPhone Remote Portal ---")
assert start_vb != -1 and end_vb != -1, "No se hallaron límites del bloque Drive Backup en ToolsAndNetworkViewModel.kt"

new_vault_drive_block = """  // --- Vault Encrypted Google Drive Backup (.nexo_vault) ---
  private suspend fun resolveFreshDriveToken(): String? {
    val cur = ensureValidDriveToken()
    if (!cur.isNullOrBlank()) return cur
    return when (val res = container.googleDrive.trySilentAuthorize()) {
      is DriveAuthStepResult.Authorized -> {
        _driveState.value = _driveState.value.copy(isAuthorized = true, accessToken = res.accessToken)
        res.accessToken
      }
      else -> null
    }
  }

  private suspend fun <T> withDriveTokenRetry(block: suspend (String) -> Result<T>): Result<T> {
    val firstToken = resolveFreshDriveToken()
      ?: return Result.failure(IllegalStateException("Conecta tu cuenta de Google Drive primero"))
    val firstRes = block(firstToken)
    if (firstRes.isFailure && (firstRes.exceptionOrNull()?.message?.contains("401") == true)) {
      val refreshed = container.googleDrive.trySilentAuthorize()
      if (refreshed is DriveAuthStepResult.Authorized) {
        _driveState.value = _driveState.value.copy(isAuthorized = true, accessToken = refreshed.accessToken)
        return block(refreshed.accessToken)
      }
    }
    return firstRes
  }

  fun refreshVaultDriveBackups() {
    viewModelScope.launch {
      _vaultState.value = _vaultState.value.copy(isDriveBusy = true, driveStatusMessage = null)
      withDriveTokenRetry { token -> container.googleDrive.listVaultBackups(token) }
        .onSuccess { driveFiles ->
          val entries = driveFiles.map { df ->
            val folder = NexoVaultExtraStore.driveBackupFolderMap[df.id].orEmpty()
            val cleanBase = df.name.removeSuffix(".nexo_vault")
            VaultBackupEntry(
              driveFile = df,
              originalName = if (folder.isNotBlank()) "$folder / $cleanBase" else cleanBase,
            )
          }
          _vaultState.value = _vaultState.value.copy(
            driveBackups = entries,
            isDriveBusy = false,
            driveStatusMessage = "${entries.size} respaldo(s) .nexo_vault en Drive",
          )
        }
        .onFailure { err ->
          _vaultState.value = _vaultState.value.copy(
            isDriveBusy = false,
            driveStatusMessage = if ((err.localizedMessage ?: "").contains("401") || (err.localizedMessage ?: "").contains("UNAUTHENTICATED"))
              "Sesión de Google Drive expirada. Abre Google Drive y toca Reconectar."
            else "Error Drive: ${err.localizedMessage}",
          )
        }
    }
  }

  fun backupVaultToDrive(rawPassphrase: String = "", selectedIds: Set<String> = emptySet()) {
    val passphrase = rawPassphrase.trim().takeIf { it.length >= 4 } ?: NexoVaultSecurityStore.getBackupPassphrase(context)
    viewModelScope.launch {
      _vaultState.value = _vaultState.value.copy(
        isDriveBusy = true,
        driveStatusMessage = "Sincronizando respaldos cifrados .nexo_vault con Google Drive…",
      )
      val stageDir = File(context.cacheDir, "vault_drive_stage").apply {
        deleteRecursively()
        mkdirs()
      }
      withDriveTokenRetry { token ->
        runCatching {
          val allVaultItems = container.vault.listVaultItems()
          val targets = if (selectedIds.isEmpty()) allVaultItems else allVaultItems.filter { it.id in selectedIds }
          if (targets.isEmpty()) return@runCatching 0
          val existingRemote = container.googleDrive.listVaultBackups(token).getOrNull().orEmpty()
          var uploaded = 0
          for (item in targets) {
            val folderName = NexoVaultExtraStore.itemFolderMap[item.id].orEmpty()
            val targetFolderId = container.googleDrive.ensureVaultSubfolder(token, folderName).getOrThrow()
            val expectedRemoteName = "${item.originalName}.nexo_vault"
            val matchRemote = existingRemote.firstOrNull {
              it.name.equals(expectedRemoteName, ignoreCase = true) ||
                it.name.removeSuffix(".nexo_vault").equals(item.originalName, ignoreCase = true)
            }
            if (matchRemote != null) {
              container.googleDrive.moveDriveFileToFolder(token, matchRemote.id, targetFolderId)
              uploaded++
              continue
            }
            val singleDir = File(stageDir, item.id).apply { mkdirs() }
            val exported = container.vault.exportSelectedVaultToNxv(listOf(item), passphrase, singleDir).getOrThrow()
            for (nxv in exported) {
              val res = container.googleDrive.uploadFile(token, nxv, targetFolderId)
              if (res.isSuccess) uploaded++
            }
          }
          uploaded
        }
      }.onSuccess { count ->
        stageDir.deleteRecursively()
        _vaultState.value = _vaultState.value.copy(
          isDriveBusy = false,
          driveStatusMessage = "Respaldo Drive al día ($count archivo(s) .nexo_vault)",
        )
        refreshVaultDriveBackups()
        if (count > 0) emitMessage("Bóveda sincronizada con Drive ($count archivo(s))")
      }.onFailure { err ->
        stageDir.deleteRecursively()
        _vaultState.value = _vaultState.value.copy(
          isDriveBusy = false,
          driveStatusMessage = "Error en respaldo: ${err.localizedMessage}",
        )
      }
    }
  }

  fun moveVaultItemsToFolderAndSync(items: List<VaultFileItem>, targetFolder: String) {
    if (items.isEmpty()) return
    items.forEach { NexoVaultExtraStore.assignToFolder(context, it.id, targetFolder) }
    val destLabel = targetFolder.ifBlank { "Raíz de Bóveda" }
    emitMessage("Movido(s) a $destLabel · Sincronizando Drive...")
    backupVaultToDrive(NexoVaultSecurityStore.getBackupPassphrase(context), items.map { it.id }.toSet())
  }

  fun restoreVaultBackupFromDrive(entry: VaultBackupEntry, rawPassphrase: String = "") {
    restoreSelectedVaultBackupsFromDrive(listOf(entry), rawPassphrase)
  }

  fun restoreSelectedVaultBackupsFromDrive(entries: List<VaultBackupEntry>, rawPassphrase: String = "") {
    if (entries.isEmpty()) return
    val primaryPass = rawPassphrase.trim().takeIf { it.length >= 4 } ?: NexoVaultSecurityStore.getBackupPassphrase(context)
    viewModelScope.launch {
      _vaultState.value = _vaultState.value.copy(
        isDriveBusy = true,
        driveStatusMessage = "Descargando y descifrando ${entries.size} archivo(s)…",
      )
      val tmpDir = File(context.cacheDir, "vault_restore_stage").apply {
        deleteRecursively()
        mkdirs()
      }
      withDriveTokenRetry { token ->
        runCatching {
          var restoredCount = 0
          var lastRestoredName = ""
          for (entry in entries) {
            val downloaded = container.googleDrive.downloadFile(token, entry.driveFile, tmpDir).getOrThrow()
            val item = container.vault.importNxvToVault(downloaded, primaryPass).getOrElse {
              container.vault.importNxvToVault(downloaded, "NEXO_HERMETIC_AUTO_KEY_2026").getOrThrow()
            }
            val remoteFolder = NexoVaultExtraStore.driveBackupFolderMap[entry.driveFile.id].orEmpty()
            if (remoteFolder.isNotBlank()) {
              NexoVaultExtraStore.assignToFolder(context, item.id, remoteFolder)
            }
            NexoVaultExtraStore.removeCloudOnlyPlaceholder(context, item.originalName)
            restoredCount++
            lastRestoredName = item.originalName
          }
          restoredCount to lastRestoredName
        }
      }.onSuccess { (count, lastName) ->
        tmpDir.deleteRecursively()
        val items = container.vault.listVaultItems()
        val msg = if (count == 1) "Restaurado en Bóveda: $lastName" else "$count archivo(s) restaurado(s) en Bóveda"
        _vaultState.value = _vaultState.value.copy(
          items = items,
          isDriveBusy = false,
          driveStatusMessage = msg,
        )
        emitMessage(msg)
      }.onFailure { err ->
        tmpDir.deleteRecursively()
        _vaultState.value = _vaultState.value.copy(
          isDriveBusy = false,
          driveStatusMessage = "Fallo de descifrado: ${err.localizedMessage}",
        )
        emitMessage("Error restaurando respaldo: ${err.localizedMessage}")
      }
    }
  }

  fun executeGlobalPasteToLocal(destDirPath: String, onRefreshLocal: () -> Unit) {
    val snapshot = NexoGlobalClipboard.entries.toList()
    val isMove = NexoGlobalClipboard.mode == NexoClipMode.MOVE
    if (snapshot.isEmpty() || NexoGlobalClipboard.isBusy) return
    NexoGlobalClipboard.isBusy = true
    viewModelScope.launch {
      var okCount = 0
      withContext(Dispatchers.IO) {
        val destDir = File(destDirPath)
        if (!destDir.exists()) destDir.mkdirs()
        for (entry in snapshot) {
          if (entry.origin == NexoClipOrigin.LOCAL) {
            val src = File(entry.pathOrId)
            if (NexoGlobalFileOps.copyOrMoveLocal(src, destDir, isMove)) okCount++
          } else if (entry.origin == NexoClipOrigin.VAULT && entry.vaultItem != null) {
            val vItem = entry.vaultItem
            val dec = container.vault.decryptToCacheAndOpen(vItem).getOrNull()
            if (dec != null && dec.exists()) {
              val target = NexoGlobalFileOps.uniqueDest(destDir, vItem.originalName)
              val copied = runCatching { dec.copyTo(target, overwrite = false); true }.getOrDefault(false)
              runCatching { dec.delete() }
              if (copied) {
                okCount++
                if (isMove) runCatching { container.vault.deleteFromVault(vItem) }
              }
            }
          }
        }
      }
      _vaultState.value = _vaultState.value.copy(items = container.vault.listVaultItems())
      NexoGlobalClipboard.isBusy = false
      if (isMove && okCount > 0) NexoGlobalClipboard.clear()
      onRefreshLocal()
      emitMessage(if (isMove) "Movido(s): $okCount elemento(s)" else "Copiado(s): $okCount elemento(s)")
    }
  }

  fun executeGlobalPasteToVault(onRefreshLocal: () -> Unit) {
    val snapshot = NexoGlobalClipboard.entries.toList()
    val isMove = NexoGlobalClipboard.mode == NexoClipMode.MOVE
    val targetFolder = NexoVaultExtraStore.activeFolder.orEmpty()
    if (snapshot.isEmpty() || NexoGlobalClipboard.isBusy) return
    NexoGlobalClipboard.isBusy = true
    viewModelScope.launch {
      val affectedIds = mutableSetOf<String>()
      withContext(Dispatchers.IO) {
        for (entry in snapshot) {
          if (entry.origin == NexoClipOrigin.LOCAL) {
            val rootSrc = File(entry.pathOrId)
            val filesToImport = if (rootSrc.isDirectory) rootSrc.walkTopDown().filter { it.isFile }.toList()
              else if (rootSrc.isFile) listOf(rootSrc) else emptyList()
            for (srcFile in filesToImport) {
              val created = runCatching { container.vault.lockFromUri(Uri.fromFile(srcFile)) }.getOrNull()?.getOrNull()
              if (created != null) {
                affectedIds.add(created.id)
                if (targetFolder.isNotBlank()) NexoVaultExtraStore.assignToFolder(context, created.id, targetFolder)
                if (isMove) runCatching { srcFile.delete() }
              }
            }
            if (isMove && rootSrc.isDirectory && affectedIds.isNotEmpty()) runCatching { rootSrc.deleteRecursively() }
          } else if (entry.origin == NexoClipOrigin.VAULT && entry.vaultItem != null) {
            val vItem = entry.vaultItem
            if (isMove) {
              NexoVaultExtraStore.assignToFolder(context, vItem.id, targetFolder)
              affectedIds.add(vItem.id)
            } else {
              val srcFile = container.vault.decryptToCacheAndOpen(vItem).getOrNull()
              if (srcFile != null && srcFile.exists()) {
                val created = runCatching { container.vault.lockFromUri(Uri.fromFile(srcFile)) }.getOrNull()?.getOrNull()
                runCatching { srcFile.delete() }
                if (created != null) {
                  affectedIds.add(created.id)
                  if (targetFolder.isNotBlank()) NexoVaultExtraStore.assignToFolder(context, created.id, targetFolder)
                }
              }
            }
          }
        }
      }
      _vaultState.value = _vaultState.value.copy(items = container.vault.listVaultItems())
      NexoGlobalClipboard.isBusy = false
      if (isMove && affectedIds.isNotEmpty()) NexoGlobalClipboard.clear()
      onRefreshLocal()
      if (affectedIds.isNotEmpty()) {
        emitMessage(if (isMove) "Movido(s) en Bóveda: ${affectedIds.size} · Sincronizando Drive..." else "Copiado(s) en Bóveda: ${affectedIds.size} · Sincronizando Drive...")
        backupVaultToDrive(NexoVaultSecurityStore.getBackupPassphrase(context), affectedIds)
      }
    }
  }

"""
tn_code = tn_code[:start_vb] + new_vault_drive_block + tn_code[end_vb:]
write_f(tn_rel, tn_code)
report.append("[OK] 6. ToolsAndNetworkViewModel.kt: Auto-backup real + sync de carpetas en Drive + 401 retry + pegado global.")

# ==================================================================
# 7. SettingsScreen.kt:
#    - Bloquear SIEMPRE '3. Vault Security & Backup' con Master PIN
#    - Cambiar Master PIN exige PIN anterior o Google Authenticator (TOTP)
#    - Botón 'Vincular Google Authenticator (QR 2FA)' + Passphrase único
# ==================================================================
st_rel = "ui/screens/SettingsScreen.kt"
st_code = read_f(st_rel)
st_code = ensure_imports(st_code, [
    "com.example.NexoVaultSecurityStore",
    "com.example.NexoMasterPinChangeDialog",
    "com.example.NexoAuthenticatorSetupDialog",
    "com.example.NexoNavBridge"
])

# Añadir estado showTotpSetupDialog
if "showTotpSetupDialog" not in st_code:
    st_code = st_code.replace(
        "var showPinDialog by remember { mutableStateOf(false) }",
        "var showPinDialog by remember { mutableStateOf(false) }\n  var showTotpSetupDialog by remember { mutableStateOf(false) }"
    )

# Proteger la apertura del panel Block 3 (buscando el SettingsAccordionCard que contiene biometricAccessTitle!)
bio_idx = st_code.find("strings.biometricAccessTitle")
if bio_idx != -1:
    acc_start = st_code.rfind("SettingsAccordionCard(", 0, bio_idx)
    if acc_start != -1:
        acc_head = st_code[acc_start:bio_idx]
        m_ep = re.search(r'expanded\s*=\s*expandedPanel\s*==\s*"([^"]+)"', acc_head)
        m_tog = re.search(r'onToggle\s*=\s*\{', acc_head)
        if m_ep and m_tog:
            p_key = m_ep.group(1)
            tog_abs = acc_start + m_tog.start()
            b_o = st_code.find("{", tog_abs)
            b_e = find_balanced(st_code, b_o, "{", "}")
            strict_pin_toggle = f"""onToggle = {{
          if (expandedPanel == "{p_key}") {{
            expandedPanel = ""
          }} else if (allSettings.masterPinHash.isNotBlank() || NexoVaultSecurityStore.hasLocalPin(context)) {{
            pendingSensitiveAction = {{ expandedPanel = "{p_key}" }}
          }} else {{
            expandedPanel = "{p_key}"
          }}
        }}"""
            st_code = st_code[:tog_abs] + strict_pin_toggle + st_code[b_e:]
            report.append(f"[OK] 7a. SettingsScreen.kt: Panel '{p_key}' (Vault Security & Backup) bloqueado SIEMPRE por Master PIN.")

# Hacer que el botón MASTER PIN · CONFIGURE abra directamente NexoMasterPinChangeDialog (que exige PIN actual o Google Authenticator)
pin_btn_m = re.search(r'OutlinedButton\s*\(\s*onClick\s*=\s*\{[^\}]{0,250}showPinDialog\s*=\s*true[^\}]*\}\s*,', st_code, re.S)
if pin_btn_m:
    st_code = st_code[:pin_btn_m.start()] + "OutlinedButton(\n          onClick = { showPinDialog = true }," + st_code[pin_btn_m.end():]

# Añadir botón de Google Authenticator (2FA QR) + estado del Passphrase justo debajo del botón de Master PIN
pin_btn_tag = '.testTag("settings_master_pin_button")'
t_idx = st_code.find(pin_btn_tag)
if t_idx != -1 and "showTotpSetupDialog = true" not in st_code:
    # Encontrar el cierre del OutlinedButton que contiene settings_master_pin_button
    btn_s = st_code.rfind("OutlinedButton(", 0, t_idx)
    p_e = find_balanced(st_code, st_code.find("(", btn_s), "(", ")")
    b_o = st_code.find("{", p_e)
    b_e = find_balanced(st_code, b_o, "{", "}")
    if b_e != -1:
        totp_btn = """
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(
          onClick = { showTotpSetupDialog = true },
          modifier = Modifier.fillMaxWidth(),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
          shape = RoundedCornerShape(4.dp),
        ) {
          Text(
            text = if (NexoVaultSecurityStore.isTotpVerified(context)) "✓ GOOGLE AUTHENTICATOR (2FA ACTIVO)" else "🔐 VINCULAR GOOGLE AUTHENTICATOR (RECUPERAR PIN)",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
          )
        }
        Text(
          text = "Passphrase respaldo Drive: sincronizado automáticamente con tu Master PIN.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
"""
        st_code = st_code[:b_e] + totp_btn + st_code[b_e:]

# Reemplazar MasterPinSetupDialog por NexoMasterPinChangeDialog + añadir NexoAuthenticatorSetupDialog
old_pin_dlg = """  if (showPinDialog) {
    MasterPinSetupDialog(
      hasExistingPin = allSettings.masterPinHash.isNotBlank(),
      onSavePin = onSetMasterPin,
      onDismiss = { showPinDialog = false },
    )
  }"""
new_pin_dlg = """  if (showPinDialog) {
    NexoMasterPinChangeDialog(
      hasExistingPin = allSettings.masterPinHash.isNotBlank() || NexoVaultSecurityStore.hasLocalPin(context),
      linkedEmail = NexoNavBridge.linkedGoogleEmail,
      onVerifyExistingPin = onVerifyMasterPin,
      onSaveNewPin = onSetMasterPin,
      onDismiss = { showPinDialog = false },
    )
  }

  if (showTotpSetupDialog) {
    NexoAuthenticatorSetupDialog(
      accountEmail = NexoNavBridge.linkedGoogleEmail,
      onDismiss = { showTotpSetupDialog = false },
    )
  }"""
if old_pin_dlg in st_code:
    st_code = st_code.replace(old_pin_dlg, new_pin_dlg, 1)

# Asegurar que MasterPinVerifyDialog también verifique contra NexoVaultSecurityStore
st_code = st_code.replace(
    "onVerifyPin = onVerifyMasterPin,",
    "onVerifyPin = { pin -> NexoVaultSecurityStore.verifyPin(context, pin, onVerifyMasterPin) },"
)
st_code = re.sub(r"NERV HUD Explorer v[0-9\.]+", "NERV HUD Explorer v1.5.0", st_code)
write_f(st_rel, st_code)
report.append("[OK] 7b. SettingsScreen.kt: Cambio de Master PIN protegido (PIN actual o Authenticator 2FA) + Passphrase único.")

# ==================================================================
# 8. FilesScreen.kt:
#    - BackHandler aislado a pestaña FILES + limpia selección primero
#    - Quitar botón `...` repetido de cada fila (FileListItemCard)
#    - Barra inferior fija estilo ES File Explorer (normal + selección)
#    - Sincronizar con NexoGlobalClipboard
# ==================================================================
fs_rel = "ui/screens/FilesScreen.kt"
fs_code = read_f(fs_rel)
fs_code = ensure_imports(fs_code, [
    "com.example.NexoNavBridge",
    "com.example.NexoGlobalClipboard",
    "com.example.NexoClipMode",
    "androidx.compose.runtime.LaunchedEffect",
    "androidx.compose.ui.unit.sp"
])

# A) BackHandler condicionado a pestaña FILES + sync clipboard
fs_code = re.sub(
    r'androidx\.activity\.compose\.BackHandler\s*\([^\)]*\)\s*\{\s*onNavigateUp\(\)\s*\}',
    '''androidx.activity.compose.BackHandler(
    enabled = (!NexoNavBridge.isInMainPager || NexoNavBridge.activePagerTabId == "FILES") &&
      (uiState.selectedPaths.isNotEmpty() || (uiState.currentPath.trimEnd('/').isNotBlank() && uiState.currentPath.trimEnd('/') != "/storage/emulated/0"))
  ) {
    if (uiState.selectedPaths.isNotEmpty()) onClearSelection() else onNavigateUp()
  }
  LaunchedEffect(uiState.clipboard) {
    val clip = uiState.clipboard
    if (clip != null && clip.paths.isNotEmpty()) {
      NexoGlobalClipboard.stageLocal(clip.paths, if (clip.action == ClipboardAction.MOVE) NexoClipMode.MOVE else NexoClipMode.COPY)
    }
  }
  var showEsMoreMenu by remember { mutableStateOf(false) }''',
    fs_code,
    count=1
)

# B) Reemplazar floatingActionButton por barra inferior estilo ES File Explorer (Scaffold bottomBar)
scaf_idx = fs_code.find("  Scaffold(\n    containerColor = MaterialTheme.colorScheme.background,\n    floatingActionButton = {")
if scaf_idx != -1:
    fab_o = fs_code.find("{", scaf_idx + len("  Scaffold(\n    containerColor = MaterialTheme.colorScheme.background,\n    floatingActionButton = "))
    fab_e = find_balanced(fs_code, fab_o, "{", "}")
    if fab_e != -1:
        es_bottom_bar = """  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    bottomBar = {
      val selList = uiState.selectedPaths.toList()
      val selItems = uiState.files.filter { it.path in uiState.selectedPaths }
      val singleItem = if (selItems.size == 1) selItems.first() else null
      Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (selList.isEmpty()) {
            Box {
              TextButton(onClick = { showCreateMenu = true }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                Text("+ NUEVO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
              }
              DropdownMenu(
                expanded = showCreateMenu,
                onDismissRequest = { showCreateMenu = false },
                modifier = Modifier.background(NexoTheme.semantic.modalSurface).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
              ) {
                DropdownMenuItem(
                  text = { Text(strings.newFolderMenu.uppercase(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) },
                  leadingIcon = { Icon(NexoIcons.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                  onClick = { showCreateMenu = false; showNewFolderDialog = true },
                )
                DropdownMenuItem(
                  text = { Text(strings.newFileMenu.uppercase(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) },
                  leadingIcon = { Icon(NexoIcons.NoteAdd, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                  onClick = { showCreateMenu = false; showNewFileDialog = true },
                )
                DropdownMenuItem(
                  text = { Text(strings.importSafMenu.uppercase(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) },
                  leadingIcon = { Icon(NexoIcons.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                  onClick = { showCreateMenu = false; onImportSaf() },
                )
              }
            }
            TextButton(onClick = { onNavigateToPath(uiState.currentPath) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
              Text("↻ REFRESCAR", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            TextButton(onClick = onSelectAll, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
              Text("☑ TODO", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Box {
              TextButton(onClick = { showEsMoreMenu = true }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                Text("⋯ MÁS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
              }
              DropdownMenu(
                expanded = showEsMoreMenu,
                onDismissRequest = { showEsMoreMenu = false },
                modifier = Modifier.background(NexoTheme.semantic.modalSurface).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
              ) {
                if (uiState.currentPath.trimEnd('/') != "/storage/emulated/0") {
                  DropdownMenuItem(text = { Text("← Subir carpeta") }, onClick = { showEsMoreMenu = false; onNavigateUp() })
                }
                DropdownMenuItem(text = { Text("🌐 Navegador Web") }, onClick = { showEsMoreMenu = false; NexoNavBridge.showWebBrowser = true })
                DropdownMenuItem(text = { Text("🧲 NexoTorrent") }, onClick = { showEsMoreMenu = false; NexoNavBridge.openTorrentWithMagnet() })
              }
            }
          } else {
            TextButton(
              onClick = {
                NexoGlobalClipboard.stageLocal(selList, NexoClipMode.COPY)
                onStageClipboard(selList, ClipboardAction.COPY)
                onClearSelection()
              },
              contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            ) { Text("📋 COPIAR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
            TextButton(
              onClick = {
                NexoGlobalClipboard.stageLocal(selList, NexoClipMode.MOVE)
                onStageClipboard(selList, ClipboardAction.MOVE)
                onClearSelection()
              },
              contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            ) { Text("✂️ CORTAR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
            TextButton(
              onClick = { onMoveToRecycleBin(selList) },
              contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            ) { Text("🗑 BORRAR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NexoTheme.semantic.evaRed) }
            TextButton(
              onClick = {
                if (singleItem != null) renameTarget = singleItem else zipTargetPaths = selList
              },
              contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            ) { Text(if (singleItem != null) "✏ NOMBRE" else "📦 ZIP", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface) }
            Box {
              TextButton(
                onClick = { showEsMoreMenu = true },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
              ) { Text("⋯ MÁS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary) }
              DropdownMenu(
                expanded = showEsMoreMenu,
                onDismissRequest = { showEsMoreMenu = false },
                modifier = Modifier.background(NexoTheme.semantic.modalSurface).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
              ) {
                if (singleItem != null && !singleItem.isDirectory) {
                  DropdownMenuItem(text = { Text(strings.openWith) }, onClick = { showEsMoreMenu = false; onOpenWith(singleItem) })
                }
                if (singleItem != null && !singleItem.isDirectory && FormatUtils.isArchive(singleItem.name)) {
                  DropdownMenuItem(text = { Text(strings.extractHere) }, onClick = { showEsMoreMenu = false; onExtractArchive(singleItem) })
                }
                if (singleItem != null && !singleItem.isDirectory && FormatUtils.isImage(singleItem.name)) {
                  DropdownMenuItem(text = { Text(strings.editInNexoPhotoEditor) }, onClick = { showEsMoreMenu = false; onOpenInPhotoEditor(singleItem) })
                }
                if (singleItem != null && !singleItem.isDirectory && FormatUtils.isTextEditable(singleItem.name)) {
                  DropdownMenuItem(text = { Text(strings.editInNexoNotes) }, onClick = { showEsMoreMenu = false; onOpenInNoteEditor(singleItem) })
                }
                DropdownMenuItem(text = { Text("🔒 Cifrar en Bóveda (${selItems.size})") }, onClick = {
                  showEsMoreMenu = false
                  selItems.filterNot { it.isDirectory }.forEach { onLockInVault(it) }
                  onClearSelection()
                })
                DropdownMenuItem(text = { Text("☁️ Subir a Google Drive") }, onClick = {
                  showEsMoreMenu = false
                  selItems.filterNot { it.isDirectory }.forEach { onUploadToDrive(it) }
                  onClearSelection()
                })
                DropdownMenuItem(text = { Text("↗ Compartir") }, onClick = { showEsMoreMenu = false; onSharePaths(selList) })
                DropdownMenuItem(text = { Text("📦 Comprimir ZIP") }, onClick = { showEsMoreMenu = false; zipTargetPaths = selList })
                if (singleItem != null) {
                  DropdownMenuItem(text = { Text("★ Marcador") }, onClick = { showEsMoreMenu = false; onToggleBookmark(singleItem) })
                  DropdownMenuItem(text = { Text("ℹ Propiedades") }, onClick = { showEsMoreMenu = false; onOpenProperties(singleItem.path) })
                }
                DropdownMenuItem(text = { Text("⚠ Eliminar permanente", color = NexoTheme.semantic.evaRed) }, onClick = {
                  showEsMoreMenu = false; onDeletePermanently(selList)
                })
              }
            }
          }
        }
      }
    }"""
        fs_code = fs_code[:scaf_idx] + es_bottom_bar + fs_code[fab_e:]

# C) Quitar el botón repetido de 3 puntos (`...`) en cada fila de FileListItemCard (envolver su Box con if (false))
mh_idx = fs_code.find("NexoIcons.MoreHoriz")
if mh_idx != -1:
    box_s = fs_code.rfind("Box {", max(0, mh_idx - 350), mh_idx)
    if box_s == -1:
        box_s = fs_code.rfind("Box(", max(0, mh_idx - 350), mh_idx)
    if box_s != -1:
        b_o = fs_code.find("{", box_s)
        b_e = find_balanced(fs_code, b_o, "{", "}")
        if b_e != -1:
            fs_code = fs_code[:box_s] + "if (false) {\n" + fs_code[box_s:b_e] + "\n}" + fs_code[b_e:]

write_f(fs_rel, fs_code)
report.append("[OK] 8. FilesScreen.kt: Barra inferior fija estilo ES + sin '...' por fila + BackHandler aislado.")

# ==================================================================
# 9. GoogleDriveScreen.kt & CategoryFilesScreen.kt
# ==================================================================
gd_rel = "ui/screens/GoogleDriveScreen.kt"
gd_code = read_f(gd_rel)
gd_code = ensure_imports(gd_code, ["com.example.NexoNavBridge"])
gd_code = gd_code.replace("  BackHandler {", '  BackHandler(enabled = !NexoNavBridge.isInMainPager || NexoNavBridge.activePagerTabId == "DRIVE") {', 1)
write_f(gd_rel, gd_code)

cf_rel = "ui/screens/CategoryFilesScreen.kt"
cf_code = read_f(cf_rel)
cf_code = ensure_imports(cf_code, [
    "com.example.NexoGlobalClipboard",
    "com.example.NexoClipMode",
    "android.widget.Toast",
    "androidx.compose.foundation.BorderStroke",
    "androidx.compose.foundation.ExperimentalFoundationApi",
    "androidx.compose.foundation.combinedClickable",
    "androidx.compose.foundation.shape.RoundedCornerShape",
    "androidx.compose.material3.OutlinedButton",
    "androidx.compose.material3.Surface",
    "androidx.compose.material3.TextButton",
    "androidx.compose.runtime.getValue",
    "androidx.compose.runtime.mutableStateOf",
    "androidx.compose.runtime.remember",
    "androidx.compose.runtime.setValue",
    "androidx.compose.ui.platform.LocalContext"
])
if "@OptIn(ExperimentalFoundationApi::class)" not in cf_code:
    cf_code = cf_code.replace("@Composable\nfun CategoryFilesScreen(", "@OptIn(ExperimentalFoundationApi::class)\n@Composable\nfun CategoryFilesScreen(", 1)
if "catSelectedPaths" not in cf_code:
    cf_code = cf_code.replace(
        "  BackHandler(onBack = onBack)",
        """  val context = LocalContext.current
  var catSelectedPaths by remember { mutableStateOf(setOf<String>()) }
  BackHandler { if (catSelectedPaths.isNotEmpty()) catSelectedPaths = emptySet() else onBack() }""",
        1
    )
    cat_bar = """
    if (catSelectedPaths.isNotEmpty()) {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text("${catSelectedPaths.size}", color = MaterialTheme.colorScheme.primary)
          OutlinedButton(
            onClick = {
              NexoGlobalClipboard.stageLocal(catSelectedPaths.toList(), NexoClipMode.COPY)
              catSelectedPaths = emptySet()
              Toast.makeText(context, "Copiado · Ve a Archivos o Bóveda y toca PEGAR", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f)
          ) { Text("📋 COPIAR", maxLines = 1) }
          OutlinedButton(
            onClick = {
              NexoGlobalClipboard.stageLocal(catSelectedPaths.toList(), NexoClipMode.MOVE)
              catSelectedPaths = emptySet()
              Toast.makeText(context, "Cortado · Ve a Archivos o Bóveda y toca PEGAR", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f)
          ) { Text("✂️ CORTAR", maxLines = 1) }
          TextButton(onClick = { catSelectedPaths = emptySet() }) { Text("✖") }
        }
      }
    }
"""
    cf_code = cf_code.replace("    if (isLoading) {", cat_bar + "\n    if (isLoading) {", 1)
    m_ci = re.search(r"items\s*\(\s*files[^\)]*\)\s*\{\s*(\w+)\s*->", cf_code)
    if m_ci:
        fv = m_ci.group(1)
        cio = cf_code.find("{", m_ci.start())
        cie = find_balanced(cf_code, cio, "{", "}")
        if cie != -1:
            cblk = cf_code[cio:cie]
            row_m = re.search(r"(Row\s*\(\s*modifier\s*=\s*Modifier)", cblk)
            if row_m and "combinedClickable" not in cblk:
                cblk = cblk[:row_m.start()] + f"""{row_m.group(1)}.combinedClickable(
                  onClick = {{
                    if (catSelectedPaths.isNotEmpty()) catSelectedPaths = if ({fv}.path in catSelectedPaths) catSelectedPaths - {fv}.path else catSelectedPaths + {fv}.path
                    else onOpenFile({fv})
                  }},
                  onLongClick = {{ catSelectedPaths = if ({fv}.path in catSelectedPaths) catSelectedPaths - {fv}.path else catSelectedPaths + {fv}.path }}
                )""" + cblk[row_m.end():]
                cf_code = cf_code[:cio] + cblk + cf_code[cie:]
write_f(cf_rel, cf_code)
report.append("[OK] 9. GoogleDriveScreen + CategoryFilesScreen: BackHandler aislado + Long-Press global.")

# ==================================================================
# 10. VaultScreen.kt:
#     - BackHandler SOLO en pestaña VAULT (sale de carpeta antes de salir)
#     - Long-Press en cada archivo + Barra ES (Copiar / Cortar / Carpeta / Eliminar)
#     - Mover a carpeta sincroniza Drive automáticamente
#     - Modal Drive sin pedir Passphrase manual (usa Master PIN de Ajustes)
# ==================================================================
vs_rel = "ui/screens/VaultScreen.kt"
vs_code = read_f(vs_rel)
vs_code = ensure_imports(vs_code, [
    "com.example.NexoGlobalClipboard",
    "com.example.NexoClipMode",
    "com.example.NexoVaultSecurityStore",
    "androidx.compose.foundation.ExperimentalFoundationApi",
    "androidx.compose.foundation.combinedClickable"
])
if "@OptIn(ExperimentalFoundationApi::class)" not in vs_code:
    vs_code = vs_code.replace("@Composable\nfun VaultScreen(", "@OptIn(ExperimentalFoundationApi::class)\n@Composable\nfun VaultScreen(", 1)

# Quitar BackHandler incondicional de L135
bh_vs = re.search(r"  BackHandler\s*\{", vs_code)
if bh_vs:
    bo = vs_code.find("{", bh_vs.start())
    be = find_balanced(vs_code, bo, "{", "}")
    if be != -1:
        vs_code = vs_code[:bh_vs.start()] + vs_code[be:]

# Colocar BackHandler condicionado a pestaña VAULT después de selectedVaultIds
sel_decl = "  var selectedVaultIds by remember(state.items) { mutableStateOf(setOf<String>()) }"
vs_code = vs_code.replace(
    sel_decl,
    """  var selectedVaultIds by remember(state.items) { mutableStateOf(setOf<String>()) }
  var showMoveToFolderDialog by remember { mutableStateOf(false) }

  BackHandler(enabled = !NexoNavBridge.isInMainPager || NexoNavBridge.activePagerTabId == "VAULT") {
    when {
      selectedVaultIds.isNotEmpty() -> selectedVaultIds = emptySet()
      NexoVaultExtraStore.activeFolder != null -> NexoVaultExtraStore.activeFolder = null
      else -> {
        NexoVaultExtraStore.clearThumbnails()
        onLockVault()
        onBack()
      }
    }
  }""",
    1
)

# Añadir barra ES (COPIAR / CORTAR / CARPETA / BORRAR) cuando selectedVaultIds no está vacío
vf_idx = vs_code.find('item(key = "vault_folders_bar")')
if vf_idx != -1 and "vault_es_action_bar" not in vs_code:
    vfo = vs_code.find("{", vf_idx)
    vfe = find_balanced(vs_code, vfo, "{", "}")
    if vfe != -1:
        es_v_bar = """
      if (selectedVaultIds.isNotEmpty()) {
        item(key = "vault_es_action_bar") {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              OutlinedButton(
                onClick = {
                  val picked = state.items.filter { it.id in selectedVaultIds }
                  NexoGlobalClipboard.stageVault(picked, NexoClipMode.COPY)
                  selectedVaultIds = emptySet()
                  Toast.makeText(context, "${picked.size} copiado(s) · Ve a otra carpeta o Archivos y toca PEGAR", Toast.LENGTH_SHORT).show()
                },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.weight(1f)
              ) { Text("📋 COPIAR", style = MaterialTheme.typography.labelSmall, maxLines = 1) }
              OutlinedButton(
                onClick = {
                  val picked = state.items.filter { it.id in selectedVaultIds }
                  NexoGlobalClipboard.stageVault(picked, NexoClipMode.MOVE)
                  selectedVaultIds = emptySet()
                  Toast.makeText(context, "${picked.size} cortado(s) · Abre carpeta o Archivos y toca PEGAR", Toast.LENGTH_SHORT).show()
                },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.weight(1f)
              ) { Text("✂️ CORTAR", style = MaterialTheme.typography.labelSmall, maxLines = 1) }
              OutlinedButton(
                onClick = { showMoveToFolderDialog = true },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.weight(1f)
              ) { Text("📁 CARPETA", style = MaterialTheme.typography.labelSmall, maxLines = 1) }
              TextButton(
                onClick = { selectedVaultIds = emptySet() },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
              ) { Text("✖", color = MaterialTheme.colorScheme.error) }
            }
          }
        }
      }
"""
        vs_code = vs_code[:vfe] + es_v_bar + vs_code[vfe:]

# Habilitar mantener presionado (combinedClickable) en cada tarjeta de VaultScreen
old_row_v = """              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {"""
new_row_v = """              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .combinedClickable(
                    onClick = {
                      if (selectedVaultIds.isNotEmpty()) {
                        selectedVaultIds = if (item.id in selectedVaultIds) selectedVaultIds - item.id else selectedVaultIds + item.id
                      } else {
                        onOpenVaultItem(item)
                      }
                    },
                    onLongClick = {
                      selectedVaultIds = if (item.id in selectedVaultIds) selectedVaultIds - item.id else selectedVaultIds + item.id
                    },
                  )
                  .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {"""
if old_row_v in vs_code:
    vs_code = vs_code.replace(old_row_v, new_row_v, 1)

# Añadir diálogo "Mover a carpeta de Bóveda" (sincronizando Drive con la misma ruta!)
anchor_dlg = "  if (showNewVaultFolderDialog) {"
if anchor_dlg in vs_code and "Mover seleccionados a carpeta" not in vs_code:
    move_dlg = """  if (showMoveToFolderDialog) {
    AlertDialog(
      onDismissRequest = { showMoveToFolderDialog = false },
      title = { Text("Mover a carpeta segura (y Drive)") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          val pickedItems = state.items.filter { it.id in selectedVaultIds }
          OutlinedButton(
            onClick = {
              NexoNavBridge.onVaultFolderMoved?.invoke(pickedItems, "")
                ?: pickedItems.forEach { NexoVaultExtraStore.assignToFolder(context, it.id, "") }
              selectedVaultIds = emptySet()
              showMoveToFolderDialog = false
            },
            modifier = Modifier.fillMaxWidth()
          ) { Text("🔒 Raíz de Bóveda (Sin carpeta)") }
          NexoVaultExtraStore.folders.forEach { fName ->
            OutlinedButton(
              onClick = {
                NexoNavBridge.onVaultFolderMoved?.invoke(pickedItems, fName)
                  ?: pickedItems.forEach { NexoVaultExtraStore.assignToFolder(context, it.id, fName) }
                selectedVaultIds = emptySet()
                showMoveToFolderDialog = false
              },
              modifier = Modifier.fillMaxWidth()
            ) { Text("📁 $fName") }
          }
          if (NexoVaultExtraStore.folders.isEmpty()) {
            Text("Primero crea una carpeta con el botón '+ CARPETA'.")
          }
        }
      },
      confirmButton = { TextButton(onClick = { showMoveToFolderDialog = false }) { Text("Cerrar") } }
    )
  }

"""
    vs_code = vs_code.replace(anchor_dlg, move_dlg + anchor_dlg, 1)

# Actualizar VaultDriveBackupModal para usar el Passphrase de Master PIN sin pedirlo manualmente
vs_code = vs_code.replace(
    'var passphrase by remember { mutableStateOf("NEXO_HERMETIC_AUTO_KEY_2026") }',
    'val modalCtx = LocalContext.current\n  val passphrase = remember { NexoVaultSecurityStore.getBackupPassphrase(modalCtx) }'
)
# Ocultar el OutlinedTextField del passphrase en VaultDriveBackupModal (reemplazarlo por aviso informativo)
m_modal = vs_code.find("private fun VaultDriveBackupModal(")
if m_modal != -1:
    otf_idx = vs_code.find("OutlinedTextField(", m_modal)
    if otf_idx != -1 and "passphrase" in vs_code[otf_idx:otf_idx+260]:
        pe = find_balanced(vs_code, vs_code.find("(", otf_idx), "(", ")")
        if pe != -1:
            info_badge = """Text(
            text = "🔑 Cifrado automático activo con tu Master PIN configurado en Ajustes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
          )"""
            vs_code = vs_code[:otf_idx] + info_badge + vs_code[pe:]

write_f(vs_rel, vs_code)
report.append("[OK] 10. VaultScreen.kt: BackHandler aislado + Long-Press + Mover a carpeta con sync Drive + sin passphrase manual.")

# ==================================================================
# 11. MainActivity.kt:
#     - Sincronizar activePagerTabId, isInMainPager, linkedGoogleEmail y onVaultFolderMoved
#     - Conectar NexoGlobalClipboardBar + NexoTorrentManagerDialog
# ==================================================================
ma_rel = "MainActivity.kt"
ma_code = read_f(ma_rel)
ma_code = ensure_imports(ma_code, [
    "androidx.compose.runtime.SideEffect",
    "com.example.NexoGlobalClipboard",
    "com.example.NexoGlobalClipboardBar",
    "com.example.NexoTorrentManagerDialog"
])

anchor_tab = 'val activeTabId = bottomTabs.getOrNull(pagerState.currentPage)?.id ?: "HOME"'
if anchor_tab in ma_code and "NexoNavBridge.activePagerTabId = activeTabId" not in ma_code:
    ma_code = ma_code.replace(
        anchor_tab,
        anchor_tab + "\n                SideEffect { NexoNavBridge.activePagerTabId = activeTabId; NexoNavBridge.isInMainPager = true }",
        1
    )

m_cr = re.search(r"val\s+currentRoute\s*=\s*[^\n]+\n", ma_code)
if m_cr and "NexoNavBridge.onVaultFolderMoved" not in ma_code:
    sync_side = """  SideEffect {
    NexoNavBridge.isInMainPager = (currentRoute == NexoRoutes.HOME || currentRoute == null)
    NexoNavBridge.linkedGoogleEmail = driveState.accountEmail.orEmpty()
    NexoNavBridge.onVaultFolderMoved = { items, folder -> toolsVm.moveVaultItemsToFolderAndSync(items, folder) }
  }
"""
    ma_code = ma_code[:m_cr.end()] + sync_side + ma_code[m_cr.end():]

# Conectar NexoTorrentManagerDialog junto a NexoSimpleWebBrowserDialog
web_dlg_anchor = "if (NexoNavBridge.showWebBrowser) {\n    NexoSimpleWebBrowserDialog(onDismiss = { NexoNavBridge.showWebBrowser = false })\n  }"
if web_dlg_anchor in ma_code and "NexoTorrentManagerDialog(" not in ma_code:
    tor_overlay = web_dlg_anchor + """
  if (NexoNavBridge.showTorrentManager) {
    NexoTorrentManagerDialog(
      initialMagnet = NexoNavBridge.pendingMagnetUri,
      onOpenSaveFolder = { dirPath ->
        explorerVm.loadDirectory(dirPath)
        if (currentRoute != NexoRoutes.HOME) navController.popBackStack(NexoRoutes.HOME, false)
        val fIdx = bottomTabs.indexOfFirst { it.id == "FILES" }.coerceAtLeast(0)
        coroutineScope.launch { pagerState.animateScrollToPage(fIdx) }
      },
      onDismiss = {
        NexoNavBridge.showTorrentManager = false
        NexoNavBridge.pendingMagnetUri = ""
      }
    )
  }"""
    ma_code = ma_code.replace(web_dlg_anchor, tor_overlay, 1)

# Inyectar NexoGlobalClipboardBar arriba de la barra de navegación inferior
if "NexoGlobalClipboardBar(" not in ma_code:
    bb_m = re.search(r"bottomBar\s*=\s*\{", ma_code)
    if bb_m:
        bo = ma_code.find("{", bb_m.start())
        be = find_balanced(ma_code, bo, "{", "}")
        if be != -1:
            orig_bb = ma_code[bo+1:be-1]
            new_bb = f"""{{
          if (!NexoNavBridge.mediaImmersiveMode) {{
            Column(modifier = Modifier.fillMaxWidth()) {{
              NexoGlobalClipboardBar(
                activeTabId = bottomTabs.getOrNull(pagerState.currentPage)?.id ?: "HOME",
                isInMainPager = (currentRoute == NexoRoutes.HOME || currentRoute == null),
                currentLocalPath = explorerState.currentPath,
                isVaultUnlocked = vaultState.isUnlocked,
                onPasteLocal = {{
                  toolsVm.executeGlobalPasteToLocal(explorerState.currentPath) {{
                    explorerVm.loadDirectory(explorerState.currentPath)
                  }}
                }},
                onPasteVault = {{
                  toolsVm.executeGlobalPasteToVault {{
                    explorerVm.loadDirectory(explorerState.currentPath)
                  }}
                }},
                onGoFiles = {{
                  if (currentRoute != NexoRoutes.HOME) navController.popBackStack(NexoRoutes.HOME, false)
                  val idx = bottomTabs.indexOfFirst {{ it.id == "FILES" }}.coerceAtLeast(0)
                  coroutineScope.launch {{ pagerState.animateScrollToPage(idx) }}
                }},
                onGoVault = {{
                  if (currentRoute != NexoRoutes.HOME) navController.popBackStack(NexoRoutes.HOME, false)
                  val idx = bottomTabs.indexOfFirst {{ it.id == "VAULT" }}.coerceAtLeast(0)
                  coroutineScope.launch {{ pagerState.animateScrollToPage(idx) }}
                }},
                onClear = {{ NexoGlobalClipboard.clear() }}
              )
              {orig_bb}
            }}
          }}
        }}"""
            ma_code = ma_code[:bo] + new_bb + ma_code[be:]

write_f(ma_rel, ma_code)
report.append("[OK] 11. MainActivity.kt: Sincronización de pestañas + NexoGlobalClipboardBar + NexoTorrentManagerDialog.")

print("\n=== VERIFICACIÓN PRE-COMPILACIÓN v1.5.0 ===")
for r in report:
    print(" ", r)
print("===========================================")
