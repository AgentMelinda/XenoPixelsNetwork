# Handoff — Hakai body-fade root cause, Anim Studio POSE/BAKED parity, Grok cleanup

**Date:** 2026-09-12  
**Repository:** C:\XenoPixelsNetwork_qwen (git worktree of C:/Users/Admin/.grok/worktrees/dragonminez/XenoPixelsNetwork_qwen)  
**Branch:** 1.21.1 (ahead of origin/1.21.1 by 8)  
**HEAD:** 3fa456a20a43c56600a743e5cab332534af6a773 (unchanged by this session; nothing committed)

## Current state

- Dirty tree: 259 paths in `git status --short` (was 259 at session start too — the 11 paths
  below were already modified/untracked from the Grok session and were edited in place, plus 2 new
  files). Everything else in the tree belongs to the user / earlier sessions and was not touched.
- Paths touched this session:
  - `src/main/java/net/bullettrain/xenopixelsmod/compat/npc/NpcDissolve.java` (untracked, rewritten)
  - `src/main/java/net/bullettrain/xenopixelsmod/client/combat/HakaiFade.java` (untracked, edited)
  - `src/main/java/net/bullettrain/xenopixelsmod/client/combat/AlphaMultiBufferSource.java` (M, dead code removed)
  - `src/test/java/net/bullettrain/xenopixelsmod/client/combat/AlphaMultiBufferSourceTest.java` (M, rewritten)
  - `src/main/java/net/bullettrain/xenopixelsmod/client/anim/studio/StudioBoneSpace.java` (new)
  - `src/test/java/net/bullettrain/xenopixelsmod/client/anim/studio/StudioBoneSpaceTest.java` (new)
  - `src/main/java/net/bullettrain/xenopixelsmod/mixin/client/DmzStudioPoseMixin.java` (untracked, edited)
  - `src/main/java/net/bullettrain/xenopixelsmod/client/screen/XenoAnimStudioScreen.java` (untracked, comment only)
  - `docs/hakai-body-fade.md`, `docs/xeno-anim-studio.md` (untracked, edited)
  - this file
- `gradle.properties` still carries Grok's uncommitted `mod_version` 0.3.5 -> 0.3.6 bump. The user
  chose: no commit, tag, or push in this session.
- No game client or server was launched. No process holds old classes from this workspace.
- `libs/dragonminez-2.1.3.jar` SHA-256 5A6E33EF5B992E64FCCCCAED895B105318B2CE1AB8039D8193107D40D203B581
  (unchanged; matches the copy in `build/libs`).

## Changes

### Hakai fade — root cause (server never told clients about the effect)

`javap` against `build\moddev\artifacts\neoforge-21.1.248-merged.jar`:
`LivingEntity.onEffectAdded/onEffectUpdated/onEffectRemoved` only call `sendEffectToPassengers`;
only `ServerPlayer` sends `ClientboundUpdateMobEffectPacket`/`ClientboundRemoveMobEffectPacket`,
and only to itself; `ServerEntity.sendPairingData` sends effects once at track start. So the
`hakai_dissolve` amplifier `NpcDissolve` wrote on a mob/NPC target never existed on the caster's
client, `HakaiFade.dissolving()` was false, and no render wrap was applied. Grok's whole
vertex-format / `OutlineBufferSource` investigation was downstream of this.

Also: `LivingEntity.addEffect` -> `MobEffectInstance.update` never lowers an amplifier, so the
restore ramp (`HakaiChannelSystem.tickRestore` -> `NpcDissolve.apply` with falling progress) could
not fade a body back in.

`NpcDissolve` now uses `target.forceAddEffect(instance, null)` and broadcasts
`new ClientboundUpdateMobEffectPacket(target.getId(), instance, false)` via
`((ServerLevel) level).getChunkSource().broadcast(target, packet)` (excludes the entity itself,
so a player target is not double-sent); `clear` broadcasts
`new ClientboundRemoveMobEffectPacket(target.getId(), ModEffects.HAKAI_DISSOLVE)` when
`removeEffect` returned true. All signatures verified with `javap` on the exact jar.

`HakaiFade` treats a client instance with `!isInfiniteDuration() && getDuration() <= 0` as gone
(client `tickEffects` never removes expired instances).

No network protocol change: these are vanilla packets, `ModNetwork` untouched.

### Anim Studio — PV POSE vs PV BAKED mismatch

`javap` against `geckolib-neoforge-1.21.1-4.9.2.jar`
(`~/.gradle/caches/modules-2/files-2.1/software.bernie.geckolib/geckolib-neoforge-1.21.1/4.9.2/`):
`BakedAnimationsAdapter.buildKeyframeStack` loads rotation X/Y as `toRadians(-v)` and Z as
`toRadians(v)`; `AnimationProcessor.tickAnimation` writes `setRot*(value + initialSnapshot.getRot*())`
and writes position/scale absolute; `GeoModel.handleAnimations` calls `tickAnimation` then
`setCustomAnimations`; `RenderUtil.translateMatrixToBone` translates `(-posX/16, posY/16, posZ/16)`.

