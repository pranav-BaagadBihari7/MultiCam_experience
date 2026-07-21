# Design Spec — Multicam Capture & Cut for Android

> **Working title only.** "Final Cut" is an Apple trademark and cannot ship. See [01-product-spec.md §10](01-product-spec.md).

**Document status:** V1 screen-and-interaction spec. It exists to answer the two questions the product spec cannot answer in prose: *what does each screen look like*, and *will a working editor reject the rough cut in thirty seconds*. Every screen below is benchmarked against its Final Cut Camera / Final Cut Pro for iPad counterpart with an explicit **MATCH / BEAT / DIVERGE** verdict and a reason. Divergence without a reason is a bug in this document.

**Audience:** the solo builder, and Claude Code as the implementer. Wireframes are ASCII on purpose — they are unambiguous to a code generator and survive a `git diff`. Pixel comp work happens in Figma later (see [00-build-setup.md](00-build-setup.md), "Optional, later"); nothing here waits on it.

**Scope boundary with the other specs.** This document owns *layout, interaction, state-to-screen mapping, and the demo choreography*. It does **not** own API signatures, the latency budget table, the transport state machine, or the model table — those are [03-technical-spec.md](03-technical-spec.md). Where a screen depends on a number that only the week-1 spike can produce (proxy resolution, grid viability, single-sequence `CompositionPlayer` seek), this document specifies **both branches of the UI** so the design does not have to be reopened when the number lands.

**The one-sentence version:** Two phones become synced cameras a tablet watches; the instant you stop recording, a frame-locked multi-angle timeline is already on the tablet — a third angle can be conjured from a single locked-off phone with one tap — and if the audio-geometry spike passed, it is already cut.

---

## 1 — Design principles (five, and they are load-bearing)

These are not mood-board words. Each one resolves a specific decision downstream and is cited by section number where it bites.

1. **The camera never lies about whether it is recording.** A phone on a stand three metres away, screen possibly off, must communicate "rolling / not rolling / dropped" from across a room. This drives the tally system (§5.4) and the on-phone capture chrome (§4).
2. **The controller is a switcher, not a settings panel.** One operator, one glance, roll/stop reachable without hunting. This drives the monitor grid (§5) and forbids nested menus on the live path.
3. **The AI's cut is a *starting position*, never a *result*.** The rough cut must present as an ordinary, fully-editable multicam timeline that the machine happened to pre-populate — visually and interactionally identical to one a human assembled. This is the entire answer to "will an editor reject it in 30 seconds" (§6, §7) and it forbids any "flatten," "render auto-cut," or one-way apply step.
4. **Every automated decision is inspectable in one tap.** The #1 documented complaint about auto-directors is "you run it and hope." We answer it by making the *signal behind each cut* visible on demand (§6.5). Black boxes get rejected; glass boxes get trusted.
5. **Do not cache the display.** Foldables and connected displays move windows between physical screens mid-session; the `Display` object, metrics, and aspect ratio change under you. Every surface here is built from `WindowSizeClass` + `FoldingFeature` recomputed on each layout pass, never a cached constant. This is a hard requirement from the product spec's form-factor research, and it is a design constraint, not just a code note: **no screen may assume a fixed aspect ratio or orientation.**

---

## 2 — Responsive foundation & the two roles

The app is **one APK, one install, two runtime roles** chosen at launch and re-selectable: **Camera** (a phone, usually) and **Controller** (the tablet, usually — but any device can be either; a phone can control, a tablet can shoot). Role is a UI mode, not a build variant.

### 2.1 Breakpoints (WindowManager `BREAKPOINTS_V2`)

| Width size class | dp range | Primary use | Layout |
|---|---|---|---|
| Compact | < 600 | Phone as Camera; phone as pocket Controller | Single pane, full-bleed viewfinder or single monitor |
| Medium | 600–839 | Small tablet / unfolded foldable | Two-pane: grid + inspector |
| Expanded | 840–1199 | Tablet Controller (the main rig) | Three-zone: grid · program · timeline |
| Large | 1200–1599 | Large tablet / DeX window | Same as Expanded, wider timeline |
| Extra-large | ≥ 1600 | Connected display / desktop mode (V2 controller) | Expanded + docked device roster rail |

The Controller UI is **designed at Expanded** (the owned tablet) and degrades to Compact (phone-as-controller, a real fallback if the tablet is the thing that dropped — §5.6). Large / Extra-large are laid out now so the V2 connected-display controller is not a re-design, only a re-flow — this is the "4→6 angle / DeX controller extends without a rewrite" continuity the architecture review asked the specs to guarantee at the surface level.

### 2.2 Tabletop-fold posture (a natural camera fit — but hardware-conditional, resolved week 1)

When `FoldingFeature.State == HALF_OPENED` and the hinge is horizontal (tabletop posture), a Camera-role device splits at the fold:

```
┌───────────────────────────────┐
│                               │   ← TOP HALF (above the fold): viewfinder only.
│         VIEWFINDER            │     Peaking / zebra / overexposure overlays render
│      (pixel overlays live     │     HERE, on the image, because they are per-pixel.
│         on the image)         │
│                               │
├───────────────────────────════┤   ← THE HINGE
│  ●REC 00:12:04   ▮▮▮▮▯ -6dB    │   ← BOTTOM HALF (below the fold): the control deck.
│  [Peak][Zebra][Overexp] ▣48kHz│     Toggle chips, meters legend, record state, ISO/shutter.
│  ISO 400   1/50   WB 5600K    │     Touch targets live here so fingers never cross the image.
└───────────────────────────────┘
```

This is the posture Final Cut Camera cannot use (no foldable), and it is genuinely better ergonomics for a locked-off talking-head camera: the phone stands itself up, glanceable, hands-free. It is a **BEAT** vs FCP Camera, and it is one of two places V1 touches a true Android-only capability rather than a market-vacancy argument.

**But it is hardware-conditional, and the demo must not depend on a hardware fact that is currently unknown.** The product spec's Q9 (are the two owned phones different brands? — needed for the mixed-vendor BEAT) and this document's D2 (does any owned device fold? — needed for this BEAT) are both **unresolved hardware questions**, and the architecture review is right that letting a *demoed* differentiation claim rest on an unknown kit configuration is a trap. So this design pre-commits the resolution rather than deferring it:

- **Both Q9 and D2 are resolved in week 1, on the bench, not at the demo shoot.** Inventory the exact make/model of the two owned phones and the tablet on day one.
- **The demo's Android-only capability beat does *not* depend on either.** The one Android-only capability that is demoable on *any* two Android phones regardless of brand or fold — including the exact owned kit whatever it is — is **G4: a camera keeps recording through controller loss** (the Wi-Fi-pull beat, §8 step 4 / §5.6). Apple's Live Multicam structurally cannot do this — *both* apps must stay foreground — and it needs no foldable and no brand mismatch. That beat carries the Android-differentiation narrative on its own.
- **Mixed-vendor and tabletop-fold are staged as upside, gated on the week-1 inventory.** If the two phones turn out to be different brands, the demo *also* says so on camera (mixed-vendor rig, iOS-impossible). If a device folds, the demo *also* shows tabletop posture. If neither holds and both matter enough, acquiring one differing-brand phone or one cheap foldable is a sub-\$300 line item resolved in week 1 — or they are simply dropped from the narrative with zero loss, because G4 already anchors the Android column. **No demoed claim is left hostage to a hardware fact discovered at week 8.**

If neither phone folds, the tabletop split degrades to §4's flat layout with zero functional loss and is simply not shown.

### 2.3 The capture-rig friction contradiction — confronted, with two presets and a week-0 test

The beachhead (product §2) is defined by *avoiding* multicam because **post-production** is the friction. The prescribed capture rig (product §5.4) — one phone per speaker, ~1.5m off-axis, on stands, driven from a tablet — moves friction to the *front* of the shoot, onto the same friction-averse buyer. That is a real contradiction, flagged by the director review, and this document does not paper it. It resolves it two ways.

**Two capture-rig presets, and the low-friction one is the default.** The session screen picks a rig preset in one tap; nothing about capture is gated on getting geometry right.

| Preset | Setup | Delivers | Costs |
|---|---|---|---|
| **FLAT (default, ~10 s)** | Two phones near each other, both facing the speakers. No stands required — prop them, or two pocket tripods. Zero geometry discipline. | **Every spike-independent wow:** the synced timeline, the conjured second camera (§6.6), the finished export, G4. None of these needs inter-angle audio separation. | Co-located phones → small inter-angle energy delta → the **SPIKE-AUDIO < 3 dB regime** (product §5.4), so the **AI Director may be absent** — which is exactly the branch §9 already ships for. |
| **SEPARATED (upgrade, ~30 s)** | One phone per speaker, ~1.5m, off-axis, on stands — the product §5.4 geometry. | The inverse-square **audio delta the AI Director needs**, *and* more parallax between the two real angles. | ~20 s more setup. Presented in-app as "better audio + a more different second angle," an opt-in for the buyer who wants the machine cut. |

The SEPARATED rig is therefore *never a precondition for using the product* — it is the upgrade a buyer chooses when they want the director. The FLAT default keeps front-loaded friction to "stand two phones up," and everything the non-consumption buyer is shown in the hero (§8) survives it.

**One honest number the rig choice moves, stated so no screen overclaims it:** audio cross-correlation aligns acoustic *arrival* times, not capture clocks. On the co-located FLAT rig the mics sit together, so sync lands **<1 ms**. On the SEPARATED rig the mics sit metres apart, and acoustic propagation (~2.9 ms per metre of path-length difference to the dominant speaker) adds a **speaker-dependent bias of up to ~6–9 ms at the prescribed geometry — worst-case sync error ≤ ~10 ms**, still comfortably inside the G2 ≤1-frame (33 ms) gate. The error term, and the drift-anchor rule that prefers same-speaker-dominant windows, are owned by 03 §4.5; this document's copy (§3.2, §6.3) claims "≤1 frame," never sub-millisecond precision on the SEPARATED rig.

**The rig-friction question is answered by a lightweight wow sanity-check that *informs* the design — not a sprint GO/NO-GO gate and not a purchase-intent test (D7, §11).** The builder is building this sprint regardless — for the learning, the portfolio, and the product bet — so gating the sprint on a purchase-intent signal would be theatre: a null result would not stop the sprint, so nothing hangs on it. What *is* worth doing before the UI hardens is cheap and non-blocking — informally show the FLAT-rig setup *and* Beat A (the conjured camera) to a few real non-consumption producers and watch whether the punch-in reads as *capability* or *gimmick*, and whether standing up two phones reads as *fine* or *too much work*. That is an engineering check steering a design default, not a precondition for starting. If the punch-in reads as a gimmick, the design leans harder on Beat 9 (the finished export) and demotes the punch-in to a supporting flourish; if even standing up two phones reads as "too much work," the FLAT default gives way to a **single-phone conjured-camera** story — one phone, one stand, the punch-in *is* the second angle (§6.6), a genuinely one-device story with zero rig — with multicam demoted to the upgrade. The design supports either pivot at zero cost because Beat A already works from a single wide angle. No purchase-intent smoke-test is claimed or run.

---

## 3 — Screen: Library / Sessions (where takes live)

**This screen exists because the architecture review is correct: a fidelity claim against Final Cut cannot skip media/project management.** FCP's whole spine is **Library → Events → Projects**, and FCP Camera clips land in that library. A capture-and-cut product with no answer to *where do my takes live, how do I reopen last week's shoot, how do masters relate to proxies and to the cut* is missing a primary surface, not a detail. Here is that surface.

### 3.1 The data model, in one paragraph (so the screen is legible)

The unit of organisation is a **Session** = one shoot. A Session owns: the per-angle **master** files (HLG10, one per camera, each sitting **on the phone that shot it** — footage never leaves its capture device over the internet, product thesis / gate G7), the per-angle **proxies** (540p/720p SDR, generated post-record §4/§7 tech spec), the **sync-offset map** (leader-time offset per angle), and the **Composition decision-list** (the cut — the `List<AngleCut>` the editor and the AI Director both emit). A single JSON **session manifest** (owned by `:core:model`, tech spec §1) is the source of truth that ties them together: `sessionId → { name, date, angles[], where each angle = (angleId, deviceId, masterUri@device, proxyUri@tablet, durationUs, syncOffsetNanos) }, decisionListUri, exportsDir`. The manifest is what the Library browses and what reopening a session loads.

### 3.2 Layout (Expanded — the tablet, the library home)

