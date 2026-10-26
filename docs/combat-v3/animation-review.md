# Combat V3 animation and camera review

## Cinematic targeting, framing and post-attack freeze — 2026-10-12

After the offline GT pass, the owner explicitly requested fixes to Pulverizing Breaker
framing/target alignment and UltimateFinisher's beam aim, plus configurable camera
distance and victim freeze. This expands the earlier completed-finisher exclusion only
for those corrections. The owner confirmed freeze scope: cinematic Strikes and
UltimateFinisher, excluding ordinary punches and standalone DMZ Ki.

- Rush movement now uses body-centered contact anchors, keeps facing the admitted
  victim and follows its height during the melee phase. Collision/loaded-area/world-border
  checks remain enforced; reaching limbs cannot authorize damage outside actual reach.
- Before native Ki release, `CameraAimHelper.store` receives eye-to-target-body aim.
  This prevents the cinematic camera's downward ray from selecting the floor.
- Wide V3 shots fit both complete fighters using live separation, height, aspect and
  FOV, with an elevated terrain alternative before camera collision clamping. Breaker
  527s has explicit launch/orb/throw shots at 130/169/213 ticks. Solid walls can still
  restrict framing; this is not a promise that every obstructed scene contains both actors.
- `v3.cinematicCameraDistanceScale` (0.25–4, default 1) scales cinematic offsets for
  both Strike and UltimateFinisher cameras. Wide-shot fitting sets a safe minimum distance.
- `v3.strikeTargetFreezeTicks` (0–1200, default 80 = 4 seconds) controls post-attack
  victim freeze. Strike freeze begins at actual END, holds the finishing position and
  runs independently of the caster's existing `strikeCameraHoldTicks`. UltimateFinisher
  uses this duration after its existing wave-end landmark. Gravity/AI and owned movement
  are restored; cancellation, disconnect, death and dimension changes clean up holds.
- Finisher waves synchronize a target entity ID through native entity metadata. The
  earlier persistent marker alone was not client metadata. Centered aim is applied before
  native collision checks and after DMZ's position/hand-offset writes, and manual Guidance
  cannot steer that owned wave away from its locked target. Beam knockback no longer moves
  its victim during the held beam phase. Native stock waves retain their own behavior.

Commands: `/xenoset v3.strikeTargetFreezeTicks 140` gives a seven-second victim hold;
`/xenoset v3.cinematicCameraDistanceScale 1.5` scales authored camera distance by 50%.
The V3 config snapshot adds optional trailing fields, preserving legacy defaults and
main-channel registration order. Both endpoints need the updated mod for the synced
finisher-wave metadata; no API or addon registry schema was changed.

Validation is offline. Geometry/config/timeline tests and distribution builds establish
code/resource behavior only. No game process was opened, and rendered target alignment,
multiplayer synchronization, wall-constrained framing and full 1:1 fidelity remain unverified.

## Coverage audit and Meteor Attack continuation — 2026-10-12

The current per-occurrence audit is [choreography-coverage.md](choreography-coverage.md)
and its machine-readable companion. Run `python tools/audit_combat_v3_choreography.py`
after regenerating the catalog. All 302 entries are unaccepted drafts. The starting
audit found 278 bulk archetype occurrences; the offline continuation below reduced
that to 274 after the GT Dragon Claw pass. Named hand clips also reuse motion;
their names alone do not establish occurrence-specific choreography.

The two Meteor Attack occurrences at 163 and 213 seconds previously shared an identical
three-hit, 48-tick clip. `tools/author_v3_meteor_attack_reference.py` replaces them with
separate complete seven-bone rotation/position tracks, contact-aligned bone timestamps,
and timelines of 212 and 200 ticks. Root translation remains zero because the server
owns world travel. Limb translations extend the reach beyond the bind pose.

The 163-second footage has nine direct contacts and a final terrain impact taking the
counter to ten. The 213-second footage has twelve direct contacts and a terrain impact
taking it to thirteen. The last counter event is recorded as terrain collision, not
an invented extra close-range punch. These bone passes end in the existing downward
launch; repeated victim launches/chases and terrain-impact damage remain incomplete.
No claim of full gameplay parity is made.

