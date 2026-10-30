package com.koinupapi.bastion

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** About and update actions share a destination, while retaining the existing updater. */
@Composable
fun BastionAboutScreen() {
    val context = LocalContext.current
    val packageInfo = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    var showUpdater by remember { mutableStateOf(false) }
    val repository = "https://github.com/koinupapi/Bastion"
    fun openExternal(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    Column(verticalArrangement = Arrangement.spacedBy(BastionSpacing.large)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
            Surface(shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Default.Shield, contentDescription = null,
                    modifier = Modifier.padding(20.dp).size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Bastion", style = MaterialTheme.typography.headlineMedium)
                Text("Version ${packageInfo.versionName ?: "Unknown"} · Build ${packageInfo.longVersionCode}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("UPDATES", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
        BastionGroupedSettings {
            BastionSettingsDestination(
                title = "Check for updates",
                description = "Find and install a newer signed APK",
                icon = Icons.Default.SystemUpdate,
                index = 0, count = 2,
            ) { showUpdater = !showUpdater }
            BastionSettingsDestination(
                title = "What's new",
                description = "Browse GitHub releases and change notes",
                icon = Icons.Default.History,
                index = 1, count = 2,
            ) { openExternal("$repository/releases") }
        }
        if (showUpdater) {
            // Preserve the existing update channel, checks, download and install logic.
            BastionUpdateSettings()
        }

        Text("PROJECT & FEEDBACK", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
        BastionGroupedSettings {
            BastionSettingsDestination(
                title = "GitHub", description = "Source code and project repository",
                icon = Icons.Default.Code, index = 0, count = 3,
            ) { openExternal(repository) }
            BastionSettingsDestination(
                title = "Report a bug", description = "Open a GitHub issue",
                icon = Icons.Default.BugReport, index = 1, count = 3,
            ) { openExternal("$repository/issues/new") }
            BastionSettingsDestination(
                title = "Request a feature", description = "Suggest an improvement on GitHub",
                icon = Icons.Default.Lightbulb, index = 2, count = 3,
            ) { openExternal("$repository/issues/new") }
        }
    }
}
