# Prompt: Build the DT SMS Forwarder Android App (Minimal) — v2, with provider logos

> Copy the whole prompt below and give it to an Android developer or an AI app builder. Fill in `<GATEWAY_KEY>` yourself before sharing — never publish the real key.

---

You are an expert Android developer. Build a minimal, lightweight SMS forwarder Android app called **DT SMS Forwarder** for the Digital Trainer online course store (Bangladesh). The app does ONE thing only: forward payment SMS received on the owner's own phone to his own server. Nothing else.

## 1. What the app is for

The owner receives bKash / Nagad / Rocket payment SMS on his personal phone number. This app forwards those SMS to his server, which matches the amount + TrxID against course orders and marks payments as matched. The app must NOT approve orders, must NOT read any other SMS, and must NOT have any other feature.

## 2. Tech stack (lightweight is a hard requirement)

- Language: **Kotlin**
- UI: **Android Views + Material Design 3** (NOT Jetpack Compose — keep the APK small)
- minSdk **26**, targetSdk **34**, versionCode 1, versionName 1.0
- Package: `com.digitaltrainer.payments`
- App name: **DT SMS Forwarder**
- Build: release APK, **R8 minifyEnabled + shrinkResources**, debug-signed so it installs directly. Target APK size **under 6 MB**.
- Libraries only: `material`, `appcompat`, `room`, `retrofit` + `okhttp`, `workmanager`, `security-crypto` (EncryptedSharedPreferences), `lifecycle-viewmodel`. No Firebase, no Compose, no image libraries, no analytics.

## 3. Server contract (the server already exists and is live — do not change it)

- Base URL: `https://digital-trainer-api.a2nnajmul.workers.dev`
- Endpoint: `POST /api/sms/gateway?key=<GATEWAY_KEY>` (the key is provided by the owner at setup time; it is also accepted as an `X-Gateway-Key` header)
- Headers: `Content-Type: application/json`
- Body (exact JSON, raw SMS forwarded as-is — the app does NO parsing):
  ```json
  {"from":"bKash","text":"<full SMS text>","sentStamp":1763400000000,"receivedStamp":1763400001000,"sim":"sim1"}
  ```
  - `from`: the SMS sender exactly as received (`bKash`, `NAGAD`, or `16216`)
  - `text`: the complete SMS text, unchanged
  - `sentStamp` / `receivedStamp`: epoch milliseconds
  - `sim`: `sim1`, `sim2`, or `undetected`
- Success = **any HTTP 2xx**. Any other response is a failure and must be retried.
- Retry: **up to 10 attempts** with exponential backoff (minimum 10 seconds), only when network is available (WorkManager, `NetworkType.CONNECTED` constraint).
- Offline: if there is no network, queue the SMS in a local Room database and send it when the network returns. Never lose a payment SMS. Dedupe the queue by (sender + full text + sentStamp) so the same SMS is never sent twice.

## 4. Sender filter (strict — this is a privacy rule, not a preference)

- Forward SMS **only** from these three senders (exact match):
  - `bKash` → bKash payment SMS
  - `NAGAD` → Nagad payment SMS
  - `16216` → Rocket payment SMS
- **Every other SMS is ignored completely** — it is never read, stored, logged, or sent anywhere. The app must not request or use any SMS content outside these three senders.

## 5. How forwarding works

1. `BroadcastReceiver` for `SMS_RECEIVED` receives an incoming SMS.
2. If the sender is not one of the three above, drop it silently.
3. If the service switch is OFF, store the SMS in the Room queue and do nothing else.
4. If the service switch is ON, POST the JSON above to the server immediately. On failure, keep it in the queue and retry via WorkManager (rule in §3).
5. Show one notification per successfully forwarded payment SMS ("Payment SMS forwarded — bKash ৳100.00") — only the provider and amount pattern from the server response, never the full SMS text in the notification.

## 6. Background service

- While the service is ON, run a **foreground service** with a persistent notification ("DT SMS Forwarder is running").
- Restart the service after device reboot (`RECEIVE_BOOT_COMPLETED`) if the switch was ON.
- Request `POST_NOTIFICATIONS` permission at runtime with a clear explanation.
- Guide the user to turn OFF battery optimization for this app (show the system settings intent from Settings) — otherwise Android may kill the service.

## 7. Screens (minimal — two screens only)

### Home
- Big **service ON/OFF switch** (the main control).
- Status line: Running / Stopped.
- Three small counters: forwarded today, one per provider, each with its logo (see §9A): bKash / Nagad / Rocket.
- Last forwarded time.
- A short log list (newest first, max 50 entries): time, sender, status (Sent / Queued / Failed). Tapping an entry shows the raw SMS text and the server response. No other actions.

