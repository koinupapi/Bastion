package com.koinupapi.bastion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
 * on the current branch until they are popped. A tab or service switch suspends
 * that branch composition while preserving its saveable UI state and history.
 * A later return restores the branch without running hidden tab effects.
 */
@Composable
internal fun BastionNavigationHost(
    destination: BastionScreenDestination,
    modifier: Modifier = Modifier,
    content: @Composable (BastionScreenDestination) -> Unit,
) {
    val branchStateHolder = rememberSaveableStateHolder()
    // Preserve navigation history across configuration changes, including Search
    // beneath Details. A plain remember map lost that history on rotation.
    val branches = rememberSaveable(saver = Saver<
        MutableMap<String, androidx.compose.runtime.snapshots.SnapshotStateList<BastionScreenDestination>>,
        ArrayList<String>
    >(
        save = { history ->
            arrayListOf<String>().apply {
                history.forEach { (branch, entries) ->
                    add(branch)
                    add(entries.size.toString())
                    entries.forEach { entry ->
                        add(entry.serviceId ?: "")
                        add(entry.tab.toString())
                        add(entry.page)
                        add(entry.depth.toString())
                    }
                }
            }
        },
        restore = { saved ->
            mutableMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<BastionScreenDestination>>().apply {
                var index = 0
                while (index + 1 < saved.size) {
                    val branch = saved[index++]
                    val count = saved[index++].toIntOrNull() ?: break
                    val entries = mutableStateListOf<BastionScreenDestination>()
                    repeat(count) {
                        if (index + 3 < saved.size) {
                            val service = saved[index++].ifEmpty { null }
                            val tab = saved[index++].toIntOrNull() ?: 0
                            val page = saved[index++]
                            val depth = saved[index++].toIntOrNull() ?: 0
                            entries.add(BastionScreenDestination(service, tab, page, depth))
                        }
                    }
                    put(branch, entries)
                }
            }
        },
    )) { mutableMapOf() }
    val branchId = "${destination.serviceId ?: "bastion"}:${destination.tab}"
    val stack = remember(branchId) { branches.getOrPut(branchId) { mutableStateListOf(destination) } }

    // Derive the visible stack without mutating snapshot state during composition.
    // Commit the history after composition; render the derived stack immediately
    // so Back never waits for an effect to reveal the previous destination.
    val visibleEntries = reconcileBastionNavigation(stack, destination)
    SideEffect {
        if (stack.toList() != visibleEntries) {
            stack.clear()
            stack.addAll(visibleEntries)
        }
    }

    // Retain each branch's saveable state when switching tabs or services.
    // Only the active branch is composed: background tabs cannot execute effects
    // or compete for global app-bar, scrolling, and network state.
    key(branchId) {
        branchStateHolder.SaveableStateProvider(branchId) {
            BastionRetainedStack(
                entries = visibleEntries,
                entryKey = { it.identity },
                modifier = modifier,
            ) { entry, _ ->
                content(entry)
            }
        }
    }
}

/** Pure history reconciliation; can be unit-tested without Compose rendering. */
internal fun reconcileBastionNavigation(
    history: List<BastionScreenDestination>,
    destination: BastionScreenDestination,
): List<BastionScreenDestination> {
    val previousIndex = history.indexOfLast { it.identity == destination.identity }
    return when {
        previousIndex >= 0 -> history.take(previousIndex) + destination
        history.isEmpty() -> listOf(destination)
        destination.depth > history.last().depth -> history + destination
        else -> history.dropLast(1) + destination
    }
}
