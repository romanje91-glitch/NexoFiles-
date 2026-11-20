import os, re

ROOT = "app/src/main/java/com/example"

def rf(rel): return open(os.path.join(ROOT, rel), encoding="utf-8").read()
def wf(rel, txt): open(os.path.join(ROOT, rel), "w", encoding="utf-8").write(txt)
def imp(c, ls):
    m = re.search(r"^package\s+[\w\.]+\s*\n", c, re.M)
    a = "".join(f"import {i}\n" for i in ls if f"import {i}" not in c)
    return c if not a else c[:m.end()] + a + c[m.end():]

# ------------------------------------------------------------------
# 1. CREAR NexoVaultPhase2.kt (FeatureFlags, DeepLink, Dialogo Nube, Campo AT)
# ------------------------------------------------------------------
phase2_kt = r"""package com.example

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

object NexoFeatureFlags {
    private const val PREFS = "nexo_feature_flags_v3"
    private const val KEY_VAULT = "vault_enabled"
    private const val KEY_DRIVE = "drive_enabled"

    var vaultEnabled by mutableStateOf(true)
    var driveEnabled by mutableStateOf(true)
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        vaultEnabled = sp.getBoolean(KEY_VAULT, true)
        driveEnabled = sp.getBoolean(KEY_DRIVE, true)
    }

    fun setVault(context: Context, enabled: Boolean) {
        vaultEnabled = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_VAULT, enabled).apply()
    }

    fun setDrive(context: Context, enabled: Boolean) {
        driveEnabled = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DRIVE, enabled).apply()
    }
}

object NexoVaultDeepLink {
    var pendingOpenVault by mutableStateOf(false)
}

@Composable
fun NexoVaultCloudActionDialog(
    fileName: String,
    isBackedUpInCloud: Boolean,
    isEs: Boolean,
    onUploadToCloud: () -> Unit,
    onRestoreToDevice: () -> Unit,
    onDeleteLocalOnly: () -> Unit,
    onDeleteCloudOnly: () -> Unit,
    onDeleteBoth: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (isBackedUpInCloud) Icons.Rounded.CloudDownload else Icons.Rounded.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isEs) "Estado y acciones" else "Status & Actions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fileName, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when {
                            isBackedUpInCloud && isEs -> "☁️ Respaldado en la nube y guardado en el teléfono"
                            isBackedUpInCloud -> "☁️ Backed up in cloud & stored on device"
                            isEs -> "📱 Solo en el teléfono (sin respaldo en la nube)"
                            else -> "📱 Device only (not backed up to cloud)"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                if (!isBackedUpInCloud) {
                    Button(
                        onClick = { onUploadToCloud(); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isEs) "Subir a la nube ahora" else "Upload to cloud now", fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = { onRestoreToDevice(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isEs) "Restaurar a galería / archivos" else "Restore to device", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { onDeleteLocalOnly(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isEs) "Eliminar solo del teléfono" else "Delete from device only", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { onDeleteCloudOnly(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isEs) "Eliminar solo de la nube" else "Delete from cloud only",
                        fontSize = 12.sp,
                        color = if (isBackedUpInCloud) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { onDeleteBoth(); onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isEs) "Eliminar de ambos (teléfono y nube)" else "Delete from both", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (isEs) "Cerrar" else "Close") }
        }
    )
}

@Composable
fun NexoAtFieldVaultGate(
    isEs: Boolean,
    isDriveAuthorized: Boolean,
    onActivateVault: () -> Unit,
    onConnectDrive: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(180.dp)
                .clickable {
                    onActivateVault()
                    if (!isDriveAuthorized) onConnectDrive()
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radii = listOf(size.minDimension * 0.46f, size.minDimension * 0.34f, size.minDimension * 0.22f)
                val colors = listOf(Color(0xFFFF6D00), Color(0xFFFF9100), Color(0xFFFFAB40))
                radii.forEachIndexed { idx, r ->
                    val path = Path()
                    for (k in 0..7) {
                        val ang = Math.toRadians((k * 45.0) - 22.5)
                        val x = cx + (r * cos(ang)).toFloat()
                        val y = cy + (r * sin(ang)).toFloat()
                        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(path, color = colors[idx], style = Stroke(width = (3.5f - idx * 0.7f).dp.toPx()))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = null,
                    tint = Color(0xFFFF9100),
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = if (isEs) "ACTIVAR" else "ACTIVATE",
                    color = Color(0xFFFFAB40),
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = if (isEs) "BÓVEDA DESACTIVADA" else "VAULT DISABLED",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isEs) "Toca el escudo para activar la Bóveda Privada y vincular tu cuenta." else "Tap the field to enable Private Vault and link your account.",
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
"""
wf("NexoVaultPhase2.kt", phase2_kt)
print("[OK] 1. NexoVaultPhase2.kt creado.")

