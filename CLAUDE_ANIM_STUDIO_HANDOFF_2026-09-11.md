# Handoff — Xeno Anim Studio: two passes

**Date:** 2026-09-11
**Repository:** `C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen`
**Branch:** `1.21.1`
**HEAD:** `3fa456a20a43c56600a743e5cab332534af6a773` ("Record developer API completion handoff")

Nothing was committed, tagged, or pushed. The work sits in the dirty tree on top of `3fa456a`,
alongside the Form Studio / `/stack` / HUD / party work from earlier sessions, which was not touched.
`ModNetwork` is unchanged and no packet was added to it. No process was left running.

---

## Pass 1 — the KEY button

`CLAUDE_GROK_SESSION_HANDOFF_2026-09-11.md` §10 shipped the animator and listed its playback as
unverified. It was broken at the model level: **KEY could only ever write at tick 0.**

1. `AnimTimeline.seek` clamped to `lengthTicks()`, which was the last key — 0 on an empty clip. Every
   scrub funnelled through it, so the playhead could never leave 0 and the timeline could never grow
   past a length only keys could create.
2. `setKey()` ended with `bind(clip)`, and `bind` rewinds, so keying threw the user back to 0.
3. `addKey` always appended; re-keying a tick left two keys, and the export wrote both under the same
   time string so the later silently won.
4. `poseAt` lerped a bone's `null` against `null` and got identity, so keying one arm flattened every
   bone that key did not mention.

Fixed, plus position/scale channels, per-key easing, onion skin, undo/redo, LOAD, per-bone keying,
keyboard shortcuts, an in-game HELP panel, and combat binding through `XenoStudioClipCache` +
`StudioClipBindings`.

## Pass 2 — the rest of the limitations

### The camera fault the user reported

The studio rendered the local player with `renderEntityInInventoryFollowsMouse`. Confirmed against
the decompiled 1.21.1 `InventoryScreen`: it computes `f2 = atan((centreX - mouseX)/40)` and writes
`yBodyRot = 180 + f2*20` against `yRot = 180 + f2*40`. Over a viewport hundreds of pixels wide the
`atan` saturates, giving ±63° of yaw with the body at half the head's angle — the twisted model in
the user's screenshot. And it restores only five of the eight rotation fields: `yBodyRotO`, `yRotO`
and `xRotO` were left modified **on the real local player**, which is the intermittent "camera gets
fucked".

`client/screen/StudioViewport.java` replaces it with an explicit orbit camera (drag / wheel /
shift-drag, `F` re-frame, `R` reset), puts the pitch in the camera quaternion instead of the entity,
keeps body and head yaw equal, and restores all eight fields in a `finally`.

### Clip model rewritten

`XenoAnimClip` is now a track per bone and four channels per track (rotation, position, scale,
visibility), each with its own key times in seconds — the shape the file format already had. Two
bones, or two channels of one bone, can be keyed at genuinely different times, and `SNAP` off lets a
key land between ticks. Old shared-tick files load unchanged; they are simply clips whose channels
share times. The exporter got *simpler*, because each channel now writes its own times instead of
inventing values it was never given.

### Everything else on the limitations list

- **Bone visibility** — a fourth channel in an `xeno:visibility` sidecar, applied with
  `GeoBone.setHidden`.
- **Sound / particle / instruction keyframes** — authored, exported in GeckoLib's own
  `sound_effects` / `particle_effects` / `timeline` blocks, and fired by `AnimEventPlayer` on our own
  playback paths. New `api/event/AnimInstructionEvent`.
- **Onion skin** — both ghosts are drawn, a scrim of the viewport background is laid over them, and
  the live pose goes on top. `RenderSystem.setShaderColor` does not reliably reach GeckoLib's render
  types, so compositing is what actually works; the previous code guessed.
- **Full-pose recording** — `DmzStudioPoseMixin` samples every bone at `setCustomAnimations` RETURN
  into `StudioBoneSampler`, and the recorder keys that. Falls back to the old three values when there
  is no model to sample.
