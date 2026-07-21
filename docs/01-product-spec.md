# Product Spec — Multicam Capture & Cut for Android

> **Working title only.** "Final Cut" is a registered Apple trademark in the exact class and goods of this product. It cannot ship. See [§10 Naming](#10--naming).

**Document status:** V1 scope-locking document. Written after five verified research passes, three of which materially contradicted the original thesis. Those contradictions are surfaced and resolved below, not buried.

**Audience:** the solo builder, and Claude Code as the implementer. Every cut in §5 carries its justification inline so that no one — including future-you at week 6, tired and tempted — reopens a settled decision without new evidence.

**The one-sentence version:** Turn 2–4 Android phones into a synced multicam rig that a tablet monitors, auto-assembles, and exports — with no desktop, no upload, and no cable.

---

## 1 — Thesis & positioning

### 1.1 The thesis has moved. Read this before anything else.

The original thesis was: *"Blackmagic captures then hands off to DaVinci Resolve on desktop. That handoff is the gap. We close it with an AI Director."*

**Both halves of that sentence needed correcting — but a widely-repeated over-correction is itself wrong and is fixed here.**

**Correction 1 — the AI Director is not the *moat*, but on Android it is not "table stakes" either. An earlier draft over-corrected into pessimism; the verified facts are more favourable.** DaVinci Resolve 20 (May 2025) ships **AI Multicam SmartSwitch**: automatic angle cutting from active-speaker detection, trained on multicam footage¹ — an established Resolve 20 feature, not a preview (absent from the Resolve 21 "what's new" page²). An earlier draft treated this as *"Blackmagic ships the AI free, so ours is table stakes we relabel away."* **Two verified facts break that premise:**

- **SmartSwitch — and Resolve's entire Neural Engine (transcript editing, Magic Mask, Voice Isolation, Smart Reframe, the lot) — is almost certainly Studio-only ($295), not free.** The strong weight of independent evidence (DaVinci Resolve Club, Filmora, ToolFarm) is that the whole Neural Engine is Studio-gated; the one outlier blog claiming it is free is likely AI-generated content (§3.4). Q2 still verifies this against Blackmagic's own docs, but the planning assumption is **paid**, not free. *(Fix the "SmartSwitch free" claim wherever the old draft made it — it is Studio-paid.)*
- **Resolve does not run on an Android phone at all — iPad and desktop only. Every "Resolve APK" is malware.** So on an Android phone, **nothing reachable does AI multicam — paid or free.** Blackmagic Camera on Android hands off to *nothing on the same device.*

**So the honest framing is not "AI is table stakes."** It is: **on Android, on-device AI multicam is genuinely absent, and we bring it to a device Blackmagic refuses to serve.** We are **not** out-AI-ing Blackmagic — a solo PM will not out-model a colour-science team, and we do not try. We are **serving a platform they decline.** On the phone in the beachhead's bag, our AI Director and (now V1, §5.5) our whisper.cpp transcript cutting are a legitimate, differentiated part of the value prop — not a relabeled commodity.

**But be precise about the moat.** A determined user with an M-series iPad can buy Resolve Studio and out-muscle anything we ship; we will not win a capability arms race. **The single load-bearing, durable moat remains market-vacancy** (Correction 2) — the AI is differentiated *on the platform we serve*, not a moat in its own right. Where we *do* have a concrete, honest opening against SmartSwitch specifically: its cuts are **speaker-only**, it **needs a usable audio track on every angle**, it **does not trim dead air**, and Blackmagic's own forum documents **"no audio after the switch" bugs**. Our on-device answers — **dead-air handling, transcript-driven structure, and single-mixed-track handling** (§5.5, §5.6) — are real differentiation against SmartSwitch's narrowness. The durable moat is still the platform; the AI is a genuine, defensible feature on it, not table stakes and not the moat.

**Correction 2 — the real gap is much bigger than a handoff, and it is economic, not technical.** There is **no pro-grade multicam editor on Android at all**:

| App | Multicam? | On Android? | Source |
|---|---|---|---|
| **LumaFusion** | Yes — 6 angles, timecode/waveform/manual sync | **Built, shipping, deliberately withheld.** LumaTouch's own purchase page lists Multicam Studio under *"iOS-exclusive features."* Android gets the $29.99 base app; iOS gets multicam for $19.99 one-time. | luma-touch.com/purchase |
| **DaVinci Resolve** | Yes (+SmartSwitch, **Studio-only $295**) | **No Android build at all — iPad + desktop only; every "Resolve APK" is malware.** So Blackmagic Camera on Android hands off to *nothing on Android — not even a tablet.* | blackmagicdesign.com |
| **KineMaster** (750M+ installs) | **No** | Yes | Confirmed against vendor site |
| **Adobe Premiere Rush** | No | **Dead** — discontinued, support ends Sept 30 2026 | helpx.adobe.com/premiere-rush/kb/end-of-life |
| **Descript** (invented transcript editing) | Yes (Automatic Multicam) | **No mobile app on any platform, ever** | descript.canny.io |
| **CapCut** | Unverified on mobile (Q1) | Yes — but cloud-tethered, pre-uploads your footage, HTTP 451 in India | capcut.com privacy policy |

**Why the vacuum persists, and why it will keep persisting:** Android monetizes at roughly half of iOS per user (~$6.19 vs ~$12.77 per app; ~77% of new subscription apps launch iOS-first). LumaTouch, Blackmagic, Adobe and Bending Spoons are all *rationally* declining to serve Android pro video. That is the moat's real substance.

**It is also the honest warning, and it must be said in the same breath as the opportunity: the same economics that left this space empty will cap what we earn in it.** Nobody is absent from this market by accident. We are entering a market that four well-capitalised companies have independently evaluated and declined. That is either an edge or a verdict, and we do not yet know which. §7 prices this risk first.

### 1.2 The positioning

> **A multicam production kit that fits in your bag and needs no computer.**
> Capture on the phones you own. Sync without hardware. Cut on the tablet. Export. Nothing ever leaves the device.

Three claims, in order of defensibility:

1. **No computer.** Every shipped auto-multicam implementation on earth requires a desktop NLE, a cloud upload, or a hardware switcher plus per-speaker mics. Zero run on-device. This is the wedge.
2. **No upload.** CapCut's own privacy policy states it "may upload or import [User Content] to the Services **before you save or post**" — explicitly "to... generate captions" — and collects facial/body feature locations. The market leader pre-uploads unreleased footage plus faceprints to ByteDance to make captions. **"Never leaves the device" is a verified differentiator, not marketing copy.** On-device AI also has no marginal cost, so it can be *unmetered* while every cloud competitor (CapCut, CyberLink, Adobe, Riverside) has converged on credits.
3. **No hardware — stated precisely, because the imprecise version is self-flattery.** Blackmagic's *accurate* multicam sync on Android wants an external Bluetooth Tentacle Sync generator; the libsoftwaresync port gives ~250µs over plain Wi-Fi at zero hardware cost, so against **Blackmagic-on-Android we remove a hardware dependency outright — we beat them.** Against **Apple we beat nothing**: Apple Live Multicam already syncs peer-to-peer in software, and Apple's own push of hardware genlock (§3.2) is the tell that their software sync, like ours, is auto-sync-grade, not frame-accurate. So the honest sync claim is exactly this: *hardware-free, Blackmagic-beating, Apple-matching.* Anything that reads as "we out-sync everyone" is overclaim, and a technical reviewer will discount it (§1.5).
4. **Several things iOS structurally cannot do — led by the one that needs no special hardware to prove.** Apple Live Multicam requires **both apps to stay foreground**, so a controller drop kills the take; our Android foreground-service cameras **keep recording through controller loss** (gate G4). That is a structural iOS gap, demoable on *any* two owned phones. Alongside it: **open-filesystem local ownership** (no library jail, gate G7), and the hardware-conditional **heterogeneous mixed-vendor rig** (a Samsung + a Pixel + a Xiaomi in one session — iOS-impossible, demoable on any two different-brand phones, Q9). These are *real capabilities*, not economics — the honest, and now broader, answer to "what can Android do that iOS cannot," consolidated in §1.5.

### 1.3 What we are explicitly NOT positioning as

**Not a pro camera for filmmakers.** This is the hardest cut in the document and it is made deliberately in §2. The original brief carried two buyers at once — the filmmaker (who wants HLG10, false colour, waveform, LOG) and the producer (who wants the machine to do the editing). They are different people who want different products. Trying to serve both in a 4–8 week sprint serves neither.

**Not an AI company.** Market-vacancy is the moat, not the AI (§1.1, §1.5). But on Android the AI is **not** "table stakes" — on-device AI multicam and transcript editing are genuinely *absent* on the platform, so our AI Director and whisper.cpp transcript cutting are a legitimate, differentiated part of the value prop, not a relabeled commodity. We simply do not mistake them for the moat.

### 1.4 The hero moment — pressure-tested on the buyer, not on the builder

The demo needs a lean-in beat that (a) survives the worst branch — SPIKE-AUDIO < 3 dB, AI Director cut (§5.4) — and (b) actually lands on **the person we are selling to**. Those are two different tests, and an earlier draft passed the first and failed the second. This section fixes the second failure explicitly, because it is the sharper one.

**The critique that forced this rewrite (and it is correct).** The obvious hero — *"you hit stop and the synced, scrubbable multi-angle timeline is already there, no import, no spinner"* — is emotionally legible **only to someone who has personally suffered manual multicam sync.** That person is an existing multicam editor with a desktop, and §9.1 rules them out as the buyer. Our named beachhead is the **non-consumption producer** (JTBD-2) who has *never cut multicam* and therefore *never felt the sync chore.* To them, a synced timeline appearing is a **fact, not a relief** — you cannot miss a two-hour job you never did. A hero moment engineered purely for spike-independence, sold as "the chore is already done," lands hardest on the one person who will not pay. That is a real defect, not a nuance.

**So the hero is re-pinned around two guaranteed, spike-independent beats, and each is stated in terms of what the *non-consumption buyer* sees and feels** — someone who shoots one angle today because two is a hassle, not someone mourning a lost afternoon of syncing:

**Beat A — the positive-capability beat, and the true lead: the virtual second camera.**

> You set **one** phone on a stand, locked off wide, and record yourself talking. You stop. On the tablet, a **second angle you never shot** is already there — a re-editable, keyframed punch-in that follows your face, cutting between the wide and the close-up like you had a camera operator. One phone in, two angles out.

This is the beat that needs **no prior multicam experience to feel like magic** — you filmed with one camera and walked away with two shots. It is a *visible new capability appearing*, not the *absence of a chore*. It is **fully spike-independent**: the reframe tracks a single angle's face bounding box (§5.6, feature #12) and needs no inter-angle audio whatsoever, so it lands in **every branch**, including SPIKE-AUDIO < 3 dB. It is currently specified as a paid-tier floor (§5.6); this section promotes it to a **staged demo beat**, and **02-design-spec.md §7 must choreograph it as such** — it is the demo's guaranteed *positive* wow, de-risking the whole demo against the AI-Director cut branch.

**Because this is the load-bearing wow in *every* branch, it cannot rest on "a face detector" alone.** A punch-in driven directly by the raw 2–5 fps bounding-box stream would *jitter and lag*, and jitter is exactly what reads as the "gimmick" design D7 fears — a janky virtual camera collapses the emotional payload no matter how clever the concept. So the tracker's **temporal pipeline** is a first-class spec item, not an afterthought: box smoothing/filtering (one-euro or Kalman over the sampled boxes), crop-path interpolation between keyframes, and a maximum-velocity/damping bound so the virtual-camera move is smooth and broadcast-plausible. That pipeline is specified in **02-design-spec §6.6** and the **03-technical-spec §11** framing row, and it must clear an explicit *smoothness quality bar* in the week-1 face-detector spike (and the short-range-vs-wide-shot geometry check, 03 §11) before this beat is trusted. The concept is spike-independent; the *execution quality* is gated on that bar.

*(Honesty note: the face-tracking signal under this beat is deterministic CV over an on-device face detector — it is not "AI," and §5.6/§7.1 no longer call it that. It is a real, visible capability regardless of the label.)*

**Beat B — the finished thing exists, no computer touched it.**

> You shot a two-phone conversation. You stop. The finished, cut, multi-angle video plays **full-screen on the tablet** — and the caption is true: *"Shot, cut, and exported on the phones in that bag. No computer. Nothing uploaded."*

For the non-consumption buyer the wow is **not** "the timeline appeared" — it is **"the finished video already exists, on this tablet, and no laptop was ever involved."** The synced-timeline reveal still happens (it is guaranteed by §5.1 #1, #8: record-local + audio-xcorr auto-sync inside the G2 ≤1-frame gate — 03 §4.5; the ~250µs exposure-phase lock is optional upside, never the guarantee — + the 4× ExoPlayer shared-clock timeline), but it is framed as the *machinery under the finished thing*, not sold to a novice as relief from a chore they never had. In the SPIKE-AUDIO < 3 dB branch the finished video is a **manual** multicam cut assembled in one fast pass on the tablet (§8), so Beat B survives the cut branch too — the "no computer" payload is spike-independent; only the *self-painting* of the cut is contingent. And with #15/#16 now in V1, the close widens to a **two-artifact beat**: beside the finished wide episode, the same footage exits as a face-tracked **vertical 9:16 Short** with an **`.srt` caption sidecar** — *episode + Short + captions, no computer* (choreographed in 02-design §7–§8). And when the captioned Short plays on camera, the captions are the **sidecar rendered live by the in-app player** (ExoPlayer `MediaItem.SubtitleConfiguration` — a stable path already in the dependency set, 03-tech §8.1): nothing is burned in, and the demo *says so* ("captions are a sidecar file you can edit"), because the close must never imply the burned-in-caption capability the specs refuse to promise.

**Beat C — the novel positive co-hero: the video tightens itself, on the phone (whisper transcript cutting).**

> You recorded yourself talking — with the *ums*, the false starts, the dead air between thoughts. On the tablet, a transcript of what you said appears, and with one tap the filler and the dead air are gone: the video visibly **tightens** as the cut points snap to sentence ends. No computer, no upload, no cloud transcription bill.

**This is the beat the creator_wow objection demanded, and it is the one that is novel AND positive AND not a commodity.** The objection is correct that the demo cannot rest on Beat A (a face-tracked punch-in, which we ourselves call *near-commodity* — §1.6) plus Beat B (whose "no computer" payload is largely an *absence*). Transcript-driven dead-air/filler removal is neither commodity nor absence: it is **absent from Resolve SmartSwitch and from every Android tool** (§1.1), and it reads as a **new capability** to the exact non-consumption buyer — the podcaster, the course-maker, the sermon-recorder who has *never cut multicam* but who **intimately knows the pain of filler words and rambling dead air**, because they live it every time they hit record. Unlike the synced-timeline reveal (which lands only on a veteran editor who has suffered manual sync, §1.4 opening), Beat C lands on the *content producer's own felt problem*. It is **spike-independent** — whisper transcribes one audio track post-record and needs no inter-angle geometry (§5.6) — and it runs entirely on-device.

**Protected status, stated so it is not discovered at week 6:** Beat C rides on whisper.cpp, which is **guaranteed V1 scope and non-cuttable** (§5.1 #14, §5.7) — the paid tier's one learned-AI beat and the demo's protected novel wow. A schedule slip does not touch it: the pre-committed relief valves are the opt-in composited preview, then the director's timing-only polish, then 4→2 angles (§5.7), and past those the **date** moves — never whisper, never the spine. So the demo **leads with Beat C as the novel co-hero**, while **Beats A and B remain the spike-independent floor beside it** — they are what guarantee the demo against the SPIKE-AUDIO cut branch (a quality axis, not a schedule axis). (A second, protected novel positive beat is available as backstop: the **G4 pulled-camera reconciliation** — the phone yanked off the network mid-take whose footage nonetheless **lands correctly on the synced timeline** — §5.6/design §5.6/§6.7; it is spike- and whisper-independent, though it reads most strongly to someone who grasps what sync is.) Beat C is what pulls the wow from "commodity + absence" toward "a genuinely new, buyer-legible capability" — and whisper is guaranteed V1 (non-cuttable), so it ships.

**What is explicitly NOT the hero:** the AI Director's rough cut painting itself. It is genuinely striking when it runs, and 02-design-spec §7 stages it as an *accelerant* beat — but it is gated on SPIKE-AUDIO and pre-committed to be cut in the < 3 dB branch (§5.4), so it cannot be load-bearing. Beats A and B carry the demo with certainty in every branch; Beat C (whisper, guaranteed V1) is the novel positive co-hero on top.

The reveal *motion* for these beats — the punch-in generating, the transcript filler deleting and the video tightening, the tracks sliding in, the full-screen playback — is specified in **02-design-spec.md §7/§6.8**. This document fixes *which* beats are the hero, *why* each is spike-independent, and *whom* each is engineered to move. Gate them in §8.

### 1.5 The differentiation is market-vacancy first, capability second — said honestly

It would be dishonest to sell this as a capability breakthrough, and dishonesty here is exactly what a technical reviewer catches and discounts. The dominant reason this space is open is **economic** (§1.1): Android monetises pro video at ~half of iOS, so four capitalised companies rationally declined it. That is the moat's real substance, and it is a *market-vacancy* claim — not a *we-do-what-iOS-cannot* claim. **We lead with vacancy because it is the true reason we have room.**

**One correction to the AI's rank, folding in §1.1's fix.** On-device AI multicam and transcript cutting are *absent* on Android (§1.1), so our AI Director and whisper.cpp transcript cutting (§5.5) are a **genuine, differentiated feature on the platform we serve** — not the relabeled commodity an earlier draft called "table stakes." They rank **below** market-vacancy and *alongside* the Android-capability column: real value that helps prove the vacancy is fillable, never itself the load-bearing purchase driver or the durable moat. A determined user with an M-series iPad still has Resolve Studio; we do not out-muscle it. Vacancy stays the sole load-bearing moat.

**But the "Android can, iOS structurally cannot" column is broader than one hardware-conditional capability, and an earlier draft under-sold it by listing only mixed-vendor rigs.** The honest version splits into two tiers by *whether it demos on the owned kit with no special hardware* — which is the axis that matters, because a differentiation claim that depends on hardware whose configuration is unknown (Q9, D2) is a claim with no on-camera proof.

**Tier 1 — iOS-structurally-impossible AND demoable on ANY two owned phones (no special hardware, so these anchor the demo):**

1. **Background / foreground-service recording *through controller loss*.** Apple Live Multicam requires **both apps to stay foreground** — the moment either app backgrounds, the link and the transfer die (product ground truth). Android's foreground-service model lets every camera keep recording locally when the controller vanishes. This is not a tuning advantage; it is a **structural iOS limitation** we cross with a standard `FOREGROUND_SERVICE_CAMERA` service. **It is already in V1 as gate G4** (§5.1 #5, §8) — the concrete, demoable instance — and it is the single most robust Android-only capability we have, because it needs *no* particular hardware to prove: pull the tablet off the network mid-take and the phones keep rolling.
2. **Open-filesystem local ownership — "never leaves the device, no library jail."** iOS sandboxes media inside app/library containers with mediated export; Android's open filesystem lets the master files sit where the user can reach them, no upload, no account, no proprietary library to escape. This is the **G7 zero-egress** guarantee made into a *user-facing ownership story* iOS's structure does not permit. Demoable on any device (airplane-mode + Wireshark, G7).

**Tier 2 — iOS-structurally-impossible but hardware-conditional (built in V1, demoed only if the week-1 hardware audit confirms the kit supports them — see below):**

3. **Heterogeneous mixed-vendor rigs** (§1.2 claim 4): a Samsung + a Pixel + a Xiaomi in one session. iOS-impossible by construction (every angle must be an Apple device). Demoable **only if the two owned phones are different brands** (Q9).
4. **Foldable tabletop-mode capture** (viewfinder above the fold, controls below; `FoldingFeature`, GA today). Demoable **only if a foldable is in the kit** (D2).
5. **Optional USB-C wired angles** — a tethered angle over USB-C for a zero-radio-latency, thermal-friendlier link on a locked-off camera. Android's open USB/accessory stack permits app-level wired data paths that iOS's accessory restrictions make impractical for this. Built as a transport option (03-technical-spec §5), demoed only if a suitable cable/host path is confirmed.
6. **DeX / connected-display controller** — GA on Pixel 8/9/10 and Samsung S26/Fold7/Flip7/Tab S11. V2 controller surface; substrate built in V1 (§5.1 #3). Not demoed in V1.

**The hardware-audit commitment (resolves the "differentiation could have zero on-camera proof" risk).** Q9 (are the two owned phones different brands?) and D2 (does any owned device fold?) are **resolved in week 1, not at demo time** (§5.7 build sequence). If neither mixed-vendor nor tabletop-fold is demoable on the drawer's hardware, the fix is **cheap and pre-committed: acquire one differing-brand phone and/or one foldable for the demo shoot.** If we choose not to spend that, then Tier 2 is dropped from the *demo narrative* entirely and the demo leans on **Tier 1 (G4 background recording + G7 local ownership) plus the market-vacancy framing** — both of which are demoable on the owned kit with certainty. **What we do not do is let a demoed differentiation claim ride on hardware whose configuration is currently unknown.**

**The honest ceiling on the entire Android-differentiation column, stated so a reviewer does not have to extract it: it is a *supporting talking point*, not a load-bearing *purchase driver*, and the sole load-bearing differentiator is market-vacancy (§1.1).** Be precise about G4, the strongest item. The *only* evidence in the whole corpus that "keeps recording through a drop" matters to anyone is a **complaint from Blackmagic's professional users** — the pro segment §9.1 explicitly rules out as our buyer. We have **no** evidence that fault-tolerance is a *purchase* driver for the non-consumption beachhead, who has never run a multi-device shoot long enough to be burned by a controller drop. So we do not claim it is one. What G4 (and the rest of the column) actually *is*: a set of capabilities that are **iOS-structurally-impossible and cheap to demonstrate on camera**, which makes them excellent **proof-of-life that Android can host this product at all** — they dramatize *why the vacancy is fillable here and not on iOS* — without themselves being why anyone opens a wallet. The pitch therefore commits to **market-vacancy as the single load-bearing differentiator**; the Android-only capabilities rank strictly below it as evidence the vacancy is real and defensible. A demo that implied the Android column was itself broad enough to *sell on* would be overclaiming — exactly the self-flattery a reviewer discounts (§1.1). If a future beta ever surfaces beachhead evidence that fault-tolerance drives purchase (Q-instrumentation, §9.1), G4 can be promoted then, on data, not now, on a pro-user complaint.

Reframing the pitch honestly — **vacancy as the one load-bearing differentiator**, then a Tier-1 iOS-impossible pair (demoable on any owned phone) as *supporting proof the vacancy is fillable*, then hardware-conditional upside resolved in week 1 — is stronger and more honest than either a single thin capability claim or an inflated list.

### 1.6 A week-0 wow sanity-check (design input, not a sprint gate)

**We are not gating the sprint on a willingness-to-pay signal, and this section no longer pretends to.** An earlier draft made week-0 a GO/NO-GO *gate* on the sprint's framing. That was theatre: the builder is building this regardless — for the learning, the portfolio, and the product bet — so a null purchase-intent signal would not stop the sprint. Gating on a signal that cannot change the decision is ceremony, not evidence. **No purchase-intent smoke-test is happening.** (The real willingness-to-pay question is Q6, and it is honestly un-closable before a demo/beta funnel exists — §7.3, §9.1.) What we *keep* is a lightweight, half-day *wow sanity-check* that informs the **design** — whether to lead on Beat A or Beat B, and which rig ships as default — not the go/no-go. It is a *showing*, not a build, and a lukewarm result reorders the demo, it does not stop it.

**Premise 1 — does Beat A read as capability or gimmick? (design D7, a design input.)** Beat A (the conjured second camera, §1.4) is by our own honest admission a *deliberately modest, near-commodity* face-tracked punch-in — the same auto-reframe CapCut and InShot ship (§5.2). We do **not** currently know whether it reads to the buyer as *capability* ("I got a second camera for free") or as *gimmick* ("that's just auto-zoom"). Since Beat A is the load-bearing positive wow in **every** demo branch (§1.4; design §9), the answer shapes whether the demo *leads* on it — an engineering/design choice, not a reason to build or not build.

**Premise 2 — the capture-friction contradiction, now confronted, not papered over.** The beachhead is *defined* by avoiding multicam because **post-production** is the friction (JTBD-2). Yet the prescribed rig — **one phone per speaker, ~1.5 m off-axis, on stands, driven from a tablet controller** (forced by the SPIKE-AUDIO geometry, §5.4) — front-loads **capture** friction onto the exact same friction-averse buyer. That is a real, previously-unconfronted tension in the beachhead logic: we deleted post friction and quietly re-added setup friction. Not fatal, but it must be *measured*, not assumed away.

> **Week-0 action (half a day, no code):** show a *pre-built mock* of Beat A (a punch-in on existing footage — no rig, no sprint) **and** a photo/description of the full capture rig to **3–5 real non-consumption producers** (podcasters, course-makers, house-of-worship media volunteers — the JTBD-2 beachhead). Ask two things: does the conjured-camera reveal read as something you couldn't do before, and does the rig-setup read as *worth it* or *too much work*?

**Pre-committed responses, so the decision is evidence, not emotion:**
- **If Beat A reads as gimmick:** do not enter the sprint leading on it. The pre-identified stronger guaranteed capability wow is **Beat B carrying alone** — the finished multi-angle cut existing in-hand with "no computer touched this" — reordered to lead, with the conjured camera demoted to a supporting flourish. Beat B is spike-independent *and* tracker-independent (§1.4), so this fallback removes all dependence on the punch-in landing.
- **If the full rig reads as too much work:** ship a **two-phones-flat default** as the out-of-box rig — two phones side-by-side or loosely angled, no stands required — which still yields *some* inter-angle audio delta (the SPIKE-AUDIO 3–6 dB "marginal" band, §5.4) and still two real angles. The prescribed one-phone-per-speaker geometry becomes the *documented upgrade* for users who want the confident audio director, not the price of entry. This trades director confidence for setup simplicity — the right trade for a friction-averse non-consumer.
- **If both land well:** proceed as specified, with evidence instead of hope.

This check is cheap and it sharpens the design; it is not a precondition the sprint cannot start without, and nothing about it is a purchase-intent test. It sits in the build sequence as a half-day **week-0 design input** (§5.7) and in the demo section as a **design note, not a gate** (§8). Design §11 D7 is likewise a design input that informs choreography, not a go/no-go on the shoot.

---

## 2 — Beachhead user + JTBD

### 2.1 The beachhead

**Two-to-four-person recorded conversations in a room you control.** Podcast, interview, video course, sermon, panel, corporate talking-head.

This is not a taste preference. It is forced by the failure profile of the technology. Resolve SmartSwitch — the most mature implementation in existence — "works well on clean, well-lit interview setups where each speaker has a dedicated camera" and degrades when (1) multiple people are visible in the same frame across multiple cameras, (2) lighting makes lip movement hard to detect, (3) speakers talk over each other frequently, and (4) an angle has no audio track. **Every auto-director on the market has the same boundary.** Our rig structurally dodges (4) — every phone records its own audio, so a per-angle audio track is guaranteed — and (3) is where every shipped product is weakest, which makes crosstalk our most defensible quality target later.

**One tension this rig creates, confronted in §1.6, not buried here:** the beachhead avoids multicam because *post* is friction, yet the SPIKE-AUDIO-driven rig (one phone per speaker, off-axis, on stands, tablet-controlled — §5.4) front-loads *capture* friction onto the same friction-averse buyer. §1.6 makes this a **week-0 design sanity-check** (not a sprint gate) with a pre-committed **two-phones-flat default** if the rig reads as too much work.

### 2.2 Segments explicitly cut from the target list

| Segment | Verdict | Why |
|---|---|---|
| **Weddings** | **Cut.** Worst possible fit. | One-shot with no retakes — "the aisle walk, the vows, and the first dance happen once." Low light where phone sensors collapse. Long runtimes that hit the thermal wall (§7.2). Unpredictable framing that breaks lip-based detection. The cost of our failure is someone's wedding. |
| **Live events / concerts** | **Cut.** | Same one-shot risk, worse thermal, and no active speaker for the director to track. |
| **Musicians** | **Cut for V1.** | Music has no active speaker. The entire VAD-driven director degenerates to nothing. It needs a beat-driven policy that does not exist in our decomposition and is not a 4-week build. |
| **Filmmakers / narrative** | **Cut as a buyer; retained as an aspiration.** | See §2.4. They are the loudest audience and the wrong one. |

### 2.3 Jobs to be done

**JTBD-1 (primary, and the one to demo first):** *"I record a 40-minute conversation with two phones. Getting the angles lined up and cut together costs me two hours in an app I don't own on a laptop I have to open. I want to walk out of the room with the edit already assembled."*

**JTBD-2:** *"I know multicam looks better. I don't do it, because the post-production is the whole reason I don't do it."* — the non-consumption job. This is where the volume is: people who currently shoot **one** angle because two angles create a desktop chore.

**JTBD-3 (the free win nobody is claiming — see §5.1):** *"My phone footage is variable frame rate and it desyncs. Every workflow starts with a VFR→CFR conversion chore."* Variable frame rate media from phones is "the single most common cause of multicam playback bugs," phones "record in variable frame rate by default," and an Adobe Premiere PM acknowledged in late 2024 that VFR still causes sync drift and audio misalignment. **We capture CFR and exposure-phase-aligned by construction. We delete a universally-hated step for free.**

### 2.4 The contradiction the research forced, and how it resolves

**The contradiction:** The brief wanted filmmakers ("pro camera," HLG10, "monitoring is OPEN TERRITORY" — Apple ships no false colour, no waveform, no histogram). The research says the money for auto-direction comes from somewhere else entirely.

The evidence for the split is unambiguous. The most successful auto-director on the market — **VVD 6.0**, six years in broadcast, sold to TV networks and parliaments at $69–$220 — pitches itself as doing *"the work of a full-time staff member."* **AutoPod** sells the same automation at **$29/mo** to professional podcast editors. Demand for auto-direction comes from buyers who want to **not hire a person**: podcasters, houses of worship, parliaments, education, corporate. **It does not come from filmmakers.** The Descript feature request drew 25 votes in two years. Disney/CMU proved the general case in SIGGRAPH 2014 and nobody commercialised it for a decade. Ecamm never built it.

Meanwhile LumaTouch — whose base *is* serious filmmakers — publicly commits to AI "that streamline[s] non-creative workflow processes... on-device... rather than replacing [storytellers]," and is rewarded for it.

**The resolution: we pick the producer and we drop the filmmaker.** This is not a marketing choice; it forces feature cuts, and those cuts are made in §5.2:

- **False colour, waveform, histogram are CUT from V1** — despite being genuinely open territory. Open territory your buyer has not asked for is not a wedge, it is a distraction. A podcast producer in a controlled room does not need a waveform monitor. They need it to not overheat.
- **Focus peaking, zebras and audio meters stay** — cheap, and they are the ones that prevent a ruined take.
- **HLG10 stays for masters**, but for a reason that has nothing to do with filmmakers: it is the only 10-bit profile with an AOSP compliance mandate (API 33+), so it is the only one we can rely on across devices. It is a *reliability* choice.

**The residual risk, stated plainly:** the free tier attracts the segment that refuses to pay (mobile shooters — see §6.1), and the segment that provably pays $29/mo already owns lavs, Premiere and a desktop, i.e. the people least in need of an on-device phone app. **This is the largest risk in the plan and it is larger than any technical risk in it.** It is ranked #1 in §7 with the argument for why it is still worth the sprint.

---

## 3 — Competitive landscape

### 3.1 Blackmagic is the floor. Confronting it directly.

**What Blackmagic Camera 2.0 for Android (Jan 2025) already ships, free:**

- 1 controller + **up to 9 cameras** (Apple caps at 4)
- Multi-view monitoring
- Per-device remote control of zoom, focus, WB, frame rate, shutter angle
- Tablets as camera or controller
- Infrastructure Wi-Fi or wired transport
- An explicit hand-built **device allowlist** (S21–S25 Ultra, Pixel 6–9 excluding Fold, OnePlus 11/12, Xiaomi 13/14, Xperia 1/5/Pro-I, Xiaomi Pad 6, Tab S9)

**And Resolve 20 adds SmartSwitch (§1.1) — but it is almost certainly Studio-only ($295), and Resolve does not run on an Android phone at all. So the full Blackmagic pipeline does capture + sync + AI direction only on a *desktop or iPad*, and the AI half is *paid*. On the Android phone in the bag, it hands off to nothing.**

**What we cannot beat, and must not pretend to:**

| Their advantage | Why we lose | Our response |
|---|---|---|
| Per-device image tuning | They have a colour-science team; they implement their own transfer curves | Ship narrower. MPC-gated device support (§3.3), not a hand-tuned allowlist. |
| 9 angles | Wi-Fi and decoder budget scale badly for a solo dev | Ship 3–4. Our beachhead has 2–4 speakers. Nine angles is a number for a market we are not serving. |
| LOG / colour pipeline | **There is no LOG API on Android at all.** No LOG profile in Camera2 or CameraX. Samsung Log is camera-app-only — .cube LUTs for Resolve and *no* developer API, SDK, or partner path. | **LOG is cut. Not deferred — cut.** It is not buildable, by anyone, on Android, today. |
| Allowlist breadth | Hand-tuned per device over years | MPC ≥ 34 (§3.3) |

**What we beat them at, and why they will not respond:**

1. **Sync without hardware.** Their accurate sync wants a Bluetooth Tentacle Sync generator. Ours is ~250µs over plain Wi-Fi via NTP + sensor exposure-phase alignment, ported from google-research/libsoftwaresync (Apache-2.0). Zero hardware.
2. **Blackmagic Camera ships zero AI.** Their AI lives in Resolve, on the desktop.
3. **They will not build an Android editor.** Resolve has no Android build and their own forum attributes it to GPU access: Resolve "runs its whole visual pipeline there rather than using the CPU" and "there's not a uniform way to fully access the GPU on Android." More fundamentally: **Blackmagic is a hardware company using the app as a Resolve funnel. On-device editing cannibalises that funnel.** This is a *strategy* bet, not a tech moat — and strategy bets can be reversed at any time. Say so in §7.

**The uncomfortable summary:** our entire differentiation against Blackmagic is **the absence of the desktop step**. That is worth exactly as much as our beachhead's unwillingness to open a laptop — which is why the beachhead choice (§2) and the wedge (§4) are the same decision.

### 3.2 The rest of the field

- **Apple / Final Cut Camera + FCP iPad:** 4 devices max; 720p proxy monitoring (hard ceiling); both apps must stay foreground; **no live switching while recording** — it is synchronised capture + monitoring, *not* a live switcher. FCP iPad ships a long AI list (Scene Removal Mask, Auto Crop, Voice Isolation, Transcript Search, Montage Maker, Edit Detection) and **none of it is an AI Director for multicam angle selection**. Apple also just made itself expensive: $12.99/mo for new customers (Jan 2026), $4.99 grandfathered. Apple's push of hardware genlock (17 Pro + ProDock) implies their own software sync is auto-sync-grade, not frame-accurate — the same honest ceiling we have.
- **AutoPod ($29/mo):** hard proof of willingness-to-pay for automated angle selection. **But** its automation is loudness-based and *requires an isolated lav per speaker* — "a single stereo mix or one shared room microphone does not provide the clean speaker-level activity AutoPod needs." See §5.4; this is the sharpest technical question in the plan.
- **Descript:** relaunched Automatic Multicam (Mar 2026) and **locked it to its own Rooms recordings** because "high-quality multitrack footage is essential." A funded competitor tried it on arbitrary footage and retreated to controlling capture. **That independently validates our vertical-integration architecture.**
- **Riverside:** switches "a second before they speak" — lookahead. Only possible offline. Another argument against live switching (§5.2).
- **Filmic Pro:** acquired by Bending Spoons **Sept 2022** (not 2025 — brief was wrong; the *conclusion* "fading, not resurgent" is strengthened). Team laid off Nov 2023.
- **Open-source layer: empty.** Every auto-multicam repo has 0–2 stars. The most-starred (autoPodcastEditor, 10 stars) has **no licence**, is dead since 2019, and is pure audio-energy with no ML. Claude Code will have nothing to crib. Near-zero traction everywhere is also a demand warning.

### 3.3 The device-support answer (better than Blackmagic's, and free)

Blackmagic hand-built an allowlist. We cannot out-tune that. **We do not have to:** gate on **Media Performance Class ≥ 34** (`androidx.core:core-performance`). It is a Google-maintained, CTS-*enforced* allowlist covering 190M+ devices / 500+ models / 40+ brands, and it gates on precisely the capability we need. Google's caveat applies: still probe runtime capabilities, because MPC is a static tier, not a live budget.

### 3.4 Research-hygiene warning (applies to every future claim in this repo)

The claim *"KineMaster 2026 introduced AI Multicam Editing (4 angles, audio+motion sync)"* is **AI-generated SEO fabrication**, contradicted by KineMaster's own site. **It would have killed the wedge if believed.** This search space is saturated with hallucinated features on real products. Every competitive claim must be confirmed against a vendor-primary source before it enters a spec.

---

## 4 — The wedge argument

Stated as a chain, each link independently verified:

1. **Multicam capture on Android is solved and free.** Blackmagic owns the floor. We cannot win here; we must merely not lose. *(Reach parity on 3–4 angles, beat them on hardware-free sync.)*
2. **Auto-direction is solved and shipped — but only off-device (desktop/iPad), behind Resolve Studio ($295), and it does not exist on an Android phone at all.** Resolve 20 SmartSwitch. So on Android this is *not* a commodity we relabel — it is **absent**, and we bring it to the platform. *(Ship it because on Android nothing does it, not merely because its absence is conspicuous.)*
3. **Every solution to (2) requires a desktop, an iPad, a cloud upload, or a hardware switcher.** Zero run on an Android phone. **This is the only unclaimed ground.**
4. **The Android editing surface is empty by economic choice, and stays empty.** LumaFusion built multicam and withheld it. Resolve never came. Rush is dead. Descript never shipped mobile. Four companies, four independent decisions, one direction.
5. **Therefore:** the product is not "AI Director." The product is **"the desktop is not in the loop."** The AI is a feature of that; it is not the thesis.
6. **The buyer for (5) is whoever finds the desktop step to be the actual cost.** That is the producer, not the filmmaker (§2.4).

**The counter-argument we must hold in view:** link 1 means multicam *capture* is a free commodity on Android, and link 4 means the reason we have room is that nobody wants the room. Auto-direction (link 2) is *not* a commodity on Android — it is absent — but neither is it a durable moat, because a determined user with an iPad can buy Resolve Studio. The wedge is real and narrow. **It is an experiment worth 4–8 weeks, not a business plan worth a year.** The question "will anyone pay for a multicam editor on Android?" has never been answered *because nobody tried*, and it is cheap to answer. That is the entire justification for the sprint, and it is sufficient for a sprint. It is not sufficient for anything longer.

---

## 5 — V1 feature set

**V1 target: a working multicam demo in 4–8 weeks on 2 phones + 1 tablet. Not a Play Store release.**

### 5.1 IN — ranked by demo load-bearing order

| # | Feature | Why it is in | Status |
|---|---|---|---|
| **1** | **Multi-device record-local, CFR, audio-synced.** Every device records full quality locally; footage never crosses the network live. **V1 default sync = audio cross-correlation** (post-record; honest accuracy per 03-tech §4.5: **<1 ms on co-located FLAT rigs, ≤ ~10 ms worst-case on the SEPARATED rig** — GCC-PHAT aligns acoustic *arrival* times, and mic-to-speaker path asymmetry adds ~2.9 ms per metre of difference; both figures sit inside the G2 ≤1-frame / 33 ms gate) — the guaranteed baseline that clears G2 on the assembled timeline and is honestly Apple-parity. The live exposure-phase-lock port (libsoftwaresync/RecSync, Apache-2.0, both archived Pixel-2/3/4-era) is an **optional upside**, week-1 go/no-go, weeks-7/8 slack only (03-tech §4). | The foundation. It delivers JTBD-3 for free and it is the substrate of the **§1.4 hero moment** — spike-independent, and independent of which sync branch wins. **Open the demo on this**, not on the AI. | Default is authored DSP (audio-xcorr); the port is upside, not core. |
| **2** | **`SENSOR_INFO_TIMESTAMP_SOURCE == REALTIME` — gates the phase-lock UPSIDE only.** | Camera2 `SENSOR_TIMESTAMP` is cross-device comparable *only* here — which matters only for *live* phase-lock. The **default audio-xcorr path needs no cross-device timestamp comparability**, so a device without REALTIME is a full CAMERA (auto-synced), not refused. | Upside gate, not a camera-eligibility gate. `HARDWARE_LEVEL_FULL` separately gates *manual controls only* (03-tech §7.1). |
| **3** | **Tablet controller:** NSD/mDNS discovery **plus a QR-pairing fast path** (two explicit modes, 03-tech §5.4: **default** — both devices already on the session Wi-Fi — the QR carries `{controllerIp, port, sessionToken}` and the phone TCP-connects straight to the controller, no Wi-Fi-join API involved; **join mode** — phone not yet on the AP — the QR carries `{ssid, psk, sessionToken}` and joins via `WifiNetworkSpecifier` + `ConnectivityManager.requestNetwork`, with one system approval dialog per phone, choreographed in 02-design §5.7. NSD remains the no-QR fallback), roll/stop all, per-device status (storage, battery, **thermal**), single APK, responsive phone/tablet/foldable. | The rig has to be operable by one person. Tabletop mode (viewfinder above fold, controls below) is a natural camera fit. | **Do not cache display metrics or assume a constant `Display`** — it changes when windows move between displays; preview Surface, aspect ratio and encoder config all key off it. Portrait-locked apps will not work. |
| **4** | **Live monitor grid** — WebRTC low-bitrate proxy, **monitoring only**. | WebRTC aggressively degrades quality to preserve latency: correct for monitoring, catastrophic if it touches master files. | `io.github.webrtc-sdk:android` (Google ships no Android artifacts). Record locally, always. |
| **5** | **Fault tolerance: recording survives controller loss.** | Blackmagic's cameras "error and cut" when the master loses contact. This is a *named user complaint about the market leader* and it is nearly free for us to beat. | Cameras roll until told to stop, not while told to continue. |
| **6** | **Thermal governor** — predictive warning + graceful degrade, not a hard stop. | See §7.2. This is a demo-killer, not a polish item. | Week-1 spike. |
| **7** | **Post-record proxy generation: 540p/720p, 8-bit SDR, H.264.** | **Forced, not chosen** — see §5.3. | Masters stay HLG10. |
| **8** | **Multicam timeline: 4× ExoPlayer on 4 SurfaceViews, shared clock, angle switching → single-sequence `Composition`.** | The re-architecture that saves the thesis — see §5.3. | Mature, ordinary path — but the "shared clock" is **authored frame-lock code, not a library property** (ExoPlayer has no cross-instance clock-slaving API): scrub = broadcast `seekTo` to all four players; playback = one master player (the audio angle) with three muted followers periodically drift-checked and corrected. Design + cadence/threshold in 03-tech §8.1; verified in the §16.1 spike batch. |
| **9** | **Program preview + export:** single-sequence `Composition` via `CompositionPlayer` + `Transformer`, MediaCodec only. | Seeking is *explicitly confirmed working* for one video sequence. The unified preview/export model survives. | Media3 **1.10.1**, pinned. No FFmpeg. |
| **10** | **AI Director v0:** per-angle VAD → FSM rough cut → editable `Composition`. Conservative/aggressive pacing dial. Video-only cuts. Per-boundary override. **Never flattened.** | See §5.4 and §5.5. | **Gated on the audio-geometry spike.** |
| **11** | Focus peaking, zebras, overexposure indicators, audio meters. | Cheap; prevents ruined takes. | — |
| **12** | **Single-angle smart reframe + auto take-quality review** — the deterministic framing model (face-bbox size, thirds offset, luma clip, Laplacian focus) exposed as a single-camera keyframed punch-in and a per-take quality score. **This is deterministic CV, not "AI"** (§5.6). Its keyframed `CropTrack` is also the substrate of #15's vertical 9:16 export. | **The spike-independent paid floor and demo Beat A** (§1.4, §5.6). Needs no inter-angle audio. Adds visible value even if the Director is cut. **Dependency: an on-device face detector — real component, not size-0 DSP; named in 03-technical-spec §11.** | CV heuristics scoped for the director (§5.2) + a face detector; no new *spike*. |
| **13** | **Heterogeneous mixed-vendor rig** — any mix of MPC ≥ 34 / REALTIME-gated Android phones (Samsung + Pixel + Xiaomi) in one session. | **A Tier-2 iOS-impossible capability** (§1.5) — Apple Live Multicam is Apple-only. Demoable only if owned phones differ (Q9); the demo's *robust* iOS-impossible beats are Tier-1 (G4 FGS-through-loss, G7 local ownership). | Free — a property of gating on capability, not on vendor. Resolve Q9 in week 1 (§5.7). |
| **14** | **whisper.cpp transcript-driven cutting (paid, V1 — pulled up from V2).** Post-record on-device transcription (whisper.cpp tiny/base, native NDK build) → a word/sentence-timestamped transcript the editor cuts by *deleting words* (Descript-style), propagating to the multicam program; sentence/turn boundaries also seed the director's cut points. **The paid tier's ONE genuinely-learned on-device model.** | **The real learned-AI beat V1 needs** — not an energy-argmax angle picker. Proven shippable (VN; Descript on desktop), MIT-licensed with a clean patent surface, on-device, and it lands exactly where Resolve cannot follow: the Android phone. Also enables **dead-air trimming** and **transcript-driven structure**, the concrete openings against SmartSwitch (§1.1, §5.5). Spike-independent (needs no inter-angle audio geometry). | **Guaranteed V1, non-cuttable (locked)** — a slip fires the §5.7 relief valves and then flexes the calendar, never this feature. Adds ~1–1.5 weeks of scope (§5.7). Post-record, not live. Model + runtime + UX + quality bar in §5.6; 03-tech §11/§13. |
| **15** | **Vertical 9:16 export (paid — rides #12).** The reframe tracker already emits a re-editable keyframed `CropTrack`; #15 renders that same crop path to a **1080×1920 export through the same Transformer path as the program export** — no new pipeline, one more export preset (03-tech export pipeline). One phone in → the wide episode **and** a face-tracked vertical Short out. **Per-angle crop policy for multicam programs (03-tech §8.3, mirrored 02 §7):** the tracked angle rides its `CropTrack`; every *untracked* angle in the program gets a **static face-centred crop** seeded from one BlazeFace detection at the segment's first sampled frame (centre-crop fallback), emitted as ordinary editable `CropTrack` keyframes so the ≤2-tap nudge grammar applies uniformly. The single-wide-angle Short is the default demo path; the multicam-program Short is the general case the policy covers — no program segment has undefined crop behaviour. | The beachhead's distribution (Shorts/Reels/TikTok) is vertical, and no earlier draft named a vertical deliverable — a genuine gap closed nearly for free because the parts exist. The demo close becomes *episode + vertical Short + captions, no computer* (§1.4 Beat B; 02-design §7–§8). | Paid, because it rides the paid reframe floor. Measured in the same heterogeneous-export spike; no new gate. |
| **16** | **SRT caption sidecar export (paid — rides #14).** whisper already produces word-level timestamps for every take; writing an **`.srt` sidecar** from them is pure-Kotlin string formatting. | Captions are table stakes for the social/vertical deliverable. A **sidecar carries no accuracy promise** — every platform lets users edit captions post-upload, and the transcript-cutting UX already lets them fix words before export — so the WER bar that keeps *burned-in* captions out of scope (§5.6) does not bite. Sidecar timestamps are emitted in **program time** through the same take-time→program-time map that transcript deletions define (03-tech §8.2/§11.4), with caption blocks split at deletion seams. | Paid. Zero new models, zero new native code. **Burned-in captions remain out of scope** — in-app playback of a captioned export renders the sidecar *live* via ExoPlayer `MediaItem.SubtitleConfiguration` (03-tech §8.1), never a burn-in. |

**Two spec-completeness ground rules adopted 2026-07-18 (details owned by 03-tech):** (1) **Storage** — masters and proxies live in app-scoped external storage (no permission, survives app update, cleared on uninstall — documented user-facing); user-facing exports go through `MediaStore` into the open `Movies/` filesystem, with SAF for arbitrary destinations including USB-C drives; USB-C SSD *recording* is V2 (§6). (2) **Audio** — V1 records each phone's default mic (`AudioSource.CAMCORDER`, 48 kHz mono AAC alongside the video master); USB/lav external input is the named V1.5 upgrade (§6), because one move improves whisper WER, the director's VAD, *and* sync xcorr.

### 5.2 OUT — every cut, with its justification

| Cut | Why | Revisit? |
|---|---|---|
| **LOG** | **No LOG API exists on Android.** Not in Camera2, not in CameraX. Samsung Log is camera-app-only with no developer path. Blackmagic implements their own transfer curves with a team. This is not a schedule cut — it is not buildable. | Never, absent an AOSP change. |
| **False colour / waveform / histogram** | Genuinely open territory (Apple ships none). **But our beachhead has not asked for it.** This is filmmaker bait and it follows directly from the §2.4 resolution. | V3, if a filmmaker tier is ever real. |
| **2×2 grid / PiP composited output** | **Issue #2439: seeking is not supported for multiple video sequences (PiP/Grid).** Maintainer, verbatim: *"Seeking is currently not supported for multiple video sequences like PiP and Grid view."* Open ~14 months; predecessor #1489 makes the defect ~25 months old; maintainer 2026-06-17: *"The development will not be complete by 1.11.0."* And **#2742: `DefaultVideoCompositor` deadlocks on media-item transitions, "All devices"** — so grid fails even without seeking, the moment any angle has more than one clip. | **V2, as an export-only effect** (no scrubbing requirement). |
| **Live switching while recording** | Four independent reasons: (a) record-local means there is no live program to switch; (b) **lookahead is a quality advantage** — Riverside switches "a second before they speak," only possible offline; (c) Apple's Live Multicam does not do it either; (d) **Verizon US12323726** claims facial-detection camera switching with debounce and blending. | Not planned. It is a worse product, not just a deferred one. |
| **4K** | Thermal (§7.2) + decoder budget. 1080p30 is the CDD-guaranteed lane. | V2, single-angle. |
| **>4 angles** | Blackmagic does 9. Our beachhead has 2–4 speakers. Nine is a number for a market we are not serving. We own 3 devices. | V2. |
| **Auto-reframe (full, learned, multi-target)** | Near-commodity. InShot ships it plus captions, background removal, beat sync, TTS, tracking, upscaling for ~$20/year, zero credits. CapCut paywalled it — which shows the value is in *metering*, not capability. On-device has no marginal cost, so we have nothing to meter. **Note the distinction:** the *single-angle heuristic* punch-in (§5.6 / #12) ships in V1 paid because its tracking signal is already computed by the director; the *full learned* auto-reframe stays V2. | V2, unmetered, unmarketed. |
| **Lip-movement ASD** | **MediaPipe has no active-speaker-detection task.** The brief's "active-speaker detection (MediaPipe face/mouth)" **is not implementable as written** — MediaPipe Tasks ships Face Landmarker only, i.e. a mouth-open heuristic that fires on chewing and laughing. There is no drop-in on-device ASD. The real option is porting **LR-ASD** (MIT, 94.45 mAP, 1.0M params, 0.6G FLOPs). That is a research port hidden behind one parenthetical — the highest-risk unestimated item in the original plan. | **V2, gated on the §5.4 spike result.** |
| **Learned shot-quality / framing model** | No on-device model exists. Spec deterministic heuristics instead (face bbox size, thirds offset, luma clipping, Laplacian focus). | Heuristics only, in V1's director. |
| **Eye-contact correction** | Both on-device precedents (Apple FaceTime, NVIDIA Maxine) run on dedicated silicon and neither is on Android. Riverside's is cloud at $39/mo. High effort, no Android precedent, off the critical path. | No. |
| **Weddings / events / music modes** | §2.2. | No. |
| **Play Store release** | Demo target. Developer verification (Sept 30 2026, 4 countries) has a free limited-distribution tier covering 20 devices with no government ID, and ADB/local dev is unaffected. **The 3-device demo is not blocked.** | Post-demo. |
| **Cloud anything** | It is the thesis. | Never. |
| **NPU delegates** | Per-vendor compile-and-test matrix (Qualcomm/MediaTek). Not solo work. NNAPI is deprecated (Android 15). | Ship LiteRT + **GPU delegate**. |
| **ffmpeg-kit** | Archived July 2 2026, partly over codec patent liability. Successor is source-only, Nix-built, 27 stars. **Its own wiki locates the exposure in building and distributing openh264/x264/x265 — i.e. bundling a codec.** Using OS-provided MediaCodec avoids both the maintenance and the bundling question. | Never. |

### 5.3 The CompositionPlayer re-architecture (the bet was wrongly specified, not wrong)

**The thesis rested on `CompositionPlayer`, and the load-bearing part of it is too raw to build on.** Status is one notch worse than assumed: on `main` the class annotation reads verbatim `@ExperimentalApi // TODO: b/470355043 - Publish CompositionPlayer.` — **it is not yet published at all.** Google's own composition-demo README says the APIs are *"work in progress, rather than experimental API"* and to *"await further announcement."* Only 22 preview-labelled issues have ever been filed (5 open) — nobody is using this in anger. We would be first, finding new bugs with no workarounds.

**Why the thesis survives anyway — the re-specification:**

> A multicam **program output** is a **single sequence** of clips from different sources. It never needs `MultipleInputVideoGraph`.
> A 4-up **monitor grid** needs no compositor either, because it renders to **four Views**, not one frame.

| Surface | Old (broken) plan | New plan | Evidence |
|---|---|---|---|
| 4-up scrub | Multi-sequence `CompositionPlayer` + 2×2 compositor | **4× plain ExoPlayer on 4 SurfaceViews, shared clock** | The known black-screen failure was specific to stacking *CompositionPlayers*, which each own a video graph. Plain ExoPlayer is the ordinary path — with the frame-lock **authored** (master + drift-corrected followers, 03 §8.1), since ExoPlayer offers no cross-instance clock-slaving. |
| Program preview + export | Same | **Single-sequence `Composition` → `CompositionPlayer` + `Transformer`** | Seeking explicitly confirmed working for one video sequence. |
| PiP / split-screen | V1 | **V2, export-only effect** | #2439, #2742. |

**The unified preview/export model — the actual load-bearing insight — is intact.** Preview and export consume the same `Composition` object. That is what makes on-device multicam newly buildable, and it does not require the compositor.

**Forced spec consequence — HLG10 and 4-up preview are mutually hostile.** CDD **[5.1/H-1-2] mandates and CTS enforces 6 concurrent 8-bit SDR 1080p30 decode sessions** at ≤1 dropped frame/sec. But **[5.1/H-1-19] guarantees only 3 concurrent 10-bit HDR sessions** (at 4K, budget *shared with the encoder*). **There is no guarantee of 4 concurrent HLG10 decodes at any resolution.** Therefore: **masters HLG10, proxies 8-bit SDR H.264.** The brief's soft "assume proxies" becomes a hard, technically-forced spec with a specific pixel format.

**Decoder budget is better news than feared, but still measured-pending.** The oft-repeated "only 2–3 concurrent 1080p" figure is Android 5.1/OMX-era (~2016) and **must not size this design**. `getMaxSupportedInstances()` merely echoes a number an OEM hand-typed into `/etc/media_codecs.xml` — a vendor *declaration*, never a measurement; Google formalised the gap into two separate requirements (H-1-1 advertise vs H-1-2 achieve). **Port Google's own harness:** AOSP CTS `MultiDecoderPerfTest.java` / `MultiCodecPerfTestBase.java` (`REQUIRED_MIN_CONCURRENT_INSTANCES = 6`) already measures exactly this.

**The fallback ladder is a real branch, not a footnote** (the spike is written but has not been run — no decoder numbers exist yet):

`4× 720p SDR proxies` → `4× 540p proxies` → `2×2 at 24fps` → `single-angle preview + still grid` → `custom MediaCodec+GL compositor`

**Do not write V1 as though the grid is known to work.**

### 5.4 The audio contradiction — and the week-1 experiment that settles it

**Two research passes contradict each other on whether the AI Director has a signal at all. This must be resolved before a line of director code is written.**

| Position | Claim |
|---|---|
| **A (prior-art pass)** | The rig gets per-speaker audio free — each phone mics its own subject — which is *structurally identical to VVD's premise*. VVD 6.0: six years in broadcast, sold to networks and parliaments, **audio-only fuzzy logic over per-mic VAD. No vision, no ASD, no transformer.** Build that first; it dodges the MediaPipe gap entirely and is the proven design. |
| **B (creator-needs pass)** | A 2–3 phone shoot has **N co-located phones each recording the same ambient room mix** — total mic bleed, zero isolated tracks. AutoPod explicitly requires an isolated track per speaker; "a single stereo mix or one shared room microphone does not provide the clean speaker-level activity AutoPod needs." **The market-leading approach is structurally inapplicable to phone multicam.** |

**Resolution: both are right, and which one is right on any given shoot is a function of rig geometry — which we control.**

If each phone sits ~1–1.5m from its own subject and off-axis from the others, the inverse-square advantage is real: enough for a *relative energy comparison between angles*, though never enough for clean isolated tracks. If the phones sit together on a shared table pointing outward, the mixes are near-identical and the signal is **zero**.

**Therefore V1 ships a prescribed rig** — *one phone per speaker, within ~1.5m, off-axis* — and the director degrades gracefully (to manual) when the geometry is not met.

**And this is cheap to measure in week 1 on hardware already owned:**

> **SPIKE-AUDIO.** Two phones, two speakers, 1.5m each, normal room. Record 3 minutes of natural alternating conversation. Compute per-angle short-window RMS. Measure the **inter-angle energy delta during single-speaker segments**.
> - **> 6 dB** → position A holds. Audio-only VAD + FSM works. Build the VVD design.
> - **3–6 dB** → marginal. Needs per-angle gain normalisation and a much more conservative FSM. Director ships but the pacing dial defaults conservative.
> - **< 3 dB** → **position B holds and the audio-only director is dead.** It needs lip movement, which has no drop-in on-device model (LR-ASD port = research risk). **In that branch the AI Director is cut from V1 entirely and the demo is capture + sync + manual multicam cut.** Say so now, in advance, so the decision is not made emotionally at week 6.

This is also the moat if it lands. Note what B actually implies: the market-leading approach **cannot work on phone multicam**, and the one named user request in the whole corpus (Kenan Azam, 2024-07-08) asks for video-based detection precisely because of *"audio mic bleed across three camera angles."* Resolve SmartSwitch already went video-based. If our geometry gives us audio separation without a lav rig, we have something AutoPod structurally cannot offer its own customers.

### 5.5 The AI Director spec (v0), if SPIKE-AUDIO passes

**Architecture: FSM over shot selection, with editing grammar as transition costs.** Twenty-five years of literature (Virtual Cinematographer SIGGRAPH 1996; Disney/CMU trellis SIGGRAPH 2014; GAZED CHI 2020; EditIQ IUI 2025) converges on **discrete optimisation or FSM — not an LLM, not RL.** This confirms the brief's "mostly NOT an LLM problem." FSM for V1; trellis is the V2 quality upgrade. EditIQ is the quality target: it *beat* speaker-detection-based editing (what everyone ships) via dialogue understanding + saliency — and whisper.cpp is now in the **V1 paid stack** (§5.1 #14, §5.6) to supply that dialogue signal, so V1's director is not energy-only in the branches where whisper runs.

**Free parameters, from prior art:**
- **LiveCUT** hands us the exact parameter set: Pre-Attack / Attack / Pre-Release / Release / Min-Time / Max-Time.
- **US7349005** supplies editing-grammar constants free: DMIN ≈ 5s, leading-role shot 6–8s, DMAX timeout, 7×7 shot-size transition matrix.
- SmartSwitch itself exposes **Minimum Edit Duration (~1.5s default)** — users are literally asking for these knobs.

**Non-negotiable design rules, each answering a specific documented user refusal:**

| Rule | The refusal it answers |
|---|---|
| **Emit a re-editable `Composition` decision list. Never flatten.** | AutoPod "outputs hard-coded cuts on separate tracks, not a true multicam clip, and once flattened, angle switching becomes limited or impossible." |
| **Video-only switching by default. One unbroken audio track.** | At least three separate Blackmagic forum threads beg SmartSwitch to cut video only. |
| **Expose min/max shot duration, speaker preference, reaction-shot hold. Conservative/aggressive dial.** | *"You run the tool and hope it makes smart decisions."* Black-box behaviour is the #1 complaint. |
| **Per-boundary manual override, ≤2 taps.** | The trust mechanism. This is the answer to Open Tension #2. |
| **Cut on SPEAKER TRANSITION, never on silence detection.** | **Patent.** See §7.6 — and it is *also the better edit*. |

**What editors actually refuse is not automation — it is autonomy and irreversibility.** The best articulation of the genuine creative objection, worth reading twice: *"speaker activity is a useful signal, but it does not tell the whole visual story... a good human edit may hold on a guest's reaction, stay wide during an interruption or avoid cutting to someone for a one-word acknowledgement."* **That is an argument for a better policy layer, not against automation.**

**And the strongest public skeptic is a witness for the prosecution.** Darren Durlach (Early Light Media) trashed Eddie AI — "interesting toy," not "trusted assistant," "weird franken-bite soundbites." But read his actual multicam complaint: *"it didn't cut from one angle to the other. It just stayed on one angle even though it claimed to be a multi cam editor."* **He is angry that the AI failed to switch.** Across the entire corpus, **no practitioner argues that automated angle selection is philosophically unwanted** — only that existing implementations are bad.

**Open Tension #2 ("AI Director is UNPROVEN AS A WANT") does NOT close cleanly, and an earlier draft was wrong to declare it closed.** The evidence splits by *whose* want, and the split is the whole point:

- **PROVEN — but for a buyer we ruled out.** Among **professional editing shops** — the people with billable hours, lavs, Premiere, and a desktop — automated angle selection is a demonstrated want: AutoPod $29/mo, VVD $69–220 across six years of broadcast. **But §9.1 explicitly rules those buyers out as our target.** Citing AutoPod/VVD as validation of *our* buyer's want is circular: it proves demand in exactly the segment we said we are not serving. We stop citing them as beachhead validation here.
- **UNPROVEN — for our actual beachhead.** The **non-consumption producer** (JTBD-2) has *never cut multicam*, has *never trusted a machine cut*, and won't open a laptop. Whether *this* person wants a machine to choose their angles is **unproven in either direction** — there is no AutoPod-equivalent evidence for a buyer who has never done the manual version at all. The corpus finding that *no practitioner objects to auto-switching in principle* (Durlach, above) narrows the philosophical risk, but it too is drawn from **practitioners = the pro segment**, not the beachhead. It does not transfer.

**And a successful technical demo will NOT close this** (§9.1 concedes the same). A demo proves the Director *works*; it does not prove the beachhead *wants* it. So the tension has a third, correct form: it is unproven as a **differentiator** (§1.1 — Blackmagic ships one), unproven as **buildable on our audio** (§5.4 — SPIKE-AUDIO), **and unproven as a want among the only buyer we are selling to.**

**What WOULD validate beachhead want (and this is the sprint's actual paid-AI success criterion, not the tech demo).** Instrument the demo/beta build's funnel from day one and watch for a behavioural signal, not a survey answer:

1. **Free-capture → paid-multicam-editor open rate** (Q7): of users who capture multicam free, what fraction ever open the paid editor at all?
2. **AI-cut engagement, conditional on the Director shipping:** of users in the editor, what fraction *tap "auto-cut"* rather than cutting manually — and of those, what fraction **keep the machine's cut** (accept it or lightly override it, ≤ the quality gate) versus **revert to a manual pass**. A user who taps auto-cut and keeps it wanted the AI. A user who never taps it, or taps it and rebuilds by hand, did not.

Until that instrumented signal exists, **the paid AI's want is a hypothesis** — which is exactly why the pricing model (§7.1) is deliberately *not* staked on the AI being wanted: it is staked on the **scarce good** (the multicam editor nobody else has on Android), with the AI as upside. That decoupling (§5.6, §7.1) is what makes the sprint survivable even if this tension resolves against us.

**The honest quality-gate expectation for energy-only selection — stated up front, so week 6 is not an emotional decision.** V1's director is **energy-argmax VAD with no active-speaker detection** (03-technical-spec §10.1) — which is precisely the *speaker-activity-only* approach our own cited evidence flags as the **known-weak version**: EditIQ *beat* speaker-detection editing via dialogue understanding (above), and Durlach's complaint was an auto-editor that "stayed on one angle." So we do **not** assume energy-only clears the ≤2-overrides/min quality gate *even in the >6 dB SPIKE-AUDIO branch*. The realistic expectation, stated now: energy-only argmax + a conservative FSM + speaker-transition-only cutting should get *angle selection* right on clean single-speaker segments (where one mic dominates by >6 dB) and will **predictably struggle on overlap/crosstalk and one-word acknowledgements** — exactly the cases §2.1 already says every shipped director is weakest on. We plan for the gate to be **marginal, not safe.** Conservative pacing is a partial mitigation, not a guarantee.

**The concrete branch if the quality gate fails at week 6 (angle choice too noisy) — pre-committed now, not decided tired at week 6.** We do **not** ship a director that trips its own ≤2-overrides/min gate. Instead we **degrade the director from *angle selection* to *cut timing*:** the FSM stops choosing *which* angle and proposes only **where** the cuts fall — the rhythm and boundaries (speaker-transition timing, min/max shot duration, hold-longer-on-reactions heuristics) — while the **angle choice stays manual**, pre-seeded to the highest-energy angle as a one-tap default the editor confirms or flips in the ribbon (§design 6.4). Timing from speaker-transition detection is robust *even when which-speaker is ambiguous*, so this is strictly easier than full angle selection; it still deletes the most tedious part of manual multicam (finding and placing every cut point). The pacing dial and timing engine are reused verbatim; only the argmax angle-assignment flips from automatic to a confirmable default.

**The timing-only mode has its OWN concrete, falsifiable quality bar — it is not exempt from scoring.** An earlier draft claimed timing-only "cannot fail its own gate because it no longer makes the claim the gate scores," which made the most-likely-shipped mode unfalsifiable. Corrected: timing-only is scored on **cut-point placement** against a human reference on the SPIKE-AUDIO clip — **≤ X spurious-or-missed cut *points* per minute, each dismissable/movable in ≤ 1 tap** (X defined and measured in 03-tech §10.4). And with whisper.cpp now in V1 (§5.1 #14), timing-only is materially stronger than energy-only: **transcript word/sentence boundaries give the FSM natural cut points** — cutting on sentence ends and speaker turns rather than on raw energy transitions — which places cut *timing* closer to where a human cuts and pulls the spurious/missed rate down toward the bar. This gives three pre-drawn week-6 outcomes: **full angle-selecting director** (angle gate passes) · **timing-only director** (angle gate fails but audio has signal — scored on the timing bar above, whisper-assisted) · **no director at all** (<3 dB, §5.4).

**And the paid tier's value does not depend on the director clearing the gate — in any of the three outcomes.** This is the entire point of the §7.1 decoupling: the paid unlock is the **scarce multicam editor**, which is spike- *and* gate-independent. A director that ships timing-only, or is cut entirely, removes an *accelerant*, never the *reason to pay*. The pricing story ("the multicam editor nobody else has on Android, plus smart-reframe and take-review") stands verbatim in all three branches.

### 5.6 The spike-independent paid floor — and it is deterministic CV, NOT "AI"

The AI Director (§5.5) is gated on SPIKE-AUDIO and pre-committed to be **cut** in the < 3 dB branch (§5.4). An earlier draft closed the resulting hole by declaring a *second AI feature* — the framing model — as the paid tier's "AI floor." **That was a mislabel, and the mislabel is exactly the kind of self-flattery this document warns a reviewer will catch and discount (§1.5).** The framing model is **classical CV/DSP** — face-bounding-box size, rule-of-thirds offset, luma clipping, Laplacian focus — and 03-technical-spec §11 correctly lists it as "DSP/CV rules," not a neural net. Selling heuristics as "AI" creates an internal contradiction between this section and the model table. So this section is corrected: **we describe the floor as what it is — on-device *smart-reframe* and *take-review*, deterministic computer vision — and we do not call it AI.**

**The floor is real and useful regardless of the label.** §5.2 already computes these per-frame, **single-angle** signals for the director's shot-variety policy; they need **no inter-angle audio separation whatsoever**, so they are completely independent of SPIKE-AUDIO. We expose them as paid features in their own right (feature #12):

- **Single-angle smart reframe / auto punch-in (the "virtual second camera").** From one static wide phone angle, track the speaker's face and emit a *re-editable, keyframed* crop — a second angle conjured from a single source (this is demo **Beat A**, §1.4). Deterministic CV, not a learned reframer (the full learned auto-reframe stays V2, §5.2).
- **Auto take-quality review.** Score every take and segment on the same framing signals plus focus and clipping, and surface "this stretch is soft / clipped / poorly framed" so the producer culls without scrubbing the whole take.

**The one real dependency, named honestly:** the face-tracking under smart-reframe needs an **on-device face detector**. That is *not* size-0 DSP — it is a real component with a runtime, size, and per-device availability. The V1 choice (ML Kit Face Detection, a GMS dependency, vs the GMS-free MediaPipe Face Detector Task) and its size/delegate/inference budget are specified in **03-technical-spec §11 / §3 build config** — this must be reconciled there against the "NO MediaPipe" build line, because smart-reframe and take-review cannot ship without a detector. It remains **deterministic CV over a detector's output**, not "AI."

Both features are on-device, unmetered, and spike-independent. They are deliberately *modest*. The reason they matter is **not** that they keep "AI" in the paid tier in the cut branch — they don't, and we no longer pretend they do. It is that they add genuine, visible value on top of the paid tier's actual reason to exist: **the scarce good.** See §7.1 — the pricing logic is repaired by *decoupling from the AI-label entirely*, not by relabeling CV as AI. And the paid tier *does* now carry one genuinely-learned on-device model in V1 — whisper.cpp transcript cutting — specified next; it is honestly labelled as *learned* AI, and it is distinct from the deterministic floor above.

**whisper.cpp transcript-driven cutting — the paid tier's ONE genuinely-learned on-device model, pulled into V1 (§5.1 #14).** Unlike the smart-reframe/take-review floor above (deterministic CV) and unlike the energy-argmax Director (a hand-written FSM), this is a *learned* model, and it is the V1 answer to the objection that "the paid tier needs one real learned-AI beat, not just an angle picker." It is also **spike-independent** — it transcribes one audio track post-record and needs no inter-angle audio geometry — so it ships in the paid tier whether or not SPIKE-AUDIO lands. Specifics:

- **Model + size (authoritative table — reconciled identically across all three specs; supersedes the earlier "~39 MB / ~74 MB" figures, which were the *parameter counts* — ~39 M / ~74 M params for tiny/base — mistakenly written as megabytes).** V1 bundles **q5_1 int-quantized** GGML weights in-APK — **not** f16. **Shipped sizes: `tiny.en` q5_1 ≈ 31 MB · `tiny` q5_1 ≈ 31 MB (multilingual) · `base.en` q5_1 ≈ 57 MB · `base` q5_1 ≈ 57 MB.** (f16 reference weights, which we do **not** ship: tiny ≈ 75 MB, base ≈ 142 MB — so any "75 / 140 MB" figure is f16, and the "int8/q5" label must never be attached to it.) **The shipped DEFAULT is `tiny.en` q5_1 (≈ 31 MB)** — consistent with the English-first stance and the tightest thermal/latency envelope, and the model the demo-path runtime precondition is measured against (**≤ 1× realtime wall-clock on the owned tablet SoC**, week-1 whisper spike, 03-tech §16.1); **`base.en` q5_1 (≈ 57 MB) is the opt-in accuracy upgrade** (offered only if it also clears the runtime bar on the owned hardware); multilingual `tiny`/`base` weights are a later option. **03-tech §11.4 is the single owner of this table; this section and design §6.8 quote it, never paraphrase it.** *(Round-1 fix: the three docs previously stated the default three different ways — base.en here, tiny.en in 02, multilingual tiny in 03. That drift is eliminated: one default, one owning table.)*
- **On-device runtime:** the **whisper.cpp native C++ build** (GGML), vendored and compiled under the NDK (arm64-v8a, NDK r28+/16 KB pages) — **not** a Maven dependency and **not** LiteRT, because whisper.cpp is its own inference runtime (03-tech §3 build config, §13). **Post-record, not live:** it runs on the STOP path / on demand over the chosen audio angle, off the capture hot path, so it never competes with the two capture encoders for thermal headroom (03-tech §7.2).
- **Transcript → cut UX:** the editor gets a **word/sentence-timestamped transcript** of the take; deleting a word or sentence removes that span from the program (Descript-style, propagating to the multicam `Composition`), and sentence/turn boundaries seed the director's / timing-engine's cut points (§5.5). It edits the same **re-editable `Composition` decision-list** — never a flattened render. **A deletion is a first-class model operation, not a hack on the assembly:** 03-tech §8.2 derives a **take-time→program-time map** from the deletion set — N deletions turn the audio track into N+1 clipped items, every downstream `AngleCut` re-times through the same map, and **audio continuity across deletion seams is an explicit binary pass/fail line in the export spike** (03 §16.1), because clipped audio items *can* gap or click where a single unclipped track could not.
- **Quality bar:** the bar is **cut-point usability, not verbatim caption accuracy.** tiny/base need only place word/sentence boundaries accurately enough that a deletion lands on the intended span and a sentence-end reads as a natural cut, on the clean single-speaker room audio the beachhead shoots (§2.1). Verbatim WER for burned-in captions is explicitly *not* the V1 bar — and #16's **`.srt` sidecar** rides these same timestamps without changing that: a sidecar carries no accuracy promise (platforms let users edit captions post-upload; the transcript UX fixes words pre-export), while **burned-in captions remain out of scope**. Measured in the whisper spike (03-tech §13).
- **Why it lands where Resolve cannot:** Resolve's transcript editing is Neural-Engine, Studio-paid, and desktop/iPad-only (§1.1) — it does not exist on an Android phone. whisper.cpp is MIT-licensed with a clean patent surface and on-device with zero marginal cost. This is the concrete "we serve the platform Blackmagic declines" claim made real, and the paid tier's one honestly-learned V1 model.

### 5.7 The build sequence — proving 14 subsystems fit the window, with a pre-committed cut ladder

**The objection this answers:** §5.1 lists ~14 interdependent, individually non-trivial subsystems to be authored by Claude Code and debugged by a solo builder with near-zero Android knowledge, much of it against experimental/unpublished APIs. The module-isolation architecture (03-technical-spec §1) scales the *codebase*; nothing yet scaled the *build effort*. Asserting "4–8 weeks" without a schedule is the weakest claim in the plan. Here is the schedule, and the pre-committed order in which scope drops when a week slips.

**The framing that makes it fit: the demo-critical subset is smaller than the feature list.** The hard gates G1–G8 + the timeline hero beats (§8) do **not** require the AI Director, the live compositor grid, or any experimental-API scrubbing surface; whisper (#14) is likewise off the *gate* path (Beat C is post-record). Those surfaces are sequenced **last**; the spike-gated ones (director, compositor grid) are **first to cut**, while whisper is **non-cuttable** — protected by decision, its slip moves the date (see the ladder note below). The demo-critical subset is provable in **~6 weeks**.

**Two honest cost additions the earlier "6 weeks + 2 slack" claim did not carry (folding in the round-3 corrections):**
- **whisper.cpp transcript cutting (V1 paid, §5.1 #14) adds ~1–1.5 weeks of scope** — the NDK native build, post-record transcription integration, and the transcript→cut UX. It is **not** demo-hero-critical (the hero beats are Beat A + Beat B, neither of which needs whisper), so it is built after the hero surface and it **consumes most of the former weeks-7/8 slack.** The realistic picture is therefore **~7–7.5 weeks of committed scope with thinner remaining slack — and a ~7.5–8-week ceiling once the debugging/learning-curve buffer below is counted** — not "6 weeks flat with 2 clean weeks spare."
- **Week 1 is a bootstrap cost, not free.** Per 03-technical-spec §16, the week-1 spikes split into a **standalone batch on pre-recorded stock-camera footage** (decoder budget, SPIKE-AUDIO, heterogeneous export, CompositionPlayer seek, thermal — no app scaffold needed) and an **infra-dependent phase** (concurrent dual-encoder, camera-stream-combo, face-tracker quality bar) honestly budgeted at ~1.5–2 weeks because it needs weeks-2–5 scaffolding. The schedule below reflects that the infra-dependent spikes trail into weeks 2–3 rather than all landing in a single week-1.
- **A third cost the earlier estimate hid: the solo near-zero-Android *debugging* buffer.** The plan's own #1 named constraint (03-tech §1.2, §16) is not authoring speed — Claude Code writes the code — it is a solo builder with **near-zero Android experience debugging** raw Camera2 + Media3 experimental surfaces + the JNI/NDK whisper build + GL tonemap shaders. Claude-Code implementation speed does not remove that human debugging cost, and with slack now near-zero, **a single multi-week debugging surprise on a protected item** — the camera stream-combo integration (03-tech §7.2 calls the stream combination the hardest feasibility question in the plan; capture is now **CameraX-first**, riding Google's device-compat quirk layer, with raw Camera2 only as the spike-gated fallback) is the likeliest — would push straight into the protected spine. So the schedule now carries an **explicit ~1-week debugging/learning-curve buffer, distinct from spike-fallback slack**, and it pre-commits *what trims if that buffer is consumed* — and **whisper (#14) is not on that list** (locked decision: whisper is **non-cuttable, demo-critical** — the paid tier's guaranteed learned-AI beat and the protected novel wow, so cutting it first would gut the very reason it was pulled into V1). The trim order, matching 03-tech §16: **(1) the opt-in composited preview, (2) the director's timing-only refinement polish (ship the simpler hold-longer degrade), (3) the demo from 4 angles → 2 angles** (the beachhead is 2 speakers; the timeline/export/whisper/reframe spine is angle-count-agnostic). And because the date is **movable (locked decision: quality over calendar)**, a slip that outruns even those valves moves the *date*, not the deliverable: "~7.5–8 weeks" is a sequencing estimate, not a commitment — the plan targets the full 4-angle + whisper + director + HLG10 build and flexes the calendar on a red gate rather than silently shrinking scope.

**Dependency serialization — the infra spikes are sequenced AHEAD of the build weeks that depend on them, never concurrent (mirrors 03-tech §16).** A solo builder cannot author the week-3 dual-encoder capture (**CameraX 1.5 + Camera2Interop first**; raw Camera2 only if the stream-combo spike proves CameraX cannot bind the surface set) *while simultaneously* proving that the capture is feasible, so the schedule serializes the dependency honestly: **the week-3 capture build cannot start until the §16.2 CAMERA-STREAM-COMBO spike is green** — it builds *on* the validated stream combination (the HLG10-master + 10-bit-sampleable-texture + preview surface set, 03-tech §7.2). The spike is typed against **named candidate CameraX mechanisms in priority order** (a `CameraEffect`/SurfaceProcessor fan-out to viewfinder + tonemap→proxy first, then Preview with an app-owned `SurfaceProvider`, only then raw Camera2 three-surface), and a red requires **all listed candidates exhausted** — not a naive bind failure — so the stack cannot flip to raw Camera2 on a false negative (03 §7.2/§16.2). The infra-dependent spike phase (stream-combo, concurrent-encoder, face-tracker bar) is budgeted at ~1.5–2 weeks and runs *before* week-3 capture, not overlapping it. **If that spike phase slips, the relief valves are the §5.7 trim ladder (preview → director polish → 4→2 angles) and, past those, the movable date — never whisper (#14, non-cuttable) and never the spine.** With the infra spikes sequenced ahead of their dependent build weeks, the hard-gate spine (G1–G8 + hero + whisper) fits the estimated window; the price of the honesty is the estimate moving to **~7.5–8 weeks**, and the calendar — not the deliverable — absorbs anything beyond it.

**QR pairing is named build scope, not silent scope (round-1 fix).** The §5.1 #3 QR fast path is demo-visible (beat 2 has both phones scanning on camera) but an earlier draft assigned it to no build week. 03-tech §16.3 now carries it as explicit line items — the transport half (`HELLO{token}` path, join flow) in week 2 alongside `:core:transport`; the UI half (QR render on the controller, `ImageAnalysis`+ZXing scanner on the camera) in week 4's controller build — at an honest ~2–3 days total. **Pre-committed relief valve:** if it slips, demo beat 2 degrades to the NSD discovery-list path (already fully specified, 03 §5.1–5.3) with **zero gate impact**, and the beat-2 script drops the QR flourish. The valve is mirrored in 02 §5.7; nothing on the gate path depends on QR.

**Program-preview note (removes the last experimental surface from the critical path):** per 03-technical-spec §8.1/§13.1, the PROGRAM preview is driven by the **active angle's already-running ExoPlayer** (the 4× grid is already built), reserving `CompositionPlayer`/`Transformer` for **export only**. Single-sequence `CompositionPlayer` preview+seek is treated as **measured-pending** until a week-1 spike exercises it across many clipped media items; the interactive scrub path does not depend on it.

| Week | Primary build | Subsystems (from §5.1) | Gates it lights | Checkpoint / cut trigger |
|---|---|---|---|---|
| **0** | **Week-0 wow sanity-check (§1.6) — half a day, no code. A design input, NOT a sprint gate.** | — | (design input, not a gate) | Show a Beat-A mock + the rig to 3–5 producers. **Gimmick verdict → reorder to lead Beat B; rig-too-heavy → ship two-phones-flat default (§1.6).** No purchase-intent test; the build proceeds regardless — this only shapes the demo. |
| **1** | **ALL SPIKES + hardware audit — nothing hardens until these land.** | — | — | **SPIKE-AUDIO** (Q3), **SPIKE-DECODER** (Q4, AOSP CTS harness), **SPIKE-SYNC** (REALTIME gate + phase-align port V0–V4 — upside go/no-go only; the default audio-xcorr sync needs no spike), **SPIKE-THERMAL** (Q5), **SPIKE-COMPPLAYER** (single-sequence seek across many clipped items), **HARDWARE AUDIT** (Q9 phone brands + D2 foldable → *buy a differing-brand phone / foldable now if needed*). Every downstream branch is chosen from these results. |
| **2** | Transport + capture foundation | #1 record-local CFR, #2 REALTIME gate, #5 fault-tolerance, #13 mixed-vendor (falls out of gating) | G1 (roll/stop), G4 (controller-loss), G8 (CFR) | Audio-xcorr is the default sync (03 §4.5); the phase-lock port is attempted later only if its week-1 go/no-go was green. |
| **3** | Controller + monitoring + thermal | #3 tablet controller + responsive substrate, #4 WebRTC monitor grid, #6 thermal governor | G2 (sync at min 20), G3 (thermal 20-min) | Monitor grid rung chosen from SPIKE-DECODER (§5.3 ladder). |
| **4** | **The hero surface (DEMO-CRITICAL)** | #7 proxies, #8 4× ExoPlayer shared-clock timeline, program preview via active-angle ExoPlayer | **Hero-moment gate** (timeline present + scrubbable < 2s), #11 monitoring overlays | **CHECKPOINT-4:** if slipping, fire cut ladder from rung 1. The hero beats must be green by end of week 4. |
| **5** | Export + paid editor interactions | #9 single-sequence export (`Transformer`), #12 smart-reframe + take-review, ≤2-tap override + glass-box | G5 (rough-cut budget prep), G6 (export ≤1.0×) | Face-detector integration for #12 (03-tech §11). |
| **6** | **AI Director — IF SPIKE-AUDIO passed** + **whisper.cpp (#14) begins** + demo capture | #10 director (VAD+FSM+Composition), #14 whisper (NDK native build + post-record transcription + transcript→cut UX) | Quality gate (≤2 overrides/min); whisper cut-point bar (03-tech §10.4/§13) | **CHECKPOINT-6:** demo choreographed + scrcpy-captured. whisper (~1–1.5 wk) spans wk 6→7. |
| **7** | **whisper finish + SLACK** | #14 whisper completion; then absorb red-spike branches, thermal tuning | — | whisper is **non-cuttable** (locked): if wk 6 slips, the relief valves fire (opt-in composited preview → director timing-only polish → 4→2 angles) and past them the **date** moves — whisper never drops to V2. |
| **8** | **SLACK (thinned)** | demo re-shoots, name clearance (§9.7), polish | — | Thinner than the old "2 clean weeks" claim, because whisper is real V1 scope. Never scope-expand into it. |

**The pre-committed cut ladder — drop in THIS order when a checkpoint slips, protecting the demo-critical subset. whisper (#14) is NOT on this ladder: it is non-cuttable (locked). A slip that would otherwise reach it instead fires the relief valves — (a) the opt-in composited preview, (b) the director's timing-only refinement polish, (c) the demo from 4 angles → 2 angles — and past those the DATE moves (quality over calendar), never whisper and never the spine:**

1. **AI Director (`:director`, #10)** — already spike-gated; drops if SPIKE-AUDIO is red *or* week 6 slips. Demo keeps the hero beats (including Beat C) + the manual multicam editor. **This is the first cut and it costs the demo nothing structural.**
2. **Smart-reframe / take-review (#12)** — degrade to *take-review only*, then cut entirely. Paid tier still = the scarce editor + whisper transcript cutting (§7.1). *(Beat A relies on smart-reframe, so this cut costs Beat A, and #15's face-tracked vertical export degrades with it to a static-crop preset; Beats B and C still carry the demo. Cut #12 only after #10.)*
3. **WebRTC live grid (#4)** → still-refresh / single-tile path (§5.3 fallback ladder). Demo monitors one angle live + periodic stills.
4. **Live 2×2 grid** → single-angle preview (decoder-budget fallback, §5.3).
5. **Mixed-vendor demo framing (#13)** → same-brand shoot (claim stays true, just not *demoed*). Narrative-only, zero build cost.
6. **Tabletop-fold posture (D2)** → flat capture layout. Zero functional loss.

**Explicitly gated OUT of the demo critical path: the hand-authored MediaCodec→GL→encoder compositor (03-technical-spec §8.4).** It is the largest authored subsystem and, if the decoder spike goes red, the honest response is to **accept a lower fallback rung** (single-angle preview + still grid, ladder rung 3–4) for the demo — **not** to author the compositor under sprint pressure. The compositor is a V2 item; it never lands on the 8-week critical path.

**PROTECTED — never cut, and this is the subset the 6-week proof covers:** G1–G8 + the hero beats (§8) = record-local + audio-xcorr sync (the 03 §4.5 default; the REALTIME-gated exposure-phase port is optional upside) + 4× ExoPlayer shared-clock timeline + single-angle export + the manual multicam editor — **plus whisper transcript cutting (#14), protected by decision rather than by API maturity: it is the paid tier's guaranteed learned-AI beat and the protected novel demo wow, and its slip moves the date, not the scope.** Every other protected item is a *mature, non-experimental* path (plain ExoPlayer, MediaCodec, `Transformer` export, our own DSP/transport) sequenced into weeks 2–5. The experimental-API-heavy or spike-gated surfaces (AI Director; live composited grid) are the last built and the first cut. **That is why the demo-critical hard-gate set + hero fits in ~6 weeks — with whisper (V1 paid), the week-1 bootstrap, and the ~1-week solo-Android debugging buffer consuming the former weeks-7/8 margin, so the honest committed ceiling is ~7.5–8 weeks with thinned slack, not "6 weeks + 2 clean."**

**If the honest sum still exceeds 6 weeks at CHECKPOINT-4,** the pre-committed response is to cut down the ladder *before* touching the protected set — e.g. defer WebRTC live monitoring to the single-tile/still-refresh path (ladder rung 3) for the demo — until the protected set fits. The decision is made against this ladder, not emotionally at week 6.

---

## 6 — V2 / V3 arc

**V1.5 — named now, built later (no new subsystems, so deferring costs no architecture)**
- **On-device highlight extraction ("cut me a Short"):** transcript (#14) + VAD energy (§5.5) + the FSM pick the strongest 30–60 s segments → reframe (#12) → vertical export (#15) → captions (#16). That is Opus Clip's cloud product, assembled on-device from V1 parts — shoot a 40-minute conversation, leave with the episode *and* three Shorts, nothing uploaded. Deliberately V1.5: zero new subsystems, so naming it now shapes the roadmap story at no build cost (design sketch owned by 03-tech).
- **USB/lav external audio input:** Android routes USB-audio-class devices automatically; the work is UI (source picker + level meter), not plumbing. One move improves whisper WER, the director's VAD, *and* sync xcorr — the highest-leverage V1.5 item. (V1 records each phone's default mic via `AudioSource.CAMCORDER`, 48 kHz — §5.1 ground rules, 03-tech.)

**V2 — "the rig gets real"** (only if V1's demo produces a buyer signal)
- PiP / split-screen as an **export-only effect** (no scrub requirement → dodges #2439/#2742)
- **whisper.cpp transcript cutting is now V1** (§5.1 #14, §5.6). V2 extends it from transcript *cutting* into an EditIQ-style **dialogue-aware angle-selection** director — the transcript driving *which* angle, not only cut timing — the quality upgrade over energy-only selection.
- Lip-movement ASD (LR-ASD port) **iff** SPIKE-AUDIO landed in the 3–6 dB band — the case where audio alone is marginal and fusion actually pays
- 6 angles (CDD guarantees 6 concurrent SDR 1080p30)
- **USB-C SSD recording** (Blackmagic-parity, iOS-painful): SAF-scoped `MediaMuxer` FD handling + removal-mid-write hardening (03-tech storage spec)
- Connected-display / desktop-mode controller. Android desktop mode is GA on Pixel 8/9/10 and Samsung S26/Fold7/Flip7/Tab S11; Jetpack WindowManager 1.5.0 stable with new Large (1200–1600dp) / Extra-large (≥1600dp) classes via `BREAKPOINTS_V2`.
- Auto-reframe, unmetered
- **FCPXML / EDL export.** Note the tension: it violates "no desktop." It is included anyway as a **trust feature, not a workflow** — "you are not locked in" is what makes people commit. LumaFusion sells FCPXML as an iOS-exclusive; we give it away.
- Trellis optimisation replacing the FSM (Disney/CMU SIGGRAPH 2014)

**V3 — "the filmmaker tier," only if V2 finds the money**
- Scopes: false colour, waveform, histogram, LUT preview (the open territory we deliberately declined in V1)
- 4K, high-speed 120/240fps (CameraX 1.5 ships direct slow-mo encode)
- Crosstalk handling — the documented weak point of *every* shipped auto-director, and the most defensible quality differentiator that exists in this space

**Explicitly not on the arc:** live switching, cloud, LOG, eye-contact correction, weddings.

---

## 7 — Tiering & pricing

### 7.1 The tension, resolved with an argument

**Open Tension #1 as briefed:** *"Auto-sync is arguably what makes multicam WORK AT ALL. Paywalling it makes the free tier feel broken. Current call: auto-sync FREE, AI Director PAID. Pressure-test this."*

**Half of the current call survives. The other half is backwards.**

**Auto-sync free — CONFIRMED, and it is forced, not generous.** Three incumbents, three business models, one line:

| Incumbent | Free | Paid |
|---|---|---|
| Blackmagic | Capture + 9-angle multicam + sync | (hardware) |
| LumaFusion | — | **$19.99 one-time for the multicam *editing surface*, not sync** |
| Riverside | Local recording | $39/mo for the AI layer |

**Sync is infrastructure; the layer above it is the product.** And the competitive floor is absolute: **Blackmagic gives away 9-angle multicam capture with sync for free.** Paywalling sync against that is suicide. Additionally, mobile shooters *violently* reject camera-app subscriptions — Filmic Pro went from $19 one-time to $2.99/week and triggered "uproar on Reddit and Twitter," with sentiment analysis citing subscription backlash as the primary driver of **migration to Blackmagic**. Capture is anchored at **$0** by the market. We do not get a vote.

**AI Director paid — REJECTED. This is the reversal.**

The brief's free tier is *capture + multicam + auto-sync + basic cut*, with the AI paid. **That prices the commodity and gives away the scarce good.** Look at what each object actually is:

| Object | Scarcity | Who else has it |
|---|---|---|
| Capture + sync | **Zero.** Free floor. | Blackmagic, free, 9 angles |
| **AI Director / AI multicam** | **Absent on Android** — but not a durable moat (Resolve Studio does it off-device for $295). | **Nobody, on an Android phone.** Resolve SmartSwitch is Studio-only + desktop/iPad-only. |
| **The multicam editor on Android** | **Total. No competitor exists at any price.** | **Nobody.** LumaFusion withheld it; Resolve never came; KineMaster never built it; Rush is dead; Descript has no mobile app. |

**You cannot build a business on a commodity. Price the scarce good.** The line moves from *capture-vs-AI* to **capture-vs-editor**:

| Tier | Contents |
|---|---|
| **Free, forever, no watermark, no time limit** | Capture · auto-sync · live monitor · per-device control · **single-angle** trim + export · CFR/VFR fix |
| **Paid (one-time unlock)** | **The multicam timeline** (the scarce good) · **single-angle smart reframe + take-quality review** (spike-independent *deterministic CV*, §5.6) · **whisper.cpp transcript-driven cutting** (the one genuinely-learned on-device model, spike-independent, §5.1 #14) · **vertical 9:16 export** (#15, rides the reframe `CropTrack`) · **SRT caption sidecar** (#16, rides whisper timestamps) · AI Director *(genuine AI, if SPIKE-AUDIO lands)* · multi-angle export · everything in V2 |

The free tier is deliberately *terminal but honest*: it is a complete single-camera tool that is genuinely useful, and it is exactly Blackmagic parity — which is the most we can charge for, i.e. nothing. **The AI Director rides along inside the paid tier as an accelerant, not as the reason.** Marketing it as the reason invites a comparison to a Blackmagic-trained model that we lose.

**The pricing claim rests on the scarce good, not on the AI label — and this is deliberate after correcting the earlier draft.** An earlier version defended the paid tier as "the multicam editor + real on-device AI" and leaned on the framing model to keep "AI" in the tier even when the Director is cut. But the framing model is **deterministic CV, not AI** (§5.6), so that defense was relabeling heuristics — precisely the self-flattery a reviewer discounts. The honest, and stronger, position:

- **What you pay for is the multicam editor itself** — total scarcity, exists nowhere else on Android at any price (§7.1 table below). That is spike-independent and label-independent; it does not need to be "AI" to justify the unlock.
- **Genuine on-device AI in the paid tier is now two things:** the **AI Director** (honestly *contingent* — SPIKE-AUDIO, §5.4 — and *V1-only-if-it-lands*) and **whisper.cpp transcript cutting** (V1, spike-independent, the one genuinely-learned model that is *not* contingent — §5.1 #14, §5.6). We do not claim more AI than ships.
- **The deterministic smart-reframe + take-review add real, visible value** (demo Beat A, §1.4) and are spike-independent — but we market them as *smart-reframe and take-review*, not as "AI."

So even in the < 3 dB branch the paid tier is **"the multicam editor nobody else has, plus whisper transcript cutting with its SRT caption sidecar, plus smart-reframe, take-review, and the vertical Short export"** — a fully honest, fully sufficient pricing story with **no dependency on the unrun SPIKE-AUDIO** (whisper and the CV floor are both spike-independent) **and no relabeled heuristic.** Nothing in the pricing model is hostage to SPIKE-AUDIO, and nothing in it calls CV "AI."

### 7.2 Subscription vs one-time — the contrarian call

**Ship a one-time unlock, not a subscription.** Five converging pieces of evidence:

1. **Photo & Video is the worst category in RevenueCat's dataset** (115k apps, $16B): lowest median trial-to-paid (**22.2%**) and **23% annual renewal**. It is structurally "pick-up/put-down." **A subscription in this category does not renew — so we would be building the renewal machinery to collect one year of revenue.**
2. **Subscription backlash is category-specific and violent** (Filmic Pro, above). It drove users *to our main competitor*.
3. **LumaFusion proves one-time works on Android** at $29.99, amid active backlash at CapCut (annual $77 → $179.99, Feb 2026) and Apple ($4.99 → $12.99/mo). **The incumbent price umbrella lifted at both ends within 12 months.** That is the opening.
4. **Zero marginal cost.** Everything is on-device: no inference bill, no storage bill, no egress bill. There is nothing recurring to fund. A subscription would be renting access to a constant.
5. **Android billing failures cause 31% of cancellations vs 14% on App Store.** Recurring billing is measurably worse here.

**Price: $49 one-time** ($39 launch). Anchors: LumaFusion iOS base + multicam = $49.98 for the comparable stack; AutoPod $29/mo — we undercut it in under two months; FCP iPad $12.99/mo; CapCut $9.99/mo. Play takes **15%** under the small-business programme.

**Trial: 30 days, full features — not 3 days.** 55.4% of 3-day trials cancel on Day 0; 17–32 day trials convert at **42.5%**.

### 7.3 The number we plan against

**Do not anchor on the widely-cited ~8% freemium benchmark — that is B2B SaaS and does not transfer to consumer mobile.** RevenueCat's actual consumer numbers: freemium converts at **2.1%** median Day 35 vs **10.7%** for a hard paywall, yielding **$0.38 revenue/install vs $3.09** — roughly **8×** worse.

**Realistic planning number: 2–4% of installs buy.** Also relevant given our paid tier carries on-device AI (whisper transcript cutting always; the AI Director if it lands): AI apps carry **+41% Year-1 LTV** ($30.16 vs $21.37) but **36% worse 12-month retention** — which, note, is an *additional* argument for one-time over subscription. (Our pricing rests on the scarce *editor*, not the AI — §7.1 — so this LTV/retention skew is a secondary read, not the thesis.)

**The honest read: this pricing is optimised to lose the least, not to win big.** That is appropriate for an experiment whose purpose is to answer a question (§4), and it should not be mistaken for a plan to build a company.

---

## 8 — Success metrics for the demo (week numbers are sequencing estimates — §5.7; the date is movable, the gates are not)

**Week-0 wow sanity-check (§1.6) — a design input, not a sprint gate.** Before any build, Beat A's wow-status and the capture-rig friction are sanity-checked with 3–5 real non-consumption producers. This is **not** a go/no-go on the sprint (the builder builds regardless; **no purchase-intent test is run** — §1.6) — it informs *design* choices, with pre-committed responses (lead Beat B if the punch-in reads as gimmick; ship a two-phones-flat default if the rig reads as too much work). The gates below stand on their own.

**Hard gates — binary, measured on owned hardware (2 phones + 1 tablet). Any red is a failed demo.**

| # | Gate | Threshold |
|---|---|---|
| G1 | Roll/stop all cameras from the tablet | 3/3 devices, <500ms spread |
| G2 | **Sync accuracy, clapper-verified across all angles, at take start AND at minute 20** | **≤ 1 frame (33ms @30fps).** Clock drift is <1.2ms/min against a 33ms frame, so a ~10min re-sync is ample — *prove it at minute 20, not minute 1.* |
| G3 | **Continuous take, all devices charging** | **20 minutes, zero thermal stop.** See §7.2 risk. |
| G4 | **Controller-loss test** — pull the tablet off the network mid-take | All cameras keep recording; all files intact and playable |
| G5 | Rough cut generated on-tablet | ≤ 0.5× take duration |
| G6 | Export 20-min 1080p30 program | ≤ 1.0× realtime |
| G7 | **Zero network egress** | Verified by airplane-mode run + Wireshark. **This is the thesis; it is not a nice-to-have.** |
| G8 | **CFR output** | Constant frame rate verified in Resolve. No VFR. This is JTBD-3. |

**Quality gate (scored, not binary):** 10-minute 2-person interview → AI rough cut → a human reviewer counts cuts needing override. **Target ≤2/min, every one fixable in ≤2 taps.**

**Hero-moment gate (underpins the §1.4 timeline-present beats, A and B; Beat C's whisper tightening is post-record and does not ride this gate) — and it must hold in BOTH sync branches.** On hitting stop, the synced multi-angle timeline is present and scrubbable across all angles in **< 2 s**, with **no import step and no network transfer of footage**. This gate is independent of SPIKE-AUDIO (the *audio-director* branch), but it is **not** automatically independent of the sync branch, and an earlier draft hid that. The timeline needs the inter-angle **offsets** to place every angle on a shared clock, and *where those offsets come from differs by sync branch* — so the gate is measured against both, and passes in both. **Note the round-3 sync inversion (03-technical-spec §4): the DEFAULT V1 sync is now audio cross-correlation, and live phase-lock is an optional upside — so the branch the demo actually ships is the audio one, and it is the one the budget is proven against.**

- **Default branch — audio cross-correlation (03 §4.5), the V1 build target.** Offsets are **not** known at STOP. The naïve fix — GCC-PHAT over the *whole* take at <0.1× realtime — is *minutes* on a 40-min take and would blow the gate; an earlier draft's hero claim quietly assumed the offsets were already known. So the default does **not** correlate the whole take at STOP. It runs a **fast windowed GCC-PHAT over only the first ~10 s of each angle's audio, in a narrow window primed by the live SNTP clock** — a bounded, fixed-cost alignment whose cost is independent of take length — resolving the inter-angle offsets in ~150–500 ms, well under the STOP→timeline budget. 03 §4.5 specifies this windowed alignment and 03 §6.4 carries it as an explicit **leg in the default branch's budget table**. Any full-take drift refinement (only ever needed on very long takes) runs *after* the timeline is already scrubbable, never on the hero path.
- **Upside branch — live phase-lock (03 §4.1–4.4, the raw-Camera2 port, built only in weeks-7/8 slack iff its week-1 go/no-go is green).** Offsets are known *at capture* via exposure-phase alignment, so at STOP the timeline assembles from local durations + already-known offsets (the windowed-xcorr leg is ~0). Trivially inside 2 s. Only reached if the optional port lands.

**The <2s figure is decomposed and budgeted in 03-technical-spec §6.4** (muxer finalize/flush per device + finalized-file ack + 4× ExoPlayer prepare/first-frame + timeline assembly; **plus, in the default audio branch, the windowed-GCC-PHAT offset leg**), marked estimated vs measured-pending; if the honest sum in *either* branch exceeds 2 s the gate or choreography is revised in week 1, not week 8. If this gate fails, the demo has no hero surface even when everything else works.

**Narrative gate:** the demo is filmed end-to-end (scrcpy). The video must be able to say, truthfully, **"no computer touched this footage."** If that sentence is not true, the demo failed regardless of the other gates. **And the gate now binds *time* as well as topology (round-1 fix):** the on-camera waits — whisper transcription, the program export, the Short/SRT export — are shown honestly: either in real time behind the existing `transcribing… / exporting…` affordances, or jump-cut with an **on-screen elapsed-time chip** (e.g. `⏱ 0:41`); no wait is silently elided and no beat is choreographed at a timestamp its own committed budget cannot meet. The 02 §8.1 timestamps are re-baselined against the specs' own bars (whisper up to 2× realtime worst-case, export G6 ≤ 1.0× realtime), with one named demo-path precondition: **`tiny.en` q5_1 clears ≤ 1× realtime wall-clock on the owned tablet SoC, measured in the week-1 whisper spike** (03 §11.4/§16.1) — and if it doesn't, the transcript beat moves later in the film behind a labelled time-skip rather than the film pretending the transcript was instant. A demo that jump-cuts a wait without labelling it is the same class of failure as a hidden upload.

**Stated in advance so it is not re-litigated at week 6:** if **SPIKE-AUDIO fails (<3 dB)**, the quality gate is **void** and the AI *Director* is **cut** — but the demo does **not** collapse. Four things still ship and still land: (a) the **§1.4 hero beats** — **Beat A**, the virtual-second-camera smart-reframe (a *visible new capability*, spike-independent, needs no multicam experience to wow), and **Beat B**, the finished cut playing full-screen with "no computer touched this" — both carry the demo's lean-in on their own; (b) the **§5.6 smart-reframe + take-review** (honest *deterministic CV*, not "AI") add real visible value to the paid tier; (b′) **Beat C — whisper.cpp transcript dead-air/filler removal** (§1.4, §5.1 #14) — is **untouched by a SPIKE-AUDIO fail**, because it needs no inter-angle audio geometry; it is spike-independent, the paid tier's one genuinely-learned model, and the demo's **novel positive co-hero** (the buyer-legible "new capability" beat that neither Beat A's commodity nor Beat B's absence supplies), still shipping and still demoing in the < 3 dB branch; and the paid tier's actual reason to exist is (c) the **manual multicam editor** — the scarce good nobody else has on Android at any price, which is what the pricing rests on (§7.1), *not* on any AI label. That demo clears G1–G8 **plus the hero-moment gate** and still proves the wedge (§4), because **the wedge was never the AI Director** — it was the absence of the desktop, and the finished multi-angle video existing in your hands with no computer in the loop.

---

## 9 — Risks, ranked

### 9.1 — Segment mismatch (BUSINESS). Highest risk in the plan.

**The free tier attracts the segment that refuses to pay; the paid tier targets a segment that already owns lavs, Premiere, and a desktop — i.e. the people least in need of an on-device phone app.** AutoPod's $29/mo buyers are professional podcast *editors* with billable hours. They are not mobile filmmakers. They are not us.

**Mitigation — and it is an argument, not a fix.** We are not competing for AutoPod's customers. Our buyer is the **producer of the content, not the editor of it**: the person for whom *the desktop step is the reason they don't shoot multicam at all* (JTBD-2, the non-consumption job). VVD's own pitch — *"does the work of a full-time staff member"* — is aimed exactly there, and it has sold into networks and parliaments for six years at $69–220. Our segment is the tier below VVD's: people who would be AutoPod customers if they had a Premiere workflow, and don't.

**That is a hypothesis. It is the single biggest unvalidated claim in this document, and the sprint exists to test it.** Do not let the demo's technical success be read as evidence for it.

**And the paid AI's want is a *distinct*, equally-unproven sub-hypothesis (§5.5).** AutoPod/VVD prove pro-editing shops want automated angle selection; they prove **nothing** about whether *our* non-consumption buyer wants a machine cut — that buyer has never done the manual version. A working AI Director demo will not close this either. **The sprint's actual paid-AI success criterion is therefore an instrumented behavioural signal, not the demo:** (1) free-capture → paid-editor open rate (Q7), and (2) conditional on the Director shipping, the share of editor users who *tap* auto-cut and then *keep* it (accept/light-override) versus revert to a manual pass (§5.5). Those are the numbers that would validate — or kill — the paid-AI thesis, and they only exist in a beta funnel, not in a demo video.

### 9.2 — Thermal. The demo-killer.

Primary Blackmagic forum reports: Android phones running the BM app "ran hot more than when using the standard camera app, and ran even hotter when charging at the same time, **stopping after about 10 minutes due to overheating**." A user's actual fix was strapping a Black Shark FunCooler 5 Neo to each phone.

**A 2–3 phone demo that must charge while recording can die in ~10 minutes. Gate G3 asks for 20.**
**Mitigation:** week-1 thermal spike alongside the decoder spike. 1080p30 not 4K. Predictive thermal governor with graceful degrade. Budget for passive cooling on the demo rig and *say so in the demo* — Blackmagic's users already strap fans to phones; this is an industry condition, not our defect. Discard the widely-repeated "68% of filmmakers experience overheating during 20+ minute takes" — it traces to a vendor blog selling cooling hardware and is unverified marketing.

### 9.3 — The AI Director may have no signal (§5.4)

**Mitigation:** SPIKE-AUDIO, week 1, decisive, cheap, on owned hardware. Pre-committed kill branch (§8).

### 9.4 — CompositionPlayer is unpublished and the compositor is broken

`@ExperimentalApi // TODO: b/470355043 - Publish CompositionPlayer.` — a ~25-month-old defect on exactly the feature we wanted; Google mid-rewrite, refusing date estimates, "high priority," shipping nothing; 1.11.0-beta01 contains **zero** CompositionPlayer entries.
**Mitigation:** the §5.3 re-architecture removes the dependency entirely — single-sequence only, 4× plain ExoPlayer for the grid. **Pin Media3 1.10.1; the API changes between minors, so the version must not float.** Do not use 1.11.0-beta01. Keep the fallback ladder live.

### 9.5 — Decoder budget (measured-pending)

**Mitigation:** CDD [5.1/H-1-2] mandates and CTS enforces 6 concurrent SDR 1080p30 — 4-up sits comfortably inside. Proxies forced to 8-bit SDR (§5.3). Port the AOSP CTS harness rather than trusting `getMaxSupportedInstances()`. The spike is written, not run.

### 9.6 — Patent: automated angle switching. Currently unmodelled; a direct hit.

**On Time Staffing** owns a 3-patent continuation family (**US10728443 / US11457140 / US11863858**) claiming automatic camera-angle switching **triggered by audio silence** to assemble an edited file with no human editing. Priority 2019-03-27, expires 2039. **It reads directly onto a naive director.**
**Mitigation:** cut on **speaker transition** (per-angle VAD), never on silence detection — **further from claim 1 *and* a better edit.** Separately, **Verizon US12323726** claims facial-detection switching with debounce and blending — another reason live auto-switching is out (§5.2). The brief already treats codec patents as why ffmpeg-kit died; **the same risk class applies to the director's core algorithm.** *Not legal advice — this needs a real opinion before any commercial release.*

### 9.7 — Naming. Release-blocking (not demo-blocking).

Apple's own trademark list registers **bare "Final Cut®"** as a standalone mark, separately from "Final Cut Pro®" and "Final Cut Camera®", and the Final Cut Pro registration sits in **International Class 009** covering software for creating, editing, processing, importing, exporting and encoding video — **the exact class and goods of this product.** Apple is a highly active enforcer: 100+ TTAB proceedings with filings continuing through July 2026; 350+ USPTO cases Jan 2008–May 2010; it opposed a five-person startup's *pear* logo in an unrelated industry and forced a change.
**No TTAB proceeding was found where Apple enforced against a third-party video app named "Final Cut." Do not read that as safety** — absence of found precedent is most plausibly deterrence or pre-filing settlement, and Class 9 identity of goods makes likelihood-of-confusion analysis trivially favourable to Apple regardless. **Budget for a clean-room name, not a near-miss.** §10.

### 9.8 — `ACCESS_LOCAL_NETWORK` and the targetSdk treadmill

Android 17 makes local-network access a **mandatory runtime permission** at targetSdk 37+. Gated: outgoing TCP connect, accepting TCP, sending *and receiving* UDP unicast/multicast/broadcast, `NsdManager`, resolving `.local`. Without it TCP fails by timeout and UDP returns EPERM. **This hits every leg of the defended transport simultaneously** — NSD discovery, TCP control, UDP time sync. Note this is a **targetSdk** trigger, distinct from the known minSdk-33 mDNS issue.
**Mitigation:** not demo-blocking (opt-in only in Android 16), but the annual treadmill forces it within ~1 year of shipping. Spec the permission request, a denial path, and a UX story for a permission users will not understand. Also declare `usesPermissionFlags="neverForLocation"` on `NEARBY_WIFI_DEVICES` — Play's one rule here is that derived data must never determine location.

### 9.9 — Blackmagic reverses its strategy bet

Their absence from on-device editing is a *funnel-protection decision*, not a technical limitation. Decisions reverse.
**Mitigation:** none available. Accept it. It argues for the short sprint and against the long build (§4). If they ship an Android editor, the thesis is dead and we should know that quickly and cheaply — which is precisely how this sprint is scoped.

### 9.10 — Other gates (schedule, not existential)

Target API 36 by Aug 31 2026 (ext. Nov 1). **16KB page size already in force since Nov 1 2025** and this app is native-heavy (whisper.cpp, LiteRT, MediaPipe, OpenGL) → **NDK r28+ is mandatory.** Camera/mic foreground services cannot start from background; declare FGS type + `FOREGROUND_SERVICE_CAMERA`. Play's AI-Generated Content policy triggers on *generative* apps and explicitly does not cover "productivity apps that use AI to improve an existing feature" — the AI Director generates no media and plausibly sits outside, **but the exclusion list is illustrative, not a safe harbor.** The July 15 2026 clarification extending User Data rules to third-party AI integrations **does not bite**: fully on-device means no third-party AI data sharing and no consent screen. **On-device is a genuine compliance moat, not just a privacy story.**

### 9.11 — Codec patents (de-escalated)

ffmpeg-kit's retirement was driven by legal **silence**, not a policy change: MPEG LA had explicitly confirmed FFmpegKit as an upstream component was **not** subject to patent fees; after Via-LA acquired MPEG LA (2023) the maintainer got **no response** to clarification requests and counsel advised retiring as the "safest option." No enforcement action. Both dates are correct and consistent: retirement announced Jan 2025, repo archived July 2 2026. Three further de-escalators: **the last US H.264 patent expires Nov 29 2027** (~16 months out); the AVC licence's first **100,000 units/year are royalty-free** (enterprise cap $9.75M); and the 2026 AVC fee hike ($100K → $4.5M) targets **Tier-1 OTT/FAST/social streaming platforms** — an app whose footage never leaves the device is not a content distributor. **HEVC is the messier surface** (no AVC-style free tier, multiple pools, patents run past 2027; Access Advance acquired Via LA's HEVC/VVC pools Dec 15 2025, rates +25% Jan 1 2026).
**Mitigation:** MediaCodec only, never bundle a codec. **Prefer H.264 for export defaults; treat HEVC as opt-in.** For *capture masters*, prefer **hardware AV1 encode** where `MediaCodecList` reports it (patent-cleaner, better bpp), HEVC otherwise — one capability query at session start, same MediaCodec surface code either way (03-tech). *The "does the OS shield you" answer is strong inference, not a published exemption. Flagged, not closed.*

---

## 10 — Naming

**Constraints:** must not contain "Final Cut" or "Cut" in a way that reads as a Final Cut derivative (§9.7); Class 9 collision is the danger; must survive a Play Store search; should not be generic-descriptive.

**Candidates, best first:**

| Name | Rationale | Concern |
|---|---|---|
| **Flypack** | **A flypack is the actual broadcast-industry term for a portable multicam production kit in a case.** That is literally what this product is — a flypack made of phones. Industry-authentic, descriptive-but-not-generic, zero Apple adjacency. | May read as jargon to the podcast beachhead |
| **Tally** | The tally light is the red light on the live camera — the switcher's native language. Short, camera-authentic, memorable. | Short common word → clearance risk |
| **Second Unit** | The film term for the crew that shoots the additional angles. Says exactly what the phones are. | Two words; possible film-industry collision |
| **Chorus** | Many voices, one output. The metaphor is the product. | Generic; likely heavily taken |
| **Vantage** | Angles, plainly. | Probably taken |
| **Coverage** | What a director calls having enough angles. | Too generic |

**Honest caveat: no USPTO, TTAB, EUIPO or Play Store clearance search has been run. These are candidates, not clearances.** Given §9.7, clearance must precede any public use of a name — and the name must be settled before the first external demo, because renaming after a demo is a marketing amputation.

---

## 11 — Open questions

| # | Question | Why it matters | How to close it |
|---|---|---|---|
| **Q1** | **Does CapCut *Android* ship multicam** (4/9-angle switching, Auto/Audio/First-marker sync)? | **The one fact that could narrow the wedge** from capture+cut+direct to capture+direct. Search summaries say "both platforms"; every corroborating tutorial found is CapCut **PC**, and the one mobile tutorial teaches **manual** syncing. CapCut docs are 451-blocked from India. Even if true it would be cloud-tethered and is not an AI Director. | **Install it and look.** Before positioning is final. |
| **Q2** | **Is Resolve 20 SmartSwitch (and the Neural Engine) Studio-only ($295)**, as the weight of evidence says, or free? | **Planning assumption is now Studio-paid** (§1.1) — the strong weight of independent sources (DaVinci Resolve Club, Filmora, ToolFarm) says the whole Neural Engine is Studio-gated; the lone "free" blog is likely AI-generated (§3.4). **Either way it is moot on Android**, where Resolve does not run at all — this only affects how we describe the *desktop/iPad* incumbent, not the Android vacancy. | **Open Resolve** (already installed per `00-build-setup.md`) and right-click a multicam clip; confirm which tier gates it. |
| **Q3** | **SPIKE-AUDIO: what is the inter-angle energy delta at 1.5m?** | **The AI Director's existence depends on it** (§5.4). | Week 1. 3 minutes of recording. |
| **Q4** | **Concurrent decoder budget on owned hardware?** | Sizes the whole preview architecture. Spike is **written and not yet run** — no numbers exist. | Week 1. Port AOSP CTS `MultiDecoderPerfTest`. |
| **Q5** | **Thermal ceiling while charging?** | Gate G3. Blackmagic's users see ~10min. | Week 1. |
| **Q6** | **Will anyone pay $49 one-time for a phone multicam editor?** | **No precedent exists in either direction** — the product category has never existed on Android. | Cannot be closed pre-demo. This is what the demo is *for*. |
| **Q7** | Does the free tier cannibalise the paid one? | If free capture is good enough, does anyone ever reach the editor? | Instrument the demo build's funnel from day one. |
| **Q8** | Naming clearance | §9.7, §10 | Real search, before any external demo. |
| **Q9** | **Are the two owned phones different brands** (so the mixed-vendor rig — §1.2 claim 4, feature #13 — is demoable), **and does any owned device fold** (so tabletop-mode capture, design D2, is demoable)? | The Tier-2 iOS-impossible capabilities (§1.5). If both phones are the same model / nothing folds, the claims stay true but are not *demoed*. The demo must not ride on hardware whose config is unknown. | **Week 1 hardware audit (§5.7), not demo time.** Check the drawer. If neither is demoable, either **buy one differing-brand phone and/or one foldable** (cheap, pre-committed) OR drop Tier-2 from the demo narrative and lean on Tier-1 (G4 background recording + G7 local ownership) + market-vacancy, both of which demo with certainty on the owned kit. |

---

## Appendix A — Decisions this document changes from the locked brief

Recorded explicitly so the deltas are auditable, not accidental.

| Locked brief said | This spec says | Because |
|---|---|---|
| "V1 hero: Live Multicam + **AI Director**" | V1 hero: Live Multicam + **no desktop**. AI Director is a *feature*, gated on SPIKE-AUDIO. | Resolve SmartSwitch is Studio-paid + desktop/iPad-only; on an Android phone AI multicam is **absent**, not a commodity (§1.1) |
| "The wedge: capture AND cut AND auto-direct on-device" | The wedge: **on-device, no desktop, no upload** — and **there is no multicam editor on Android at all** | LumaFusion withholds; Resolve absent; economic not technical (§1.1, §4) |
| Free: capture + multicam + auto-sync + **basic cut**. Paid: AI. | Free: capture + sync + monitor + **single-angle** cut. Paid: **the multicam editor**, AI included. | Pricing the commodity, giving away the scarce good (§7.1) |
| (pricing unspecified) | **$49 one-time**, not subscription. 30-day trial. | 23% annual renewal in Photo & Video; subscription backlash; zero marginal cost (§7.2) |
| Media3 **1.9.0** CompositionPlayer w/ 2×2 compositor is the load-bearing bet | Media3 **1.10.1** pinned; **single-sequence only**; 4× plain ExoPlayer for the grid; compositor **cut to V2** | #2439 seek unsupported; #2742 deadlock "all devices"; class unpublished (§5.3) |
| "assume proxies" | **Proxies MUST be 8-bit SDR H.264**; masters HLG10 | CDD H-1-2 (6 SDR) vs H-1-19 (3 HDR) — no guarantee of 4 concurrent HLG10 (§5.3) |
| Device allowlist + HARDWARE_LEVEL_FULL gate | **MPC ≥ 34** + runtime probe; `REALTIME` gates the *phase-lock upside* only, `FULL` gates *manual controls* only — neither refuses a camera | Google-maintained, CTS-enforced, 190M+ devices, free (§3.3; 03-tech §7.1) |
| "active-speaker detection (**MediaPipe** face/mouth)" | **MediaPipe has no ASD task.** Audio VAD + FSM for V1; LR-ASD port is V2 research risk. | Not implementable as written (§5.2, §5.4) |
| "Monitoring is OPEN TERRITORY" → implied V1 scopes | False colour / waveform / histogram **CUT** | Open territory the beachhead has not asked for (§2.4) |
| Filmic Pro acquired **2025** | Acquired **Sept 2022**; team laid off Nov 2023 | Conclusion ("fading") strengthened |
| Open Tension #2: "AI Director UNPROVEN AS A WANT" | **NOT closed — split.** Want PROVEN for pro-editing shops (AutoPod/VVD) who are *not* our buyer (§9.1); **UNPROVEN for our non-consumption beachhead**, and a tech demo won't prove it. Success criterion = instrumented free→paid + keep-the-cut signal (§5.5). Plus: unproven as a **differentiator** and unproven on **our audio**. | AutoPod/VVD validate the wrong segment; beachhead want needs a beta funnel, not a demo (§5.5, §9.1) |
| (thermal unlisted) | **Risk #2, demo-killer, week-1 spike** | ~10min stop while charging, primary-source (§9.2) |
| (patent risk = codecs only) | **On Time Staffing US10728443/11457140/11863858** reads onto the director. Cut on speaker transition, not silence. | Same risk class, unmodelled (§9.6) |
| (fault tolerance unaddressed) | **G4 controller-loss gate.** Record-local must survive controller loss. | Blackmagic "errors and cuts" — a free win (§5.1) |
| (VFR unclaimed) | **Lead the demo with CFR.** JTBD-3, gate G8. | Most common cause of multicam bugs; nearly free for us (§2.3) |
| V1 hero = "walk out with the edit assembled" (**AI-contingent**) | V1 hero = **two spike-independent beats engineered for the *non-consumption buyer***: Beat A the *virtual second camera* (positive new capability, no multicam experience needed) + Beat B the *finished cut, no computer* | The synced-timeline "relief" lands only on editors who suffered manual sync — not our buyer; the hero must move the non-buyer and survive SPIKE-AUDIO (§1.4, §8) |
| Paid tier = multicam editor + **AI Director only** | Paid tier = **the multicam editor (scarce good)** + **whisper transcript cutting (V1, genuinely-learned, spike-independent)** + smart-reframe + take-review (**deterministic CV, NOT "AI"**) + AI Director *if it lands* | Pricing rests on the scarce good, decoupled from any AI label; whisper is the one genuinely-learned V1 beat; relabeling CV as "AI" was self-flattery a reviewer discounts (§5.6, §7.1) |
| whisper.cpp transcript cutting = **V2** | **Pulled into V1** as a paid feature — the paid tier's one genuinely-learned on-device model | Proven shippable, MIT-licensed clean patent surface, on-device, lands where Resolve can't follow (Android phone); answers "V1 needs one real learned-AI beat" (§5.1 #14, §5.6) |
| (build effort unscheduled) | **Week-by-week build sequence (§5.7)** with a pre-committed cut ladder; demo-critical G1–G8 + hero fits ~6 weeks; **whisper (V1) adds ~1–1.5 weeks, week-1 bootstrap is real, and a ~1-week solo-Android debugging buffer is carried explicitly, consuming the former weeks-7/8 slack → ~7.5–8 weeks committed ceiling + thinned slack; infra spikes are sequenced ahead of their dependent build weeks, with a preview → director-polish → 4→2-angle trim ladder (whisper is non-cuttable) and a movable date absorbing tail risk** | Module isolation scaled the codebase, not the build effort; whisper scope + bootstrap cost budgeted honestly, correcting the "6 weeks + 2 clean" claim (§5.7) |
| Android differentiation = **one thin capability** (mixed-vendor) | **Tier-1 (demoable on any owned phone): FGS recording through controller loss [G4] + open-filesystem local ownership [G7]**; Tier-2 hardware-conditional (mixed-vendor, tabletop-fold, USB-C, DeX) resolved week 1 | Apple Live Multicam needs both apps foreground — FGS-through-loss is a structural iOS gap and needs no special hardware to demo (§1.5, Q9) |
| Differentiation read as **capability** (hardware-free sync "beats" everyone) | **Market-vacancy is the SOLE load-bearing differentiator.** The Tier-1 iOS-impossible pair (G4 FGS-through-loss, G7 local ownership) is a **supporting talking point / proof-of-life, NOT a proven purchase driver** — G4's only evidence is a *pro-user* complaint (§9.1); Tier-2 (mixed-vendor, fold, USB-C, DeX) is hardware-conditional, resolved week 1; sync is **Blackmagic-beating but Apple-matching** | Overclaiming the Android column is self-flattery a reviewer discounts; no beachhead evidence that fault-tolerance drives purchase (§1.1, §1.5) |
| (hero wow + capture-friction premise assumed) | **Week-0 wow sanity-check (§1.6) — a design input, NOT a sprint gate:** show a Beat-A mock + the rig to 3–5 producers; informs whether to lead Beat B and whether to default to the flat rig. **No purchase-intent / WTP test is run** — the builder builds regardless. | A null WTP signal would not stop the sprint, so gating on it is theatre; the wow check still usefully shapes the design (§1.6) |
| AI Director = full **angle-selecting** director whenever SPIKE-AUDIO passes | **Three pre-drawn week-6 branches:** angle-selecting (gate passes) · **timing-only** (gate fails but audio has signal — director proposes cut *timing*; angle choice is a confirmable highest-energy default) · none (<3 dB). Paid tier stands in all three. | Energy-only argmax with no ASD is the known-weak approach; plan for a *marginal* gate and never ship a director that trips it (§5.5) |

---

¹ cined.com — DaVinci Resolve 20 release coverage.
² blackmagicdesign.com/products/davinciresolve/whatsnew — SmartSwitch absent from Resolve 21 "what's new", consistent with an established Resolve 20 feature. *(Directly fetched.)*

**Sourcing honesty note:** Reddit was not directly accessible during research (old.reddit.com, www.reddit.com, and the JSON endpoint all blocked) and `forum.blackmagicdesign.com` returns HTTP 403 to automated fetches. **Every Blackmagic-forum and Reddit-derived statement in this document reached the author via search-engine summarisation, not first-hand reading.** Treat that wording as paraphrase, not verbatim quotation, even where quoted. The search index for this topic is also severely SEO-poisoned by AI-tool marketing content (§3.4).

---

## Changelog

**2026-07-18 — scope-addition addendum (former Addendum A) integrated into the body:** vertical 9:16 export **#15** and SRT caption sidecar **#16** added to the §5.1 feature list, §1.4 Beat B (two-artifact close) and the §7.1 paid tier; capture stack rewritten **CameraX-first** throughout §5.7 (raw Camera2 demoted to spike-gated fallback, per 03-tech); **QR pairing** noted at §5.1 #3; storage + audio-source ground rules added under §5.1 (masters app-scoped, exports via MediaStore/SAF, V1 mic = `CAMCORDER`); **V1.5 named** in §6 — highlight extraction + USB/lav audio input — and USB-C SSD recording added to V2; whisper cut-ladder contradiction removed — whisper is **non-cuttable** (§1.4, §5.1 #14, §5.7), the relief valves are composited preview → director timing-only polish → 4→2 angles, and past them the **date** moves, never the deliverable.

**2026-07-18 — refinement round 1:** (1) **whisper default model unified** — `tiny.en` q5_1 (≈31 MB) is the shipped default, `base.en` q5_1 (≈57 MB) the opt-in accuracy upgrade, with 03 §11.4 as the single owning table quoted (not paraphrased) here and in 02 §6.8; the round found the three docs stating the default three different ways (§5.6). (2) **Sync-accuracy copy corrected for acoustic propagation** in §5.1 #1 and §1.4 Beat B: <1 ms on co-located FLAT rigs, ≤ ~10 ms worst-case on the SEPARATED rig (arrival-time asymmetry ~2.9 ms/m) — both inside G2's ≤1-frame gate; the guaranteed-sync parenthetical no longer cites the ~250 µs upside figure as the guarantee. (3) **QR pairing** restated as 03 §5.4's two explicit modes (same-AP `{controllerIp, port, sessionToken}` direct-TCP default; `{ssid, psk, sessionToken}` `WifiNetworkSpecifier` join mode with its system dialog) and named as scheduled build scope (~2–3 days, weeks 2+4 per 03 §16.3) with a pre-committed NSD discovery-list relief valve (§5.1 #3, §5.7). (4) **"Shared clock" labelled authored frame-lock** (broadcast-seek scrub; master + drift-corrected muted followers, 03 §8.1) in §5.1 #8 and §5.3 — not a library property. (5) **#15 gains the per-angle crop policy** (tracked angle → `CropTrack`; untracked angles → static face-centred crop as editable keyframes) so no multicam-program segment has undefined 9:16 behaviour. (6) **#16/Beat B name the captioned-playback mechanism** — sidecar rendered live via ExoPlayer `SubtitleConfiguration`, nothing burned in, said on camera. (7) **Transcript deletions tied to 03 §8.2's take-time→program-time map** (N+1 clipped audio items, `AngleCut` re-timing, deletion-seam audio continuity as a binary spike line; SRT emitted in program time) in §5.6/#16. (8) **Narrative gate extended to truthful time** (§8): waits shown in real time or behind labelled elapsed-time chips, with `tiny.en` ≤1× realtime on the owned tablet as a named, measured demo precondition. (9) **Stream-combo spike** noted as typed against named candidate CameraX mechanisms, red only when all are exhausted (§5.7). No locked decision weakened: whisper remains guaranteed/non-cuttable, moat remains market-vacancy, date remains movable, free/paid boundary unchanged.