```
┌────────────────────────────────────────────────────────────────────────┐
│  SESSIONS                                   [ + New session ]   [ ⚙ ]   │
│  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐                  │
│  │ ▣ thumb       │ │ ▣ thumb       │ │ ▣ thumb       │                  │
│  │ Ep. 14 · Ana  │ │ Sermon 07-12  │ │ Course L3     │                  │
│  │ 2 cams · 41:08│ │ 3 cams · 58:20│ │ 1 cam · 22:04 │                  │
│  │ ✓ cut · ⤓ x1  │ │ ● rough cut   │ │ ⚠ masters on  │                  │
│  │               │ │               │ │   CAM2 offline│                  │
│  └───────────────┘ └───────────────┘ └───────────────┘                  │
│                                                                          │
│  Selected: "Ep. 14 · Ana"                                                │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Angles   CAM1 "Host"  master ✓on-device · proxy ✓ · 41:08         │  │
│  │          CAM2 "Guest"  master ✓on Pixel-7 · proxy ✓ · 41:08       │  │
│  │ Sync     ≤1 frame · offsets locked ✓                              │  │
│  │ Cut      1 Composition · 22 cuts · last edited 3d ago             │  │
│  │ Exports  1 file → Movies/Multicam/Ep14/  (open filesystem)        │  │
│  │ [ ▷ Open in editor ]   [ ⤓ Re-export ]   [ ⧉ Gather masters ]     │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

On Compact (phone-as-controller) this reflows to a single scrolling list of session cards; the detail panel becomes a tap-through screen. Same data, no cached-metric assumptions (Principle 5).

### 3.3 The four things the reviewer demanded, answered explicitly

- **Where takes live / how they are stored and browsed.** Masters stay on the phone that recorded them, in the app's session folder on the **open filesystem** (not a sealed library bundle). The tablet holds the session manifest, the proxies (pulled over the **LAN**, not the internet, after STOP — a device-to-device copy, still "nothing uploaded, no cloud," gate G7 unaffected because G7 is zero *internet* egress), and the decision-list. The Library browses the manifest set: session cards sorted by date, each showing angle count, duration, cut state, export count, and — critically — **master availability** (a session whose masters live on a phone not currently reachable shows `⚠ masters on CAM2 offline`, so the user is never surprised at export time).
- **How a user reopens a prior multicam session.** Tap a card → `Open in editor`. The manifest loads: proxies (on the tablet) hydrate the 4× ExoPlayer angle ribbon and the program preview instantly; the decision-list restores the exact cut. **Re-export then splits into two paths, and the honest DIVERGE from FCP (§3.4) lives here.** A **proxy-quality re-export is always available from the tablet alone** — the proxies live on the tablet, so a reopened shoot can always be re-cut and re-exported at proxy resolution with no phones present. **Master-quality re-export is an explicit opt-in**: if a needed master lives on an offline phone, the card's `⧉ Gather masters` action prompts to reconnect that phone over the LAN and copies the needed master ranges — never silently, never to the cloud. The card's `⚠ masters on CAM2 offline` state is what tells the user, *before* they try, that master re-export will need that phone back and proxy re-export will not. This reconnection step is a workflow tax FCP's single managed-library model does not impose (§3.4).
- **How masters ↔ proxies ↔ the Composition decision-list associate.** Through the manifest's per-angle record (§3.1). The decision-list references `angleId` + timecodes only; those resolve against **proxies for preview/scrub** and against **masters for export** — the same decision-list, two resolutions. This is the single association that makes "edit on the tablet, export at master quality" coherent without gathering every master to the tablet up front.
- **Where exports land.** In the device's shared `Movies/Multicam/<SessionName>/` via `MediaStore` — the **open filesystem**, visible to every gallery and every other app on the device. A per-session `Exports` subfolder. No sealed bundle, no "you can only get it out through Share."

### 3.4 Library vs Final Cut Pro for iPad's library model — the benchmark

| Aspect | FCP iPad | Us | Verdict |
|---|---|---|---|
| Organisational tiers | **Library → Events → Projects** (three) | **Session** (one) | DIVERGE — a phone-multicam shoot *is* one event and one project; a three-tier desktop-scale hierarchy is organisation our beachhead (§2 product) does not have the volume to need. One tier reads instantly; three tiers is a filing system. Divergence with a reason. |
| Reopen a prior shoot (to edit / proxy-export) | Yes | Yes — tap a card, cut and proxies restore; proxy-quality re-export always available from the tablet alone | MATCH |
| **Master-quality re-export of a reopened shoot** | Always available — one managed library holds every master | **Requires physically reconnecting the phone(s) holding the masters (opt-in `⧉ Gather masters`, §3.3); proxy re-export needs no phones** | **DIVERGE** — our distributed-master model imposes a reconnection tax FCP's single-library model does not. Honest split: reopen-to-edit and reopen-to-proxy-export are MATCH; reopen-to-master-export is the DIVERGE we own. |
| Clips land in a browsable library | Yes (opaque managed bundle) | Yes (session manifest + open-filesystem masters) | MATCH interaction |
| Master ↔ proxy association | Managed internally, location opaque to user | Explicit per-angle in the manifest; master-availability shown on the card | MATCH function, BEAT transparency |
| Where media physically lives | Sealed library package | **Masters on their capture device; exports in open `Movies/`** | **BEAT** — no library jail. The open Android filesystem means your footage and your exports are *files you own*, reachable by any app, never trapped. This is the "never leaves the device / no library jail" story the product's Android-differentiation thesis names, and it is a real capability iOS structurally does not offer. |
| Proxy generation | Automatic, background | Automatic post-record (§7 tech spec) | MATCH |

The honest DIVERGE we own: FCP's three-tier library is *better* for a working editor juggling fifty projects across a season. We are deliberately not that; we are one shoot, opened, cut, exported, done — and the flat model is the right fit for the beachhead, not a shortcut. The BEAT we own — open-filesystem, no jail — is the one place the Library screen touches a true Android capability.

**The second honest DIVERGE, previously mis-labelled a MATCH: multi-device master availability.** FCP's single managed library keeps every master in one place, so a re-export at master quality is always one tap away. Ours does not: the masters are distributed across the phones that shot them (thesis / G7 — footage never leaves its capture device over the internet), so **reopening a shoot for a master-quality re-export requires physically reconnecting those phones over the LAN** — a real workflow tax FCP does not charge. We do not paper over it. The mitigation is that the *common* re-open cases pay nothing: editing and proxy-quality re-export are always available **from the tablet alone**, because the proxies and the decision-list already live on the tablet. Master re-export is the explicit opt-in that prompts for the offline phones (§3.3), and the Library card surfaces `⚠ masters on … offline` up front so the tax is never a surprise at export time.

---

## 4 — Screen: Capture viewfinder (Camera role)

The phone in someone's hand or on a stand. Its whole job: frame the shot, expose it, and never let the operator wonder if it is recording.

### 4.1 Layout (Compact, flat / handheld)

```
┌─────────────────────────────────────────────┐
│ ●REC 00:12:04            ▣ 1080p·HLG10 ⛰NONE │  status bar: record time · format · thermal
│                                              │
│                                              │
│              [ live viewfinder ]             │
│         zebra hatch on clipped highlights    │  ← overlays render on the image
│         red peaking on in-focus edges        │
│                                              │
│                                              │
│  CAM 2 · "Guest"          ▮▮▮▮▮▮▯▯ -6 dB ▁▂▅ │  ← device label + audio meter (retained)
├─────────────────────────────────────────────┤
│  ISO 400  1/50s  WB 5600K   [Peak][Zeb][OvrX]│  control strip: manual + overlay toggles
│              ◉ armed by Controller            │  arm/tally state from the network
└─────────────────────────────────────────────┘
```

### 4.2 The monitoring overlay set — and the FCP Camera benchmark

The product spec (§2.4) cut false colour, waveform, and histogram and kept focus peaking, zebras, overexposure, and audio meters. The architect objection reads this as "ships LESS monitoring than Final Cut Camera." **That framing is factually wrong, and the correction strengthens our position:**

| Overlay | FCP Camera ships it? | We ship it? | Verdict |
|---|---|---|---|
| Focus peaking | Yes | **Yes** | MATCH |
| Zebras | Yes | **Yes** | MATCH |
| Overexposure indicator | Yes | **Yes** | MATCH |
| Audio meters | Yes | **Yes** | MATCH |
| LUT preview | Yes | **No** | DIVERGE — there is no LOG pipeline on Android to preview a LUT *against* (product spec §3.1); shipping a LUT picker over HLG10 would be a control that does nothing. Cut for honesty, not scope. |
| False colour | **No** | No | MATCH (both absent) |
| Waveform | **No** | No | MATCH (both absent) |
| Histogram | **No** | No | MATCH (both absent) |

**The conclusion the reviewer's premise inverts:** Final Cut Camera *itself* ships no false colour, no waveform, no histogram. Our retained set is not "less than FCP Camera" — it is **FCP Camera's exact monitoring set minus a LUT preview that has no Android backing.** We are at deliberate near-parity with Apple's own capture app, and the "open territory" (scopes) is territory *Apple also declined*. That is a MATCH we can defend on camera, not a cut we have to apologise for. Scopes return only in V3's filmmaker tier (product spec §6), the same tier that would justify them.

### 4.3 Manual controls — designed around the Android constraint

Manual ISO / shutter / WB live in the control strip, and the design honours the platform facts *precisely* — including a correction this revision makes to an earlier overclaim. An earlier draft asserted that manual 3A is "settable only *before* use-case bind, not on a live repeating request," making every mid-take change a re-arm cycle. **That was stronger than the platform fact.** Two different CameraX interop surfaces exist: `Camera2Interop.Extender` build-time options are indeed pre-bind, but **`Camera2CameraControl.setCaptureRequestOptions()` — the API 03 §4.0/§7.1 itself names — applies capture-request options to the LIVE repeating request without rebinding.** With `AE_MODE_OFF`, `SENSOR_SENSITIVITY` / `SENSOR_EXPOSURE_TIME` are adjustable mid-take on `FULL` devices. The invented re-arm interruption was a self-inflicted capture-fidelity divergence vs FCP Camera's live manual dials that the platform does not force; it is corrected here and mirrored in 03 §4.0/§7.1.

Design consequence, made explicit so it is not discovered at week 5:
- On a device **without** `FULL`, the manual strip renders **disabled with a one-line reason** ("Auto only — this device does not expose manual capture"), never hidden. Hiding it makes two devices look like different apps; disabling-with-reason keeps the mental model intact.
- Changing ISO/shutter **before arming** is free. Changing them **while recording** is attempted **live** via `Camera2CameraControl.setCaptureRequestOptions()` — the primary path, a dial that turns while the take rolls, matching FCP Camera's live manual behaviour. The *re-arm* micro-interaction (brief "adjusting…" state) is retained as the **pre-designed FALLBACK state**, shown **only if** the owned device demonstrably drops or ignores live capture-request updates — a one-line check added to the week-1 hardware audit (03 §16.2). Both states are designed now so neither is improvised at week 5; *which one ships is a measured bench result, not an assumption.* This matches the technical spec's capture-path resolution (03 §7.1, **CameraX-first**: CameraX 1.5 + `Camera2Interop` with HLG10 and Feature Groups; raw Camera2 only if the week-1 stream-combo spike proves CameraX cannot express the {HLG10 master + 10-bit-sampleable SurfaceTexture + preview} surface set **after exhausting the named candidate bindings — the `CameraEffect`/SurfaceProcessor fan-out first, then a custom-`SurfaceProvider` SurfaceTexture tap — 03 §7.2/§16.2**).

**The `HARDWARE_LEVEL_FULL` population is a week-1 audit output, not an assumption — and the manual-control fidelity claim is honestly conditional on it.** Manual ISO/shutter/WB are *guaranteed* only on `HARDWARE_LEVEL_FULL` (product ground truth; 03 §4.4 V0, §7.1). **What fraction of the MPC ≥ 34 target population actually exposes `FULL` is unknown until the week-1 device audit measures it** — across the two owned phones first, then the MPC ≥ 34 model list. Two consequences this design states rather than hides:

1. **Below `FULL` = auto-only, a real capture-side gap vs FCP Camera.** On any camera device that does not report `FULL`, the manual strip is auto-only (disabled-with-reason, above) — a strictly *less capable* capture experience than Final Cut Camera, which exposes manual control broadly on Apple's tightly controlled hardware set. Our prescribed fixed-geometry rig (§2.3) is also less flexible than FCP Camera's general handheld capture. **Both divergences are logged explicitly in Appendix A (row 4); they are not silently assumed away.**
2. **Whether a below-`FULL` device can be a CAMERA at all depends on the sync branch.** The **live phase-lock** path (03 §4.0/§4.4) needs per-frame frame-duration control that co-travels with `FULL` — and the raw-Camera2 path, since CameraX cannot drive a per-frame repeating request — so it effectively requires `FULL` — a below-`FULL` phone is CONTROLLER-only there. The **audio-xcorr** path (03 §4.5) does *not* require per-frame control, so it *admits* auto-only cameras synced post-record. The design therefore renders **both** the FULL (manual strip live) and non-FULL (auto-only, one-line reason) capture states, and the demo is shot on whichever the owned phones actually expose — measured week 1, on the bench. **No fidelity or capability claim in this document silently assumes `FULL`.**

### 4.4 The arm / tally state on the phone

A Camera-role device shows a **full-width edge glow** keyed to network state, legible from across a room (Principle 1):

| State | Phone edge | Meaning |
|---|---|---|
| Discovered, not armed | dim grey | Controller sees it; not in the show |
| Armed | steady amber | In the show, ready to roll |
| Recording | **solid red, full perimeter** | Rolling. Matches broadcast tally-light convention. |
| Recording + is the live/program angle (editor picked it) | red perimeter + white corner ticks | "you are the one being cut to" |
| Controller lost, still recording | **red perimeter + slow pulse** | The critical fault-tolerance state — *still rolling, just alone* (§5.6, G4) |
| Storage/thermal warning | red perimeter + top-edge yellow | keep rolling, but the operator should know |

FCP Camera has no cross-device tally concept (its multicam monitoring is on the controller only). This is a **BEAT** born of the record-local architecture: because every phone is an independent recorder, each one *can* and *must* tell its own truth.

---

## 5 — Screen: Tablet Controller (monitor grid + switcher)

The rig's cockpit. One operator, standing, glancing. This is where the "no live switcher, but a great live monitor" divergence from a broadcast switcher is made concrete.

### 5.1 Layout (Expanded — the owned tablet, landscape)

```
┌────────────────────────────────────────────────────────────────────────┐
│  ● REC  00:12:04   |  4 cams · clocks locked ✓ sub-ms   |  ⛰ 1 warm  ⚙ │  session bar
├──────────────────────────────────────┬─────────────────────────────────┤
│  ┌────────────┐   ┌────────────┐      │   PER-DEVICE STATUS RAIL        │
│  │ CAM1 ●     │   │ CAM2 ●     │      │  ┌─────────────────────────┐    │
│  │  "Host"    │   │  "Guest"   │      │  │CAM1 Host   🔋82 ▤44GB ⛰○│    │
│  │  ▮▮▮▯ live │   │  ▮▮▮▮▮ live │      │  │CAM2 Guest  🔋61 ▤44GB ⛰◐│    │
│  └────────────┘   └────────────┘      │  │CAM3 Wide   🔋90 ▤120 ⛰○ │    │
│  ┌────────────┐   ┌────────────┐      │  │CAM4 —      offline 3s ⟳ │    │
│  │ CAM3 ●     │   │ CAM4  ⚠    │      │  └─────────────────────────┘    │
│  │  "Wide"    │   │  reconnect… │      │                                 │
│  │  ▮▮▮ live  │   │  last frame │      │   [ + Add camera ]  [ Layout ▦ ]│
│  └────────────┘   └────────────┘      │                                 │
├──────────────────────────────────────┴─────────────────────────────────┤
│              ◉ ROLL ALL              ◼ STOP ALL          ⏱ resync in 6:12 │  transport
└──────────────────────────────────────────────────────────────────────────┘
```

### 5.2 The grid, and the felt-latency reality

The grid is **4× WebRTC low-bitrate proxy tiles, monitoring only** (product spec §5.1). Design rules that follow directly from the transport truth:

- **Every tile carries its own freshness state**, because WebRTC degrades quality to preserve latency and a frozen tile must never masquerade as a live one. A tile that has not received a frame within its budget dims and shows `last frame · 0.4s` — it does **not** hold a stale image silently. The glass-to-glass and degradation-ceiling numbers this UI keys off (target ≤ 250 ms, hard ceiling before a tile is declared stale) are owned by [03-technical-spec.md](03-technical-spec.md)'s latency budget; this screen only renders the states that budget defines. That budget also carries the technical spec's concrete WebRTC config (target playout delay, loss-vs-latency tradeoff on real Wi-Fi) — this screen renders the *stale* state that config produces when the tradeoff goes against us, so a glitchy link degrades visibly rather than lying.
- **Per-angle decode health is shown at the tile, but per-angle *drop attribution* is not claimed.** Media3's in-source TODO (b/451741691) aggregates dropped-frame counts across all sequences — you cannot attribute a drop to a specific angle in the compositor path. The monitor grid sidesteps this entirely by using **independent per-tile WebRTC decoders** (not the compositor), so each tile's freshness *is* independently known. This is a design reason to keep the grid off the compositor, reinforcing the §6 architecture.
- **Layout button** re-flows 1/2/3/4-up. At 4 cams the grid is 2×2; at 2 cams it is side-by-side. This is rendered as **four independent Views**, never a composited single frame — the same insight that lets the grid dodge the CompositionPlayer compositor bugs (product spec §5.3).

### 5.3 Monitor grid vs Final Cut Camera's monitor — the benchmark

| Aspect | FCP Camera controller | Us | Verdict |
|---|---|---|---|
| Live multi-view of all angles | Yes | Yes | MATCH |
| Proxy resolution for monitoring | 720p hard ceiling | 540p/720p proxy (spike-set) | MATCH — Apple proves 1080p monitoring was never required |
| Per-device remote control (roll/stop, format) | Yes | Yes (roll/stop all; per-device arm) | MATCH |
| Per-device **thermal / storage / battery** on the grid | Minimal | **Prominent status rail** | BEAT — our beachhead shoots long-form in a warm room; thermal is a demo-killer (product spec §9.2), so it earns first-class real estate |
| Live switching *while recording* | No | No | MATCH — and deliberately (product spec §5.2): record-local means there is no live program to switch, and offline cutting gets lookahead a live switch can't |
| Camera keeps recording if controller drops | **No — Apple both apps must stay foreground** | **Yes** | BEAT — the G4 fault-tolerance win, §5.6, and the Android-only capability that carries the demo regardless of hardware (§2.2) |

The honest divergence: we are **not a live switcher** and the UI must never imply we are. There is no "PGM/PVW" bus, no T-bar, no take button that cuts a live program. The transport bar says `ROLL ALL / STOP ALL`, not `TAKE`. Selling this as a switcher would write a cheque the record-local architecture refuses to cash.

### 5.4 Tally, mirrored both directions

The controller tile border mirrors the phone edge-glow (§4.4): red = recording, amber = armed, grey = idle, pulsing red = recording-but-controller-was-lost-and-recovered. One visual language on both ends of the wire so the operator and the on-camera subject read the same truth.

### 5.5 Roll / stop, and the < 500 ms gate

`ROLL ALL` and `STOP ALL` are the only two persistently-reachable actions on the live path (Principle 2). Both fire to all devices over the TCP control channel and must land within the G1 spread (< 500 ms across devices). The button does **not** show "done" until it has heard acknowledgement from every armed device; a device that does not ack within the window flips to a warning state in the rail rather than leaving the operator guessing. The control-command RTT that this < 500 ms budget decomposes into is specified in [03-technical-spec.md](03-technical-spec.md); this screen owns only what the operator sees while waiting.

**One state this screen renders that the reviewer flagged as unbudgeted: the STOP → timeline-present transition.** When the operator taps `STOP ALL`, the screen shows a brief, honest `finalising…` state per device (muxer flush + finalized-file ack), then transitions to the editor with the timeline already assembled. The <2 s budget for this transition, decomposed leg by leg (per-device muxer finalize, ack collection, 4× ExoPlayer prepare/first-frame, timeline assembly from local durations+offsets), is owned by [03-technical-spec.md §6](03-technical-spec.md); this screen owns only the `finalising…` affordance that makes the wait legible instead of a mystery spinner. The hero moment (§8) depends on that budget being met, so the design surfaces the wait rather than pretending it is zero.

**The <2 s transition is identical in BOTH sync branches — the design does not let the audio-xcorr fallback silently blow the gate.** In the live phase-lock branch the inter-angle offsets are already known at STOP. In the audio-xcorr fallback branch (03 §4.5) they are not — but the offset is recovered by a **fast windowed** GCC-PHAT pass over only the first ~10 s of each angle's audio (03 §4.5/§6.4), which resolves within the same STOP→timeline budget rather than the minutes a whole-take pass would take. So the `finalising…` affordance and the <2 s gate are the same screen state and the same wait in either branch; the hero-moment gate (product §8) is measured against both, not only under live phase-lock. This screen never shows an *unsynced* timeline in <2 s, and it never waits minutes for a full-take correlation — a distinction the design owns because it choreographs the wait.

### 5.6 The fault-tolerance UX (Gate G4 — the free BEAT, and the demo's Android anchor)

Blackmagic's cameras "error and cut" when the controller is lost; this is a named complaint about the market leader and it is nearly free for us to beat because every device already records locally — and, structurally, because a camera-role phone keeps its recording alive in an Android **foreground service** even when the controller link dies, which is exactly the capability Apple's Live Multicam lacks (both apps must stay foreground). The design makes the recovery *legible* rather than silent:

```
CONTROLLER SIDE (tablet regains a camera)          CAMERA SIDE (phone loses controller)
─────────────────────────────────────────          ───────────────────────────────────
CAM4 tile → ⚠ "reconnecting… holding last frame"    edge glow → red + slow pulse
     ↓ (backoff reconnect succeeds)                 banner → "Controller lost — still
