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

# ------------------------------------------------------------------
# A. CREAR NexoXmbWaveBackground.kt (Fondo animado muy leve estilo PS3 XMB + Icono dinámico)
# ------------------------------------------------------------------
xmb_kt = r"""package com.example.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun NexoXmbWaveBackground(modifier: Modifier = Modifier) {
    val palette = NexoEvaThemeManager.activePalette
    val primary = palette.primary
    val secondary = palette.secondary
    val transition = rememberInfiniteTransition(label = "nexo_xmb_wave")

    val phase1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )
    val phase2 by transition.animateFloat(
        initialValue = (2.0 * PI).toFloat(),
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 23000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 19000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val steps = 44

        fun drawRibbon(
            baseYRatio: Float,
            amp: Float,
            freq: Float,
            phase: Float,
            ribbonThickness: Float,
            color: Color,
            fillAlpha: Float,
            strokeAlpha: Float
        ) {
            val topPath = Path()
            val fillPath = Path()
            val baseY = h * baseYRatio
            for (i in 0..steps) {
                val frac = i.toFloat() / steps
                val x = w * frac
                val y = baseY + sin(frac * freq * PI.toFloat() + phase) * amp +
                    sin(frac * (freq * 0.55f) * PI.toFloat() - phase * 0.7f) * (amp * 0.45f)
                if (i == 0) {
                    topPath.moveTo(x, y)
                    fillPath.moveTo(x, y)
                } else {
                    topPath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            for (i in steps downTo 0) {
                val frac = i.toFloat() / steps
                val x = w * frac
                val y = baseY + sin(frac * freq * PI.toFloat() + phase) * amp +
                    sin(frac * (freq * 0.55f) * PI.toFloat() - phase * 0.7f) * (amp * 0.45f) +
                    ribbonThickness * (0.65f + 0.35f * sin(frac * PI.toFloat() + phase * 0.5f))
                fillPath.lineTo(x, y)
            }
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = fillAlpha), color.copy(alpha = fillAlpha * 0.15f), Color.Transparent),
                    startY = baseY - amp,
                    endY = baseY + amp + ribbonThickness * 1.4f
                )
            )
            drawPath(
                path = topPath,
                color = color.copy(alpha = strokeAlpha),
                style = Stroke(width = 1.6f)
            )
        }

        // Tres ondas translúcidas muy sutiles estilo PS3 XMB
        drawRibbon(0.36f, h * 0.032f, 2.0f, phase1, h * 0.065f, primary, 0.055f, 0.11f)
        drawRibbon(0.42f, h * 0.042f, 1.6f, phase2 + 1.2f, h * 0.080f, secondary, 0.045f, 0.09f)
        drawRibbon(0.48f, h * 0.026f, 2.5f, phase1 * 1.3f + 2.6f, h * 0.050f, Color.White, 0.032f, 0.07f)

        // Partículas luminosas diminutas flotando sobre la onda
        for (p in 0 until 14) {
            val seed = p * 0.37f
            val px = ((drift + seed) % 1f) * w
            val waveY = h * 0.42f + sin((px / w) * 2.0f * PI.toFloat() + phase1) * (h * 0.04f)
            val py = waveY + sin(phase2 + p * 1.7f) * (h * 0.035f)
            val radius = if (p % 3 == 0) 2.6f else 1.5f
            val pColor = if (p % 2 == 0) primary else Color.White
            drawCircle(
                color = pColor.copy(alpha = 0.16f),
                radius = radius,
                center = Offset(px, py)
            )
        }
    }
}

object NexoDynamicIconManager {
    private val aliasMap = mapOf(
        "EVA-00" to "com.example.LauncherEva00",
        "EVA-01" to "com.example.LauncherEva01",
        "EVA-02" to "com.example.LauncherEva02",
        "EVA-03" to "com.example.LauncherEva03",
        "EVA-04" to "com.example.LauncherEva04",
        "EVA-05" to "com.example.LauncherEva05",
        "EVA-08" to "com.example.LauncherEva08",
        "EVA-13" to "com.example.LauncherEva13"
    )

    fun applyThemeIcon(context: Context, themeId: String) {
        val targetClass = aliasMap[themeId] ?: aliasMap["EVA-01"]!!
        runCatching {
            val pm = context.packageManager
            val pkg = context.packageName
            val targetComp = ComponentName(pkg, targetClass)
            if (pm.getComponentEnabledSetting(targetComp) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return
            }
            // Activar primero el nuevo alias y luego desactivar los demás sin cerrar la app
            pm.setComponentEnabledSetting(
                targetComp,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            aliasMap.values.forEach { cls ->
                if (cls != targetClass) {
                    pm.setComponentEnabledSetting(
                        ComponentName(pkg, cls),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        }
    }
}
"""
write_f("ui/components/NexoXmbWaveBackground.kt", xmb_kt)
report.append("[OK] A. NexoXmbWaveBackground.kt: Ondas sutiles PS3 XMB + NexoDynamicIconManager.")

