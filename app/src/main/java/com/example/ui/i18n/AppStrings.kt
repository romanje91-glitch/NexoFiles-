package com.example.ui.i18n

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.os.LocaleListCompat
import com.example.data.model.AppLanguageOption
import com.example.data.model.AutoBackupFrequency
import com.example.data.model.FileCategory
import com.example.data.model.LayoutViewMode
import com.example.data.model.SearchEngineMode
import com.example.data.model.SortBy
import com.example.data.model.ThemeMode
import com.example.data.model.VaultLockMethod
import java.util.Locale

interface AppStrings {
  val isEnglish: Boolean

  // Navigation & Drawer
  val navHome: String
  val navFiles: String
  val navVault: String
  val navAnalyze: String
  val navTools: String
  val drawerSubtitleFallback: String
  val drawerHome: String
  val drawerFiles: String
  val drawerAnalyze: String
  val drawerSender: String
  val drawerDrive: String
  val drawerGooglePhotos: String
  val drawerExternalClouds: String
  val drawerViewOnPc: String
  val drawerVault: String
  val drawerRecycleBin: String
  val drawerTheme: String
  val drawerLanguage: String
  val drawerSettings: String

  // Common Actions & States
  val back: String
  val close: String
  val cancel: String
  val confirm: String
  val save: String
  val create: String
  val delete: String
  val deleteForever: String
  val restore: String
  val share: String
  val openWith: String
  val openExternal: String
  val apply: String
  val reset: String
  val refresh: String
  val loading: String
  val empty: String
  val search: String
  val clear: String
  val edit: String
  val selectFolderTitle: String
  val selectThisFolder: String
  val upOneLevel: String
  val newFolder: String
  val newFolderNameLabel: String
  val noSubfolders: String
  val folderCreateError: String
  val itemsCount: (Int) -> String
  val filesCount: (Int) -> String

  // Storage & Permissions
  val internalStorage: String
  val sdCard: String
  val usedLabel: String
  val usedOfTotal: (String, String) -> String
  val storageHealthyMessage: String
  val storageWarningMessage: String
  val quickClean: String
  val permissionTitle: String
  val permissionDesc: String
  val permissionCd: String
  val grantAccess: String
  val multimedia: String
  val fullAccessActive: String
  val tapToEnableAccess: String

  // Home Screen
  val greetingHello: String
  val openMenuCd: String
  val quickSettingsCd: String
  val searchPlaceholder: String
  val searchResultsCount: (Int) -> String
  val noMatches: String
  val categoriesTitle: String
  val recentFilesTitle: String
  val bookmarksTitle: String
  val quickToolsTitle: String
  val toolDrive: String
  val toolViewOnPc: String
  val toolSender: String
  val toolLanSmb: String
  val toolApps: String
  val toolNotes: String
  val toolDownloader: String
  val toolVault: String

  // Files Screen & Dialogs
  val myStorage: String
  val searchInFolderPlaceholder: String
  val viewModeCd: String
  val layoutModeTitle: String
  val showHiddenCd: String
  val hideHiddenCd: String
  val newItemCd: String
  val newFileMenu: String
  val newFolderMenu: String
  val importSafMenu: String
  val clipboardReady: (String, Int) -> String
  val actionCopy: String
  val actionMove: String
  val pasteHere: String
  val pasteCd: String
  val selectedCount: (Int) -> String
  val selectAllCd: String
  val compressZipCd: String
  val deleteCd: String
  val clearSelectionCd: String
  val folderLabel: String
  val emptyFolder: String
  val open: String
  val inspectArchive: String
  val extractHere: String
  val extractToFolder: String
  val editInNexoNotes: String
  val editInNexoPhotoEditor: String
  val uploadToDrive: String
  val exportViaSaf: String
  val lockInVault: String
  val rename: String
  val bookmark: String
  val properties: String
  val folderNameLabel: String
  val newFileTitle: String
  val fileNameLabel: String
  val renameTitle: String
  val newNameLabel: String
  val createZipTitle: String
  val zipNameLabel: String
  val compressBtn: String
  val deleteConfirmTitle: (Int) -> String
  val deleteConfirmDesc: String
  val extractToTitle: (String) -> String
  val inspectTitle: (String) -> String
  val calculatingProperties: String
  val propertiesTitle: String
  val propName: String
  val propPath: String
  val propTotalSize: String
  val propContents: String
  val propContentsValue: (Int, Int) -> String
  val propModified: String
  val propPermissions: String

  // Categories
  val catImages: String
  val catVideos: String
  val catDocs: String
  val catMusic: String
  val catApps: String
  val chartPhotos: String
  val chartVideos: String
  val chartApps: String
  val chartDocs: String
  val chartOthers: String
  val categoryEmpty: (String) -> String

  // Analyze Screen
  val storageAnalysisTitle: String
  val rescanCd: String
  val analyzingStorage: String
  val duplicateFilesTitle: String
  val duplicateSummary: (Int, String) -> String
  val review: String
  val largeFilesTitle: String
  val largeFilesSummary: (Int, String) -> String
  val junkCacheTitle: String
  val junkSummary: (String, Int) -> String
  val cleanBtn: String
  val recentNewFilesTitle: String
  val smartSelectTitle: String
  val cleanSelected: (Int) -> String
  val noDuplicatesFound: String
  val duplicateGroupTitle: (Int, String) -> String
  val originalTag: String
  val moveToTrashCd: String
  val noLargeFiles: String
  val largeFilesSubtitle: String
  val emptyJunkBanner: String
  val emptyFoldersTitle: (Int) -> String
  val junkFilesTitle: (Int) -> String
  val emptyFolderLabel: String
  val appFoldersTitle: (Int) -> String
  val recent7DaysTitle: (Int) -> String
  val noRecentFiles: String
  val cleaningInProgress: String
  val spaceFreedLabel: String
  val cleanupCompletedTitle: String
  val spaceRecovered: (String) -> String
  val undoTrashBtn: String
  val doneBtn: String
  val keepOriginalBadge: String
  val dupFunnelSubtitle: String
  val dupPhase1Label: (Int, Int) -> String
  val dupPhase2Label: (Int, Int) -> String
  val dupPhase3Label: (Int, Int) -> String
  val dupFunnelStatsBadge: (Int, Int, Int) -> String
  val rescanDuplicatesBtn: String
  val similarPhotosTitle: String
  val similarPhotosSubtitle: (Int, String) -> String
  val noSimilarPhotosFound: String
  val scanSimilarPhotosBtn: String
  val similarityBadge: (Int) -> String
  val bestShotBadge: String
  val burstGroupBadge: String
  val tapToPreviewPhotoHint: String
  val smartSelectPhotosBtn: String
  val cleanSelectedPhotosBtn: (Int) -> String

  // Recycle Bin
  val recycleBinTitle: String
  val recoverableCount: (Int) -> String
  val emptyBinBtn: String
  val recycleBinEmpty: String

  // Tools Hub
  val toolsHubTitle: String
  val toolsHubSubtitle: String
  val connectedAs: (String) -> String
  val driveNotConnectedDesc: String
  val serverActive: String
  val serverStopped: String
  val viewOnPcDesc: String
  val senderTitle: String
  val senderDesc: String
  val lanNetworkTitle: String
  val lanNetworkDesc: String
  val appManagerTitle: String
  val appManagerDesc: String
  val noteEditorTitle: String
  val noteEditorDesc: String
  val downloaderTitle: String
  val downloaderDesc: String
  val vaultTitle: String
  val vaultDesc: String
  val recycleBinDesc: String
  val advancedSettingsTitle: String
  val advancedSettingsDesc: String

  // Quick Settings Sheet
  val quickSettingsTitle: String
  val settingsBtn: String
  val unlockedFeaturesNote: String
  val appThemeTitle: String
  val hiddenFilesTitle: String
  val hiddenFilesDesc: String
  val storagePermTitle: String
  val safPickerBtn: String
  val openRecycleAndPort: (Int) -> String

  // Settings & Subscreens
  val settingsTitle: String
  val settingsSubtitle: String
  val expandCollapseCd: String
  val block1Title: String
  val block1Display: String
  val block1Cleanup: String
  val block1Directories: String
  val block1Search: String
  val block1Notifications: String
  val block1FloatingLogger: String
  val themeTitle: String
  val languageTitle: String
  val languageSelectModalTitle: String
  val languageAutoSystem: String
  val languageAutoSystemDesc: String
  val languageSpanishDesc: String
  val languageEnglishDesc: String
  val showHiddenAllTitle: String
  val showHiddenAllDesc: String
  val showOnlyFoldersHistory: String
  val showFileTransferStation: String
  val showNewFilesHome: String
  val showSdStatusBar: String
  val showThumbnails: String
  val showAppIconFolders: String
  val showSdSizeHistory: String
  val usbConnectPrompt: String
  val hideHomeWhatsNew: String
  val showFloatingClipboard: String
  val clearHistoryOnExit: String
  val clearCacheOnExit: String
  val clearCacheNow: String
  val clearHistoryNow: String
  val resetDefaultAppsBtn: String
  val resetDefaultAppsDesc: String
  val resetDefaultAppsToast: String
  val configuredDefaultAppsCount: (Int) -> String
  val homeDirLabel: String
  val bluetoothDirLabel: String
  val downloadDirLabel: String
  val showSearchBarHome: String
  val dismissNotifOnFinish: String
  val notifyApkPermissions: String
  val notifyNewFiles: String
  val remindNewCameraPhotos: String
  val floatingLoggerEnable: String
  val floatingLoggerFormats: String
  val floatingLoggerSourceApp: String

  val block2Title: String
  val defaultViewTitle: String
  val drawerSetupBtn: String
  val toolbarSetupBtn: String
  val bottomBarSetupBtn: String
  val bottomBarSetupTitle: String
  val bottomBarSetupDesc: String
  val bottomBarLimitToast: String
  val bottomBarResetBtn: String
  val bottomBarLongPressHint: String
  val configureDrawerTitle: String
  val visibleInMenu: String
  val configureToolbarTitle: String
  val toolbarSelectNote: String

  val block3Title: String
  val biometricAccessTitle: String
  val biometricAccessDesc: String
  val biometricNotAvailable: String
  val masterPinActiveDesc: String
  val masterPinInactiveDesc: String
  val masterPinTitle: String
  val disablePin: String
  val configureBtn: String
  val remoteSyncTitle: String
  val remoteSyncDesc: String
  val securityBackupTitle: String
  val securityBackupDesc: String
  val exportJsonBtn: String
  val importJsonBtn: String
  val masterPinDialogTitle: String
  val masterPinDialogDesc: String
  val newPinLabel: String
  val masterPinRequiredTitle: String
  val masterPinRequiredDesc: String
  val masterPinLabel: String
  val masterPinIncorrect: String
  val enterBtn: String

  // Exclusive Vault Security in Settings
  val vaultSecurityConfigTitle: String
  val vaultSecurityConfigDesc: String
  val vaultActiveMethodLabel: (String) -> String
  val vaultChangeMethodBtn: String
  val vaultVerifyCurrentTitle: String
  val vaultVerifyCurrentDesc: (String) -> String
  val vaultSelectNewMethodTitle: String
  val vaultSetupCredentialHint: String
  val vaultPatternHint: String
  val vaultPinHint: String
  val vaultPasswordHint: String
  val vaultCredentialIncorrect: String
  val vaultSecurityUpdatedToast: (String) -> String

  val block4Title: String
  val defaultSortTitle: String
  val autoBackupApkTitle: String
  val apkBackupFolderLabel: String
  val cleanResidualFoldersTitle: String
  val disableDownloaderTitle: String
  val multithreadTransferTitle: String
  val initialWindowsCount: (Int) -> String
  val resetWindows: String
  val recycleBinSettingTitle: String
  val gesturesSettingTitle: String
  val addGestureBtn: String
  val addGestureTitle: String
  val gestureShapeLabel: String
  val gestureActionLabel: String
  val gestureRouteLabel: String
  val viewOnPcPortLabel: (Int) -> String

  val block5Title: String
  val autoCheckUpdates: String
  val crashDiagTitle: String
  val crashDiagSubtitle: String
  val crashDiagEmpty: String
  val crashAlertTitle: String
  val crashAlertDesc: String
  val crashCopyReportBtn: String
  val crashClearLogBtn: String
  val crashCopiedToast: String
  val versionLabel: String
  val aboutNexoTitle: String
  val aboutNexoDesc: String
  val privacyPolicyTitle: String
  val privacyPolicyDesc: String
  val helpFeedbackTitle: String
  val helpFeedbackDesc: String
  val resetAllSettingsBtn: String
  val resetAllConfirmTitle: String
  val resetAllConfirmDesc: String

  // Viewers & Players
  val audioPlayerTitle: String
  val audioTrackPosition: (Int, Int) -> String
  val audioPlayCd: String
  val audioPauseCd: String
  val audioPrevCd: String
  val audioNextCd: String
  val audioCloseCd: String
  val audioErrorLoad: String
  val audioSeekError: String
  val noteEditorHeader: String
  val unsavedBadge: String
  val readOnlyBinaryBadge: String
  val truncatedBadge: String
  val modeTextLabel: String
  val modeHexLabel: String
  val binaryReadOnlyHint: String
  val fileNameInputLabel: String
  val editorPlaceholder: String
  val charsLinesStat: (Int, Int) -> String
  val imageLoadError: String
  val tryExternalApp: String
  val prevImageCd: String
  val nextImageCd: String
  val zoomOutCd: String
  val zoomInCd: String
  val rotate90Cd: String
  val videoLoadError: (Int, Int) -> String
  val openWithExternalPlayer: String
  val unmuteCd: String
  val muteCd: String
  val rewind10Cd: String
  val forward10Cd: String
  val pdfPageOf: (Int, Int) -> String
  val pdfAccessError: String
  val pdfEmptyError: String
  val pdfPasswordError: String
  val pdfGenericError: String
  val prevPageCd: String
  val nextPageCd: String
  val zoomOutPdfCd: String
  val zoomInPdfCd: String

