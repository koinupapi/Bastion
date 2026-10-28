package com.koinupapi.bastion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
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
        val labels = listOf("Home", "Servers", "Media", "Settings")
        val icons = listOf(Icons.Default.Home, Icons.Default.Dns, Icons.Default.Movie, Icons.Default.Settings)
        Scaffold(bottomBar = {
            NavigationBar {
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icons[index], contentDescription = null) },
                        label = { Text(label) }
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
                        item { SectionTitle("Service connections") }
                        items(services) { DemoRow(it, "Demo instance • not connected") }
                        item { SectionTitle("Appearance") }
                        item {
                            Card {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Dark theme", Modifier.weight(1f))
                                        Switch(checked = dark, onCheckedChange = { dark = it })
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Dynamic colors", Modifier.weight(1f))
                                        Switch(checked = dynamic, onCheckedChange = { dynamic = it })
                                    }
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
private fun DownloadLine(name: String, progress: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(name, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
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
