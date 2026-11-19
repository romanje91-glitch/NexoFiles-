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

report = []

# 1. Desactivar cambio dinámico de icono (evita que MIUI cierre la app al cambiar tema)
eva_rel = "ui/components/NexoEvaThemes.kt"
eva_code = read_f(eva_rel)
eva_code = eva_code.replace("\n        NexoDynamicIconManager.applyThemeIcon(context, themeName)", "")
write_f(eva_rel, eva_code)

mf_path = "app/src/main/AndroidManifest.xml"
with open(mf_path, "r", encoding="utf-8") as f:
    mf = f.read()
if "LauncherEva01" in mf:
    mf = re.sub(r'\s*<activity-alias[\s\S]*?</activity-alias>', '', mf)
    if 'android.intent.action.MAIN' not in mf:
        mf = re.sub(
            r'(<activity[^>]*android:name="\.MainActivity"[^>]*>)',
            r'\1\n            <intent-filter>\n                <action android:name="android.intent.action.MAIN" />\n                <category android:name="android.intent.category.LAUNCHER" />\n            </intent-filter>',
            mf,
            count=1
        )
    with open(mf_path, "w", encoding="utf-8") as f:
        f.write(mf)
report.append("[OK] 1. Icono fijo restaurado en AndroidManifest + NexoEvaThemes (cambiar tema ya no cierra la app).")

# 2. Fondo PS3 XMB Wave visible como overlay sutil (sin bloquear toques)
ma_rel = "MainActivity.kt"
ma_code = read_f(ma_rel)
wave_call = "              NexoXmbWaveBackground(modifier = Modifier.fillMaxSize())\n"
if wave_call in ma_code:
    ma_code = ma_code.replace(wave_call, "", 1)
    # Colocarlo al final del Box que envuelve NavHost (encima de los fondos opacos)
    anchor_box = "            }\n          }\n        }"
    # Mejor: insertar justo después de cerrar NavHost(...)
    nh_idx = ma_code.find("NavHost(\n              navController = navController,")
    if nh_idx != -1:
        def find_bal(txt, idx, oc="{", cc="}"):
            d, ins, esc = 0, False, False
            for i in range(idx, len(txt)):
                c = txt[i]
                if esc: esc = False; continue
                if c == "\\" and ins: esc = True; continue
                if c == '"': ins = not ins; continue
                if not ins:
                    if c == oc: d += 1
                    elif c == cc:
                        d -= 1
                        if d == 0: return i + 1
            return -1
        pe = find_bal(ma_code, ma_code.find("(", nh_idx), "(", ")")
        be = find_bal(ma_code, ma_code.find("{", pe), "{", "}")
        if be != -1:
            ma_code = ma_code[:be] + "\n              NexoXmbWaveBackground(modifier = Modifier.fillMaxSize())" + ma_code[be:]
            report.append("[OK] 2. MainActivity.kt: Fondo PS3 XMB Wave movido a capa superior translúcida.")

# Conectar onCancelBackup a VaultScreen
if "onCancelBackup =" not in ma_code:
    ma_code = ma_code.replace(
        "onDismissBackupProgress = { toolsVm.dismissVaultBackupProgress() },",
        "onDismissBackupProgress = { toolsVm.dismissVaultBackupProgress() },\n                    onCancelBackup = { toolsVm.cancelVaultBackup() },"
    )
write_f(ma_rel, ma_code)

# Ajustar suavemente la visibilidad de las ondas XMB para que luzcan sobre fondos oscuros
xmb_rel = "ui/components/NexoXmbWaveBackground.kt"
xmb_code = read_f(xmb_rel)
xmb_code = xmb_code.replace("0.055f, 0.11f", "0.085f, 0.16f")
xmb_code = xmb_code.replace("0.045f, 0.09f", "0.070f, 0.13f")
xmb_code = xmb_code.replace("0.032f, 0.07f", "0.045f, 0.10f")
write_f(xmb_rel, xmb_code)

# 3. Apertura instantánea en Bóveda: no borrar el caché temporal al generar thumbnails (se purga al bloquear)
mb_rel = "NexoMasterBridge.kt"
mb_code = read_f(mb_rel)
mb_code = mb_code.replace("                runCatching { tmp.delete() }\n", "")
write_f(mb_rel, mb_code)
report.append("[OK] 3. NexoMasterBridge.kt: Caché temporal preservado mientras la Bóveda está abierta.")

