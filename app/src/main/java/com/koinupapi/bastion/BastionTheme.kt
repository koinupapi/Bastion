package com.koinupapi.bastion

import android.os.Build
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Application-wide native Material 3 Expressive theme entry point.
 *
 * Preserve the existing dynamic-color and light/dark behavior while keeping
 * service screens independent of the user's selected color source.
 * Additional theme modes and semantic tokens can be introduced here later.
 */
@Composable
fun BastionTheme(
    dark: Boolean,
    dynamic: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) {
        darkColorScheme(
            primary = Color(0xFFADC6FF), onPrimary = Color(0xFF002E69),
            primaryContainer = Color(0xFF16488F), onPrimaryContainer = Color(0xFFD8E3FF),
            secondary = Color(0xFFBCC6DC), onSecondary = Color(0xFF263144),
            secondaryContainer = Color(0xFF3D475A), onSecondaryContainer = Color(0xFFD8E2F9),
            tertiary = Color(0xFFDABDE4), onTertiary = Color(0xFF3D2947),
            tertiaryContainer = Color(0xFF553F5F), onTertiaryContainer = Color(0xFFF7D8FF),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF326FD3), onPrimary = Color.White,
            primaryContainer = Color(0xFFD8E3FF), onPrimaryContainer = Color(0xFF001A41),
            secondary = Color(0xFF565F71), onSecondary = Color.White,
            secondaryContainer = Color(0xFFDAE2F9), onSecondaryContainer = Color(0xFF131C2B),
            tertiary = Color(0xFF715574), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFAD7F9), onTertiaryContainer = Color(0xFF29132D),
        )
    }
    MaterialExpressiveTheme(colorScheme = colors, content = content)
}
