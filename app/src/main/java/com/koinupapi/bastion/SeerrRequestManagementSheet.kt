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
    var servers by remember(instance.id, request.requestId) { mutableStateOf<List<SeerrRequestServer>>(emptyList()) }
    var destinationLoading by remember(instance.id, request.requestId) { mutableStateOf(false) }
    var destinationError by remember(instance.id, request.requestId) { mutableStateOf<String?>(null) }
    var selectedServer by remember(instance.id, request.requestId) { mutableIntStateOf(0) }
    var selectedProfile by remember(instance.id, request.requestId) { mutableIntStateOf(0) }
    var selectedRoot by remember(instance.id, request.requestId) { mutableStateOf("") }
    var originalServer by remember(instance.id, request.requestId) { mutableIntStateOf(0) }
    var originalProfile by remember(instance.id, request.requestId) { mutableIntStateOf(0) }
    var originalRoot by remember(instance.id, request.requestId) { mutableStateOf("") }
    var savedMessage by remember(instance.id, request.requestId) { mutableStateOf<String?>(null) }
    val activeServer = servers.firstOrNull { it.id == selectedServer }
    val destinationChanged = selectedServer != originalServer ||
        selectedProfile != originalProfile || selectedRoot != originalRoot
    val serverValid = activeServer != null
    val profileValid = activeServer?.profiles?.any { it.id == selectedProfile } == true
    val rootValid = activeServer?.roots?.any { it.name == selectedRoot } == true
    val validDestination = serverValid && profileValid && rootValid
    var adminMetadata by remember(instance.id, request.requestId) { mutableStateOf<SeerrRequestAdminMetadata?>(null) }
    var approvalDetails by remember(instance.id, request.requestId) {
        mutableStateOf<Result<SeerrApprovalDetails>?>(null)
    }
    LaunchedEffect(instance, request.requestId, request.isTv) {
        approvalDetails = null
        canManage = SeerrClient.canManageRequests(instance).getOrDefault(false)
        if (canManage == true) {
            adminMetadata = SeerrClient.requestAdminMetadata(instance, request.requestId).getOrNull()
            approvalDetails = SeerrClient.requestApprovalDetails(instance, request.requestId, request.isTv)
            if (request.requestStatus == "Pending") {
                destinationLoading = true
                val choices = SeerrClient.requestOptions(instance, request.isTv)
                val existing = SeerrClient.editableRequestDestination(instance, request.requestId)
                if (choices.isSuccess && existing.isSuccess) {
                    servers = choices.getOrThrow()
                    val original = existing.getOrThrow()
                    originalServer = original.serverId
                    originalProfile = original.profileId
                    originalRoot = original.rootFolder
                    val configured = servers.firstOrNull { it.id == original.serverId }
                        ?: servers.filter { candidate ->
                            candidate.profiles.any { it.id == original.profileId } &&
                                candidate.roots.any { it.name == original.rootFolder }
                        }.singleOrNull()
                    selectedServer = configured?.id ?: -1
                    selectedProfile = original.profileId.takeIf { id ->
                        configured?.profiles?.any { it.id == id } == true
                    } ?: configured?.defaultProfile?.takeIf { id ->
                        configured.profiles.any { it.id == id }
                    } ?: configured?.profiles?.firstOrNull()?.id ?: 0
                    selectedRoot = original.rootFolder.takeIf { root ->
                        configured?.roots?.any { it.name == root } == true
                    } ?: configured?.roots?.firstOrNull { it.id == configured.defaultRoot }?.name
                        ?: configured?.roots?.firstOrNull()?.name.orEmpty()
                } else {
                    destinationError = choices.exceptionOrNull()?.message
                        ?: existing.exceptionOrNull()?.message ?: "Cannot load destination options."
                }
                destinationLoading = false
            }
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = BottomSheetDefaults.ExpandedShape) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = BastionSheetDefaults.horizontalPadding).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(BastionSpacing.large)) {
            Text(if (canManage == true) "Manage request" else "Request details",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BastionSpacing.large)) {
                if (!request.posterUrl.isNullOrBlank()) {
                    AsyncImage(model = request.posterUrl, contentDescription = "${request.title} poster",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(112.dp).height(168.dp)
                            .clip(MaterialTheme.shapes.large))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(request.title, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text("${if (request.isTv) "TV series" else "Movie"} · Request #${request.requestId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(BastionSpacing.small)) {
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
                    Text("Requested seasons", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium)
                    Text(request.seasons.joinToString(", ") { "Season $it" },
                        modifier = Modifier.weight(1.35f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Request status", style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                Text(request.requestStatus, style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Media acquisition", style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                Text(request.mediaStatus ?: "Not yet tracked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canManage == true) {
                adminMetadata?.let { metadata ->
                    Text("ADMINISTRATIVE DETAILS", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    fun displayTime(value: String): String = runCatching {
                        java.time.OffsetDateTime.parse(value)
                            .atZoneSameInstant(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))
                    }.getOrDefault(value)
                    metadata.createdAt?.let { Text("Created: ${displayTime(it)}", style = MaterialTheme.typography.bodySmall) }
                    metadata.updatedAt?.let { Text("Last updated: ${displayTime(it)}", style = MaterialTheme.typography.bodySmall) }
                    metadata.requesterId?.let { Text("Requester ID: $it", style = MaterialTheme.typography.bodySmall) }
                    Text(if (metadata.is4k) "4K request: Yes" else "4K request: No",
                        style = MaterialTheme.typography.bodySmall)
                }
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
                                Row(Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(BastionSpacing.medium),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text("Root folder", modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium)
                                    Text(destination.rootFolder ?: "Not specified",
                                        modifier = Modifier.weight(1.35f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (request.requestStatus == "Pending" &&
                                    (!destination.hasServer || !destination.hasProfile || !destination.hasRoot)) {
                                    Text("Destination could not be uniquely verified. Select a valid instance, profile and root folder, then save before approving.",
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
                HorizontalDivider()
                Text("EDIT DESTINATION", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (destinationLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                destinationError?.let {
                    Text("Destination editing unavailable: $it", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (!destinationLoading && destinationError == null) {
                    RequestDestinationSelector("Sonarr/Radarr instance",
                        servers.map { it.id.toString() to it.name },
                        selectedServer.toString(), !busy) { value ->
                        selectedServer = value.toInt()
                        val server = servers.first { it.id == selectedServer }
                        selectedProfile = server.defaultProfile.takeIf { id ->
                            server.profiles.any { it.id == id }
                        } ?: server.profiles.firstOrNull()?.id ?: 0
                        selectedRoot = server.roots.firstOrNull { it.id == server.defaultRoot }?.name
                            ?: server.roots.firstOrNull()?.name.orEmpty()
                        savedMessage = null
                    }
                    RequestDestinationSelector("Quality profile",
                        activeServer?.profiles?.map { it.id.toString() to it.name }.orEmpty(),
                        selectedProfile.toString(), !busy) {
                        selectedProfile = it.toInt()
                        savedMessage = null
                    }
                    RequestDestinationSelector("Root folder",
                        activeServer?.roots?.map { it.name to it.name }.orEmpty(),
                        selectedRoot, !busy) {
                        selectedRoot = it
                        savedMessage = null
                    }
                    if (!validDestination) {
                        val missing = buildList {
                            if (!serverValid) add("instance")
                            if (!profileValid) add("quality profile")
                            if (!rootValid) add("root folder")
                        }
                        Text("Invalid selection: ${missing.joinToString(", ")}. Choose an option from each dropdown.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    if (!destinationChanged && validDestination) Text(
                        "No destination changes to save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    savedMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    OutlinedButton(onClick = {
                        busy = true
                        error = null
                        savedMessage = null
                        scope.launch {
                            SeerrClient.updateRequestDestination(instance, request.requestId,
                                request.isTv, selectedServer, selectedProfile, selectedRoot)
                                .onSuccess {
                                    originalServer = selectedServer
                                    originalProfile = selectedProfile
                                    originalRoot = selectedRoot
                                    approvalDetails = SeerrClient.requestApprovalDetails(
                                        instance, request.requestId, request.isTv)
                                    adminMetadata = SeerrClient.requestAdminMetadata(
                                        instance, request.requestId).getOrNull()
                                    savedMessage = "Destination saved and verified. You can now approve."
                                    onChanged()
                                }
                                .onFailure { error = it.message ?: "Could not save destination" }
                            busy = false
                        }
                    }, enabled = !busy && validDestination && destinationChanged,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Save destination changes")
                    }
                }
            }
            if (request.requestStatus == "Pending" && canManage == true) {
                Button(onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        runCatching {
                            SeerrClient.pendingRequestForAction(instance, request.requestId).getOrThrow()
                            SeerrClient.changeRequestStatus(instance, request.requestId, true).getOrThrow()
                        }
                            .onSuccess { onChanged(); onDismiss() }
                            .onFailure { error = it.message ?: "Approval failed" }
                        busy = false
                    }
                }, enabled = !busy && approvalDetails?.isSuccess == true && !destinationLoading &&
                    destinationError == null && !destinationChanged && validDestination,
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Approve request")
                }
                OutlinedButton(onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        runCatching {
                            SeerrClient.pendingRequestForAction(instance, request.requestId).getOrThrow()
                            SeerrClient.changeRequestStatus(instance, request.requestId, false).getOrThrow()
                        }
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
                Row(horizontalArrangement = Arrangement.spacedBy(BastionSpacing.small)) {
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

@Composable
private fun RequestDestinationSelector(label: String, options: List<Pair<String, String>>,
    selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.firstOrNull { it.first == selected }?.second ?: "Select…"
    Column(verticalArrangement = Arrangement.spacedBy(BastionSpacing.xSmall)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true },
                enabled = enabled && options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(display, maxLines = 2)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = {
                        expanded = false
                        onSelect(id)
                    })
                }
            }
        }
    }
}
