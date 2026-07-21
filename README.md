# MultiCam Experience (working title)

**A multicam production kit that fits in your bag and needs no computer.**
Turn 2–4 Android phones into a synced multicam rig that a tablet monitors, cuts, and exports — on-device, nothing uploaded.

> "Final Cut" appears in some documents as the original research reference (Apple's Final Cut Camera + Final Cut Pro for iPad Live Multicam workflow). It is an Apple trademark and is **not** this product's name.

## Why this exists

There is no pro-grade multicam editor on Android — LumaFusion withholds multicam as iOS-exclusive, DaVinci Resolve has no Android build, Premiere Rush is dead, CapCut is cloud-tethered. This project fills that vacancy: free capture + sync + cut, with a paid on-device AI layer (transcript-driven editing via whisper.cpp, auto-reframe, AI-assisted angle selection) that nothing reachable on an Android phone offers.

## Repository layout

| Path | What it is |
|---|---|
| `docs/00-build-setup.md` | Toolchain install + the week-1 decoder spike definition |
| `docs/01-product-spec.md` | Product spec — thesis, positioning, features, tiering, risks |
| `docs/02-design-spec.md` | Design spec — screens, interactions, demo choreography |
| `docs/03-technical-spec.md` | Technical spec — architecture + the build plan (executable by Claude Code) |
| `docs/05-review-log.md` | Adversarial review loop: per-round scores and objections |
| `docs/06-handoff.md` | Cold-start state-of-play for a fresh session |
| `spike/` | Canonical source of the decoder spike (4 files + README) |
| `DecoderSpike/` | The buildable spike app (Media3 1.10.1 CompositionPlayer, 2×2 grid, instrumented) |
| `Prompts used.txt` | The prompts that drove the spec process |

## Status

- Specs written and adversarially reviewed (architect + product director) to 8.3/8.3
- Spike APK builds clean (headless Gradle, `assembleDebug`)
- **Next:** run the spike on real hardware (2 phones + 1 tablet) — the decoder numbers gate the build

Built by a PM driving Claude Code end-to-end.
