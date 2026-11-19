package com.example.ui.screens

import androidx.compose.material3.Switch
import android.widget.Toast
import androidx.compose.ui.unit.sp
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.NexoNotificationPrefs
import com.example.NexoCrashLibraryDialog
import com.example.NexoVaultSecurityStore
import com.example.NexoMasterPinChangeDialog
import com.example.NexoAuthenticatorSetupDialog
import androidx.compose.foundation.layout.Box
import com.example.NexoNavBridge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.ui.components.*

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.NexoFilesApp
import com.example.data.model.AllSettingsState
import com.example.data.model.AppLanguageOption
import com.example.data.model.GestureBinding
import com.example.data.model.LayoutViewMode
import com.example.data.model.SearchEngineMode
import com.example.data.model.SortBy
import com.example.data.model.ThemeMode
import com.example.data.model.VaultLockMethod
import com.example.data.prefs.BottomBarPreferences
import com.example.security.BiometricAuthRequest
import com.example.security.BiometricAuthResult
import com.example.security.BiometricSecurityManager
import com.example.ui.components.BichromaticCard
import com.example.ui.components.FolderPickerDialog
import com.example.ui.components.IconContainerBadge
import com.example.ui.components.NexoIcons
import com.example.ui.components.ThemeModeSelector
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoFilesTheme
import com.example.ui.theme.NexoTheme