# ------------------------------------------------------------------
# B. CONECTAR CAMBIO DE ICONO EN NexoEvaThemes.kt
# ------------------------------------------------------------------
eva_rel = "ui/components/NexoEvaThemes.kt"
eva_code = read_f(eva_rel)
if "NexoDynamicIconManager.applyThemeIcon" not in eva_code:
    eva_code = eva_code.replace(
        "context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_THEME, themeName).apply()",
        "context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_THEME, themeName).apply()\n        NexoDynamicIconManager.applyThemeIcon(context, themeName)"
    )
    write_f(eva_rel, eva_code)
    report.append("[OK] B. NexoEvaThemes.kt: Cambio automático de icono vinculado al selector de temas.")

# ------------------------------------------------------------------
# C. ACTUALIZAR AndroidManifest.xml CON LOS 8 <activity-alias> + NUEVO ICONO POR DEFECTO
# ------------------------------------------------------------------
mf_path = "app/src/main/AndroidManifest.xml"
with open(mf_path, "r", encoding="utf-8") as f:
    mf = f.read()

mf = re.sub(r'android:icon="[^"]+"', 'android:icon="@drawable/ic_nexo_eva_01"', mf, count=1)
mf = re.sub(r'android:roundIcon="[^"]+"', 'android:roundIcon="@drawable/ic_nexo_eva_01"', mf, count=1)

if "LauncherEva01" not in mf:
    # Quitar el intent-filter MAIN/LAUNCHER de MainActivity y moverlo a los 8 activity-alias
    launcher_filter_pat = re.compile(
        r'\s*<intent-filter>\s*<action\s+android:name="android\.intent\.action\.MAIN"\s*/>\s*<category\s+android:name="android\.intent\.category\.LAUNCHER"\s*/>\s*</intent-filter>',
        re.S
    )
    mf, n_rem = launcher_filter_pat.subn("", mf, count=1)
    assert n_rem == 1, "No se encontró el intent-filter MAIN/LAUNCHER en AndroidManifest.xml"

    aliases_xml = ""
    for suffix in ["00", "01", "02", "03", "04", "05", "08", "13"]:
        enabled_val = "true" if suffix == "01" else "false"
        aliases_xml += f"""
        <activity-alias
            android:name=".LauncherEva{suffix}"
            android:enabled="{enabled_val}"
            android:exported="true"
            android:icon="@drawable/ic_nexo_eva_{suffix}"
            android:roundIcon="@drawable/ic_nexo_eva_{suffix}"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>"""

    mf = mf.replace("</application>", aliases_xml + "\n    </application>", 1)
    with open(mf_path, "w", encoding="utf-8") as f:
        f.write(mf)
    report.append("[OK] C. AndroidManifest.xml: 8 activity-alias configurados para icono dinámico por tema.")
else:
    with open(mf_path, "w", encoding="utf-8") as f:
        f.write(mf)
    report.append("[OK] C. AndroidManifest.xml: activity-alias ya presentes.")

# ------------------------------------------------------------------
# D. APERTURA INSTANTÁNEA EN BÓVEDA (VaultRepository.kt)
# ------------------------------------------------------------------
vr_rel = "data/repo/VaultRepository.kt"
vr_code = read_f(vr_rel)
if "if (tempOut.exists() && tempOut.length() > 0L) return@runCatching tempOut" not in vr_code:
    vr_code = vr_code.replace(
        'val tempOut = File(previewDir, "${item.id}_$safeName")\n      decryptFileToDest(File(item.vaultFilePath), tempOut)',
        'val tempOut = File(previewDir, "${item.id}_$safeName")\n      if (tempOut.exists() && tempOut.length() > 0L) return@runCatching tempOut\n      decryptFileToDest(File(item.vaultFilePath), tempOut)'
    )
    write_f(vr_rel, vr_code)
    report.append("[OK] D. VaultRepository.kt: Caché de sesión en Bóveda para apertura instantánea (se purga al bloquear).")