- **Preview parity** — `PV POSE` / `PV BAKED`. BAKED saves a scratch clip, bakes it, and plays it
  through `IPlayerAnimatable.dragonminez$playMeleeAnimation`, the path a bound move uses. It disables
  the pose buffer and stops the screen pausing the world, or there would be nothing to watch.

### Blending and motion helpers (the user's follow-ups)

- `StudioPoseBuffer` has a **weight**; the mixin lerps from DragonMineZ's posed value instead of
  overwriting. Clips carry `blendInTicks` / `blendOutTicks` (3 each) and stopping ramps out. **At
  weight 1 the mixin is bit-for-bit the old behaviour.**
- `AnimMotionOps` — `SETTLE` (eased return to rest from the live pose), `SMOOTH` (three-point
  average, ends pinned), `BRIDGE` (bake a curve into real keys), `THIN` (Ramer–Douglas–Peucker).
- `AnimOscillator` — continuous waves per bone/channel/axis, live during playback or baked into keys
  so they survive into a bound move.

### Editing existing clips and BT3 animations

`SRC` switches LOAD between saved clips, the 106 shipped `combat.xeno_*` clips, and DragonMineZ's own
file. Both file sources go through the resource manager, so pack overrides are what you open — which
is why there is no separate "baked" source: `GeckoLibCache` holds compiled keyframes whose values may
be molang expressions, while the file has the authored constants. `EXPORT` (development runs only,
when `src/main/resources/...` is reachable from the run directory) merges an edit back into the
shipped file, backing it up to `config/xenopixelsmod-anims/.source-backups/` first and writing
through a temp file and an atomic move.

### Scripting API and the server clip library

- `api/anim/XenoAnimApi` — `playClip`, `stopClip`, `listClips`, `isClipAvailable`, `canPlay`.
- `XenoPixels.playClip(npc, name[, speed])` and friends on both NPC script objects, delegating to it.
  Script API `VERSION` is now `"19"`.
- `XenoClipLibrary` + `AnimClipsNetwork` (channel `xenopixelsmod:anim_clips`, protocol `"1"`,
  modelled on `HudPartsNetwork`) + `/xenoanim global push|clear|list`, permission
  `xenopixelsmod.xenoanim.global`. Clients store the server's clips in a `server/` subfolder and
  re-bake; server clips win a name clash. Caps: 64 clips, 256 KB each, 2 MB total.
- `Bt3AnimationCatalog` gained a runtime name registry so library clips pass `isPlayable`. The
  shipped table is untouched and stays authoritative.

**`XenoPixelsApi.API_VERSION` was deliberately NOT bumped.** The plan said to, but that field's own
javadoc says it moves only for changes that could break a compiled addon, and these are purely
additive. Addons detect the `XenoAnimApi` class instead, which is the pattern `XenoPixelsApi` already
tells them to use.

---

## Bytecode facts established (javap, pinned jars)

- `GeoBone` declares `setRot*`, `setPos*`, `setScale*`, `setHidden` and matching getters.
- `FileLoader.loadAnimationsFile` is `KeyFramesAdapter.GEO_GSON.fromJson(GsonHelper.getAsJsonObject(
  root, "animations"), BakedAnimations.class)` — the call `XenoStudioClipCache` makes.
- `Animation.length()` is in **ticks**: the adapter multiplies `animation_length` by 20.
- `BakedAnimationsAdapter` reads `animation_length`, `loop`, `bones`, and per keyframe `vector`,
  `pre`, `post`, `easing`, `easingArgs`, `lerp_mode`. `KeyFramesAdapter` reads `sound_effects`,
  `particle_effects` (`effect`, `locator`, `pre_effect_script`) and `timeline`.
- `EasingType.EASING_TYPES` ids are lowercase, no separators: `linear`, `step`, `easeinsine`,
  `easeinoutcubic`, `catmullrom`, …
- `InventoryScreen` declares `renderEntityInInventory(GuiGraphics, float, float, float, Vector3f,
  Quaternionf, Quaternionf, LivingEntity)` in 1.21.1.
