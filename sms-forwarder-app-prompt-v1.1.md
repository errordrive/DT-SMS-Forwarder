# Prompt: Update the DT SMS Forwarder Android App to v1.1 (Multi-Account Gateway)

> Give this prompt to the same Android developer or AI app builder who built v1.0, **together with the complete v1.0 source code**. This is an UPDATE, not a rebuild: apply only the changes listed below. Do not rebuild from scratch and do not change anything that is not listed here. Fill in `<GATEWAY_KEY>` yourself before sharing — never publish the real key.

---

## 0. Why this update (read first)

The server (Digital Trainer store) now supports **multiple payment accounts per method**. The owner can have several bKash numbers, each on its own phone. The gateway shows customers a **random number only from phones that are online**. The server decides "online" like this:

- A phone is **online** when it reached the server in the last **15 minutes** — by forwarding an SMS or by pinging the heartbeat endpoint below.
- If no phone is online, the gateway falls back to any enabled account, so sales never stop.

The v1.0 app only forwards SMS. If no payment SMS arrives for 15+ minutes, the server thinks the phone is offline and stops showing its number — even though the phone is actually online. **That is what v1.1 fixes: the app now also sends a small heartbeat ping every 5 minutes, and it identifies itself with a Device ID.** No other behaviour changes.

## 1. What the server already does (already live — do not change the server)

- Base URL: `https://digital-trainer-api.a2nnajmul.workers.dev`
- SMS endpoint: `POST /api/sms/gateway?key=<GATEWAY_KEY>&device=<DEVICE_ID>` (the key is also accepted as an `X-Gateway-Key` header; the device id is also accepted in the JSON body as `"device"`)
- **Heartbeat endpoint (new): `GET /api/devices/ping?key=<GATEWAY_KEY>&device=<DEVICE_ID>`**
  - Success response: `{"ok":true,"device":"phone1","online":true}`
  - Wrong/missing key → `401`. Missing device → `400`.
  - This endpoint only records "this phone was seen now". It sends no SMS and changes no order.
- The server picks a random online account per order and pins it to that order, so the customer keeps seeing the same number for that order.

## 2. New feature: Device ID (one per phone)

- Add a **Device ID** field in Settings (plain text input, stored in **EncryptedSharedPreferences** next to the webhook URL and gateway key).
- Format: lowercase letters, numbers and dashes only, no spaces — for example `phone1`, `phone2`, `shop-phone`. Trim whitespace and lowercase the input on save. Reject an empty value with a plain error message ("Device ID is required").
- **Every phone gets its own Device ID.** The owner installs the same APK on each phone and types a different Device ID on each (`phone1` on the first phone, `phone2` on the second, and so on). The app never invents an id by itself.
- The Device ID is used in two places:
  1. Appended to the webhook URL as `&device=<DEVICE_ID>` (see §3).
  2. Sent in the heartbeat ping as `&device=<DEVICE_ID>` (see §4).
- Settings persist across the update — if the owner updates from v1.0 without reinstalling, the saved webhook URL and gateway key must survive. The Device ID is new, so it starts empty and the owner types it once.

## 3. Change: how the webhook URL is built

- Settings keeps the **Webhook URL** field (default: `https://digital-trainer-api.a2nnajmul.workers.dev/api/sms/gateway`). The owner types the base URL only — **without** `?key=` and without `&device=`.
- The app builds the final URL itself for every request: `base + "?key=" + <GATEWAY_KEY> + "&device=" + <DEVICE_ID>`. If the base URL already contains a `?`, use `&` for the first parameter instead (defensive only — the default has none).
- The Gateway Key field stays password-masked. The key must never appear in logs, toasts, or screenshots — same rule as v1.0.
- **Minor UI fix from the v1.0 screenshots:** the Webhook URL field cut the URL off mid-text. Make the field horizontally scrollable (or full-width with smaller text) so the whole URL is visible.

## 4. New feature: heartbeat ping (the most important change)

- While the service switch is **ON**, the app must ping the heartbeat endpoint **every 5 minutes**. While the switch is OFF, it must not ping (the phone is intentionally offline).
- Ping = `GET /api/devices/ping?key=<GATEWAY_KEY>&device=<DEVICE_ID>`, with a 10-second timeout. Any 2xx response means success. On failure, do nothing special — just try again at the next 5-minute tick. Never show a notification for a ping, never log the key.
- **How to schedule it:** the app already runs a foreground service while ON, so run the ping from that service with a coroutine/Handler timer every 5 minutes. Do NOT use `WorkManager` periodic work as the main timer — its minimum interval is 15 minutes, which is exactly the server's offline window and would let the phone flip offline. As a backup only, also schedule a `WorkManager` periodic task (15-minute interval, `NetworkType.CONNECTED`) that pings once, in case the foreground service was killed.
- Restart the ping timer after device reboot together with the service (same `RECEIVE_BOOT_COMPLETED` path as v1.0).
- Use the ping response to show the phone's online state on Home (see §6). If a ping fails, keep showing the last known state — do not flash "Offline" on a single failed ping.
- **No new permissions.** The ping uses `INTERNET`, which the app already has. Do not add any permission, SDK, or analytics for this.

