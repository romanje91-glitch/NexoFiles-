package com.example.ui.viewmodel

import com.example.NexoVaultDriveMirror
import com.example.NexoNavBridge
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.db.AutoBackupRuleEntity
import com.example.data.db.DownloadHistoryEntity
import com.example.data.db.NetworkServerEntity
import com.example.data.db.SenderHistoryEntity
import com.example.data.model.DriveFileItem
import com.example.data.model.GooglePhotoMediaItem
import com.example.data.model.InstalledAppInfo
import com.example.data.model.LanDevice
import com.example.data.model.LocalFileItem
import com.example.data.model.PhotoExportFormat
import com.example.data.model.PickerSessionInfo
import com.example.data.model.RemoteFileEntry
import com.example.data.model.RemoteProtocol
import com.example.data.model.VaultBackupEntry
import com.example.data.model.VaultFileItem
import com.example.data.model.VaultPortalState
import com.example.data.repo.DriveAuthStepResult
import com.example.data.repo.PhotosAuthStepResult
import com.example.data.repo.SenderServerState
import com.example.data.repo.StagedVaultPortalBlob
import com.example.data.repo.WebServerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DriveUiState(
  val isAuthorized: Boolean = false,
  val accessToken: String? = null,
  val accountEmail: String? = null,
  val accountName: String? = null,
  val currentFolderId: String = "root",
  val folderStack: List<Pair<String, String>> = listOf("root" to "Mi Unidad"),
  val files: List<DriveFileItem> = emptyList(),
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val pendingConsentIntent: PendingIntent? = null,
  val isSyncingBackup: Boolean = false,
)

data class GooglePhotosUiState(
  val isAuthorized: Boolean = false,
  val accessToken: String? = null,
  val accountEmail: String? = null,
  val accountName: String? = null,
  val activeSession: PickerSessionInfo? = null,
  val isPollingSession: Boolean = false,
  val pickedItems: List<GooglePhotoMediaItem> = emptyList(),
  val downloadingItemIds: Set<String> = emptySet(),
  val isLoading: Boolean = false,
  val isUploadingCopy: Boolean = false,
  val errorMessage: String? = null,
  val pendingConsentIntent: PendingIntent? = null,
)

data class NetworkUiState(
  val lanDevices: List<LanDevice> = emptyList(),
  val isScanningLan: Boolean = false,
  val activeServer: NetworkServerEntity? = null,
  val currentRemotePath: String = "/",
  val remoteEntries: List<RemoteFileEntry> = emptyList(),
  val isConnectingRemote: Boolean = false,
  val remoteError: String? = null,
)

data class AppManagerUiState(
  val userApps: List<InstalledAppInfo> = emptyList(),
  val systemApps: List<InstalledAppInfo> = emptyList(),
  val backedUpApks: List<LocalFileItem> = emptyList(),
  val isLoading: Boolean = false,
  val searchQuery: String = "",
)

data class NoteEditorUiState(
  val filePath: String? = null,
  val fileName: String = "nota_nueva.txt",
  val content: String = "",
  val hexContent: String = "",
  val isBinary: Boolean = false,
  val isTruncated: Boolean = false,
  val isDirty: Boolean = false,
  val isLoading: Boolean = false,
  val isVaultMode: Boolean = false,
)

data class VaultUiState(
  val isUnlocked: Boolean = false,
  val items: List<VaultFileItem> = emptyList(),
  val isBusy: Boolean = false,
  val openingItemId: String? = null,
  val driveBackups: List<VaultBackupEntry> = emptyList(),
  val isDriveBusy: Boolean = false,
  val driveStatusMessage: String? = null,
)