- `MathValue.get()` / `isMutable()` exist — the basis for the note about baked animations.
- `bt3_combat.animation.json` uses exactly `root, waist, head, right_arm, left_arm, right_leg,
  left_leg` across all 106 clips, matching `XenoRig.COMBAT`.

## Verified

From the repository root, 2026-09-11:

- `./gradlew compileJava compileTestJava -PofflineMcMeta --offline` — BUILD SUCCESSFUL.
- `./gradlew test -PofflineMcMeta --offline` — BUILD SUCCESSFUL. From a cleaned
  `build/test-results/test/`: **127 test classes, 728 tests, 0 failures, 0 errors, 0 skipped**
  (the baseline before this work was 121 / 670).
- `./gradlew build jarJar serverJar buildApiExampleAddon -PofflineMcMeta --offline` —
  BUILD SUCCESSFUL; the API example still compiles against the published tree, which is what says
  the API additions are source-compatible.
  - `xenopixelsmod-Server-0.3.5-1.21.1.jar` — 9,425,701 bytes, SHA-256
    `e88787f621f220221a5e1e55cf25d2beda629aead43d03fb2107c2aaa84362a6`, **0 entries under
    `META-INF/jarjar/`**.
  - `xenopixelsmod-0.3.5-1.21.1.jar` — 33,572,929 bytes, SHA-256
    `5742e3589b8f95f30eb50dfd09ccf1199321f60e31bd9065cc16754cd4a9e514` (3 jarjar entries, expected).

New test classes: `AnimChannelTest`, `AnimBoneTrackTest`, `AnimTimelineTest`, `AnimMotionOpsTest`,
`AnimOscillatorTest`, `AnimPoseClipboardTest`, `AnimUndoStackTest`, `StudioClipBindingsTest`,
`XenoClipLibraryTest`, `XenoAnimApiTest`, plus a rewritten `XenoAnimClipTest`.
`AnimTimelineTest.playheadMovesOnAnEmptyClip` and
`XenoAnimClipTest.twoBonesCanBeKeyedAtDifferentTimes` are the regression locks for the two defects
this work existed to remove.

## Not verified

**No client was launched in either pass.** Everything below is manual pending.

- The camera fix itself. Orbit, zoom and pan, then leave the studio and confirm the player's own
  yaw and pitch are untouched — this is the reported fault, so check it first.
- The screenshot the user supplied is from a build predating pass 1. Get them onto a build with this
  work in it before chasing anything further in game.
- Two bones keyed at different sub-tick times, and the four per-channel timeline rows.
- POS / SCALE / visibility visibly moving the DMZ model. This is `DmzStudioPoseMixin`; check
  `run/logs/latest.log` for a mixin apply failure before trusting it.
- `DmzCombatAnimationNamesAccessor` applying. It is a static `@Accessor` on the same private static
  field `DmzCombatAnimationRegistryMixin` already shadows, following the `DmzLockOnAccessor` pattern
  already used against DragonMineZ — but it has never been loaded in a run.
- Blend in/out easing rather than snapping, on play, stop and studio close.
- Sound and particle keyframes firing on `/xenoanim play` and staying silent through a bound move.
- Full-pose `REC`, and `THIN` on the result.
- `PV BAKED` matching what a bound move renders.
- `SRC` loading a shipped BT3 clip, and `EXPORT` writing it back with a backup, in a dev run.
- `/xenoanim global push all`, a second client joining, and an NPC playing that clip for a player who
  never had the file.
- `XenoPixels.playClip` from an NPC script.

## Next steps

1. `./gradlew runClient` — **without** `-PofflineMcMeta`; that flag's vendored Minecraft metadata
   carries no LWJGL natives and dies in Sodium's probe (`build.gradle:109`, `ai/validation.md`).
2. Work the unverified list against a fresh `run/logs/latest.log`, camera first.
3. Commit the reviewed paths explicitly. Never `git add -A`. `client/anim/`, `anim/`, `api/anim/`,
   `client/screen/StudioViewport.java`, `docs/xeno-anim-studio.md` and the new tests are untracked
   and git cannot recover them if they are lost.
