package com.example.ui.screens

import com.example.NexoFeatureFlags
import com.example.NexoVaultCloudActionDialog
import com.example.NexoAtFieldVaultGate
import com.example.NexoCloudStatusIcon
import com.example.NexoVaultExpandableFab
import com.example.NexoVaultCloudSyncDialog
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.example.NexoGlobalClipboard
import com.example.NexoClipMode
import com.example.NexoVaultSecurityStore
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.example.NexoVaultExtraStore
import com.example.NexoCloudPlaceholderItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.produceState
import com.example.NexoVaultDriveMirror
import com.example.NexoNavBridge
import com.example.ui.components.*

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.NexoFilesApp
import com.example.data.model.VaultBackupEntry
import com.example.data.model.VaultFileItem
import com.example.data.model.VaultLockMethod
import com.example.data.model.VaultPortalState
import com.example.security.BiometricAuthRequest
import com.example.security.BiometricAuthResult
import com.example.security.BiometricSecurityManager
import com.example.ui.components.BichromaticCard
import com.example.ui.components.ChamferedTechCard
import com.example.ui.components.IconContainerBadge
import com.example.ui.components.NexoIcons
import com.example.ui.components.QrCodeComposable
import com.example.ui.components.TacticalPatternGrid
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme
import com.example.ui.viewmodel.VaultUiState
import com.example.util.FormatUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(
  state: VaultUiState,
  portalState: VaultPortalState = VaultPortalState(),
  activeLockMethod: VaultLockMethod = VaultLockMethod.BIOMETRIC,
  hasConfiguredSecret: Boolean = false,
  vaultBiometricEnabled: Boolean = true,
  isDriveAuthorized: Boolean = false,
  securityManager: BiometricSecurityManager? = null,
  onVerifySecret: (String) -> Boolean = { false },
  onUnlockSuccess: () -> Unit,
  onLockVault: () -> Unit,
  onAddUriToVault: (Uri) -> Unit,
  onOpenVaultItem: (VaultFileItem) -> Unit,
  onRestoreVaultItem: (VaultFileItem) -> Unit,
  onDeleteVaultItem: (VaultFileItem) -> Unit,
  onDeleteVaultCloudBackup: (VaultFileItem) -> Unit = {},
  onConnectDrive: () -> Unit = {},
  onRefreshDriveBackups: () -> Unit = {},
  onBackupVaultToDrive: (String) -> Unit = {},
  onRunSmartBackup: () -> Unit = { onBackupVaultToDrive("") },
  onDismissBackupProgress: () -> Unit = {},
  onReopenBackupProgress: () -> Unit = {},
  onCancelBackup: () -> Unit = {},
  onTogglePauseBackup: () -> Unit = {},
  onPasteVault: () -> Unit = {},
  onBackupSelectedVaultToDrive: (String, Set<String>) -> Unit = { pass, _ -> onBackupVaultToDrive(pass) },
  onRestoreDriveBackup: (VaultBackupEntry, String) -> Unit = { _, _ -> },
  onRestoreSelectedDriveBackups: (List<VaultBackupEntry>, String) -> Unit = { list, pass -> list.firstOrNull()?.let { onRestoreDriveBackup(it, pass) } },
  onExternalPickerStateChange: (Boolean) -> Unit = {},
  onStartPortal: (String, Int) -> Unit = { _, _ -> },
  onStopPortal: () -> Unit = {},
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
  val availability = remember(resolvedSecurity) { resolvedSecurity.checkAvailability() }

  var authError by remember(activeLockMethod) { mutableStateOf<String?>(null) }
  var secretInput by remember(activeLockMethod) { mutableStateOf("") }
  val patternNodes = remember(activeLockMethod) { mutableStateListOf<Int>() }

  LaunchedEffect(Unit) {
    NexoVaultExtraStore.init(context)
    NexoFeatureFlags.init(context)
    if (isDriveAuthorized && state.driveBackups.isEmpty() && !state.isDriveBusy) {
      onRefreshDriveBackups()
    }
  }
  val isEsVault = strings.languageTitle.contains("Idioma", ignoreCase = true)
  if (!NexoFeatureFlags.vaultEnabled) {
    NexoAtFieldVaultGate(
      isEs = isEsVault,
      isDriveAuthorized = isDriveAuthorized,
      onActivateVault = { NexoFeatureFlags.setVault(context, true) },
      onConnectDrive = onConnectDrive
    )
    return
  }
  var showDriveBackupModal by remember { mutableStateOf(false) }
  var showCloudSyncPanel by remember { mutableStateOf(false) }
  var showNewVaultFolderDialog by remember { mutableStateOf(false) }
  var newVaultFolderName by remember { mutableStateOf("") }
  var pendingCloudDownloadItem by remember { mutableStateOf<NexoCloudPlaceholderItem?>(null) }
  var pendingDeleteChoiceItem by remember { mutableStateOf<VaultFileItem?>(null) }
    var pendingSelectiveDeleteItem by remember { mutableStateOf<VaultFileItem?>(null) }
  var pendingCloudActionItem by remember { mutableStateOf<Pair<VaultFileItem, Boolean>?>(null) }
  var showPortalModal by remember { mutableStateOf(false) }
  var selectedVaultIds by remember(state.items) { mutableStateOf(setOf<String>()) }
  var showMoveToFolderDialog by remember { mutableStateOf(false) }
  var draggingVaultItem by remember { mutableStateOf<VaultFileItem?>(null) }
  var dragFingerRoot by remember { mutableStateOf(Offset.Zero) }
  var hoveredDropFolder by remember { mutableStateOf<String?>(null) }
  val folderDropBounds = remember { mutableStateMapOf<String, Rect>() }

  BackHandler(enabled = !NexoNavBridge.isInMainPager || NexoNavBridge.activePagerTabId == "VAULT") {
    when {
      selectedVaultIds.isNotEmpty() -> selectedVaultIds = emptySet()
      NexoVaultExtraStore.activeFolder != null -> NexoVaultExtraStore.activeFolder = null
      else -> {
        NexoVaultExtraStore.clearThumbnails()
        onLockVault()
        onBack()
      }
    }
  }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(),
  ) { uris ->
    onExternalPickerStateChange(false)
    uris.forEach(onAddUriToVault)
  }

  val safPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments(),
  ) { uris ->
    onExternalPickerStateChange(false)
    uris.forEach(onAddUriToVault)
  }

  fun triggerBiometricUnlock() {
    if (!vaultBiometricEnabled) {
      authError = null
      onUnlockSuccess()
      return
    }
    val activity = fragmentActivity ?: run {
      onUnlockSuccess()
      return
    }
    resolvedSecurity.authenticate(
      activity = activity,
      request = BiometricAuthRequest(
        title = strings.vaultScreenTitle,
        subtitle = strings.vaultLockedDesc,
        allowDeviceCredential = true,
        negativeButtonText = strings.cancel,
      ),
    ) { result ->
      when (result) {
        is BiometricAuthResult.Success -> {
          authError = null
          onUnlockSuccess()
        }
        is BiometricAuthResult.Cancelled -> {
          authError = strings.vaultAuthError(strings.cancel)
        }
        is BiometricAuthResult.Unavailable -> {
          authError = "${result.reason}"
          onUnlockSuccess()
        }
        is BiometricAuthResult.Error -> {
          authError = strings.vaultAuthError(result.message)
        }
      }
    }
  }

  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // HUD Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = {
          run { NexoVaultExtraStore.clearThumbnails(); onLockVault() }
          onBack()
        },
        modifier = Modifier.testTag("vault_back_button"),
      ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back, tint = MaterialTheme.colorScheme.primary)
      }
      Spacer(modifier = Modifier.width(6.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = strings.vaultScreenTitle.uppercase(),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = strings.vaultScreenSubtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (state.isUnlocked) {
        IconButton(onClick = { showCloudSyncPanel = true; if (isDriveAuthorized) onRefreshDriveBackups() }) {
          Icon(NexoIcons.Cloud, contentDescription = "Cloud Sync", tint = MaterialTheme.colorScheme.primary)
        }
        Surface(
          shape = RoundedCornerShape(4.dp),
          color = NexoTheme.semantic.evaRed.copy(alpha = 0.16f),
          border = BorderStroke(1.dp, NexoTheme.semantic.evaRed),
        ) {
          IconButton(
            onClick = onLockVault,
            modifier = Modifier.testTag("vault_lock_button"),
          ) {
            Icon(Icons.Rounded.Lock, contentDescription = strings.lockBtn, tint = NexoTheme.semantic.evaRed)
          }
        }
      }
    }

    if (!state.isUnlocked) {
      // LOCKED VIEW — Displays ONLY the configured activeLockMethod (managed strictly from SettingsScreen)
      ChamferedTechCard(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("vault_locked_card"),
        chamfer = 10.dp,
        accentGlow = true,
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          IconContainerBadge(
            icon = when (activeLockMethod) {
              VaultLockMethod.BIOMETRIC -> NexoIcons.Fingerprint
              else -> Icons.Rounded.Lock
            },
            contentDescription = null,
            size = 60.dp,
            highlighted = true,
          )
          Text(
            text = strings.vaultLockedHeadline.uppercase(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
          )
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
          ) {
            Text(
              text = strings.vaultMethodLabel(activeLockMethod).uppercase(),
              style = MonoTnumStyle,
              color = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }

          when (activeLockMethod) {
            VaultLockMethod.BIOMETRIC -> {
              Text(
                text = if (vaultBiometricEnabled) {
                  "${strings.vaultLockedDesc} (${availability.label})"
                } else {
                  strings.vaultLockedDesc
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )
              Button(
                onClick = { triggerBiometricUnlock() },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("vault_unlock_button"),
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(4.dp),
              ) {
                Icon(NexoIcons.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.unlockNowBtn.uppercase(), fontWeight = FontWeight.Bold)
              }
            }

            VaultLockMethod.PIN, VaultLockMethod.PASSWORD -> {
              if (!hasConfiguredSecret) {
                Text(
                  text = strings.vaultSecurityManagedInSettingsNote,
                  style = MaterialTheme.typography.bodySmall,
                  color = NexoTheme.semantic.evaOrange,
                  textAlign = TextAlign.Center,
                )
              } else {
                OutlinedTextField(
                  value = secretInput,
                  onValueChange = { input ->
                    secretInput = if (activeLockMethod == VaultLockMethod.PIN) {
                      input.filter(Char::isDigit).take(8)
                    } else {
                      input
                    }
                    authError = null
                  },
                  singleLine = true,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vault_secret_input"),
                  visualTransformation = PasswordVisualTransformation(),
                  keyboardOptions = KeyboardOptions(
                    keyboardType = if (activeLockMethod == VaultLockMethod.PIN) KeyboardType.NumberPassword else KeyboardType.Password,
                  ),
                  label = { Text(strings.vaultMethodLabel(activeLockMethod)) },
                  shape = RoundedCornerShape(6.dp),
                  colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                  ),
                )
                Button(
                  onClick = {
                    if (onVerifySecret(secretInput)) {
                      secretInput = ""
                      authError = null
                      onUnlockSuccess()
                    } else {
                      secretInput = ""
                      authError = strings.vaultCredentialIncorrect
                    }
                  },
                  enabled = secretInput.isNotBlank(),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("vault_unlock_button"),
                  colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                  ),
                  shape = RoundedCornerShape(4.dp),
                ) {
                  Icon(NexoIcons.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(strings.unlockNowBtn.uppercase(), fontWeight = FontWeight.Bold)
                }
              }
            }

            VaultLockMethod.PATTERN -> {
              if (!hasConfiguredSecret) {
                Text(
                  text = strings.vaultSecurityManagedInSettingsNote,
                  style = MaterialTheme.typography.bodySmall,
                  color = NexoTheme.semantic.evaOrange,
                  textAlign = TextAlign.Center,
                )
              } else {
                Text(
                  text = strings.vaultPatternHint,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                )
                TacticalPatternGrid(
                  selectedNodes = patternNodes,
                  onNodeTap = { node ->
                    if (node !in patternNodes) {
                      patternNodes.add(node)
                      authError = null
                    }
                  },
                  onReset = {
                    patternNodes.clear()
                    authError = null
                  },
                  isError = authError != null,
                )
                Button(
                  onClick = {
                    val code = patternNodes.joinToString("-")
                    if (patternNodes.size < 4) {
                      authError = strings.vaultPatternHint
                    } else if (onVerifySecret(code)) {
                      patternNodes.clear()
                      authError = null
                      onUnlockSuccess()
                    } else {
                      patternNodes.clear()
                      authError = strings.vaultCredentialIncorrect
                    }
                  },
                  enabled = patternNodes.size >= 4,
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("vault_unlock_button"),
                  colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                  ),
                  shape = RoundedCornerShape(4.dp),
                ) {
                  Icon(NexoIcons.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(strings.unlockNowBtn.uppercase(), fontWeight = FontWeight.Bold)
                }
              }
            }
          }

          authError?.let { err ->
            Text(
              text = err,
              style = MaterialTheme.typography.labelMedium,
              color = NexoTheme.semantic.evaRed,
              textAlign = TextAlign.Center,
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outline)
          Text(
            text = strings.vaultSecurityManagedInSettingsNote,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      }
    } else {
      // UNLOCKED VAULT VIEW (Acciones en FAB '+' flotante)
      if (state.isBusy) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(8.dp))
          Text(strings.loading, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
      }

      if (state.items.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (selectedVaultIds.isNotEmpty()) strings.vaultSelectedCountLabel(selectedVaultIds.size).uppercase() else strings.filesCount(state.items.size).uppercase(),
            style = MonoTnumStyle,
            color = if (selectedVaultIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { selectedVaultIds = state.items.map { it.id }.toSet() }) {
              Text(strings.vaultSelectAllBtn.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (selectedVaultIds.isNotEmpty()) {
              TextButton(onClick = { selectedVaultIds = emptySet() }) {
                Text(strings.vaultClearSelBtn.uppercase(), style = MaterialTheme.typography.labelSmall, color = NexoTheme.semantic.evaOrange)
              }
            }
          }
        }
      }

      if (state.items.isEmpty()) {
        BichromaticCard(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = strings.vaultEmpty,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(bottom = 24.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          item { NexoSecureVaultSummaryPanel(localCount = state.items.size, localBytes = state.items.sumOf { it.sizeBytes }, cloudCount = state.driveBackups.size, onOpenDriveBackupModal = { if (state.isDriveBusy) onReopenBackupProgress() else onRunSmartBackup() }) }
                
      item(key = "vault_folders_bar") {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = when {
                draggingVaultItem != null -> "✋ SUELTA SOBRE UNA CARPETA"
                NexoVaultExtraStore.activeFolder != null -> "📁 CARPETA: ${NexoVaultExtraStore.activeFolder}"
                else -> "CARPETAS SEGURAS (${NexoVaultExtraStore.folders.size})"
              },
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              if (NexoVaultExtraStore.activeFolder != null) {
                OutlinedButton(
                  onClick = { NexoVaultExtraStore.activeFolder = null },
                  contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) { Text("← TODAS", style = MaterialTheme.typography.labelSmall) }
              }
              OutlinedButton(
                onClick = { showNewVaultFolderDialog = true },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
              ) { Text("+ CARPETA", style = MaterialTheme.typography.labelSmall) }
              OutlinedButton(
                onClick = { onRunSmartBackup() },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
              ) { Text("☁️ SYNC", style = MaterialTheme.typography.labelSmall) }
            }
          }
          if (NexoVaultExtraStore.folders.isNotEmpty() || draggingVaultItem != null) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              if (draggingVaultItem != null || NexoVaultExtraStore.activeFolder != null) {
                val isRootHot = hoveredDropFolder == "__ROOT__"
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = if (isRootHot) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
                  border = BorderStroke(if (isRootHot) 2.dp else 1.dp, if (isRootHot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                  modifier = Modifier
                    .onGloballyPositioned { folderDropBounds["__ROOT__"] = it.boundsInRoot() }
                    .clickable { NexoVaultExtraStore.activeFolder = null }
                ) {
                  Text(
                    text = "🔒 Raíz",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                  )
                }
              }
              NexoVaultExtraStore.folders.take(4).forEach { folderName ->
                val isHot = hoveredDropFolder == folderName
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = if (isHot) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
                  border = BorderStroke(if (isHot) 2.dp else 1.dp, if (isHot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                  modifier = Modifier
                    .onGloballyPositioned { folderDropBounds[folderName] = it.boundsInRoot() }
                    .clickable { NexoVaultExtraStore.activeFolder = folderName }
                ) {
                  Text(
                    text = "📁 $folderName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                  )
                }
              }
            }
          }
        }
      }
      if (selectedVaultIds.isNotEmpty() || NexoGlobalClipboard.hasItems()) {
        item(key = "vault_es_action_bar") {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              if (selectedVaultIds.isNotEmpty()) {
                OutlinedButton(
                  onClick = {
                    val picked = state.items.filter { it.id in selectedVaultIds }
                    NexoGlobalClipboard.stageVault(picked, NexoClipMode.COPY)
                    selectedVaultIds = emptySet()
                    Toast.makeText(context, "${picked.size} copiado(s)", Toast.LENGTH_SHORT).show()
                  },
                  contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                  modifier = Modifier.weight(1f)
                ) { Text("📋 COPIAR", style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                OutlinedButton(
                  onClick = {
                    val picked = state.items.filter { it.id in selectedVaultIds }
                    NexoGlobalClipboard.stageVault(picked, NexoClipMode.MOVE)
                    selectedVaultIds = emptySet()
                    Toast.makeText(context, "${picked.size} cortado(s)", Toast.LENGTH_SHORT).show()
                  },
                  contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                  modifier = Modifier.weight(1f)
                ) { Text("✂️ CORTAR", style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                OutlinedButton(
                  onClick = { showMoveToFolderDialog = true },
                  contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                  modifier = Modifier.weight(1f)
                ) { Text("↪ MOVER", style = MaterialTheme.typography.labelSmall, maxLines = 1) }
              }
              if (NexoGlobalClipboard.hasItems()) {
                Button(
                  onClick = { onPasteVault() },
                  contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                  modifier = Modifier.weight(1f)
                ) { Text("📥 PEGAR", style = MaterialTheme.typography.labelSmall, maxLines = 1) }
              }
              if (selectedVaultIds.isNotEmpty()) {
                TextButton(
                  onClick = { selectedVaultIds = emptySet() },
                  contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                ) { Text("✖", color = MaterialTheme.colorScheme.error) }
              }
            }
          }
        }
      }

      items(
        NexoVaultExtraStore.cloudOnlyList.filter {
          NexoVaultExtraStore.activeFolder == null || it.folderName == NexoVaultExtraStore.activeFolder
        },
        key = { "cloud_${it.id}_${it.originalName}" }
      ) { cloudItem ->
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
          modifier = Modifier.fillMaxWidth().clickable { pendingCloudDownloadItem = cloudItem }
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(cloudItem.originalName, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
              Text("☁️ SOLO EN LA NUBE (0 B local) · Toca para descargar", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
            }
            Text("⬇", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
          }
        }
      }

      items(state.items.filter { NexoVaultExtraStore.activeFolder == null || NexoVaultExtraStore.itemFolderMap[it.id] == NexoVaultExtraStore.activeFolder }, key = { it.id }) { item ->
            val isOpeningThis = state.openingItemId == item.id
            val isChecked = item.id in selectedVaultIds
            Surface(
              color = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("vault_item_${item.id}"),
            ) {
              var itemRootTopLeft by remember(item.id) { mutableStateOf(Offset.Zero) }
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .onGloballyPositioned { coords -> itemRootTopLeft = coords.boundsInRoot().topLeft }
                  .pointerInput(item.id, selectedVaultIds) {
                    var totalDragPx = 0f
                    detectDragGesturesAfterLongPress(
                      onDragStart = { startOffset ->
                        totalDragPx = 0f
                        draggingVaultItem = item
                        dragFingerRoot = itemRootTopLeft + startOffset
                        hoveredDropFolder = folderDropBounds.entries.firstOrNull { it.value.contains(dragFingerRoot) }?.key
                      },
                      onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragPx += dragAmount.getDistance()
                        dragFingerRoot = itemRootTopLeft + change.position
                        hoveredDropFolder = folderDropBounds.entries.firstOrNull { it.value.contains(dragFingerRoot) }?.key
                      },
                      onDragCancel = {
                        draggingVaultItem = null
                        hoveredDropFolder = null
                      },
                      onDragEnd = {
                        val dropTarget = hoveredDropFolder
                        val grabbed = draggingVaultItem
                        draggingVaultItem = null
                        hoveredDropFolder = null
                        if (totalDragPx < 24f) {
                          selectedVaultIds = if (item.id in selectedVaultIds) selectedVaultIds - item.id else selectedVaultIds + item.id
                        } else if (grabbed != null && dropTarget != null) {
                          val folderDest = if (dropTarget == "__ROOT__") "" else dropTarget
                          NexoNavBridge.onVaultFolderMoved?.invoke(listOf(grabbed), folderDest)
                            ?: NexoVaultExtraStore.assignToFolder(context, grabbed.id, folderDest)
                        }
                      }
                    )
                  }
                  .clickable {
                    if (selectedVaultIds.isNotEmpty()) {
                      selectedVaultIds = if (item.id in selectedVaultIds) selectedVaultIds - item.id else selectedVaultIds + item.id
                    } else {
                      onOpenVaultItem(item)
                    }
                  }
                  .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(
                  checked = isChecked,
                  onCheckedChange = { checked ->
                    selectedVaultIds = if (checked) selectedVaultIds + item.id else selectedVaultIds - item.id
                  },
                  colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                  ),
                )
                if (isOpeningThis) {
                  Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                  }
                } else {
                  val itemThumb by produceState<ImageBitmap?>(initialValue = null, key1 = item.id) {
            value = NexoVaultExtraStore.loadThumbnail(context, item)
          }
          if (itemThumb != null) {
            Image(
              bitmap = itemThumb!!,
              contentDescription = item.originalName,
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            )
          } else {
            IconContainerBadge(icon = Icons.Rounded.Lock, contentDescription = null, highlighted = true)
          }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Text(
                    text = item.originalName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                      text = "${FormatUtils.formatBytes(item.sizeBytes)} · ${FormatUtils.formatDate(item.addedAt)}",
                      style = MonoTnumStyle,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                    )
                  }

                }
                val isInCloud = remember(state.driveBackups, item.id, item.originalName) {
                  state.driveBackups.any { b ->
                    val clean = b.originalName.substringAfterLast(" / ").removeSuffix(".nexo_vault")
                    clean.equals(item.originalName, ignoreCase = true) ||
                      b.driveFile.name.removeSuffix(".nexo_vault").equals(item.originalName, ignoreCase = true) ||
                      b.driveFile.name.contains(item.id, ignoreCase = true)
                  }
                }
                IconButton(
                  onClick = { pendingCloudActionItem = item to isInCloud },
                  modifier = Modifier.testTag("vault_cloud_action_${item.id}"),
                ) {
                  NexoCloudStatusIcon(
                    isBackedUpInCloud = isInCloud,
                    tint = if (isInCloud) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  if (state.isUnlocked) {
    NexoVaultExpandableFab(
      isEs = isEsVault,
      onAddMedia = {
        onExternalPickerStateChange(true)
        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
      },
      onAddOtherFiles = {
        onExternalPickerStateChange(true)
        safPickerLauncher.launch(arrayOf("*/*"))
      },
      onNewFolder = { showNewVaultFolderDialog = true },
      onOpenCloudSync = { showCloudSyncPanel = true; if (isDriveAuthorized) onRefreshDriveBackups() },
      onOpenWebPortal = { showPortalModal = true }
    )
  }
  } // Fin Box principal

  if (showCloudSyncPanel) {
    NexoVaultCloudSyncDialog(
      state = state,
      isDriveAuthorized = isDriveAuthorized,
      isEs = isEsVault,
      onConnectDrive = onConnectDrive,
      onRefresh = onRefreshDriveBackups,
      onUploadPending = { onRunSmartBackup() },
      onDownloadPending = { list -> onRestoreSelectedDriveBackups(list, "") },
      onOpenProgress = onReopenBackupProgress,
      onOpenAdvancedModal = { showDriveBackupModal = true },
      onDismiss = { showCloudSyncPanel = false }
    )
  }

  pendingCloudActionItem?.let { (target, inCloud) ->
    NexoVaultCloudActionDialog(
      fileName = target.originalName,
      isBackedUpInCloud = inCloud,
      isEs = isEsVault,
      onUploadToCloud = {
        if (state.isDriveBusy) onReopenBackupProgress()
        else onBackupSelectedVaultToDrive("", setOf(target.id))
      },
      onRestoreToDevice = { onRestoreVaultItem(target) },
      onDeleteLocalOnly = { onDeleteVaultItem(target) },
      onDeleteCloudOnly = { onDeleteVaultCloudBackup(target) },
      onDeleteBoth = {
        if (inCloud) onDeleteVaultCloudBackup(target)
        onDeleteVaultItem(target)
      },
      onDismiss = { pendingCloudActionItem = null }
    )
  }
  if (pendingSelectiveDeleteItem != null) {
        val t = pendingSelectiveDeleteItem!!
        NexoVaultSelectiveDeleteDialog(
            fileName = t.originalName,
            onDismiss = { pendingSelectiveDeleteItem = null },
            onSelectOption = { opt ->
                when (opt) {
                    NexoVaultDeleteOption.LOCAL_ONLY -> { NexoSecureVaultManager.markEncryptedAndHiddenInDrive(context, t.id, t.originalName, t.sizeBytes); onDeleteVaultItem(t) }
                    NexoVaultDeleteOption.CLOUD_ONLY -> { NexoSecureVaultManager.removeGhost(context, t.id); showDriveBackupModal = true }
                    NexoVaultDeleteOption.BOTH -> { NexoSecureVaultManager.removeGhost(context, t.id); onDeleteVaultItem(t) }
                }
                pendingSelectiveDeleteItem = null
            }
        )
    }
    if (showDriveBackupModal) {
    VaultDriveBackupModal(
      state = state,
      isDriveAuthorized = isDriveAuthorized,
      initialSelectedVaultIds = selectedVaultIds,
      onConnectDrive = onConnectDrive,
      onRefreshBackups = onRefreshDriveBackups,
      onBackupSelected = onBackupSelectedVaultToDrive,
      onRestoreBackup = onRestoreDriveBackup,
      onRestoreSelectedBackups = onRestoreSelectedDriveBackups,
      onDismiss = { showDriveBackupModal = false },
    )
  }

  if (showPortalModal) {
    VaultEphemeralPortalModal(
      portalState = portalState,
      isBusy = state.isBusy,
      onStartPortal = onStartPortal,
      onStopPortal = onStopPortal,
      onDismiss = { showPortalModal = false },
    )
  }

  draggingVaultItem?.let { grabbed ->
    Dialog(
      onDismissRequest = { draggingVaultItem = null; hoveredDropFolder = null },
      properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
          border = BorderStroke(1.5.dp, Color.White),
          modifier = Modifier
            .offset { IntOffset((dragFingerRoot.x - 140f).roundToInt().coerceAtLeast(12), (dragFingerRoot.y - 120f).roundToInt().coerceAtLeast(12)) }
            .zIndex(99f)
        ) {
          Text(
            text = "✋ ${grabbed.originalName.take(26)}" + (hoveredDropFolder?.let { if (it == "__ROOT__") " → 🔒 Raíz" else " → 📁 $it" } ?: ""),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
          )
        }
      }
    }
  }

  if (state.showBackupProgressDialog) {
    val pct = (state.backupProgress * 100f).roundToInt().coerceIn(0, 100)
    val etaLabel = when {
      !state.isDriveBusy && pct >= 100 -> "Finalizado"
      state.backupEtaSeconds < 0 -> "Calculando tiempo…"
      state.backupEtaSeconds < 60 -> "Tiempo restante: ~${state.backupEtaSeconds} s"
      else -> "Tiempo restante: ~${state.backupEtaSeconds / 60}m ${state.backupEtaSeconds % 60}s"
    }
    AlertDialog(
      onDismissRequest = { onDismissBackupProgress() },
      title = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Respaldo en la Nube", style = MaterialTheme.typography.titleMedium)
          Text("$pct%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          LinearProgressIndicator(
            progress = { state.backupProgress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = state.backupCurrentFileName.ifBlank { "Sincronizando respaldos cifrados…" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          val countInfo = if (state.backupTotalCount > 0) {
            "Subiendo ${state.backupDoneCount} de ${state.backupTotalCount} nuevo(s)" +
              if (state.backupSkippedCount > 0) " · ${state.backupSkippedCount} ya en Drive" else ""
          } else if (state.backupSkippedCount > 0) {
            "${state.backupSkippedCount} archivo(s) ya respaldados en Drive"
          } else ""
          if (countInfo.isNotBlank()) {
            Text(countInfo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
          }
          Text(etaLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          if (state.isDriveBusy) {
            OutlinedButton(
              onClick = onTogglePauseBackup,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(if (state.isBackupPaused) "▶ Reanudar respaldo" else "⏸ Pausar respaldo")
            }
          }
        }
      },
      confirmButton = {
        if (state.isDriveBusy) {
          Button(onClick = {
            onDismissBackupProgress()
            Toast.makeText(context, "Respaldo continúa en segundo plano (revisa la barra de notificaciones)", Toast.LENGTH_SHORT).show()
          }) { Text("Segundo plano") }
        } else {
          Button(onClick = onDismissBackupProgress) { Text("Listo") }
        }
      },
      dismissButton = {
        if (state.isDriveBusy) {
          TextButton(onClick = onCancelBackup) {
            Text("Cancelar", color = MaterialTheme.colorScheme.error)
          }
        } else {
          TextButton(onClick = {
            onDismissBackupProgress()
            showDriveBackupModal = true
          }) { Text("Ver / Restaurar") }
        }
      }
    )
  }

  if (showMoveToFolderDialog) {
    AlertDialog(
      onDismissRequest = { showMoveToFolderDialog = false },
      title = { Text("Mover a carpeta segura (y Drive)") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          val pickedItems = state.items.filter { it.id in selectedVaultIds }
          OutlinedButton(
            onClick = {
              NexoNavBridge.onVaultFolderMoved?.invoke(pickedItems, "")
                ?: pickedItems.forEach { NexoVaultExtraStore.assignToFolder(context, it.id, "") }
              selectedVaultIds = emptySet()
              showMoveToFolderDialog = false
            },
            modifier = Modifier.fillMaxWidth()
          ) { Text("🔒 Raíz de Bóveda (Sin carpeta)") }
          NexoVaultExtraStore.folders.forEach { fName ->
            OutlinedButton(
              onClick = {
                NexoNavBridge.onVaultFolderMoved?.invoke(pickedItems, fName)
                  ?: pickedItems.forEach { NexoVaultExtraStore.assignToFolder(context, it.id, fName) }
                selectedVaultIds = emptySet()
                showMoveToFolderDialog = false
              },
              modifier = Modifier.fillMaxWidth()
            ) { Text("📁 $fName") }
          }
          if (NexoVaultExtraStore.folders.isEmpty()) {
            Text("Primero crea una carpeta con el botón '+ CARPETA'.")
          }
        }
      },
      confirmButton = { TextButton(onClick = { showMoveToFolderDialog = false }) { Text("Cerrar") } }
    )
  }

  if (showNewVaultFolderDialog) {
    AlertDialog(
      onDismissRequest = { showNewVaultFolderDialog = false },
      title = { Text("Nueva carpeta segura en Bóveda") },
      text = {
        OutlinedTextField(
          value = newVaultFolderName,
          onValueChange = { newVaultFolderName = it },
          label = { Text("Nombre de la carpeta") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        Button(onClick = {
          NexoVaultExtraStore.createFolder(context, newVaultFolderName)
          newVaultFolderName = ""
          showNewVaultFolderDialog = false
        }) { Text("Crear") }
      },
      dismissButton = {
        TextButton(onClick = { showNewVaultFolderDialog = false }) { Text("Cancelar") }
      }
    )
  }

  pendingDeleteChoiceItem?.let { targetItem ->
    AlertDialog(
      onDismissRequest = { pendingDeleteChoiceItem = null },
      title = { Text("Eliminar archivo de la Bóveda") },
      text = { Text("¿Deseas eliminar «${targetItem.originalName}» solo del teléfono (quedará visible sin datos locales para descargarlo de la nube al abrirlo) o eliminarlo por completo?") },
      confirmButton = {
        Button(onClick = {
          NexoVaultExtraStore.addCloudOnlyPlaceholder(context, targetItem)
          onDeleteVaultItem(targetItem)
          pendingDeleteChoiceItem = null
        }) { Text("Solo teléfono (☁️)") }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          TextButton(onClick = {
            onDeleteVaultItem(targetItem)
            pendingDeleteChoiceItem = null
          }) { Text("Eliminar todo") }
          TextButton(onClick = { pendingDeleteChoiceItem = null }) { Text("Cancelar") }
        }
      }
    )
  }

  pendingCloudDownloadItem?.let { cloudItem ->
    AlertDialog(
      onDismissRequest = { pendingCloudDownloadItem = null },
      title = { Text("Archivo almacenado en la nube") },
      text = { Text("«${cloudItem.originalName}» fue eliminado localmente para ahorrar espacio. ¿Deseas descargarlo y descifrarlo desde Google Drive ahora?") },
      confirmButton = {
        Button(onClick = {
          val match = state.driveBackups.firstOrNull {
            it.originalName.equals(cloudItem.originalName, ignoreCase = true) ||
            it.driveFile.name.startsWith(cloudItem.originalName, ignoreCase = true)
          }
          if (match != null) {
            onRestoreDriveBackup(match, "NEXO_HERMETIC_AUTO_KEY_2026")
            NexoVaultExtraStore.removeCloudOnlyPlaceholder(context, cloudItem.originalName)
          } else {
            onRefreshDriveBackups()
            showDriveBackupModal = true
            Toast.makeText(context, "Actualizando respaldos en Google Drive...", Toast.LENGTH_SHORT).show()
          }
          pendingCloudDownloadItem = null
        }) { Text("Descargar de la nube") }
      },
      dismissButton = {
        TextButton(onClick = { pendingCloudDownloadItem = null }) { Text("Cancelar") }
      }
    )
  }

}

@Composable
private fun VaultDriveBackupModal(
  state: VaultUiState,
  isDriveAuthorized: Boolean,
  initialSelectedVaultIds: Set<String> = emptySet(),
  onConnectDrive: () -> Unit,
  onRefreshBackups: () -> Unit,
  onBackupSelected: (String, Set<String>) -> Unit,
  onRestoreBackup: (VaultBackupEntry, String) -> Unit,
  onRestoreSelectedBackups: (List<VaultBackupEntry>, String) -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  val modalCtx = LocalContext.current
  var passphrase by remember { mutableStateOf(NexoVaultSecurityStore.getBackupPassphrase(modalCtx)) }
  var checkedVaultIds by remember(state.items, initialSelectedVaultIds) {
    mutableStateOf(
      initialSelectedVaultIds.ifEmpty { state.items.map { it.id }.toSet() },
    )
  }
  var checkedBackupFileIds by remember(state.driveBackups) {
    mutableStateOf(setOf<String>())
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(10.dp),
    containerColor = NexoTheme.semantic.modalSurface,
    title = {
      Text(
        text = strings.vaultDriveModalTitle.uppercase(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = strings.vaultDriveModalDesc,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!isDriveAuthorized) {
          Button(
            onClick = onConnectDrive,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("vault_drive_connect_btn"),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(4.dp),
          ) {
            Icon(NexoIcons.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(strings.signInGoogleBtn.uppercase(), fontWeight = FontWeight.Bold)
          }
        } else {
          Text(
            text = "🔑 Cifrado automático activo con tu Master PIN configurado en Ajustes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
          )

          if (state.items.isNotEmpty()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = strings.vaultSelectedCountLabel(checkedVaultIds.size).uppercase(),
                style = MonoTnumStyle,
                color = MaterialTheme.colorScheme.primary,
              )
              Row {
                TextButton(onClick = { checkedVaultIds = state.items.map { it.id }.toSet() }) {
                  Text(strings.vaultSelectAllBtn.uppercase(), style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { checkedVaultIds = emptySet() }) {
                  Text(strings.vaultClearSelBtn.uppercase(), style = MaterialTheme.typography.labelSmall)
                }
              }
            }
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 150.dp)
                .verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              state.items.forEach { vItem ->
                val isChecked = vItem.id in checkedVaultIds
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Checkbox(
                    checked = isChecked,
                    onCheckedChange = { c ->
                      checkedVaultIds = if (c) checkedVaultIds + vItem.id else checkedVaultIds - vItem.id
                    },
                    colors = CheckboxDefaults.colors(
                      checkedColor = MaterialTheme.colorScheme.primary,
                      checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                  )
                  Text(
                    text = vItem.originalName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                  )
                  Text(
                    text = FormatUtils.formatBytes(vItem.sizeBytes),
                    style = MonoTnumStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = { onBackupSelected(passphrase, checkedVaultIds) },
              enabled = !state.isDriveBusy && passphrase.length >= 4 && checkedVaultIds.isNotEmpty(),
              modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .testTag("vault_drive_backup_all_btn"),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
              shape = RoundedCornerShape(4.dp),
            ) {
              Text(
                text = strings.backupSelectedToDriveBtn(checkedVaultIds.size).uppercase(),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            OutlinedButton(
              onClick = onRefreshBackups,
              enabled = !state.isDriveBusy,
              modifier = Modifier.height(44.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
              shape = RoundedCornerShape(4.dp),
            ) {
              Icon(NexoIcons.Refresh, contentDescription = strings.vaultRefreshBackupsBtn, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
            }
          }

          if (state.isDriveBusy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
              Text(state.driveStatusMessage ?: strings.loading, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
          } else if (!state.driveStatusMessage.isNullOrBlank()) {
            Text(state.driveStatusMessage, style = MonoTnumStyle, color = MaterialTheme.colorScheme.secondary)
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outline)

          if (state.driveBackups.isEmpty()) {
            Text(strings.vaultNoDriveBackups, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          } else {
            if (checkedBackupFileIds.isNotEmpty()) {
              Button(
                onClick = {
                  val selectedEntries = state.driveBackups.filter { it.driveFile.id in checkedBackupFileIds }
                  onRestoreSelectedBackups(selectedEntries, passphrase)
                },
                enabled = !state.isDriveBusy && passphrase.length >= 4,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.secondary,
                  contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                shape = RoundedCornerShape(4.dp),
              ) {
                Text(strings.restoreSelectedBtn(checkedBackupFileIds.size).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
              }
            }
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              state.driveBackups.forEach { entry ->
                val isBackupChecked = entry.driveFile.id in checkedBackupFileIds
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = MaterialTheme.colorScheme.surface,
                  border = BorderStroke(1.dp, if (isBackupChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Checkbox(
                      checked = isBackupChecked,
                      onCheckedChange = { c ->
                        checkedBackupFileIds = if (c) checkedBackupFileIds + entry.driveFile.id else checkedBackupFileIds - entry.driveFile.id
                      },
                      colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                      ),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = entry.originalName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text = FormatUtils.formatBytes(entry.driveFile.sizeBytes),
                        style = MonoTnumStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    Button(
                      onClick = { onRestoreBackup(entry, passphrase) },
                      enabled = !state.isDriveBusy && passphrase.length >= 4,
                      colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                      ),
                      shape = RoundedCornerShape(4.dp),
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                      Text(strings.vaultRestoreFromDriveBtn.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(strings.close.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
      }
    },
  )
}

@Composable
private fun VaultEphemeralPortalModal(
  portalState: VaultPortalState,
  isBusy: Boolean,
  onStartPortal: (String, Int) -> Unit,
  onStopPortal: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val strings = LocalStrings.current
  val modalCtx = LocalContext.current
  var passphrase by remember { mutableStateOf(NexoVaultSecurityStore.getBackupPassphrase(modalCtx)) }
  var selectedTtlMinutes by remember { mutableIntStateOf(15) }
  var remainingSeconds by remember(portalState.expiresAtMs, portalState.isRunning) { mutableLongStateOf(0L) }

  LaunchedEffect(portalState.isRunning, portalState.expiresAtMs) {
    while (portalState.isRunning) {
      val rem = ((portalState.expiresAtMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
      remainingSeconds = rem
      delay(1000L)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(10.dp),
    containerColor = NexoTheme.semantic.modalSurface,
    title = {
      Text(
        text = strings.vaultPortalModalTitle.uppercase(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = strings.vaultPortalModalDesc,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!portalState.isRunning) {
          OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text(strings.vaultPassphraseLabel) },
            placeholder = { Text(strings.vaultPassphrasePlaceholder) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("vault_portal_passphrase_input"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            listOf(5, 15, 30).forEach { mins ->
              val active = selectedTtlMinutes == mins
              FilterChip(
                selected = active,
                onClick = { selectedTtlMinutes = mins },
                label = { Text("$mins MIN", style = MonoTnumStyle) },
                shape = RoundedCornerShape(4.dp),
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primary,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.weight(1f),
              )
            }
          }

          Button(
            onClick = { onStartPortal(passphrase, selectedTtlMinutes) },
            enabled = !isBusy && passphrase.length >= 4,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("vault_start_portal_btn"),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(4.dp),
          ) {
            if (isBusy) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
              Spacer(modifier = Modifier.width(8.dp))
            }
            Text(strings.vaultStartPortalBtn.uppercase(), fontWeight = FontWeight.Bold)
          }
        } else {
          val mins = remainingSeconds / 60L
          val secs = remainingSeconds % 60L
          val timerFormatted = String.format(java.util.Locale.US, "%02d:%02d", mins, secs)

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(6.dp))
              .background(MaterialTheme.colorScheme.background)
              .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
              .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "TOKEN // ${portalState.token.take(8).uppercase()}",
              style = MonoTnumStyle,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "TTL $timerFormatted",
              style = MonoTnumStyle,
              color = if (remainingSeconds < 60L) NexoTheme.semantic.evaRed else NexoTheme.semantic.evaOrange,
            )
          }

          QrCodeComposable(content = portalState.url, size = 188.dp)

          Text(
            text = portalState.url,
            style = MonoTnumStyle,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
          )

          Text(
            text = strings.vaultPortalActiveHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )

          if (portalState.lastEvent.isNotBlank()) {
            Text(
              text = portalState.lastEvent,
              style = MonoTnumStyle,
              color = MaterialTheme.colorScheme.secondary,
              textAlign = TextAlign.Center,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("NexoVault Portal", portalState.url))
                Toast.makeText(context, strings.vaultPortalCopyLinkBtn, Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier
                .weight(1f)
                .height(44.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
              shape = RoundedCornerShape(4.dp),
            ) {
              Icon(NexoIcons.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(strings.vaultPortalCopyLinkBtn.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Button(
              onClick = onStopPortal,
              modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .testTag("vault_stop_portal_btn"),
              colors = ButtonDefaults.buttonColors(
                containerColor = NexoTheme.semantic.evaRed,
                contentColor = MaterialTheme.colorScheme.onError,
              ),
              shape = RoundedCornerShape(4.dp),
            ) {
              Text(strings.vaultStopPortalBtn.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(strings.close.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    },
  )
}
