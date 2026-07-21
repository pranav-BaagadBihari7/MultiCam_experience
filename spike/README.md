# The Decoder Spike

**One question:** can Media3's `CompositionPlayer` preview **4 concurrent 1080p H.264 clips in a 2×2 grid at a sustained 30 fps** on your tablet?

This is the load-bearing bet of the whole product. Everything else waits on the number this produces.

---

## Why you create the project in Android Studio instead of me writing it

I'm giving you source files, not a whole project. Android Studio's wizard generates the Gradle wrapper, the Android Gradle Plugin version, the Kotlin version, and the `compileSdk` — all of which shift over time and all of which break in confusing ways if hand-written wrong. Let the wizard do the boilerplate; we supply the parts that matter.

---

## Step 1 — Create the project

**Android Studio → New Project → Empty Activity** (the Compose one, not "Empty Views Activity").

| Field | Value |
|---|---|
| Name | `DecoderSpike` |
| Package name | `com.spike.decoder` |
| Language | Kotlin |
| Minimum SDK | **API 33** |
| Build configuration language | Kotlin DSL |

Let it finish and sync. If it asks to upgrade anything, accept.

## Step 2 — Add the dependencies

Open **`app/build.gradle.kts`**. Find the `dependencies { }` block and paste these inside it:

```kotlin
// Media3 — pinned to 1.10.1. CompositionPlayer is experimental and its API
// changes between minor versions, so do not float this.
implementation("androidx.media3:media3-transformer:1.10.1")  // CompositionPlayer, Composition, EditedMediaItem
implementation("androidx.media3:media3-effect:1.10.1")       // MultipleInputVideoGraph, StaticOverlaySettings
implementation("androidx.media3:media3-common:1.10.1")       // VideoCompositorSettings, C, MediaItem
implementation("androidx.media3:media3-exoplayer:1.10.1")    // AnalyticsListener lives here
implementation("androidx.media3:media3-ui:1.10.1")           // PlayerView
implementation("androidx.annotation:annotation:1.9.1")       // androidx.annotation.OptIn
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
```

In the same file, inside `android { }`, confirm `minSdk = 33`.

Sync when prompted (elephant icon, top right).

## Step 3 — Add the source files

Copy the four `.kt` files from this folder into
`app/src/main/java/com/spike/decoder/`, replacing `MainActivity.kt`.

## Step 4 — Get four test clips onto the device

Record a **1080p30** clip with the stock camera app, ~30 seconds. One clip is enough — we'll use it four times, which still forces four independent decoder instances, which is what we're measuring.

Plug in the tablet, then:

```bash
adb devices                       # confirm it's listed
adb shell mkdir -p /sdcard/Android/data/com.spike.decoder/files
adb push clip.mp4 /sdcard/Android/data/com.spike.decoder/files/clip1.mp4
adb push clip.mp4 /sdcard/Android/data/com.spike.decoder/files/clip2.mp4
adb push clip.mp4 /sdcard/Android/data/com.spike.decoder/files/clip3.mp4
adb push clip.mp4 /sdcard/Android/data/com.spike.decoder/files/clip4.mp4
```

That path is the app's own external directory, so it needs **no runtime permissions** — one less thing to debug. (Run the app once first so the directory exists.)

## Step 5 — Run it

Press ▶. The app shows a 2×2 grid, plays for 60 seconds, and reports live:

- **Dropped frames** — the number that decides this
- **Frames dropped per second** over the run
- **Thermal status** — a green result that only holds for 40 seconds is a red result
- **Device capabilities** — the four values from the setup doc

Tap **Copy Results** and paste them back to me.

Run on **all three devices**, tablet first.

---

## Reading the result

**Green:** sustained 30 fps, no meaningful drops, thermal stays `NONE` or `LIGHT` for the full 60 s.

**Red:** sustained drops, or thermal climbs to `MODERATE`+ and drops follow.

A red result **does not kill the product.** It moves the proxy resolution and rewrites one section of the tech spec. That's the entire reason we're doing this in week one instead of month six. The fallback ladder, in order:

1. **540p proxies** — Apple's own Live Multicam monitors at a 720p ceiling, so 1080p monitoring was never actually required
2. 2×2 at 24 fps
3. Single-angle live preview, still-grid for the inactive angles
4. Custom MediaCodec + OpenGL ES compositor, bypassing `CompositionPlayer`

---

## Known bugs this spike deliberately avoids

Verified open issues in `androidx/media`. They shaped the code — worth knowing so you don't "fix" them back in:

- **[#2439](https://github.com/androidx/media/issues/2439)** — seeking crashes in 2×2 grid and PiP mode with `ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED`. Open since May 2025, last touched June 2026, 28 comments. **So: no seek bar.** Play from zero, straight through.
- **[#2742](https://github.com/androidx/media/issues/2742)** — `DefaultVideoCompositor` deadlock freezes multi-sequence compositions during media-item transitions, on all devices. **So: exactly one media item per sequence.** Four files in a 2×2 gives us that naturally.
- **[#2854](https://github.com/androidx/media/issues/2854)** — freezes on clips with no audio track when audio is declared. **So: sequences declare `TRACK_TYPE_VIDEO` only.** This also isolates what we're measuring — video decode, not audio mixing.
- **[b/451741691](https://github.com/androidx/media/blob/1.10.1/libraries/transformer/src/main/java/androidx/media3/transformer/CompositionPlayer.java)** — an in-source TODO: dropped frames from all four sequences are **aggregated into one counter**. We cannot attribute a drop to a specific angle. Fine for a go/no-go; a real limitation later.

Google's own framing in the release notes: CompositionPlayer "is available for experimentation, but is still under development… there are known issues and limitations with some use-cases (**some undocumented**)."

That parenthetical is why we measure instead of trust.
