package com.example.ui.screens

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
  onConnectDrive: () -> Unit = {},
  onRefreshDriveBackups: () -> Unit = {},
  onBackupVaultToDrive: (String) -> Unit = {},
  onBackupSelectedVaultToDrive: (String, Set<String>) -> Unit = { pass, _ -> onBackupVaultToDrive(pass) },
  onRestoreDriveBackup: (VaultBackupEntry, String) -> Unit = { _, _ -> },
  onRestoreSelectedDriveBackups: (List<VaultBackupEntry>, String) -> Unit = { list, pass -> list.firstOrNull()?.let { onRestoreDriveBackup(it, pass) } },
  onExternalPickerStateChange: (Boolean) -> Unit = {},
  onStartPortal: (String, Int) -> Unit = { _, _ -> },
  onStopPortal: () -> Unit = {},
  onBack: () -> Unit,
) {
  BackHandler {
    run { NexoVaultExtraStore.clearThumbnails(); onLockVault() }
    onBack()
  }

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

  LaunchedEffect(Unit) { NexoVaultExtraStore.init(context) }
  var showDriveBackupModal by remember { mutableStateOf(false) }
  var showNewVaultFolderDialog by remember { mutableStateOf(false) }
  var newVaultFolderName by remember { mutableStateOf("") }
  var pendingCloudDownloadItem by remember { mutableStateOf<NexoCloudPlaceholderItem?>(null) }
  var pendingDeleteChoiceItem by remember { mutableStateOf<VaultFileItem?>(null) }
    var pendingSelectiveDeleteItem by remember { mutableStateOf<VaultFileItem?>(null) }
  var showPortalModal by remember { mutableStateOf(false) }
  var selectedVaultIds by remember(state.items) { mutableStateOf(setOf<String>()) }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    onExternalPickerStateChange(false)
    uri?.let(onAddUriToVault)
  }

  val safPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    onExternalPickerStateChange(false)
    uri?.let(onAddUriToVault)
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

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
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
      // UNLOCKED VAULT VIEW
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = {
            onExternalPickerStateChange(true)
            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
          },
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("vault_add_media_btn"),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
          shape = RoundedCornerShape(4.dp),
        ) {
          Icon(NexoIcons.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(strings.multimedia.uppercase(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
          onClick = {
            onExternalPickerStateChange(true)
            safPickerLauncher.launch(arrayOf("*/*"))
          },
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("vault_add_file_btn"),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
          shape = RoundedCornerShape(4.dp),
        ) {
          Icon(NexoIcons.FileOpen, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(strings.addEncryptedFileBtn.uppercase(), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }

      // Google Drive Encrypted Backup (.nexo_vault) + Ephemeral Web/iPhone Portal QR
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = {
            showDriveBackupModal = true
            if (isDriveAuthorized) onRefreshDriveBackups()
          },
          modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .testTag("vault_drive_backup_btn"),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
          shape = RoundedCornerShape(4.dp),
          contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
          Icon(NexoIcons.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = strings.vaultDriveBackupBtn.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        OutlinedButton(
          onClick = { showPortalModal = true },
          modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .testTag("vault_web_portal_btn"),
          border = BorderStroke(
            1.dp,
            if (portalState.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
          ),
          shape = RoundedCornerShape(4.dp),
          contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
          Icon(
            NexoIcons.QrCode2,
            contentDescription = null,
            tint = if (portalState.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = strings.vaultWebPortalBtn.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (portalState.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

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
          item { NexoSecureVaultSummaryPanel(localCount = state.items.size, localBytes = state.items.sumOf { it.sizeBytes }, cloudCount = state.driveBackups.size, onOpenDriveBackupModal = { showDriveBackupModal = true }) }
                
      item(key = "vault_folders_bar") {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = if (NexoVaultExtraStore.activeFolder != null) "📁 CARPETA: ${NexoVaultExtraStore.activeFolder}" else "CARPETAS SEGURAS (${NexoVaultExtraStore.folders.size})",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              if (NexoVaultExtraStore.activeFolder != null) {
                OutlinedButton(
                  onClick = { NexoVaultExtraStore.activeFolder = null },
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) { Text("← TODAS", style = MaterialTheme.typography.labelSmall) }
              }
              OutlinedButton(
                onClick = { showNewVaultFolderDialog = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
              ) { Text("+ CARPETA", style = MaterialTheme.typography.labelSmall) }
            }
          }
          if (NexoVaultExtraStore.folders.isNotEmpty() && NexoVaultExtraStore.activeFolder == null) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              NexoVaultExtraStore.folders.take(4).forEach { folderName ->
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = MaterialTheme.colorScheme.surface,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                  modifier = Modifier.clickable { NexoVaultExtraStore.activeFolder = folderName }
                ) {
                  Text(
                    text = "🔒 $folderName",
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
            BichromaticCard(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("vault_item_${item.id}"),
              accentBorder = isOpeningThis || isChecked,
              onClick = { onOpenVaultItem(item) },
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 8.dp, vertical = 10.dp),
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
                  Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.secondary),
                  ) {
                    Text(
                      text = strings.vaultInternalBadge.uppercase(),
                      style = MonoTnumStyle,
                      color = MaterialTheme.colorScheme.secondary,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                  }
                }
                IconButton(
                  onClick = { onRestoreVaultItem(item) },
                  modifier = Modifier.testTag("vault_restore_${item.id}"),
                ) {
                  Icon(NexoIcons.Restore, contentDescription = strings.restore, tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(
                  onClick = { pendingSelectiveDeleteItem = item },
                  modifier = Modifier.testTag("vault_delete_${item.id}"),
                ) {
                  Icon(NexoIcons.DeleteForever, contentDescription = strings.deleteForever, tint = NexoTheme.semantic.evaRed)
                }
              }
            }
          }
        }
      }
    }
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
  var passphrase by remember { mutableStateOf("NEXO_HERMETIC_AUTO_KEY_2026") }
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
              .testTag("vault_drive_passphrase_input"),
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
  var passphrase by remember { mutableStateOf("NEXO_HERMETIC_AUTO_KEY_2026") }
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
