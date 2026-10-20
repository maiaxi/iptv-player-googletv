package com.maiaxi.iptvplayer.views

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.darkColorScheme

private val colors = darkColorScheme(
    primary = Color(0xFFB4A7FF),
    onPrimary = Color(0xFF1D1065),
    background = Color(0xFF0B0D10),
    surface = Color(0xFF0B0D10),
    surfaceVariant = Color(0xFF22262E),
    onSurfaceVariant = Color(0xFFB8BCC6),
)

/** TV safe area: overscan can hide the edges of the screen. */
val SafeHorizontal = 48.dp
val SafeVertical = 24.dp

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            colors = SurfaceDefaults.colors(containerColor = colors.background),
        ) {
            content()
        }
    }
}