# ------------------------------------------------------------------
# 2. ToolsAndNetworkViewModel.kt:
#    - deleteVaultCloudBackup(item) con alerta real si no existe en nube
#    - Refrescar driveBackups al cancelar respaldo (mostrar conteo real)
#    - PendingIntent con extra "nexo_open_vault" para abrir directo la Bóveda
# ------------------------------------------------------------------
tn_rel = "ui/viewmodel/ToolsAndNetworkViewModel.kt"
tn = rf(tn_rel)

# Detectar nombre real del método delete en GoogleDriveRepository
gd_repo_path = os.path.join(ROOT, "data/repo/GoogleDriveRepository.kt")
del_fn = "deleteFile"
if os.path.exists(gd_repo_path):
    gdr = open(gd_repo_path, encoding="utf-8").read()
    m_del = re.search(r"suspend\s+fun\s+(delete\w+)\s*\(", gdr)
    if m_del:
        del_fn = m_del.group(1)

if "fun deleteVaultCloudBackup(" not in tn:
    add_fn = f"""
  fun deleteVaultCloudBackup(item: com.example.data.model.VaultFileItem) {{
    val match = _vaultState.value.driveBackups.firstOrNull {{ b ->
      val clean = b.originalName.substringAfterLast(" / ").removeSuffix(".nexo_vault")
      clean.equals(item.originalName, ignoreCase = true) ||
        b.driveFile.name.removeSuffix(".nexo_vault").equals(item.originalName, ignoreCase = true) ||
        b.driveFile.name.contains(item.id, ignoreCase = true)
    }}
    if (match == null) {{
      emitMessage("No existe respaldo en la nube para ${{item.originalName}}")
      return
    }}
    viewModelScope.launch {{
      withDriveTokenRetry {{ token -> container.googleDrive.{del_fn}(token, match.driveFile.id) }}
        .onSuccess {{
          emitMessage("Respaldo eliminado de la nube: ${{item.originalName}}")
          refreshVaultDriveBackups()
        }}
        .onFailure {{ err ->
          emitMessage("Error eliminando en la nube: ${{err.localizedMessage}}")
        }}
    }}
  }}
"""
    tn = tn.replace("  fun cancelVaultBackup() {", add_fn + "\n  fun cancelVaultBackup() {", 1)

# Al cancelar respaldo, refrescar conteo real en Drive
if "refreshVaultDriveBackups()" not in tn[tn.find("fun cancelVaultBackup()"):tn.find("fun cancelVaultBackup()")+450]:
    tn = tn.replace(
        "  fun cancelVaultBackup() {\n    vaultBackupPaused = false\n    vaultBackupJob?.cancel()",
        "  fun cancelVaultBackup() {\n    vaultBackupPaused = false\n    vaultBackupJob?.cancel()\n    refreshVaultDriveBackups()",
        1
    )

# PendingIntent con extra para abrir la Bóveda directo al tocar notificación
tn = tn.replace(
    'Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)',
    'Intent(context, MainActivity::class.java).putExtra("nexo_open_vault", true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)'
)
wf(tn_rel, tn)
print(f"[OK] 2. ToolsAndNetworkViewModel.kt actualizado (delete={del_fn}, conteo real al cancelar, deep-link notificación).")

# ------------------------------------------------------------------
# 3. VaultScreen.kt:
#    - Reemplazar reloj + basurero por icono único (nube/teléfono) + menú inteligente
#    - Banner interactivo si hay respaldo corriendo para volver al progreso
#    - Puerta Campo AT cuando la Bóveda está desactivada
# ------------------------------------------------------------------
vs_rel = "ui/screens/VaultScreen.kt"
vs = rf(vs_rel)
vs = imp(vs, [
    "com.example.NexoFeatureFlags",
    "com.example.NexoVaultCloudActionDialog",
    "com.example.NexoAtFieldVaultGate",
    "androidx.compose.material.icons.rounded.CloudDownload",
    "androidx.compose.material.icons.rounded.PhoneAndroid",
    "androidx.compose.material.icons.rounded.CloudSync"
])

if "onDeleteVaultCloudBackup:" not in vs:
    vs = vs.replace(
        "  onDeleteVaultItem: (VaultFileItem) -> Unit,",
        "  onDeleteVaultItem: (VaultFileItem) -> Unit,\n  onDeleteVaultCloudBackup: (VaultFileItem) -> Unit = {},"
    )