## 5. Change: the forwarded SMS payload (add one field)

- The v1.0 payload is unchanged except for **one added field**: `"device":"<DEVICE_ID>"`.
  ```json
  {"from":"bKash","text":"<full SMS text>","sentStamp":1763400000000,"receivedStamp":1763400001000,"sim":"sim1","device":"phone1"}
  ```
- Everything else about forwarding is identical to v1.0: only the three exact senders (`bKash`, `NAGAD`, `16216`), raw SMS forwarded as-is, no parsing, success = any HTTP 2xx, up to 10 retries with exponential backoff (minimum 10 seconds), offline queue in Room with dedupe by (sender + full text + sentStamp), one notification per forwarded payment SMS.
- The device id in the body is a backup — the URL query parameter (§3) is the primary channel. Send both.

## 6. Change: Home screen (add one status line)

- Keep everything from v1.0 exactly as it is: service ON/OFF switch, Running/Stopped line, three counters (bKash / Nagad / Rocket forwarded today), Last forwarded time, Recent Logs (max 50).
- **Add one line under the Running/Stopped line:** `Server sees this phone: Online` (green) or `Server sees this phone: Offline` (grey/red), plus the last ping time in small text (for example `Last ping: 21:52`). The state comes from the most recent ping response (`online: true`) or, if no ping has succeeded yet, from "never pinged" → show `Server sees this phone: Unknown`.
- Add the last ping time to the Recent Logs area as a single non-SMS entry only if it is cheap to do so — if it complicates the log list, skip it; the status line above is the requirement, the log entry is optional.
- Do not add any other screen, tab, or navigation. Bottom navigation stays Home · Settings.

## 7. Change: Settings screen

- Keep everything from v1.0: Webhook URL field, Gateway Key field (password-masked), permissions status list with buttons to open the matching system setting, About section.
- **Add the Device ID field** (§2) directly under the Gateway Key field, with the hint text `One id per phone, e.g. phone1`.
- **"Test connection" now tests two things and shows both results:**
  1. The gateway POST (same dummy payload as v1.0) — show `Gateway: OK` or `Gateway: failed`.
  2. The heartbeat ping (`GET /api/devices/ping?...`) — show `Heartbeat: OK (online)` or `Heartbeat: failed`.
  - Both must be tested with the saved key and device id. If the Device ID is empty, disable the button with the message "Enter a Device ID first".
- Keep the battery-optimization guidance exactly as in v1.0 (open the system settings intent from Settings). The owner must still turn battery optimization OFF for this app, otherwise Android may kill the service and the pings.
- About section: update the version text to the new version (see §9) and keep the line "Forwards only bKash / Nagad / Rocket SMS to your own server."

## 8. What does NOT change (do not touch)

- The three senders and the exact-match filter. The app still ignores every other SMS completely.
- The server URL/host, the JSON field names, the retry and queue rules, the foreground service, the boot restart, the notification text.
- The design system from v1.0 (clean, minimal, no gradients; primary `#EC1C24`, background `#F7F7F8`, surface `#FFFFFF`, text `#231F20`, secondary `#808285`, success `#16A34A`, warning `#F59E0B`, error `#DC2626`; provider colors bKash `#E2136E`, Nagad `#F6921E`, Rocket `#89288F` for the small badges only).
- The package name (`com.digitaltrainer.payments`), the app name (DT SMS Forwarder), the two screens, the bottom navigation.
- The security rules from v1.0 §10 (key never hardcoded, never in logs, EncryptedSharedPreferences only, no other server/SDK/analytics, the app never approves or changes an order).
- Tech stack and size target from v1.0 §2 (Kotlin, Views + Material 3, minSdk 26, targetSdk 34, R8, APK under 6 MB).

## 9. Version and deliverables

- Bump to **versionCode 2, versionName 1.1**.
- `./gradlew testDebugUnitTest` passes. Add unit tests for: URL builder (base + key + device, including the `?`/`&` case); Device ID validation (trims, lowercases, rejects empty); payload builder includes the `"device"` field; ping scheduler fires only while the service is ON.
- `./gradlew assembleRelease` succeeds. Report the APK path, size in bytes, and `aapt dump badging` output (package + version).
- Live test against the real server with the owner's key and Device ID `phone1`:
  1. Set Device ID to `phone1`, tap "Test connection" — both Gateway and Heartbeat show OK.
  2. Turn the service ON, wait 5+ minutes, and confirm in the store admin (Admin → SMS Payments → Connected phones) that `phone1` shows **Online** with a recent "Last seen" time — without any SMS arriving.
  3. Forward the 3 test SMS from v1.0 §11 and confirm 2xx each; confirm a non-payment SMS returns 2xx ignored (no retry storm); confirm a wrong key returns 401.
  4. Confirm the Home screen shows `Server sees this phone: Online` and the last ping time.
- Deliverables: the release APK file, the full source code (with the v1.0 → v1.1 changes clearly committed or listed), and a short "what changed" note for the owner: Device ID field, automatic 5-minute heartbeat, online status on Home, Test Connection now checks both endpoints. Install instructions are the same as v1.0, plus: "type a different Device ID on each phone, and tap Test Connection — both lines must say OK."
