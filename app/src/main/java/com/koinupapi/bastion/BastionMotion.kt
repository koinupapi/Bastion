package com.koinupapi.bastion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared navigation motion. Screen content is keyed by destination, not service type.
 * The direction hint lets nested settings travel forward/back while tabs use fade-through.
 */
@Composable
fun <T : Any> BastionDestinationTransition(
    destination: T,
    modifier: Modifier = Modifier,
    direction: (from: T, to: T) -> Int = { _, _ -> 0 },
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = destination,
        modifier = modifier,
        transitionSpec = {
            val sign = direction(initialState, targetState)
            val enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) {
                    if (sign == 0) 0 else sign * it / 8
                }
            val exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) {
                    if (sign == 0) 0 else -sign * it / 8
                }
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        label = "Bastion destination",
        content = { state -> content(state) },
    )
}

/** Shared spring-based reveal for search, filters, and future expandable controls. */
@Composable
fun BastionReveal(
    visible: Boolean,
    content: @Composable androidx.compose.animation.AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
            androidx.compose.animation.expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
            androidx.compose.animation.shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        content = content,
    )
}

/**
 * Common destination identity for the app shell. All services use the same
 * navigation transition host; only the supplied destination keys differ.
 */
data class BastionScreenDestination(
    val serviceId: String?,
    val tab: Int,
    val page: String = "root",
    val depth: Int = 0,
)

@Composable
fun BastionNavigationHost(
    destination: BastionScreenDestination,
    modifier: Modifier = Modifier,
    content: @Composable (BastionScreenDestination) -> Unit,
) {
    BastionDestinationTransition(
        destination = destination,
        modifier = modifier,
        direction = { from, to ->
            when {
                from.serviceId != to.serviceId -> 0
                from.tab != to.tab -> 0
                to.depth < from.depth -> -1
                to.depth > from.depth -> 1
                else -> 0
            }
        },
        content = content,
    )
}