Source frames and source-video SHA-256 manifests are preserved in
`video-pipeline-302/meteor-attack-163/` and `video-pipeline-302/meteor-attack-213/`.
Dense samples are 12 fps; quick contact sheets are 4 fps. Bone angles, limb identity
where obscured, and contact locations remain retargeting estimates until game comparison.

DMZ 2.1.3 writes head look and arm aim/sway after the authored pose. The existing
`DmzMeleeHeadLookMixin` now gates those X/Y writes for an active registered V3 occurrence.
It reads GeckoLib's `attack_controller`; DMZ's `getCurrentPlayingAnimation()` reads the
movement controller and cannot identify an occurrence played by `MeleeAnimationS2C`.
This preserves authored head/arm rotations for local and remote player models;
stock animations, inactive clips, and the control-release clip retain procedural motion.
The UltimateFinisher implementation and its camera were not edited.

Authoring order: batch2/bulk scripts first, then
`python tools/author_v3_meteor_attack_reference.py`, then
`python tools/gen_combat_v3_techniques.py` and the coverage audit. Rerunning batch2 after
the reference author would restore its old template, so reapply this author last.

All entries remain `reference_timed_unverified`. Fresh render checks do not substitute
for a recorded combat comparison of every pose, contact, and victim trajectory.

The default bulk pipeline now preserves registered hand-authored occurrences with matching
source timestamps, regardless of whether evidence metadata is a string, list, or object.
The 278 bulk evidence references had a spurious `s` before `.jpg`; those references and
the generator were corrected to the actual extracted filenames. No bulk motion was promoted
by repairing its evidence link.

The diagnostic client played both raw clips and captured 14 frames on 2026-10-12.
Ground-level playback clips through the floor during body tilts; these are airborne source
sequences, and bone authoring alone does not supply their world-space staging. Those captures
used the preliminary controller gate and are retained as failed comparison evidence, not
acceptance of the corrected gate or full choreography.

## Offline placeholder replacement — 2026-10-12

Owner direction: continue from the local MP4 without opening Minecraft again. Further
validation is offline unless the owner changes that instruction. UltimateFinisher and
its camera remain outside this continuation.

`tools/author_v3_strike_reference_359_527.py` replaces two hashed bulk placeholders:

- Dragon Claw at 359s: standing preparation, low rush, one rising direct contact,
  central two-hand energy gather, twisting wind-up, raised right-fist thrust, and
  recovery. Two seven-bone clips span 302 ticks, with a continuous held/release boundary.
- Pulverizing Breaker at 527s: seven measured contacts, successive hand/elbow actions,
  rotating airborne kicks with unwrapped body yaw through 360 degrees, upward launch,
  overhead orb posture, forward throw, and recovery. Three seven-bone clips span 275
  ticks. The source energy impact raises the counter beyond the seven physical hits;
  those extra increments are not represented as invented punches.

Both provide complete XYZ rotation and position channels for every bone, including
limb offsets beyond the bind pose. Root translation remains zero. Their timestamps,
body angles, guarded opposite arm, and distinct phases replace the short family template.
The orb occurrence is corrected to `melee_energy` with DMZ `soul_punisher`; its saved
25 Ki cost and 160-tick cooldown are preserved. Native Ki mapping remains an approximation
for source-specific visual effects; the dragon mesh and source-specific orb VFX are absent.

136 overview frames and 60 close-contact frames are preserved under
`video-pipeline-302/strike-reference-359/` and `strike-reference-527/`, with source hashes.
After that batch the audit reported 276 bulk archetype occurrences, 9 named occurrences reusing
the same bone track sequences, and 17 distinct track drafts. Changing a POSE start offset
does not count as unique bone choreography. No occurrence is promoted to `reference_compared`.

Run the new reference author after the bulk scripts, then regenerate the catalog and
coverage report. Its charge/release boundary and clip duration checks are in
`tools/test_placeholder_strike_reference.py`. Full visual parity and complete victim
travel remain unverified; no game run was performed for these two replacements.

Evidence date: 2026-10-08.

The source is the owner's local 720p MP4 in `C:/Users/Admin/Downloads/`:
`Dragon Ball Budokai Tenkaichi 3 - All Attacks And Transformations [Part 1] - simorollo91 simorollo91 (720p).mp4`.
No browser or YouTube playback is required for this work.

