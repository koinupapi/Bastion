package com.koinupapi.bastion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Lemon = RoundedCornerShape(topStart = 32.dp, topEnd = 14.dp, bottomEnd = 32.dp, bottomStart = 14.dp)
private val SampleServices = listOf("Sonarr", "Radarr", "qBittorrent", "Seerr", "Plex", "Docker")
private val SampleContainers = listOf("sonarr", "radarr", "qbittorrent", "plex", "seerr", "uptime-kuma")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = colors) {
        var selected by remember { mutableIntStateOf(0) }
        val labels = listOf("Home", "Servers", "Media", "Services")
        val icons = listOf(Icons.Default.Home, Icons.Default.Dns, Icons.Default.Movie, Icons.Default.Widgets)
        Scaffold(
            bottomBar = {
                NavigationBar {
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { Icon(icons[index], contentDescription = null) },
                            label = { Text(label) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        ) { insets ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(insets),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("YOUR HOMELAB", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(if (selected == 0) "Bastion" else labels[selected], style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.Shield, contentDescription = "Bastion", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    }
                }
                when (selected) {
                    0 -> {
                        item { DemoNotice() }
                        item { ServerHero() }
                        item { SectionTitle("Active downloads") }
                        item { DownloadCard("Example.Show.S01E04.1080p", 0.72f, "8.4 MB/s") }
                        item { DownloadCard("Example.Movie.2026.2160p", 0.38f, "3.1 MB/s") }
                        item { SectionTitle("Your services") }
                        item { ServiceGrid() }
                    }
                    1 -> {
                        item { DemoNotice() }
                        item { ServerHero() }
                        item { SectionTitle("Docker containers") }
                        items(SampleContainers) { ContainerRow(it) }
                    }
                    2 -> {
                        item { DemoNotice() }
                        item { SectionTitle("Media management") }
                        items(listOf("Sonarr • 128 series", "Radarr • 342 movies", "qBittorrent • 2 downloads", "Seerr • 4 requests")) {
                            DemoRow(it, "Full management coming later")
                        }
                    }
                    else -> {
                        item { DemoNotice() }
                        item { SectionTitle("Connected services") }
                        items(SampleServices) { DemoRow(it, "Demo instance • not connected") }
                        item { SectionTitle("Appearance") }
                        item {
                            Card(shape = Lemon) {
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
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Science, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Preview mode • all data is simulated", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun ServerHero() {
    Card(shape = Lemon, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Dns, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Home Server", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("● Online", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("CPU", "18%", 0.18f, Modifier.weight(1f))
                Metric("Memory", "6.8 / 16 GB", 0.425f, Modifier.weight(1f))
            }
            HorizontalDivider()
            Text("21 containers running  •  Uptime 12 days", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, progress: Float, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DownloadCard(name: String, progress: Float, speed: String) {
    Card(shape = Lemon, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text("↓ $speed  •  Demo download", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ServiceGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SampleServices.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { service ->
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = Lemon,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Text(service, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContainerRow(name: String) {
    DemoRow(name, "● Running • Demo container")
}

@Composable
private fun DemoRow(title: String, subtitle: String) {
    Card(shape = Lemon, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
