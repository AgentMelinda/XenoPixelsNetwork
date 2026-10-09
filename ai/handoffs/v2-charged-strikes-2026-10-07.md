# Handoff — charged strikes, whole-body animations and finisher flight jitter

**Date:** 2026-10-07  
**Repository:** C:/Users/Admin/.grok/worktrees/dragonminez/XenoPixelsNetwork_qwen  
**Branch:** 1.21.1  
**HEAD:** e011e7bec8061d8c2228cfbdc63ee57468749d0d

## Current state

- Dirty user-owned tree preserved. Complete snapshots: `v2-charged-strikes-2026-10-07-status-before.txt`
  and `v2-charged-strikes-2026-10-07-status-after.txt` in this directory. The latter recorded 340 paths.
  Resumed snapshot: `v2-charged-strikes-2026-10-07-status-resumed.txt`, 341 paths.
  Fresh upstream divergence is 0/0. HEAD and branch remain as above.
- No commit, staging, push or cleanup. No user Java process was stopped. The default PowerShell
  process query returned no Java entries; process visibility was not independently verified.
- DMZ jar SHA-256 `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`
  exactly matches `dragonminez_sha256` in `gradle.properties` (fresh comparison).
- The initial approval-review usage-limit failure was resolved after the user requested resumption.
  Three isolated validation server processes subsequently ran and were stopped through loopback
  RCON. The final process exited normally; no validation server remains running. User processes
  were not stopped and may still hold earlier classes.
- Generated validation configuration remains `enable-rcon=false`, with an empty RCON password.

## Changes

- Hold LMB for charged punch, RMB for charged kick. `ChargeGesture` makes a mouse press either
  one tap on release or one charge, not a tap plus a charged combo follow-up. Holding eight ticks
  begins the server charge clock; twenty more server ticks reach full power. Cancellation covers
  screens, lost lock, guard, stun/damage, seat, controller changes and timeout.
- New appended V2 inputs 12–14 start/cancel charges; appended state 9 is CHARGE. Main packet ids
  are unchanged; exact protocol is 105, requiring matching client and server builds. No public
  API package or dependency binary was changed.
- `V2Charges` derives power from the server clock, ignores client charge percentages and validates
  target identity. Separate persistent punch/kick counters advance on eligible full releases at
  7–20 blocks with sight. Every fourth such attempt teleports: punch in front of body yaw, kick
  behind, at the target's height. Collision/border checks reject unsafe landings. A blocked fourth
  attempt consumes the cycle. Other ranged releases may miss; they do not automatically chase.
  Counters survive logout and player clone/death.
- Dedicated charged strike nodes deliver heavy damage. A connected fourth kick starts a
  24-tick, 20-block horizontal, six-block apex arc. Explicit collision-aware movement supports
  NoAI Xeno NPCs, clears consumed velocity and restores borrowed gravity on end/cleanup.
  Full punches emit a chest-height visual explosion after accepted damage, without another
  damage event or terrain destruction. Existing protection and guard restrictions remain.
- Golden feet ring/body flame shell grows dim-to-bright and pulses at full power; bounded
  server gold dust makes charges visible to nearby clients. No profiler/TPS benefit is claimed.
- Thirteen Xeno-owned clips were added/re-authored: mirrored hooks, low/mid kicks, knees,
  high roundhouse and four charged hold/fire clips. Seven verified DMZ rig bones transfer
  weight with waist twist, planted-leg movement and counterbalancing arms, without scaling.
  `tools/weighted_bt3_strikes.py` is imported by the existing animation generator. Only these
  thirteen JSON entries were updated, preserving other user animation entries.
- Charge slots preserve studio overrides and give Legacy/BT3 the same defaults. Delayed
  legacy strike chains no longer interrupt these longer authored releases. Default combo graph
  also routes A/D to hooks after a heavy-to-light branch; custom external graphs were not edited.
- FOLLOW-UP AFTER THE SUCCESSFUL BUILD: user reported finisher shaking, especially while flying.
  Source diagnosis: `V2Motion.stopTravel` clears ChaseFlightState at approach arrival, but
  UltimateFinisher continues sending zero motion while native Search/Combat Fly writes flight
  velocity. Both existing client mixins now also suppress native flight while the finisher
  cinematic is active; the camera's movement-input event prevents additional local drift.
  This follow-up compiled and is included in the final artifacts below. Visual shaking resolution
  remains manual pending; a server cannot prove client flight rendering or camera behavior.
- Resumed world validation exposed a native damage-order defect: vanilla hurt's knockback runs
  after LivingDamageEvent.Post and could overwrite the finisher's fixed beam impulse. The event
  now records a positive accepted-damage witness; the narrow AbstractKiProjectile.applyDamageOrHeal
  RETURN mixin consumes it after hurt completes, validates the victim and protection again, and
  applies the owned wave impulse. Refused/zero damage and unrelated projectiles do not gain it.
  The native regression now samples eight source directions. Exact DMZ javap verified the target
  method and TargetHelper.resolveHittable; pinned MC source verified event/knockback order.
