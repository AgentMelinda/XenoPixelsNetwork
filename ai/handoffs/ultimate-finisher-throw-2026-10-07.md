# Handoff — UltimateFinisher held Xeno NPC throw

**Date:** 2026-10-07  
**Repository:** C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen  
**Branch:** 1.21.1  
**HEAD:** e011e7bec8061d8c2228cfbdc63ee57468749d0d

## Current state

- User reported no throw after UltimateFinisher's grab and identified the victim as a XenoPixels NPC. Conversation is Hebrew; source comments are English.
- HEAD/upstream divergence 0/0. Extensive existing dirty work remains preserved. No staging, commits, tags or pushes. Full inventory: `ultimate-finisher-throw-2026-10-07-status-after.txt`.
- This turn changed `combat/v2/UltimateFinisher.java`, added `combat/v2/UltimateFinisherThrow.java`, extended `gametest/UltimateFinisherGameTests.java`, updated `docs/combat-v2.md` and added these evidence files. Combat directories and the GameTest file were already untracked work from earlier turns.
- Owned validation/build JVMs exited. Remaining Java PIDs: 13976, 32916, 61244 (started 07:54–07:55), 47464 and 72000 (started 01:48–01:51). User processes were not stopped and may hold old classes. The user client log starts 07:55:15 and records finisher casts at 07:59–08:00.
- DMZ SHA-256 unchanged: `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`, matching gradle.properties.
- This handoff supersedes the prior Kamehameha handoff's throw limitation and artifact hashes.

## Changes

- Root cause: holdTarget sets Mob NoAI. Exact pinned Minecraft/NeoForge source shows `Mob.isEffectiveAi()` is false under NoAI; `Entity.isControlledByLocalInstance()` delegates to it, and `LivingEntity.travel()` requires local control. Velocity alone cannot move the held mob.
- The extracted production throw helper retains the twenty-step 15-block/four-block arc, protection gate and swept collision check. For NoAI mobs it consumes velocity with `Entity.move(MoverType.SELF, velocity)` and clears the remaining impulse, preventing double movement. No mob teleport or AI reactivation during the throw. CombatKnockback preserves grace/sync flags.
- Existing controller release/cancellation still restores borrowed AI/gravity. Player throw movement and unrelated knockback code were not changed.
- New real server tests cover actual held Pig/Xeno NPC positions, ascent/endpoint, preserved NoAI, no queued movement, wall obstruction and NPC protection. Native beam regressions remain.
- No published API, channel ordinal/protocol, AddonNetwork registry, mixin/build configuration or dependency changes.

## Verified

- Initial fixture used `spawnWithNoFreeWill`, which removes goals but does not set NoAI. Its passing Pig result was not proof for the hold. An intermediate server loaded that older fixture, confirmed by javap. The final fixture explicitly sets NoAI and no gravity.
- Red log: `ultimate-finisher-throw-2026-10-07-red.log`. Held Pig failed at 08:08:46.348 and held Xeno NPC at 08:08:46.997, both with zero displacement. A native beam push assertion also failed in that batch; cause was not established. Beam production code remained unchanged and the subsequent fresh final batch passed it.
- Focused command after fix: `.\gradlew.bat -g C:/Users/Admin/.gradle test --tests '*UltimateFinisher*Test' -PofflineMcMeta`, exit 0, BUILD SUCCESSFUL in 43s.
- Full command: `.\gradlew.bat -g C:/Users/Admin/.gradle test build jarJar serverJar buildApiExampleAddon -PofflineMcMeta`, exit 0, BUILD SUCCESSFUL in 1m 56s; example addon exit 0. XML: 3,381 tests, 517 suites, zero failures/errors/skips.
- Audit: `node C:/Users/Admin/.agents/skills/xenopixels-addon-development/scripts/audit-project.mjs .`, exit 0, zero failures/six existing integration-reflection advisories. `git diff --check` exit 0, existing LF/CRLF warnings.
- Fresh final runtime: `.\gradlew.bat -g C:/Users/Admin/.gradle --init-script ai/handoffs/ultimate-finisher-2026-10-07-server.init.gradle runServer -PofflineMcMeta`. ModLauncher 08:10:55.438; server Done 08:11:23.544.
- `ai/handoffs/ultimate-finisher-2026-10-07-rcon.ps1 -Command 'test runall'` ran eight tests. Explicit passes: accepted/refused beam push 08:11:50.387, NPC protection 08:11:50.542, wall obstruction 08:11:50.551, held Pig displacement `(0,0,15)` 08:11:51.385, held native Xeno NPC displacement `(0,0,15)` 08:11:51.590, charged/fired native WAVE 08:11:52.431. Ascent exceeds 3.5 blocks, endpoint within 0.1 of 15 horizontal blocks and original height, NoAI preserved and final velocity zero. No final LogTestReporter failures.
- Evidence: `ultimate-finisher-throw-2026-10-07-green.log`. Graceful RCON `-Command 'stop'` saved all dimensions; listener stopped 08:12:11.248. runServer exit 0, BUILD SUCCESSFUL in 1m 30s. Generated isolated server properties restored to `enable-rcon=false` and empty password.
- Client jar: `build/libs/xenopixelsmod-0.5.11-1.21.1.jar`, 69,529,639 bytes, SHA-256 `ee92dd724c4ad63ca5ac75564d25787801770b9d053e60f28c953555324e055e`.
- Server jar: `build/libs/xenopixelsmod-Server-0.5.11-1.21.1.jar`, 45,382,790 bytes, SHA-256 `1b1268de9341dfbe490a046d8af8e10d0eee20707b23e6d9e09a4a80f586cbdc`.
- Both jars contain UltimateFinisherThrow.class. Server META-INF/jarjar has only metadata.json and aaa_particles-neoforge-1.21.1-2.3.1.jar; no Modern UI or Nashorn. Metadata: `ultimate-finisher-throw-2026-10-07-artifacts.json`.

## Not verified

- Fresh-client rendering/interpolation, cinematic framing and complete live six-punch/grab/throw/beam sequence. Tests exercise the production mover and actual level/entity positions, not the complete ServerPlayer controller.
- Player throws, Saga-specific states, scripted NPC teleports/leashes, multiplayer timing, and release/cancellation restoration did not receive a fresh manual playtest this turn.
- No TPS/MSPT profile or measured performance-gain claim.

## Next steps

1. Preserve dirty paths/assets. Restart the older user game JVM with the new jar.
2. Observe the NPC leaving the grab along the arc, then charge/fire/camera restoration. Keep presentation claims pending until observed.
3. Record target profile and fresh phase/log evidence for another issue. Keep local RCON disabled outside isolated validation; do not fix unrelated integration warnings speculatively.
