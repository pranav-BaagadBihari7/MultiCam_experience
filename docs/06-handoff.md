# Handoff — State of Play & Next Steps

*For any fresh session picking this project up cold. Written 2026-07-18. Read this first; everything else is depth.*

---

## What this is, in one paragraph

An **Android phone-native multicam production kit**: 2–4 phones as synced cameras, a tablet that monitors, cuts (manually + AI-assisted), and exports — episode *and* vertical Short *and* captions — with **no computer, no upload, no cable**. It is *not* a Final Cut Pro clone; the reference workflow is Apple's Final Cut Camera + FCP-iPad Live Multicam. **"Final Cut" is an Apple trademark and is a working title only — it cannot ship.** The durable moat is **market vacancy**: no pro multicam editor exists on Android at all (LumaFusion withholds it as iOS-exclusive, Resolve has no Android build, Premiere Rush is dead, CapCut is cloud-tethered), because Android monetizes pro video at ~half of iOS — four capitalized companies rationally declined this market. That economics is both the moat and the ceiling, and the specs say so honestly.

## Where it stands

Five spec docs + a spike app, produced through ~3 rounds of multi-agent research (claims 3-vote adversarially verified) and 6 rounds of adversarial review (senior-architect + product-director personas). **Final scores: architect 8.0 / director 8.2 against an 8.5 bar** — held by fine-grained precision objections, not substance; the loop was deliberately capped. The known table-inconsistency that pinned `on_device_ai` at 7.5 (whisper model sizes) was fixed by hand afterward. **No code beyond the spike has been written. No spike has been run.**

| Doc | What |
|---|---|
| `00-build-setup.md` | Toolchain install + the week-1 decoder spike definition + results table (empty) |
| `01-product-spec.md` | Thesis, beachhead, tiering, risks, naming + **Addendum A** (scope additions) |
| `02-design-spec.md` | Five screens, interaction grammar, demo choreography + **Addendum B** |
| `03-technical-spec.md` | Architecture, budgets, week-plan — the executable build doc + **Addendum C** |
| `05-review-log.md` | All scores + every surviving objection, all rounds |
| `spike/` | DecoderSpike app source (4×1080p 2×2 CompositionPlayer grid, instrumented) — **written, never run** |

## Locked decisions — do not relitigate without new evidence

1. **Free tier:** capture + multicam + auto-sync + basic cut. **Paid:** the AI/learned layer (transcript cutting, reframe, director, vertical export, captions).
2. **whisper.cpp transcript cutting is GUARANTEED V1 and non-cuttable** — the paid tier's one genuinely-learned capability and the demo's protected novel wow (dead-air/filler removal). Model: **GGML q5_1** — tiny/tiny.en ≈31 MB, base ≈57 MB. f16 is NOT shipped.
3. **The date is movable — quality over calendar.** Week numbers are sequencing estimates gated on go/no-go spikes (~7.5–8 weeks estimated). A red gate flexes the calendar, never silently shrinks the deliverable. Relief valves, in order: opt-in composited preview → director timing-only polish → 4→2 angles. Never whisper, never the spine.
4. **AI framing (corrected twice — final):** DaVinci Resolve's AI (SmartSwitch, transcript editing) is **Studio-paid ($295) AND absent from Android phones entirely** — so on-device AI multicam is genuinely differentiated *on this platform*. But market-vacancy, not AI, is the load-bearing moat. We serve a platform Blackmagic declines; we don't out-model them.
5. **Capture stack: CameraX-first** (Addendum C.1) — raw Camera2 only if the stream-combo spike proves CameraX can't express the three-surface set. **Sync default: audio cross-correlation** (GCC-PHAT, SNTP-primed); the raw-Camera2 live phase-aligner is an optional upside, not the plan.
6. **V1 scope additions (Addenda, 2026-07-18):** #15 vertical 9:16 export (rides the reframe CropTrack), #16 SRT caption sidecar (rides whisper). **V1.5 named:** highlight extraction, USB/lav audio input. **V2:** USB-C SSD recording, DeX controller, LR-ASD fusion director.
7. Solo build, near-zero Android experience, Claude Code authors; 2 owned phones + 1 tablet; demo-quality deliverable, not a Play Store release. minSdk 33. Device allowlist, not capability detection.

