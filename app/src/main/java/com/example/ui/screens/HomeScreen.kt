package com.example.ui.screens

import com.example.NexoNavBridge
import com.example.NexoGlobeIcon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data.db.BookmarkEntity
import com.example.data.model.AllSettingsState
import com.example.data.model.FileCategory
import com.example.data.model.LocalFileItem
import com.example.data.model.StorageStats
import com.example.ui.components.BichromaticCard
import com.example.ui.components.IconContainerBadge
import com.example.ui.components.NexoIcons
import com.example.ui.components.PermissionBannerCard
import com.example.ui.components.StorageCircularGauge
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoFilesTheme
import com.example.ui.theme.NexoTheme
import com.example.ui.viewmodel.ExplorerUiState
import com.example.util.FormatUtils

@Composable
fun HomeScreen(
  uiState: ExplorerUiState,
  bookmarks: List<BookmarkEntity>,
  allSettings: AllSettingsState = AllSettingsState(),
  onSearchQueryChange: (String) -> Unit,
  onQuickCleanClick: () -> Unit,
  onCategoryClick: (FileCategory) -> Unit,
  onOpenDrive: () -> Unit,
  onOpenViewOnPc: () -> Unit,
  onOpenSender: () -> Unit = {},
  onOpenNetwork: () -> Unit,
  onOpenAppManager: () -> Unit,
  onOpenNoteEditor: () -> Unit,
  onOpenDownloader: () -> Unit,
  onOpenVault: () -> Unit,
  onOpenRecycleBin: () -> Unit,
  onOpenQuickSettings: () -> Unit,
  onOpenDrawer: () -> Unit,
  onGrantAllFiles: () -> Unit,
  onGrantMedia: () -> Unit,
  onOpenLocalFile: (LocalFileItem) -> Unit,
  onOpenBookmarkPath: (String) -> Unit,
  onDeleteBookmark: (Long) -> Unit,
) {
  var searchExpanded by remember { mutableStateOf(false) }
  val strings = LocalStrings.current

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // NERV HUD Top Command Bar
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        IconButton(
          onClick = onOpenDrawer,
          modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .testTag("open_drawer_button"),
        ) {
          Icon(
            imageVector = NexoIcons.Menu,
            contentDescription = strings.openMenuCd,
            tint = MaterialTheme.colorScheme.primary,
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = strings.greetingHello.uppercase(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "NERV // STORAGE TELEMETRY",
            style = MonoTnumStyle,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
          )
        }
        IconButton(
              onClick = { NexoNavBridge.showWebBrowser = true },
              modifier = Modifier
                .padding(end = 4.dp)
                .size(40.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
            ) {
              NexoGlobeIcon(tint = MaterialTheme.colorScheme.primary, size = 20.dp)
            }
            if (allSettings.showSearchBarOnHome) {
          IconButton(
            onClick = {
              if (uiState.searchQuery.isNotEmpty()) {
                onSearchQueryChange("")
                searchExpanded = false
              } else {
                searchExpanded = !searchExpanded
              }
            },
            modifier = Modifier
              .size(44.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(if (searchExpanded || uiState.searchQuery.isNotEmpty()) NexoTheme.semantic.tacticalSurface else MaterialTheme.colorScheme.surface)
              .border(
                width = 1.dp,
                color = if (searchExpanded || uiState.searchQuery.isNotEmpty()) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(6.dp),
              )
              .testTag("home_search_toggle_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Search,
              contentDescription = strings.search,
              tint = MaterialTheme.colorScheme.primary,
            )
          }
        }
        IconButton(
          onClick = onOpenQuickSettings,
          modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .testTag("quick_settings_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Settings,
            contentDescription = strings.quickSettingsCd,
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }

    // Expandable Global instant search bar
    if (allSettings.showSearchBarOnHome && (searchExpanded || uiState.searchQuery.isNotEmpty())) {
      item {
        AnimatedVisibility(visible = true) {
          OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("global_search_input"),
            placeholder = {
              Text(
                text = strings.searchPlaceholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            },
            leadingIcon = {
              Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingIcon = {
              IconButton(
                onClick = {
                  onSearchQueryChange("")
                  searchExpanded = false
                },
              ) {
                Icon(Icons.Rounded.Close, contentDescription = strings.clear, tint = NexoTheme.semantic.evaRed)
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surface,
              unfocusedContainerColor = MaterialTheme.colorScheme.surface,
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              unfocusedBorderColor = MaterialTheme.colorScheme.outline,
              focusedTextColor = MaterialTheme.colorScheme.onSurface,
              unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
          )
        }
      }
    }

    // Instant Search Results
    if (uiState.searchQuery.isNotBlank()) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = strings.searchResultsCount(uiState.searchResults.size).uppercase(),
            style = MonoTnumStyle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
          if (uiState.isSearching) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
          }
        }
      }
      if (uiState.searchResults.isEmpty() && !uiState.isSearching) {
        item {
          BichromaticCard(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = strings.noMatches,
              modifier = Modifier.padding(16.dp),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        items(uiState.searchResults, key = { it.path }) { item ->
          CompactFileRow(item = item, onClick = { onOpenLocalFile(item) })
        }
      }
      return@LazyColumn
    }

    // Storage permission banner
    item {
      PermissionBannerCard(
        hasAllFiles = uiState.hasAllFilesAccess,
        onGrantAllFiles = onGrantAllFiles,
        onGrantMedia = onGrantMedia,
      )
    }

    // Hero Storage Telemetry Card + Primary Neon Green Quick Clean CTA
    item {
      val stats = uiState.storageStats
      val isHighUsage = stats.usedPercent >= 85
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
          1.dp,
          if (isHighUsage) NexoTheme.semantic.evaRed else MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
        ),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              Brush.verticalGradient(
                colors = listOf(
                  NexoTheme.semantic.tacticalSurface.copy(alpha = 0.65f),
                  MaterialTheme.colorScheme.surface,
                ),
              ),
            )
            .padding(horizontal = 20.dp, vertical = 20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          StorageCircularGauge(
            usedPercent = stats.usedPercent,
            size = 148.dp,
          )
          Spacer(modifier = Modifier.height(14.dp))
          Text(
            text = strings.usedOfTotal(FormatUtils.formatBytes(stats.usedBytes), FormatUtils.formatBytes(stats.totalBytes)),
            style = MonoTnumStyle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = (if (isHighUsage) strings.storageWarningMessage else strings.storageHealthyMessage),
            style = MaterialTheme.typography.bodySmall,
            color = if (isHighUsage) NexoTheme.semantic.evaOrange else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(16.dp))
          Button(
            onClick = onQuickCleanClick,
            modifier = Modifier
              .fillMaxWidth(0.86f)
              .height(46.dp)
              .testTag("home_quick_clean_button"),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
          ) {
            Text(
              text = strings.quickClean.uppercase(),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
              imageVector = NexoIcons.AutoAwesome,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }

    // Categories Grid (Chamfered EVA-01 HUD Badges)
    item {
      Text(
        text = strings.categoriesTitle.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
      )
      Spacer(modifier = Modifier.height(12.dp))
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          CategoryShortcutItem(
            label = strings.catImages,
            icon = NexoIcons.Image,
            tag = "cat_images",
            onClick = { onCategoryClick(FileCategory.IMAGES) },
          )
          CategoryShortcutItem(
            label = strings.catVideos,
            icon = NexoIcons.PhotoCamera,
            tag = "cat_videos",
            onClick = { onCategoryClick(FileCategory.VIDEOS) },
          )
          CategoryShortcutItem(
            label = strings.catDocs,
            icon = NexoIcons.Description,
            tag = "cat_docs",
            onClick = { onCategoryClick(FileCategory.DOCUMENTS) },
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          CategoryShortcutItem(
            label = strings.catMusic,
            icon = NexoIcons.MusicNote,
            tag = "cat_music",
            onClick = { onCategoryClick(FileCategory.MUSIC) },
          )
          CategoryShortcutItem(
            label = strings.catApps,
            icon = NexoIcons.AppsGrid,
            tag = "cat_apks",
            onClick = { onCategoryClick(FileCategory.APKS) },
          )
          CategoryShortcutItem(
            label = "Google Drive",
            icon = NexoIcons.Cloud,
            tag = "cat_drive",
            highlighted = true,
            badgeIcon = NexoIcons.CloudDone,
            onClick = onOpenDrive,
          )
        }
      }
    }

    // Quick Tools compact grid (3 rows x 3)
    item {
      Text(
        text = strings.quickToolsTitle.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
      )
      Spacer(modifier = Modifier.height(10.dp))
      val quickTools = listOf(
        Triple(strings.toolSender, NexoIcons.Send, onOpenSender),
        Triple(strings.toolViewOnPc, NexoIcons.Computer, onOpenViewOnPc),
        Triple(strings.toolLanSmb, NexoIcons.Router, onOpenNetwork),
        Triple(strings.toolDrive, NexoIcons.Cloud, onOpenDrive),
        Triple(strings.toolApps, NexoIcons.Android, onOpenAppManager),
        Triple(strings.toolDownloader, NexoIcons.Download, onOpenDownloader),
        Triple(strings.toolNotes, NexoIcons.EditNote, onOpenNoteEditor),
        Triple(strings.toolVault, Icons.Rounded.Lock, onOpenVault),
        Triple(strings.recycleBinTitle, NexoIcons.DeleteOutline, onOpenRecycleBin),
      )
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        quickTools.chunked(3).forEach { rowTools ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            rowTools.forEach { (title, icon, action) ->
              BichromaticCard(
                modifier = Modifier
                  .weight(1f)
                  .testTag("quick_tool_$title"),
                onClick = action,
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 6.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  IconContainerBadge(
                    icon = icon,
                    contentDescription = title,
                    size = 40.dp,
                    highlighted = action == onOpenDrive || action == onOpenViewOnPc || action == onOpenSender || action == onOpenVault,
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
      }
    }

    // Recent files grouped card
    if (allSettings.showNewFilesOnHome) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = strings.recentFilesTitle.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = strings.itemsCount(uiState.recentDiskFiles.size),
            style = MonoTnumStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (uiState.recentDiskFiles.isEmpty()) {
          BichromaticCard(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = if (strings.isEnglish) {
                "No recent files detected yet. Grant storage access to browse your folders."
              } else {
                "Aún no hay archivos recientes detectados. Concede acceso al almacenamiento para explorar tus carpetas."
              },
              modifier = Modifier.padding(16.dp),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          BichromaticCard(modifier = Modifier.fillMaxWidth()) {
            val displayed = uiState.recentDiskFiles.take(6)
            displayed.forEachIndexed { idx, file ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { onOpenLocalFile(file) }
                  .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = when {
                      file.isHidden -> NexoIcons.Visibility
                      file.isDirectory -> NexoIcons.Folder
                      else -> NexoIcons.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = if (file.isHidden) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                  )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = file.path,
                    style = MonoTnumStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
              if (idx < displayed.lastIndex) {
                HorizontalDivider(
                  modifier = Modifier.padding(horizontal = 14.dp),
                  color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                )
              }
            }
          }
        }
      }
    }

    // Bookmarks
    item {
      Text(
        text = strings.bookmarksTitle.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
      )
    }

    if (bookmarks.isEmpty()) {
      item {
        BichromaticCard(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconContainerBadge(icon = NexoIcons.Bookmark, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = if (strings.isEnglish) {
                "Pin your favorite folders from the Files tab for one-tap access."
              } else {
                "Fija tus carpetas favoritas desde la pestaña Archivos para acceder en un toque."
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      items(bookmarks, key = { it.id }) { bm ->
        BichromaticCard(
          modifier = Modifier.fillMaxWidth(),
          onClick = { onOpenBookmarkPath(bm.path) },
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconContainerBadge(icon = NexoIcons.Folder, contentDescription = null, highlighted = true)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(bm.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
              Text(bm.path, style = MonoTnumStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = { onDeleteBookmark(bm.id) }) {
              Text((if (strings.isEnglish) "Remove" else "Quitar").uppercase(), color = NexoTheme.semantic.evaRed, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CategoryShortcutItem(
  label: String,
  icon: ImageVector,
  tag: String,
  highlighted: Boolean = false,
  badgeIcon: ImageVector = NexoIcons.Visibility,
  onClick: () -> Unit,
) {
  Column(
    modifier = Modifier
      .width(92.dp)
      .clip(RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .padding(vertical = 4.dp)
      .testTag(tag),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier.size(64.dp),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier = Modifier
          .size(60.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(
            Brush.radialGradient(
              colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                NexoTheme.semantic.tacticalSurface,
              ),
            ),
          )
          .border(
            width = 1.dp,
            color = if (highlighted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(12.dp),
          ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = label,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(28.dp),
        )
      }
      Box(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .offset(x = (-1).dp, y = (-1).dp)
          .size(20.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(MaterialTheme.colorScheme.surface)
          .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = badgeIcon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.size(12.dp),
        )
      }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = label.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onBackground,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
fun CompactFileRow(
  item: LocalFileItem,
  onClick: () -> Unit,
) {
  val strings = LocalStrings.current
  BichromaticCard(
    modifier = Modifier.fillMaxWidth(),
    onClick = onClick,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconContainerBadge(
        icon = if (item.isDirectory) NexoIcons.Folder else NexoIcons.InsertDriveFile,
        contentDescription = null,
        highlighted = item.isDirectory,
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.name,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = if (item.isDirectory) strings.itemsCount(item.childCount) else "${FormatUtils.formatBytes(item.sizeBytes)} · ${FormatUtils.formatDate(item.lastModified)}",
          style = MonoTnumStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenDarkPreview() {
  NexoFilesTheme(themeMode = com.example.data.model.ThemeMode.DARK) {
    HomeScreen(
      uiState = ExplorerUiState(
        storageStats = StorageStats(
          totalBytes = 222L * 1024 * 1024 * 1024,
          usedBytes = 130L * 1024 * 1024 * 1024,
          freeBytes = 92L * 1024 * 1024 * 1024,
          hasAllFilesAccess = true,
        ),
        recentDiskFiles = listOf(
          LocalFileItem("Factura_Nov.pdf", "/storage/emulated/0/Download/Factura_Nov.pdf", false, 450_000L, System.currentTimeMillis()),
          LocalFileItem("Backup_App.apk", "/storage/emulated/0/Download/Backup_App.apk", false, 12_400_000L, System.currentTimeMillis()),
        ),
      ),
      bookmarks = listOf(BookmarkEntity(1, "Descargas", "/storage/emulated/0/Download")),
      onSearchQueryChange = {},
      onQuickCleanClick = {},
      onCategoryClick = {},
      onOpenDrive = {},
      onOpenViewOnPc = {},
      onOpenNetwork = {},
      onOpenAppManager = {},
      onOpenNoteEditor = {},
      onOpenDownloader = {},
      onOpenVault = {},
      onOpenRecycleBin = {},
      onOpenQuickSettings = {},
      onOpenDrawer = {},
      onGrantAllFiles = {},
      onGrantMedia = {},
      onOpenLocalFile = {},
      onOpenBookmarkPath = {},
      onDeleteBookmark = {},
    )
  }
}
