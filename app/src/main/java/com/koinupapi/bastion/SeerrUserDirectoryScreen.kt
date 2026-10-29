package com.koinupapi.bastion

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Seerr server users; no account modifications are performed on this screen. */
@Composable
fun SeerrUserDirectoryScreen(instance: ServiceConfig) {
    var selectedUser by remember(instance.id) { mutableStateOf<Int?>(null) }
    BackHandler(selectedUser != null) { selectedUser = null }
    if (selectedUser != null) {
        SeerrAdminUserDetailsScreen(instance, selectedUser!!, onBack = { selectedUser = null })
        return
    }
    val scope = rememberCoroutineScope()
    var showCreate by remember(instance.id) { mutableStateOf(false) }
    var showPlex by remember(instance.id) { mutableStateOf(false) }
    var plexLoading by remember(instance.id) { mutableStateOf(false) }
    var plexCandidates by remember(instance.id) { mutableStateOf<List<SeerrPlexCandidate>>(emptyList()) }
    var plexSelected by remember(instance.id) { mutableStateOf<Set<String>>(emptySet()) }
    var plexError by remember(instance.id) { mutableStateOf<String?>(null) }
    var plexConfirmation by remember(instance.id) { mutableStateOf(false) }
    var plexImporting by remember(instance.id) { mutableStateOf(false) }
    var plexMessage by remember(instance.id) { mutableStateOf<String?>(null) }
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
        OutlinedButton(onClick = { showCreate = !showCreate }) {
            Text(if (showCreate) "Close new user form" else "Create local user")
        }
        if (showCreate) SeerrCreateLocalUserSettings(instance, onUserCreated = {
            page = 0
            refresh++
        })
        OutlinedButton(onClick = {
            showPlex = !showPlex
            if (showPlex) {
                plexLoading = true
                plexError = null
                scope.launch {
                    SeerrClient.plexImportCandidates(instance)
                        .onSuccess { plexCandidates = it; plexSelected = emptySet() }
                        .onFailure { plexError = it.message ?: "Could not load Plex users" }
                    plexLoading = false
                }
            }
        }) { Text(if (showPlex) "Close Plex import" else "Import Plex users") }
        if (showPlex) {
            Text("Choose Plex accounts to import into Seerr.", style = MaterialTheme.typography.bodyMedium)
            if (plexLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            plexError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!plexLoading && plexError == null && plexCandidates.isEmpty()) Text("No Plex users available to import.")
            plexCandidates.forEach { candidate ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = candidate.id in plexSelected, enabled = !plexImporting,
                        onCheckedChange = { checked ->
                            plexSelected = if (checked) plexSelected + candidate.id else plexSelected - candidate.id
                        })
                    Column {
                        Text(candidate.name.ifBlank { "Plex user" })
                        if (candidate.email.isNotBlank()) Text(candidate.email, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Button(onClick = { plexConfirmation = true },
                enabled = plexSelected.isNotEmpty() && !plexLoading && !plexImporting) {
                Text("Review import (${plexSelected.size})")
            }
            plexMessage?.let { Text(it) }
        }
        Text("Accounts managed by this Seerr server", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        directory?.let { data ->
            Text("${data.total} users", style = MaterialTheme.typography.titleMedium)
            data.users.forEach { user ->
                ElevatedCard(onClick = { selectedUser = user.id }, modifier = Modifier.fillMaxWidth()) {
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
    if (plexConfirmation) AlertDialog(
        onDismissRequest = { if (!plexImporting) plexConfirmation = false },
        title = { Text("Import Plex users?") },
        text = { Text("Import ${plexSelected.size} selected Plex account(s) into Seerr? Existing accounts are managed by Seerr.") },
        confirmButton = {
            TextButton(enabled = !plexImporting, onClick = {
                plexImporting = true
                val ids = plexSelected.toList()
                scope.launch {
                    SeerrClient.importPlexUsers(instance, ids)
                        .onSuccess {
                            plexMessage = "Seerr imported $it account(s)."
                            plexSelected = emptySet()
                            refresh++
                        }
                        .onFailure { plexError = it.message ?: "Plex import failed" }
                    plexImporting = false
                    plexConfirmation = false
                }
            }) { Text("Import") }
        },
        dismissButton = { TextButton(enabled = !plexImporting, onClick = { plexConfirmation = false }) { Text("Cancel") } }
    )
}
