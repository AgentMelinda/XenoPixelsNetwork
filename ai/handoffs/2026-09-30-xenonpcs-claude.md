# Handoff — Claude helping with XenoNPCs 1.20.1

**Date:** 2026-09-30  
**Repository:** `C:\XenoPixelsNetwork_qwen`  
**Branch:** `1.21.1`  
**HEAD:** `53a025e45a4fecc2aeb1a2d062db550f88bbf571`

## Current state

The task is unfinished. The user reports missing/misaligned speech bubbles, odd NPC movement, repeated ki blasts, missing ki waves, air punching, and failure to leave flight when the player lands, specifically on **1.20.1**. They want behavior consistent with native XenoPixels. They explicitly asked to learn bubble positioning from DragonMineZ ki-sense BP/HP markers. Do not tell them these issues are fixed based on compilation.

The owner originally asked to continue Claude session `f68a257c-007c-4d05-ab17-568a18ef1399`. That identifier is context, not proof that the current agent recovered the entire original session. This handoff reports current filesystem and runtime evidence.

- Main repository has **892 dirty status entries** in the saved snapshot, predominantly pre-existing user work. Do not blanket stage, reset, or clean it.
- Separate Forge export: `C:\XenoPixelsNetwork_qwen\XenoNPCs-1.20.1`, branch `1.20.1`, HEAD `b77ef2b5a2695c53e26d8773a8399e8e7f215e06`. At handoff it has 14 modified tracked Java files, six new Java/test files, and untracked logs/material. Full list is in the evidence directory.
- Separate NeoForge export: `C:\XenoPixelsNetwork_qwen\XenoNPCs`, branch `1.21.1`, HEAD `d5f7bc0a815155bdb29d0507c323de9eb721d190`. No tracked changes at handoff; four untracked build/runtime logs.
- Local remote-tracking comparisons returned `0 0` for each repository against its corresponding `origin` branch. These were local comparisons, not a fresh fetch.
- No `java.exe` or `javaw.exe` process was returned by the final process inspection. The last diagnostic client saved its dimensions at **15:18:35** and its Gradle log ended successfully. Recheck before restarting.
- No new commits, tags, pushes, or release uploads were made during these latest fixes.
- Main tracked DMZ jar SHA-256: `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`; matches `dragonminez_sha256` in main `gradle.properties`. That is the main NeoForge dependency, not the Forge Curse dependency below.

Evidence is preserved under [2026-09-30-xenonpcs-claude-evidence](2026-09-30-xenonpcs-claude-evidence/): full status snapshots, latest diagnostic runtime log, screenshots, temporary probe source/init script copies, and SHA-256/size manifest. Screenshot filenames were reused by successive probes; the preserved images are from the **last trace run**, not the earlier runs.

### Owner corrections that matter

- Earlier "never teleport in combat and no chasing" produced an overly broad stationary combat implementation. The user subsequently objected: "instead of moving he just shooting ki blast every second nonstoply that's not xenopixesl natvie way". Latest implementation **restores pursuit**, keeps combat teleport helpers disabled, and stops approach only within actual melee reach. Do not reintroduce stationary combat from the old plan.
- "Don't wave" was clarified as missing expected greeting animation, not permission to delete greeting assets. Distinguish **greeting waves** from **ki wave attacks**; both have been reported missing at different points.
- Respawn complaint was withdrawn by the user: "nvm he does". Do not report a confirmed respawn failure.
- Alleged ki-blast crash was clarified by the user as the agent's rebuild/restart. Do not invent a confirmed ki-hit crash.
- Initial supplied loading crash was real: `DmzLockOnNpcMixin` targeted nonexistent `lambda$findTargetInFront$4` in the Forge DMZ jar. The existing Forge export before this turn already corrected it to `$3`. Do not attribute a newly verified fix to this handoff.

## Changes

Read `AGENTS.md`, `ai/README.md`, and relevant playbooks. Maintained changes belong in the export tooling; regenerating produces the separate Forge tree:

```powershell
python tools/xenonpcs/export_xenonpcs.py --target 1.20.1 --out XenoNPCs-1.20.1
```