if "pendingCloudActionItem" not in vs:
    vs = vs.replace(
        "  var pendingSelectiveDeleteItem by remember { mutableStateOf<VaultFileItem?>(null) }",
        "  var pendingSelectiveDeleteItem by remember { mutableStateOf<VaultFileItem?>(null) }\n  var pendingCloudActionItem by remember { mutableStateOf<Pair<VaultFileItem, Boolean>?>(null) }"
    )

# Puerta Campo AT si la Bóveda está desactivada
if "NexoAtFieldVaultGate(" not in vs:
    vs = vs.replace(
        "  LaunchedEffect(Unit) { NexoVaultExtraStore.init(context) }",
        """  LaunchedEffect(Unit) {
    NexoVaultExtraStore.init(context)
    NexoFeatureFlags.init(context)
    if (isDriveAuthorized && state.driveBackups.isEmpty() && !state.isDriveBusy) {
      onRefreshDriveBackups()
    }
  }
  val isEsVault = strings.languageTitle.contains("Idioma", ignoreCase = true)
  if (!NexoFeatureFlags.vaultEnabled) {
    NexoAtFieldVaultGate(
      isEs = isEsVault,
      isDriveAuthorized = isDriveAuthorized,
      onActivateVault = { NexoFeatureFlags.setVault(context, true) },
      onConnectDrive = onConnectDrive
    )
    return
  }"""
    )

# Reemplazar los dos IconButton (Restore + DeleteForever) por 1 solo botón inteligente de nube/teléfono
old_pair_pat = re.compile(
    r'IconButton\(\s*onClick\s*=\s*\{\s*onRestoreVaultItem\(item\)\s*\},[\s\S]*?Icon\(NexoIcons\.DeleteForever[\s\S]*?\}\s*\}',
    re.S
)
new_single_btn = """val isInCloud = remember(state.driveBackups, item.id, item.originalName) {
                  state.driveBackups.any { b ->
                    val clean = b.originalName.substringAfterLast(" / ").removeSuffix(".nexo_vault")
                    clean.equals(item.originalName, ignoreCase = true) ||
                      b.driveFile.name.removeSuffix(".nexo_vault").equals(item.originalName, ignoreCase = true) ||
                      b.driveFile.name.contains(item.id, ignoreCase = true)
                  }
                }
                IconButton(
                  onClick = { pendingCloudActionItem = item to isInCloud },
                  modifier = Modifier.testTag("vault_cloud_action_${item.id}"),
                ) {
                  Icon(
                    imageVector = if (isInCloud) Icons.Rounded.CloudDownload else Icons.Rounded.PhoneAndroid,
                    contentDescription = if (isInCloud) "Cloud & Device" else "Device only",
                    tint = if (isInCloud) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }"""

vs, n_rep = old_pair_pat.subn(new_single_btn, vs, count=1)
assert n_rep == 1, "No se encontró el par de botones Restore + DeleteForever en VaultScreen.kt"

# Mostrar el diálogo inteligente al tocar el icono único + banner de progreso en vivo
if "NexoVaultCloudActionDialog(" not in vs:
    vs = vs.replace(
        "  if (pendingSelectiveDeleteItem != null) {",
        """  pendingCloudActionItem?.let { (target, inCloud) ->
    NexoVaultCloudActionDialog(
      fileName = target.originalName,
      isBackedUpInCloud = inCloud,
      isEs = isEsVault,
      onUploadToCloud = {
        if (state.isDriveBusy) onReopenBackupProgress()
        else onBackupSelectedVaultToDrive("", setOf(target.id))
      },
      onRestoreToDevice = { onRestoreVaultItem(target) },
      onDeleteLocalOnly = { onDeleteVaultItem(target) },
      onDeleteCloudOnly = { onDeleteVaultCloudBackup(target) },
      onDeleteBoth = {
        if (inCloud) onDeleteVaultCloudBackup(target)
        onDeleteVaultItem(target)
      },
      onDismiss = { pendingCloudActionItem = null }
    )
  }
  if (pendingSelectiveDeleteItem != null) {""",
        1
    )

wf(vs_rel, vs)
print("[OK] 3. VaultScreen.kt actualizado (icono único nube/teléfono + menú inteligente + Campo AT).")

# ------------------------------------------------------------------
# 4. MainActivity.kt: Conectar deep-link de notificación a Bóveda + onDeleteVaultCloudBackup
# ------------------------------------------------------------------
ma_rel = "MainActivity.kt"
ma = rf(ma_rel)
ma = imp(ma, ["com.example.NexoFeatureFlags", "com.example.NexoVaultDeepLink"])

