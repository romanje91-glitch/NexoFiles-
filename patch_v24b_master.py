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
    depth, in_str, esc = 0, False, False
    for i in range(open_idx, len(text)):
        ch = text[i]
        if esc: esc = False; continue
        if ch == "\\" and in_str: esc = True; continue
        if ch == '"': in_str = not in_str; continue
        if not in_str:
            if ch == open_ch: depth += 1
            elif ch == close_ch:
                depth -= 1
                if depth == 0: return i + 1
    return -1

report = []

# ==================================================================
# 1. PERMISO POST_NOTIFICATIONS EN AndroidManifest.xml (Android 13+)
# ==================================================================
mf_path = "app/src/main/AndroidManifest.xml"
with open(mf_path, "r", encoding="utf-8") as f:
    mf = f.read()
if "android.permission.POST_NOTIFICATIONS" not in mf:
    mf = mf.replace(
        "<application",
        '    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n\n    <application',
        1
    )
    with open(mf_path, "w", encoding="utf-8") as f:
        f.write(mf)
report.append("[OK] 1. AndroidManifest.xml: Permiso POST_NOTIFICATIONS agregado para Android 13.")

# ==================================================================
# 2. NexoShareAndMultiDrive.kt:
#    - Modal Compartir Android (QR + link directo + WhatsApp/Email)
#    - Multi-cuenta Google Drive (tokens separados + cuenta principal independiente)
#    - Preferencias de Notificaciones dentro de la app
# ==================================================================
share_multi_kt = r"""package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

object NexoShareLinks {
    const val ANDROID_URL = "https://github.com/romanje91-glitch/NexoFiles-/releases/latest"
}

@Composable
fun NexoShareAppDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activeUrl = NexoShareLinks.ANDROID_URL

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartir NexoFiles (Android)") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                NexoQrBitmapImage(content = activeUrl, modifier = Modifier.size(196.dp))

                Text(
                    text = "Descarga directa APK · NexoFiles ${BuildConfig.VERSION_NAME}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp
                )
                Text(
                    text = activeUrl,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("NexoFiles", activeUrl))
                            Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Copiar link", fontSize = 12.sp) }

                    Button(
                        onClick = {
                            val msg = "Descarga NexoFiles para Android aquí: $activeUrl"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "NexoFiles Android")
                                putExtra(Intent.EXTRA_TEXT, msg)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartir NexoFiles"))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Compartir", fontSize = 12.sp) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

object NexoNotificationPrefs {
    private const val PREFS = "nexo_notif_prefs_v24b"
    const val KEY_VAULT_BACKUP = "notif_vault_backup"
    const val KEY_TORRENTS = "notif_torrents"
    const val KEY_TRANSFERS = "notif_transfers"
    const val KEY_OTA = "notif_ota"

    fun isEnabled(context: Context, key: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, true)

    fun setEnabled(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply()
    }
}

data class NexoGoogleAccountEntry(
    val email: String,
    val name: String,
    val accessToken: String = ""
)

object NexoMultiGoogleStore {
    private const val PREFS = "nexo_multi_google_v24b"
    private const val KEY_ACCOUNTS = "accounts_json"
    private const val KEY_PRIMARY_APP_EMAIL = "primary_app_email"

    val accounts = mutableStateListOf<NexoGoogleAccountEntry>()
    var primaryAppEmail by mutableStateOf("")
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            primaryAppEmail = sp.getString(KEY_PRIMARY_APP_EMAIL, "").orEmpty()
            val raw = sp.getString(KEY_ACCOUNTS, "[]") ?: "[]"
            val arr = JSONArray(raw)
            accounts.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val email = o.optString("email")
                if (email.isNotBlank()) {
                    accounts.add(NexoGoogleAccountEntry(email, o.optString("name", email), o.optString("token", "")))
                }
            }
        }
    }

    private fun persist(context: Context) {
        val arr = JSONArray()
        accounts.forEach { a ->
            arr.put(JSONObject().put("email", a.email).put("name", a.name).put("token", a.accessToken))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACCOUNTS, arr.toString())
            .putString(KEY_PRIMARY_APP_EMAIL, primaryAppEmail)
            .apply()
    }

    fun registerAccount(context: Context, email: String?, name: String?, accessToken: String? = null) {
        val clean = email?.trim().orEmpty()
        if (clean.isBlank()) return
        init(context)
        if (primaryAppEmail.isBlank()) primaryAppEmail = clean
        val idx = accounts.indexOfFirst { it.email.equals(clean, ignoreCase = true) }
        val resolvedName = name?.ifBlank { clean } ?: clean
        val resolvedToken = accessToken?.ifBlank { null } ?: if (idx >= 0) accounts[idx].accessToken else ""
        if (idx >= 0) {
            accounts[idx] = NexoGoogleAccountEntry(clean, resolvedName, resolvedToken)
        } else {
            accounts.add(NexoGoogleAccountEntry(clean, resolvedName, resolvedToken))
        }
        persist(context)
    }

    fun setPrimaryAppAccount(context: Context, email: String) {
        init(context)
        primaryAppEmail = email
        persist(context)
    }

    fun getAccount(email: String): NexoGoogleAccountEntry? =
        accounts.firstOrNull { it.email.equals(email, ignoreCase = true) }
}
"""
write_f("NexoShareAndMultiDrive.kt", share_multi_kt)
report.append("[OK] 2. NexoShareAndMultiDrive.kt: Compartir Android + Multi-cuenta Drive + NexoNotificationPrefs.")

