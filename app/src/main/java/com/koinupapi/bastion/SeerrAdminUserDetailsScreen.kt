package com.koinupapi.bastion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class PermissionEntry(val label: String, val bit: Int, val children: List<PermissionEntry> = emptyList())
private val seerrPermissionGroups = listOf(
    PermissionEntry("Admin", 2),
    PermissionEntry("Manage Users", 8),
    PermissionEntry("Manage Requests", 16, listOf(PermissionEntry("Advanced Requests", 8192), PermissionEntry("View Requests", 16384), PermissionEntry("View Recently Added", 67108864), PermissionEntry("View Plex Watchlists", 134217728))),
    PermissionEntry("Request", 32, listOf(PermissionEntry("Request Movies", 262144), PermissionEntry("Request Series", 524288))),
    PermissionEntry("Auto-Approve", 128, listOf(PermissionEntry("Auto-Approve Movies", 256), PermissionEntry("Auto-Approve Series", 512))),
    PermissionEntry("Auto-Request", 8388608, listOf(PermissionEntry("Auto-Request Movies", 16777216), PermissionEntry("Auto-Request Series", 33554432))),
    PermissionEntry("Request 4K", 1024, listOf(PermissionEntry("Request 4K Movies", 2048), PermissionEntry("Request 4K Series", 4096))),
    PermissionEntry("Auto-Approve 4K", 32768, listOf(PermissionEntry("Auto-Approve 4K Movies", 65536), PermissionEntry("Auto-Approve 4K Series", 131072))),
    PermissionEntry("Manage Issues", 1048576, listOf(PermissionEntry("Report Issues", 4194304), PermissionEntry("View Issues", 2097152))),
    PermissionEntry("Manage Blocklist", 268435456, listOf(PermissionEntry("View Blocklisted Media", 1073741824)))
)
private fun hasBit(value: Int, bit: Int) = (value and bit) != 0
private fun permissionRequires(bit: Int, value: Int): Boolean = when (bit) {
    128, 8388608 -> hasBit(value, 32)
    256, 512, 16777216, 33554432 -> hasBit(value, 32) || hasBit(value, if (bit == 256 || bit == 16777216) 262144 else 524288)
    32768 -> hasBit(value, 1024)
    65536, 131072 -> hasBit(value, 1024) || hasBit(value, if (bit == 65536) 2048 else 4096)
    else -> true
}

@Composable
private fun PermissionCheckbox(entry: PermissionEntry, value: Int, parentBit: Int?, onChange: (Int) -> Unit) {
    val admin = hasBit(value, 2)
    val managed = hasBit(value, 16) && entry.bit in listOf(128, 256, 512, 32768, 65536, 131072)
    val inherited = parentBit != null && hasBit(value, parentBit)
    val enabledByParent = admin || managed || inherited
    val requirementMet = permissionRequires(entry.bit, value)
    val checked = requirementMet && (hasBit(value, entry.bit) || enabledByParent)
    val locked = enabledByParent || !requirementMet
    Row(Modifier.fillMaxWidth().padding(start = if (parentBit != null) 28.dp else 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(entry.label, modifier = Modifier.weight(1f), style = if (parentBit == null)
            MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium)
        Checkbox(checked = checked, enabled = !locked,
            onCheckedChange = { selected ->
                onChange(if (selected) value or entry.bit else value and entry.bit.inv())
            })
    }
    entry.children.forEach { child -> PermissionCheckbox(child, value, entry.bit, onChange) }
}

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
                seerrPermissionGroups.forEach { entry ->
                    PermissionCheckbox(entry, selectedPermissions, null) { selectedPermissions = it }
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
