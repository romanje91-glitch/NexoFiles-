import os, re

ROOT = "app/src/main/java/com/example"

def rf(rel): return open(os.path.join(ROOT, rel), encoding="utf-8").read()
def wf(rel, txt): open(os.path.join(ROOT, rel), "w", encoding="utf-8").write(txt)
def imp(c, ls):
    m = re.search(r"^package\s+[\w\.]+\s*\n", c, re.M)
    a = "".join(f"import {i}\n" for i in ls if f"import {i}" not in c)
    return c if not a else c[:m.end()] + a + c[m.end():]

# 1. TECLADO NUMÉRICO PARA MASTER PIN EN TODA LA APP
for dirpath, _, files in os.walk(ROOT):
    for fn in files:
        if not fn.endswith(".kt"): continue
        p = os.path.join(dirpath, fn)
        c = open(p, encoding="utf-8").read()
        if "Master PIN" in c and "KeyboardType.NumberPassword" not in c:
            c = imp(c, ["androidx.compose.foundation.text.KeyboardOptions", "androidx.compose.ui.text.input.KeyboardType"])
            c = re.sub(
                r'(label\s*=\s*\{\s*Text\("Master PIN"\)\s*\},)',
                r'\1\n            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),',
                c
            )
            open(p, "w", encoding="utf-8").write(c)
print("[OK] 1. Teclado numérico activado para Master PIN.")

# 2. AJUSTES: QUITAR NUMERACIÓN (1., 1.1..5.) + UNIFICAR NOTIFICACIONES
for rel in ["ui/i18n/AppStrings.kt", "ui/screens/SettingsScreen.kt"]:
    c = rf(rel)
    c = re.sub(r'"\d+\.\d+\s+', '"', c)
    c = re.sub(r'"\d+\.\s+', '"', c)
    c = re.sub(r"NERV HUD Explorer v[0-9\.a-zA-Z]+", "NERV HUD Explorer v2.5b", c)
    wf(rel, c)

ss = rf("ui/screens/SettingsScreen.kt")
# Quitar botón duplicado de Notificaciones en System & About y abrir diálogo desde el panel de notificaciones
ss = re.sub(r'\s*OutlinedButton\(onClick = \{ showNotificationsDialog = true \}[^\n]+\n', '\n', ss)
ss = ss.replace(
    'expandedPanel = if (expandedPanel == "NOTIF") "" else "NOTIF"',
    'showNotificationsDialog = true'
)
wf("ui/screens/SettingsScreen.kt", ss)
print("[OK] 2. SettingsScreen: sin números y Notificaciones unificadas.")

# 3. DRAWER: BOTÓN ICONO ACTUALIZAR + BOTÓN ICONO+TEXTO COMPARTIR (BILINGÜE)
ma = rf("MainActivity.kt")
ma = imp(ma, ["androidx.compose.material.icons.Icons", "androidx.compose.material.icons.rounded.Refresh", "androidx.compose.material.icons.rounded.Share"])
pat_dr = re.compile(r'Row\(modifier = Modifier\.fillMaxWidth\(\)\.padding\(horizontal = 4\.dp\), horizontalArrangement = Arrangement\.spacedBy\(8\.dp\)\) \{[\s\S]*?Spacer\(modifier = Modifier\.height\(12\.dp\)\)', re.S)
new_dr = """val isEsDrawer = strings.languageTitle.contains("Idioma", ignoreCase = true)
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick = { NexoShareLinks.checkUpdateNow(drawerCtx) },
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(44.dp),
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = if (isEsDrawer) "Actualizar" else "Update", modifier = Modifier.size(20.dp))
          }
          Button(
            onClick = { NexoShareLinks.showShareDialog = true },
            modifier = Modifier.weight(1f).height(44.dp),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
          ) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isEsDrawer) "COMPARTIR" else "SHARE", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
          }
        }
        Spacer(modifier = Modifier.height(12.dp))"""
