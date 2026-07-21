# Build Setup + The Week-1 Spike

> **Working title only.** "Final Cut" is an Apple trademark and cannot ship. Naming is tracked as a real workstream in the product spec.

**Who this is for:** a solo builder with near-zero Android experience, using Claude Code to author the code, with 2 Android phones + 1 Android tablet, targeting a working multicam demo in 4–8 weeks.

**Read this first:** everything below serves one goal — answering the single question in [Part 3](#part-3--the-spike) before we write a line of product code. Install in the order given; Part 1 is a large download and should be started now.

---

## Part 1 — Install (start the big download first)

### 1. Android Studio — **start this now**
<https://developer.android.com/studio> · latest stable · ~1 GB download, ~8–12 GB on disk once the SDK lands.

This one package is your entire toolchain. It bundles the JDK, the Gradle build system, the Kotlin compiler, `adb` (the tool that talks to your phones), the emulator, and the SDK manager. **Do not install Java, Kotlin, or Gradle separately** — a second JDK on the machine is a classic source of build errors that are miserable to diagnose.

During the setup wizard, accept the standard install. Afterwards, open **SDK Manager** (the cube-with-arrow icon in the toolbar) and confirm:

| Tab | What you need | Why |
|---|---|---|
| SDK Platforms | Latest stable Android + **Android 13 (API 33)** | We compile against latest, but `minSdk 33` — see below |
| SDK Tools | Android SDK Build-Tools, Platform-Tools, Command-line Tools | `adb` lives in Platform-Tools |

**Why `minSdk 33` specifically:** Android's network service discovery (`NsdManager` — how the tablet will find your phones) is genuinely broken on Android 12 and below. Setting the floor at 33 buys us out of an entire category of bug for free. It costs us the small tail of users still on Android 12, which is irrelevant for a demo.

### 2. Git
<https://git-scm.com/download/win> · accept every default.

**This is not optional and it is not bureaucracy.** Claude Code will write thousands of lines you did not read. Git is the undo button for all of it. Without it, one bad refactor loses a week. We commit before every significant change.

A GitHub account (free) is worth creating at the same time for off-machine backup.

### 3. scrcpy
<https://github.com/Genymobile/scrcpy/releases> · download the Windows zip, unzip somewhere permanent (e.g. `C:\tools\scrcpy`), add that folder to your PATH.

Mirrors and controls an Android device from your desktop, over USB or Wi-Fi. You will use it constantly — to watch what all three devices are doing at once without juggling hardware, and eventually to film the demo itself. Free, no app to install on the phone.

### 4. DaVinci Resolve (free edition)
<https://www.blackmagicdesign.com/products/davinciresolve> · ~3 GB. Requires a free account.

Your ground truth. When our export looks wrong, Resolve tells you whether the file is broken or our player is. It is also the reference for what correct multicam sync and grading look like — and, usefully, it is the destination Blackmagic wants users to leave for, which is the exact handoff this product refuses to make.

### Optional, later
- **Wireshark** <https://www.wireshark.org/download.html> — you will eventually need to see the time-sync packets on the wire. Install when that day comes, not before.
- **Figma** (free, browser) — for design spec artifacts.

### Explicitly NOT installing

| Skipped | Why |
|---|---|
| **ffmpeg / ffmpeg-kit** | Archived **July 2, 2026**. The maintainer cited *codec patent liability* after the Via-LA consolidation, not just burnout — and that exposure would transfer to you the moment you ship your own H.264/HEVC encoder. The successor (`ffmpeg-kit-next`) is source-only, Nix-built, 27 stars. We use the OS-provided MediaCodec instead, which sidesteps both the maintenance and the patent question. |
| **Vulkan tooling** | OpenGL ES is correct here. It's what Media3's effects pipeline already uses, it interops trivially with MediaCodec Surfaces, and every sample on the internet uses it. Vulkan's advantage is CPU draw-call overhead — the wrong axis entirely for a full-screen video shader chain. |
| **NPU vendor SDKs** (Qualcomm/MediaTek) | NNAPI was deprecated in Android 15 and the replacement is a per-vendor compile-and-test matrix. That's a team's job. LiteRT's GPU delegate is vendor-neutral and fast enough for realtime segmentation. |
| **A separate JDK/Kotlin/Gradle** | Bundled with Android Studio. Installing your own causes version conflicts. |

---

## Part 2 — Hardware and environment

### A Wi-Fi router you control
**This is a hardware requirement, not a nice-to-have.** The architecture is infrastructure Wi-Fi + NSD discovery — the same choice Blackmagic made for their Android multicam. We ruled out the alternatives on evidence:

- **Wi-Fi Aware** — messages cap at ~255 bytes and are *explicitly documented* as possibly undelivered, out-of-order, or duplicated. It also dies whenever Wi-Fi Direct or tethering is active.
- **Wi-Fi Direct** — no client-to-client communication inside a group, no multi-hop, heavy battery drain.
- **Nearby Connections** — picks its own transport, so latency is non-deterministic. That's poison for time sync, and it's Google-Play-Services-only.

Apple's Live Multicam needs no router (it's AirDrop-class peer-to-peer, almost certainly AWDL). We can't match that; Android has no AWDL equivalent. Using the tablet's hotspot as a fallback is on the table and is a question for the tech spec.