  // App Chooser
  val chooserTitle: String
  val chooserSubtitle: (String) -> String
  val chooserInternalSection: String
  val chooserExternalSection: String
  val chooserNoExternalApps: String
  val chooserAlwaysBtn: String
  val chooserOnceBtn: String
  val viewerAudioTitle: String
  val viewerAudioSub: String
  val viewerVideoTitle: String
  val viewerVideoSub: String
  val viewerImageTitle: String
  val viewerImageSub: String
  val viewerPdfTitle: String
  val viewerPdfSub: String
  val viewerTextHexTitle: String
  val viewerTextHexSub: String
  val viewerArchiveTitle: String
  val viewerArchiveSub: String
  val chooserSavedDefaultToast: (String, String) -> String

  // Google Drive, Network, Sender, ViewOnPC, AppManager, Downloader, Vault
  val driveTitle: String
  val addCloudBtn: String
  val driveNotConnectedHeadline: String
  val driveNotConnectedBody: String
  val signInGoogleBtn: String
  val authorizeDriveScopeBtn: String
  val disconnectBtn: String
  val uploadSafBtn: String
  val newFolderBtn: String
  val driveTabFiles: String
  val driveTabBackup: String
  val driveTabClouds: String
  val driveRootName: String
  val driveGoUp: String
  val driveEmptyFolder: String
  val backupNowBtn: String
  val syncingProgress: String
  val addRuleBtn: String
  val noBackupRules: String
  val backupFrequencyWifi: String
  val backupFrequencyDaily: String
  val externalCloudsDesc: String
  val addCloudServerTitle: String
  val aliasNameLabel: String
  val urlOrHostLabel: String
  val userLabel: String
  val passwordLabel: String
  val useHttpsLabel: String
  val addBackupRuleTitle: String
  val pickFolderHint: String
  val tapToChooseFolder: String
  val frequencyLabel: String
  val wifiOnlyLabel: String
  val saveRuleBtn: String
  val newDriveFolderTitle: String

  val networkTitle: String
  val networkSubtitle: String
  val disconnectShort: String
  val addServerTitle: String
  val emptyRemoteFolder: String
  val scanWifiDevices: String
  val scanningLan: String
  val savedServersTitle: String
  val noSavedServers: String
  val connectBtn: String
  val lanDevicesTitle: String
  val noLanDevices: String
  val openPortsLabel: (String) -> String
  val saveAsServerBtn: String
  val newServerModalTitle: String
  val protocolLabel: String
  val hostOrIpLabel: String
  val portLabel: String
  val domainLabel: String
  val anonymousAccess: String
  val ftpModeLabel: String
  val encodingLabel: String

  val senderTitleScreen: String
  val senderSubtitleScreen: String
  val sameWifiMode: String
  val sameWifiDesc: String
  val visibleNameLabel: String
  val receiveFolderLabel: String
  val tabSend: String
  val tabReceive: String
  val tabShareApp: String
  val sendActionTitle: String
  val sendActionDesc: String
  val chooseFilesBtn: String
  val shareApkBtn: String
  val shareApkDesc: String
  val shareNexoApkBtn: String
  val receiveModeTitle: String
  val receiveModeDesc: String
  val activateReceiveBtn: String
  val senderActiveTitle: String
  val openBrowserQrHint: String
  val stopServerBtn: String
  val transferHistoryTitle: String
  val noTransfersYet: String

  val viewOnPcTitle: String
  val viewOnPcSubtitle: String
  val httpServerActive: String
  val httpServerStopped: String
  val copyAddressBtn: String
  val viewOnPcInstruction: String
  val stopHttpServerBtn: String
  val startHttpServerPort: (Int) -> String
  val portConfigTitle: String
  val httpPortLabel: String
  val transfersStat: (String, Int) -> String

  val appManagerScreenTitle: String
  val appManagerCount: (Int, Int) -> String
  val searchAppPlaceholder: String
  val tabUserApps: (Int) -> String
  val tabSystemApps: (Int) -> String
  val tabBackedUpApks: (Int) -> String
  val backupBtn: String
  val installBtn: String
  val uninstallCd: String
  val noBackedUpApks: String
  val backedUpApkBadge: String

  val downloaderScreenTitle: String
  val downloaderScreenSubtitle: String
  val pasteUrlLabel: String
  val clipboardCd: String
  val optionalFileNameLabel: String
  val downloadNowBtn: String
  val downloadingProgress: (String, Int) -> String
  val downloadHistoryTitle: String
  val noDownloadsYet: String
  val bytesStat: (String, String) -> String

  val vaultScreenTitle: String
  val vaultScreenSubtitle: String
  val lockBtn: String
  val vaultLockedHeadline: String
  val vaultLockedDesc: String
  val unlockNowBtn: String
  val addEncryptedFileBtn: String
  val vaultEmpty: String
  val vaultAuthError: (String) -> String

  // Vault Drive Backup & Ephemeral Web Portal
  val vaultDriveBackupBtn: String
  val vaultWebPortalBtn: String
  val vaultInternalBadge: String
  val vaultSecurityManagedInSettingsNote: String
  val vaultDriveModalTitle: String
  val vaultDriveModalDesc: String
  val vaultPassphraseLabel: String
  val vaultPassphrasePlaceholder: String
  val vaultBackupAllBtn: String
  val vaultRefreshBackupsBtn: String
  val vaultNoDriveBackups: String
  val vaultRestoreFromDriveBtn: String
  val vaultPortalModalTitle: String
  val vaultPortalModalDesc: String
  val vaultStartPortalBtn: String
  val vaultStopPortalBtn: String
  val vaultPortalCopyLinkBtn: String
  val vaultPortalActiveHint: String

  // External Clouds Screen
  val externalCloudsTitle: String
  val externalCloudsSubtitle: String
  val externalCloudsProvidersTitle: String
  val externalCloudsSavedTitle: String
  val externalCloudsEmptySaved: String

  // Selective Backup Checkboxes
  val vaultSelectAllBtn: String
  val vaultClearSelBtn: String
  val vaultSelectedCountLabel: (Int) -> String
  val backupSelectedToDriveBtn: (Int) -> String
  val restoreSelectedBtn: (Int) -> String
  val driveSelectedFilesLabel: (Int) -> String

  // Google Photos & Photo Editor
  val googlePhotosTitle: String
  val googlePhotosSubtitle: String
  val cloudTabDrive: String
  val cloudTabPhotos: String
  val connectGooglePhotosBtn: String
  val googlePhotosNotConnectedDesc: String
  val openPhotosPickerBtn: String
  val checkPickerSelectionBtn: String
  val pickerWaitingBanner: String
  val pickerPollingProgress: String
  val noPickedPhotosYet: String
  val pickedPhotosSectionTitle: (Int) -> String
  val downloadPhotoToLocalBtn: String
  val downloadedReadyBadge: String
  val clearPickedSessionBtn: String
  val photoEditorTitle: String
  val photoEditorNonDestructiveSub: String
  val editorTabAdjust: String
  val editorTabFilters: String
  val editorTabTransform: String
  val sliderBrightness: String
  val sliderContrast: String
  val sliderSaturation: String
  val sliderWarmth: String
  val filterOriginal: String
  val filterVivid: String
  val filterHighContrast: String
  val filterMono: String
  val filterNightViolet: String
  val cropFree: String
  val rotate90Label: String
  val flipHorizontalLabel: String
  val flipVerticalLabel: String
  val resetEditsBtn: String
  val saveToDeviceBtn: String
  val uploadCopyGooglePhotosBtn: String
  val exportFormatLabel: String
  val savingPhotoProgress: String
  val savedToPicturesToast: (String) -> String
  val uploadedToGooglePhotosToast: (String) -> String

  fun vaultMethodLabel(method: VaultLockMethod): String = when (method) {
    VaultLockMethod.BIOMETRIC -> if (isEnglish) "Biometrics (Fingerprint / Face)" else "Biometría (Huella / Rostro)"
    VaultLockMethod.PIN -> if (isEnglish) "Numeric PIN" else "PIN Numérico"
    VaultLockMethod.PATTERN -> if (isEnglish) "3x3 Tactical Pattern" else "Patrón Táctico 3x3"
    VaultLockMethod.PASSWORD -> if (isEnglish) "Master Password" else "Contraseña Maestra"
  }

  fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.LIGHT -> if (isEnglish) "Day HUD" else "Modo Día"
    ThemeMode.DARK -> if (isEnglish) "Night HUD" else "Modo Noche"
    ThemeMode.SYSTEM -> if (isEnglish) "System Auto" else "Sistema"
  }

  fun themeDesc(mode: ThemeMode): String = when (mode) {
    ThemeMode.LIGHT -> if (isEnglish) "Tactical #F4F3F8 daylight surface with EVA accents" else "Fondo táctico claro #F4F3F8 con acentos EVA"
    ThemeMode.DARK -> if (isEnglish) "Cybernetic #08060E dark-purple & neon HUD" else "Negro-púrpura cibernético #08060E y HUD neón"
    ThemeMode.SYSTEM -> if (isEnglish) "Follows system Day/Night appearance" else "Alterna Modo Día o Noche según el sistema"
  }

  fun sortLabel(sort: SortBy): String = when (sort) {
    SortBy.DATE -> if (isEnglish) "Date" else "Fecha"
    SortBy.NAME -> if (isEnglish) "Name" else "Nombre"
    SortBy.SIZE -> if (isEnglish) "Size" else "Tamaño"
  }

  fun categoryLabel(cat: FileCategory): String = when (cat) {
    FileCategory.IMAGES -> catImages
    FileCategory.VIDEOS -> catVideos
    FileCategory.DOCUMENTS -> catDocs
    FileCategory.MUSIC -> catMusic
    FileCategory.APKS -> catApps
  }

  fun layoutLabel(mode: LayoutViewMode): String = when (mode) {
    LayoutViewMode.LIST -> if (isEnglish) "List" else "Lista"
    LayoutViewMode.GRID -> if (isEnglish) "Grid" else "Cuadrícula"
    LayoutViewMode.COMPACT -> if (isEnglish) "Compact" else "Compacto"
    LayoutViewMode.DETAILED -> if (isEnglish) "Detailed" else "Detallado"
  }

  fun searchEngineLabel(mode: SearchEngineMode): String = when (mode) {
    SearchEngineMode.AUTO -> if (isEnglish) "Automatic" else "Automático"
    SearchEngineMode.FAST_INDEX -> if (isEnglish) "Fast index" else "Índice rápido"
    SearchEngineMode.DEEP_SCAN -> if (isEnglish) "Deep scan" else "Búsqueda profunda"
  }

  fun searchEngineDesc(mode: SearchEngineMode): String = when (mode) {
    SearchEngineMode.AUTO -> if (isEnglish) "Combines fast index and deep scan" else "Combina índice rápido y escaneo profundo"
    SearchEngineMode.FAST_INDEX -> if (isEnglish) "Fast lookup across main directories" else "Búsqueda veloz en directorios principales"
    SearchEngineMode.DEEP_SCAN -> if (isEnglish) "Traverses the entire storage tree" else "Explora todo el árbol de almacenamiento"
  }

  fun langLabel(opt: AppLanguageOption): String = when (opt) {
    AppLanguageOption.AUTO -> languageAutoSystem
    AppLanguageOption.ES -> "Español"
    AppLanguageOption.EN -> "English"
  }

  fun backupFreqLabel(freq: AutoBackupFrequency): String = when (freq) {
    AutoBackupFrequency.MANUAL -> "Manual"
    AutoBackupFrequency.DAILY -> if (isEnglish) "Daily" else "Diario"
    AutoBackupFrequency.WIFI_AUTO -> if (isEnglish) "Wi-Fi auto" else "Wi-Fi automático"
  }
}

object EsStrings : AppStrings {
  override val isEnglish = false
  override val navHome = "Inicio"
  override val navFiles = "Archivos"
  override val navVault = "Vault"
  override val navAnalyze = "Análisis"
  override val navTools = "Red y Más"
  override val drawerSubtitleFallback = "MAGI // EXPLORADOR TÁCTICO"
  override val drawerHome = "Inicio"
  override val drawerFiles = "Archivos locales"
  override val drawerAnalyze = "Análisis de espacio"
  override val drawerSender = "Sender (Wi-Fi / QR)"
  override val drawerDrive = "Google Drive"
  override val drawerGooglePhotos = "Google Photos"
  override val drawerExternalClouds = "Otras Nubes"
  override val drawerViewOnPc = "Ver en PC (Wi-Fi)"
  override val drawerVault = "Bóveda Privada"
  override val drawerRecycleBin = "Papelera de reciclaje"
  override val drawerTheme = "Tema"
  override val drawerLanguage = "Idioma"
  override val drawerSettings = "Ajustes"