if "nexo_open_vault" not in ma:
    ma = ma.replace(
        "NexoMasterDirs.ensureAll()",
        'NexoMasterDirs.ensureAll()\n    NexoFeatureFlags.init(applicationContext)\n    if (intent?.getBooleanExtra("nexo_open_vault", false) == true) { NexoVaultDeepLink.pendingOpenVault = true }',
        1
    )

if "override fun onNewIntent(" not in ma:
    ma = ma.replace(
        "  override fun onCreate(",
        """  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    if (intent.getBooleanExtra("nexo_open_vault", false)) {
      NexoVaultDeepLink.pendingOpenVault = true
    }
  }

  override fun onCreate(""",
        1
    )

if "onDeleteVaultCloudBackup =" not in ma:
    ma = ma.replace(
        "onDeleteVaultItem = { toolsVm.deleteVaultItem(it) },",
        "onDeleteVaultItem = { toolsVm.deleteVaultItem(it) },\n                    onDeleteVaultCloudBackup = { toolsVm.deleteVaultCloudBackup(it) },"
    )

if "NexoVaultDeepLink.pendingOpenVault" not in ma[ma.find("val notifPermLauncher"):]:
    ma = ma.replace(
        "  LaunchedEffect(driveState.accountEmail, driveState.accessToken)",
        """  LaunchedEffect(NexoVaultDeepLink.pendingOpenVault) {
    if (NexoVaultDeepLink.pendingOpenVault) {
      NexoVaultDeepLink.pendingOpenVault = false
      navController.navigate(NexoRoutes.VAULT) { launchSingleTop = true }
      if (vaultState.isDriveBusy) toolsVm.reopenVaultBackupProgress()
    }
  }
  LaunchedEffect(driveState.accountEmail, driveState.accessToken)""",
        1
    )

wf(ma_rel, ma)
print("[OK] 4. MainActivity.kt conectado (deep-link notificación -> Bóveda + delete cloud backup).")

# ------------------------------------------------------------------
# 5. SettingsScreen.kt: Switches de Bóveda, Drive y Cierre Boca Abajo
# ------------------------------------------------------------------
ss_rel = "ui/screens/SettingsScreen.kt"
ss = rf(ss_rel)
ss = imp(ss, ["com.example.NexoFeatureFlags", "com.example.NexoVaultFlipGuard"])

if "NexoFeatureFlags.vaultEnabled" not in ss:
    anchor_bio = "onVaultBiometricEnabledChange"
    idx = ss.find(anchor_bio)
    if idx != -1:
        # Insertar switches justo después del bloque de Acceso biométrico (buscar el cierre del Row o Switch)
        end_line = ss.find("\n", ss.find(")", idx))
        if end_line != -1:
            switches_block = """
          val isEsSet = strings.languageTitle.contains("Idioma", ignoreCase = true)
          var flipEnabled by remember { mutableStateOf(NexoVaultFlipGuard.isEnabled(context)) }
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(if (isEsSet) "Habilitar Bóveda Privada" else "Enable Private Vault", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              Text(if (isEsSet) "Activa o desactiva la Bóveda en la app" else "Turn Vault module on or off", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = NexoFeatureFlags.vaultEnabled, onCheckedChange = { NexoFeatureFlags.setVault(context, it) })
          }
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(if (isEsSet) "Habilitar Google Drive" else "Enable Google Drive", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              Text(if (isEsSet) "Activa o desactiva la integración con Drive" else "Turn Google Drive module on or off", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = NexoFeatureFlags.driveEnabled, onCheckedChange = { NexoFeatureFlags.setDrive(context, it) })
          }
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(if (isEsSet) "Cierre rápido boca abajo" else "Flip-to-Close Vault", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              Text(if (isEsSet) "Sella la Bóveda y abre WhatsApp al voltear el teléfono" else "Locks Vault and opens WhatsApp when placed face down", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = flipEnabled, onCheckedChange = { flipEnabled = it; NexoVaultFlipGuard.setEnabled(context, it) })
          }
"""
            # Buscar la línea siguiente que cierre el contenedor inmediato del switch biométrico
            close_pos = ss.find(")", end_line + 1)
            next_nl = ss.find("\n", close_pos) if close_pos != -1 else end_line
            ss = ss[:next_nl+1] + switches_block + ss[next_nl+1:]
            wf(ss_rel, ss)
            print("[OK] 5. SettingsScreen.kt: Switches de Bóveda, Drive y Flip-to-Close añadidos.")
        else:
            print("[AVISO] 5. No se encontró fin de línea para switches en SettingsScreen.kt.")
