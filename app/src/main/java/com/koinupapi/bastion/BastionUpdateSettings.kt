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
    var branches by remember { mutableStateOf(listOf("main")) }
    var expanded by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<BastionUpdate?>(null) }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    }
    fun check() {
        scope.launch {
            checking = true
            message = null
            try {
                update = BastionUpdater.check(context, branch)
                message = if (update == null) "No newer installable build found for " + branch + ". Installed build: " + version + ". " + BastionUpdater.releaseDiagnostics(branch)
                    else "Build ${update?.versionCode} is available from " + branch + "."
            } catch (e: Exception) { message = e.message ?: "Unable to check GitHub releases." }
            finally { checking = false }
        }
    }
    LaunchedEffect(Unit) {
        try { branches = (listOf("main") + BastionUpdater.availableBranches()).distinct() }
        catch (e: Exception) { message = e.message }
    }
    LaunchedEffect(branch) { update = null; check() }
    Card(shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Bastion updates", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("Installed development build $version",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Signed APK updates are published to public GitHub development releases. Android will ask you to confirm installation.",
                style = MaterialTheme.typography.bodyMedium)
            Text("Update channel", style = MaterialTheme.typography.titleMedium)
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (branch == "main") "Stable (main)" else "Test: " + branch)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    branches.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(if (option == "main") "Stable (main)" else option) },
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