  override val back = "Volver"
  override val close = "Cerrar"
  override val cancel = "Cancelar"
  override val confirm = "Confirmar"
  override val save = "Guardar"
  override val create = "Crear"
  override val delete = "Eliminar"
  override val deleteForever = "Borrar definitivo"
  override val restore = "Restaurar"
  override val share = "Compartir"
  override val openWith = "Abrir con…"
  override val openExternal = "Abrir en app externa"
  override val apply = "Aplicar"
  override val reset = "Restablecer"
  override val refresh = "Actualizar"
  override val loading = "Cargando…"
  override val empty = "Vacío"
  override val search = "Buscar"
  override val clear = "Limpiar"
  override val edit = "Editar"
  override val selectFolderTitle = "Seleccionar carpeta"
  override val selectThisFolder = "Seleccionar esta carpeta"
  override val upOneLevel = "Subir un nivel (..)"
  override val newFolder = "Nueva carpeta"
  override val newFolderNameLabel = "Nombre de la nueva carpeta"
  override val noSubfolders = "Sin subcarpetas en este directorio"
  override val folderCreateError = "No se pudo crear la carpeta aquí"
  override val itemsCount: (Int) -> String = { n -> "$n elementos" }
  override val filesCount: (Int) -> String = { n -> "$n archivos" }

  override val internalStorage = "Almacenamiento interno"
  override val sdCard = "Tarjeta SD"
  override val usedLabel = "USADO"
  override val usedOfTotal: (String, String) -> String = { u, t -> "$u / $t" }
  override val storageHealthyMessage = "TELEMETRÍA NOMINAL · Espacio libre óptimo."
  override val storageWarningMessage = "ALERTA EVA-02 · Capacidad crítica detectada."
  override val quickClean = "Limpieza rápida"
  override val permissionTitle = "Acceso completo al almacenamiento"
  override val permissionDesc = "Activa el permiso para explorar carpetas internas, ocultas y APK."
  override val permissionCd = "Permiso de almacenamiento"
  override val grantAccess = "Conceder acceso"
  override val multimedia = "Multimedia"
  override val fullAccessActive = "Acceso total activo"
  override val tapToEnableAccess = "Toca para habilitar acceso completo"

  override val greetingHello = "NexoFiles"
  override val openMenuCd = "Abrir menú"
  override val quickSettingsCd = "Configuración rápida"
  override val searchPlaceholder = "Buscar archivos, carpetas, APKs…"
  override val searchResultsCount: (Int) -> String = { n -> "RESULTADOS ($n)" }
  override val noMatches = "Sin coincidencias."
  override val categoriesTitle = "Categorías"
  override val recentFilesTitle = "Archivos recientes"
  override val bookmarksTitle = "Marcadores"
  override val quickToolsTitle = "Herramientas rápidas"
  override val toolDrive = "Drive"
  override val toolViewOnPc = "Ver en PC"
  override val toolSender = "Sender"
  override val toolLanSmb = "LAN / SMB"
  override val toolApps = "Apps"
  override val toolNotes = "Notas"
  override val toolDownloader = "Descargas"
  override val toolVault = "Bóveda"

  override val myStorage = "Mi Almacenamiento"
  override val searchInFolderPlaceholder = "Interno · Buscar aquí"
  override val viewModeCd = "Modo de vista"
  override val layoutModeTitle = "Modo de diseño"
  override val showHiddenCd = "Mostrar archivos ocultos"
  override val hideHiddenCd = "Ocultar archivos ocultos"
  override val newItemCd = "Nuevo elemento"
  override val newFileMenu = "Nuevo archivo"
  override val newFolderMenu = "Nueva carpeta"
  override val importSafMenu = "Importar desde SAF / Fotos"
  override val clipboardReady: (String, Int) -> String = { action, count -> "$action: $count elemento(s) listo(s)" }
  override val actionCopy = "Copiar"
  override val actionMove = "Mover"
  override val pasteHere = "Pegar aquí"
  override val pasteCd = "Pegar"
  override val selectedCount: (Int) -> String = { n -> "$n SELECCIONADO(S)" }
  override val selectAllCd = "Seleccionar todo"
  override val compressZipCd = "Comprimir ZIP"
  override val deleteCd = "Eliminar"
  override val clearSelectionCd = "Limpiar selección"
  override val folderLabel = "Carpeta"
  override val emptyFolder = "Carpeta vacía"
  override val open = "Abrir"
  override val inspectArchive = "Ver contenido (ZIP/RAR)"
  override val extractHere = "Descomprimir aquí"
  override val extractToFolder = "Extraer en otra carpeta…"
  override val editInNexoNotes = "Editar en NexoNotes"
  override val editInNexoPhotoEditor = "Editar en NexoFiles"
  override val uploadToDrive = "Subir a Google Drive"
  override val exportViaSaf = "Exportar vía SAF"
  override val lockInVault = "Cifrar en Bóveda"
  override val rename = "Renombrar"
  override val bookmark = "Marcar"
  override val properties = "Propiedades"
  override val folderNameLabel = "Nombre de carpeta"
  override val newFileTitle = "Nuevo archivo"
  override val fileNameLabel = "Nombre del archivo"
  override val renameTitle = "Renombrar"
  override val newNameLabel = "Nuevo nombre"
  override val createZipTitle = "Comprimir a ZIP"
  override val zipNameLabel = "Nombre del archivo .zip"
  override val compressBtn = "Comprimir"
  override val deleteConfirmTitle: (Int) -> String = { n -> "¿Eliminar $n elemento(s)?" }
  override val deleteConfirmDesc = "Se enviarán a la Papelera si está activa, o se eliminarán permanentemente."
  override val extractToTitle: (String) -> String = { n -> "Extraer $n en…" }
  override val inspectTitle: (String) -> String = { n -> "Contenido: $n" }
  override val calculatingProperties = "Calculando propiedades y hashes…"
  override val propertiesTitle = "Propiedades y Checksums"
  override val propName = "Nombre"
  override val propPath = "Ruta"
  override val propTotalSize = "Tamaño total"
  override val propContents = "Contenido"
  override val propContentsValue: (Int, Int) -> String = { files, folders -> "$files archivo(s), $folders carpeta(s)" }
  override val propModified = "Modificado"
  override val propPermissions = "Permisos"

  override val catImages = "Imágenes"
  override val catVideos = "Videos"
  override val catDocs = "Documentos"
  override val catMusic = "Música"
  override val catApps = "Apps"
  override val chartPhotos = "Fotos"
  override val chartVideos = "Video"
  override val chartApps = "Apps"
  override val chartDocs = "Docs"
  override val chartOthers = "Otros"
  override val categoryEmpty: (String) -> String = { cat -> "No se encontraron archivos en $cat." }

  override val storageAnalysisTitle = "Análisis de Almacenamiento"
  override val rescanCd = "Reescanear almacenamiento"
  override val analyzingStorage = "ESCANEANDO SECTORES…"
  override val duplicateFilesTitle = "Archivos Duplicados"
  override val duplicateSummary: (Int, String) -> String = { count, bytes -> "$count grupo(s) · $bytes recuperables" }
  override val review = "Revisar"
  override val largeFilesTitle = "Archivos Grandes"
  override val largeFilesSummary: (Int, String) -> String = { count, bytes -> "$count archivos ≥ 50 MB · $bytes" }
  override val junkCacheTitle = "Basura o caché"
  override val junkSummary: (String, Int) -> String = { bytes, emptyCount -> "$bytes · $emptyCount vacías" }
  override val cleanBtn = "Limpiar"
  override val recentNewFilesTitle = "Archivos nuevos recientes (últimos 7 días)"
  override val smartSelectTitle = "Selección inteligente"
  override val cleanSelected: (Int) -> String = { n -> "Limpiar seleccionados ($n)" }
  override val noDuplicatesFound = "No se encontraron archivos duplicados."
  override val duplicateGroupTitle: (Int, String) -> String = { count, bytes -> "$count copias idénticas · $bytes c/u" }
  override val originalTag = "ORIGINAL"
  override val moveToTrashCd = "Enviar a papelera"
  override val noLargeFiles = "No hay archivos mayores a 50 MB."
  override val largeFilesSubtitle = "Archivos mayores a 50 MB ordenados por tamaño"
  override val emptyJunkBanner = "Sin archivos temporales ni carpetas vacías pendientes."
  override val emptyFoldersTitle: (Int) -> String = { n -> "Carpetas vacías ($n)" }
  override val junkFilesTitle: (Int) -> String = { n -> "Archivos temporales y caché ($n)" }
  override val emptyFolderLabel = "Carpeta vacía"
  override val appFoldersTitle: (Int) -> String = { n -> "Carpetas asociadas a apps ($n)" }
  override val recent7DaysTitle: (Int) -> String = { n -> "Creados en los últimos 7 días ($n)" }
  override val noRecentFiles = "No se detectaron archivos nuevos recientes."
  override val cleaningInProgress = "PURGA EN CURSO…"
  override val spaceFreedLabel = "Espacio liberado"
  override val cleanupCompletedTitle = "Purga completada"
  override val spaceRecovered: (String) -> String = { bytes -> "Se recuperaron $bytes" }
  override val undoTrashBtn = "Deshacer (Papelera)"
  override val doneBtn = "Listo"
  override val keepOriginalBadge = "CONSERVAR"
  override val dupFunnelSubtitle = "Embudo de 3 fases: Peso exacto → Cabecera/Cola 8 KB → MD5 completo"
  override val dupPhase1Label: (Int, Int) -> String = { cur, tot -> "FASE 1/3 · PESO EXACTO ($cur/$tot)" }
  override val dupPhase2Label: (Int, Int) -> String = { cur, tot -> "FASE 2/3 · MUESTREO 8 KB ($cur/$tot)" }
  override val dupPhase3Label: (Int, Int) -> String = { cur, tot -> "FASE 3/3 · CONFIRMACIÓN MD5 ($cur/$tot)" }
  override val dupFunnelStatsBadge: (Int, Int, Int) -> String = { p1, p2, p3 -> "Peso igual: $p1 · 8 KB: $p2 · MD5: $p3" }
  override val rescanDuplicatesBtn = "Escanear duplicados"
  override val similarPhotosTitle = "Fotos Similares"
  override val similarPhotosSubtitle: (Int, String) -> String = { count, bytes -> "$count grupo(s) ≥ 88% · $bytes recuperables" }
  override val noSimilarPhotosFound = "No se detectaron ráfagas ni fotos similares (≥ 88% dHash)."
  override val scanSimilarPhotosBtn = "Analizar fotos"
  override val similarityBadge: (Int) -> String = { pct -> "$pct% SIMILAR" }
  override val bestShotBadge = "MEJOR TOMA"
  override val burstGroupBadge = "RÁFAGA"
  override val tapToPreviewPhotoHint = "Toca cualquier miniatura para ampliar o editar"
  override val smartSelectPhotosBtn = "Conservar mejores tomas"
  override val cleanSelectedPhotosBtn: (Int) -> String = { n -> "Eliminar copias ($n)" }

  override val recycleBinTitle = "Papelera de Reciclaje"
  override val recoverableCount: (Int) -> String = { n -> "$n elemento(s) recuperable(s)" }
  override val emptyBinBtn = "Vaciar"
  override val recycleBinEmpty = "La papelera está vacía"

  override val toolsHubTitle = "Red y Herramientas"
  override val toolsHubSubtitle = "Módulos tácticos desbloqueados"
  override val connectedAs: (String) -> String = { email -> "Conectado: $email" }
  override val driveNotConnectedDesc = "Conecta tu cuenta y programa respaldos"
  override val serverActive = "SERVIDOR ACTIVO:"
  override val serverStopped = "Inicia un servidor HTTP local sin cables"
  override val viewOnPcDesc = "Explorador web HTTP Wi-Fi"
  override val senderTitle = "Sender Wi-Fi y QR"
  override val senderDesc = "Transfiere archivos entre dispositivos sin internet"
  override val lanNetworkTitle = "Red LAN / FTP / WebDAV / SFTP"
  override val lanNetworkDesc = "Escaneo LAN y clientes remotos cifrados"
  override val appManagerTitle = "Gestor de Apps (APK)"
  override val appManagerDesc = "Respalda, instala y desinstala APKs"
  override val noteEditorTitle = "Editor de Notas y Código"
  override val noteEditorDesc = "Edita .txt, .md, .json y código local"
  override val downloaderTitle = "Gestor de Descargas"
  override val downloaderDesc = "Descarga URLs directas e historial"
  override val vaultTitle = "Bóveda Cifrada"
  override val vaultDesc = "AES-256-GCM, respaldo Drive y enlace Web/QR"
  override val recycleBinDesc = "Recupera o vacía archivos eliminados"
  override val advancedSettingsTitle = "Ajustes avanzados"
  override val advancedSettingsDesc = "Seguridad de Bóveda, diseño, gestos y respaldos"

  override val quickSettingsTitle = "Configuración rápida"
  override val settingsBtn = "Ajustes"
  override val unlockedFeaturesNote = "NERV HUD · Todas las utilidades operativas sin anuncios."
  override val appThemeTitle = "Perfil visual HUD"
  override val hiddenFilesTitle = "Archivos y carpetas ocultas"
  override val hiddenFilesDesc = "Mostrar .nomedia y archivos de sistema"
  override val storagePermTitle = "Permiso de almacenamiento"
  override val safPickerBtn = "Selector SAF"
  override val openRecycleAndPort: (Int) -> String = { port -> "Abrir Papelera · Puerto PC: $port" }

