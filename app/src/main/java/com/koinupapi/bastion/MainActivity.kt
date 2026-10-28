package com.koinupapi.bastion

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
import coil.compose.AsyncImage
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
        val serviceStore = remember(context) { ServiceStore(context) }
        val savedInstances = remember { mutableStateListOf<ServiceConfig>().apply { addAll(serviceStore.load()) } }
        var editingId by remember { mutableStateOf<String?>(null) }
        var localUrl by remember { mutableStateOf("") }
        var remoteUrl by remember { mutableStateOf("") }
        var apiKey by remember { mutableStateOf("") }
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
                        item { RequestsWidget(savedInstances.filter { it.type == "Seerr" }) }
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
                                    if (savedInstances.isEmpty()) {
                                        item {
                                            Card(shape = MaterialTheme.shapes.extraLarge) {
                                                Column(Modifier.fillMaxWidth().padding(24.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        items(savedInstances, key = { it.id }) { instance ->
                                            SettingsEntry(instance.name, "${instance.type} • Configured",
                                                Icons.Default.Dns) {
                                                editService(instance)
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
                                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Text("Coming soon", style = MaterialTheme.typography.titleLarge)
                                                Text("Real connections for ${selectedIntegration} aren't implemented yet. Seerr is our first integration.")
                                            }
                                        }
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Card(shape = MaterialTheme.shapes.extraLarge) {
                                                Column(Modifier.fillMaxWidth().padding(20.dp),
                                                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                                    Text("Seerr connection", style = MaterialTheme.typography.titleLarge)
                                                    OutlinedTextField(value = previewName, onValueChange = { previewName = it },
                                                        label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                                    OutlinedTextField(value = localUrl, onValueChange = { localUrl = it },
                                                        label = { Text("Local URL (HTTP or HTTPS)") },
                                                        placeholder = { Text("http://192.168.1.100:5055") },
                                                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                                                    OutlinedTextField(value = remoteUrl, onValueChange = { remoteUrl = it },
                                                        label = { Text("Remote URL (HTTPS)") },
                                                        placeholder = { Text("https://seerr.example.com") },
                                                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                                                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it },
                                                        label = { Text("Seerr API key") },
                                                        visualTransformation = PasswordVisualTransformation(),
                                                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                                                    Text("Connection preference", style = MaterialTheme.typography.titleMedium)
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
                                                    OutlinedButton(enabled = valid && !isTesting,
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
    WidgetCard("Seerr requests", Icons.Default.Movie) {
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
                        Text("${result.total} requested",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (result.requests.isEmpty()) Text("No requests yet.")
                        result.requests.forEachIndexed { index, request ->
                            if (index > 0) HorizontalDivider()
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                    Text(request.title, style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold)
                                    Text(request.status, style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary)
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
