package com.koinupapi.bastion

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Native Material 3 pull-to-refresh for live Seerr Discover API data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BastionDiscoverRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier,
        content = content,
    )
}