## GT Dragon Claw continuation — 2026-10-12

`tools/author_v3_gt_dragon_reference.py` replaces the bulk clips at 564s and 631s
after inspecting their complete sequences at 4 fps and contact/action details at 12 fps.
Both use explicit rotations and translations for all seven existing rig bones. The
shared GT poses correspond to visible shared actions; camera orbits are not invented
body revolutions. No hash jitter supplies supposed uniqueness.

- 564s: crouched preparation, rising strike, recoil, jump/raised fist, chest power-up,
  aerial rush, extended dragon fist, and recovery. One 215-tick clip has contact
  phases at ticks 23 and 154. Ordinary Ki shots after 575s are excluded.
- 631s: upright preparation precedes the GT crouch/rising strike and dragon rush;
  a separate two-hand gather and beam release follows. Three clips span 285 ticks,
  with contact phases at 54 and 185 and beam release at 229. Both clip boundaries
  preserve every bone channel exactly, including the held charge/release boundary.

The 631s occurrence alone is mapped to `melee_energy` and the existing DMZ `kamehameha`
native for its beam. Both occurrences retain 25 Ki cost, 160-tick cooldown, IDs and
compatibility aliases. The source HUD increments during the beam and terrain collision
are not authored as extra melee punches. Dragon visuals and full world-space staging
remain unfinished; the 564s sequence does not substitute an unrelated native projectile
for the missing dragon mesh.

Evidence folders `video-pipeline-302/strike-reference-564/` and `strike-reference-631/`
contain 160 overview samples, 204 dense samples, timestamped sheets and source-hash
manifests. Contact timing has approximately 84 ms sampling uncertainty; numeric bone
angles and occluded limb identity remain estimates. Neither occurrence is accepted as
1:1. No game was launched during this continuation.

The current static audit reports 274 bulk archetypes, 9 shared-motion entries and 19
distinct drafts, with zero accepted comparisons. The new author and catalog generator
rerun byte-stably. Existing clips and the other 300 occurrence records are preserved.

The 564s saved ID includes `solar_flare_551`, an earlier video section name. The
runtime's substring classifier previously treated it as Solar Flare and would send
the native flash animation during a Dragon Claw POSE. `V3SolarFlare` now classifies
the actual attack name first, with ID fallback only when the name is absent. The
saved ID remains stable. A failing regression reproduced the misclassification
before the fix; runtime playback is still unverified under the no-launch direction.

## Current coverage

### Video-backed batch 1 — 2026-03-22

Local Part 1 MP4 frames in \docs/combat-v3/video-batch1/\. Tool: \	ools/author_v3_video_batch1.py\.

Ten occurrences now eference_timed_unverified\ with unique full-body XYZ clips (charge/release where needed): Kamehameha, Penetrate, Kaioken Attack, Solar Flare, Spirit Bomb, Super Energy Wave Volley, Super Explosive Wave, Big Bang Attack, Kienzan, Dodon Ray.

Still not eference_compared\ until fresh client accept. Next batch: next 10 catalog ids after these PTS times.


### Bulk author pass — 2026-03-22

`tools/author_v3_all_strikes_batch.py` authored type-realistic beats plus rush/wide or charge cameras for all 302 occurrences. Status is `authored_gameplay_unverified` (299) plus preserved `reference_timed_unverified` (3). This is not accepted 1:1 video parity; bone angles remain gameplay estimates. Graphify combat corpus outputs live in `graphify-out/`.

### Full-302 video pipeline — 2026-03-22

Owner chose: script all 302, then chunked frame review.

- Tool: `tools/author_v3_video_pipeline_302.py --force` then `--extract`
- Unique hashed seven-bone XYZ clips per occurrence (limb Z overshoot, hip Y capped)
- Duration coverage fixed (laser/charge overrun bug cleared)
- 906 evidence frames in `docs/combat-v3/video-pipeline-302/frames/`
- Review table: `docs/combat-v3/video-pipeline-302/REVIEW_INDEX.md`
- Status remains `reference_timed_unverified` for all 302 until in-game accept
- Hand batch1/2 + Dodoria source re-applied after force rewrite
- Catalog/timeline tests green after restore