@Composable
fun SettingsScreen(
  themeMode: ThemeMode,
  showHiddenFiles: Boolean,
  sortBy: SortBy,
  vaultBiometricEnabled: Boolean,
  hasAllFilesAccess: Boolean,
  webServerPort: Int,
  allSettings: AllSettingsState = AllSettingsState(themeMode = themeMode, showHiddenFiles = showHiddenFiles, sortBy = sortBy, vaultBiometricEnabled = vaultBiometricEnabled),
  availableVolumes: List<Pair<String, String>> = listOf("Almacenamiento interno" to "/storage/emulated/0"),
  bottomBarTabs: List<String> = BottomBarPreferences.DEFAULT_TABS,
  onSelectThemeMode: (ThemeMode) -> Unit,
  onToggleHiddenFiles: (Boolean) -> Unit,
  onSelectSortBy: (SortBy) -> Unit,
  onToggleVaultBiometric: (Boolean) -> Unit,
  onGrantStorageAccess: () -> Unit,
  onOpenViewOnPc: () -> Unit,
  onUpdateBooleanSetting: (String, Boolean) -> Unit = { _, _ -> },
  onUpdateStringSetting: (String, String) -> Unit = { _, _ -> },
  onSelectLanguage: (AppLanguageOption) -> Unit = {},
  onSetHomeDirectory: (String) -> Unit = {},
  onSetBluetoothDir: (String) -> Unit = {},
  onSetDownloadDir: (String) -> Unit = {},
  onSetSearchEngineMode: (SearchEngineMode) -> Unit = {},
  onSetLayoutViewMode: (LayoutViewMode) -> Unit = {},
  onSetDrawerConfig: (List<String>, Set<String>) -> Unit = { _, _ -> },
  onSetToolbarActions: (List<String>) -> Unit = {},
  onToggleBottomBarTab: (String) -> Boolean = { true },
  onMoveBottomBarTab: (String, Int) -> Unit = { _, _ -> },
  onResetBottomBarTabs: () -> Unit = {},
  onSetAppBackupDirectory: (String) -> Unit = {},
  onSetInitialWindows: (List<String>) -> Unit = {},
  onSetMasterPin: (String) -> Unit = {},
  onSetVaultSecurityConfig: (VaultLockMethod, String?) -> Unit = { _, _ -> },
  onVerifyVaultSecret: (String) -> Boolean = { true },
  onSaveGestureBinding: (GestureBinding) -> Unit = {},
  onDeleteGestureBinding: (String) -> Unit = {},
  onClearAppCacheNow: () -> Unit = {},
  onClearRecentHistoryNow: () -> Unit = {},
  onResetDefaults: () -> Unit = {},
  onExportSettingsJson: () -> Unit = {},
  onImportSettingsJson: () -> Unit = {},
  securityManager: BiometricSecurityManager? = null,
  onVerifyMasterPin: (String) -> Boolean = { true },
  defaultAppMappingsCount: Int = 0,
  onResetDefaultApps: () -> Unit = {},
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val strings = LocalStrings.current
  val fragmentActivity = context as? FragmentActivity
  val resolvedSecurity = remember(context, securityManager) {
    securityManager
      ?: (context.applicationContext as? NexoFilesApp)?.container?.security
      ?: BiometricSecurityManager(context.applicationContext)
  }
  val biometricAvailability = remember(resolvedSecurity) { resolvedSecurity.checkAvailability() }

  var expandedPanel by remember { mutableStateOf("") }
  BackHandler { if (expandedPanel.isNotEmpty()) expandedPanel = "" else onBack() }
  var folderPickerTarget by remember { mutableStateOf<String?>(null) }
  var showPinDialog by remember { mutableStateOf(false) }
  var showTotpSetupDialog by remember { mutableStateOf(false) }
  var showGesturesDialog by remember { mutableStateOf(false) }
  var showLanguageModal by remember { mutableStateOf(false) }
  var showBottomBarDialog by remember { mutableStateOf(false) }
  var showCrashDiagnosticsDialog by remember { mutableStateOf(false) }
  var showNotificationsDialog by remember { mutableStateOf(false) }
  var notifPermGranted by remember {
    mutableStateOf(
      Build.VERSION.SDK_INT < 33 ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    )
  }
  val settingsNotifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    notifPermGranted = granted
    Toast.makeText(context, if (granted) "Notificaciones activadas" else "Permiso denegado", Toast.LENGTH_SHORT).show()
  }
  var showVaultVerifyBeforeChange by remember { mutableStateOf(false) }
  var showVaultConfigModal by remember { mutableStateOf(false) }
  var updateCheckMessage by remember { mutableStateOf<String?>(null) }
  var securityAuthNotice by remember { mutableStateOf<String?>(null) }
  var pendingSensitiveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
  var formatsDraft by remember(allSettings.floatingLoggerFormats) { mutableStateOf(allSettings.floatingLoggerFormats) }

  fun runProtectedAction(title: String, subtitle: String, action: () -> Unit) {
    val hasPin = allSettings.masterPinHash.isNotBlank()
    resolvedSecurity.authorizeSensitiveOperation(
      activity = fragmentActivity,
      biometricEnabled = vaultBiometricEnabled,
      request = BiometricAuthRequest(
        title = title,
        subtitle = subtitle,
        allowDeviceCredential = true,
      ),
      hasMasterPin = hasPin,
      onRequestMasterPinFallback = { pendingSensitiveAction = action },
      onAuthorized = {
        securityAuthNotice = null
        action()
      },
      onDeniedOrError = { reason ->
        securityAuthNotice = reason
      },
    )
  }

  /**
   * Exclusively guards changing the Vault lock method behind verification of the currently active
   * Vault credential (BiometricPrompt if BIOMETRIC, or VaultVerifyCurrentDialog if PIN/PATTERN/PASSWORD).
   */
  fun initiateVaultSecurityChange() {
    val activeMethod = allSettings.vaultLockMethod
    if (activeMethod == VaultLockMethod.BIOMETRIC) {
      val activity = fragmentActivity
      if (activity == null) {
        showVaultConfigModal = true
        return
      }
      resolvedSecurity.authenticate(
        activity = activity,
        request = BiometricAuthRequest(
          title = strings.vaultVerifyCurrentTitle,
          subtitle = strings.vaultVerifyCurrentDesc(strings.vaultMethodLabel(activeMethod)),
          allowDeviceCredential = true,
          negativeButtonText = strings.cancel,
        ),
      ) { res ->
        when (res) {
          is BiometricAuthResult.Success -> {
            securityAuthNotice = null
            showVaultConfigModal = true
          }
          is BiometricAuthResult.Unavailable -> {
            securityAuthNotice = null
            showVaultConfigModal = true
          }
          is BiometricAuthResult.Cancelled -> {
            securityAuthNotice = strings.cancel
          }
          is BiometricAuthResult.Error -> {
            securityAuthNotice = res.message
          }
        }
      }
    } else if (allSettings.vaultSecretHash.isBlank()) {
      showVaultConfigModal = true
    } else {
      showVaultVerifyBeforeChange = true
    }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(6.dp))
        Column {
          Text(strings.settingsTitle.uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
          Text(strings.settingsSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }

    item { NexoEvaThemeSelectorCard() }
        // BLOQUE 1: AJUSTES GENERALES
    item { SettingsBlockHeader(strings.block1Title) }

    // 1.1 Pantalla
    item {
      SettingsAccordionCard(
        title = strings.block1Display,
        subtitle = "${strings.themeLabel(themeMode)} · ${strings.langLabel(allSettings.appLanguage)}",
        icon = NexoIcons.Palette,
        expanded = expandedPanel == "DISPLAY",
        onToggle = { expandedPanel = if (expandedPanel == "DISPLAY") "" else "DISPLAY" },
      ) {
        ThemeModeSelector(selectedMode = themeMode, onSelectMode = onSelectThemeMode, tagPrefix = "settings_theme")
        Spacer(modifier = Modifier.height(10.dp))
        val langBoxShape = RoundedCornerShape(6.dp)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(langBoxShape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f), langBoxShape)
            .clickable { showLanguageModal = true }
            .padding(12.dp)
            .testTag("settings_language_button"),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(NexoIcons.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
          Spacer(modifier = Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(strings.languageTitle.uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(strings.langLabel(allSettings.appLanguage), style = MonoTnumStyle, color = MaterialTheme.colorScheme.primary)
          }
          Icon(NexoIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SettingsSwitchRow(strings.showHiddenAllTitle, strings.showHiddenAllDesc, showHiddenFiles, onToggleHiddenFiles)
        SettingsSwitchRow(strings.showThumbnails, null, allSettings.showThumbnails) { onUpdateBooleanSetting("show_thumbnails", it) }
        SettingsSwitchRow(strings.showOnlyFoldersHistory, null, allSettings.showOnlyFoldersInHistory) { onUpdateBooleanSetting("show_only_folders_history", it) }
        SettingsSwitchRow(strings.showFileTransferStation, null, allSettings.showFileTransferStation) { onUpdateBooleanSetting("show_file_transfer_station", it) }
        SettingsSwitchRow(strings.showNewFilesHome, null, allSettings.showNewFilesOnHome) { onUpdateBooleanSetting("show_new_files_home", it) }
        SettingsSwitchRow(strings.showSdStatusBar, null, allSettings.showSdCardStatusBar) { onUpdateBooleanSetting("show_sd_status_bar", it) }
        SettingsSwitchRow(strings.showAppIconFolders, null, allSettings.showAppIconOnFolders) { onUpdateBooleanSetting("show_app_icon_folders", it) }
        SettingsSwitchRow(strings.showSdSizeHistory, null, allSettings.showSdSizeInHistory) { onUpdateBooleanSetting("show_sd_size_history", it) }
        SettingsSwitchRow(strings.usbConnectPrompt, null, allSettings.usbConnectPrompt) { onUpdateBooleanSetting("usb_connect_prompt", it) }
        SettingsSwitchRow(strings.hideHomeWhatsNew, null, allSettings.hideHomeWhatsNewCard) { onUpdateBooleanSetting("hide_home_whats_new", it) }
        SettingsSwitchRow(strings.showFloatingClipboard, null, allSettings.showFloatingClipboardButton) { onUpdateBooleanSetting("show_floating_clipboard", it) }
        Spacer(modifier = Modifier.height(6.dp))
        Text(strings.defaultSortTitle.uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          SortBy.entries.forEach { option ->
            FilterChip(
              selected = sortBy == option,
              onClick = { onSelectSortBy(option) },
              label = { Text(strings.sortLabel(option).uppercase()) },
              shape = RoundedCornerShape(4.dp),
              colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
            )
          }
        }
      }
    }

    // 1.2 Limpieza
    item {
      SettingsAccordionCard(
        title = strings.block1Cleanup,
        subtitle = strings.configuredDefaultAppsCount(defaultAppMappingsCount),
        icon = NexoIcons.CleaningServices,
        expanded = expandedPanel == "CLEANUP",
        onToggle = { expandedPanel = if (expandedPanel == "CLEANUP") "" else "CLEANUP" },
      ) {
        SettingsSwitchRow(strings.clearHistoryOnExit, null, allSettings.clearHistoryOnExit) { onUpdateBooleanSetting("clear_history_on_exit", it) }
        SettingsSwitchRow(strings.clearCacheOnExit, null, allSettings.clearCacheOnExit) { onUpdateBooleanSetting("clear_cache_on_exit", it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = onClearAppCacheNow,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
          ) {
            Text(strings.clearCacheNow.uppercase(), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          OutlinedButton(
            onClick = onClearRecentHistoryNow,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
          ) {
            Text(strings.clearHistoryNow.uppercase(), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(
          onClick = onResetDefaultApps,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_reset_default_apps_button"),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
          shape = RoundedCornerShape(4.dp),
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            
            
            Text(strings.resetDefaultAppsBtn.uppercase(), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
            Text(strings.configuredDefaultAppsCount(defaultAppMappingsCount), style = MonoTnumStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Button(
          onClick = {
            runProtectedAction(
              title = strings.resetAllConfirmTitle,
              subtitle = strings.resetAllConfirmDesc,
            ) { onResetDefaults() }
          },
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(
            containerColor = NexoTheme.semantic.evaRed,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
          shape = RoundedCornerShape(4.dp),
        ) {
          Text(strings.resetAllSettingsBtn.uppercase(), fontWeight = FontWeight.Bold)
        }
      }
    }

    // 1.3 Directorios
    item {
      SettingsAccordionCard(
        title = strings.block1Directories,
        subtitle = "${strings.homeDirLabel}, Bluetooth, ${strings.downloadDirLabel}",
        icon = NexoIcons.FolderSpecial,
        expanded = expandedPanel == "DIRS",
        onToggle = { expandedPanel = if (expandedPanel == "DIRS") "" else "DIRS" },
      ) {
        FolderSettingRow(strings.homeDirLabel, allSettings.homeDirectory) { folderPickerTarget = "HOME" }
        Spacer(modifier = Modifier.height(6.dp))
        FolderSettingRow(strings.bluetoothDirLabel, allSettings.bluetoothReceiveDir) { folderPickerTarget = "BT" }
        Spacer(modifier = Modifier.height(6.dp))
        FolderSettingRow(strings.downloadDirLabel, allSettings.downloadDirectory) { folderPickerTarget = "DOWNLOAD" }
      }
    }

    // 1.4 Motor de búsqueda
    item {
      SettingsAccordionCard(
        title = strings.block1Search,
        subtitle = strings.searchEngineLabel(allSettings.searchEngineMode),
        icon = NexoIcons.Search,
        expanded = expandedPanel == "SEARCH",
        onToggle = { expandedPanel = if (expandedPanel == "SEARCH") "" else "SEARCH" },
      ) {
        SearchEngineMode.entries.forEach { mode ->
          FilterChip(
            selected = allSettings.searchEngineMode == mode,
            onClick = { onSetSearchEngineMode(mode) },
            label = { Text("${strings.searchEngineLabel(mode)} · ${strings.searchEngineDesc(mode)}") },
            shape = RoundedCornerShape(4.dp),
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
          )
        }
        SettingsSwitchRow(strings.showSearchBarHome, null, allSettings.showSearchBarOnHome) { onUpdateBooleanSetting("show_search_bar_home", it) }
      }
    }

    // 1.5 Notificaciones + Widget flotante
    item {
      SettingsAccordionCard(
        title = strings.block1Notifications,
        subtitle = strings.block1FloatingLogger,
        icon = NexoIcons.Notifications,
        expanded = expandedPanel == "NOTIF",
        onToggle = { expandedPanel = if (expandedPanel == "NOTIF") "" else "NOTIF" },
      ) {
        SettingsSwitchRow(strings.dismissNotifOnFinish, null, allSettings.dismissNotifOnTaskFinish) { onUpdateBooleanSetting("dismiss_notif_on_finish", it) }
        SettingsSwitchRow(strings.notifyApkPermissions, null, allSettings.notifyApkInstallPermissions) { onUpdateBooleanSetting("notify_apk_permissions", it) }
        SettingsSwitchRow(strings.notifyNewFiles, null, allSettings.notifyNewFilesCreated) { onUpdateBooleanSetting("notify_new_files", it) }
        SettingsSwitchRow(strings.remindNewCameraPhotos, null, allSettings.remindNewCameraPhotos) { onUpdateBooleanSetting("remind_new_camera_photos", it) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SettingsSwitchRow(
          title = strings.floatingLoggerEnable,
          subtitle = if (Settings.canDrawOverlays(context)) strings.fullAccessActive else strings.tapToEnableAccess,
          checked = allSettings.floatingLoggerEnabled,
          onCheckedChange = { enable ->
            if (enable && !Settings.canDrawOverlays(context)) {
              context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            }
            onUpdateBooleanSetting("floating_logger_enabled", enable)
          },
        )
        OutlinedTextField(
          value = formatsDraft,
          onValueChange = { formatsDraft = it; onUpdateStringSetting("floating_logger_formats", it) },
          label = { Text(strings.floatingLoggerFormats) },
          singleLine = true,
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    // BLOQUE 2: GESTIÓN DE DISEÑO
    item { SettingsBlockHeader(strings.block2Title) }

    item {
      SettingsAccordionCard(
        title = strings.defaultViewTitle,
        subtitle = "${strings.layoutLabel(allSettings.layoutViewMode)} · ${allSettings.toolbarEnabledActions.size}",
        icon = NexoIcons.ViewAgenda,
        expanded = expandedPanel == "LAYOUT",
        onToggle = { expandedPanel = if (expandedPanel == "LAYOUT") "" else "LAYOUT" },
      ) {
        Text(strings.defaultViewTitle.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          LayoutViewMode.entries.forEach { mode ->
            FilterChip(
              selected = allSettings.layoutViewMode == mode,
              onClick = { onSetLayoutViewMode(mode) },
              label = { Text(strings.layoutLabel(mode).uppercase(), style = MaterialTheme.typography.labelSmall) },
              shape = RoundedCornerShape(4.dp),
              colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
            )
          }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text(strings.drawerSetupBtn.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        DrawerManagementSection(
          orderIds = allSettings.drawerOrderIds,
          hiddenIds = allSettings.drawerHiddenIds,
          onSave = onSetDrawerConfig,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text(strings.toolbarSetupBtn.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        ToolbarCustomizationSection(
          enabledActions = allSettings.toolbarEnabledActions,
          onUpdateActions = onSetToolbarActions,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Button(
          onClick = { showBottomBarDialog = true },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_bottom_bar_btn"),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
          shape = RoundedCornerShape(4.dp),
        ) {
          Icon(NexoIcons.ViewAgenda, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "${strings.bottomBarSetupBtn.uppercase()} (${bottomBarTabs.size}/5)",
            fontWeight = FontWeight.Bold,
          )
        }
        Text(
          text = strings.bottomBarLongPressHint,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // BLOQUE 3: SEGURIDAD Y RESPALDO (Gestión EXCLUSIVA de método de seguridad de la Bóveda)
    item { SettingsBlockHeader(strings.block3Title) }

    item {
      SettingsAccordionCard(
        title = strings.block3Title,
        subtitle = strings.vaultActiveMethodLabel(strings.vaultMethodLabel(allSettings.vaultLockMethod)),
        icon = Icons.Rounded.Lock,
        expanded = expandedPanel == "SECURITY",
        onToggle = {
          if (expandedPanel == "SECURITY") {
            expandedPanel = ""
          } else if (allSettings.masterPinHash.isNotBlank() || NexoVaultSecurityStore.hasLocalPin(context)) {
            pendingSensitiveAction = { expandedPanel = "SECURITY" }
          } else {
            expandedPanel = "SECURITY"
          }
        },
      ) {
        Text(
          text = biometricAvailability.label,
          style = MonoTnumStyle,
          color = MaterialTheme.colorScheme.secondary,
        )
        securityAuthNotice?.let { notice ->
          Text(
            text = notice,
            style = MaterialTheme.typography.labelSmall,
            color = NexoTheme.semantic.evaRed,
          )
        }

        // Exclusive Vault Security Configuration Panel
        val vaultCfgShape = RoundedCornerShape(6.dp)
        Surface(
          shape = vaultCfgShape,
          color = MaterialTheme.colorScheme.background,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = strings.vaultSecurityConfigTitle.uppercase(),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = strings.vaultSecurityConfigDesc,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = strings.vaultActiveMethodLabel(strings.vaultMethodLabel(allSettings.vaultLockMethod)),
              style = MonoTnumStyle,
              color = NexoTheme.semantic.evaOrange,
              fontWeight = FontWeight.Bold,
            )
            Button(
              onClick = { initiateVaultSecurityChange() },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_vault_security_btn"),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
              shape = RoundedCornerShape(4.dp),
            ) {
              Text(strings.vaultChangeMethodBtn.uppercase(), fontWeight = FontWeight.Bold)
            }
          }
        }

        SettingsSwitchRow(
          title = strings.biometricAccessTitle,
          subtitle = strings.biometricAccessDesc,
          checked = vaultBiometricEnabled,
          onCheckedChange = { enabled ->
            if (!enabled) {
              runProtectedAction(
                title = strings.biometricAccessTitle,
                subtitle = strings.biometricAccessDesc,
              ) { onToggleVaultBiometric(false) }
            } else {
              onToggleVaultBiometric(true)
            }
          },
        )
        OutlinedButton(
          onClick = {
            runProtectedAction(
              title = strings.masterPinDialogTitle,
              subtitle = strings.masterPinDialogDesc,
            ) { showPinDialog = true }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_master_pin_button"),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
          shape = RoundedCornerShape(4.dp),
        ) {
          Text(
            text = (if (allSettings.masterPinHash.isNotBlank()) "${strings.masterPinTitle} · ${strings.configureBtn}" else strings.masterPinDialogTitle).uppercase(),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
          )
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(
          onClick = { showTotpSetupDialog = true },
          modifier = Modifier.fillMaxWidth(),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
          shape = RoundedCornerShape(4.dp),
        ) {
          Text(
            text = if (NexoVaultSecurityStore.isTotpVerified(context)) "✓ GOOGLE AUTHENTICATOR (2FA ACTIVO)" else "🔐 VINCULAR GOOGLE AUTHENTICATOR (RECUPERAR PIN)",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
          )
        }
        Text(
          text = "Passphrase respaldo Drive: sincronizado automáticamente con tu Master PIN.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsSwitchRow(strings.storagePermTitle, if (hasAllFilesAccess) strings.fullAccessActive else strings.tapToEnableAccess, hasAllFilesAccess) { onGrantStorageAccess() }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SettingsSwitchRow(strings.remoteSyncTitle, strings.remoteSyncDesc, allSettings.autoSyncRemoteEdits) { onUpdateBooleanSetting("auto_sync_remote_edits", it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = {
              runProtectedAction(
                title = strings.exportJsonBtn,
                subtitle = strings.securityBackupDesc,
              ) { onExportSettingsJson() }
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
          ) {
            Text(strings.exportJsonBtn.uppercase())
          }
          OutlinedButton(
            onClick = {
              runProtectedAction(
                title = strings.importJsonBtn,
                subtitle = strings.securityBackupDesc,
              ) { onImportSettingsJson() }
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
          ) {
            Text(strings.importJsonBtn.uppercase())
          }
        }
      }
    }

    // BLOQUE 4: AJUSTES DE HERRAMIENTAS
    item { SettingsBlockHeader(strings.block4Title) }

    item {
      SettingsAccordionCard(
        title = strings.block4Title,
        subtitle = strings.viewOnPcPortLabel(webServerPort),
        icon = NexoIcons.Tune,
        expanded = expandedPanel == "TOOLS_CFG",
        onToggle = { expandedPanel = if (expandedPanel == "TOOLS_CFG") "" else "TOOLS_CFG" },
      ) {
        Text(strings.appManagerTitle.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        SettingsSwitchRow(strings.autoBackupApkTitle, null, allSettings.autoBackupApkBeforeUninstall) { onUpdateBooleanSetting("auto_backup_apk_uninstall", it) }
        FolderSettingRow(strings.apkBackupFolderLabel, allSettings.appBackupDirectory) { folderPickerTarget = "APP_BACKUP" }
        SettingsSwitchRow(strings.cleanResidualFoldersTitle, null, allSettings.cleanResidualAppFolders) { onUpdateBooleanSetting("clean_residual_app_folders", it) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Text(strings.downloaderTitle.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        SettingsSwitchRow(strings.multithreadTransferTitle, null, allSettings.multithreadTransferEnabled) { onUpdateBooleanSetting("multithread_transfer", it) }
        SettingsSwitchRow(strings.disableDownloaderTitle, null, allSettings.disableBuiltInDownloader) { onUpdateBooleanSetting("disable_builtin_downloader", it) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Text(strings.initialWindowsCount(allSettings.initialWindows.size).uppercase(), style = MonoTnumStyle, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        allSettings.initialWindows.forEach { winPath ->
          Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(winPath, modifier = Modifier.weight(1f), style = MonoTnumStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (allSettings.initialWindows.size > 1) {
              IconButton(onClick = { onSetInitialWindows(allSettings.initialWindows - winPath) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = strings.delete, tint = NexoTheme.semantic.evaRed, modifier = Modifier.size(16.dp))
              }
            }
          }
        }
        OutlinedButton(
          onClick = { folderPickerTarget = "ADD_WINDOW" },
          shape = RoundedCornerShape(4.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
        ) {
          Text("+ ${strings.selectFolderTitle.uppercase()}")
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        SettingsSwitchRow(strings.recycleBinSettingTitle, strings.recycleBinDesc, allSettings.recycleBinEnabled) { onUpdateBooleanSetting("recycle_bin_enabled", it) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = { showGesturesDialog = true },
            modifier = Modifier.weight(1f).testTag("settings_open_gestures"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
            shape = RoundedCornerShape(4.dp),
          ) {
            Icon(NexoIcons.Gesture, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("${strings.gesturesSettingTitle.uppercase()} (${allSettings.gestureBindings.size})", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          OutlinedButton(
            onClick = onOpenViewOnPc,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
          ) {
            Text("${strings.toolViewOnPc.uppercase()} ($webServerPort)", style = MonoTnumStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    }

    // BLOQUE 5: SISTEMA
    item { SettingsBlockHeader(strings.block5Title) }

    item {
      SettingsAccordionCard(
        title = strings.aboutNexoTitle,
        subtitle = strings.aboutNexoDesc,
        icon = NexoIcons.InfoOutline,
        expanded = expandedPanel == "SYSTEM",
        onToggle = { expandedPanel = if (expandedPanel == "SYSTEM") "" else "SYSTEM" },
      ) {
        SettingsSwitchRow(strings.autoCheckUpdates, null, allSettings.autoCheckUpdates) { onUpdateBooleanSetting("auto_check_updates", it) }
        OutlinedButton(
          onClick = { NexoOtaUpdater.downloadAndInstallUpdate(context) { msg -> updateCheckMessage = msg } },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(4.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
        ) {
          Icon(NexoIcons.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(strings.autoCheckUpdates.uppercase())
        }
        updateCheckMessage?.let { msg ->
          Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
          OutlinedButton(onClick = { showNotificationsDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("🔔 NOTIFICACIONES DE LA APP", fontWeight = FontWeight.Bold) }
        Button(

          onClick = { showCrashDiagnosticsDialog = true },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_crash_diagnostics_btn"),
          colors = ButtonDefaults.buttonColors(
            containerColor = NexoTheme.semantic.evaOrange,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
          shape = RoundedCornerShape(4.dp),
        ) {
          Icon(NexoIcons.InfoOutline, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(strings.crashDiagTitle.uppercase(), fontWeight = FontWeight.Bold)
        }
        Text(strings.crashDiagSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text(strings.privacyPolicyTitle.uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(strings.privacyPolicyDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(strings.helpFeedbackDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }

  folderPickerTarget?.let { target ->
    val initial = when (target) {
      "HOME" -> allSettings.homeDirectory
      "BT" -> allSettings.bluetoothReceiveDir
      "DOWNLOAD" -> allSettings.downloadDirectory
      "APP_BACKUP" -> allSettings.appBackupDirectory
      else -> allSettings.homeDirectory
    }
    FolderPickerDialog(
      initialPath = initial,
      availableVolumes = availableVolumes,
      title = strings.selectFolderTitle,
      onSelectFolder = { picked ->
        when (target) {
          "HOME" -> onSetHomeDirectory(picked)
          "BT" -> onSetBluetoothDir(picked)
          "DOWNLOAD" -> onSetDownloadDir(picked)
          "APP_BACKUP" -> onSetAppBackupDirectory(picked)
          "ADD_WINDOW" -> if (picked !in allSettings.initialWindows) onSetInitialWindows(allSettings.initialWindows + picked)
        }
        folderPickerTarget = null
      },
      onDismiss = { folderPickerTarget = null },
    )
  }

  if (showLanguageModal) {
    AlertDialog(
      onDismissRequest = { showLanguageModal = false },
      shape = RoundedCornerShape(10.dp),
      containerColor = NexoTheme.semantic.modalSurface,
      title = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(NexoIcons.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text(strings.languageSelectModalTitle.uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          AppLanguageOption.entries.forEach { opt ->
            val selected = allSettings.appLanguage == opt
            val desc = when (opt) {
              AppLanguageOption.AUTO -> strings.languageAutoSystemDesc
              AppLanguageOption.ES -> strings.languageSpanishDesc
              AppLanguageOption.EN -> strings.languageEnglishDesc
            }
            val rowShape = RoundedCornerShape(6.dp)
            Surface(
              shape = rowShape,
              color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background,
              border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
              modifier = Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .clickable {
                  onSelectLanguage(opt)
                  showLanguageModal = false
                }
                .testTag("language_option_${opt.name}"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(
                  selected = selected,
                  onClick = {
                    onSelectLanguage(opt)
                    showLanguageModal = false
                  },
                  colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  ),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                  Text(strings.langLabel(opt), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                  Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showLanguageModal = false }) {
          Text(strings.close.uppercase(), color = MaterialTheme.colorScheme.primary)
        }
      },
    )
  }

  if (showVaultVerifyBeforeChange) {
    VaultVerifyCurrentDialog(
      currentMethod = allSettings.vaultLockMethod,
      onVerifySecret = onVerifyVaultSecret,
      onVerified = {
        showVaultVerifyBeforeChange = false
        showVaultConfigModal = true
      },
      onDismiss = { showVaultVerifyBeforeChange = false },
    )
  }

  if (showVaultConfigModal) {
    VaultSecurityConfigDialog(
      currentMethod = allSettings.vaultLockMethod,
      onSaveConfig = onSetVaultSecurityConfig,
      onDismiss = { showVaultConfigModal = false },
    )
  }

  if (showPinDialog) {
    NexoMasterPinChangeDialog(
      hasExistingPin = allSettings.masterPinHash.isNotBlank() || NexoVaultSecurityStore.hasLocalPin(context),
      linkedEmail = NexoNavBridge.linkedGoogleEmail,
      onVerifyExistingPin = onVerifyMasterPin,
      onSaveNewPin = onSetMasterPin,
      onDismiss = { showPinDialog = false },
    )
  }

  if (showTotpSetupDialog) {
    NexoAuthenticatorSetupDialog(
      accountEmail = NexoNavBridge.linkedGoogleEmail,
      onDismiss = { showTotpSetupDialog = false },
    )
  }

  if (showGesturesDialog) {
    GestureSettingsDialog(
      gesturesEnabled = allSettings.gesturesEnabled,
      bindings = allSettings.gestureBindings,
      onToggleGestures = { onUpdateBooleanSetting("gestures_enabled", it) },
      onSaveGesture = onSaveGestureBinding,
      onDeleteGesture = onDeleteGestureBinding,
      onDismiss = { showGesturesDialog = false },
    )
  }

  pendingSensitiveAction?.let { pending ->
    MasterPinVerifyDialog(
      onVerifyPin = { pin -> NexoVaultSecurityStore.verifyPin(context, pin, onVerifyMasterPin) },
      onVerified = {
        pendingSensitiveAction = null
        securityAuthNotice = null
        pending()
      },
      onDismiss = {
        pendingSensitiveAction = null
        securityAuthNotice = strings.cancel
      },
    )
  }

  if (showBottomBarDialog) {
    BottomBarCustomizationDialog(
      activeTabs = bottomBarTabs,
      onToggleTab = onToggleBottomBarTab,
      onMoveTab = onMoveBottomBarTab,
      onResetDefaults = onResetBottomBarTabs,
      onDismiss = { showBottomBarDialog = false },
    )
  }

  if (showNotificationsDialog) {
    var nVault by remember { mutableStateOf(NexoNotificationPrefs.isEnabled(context, NexoNotificationPrefs.KEY_VAULT_BACKUP)) }
    var nTor by remember { mutableStateOf(NexoNotificationPrefs.isEnabled(context, NexoNotificationPrefs.KEY_TORRENTS)) }
    var nXfer by remember { mutableStateOf(NexoNotificationPrefs.isEnabled(context, NexoNotificationPrefs.KEY_TRANSFERS)) }
    var nOta by remember { mutableStateOf(NexoNotificationPrefs.isEnabled(context, NexoNotificationPrefs.KEY_OTA)) }

    AlertDialog(
      onDismissRequest = { showNotificationsDialog = false },
      title = { Text("🔔 Notificaciones de NexoFiles") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = if (notifPermGranted) "✅ Permiso del sistema activo" else "⚠️ Permiso bloqueado en Android",
                fontWeight = FontWeight.Bold,
                color = if (notifPermGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontSize = 12.sp
              )
              if (!notifPermGranted && Build.VERSION.SDK_INT >= 33) {
                Button(
                  onClick = { settingsNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                  modifier = Modifier.fillMaxWidth()
                ) { Text("Activar notificaciones ahora") }
              }
            }
          }
          listOf(
            Triple("Respaldo de Bóveda (2º plano)", NexoNotificationPrefs.KEY_VAULT_BACKUP, nVault to { v: Boolean -> nVault = v }),
            Triple("Descargas y NexoTorrent", NexoNotificationPrefs.KEY_TORRENTS, nTor to { v: Boolean -> nTor = v }),
            Triple("Transferencias Wi-Fi / LAN", NexoNotificationPrefs.KEY_TRANSFERS, nXfer to { v: Boolean -> nXfer = v }),
            Triple("Actualizaciones OTA", NexoNotificationPrefs.KEY_OTA, nOta to { v: Boolean -> nOta = v }),
          ).forEach { (label, key, statePair) ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f))
              Switch(
                checked = statePair.first,
                onCheckedChange = { v ->
                  statePair.second(v)
                  NexoNotificationPrefs.setEnabled(context, key, v)
                  if (v && !notifPermGranted && Build.VERSION.SDK_INT >= 33) {
                    settingsNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  }
                }
              )
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showNotificationsDialog = false }) { Text("Listo") }
      }
    )
  }

  if (showCrashDiagnosticsDialog) {
    NexoCrashLibraryDialog(
      onDismiss = { showCrashDiagnosticsDialog = false },
    )
  }
}

@Composable
private fun SettingsBlockHeader(title: String) {
  Text(
    text = title.uppercase(),
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    color = NexoTheme.semantic.evaOrange,
    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
  )
}

@Composable
private fun SettingsAccordionCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  expanded: Boolean,
  onToggle: () -> Unit,
  content: @Composable () -> Unit,
) {
  val isVaultRow = title.contains("VAULT", ignoreCase = true) || title.contains("BÓVEDA", ignoreCase = true)
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onToggle)
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .background(
            color = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            shape = RoundedCornerShape(10.dp),
          ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (expanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
      }
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
        )
        if (subtitle.isNotBlank()) {
          Text(
            text = if (isVaultRow && !expanded) "🔒 Master Code · $subtitle" else subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }
      }
      Text(
        text = if (expanded) "⌄" else "›",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp),
      )
    }
    AnimatedVisibility(visible = expanded) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
          .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), RoundedCornerShape(12.dp))
          .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        content()
      }
    }
    HorizontalDivider(
      modifier = Modifier.padding(start = 68.dp, end = 16.dp),
      color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenDarkPreview() {
  NexoFilesTheme(themeMode = ThemeMode.DARK) {
    SettingsScreen(
      themeMode = ThemeMode.DARK,
      showHiddenFiles = true,
      sortBy = SortBy.NAME,
      vaultBiometricEnabled = true,
      hasAllFilesAccess = true,
      webServerPort = 8080,
      onSelectThemeMode = {},
      onToggleHiddenFiles = {},
      onSelectSortBy = {},
      onToggleVaultBiometric = {},
      onGrantStorageAccess = {},
      onOpenViewOnPc = {},
      onBack = {},
    )
  }
}
