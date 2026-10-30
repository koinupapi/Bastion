package com.koinupapi.bastion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Seerr Settings hierarchy: root -> app/server -> server category. */
@Composable
fun SeerrSettingsScreen(instance: ServiceConfig) {
    var section by androidx.compose.runtime.saveable.rememberSaveable(instance.id) {
        mutableStateOf("root")
    }
    BackHandler(section != "root") {
        section = if (section == "app" || section == "server") "root" else "server"
    }
    Column(verticalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
        if (section != "root") {
            TextButton(onClick = {
                section = if (section == "app" || section == "server") "root" else "server"
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Back")
            }
        }
        Text(when (section) {
            "root" -> "Settings"
            "app" -> "App Settings"
            "server" -> "Server Settings"
            else -> section.replaceFirstChar { it.uppercase() }
        }, style = MaterialTheme.typography.headlineSmall)
        when (section) {
            "root" -> {
                SettingsNavigationCard("App Settings", "Preferences stored only in Bastion") { section = "app" }
                SettingsNavigationCard("Server Settings", "Manage settings on this Seerr instance") { section = "server" }
            }
            "app" -> {
                Text("Bastion-only settings for this Seerr instance.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Requests per page is currently configured at the top of the Requests screen.",
                    style = MaterialTheme.typography.bodyMedium)
            }
            "server" -> {
                listOf("General", "Permissions", "Users", "Plex", "Radarr", "Sonarr",
                    "Notifications", "Jobs & Cache", "About").forEach { category ->
                    SettingsNavigationCard(category,
                        if (category == "Users") "View and manage Seerr accounts"
                        else "Settings category · Coming soon") { section = category.lowercase() }
                }
            }
            "users" -> SeerrUserDirectoryScreen(instance)
            else -> {
                Text("This Seerr settings category is not implemented yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsNavigationCard(title: String, subtitle: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BastionSpacing.xSmall)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