# ------------------------------------------------------------------
# E. ToolsAndNetworkViewModel.kt:
#    - Pre-calentamiento silencioso al desbloquear Vault (apertura instantánea)
#    - Respaldo manual incremental (omite lo que ya está en Drive) + progreso % + ETA
# ------------------------------------------------------------------
tn_rel = "ui/viewmodel/ToolsAndNetworkViewModel.kt"
tn_code = read_f(tn_rel)

# 1) Añadir campos de progreso y ETA a VaultUiState
old_vus = """data class VaultUiState(
  val isUnlocked: Boolean = false,
  val items: List<VaultFileItem> = emptyList(),
  val isBusy: Boolean = false,
  val openingItemId: String? = null,
  val driveBackups: List<VaultBackupEntry> = emptyList(),
  val isDriveBusy: Boolean = false,
  val driveStatusMessage: String? = null,
)"""
new_vus = """data class VaultUiState(
  val isUnlocked: Boolean = false,
  val items: List<VaultFileItem> = emptyList(),
  val isBusy: Boolean = false,
  val openingItemId: String? = null,
  val driveBackups: List<VaultBackupEntry> = emptyList(),
  val isDriveBusy: Boolean = false,
  val driveStatusMessage: String? = null,
  val showBackupProgressDialog: Boolean = false,
  val backupProgress: Float = 0f,
  val backupDoneCount: Int = 0,
  val backupTotalCount: Int = 0,
  val backupSkippedCount: Int = 0,
  val backupEtaSeconds: Int = -1,
  val backupCurrentFileName: String = "",
)"""
assert old_vus in tn_code, "No se encontró VaultUiState exacto en ToolsAndNetworkViewModel.kt"
tn_code = tn_code.replace(old_vus, new_vus, 1)

# 2) Pre-calentar caché de vista previa al desbloquear la Bóveda (solo archivos <= 25 MB, en segundo plano)
old_unlock = """      val items = container.vault.listVaultItems()
      _vaultState.value = _vaultState.value.copy(items = items, isBusy = false)"""
new_unlock = """      val items = container.vault.listVaultItems()
      _vaultState.value = _vaultState.value.copy(items = items, isBusy = false)
      launch(Dispatchers.IO) {
        items.filter { it.sizeBytes in 1L..(25L * 1024L * 1024L) }.take(24).forEach { vItem ->
          if (!_vaultState.value.isUnlocked) return@launch
          runCatching { container.vault.decryptToCacheAndOpen(vItem) }
        }
      }"""
if old_unlock in tn_code:
    tn_code = tn_code.replace(old_unlock, new_unlock, 1)

# 3) Mejorar backupVaultToDrive para que sea 100% incremental, muestre progreso (0-100%) + ETA y permita cerrar diálogo
m_bkp = re.search(r"  fun backupVaultToDrive\(", tn_code)
assert m_bkp is not None, "No se encontró backupVaultToDrive en ToolsAndNetworkViewModel.kt"
b_o = tn_code.find("{", m_bkp.start())
b_e = find_balanced(tn_code, b_o, "{", "}")