  override val settingsTitle = "Ajustes"
  override val settingsSubtitle = "Configuración general y seguridad de Bóveda"
  override val expandCollapseCd = "Expandir o contraer"
  override val block1Title = "Ajustes Generales"
  override val block1Display = "Pantalla (Display)"
  override val block1Cleanup = "Limpieza y Mantenimiento"
  override val block1Directories = "Ajustes de Directorios"
  override val block1Search = "Motor de búsqueda"
  override val block1Notifications = "Notificaciones"
  override val block1FloatingLogger = "Widget flotante de registro"
  override val themeTitle = "Tema HUD"
  override val languageTitle = "Idioma / Language"
  override val languageSelectModalTitle = "Seleccionar idioma"
  override val languageAutoSystem = "Automático (Sistema)"
  override val languageAutoSystemDesc = "Sigue el idioma configurado en tu dispositivo"
  override val languageSpanishDesc = "Interfaz completa en español"
  override val languageEnglishDesc = "Full interface in English"
  override val showHiddenAllTitle = "Mostrar archivos y carpetas ocultas"
  override val showHiddenAllDesc = "Incluye archivos que comienzan con punto (.)"
  override val showOnlyFoldersHistory = "Solo mostrar carpetas en historial"
  override val showFileTransferStation = "Mostrar estación de transferencia"
  override val showNewFilesHome = "Mostrar nuevos archivos en Inicio"
  override val showSdStatusBar = "Barra de estado de tarjeta SD"
  override val showThumbnails = "Mostrar miniaturas de medios"
  override val showAppIconFolders = "Mostrar icono de app en carpetas"
  override val showSdSizeHistory = "Mostrar tamaño de SD en historial"
  override val usbConnectPrompt = "Mostrar aviso al conectar USB / OTG"
  override val hideHomeWhatsNew = "Ocultar sección \"Novedades\" en Inicio"
  override val showFloatingClipboard = "Botón flotante de portapapeles"
  override val clearHistoryOnExit = "Borrar historial al salir"
  override val clearCacheOnExit = "Limpiar caché al salir"
  override val clearCacheNow = "Limpiar caché ahora"
  override val clearHistoryNow = "Borrar historial"
  override val resetDefaultAppsBtn = "Restablecer aplicaciones predeterminadas"
  override val resetDefaultAppsDesc = "Borra las asociaciones guardadas con \"Siempre\" por extensión"
  override val resetDefaultAppsToast = "Aplicaciones predeterminadas restablecidas"
  override val configuredDefaultAppsCount: (Int) -> String = { n -> "$n extensión(es) con app predeterminada" }
  override val homeDirLabel = "Directorio de inicio"
  override val bluetoothDirLabel = "Carpeta recepción Bluetooth"
  override val downloadDirLabel = "Carpeta de descargas"
  override val showSearchBarHome = "Mostrar barra de búsqueda en Inicio"
  override val dismissNotifOnFinish = "Descartar notificación al terminar tarea"
  override val notifyApkPermissions = "Notificar permisos de APK"
  override val notifyNewFiles = "Notificar nuevos archivos creados"
  override val remindNewCameraPhotos = "Recordar fotos nuevas en cámara"
  override val floatingLoggerEnable = "Activar widget flotante de registro"
  override val floatingLoggerFormats = "Formatos monitorizados (ej. apk,pdf,zip)"
  override val floatingLoggerSourceApp = "App de origen"

  override val block2Title = "Gestión de Diseño"
  override val defaultViewTitle = "Vista de lista predeterminada"
  override val drawerSetupBtn = "Configurar Menú Lateral"
  override val toolbarSetupBtn = "Personalizar Barra de Herramientas"
  override val bottomBarSetupBtn = "Personalizar Barra Inferior (2–5)"
  override val bottomBarSetupTitle = "Personalizar Barra Inferior"
  override val bottomBarSetupDesc = "Activa y reordena entre 2 y 5 accesos directos en la barra inferior."
  override val bottomBarLimitToast = "Debe haber entre 2 y 5 pestañas activas en la barra inferior"
  override val bottomBarResetBtn = "Restablecer 5 por defecto"
  override val bottomBarLongPressHint = "Mantén pulsada la barra inferior en cualquier momento para editarla."
  override val configureDrawerTitle = "Orden y visibilidad del menú"
  override val visibleInMenu = "Visible en el menú lateral"
  override val configureToolbarTitle = "Barra inferior de selección"
  override val toolbarSelectNote = "Activa las acciones que aparecen al seleccionar archivos."

  override val block3Title = "Seguridad de Bóveda y Respaldo"
  override val biometricAccessTitle = "Acceso biométrico a Bóveda"
  override val biometricAccessDesc = "Solicitar huella o rostro al abrir"
  override val biometricNotAvailable = "Sensor biométrico no configurado en el dispositivo"
  override val masterPinActiveDesc = "PIN maestro activo"
  override val masterPinInactiveDesc = "Sin PIN maestro"
  override val masterPinTitle = "PIN Maestro"
  override val disablePin = "Quitar PIN"
  override val configureBtn = "Configurar"
  override val remoteSyncTitle = "Auto-sincronizar ediciones remotas"
  override val remoteSyncDesc = "Subir cambios en la nube/FTP automáticamente"
  override val securityBackupTitle = "Respaldo de Ajustes (JSON)"
  override val securityBackupDesc = "Exporta marcadores, servidores y preferencias"
  override val exportJsonBtn = "Exportar JSON"
  override val importJsonBtn = "Importar JSON"
  override val masterPinDialogTitle = "Configurar PIN Maestro"
  override val masterPinDialogDesc = "Elige un PIN numérico de 4 a 8 dígitos."
  override val newPinLabel = "Nuevo PIN (mínimo 4 dígitos)"
  override val masterPinRequiredTitle = "PIN Maestro requerido"
  override val masterPinRequiredDesc = "Ingresa tu PIN maestro."
  override val masterPinLabel = "PIN Maestro"
  override val masterPinIncorrect = "PIN incorrecto"
  override val enterBtn = "Entrar"

  override val vaultSecurityConfigTitle = "Método de Seguridad de Bóveda Privada"
  override val vaultSecurityConfigDesc = "Único lugar autorizado para cambiar Huella, PIN, Patrón o Contraseña"
  override val vaultActiveMethodLabel: (String) -> String = { method -> "MÉTODO ACTIVO: $method" }
  override val vaultChangeMethodBtn = "Cambiar seguridad de Bóveda"
  override val vaultVerifyCurrentTitle = "Verificación requerida"
  override val vaultVerifyCurrentDesc: (String) -> String = { active -> "Autentica tu método actual ($active) antes de modificar la seguridad de la Bóveda." }
  override val vaultSelectNewMethodTitle = "Elegir nuevo método de Bóveda"
  override val vaultSetupCredentialHint = "Ingresa la nueva credencial para activar este método:"
  override val vaultPatternHint = "Toca al menos 4 nodos para trazar el nuevo patrón"
  override val vaultPinHint = "PIN numérico (4 a 8 dígitos)"
  override val vaultPasswordHint = "Contraseña maestra (mínimo 4 caracteres)"
  override val vaultCredentialIncorrect = "Credencial actual incorrecta"
  override val vaultSecurityUpdatedToast: (String) -> String = { method -> "Seguridad de Bóveda actualizada: $method" }

  override val block4Title = "Ajustes de Herramientas"
  override val defaultSortTitle = "Orden predeterminado"
  override val autoBackupApkTitle = "Respaldar APK antes de desinstalar"
  override val apkBackupFolderLabel = "Directorio de respaldo APK"
  override val cleanResidualFoldersTitle = "Limpiar carpetas residuales tras desinstalar"
  override val disableDownloaderTitle = "Desactivar gestor de descargas integrado"
  override val multithreadTransferTitle = "Transferencia multihilo (búfer ampliado)"
  override val initialWindowsCount: (Int) -> String = { n -> "Ventanas iniciales configuradas ($n)" }
  override val resetWindows = "Restablecer ventanas"
  override val recycleBinSettingTitle = "Activar Papelera de reciclaje"
  override val gesturesSettingTitle = "Activar gestos rápidos"
  override val addGestureBtn = "Añadir gesto"
  override val addGestureTitle = "Nuevo gesto rápido"
  override val gestureShapeLabel = "Letra o trazo (V, O, L, Z, S)"
  override val gestureActionLabel = "Nombre de acción"
  override val gestureRouteLabel = "Ruta o destino (sender, drive, vault)"
  override val viewOnPcPortLabel: (Int) -> String = { port -> "Puerto actual del servidor Ver en PC: $port" }

  override val block5Title = "Sistema y Acerca de"
  override val autoCheckUpdates = "Buscar actualizaciones automáticamente"
  override val crashDiagTitle = "Diagnóstico y Reporte de Errores"
  override val crashDiagSubtitle = "Inspeccionar nexofiles_error_log.txt y copiar traza de fallos"
  override val crashDiagEmpty = "Sin errores fatales registrados. Telemetría nominal."
  override val crashAlertTitle = "FALLO DETECTADO EN LA SESIÓN ANTERIOR"
  override val crashAlertDesc = "Se capturó un cierre inesperado y se guardó en Descargas/nexofiles_error_log.txt."
  override val crashCopyReportBtn = "Copiar Reporte"
  override val crashClearLogBtn = "Limpiar Log"
  override val crashCopiedToast = "Reporte de error copiado al portapapeles"
  override val versionLabel = "Versión"
  override val aboutNexoTitle = "Acerca de NexoFiles"
  override val aboutNexoDesc = "NERV HUD Explorer v2.5b · Cero anuncios · Código limpio"
  override val privacyPolicyTitle = "Política de Privacidad"
  override val privacyPolicyDesc = "Sin telemetría. Todos los datos permanecen en tu dispositivo o tu Google Drive."
  override val helpFeedbackTitle = "Ayuda y Comentarios"
  override val helpFeedbackDesc = "Protocolos compatibles: Local, Drive API v3, FTP/FTPS, SFTP, WebDAV, SMB y Wi-Fi QR."
  override val resetAllSettingsBtn = "Restablecer todos los ajustes"
  override val resetAllConfirmTitle = "¿Restablecer todos los ajustes?"
  override val resetAllConfirmDesc = "Se restaurarán los valores predeterminados manteniendo tu cuenta de Drive vinculada."

  override val audioPlayerTitle = "REPRODUCTOR DE AUDIO NEXO"
  override val audioTrackPosition: (Int, Int) -> String = { cur, total -> "PISTA $cur / $total" }
  override val audioPlayCd = "Reproducir"
  override val audioPauseCd = "Pausar"
  override val audioPrevCd = "Pista anterior"
  override val audioNextCd = "Siguiente pista"
  override val audioCloseCd = "Cerrar reproductor"
  override val audioErrorLoad = "No se pudo reproducir este archivo de audio."
  override val audioSeekError = "Error al avanzar el audio"
  override val noteEditorHeader = "NEXONOTES // INSPECTOR"
  override val unsavedBadge = "SIN GUARDAR"
  override val readOnlyBinaryBadge = "BINARIO (LECTURA)"
  override val truncatedBadge = "VISTA 64 KB"
  override val modeTextLabel = "TEXTO"
  override val modeHexLabel = "HEXADECIMAL"
  override val binaryReadOnlyHint = "Archivo binario o no textual: inspeccionando los primeros 64 KB sin modificar el original."
  override val fileNameInputLabel = "Nombre de archivo"
  override val editorPlaceholder = "Escribe tus notas, JSON, XML o código aquí…"
  override val charsLinesStat: (Int, Int) -> String = { chars, lines -> "$chars chars · $lines líneas" }
  override val imageLoadError = "No se pudo decodificar la imagen."
  override val tryExternalApp = "Intentar con app externa"
  override val prevImageCd = "Imagen anterior"
  override val nextImageCd = "Siguiente imagen"
  override val zoomOutCd = "Alejar"
  override val zoomInCd = "Acercar"
  override val rotate90Cd = "Rotar 90 grados"
  override val videoLoadError: (Int, Int) -> String = { w, e -> "No se pudo reproducir el vídeo (código $w/$e)." }
  override val openWithExternalPlayer = "Abrir con reproductor externo"
  override val unmuteCd = "Activar sonido"
  override val muteCd = "Silenciar"
  override val rewind10Cd = "Retroceder 10 s"
  override val forward10Cd = "Adelantar 10 s"
  override val pdfPageOf: (Int, Int) -> String = { cur, total -> "PÁGINA $cur / $total" }
  override val pdfAccessError = "No se pudo acceder al archivo PDF."
  override val pdfEmptyError = "El PDF no contiene páginas."
  override val pdfPasswordError = "Este PDF está protegido con contraseña."
  override val pdfGenericError = "No se pudo abrir el PDF."
  override val prevPageCd = "Página anterior"
  override val nextPageCd = "Página siguiente"
  override val zoomOutPdfCd = "Reducir zoom"
  override val zoomInPdfCd = "Aumentar zoom"

