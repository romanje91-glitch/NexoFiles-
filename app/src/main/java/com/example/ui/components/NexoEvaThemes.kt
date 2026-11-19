package com.example.ui.components

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

data class EvaUnitPalette(
    val id: String,
    val label: String,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val bgPrimary: Color,
    val surfaceCard: Color,
    val surfaceTactical: Color,
    val border: Color
)

object NexoEvaThemeManager {
    private const val PREFS = "nexo_eva_theme_prefs"
    private const val KEY_THEME = "eva_unit_theme"

    var currentTheme by mutableStateOf("EVA-01")

    val units = listOf(
        EvaUnitPalette("EVA-00", "Unidad 00 / EVA-00 (Ámbar • Azul • Gris)", Color(0xFFE09F00), Color(0xFF2B6CB0), Color(0xFF94A3B8), Color(0xFF12100B), Color(0xFF1D1912), Color(0xFF262118), Color(0xFF7C5E14)),
        EvaUnitPalette("EVA-01", "Unidad 01 / EVA-01 (Morado • Verde • Ámbar)", Color(0xFF00E676), Color(0xFF9D4EDD), Color(0xFFFF9100), Color(0xFF0A0D14), Color(0xFF131826), Color(0xFF1C2233), Color(0xFF5A2D8C)),
        EvaUnitPalette("EVA-02", "Unidad 02 / EVA-02 (Rojo • Naranja • Oro)", Color(0xFFFF2A54), Color(0xFFFF7B00), Color(0xFFFFD166), Color(0xFF14090B), Color(0xFF221014), Color(0xFF2E161B), Color(0xFF8C1D35)),
        EvaUnitPalette("EVA-03", "Unidad 03 / EVA-03 (Acero • Cian • Carmesí)", Color(0xFF3B82F6), Color(0xFF94A3B8), Color(0xFFEF4444), Color(0xFF0B0E14), Color(0xFF151A24), Color(0xFF1F2633), Color(0xFF475569)),
        EvaUnitPalette("EVA-04", "Unidad 04 / EVA-04 (Cromo • Celeste • Grafito)", Color(0xFF38BDF8), Color(0xFF64748B), Color(0xFFCBD5E1), Color(0xFF0E1117), Color(0xFF171C26), Color(0xFF222938), Color(0xFF52607A)),
        EvaUnitPalette("EVA-05", "Unidad 05 / EVA-05 (Oliva • Ámbar • Arena)", Color(0xFF4ADE80), Color(0xFFF59E0B), Color(0xFF94A3B8), Color(0xFF0B1310), Color(0xFF13221C), Color(0xFF1C3028), Color(0xFF2D6A4F)),
        EvaUnitPalette("EVA-08", "Unidad 08 / EVA-08 (Rosa • Cian • Dorado)", Color(0xFFFF4D94), Color(0xFF06B6D4), Color(0xFFFBBF24), Color(0xFF140A12), Color(0xFF22121E), Color(0xFF2F1929), Color(0xFF8C2558)),
        EvaUnitPalette("EVA-13", "Unidad 13 / EVA-13 (Violeta • Oro • Cian)", Color(0xFFC084FC), Color(0xFFF59E0B), Color(0xFF38BDF8), Color(0xFF0D0914), Color(0xFF171124), Color(0xFF221936), Color(0xFF6D28D9))
    )

    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        currentTheme = sp.getString(KEY_THEME, "EVA-01") ?: "EVA-01"
    }

    fun setTheme(context: Context, themeName: String) {
        currentTheme = themeName
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_THEME, themeName).apply()
    }

    val activePalette: EvaUnitPalette
        get() = units.firstOrNull { it.id == currentTheme } ?: units[1]

    val primaryColor: Color get() = activePalette.primary
    val surfaceColor: Color get() = activePalette.surfaceCard

    fun adaptivePrimary(isDark: Boolean): Color {
        val p = activePalette.primary
        if (isDark) return p
        // Oscurecer en modo Día para que nunca se pierda sobre blanco
        return Color(
            red = (p.red * 0.62f).coerceIn(0f, 0.72f),
            green = (p.green * 0.62f).coerceIn(0f, 0.72f),
            blue = (p.blue * 0.62f).coerceIn(0f, 0.72f),
            alpha = 1f
        )
    }

    fun adaptiveSecondary(isDark: Boolean): Color {
        val s = activePalette.secondary
        if (isDark) return s
        return Color(
            red = (s.red * 0.68f).coerceIn(0f, 0.75f),
            green = (s.green * 0.68f).coerceIn(0f, 0.75f),
            blue = (s.blue * 0.68f).coerceIn(0f, 0.75f),
            alpha = 1f
        )
    }
}
