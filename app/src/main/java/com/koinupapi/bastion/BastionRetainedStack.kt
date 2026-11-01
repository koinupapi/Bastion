package com.koinupapi.bastion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * A retained, non-animated stack for destination content.
 *
 * All supplied entries remain in composition while they are in the back stack.
 * Only the top entry is drawn and exposed to accessibility. This is intentionally
 * different from AnimatedContent: popping an entry does not recreate the previous
 * destination's composable subtree.
 *
 * The caller owns the entries and their stable IDs. In particular, never use a
 * page number, filter hash or mutable query as the ID of an existing entry.
 *
 * This foundation does not automatically stop network effects in covered entries;
 * screen models must own and deduplicate requests independently.
 */
@Composable
internal fun <T> BastionRetainedStack(
    entries: List<T>,
    entryKey: (T) -> String,
    modifier: Modifier = Modifier,
    content: @Composable (entry: T, isTop: Boolean) -> Unit,
) {
    val stateHolder = rememberSaveableStateHolder()
    Box(modifier) {
        entries.forEachIndexed { index, entry ->
            val id = entryKey(entry)
            val isTop = index == entries.lastIndex
            key(id) {
                stateHolder.SaveableStateProvider(id) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (isTop) 1f else 0f }
                            .then(if (isTop) Modifier else Modifier.clearAndSetSemantics { }),
                    ) {
                        content(entry, isTop)
                    }
                }
            }
        }
    }
}
