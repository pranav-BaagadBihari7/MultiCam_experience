# Review Log

Adversarial review loop: senior architect + senior product director.
**Pass bar:** both reviewers overall >= 8.5 AND no single parameter < 7.5.
**Cap:** 4 rounds.

## Result: NOT CONVERGED after 4 round(s) — see objections below

## Final round (4) scores

| Parameter | Architect | Product Director |
|---|---|---|
| fcp_fidelity | 8.5 | 8 |
| scalability | 8 | 8 |
| ai_features | 7.5 | 7.5 |
| latency | 8 | 8.5 |
| on_device_ai | 8.5 | 8.5 |
| creator_wow | 7.5 | 7.5 |
| android_differentiation | 8 | 8.5 |
| tech_stack | 8 | 8 |
| **OVERALL** | **8** | **8** |

## Outstanding objections at close

### [architect] tech_stack -> 03-technical-spec.md
**Objection:** The entire capture pipeline (SS7.1-7.2) depends on Camera2 concurrently outputting three surfaces from one camera: the HLG10 10-bit master-encoder input surface, a 10-bit-readable SurfaceTexture feeding the HLG10->SDR GL tonemap that drives the proxy encoder, and the viewfinder. This is the hardest feasibility question in the whole plan (sampling a 10-bit HLG EXTERNAL_OES texture in GL ES requires specific EGL/YUV-target extensions, and the combination must sit inside the device's supported stream-combination table at its hardware level), yet it is not spiked as a distinct item - it is folded into the 'concurrent-encoder' spike (SS16), which measures encoder COUNT, not stream-combination or 10-bit-GL feasibility. If this exact surface set is unsupported on the owned SoCs, the dual-encoder-from-one-camera architecture, the 'editing proxy exists at STOP' hero guarantee (SS6.4), and WebRTC monitoring all break simultaneously - and it would be discovered mid-build, not week 1.

**Required fix:** Add an explicit week-1 CAMERA-STREAM-COMBO spike (separate from the encoder-count spike) to SS16 that verifies, on both owned phones, the concurrent output set {HLG10 encode surface + 10-bit GL SurfaceTexture + preview} against the device's reported stream-combination table and confirms 10-bit HLG sampling in the GL tonemap shader. State a pre-committed fallback (e.g. SDR-only capture dropping HLG10 to eliminate the 10-bit-GL path, or deriving the proxy by a MediaCodec transcode/copy rather than a live GL tonemap) so a red result does not cascade into the master, proxy, and monitor paths at once.

### [architect] ai_features -> 03-technical-spec.md
**Objection:** By the authors' own analysis (SS10.4; product SS5.5), the flagship angle-selecting director is expected to be 'marginal, not safe' even in the >6dB band and to miss the <=2-overrides/min gate in the 3-6dB band, so the realistic V1 deliverable is the timing-only degrade. But timing-only is explicitly defined as having NO quality gate - the spec says it 'cannot fail its own gate because it no longer makes the claim the gate scores.' That means the mode most likely to actually ship has no measurable success criterion at all: nothing prevents timing-only from proposing noisy, poorly-placed cut points (speaker-transition timing from energy is itself unreliable at 3-6dB) while remaining unfalsifiable.

**Required fix:** Define a concrete, falsifiable quality bar for the timing-only director in SS10.4 - e.g. on the SPIKE-AUDIO test clip, <=X spurious-or-missed cut POINTS per minute, each dismissable/movable in <=1 tap, measured against a human reference - so the most-probable shipped mode has a pass/fail criterion rather than being defined into un-testability.

### [architect] latency -> 03-technical-spec.md
**Objection:** The long-take drift correction (SS4.5, SS6.4) is a two-point LINEAR offset+rate fit from an early ~10s and a late ~10s audio window. But the demo deliberately runs under thermal stress (G3: 20 min while charging), and thermal throttling changes crystal oscillator frequency nonlinearly precisely on the devices being heated. A straight-line fit whose two endpoints both satisfy <=1 frame can still exceed the G2 budget in the thermally-hottest middle of a 40-min take. G2 is only clapper-checked at start and minute 20, so a mid-take nonlinear excursion would pass validation yet desync the assembled timeline where it matters most.

**Required fix:** Specify either a mid-take third correlation window (piecewise/three-point fit) or a bounded re-correlation cadence (e.g. every ~10 min), and state the residual-drift budget under a documented worst-case throttle profile, so the <=1-frame claim holds across a throttled long take rather than only at its endpoints.

### [architect] creator_wow -> 02-design-spec.md
**Objection:** The default program preview and the load-bearing Beat 7 scrub both claim to run on 'the active angle's plain ExoPlayer surface' (SS6.2; 03 SS8.1), but the conjured-camera angle (CAM1-A punch-in) is a live crop/zoom of another angle, not a raw decoded surface. Previewing it interactively during scrub therefore requires applying a Media3 video Effect (crop/zoom via the CropTrack) on ExoPlayer PLAYBACK - a newer, less-battle-tested path than plain playback - and that rendering route is never specified. Beat 7's export path is defined; its live-scrub preview path is not, so the demo's guaranteed spike-independent wow rests on an unspecified interactive rendering mechanism.

**Required fix:** Specify in 02 SS6.6 / 03 SS8.1 how the CropTrack is rendered on the interactive scrub path - ExoPlayer setVideoEffects vs a dedicated GL SurfaceView crop shim - name the exact API, flag its experimental/maturity status, and give a fallback, so Beat 7's live preview (not merely its export) has a defended, buildable path.

### [architect] on_device_ai -> 03-technical-spec.md
**Objection:** The thermal governor (SS12) sheds heat in the wrong order relative to the actual heat sources. Its early rungs (MODERATE->SEVERE) drop only on-phone preview and monitoring NETWORK send, while both hardware encoders + the camera ISP + charging - the dominant heat contributors, and exactly the load behind Blackmagic's cited ~10-min stop - keep running at full rate until CRITICAL (encoder bitrate) and EMERGENCY (encoder resolution). The claim that predictive early degradation buys the 10->20-min improvement G3 demands is therefore unvalidated and likely optimistic, because the governor's early actions barely touch the real thermal budget.

**Required fix:** Require the week-1 thermal spike (SS12/SS16) to measure the MARGINAL runtime bought by each governor rung, not merely 'proxy survives to SEVERE.' If preview/network-shedding alone does not close the 10->20-min gap, promote an encoder-load lever earlier in the ladder (e.g. proxy resolution 720p->540p at MODERATE, or a single-encoder + post-record-proxy thermal fallback) before SEVERE, so the G3 target rests on the lever that actually moves temperature.

### [director] creator_wow -> 01-product-spec.md §1.6 / §8 (and 02-design-spec.md §11 D7)
**Objection:** The spec names segment-mismatch/demand (§9.1) as the single biggest unvalidated claim, yet the only pre-build demand test is a half-day WOW check (does Beat A read as capability vs gimmick; is the rig too much work). Willingness-to-pay — the actual §9.1 risk — is never tested until a post-demo beta funnel that may never be funded. The biggest risk is being retired last and cheapest, while 8 weeks buy down the second-biggest risk (buildability). Separately, the demo's presence-wow is fragile: Beat 7 is admitted near-commodity auto-reframe (CapCut/InShot ship it) and Beat 9's payoff is an ABSENCE ('no computer'), not a novel on-camera capability a savvy creator hasn't seen.

**Required fix:** Add a purchase-intent smoke-test to the week-0 gate BEFORE committing the sprint: put the $49 one-time offer against the demo mock as a fake-door / waitlist / pre-order to 3-5 (ideally 15-20) real non-consumption producers and record a hard willingness-to-pay signal, not just a wow verdict. Pre-commit a no-go threshold. In parallel, identify one genuinely-novel PRESENCE wow for the demo (candidate: a reliable-enough 'the cut paints itself' beat, or transcript-marker cutting) so the hero is not carried solely by a commodity reframe plus an absence.

### [director] ai_features -> 01-product-spec.md §5.5 / 03-technical-spec.md §10
**Objection:** V1's only shipped 'AI' is an energy-argmax VAD director that the spec's own cited evidence (EditIQ, Durlach) labels the KNOWN-WEAK approach, and the spec plans for its quality gate to be 'marginal, not safe,' likely degrading to 'suggested cut points' or being cut. All genuinely-learned AI (whisper transcript cutting, LR-ASD, dialogue-aware direction) is deferred to V2, and the smart-reframe is honestly relabeled as not-AI. So the flagship 'AI Director' is at real risk of shipping as either nothing or a confirmable default, leaving V1 with no differentiated learned-AI beat.

**Required fix:** Stop treating timing-only ('suggested cut points') as a week-6 degrade discovered under pressure and COMMIT it as the V1 director baseline, since energy-argmax angle-selection is admitted known-weak — angle choice becomes the confirmable highest-energy default from day one. Additionally, evaluate pulling whisper.cpp-tiny transcript-marker cutting (already proven shippable per VN, MIT, clean patent surface) forward to V1 to give the paid tier one genuinely-learned on-device capability that is not commodity CV.

### [director] scalability -> 03-technical-spec.md §16 / 01-product-spec.md §5.7
**Objection:** Week 1 is labeled 'all spikes, no product code' but stacks ten spikes (a-j), several of which secretly require infrastructure scheduled for weeks 2-5: the concurrent-encoder spike IS the raw dual-encoder capture pipeline (week 3 work); the face-detector wide-geometry spike requires authoring the §11.2 One-Euro/interpolation/damping tracker (real product code); the audio-xcorr baseline needs recorded multi-device takes. For a solo builder with near-zero Android experience driving Claude Code, compressing this into one week — the premise on which the '6 weeks + 2 slack' claim rests — is the least-defended quantitative claim in the set.

**Required fix:** Re-sequence week 1 into an explicit 2-phase bootstrap: (1) a standalone-runnable spike batch using pre-recorded stock-camera footage (decoder budget, SPIKE-AUDIO, heterogeneous export, CompositionPlayer seek, thermal) that needs no app infrastructure; (2) an infra-dependent spike phase (concurrent dual-encoder, face-tracker quality bar) that is honestly acknowledged to require a minimal capture/tracker scaffold and budgeted as ~1.5-2 weeks. Correct the '6 weeks + 2 slack' claim to reflect the bootstrap cost, or move the two infra-dependent spikes into week 2-3 with their gates.

### [director] tech_stack -> 03-technical-spec.md §8.3 / §16
**Objection:** The demo's closing money-shot (Beat 9 'finished cut, no computer') and gate G6 rest on a two-sequence Transformer EXPORT concatenating 20+ clipped items from heterogeneous sources (Pixel + Samsung, different res/color/encoder). The spec correctly flags this as measured-pending with NO cited open-bug guard for audio-continuity-across-heterogeneous-video-switches on export — a genuine load-bearing unknown on the critical path — but still treats direct heterogeneous passthrough as the primary path and the homogenize-to-common-intermediate transcode as a contingency, without budgeting the fallback's extra transcode cost into G6.

**Required fix:** Promote the two-sequence heterogeneous-export spike to the #1 week-1 priority (it gates both Beat 9 and G6). Make the homogenize-all-proxies-to-a-common-intermediate export path the DEFAULT export architecture rather than the fallback, since cross-vendor passthrough is unlikely, and budget that extra per-clip transcode into the G6 ≤1.0x-realtime target now — not as a week-8 discovery. Explicitly verify audio continuity across every video-item boundary on export as a pass/fail line.

### [director] fcp_fidelity -> 02-design-spec.md §3.3 / §3.4 / Appendix A rows 18-19
**Objection:** The Library/Sessions benchmark labels 'reopen a prior shoot' and 'master<->proxy association' as MATCH (BEAT on transparency), but the record-local architecture (masters never leave the capture phone) imposes a workflow tax FCP does not: reopening a shoot for master-quality re-export requires physically reconnecting the specific phones that hold those masters over the LAN ('masters on CAM2 offline', 'gather masters'). FCP keeps proxies and originals in one library; the user never faces 'your footage is on a device that isn't here.' Labeling this MATCH understates a real capture->edit fidelity regression on the core reopen/re-export path.

**Required fix:** Re-label the multi-device master-availability workflow as an honest DIVERGE (not MATCH) in §3.4 and Appendix A, and specify the default proxy-quality re-export path so that reopening and exporting a prior shoot never REQUIRES reassembling the physical rig — master-quality re-export becomes an explicit opt-in that prompts for the offline phones, while a fully-usable proxy-quality export is always available from the tablet alone.

## Research verification

15 claims survived 3-vote adversarial verification.
5 claims were killed (2/3 refutation) and excluded from the specs.

---

# Final Revision (post-interview corrections)

Applied: corrected Resolve framing (Studio-paid + Android-absent → AI multicam is differentiated on
Android; vacancy stays the moat) · whisper.cpp transcript cutting pulled into V1 · week-0 WTP gate
dropped · five architect engineering fixes + three director fixes folded in.

## Result: still short after 2 revision round(s)

| Parameter | Architect | Product Director |
|---|---|---|
| fcp_fidelity | 8 | 8.5 |
| scalability | 8 | 8 |
| ai_features | 8 | 8 |
| latency | 8.5 | 8.5 |
| on_device_ai | 7.5 | 7.5 |
| creator_wow | 8 | 8 |
| android_differentiation | 8.5 | 8.5 |
| tech_stack | 8 | 8 |
| **OVERALL** | **8** | **8.2** |

Weakest parameter — architect: 7.5, director: 7.5.

## Outstanding objections at close

### [architect] on_device_ai → 02-design-spec.md §6.8 (reconcile with 01 §5.6 / 03 §11.4, §13, Appendix)
**Objection:** The three specs FLATLY CONTRADICT each other on the whisper model V1 actually ships — the single on-device learned model decision-2 was told to specify. Design §6.8 (line 446) says 'V1 ships f16, not quantized', tiny≈75MB/base≈140MB, and explicitly calls q5_1 'not what V1 bundles' — while claiming it 'matches [03 §11] exactly'. Product §5.6 and Tech §11.4/§13/Appendix say the opposite: V1 bundles q5_1 at 31/57MB, f16 is 'NOT shipped', and 'design §6.8 [is] corrected to these q5_1 sizes'. Each side declares itself the reconciled authority. This is not cosmetic: f16-140MB vs q5_1-57MB changes APK size, on-device RAM footprint, NEON runtime speed, and thermal headroom for the post-record pass — the exact on_device_ai fidelity being scored. It also falsifies the 'reconciled identically across all three specs' claim the docs repeatedly assert.

**Required fix:** Pick one weight set — q5_1 (31/57MB) is the correct on-device choice and is what product+tech already carry — and rewrite design §6.8 line 446 verbatim to match 03 §11.4's table (q5_1, 31/57MB; f16 75/142MB explicitly NOT shipped). Delete design §6.8's 'V1 ships f16, not quantized' and 'q5_1... not what V1 bundles' sentences. Only after they actually agree, keep the 'authoritative table in 03 §11.4' pointer.

### [architect] ai_features → 03-technical-spec.md §10.4 and §11.4
**Objection:** The timing-only director's new falsifiable bar (fix B: ≤1 spurious/missed cut-point per minute) is load-bearing because timing-only is the most-likely-shipped mode, and §10.4 credits whisper transcript boundaries for pulling the rate from ~2-4/min (energy-only) toward ≤1/min. But that assist silently assumes WORD-LEVEL whisper timestamps within ±150ms on noisy multi-speaker room audio — an unmeasured bar that design §6.8 itself concedes may degrade to SEGMENT-LEVEL on room audio. If whisper degrades to segment-level, the transcript assist to cut-point placement largely evaporates and §10.4's ≤1/min bar loses its stated path to being met, but §10.4 does not say what timing-only scores against in that case.

**Required fix:** In 03 §10.4, state the ≤1/min timing-only bar's explicit dependency on word-level whisper, and pre-commit the timing-only outcome when whisper is only segment-level accurate on the SPIKE-AUDIO clip (e.g. bar relaxes to the hold-longer/DMIN degrade of step 1, or director cuts to step 3) — so the most-likely-shipped mode's quality claim is not conditioned on an unmeasured ASR bar.

### [architect] creator_wow → 01-product-spec.md §5.7 and 02-design-spec.md §8
**Objection:** The demo's ONE genuinely-novel co-hero (beat 8b, whisper transcript cleanup) is cut-ladder rung 2 — it reverts to V2 if week 6 slips — and beat 7 is self-admitted near-commodity. In the slip case the wow reverts to exactly the commodity(beat7)+absence(beat9) pairing the creator_wow objection was raised against. The only named protected novel-positive backstop (G4 pulled-camera reconciliation) is conceded to 'read most strongly to someone who grasps what sync is' — i.e. NOT the non-consumption buyer this whole hero section is re-pinned onto.

**Required fix:** In product §5.7 / design §8, name the novel positive beat that survives when BOTH whisper is cut AND the face-tracker smoothness bar is missed, engineered for a buyer with no multicam history — or concede that in the double-slip case the demo's wow is commodity+absence and adjust the cut-ladder so whisper (or a degraded segment-level transcript trim) is protected above smart-reframe/take-review rather than below the director.

### [architect] fcp_fidelity → 02-design-spec.md §4.3 and Appendix A
**Objection:** The manual-control fidelity claim is honestly conditioned on HARDWARE_LEVEL_FULL, but the FULL fraction of the MPC≥34 population is unknown until the week-1 audit, and a below-FULL owned phone forces auto-only capture — a strictly-less-capable-than-FCP-Camera experience the demo would then be shot in. The spec logs this as an Appendix A DIVERGE (rows 4/16a/18a are cross-referenced from §3.4, §4.3, §6.8, §7), but Appendix A itself was not verifiable in the reviewed span, so the cited rows may not exist to carry the DIVERGEs the body promises.

**Required fix:** Confirm 02-design-spec Appendix A actually contains the cited rows (row 4 manual-control-below-FULL + fixed-geometry, row 16a transcript BEAT, row 18a multi-device master DIVERGE); if any are missing, add them. Add to §4.3 the explicit demo-narrative fallback for the case where the owned phones report non-FULL (demo shot auto-only, manual strip disabled-with-reason), so the fidelity claim is not silently conditional.

### [architect] scalability → 03-technical-spec.md §16
**Objection:** The ~7.5-8 week envelope carries only a ~0.5-1 week debugging buffer against a stack the plan itself flags as high-attrition for a near-zero-code solo builder: raw-Camera2 stream-combo integration ('the hardest feasibility question in the plan'), Media3 experimental surfaces, the JNI/NDK whisper build, and GL tonemap/crop shaders. A single multi-week surprise on the protected week-3 stream-combo integration exhausts the buffer, and the pre-committed relief valve is dropping the demo 4→2 angles — which is scope loss on a protected build week, not slack. The buffer is thin relative to the named risk.

**Required fix:** In 03 §16, tie the stream-combo integration to a CALENDAR trigger, not just a spike color: 'if stream-combo integration debugging exceeds N days into week 3, fall to SDR-only capture (§7.2 fallback i) immediately' — converting the hardest-item risk into a dated, bounded decision rather than an open-ended debugging hole that eats the buffer before the relief valves fire.

### [architect] tech_stack → 01-product-spec.md §5.6 (and cross-spec) plus 02 §6.6.3 vs 03 §8.1
**Objection:** The whisper contradiction proves the repeated 'reconciled identically across all three specs' / 'matches it exactly' assertions were not actually verified, which undermines confidence that the other cross-spec reconciliations (Media3 1.10.1 signatures, latency/thermal budgets, the CropTrack render path) were verified rather than asserted. Concretely, the CropTrack live-scrub primary is already stated two different ways: design §6.6.3 names a custom 'MatrixTransformation whose getGlMatrixArray(presentationTimeUs)' effect, while tech §8.1 names 'a media3-effect Crop (optionally Presentation/ScaleAndRotate)' — a real API-surface divergence on the fix-D path.

**Required fix:** After fixing whisper, run a cross-spec consistency pass and either substantiate or delete every 'reconciled identically' claim. Specifically reconcile the CropTrack render primary between 02 §6.6.3 and 03 §8.1 to a single named effect (MatrixTransformation vs Crop+Presentation), since the interactive scrub path for the every-branch wow must resolve to one API.

### [director] on_device_ai → 02-design-spec.md (reconcile with 01 and 03)
**Objection:** The whisper model spec — the flagship new V1 learned-AI feature — carries a live three-way contradiction. Product §5.6 (L409) and Technical §11.4/§3/Appendix (L653, L858) state V1 bundles GGML q5_1 weights (tiny ~31MB default / base ~57MB) and explicitly 'f16 ~75/142MB NOT shipped'; Design §6.8 (L446) states the OPPOSITE — 'V1 ships f16, not quantized' at 75/140MB, calling q5_1 'not what V1 bundles.' Tech §11.4 asserts 'design §6.8 is corrected to these q5_1 sizes' and Product §5.6 asserts 'design §6.8 matches it' — both demonstrably false. The docs also disagree on the default model (tech: tiny; product: base.en). This is buried, not surfaced, and it directly falsifies the repeated 'reconciled identically across all three specs / authoritative' claims — which corrodes trust in every other 'verified/reconciled' assertion in the corpus.

**Required fix:** Pick ONE bundled quantization, default model, and size triple; rewrite Design §6.8 to actually match Technical §11.4; and delete every 'reconciled identically / authoritative / design §6.8 matches it' claim until the three tables are byte-for-byte identical. This is a concrete on-page defect, not a contingency, and it is trivially fixable.

### [director] ai_features → 01-product-spec.md §5.7 + 03-technical-spec.md §16
**Objection:** whisper transcript cutting is the ENTIRE answer to 'V1 needs one real learned-AI beat, not just an energy-argmax angle picker' — yet it sits at cut-ladder rung 2 (drops to V2 on any week-6 slip) inside a plan whose own committed envelope is ~7.5-8 weeks against an 8-week sprint with slack 'thinned to near-zero.' So the learned-AI claim is schedule-contingent on the feature most likely to be cut first; in the slip case V1 ships only deterministic CV (framing) + a hand-written FSM director, i.e. exactly the ai_features hole the prior round flagged.

**Required fix:** Either re-order the cut ladder so the deterministic take-review floor (#12) is cut BEFORE whisper (make whisper the last learned capability to drop, not the second), OR state plainly in §5.7/§16 that in the slip branch V1 ships with NO genuinely-learned AI and let the ai_features claim be scored on that floor rather than on the stretch case.

### [director] creator_wow → 02-design-spec.md §8 + 01-product-spec.md §1.4
**Objection:** The demo's one genuinely-novel, non-commodity, buyer-legible wow is beat 8b (transcript filler/dead-air cleanup), which rides whisper — cut-ladder rung 2. The docs concede beat 7 (conjured camera) is 'near-commodity' and beat 6 (sync reveal) 'lands only on veterans' and beat 9 ('no computer') is 'largely an absence.' So if whisper slips, the demo reverts to precisely the 'commodity + absence' the creator_wow objection named, for the exact non-consumption buyer the whole hero section is re-pinned around.

**Required fix:** Specify a protected novel-capability wow that does NOT depend on whisper — e.g. promote the G4 pulled-camera-lands-correctly-on-the-synced-timeline reconciliation (§5.6/§6.7) to a first-class demoed wow beat, OR make whisper demo-critical/non-cuttable rather than rung-2 — so a genuinely-novel beat survives the whisper-drop branch in 02 §8/§9.

### [director] scalability → 03-technical-spec.md §16 + 01-product-spec.md §5.7
**Objection:** The honest committed envelope is ~7.5-8 weeks inside an 8-week sprint with slack the docs themselves call 'thinned' and 'near-zero,' while the three highest-attrition surfaces (raw-Camera2 stream-combo, whisper NDK/JNI native build, GL HLG10 tonemap) all sit in the back half, and the plan names 'a near-zero-Android builder debugging experimental surfaces' as its binding constraint yet leaves only ~0.5-1 wk buffer. A single multi-week debugging surprise on the stream-combo (the plan's self-declared hardest feasibility question) blows the demo; confronting this is not the same as de-risking it.

**Required fix:** State explicitly whether the week-8 demo date is movable. If it is not, pre-commit the fuller descope (2-angle + no-whisper + no-director + SDR-only capture) as the guaranteed week-8 deliverable FLOOR and re-label the 4-angle+whisper+director cut as the stretch, so the schedule's baseline is what it can guarantee, not the ceiling it hopes for.

### [director] tech_stack → 03-technical-spec.md §7.2 / §16.2 (and product §2.4/§5.3)
**Objection:** The demo-critical capture pillar (week-3 build, gated on the §16.2 stream-combo spike) requires a near-zero-code builder to ship a 10-bit-HLG-sampling GL tonemap shader (GL_EXT_YUV_target + EGL_EXT_gl_colorspace_bt2020_hlg, self-noted 'not guaranteed per-SoC') on a stream combo the doc calls 'the single hardest feasibility question in the plan.' The pre-committed SDR-only fallback (i) silently sacrifices the HLG10 master that §2.4/§5.3 treat as the load-bearing RELIABILITY pillar (the only AOSP-mandated 10-bit profile), yet no demo gate protects master bit-depth — G8 checks CFR only. So the headline reliability pillar can vanish with no gate catching it.

**Required fix:** Add an explicit demo gate asserting HLG10 masters on both owned phones (or, if SDR-acceptable, downgrade the 'HLG10 as reliability pillar' claim consistently across product §2.4/§5.3 so the fallback is not a silent retraction of a headline). Additionally pre-stage the MediaCodec-transcode-proxy fallback (ii) as the DEFAULT capture-proxy path if the week-1 spike shows the novice-authored GL HLG shader is where the debugging risk concentrates, rather than treating the live GL tonemap as the primary.