  override val chooserTitle = "Select"
  override val chooserSubtitle: (String) -> String = { name -> "Abrir \"$name\" con:" }
  override val chooserInternalSection = "Visores integrados de NexoFiles"
  override val chooserExternalSection = "Aplicaciones instaladas en el dispositivo"
  override val chooserNoExternalApps = "No se detectaron aplicaciones externas compatibles."
  override val chooserAlwaysBtn = "Siempre"
  override val chooserOnceBtn = "Solo esta vez"
  override val viewerAudioTitle = "Nexo Audio Player"
  override val viewerAudioSub = "Reproductor nativo con mini-reproductor"
  override val viewerVideoTitle = "Nexo Video Player"
  override val viewerVideoSub = "Reproductor Media3 ExoPlayer integrado"
  override val viewerImageTitle = "Nexo Image Viewer"
  override val viewerImageSub = "Visor con zoom, rotación y galería"
  override val viewerPdfTitle = "Nexo PDF Viewer"
  override val viewerPdfSub = "Lector PDF página a página integrado"
  override val viewerTextHexTitle = "Nexo Note & Text/Hex Viewer"
  override val viewerTextHexSub = "Lector de texto, código y volcado hexadecimal"
  override val viewerArchiveTitle = "Nexo ZIP / RAR Inspector"
  override val viewerArchiveSub = "Explorar y extraer archivos comprimidos"
  override val chooserSavedDefaultToast: (String, String) -> String = { ext, app -> "Predeterminado para .$ext: $app" }

  override val driveTitle = "Nube y Respaldo"
  override val addCloudBtn = "+ Nube"
  override val driveNotConnectedHeadline = "Conecta tu Google Drive"
  override val driveNotConnectedBody = "Usa Credential Manager y Google Drive API v3 para explorar archivos, subir contenido y programar respaldos automáticos."
  override val signInGoogleBtn = "Iniciar sesión con Google"
  override val authorizeDriveScopeBtn = "Autorizar acceso a Google Drive API"
  override val disconnectBtn = "Desconectar"
  override val uploadSafBtn = "Subir archivo SAF"
  override val newFolderBtn = "Nueva carpeta"
  override val driveTabFiles = "Archivos Drive"
  override val driveTabBackup = "Auto-Respaldo"
  override val driveTabClouds = "Otras Nubes"
  override val driveRootName = "Mi Unidad"
  override val driveGoUp = "Subir a carpeta anterior"
  override val driveEmptyFolder = "Esta carpeta de Drive está vacía."
  override val backupNowBtn = "Respaldar ahora"
  override val syncingProgress = "Sincronizando…"
  override val addRuleBtn = "+ Regla"
  override val noBackupRules = "Añade una regla para respaldar automáticamente carpetas locales como DCIM/Camera."
  override val backupFrequencyWifi = "Solo Wi-Fi"
  override val backupFrequencyDaily = "Wi-Fi o datos"
  override val externalCloudsDesc = "Conecta cuentas adicionales compatibles mediante protocolo WebDAV / HTTPS (Nextcloud, ownCloud, Koofr, etc.)."
  override val addCloudServerTitle = "Añadir servidor en la nube"
  override val aliasNameLabel = "Nombre alias"
  override val urlOrHostLabel = "URL o Host WebDAV"
  override val userLabel = "Usuario"
  override val passwordLabel = "Contraseña"
  override val useHttpsLabel = "Usar HTTPS"
  override val addBackupRuleTitle = "Nueva regla de respaldo"
  override val pickFolderHint = "Carpeta local para Auto-Respaldo"
  override val tapToChooseFolder = "Toca para explorar y elegir carpeta"
  override val frequencyLabel = "Frecuencia"
  override val wifiOnlyLabel = "Solo con conexión Wi-Fi"
  override val saveRuleBtn = "Guardar regla"
  override val newDriveFolderTitle = "Nueva carpeta en Drive"

  override val networkTitle = "Red LAN y Remotos"
  override val networkSubtitle = "FTP / FTPS / SFTP / WebDAV / SMB"
  override val disconnectShort = "Desconectar"
  override val addServerTitle = "Añadir servidor"
  override val emptyRemoteFolder = "Directorio remoto vacío."
  override val scanWifiDevices = "Escanear dispositivos Wi-Fi"
  override val scanningLan = "Escaneando…"
  override val savedServersTitle = "Servidores guardados"
  override val noSavedServers = "No hay servidores guardados. Pulsa + para añadir FTP, FTPS, SFTP, WebDAV o SMB."
  override val connectBtn = "Conectar"
  override val lanDevicesTitle = "Dispositivos descubiertos en LAN"
  override val noLanDevices = "Toca \"Escanear\" para detectar ordenadores y servidores en tu red."
  override val openPortsLabel: (String) -> String = { ports -> "Puertos abiertos: $ports" }
  override val saveAsServerBtn = "Guardar como servidor"
  override val newServerModalTitle = "Nuevo servidor remoto"
  override val protocolLabel = "Protocolo"
  override val hostOrIpLabel = "Host o IP"
  override val portLabel = "Puerto"
  override val domainLabel = "Dominio (opcional)"
  override val anonymousAccess = "Acceso anónimo"
  override val ftpModeLabel = "Modo FTP:"
  override val encodingLabel = "Codificación:"

  override val senderTitleScreen = "Sender Wi-Fi / QR"
  override val senderSubtitleScreen = "Comparte archivos sin internet"
  override val sameWifiMode = "Mismo modo Wi-Fi directo"
  override val sameWifiDesc = "Prioriza la IP LAN actual para máxima velocidad"
  override val visibleNameLabel = "Nombre visible"
  override val receiveFolderLabel = "Carpeta de recepción"
  override val tabSend = "Enviar"
  override val tabReceive = "Recibir"
  override val tabShareApp = "Compartir App"
  override val sendActionTitle = "Enviar archivos por Wi-Fi"
  override val sendActionDesc = "Elige fotos, vídeos, documentos o ZIPs y genera un enlace QR al instante."
  override val chooseFilesBtn = "Elegir archivos"
  override val shareApkBtn = "Compartir APK"
  override val shareApkDesc = "Comparte el APK de NexoFiles para instalar en otro teléfono sin internet."
  override val shareNexoApkBtn = "Compartir APK de NexoFiles"
  override val receiveModeTitle = "Recibir archivos en este dispositivo"
  override val receiveModeDesc = "Activa el portal de recepción para subir archivos desde cualquier móvil o PC."
  override val activateReceiveBtn = "Activar modo Recibir"
  override val senderActiveTitle = "Sender activo"
  override val openBrowserQrHint = "Escanea este código QR o abre la dirección en otro dispositivo:"
  override val stopServerBtn = "Detener servidor"
  override val transferHistoryTitle = "Historial de transferencias"
  override val noTransfersYet = "Sin transferencias registradas aún."

  override val viewOnPcTitle = "Ver en PC (HTTP Wi-Fi)"
  override val viewOnPcSubtitle = "Explora y transfiere archivos desde el navegador web"
  override val httpServerActive = "SERVIDOR HTTP ACTIVO"
  override val httpServerStopped = "SERVIDOR DETENIDO"
  override val copyAddressBtn = "Copiar dirección"
  override val viewOnPcInstruction = "Conecta tu móvil y PC a la misma red Wi-Fi e inicia el servidor."
  override val stopHttpServerBtn = "Detener servidor"
  override val startHttpServerPort: (Int) -> String = { port -> "Iniciar servidor en puerto $port" }
  override val portConfigTitle = "Configuración de puerto y actividad"
  override val httpPortLabel = "Puerto HTTP"
  override val transfersStat: (String, Int) -> String = { event, count -> "$event · Transferencias: $count" }

  override val appManagerScreenTitle = "Gestor de Apps"
  override val appManagerCount: (Int, Int) -> String = { u, s -> "$u usuario · $s sistema" }
  override val searchAppPlaceholder = "Buscar app o paquete…"
  override val tabUserApps: (Int) -> String = { n -> "Usuario ($n)" }
  override val tabSystemApps: (Int) -> String = { n -> "Sistema ($n)" }
  override val tabBackedUpApks: (Int) -> String = { n -> "APKs ($n)" }
  override val backupBtn = "Respaldar"
  override val installBtn = "Instalar"
  override val uninstallCd = "Desinstalar"
  override val noBackedUpApks = "No hay APKs respaldados en NexoFiles/Backed-up Apps."
  override val backedUpApkBadge = "APK RESPALDADO"

  override val downloaderScreenTitle = "Gestor de Descargas"
  override val downloaderScreenSubtitle = "Descargas directas HTTP/HTTPS"
  override val pasteUrlLabel = "Pegar URL http:// o https://"
  override val clipboardCd = "Pegar desde portapapeles"
  override val optionalFileNameLabel = "Nombre de archivo (opcional)"
  override val downloadNowBtn = "Descargar ahora"
  override val downloadingProgress: (String, Int) -> String = { name, pct -> "Descargando $name ($pct%)" }
  override val downloadHistoryTitle = "Historial de descargas"
  override val noDownloadsYet = "No hay descargas registradas todavía."
  override val bytesStat: (String, String) -> String = { bytes, status -> "$bytes · $status" }

  override val vaultScreenTitle = "Bóveda Privada"
  override val vaultScreenSubtitle = "Almacenamiento privado protegido"
  override val lockBtn = "Bloquear"
  override val vaultLockedHeadline = "BÓVEDA SELLADA"
  override val vaultLockedDesc = "Autentícate mediante el método activo configurado en Ajustes para acceder a la bóveda."
  override val unlockNowBtn = "Desbloquear ahora"
  override val addEncryptedFileBtn = "Cifrar archivo en la Bóveda"
  override val vaultEmpty = "No hay archivos cifrados en tu Bóveda."
  override val vaultAuthError: (String) -> String = { msg -> "Autenticación requerida: $msg" }

  override val vaultDriveBackupBtn = "Drive .nexo_vault"
  override val vaultWebPortalBtn = "Portal Web / QR"
  override val vaultInternalBadge = "PROTEGIDO"
  override val vaultSecurityManagedInSettingsNote = "El cambio de método de bloqueo (Huella/PIN/Patrón/Clave) se gestiona exclusivamente en Ajustes."
  override val vaultDriveModalTitle = "Respaldo Cifrado Google Drive"
  override val vaultDriveModalDesc = "Cifra tus archivos en formato .nexo_vault (AES-256-GCM + PBKDF2) dentro de la carpeta NexoVault_Encrypted de tu Google Drive."
  override val vaultPassphraseLabel = "Contraseña de cifrado portátil"
  override val vaultPassphrasePlaceholder = "Mínimo 4 caracteres (usada también en Safari/Web)"
  override val vaultBackupAllBtn = "Subir respaldo cifrado a Drive"
  override val vaultRefreshBackupsBtn = "Listar respaldos"
  override val vaultNoDriveBackups = "No hay archivos .nexo_vault respaldados en Google Drive."
  override val vaultRestoreFromDriveBtn = "Descifrar a Bóveda"
  override val vaultPortalModalTitle = "Acceso Remoto Efímero Web / iPhone"
  override val vaultPortalModalDesc = "Genera un token aleatorio y un código QR temporal para acceder a tus archivos desde un navegador en tu red local."
  override val vaultStartPortalBtn = "Generar Token + QR"
  override val vaultStopPortalBtn = "Revocar sesión y cerrar portal"
  override val vaultPortalCopyLinkBtn = "Copiar enlace"
  override val vaultPortalActiveHint = "Escanea con la cámara de iPhone (Safari) o abre esta URL en la misma red Wi-Fi. Ingresa tu contraseña portátil para descifrar localmente:"

  override val externalCloudsTitle = "Otras Nubes"
  override val externalCloudsSubtitle = "OneDrive · Dropbox · WebDAV / Nextcloud"
  override val externalCloudsProvidersTitle = "VINCULAR PROVEEDOR EXTERNO"
  override val externalCloudsSavedTitle = "CONEXIONES GUARDADAS"
  override val externalCloudsEmptySaved = "No hay nubes WebDAV, Dropbox u OneDrive guardadas aún."

  override val vaultSelectAllBtn = "Seleccionar todo"
  override val vaultClearSelBtn = "Limpiar"
  override val vaultSelectedCountLabel: (Int) -> String = { n -> "$n SELECCIONADO(S)" }
  override val backupSelectedToDriveBtn: (Int) -> String = { n -> "Respaldar seleccionados ($n)" }
  override val restoreSelectedBtn: (Int) -> String = { n -> "Restaurar seleccionados ($n)" }
  override val driveSelectedFilesLabel: (Int) -> String = { n -> "$n elemento(s) marcado(s)" }