new_bkp_fn = """  fun dismissVaultBackupProgress() {
    _vaultState.value = _vaultState.value.copy(showBackupProgressDialog = false)
  }

  fun backupVaultToDrive(
    rawPassphrase: String = "",
    selectedIds: Set<String> = emptySet(),
    showProgressModal: Boolean = false,
  ) {
    val passphrase = rawPassphrase.trim().takeIf { it.length >= 4 } ?: NexoVaultSecurityStore.getBackupPassphrase(context)
    viewModelScope.launch {
      _vaultState.value = _vaultState.value.copy(
        isDriveBusy = true,
        showBackupProgressDialog = showProgressModal,
        backupProgress = 0.05f,
        backupDoneCount = 0,
        backupTotalCount = 0,
        backupSkippedCount = 0,
        backupEtaSeconds = -1,
        backupCurrentFileName = "Verificando archivos en Google Drive…",
        driveStatusMessage = "Verificando respaldos en Google Drive…",
      )
      val stageDir = File(context.cacheDir, "vault_drive_stage").apply {
        deleteRecursively()
        mkdirs()
      }
      val startMs = System.currentTimeMillis()
      withDriveTokenRetry { token ->
        runCatching {
          val allVaultItems = container.vault.listVaultItems()
          val targets = if (selectedIds.isEmpty()) allVaultItems else allVaultItems.filter { it.id in selectedIds }
          if (targets.isEmpty()) return@runCatching Triple(0, 0, 0)

          val existingRemote = container.googleDrive.listVaultBackups(token).getOrNull().orEmpty()
          val pendingUpload = mutableListOf<VaultFileItem>()
          var skippedAlreadySynced = 0

          for (item in targets) {
            val folderName = NexoVaultExtraStore.itemFolderMap[item.id].orEmpty()
            val expectedRemoteName = "${item.originalName}.nexo_vault"
            val matchRemote = existingRemote.firstOrNull {
              it.name.equals(expectedRemoteName, ignoreCase = true) ||
                it.name.removeSuffix(".nexo_vault").equals(item.originalName, ignoreCase = true)
            }
            if (matchRemote != null) {
              val remoteFolder = NexoVaultExtraStore.driveBackupFolderMap[matchRemote.id].orEmpty()
              if (remoteFolder != folderName) {
                val targetFolderId = container.googleDrive.ensureVaultSubfolder(token, folderName).getOrThrow()
                container.googleDrive.moveDriveFileToFolder(token, matchRemote.id, targetFolderId)
              }
              skippedAlreadySynced++
            } else {
              pendingUpload.add(item)
            }
          }

          val totalToUpload = pendingUpload.size
          if (totalToUpload == 0) {
            _vaultState.value = _vaultState.value.copy(
              backupProgress = 1f,
              backupDoneCount = 0,
              backupTotalCount = 0,
              backupSkippedCount = skippedAlreadySynced,
              backupEtaSeconds = 0,
              backupCurrentFileName = "Todo respaldado al día",
            )
            return@runCatching Triple(0, skippedAlreadySynced, 0)
          }

          _vaultState.value = _vaultState.value.copy(
            backupTotalCount = totalToUpload,
            backupSkippedCount = skippedAlreadySynced,
            backupProgress = 0.08f,
          )

          var uploaded = 0
          for ((idx, item) in pendingUpload.withIndex()) {
            _vaultState.value = _vaultState.value.copy(
              backupCurrentFileName = item.originalName,
              backupDoneCount = idx,
              backupProgress = ((idx.toFloat() / totalToUpload.toFloat()) * 0.92f + 0.05f).coerceIn(0.05f, 0.98f),
            )
            val folderName = NexoVaultExtraStore.itemFolderMap[item.id].orEmpty()
            val targetFolderId = container.googleDrive.ensureVaultSubfolder(token, folderName).getOrThrow()
            val singleDir = File(stageDir, item.id).apply { mkdirs() }
            val exported = container.vault.exportSelectedVaultToNxv(listOf(item), passphrase, singleDir).getOrThrow()
            for (nxv in exported) {
              val res = container.googleDrive.uploadFile(token, nxv, targetFolderId)
              if (res.isSuccess) uploaded++
            }
            val doneNow = idx + 1
            val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(300L)
            val avgPerItemMs = elapsedMs / doneNow
            val remSec = (((totalToUpload - doneNow) * avgPerItemMs) / 1000L).toInt().coerceAtLeast(0)
            _vaultState.value = _vaultState.value.copy(
              backupDoneCount = doneNow,
              backupProgress = (doneNow.toFloat() / totalToUpload.toFloat()).coerceIn(0f, 1f),
              backupEtaSeconds = remSec,
            )
          }
          Triple(uploaded, skippedAlreadySynced, totalToUpload)
        }
      }.onSuccess { (uploaded, skipped, _) ->
        stageDir.deleteRecursively()
        val msg = when {
          uploaded == 0 && skipped > 0 -> "✓ Todo al día ($skipped ya estaban en la nube)"
          uploaded > 0 && skipped > 0 -> "✓ Respaldo completado: $uploaded nuevo(s) ($skipped omitidos)"
          else -> "✓ Respaldo completado ($uploaded archivo(s))"
        }
        _vaultState.value = _vaultState.value.copy(
          isDriveBusy = false,
          backupProgress = 1f,
          backupEtaSeconds = 0,
          backupCurrentFileName = msg,
          driveStatusMessage = msg,
        )
        refreshVaultDriveBackups()
        emitMessage(msg)
      }.onFailure { err ->
        stageDir.deleteRecursively()
        _vaultState.value = _vaultState.value.copy(
          isDriveBusy = false,
          backupEtaSeconds = -1,
          backupCurrentFileName = "Error: ${err.localizedMessage}",
          driveStatusMessage = "Error en respaldo: ${err.localizedMessage}",
        )
        emitMessage("Error respaldando en Drive: ${err.localizedMessage}")
      }
    }
  }"""

tn_code = tn_code[:m_bkp.start()] + new_bkp_fn + tn_code[b_e:]
write_f(tn_rel, tn_code)
report.append("[OK] E. ToolsAndNetworkViewModel.kt: Respaldo incremental + progreso 0-100% + ETA + pre-caché instantáneo.")