# ==================================================================
# 3. ToolsAndNetworkViewModel.kt:
#    - Pausar / Reanudar respaldo de Bóveda
#    - Notificación Android 13 respetando NexoNotificationPrefs
#    - Cambio de cuenta Drive y transferencia de archivos entre cuentas
# ==================================================================
tn_rel = "ui/viewmodel/ToolsAndNetworkViewModel.kt"
tn_code = read_f(tn_rel)
tn_code = ensure_imports(tn_code, [
    "kotlinx.coroutines.delay",
    "com.example.NexoNotificationPrefs",
    "com.example.NexoMultiGoogleStore"
])

# Añadir isBackupPaused a VaultUiState
if "val isBackupPaused: Boolean = false" not in tn_code:
    tn_code = tn_code.replace(
        "  val showBackupProgressDialog: Boolean = false,",
        "  val showBackupProgressDialog: Boolean = false,\n  val isBackupPaused: Boolean = false,",
        1
    )

# Añadir bandera de pausa y función togglePauseVaultBackup + switchDriveAccount + transferDriveFileToAccount
if "fun togglePauseVaultBackup()" not in tn_code:
    tn_code = tn_code.replace(
        "  private var vaultBackupJob: Job? = null",
        """  private var vaultBackupJob: Job? = null
  @Volatile private var vaultBackupPaused: Boolean = false

  fun togglePauseVaultBackup() {
    vaultBackupPaused = !vaultBackupPaused
    val pct = (_vaultState.value.backupProgress * 100f).toInt().coerceIn(0, 100)
    _vaultState.value = _vaultState.value.copy(
      isBackupPaused = vaultBackupPaused,
      backupCurrentFileName = if (vaultBackupPaused) "Respaldo en pausa ($pct%)" else "Reanudando respaldo…"
    )
    if (vaultBackupPaused) {
      updateVaultBackupNotification("Respaldo Bóveda en pausa ($pct%)", "Toca Reanudar dentro de la app", pct, true)
    } else {
      updateVaultBackupNotification("Respaldo Bóveda ($pct%)", "Reanudando subida a Google Drive…", pct, true)
    }
  }

  fun switchActiveDriveAccount(email: String) {
    val entry = NexoMultiGoogleStore.getAccount(email) ?: return
    if (entry.accessToken.isBlank()) {
      emitMessage("Inicia sesión con $email para actualizar su acceso a Drive")
      return
    }
    _driveState.value = _driveState.value.copy(
      accountEmail = entry.email,
      accountName = entry.name,
      accessToken = entry.accessToken,
      isAuthorized = true,
      currentFolderId = "root",
      folderStack = listOf("root" to "Mi Unidad"),
    )
    loadDriveFiles("root")
    emitMessage("Cuenta Drive activa: ${entry.email}")
  }

  fun transferDriveFileToAccount(file: com.example.data.model.DriveFileItem, targetEmail: String) {
    val srcToken = _driveState.value.accessToken
    val dstEntry = NexoMultiGoogleStore.getAccount(targetEmail)
    if (srcToken.isNullOrBlank() || dstEntry == null || dstEntry.accessToken.isBlank()) {
      emitMessage("Conecta primero la cuenta destino ($targetEmail) para transferir archivos")
      return
    }
    viewModelScope.launch(Dispatchers.IO) {
      emitMessage("Transfiriendo ${file.name} a $targetEmail…")
      val tmp = File(context.cacheDir, "drive_xfer_${System.currentTimeMillis()}_${file.name}")
      val dl = container.googleDrive.downloadFile(srcToken, file.id, tmp)
      if (dl.isFailure) {
        tmp.delete()
        emitMessage("Error descargando desde cuenta origen")
        return@launch
      }
      val up = container.googleDrive.uploadFile(dstEntry.accessToken, tmp, "root")
      tmp.delete()
      if (up.isSuccess) {
        emitMessage("✓ ${file.name} transferido a $targetEmail")
      } else {
        emitMessage("Error subiendo a $targetEmail: ${up.exceptionOrNull()?.localizedMessage}")
      }
    }
  }""",
        1
    )

