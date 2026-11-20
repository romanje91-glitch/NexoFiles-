import os, re

ROOT = "app/src/main/java/com/example"

def rf(rel): return open(os.path.join(ROOT, rel), encoding="utf-8").read()
def wf(rel, txt): open(os.path.join(ROOT, rel), "w", encoding="utf-8").write(txt)
def imp(c, ls):
    m = re.search(r"^package\s+[\w\.]+\s*\n", c, re.M)
    a = "".join(f"import {i}\n" for i in ls if f"import {i}" not in c)
    return c if not a else c[:m.end()] + a + c[m.end():]

# ------------------------------------------------------------------
# 1. CREAR NexoMasterDirsAndSecurity.kt:
#    - Carpeta Máster /storage/emulated/0/NexoFiles/{Received,Torrents,BrowserDownloads,Exports}
#    - Sensor Boca Abajo (Flip-to-Close) para la Bóveda
# ------------------------------------------------------------------
master_kt = r"""package com.example

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Environment
import java.io.File

object NexoMasterDirs {
    val rootDir: File
        get() = File(Environment.getExternalStorageDirectory(), "NexoFiles").apply { if (!exists()) mkdirs() }
    val receivedDir: File
        get() = File(rootDir, "Received").apply { if (!exists()) mkdirs() }
    val torrentsDir: File
        get() = File(rootDir, "Torrents").apply { if (!exists()) mkdirs() }
    val browserDownloadsDir: File
        get() = File(rootDir, "BrowserDownloads").apply { if (!exists()) mkdirs() }
    val exportsDir: File
        get() = File(rootDir, "Exports").apply { if (!exists()) mkdirs() }

    fun ensureAll() {
        runCatching {
            rootDir; receivedDir; torrentsDir; browserDownloadsDir; exportsDir
        }
    }
}

object NexoVaultFlipGuard {
    private const val PREFS = "nexo_vault_flip_prefs"
    private const val KEY_ENABLED = "flip_close_enabled"
    private const val KEY_TARGET_PKG = "flip_target_pkg"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getTargetPackage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TARGET_PKG, "com.whatsapp") ?: "com.whatsapp"

    fun setTargetPackage(context: Context, pkg: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TARGET_PKG, pkg).apply()
    }

    fun registerListener(context: Context, onFaceDown: () -> Unit): () -> Unit {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return {}
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return {}
        var wasFaceUp = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val z = event.values[2]
                if (z > 6.5f) wasFaceUp = true
                if (wasFaceUp && z < -7.5f) {
                    wasFaceUp = false
                    onFaceDown()
                    val pkg = getTargetPackage(context)
                    val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(launch) }
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        return { runCatching { sm.unregisterListener(listener) } }
    }
}
"""
wf("NexoMasterDirsAndSecurity.kt", master_kt)
print("[OK] 1. NexoMasterDirsAndSecurity.kt creado (Carpeta Máster + Flip-to-Close).")

# ------------------------------------------------------------------
# 2. TECLADO NUMÉRICO PARA MASTER PIN (ESPAÑOL E INGLÉS)
# ------------------------------------------------------------------
pin_fixed = 0
for dirpath, _, files in os.walk(ROOT):
    for fn in files:
        if not fn.endswith(".kt"): continue
        p = os.path.join(dirpath, fn)
        c = open(p, encoding="utf-8").read()
        if ("Master PIN" in c or "PIN Maestro" in c or "pinDraft" in c) and "OutlinedTextField(" in c:
            c = imp(c, ["androidx.compose.foundation.text.KeyboardOptions", "androidx.compose.ui.text.input.KeyboardType"])
            new_c = re.sub(
                r'(label\s*=\s*\{\s*Text\([^)]*(?:PIN|Pin)[^)]*\)\s*\},)',
                r'\1\n            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),',
                c
            )
            if new_c != c:
                # Evitar duplicados si ya tenía keyboardOptions justo debajo
                new_c = new_c.replace(
                    "keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),\n            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),",
                    "keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),"
                )
                open(p, "w", encoding="utf-8").write(new_c)
                pin_fixed += 1
print(f"[OK] 2. Teclado numérico forzado en {pin_fixed} archivo(s) de PIN Maestro.")