### All-chunks review (#1–302) — 2026-03-22

Graphify BFS (`V3NativeKi` / `V3KiStyle.colours` / `PredefinedTechniques`) guided a full-catalog
DMZ counterpart pass. 906 evidence frames remain under `video-pipeline-302/frames/`.

**Catalog health:** 0 `KI_RELEASE`↔`kiTechnique` mismatches. Catalog/lockless tests green.

**DMZ native distribution (post-fix):**
`kamehameha` 58 · `makkanko` 20 · `big_bang` 20 · `ki_barrage` 28 · `death_beam` 11 ·
`final_flash` 10 · `burning_attack` 9 · `masenko` 7 · `sokidan` 7 · `supernova` 6 ·
`galick_gun` 5 · `soul_punisher` 4 · `spiritbomb` 4 · `kienzan` 4 · `None` 109
(melee/grab/hold/radial — radial still fires `final_explosion` at RADIAL beat in DMZ mode).

**Mis-maps fixed this pass:** Scatter Beam / Tri-Beam / Spirit Breaking / Great Scatter → `makkanko`;
Special Soul Cannon → `soul_punisher`; Lethal Destroyer → `death_beam`; Elegant Blaster →
`burning_attack`; Recoome Eraser / Punishing Blaster / Bakuretsu Mahoko → `final_flash`;
Solar Flare stays `None` (blind via `V3SolarFlare`, not KI_RELEASE).

**Chunk spot frames:** 15s Kame · 447s Explosive Wave · 848s Masenko · 910s Father-Son ·
929s Justice Slash · 1193s Galick · 1500s Energy Volley · 1804s Neo Tri-Beam · 2395s Mess-Em-Up ·
3023s Recoome Bomber · 3300s Psychokinesis.

Status remains `reference_timed_unverified` for all 302 until in-game accept.

### Chunk 3 frame review (#61–90, 831s–1137s) — 2026-03-22

Graphify BFS (combat V3 graph) pointed at `V3TechniqueRuntime` / `V3NativeKi` / `kiTechnique` as the
authoritative path for DMZ counterparts + colours — same wiring used for this chunk.

| Start | Technique | Frame notes | DMZ native |
|------:|-----------|-------------|------------|
| 831s | Super Violent Strike | Adult Gohan sword cut (section timing noisy) | `makkanko` |
| 848s | Super Masenko | Gohan overhead yellow charge | `masenko` |
| 910s | Father-Son Kamehameha | Dual charge (Goku+Gohan) blue | `kamehameha` |
| 929s | Justice Slash | Yellow sword slash (was wrongly kame) | `masenko` |
| 1059s | Full Power Energy Barrier | Blue barrier sphere (no blast) | `None` |
| 1090s | Bursting Rush | Melee punch rush | `None` |
| * | Explosive / Super Explosive Wave | radial blast | `final_explosion` |
| * | Explosive Madan / barrages | volley | `ki_barrage` |
| * | Super / Bros / Father-Son Kame | blue beam | `kamehameha` |

Status stays `reference_timed_unverified`. Hand clips unchanged this chunk; ki map regen only.

### Chunk 2 frame review + DMZ ki map — 2026-03-22

Reviewed frames #31–60 (447s–822s): Explosive Wave, Final/Big Bang Kamehameha, Pulverizing Breaker,
Solar Flare, Energy Barrage, Masenko overhead, Meteor Crash, Supreme Impact, Bakuretsu Ranma.

DMZ counterparts (regen via `gen_combat_v3_techniques.py`):
- Big Bang Kamehameha → `big_bang` (was wrongly `kamehameha`)
- Explosive / Super Explosive Wave → `final_explosion` (+ fire on RADIAL)
- Masenko → `masenko`; barrages → `ki_barrage`; Spirit Bomb → `spiritbomb`
- Solar Flare → `taiyoken` (blind still via `V3SolarFlare.applyBlind`)

Also: Ultimate Finisher wave clears DMZ OFFSET_X=0.4 (right-hand miss); Dodoria Head Breaker
SHOVE=`DOWN` using `/xenoset v3.strikeLaunchDistance` (default 20).