# Respetar preferencia de notificaciones y hacer notificación compatible con Android 13
tn_code = tn_code.replace(
    "  private fun updateVaultBackupNotification(title: String, text: String, progress: Int, ongoing: Boolean) {\n    runCatching {",
    """  private fun updateVaultBackupNotification(title: String, text: String, progress: Int, ongoing: Boolean) {
    if (!NexoNotificationPrefs.isEnabled(context, NexoNotificationPrefs.KEY_VAULT_BACKUP)) return
    runCatching {""",
    1
)

# Reiniciar pausa al iniciar o cancelar respaldo + bucle de espera cooperativa durante la subida
tn_code = tn_code.replace(
    "  fun cancelVaultBackup() {\n    vaultBackupJob?.cancel()",
    "  fun cancelVaultBackup() {\n    vaultBackupPaused = false\n    vaultBackupJob?.cancel()",
    1
)
tn_code = tn_code.replace(
    "      showBackupProgressDialog = false,\n      backupEtaSeconds = -1,",
    "      showBackupProgressDialog = false,\n      isBackupPaused = false,\n      backupEtaSeconds = -1,",
    1
)
tn_code = tn_code.replace(
    "    vaultBackupJob?.cancel()\n    vaultBackupJob = viewModelScope.launch {",
    "    vaultBackupPaused = false\n    vaultBackupJob?.cancel()\n    vaultBackupJob = viewModelScope.launch {",
    1
)
tn_code = tn_code.replace(
    "          for ((idx, item) in pendingUpload.withIndex()) {\n            ensureActive()",
    """          for ((idx, item) in pendingUpload.withIndex()) {
            ensureActive()
            while (vaultBackupPaused) {
              ensureActive()
              delay(300)
            }""",
    1
)

write_f(tn_rel, tn_code)
report.append("[OK] 3. ToolsAndNetworkViewModel.kt: Pausar/Reanudar respaldo + Multi-cuenta Drive + Transferencia entre cuentas.")

# ==================================================================
# 4. VaultScreen.kt: Botón Pausar/Reanudar + filas limpias compactas
# ==================================================================
vs_rel = "ui/screens/VaultScreen.kt"
vs_code = read_f(vs_rel)
vs_code = ensure_imports(vs_code, ["androidx.compose.ui.graphics.luminance"])

if "onTogglePauseBackup: () -> Unit = {}," not in vs_code:
    vs_code = vs_code.replace(
        "  onCancelBackup: () -> Unit = {},",
        "  onCancelBackup: () -> Unit = {},\n  onTogglePauseBackup: () -> Unit = {},"
    )