# ------------------------------------------------------------------
# 3. LIMPIEZA DE TEXTOS TÉCNICOS (AES-256-GCM, API v3, iPhone, Credential Manager)
# ------------------------------------------------------------------
replacements = [
    ("AES-256-GCM encryption · Hermetic internal viewer", "Almacenamiento privado protegido"),
    ("Cifrado AES-256-GCM · Visor interno hermético", "Almacenamiento privado protegido"),
    ("GOOGLE DRIVE API V3 · AUTO-BACKUP", "GOOGLE DRIVE · RESPALDO"),
    ("Credential Manager + AuthorizationClient (Drive API v3).", "Conecta tu cuenta de Google para gestionar tus archivos en la nube."),
    ("ACCESO REMOTO EFÍMERO WEB / IPHONE", "ACCESO REMOTO VAULT"),
    ("EPHEMERAL WEB / IPHONE REMOTE ACCESS", "VAULT REMOTE ACCESS"),
    ("para ver y descifrar archivos en Safari (iPhone) o navegador sin subir la clave al servidor.", "para acceder a tus archivos desde un navegador en tu red local."),
    ("MODO HERMÉTICO · VISOR INTERNO DIRECTO", "PROTEGIDO"),
    ("HERMETIC MODE · DIRECT INTERNAL VIEWER", "PROTECTED"),
]
cleaned = 0
for dirpath, _, files in os.walk(ROOT):
    for fn in files:
        if not fn.endswith(".kt"): continue
        p = os.path.join(dirpath, fn)
        c = open(p, encoding="utf-8").read()
        orig = c
        for old, new in replacements:
            c = c.replace(old, new)
        if c != orig:
            open(p, "w", encoding="utf-8").write(c)
            cleaned += 1
print(f"[OK] 3. Textos técnicos y menciones a iPhone limpiados en {cleaned} archivo(s).")

# ------------------------------------------------------------------
# 4. FILES SCREEN: REDISEÑO COMPACTO REAL (11-14 FILAS, SIN RECUADROS, CONTRASTE BLANCO/NEGRO)
# ------------------------------------------------------------------
fs = rf("ui/screens/FilesScreen.kt")
fs = imp(fs, ["androidx.compose.ui.graphics.Color", "androidx.compose.ui.graphics.luminance", "androidx.compose.material3.HorizontalDivider"])
fl = fs.find("fun FileListItemCard(")
if fl != -1:
    head, tail = fs[:fl], fs[fl:]
    # Quitar cualquier tarjeta con borde y usar filas planas y compactas
    tail = re.sub(r"Surface\([^)]*RoundedCornerShape\([^)]*\)[^)]*,", "Column(modifier = Modifier.fillMaxWidth().background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent),", tail, count=1)
    tail = tail.replace("size(36.dp)", "size(30.dp)").replace("size(40.dp)", "size(30.dp)")
    tail = tail.replace("padding(horizontal = 8.dp, vertical = 4.dp)", "padding(horizontal = 10.dp, vertical = 2.dp)")
    tail = tail.replace("vertical = 6.dp", "vertical = 2.dp").replace("vertical = 8.dp", "vertical = 2.dp")
    fs = head + tail
fs = fs.replace("Arrangement.spacedBy(4.dp)", "Arrangement.spacedBy(0.dp)")
fs = fs.replace("Arrangement.spacedBy(2.dp)", "Arrangement.spacedBy(0.dp)")
wf("ui/screens/FilesScreen.kt", fs)
print("[OK] 4. FilesScreen.kt: Filas ultra-compactas sin recuadros (estilo 11-14 ítems visibles).")

# ------------------------------------------------------------------
# 5. MAINACTIVITY: CARPETA MÁSTER AL INICIAR + FLAG_SECURE (PANTALLA NEGRA EN VAULT) + FLIP-TO-CLOSE
# ------------------------------------------------------------------
ma = rf("MainActivity.kt")
ma = imp(ma, [
    "android.view.WindowManager",
    "com.example.NexoMasterDirs",
    "com.example.NexoVaultFlipGuard"
])
if "NexoMasterDirs.ensureAll()" not in ma:
    ma = ma.replace(
        "NexoCrashLibrary.install(applicationContext)",
        "NexoCrashLibrary.install(applicationContext)\n    NexoMasterDirs.ensureAll()",
        1
    )

if "FLAG_SECURE" not in ma:
    ma = ma.replace(
        "var showShareAppDialog by remember { mutableStateOf(false) }",
        """var showShareAppDialog by remember { mutableStateOf(false) }
  DisposableEffect(currentRoute) {
    val act = context as? android.app.Activity
    if (currentRoute == NexoRoutes.VAULT) {
      act?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
      act?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    val unregisterFlip = if (currentRoute == NexoRoutes.VAULT && NexoVaultFlipGuard.isEnabled(context)) {
      NexoVaultFlipGuard.registerListener(context) {
        toolsVm.lockVault()
        currentRoute = NexoRoutes.HOME
      }
    } else { {} }
    onDispose {
      unregisterFlip()
      if (currentRoute != NexoRoutes.VAULT) {
        act?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
      }
    }
  }"""
    )
wf("MainActivity.kt", ma)
print("[OK] 5. MainActivity.kt: Carpeta Máster + FLAG_SECURE en Bóveda + Flip-to-Close.")

# ------------------------------------------------------------------
# 6. SUBIR VERSIÓN A v2.6b (versionCode = 13)
# ------------------------------------------------------------------
gp = "app/build.gradle.kts"
g = open(gp, encoding="utf-8").read()
g = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 13", g)
g = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "2.6b"', g)
open(gp, "w", encoding="utf-8").write(g)
print("[OK] 6. Versión actualizada a 2.6b (versionCode = 13).")