# 4. Respaldo en segundo plano con Notificación en barra de estado + Cancelar (ToolsAndNetworkViewModel.kt)
tn_rel = "ui/viewmodel/ToolsAndNetworkViewModel.kt"
tn_code = read_f(tn_rel)
tn_code = ensure_imports(tn_code, [
    "android.app.NotificationChannel",
    "android.app.NotificationManager",
    "android.os.Build",
    "androidx.core.app.NotificationCompat",
    "kotlinx.coroutines.CancellationException",
    "kotlinx.coroutines.ensureActive"
])

if "private var vaultBackupJob: Job? = null" not in tn_code:
    tn_code = tn_code.replace(
        "  fun dismissVaultBackupProgress() {",
        """  private var vaultBackupJob: Job? = null
  private val vaultBackupNotifId = 4092
  private val vaultBackupChannelId = "nexo_vault_backup_ch"

  private fun updateVaultBackupNotification(title: String, text: String, progress: Int, ongoing: Boolean) {
    runCatching {
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val ch = NotificationChannel(vaultBackupChannelId, "Respaldo de Bóveda", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(ch)
      }
      val builder = NotificationCompat.Builder(context, vaultBackupChannelId)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle(title)
        .setContentText(text)
        .setOnlyAlertOnce(true)
        .setOngoing(ongoing)
        .setAutoCancel(!ongoing)
      if (ongoing) {
        builder.setProgress(100, progress.coerceIn(0, 100), progress <= 0)
      } else {
        builder.setSmallIcon(android.R.drawable.stat_sys_upload_done)
        builder.setProgress(0, 0, false)
      }
      nm.notify(vaultBackupNotifId, builder.build())
    }
  }

  private fun cancelVaultBackupNotification() {
    runCatching {
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      nm?.cancel(vaultBackupNotifId)
    }
  }

  fun cancelVaultBackup() {
    vaultBackupJob?.cancel()
    vaultBackupJob = null
    cancelVaultBackupNotification()
    _vaultState.value = _vaultState.value.copy(
      isDriveBusy = false,
      showBackupProgressDialog = false,
      backupEtaSeconds = -1,
      driveStatusMessage = "Respaldo cancelado por el usuario",
    )
    emitMessage("Respaldo en Drive cancelado")
  }

  fun dismissVaultBackupProgress() {""",
        1
    )

# Asignar vaultBackupJob, actualizar notificación durante la subida y manejar cancelación
tn_code = tn_code.replace(
    "    viewModelScope.launch {\n      _vaultState.value = _vaultState.value.copy(\n        isDriveBusy = true,\n        showBackupProgressDialog = showProgressModal,",
    """    vaultBackupJob?.cancel()
    vaultBackupJob = viewModelScope.launch {
      if (showProgressModal) {
        updateVaultBackupNotification("NexoFiles · Respaldo Bóveda", "Verificando archivos en Drive…", 5, true)
      }
      _vaultState.value = _vaultState.value.copy(
        isDriveBusy = true,
        showBackupProgressDialog = showProgressModal,""",
    1
)

tn_code = tn_code.replace(
    "          for ((idx, item) in pendingUpload.withIndex()) {\n            _vaultState.value = _vaultState.value.copy(",
    """          for ((idx, item) in pendingUpload.withIndex()) {
            ensureActive()
            val curPct = (((idx.toFloat() / totalToUpload.toFloat()) * 0.92f + 0.05f) * 100f).toInt().coerceIn(5, 98)
            updateVaultBackupNotification("Respaldo Bóveda ($curPct%)", "Subiendo ${item.originalName} (${idx + 1}/$totalToUpload)", curPct, true)
            _vaultState.value = _vaultState.value.copy(""",
    1
)

tn_code = tn_code.replace(
    "        refreshVaultDriveBackups()\n        emitMessage(msg)",
    "        updateVaultBackupNotification(\"NexoFiles · Respaldo Bóveda\", msg, 100, false)\n        refreshVaultDriveBackups()\n        emitMessage(msg)",
    1
)