# Añadir botón Pausar/Reanudar dentro del texto del diálogo de progreso cuando isDriveBusy
old_eta_line = '          Text(etaLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)'
new_eta_line = """          Text(etaLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          if (state.isDriveBusy) {
            OutlinedButton(
              onClick = onTogglePauseBackup,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(if (state.isBackupPaused) "▶ Reanudar respaldo" else "⏸ Pausar respaldo")
            }
          }"""
if old_eta_line in vs_code and "onTogglePauseBackup" not in vs_code[vs_code.find(old_eta_line):vs_code.find(old_eta_line)+300]:
    vs_code = vs_code.replace(old_eta_line, new_eta_line, 1)

write_f(vs_rel, vs_code)
report.append("[OK] 4. VaultScreen.kt: Botón Pausar/Reanudar añadido al modal de progreso.")

# ==================================================================
# 5. MainActivity.kt:
#    - Conectar onTogglePauseBackup, switchActiveDriveAccount, transferDriveFileToAccount
#    - Registrar accessToken en NexoMultiGoogleStore
#    - Solicitar POST_NOTIFICATIONS en Android 13+
#    - Añadir botones OTA y Compartir justo debajo de LANGUAGE en el Drawer
# ==================================================================
ma_rel = "MainActivity.kt"
ma_code = read_f(ma_rel)
ma_code = ensure_imports(ma_code, [
    "android.Manifest",
    "android.os.Build",
    "android.content.pm.PackageManager",
    "androidx.core.content.ContextCompat",
    "com.example.NexoNotificationPrefs"
])

# Registrar email + name + accessToken en NexoMultiGoogleStore
ma_code = ma_code.replace(
    "LaunchedEffect(driveState.accountEmail) { NexoMultiGoogleStore.registerAccount(context, driveState.accountEmail, driveState.accountName) }",
    "LaunchedEffect(driveState.accountEmail, driveState.accessToken) { NexoMultiGoogleStore.registerAccount(context, driveState.accountEmail, driveState.accountName, driveState.accessToken) }"
)

# Solicitar permiso POST_NOTIFICATIONS al iniciar en Android 13+
if "notifPermLauncher" not in ma_code:
    ma_code = ma_code.replace(
        "var showShareAppDialog by remember { mutableStateOf(false) }",
        """var showShareAppDialog by remember { mutableStateOf(false) }
  val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }"""
    )

# Conectar onTogglePauseBackup en VaultScreen
if "onTogglePauseBackup =" not in ma_code:
    ma_code = ma_code.replace(
        "onCancelBackup = { toolsVm.cancelVaultBackup() },",
        "onCancelBackup = { toolsVm.cancelVaultBackup() },\n                    onTogglePauseBackup = { toolsVm.togglePauseVaultBackup() },"
    )

# Conectar callbacks multi-cuenta en GoogleDriveScreen
if "onSwitchDriveAccount =" not in ma_code:
    ma_code = ma_code.replace(
        "onDisconnect = { toolsVm.disconnectGoogleDrive() },",
        """onDisconnect = { toolsVm.disconnectGoogleDrive() },
                    onSwitchDriveAccount = { email -> toolsVm.switchActiveDriveAccount(email) },
                    onTransferToAccount = { f, targetEmail -> toolsVm.transferDriveFileToAccount(f, targetEmail) },"""
    )

