package com.koinupapi.bastion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Read-only first pass. Server write operations will be added after device validation. */
@Composable
fun SeerrAdminUserDetailsScreen(instance: ServiceConfig, userId: Int, onBack: () -> Unit) {
    var user by remember(instance.id, userId) { mutableStateOf<SeerrAdminUserDetails?>(null) }
    var error by remember(instance.id, userId) { mutableStateOf<String?>(null) }
    var loading by remember(instance.id, userId) { mutableStateOf(true) }
    var refresh by remember(instance.id, userId) { mutableIntStateOf(0) }
    var editPermissions by remember(instance.id, userId) { mutableStateOf(false) }
    var permissionText by remember(instance.id, userId) { mutableStateOf("") }
    var editQuotas by remember(instance.id, userId) { mutableStateOf(false) }
    var quotaValues by remember(instance.id, userId) { mutableStateOf(listOf("", "", "", "")) }
    var confirmation by remember(instance.id, userId) { mutableStateOf<String?>(null) }
    var saving by remember(instance.id, userId) { mutableStateOf(false) }
    var saveError by remember(instance.id, userId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(instance, userId, refresh) {
        loading = true
        SeerrClient.adminUserDetails(instance, userId)
            .onSuccess { user = it; error = null }
            .onFailure { error = it.message ?: "Could not load user"; user = null }
        loading = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("Back to users") }
        Text("User details", style = MaterialTheme.typography.headlineSmall)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { refresh++ }) { Text("Retry") }
        }
        user?.let { details ->
            Text(details.displayName.ifBlank { details.username.ifBlank { "User #${details.id}" } },
                style = MaterialTheme.typography.titleLarge)
            Text(details.email)
            Text("Username: ${details.username.ifBlank { "Not set" }}")
            Text("User ID: ${details.id}")
            Text("Account type: ${details.userType}")
            HorizontalDivider()
            Text("Permissions", style = MaterialTheme.typography.titleMedium)
            Text("Seerr permission bitmask: ${details.permissions}")
            OutlinedButton(onClick = {
                permissionText = details.permissions.toString()
                editPermissions = !editPermissions
            }) { Text(if (editPermissions) "Cancel permission edit" else "Edit permissions") }
            if (editPermissions) {
                OutlinedTextField(value = permissionText, onValueChange = { permissionText = it },
                    label = { Text("Seerr permissions bitmask") },
                    supportingText = { Text("Advanced: enter the complete numeric Seerr permission value.") })
                Button(onClick = { confirmation = "permissions" },
                    enabled = permissionText.toIntOrNull()?.let { it >= 0 } == true && !saving) {
                    Text("Review permission change")
                }
            }
            HorizontalDivider()
            Text("Request quotas", style = MaterialTheme.typography.titleMedium)
            QuotaSummary("Movies", details.movieQuota)
            QuotaSummary("TV", details.tvQuota)
            Text("Configured movie quota: ${details.movieQuotaLimit} per ${details.movieQuotaDays} days")
            Text("Configured TV quota: ${details.tvQuotaLimit} per ${details.tvQuotaDays} days")
            OutlinedButton(onClick = {
                quotaValues = listOf(details.movieQuotaLimit, details.movieQuotaDays,
                    details.tvQuotaLimit, details.tvQuotaDays).map { it.toString() }
                editQuotas = !editQuotas
            }) { Text(if (editQuotas) "Cancel quota edit" else "Edit quotas") }
            if (editQuotas) {
                listOf("Movie limit", "Movie days", "TV limit", "TV days").forEachIndexed { index, label ->
                    OutlinedTextField(value = quotaValues[index],
                        onValueChange = { value ->
                            quotaValues = quotaValues.toMutableList().also { it[index] = value }
                        }, label = { Text(label) },
                        supportingText = { Text("Enter a nonnegative number, or leave blank to inherit server defaults.") })
                }
                Button(onClick = { confirmation = "quotas" },
                    enabled = !saving && quotaValues.all {
                        it.isBlank() || (it.toIntOrNull()?.let { n -> n >= 0 } == true)
                    }) { Text("Review quota change") }
            }
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    confirmation?.let { action ->
        AlertDialog(onDismissRequest = { if (!saving) confirmation = null },
            title = { Text(if (action == "permissions") "Save permissions?" else "Save quotas?") },
            text = {
                Text(if (action == "permissions")
                    "Change this user's Seerr permissions from ${user?.permissions} to $permissionText?"
                else "Save movie and TV quota settings for this user? Blank fields inherit server defaults.")
            },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    scope.launch {
                        val result = if (action == "permissions") {
                            SeerrClient.updateAdminUserPermissions(instance, userId, permissionText.toInt())
                        } else {
                            val values = quotaValues.map { it.trim().toIntOrNull() }
                            SeerrClient.updateAdminUserQuotas(instance, userId,
                                values[0], values[1], values[2], values[3])
                        }
                        result.onSuccess {
                            editPermissions = false
                            editQuotas = false
                            saveError = null
                            refresh++
                        }.onFailure { saveError = it.message ?: "Unable to save user settings" }
                        saving = false
                        confirmation = null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = { confirmation = null }) { Text("Cancel") } })
    }
}

@Composable
private fun QuotaSummary(label: String, quota: SeerrAdminQuota) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(if (quota.restricted) "Restricted" else "Not restricted")
            Text("Used: ${quota.used} · Remaining: ${quota.remaining}")
            Text("Limit: ${quota.limit} · Window: ${quota.days} days")
        }
    }
}