### Chunk 1 frame review — 2026-03-22

Reviewed `frame_t015000` / `_p35` / `_p70`, `t026000` / `_p35`, `t044000` / `_p35`, `t069000`.

| Start | Technique | Frame notes | Clip status |
|------:|-----------|-------------|-------------|
| 15s | Kid Goku Kamehameha | Charge crouch → cupped hands at side → forward fire | batch1 charge/release kept |
| 26s | Penetrate! | Right-arm thrust, forward lean, rear foot drive | batch1 penetrate kept |
| 44s | Kaioken Attack | Red aura rush impact / knockaway | batch1 kaioken kept |
| 69s | Solar Flare | Hands-up Taiyoken | batch1 solar kept |

Hand batch1 clips already match these poses; no rewrite this chunk. Continue chunk 2 from REVIEW_INDEX after #30.

Camera fix same day: `v3.strikeCameraHoldTicks` (post-END) and `v3.strikeCinematicCameraHoldTicks` (opening, ki/charge only) are separate again; rush/APPROACH no longer stretches opening (that caused lock-on flicker). Client freezes horizontal camera basis during cinematic.


- Reference inventory: 554 rows, including attacks and non-attack intervals.
- Eligible attack occurrences: 302 separately registered definitions.
- Label-only save compatibility: 144 aliases plus the two prior spelling aliases.
- Occurrences with authored clips awaiting comparison: 3 (two Kamehameha phase clips and one headbutt clip shared by two Dodoria occurrences).
- Finite neutral controller-release clips: 1 (not an attack occurrence).
- Authored occurrence camera timelines awaiting comparison: 1.
- Fresh in-game comparisons accepted: 0.

299 shipped techniques have `animationStatus: archetype_placeholder`; the first kid Goku
Kamehameha has `reference_timed_unverified`; both Dodoria Head Breaker occurrences have
`authored_gameplay_unverified`. Repeated labels
are kept as distinct occurrences; a label does not prove two characters or forms use the same
choreography. Sampled timestamps do not prove exact source PTS or contact timing.

## Camera infrastructure

An optional `camera` array in each technique contains ordered, non-overlapping shots:

```json
{
  "tick": 0,
  "duration": 20,
  "position": [4, 2, -3],
  "look": [0, 0, 0],
  "focus": 0.5,
  "easing": "SMOOTH"
}
```

This is a schema example, not an authored reference shot. Offsets use the attacker-to-target
horizontal frame: right, up, forward. `focus` blends the attacker and victim body centers.
`CUT` changes immediately; `LINEAR` and `SMOOTH` interpolate from the previous shot over the
current shot's duration. First shots start at their authored pose. At most 128 shots are allowed,
with finite offsets bounded to 64 blocks and a timeline bounded to 1200 ticks.

The server sends fighter-session, cast, and target UUIDs together. The client rejects stale casts,
wrong sessions/targets, and states outside CINEMATIC or the approved Dragon Dash follow window. A held energy release pauses the camera at the
same timeline time; release resumes both. Camera interpolation uses tick time and partial ticks,
not accumulated render-frame smoothing. Cancellation, lock loss, death, dimension change,
logout, competing control cameras, and timeout release the prior perspective.

The cinematic movement lease uses the unique cast token. Client input suppression also applies
to unfinished entries with no camera data. This prevents a camera's availability from deciding
whether player input can compete with server cinematic motion.

## Cinematic rush and head contact — 2026-10-08

APPROACH now travels using server-owned velocity under the exact cinematic cast lease. Its
duration selects speed (capped at three blocks per tick), and pending contact beats wait for
arrival. Borders, blocked paths, unloaded destinations and lost borrowed flight abort the cast.
The existing verified DMZ Search Fly helper borrows and restores the learned flight and aura
state. This is gameplay implementation evidence, not a fresh in-game movement comparison.

Physical cinematics, including mixed melee/energy attacks, admit victim control through the
cancellable TechniqueControlEvent before spending ki. One cast owns a victim at a time.
The victim stays pinned until the scripted shove, then a collision-aware twenty-block launch
controls the flight without pinning it back. Player victims use an exact cinematic motion token;
lease loss aborts the caster. Gravity is restored on release and interruption. Pure beam casts
do not acquire victim control. Non-authored physical camera timelines use a wide midpoint shot;
Dragon Dash uses an attacker-focused rush shot and switches to that wide shot after contact.
These fallback shots are not source-authored reference shots.

