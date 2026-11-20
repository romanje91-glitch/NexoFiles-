package com.example

import com.example.NexoPlayerPrefs
import com.example.NexoOnboardingStore
import com.example.NexoFirstRunOnboardingDialog
import com.example.NexoFeatureFlags
import com.example.NexoVaultDeepLink
import android.view.WindowManager
import com.example.NexoMasterDirs
import com.example.NexoVaultFlipGuard
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.NexoShareLinks
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.NexoNotificationPrefs
import com.example.NexoCrashLibrary
import com.example.NexoShareAppDialog
import com.example.NexoMultiGoogleStore
import com.example.ui.components.NexoOtaUpdater
import com.example.ui.components.NexoXmbWaveBackground
import androidx.compose.runtime.SideEffect
import com.example.NexoGlobalClipboard
import com.example.NexoGlobalClipboardBar
import com.example.NexoTorrentManagerDialog
import com.example.NexoNavBridge
import com.example.NexoSimpleWebBrowserDialog
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.AppLanguageOption
import com.example.data.model.LocalFileItem
import com.example.data.model.RemoteProtocol
import com.example.data.model.ThemeMode
import com.example.data.model.VaultFileItem
import com.example.ui.audio.AudioMiniPlayerBar
import com.example.ui.audio.AudioPlayerFullDialog
import com.example.ui.audio.AudioPlayerManager
import com.example.ui.components.IconContainerBadge
import com.example.ui.components.NexoIcons
import com.example.ui.components.QuickSettingsSheet
import com.example.ui.components.ThemeModeSelector
import com.example.ui.i18n.LocalStrings
import com.example.ui.i18n.applyAppLocale
import com.example.ui.i18n.resolveAppStrings
import com.example.ui.screens.AnalyzeScreen
import com.example.ui.screens.AppLaunchOption
import com.example.ui.screens.AppManagerScreen
import com.example.ui.screens.BottomBarCustomizationDialog
import com.example.ui.screens.CategoryFilesScreen
import com.example.ui.screens.DownloaderScreen
import com.example.ui.screens.ExternalCloudsScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.GoogleDriveScreen
import com.example.ui.screens.GooglePhotosScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageViewerScreen
import com.example.ui.screens.NetworkScreen
import com.example.ui.screens.NexoInternalViewerIds
import com.example.ui.screens.NexoPhotoEditorScreen
import com.example.ui.screens.NoteEditorScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.RecycleBinScreen
import com.example.ui.screens.SelectAppChooserDialog
import com.example.ui.screens.SenderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ToolsHubScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.screens.ViewOnPcScreen
import com.example.ui.screens.launchExternalAppOption
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoFilesTheme
import com.example.ui.theme.NexoTheme
import com.example.ui.theme.resolveDarkTheme
import com.example.ui.viewmodel.AnalyzerViewModel
import com.example.ui.viewmodel.MainExplorerViewModel
import com.example.ui.viewmodel.ToolsAndNetworkViewModel
import com.example.util.CrashLogger
import com.example.util.FormatUtils
import com.example.util.StoragePermissionHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.absoluteValue

object NexoRoutes {
  const val HOME = "home"
  const val FILES = "files"
  const val ANALYZE = "analyze"
  const val TOOLS = "tools"
  const val CATEGORY = "category"
  const val DRIVE = "drive"
  const val GOOGLE_PHOTOS = "google_photos"
  const val EXTERNAL_CLOUDS = "external_clouds"
  const val VIEW_ON_PC = "view_on_pc"
  const val SENDER = "sender"
  const val NETWORK = "network"
  const val APP_MANAGER = "app_manager"
  const val NOTE_EDITOR = "note_editor"
  const val IMAGE_VIEWER = "image_viewer"
  const val PHOTO_EDITOR = "photo_editor"
  const val VIDEO_PLAYER = "video_player"
  const val PDF_VIEWER = "pdf_viewer"
  const val DOWNLOADER = "downloader"
  const val VAULT = "vault"
  const val RECYCLE_BIN = "recycle_bin"
  const val SETTINGS = "settings"
}

private data class BottomTab(
  val id: String,
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
)

class MainActivity : FragmentActivity() {
  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    if (intent.getBooleanExtra("nexo_open_vault", false)) {
      NexoVaultDeepLink.pendingOpenVault = true
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    CrashLogger.install(applicationContext)
    super.onCreate(savedInstanceState)
    NexoCrashLibrary.install(applicationContext)
    NexoMasterDirs.ensureAll()
    NexoFeatureFlags.init(applicationContext)
    NexoPlayerPrefs.init(applicationContext)
    if (intent?.getBooleanExtra("nexo_open_vault", false) == true) { NexoVaultDeepLink.pendingOpenVault = true }
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
    )
    val appContainer = (application as NexoFilesApp).container

    setContent {
      val explorerVm: MainExplorerViewModel = viewModel(
        factory = MainExplorerViewModel.Factory(this, appContainer),
      )
      val themeMode by explorerVm.themeMode.collectAsStateWithLifecycle()
      val isDarkTheme = resolveDarkTheme(themeMode)

      LaunchedEffect(isDarkTheme) {
        val barStyle = if (isDarkTheme) {
          SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
          SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
      }

      NexoFilesTheme(themeMode = themeMode, darkTheme = isDarkTheme) {
        NexoFilesRoot(
          container = appContainer,
          context = this,
          explorerVm = explorerVm,
          themeMode = themeMode,
        )
      }
    }
  }
}

