package com.koinupapi.bastion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeerrIssueManagementSheet(instance: ServiceConfig, initial: SeerrIssue,
    onDismiss: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var issue by remember(initial.id) { mutableStateOf(initial) }
    var reply by remember(initial.id) { mutableStateOf("") }
    var loading by remember(initial.id) { mutableStateOf(true) }
    var busy by remember(initial.id) { mutableStateOf(false) }
    var error by remember(initial.id) { mutableStateOf<String?>(null) }
    var currentUser by remember(instance.id) { mutableStateOf<SeerrIssueUser?>(null) }
    var userLookupComplete by remember(instance.id) { mutableStateOf(false) }
    var confirmDelete by remember(initial.id) { mutableStateOf(false) }
    var editingComment by remember(initial.id) { mutableStateOf<SeerrIssueComment?>(null) }
    var editText by remember(initial.id) { mutableStateOf("") }
    var deletingComment by remember(initial.id) { mutableStateOf<SeerrIssueComment?>(null) }
    val isAdminConnection = !instance.apiKey.startsWith("session:")
    val isAdminUser = (currentUser?.permissions?.and(2) ?: 0) != 0
    val isOwner = currentUser?.id != null && currentUser!!.id > 0 &&
        currentUser!!.id == issue.creatorId
    val canManage = isAdminConnection || isAdminUser ||
        ((currentUser?.permissions?.and(1048576) ?: 0) != 0)
    val canCreate = canManage || ((currentUser?.permissions?.and(4194304) ?: 0) != 0)
    val canReply = userLookupComplete && canCreate && (canManage || isOwner)
    val canDelete = !busy && !loading && userLookupComplete &&
        (canManage || (isOwner && issue.commentEntries.size <= 1))
    LaunchedEffect(instance) {
        SeerrClient.currentIssueUser(instance).onSuccess { currentUser = it }
        userLookupComplete = true
    }
    LaunchedEffect(instance, initial.id) {
        SeerrClient.issueDetail(instance, initial.id)
            .onSuccess { issue = it }
            .onFailure { error = "Could not load latest issue: ${it.message}" }
        loading = false
    }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = BastionShapes.bottomSheet) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.86f)
            .padding(horizontal = BastionSheetDefaults.horizontalPadding).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${issue.type} issue", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (issue.resolved) "Resolved" else "Open",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Reported by ${issue.author}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
                if (issue.comments.isEmpty()) Text("No comments yet.")
                issue.commentEntries.forEachIndexed { index, comment ->
                    val ownComment = currentUser?.id != null && currentUser!!.id > 0 &&
                        currentUser!!.id == comment.userId
                    val mayEdit = userLookupComplete && canCreate && ownComment && comment.id > 0
                    val mayDelete = userLookupComplete && canCreate &&
                        (ownComment || canManage) && comment.id > 0
                    // Only verified user IDs determine ownership. API-key connections
                    // may not resolve to a specific user, so keep those bubbles neutral.
                    val isMine = ownComment && comment.userId > 0
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
                    Surface(shape = BastionShapes.mediaArtwork,
                        modifier = Modifier.fillMaxWidth(0.88f),
                        color = if (isMine) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(buildString {
                                append(comment.author)
                                if (index == 0) append(" • Original report")
                                if (isMine) append(" • You")
                            }, style = MaterialTheme.typography.labelMedium,
                                color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.primary)
                            Text(comment.message, style = MaterialTheme.typography.bodyMedium,
                                color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface)
                            if (comment.createdAt.isNotBlank()) {
                                Text(comment.createdAt.replace("T", " ").take(16),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (mayEdit || mayDelete) {
                                Row(horizontalArrangement = Arrangement.spacedBy(BastionSpacing.small)) {
                                    if (mayEdit) TextButton(enabled = !busy, onClick = {
                                        editingComment = comment
                                        editText = comment.message
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Edit")
                                    }
                                    if (mayDelete) TextButton(enabled = !busy,
                                        onClick = { deletingComment = comment }) {
                                        Icon(Icons.Default.Delete, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
            if (userLookupComplete && !canReply) {
                Text("You can view this issue, but cannot reply.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            if (canReply) OutlinedTextField(value = reply, onValueChange = { reply = it },
                label = { Text("Write a reply") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4, minLines = 2, enabled = !busy)
            if (canReply) Button(onClick = {
                busy = true
                error = null
                scope.launch {
                    SeerrClient.commentOnIssue(instance, issue.id, reply)
                        .onSuccess {
                            reply = ""
                            onChanged()
                            SeerrClient.issueDetail(instance, issue.id)
                                .onSuccess { issue = it }
                                .onFailure { error = it.message }
                        }.onFailure { error = it.message ?: "Could not send reply" }
                    busy = false
                }
            }, enabled = !busy && reply.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Post reply")
            }
            if (userLookupComplete && (canManage || isOwner)) OutlinedButton(onClick = {
                busy = true
                error = null
                scope.launch {
                    SeerrClient.changeIssueStatus(instance, issue.id, !issue.resolved)
                        .onSuccess {
                            issue = issue.copy(resolved = !issue.resolved)
                            onChanged()
                        }.onFailure { error = it.message ?: "Could not change issue status" }
                    busy = false
                }
            }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(if (issue.resolved) Icons.Default.Refresh else Icons.Default.CheckCircle,
                    contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (issue.resolved) "Reopen issue" else "Resolve issue")
            }
            if (canDelete) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete issue")
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDelete = false },
            title = { Text("Permanently delete issue?") },
            text = { Text("This removes the report and its discussion from Seerr. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            SeerrClient.deleteIssue(instance, issue.id)
                                .onSuccess {
                                    confirmDelete = false
                                    onChanged()
                                    onDismiss()
                                }
                                .onFailure {
                                    error = it.message ?: "Could not delete issue"
                                    confirmDelete = false
                                }
                            busy = false
                        }
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    editingComment?.let { comment ->
        AlertDialog(
            onDismissRequest = { if (!busy) editingComment = null },
            title = { Text("Edit comment") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text("Comment") },
                    minLines = 2, maxLines = 6,
                    enabled = !busy
                )
            },
            confirmButton = {
                TextButton(enabled = !busy && editText.isNotBlank(), onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        SeerrClient.editIssueComment(instance, comment.id, editText)
                            .onSuccess {
                                editingComment = null
                                onChanged()
                                SeerrClient.issueDetail(instance, issue.id)
                                    .onSuccess { issue = it }
                                    .onFailure { error = it.message }
                            }
                            .onFailure { error = it.message ?: "Could not edit comment" }
                        busy = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { editingComment = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    deletingComment?.let { comment ->
        AlertDialog(
            onDismissRequest = { if (!busy) deletingComment = null },
            title = { Text("Delete comment?") },
            text = { Text("This permanently removes the comment from Seerr.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        SeerrClient.deleteIssueComment(instance, comment.id)
                            .onSuccess {
                                deletingComment = null
                                onChanged()
                                SeerrClient.issueDetail(instance, issue.id)
                                    .onSuccess { issue = it }
                                    .onFailure { error = it.message }
                            }
                            .onFailure { error = it.message ?: "Could not delete comment" }
                        busy = false
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { deletingComment = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