`tools/author_v3_contact_moves.py` preserves the Kamehameha clips and reproduces a head-first
Dodoria Head Breaker clip and an airborne downward stamp for N+S. Both observed Dodoria IDs
use the durable override: approach, whole-body/head wind-up, one strong head contact and shove.
Head/leg XYZ offsets add reach while arms stay in compact guard; offsets return to zero.
Angles and contact timings are gameplay estimates, not measured BT3 parity. NPC/player control,
travel/contact ordering, side launch distance, camera framing and animation appearance remain
pending a fresh game comparison.

## Next delivery unit

The early kid Goku Kamehameha occurrence at nominal 15 seconds now has a separate seven-bone
charge/release pair, source-timed beats, and rear/right camera framing. The named cast begins at source PTS
16.950267 seconds; the beam flare at 18.918900 maps to tick39. The preceding ordinary charge,
backward reposition, and subsequent forward movement are excluded. `docs/combat-v3/choreography.json`
stores the authored override so catalog regeneration preserves it and refuses stale source starts.
`tools/author_v3_kid_kamehameha.py` reproduces this clip without replacing any existing melee asset.

The charge holds its last frame at KI_HOLD tick36. Releasing admits the windup POSE at36 and
the beam at39, preserving the three-tick hand-push lead-in; holding cannot reveal that POSE early. Cancellation and
end replace the held controller with a finite neutral clip before restoring perspective.
The existing DMZ studio-finish mixin keeps GeckoLib's paused hold controller alive.

Bone angles and camera distances are retargeted estimates. Fingers, child/adult proportions, supporting-foot contact,
held-release animation, projectile duration, and recovery need a fresh
client comparison. The victim is obscured by the blast, so no victim reaction was invented.
This first draft does not satisfy the owner's one-to-one animation requirement yet.

## Arm choreography correction — 2026-10-08

The owner rejected the thrown-back arm in all newly authored combat clips, including kicks.
The narrow correction changes arm vectors in 67 clips from the 68 selected new V3/V4, charged,
chase/dash and cinematic clips. Punches retain a compact non-striking hand; both kick arms remain
forward. Supporting legs, body/root motion, keyframe times and durations are preserved.
Native DragonMineZ and legacy V1/V2 clips remain unchanged. The generator applies the same
policy; a second application changes zero clips.

Independent source review and focused catalog/resource/timeline tests pass. These checks establish
asset values and preservation of non-arm content, not rendered silhouettes or exact BT3 parity.
Fresh gameplay comparison of punches, kicks and charged variants remains pending.

The owner additionally approved limb XYZ translation beyond the torso on 2026-10-08.
Thirteen weighted clips now use authored reach: hook contact moves wider and forward;
low/mid/knee and roundhouse striking legs extend during contact and recover to their bind
position. Charged punch/kick carry the same translated chamber from held charge into release,
extend farther during impact, and return after recovery. Supporting-leg tracks, durations and
compact off-hand guards remain unchanged. These are artistic retargeting estimates, not measured
BT3 bone coordinates or a verified rendered result.

## Strike picker presentation — 2026-10-08

The owner requested removal of duplicate rows from the attack selection list. The302 reference
occurrences retain their distinct IDs and saved/equipped data; the picker groups known owned V3
entries by display name (144 unique names in the bundled catalog). It chooses only from IDs
already visible after DMZ unlock/race checks, preferring compared or authored choreography,
canonical IDs, then earliest source occurrence. Alias-only saves stay selectable. Native and
unrelated addon entries are preserved. The same filtered list drives rendering and click lookup.
Focused tests and independent source review passed; actual screen interaction remains pending.

## Meteor reference pass — 2026-10-09

