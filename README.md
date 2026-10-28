# Bastion 🏰

A private, native Android homelab and media-management app.

## v0.1 — UI playground

Built with Kotlin, Jetpack Compose, and Material 3. The current prototype has a sample home dashboard, simulated servers, Docker containers, media service cards, and theme toggles. **All statistics and services are mock data.** No credentials, network access, or management operations exist yet.

## Build an APK

Open **Actions → Build Bastion APK → Run workflow**. Download the `Bastion-debug-apk` artifact from the completed run and install `app-debug.apk`.

Local build (Android SDK + JDK 17 + Gradle 8.13):

```bash
gradle assembleDebug
```

## Planned

Multi-server support, qBittorrent, Sonarr, Radarr, Seerr, Docker management, secure connections, and a customizable dashboard.