CAM4 tile → ● live, brief green "rejoined ✓"                    recording. Your files are safe."
     files never interrupted                        ↓ (rejoin)
                                                    banner clears; glow returns to steady red
```

The one inviolable rule surfaced in UI copy: **"still recording."** A camera that loses the controller keeps rolling and says so; it never stops on its own. Split-brain (two controllers claim the session), storage-full mid-take, and clock-resync-failure are **behavioural** state transitions owned by [03-technical-spec.md](03-technical-spec.md)'s session state machine — this document specifies only their *screen states*: each surfaces as a distinct, non-modal banner on the affected device (never a blocking dialog on the live path, which would be its own failure). The resync countdown (`resync in 6:12`, §5.1) makes the ~10-minute clock-drift re-sync visible so the operator is never surprised by it.

**What rejoin does to the timeline, not just the file — the G4 claim finished end-to-end.** The android_differentiation review is right that G4 is only as strong as the *timeline* it produces: "file intact" is not the whole claim. A camera that was off-network for part of a take kept recording locally (its file is unbroken), but while it was gone it **missed its ~10-minute SNTP re-sync cycles**, so its clock offset may have drifted and 03 §5.1's session machine marks it `SYNC_DEGRADED`. On rejoin the design requires — and surfaces — an explicit reconciliation, not a silent merge:

- **Offset re-derived on rejoin, never assumed.** Because every phone always recorded its own 48 kHz audio locally (03 §4.5), the rejoined angle's true inter-angle offset for the degraded stretch is recovered by a **fast windowed audio cross-correlation** to <1 frame — independent of whether live phase-lock ever held. The controller shows a brief `re-syncing angle…` on that tile, then `re-synced ✓`.
- **The degraded angle is flagged into the editor.** It carries a `SYNC_DEGRADED → re-synced` provenance that the editor renders as a badge (§6.7), so downstream the editor knows this angle's alignment came from audio re-derivation, not capture-time phase-lock.
- **Coverage gaps are represented, never faked.** If an angle genuinely lacks *frames* for a span (it started late, or a storage-floor forced a stop-and-resume, 03 §5.1), that span is a hatched "no coverage" region on that angle and is excluded from the `AngleCut` candidate set for that time (§6.7). The reconciliation of a controller-loss/rejoin angle into the assembled timeline — offset re-derivation, `SYNC_DEGRADED` flagging, and coverage-gap representation — is specified for the assembly side in 03 §5.1/§8.2 and for the *screen* side here and in §6.7. The demo (§8 Beat 4/6) ends by showing the pulled camera's footage **correctly placed on the synced timeline**, not merely "file intact."

### 5.7 Pairing: QR-first, discovery-list fallback — two modes, one choreography

The pairing flow's discovery-list step has a faster default path: the tablet shows a **QR code**; a phone scans it from the Camera-role join screen and lands in the session in one gesture — no network picker, no device-list ambiguity, no "which of these three CAM2s is mine." An earlier draft encoded `{ssid, sessionToken}` and hand-waved a "`WifiNetworkSpecifier` suggestion flow" — a payload that could neither join a secured network (no PSK) nor open a TCP connection (no address), naming two different Android APIs as one. Corrected: the flow has **two explicit modes**, and the choreography differs between them because one of them puts a *system dialog* on camera:

- **MODE-1 (default — matches the infra-Wi-Fi transport premise, 03 §5).** All devices are already on the same AP. The QR carries **`{controllerIp, port, sessionToken}` only** — no Wi-Fi credentials, no join API touched. The phone scans and TCP-connects straight to the controller with the token-primed `HELLO{token}` handshake, skipping the NSD browse entirely. **No system dialog appears.** This is the demo path (§8.1 beat 2).
- **MODE-2 (phone not yet on the AP).** The QR additionally carries **`{ssid, psk}`**; the phone joins via **`WifiNetworkSpecifier` + `ConnectivityManager.requestNetwork`**, which **mandates a per-connection system approval dialog on that phone** — choreographed honestly, not hidden: the join screen shows "approve the connection prompt," and the demo script line for this branch is *"each phone approves one system prompt — once."* All session sockets are bound to the granted `Network` object (a Specifier network is app-scoped, not the default network), and **the NSD fallback does not operate in this mode** — both facts owned by 03 §5.4. The word "suggestion" is gone: `WifiNetworkSuggestion`'s non-deterministic background join is not used.

NSD discovery (`+ Add camera`, §5.1) remains the no-QR fallback for phones already on the session Wi-Fi (MODE-1's network premise). This kills the "which network am I on" failure class that Apple's own Live Multicam trips over (iCloud-Keychain-off, product ground truth). The wire detail is owned by 03 §5.4/§5 (transport); this screen owns the two-surface choreography — QR on the controller, `ImageAnalysis`+ZXing scanner on the camera's join screen — plus the MODE-2 approval-dialog step above.

**Build cost and the pre-committed relief valve (the demo-visible subsystem is scheduled, not silent scope).** QR pairing carries named line items in 03 §16.3 — the transport half (`HELLO{token}`, MODE-2 join flow) in week 2 beside `:core:transport`, the UI half (QR render on the controller, scanner screen on the camera) in week 4's controller build, ~2–3 days total. **If it slips, demo beat 2 degrades to the NSD discovery-list path (§5.1, fully specified in 03 §5.1–5.3) with zero gate impact, and the beat-2 script simply drops the QR flourish.** The valve is pre-committed here so nobody debates it at week 6.

---

## 6 — Screen: The multicam editor (where trust is won or lost)

This is the screen the whole product is about, and the one the reviewer correctly names as unanswerable-until-specified: *will an editor reject the rough cut in thirty seconds?* Everything below is engineered so the answer is no.

**Free vs paid boundary, made visual (product spec §7.1):** the free tier reaches this screen with a **single-angle** timeline — trim and export one camera, genuinely useful, terminal-but-honest. The **multicam timeline, angle switching, the on-device smart-reframe, transcript-driven cutting (§6.8), the vertical 9:16 export and `.srt` caption sidecar (#15/#16, §7), and the AI Director rough cut are the paid unlock.** Of these, **transcript-driven cutting is the paid tier's one genuinely-*learned* on-device capability** — everything else is deterministic CV or DSP — and it lands on ground that, on an Android phone, is genuinely *empty*: on-device transcript editing is absent on Android (Descript never shipped mobile; DaVinci Resolve's transcript editing is desktop/iPad, Studio-gated, and Resolve runs on no Android phone at all). It is a differentiated capability on Android, **not the moat — the moat remains market-vacancy** (product §1.1/§1.5); we are not out-AI-ing Blackmagic, we are serving a platform they decline. The transition is a single inline upsell on the timeline itself ("Add angles — unlock the multicam editor"), never a wall in front of capture.

### 6.1 Layout (Expanded)

```
┌────────────────────────────────────────────────────────────────────────┐
│  PROGRAM  ▷ 00:03:12 / 00:41:08        [Angles ▦4]  [Pacing ◐]  [Export]│
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                                                                  │  │
│  │        PROGRAM PREVIEW  (active angle's ExoPlayer at playhead)    │  │  ← NOT CompositionPlayer.
│  │                    currently: CAM2 "Guest"                        │  │    Preview = the already-running
│  │                                                                  │  │    ExoPlayer surface of the
│  └──────────────────────────────────────────────────────────────────┘  │    angle selected at the playhead.
│  ANGLE RIBBON (tap an angle here = override the cut at the playhead):    │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐                                            │
│  │CAM1│ │CAM2│ │CAM3│ │CAM4│   ← 4× ExoPlayer thumbs, frame-locked scrub  │
│  │host│ │•LIVE│ │wide│ │ 2s │      (one of these IS the program preview)   │
│  └────┘ └────┘ └────┘ └────┘                                            │
├────────────────────────────────────────────────────────────────────────┤
│ TIMELINE  (one colour per angle; height = confidence; ✎ = manual override) │
│ 0:00        0:10        0:20        0:30        0:40      0:41           │
│ ▓▓▓▓│░░░░░░░│▓▓▓▓▓▓│▒▒▒▒│░░░░░░│▓▓▓▓▓▓▓▓│▒▒▒▒▒▒│░░░░│▓▓▓▓▓▓  (CAM colours)│
│     ▲cut     ▲cut   ▲cut ▲cut   ▲cut     ▲cut   ▲cut ▲cut                 │
│                    ◇ playhead                                            │
│ ♪ ═══════════════════ CAM2 audio (one unbroken track) ══════════════════ │  ← audio-only sequence
└────────────────────────────────────────────────────────────────────────┘
```

### 6.2 The core architecture the screen sits on (consistency check with product spec §5.3 and tech spec §8)

The reviewer's sharpest tech objection is that the program preview was riding an **experimental, unpublished** API (`CompositionPlayer`, b/470355043) on the demo-critical interactive path, on the strength of a "single-sequence seek is confirmed working" claim that the week-1 spike has **not yet verified**. This design removes `CompositionPlayer` from the scrub path entirely:

- **Scrubbing** is served by **4× plain ExoPlayer on 4 SurfaceViews sharing one clock** — the ordinary, non-experimental path. The angle ribbon scrubs all four angles frame-locked without touching any compositor. **"Sharing one clock" is authored synchronization, not a library property** — ExoPlayer has no cross-instance clock-slaving API, and scrub and playback are different problems: scrub = broadcast `seekTo(position)` to all four players; playback = **one master player** (the promoted/audio angle) as the clock source, three muted followers periodically drift-checked against it and corrected when divergence exceeds ~1 frame. The mechanism, cadence, and threshold are specified in 03 §8.1, and the week-1 spike batch verifies 4-player scrub+play lock on the owned tablet; this screen renders the result.
- **The PROGRAM PREVIEW is the active angle's ExoPlayer surface at the playhead** — literally the ribbon tile for whichever angle the cut selects at the current time, promoted to the large preview pane. The 4× ExoPlayer grid is already built for the ribbon; the program preview reuses it. **The interactive preview never depends on `CompositionPlayer`.** When the playhead crosses a cut, the preview swaps which ExoPlayer surface is promoted — a view swap, not a re-render through an experimental graph.
- **`CompositionPlayer` / `Transformer` are reserved for EXPORT** (and an optional, clearly-labelled "preview final render" that is *not* on the scrub path — §7). The single-sequence `Composition` is still the export object; the unified preview/export *model* survives, but the *interactive* preview is de-risked onto ExoPlayer.
- **Status of single-sequence `CompositionPlayer` seek is measured-pending, not confirmed.** A week-1 spike (tech spec §8, §13) specifically exercises single-sequence `CompositionPlayer` preview + seek across *dozens* of concatenated clipped media items — the real load-bearing surface, not the demos/composition multi-sequence reference. Until that spike is green, `CompositionPlayer` is used only for the export render, where a seek defect cannot break the interactive demo. If the spike later proves single-sequence scrub-seek solid on the owned tablet, the program preview *may* be upgraded to `CompositionPlayer` for pixel-exact pre-export preview — but the design does not require it and the demo does not wait on it.
- Media3 **1.10.1**, pinned. This screen adopts no multi-sequence compositor: #2439 (multi-sequence seek crash) and #2742 (compositor deadlock) are avoided by construction, not hoped around.

### 6.3 The multicam timeline vs Final Cut Pro for iPad — the benchmark

| Aspect | FCP iPad multicam editor | Us | Verdict |
|---|---|---|---|
| Max angles | 4 | 4 (V1); 6 (V2) | MATCH |
| Angle viewer + tap-to-switch | Yes — tap an angle during playback to cut to it | Yes — **tap an angle in the ribbon to override at the playhead** | MATCH interaction, DIVERGE on origin (§6.4) |
| Starting state of the cut | **Blank — you cut every angle yourself, live or manually** | **Pre-populated by the AI Director** (paid) or one-angle default (free) | **BEAT** — you start from a full assembly and *correct*, not from nothing and *build* |
| Timeline representation | Angles as stacked lanes | **One colour-coded program lane** + expandable per-angle lanes + one unbroken audio lane | DIVERGE — a single program lane reads as "the cut" at a glance; stacked lanes read as "raw material." The rough cut must look like an answer, not homework. |
| Re-editability of an auto-cut | N/A (no auto-cut) | **Identical to a manual cut — same object** | BEAT (§6.5) |
| Frame-accurate sync as the starting point | Manual sync / timecode / audio | **<1-frame sync by construction — SNTP-primed audio cross-correlation (GCC-PHAT) by default; live exposure-phase lock as optional upside** | BEAT — the sync chore that opens every FCP multicam session is already done (product spec JTBD-3) |
| Conjure an angle from one source | No | **Smart-reframe punch-in — a new "virtual camera" from a single static phone** (§6.6) | BEAT — a re-editable angle that did not exist at capture, on-device, deterministic CV |

The honest DIVERGE we own: FCP iPad's model is *you are the switcher, the app is the recorder*. Ours is *the app proposes a switch, you are the editor-in-chief*. That is a different trust contract, and §6.5 is how we earn it.

### 6.4 The ≤ 2-tap per-boundary override (the trust mechanism, specified exactly)

This is the single most important interaction in the product. It must be ≤ 2 taps and it must never destroy the AI's reasoning.

**Override an angle choice for a segment:**
1. **Tap 1** — tap the segment on the timeline (or tap the boundary marker `▲`). The segment highlights; the angle ribbon above lights the four thumbnails *at that exact timecode*, frame-locked, so you see all four choices for that moment.
2. **Tap 2** — tap the angle you want in the ribbon. The segment recolours to that angle. Done. The `AngleCut` for that time range is reassigned; the program preview (the promoted ExoPlayer surface) swaps to the new angle in place.

That is the whole gesture. Two taps, reversible, no dialog, no render.

**Move a cut point:** drag the boundary marker `▲` left/right. The two adjacent segments retime live. (This is a drag, not a tap, and is unlimited.)

**Add a cut the AI didn't make:** long-press a segment → "split here" → the new boundary appears at the playhead, both halves editable. (One press + one tap; still ≤ 2.)

**Delete a cut (merge two segments to one angle):** tap the boundary `▲` → tap "remove cut." The later segment adopts the earlier segment's angle. (≤ 2.)

Every override drops a small `✎` (manual) mark on the boundary so the editor can see, at a glance, *which cuts are theirs and which are the machine's* — and can bulk-accept or re-run the AI on only the untouched regions.

### 6.5 The AI-trust UX — the direct answer to "reject in 30 seconds?"

An editor rejects an auto-cut in thirty seconds when it is (a) irreversible, (b) opaque, or (c) obviously wrong with no cheap fix. We remove all three:

- **Not irreversible (Principle 3).** The rough cut is *the same editable decision-list* a manual edit would produce. There is no "flatten," no separate auto-cut render, no destructive apply. The AI's output and a human's edit are the same kind of object; the AI merely wrote the first draft of the angle-assignment. Product spec §5.5 rule "Emit a re-editable `Composition` decision list. Never flatten." is enforced *by this screen having no flatten affordance to begin with.*
- **Not opaque (Principle 4 — the glass box).** Tap any boundary → a **"why here?"** sheet slides up showing the signal behind the cut: the per-angle audio-energy bars at that moment, which angle won, and the rule that fired ("speaker transition — CAM2 rose +7 dB over CAM1 for >1.5 s"). This is the direct answer to the documented #1 complaint, *"you run the tool and hope it makes smart decisions."* You never have to hope; you can always look.
- **Cheap to fix when wrong (§6.4).** ≤ 2 taps per boundary, ≤ 2 taps/min target (product spec quality gate). An editor who can fix a bad cut in two taps does not rage-quit over it; they fix it and move on.
- **Cuts on speaker transition, never on silence** (product spec §5.5, §9.6 patent). This is also the *better edit* and the "why here?" copy says so — the machine's reasoning matches an editor's instinct, which is itself a trust signal.
- **Video-only by default; one unbroken audio track — built from two sequences, not a chopped one.** The timeline shows a single continuous audio lane beneath the coloured program lane. This is not a UI fiction: it is a literal **audio-only `EditedMediaItemSequence`** from the chosen audio angle — **one unclipped media item for the whole take when no transcript deletions exist; each transcript deletion (§6.8) splits it into butt-joined clipped items (N deletions → N+1 items) through 03 §8.2's take-time→program-time map, still one unbroken lane at every angle switch** — composited alongside a **video-only `EditedMediaItemSequence`** of the per-cut clipped video items (tech spec §8.2). Because the second sequence is audio-only, it does **not** invoke `MultipleInputVideoGraph` and does **not** trip #2439/#2742 (those are multiple-*video*-input defects). The audio is genuinely never chopped at an angle switch — the exact behaviour three Blackmagic forum threads begged SmartSwitch for — because the clipping that chops video simply is not applied to the audio sequence.
- **The pacing dial** (`◐` in the header) is a single conservative↔aggressive control that re-runs the FSM over the *untouched* regions only, preserving every manual override. Defaulting to conservative when SPIKE-AUDIO landed in the 3–6 dB band (product spec §5.4) is a design default, not a code branch the user sees.

**The thirty-second test, scripted:** an editor opens the rough cut, scrubs the ribbon (angles are frame-locked — first trust signal), sees a plausible colour-coded assembly (not homework — second signal), taps one cut they disagree with, sees *why* the machine chose it (glass box — third signal), taps the angle they prefer (fixed in two taps — fourth signal), and keeps working. At no point were they asked to accept an irreversible result. That is the design's answer to the reviewer's core question, and it is falsifiable by the product spec's quality gate (≤ 2 overrides/min, each ≤ 2 taps).

### 6.6 Smart-reframe — the conjured second camera (deterministic CV, spike-independent)

**This is a paid capability *and* a demo beat (§8), and the language here is deliberately precise: it is on-device deterministic computer vision, not "AI."** The product spec's honesty rule (§1.5) says selling heuristics as "AI" is exactly the self-flattery a reviewer discounts, so this screen calls it what it is: a **smart-reframe** driven by a face bounding-box tracker plus rule-of-thirds/luma/focus scoring. The face detector it rides on is named and sized in the technical spec's model table (§11): the **GMS-free MediaPipe Face Detector Task** (BlazeFace) running on the owned SoCs — *not* MediaPipe Face Landmarker, and reconciled against the "NO MediaPipe *Tasks-vision ASD*" line, because a face *detector* is a different, shipping component from the absent active-speaker task.

#### 6.6.1 The temporal pipeline — why the punch-in is smooth, not a jittering box

The creator_wow review is right that Beat 7 (the conjured second camera) is the demo's guaranteed, spike-independent peak wow in *every* branch (§8, §9) — and that it **cannot rest on "a face detector."** A raw face *detector* emits sparse, noisy bounding boxes at **2–5 fps** on sampled frames; feeding those straight to a crop would jitter and lag — the exact "gimmick" tell design question D7 concedes is the risk. So the smart-reframe is specified as a **virtual camera operator**, a fixed temporal pipeline sitting between the detector and the editable crop keyframes. Each stage answers a specific failure mode:

1. **Box filtering — a one-euro filter over the sampled detections.** Every detected box (center *x/y* and size) is smoothed with a **one-euro filter**, chosen over Kalman because it is jitter-vs-lag tunable at *low, variable* frame rates with two intuitive parameters (min-cutoff, beta) and is the standard for smoothing a noisy interactive target. Low target speed → aggressive smoothing (kills jitter while the speaker is still); high speed → less smoothing (keeps up when they actually move). Runs on the sparse 2–5 fps stream, before any crop exists.
2. **Dead-zone + hysteresis.** The virtual camera does **not** move while the smoothed face stays inside a central **dead-zone** (≈ the middle third of the crop). Only when the face crosses the dead-zone edge does the target crop begin to follow — mirroring a real operator who does not chase micro-movements. Re-entry hysteresis prevents oscillation at the boundary.
3. **Crop-path interpolation up to output frame rate.** The filtered targets are keyframes at 2–5 fps; the *rendered* crop is interpolated between them on center and scale with a **monotone cubic (monotonic Catmull-Rom) spline**, evaluated at the full output frame rate (30 fps), so the move is continuous even though detections are sparse. The keyframes are what the user sees and edits in the ribbon; the interpolation is what plays.
4. **Maximum-velocity + damping bound.** Crop-center pan speed is capped (≤ ~15 % of frame width/sec, tuned in the spike) and acceleration is **critically damped**, so a single bad or jumped detection can never *snap* the frame — at most it starts a slow, bounded move the next good detection corrects. Crop-scale (zoom) rate is capped separately and more tightly, because a jumpy zoom reads worse than a jumpy pan.
5. **Hold-on-loss.** If detection drops for up to ~1 s (occlusion, profile turn) the crop **holds its last position** rather than snapping to full-frame; past that it eases back to a safe wide framing. A dropped box never produces a visible jerk.

The output is a **re-editable keyframed `CropTrack`** (03 §11, `:framing`): the user can drag any keyframe, retarget to the other speaker (which re-seeds the filter at that time and re-solves forward), or delete keyframes — smoothing and bounds re-apply so a hand edit stays smooth too.

#### 6.6.2 The quality bar the week-1 face-detector spike must clear — on the WIDE-shot geometry

Beat 7's conjured camera runs on the **locked-off wide angle framing both speakers**, where faces may sit **> 2 m** from the phone — *outside* BlazeFace **short-range**'s reliable envelope (short-range is designed for < 2 m; technical ground truth). The design does not assume the detector works there. The week-1 face-detector spike (03 §11 framing row) must **prove it on that exact geometry** and clear all of:

- detection **cadence ≥ 3 fps** with a valid box on the intended speaker for **≥ 90 %** of a 60 s wide-shot clip, on each owned SoC;
- post-filter crop-center **jitter < 0.5 % of frame width** while the speaker is still (validates the one-euro tuning, stage 1);
- **no visible snap** — no single rendered frame-to-frame crop-center move exceeds the velocity cap (stage 4) across the clip;
- **recovery < 1 s** from an induced 0.5 s detection dropout, hold-then-ease (validates stage 5).

**Pre-committed fallback:** if short-range fails the wide-shot geometry, ship **BlazeFace full-range** for the reframe/tracking path (03 §11), retaining short-range only for close talking-head take-review. Beat 7 rides a *validated* detector-plus-pipeline, never "a face detector" — the wow that is load-bearing in every branch is not left to chance on an unverified detector-vs-geometry match.

**What the user does, on the editor screen:** select a single static wide angle (e.g. CAM1 framing both speakers), tap **`Reframe → punch in on speaker`**. The tracker follows the chosen face bbox and emits a **re-editable, keyframed crop** that appears in the angle ribbon as a *new* angle — "CAM1-A, punch-in" — a virtual second camera conjured from one physical source. It is a decision-list of crop keyframes, fully editable (drag a keyframe, change the target, delete it), and it costs no second phone.

- It needs **no inter-angle audio**, so it is entirely independent of SPIKE-AUDIO. It ships in the paid tier whether or not the AI Director does.
- It is deliberately *modest* — near-commodity single-target reframe — and that modesty is the point: it keeps a real, demoable, spike-independent capability in the paid tier so the pricing story is not hostage to one unrun spike (product spec §5.6, §7.1).
- On the timeline, a conjured angle carries a small `⌖` mark so it reads as derived, never as a fifth physical camera.

#### 6.6.3 The live-scrub rendering path for the conjured angle (objection: creator_wow — the interactive crop path was unspecified)

The temporal pipeline (§6.6.1) produces a `CropTrack`, and export (§7) knows how to bake it. But Beat 7's punch-in is *interactive* — the user scrubs it, re-aims it, and watches it update live in the ribbon and program preview — and that **live-scrub rendering path was previously specified only for export, never for the interactive surface.** It is specified here, and it deliberately keeps `CompositionPlayer` off the scrub path (§6.2).

**The conjured angle is not a second decoded stream.** It reuses the **source wide angle's already-running `ExoPlayer`** (the ribbon tile that shot it) and applies a *time-varying crop* to that one decoder's output — no extra decode session is spent (protecting the decoder budget, §10) and no compositor is involved.

- **Primary path — `ExoPlayer.setVideoEffects(...)` with a time-parameterized crop.** The conjured tile is a second `ExoPlayer` view on the *same* source `MediaItem`, carrying one video effect: a custom **`MatrixTransformation`** whose `getGlMatrixArray(presentationTimeUs)` returns the crop/zoom matrix **interpolated from the `CropTrack` keyframes at that exact presentation time** (the monotone-cubic path of §6.6.1, evaluated per displayed frame). Because `MatrixTransformation` is itself time-parameterized, the keyframed move renders continuously as the playhead moves, with no per-frame app work on the main thread. **Maturity flag:** `ExoPlayer.setVideoEffects(List<Effect>)` (in `media3-exoplayer`) is `@UnstableApi` — so it is *experimental* — but it is a materially more exercised surface than `CompositionPlayer`'s `MultipleInputVideoGraph`, it operates on a single `ExoPlayer`'s output (one video input, no multi-sequence graph, no #2439/#2742 exposure), and it is validated on the owned tablet in the same week-1 editor spike (03 §8.1/§16) before Beat 7 relies on it.
- **Pre-committed fallback — a dedicated single-input GL crop shim.** If `setVideoEffects` proves unstable on the owned tablet, the conjured tile renders into its **own `SurfaceView` fed by the source decoder via a `SurfaceTexture`**, and a GL fragment shader applies the interpolated crop matrix per frame. This is a *single-input* special case of the fallback compositor (03 §8.4) — far simpler than the 4-up grid (one texture, one quad, one crop matrix), shares no code with `CompositionPlayer`, and is authored GL the compositor fallback already requires. The user-facing behaviour is identical; only the renderer beneath differs.
- **Export uses the same `CropTrack`.** On export the crop is a `Presentation`/matrix effect on that clip's `EditedMediaItem` inside the video-only sequence (§6.5, 03 §8.3) — the same keyframes, the same interpolation, evaluated by `Transformer`. Scrub and export agree by construction. The **vertical 9:16 Short export (#15, §7)** rides this same `CropTrack` through the same Transformer path (03 §8) at 1080×1920 — the Short is the same re-editable crop, not a second tracker.

The interactive punch-in is thus rendered by a single-input, non-`CompositionPlayer` path with a named experimental primary, a named authored fallback, and a week-1 validation gate — so the wow that is load-bearing in every branch does not ride an unspecified rendering path.

### 6.7 Degraded, re-synced, and gapped angles in the editor (the G4 timeline consequence)

G4 (a camera survives controller loss) is only as strong as the *timeline* it produces (§5.6). When an angle was pulled and rejoined, or dropped a sync cycle, the editor must show the truth, not a uniformly-perfect grid. §8.2 assembly must **not** assume "gap-free ordered cuts over uniformly-synced angles":

- **`SYNC_DEGRADED → re-synced` badge.** An angle whose offset was recovered by post-record audio cross-correlation (§5.6, 03 §4.5) rather than capture-time phase-lock carries a small `≈` badge on its ribbon tile and on any segment cut from it, with a one-line "why here?"-style note ("aligned by audio — this angle lost controller contact 07:12–07:40"). It is still fully usable and still frame-accurate to < 1 frame; the badge is honesty, not a warning.
- **Coverage gaps are first-class.** If an angle has **no frames** for a span (started late, or storage-floor forced a stop/resume — 03 §5.1), that span renders as a **hatched "no coverage" band** on that angle's ribbon lane. The `AngleCut` candidate set excludes that angle for that time: the AI Director cannot assign it there, and the ≤ 2-tap override (§6.4) simply does not light that angle's thumbnail at that timecode — you cannot cut to footage that does not exist, and the UI shows *why* it is unavailable rather than offering a black frame.
- **The `AngleCut` list carries availability, not just choice.** Each `AngleCut` resolves against per-angle coverage, so assembly (03 §8.2) is over the *available* angles per span. Where the previously-live angle has a gap, the program lane falls back to the nearest covered angle and marks that boundary as **machine-forced** (a distinct mark from a chosen cut), so the editor can see the switch was coverage-driven, not editorial.

This is the editor-side completion of the G4 claim: the pulled camera's footage does not merely survive as a file — it lands on the synced timeline, correctly placed, honestly labelled, and cuttable.

### 6.8 Transcript-driven cutting — the paid tier's one genuinely-learned capability

Pulled into **V1 paid** (product decision; was V2). Rationale: it is proven shippable (VN, Descript-on-desktop), MIT-licensed with a clean patent surface, runs **on-device, post-record**, and it lands on exactly the ground Resolve cannot follow — an Android phone (Resolve does not run on Android phones at all; its transcript editing is desktop/iPad and Studio-gated). It is the one paid feature that is genuinely *learned* AI rather than deterministic CV/DSP, so it — not the energy-argmax director — is the paid tier's real learned-AI beat, and it directly answers the objection that V1 otherwise ships no learned-AI capability, only an angle picker. **It is a differentiated capability on Android, not the moat — the moat remains market-vacancy (product §1.1/§1.5).** (The ~1–1.5 weeks of added scope is budgeted in 03 §16, consuming weeks-7/8 slack.)

**Model + runtime (design-level; the one authoritative whisper size table lives in 03 §11.4 / §13, and this line matches it exactly).** whisper.cpp **tiny (≈ 31 MB) or base (≈ 57 MB)**, bundled in-APK as **`q5_1` int-quantized GGML weights** — V1 ships **`q5_1`, not f16.** The shipped default is **`tiny.en` q5_1 (≈ 31 MB)**; **`base.en` q5_1 (≈ 57 MB)** is the opt-in accuracy upgrade where the thermal/latency budget allows — stated here as a **verbatim quote of the one authoritative table in 03 §11.4** (which product §5.6 also quotes; no document paraphrases its own default — the cross-doc default drift a prior round produced is closed by quoting, not restating). *(For reference, the unquantized **f16** weights would be ≈ 75 MB / ≈ 142 MB — those are **not** shipped, and any "≈ 75/140 MB" figure is f16, never to be labelled "q5"/"int8". An earlier draft mislabeled the f16 sizes as quantized; product §5.6's old ≈ 39/74 MB figures were the parameter counts miswritten as MB. All three specs now carry this identical `q5_1` table.)* Built native under the NDK (`arm64-v8a`, NEON), **run POST-record, never live** — the transcript is produced from the chosen audio angle's recorded 48 kHz track after STOP, on `Dispatchers.Default`/native, off the capture hot path. It adds no encoder and no capture-time load.

**The UX — transcript as a second editing axis over the *same* decision list.**

```
┌────────────────────────────────────────────────────────────────────────┐
│ TRANSCRIPT  (CAM2 "Guest" audio · tiny.en · ✓ done)   [ Remove filler ] │
│ 00:03 So the thing about multicam on a phone [um] is that nobody…       │
│ 00:07 …actually ▓shipped it▓  ← selected range (2.4s)   [Delete] [Keep] │
│ 00:11 and that's the whole… [·····3.1s silence·····]  ← dead-air flagged │
└────────────────────────────────────────────────────────────────────────┘
```

- **Transcript-as-navigation.** Word/segment tokens carry timestamps aligned to the program timeline; tap a word → the playhead (and all four ribbon angles, frame-locked) jump there. Finding "the part where she said X" stops being a scrub.
- **Delete text → delete time (the Descript/VN paradigm).** Select a text range → `Delete` lifts that time span out of the program (ripple: the program shortens; a `Keep only` inverse also exists). This is a **time-axis** edit and is **orthogonal to angle choice** — which camera plays where is untouched; only *what stays in the show* changes. Under the hood the deletion set defines a **take-time→program-time map** (03 §8.2's program-time model): every downstream `AngleCut` re-times through that map, and the audio sequence becomes an **ordered list of clipped audio-only items butt-joined at each seam (N deletions → N+1 items, §6.5)**. So "the audio stays continuous across the removal seam" is a precise claim — *no gap and no click at the join* — and it is an **explicit binary pass/fail line in 03 §16.1's export spike**, tested separately from video-switch boundaries because deletion seams are a different failure surface (clipped audio items CAN gap or click; the unclipped whole-take item never could). The cut list remains the same re-editable object (Principle 3 — never flattened).
- **Filler + dead-air removal — the concrete SmartSwitch differentiation.** `Remove filler` one-taps out detected "um/uh" tokens and long silences (transcript tokens + the director's VAD, 03 §10). This is a documented gap in Blackmagic's SmartSwitch, which **does not trim dead air** (product §1.1) and is speaker-cut-only over per-angle audio; transcript-driven structure and single-mixed-track handling are real on-device differentiation their narrower switcher does not offer — while the durable moat stays the platform, not this.
- **Non-destructive and inspectable.** Every transcript edit drops the same `✎` provenance mark on the affected boundaries (§6.4) and is reversible; nothing about it is a one-way apply.
- **One surface feeds the caption sidecar (#16, §7).** The `.srt` caption export rides these same word/segment timestamps *after* the user's transcript edits — fixing a misheard word in the transcript fixes the caption; there is no separate captions editor. Sidecar timestamps are emitted in **program time** through the same deletion map (03 §11.4's `exportSrt` semantics): deleted words are omitted, caption blocks spanning a deletion seam split at the seam, and every surviving block ripples to its post-deletion time — the `.srt` always matches the exported program, never the raw take. A sidecar carries no accuracy promise (every platform lets captions be edited post-upload), which is exactly why it ships in V1 while burned-in captions stay out of scope (quality bar below).

**Quality bar (the honest ceiling).** whisper tiny/base on real room audio is good enough for **navigation and coarse structural cuts** — find a fluffed take, lift a tangent, strip filler — but it is **not** pitched as broadcast-caption-grade in V1: burned-in captions are out of scope (they would demand a lower WER than tiny/base clears on noisy multi-speaker room audio). The falsifiable bar, measured on the **SPIKE-AUDIO test clip**: a producer can locate and cut a specific spoken passage and remove filler/dead-air across a 3-minute clip **without fighting misrecognitions on the words they act on**, using **base** if **tiny**'s WER on that clip is too high. If even **base** cannot clear that bar on room audio, transcript cutting **degrades to segment-level** (silence-delimited chunks) rather than word-level — still useful for structural cuts, still spike-independent.

**Spike-independence and its demo role.** Transcript cutting depends only on recorded audio and ASR — **not** on inter-angle audio geometry — so it is entirely independent of SPIKE-AUDIO and survives the < 3 dB director-cut branch (§9). In that branch it is the paid tier's *learned-AI* capability that ships even when the energy-argmax director does not, which is exactly why the paid tier's value never rested on the director clearing its gate (product §7.1; 03 §10.4).

**Verdict vs FCP iPad:** FCP iPad ships Transcript *Search* but not transcript-driven *cutting* of a multicam program; Descript ships the paradigm but never shipped a mobile app on any platform. On-device transcript cutting on an Android phone is a **BEAT** (Appendix A row 16a).

---

## 7 — Screen: Program preview + export ("the reveal")

```
┌───────────────────────────────────────────────┐
│  EXPORT                                        │
│  Program: 41:08 · single-sequence Composition  │
│  ┌───────────────────────────────────────────┐│
│  │ Deliver  ● Program 16:9   ○ Short 9:16     ││  ← #15: vertical rides the CropTrack (§6.6)
│  │ Master   ● 1080p30  HLG10   H.264*         ││  *H.264 export default;
│  │ Proxy    ○ 540p SDR (already have these)   ││   HEVC opt-in (patent surface,
│  │ Range    ● full take   ○ in/out            ││   product spec §9.11)
│  │ Captions ☑ .srt sidecar (from transcript)  ││  ← #16: default ON when a transcript exists
│  └───────────────────────────────────────────┘│
│  Estimated: ~38:00 on this tablet (≤1.0× gate) │
│  Lands in: Movies/Multicam/Ep14/  (open files) │  ← where exports go (§3.3)
│                                                │
│  [ ▷ Preview final ]        [ ⤓ EXPORT ]       │
│                                                │
│  ⌂ Stays on this device. No upload. No account.│  ← the thesis, in the UI
└───────────────────────────────────────────────┘
```

Export is `Transformer` over the single-sequence video `EditedMediaItemSequence` plus the single-item audio sequence (§6.5), MediaCodec-only, no FFmpeg. The screen states the target explicitly (≤ 1.0× realtime, gate G6) so a slow export is legible, not mysterious. **On a reopened shoot (§3.3), the `Proxy` option always exports from the tablet alone; the `Master` option renders disabled-with-reason if any needed master lives on an offline phone, offering `⧉ Gather masters` to reconnect it first — the honest multi-device master-availability DIVERGE from FCP (§3.4, Appendix A row 18a) surfaced at the point of action, never as a silent failure.** `[ ▷ Preview final ]` renders a short pre-export pass through `CompositionPlayer`/`Transformer` — this is the *one* place the experimental composition path is exercised interactively, and it is off the scrub path (§6.2), clearly a "render a preview" action, not the live editor. If the single-sequence-seek spike (§6.2) is not yet green, this button renders a fixed range rather than offering scrub-seek. The `⌂ Stays on this device` line is the product's differentiator rendered where the user is about to act, and it is *true* (gate G7, verified airplane-mode + Wireshark). Exports land in the open filesystem (§3.3), not a sealed bundle — FCP iPad has no equivalent line because it has nothing to claim there; this is a **BEAT** that costs two rows of UI.

**Vertical Short (9:16) — feature #15, paid.** Selecting `Short 9:16` opens a **9:16-masked preview of the program riding the existing reframe `CropTrack`** (§6.6) — the same draggable crop keyframes, shown in a portrait frame. The user can nudge any keyframe with the same ≤2-tap grammar as §6.4 before export. There is **no new editing surface**: it is the reframe screen with a portrait mask and an export button, rendered by the same Transformer path as the 16:9 program (03 §8) at 1080×1920. One take out of the bag therefore yields the episode *and* a face-tracked vertical Short — the format the beachhead actually posts (Shorts/Reels/TikTok, product §5.1 #15).

**Per-angle crop policy — no program segment has an undefined crop.** The tracker's `CropTrack` exists only for the angle it ran on (the locked-off wide), so a multicam program cut to *other* angles needs a stated policy, not undefined behavior. It is (mirrored from 03 §8.3, which owns it): the **tracked angle** rides its `CropTrack`; every **untracked angle's segment** (the close-up phone, a conjured angle's source, a `SYNC_DEGRADED` angle) gets a **static face-centred crop seeded from one BlazeFace detection at the segment's first sampled frame** (fallback: centre crop) — emitted as *ordinary editable `CropTrack` keyframes*, so the same ≤2-tap nudge grammar (§6.4) applies to every segment uniformly and the user can correct any seed with the gesture they already know. The **single-angle-source Short** (one wide phone — the §2.3 single-phone story) is the demo's default path; the multicam-program Short is the general case this policy covers.

**Caption sidecar (`.srt`) — feature #16, paid.** The `Captions` toggle (default **ON** whenever a transcript exists) writes an `.srt` sidecar from the whisper word/segment timestamps *as edited* in §6.8, and the share sheet delivers video + sidecar together. Caption text is whatever the transcript says after the user's edits — one surface, no separate captions editor. Timestamps are **program-time via the deletion map** (§6.8; 03 §11.4), so the sidecar always matches the export. The sidecar carries **no accuracy promise** (every platform lets captions be edited post-upload), which is why it ships in V1 while burned-in captions remain out of scope (§6.8). **And the sidecar is watchable in-app without burning anything in:** the in-app player renders the `.srt` live at playback time via ExoPlayer **`MediaItem.SubtitleConfiguration`** — a stable, non-experimental Media3 surface already in the dependency set (listed in 03 §8.1's surface table) — which is how demo beat 10 (§8.1) shows a *captioned* Short on camera while burned-in captions stay honestly out of scope.

---

## 8 — Demo choreography & the hero moment

The demo is filmed end-to-end with scrcpy (product spec §8, narrative gate). It is engineered so that **every wow beat is either spike-independent or explicitly branch-gated**, and — the reviewer's decisive correction — so that its wow is legible to the actual buyer (the non-consumption producer who has *never* cut multicam), not only to a veteran editor who has personally suffered manual sync.

### 8.1 The choreography (≈ 3 minutes on camera, with two labelled time-skips)

**The honesty rule the whole film obeys — machine waits are real, and shown as real.** An earlier draft's timestamps were arithmetically infeasible against 03's own committed bars: it put the transcript on screen 28 s after STOP on a ~45 s take when 03 §11.4's runtime bar admits up to 2× realtime (45–90 s), and played a finished export ~13 s after tapping EXPORT against a G6 bar of ≤1.0× realtime (≤ ~40 s). That was hoping, not budgeting — on the exact surface this document set prides itself on budgeting. This cut re-baselines every timestamp against the bars and defines the one permitted compression: **any machine wait longer than a few seconds is either shown in full or jump-cut with an on-screen elapsed-time chip (`⏱ 0:38 · done ✓`)** — the same affordance grammar beat 5's `finalising…` state establishes. The film never shows a result at a timestamp its own budget says cannot exist, and product §8's narrative gate carries the same shown-truthfully rule. Beat 8b additionally rests on a **named, week-1-measured demo precondition**: the 03 §16.1 whisper spike measures `tiny.en` q5_1 wall-clock on the *owned tablet SoC* for a ~60 s clip, and the demo path requires **≤ 1× realtime measured** (03 §11.4 names this precondition; `base.en` is attempted only if it also clears ≤1×). If the bench measures slower, the choreography still holds — beat 8b is pinned to the transcribing-✓ chip, not to a clock time, and the film jump-cuts to the chip flipping with its elapsed time visible.

1. **The bag.** Open a bag: two phones, one tablet, two small stands. No laptop, no cables, no lav rig, no sync box. Hold the empty bag up. *(0:00–0:10)*
2. **The rig, in ten seconds — QR pairing, join mode stated.** Stand the phones — one framed **wide, locked off, seeing both speakers** (this angle is load-bearing for beat 7), one on a subject. (If the week-1 inventory found a foldable, one stands in tabletop-fold posture, §2.2.) Each phone scans the tablet's pairing QR (§5.7). **The demo runs pairing MODE-1**: all three devices are already on the venue AP, the QR carries only `{controllerIp, port, sessionToken}`, and the phones TCP-connect directly — **no Wi-Fi join API, no system dialog on camera.** (In the MODE-2 branch — a phone not yet on the AP — Android's `WifiNetworkSpecifier` flow mandates one system approval dialog per phone; the script line for that branch is *"each phone approves one system prompt — once,"* §5.7.) Two tiles appear, both flip to `clocks locked ✓` (frame-accurate offsets resolve at STOP, §5.5). Tap `ROLL ALL`. Red tallies light at once. *(If QR pairing slipped its 03 §16.3 build slots, this beat runs the NSD discovery-list path instead — §5.7's pre-committed valve, zero gate impact, QR flourish dropped.)* *(0:10–0:25)*
3. **A real two-person conversation.** Shoot ~40 seconds of natural alternating talk. On the tablet grid, the operator does nothing but watch — this is a *monitor*, not a switcher, and the demo shows that honestly. *(0:25–1:05)*
4. **The pull (the Android-only capability beat).** Mid-take, walk one phone out of Wi-Fi range and back (G4). Its tile shows `reconnecting… holding last frame`, then `re-syncing angle…`, then `rejoined ✓`. The file is unbroken *and the angle re-derives its offset by audio on rejoin* (§5.6). Say the line: *"On iPhone, both apps have to stay in the foreground — pull one and the shoot dies. This one just keeps rolling — and it puts itself back on the synced timeline."* The payoff is **deferred to beat 6**, deliberately: file-intact is not the whole G4 claim. This beat needs no foldable and no brand mismatch, so it carries the Android-differentiation narrative on the exact owned kit whatever it is (§2.2). *(0:55–1:10, overlapping)*
5. **`STOP ALL`.** A brief honest `finalising…`, then the cut is on screen. **The moment STOP lands, a `transcribing…` chip appears on the transcript tab (§6.8) and runs in the background for the rest of the film** — its budget is the take length (~45 s of audio at the ≤1×-realtime measured precondition), so it is expected to flip `✓ done` at ~1:55, *after* beats 6–8. *(1:10–1:12)*
6. **THE SYNCED TIMELINE, INSTANTLY — including the camera that was pulled.** The instant recording stops, the tablet shows a **frame-locked multi-angle timeline that is already there** — no import, no sync step, no spinner. Drag the playhead: all angles move together, locked to the frame — **including the angle that was walked out of range in beat 4**, which sits correctly placed, carrying its `≈ audio-re-synced` badge (§6.7), and is cuttable like any other. That is G4 proven *all the way through to the cut*, not merely "file intact." *(1:12–1:22)*
7. **THE CONJURED SECOND CAMERA (the guaranteed visual-capability wow).** From the single locked-off wide phone, tap `Reframe → punch in on speaker`. A **new angle materialises in the ribbon** — a tight punch-in on the left speaker that no camera physically shot — and it is re-editable, keyframed, live on the tablet. Drag its target to the other speaker; it re-tracks. *One phone just became two cameras.* This is a **visible new thing appearing**, it needs no prior multicam experience to read as impressive, and it needs no inter-angle audio, so **it lands in every branch including SPIKE-AUDIO < 3 dB** (§9). *(1:22–1:38)*
8. **(If AI on) the cut paints itself.** The colour-coded program lane fills in across the timeline as the FSM assigns angles — visibly, left to right, in a couple of seconds. Scrub it: it cuts on speaker changes. Tap one cut, see "why here?", change it in two taps. *(1:38–1:52)*
**8b — Transcript cleanup: the co-hero — pinned to the chip, not the clock.** The `transcribing… ✓ done` chip from beat 5 has flipped by now: at the measured ≤1×-realtime precondition (§8.1 preamble), a ~45 s take is transcribed ≤ ~45 s after STOP — ready ~1:55, which is exactly why 8b sits *here*, after beats 6–8, and not at 1:38 where an earlier draft impossibly staged it against its own 2×-realtime runtime bar. Open the transcript, select a fluffed sentence, tap `Delete` — the program shortens and the audio stays unbroken across the seam (a tested claim, §6.8) — then tap `Remove filler` to strip the "um"s and the dead air. This beat needs no inter-angle audio, so it runs in **every branch** — it is **not** gated behind beat 8's "if AI on," and it **survives the SPIKE-AUDIO < 3 dB branch** (§9; in the director-cut branch it simply starts at ~1:52). It is the demo's *learned-AI* moment (whisper.cpp, §6.8), and — the point the creator_wow review presses — it is the one hero beat that is neither a near-commodity (§8.2) nor an *absence*: delete-a-word-to-delete-the-time and one-tap filler/dead-air removal exist in **no** Android tool and are a documented gap in Blackmagic's SmartSwitch (which does not trim dead air, product §1.1). Staged as a guaranteed hero beat, ~15 s on camera; if the bench measured slower than 1×, the film shows the chip's elapsed time and jump-cuts to ✓ — the transcript is never shown finished faster than the measured number allows. *(1:55–2:10)*
9. **Export → the labelled wait → play → the line.** Tap `EXPORT` at ~2:10. G6's bar is ≤ 1.0× realtime *with transcode* (03 §8.3), so the ~40 s program may honestly take up to ~40 s: the film shows the progress bar start, then jump-cuts with the elapsed-time chip (**`⏱ export 0:38 · done ✓`** — the first labelled time-skip), then plays the finished cut full-screen. Cut to the caption: **"Shot, synced, cut, and exported on the three devices in that bag. No computer touched this footage. Nothing was uploaded."** *(2:10–2:30)*
10. **"…and it cut this too." — the captioned Short, with the mechanism and the honesty on camera.** The vertical Short is a **second Transformer pass** (03 §8.3) — started on camera from the export sheet's `Short 9:16` card, finished behind the second labelled elapsed-time chip — and then it **plays full-screen, phone-shaped, on the tablet, captioned by rendering the `.srt` sidecar live in the in-app player via ExoPlayer `MediaItem.SubtitleConfiguration` (§7): nothing is burned in**, and the narration says so — *"the captions are a sidecar file you can edit; nothing is burned into the video."* Nothing was re-shot: it is the same take through the reframe `CropTrack` (#15, §7) and the program-time transcript timestamps (#16, §7). The close now holds **two artifacts**, and the final caption reads: **"Shot, cut, exported — episode *and* Short — on the phones in that bag. No computer. Nothing uploaded."** *(2:30–2:50)*

### 8.2 The hero moment, pressure-tested on the actual buyer

The reviewer's correction is exactly right and this section takes it head-on: **"the synced timeline appears the instant you stop" is emotionally legible only to someone who has personally suffered manual multicam sync** — a veteran editor with a desktop, who is *not* our buyer. To the named beachhead — the non-consumption producer (JTBD-2) who has **never cut multicam** and never felt the sync chore — a synced timeline appearing is a *fact*, not a *relief*. Engineering the wow purely for spike-independence had quietly engineered it for the wrong audience. So the hero is re-pinned to land on the person who is actually buying.

**The hero moment for the non-consumption buyer is a triad — beat 7, beat 8b, and beat 9: a second camera conjured from one phone, the "um"s and dead air stripped by deleting text, and the finished cut playing back with "no computer touched this."** All three are *positive, visible capabilities*, not the absence of a chore:

- **What the non-consumption buyer sees:** they pointed one phone at a conversation, and on the tablet a *second camera angle appeared that they never set up* — a punch-in they can re-aim with a fingertip. They deleted a rambling sentence by selecting its *text*, and the "um"s vanished with one tap. Then the whole thing played back as a finished, cut video. No prior multicam experience is needed to feel "I could not do that before, and it happened in the room with no laptop."
- **What they feel:** capability, not relief. "This gives me a second camera, a self-cleaning edit, and a finished cut out of one phone and a tablet" is a *new thing I can now make*, which is the emotion that converts a non-consumer, whereas "your sync chore is done" is an emotion only a prior sufferer has.

**Why beat 8b — not beat 7 — is the co-hero the creator_wow review demanded, and why it lands on a buyer with *no multicam history*.** The review's sharp objection is correct: beat 7 (face-tracked punch-in) is by our own admission near-commodity (product §5.2), the beat 6 sync reveal only lands on veterans, and beat 9's "no computer" is largely an *absence*. Beat 8b is the one hero beat that is **novel** (delete-text-to-delete-time transcript cutting exists in **no** Android editor, and filler/dead-air removal is a documented SmartSwitch gap — product §1.1), **spike-independent** (needs only recorded audio + ASR, §6.8), **and not a commodity**. Critically, it is the beat the non-consumption buyer *can feel with no multicam history at all*: their existing one-angle talking-head workflow already burns them on rambling takes, "ums," and dead air — that friction is theirs today, unlike multicam sync, which they have never suffered. So watching a fluffed sentence lift out by selecting its words, and the filler disappear in a tap, reads as a capability that fixes a pain they already have. That is the wow beat 6 structurally cannot deliver to this buyer, and it is why the demo does not rest on commodity-plus-absence.

**The synced-timeline reveal (beat 6) is retained, but reclassified.** It is the wow that lands hardest on the *veteran* editor (who will also watch the demo and who *has* felt the chore) and it is the spike-independent bedrock the whole show stands on — but it is no longer asked to be the emotional peak for a buyer who cannot feel it. Two audiences, layered beats, one demo.

**The two-artifact close (beat 10) aims at where the beachhead actually publishes.** The buyer's distribution is Shorts/Reels/TikTok-first, and ending on the captioned vertical Short — same take, nothing re-shot, riding #15 + #16 — turns beat 9's "no computer" claim from one deliverable into two, one of which is the format the buyer posts most. It costs no new subsystem and no extra shoot time; it is the export sheet's second card played on camera.

**Every hero beat survives the worst spike branch.** Beat 6 (sync) is spike-independent by construction. Beat 7 (conjured camera) needs no inter-angle audio and is spike-independent. Beat 8b (transcript cleanup) needs only recorded audio + ASR and is spike-independent — it is the *learned-AI* co-hero that ships even when the director is cut. Beat 9 (export + "no computer") is spike-independent. Only beat 8 (the self-painting AI cut) is contingent (§9), and it is deliberately *not* the hero. The demo has a positive, novel-capability wow for the actual buyer in every branch.

**But "spike-independent" is not the same as "known to land," and Beat 7's *wow-status* is de-risked two ways — one a hard engineering gate, one a lightweight sanity-check that does not gate the sprint.** Beat 7 is the load-bearing capability wow for the non-consumption buyer in *every* branch, so its execution quality is attacked directly and its buyer-reading is sampled cheaply:

- **Its smoothness is engineered and spike-verified, not assumed** — the temporal pipeline of §6.6.1 and the wide-shot quality bar of §6.6.2 exist precisely so the punch-in reads as a broadcast-plausible virtual camera rather than a jittering box. A janky tracker is the one way this beat collapses, and the design attacks it directly. This *is* a real week-1 engineering gate (03 §16).
- **Its buyer-perception is a lightweight wow sanity-check that *informs* the design, not a sprint gate (D7, §11).** The builder is building this sprint regardless, so a purchase-intent smoke-test would be theatre — a null signal would not stop the sprint, so nothing hangs on it. What is worth doing, and cheap, is informally showing Beat 7 (smoothed per §6.6.1) and the FLAT-rig setup (§2.3) to a few real non-consumption producers and watching whether the punch-in reads as *capability* or *gimmick* and whether standing up two phones reads as *fine*. If the punch-in reads as a gimmick, the design leads with beat 9 (the finished export) and demotes the punch-in to a flourish; if the rig reads as too much work, the demo defaults to the single-phone conjured-camera story (§2.3). These are design defaults steered by a sanity-check — not a GO/NO-GO gate and not a purchase-intent test.

---

## 9 — The director-cut branch (SPIKE-AUDIO < 3 dB): the demo still lands

The product spec pre-commits (§5.4, §8) to **cutting the AI Director entirely if SPIKE-AUDIO returns < 3 dB** — the case where co-located phones capture near-identical room mixes and audio-only VAD has no signal. The old worry was that in that branch the deliverable collapses to "synced capture + manual multicam cut — Blackmagic parity minus a desktop," a materially weaker wow. **That worry is now largely closed by beat 7:** the conjured second camera is a positive, visible, spike-independent capability, so the branch keeps a genuine capability wow, not only the sync reveal.

### 9.1 What survives, and why it is still a wow

In the AI-cut branch, every step of §8 survives **except step 8.** Specifically:

- **The conjured second camera (beat 7) is untouched** — it never depended on inter-angle audio. This is the branch's positive-capability wow, and it lands on the non-consumption buyer exactly as in the full demo.
- **Transcript-driven cutting (beat 8b, §6.8) is untouched** — it depends only on recorded audio and ASR, never on inter-angle geometry. So the paid tier's *learned-AI* capability ships in this branch even though the energy-argmax director does not: the < 3 dB branch is not "the paid tier lost its AI," it is "the paid tier's learned AI is transcript editing, not angle-picking." This is the branch's second positive capability wow.
- **The vertical Short + caption sidecar (beat 10, #15/#16) are untouched** — the Short rides the single-angle `CropTrack` and the captions ride whisper timestamps; neither depends on inter-angle audio geometry, so the two-artifact close survives every branch.
- **The frame-locked synced timeline, instantly, with no desktop (beat 6)** — untouched, still the bedrock, still nothing anyone ships on Android.
- **Manual multicam cutting that is *fast because the sync is already perfect*.** The angle ribbon (§6.1) scrubs all angles frame-locked; tapping an angle at the playhead cuts to it — the exact FCP-iPad live-cut gesture (§6.3), but with the sync chore pre-solved. The editor cuts the show by tapping angles as they scrub, in one pass, on the tablet, in the room.
- **The finished-export payoff (beat 9), the VFR→CFR win (JTBD-3), the fault tolerance (G4), the no-upload guarantee (G7), and hardware-free sync** — all spike-independent, all demoable.

### 9.2 The emotional payload of the director-cut branch, for the non-consumption buyer

The line for this branch is **not** "the AI cut it for you." It is:

> **"I pointed two phones at a conversation, and I walked away with a second camera I never set up, everything already synced, the 'um's and dead air already gone, and a finished cut — no laptop, no sync box, no upload. The edit that normally starts tomorrow started the second I stopped recording."**

That is a real, positive payload for the non-consumption buyer (JTBD-2): the reason they don't shoot multicam is the post-production friction, and this branch *still deletes the friction while adding a conjured angle and a self-cleaning transcript edit* — it just doesn't also auto-assemble the angle cut. It is a weaker wow than the full AI branch. With beat 7 **and the novel, spike-independent beat 8b (transcript filler/dead-air removal, the co-hero of §8.2)** in it, **it is not a weak wow** — it keeps both a positive visual capability and a genuinely-novel learned-AI capability the buyer can feel.

### 9.3 The demo decision is pre-made, not emotional

Per product spec §8, the SPIKE-AUDIO result is known in **week 1**, so the demo is choreographed for the correct branch from the start — the §8 script simply omits step 8 if the spike came back < 3 dB. No one decides at week 6, tired, whether to fake an AI that doesn't work. The design supports both cuts of the film because the hero beats (6, 7, 9) are the same shots in both.

---

## 10 — Cross-cutting UI states driven by the fallback ladder & degradation

The week-1 decoder spike (product spec §5.3) may force a lower grid capability. The UI is specified for **every rung** so the design does not reopen when the number lands:

| Spike result | Monitor grid (§5) | Editor ribbon (§6) | User-visible change |
|---|---|---|---|
| Green (4× 720p @30) | 2×2 live, full | 4× ExoPlayer frame-locked | none — the designed-for case |
| 4× 540p | 2×2 live at 540p | 4× ExoPlayer at 540p | slightly softer tiles; no interaction change |
| 2×2 @24fps | 2×2 live at 24fps | scrub at 24fps | motion slightly less smooth; a caption, not a redesign |
| Single-angle + still grid | **1 live tile + 3 still thumbnails** that refresh on a slow timer | active angle live, others as periodic stills | operator monitors one angle live, others as "last frame Xs ago" — the freshness state (§5.2) already exists for this |
| Custom MediaCodec+GL compositor | as green, different renderer beneath | unchanged | invisible to the user by design |

The point: **the fallback ladder is a rendering-layer decision, and the screens above are specified so that no rung of it changes the layout, the interactions, or the demo choreography.** The still-grid rung is the only one the user can perceive, and it reuses the freshness-state UI already required for WebRTC degradation — so even the worst rung is a copy change, not a new screen. Note that the program preview (§6.2) rides plain ExoPlayer, so no rung of this ladder touches the interactive preview path either. This is what "the design does not have to be reopened when the spike lands" means concretely.

---

## 11 — Open design questions

| # | Question | Why it matters | How to close |
|---|---|---|---|
| D1 | Does the "why here?" glass-box sheet need the audio waveform, or do energy bars suffice? | Too much data re-opens the black-box complaint from the other side (overwhelm). | Test on the first real rough cut; start with bars only. |
| D2 | **Does any owned device fold, and is the fold-line control deck reachable one-handed?** | The tabletop BEAT (§2.2) is only real on actual hardware, and a demoed differentiation claim must not depend on unknown kit. | **Resolve in week 1 by inventory, not at the demo.** If a device folds, confirm ergonomics; if none does, drop tabletop from the narrative (G4 already anchors the Android column) or acquire a cheap foldable. Pre-committed in §2.2. |
| D6 | **Are the two owned phones different brands (product Q9)?** | The mixed-vendor BEAT is iOS-impossible and demoable — but only if the kit actually mixes brands. | **Resolve in week 1 by inventory.** If same-brand, either acquire one differing-brand phone (sub-\$300) or drop mixed-vendor from the demo and lean on G4 + market-vacancy. Cross-referenced from §2.2. |
| D3 | Should manual overrides (`✎` marks) be re-runnable-around, or should any manual touch freeze the whole timeline from AI re-runs? | Determines whether the pacing dial is safe to re-pull after edits. | Current design: re-run only untouched regions (§6.5). Validate it feels safe, not surprising. |
| D4 | Single program lane vs stacked angle lanes as the *default* view — does "one lane" over-simplify for editors who want to see all coverage at once? | The §6.3 divergence bet. | Ship single-lane default with a one-tap "expand all angles"; measure which they live in. |
| D5 | Free-tier single-angle editor: does it feel like a real tool or a demo of the paid one? | Product spec §7.1 depends on the free tier being "terminal but honest," not crippled. | Dogfood the free path end-to-end before the paid path is built. |
| D7 | **Does the conjured-second-camera punch-in (§6.6) read as capability or gimmick, AND does the capture-rig setup (§2.3) read as acceptable, to the actual non-consumption buyer?** | Beat 7 is the guaranteed spike-independent wow in *every* branch (§8, §9). Its *reading* is worth a cheap look before the UI hardens, but nothing gates the sprint on it — the builder builds regardless. | **Lightweight wow sanity-check that informs the design — NOT a sprint gate and NOT a purchase-intent test.** The builder is building this sprint regardless (learning + portfolio + product bet), so gating on a WTP/purchase signal would be theatre — a null result would not stop the sprint. Informally show (a) Beat 7 running (smoothed per §6.6.1) and (b) the FLAT-rig setup (§2.3) to a few non-consumption producers and note whether the punch-in reads as *capability* or *gimmick* and whether the FLAT setup reads as *fine*. This steers a design default, not a go/no-go: gimmick → lead with beat 9's finished export and demote the punch-in; rig-too-heavy → default to the single-phone conjured-camera story (§2.3). No purchase-intent smoke-test is run. (Beat 7's *smoothness*, separately, IS a hard week-1 engineering gate — §6.6.2, 03 §16.) |

---

## Appendix A — Every FCP benchmark verdict, collected

Auditable summary of every MATCH / BEAT / DIVERGE call, so the fidelity claim can be checked at a glance.

| # | Surface | vs Apple counterpart | Verdict | One-line reason |
|---|---|---|---|---|
| 1 | Focus peaking / zebras / overexposure / meters | FCP Camera | MATCH | identical retained set |
| 2 | False colour / waveform / histogram absent | FCP Camera | MATCH | Apple ships none either — parity, not a cut |
| 3 | LUT preview | FCP Camera | DIVERGE | no Android LOG pipeline to preview against |
| 4 | **Manual capture control (ISO/shutter/WB)** | FCP Camera | **DIVERGE (conditional, wk-1-measured)** | **`FULL`-gated on Android → some devices are auto-only, and the prescribed fixed-geometry rig is less flexible than FCP Camera's general handheld capture; MPC ≥ 34 `FULL` fraction is a wk-1 audit output (§4.3). Mid-take dials are LIVE via `Camera2CameraControl.setCaptureRequestOptions()` — the re-arm micro-interaction is a fallback shown only if the wk-1 audit measures the owned device dropping live updates, so that leg of the DIVERGE is conditioned on the bench, not asserted as a platform requirement** |
| 5 | Tabletop-fold capture | FCP Camera | BEAT | Apple has no foldable (hardware-gated, resolved wk 1) |
| 6 | Cross-device tally (phone + controller) | FCP Camera | BEAT | record-local lets each phone tell its own truth |
| 7 | Live multi-view monitoring | FCP Camera | MATCH | same capability |
| 8 | 720p/540p proxy monitoring | FCP Camera | MATCH | Apple proves 1080p monitoring unneeded |
| 9 | Thermal/storage/battery on grid | FCP Camera | BEAT | long-form-in-a-warm-room beachhead |
| 10 | Live switching while recording | FCP Camera | MATCH (both decline) | record-local + offline lookahead |
| 11 | Camera survives controller loss | FCP Camera | BEAT | G4; Apple requires both apps foreground — the pulled angle lands correctly on the synced timeline (§6.7), not merely "file intact" |
| 12 | 4 angles, tap-to-switch | FCP iPad multicam | MATCH | same ceiling, same gesture |
| 13 | Starting from a pre-populated cut | FCP iPad multicam | BEAT | correct a draft vs build from blank |
| 14 | Frame-accurate sync as the given | FCP iPad multicam | BEAT | SNTP-primed audio-xcorr auto-sync (phase-lock as upside) deletes the sync step |
| 15 | Auto-cut is fully re-editable (same object) | FCP iPad multicam | BEAT | no flatten; glass box |
| 16 | Conjured second camera (smart-reframe punch-in) | FCP iPad multicam | BEAT | a re-editable angle from one static phone; deterministic CV over a one-euro-smoothed, velocity-bounded tracker (§6.6); live-scrub via `ExoPlayer.setVideoEffects` time-parameterized crop with a GL crop-shim fallback (§6.6.3); spike-independent |
| 16a | Transcript-driven cutting (delete text → delete time; filler/dead-air removal) | FCP iPad multicam | **BEAT** | on-device whisper.cpp transcript editing on an Android phone — Descript never shipped mobile; Resolve's is desktop/iPad + Studio-gated and runs on no Android phone; the paid tier's one genuinely-learned capability (§6.8) |
| 16b | Vertical 9:16 export riding the reframe `CropTrack` (#15) | FCP iPad | MATCH function, BEAT locus | FCP iPad ships Auto Crop; ours is the *same re-editable keyframed crop* as #12, exported beside the episode in one pass, on-device on an Android phone (§7) |
| 16c | `.srt` caption sidecar from on-device transcript (#16) | FCP iPad | BEAT | rides the 16a transcript surface — captions generated and corrected on-device from whisper timestamps (§6.8, §7); FCP iPad's transcript feature is *Search*, not caption export (product §3.2); sidecar carries no accuracy promise, burned-in captions out of scope |
| 17 | Session library — organisational tiers | FCP iPad library | DIVERGE | one Session tier vs Library→Events→Projects; right-sized for a one-shoot beachhead |
| 18 | Session library — reopen a prior shoot (edit / proxy-export) | FCP iPad library | MATCH | tap a card, cut + proxies restore; proxy-quality re-export always available from the tablet alone |
| 18a | **Master-quality re-export of a reopened shoot** | FCP iPad library | **DIVERGE** | distributed masters need the offline phone reconnected (opt-in `⧉ Gather masters`); proxy re-export always works from the tablet alone (§3.3/§3.4) — a reconnection tax FCP's single library does not impose |
| 19 | Master↔proxy↔decision-list association | FCP iPad library | MATCH function, BEAT transparency | explicit per-angle manifest; master-availability on the card |
| 20 | Where media & exports physically live | FCP iPad library | BEAT | open filesystem, no library jail; exports in `Movies/` |
| 21 | "Stays on this device" export | FCP iPad | BEAT | on-device thesis, one UI row |

---

**Consistency note.** This document takes as fixed, and does not relitigate: the beachhead and pricing (product spec §2, §7), the SPIKE-AUDIO kill branch (§5.4, §8), the CompositionPlayer re-architecture and Media3 1.10.1 pin (§5.3), the retained/cut monitoring set (§2.4), and the fault-tolerance and thermal gates (§8). It aligns with the technical spec's resolutions: program preview rides plain ExoPlayer with `CompositionPlayer` reserved for export (03 §8.1–8.2); the unbroken audio track is a two-sequence Composition, video-only + audio-only (03 §8.2); single-sequence `CompositionPlayer` seek is **measured-pending**, gated on a week-1 spike, not "confirmed working" (03 §8); the smart-reframe rides a named on-device face detector in the model table (03 §11) and is described as deterministic CV, not "AI"; and capture is **CameraX-first** (CameraX 1.5 + `Camera2Interop`, HLG10, Feature Groups — 03 §7.1), with raw Camera2 only as the fallback if the week-1 stream-combo spike proves CameraX cannot express the required surface set.

Round-3 resolutions this document owns or co-owns: (1) the **reframe tracker's temporal pipeline** — one-euro box filtering, dead-zone, monotone-cubic crop interpolation, velocity/damping bound, hold-on-loss — and its **wide-shot quality bar** for the week-1 face-detector spike, including the BlazeFace short-range→full-range fallback (§6.6.1–6.6.2; 03 §11 framing row); (2) the **G4 rejoin timeline consequence** — offset re-derivation by windowed audio xcorr on rejoin, the `SYNC_DEGRADED → re-synced` editor badge, and coverage-gap representation in the `AngleCut` list (§5.6, §6.7; assembly side 03 §5.1/§8.2), demoed to completion in Beat 4/6; (3) the **capture-rig friction** resolution — FLAT default vs SEPARATED upgrade presets — and **D7 as a lightweight wow sanity-check that informs the design, not a week-0 sprint gate and not a purchase-intent test** (§2.3, §8.2, §11); (4) the **manual-capture-control DIVERGE** vs FCP Camera and the `HARDWARE_LEVEL_FULL` fraction as a week-1 audit output (§4.3, Appendix A row 4); (5) the STOP→timeline <2 s affordance is identical across both sync branches via a fast windowed GCC-PHAT pass (§5.5; 03 §4.5/§6.4); (6) **transcript-driven cutting pulled into V1 paid** — the paid tier's one genuinely-*learned* on-device capability (whisper.cpp tiny ≈ 31 MB / base ≈ 57 MB, **`q5_1` int-quantized GGML weights** — f16 ~75/142 MB is NOT shipped; the old "int8/q5"-on-f16-sizes label and product's ≈ 39/74 MB figures were wrong and are reconciled to these `q5_1` sizes matching 03 §11.4; post-record), with its transcript-as-navigation + delete-text-→-delete-time + filler/dead-air UX and quality bar (§6.8; model/runtime owned by 03 §11/§13, scope budgeted in 03 §16), and **beat 8b (transcript filler/dead-air removal) elevated to a co-hero** — the demo's one novel, non-commodity, spike-independent wow that a buyer with no multicam history can feel (§8.1, §8.2); (7) the **CropTrack live-scrub rendering path** — `ExoPlayer.setVideoEffects` with a time-parameterized `MatrixTransformation`, experimental-flagged, plus a single-input GL crop-shim fallback, kept off the `CompositionPlayer` scrub path (§6.6.3; 03 §8.1); (8) the **multi-device master-availability DIVERGE** vs FCP — re-labelled from MATCH — with proxy-quality re-export always available from the tablet alone and master-quality re-export an explicit opt-in that prompts to reconnect the offline phones (§3.3, §3.4, Appendix A rows 18a). Where this document names a number owned by the technical spec — glass-to-glass monitor latency, control-command RTT, the STOP→timeline <2 s budget, the session state machine's transitions, the decoder budget — it renders the *screen states* those numbers produce and defers the numbers themselves to [03-technical-spec.md](03-technical-spec.md).

Refinement-round-1 resolutions this document owns or co-owns: (1) the **demo choreography re-baselined against 03's committed runtime bars** — the elapsed-time-chip wait grammar, beat 8b pinned to the transcribing-✓ chip with a named week-1-measured ≤1×-realtime `tiny.en` precondition (03 §11.4/§16.1), and two labelled export time-skips; the film never shows a result its own budget forbids (§8.1); (2) **beat 10's captioned close given a real playback mechanism** — the `.srt` rendered live via ExoPlayer `MediaItem.SubtitleConfiguration` (stable, non-experimental, in 03 §8.1's surface table), with the nothing-burned-in honesty line spoken on camera, keeping burned-in captions out of scope (§7, §8.1); (3) **QR pairing split into two explicit modes** — MODE-1 (same-AP, `{controllerIp, port, sessionToken}`, no join API, the demo path) and MODE-2 (`WifiNetworkSpecifier` + `requestNetwork` with its mandated system approval dialog choreographed, sockets bound to the granted `Network`, NSD fallback unavailable) — with named 03 §16.3 build-week line items and the pre-committed NSD discovery-list relief valve (§5.7, §8.1 beat 2); (4) **mid-take manual 3A corrected to live `Camera2CameraControl.setCaptureRequestOptions()`**, the re-arm micro-interaction demoted to a bench-measured fallback and Appendix A row 4 conditioned on the week-1 audit rather than asserted as platform fact (§4.3); (5) **sync-accuracy copy corrected for acoustic propagation** — <1 ms on the co-located FLAT rig, ≤ ~10 ms worst-case on the SEPARATED rig (speaker-dependent arrival-time bias), both inside the G2 ≤1-frame gate; the "±0.3 frame" claim removed (§2.3, §3.2; error term owned by 03 §4.5); (6) **transcript deletions aligned to 03 §8.2's take-time→program-time model** — the audio sequence becomes N+1 butt-joined clipped items, deletion-seam audio continuity is an explicit binary line in 03 §16.1's export spike (a different failure surface from video-switch boundaries), and `exportSrt` emits program-time, seam-split captions (§6.5, §6.8, §7); (7) the **9:16 per-angle crop policy** — tracked angle rides its `CropTrack`; untracked segments get a face-centred static crop seeded from one BlazeFace detection (fallback centre crop) as ordinary editable keyframes under the same ≤2-tap grammar; single-angle Short is the demo default (§7; policy owned by 03 §8.3); (8) the 4-player **"shared clock" named as authored master/follower synchronization** specified in 03 §8.1 — never presented as a library property (§6.2); (9) the **whisper default stated as a verbatim quote of 03 §11.4's authoritative table** — `tiny.en` q5_1 (≈31 MB) default, `base.en` q5_1 (≈57 MB) opt-in — closing the cross-doc default drift (§6.8).

---

## Changelog

**2026-07-18 — refinement round 1:** demo choreography re-baselined against 03's runtime bars — ≈3-minute film, elapsed-time-chip wait grammar, beat 8b pinned to the transcript-✓ chip with a measured ≤1×-realtime `tiny.en` precondition, two labelled export time-skips (§8.1); captioned-Short playback mechanism named — `.srt` rendered live via ExoPlayer `MediaItem.SubtitleConfiguration`, honesty line on camera (§7, §8.1 beat 10); QR pairing rewritten as MODE-1/MODE-2 with the `WifiNetworkSpecifier` approval dialog choreographed, build-week line items, and the NSD relief valve (§5.7, §8.1 beat 2); mid-take manual 3A corrected to live `Camera2CameraControl.setCaptureRequestOptions()` with re-arm as a bench-measured fallback (§4.3, Appendix A row 4); sync copy made acoustic-propagation-honest (§2.3, §3.2); transcript deletions mapped through 03 §8.2's program-time model with seam continuity as an export-spike gate and program-time `.srt` semantics (§6.5, §6.8, §7); 9:16 per-angle crop policy mirrored from 03 §8.3 (§7); the 4-player shared clock named as authored sync per 03 §8.1 (§6.2); whisper default quoted verbatim from 03 §11.4 — `tiny.en` q5_1 default, `base.en` opt-in (§6.8).

**2026-07-18 — scope-addition addendum (former Addendum B) integrated into the body**, companion to product Addendum A / technical Addendum C: vertical 9:16 export **#15** and `.srt` caption sidecar **#16** folded into the export screen (§7), the paid boundary (§6), the CropTrack spec (§6.6.3) and the transcript surface (§6.8); **QR-code pairing** added as §5.7; the demo close gained the two-artifact beat 10 (§8.1–8.2, §9.1); capture-stack references rewritten **CameraX-first** (§4.3, consistency note); stale phase-lock-default sync phrasing corrected to the audio-xcorr default (§5.1, §6.3, Appendix A row 14); storage/audio spec and V1.5 highlight extraction remain owned by 03 §C-integrated sections and product A.3.
