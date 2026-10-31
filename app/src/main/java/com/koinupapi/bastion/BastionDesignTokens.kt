package com.koinupapi.bastion

import androidx.compose.ui.unit.dp

/**
 * Shared layout tokens for Bastion's Material 3 design system.
 *
 * Color, typography and standard component shapes come directly from
 * MaterialTheme; these tokens cover repeated Bastion-specific dimensions.
 * Use a local value when a screen genuinely needs a different layout.
 */
object BastionSpacing {
    val xSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val xLarge = 24.dp
    val xxLarge = 32.dp
}

/** Common spacing for standard modal sheet content; bottom insets may vary. */
object BastionSheetDefaults {
    val horizontalPadding = BastionSpacing.xLarge
    val contentSpacing = BastionSpacing.large
}
