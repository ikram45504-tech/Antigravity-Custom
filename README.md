<div align="center">

  <img src="app/src/main/res/drawable-nodpi/ic_launcher_logo.png" alt="Antigravity Logo" width="88" height="88" style="border-radius: 18px;" />

  # Antigravity for Android

  <p align="center">
    <a href="README.md"><img src="https://img.shields.io/badge/English-007ACC?style=for-the-badge" alt="English" /></a>
    &nbsp;
    <a href="README.id.md"><img src="https://img.shields.io/badge/Bahasa%20Indonesia-DC3545?style=for-the-badge" alt="Bahasa Indonesia" /></a>
  </p>

  [![Android](https://img.shields.io/badge/Android-11.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://android.com)
  [![Architecture](https://img.shields.io/badge/Architecture-ARM64-blue?style=flat-square)](https://arm.com)
  [![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
  [![Language](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
  [![License](https://img.shields.io/badge/License-AGPL_3.0-blue?style=flat-square)](LICENSE)

  <br /><br />

  <img src="assets/banner.png" alt="Antigravity for Android Showcase" width="100%" />

</div>

Antigravity for Android runs Google Antigravity AI coding agents on Android devices. It provides a standalone ARM64 Linux userspace with a native Jetpack Compose UI, enabling the agent to inspect workspace files, edit code, and run terminal commands (`git`, `bash`, `python`, `pkg`) directly on your phone without a PC.

## Features

- **Jetpack Compose UI**: Lightweight chat interface and code editor built for mobile.
- **Local Linux Userspace**: ARM64 environment with `bash`, `python3`, and CLI tools.
- **Official Agent Core**: Runs the Antigravity `language_server` daemon authenticated via Google OAuth 2.0 PKCE.
- **Package Manager (`pkg`)**: Built-in Bash/AWK package manager for installing CLI tools.
- **Background Daemon (`agy-daemon`)**: Background service handling cron tasks, file watchers, and notifications.
- **Skills & Plugins**: Fetch community skills and plugins directly from the catalogue.

## Related Repositories

- [KuatoDev/antigravity-bootstrap](https://github.com/KuatoDev/antigravity-bootstrap) — Pre-built Linux ARM64 rootfs extracted on first run.
- [KuatoDev/antigravity-catalogue](https://github.com/KuatoDev/antigravity-catalogue) — Skills and plugins catalogue. Submit a pull request here to share custom skills :)

## Requirements

- Android 11+ (`arm64-v8a`) — enforced by `minSdk = 30`
- Storage & notification permissions

## Building from source

Prerequisites: **JDK 17**, the **Android SDK** with platform 36, and an ARM64 target device.

```bash
git clone https://github.com/ikram45504-tech/Antigravity-Custom.git
cd Antigravity-Custom

# Point Gradle at your SDK (or export ANDROID_HOME instead).
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

# Debug build — this always works and needs no signing key.
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

### Release builds

Release builds require a signing key and **fail closed** if one is missing, so a debug-signed APK
can never be published as a release by accident.

1. Create or obtain the keystore (never committed):

   ```bash
   keytool -genkeypair -v -keystore app/release.keystore \
     -alias antigravity -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Supply the credentials, either in `local.properties`:

   ```properties
   signing.storePassword=...
   signing.keyAlias=antigravity
   signing.keyPassword=...
   ```

   ...or through the environment:

   ```bash
   SIGNING_STORE_PASSWORD=... SIGNING_KEY_ALIAS=antigravity SIGNING_KEY_PASSWORD=... \
     ./gradlew assembleRelease
   ```

There are no default passwords. `local.properties`, `*.keystore` and `*.jks` are git-ignored.

### Tests

```bash
./gradlew testDebugUnitTest                        # Kotlin/JVM unit tests
python3 -m unittest discover -s app/src/test/python -v   # overlay/daemon helpers
```

Both run in CI (`.github/workflows/tests.yml`).

### CI release pipeline

`.github/workflows/android-release.yml` builds and, on a `v*` tag, publishes a GitHub Release.
Configure these repository secrets before tagging:

| Secret | Purpose |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 app/release.keystore` output |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias |
| `SIGNING_KEY_PASSWORD` | Key password |

The workflow verifies all four are present and fails the build if any is missing.

## Architecture

The app is a Kotlin/Compose shell around a bundled ARM64 Linux userspace.

```
 ┌─────────────────────────── Android app (Kotlin) ───────────────────────────┐
 │  ui/         Compose screens (Chat, Workspace, Settings, Splash)            │
 │  viewmodel/  State holders; delegates split by concern (auth, workspace,    │
 │              customization, git, persistence, transcript parsing)           │
 │  service/    CoreServerService (foreground), quota, terminal, config sync    │
 │  gateway/    SystemGatewayServer — loopback JSON API on 127.0.0.1:45161      │
 │  executor/   EngineExecutor — runs commands inside the userspace            │
 │  install/    BootstrapInstaller, CoreUpdateManager, CoreBinaryPatcher       │
 │  oauth/      Google OAuth 2.0 + PKCE, multi-account store                   │
 └────────────────────────────────────────────────────────────────────────────┘
                                   │ ProcessBuilder
 ┌───────────────────────────── filesDir/ (app-private) ──────────────────────┐
 │  usr/       Linux prefix: bash, git, python3, pkg, overlay scripts         │
 │  engine/    glibc runtime + patched `agy` core binary                      │
 │  home/      HOME: .gemini config, skills, plugins, tokens                  │
 │  .gemini/   config.json, settings.json, skills/, plugins/                  │
 └────────────────────────────────────────────────────────────────────────────┘
```

Two pieces drive the agent side:

- **`usr/bin/agy-daemon`** — Python scheduler (cron, interval, delay, file watch, GitHub polling,
  webhook) whose pure helpers are covered by `app/src/test/python/test_scheduler.py`.
- **`assets/overlay/patches/`** — a byte-level patch pipeline that adapts the closed-source `agy`
  ELF for Android, where several syscalls and paths differ. `patches/spec/patch_manifest.json`
  describes each patch; `CoreBinaryPatcher` applies them, falling back to direct byte replacement
  if the Python pipeline is unavailable.

## Community

- **Telegram Channel**: [t.me/vernkuato](https://t.me/vernkuato)
- **Telegram Group**: [t.me/kuatodevprojects](https://t.me/kuatodevprojects)

## Donation

If you find Antigravity for Android useful and want to support its development, you can donate via:

<div align="center">

  <a href="https://saweria.co/kuatodev">
    <img src="assets/saweria-qr.png" alt="Saweria QR Code" width="180" height="180" style="border-radius: 12px; background: white; padding: 8px;" />
  </a>

  <br />

  [![Saweria](https://img.shields.io/badge/Saweria-kuatodev-FAAE2B?style=for-the-badge&logo=coffee&logoColor=black)](https://saweria.co/kuatodev)
  &nbsp;
  [![PayPal](https://img.shields.io/badge/PayPal-vernkuato-00457C?style=for-the-badge&logo=paypal&logoColor=white)](http://paypal.me/vernkuato)

  <p><sub>Scan the QR code above or visit <strong><a href="https://saweria.co/kuatodev">Saweria</a></strong> (QRIS, GoPay, OVO, Dana) or <strong><a href="http://paypal.me/vernkuato">PayPal</a></strong>.</sub></p>

</div>

## Credits

- **[Werdik (werdio325-png)](https://github.com/werdio325-png)** — `pkg`, `agy-daemon`, and userspace scripts.
- **[Termux](https://github.com/termux)** — Android ARM64 rootfs base and `libtermux-exec`.
- **[Google DeepMind](https://deepmind.google)** — Antigravity core `language_server`.

---

<div align="center">
  <sub>Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).</sub>
</div>
