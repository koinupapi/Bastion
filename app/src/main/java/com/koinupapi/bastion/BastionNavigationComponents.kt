package com.koinupapi.bastion

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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

/** One native Material 3 app bar shared by Bastion and every connected service. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BastionPageHeader(
    title: String,
    subtitle: String?,
    isBack: Boolean,
    onNavigationClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigationClick) {
                Icon(if (isBack) Icons.Default.ArrowBack else Icons.Default.Menu,
                    contentDescription = if (isBack) "Navigate back" else "Open services")
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}
