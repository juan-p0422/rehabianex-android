package com.example.rehabianex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = RehabianexColors.GreenMain,
    onPrimary = RehabianexColors.GrayCard,
    primaryContainer = RehabianexColors.GreenSoft,
    onPrimaryContainer = RehabianexColors.GreenDark,
    secondary = RehabianexColors.BlueSoft,
    onSecondary = RehabianexColors.GrayCard,
    secondaryContainer = RehabianexColors.BlueLight,
    onSecondaryContainer = RehabianexColors.Black,
    error = RehabianexColors.Red,
    background = RehabianexColors.GrayBg,
    onBackground = RehabianexColors.Black,
    surface = RehabianexColors.GrayCard,
    onSurface = RehabianexColors.Black,
    outline = RehabianexColors.GrayBorder,
)

@Composable
fun RehabianexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = RehabianexTypography,
        content = content,
    )
}
