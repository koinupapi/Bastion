package com.koinupapi.bastion

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * One native Compose scaffold for Bastion and all service destinations.
 * The caller supplies actions and content; the shared shell owns the chrome.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BastionAppShell(
    title: String,
    subtitle: String?,
    isBack: Boolean,
    onNavigationClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            BastionPageHeader(
                title = title,
                subtitle = subtitle,
                isBack = isBack,
                onNavigationClick = onNavigationClick,
                scrollBehavior = scrollBehavior,
                actions = actions,
            )
        },
        snackbarHost = snackbarHost,
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}
