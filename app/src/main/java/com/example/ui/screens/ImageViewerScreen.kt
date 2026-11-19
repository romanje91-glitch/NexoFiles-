package com.example.ui.screens

import com.example.NexoNavBridge
import androidx.compose.runtime.DisposableEffect
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.BichromaticCard
import com.example.ui.components.NexoIcons
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme
import com.example.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private data class LoadedImageData(
  val bitmap: Bitmap?,
  val originalWidth: Int,
  val originalHeight: Int,
  val error: String? = null,
)

@Composable
fun ImageViewerScreen(
  initialPath: String,
  imagePaths: List<String>,
  isVaultMode: Boolean = false,
  onBack: () -> Unit,
  onShare: (File) -> Unit,
  onOpenExternal: (File) -> Unit,
  onEditPhoto: (File) -> Unit = {},
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current

  val gallery = remember(initialPath, imagePaths) {
    if (imagePaths.isNotEmpty() && initialPath in imagePaths) imagePaths else listOf(initialPath)
  }
  var currentIndex by remember(gallery, initialPath) {
    mutableIntStateOf(gallery.indexOf(initialPath).coerceAtLeast(0))
  }
  val activeFile = remember(gallery, currentIndex) {
    File(gallery.getOrElse(currentIndex) { initialPath })
  }

  var scale by remember(activeFile.absolutePath) { mutableFloatStateOf(1f) }
  var offset by remember(activeFile.absolutePath) { mutableStateOf(Offset.Zero) }
  var userRotation by remember(activeFile.absolutePath) { mutableFloatStateOf(0f) }
  var loaded by remember(activeFile.absolutePath) { mutableStateOf<LoadedImageData?>(null) }
  var isLoading by remember(activeFile.absolutePath) { mutableStateOf(true) }
  var showControls by remember { mutableStateOf(true) }
  LaunchedEffect(showControls) { NexoNavBridge.mediaImmersiveMode = !showControls }
  DisposableEffect(Unit) {
    NexoNavBridge.isMediaViewerOpen = true
    onDispose {
      NexoNavBridge.isMediaViewerOpen = false
      NexoNavBridge.mediaImmersiveMode = false
    }
  }

  LaunchedEffect(activeFile.absolutePath) {
    isLoading = true
    loaded = withContext(Dispatchers.IO) {
      decodeSampledImage(activeFile)
    }
    isLoading = false
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
  ) {
    if (showControls) {
Surface(
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 0.dp,
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, MaterialTheme.colorScheme.outline),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.testTag("image_viewer_back"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = strings.back,
            tint = MaterialTheme.colorScheme.primary,
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = activeFile.name,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
            if (isVaultMode) {
              Row(
                modifier = Modifier
                  .clip(CutCornerShape(4.dp))
                  .background(NexoTheme.semantic.evaRed.copy(alpha = 0.16f))
                  .border(1.dp, NexoTheme.semantic.evaRed, CutCornerShape(4.dp))
                  .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = NexoTheme.semantic.evaRed, modifier = Modifier.size(11.dp))
                Text(
                  text = if (strings.isEnglish) "VAULT" else "BÓVEDA",
                  style = MonoTnumStyle,
                  fontWeight = FontWeight.Bold,
                  color = NexoTheme.semantic.evaRed,
                )
              }
            }
          }
          val info = loaded?.let {
            if (it.originalWidth > 0 && it.originalHeight > 0) {
              "${it.originalWidth}×${it.originalHeight} · ${FormatUtils.formatBytes(activeFile.length())}"
            } else {
              FormatUtils.formatBytes(activeFile.length())
            }
          } ?: FormatUtils.formatBytes(activeFile.length())
          Text(
            text = if (gallery.size > 1) "$info · ${currentIndex + 1}/${gallery.size}" else info,
            style = MonoTnumStyle,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        // Strictly hide external sharing / chooser / public photo export when opened inside Vault
        if (!isVaultMode) {
          IconButton(
            onClick = { onEditPhoto(activeFile) },
            modifier = Modifier.testTag("image_viewer_edit_nexo"),
          ) {
            Icon(Icons.Rounded.Edit, contentDescription = strings.editInNexoPhotoEditor, tint = MaterialTheme.colorScheme.secondary)
          }
          IconButton(
            onClick = { onShare(activeFile) },
            modifier = Modifier.testTag("image_viewer_share"),
          ) {
            Icon(Icons.Rounded.Share, contentDescription = strings.share, tint = MaterialTheme.colorScheme.onSurface)
          }
          IconButton(
            onClick = { onOpenExternal(activeFile) },
            modifier = Modifier.testTag("image_viewer_external"),
          ) {
            Icon(NexoIcons.OpenInNew, contentDescription = strings.openExternal, tint = MaterialTheme.colorScheme.primary)
          }
        }
      }
    }
}

    // Canvas area
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.background)
        .pointerInput(activeFile.absolutePath) {
          detectTapGestures(onTap = { showControls = !showControls }, 
            onDoubleTap = {
              if (scale > 1.1f) {
                scale = 1f
                offset = Offset.Zero
              } else {
                scale = 2.5f
              }
            },
          )
        }
        .pointerInput(activeFile.absolutePath) {
          detectTransformGestures { _, pan, zoom, _ ->
            val newScale = (scale * zoom).coerceIn(1f, 5f)
            scale = newScale
            offset = if (newScale > 1.02f) {
              Offset(offset.x + pan.x, offset.y + pan.y)
            } else {
              Offset.Zero
            }
          }
        },
      contentAlignment = Alignment.Center,
    ) {
      val data = loaded
      when {
        isLoading -> {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        data == null || data.bitmap == null -> {
          BichromaticCard(modifier = Modifier.padding(24.dp)) {
            Column(
              modifier = Modifier.padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Icon(NexoIcons.Image, contentDescription = null, tint = NexoTheme.semantic.evaRed, modifier = Modifier.size(44.dp))
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = data?.error ?: strings.imageLoadError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              if (!isVaultMode) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { onOpenExternal(activeFile) }) {
                  Text(strings.tryExternalApp.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
        else -> {
          Image(
            bitmap = data.bitmap.asImageBitmap(),
            contentDescription = activeFile.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
              .fillMaxSize()
              .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
                rotationZ = userRotation
              },
          )
        }
      }
    }

    // Bottom HUD toolbar
    if (showControls) {
Surface(
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, MaterialTheme.colorScheme.outline),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (gallery.size > 1) {
          ViewerActionChip(
            icon = NexoIcons.ChevronLeft,
            contentDescription = strings.prevImageCd,
            enabled = currentIndex > 0,
            onClick = { if (currentIndex > 0) currentIndex-- },
            testTag = "image_viewer_prev",
          )
        }
        ViewerActionChip(
          icon = NexoIcons.ZoomOut,
          contentDescription = strings.zoomOutCd,
          enabled = scale > 1f,
          onClick = {
            scale = (scale - 0.5f).coerceAtLeast(1f)
            if (scale <= 1f) offset = Offset.Zero
          },
          testTag = "image_viewer_zoom_out",
        )
        Text(
          text = "${(scale * 100).roundToInt()}%",
          style = MonoTnumStyle,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
        )
        ViewerActionChip(
          icon = NexoIcons.ZoomIn,
          contentDescription = strings.zoomInCd,
          enabled = scale < 5f,
          onClick = { scale = (scale + 0.5f).coerceAtMost(5f) },
          testTag = "image_viewer_zoom_in",
        )
        ViewerActionChip(
          icon = NexoIcons.RotateRight,
          contentDescription = strings.rotate90Cd,
          enabled = loaded?.bitmap != null,
          onClick = { userRotation = (userRotation + 90f) % 360f },
          testTag = "image_viewer_rotate",
        )
        if (gallery.size > 1) {
          ViewerActionChip(
            icon = NexoIcons.ChevronRight,
            contentDescription = strings.nextImageCd,
            enabled = currentIndex < gallery.lastIndex,
            onClick = { if (currentIndex < gallery.lastIndex) currentIndex++ },
            testTag = "image_viewer_next",
          )
        }
      }
    }
}
  }
}

