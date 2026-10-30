package com.koinupapi.bastion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun BastionUpdateSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("bastion_updates", android.content.Context.MODE_PRIVATE) }
    var branch by remember { mutableStateOf(prefs.getString("branch", "main") ?: "main") }
    var branches by remember { mutableStateOf(listOf("main", "develop", branch).distinct()) }
    var expanded by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<BastionUpdate?>(null) }
    var checking by remember { mutableStateOf(false) }
    var refreshingChannels by remember { mutableStateOf(false) }
    var channelMessage by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val packageInfo = remember {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val version = packageInfo.longVersionCode
    val versionName = packageInfo.versionName ?: "Unknown"
    fun check() {
        if (checking || downloading) return
        scope.launch {
            checking = true
            message = null
            try {
                update = BastionUpdater.check(context, branch)
                message = if (update == null) "No newer installable build found for " + branch + ". Installed build: " + version + "."
                    else "Build ${update?.versionCode} is available from " + branch + "."
            } catch (e: Exception) { message = e.message ?: "Unable to check GitHub releases." }
            finally { checking = false }
        }
    }
    // Triggered on entry and by the explicit Refresh channels action.
    // Preserve known channels and the selected channel on network failure.
    var channelRefreshRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(channelRefreshRequest) {
        refreshingChannels = true
        channelMessage = null
        try {
            val fetched = BastionUpdater.availableBranches()
            branches = (listOf("main", "develop", branch) + fetched).distinct()
            if (channelRefreshRequest > 0) {
                channelMessage = "Channels refreshed: ${branches.size} available."
            }
        } catch (e: Exception) {
            channelMessage = "Could not refresh channels: " +
                (e.message ?: "Network error. Check your connection and try again.")
        } finally {
            refreshingChannels = false
        }
    }
    LaunchedEffect(branch) { update = null; message = "Tap Check for updates to check " + branch + "." }
    Card(shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Bastion updates", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("Version $versionName", style = MaterialTheme.typography.titleMedium)
            Text("Build number $version",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Signed APK updates are published through the selected GitHub release feed. Android will ask you to confirm installation.",
                style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Update channel", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { channelRefreshRequest++ },
                    enabled = !refreshingChannels && !downloading) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh channels")
                }
            }
            if (refreshingChannels) LinearProgressIndicator(Modifier.fillMaxWidth())
            channelMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("Could not"))
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (branch == "main") "Stable (main)" else if (branch == "develop") "Development (develop)" else "Test: " + branch)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    branches.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(if (option == "main") "Stable (main)" else if (option == "develop") "Development (develop)" else option) },
                            onClick = {
                                expanded = false
                                branch = option
                                prefs.edit().putString("branch", option).apply()
                            }
                        )
                    }
                }
            }
            if (branch != "main") Text(
                "Experimental branch. Android cannot install an older version code over a newer build.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (checking || downloading) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = { check() }, enabled = !checking && !downloading,
                modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Check for updates")
            }
            update?.let { candidate ->
                Button(onClick = {
                    scope.launch {
                        downloading = true
                        message = "Downloading signed APK…"
                        try {
                            val apk = BastionUpdater.download(context, candidate)
                            message = "Download complete. Confirm the update in Android."
                            BastionUpdater.install(context, apk)
                        } catch (e: Exception) {
                            message = e.message ?: "Could not install update."
                        } finally { downloading = false }
                    }
                }, enabled = !downloading && !checking,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(24.dp)) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download and install build ${candidate.versionCode}")
                }
            }
        }
    }
}
