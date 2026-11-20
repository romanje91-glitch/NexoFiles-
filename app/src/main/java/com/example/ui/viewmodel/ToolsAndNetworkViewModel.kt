package com.example.ui.viewmodel

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import com.example.MainActivity
import com.example.NexoNotificationPrefs
import com.example.NexoMultiGoogleStore
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import com.example.NexoVaultSecurityStore
import com.example.NexoVaultExtraStore
import com.example.NexoGlobalClipboard
import com.example.NexoGlobalFileOps
import com.example.NexoClipMode
import com.example.NexoClipOrigin
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
  val showBackupProgressDialog: Boolean = false,
  val isBackupPaused: Boolean = false,
  val backupProgress: Float = 0f,
  val backupDoneCount: Int = 0,
  val backupTotalCount: Int = 0,
  val backupSkippedCount: Int = 0,
  val backupEtaSeconds: Int = -1,
  val backupCurrentFileName: String = "",
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
      launch(Dispatchers.IO) {
        items.filter { it.sizeBytes in 1L..(25L * 1024L * 1024L) }.take(24).forEach { vItem ->
          if (!_vaultState.value.isUnlocked) return@launch
          runCatching { container.vault.decryptToCacheAndOpen(vItem) }
        }
      }
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

  private var vaultBackupJob: Job? = null
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
    // refresh on next open
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
      val dl = container.googleDrive.downloadFile(srcToken, file, tmp)
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
  }
  private val vaultBackupNotifId = 4092
  private val vaultBackupChannelId = "nexo_vault_backup_ch"

  private fun updateVaultBackupNotification(title: String, text: String, progress: Int, ongoing: Boolean) {
    if (!NexoNotificationPrefs.isEnabled(context, NexoNotificationPrefs.KEY_VAULT_BACKUP)) return
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
        .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).putExtra("nexo_open_vault", true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
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


  fun deleteVaultCloudBackup(item: com.example.data.model.VaultFileItem) {
    val match = _vaultState.value.driveBackups.firstOrNull { b ->
      val clean = b.originalName.substringAfterLast(" / ").removeSuffix(".nexo_vault")
      clean.equals(item.originalName, ignoreCase = true) ||
        b.driveFile.name.removeSuffix(".nexo_vault").equals(item.originalName, ignoreCase = true) ||
        b.driveFile.name.contains(item.id, ignoreCase = true)
    }
    if (match == null) {
      emitMessage("No existe respaldo en la nube para ${item.originalName}")
      return
    }
    viewModelScope.launch {
      withDriveTokenRetry { token -> container.googleDrive.deleteFile(token, match.driveFile.id) }
        .onSuccess {
          emitMessage("Respaldo eliminado de la nube: ${item.originalName}")
          refreshVaultDriveBackups()
        }
        .onFailure { err ->
          emitMessage("Error eliminando en la nube: ${err.localizedMessage}")
        }
    }
  }

  fun cancelVaultBackup() {
    vaultBackupPaused = false
    vaultBackupJob?.cancel()
    refreshVaultDriveBackups()
    vaultBackupJob = null
    cancelVaultBackupNotification()
    _vaultState.value = _vaultState.value.copy(
      isDriveBusy = false,
      showBackupProgressDialog = false,
      isBackupPaused = false,
      backupEtaSeconds = -1,
      driveStatusMessage = "Respaldo cancelado por el usuario",
    )
    emitMessage("Respaldo en Drive cancelado")
  }

  fun reopenVaultBackupProgress() { _vaultState.value = _vaultState.value.copy(showBackupProgressDialog = true) }

  fun dismissVaultBackupProgress() {
    _vaultState.value = _vaultState.value.copy(showBackupProgressDialog = false)
  }

  fun backupVaultToDrive(
    rawPassphrase: String = "",
    selectedIds: Set<String> = emptySet(),
    showProgressModal: Boolean = false,
  ) {
    val passphrase = rawPassphrase.trim().takeIf { it.length >= 4 } ?: NexoVaultSecurityStore.getBackupPassphrase(context)
    vaultBackupPaused = false
    vaultBackupJob?.cancel()
    vaultBackupJob = viewModelScope.launch {
      if (showProgressModal) {
        updateVaultBackupNotification("NexoFiles · Respaldo Bóveda", "Verificando archivos en Drive…", 5, true)
      }
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

          val uploadedCount = AtomicInteger(0)
          val doneCount = AtomicInteger(0)
          val folderIdCache = mutableMapOf<String, String>()
          // Pre-resolver carpetas únicas UNA sola vez (evita N llamadas repetidas a Drive)
          pendingUpload.map { NexoVaultExtraStore.itemFolderMap[it.id].orEmpty() }.distinct().forEach { fn ->
            folderIdCache[fn] = container.googleDrive.ensureVaultSubfolder(token, fn).getOrThrow()
          }
          val sem = Semaphore(3)
          kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            pendingUpload.map { item ->
              async {
                sem.withPermit {
                  ensureActive()
                  while (vaultBackupPaused) {
                    ensureActive()
                    delay(300)
                  }
                  val folderName = NexoVaultExtraStore.itemFolderMap[item.id].orEmpty()
                  val targetFolderId = folderIdCache[folderName] ?: container.googleDrive.ensureVaultSubfolder(token, folderName).getOrThrow()
                  val singleDir = File(stageDir, item.id).apply { mkdirs() }
                  try {
                    val exported = container.vault.exportSelectedVaultToNxv(listOf(item), passphrase, singleDir).getOrThrow()
                    for (nxv in exported) {
                      val res = container.googleDrive.uploadFile(token, nxv, targetFolderId)
                      if (res.isSuccess) uploadedCount.incrementAndGet()
                    }
                  } finally {
                    singleDir.deleteRecursively()
                  }
                  val doneNow = doneCount.incrementAndGet()
                  val pctFloat = (doneNow.toFloat() / totalToUpload.toFloat()).coerceIn(0.05f, 1f)
                  val curPct = (pctFloat * 100f).toInt().coerceIn(5, 99)
                  val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(300L)
                  val remSec = (((totalToUpload - doneNow) * (elapsedMs / doneNow)) / 1000L).toInt().coerceAtLeast(0)
                  updateVaultBackupNotification("Respaldo Bóveda ($curPct%)", "Subiendo ${item.originalName} ($doneNow/$totalToUpload)", curPct, true)
                  _vaultState.value = _vaultState.value.copy(
                    backupCurrentFileName = item.originalName,
                    backupDoneCount = doneNow,
                    backupProgress = pctFloat,
                    backupEtaSeconds = remSec,
                  )
                }
              }
            }.awaitAll()
          }
          Triple(uploadedCount.get(), skippedAlreadySynced, totalToUpload)
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
        updateVaultBackupNotification("NexoFiles · Respaldo Bóveda", msg, 100, false)
        refreshVaultDriveBackups()
        emitMessage(msg)
      }.onFailure { err ->
        stageDir.deleteRecursively()
        if (err is CancellationException) {
          cancelVaultBackupNotification()
          return@launch
        }
        updateVaultBackupNotification("NexoFiles · Error en respaldo", err.localizedMessage ?: "Error", 0, false)
        _vaultState.value = _vaultState.value.copy(
          isDriveBusy = false,
          backupEtaSeconds = -1,
          backupCurrentFileName = "Error: ${err.localizedMessage}",
          driveStatusMessage = "Error en respaldo: ${err.localizedMessage}",
        )
        emitMessage("Error respaldando en Drive: ${err.localizedMessage}")
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
