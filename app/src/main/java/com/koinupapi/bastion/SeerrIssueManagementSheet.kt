package com.koinupapi.bastion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
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
    LaunchedEffect(instance, initial.id) {
        SeerrClient.issueDetail(instance, initial.id)
            .onSuccess { issue = it }
            .onFailure { error = "Could not load latest issue: ${it.message}" }
        loading = false
    }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.86f)
            .padding(horizontal = 24.dp).padding(bottom = 24.dp),
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
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (issue.comments.isEmpty()) Text("No comments yet.")
                issue.comments.forEachIndexed { index, message ->
                    Surface(shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (index == 0) "Original report" else "Reply ${index}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Text(message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            OutlinedTextField(value = reply, onValueChange = { reply = it },
                label = { Text("Write a reply") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4, minLines = 2, enabled = !busy)
            Button(onClick = {
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
            OutlinedButton(onClick = {
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
        }
    }
}