  override val googlePhotosTitle = "Google Photos"
  override val googlePhotosSubtitle = "Selector seguro Photo Picker API y respaldo directo"
  override val cloudTabDrive = "Google Drive"
  override val cloudTabPhotos = "Google Photos"
  override val connectGooglePhotosBtn = "Conectar Google Photos"
  override val googlePhotosNotConnectedDesc = "Autoriza Google Photos (Picker API + AppendOnly) para importar fotos en máxima resolución y subir copias editadas sin salir de NexoFiles."
  override val openPhotosPickerBtn = "Elegir fotos de Google Photos"
  override val checkPickerSelectionBtn = "Sincronizar selección"
  override val pickerWaitingBanner = "Sesión de selección abierta. Elige tus fotos en Google Photos y vuelve a NexoFiles."
  override val pickerPollingProgress = "Consultando fotos seleccionadas en la nube…"
  override val noPickedPhotosYet = "Aún no has importado fotos en esta sesión. Pulsa «Elegir fotos de Google Photos» para abrir tu biblioteca en la nube."
  override val pickedPhotosSectionTitle: (Int) -> String = { n -> "Fotos seleccionadas ($n)" }
  override val downloadPhotoToLocalBtn = "Guardar local"
  override val downloadedReadyBadge = "Lista en caché"
  override val clearPickedSessionBtn = "Nueva selección"
  override val photoEditorTitle = "Nexo Photo Editor"
  override val photoEditorNonDestructiveSub = "Edición no destructiva · El archivo original permanece intacto"
  override val editorTabAdjust = "Ajustes"
  override val editorTabFilters = "Filtros"
  override val editorTabTransform = "Recorte y Giro"
  override val sliderBrightness = "Brillo"
  override val sliderContrast = "Contraste"
  override val sliderSaturation = "Saturación"
  override val sliderWarmth = "Temperatura / Calidez"
  override val filterOriginal = "Original"
  override val filterVivid = "Vívido"
  override val filterHighContrast = "Contraste Alto"
  override val filterMono = "Monocromo"
  override val filterNightViolet = "Violeta Nocturno"
  override val cropFree = "Libre"
  override val rotate90Label = "Girar 90°"
  override val flipHorizontalLabel = "Espejo H"
  override val flipVerticalLabel = "Espejo V"
  override val resetEditsBtn = "Restablecer ajustes"
  override val saveToDeviceBtn = "Guardar en dispositivo"
  override val uploadCopyGooglePhotosBtn = "Subir copia a Google Photos"
  override val exportFormatLabel = "Formato de salida:"
  override val savingPhotoProgress = "Procesando imagen de alta calidad…"
  override val savedToPicturesToast: (String) -> String = { path -> "Guardada en Pictures/NexoFiles: $path" }
  override val uploadedToGooglePhotosToast: (String) -> String = { name -> "Copia subida a Google Photos: $name" }
}

object EnStrings : AppStrings {
  override val isEnglish = true
  override val navHome = "Home"
  override val navFiles = "Files"
  override val navVault = "Vault"
  override val navAnalyze = "Analyze"
  override val navTools = "Network & More"
  override val drawerSubtitleFallback = "MAGI // TACTICAL EXPLORER"
  override val drawerHome = "Home"
  override val drawerFiles = "Local Files"
  override val drawerAnalyze = "Storage Analysis"
  override val drawerSender = "Sender (Wi-Fi / QR)"
  override val drawerDrive = "Google Drive"
  override val drawerGooglePhotos = "Google Photos"
  override val drawerExternalClouds = "Other Clouds"
  override val drawerViewOnPc = "View on PC (Wi-Fi)"
  override val drawerVault = "Private Vault"
  override val drawerRecycleBin = "Recycle Bin"
  override val drawerTheme = "Theme"
  override val drawerLanguage = "Language"
  override val drawerSettings = "Settings"

  override val back = "Back"
  override val close = "Close"
  override val cancel = "Cancel"
  override val confirm = "Confirm"
  override val save = "Save"
  override val create = "Create"
  override val delete = "Delete"
  override val deleteForever = "Delete permanently"
  override val restore = "Restore"
  override val share = "Share"
  override val openWith = "Open with…"
  override val openExternal = "Open in external app"
  override val apply = "Apply"
  override val reset = "Reset"
  override val refresh = "Refresh"
  override val loading = "Loading…"
  override val empty = "Empty"
  override val search = "Search"
  override val clear = "Clear"
  override val edit = "Edit"
  override val selectFolderTitle = "Select folder"
  override val selectThisFolder = "Select this folder"
  override val upOneLevel = "Up one level (..)"
  override val newFolder = "New folder"
  override val newFolderNameLabel = "New folder name"
  override val noSubfolders = "No subfolders in this directory"
  override val folderCreateError = "Could not create folder here"
  override val itemsCount: (Int) -> String = { n -> "$n items" }
  override val filesCount: (Int) -> String = { n -> "$n files" }

  override val internalStorage = "Internal storage"
  override val sdCard = "SD Card"
  override val usedLabel = "USED"
  override val usedOfTotal: (String, String) -> String = { u, t -> "$u / $t" }
  override val storageHealthyMessage = "TELEMETRY NOMINAL · Optimal free capacity."
  override val storageWarningMessage = "EVA-02 ALERT · High storage utilization."
  override val quickClean = "Quick clean"
  override val permissionTitle = "Full storage access"
  override val permissionDesc = "Grant permission to browse internal, hidden, and APK folders."
  override val permissionCd = "Storage permission"
  override val grantAccess = "Grant access"
  override val multimedia = "Media"
  override val fullAccessActive = "Full access active"
  override val tapToEnableAccess = "Tap to enable full access"

  override val greetingHello = "NexoFiles"
  override val openMenuCd = "Open menu"
  override val quickSettingsCd = "Quick settings"
  override val searchPlaceholder = "Search files, folders, APKs…"
  override val searchResultsCount: (Int) -> String = { n -> "RESULTS ($n)" }
  override val noMatches = "No matches found."
  override val categoriesTitle = "Categories"
  override val recentFilesTitle = "Recent files"
  override val bookmarksTitle = "Bookmarks"
  override val quickToolsTitle = "Quick tools"
  override val toolDrive = "Drive"
  override val toolViewOnPc = "View on PC"
  override val toolSender = "Sender"
  override val toolLanSmb = "LAN / SMB"
  override val toolApps = "Apps"
  override val toolNotes = "Notes"
  override val toolDownloader = "Downloads"
  override val toolVault = "Vault"

  override val myStorage = "My Storage"
  override val searchInFolderPlaceholder = "Internal · Search here"
  override val viewModeCd = "View mode"
  override val layoutModeTitle = "Layout mode"
  override val showHiddenCd = "Show hidden files"
  override val hideHiddenCd = "Hide hidden files"
  override val newItemCd = "New item"
  override val newFileMenu = "New file"
  override val newFolderMenu = "New folder"
  override val importSafMenu = "Import from SAF / Photos"
  override val clipboardReady: (String, Int) -> String = { action, count -> "$action: $count item(s) ready" }
  override val actionCopy = "Copy"
  override val actionMove = "Move"
  override val pasteHere = "Paste here"
  override val pasteCd = "Paste"
  override val selectedCount: (Int) -> String = { n -> "$n SELECTED" }
  override val selectAllCd = "Select all"
  override val compressZipCd = "Compress ZIP"
  override val deleteCd = "Delete"
  override val clearSelectionCd = "Clear selection"
  override val folderLabel = "Folder"
  override val emptyFolder = "Empty folder"
  override val open = "Open"
  override val inspectArchive = "Inspect archive (ZIP/RAR)"
  override val extractHere = "Extract here"
  override val extractToFolder = "Extract to another folder…"
  override val editInNexoNotes = "Edit in NexoNotes"
  override val editInNexoPhotoEditor = "Edit in NexoFiles"
  override val uploadToDrive = "Upload to Google Drive"
  override val exportViaSaf = "Export via SAF"
  override val lockInVault = "Encrypt in Vault"
  override val rename = "Rename"
  override val bookmark = "Bookmark"
  override val properties = "Properties"
  override val folderNameLabel = "Folder name"
  override val newFileTitle = "New file"
  override val fileNameLabel = "File name"
  override val renameTitle = "Rename"
  override val newNameLabel = "New name"
  override val createZipTitle = "Compress to ZIP"
  override val zipNameLabel = "Archive .zip name"
  override val compressBtn = "Compress"
  override val deleteConfirmTitle: (Int) -> String = { n -> "Delete $n item(s)?" }
  override val deleteConfirmDesc = "Items will move to Recycle Bin if enabled, or be deleted permanently."
  override val extractToTitle: (String) -> String = { n -> "Extract $n to…" }
  override val inspectTitle: (String) -> String = { n -> "Contents: $n" }
  override val calculatingProperties = "Calculating properties and hashes…"
  override val propertiesTitle = "Properties & Checksums"
  override val propName = "Name"
  override val propPath = "Path"
  override val propTotalSize = "Total size"
  override val propContents = "Contents"
  override val propContentsValue: (Int, Int) -> String = { files, folders -> "$files file(s), $folders folder(s)" }
  override val propModified = "Modified"
  override val propPermissions = "Permissions"

  override val catImages = "Photos"
  override val catVideos = "Videos"
  override val catDocs = "Documents"
  override val catMusic = "Music"
  override val catApps = "Apps"
  override val chartPhotos = "Photos"
  override val chartVideos = "Videos"
  override val chartApps = "Apps"
  override val chartDocs = "Docs"
  override val chartOthers = "Others"
  override val categoryEmpty: (String) -> String = { cat -> "No files found in $cat." }

  override val storageAnalysisTitle = "Storage Analysis"
  override val rescanCd = "Rescan storage"
  override val analyzingStorage = "SCANNING SECTORS…"
  override val duplicateFilesTitle = "Duplicate Files"
  override val duplicateSummary: (Int, String) -> String = { count, bytes -> "$count group(s) · $bytes recoverable" }
  override val review = "Review"
  override val largeFilesTitle = "Large Files"
  override val largeFilesSummary: (Int, String) -> String = { count, bytes -> "$count files ≥ 50 MB · $bytes" }
  override val junkCacheTitle = "Junk & Cache"
  override val junkSummary: (String, Int) -> String = { bytes, emptyCount -> "$bytes · $emptyCount empty" }
  override val cleanBtn = "Clean"
  override val recentNewFilesTitle = "Recent new files (last 7 days)"
  override val smartSelectTitle = "Smart select"
  override val cleanSelected: (Int) -> String = { n -> "Clean selected ($n)" }
  override val noDuplicatesFound = "No duplicate files found."
  override val duplicateGroupTitle: (Int, String) -> String = { count, bytes -> "$count identical copies · $bytes each" }
  override val originalTag = "ORIGINAL"
  override val moveToTrashCd = "Move to Recycle Bin"
  override val noLargeFiles = "No files larger than 50 MB."
  override val largeFilesSubtitle = "Files larger than 50 MB sorted by size"
  override val emptyJunkBanner = "No temporary files or empty folders pending."
  override val emptyFoldersTitle: (Int) -> String = { n -> "Empty folders ($n)" }
  override val junkFilesTitle: (Int) -> String = { n -> "Temporary & cache files ($n)" }
  override val emptyFolderLabel = "Empty folder"
  override val appFoldersTitle: (Int) -> String = { n -> "App-associated folders ($n)" }
  override val recent7DaysTitle: (Int) -> String = { n -> "Created in the last 7 days ($n)" }
  override val noRecentFiles = "No recent new files detected."
  override val cleaningInProgress = "PURGE IN PROGRESS…"
  override val spaceFreedLabel = "Space freed"
  override val cleanupCompletedTitle = "Purge complete"
  override val spaceRecovered: (String) -> String = { bytes -> "Recovered $bytes" }
  override val undoTrashBtn = "Undo (Recycle Bin)"
  override val doneBtn = "Done"
  override val keepOriginalBadge = "KEEP"
  override val dupFunnelSubtitle = "3-phase funnel: Exact size → 8 KB Header/Tail → Full MD5"
  override val dupPhase1Label: (Int, Int) -> String = { cur, tot -> "PHASE 1/3 · EXACT SIZE ($cur/$tot)" }
  override val dupPhase2Label: (Int, Int) -> String = { cur, tot -> "PHASE 2/3 · 8 KB STRUCT ($cur/$tot)" }
  override val dupPhase3Label: (Int, Int) -> String = { cur, tot -> "PHASE 3/3 · FULL MD5 ($cur/$tot)" }
  override val dupFunnelStatsBadge: (Int, Int, Int) -> String = { p1, p2, p3 -> "Equal size: $p1 · 8 KB: $p2 · MD5: $p3" }
  override val rescanDuplicatesBtn = "Scan duplicates"
  override val similarPhotosTitle = "Similar Photos"
  override val similarPhotosSubtitle: (Int, String) -> String = { count, bytes -> "$count group(s) ≥ 88% similarity · $bytes recoverable" }
  override val noSimilarPhotosFound = "No camera bursts or similar photos (≥ 88% dHash) detected."
  override val scanSimilarPhotosBtn = "Scan photos"
  override val similarityBadge: (Int) -> String = { pct -> "$pct% SIMILAR" }
  override val bestShotBadge = "BEST SHOT"
  override val burstGroupBadge = "BURST"
  override val tapToPreviewPhotoHint = "Tap any photo card to zoom or edit"
  override val smartSelectPhotosBtn = "Keep best shots"
  override val cleanSelectedPhotosBtn: (Int) -> String = { n -> "Delete copies ($n)" }

  override val recycleBinTitle = "Recycle Bin"
  override val recoverableCount: (Int) -> String = { n -> "$n recoverable item(s)" }
  override val emptyBinBtn = "Empty"
  override val recycleBinEmpty = "Recycle Bin is empty"

  override val toolsHubTitle = "Network & Tools"
  override val toolsHubSubtitle = "Tactical modules unlocked"
  override val connectedAs: (String) -> String = { email -> "Connected: $email" }
  override val driveNotConnectedDesc = "Connect your account and schedule backups"
  override val serverActive = "ACTIVE SERVER:"
  override val serverStopped = "Start a wireless local HTTP server"
  override val viewOnPcDesc = "HTTP Wi-Fi web explorer"
  override val senderTitle = "Wi-Fi & QR Sender"
  override val senderDesc = "Transfer files between devices without internet"
  override val lanNetworkTitle = "LAN / FTP / WebDAV / SFTP"
  override val lanNetworkDesc = "LAN scanner & encrypted remote clients"
  override val appManagerTitle = "App Manager (APK)"
  override val appManagerDesc = "Back up, install, and uninstall APKs"
  override val noteEditorTitle = "Note & Code Editor"
  override val noteEditorDesc = "Edit local .txt, .md, .json and code"
  override val downloaderTitle = "Download Manager"
  override val downloaderDesc = "Direct HTTP/HTTPS URL downloads & history"
  override val vaultTitle = "Encrypted Vault"
  override val vaultDesc = "AES-256-GCM, Drive backup & Web/QR portal"
  override val recycleBinDesc = "Restore or purge deleted items"
  override val advancedSettingsTitle = "Advanced settings"
  override val advancedSettingsDesc = "Vault security, layout, gestures & backups"