The current work is uncommitted:

1. **`tools/xenonpcs/patches_1_20_1_npc.py`**, imported by `patches_1_20_1.py`: exact Forge-only replacements for native motion/turning, ki selection, social playback readiness, centered bubble anchor, pitched-camera dialogue hit rectangles, and moving `WorldToScreenCache` to the same `AFTER_WEATHER` stage as the bubble renderers. Existing `patches_1_20_1.py` already moves those renderers from `AFTER_LEVEL` to `AFTER_WEATHER`.
2. **`tools/xenonpcs/overlay-1.20.1/java/net/bullettrain/xenonpcs/anim/AnimClipsEvents.java`**: loads studio clip library and technique animation bindings at server start, sends clips on player login, forgets combat state animation on logout. The exporter previously omitted the main mod's lifecycle event entry point. The library classes/assets existed but were not initialized/synchronized through that missing entry point.
3. **`.../compat/npc/NpcCombatMotionPolicy.java`**: native approach remains active outside actual melee range; inside reach it stops navigation/sprint. Disables native combat vanish/chase/backstep/teleport-above through `NpcCombatMoves.specialsAllowed`; saga context also marks dash/vanish unavailable so it selects ordinary run. Bounds turn changes from prior-tick rotation fields. Physical velocity/gravity are not zeroed. Former experimental `NpcStationaryCombat`/`NpcStationaryMeleeGoal` files were removed from this work; they are not current implementation.
4. **`.../client/npc/speech/NpcBubbleAnchor.java`** and **`BubbleBillboardGeometry.java`**: central head anchor and camera-oriented corner math. Current anchor uses bounding height divided by authored hitbox scale, multiplied by visible size, plus bubble height. This formula is unit tested in isolation but has not been validated across all actual size/follow-hitbox combinations.
5. **`NpcBrainKiRotation` patch**: generic fallback previously ignored the supplied readiness/band predicate. It now rotates wave/blast/disk candidates while respecting enabled bands and readiness. Configured technique selection remains ahead of the generic fallback.
6. **Social playback**: retry rather than book a long greeting cooldown when `playClip` fails; wait for synchronized social clips to be cached before consuming their pending playback in Full/Geo renderers.
7. **`tools/xenonpcs/downport_1_20_1.py`** copies Forge overlay test sources. New tests are `BubbleBillboardGeometryTest` and `GenericKiRotationTest`.

No intended public API signatures, packet IDs, or data schemas were changed. No 1.21.1 gameplay code was changed by these latest edits. Review diffs rather than treating that intent as a compatibility audit.

**Temporary diagnostic code still needs removal:** `patches_1_20_1_npc.py` currently inserts a `BUBBLE_TRACE` logger at `entity.tickCount == 20` into `SpeechBubbleRenderer`; the regenerated Forge source contains it too. This was intentionally left for investigation, not release. Remove the maintained insertion and regenerate when done.

## Verified

### Commands and tests

Commands below ran from the Forge export unless noted:

- `python tools/xenonpcs/export_xenonpcs.py --target 1.20.1 --out XenoNPCs-1.20.1` from main root: successive exports succeeded. Logs are under `XenoNPCs-1.20.1/logs/`, including `export-native-motion-20260930.log`, `export-bubble-probe-20260930.log`, and `export-bubble-trace-20260930.log`.
- `./gradlew.bat test --tests '*GenericKiRotationTest' --tests '*BubbleBillboardGeometryTest' --no-daemon`: **BUILD SUCCESSFUL in 47s**, `logs/focused-native-motion-20260930.log`. Current XML results contain **6 tests, 0 failures, 0 errors, 0 skipped** across two files. This is the focused run, not a current full-suite result.
- Earlier `./gradlew.bat build --no-daemon`: **BUILD SUCCESSFUL in 36s**, `logs/build-stationary-20260930.log`. This predates restored pursuit and latest render/cache/trace changes. It does **not** validate the current final source.
- Latest diagnostic `./gradlew.bat runClient --no-daemon --init-script C:/Users/Admin/AppData/Local/Temp/codex-npc-regression-20260930.gradle`: **BUILD SUCCESSFUL in 1m 35s**, `logs/client-bubble-trace-20260930.log`. It compiled and ran the latest traced source with a temporary probe.