- New charged GameTests needed PrefixGameTestTemplate(false): the first server skipped their
  missing class-prefixed templates. The corrected registration runs all thirteen tests.

## Verified

- `.\gradlew.bat -g C:/Users/Admin/.gradle compileJava -PofflineMcMeta` — exit 0, 21s.
- Initial focused test run found a newly authored knee length of 0.32s versus catalog 0.28s;
  corrected the generator and its two owned JSON entries.
- `.\gradlew.bat -g C:/Users/Admin/.gradle test --tests '*V2ChargeRulesTest' --tests '*ChargeGestureTest' --tests '*CombatStateAnimTest' --tests '*Bt3AnimationCatalogTest' --tests '*CombatV2ProtocolTest' -PofflineMcMeta`
  — corrected run exit 0, 50s. Covers full-charge timing, inclusive range, deterministic fourth
  cadence, arc geometry, cancel/re-entry gestures, stable ordinals and hold/fire rig continuity.
- `.\gradlew.bat -g C:/Users/Admin/.gradle test build jarJar serverJar buildApiExampleAddon -PofflineMcMeta`
  — final resumed run exit 0, 1m16s; standalone addon build exit 0, 8s.
- Resumed focused flight/charge/protocol test run exited 0 in 42s. The later focused
  `test --tests '*UltimateFinisherRulesTest' --tests '*UltimateFinisherTechniqueTest' --tests '*V2ChargeRulesTest' -PofflineMcMeta`
  using the same Gradle executable/cache exited 0 in 51s after the damage-order fix.
- Fresh JUnit XML totals: 3395 tests, 521 suites, zero failures/errors/skips.
- Built client: `build/libs/xenopixelsmod-0.5.11-1.21.1.jar`, 69,560,774 bytes,
  SHA-256 `9597b25382173be5a70fb26fe478b1c3fecbc40e40eec64224a741822b60ed2f`.
- Built server: `build/libs/xenopixelsmod-Server-0.5.11-1.21.1.jar`, 45,413,925 bytes,
  SHA-256 `7aea6246eaea330d0a09d5a1c6c8b01cd37b38ca19579eff1bbe2790d64e184b`.
- Fresh archive inspection: server META-INF/jarjar contains metadata.json and
  aaa_particles-neoforge-1.21.1-2.3.1.jar, plus the directory entry. No Modern UI/Nashorn.
- Exact-jar `javap -p` verified private static `FlySkillEvent.handleFlightMovement(LocalPlayer,int,boolean)`
  and public static `CombatFlightHandler.handle(LocalPlayer,StatsData,boolean)` before changing
  their existing mixin conditions. The exact NeoForge sources jar confirms Input.shiftKeyDown.
- Resumed `git status --short`, `git rev-parse HEAD`, `git branch --show-current`,
  `git rev-list --left-right --count 'HEAD...@{upstream}'` and `git diff --check` exited 0
  under approved escalation. Diff check produced only CRLF conversion warnings.
- Final fresh server command:
  `.\gradlew.bat -g C:/Users/Admin/.gradle --init-script ai/handoffs/ultimate-finisher-2026-10-07-server.init.gradle runServer`
  exited 0 in 1m24s after RCON stop. Final startup 2026-10-07 12:16:02 IDT;
  `test runall` ran thirteen tests at 12:16:45.922. Explicit PASS lines at 12:16:46–49 prove
  body-yaw front/back/height landings, blocked landing/arc, actual NoAI Xeno NPC displacement
  (0,0,20), eight-direction accepted beam impulse/refused damage, native WAVE release, 15-block
  held Xeno NPC throw and AAA impact height. `test runfailed` returned "No tests found".
  Final log: `v2-charged-strikes-2026-10-07-server-final.log`. First/second diagnostic logs are
  preserved separately; the second records the pre-fix beam failure and isolated rerun.
- Existing Curios missing CustomNPCs entity and Sable missing create:flywheel startup errors
  persist. They did not prevent startup/tests and were not changed.

## Not verified

- Actual client golden glow rendering, seven-bone pose appearance, charge packets, player/NPC
  fourth-release gameplay and counter survival in a live session remain manual pending.
- Finisher flight jitter follow-up: fresh client visual resolution NOT verified. Final jars
  include the follow-up, but a running game may still hold older code.

## Next steps

1. Fresh client with matching protocol-105 server: hold/release LMB and RMB; verify dim/full
   glow and poses; exercise fourth
   releases at exactly 7/20 blocks, changed height and blocked landing; verify NoAI Xeno NPC arc.
2. In Search Fly and Combat Fly, use UltimateFinisher with movement keys held/released and
   verify no shaking through arrival, combo, grab, throw, charge, beam and STOP/timeout.
