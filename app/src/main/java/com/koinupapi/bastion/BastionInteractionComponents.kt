package com.koinupapi.bastion

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.graphics.Color

/** Shared expandable surface; clients supply content and optional supporting text. */
@Composable
fun BastionExpandableSection(
    title: String,
    description: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (description != null) Text(description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand")
            }
            BastionReveal(expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(BastionSpacing.medium), content = content)
            }
        }
    }
}

/** Shared search field; no integration-specific colors or shapes. */
@Composable
fun BastionSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(value = value, onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(), singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onValueChange("") }) {
                Icon(Icons.Default.Close, contentDescription = "Clear search")
            } }
        } else null,
        placeholder = { Text(placeholder) })
}

/** Shared native modal sheet; integration content and lifecycle remain caller-owned. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BastionBottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = BottomSheetDefaults.ExpandedShape,
        content = content)
}

/** Short theme-aware contrast scrim; does not alter the media's bottom/title fade. */
@Composable
fun BastionStatusBarScrim(modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surface
    val height = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 48.dp
    Box(modifier.fillMaxWidth().height(height).background(
        Brush.verticalGradient(listOf(surface.copy(alpha = 0.92f),
            surface.copy(alpha = 0.56f), Color.Transparent))))
}

enum class BastionTransferState { Active, Paused, Stopped }

/** Native progress presentation selected by the service's actual transfer state. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BastionTransferProgress(
    progress: Float,
    state: BastionTransferState,
    modifier: Modifier = Modifier,
) {
    val value = progress.coerceIn(0f, 1f)
    val active = state == BastionTransferState.Active
    val stopped = state == BastionTransferState.Stopped
    val wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength
    LinearWavyProgressIndicator(
        progress = { value },
        modifier = modifier,
        color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        amplitude = { if (stopped) 0f else 1f },
        wavelength = wavelength,
        waveSpeed = if (active) wavelength else 0.dp,
    )
}
