# Handoff — Dragon Dash N continuation

**Date:** 2026-10-07  
**Repository:** C:/Users/Admin/.grok/worktrees/dragonminez/XenoPixelsNetwork_qwen  
**Branch:** 1.21.1  
**HEAD:** e011e7bec8061d8c2228cfbdc63ee57468749d0d

## Current state

- User explicitly approved the design: initial N launches the existing dash; another N during
  approach or immediately after its hit crosses the same target, faces it and allows a mouse
  attack continuation. Conversation is Hebrew; source comments are English.
- Existing dirty work preserved. Baseline is the 341-path snapshot
  `v2-charged-strikes-2026-10-07-status-resumed.txt`; review snapshot for this task recorded 343
  paths in `dragon-dash-continuation-2026-10-07-status-review.txt`. The final complete dirty-path
  snapshot is `dragon-dash-continuation-2026-10-07-status-after.txt` (343 paths). Comparison to
  the baseline found only two added entries and no removed entries. Untracked directories hide
  individual child files in short status, so counts are not source-file change counts.
- Fresh branch/HEAD and upstream divergence confirmed: 0/0. No staging, commit, push or cleanup.
- Exact DMZ dependency SHA-256 remains
  `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`, matching
  `dragonminez_sha256` in gradle.properties. No dependency, public API package, mixin, build
  configuration or sequential packet registration was changed by this task.
- Two isolated validation server processes ran. First crashed in DMZ radar synchronization;
  second ran the native tests and stopped through local RCON, with process exit 0. All builds
  launched for this task have ended. No user process was stopped; user clients may hold old code.
- Generated validation server config has `enable-rcon=false` and empty RCON password again.

## Changes

- `client/combat/v2/DragonDashGesture` keeps the first dash charged on release. A fresh press
  during a server-reported continuation fires immediately once, with no duplicate on release.
  Holding before a window opens does not synthesize a fresh press. Paused/unlocked holds require
  release and another press. `V2InputLayer` cancels the owned dash on a screen, lost/changed lock,
  guard, unavailable fists or leaving combat, and sends the stop only once until state updates.
- `combat/v2/V2Moves` checks the same-target continuation before the initial dash cooldown/travel
  rejection. The crossing spends the existing Vanish ki/stamina cost and cooldown, reuses its
  blink/flight cleanup, faces the target and gives its configured iframes. No automatic extra hit
  is added: the player continues with a mouse attack. Early N cancels the incoming automatic hit;
  without early N the original dash still hits/launches and opens the post-hit continuation.
- `V2DragonDashLanding` crosses the actual approach line even at melee range, lands at target
  height, uses body yaw only for vertical alignment fallback, and rejects range, level, border
  or destination collision failures. Protection/lock/visibility validation stays on the server.
  Failure before spending retains the window and costs nothing.
- `V2Fighter`, `V2Motion`, `V2CombatServer` own the travel/post-hit window and cleanup. Default
  `dragonDashFollowupTicks=24` is 1.2 seconds, clamped 0–100; zero disables only the post-hit
  window. Another V2 input closes the post-hit window; damage/guard/stun/end clear it. A valid
  single-use continuation bypasses the launch's generic minimum input interval, avoiding a fast
  second N being consumed before it can execute; Vanish cooldown and server window still apply.
- `CombatV2StatePacket` appends dash ticks/target after existing fields; `V2ClientState` mirrors
  and expires them. `CombatPromptOverlay` shows `N: Dash vanish` for the matching target under
  existing prompt visibility settings. Main protocol is now **106**, exact matching required;
  packet IDs and input/state ordinals remain unchanged.
- Added gesture, window/config, client countdown and real buffer packet round-trip unit tests.
  `V2DragonDashGameTests` adds two native world tests for the new landing calculation. Updated
  `docs/combat-v2.md` with controls, config, compatibility and verification limits.
- No commit hashes for these changes: all remain in the existing working tree.

## Verified

- Focused command, exit 0 in 1m02s:
  `.\gradlew.bat -g C:/Users/Admin/.gradle test --tests '*DragonDashGestureTest' --tests '*V2DashWindowTest' --tests '*CombatV2StatePacketTest' --tests '*CombatV2ProtocolTest' --tests '*V2RulesTest' --tests '*V2ChargeRulesTest' -PofflineMcMeta`.