@Composable
private fun NexoFilesRoot(
  container: AppContainer,
  context: FragmentActivity,
  explorerVm: MainExplorerViewModel,
  themeMode: ThemeMode,
) {
  val analyzerVm: AnalyzerViewModel = viewModel(
    factory = AnalyzerViewModel.Factory(container),
  )
  val toolsVm: ToolsAndNetworkViewModel = viewModel(
    factory = ToolsAndNetworkViewModel.Factory(context, container),
  )

  val explorerState by explorerVm.uiState.collectAsStateWithLifecycle()
  val bookmarks by explorerVm.bookmarks.collectAsStateWithLifecycle()
  val vaultBiometricEnabled by explorerVm.vaultBiometricEnabled.collectAsStateWithLifecycle()
  val allSettings by explorerVm.allSettings.collectAsStateWithLifecycle()
  val defaultAppMappings by explorerVm.defaultAppMappings.collectAsStateWithLifecycle()
  val analysisState by analyzerVm.analysis.collectAsStateWithLifecycle()
  val duplicateProgress by analyzerVm.duplicateProgress.collectAsStateWithLifecycle()
  val similarScanState by analyzerVm.similarScanState.collectAsStateWithLifecycle()
  val selectedDuplicates by analyzerVm.selectedDuplicates.collectAsStateWithLifecycle()
  val selectedSimilarPhotos by analyzerVm.selectedSimilarPhotos.collectAsStateWithLifecycle()
  val cleanupState by analyzerVm.cleanupState.collectAsStateWithLifecycle()
  val recycleBinItems by analyzerVm.recycleBinItems.collectAsStateWithLifecycle()
  val driveState by toolsVm.driveState.collectAsStateWithLifecycle()
  val googlePhotosState by toolsVm.googlePhotosState.collectAsStateWithLifecycle()
  val autoBackupRules by toolsVm.autoBackupRules.collectAsStateWithLifecycle()
  val webServerState by toolsVm.webServerState.collectAsStateWithLifecycle()
  val webServerPort by toolsVm.webServerPort.collectAsStateWithLifecycle()
  val senderState by toolsVm.senderState.collectAsStateWithLifecycle()
  val senderHistory by toolsVm.senderHistory.collectAsStateWithLifecycle()
  val networkState by toolsVm.networkState.collectAsStateWithLifecycle()
  val savedServers by toolsVm.savedServers.collectAsStateWithLifecycle()
  val appManagerState by toolsVm.appManagerState.collectAsStateWithLifecycle()
  val downloads by toolsVm.downloads.collectAsStateWithLifecycle()
  val activeDownload by toolsVm.activeDownloadProgress.collectAsStateWithLifecycle()
  val noteEditorState by toolsVm.noteEditorState.collectAsStateWithLifecycle()
  val vaultState by toolsVm.vaultState.collectAsStateWithLifecycle()
  val vaultPortalState by toolsVm.vaultPortalState.collectAsStateWithLifecycle()
  val bottomBarTabIds by toolsVm.bottomBarTabs.collectAsStateWithLifecycle()

  val strings = resolveAppStrings(allSettings.appLanguage)
  LaunchedEffect(allSettings.appLanguage) {
    applyAppLocale(allSettings.appLanguage)
  }

  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_STOP -> {
          if (!toolsVm.isLaunchingExternalPicker.value) {
            toolsVm.lockVault()
          }
        }
        Lifecycle.Event.ON_RESUME -> {
          toolsVm.setExternalPickerActive(false)
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val allTabDefinitions = remember(strings) {
    mapOf(
      "HOME" to BottomTab("HOME", NexoRoutes.HOME, strings.navHome, Icons.Rounded.Home, Icons.Outlined.Home),
      "FILES" to BottomTab("FILES", NexoRoutes.FILES, strings.navFiles, NexoIcons.Folder, NexoIcons.FolderOutlined),
      "VAULT" to BottomTab("VAULT", NexoRoutes.VAULT, strings.navVault, Icons.Rounded.Lock, Icons.Rounded.Lock),
      "ANALYZE" to BottomTab("ANALYZE", NexoRoutes.ANALYZE, strings.navAnalyze, NexoIcons.Analytics, NexoIcons.AnalyticsOutlined),
      "TOOLS" to BottomTab("TOOLS", NexoRoutes.TOOLS, strings.navTools, Icons.Rounded.Build, Icons.Outlined.Build),
      "DRIVE" to BottomTab("DRIVE", NexoRoutes.DRIVE, strings.drawerDrive, NexoIcons.Cloud, NexoIcons.Cloud),
      "EXTERNAL_CLOUDS" to BottomTab("EXTERNAL_CLOUDS", NexoRoutes.EXTERNAL_CLOUDS, strings.drawerExternalClouds, NexoIcons.Cloud, NexoIcons.Cloud),
      "NETWORK" to BottomTab("NETWORK", NexoRoutes.NETWORK, strings.networkTitle, NexoIcons.Router, NexoIcons.Router),
      "RECYCLE_BIN" to BottomTab("RECYCLE_BIN", NexoRoutes.RECYCLE_BIN, strings.drawerRecycleBin, NexoIcons.DeleteOutline, NexoIcons.DeleteOutline),
      "SETTINGS" to BottomTab("SETTINGS", NexoRoutes.SETTINGS, strings.drawerSettings, NexoIcons.Tune, NexoIcons.Tune),
    )
  }
  val bottomTabs = remember(bottomBarTabIds, allTabDefinitions) {
    bottomBarTabIds.mapNotNull { allTabDefinitions[it] }.ifEmpty {
      listOfNotNull(allTabDefinitions["HOME"], allTabDefinitions["FILES"])
    }
  }

  val navController = rememberNavController()
  val navBackStack by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStack?.destination?.route ?: NexoRoutes.HOME
  SideEffect {
    NexoNavBridge.isInMainPager = (currentRoute == NexoRoutes.HOME || currentRoute == null)
    NexoNavBridge.linkedGoogleEmail = driveState.accountEmail.orEmpty()
    NexoNavBridge.onVaultFolderMoved = { items, folder -> toolsVm.moveVaultItemsToFolderAndSync(items, folder) }
  }
  val pagerState = rememberPagerState(initialPage = 0, pageCount = { bottomTabs.size })
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

  LaunchedEffect(bottomTabs.size) {
    if (pagerState.currentPage >= bottomTabs.size) {
      pagerState.scrollToPage((bottomTabs.size - 1).coerceAtLeast(0))
    }
  }

  var showQuickSettings by remember { mutableStateOf(false) }
  var showBottomBarDialog by remember { mutableStateOf(false) }
  var showStartupCrashDialog by remember { mutableStateOf(CrashLogger.hasUnseenCrash(context)) }
  var pendingSafExportPath by remember { mutableStateOf<String?>(null) }
  var viewerTargetPath by remember { mutableStateOf<String?>(null) }
  var viewerIsVaultMode by remember { mutableStateOf(false) }
  var photoEditorTargetPath by remember { mutableStateOf<String?>(null) }
  var viewerImageGallery by remember { mutableStateOf<List<String>>(emptyList()) }
  var chooserTargetFile by remember { mutableStateOf<File?>(null) }
  var chooserTargetGallery by remember { mutableStateOf<List<String>>(emptyList()) }
  var showFullAudioPlayer by remember { mutableStateOf(false) }
  var showShareAppDialog by remember { mutableStateOf(false) }
  var showFirstRunOnboarding by remember { mutableStateOf(NexoOnboardingStore.shouldShow(context)) }
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
      }
    } else { {} }
    onDispose {
      unregisterFlip()
      if (currentRoute != NexoRoutes.VAULT) {
        act?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
      }
    }
  }
  val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }
  LaunchedEffect(NexoVaultDeepLink.pendingOpenVault) {
    if (NexoVaultDeepLink.pendingOpenVault) {
      NexoVaultDeepLink.pendingOpenVault = false
      navController.navigate(NexoRoutes.VAULT) { launchSingleTop = true }
      if (vaultState.isDriveBusy) toolsVm.reopenVaultBackupProgress()
    }
  }
  LaunchedEffect(driveState.accountEmail, driveState.accessToken) { NexoMultiGoogleStore.registerAccount(context, driveState.accountEmail, driveState.accountName, driveState.accessToken) }

  val audioPlayer = remember(context) { AudioPlayerManager(context, coroutineScope) }
  DisposableEffect(audioPlayer) {
    onDispose { audioPlayer.release() }
  }
  val audioState by audioPlayer.state.collectAsStateWithLifecycle()

  BackHandler(enabled = drawerState.isOpen) {

    coroutineScope.launch { drawerState.close() }
  }

  // Handle Google Drive authorization consent when initiated outside GoogleDriveScreen (e.g., from Vault)
  val vaultDriveConsentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartIntentSenderForResult(),
  ) { result ->
    toolsVm.onDriveConsentIntentResult(result.data)
  }
  LaunchedEffect(driveState.pendingConsentIntent, currentRoute) {
    if (currentRoute != NexoRoutes.DRIVE) {
      val pending = driveState.pendingConsentIntent ?: return@LaunchedEffect
      toolsVm.consumePendingDriveConsent()
      vaultDriveConsentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
    }
  }

  LaunchedEffect(Unit) {
    launch { explorerVm.messages.collect { snackbarHostState.showSnackbar(it) } }
    launch { analyzerVm.messages.collect { snackbarHostState.showSnackbar(it) } }
    launch { toolsVm.messages.collect { snackbarHostState.showSnackbar(it) } }
  }

  val allFilesLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
  ) {
    explorerVm.refreshPermissionsAndDashboard()
    analyzerVm.runScan()
  }

  val mediaPermLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) {
    explorerVm.refreshPermissionsAndDashboard()
  }

  val safImportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    uri?.let { explorerVm.importFromSaf(it) }
  }

  val safDriveUploadLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    uri?.let {
      coroutineScope.launch {
        val res = container.localFiles.importFromSafUri(it, context.cacheDir.absolutePath)
        res.onSuccess { localTemp -> toolsVm.uploadLocalFileToDrive(localTemp) }
      }
    }
  }

  val safExportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("*/*"),
  ) { uri ->
    val srcPath = pendingSafExportPath
    pendingSafExportPath = null
    if (uri != null && srcPath != null) {
      explorerVm.exportToSaf(srcPath, uri)
    }
  }

  val jsonBackupExportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json"),
  ) { uri ->
    uri?.let { explorerVm.exportSettingsBackup(it) }
  }

  val jsonBackupImportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    uri?.let { explorerVm.importSettingsBackup(it) }
  }

  fun requestAllFilesPermission() {
    allFilesLauncher.launch(StoragePermissionHelper.createManageAllFilesIntent(context))
  }

  fun requestMediaPermissions() {
    mediaPermLauncher.launch(StoragePermissionHelper.requiredRuntimePermissions())
  }

  val showPrimaryNav = currentRoute == NexoRoutes.HOME
  val activeRouteOrTab = if (currentRoute == NexoRoutes.HOME) {
    bottomTabs.getOrNull(pagerState.currentPage)?.route ?: NexoRoutes.HOME
  } else {
    currentRoute
  }

  fun navigateRoute(route: String) {
    navController.navigate(route) {
      launchSingleTop = true
    }
  }

  fun navigateTab(route: String) {
    val targetPage = bottomTabs.indexOfFirst { it.route == route }
    if (targetPage >= 0) {
      if (currentRoute != NexoRoutes.HOME) {
        navController.popBackStack(NexoRoutes.HOME, inclusive = false)
      }
      coroutineScope.launch {
        pagerState.animateScrollToPage(
          page = targetPage,
          animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        )
      }
    } else {
      navigateRoute(route)
    }
  }

  fun openPhotoEditor(path: String) {
    photoEditorTargetPath = path
    navigateRoute(NexoRoutes.PHOTO_EDITOR)
  }

  fun executeInternalViewer(
    viewerId: String,
    file: File,
    siblingGallery: List<String> = emptyList(),
    isVaultMode: Boolean = false,
  ) {
    when (viewerId) {
      NexoInternalViewerIds.AUDIO -> {
        val queue = if (isVaultMode) {
          listOf(file)
        } else {
          file.parentFile?.listFiles()
            ?.filter { it.isFile && FormatUtils.isAudio(it.name) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
        }
        audioPlayer.playTrack(file, queue, isVaultMode = isVaultMode)
      }
      NexoInternalViewerIds.VIDEO -> {
        viewerIsVaultMode = isVaultMode
        viewerTargetPath = file.absolutePath
        navigateRoute(NexoRoutes.VIDEO_PLAYER)
      }
      NexoInternalViewerIds.IMAGE -> {
        viewerIsVaultMode = isVaultMode
        viewerTargetPath = file.absolutePath
        viewerImageGallery = siblingGallery.ifEmpty {
          if (isVaultMode) {
            listOf(file.absolutePath)
          } else {
            file.parentFile?.listFiles()
              ?.filter { it.isFile && FormatUtils.isImage(it.name) }
              ?.sortedBy { it.name.lowercase() }
              ?.map { it.absolutePath }
              ?: listOf(file.absolutePath)
          }
        }
        navigateRoute(NexoRoutes.IMAGE_VIEWER)
      }
      NexoInternalViewerIds.PDF -> {
        viewerIsVaultMode = isVaultMode
        viewerTargetPath = file.absolutePath
        navigateRoute(NexoRoutes.PDF_VIEWER)
      }
      NexoInternalViewerIds.TEXT_HEX -> {
        viewerIsVaultMode = isVaultMode
        toolsVm.openFileInEditor(file.absolutePath, isVaultMode = isVaultMode)
        navigateRoute(NexoRoutes.NOTE_EDITOR)
      }
      NexoInternalViewerIds.ARCHIVE -> {
        val archiveItem = LocalFileItem(
          name = file.name,
          path = file.absolutePath,
          isDirectory = false,
          sizeBytes = file.length(),
          lastModified = file.lastModified(),
          isHidden = file.isHidden,
          mimeType = FormatUtils.getMimeType(file.name),
        )
        file.parentFile?.absolutePath?.let { explorerVm.loadDirectory(it) }
        explorerVm.inspectArchive(archiveItem)
        navigateTab(NexoRoutes.FILES)
      }
      else -> if (!isVaultMode) explorerVm.openFile(file)
    }
  }

  /**
   * Direct Internal Opening ONLY for Private Vault entries.
   * Strictly bypasses Android Chooser, saved package defaults, and external sharing.
   */
  fun openVaultFileInternally(item: VaultFileItem, file: File) {
    val probeName = item.originalName.ifBlank { file.name }
    val viewerId = when {
      FormatUtils.isImage(probeName) || FormatUtils.isImage(file.name) -> NexoInternalViewerIds.IMAGE
      FormatUtils.isVideo(probeName) || FormatUtils.isVideo(file.name) -> NexoInternalViewerIds.VIDEO
      FormatUtils.isAudio(probeName) || FormatUtils.isAudio(file.name) -> NexoInternalViewerIds.AUDIO
      FormatUtils.isPdf(probeName) || FormatUtils.isPdf(file.name) -> NexoInternalViewerIds.PDF
      else -> NexoInternalViewerIds.TEXT_HEX
    }
    executeInternalViewer(
      viewerId = viewerId,
      file = file,
      siblingGallery = listOf(file.absolutePath),
      isVaultMode = true,
    )
  }

  fun openFileSmart(
    file: File,
    siblingGallery: List<String> = emptyList(),
    forceChooser: Boolean = false,
  ) {
    explorerVm.recordRecentOpen(file)
    if (!forceChooser) {
      val ext = file.extension.lowercase()
      val savedHandler = if (ext.isNotBlank()) defaultAppMappings[ext] else null
      if (savedHandler != null) {
        if (savedHandler.startsWith("internal:")) {
          executeInternalViewer(savedHandler, file, siblingGallery, isVaultMode = false)
          return
        } else if (savedHandler.startsWith("pkg:")) {
          val spec = savedHandler.removePrefix("pkg:")
          val pkg = spec.substringBefore('/')
          val act = spec.substringAfter('/', "")
          if (pkg.isNotBlank() && act.isNotBlank() && launchExternalAppOption(context, file, pkg, act)) {
            return
          }
        }
      }
    }
    chooserTargetGallery = siblingGallery
    chooserTargetFile = file
  }

  fun closeActiveViewerAndReturn() {
    if (viewerIsVaultMode) {
      viewerIsVaultMode = false
      toolsVm.purgeVaultPreviewCache()
    }
    navController.popBackStack()
  }

  CompositionLocalProvider(LocalStrings provides strings) {
    if (showFirstRunOnboarding) {
    val isEsOnb = strings.languageTitle.contains("Idioma", ignoreCase = true)
    NexoFirstRunOnboardingDialog(
      context = context,
      isEs = isEsOnb,
      onRequestStorage = {
        if (Build.VERSION.SDK_INT >= 30) {
          runCatching {
            val i = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
            context.startActivity(i)
          }
        }
      },
      onConnectDrive = { (context as? android.app.Activity)?.let { toolsVm.connectGoogleDrive(it) } },
      onComplete = { showFirstRunOnboarding = false }
    )
  }
  if (showShareAppDialog || NexoShareLinks.showShareDialog) {
    NexoShareAppDialog(onDismiss = { showShareAppDialog = false; NexoShareLinks.showShareDialog = false })
  }
  if (NexoNavBridge.showWebBrowser) {
    NexoSimpleWebBrowserDialog(onDismiss = { NexoNavBridge.showWebBrowser = false })
  }
  if (NexoNavBridge.showTorrentManager) {
    NexoTorrentManagerDialog(
      initialMagnet = NexoNavBridge.pendingMagnetUri,
      initialTorrentPath = NexoNavBridge.pendingTorrentFilePath,
      onOpenSaveFolder = { dirPath ->
        explorerVm.loadDirectory(dirPath)
        if (currentRoute != NexoRoutes.HOME) navController.popBackStack(NexoRoutes.HOME, false)
        val fIdx = bottomTabs.indexOfFirst { it.id == "FILES" }.coerceAtLeast(0)
        coroutineScope.launch { pagerState.animateScrollToPage(fIdx) }
      },
      onDismiss = {
        NexoNavBridge.showTorrentManager = false
        NexoNavBridge.pendingMagnetUri = ""
        NexoNavBridge.pendingTorrentFilePath = ""
      }
    )
  }
  ModalNavigationDrawer(
      drawerState = drawerState,
      gesturesEnabled = showPrimaryNav || drawerState.isOpen,
      drawerContent = {
        NexoDrawerSheet(
          activeRouteOrTab = activeRouteOrTab,
          drawerOrderIds = allSettings.drawerOrderIds,
          drawerHiddenIds = allSettings.drawerHiddenIds,
          usedBytes = explorerState.storageStats.usedBytes,
          totalBytes = explorerState.storageStats.totalBytes,
          themeMode = themeMode,
          appLanguage = allSettings.appLanguage,
          onSelectThemeMode = explorerVm::setThemeMode,
          onSelectAppLanguage = explorerVm::setAppLanguage,
          onNavigateTab = { route ->
            coroutineScope.launch { drawerState.close() }
            navigateTab(route)
          },
          onNavigateRoute = { route ->
            coroutineScope.launch { drawerState.close() }
            navigateTab(route)
          },
        )
      },
    ) {
      BoxWithConstraints(
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background),
      ) {
        val useNavRail = maxWidth >= 720.dp
        val longPressBarModifier = Modifier.pointerInput(Unit) {
          val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis
          val touchSlop = viewConfiguration.touchSlop
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val startPos = down.position
            val expired = withTimeoutOrNull(longPressTimeoutMs) {
              while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) return@withTimeoutOrNull false
                if ((change.position - startPos).getDistance() > touchSlop) return@withTimeoutOrNull false
              }
            } == null
            if (expired) {
              showBottomBarDialog = true
            }
          }
        }

        Scaffold(
          containerColor = MaterialTheme.colorScheme.background,
          contentWindowInsets = WindowInsets.safeDrawing,
          snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
              Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(6.dp),
                containerColor = NexoTheme.semantic.modalSurface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                actionColor = MaterialTheme.colorScheme.primary,
              )
            }
          },
          bottomBar = {
          if (!NexoNavBridge.mediaImmersiveMode) {
            Column(modifier = Modifier.fillMaxWidth()) {
              NexoGlobalClipboardBar(
                activeTabId = bottomTabs.getOrNull(pagerState.currentPage)?.id ?: "HOME",
                isInMainPager = (currentRoute == NexoRoutes.HOME || currentRoute == null),
                currentLocalPath = explorerState.currentPath,
                isVaultUnlocked = vaultState.isUnlocked,
                onPasteLocal = {
                  toolsVm.executeGlobalPasteToLocal(explorerState.currentPath) {
                    explorerVm.loadDirectory(explorerState.currentPath)
                  }
                },
                onPasteVault = {
                  toolsVm.executeGlobalPasteToVault {
                    explorerVm.loadDirectory(explorerState.currentPath)
                  }
                },
                onGoFiles = {
                  if (currentRoute != NexoRoutes.HOME) navController.popBackStack(NexoRoutes.HOME, false)
                  val idx = bottomTabs.indexOfFirst { it.id == "FILES" }.coerceAtLeast(0)
                  coroutineScope.launch { pagerState.animateScrollToPage(idx) }
                },
                onGoVault = {
                  if (currentRoute != NexoRoutes.HOME) navController.popBackStack(NexoRoutes.HOME, false)
                  val idx = bottomTabs.indexOfFirst { it.id == "VAULT" }.coerceAtLeast(0)
                  coroutineScope.launch { pagerState.animateScrollToPage(idx) }
                },
                onClear = { NexoGlobalClipboard.clear() }
              )
               if (!NexoNavBridge.mediaImmersiveMode) 
            Column(modifier = Modifier.fillMaxWidth()) {
              AudioMiniPlayerBar(
                state = audioState,
                onTogglePlayPause = audioPlayer::togglePlayPause,
                onPrevious = audioPlayer::playPrevious,
                onNext = audioPlayer::playNext,
                onOpenFullPlayer = { showFullAudioPlayer = true },
                onClose = {
                  showFullAudioPlayer = false
                  audioPlayer.stop()
                },
              )
              if (showPrimaryNav && !useNavRail) {
                Surface(
                  color = MaterialTheme.colorScheme.surface,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                  modifier = longPressBarModifier.testTag("bottom_nav_surface"),
                ) {
                  NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                  ) {
                    bottomTabs.forEachIndexed { idx, tab ->
                      val selected = pagerState.currentPage == idx
                      NavigationBarItem(
                        selected = selected,
                        onClick = { navigateTab(tab.route) },
                        modifier = Modifier.testTag("bottom_tab_${tab.route}"),
                        icon = {
                          Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                          )
                        },
                        label = {
                          Text(
                            text = tab.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                          )
                        },
                        colors = NavigationBarItemDefaults.colors(
                          selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                          selectedTextColor = MaterialTheme.colorScheme.primary,
                          indicatorColor = MaterialTheme.colorScheme.primary,
                          unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                          unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                      )
                    }
                  }
                }
              }
            }
          
            }
          }
        },
        ) { innerPadding ->
          Row(
            modifier = Modifier
              .fillMaxSize()
              .padding(innerPadding),
          ) {
            if (showPrimaryNav && useNavRail) {
              NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                  .fillMaxHeight()
                  .border(1.dp, MaterialTheme.colorScheme.outline)
                  .then(longPressBarModifier),
              ) {
                bottomTabs.forEachIndexed { idx, tab ->
                  val selected = pagerState.currentPage == idx
                  NavigationRailItem(
                    selected = selected,
                    onClick = { navigateTab(tab.route) },
                    icon = {
                      Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                      )
                    },
                    label = {
                      Text(
                        text = tab.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                      )
                    },
                    colors = NavigationRailItemDefaults.colors(
                      selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                      selectedTextColor = MaterialTheme.colorScheme.primary,
                      indicatorColor = MaterialTheme.colorScheme.primary,
                      unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                      unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                  )
                }
              }
            }

            Box(modifier = Modifier.weight(1f)) {
              NavHost(
              navController = navController,
              startDestination = NexoRoutes.HOME,
              enterTransition = { EnterTransition.None },
              exitTransition = { ExitTransition.None },
              popEnterTransition = { EnterTransition.None },
              popExitTransition = { ExitTransition.None },
              modifier = Modifier.fillMaxSize(),
            ) {
              composable(NexoRoutes.HOME) {
                val activeTabId = bottomTabs.getOrNull(pagerState.currentPage)?.id ?: "HOME"
                SideEffect { NexoNavBridge.activePagerTabId = activeTabId; NexoNavBridge.isInMainPager = true }
                BackHandler(enabled = (activeTabId != "HOME" || explorerState.currentPath.trimEnd('/') != "/storage/emulated/0") && !drawerState.isOpen) {
                    val inSubfolder = explorerState.currentPath.trimEnd('/') != "/storage/emulated/0"
                    when {
                        activeTabId == "FILES" && inSubfolder -> explorerVm.navigateUp()
                        activeTabId == "DRIVE" && driveState.isAuthorized && toolsVm.navigateDriveUp() -> Unit
                        inSubfolder && explorerVm.navigateUp() -> Unit
                        else -> {
                            val homeIndex = bottomTabs.indexOfFirst { it.id == "HOME" }.coerceAtLeast(0)
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    page = homeIndex,
                                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                                )
                            }
                        }
                    }
                }

                HorizontalPager(
                  state = pagerState,
                  userScrollEnabled = !(activeTabId == "FILES" && explorerState.currentPath.trimEnd('/') != "/storage/emulated/0"),
                  modifier = Modifier.fillMaxSize(),
                  beyondViewportPageCount = 1,
                ) { page ->
                  val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                  val pageAlpha = 1f - (pageOffset * 0.14f)
                  val pageScale = 1f - (pageOffset * 0.025f)
                  val pageTabId = bottomTabs.getOrNull(page)?.id ?: "HOME"

                  Box(
                    modifier = Modifier
                      .fillMaxSize()
                      .graphicsLayer {
                        alpha = pageAlpha
                        scaleX = pageScale
                        scaleY = pageScale
                      },
                  ) {
                    when (pageTabId) {
                      "HOME" -> HomeScreen(
                        uiState = explorerState,
                        bookmarks = bookmarks,
                        allSettings = allSettings,
                        onSearchQueryChange = explorerVm::updateSearchQuery,
                        onQuickCleanClick = {
                          navigateTab(NexoRoutes.ANALYZE)
                          analyzerVm.cleanJunkAndCache()
                        },
                        onCategoryClick = { cat ->
                          explorerVm.openCategory(cat)
                          navigateRoute(NexoRoutes.CATEGORY)
                        },
                        onOpenDrive = { navigateTab(NexoRoutes.DRIVE) },
                        onOpenViewOnPc = { navigateRoute(NexoRoutes.VIEW_ON_PC) },
                        onOpenSender = { navigateRoute(NexoRoutes.SENDER) },
                        onOpenNetwork = { navigateTab(NexoRoutes.NETWORK) },
                        onOpenAppManager = { navigateRoute(NexoRoutes.APP_MANAGER) },
                        onOpenNoteEditor = {
                          toolsVm.openFileInEditor(null)
                          navigateRoute(NexoRoutes.NOTE_EDITOR)
                        },
                        onOpenDownloader = { navigateRoute(NexoRoutes.DOWNLOADER) },
                        onOpenVault = { navigateTab(NexoRoutes.VAULT) },
                        onOpenRecycleBin = { navigateTab(NexoRoutes.RECYCLE_BIN) },
                        onOpenQuickSettings = { showQuickSettings = true },
                        onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                        onGrantAllFiles = ::requestAllFilesPermission,
                        onGrantMedia = ::requestMediaPermissions,
                        onOpenLocalFile = { item ->
                          if (item.isDirectory) {
                            explorerVm.loadDirectory(item.path)
                            navigateTab(NexoRoutes.FILES)
                          } else {
                            openFileSmart(File(item.path))
                          }
                        },
                        onOpenBookmarkPath = { path ->
                          explorerVm.loadDirectory(path)
                          navigateTab(NexoRoutes.FILES)
                        },
                        onDeleteBookmark = explorerVm::deleteBookmark,
                      )
                      "FILES" -> FilesScreen(
                        uiState = explorerState,
                        layoutViewMode = allSettings.layoutViewMode,
                        onChangeLayoutMode = explorerVm::setLayoutViewMode,
                        onNavigateToPath = explorerVm::loadDirectory,
                        onNavigateUp = { explorerVm.navigateUp() },
                        onToggleHidden = explorerVm::toggleHiddenFiles,
                        onSortBy = explorerVm::setSortBy,
                        onToggleSelect = explorerVm::toggleSelection,
                        onSelectAll = explorerVm::selectAllCurrent,
                        onClearSelection = explorerVm::clearSelection,
                        onOpenFile = { item ->
                          val gallery = explorerState.files
                            .filter { !it.isDirectory && FormatUtils.isImage(it.name) }
                            .map { it.path }
                          openFileSmart(File(item.path), gallery)
                        },
                        onOpenWith = { item ->
                          val gallery = explorerState.files
                            .filter { !it.isDirectory && FormatUtils.isImage(it.name) }
                            .map { it.path }
                          openFileSmart(File(item.path), gallery, forceChooser = true)
                        },
                        onOpenInNoteEditor = { item ->
                          toolsVm.openFileInEditor(item.path)
                          navigateRoute(NexoRoutes.NOTE_EDITOR)
                        },
                        onOpenInPhotoEditor = { item -> openPhotoEditor(item.path) },
                        onInspectArchive = explorerVm::inspectArchive,
                        onExtractArchive = explorerVm::extractArchive,
                        onExtractArchiveTo = explorerVm::extractArchiveTo,
                        onCreateFolder = explorerVm::createFolder,
                        onCreateTextFile = explorerVm::createTextFile,
                        onRenameItem = explorerVm::renameItem,
                        onStageClipboard = explorerVm::stageClipboard,
                        onPasteClipboard = explorerVm::pasteClipboard,
                        onClearClipboard = explorerVm::clearClipboard,
                        onMoveToRecycleBin = explorerVm::moveToRecycleBin,
                        onDeletePermanently = explorerVm::deletePermanently,
                        onSharePaths = explorerVm::sharePaths,
                        onOpenProperties = explorerVm::openProperties,
                        onDismissProperties = explorerVm::dismissProperties,
                        onDismissArchiveInspector = explorerVm::dismissArchiveInspector,
                        onToggleBookmark = explorerVm::toggleBookmark,
                        onCreateZip = explorerVm::createZipFromPaths,
                        onImportSaf = { safImportLauncher.launch(arrayOf("*/*")) },
                        onExportSaf = { path ->
                          pendingSafExportPath = path
                          safExportLauncher.launch(File(path).name)
                        },
                        onUploadToDrive = { item ->
                          if (driveState.isAuthorized) {
                            toolsVm.uploadLocalFileToDrive(File(item.path))
                          } else {
                            navigateTab(NexoRoutes.DRIVE)
                            coroutineScope.launch {
                              snackbarHostState.showSnackbar("Conecta tu cuenta de Google Drive para subir ${item.name}")
                            }
                          }
                        },
                        onLockInVault = { item -> explorerVm.lockInVault(item.path) },
                      )
                      "VAULT" -> VaultScreen(
                        state = vaultState,
                        portalState = vaultPortalState,
                        activeLockMethod = allSettings.vaultLockMethod,
                        hasConfiguredSecret = allSettings.vaultSecretHash.isNotBlank(),
                        vaultBiometricEnabled = vaultBiometricEnabled,
                        isDriveAuthorized = driveState.isAuthorized,
                        securityManager = container.security,
                        onVerifySecret = explorerVm::verifyVaultSecret,
                        onUnlockSuccess = toolsVm::unlockVault,
                        onLockVault = toolsVm::lockVault,
                        onAddUriToVault = toolsVm::addUriToVault,
                        onOpenVaultItem = { item ->
                          toolsVm.openVaultItem(item) { decryptedFile, vaultItem ->
                            openVaultFileInternally(vaultItem, decryptedFile)
                          }
                        },
                        onRestoreVaultItem = toolsVm::restoreVaultItem,
                        onDeleteVaultItem = toolsVm::deleteVaultItem,
                        onConnectDrive = { toolsVm.connectGoogleDrive(context) },
                        onRefreshDriveBackups = toolsVm::refreshVaultDriveBackups,
                        onBackupVaultToDrive = { pass -> toolsVm.backupVaultToDrive(pass) },
                    onRunSmartBackup = { toolsVm.backupVaultToDrive("", emptySet(), showProgressModal = true) },
                    onDismissBackupProgress = { toolsVm.dismissVaultBackupProgress() },
                    onReopenBackupProgress = { toolsVm.reopenVaultBackupProgress() },
                    onCancelBackup = { toolsVm.cancelVaultBackup() },
                    onTogglePauseBackup = { toolsVm.togglePauseVaultBackup() },
                    onPasteVault = { toolsVm.executeGlobalPasteToVault { explorerVm.loadDirectory(explorerState.currentPath) } },
                        onBackupSelectedVaultToDrive = { pass, ids -> toolsVm.backupVaultToDrive(pass, ids) },
                        onRestoreDriveBackup = toolsVm::restoreVaultBackupFromDrive,
                        onRestoreSelectedDriveBackups = toolsVm::restoreSelectedVaultBackupsFromDrive,
                        onExternalPickerStateChange = toolsVm::setExternalPickerActive,
                        onStartPortal = toolsVm::startVaultWebPortal,
                        onStopPortal = toolsVm::stopVaultWebPortal,
                        onBack = { navigateTab(NexoRoutes.HOME) },
                      )
                      "ANALYZE" -> AnalyzeScreen(
                        analysis = analysisState,
                        selectedDuplicates = selectedDuplicates,
                        recycleBinItems = recycleBinItems,
                        cleanupState = cleanupState,
                        duplicateProgress = duplicateProgress,
                        similarScanState = similarScanState,
                        selectedSimilarPhotos = selectedSimilarPhotos,
                        onRescan = analyzerVm::runScan,
                        onRescanDuplicates = analyzerVm::runDedicatedDuplicateScan,
                        onToggleDuplicate = analyzerVm::toggleDuplicateSelection,
                        onSmartSelectDuplicates = analyzerVm::smartSelectDuplicates,
                        onDeleteSelectedDuplicates = { analyzerVm.deleteSelectedDuplicates(permanent = false) },
                        onScanSimilarPhotos = analyzerVm::runDedicatedSimilarPhotosScan,
                        onToggleSimilarPhoto = analyzerVm::toggleSimilarPhotoSelection,
                        onSmartSelectSimilarPhotos = analyzerVm::smartSelectSimilarPhotos,
                        onDeleteSelectedSimilarPhotos = { analyzerVm.deleteSelectedSimilarPhotos(permanent = false) },
                        onEditPhotoPath = ::openPhotoEditor,
                        onCleanJunk = analyzerVm::cleanJunkAndCache,
                        onTrashLargeFile = analyzerVm::moveLargeFileToTrash,
                        onOpenFolderPath = { path ->
                          explorerVm.loadDirectory(path)
                          navigateTab(NexoRoutes.FILES)
                        },
                        onRestoreRecycleItem = analyzerVm::restoreRecycleItem,
                        onDeleteRecycleForever = analyzerVm::deleteRecycleItemForever,
                        onEmptyRecycleBin = analyzerVm::emptyRecycleBin,
                        onDismissCleanupConfirmation = analyzerVm::dismissCleanupConfirmation,
                        onUndoLastCleanup = analyzerVm::undoLastCleanup,
                      )
                      "DRIVE" -> GoogleDriveScreen(
                        driveState = driveState,
                        photosState = googlePhotosState,
                        autoBackupRules = autoBackupRules,
                        defaultLocalBackupPath = "${container.localFiles.rootStoragePath}/DCIM/Camera",
                        availableVolumes = explorerState.availableVolumes,
                        onBack = { navigateTab(NexoRoutes.HOME) },
                        onConnectDrive = toolsVm::connectGoogleDrive,
                        onSilentRefreshGoogle = { toolsVm.refreshGoogleSessionsSilently(forceDriveReload = false) },
                        onConsentIntentResult = toolsVm::onDriveConsentIntentResult,
                        onConsumePendingConsent = toolsVm::consumePendingDriveConsent,
                        onDisconnect = toolsVm::disconnectGoogleDrive,
                        onOpenFolder = { folder -> toolsVm.loadDriveFolder(folder.id, folder.name) },
                        onClickBreadcrumb = toolsVm::navigateDriveToBreadcrumb,
                        onNavigateUpDrive = toolsVm::navigateDriveUp,
                        onCreateFolder = toolsVm::createDriveFolder,
                        onPickAndUploadFile = { safDriveUploadLauncher.launch(arrayOf("*/*")) },
                        onDownloadFile = toolsVm::downloadDriveFile,
                        onDownloadSelectedFiles = toolsVm::downloadSelectedDriveFiles,
                        onDeleteFile = toolsVm::deleteDriveFile,
                        onDeleteSelectedFiles = toolsVm::deleteSelectedDriveFiles,
                        onAddAutoBackupRule = toolsVm::addAutoBackupRule,
                        onToggleAutoBackupRule = toolsVm::toggleAutoBackupRule,
                        onDeleteAutoBackupRule = toolsVm::deleteAutoBackupRule,
                        onRunAutoBackupNow = toolsVm::runAutoBackupNow,
                        onConnectGooglePhotos = toolsVm::connectGooglePhotos,
                        onPhotosConsentIntentResult = toolsVm::onPhotosConsentIntentResult,
                        onConsumePendingPhotosConsent = toolsVm::consumePendingPhotosConsent,
                        onDisconnectGooglePhotos = toolsVm::disconnectGooglePhotos,
                        onStartPhotosPickerSession = { toolsVm.startGooglePhotosPickerSession(context) },
                        onCheckPhotosPickerSelection = toolsVm::pollActivePhotosPickerNow,
                        onClearPhotosSession = toolsVm::clearPickedPhotosSession,
                        onEnsurePhotoCached = toolsVm::ensurePickedPhotoCached,
                        onSavePickedPhotoToDevice = toolsVm::savePickedPhotoToPictures,
                        onEditPickedPhotoInNexo = { item ->
                          toolsVm.preparePickedPhotoForEdit(item, ::openPhotoEditor)
                        },
                      )
                      "EXTERNAL_CLOUDS" -> ExternalCloudsScreen(
                        savedServers = savedServers,
                        onSaveWebDavCloudAccount = { name, host, port, user, pass, useHttps ->
                          toolsVm.saveNetworkServer(
                            name = name,
                            protocol = RemoteProtocol.WEBDAV,
                            host = host,
                            port = port,
                            username = user,
                            password = pass,
                            useHttps = useHttps,
                          )
                        },
                        onOpenServer = { srv ->
                          toolsVm.connectRemoteServer(srv, srv.remotePath)
                          navigateTab(NexoRoutes.NETWORK)
                        },
                        onDeleteServer = toolsVm::deleteSavedServer,
                        onBack = { navigateTab(NexoRoutes.HOME) },
                      )
                      "NETWORK" -> NetworkScreen(
                        state = networkState,
                        savedServers = savedServers,
                        onScanLan = toolsVm::scanLan,
                        onSaveServer = toolsVm::saveNetworkServer,
                        onDeleteServer = toolsVm::deleteSavedServer,
                        onConnectServer = toolsVm::connectRemoteServer,
                        onDisconnectServer = toolsVm::disconnectRemoteServer,
                        onDownloadRemoteFile = toolsVm::downloadRemoteFile,
                        onBack = { navigateTab(NexoRoutes.HOME) },
                      )
                      "RECYCLE_BIN" -> RecycleBinScreen(
                        items = recycleBinItems,
                        onRestore = analyzerVm::restoreRecycleItem,
                        onDeleteForever = analyzerVm::deleteRecycleItemForever,
                        onEmptyAll = analyzerVm::emptyRecycleBin,
                        onBack = { navigateTab(NexoRoutes.HOME) },
                      )
                      "SETTINGS" -> SettingsScreen(
                        themeMode = themeMode,
                        showHiddenFiles = explorerState.showHiddenFiles,
                        sortBy = explorerState.sortBy,
                        vaultBiometricEnabled = vaultBiometricEnabled,
                        hasAllFilesAccess = explorerState.hasAllFilesAccess,
                        webServerPort = webServerPort,
                        allSettings = allSettings,
                        availableVolumes = explorerState.availableVolumes,
                        bottomBarTabs = bottomBarTabIds,
                        onSelectThemeMode = explorerVm::setThemeMode,
                        onToggleHiddenFiles = explorerVm::setHiddenFiles,
                        onSelectSortBy = explorerVm::setSortBy,
                        onToggleVaultBiometric = explorerVm::setVaultBiometric,
                        onGrantStorageAccess = ::requestAllFilesPermission,
                        onOpenViewOnPc = { navigateRoute(NexoRoutes.VIEW_ON_PC) },
                        onUpdateBooleanSetting = explorerVm::updateBooleanSetting,
                        onUpdateStringSetting = explorerVm::updateStringSetting,
                        onSelectLanguage = explorerVm::setAppLanguage,
                        onSetHomeDirectory = explorerVm::setHomeDirectory,
                        onSetBluetoothDir = explorerVm::setBluetoothReceiveDir,
                        onSetDownloadDir = explorerVm::setDownloadDirectory,
                        onSetSearchEngineMode = explorerVm::setSearchEngineMode,
                        onSetLayoutViewMode = explorerVm::setLayoutViewMode,
                        onSetDrawerConfig = explorerVm::setDrawerConfig,
                        onSetToolbarActions = explorerVm::setToolbarActions,
                        onToggleBottomBarTab = toolsVm::toggleBottomBarTab,
                        onMoveBottomBarTab = toolsVm::moveBottomBarTab,
                        onResetBottomBarTabs = toolsVm::resetBottomBarTabs,
                        onSetAppBackupDirectory = explorerVm::setAppBackupDirectory,
                        onSetInitialWindows = explorerVm::setInitialWindows,
                        onSetMasterPin = explorerVm::setMasterPin,
                        onSetVaultSecurityConfig = explorerVm::setVaultSecurityConfig,
                        onVerifyVaultSecret = explorerVm::verifyVaultSecret,
                        onSaveGestureBinding = explorerVm::saveGestureBinding,
                        onDeleteGestureBinding = explorerVm::deleteGestureBinding,
                        onClearAppCacheNow = explorerVm::clearAppCacheNow,
                        onClearRecentHistoryNow = explorerVm::clearRecentHistoryNow,
                        onResetDefaults = explorerVm::resetAllSettings,
                        onExportSettingsJson = { jsonBackupExportLauncher.launch("nexofiles_respaldo.json") },
                        onImportSettingsJson = { jsonBackupImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        securityManager = container.security,
                        onVerifyMasterPin = explorerVm::verifyMasterPin,
                        defaultAppMappingsCount = defaultAppMappings.size,
                        onResetDefaultApps = { explorerVm.resetDefaultApps(strings.resetDefaultAppsToast) },
                        onBack = { navigateTab(NexoRoutes.HOME) },
                      )
                      else -> ToolsHubScreen(
                        driveAccountEmail = driveState.accountEmail,
                        googlePhotosEmail = googlePhotosState.accountEmail,
                        webServerState = webServerState,
                        onToggleWebServer = toolsVm::toggleWebServer,
                        onOpenDrive = { navigateTab(NexoRoutes.DRIVE) },
                        onOpenGooglePhotos = { navigateRoute(NexoRoutes.GOOGLE_PHOTOS) },
                        onOpenExternalClouds = { navigateTab(NexoRoutes.EXTERNAL_CLOUDS) },
                        onOpenViewOnPc = { navigateRoute(NexoRoutes.VIEW_ON_PC) },
                        onOpenSender = { navigateRoute(NexoRoutes.SENDER) },
                        onOpenNetwork = { navigateTab(NexoRoutes.NETWORK) },
                        onOpenAppManager = { navigateRoute(NexoRoutes.APP_MANAGER) },
                        onOpenNoteEditor = {
                          toolsVm.openFileInEditor(null)
                          navigateRoute(NexoRoutes.NOTE_EDITOR)
                        },
                        onOpenDownloader = { navigateRoute(NexoRoutes.DOWNLOADER) },
                        onOpenVault = { navigateTab(NexoRoutes.VAULT) },
                        onOpenRecycleBin = { navigateTab(NexoRoutes.RECYCLE_BIN) },
                        onOpenSettings = { navigateTab(NexoRoutes.SETTINGS) },
                      )
                    }
                  }
                }
              }

              composable(NexoRoutes.CATEGORY) {
                val activeCat = explorerState.activeCategory
                if (activeCat != null) {
                  CategoryFilesScreen(
                    category = activeCat,
                    files = explorerState.categoryFiles,
                    isLoading = explorerState.isLoadingCategory,
                    onBack = {
                      explorerVm.openCategory(null)
                      navController.popBackStack()
                    },
                    onOpenFile = { item ->
                      val gallery = explorerState.categoryFiles
                        .filter { !it.isDirectory && FormatUtils.isImage(it.name) }
                        .map { it.path }
                      openFileSmart(File(item.path), gallery)
                    },
                    onShareFile = { explorerVm.sharePaths(listOf(it.path)) },
                    onMoveToTrash = {
                      explorerVm.moveToRecycleBin(listOf(it.path))
                      explorerVm.openCategory(activeCat)
                    },
                  )
                }
              }

              composable(NexoRoutes.DRIVE) {
                GoogleDriveScreen(
                  driveState = driveState,
                  photosState = googlePhotosState,
                  autoBackupRules = autoBackupRules,
                  defaultLocalBackupPath = "${container.localFiles.rootStoragePath}/DCIM/Camera",
                  availableVolumes = explorerState.availableVolumes,
                  onBack = { navController.popBackStack() },
                  onConnectDrive = toolsVm::connectGoogleDrive,
                  onSilentRefreshGoogle = { toolsVm.refreshGoogleSessionsSilently(forceDriveReload = false) },
                  onConsentIntentResult = toolsVm::onDriveConsentIntentResult,
                  onConsumePendingConsent = toolsVm::consumePendingDriveConsent,
                  onDisconnect = toolsVm::disconnectGoogleDrive,
                  onOpenFolder = { folder -> toolsVm.loadDriveFolder(folder.id, folder.name) },
                  onClickBreadcrumb = toolsVm::navigateDriveToBreadcrumb,
                  onNavigateUpDrive = toolsVm::navigateDriveUp,
                  onCreateFolder = toolsVm::createDriveFolder,
                  onPickAndUploadFile = { safDriveUploadLauncher.launch(arrayOf("*/*")) },
                  onDownloadFile = toolsVm::downloadDriveFile,
                  onDownloadSelectedFiles = toolsVm::downloadSelectedDriveFiles,
                  onDeleteFile = toolsVm::deleteDriveFile,
                  onDeleteSelectedFiles = toolsVm::deleteSelectedDriveFiles,
                  onAddAutoBackupRule = toolsVm::addAutoBackupRule,
                  onToggleAutoBackupRule = toolsVm::toggleAutoBackupRule,
                  onDeleteAutoBackupRule = toolsVm::deleteAutoBackupRule,
                  onRunAutoBackupNow = toolsVm::runAutoBackupNow,
                  onConnectGooglePhotos = toolsVm::connectGooglePhotos,
                  onPhotosConsentIntentResult = toolsVm::onPhotosConsentIntentResult,
                  onConsumePendingPhotosConsent = toolsVm::consumePendingPhotosConsent,
                  onDisconnectGooglePhotos = toolsVm::disconnectGooglePhotos,
                  onStartPhotosPickerSession = { toolsVm.startGooglePhotosPickerSession(context) },
                  onCheckPhotosPickerSelection = toolsVm::pollActivePhotosPickerNow,
                  onClearPhotosSession = toolsVm::clearPickedPhotosSession,
                  onEnsurePhotoCached = toolsVm::ensurePickedPhotoCached,
                  onSavePickedPhotoToDevice = toolsVm::savePickedPhotoToPictures,
                  onEditPickedPhotoInNexo = { item ->
                    toolsVm.preparePickedPhotoForEdit(item, ::openPhotoEditor)
                  },
                )
              }

              composable(NexoRoutes.EXTERNAL_CLOUDS) {
                ExternalCloudsScreen(
                  savedServers = savedServers,
                  onSaveWebDavCloudAccount = { name, host, port, user, pass, useHttps ->
                    toolsVm.saveNetworkServer(
                      name = name,
                      protocol = RemoteProtocol.WEBDAV,
                      host = host,
                      port = port,
                      username = user,
                      password = pass,
                      useHttps = useHttps,
                    )
                  },
                  onOpenServer = { srv ->
                    toolsVm.connectRemoteServer(srv, srv.remotePath)
                    navigateTab(NexoRoutes.NETWORK)
                  },
                  onDeleteServer = toolsVm::deleteSavedServer,
                  onBack = { navController.popBackStack() },
                )
              }

              composable(NexoRoutes.GOOGLE_PHOTOS) {
                GooglePhotosScreen(
                  photosState = googlePhotosState,
                  onBack = { navController.popBackStack() },
                  onConnectPhotos = toolsVm::connectGooglePhotos,
                  onSilentRefreshPhotos = { toolsVm.refreshGoogleSessionsSilently(forceDriveReload = false) },
                  onConsentIntentResult = toolsVm::onPhotosConsentIntentResult,
                  onConsumePendingConsent = toolsVm::consumePendingPhotosConsent,
                  onDisconnectPhotos = toolsVm::disconnectGooglePhotos,
                  onStartPickerSession = { toolsVm.startGooglePhotosPickerSession(context) },
                  onCheckPickerSelection = toolsVm::pollActivePhotosPickerNow,
                  onClearSession = toolsVm::clearPickedPhotosSession,
                  onEnsureCached = toolsVm::ensurePickedPhotoCached,
                  onSaveToDevice = toolsVm::savePickedPhotoToPictures,
                  onEditInNexo = { item ->
                    toolsVm.preparePickedPhotoForEdit(item, ::openPhotoEditor)
                  },
                )
              }

              composable(NexoRoutes.VIEW_ON_PC) {
                ViewOnPcScreen(
                  state = webServerState,
                  configuredPort = webServerPort,
                  onPortChange = toolsVm::setWebServerPort,
                  onToggleServer = toolsVm::toggleWebServer,
                  onCopiedUrl = {
                    coroutineScope.launch { snackbarHostState.showSnackbar("URL copiada al portapapeles") }
                  },
                  onBack = { navController.popBackStack() },
                )
              }

              composable(NexoRoutes.SENDER) {
                SenderScreen(
                  senderState = senderState,
                  history = senderHistory,
                  nickname = allSettings.senderNickname,
                  savePath = allSettings.senderSavePath,
                  sameWifiMode = allSettings.senderSameWifiMode,
                  availableVolumes = explorerState.availableVolumes,
                  onStageAndSendUris = toolsVm::stageSenderUris,
                  onStartReceiveMode = toolsVm::startSenderReceiveMode,
                  onStartShareAppMode = toolsVm::startSenderShareAppMode,
                  onStopServer = toolsVm::stopSenderServer,
                  onClearHistory = toolsVm::clearSenderHistory,
                  onUpdateNickname = explorerVm::setSenderNickname,
                  onUpdateSavePath = explorerVm::setSenderSavePath,
                  onToggleSameWifiMode = explorerVm::setSenderSameWifiMode,
                  onBack = { navController.popBackStack() },
                )
              }

              composable(NexoRoutes.NETWORK) {
                NetworkScreen(
                  state = networkState,
                  savedServers = savedServers,
                  onScanLan = toolsVm::scanLan,
                  onSaveServer = toolsVm::saveNetworkServer,
                  onDeleteServer = toolsVm::deleteSavedServer,
                  onConnectServer = toolsVm::connectRemoteServer,
                  onDisconnectServer = toolsVm::disconnectRemoteServer,
                  onDownloadRemoteFile = toolsVm::downloadRemoteFile,
                  onBack = { navController.popBackStack() },
                )
              }

              composable(NexoRoutes.APP_MANAGER) {
                AppManagerScreen(
                  state = appManagerState,
                  onLoadApps = toolsVm::loadInstalledApps,
                  onSearchChange = toolsVm::setAppSearchQuery,
                  onBackupApk = toolsVm::backupApk,
                  onInstallApk = toolsVm::installApk,
                  onUninstallApp = toolsVm::uninstallApp,
                  onBack = { navController.popBackStack() },
                )
              }

              composable(NexoRoutes.NOTE_EDITOR) {
                NoteEditorScreen(
                  state = noteEditorState,
                  onFileNameChange = toolsVm::updateEditorFileName,
                  onContentChange = toolsVm::updateEditorContent,
                  onSave = {
                    toolsVm.saveEditorFile(explorerState.currentPath)
                    if (!noteEditorState.isVaultMode) {
                      explorerVm.loadDirectory(explorerState.currentPath)
                    }
                  },
                  onBack = { closeActiveViewerAndReturn() },
                )
              }

              composable(NexoRoutes.IMAGE_VIEWER) {
                val target = viewerTargetPath
                if (target != null) {
                  ImageViewerScreen(
                    initialPath = target,
                    imagePaths = viewerImageGallery,
                    isVaultMode = viewerIsVaultMode,
                    onBack = { closeActiveViewerAndReturn() },
                    onShare = { file -> if (!viewerIsVaultMode) FormatUtils.shareFiles(context, listOf(file)) },
                    onOpenExternal = { file -> if (!viewerIsVaultMode) explorerVm.openFile(file) },
                    onEditPhoto = { file -> if (!viewerIsVaultMode) openPhotoEditor(file.absolutePath) },
                  )
                }
              }

              composable(NexoRoutes.PHOTO_EDITOR) {
                val target = photoEditorTargetPath ?: viewerTargetPath
                if (target != null) {
                  NexoPhotoEditorScreen(
                    sourceFilePath = target,
                    isUploadingToGooglePhotos = googlePhotosState.isUploadingCopy,
                    onBack = { navController.popBackStack() },
                    onSavedToDevice = { savedFile ->
                      toolsVm.recordLocalPhotoSaved(
                        file = savedFile,
                        message = strings.savedToPicturesToast(savedFile.name),
                      )
                      explorerVm.loadDirectory(explorerState.currentPath)
                    },
                    onUploadCopyToGooglePhotos = { savedFile, format ->
                      toolsVm.uploadEditedFileToGooglePhotos(
                        editedFile = savedFile,
                        format = format,
                        successMessageFormatter = strings.uploadedToGooglePhotosToast,
                        onConnectRequired = {
                          toolsVm.recordLocalPhotoSaved(
                            file = savedFile,
                            message = strings.savedToPicturesToast(savedFile.name),
                          )
                          toolsVm.connectGooglePhotos(context)
                          navigateRoute(NexoRoutes.GOOGLE_PHOTOS)
                        },
                      )
                    },
                  )
                }
              }

              composable(NexoRoutes.VIDEO_PLAYER) {
                val target = viewerTargetPath
                if (target != null) {
                  VideoPlayerScreen(
                    filePath = target,
                    isVaultMode = viewerIsVaultMode,
                    onBack = { closeActiveViewerAndReturn() },
                    onShare = { file -> if (!viewerIsVaultMode) FormatUtils.shareFiles(context, listOf(file)) },
                    onOpenExternal = { file -> if (!viewerIsVaultMode) explorerVm.openFile(file) },
                  )
                }
              }

              composable(NexoRoutes.PDF_VIEWER) {
                val target = viewerTargetPath
                if (target != null) {
                  PdfViewerScreen(
                    filePath = target,
                    isVaultMode = viewerIsVaultMode,
                    onBack = { closeActiveViewerAndReturn() },
                    onShare = { file -> if (!viewerIsVaultMode) FormatUtils.shareFiles(context, listOf(file)) },
                    onOpenExternal = { file -> if (!viewerIsVaultMode) explorerVm.openFile(file) },
                  )
                }
              }

              composable(NexoRoutes.DOWNLOADER) {
                DownloaderScreen(
                  downloads = downloads,
                  activeProgress = activeDownload,
                  onReadClipboardUrl = toolsVm::readClipboardHttpUrl,
                  onSuggestFileName = toolsVm::suggestDownloadFileName,
                  onStartDownload = toolsVm::startDirectDownload,
                  onOpenDownloadedFile = { openFileSmart(it) },
                  onDeleteRecord = toolsVm::deleteDownloadHistory,
                  onClearHistory = toolsVm::clearDownloadHistory,
                  onBack = { navController.popBackStack() },
                )
              }

              composable(NexoRoutes.VAULT) {
                VaultScreen(
                  state = vaultState,
                  portalState = vaultPortalState,
                  activeLockMethod = allSettings.vaultLockMethod,
                  hasConfiguredSecret = allSettings.vaultSecretHash.isNotBlank(),
                  vaultBiometricEnabled = vaultBiometricEnabled,
                  isDriveAuthorized = driveState.isAuthorized,
                  securityManager = container.security,
                  onVerifySecret = explorerVm::verifyVaultSecret,
                  onUnlockSuccess = toolsVm::unlockVault,
                  onLockVault = toolsVm::lockVault,
                  onAddUriToVault = toolsVm::addUriToVault,
                  onOpenVaultItem = { item ->
                    toolsVm.openVaultItem(item) { decryptedFile, vaultItem ->
                      openVaultFileInternally(vaultItem, decryptedFile)
                    }
                  },
                  onRestoreVaultItem = toolsVm::restoreVaultItem,
                  onDeleteVaultItem = toolsVm::deleteVaultItem,
                  onConnectDrive = { toolsVm.connectGoogleDrive(context) },
                  onRefreshDriveBackups = toolsVm::refreshVaultDriveBackups,
                  onBackupVaultToDrive = { pass -> toolsVm.backupVaultToDrive(pass) },
                    onRunSmartBackup = { toolsVm.backupVaultToDrive("", emptySet(), showProgressModal = true) },
                    onDismissBackupProgress = { toolsVm.dismissVaultBackupProgress() },
                    onReopenBackupProgress = { toolsVm.reopenVaultBackupProgress() },
                    onCancelBackup = { toolsVm.cancelVaultBackup() },
                    onTogglePauseBackup = { toolsVm.togglePauseVaultBackup() },
                    onPasteVault = { toolsVm.executeGlobalPasteToVault { explorerVm.loadDirectory(explorerState.currentPath) } },
                  onBackupSelectedVaultToDrive = { pass, ids -> toolsVm.backupVaultToDrive(pass, ids) },
                  onRestoreDriveBackup = toolsVm::restoreVaultBackupFromDrive,
                  onRestoreSelectedDriveBackups = toolsVm::restoreSelectedVaultBackupsFromDrive,
                  onExternalPickerStateChange = toolsVm::setExternalPickerActive,
                  onStartPortal = toolsVm::startVaultWebPortal,
                  onStopPortal = toolsVm::stopVaultWebPortal,
                  onBack = { navController.popBackStack() },
                )
              }

              composable(NexoRoutes.RECYCLE_BIN) {
                RecycleBinScreen(
                  items = recycleBinItems,
                  onRestore = analyzerVm::restoreRecycleItem,
                  onDeleteForever = analyzerVm::deleteRecycleItemForever,
                  onEmptyAll = analyzerVm::emptyRecycleBin,
                  onBack = { navController.popBackStack() },
                )
              }

              composable(NexoRoutes.SETTINGS) {
                SettingsScreen(
                  themeMode = themeMode,
                  showHiddenFiles = explorerState.showHiddenFiles,
                  sortBy = explorerState.sortBy,
                  vaultBiometricEnabled = vaultBiometricEnabled,
                  hasAllFilesAccess = explorerState.hasAllFilesAccess,
                  webServerPort = webServerPort,
                  allSettings = allSettings,
                  availableVolumes = explorerState.availableVolumes,
                  bottomBarTabs = bottomBarTabIds,
                  onSelectThemeMode = explorerVm::setThemeMode,
                  onToggleHiddenFiles = explorerVm::setHiddenFiles,
                  onSelectSortBy = explorerVm::setSortBy,
                  onToggleVaultBiometric = explorerVm::setVaultBiometric,
                  onGrantStorageAccess = ::requestAllFilesPermission,
                  onOpenViewOnPc = { navigateRoute(NexoRoutes.VIEW_ON_PC) },
                  onUpdateBooleanSetting = explorerVm::updateBooleanSetting,
                  onUpdateStringSetting = explorerVm::updateStringSetting,
                  onSelectLanguage = explorerVm::setAppLanguage,
                  onSetHomeDirectory = explorerVm::setHomeDirectory,
                  onSetBluetoothDir = explorerVm::setBluetoothReceiveDir,
                  onSetDownloadDir = explorerVm::setDownloadDirectory,
                  onSetSearchEngineMode = explorerVm::setSearchEngineMode,
                  onSetLayoutViewMode = explorerVm::setLayoutViewMode,
                  onSetDrawerConfig = explorerVm::setDrawerConfig,
                  onSetToolbarActions = explorerVm::setToolbarActions,
                  onToggleBottomBarTab = toolsVm::toggleBottomBarTab,
                  onMoveBottomBarTab = toolsVm::moveBottomBarTab,
                  onResetBottomBarTabs = toolsVm::resetBottomBarTabs,
                  onSetAppBackupDirectory = explorerVm::setAppBackupDirectory,
                  onSetInitialWindows = explorerVm::setInitialWindows,
                  onSetMasterPin = explorerVm::setMasterPin,
                  onSetVaultSecurityConfig = explorerVm::setVaultSecurityConfig,
                  onVerifyVaultSecret = explorerVm::verifyVaultSecret,
                  onSaveGestureBinding = explorerVm::saveGestureBinding,
                  onDeleteGestureBinding = explorerVm::deleteGestureBinding,
                  onClearAppCacheNow = explorerVm::clearAppCacheNow,
                  onClearRecentHistoryNow = explorerVm::clearRecentHistoryNow,
                  onResetDefaults = explorerVm::resetAllSettings,
                  onExportSettingsJson = { jsonBackupExportLauncher.launch("nexofiles_respaldo.json") },
                  onImportSettingsJson = { jsonBackupImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                  securityManager = container.security,
                  onVerifyMasterPin = explorerVm::verifyMasterPin,
                  defaultAppMappingsCount = defaultAppMappings.size,
                  onResetDefaultApps = { explorerVm.resetDefaultApps(strings.resetDefaultAppsToast) },
                  onBack = { navController.popBackStack() },
                )
              }
            }
              if ((currentRoute == NexoRoutes.HOME || currentRoute == null) && !NexoNavBridge.isMediaViewerOpen && !NexoNavBridge.showWebBrowser && !NexoNavBridge.showTorrentManager) { NexoXmbWaveBackground(modifier = Modifier.fillMaxSize()) }
            }
          }
        }
      }
    }

    if (showQuickSettings) {
      QuickSettingsSheet(
        showHiddenFiles = explorerState.showHiddenFiles,
        hasAllFilesAccess = explorerState.hasAllFilesAccess,
        webServerPort = webServerPort,
        driveEmail = driveState.accountEmail,
        themeMode = themeMode,
        onSelectThemeMode = explorerVm::setThemeMode,
        onToggleHidden = explorerVm::setHiddenFiles,
        onGrantStorage = {
          showQuickSettings = false
          requestAllFilesPermission()
        },
        onOpenSafPicker = { safImportLauncher.launch(arrayOf("*/*")) },
        onOpenDrive = { navigateTab(NexoRoutes.DRIVE) },
        onOpenRecycleBin = { navigateTab(NexoRoutes.RECYCLE_BIN) },
        onOpenFullSettings = { navigateTab(NexoRoutes.SETTINGS) },
        onDismiss = { showQuickSettings = false },
      )
    }

    if (showBottomBarDialog) {
      BottomBarCustomizationDialog(
        activeTabs = bottomBarTabIds,
        onToggleTab = toolsVm::toggleBottomBarTab,
        onMoveTab = toolsVm::moveBottomBarTab,
        onResetDefaults = toolsVm::resetBottomBarTabs,
        onDismiss = { showBottomBarDialog = false },
      )
    }

    if (showStartupCrashDialog) {
      val crashSummary = remember { CrashLogger.getLastCrashSummary(context) }
      val crashDetail = remember { CrashLogger.readCrashLog(context).take(3500) }
      AlertDialog(
        onDismissRequest = {
          CrashLogger.acknowledgeCrash(context)
          showStartupCrashDialog = false
        },
        shape = RoundedCornerShape(10.dp),
        containerColor = NexoTheme.semantic.modalSurface,
        title = {
          Text(
            text = strings.crashAlertTitle.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = NexoTheme.semantic.evaRed,
          )
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = strings.crashAlertDesc,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (crashSummary.isNotBlank()) {
              Text(
                text = crashSummary,
                style = MonoTnumStyle,
                color = NexoTheme.semantic.evaOrange,
                fontWeight = FontWeight.Bold,
              )
            }
            if (crashDetail.isNotBlank()) {
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(max = 200.dp),
              ) {
                Text(
                  text = crashDetail,
                  style = MonoTnumStyle,
                  color = MaterialTheme.colorScheme.onBackground,
                  modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
                )
              }
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              if (CrashLogger.copyCrashLogToClipboard(context)) {
                Toast.makeText(context, strings.crashCopiedToast, Toast.LENGTH_SHORT).show()
              }
              CrashLogger.acknowledgeCrash(context)
              showStartupCrashDialog = false
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(4.dp),
          ) {
            Icon(NexoIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(strings.crashCopyReportBtn.uppercase(), fontWeight = FontWeight.Bold)
          }
        },
        dismissButton = {
          TextButton(
            onClick = {
              CrashLogger.acknowledgeCrash(context)
              showStartupCrashDialog = false
            },
          ) {
            Text(strings.close.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
      )
    }

    chooserTargetFile?.let { targetFile ->
      SelectAppChooserDialog(
        file = targetFile,
        onSelectOption = { option, always ->
          chooserTargetFile = null
          val ext = targetFile.extension.lowercase()
          if (always && ext.isNotBlank()) {
            explorerVm.setDefaultAppForExtension(ext, option.optionId)
            coroutineScope.launch {
              snackbarHostState.showSnackbar(strings.chooserSavedDefaultToast(ext, option.title))
            }
          }
          when (option) {
            is AppLaunchOption.InternalViewer -> {
              executeInternalViewer(option.optionId, targetFile, chooserTargetGallery, isVaultMode = false)
            }
            is AppLaunchOption.ExternalApp -> {
              val launched = launchExternalAppOption(context, targetFile, option.packageName, option.activityName)
              if (!launched) {
                explorerVm.openFile(targetFile)
              }
            }
          }
        },
        onDismiss = { chooserTargetFile = null },
      )
    }

    if (showFullAudioPlayer && audioState.hasActiveTrack) {
      AudioPlayerFullDialog(
        state = audioState,
        onTogglePlayPause = audioPlayer::togglePlayPause,
        onSeekTo = audioPlayer::seekTo,
        onPrevious = audioPlayer::playPrevious,
        onNext = audioPlayer::playNext,
        onOpenExternal = { file ->
          if (!audioState.isVaultMode) {
            showFullAudioPlayer = false
            openFileSmart(file, forceChooser = true)
          }
        },
        onClosePlayer = {
          showFullAudioPlayer = false
          audioPlayer.stop()
        },
        onDismissModal = { showFullAudioPlayer = false },
      )
    }
  }
}

@Composable
private fun NexoDrawerSheet(
  activeRouteOrTab: String,
  drawerOrderIds: List<String>,
  drawerHiddenIds: Set<String>,
  usedBytes: Long,
  totalBytes: Long,
  themeMode: ThemeMode,
  appLanguage: AppLanguageOption,
  onSelectThemeMode: (ThemeMode) -> Unit,
  onSelectAppLanguage: (AppLanguageOption) -> Unit,
  onNavigateTab: (String) -> Unit,
  onNavigateRoute: (String) -> Unit,
) {
  val strings = LocalStrings.current
  ModalDrawerSheet(
    drawerContainerColor = MaterialTheme.colorScheme.surface,
    drawerContentColor = MaterialTheme.colorScheme.onSurface,
    drawerShape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp),
    modifier = Modifier
      .width(316.dp)
      .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)),
  ) {
    Column(
      modifier = Modifier
        .fillMaxHeight()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 18.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconContainerBadge(
          icon = NexoIcons.Folder,
          contentDescription = null,
          size = 46.dp,
          highlighted = true,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = "NEXOFILES // HUD",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = if (totalBytes > 0L) {
              "${FormatUtils.formatBytes(usedBytes)} / ${FormatUtils.formatBytes(totalBytes)}"
            } else {
              strings.drawerSubtitleFallback
            },
            style = MonoTnumStyle,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline)
      Spacer(modifier = Modifier.height(4.dp))

      val drawerItemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
        selectedTextColor = MaterialTheme.colorScheme.onPrimary,
        unselectedContainerColor = MaterialTheme.colorScheme.surface,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurface,
      )
      val drawerItemShape = RoundedCornerShape(4.dp)

      val visibleIds = remember(drawerOrderIds, drawerHiddenIds) {
        val withPhotos = if (
          "DRIVE" in drawerOrderIds &&
          "GOOGLE_PHOTOS" !in drawerOrderIds &&
          "GOOGLE_PHOTOS" !in drawerHiddenIds
        ) {
          val driveIdx = drawerOrderIds.indexOf("DRIVE")
          drawerOrderIds.toMutableList().apply { add(driveIdx + 1, "GOOGLE_PHOTOS") }
        } else {
          drawerOrderIds
        }
        val withExternalClouds = if (
          "EXTERNAL_CLOUDS" !in withPhotos &&
          "EXTERNAL_CLOUDS" !in drawerHiddenIds
        ) {
          val anchorIdx = withPhotos.indexOf("GOOGLE_PHOTOS").takeIf { it >= 0 }
            ?: withPhotos.indexOf("DRIVE")
          withPhotos.toMutableList().apply {
            if (anchorIdx >= 0) add(anchorIdx + 1, "EXTERNAL_CLOUDS") else add("EXTERNAL_CLOUDS")
          }
        } else {
          withPhotos
        }
        withExternalClouds.filterNot { it in drawerHiddenIds }
      }

      visibleIds.forEach { id ->
        when (id) {
          "HOME" -> NavigationDrawerItem(
            label = { Text(strings.drawerHome.uppercase(), style = MaterialTheme.typography.labelLarge) },
            selected = activeRouteOrTab == NexoRoutes.HOME,
            onClick = { onNavigateTab(NexoRoutes.HOME) },
            icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
            colors = drawerItemColors,
            shape = drawerItemShape,
          )
          "FILES" -> NavigationDrawerItem(
            label = { Text(strings.drawerFiles.uppercase(), style = MaterialTheme.typography.labelLarge) },
            selected = activeRouteOrTab == NexoRoutes.FILES,
            onClick = { onNavigateTab(NexoRoutes.FILES) },
            icon = { Icon(NexoIcons.Folder, contentDescription = null) },
            colors = drawerItemColors,
            shape = drawerItemShape,
          )
          "ANALYZE" -> NavigationDrawerItem(
            label = { Text(strings.drawerAnalyze.uppercase(), style = MaterialTheme.typography.labelLarge) },
            selected = activeRouteOrTab == NexoRoutes.ANALYZE,
            onClick = { onNavigateTab(NexoRoutes.ANALYZE) },
            icon = { Icon(NexoIcons.Analytics, contentDescription = null) },
            colors = drawerItemColors,
            shape = drawerItemShape,
          )
          "SENDER" -> NavigationDrawerItem(
            label = { Text(strings.drawerSender.uppercase(), style = MaterialTheme.typography.labelLarge) },
            selected = activeRouteOrTab == NexoRoutes.SENDER,
            onClick = { onNavigateRoute(NexoRoutes.SENDER) },
            icon = { Icon(NexoIcons.Send, contentDescription = null) },
            colors = drawerItemColors,
            shape = drawerItemShape,
          )
          "DRIVE" -> NavigationDrawerItem(
            label = { Text(strings.drawerDrive.uppercase(), style = MaterialTheme.typography.labelLarge) },
            selected = activeRouteOrTab == NexoRoutes.DRIVE,
            onClick = { onNavigateRoute(NexoRoutes.DRIVE) },
            icon = { Icon(NexoIcons.Cloud, contentDescription = null) },
            colors = drawerItemColors,
            shape = drawerItemShape,
          )
          "GOOGLE_PHOTOS" -> NavigationDrawerItem(
            label = { Text(strings.drawerGooglePhotos.uppercase(), style = MaterialTheme.typography.labelLarge) },
            selected = activeRouteOrTab == NexoRoutes.GOOGLE_PHOTOS,
            onClick = { onNavigateRoute(NexoRoutes.GOOGLE_PHOTOS) },
            icon = { Icon(NexoIcons.PhotoLibrary, contentDescription = null) },
            colors = drawerItemColors,
            shape = drawerItemShape,
            modifier = Modifier.testTag("drawer_google_photos_item"),
          )
          "EXTERNAL_CLOUDS" -> NavigationDrawerItem(
            label = { Text(strings.drawerExternalClouds.uppercase(), style = MaterialTheme.typography.labelLarge) },
            selected = activeRouteOrTab == NexoRoutes.EXTERNAL_CLOUDS,
            onClick = { onNavigateRoute(NexoRoutes.EXTERNAL_CLOUDS) },
            icon = { Icon(NexoIcons.Cloud, contentDescription = null) },
            colors = drawerItemColors,
            shape = drawerItemShape,
            modifier = Modifier.testTag("drawer_external_clouds_item"),
          )
          "VIEW_ON_PC" -> NavigationDrawerItem(
            label = { Text(strings.drawerViewOnPc.uppercase(), style = MaterialTheme.typography.labelLarge) },
            selected = activeRouteOrTab == NexoRoutes.VIEW_ON_PC,
            onClick = { onNavigateRoute(NexoRoutes.VIEW_ON_PC) },
            icon = { Icon(NexoIcons.Computer, contentDescription = null) },
            colors = drawerItemColors,
            shape = drawerItemShape,
          )
          "VAULT" -> NavigationDrawerItem(
            label = { Text(strings.drawerVault.uppercase(), style = MaterialTheme.typography.labelLarge) },
            selected = activeRouteOrTab == NexoRoutes.VAULT,
            onClick = { onNavigateRoute(NexoRoutes.VAULT) },
            icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
            colors = drawerItemColors,
            shape = drawerItemShape,
          )
          "RECYCLE_BIN" -> NavigationDrawerItem(
            label = { Text(strings.drawerRecycleBin.uppercase(), style = MaterialTheme.typography.labelLarge) },
            selected = activeRouteOrTab == NexoRoutes.RECYCLE_BIN,
            onClick = { onNavigateRoute(NexoRoutes.RECYCLE_BIN) },
            icon = { Icon(NexoIcons.DeleteOutline, contentDescription = null) },
            colors = drawerItemColors,
            shape = drawerItemShape,
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outline)
      Spacer(modifier = Modifier.height(6.dp))

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = NexoIcons.Palette,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = strings.drawerTheme.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        ThemeModeSelector(
          selectedMode = themeMode,
          onSelectMode = onSelectThemeMode,
          tagPrefix = "drawer_theme",
        )
      }

      Spacer(modifier = Modifier.height(6.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outline)
      Spacer(modifier = Modifier.height(4.dp))

      NavigationDrawerItem(
        label = { Text(strings.drawerSettings.uppercase(), style = MaterialTheme.typography.labelLarge) },
        selected = activeRouteOrTab == NexoRoutes.SETTINGS,
        onClick = { onNavigateRoute(NexoRoutes.SETTINGS) },
        icon = { Icon(NexoIcons.Tune, contentDescription = null) },
        colors = drawerItemColors,
        shape = drawerItemShape,
        modifier = Modifier.testTag("drawer_settings_item"),
      )

      Spacer(modifier = Modifier.height(4.dp))
      val activeIsEnglish = strings.isEnglish
      val langOuterShape = RoundedCornerShape(6.dp)
      val langInnerShape = RoundedCornerShape(4.dp)
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 6.dp),
        shape = langOuterShape,
        color = NexoTheme.semantic.tacticalSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = NexoIcons.Language,
              contentDescription = strings.drawerLanguage,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.width(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = strings.drawerLanguage.uppercase(),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          Row(
            modifier = Modifier
              .clip(langInnerShape)
              .background(MaterialTheme.colorScheme.background)
              .border(1.dp, MaterialTheme.colorScheme.outline, langInnerShape)
              .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            val esSelected = appLanguage == AppLanguageOption.ES || (appLanguage == AppLanguageOption.AUTO && !activeIsEnglish)
            val enSelected = appLanguage == AppLanguageOption.EN || (appLanguage == AppLanguageOption.AUTO && activeIsEnglish)
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(if (esSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable { onSelectAppLanguage(AppLanguageOption.ES) }
                .padding(horizontal = 12.dp, vertical = 5.dp)
                .testTag("drawer_lang_es"),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "ES",
                style = MonoTnumStyle,
                fontWeight = FontWeight.Bold,
                color = if (esSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )
            }
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(if (enSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable { onSelectAppLanguage(AppLanguageOption.EN) }
                .padding(horizontal = 12.dp, vertical = 5.dp)
                .testTag("drawer_lang_en"),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "EN",
                style = MonoTnumStyle,
                fontWeight = FontWeight.Bold,
                color = if (enSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )
            }
          }
        }
      }
        val drawerCtx = LocalContext.current
        Spacer(modifier = Modifier.height(10.dp))
        val isEsDrawer = strings.languageTitle.contains("Idioma", ignoreCase = true)
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
        Spacer(modifier = Modifier.height(12.dp))
}
  }
}
