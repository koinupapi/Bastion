package com.koinupapi.bastion

import android.os.Build
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
        darkColorScheme()
    } else {
        lightColorScheme()
    }
    MaterialExpressiveTheme(colorScheme = colors, content = content)
}
