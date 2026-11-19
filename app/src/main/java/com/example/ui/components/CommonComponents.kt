package com.example.ui.components

import com.example.ui.components.NexoEvaThemeSelectorCard
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.CategoryBreakdown
import com.example.data.model.ThemeMode
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoFilesTheme
import com.example.ui.theme.NexoTheme
import com.example.util.FormatUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BichromaticCard(
  modifier: Modifier = Modifier,
  elevated: Boolean = false,
  accentBorder: Boolean = false,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = RoundedCornerShape(6.dp)
  val bgColor = if (elevated) NexoTheme.semantic.modalSurface else MaterialTheme.colorScheme.surface
  val borderBrush = if (accentBorder) {
    Brush.linearGradient(
      colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
      )
    )
  } else {
    Brush.linearGradient(
      colors = listOf(
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f),
        MaterialTheme.colorScheme.outline,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
      )
    )
  }
  Surface(
    modifier = modifier
      .clip(shape)
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    shape = shape,
    color = bgColor,
    contentColor = MaterialTheme.colorScheme.onSurface,
    border = BorderStroke(1.dp, borderBrush),
  ) {
    Column(content = content)
  }
}

@Composable
fun ChamferedTechCard(
  modifier: Modifier = Modifier,
  chamfer: Dp = 8.dp,
  accentGlow: Boolean = false,
  showTechTicks: Boolean = true,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = RoundedCornerShape(chamfer)
  val surfaceTop = NexoTheme.semantic.modalSurface
  val surfaceBottom = MaterialTheme.colorScheme.surface
  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary

  val borderBrush = if (accentGlow) {
    Brush.linearGradient(
      colors = listOf(primaryColor, secondaryColor, primaryColor),
    )
  } else {
    Brush.linearGradient(
      colors = listOf(
        primaryColor.copy(alpha = 0.75f),
        secondaryColor.copy(alpha = 0.75f),
      ),
    )
  }

  Surface(
    modifier = modifier
      .clip(shape)
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .drawWithContent {
        drawContent()
        if (showTechTicks) {
          val tickColor = if (accentGlow) primaryColor.copy(alpha = 0.85f) else secondaryColor.copy(alpha = 0.55f)
          val cPx = chamfer.toPx()
          val w = size.width
          val h = size.height
          drawLine(
            color = tickColor,
            start = Offset(cPx + 6.dp.toPx(), 3.dp.toPx()),
            end = Offset(cPx + 28.dp.toPx(), 3.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Square,
          )
          drawLine(
            color = tickColor,
            start = Offset(w - cPx - 28.dp.toPx(), h - 3.dp.toPx()),
            end = Offset(w - cPx - 6.dp.toPx(), h - 3.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Square,
          )
        }
      },
    shape = shape,
    color = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
    border = BorderStroke(1.dp, borderBrush),
  ) {
    Column(
      modifier = Modifier.background(
        Brush.verticalGradient(colors = listOf(surfaceTop, surfaceBottom)),
      ),
      content = content,
    )
  }
}

@Composable
fun IconContainerBadge(
  icon: ImageVector,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  size: Dp = 44.dp,
  highlighted: Boolean = false,
  containerColor: Color = NexoTheme.semantic.modalSurface,
  iconTint: Color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
) {
  val shape = RoundedCornerShape(5.dp)
  Box(
    modifier = modifier
      .size(size)
      .clip(shape)
      .background(containerColor)
      .border(
        width = 1.dp,
        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
        shape = shape,
      ),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = iconTint,
      modifier = Modifier.size(size * 0.52f),
    )
  }
}

@Composable
fun ThemeModeSelector(
  selectedMode: ThemeMode,
  onSelectMode: (ThemeMode) -> Unit,
  modifier: Modifier = Modifier,
  tagPrefix: String = "theme_option",
) {
  val strings = LocalStrings.current
  val outerShape = RoundedCornerShape(6.dp)
  val itemShape = RoundedCornerShape(4.dp)
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(outerShape)
      .background(MaterialTheme.colorScheme.background)
      .border(1.dp, MaterialTheme.colorScheme.secondary, outerShape)
      .padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    ThemeMode.entries.forEach { mode ->
      val selected = selectedMode == mode
      val icon = when (mode) {
        ThemeMode.LIGHT -> NexoIcons.LightMode
        ThemeMode.DARK -> NexoIcons.DarkMode
        ThemeMode.SYSTEM -> NexoIcons.BrightnessAuto
      }
      Row(
        modifier = Modifier
          .weight(1f)
          .clip(itemShape)
          .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
          .clickable { onSelectMode(mode) }
          .padding(vertical = 10.dp, horizontal = 6.dp)
          .testTag("${tagPrefix}_${mode.name}"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
          text = strings.themeLabel(mode).uppercase(),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
          color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
fun StorageCircularGauge(
  usedPercent: Int,
  modifier: Modifier = Modifier,
  size: Dp = 118.dp,
) {
  val animatedProgress by animateFloatAsState(
    targetValue = (usedPercent.coerceIn(0, 100) / 100f),
    animationSpec = spring(stiffness = 220f),
    label = "storageGauge",
  )
  val isAlert = usedPercent >= 85
  val trackColor = MaterialTheme.colorScheme.surfaceVariant
  val outlineColor = MaterialTheme.colorScheme.outline
  val secondaryColor = MaterialTheme.colorScheme.secondary
  val activeColor = if (isAlert) NexoTheme.semantic.evaRed else MaterialTheme.colorScheme.primary
  val activeSoftColor = if (isAlert) NexoTheme.semantic.evaOrange else NexoTheme.semantic.primarySoft

  Box(
    modifier = modifier.size(size),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.size(size)) {
      val strokeWidth = (size * 0.095f).toPx().coerceAtLeast(10.dp.toPx())
      val pad = strokeWidth / 2f
      val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
      val center = Offset(this.size.width / 2f, this.size.height / 2f)
      val innerRadius = (this.size.minDimension / 2f) - strokeWidth - 6.dp.toPx()

      if (innerRadius > 12f) {
        drawCircle(
          color = activeColor.copy(alpha = 0.32f),
          radius = innerRadius * 0.94f,
          center = center,
          style = Stroke(width = 1.2.dp.toPx()),
        )
        drawCircle(
          color = outlineColor.copy(alpha = 0.7f),
          radius = innerRadius * 0.72f,
          center = center,
          style = Stroke(width = 1.dp.toPx()),
        )
        for (i in 0 until 6) {
          val angleRad = (i * 60.0) * (Math.PI / 180.0)
          val nodeR = innerRadius * 0.83f
          val nx = center.x + (nodeR * cos(angleRad)).toFloat()
          val ny = center.y + (nodeR * sin(angleRad)).toFloat()
          drawCircle(
            color = secondaryColor.copy(alpha = 0.65f),
            radius = 2.8.dp.toPx(),
            center = Offset(nx, ny),
            style = Stroke(width = 1.1.dp.toPx()),
          )
        }
      }

      drawArc(
        color = trackColor,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(pad, pad),
        size = arcSize,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
      )

      drawArc(
        color = activeColor.copy(alpha = 0.22f),
        startAngle = -90f,
        sweepAngle = animatedProgress * 360f,
        useCenter = false,
        topLeft = Offset(pad, pad),
        size = arcSize,
        style = Stroke(width = strokeWidth + 6.dp.toPx(), cap = StrokeCap.Butt),
      )

      drawArc(
        brush = Brush.sweepGradient(
          colors = listOf(activeSoftColor, activeColor, activeColor),
        ),
        startAngle = -90f,
        sweepAngle = animatedProgress * 360f,
        useCenter = false,
        topLeft = Offset(pad, pad),
        size = arcSize,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
      )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "$usedPercent%",
        style = MonoTnumStyle.copy(
          fontSize = if (size >= 136.dp) MaterialTheme.typography.headlineLarge.fontSize else MaterialTheme.typography.headlineMedium.fontSize,
        ),
        fontWeight = FontWeight.Bold,
        color = activeColor,
      )
      Text(
        text = LocalStrings.current.usedLabel.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun CategoryBarChart(
  breakdown: CategoryBreakdown,
  deviceTotalBytes: Long = 0L,
  animationKey: Int = 0,
  modifier: Modifier = Modifier,
) {
  val strings = LocalStrings.current
  val values = listOf(
    strings.chartPhotos to breakdown.photosBytes,
    strings.chartVideos to breakdown.videosBytes,
    strings.chartApps to breakdown.appsBytes,
    strings.chartDocs to breakdown.docsBytes,
    strings.chartOthers to breakdown.othersBytes,
  )
  val sumCategories = values.sumOf { it.second }.coerceAtLeast(1L)
  val denomBytes = if (deviceTotalBytes > 0L) deviceTotalBytes else sumCategories
  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary
  val orangeColor = NexoTheme.semantic.evaOrange
  val outlineColor = MaterialTheme.colorScheme.outline
  val surfaceBg = MaterialTheme.colorScheme.surface
  val mutedLavender = MaterialTheme.colorScheme.onSurfaceVariant

  val animatables = remember { List(5) { Animatable(0f) } }
  LaunchedEffect(breakdown, denomBytes, animationKey) {
    animatables.forEach { it.snapTo(0f) }
    values.forEachIndexed { idx, (_, bytes) ->
      val targetRatio = if (bytes <= 0L) {
        0f
      } else {
        (bytes.toDouble() / denomBytes.toDouble()).toFloat().coerceIn(0.06f, 1f)
      }
      launch {
        delay(idx * 80L)
        animatables[idx].animateTo(
          targetValue = targetRatio,
          animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        )
      }
    }
  }

  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.Bottom,
  ) {
    values.forEachIndexed { idx, (label, bytes) ->
      val ratio = animatables[idx].value
      val pctValue = (bytes.toDouble() * 100.0) / denomBytes.toDouble()
      val pctLabel = when {
        bytes <= 0L -> "0%"
        pctValue < 0.1 -> "<0.1%"
        else -> "${String.format(Locale.US, "%.1f", pctValue)}%"
      }
      val barColor = when (idx) {
        0 -> primaryColor
        1 -> secondaryColor
        2 -> orangeColor
        3 -> primaryColor.copy(alpha = 0.72f)
        else -> mutedLavender.copy(alpha = 0.55f)
      }
      val isHighlightedBar = idx == 2 || idx == 3
      val barShape = RoundedCornerShape(4.dp)

      Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(138.dp)
            .clip(barShape)
            .background(surfaceBg)
            .border(
              width = 1.dp,
              color = if (isHighlightedBar) barColor.copy(alpha = 0.75f) else outlineColor,
              shape = barShape,
            ),
          contentAlignment = Alignment.BottomCenter,
        ) {
          Canvas(
            modifier = Modifier
              .fillMaxWidth()
              .height(138.dp),
          ) {
            val clipRect = Path().apply {
              moveTo(0f, 0f)
              lineTo(this@Canvas.size.width, 0f)
              lineTo(this@Canvas.size.width, this@Canvas.size.height)
              lineTo(0f, this@Canvas.size.height)
              close()
            }
            clipPath(clipRect) {
              if (ratio > 0f) {
                val barHeight = this@Canvas.size.height * ratio
                val barTop = this@Canvas.size.height - barHeight
                drawRect(
                  color = barColor,
                  topLeft = Offset(0f, barTop),
                  size = Size(this@Canvas.size.width, barHeight),
                )
              }
              val cols = 4
              val rows = 10
              val gridColor = outlineColor.copy(alpha = 0.55f)
              val colW = this@Canvas.size.width / cols
              val rowH = this@Canvas.size.height / rows
              for (c in 1 until cols) {
                val x = c * colW
                drawLine(color = gridColor, start = Offset(x, 0f), end = Offset(x, this@Canvas.size.height), strokeWidth = 0.75.dp.toPx())
              }
              for (r in 1 until rows) {
                val y = r * rowH
                drawLine(color = gridColor, start = Offset(0f, y), end = Offset(this@Canvas.size.width, y), strokeWidth = 0.75.dp.toPx())
              }
            }
          }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = label.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = FormatUtils.formatBytes(bytes),
          style = MonoTnumStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = pctLabel,
          style = MonoTnumStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
          fontWeight = FontWeight.Bold,
          color = barColor,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
fun PermissionBannerCard(
  hasAllFiles: Boolean,
  onGrantAllFiles: () -> Unit,
  onGrantMedia: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (hasAllFiles) return
  val strings = LocalStrings.current
  BichromaticCard(
    modifier = modifier.fillMaxWidth(),
    elevated = true,
    accentBorder = true,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconContainerBadge(
        icon = NexoIcons.Security,
        contentDescription = strings.permissionCd,
        highlighted = true,
        iconTint = NexoTheme.semantic.evaOrange,
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = strings.permissionTitle.uppercase(),
          style = MaterialTheme.typography.titleSmall,
          color = NexoTheme.semantic.evaOrange,
        )
        Text(
          text = strings.permissionDesc,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = onGrantAllFiles,
            modifier = Modifier.testTag("grant_all_files_button"),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(4.dp),
          ) {
            Text(strings.grantAccess.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
          }
          OutlinedButton(
            onClick = onGrantMedia,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
            shape = RoundedCornerShape(4.dp),
          ) {
            Text(strings.multimedia.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
          }
        }
      }
    }
  }
}

/**
 * Reusable 3x3 Angular Tactical Pattern Grid shared between SettingsScreen (Vault setup/verification)
 * and VaultScreen (lock prompt when active lock method is PATTERN).
 */
@Composable
fun TacticalPatternGrid(
  selectedNodes: List<Int>,
  onNodeTap: (Int) -> Unit,
  onReset: () -> Unit,
  isError: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary
  val errorColor = MaterialTheme.colorScheme.error
  val activeBorder = if (isError) errorColor else primaryColor

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    for (row in 0 until 3) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        for (col in 0 until 3) {
          val nodeId = row * 3 + col + 1
          val orderIdx = selectedNodes.indexOf(nodeId)
          val selected = orderIdx >= 0
          val nodeShape = RoundedCornerShape(6.dp)
          Box(
            modifier = Modifier
              .size(56.dp)
              .clip(nodeShape)
              .background(
                when {
                  selected && isError -> errorColor.copy(alpha = 0.25f)
                  selected -> primaryColor.copy(alpha = 0.22f)
                  else -> MaterialTheme.colorScheme.surface
                }
              )
              .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) activeBorder else secondaryColor.copy(alpha = 0.65f),
                shape = nodeShape,
              )
              .clickable { onNodeTap(nodeId) }
              .testTag("pattern_node_$nodeId"),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = if (selected) "${orderIdx + 1}" else "•",
              style = MonoTnumStyle,
              fontWeight = FontWeight.Bold,
              color = if (selected) activeBorder else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "NODOS: ${selectedNodes.size}/4+ [${selectedNodes.joinToString("-")}]",
        style = MonoTnumStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
        color = if (isError) errorColor else MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (selectedNodes.isNotEmpty()) {
        TextButton(onClick = onReset) {
          Text("REINICIAR", style = MaterialTheme.typography.labelSmall, color = NexoTheme.semantic.evaOrange)
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsSheet(
  showHiddenFiles: Boolean,
  hasAllFilesAccess: Boolean,
  webServerPort: Int,
  driveEmail: String?,
  themeMode: ThemeMode,
  onSelectThemeMode: (ThemeMode) -> Unit,
  onToggleHidden: (Boolean) -> Unit,
  onGrantStorage: () -> Unit,
  onOpenSafPicker: () -> Unit,
  onOpenDrive: () -> Unit,
  onOpenRecycleBin: () -> Unit,
  onOpenFullSettings: () -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = NexoTheme.semantic.modalSurface,
    contentColor = MaterialTheme.colorScheme.onSurface,
    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = strings.quickSettingsTitle.uppercase(),
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.primary,
        )
        OutlinedButton(
          onClick = {
            onDismiss()
            onOpenFullSettings()
          },
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
          shape = RoundedCornerShape(4.dp),
        ) {
          Icon(NexoIcons.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(strings.settingsBtn.uppercase(), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
        }
      }
      Text(
        text = strings.unlockedFeaturesNote,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      HorizontalDivider(color = MaterialTheme.colorScheme.outline)

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(strings.appThemeTitle.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        ThemeModeSelector(
          selectedMode = themeMode,
          onSelectMode = onSelectThemeMode,
          tagPrefix = "quick_theme",
        )
      }

      NexoEvaThemeSelectorCard(compact = true)
Spacer(modifier = Modifier.height(6.dp))
Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconContainerBadge(icon = NexoIcons.Visibility, contentDescription = null, highlighted = showHiddenFiles)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(strings.hiddenFilesTitle.uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
          Text(strings.hiddenFilesDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
          checked = showHiddenFiles,
          onCheckedChange = onToggleHidden,
          modifier = Modifier.testTag("quick_settings_hidden_switch"),
          colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
          ),
        )
      }

      BichromaticCard(
        elevated = true,
        accentBorder = hasAllFilesAccess,
        onClick = onGrantStorage,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("quick_settings_storage_access"),
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconContainerBadge(
            icon = if (hasAllFilesAccess) NexoIcons.LockOpen else NexoIcons.Storage,
            contentDescription = null,
            highlighted = hasAllFilesAccess,
          )
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(strings.storagePermTitle.uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
              if (hasAllFilesAccess) strings.fullAccessActive else strings.tapToEnableAccess,
              style = MaterialTheme.typography.bodySmall,
              color = if (hasAllFilesAccess) MaterialTheme.colorScheme.primary else NexoTheme.semantic.evaOrange,
            )
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
          onClick = {
            onDismiss()
            onOpenSafPicker()
          },
          modifier = Modifier.weight(1f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
          shape = RoundedCornerShape(4.dp),
        ) {
          Icon(NexoIcons.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(strings.safPickerBtn.uppercase(), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
        OutlinedButton(
          onClick = {
            onDismiss()
            onOpenDrive()
          },
          modifier = Modifier.weight(1f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
          shape = RoundedCornerShape(4.dp),
        ) {
          Icon(NexoIcons.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(driveEmail?.substringBefore('@') ?: "GOOGLE DRIVE", color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
      }

      Button(
        onClick = {
          onDismiss()
          onOpenRecycleBin()
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(4.dp),
      ) {
        Text(strings.openRecycleAndPort(webServerPort).uppercase(), style = MonoTnumStyle, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun CommonComponentsHudPreview() {
  NexoFilesTheme(themeMode = ThemeMode.DARK) {
    Surface(color = MaterialTheme.colorScheme.background) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ThemeModeSelector(selectedMode = ThemeMode.DARK, onSelectMode = {})
        StorageCircularGauge(usedPercent = 58)
        CategoryBarChart(
          breakdown = CategoryBreakdown(
            photosBytes = 6_000_000_000L,
            videosBytes = 8_000_000_000L,
            appsBytes = 120_000_000L,
            docsBytes = 450_000_000L,
            othersBytes = 2_400_000_000L,
          )
        )
      }
    }
  }
}
