package com.koinupapi.bastion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

/**
 * Thin semantic adapters over Google's native Material 3 Expressive segmented lists.
 * Shape, spacing, interaction morphing, and colors are owned by Material 3.
 */
@Composable
fun BastionGroupedSettings(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        content = content,
    )
}

@Composable
fun BastionNavigationSetting(
    title: String,
    description: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = modifier,
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        supportingContent = { Text(description) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        content = { Text(title) },
    )
}

@Composable
fun BastionSwitchSetting(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = modifier,
        enabled = enabled,
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        supportingContent = if (description != null) { { Text(description) } } else null,
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        },
        content = { Text(title) },
    )
}

/**
 * Native Expressive navigation card for top-level settings destinations.
 * A single-item segmented group retains Google's shape and interaction behavior.
 */
@Composable
fun BastionSettingsDestination(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier,
        leadingContent = {
            androidx.compose.material3.Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
        supportingContent = { Text(description) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        content = { Text(title, style = MaterialTheme.typography.titleMedium) },
    )
}