  override val quickSettingsTitle = "Quick settings"
  override val settingsBtn = "Settings"
  override val unlockedFeaturesNote = "NERV HUD · All utilities operational with zero ads."
  override val appThemeTitle = "HUD Profile"
  override val hiddenFilesTitle = "Hidden files & folders"
  override val hiddenFilesDesc = "Show .nomedia and system files"
  override val storagePermTitle = "Storage permission"
  override val safPickerBtn = "SAF Picker"
  override val openRecycleAndPort: (Int) -> String = { port -> "Open Recycle Bin · PC Port: $port" }

  override val settingsTitle = "Settings"
  override val settingsSubtitle = "General and Vault security configuration"
  override val expandCollapseCd = "Expand or collapse"
  override val block1Title = "General Settings"
  override val block1Display = "Display settings"
  override val block1Cleanup = "Cleanup & Maintenance"
  override val block1Directories = "Directory settings"
  override val block1Search = "Search engine"
  override val block1Notifications = "Notifications"
  override val block1FloatingLogger = "Floating logger widget"
  override val themeTitle = "HUD Theme"
  override val languageTitle = "Language"
  override val languageSelectModalTitle = "Select language"
  override val languageAutoSystem = "Automatic (System)"
  override val languageAutoSystemDesc = "Follows your device's system language"
  override val languageSpanishDesc = "Interfaz completa en español"
  override val languageEnglishDesc = "Full interface in English"
  override val showHiddenAllTitle = "Show hidden files & folders"
  override val showHiddenAllDesc = "Includes entries starting with a dot (.)"
  override val showOnlyFoldersHistory = "Show only folders in history"
  override val showFileTransferStation = "Show file transfer station"
  override val showNewFilesHome = "Show new files on Home"
  override val showSdStatusBar = "SD card status bar"
  override val showThumbnails = "Show media thumbnails"
  override val showAppIconFolders = "Show app icons on folders"
  override val showSdSizeHistory = "Show SD size in history"
  override val usbConnectPrompt = "Prompt when USB / OTG is connected"
  override val hideHomeWhatsNew = "Hide \"What's new\" card on Home"
  override val showFloatingClipboard = "Floating clipboard button"
  override val clearHistoryOnExit = "Clear history on exit"
  override val clearCacheOnExit = "Clear cache on exit"
  override val clearCacheNow = "Clear cache now"
  override val clearHistoryNow = "Clear history"
  override val resetDefaultAppsBtn = "Reset default apps"
  override val resetDefaultAppsDesc = "Clears saved \"Always\" file associations by extension"
  override val resetDefaultAppsToast = "Default app associations reset"
  override val configuredDefaultAppsCount: (Int) -> String = { n -> "$n extension(s) with saved default" }
  override val homeDirLabel = "Home directory"
  override val bluetoothDirLabel = "Bluetooth receive folder"
  override val downloadDirLabel = "Download directory"
  override val showSearchBarHome = "Show search bar on Home"
  override val dismissNotifOnFinish = "Dismiss notification when finished"
  override val notifyApkPermissions = "Notify APK permissions"
  override val notifyNewFiles = "Notify newly created files"
  override val remindNewCameraPhotos = "Remind about new camera photos"
  override val floatingLoggerEnable = "Enable floating logger widget"
  override val floatingLoggerFormats = "Monitored formats (e.g. apk,pdf,zip)"
  override val floatingLoggerSourceApp = "Source app"

  override val block2Title = "Layout Management"
  override val defaultViewTitle = "Default list view"
  override val drawerSetupBtn = "Configure Side Drawer"
  override val toolbarSetupBtn = "Customize Action Toolbar"
  override val bottomBarSetupBtn = "Customize Bottom Bar (2–5)"
  override val bottomBarSetupTitle = "Customize Bottom Bar"
  override val bottomBarSetupDesc = "Enable and reorder 2 to 5 shortcuts on the bottom navigation bar."
  override val bottomBarLimitToast = "Keep between 2 and 5 active tabs on the bottom bar"
  override val bottomBarResetBtn = "Reset 5 defaults"
  override val bottomBarLongPressHint = "Long-press the bottom bar anytime to customize it."
  override val configureDrawerTitle = "Menu order & visibility"
  override val visibleInMenu = "Visible in side drawer"
  override val configureToolbarTitle = "Bottom selection toolbar"
  override val toolbarSelectNote = "Toggle actions shown when selecting files."

  override val block3Title = "Vault Security & Backup"
  override val biometricAccessTitle = "Biometric Vault access"
  override val biometricAccessDesc = "Require fingerprint or face unlock"
  override val biometricNotAvailable = "Biometric sensor is not enrolled on this device"
  override val masterPinActiveDesc = "Master PIN active"
  override val masterPinInactiveDesc = "No Master PIN"
  override val masterPinTitle = "Master PIN"
  override val disablePin = "Remove PIN"
  override val configureBtn = "Configure"
  override val remoteSyncTitle = "Auto-sync remote edits"
  override val remoteSyncDesc = "Automatically upload cloud/FTP edits"
  override val securityBackupTitle = "Settings Backup (JSON)"
  override val securityBackupDesc = "Export bookmarks, servers & preferences"
  override val exportJsonBtn = "Export JSON"
  override val importJsonBtn = "Import JSON"
  override val masterPinDialogTitle = "Configure Master PIN"
  override val masterPinDialogDesc = "Choose a 4 to 8 digit PIN."
  override val newPinLabel = "New PIN (at least 4 digits)"
  override val masterPinRequiredTitle = "Master PIN required"
  override val masterPinRequiredDesc = "Enter your Master PIN."
  override val masterPinLabel = "Master PIN"
  override val masterPinIncorrect = "Incorrect PIN"
  override val enterBtn = "Unlock"

  override val vaultSecurityConfigTitle = "Private Vault Security Method"
  override val vaultSecurityConfigDesc = "Exclusive control to change Biometric, PIN, Pattern, or Password"
  override val vaultActiveMethodLabel: (String) -> String = { method -> "ACTIVE METHOD: $method" }
  override val vaultChangeMethodBtn = "Change Vault security"
  override val vaultVerifyCurrentTitle = "Verification Required"
  override val vaultVerifyCurrentDesc: (String) -> String = { active -> "Verify your current active method ($active) before changing Vault security." }
  override val vaultSelectNewMethodTitle = "Select New Vault Lock Method"
  override val vaultSetupCredentialHint = "Enter the new credential to enable this method:"
  override val vaultPatternHint = "Tap at least 4 nodes to trace the new pattern"
  override val vaultPinHint = "Numeric PIN (4 to 8 digits)"
  override val vaultPasswordHint = "Master password (minimum 4 characters)"
  override val vaultCredentialIncorrect = "Current credential is incorrect"
  override val vaultSecurityUpdatedToast: (String) -> String = { method -> "Vault security updated: $method" }

  override val block4Title = "Tool Settings"
  override val defaultSortTitle = "Default sort order"
  override val autoBackupApkTitle = "Back up APK before uninstalling"
  override val apkBackupFolderLabel = "APK backup directory"
  override val cleanResidualFoldersTitle = "Clean residual folders after uninstall"
  override val disableDownloaderTitle = "Disable built-in download manager"
  override val multithreadTransferTitle = "Multithreaded transfer (large buffer)"
  override val initialWindowsCount: (Int) -> String = { n -> "Configured initial windows ($n)" }
  override val resetWindows = "Reset windows"
  override val recycleBinSettingTitle = "Enable Recycle Bin"
  override val gesturesSettingTitle = "Enable quick gestures"
  override val addGestureBtn = "Add gesture"
  override val addGestureTitle = "New quick gesture"
  override val gestureShapeLabel = "Letter or stroke (V, O, L, Z, S)"
  override val gestureActionLabel = "Action label"
  override val gestureRouteLabel = "Route or target (sender, drive, vault)"
  override val viewOnPcPortLabel: (Int) -> String = { port -> "Current View on PC server port: $port" }

  override val block5Title = "System & About"
  override val autoCheckUpdates = "Check for updates automatically"
  override val crashDiagTitle = "Diagnostics & Crash Reports"
  override val crashDiagSubtitle = "Inspect nexofiles_error_log.txt and copy stacktrace"
  override val crashDiagEmpty = "No fatal errors recorded. Telemetry nominal."
  override val crashAlertTitle = "PREVIOUS SESSION CRASH DETECTED"
  override val crashAlertDesc = "An unexpected error was captured and written to Download/nexofiles_error_log.txt."
  override val crashCopyReportBtn = "Copy Report"
  override val crashClearLogBtn = "Clear Log"
  override val crashCopiedToast = "Crash report copied to clipboard"
  override val versionLabel = "Version"
  override val aboutNexoTitle = "About NexoFiles"
  override val aboutNexoDesc = "NERV HUD Explorer v2.5b · Zero ads · Clean build"
  override val privacyPolicyTitle = "Privacy Policy"
  override val privacyPolicyDesc = "Zero telemetry. All data stays on your device or your own Google Drive."
  override val helpFeedbackTitle = "Help & Feedback"
  override val helpFeedbackDesc = "Supported protocols: Local, Drive API v3, FTP/FTPS, SFTP, WebDAV, SMB & Wi-Fi QR."
  override val resetAllSettingsBtn = "Reset all settings"
  override val resetAllConfirmTitle = "Reset all settings?"
  override val resetAllConfirmDesc = "Default values will be restored while keeping your linked Drive account."

  override val audioPlayerTitle = "NEXO AUDIO PLAYER"
  override val audioTrackPosition: (Int, Int) -> String = { cur, total -> "TRACK $cur / $total" }
  override val audioPlayCd = "Play"
  override val audioPauseCd = "Pause"
  override val audioPrevCd = "Previous track"
  override val audioNextCd = "Next track"
  override val audioCloseCd = "Close player"
  override val audioErrorLoad = "Could not play this audio file."
  override val audioSeekError = "Could not seek audio"
  override val noteEditorHeader = "NEXONOTES // INSPECTOR"
  override val unsavedBadge = "UNSAVED"
  override val readOnlyBinaryBadge = "BINARY (READ-ONLY)"
  override val truncatedBadge = "64 KB PREVIEW"
  override val modeTextLabel = "TEXT"
  override val modeHexLabel = "HEXADECIMAL"
  override val binaryReadOnlyHint = "Binary or non-text file: inspecting the first 64 KB safely without altering the original."
  override val fileNameInputLabel = "File name"
  override val editorPlaceholder = "Write your notes, JSON, XML or code here…"
  override val charsLinesStat: (Int, Int) -> String = { chars, lines -> "$chars chars · $lines lines" }
  override val imageLoadError = "Could not decode image."
  override val tryExternalApp = "Try external app"
  override val prevImageCd = "Previous image"
  override val nextImageCd = "Next image"
  override val zoomOutCd = "Zoom out"
  override val zoomInCd = "Zoom in"
  override val rotate90Cd = "Rotate 90 degrees"
  override val videoLoadError: (Int, Int) -> String = { w, e -> "Could not play video (code $w/$e)." }
  override val openWithExternalPlayer = "Open with external player"
  override val unmuteCd = "Unmute"
  override val muteCd = "Mute"
  override val rewind10Cd = "Rewind 10s"
  override val forward10Cd = "Forward 10s"
  override val pdfPageOf: (Int, Int) -> String = { cur, total -> "PAGE $cur / $total" }
  override val pdfAccessError = "Could not access PDF file."
  override val pdfEmptyError = "This PDF contains no pages."
  override val pdfPasswordError = "This PDF is password-protected."
  override val pdfGenericError = "Could not open PDF."
  override val prevPageCd = "Previous page"
  override val nextPageCd = "Next page"
  override val zoomOutPdfCd = "Zoom out"
  override val zoomInPdfCd = "Zoom in"

  override val chooserTitle = "Select"
  override val chooserSubtitle: (String) -> String = { name -> "Open \"$name\" with:" }
  override val chooserInternalSection = "Built-in NexoFiles viewers"
  override val chooserExternalSection = "Installed device apps"
  override val chooserNoExternalApps = "No compatible external apps found."
  override val chooserAlwaysBtn = "Always"
  override val chooserOnceBtn = "This Time Only"
  override val viewerAudioTitle = "Nexo Audio Player"
  override val viewerAudioSub = "Native audio player with mini-bar"
  override val viewerVideoTitle = "Nexo Video Player"
  override val viewerVideoSub = "Built-in Media3 ExoPlayer engine"
  override val viewerImageTitle = "Nexo Image Viewer"
  override val viewerImageSub = "Viewer with zoom, rotation & gallery"
  override val viewerPdfTitle = "Nexo PDF Viewer"
  override val viewerPdfSub = "Built-in page-by-page PDF reader"
  override val viewerTextHexTitle = "Nexo Note & Text/Hex Viewer"
  override val viewerTextHexSub = "Text, code & hexadecimal dump inspector"
  override val viewerArchiveTitle = "Nexo ZIP / RAR Inspector"
  override val viewerArchiveSub = "Browse and extract compressed archives"
  override val chooserSavedDefaultToast: (String, String) -> String = { ext, app -> "Default for .$ext: $app" }

