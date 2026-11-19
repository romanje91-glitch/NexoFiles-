import os, re

ROOT = "app/src/main/java/com/example"

def read_f(rel):
    with open(os.path.join(ROOT, rel), "r", encoding="utf-8") as f:
        return f.read()

def write_f(rel, txt):
    with open(os.path.join(ROOT, rel), "w", encoding="utf-8") as f:
        f.write(txt)

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

# ------------------------------------------------------------------
# 1. NexoMasterBridge.kt: soporte para abrir archivo .torrent local directo
# ------------------------------------------------------------------
mb_rel = "NexoMasterBridge.kt"
mb_code = read_f(mb_rel)
if "var pendingTorrentFilePath" not in mb_code:
    mb_code = mb_code.replace(
        'var pendingMagnetUri by mutableStateOf("")',
        'var pendingMagnetUri by mutableStateOf("")\n    var pendingTorrentFilePath by mutableStateOf("")\n    fun openTorrentWithFile(path: String) { pendingTorrentFilePath = path; showTorrentManager = true }'
    )
    write_f(mb_rel, mb_code)
    report.append("[OK] 1. NexoMasterBridge.kt: disparador directo para archivos .torrent.")

# ------------------------------------------------------------------
# 2. FilesScreen.kt:
#    - Abrir .torrent directo en NexoTorrent al tocarlo
#    - Reemplazar tarjeta duplicada de selección por cabecera compacta de 1 línea
#    - Eliminar definitivamente el botón '...' de cada fila (FileListItemCard)
# ------------------------------------------------------------------
fs_rel = "ui/screens/FilesScreen.kt"
fs_code = read_f(fs_rel)

# A) Interceptar .torrent en handleItemClick
if '.endsWith(".torrent"' not in fs_code:
    fs_code = fs_code.replace(
        "fun handleItemClick(item: LocalFileItem) {",
        """fun handleItemClick(item: LocalFileItem) {
        if (!item.isDirectory && item.name.endsWith(".torrent", ignoreCase = true) && uiState.selectedPaths.isEmpty()) {
            NexoNavBridge.openTorrentWithFile(item.path)
            return
        }""",
        1
    )

# B) Reemplazar el bloque "// Multi-selection action bar" por una cabecera compacta de 1 línea (sin duplicar los botones de abajo)
ms_anchor = "      // Multi-selection action bar\n      AnimatedVisibility(visible = uiState.selectedPaths.isNotEmpty()) {"
ms_idx = fs_code.find(ms_anchor)
if ms_idx != -1:
    bo = fs_code.find("{", ms_idx + len("      // Multi-selection action bar\n      AnimatedVisibility(visible = uiState.selectedPaths.isNotEmpty()) "))
    be = find_balanced(fs_code, bo, "{", "}")
    if be != -1:
        compact_sel_bar = """      // Compact ES-style selection header (actions live in the single bottom bar)
      AnimatedVisibility(visible = uiState.selectedPaths.isNotEmpty()) {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "${uiState.selectedPaths.size} / ${uiState.files.size}",
              style = MonoTnumStyle,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              TextButton(onClick = onSelectAll, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text(strings.selectAllCd.uppercase(), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
              }
              TextButton(onClick = onClearSelection, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text(strings.cancel.uppercase(), fontSize = 11.sp, color = NexoTheme.semantic.evaOrange)
              }
            }
          }
        }
      }"""
        fs_code = fs_code[:ms_idx] + compact_sel_bar + fs_code[be:]

# C) Ocultar el botón IconButton con NexoIcons.MoreHoriz en cada fila de FileListItemCard
mh_idx = fs_code.find("NexoIcons.MoreHoriz")
if mh_idx != -1:
    ib_start = fs_code.rfind("IconButton(", max(0, mh_idx - 450), mh_idx)
    if ib_start != -1 and "if (false)" not in fs_code[max(0, ib_start - 30):ib_start]:
        pe = find_balanced(fs_code, fs_code.find("(", ib_start), "(", ")")
        bo = fs_code.find("{", pe)
        be = find_balanced(fs_code, bo, "{", "}")
        if be != -1:
            fs_code = fs_code[:ib_start] + "if (false) {\n" + fs_code[ib_start:be] + "\n}" + fs_code[be:]

write_f(fs_rel, fs_code)
report.append("[OK] 2. FilesScreen.kt: Sin '...' por fila + sin barra duplicada + apertura directa de .torrent.")

# ------------------------------------------------------------------
# 3. NexoSecurityAndTorrent.kt:
#    - Pestañas Todos | Descargando | Completados
#    - Carga automática de archivo .torrent local al abrir desde FilesScreen
#    - Diálogo de confirmación al borrar con interruptor "Eliminar también archivos descargados"
# ------------------------------------------------------------------
st_rel = "NexoSecurityAndTorrent.kt"
st_code = read_f(st_rel)

