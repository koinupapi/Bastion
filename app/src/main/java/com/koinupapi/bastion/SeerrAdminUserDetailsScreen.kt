package com.koinupapi.bastion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val seerrPermissionOptions = listOf(
    "Administrator" to 2,
    "Manage settings" to 4,
    "Manage users" to 8,
    "Manage requests" to 16,
    "Request media" to 32,
    "Vote" to 64,
    "Auto approve" to 128,
    "Auto approve movies" to 256,
    "Auto approve TV" to 512,
    "Request 4K" to 1024,
    "Request 4K movies" to 2048,
    "Request 4K TV" to 4096,
    "Advanced requests" to 8192,
    "View requests" to 16384,
    "Auto approve 4K" to 32768,
    "Auto approve 4K movies" to 65536,
    "Auto approve 4K TV" to 131072,
    "Request movies" to 262144,
    "Request TV" to 524288,
    "Manage issues" to 1048576,
    "View issues" to 2097152,
    "Create issues" to 4194304,
    "Auto request" to 8388608,
    "Auto request movies" to 16777216,
    "Auto request TV" to 33554432,
    "View recently added" to 67108864,
    "View watchlists" to 134217728,
    "Manage blocklist" to 268435456,
    "View blocklist" to 1073741824
)


@Composable
fun SeerrAdminUserDetailsScreen(instance: ServiceConfig, userId: Int, onBack: () -> Unit) {
    var user by remember(instance.id, userId) { mutableStateOf<SeerrAdminUserDetails?>(null) }
    var error by remember(instance.id, userId) { mutableStateOf<String?>(null) }
    var loading by remember(instance.id, userId) { mutableStateOf(true) }
    var refresh by remember(instance.id, userId) { mutableIntStateOf(0) }
    var editPermissions by remember(instance.id, userId) { mutableStateOf(false) }
    var selectedPermissions by remember(instance.id, userId) { mutableIntStateOf(0) }
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
                selectedPermissions = details.permissions
                editPermissions = !editPermissions
            }) { Text(if (editPermissions) "Cancel permission edit" else "Edit permissions") }
            if (editPermissions) {
                seerrPermissionOptions.forEach { (name, bit) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, modifier = Modifier.weight(1f))
                        Checkbox(checked = (selectedPermissions and bit) != 0,
                            onCheckedChange = { checked ->
                                selectedPermissions = if (checked) selectedPermissions or bit
                                else selectedPermissions and bit.inv()
                            })
                    }
                }
                Button(onClick = { confirmation = "permissions" }, enabled = !saving) {
                    Text("Review permission change")
                }
            }
            HorizontalDivider()
            Text("Request quotas", style = MaterialTheme.typography.titleMedium)
            QuotaSummary("Movies", details.movieQuota)
            QuotaSummary("TV", details.tvQuota)
            Text("Configured movie quota: ${details.movieQuotaLimit?.toString() ?: "Inherited"} · Period: ${details.movieQuotaDays?.toString() ?: "Inherited"}")
            Text("Configured TV quota: ${details.tvQuotaLimit?.toString() ?: "Inherited"} · Period: ${details.tvQuotaDays?.toString() ?: "Inherited"}")
            OutlinedButton(onClick = {
                quotaValues = listOf(details.movieQuotaLimit, details.movieQuotaDays,
                    details.tvQuotaLimit, details.tvQuotaDays).map { it?.toString() ?: "" }
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
                    "Change this user's Seerr permissions from ${user?.permissions} to $selectedPermissions?"
                else "Save movie and TV quota settings for this user? Blank fields inherit server defaults.")
            },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    scope.launch {
                        val result = if (action == "permissions") {
                            SeerrClient.updateAdminUserPermissions(instance, userId, selectedPermissions)
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