- Full distribution/API command executed after each later source change, all exit 0:
  `.\gradlew.bat -g C:/Users/Admin/.gradle test build jarJar serverJar buildApiExampleAddon -PofflineMcMeta`.
  Final run completed in 1m44s; API example build 11s. Fresh final XML: **3406 tests, 524 suites,
  zero failures/errors/skips**. Default C:/.gradle is unavailable; use the explicit cache path.
- Final client `build/libs/xenopixelsmod-0.5.11-1.21.1.jar`, **69,567,755 bytes**, SHA-256
  `04d3646386aa0a4bc56578e49f31e44d850f49ae6575d68736c0c4a8192e8f94`.
- Final server `build/libs/xenopixelsmod-Server-0.5.11-1.21.1.jar`, **45,420,906 bytes**, SHA-256
  `7fa4686bcbdf2c99d01dc0e82897d6add5fcadf2a6d4d70f84d06e4bea76d95e`.
- Final ZIP entry audit: server META-INF/jarjar contains only metadata.json and
  aaa_particles-neoforge-1.21.1-2.3.1.jar, plus directory entry. No Modern UI/Nashorn. Client
  includes Modern UI as expected. The new landing/gesture classes are packaged.
- Exact final client/server jars inspected with JDK21 `javap -p -constants` against
  net.bullettrain.xenopixelsmod.network.ModNetwork: both embed `PROTOCOL = "106"`.
- Runtime command (both attempts):
  `.\gradlew.bat -g C:/Users/Admin/.gradle --init-script ai/handoffs/ultimate-finisher-2026-10-07-server.init.gradle runServer`.
  Uses only generated `build/ultimate-finisher-server-runtime`, loopback 25575 and RCON 25577.
- First log: `dragon-dash-continuation-2026-10-07-server-first.log`, startup Done at 13:00:15.264,
  protocol 106/80 packets at 13:00:02.654. Crash at 13:00:22.393:
  `No payload binding for com.dragonminez.common.network.S2C.RadarSyncS2C`, from DMZ
  DragonBallsHandler.onLevelTick. Crash report archived as
  `dragon-dash-continuation-2026-10-07-server-first-crash.txt`. The Gradle run exits 0 despite
  that crash; it is not a passing runtime check. First RCON connection was refused after crash.
- Retry log: `dragon-dash-continuation-2026-10-07-server-second.log`. Done at 13:03:15.035;
  `test runall` via the existing local RCON helper started **15 tests** at 13:03:17.371.
  New PASS lines at 13:03:18.605 and 13:03:18.635 verify blocked/range refusal and opposite-side
  landings at near/far range and different heights. Existing charge/throw/beam/impact regressions
  also passed through 13:03:20.693. `test runfailed` returned `No tests found`; then `stop` at
  13:03:21.631. Process exited 0 (47s Gradle run). No validation server remains running.
- Known startup messages persisted: client-only mixin target warnings on dedicated server,
  Curios references to absent CustomNPCs entities, and Sable create:flywheel tag warnings.
  These unrelated issues were not changed. The new radar exception is preserved separately.
- Fresh `git diff --check`, branch/hash and upstream divergence checks exit 0.

## Not verified

- Actual client N packet gameplay, rendered teleport/effects, attack continuation and multiplayer
  latency. World tests calculate destinations with mock players; they do not prove network
  teleport execution or input events in a live client. No native app gameplay control was used.
- Long-running dedicated server stability: the first attempt crashed after startup. The retry
  was stopped immediately after the tests; this does not prove that radar synchronization will
  remain stable. No unrelated DMZ networking repair was attempted.
- The final minimum-input-interval exception was added after the successful native geometry run;
  it passed the final full unit/build/API checks, but real rapid N presses remain manual pending.
  The tested landing/world code did not change after that run.
- Previous finisher camera/flight visual and charged animation/glow presentation checks remain
  manual pending, as recorded in the earlier charged-strikes handoff.

## Next steps

1. Fresh matching protocol-106 client/server: lock an Xeno NPC, hold/release N, then press N again
   during approach and separately within 24 ticks after its hit. Verify crossing/facing at near,
   far and different heights, then mouse attack continuation. Test rapid N and no duplicate on
   release, cost/cooldown, blocked destination, screen/lock loss/guard/damage cancellation.
2. Independently reproduce the DMZ RadarSyncS2C registration failure before claiming stable
   dedicated gameplay. Preserve the first crash; do not silently replace the pinned jar or fix
   unrelated failures inside this combat task.
3. Preserve the dirty tree and exact artifacts. No commit/push is authorized. Use the earlier
   handoffs for prior changes; the protocol-105 artifacts they describe are superseded above.