The local 720p BT3 Part 1 file was inspected around 116, 310, and 691 seconds. The 116-second
Meteor Combination now has a 200-tick seven-bone XYZ clip with 19 contact beats, an inverted
mid-combination pose, and a guarded opposite arm. The two Meteor Crash occurrences are separate:
the 310-second occurrence has a spaced opening and 13 contacts over 232 ticks, while the
691-second occurrence has a faster airborne rush, a sideways fall, and 14 contacts over 204 ticks.
Both use seven-bone XYZ rotation and translation, including limb reach beyond the bind pose.
Root position stays zero because the server already owns world-space travel; the root still
rotates for body lean and turns.
`tools/normalize_v3_occurrence_root.py` applies that rule to the full occurrence resource
after all authoring scripts; it corrected 64 older nonzero root-position frames without
changing limb tracks.

Charged punch and kick were also regenerated from `tools/author_v3_charged_melee_anims.py`:
both arms remain in front of the shoulder, the punch's supporting leg matches its held
pose on release, and root scale pulses were removed. The hold and release now share the
same whole-body pose at their boundary. Rendered appearance still needs a fresh client check.
The authored scripts are `tools/author_v3_meteor_combination_116.py` and
`tools/author_v3_meteor_crash_reference.py`; frame evidence is under
`docs/combat-v3/video-pipeline-302/meteor-*`.

The source video establishes sequence, rough timing, and visible hit counts at this sampling
rate. Bone angles and contact points are artistic retargeting estimates; none of these clips has
been accepted as one-to-one in a fresh game comparison. The other 299 occurrences retain their
previous animation review status.


## 2026-10-12: next 40 offline occurrence drafts and recovery

The exact selected batch is `video-pipeline-302/offline-batch40/manifest.json`; actual source boundaries, clip names and observations are in `offline-batch40/authored-review.json`. `tools/author_v3_offline40_reference.py` reproduces these drafts. The old 19 individual drafts and all unselected rows were preserved. 733/738 are fragments of one Supreme Impact; 778/783 are fragments of one Bakuretsu Ranma. IDs and nominal sourceStartsMs remain stable, with corrected actual animationStartMs in the evidence.

Current static audit: 59 individual drafts, 241 bulk archetypes, 2 shared-motion entries, 302 catalog occurrences, zero accepted reference comparisons. This count does not mean 59 finished 1:1 animations. Instant Kamehameha teleport, complete paired world travel/victim reactions, dragon mesh and occluded Supreme Impact contacts remain unfinished; numeric 3D angles are estimates. Root translation stays server-owned.

Native owned Ki uses the registered ref40 cast/fire prefixes; DMZ's procedural head/arm writes respect both attack_controller and ki_controller. Big Bang Kamehameha occurrences 505/728/751 use a native wave rather than a ball; Ranma uses Masenko after its melee segment. Native free charge timing remains player-controlled.

Recovery uses the existing RED combat_prompt_alert_w120 atlas from tools, 120x32, centered 36 GUI pixels below the crosshair. Only the held victim receives the recovery token/state. Distinct movement/combat key or LMB/RMB presses add four server ticks of progress, rate limited to one accepted press per two ticks; held keyboard auto-repeat does not count. Rush/UltimateFinisher recovery begins after the authored attack/wave ends. Dash recovery begins on accepted approach, uses min(strikeTargetFreezeTicks, dragonDashFollowWindowTicks), and repeat N never resets an active meter. Escape gives ten ticks of dash re-freeze protection. A successful follow N attempts one normal native light hit with normal cooldown/guard/range checks; HUD combo count reads actual positive server damage.

Cinematic cameras retain their cast direction across actor crossings and camera packets, damp position/focus with simulation time, clip once without per-frame elevated alternates, and suppress extra impact pitch shake. Wide framing still depends on unobstructed terrain. Configs: `/xenoset v3.cinematicCameraDistanceScale 1.5` and `/xenoset v3.strikeTargetFreezeTicks 80`. Zero `lockon.lockTimeSeconds` now initializes pilot lock progress at 100% in its request handler; V3 on-foot ray acquisition already has no timed lock charge. Existing explicit unlock, death, unload and dimension-change rules still apply.

Owned finisher waves remain centered on the eye→target ray for server collision. They use the native continuous renderer with a render-time origin/rotation correction; persistent HD segments are bypassed for this wave because their captured tick ray cannot follow that correction. No client/server launched; visual aim, smoothness, HUD placement, multiplayer recovery and attack parity are not runtime verified.
