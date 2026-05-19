package com.ansim.guardian.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = SurfaceWhite,
    primaryContainer = CriticalRedLight,
    background = BackgroundLight,
    surface = SurfaceWhite,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun AnsimTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = AnsimTypography,
        content = content
    )
}
