package com.example.ui.viewmodel

import com.example.NexoNavBridge
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.db.BookmarkEntity
import com.example.data.db.NetworkServerEntity
import com.example.data.db.RecentFileActionEntity
import com.example.data.model.AllSettingsState
import com.example.data.model.AppLanguageOption
import com.example.data.model.ArchiveEntryInfo
import com.example.data.model.ClipboardAction
import com.example.data.model.FileCategory
import com.example.data.model.FileClipboard
import com.example.data.model.FileProperties
import com.example.data.model.GestureBinding
import com.example.data.model.LayoutViewMode
import com.example.data.model.LocalFileItem
import com.example.data.model.SearchEngineMode
import com.example.data.model.SortBy
import com.example.data.model.StorageStats
import com.example.data.model.ThemeMode
import com.example.data.model.VaultLockMethod
import com.example.util.FormatUtils
import com.example.util.StoragePermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ExplorerUiState(
  val storageStats: StorageStats = StorageStats(),
  val currentPath: String = "/storage/emulated/0",
  val files: List<LocalFileItem> = emptyList(),
  val isLoadingFiles: Boolean = false,
  val errorMessage: String? = null,
  val showHiddenFiles: Boolean = true,
  val sortBy: SortBy = SortBy.NAME,
  val sortAscending: Boolean = true,
  val selectedPaths: Set<String> = emptySet(),
  val clipboard: FileClipboard? = null,
  val activeCategory: FileCategory? = null,
  val categoryFiles: List<LocalFileItem> = emptyList(),
  val isLoadingCategory: Boolean = false,
  val searchQuery: String = "",
  val searchResults: List<LocalFileItem> = emptyList(),
  val isSearching: Boolean = false,
  val recentDiskFiles: List<LocalFileItem> = emptyList(),
  val activeProperties: FileProperties? = null,
  val isLoadingProperties: Boolean = false,
  val archiveInspectTarget: LocalFileItem? = null,
  val archiveEntries: List<ArchiveEntryInfo> = emptyList(),
  val isArchiveBusy: Boolean = false,
  val hasAllFilesAccess: Boolean = false,
  val hasMediaPermission: Boolean = false,
  val availableVolumes: List<Pair<String, String>> = emptyList(),
)

