package com.koinupapi.bastion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/** Immutable snapshot of a navigation destination. */
internal data class BastionScreenDestination(
    val serviceId: String?,
    val tab: Int,
    val page: String,
    val depth: Int,
) {
    val identity: String get() = "${serviceId ?: "bastion"}:$tab:$page"
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
    val branches = remember { mutableMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<BastionScreenDestination>>() }
    val branchId = "${destination.serviceId ?: "bastion"}:${destination.tab}"
    val stack = remember(branchId) {
        branches.getOrPut(branchId) { mutableStateListOf(destination) }
    }

    // Update the branch synchronously so a Back action reveals the retained
    // previous composition in the same frame, rather than after an effect.
    val previousIndex = stack.indexOfLast { it.identity == destination.identity }
    when {
        previousIndex >= 0 -> {
            while (stack.lastIndex > previousIndex) stack.removeAt(stack.lastIndex)
        }
        stack.isEmpty() -> stack.add(destination)
        destination.depth > stack.last().depth -> stack.add(destination)
        else -> {
            // A sibling replacement (for example changing browse filters) is
            // not a push; keep the underlying history and replace its top entry.
            stack[stack.lastIndex] = destination
        }
    }

    BastionRetainedStack(
        entries = stack,
        entryKey = { it.identity },
        modifier = modifier,
    ) { entry, _ ->
        content(entry)
    }
}