## The one load-bearing bet

**Media3 `CompositionPlayer` (`@ExperimentalApi`, pinned 1.10.1 — do not float the version).** The whole on-device-editing thesis rests on the Composition preview/export model. The week-1 decoder spike measures whether 4×1080p concurrent decode holds on the owned tablet. **A red result is a parameter change (drop proxy resolution), not a thesis change.** Fallback ladder: 540p proxies → 2×2@24fps → single-angle + still grid → custom MediaCodec+GL compositor. Note the architecture already hedges: interactive preview rides *plain ExoPlayer* (single decode); `CompositionPlayer` multi-input is reserved for export-path work.

## Do next, in order

1. **Install:** Android Studio (start first, it's ~10 GB), Git, scrcpy, DaVinci Resolve — `00-build-setup.md` Part 1. Enable developer mode + wireless debugging on all 3 devices (Part 2).
2. **Run the decoder spike** (`spike/README.md`): create the DecoderSpike project, drop in the 4 source files, push 4 test clips, run 60 s on all three devices. **Fill the results table in `00-build-setup.md`.**
3. **Run the remaining week-1 spikes** per `03 §16` (updated by C.1): CAMERA-STREAM-COMBO (CameraX path first: HLG10 + 10-bit GL texture + preview), SPIKE-AUDIO (xcorr sync accuracy on real 2-phone takes), heterogeneous two-sequence export, thermal (marginal minutes per governor rung), whisper timestamp accuracy (±150 ms bar), face-detector wide-geometry.
4. **Only then start the build**, following `03 §16`'s sequence — capture core cannot start before its gating spike is green.

## Risks the builder must hold in mind

- **CompositionPlayer is experimental:** seek **crashes** in 2×2 grid mode ([androidx/media#2439](https://github.com/androidx/media/issues/2439), open >1 yr); compositor **deadlock** on multi-sequence transitions ([#2742](https://github.com/androidx/media/issues/2742)); freeze on declared-but-absent audio ([#2854](https://github.com/androidx/media/issues/2854)). The spike and architecture route around all three — don't "fix" them back in.
- **LOG has no Android API.** Not in Camera2, not in CameraX, no OEM SDK. HLG10 is the only mandated 10-bit path. Don't re-propose LOG.
- **ffmpeg-kit is archived (July 2026), partly over codec patent liability.** MediaCodec/Media3 only. No FFmpeg on the critical path, ever.
- **Sync reference code is archived + Pixel-2-era** (libsoftwaresync ~250µs, Apache-2.0) — which is fine, because audio-xcorr is the default and clears the gate; the port is optional upside.
- **OEM fragmentation is unfixable solo** — ship a device allowlist (Blackmagic's approach), gate on `HARDWARE_LEVEL_FULL` for manual controls, and expect the stock camera to out-process you on image quality.
- **Media3 facts are verified at the 1.10.1 tag** (see 03's corrections block): `EditedMediaItemSequence.Builder(Set<TrackType>)`, `MultipleInputVideoGraph.Factory()` mandatory for multi-video-input, `VideoCompositorSettings` in `.common`, `androidx.annotation.OptIn` (not `kotlin.OptIn`), lint-enforced. Trust these over memory.

## What would close the last half-point (optional polish)

The remaining objections in `05-review-log.md`'s final section are consistency-grade: verify design Appendix A rows exist as cited, ensure no stray "reconciled identically" claim outruns reality, and the HLG10-demo-gate wording in product §2.4/§5.3 vs the SDR fallback. Worth an hour someday; not worth blocking the build. **The spikes are the real next information — specs are done enough to build against.**
