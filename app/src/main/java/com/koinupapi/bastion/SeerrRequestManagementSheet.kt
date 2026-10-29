package com.koinupapi.bastion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeerrRequestManagementSheet(
    instance: ServiceConfig,
    request: SeerrRecentRequest,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var canManage by remember(instance.id) { mutableStateOf<Boolean?>(null) }
    var approvalDetails by remember(instance.id, request.requestId) {
        mutableStateOf<Result<SeerrApprovalDetails>?>(null)
    }
    LaunchedEffect(instance, request.requestId, request.isTv) {
        approvalDetails = null
        canManage = SeerrClient.canManageRequests(instance).getOrDefault(false)
        if (canManage == true) {
            approvalDetails = SeerrClient.requestApprovalDetails(instance, request.requestId, request.isTv)
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (canManage == true) "Manage request" else "Request details",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!request.posterUrl.isNullOrBlank()) {
                    AsyncImage(model = request.posterUrl, contentDescription = "${request.title} poster",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(112.dp).height(168.dp)
                            .clip(RoundedCornerShape(20.dp)))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(request.title, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text("${if (request.isTv) "TV series" else "Movie"} · Request #${request.requestId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(request.requestStatus, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    if (request.isTv && request.seasons.isNotEmpty()) {
                        Text(request.seasons.joinToString(", ") { "Season $it" },
                            style = MaterialTheme.typography.labelMedium)
                    }
                    request.requestedBy?.takeIf { it.isNotBlank() }?.let {
                        Text("Requested by $it", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider()
            Text("REQUEST INFORMATION", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (request.isTv && request.seasons.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Requested seasons", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium)
                    Text(request.seasons.joinToString(", ") { "Season $it" },
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Request status", style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                Text(request.requestStatus, style = MaterialTheme.typography.bodyMedium)
            }
            if (canManage == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (canManage == true) {
                HorizontalDivider()
                Text("DESTINATION", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (val details = approvalDetails) {
                    null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    else -> details.fold(
                        onSuccess = { destination ->
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Instance", style = MaterialTheme.typography.bodyMedium)
                                    Text(destination.server ?: "Not specified",
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Quality profile", style = MaterialTheme.typography.bodyMedium)
                                    Text(destination.profile ?: "Not specified",
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Root folder", style = MaterialTheme.typography.bodyMedium)
                                    Text(destination.rootFolder ?: "Not specified",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (request.requestStatus == "Pending" &&
                                    (!destination.hasServer || !destination.hasProfile || !destination.hasRoot)) {
                                    Text("Some settings are missing. Check Seerr before approving.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        },
                        onFailure = {
                            Text("Destination details unavailable: ${it.message ?: "Unknown error"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )
                }
            }
            if (request.requestStatus == "Pending" && canManage == true) {
                Button(onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        SeerrClient.changeRequestStatus(instance, request.requestId, true)
                            .onSuccess { onChanged(); onDismiss() }
                            .onFailure { error = it.message ?: "Approval failed" }
                        busy = false
                    }
                }, enabled = !busy && approvalDetails?.isSuccess == true, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Approve request")
                }
                OutlinedButton(onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        SeerrClient.changeRequestStatus(instance, request.requestId, false)
                            .onSuccess { onChanged(); onDismiss() }
                            .onFailure { error = it.message ?: "Decline failed" }
                        busy = false
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Decline request")
                }
            }
            if (request.requestStatus == "Failed" && canManage == true) {
                Button(onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        SeerrClient.retryRequest(instance, request.requestId)
                            .onSuccess { onChanged(); onDismiss() }
                            .onFailure { error = it.message ?: "Retry failed" }
                        busy = false
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Retry request")
                }
            }
            if (canManage == true && !confirmDelete) {
                TextButton(onClick = { confirmDelete = true }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Remove request", color = MaterialTheme.colorScheme.error)
                }
            } else if (canManage == true) {
                Text("Remove this request from Seerr? This does not remove files from your media library.",
                    color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { confirmDelete = false }, enabled = !busy,
                        modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            SeerrClient.removeRequest(instance, request.requestId)
                                .onSuccess { onChanged(); onDismiss() }
                                .onFailure { error = it.message ?: "Removal failed" }
                            busy = false
                        }
                    }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Remove") }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
