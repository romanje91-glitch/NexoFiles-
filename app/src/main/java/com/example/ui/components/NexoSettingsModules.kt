package com.example.ui.components

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextOverflow
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NexoEvaThemeSelectorCard(compact: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themes = listOf(
        "EVA-00" to "Unidad 00 (Ámbar)",
        "EVA-01" to "Unidad 01 (Verde Berserk)",
        "EVA-02" to "Unidad 02 (Rojo NERV)",
        "EVA-03" to "Unidad 03 (Gris Militar)",
        "EVA-04" to "Unidad 04 (Plateado)",
        "EVA-05" to "Unidad 05 (Verde Oliva)",
        "EVA-08" to "Unidad 08 (Magenta)",
        "EVA-13" to "Unidad 13 (Celeste Núcleo)"
    )

    val themeColor = NexoEvaThemeManager.primaryColor
    val surfaceColor = NexoEvaThemeManager.surfaceColor
    var dropdownExpanded by remember { mutableStateOf(false) }

    val totalVirtual = 8000
    val currentIdx = themes.indexOfFirst { it.first == NexoEvaThemeManager.currentTheme }.coerceAtLeast(0)
    val startIdx = remember { (totalVirtual / 2) - ((totalVirtual / 2) % themes.size) + currentIdx }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIdx)
    val snapFling = rememberSnapFlingBehavior(lazyListState = listState)
    var userScrolled by remember { mutableStateOf(false) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            userScrolled = true
        } else if (userScrolled) {
            val info = listState.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val centered = info.visibleItemsInfo.minByOrNull { item ->
                abs((item.offset + item.size / 2) - viewportCenter)
            }
            if (centered != null) {
                val chosen = themes[centered.index % themes.size]
                if (chosen.first != NexoEvaThemeManager.currentTheme) {
                    NexoEvaThemeManager.setTheme(context, chosen.first)
                }
            }
            userScrolled = false
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, themeColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 0.dp else 14.dp, vertical = if (compact) 2.dp else 6.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            if (!compact) {
                Text(
                    text = "TEMA EVA (DESLIZA AL CENTRO O ELIGE ▼)",
                    color = themeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BoxWithConstraints(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    val chipWidth = 196.dp
                    val sidePad = ((maxWidth - chipWidth) / 2).coerceAtLeast(0.dp)
                    LazyRow(
                        state = listState,
                        flingBehavior = snapFling,
                        contentPadding = PaddingValues(horizontal = sidePad),
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(totalVirtual) { idx ->
                            val (id, label) = themes[idx % themes.size]
                            val selected = NexoEvaThemeManager.currentTheme == id
                            val paletteDot = NexoEvaThemeManager.units.firstOrNull { it.id == id }?.primary ?: themeColor
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) themeColor.copy(alpha = 0.24f) else Color(0xFF161B26),
                                border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) themeColor else Color(0xFF2E364F)),
                                modifier = Modifier
                                    .width(chipWidth)
                                    .clickable {
                                        NexoEvaThemeManager.setTheme(context, id)
                                        scope.launch { listState.animateScrollToItem(idx) }
                                        Toast.makeText(context, "Tema: $label", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(paletteDot))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        color = if (selected) themeColor else Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                Box {
                    IconButton(onClick = { dropdownExpanded = true }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Lista de temas", tint = themeColor)
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(Color(0xFF121622))
                    ) {
                        themes.forEachIndexed { tIdx, (id, label) ->
                            val selected = NexoEvaThemeManager.currentTheme == id
                            val paletteDot = NexoEvaThemeManager.units.firstOrNull { it.id == id }?.primary ?: themeColor
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(paletteDot))
                                        Text(
                                            text = if (selected) "✓ $label" else label,
                                            color = if (selected) themeColor else Color.White,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                    }
                                },
                                onClick = {
                                    dropdownExpanded = false
                                    NexoEvaThemeManager.setTheme(context, id)
                                    val base = (totalVirtual / 2) - ((totalVirtual / 2) % themes.size)
                                    scope.launch { listState.animateScrollToItem(base + tIdx) }
                                    Toast.makeText(context, "Tema: $label", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NexoAutoUpdateSettingsCard() {
    var autoUpdateEnabled by remember { mutableStateOf(true) }
    var checking by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val themeColor = NexoEvaThemeManager.primaryColor
    val surfaceColor = NexoEvaThemeManager.surfaceColor

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, themeColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ACTUALIZACIONES AUTOMÁTICAS", color = themeColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Verificar y descargar nuevas versiones", color = Color.White, fontSize = 12.sp)
                Switch(
                    checked = autoUpdateEnabled,
                    onCheckedChange = { autoUpdateEnabled = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = themeColor, checkedTrackColor = themeColor.copy(alpha = 0.5f))
                )
            }
            OutlinedButton(
                onClick = {
                    checking = true
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        checking = false
                        Toast.makeText(context, "NexoFiles está actualizado (v1.0 - NERV HUD)", Toast.LENGTH_LONG).show()
                    }, 1200)
                },
                border = BorderStroke(1.dp, themeColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (checking) "VERIFICANDO..." else "BUSCAR ACTUALIZACIÓN AHORA", color = themeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