### Prepare all three devices
On each phone and the tablet:
1. **Settings → About phone → tap "Build number" seven times** to unlock Developer Options.
2. **Settings → System → Developer options → enable "USB debugging" and "Wireless debugging."**
3. Connect once over USB, accept the "Allow USB debugging?" prompt, then switch to wireless.

Record what you own — model and chipset for each — because the capability gates in Part 3 depend on the silicon, and because **we will ship a device allowlist, not capability detection.** That's not laziness; it's what Blackmagic does. They allowlist ~6 flagship families and tune per device, and their public explanation is exactly that Android's OEM fragmentation makes anything else untenable. A solo developer cannot out-tune a company with a color-science team.

---

## Part 3 — The spike

**Nothing else starts until this returns a number.**

### The question

> Can Media3's `CompositionPlayer` preview **4 concurrent 1080p H.264 proxies in a 2×2 grid at a sustained 30 fps** on the actual tablet?

### Why this and nothing else

The entire product thesis is *capture and cut and auto-direct on-device* — the loop Blackmagic won't close. `CompositionPlayer` is what makes that newly buildable in 2026: it's the first Media3 API where realtime preview consumes **the same `Composition` object as the export**, so what you see is what you render.

It is also marked `@ExperimentalApi`, and concurrent hardware decoder limits are per-SoC and real — the advertised `getMaxSupportedInstances()` routinely overstates what a device actually sustains.

So: the most load-bearing bet in the plan is also the least proven. We price it in week one, on your hardware, before any spec hardens around it.

### Method

1. Confirm the toolchain: Gradle build succeeds, app deploys to all three devices over wireless ADB.
2. **Log device capabilities on each device.** Four values decide what we can build:

   | Characteristic | Why it matters |
   |---|---|
   | `INFO_SUPPORTED_HARDWARE_LEVEL` | Manual ISO/shutter are guaranteed **only** on `FULL`. Below that, the pro camera UI can't exist. |
   | `REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT` | Gates HLG10, our only 10-bit path (LOG has no Android API at all). |
   | `SENSOR_INFO_TIMESTAMP_SOURCE` | **Must be `REALTIME`.** On `UNKNOWN` devices, frame timestamps are not comparable across the network — which means multicam sync is impossible on that device, full stop. This is the single highest-stakes value in the table. |
   | `getMaxSupportedInstances()` (H.264 + HEVC) | The advertised decoder ceiling. Measure the real one below. |

3. Side-load 4 × 1080p30 H.264 clips. Build the 2×2 grid on Media3's custom-compositor sample.
4. Play for a sustained 60 s and record: **dropped frames, actual decoder instances, thermal status, memory.** Repeat on the tablet and both phones.

### Go / no-go

**Green:** sustained 30 fps, 2×2, 1080p, 60 s on the tablet, no sustained drops.

**If red — the fallback ladder, in order:**
1. 540p proxies (Apple's own Live Multicam monitors at a 720p ceiling; we do not need 1080p to *monitor*)
2. 2×2 at 24 fps
3. Single-angle live preview + still-grid for inactive angles
4. Custom MediaCodec + OpenGL ES compositor, bypassing `CompositionPlayer` entirely

**A red result does not kill the product.** It moves the proxy resolution and rewrites the tech spec's core — which is precisely why we do it in week one rather than month six.

### Results

| Device | Model / SoC | HW level | 10-bit | Timestamp source | Max instances (H.264) | 2×2 1080p30 result |
|---|---|---|---|---|---|---|
| Phone 1 | | | | | | |
| Phone 2 | | | | | | |
| Tablet | | | | | | |

**Go / no-go:** _pending_
**Fallback selected (if red):** _n/a_

---

## What happens next

1. **You:** install Part 1, prep the devices in Part 2. (~1 hour of attention, mostly waiting on downloads.)
2. **Me:** the spike app is being scaffolded now, against verified Media3 API signatures rather than remembered ones.
3. **Together:** run it on all three devices, fill in the table above, make the call.
4. **Then:** the technical spec hardens against measured reality instead of hope.

The product, design, and technical specs are being drafted in parallel by a multi-agent research and review pass while you install — so the install time isn't idle. The technical spec's decoder-budget section is the one part deliberately left open until this table has numbers in it.