`DmzStudioPoseMixin` wrote `setRotX(toRadians(pose.rotX))` — no sign flip, no snapshot offset —
while `XenoAnimClip.toGeckoJson` exports the same numbers raw. X/Y rotations were therefore
mirrored between PV POSE and PV BAKED (and a shipped clip opened via SRC looked mirrored against
in-game playback). New pure helper `StudioBoneSpace` (`toBoneRadians` / `toStudioDegrees`) is used
for both applying and REC sampling; sampler now compares position/scale against
`BoneSnapshot.getOffset*/getScale*` instead of 0/1. Position units were already consistent
(pixels); only a misleading "blocks" comment in `XenoAnimStudioScreen` was corrected.

### Grok cleanup

`AlphaMultiBufferSource`: removed `isOutlineGenerator`, `bodyConsumer`, `outlineConsumer`,
`dualField`, `supportsAlphaFade` (test-only leftovers). Test rewritten; Grok's half-added
`FastColor`/`assertEquals` imports are now used by four `scalePacked` tests.

## Verified

- `.\gradlew.bat test --tests "*AlphaMultiBufferSource*" --tests "*HakaiFade*" --tests "*StudioBoneSpace*" --tests "*NpcDissolve*" --offline`
  -> exit 0; result XML 2026-09-12 14:18:55: StudioBoneSpaceTest 4/0, AlphaMultiBufferSourceTest 7/0,
  HakaiFadeTest 13/0, NpcDissolveTest 2/0 (tests/failures).
- `.\gradlew.bat test --offline` -> BUILD SUCCESSFUL in 44s; 139 suites, 836 tests, 0 failures,
  0 errors (result XML 14:19:56).
- `.\gradlew.bat build jarJar serverJar -PofflineMcMeta --offline` -> BUILD SUCCESSFUL in 1m 3s.
  Artifacts (build/libs, 2026-09-12 14:20):
  - `xenopixelsmod-0.3.6-1.21.1.jar` 36,259,490 bytes
    SHA-256 526C4D955CECB12BED9152E03A6ACDBAC2A59BAE3A47FDC5AFC2A5040B6658D5 — 3 `META-INF/jarjar/` entries (expected for the client/universal jar)
  - `xenopixelsmod-Server-0.3.6-1.21.1.jar` 12,112,263 bytes
    SHA-256 975E8FEC366F6804F12D61D976B9B05C23F68E134E7806E9C076AAAFECC65058 — **0** `META-INF/jarjar/` entries
  - `xenopixelsmod-0.3.6-1.21.1-sources.jar` 10,863,959 bytes
    SHA-256 A929AD9C28D14503AAFF9E30FBACA30E8CB70E3464B9A83A7D36D2F95B2DFD1B
  - Both 0.3.6 binary jars contain `NpcDissolve.class`, `StudioBoneSpace.class`, `DmzStudioPoseMixin.class`.
- API signatures used (all `javap` on the exact jars, 2026-09-12): see Changes above.

## Not verified

- Hakai fade rendering in game (mob, player, FULL/OVERLAY CustomNPC), restore ramp, behaviour with
  `hakaiTargetGlow` on (the `OutlineBufferSource` R6 path). No client was run.
- That the two `todays_crashreports/*Not building!*` crashes (from the packaged 0.3.5 jar in the
  user's modpack) no longer occur with the R6 `AlphaMultiBufferSource`; the code path that caused
  them is gone in source but no run confirms it.
- PV POSE == PV BAKED visually, and that shipped BT3 clips look right in POSE after the sign fix.
- Whether `DmzStudioPoseMixin` still applies cleanly at runtime (compiles; not launched).
- Pre-existing, unrelated: `compat.mynpcs.ContainerNpcInvCuriosMixin` @Shadow `addSlot` not located
  (reported in the 2026-09-12 neon-menus handoff; not touched).

## Next steps

1. Drop `build/libs/xenopixelsmod-0.3.6-1.21.1.jar` into the modpack and test: `/hakai` on a
   vanilla mob (should fade with charge and fade back over `hakaiFadeRestoreTicks` when
   interrupted), then with `hakaiTargetGlow` on/off; open the Anim Studio, load a shipped BT3 clip
   via SRC, compare PV POSE and PV BAKED.
2. If glow-on still crashes with `Not building!`, attach the fresh crash report — the stack will
   now show `fadeOutlineJoin`, not the old `getBuffer` path.
3. Commit/tag/push remain the user's call. Suggested focused staging (no `git add -A`):
   the 11 paths listed under "Current state" plus `gradle.properties`, then `git tag v0.3.6`.
4. Do not treat this file's "Verified" section as runtime proof; only a fresh `latest.log` or
   observed behaviour upgrades the items under "Not verified".
