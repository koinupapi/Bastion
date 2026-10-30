package com.koinupapi.bastion

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/** A destination's content and selection are owned by its screen, not the shared navigation UI. */
data class BastionNavDestination(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/** One native Material 3 navigation bar for Bastion and connected services. */
@Composable
fun BastionBottomNavigation(
    destinations: List<BastionNavDestination>,
    showAllLabels: Boolean,
) {
    NavigationBar {
        destinations.forEach { destination ->
            NavigationBarItem(
                selected = destination.selected,
                onClick = destination.onClick,
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
                alwaysShowLabel = showAllLabels,
            )
        }
    }
}

/** Shared native drawer surface; individual drawers provide their own destinations and content. */
@Composable
fun BastionNavigationDrawerSheet(content: @Composable ColumnScope.() -> Unit) {
    ModalDrawerSheet(content = content)
}
