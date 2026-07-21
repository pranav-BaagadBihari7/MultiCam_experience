# Technical Spec — Multicam Capture & Cut for Android

> **Working title only.** "Final Cut" is an Apple trademark and cannot ship (see product spec §10).

**Document status:** V1 buildable architecture. This is the executable artifact. Where the product spec argues *why*, this spec states *what to type* — module boundaries, exact Media3 1.10.1 signatures, state machines, budgets, a week-by-week build sequence, and the fallback branches taken when a spike returns red. Every API surface below is cited at the `androidx/media` **1.10.1** tag and matches the written-but-unrun spike in `spike/`.

**Audience:** Claude Code as implementer, and the solo builder reviewing what it wrote.

**The load-bearing honesty:** the week-1 spikes are **written and not yet run**. No decoder numbers exist, no CompositionPlayer seek is verified, no concurrent-*encoder* count is measured, and **no two-sequence Transformer *export* has been run**. This spec treats the 4-up grid, single-sequence CompositionPlayer preview, the **camera stream-combination** (the HLG10-master + 10-bit-sampleable-texture + preview surface set — the hardest feasibility question, §7.2), the capture-side concurrent-encoder *count*, and the **two-sequence heterogeneous-source export** all as **measured-pending**, each with a real, non-cascading fallback branch (§8.1, §9, §7.2), not a footnote. Do not read any of them as known to work.

**The round-3 structural change — sync is inverted (objection: scalability).** A prior draft put the **raw-Camera2 live exposure-phase-aligner** — the single hardest, highest-attrition Android subsystem in the plan — in the *uncuttable* weeks-2/3 core. This draft **makes audio cross-correlation (§4.5) the DEFAULT V1 sync** (it already clears G2 on the assembled timeline: <1 ms co-located, ≤ ~10 ms worst-case SEPARATED-rig — both inside the ≤1-frame/33 ms gate, §4.5) and **demotes the live phase-lock port to a week-1 go/no-go spike plus a weeks-7/8 slack upside.** The highest-attrition code is now off the protected path entirely, and no demoable gate weakens, because **G2 is measured on the assembled timeline, which audio-xcorr satisfies.** §4 leads with the default; §4.1–4.4 are the upside.

**The 2026-07-18 scope-lock (integrated throughout; changelog at end of document):** capture stack inverted to **CameraX-first** (§7.1), vertical 9:16 export **#15** (§8.3), SRT caption sidecar **#16** (§11.4), QR-primed pairing (§5.4), storage + audio-source spec (§7.3–7.4), highlight extraction named **V1.5** (§10.5). Companion: product Addendum A, design Addendum B.

**Verified-fact discipline:** the corrections block overrides all remembered API knowledge. The three signatures most easily gotten wrong, stated once here and reused throughout:

```kotlin
// 1. Track types via the Builder CONSTRUCTOR, not builder methods. The (EditedMediaItem...) ctor
//    is DEPRECATED (b/445884217) and silently sets TRACK_TYPE_NONE.
EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))          // media3-transformer
    .addItem(EditedMediaItem.Builder(MediaItem.fromUri(uri)).build())
    .build()

// 2. MANDATORY only for a MULTI-VIDEO-INPUT composite. Omit it -> silent single-input graph.
//    A one-video-sequence + one-audio-sequence Composition (§8.2) is single-video-input and does
//    NOT need this and must NOT set it (setting it invites the #2439/#2742 multi-input graph).
CompositionPlayer.Builder(context)
    .setVideoGraphFactory(MultipleInputVideoGraph.Factory())        // media3-effect — V2 grid export ONLY
    .build()

// 3. VideoCompositorSettings lives in .common (NOT .effect). StaticOverlaySettings impl is in .effect.
Composition.Builder(sequences)
    .setVideoCompositorSettings(gridSettings)                        // V2 grid export ONLY
    .build()

// Opt-in: import androidx.annotation.OptIn — NOT kotlin.OptIn. Enforced by LINT (UnsafeOptInUsageError),
// not the Kotlin compiler: a miss compiles debug and fails the release build.
@OptIn(UnstableApi::class, ExperimentalApi::class)
```

---

## 1 — Module & package architecture

### 1.1 One APK, three roles, four layers

The phone-capture app and the tablet-controller are **the same APK**. There is no separate build. A device's *role* (`CAMERA`, `CONTROLLER`, or `SOLO`) is chosen at runtime on the session screen and can change per session — a tablet can be a camera, a phone can control (matching Blackmagic's "tablets as camera or controller"). This is forced by the solo-maintenance constraint: two APKs means two build configs, two release trains, and duplicated transport code. One APK with a role switch is the only tractable shape.

Role selection gates which Gradle *feature modules* are loaded, but all ship in one `:app`:

```
:app                      // Compose UI host, role router, DI wiring, single Application
├── :core:transport       // NSD discovery, TCP control channel, UDP time-sync — role-agnostic
├── :core:sync            // SNTP clock agreement + audio-xcorr aligner (DEFAULT §4.5);
│                         //   OPTIONAL raw-Camera2 phase-aligner port (upside §4.1-4.4)
├── :core:model           // Composition decision-list types, session state, DTOs — pure Kotlin, no Android
├── :capture              // CameraX-first capture (1.5 + Camera2Interop, Feature Groups; raw-Camera2
│                         //   fallback per §7.1), HLG10 encode, local record, viewfinder + one SDR
│                         //   proxy encoder (CAMERA role). Depends on :core:sync.
├── :controller           // monitor grid, timeline, roll/stop orchestration, WebRTC receiver (CONTROLLER role)
├── :editor              // Media3 preview + Transformer export pipeline (§7, §8)
├── :compositor           // fallback MediaCodec+GL surface compositor (§8.4) — V2 / OUT OF SCOPE for the demo (§16)
├── :framing             // MediaPipe Face Detector + deterministic framing/reframe pipeline (§11) — PAID floor #12
├── :transcript          // whisper.cpp tiny/base + transcript→cut logic (§11.4) — PAID, V1, SPIKE-INDEPENDENT
└── :director            // AI Director: VAD, FSM, Composition emission (§10) — PAID, gated on SPIKE-AUDIO
```

**The interfaces between these are the maintainability story (objection: scalability).** Each module exposes exactly one Kotlin `interface` to `:app`; nothing reaches across a sibling boundary. Concretely:

| Module | Single public interface | Depends on | Never touches |
|---|---|---|---|
| `:core:transport` | `Transport { discover(); openControl(); sendCmd(); timeSyncRtt() }` | `:core:model` | Camera2, Media3 |
| `:core:sync` | `SyncEngine { clockOffsetNanos(): Long; resolveTimelineOffsets(angles): Map<Int,Offset>; state(): SyncState }` | `:core:transport`, Camera2 | Media3, UI |
| `:capture` | `CaptureController { arm(); roll(); stop(); localMaster(): Uri; localProxy(): Uri }` | `:core:sync`, `:core:transport` | Media3 Transformer |
| `:editor` | `EditPipeline { previewProgram(...); export(Composition): Progress }` | `:core:model`, Media3 | transport, Camera2 |
| `:framing` | `Framing { track(clip): SmoothedTrack; reframe(clip): CropTrack; score(clip): TakeScore }` | `:core:model`, MediaPipe | transport, Media3 |
| `:transcript` | `Transcriber { transcribe(clip): Transcript; proposeCuts(Transcript): List<CutPoint>; exportSrt(transcript, deletions): Uri }` | `:core:model`, whisper.cpp (JNI) | transport, Media3, Camera2 |
| `:director` | `Director { propose(clips, transcript?): Composition }` | `:core:model`, `:framing`, `:transcript` (optional) | everything else |

