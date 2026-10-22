# DownFlow

A production-ready Android download manager powered by an embedded [aria2](https://aria2.github.io) binary.

## Features

- **HTTP / HTTPS / FTP / BitTorrent** downloads (magnet links + `.torrent` files)
- **aria2c embedded** — launched as a native subprocess, communicates via JSON-RPC 2.0 bound exclusively to `127.0.0.1:6800`
- **Foreground service** — downloads continue in the background with a persistent notification
- **In-app browser** — intercepts direct-file and magnet links automatically
- **Material 3 UI** — Downloads, Browser, History, Settings screens with dark-mode support
- **Android Keystore security** — RPC secret stored with AES-256-GCM, key never leaves hardware security module
- **Custom download folder** — select any folder via Storage Access Framework
- **Proton VPN compatible** — DownFlow routes all traffic through Android's active VPN automatically

---

## Building on-device with Termux

You can build and install DownFlow entirely on your Android phone using
[Termux](https://github.com/termux/termux-app) — no PC or Android Studio
required.

### Quick start (3 commands)

```bash
# 1. Clone the project (or extract the ZIP into ~/DownFlow)
pkg install -y git
git clone https://github.com/your-org/DownFlow.git
cd DownFlow

# 2. Install all dependencies and copy the aria2 binary (one-time setup)
bash scripts/setup_termux.sh
source ~/.bashrc

# 3. Build the APK and install it on this device
bash scripts/build_termux.sh
bash scripts/install_termux.sh
```

That's it. The setup script installs JDK 17, the Android SDK, and copies
Termux's own pre-built `aria2c` binary (already compiled for your device's
ARM64) into the APK assets.

### Termux prerequisites

| Requirement | Notes |
|-------------|-------|
| Termux | Install from [F-Droid](https://f-droid.org/en/packages/com.termux/) — **not** the Play Store version (outdated) |
| Android version | 8.0+ (API 26) |
| Free storage | ~3 GB (SDK + Gradle cache + build outputs) |
| RAM | 2 GB minimum; 4 GB recommended |
| Internet | Required for first build (downloads SDK + Gradle) |

### Termux script reference

| Script | Purpose |
|--------|---------|
| `scripts/setup_termux.sh` | One-time setup: JDK 17, Android SDK, aria2 binary |
| `scripts/build_termux.sh` | Build debug APK (pass `release` for release build) |
| `scripts/install_termux.sh` | Install APK on this device |
| `scripts/build_aria2_termux.sh` | Build aria2 from source in Termux (optional — only needed if you want a custom or newer aria2 than the Termux package) |

### Troubleshooting Termux builds

**Out of memory during Gradle build**  
The build script auto-detects RAM and sets conservative heap limits. If you
still get OOM kills, close other apps and retry. Alternatively, add more
swap via [Termux:Boot](https://github.com/termux/termux-boot) or a swap
file.

**`JAVA_HOME` not set**  
Run `source ~/.bashrc` after `setup_termux.sh`, or open a new Termux session.

**`sdkmanager: command not found`**  
Re-run `setup_termux.sh` — it downloads and installs the SDK command-line tools.

**Build fails with `> Could not resolve com.android…`**  
Network issue. Check your connection; Termux's DNS sometimes needs a kick:
`pkg install resolv-conf` then retry.

**aria2c binary architecture warning**  
This is expected if you're on an x86_64 emulator — the Termux `aria2c` will
match the emulator's ABI, not ARM64. Use the app on a real device or ARM64
emulator.

---

## Prerequisites (PC / Android Studio build)

| Tool | Version |
|------|---------|
| Android Studio | Hedgehog 2023.1.1+ (or later) |
| JDK | 17 (bundled with Android Studio) |
| Android NDK | r26.3+ |
| Gradle | 8.9 (via wrapper) |
| Android SDK | API 35 (compileSdk), API 26 minimum |

---

## Building

### Step 1 — Clone the repository

```bash
git clone https://github.com/your-org/DownFlow.git
cd DownFlow
```

### Step 2 — Build the aria2 ARM64 binary

DownFlow requires a statically-linked `aria2c` binary compiled for Android ARM64.
A cross-compile script is provided:

```bash
# Set your NDK path (example – adjust to your installation)
export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk/26.3.11579264"

# Install build dependencies (Ubuntu / Debian)
sudo apt-get install -y autoconf automake libtool pkg-config make curl

# Run the build script (~10–20 minutes on first run; deps cached afterwards)
bash scripts/build_aria2_android.sh
```

On success the binary is placed at:

```
app/src/main/assets/aria2c
```

> **GitHub Actions** — the CI workflow (`ci.yml`) builds the binary automatically.
> You can also download a prebuilt artifact from a CI run.

### Step 3 — Open in Android Studio

1. Open Android Studio → **Open** → select the `DownFlow` folder.
2. Let Gradle sync complete (it downloads all Kotlin / Jetpack dependencies).
3. Select **Run → Run 'app'** or use the play button to build and deploy.

### Step 4 — Build from the command line

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (requires signing config)
./gradlew assembleRelease

# Unit tests
./gradlew testDebugUnitTest

# Instrumentation tests (emulator or device required)
./gradlew connectedDebugAndroidTest
```

---

## Architecture

```
com.downflow.app
├── aria2/          JSON-RPC models, RPC client, process manager
├── browser/        WebView download interceptor
├── data/
│   ├── db/         Room database (download history)
│   └── repository/ DownloadRepository (binds service, merges flows)
├── di/             Hilt modules
├── notifications/  Notification channels + foreground notification builder
├── service/        DownloadService (foreground), BootReceiver
├── settings/       DataStore + Android Keystore–backed AppSettings
├── storage/        SAF folder picker, FileProvider, path resolution
└── ui/
    ├── components/ DownloadCard, SpeedBadge, StateChip
    ├── screens/    Downloads, Browser, History, Settings, Detail
    ├── theme/      Material 3 ColorScheme, Typography
    ├── MainActivity.kt
    ├── Navigation.kt
    └── DownloadsViewModel.kt
```

### Key design decisions

| Decision | Rationale |
|----------|-----------|
| aria2 as embedded subprocess | Full download feature set (multi-connection, BitTorrent, metadata) without re-implementing protocol logic |
| JSON-RPC bound to 127.0.0.1 only | Zero network exposure; `network_security_config.xml` enforces TLS everywhere except loopback |
| Android Keystore AES-256-GCM for RPC secret | Secret never in plaintext on disk; hardware-backed where available |
| Foreground service with `dataSync` type | Required for Android 14+ file-transfer services; survives background restrictions |
| Room for history (not aria2) | aria2 session is ephemeral; Room provides persistent, queryable history independent of engine state |
| SAF + DocumentsContract heuristic | SAF gives scoped access; heuristic recovers filesystem paths for aria2 (which cannot use content:// URIs) |

---

## Manual Device Test Checklist

Run through this after building a release candidate APK.

### Installation
- [ ] Install APK on API 26, 31, 33, 34+ devices
- [ ] All screens launch without crash
- [ ] Notification permission prompt shown on API 33+

### aria2 engine
- [ ] Engine starts within ~5 seconds of app launch
- [ ] Engine status shows "Running" in Settings
- [ ] Engine restarts after tapping **Restart** in Settings
- [ ] Engine survives device screen off / doze

### Downloads
- [ ] HTTP file download completes and appears in History
- [ ] HTTPS file download completes
- [ ] Magnet link download starts (may take time for metadata)
- [ ] `.torrent` file opened from Files app starts download
- [ ] Pause / Resume works on an active download
- [ ] Remove download removes it from the list
- [ ] Multiple simultaneous downloads at configured concurrency

### Browser
- [ ] Navigating to a direct `.zip` / `.apk` URL triggers download
- [ ] Clicking a magnet link on a page triggers download
- [ ] Snackbar "Cannot intercept" shown for SPA / JS blob downloads

### Settings
- [ ] Download folder picker works; selected path shown
- [ ] Concurrency sliders persist across app restart
- [ ] Speed limit field applies and is reflected in aria2 (`aria2.getGlobalOption`)
- [ ] VPN info dialog opens; "VPN Settings" button opens Android VPN settings

### Permissions
- [ ] INTERNET used for JSON-RPC (loopback only — verified with network monitor)
- [ ] No external network traffic to non-user-initiated hosts

### Kill / restart
- [ ] Force-stop app → relaunch → engine restarts, history preserved
- [ ] Reboot device → download service restarts via `BootReceiver` (if any downloads were queued)

---

## CI / CD

GitHub Actions workflow: `.github/workflows/ci.yml`

| Job | Trigger |
|-----|---------|
| Build aria2 ARM64 | Every push/PR (cached) |
| Unit tests + debug APK | Every push/PR |
| Instrumentation tests | PRs to `main` only |
| Release APK | Push to `main` |

Secrets required for release builds:
- `RELEASE_KEYSTORE_BASE64` — base64-encoded `.keystore` file
- `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

---

## License

DownFlow application code: **MIT License** (see `LICENSE`).

Third-party components (including the embedded aria2 binary, which is
GPL-2.0+): see `THIRD_PARTY_NOTICES.md`.

Because aria2 is GPL-licensed and embedded in the APK, you must make the
aria2 source and build instructions available to users. The
`scripts/build_aria2_android.sh` script fulfils this requirement.