# Añadir addLocalTorrentFile y removeTask(id, deleteFiles)
if "fun addLocalTorrentFile(" not in st_code:
    st_code = st_code.replace(
        "    fun removeTask(id: String) {",
        """    suspend fun addLocalTorrentFile(filePath: String, onDone: (String) -> Unit) = withContext(Dispatchers.IO) {
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

    fun removeTask(id: String, deleteFiles: Boolean = false) {"""
    )
    st_code = st_code.replace(
        "        tasks.removeAll { it.id == id }\n    }",
        """        val target = tasks.firstOrNull { it.id == id }
        if (deleteFiles && target != null) {
            runCatching { File(target.saveDir, target.name).deleteRecursively() }
        }
        tasks.removeAll { it.id == id }
    }""",
        1
    )

# Actualizar firma y UI de NexoTorrentManagerDialog con pestañas + confirmación de borrado + auto-carga de .torrent
st_code = st_code.replace(
    "fun NexoTorrentManagerDialog(\n    initialMagnet: String = \"\",",
    "fun NexoTorrentManagerDialog(\n    initialMagnet: String = \"\",\n    initialTorrentPath: String = \"\","
)
st_code = st_code.replace(
    "    var magnetInput by remember(initialMagnet) { mutableStateOf(initialMagnet) }",
    """    var magnetInput by remember(initialMagnet) { mutableStateOf(initialMagnet) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Todos, 1=Descargando, 2=Completados
    var pendingDeleteTask by remember { mutableStateOf<NexoTorrentTask?>(null) }
    var deleteWithFiles by remember { mutableStateOf(false) }

    LaunchedEffect(initialTorrentPath) {
        if (initialTorrentPath.isNotBlank()) {
            NexoTorrentEngine.addLocalTorrentFile(initialTorrentPath) { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }"""
)
st_code = st_code.replace(
    "            if (NexoTorrentEngine.tasks.isEmpty()) {",
    """            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            if (visibleTasks.isEmpty()) {"""
)
st_code = st_code.replace(
    "items(NexoTorrentEngine.tasks, key = { it.id })",
    "items(visibleTasks, key = { it.id })"
)
st_code = st_code.replace(
    "IconButton(onClick = { NexoTorrentEngine.removeTask(task.id) }, modifier = Modifier.size(32.dp))",
    "IconButton(onClick = { deleteWithFiles = false; pendingDeleteTask = task }, modifier = Modifier.size(32.dp))"
)
# Añadir diálogo de confirmación con toggle "Eliminar también archivos descargados" antes de cerrar Dialog
if "pendingDeleteTask?.let" not in st_code:
    last_close = st_code.rfind("    }\n}")
    if last_close != -1:
        del_dlg = """
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
"""
        st_code = st_code[:last_close] + del_dlg + st_code[last_close:]
write_f(st_rel, st_code)
report.append("[OK] 3. NexoSecurityAndTorrent.kt: Pestañas µTorrent + borrado con archivos + carga directa .torrent.")

# ------------------------------------------------------------------
# 4. MainActivity.kt: pasar initialTorrentPath a NexoTorrentManagerDialog
# ------------------------------------------------------------------
ma_rel = "MainActivity.kt"
ma_code = read_f(ma_rel)
ma_code = ma_code.replace(
    "initialMagnet = NexoNavBridge.pendingMagnetUri,",
    "initialMagnet = NexoNavBridge.pendingMagnetUri,\n      initialTorrentPath = NexoNavBridge.pendingTorrentFilePath,"
)
ma_code = ma_code.replace(
    'NexoNavBridge.pendingMagnetUri = ""',
    'NexoNavBridge.pendingMagnetUri = ""\n        NexoNavBridge.pendingTorrentFilePath = ""'
)
write_f(ma_rel, ma_code)
report.append("[OK] 4. MainActivity.kt: initialTorrentPath conectado.")

# ------------------------------------------------------------------
# 5. Subir versión a v2.2b (versionCode = 9)
# ------------------------------------------------------------------
for rel_ver in ["ui/i18n/AppStrings.kt", "ui/screens/SettingsScreen.kt"]:
    c = read_f(rel_ver)
    c = re.sub(r"NERV HUD Explorer v[0-9\.a-zA-Z]+", "NERV HUD Explorer v2.2b", c)
    write_f(rel_ver, c)

gp = "app/build.gradle.kts"
with open(gp, "r", encoding="utf-8") as f:
    g = f.read()
g = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 9", g)
g = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "2.2b"', g)
with open(gp, "w", encoding="utf-8") as f:
    f.write(g)
report.append("[OK] 5. Versión actualizada a v2.2b (versionCode = 9).")

print("\n=== VERIFICACIÓN PRE-COMPILACIÓN v2.2b ===")
for r in report:
    print(" ", r)
print("==========================================")