### Settings
- Webhook URL field (default: `https://digital-trainer-api.a2nnajmul.workers.dev/api/sms/gateway`) and Gateway Key field (password-masked input). Both stored in **EncryptedSharedPreferences**. The key must never appear in logs, toasts, or screenshots.
- Permissions status list (SMS permission, notifications, battery optimization) with a button to open the matching system setting for each missing one.
- "Test connection" button: sends a test POST with a dummy payload and shows success/failure.
- About section: app version + "Forwards only bKash / Nagad / Rocket SMS to your own server."

## 8. Permissions (manifest)

`RECEIVE_SMS`, `INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, `WAKE_LOCK`. Request SMS + notification permissions at runtime with a plain-language explanation. The app must work with no other permission.

## 9. Design

- Clean, minimal, no gradients.
- Colors: primary `#EC1C24`, background `#F7F7F8`, surface `#FFFFFF`, text `#231F20`, secondary text `#808285`, success `#16A34A`, warning `#F59E0B`, error `#DC2626`.
- Provider colors: bKash `#E2136E`, Nagad `#F6921E`, Rocket `#89288F` (used only for the small provider badge in the log list).
- The log list shows each provider's logo (see §9A) at 24dp next to the entry — never a black-background image.
- Bottom navigation: Home · Settings. No other navigation.


## 9A. Provider logos (supplied assets — use these exact files)

- The owner supplied the three provider logos. They were given as **black-background JPGs** — do NOT use those JPGs as-is in the app: on the light `#F7F7F8` / white background they would show as black boxes.
- Use the **transparent PNG versions supplied with this prompt** instead (black background already removed, letter counters clear, bKash dark-grey "Kash" text preserved):
  - `bkash.png` — 1600×733, bKash (pink `#E2136E` + dark-grey wordmark + pink bird)
  - `nagad.png` — 1600×673, Nagad (red `#EC1C24` + orange `#F6921E`)
  - `rocket.png` — 1600×1008, Rocket (purple `#89288F`)
- Place them as drawables, no resizing in code beyond the sizes below:
  - `app/src/main/res/drawable-nodpi/bkash.png`
  - `app/src/main/res/drawable-nodpi/nagad.png`
  - `app/src/main/res/drawable-nodpi/rocket.png`
- Where they appear:
  - Home counters: logo at **28dp** above each provider's today-count.
  - Log list: logo at **24dp** at the left of each entry (sender `bKash` → bkash.png, `NAGAD` → nagad.png, `16216` → rocket.png).
  - Notification: no logo (text only, per §5).
  - Settings / About: no logo.
- Do NOT recolour, stretch, or crop the logos. Keep their aspect ratio. On the light background they render exactly as the official lockups.
- Note: the supplied bKash image is cropped tight at the left edge (the stem of the "b" touches the edge). At 24–28dp this is not noticeable; do not try to "fix" it by stretching — if the owner later supplies a full-bleed bKash lockup, swap only that one file.

## 10. Security rules (non-negotiable)

- The gateway key is NEVER hardcoded in source code, NEVER committed to any repo, NEVER printed in logs. It lives in `local.properties` at build time and in EncryptedSharedPreferences at runtime.
- No password is ever stored in a file. There is no login screen in this minimal app.
- The app sends SMS data only to the configured webhook URL — to no other server, SDK, or analytics service.
- The app never approves, rejects, or changes any order. It only forwards SMS.

## 11. Test fixtures (use these to verify the forwarded payload)

Forward each of these exactly as the `text` field and confirm the server returns 2xx:

- bKash, from `bKash`: `You have received Tk 100.00 from 01580471306. Fee Tk 0.00. Balance Tk 532.20. TrxID DI445VCEIE at 04/09/2026 18:06`
- Nagad, from `NAGAD`: `Money Received. Amount: Tk 49.00 / Sender: 01755340446 / TxnID: 75GIMCJS`
- Rocket, from `16216`: `Tk99.00 received from A/C:***127. TxnId:6587766794. Balance Tk 1,250.00.`

Also verify: an SMS from any other sender (e.g. `GP`, `Bkash` with different casing is NOT the same — only exact `bKash`) is ignored; the app works offline and sends the queued SMS when the network returns; killing and rebooting the phone restarts the service when the switch was ON.

## 12. Verification before delivery

- `./gradlew testDebugUnitTest` passes (unit tests: sender filter accepts only the 3 senders; payload builder produces the exact JSON from §3; queue dedupe works).
- `./gradlew assembleRelease` succeeds. Report the APK path, size in bytes, and `aapt dump badging` output (package + version).
- Visual check: Home counters and log entries show the three logos cleanly on the light background — no black box behind any logo, no black fill inside the bKash 'b'/'a' counters or the Rocket letters.
- Live test against the real server with the owner's key: forward the 3 test SMS above and confirm 2xx; confirm a non-payment SMS returns 2xx ignored (no retry storm); confirm a wrong key returns 401.
- Deliverables: the release APK file, the full source code (including the three logo PNGs in `drawable-nodpi`), and short install instructions (enable "Install unknown apps", open the APK, grant SMS permission, turn the switch ON, turn off battery optimization, enter the webhook URL + key in Settings, tap "Test connection").
