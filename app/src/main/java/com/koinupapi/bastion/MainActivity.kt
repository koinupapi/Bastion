package com.koinupapi.bastion

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import java.time.LocalDate
import java.time.YearMonth
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import coil.compose.AsyncImage
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val services = listOf("Sonarr", "Radarr", "qBittorrent", "Seerr", "Plex", "Docker")
private data class Integration(val name: String, val category: String)
private val integrationCatalog = listOf(
    Integration("Docker", "Infrastructure"),
    Integration("Uptime Kuma", "Infrastructure"),
    Integration("Seerr", "Media management"),
    Integration("Sonarr", "Media management"),
    Integration("Radarr", "Media management"),
    Integration("qBittorrent", "Media management"),
    Integration("Plex", "Media servers"),
    Integration("Audiobookshelf", "Media servers"),
    Integration("Navidrome", "Media servers"),
    Integration("Immich", "Other services")
)

private val containers = listOf("sonarr", "radarr", "qbittorrent", "plex", "seerr", "uptime-kuma")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BastionApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BastionApp() {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("bastion_settings", android.content.Context.MODE_PRIVATE) }
    var hideNavOnScroll by remember { mutableStateOf(preferences.getBoolean("hide_nav_on_scroll", false)) }
    var hideHeaderOnScroll by remember { mutableStateOf(preferences.getBoolean("hide_header_on_scroll", false)) }
    var navVisible by remember { mutableStateOf(true) }
    val navigationScrollConnection = remember(hideNavOnScroll) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (hideNavOnScroll && source == NestedScrollSource.UserInput) {
                    if (available.y < -5f) navVisible = false
                    else if (available.y > 5f) navVisible = true
                }
                return Offset.Zero
            }
        }
    }
    var dark by remember { mutableStateOf(preferences.getBoolean("dark_theme", false)) }
    var dynamic by remember { mutableStateOf(preferences.getBoolean("dynamic_colors", true)) }
    var showAllNavLabels by remember { mutableStateOf(preferences.getBoolean("show_all_nav_labels", true)) }
    var showInstanceSubtitle by remember { mutableStateOf(preferences.getBoolean("show_instance_subtitle", true)) }
    var returnToDashboard by remember { mutableStateOf(preferences.getBoolean("return_to_dashboard", true)) }
    var doubleBackToExit by remember { mutableStateOf(preferences.getBoolean("double_back_exit", true)) }
    val activity = context as ComponentActivity
    SideEffect {
        activity.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        @Suppress("DEPRECATION")
        activity.window.isNavigationBarContrastEnforced = false
    }
    BastionTheme(dark = dark, dynamic = dynamic) {
        var tab by remember { mutableIntStateOf(0) }
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        var selectedMediaId by remember { mutableStateOf<String?>(null) }
        var seerrTab by remember(selectedMediaId) { mutableIntStateOf(0) }
        var seerrSettingsSection by remember(selectedMediaId) { mutableStateOf("root") }
        var seerrSearching by remember(selectedMediaId) { mutableStateOf(false) }
        val seerrMediaStack = remember { mutableStateListOf<SeerrRecentRequest>() }
        val seerrMediaCache = remember { mutableStateMapOf<String, SeerrMediaDetails>() }
        val seerrScrollStates = remember(selectedMediaId) { mutableMapOf<String, androidx.compose.foundation.lazy.LazyListState>() }
        val seerrCarouselScrollStates = remember(selectedMediaId) { mutableMapOf<String, androidx.compose.foundation.ScrollState>() }
        val seerrDiscoverCache = remember(selectedMediaId) { mutableStateMapOf<String, SeerrDiscoverResult>() }
        val seerrPageStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
        val openSeerrRequest = seerrMediaStack.lastOrNull()
        val popSeerrMedia = { if (seerrMediaStack.isNotEmpty()) seerrMediaStack.removeAt(seerrMediaStack.lastIndex) }
        var settingsPage by remember { mutableStateOf("Settings") }
        var selectedIntegration by remember { mutableStateOf("Seerr") }
        var serviceSearch by remember { mutableStateOf("") }
        var previewName by remember { mutableStateOf("") }
        val serviceStore = remember(context) { ServiceStore(context) }
        val savedInstances = remember { mutableStateListOf<ServiceConfig>().apply { addAll(serviceStore.load()) } }
        var editingId by remember { mutableStateOf<String?>(null) }
        var localUrl by remember { mutableStateOf("") }
        var remoteUrl by remember { mutableStateOf("") }
        var apiKey by remember { mutableStateOf("") }
        var seerrAuthMethod by remember { mutableStateOf("API key") }
        var loginEmail by remember { mutableStateOf("") }
        var loginPassword by remember { mutableStateOf("") }
        var loggingIn by remember { mutableStateOf(false) }
        var connectionMode by remember { mutableStateOf("Automatic") }
        var testResult by remember { mutableStateOf("") }
        var isTesting by remember { mutableStateOf(false) }
        var deleteConfirmation by remember { mutableStateOf(false) }
        fun persistServices() { serviceStore.save(savedInstances.toList()) }
        fun editService(config: ServiceConfig?) {
            editingId = config?.id
            selectedIntegration = config?.type ?: selectedIntegration
            previewName = config?.name ?: selectedIntegration
            localUrl = config?.localUrl.orEmpty()
            remoteUrl = config?.remoteUrl.orEmpty()
            apiKey = config?.apiKey.orEmpty()
            seerrAuthMethod = if (apiKey.startsWith("session:")) "Local user" else "API key"
            loginEmail = ""
            loginPassword = ""
            connectionMode = config?.mode ?: "Automatic"
            testResult = ""
        }
        val settingsHistory = remember { mutableStateListOf<String>() }
        fun openSettingsPage(page: String) {
            settingsHistory.add(settingsPage)
            settingsPage = page
        }
        fun goBackSettings() {
            if (settingsHistory.isNotEmpty()) settingsPage = settingsHistory.removeAt(settingsHistory.lastIndex)
            else settingsPage = "Settings"
        }
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        var lastExitBackAt by remember { mutableLongStateOf(0L) }
        BackHandler {
            when {
                openSeerrRequest != null -> popSeerrMedia()
                selectedMediaId != null -> {
                    if (seerrTab == 4 && seerrSettingsSection != "root") {
                        seerrSettingsSection = when (seerrSettingsSection) {
                            "Plex", "Radarr", "Sonarr", "Override Rules" -> "Media Services"
                            "Logs", "Scheduled Jobs", "Cache Management" -> "Maintenance & Diagnostics"
                            else -> "root"
                        }
                    } else {
                    // Back from a Seerr service tab returns to its home (Discover),
                    // not the global Bastion dashboard. Nested Settings back is
                    // handled by SeerrSettingsScreen while a subpage is open.
                    if (seerrTab != 0) seerrTab = 0
                    else {
                        selectedMediaId = null
                        tab = 0
                        settingsHistory.clear()
                        settingsPage = "Settings"
                    }
                    }
                }
                tab == 2 && settingsPage != "Settings" -> goBackSettings()
                tab != 0 && returnToDashboard -> {
                    tab = 0
                    settingsPage = "Settings"
                }
                !doubleBackToExit -> activity.finish()
                else -> {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastExitBackAt <= 2000L) {
                        activity.finish()
                    } else {
                        lastExitBackAt = now
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                message = "Press Back again to exit Bastion",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                }
            }
        }
        val labels = listOf("Dash", "Calendar", "Settings")
        val seerrLabels = listOf("Discover", "Requests", "Issues", "Users", "Settings")
        val serviceScreen = selectedMediaId != null
        val nestedBastionSettings = !serviceScreen && tab == 2 && settingsPage != "Settings"
        val nestedSeerrSettings = serviceScreen && seerrTab == 4 && seerrSettingsSection != "root"
        val headerTitle = when {
            openSeerrRequest != null -> openSeerrRequest.title
            nestedSeerrSettings -> seerrSettingsSection
            serviceScreen -> seerrLabels[seerrTab]
            nestedBastionSettings -> if (settingsPage == "Service detail") selectedIntegration else settingsPage
            tab == 0 -> "Bastion"
            else -> if (tab == 1) "Calendar" else "Settings"
        }
        val headerContext = if (serviceScreen && showInstanceSubtitle) savedInstances.firstOrNull { it.id == selectedMediaId }?.name else null
        val headerBack = openSeerrRequest != null || nestedSeerrSettings || nestedBastionSettings
        val topBarScrollBehavior = if (hideHeaderOnScroll) TopAppBarDefaults.enterAlwaysScrollBehavior() else TopAppBarDefaults.pinnedScrollBehavior()
        val icons = listOf(Icons.Default.Home, Icons.Default.CalendarMonth, Icons.Default.Settings)
        ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
            BastionNavigationDrawerSheet {
                Column(Modifier.fillMaxHeight().padding(horizontal = 12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 24.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Bastion", style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold)
                            Text("Your homelab", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("YOUR SERVICES", modifier = Modifier.padding(start = 16.dp, bottom = 10.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
                        val categories = savedInstances.groupBy { config ->
                            integrationCatalog.firstOrNull { it.name == config.type }?.category ?: "Other services"
                        }
                        categories.forEach { (category, entries) ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(category, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                entries.forEachIndexed { index, service ->
                                    val shape = connectedServiceShape(index, entries.size)
                                    Surface(onClick = {
                                        if (selectedMediaId != service.id) {
                                            seerrMediaStack.clear()
                                            seerrTab = 0
                                        }
                                        selectedMediaId = service.id
                                        scope.launch { drawerState.close() }
                                    }, shape = shape,
                                        color = if (selectedMediaId == service.id)
                                            MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Icon(if (service.type == "Seerr") Icons.Default.Movie
                                                else Icons.Default.Dns, contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(14.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(service.name, style = MaterialTheme.typography.titleSmall)
                                                Text(service.type, style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (selectedMediaId == service.id)
                                                Icon(Icons.Default.Check, contentDescription = "Selected")
                                        }
                                    }
                                }
                            }
                        }
                        if (savedInstances.isEmpty()) {
                            Text("Add a service in Settings to see it here.",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    NavigationDrawerItem(
                        label = { Text("Manage services") },
                        selected = false,
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        onClick = {
                            selectedMediaId = null
                            seerrMediaStack.clear()
                            tab = 2
                            settingsHistory.clear()
                            settingsPage = "Services"
                            scope.launch { drawerState.close() }
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }) {
        LaunchedEffect(tab, selectedMediaId, seerrTab, settingsPage, hideNavOnScroll) { navVisible = true }
        BastionAppShell(
            title = headerTitle,
            subtitle = headerContext,
            isBack = headerBack,
            scrollBehavior = topBarScrollBehavior,
            onNavigationClick = {
                        when {
                            openSeerrRequest != null -> popSeerrMedia()
                            nestedSeerrSettings -> seerrSettingsSection = when (seerrSettingsSection) {
                                "Plex", "Radarr", "Sonarr", "Override Rules" -> "Media Services"
                                "Logs", "Scheduled Jobs", "Cache Management" -> "Maintenance & Diagnostics"
                                else -> "root"
                            }
                            nestedBastionSettings -> goBackSettings()
                            else -> scope.launch { drawerState.open() }
                        }
            },
            actions = {
                        if (serviceScreen && seerrTab == 0 && openSeerrRequest == null) {
                            IconButton(onClick = { seerrSearching = !seerrSearching }) {
                                Icon(if (seerrSearching) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = if (seerrSearching) "Close search" else "Search movies and TV")
                            }
                        }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { insets ->
            Box(Modifier.fillMaxSize()) {
            val activeDestination = BastionScreenDestination(
                serviceId = selectedMediaId,
                tab = if (selectedMediaId == null) tab else seerrTab,
                page = when {
                    openSeerrRequest != null -> "media:${seerrMediaStack.size}:${openSeerrRequest.tmdbId}"
                    selectedMediaId != null && seerrTab == 4 -> seerrSettingsSection
                    selectedMediaId == null && tab == 2 -> settingsPage
                    else -> "root"
                },
                depth = when {
                    openSeerrRequest != null -> seerrMediaStack.size
                    selectedMediaId != null && seerrTab == 4 -> when (seerrSettingsSection) {
                        "root" -> 0
                        "Plex", "Radarr", "Sonarr", "Override Rules",
                        "Logs", "Scheduled Jobs", "Cache Management" -> 2
                        else -> 1
                    }
                    selectedMediaId == null && tab == 2 -> settingsHistory.size
                    else -> 0
                },
            )
            BastionNavigationHost(destination = activeDestination, modifier = Modifier.fillMaxSize()) { destination ->
                val scrollKey = if (destination.page.startsWith("media:")) destination.page
                    else "main-${destination.serviceId ?: "bastion"}-${destination.tab}-${destination.page}"
                val screenScroll = seerrScrollStates.getOrPut(scrollKey) {
                    androidx.compose.foundation.lazy.LazyListState()
                }
            LazyColumn(
                state = screenScroll,
                overscrollEffect = rememberOverscrollEffect(),
                modifier = Modifier.fillMaxSize().nestedScroll(navigationScrollConnection).padding(insets),
                contentPadding = PaddingValues(start = if (selectedMediaId != null && savedInstances.any { it.id == selectedMediaId && it.type == "Seerr" }) 0.dp else 16.dp, top = if (openSeerrRequest != null) 0.dp else 16.dp, end = if (selectedMediaId != null && savedInstances.any { it.id == selectedMediaId && it.type == "Seerr" }) 0.dp else 16.dp,
                    bottom = if (openSeerrRequest != null) 28.dp else if (!hideNavOnScroll || navVisible) 100.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(BastionSpacing.large)
            ) {
                if (destination.serviceId == null && destination.tab == 0) item { DemoNotice() }
                if (destination.serviceId != null) {
                    val service = savedInstances.firstOrNull { it.id == destination.serviceId }
                    if (service?.type == "Seerr") {
                        if (destination.page.startsWith("media:") && openSeerrRequest != null) item { SeerrRequestDetails(service, openSeerrRequest!!,
                            cache = seerrMediaCache,
                            carouselScrollStates = seerrCarouselScrollStates,
                            navigationDepth = seerrMediaStack.size,
                            onBack = { popSeerrMedia() },
                            onRelatedClick = { seerrMediaStack.add(it) }) }
                        else item {
                            seerrPageStateHolder.SaveableStateProvider("seerr-${service.id}-${destination.tab}-${destination.page}") {
                                Box(Modifier.fillMaxWidth().then(if (destination.tab == 0) Modifier else Modifier.padding(horizontal = 16.dp))) {
                                    SeerrManagementScreen(service, destination.tab, seerrDiscoverCache,
                                        destination.page, { seerrSettingsSection = it },
                                        seerrSearching, { seerrSearching = it }) {
                                        seerrMediaStack.add(it)
                                    }
                                }
                            }
                        }
                    }
                    else item { Text("Service management coming soon.") }
                } else when (destination.tab) {
                    0 -> {
                        item { ServerWidget() }
                        item { DownloadsWidget() }
                        item { RequestsWidget(savedInstances.filter { it.type == "Seerr" }) }
                        item { SonarrWidget() }
                        item { RadarrWidget() }
                        item { ServicesWidget() }
                    }
                    1 -> { item { UnifiedCalendarScreen() } }
                    else -> {
                        if (destination.page == "Settings") {
                            item { Text("Customize Bastion and manage your integrations",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            item {
                                BastionGroupedSettings {
                                    BastionSettingsDestination("Appearance", "Theme, dynamic colors and display",
                                        Icons.Default.Palette, index = 0, count = 2) {
                                        openSettingsPage("Appearance")
                                    }
                                    BastionSettingsDestination("Behavior", "Navigation and exit preferences",
                                        Icons.Default.Tune, index = 1, count = 2) {
                                        openSettingsPage("Behavior")
                                    }
                                }
                            }
                            item {
                                BastionGroupedSettings {
                                    BastionSettingsDestination("Services", "Manage homelab integrations",
                                        Icons.Default.Widgets, index = 0, count = 2) {
                                        openSettingsPage("Services")
                                    }
                                    BastionSettingsDestination("Notifications", "Alerts and notification preferences",
                                        Icons.Default.Notifications, index = 1, count = 2) {
                                        openSettingsPage("Notifications")
                                    }
                                }
                            }
                            item {
                                BastionGroupedSettings {
                                    BastionSettingsDestination("About & Updates", "App version, updates and release information",
                                        Icons.Default.Info, index = 0, count = 1) {
                                        openSettingsPage("About & Updates")
                                    }
                                }
                            }
                        } else {
                            when (destination.page) {
                                "About & Updates" -> item {
                                    BastionAboutScreen(openUpdates = { openSettingsPage("Check for updates") })
                                }
                                "Check for updates" -> item {
                                    BastionUpdateSettings()
                                }
                                "Notifications" -> item {
                                    Column(verticalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
                                        Text("Notifications", style = MaterialTheme.typography.headlineSmall)
                                        Text("Notification preferences are not available yet. This section will house alert and notification controls as integrations gain support.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                "Appearance" -> item {
                                    BastionGroupedSettings {
                                        BastionSwitchSetting(
                                            title = "Dark theme",
                                            checked = dark,
                                            onCheckedChange = {
                                                dark = it
                                                preferences.edit().putBoolean("dark_theme", it).apply()
                                            },
                                            index = 0, count = 4,
                                        )
                                        BastionSwitchSetting(
                                            title = "Dynamic colors",
                                            checked = dynamic,
                                            onCheckedChange = {
                                                dynamic = it
                                                preferences.edit().putBoolean("dynamic_colors", it).apply()
                                            },
                                            index = 1, count = 4,
                                        )
                                        BastionSwitchSetting(
                                            title = "Show instance name in top bar",
                                            description = "Display the selected service instance below the page title.",
                                            checked = showInstanceSubtitle,
                                            onCheckedChange = {
                                                showInstanceSubtitle = it
                                                preferences.edit().putBoolean("show_instance_subtitle", it).apply()
                                            },
                                            index = 2, count = 4,
                                        )
                                        BastionSettingsContent(
                                            index = 3, count = 4,
                                            content = {
                                                Column(Modifier.fillMaxWidth()) {
                                                Text("Navigation labels", style = MaterialTheme.typography.titleMedium)
                                                Text("Choose which destination names appear in the bottom bar.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                SingleChoiceSegmentedButtonRow(
                                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                                                ) {
                                                    SegmentedButton(
                                                        selected = showAllNavLabels,
                                                        onClick = {
                                                            showAllNavLabels = true
                                                            preferences.edit().putBoolean("show_all_nav_labels", true).apply()
                                                        },
                                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                                    ) { Text("All labels") }
                                                    SegmentedButton(
                                                        selected = !showAllNavLabels,
                                                        onClick = {
                                                            showAllNavLabels = false
                                                            preferences.edit().putBoolean("show_all_nav_labels", false).apply()
                                                        },
                                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                                    ) { Text("Selected only") }
                                                }
                                                }
                                            },
                                        )
                                    }
                                }
                                "Behavior" -> item {
                                    Column(verticalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
                                        Text("Back button behavior", style = MaterialTheme.typography.titleMedium)
                                        Text("When you press Back from a main navigation tab:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        BastionGroupedSettings {
                                            BastionChoiceSetting(
                                                title = "Exit from current tab",
                                                description = "Back initiates exiting Bastion.",
                                                selected = !returnToDashboard,
                                                index = 0, count = 2,
                                                onClick = {
                                                    returnToDashboard = false
                                                    preferences.edit().putBoolean("return_to_dashboard", false).apply()
                                                },
                                            )
                                            BastionChoiceSetting(
                                                title = "Return to Dashboard",
                                                description = "Go Home first from Servers, Media, or Settings.",
                                                selected = returnToDashboard,
                                                index = 1, count = 2,
                                                onClick = {
                                                    returnToDashboard = true
                                                    preferences.edit().putBoolean("return_to_dashboard", true).apply()
                                                },
                                            )
                                        }
                                        BastionGroupedSettings {
                                            BastionSwitchSetting(
                                                title = "Double Back to exit",
                                                description = "Press Back twice within 2 seconds to exit.",
                                                checked = doubleBackToExit,
                                                onCheckedChange = {
                                                    doubleBackToExit = it
                                                    preferences.edit().putBoolean("double_back_exit", it).apply()
                                                },
                                                index = 0, count = 1,
                                            )
                                        }
                                        Text("Scrolling behavior", style = MaterialTheme.typography.titleMedium)
                                        BastionGroupedSettings {
                                        BastionSwitchSetting(
                                            title = "Hide bottom bar while scrolling",
                                            description = "Scroll down to hide the bottom bar; scroll up to show it.",
                                            checked = hideNavOnScroll,
                                            onCheckedChange = {
                                                hideNavOnScroll = it
                                                preferences.edit().putBoolean("hide_nav_on_scroll", it).apply()
                                            },
                                            index = 0, count = 2,
                                        )
                                        BastionSwitchSetting(
                                            title = "Hide top bar while scrolling",
                                            description = "Scroll down to hide the page header; scroll up to show it.",
                                            checked = hideHeaderOnScroll,
                                            onCheckedChange = {
                                                hideHeaderOnScroll = it
                                                preferences.edit().putBoolean("hide_header_on_scroll", it).apply()
                                            },
                                            index = 1, count = 2,
                                        )
                                        }

                                    }
                                }
                                "Services" -> {
                                    item {
                                        Text("Manage your configured integrations and add new ones.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    item {
                                        SettingsEntry("Add service", "Browse available integrations", Icons.Default.AddCircle) {
                                            serviceSearch = ""
                                            openSettingsPage("Add service")
                                        }
                                    }
                                    item { SectionTitle("Your services") }
                                    if (savedInstances.isEmpty()) {
                                        item {
                                            Card(shape = MaterialTheme.shapes.extraLarge) {
                                                Column(Modifier.fillMaxWidth().padding(24.dp),
                                                    verticalArrangement = Arrangement.spacedBy(BastionSpacing.small)) {
                                                    Icon(Icons.Default.Extension, contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary)
                                                    Text("No services configured yet", style = MaterialTheme.typography.titleMedium)
                                                    Text("Add Seerr to connect your server. Other integrations are coming later.",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    } else {
                                        val groups = savedInstances.groupBy { it.type }
                                        groups.forEach { (type, instances) ->
                                            item {
                                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    Text(type, style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(bottom = 8.dp))
                                                    instances.forEachIndexed { index, instance ->
                                                        SettingsEntry(instance.name, instance.type + " • Configured",
                                                            Icons.Default.Dns,
                                                            index = index, count = instances.size) {
                                                            editService(instance)
                                                            openSettingsPage("Service detail")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                "Add service" -> {
                                    item {
                                        OutlinedTextField(
                                            value = serviceSearch,
                                            onValueChange = { serviceSearch = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("Search integrations") },
                                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                            singleLine = true
                                        )
                                    }
                                    val filtered = integrationCatalog.filter {
                                        it.name.contains(serviceSearch.trim(), ignoreCase = true) ||
                                            it.category.contains(serviceSearch.trim(), ignoreCase = true)
                                    }
                                    if (filtered.isEmpty()) {
                                        item { BastionNoSearchResults(serviceSearch) }
                                    }
                                    listOf("Infrastructure", "Media management", "Media servers", "Other services").forEach { category ->
                                        val matches = filtered.filter { it.category == category }
                                        if (matches.isNotEmpty()) {
                                            item { SectionTitle(category) }
                                            items(matches, key = { it.name }) { integration ->
                                                SettingsEntry(integration.name, "Add ${integration.name} instance",
                                                    Icons.Default.Add) {
                                                    selectedIntegration = integration.name
                                                    editService(null)
                                                    openSettingsPage("Service detail")
                                                }
                                            }
                                        }
                                    }
                                }
                                "Service detail" -> item {
                                    if (selectedIntegration != "Seerr") {
                                        Card(shape = MaterialTheme.shapes.extraLarge) {
                                            Column(Modifier.fillMaxWidth().padding(20.dp),
                                                verticalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
                                                Text("Coming soon", style = MaterialTheme.typography.titleLarge)
                                                Text("Real connections for ${selectedIntegration} aren't implemented yet. Seerr is our first integration.")
                                            }
                                        }
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(BastionSpacing.large)) {
                                            Column(Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
                                                    Text("SERVICE", style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.primary)
                                                    BastionSettingsContent(index = 0, count = 1) {
                                                        Row(verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
                                                            Icon(Icons.Default.Dns, contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary)
                                                            Column {
                                                                Text("Seerr", style = MaterialTheme.typography.titleMedium)
                                                                Text("Media requests", style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            }
                                                        }
                                                    }
                                                    OutlinedTextField(value = previewName, onValueChange = { previewName = it },
                                                        label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                                    Text("CONNECTION", style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.primary)
                                                    OutlinedTextField(value = localUrl, onValueChange = { localUrl = it },
                                                        label = { Text("Local URL (HTTP or HTTPS)") },
                                                        placeholder = { Text("http://192.168.1.100:5055") },
                                                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                                                    OutlinedTextField(value = remoteUrl, onValueChange = { remoteUrl = it },
                                                        label = { Text("Remote URL (HTTPS)") },
                                                        placeholder = { Text("https://seerr.example.com") },
                                                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                                                    Text("AUTHENTICATION", style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.primary)
                                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                                        listOf("API key", "Local user").forEachIndexed { index, method ->
                                                            SegmentedButton(
                                                                selected = seerrAuthMethod == method,
                                                                onClick = {
                                                                    if (seerrAuthMethod != method) {
                                                                        seerrAuthMethod = method
                                                                        apiKey = ""
                                                                        testResult = ""
                                                                    }
                                                                },
                                                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                                                            ) { Text(method) }
                                                        }
                                                    }
                                                    if (seerrAuthMethod == "API key") {
                                                        OutlinedTextField(value = apiKey, onValueChange = { apiKey = it },
                                                            label = { Text("Seerr API key") },
                                                            visualTransformation = PasswordVisualTransformation(),
                                                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                                                    } else {
                                                        Text("Sign in with a local Seerr account. Only the encrypted session is saved.",
                                                            style = MaterialTheme.typography.bodySmall)
                                                        OutlinedTextField(value = loginEmail, onValueChange = { loginEmail = it },
                                                            label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                                        OutlinedTextField(value = loginPassword, onValueChange = { loginPassword = it },
                                                            label = { Text("Password") },
                                                            visualTransformation = PasswordVisualTransformation(),
                                                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                                                        OutlinedButton(enabled = !loggingIn && loginEmail.isNotBlank() &&
                                                            loginPassword.isNotBlank() && (localUrl.isNotBlank() || remoteUrl.isNotBlank()),
                                                            onClick = {
                                                                loggingIn = true
                                                                testResult = "Signing in…"
                                                                scope.launch {
                                                                    SeerrClient.loginLocal(localUrl.trim(), remoteUrl.trim(),
                                                                        connectionMode, loginEmail, loginPassword)
                                                                        .onSuccess {
                                                                            apiKey = it.first
                                                                            testResult = "Signed in via " + it.second + ". Save the connection."
                                                                        }.onFailure {
                                                                            apiKey = ""
                                                                            testResult = it.message ?: "Sign-in failed"
                                                                        }
                                                                    loginPassword = ""
                                                                    loggingIn = false
                                                                }
                                                            }, modifier = Modifier.fillMaxWidth()) {
                                                            Text(if (loggingIn) "Signing in…" else "Sign in to Seerr")
                                                        }
                                                    }
                                                    Text("CONNECTION PREFERENCE", style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.primary)
                                                    listOf("Automatic", "Local only", "Remote only").forEach { mode ->
                                                        Row(verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.fillMaxWidth().clickable { connectionMode = mode }) {
                                                            RadioButton(selected = connectionMode == mode,
                                                                onClick = { connectionMode = mode })
                                                            Text(mode)
                                                        }
                                                    }
                                                    Text("Automatic tries your local address first, then falls back to HTTPS remotely.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    val valid = previewName.isNotBlank() && apiKey.isNotBlank() &&
                                                        (localUrl.isNotBlank() || remoteUrl.isNotBlank()) &&
                                                        (localUrl.isBlank() || localUrl.startsWith("http://") || localUrl.startsWith("https://")) &&
                                                        (remoteUrl.isBlank() || remoteUrl.startsWith("https://")) &&
                                                        (connectionMode != "Local only" || localUrl.isNotBlank()) &&
                                                        (connectionMode != "Remote only" || remoteUrl.isNotBlank())
                                                    OutlinedButton(enabled = valid && !isTesting && seerrAuthMethod == "API key",
                                                        onClick = {
                                                            isTesting = true
                                                            testResult = "Connecting…"
                                                            scope.launch {
                                                                val result = SeerrClient.test(localUrl.trim(), remoteUrl.trim(),
                                                                    apiKey.trim(), connectionMode)
                                                                testResult = result
                                                                isTesting = false
                                                            }
                                                        }, modifier = Modifier.fillMaxWidth()) {
                                                        if (isTesting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                                        else Icon(Icons.Default.WifiFind, contentDescription = null)
                                                        Spacer(Modifier.width(8.dp))
                                                        Text("Test connection")
                                                    }
                                                    if (testResult.isNotBlank()) Text(testResult,
                                                        style = MaterialTheme.typography.bodySmall)
                                                    Button(enabled = valid, onClick = {
                                                        val config = ServiceConfig(
                                                            editingId ?: java.util.UUID.randomUUID().toString(),
                                                            "Seerr", previewName.trim(), localUrl.trim(),
                                                            remoteUrl.trim(), apiKey.trim(), connectionMode
                                                        )
                                                        val index = savedInstances.indexOfFirst { it.id == config.id }
                                                        if (index >= 0) savedInstances[index] = config
                                                        else savedInstances.add(config)
                                                        persistServices()
                                                        settingsHistory.clear()
                                                        settingsHistory.add("Settings")
                                                        settingsPage = "Services"
                                                    }, modifier = Modifier.fillMaxWidth()) {
                                                        Text(if (editingId == null) "Save service" else "Save changes")
                                                    }
                                            }
                                            if (editingId != null) {
                                                OutlinedButton(onClick = { deleteConfirmation = true },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.error)) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("Remove service")
                                                }
                                            }
                                        }
                                        if (deleteConfirmation) {
                                            AlertDialog(onDismissRequest = { deleteConfirmation = false },
                                                title = { Text("Remove ${previewName}?") },
                                                text = { Text("This removes the Bastion connection and its saved API key. Your Seerr server and media will not be changed.") },
                                                confirmButton = {
                                                    TextButton(onClick = {
                                                        savedInstances.removeAll { it.id == editingId }
                                                        persistServices()
                                                        deleteConfirmation = false
                                                        settingsHistory.clear()
                                                        settingsHistory.add("Settings")
                                                        settingsPage = "Services"
                                                    }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                                                },
                                                dismissButton = { TextButton(onClick = { deleteConfirmation = false }) { Text("Cancel") } })
                                        }
                                    }
                                }
                                else -> item { DemoRow("Bastion 0.1.0", "UI preview • simulated data") }
                            }
                        }
                    }
                }
            }
            }
            val activeTabs = if (selectedMediaId != null && savedInstances.any { it.id == selectedMediaId && it.type == "Seerr" }) {
                seerrLabels.mapIndexed { index, label ->
                    BastionNavDestination(
                        label = label,
                        icon = when (index) {
                            0 -> Icons.Default.Explore
                            1 -> Icons.Default.ListAlt
                            2 -> Icons.Default.ReportProblem
                            3 -> Icons.Default.People
                            else -> Icons.Default.Settings
                        },
                        selected = seerrTab == index,
                        onClick = { seerrTab = index },
                    )
                }
            } else if (selectedMediaId == null && (tab != 2 || settingsPage == "Settings")) {
                labels.mapIndexed { index, label ->
                    BastionNavDestination(
                        label = label,
                        icon = icons[index],
                        selected = tab == index,
                        onClick = {
                            tab = index
                            selectedMediaId = null
                            settingsPage = "Settings"
                            settingsHistory.clear()
                            lastExitBackAt = 0L
                        },
                    )
                }
            } else emptyList()
            BastionShellBottomBar(
                visible = activeTabs.isNotEmpty() && openSeerrRequest == null && (!hideNavOnScroll || navVisible),
                destinations = activeTabs,
                showAllLabels = showAllNavLabels,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        }
    }
    }
}

@Composable
private fun DemoNotice() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Science, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Preview • Seerr requests live; other widgets simulated", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun WidgetCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit) {
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun ServerWidget() {
    WidgetCard("Home Server", Icons.Default.Dns) {
        Text("● Online  •  Uptime 12 days", color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(BastionSpacing.large)) {
            Metric("CPU", "18%", 0.18f, Modifier.weight(1f))
            Metric("Memory", "6.8 / 16 GB", 0.425f, Modifier.weight(1f))
        }
        HorizontalDivider()
        Text("21 containers running", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Metric(label: String, value: String, progress: Float, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DownloadsWidget() {
    WidgetCard("qBittorrent", Icons.Default.Download) {
        Text("2 active downloads  •  ↓ 11.5 MB/s", style = MaterialTheme.typography.bodyMedium)
        DownloadLine("Example.Show.S01E04.1080p", 0.72f)
        DownloadLine("Example.Movie.2026.2160p", 0.38f)
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun DownloadLine(name: String, progress: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(name, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
        }
        LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RequestsWidget(instances: List<ServiceConfig>) {
    var results by remember { mutableStateOf<List<SeerrRecentResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val fingerprint = instances.joinToString("|") { it.id + it.apiKey + it.localUrl + it.remoteUrl + it.mode }
    LaunchedEffect(fingerprint, refresh) {
        results = emptyList()
        if (instances.isNotEmpty()) {
            loading = true
            try {
                results = instances.map { SeerrClient.recentRequests(it) }
            } finally { loading = false }
        }
    }
    val total = results.filter { it.error == null }.sumOf { it.total }
    WidgetCard("Seerr requests", Icons.Default.Movie, trailing = {
        if (instances.isNotEmpty() && !loading && results.any { it.error == null }) {
            Surface(shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer) {
                Text("$total requested", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }) {
        if (instances.isEmpty()) {
            Text("Add Seerr in Settings → Services to see live requests.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent requests", Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { refresh++ }, enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh requests")
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            else {
                results.forEach { result ->
                    if (instances.size > 1) Text(result.instance,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    if (result.error != null) {
                        Text(result.error, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        if (result.requests.isEmpty()) BastionEmptyState("All caught up!", "There are no recent requests to show.")
                        result.requests.forEachIndexed { index, request ->
                            if (index > 0) Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
                                Surface(shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.width(58.dp).height(86.dp)) {
                                    if (request.posterUrl != null) {
                                        AsyncImage(model = request.posterUrl,
                                            contentDescription = "Poster for ${request.title}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize())
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Movie, contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Column(Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(request.title + (request.year?.let { " ($it)" } ?: ""),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold)
                                    Text(request.status, style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary)
                                    request.requestedBy?.let { username ->
                                        Text("Requested by $username",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        if (result.total > result.requests.size) {
                            Text("+${result.total - result.requests.size} more",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SonarrWidget() {
    WidgetCard("Sonarr", Icons.Default.Tv) {
        Text("128 series  •  3 upcoming episodes", style = MaterialTheme.typography.bodyMedium)
        Text("Next: Example Series — Tomorrow", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RadarrWidget() {
    WidgetCard("Radarr", Icons.Default.Movie) {
        Text("342 movies  •  2 monitored missing", style = MaterialTheme.typography.bodyMedium)
        Text("Recently added: Example Movie (2026)", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ServicesWidget() {
    WidgetCard("Services", Icons.Default.Widgets) {
        Text("6 demo services  •  All simulated", style = MaterialTheme.typography.bodyMedium)
        Text(services.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DemoRow(title: String, subtitle: String) {
    Card(shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsEntry(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    index: Int = 0,
    count: Int = 1,
    onClick: () -> Unit,
) {
    BastionSettingsDestination(
        title = title,
        description = subtitle,
        icon = icon,
        index = index,
        count = count,
        onClick = onClick,
    )
}

@Composable
private fun SeerrDiscoverScreen(instance: ServiceConfig,
    results: androidx.compose.runtime.snapshots.SnapshotStateMap<String, SeerrDiscoverResult>,
    searching: Boolean, onSearchingChange: (Boolean) -> Unit,
    onMediaClick: (SeerrRecentRequest) -> Unit) {
    val sections = remember { listOf("Trending", "Popular Movies", "Popular TV Shows",
        "Upcoming Movies", "Upcoming TV Shows") }
    var category by androidx.compose.runtime.saveable.rememberSaveable(instance.id) { mutableStateOf("All") }
    var query by remember(instance.id) { mutableStateOf("") }
    var searchResult by remember(instance.id) { mutableStateOf<SeerrDiscoverResult?>(null) }
    var searchLoading by remember(instance.id) { mutableStateOf(false) }
    LaunchedEffect(instance, searching, query) {
        if (!searching || query.trim().length < 2) {
            searchResult = null
            searchLoading = false
        } else {
            searchLoading = true
            kotlinx.coroutines.delay(350)
            searchResult = SeerrClient.discover(instance, "Search", query)
            searchLoading = false
        }
    }
    LaunchedEffect(instance) {
        kotlinx.coroutines.coroutineScope {
            sections.filter { it !in results }.forEach { section ->
                launch {
                    val response = SeerrClient.discover(instance, section)
                    results[section] = response
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        BastionReveal(visible = searching) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
                OutlinedTextField(value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        } }
                    } else null,
                    placeholder = { Text("Search movies and TV shows") })
                if (query.trim().length < 2) {
                    Text("Enter at least two characters to search Seerr.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (searchLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else if (searchResult?.error != null) {
                    Text(searchResult?.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                } else if (searchResult != null && searchResult?.items.isNullOrEmpty()) {
                    Text("No matching movies or TV shows.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    searchResult?.items?.take(20)?.forEach { title ->
                        Row(Modifier.fillMaxWidth().clickable {
                            onMediaClick(SeerrRecentRequest(title = title.title, status = "",
                                posterUrl = title.posterUrl, year = title.year,
                                requestedBy = null, isTv = title.isTv, tmdbId = title.tmdbId))
                        }.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(width = 58.dp, height = 86.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                if (title.posterUrl != null)
                                    AsyncImage(model = title.posterUrl,
                                        contentDescription = title.title,
                                        contentScale = ContentScale.Crop)
                                else Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Movie, contentDescription = null)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(title.title, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text(listOf(if (title.isTv) "TV series" else "Movie",
                                    title.year).filter { it.isNotBlank() }.joinToString(" • "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
        }
        if (!searching) {
        Text("Find something worth watching", modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(BastionSpacing.small)) {
            listOf("All", "Movies", "TV Shows").forEach { choice ->
                FilterChip(selected = category == choice, onClick = { category = choice },
                    label = { Text(choice) },
                    leadingIcon = if (category == choice) {
                        { Icon(Icons.Default.Check, contentDescription = null,
                            modifier = Modifier.size(16.dp)) }
                    } else null)
            }
        }
        sections.filter { section ->
            category == "All" || section == "Trending" ||
                (category == "Movies" && section.contains("Movies")) ||
                (category == "TV Shows" && section.contains("TV"))
        }.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(section, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                val result = results[section]
                val visibleItems = result?.items?.filter {
                    section != "Trending" || category == "All" ||
                        (category == "Movies" && !it.isTv) ||
                        (category == "TV Shows" && it.isTv)
                }.orEmpty()
                if (result == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else if (result.error != null) {
                    Text(result.error, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                } else if (visibleItems.isEmpty()) {
                    Text("No titles found", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(BastionSpacing.medium),
                        contentPadding = PaddingValues(horizontal = 16.dp)) {
                        items(visibleItems, key = { "${it.tmdbId}-${it.isTv}" }) { title ->
                            Column(Modifier.width(124.dp).clickable {
                                onMediaClick(SeerrRecentRequest(
                                    title = title.title, status = "",
                                    posterUrl = title.posterUrl, year = title.year,
                                    requestedBy = null, isTv = title.isTv, tmdbId = title.tmdbId))
                            }, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.width(124.dp).height(186.dp)) {
                                    Surface(shape = RoundedCornerShape(18.dp),
                                        modifier = Modifier.fillMaxSize(),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                        if (title.posterUrl != null)
                                            AsyncImage(model = title.posterUrl,
                                                contentDescription = title.title,
                                                contentScale = ContentScale.Crop)
                                        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Movie, contentDescription = null)
                                        }
                                    }
                                    val badge = when (title.mediaStatus) {
                                        2 -> "Pending"
                                        3 -> "Processing"
                                        4 -> "Partial"
                                        5 -> "Available"
                                        else -> null
                                    }
                                    if (badge != null) {
                                        val tint = when (title.mediaStatus) {
                                            5 -> Color(0xFF167D48)
                                            4 -> Color(0xFF8460B3)
                                            3 -> Color(0xFF246BB1)
                                            else -> Color(0xFFAD741C)
                                        }
                                        Surface(modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                                            shape = RoundedCornerShape(50),
                                            color = tint) {
                                            Text(badge, Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White, maxLines = 1)
                                        }
                                    }
                                }
                                Text(title.title, style = MaterialTheme.typography.labelMedium,
                                    maxLines = 2, minLines = 2, lineHeight = MaterialTheme.typography.labelMedium.lineHeight)
                                Text(listOfNotNull(title.year.takeIf { it.isNotBlank() },
                                    title.rating.takeIf { it > 0 }?.let {
                                        "★ " + String.format(java.util.Locale.US, "%.1f", it)
                                    }).joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SeerrIssuesScreen(instance: ServiceConfig) {
    var filter by remember { mutableStateOf("open") }
    var page by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var result by remember(instance.id) { mutableStateOf<SeerrIssueResult?>(null) }
    var selected by remember { mutableStateOf<SeerrIssue?>(null) }
    LaunchedEffect(instance, filter, page, refresh) {
        result = null
        result = SeerrClient.issuePage(instance, 20, page * 20, filter)
    }
    Column(verticalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
        Text("Reported media problems • Manage issues",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(BastionSpacing.small)) {
            listOf("open", "resolved", "all").forEach { option ->
                FilterChip(selected = filter == option,
                    onClick = { filter = option; page = 0 },
                    label = { Text(option.replaceFirstChar { it.uppercase() }) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${result?.total ?: 0} issues", Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { refresh++ }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh issues")
            }
        }
        if (result == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        result?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (result?.issues?.isEmpty() == true) BastionEmptyState("No issues to show", "Everything looks clear here.")
        result?.issues?.forEach { issue ->
            ElevatedCard(onClick = { selected = issue }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(issue.type + " issue", style = MaterialTheme.typography.titleMedium)
                    Text(if (issue.resolved) "Resolved" else "Open",
                        color = MaterialTheme.colorScheme.primary)
                    if (issue.message.isNotBlank()) Text(issue.message, maxLines = 2)
                    Text(issue.author + " • " + issue.createdAt,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (page > 0 || (result?.total ?: 0) > (page + 1) * 20) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { page-- }, enabled = page > 0) { Text("Previous") }
                Text("Page " + (page + 1))
                TextButton(onClick = { page++ },
                    enabled = (result?.total ?: 0) > (page + 1) * 20) { Text("Next") }
            }
        }
    }
    selected?.let { issue ->
        SeerrIssueManagementSheet(instance, issue,
            onDismiss = { selected = null },
            onChanged = { refresh++ })
    }
}

@Composable
private fun SeerrManagementScreen(instance: ServiceConfig, destination: Int,
    discoverResults: androidx.compose.runtime.snapshots.SnapshotStateMap<String, SeerrDiscoverResult>,
    settingsSection: String, onSettingsSectionChange: (String) -> Unit,
    searching: Boolean, onSearchingChange: (Boolean) -> Unit,
    onRequestClick: (SeerrRecentRequest) -> Unit) {
    var page by androidx.compose.runtime.saveable.rememberSaveable(instance.id) { mutableIntStateOf(0) }
    var filter by androidx.compose.runtime.saveable.rememberSaveable(instance.id) { mutableStateOf("All") }
    var mediaType by androidx.compose.runtime.saveable.rememberSaveable(instance.id) { mutableStateOf("all") }
    var sortOrder by androidx.compose.runtime.saveable.rememberSaveable(instance.id) { mutableStateOf("added") }
    var sortDirection by androidx.compose.runtime.saveable.rememberSaveable(instance.id) { mutableStateOf("desc") }
    var result by remember(instance.id) { mutableStateOf<SeerrRecentResult?>(null) }
    var counts by remember(instance.id) { mutableStateOf<SeerrRequestCounts?>(null) }
    var loading by remember(instance.id) { mutableStateOf(false) }
    var refresh by remember(instance.id) { mutableIntStateOf(0) }
    var managingRequest by remember(instance.id) { mutableStateOf<SeerrRecentRequest?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val pagePreferences = remember(context) {
        context.getSharedPreferences("bastion_seerr_local", android.content.Context.MODE_PRIVATE)
    }
    var pageSize by remember(instance.id) {
        mutableIntStateOf(pagePreferences.getInt("requests_page_size_${instance.id}", 25)
            .takeIf { it in listOf(25, 50, 100) } ?: 25)
    }
    DisposableEffect(instance.id) {
        val key = "requests_page_size_${instance.id}"
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, changed ->
            if (changed == key) {
                pageSize = prefs.getInt(key, 25).takeIf { it in listOf(25, 50, 100) } ?: 25
                page = 0
            }
        }
        pagePreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { pagePreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(instance, destination, page, pageSize, filter, mediaType, sortOrder, sortDirection, refresh) {
        if (destination != 1) return@LaunchedEffect
        loading = true
        result = SeerrClient.requestPage(instance, pageSize, page * pageSize, filter,
            mediaType, sortOrder, sortDirection)
        loading = false
    }
    LaunchedEffect(instance, destination, refresh) {
        if (destination == 1) counts = SeerrClient.requestCounts(instance).getOrNull()
    }
    Column(verticalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
        if (destination == 0) {
            SeerrDiscoverScreen(instance, discoverResults, searching, onSearchingChange, onRequestClick)
        } else if (destination == 2) {
            SeerrIssuesScreen(instance)
        } else if (destination == 3) {
            SeerrUserDirectoryScreen(instance)
        } else if (destination == 4) {
            SeerrSettingsScreen(instance, settingsSection, onSettingsSectionChange)
        } else {
        Text("Browse and manage Seerr requests", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BastionSpacing.small)) {
            var typeExpanded by remember { mutableStateOf(false) }
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { typeExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(when (mediaType) { "movie" -> "Movies"; "tv" -> "TV"; else -> "All media" })
                }
                DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    listOf("all" to "All media", "movie" to "Movies", "tv" to "TV").forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            mediaType = value; page = 0; typeExpanded = false
                        })
                    }
                }
            }
            var sortExpanded by remember { mutableStateOf(false) }
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { sortExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (sortOrder == "added") "Date added" else "Last modified")
                }
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    listOf("added" to "Date added", "modified" to "Last modified").forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            sortOrder = value; page = 0; sortExpanded = false
                        })
                    }
                }
            }
            IconButton(onClick = {
                sortDirection = if (sortDirection == "desc") "asc" else "desc"; page = 0
            }) {
                Icon(if (sortDirection == "desc") Icons.Default.ArrowDownward
                    else Icons.Default.ArrowUpward, contentDescription =
                    if (sortDirection == "desc") "Sort descending" else "Sort ascending")
            }
        }
        counts?.let { summary ->
            Text("Total ${summary.total}  ·  Pending ${summary.pending}  ·  Approved ${summary.approved}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Processing ${summary.processing}  ·  Available ${summary.available}  ·  Completed ${summary.completed}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val requestFilters = listOf("All", "Pending", "Approved")
        val filterInteractions = remember { List(3) { MutableInteractionSource() } }
        val filterPressed = filterInteractions.map { it.collectIsPressedAsState().value }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            requestFilters.forEachIndexed { index, option ->
                val active = filter == option
                val weight by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (filterPressed[index]) 1.2f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "Request filter press width")
                val innerCorner by animateDpAsState(
                    targetValue = if (active || filterPressed[index]) 24.dp else 8.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "Request filter shape")
                val shape = RoundedCornerShape(
                    topStart = if (index == 0) 24.dp else innerCorner,
                    bottomStart = if (index == 0) 24.dp else innerCorner,
                    topEnd = if (index == requestFilters.lastIndex) 24.dp else innerCorner,
                    bottomEnd = if (index == requestFilters.lastIndex) 24.dp else innerCorner)
                Surface(onClick = { filter = option; page = 0 },
                    interactionSource = filterInteractions[index],
                    modifier = Modifier.weight(weight).height(48.dp),
                    shape = shape,
                    color = if (active) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        if (active) {
                            Icon(Icons.Default.Check, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(option, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Additional statuses", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            var extraExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { extraExpanded = true }) {
                    Text(if (filter in listOf("All", "Pending", "Approved")) "Choose status" else filter)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = extraExpanded, onDismissRequest = { extraExpanded = false }) {
                    listOf("Available", "Processing", "Unavailable", "Failed", "Deleted", "Completed").forEach { status ->
                        DropdownMenuItem(text = { Text(status) }, onClick = {
                            filter = status; page = 0; extraExpanded = false
                        })
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (result?.error == null) "${result?.total ?: 0} requests" else "Requests",
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { refresh++ }, enabled = !loading) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh requests")
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        else {
            result?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (result?.error == null && result?.requests?.isEmpty() == true) BastionEmptyState("No requests found", "Try another filter or check back later.")
            result?.requests?.forEach { request ->
                key(request.title, request.requestedBy, request.releaseDate) {
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val cardScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (pressed) 0.975f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium),
                        label = "Request card press"
                    )
                    ElevatedCard(
                        onClick = { onRequestClick(request) },
                        interactionSource = interaction,
                        modifier = Modifier.fillMaxWidth()
                            .graphicsLayer {
                                scaleX = cardScale
                                scaleY = cardScale
                            },
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(BastionSpacing.large)) {
                            Surface(shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.width(88.dp).height(132.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                                if (request.posterUrl != null) AsyncImage(
                                    model = request.posterUrl,
                                    contentDescription = "Poster for ${request.title}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize())
                                else Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Movie, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(BastionSpacing.small)) {
                                Text(request.title + (request.year?.let { " ($it)" } ?: ""),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                val statusColor = when (request.status.lowercase()) {
                                    "available" -> Color(0xFF176B43)
                                    "processing" -> Color(0xFF245DA3)
                                    "pending" -> Color(0xFF996000)
                                    "declined", "failed" -> MaterialTheme.colorScheme.error
                                    "partially available" -> Color(0xFF7652A2)
                                    else -> MaterialTheme.colorScheme.secondary
                                }
                                Surface(shape = RoundedCornerShape(50),
                                    color = statusColor.copy(alpha = 0.16f)) {
                                    Text(request.status,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (request.status.lowercase() in listOf("available", "processing",
                                                "pending", "partially available") &&
                                            !androidx.compose.foundation.isSystemInDarkTheme())
                                            statusColor else MaterialTheme.colorScheme.onSurface)
                                }
                                request.requestedBy?.let {
                                    Text("Requested by $it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(if (request.isTv) "TV series" else "Movie",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { managingRequest = request },
                                enabled = request.requestId > 0) {
                                Icon(Icons.Default.MoreVert,
                                    contentDescription = "Manage request for ${request.title}",
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            if (result?.error == null && (result?.total ?: 0) > pageSize) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { page-- }, enabled = page > 0) { Text("Previous") }
                    Text("Page ${page + 1}", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { page++ },
                        enabled = (page + 1) * pageSize < (result?.total ?: 0)) { Text("Next") }
                }
            }
        }
        }
    }
    managingRequest?.let { request ->
        SeerrRequestManagementSheet(instance, request,
            onDismiss = { managingRequest = null },
            onChanged = { refresh++ })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeerrMovieRequestSheet(instance: ServiceConfig, tmdbId: Int,
    isTv: Boolean = false, seasons: List<SeerrTvSeason> = emptyList(),
    onDismiss: () -> Unit, onConfirmed: (List<Int>) -> Unit, onSubmitted: () -> Unit) {
    var partialEnabled by remember(instance.id) { mutableStateOf<Boolean?>(null) }
    var policyError by remember(instance.id) { mutableStateOf<String?>(null) }
    var options by remember(instance.id) { mutableStateOf<Result<List<SeerrRequestServer>>?>(null) }
    var serverIndex by remember { mutableIntStateOf(0) }
    var profileId by remember { mutableIntStateOf(-1) }
    var rootPath by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var submitted by remember(instance.id, tmdbId, isTv) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val selectedSeasons = remember(tmdbId) { mutableStateListOf<Int>() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(instance, isTv) {
        options = SeerrClient.requestOptions(instance, isTv)
        if (isTv) SeerrClient.partialRequestsEnabled(instance)
            .onSuccess { partialEnabled = it }
            .onFailure {
                // Standard users cannot read the admin-only settings endpoint.
                // Let Seerr validate season selection on submission.
                policyError = if (it.message?.contains("HTTP 403") == true)
                    "Your account cannot read administrator request settings. Select seasons to continue."
                else "Request policy unavailable. Select seasons to continue."
            }
    }
    val servers = options?.getOrNull().orEmpty()
    val server = servers.getOrNull(serverIndex)
    LaunchedEffect(server?.id) {
        profileId = server?.defaultProfile?.takeIf { id -> server.profiles.any { it.id == id } }
            ?: server?.profiles?.firstOrNull()?.id ?: -1
        rootPath = server?.roots?.firstOrNull {
            it.id == server.defaultRoot
        }?.name ?: server?.roots?.firstOrNull()?.name.orEmpty()
    }
    ModalBottomSheet(onDismissRequest = { if (!submitting) { if (submitted) onSubmitted() else onDismiss() } },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = BottomSheetDefaults.ExpandedShape) {
        Column(Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = BastionSheetDefaults.horizontalPadding)
            .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(BastionSpacing.large)) {
            AnimatedVisibility(visible = submitted, enter = fadeIn(), exit = fadeOut()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    val checkScale by animateFloatAsState(
                        targetValue = if (submitted) 1f else 0.6f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "Request success check")
                    Surface(shape = RoundedCornerShape(36.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(112.dp).graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                        }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(68.dp))
                        }
                    }
                    Text("Request submitted!", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(if (isTv) "Your TV series request was sent to Seerr."
                        else "Your movie request was sent to Seerr.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                    Button(onClick = onSubmitted, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = MaterialTheme.shapes.extraLarge) {
                        Text("Done")
                    }
                }
            }
            AnimatedVisibility(visible = !submitted, enter = fadeIn(), exit = fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(BastionSpacing.large)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Default.Movie, contentDescription = null,
                        modifier = Modifier.padding(16.dp).size(28.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Column {
                    Text(if (isTv) "Request TV series" else "Request movie", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text(if (isTv) "Choose seasons and Sonarr settings" else "Choose how to add this movie",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (options == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            options?.exceptionOrNull()?.let {
                Text(it.message ?: "Could not load server options",
                    color = MaterialTheme.colorScheme.error)
            }
            if (options != null && servers.isEmpty() && options?.isSuccess == true)
                Text(if (isTv) "No Sonarr servers are configured in Seerr." else "No Radarr servers are configured in Seerr.")
            if (servers.isNotEmpty()) {
                SeerrRequestDropdown(if (isTv) "Sonarr server" else "Radarr server", server?.name ?: "Choose server",
                    Icons.Default.Dns, servers.map { it.name }, serverIndex) {
                    serverIndex = it
                }
            }
            if (server != null) {
                SeerrRequestDropdown("Quality profile",
                    server.profiles.firstOrNull { it.id == profileId }?.name ?: "Choose profile",
                    Icons.Default.Tune, server.profiles.map { it.name },
                    server.profiles.indexOfFirst { it.id == profileId }) {
                    profileId = server.profiles[it].id
                }
                SeerrRequestDropdown("Root folder", rootPath.ifBlank { "Choose folder" },
                    Icons.Default.Folder, server.roots.map { it.name },
                    server.roots.indexOfFirst { it.name == rootPath }) {
                    rootPath = server.roots[it].name
                }
                if (server.profiles.isEmpty() || server.roots.isEmpty())
                    Text("This server has no selectable profiles or root folders.",
                        color = MaterialTheme.colorScheme.error)
            }
            if (isTv) {
                if (policyError != null) Text(policyError!!,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (partialEnabled == false) Text("Seerr requires requesting all unrequested seasons.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Select seasons", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                if (seasons.isEmpty()) Text("Season information isn't available yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                seasons.filter { it.number > 0 }.forEach { season ->
                    val unavailable = season.status in listOf(2, 3, 4, 5)
                    val forced = partialEnabled == false && !unavailable
                    Surface(onClick = {
                        if (!unavailable && !forced) {
                            if (season.number in selectedSeasons) selectedSeasons.remove(season.number)
                            else selectedSeasons.add(season.number)
                        }
                    }, enabled = !unavailable && !forced,
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
                            Checkbox(checked = season.number in selectedSeasons || forced,
                                onCheckedChange = null, enabled = !unavailable && !forced)
                            Column(Modifier.weight(1f)) {
                                Text(season.name.ifBlank { "Season ${season.number}" },
                                    style = MaterialTheme.typography.titleMedium)
                                Text("${season.episodes} episodes" +
                                    if (unavailable) " • Already requested or available" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.extraLarge,
                enabled = server != null && profileId >= 0 && rootPath.isNotBlank() && !submitting &&
                    (!isTv || (seasons.any { it.number > 0 && it.status !in listOf(2, 3, 4, 5) } &&
                        (partialEnabled == false || selectedSeasons.isNotEmpty()))),
                onClick = {
                    val chosen = server ?: return@Button
                    submitting = true
         

           error = null
                    scope.launch {
                        val response = SeerrClient.submitRequest(instance, tmdbId, isTv,
                            chosen.id, profileId, rootPath,
                            if (isTv && partialEnabled == false) seasons.filter {
                                it.number > 0 && it.status !in listOf(2, 3, 4, 5)
                            }.map { it.number } else selectedSeasons.toList())
                        submitting = false
                        response.onSuccess {
                            val confirmedSeasons = if (isTv && partialEnabled == false)
                                seasons.filter { it.number > 0 && it.status !in listOf(2, 3, 4, 5) }
                                    .map { it.number }
                            else selectedSeasons.toList()
                            submitted = true
                            onConfirmed(confirmedSeasons)
                        }
                            .onFailure { error = it.message ?: "Request failed" }
                    }
                }) {
                if (submitting) CircularProgressIndicator(Modifier.size(20.dp),
                    strokeWidth = 2.dp)
                else {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Submit request")
                }
            }
                }
            }
        }
    }
}

@Composable
private fun SeerrRequestDropdown(label: String, selected: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    choices: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth()) {
            Surface(onClick = { if (choices.isNotEmpty()) expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
                    Icon(leadingIcon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Text(selected, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose $label")
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
                choices.forEachIndexed { index, choice ->
                    DropdownMenuItem(text = {
                        Text(choice, fontWeight = if (index == selectedIndex)
                            FontWeight.Bold else FontWeight.Normal)
                    }, onClick = { onSelect(index); expanded = false },
                        leadingIcon = if (index == selectedIndex) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null)
                }
            }
        }
    }
}

@Composable
private fun SeerrRequestDetails(instance: ServiceConfig, request: SeerrRecentRequest,
    cache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, SeerrMediaDetails>,
    carouselScrollStates: MutableMap<String, androidx.compose.foundation.ScrollState>,
    navigationDepth: Int,
    onBack: () -> Unit, onRelatedClick: (SeerrRecentRequest) -> Unit) {
    var showRequestSheet by remember(request.tmdbId, request.isTv) { mutableStateOf(false) }
    var requestSubmitted by remember(request.tmdbId, request.isTv) { mutableStateOf(false) }
    var showIssueSheet by remember(request.tmdbId, request.isTv) { mutableStateOf(false) }
    val cacheKey = "${instance.id}-${request.tmdbId}-${request.isTv}"
    fun protectConfirmedSeasons(numbers: List<Int>) {
        val current = cache[cacheKey] ?: return
        val protected = numbers.toSet()
        cache[cacheKey] = current.copy(tvSeasons = current.tvSeasons.map { season ->
            if (season.number in protected && season.status !in listOf(2, 3, 4, 5))
                season.copy(status = 2) else season
        })
    }
    // Request records, not mediaInfo.seasons alone, determine which seasons can be requested.
    var knownRequestedSeasons by remember(cacheKey) { mutableStateOf<Set<Int>?>(null) }
    LaunchedEffect(instance, cacheKey) {
        if (!request.isTv) return@LaunchedEffect
        val confirmed = mutableSetOf<Int>()
        var skip = 0
        var complete = false
        while (skip < 1000) {
            val page = SeerrClient.requestPage(instance, 100, skip, "All")
            if (page.error != null) break
            page.requests.filter {
                it.isTv && it.tmdbId == request.tmdbId &&
                    it.requestStatus in listOf("Pending", "Approved", "Completed")
            }.forEach { confirmed.addAll(it.seasons) }
            skip += page.requests.size
            if (page.requests.isEmpty() || skip >= page.total) {
                complete = true
                break
            }
        }
        if (complete) {
            knownRequestedSeasons = confirmed
            protectConfirmedSeasons(confirmed.toList())
        }
    }
    var expanded by remember(cacheKey) { mutableStateOf(false) }
    LaunchedEffect(instance, cacheKey) {
        if (cacheKey !in cache) {
            val fetched = SeerrClient.mediaDetails(instance, request)
            if (fetched.error == null) cache[cacheKey] = fetched
        }
    }
    // Invalidate stale season and availability information after a confirmed request.
    // This also runs while the success sheet is still visible.
    LaunchedEffect(instance, cacheKey, requestSubmitted) {
        if (requestSubmitted) {
            val refreshed = SeerrClient.mediaDetails(instance, request)
            if (refreshed.error == null) {
                val previous = cache[cacheKey]
                cache[cacheKey] = refreshed.copy(tvSeasons = refreshed.tvSeasons.map { season ->
                    val confirmed = previous?.tvSeasons?.firstOrNull { it.number == season.number }
                    if (confirmed?.status in listOf(2, 3, 4, 5) &&
                        season.status !in listOf(2, 3, 4, 5))
                        season.copy(status = confirmed!!.status) else season
                })
            }
        }
    }
    if (showRequestSheet) {
        SeerrMovieRequestSheet(instance, request.tmdbId,
            isTv = request.isTv, seasons = cache[cacheKey]?.tvSeasons.orEmpty(),
            onDismiss = { showRequestSheet = false },
            onConfirmed = { confirmedSeasons ->
                knownRequestedSeasons = knownRequestedSeasons.orEmpty() + confirmedSeasons
                protectConfirmedSeasons(confirmedSeasons)
                requestSubmitted = true
            },
            onSubmitted = { showRequestSheet = false })
    }
    if (showIssueSheet) {
        SeerrReportIssueSheet(instance, request.title,
            cache[cacheKey]?.seerrMediaId ?: 0,
            onDismiss = { showIssueSheet = false },
            onSubmitted = { showIssueSheet = false })
    }
    val media = cache[cacheKey]
    val displayedStatus = when (media?.mediaStatus) {
        2 -> "Pending"
        3 -> "Processing"
        4 -> "Partially available"
        5 -> "Available"
        else -> request.status.takeIf { it.isNotBlank() }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.fillMaxWidth().height(405.dp)) {
            val pageColor = MaterialTheme.colorScheme.background
            Box(Modifier.fillMaxSize()) {
                if (media?.backdropUrl != null) {
                    AsyncImage(model = media.backdropUrl,
                        contentDescription = "Backdrop for ${request.title}",
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.30f),
                    0.38f to Color.Transparent,
                    0.60f to pageColor.copy(alpha = 0.70f),
                    0.88f to pageColor,
                    1f to pageColor)))
            }
            FilledTonalIconButton(onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to requests")
            }
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(BastionSpacing.large)) {
                val poster = media?.posterUrl ?: request.posterUrl
                Surface(shape = RoundedCornerShape(18.dp), shadowElevation = 8.dp,
                    modifier = Modifier.width(122.dp).height(183.dp)) {
                    if (poster != null) AsyncImage(model = poster,
                        contentDescription = "Poster for ${request.title}",
                        contentScale = ContentScale.Crop)
                    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Movie, contentDescription = null)
                    }
                }
                Column(Modifier.weight(1f).padding(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(request.title, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text(if (request.isTv) "TV series" else "Movie",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOfNotNull(request.year,
                            media?.runtime?.let { "${it / 60}h ${it % 60}m" },
                            media?.rating?.let {
                                "★ " + String.format(java.util.Locale.US, "%.1f", it)
                            }).forEach { value ->
                            Surface(shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Text(value, Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (displayedStatus != null && displayedStatus != "Available") Surface(shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(displayedStatus, Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                request.requestedBy?.let {
                    Text("Requested by $it", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        if (media?.seerrMediaId != null && media.seerrMediaId > 0 &&
            displayedStatus in listOf("Available", "Partially available")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BastionSpacing.xSmall)) {
                Surface(modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                    shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp,
                        topEnd = 8.dp, bottomEnd = 8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(6.dp))
                        Text(displayedStatus ?: "Available", color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
                Button(onClick = { showIssueSheet = true },
                    modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp,
                        topEnd = 28.dp, bottomEnd = 28.dp)) {
                    Icon(Icons.Default.ReportProblem, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("Report issue", maxLines = 1)
                }
            }
        }
        if (request.tmdbId > 0 && (displayedStatus == null || (request.isTv && displayedStatus in listOf("Partially available", "Available", "Processing", "Pending") &&
            knownRequestedSeasons != null &&
            media?.tvSeasons?.any { it.number > 0 &&
                it.number !in knownRequestedSeasons.orEmpty() &&
                it.status !in listOf(2, 3, 4, 5) } == true)) && !requestSubmitted) {
            Button(onClick = { showRequestSheet = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (request.isTv && displayedStatus != null) "Request More" else if (request.isTv) "Request" else "Request movie")
            }
        }
        if (media == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        media?.error?.let { Text("Additional details unavailable: $it",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (!media?.tagline.isNullOrBlank()) Text(media!!.tagline!!,
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!media?.genres.isNullOrEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(BastionSpacing.small),
                verticalArrangement = Arrangement.spacedBy(BastionSpacing.small)) {
                media!!.genres.forEach { genre ->
                    Surface(shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(genre, Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }
        val overview = media?.overview ?: request.overview
        if (!overview.isNullOrBlank()) {
            ElevatedCard(shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(BastionSpacing.small)) {
                    Text("Overview", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(overview, maxLines = if (expanded) Int.MAX_VALUE else 4,
                        style = MaterialTheme.typography.bodyMedium)
                    if (overview.length > 150) TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Show less" else "Show more")
                    }
                }
            }
        }
        if (request.isTv && request.seasons.isNotEmpty()) {
            Text("Requested seasons: " + request.seasons.joinToString(", ") {
                if (it == 0) "Specials" else "Season $it"
            }, style = MaterialTheme.typography.bodyMedium)
        }
        if (media != null && media.cast.isNotEmpty()) {
            Text("Cast", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
                media.cast.forEach { actor ->
                    Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Surface(shape = RoundedCornerShape(50),
                            modifier = Modifier.size(82.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            if (actor.photoUrl != null) AsyncImage(model = actor.photoUrl,
                                contentDescription = actor.name, contentScale = ContentScale.Crop)
                            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, contentDescription = null)
                            }
                        }
                        Text(actor.name, style = MaterialTheme.typography.labelMedium,
                            maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        if (actor.character.isNotBlank()) Text(actor.character,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
        if (media != null) {
            val facts = listOfNotNull(
                (media.releaseDate.ifBlank { request.releaseDate.orEmpty() }).takeIf { it.isNotBlank() }?.let { "Release date" to it },
                media.runtime?.let { "Runtime" to "${it / 60}h ${it % 60}m" },
                media.directors.takeIf { it.isNotEmpty() }?.let { "Director" to it.joinToString(", ") },
                media.studios.takeIf { it.isNotEmpty() }?.let { (if (request.isTv) "Networks" else "Studios") to it.joinToString(", ") },
                media.countries.takeIf { it.isNotEmpty() }?.let { "Countries" to it.joinToString(", ") },
                media.languages.takeIf { it.isNotEmpty() }?.let { "Languages" to it.joinToString(", ") })
            if (facts.isNotEmpty()) {
                Text(if (request.isTv) "Series details" else "Movie details",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Column(Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(BastionSpacing.large)) {
                    facts.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BastionSpacing.large)) {
                            pair.forEach { (label, value) ->
                                Column(Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(label, style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(value, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            listOf("Recommendations" to media.recommendations, "Similar" to media.similar)
                .forEach { (heading, titles) ->
                    if (titles.isNotEmpty()) {
                        Text(heading, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        val carouselKey = "${instance.id}-${navigationDepth}-${request.tmdbId}-${request.isTv}-$heading"
                        val carouselScroll = carouselScrollStates.getOrPut(carouselKey) {
                            androidx.compose.foundation.ScrollState(0)
                        }
                        Row(Modifier
                            .layout { measurable, constraints ->
                                // Give the carousel a genuine full-screen viewport even though
                                // its heading and surrounding details are padded by 20dp.
                                val inset = 20.dp.roundToPx()
                                val fullWidth = constraints.maxWidth + inset * 2
                                val child = measurable.measure(
                                    constraints.copy(minWidth = fullWidth, maxWidth = fullWidth))
                                layout(constraints.maxWidth, child.height) {
                                    child.placeRelative(-inset, 0)
                                }
                            }
                            .horizontalScroll(carouselScroll),
                            horizontalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
                            Spacer(Modifier.width(20.dp))
                            titles.forEach { title ->
                                Column(Modifier.width(125.dp)
                                    .clickable {
                                        onRelatedClick(SeerrRecentRequest(
                                            title = title.title, status = "",
                                            posterUrl = title.posterUrl,
                                            year = title.year, requestedBy = null,
                                            isTv = title.isTv, tmdbId = title.tmdbId))
                                    },
                                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.width(125.dp).height(185.dp)) {
                                        Surface(shape = RoundedCornerShape(18.dp),
                                        modifier = Modifier.fillMaxSize(),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                        if (title.posterUrl != null)
                                            AsyncImage(model = title.posterUrl,
                                                contentDescription = title.title,
                                                contentScale = ContentScale.Crop)
                                        else Box(Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Movie, contentDescription = null)
                                        }
                                    }

                                        val relatedBadge = when (title.mediaStatus) {
                                        2 -> "Pending"
                                        3 -> "Processing"
                                        4 -> "Partial"
                                        5 -> "Available"
                                        else -> null
                                    }
                                        if (relatedBadge != null) {
                                            Surface(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                                shape = RoundedCornerShape(12.dp),
                                            color = when (title.mediaStatus) {
                                                2 -> Color(0xFFE3A008)
                                                3 -> Color(0xFF247AC0)
                                                4 -> Color(0xFF8E56C8)
                                                else -> Color(0xFF168A55)
                                            }) {
                                            Text(relatedBadge,
                                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White)
                                        }
                                    }
                                    }
                                    Text(title.title, style = MaterialTheme.typography.labelMedium,
                                        maxLines = 2)
                                    Text(listOfNotNull(title.year.takeIf { it.isNotBlank() },
                                        title.rating.takeIf { it > 0 }?.let {
                                            "★ " + String.format(java.util.Locale.US, "%.1f", it)
                                        }).joinToString(" • "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
        }
    }
        }
    }

@Composable
private fun UnifiedCalendarScreen() {
    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    var hideDownloaded by remember { mutableStateOf(false) }
    val sources = listOf("Sonarr", "Radarr", "Bindery")
    val sourceColors = listOf(Color(0xFF448DD6), Color(0xFF51B86B), Color(0xFFB18CE6))
    var enabledSources by remember { mutableStateOf(setOf("Sonarr", "Radarr", "Bindery")) }

    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(BastionSpacing.large)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BastionSpacing.xSmall)) {
            Column(Modifier.weight(1f)) {
                Text(month.year.toString(), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()),
                    style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            }
            FilledTonalButton(onClick = { month = YearMonth.from(today); selected = today },
                contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Today") }
            IconButton(onClick = { month = month.minusMonths(1); selected = month.atDay(1) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
            }
            IconButton(onClick = { month = month.plusMonths(1); selected = month.atDay(1) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
            }
        }

        AnimatedContent(targetState = month, label = "Calendar month",
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally { direction * it / 3 } + fadeIn())
                    .togetherWith(slideOutHorizontally { -direction * it / 3 } + fadeOut())
            }) { visibleMonth ->
            val firstOffset = visibleMonth.atDay(1).dayOfWeek.value % 7
            val rows = (firstOffset + visibleMonth.lengthOfMonth() + 6) / 7
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                repeat(rows) { week ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { weekday ->
                            val day = week * 7 + weekday - firstOffset + 1
                            Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                                if (day in 1..visibleMonth.lengthOfMonth()) {
                                    val date = visibleMonth.atDay(day)
                                    CalendarDateCell(date, selected == date, today == date) { selected = date }
                                }
                            }
                        }
                    }
                }
            }
        }

        Text("RELEASE SOURCES", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val pressSources = remember { List(3) { MutableInteractionSource() } }
        val pressed = pressSources.map { it.collectIsPressedAsState().value }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            sources.forEachIndexed { index, source ->
                val active = source in enabledSources
                val isPressed = pressed[index]
                val expandedWeight by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isPressed) 1.22f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium),
                    label = "Filter press expansion"
                )
                val innerCorner by animateDpAsState(
                    targetValue = if (active || isPressed) 24.dp else 8.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "Filter inner corners"
                )
                val outerCorner = 24.dp
                val shape = RoundedCornerShape(
                    topStart = if (index == 0) outerCorner else innerCorner,
                    topEnd = if (index == sources.lastIndex) outerCorner else innerCorner,
                    bottomStart = if (index == 0) outerCorner else innerCorner,
                    bottomEnd = if (index == sources.lastIndex) outerCorner else innerCorner
                )
                Surface(
                    onClick = {
                        enabledSources = if (active) enabledSources - source else enabledSources + source
                    },
                    interactionSource = pressSources[index],
                    modifier = Modifier.weight(expandedWeight).height(48.dp),
                    shape = shape,
                    color = if (active) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(50), color = sourceColors[index],
                            modifier = Modifier.size(8.dp)) {}
                        Spacer(Modifier.width(6.dp))
                        Text(source, style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            color = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hide downloaded", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium)
            Switch(checked = hideDownloaded, onCheckedChange = { hideDownloaded = it })
        }
        HorizontalDivider()
        AnimatedContent(targetState = selected, label = "Selected day agenda",
            transitionSpec = { fadeIn().togetherWith(fadeOut()) }) { date ->
            Column(verticalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
                Text(date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Surface(shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(BastionSpacing.medium)) {
                        Icon(Icons.Default.EventNote, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("No release data yet", style = MaterialTheme.typography.titleMedium)
                            Text("Your Sonarr and Radarr releases will appear here after calendar integration. Bindery support is being investigated.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDateCell(date: LocalDate, selected: Boolean, today: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium), label = "Day bounce")
    val radius by animateDpAsState(if (selected) 24.dp else 12.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "Day shape")
    Surface(onClick = onClick,
        modifier = Modifier.size(42.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(radius),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent) {
        Box(contentAlignment = Alignment.Center) {
            Text(date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else if (today) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center)
        }
    }
}

private fun connectedServiceShape(index: Int, count: Int): RoundedCornerShape {
    if (count <= 1) return RoundedCornerShape(28.dp)
    val large = 28.dp
    val small = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) large else small,
        topEnd = if (index == 0) large else small,
        bottomStart = if (index == count - 1) large else small,
        bottomEnd = if (index == count - 1) large else small
    )
}
