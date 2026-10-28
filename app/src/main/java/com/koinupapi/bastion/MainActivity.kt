package com.koinupapi.bastion

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
private data class ServicePreview(val id: Int, val type: String, val name: String)
private val containers = listOf("sonarr", "radarr", "qbittorrent", "plex", "seerr", "uptime-kuma")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BastionApp() }
    }
}

@Composable
private fun BastionApp() {
    val context = LocalContext.current
    var dark by remember { mutableStateOf(false) }
    var dynamic by remember { mutableStateOf(true) }
    var showAllNavLabels by remember { mutableStateOf(true) }
    val preferences = remember(context) { context.getSharedPreferences("bastion_settings", android.content.Context.MODE_PRIVATE) }
    var returnToDashboard by remember { mutableStateOf(preferences.getBoolean("return_to_dashboard", true)) }
    var doubleBackToExit by remember { mutableStateOf(preferences.getBoolean("double_back_exit", true)) }
    val colors = if (dynamic && android.os.Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme() else lightColorScheme()
    val activity = context as ComponentActivity
    SideEffect {
        activity.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        @Suppress("DEPRECATION")
        activity.window.isNavigationBarContrastEnforced = false
    }
    MaterialTheme(colorScheme = colors) {
        var tab by remember { mutableIntStateOf(0) }
        var settingsPage by remember { mutableStateOf("Settings") }
        var selectedIntegration by remember { mutableStateOf("Seerr") }
        var serviceSearch by remember { mutableStateOf("") }
        var previewName by remember { mutableStateOf("") }
        val previewInstances = remember { mutableStateListOf<ServicePreview>() }
        var nextPreviewId by remember { mutableIntStateOf(1) }
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
                tab == 3 && settingsPage != "Settings" -> goBackSettings()
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
        val labels = listOf("Home", "Servers", "Media", "Settings")
        val icons = listOf(Icons.Default.Home, Icons.Default.Dns, Icons.Default.Movie, Icons.Default.Settings)
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, bottomBar = {
            NavigationBar {
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index; settingsPage = "Settings"; settingsHistory.clear(); lastExitBackAt = 0L },
                        icon = { Icon(icons[index], contentDescription = null) },
                        label = { Text(label) },
                        alwaysShowLabel = showAllNavLabels
                    )
                }
            }
        }) { insets ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(insets),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("YOUR HOMELAB", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(if (tab == 0) "Bastion" else labels[tab],
                                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.Shield, contentDescription = "Bastion", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                item { DemoNotice() }
                when (tab) {
                    0 -> {
                        item { ServerWidget() }
                        item { DownloadsWidget() }
                        item { RequestsWidget() }
                        item { SonarrWidget() }
                        item { RadarrWidget() }
                        item { ServicesWidget() }
                    }
                    1 -> {
                        item { ServerWidget() }
                        item { SectionTitle("Docker containers") }
                        items(containers) { DemoRow(it, "● Running • Demo container") }
                    }
                    2 -> {
                        item { SectionTitle("Media management") }
                        items(listOf("Sonarr • 128 series", "Radarr • 342 movies",
                            "qBittorrent • 2 downloads", "Seerr • 4 requests")) {
                            DemoRow(it, "Full management coming later")
                        }
                    }
                    else -> {
                        if (settingsPage == "Settings") {
                            item { Text("Customize Bastion and manage your integrations",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            item {
                                SettingsEntry("Appearance", "Theme, dynamic colors and display", Icons.Default.Palette) {
                                    openSettingsPage("Appearance")
                                }
                            }
                            item {
                                SettingsEntry("Services", "Manage homelab service connections", Icons.Default.Widgets) {
                                    openSettingsPage("Services")
                                }
                            }
                            item {
                                SettingsEntry("Behavior", "Back navigation and exit confirmation", Icons.Default.Tune) {
                                    openSettingsPage("Behavior")
                                }
                            }
                            item {
                                SettingsEntry("About", "Version and preview information", Icons.Default.Info) {
                                    openSettingsPage("About")
                                }
                            }
                        } else {
                            item {
                                TextButton(onClick = { goBackSettings() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(settingsHistory.lastOrNull() ?: "Settings")
                                }
                            }
                            item { SectionTitle(if (settingsPage == "Service detail") selectedIntegration else settingsPage) }
                            when (settingsPage) {
                                "Appearance" -> item {
                                    Card(shape = MaterialTheme.shapes.extraLarge) {
                                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Dark theme", Modifier.weight(1f))
                                                Switch(checked = dark, onCheckedChange = { dark = it })
                                            }
                                            HorizontalDivider()
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Dynamic colors", Modifier.weight(1f))
                                                Switch(checked = dynamic, onCheckedChange = { dynamic = it })
                                            }
                                            HorizontalDivider()
                                            Text("Navigation labels", style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                                            Text("Choose which destination names appear in the bottom bar.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            SingleChoiceSegmentedButtonRow(
                                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                                            ) {
                                                SegmentedButton(
                                                    selected = showAllNavLabels,
                                                    onClick = { showAllNavLabels = true },
                                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                                ) { Text("All labels") }
                                                SegmentedButton(
                                                    selected = !showAllNavLabels,
                                                    onClick = { showAllNavLabels = false },
                                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                                ) { Text("Selected only") }
                                            }
                                        }
                                    }
                                }
                                "Behavior" -> item {
                                    Card(shape = MaterialTheme.shapes.extraLarge) {
                                        Column(Modifier.fillMaxWidth().padding(20.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("Back button behavior", style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold)
                                            Text("When you press Back from a main navigation tab:",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(selected = !returnToDashboard, onClick = {
                                                    returnToDashboard = false
                                                    preferences.edit().putBoolean("return_to_dashboard", false).apply()
                                                })
                                                Column(Modifier.weight(1f)) {
                                                    Text("Exit from current tab")
                                                    Text("Back initiates exiting Bastion.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(selected = returnToDashboard, onClick = {
                                                    returnToDashboard = true
                                                    preferences.edit().putBoolean("return_to_dashboard", true).apply()
                                                })
                                                Column(Modifier.weight(1f)) {
                                                    Text("Return to Dashboard")
                                                    Text("Go Home first from Servers, Media, or Settings.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            HorizontalDivider()
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Text("Double Back to exit", style = MaterialTheme.typography.titleMedium)
                                                    Text("Press Back twice within 2 seconds to exit.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Switch(checked = doubleBackToExit, onCheckedChange = {
                                                    doubleBackToExit = it
                                                    preferences.edit().putBoolean("double_back_exit", it).apply()
                                                })
                                            }
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
                                    if (previewInstances.isEmpty()) {
                                        item {
                                            Card(shape = MaterialTheme.shapes.extraLarge) {
                                                Column(Modifier.fillMaxWidth().padding(24.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Icon(Icons.Default.Extension, contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary)
                                                    Text("No services configured yet", style = MaterialTheme.typography.titleMedium)
                                                    Text("Add a service to start setting up Bastion. Connections are not active in this preview.",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    } else {
                                        items(previewInstances, key = { it.id }) { instance ->
                                            SettingsEntry(instance.name, "${instance.type} • Preview only",
                                                Icons.Default.Dns) {
                                                selectedIntegration = instance.type
                                                previewName = instance.name
                                                openSettingsPage("Service detail")
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
                                        item { Text("No matching integrations found.") }
                                    }
                                    listOf("Infrastructure", "Media management", "Media servers", "Other services").forEach { category ->
                                        val matches = filtered.filter { it.category == category }
                                        if (matches.isNotEmpty()) {
                                            item { SectionTitle(category) }
                                            items(matches, key = { it.name }) { integration ->
                                                SettingsEntry(integration.name, "Add ${integration.name} instance",
                                                    Icons.Default.Add) {
                                                    selectedIntegration = integration.name
                                                    previewName = integration.name
                                                    openSettingsPage("Service detail")
                                                }
                                            }
                                        }
                                    }
                                }
                                "Service detail" -> item {
                                    Card(shape = MaterialTheme.shapes.extraLarge) {
                                        Column(Modifier.fillMaxWidth().padding(20.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Text("Instance preview", style = MaterialTheme.typography.titleMedium)
                                            Text("This screen previews the configuration flow. No server connection or credentials are stored yet.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            OutlinedTextField(
                                                value = previewName,
                                                onValueChange = { previewName = it },
                                                label = { Text("Display name") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )
                                            Button(
                                                enabled = previewName.isNotBlank(),
                                                onClick = {
                                                    previewInstances.add(ServicePreview(nextPreviewId++, selectedIntegration, previewName.trim()))
                                                    settingsHistory.clear()
                                                    settingsHistory.add("Settings")
                                                    settingsPage = "Services"
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Add preview instance") }
                                            Text("You can add multiple instances of the same integration. Live configuration comes next.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    }
}

@Composable
private fun DemoNotice() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Science, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Preview mode • all data is simulated", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun WidgetCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit) {
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
private fun RequestsWidget() {
    WidgetCard("Seerr requests", Icons.Default.Movie) {
        Text("4 pending requests", style = MaterialTheme.typography.titleMedium)
        Text("• Example Movie (2026)  —  Pending", style = MaterialTheme.typography.bodyMedium)
        Text("• Example Series  —  Approved", style = MaterialTheme.typography.bodyMedium)
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
    onClick: () -> Unit
) {
    Card(onClick = onClick, shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
