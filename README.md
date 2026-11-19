# IPSnoop

A lightweight macOS system-tray utility that monitors your device's **public (external) IP address**, alerts you when it changes, and requires you to confirm the new address before it's considered trusted.

**Version:** 1.3.1 (build 633) · © 2007 netbridgelimited.com

---

## What it does

IPSnoop lives in your macOS menu bar and periodically checks your public IP address by querying an external service. When the address changes — for example after a VPN connection drops, your ISP reassigns your IP, or you switch networks — IPSnoop plays an audible ping and switches its tray icon to a **red** state to indicate the new IP is unconfirmed. Once you confirm the new address, the icon returns to **green**.

This gives you an at-a-glance signal that your externally visible identity has changed, which is useful for anyone relying on a stable IP for remote access, allow-lists, or security monitoring.

---

## Features

- **Live public IP display** in the menu bar, updated on launch and checked **hourly** (aligned to the top of each hour).
- **Change detection with audible alert** — plays a ping sound when your IP differs from the last confirmed value.
- **Confirmation workflow** — the tray icon turns **red** on change and stays red until you explicitly confirm the new IP; **green** indicates a confirmed, trusted address.
- **Confirmation notification** — a native macOS notification shows the old and new IP when a change is detected.
- **Copy IP to clipboard** — double-click the tray icon, or use the menu item.
- **Selectable ping sound** — choose from Ping, Submarine, Basso, or Pop.
- **Manual "Check IP Address"** — force an immediate re-check without waiting for the timer.
- **Load at Startup** — optionally registers a `LaunchAgent` so IPSnoop launches at login.
- **Show in App Switcher** — toggle the app's activation policy so it appears (or stays hidden) in ⌘-Tab.
- **About dialog** with version and build info.
- **Single-instance enforcement** — a loopback socket lock prevents multiple copies; launching a second copy notifies the running instance.

---

## Requirements

- **macOS** (uses `SystemTray`, `osascript`, `afplay`, and the `com.apple.eawt` API).
- **Java** — compiled for a JDK supporting Java 8+ language features.
- A system tray (standard on macOS).
- An internet connection (to reach the IP lookup service).

> Note: IPSnoop relies on macOS-specific tools (`afplay`, `osascript`) and internal Apple APIs, so it will not run unmodified on Windows or Linux.

---

## Building

The project is a NetBeans Java project and can be built with NetBeans or Ant.

**With NetBeans:**
1. Open the `IPSnoop` folder as a project.
2. Run **Clean and Build** (`Shift+F11`).

**With Ant:**
```bash
ant clean jar
```

Build outputs:

- `build/classes/` — compiled `.class` files
- `dist/IPSnoop.jar` — the runnable application JAR

---

## Running

**From the JAR:**
```bash
java -jar dist/IPSnoop.jar
```

**From the macOS app bundle** (if present):
```bash
open IPSnoop.app
```

On launch, IPSnoop installs itself into the menu bar, performs an immediate IP check, and starts the hourly timer.

### Menu options

| Menu item | Action |
|-----------|--------|
| **About** | Shows version, build, and description. |
| **Confirm IP Address** | Marks the current IP as confirmed (clears the red/unconfirmed state). |
| **Check IP Address** | Forces an immediate IP re-check. |
| **Copy IP Address** | Copies the current IP to the clipboard. |
| **Ping Sound** | Submenu to choose the alert sound (Ping, Submarine, Basso, Pop). |
| **Load at Startup** | Checkbox — installs/removes `~/Library/LaunchAgents/com.netbridgelimited.ipsnoop.plist`. |
| **Show in App Switcher** | Toggles the app's macOS activation policy (ACCESSORY). |
| **Quit** | Exits IPSnoop. |

**Double-click** the tray icon to copy the current IP to the clipboard.

---

## How it works

- **IP lookup:** queries `http://checkip.amazonaws.com` and reads the first line of the response.
- **Change detection:** the current IP is compared against the last *confirmed* IP (`lastKnownIP`). Any mismatch sets the unconfirmed flag and triggers the ping + red icon.
- **Hourly schedule:** a `Timer` fires on the hour boundary and every 60 minutes thereafter. If a change was pending (unconfirmed), the ping replays on each check until you confirm.
- **Tray rendering:** a `BufferedImage` is built combining a green/red status dot (or a loaded `green.png` / `red.png` resource) with the IP text, using the macOS SF Pro font with sensible fallbacks.
- **Single instance:** binds a `ServerSocket` on `127.0.0.1:49275`; a second launch connects and sends `show`, prompting the running instance to notify.
- **Startup:** writes a `LaunchAgent` plist that runs `java -jar <path>/dist/IPSnoop.jar` at login.
- **Sounds:** played via `afplay` from `/System/Library/Sounds/`.

---

## Project layout

```
IPSnoop/
├── src/ipsnoop/IPSnoop.java      # Application source
├── resources/                    # Status icons (green.png, red.png, etc.)
├── build.xml                     # Ant build script
├── manifest.mf                   # JAR manifest
├── nbproject/                    # NetBeans project metadata
├── dist/                         # Build output (IPSnoop.jar)
└── build/                        # Compiled classes
```

---

## Known limitations

- The IP lookup uses **plain HTTP** (`checkip.amazonaws.com`); swap in an HTTPS service such as `https://api.ipify.org` if you prefer encrypted lookups.
- The selected ping sound (`setPingSound`) is currently only acknowledged via notification — actual playback always uses `Ping.aiff` regardless of choice. Wiring the selected sound into `playPingSound()` would complete this feature.
- Uses the internal `com.apple.eawt.Application` API, which may require `--add-exports` / `--add-opens` flags on newer JDKs.
- The hourly check means changes can take up to an hour to be detected unless you use **Check IP Address** manually.

---

## License

© 2007 netbridgelimited.com. All rights reserved.