  override val driveTitle = "Cloud & Backup"
  override val addCloudBtn = "+ Cloud"
  override val driveNotConnectedHeadline = "Connect your Google Drive"
  override val driveNotConnectedBody = "Use Credential Manager and Google Drive API v3 to browse files, upload content, and schedule automatic backups."
  override val signInGoogleBtn = "Sign in with Google"
  override val authorizeDriveScopeBtn = "Authorize Google Drive API access"
  override val disconnectBtn = "Disconnect"
  override val uploadSafBtn = "Upload SAF file"
  override val newFolderBtn = "New folder"
  override val driveTabFiles = "Drive Files"
  override val driveTabBackup = "Auto-Backup"
  override val driveTabClouds = "Other Clouds"
  override val driveRootName = "My Drive"
  override val driveGoUp = "Go to parent folder"
  override val driveEmptyFolder = "This Drive folder is empty."
  override val backupNowBtn = "Back up now"
  override val syncingProgress = "Syncing…"
  override val addRuleBtn = "+ Rule"
  override val noBackupRules = "Add a rule to automatically back up local folders like DCIM/Camera."
  override val backupFrequencyWifi = "Wi-Fi only"
  override val backupFrequencyDaily = "Wi-Fi or mobile"
  override val externalCloudsDesc = "Connect additional WebDAV / HTTPS cloud storage accounts (Nextcloud, ownCloud, Koofr, etc.)."
  override val addCloudServerTitle = "Add cloud server"
  override val aliasNameLabel = "Alias name"
  override val urlOrHostLabel = "WebDAV URL or Host"
  override val userLabel = "Username"
  override val passwordLabel = "Password"
  override val useHttpsLabel = "Use HTTPS"
  override val addBackupRuleTitle = "New backup rule"
  override val pickFolderHint = "Local folder for Auto-Backup"
  override val tapToChooseFolder = "Tap to browse and choose folder"
  override val frequencyLabel = "Frequency"
  override val wifiOnlyLabel = "Wi-Fi connection only"
  override val saveRuleBtn = "Save rule"
  override val newDriveFolderTitle = "New Drive folder"

  override val networkTitle = "LAN & Remote"
  override val networkSubtitle = "FTP / FTPS / SFTP / WebDAV / SMB"
  override val disconnectShort = "Disconnect"
  override val addServerTitle = "Add server"
  override val emptyRemoteFolder = "Remote directory is empty."
  override val scanWifiDevices = "Scan Wi-Fi devices"
  override val scanningLan = "Scanning…"
  override val savedServersTitle = "Saved servers"
  override val noSavedServers = "No saved servers yet. Tap + to add FTP, FTPS, SFTP, WebDAV or SMB."
  override val connectBtn = "Connect"
  override val lanDevicesTitle = "Discovered LAN devices"
  override val noLanDevices = "Tap \"Scan\" to detect computers and servers on your network."
  override val openPortsLabel: (String) -> String = { ports -> "Open ports: $ports" }
  override val saveAsServerBtn = "Save as server"
  override val newServerModalTitle = "New remote server"
  override val protocolLabel = "Protocol"
  override val hostOrIpLabel = "Host or IP"
  override val portLabel = "Port"
  override val domainLabel = "Domain (optional)"
  override val anonymousAccess = "Anonymous login"
  override val ftpModeLabel = "FTP mode:"
  override val encodingLabel = "Encoding:"

  override val senderTitleScreen = "Sender Wi-Fi / QR"
  override val senderSubtitleScreen = "Share files without internet"
  override val sameWifiMode = "Same Wi-Fi direct mode"
  override val sameWifiDesc = "Prioritizes current LAN IP for maximum speed"
  override val visibleNameLabel = "Visible name"
  override val receiveFolderLabel = "Receive folder"
  override val tabSend = "Send"
  override val tabReceive = "Receive"
  override val tabShareApp = "Share App"
  override val sendActionTitle = "Send files over Wi-Fi"
  override val sendActionDesc = "Pick photos, videos, docs, or ZIPs and generate an instant QR link."
  override val chooseFilesBtn = "Choose files"
  override val shareApkBtn = "Share APK"
  override val shareApkDesc = "Share the NexoFiles APK to install on another phone offline."
  override val shareNexoApkBtn = "Share NexoFiles APK"
  override val receiveModeTitle = "Receive files on this device"
  override val receiveModeDesc = "Start the receiving portal to upload files from any phone or PC."
  override val activateReceiveBtn = "Enable Receive mode"
  override val senderActiveTitle = "Sender active"
  override val openBrowserQrHint = "Scan this QR code or open the address on another device:"
  override val stopServerBtn = "Stop server"
  override val transferHistoryTitle = "Transfer history"
  override val noTransfersYet = "No transfers recorded yet."

  override val viewOnPcTitle = "View on PC (HTTP Wi-Fi)"
  override val viewOnPcSubtitle = "Browse and transfer files from any web browser"
  override val httpServerActive = "HTTP SERVER ACTIVE"
  override val httpServerStopped = "SERVER STOPPED"
  override val copyAddressBtn = "Copy address"
  override val viewOnPcInstruction = "Connect your phone and PC to the same Wi-Fi network and start the server."
  override val stopHttpServerBtn = "Stop server"
  override val startHttpServerPort: (Int) -> String = { port -> "Start server on port $port" }
  override val portConfigTitle = "Port & activity settings"
  override val httpPortLabel = "HTTP Port"
  override val transfersStat: (String, Int) -> String = { event, count -> "$event · Transfers: $count" }

  override val appManagerScreenTitle = "App Manager"
  override val appManagerCount: (Int, Int) -> String = { u, s -> "$u user · $s system" }
  override val searchAppPlaceholder = "Search app or package…"
  override val tabUserApps: (Int) -> String = { n -> "User ($n)" }
  override val tabSystemApps: (Int) -> String = { n -> "System ($n)" }
  override val tabBackedUpApks: (Int) -> String = { n -> "APKs ($n)" }
  override val backupBtn = "Back up"
  override val installBtn = "Install"
  override val uninstallCd = "Uninstall"
  override val noBackedUpApks = "No backed up APKs in NexoFiles/Backed-up Apps."
  override val backedUpApkBadge = "BACKED UP APK"

  override val downloaderScreenTitle = "Download Manager"
  override val downloaderScreenSubtitle = "Direct HTTP/HTTPS downloads"
  override val pasteUrlLabel = "Paste http:// or https:// URL"
  override val clipboardCd = "Paste from clipboard"
  override val optionalFileNameLabel = "File name (optional)"
  override val downloadNowBtn = "Download now"
  override val downloadingProgress: (String, Int) -> String = { name, pct -> "Downloading $name ($pct%)" }
  override val downloadHistoryTitle = "Download history"
  override val noDownloadsYet = "No downloads recorded yet."
  override val bytesStat: (String, String) -> String = { bytes, status -> "$bytes · $status" }

  override val vaultScreenTitle = "Private Vault"
  override val vaultScreenSubtitle = "Almacenamiento privado protegidos"
  override val lockBtn = "Lock"
  override val vaultLockedHeadline = "VAULT SEALED"
  override val vaultLockedDesc = "Autenticación requerida"
  override val unlockNowBtn = "Unlock now"
  override val addEncryptedFileBtn = "Encrypt file into Vault"
  override val vaultEmpty = "No encrypted files in your Vault."
  override val vaultAuthError: (String) -> String = { msg -> "Authentication required: $msg" }

  override val vaultDriveBackupBtn = "Drive .nexo_vault"
  override val vaultWebPortalBtn = "Web / QR Portal"
  override val vaultInternalBadge = "PROTECTED"
  override val vaultSecurityManagedInSettingsNote = ""
  override val vaultDriveModalTitle = "Google Drive Encrypted Backup"
  override val vaultDriveModalDesc = "Encrypts your files as .nexo_vault (AES-256-GCM + PBKDF2) inside the NexoVault_Encrypted Drive folder."
  override val vaultPassphraseLabel = "Portable encryption passphrase"
  override val vaultPassphrasePlaceholder = "Minimum 4 chars (also used in Safari/Web)"
  override val vaultBackupAllBtn = "Upload encrypted backup to Drive"
  override val vaultRefreshBackupsBtn = "List backups"
  override val vaultNoDriveBackups = "No .nexo_vault backups found in Google Drive."
  override val vaultRestoreFromDriveBtn = "Decrypt to Vault"
  override val vaultPortalModalTitle = "Ephemeral Web / iPhone Access"
  override val vaultPortalModalDesc = "Generates a random session token and QR code to inspect & decrypt files locally in iPhone Safari or browser."
  override val vaultStartPortalBtn = "Generate Token + QR"
  override val vaultStopPortalBtn = "Revoke session & stop portal"
  override val vaultPortalCopyLinkBtn = "Copy link"
  override val vaultPortalActiveHint = "Scan with iPhone Camera (Safari) or open on same Wi-Fi. Enter your portable passphrase to decrypt locally:"

  override val externalCloudsTitle = "Other Clouds"
  override val externalCloudsSubtitle = "OneDrive · Dropbox · WebDAV / Nextcloud"
  override val externalCloudsProvidersTitle = "LINK EXTERNAL PROVIDER"
  override val externalCloudsSavedTitle = "SAVED CLOUD SERVERS"
  override val externalCloudsEmptySaved = "No WebDAV, Dropbox, or OneDrive connections saved yet."

  override val vaultSelectAllBtn = "Select all"
  override val vaultClearSelBtn = "Clear"
  override val vaultSelectedCountLabel: (Int) -> String = { n -> "$n SELECTED" }
  override val backupSelectedToDriveBtn: (Int) -> String = { n -> "Back up selected ($n)" }
  override val restoreSelectedBtn: (Int) -> String = { n -> "Restore selected ($n)" }
  override val driveSelectedFilesLabel: (Int) -> String = { n -> "$n item(s) checked" }

  override val googlePhotosTitle = "Google Photos"
  override val googlePhotosSubtitle = "Secure Photo Picker API & direct library upload"
  override val cloudTabDrive = "Google Drive"
  override val cloudTabPhotos = "Google Photos"
  override val connectGooglePhotosBtn = "Connect Google Photos"
  override val googlePhotosNotConnectedDesc = "Authorize Google Photos (Picker API + AppendOnly) to import full-resolution cloud photos and upload edited copies directly from NexoFiles."
  override val openPhotosPickerBtn = "Pick from Google Photos"
  override val checkPickerSelectionBtn = "Sync selection"
  override val pickerWaitingBanner = "Picker session active. Choose your photos in Google Photos and return to NexoFiles."
  override val pickerPollingProgress = "Fetching selected cloud photos…"
  override val noPickedPhotosYet = "No photos imported in this session yet. Tap “Pick from Google Photos” to browse your cloud library."
  override val pickedPhotosSectionTitle: (Int) -> String = { n -> "Selected photos ($n)" }
  override val downloadPhotoToLocalBtn = "Save locally"
  override val downloadedReadyBadge = "Cached"
  override val clearPickedSessionBtn = "New selection"
  override val photoEditorTitle = "Nexo Photo Editor"
  override val photoEditorNonDestructiveSub = "Non-destructive editing · Original file stays untouched"
  override val editorTabAdjust = "Adjust"
  override val editorTabFilters = "Filters"
  override val editorTabTransform = "Crop & Rotate"
  override val sliderBrightness = "Brightness"
  override val sliderContrast = "Contrast"
  override val sliderSaturation = "Saturation"
  override val sliderWarmth = "Temperature / Warmth"
  override val filterOriginal = "Original"
  override val filterVivid = "Vivid"
  override val filterHighContrast = "High Contrast"
  override val filterMono = "Monochrome"
  override val filterNightViolet = "Night Violet"
  override val cropFree = "Free"
  override val rotate90Label = "Rotate 90°"
  override val flipHorizontalLabel = "Flip H"
  override val flipVerticalLabel = "Flip V"
  override val resetEditsBtn = "Reset adjustments"
  override val saveToDeviceBtn = "Save to device"
  override val uploadCopyGooglePhotosBtn = "Upload copy to Google Photos"
  override val exportFormatLabel = "Output format:"
  override val savingPhotoProgress = "Rendering high-quality image…"
  override val savedToPicturesToast: (String) -> String = { path -> "Saved to Pictures/NexoFiles: $path" }
  override val uploadedToGooglePhotosToast: (String) -> String = { name -> "Copy uploaded to Google Photos: $name" }
}

val LocalStrings = staticCompositionLocalOf<AppStrings> { EsStrings }

fun resolveAppStrings(option: AppLanguageOption, deviceLocale: Locale = Locale.getDefault()): AppStrings {
  return when (option) {
    AppLanguageOption.ES -> EsStrings
    AppLanguageOption.EN -> EnStrings
    AppLanguageOption.AUTO -> if (deviceLocale.language.equals("en", ignoreCase = true)) EnStrings else EsStrings
  }
}

fun applyAppLocale(option: AppLanguageOption) {
  runCatching {
    val locales = when (option) {
      AppLanguageOption.AUTO -> LocaleListCompat.getEmptyLocaleList()
      AppLanguageOption.ES -> LocaleListCompat.forLanguageTags("es")
      AppLanguageOption.EN -> LocaleListCompat.forLanguageTags("en")
    }
    AppCompatDelegate.setApplicationLocales(locales)
  }
}
