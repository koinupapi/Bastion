package com.koinupapi.bastion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Shared semantic surface; native Material 3 shape and tonal color tokens. */
@Composable
fun BastionContentCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(BastionSpacing.medium),
            content = content)
    }
}

/** Use Material's native button variants, not custom-rendered buttons. */
enum class BastionActionStyle { Primary, Secondary, Tonal, Destructive }

@Composable
fun BastionActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: BastionActionStyle = BastionActionStyle.Primary,
) {
    when (style) {
        BastionActionStyle.Primary -> Button(onClick = onClick, modifier = modifier, enabled = enabled) { Text(label) }
        BastionActionStyle.Secondary -> OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) { Text(label) }
        BastionActionStyle.Tonal -> FilledTonalButton(onClick = onClick, modifier = modifier, enabled = enabled) { Text(label) }
        BastionActionStyle.Destructive -> TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Text(label, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Standard input presentation with native error and supporting text behavior. */
@Composable
fun BastionTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        isError = error != null,
        supportingText = when {
            error != null -> ({ Text(error) })
            supportingText != null -> ({ Text(supportingText) })
            else -> null
        },
    )
}
