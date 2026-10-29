package com.koinupapi.bastion

import androidx.compose.foundation.layout.*
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
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Manage request", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text(request.title, style = MaterialTheme.typography.titleLarge)
            Text("Request #${request.requestId} • ${request.requestStatus}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (request.isTv && request.seasons.isNotEmpty())
                Text("Seasons: ${request.seasons.joinToString(", ")}")
            if (request.requestStatus == "Pending") {
                Button(onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        SeerrClient.changeRequestStatus(instance, request.requestId, true)
                            .onSuccess { onChanged(); onDismiss() }
                            .onFailure { error = it.message ?: "Approval failed" }
                        busy = false
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
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
            if (!confirmDelete) {
                TextButton(onClick = { confirmDelete = true }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Remove request", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Text("Remove this request from Seerr? This does not remove files from your media library.",
                    color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { confirmDelete = false }, enabled = !busy,
                        modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = {
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
