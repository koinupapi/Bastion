package com.koinupapi.bastion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/**
 * Settings are scoped to the selected Seerr instance.
 * Server categories are deliberately separate from Bastion's local preferences.
 */
@Composable
fun SeerrSettingsScreen(instance: ServiceConfig, section: String, onSectionChange: (String) -> Unit) {
    val mediaPages = listOf("Plex", "Radarr", "Sonarr", "Override Rules")
    val maintenancePages = listOf("Logs", "Scheduled Jobs", "Cache Management")
    val parent = when {
        section in mediaPages -> "Media Services"
        section in maintenancePages -> "Maintenance & Diagnostics"
        else -> "root"
    }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("bastion_seerr_local", android.content.Context.MODE_PRIVATE)
    }
    var requestsPageSize by remember(instance.id) {
        mutableIntStateOf(preferences.getInt("requests_page_size_${instance.id}", 25)
            .takeIf { it in listOf(25, 50, 100) } ?: 25)
    }

    Column(verticalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {

        when (section) {
            "root" -> {
                BastionGroupedSettings {
                    listOf(
                        "General" to "Discovery and application defaults",
                        "Access & Users" to "Sign-in, default permissions and request limits",
                        "Network" to "Network and security configuration",
                    ).forEachIndexed { index, entry ->
                        BastionNavigationSetting(entry.first, entry.second, index, 3,
                            icon = listOf(Icons.Default.Tune, Icons.Default.ManageAccounts, Icons.Default.Language)[index]) {
                            onSectionChange(entry.first)
                        }
                    }
                }
                BastionGroupedSettings {
                    listOf(
                        "Media Services" to "Plex, Radarr, Sonarr and override rules",
                        "Metadata Providers" to "External metadata configuration",
                    ).forEachIndexed { index, entry ->
                        BastionNavigationSetting(entry.first, entry.second, index, 2,
                            icon = listOf(Icons.Default.Dns, Icons.Default.AutoAwesome)[index]) {
                            onSectionChange(entry.first)
                        }
                    }
                }
                BastionGroupedSettings {
                    listOf(
                        "Notifications" to "Seerr server notification channels",
                        "Maintenance & Diagnostics" to "Logs, scheduled jobs and cache",
                    ).forEachIndexed { index, entry ->
                        BastionNavigationSetting(entry.first, entry.second, index, 2,
                            icon = listOf(Icons.Default.Notifications, Icons.Default.Build)[index]) {
                            onSectionChange(entry.first)
                        }
                    }
                }
                BastionGroupedSettings {
                    BastionNavigationSetting(
                        "App Preferences", "Bastion preferences for this Seerr connection",
                        index = 0, count = 1, icon = Icons.Default.PhoneAndroid,
                    ) { onSectionChange("App Preferences") }
                }
                Text("Seerr server settings are separate from Bastion's local preferences.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            "Media Services" -> BastionGroupedSettings {
                mediaPages.forEachIndexed { index, name ->
                    BastionNavigationSetting(name, "Configure ${name.lowercase()} integration",
                        index, mediaPages.size,
                        icon = listOf(Icons.Default.Tv, Icons.Default.Movie, Icons.Default.LiveTv, Icons.Default.Rule)[index]) { onSectionChange(name) }
                }
            }
            "Maintenance & Diagnostics" -> BastionGroupedSettings {
                maintenancePages.forEachIndexed { index, name ->
                    BastionNavigationSetting(name, "Manage ${name.lowercase()}",
                        index, maintenancePages.size,
                        icon = listOf(Icons.Default.Article, Icons.Default.Schedule, Icons.Default.CleaningServices)[index]) { onSectionChange(name) }
                }
            }
            "App Preferences" -> {
                Text("These preferences apply only to this Seerr connection and are stored in Bastion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Requests per page", style = MaterialTheme.typography.titleMedium)
                BastionGroupedSettings {
                    listOf(25, 50, 100).forEachIndexed { index, size ->
                        BastionChoiceSetting(
                            title = "$size requests",
                            description = "Show $size requests on each page",
                            selected = requestsPageSize == size,
                            index = index,
                            count = 3,
                            onClick = {
                                requestsPageSize = size
                                preferences.edit().putInt("requests_page_size_${instance.id}", size).apply()
                            },
                        )
                    }
                }
            }
            else -> {
                Text("This Seerr server settings category is not implemented yet. Existing server settings are unchanged.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
