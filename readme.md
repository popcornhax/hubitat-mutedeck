# MuteDeck Webhook Receiver for Hubitat

This Hubitat app receives webhook events from **MuteDeck** (<https://mutedeck.com>) and exposes meeting state (mute, in-call, video, screen sharing, recording) to Hubitat as standard device events using **Switches**.

---

## What This App Does

When MuteDeck sends a webhook update, this app can:

- Turn **Switches ON/OFF** based on:
  - Mute state
  - In‑call / meeting state
  - Camera (video) state
  - Screen sharing state
  - Recording state
- All switches are turned off when a call ends
  - MuteDeck can control system mic when outside a call, but it seems to have a bug where it doesn't report changes to the webhook
- Enable automations using Rule Machine, dashboards, indicators, or lighting scenes
- Keep all logic local to your Hubitat hub (LAN‑first by default)

Example use cases:

- Turn on a key light when your camera is active
- Show mute / in‑call status on a dashboard tile
- Trigger a “do not disturb” lighting scene when a meeting starts

---

## Requirements

- Hubitat Elevation hub (recent firmware recommended)
- MuteDeck running on a machine reachable by the hub (same LAN for local mode)
- One or more **Switch** devices created in Hubitat

---

### Cloud / External Access (Optional)

- The app can optionally expose a **Hubitat Cloud endpoint**
- Cloud access is disabled unless explicitly enabled by the user

---

## Installation (via Hubitat Package Manager)

1. Install **Hubitat Package Manager**
2. Choose **Install a Package**
3. Select **MuteDeck Webhook Receiver**
4. Complete installation

(Alternatively, install manually via Apps Code using the Groovy file and enable oAuth for the app.)

---

## Configuration

1. Add the app under **Apps**
2. Select the Switches you want to map to each MuteDeck state
3. (Optional) Enable Cloud Endpoint access
4. Copy the generated webhook URL
5. Paste the URL into **MuteDeck → Settings → Notifications → Webhook**

Changes take effect immediately.

---

## Notes & Limitations

- This app does **not** create devices automatically — use Hubitat’s built‑in Switch device
- The webhook endpoint supports both `POST` (events) and `GET` (status/health)
- IP allow‑listing is available but may not work as expected in Cloud mode due to Hubitat relay IPs