### Fresh runtime observations

Diagnostic runs used a copy of the existing save, **`run/saves/Codex NPC Regression 20260930`**. The original `New World` was not the target of this probe. The probe modifies the copy: platform, time, spawned NPCs, player position/game mode. Do not launch it against the owner's original save.

- Studio clips synchronized and greeting `combat.xeno_hi_wave` was queued/drained by Full renderer in fresh logs. Preserved `npc-probe-60.png` visibly shows the raised greeting arm. This proves this Full-mode greeting case, not every social animation/renderer combination.
- **Speech bubble failure is reproduced.** At **15:18:06.645**, `BUBBLE_TRACE` logs NPC ID 471 at `(0,101,0)`, camera `(0,102.59999990463257,4)`, anchor height **2.5**, world view pose approximately identity and a perspective projection. `RUNTIME_PROBE client bubbles` confirms `Centered above the NPC` is present with a 600-tick lifetime. Screenshots 60, 230, and 290 show the NPC/name but **no expected speech bubble**. The packet was sent, queued, and reached the renderer. The missing visual is unresolved.
- At **15:18:11.504**, a directly dispatched generic `KiWaveEntity` had `age=20`, `firing=true`, yaw `0`, pitch `-17.822378`. Earlier ages 5/10/15 were charging. This proves a server wave entity transitions into firing after a direct dispatcher call; it does **not** prove the default combat brain autonomously selects waves or that the visible beam accurately hits a distant airborne player.
- At **15:18:19.994**, live combat helper calls returned `vanish=false chase=false backstep=false` for the native fighter with its target.
- The fighter started 12 blocks away. At **15:18:20.497** distance was **5.693**, at **15:18:20.997** **3.393**, at **15:18:21.493** **0.419**, then about **0.967** with later zero steps while in reach. This proves ground approach occurred in this scenario. Position was sampled every ten ticks, so it does not establish a maximum per-tick displacement or prove absence of every possible teleport route.
- The latest bedrock-floor probe kept fighter Y at 101 during ground pursuit. Earlier stone-floor probe fell after attacks destroyed the floor; do not call that a proven flight/teleport bug.

### Exact dependency/source evidence

Forge DMZ inspected with `javap`:

`C:/Users/Admin/.gradle/caches/forge_gradle/deobf_dependencies/curse/maven/dragonminez-1136088/8469416_mapped_parchment_2023.09.03-1.20.1/dragonminez-1136088-8469416_mapped_parchment_2023.09.03-1.20.1.jar`

- `com.dragonminez.client.events.KiSenseEvent` has `onRenderNameTag(net.minecraftforge.client.event.RenderNameTagEvent)` and translates above the entity, then uses dispatcher camera orientation. Tracked decompiled references are NeoForge; the Forge method signature was checked against the exact jar.
- Exact Forge 47.4.10 mapped sources jar was inspected with Python `ZipFile`, without modifying caches. `GameRenderer` dispatches `AFTER_LEVEL` with its separate `posestack`; `LevelRenderer` dispatches `AFTER_WEATHER` with `pPoseStack`. Simply swapping stages was insufficient: the current bubble still does not draw visibly.
- Exact MC 1.20.1 `Camera.setRotation` uses `rotationYXZ(-yawRadians, pitchRadians, 0)`. See the hypothesis below.

### Existing local artifacts — stale, not final

These jars currently exist in `XenoNPCs-1.20.1/build/libs`; they came from the earlier full build and **do not contain all current fixes**:

| File | Bytes | SHA-256 |
|---|---:|---|
| `xenonpcs-0.0.1-1.20.1-all.jar` | 13543059 | `3978c51f4b8a57c602016a42d25f4637b95f64e2097a27b0c2e136768e7d40f5` |
| `xenonpcs-0.0.1-1.20.1.jar` | 13359936 | `9fea1c30dedf592602b850fd5bc8e64398e111a8271168fd24445328ffe807d8` |
| `xenonpcs-0.0.1-1.20.1-sources.jar` | 12119904 | `766177b4b3146a5a47be77508da12622607b9ed585a426518fb95ad0cc0f5a1a` |

