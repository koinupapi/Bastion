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
import com.google.android.material.color.utilities.CorePalette

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
    // Derive every modern surface role from the selected seed's neutral tonal palette.
    val neutral = CorePalette.of(seed).n1
    val tone = { value: Int -> colors(neutral.tone(value)) }
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
        background = tone(if (dark) 6 else 98),
        onBackground = colors(scheme.onBackground),
        surface = tone(if (dark) 6 else 98),
        surfaceDim = tone(if (dark) 6 else 87),
        surfaceBright = tone(if (dark) 24 else 98),
        surfaceContainerLowest = tone(if (dark) 4 else 100),
        surfaceContainerLow = tone(if (dark) 10 else 96),
        surfaceContainer = tone(if (dark) 12 else 94),
        surfaceContainerHigh = tone(if (dark) 17 else 92),
        surfaceContainerHighest = tone(if (dark) 22 else 90),
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
