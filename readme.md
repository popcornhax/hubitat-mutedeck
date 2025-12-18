

# MuteDeck Webhook Receiver for Hubitat

This Hubitat app receives webhook events from **MuteDeck** (https://mutedeck.com) and exposes meeting state (mute, in-call, video, screen sharing, recording) to Hubitat as standard device events using **Switches**.

---

## What This App Does

When MuteDeck sends a webhook update, this app can:

- Turn **Switches ON/OFF** based on:
  - Mute state
  - In‑call / meeting state
  - Camera (video) state
  - Screen sharing state
  - Recording state
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

## Security Model (Important, but simple)

### OAuth (Optional)
- OAuth is **optional** for LAN-only mode
- If OAuth is enabled for this app in Hubitat Apps Code:
  - A tokenized webhook URL is generated
  - Requests require `access_token=...`
- If OAuth is not enabled:
  - The **local LAN endpoint is unauthenticated**

### Cloud / External Access (Optional)
- The app can optionally expose a **Hubitat Cloud endpoint**
- Cloud mode **requires OAuth**
- Cloud access is disabled unless explicitly enabled by the user

---

## Installation (via Hubitat Package Manager)

1. Install **Hubitat Package Manager**
2. Choose **Install a Package**
3. Select **MuteDeck Webhook Receiver**
4. Complete installation

(Alternatively, install manually via Apps Code using the Groovy file.)

---

## Configuration

1. Add the app under **Apps**
2. (Optional) Enable OAuth for the app in **Apps Code**
3. Select the Switches you want to map to each MuteDeck state
4. (Optional) Enable Cloud Endpoint access
5. Copy the generated webhook URL
6. Paste the URL into **MuteDeck → Settings → Notifications → Webhook**

Changes take effect immediately.

---

## Notes & Limitations

- This app does **not** create devices automatically — use Hubitat’s built‑in Switch device
- The webhook endpoint supports both `POST` (events) and `GET` (status/health)
- IP allow‑listing is available but may not work as expected in Cloud mode due to Hubitat relay IPs