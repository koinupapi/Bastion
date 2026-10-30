package com.koinupapi.bastion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeerrReportIssueSheet(instance: ServiceConfig, title: String, mediaId: Int,
    onDismiss: () -> Unit, onSubmitted: () -> Unit) {
    val types = listOf("Video" to 1, "Audio" to 2, "Subtitles" to 3, "Other" to 4)
    var selected by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss,
        shape = BastionShapes.bottomSheet) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = BastionSheetDefaults.horizontalPadding).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(BastionSpacing.large)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = BastionShapes.mediaArtwork,
                    color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Default.ReportProblem, contentDescription = null,
                        modifier = Modifier.padding(16.dp).size(28.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Column {
                    Text("Report issue", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text("Tell Seerr what's wrong with this movie",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(value = types[selected].first, onValueChange = {},
                    readOnly = true, label = { Text("Issue category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(
                        ExposedDropdownMenuAnchorType.PrimaryNotEditable))
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    types.forEachIndexed { index, type ->
                        DropdownMenuItem(text = { Text(type.first) }, onClick = {
                            selected = index
                            expanded = false
                        })
                    }
                }
            }
            OutlinedTextField(value = description, onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp),
                label = { Text("Describe the problem") },
                minLines = 3, maxLines = 6)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                submitting = true
                error = null
                scope.launch {
                    val result = SeerrClient.submitIssue(instance, mediaId,
                        types[selected].second, description.trim())
                    submitting = false
                    result.onSuccess { onSubmitted() }
                        .onFailure { error = it.message ?: "Unable to report issue" }
                }
            }, enabled = !submitting && mediaId > 0 && description.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = BastionShapes.prominentAction) {
                if (submitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Submit issue")
            }
            if (mediaId <= 0) Text("Seerr hasn't returned a media record for this title.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