# ------------------------------------------------------------------
# F. VaultScreen.kt:
#    - Respaldo manual directo con ventana compacta de progreso (% + ETA) sin menú de selección
#    - Drag & Drop con la mano (arrastrar archivo sobre carpeta o Raíz)
#    - Botones completos: COPIAR | CORTAR | MOVER | PEGAR | CARPETA
# ------------------------------------------------------------------
vs_rel = "ui/screens/VaultScreen.kt"
vs_code = read_f(vs_rel)
vs_code = ensure_imports(vs_code, [
    "androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress",
    "androidx.compose.foundation.layout.offset",
    "androidx.compose.material3.LinearProgressIndicator",
    "androidx.compose.ui.geometry.Offset",
    "androidx.compose.ui.geometry.Rect",
    "androidx.compose.ui.input.pointer.pointerInput",
    "androidx.compose.ui.layout.boundsInRoot",
    "androidx.compose.ui.layout.onGloballyPositioned",
    "androidx.compose.ui.unit.IntOffset",
    "androidx.compose.ui.zIndex",
    "kotlin.math.roundToInt"
])

# 1) Añadir callbacks opcionales onRunSmartBackup y onDismissBackupProgress a VaultScreen
if "onRunSmartBackup" not in vs_code:
    vs_code = vs_code.replace(
        "  onBackupVaultToDrive: (String) -> Unit = {},",
        "  onBackupVaultToDrive: (String) -> Unit = {},\n  onRunSmartBackup: () -> Unit = { onBackupVaultToDrive(\"\") },\n  onDismissBackupProgress: () -> Unit = {},\n  onPasteVault: () -> Unit = {},"
    )

# 2) Añadir estados de Drag & Drop
if "draggingVaultItem" not in vs_code:
    vs_code = vs_code.replace(
        "  var showMoveToFolderDialog by remember { mutableStateOf(false) }",
        """  var showMoveToFolderDialog by remember { mutableStateOf(false) }
  var draggingVaultItem by remember { mutableStateOf<VaultFileItem?>(null) }
  var dragFingerRoot by remember { mutableStateOf(Offset.Zero) }
  var hoveredDropFolder by remember { mutableStateOf<String?>(null) }
  val folderDropBounds = remember { mutableStateMapOf<String, Rect>() }"""
    )

# 3) Cambiar toque en NexoSecureVaultSummaryPanel para disparar respaldo incremental directo (con ventana compacta de progreso)
vs_code = vs_code.replace(
    "onOpenDriveBackupModal = { showDriveBackupModal = true }",
    "onOpenDriveBackupModal = { onRunSmartBackup() }"
)

# 4) Mejorar vault_folders_bar: zonas de soltado (Drop Targets) para Drag & Drop + botón ☁️ Nube (restaurar)
vf_idx = vs_code.find('item(key = "vault_folders_bar")')
if vf_idx != -1:
    vfo = vs_code.find("{", vf_idx)
    vfe = find_balanced(vs_code, vfo, "{", "}")
    if vfe != -1:
        new_vf_bar = """item(key = "vault_folders_bar") {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = when {
                draggingVaultItem != null -> "✋ SUELTA SOBRE UNA CARPETA"
                NexoVaultExtraStore.activeFolder != null -> "📁 CARPETA: ${NexoVaultExtraStore.activeFolder}"
                else -> "CARPETAS SEGURAS (${NexoVaultExtraStore.folders.size})"
              },
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              if (NexoVaultExtraStore.activeFolder != null) {
                OutlinedButton(
                  onClick = { NexoVaultExtraStore.activeFolder = null },
                  contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) { Text("← TODAS", style = MaterialTheme.typography.labelSmall) }
              }
              OutlinedButton(
                onClick = { showNewVaultFolderDialog = true },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
              ) { Text("+ CARPETA", style = MaterialTheme.typography.labelSmall) }
              OutlinedButton(
                onClick = { onRunSmartBackup() },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
              ) { Text("☁️ SYNC", style = MaterialTheme.typography.labelSmall) }
            }
          }
          if (NexoVaultExtraStore.folders.isNotEmpty() || draggingVaultItem != null) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              if (draggingVaultItem != null || NexoVaultExtraStore.activeFolder != null) {
                val isRootHot = hoveredDropFolder == "__ROOT__"
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = if (isRootHot) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
                  border = BorderStroke(if (isRootHot) 2.dp else 1.dp, if (isRootHot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                  modifier = Modifier
                    .onGloballyPositioned { folderDropBounds["__ROOT__"] = it.boundsInRoot() }
                    .clickable { NexoVaultExtraStore.activeFolder = null }
                ) {
                  Text(
                    text = "🔒 Raíz",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                  )
                }
              }
              NexoVaultExtraStore.folders.take(4).forEach { folderName ->
                val isHot = hoveredDropFolder == folderName
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = if (isHot) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
                  border = BorderStroke(if (isHot) 2.dp else 1.dp, if (isHot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                  modifier = Modifier
                    .onGloballyPositioned { folderDropBounds[folderName] = it.boundsInRoot() }
                    .clickable { NexoVaultExtraStore.activeFolder = folderName }
                ) {
                  Text(
                    text = "📁 $folderName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                  )
                }
              }
            }
          }
        }
      }"""
        vs_code = vs_code[:vf_idx] + new_vf_bar + vs_code[vfe:]

