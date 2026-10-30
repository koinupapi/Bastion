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
data class BastionColorPreset(val name: String, val argb: Int)

val BastionColorPresets = listOf(
    BastionColorPreset("Bastion Blue", 0xFF326FD3.toInt()),
    BastionColorPreset("Purple", 0xFF7655B5.toInt()),
    BastionColorPreset("Green", 0xFF24845A.toInt()),
    BastionColorPreset("Red", 0xFFBC4050.toInt()),
    BastionColorPreset("Orange", 0xFFBD681F.toInt()),
    BastionColorPreset("Teal", 0xFF00877F.toInt()),
    BastionColorPreset("Yellow", 0xFFA47B00.toInt()),
    BastionColorPreset("Pink", 0xFFB64E82.toInt()),
)

private const val BASTION_BLUE = 0xFF326FD3.toInt()

private fun generatedBastionScheme(dark: Boolean, seed: Int): ColorScheme {
    val scheme = if (dark) Scheme.dark(seed) else Scheme.light(seed)
    val colors = { argb: Int -> Color(argb) }
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val charcoal = if (dark) Color(0xFF24272D) else colors(scheme.background)
    val raised = if (dark) Color(0xFF343840) else colors(scheme.surfaceVariant)
    val high = if (dark) Color(0xFF424750) else colors(scheme.surfaceVariant)
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
        background = charcoal,
        onBackground = colors(scheme.onBackground),
        surface = charcoal,
        surfaceDim = if (dark) charcoal else colors(scheme.surface),
        surfaceBright = if (dark) high else colors(scheme.surface),
        surfaceContainerLowest = if (dark) charcoal else colors(scheme.surface),
        surfaceContainerLow = if (dark) charcoal else colors(scheme.surface),
        surfaceContainer = raised,
        surfaceContainerHigh = high,
        surfaceContainerHighest = high,
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
    seed: Int = BASTION_BLUE,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        remember(dark, seed) { generatedBastionScheme(dark, seed) }
    }
    MaterialExpressiveTheme(colorScheme = colors, content = content)
}
