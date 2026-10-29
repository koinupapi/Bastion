package com.koinupapi.bastion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Read-only first pass. Server write operations will be added after device validation. */
@Composable
fun SeerrAdminUserDetailsScreen(instance: ServiceConfig, userId: Int, onBack: () -> Unit) {
    var user by remember(instance.id, userId) { mutableStateOf<SeerrAdminUserDetails?>(null) }
    var error by remember(instance.id, userId) { mutableStateOf<String?>(null) }
    var loading by remember(instance.id, userId) { mutableStateOf(true) }
    var refresh by remember(instance.id, userId) { mutableIntStateOf(0) }
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
            Text("Permission editing will be added after validating account details.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("Request quotas", style = MaterialTheme.typography.titleMedium)
            QuotaSummary("Movies", details.movieQuota)
            QuotaSummary("TV", details.tvQuota)
            Text("Configured movie quota: ${details.movieQuotaLimit} per ${details.movieQuotaDays} days")
            Text("Configured TV quota: ${details.tvQuotaLimit} per ${details.tvQuotaDays} days")
        }
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