ma = pat_dr.sub(new_dr, ma, count=1)
wf("MainActivity.kt", ma)
print("[OK] 3. MainActivity: Botones dinámicos (ícono Actualizar + ícono/texto Compartir).")

# 4. FILES SCREEN: SIN RECUADROS, COMPACTO Y TEXTO BLANCO/NEGRO DE ALTO CONTRASTE
fs = rf("ui/screens/FilesScreen.kt")
fs = imp(fs, ["androidx.compose.ui.graphics.Color", "androidx.compose.ui.graphics.luminance", "androidx.compose.material3.HorizontalDivider"])
fl = fs.find("fun FileListItemCard(")
if fl != -1:
    head, tail = fs[:fl], fs[fl:]
    tail = tail.replace("BichromaticCard(", "Surface(color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent, shape = RoundedCornerShape(4.dp),")
    tail = re.sub(r"\baccentBorder\s*=\s*[^,\n]+,?", "", tail)
    tail = tail.replace("size(48.dp)", "size(36.dp)").replace("size(44.dp)", "size(36.dp)")
    tail = tail.replace("padding(horizontal = 12.dp, vertical = 10.dp)", "padding(horizontal = 8.dp, vertical = 4.dp)")
    tail = tail.replace("MaterialTheme.colorScheme.primary", "(if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF111827))")
    fs = head + tail
wf("ui/screens/FilesScreen.kt", fs)
print("[OK] 4. FilesScreen: Filas compactas sin recuadro y texto blanco/negro.")

# 5. VAULT: VOLVER AL PROGRESO DESDE BÓVEDA Y NOTIFICACIÓN + CONTEO REAL
tn = rf("ui/viewmodel/ToolsAndNetworkViewModel.kt")
tn = imp(tn, ["android.app.PendingIntent", "android.content.Intent", "com.example.MainActivity"])
if "fun reopenVaultBackupProgress()" not in tn:
    tn = tn.replace(
        "  fun dismissVaultBackupProgress() {",
        "  fun reopenVaultBackupProgress() { _vaultState.value = _vaultState.value.copy(showBackupProgressDialog = true) }\n\n  fun dismissVaultBackupProgress() {"
    )
# Añadir PendingIntent a la notificación para volver a la app al tocarla
if "setContentIntent(" not in tn:
    tn = tn.replace(
        ".setAutoCancel(!ongoing)",
        """.setAutoCancel(!ongoing)
        .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))"""
    )
wf("ui/viewmodel/ToolsAndNetworkViewModel.kt", tn)

vs = rf("ui/screens/VaultScreen.kt")
if "onReopenBackupProgress" not in vs:
    vs = vs.replace("  onDismissBackupProgress: () -> Unit = {},", "  onDismissBackupProgress: () -> Unit = {},\n  onReopenBackupProgress: () -> Unit = {},")
    vs = vs.replace(
        "onOpenDriveBackupModal = { onRunSmartBackup() }",
        "onOpenDriveBackupModal = { if (state.isDriveBusy) onReopenBackupProgress() else onRunSmartBackup() }"
    )
wf("ui/screens/VaultScreen.kt", vs)

ma2 = rf("MainActivity.kt")
if "onReopenBackupProgress =" not in ma2:
    ma2 = ma2.replace(
        "onDismissBackupProgress = { toolsVm.dismissVaultBackupProgress() },",
        "onDismissBackupProgress = { toolsVm.dismissVaultBackupProgress() },\n                    onReopenBackupProgress = { toolsVm.reopenVaultBackupProgress() },"
    )
    wf("MainActivity.kt", ma2)
print("[OK] 5. Vault: Retomar progreso desde Bóveda y desde la notificación.")

# 6. VERSIÓN 2.5b (versionCode = 12)
gp = "app/build.gradle.kts"
g = open(gp, encoding="utf-8").read()
g = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 12", g)
g = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "2.5b"', g)
open(gp, "w", encoding="utf-8").write(g)
print("[OK] 6. Versión actualizada a 2.5b (versionCode = 12).")