@Composable
private fun ViewerActionChip(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  contentDescription: String,
  enabled: Boolean = true,
  testTag: String,
  onClick: () -> Unit,
) {
  IconButton(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier
      .size(44.dp)
      .clip(CutCornerShape(6.dp))
      .background(if (enabled) NexoTheme.semantic.tacticalSurface else MaterialTheme.colorScheme.surface)
      .border(1.dp, if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline, CutCornerShape(6.dp))
      .testTag(testTag),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
      modifier = Modifier.size(20.dp),
    )
  }
}

private fun decodeSampledImage(file: File): LoadedImageData {
  if (!file.exists() || !file.canRead()) {
    return LoadedImageData(null, 0, 0, "El archivo de imagen no está accesible.")
  }
  return runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val origW = bounds.outWidth
    val origH = bounds.outHeight
    if (origW <= 0 || origH <= 0) {
      return LoadedImageData(null, 0, 0, "Formato de imagen no reconocido.")
    }
    var sample = 1
    val maxDim = 2048
    while ((origW / sample) > maxDim || (origH / sample) > maxDim) {
      sample *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply {
      inSampleSize = sample
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val rawBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
      ?: return LoadedImageData(null, origW, origH, "No se pudo cargar el bitmap.")

    val rotated = applyExifOrientation(rawBitmap, file.absolutePath)
    LoadedImageData(rotated, origW, origH)
  }.getOrElse { err ->
    LoadedImageData(null, 0, 0, err.localizedMessage ?: "Error abriendo imagen.")
  }
}

private fun applyExifOrientation(bitmap: Bitmap, path: String): Bitmap {
  val orientation = runCatching {
    ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
  }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

  val matrix = Matrix()
  when (orientation) {
    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
    else -> return bitmap
  }
  return runCatching {
    val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (result != bitmap) bitmap.recycle()
    result
  }.getOrDefault(bitmap)
}