class ToolsAndNetworkViewModel(
  private val context: Context,
  private val container: AppContainer,
) : ViewModel() {

  private val _driveState = MutableStateFlow(DriveUiState())
  val driveState: StateFlow<DriveUiState> = _driveState.asStateFlow()

  private val _googlePhotosState = MutableStateFlow(GooglePhotosUiState())
  val googlePhotosState: StateFlow<GooglePhotosUiState> = _googlePhotosState.asStateFlow()
  private var photosPollingJob: Job? = null

  val autoBackupRules: StateFlow<List<AutoBackupRuleEntity>> = container.googleDrive.observeAutoBackupRules()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val webServerState: StateFlow<WebServerState> = container.httpServer.state

  val webServerPort: StateFlow<Int> = container.preferences.webServerPort
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8080)

  val senderState: StateFlow<SenderServerState> = container.sender.state

  val senderHistory: StateFlow<List<SenderHistoryEntity>> = container.sender.observeHistory()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _networkState = MutableStateFlow(NetworkUiState())
  val networkState: StateFlow<NetworkUiState> = _networkState.asStateFlow()

  val savedServers: StateFlow<List<NetworkServerEntity>> = container.network.observeSavedServers()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _appManagerState = MutableStateFlow(AppManagerUiState())
  val appManagerState: StateFlow<AppManagerUiState> = _appManagerState.asStateFlow()

  val downloads: StateFlow<List<DownloadHistoryEntity>> = container.downloader.observeDownloads()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _activeDownloadProgress = MutableStateFlow<Pair<String, Float>?>(null)
  val activeDownloadProgress: StateFlow<Pair<String, Float>?> = _activeDownloadProgress.asStateFlow()

  private val _noteEditorState = MutableStateFlow(NoteEditorUiState())
  val noteEditorState: StateFlow<NoteEditorUiState> = _noteEditorState.asStateFlow()

  private val _vaultState = MutableStateFlow(VaultUiState())
  val vaultState: StateFlow<VaultUiState> = _vaultState.asStateFlow()

  val vaultPortalState: StateFlow<VaultPortalState> = container.vaultWebPortal.state

  val bottomBarTabs: StateFlow<List<String>> = container.bottomBarPrefs.activeTabIds

  private val _isLaunchingExternalPicker = MutableStateFlow(false)
  val isLaunchingExternalPicker: StateFlow<Boolean> = _isLaunchingExternalPicker.asStateFlow()

  private var isRefreshingGoogleSilently = false

  private val _messages = MutableSharedFlow<String>()
  val messages = _messages.asSharedFlow()

  init {
    viewModelScope.launch {
      container.preferences.driveAccountEmail.collect { email ->
        val hasAccount = !email.isNullOrBlank()
        _driveState.value = _driveState.value.copy(accountEmail = email, isAuthorized = hasAccount)
        _googlePhotosState.value = _googlePhotosState.value.copy(accountEmail = email, isAuthorized = hasAccount)
        if (hasAccount) {
          refreshGoogleSessionsSilently()
        }
      }
    }
    viewModelScope.launch {
      container.preferences.driveAccountName.collect { name ->
        _driveState.value = _driveState.value.copy(accountName = name)
        _googlePhotosState.value = _googlePhotosState.value.copy(accountName = name)
      }
    }
  }

  fun toggleBottomBarTab(tabId: String): Boolean = container.bottomBarPrefs.toggleTab(tabId)

  fun moveBottomBarTab(tabId: String, direction: Int) {
    container.bottomBarPrefs.moveTab(tabId, direction)
  }

  fun resetBottomBarTabs() {
    container.bottomBarPrefs.resetDefaults()
  }

  fun setExternalPickerActive(active: Boolean) {
    _isLaunchingExternalPicker.value = active
  }

  fun refreshGoogleSessionsSilently(forceDriveReload: Boolean = false) {
    val email = _driveState.value.accountEmail ?: _googlePhotosState.value.accountEmail
    if (email.isNullOrBlank() || isRefreshingGoogleSilently) return
    isRefreshingGoogleSilently = true
    viewModelScope.launch {
      try {
        if (_driveState.value.accessToken.isNullOrBlank() || forceDriveReload) {
          when (val driveRes = container.googleDrive.trySilentAuthorize()) {
            is DriveAuthStepResult.Authorized -> {
              _driveState.value = _driveState.value.copy(
                isAuthorized = true,
                accessToken = driveRes.accessToken,
                accountEmail = driveRes.email ?: _driveState.value.accountEmail,
                accountName = driveRes.name ?: _driveState.value.accountName,
                errorMessage = null,
              )
              if (_driveState.value.files.isEmpty() || forceDriveReload) {
                loadDriveFolder(_driveState.value.currentFolderId, resetStack = false)
              }
            }
            is DriveAuthStepResult.NeedsConsent -> Unit // Wait silently until user taps action
            is DriveAuthStepResult.Error -> Unit
          }
        }
        if (_googlePhotosState.value.accessToken.isNullOrBlank()) {
          when (val photosRes = container.googlePhotos.trySilentAuthorize()) {
            is PhotosAuthStepResult.Authorized -> {
              _googlePhotosState.value = _googlePhotosState.value.copy(
                isAuthorized = true,
                accessToken = photosRes.accessToken,
                accountEmail = photosRes.email ?: _googlePhotosState.value.accountEmail,
                accountName = photosRes.name ?: _googlePhotosState.value.accountName,
                errorMessage = null,
              )
            }
            is PhotosAuthStepResult.NeedsConsent -> Unit
            is PhotosAuthStepResult.Error -> Unit
          }
        }
      } finally {
        isRefreshingGoogleSilently = false
      }
    }
  }

  private suspend fun ensureValidDriveToken(): String? {
    _driveState.value.accessToken?.takeIf { it.isNotBlank() }?.let { return it }
    if (_driveState.value.accountEmail.isNullOrBlank()) return null
    return when (val res = container.googleDrive.trySilentAuthorize()) {
      is DriveAuthStepResult.Authorized -> {
        _driveState.value = _driveState.value.copy(
          isAuthorized = true,
          accessToken = res.accessToken,
          accountEmail = res.email ?: _driveState.value.accountEmail,
          accountName = res.name ?: _driveState.value.accountName,
          errorMessage = null,
        )
        res.accessToken
      }
      is DriveAuthStepResult.NeedsConsent -> {
        _driveState.value = _driveState.value.copy(pendingConsentIntent = res.pendingIntent)
        null
      }
      is DriveAuthStepResult.Error -> null
    }
  }

  private suspend fun ensureValidPhotosToken(): String? {
    _googlePhotosState.value.accessToken?.takeIf { it.isNotBlank() }?.let { return it }
    if (_googlePhotosState.value.accountEmail.isNullOrBlank()) return null
    return when (val res = container.googlePhotos.trySilentAuthorize()) {
      is PhotosAuthStepResult.Authorized -> {
        _googlePhotosState.value = _googlePhotosState.value.copy(
          isAuthorized = true,
          accessToken = res.accessToken,
          accountEmail = res.email ?: _googlePhotosState.value.accountEmail,
          accountName = res.name ?: _googlePhotosState.value.accountName,
          errorMessage = null,
        )
        res.accessToken
      }
      is PhotosAuthStepResult.NeedsConsent -> {
        _googlePhotosState.value = _googlePhotosState.value.copy(pendingConsentIntent = res.pendingIntent)
        null
      }
      is PhotosAuthStepResult.Error -> null
    }
  }

  // --- Google Drive ---
  fun connectGoogleDrive(activity: Activity) {
    viewModelScope.launch {
      _driveState.value = _driveState.value.copy(isLoading = true, errorMessage = null)
      when (val res = container.googleDrive.authorizeDrive(activity)) {
        is DriveAuthStepResult.Authorized -> {
          _driveState.value = _driveState.value.copy(
            isAuthorized = true,
            accessToken = res.accessToken,
            accountEmail = res.email ?: _driveState.value.accountEmail,
            accountName = res.name ?: _driveState.value.accountName,
            isLoading = false,
          )
          loadDriveFolder("root", resetStack = true)
        }
        is DriveAuthStepResult.NeedsConsent -> {
          _driveState.value = _driveState.value.copy(
            isLoading = false,
            pendingConsentIntent = res.pendingIntent,
          )
        }
        is DriveAuthStepResult.Error -> {
          _driveState.value = _driveState.value.copy(
            isLoading = false,
            errorMessage = res.message,
          )
        }
      }
    }
  }

  fun consumePendingDriveConsent() {
    _driveState.value = _driveState.value.copy(pendingConsentIntent = null)
  }

  fun onDriveConsentIntentResult(data: Intent?) {
    viewModelScope.launch {
      _driveState.value = _driveState.value.copy(isLoading = true)
      when (val res = container.googleDrive.handleAuthorizationIntentResult(data)) {
        is DriveAuthStepResult.Authorized -> {
          _driveState.value = _driveState.value.copy(
            isAuthorized = true,
            accessToken = res.accessToken,
            accountEmail = res.email ?: _driveState.value.accountEmail,
            accountName = res.name ?: _driveState.value.accountName,
            isLoading = false,
            errorMessage = null,
          )
          loadDriveFolder("root", resetStack = true)
        }
        is DriveAuthStepResult.Error -> {
          _driveState.value = _driveState.value.copy(isLoading = false, errorMessage = res.message)
        }
        is DriveAuthStepResult.NeedsConsent -> Unit
      }
    }
  }

  fun disconnectGoogleDrive() {
    viewModelScope.launch {
      container.googleDrive.signOut()
      photosPollingJob?.cancel()
      _driveState.value = DriveUiState()
      _googlePhotosState.value = GooglePhotosUiState()
      _vaultState.value = _vaultState.value.copy(driveBackups = emptyList(), driveStatusMessage = null)
      emitMessage("Sesión de Google cerrada")
    }
  }

  // --- Google Photos (Picker API + Library Append) ---
  fun connectGooglePhotos(activity: Activity) {
    viewModelScope.launch {
      _googlePhotosState.value = _googlePhotosState.value.copy(isLoading = true, errorMessage = null)
      when (val res = container.googlePhotos.authorizeGooglePhotos(activity)) {
        is PhotosAuthStepResult.Authorized -> {
          _googlePhotosState.value = _googlePhotosState.value.copy(
            isAuthorized = true,
            accessToken = res.accessToken,
            accountEmail = res.email ?: _googlePhotosState.value.accountEmail,
            accountName = res.name ?: _googlePhotosState.value.accountName,
            isLoading = false,
            errorMessage = null,
          )
          emitMessage("Google Photos conectado")
        }
        is PhotosAuthStepResult.NeedsConsent -> {
          _googlePhotosState.value = _googlePhotosState.value.copy(
            isLoading = false,
            pendingConsentIntent = res.pendingIntent,
          )
        }
        is PhotosAuthStepResult.Error -> {
          _googlePhotosState.value = _googlePhotosState.value.copy(
            isLoading = false,
            errorMessage = res.message,
          )
        }
      }
    }
  }

  fun consumePendingPhotosConsent() {
    _googlePhotosState.value = _googlePhotosState.value.copy(pendingConsentIntent = null)
  }

  fun onPhotosConsentIntentResult(data: Intent?) {
    viewModelScope.launch {
      _googlePhotosState.value = _googlePhotosState.value.copy(isLoading = true)
      when (val res = container.googlePhotos.handleAuthorizationIntentResult(data)) {
        is PhotosAuthStepResult.Authorized -> {
          _googlePhotosState.value = _googlePhotosState.value.copy(
            isAuthorized = true,
            accessToken = res.accessToken,
            accountEmail = res.email ?: _googlePhotosState.value.accountEmail,
            accountName = res.name ?: _googlePhotosState.value.accountName,
            isLoading = false,
            errorMessage = null,
          )
          emitMessage("Permisos de Google Photos confirmados")
        }
        is PhotosAuthStepResult.Error -> {
          _googlePhotosState.value = _googlePhotosState.value.copy(
            isLoading = false,
            errorMessage = res.message,
          )
        }
        is PhotosAuthStepResult.NeedsConsent -> Unit
      }
    }
  }

  fun disconnectGooglePhotos() {
    photosPollingJob?.cancel()
    _googlePhotosState.value = GooglePhotosUiState(
      accountEmail = _driveState.value.accountEmail,
      accountName = _driveState.value.accountName,
    )
    emitMessage("Sesión de Google Photos desconectada")
  }

  fun startGooglePhotosPickerSession(context: Context) {
    photosPollingJob?.cancel()
    viewModelScope.launch {
      val token = ensureValidPhotosToken() ?: run {
        emitMessage("Conecta tu cuenta de Google Photos primero")
        return@launch
      }
      _googlePhotosState.value = _googlePhotosState.value.copy(
        isLoading = true,
        errorMessage = null,
      )
      container.googlePhotos.createPickerSession(token)
        .onSuccess { session ->
          _googlePhotosState.value = _googlePhotosState.value.copy(
            activeSession = session,
            isLoading = false,
            isPollingSession = true,
          )
          container.googlePhotos.launchPickerUriInCustomTab(context, session.pickerUri)
          beginPollingPickerSession(token, session)
        }
        .onFailure { err ->
          _googlePhotosState.value = _googlePhotosState.value.copy(
            isLoading = false,
            isPollingSession = false,
            errorMessage = err.localizedMessage,
          )
        }
    }
  }

  private fun beginPollingPickerSession(token: String, initialSession: PickerSessionInfo) {
    photosPollingJob?.cancel()
    photosPollingJob = viewModelScope.launch {
      var attempts = 0
      var intervalMs = initialSession.pollIntervalMs
      while (isActive && attempts < 100) {
        delay(intervalMs)
        attempts++
        val statusRes = container.googlePhotos.getPickerSessionStatus(token, initialSession.sessionId)
        val status = statusRes.getOrNull() ?: continue
        intervalMs = status.pollIntervalMs
        if (status.mediaItemsSet) {
          fetchPickedPhotosFromSession(token, status)
          break
        }
      }
      _googlePhotosState.value = _googlePhotosState.value.copy(isPollingSession = false)
    }
  }

  fun pollActivePhotosPickerNow() {
    val token = _googlePhotosState.value.accessToken ?: return
    val session = _googlePhotosState.value.activeSession ?: return
    if (session.mediaItemsSet) return
    viewModelScope.launch {
      container.googlePhotos.getPickerSessionStatus(token, session.sessionId)
        .onSuccess { status ->
          if (status.mediaItemsSet) {
            photosPollingJob?.cancel()
            fetchPickedPhotosFromSession(token, status)
          }
        }
    }
  }

  private suspend fun fetchPickedPhotosFromSession(token: String, session: PickerSessionInfo) {
    _googlePhotosState.value = _googlePhotosState.value.copy(
      activeSession = session,
      isLoading = true,
      isPollingSession = false,
    )
    container.googlePhotos.fetchPickedMediaItems(token, session.sessionId)
      .onSuccess { items ->
        val merged = (items + _googlePhotosState.value.pickedItems).distinctBy { it.id }
        _googlePhotosState.value = _googlePhotosState.value.copy(
          pickedItems = merged,
          isLoading = false,
          errorMessage = null,
        )
        merged.take(16).forEach { item ->
          ensurePickedPhotoCached(item)
        }
      }
      .onFailure { err ->
        _googlePhotosState.value = _googlePhotosState.value.copy(
          isLoading = false,
          errorMessage = err.localizedMessage,
        )
      }
  }

  fun ensurePickedPhotoCached(item: GooglePhotoMediaItem) {
    val token = _googlePhotosState.value.accessToken ?: return
    if (item.cachedFilePath != null && File(item.cachedFilePath).exists()) return
    if (item.id in _googlePhotosState.value.downloadingItemIds) return
    viewModelScope.launch {
      _googlePhotosState.value = _googlePhotosState.value.copy(
        downloadingItemIds = _googlePhotosState.value.downloadingItemIds + item.id,
      )
      container.googlePhotos.downloadMediaItemHighRes(token, item)
        .onSuccess { cached ->
          _googlePhotosState.value = _googlePhotosState.value.copy(
            downloadingItemIds = _googlePhotosState.value.downloadingItemIds - item.id,
            pickedItems = _googlePhotosState.value.pickedItems.map { existing ->
              if (existing.id == item.id) existing.copy(cachedFilePath = cached.absolutePath) else existing
            },
          )
        }
        .onFailure {
          _googlePhotosState.value = _googlePhotosState.value.copy(
            downloadingItemIds = _googlePhotosState.value.downloadingItemIds - item.id,
          )
        }
    }
  }

  fun preparePickedPhotoForEdit(item: GooglePhotoMediaItem, onReadyPath: (String) -> Unit) {
    val existing = item.cachedFilePath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }
    if (existing != null) {
      onReadyPath(existing.absolutePath)
      return
    }
    val token = _googlePhotosState.value.accessToken ?: run {
      emitMessage("Sesión de Google Photos expirada")
      return
    }
    viewModelScope.launch {
      _googlePhotosState.value = _googlePhotosState.value.copy(
        downloadingItemIds = _googlePhotosState.value.downloadingItemIds + item.id,
      )
      container.googlePhotos.downloadMediaItemHighRes(token, item)
        .onSuccess { cached ->
          _googlePhotosState.value = _googlePhotosState.value.copy(
            downloadingItemIds = _googlePhotosState.value.downloadingItemIds - item.id,
            pickedItems = _googlePhotosState.value.pickedItems.map { cur ->
              if (cur.id == item.id) cur.copy(cachedFilePath = cached.absolutePath) else cur
            },
          )
          onReadyPath(cached.absolutePath)
        }
        .onFailure { err ->
          _googlePhotosState.value = _googlePhotosState.value.copy(
            downloadingItemIds = _googlePhotosState.value.downloadingItemIds - item.id,
          )
          emitMessage("Error descargando foto: ${err.localizedMessage}")
        }
    }
  }

  fun savePickedPhotoToPictures(item: GooglePhotoMediaItem) {
    preparePickedPhotoForEdit(item) { cachedPath ->
      viewModelScope.launch {
        runCatching {
          withContext(Dispatchers.IO) {
            val src = File(cachedPath)
            val picturesDir = File(
              Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                ?: File(container.localFiles.rootStoragePath, "Pictures"),
              "NexoFiles",
            )
            picturesDir.mkdirs()
            val out = File(picturesDir, item.filename.ifBlank { src.name })
            src.copyTo(out, overwrite = true)
            out
          }
        }.onSuccess { out ->
          container.localFiles.recordRecentAction(out)
          emitMessage("Guardado en Pictures/NexoFiles/${out.name}")
        }.onFailure { err ->
          emitMessage("Error guardando imagen: ${err.localizedMessage}")
        }
      }
    }
  }

  fun clearPickedPhotosSession() {
    photosPollingJob?.cancel()
    _googlePhotosState.value = _googlePhotosState.value.copy(
      activeSession = null,
      isPollingSession = false,
      pickedItems = emptyList(),
    )
  }

  fun uploadEditedFileToGooglePhotos(
    editedFile: File,
    format: PhotoExportFormat,
    successMessageFormatter: (String) -> String,
    onConnectRequired: () -> Unit = {},
    onCompleted: () -> Unit = {},
  ) {
    val token = _googlePhotosState.value.accessToken
    if (token.isNullOrBlank()) {
      onConnectRequired()
      return
    }
    viewModelScope.launch {
      _googlePhotosState.value = _googlePhotosState.value.copy(isUploadingCopy = true)
      container.googlePhotos.uploadEditedCopyToGooglePhotos(
        accessToken = token,
        imageFile = editedFile,
        mimeType = format.mimeType,
      ).onSuccess { uploadedName ->
        _googlePhotosState.value = _googlePhotosState.value.copy(isUploadingCopy = false)
        emitMessage(successMessageFormatter(uploadedName))
        onCompleted()
      }.onFailure { err ->
        _googlePhotosState.value = _googlePhotosState.value.copy(isUploadingCopy = false)
        emitMessage("Error subiendo a Google Photos: ${err.localizedMessage}")
      }
    }
  }

  fun recordLocalPhotoSaved(file: File, message: String) {
    viewModelScope.launch {
      container.localFiles.recordRecentAction(file)
      emitMessage(message)
    }
  }

  fun loadDriveFolder(folderId: String, folderName: String? = null, resetStack: Boolean = false) {
    viewModelScope.launch {
      val token = ensureValidDriveToken() ?: return@launch
      val currentStack = _driveState.value.folderStack
      val updatedStack = when {
        resetStack -> listOf("root" to "Mi Unidad")
        folderName != null -> {
          val existingIdx = currentStack.indexOfFirst { it.first == folderId }
          if (existingIdx >= 0) currentStack.take(existingIdx + 1) else currentStack + (folderId to folderName)
        }
        else -> {
          val existingIdx = currentStack.indexOfFirst { it.first == folderId }
          if (existingIdx >= 0) currentStack.take(existingIdx + 1) else currentStack
        }
      }
      _driveState.value = _driveState.value.copy(
        currentFolderId = folderId,
        folderStack = updatedStack,
        isLoading = true,
        errorMessage = null,
      )
      var result = container.googleDrive.listFiles(token, folderId)
      if (result.isFailure && result.exceptionOrNull()?.message?.contains("401") == true) {
        when (val refresh = container.googleDrive.trySilentAuthorize()) {
          is DriveAuthStepResult.Authorized -> {
            _driveState.value = _driveState.value.copy(accessToken = refresh.accessToken, isAuthorized = true)
            result = container.googleDrive.listFiles(refresh.accessToken, folderId)
          }
          else -> Unit
        }
      }
      result
        .onSuccess { files ->
          _driveState.value = _driveState.value.copy(files = files, isLoading = false, errorMessage = null)
        }
        .onFailure { err ->
          _driveState.value = _driveState.value.copy(
            isLoading = false,
            errorMessage = err.localizedMessage,
          )
        }
    }
  }

  fun navigateDriveToBreadcrumb(folderId: String) {
    val stack = _driveState.value.folderStack
    val idx = stack.indexOfFirst { it.first == folderId }
    if (idx >= 0) {
      val trimmed = stack.take(idx + 1)
      _driveState.value = _driveState.value.copy(folderStack = trimmed)
      loadDriveFolder(folderId, resetStack = false)
    }
  }

  fun navigateDriveUp(): Boolean {
    val stack = _driveState.value.folderStack
    if (stack.size <= 1) return false
    val popped = stack.dropLast(1)
    _driveState.value = _driveState.value.copy(folderStack = popped)
    loadDriveFolder(popped.last().first, resetStack = false)
    return true
  }

  fun createDriveFolder(name: String) {
    viewModelScope.launch {
      val token = ensureValidDriveToken() ?: return@launch
      val parent = _driveState.value.currentFolderId
      container.googleDrive.createFolder(token, name, parent)
        .onSuccess {
          loadDriveFolder(parent)
          emitMessage("Carpeta creada en Google Drive")
        }
        .onFailure { emitMessage("Error Drive: ${it.localizedMessage}") }
    }
  }

  fun uploadLocalFileToDrive(localFile: File) {
    val parent = _driveState.value.currentFolderId
    viewModelScope.launch {
      val token = ensureValidDriveToken() ?: run {
        emitMessage("Conecta tu cuenta de Google Drive primero")
        return@launch
      }
      _driveState.value = _driveState.value.copy(isLoading = true)
      container.googleDrive.uploadFile(token, localFile, parent)
        .onSuccess {
          loadDriveFolder(parent)
          emitMessage("Subido a Google Drive: ${it.name}")
        }
        .onFailure {
          _driveState.value = _driveState.value.copy(isLoading = false)
          emitMessage("Error subiendo a Drive: ${it.localizedMessage}")
        }
    }
  }

  fun downloadDriveFile(driveFile: DriveFileItem) {
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
      ?: File(container.localFiles.rootStoragePath, "Download")
    viewModelScope.launch {
      val token = ensureValidDriveToken() ?: return@launch
      container.googleDrive.downloadFile(token, driveFile, downloadsDir)
        .onSuccess { emitMessage("Descargado en Descargas/${it.name}") }
        .onFailure { emitMessage("Error descargando: ${it.localizedMessage}") }
    }
  }

  fun downloadSelectedDriveFiles(files: List<DriveFileItem>) {
    val targets = files.filterNot { it.isFolder }
    if (targets.isEmpty()) return
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
      ?: File(container.localFiles.rootStoragePath, "Download")
    viewModelScope.launch {
      val token = ensureValidDriveToken() ?: return@launch
      var okCount = 0
      for (file in targets) {
        if (container.googleDrive.downloadFile(token, file, downloadsDir).isSuccess) {
          okCount++
        }
      }
      emitMessage("Descargados $okCount/${targets.size} archivo(s) en Descargas")
    }
  }

  fun deleteDriveFile(fileId: String) {
    viewModelScope.launch {
      val token = ensureValidDriveToken() ?: return@launch
      container.googleDrive.deleteFile(token, fileId)
        .onSuccess {
          loadDriveFolder(_driveState.value.currentFolderId)
          emitMessage("Eliminado de Google Drive")
        }
        .onFailure { emitMessage("Error: ${it.localizedMessage}") }
    }
  }

  fun deleteSelectedDriveFiles(fileIds: Set<String>) {
    if (fileIds.isEmpty()) return
    viewModelScope.launch {
      val token = ensureValidDriveToken() ?: return@launch
      var deleted = 0
      for (id in fileIds) {
        if (container.googleDrive.deleteFile(token, id).isSuccess) deleted++
      }
      loadDriveFolder(_driveState.value.currentFolderId)
      emitMessage("Eliminados $deleted elemento(s) de Drive")
    }
  }

  fun addAutoBackupRule(localFolderPath: String, wifiOnly: Boolean, frequency: String = "WIFI_AUTO") {
    val currentDriveFolder = _driveState.value.folderStack.lastOrNull() ?: ("root" to "Mi Unidad")
    viewModelScope.launch {
      container.googleDrive.addAutoBackupRule(
        localFolderPath = localFolderPath,
        driveFolderId = currentDriveFolder.first,
        driveFolderName = currentDriveFolder.second,
        wifiOnly = wifiOnly,
        frequency = frequency,
      )
      emitMessage("Regla de respaldo automático guardada")
    }
  }

  fun toggleAutoBackupRule(rule: AutoBackupRuleEntity, enabled: Boolean) {
    viewModelScope.launch { container.googleDrive.toggleAutoBackupRule(rule, enabled) }
  }

  fun deleteAutoBackupRule(id: Long) {
    viewModelScope.launch { container.googleDrive.deleteAutoBackupRule(id) }
  }

  fun runAutoBackupNow(selectedRuleIds: Set<Long> = emptySet()) {
    viewModelScope.launch {
      val token = ensureValidDriveToken() ?: run {
        emitMessage("Autoriza Google Drive antes de respaldar")
        return@launch
      }
      _driveState.value = _driveState.value.copy(isSyncingBackup = true)
      container.googleDrive.runAutoBackupNow(token, selectedRuleIds.ifEmpty { null })
        .onSuccess { count ->
          _driveState.value = _driveState.value.copy(isSyncingBackup = false)
          emitMessage("Respaldo completado: $count archivo(s) nuevo(s) subido(s)")
        }
        .onFailure { err ->
          _driveState.value = _driveState.value.copy(isSyncingBackup = false)
          emitMessage("Error en respaldo: ${err.localizedMessage}")
        }
    }
  }

  // --- View on PC ---
  fun toggleWebServer() {
    if (webServerState.value.isRunning) {
      container.httpServer.stop()
    } else {
      container.httpServer.start(port = webServerPort.value, rootDirPath = container.localFiles.rootStoragePath)
    }
  }

  fun setWebServerPort(port: Int) {
    viewModelScope.launch { container.preferences.setWebServerPort(port) }
  }

  // --- Sender ---
  fun stageSenderUris(uris: List<Uri>) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      val staged = container.sender.stageUris(uris)
      val settings = container.preferences.allSettings.first()
      container.sender.startSenderServer(
        mode = "SEND",
        saveDirPath = settings.senderSavePath,
        nickname = settings.senderNickname,
      )
      emitMessage("${staged.size} archivo(s) listo(s) para enviar por Wi-Fi")
    }
  }

  fun startSenderReceiveMode() {
    viewModelScope.launch {
      val settings = container.preferences.allSettings.first()
      container.sender.startSenderServer(
        mode = "RECEIVE",
        saveDirPath = settings.senderSavePath,
        nickname = settings.senderNickname,
      )
      emitMessage("Modo Recibir activo en ${settings.senderSavePath}")
    }
  }

  fun startSenderShareAppMode() {
    viewModelScope.launch {
      val settings = container.preferences.allSettings.first()
      container.sender.startSenderServer(
        mode = "SHARE_APP",
        saveDirPath = settings.senderSavePath,
        nickname = settings.senderNickname,
      )
    }
  }

  fun stopSenderServer() {
    container.sender.stopSenderServer()
  }

  fun clearSenderStagedFiles() {
    container.sender.clearStagedFiles()
  }

  fun clearSenderHistory() {
    viewModelScope.launch {
      container.sender.clearHistory()
      emitMessage("Historial de Sender borrado")
    }
  }

  // --- LAN / FTP / WebDAV / SFTP ---
  fun scanLan() {
    if (_networkState.value.isScanningLan) return
    viewModelScope.launch {
      _networkState.value = _networkState.value.copy(isScanningLan = true)
      val found = container.network.scanLanDevices()
      _networkState.value = _networkState.value.copy(lanDevices = found, isScanningLan = false)
      emitMessage("Escaneo LAN finalizado: ${found.size} dispositivo(s)")
    }
  }

  fun saveNetworkServer(
    name: String,
    protocol: RemoteProtocol,
    host: String,
    port: Int,
    username: String,
    password: String,
    ftpMode: String = "PASSIVE",
    encoding: String = "AUTO",
    useHttps: Boolean = false,
    domain: String = "",
    anonymous: Boolean = false,
  ) {
    viewModelScope.launch {
      container.network.saveServer(
        NetworkServerEntity(
          name = name.ifBlank { "$host (${protocol.label})" },
          protocol = protocol.name,
          host = host.trim(),
          port = port,
          username = if (anonymous) "" else username.trim(),
          password = if (anonymous) "" else password,
          ftpMode = ftpMode,
          encoding = encoding,
          useHttps = useHttps,
          domain = domain.trim(),
          anonymous = anonymous,
        )
      )
      emitMessage("Servidor ${protocol.label} guardado")
    }
  }

  fun deleteSavedServer(id: Long) {
    viewModelScope.launch { container.network.deleteServer(id) }
  }

  fun connectRemoteServer(server: NetworkServerEntity, path: String = server.remotePath) {
    viewModelScope.launch {
      _networkState.value = _networkState.value.copy(
        activeServer = server,
        currentRemotePath = path,
        isConnectingRemote = true,
        remoteError = null,
      )
      container.network.listRemoteDirectory(server, path)
        .onSuccess { entries ->
          _networkState.value = _networkState.value.copy(
            remoteEntries = entries,
            isConnectingRemote = false,
          )
        }
        .onFailure { err ->
          _networkState.value = _networkState.value.copy(
            remoteEntries = emptyList(),
            isConnectingRemote = false,
            remoteError = err.localizedMessage,
          )
        }
    }
  }

  fun disconnectRemoteServer() {
    _networkState.value = _networkState.value.copy(
      activeServer = null,
      remoteEntries = emptyList(),
      remoteError = null,
    )
  }

  fun downloadRemoteFile(entry: RemoteFileEntry) {
    val server = _networkState.value.activeServer ?: return
    viewModelScope.launch {
      val settings = container.preferences.allSettings.first()
      val destDir = File(settings.downloadDirectory).takeIf { it.exists() || it.mkdirs() }
        ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        ?: File(container.localFiles.rootStoragePath, "Download")
      container.network.downloadRemoteFile(server, entry.path, File(destDir, entry.name))
        .onSuccess { emitMessage("Descargado desde ${server.name}: ${it.name}") }
        .onFailure { emitMessage("Error descargando: ${it.localizedMessage}") }
    }
  }

  // --- App Manager ---
  fun loadInstalledApps() {
    viewModelScope.launch {
      _appManagerState.value = _appManagerState.value.copy(isLoading = true)
      val backupDir = container.preferences.allSettings.first().appBackupDirectory
      val all = container.appManager.getInstalledApps()
      val backedUp = container.appManager.listBackedUpApks(backupDir)
      _appManagerState.value = _appManagerState.value.copy(
        userApps = all.filter { !it.isSystemApp },
        systemApps = all.filter { it.isSystemApp },
        backedUpApks = backedUp,
        isLoading = false,
      )
    }
  }

  fun setAppSearchQuery(query: String) {
    _appManagerState.value = _appManagerState.value.copy(searchQuery = query)
  }

  fun backupApk(app: InstalledAppInfo) {
    viewModelScope.launch {
      val backupDir = container.preferences.allSettings.first().appBackupDirectory
      container.appManager.backupApk(app, backupDir)
        .onSuccess { file ->
          loadInstalledApps()
          emitMessage("APK respaldado: ${file.name}")
        }
        .onFailure { emitMessage("Error respaldando APK: ${it.localizedMessage}") }
    }
  }

  fun installApk(apkPath: String) {
    container.appManager.launchInstallApk(File(apkPath))
  }

  fun uninstallApp(packageName: String) {
    container.appManager.launchUninstallApp(packageName)
  }

  // --- Downloader ---
  fun readClipboardHttpUrl(): String? = container.downloader.readClipboardHttpUrl()

  fun suggestDownloadFileName(url: String): String = container.downloader.suggestFileNameFromUrl(url)

  fun startDirectDownload(url: String, customName: String?) {
    viewModelScope.launch {
      val settings = container.preferences.allSettings.first()
      _activeDownloadProgress.value = (customName ?: suggestDownloadFileName(url)) to 0f
      container.downloader.startDownload(
        url = url,
        customFileName = customName,
        destinationDirPath = settings.downloadDirectory,
        multithreadBuffer = settings.multithreadTransferEnabled,
        dismissOnFinish = settings.dismissNotifOnTaskFinish,
      ) { down, total ->
        val progress = if (total > 0) (down.toFloat() / total.toFloat()).coerceIn(0f, 1f) else -1f
        _activeDownloadProgress.value = (customName ?: suggestDownloadFileName(url)) to progress
      }.onSuccess {
        _activeDownloadProgress.value = null
        emitMessage("Descarga completada: ${it.fileName}")
      }.onFailure {
        _activeDownloadProgress.value = null
        emitMessage("Error en descarga: ${it.localizedMessage}")
      }
    }
  }

  fun deleteDownloadHistory(id: Long) {
    viewModelScope.launch { container.downloader.deleteDownloadRecord(id) }
  }

  fun clearDownloadHistory() {
    viewModelScope.launch { container.downloader.clearHistory() }
  }

  // --- Note & Code Editor ---
  fun openFileInEditor(path: String?, isVaultMode: Boolean = false) {
    if (path == null) {
      _noteEditorState.value = NoteEditorUiState(
        filePath = null,
        fileName = "nota_${System.currentTimeMillis() % 10000}.md",
        content = "",
        hexContent = "",
        isBinary = false,
        isTruncated = false,
        isDirty = false,
        isVaultMode = isVaultMode,
      )
      return
    }
    viewModelScope.launch {
      _noteEditorState.value = _noteEditorState.value.copy(isLoading = true, isVaultMode = isVaultMode)
      val file = File(path)
      val inspection = withContext(Dispatchers.IO) {
        if (com.example.util.FormatUtils.isTextEditable(file.name) && file.exists() && file.length() <= 512L * 1024L) {
          val text = runCatching { file.readText() }.getOrElse { "" }
          com.example.util.FormatUtils.FileContentInspection(
            textPreview = text,
            hexPreview = com.example.util.FormatUtils.readTextOrHexPreview(file, maxBytes = 16 * 1024).hexPreview,
            isBinary = false,
            isTruncated = false,
            totalBytes = file.length(),
          )
        } else {
          com.example.util.FormatUtils.readTextOrHexPreview(file)
        }
      }
      _noteEditorState.value = _noteEditorState.value.copy(
        filePath = file.absolutePath,
        fileName = file.name,
        content = inspection.textPreview,
        hexContent = inspection.hexPreview,
        isBinary = inspection.isBinary,
        isTruncated = inspection.isTruncated,
        isDirty = false,
        isLoading = false,
        isVaultMode = isVaultMode,
      )
    }
  }

  fun updateEditorContent(newContent: String) {
    _noteEditorState.value = _noteEditorState.value.copy(content = newContent, isDirty = true)
  }

  fun updateEditorFileName(name: String) {
    _noteEditorState.value = _noteEditorState.value.copy(fileName = name, isDirty = true)
  }

  fun saveEditorFile(defaultDirPath: String) {
    viewModelScope.launch {
      val st = _noteEditorState.value
      val target = if (st.filePath != null) File(st.filePath) else File(defaultDirPath, st.fileName.ifBlank { "nota.txt" })
      runCatching {
        withContext(Dispatchers.IO) {
          target.parentFile?.mkdirs()
          target.writeText(st.content)
        }
        if (!st.isVaultMode) {
          container.localFiles.recordRecentAction(target)
        }
      }.onSuccess {
        _noteEditorState.value = st.copy(filePath = target.absolutePath, fileName = target.name, isDirty = false)
        emitMessage("Archivo guardado: ${target.name}")
      }.onFailure {
        emitMessage("Error al guardar: ${it.localizedMessage}")
      }
    }
  }

  // --- Private Vault ---
  fun unlockVault() {
    viewModelScope.launch {
      _vaultState.value = _vaultState.value.copy(isUnlocked = true, isBusy = true)
      val items = container.vault.listVaultItems()
      _vaultState.value = _vaultState.value.copy(items = items, isBusy = false)
      if (!_driveState.value.accessToken.isNullOrBlank()) {
        refreshVaultDriveBackups()
      }
    }
  }

  fun lockVault() {
    container.vaultWebPortal.stop(deleteStagedFiles = true)
    viewModelScope.launch { container.vault.purgePreviewCache() }
    _vaultState.value = VaultUiState(isUnlocked = false, items = emptyList())
  }

  fun purgeVaultPreviewCache() {
    viewModelScope.launch { container.vault.purgePreviewCache() }
  }

  fun addUriToVault(uri: Uri) {
    viewModelScope.launch {
      _vaultState.value = _vaultState.value.copy(isBusy = true)
      container.vault.lockFromUri(uri)
        .onSuccess {
          val items = container.vault.listVaultItems()
          _vaultState.value = _vaultState.value.copy(items = items, isBusy = false)
          emitMessage("Cifrado en Bóveda Privada: ${it.originalName}")
        }
        .onFailure {
          _vaultState.value = _vaultState.value.copy(isBusy = false)
                if (com.example.ui.components.NexoSecureVaultManager.autoBackupOnImport) { backupVaultToDrive("NEXO_HERMETIC_AUTO_KEY_2026") }
          emitMessage("Error cifrando: ${it.localizedMessage}")
        }
    }
  }

  /**
   * Decrypts a vault entry completely into `cacheDir/vault_preview/` and hands the closed
   * local File + VaultFileItem to MainActivity so it opens immediately in the internal viewer/player.
   */
  fun openVaultItem(item: VaultFileItem, onReady: (File, VaultFileItem) -> Unit) {
    if (_vaultState.value.openingItemId != null) return
    viewModelScope.launch {
      _vaultState.value = _vaultState.value.copy(openingItemId = item.id)
      container.vault.decryptToCacheAndOpen(item)
        .onSuccess { decryptedFile ->
          _vaultState.value = _vaultState.value.copy(openingItemId = null)
          onReady(decryptedFile, item)
        }
        .onFailure { err ->
          _vaultState.value = _vaultState.value.copy(openingItemId = null)
          emitMessage("No se pudo descifrar: ${err.localizedMessage}")
        }
    }
  }

  fun restoreVaultItem(item: VaultFileItem) {
    viewModelScope.launch {
      val fallback = File(container.localFiles.rootStoragePath, "Download")
      container.vault.restoreToOriginalOrDownloads(item, fallback)
        .onSuccess {
          val items = container.vault.listVaultItems()
          _vaultState.value = _vaultState.value.copy(items = items)
          emitMessage("Restaurado de la Bóveda: ${it.name}")
        }
        .onFailure { emitMessage("Error restaurando: ${it.localizedMessage}") }
    }
  }

  fun deleteVaultItem(item: VaultFileItem) {
    viewModelScope.launch {
      container.vault.deleteFromVault(item)
      _vaultState.value = _vaultState.value.copy(items = container.vault.listVaultItems())
      emitMessage("Eliminado de la Bóveda")
    }
  }

  // --- Vault Encrypted Google Drive Backup (.nexo_vault) ---
  fun refreshVaultDriveBackups() {
    viewModelScope.launch {
      val token = ensureValidDriveToken() ?: run {
        _vaultState.value = _vaultState.value.copy(
          driveStatusMessage = "Conecta Google Drive para listar respaldos cifrados",
        )
        return@launch
      }
      _vaultState.value = _vaultState.value.copy(isDriveBusy = true, driveStatusMessage = null)
      container.googleDrive.listVaultBackups(token)
        .onSuccess { driveFiles ->
          val entries = driveFiles.map { df ->
            VaultBackupEntry(
              driveFile = df,
              originalName = df.name.removeSuffix(".nexo_vault"),
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
            driveStatusMessage = if ((err.localizedMessage ?: "").contains("401") || (err.localizedMessage ?: "").contains("UNAUTHENTICATED")) "Sesión de Google Drive expirada. Abre Google Drive y toca Reconectar." else "Error Drive: ${err.localizedMessage}",
          )
        }
    }
  }

  val effectivePassphrase = "NEXO_HERMETIC_AUTO_KEY_2026"
  fun backupVaultToDrive(rawPassphrase: String, selectedIds: Set<String> = emptySet()) {
    val passphrase = rawPassphrase.trim().ifBlank { effectivePassphrase }
    viewModelScope.launch {
      val token = ensureValidDriveToken() ?: run {
        emitMessage("Conecta tu cuenta de Google Drive primero")
        return@launch
      }
      _vaultState.value = _vaultState.value.copy(
        isDriveBusy = true,
        driveStatusMessage = "Cifrando archivos .nexo_vault y subiendo a Drive…",
      )
      val stageDir = File(context.cacheDir, "vault_drive_stage").apply {
        deleteRecursively()
        mkdirs()
      }
      runCatching {
        val folderId = container.googleDrive.ensureVaultBackupFolder(token).getOrThrow()
        val exported = if (selectedIds.isEmpty()) {
          container.vault.exportAllVaultToNxv(effectivePassphrase, stageDir).getOrThrow()
        } else {
          val targets = _vaultState.value.items.filter { it.id in selectedIds }
          container.vault.exportSelectedVaultToNxv(targets, effectivePassphrase, stageDir).getOrThrow()
        }
        var uploaded = 0
        for (nxv in exported) {
          val res = container.googleDrive.uploadFile(token, nxv, folderId)
          if (res.isSuccess) uploaded++
        }
        uploaded
      }.onSuccess { count ->
        stageDir.deleteRecursively()
        _vaultState.value = _vaultState.value.copy(
          isDriveBusy = false,
          driveStatusMessage = "Respaldo Drive completado ($count archivo(s) .nexo_vault)",
        )
        refreshVaultDriveBackups()
        emitMessage("Respaldo cifrado subido a Drive: $count archivo(s)")
      }.onFailure { err ->
        stageDir.deleteRecursively()
        _vaultState.value = _vaultState.value.copy(
          isDriveBusy = false,
          driveStatusMessage = "Error en respaldo: ${err.localizedMessage}",
        )
        emitMessage("Error respaldando en Drive: ${err.localizedMessage}")
      }
    }
  }

  fun restoreVaultBackupFromDrive(entry: VaultBackupEntry, passphrase: String) {
    restoreSelectedVaultBackupsFromDrive(listOf(entry), passphrase)
  }

  fun restoreSelectedVaultBackupsFromDrive(entries: List<VaultBackupEntry>, passphrase: String) {
    if (entries.isEmpty()) return
    if (passphrase.length < 4) {
      emitMessage("Introduce la contraseña del respaldo .nexo_vault")
      return
    }
    viewModelScope.launch {
      val token = ensureValidDriveToken() ?: run {
        emitMessage("Conecta tu cuenta de Google Drive primero")
        return@launch
      }
      _vaultState.value = _vaultState.value.copy(
        isDriveBusy = true,
        driveStatusMessage = "Descargando y descifrando ${entries.size} archivo(s)…",
      )
      val tmpDir = File(context.cacheDir, "vault_restore_stage").apply {
        deleteRecursively()
        mkdirs()
      }
      runCatching {
        var restoredCount = 0
        var lastRestoredName = ""
        for (entry in entries) {
          val downloaded = container.googleDrive.downloadFile(token, entry.driveFile, tmpDir).getOrThrow()
          val item = container.vault.importNxvToVault(downloaded, if (passphrase.length < 4) "NEXO_HERMETIC_AUTO_KEY_2026" else passphrase).getOrThrow()
          restoredCount++
          lastRestoredName = item.originalName
        }
        restoredCount to lastRestoredName
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
        emitMessage("Contraseña incorrecta o archivo corrupto")
      }
    }
  }

  // --- Ephemeral Web / iPhone Remote Portal ---
  fun startVaultWebPortal(passphrase: String, ttlMinutes: Int = 15) {
    if (passphrase.length < 4) {
      emitMessage("La clave de sesión debe tener al menos 4 caracteres")
      return
    }
    val items = _vaultState.value.items
    if (items.isEmpty()) {
      emitMessage("La bóveda está vacía")
      return
    }
    viewModelScope.launch {
      _vaultState.value = _vaultState.value.copy(isBusy = true)
      val stageDir = File(context.cacheDir, "vault_portal_stage").apply {
        deleteRecursively()
        mkdirs()
      }
      runCatching {
        items.map { item ->
          val nxv = container.vault.exportVaultItemToNxv(item, passphrase, stageDir).getOrThrow()
          StagedVaultPortalBlob(
            id = item.id,
            originalName = item.originalName,
            mimeType = item.mimeType,
            sizeBytes = item.sizeBytes,
            nxvFile = nxv,
          )
        }
      }.onSuccess { blobs ->
        container.vaultWebPortal.start(blobs = blobs, stageRootDir = stageDir, ttlMinutes = ttlMinutes)
        _vaultState.value = _vaultState.value.copy(isBusy = false)
        emitMessage("Portal Web efímero activo (${blobs.size} archivo(s))")
      }.onFailure { err ->
        stageDir.deleteRecursively()
        _vaultState.value = _vaultState.value.copy(isBusy = false)
        emitMessage("Error iniciando portal Web: ${err.localizedMessage}")
      }
    }
  }

  fun stopVaultWebPortal() {
    container.vaultWebPortal.stop(deleteStagedFiles = true)
    emitMessage("Portal Web de Bóveda detenido")
  }

  private fun emitMessage(msg: String) {
    viewModelScope.launch { _messages.emit(msg) }
  }

  class Factory(
    private val context: Context,
    private val container: AppContainer,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return ToolsAndNetworkViewModel(context.applicationContext, container) as T
    }
  }
}