# 5) Actualizar vault_es_action_bar con COPIAR | CORTAR | MOVER | PEGAR | CARPETA
ve_idx = vs_code.find('if (selectedVaultIds.isNotEmpty()) {\n        item(key = "vault_es_action_bar")')
if ve_idx != -1:
    ve_o = vs_code.find("{", ve_idx)
    ve_e = find_balanced(vs_code, ve_o, "{", "}")
    if ve_e != -1:
        new_es_bar = """if (selectedVaultIds.isNotEmpty() || NexoGlobalClipboard.hasItems()) {
        item(key = "vault_es_action_bar") {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              if (selectedVaultIds.isNotEmpty()) {
                OutlinedButton(
                  onClick = {
                    val picked = state.items.filter { it.id in selectedVaultIds }
                    NexoGlobalClipboard.stageVault(picked, NexoClipMode.COPY)
                    selectedVaultIds = emptySet()
                    Toast.makeText(context, "${picked.size} copiado(s)", Toast.LENGTH_SHORT).show()
                  },
                  contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                  modifier = Modifier.weight(1f)
                ) { Text("📋 COPIAR", style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                OutlinedButton(
                  onClick = {
                    val picked = state.items.filter { it.id in selectedVaultIds }
                    NexoGlobalClipboard.stageVault(picked, NexoClipMode.MOVE)
                    selectedVaultIds = emptySet()
                    Toast.makeText(context, "${picked.size} cortado(s)", Toast.LENGTH_SHORT).show()
                  },
                  contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                  modifier = Modifier.weight(1f)
                ) { Text("✂️ CORTAR", style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                OutlinedButton(
                  onClick = { showMoveToFolderDialog = true },
                  contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                  modifier = Modifier.weight(1f)
                ) { Text("↪ MOVER", style = MaterialTheme.typography.labelSmall, maxLines = 1) }
              }
              if (NexoGlobalClipboard.hasItems()) {
                Button(
                  onClick = { onPasteVault() },
                  contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                  modifier = Modifier.weight(1f)
                ) { Text("📥 PEGAR", style = MaterialTheme.typography.labelSmall, maxLines = 1) }
              }
              if (selectedVaultIds.isNotEmpty()) {
                TextButton(
                  onClick = { selectedVaultIds = emptySet() },
                  contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                ) { Text("✖", color = MaterialTheme.colorScheme.error) }
              }
            }
          }
        }
      }"""
        vs_code = vs_code[:ve_idx] + new_es_bar + vs_code[ve_e:]

