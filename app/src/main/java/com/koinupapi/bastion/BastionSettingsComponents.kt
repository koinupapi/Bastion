package com.koinupapi.bastion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
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
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        leadingContent = if (icon != null) {
            {
                Box(contentAlignment = Alignment.Center) {
                    Surface(shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer) {
                        Icon(icon, contentDescription = null,
                            modifier = Modifier.padding(12.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        } else null,
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
        verticalAlignment = Alignment.CenterVertically,
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
    modifier: Modifier = Modifier,
    index: Int = 0,
    count: Int = 1,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        verticalAlignment = Alignment.CenterVertically,
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier,
        leadingContent = {
            Box(contentAlignment = Alignment.Center) {
                Surface(shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(imageVector = icon, contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        },
        supportingContent = { Text(description) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        content = { Text(title, style = MaterialTheme.typography.titleMedium) },
    )
}

/** Native Expressive radio row for mutually exclusive settings. */
@Composable
fun BastionChoiceSetting(
    title: String,
    description: String,
    selected: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        checked = selected,
        onCheckedChange = { if (it) onClick() },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        verticalAlignment = Alignment.CenterVertically,
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = modifier,
        supportingContent = { Text(description) },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        content = { Text(title) },
    )
}

/** Native segmented surface for controls that do not fit a standard switch or navigation row. */
@Composable
fun BastionSettingsContent(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        verticalAlignment = Alignment.CenterVertically,
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier,
        content = content,
    )
}
