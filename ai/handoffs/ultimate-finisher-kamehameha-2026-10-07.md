# Handoff — UltimateFinisher native Kamehameha, DMZ punches and knockback

Follow-up: `ultimate-finisher-throw-2026-10-07.md` fixes held Xeno NPC movement and supplies newer artifact hashes and actual throw runtime evidence.

**Date:** 2026-10-07  
**Repository:** C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen  
**Branch:** 1.21.1  
**HEAD:** e011e7bec8061d8c2228cfbdc63ee57468749d0d

## Current state

- This correction supersedes the earlier UltimateFinisher build on 2026-10-07. No commits, staging, tags, pushes, dependency changes, or public API changes were performed. The tree remains extensively dirty; unrelated work is preserved. Final dirty paths are recorded in the adjacent status file.
- The user's client started at approximately 07:25 and its log shows protocol 104 and actual `strike.intercept` calls for `xenopixelsmod:ultimate_finisher`. This proves interception was requested; it does not prove each cinematic phase completed. That process was not stopped by this task and may still hold the previous implementation.
- Final Java process inventory after validation contains only the pre-existing IDE JVMs, PIDs 47464 and 72000 (started 01:51:33 and 01:48:50). No game or validation server JVM was observed then. No clean-tree claim is made; the branch remains 0/0 relative to upstream with the same HEAD.
- Pinned DMZ jar SHA-256 remains `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`, matching `gradle.properties`.

## Changes

- `combat/v2/UltimateFinisherKamehameha.java` now calls the exact native `TechniqueDispatcher.executeKiAttack` for charge creation and release. Native initialization supplies WAVE type, Kamehameha renderer/colors, utility flags, armor penetration, damage scaling and lifetime. It refuses an existing unrelated charge and checks exclusive ownership before DMZ's multi-projectile release operation.
- `combat/v2/UltimateFinisher.java` uses the native DMZ `MeleeAnimationS2C` with alternating `combat.one_handed_punch_right` and `combat.one_handed_punch_left`, including self and tracking clients. Punch damage continues through the existing DMZ-aware V2 damage path. The six punches keep their existing no-push behavior.
- The released beam keeps its firing direction rather than following the victim each tick. Camera aim is corrected once at release, preserving the cinematic camera's separation from gameplay aim.
- A persistent server-only marker identifies this finisher's wave. Only its accepted, positive damage applies the additional beam push. `CombatKnockback` preserves protections/NPC movement grace; native `KnockbackHelper` handles flight synchronization and Saga movement. Ordinary Kamehameha projectiles have no marker and retain their existing behavior. Arc throw remains the existing velocity-driven 15-block path.
- `gametest/UltimateFinisherGameTests.java` adds real server-level charge/fire/direction and accepted/refused-damage knockback regressions. These use non-networked mock players so Sable's connection negotiation does not interfere. Test stats explicitly set Ki Power and Power Release.
- `docs/combat-v2.md` records the native path and the runtime boundaries. No packet ordinals, protocol version, mixin configuration, or AddonNetwork changes were needed for this correction.

## Verified

- Exact DMZ source/javap established the discrepancy: `KiWaveEntity(Level, LivingEntity)` retains the base SMALL_BALL type; DMZ's WAVE branch explicitly sets WAVE. The old finisher omitted that assignment. `TechniqueDispatcher.executeKiAttack(LivingEntity, Level, KiAttackData, StatsData, float)` is present in the pinned jar.
- Native punch names were checked in the exact jar's `assets/dragonminez/animations/entity/races/combat.animation.json`. Native packet constructor and flight knockback helper were checked against dependency source.
- Before the fix, real server GameTests at 07:41:17 failed with `Kamehameha must have WAVE type, found SMALL_BALL` and `Accepted beam damage pushes along the ray and lifts the victim`. Evidence: `ultimate-finisher-kamehameha-2026-10-07-red.log`.
- Focused command: `.\gradlew.bat -g C:/Users/Admin/.gradle test --tests '*UltimateFinisher*Test' --tests '*ChaseFlightOwnershipTest' --tests '*XenoRushTechniquesTest' --tests '*V2RulesTest' -PofflineMcMeta`, exit 0, BUILD SUCCESSFUL in 1m 17s.
- Full command: `.\gradlew.bat -g C:/Users/Admin/.gradle test build jarJar serverJar buildApiExampleAddon -PofflineMcMeta`, exit 0, BUILD SUCCESSFUL in 2m 11s; example addon exit 0. Final XML totals: 3,381 tests, 517 suites, zero failures/errors/skips.
- Audit command `node C:/Users/Admin/.agents/skills/xenopixels-addon-development/scripts/audit-project.mjs .`, exit 0, zero failures and six existing integration/reflection advisories. `git diff --check` passes; CRLF conversion warnings are recorded separately from errors.
- Artifact details are in `ultimate-finisher-kamehameha-2026-10-07-artifacts.json`. Client jar: 69,526,432 bytes, SHA-256 `f363fa173ccd7a294d3940864d8eeb93b4d97c3881bd8036e0c423a445391942`. Server jar: 45,379,583 bytes, SHA-256 `ebf162cdd5a3319f919e0420189295a1cef8ac66ad1b4748181e5a5698d61391`.
- The server jar's only non-directory `META-INF/jarjar/` entries are `metadata.json` and the AAA Particles jar. Modern UI and Nashorn are absent there. Both artifacts include the native finisher helper and regression tests.