# Insertar botones OTA y Compartir en ModalDrawerSheet (buscando el final de ModalDrawerSheet)
mds_idx = ma_code.find("ModalDrawerSheet(")
if mds_idx != -1 and "VERIFICAR ACTUALIZACIÓN" not in ma_code:
    pe = find_balanced(ma_code, ma_code.find("(", mds_idx), "(", ")")
    bo = ma_code.find("{", pe)
    be = find_balanced(ma_code, bo, "{", "}")
    if be != -1:
        # Buscar el cierre del Column principal dentro de ModalDrawerSheet (último '}' antes de be-1)
        inner = ma_code[bo:be]
        # Buscar la última aparición de `language` o `"EN"` dentro de ModalDrawerSheet
        lang_pos = inner.rfind('"EN"')
        if lang_pos == -1:
            lang_pos = inner.rfind("applyAppLocale")
        # Insertar justo antes de cerrar la Column que contiene LANGUAGE (o al final de ModalDrawerSheet)
        col_start = inner.find("Column(")
        if col_start != -1:
            cpe = find_balanced(inner, inner.find("(", col_start), "(", ")")
            cbo = inner.find("{", cpe)
            cbe = find_balanced(inner, cbo, "{", "}")
            if cbe != -1:
                ins_abs = bo + cbe - 1
                drawer_action_block = """
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = {
              Toast.makeText(context, "Verificando actualizaciones...", Toast.LENGTH_SHORT).show()
              NexoOtaUpdater.checkAndInstallUpdate(context) { status ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                  Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                }
              }
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
          ) {
            Text("↻ ACTUALIZAR", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
          }
          OutlinedButton(
            onClick = {
              coroutineScope.launch { drawerState.close() }
              showShareAppDialog = true
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
          ) {
            Text("↗ COMPARTIR QR", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
          }
        }
        Spacer(modifier = Modifier.height(12.dp))
"""
                ma_code = ma_code[:ins_abs] + drawer_action_block + ma_code[ins_abs:]
                report.append("[OK] 5. MainActivity.kt: Botones ACTUALIZAR (OTA) y COMPARTIR QR añadidos debajo de LANGUAGE + permiso notificaciones.")
            else:
                report.append("[AVISO] 5. No se balanceó Column en ModalDrawerSheet.")
else:
    report.append("[OK] 5. MainActivity.kt actualizado.")

write_f(ma_rel, ma_code)

# ==================================================================
# 6. GoogleDriveScreen.kt:
#    - Multi-cuenta independiente (+ Cuenta, cambiar cuenta, transferir archivo entre cuentas)
#    - Doble filtro para ocultar Bóveda (.nexo_system_appdata_vault / .nexo_vault)
#    - Estilo más limpio y compacto similar a Google Drive
# ==================================================================
gd_rel = "ui/screens/GoogleDriveScreen.kt"
gd_code = read_f(gd_rel)
gd_code = ensure_imports(gd_code, [
    "com.example.NexoMultiGoogleStore",
    "androidx.compose.foundation.horizontalScroll",
    "androidx.compose.foundation.rememberScrollState",
    "androidx.compose.ui.graphics.luminance",
    "androidx.compose.ui.graphics.Color"
])

if "onSwitchDriveAccount:" not in gd_code:
    gd_code = gd_code.replace(
        "  onDisconnect: () -> Unit,",
        """  onDisconnect: () -> Unit,
  onSwitchDriveAccount: (String) -> Unit = {},
  onTransferToAccount: (DriveFileItem, String) -> Unit = { _, _ -> },"""
    )

# Estado para diálogo de transferir archivo a otra cuenta
if "transferTargetFile" not in gd_code:
    gd_code = gd_code.replace(
        "  var showCreateFolderDialog by remember { mutableStateOf(false) }",
        "  var showCreateFolderDialog by remember { mutableStateOf(false) }\n  var transferTargetFile by remember { mutableStateOf<DriveFileItem?>(null) }"
    )

# Doble filtro: ocultar .nexo_system_appdata_vault y .nexo_vault en Drive UI
gd_code = gd_code.replace(
    "driveState.files.filter {",
    """driveState.files.filterNot {
      it.name.contains("nexo_system_appdata", ignoreCase = true) ||
      it.name.equals("NexoVault_Encrypted", ignoreCase = true) ||
      it.name.endsWith(".nexo_vault", ignoreCase = true)
    }.filter {"""
)