class MainExplorerViewModel(
  private val context: Context,
  private val container: AppContainer,
) : ViewModel() {

  private val _uiState = MutableStateFlow(
    ExplorerUiState(
      currentPath = container.localFiles.rootStoragePath,
      availableVolumes = container.localFiles.getAvailableVolumes(),
    )
  )
  val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

  val bookmarks: StateFlow<List<BookmarkEntity>> = container.localFiles.observeBookmarks()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val recentActions: StateFlow<List<RecentFileActionEntity>> = container.localFiles.observeRecentActions()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val themeMode: StateFlow<ThemeMode> = container.preferences.themeMode
    .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)

  val allSettings: StateFlow<AllSettingsState> = container.preferences.allSettings
    .stateIn(viewModelScope, SharingStarted.Eagerly, AllSettingsState())

  val vaultBiometricEnabled: StateFlow<Boolean> = container.preferences.vaultBiometricEnabled
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

  val defaultAppMappings: StateFlow<Map<String, String>> = container.preferences.defaultAppMappings
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

  private val _messages = MutableSharedFlow<String>()
  val messages = _messages.asSharedFlow()

  private var searchJob: Job? = null

  init {
    viewModelScope.launch {
      combine(
        container.preferences.showHiddenFiles,
        container.preferences.sortBy,
        container.preferences.sortAscending,
      ) { hidden, sort, asc ->
        Triple(hidden, sort, asc)
      }.collect { (hidden, sort, asc) ->
        _uiState.value = _uiState.value.copy(
          showHiddenFiles = hidden,
          sortBy = sort,
          sortAscending = asc,
        )
        loadDirectory(_uiState.value.currentPath)
      }
    }
    viewModelScope.launch {
      val savedHome = container.preferences.allSettings.first().homeDirectory
      val homeDir = File(savedHome)
      if (homeDir.exists() && homeDir.isDirectory && homeDir.canRead() && savedHome != _uiState.value.currentPath) {
        loadDirectory(savedHome)
      }
    }
    refreshPermissionsAndDashboard()
  }

  fun refreshPermissionsAndDashboard() {
    viewModelScope.launch {
      val hasAll = StoragePermissionHelper.hasAllFilesAccess(context)
      val hasMedia = StoragePermissionHelper.hasMediaPermissions(context)
      val stats = container.localFiles.getStorageStats()
      val recent = container.localFiles.getRecentFiles(limit = 12)
      _uiState.value = _uiState.value.copy(
        storageStats = stats,
        hasAllFilesAccess = hasAll,
        hasMediaPermission = hasMedia,
        recentDiskFiles = recent,
        availableVolumes = container.localFiles.getAvailableVolumes(),
      )
      loadDirectory(_uiState.value.currentPath)
    }
  }

  fun loadDirectory(path: String) {
    viewModelScope.launch {
      val state = _uiState.value
      _uiState.value = state.copy(
        currentPath = path,
        isLoadingFiles = true,
        errorMessage = null,
        selectedPaths = emptySet(),
      )
      val res = container.localFiles.listDirectory(
        path = path,
        showHidden = state.showHiddenFiles,
        sortBy = state.sortBy,
        ascending = state.sortAscending,
      )
      res.onSuccess { items ->
        _uiState.value = _uiState.value.copy(files = items, isLoadingFiles = false)
      }.onFailure { err ->
        _uiState.value = _uiState.value.copy(
          files = emptyList(),
          isLoadingFiles = false,
          errorMessage = err.localizedMessage ?: "No se pudo abrir el directorio",
        )
      }
    }
  }

  fun navigateUp(): Boolean {
    val current = _uiState.value.currentPath.trimEnd('/')
    val root = container.localFiles.rootStoragePath.trimEnd('/')
    if (current.isEmpty() || current == root) return false
    val parent = File(current).parent ?: return false
    loadDirectory(parent)
    return true
  }

  fun toggleHiddenFiles() {
    viewModelScope.launch {
      container.preferences.setShowHiddenFiles(!_uiState.value.showHiddenFiles)
    }
  }

  fun setHiddenFiles(show: Boolean) {
    viewModelScope.launch {
      container.preferences.setShowHiddenFiles(show)
    }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch {
      container.preferences.setThemeMode(mode)
    }
  }

  fun setVaultBiometric(enabled: Boolean) {
    viewModelScope.launch {
      container.preferences.setVaultBiometric(enabled)
    }
  }

  fun setVaultSecurityConfig(method: VaultLockMethod, rawSecret: String? = null) {
    viewModelScope.launch {
      container.preferences.setVaultSecurityConfig(method, rawSecret)
      emitMessage("Seguridad de Bóveda actualizada: ${method.name}")
    }
  }

  fun verifyVaultSecret(rawSecret: String): Boolean {
    return container.preferences.verifyVaultSecret(rawSecret, allSettings.value.vaultSecretHash)
  }

  fun updateBooleanSetting(keyName: String, value: Boolean) {
    viewModelScope.launch { container.preferences.updateBooleanSetting(keyName, value) }
  }

  fun updateStringSetting(keyName: String, value: String) {
    viewModelScope.launch { container.preferences.updateStringSetting(keyName, value) }
  }

  fun setAppLanguage(lang: AppLanguageOption) {
    viewModelScope.launch { container.preferences.setAppLanguage(lang) }
  }

  fun setDefaultAppForExtension(extension: String, handlerId: String) {
    viewModelScope.launch {
      container.preferences.setDefaultAppForExtension(extension, handlerId)
    }
  }

  fun resetDefaultApps(confirmationMessage: String) {
    viewModelScope.launch {
      container.preferences.clearAllDefaultApps()
      emitMessage(confirmationMessage)
    }
  }

  fun setHomeDirectory(path: String) {
    viewModelScope.launch {
      container.preferences.setHomeDirectory(path)
      loadDirectory(path)
      emitMessage("Directorio de inicio actualizado")
    }
  }

  fun setBluetoothReceiveDir(path: String) {
    viewModelScope.launch { container.preferences.setBluetoothReceiveDir(path) }
  }

  fun setDownloadDirectory(path: String) {
    viewModelScope.launch { container.preferences.setDownloadDirectory(path) }
  }

  fun setSearchEngineMode(mode: SearchEngineMode) {
    viewModelScope.launch { container.preferences.setSearchEngineMode(mode) }
  }

  fun setLayoutViewMode(mode: LayoutViewMode) {
    viewModelScope.launch { container.preferences.setLayoutViewMode(mode) }
  }

  fun setDrawerConfig(orderIds: List<String>, hiddenIds: Set<String>) {
    viewModelScope.launch { container.preferences.setDrawerConfig(orderIds, hiddenIds) }
  }

  fun setToolbarActions(actions: List<String>) {
    viewModelScope.launch { container.preferences.setToolbarActions(actions) }
  }

  fun setAppBackupDirectory(path: String) {
    viewModelScope.launch { container.preferences.setAppBackupDirectory(path) }
  }

  fun setInitialWindows(paths: List<String>) {
    viewModelScope.launch { container.preferences.setInitialWindows(paths) }
  }

  fun setSenderNickname(nickname: String) {
    viewModelScope.launch { container.preferences.setSenderNickname(nickname) }
  }

  fun setSenderSavePath(path: String) {
    viewModelScope.launch { container.preferences.setSenderSavePath(path) }
  }

  fun setSenderSameWifiMode(enabled: Boolean) {
    viewModelScope.launch { container.preferences.setSenderSameWifiMode(enabled) }
  }

  fun setMasterPin(pinPlain: String) {
    viewModelScope.launch {
      container.preferences.setMasterPin(pinPlain)
      emitMessage(if (pinPlain.isBlank()) "PIN maestro desactivado" else "PIN maestro configurado")
    }
  }

  fun verifyMasterPin(pinPlain: String): Boolean {
    return container.preferences.verifyPin(pinPlain, allSettings.value.masterPinHash)
  }

  fun saveGestureBinding(binding: GestureBinding) {
    viewModelScope.launch {
      container.preferences.saveGestureBinding(binding)
      emitMessage("Gesto '${binding.gestureShape}' guardado")
    }
  }

  fun deleteGestureBinding(id: String) {
    viewModelScope.launch { container.preferences.deleteGestureBinding(id) }
  }

  fun clearAppCacheNow() {
    viewModelScope.launch {
      var freed = 0L
      withContext(Dispatchers.IO) {
        context.cacheDir?.listFiles()?.forEach { f ->
          freed += if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
          f.deleteRecursively()
        }
      }
      emitMessage("Caché limpiada (${FormatUtils.formatBytes(freed)})")
    }
  }

  fun clearRecentHistoryNow() {
    viewModelScope.launch {
      container.dao.clearRecentFiles()
      emitMessage("Historial reciente limpiado")
    }
  }

  fun resetAllSettings() {
    viewModelScope.launch {
      container.preferences.resetToDefaults()
      emitMessage("Ajustes restablecidos a valores predeterminados")
    }
  }

  fun exportSettingsBackup(destUri: Uri) {
    viewModelScope.launch {
      runCatching {
        val bookmarks = container.dao.getBookmarksSync()
        val servers = container.dao.getNetworkServersSync()
        val bArr = JSONArray().apply {
          bookmarks.forEach { b ->
            put(
              JSONObject().apply {
                put("name", b.name)
                put("path", b.path)
                put("isDirectory", b.isDirectory)
              }
            )
          }
        }
        val sArr = JSONArray().apply {
          servers.forEach { s ->
            put(
              JSONObject().apply {
                put("name", s.name)
                put("protocol", s.protocol)
                put("host", s.host)
                put("port", s.port)
                put("username", s.username)
                put("ftpMode", s.ftpMode)
                put("encoding", s.encoding)
                put("useHttps", s.useHttps)
                put("domain", s.domain)
                put("anonymous", s.anonymous)
              }
            )
          }
        }
        val json = container.preferences.exportSettingsJson(bArr, sArr)
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(destUri)?.use { out ->
            out.write(json.toByteArray())
          } ?: throw IllegalStateException("No se pudo escribir el archivo de respaldo")
        }
      }.onSuccess {
        emitMessage("Respaldo JSON de configuración exportado")
      }.onFailure {
        emitMessage("Error exportando respaldo: ${it.localizedMessage}")
      }
    }
  }

  fun importSettingsBackup(sourceUri: Uri) {
    viewModelScope.launch {
      runCatching {
        val text = withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(sourceUri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("No se pudo leer el archivo")
        }
        val obj = container.preferences.importSettingsJson(text)
        obj.optJSONArray("bookmarks")?.let { arr ->
          for (i in 0 until arr.length()) {
            val b = arr.getJSONObject(i)
            container.dao.insertBookmark(
              BookmarkEntity(
                name = b.optString("name"),
                path = b.optString("path"),
                isDirectory = b.optBoolean("isDirectory", true),
              )
            )
          }
        }
        obj.optJSONArray("servers")?.let { arr ->
          for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            container.dao.insertNetworkServer(
              NetworkServerEntity(
                name = s.optString("name"),
                protocol = s.optString("protocol", "FTP"),
                host = s.optString("host"),
                port = s.optInt("port", 21),
                username = s.optString("username"),
                ftpMode = s.optString("ftpMode", "PASSIVE"),
                encoding = s.optString("encoding", "AUTO"),
                useHttps = s.optBoolean("useHttps", false),
                domain = s.optString("domain"),
                anonymous = s.optBoolean("anonymous", false),
              )
            )
          }
        }
      }.onSuccess {
        emitMessage("Configuración y servidores restaurados desde JSON")
      }.onFailure {
        emitMessage("Error restaurando respaldo: ${it.localizedMessage}")
      }
    }
  }

  fun setSortBy(sortBy: SortBy) {
    viewModelScope.launch {
      if (_uiState.value.sortBy == sortBy) {
        container.preferences.setSortAscending(!_uiState.value.sortAscending)
      } else {
        container.preferences.setSortBy(sortBy)
        container.preferences.setSortAscending(sortBy == SortBy.NAME)
      }
    }
  }

  fun toggleSelection(path: String) {
    val current = _uiState.value.selectedPaths.toMutableSet()
    if (!current.add(path)) current.remove(path)
    _uiState.value = _uiState.value.copy(selectedPaths = current)
  }

  fun selectAllCurrent() {
    _uiState.value = _uiState.value.copy(selectedPaths = _uiState.value.files.map { it.path }.toSet())
  }

  fun clearSelection() {
    _uiState.value = _uiState.value.copy(selectedPaths = emptySet())
  }

  fun stageClipboard(paths: List<String>, action: ClipboardAction) {
    _uiState.value = _uiState.value.copy(
      clipboard = FileClipboard(paths = paths, action = action),
      selectedPaths = emptySet(),
    )
    emitMessage(
      if (action == ClipboardAction.COPY) "${paths.size} elemento(s) copiado(s) al portapapeles"
      else "${paths.size} elemento(s) listo(s) para mover"
    )
  }

  fun pasteClipboard() {
    val clip = _uiState.value.clipboard ?: return
    val dest = _uiState.value.currentPath
    viewModelScope.launch {
      val res = container.localFiles.copyOrMoveFiles(
        sourcePaths = clip.paths,
        destDirPath = dest,
        move = clip.action == ClipboardAction.MOVE,
      )
      res.onSuccess { count ->
        _uiState.value = _uiState.value.copy(clipboard = null)
        loadDirectory(dest)
        refreshPermissionsAndDashboard()
        emitMessage("Operación completada: $count elemento(s)")
      }.onFailure {
        emitMessage("Error al pegar: ${it.localizedMessage}")
      }
    }
  }

  fun clearClipboard() {
    _uiState.value = _uiState.value.copy(clipboard = null)
  }

  fun createFolder(name: String) {
    viewModelScope.launch {
      container.localFiles.createFolder(_uiState.value.currentPath, name)
        .onSuccess {
          loadDirectory(_uiState.value.currentPath)
          emitMessage("Carpeta \"$name\" creada")
        }
        .onFailure { emitMessage(it.localizedMessage ?: "No se pudo crear la carpeta") }
    }
  }

  fun createTextFile(name: String) {
    viewModelScope.launch {
      container.localFiles.createTextFile(_uiState.value.currentPath, name)
        .onSuccess {
          loadDirectory(_uiState.value.currentPath)
          emitMessage("Archivo \"$name\" creado")
        }
        .onFailure { emitMessage(it.localizedMessage ?: "No se pudo crear el archivo") }
    }
  }

  fun renameItem(path: String, newName: String) {
    viewModelScope.launch {
      container.localFiles.renameFile(path, newName)
        .onSuccess {
          loadDirectory(_uiState.value.currentPath)
          emitMessage("Renombrado a \"$newName\"")
        }
        .onFailure { emitMessage(it.localizedMessage ?: "Error al renombrar") }
    }
  }

  fun moveToRecycleBin(paths: List<String>) {
    viewModelScope.launch {
      val useTrash = allSettings.value.recycleBinEnabled
      val res = if (useTrash) {
        container.localFiles.moveToRecycleBin(paths)
      } else {
        container.localFiles.deletePermanently(paths)
      }
      res.onSuccess { count ->
        _uiState.value = _uiState.value.copy(selectedPaths = emptySet())
        loadDirectory(_uiState.value.currentPath)
        refreshPermissionsAndDashboard()
        emitMessage(
          if (useTrash) "$count elemento(s) enviado(s) a la Papelera"
          else "$count elemento(s) eliminado(s) definitivamente"
        )
      }.onFailure { emitMessage(it.localizedMessage ?: "Error al eliminar") }
    }
  }

  fun deletePermanently(paths: List<String>) {
    viewModelScope.launch {
      container.localFiles.deletePermanently(paths)
        .onSuccess { count ->
          _uiState.value = _uiState.value.copy(selectedPaths = emptySet())
          loadDirectory(_uiState.value.currentPath)
          refreshPermissionsAndDashboard()
          emitMessage("$count elemento(s) eliminado(s) definitivamente")
        }
        .onFailure { emitMessage(it.localizedMessage ?: "Error al eliminar") }
    }
  }

  fun openProperties(path: String) {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoadingProperties = true, activeProperties = null)
      val props = container.localFiles.getFileProperties(path)
      _uiState.value = _uiState.value.copy(activeProperties = props, isLoadingProperties = false)
    }
  }

  fun dismissProperties() {
    _uiState.value = _uiState.value.copy(activeProperties = null, isLoadingProperties = false)
  }

  fun toggleBookmark(item: LocalFileItem) {
    viewModelScope.launch {
      val added = container.localFiles.toggleBookmark(item)
      emitMessage(if (added) "Marcador guardado" else "Marcador eliminado")
    }
  }

  fun deleteBookmark(id: Long) {
    viewModelScope.launch { container.localFiles.deleteBookmark(id) }
  }

  fun openCategory(category: FileCategory?) {
    if (category == null) {
      _uiState.value = _uiState.value.copy(activeCategory = null, categoryFiles = emptyList())
      return
    }
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(activeCategory = category, isLoadingCategory = true)
      val items = container.localFiles.listCategoryFiles(category, _uiState.value.showHiddenFiles)
      _uiState.value = _uiState.value.copy(categoryFiles = items, isLoadingCategory = false)
    }
  }

  fun updateSearchQuery(query: String) {
    _uiState.value = _uiState.value.copy(searchQuery = query)
    searchJob?.cancel()
    if (query.isBlank()) {
      _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
      return
    }
    searchJob = viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isSearching = true)
      val results = container.localFiles.globalSearch(query, _uiState.value.showHiddenFiles)
      _uiState.value = _uiState.value.copy(searchResults = results, isSearching = false)
    }
  }

  fun createZipFromPaths(paths: List<String>, zipName: String, destDirPath: String? = null) {
    viewModelScope.launch {
      val cleanName = if (zipName.endsWith(".zip", true)) zipName else "$zipName.zip"
      val targetDir = destDirPath?.takeIf { it.isNotBlank() } ?: _uiState.value.currentPath
      val destZip = File(targetDir, cleanName)
      val sources = paths.map { File(it) }
      container.archives.createZip(sources, destZip)
        .onSuccess {
          _uiState.value = _uiState.value.copy(selectedPaths = emptySet())
          loadDirectory(_uiState.value.currentPath)
          emitMessage("Archivo comprimido: ${destZip.name}")
        }
        .onFailure { emitMessage("Error al comprimir: ${it.localizedMessage}") }
    }
  }

  fun inspectArchive(item: LocalFileItem) {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(archiveInspectTarget = item, isArchiveBusy = true, archiveEntries = emptyList())
      container.archives.listArchiveEntries(File(item.path))
        .onSuccess { entries ->
          _uiState.value = _uiState.value.copy(archiveEntries = entries, isArchiveBusy = false)
        }
        .onFailure { err ->
          _uiState.value = _uiState.value.copy(isArchiveBusy = false, archiveInspectTarget = null)
          emitMessage(err.localizedMessage ?: "No se pudo leer el archivo comprimido")
        }
    }
  }

  fun extractArchive(item: LocalFileItem) {
    val archiveFile = File(item.path)
    val folderName = archiveFile.nameWithoutExtension.removeSuffix(".tar")
    val defaultDest = File(archiveFile.parentFile ?: File(_uiState.value.currentPath), folderName)
    extractArchiveTo(item, defaultDest.absolutePath)
  }

  fun extractArchiveTo(item: LocalFileItem, destDirPath: String) {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isArchiveBusy = true)
      val archiveFile = File(item.path)
      val destDir = File(destDirPath)
      container.archives.extractArchive(archiveFile, destDir)
        .onSuccess { count ->
          _uiState.value = _uiState.value.copy(isArchiveBusy = false, archiveInspectTarget = null)
          loadDirectory(_uiState.value.currentPath)
          emitMessage("Extraídos $count archivo(s) en ${destDir.name}")
        }
        .onFailure { err ->
          _uiState.value = _uiState.value.copy(isArchiveBusy = false)
          emitMessage("Error al extraer: ${err.localizedMessage}")
        }
    }
  }

  fun dismissArchiveInspector() {
    _uiState.value = _uiState.value.copy(archiveInspectTarget = null, archiveEntries = emptyList(), isArchiveBusy = false)
  }

  fun importFromSaf(uri: Uri) {
    viewModelScope.launch {
      container.localFiles.importFromSafUri(uri, _uiState.value.currentPath)
        .onSuccess { file ->
          loadDirectory(_uiState.value.currentPath)
          emitMessage("Importado vía SAF: ${file.name}")
        }
        .onFailure { emitMessage("Error importando archivo: ${it.localizedMessage}") }
    }
  }

  fun exportToSaf(sourcePath: String, destUri: Uri) {
    viewModelScope.launch {
      container.localFiles.exportToSafUri(File(sourcePath), destUri)
        .onSuccess { emitMessage("Exportado correctamente vía SAF") }
        .onFailure { emitMessage("Error exportando: ${it.localizedMessage}") }
    }
  }

  fun sharePaths(paths: List<String>) {
    val files = paths.map { File(it) }.filter { it.exists() && it.isFile }
    if (files.isEmpty()) {
      emitMessage("Selecciona archivos válidos para compartir")
      return
    }
    FormatUtils.shareFiles(context, files)
  }

  fun recordRecentOpen(file: File) {
    viewModelScope.launch { container.localFiles.recordRecentAction(file) }
  }

  fun openFile(file: File) {
    recordRecentOpen(file)
    FormatUtils.openFileWithSystem(context, file)
  }

  fun lockInVault(path: String) {
    viewModelScope.launch {
      container.vault.lockLocalFile(File(path), removeOriginal = true)
        .onSuccess {
          loadDirectory(_uiState.value.currentPath)
          emitMessage("Archivo cifrado y movido a la Bóveda Privada")
        }
        .onFailure { emitMessage("Error moviendo a bóveda: ${it.localizedMessage}") }
    }
  }

  private fun emitMessage(msg: String) {
    viewModelScope.launch { _messages.emit(msg) }
  }

  class Factory(private val context: Context, private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return MainExplorerViewModel(context.applicationContext, container) as T
    }
  }
}
