package com.appmovilidadclinica.passenger.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Mismo verde (emerald) que el panel admin (AdminPreset en admin/src/main.ts)
// — consistencia de marca entre el panel web y la app movil.
private val Emerald500 = Color(0xFF10B981)
private val Emerald700 = Color(0xFF047857)
private val Emerald200 = Color(0xFFA7F3D0)

private val LightColors = lightColorScheme(
    primary = Emerald700,
    onPrimary = Color.White,
    primaryContainer = Emerald200,
    secondary = Emerald500,
)

private val DarkColors = darkColorScheme(
    primary = Emerald200,
    onPrimary = Emerald700,
    primaryContainer = Emerald700,
    secondary = Emerald500,
)

@Composable
fun PassengerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