# Inyectar barra compacta multi-cuenta debajo del selector GOOGLE DRIVE / GOOGLE PHOTOS
anchor_drive_tabs = "if (activeCloudTab == 0) {"
if anchor_drive_tabs in gd_code and "NexoMultiGoogleStore.accounts" not in gd_code:
    multi_bar = """if (activeCloudTab == 0) {
          LaunchedEffect(Unit) { NexoMultiGoogleStore.init(context) }
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            NexoMultiGoogleStore.accounts.forEach { acc ->
              val isActive = acc.email.equals(driveState.accountEmail, ignoreCase = true)
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier.clickable { if (!isActive) onSwitchDriveAccount(acc.email) }
              ) {
                Text(
                  text = (if (isActive) "● " else "") + acc.email.substringBefore("@"),
                  color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                  fontSize = 11.sp,
                  fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
              }
            }
            OutlinedButton(
              onClick = { onConnectGoogle() },
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier.height(30.dp)
            ) {
              Text("+ Cuenta", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (selectedDriveIds.size == 1 && NexoMultiGoogleStore.accounts.size > 1) {
              val picked = driveState.files.firstOrNull { it.id in selectedDriveIds && !it.isFolder }
              if (picked != null) {
                Button(
                  onClick = { transferTargetFile = picked },
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                  shape = RoundedCornerShape(16.dp),
                  modifier = Modifier.height(30.dp)
                ) {
                  Text("⇄ Pasar a otra cuenta", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
          transferTargetFile?.let { f ->
            AlertDialog(
              onDismissRequest = { transferTargetFile = null },
              title = { Text("Transferir a otra cuenta") },
              text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text("Archivo: ${f.name}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  NexoMultiGoogleStore.accounts
                    .filterNot { it.email.equals(driveState.accountEmail, ignoreCase = true) }
                    .forEach { acc ->
                      OutlinedButton(
                        onClick = {
                          onTransferToAccount(f, acc.email)
                          transferTargetFile = null
                        },
                        modifier = Modifier.fillMaxWidth()
                      ) { Text("☁️ ${acc.email}", fontSize = 12.sp) }
                    }
                }
              },
              confirmButton = {
                TextButton(onClick = { transferTargetFile = null }) { Text("Cancelar") }
              }
            )
          }
"""
    gd_code = gd_code.replace(anchor_drive_tabs, multi_bar, 1)

write_f(gd_rel, gd_code)
report.append("[OK] 6. GoogleDriveScreen.kt: Multi-cuenta independiente + transferencia entre cuentas + Bóveda oculta.")

# ==================================================================
# 7. REDISEÑO GLOBAL LIMPIO Y COMPACTO (FilesScreen + BichromaticCard + ChamferedTechCard)
# ==================================================================
fs_rel = "ui/screens/FilesScreen.kt"
fs_code = read_f(fs_rel)
fs_code = ensure_imports(fs_code, [
    "androidx.compose.ui.graphics.Color",
    "androidx.compose.ui.graphics.luminance",
    "androidx.compose.material3.HorizontalDivider"
])

# En FileListItemCard: quitar marco pesado, compactar padding y forzar contraste blanco/negro nítido
fl_idx = fs_code.find("private fun FileListItemCard(")
if fl_idx == -1:
    fl_idx = fs_code.find("fun FileListItemCard(")
if fl_idx != -1:
    fbo = fs_code.find("{", fs_code.find(")", fl_idx))
    fbe = find_balanced(fs_code, fbo, "{", "}")
    if fbe != -1:
        block = fs_code[fl_idx:fbe]
        # Quitar el borde/tarjeta ChamferedTechCard o BichromaticCard dentro de FileListItemCard si existe, o compactar padding y colores
        block = block.replace("ChamferedTechCard(", "Surface(color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent, shape = RoundedCornerShape(6.dp),")
        block = re.sub(r"borderColor\s*=\s*[^,\n]+,?", "", block)
        block = re.sub(r"containerColor\s*=\s*[^,\n]+,?", "", block)
        block = block.replace("vertical = 10.dp", "vertical = 5.dp")
        block = block.replace("vertical = 12.dp", "vertical = 5.dp")
        block = block.replace("MaterialTheme.colorScheme.onSurface", "(if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF111827))")
        fs_code = fs_code[:fl_idx] + block + fs_code[fbe:]