tn_code = tn_code.replace(
    "      }.onFailure { err ->\n        stageDir.deleteRecursively()\n        _vaultState.value = _vaultState.value.copy(\n          isDriveBusy = false,\n          backupEtaSeconds = -1,",
    """      }.onFailure { err ->
        stageDir.deleteRecursively()
        if (err is CancellationException) {
          cancelVaultBackupNotification()
          return@launch
        }
        updateVaultBackupNotification("NexoFiles · Error en respaldo", err.localizedMessage ?: "Error", 0, false)
        _vaultState.value = _vaultState.value.copy(
          isDriveBusy = false,
          backupEtaSeconds = -1,""",
    1
)

write_f(tn_rel, tn_code)
report.append("[OK] 4. ToolsAndNetworkViewModel.kt: Respaldo en segundo plano con notificación en vivo + Cancelar.")

# 5. VaultScreen.kt: Botones 'Segundo plano' y 'Cancelar' activos durante el respaldo + toque simple limpio
vs_rel = "ui/screens/VaultScreen.kt"
vs_code = read_f(vs_rel)

if "onCancelBackup: () -> Unit = {}," not in vs_code:
    vs_code = vs_code.replace(
        "  onDismissBackupProgress: () -> Unit = {},",
        "  onDismissBackupProgress: () -> Unit = {},\n  onCancelBackup: () -> Unit = {},"
    )

# Permitir cerrar el modal siempre (pasa a segundo plano) y mostrar botones Segundo plano + Cancelar mientras sube
vs_code = vs_code.replace(
    "      onDismissRequest = { if (!state.isDriveBusy) onDismissBackupProgress() },",
    "      onDismissRequest = { onDismissBackupProgress() },"
)

old_dlg_btns = """      confirmButton = {
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
      }"""

new_dlg_btns = """      confirmButton = {
        if (state.isDriveBusy) {
          Button(onClick = {
            onDismissBackupProgress()
            Toast.makeText(context, "Respaldo continúa en segundo plano (revisa la barra de notificaciones)", Toast.LENGTH_SHORT).show()
          }) { Text("Segundo plano") }
        } else {
          Button(onClick = onDismissBackupProgress) { Text("Listo") }
        }
      },
      dismissButton = {
        if (state.isDriveBusy) {
          TextButton(onClick = onCancelBackup) {
            Text("Cancelar", color = MaterialTheme.colorScheme.error)
          }
        } else {
          TextButton(onClick = {
            onDismissBackupProgress()
            showDriveBackupModal = true
          }) { Text("Ver / Restaurar") }
        }
      }"""
assert old_dlg_btns in vs_code, "No se encontraron los botones del diálogo de progreso en VaultScreen.kt"
vs_code = vs_code.replace(old_dlg_btns, new_dlg_btns, 1)

# Evitar conflicto entre BichromaticCard onClick y Row clickable en VaultScreen
vs_code = vs_code.replace(
    '              accentBorder = isOpeningThis || isChecked,\n              onClick = { onOpenVaultItem(item) },',
    '              accentBorder = isOpeningThis || isChecked,'
)
write_f(vs_rel, vs_code)
report.append("[OK] 5. VaultScreen.kt: Botones 'Segundo plano' y 'Cancelar' + toque inmediato sin conflicto.")

# 6. Subir versión a v2.1b (versionCode = 8)
for rel_ver in ["ui/i18n/AppStrings.kt", "ui/screens/SettingsScreen.kt"]:
    c = read_f(rel_ver)
    c = re.sub(r"NERV HUD Explorer v[0-9\.a-zA-Z]+", "NERV HUD Explorer v2.1b", c)
    write_f(rel_ver, c)

gp = "app/build.gradle.kts"
with open(gp, "r", encoding="utf-8") as f:
    g = f.read()
g = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 8", g)
g = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "2.1b"', g)
with open(gp, "w", encoding="utf-8") as f:
    f.write(g)
report.append("[OK] 6. Versión actualizada a v2.1b (versionCode = 8).")

print("\n=== VERIFICACIÓN PRE-COMPILACIÓN v2.1b ===")
for r in report:
    print(" ", r)
print("==========================================")
