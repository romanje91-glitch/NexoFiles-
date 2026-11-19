package com.example.ui.theme

import com.example.ui.components.NexoEvaThemeManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemeMode

private val NexoHudDarkColorScheme = darkColorScheme(
  primary = NexoHudPalette.EvaGreen,
  onPrimary = NexoHudPalette.OnNeonBlack,
  primaryContainer = NexoHudPalette.SurfaceModal,
  onPrimaryContainer = NexoHudPalette.EvaGreen,
  secondary = NexoHudPalette.EvaPurple,
  onSecondary = NexoHudPalette.TextPrimary,
  secondaryContainer = NexoHudPalette.SurfaceTactical,
  onSecondaryContainer = NexoHudPalette.EvaPurpleBright,
  tertiary = NexoHudPalette.EvaOrange,
  onTertiary = NexoHudPalette.OnNeonBlack,
  tertiaryContainer = NexoHudPalette.SurfaceModal,
  onTertiaryContainer = NexoHudPalette.EvaOrange,
  background = NexoHudPalette.BgPrimary,
  onBackground = NexoHudPalette.TextPrimary,
  surface = NexoHudPalette.SurfaceCard,
  onSurface = NexoHudPalette.TextPrimary,
  surfaceVariant = NexoHudPalette.SurfaceModal,
  onSurfaceVariant = NexoHudPalette.TextSecondary,
  surfaceContainerLowest = NexoHudPalette.BgPrimary,
  surfaceContainerLow = NexoHudPalette.SurfaceCard,
  surfaceContainer = NexoHudPalette.SurfaceCard,
  surfaceContainerHigh = NexoHudPalette.SurfaceModal,
  surfaceContainerHighest = NexoHudPalette.SurfaceTactical,
  outline = NexoHudPalette.BorderSubtle,
  outlineVariant = NexoHudPalette.BorderViolet,
  error = NexoHudPalette.EvaRed,
  onError = NexoHudPalette.TextPrimary,
  errorContainer = NexoHudPalette.EvaRedDim,
  onErrorContainer = NexoHudPalette.TextPrimary,
)

private val NexoHudLightColorScheme = lightColorScheme(
  primary = NexoHudLightPalette.EvaGreen,
  onPrimary = NexoHudLightPalette.OnPrimaryLight,
  primaryContainer = NexoHudLightPalette.SurfaceTactical,
  onPrimaryContainer = NexoHudLightPalette.EvaGreen,
  secondary = NexoHudLightPalette.EvaPurple,
  onSecondary = NexoHudLightPalette.OnPrimaryLight,
  secondaryContainer = NexoHudLightPalette.SurfaceTactical,
  onSecondaryContainer = NexoHudLightPalette.EvaPurpleDeep,
  tertiary = NexoHudLightPalette.EvaOrange,
  onTertiary = NexoHudLightPalette.OnPrimaryLight,
  tertiaryContainer = NexoHudLightPalette.SurfaceTactical,
  onTertiaryContainer = NexoHudLightPalette.EvaOrange,
  background = NexoHudLightPalette.BgPrimary,
  onBackground = NexoHudLightPalette.TextPrimary,
  surface = NexoHudLightPalette.SurfaceCard,
  onSurface = NexoHudLightPalette.TextPrimary,
  surfaceVariant = NexoHudLightPalette.SurfaceTactical,
  onSurfaceVariant = NexoHudLightPalette.TextSecondary,
  surfaceContainerLowest = NexoHudLightPalette.SurfaceCard,
  surfaceContainerLow = NexoHudLightPalette.BgPrimary,
  surfaceContainer = NexoHudLightPalette.SurfaceCard,
  surfaceContainerHigh = NexoHudLightPalette.SurfaceModal,
  surfaceContainerHighest = NexoHudLightPalette.SurfaceTactical,
  outline = NexoHudLightPalette.BorderSubtle,
  outlineVariant = NexoHudLightPalette.BorderViolet,
  error = NexoHudLightPalette.EvaRed,
  onError = NexoHudLightPalette.OnPrimaryLight,
  errorContainer = NexoHudLightPalette.EvaRedSoft,
  onErrorContainer = NexoHudLightPalette.EvaRed,
)

