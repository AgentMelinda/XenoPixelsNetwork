# Handoff — V2 Vanish teleport motion synchronization

**Date:** 2026-10-06  
**Repository:** C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen  
**Branch:** 1.21.1  
**HEAD:** e011e7bec8061d8c2228cfbdc63ee57468749d0d

## Current state

- Tree was already dirty, including untracked V2 source and the preceding side-key Vanish/Step
  removal. Existing work was preserved. No staging, commit, tag or push. Upstream: 0 ahead/0 behind.
- Dirty paths and running Java process IDs/start times are recorded in the adjacent
  `vanish-v2-teleport-motion-2026-10-06-evidence.json`.
- A gameplay client/integrated server was already running since 2026-10-06 18:31
  Asia/Jerusalem, before this fix. It was left running. Rebuilding does not replace its loaded
  classes; its current log is not post-fix gameplay evidence. This is not a clean-tree claim.
- DragonMineZ 2.1.3 SHA-256 still matches the unchanged dependency property:
  `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`.

## Changes

- The user confirmed the reported failure also happens with a close, locked target.
- Source comparison found that legacy/BT3 `Bt3CombatPacket.teleportFacing` clears velocity and
  sets `hurtMarked`, while V2 cleared velocity without that flag. The pinned Minecraft source
  in `build/moddev/artifacts/neoforge-21.1.248-sources.jar` confirms that
  `ServerEntity.sendChanges` uses `hurtMarked` to send a motion packet to observers and the
  player. Missing the flag can leave the controlling client with its previous motion.
- `V2Moves.blink` now calls `resetTeleportMotion`, which also sets `hurtMarked`. The helper is
  package-private and shares the same path for Vanish, counter and directional blink.
- Added `V2TeleportMotionTest` using a real Minecraft Entity/Marker, with no motion mocks.
  It verifies zero velocity, the client synchronization flag, impulse flag and fall reset.
- No public API, network ordinal/layout, config value, landing geometry, target protection,
  range or cooldown changed. The unrelated range/protection Blink fallback was left intact.
- Source write set: `src/main/java/net/bullettrain/xenopixelsmod/combat/v2/V2Moves.java` and
  `src/test/java/net/bullettrain/xenopixelsmod/combat/v2/V2TeleportMotionTest.java`.
  Additional files are this handoff and its adjacent red/focused/artifact/evidence reports.

## Verified

- Failing regression command:
  `.\gradlew.bat -g C:/Users/Admin/.gradle test --tests net.bullettrain.xenopixelsmod.combat.v2.V2TeleportMotionTest -PofflineMcMeta`
  — exit 1 before the flag fix: expected the synchronization flag true, observed false.
  Saved result: `vanish-v2-teleport-motion-2026-10-06-red.xml`.
- Focused verification:
  `.\gradlew.bat -g C:/Users/Admin/.gradle test --tests net.bullettrain.xenopixelsmod.combat.v2.V2TeleportMotionTest --tests net.bullettrain.xenopixelsmod.combat.v2.V2RulesTest --tests net.bullettrain.xenopixelsmod.client.combat.v2.VanishGestureTest --tests net.bullettrain.xenopixelsmod.network.Bt3VanishGeometryTest -PofflineMcMeta`
  — exit 0; 63 tests passed. Saved suite counts in the adjacent focused JSON.
- Full validation:
  `.\gradlew.bat -g C:/Users/Admin/.gradle test build jarJar serverJar -PofflineMcMeta`
  — exit 0; full results recorded in the evidence JSON.
- `git diff --check` — exit 0; existing Git permission/CRLF advisory warnings only.
- Current server jar has exactly the metadata file and AAA Particles beneath
  `META-INF/jarjar/`, excluding the directory entry. No Modern UI or Nashorn there.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| xenopixelsmod-0.5.11-1.21.1.jar | 69,495,348 | `1685d6804eb7027a08ce1994c6c1f3b9b0ca1d9c787abdef841ca9ea44084202` |
| xenopixelsmod-Server-0.5.11-1.21.1.jar | 45,348,499 | `12b313e22c133e2f22d1ee79aeadabdbfd05a7e028d1208cb65254eb5ca78e76` |

## Not verified

- The missing synchronization flag is reproduced by the regression test; the original gameplay
  symptom and its resolution were not independently observed by the agent.
- In-game landing after restart, multiplayer latency and DMZ flight interactions remain manual
  pending. No fresh gameplay process was started for the fixed build.

## Next steps

1. Close and restart the existing client with the rebuilt code before testing.
2. Under V2, lock on within 12 blocks and double-tap A/D, on the ground and while flying.
3. Confirm the fighter arrives beside/beyond the target without old velocity carrying them away.
4. If the symptom persists, capture the target type and target eligibility: a protected target,
   blocked sight with through-block lock disabled, or range beyond 12 still selects directional
   Blink. Those conditions require separate reproduction, not a guessed change to combat rules.
