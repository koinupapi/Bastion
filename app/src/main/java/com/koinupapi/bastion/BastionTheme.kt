package com.koinupapi.bastion

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.color.utilities.Scheme

/**
 * Bastion's only branded source color. All fallback theme roles are generated
 * using Google's Material Color Utilities rather than manually selected.
 */
private const val BASTION_BLUE = 0xFF326FD3.toInt()

private fun generatedBastionScheme(dark: Boolean): ColorScheme {
    val scheme = if (dark) Scheme.dark(BASTION_BLUE) else Scheme.light(BASTION_BLUE)
    val colors = { argb: Int -> Color(argb) }
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = colors(scheme.primary),
        onPrimary = colors(scheme.onPrimary),
        primaryContainer = colors(scheme.primaryContainer),
        onPrimaryContainer = colors(scheme.onPrimaryContainer),
        secondary = colors(scheme.secondary),
        onSecondary = colors(scheme.onSecondary),
        secondaryContainer = colors(scheme.secondaryContainer),
        onSecondaryContainer = colors(scheme.onSecondaryContainer),
        tertiary = colors(scheme.tertiary),
        onTertiary = colors(scheme.onTertiary),
        tertiaryContainer = colors(scheme.tertiaryContainer),
        onTertiaryContainer = colors(scheme.onTertiaryContainer),
        background = colors(scheme.background),
        onBackground = colors(scheme.onBackground),
        surface = colors(scheme.surface),
        onSurface = colors(scheme.onSurface),
        surfaceVariant = colors(scheme.surfaceVariant),
        onSurfaceVariant = colors(scheme.onSurfaceVariant),
        outline = colors(scheme.outline),
        outlineVariant = colors(scheme.outlineVariant),
        inverseSurface = colors(scheme.inverseSurface),
        inverseOnSurface = colors(scheme.inverseOnSurface),
        inversePrimary = colors(scheme.inversePrimary),
        error = colors(scheme.error),
        onError = colors(scheme.onError),
        errorContainer = colors(scheme.errorContainer),
        onErrorContainer = colors(scheme.onErrorContainer),
    )
}

/** One theme entry point for Bastion and every integration. */
@Composable
fun BastionTheme(
    dark: Boolean,
    dynamic: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        remember(dark) { generatedBastionScheme(dark) }
    }
    MaterialExpressiveTheme(colorScheme = colors, content = content)
}
