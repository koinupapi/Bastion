package com.koinupapi.bastion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/** Native, live palette inspector: samples the actual active Compose theme. */
@Composable
fun BastionPaletteSettings(
    dynamic: Boolean,
    selectedSeed: Int,
    onSeedChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Basic colors", style = MaterialTheme.typography.titleMedium)
        Text(
            if (dynamic) "Turn off dynamic colors to choose a Bastion color."
            else "Material generates the full light and dark palette from your chosen color.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BastionColorPresets.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { preset ->
                    Column(
                        modifier = Modifier.weight(1f).clickable(enabled = !dynamic) { onSeedChange(preset.argb) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(48.dp).background(Color(preset.argb), CircleShape),
                            contentAlignment = Alignment.Center) {
                            if (selectedSeed == preset.argb) {
                                Icon(Icons.Default.Check, contentDescription = "Selected",
                                    tint = Color.White)
                            }
                        }
                        Text(preset.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        HorizontalDivider()
        TextButton(onClick = { expanded = !expanded }) {
            Text("Current Material 3 palette")
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null)
        }
        if (expanded) {
            val scheme = MaterialTheme.colorScheme
            val entries = listOf(
                "Primary" to scheme.primary, "On primary" to scheme.onPrimary,
                "Primary container" to scheme.primaryContainer,
                "Secondary" to scheme.secondary, "Secondary container" to scheme.secondaryContainer,
                "Tertiary" to scheme.tertiary, "Tertiary container" to scheme.tertiaryContainer,
                "Background" to scheme.background, "Surface" to scheme.surface,
                "Surface container" to scheme.surfaceContainer,
                "Surface container high" to scheme.surfaceContainerHigh,
                "Surface container highest" to scheme.surfaceContainerHighest,
                "Outline" to scheme.outline, "Error" to scheme.error,
            )
            entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { (name, color) ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(color = color, shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth().height(48.dp)) {}
                            Text(name, style = MaterialTheme.typography.labelSmall)
                            Text(String.format("#%06X", color.toArgb() and 0xFFFFFF),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