# 6) Añadir arrastre con la mano (detectDragGesturesAfterLongPress) en cada tarjeta de archivo del Vault
old_item_row = """              Row(
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

new_item_row = """              var itemRootTopLeft by remember(item.id) { mutableStateOf(Offset.Zero) }
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .onGloballyPositioned { coords -> itemRootTopLeft = coords.boundsInRoot().topLeft }
                  .pointerInput(item.id, selectedVaultIds) {
                    var totalDragPx = 0f
                    detectDragGesturesAfterLongPress(
                      onDragStart = { startOffset ->
                        totalDragPx = 0f
                        draggingVaultItem = item
                        dragFingerRoot = itemRootTopLeft + startOffset
                        hoveredDropFolder = folderDropBounds.entries.firstOrNull { it.value.contains(dragFingerRoot) }?.key
                      },
                      onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragPx += dragAmount.getDistance()
                        dragFingerRoot = itemRootTopLeft + change.position
                        hoveredDropFolder = folderDropBounds.entries.firstOrNull { it.value.contains(dragFingerRoot) }?.key
                      },
                      onDragCancel = {
                        draggingVaultItem = null
                        hoveredDropFolder = null
                      },
                      onDragEnd = {
                        val dropTarget = hoveredDropFolder
                        val grabbed = draggingVaultItem
                        draggingVaultItem = null
                        hoveredDropFolder = null
                        if (totalDragPx < 24f) {
                          selectedVaultIds = if (item.id in selectedVaultIds) selectedVaultIds - item.id else selectedVaultIds + item.id
                        } else if (grabbed != null && dropTarget != null) {
                          val folderDest = if (dropTarget == "__ROOT__") "" else dropTarget
                          NexoNavBridge.onVaultFolderMoved?.invoke(listOf(grabbed), folderDest)
                            ?: NexoVaultExtraStore.assignToFolder(context, grabbed.id, folderDest)
                        }
                      }
                    )
                  }
                  .clickable {
                    if (selectedVaultIds.isNotEmpty()) {
                      selectedVaultIds = if (item.id in selectedVaultIds) selectedVaultIds - item.id else selectedVaultIds + item.id
                    } else {
                      onOpenVaultItem(item)
                    }
                  }
                  .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {"""

if old_item_row in vs_code:
    vs_code = vs_code.replace(old_item_row, new_item_row, 1)

# 7) Añadir ventana compacta de progreso de respaldo (% + ETA) y fantasma flotante del archivo arrastrado
anchor_dlg_vs = "  if (showMoveToFolderDialog) {"
compact_progress_and_ghost = """  draggingVaultItem?.let { grabbed ->
    Dialog(
      onDismissRequest = { draggingVaultItem = null; hoveredDropFolder = null },
      properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
          border = BorderStroke(1.5.dp, Color.White),
          modifier = Modifier
            .offset { IntOffset((dragFingerRoot.x - 140f).roundToInt().coerceAtLeast(12), (dragFingerRoot.y - 120f).roundToInt().coerceAtLeast(12)) }
            .zIndex(99f)
        ) {
          Text(
            text = "✋ ${grabbed.originalName.take(26)}" + (hoveredDropFolder?.let { if (it == "__ROOT__") " → 🔒 Raíz" else " → 📁 $it" } ?: ""),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
          )
        }
      }
    }
  }

  if (state.showBackupProgressDialog) {
    val pct = (state.backupProgress * 100f).roundToInt().coerceIn(0, 100)
    val etaLabel = when {
      !state.isDriveBusy && pct >= 100 -> "Finalizado"
      state.backupEtaSeconds < 0 -> "Calculando tiempo…"
      state.backupEtaSeconds < 60 -> "Tiempo restante: ~${state.backupEtaSeconds} s"
      else -> "Tiempo restante: ~${state.backupEtaSeconds / 60}m ${state.backupEtaSeconds % 60}s"
    }
    AlertDialog(
      onDismissRequest = { if (!state.isDriveBusy) onDismissBackupProgress() },
      title = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Respaldo en la Nube", style = MaterialTheme.typography.titleMedium)
          Text("$pct%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          LinearProgressIndicator(
            progress = { state.backupProgress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = state.backupCurrentFileName.ifBlank { "Sincronizando respaldos cifrados…" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          val countInfo = if (state.backupTotalCount > 0) {
            "Subiendo ${state.backupDoneCount} de ${state.backupTotalCount} nuevo(s)" +
              if (state.backupSkippedCount > 0) " · ${state.backupSkippedCount} ya en Drive" else ""
          } else if (state.backupSkippedCount > 0) {
            "${state.backupSkippedCount} archivo(s) ya respaldados en Drive"
          } else ""
          if (countInfo.isNotBlank()) {
            Text(countInfo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
          }
          Text(etaLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      },
      confirmButton = {
        if (!state.isDriveBusy) {
          Button(onClick = onDismissBackupProgress) { Text("Listo") }
        }
      },
      dismissButton = {
        if (!state.isDriveBusy) {
          TextButton(onClick = {
            onDismissBackupProgress()
            showDriveBackupModal = true
          }) { Text("Ver / Restaurar") }
        }
      }
    )
  }

"""
if anchor_dlg_vs in vs_code and "showBackupProgressDialog" not in vs_code:
    vs_code = vs_code.replace(anchor_dlg_vs, compact_progress_and_ghost + anchor_dlg_vs, 1)

write_f(vs_rel, vs_code)
report.append("[OK] F. VaultScreen.kt: Respaldo compacto (% + ETA) + Drag & Drop con la mano + Copiar/Cortar/Mover/Pegar.")

# ------------------------------------------------------------------
# G. MainActivity.kt:
#    - Conectar onRunSmartBackup, onDismissBackupProgress y onPasteVault a VaultScreen
#    - Superponer NexoXmbWaveBackground muy sutil (no bloquea toques) sobre la raíz
# ------------------------------------------------------------------
ma_rel = "MainActivity.kt"
ma_code = read_f(ma_rel)
ma_code = ensure_imports(ma_code, ["com.example.ui.components.NexoXmbWaveBackground"])

# Conectar nuevos callbacks en las invocaciones de VaultScreen
if "onRunSmartBackup =" not in ma_code:
    ma_code = ma_code.replace(
        "onBackupVaultToDrive = { pass -> toolsVm.backupVaultToDrive(pass) },",
        """onBackupVaultToDrive = { pass -> toolsVm.backupVaultToDrive(pass) },
                    onRunSmartBackup = { toolsVm.backupVaultToDrive("", emptySet(), showProgressModal = true) },
                    onDismissBackupProgress = { toolsVm.dismissVaultBackupProgress() },
                    onPasteVault = { toolsVm.executeGlobalPasteToVault { explorerVm.loadDirectory(explorerState.currentPath) } },"""
    )

# Renderizar el fondo animado sutil PS3 XMB en la raíz (usando Box que envuelve ModalNavigationDrawer)
if "NexoXmbWaveBackground(" not in ma_code:
    anchor_mnd = "  ModalNavigationDrawer("
    if anchor_mnd in ma_code:
        # Insertar NexoXmbWaveBackground dentro del contenido del Scaffold justo arriba del NavHost
        nh_idx = ma_code.find("NavHost(\n              navController = navController,")
        if nh_idx != -1:
            # Envolver el contenido del Scaffold o insertar dentro del Box raíz
            ma_code = ma_code.replace(
                "NavHost(\n              navController = navController,",
                "Box(modifier = Modifier.weight(1f)) {\n              NexoXmbWaveBackground(modifier = Modifier.fillMaxSize())\n              NavHost(\n              navController = navController,",
                1
            )
            # Cerrar ese Box justo después de cerrar NavHost(...)
            nh_start = ma_code.find("NavHost(\n              navController = navController,")
            p_o = ma_code.find("(", nh_start)
            p_e = find_balanced(ma_code, p_o, "(", ")")
            b_o = ma_code.find("{", p_e)
            b_e = find_balanced(ma_code, b_o, "{", "}")
            if b_e != -1:
                # Reemplazar modifier = Modifier.weight(1f) dentro de NavHost por Modifier.fillMaxSize()
                nh_block = ma_code[nh_start:b_e].replace("modifier = Modifier.weight(1f),", "modifier = Modifier.fillMaxSize(),", 1)
                ma_code = ma_code[:nh_start] + nh_block + "\n            }" + ma_code[b_e:]
                report.append("[OK] G. MainActivity.kt: Fondo PS3 XMB Wave integrado + callbacks Vault conectados.")
            else:
                report.append("[AVISO] G. No se pudo balancear NavHost en MainActivity.kt.")

write_f(ma_rel, ma_code)

# ------------------------------------------------------------------
# H. OTA SMART PARSER COMPATIBLE CON "2.0b" + VERSIÓN 2.0b (versionCode = 7)
# ------------------------------------------------------------------
ota_rel = "ui/components/NexoVaultAndComicAdvanced.kt"
ota_code = read_f(ota_rel)
ota_code = ota_code.replace(
    '.mapNotNull { it.toIntOrNull() }',
    '.mapNotNull { Regex("^\\\\d+").find(it)?.value?.toIntOrNull() }'
)
ota_code = ota_code.replace('?: "1.4.0"', '?: "2.0b"')
ota_code = ota_code.replace('{ "1.4.0" }', '{ "2.0b" }')
write_f(ota_rel, ota_code)

for rel_ver in ["ui/i18n/AppStrings.kt", "ui/screens/SettingsScreen.kt"]:
    c = read_f(rel_ver)
    c = re.sub(r"NERV HUD Explorer v[0-9\.a-zA-Z]+", "NERV HUD Explorer v2.0b", c)
    write_f(rel_ver, c)

gradle_p = "app/build.gradle.kts"
with open(gradle_p, "r", encoding="utf-8") as f:
    g = f.read()
g = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 7", g)
g = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "2.0b"', g)
with open(gradle_p, "w", encoding="utf-8") as f:
    f.write(g)
report.append("[OK] H. Versión actualizada a 2.0b (versionCode = 7) + parser OTA compatible con sufijo beta.")

print("\n=== VERIFICACIÓN PRE-COMPILACIÓN v2.0b ===")
for r in report:
    print(" ", r)
print("==========================================")
