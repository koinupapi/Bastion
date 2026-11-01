package com.koinupapi.bastion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier

/** Immutable snapshot of a navigation destination. */
internal data class BastionScreenDestination(
    val serviceId: String?,
    val tab: Int,
    val page: String,
    val depth: Int,
) {
    val identity: String
        get() {
            val stablePage = if (page.startsWith("browse:")) {
                page.split(':').take(3).joinToString(":")
            } else page
            return "${serviceId ?: "bastion"}:$tab:$stablePage"
        }
}

/**
 * Retained native destination host.
 *
 * The parent owns navigation decisions; this host retains visited destinations
 * on the current branch until they are popped. A tab or service switch begins a
 * separate visible branch. A later return to that branch restores its entries.
 */
@Composable
internal fun BastionNavigationHost(
    destination: BastionScreenDestination,
    modifier: Modifier = Modifier,
    content: @Composable (BastionScreenDestination) -> Unit,
) {
    val branches = remember { mutableStateMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<BastionScreenDestination>>() }
    val branchId = "${destination.serviceId ?: "bastion"}:${destination.tab}"
    val stack = branches.getOrPut(branchId) { mutableStateListOf(destination) }

    // Derive the visible stack without mutating snapshot state during composition.
    // Commit the history after composition; render the derived stack immediately
    // so Back never waits for an effect to reveal the previous destination.
    val previousIndex = stack.indexOfLast { it.identity == destination.identity }
    val visibleEntries = when {
        previousIndex >= 0 -> stack.take(previousIndex) + destination
        stack.isEmpty() -> listOf(destination)
        destination.depth > stack.last().depth -> stack.toList() + destination
        else -> stack.dropLast(1) + destination
    }
    SideEffect {
        if (stack.toList() != visibleEntries) {
            stack.clear()
            stack.addAll(visibleEntries)
        }
    }

    BastionRetainedStack(
        entries = visibleEntries,
        entryKey = { it.identity },
        modifier = modifier,
    ) { entry, _ ->
        content(entry)
    }
}
