package com.koinupapi.bastion

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Native Material 3 settings building blocks.
 *
 * Group position is a layout concern; switches, radio buttons and checkboxes
 * remain native Material 3 controls with their own interaction behavior.
 */
object BastionSettingsDefaults {
    val itemGap = 3.dp
    // Group geometry remains isolated until the native segmented API is verified.
    val outerCorner = 28.dp
    val innerCorner = 6.dp
    val horizontalPadding = 16.dp
    val verticalPadding = 12.dp

    fun itemShape(index: Int, count: Int): Shape {
        require(count > 0 && index in 0 until count)
        return RoundedCornerShape(
            topStart = if (index == 0) outerCorner else innerCorner,
            topEnd = if (index == 0) outerCorner else innerCorner,
            bottomStart = if (index == count - 1) outerCorner else innerCorner,
            bottomEnd = if (index == count - 1) outerCorner else innerCorner,
        )
    }
}

@Composable
fun BastionGroupedSettings(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(BastionSettingsDefaults.itemGap), content = content)
}

@Composable
fun BastionNavigationSetting(
    title: String,
    description: String,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(description) },
            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
    }
}

@Composable
fun BastionSwitchSetting(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = if (description != null) { { Text(description) } } else null,
            trailingContent = {
                Switch(checked = checked, onCheckedChange = null, enabled = enabled)
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
    }
}