These three existing jars have zero `devprobe/` entries. However, the latest diagnostic compile added `devprobe.NpcRuntimeProbe` to build classes via the temporary init script. A new jar task could package it. **Final distribution must be rebuilt without that init script and checked for zero `devprobe/` entries.**

## Not verified

- Bubble visibility, alignment while rotating camera/moving, dialogue option click alignment, custom NPC sizes/hitboxes, third-party NPC renderers.
- Natural-feeling movement, no air punches outside melee, flight-to-ground transition in retaliation mode, pursuit across ledges, per-tick movement smoothness.
- Autonomous default V9 ki-wave selection versus repeated blast selection, ki-wave/laser hit accuracy at 10–20 blocks airborne, cooldown balance.
- Current full test suite/distribution build, dedicated server loading of the latest changes, multiplayer behavior.
- No claim that the current published release contains these changes.

### Concrete next hypothesis, NOT a fix

`SpeechBubbleRenderer` and `DialogueBubbleRenderer` currently scale their billboard as `(BUBBLE_SCALE, -BUBBLE_SCALE, BUBBLE_SCALE)`. Vanilla nameplates and DMZ BP/HP markers use **negative X and negative Y**. With the exact MC camera quaternion, the current quad may face away from the camera and be culled; text orientation may also be affected. This is the strongest next investigation lead from the source comparison, but **no negative-X fix has been implemented or run**. Do not describe culling as the established root cause yet.

If testing negative X, keep drawing and `BubbleBillboardGeometry.offset`/dialogue hit projection consistent, and add a meaningful orientation/front-facing regression check. Verify with fresh screenshots from multiple camera directions/pitch, not just compile success. Do not switch to a name-tag subscriber blindly: native Full-mode `XenoNpcRenderer` renders its own model/nameplate and may bypass the ordinary `EntityRenderer.render` name-tag event route.

## Next steps

1. Inspect preserved evidence and current diffs. Resolve bubble rendering first from the negative-X/culling hypothesis or further trace, using the exact 1.20.1 sources/jars.
2. Use the isolated copied save for fresh regression runs. Extend the probe for airborne target movement and landing, actual autonomous attack selection and distant wave aim. Do not infer those outcomes from the direct wave call.
3. Remove `BUBBLE_TRACE` insertion before final export. Build/test without the temporary probe init script; check all final jars for probe entries, required clips/assets, mixins and nested runtime dependency. Restore settings changed by the probe as appropriate. `pauseOnLostFocus` was temporarily persisted false; **this handoff restored it to its known pre-probe true value** in Forge `run/options.txt`.
4. Keep the owner's heavily dirty main repository intact. If committing the separate Forge repository under the earlier explicit release authorization, stage reviewed paths individually and inspect cached diff/checks. Do not stage untracked logs or diagnostic probes.
5. Existing release workflow in `XenoNPCs/.github/workflows/release.yml` pins Forge commit **b77ef2b5a2695c53e26d8773a8399e8e7f215e06**. Update that pin and its maintained template (`tools/xenonpcs/overlay/project/.github/workflows/release.yml`) only after the correct Forge changes are committed. Do not rewrite the 1.21.1 gameplay implementation just to publish Forge fixes.
6. Earlier context records a published `v0.0.1` with both versions at https://github.com/AgentMelinda/XenoNPCs/releases/tag/v0.0.1 and successful CI run `36702730032`. Remote state was **not rechecked in this handoff**. Verify before modifying it. We held further publication because current gameplay/rendering issues remain unresolved. Keep release notes explicit about unverified scenarios.

The requested six missing userdata skills were installed earlier into `C:/Users/Admin/.codex/skills`: xeno-clarifier, xeno-optimizer, xeno-researcher, xeno-rewrite, xeno-tps-optimizer, xenopixels-addon-development. Existing skill directories were preserved and copied files hash checked. There is no need to repeat installation for this bug.
