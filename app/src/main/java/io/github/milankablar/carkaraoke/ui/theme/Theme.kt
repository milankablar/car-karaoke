package io.github.milankablar.carkaraoke.ui.theme

import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA5E8CB), onPrimary = Color(0xFF07382C),
    secondaryContainer = Color(0xFF233D34), onSecondaryContainer = Color(0xFFCAEDDC),
    background = Color(0xFF101B17), surface = Color(0xFF101B17), surfaceContainerLow = Color(0xFF192820), surfaceContainer = Color(0xFF192820),
    onSurface = Color(0xFFEDF5EE), onBackground = Color(0xFFEDF5EE), onSurfaceVariant = Color(0xFFADC1B6)
)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF21634D), onPrimary = Color.White,
    secondaryContainer = Color(0xFFDCEFE3), onSecondaryContainer = Color(0xFF183C2B),
    background = Color(0xFFF7F9F3), surface = Color(0xFFF7F9F3), surfaceContainerLow = Color(0xFFECF1E9), surfaceContainer = Color(0xFFECF1E9),
    onSurface = Color(0xFF182C22), onBackground = Color(0xFF182C22), onSurfaceVariant = Color(0xFF55685D)
)

@Composable
fun MediaBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    amoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = if (amoled) colorScheme.copy(background = Color.Black, surface = Color.Black) else colorScheme,
        typography = Typography,
        content = content
    )
}