private val DarkSemanticColors = NexoSemanticColors()

private val LightSemanticColors = NexoSemanticColors(
  evaGreen = NexoHudLightPalette.EvaGreen,
  evaPurple = NexoHudLightPalette.EvaPurple,
  evaRed = NexoHudLightPalette.EvaRed,
  evaOrange = NexoHudLightPalette.EvaOrange,
  warning = NexoHudLightPalette.EvaOrange,
  primarySoft = NexoHudLightPalette.EvaGreenSoft,
  secondarySoft = NexoHudLightPalette.EvaPurpleDeep,
  tacticalOrange = NexoHudLightPalette.EvaOrange,
  tacticalAmber = NexoHudLightPalette.EvaAmber,
  tacticalSurface = NexoHudLightPalette.SurfaceTactical,
  modalSurface = NexoHudLightPalette.SurfaceModal,
  hudBorder = NexoHudLightPalette.BorderSubtle,
)

/**
 * Angular chamfered geometry enforced globally across cards, dialogs, inputs, and buttons.
 */
val NexoHudShapes = Shapes(
  extraSmall = RoundedCornerShape(2.dp),
  small = RoundedCornerShape(4.dp),
  medium = RoundedCornerShape(6.dp),
  large = RoundedCornerShape(8.dp),
  extraLarge = RoundedCornerShape(10.dp),
)

val LocalNexoSemanticColors = staticCompositionLocalOf { NexoSemanticColors() }

object NexoTheme {
  val semantic: NexoSemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalNexoSemanticColors.current
}

@Composable
fun resolveDarkTheme(themeMode: ThemeMode): Boolean {
  return when (themeMode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
  }
}

@Composable
fun NexoFilesTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    darkTheme: Boolean = resolveDarkTheme(themeMode),
    content: @Composable () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.example.ui.components.NexoEvaThemeManager.init(context)
    }
    val eva = com.example.ui.components.NexoEvaThemeManager.activePalette
    val baseScheme = if (darkTheme) NexoHudDarkColorScheme else NexoHudLightColorScheme
    val colorScheme = baseScheme.copy(
        primary = eva.primary,
        secondary = eva.secondary,
        tertiary = eva.accent,
        background = if (darkTheme) eva.bgPrimary else baseScheme.background,
        surface = if (darkTheme) eva.surfaceCard else baseScheme.surface,
        surfaceVariant = if (darkTheme) eva.surfaceTactical else baseScheme.surfaceVariant,
        surfaceContainerLowest = if (darkTheme) eva.surfaceCard else baseScheme.surfaceContainerLowest,
        surfaceContainerLow = if (darkTheme) eva.bgPrimary else baseScheme.surfaceContainerLow,
        surfaceContainer = if (darkTheme) eva.surfaceCard else baseScheme.surfaceContainer,
        surfaceContainerHigh = if (darkTheme) eva.surfaceTactical else baseScheme.surfaceContainerHigh,
        surfaceContainerHighest = if (darkTheme) eva.surfaceTactical else baseScheme.surfaceContainerHighest,
        outline = if (darkTheme) eva.border else baseScheme.outline,
        outlineVariant = eva.secondary.copy(alpha = 0.65f)
    )
    val baseSemantic = if (darkTheme) DarkSemanticColors else LightSemanticColors
    val semanticColors = baseSemantic.copy(
        evaGreen = eva.primary,
        evaPurple = eva.secondary,
        evaOrange = eva.accent,
        warning = eva.accent,
        primarySoft = eva.primary.copy(alpha = 0.18f),
        secondarySoft = eva.secondary.copy(alpha = 0.22f),
        tacticalOrange = eva.accent,
        tacticalAmber = eva.accent,
        tacticalSurface = if (darkTheme) eva.surfaceTactical else baseSemantic.tacticalSurface,
        modalSurface = if (darkTheme) eva.surfaceCard else baseSemantic.modalSurface,
        hudBorder = if (darkTheme) eva.border else baseSemantic.hudBorder
    )
    CompositionLocalProvider(LocalNexoSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NexoTypography,
            shapes = NexoHudShapes,
            content = content,
        )
    }
}
