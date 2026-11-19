package com.example.ui.screens

import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.ui.CaptionStyleCompat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.NexoNavBridge
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ui.components.BichromaticCard
import com.example.ui.components.NexoIcons
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.MonoTnumStyle
import com.example.ui.theme.NexoTheme
import com.example.util.FormatUtils
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
  filePath: String,
  isVaultMode: Boolean = false,
  onBack: () -> Unit,
  onShare: (File) -> Unit,
  onOpenExternal: (File) -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val strings = LocalStrings.current

  val videoFile = remember(filePath) { File(filePath) }
  var isReady by remember(filePath) { mutableStateOf(false) }
  var isPlaying by remember(filePath) { mutableStateOf(false) }
  var isMuted by remember(filePath) { mutableStateOf(false) }
  var durationMs by remember(filePath) { mutableLongStateOf(0L) }
  var positionMs by remember(filePath) { mutableLongStateOf(0L) }
  var isSeeking by remember { mutableStateOf(false) }
  var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showControlsOverlay by remember { mutableStateOf(true) }
  LaunchedEffect(showControlsOverlay, isPlaying) {
    NexoNavBridge.mediaImmersiveMode = !showControlsOverlay
    if (showControlsOverlay && isPlaying) {
      delay(3500L)
      showControlsOverlay = false
    }
  }
    var showVlcTrackDialog by remember { mutableStateOf(false) }
  var subtitleScale by remember { mutableStateOf(1.0f) }
  var subtitleColorIdx by remember { mutableStateOf(0) }
  var loadedSubName by remember { mutableStateOf<String?>(null) }
  var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }


  DisposableEffect(Unit) {
    NexoNavBridge.isMediaViewerOpen = true
    onDispose {
      NexoNavBridge.isMediaViewerOpen = false
      NexoNavBridge.mediaImmersiveMode = false
    }
  }
  var errorMsg by remember(filePath) { mutableStateOf<String?>(null) }

  val exoPlayer = remember(filePath) {
    ExoPlayer.Builder(context).build().apply {
      val mediaItem = MediaItem.fromUri(Uri.fromFile(videoFile))
      setMediaItem(mediaItem)
      playWhenReady = true
      prepare()
    }
  }

  val subPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { subUri ->
    if (subUri != null) {
      val pos = exoPlayer.currentPosition
      val wasPlaying = exoPlayer.isPlaying
      val mime = if (subUri.toString().endsWith(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
      val subCfg = MediaItem.SubtitleConfiguration.Builder(subUri)
        .setMimeType(mime)
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build()
      val newItem = MediaItem.Builder()
        .setUri(Uri.fromFile(videoFile))
        .setSubtitleConfigurations(listOf(subCfg))
        .build()
      exoPlayer.setMediaItem(newItem, pos)
      exoPlayer.prepare()
      exoPlayer.playWhenReady = wasPlaying
      loadedSubName = subUri.lastPathSegment ?: "Subtítulo externo"
      Toast.makeText(context, "Subtítulos cargados", Toast.LENGTH_SHORT).show()
    }
  }


  DisposableEffect(exoPlayer) {
    val listener = object : Player.Listener {
      override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
          Player.STATE_READY -> {
            isReady = true
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            errorMsg = null
          }
          Player.STATE_ENDED -> {
            isPlaying = false
            positionMs = exoPlayer.duration.coerceAtLeast(0L)
          }
          else -> Unit
        }
      }

      override fun onIsPlayingChanged(playing: Boolean) {
        isPlaying = playing
      }

      override fun onPlayerError(error: PlaybackException) {
        isReady = false
        isPlaying = false
        errorMsg = strings.videoLoadError(error.errorCode, 0)
      }
    }
    exoPlayer.addListener(listener)
    onDispose {
      exoPlayer.removeListener(listener)
      runCatching { exoPlayer.stop() }
      runCatching { exoPlayer.release() }
    }
  }

  LaunchedEffect(exoPlayer, isPlaying, isReady, isSeeking) {
    while (true) {
      if (isReady && !isSeeking) {
        durationMs = exoPlayer.duration.coerceAtLeast(durationMs)
        positionMs = exoPlayer.currentPosition.coerceIn(0L, durationMs.coerceAtLeast(1L))
      }
      delay(250L)
    }
  }

  fun togglePlayPause() {
    if (!isReady) return
    if (exoPlayer.isPlaying) {
      exoPlayer.pause()
    } else {
      if (exoPlayer.playbackState == Player.STATE_ENDED) {
        exoPlayer.seekTo(0L)
      }
      exoPlayer.play()
    }
  }

  fun seekBy(deltaMs: Long) {
    if (!isReady) return
    val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, durationMs.coerceAtLeast(0L))
    exoPlayer.seekTo(target)
    positionMs = target
  }

  fun toggleMute() {
    isMuted = !isMuted
    exoPlayer.volume = if (isMuted) 0f else 1f
  }

  fun cycleSpeed() {
    val next = when {
      playbackSpeed < 1.25f -> 1.5f
      playbackSpeed < 1.75f -> 2.0f
      else -> 1.0f
    }
    playbackSpeed = next
    exoPlayer.setPlaybackSpeed(next)
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
  ) {
    // NERV HUD Top bar
    if (showControlsOverlay) {
Surface(
      color = NexoTheme.semantic.modalSurface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.testTag("video_player_back"),
        ) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back, tint = MaterialTheme.colorScheme.primary)
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = videoFile.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = FormatUtils.formatBytes(videoFile.length()),
              style = MonoTnumStyle,
              color = MaterialTheme.colorScheme.primary,
            )
            if (isVaultMode) {
              Text(
                text = strings.vaultInternalBadge.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = NexoTheme.semantic.evaOrange,
              )
            }
          }
        }
        if (!isVaultMode) {
          IconButton(onClick = { onShare(videoFile) }) {
            Icon(Icons.Rounded.Share, contentDescription = strings.share, tint = MaterialTheme.colorScheme.onSurface)
          }
          IconButton(onClick = { onOpenExternal(videoFile) }) {
            Icon(NexoIcons.OpenInNew, contentDescription = strings.openExternal, tint = MaterialTheme.colorScheme.secondary)
          }
        }
      }
    }
}

    // Video viewport
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.background),
      contentAlignment = Alignment.Center,
    ) {
      if (errorMsg != null) {
        BichromaticCard(modifier = Modifier.padding(24.dp), elevated = true, accentBorder = true) {
          Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(NexoIcons.VideoLibrary, contentDescription = null, tint = NexoTheme.semantic.evaRed, modifier = Modifier.size(44.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(errorMsg.orEmpty(), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            if (!isVaultMode) {
              Spacer(modifier = Modifier.height(8.dp))
              TextButton(onClick = { onOpenExternal(videoFile) }) {
                Text(strings.openWithExternalPlayer.uppercase(), color = MaterialTheme.colorScheme.primary)
              }
            }
          }
        }
      } else {
        AndroidView(
          factory = { ctx ->
            PlayerView(ctx).apply {
          useController = false
          setOnClickListener { showControlsOverlay = !showControlsOverlay }
          playerViewRef = this
              player = exoPlayer
              useController = false
              resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
              layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
              )
            }
          },
          update = { pv ->
            if (pv.player !== exoPlayer) {
              pv.player = exoPlayer
            }
          },
          modifier = Modifier.fillMaxSize(),
        )
        if (!isReady) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("DECRYPTING / BUFFERING STREAM...", style = MonoTnumStyle, color = MaterialTheme.colorScheme.primary)
          }
        }
      }
    }

    // Custom NERV HUD Player Controls Bar
    if (showControlsOverlay) {
Surface(
      color = NexoTheme.semantic.modalSurface,
      shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = formatVideoTime(positionMs.toInt()),
            style = MonoTnumStyle,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "${playbackSpeed}x",
            style = MonoTnumStyle,
            color = NexoTheme.semantic.evaOrange,
          )
          Text(
            text = formatVideoTime(durationMs.toInt()),
            style = MonoTnumStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Slider(
          value = if (durationMs > 0L) positionMs.toFloat() else 0f,
          onValueChange = { value ->
            isSeeking = true
            positionMs = value.toLong()
          },
          onValueChangeFinished = {
            exoPlayer.seekTo(positionMs)
            isSeeking = false
          },
          valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
          enabled = isReady,
          colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.outline,
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("video_player_seekbar"),
        )
        Spacer(modifier = Modifier.height(4.dp))
        val btnShape = RoundedCornerShape(6.dp)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = ::toggleMute,
            modifier = Modifier
              .size(44.dp)
              .clip(btnShape)
              .background(MaterialTheme.colorScheme.surface)
              .border(1.dp, if (isMuted) NexoTheme.semantic.evaRed else MaterialTheme.colorScheme.secondary, btnShape)
              .testTag("video_player_mute"),
          ) {
            Icon(
              imageVector = if (isMuted) NexoIcons.VolumeOff else NexoIcons.VolumeUp,
              contentDescription = if (isMuted) strings.unmuteCd else strings.muteCd,
              tint = if (isMuted) NexoTheme.semantic.evaRed else MaterialTheme.colorScheme.primary,
            )
          }
          IconButton(
            onClick = { seekBy(-10_000L) },
            enabled = isReady,
            modifier = Modifier
              .size(44.dp)
              .clip(btnShape)
              .background(MaterialTheme.colorScheme.surface)
              .border(1.dp, MaterialTheme.colorScheme.outline, btnShape)
              .testTag("video_player_rewind"),
          ) {
            Icon(NexoIcons.FastRewind, contentDescription = strings.rewind10Cd, tint = MaterialTheme.colorScheme.onSurface)
          }
          IconButton(
            onClick = ::togglePlayPause,
            enabled = isReady,
            modifier = Modifier
              .size(56.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.primary)
              .testTag("video_player_play_pause"),
          ) {
            Icon(
              imageVector = if (isPlaying) NexoIcons.Pause else NexoIcons.PlayArrow,
              contentDescription = if (isPlaying) strings.audioPauseCd else strings.audioPlayCd,
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(30.dp),
            )
          }
          IconButton(
            onClick = { seekBy(10_000L) },
            enabled = isReady,
            modifier = Modifier
              .size(44.dp)
              .clip(btnShape)
              .background(MaterialTheme.colorScheme.surface)
              .border(1.dp, MaterialTheme.colorScheme.outline, btnShape)
              .testTag("video_player_forward"),
          ) {
            Icon(NexoIcons.FastForward, contentDescription = strings.forward10Cd, tint = MaterialTheme.colorScheme.onSurface)
          }
          TextButton(
            onClick = ::cycleSpeed,
            modifier = Modifier
              .height(44.dp)
              .clip(btnShape)
              .background(MaterialTheme.colorScheme.surface)
              .border(1.dp, NexoTheme.semantic.evaOrange.copy(alpha = 0.7f), btnShape),
          ) {
            Text(
              text = "${playbackSpeed}X",
              style = MonoTnumStyle,
              fontWeight = FontWeight.Bold,
              color = NexoTheme.semantic.evaOrange,
            )
          }
        }
      }
    }
}
  }

  LaunchedEffect(subtitleScale, subtitleColorIdx, playerViewRef) {
    val pv = playerViewRef ?: return@LaunchedEffect
    val fg = when (subtitleColorIdx) {
      1 -> AndroidColor.YELLOW
      2 -> AndroidColor.CYAN
      else -> AndroidColor.WHITE
    }
    pv.subtitleView?.setStyle(
      CaptionStyleCompat(fg, AndroidColor.argb(150, 0, 0, 0), AndroidColor.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, AndroidColor.BLACK, null)
    )
    pv.subtitleView?.setFractionalTextSize(0.0533f * subtitleScale)
  }

  if (showControlsOverlay) {
    Box(modifier = Modifier.fillMaxSize().padding(top = 56.dp, end = 12.dp), contentAlignment = Alignment.TopEnd) {
      OutlinedButton(onClick = { showVlcTrackDialog = true }, shape = RoundedCornerShape(8.dp)) {
        Text("CC / AUDIO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
      }
    }
  }

  if (showVlcTrackDialog) {
    AlertDialog(
      onDismissRequest = { showVlcTrackDialog = false },
      title = { Text("Subtítulos y Audio (VLC)") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { subPickerLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
          ) { Text("📂 Cargar subtítulo (.srt / .vtt)") }
          loadedSubName?.let { Text("Activo: $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary) }
          Text("Tamaño de subtítulos: ${(subtitleScale * 100).toInt()}%", fontSize = 12.sp)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Pequeño" to 0.75f, "Normal" to 1.0f, "Grande" to 1.35f).forEach { (lbl, sc) ->
              OutlinedButton(onClick = { subtitleScale = sc }, modifier = Modifier.weight(1f)) { Text(lbl, fontSize = 10.sp) }
            }
          }
          Text("Color de subtítulos:", fontSize = 12.sp)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Blanco" to 0, "Amarillo" to 1, "Cian" to 2).forEach { (lbl, idx) ->
              OutlinedButton(onClick = { subtitleColorIdx = idx }, modifier = Modifier.weight(1f)) { Text(lbl, fontSize = 10.sp) }
            }
          }
          OutlinedButton(
            onClick = {
              val curOff = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
              exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !curOff)
                .build()
              Toast.makeText(context, if (curOff) "Subtítulos activados" else "Subtítulos ocultos", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
          ) { Text("Activar / Ocultar subtítulos") }
        }
      },
      confirmButton = {
        TextButton(onClick = { showVlcTrackDialog = false }) { Text("Cerrar") }
      }
    )
  }
}

private fun formatVideoTime(ms: Int): String {
  val totalSec = (ms / 1000).coerceAtLeast(0)
  val hours = totalSec / 3600
  val mins = (totalSec % 3600) / 60
  val secs = totalSec % 60
  return if (hours > 0) {
    String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
  } else {
    String.format(Locale.US, "%02d:%02d", mins, secs)
  }


}