package com.example.ui.screens

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
  var errorMsg by remember(filePath) { mutableStateOf<String?>(null) }

  val exoPlayer = remember(filePath) {
    ExoPlayer.Builder(context).build().apply {
      val mediaItem = MediaItem.fromUri(Uri.fromFile(videoFile))
      setMediaItem(mediaItem)
      playWhenReady = true
      prepare()
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
