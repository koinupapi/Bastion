package com.koinupapi.bastion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Seerr server users; no account modifications are performed on this screen. */
@Composable
fun SeerrUserDirectoryScreen(instance: ServiceConfig) {
    var page by remember(instance.id) { mutableIntStateOf(0) }
    var refresh by remember(instance.id) { mutableIntStateOf(0) }
    var directory by remember(instance.id) { mutableStateOf<SeerrUserDirectory?>(null) }
    var error by remember(instance.id) { mutableStateOf<String?>(null) }
    var loading by remember(instance.id) { mutableStateOf(true) }
    LaunchedEffect(instance, page, refresh) {
        loading = true
        SeerrClient.userDirectory(instance, 25, page * 25)
            .onSuccess { directory = it; error = null }
            .onFailure { error = it.message ?: "Unable to load Seerr users"; directory = null }
        loading = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Users", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { refresh++ }, enabled = !loading) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh users")
            }
        }
        Text("Accounts managed by this Seerr server", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        directory?.let { data ->
            Text("${data.total} users", style = MaterialTheme.typography.titleMedium)
            data.users.forEach { user ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(user.displayName.ifBlank { user.username.ifBlank { "User #${user.id}" } },
                            style = MaterialTheme.typography.titleMedium)
                        if (user.email.isNotBlank()) Text(user.email, style = MaterialTheme.typography.bodySmall)
                        Text("ID ${user.id} · Permissions ${user.permissions}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { page-- }, enabled = page > 0 && !loading) { Text("Previous") }
                Text("Page ${page + 1}", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { page++ },
                    enabled = !loading && (page + 1) * 25 < data.total) { Text("Next") }
            }
        }
    }
}
