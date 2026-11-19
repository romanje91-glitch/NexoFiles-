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
        EvaUnitPalette("EVA-00", "Unidad 00 / EVA-00 (Prototipo Ámbar)", Color(0xFFFFB703), Color(0xFFFB8500), Color(0xFFE0E1DD), Color(0xFF12100B), Color(0xFF1D1912), Color(0xFF262118), Color(0xFF6B5314)),
        EvaUnitPalette("EVA-01", "Unidad 01 / EVA-01 (Shinji • Morado/Verde)", Color(0xFF00FF66), Color(0xFF9D4EDD), Color(0xFFFF6D00), Color(0xFF0A0D14), Color(0xFF121722), Color(0xFF1A2030), Color(0xFF4A2574)),
        EvaUnitPalette("EVA-02", "Unidad 02 / EVA-02 (Asuka • Rojo/Naranja)", Color(0xFFFF2A54), Color(0xFFFF7B00), Color(0xFFFFB703), Color(0xFF14090B), Color(0xFF221014), Color(0xFF2E161B), Color(0xFF7A1C2E)),
        EvaUnitPalette("EVA-03", "Unidad 03 / EVA-03 (Negro Táctico/Acero)", Color(0xFF4EA8DE), Color(0xFFADB5BD), Color(0xFFE63946), Color(0xFF0B0D10), Color(0xFF14181D), Color(0xFF1D2229), Color(0xFF3D4957)),
        EvaUnitPalette("EVA-04", "Unidad 04 / EVA-04 (Plateado/Cromo)", Color(0xFFE9ECEF), Color(0xFF90E0EF), Color(0xFFCED4DA), Color(0xFF0F1114), Color(0xFF181B20), Color(0xFF22262D), Color(0xFF5C677D)),
        EvaUnitPalette("EVA-05", "Unidad 05 / EVA-05 (Rebuild • Verde/Ámbar)", Color(0xFF80ED99), Color(0xFFFFB703), Color(0xFF57CC99), Color(0xFF0B1310), Color(0xFF12201B), Color(0xFF1A2D26), Color(0xFF2D6A4F)),
        EvaUnitPalette("EVA-08", "Unidad 08 / EVA-08 (Rebuild • Rosa/Cian)", Color(0xFFFF4D94), Color(0xFF00F5D4), Color(0xFFFFB703), Color(0xFF140A12), Color(0xFF22121E), Color(0xFF2F1929), Color(0xFF802352)),
        EvaUnitPalette("EVA-13", "Unidad 13 / EVA-13 (Rebuild • Violeta/Oro)", Color(0xFFC77DFF), Color(0xFFFFD166), Color(0xFF00BBF9), Color(0xFF0D0914), Color(0xFF171124), Color(0xFF211833), Color(0xFF5A189A))
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
}