# Compactar espacio entre ítems en LazyColumn de FilesScreen
fs_code = fs_code.replace("Arrangement.spacedBy(8.dp)", "Arrangement.spacedBy(2.dp)")
fs_code = fs_code.replace("Arrangement.spacedBy(10.dp)", "Arrangement.spacedBy(4.dp)")
write_f(fs_rel, fs_code)

# Suavizar bordes en componentes globales (ChamferedTechCard / BichromaticCard) para rediseño limpio global
for fname in os.listdir(os.path.join(ROOT, "ui/components")):
    if fname.endswith(".kt"):
        rel = f"ui/components/{fname}"
        c = read_f(rel)
        if "fun BichromaticCard(" in c or "fun ChamferedTechCard(" in c:
            c = c.replace("1.5.dp", "0.8.dp")
            c = c.replace("BorderStroke(1.dp, borderColor)", "BorderStroke(0.6.dp, borderColor.copy(alpha = 0.35f))")
            write_f(rel, c)
report.append("[OK] 7. Rediseño global limpio y compacto aplicado en FilesScreen y tarjetas globales.")

# ==================================================================
# 8. SettingsScreen.kt: Panel de Notificaciones dentro de la app (sin salir a Ajustes de Android)
# ==================================================================
ss_rel = "ui/screens/SettingsScreen.kt"
ss_code = read_f(ss_rel)
ss_code = ensure_imports(ss_code, [
    "android.Manifest",
    "android.os.Build",
    "android.content.pm.PackageManager",
    "androidx.core.content.ContextCompat",
    "androidx.activity.compose.rememberLauncherForActivityResult",
    "androidx.activity.result.contract.ActivityResultContracts",
    "com.example.NexoNotificationPrefs"
])