`:director`, `:framing`, and `:transcript` depending **only on `:core:model`** (plus, for `:director`, `:framing` and optionally `:transcript`) is deliberate: they consume recorded per-angle audio/frames + timestamps and emit either a `Composition` decision list or transcript-anchored cut points. They have no dependency on Camera2, Media3 playback, or the network. That isolation is what lets the SPIKE-AUDIO kill-branch (product spec §5.4) delete `:director` without touching capture, sync, export, `:framing`, or `:transcript` — the AI Director can evaporate and the free product **and both paid floors** (framing #12 and transcript cutting) still build.

**`:transcript` is the paid tier's one genuinely-learned on-device capability, and it is deliberately spike-independent (decision: whisper in V1).** whisper.cpp transcript-driven cutting does *not* depend on the audio-geometry signal SPIKE-AUDIO measures, so it ships whether or not `:director` does. Its point is exactly the corrected AI framing (product §1.1): on an Android phone, on-device transcript editing and AI multicam are *genuinely absent* — DaVinci Resolve's Neural Engine (SmartSwitch, transcript editing, Magic Mask) is Studio-gated and, decisively, **Resolve does not run on Android phones at all** — so `:transcript` lands on ground Blackmagic declines to serve, not ground it already owns. It is not "out-AI-ing Blackmagic"; it is serving a platform Blackmagic refuses. The durable moat remains market-vacancy (product §1.5); `:transcript` is a real, differentiated, learned-AI beat on top of it, not the moat itself.

### 1.2 Why not a shared-nothing microkernel

Rejected. A solo dev with near-zero Android experience cannot maintain plugin infrastructure. The `interface`-per-module rule above gives isolation without a framework. The cost of a wrong call crossing a boundary is a compile error, not a runtime surprise.

---

## 2 — Threading model

Video is a threading problem before it is anything else. The model is fixed here so it is not reinvented per feature.

| Concern | Thread / dispatcher | Rule |
|---|---|---|
| Camera capture callbacks (CameraX executor + `Camera2Interop`) | dedicated executor / `HandlerThread` ("camera-bg") | Never touch UI. CameraX use-case and `Camera2Interop` capture callbacks post here; on the raw-Camera2 fallback path (§7.1) the `CameraCaptureSession` callbacks post to the same thread. In the **default** sync path the capture request is static; the **optional** phase aligner (§4.2), only if built (raw path only), resubmits the repeating request frame-by-frame from this thread. |
| Encoder / MediaCodec (master + proxy) | its own `HandlerThread` per codec | Surface-based (§8), so no ByteBuffer copies cross threads. **Two encoders max during capture** (§7.2). |
| Media3 CompositionPlayer / ExoPlayer | **main thread only** | Media3 players are single-threaded and assert on it. All `player.*` calls on `Dispatchers.Main`. Non-negotiable — cross-thread access is undefined behavior. |
| Transformer export | Media3-owned internal threads | We only call `start()`/`cancel()` from main and receive `Listener` callbacks on main. |
| Transport (TCP/UDP/NSD) | `Dispatchers.IO` coroutines | One supervisor scope per session; cancels atomically on session end. |
| Time-sync UDP loop (SNTP) | single-thread executor, `THREAD_PRIORITY_URGENT_AUDIO` | Elevated priority: jitter here corrupts the clock offset that primes both roll/stop coordination and the audio-xcorr search window (§4.5, §6). |
| Audio-xcorr / VAD / Director / Framing inference | `Dispatchers.Default` (CPU) + GPU delegate for the face detector | Post-record, off the hot path. Bounded parallelism = angle count. |
| GL compositor (fallback, V2) | single GL thread owning the `EGLContext` | All GL calls on it; `SurfaceTexture.updateTexImage()` on the same thread that made the context current. |

**The one cross-cutting rule:** display metrics are read fresh on every relevant callback, never cached (product ground truth: `Display` changes when windows move between displays). Preview `Surface` size, encoder config, and aspect ratio all re-derive from a `WindowSizeClass` recomputed via `WindowManager` on configuration change. A cached metric on a foldable is a corrupted encoder config.

---

## 3 — Build config & dependency pins

```kotlin
// build.gradle.kts (:app and feature modules)
android {
    compileSdk = 36                     // target API 36 by Aug 31 2026 (product spec §9.10)
    defaultConfig {
        minSdk = 33                     // dodges NsdManager/mDNS breakage on Android 12- (build-setup §1)
        targetSdk = 36
        ndk { abiFilters += listOf("arm64-v8a") }  // NDK r28+ mandatory: 16KB page size in force since Nov 1 2025
    }
}

dependencies {
    val media3 = "1.10.1"               // PINNED. CompositionPlayer's API changes between minors; must not float.
    implementation("androidx.media3:media3-transformer:$media3")   // CompositionPlayer, Composition, EditedMediaItem*
    implementation("androidx.media3:media3-effect:$media3")        // MultipleInputVideoGraph (V2 grid export only)
    implementation("androidx.media3:media3-common:$media3")        // C, VideoCompositorSettings, OverlaySettings
    implementation("androidx.media3:media3-exoplayer:$media3")     // ExoPlayer (grid + program preview), AnalyticsListener
    implementation("androidx.media3:media3-ui:$media3")            // PlayerView / SurfaceView attach

    // CAMERA-role capture is CameraX-FIRST (locked 2026-07-18; §7.1). CameraX 1.5 + Camera2Interop
    //   expresses HLG10 (VideoCapture dynamic range + Feature Groups) and FULL-gated manual 3A
    //   (Camera2Interop/Camera2CameraControl), and brings the device-compat quirk database a solo
    //   builder cannot replicate. Raw Camera2 (framework, no dependency) is the FALLBACK, adopted only
    //   if the week-1 CAMERA-STREAM-COMBO spike (§16.2) proves CameraX cannot bind
    //   {HLG10 master + 10-bit-sampleable SurfaceTexture + preview}. The OPTIONAL phase-aligner upside
    //   (§4.1) requires the raw path — that cost sits on the upside, not the default.
    val camerax = "1.5.0"               // PINNED. Camera2Interop + HLG10 dynamic range + Feature Groups.
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")      // Camera2Interop lives here
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-video:$camerax")        // VideoCapture + DynamicRange.HLG_10_BIT

    implementation("androidx.core:core-performance:1.0.0")         // MediaPerformanceClass gate (product spec §3.3)
    implementation("androidx.window:window:1.5.0")                 // WindowSizeClass BREAKPOINTS_V2

    implementation("io.github.webrtc-sdk:android:<pinned>")        // monitoring only; Google ships no artifacts
    implementation("com.google.mediapipe:tasks-vision:<pinned>")   // Face Detector Task (BlazeFace) — GMS-FREE.
                                                                   //   Feeds framing #12 + reframe tracker (§11). V1.
    implementation("com.google.zxing:core:<pinned>")               // QR pairing (§5.4) — pure Java, ~500 KB, GMS-FREE
    implementation("com.google.ai.edge.litert:litert:<TF2.21+>")  // + litert-gpu; V2 only (LR-ASD, seg), GPU delegate
    // whisper.cpp: vendored source, built under NDK (§11.4, §13). NOT a Maven dep. V1 PAID (:transcript), post-record.
    // libsoftwaresync/RecSync: vendored + ported (§4). NOT a dep. OPTIONAL upside, not V1 core.
    // NO ffmpeg-kit. NO NNAPI. NO Vulkan. NO ML Kit (GMS — same disqualifier as Nearby Connections).
}
```

**MediaPipe reconciliation (objection: `NO MediaPipe` vs the framing/reframe face-bbox dependency).** The prior draft's blanket "NO MediaPipe" was **wrong and is corrected here.** The correct, narrower fact is: **MediaPipe has no active-speaker-detection (ASD) task** — its Face *Landmarker* is a mouth-open heuristic that fires on chewing (product spec §5.2), so it is not a substitute for real ASD, which is the V2 LR-ASD port. But MediaPipe **Tasks-Vision Face *Detector*** (BlazeFace) is a real, shipping, **GMS-free** face *detector*, and it is exactly the face-bbox source that feature #12 (smart-reframe + take-quality review, product spec §5.6) requires. So: **no MediaPipe ASD/Landmarker; yes MediaPipe Face Detector.** It is chosen over ML Kit Face Detection specifically because ML Kit is a **GMS dependency** (same disqualifier that ruled out Nearby Connections in transport), whereas MediaPipe Tasks bundles the model in-APK and runs GMS-free. §11 specifies **which BlazeFace variant on which geometry** (full-range for the wide-shot reframe path, short-range for close take-review), and the reframe tracker's temporal pipeline.

**Opt-in placement (objection: opt-in annotation placement):** `@OptIn(UnstableApi::class, ExperimentalApi::class)` is applied at **class scope** on every class that constructs or holds a `CompositionPlayer`, `Composition`, `MultipleInputVideoGraph`, or `VideoCompositorSettings` — i.e. `Media3EditPipeline` in `:editor`, plus `:compositor`. Both markers use `androidx.annotation.RequiresOptIn` at `Level.ERROR`. The import must be `androidx.annotation.OptIn`. A missing opt-in **passes `compileDebugKotlin` and fails the release build** via LINT `UnsafeOptInUsageError`, so CI must run `lintRelease`, not just assemble debug. `ExperimentalApi` and `UnstableApi` both live in `androidx.media3.common.util`.

---

## 4 — The sync engine (default = audio cross-correlation; live phase-lock = upside)

**This is the differentiator the positioning rests on (product spec §1.2): hardware-free multicam sync. The round-3 correction is which path delivers it in V1.**

### 4.0 The two-tier sync architecture, and the capture-path correction

**Default (V1 core, built week 2): SNTP clock agreement + post-record audio cross-correlation (§4.5).** This clears gate G2 (≤1 frame, clapper-verified at take start and minute 20) at **<1 ms on co-located FLAT rigs and ≤ ~10 ms worst-case on the SEPARATED rig** (the acoustic-propagation term, §4.5 — both inside the 33 ms gate), is buildable by a novice, is honestly Apple-parity, and — because it re-derives offsets *from the recorded audio* rather than from live sensor-timing — is **robust to controller loss and clock-resync misses by construction** (§5.1). It is the guaranteed baseline that anchors G2 on the assembled timeline.

**Upside (week-1 go/no-go spike, then weeks-7/8 slack ONLY): live exposure-phase alignment (§4.1–4.4).** Ported from RecSync, this drives per-frame `SENSOR_FRAME_DURATION` to land shutters on a shared phase grid at ~250 µs *live*. It is the single hardest, least-tractable subsystem for a near-zero-Android builder (archived Pixel-2/3/4-era code, SoC-specific sensor timing), so **it never enters the protected weeks-2/3 core.** A hard week-1 go/no-go (V0–V2 clapper by end of week 1, §4.4) decides whether it is even attempted; if attempted, it lands only in slack, and if it fails at any point the default already carries every gate. **Why this ordering (objection: the hardest thing was in the protected core):** port failure is rarely a clean week-1 signal, and diagnosing why phase-lock won't hold on a specific SoC can eat weeks — so the port is fenced behind a dated go/no-go and can *never* bleed into the capture build. The differentiator (hardware-free, Blackmagic-beating, Apple-matching) is delivered by the default; live phase-lock only sharpens it from Apple-parity toward Apple-beating.

**The capture-path status (2026-07-18 inversion — this supersedes the earlier raw-Camera2-first correction).** Round 2 correctly established that the live phase aligner is architecturally impossible on CameraX: driving the *next* frame's `SENSOR_FRAME_DURATION` needs a raw repeating request we own. While the phase aligner sat in the protected core, that made raw Camera2 the default. Round 3 then demoted the phase aligner to an optional, go/no-go-gated upside behind the audio-xcorr default — which removed raw Camera2's load-bearing justification. **V1 capture is therefore CameraX-first (1.5 + `Camera2Interop`, §7.1):** HLG10 and FULL-gated manual 3A are expressible on CameraX 1.5, and its device-compat quirk database absorbs exactly the cross-device capture debugging a near-zero-Android solo builder cannot. Two consequences:

- **Manual 3A on CameraX is LIVE-first, not rebind-first (round-5 correction — the prior "re-arm cycle" claim was stronger than the platform fact).** `Camera2Interop`'s build-time `Extender` options are indeed pre-bind, but **`Camera2CameraControl.setCaptureRequestOptions()` applies capture-request options to the LIVE repeating request without rebinding** — with `CONTROL_AE_MODE_OFF`, `SENSOR_SENSITIVITY` / `SENSOR_EXPOSURE_TIME` are adjustable mid-take on `FULL` devices. So mid-take ISO/shutter changes are **attempted live by default**; the re-arm micro-interaction (design 02 §4.3) is the **FALLBACK state, shown only if the owned device demonstrably drops or ignores live updates** — a one-line check in the §16.2 hardware audit measures exactly this on both owned phones, and design 02 Appendix A row 4's DIVERGE is conditioned on that measured result, not asserted as a platform requirement.
- **The phase-lock upside, if ever attempted, requires switching `:capture` to the raw-Camera2 fallback path (§7.1)** — that porting cost now correctly sits on the upside, not the default. Raw Camera2 otherwise enters the build only if the week-1 stream-combo spike (§16.2) proves CameraX cannot bind the §7.2 surface set.

**In the default sync path the capture request is STATIC on either stack** (fixed frame duration, standard CFR recording) — no per-frame loop; the phase-aligner code is simply absent from V1 core.

### 4.1 Source of truth (phase-lock UPSIDE only)

| Concern | Repo / module | License | Role |
|---|---|---|---|
| **Primary** | `MobileRoboticsSkoltech/RecSync-android` | Apache-2.0 | Source of truth for the *upside*. Productized libsoftwaresync to sub-millisecond. Port its raw `CameraCaptureSession` loop, `SoftwareSyncBase`, leader/client SNTP, and `PhaseAligner` — **only if §4.4 is green and slack exists.** |
| Reference | `google-research/libsoftwaresync` (ICCP 2019, arXiv 1812.09366) | Apache-2.0 | Algorithm of record for exposure-phase alignment. |
| Fallback reference | `prime-slam/OpenCamera-Sensors` | — | Raw-Camera2 sensor-timestamp plumbing on non-Pixel hardware. |

**Named risk (unchanged, but now off the core path):** both primary sources are **archived** (libsoftwaresync Apr 2026, RecSync Sept 2021), Pixel 2/3/4-era, and assume a controllable exposure phase and a `REALTIME`-timebase `SENSOR_TIMESTAMP`. **The owned hardware is not Pixel 2/3/4.** Because this is now upside-only, that risk costs the demo nothing: §4.5 is the default, not a fallback taken under duress.

### 4.2 The exact Camera2 hooks (phase-lock UPSIDE only)

- Frame timestamps arrive on `CaptureCallback.onCaptureCompleted()` as `CaptureResult.SENSOR_TIMESTAMP`. Convert to leader time: `t_leader = SENSOR_TIMESTAMP + clockOffsetNanos`.
- Phase = `t_leader mod frame_period` (33_333_333 ns @30fps). Adjust the next frame's `SENSOR_FRAME_DURATION` (with `SENSOR_EXPOSURE_TIME` bounded below it) on the next `CaptureRequest.Builder`, then `setRepeatingRequest(...)`. This is RecSync's `PhaseAligner` on the session we own.
- **REALTIME gate:** phase-lock requires `SENSOR_INFO_TIMESTAMP_SOURCE == REALTIME` for cross-device `SENSOR_TIMESTAMP` comparability. **This gate binds only the upside** — the default audio-xcorr path needs no cross-device timestamp comparability at all, which is why the CAMERA role is no longer refused on `UNKNOWN` (§4.4, §7.1).

### 4.3 SyncEngine state

**Default path (audio-xcorr):**
```
Unsynced --SNTP clock converged (offset std-dev < 200 µs)--> ClockAgreed
ClockAgreed --stays here through the whole take--> ClockAgreed   // roll/stop coordination only; no live phase loop
[post-STOP] resolveTimelineOffsets() runs audio-xcorr --> offsets known, timeline frame-locked (§4.5, §6.4)
```
`ClockAgreed` is all the live path needs: SNTP gives sub-ms clock agreement for coordinated roll/stop (G1) and primes the audio-xcorr search window (§4.5). Frame-accurate offset is resolved after STOP.

**Upside path (phase-lock), if built:**
```
Unsynced --REALTIME ok + SNTP converged--> ClockLocked
ClockLocked --phase error < 1 frame for 3 consecutive checks--> PhaseLocked
PhaseLocked --drift check every ~10 min--> [re-run SNTP] --> PhaseLocked
PhaseLocked --phase error > 1 frame OR offset variance spike--> ClockLocked (re-align, never drop recording)
Unsynced --REALTIME == false--> [upside unavailable; default audio-xcorr used]
```

### 4.4 Week-1 go/no-go for the phase-lock upside (does NOT gate the demo)

Run in week 1 (§16.2) alongside the other spikes, **on a throwaway raw-Camera2 harness** — the upside's required stack (§7.1); the default CameraX build is untouched by the result. **A red result here changes nothing downstream** — the default (§4.5) is already the build target. Green **and** slack surviving the whisper scope = attempt the port in the weeks-6.5–8 tail (§16.3); whisper is ahead of it in the slack queue.

| Step | Method | Pass condition |
|---|---|---|
| U0 | `DeviceCapabilities.collect()` | `TIMESTAMP_SOURCE==REALTIME` on both phones. If UNKNOWN → upside unavailable; **default unaffected.** |
| U1 | Raw session opens `{master, preview, proxy}`; SNTP-only offset stability | offset std-dev < 200 µs over 60 s (this leg is *also* the default path's clock-agreement validation) |
| U2 | `PhaseAligner` on own repeating request, two phones, **clapper** | visual clap ≤1 frame across both angles at take start — **END-OF-WEEK-1 GO/NO-GO.** No green here → port is not attempted; slack is spent elsewhere. |
| U3 | Phase hold at minute 20 | still ≤1 frame after 20 min (only if U2 green) |
| U4 | Cross-SoC | phase grid holds *between* two different SoCs (only if U2 green) |

U1's SNTP-stability result is reused by the default path (it needs the same clock agreement for roll/stop and the xcorr prior). So week 1 is not wasted even on a red U2.

### 4.5 The default sync path: audio cross-correlation (concrete accuracy + latency budget)

Every phone records its own audio locally at 48 kHz. Sync is resolved from that audio, primed by the live SNTP clock.

**Fast windowed GCC-PHAT — resolves offsets inside the STOP→timeline budget (objection: latency — a full-take xcorr blows the <2 s hero gate).** The prior draft budgeted xcorr at "<0.1× realtime of the *whole take*" (minutes for a 40-min take), which would either show an unsynced timeline in <2 s or blow the gate. Corrected: the offset is resolved from a **narrow windowed correlation primed by the SNTP prior**, not a blind full-take search:

- At STOP, correlate **only the first ~10 s** of each angle's audio against the leader's first ~10 s, **searching a narrow ±window around the SNTP-predicted offset** (SNTP already agrees to sub-ms; xcorr only resolves the residual + exposure phase). FFT-based GCC-PHAT over a 10 s / 48 kHz window is a handful of FFTs. **Budget: ~150–500 ms for 3 angles, parallelized** — a real leg in the §6.4 STOP→timeline table, comfortably inside <2 s.
- **Accuracy — with the acoustic-propagation error term stated, because the old sub-ms headline ignored physics (round-5 correction, objection: latency).** One-sample resolution at 48 kHz is 20.8 µs, and robust GCC-PHAT on real room audio resolves the *arrival-time* alignment to <1 ms. But GCC-PHAT aligns acoustic **arrival** times, not capture clocks: sound travels ~343 m/s (~2.9 ms per metre), so when the mics are metres apart the path-length asymmetry to the dominant speaker biases the offset by `|d_leader − d_angle| / 343 m/s` — and the bias **flips sign as the dominant speaker alternates**. On the prescribed SEPARATED rig (one phone per speaker, ~1.5 m off-axis; asymmetry to the dominant source realistically ≤ ~2–3 m) that contributes **~6–9 ms worst case**. The honest accuracy claim is therefore: **sync error ≤ ~10 ms worst-case on the SEPARATED rig; <1 ms on co-located FLAT rigs — both comfortably inside the G2 ≤1-frame (33 ms) gate**, which still clears with ≥3× margin in the worst case. The sub-ms figure applies only where the geometry earns it (co-located mics); a bench measurement on the SEPARATED rig will show the ~ms-scale speaker-dependent bias, and this spec predicts it rather than being contradicted by it. **This is the authoritative accuracy statement — product §5.1 #1 and design §3.2's "±0.3 frame" copy must quote it, not the old sub-ms headline.**
- **Drift anchors are speaker-stabilized.** Because the propagation bias flips with the dominant speaker, the piecewise drift anchors (below) would inherit a speaker-dependent wobble if each ~10 s window happened to catch a different dominant speaker. Mitigation, specified: anchor windows **prefer stretches where the SAME speaker dominates** (the VAD stream already labels this for free), and where no such window exists near the target cadence, the anchor **averages the offset across adjacent speaker turns** — stabilizing the piecewise fit against the flip. The *relative drift* between anchors is what the fit consumes, so a consistent per-speaker bias cancels; only speaker *alternation between anchors* wobbles it, and this rule removes exactly that.
- **Long-take drift — and why a two-point linear fit is unsafe under thermal stress (objection: latency).** Free-running crystals drift ~1.2 ms/min relative, so a start-only offset could reach ~1.4 frames by minute 40. A **background refinement** (starting the instant the timeline is present, off the hot path) correlates additional ~10 s windows and fits a per-angle offset+rate resample ratio. **But a two-point (early + late) linear fit is not safe:** thermal throttling shifts each SoC's oscillator frequency **nonlinearly mid-take** — a hot middle can drift at a different rate than the cool endpoints — so *both* endpoints can pass ≤1 frame while the throttled middle desyncs past a frame. So the refinement is a **piecewise fit on a bounded re-correlation cadence, not a global 2-point line:** correlate a ~10 s window **every ~10 min of take** (plus the early and late windows) and fit a **piecewise-linear (3+ anchor) offset+rate** curve between consecutive anchors. Each ~10 s window is a few FFTs (the windowed leg above), so N anchors over a 40-min take is still sub-second aggregate compute, off the hot path.
- **Residual-drift budget — the documented worst-case number, not a cited profile (objection: latency).** Between two anchors ≤10 min apart, the intra-segment error left by piecewise-linear interpolation is the *curvature* of the drift (its rate-of-change), not its slope. The budget rests on one documented assumption, stated here as a number rather than deferred to a §12 "profile" that only lists status rungs: **the worst-case mid-take rate-of-change of relative oscillator drift is ≤ ~1.5 ppm/min**, occurring on the steepest throttle ramp (SEVERE→CRITICAL). It is tied per thermal rung to the explicit `ċ` curve now documented in §12 (NONE/LIGHT ~0.2 · MODERATE ~0.6 · SEVERE ~1.2 · CRITICAL/EMERGENCY ~1.5 ppm/min). The piecewise-linear midpoint residual is `ċ·T²/8`, where `ċ` is the drift-rate curvature and `T` the inter-anchor span. At the worst rung `ċ = 1.5 ppm/min = 2.5×10⁻⁸ s⁻¹` and `T = 600 s`, so the residual is `2.5×10⁻⁸ × 600² / 8 ≈ 1.1 ms ≈ 1/30 frame` — an order of magnitude inside the G2 33 ms (1-frame) gate, even if the middle of the take throttles hardest. **Falsifiable week-1 coupling (an explicit pass/fail, not a footnote):** the §16.1 thermal + audio-xcorr spike measures the *actual* mid-take drift-rate curvature per rung on the owned phones; if any rung exceeds its assumed `ċ`, the re-correlation cadence tightens from ~10 min to **~5 min**, which *quarters* the residual (`T²` scaling: ~0.3 ms) and restores margin. The drift budget and the §12 thermal curve are therefore one coupled week-1 measurement, not two independent hopes. G2's minute-20 clapper is measured on an *anchored* window, never an extrapolated one; the timeline is scrubbable at frame accuracy for the early portion immediately and for the whole take within seconds — never a blocking wait.

**Positioning:** sync is **auto-sync-grade at edit time**, exactly where Apple's software Live Multicam already sits (their genlock push is the tell). So the default lands at *parity with Apple*, and still **hardware-free vs Blackmagic's Tentacle requirement** — a true Android differentiator. If the §4.1 upside later lands, we sharpen from edit-time xcorr (~1–10 ms, geometry-dependent per the propagation term above) to ~250 µs *live* — clock-domain sync has no acoustic path in it — but the demo never depends on it.

**Compute:** one-time, offline (or on the STOP path for the first window), the same GCC-PHAT primitive the AI Director's auto-sync uses (§10.3).

---

## 5 — Networking state machine (fault tolerance, specified)

**Objection: fault tolerance was asserted as a gate (G4) with zero state-machine spec. Here it is — and round 3 adds the editor-side reconciliation of a rejoined angle (objection: android_differentiation — G4's on-timeline consequence was unspecified).** Transport = infrastructure Wi-Fi + NSD/mDNS discovery + own TCP control channel + UDP time-sync (Wi-Fi Aware/Direct/Nearby all ruled out). Two machines: per-device, and per-session on the controller.

### 5.1 Device (camera) state machine

States: `Idle · Discovering · Connected · Armed · Recording · ControllerLost · Rejoining · Stopping · Error`.

| From | Event | To | Concrete action (API) |
|---|---|---|---|
| Idle | user picks CAMERA role | Discovering | `NsdManager.registerService()` (TXT: role, model, REALTIME flag, FULL flag) |
| Discovering | controller resolves us, opens TCP | Connected | accept on `ServerSocket`; start SNTP responder/client on UDP |
| Discovering | **no controller in 30 s** | Discovering (retry) | re-`registerService()`; "waiting for controller" UI; never error out |
| Connected | `ARM` cmd on TCP | Armed | bind the capture session `{master, preview, proxy}` (CameraX use-case group, §7.1–7.2), start HLG10 pre-roll + SDR proxy encoder; SyncEngine → ClockAgreed |
| Armed | `ROLL` (carries leader-time start-at) | Recording | `MediaCodec.start()` (master + proxy) + `MediaMuxer` local files; first frame scheduled to `startAtLeaderTime`; **record `recStartLeaderUs`** |
| Recording | **TCP control drop** (read timeout / RST) | ControllerLost | **KEEP RECORDING.** Buffer status locally. Start reconnect backoff. This is G4. |
| Recording | `STOP` cmd | Stopping | finalize both muxers, flush; report `recStartLeaderUs`, `recEndLeaderUs`, duration, hash |
| Recording | **storage < 500 MB** | Recording (warn) | emit `LOW_STORAGE`; drop proxy to 720p→540p rung if <200 MB; stop only at <50 MB with a finalized valid file |
| Recording | **SNTP re-sync fails at ~10 min** | Recording | hold last-good clock offset; mark angle `SYNC_DEGRADED`; keep recording. **In the default path this is cosmetic** — the frame-accurate offset is re-derived from audio post-record regardless (§5.1a). |
| ControllerLost | reconnect succeeds | Rejoining → Recording | replay buffered status; re-run 8-probe SNTP; **record a `rejoinLeaderUs` marker** |
| ControllerLost | reconnect fails, backoff exhausted (still recording) | Recording | **stay recording until local STOP or storage floor.** "Roll until told to stop, not while told to continue." |
| Rejoining | **second controller (split-brain)** | Rejoining | accept only the controller whose `sessionId` matches the in-flight session; reject others `SESSION_BUSY`. First writer of `sessionId` at ARM wins. |
| any | `CameraDevice.StateCallback.onError` / codec error | Error | finalize whatever files exist (never lose footage); surface error; return to Connected if link alive |
| Error/Stopping | done | Connected | ready for next take |

Backoff: 250 ms → 500 → 1 s → 2 s → 5 s, capped, jittered. TCP control uses `SO_KEEPALIVE` + a 3 s application-level heartbeat so silent link death is detected within ~3 s.

### 5.1a Reconciling a controller-loss / rejoin angle into the timeline (objection: android_differentiation)

**G4's whole point is that the pulled camera's footage lands *correctly on the synced timeline*, not merely "file intact." Here is how.** A camera that loses the controller keeps recording locally, but three things can differ from the other angles: (i) its live clock re-sync missed cycles (`SYNC_DEGRADED`), (ii) it may have started or stopped at a different leader-time, and (iii) if it was pulled and returned it has full local footage but its clock offset during the gap is uncertain. The default audio-xcorr sync path makes this tractable:

- **Offset is re-derived, not trusted.** `resolveTimelineOffsets()` (§4.5) aligns every angle — including a `SYNC_DEGRADED` one — from its *recorded audio* against the leader, post-record. A missed live re-sync cycle is irrelevant to a post-record acoustic alignment: the audio was recorded continuously and correlates regardless of what the live clock did. **This is a structural benefit of the audio-xcorr default:** controller-loss robustness on the *timeline* is free, because alignment does not depend on live clock continuity. (In the phase-lock upside path, a `SYNC_DEGRADED` angle falls back to audio-xcorr for its offset only.)
- **Coverage is explicit in the model.** Each angle carries `coverage: [availableStartLeaderUs, availableEndLeaderUs]` derived from its reported `recStartLeaderUs`/`recEndLeaderUs` mapped through its resolved offset. An angle that started late, stopped early, or (rare) had a genuine local recording gap has a coverage interval that does not span the full take.
- **The AngleCut list respects coverage (ties to §8.2).** `assembleProgram()` and the director's candidate set at any window `t` **exclude angles whose coverage does not contain `t`.** A wide/leader angle designated `alwaysCovered` fills any instant no other angle covers, so the program is never empty. A cut is never emitted onto an angle at a time it wasn't recording.
- **`SYNC_DEGRADED` is surfaced, not hidden.** The manifest flags the angle; the editor (design 02 §6) badges its ribbon tile and shows the derived confidence from xcorr (a low correlation peak → a visible "sync uncertain" mark). The operator sees which angle was pulled and that it was nonetheless placed on the timeline. The G4 demo (Beat 4) ends by showing the pulled camera's footage **correctly positioned and scrubbable** on the synced timeline.

### 5.2 Session (controller) state machine

`NoSession · Discovering · Assembling · Armed · Rolling · Degraded · Stopping · Review`.

| From | Event | To | Action |
|---|---|---|---|
| Discovering | ≥1 eligible camera resolved | Assembling | show grid; probe each camera's FULL/REALTIME flags (for manual-control + upside availability, **not** camera eligibility) |
| Assembling | user taps ARM | Armed | broadcast `ARM`; wait for all-armed acks; run SNTP convergence; ROLL allowed once all `ClockAgreed` |
| Armed | user taps ROLL | Rolling | broadcast `ROLL(startAtLeaderTime = now + 200 ms)` — the 200 ms lets the slowest camera pre-roll; **the <500 ms G1 spread budget, itemized in §6** |
| Rolling | a camera drops off NSD | Degraded | mark that tile "recording locally, link lost"; **do not stop others**; keep the take alive |
| Rolling | dropped camera reappears | Rolling | re-resolve, reattach monitor, resume status, note `rejoinLeaderUs` |
| Rolling | user taps STOP | Stopping | broadcast `STOP`; collect finalized-file acks with `recStart/EndLeaderUs`+duration+hash |
| Stopping | all acks in (or timeout) | Review | files stay local; **run fast windowed GCC-PHAT (§4.5) to resolve offsets**, then build the timeline (STOP→timeline budget §6.4) |

**Files never cross the network during or after record over the internet** (product thesis; WebRTC touches monitoring only; proxies are a device-to-device LAN copy after STOP, design 02 §3.3). Review assembles a `Composition` from *local* files the editor opens per-device.

### 5.3 The `ACCESS_LOCAL_NETWORK` / NsdManager denial path (risk 9.8)

Android 17 makes local-network access a runtime permission at targetSdk 37+. Not demo-blocking at targetSdk 36, but specified now:

- `NsdManager.registerService()` / `discoverServices()`, TCP `connect()`/`accept()`, and UDP send/recv **all** fail (timeout / `EPERM`) without the grant.
- On entering `Discovering`, check the permission. If not granted, request it with a rationale ("This app finds your other phones over your Wi-Fi. It never uses the internet."). On **denial**: transport enters terminal `NetworkDenied` → app offers **SOLO role only** (single-device capture + single-angle edit, the free tier's terminal-but-honest surface). Say so plainly rather than failing silently by timeout.
- Also declare `usesPermissionFlags="neverForLocation"` on `NEARBY_WIFI_DEVICES`.

### 5.4 QR-primed pairing — two explicit modes (round-5 rewrite; design 02 §5.7/§B.3 owns the flow's UX)

**Round-5 correction (objection: tech_stack, both reviewers — the prior draft was unbuildable as written).** The old text (a) carried a payload `{ssid, sessionToken}` that could neither join a secured AP (no PSK) nor TCP-connect (no address), (b) conflated two different Android APIs by writing "`WifiNetworkSpecifier` suggestion flow" — `WifiNetworkSpecifier` (peer-to-peer via `ConnectivityManager.requestNetwork`, per-connection **system approval dialog**, app-scoped `Network` requiring explicit socket binding) and `WifiNetworkSuggestion` (background hint with **no guaranteed or timely join**) are distinct mechanisms with different UX and transport consequences — and (c) choreographed no approval dialog anywhere. Corrected into two explicit modes. The word "suggestion" is deleted; `WifiNetworkSuggestion` is **not used** (its non-deterministic join latency is unacceptable on a demo-visible pairing beat).

**Who hosts the network: the session's infrastructure AP (the §5 transport premise — a venue/home Wi-Fi both devices can reach).** A tablet-hosted `LocalOnlyHotspot` is the named no-AP contingency (the tablet's LOHO callback yields a generated SSID/PSK and the tablet's interface address, which simply populate the MODE-2 payload below); it is a contingency, not the default, because LOHO forces every phone through the MODE-2 join path.

- **MODE-1 (default — both devices already on the same AP; the normal rig case and the demo path).** The QR carries **`{controllerIp, port, sessionToken}` only. No Wi-Fi join API is invoked at all.** The CONTROLLER renders the QR (ZXing core — pure Java, ~500 KB, **GMS-free**, consistent with the no-GMS discipline that ruled out ML Kit and Nearby Connections) encoding its current IP on the session interface + listening port + a random per-session token; a CAMERA-role phone scans it via a CameraX `ImageAnalysis` frame stream + ZXing decode and opens a **straight token-primed TCP `HELLO{token}`** to that address — skipping the NSD browse entirely. No system dialog, no join latency, nothing to approve. This is the mode demo beat 2 stages.
- **MODE-2 (the scanning phone is NOT on the session AP).** The QR carries **`{ssid, psk, controllerIp, port, sessionToken}`**. The phone joins via **`WifiNetworkSpecifier.Builder().setSsid(ssid).setWpa2Passphrase(psk)` + `ConnectivityManager.requestNetwork()`** — which surfaces **one system approval dialog on the phone** (stated here and mirrored into design 02 §5.7's choreography and the beat-2 script: *"each phone approves one system prompt — once"*). Two transport consequences, stated because they change the code: (1) a Specifier-joined network is **app-scoped and NOT the device's default network**, so **every session socket (TCP control, UDP time-sync, WebRTC) must be created via the granted `Network` object** (`network.socketFactory` / `network.bindSocket`) — an unbound socket routes over the default network and silently fails; (2) **NSD/mDNS is unavailable in this mode** (NsdManager operates on the default network), so the discovery-list fallback does not exist here — MODE-2's only fallback is re-scanning the QR or joining the AP manually in Settings (which converts the phone to MODE-1).

`:core:transport` gains exactly one message type: `HELLO{token}` replaces the discovery handshake when token-primed; the `Transport` interface gains an optional `boundNetwork: Network?` that every socket factory honours (null in MODE-1/NSD paths). **NSD/mDNS discovery remains the no-QR fallback for MODE-1** (phones already on the Wi-Fi), so the §5.1–5.3 state machines are unchanged — the QR path merely enters `Connected` without passing through a browse. This kills the "which network am I on" failure class Apple's own Live Multicam trips over (iCloud-Keychain-off ground truth). The `ACCESS_LOCAL_NETWORK` denial path (§5.3) applies identically in both modes. **Build cost and relief valve are scheduled in §16.3** (transport half in week 2, UI half in week 4, ~2–3 days total): if QR pairing slips, demo beat 2 degrades to the NSD discovery-list path — already fully specified in §5.1–5.3 — with **zero gate impact**, and the beat-2 script drops the QR flourish (mirrored in product §5.7 and design 02 §5.7).

---

## 6 — Latency & timing budget

**Objection: latency was gates, not a budget; and the headline STOP→timeline moment assumed offsets were already known — true only in the phase-lock branch, which is no longer the default.** Round 3 makes the STOP→timeline budget correct for the **default** (audio-xcorr) branch by adding the windowed-GCC-PHAT leg, and states the hero-moment gate is measured against **both** sync branches.

### 6.1 WebRTC monitor path — glass-to-glass

Target **≤ 250 ms glass-to-glass**, ceiling **400 ms**. Monitoring only — WebRTC degrades quality to hold latency (correct here, catastrophic on masters).

| Stage | Budget | Basis | Status |
|---|---|---|---|
| Camera capture → frame available | 33 ms (1 frame) | sensor + ISP | estimated |
| Proxy encode (540p/720p SDR, realtime) — **the shared proxy encoder, §7.2** | 15–25 ms | HW encoder, low-latency | measured-pending |
| Network (infra Wi-Fi, 1 hop) | 5–15 ms | LAN RTT/2 | measured-pending |
| **Jitter buffer** | **20–60 ms** | pinned floor via `setJitterBufferMinimumDelay` | measured-pending |
| Decode (tablet) | 10–20 ms | HW decoder | measured-pending |
| Compose 4-up + present (1 vsync) | 16–33 ms | render + display | estimated |
| **Total** | **~100–190 ms typical, 250 ms ceiling** | sum | **measured-pending** |

**Jitter-buffer config:** we do not hard-floor NetEq; we set a **minimum playout delay** via `setJitterBufferMinimumDelay(0.02–0.06 s)` (or the sender's `playout-delay` RTP header ext) — a low target, not a hard cap. Loss on lossy Wi-Fi then shows as an occasional concealment judder, not added latency — the correct trade for a monitor. This leg is measured-pending on real Wi-Fi in the week-1 WebRTC spike; if the AP is lossy the floor rises toward 60 ms (still under the ceiling).

### 6.2 UDP time-sync RTT and convergence

| Quantity | Budget | Justification | Status |
|---|---|---|---|
| Single SNTP probe RTT (LAN, urgent-audio thread) | < 4 ms | one Wi-Fi hop; asymmetry is the error term | measured-pending |
| Clock offset after 8 probes (arm time) | converged, std-dev < 200 µs | median-of-8 + outlier rejection | measured-pending (U1 §4.4) |
| **Default: post-record audio-xcorr offset** | **<1 ms co-located / ≤ ~10 ms worst-case SEPARATED rig (acoustic propagation, §4.5) — both ≤1 frame; windowed leg ~150–500 ms on the STOP path** | GCC-PHAT at 48 kHz, SNTP-primed narrow window (§4.5) | derived; **the number G2 is measured against** |
| Upside (if built): live phase-alignment convergence | < 3 frames to lock | RecSync on own repeating request | estimated; moot unless §4.4 U2 green |
| Re-sync interval (upside only) | ~10 min | drift <1.2 ms/min vs 33 ms frame | derived, safe |

### 6.3 Control-command latency

| Command | Target | Budget breakdown | Status |
|---|---|---|---|
| Roll/stop spread across 3 devices (G1) | **< 500 ms** | ROLL carries `startAtLeaderTime = now + 200 ms`; spread = SNTP offset error (<1 ms) + scheduling jitter. 200 ms pre-roll absorbs the slowest warm-up. | measured-pending (G1) |
| Control command RTT (focus/WB) | < 50 ms | one LAN round trip + apply-on-next-request | estimated |
| **Program-preview seek (default = active-angle ExoPlayer)** | **< 80 ms perceived** | plain ExoPlayer `seekTo` on a local proxy (§8.1) | measured-pending |
| Program-preview seek (opt-in CompositionPlayer) | < 150 ms perceived | single-sequence seek — measured-pending (§8.1); gated behind the week-1 spike | measured-pending |

### 6.4 STOP → synced scrubbable timeline present (the hero-moment budget, BOTH sync branches)

**Sequence begins at `STOP ALL` and ends when the multi-angle timeline is scrubbable on the tablet. The critical round-3 fix: in the DEFAULT branch the inter-angle offsets are NOT known at STOP — they are resolved by the windowed GCC-PHAT leg below, which is budgeted here.**

| Leg | Default (audio-xcorr) branch | Upside (phase-lock) branch | Basis | Status |
|---|---|---|---|---|
| Muxer finalize/flush per device (master + proxy) | 100–400 ms | 100–400 ms | parallel on all cameras; bounded by slowest | measured-pending |
| Finalized-file ack collection over TCP | 50–150 ms | 50–150 ms | parallel; overlaps finalize tail | measured-pending |
| **Windowed GCC-PHAT offset resolution (first ~10 s, N angles, SNTP-primed narrow window)** | **150–500 ms** | **~0 (offsets already live-known)** | a few FFTs (§4.5); this is the leg the prior draft omitted | measured-pending |
| Timeline assembly from local durations + resolved offsets (pure Kotlin) | < 20 ms | < 20 ms | build `List<AngleCut>` + coverage model | estimated |
| 4× ExoPlayer `prepare()` → first frame on local proxies | 150–500 ms | 150–500 ms | parallel; **proxies exist at STOP (§7.2)**; local seek | measured-pending |
| **Total (with overlap)** | **~0.5–1.4 s typical** | **~0.3–1.1 s typical** | legs overlap (finalize ‖ ack; xcorr ‖ per-tile prepare) | **measured-pending** |
| **Total (worst-case, NO overlap)** | **~1.57 s** (400 + 150 + 500 + 20 + 500 upper bounds summed) | ~1.05 s | the honest non-overlapped ceiling — stated so <2 s does not secretly rest on overlap | **measured-pending** |

**The gate is met by construction, not by asserted overlap (objection: latency).** The non-overlapped upper-bound sum is stated above (**~1.57 s**: muxer finalize 400 ms + ack 150 ms + windowed GCC-PHAT 500 ms + assembly 20 ms + 4× prepare 500 ms), so the <2 s claim does not secretly depend on legs overlapping — even fully serialized it clears the gate, though with thin margin. But the **DEFAULT rendering path is progressive per-tile offset paint**, which removes the dependency on both overlap *and* that thin margin: the timeline is rendered from local durations + the leader angle **immediately on assembly** (the leader needs no offset), and each non-leader tile's offset is slotted in the instant that angle's windowed xcorr completes. So the timeline is **present and scrubbable within the finalize+assembly legs (~0.5–0.9 s)** regardless of how long the last angle's xcorr takes — the xcorr and per-tile prepare legs paint *into* an already-present timeline rather than gating its appearance. This is the default, not an escape hatch invoked "if the honest sum exceeds 2 s." Further tighteners if even the progressive first paint is slow: (1) narrow the xcorr window to ~5 s (the SNTP prior is tight); (2) a slow device's finalize shows "finalizing…" on its tile while the rest are scrubbable. The long-take drift refinement and its piecewise ~10-min re-correlation cadence (§4.5) run *after* the gate, off the hot path — **and that refinement's residual budget is coupled to the §12 thermal curve as an explicit week-1 pass/fail (§4.5), not a hoped-for footnote:** if the measured mid-take drift-rate curvature exceeds ~1.5 ppm/min, the cadence tightens to ~5 min. This is decomposed up front, before any code, for the branch the demo actually ships (default), not only for the branch that may never be built.

---

## 7 — Capture pipeline

### 7.1 Camera + encode — CameraX-first (locked 2026-07-18) — and the CAMERA-role gate (objection: fcp_fidelity)

**V1 capture is CameraX 1.5 + `Camera2Interop`; raw Camera2 is the spike-gated fallback.** An earlier draft was raw-Camera2-first, on three grounds: (a) HLG10 via `OutputConfiguration.setDynamicRangeProfile`, (b) manual 3A on an owned repeating request, (c) the live phase-aligner substrate. Ground (c) died in round 3 (§4.0 demoted the phase-aligner to an optional upside), and (a)/(b) are served by CameraX 1.5 — HLG10 via its dynamic-range API plus **Feature Groups** to co-declare 10-bit with the other use cases, and manual controls via `Camera2Interop`/`Camera2CameraControl` — **live mid-take via `Camera2CameraControl.setCaptureRequestOptions()` on the running repeating request** (§4.0), with design 02 §4.3's re-arm micro-interaction as the fallback only where the §16.2 audit shows a device drops live updates. What CameraX adds is the one asset a near-zero-code solo builder cannot replicate: **its device-compat quirk database** — per the research corpus, "the single largest hidden asset in that library." The standing architect prediction was that raw-camera stream-combo debugging is where the build bleeds; this inversion moves that debugging onto Google's quirk layer.

- **Stack decision rule:** raw Camera2 enters the build **only** if the week-1 CAMERA-STREAM-COMBO spike (§16.2) proves CameraX cannot bind `{HLG10 master + 10-bit-sampleable SurfaceTexture + preview}` on the owned phones. The phase-lock upside (§4.1–4.4), if ever attempted, also requires the raw path — a cost carried by the upside, never the default.
- **HLG10 masters** via CameraX `DynamicRange.HLG_10_BIT` on `VideoCapture` (raw-fallback equivalent: `OutputConfiguration.setDynamicRangeProfile(DynamicRangeProfiles.HLG10)`), gated on `REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT`. **No LOG** — no LOG API exists on Android.
- **Master encode: `MediaCodec` — hardware AV1 preferred where `MediaCodecList` reports an AV1 hardware encoder (patent-cleaner, better bits-per-pixel; 2026-07-18), HEVC Main10 otherwise.** One capability query at session start; both paths are the same MediaCodec surface code → `MediaMuxer`. Never bundle a codec — OS `MediaCodec` only.

**CAMERA-role eligibility is now decoupled from `HARDWARE_LEVEL_FULL` and from `REALTIME` (the fcp_fidelity fix).** A prior draft gated the CAMERA role on `FULL` *and* `REALTIME`, which silently gave "an unknown but potentially large fraction of MPC≥34 devices" an auto-only or refused experience with no disclosure. Corrected, because the default sync path needs neither:

- **CAMERA role requires only:** MPC ≥ 34 + ability to record HLG10 (or SDR fallback) locally at CFR. That is the actual capture requirement.
- **`HARDWARE_LEVEL_FULL` gates *manual 3A controls only*.** On a non-FULL device the CAMERA role is fully available in **auto mode**; the manual ISO/shutter strip renders **disabled-with-reason** (design 02 §4.3), never hidden, never refused. This is an explicit **DIVERGE vs FCP Camera's manual control** and design 02 Appendix A carries the DIVERGE row.
- **`REALTIME` gates *the phase-lock upside only*** (§4.2). Its absence costs nothing in the default path.
- **The MPC≥34 FULL-fraction is a week-1 device-audit output, not an assumption.** The week-1 hardware audit (§16) records, for both owned phones and — to the extent the MPC≥34 population is knowable from CDD/`core-performance` data — **what fraction of the target population exposes `HARDWARE_LEVEL_FULL`**, since the manual-control fidelity claim hinges on it. We do not silently assume FULL. The prescribed fixed-geometry rig is also explicitly less flexible than FCP Camera's general handheld capture (design 02 Appendix A DIVERGE).

### 7.2 Proxy + monitor encode: TWO encoders during capture, one proxy serving both (objection: concurrent encoder budget; proxy-existence-at-STOP)

**Objection (both reviewers): the capture-side concurrent-ENCODE budget was never analyzed or spiked, and §7.1+§7.2 implied up to THREE concurrent hardware encoders (master HEVC/HLG10 + WebRTC monitor proxy + a separate 540p editor proxy) on the exact device that is the thermal bottleneck. Separately, the <2 s hero budget assumed editing proxies "exist at STOP," but the thermal governor sheds proxy quality first, so under warm-room long takes the proxy the hero depends on could be absent.** Both are resolved structurally by reducing to **two encoders and making one proxy serve both roles**:

- **Encoder 1 — master:** HEVC Main10, HLG10, `MediaMuxer` → local master.
- **Encoder 2 — the single SDR proxy encoder:** H.264, 540p/720p SDR, 30 fps, fed from the camera via a GL tone-map (HLG10→SDR) pass. Its bitstream is **muxed to a local `proxy.mp4` continuously during record** — so **the editing proxy exists at STOP by construction** (it is not a post-record Transformer pass, and it is not shed for the master). This same encoded stream is **also the source for WebRTC LAN monitoring** (via an encoded-frame video source), so monitoring adds **no third encoder**.
- **Session output targets:** `{ masterEncoderInputSurface (HLG10), viewfinderSurfaceView, proxyEncoderInputSurface (SDR) }` — one camera session, one master encoder, one proxy encoder. **Every device records full quality locally; footage never crosses the internet.**

**The stream-combination feasibility is the single hardest question in the plan, and it is NOT the encoder-count question (objection A).** The pipeline above asks the camera stack to concurrently drive, *from one physical camera*, three outputs of two different pixel worlds: a **10-bit HLG10 surface** into the master encoder, a **10-bit-readable `SurfaceTexture`** that feeds the HLG10→SDR GL tonemap into the proxy encoder, and the **viewfinder**. Whether a given device's Camera2 HAL admits *this exact surface set* — a 10-bit dynamic-range master output *plus* a 10-bit-sampleable texture target *plus* preview, concurrently — is a distinct feasibility question from "can two `MediaCodec` encoders sustain," and the prior draft wrongly folded it into the encoder-*count* spike, which measures the wrong thing. It gets **its own week-1 CAMERA-STREAM-COMBO spike (§16.2)** — and, round-5 fix (objection: tech_stack), the spike now names **the exact candidate CameraX mechanisms to attempt, in priority order**, because "bind {HLG10 master + 10-bit-sampleable SurfaceTexture + preview}" names *surfaces*, not CameraX *use cases*: CameraX offers only Preview / ImageCapture / VideoCapture / ImageAnalysis, `ImageAnalysis` is 8-bit YUV (it cannot carry the 10-bit tonemap tap), and a second `Preview` cannot bind — so without named candidates the spike author has no binding to attempt, and a naive bind failure would produce a **false "CameraX cannot do it" red** that flips the stack to raw Camera2 unnecessarily. The candidates, tried in this order — **a red requires ALL of them exhausted, not one naive bind failure**:

1. **CameraX `CameraEffect`/`SurfaceProcessor` targeting `PREVIEW | VIDEO_CAPTURE` — tried FIRST.** The app-owned GL processor receives the camera stream **once** and fans out to the viewfinder quad *and* the HLG10→SDR tonemap feeding the proxy-encoder input surface. This reduces the HAL-facing surface count to two (HLG10 master + the effect's input) and keeps the entire 10-bit tap inside app GL we control — the most likely-to-bind shape.
2. **`Preview` with a custom `SurfaceProvider` handing an app-owned `SurfaceTexture`.** App GL samples that single tap and renders both the on-screen viewfinder quad and the proxy-encoder input from it (the viewfinder becomes app-drawn rather than a direct HAL surface).
3. **Raw Camera2 three-surface session** — validated against the **mandatory stream-combination table** (`CameraCharacteristics.getMandatoryStreamCombinations` / `isSessionConfigurationSupported`) — attempted **only after 1 and 2 fail**; a green here is the only thing that admits §7.1's raw fallback into the build.

The spike runs on **both** owned phones and separately confirms the GL tonemap shader can actually **sample 10-bit HLG** (needs `EGL_EXT_yuv_surface` / `GL_EXT_YUV_target` `samplerExternalOES` with `EGL_EXT_gl_colorspace_bt2020_hlg` — not guaranteed on every SoC). **Pre-committed fallback, structured so a red result does NOT cascade into losing master + proxy + monitor at once — and each fallback's cost is quantified against the binding budget it spends, not waved off as "absorbed" (objection: tech_stack):**

- **(i) SDR-only capture** — drop the 10-bit-GL path entirely and capture master *and* proxy in 8-bit SDR H.264. **Pillar sacrificed: HLG10 masters** — the only 10-bit profile with an AOSP compliance mandate (product §2.4/§5.3), i.e. the *reliability* pillar. Concretely, **gate G8 (master quality) degrades from "HLG10 10-bit HDR master" to "8-bit SDR master"** on any device that fails the stream combo; the master stays full-resolution, full-rate, never-uploaded, open-filesystem — only bit depth and the HDR transfer are lost. This is a real, named degrade of the master-quality claim, taken *only* on devices whose HAL refuses the 10-bit surface set, and surfaced to the user (the capture chrome shows `SDR` instead of `HLG10`, design 02 §4.2). It costs **zero thermal and zero latency** (fewer bits, not more work).
- **(ii) MediaCodec-transcode proxy off the master** — keep the HLG10 master; derive the editing proxy by a `MediaCodec` decode→tonemap→encode pass off the master instead of a live camera-session GL tonemap. **Pillar sacrificed: the proxy's zero-cost-at-capture property**, which spends *both* binding budgets — itemized, not "absorbed": **(a) thermal** — the transcode is extra decode+encode work whose estimated marginal cost is a **distinct line in the §12 thermal table** (est. **+1 heat rung / ~3–5 fewer minutes-to-throttle**, spike-measured); if it pushes G3 under 20 min it triggers the §12 single-encoder analysis. **(b) latency** — a *post-record* transcode adds a proxy-generation step to the STOP→timeline hero, budgeted as an **explicit line in §6.4 / §12** (est. **+2–6 s for a 40-min take** at ~4–8× realtime on the low-res proxy, shown as a determinate progress step, not hidden). A *live-during-record* transcode instead spends (a) but not (b). Which sub-variant is used is the §16.2 spike's call.

One lever moves; the other outputs survive — but neither lever is free, and the cost of each is now a budgeted number, not an assertion.

**The week-1 concurrent-encoder spike (§16.2), run only once the stream combo is confirmed viable, measures whether two encoders start and sustain reliably, and whether WebRTC on the owned stack can consume the pre-encoded proxy stream:**
- If WebRTC **can** consume the pre-encoded stream → two encoders total; done.
- If WebRTC **requires its own encoder** (→ three) *and* the spike shows three exceeds the measured per-SoC start budget or blows the G3 thermal envelope → **pre-committed fallback: keep two encoders (master + editing-proxy) and drive live monitoring from a low-fps slice / periodic still-frames of the proxy stream over the TCP channel** (the still-refresh monitor rung, §9 / design 02 §5.2, §10). **We never author a third concurrent encoder to keep the live grid.** Either way the editing proxy exists at STOP and encoder count is capped at two.

**Proxy 8-bit SDR is forced, not chosen:** CDD [5.1/H-1-2] mandates 6 concurrent 8-bit SDR 1080p30 *decodes*; [5.1/H-1-19] guarantees only 3 concurrent 10-bit HDR. There is no guarantee of 4 concurrent HLG10 decodes at any resolution, so the 4-up preview must be SDR (§8.2/§9). The proxy encoder therefore tone-maps to SDR at capture.

### 7.3 Storage: where files live (2026-07-18 gap fix)

- **Masters + proxies:** app-scoped external storage (`Context.getExternalFilesDir`) — no runtime permission, survives app update, **cleared on uninstall** (documented user-facing, so the "your files are safe" copy never overpromises). Session folder layout follows the design 02 §3.1 manifest model; masters stay on the phone that shot them (G7).
- **User-facing export:** `MediaStore.Video` insert into the shared `Movies/` collection for gallery visibility (design 02 §3.3), plus SAF `ACTION_CREATE_DOCUMENT` for arbitrary destinations — including USB-C drives.
- **USB-C SSD *recording*** (Blackmagic-parity, iOS-painful): **V2** — requires SAF-scoped `MediaMuxer` FD handling plus removal-mid-write hardening. Named now (alongside the V2 DeX controller surface and LR-ASD fusion, §11.1) so the roadmap story is coherent; not built.

### 7.4 Audio capture source (2026-07-18 gap fix)

- **V1 records `MediaRecorder.AudioSource.CAMCORDER`** (tuned for video: AGC/NS profile), 48 kHz mono AAC muxed alongside the video track. This is the audio that feeds sync xcorr (§4.5), the director's VAD (§10.1), and whisper (§11.4).
- **USB audio-class (lav/interface) input: V1.5.** Android routes UAC automatically via `AudioManager`; the work is UI (source picker + level meter), not plumbing. It is the **highest-leverage V1.5 item** because one change improves three subsystems at once: whisper WER, VAD margin, and xcorr SNR.

---

## 8 — The Media3 editor: preview + export pipeline

### 8.1 The re-architecture, and the preview path that needs no experimental API

A multicam **program output is two sequences** (§8.2): one **video-only** sequence of per-cut clipped items, one **audio-only** sequence — a single clipped item when no transcript deletions exist, N+1 clipped items (boundaries only at deletion seams) when they do (§8.2). Because there is exactly **one video input**, it never invokes `MultipleInputVideoGraph` and dodges the multi-sequence bug class (#2439 seek crash, #2742 compositor deadlock). The **4-up monitor grid** is four Views, not one composited frame, so it needs no compositor either.

| Surface | Implementation | Why it's safe |
|---|---|---|
| 4-up scrub/monitor ribbon | **4× plain `ExoPlayer` on 4 `SurfaceView`s, one shared clock — an AUTHORED lock, specified in §8.1a below, not a library property** | Ordinary stable ExoPlayer + ~a page of authored sync code (§8.1a). Not CompositionPlayer. |
| **Program preview (DEFAULT)** | **The active angle's already-running `ExoPlayer` surface at the playhead** — swaps which of the 4 ExoPlayers it shows as the decision list crosses a cut | **Zero dependency on CompositionPlayer.** The experimental class is removed from the interactive scrub path entirely. |
| Program preview (opt-in) | Single-sequence `CompositionPlayer` — **only if** the week-1 spike (§16.1) confirms single-sequence seek across many clipped items | True composited preview once verified; never on the critical path until then. |
| **Conjured-angle (`CropTrack`) scrub preview** — Beat 7 punch-in | **The source angle's `ExoPlayer` + a `Crop` effect applied via `ExoPlayer.setVideoEffects(List<Effect>)`** (`@UnstableApi`, media3-effect), the crop rect driven per output frame from the interpolated `CropTrack` (§11.2). **Fallback:** a **dedicated single-input GL `SurfaceView` crop shim** — decode to `SurfaceTexture`, draw the crop quad in a fragment shader, no Media3 effect involved (the §8.4 machinery at 1× input). | Interactive path was previously specified only for *export*, never for scrub (objection D). See note below. |
| Export | Two-sequence `Composition` → `Transformer` | `Transformer` is stable; export is not a scrub surface. **Its heterogeneous-source correctness is measured-pending (§8.3, §16).** |
| **In-app captioned playback (the #16 demo close)** | `ExoPlayer` with `MediaItem.SubtitleConfiguration` pointing at the exported `.srt` sidecar — captions rendered **live at playback time, nothing burned in** (design 02 §7/§8.1 stages the close on this player and says so on camera: "captions are a sidecar file you can edit — nothing is burned in") | Stable, ordinary ExoPlayer subtitle path, already in the dependency set; keeps the burned-in-captions out-of-scope lock intact while making the captioned close honest. |
| **Vertical 9:16 export (#15)** | Same two-sequence `Composition`; the reframe `CropTrack` (§11.2) drives per-item `MatrixTransformation` + `Presentation.createForWidthAndHeight(1080, 1920, LAYOUT_SCALE_TO_FIT_WITH_CROP)` on the **same Transformer path** (§8.3) | One more export preset, not a new pipeline; covered by the same export spike, **no new gate**. |
| 2×2 composited grid / PiP | **CUT to V2, export-only effect** | #2439 + #2742 make it unscrubbable in V1. |

Single-sequence CompositionPlayer seek remains **measured-pending** until the §16 week-1 spike proves it on the owned tablet at 1.10.1; the product does not *need* it because the default program preview is the active-angle ExoPlayer.

### 8.1a The 4-player frame-lock design — authored synchronization, not a library property (round-5, objection: scalability)

**The objection is correct: ExoPlayer has no cross-instance clock-slaving API, and "one shared clock" was being asserted as if Media3 provided it.** Four free-running players drift apart within seconds of playback (the three muted followers have no audio master disciplining them), and scrub-lock and playback-lock are *different problems*. Both are authored here, explicitly:

- **Scrub (paused) = broadcast seek.** The timeline playhead is the single source of truth. On every scrub gesture, all four players receive `seekTo(playheadInAngleTime)` with `SeekParameters.CLOSEST_SYNC` for the in-motion drag (fast, keyframe-snapped, all four land on the *same* snapped instant because proxies share keyframe cadence by construction — one encoder config, §7.2), then an exact-position seek on gesture end for frame-precise alignment. Paused players cannot drift; scrub-lock is therefore just consistent seek targeting.
- **Playback = ONE master, three disciplined followers.** The player whose angle carries the program audio (the promoted/audio angle) is the **master clock**; the other three are muted followers. A `Choreographer`-independent poll every **~500 ms** compares each follower's `currentPosition` (offset-adjusted to leader time) against the master's. Correction policy, in order: divergence ≤ 1 frame (33 ms) → do nothing; divergence > 1 frame and ≤ ~120 ms → **micro-trim via `setPlaybackParameters(speed = 1 ± 0.02)`** until convergence (imperceptible on a muted thumbnail, no visual jump); divergence > ~120 ms (a stall or decoder hiccup) → hard `seekTo` resnap to the master position. Cadence and thresholds are stated so the implementation is typed, not tuned in a panic: **poll 500 ms, deadband 33 ms, trim band 33–120 ms at ±2% speed, resnap beyond 120 ms.**
- **Cut-crossing promotion** (the program preview swapping which player is promoted, §8.1) does not disturb the lock: the master role follows the *audio* angle, not the promoted video angle, so promotion is a pure view swap.
- **Verification is a named week-1 spike line, not an assumption:** the §16.1 batch verifies **4-player scrub+play lock on the owned tablet at the chosen proxy rung** — scrub-snap agreement across four tiles, and playback divergence staying inside the deadband over a 5-minute play with the follower-discipline loop running. This is the substrate of the entire editor and the hero moment; it is spiked as such.

**The `CropTrack` live-scrub rendering path, specified (objection: creator_wow — Beat 7's interactive preview was never specified, only its export path).** When the user scrubs the conjured punch-in angle (design 02 §6.6), the crop/zoom must render *live*, not only on export. Two paths, primary + fallback:

- **Primary: `ExoPlayer.setVideoEffects(List<Effect>)` with a media3-effect `Crop`** (optionally `Presentation`/`ScaleAndRotate`) on the *same* per-angle `ExoPlayer` that already backs the ribbon tile — the crop rectangle is recomputed each output frame from the interpolated `CropTrack` keyframes and pushed to the effect. This keeps the conjured angle on the ordinary ExoPlayer scrub clock, in frame-lock with the other angles. **Maturity flag:** `ExoPlayer.setVideoEffects` is `@UnstableApi`, and effect-on-*preview* (as opposed to Transformer export) is materially less battle-tested than plain ExoPlayer playback — it must be exercised in the week-1 face-tracker/framing spike scaffold before it is trusted on the demo-critical path, exactly as CompositionPlayer is.
- **Fallback (removes all Media3-effect dependency): a dedicated single-input GL `SurfaceView` crop shim.** Decode the source angle to a `SurfaceTexture`, sample it in a fragment shader, and draw only the `CropTrack` sub-rectangle to the `SurfaceView` — the §8.4 compositor machinery reduced to one input, one quad. This is authored GL we control (no experimental Media3 surface), and it is the pre-committed path if `setVideoEffects`-on-preview proves buggy on the owned tablet.
- **Export renders the identical `CropTrack`** as a `Crop`/`Presentation` effect inside the `Transformer` pass (§8.3), so scrub preview and export share one crop definition — the unified preview/export *model* holds for the conjured angle too, with only the *interactive renderer* de-risked onto ExoPlayer-effect-or-GL-shim.

### 8.2 Assembling the Composition — TWO sequences, coverage-aware, with a program-time model for transcript deletions

`ClippingConfiguration` clips *both* tracks, so per-cut clipped items would chop audio at every switch; an **audio-only** `EditedMediaItemSequence` does **not** invoke `MultipleInputVideoGraph` (that graph is for multiple *video* inputs).

**Round-5 correction (objection: ai_features — the guaranteed, non-cuttable feature #14 could not be expressed by this function's own signature).** The prior draft required `cuts` to be "gap-free over the take" and modeled audio as ONE unclipped item spanning the whole take — but a transcript deletion (§11.4, the locked V1 wow) removes a *mid-take time range*, which that model had no representation for: it would force the audio into clipped pieces and shift every downstream cut, and the function had no vocabulary for either. Design 02 §6.8's promise that "audio stays continuous across the removal seam" was riding on an unspecified mechanism. Fixed with a first-class **program-time model**:

```kotlin
// TAKE time = leader time on the recorded media. PROGRAM time = take time with deletions excised.
data class Deletion(val startUs: Long, val endUs: Long)   // a transcript-deleted TAKE-time range

// Sorted, non-overlapping deletions D define the KEPT ranges (their complement over [0, takeDurationUs])
// and the single take→program mapping used everywhere downstream:
//     P(t) = t − Σ length(d)  over every deletion d entirely before t     (defined on kept ranges)
// P is consumed by three clients, and ONLY via this one definition: video cut re-timing (below),
// audio item boundaries (below), and SRT timestamp emission (§11.4).
```

```kotlin
@OptIn(UnstableApi::class, ExperimentalApi::class)
fun assembleProgram(
    cuts: List<AngleCut>,                 // (angleId, startUs, endUs) in TAKE time, ordered,
                                          //   gap-free over the KEPT ranges (not the raw take);
                                          //   the caller has already SPLIT any cut spanning a deletion
                                          //   seam and DROPPED fully-deleted cuts (re-timing through P)
    deletions: List<Deletion>,            // sorted, non-overlapping; empty list ⇒ exactly the old model
    angleFiles: Map<Int, Uri>,
    audioAngleId: Int,                    // the single chosen audio angle for the whole take
    coverage: Map<Int, LongRange>,        // per-angle available leader-time interval (§5.1a)
    takeDurationUs: Long,
): Composition {
    // Precondition (caller-enforced): every cut's angle COVERS [startUs,endUs] (§5.1a), and no cut
    // or audio item intersects a deletion. A designated alwaysCovered angle fills otherwise-empty instants.

    // --- VIDEO: one video-ONLY sequence of per-cut clipped items over the KEPT ranges ---
    // Items are clipped in SOURCE (take) time; their PROGRAM positions are implicit in sequence
    // order — a sequence concatenates, so the emitted program is P-mapped by construction.
    val video = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
    for (cut in cuts) {
        val clip = MediaItem.Builder()
            .setUri(angleFiles.getValue(cut.angleId))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(cut.startUs / 1000)
                    .setEndPositionMs(cut.endUs / 1000)
                    .build()
            ).build()
        video.addItem(EditedMediaItem.Builder(clip).setRemoveAudio(true).build())
    }

    // --- AUDIO: one audio-ONLY sequence of N+1 CLIPPED items (N = deletions.size) ---
    // One clipped item of the audio angle per KEPT range, in order. Audio item boundaries exist
    // ONLY at deletion seams — never at video switches — so between seams the audio remains one
    // continuous take (the design 02 §6.8 promise, now expressed by the model rather than asserted),
    // and each seam is exactly the failure surface the §16.1 spike's deletion-seam line tests.
    val audio = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
    for (kept in keptRanges(deletions, takeDurationUs)) {       // N deletions → N+1 kept ranges
        val slice = MediaItem.Builder()
            .setUri(angleFiles.getValue(audioAngleId))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(kept.first / 1000)
                    .setEndPositionMs(kept.last / 1000)
                    .build()
            ).build()
        audio.addItem(EditedMediaItem.Builder(slice).setRemoveVideo(true).build())
    }

    // ONE video input + ONE audio sequence. No MultipleInputVideoGraph, no setVideoGraphFactory,
    // no VideoCompositorSettings -> does NOT hit #2439/#2742.
    return Composition.Builder(listOf(video.build(), audio.build())).build()
}
```

**How a transcript deletion propagates (the re-timing rules, stated once):** deleting a transcript range `[a,b)` (word-boundary-snapped, §11.4) (1) inserts a `Deletion(a,b)` into the sorted set; (2) **splits** any `AngleCut` spanning `a` or `b` at the seam, **truncates** cuts partially inside, and **drops** cuts fully inside — all in take time, so the decision-list remains take-time-native and reversible (removing the deletion restores the original cuts from the stored pre-split list); (3) rebuilds both sequences. Because *both* sequences excise the identical take-time ranges, their concatenated durations agree and audio–video alignment holds across every seam by construction. The editor's timeline ruler displays **program time** via `P`; scrub, cut badges, and the SRT export (§11.4) all consult the same map. No re-encode happens until export — a deletion is a decision-list edit like any other.

**Coverage (objection: android_differentiation).** `cuts` is guaranteed gap-free over the kept ranges by the caller, which consults `coverage` (§5.1a) and never assigns an angle at a time it wasn't recording; the `alwaysCovered` leader angle fills gaps. A controller-loss angle with a short coverage interval simply isn't a candidate outside it.

**#2854 guard:** freezes occur on clips with no audio track when audio is declared in the track-type set. The video sequence declares `TRACK_TYPE_VIDEO` only and strips audio (`setRemoveAudio(true)`), so it never declares audio on a possibly-audioless clip. Every audio item points at the one file every phone guarantees has an audio track. Structurally avoided. **But the export-side behavior of this guard under real heterogeneous sources is measured-pending — it is a named line in the §16 export spike, not assumed.**

**Changing the audio angle** swaps the URI across the N+1 audio items (still one logical operation); **splitting/moving a video cut** edits `cuts` and rebuilds the video sequence only; **editing a deletion** rebuilds both sequences — in every case, no re-encode until export.

### 8.3 Export (Transformer, MediaCodec-only) — the heterogeneous-source path is the real risk

```kotlin
@OptIn(UnstableApi::class)
fun export(composition: Composition, out: String, listener: Transformer.Listener) {
    Transformer.Builder(context)
        .setVideoMimeType(MimeTypes.VIDEO_H264)   // H.264 default (patent surface §9.11); HEVC opt-in
        .addListener(listener)
        .build()
        .start(composition, out)                  // the SAME two-sequence Composition as §8.2
}
```

**Objection (tech_stack, both reviewers): G6 export and demo Beat 9 rest on a two-sequence Composition concatenating 20+ per-cut CLIPPED items from DIFFERENT source files (a Pixel angle + a Samsung angle = different encoder params/resolutions/color), and this pattern was asserted correct but never spiked. It is a different code path from single-sequence *seek*.** Confronted, not assumed:

- **Homogenize-to-common-intermediate is the DEFAULT export path, not a fallback (objection G).** Cross-vendor heterogeneous *passthrough* — concatenating clips of different resolution/encoder/color untouched — is unlikely to hold across a Pixel angle + a Samsung angle, so **V1 does not attempt it as the primary path.** The default is: **re-encode every clip to a single common output format** (one resolution, one codec/profile, one color space), so the concatenation is homogeneous by construction. The worst case (every cut is a source switch) transcodes every clip, and that *is* the budgeted case — not an exception. **G6 ≤1.0× realtime is budgeted NOW *with* full per-clip transcode**, heavier than any passthrough dream, and that is exactly what the (week-1-priority) §16.1 export spike measures. Passthrough, if the spike happens to show it works for homogeneous same-vendor rigs, is a *later* optimization, never the thing the demo depends on.
- **Audio continuity is TWO explicit binary pass/fails, not a note (objection G + round-5 ai_features fix).** (1) **Across every video switch:** within a kept range the audio is one continuous clipped item (§8.2) and must play as one unbroken track while the video sequence re-encodes clip-by-clip — **zero gaps, clicks, or resyncs across ALL 20+ video-item boundaries.** (2) **Across every transcript-deletion seam:** this is a *different* failure surface — at a seam two *clipped audio items* must join, and clipped items CAN gap or click — so the spike separately asserts **zero gaps/clicks/resyncs at every deletion seam** on a run with ≥3 deletions. Because there is no cited open-bug guard equivalent to the video-side #2439/#2742 analysis, one failure on either line anywhere is a red spike, not a caveat. The #2854 audioless-clip edge is checked in the same pass.
- **Hard-cut frame accuracy at boundaries.** Each `ClippingConfiguration` boundary must land on the intended frame after transcode (no off-by-one, no dropped/duplicated frame at the seam). Verified in the export spike.
- **HLG10 vs SDR export:** all-HLG10 sources → keep HEVC Main10 + HLG10 transfer (Media3 preserves it absent a tone-map effect). SDR export adds a tone-map effect. Default V1 export is **SDR H.264** for playability; HLG10 master export is opt-in.
- **No transitions between media items** — they don't exist in Media3; cuts are hard cuts, which matches a multicam switch.
- **Master-quality re-export needs the masters, which never left their capture phones.** The decision-list resolves against **proxies for scrub, masters for export** (§8.2; design 02 §3.3). A session reopened on the tablet alone therefore has everything for a **proxy-quality re-export by default** (proxies live on the tablet), but a **master-quality re-export is an explicit opt-in that must first gather the referenced master ranges over the LAN from the phones that hold them** (`⧉ Gather masters`, design 02 §3.4). This is an honest workflow tax FCP's single-library model does not impose — surfaced, never silent — and it is why design 02 §3.4/Appendix A logs multi-device master-availability as a **DIVERGE, not a MATCH**.
- Export throughput gate G6: ≤ 1.0× realtime **with transcode**. Measured-pending (§16).

**Vertical 9:16 export (#15, 2026-07-18) — a second preset on this same path, not a new pipeline.** The program's `AngleCut` list plus a per-angle crop source (below) drive per-item `MatrixTransformation` (crop) + `Presentation.createForWidthAndHeight(1080, 1920, LAYOUT_SCALE_TO_FIT_WITH_CROP)` effects on the same two-sequence Transformer export above. The CropTrack's keyframes are already time-parameterized (design 02 §6.6); the export samples them at output frame rate exactly as the scrub preview does (§8.1), so preview and export share one crop definition in the vertical preset too. Cost: one extra export pass, ≤ the budget above (smaller output raster). Measured in the same heterogeneous-export spike (§16.1); **no new gate**. UX: the export sheet's "Vertical Short (9:16)" card with ≤2-tap keyframe nudge (design 02 §B.1→§7). **V1 PAID** — it rides the paid reframe floor (#12), and one phone in yields the wide episode **and** a face-tracked vertical Short out (product #15).

**Per-angle crop policy — every program segment has a defined crop, not just the tracked angle (round-5 fix, objection: creator_wow).** The reframe `CropTrack` exists only for the single wide angle the tracker ran on; the prior draft left the vertical preset **undefined** for program segments cut to any other angle (the close-up phone, a conjured angle's source, a `SYNC_DEGRADED` rejoiner). Specified now, mirrored in design 02 §7:

- **Tracked angle** → its `CropTrack`, as before.
- **Untracked angles** → a **static face-centred crop seeded from ONE BlazeFace detection at the segment's first sampled frame** (the detector is already in `:framing`; one inference per segment, negligible cost). No face found → **centre crop**. Either way the result is **emitted as ordinary editable `CropTrack` keyframes** — one keyframe per segment — so the ≤2-tap nudge grammar applies uniformly to every segment of the Short; the user never meets a segment whose crop cannot be adjusted.
- **Default demo path vs general case, stated:** the **single-angle-source Short** (one wide phone — the product §1.4 Beat A / design §2.3 single-phone story) is the *default demo path*, where the tracked CropTrack covers the whole program and this policy never bites; the **multicam-program Short is the general case the policy covers**, so a paid deliverable never rests on undefined behavior. The §16.1 export-spike vertical pass includes at least one untracked-angle segment so the static-crop path is exercised, not just specified.

### 8.4 The fallback compositor (surface-based MediaCodec → GL → encoder) — V2 / OUT OF SCOPE for the demo

Explicitly gated out of the 8-week critical path. Authored only if a future V2 needs export-only PiP/grid, or as a documented last rung the **demo does not rely on**. The demo's decoder floor is **single-angle preview + still-grid**, *above* this compositor.

```
For each of N angles:
  MediaCodec(decoder) --configure(outputSurface = Surface(SurfaceTexture_i))--> decodes into a GL external texture
The GL thread (owns EGLContext):
  for each angle i: SurfaceTexture_i.updateTexImage(); getTransformMatrix()
  bind angle i's external texture (GL_TEXTURE_EXTERNAL_OES) -> fragment shader -> draw into quadrant i of the FBO
  the FBO is the encoder's input surface: MediaCodec(encoder).createInputSurface() wrapped in an EGLSurface
  eglSwapBuffers() -> one composited frame handed to the encoder with no CPU copy
MediaCodec(encoder) --> MediaMuxer (export)  OR  --> SurfaceView (preview)
```

Decode **to `SurfaceTexture`**, composite in a **GL fragment shader** sampling `samplerExternalOES`, encode via **`createInputSurface()`**. Never mix a GL renderer with a ByteBuffer codec. OpenGL ES, not Vulkan. **Not on the V1 critical path; the demo must be provable without it.**

---

## 9 — The decoder-budget fallback ladder (measured-pending)

**The spike is written (`spike/SpikeViewModel.kt`) and not yet run.** `getMaxSupportedInstances()` is an advertised upper bound, not a guarantee; the real limit is measured via `configure()`/`start()` failures. Port AOSP CTS `MultiDecoderPerfTest.java` / `MultiCodecPerfTestBase.java` (`REQUIRED_MIN_CONCURRENT_INSTANCES = 6`).

```
4× 720p SDR proxies (target)
   ↓ red
4× 540p SDR proxies         (Apple monitors at a 720p ceiling; we don't need 1080p to monitor)
   ↓ red
2×2 at 24 fps
   ↓ red
single-angle live preview + still-grid for inactive angles   ← DEMO FLOOR. The demo is provable here.
   ↓ red (post-demo only)
custom MediaCodec + OpenGL ES compositor (§8.4)              ← V2 / OUT OF SCOPE for the 8-week demo
```

Per-angle health caveat: **b/451741691** — dropped-frame counts are aggregated across all sequences; a drop cannot be attributed to a specific angle. Per-angle health in V1 is inferred (link state, storage, thermal), not decode-attributed.

**Decoder budget results table — left open for spike numbers:**

| Device | SoC | HW level | 10-bit | Timestamp source | Max instances (H.264) | 4× 1080p30 grid result |
|---|---|---|---|---|---|---|
| Phone 1 | _pending_ | _pending_ | _pending_ | _pending_ | _pending_ | _pending_ |
| Phone 2 | _pending_ | _pending_ | _pending_ | _pending_ | _pending_ | _pending_ |
| Tablet | _pending_ | _pending_ | _pending_ | _pending_ | _pending_ | _pending_ |

---

## 10 — The AI Director engine (V1, if SPIKE-AUDIO passes)

Gated on SPIKE-AUDIO (product §5.4): passes at >6 dB inter-angle energy delta, conditional at 3–6 dB, **cut entirely at <3 dB**.

### 10.1 The V1 VAD — named, sized, budgeted

**V1 VAD is gated energy VAD (DSP), not WebRTC-VAD or Silero.** The SPIKE-AUDIO signal *is* inter-angle energy delta; a learned VAD buys nothing when the discriminator is relative loudness between co-located mics.

**Algorithm (per angle, post-record, `Dispatchers.Default`):**
- Window: 20 ms frames, 50% overlap (10 ms hop).
- Per frame: short-window RMS in dB; a noise-floor tracker (running 10th-percentile over 2 s) for per-angle gain normalization.
- Feature per window per angle: `[rms_db, rms_db − noise_floor_db, zero-crossing-rate]`.
- **Active angle** = `argmax` of gain-normalized energy, **only if** the winning margin ≥ the SPIKE-AUDIO threshold (6 dB confident; 3–6 dB longer hold; <3 dB not shipped).
- Smoothing: 300 ms median filter over the argmax stream before the FSM.

**Compute:** RMS + ZCR + percentile over 20 ms windows is O(samples). 40-min × 4 angles × 48 kHz: **well under 0.1× realtime, single-threaded CPU**, est. < 5 s wall-clock.

### 10.2 The FSM — concrete states, transitions, costs

FSM over shot selection, editing grammar as transition costs. States = "currently on angle *k*." A cut is a state transition minimizing cost per window subject to hard timing constraints.

| Constant | Value | Source |
|---|---|---|
| `DMIN` | 1.5 s (conservative) / 5.0 s (default) | US7349005 / SmartSwitch ~1.5 s |
| `DMAX` | 8 s (leading role 6–8 s) | US7349005 |
| Pre-Attack / Attack | 120 ms / 40 ms | LiveCUT |
| Pre-Release / Release | 200 ms / 300 ms | LiveCUT |
| Reaction-shot hold | 1.0 s | design rule (product §5.5) |

```
cost(c→k, t) =
    w_speaker * (1 − activeConfidence(k, t))
  + w_hold    * holdPenalty(timeOnCurrent, DMIN, DMAX)
  + w_variety * varietyPenalty(k, recentAngles)
  + w_grammar * grammarCost(shotSize[c], shotSize[k])
  + w_switch  * switchCost                              // the conservative/aggressive dial
```

**Hard constraints:** no transition before `DMIN` (except a hard speaker change past threshold); **cut on SPEAKER TRANSITION, never on silence** (patent avoidance, product §9.6, and the better edit); candidate set at `t` excludes angles whose coverage (§5.1a) does not contain `t`. The conservative/aggressive dial is `w_switch`; at the 3–6 dB band it defaults conservative.

**Output → `List<AngleCut>`** — exactly §8.2's input. Re-editable, never flattened; per-boundary override ≤2 taps.

### 10.3 Compute budget summary (owned SoC)

| Stage | Method | Cost | Basis |
|---|---|---|---|
| Auto-sync (audio X-corr, windowed) | GCC-PHAT, SNTP-primed | < 0.1× realtime; ~150–500 ms windowed leg | §4.5 |
| VAD (per angle) | energy + ZCR, 20 ms/50% | < 0.1× realtime | §10.1 |
| Framing face detect | BlazeFace, GPU delegate | see §11 | sampled frames |
| FSM optimal path | Viterbi-style DP | O(states² × windows), ms | discrete opt |
| Composition assembly | two-sequence build | negligible | §8.2 |
| **Rough cut total (G5)** | — | **≤ 0.5× take duration** | sum |

### 10.4 If the quality gate fails at week 6 with energy-only selection (objection: ai_features)

**The V1 director is pure energy-argmax VAD with no ASD — the spec's own cited evidence (EditIQ beating speaker-detection editing; Durlach's "stayed on one angle") calls this the known-weak version. State the realistic expectation up front and pre-commit the degrade, so week 6 is not an emotional decision.**

**Realistic quality-gate expectation for energy-only selection (stated in advance):**
- **>6 dB SPIKE-AUDIO band:** energy-argmax + conservative pacing is *expected* to clear ≤2 overrides/min on the beachhead's clean, one-mic-per-speaker geometry. This is the case the gate was written for.
- **3–6 dB band:** energy-only is *expected to miss* ≤2/min (roughly 2–4/min anticipated); it ships only with the conservative dial and the degrade below is likely.
- **<3 dB:** director cut entirely (product §5.4).

**Pre-committed degrade ladder if the gate is red at week 6 (instead of shipping a director that trips its own gate):**
1. **Increase hold / lengthen DMIN** (hold-longer heuristic): raise `DMIN` toward 5 s and `w_switch`, so the director cuts less often and each cut is higher-confidence. Fewer, safer cuts → fewer overrides. First lever, cheap, a parameter change.
2. **Degrade to timing-only.** If angle *choice* is what trips the gate, the director proposes cut **timing** only — it marks *where* a speaker transition warrants a cut (high-confidence energy transitions) and leaves *which angle* to a conservative default (the active-speaker argmax at that instant) that the user overrides in ≤2 taps. The valuable, defensible signal (a good human still has to decide *when* to cut) ships; the weak signal (fine-grained angle choice) becomes an editable default rather than a claim. This is honestly less than a full auto-director and is labeled as "suggested cut points," not "the AI cut it."

   **The timing-only mode has its own falsifiable bar (objection B — the prior draft's timing-only degrade "could not fail its own gate because it no longer made the claim the gate scored").** Timing-only is scored, on the SPIKE-AUDIO test clip, against a human reference cut, as: **≤ 1 spurious-or-missed cut *point* per minute**, where a proposed point counts as *matched* if it lands within **±0.5 s** of a human cut, every spurious point is **dismissable in ≤ 1 tap**, and every kept point is **movable in ≤ 1 tap**. A run that exceeds ~1 wrong point/min *fails* — this is a real, losable gate, unlike the unfalsifiable version it replaces. If timing-only itself fails this bar, the ladder proceeds to step 3 (cut the director) rather than shipping unscored suggestions.

   **Transcript markers improve cut-point placement over energy-only (this is where whisper-in-V1 pays off, decision 2).** Because `:transcript` (§11.4) now ships in V1, timing-only consumes whisper's word/segment timestamps: an energy-triggered candidate cut point is **re-timed to the nearest transcript boundary** (sentence/clause end or speaker-turn) and **suppressed if it would land mid-word/mid-utterance**. Cut *timing* thus keys off dialogue structure — the EditIQ signal that *beat* speaker-detection editing (product §5.5) — rather than energy transitions alone. This is expected to move timing-only from the ~2–4 spurious/min anticipated for energy-only in the 3–6 dB band toward the ≤ 1/min bar, and it is the concrete way the V1 learned-AI beat (transcript) strengthens the director's *most-likely-shipped* mode instead of only its best-case mode.
3. **Cut the director** (product §5.4 branch), keeping the manual multicam editor + smart-reframe.

**Crucially, the paid tier's value does not depend on the director clearing the gate.** Per product §7.1, the paid unlock is **the multicam editor itself** (the scarce good, exists nowhere else on Android) plus smart-reframe/take-review (§11) **plus whisper transcript cutting (§11.4, the one genuinely-learned on-device capability, spike-independent)**; the director rides along as an accelerant. So a red quality gate — or even the full <3 dB director cut — degrades a *feature*, not the *pricing thesis*, and the paid tier still ships two real learned/CV capabilities (transcript cutting + smart-reframe) that Blackmagic serves nowhere on an Android phone (product §1.1). There is no week-6 emotional cliff.

### 10.5 V1.5: highlight extraction ("cut me a Short") — designed now, built later

`:director` gains `proposeHighlights(transcript, vad, n): List<Segment>` — score windows by speech density × energy dynamics × sentence-boundary alignment; the top-n non-overlapping 30–60 s segments each render through the vertical preset (#15, §8.3) with an SRT sidecar (#16, §11.4). That is Opus Clip's cloud product assembled on-device from shipped V1 parts — shoot a 40-minute conversation, leave with the episode and three Shorts, nothing uploaded. **Deliberately V1.5** (named alongside USB/lav audio input, §7.4): no new module, no new model, no new subsystem, so deferring it costs no architecture — naming it now shapes the roadmap story (product Addendum A.3). The one open design question, deferred with it: the segment-*ranking* quality bar, and whether a Gemini-Nano-class reranker (AICore, flagship-gated, non-AI fallback = VAD ranking) earns its place.

---

## 11 — On-device model table, face-detector geometry, and the reframe tracker pipeline

**Objection 1 (on_device_ai): BlazeFace SHORT-RANGE (<2 m) was chosen for talking-head geometry, but the guaranteed hero (Beat A) runs the punch-in tracker on the WIDE locked-off angle that frames BOTH speakers, where faces are likely >2 m — exactly where short-range is weakest. Objection 2 (creator_wow): the punch-in rides "a face detector" with no specified temporal smoothing/interpolation, so it risks reading as a jittery gimmick.** Both are confronted below.

### 11.1 Model table

| Ver | Feature | Model / method | Format | Delegate | Size | Inference (owned SoC) | Non-AI fallback |
|---|---|---|---|---|---|---|---|
| **V1** | Auto-sync | Windowed GCC-PHAT | DSP | CPU | 0 | < 0.1× realtime, est. | (is the default) |
| **V1** | Active-angle VAD | Gated energy VAD | DSP | CPU | 0 | < 0.1× realtime, est. | manual timeline |
| **V1 (PAID #12)** | **Face detection — WIDE reframe/punch-in path** | **MediaPipe Face Detector Task, BlazeFace FULL-RANGE** | TFLite (bundled, GMS-FREE) | GPU delegate; CPU fallback | **~1.5 MB model** | **~5–12 ms/frame GPU (est.); sampled frames** | fixed center crop |
| **V1 (PAID #12)** | Face detection — CLOSE take-review/framing | MediaPipe Face Detector Task, BlazeFace SHORT-RANGE | TFLite (bundled, GMS-FREE) | GPU delegate; CPU fallback | ~230 KB | ~3–8 ms/frame GPU (est.); sampled frames | off = neutral score |
| **V1 (PAID #12)** | Framing-quality + reframe heuristic | Deterministic on the bbox: size, thirds offset, luma clip, Laplacian focus + the §11.2 tracker | DSP/CV rules | CPU | 0 | < realtime, est. | off = neutral score |
| **V1 (PAID, `:transcript`)** | **Transcript cutting (§11.4) — post-record** | **whisper.cpp `tiny.en` (SHIPPED DEFAULT) · `base.en` (opt-in accuracy upgrade) · `tiny` (multilingual sessions)** | **GGML `q5_1` (the shipped quant)** | CPU (NEON); opt GPU | **~31 MB tiny.en · ~57 MB base.en · ~31 MB tiny** (`q5_1`; f16 ~75/~142 MB NOT shipped) | demo path: **≤1× realtime tiny.en on the owned tablet (named precondition, §11.4/§16.1)**; background bar ≤2× realtime; **post-record, not live** | manual transcript-free trim |
| **V2** | Active-speaker (fusion) | LR-ASD port (research risk) | LiteRT int8 | GPU delegate | ~1.0 M params, ~4 MB | measured-pending | audio-only director |
| **V2** | Subject seg (full reframe) | MediaPipe Selfie Seg (<2 m) | LiteRT | GPU delegate | small | measured-pending | fixed center crop |

### 11.1a Detector-vs-geometry: FULL-RANGE on the wide shot is the pre-committed default

**The reframe/punch-in path — the one Beat A depends on — ships BlazeFace FULL-RANGE**, because it operates on the wide locked-off angle where both speakers sit likely >2 m from the phone, outside short-range's reliable envelope. Short-range is retained only for the *close* take-review/framing path (a subject phone ~1–1.5 m from its speaker), where it is the correct, smaller model. **The week-1 face-detector spike (§16) explicitly validates FULL-RANGE detection on the wide-shot geometry on the owned SoCs' GPUs** — face recall on both seated speakers at the rig's actual wide distance, at the sampled frame rate, is a pass/fail line. If FULL-RANGE somehow underperforms on the owned GPUs, the pre-committed fallback is CPU-delegate FULL-RANGE (it is small enough), then short-range with a validated wider crop. The pricing-and-hero-critical feature #12 does **not** rest on an unverified detector-vs-geometry match.

### 11.2 The reframe tracker's temporal pipeline (objection: creator_wow — the punch-in cannot be "a face detector" alone)

A punch-in that jitters or lags on a 2–5 fps sampled-box stream reads as the gimmick design D7 fears. The virtual-camera move is made broadcast-plausible by a specified temporal pipeline between sparse detections and the emitted crop:

1. **Box smoothing/filtering.** The sampled BlazeFace boxes (2–5 fps) feed a **One-Euro filter** per box parameter (cx, cy, w, h) — chosen over a Kalman filter because One-Euro is a two-parameter (min-cutoff, beta) low-latency jitter filter designed exactly for interactive tracking of noisy human-motion signals and is trivial to author and tune; a constant-velocity Kalman is the documented fallback if One-Euro's lag under fast head turns is unacceptable. This removes per-detection jitter without the lag a naive moving average adds.
2. **Crop-path interpolation between keyframes.** The smoothed box drives a **crop keyframe** at each sample; the emitted crop path between keyframes is **Catmull-Rom (or monotone-cubic) interpolated** at the display/output frame rate (30 fps), so the virtual camera moves continuously even though detections are sparse — no stair-stepping at the sample rate.
3. **Maximum-velocity + damping bound.** The crop center and zoom are rate-limited to a **max pan/zoom velocity** (a fraction of frame-width per second) and **critically damped** toward the smoothed target, so a sudden detection jump (or a spurious box) produces a controlled ease, never a snap. A lost detection **holds the last crop** (does not recenter) until re-acquisition, then eases in.
4. **Output = a re-editable `CropTrack`** of crop keyframes (design 02 §6.6) — draggable, retargetable, deletable — not a baked pan.

**The quality bar the week-1 spike must clear** (so Beat A is de-risked before the sprint commits, not discovered at the demo): on the wide-shot rig geometry, after the pipeline, (a) residual crop-center jitter under a held pose **< ~0.5% of frame width**, (b) pan velocity on a normal speaker shift **within the damping bound with no visible overshoot**, and (c) end-to-end tracker lag behind a head movement **< ~150 ms perceived**. If the bar is not cleared, Beat A leads with Beat 9 instead (design D7), and the tracker ships as take-review-only. Beat 7 does not rest on "a face detector."

### 11.3 Native build / delegate config (whisper.cpp V1 + LiteRT V2)

- whisper.cpp: vendored under `:transcript/src/main/cpp`, CMake + NDK **r28+** (16 KB page size), `arm64-v8a`, NEON on. JNI: 16 kHz mono f32 PCM → word/segment timestamps. **V1 PAID; see §11.4.**
- LiteRT (LR-ASD): `LiteRtInterpreter` + `GpuDelegate`, int8; input = stacked face crops (from the same detector) + audio embedding; output = per-face speaking probability. Measured inference is a V2 spike.

**Runtime choices locked:** LiteRT + GPU delegate for V2 learned models, never NNAPI (deprecated Android 15) nor per-vendor NPU delegates. MediaPipe Tasks-Vision only for the Face *Detector* (GMS-free, V1); no MediaPipe ASD/Landmarker. whisper.cpp vendored, run post-record.

### 11.4 Transcript cutting (`:transcript`, V1 PAID) — the one genuinely-learned on-device capability

**Why it is in V1 (decision 2).** Pulling whisper.cpp transcript-driven cutting from V2 into V1 gives the paid tier its **one real learned-AI beat** — an answer to the ai_features objection that V1 otherwise carried only an energy-argmax angle picker. It is chosen because it is (a) **proven shippable on-device** (VN ships local Whisper free in 43 languages; Descript pioneered transcript editing on desktop), (b) **MIT-licensed with a clean patent surface**, (c) **fully on-device** (no cloud, thesis-consistent), and (d) landing on **exactly the ground Resolve cannot follow**: on-device transcript editing on an Android *phone*, which Blackmagic's Studio-gated, non-Android-phone Neural Engine does not serve (product §1.1). It is **spike-independent** — it does not touch SPIKE-AUDIO — so it survives the <3 dB director cut.

- **Model + size (the canonical table — all three specs must QUOTE this, not paraphrase it; round-5 fix, objection: on_device_ai — the three docs stated three different defaults).** **The shipped DEFAULT is `tiny.en` `q5_1` (≈ 31 MB)** — consistent with the product's English-first (`.en` weights) stance and the tightest thermal/latency envelope. **`base.en` `q5_1` (≈ 57 MB) is the opt-in accuracy upgrade.** `tiny` (multilingual, ≈ 31 MB) is selectable for non-English sessions; it is *not* the default. V1 bundles GGML `q5_1`-quantized weights, not f16 — for reference, the *unquantized* `f16` weights would be ~75 MB (tiny/tiny.en) / ~142 MB (base) — those are **not** what ships, and the earlier "~75/~140 MB, GGML int8/q5" label was wrong (it quoted f16 byte sizes under a quantized label). **This 03 table is the single owner: product §5.6 (which previously said "base.en is the default") and design §6.8 must match it verbatim — default `tiny.en` ≈31 MB, opt-in `base.en` ≈57 MB.** No LiteRT for whisper — **whisper.cpp is its own NEON-optimized C runtime**, built under NDK r28+ (§11.3).
- **On-device runtime.** **whisper.cpp native build**, `arm64-v8a`, NEON, CPU (optional GPU); **run POST-record, not live** — transcription happens on `Dispatchers.Default` after STOP (or on demand when the user opens the transcript), off the capture hot path, so it never competes with the two capture encoders (§7.2). Input is 16 kHz mono f32 PCM decoded from the chosen audio angle; output is **word- and segment-level timestamps**.
- **The transcript→cut UX.** After STOP, `:transcript` transcribes the single chosen audio angle (§8.2). The editor (design 02 §6) shows the transcript beside the timeline; **deleting a word/sentence range in the transcript deletes the corresponding time range from the program**, and cut points **snap to word-boundary timestamps** (transcript-driven trimming, the VN/Descript interaction). **Dead-air and filler-word ("um"/"uh"/long-pause) removal is a first-class one-gesture action here** — the transcript surfaces silences and fillers and a single select-and-delete drops them across the whole program. This is a **novel, spike-independent, non-commodity capability** absent from Resolve SmartSwitch (which *does not trim dead air*, product §1.1) and from every Android tool, which is why design 02 §8 may stage it as a demo **co-hero** beat rather than a supporting flourish — it reads as a new capability even to a buyer with no multicam history, because it needs no multicam context to land. The same word/segment markers also feed the director's timing-only cut placement (§10.4). Nothing is destructive — a transcript-driven trim edits the same `cuts`/decision-list object the manual editor and director emit (§8.2), re-editable and reversible.
- **SRT caption sidecar (#16, 2026-07-18) — with the source-time→program-time remap specified (round-5 fix, objection: ai_features — the prior draft never said which clock the sidecar ticks in).** `:transcript` gains `exportSrt(transcript, deletions): Uri` — pure-Kotlin formatting of the word/segment timestamps into `.srt` (sequence numbers, `HH:MM:SS,mmm` ranges, text), with these exact semantics, all via the single §8.2 map `P`: **(a) timestamps are emitted in PROGRAM time** — each kept word's take-time stamp passes through `P` so captions align with the exported video, not the raw take; **(b) words inside a deleted range are omitted**; **(c) a caption block whose take-time span crosses a deletion seam is SPLIT at the seam** into two blocks (one ending at the seam's program instant, one starting there), so no block claims time that no longer exists. Rippling every deletion through the sidecar is what makes the captions correct on the *tightened* video — the whole point of shipping them together. No native code, no model change, no gate. The export sheet's captions toggle (design 02 §B.2) defaults ON when a transcript exists, and the share sheet delivers video + sidecar together; caption text is whatever the transcript says *after* the user's edits, so fixing a misheard word in the transcript fixes the caption — one surface, no separate captions editor. **A sidecar carries no accuracy promise** — every platform lets users edit captions post-upload — which is why #16 ships while ***burned-in* captions remain out of scope** (the broadcast-caption WER bar does not apply to a sidecar).
- **Quality bar — with the demo-path runtime split from the background bar (round-5 fix, objection: on_device_ai — the ≤2× background bar and design 02's beat-8b choreography were written against each other).** (1) **Word-timestamp alignment** within **±150 ms** of the spoken word on the SPIKE-AUDIO test clip — timestamp *placement* is what the cut UX depends on, more than lexical perfection. (2) **Runtime, two named bars:** the **background bar** stays ≤ 2× realtime (a 40-min take transcribes in ≤ ~80 min worst case, backgrounded — fine off the hero path); the **demo-path bar is stricter and is a NAMED DEMO PRECONDITION: ≤ 1.0× realtime for `tiny.en` `q5_1` on the owned tablet SoC, measured as wall-clock on a ~60 s clip in the §16.1 whisper spike** — because design 02 §8.1's beat 8b (transcript ready ~28 s after STOP on a ~45 s take) is arithmetically feasible only at ≤ ~0.6× realtime, the choreography is **re-baselined against the measured week-1 number**: if the measured rate is ≤ ~0.6×, beat 8b stands as staged; if between 0.6× and 1×, beat 8b moves behind beat 7 with the visible `transcribing… ✓` affordance beat 5's finalising state already establishes; if `tiny.en` misses 1× (and `base.en` also does), the demo shows the wait honestly or time-skips with an on-screen elapsed-time chip — design 02 §8.1 owns which, but the *bar and the measurement* live here, so the two documents can no longer contradict. (3) **WER low enough to *navigate*** — the user is locating cuts by reading, not publishing captions, so roughly-correct words with correct timestamps clear the bar. Measured in the week-1 standalone spike batch (§16.1) on stock footage, since it needs no app scaffold.

---

## 12 — Thermal governor (the demo-killer, with an actual algorithm)

**Objection: the concurrent-encode load — the dominant heat source — was absent from the thermal model inputs, and the governor dropped proxy quality FIRST, which would shed the very proxy the hero moment depends on.** Both fixed. Blackmagic's Android users see ~10-min stops while charging; G3 wants 20.

**Inputs:** `PowerManager.currentThermalStatus` (polled 1/sec) — `NONE · LIGHT · MODERATE · SEVERE · CRITICAL · EMERGENCY · SHUTDOWN` — plus `addThermalStatusListener`, battery temp, charging state, **and the concurrent-encoder load (master + one proxy encoder, §7.2).** **The heat honesty (objection E):** the *dominant* heat sources are the **two hardware encoders + the camera ISP + charging**, which run full-rate through the early governor rungs; on-phone preview and the monitor network send are **comparatively minor** heat. So an ordering that sheds only preview + network first may not buy the minutes G3 needs — which is why the week-1 thermal spike must measure the **marginal runtime each rung actually buys** (below), not merely confirm "proxy survives to SEVERE."

**Predictive, degrades in a fixed order that protects BOTH the master AND the editing-proxy mux above monitoring/preview:**

| Thermal status | Action (in order; additive) | Master | Editing-proxy mux |
|---|---|---|---|
| `NONE` / `LIGHT` | Full config: HLG10 master, one SDR proxy encoder, live monitor 720p, 30 fps | untouched | untouched |
| `MODERATE` (first entry) | **Drop 1:** on-phone preview dim. **Drop 2:** monitor *network send* 720p→540p, 30→24 fps (proxy encoder still muxes full quality locally). **Drop 2b (CONDITIONAL — promoted encoder-load lever, objection E):** *if the week-1 spike shows preview+network shedding alone does not buy enough marginal minutes to reach G3's 20 min*, also drop the **proxy encoder 720p→540p HERE**, at MODERATE — an encoder-load lever moved ahead of SEVERE. Amber UI. | untouched | untouched, **or 540p if Drop 2b armed** |
| `SEVERE` | **Drop 3:** monitor network send → **still-refresh only** (stop live WebRTC transmission; proxy encoder keeps muxing). **Drop 4:** disable on-camera preview (screen is a heat source). Red UI. | **still recording** | **still muxing (protected)** |
| `CRITICAL` | **Drop 5:** master HEVC/HLG10 bitrate step down (keep 10-bit). **Drop 6:** proxy encoder resolution 720p→540p (still muxing). | **still recording** | still muxing, lower res |
| `EMERGENCY` | **Drop 7:** master 1080p→720p (a degraded master beats a lost take). Prompt to cool. | **still recording, degraded** | still muxing |
| `SHUTDOWN` (imminent) | Finalize both muxers *now* — flush valid files before the OS kills the process. | **finalized clean, then stop** | **finalized clean** |

**Stream-combo-fallback costs, recorded here as budgeted lines (the §7.2 cross-reference, not an "absorb").** If the §16.2 stream-combo spike is red and fallback (ii) — MediaCodec-transcode proxy off the master (§7.2) — is taken, its cost is carried explicitly, not silently: **thermal — est. +1 heat rung / ~3–5 fewer minutes-to-throttle** (spike-measured; if it pushes G3 under 20 min it arms the single-encoder analysis below), and **latency — est. +2–6 s** on the STOP→timeline hero if the transcode runs post-record (§6.4). Fallback (i), SDR-only capture, costs **zero thermal and zero latency** but degrades gate G8 to an 8-bit SDR master (§7.2). Neither is free; both are numbers, not assertions.

**Ordering principle (round-4 correction, objection E — shed heat where the heat actually is).** The old default (shed preview + network first, touch encoders only at CRITICAL) is only correct *if* preview + network shedding buys the minutes G3 needs. Since those are the *minor* heat sources, that is now an empirical question, not an assumption. So the rung order is **measured, not assumed:**

- **The week-1 thermal spike measures the MARGINAL runtime each rung buys** — how many additional minutes-to-throttle each of {dim preview, cut network send, proxy 720p→540p, master bitrate step, single-encoder} actually purchases on the owned phones, under a 20-min charging take. It does *not* stop at "proxy survives to SEVERE."
- **If preview + network shedding closes the 10→20-min G3 gap:** keep the conservative order (Drop 2b disarmed); the editing proxy is protected just above the master and survives to SEVERE, present at STOP for the hero (§6.4).
- **If it does not:** arm **Drop 2b** (proxy 720p→540p at MODERATE) — an encoder-load lever moved *before* SEVERE — and, as the strongest lever, pre-commit the **single-encoder + post-record-proxy fallback** (§7.2): drop the live proxy encoder entirely, generate the editing proxy post-record. **Numerically bounded hero-path cost (objection: on_device_ai — the fallback silently degraded the <2 s hero; now bounded).** Dropping the live proxy means the instant-at-STOP proxy no longer exists, so the STOP→timeline hero (§6.4) inserts a **post-record proxy-generation step of est. +2–6 s for a 40-min take** (the 540p/720p proxy transcodes at ~4–8× realtime off the just-finalized master), during which the operator sees an **explicit determinate progress affordance** — `building preview proxy… 40%` on the STOP→editor transition (design 02 §5.5). The timeline still appears the instant the *leader* proxy is ready (progressive per-tile paint, §6.4), so scrub begins before the last angle's proxy finishes. The worst-case floor is therefore a **bounded, labelled few-second build with a progress bar, not an unspecified hang**: the hero degrades from "instant" to "a few-second build," and that number (+2–6 s) is the pre-committed cost of surviving a two-encoder-can't-make-20-min result. Taken only if the spike proves the two-encoder heat cannot make 20 min.

Within all rungs: shed *bitrate* before *resolution* on the master; **never lose footage** (SHUTDOWN always finalizes valid files); the master is the last video output touched. Charging is a heat source: at SEVERE while charging the UI prompts to unplug.

**The demo-rig passive-cooling commitment, specified concretely (objection: on_device_ai — the demo-killer gate needs a fully-specified worst-case floor, not a qualitative "budgets passive cooling").** The G3 20-min-while-charging demo is shot with each camera phone on a **passive (fanless) finned-aluminium cooler**: specifically a **magnetic (MagSafe-compatible) finned aluminium cooling plate** (~$15–25), **attached to the phone's rear centre over the SoC/camera-module hotspot**, with the phone standing in open air on its tripod (not laid flat on a table, which traps heat). No active Peltier/fan cooler is assumed — those need power and are not part of a "phones in a bag" story. **Expected marginal minutes: passive dissipation of this class typically buys ~5–10 additional minutes-to-throttle** under sustained encode load. The week-1 thermal spike (§16.1) measures the *actual* marginal minutes on the owned phones **with and without the plate**, and that number decides whether the conservative rung order alone reaches 20 min or whether Drop 2b / single-encoder must arm. So the demo-killer gate has a fully-specified floor: `{measured two-encoder minutes} + {measured passive-cooling minutes}` vs 20; if short, the bounded single-encoder fallback above closes the rest at the stated **+2–6 s** hero cost.

**The oscillator-drift-rate curve the §4.5 sync budget depends on (documented here as a curve, so §4.5 cites a number, not this section's status list).** §4.5's piecewise re-correlation residual rests on a bound on how fast relative oscillator drift *rate* changes mid-take under throttling. That bound — the "documented worst-case throttle profile" §4.5 names — is this per-rung curve:

| Thermal rung | Assumed max drift-rate curvature `ċ` | Basis |
|---|---|---|
| `NONE` / `LIGHT` | ~0.2 ppm/min | near-isothermal; oscillator stable |
| `MODERATE` | ~0.6 ppm/min | gentle ramp |
| `SEVERE` | ~1.2 ppm/min | active throttle |
| `CRITICAL` / `EMERGENCY` | ~1.5 ppm/min | steepest ramp (worst case) |

Over a 10-min inter-anchor span the worst-rung midpoint residual is `ċ·T²/8 ≈ 1.1 ms` (§4.5 arithmetic) — inside the 33 ms one-frame gate by an order of magnitude. **These are assumptions the week-1 thermal spike (§16.1) measures and can falsify:** it records the actual mid-take drift-rate curvature per rung on the owned phones; if any rung exceeds its assumed `ċ`, the §4.5 re-correlation cadence tightens from ~10 to ~5 min (quartering the residual). This table is what §4.5 cites — a curve, not the status-rung list it was previously conflated with.

---

## 13 — Maintainability & dependency-risk register

### 13.1 The CompositionPlayer isolation boundary

```kotlin
interface EditPipeline {
    fun previewProgram(cuts: List<AngleCut>, angles: Map<Int, ExoPlayer>, into: SurfaceView) // active-angle ExoPlayer
    fun export(composition: Composition, out: String, cb: Transformer.Listener)              // Transformer only
    fun previewComposited(composition: Composition, into: PlayerView)                        // opt-in, gated on §16 spike
}
// Media3EditPipeline is the ONLY class importing CompositionPlayer / Transformer.
// The DEFAULT program preview (previewProgram) does NOT touch CompositionPlayer at all.
```

`previewComposited` and `export` are the only CompositionPlayer/Transformer users, both localized here. A Media3 bump touches one file; a retreat to §8.4 swaps one implementation.

### 13.2 Dependency-risk register

| Dependency | State | Trigger to revisit | Exit plan |
|---|---|---|---|
| **Audio-xcorr sync (DEFAULT)** | Authored DSP, standard GCC-PHAT | windowed leg too slow for §6.4 | Narrow window to 5 s; progressive per-tile offset paint (§6.4). Low risk — it *is* the baseline. |
| **Raw-Camera2 phase-lock port (UPSIDE)** | **Archived** (2026/2021), Pixel-era | §4.4 U2 red by end of week 1 | **Not attempted; default already ships.** Never in the core path. |
| **Media3 CompositionPlayer** 1.10.1 | @ExperimentalApi, unpublished (b/470355043), pinned | single-sequence seek spike red | Not on scrub path; export uses Transformer. Exit = ship without composited preview. |
| **Two-sequence Transformer EXPORT** | measured-pending on heterogeneous sources; **default = homogenize-to-common-intermediate (full per-clip transcode), not passthrough** (§8.3) | §16.1 export spike red (audio breaks at a boundary / G6 blown even with transcode) | Per-angle pre-render to the common format, then concatenate homogeneous items; then §8.4 compositor (V2). Passthrough is never the default it falls back *from*. |
| **Concurrent encoders (2)** | measured-pending | §16 encoder spike: three needed / two won't sustain | Monitor → still-refresh (two encoders); or editing proxy post-record if even two won't sustain (accepts slower STOP→timeline). |
| **MediaPipe Face Detector (BlazeFace full/short)** | Active, GMS-free, bundled | full-range fails wide geometry (§11.1a) | CPU-delegate full-range; then short-range + wider crop; then fixed center crop |
| **whisper.cpp** | Active, MIT, clean patent surface | tiny.en/base.en too slow or timestamp-inaccurate post-record on owned SoC (§11.4 bar) | **V1 PAID (`:transcript`), NON-CUTTABLE**; vendored; a red bar swaps model (tiny.en↔base.en) or **flexes the date** (§16) — the per-session fallback for a failed transcription is manual transcript-free trim, but the feature itself never leaves V1. **Spike-independent** — survives the SPIKE-AUDIO director cut. |
| **io.github.webrtc-sdk** | Community-maintained | maintainer stalls / no encoded-frame source | Monitoring only — still-refresh over TCP (also the two-encoder fallback) |
| **LiteRT + GPU delegate** | Production (TF 2.21) | delegate breaks | V2 only; fallback deterministic heuristics |
| **CameraX 1.5 + Camera2Interop (capture DEFAULT)** | Stable library; the quirk database is the asset | §16.2 stream-combo spike: CameraX cannot bind the 10-bit surface set | Raw-Camera2 fallback (§7.1) — same surface set, authored session code; only path the phase-lock upside can ride |
| **ffmpeg-kit / NNAPI / ML Kit** | **Not used** | — | N/A by design |

**Freshness audit:** the load-bearing archived/experimental bets are **CompositionPlayer** (off the scrub path, §13.1) and — only if attempted in slack — the **phase-lock port** (§4.4 go/no-go, default §4.5 carries the demo). Everything else in V1 core is a stable library, a small bundled model, or DSP we author.

### 13.3 Vendored vs authored vs library

| Component | Provenance |
|---|---|
| Transport (NSD/TCP/UDP), state machines | **authored** |
| Default sync (SNTP clock + audio-xcorr aligner) | **authored** (DSP) |
| Phase-aligner + raw Camera2 phase loop (UPSIDE) | **ported** (RecSync) + authored glue — slack only |
| HLG10 encode config, two-encoder capture | **library + framework** (CameraX 1.5 / MediaCodec; raw-Camera2 fallback §7.1) + authored config |
| Preview grid + program preview (ExoPlayer), export | **library** (Media3) behind authored `EditPipeline` |
| Face detector + reframe tracker pipeline | **library** (MediaPipe) + **authored** One-Euro/interpolation/damping (§11.2) |
| Fallback GL compositor | **authored** (bigflake/LiTr) — V2 / out of demo scope |
| VAD, FSM, framing heuristics, transcript→cut snap-to-boundary logic | **authored** (DSP) |
| whisper.cpp (`:transcript`, V1 PAID) | **vendored** |
| LiteRT models (LR-ASD, seg) | **vendored** (V2) |

---

## 14 — 4 → 6 angles (the V2 claim is not a rewrite)

- **Program output is two sequences (one video input) regardless of angle count.** `assembleProgram()` takes `List<AngleCut>` + `Map<Int, Uri>` + `coverage` — N angles is a map size.
- **The FSM** is `O(states² × windows)`; states = angle count. 4→6 is a parameter.
- **The decoder budget** is the CDD number: [5.1/H-1-2] guarantees 6 concurrent SDR 1080p30; the still-grid rung covers the 5th/6th tiles until a compositor exists.
- **The encoder budget** is per-phone and unchanged by angle count (each phone still runs two encoders); more angles = more phones, not more encoders per phone.
- **Transport and state machines** are already N-device.

---

## 15 — What must be true for V1 (the buildable summary)

1. **Default sync = audio cross-correlation (§4.5)** clears G2 (≤1 frame, minute-20 clapper) on the assembled timeline — <1 ms co-located, ≤ ~10 ms worst-case SEPARATED-rig (acoustic-propagation term, §4.5) — with the windowed leg inside the §6.4 STOP→timeline budget. **This is the core; it does not depend on the phase-lock port.**
2. The phase-lock upside (§4.1–4.4) is a **week-1 go/no-go** and weeks-7/8 slack only; a red U2 costs the demo nothing.
3. The decoder spike returns a rung on the §9 ladder that sustains ≥ a 2×2 monitor (or the still-grid demo floor). Measured-pending.
4. **The CAMERA-STREAM-COMBO spike (§16.2) confirms the `{HLG10 master + 10-bit-sampleable SurfaceTexture → GL tonemap → proxy + viewfinder}` surface set is admitted on both owned phones — attempted through the §7.2 named candidate ladder (CameraEffect/SurfaceProcessor → custom-SurfaceProvider Preview → raw Camera2), a red requiring ALL candidates exhausted (§7.1) — and the GL shader can sample 10-bit HLG (§7.2);** the concurrent-encoder spike then confirms two encoders sustain a 20-min charging take within G3. Pre-committed fallbacks, each with a **budgeted** cost, not an "absorb": SDR-only capture (degrades G8 to an 8-bit SDR master; zero thermal/latency) or MediaCodec-transcode proxy (est. +1 heat rung / ~3–5 fewer min-to-throttle, and +2–6 s on the STOP→timeline hero if post-record) for the stream combo; monitor still-refresh or single-encoder + post-record proxy (bounded +2–6 s hero) for the encoder count — none cascade (§7.2, §12).
5. **The two-sequence Transformer EXPORT spike (§16.1) confirms** a 20+ cut heterogeneous-source Composition exports — via the default homogenize-to-common-intermediate transcode (§8.3) — to a valid file with an unbroken audio track across every boundary, frame-accurate hard cuts, and G6 ≤1.0× *with* per-clip transcode. Measured-pending.
6. SPIKE-AUDIO ≥ 3 dB (§10). If <3 dB, `:director` is cut; V1 = synced capture + manual multicam cut + framing floor #12 + **whisper transcript cutting (§11.4)** — two spike-independent paid floors — clearing every hard gate. If the quality gate is red at ≥3 dB, the director degrades to timing-only against the §10.4 **falsifiable bar** (≤1 wrong cut point/min, transcript-informed), not off.
7. **Program preview is the active-angle ExoPlayer** (§8.1) — no CompositionPlayer on the scrub path. The conjured-angle punch-in scrubs via `ExoPlayer.setVideoEffects` `Crop` or the GL crop-shim fallback (§8.1). Single-sequence CompositionPlayer preview is measured-pending, opt-in upside only.
8. **whisper transcript cutting (`:transcript`, §11.4) ships in V1 paid** — post-record, spike-independent, the paid tier's one genuinely-learned on-device capability, landing where Resolve cannot follow on an Android phone (product §1.1). Its ~1–1.5 weeks of scope is budgeted out of former slack (§16), ahead of the phase-lock port in the drop queue.
9. **Vertical 9:16 export (#15) and the SRT sidecar (#16) ride shipped parts** — #15 is one more preset on the §8.3 Transformer path driven by the #12 `CropTrack` **with the §8.3 per-angle crop policy covering untracked segments**; #16 is pure-Kotlin formatting of whisper timestamps **remapped to program time through the §8.2 deletion map** (§11.4). Neither adds a spike, a model, or a gate; both are paid (they ride the paid reframe and transcript floors). **Transcript deletions themselves are first-class in the export model (§8.2): N deletions → N+1 clipped audio items + P-remapped cuts, with deletion-seam audio continuity a binary line in the §16.1 export spike.**

Most items are validated on owned hardware in week 1 (§16.1 scaffold-free batch + §16.2 infra-dependent phase); `:transcript` and the director land in the build weeks. Product code hardens only behind green spikes.

---

## 16 — Week-by-week execution plan

**Honest budget, corrected for bootstrap cost AND the added whisper scope (objection F + decision 2).** The prior "6 weeks build + 2 weeks slack" no longer holds cleanly, for two reasons the round-4 review is right about: (1) week 1 previously stacked ~10 spikes as if all were standalone, but several — the concurrent dual-encoder, the camera-stream-combo, the face-tracker quality bar — **secretly need week-2–5 capture infrastructure** and cannot run on a bare app; and (2) pulling whisper transcript cutting into V1 (`:transcript`, decision 2) adds **~1–1.5 weeks of real scope**. So the corrected shape is **≈6.5–7 weeks of build (bootstrap + whisper) + ≈1–1.5 weeks of slack**, and **the whisper scope consumes most of the former weeks-7/8 slack** — which is why the phase-lock port upside (§4.4) now sits *behind* whisper in the drop queue. Week 1 is split into a **scaffold-free spike batch (16.1)** and an **infra-dependent spike phase (16.2, ~1.5–2 weeks)**.

**The infra spikes are sequenced AHEAD of their dependent build weeks, not concurrent with them (objection: scalability).** A solo near-zero-code builder cannot run the camera-stream-combo / concurrent-encoder / face-tracker spikes *while* authoring the capture core those spikes validate — that serialization the schedule needs is one a solo builder cannot parallelize. So the plan serializes it honestly: **week-2 (transport + default sync) needs no camera stream combo and proceeds during the §16.2 spike phase; but the week-3 capture build CANNOT start until the §16.2 CAMERA-STREAM-COMBO spike is green**, because it builds the exact surface set that spike validates. The §16.2 phase is therefore the ~1.5–2-week gate *between* weeks 2 and 3, not an overlay on them. **Which protected item absorbs a §16.2 slip:** the phase-lock port (already tail/slack-only) is first, then the **opt-in composited CompositionPlayer preview** (§13.1) is pre-committed to drop — both sit outside the hard-gate spine, so a stream-combo spike that runs long pushes *upside* into the wall, never the G1/G3/G4/G5/G6 spine. Mirrored in product §5.7.

**An explicit debugging / learning-curve buffer for the solo near-zero-Android builder (objection: scalability).** The plan's own #1 named constraint is not implementation speed (Claude Code writes fast) but a near-zero-Android-experience solo builder *debugging* capture stream combos (CameraX Feature-Group binds — or raw Camera2 if the spike forces the fallback, §7.1), Media3 experimental surfaces, the JNI/NDK whisper build, and GL tonemap shaders. With whisper having eaten the former slack, the honest total carries **an additional ~0.5–1 week debugging buffer, distinct from spike-fallback slack** — bringing the committed envelope to **~7.5–8 weeks, not 7**. **Pre-committed trim if that buffer is consumed by a single multi-week debugging surprise on a protected item** (most likely the capture stream-combo integration — the CameraX Feature-Group bind, or the raw-Camera2 fallback if the spike forces it — the plan's hardest feasibility question), dropped in order: (1) the opt-in composited preview (already queued); (2) the director's gate-fail *timing-only* refinement polish (ship the simpler hold-longer degrade, §10.4); (3) the demo from 4 angles down to **2 angles** (the beachhead is 2 speakers; the timeline/export/whisper/reframe spine is angle-count-agnostic, §14). The hard-gate spine — synced capture + STOP→timeline + manual multicam cut + export + `:framing` + `:transcript` — is what the buffer protects; these three are the pre-committed relief valves, so a debugging surprise degrades scope, never blows the demo. **Past those valves, the date moves (locked decision: quality over calendar)** — the week numbers in §16 are sequencing estimates gated on go/no-go results, not calendar commitments; a red gate flexes the schedule, never silently shrinks the guaranteed deliverable (which includes `:transcript` — whisper is non-cuttable).

### 16.1 Week 1a — standalone spike batch (pre-recorded stock-camera footage, NO app scaffold)

These run on footage shot with the phones' stock camera apps (or a throwaway ~50-line capture harness) and need none of the app's transport/capture/session code. They retire the highest-uncertainty *media* questions before any scaffold exists:

| Spike | Method | Closes | If red |
|---|---|---|---|
| **Decoder-budget** | AOSP CTS `MultiDecoderPerfTest` / `MultiCodecPerfTestBase` harness (§9) on stock 1080p clips | picks §9 rung | → 540p / still-grid rung |
| **SPIKE-AUDIO** | per-angle RMS delta on a 3-min two-phone conversation (§10, product §5.4) | director go/no-go | → director cut / conservative |
| **Two-sequence heterogeneous EXPORT** (week-1 PRIORITY, objection G) | 20+ cuts, ≥2 DIFFERENT source files (SoC/res/color), full Transformer export **with the default homogenize-to-common-intermediate transcode** (§8.3): valid file, **audio unbroken across EVERY video-item boundary (explicit binary pass/fail)**, **PLUS a separate binary line: audio continuity across TRANSCRIPT-DELETION seams** — a run with ≥3 `Deletion`s whose N+1 clipped audio items (§8.2) must join with **zero gaps, clicks, or resyncs at every seam** (a *different* failure surface from video-switch boundaries: there the audio is continuous inside one item; at a seam, clipped audio items CAN gap/click) — hard-cut frame accuracy, **G6 ≤1.0× WITH per-clip transcode**; the vertical pass includes ≥1 untracked-angle segment (static-crop policy, §8.3) | export go/no-go (incl. #14's seam surface) | → per-angle pre-render then concatenate |
| **4-player frame-lock** (§8.1a) | 4× ExoPlayer on the owned tablet at the chosen proxy rung: scrub-snap agreement across tiles; playback divergence stays inside the 33 ms deadband over a 5-min play with the master/follower discipline loop (poll 500 ms, trim ±2%, resnap >120 ms) | the editor's substrate + hero moment | → tighten poll cadence / resnap-only discipline; if a rung can't hold lock, drop a §9 rung |
| **Single-sequence CompositionPlayer preview+seek** | across many concatenated clipped items (§8.1) | composited-preview go/no-go | → active-angle ExoPlayer only (already the default) |
| **Thermal baseline + marginal-runtime** (objection E) | idle→load curve per phone; measure the **marginal minutes each governor rung buys** toward the 10→20-min G3 gap (§12) | thermal model + rung order | → arm Drop 2b / single-encoder fallback (§12) |
| **Audio-xcorr baseline** | windowed GCC-PHAT accuracy+latency on 2 stock takes (§4.5, §6.4), incl. the **piecewise ~10-min re-correlation** residual under a warm take (objection C) | default-sync validation | → narrow window / tighten cadence to 5 min |
| **whisper transcript** (§11.4) | `tiny.en`/`base.en` `q5_1` word-timestamp accuracy + runtime on a stock clip; **MUST include tiny.en WALL-CLOCK on the OWNED TABLET SoC for a ~60 s clip — the §11.4 demo-path precondition (≤1× realtime) that design 02 §8.1's beat-8b timing is re-baselined against** | `:transcript` model choice + quality-bar validation + demo choreography input | → swap model (tiny.en↔base.en) / tune; demo re-choreographs per §11.4 if >1×; a persistent quality red **flexes the date** — whisper is non-cuttable (§16.3) |

### 16.2 Week 1b–2 — infra-dependent spike phase (~1.5–2 weeks, needs a minimal capture scaffold)

These cannot run on stock footage — they need a small capture harness on the CameraX-first stack (the seed of `:capture`, §7.1; only the phase-lock go/no-go uses a throwaway raw-Camera2 session), so they are budgeted honestly as ~1.5–2 weeks straddling into week 2, **not** free week-1 items:

| Spike | Method | Closes | If red → pre-committed fallback |
|---|---|---|---|
| **CAMERA-STREAM-COMBO** (objection A — the hardest feasibility question, split OUT of the encoder-count spike; **candidate bindings NAMED, §7.2 — a red requires ALL exhausted, never one naive bind failure**) | On BOTH phones, in priority order: **(1) CameraX `CameraEffect`/`SurfaceProcessor` targeting `PREVIEW \| VIDEO_CAPTURE`** — app GL receives the stream once, fans out viewfinder + HLG10→SDR tonemap→proxy encoder (HAL surface count = 2); **(2) `Preview` with a custom `SurfaceProvider`** handing an app-owned `SurfaceTexture` (app GL draws viewfinder quad + proxy input from one tap); **(3) raw Camera2 three-surface session** via `getMandatoryStreamCombinations` / `isSessionConfigurationSupported` — a green at (3) only is what admits §7.1's raw fallback. Then confirm the GL shader **samples 10-bit HLG** (`GL_EXT_YUV_target` + `EGL_EXT_gl_colorspace_bt2020_hlg`) | stream-combo feasibility + capture-stack decision (§7.1); spike starts at candidate (1) | **SDR-only capture** (drop 10-bit-GL path), OR **MediaCodec-transcode proxy** off the master — one lever moves, master+proxy+monitor do NOT fail together (§7.2) |
| **Concurrent-ENCODER count** (now measures encoder *count* only, not stream combo) | master HEVC/HLG10 + one SDR proxy via `configure()`/`start()`, sustained 20 min charging; + whether WebRTC consumes the pre-encoded proxy or needs its own encoder (§7.2) | encoder-count decision, G3 heat | monitor → still-refresh (2 encoders); or editing proxy post-record if even 2 won't sustain |
| **Face-detector: BlazeFace FULL-RANGE on the WIDE-shot geometry** + §11.2 tracker quality bar | recall on both seated speakers at rig distance on owned GPUs; tracker jitter/lag/velocity bar (§11.1a, §11.2); also exercises `ExoPlayer.setVideoEffects` `Crop` for the CropTrack scrub path (§8.1) | Beat A go/no-go | CPU full-range / short-range+wider crop / Beat 9 leads; GL crop-shim if setVideoEffects buggy |
| **Hardware audit** | Q9 phone brands, D2 foldable, **MPC≥34 `HARDWARE_LEVEL_FULL` fraction** (§7.1); **+ one-line live-3A check (§4.0): does `Camera2CameraControl.setCaptureRequestOptions()` apply `SENSOR_SENSITIVITY`/`SENSOR_EXPOSURE_TIME` on the LIVE repeating request mid-take on each owned phone, or does the device drop/ignore live updates (→ re-arm fallback UI, design 02 §4.3 / Appendix A row 4)** | fidelity-claim inputs incl. live-vs-re-arm manual-3A state | acquire differing-brand phone / drop Tier-2; re-arm UI only where measured necessary |
| **Phase-lock go/no-go** U0–U2 clapper (§4.4) — **dated decision, UPSIDE only** | RecSync `PhaseAligner` on a throwaway raw-Camera2 session (the upside's required stack, §7.1), clapper ≤1 frame | phase-lock go/no-go | **not attempted; default audio-xcorr already ships** |

### 16.3 The build weeks

| Wk | Primary build | Subsystems | Gate/spike closed | If red → CUT |
|---|---|---|---|---|
| **2** | Transport + **default sync** (runs *during* the §16.2 spike phase; needs no camera stream combo) | `:core:transport` (NSD/TCP/UDP), device state machine (§5.1); **QR pairing — transport half (~1–1.5 d): `HELLO{token}` path, MODE-1 payload, MODE-2 `WifiNetworkSpecifier` join + `Network`-bound sockets (§5.4)**; `:core:sync` SNTP + audio-xcorr aligner incl. piecewise re-correlation (§4.5) | (G2 substrate) | Sync is the *default* — **no port risk this week**; QR transport slips → NSD discovery-list path only (§5.4 valve, zero gate impact) |
| **3** | Capture + local record + resilience — **GATED: cannot start until §16.2 CAMERA-STREAM-COMBO is green** | `:capture` CameraX HLG10 (raw-Camera2 fallback only if the spike forced it, §7.1) → master + one SDR proxy encoder (the validated stream combo, §7.2) → MediaMuxer; arm/roll/stop (G1); fault tolerance + rejoin reconciliation (G4, §5.1a); thermal governor with measured rung order (G3, §12) | **G1, G3, G4** | — (core, uncuttable); a §16.2 slip pushes this right and is absorbed by dropping upside (phase-lock, opt-in preview), never the spine |
| **4** | Monitor + controller | 4× ExoPlayer grid or fallback rung (§9); WebRTC monitoring off the shared proxy stream (§6.1, §7.2); controller session machine (§5.2); **QR pairing — UI half (~1–1.5 d): QR render on controller, CameraX `ImageAnalysis`+ZXing scanner on the camera join screen (§5.4)** | monitor viability | Slip → **single-tile / still-refresh** rung; QR UI slips → demo beat 2 degrades to the NSD discovery list (§5.4 valve — beat-2 script drops the QR flourish, zero gate impact; mirrored product §5.7 / design 02 §5.7) |
| **5** | Editor: timeline + preview + export | 4× ExoPlayer ribbon; **active-angle ExoPlayer program preview + CropTrack scrub path** (§8.1); two-sequence Composition (§8.2); **Transformer export with homogenize transcode** (G6) incl. the **vertical 9:16 preset (#15, §8.3)**; **STOP→timeline < 2 s incl. windowed-xcorr leg** (§6.4) + hero moment | **G5-ready, G6, hero, G7, G8** | Slip → drop opt-in CompositionPlayer preview; export-only |
| **6** | Director **or** framing floor + **transcript cutting** + polish | If SPIKE-AUDIO ≥ 3 dB: `:director` VAD+FSM (§10) + override UX + **§10.4 gate-fail degrade** (incl. transcript-informed timing). Always: `:framing` #12 (§11.1a/§11.2) **and `:transcript` whisper cutting incl. the SRT sidecar (#16, §11.4)** | **G5**, quality gate, transcript UX | SPIKE-AUDIO <3 dB → `:director` cut; gate red → timing-only (§10.4); `:framing` + `:transcript` still ship |
| **6.5–8** | **whisper integration + SLACK** | finish `:transcript` (whisper NDK build + transcript→cut UX, §11.4) if it slipped week 6; integration, gate validation, demo film; **iff §4.4 U2 green AND slack survives whisper: attempt the phase-lock port** | all gates re-verified; transcript quality bar | whisper eats the slack first; phase-lock port only if anything is left |

**Pre-committed cut ladder (drops first as weeks slip):**
1. **GL compositor (§8.4)** — never in scope. Demo floor is still-grid. *(before week 1)*
2. **Live phase-lock port** — never in the core; attempted only in the tail iff §4.4 green **and** whisper scope left slack. Default audio-xcorr carries G2. *(week-1 decision; now behind whisper in the slack queue)*
3. **Opt-in composited CompositionPlayer preview** — cut if the §16.1 spike is red or week 5 slips. *(week 1 / 5)*
4. **Full live WebRTC monitor** → still-refresh if the encoder spike shows three encoders unviable, or week 4 slips. *(week 1 / 4)*
5. **AI Director (`:director`)** — cut if SPIKE-AUDIO <3 dB; degrade to timing-only against the §10.4 falsifiable bar if the gate is red. `:framing` #12 **and `:transcript` transcript cutting** preserve the paid tier's real capabilities either way. *(week 1 / 6 decision)*

**Why this fits ~7.5–8 weeks, honestly.** The uncuttable core (weeks 2–3: transport + **audio-xcorr default sync** + capture + resilience) plus the editor (week 5) plus the framing/transcript/director week (6) is the hard-gate spine, and **every item in it is a mature, non-experimental path** (SNTP + our own DSP, MediaCodec, plain ExoPlayer, `Transformer` export, whisper.cpp — a proven on-device runtime VN and desktop Descript already ship). The bootstrap cost is paid honestly by splitting week 1 into a scaffold-free batch (§16.1) and an infra-dependent phase (§16.2, ~1.5–2 weeks) **sequenced ahead of — not concurrent with — the week-3 capture build it gates**. The added whisper scope is paid out of the former slack, which is why the highest-attrition subsystem (raw-Camera2 phase-lock) now sits *behind* whisper in the slack queue and remains fenced behind a dated go/no-go. The one variable the plan repeatedly names as its binding constraint — a near-zero-Android builder *debugging* experimental surfaces — is carried by the explicit **~0.5–1 week debugging buffer** above (envelope ~7.5–8 weeks), with a pre-committed scope-trim ladder (opt-in preview → timing-only polish → 4→2 angles) so a debugging surprise degrades scope, never the spine. The measured-pending capture/export risks (stream-combo + encoder count; heterogeneous export) are spiked before their build weeks with pre-committed non-cascading fallbacks whose costs are now budgeted numbers (§7.2, §12), so none can surprise the demo.

---

## Appendix — Signature cross-reference (all verified at androidx/media 1.10.1)

| Need | Exact API | Module | Note |
|---|---|---|---|
| Declare track types | `EditedMediaItemSequence.Builder(Set<@C.TrackType Integer>)` | transformer | ctor, not builder method; `(EditedMediaItem...)` ctor deprecated → silent `TRACK_TYPE_NONE` (b/445884217) |
| Video-only sequence | `EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))` + `setRemoveAudio(true)` per item | transformer | one video input; no compositor (§8.2) |
| Audio-only sequence | `EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))`, N+1 clipped items over the kept ranges (boundaries only at deletion seams) + `setRemoveVideo(true)` | transformer | does **not** invoke `MultipleInputVideoGraph`; audio continuity at video-switch boundaries AND deletion seams both binary lines in the §16.1 export spike (§8.2, §8.3) |
| Static track factories | `EditedMediaItemSequence.withVideoFrom(...)` etc. | transformer | **static factory methods on the class**, not builder methods |
| Multi-input graph | `MultipleInputVideoGraph.Factory()` | effect | no-arg ctor; **V2 grid-export only** — never set for the V1 two-sequence Composition |
| Compositor settings | `VideoCompositorSettings` | **common** | not `.effect`; V2 grid-export only |
| Overlay settings | `OverlaySettings` (iface, common) / `StaticOverlaySettings` (impl, effect) | common / effect | V2 |
| Set compositor | `Composition.Builder.setVideoCompositorSettings(...)` | transformer | V2 grid-export only |
| Opt-in | `@OptIn(UnstableApi::class, ExperimentalApi::class)`, import `androidx.annotation.OptIn` | common.util | LINT-enforced (`UnsafeOptInUsageError`); passes debug, fails release if missing |
| Player (export + opt-in preview) | `CompositionPlayer` extends `SimpleBasePlayer`, **final**, exposes `addAnalyticsListener()` | transformer | requires `media3-exoplayer`; **not on the default scrub path** (§8.1) |
| Program preview (default) | `ExoPlayer` on a `SurfaceView` | exoplayer | the active-angle surface at the playhead — no experimental API |
| Drop counts | `AnalyticsListener.onDroppedVideoFrames(...)` | exoplayer | aggregated across sequences (b/451741691) — no per-angle attribution |
| Camera keys/values | keys on `CameraCharacteristics`, value constants on `CameraMetadata` | android | 10-bit is a value in `REQUEST_AVAILABLE_CAPABILITIES` int[], not its own key |
| Per-frame phase control (UPSIDE only) | `CaptureRequest.SENSOR_FRAME_DURATION` / `SENSOR_EXPOSURE_TIME` on our own `setRepeatingRequest` | android (Camera2) | legal on a raw session we own (§4.0); impossible via CameraX; **default path uses a static request** |
| HLG10 (default stack) | `DynamicRange.HLG_10_BIT` on `VideoCapture` (+ Feature Groups) | camera-video | only 10-bit profile with AOSP mandate (API 33+); CameraX-first (§7.1) |
| HLG10 (raw fallback) | `OutputConfiguration.setDynamicRangeProfile(HLG10)` | android | raw-Camera2 path only (§7.1) |
| Manual 3A (FULL-gated) | `Camera2Interop.Extender` / `Camera2CameraControl` | camera-camera2 | manual ISO/shutter/WB on the CameraX stack; disabled-with-reason below FULL (§7.1, design 02 §4.3) |
| Thermal | `PowerManager.currentThermalStatus` / `addThermalStatusListener` | android | governor input incl. two-encoder load (§12) |
| WebRTC jitter floor | `RtpReceiver.setJitterBufferMinimumDelay(seconds)` / `playout-delay` RTP header ext | webrtc-sdk | low target, not a hard cap (§6.1) |
| Face detection | MediaPipe `FaceDetector` (Tasks-Vision), BlazeFace **full-range (wide) / short-range (close)**, GMS-free | mediapipe tasks-vision | full-range on the wide reframe geometry (§11.1a) |
| CropTrack scrub preview | `ExoPlayer.setVideoEffects(List<Effect>)` with `Crop` (+ opt `Presentation`) | exoplayer / effect | `@UnstableApi`; effect-on-preview less battle-tested than plain playback; GL crop-shim fallback (§8.1) |
| Stream-combination validation (stream-combo spike) | CameraX Feature-Group/`SessionConfig` bind FIRST; raw fallback `getMandatoryStreamCombinations()` / `isSessionConfigurationSupported(...)` | camerax / android | validate `{HLG10 master + 10-bit SurfaceTexture + preview}` before build (§7.1–7.2, §16.2) |
| 10-bit HLG GL sampling | `GL_EXT_YUV_target` `samplerExternalOES` + `EGL_EXT_gl_colorspace_bt2020_hlg` / `EGL_EXT_yuv_surface` | egl / gles | tonemap shader samples HLG10; not guaranteed per-SoC — stream-combo spike confirms (§7.2) |
| Transcript (V1 PAID) | whisper.cpp (vendored, NDK r28+, NEON), word/segment timestamps | `:transcript` (native) | **GGML `q5_1` weights that ship: `tiny.en` ≈31 MB (SHIPPED DEFAULT) · `base.en` ≈57 MB (opt-in) · `tiny` ≈31 MB (multilingual)** (f16 ~75/~142 MB NOT shipped); post-record; demo precondition ≤1× realtime tiny.en on owned tablet (§11.4/§16.1); not a Maven dep (§11.3, §11.4) |
| Vertical export (#15) | `MatrixTransformation` (CropTrack crop) + `Presentation.createForWidthAndHeight(1080, 1920, LAYOUT_SCALE_TO_FIT_WITH_CROP)` | effect | same Transformer path as §8.3; one more preset, no new gate |
| SRT sidecar (#16) | `exportSrt(transcript, deletions): Uri` — pure Kotlin, no platform API | `:transcript` | timestamps emitted in **program time** via the §8.2 map `P`; blocks split at deletion seams; deleted words omitted (§11.4); sidecar carries no accuracy promise |
| QR pairing MODE-1 (default) | payload `{controllerIp, port, sessionToken}`; ZXing core encode/decode + CameraX `ImageAnalysis`; straight token-primed TCP `HELLO{token}` — **no Wi-Fi join API** | zxing / camerax | both devices already on the AP; GMS-free; NSD remains the no-QR fallback (§5.4) |
| QR pairing MODE-2 (phone not on AP) | payload `{ssid, psk, controllerIp, port, sessionToken}`; `WifiNetworkSpecifier.Builder().setWpa2Passphrase()` + `ConnectivityManager.requestNetwork()`; **all session sockets bound to the granted `Network`** (`network.socketFactory`/`bindSocket`) | android | one system approval dialog per phone (choreographed, design 02 §5.7); **NSD unavailable in this mode**; `WifiNetworkSuggestion` NOT used (§5.4) |
| Live manual 3A mid-take | `Camera2CameraControl.setCaptureRequestOptions()` on the live repeating request (`AE_MODE_OFF` + `SENSOR_SENSITIVITY`/`SENSOR_EXPOSURE_TIME`, FULL devices) | camera-camera2 | live-first; re-arm UI is the measured fallback only (§4.0, §16.2 audit) |
| 4-player frame-lock | broadcast `seekTo` + `SeekParameters.CLOSEST_SYNC` (scrub); master/follower poll + `setPlaybackParameters(speed)` micro-trim / `seekTo` resnap (play) | exoplayer | authored discipline loop, not a library property (§8.1a); spiked §16.1 |
| Captioned Short playback (demo close) | `MediaItem.SubtitleConfiguration` pointing at the `.srt` sidecar on the in-app ExoPlayer | exoplayer | rendered live at playback; nothing burned in (§8.1; design 02 §7/§8.1) |
| AV1-preferred master encode | `MediaCodecList` query for a hardware AV1 encoder; HEVC Main10 otherwise | android | one query at session start; same MediaCodec surface code (§7.1) |
| Storage / export sinks | `Context.getExternalFilesDir` (masters/proxies); `MediaStore.Video` + SAF `ACTION_CREATE_DOCUMENT` (user export) | android | §7.3; USB-C SSD *recording* is V2 |

---

## Changelog

**2026-07-18 — Refinement round 1 (round-5 review objections addressed in place).** (1) **§8.2 program-time model:** transcript deletions are now first-class — `Deletion` set, take→program map `P`, audio as N+1 clipped items with boundaries only at deletion seams, `AngleCut` split/truncate/drop rules; the non-cuttable #14 is now expressible by the export assembly that ships it, and the §16.1 export spike gains an explicit **deletion-seam audio-continuity binary line** (a different failure surface from video-switch boundaries). (2) **§11.4 `exportSrt(transcript, deletions)`:** program-time emission via `P`, blocks split at seams, deleted words omitted. (3) **§5.4 QR pairing rewritten into two explicit modes** — MODE-1 `{controllerIp, port, sessionToken}`, no join API; MODE-2 `{ssid, psk, controllerIp, port, sessionToken}` via `WifiNetworkSpecifier` + `requestNetwork` with `Network`-bound sockets, system approval dialog choreographed, NSD unavailable in MODE-2; "suggestion" deleted, `WifiNetworkSuggestion` explicitly not used; network host named (infra AP default, LOHO contingency); **build cost scheduled** (§16.3 weeks 2+4, ~2–3 d) with the NSD-degrade relief valve. (4) **§7.2/§16.2 stream-combo spike now names its candidate CameraX bindings in priority order** (CameraEffect/SurfaceProcessor `PREVIEW|VIDEO_CAPTURE` → custom-SurfaceProvider `Preview` → raw Camera2); a red requires all exhausted. (5) **§4.0/§7.1 manual 3A corrected to live-first** via `Camera2CameraControl.setCaptureRequestOptions()` on the live repeating request; re-arm demoted to measured fallback (one-line §16.2 audit check; design 02 §4.3/Appendix A row 4 conditioned on it). (6) **§8.1a added:** the 4× ExoPlayer frame-lock is specified as authored code (broadcast seek w/ `CLOSEST_SYNC`; master/follower discipline — poll 500 ms, deadband 33 ms, ±2% micro-trim, resnap >120 ms) + a §16.1 spike line. (7) **§4.5 acoustic-propagation term added:** honest accuracy is ≤ ~10 ms worst-case SEPARATED rig / <1 ms co-located FLAT — both inside G2's 33 ms; drift anchors speaker-stabilized; product §5.1 #1 and design §3.2 must quote this. (8) **Whisper default unified: `tiny.en` `q5_1` ≈31 MB ships as DEFAULT, `base.en` ≈57 MB opt-in** (03 owns the table; 01 §5.6 / 02 §6.8 must quote it); **demo-path runtime bar split out as a named precondition** (≤1× realtime tiny.en on the owned tablet, ~60 s wall-clock measured in §16.1) with beat-8b re-baselining rules. (9) **§8.3 per-angle crop policy for the 9:16 preset** (untracked segments → one-detection static face-centred crop emitted as editable CropTrack keyframes; centre-crop fallback). (10) **Captioned demo close made honest:** in-app ExoPlayer `MediaItem.SubtitleConfiguration` renders the `.srt` live (surface-table + Appendix rows); nothing burned in — the burned-in-captions out-of-scope lock is untouched. No locked decision weakened; whisper remains guaranteed V1 non-cuttable.

**2026-07-18 — Addendum C integrated into the body** (companion: product Addendum A, design Addendum B). What moved where: **vertical 9:16 export #15** → §8.1/§8.3 (same Transformer path, one more preset); **SRT caption sidecar #16** → §11.4 (pure-Kotlin `exportSrt`, no accuracy promise); **capture stack inverted to CameraX-first** → §3, §4.0, §7.1–7.2, §13, §16.2 (raw Camera2 demoted to spike-gated fallback + phase-lock-upside substrate); **QR-primed pairing** → §5.4; **storage + audio-source spec** (app-scoped storage, MediaStore/SAF export, `CAMCORDER` 48 kHz source, AV1-preferred master encode) → §7.1, §7.3–7.4; **highlight extraction named V1.5** → §10.5 (with USB/lav audio V1.5 in §7.4; USB-C SSD recording, DeX controller, LR-ASD fusion remain V2). No scored content was weakened; the addendum text now lives in the sections above.
