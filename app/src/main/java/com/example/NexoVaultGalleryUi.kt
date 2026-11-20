package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VaultBackupEntry
import com.example.data.model.VaultFileItem
import com.example.ui.components.NexoIcons
import com.example.ui.viewmodel.VaultUiState
import com.example.util.FormatUtils

private fun isImgName(n: String) = n.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp") || it.endsWith(".gif") || it.endsWith(".heic") || it.endsWith(".heif") }
private fun isVidName(n: String) = n.lowercase().let { it.endsWith(".mp4") || it.endsWith(".mkv") || it.endsWith(".mov") || it.endsWith(".avi") || it.endsWith(".webm") }

@Composable
fun NexoVaultCloudSyncDialog(
    state: VaultUiState,
    isDriveAuthorized: Boolean,
    isEs: Boolean,
    onConnectDrive: () -> Unit,
    onRefresh: () -> Unit,
    onUploadPending: () -> Unit,
    onDownloadPending: (List<VaultBackupEntry>) -> Unit,
    onOpenProgress: () -> Unit,
    onOpenAdvancedModal: () -> Unit,
    onDismiss: () -> Unit,
) {
    val localImgs = state.items.count { isImgName(it.originalName) }
    val localVids = state.items.count { isVidName(it.originalName) }
    val localDocs = (state.items.size - localImgs - localVids).coerceAtLeast(0)
    val localBytes = state.items.sumOf { it.sizeBytes }

    val cloudImgs = state.driveBackups.count { isImgName(it.originalName) }
    val cloudVids = state.driveBackups.count { isVidName(it.originalName) }
    val cloudDocs = (state.driveBackups.size - cloudImgs - cloudVids).coerceAtLeast(0)
    val cloudBytes = state.driveBackups.sumOf { it.driveFile.sizeBytes }

    val remoteNames = remember(state.driveBackups) {
        state.driveBackups.map { it.originalName.substringAfterLast(" / ").removeSuffix(".nexo_vault").lowercase() }.toSet()
    }
    val localNames = remember(state.items) { state.items.map { it.originalName.lowercase() }.toSet() }

    val notUploaded = remember(state.items, remoteNames) { state.items.filter { it.originalName.lowercase() !in remoteNames } }
    val notDownloaded = remember(state.driveBackups, localNames) {
        state.driveBackups.filter { it.originalName.substringAfterLast(" / ").removeSuffix(".nexo_vault").lowercase() !in localNames }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cloud Sync", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(onClick = onRefresh, enabled = !state.isDriveBusy) {
                    Icon(NexoIcons.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (!isDriveAuthorized) {
                    Text(if (isEs) "Conecta tu cuenta de Google Drive para sincronizar tu Bóveda." else "Connect Google Drive to sync your Vault.", fontSize = 12.sp)
                    Button(onClick = { onConnectDrive(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isEs) "Conectar Google Drive" else "Connect Google Drive")
                    }
                } else {
                    if (state.isDriveBusy) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            modifier = Modifier.fillMaxWidth().clickable { onDismiss(); onOpenProgress() }
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(if (isEs) "Sincronizando… Toca para ver progreso" else "Syncing… Tap for progress", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Cloud Row
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isEs) "☁️ Nube" else "☁️ Cloud", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${FormatUtils.formatBytes(cloudBytes)} · ${state.driveBackups.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(if (isEs) "Fotos: $cloudImgs  ·  Videos: $cloudVids  ·  Otros: $cloudDocs" else "Photos: $cloudImgs  ·  Videos: $cloudVids  ·  Files: $cloudDocs", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Local Row
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("📱 Local", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${FormatUtils.formatBytes(localBytes)} · ${state.items.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                            Text(if (isEs) "Fotos: $localImgs  ·  Videos: $localVids  ·  Otros: $localDocs" else "Photos: $localImgs  ·  Videos: $localVids  ·  Files: $localDocs", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Not Uploaded Row
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(if (isEs) "${notUploaded.size} sin subir" else "${notUploaded.size} not uploaded", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(FormatUtils.formatBytes(notUploaded.sumOf { it.sizeBytes }), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { onDismiss(); onUploadPending() }, enabled = notUploaded.isNotEmpty() && !state.isDriveBusy) {
                            Text(if (isEs) "Subir ahora" else "Upload Now", fontSize = 11.sp)
                        }
                    }

                    // Not Downloaded Row
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(if (isEs) "${notDownloaded.size} sin descargar" else "${notDownloaded.size} not downloaded", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(FormatUtils.formatBytes(notDownloaded.sumOf { it.driveFile.sizeBytes }), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { onDismiss(); onDownloadPending(notDownloaded) }, enabled = notDownloaded.isNotEmpty() && !state.isDriveBusy) {
                            Text(if (isEs) "Descargar" else "Download", fontSize = 11.sp)
                        }
                    }

                    TextButton(onClick = { onDismiss(); onOpenAdvancedModal() }, modifier = Modifier.align(Alignment.End)) {
                        Text(if (isEs) "Gestión avanzada de respaldos →" else "Advanced backup manager →", fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (isEs) "Cerrar" else "Close") } }
    )
}

@Composable
fun NexoVaultExpandableFab(
    isEs: Boolean,
    onAddMedia: () -> Unit,
    onAddOtherFiles: () -> Unit,
    onNewFolder: () -> Unit,
    onOpenCloudSync: () -> Unit,
    onOpenWebPortal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { expanded = false }
            )
        }
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(visible = expanded) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FabMiniRow(if (isEs) "Portal Web QR" else "Web Portal QR", NexoIcons.QrCode2) { expanded = false; onOpenWebPortal() }
                    FabMiniRow("Cloud Sync", NexoIcons.Cloud) { expanded = false; onOpenCloudSync() }
                    FabMiniRow(if (isEs) "Nueva carpeta" else "New Folder", NexoIcons.Folder) { expanded = false; onNewFolder() }
                    FabMiniRow(if (isEs) "Otros archivos" else "Add Other Files", NexoIcons.FileOpen) { expanded = false; onAddOtherFiles() }
                    FabMiniRow(if (isEs) "Fotos y Videos" else "Add Pictures & Videos", NexoIcons.AddPhotoAlternate) { expanded = false; onAddMedia() }
                }
            }
            FloatingActionButton(
                onClick = { expanded = !expanded },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(if (expanded) Icons.Rounded.Close else Icons.Rounded.Add, contentDescription = null)
            }
        }
    }
}

@Composable
private fun FabMiniRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.clickable(onClick = onClick)) {
        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xDD1A1A24), border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.15f))) {
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, shadowElevation = 4.dp, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