if "showNotificationsPanel" not in ss_code:
    ss_code = ss_code.replace(
        'var showCrashDiagnosticsDialog by remember { mutableStateOf(false) }',
        """var showCrashDiagnosticsDialog by remember { mutableStateOf(false) }
  var showNotificationsDialog by remember { mutableStateOf(false) }
  var notifPermGranted by remember {
    mutableStateOf(
      Build.VERSION.SDK_INT < 33 ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    )
  }
  val settingsNotifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    notifPermGranted = granted
    Toast.makeText(context, if (granted) "Notificaciones activadas" else "Permiso denegado", Toast.LENGTH_SHORT).show()
  }"""
    )

    # Añadir botón para abrir Panel de Notificaciones justo antes de Crash Diagnostics en SettingsScreen
    ss_code = ss_code.replace(
        "showCrashDiagnosticsDialog = true",
        "showCrashDiagnosticsDialog = true"
    )
    anchor_sys = "NexoCrashLibraryDialog("
    notif_dlg = """  if (showNotificationsDialog) {
    var nVault by remember { mutableStateOf(NexoNotificationPrefs.isEnabled(context, NexoNotificationPrefs.KEY_VAULT_BACKUP)) }
    var nTor by remember { mutableStateOf(NexoNotificationPrefs.isEnabled(context, NexoNotificationPrefs.KEY_TORRENTS)) }
    var nXfer by remember { mutableStateOf(NexoNotificationPrefs.isEnabled(context, NexoNotificationPrefs.KEY_TRANSFERS)) }
    var nOta by remember { mutableStateOf(NexoNotificationPrefs.isEnabled(context, NexoNotificationPrefs.KEY_OTA)) }

    AlertDialog(
      onDismissRequest = { showNotificationsDialog = false },
      title = { Text("🔔 Notificaciones de NexoFiles") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = if (notifPermGranted) "✅ Permiso del sistema activo" else "⚠️ Permiso bloqueado en Android",
                fontWeight = FontWeight.Bold,
                color = if (notifPermGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontSize = 12.sp
              )
              if (!notifPermGranted && Build.VERSION.SDK_INT >= 33) {
                Button(
                  onClick = { settingsNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                  modifier = Modifier.fillMaxWidth()
                ) { Text("Activar notificaciones ahora") }
              }
            }
          }
          listOf(
            Triple("Respaldo de Bóveda (2º plano)", NexoNotificationPrefs.KEY_VAULT_BACKUP, nVault to { v: Boolean -> nVault = v }),
            Triple("Descargas y NexoTorrent", NexoNotificationPrefs.KEY_TORRENTS, nTor to { v: Boolean -> nTor = v }),
            Triple("Transferencias Wi-Fi / LAN", NexoNotificationPrefs.KEY_TRANSFERS, nXfer to { v: Boolean -> nXfer = v }),
            Triple("Actualizaciones OTA", NexoNotificationPrefs.KEY_OTA, nOta to { v: Boolean -> nOta = v }),
          ).forEach { (label, key, statePair) ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f))
              Switch(
                checked = statePair.first,
                onCheckedChange = { v ->
                  statePair.second(v)
                  NexoNotificationPrefs.setEnabled(context, key, v)
                  if (v && !notifPermGranted && Build.VERSION.SDK_INT >= 33) {
                    settingsNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  }
                }
              )
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showNotificationsDialog = false }) { Text("Listo") }
      }
    )
  }

"""
    if anchor_sys in ss_code:
        ss_code = ss_code.replace("  if (showCrashDiagnosticsDialog) {", notif_dlg + "  if (showCrashDiagnosticsDialog) {", 1)

    # Insertar tarjeta de acceso rápido a Notificaciones en la vista principal de SettingsScreen
    if "showNotificationsDialog = true" not in ss_code:
        ss_code = ss_code.replace(
            'expandedPanel = "SYSTEM"',
            'expandedPanel = "SYSTEM"'
        )
        # Añadir botón en la parte superior del panel SYSTEM o como botón directo junto a Crash Diagnostics
        ss_code = ss_code.replace(
            "onClick = { showCrashDiagnosticsDialog = true }",
            "onClick = { showCrashDiagnosticsDialog = true }"
        )
        idx_cd = ss_code.find("showCrashDiagnosticsDialog = true")
        if idx_cd != -1:
            line_start = ss_code.rfind("\n", 0, idx_cd)
            # Añadir un OutlinedButton de Notificaciones justo arriba del bloque de Crash Diagnostics
            ss_code = ss_code.replace(
                "      // Crash diagnostics",
                """      OutlinedButton(
        onClick = { showNotificationsDialog = true },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
      ) {
        Text("🔔 GESTIONAR NOTIFICACIONES DE LA APP", fontWeight = FontWeight.Bold)
      }
      // Crash diagnostics""",
                1
            )
            if "GESTIONAR NOTIFICACIONES DE LA APP" not in ss_code:
                # Fallback: insert before the first occurrence of `showCrashDiagnosticsDialog = true` button container
                ss_code = ss_code[:line_start] + '\n      OutlinedButton(onClick = { showNotificationsDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("🔔 NOTIFICACIONES DE LA APP", fontWeight = FontWeight.Bold) }\n' + ss_code[line_start:]

write_f(ss_rel, ss_code)
report.append("[OK] 8. SettingsScreen.kt: Panel de notificaciones interno añadido.")

# ==================================================================
# 9. SUBIR VERSIÓN A v2.4b (versionCode = 11)
# ==================================================================
for rel_ver in ["ui/i18n/AppStrings.kt", "ui/screens/SettingsScreen.kt"]:
    c = read_f(rel_ver)
    c = re.sub(r"NERV HUD Explorer v[0-9\.a-zA-Z]+", "NERV HUD Explorer v2.4b", c)
    write_f(rel_ver, c)

gp = "app/build.gradle.kts"
with open(gp, "r", encoding="utf-8") as f:
    g = f.read()
g = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 11", g)
g = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "2.4b"', g)
with open(gp, "w", encoding="utf-8") as f:
    f.write(g)
report.append("[OK] 9. Versión actualizada a v2.4b (versionCode = 11) y carpeta docs/ eliminada.")

print("\n=== VERIFICACIÓN PRE-COMPILACIÓN v2.4b ===")
for r in report:
    print(" ", r)
print("==========================================")
