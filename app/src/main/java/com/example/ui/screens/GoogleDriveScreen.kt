package com.example.ui.screens

import com.example.NexoNavBridge
import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.AutoBackupRuleEntity
import com.example.data.model.AutoBackupFrequency
import com.example.data.model.DriveFileItem
import com.example.data.model.GooglePhotoMediaItem
import com.example.ui.components.BichromaticCard
import com.example.ui.components.EsViewSortDialog
import com.example.ui.components.FolderPickerDialog
import com.example.ui.components.IconContainerBadge
import com.example.ui.components.NexoIcons
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme
import com.example.ui.viewmodel.DriveUiState
import com.example.ui.viewmodel.GooglePhotosUiState
import com.example.util.FormatUtils

@Composable
fun GoogleDriveScreen(
  driveState: DriveUiState,
  photosState: GooglePhotosUiState = GooglePhotosUiState(),
  autoBackupRules: List<AutoBackupRuleEntity>,
  defaultLocalBackupPath: String,
  availableVolumes: List<Pair<String, String>> = listOf("Almacenamiento interno" to "/storage/emulated/0"),
  initialCloudTab: Int = 0,
  onBack: () -> Unit,
  onConnectDrive: (Activity) -> Unit,
  onSilentRefreshGoogle: () -> Unit = {},
  onConsentIntentResult: (Intent?) -> Unit,
  onConsumePendingConsent: () -> Unit,
  onDisconnect: () -> Unit,
  onOpenFolder: (DriveFileItem) -> Unit,
  onClickBreadcrumb: (String) -> Unit = {},
  onNavigateUpDrive: () -> Boolean,
  onCreateFolder: (String) -> Unit,
  onPickAndUploadFile: () -> Unit,
  onDownloadFile: (DriveFileItem) -> Unit,
  onDownloadSelectedFiles: (List<DriveFileItem>) -> Unit = {},
  onDeleteFile: (String) -> Unit,
  onDeleteSelectedFiles: (Set<String>) -> Unit = {},
  onAddAutoBackupRule: (String, Boolean, String) -> Unit,
  onToggleAutoBackupRule: (AutoBackupRuleEntity, Boolean) -> Unit,
  onDeleteAutoBackupRule: (Long) -> Unit,
  onRunAutoBackupNow: (Set<Long>) -> Unit,
  onConnectGooglePhotos: (Activity) -> Unit = {},
  onPhotosConsentIntentResult: (Intent?) -> Unit = {},
  onConsumePendingPhotosConsent: () -> Unit = {},
  onDisconnectGooglePhotos: () -> Unit = {},
  onStartPhotosPickerSession: () -> Unit = {},
  onCheckPhotosPickerSelection: () -> Unit = {},
  onClearPhotosSession: () -> Unit = {},
  onEnsurePhotoCached: (GooglePhotoMediaItem) -> Unit = {},
  onSavePickedPhotoToDevice: (GooglePhotoMediaItem) -> Unit = {},
  onEditPickedPhotoInNexo: (GooglePhotoMediaItem) -> Unit = {},
) {
  val context = LocalContext.current
  val activity = context as? Activity
  val strings = LocalStrings.current
  var activeCloudTab by remember(initialCloudTab) { mutableIntStateOf(initialCloudTab) }
  var showCreateFolderDialog by remember { mutableStateOf(false) }
  var showAddRuleDialog by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var showSearchBar by remember { mutableStateOf(false) }
  var gridView by remember { mutableStateOf(false) }
  var showEsViewDialog by remember { mutableStateOf(false) }
  var esViewMode by remember { mutableStateOf("MEDIUM_LIST") }
  var esSortBy by remember { mutableStateOf("NAME") }
  var esSortAsc by remember { mutableStateOf(true) }
  var selectedDriveIds by remember(driveState.currentFolderId, driveState.files) { mutableStateOf(setOf<String>()) }
  var selectedRuleIds by remember(autoBackupRules) {
    mutableStateOf(autoBackupRules.filter { it.enabled }.map { it.id }.toSet())
  }

  LaunchedEffect(driveState.accountEmail, driveState.accessToken) {
    if (!driveState.accountEmail.isNullOrBlank() && driveState.accessToken.isNullOrBlank()) {
      onSilentRefreshGoogle()
    }
  }

  val consentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartIntentSenderForResult(),
  ) { result ->
    onConsentIntentResult(result.data)
  }

  LaunchedEffect(driveState.pendingConsentIntent) {
    val pending = driveState.pendingConsentIntent ?: return@LaunchedEffect
    onConsumePendingConsent()
    consentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
  }

  BackHandler(enabled = !NexoNavBridge.isInMainPager || NexoNavBridge.activePagerTabId == "DRIVE") {
    if (activeCloudTab == 0 && driveState.isAuthorized && onNavigateUpDrive()) {
      // Navigated up inside Drive folder tree
    } else {
      onBack()
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    bottomBar = {
      if (activeCloudTab == 0) {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CloudBottomAction(Icons.Rounded.Add, if (strings.isEnglish) "+ NEW" else "+ NUEVO") {
              if (driveState.isAuthorized) showCreateFolderDialog = true else activity?.let(onConnectDrive)
            }
            CloudBottomAction(NexoIcons.Search, strings.search) {
              showSearchBar = !showSearchBar
              if (!showSearchBar) searchQuery = ""
            }
            CloudBottomAction(NexoIcons.Refresh, strings.refresh) {
              if (driveState.isAuthorized) {
                val cur = driveState.folderStack.lastOrNull()?.first ?: "root"
                onOpenFolder(DriveFileItem(cur, driveState.folderStack.lastOrNull()?.second ?: strings.driveRootName, "", 0L, "", true))
              }
            }
            CloudBottomAction(if (gridView) NexoIcons.ViewList else NexoIcons.GridView, if (strings.isEnglish) "VIEW" else "VISTA") {
              showEsViewDialog = true
            }
            CloudBottomAction(NexoIcons.MoreHoriz, if (strings.isEnglish) "BACKUP" else "REGLAS") {
              showAddRuleDialog = true
            }
          }
        }
      }
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(MaterialTheme.colorScheme.background),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(onClick = {
            if (activeCloudTab == 0 && driveState.isAuthorized && onNavigateUpDrive()) {
              // Handled
            } else {
              onBack()
            }
          }) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back, tint = MaterialTheme.colorScheme.primary)
          }
          Spacer(modifier = Modifier.width(6.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = (if (activeCloudTab == 0) strings.driveTitle else strings.googlePhotosTitle).uppercase(),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = if (activeCloudTab == 0) {
                driveState.accountEmail ?: "GOOGLE DRIVE API V3 · AUTO-BACKUP"
              } else {
                photosState.accountEmail ?: strings.googlePhotosSubtitle
              },
              style = MonoTnumStyle,
              color = if ((activeCloudTab == 0 && driveState.isAuthorized) || (activeCloudTab == 1 && photosState.isAuthorized)) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
            )
          }
          if (activeCloudTab == 0 && driveState.isAuthorized) {
            IconButton(onClick = onDisconnect) {
              Icon(NexoIcons.Logout, contentDescription = strings.disconnectBtn, tint = NexoTheme.semantic.evaRed)
            }
          } else if (activeCloudTab == 1 && photosState.isAuthorized) {
            IconButton(onClick = onDisconnectGooglePhotos) {
              Icon(NexoIcons.Logout, contentDescription = strings.disconnectBtn, tint = NexoTheme.semantic.evaRed)
            }
          }
        }

        // Dedicated Cloud Submenu Switcher: Google Drive | Google Photos
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(4.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          val cloudTabs = listOf(
            strings.cloudTabDrive to NexoIcons.Cloud,
            strings.cloudTabPhotos to NexoIcons.PhotoLibrary,
          )
          cloudTabs.forEachIndexed { index, (title, icon) ->
            val selected = activeCloudTab == index
            Row(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable { activeCloudTab = index }
                .padding(vertical = 9.dp, horizontal = 10.dp)
                .testTag("cloud_submenu_tab_$index"),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      if (activeCloudTab == 1) {
        GooglePhotosBodyContent(
          photosState = photosState,
          onConnectPhotos = onConnectGooglePhotos,
          onSilentRefreshPhotos = onSilentRefreshGoogle,
          onConsentIntentResult = onPhotosConsentIntentResult,
          onConsumePendingConsent = onConsumePendingPhotosConsent,
          onDisconnectPhotos = onDisconnectGooglePhotos,
          onStartPickerSession = onStartPhotosPickerSession,
          onCheckPickerSelection = onCheckPhotosPickerSelection,
          onClearSession = onClearPhotosSession,
          onEnsureCached = onEnsurePhotoCached,
          onSaveToDevice = onSavePickedPhotoToDevice,
          onEditInNexo = onEditPickedPhotoInNexo,
          modifier = Modifier.weight(1f),
        )
      } else {
        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (showSearchBar) {
            item {
              OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(if (strings.isEnglish) "Filter Drive files" else "Filtrar archivos en Drive") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
              )
            }
          }

          if (!driveState.isAuthorized) {
            item {
              BichromaticCard(modifier = Modifier.fillMaxWidth(), accentBorder = true) {
                Column(modifier = Modifier.padding(16.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    IconContainerBadge(icon = NexoIcons.Cloud, contentDescription = null, highlighted = true, size = 44.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                      Text(strings.driveNotConnectedHeadline.uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                      Text("Credential Manager + AuthorizationClient (Drive API v3).", style = MonoTnumStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
                  Spacer(modifier = Modifier.height(12.dp))
                  Button(
                    onClick = { activity?.let(onConnectDrive) },
                    enabled = !driveState.isLoading && activity != null,
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(46.dp)
                      .testTag("drive_connect_button"),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.primary,
                      contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(6.dp),
                  ) {
                    if (driveState.isLoading) {
                      CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                      Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(strings.signInGoogleBtn.uppercase(), fontWeight = FontWeight.Bold)
                  }
                  driveState.errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(err, style = MaterialTheme.typography.bodySmall, color = NexoTheme.semantic.evaRed)
                  }
                }
              }
            }
          } else {
            item {
              BichromaticCard(modifier = Modifier.fillMaxWidth(), elevated = true, accentBorder = true) {
                Column(modifier = Modifier.padding(14.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(NexoIcons.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = driveState.accountName ?: "GOOGLE DRIVE",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                      )
                      Text(
                        text = driveState.accountEmail ?: (if (strings.isEnglish) "Connected" else "Conectado"),
                        style = MonoTnumStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                  Spacer(modifier = Modifier.height(8.dp))
                  // Navigable Drive Folder Stack Breadcrumbs
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    driveState.folderStack.forEachIndexed { idx, (folderId, folderName) ->
                      val isCurrent = idx == driveState.folderStack.lastIndex
                      Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                          .clip(RoundedCornerShape(4.dp))
                          .clickable {
                            if (!isCurrent) onClickBreadcrumb(folderId)
                          },
                      ) {
                        Text(
                          text = if (idx == 0) folderName else "› $folderName",
                          style = MonoTnumStyle,
                          color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                      }
                    }
                  }
                  Spacer(modifier = Modifier.height(10.dp))
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                      onClick = onPickAndUploadFile,
                      modifier = Modifier.weight(1f),
                      colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                      ),
                      shape = RoundedCornerShape(6.dp),
                    ) {
                      Icon(NexoIcons.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(strings.uploadSafBtn.uppercase(), fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                      onClick = { showCreateFolderDialog = true },
                      modifier = Modifier.weight(1f),
                      border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                      shape = RoundedCornerShape(6.dp),
                    ) {
                      Icon(NexoIcons.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(strings.newFolderBtn.uppercase(), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    }
                  }
                }
              }
            }
          }

          // Auto-Backup rules section with selective checkboxes
          item {
            BichromaticCard(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(14.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconContainerBadge(icon = NexoIcons.Backup, contentDescription = null, size = 38.dp, highlighted = true)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Text(strings.driveTabBackup.uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                      Text(if (strings.isEnglish) "Sync local folders to Drive" else "Sincroniza carpetas locales con Drive", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
                  TextButton(
                    onClick = { showAddRuleDialog = true },
                    modifier = Modifier.testTag("drive_add_rule_button"),
                  ) {
                    Text(strings.addRuleBtn.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                  }
                }
                if (autoBackupRules.isNotEmpty()) {
                  Spacer(modifier = Modifier.height(8.dp))
                  autoBackupRules.forEach { rule ->
                    val ruleChecked = rule.id in selectedRuleIds
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Checkbox(
                        checked = ruleChecked,
                        onCheckedChange = { checked ->
                          selectedRuleIds = if (checked) selectedRuleIds + rule.id else selectedRuleIds - rule.id
                        },
                        colors = CheckboxDefaults.colors(
                          checkedColor = MaterialTheme.colorScheme.primary,
                          checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                      )
                      Column(modifier = Modifier.weight(1f)) {
                        Text("${rule.localFolderName} → ${rule.driveFolderName}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        val freqLabel = runCatching { strings.backupFreqLabel(AutoBackupFrequency.valueOf(rule.frequency)) }.getOrDefault(rule.frequency)
                        val pendingLabel = if (strings.isEnglish) "Pending sync" else "Pendiente de sincronizar"
                        val filesSuffix = if (strings.isEnglish) "files" else "arch."
                        Text(
                          text = if (rule.lastSyncAt > 0L) "$freqLabel · ${FormatUtils.formatDate(rule.lastSyncAt)} (${rule.lastSyncCount} $filesSuffix)" else "$freqLabel · $pendingLabel",
                          style = MonoTnumStyle,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      Switch(
                        checked = rule.enabled,
                        onCheckedChange = { onToggleAutoBackupRule(rule, it) },
                        colors = SwitchDefaults.colors(
                          checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                          checkedTrackColor = MaterialTheme.colorScheme.primary,
                          uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                          uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                      )
                      IconButton(onClick = { onDeleteAutoBackupRule(rule.id) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = strings.delete, tint = NexoTheme.semantic.evaRed)
                      }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                  }
                  Spacer(modifier = Modifier.height(8.dp))
                  Button(
                    onClick = { onRunAutoBackupNow(selectedRuleIds) },
                    enabled = driveState.isAuthorized && !driveState.isSyncingBackup,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.primary,
                      contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(6.dp),
                  ) {
                    if (driveState.isSyncingBackup) {
                      CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                      Spacer(modifier = Modifier.width(8.dp))
                    } else {
                      Icon(NexoIcons.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                      text = if (selectedRuleIds.isNotEmpty() && selectedRuleIds.size < autoBackupRules.size) {
                        "${strings.backupNowBtn.uppercase()} (${selectedRuleIds.size})"
                      } else {
                        strings.backupNowBtn.uppercase()
                      },
                      fontWeight = FontWeight.Bold,
                    )
                  }
                }
              }
            }
          }

          if (driveState.isAuthorized) {
            val visibleDriveFiles = if (searchQuery.isBlank()) {
              driveState.files
            } else {
              driveState.files.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
            if (selectedDriveIds.isNotEmpty()) {
              item {
                val chosenFiles = driveState.files.filter { it.id in selectedDriveIds }
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = NexoTheme.semantic.tacticalSurface,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Text(
                      text = strings.driveSelectedFilesLabel(selectedDriveIds.size).uppercase(),
                      style = MonoTnumStyle,
                      color = MaterialTheme.colorScheme.primary,
                      fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                      if (chosenFiles.any { !it.isFolder }) {
                        IconButton(onClick = {
                          onDownloadSelectedFiles(chosenFiles)
                          selectedDriveIds = emptySet()
                        }) {
                          Icon(NexoIcons.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                      }
                      IconButton(onClick = {
                        onDeleteSelectedFiles(selectedDriveIds)
                        selectedDriveIds = emptySet()
                      }) {
                        Icon(Icons.Rounded.Delete, contentDescription = strings.delete, tint = NexoTheme.semantic.evaRed)
                      }
                      TextButton(onClick = { selectedDriveIds = emptySet() }) {
                        Text(strings.clear.uppercase(), style = MaterialTheme.typography.labelSmall)
                      }
                    }
                  }
                }
              }
            }
            if (driveState.isLoading) {
              item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                  CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
              }
            } else if (visibleDriveFiles.isEmpty()) {
              item {
                BichromaticCard(modifier = Modifier.fillMaxWidth()) {
                  Text(strings.driveEmptyFolder, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            } else {
              items(
                visibleDriveFiles.sortedWith(
                  compareByDescending<DriveFileItem> { it.isFolder }
                    .thenComparator { a, b ->
                      val cmp = when (esSortBy) {
                        "SIZE" -> a.sizeBytes.compareTo(b.sizeBytes)
                        "TYPE" -> a.name.substringAfterLast('.', "").compareTo(b.name.substringAfterLast('.', ""), ignoreCase = true)
                        else -> a.name.compareTo(b.name, ignoreCase = true)
                      }
                      if (esSortAsc) cmp else -cmp
                    },
                ),
                key = { it.id },
              ) { file ->
                val isChecked = file.id in selectedDriveIds
                BichromaticCard(
                  modifier = Modifier.fillMaxWidth(),
                  accentBorder = isChecked,
                  onClick = { if (file.isFolder) onOpenFolder(file) else onDownloadFile(file) },
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(
                        horizontal = if (esViewMode.startsWith("SMALL_")) 8.dp else 10.dp,
                        vertical = if (esViewMode.startsWith("SMALL_")) 4.dp else if (esViewMode.startsWith("LARGE_")) 14.dp else 8.dp,
                      ),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Checkbox(
                      checked = isChecked,
                      onCheckedChange = { checked ->
                        selectedDriveIds = if (checked) selectedDriveIds + file.id else selectedDriveIds - file.id
                      },
                      colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                      ),
                    )
                    IconContainerBadge(
                      icon = if (file.isFolder) NexoIcons.Folder else NexoIcons.InsertDriveFile,
                      contentDescription = null,
                      highlighted = file.isFolder,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(file.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                      Text(
                        text = if (file.isFolder) (if (strings.isEnglish) "Drive folder" else "Carpeta de Drive") else FormatUtils.formatBytes(file.sizeBytes),
                        style = MonoTnumStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    if (!file.isFolder) {
                      IconButton(onClick = { onDownloadFile(file) }) {
                        Icon(NexoIcons.CloudDownload, contentDescription = if (strings.isEnglish) "Download" else "Descargar", tint = MaterialTheme.colorScheme.primary)
                      }
                    }
                    IconButton(onClick = { onDeleteFile(file.id) }) {
                      Icon(Icons.Rounded.Delete, contentDescription = strings.delete, tint = NexoTheme.semantic.evaRed)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (showCreateFolderDialog) {
    TextInputDialog(
      title = strings.newDriveFolderTitle,
      label = strings.propName,
      confirmText = strings.create,
      onConfirm = {
        showCreateFolderDialog = false
        onCreateFolder(it)
      },
      onDismiss = { showCreateFolderDialog = false },
    )
  }

  if (showAddRuleDialog) {
    AddAutoBackupRuleModal(
      initialPath = defaultLocalBackupPath,
      availableVolumes = availableVolumes,
      onSaveRule = { path, wifiOnly, freq ->
        showAddRuleDialog = false
        onAddAutoBackupRule(path, wifiOnly, freq.name)
      },
      onDismiss = { showAddRuleDialog = false },
    )
  }

  if (showEsViewDialog) {
    EsViewSortDialog(
      currentMode = esViewMode,
      sortBy = esSortBy,
      sortAsc = esSortAsc,
      onSelectMode = {
        esViewMode = it
        gridView = it.endsWith("_ICON")
        showEsViewDialog = false
      },
      onSelectSort = { by, asc ->
        esSortBy = by
        esSortAsc = asc
        showEsViewDialog = false
      },
      onDismiss = { showEsViewDialog = false },
    )
  }
}

@Composable
private fun AddAutoBackupRuleModal(
  initialPath: String,
  availableVolumes: List<Pair<String, String>>,
  onSaveRule: (String, Boolean, AutoBackupFrequency) -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  var selectedPath by remember { mutableStateOf(initialPath) }
  var showFolderPicker by remember { mutableStateOf(false) }
  var wifiOnly by remember { mutableStateOf(true) }
  var frequency by remember { mutableStateOf(AutoBackupFrequency.WIFI_AUTO) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NexoTheme.semantic.modalSurface,
    shape = RoundedCornerShape(10.dp),
    title = { Text(strings.pickFolderHint.uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(strings.tapToChooseFolder, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NexoTheme.semantic.tacticalSurface)
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
            .clickable { showFolderPicker = true }
            .padding(12.dp)
            .testTag("rule_folder_picker_trigger"),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconContainerBadge(icon = NexoIcons.FolderOpen, contentDescription = null, size = 36.dp, highlighted = true)
          Spacer(modifier = Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(selectedPath.substringAfterLast('/').ifBlank { selectedPath }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(selectedPath, style = MonoTnumStyle, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          Icon(NexoIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(strings.frequencyLabel.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AutoBackupFrequency.entries.forEach { option ->
              FilterChip(
                selected = frequency == option,
                onClick = { frequency = option },
                label = { Text(strings.backupFreqLabel(option), style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(4.dp),
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primary,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(strings.wifiOnlyLabel, style = MaterialTheme.typography.bodyMedium)
          Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSaveRule(selectedPath, wifiOnly, frequency) },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(6.dp),
      ) {
        Text(strings.saveRuleBtn.uppercase(), fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(strings.cancel.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    },
  )

  if (showFolderPicker) {
    FolderPickerDialog(
      initialPath = selectedPath,
      availableVolumes = availableVolumes,
      title = strings.pickFolderHint,
      onSelectFolder = { picked ->
        selectedPath = picked
        showFolderPicker = false
      },
      onDismiss = { showFolderPicker = false },
    )
  }
}

@Composable
private fun CloudBottomAction(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onClick: () -> Unit,
) {
  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(6.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
  }
}
