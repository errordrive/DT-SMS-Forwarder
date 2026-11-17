# DT SMS Forwarder (Android) — v1.1

Minimal, lightweight SMS forwarder Android app for the **Digital Trainer** online course store (Bangladesh).

The app forwards payment SMS received on the owner's phone to his own server and maintains online status via a 5-minute heartbeat ping for multi-account gateways.

## Features & Privacy Rules (v1.1)
- **Multi-Account Gateway & Device ID (§2)**: Unique Device ID per phone (e.g., `phone1`, `phone2`) identifying each phone to the server.
- **Heartbeat Ping (§4)**: Sends automatic 5-minute heartbeat ping (`GET /api/devices/ping?key=<KEY>&device=<ID>`) so the store gateway knows which phones are online to distribute orders evenly.
- **Online Status Indicator (§6)**: Shows real-time server presence (`Server sees this phone: Online` / `Offline` / `Unknown`) with last ping timestamp directly on Home.
- **Dual Test Connection (§7)**: Tests both Gateway POST and Heartbeat GET endpoints with instant feedback.
- **Strict Sender Filtering**: Forwards SMS **only** from exact senders:
  - `bKash` (bKash payment SMS)
  - `NAGAD` (Nagad payment SMS)
  - `16216` (Rocket payment SMS)
- **Zero SMS leakage**: Every other SMS is completely ignored (never read, stored, logged, or sent anywhere).
- **Offline Resilient**: Local Room database queues payment SMS when offline or when network errors occur, deduping by `(from, text, sentStamp)`.
- **WorkManager Auto-Retry**: Automatically retries failed/pending SMS up to 10 attempts with exponential backoff whenever network connectivity is restored.
- **Foreground Service**: Persistent foreground service with auto-restart on device reboot (`BOOT_COMPLETED`).
- **Secure Credentials**: Webhook URL, Gateway Key, and Device ID stored using `EncryptedSharedPreferences`.
- **Brand Assets**: Embedded official transparent vector/PNG logos for bKash, Nagad, and Rocket at exact required display sizes (28dp for counters, 24dp for log entries).
- **Target APK Size**: Under 6 MB with R8 `minifyEnabled` + `shrinkResources`. Debug-signed release APK ready for direct sideloading.

## Cloud Build & CI/CD
This project is configured with GitHub Actions workflow in `.github/workflows/build.yml` to automatically:
1. Run all unit tests (`testDebugUnitTest`) verifying exact JSON schema, strict sender filtering, and amount parsing.
2. Build the optimized release APK (`assembleRelease`).
3. Upload the resulting APK as a GitHub Actions workflow artifact ready for download.

## Install Instructions
1. Download the release APK from GitHub Actions artifacts or releases.
2. Enable "Install unknown apps" in Android settings.
3. Open the APK and install.
4. Open the app and grant the SMS permission.
5. In Settings:
   - Configure the Webhook URL and your Gateway Key.
   - Tap "Test connection" to verify server connectivity.
   - Tap "Disable" under Battery Optimization so Android does not kill the forwarder service in the background.
6. Return to Home and turn the Forwarder Service switch **ON**.