## Runtime validation route

- `runGameTestServer` with the isolated `ultimate-finisher-kamehameha-2026-10-07-gametest.init.gradle` failed before tests at 07:35:54: MyNPCs `PlayerDataController` dereferenced a null directory. The separate bootstrap-failure log preserves it. No MyNPCs code or dependency was changed.
- Ordinary isolated `runServer` works with the original server init script. Its game directory is `build/ultimate-finisher-server-runtime`, server IP `127.0.0.1`, game port 25575. For validation only, local RCON used port 25577 and a task-specific password. The adjacent RCON script sends commands only to loopback.
- Initial networked mock players failed Sable's `udp_activation` payload negotiation. Switching the test fixture to `GameTestHelper.makeMockPlayer(GameType.SURVIVAL)` avoided network login entirely.
- After the implementation correction, accepted/refused-damage knockback passed at 07:46:43. The beam check reached native charge/release but failed its positive-damage assertion because the fresh test stats had zero Power Release. The fixture now explicitly sets Ki Power 100 and Power Release 100. This was a test-input correction, not a change to combat damage behavior.
- Final fresh server: ModLauncher 07:50:57.105, dedicated-server Done at 07:51:42.779. Local `test runall` started four tests at 07:53:04.129. Both new regressions passed explicitly: accepted/refused-damage push at 07:53:04.948; native WAVE charge, release, positive damage, growing beam and fixed firing direction at 07:53:06.852. No LogTestReporter failures appeared in that final batch. Evidence: `ultimate-finisher-kamehameha-2026-10-07-green.log`.
- Runtime command was `.\gradlew.bat -g C:/Users/Admin/.gradle --init-script ai/handoffs/ultimate-finisher-2026-10-07-server.init.gradle runServer`; tests and graceful stop were sent through `ai/handoffs/ultimate-finisher-2026-10-07-rcon.ps1 -Command 'test runall'` and `-Command 'stop'`. The isolated server's RCON setting is disabled again after validation.
- Final server shutdown saved all dimensions and stopped the RCON listener at 07:53:35. `runServer` exited 0, BUILD SUCCESSFUL in 3m 5s. The saved green log includes shutdown. `enable-rcon=false` and an empty password were restored in the isolated generated server properties.

## Not verified

- Fresh-client pixels, shader output, punch animation playback, cinematic framing, and the complete six-punch/grab/throw/beam sequence still require user observation after restarting with the new build.
- Multiplayer flight injection, addon protection cancellations, and in-game Surge growth/resource consumption have not been manually observed with this correction.
- The server GameTests validate native projectile metadata, charge survival, firing growth/direction and damage-owned push; they do not render frames or exercise the complete cinematic controller.
- No TPS/MSPT profile was taken and no measured performance gain is claimed.

## Xeno Notes / TODOs / ToImplement

- Notes: Preserve exact pinned APIs and native projectile behavior; keep the additional push scoped to this finisher. No speculative performance changes or unrelated integration fixes were made.
- TODOs: Restart the existing client before testing the new classes; equip UltimateFinisher, lock an eligible target, and inspect native punches, Kamehameha presentation, throw and beam knockback, and camera restoration.
- ToImplement: No additional feature scope is pending. Address any remaining live visual issue from a fresh observation/log rather than treating a server assertion as rendered proof.

## Next steps

1. Preserve the dirty working tree and user assets; do not blanket stage or clean it.
2. Use the new client/server jars and restart old JVMs before comparing behavior.
3. Keep local RCON disabled outside the isolated validation run. Record fresh observations before promoting unverified visual/multiplayer scenarios.
