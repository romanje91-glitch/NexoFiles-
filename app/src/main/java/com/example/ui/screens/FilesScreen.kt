package com.example.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.HorizontalDivider
import com.example.NexoGlobalClipboard
import com.example.NexoClipMode
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.sp
import com.example.NexoNavBridge
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.example.ui.components.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data.model.ClipboardAction
import com.example.data.model.LayoutViewMode
import com.example.data.model.LocalFileItem
import com.example.data.model.SortBy
import com.example.ui.components.BichromaticCard
import com.example.ui.components.ChamferedTechCard
import com.example.ui.components.IconContainerBadge
import com.example.ui.components.NexoIcons
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoFilesTheme
import com.example.ui.theme.NexoTheme
import com.example.ui.viewmodel.ExplorerUiState
import com.example.util.FormatUtils
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
  uiState: ExplorerUiState,
  layoutViewMode: LayoutViewMode = LayoutViewMode.DETAILED,
  onChangeLayoutMode: (LayoutViewMode) -> Unit = {},
  onNavigateToPath: (String) -> Unit,
  onNavigateUp: () -> Unit,
  onToggleHidden: () -> Unit,
  onSortBy: (SortBy) -> Unit,
  onToggleSelect: (String) -> Unit,
  onSelectAll: () -> Unit,
  onClearSelection: () -> Unit,
  onOpenFile: (LocalFileItem) -> Unit,
  onOpenWith: (LocalFileItem) -> Unit = {},
  onOpenInNoteEditor: (LocalFileItem) -> Unit,
  onOpenInPhotoEditor: (LocalFileItem) -> Unit = {},
  onInspectArchive: (LocalFileItem) -> Unit,
  onExtractArchive: (LocalFileItem) -> Unit,
  onExtractArchiveTo: (LocalFileItem, String) -> Unit = { _, _ -> },
  onCreateFolder: (String) -> Unit,
  onCreateTextFile: (String) -> Unit,
  onRenameItem: (String, String) -> Unit,
  onStageClipboard: (List<String>, ClipboardAction) -> Unit,
  onPasteClipboard: () -> Unit,
  onClearClipboard: () -> Unit,
  onMoveToRecycleBin: (List<String>) -> Unit,
  onDeletePermanently: (List<String>) -> Unit,
  onSharePaths: (List<String>) -> Unit,
  onOpenProperties: (String) -> Unit,
  onDismissProperties: () -> Unit,
  onDismissArchiveInspector: () -> Unit,
  onToggleBookmark: (LocalFileItem) -> Unit,
  onCreateZip: (List<String>, String, String?) -> Unit,
  onImportSaf: () -> Unit,
  onExportSaf: (String) -> Unit,
  onUploadToDrive: (LocalFileItem) -> Unit,
  onLockInVault: (LocalFileItem) -> Unit,
) {
  val strings = LocalStrings.current
  var showEsMoreMenu by remember { mutableStateOf(false) }
  var showCreateMenu by remember { mutableStateOf(false) }
  var showNewFolderDialog by remember { mutableStateOf(false) }
  var showNewFileDialog by remember { mutableStateOf(false) }
  var zipTargetPaths by remember { mutableStateOf<List<String>?>(null) }
  androidx.activity.compose.BackHandler(enabled = uiState.currentPath.trimEnd('/').isNotBlank() && uiState.currentPath.trimEnd('/') != "/storage/emulated/0") { onNavigateUp() }
    var renameTarget by remember { mutableStateOf<LocalFileItem?>(null) }
  var folderFilterQuery by remember { mutableStateOf("") }
  val isLandscapeExplorer = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
  var expandLandscapeHeader by remember { mutableStateOf(false) }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    bottomBar = {
      val selList = uiState.selectedPaths.toList()
      val selItems = uiState.files.filter { it.path in uiState.selectedPaths }
      val singleItem = if (selItems.size == 1) selItems.first() else null
      Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (selList.isEmpty()) {
            Box {
              TextButton(onClick = { showCreateMenu = true }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                Text("+ NUEVO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
              }
              DropdownMenu(
                expanded = showCreateMenu,
                onDismissRequest = { showCreateMenu = false },
                modifier = Modifier.background(NexoTheme.semantic.modalSurface).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
              ) {
                DropdownMenuItem(
                  text = { Text(strings.newFolderMenu.uppercase(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) },
                  leadingIcon = { Icon(NexoIcons.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                  onClick = { showCreateMenu = false; showNewFolderDialog = true },
                )
                DropdownMenuItem(
                  text = { Text(strings.newFileMenu.uppercase(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) },
                  leadingIcon = { Icon(NexoIcons.NoteAdd, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                  onClick = { showCreateMenu = false; showNewFileDialog = true },
                )
                DropdownMenuItem(
                  text = { Text(strings.importSafMenu.uppercase(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) },
                  leadingIcon = { Icon(NexoIcons.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                  onClick = { showCreateMenu = false; onImportSaf() },
                )
              }
            }
            TextButton(onClick = { onNavigateToPath(uiState.currentPath) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
              Text("↻ REFRESCAR", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            TextButton(onClick = onSelectAll, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
              Text("☑ TODO", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Box {
              TextButton(onClick = { showEsMoreMenu = true }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                Text("⋯ MÁS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
              }
              DropdownMenu(
                expanded = showEsMoreMenu,
                onDismissRequest = { showEsMoreMenu = false },
                modifier = Modifier.background(NexoTheme.semantic.modalSurface).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
              ) {
                if (uiState.currentPath.trimEnd('/') != "/storage/emulated/0") {
                  DropdownMenuItem(text = { Text("← Subir carpeta") }, onClick = { showEsMoreMenu = false; onNavigateUp() })
                }
                DropdownMenuItem(text = { Text("🌐 Navegador Web") }, onClick = { showEsMoreMenu = false; NexoNavBridge.showWebBrowser = true })
                DropdownMenuItem(text = { Text("🧲 NexoTorrent") }, onClick = { showEsMoreMenu = false; NexoNavBridge.openTorrentWithMagnet() })
              }
            }
          } else {
            TextButton(
              onClick = {
                NexoGlobalClipboard.stageLocal(selList, NexoClipMode.COPY)
                onStageClipboard(selList, ClipboardAction.COPY)
                onClearSelection()
              },
              contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            ) { Text("📋 COPIAR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
            TextButton(
              onClick = {
                NexoGlobalClipboard.stageLocal(selList, NexoClipMode.MOVE)
                onStageClipboard(selList, ClipboardAction.MOVE)
                onClearSelection()
              },
              contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            ) { Text("✂️ CORTAR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
            TextButton(
              onClick = { onMoveToRecycleBin(selList) },
              contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            ) { Text("🗑 BORRAR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NexoTheme.semantic.evaRed) }
            TextButton(
              onClick = {
                if (singleItem != null) renameTarget = singleItem else zipTargetPaths = selList
              },
              contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            ) { Text(if (singleItem != null) "✏ NOMBRE" else "📦 ZIP", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface) }
            Box {
              TextButton(
                onClick = { showEsMoreMenu = true },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
              ) { Text("⋯ MÁS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary) }
              DropdownMenu(
                expanded = showEsMoreMenu,
                onDismissRequest = { showEsMoreMenu = false },
                modifier = Modifier.background(NexoTheme.semantic.modalSurface).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
              ) {
                if (singleItem != null && !singleItem.isDirectory) {
                  DropdownMenuItem(text = { Text(strings.openWith) }, onClick = { showEsMoreMenu = false; onOpenWith(singleItem) })
                }
                if (singleItem != null && !singleItem.isDirectory && FormatUtils.isArchive(singleItem.name)) {
                  DropdownMenuItem(text = { Text(strings.extractHere) }, onClick = { showEsMoreMenu = false; onExtractArchive(singleItem) })
                }
                if (singleItem != null && !singleItem.isDirectory && FormatUtils.isImage(singleItem.name)) {
                  DropdownMenuItem(text = { Text(strings.editInNexoPhotoEditor) }, onClick = { showEsMoreMenu = false; onOpenInPhotoEditor(singleItem) })
                }
                if (singleItem != null && !singleItem.isDirectory && FormatUtils.isTextEditable(singleItem.name)) {
                  DropdownMenuItem(text = { Text(strings.editInNexoNotes) }, onClick = { showEsMoreMenu = false; onOpenInNoteEditor(singleItem) })
                }
                DropdownMenuItem(text = { Text("🔒 Cifrar en Bóveda (${selItems.size})") }, onClick = {
                  showEsMoreMenu = false
                  selItems.filterNot { it.isDirectory }.forEach { onLockInVault(it) }
                  onClearSelection()
                })
                DropdownMenuItem(text = { Text("☁️ Subir a Google Drive") }, onClick = {
                  showEsMoreMenu = false
                  selItems.filterNot { it.isDirectory }.forEach { onUploadToDrive(it) }
                  onClearSelection()
                })
                DropdownMenuItem(text = { Text("↗ Compartir") }, onClick = { showEsMoreMenu = false; onSharePaths(selList) })
                DropdownMenuItem(text = { Text("📦 Comprimir ZIP") }, onClick = { showEsMoreMenu = false; zipTargetPaths = selList })
                if (singleItem != null) {
                  DropdownMenuItem(text = { Text("★ Marcador") }, onClick = { showEsMoreMenu = false; onToggleBookmark(singleItem) })
                  DropdownMenuItem(text = { Text("ℹ Propiedades") }, onClick = { showEsMoreMenu = false; onOpenProperties(singleItem.path) })
                }
                DropdownMenuItem(text = { Text("⚠ Eliminar permanente", color = NexoTheme.semantic.evaRed) }, onClick = {
                  showEsMoreMenu = false; onDeletePermanently(selList)
                })
              }
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
        .padding(horizontal = 16.dp),
    ) {
      if (isLandscapeExplorer) {
        Row(
          modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (uiState.currentPath.trimEnd('/') != "/storage/emulated/0") {
              TextButton(onClick = onNavigateUp, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                Text("←", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
              }
            }
            Text(
              text = uiState.currentPath.substringAfterLast('/').ifBlank { "Almacenamiento" },
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1
            )
          }
          TextButton(
            onClick = { expandLandscapeHeader = !expandLandscapeHeader },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
          ) {
            Text(
              text = if (expandLandscapeHeader) "▲ Ocultar barra" else "▼ Buscar / Ordenar",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.secondary
            )
          }
        }
      }

      if (!isLandscapeExplorer || expandLandscapeHeader) {
Spacer(modifier = Modifier.height(10.dp))

NexoComicViewerHost()

NexoWebBrowserHost()

NexoExplorerTabBar(currentPath = uiState.currentPath, onNavigatePath = onNavigateToPath)

// Top Breadcrumb HUD bar + Layout & Hidden toggles

Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Box(modifier = Modifier.weight(1f)) {
          BreadcrumbBar(
            currentPath = uiState.currentPath,
            onNavigateToPath = onNavigateToPath,
            onNavigateUp = onNavigateUp,
          )
        }
        IconButton(
          onClick = {
            val entries = LayoutViewMode.entries
            val next = entries[(entries.indexOf(layoutViewMode) + 1) % entries.size]
            onChangeLayoutMode(next)
          },
          modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .testTag("toggle_layout_mode_button"),
        ) {
          Icon(
            imageVector = when (layoutViewMode) {
              LayoutViewMode.GRID -> NexoIcons.GridView
              LayoutViewMode.COMPACT -> NexoIcons.ViewList
              else -> NexoIcons.ViewAgenda
            },
            contentDescription = "${strings.viewModeCd}: ${strings.layoutLabel(layoutViewMode)}",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
        }
        IconButton(
          onClick = onToggleHidden,
          modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (uiState.showHiddenFiles) NexoTheme.semantic.tacticalSurface else MaterialTheme.colorScheme.surface)
            .border(
              1.dp,
              if (uiState.showHiddenFiles) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
              RoundedCornerShape(6.dp),
            )
            .testTag("toggle_hidden_files_button"),
        ) {
          Icon(
            imageVector = if (uiState.showHiddenFiles) NexoIcons.Visibility else NexoIcons.VisibilityOff,
            contentDescription = if (uiState.showHiddenFiles) strings.hideHiddenCd else strings.showHiddenCd,
            tint = if (uiState.showHiddenFiles) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
      }

// Volume chips when more than one volume exists

if (uiState.availableVolumes.size > 1) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          uiState.availableVolumes.forEach { (label, volPath) ->
            val active = uiState.currentPath.startsWith(volPath)
            FilterChip(
              selected = active,
              onClick = { onNavigateToPath(volPath) },
              label = { Text(label.uppercase(), maxLines = 1) },
              shape = RoundedCornerShape(6.dp),
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = NexoTheme.semantic.tacticalSurface,
                selectedLabelColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.surface,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
              ),
              border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = active,
                borderColor = MaterialTheme.colorScheme.outline,
                selectedBorderColor = MaterialTheme.colorScheme.primary,
              ),
            )
          }
        }
      }

Spacer(modifier = Modifier.height(10.dp))

// Chamfered Tactical Search / Filter bar

Surface(
        modifier = Modifier
          .fillMaxWidth()
          .height(46.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
          width = 1.dp,
          color = if (folderFilterQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
      ) {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = strings.search,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(10.dp))
          Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
          ) {
            if (folderFilterQuery.isEmpty()) {
              Text(
                text = strings.searchInFolderPlaceholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            BasicTextField(
              value = folderFilterQuery,
              onValueChange = { folderFilterQuery = it },
              singleLine = true,
              textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = JetBrainsMonoFamily,
              ),
              cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
              modifier = Modifier.fillMaxWidth(),
            )
          }
          if (folderFilterQuery.isNotEmpty()) {
            IconButton(
              onClick = { folderFilterQuery = "" },
              modifier = Modifier.size(28.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = strings.clear,
                tint = NexoTheme.semantic.evaRed,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }
      }

Spacer(modifier = Modifier.height(10.dp))

// Angular Sort chips + Hidden indicator

Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        SortBy.entries.forEach { option ->
          val selected = uiState.sortBy == option
          val arrow = if (selected) (if (uiState.sortAscending) " ↑" else " ↓") else ""
          Surface(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .clickable { onSortBy(option) }
              .testTag("sort_chip_${option.name}"),
            shape = RoundedCornerShape(6.dp),
            color = if (selected) NexoTheme.semantic.tacticalSurface else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
              width = 1.dp,
              color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            ),
          ) {
            Text(
              text = "${strings.sortLabel(option).uppercase()}$arrow",
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
          }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = if (strings.isEnglish) {
            if (uiState.showHiddenFiles) "HIDDEN:ON" else "HIDDEN:OFF"
          } else {
            if (uiState.showHiddenFiles) "OCULTOS:ON" else "OCULTOS:OFF"
          },
          style = MonoTnumStyle,
          color = if (uiState.showHiddenFiles) NexoTheme.semantic.evaOrange else MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
        )
      }


      }
// Clipboard banner if pending copy/move
      AnimatedVisibility(visible = uiState.clipboard != null) {
        uiState.clipboard?.let { clip ->
          BichromaticCard(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 6.dp),
            elevated = true,
            accentBorder = true,
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(NexoIcons.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(10.dp))
              val actionWord = if (clip.action == ClipboardAction.COPY) strings.actionCopy else strings.actionMove
              Text(
                text = strings.clipboardReady(actionWord, clip.paths.size),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Button(
                onClick = onPasteClipboard,
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
              ) {
                Text(strings.pasteHere.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
              }
              IconButton(onClick = onClearClipboard) {
                Icon(Icons.Rounded.Close, contentDescription = strings.cancel, tint = NexoTheme.semantic.evaRed)
              }
            }
          }
        }
      }

      // Compact ES-style selection header (actions live in the single bottom bar)
      AnimatedVisibility(visible = uiState.selectedPaths.isNotEmpty()) {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "${uiState.selectedPaths.size} / ${uiState.files.size}",
              style = MonoTnumStyle,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              TextButton(onClick = onSelectAll, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text(strings.selectAllCd.uppercase(), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
              }
              TextButton(onClick = onClearSelection, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text(strings.cancel.uppercase(), fontSize = 11.sp, color = NexoTheme.semantic.evaOrange)
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      val visibleFiles = remember(uiState.files, folderFilterQuery) {
        if (folderFilterQuery.isBlank()) {
          uiState.files
        } else {
          uiState.files.filter { it.name.contains(folderFilterQuery, ignoreCase = true) }
        }
      }
      if (uiState.isLoadingFiles) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
      } else if (uiState.errorMessage != null) {
        BichromaticCard(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(uiState.errorMessage, color = NexoTheme.semantic.evaRed, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onNavigateUp) {
              Text(strings.back.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
          }
        }
      } else if (visibleFiles.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconContainerBadge(icon = NexoIcons.Folder, contentDescription = null, size = 60.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = (if (folderFilterQuery.isNotBlank()) strings.noMatches else strings.emptyFolder).uppercase(),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        fun handleItemClick(item: LocalFileItem) {
        if (!item.isDirectory && item.name.endsWith(".torrent", ignoreCase = true) && uiState.selectedPaths.isEmpty()) {
            NexoNavBridge.openTorrentWithFile(item.path)
            return
        }
        if (!item.isDirectory && NexoComicViewerState.isSupportedComicOrWebp(java.io.File(item.path))) {
            NexoComicViewerState.open(java.io.File(item.path))
            return
        }
          if (uiState.selectedPaths.isNotEmpty()) {
            onToggleSelect(item.path)
          } else if (item.isDirectory) {
            folderFilterQuery = ""
            onNavigateToPath(item.path)
          } else {
            onOpenFile(item)
          }
        }

        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(bottom = 88.dp),
          verticalArrangement = Arrangement.spacedBy(if (layoutViewMode == LayoutViewMode.COMPACT) 6.dp else 10.dp),
        ) {
          if (layoutViewMode == LayoutViewMode.GRID) {
            val rows = visibleFiles.chunked(2)
            items(rows, key = { it.first().path }) { rowItems ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                rowItems.forEach { item ->
                  val isSelected = item.path in uiState.selectedPaths
                  Box(modifier = Modifier.weight(1f)) {
                    FileGridItemCard(
                      item = item,
                      isSelected = isSelected,
                      onClick = { handleItemClick(item) },
                      onLongClick = { onToggleSelect(item.path) },
                    )
                  }
                }
                if (rowItems.size == 1) {
                  Spacer(modifier = Modifier.weight(1f))
                }
              }
            }
          } else {
            items(visibleFiles, key = { it.path }) { item ->
              val isSelected = item.path in uiState.selectedPaths
              FileListItemCard(
                item = item,
                isSelected = isSelected,
                layoutViewMode = layoutViewMode,
                onClick = { handleItemClick(item) },
                onLongClick = { onToggleSelect(item.path) },
                onOpenWith = { onOpenWith(item) },
                onCopy = { onStageClipboard(listOf(item.path), ClipboardAction.COPY) },
                onMove = { onStageClipboard(listOf(item.path), ClipboardAction.MOVE) },
                onRename = { renameTarget = item },
                onRecycle = { onMoveToRecycleBin(listOf(item.path)) },
                onDeletePermanent = { onDeletePermanently(listOf(item.path)) },
                onShare = { onSharePaths(listOf(item.path)) },
                onProperties = { onOpenProperties(item.path) },
                onToggleBookmark = { onToggleBookmark(item) },
                onCompressZip = { zipTargetPaths = listOf(item.path) },
                onExtractArchive = { onExtractArchive(item) },
                onEditNote = { onOpenInNoteEditor(item) },
                onEditPhoto = { onOpenInPhotoEditor(item) },
                onExportSaf = { onExportSaf(item.path) },
                onUploadDrive = { onUploadToDrive(item) },
                onLockVault = { onLockInVault(item) },
              )
            }
          }
        }
      }
    }
  }

  // Dialogs
  if (showNewFolderDialog) {
    TextInputDialog(
      title = strings.newFolder,
      label = strings.folderNameLabel,
      confirmText = strings.create,
      onConfirm = {
        showNewFolderDialog = false
        onCreateFolder(it)
      },
      onDismiss = { showNewFolderDialog = false },
    )
  }

  if (showNewFileDialog) {
    TextInputDialog(
      title = strings.newFileTitle,
      label = strings.fileNameLabel,
      initialValue = "nota.txt",
      confirmText = strings.create,
      onConfirm = {
        showNewFileDialog = false
        onCreateTextFile(it)
      },
      onDismiss = { showNewFileDialog = false },
    )
  }

  zipTargetPaths?.let { targets ->
    val suggested = if (targets.size == 1) {
      val singleName = File(targets.first()).name
      "${singleName.substringBeforeLast('.', singleName)}.zip"
    } else {
      "archivo_${System.currentTimeMillis() % 1000}.zip"
    }
    CompressArchiveDialog(
      selectedCount = targets.size,
      defaultName = suggested,
      currentDirPath = uiState.currentPath,
      availableVolumes = uiState.availableVolumes,
      onCompressZip = { name, destDirPath ->
        zipTargetPaths = null
        onCreateZip(targets, name, destDirPath)
      },
      onDismiss = { zipTargetPaths = null },
    )
  }

  renameTarget?.let { target ->
    TextInputDialog(
      title = strings.renameTitle,
      label = strings.newNameLabel,
      initialValue = target.name,
      confirmText = strings.save,
      onConfirm = { newName ->
        renameTarget = null
        onRenameItem(target.path, newName)
      },
      onDismiss = { renameTarget = null },
    )
  }

  if (uiState.isLoadingProperties || uiState.activeProperties != null) {
    FilePropertiesDialog(
      properties = uiState.activeProperties,
      isLoading = uiState.isLoadingProperties,
      onDismiss = onDismissProperties,
    )
  }

  uiState.archiveInspectTarget?.let { archive ->
    ArchiveInspectorDialog(
      archiveItem = archive,
      entries = uiState.archiveEntries,
      isBusy = uiState.isArchiveBusy,
      availableVolumes = uiState.availableVolumes,
      onExtractHere = { onExtractArchive(archive) },
      onExtractToFolder = { destDir -> onExtractArchiveTo(archive, destDir) },
      onDismiss = onDismissArchiveInspector,
    )
  }
}

@Composable
private fun BreadcrumbBar(
  currentPath: String,
  onNavigateToPath: (String) -> Unit,
  onNavigateUp: () -> Unit,
) {
  val strings = LocalStrings.current
  val rootLabel = strings.myStorage
  val segments = remember(currentPath, rootLabel) {
    val parts = currentPath.split('/').filter { it.isNotEmpty() }
    val acc = mutableListOf<Pair<String, String>>()
    var built = ""
    for (part in parts) {
      built += "/$part"
      val display = if (built == "/storage/emulated/0") rootLabel else part
      acc.add(display to built)
    }
    val idxRoot = acc.indexOfFirst { it.second == "/storage/emulated/0" }
    if (idxRoot >= 0) acc.drop(idxRoot) else acc
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(
      onClick = onNavigateUp,
      modifier = Modifier
        .size(34.dp)
        .testTag("breadcrumb_back_button"),
    ) {
      Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.upOneLevel, tint = MaterialTheme.colorScheme.primary)
    }
    Icon(
      imageVector = NexoIcons.Folder,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(20.dp),
    )
    LazyRow(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      items(segments) { (label, fullPath) ->
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onNavigateToPath(fullPath) }
            .padding(horizontal = 4.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            NexoIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(2.dp))
          Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
              fontFamily = if (label == rootLabel) MaterialTheme.typography.titleSmall.fontFamily else JetBrainsMonoFamily,
            ),
            fontWeight = if (fullPath == currentPath) FontWeight.Bold else FontWeight.Normal,
            color = if (fullPath == currentPath) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridItemCard(
  item: LocalFileItem,
  isSelected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val strings = LocalStrings.current
  val glyph = remember(item.name, item.isDirectory, item.isHidden, isSelected) {
    when {
      isSelected -> Icons.Rounded.CheckCircle
      item.isDirectory -> NexoIcons.Folder
      FormatUtils.isImage(item.name) -> NexoIcons.Image
      FormatUtils.isVideo(item.name) -> NexoIcons.VideoLibrary
      FormatUtils.isAudio(item.name) -> NexoIcons.MusicNote
      FormatUtils.isPdf(item.name) -> NexoIcons.PictureAsPdf
      FormatUtils.isArchive(item.name) -> NexoIcons.FolderZip
      FormatUtils.isTextEditable(item.name) -> NexoIcons.Description
      else -> NexoIcons.InsertDriveFile
    }
  }
  BichromaticCard(
    modifier = Modifier
      .fillMaxWidth()
      .combinedClickable(onClick = onClick, onLongClick = onLongClick)
      .testTag("file_grid_item_${item.name}"),
    accentBorder = isSelected,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      IconContainerBadge(
        icon = glyph,
        contentDescription = item.name,
        size = 48.dp,
        highlighted = isSelected || item.isDirectory,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = item.name,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = if (item.isDirectory) strings.itemsCount(item.childCount) else FormatUtils.formatBytes(item.sizeBytes),
        style = MonoTnumStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItemCard(
  item: LocalFileItem,
  isSelected: Boolean,
  layoutViewMode: LayoutViewMode = LayoutViewMode.DETAILED,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onOpenWith: () -> Unit,
  onCopy: () -> Unit,
  onMove: () -> Unit,
  onRename: () -> Unit,
  onRecycle: () -> Unit,
  onDeletePermanent: () -> Unit,
  onShare: () -> Unit,
  onProperties: () -> Unit,
  onToggleBookmark: () -> Unit,
  onCompressZip: () -> Unit,
  onExtractArchive: () -> Unit,
  onEditNote: () -> Unit,
  onEditPhoto: () -> Unit = {},
  onExportSaf: () -> Unit,
  onUploadDrive: () -> Unit,
  onLockVault: () -> Unit,
) {
  val strings = LocalStrings.current
  var menuExpanded by remember { mutableStateOf(false) }

  val folderGlyph = remember(item.name, item.isDirectory, item.isHidden, isSelected) {
    when {
      isSelected -> Icons.Rounded.CheckCircle
      item.isDirectory -> when (item.name.lowercase()) {
        "dcim", "camera", "pictures", "imágenes" -> NexoIcons.PhotoCamera
        "download", "downloads", "descargas" -> NexoIcons.Download
        "documents", "documentos" -> NexoIcons.Description
        "music", "música", "audio" -> NexoIcons.MusicNote
        else -> NexoIcons.Folder
      }
      FormatUtils.isImage(item.name) -> NexoIcons.Image
      FormatUtils.isVideo(item.name) -> NexoIcons.VideoLibrary
      FormatUtils.isAudio(item.name) -> NexoIcons.MusicNote
      FormatUtils.isPdf(item.name) -> NexoIcons.PictureAsPdf
      FormatUtils.isArchive(item.name) -> NexoIcons.FolderZip
      FormatUtils.isTextEditable(item.name) -> NexoIcons.Description
      item.isHidden -> NexoIcons.VisibilityOff
      else -> NexoIcons.InsertDriveFile
    }
  }

  val barFraction = remember(item.childCount, item.sizeBytes, item.isDirectory) {
    if (item.isDirectory) {
      (0.24f + (item.childCount.coerceAtMost(80) / 110f)).coerceIn(0.24f, 0.85f)
    } else {
      val mb = (item.sizeBytes / (1024f * 1024f)).coerceIn(0f, 100f)
      (0.22f + (mb / 160f)).coerceIn(0.22f, 0.85f)
    }
  }

  val iconBoxSize = if (layoutViewMode == LayoutViewMode.COMPACT) 36.dp else 50.dp
  val verticalPad = if (layoutViewMode == LayoutViewMode.COMPACT) 8.dp else 12.dp
  val iconShape = RoundedCornerShape(if (layoutViewMode == LayoutViewMode.COMPACT) 6.dp else 8.dp)

  ChamferedTechCard(
    modifier = Modifier
      .fillMaxWidth()
      .combinedClickable(onClick = onClick, onLongClick = onLongClick)
      .testTag("file_item_${item.name}"),
    chamfer = if (layoutViewMode == LayoutViewMode.DETAILED) 8.dp else 4.dp,
    accentGlow = isSelected,
    showTechTicks = layoutViewMode == LayoutViewMode.DETAILED,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = verticalPad),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(iconBoxSize)
          .clip(iconShape)
          .background(
            when {
              isSelected -> (if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF111827))
              item.isDirectory -> MaterialTheme.colorScheme.secondary
              else -> NexoTheme.semantic.tacticalSurface
            },
          )
          .border(
            width = 1.dp,
            color = when {
              isSelected -> (if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF111827))
              item.isDirectory -> MaterialTheme.colorScheme.secondary
              else -> MaterialTheme.colorScheme.outline
            },
            shape = iconShape,
          ),
        contentAlignment = Alignment.Center,
      ) {
        NexoFileThumbnailOrFallback(file = java.io.File(item.path), size = iconBoxSize) { Icon(
          imageVector = folderGlyph,
          contentDescription = null,
          tint = when {
            isSelected -> MaterialTheme.colorScheme.onPrimary
            item.isDirectory -> MaterialTheme.colorScheme.onSecondary
            item.isHidden || FormatUtils.isAudio(item.name) -> (if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF111827))
            else -> MaterialTheme.colorScheme.onSurface
          },
          modifier = Modifier.size(if (layoutViewMode == LayoutViewMode.COMPACT) 20.dp else 26.dp),
        ) }
      }

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.name,
          style = if (layoutViewMode == LayoutViewMode.COMPACT) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = when {
            isSelected -> (if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF111827))
            item.isDirectory && !item.isHidden -> (if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF111827))
            item.isHidden -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
          },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (layoutViewMode != LayoutViewMode.COMPACT) {
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = if (item.isDirectory) {
              "${strings.filesCount(item.childCount)} · ${FormatUtils.formatDate(item.lastModified)}"
            } else {
              "${FormatUtils.formatBytes(item.sizeBytes)} · ${FormatUtils.formatDate(item.lastModified)}"
            },
            style = MonoTnumStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (layoutViewMode == LayoutViewMode.DETAILED) {
          Spacer(modifier = Modifier.height(8.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(4.dp)
              .clip(RoundedCornerShape(2.dp))
              .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth(barFraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                  Brush.horizontalGradient(
                    colors = listOf(
                      MaterialTheme.colorScheme.secondary,
                      (if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF111827)),
                    ),
                  ),
                ),
            )
          }
        }
      }

      Spacer(modifier = Modifier.width(10.dp))

      Box {
        if (false) {
IconButton(
          onClick = { menuExpanded = true },
          modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
        ) {
          Icon(
            imageVector = NexoIcons.MoreHoriz,
            contentDescription = "Opciones",
            tint = (if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF111827)),
            modifier = Modifier.size(18.dp),
          )
        }
}
        DropdownMenu(
          expanded = menuExpanded,
          onDismissRequest = { menuExpanded = false },
          modifier = Modifier
            .background(NexoTheme.semantic.modalSurface)
            .border(1.dp, (if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF111827)).copy(alpha = 0.7f)),
        ) {
          if (!item.isDirectory) {
            DropdownMenuItem(
              text = { Text(strings.openWith, color = (if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF111827)), fontWeight = FontWeight.SemiBold) },
              leadingIcon = { Icon(NexoIcons.OpenWith, null, tint = (if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF111827))) },
              onClick = { menuExpanded = false; onOpenWith() },
            )
          }
          if (!item.isDirectory && FormatUtils.isArchive(item.name)) {
            DropdownMenuItem(
              text = { Text(strings.extractHere, color = (if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF111827))) },
              leadingIcon = { Icon(NexoIcons.Archive, null, tint = (if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF111827))) },
              onClick = { menuExpanded = false; onExtractArchive() },
            )
          }
          if (!item.isDirectory && FormatUtils.isImage(item.name)) {
            DropdownMenuItem(
              text = { Text(strings.editInNexoPhotoEditor, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold) },
              leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.secondary) },
              onClick = { menuExpanded = false; onEditPhoto() },
            )
          }
          if (!item.isDirectory && FormatUtils.isTextEditable(item.name)) {
            DropdownMenuItem(
              text = { Text(strings.editInNexoNotes, color = (if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF111827))) },
              leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = (if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF111827))) },
              onClick = { menuExpanded = false; onEditNote() },
            )
          }
          DropdownMenuItem(
            text = { Text(strings.actionCopy, color = MaterialTheme.colorScheme.onSurface) },
            leadingIcon = { Icon(NexoIcons.ContentCopy, null, tint = MaterialTheme.colorScheme.onSurface) },
            onClick = { menuExpanded = false; onCopy() },
          )
          DropdownMenuItem(
            text = { Text(strings.actionMove, color = MaterialTheme.colorScheme.onSurface) },
            leadingIcon = { Icon(NexoIcons.ContentCut, null, tint = MaterialTheme.colorScheme.onSurface) },
            onClick = { menuExpanded = false; onMove() },
          )
          DropdownMenuItem(
            text = { Text(strings.rename, color = MaterialTheme.colorScheme.onSurface) },
            leadingIcon = { Icon(NexoIcons.DriveFileRenameOutline, null, tint = MaterialTheme.colorScheme.onSurface) },
            onClick = { menuExpanded = false; onRename() },
          )
          DropdownMenuItem(
            text = { Text(strings.compressZipCd, color = MaterialTheme.colorScheme.onSurface) },
            leadingIcon = { Icon(NexoIcons.FolderZip, null, tint = MaterialTheme.colorScheme.onSurface) },
            onClick = { menuExpanded = false; onCompressZip() },
          )
          DropdownMenuItem(
            text = { Text(strings.properties, color = MaterialTheme.colorScheme.onSurface) },
            leadingIcon = { Icon(Icons.Rounded.Info, null, tint = (if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF111827))) },
            onClick = { menuExpanded = false; onProperties() },
          )
          DropdownMenuItem(
            text = { Text(strings.bookmark, color = MaterialTheme.colorScheme.onSurface) },
            leadingIcon = { Icon(NexoIcons.BookmarkAdd, null, tint = MaterialTheme.colorScheme.secondary) },
            onClick = { menuExpanded = false; onToggleBookmark() },
          )
          if (!item.isDirectory) {
            DropdownMenuItem(
              text = { Text(strings.share, color = MaterialTheme.colorScheme.onSurface) },
              leadingIcon = { Icon(Icons.Rounded.Share, null, tint = MaterialTheme.colorScheme.onSurface) },
              onClick = { menuExpanded = false; onShare() },
            )
            DropdownMenuItem(
              text = { Text(strings.uploadToDrive, color = MaterialTheme.colorScheme.onSurface) },
              leadingIcon = { Icon(NexoIcons.CloudUpload, null, tint = (if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF111827))) },
              onClick = { menuExpanded = false; onUploadDrive() },
            )
            DropdownMenuItem(
              text = { Text(strings.exportViaSaf, color = MaterialTheme.colorScheme.onSurface) },
              leadingIcon = { Icon(NexoIcons.FileOpen, null, tint = MaterialTheme.colorScheme.onSurface) },
              onClick = { menuExpanded = false; onExportSaf() },
            )
            DropdownMenuItem(
              text = { Text(strings.lockInVault, color = MaterialTheme.colorScheme.secondary) },
              leadingIcon = { Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.secondary) },
              onClick = { menuExpanded = false; onLockVault() },
            )
          }
          DropdownMenuItem(
            text = { Text(strings.moveToTrashCd, color = NexoTheme.semantic.evaOrange) },
            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = NexoTheme.semantic.evaOrange) },
            onClick = { menuExpanded = false; onRecycle() },
          )
          DropdownMenuItem(
            text = { Text(strings.deleteForever, color = NexoTheme.semantic.evaRed) },
            leadingIcon = { Icon(NexoIcons.DeleteForever, null, tint = NexoTheme.semantic.evaRed) },
            onClick = { menuExpanded = false; onDeletePermanent() },
          )
        }
      }
    }
  }
}

@Composable
private fun ActionIconButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  isAlert: Boolean = false,
  onClick: () -> Unit,
) {
  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(6.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
      icon,
      contentDescription = label,
      tint = if (isAlert) NexoTheme.semantic.evaRed else (if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF111827)),
      modifier = Modifier.size(20.dp),
    )
    Text(
      label.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      color = if (isAlert) NexoTheme.semantic.evaRed else MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun FilesScreenPreview() {
  NexoFilesTheme {
    FilesScreen(
      uiState = ExplorerUiState(
        currentPath = "/storage/emulated/0",
        availableVolumes = listOf("Almacenamiento interno" to "/storage/emulated/0"),
        files = listOf(
          LocalFileItem("DCIM", "/storage/emulated/0/DCIM", true, 0L, System.currentTimeMillis(), childCount = 12),
          LocalFileItem("Download", "/storage/emulated/0/Download", true, 0L, System.currentTimeMillis(), childCount = 43),
          LocalFileItem(".nomedia", "/storage/emulated/0/.nomedia", false, 0L, System.currentTimeMillis(), isHidden = true),
          LocalFileItem("respaldo.zip", "/storage/emulated/0/respaldo.zip", false, 18_400_000L, System.currentTimeMillis()),
        ),
      ),
      onNavigateToPath = {},
      onNavigateUp = {},
      onToggleHidden = {},
      onSortBy = {},
      onToggleSelect = {},
      onSelectAll = {},
      onClearSelection = {},
      onOpenFile = {},
      onOpenInNoteEditor = {},
      onInspectArchive = {},
      onExtractArchive = {},
      onExtractArchiveTo = { _, _ -> },
      onCreateFolder = {},
      onCreateTextFile = {},
      onRenameItem = { _, _ -> },
      onStageClipboard = { _, _ -> },
      onPasteClipboard = {},
      onClearClipboard = {},
      onMoveToRecycleBin = {},
      onDeletePermanently = {},
      onSharePaths = {},
      onOpenProperties = {},
      onDismissProperties = {},
      onDismissArchiveInspector = {},
      onToggleBookmark = {},
      onCreateZip = { _, _, _ -> },
      onImportSaf = {},
      onExportSaf = {},
      onUploadToDrive = {},
      onLockInVault = {},
    )
  }
}
