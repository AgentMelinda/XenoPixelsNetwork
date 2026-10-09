# Handoff — V2 side-key Vanish and Step removal

**Date:** 2026-10-06  
**Repository:** C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen  
**Branch:** 1.21.1  
**HEAD:** e011e7bec8061d8c2228cfbdc63ee57468749d0d

## Current state

- Upstream divergence: 0 ahead, 0 behind. The tree was already dirty, including the V2
  subsystem as untracked source. Existing work was preserved. No commit, staging, push or tag.
- Full dirty-path inventory, test-suite results and artifact details are in
  `vanish-v2-no-step-2026-10-06-evidence.json` beside this handoff.
- No gameplay client or server was started. Process inventory is recorded in the evidence file;
  running Gradle/test JVMs are not gameplay evidence. This is not a clean-tree claim.
- DragonMineZ 2.1.3 SHA-256 matches `gradle.properties`:
  `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`.
- Current distribution version in the existing properties: `0.5.11-1.21.1`.

## Changes

- Double-tap either side movement binding while locked on to request V2 Vanish, using the
  legacy/BT3 280 ms window. A selects left, D selects right, including when forward is held.
  Held movement and taps before acquiring a lock do not arm a Vanish. The existing B alternative
  remains; a B press coinciding with the gesture produces one request.
- Removed V2 Step movement, fighter fields, configuration, registration, shortcuts and HUD chip.
  Back movement still stops chase. Legacy/BT3 Sonic Sway behavior remains under those controllers.
- Retained only the retired STEP enum slots to preserve wire ordinals. Old Step input is dropped
  at decode and server admission; the old state slot decodes to neutral. No packet layout,
  sequential channel registration or published API signature was changed.
- Counter/Vanish HUD labels now show the side-key gesture. Documentation and tests are in English.
- Write set: `client/combat/v2/{VanishGesture,V2InputLayer,V2Keys,CombatStance,CombatPromptOverlay}.java`,
  `client/combat/Bt3CombatClient.java`, `client/keybind/XenoKeybinds.java`,
  `client/XenoCooldownHudOverlay.java`,
  `combat/v2/{V2Input,V2State,V2CombatServer,V2Motion,V2Fighter,V2Config,V2Moves,V2Strikes,V2Support,V2Grab}.java`,
  and `combat/v2/motion/MotionRules.java` under the existing main Java package;
  `src/main/resources/assets/xenopixelsmod/lang/en_us.json`, `docs/combat-v2.md`,
  `client/combat/v2/{VanishGestureTest,TapGestureTest}.java` and `combat/v2/V2RulesTest.java`
  under the test package, and this handoff/evidence pair.

## Verified

- Project audit: `node C:/Users/Admin/.agents/skills/xenopixels-addon-development/scripts/audit-project.mjs .`
  — exit 0, no failures; six existing integration/reflection advisories.
- Focused command:
  `.\gradlew.bat -g C:/Users/Admin/.gradle test --tests net.bullettrain.xenopixelsmod.client.combat.v2.VanishGestureTest --tests net.bullettrain.xenopixelsmod.client.combat.v2.TapGestureTest --tests net.bullettrain.xenopixelsmod.combat.v2.V2RulesTest --tests net.bullettrain.xenopixelsmod.network.CombatV2ProtocolTest -PofflineMcMeta`
  — exit 0; 69 tests passed, including timing boundaries, lock loss, held keys, retired wire slots
  and ignoring old Step configuration while retaining Vanish tuning.
- Full validation:
  `.\gradlew.bat -g C:/Users/Admin/.gradle test build jarJar serverJar -PofflineMcMeta`
  — exit 0; 3,374 tests in 513 suites, no failures/errors/skips.
- `git diff --check` — exit 0. Existing CRLF conversion and restricted global-ignore warnings.
- Both current distribution jars contain `VanishGesture.class`.
- Server jar file entries beneath `META-INF/jarjar/` are exactly `metadata.json` and
  `aaa_particles-neoforge-1.21.1-2.3.1.jar`; no Modern UI or Nashorn.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| xenopixelsmod-0.5.11-1.21.1.jar | 69,495,262 | `03632fdcdc24fd345443e1926f674599c331838ccb0fa7a6d81e8f987ef14ad5` |
| xenopixelsmod-Server-0.5.11-1.21.1.jar | 45,348,413 | `3db53897d0608dea384ac22f730fe8183818c413c3a6758b79f39e374254d1b8` |

## Not verified

- Gameplay execution, counter timing under multiplayer latency, and the visual feel of Vanish.
- Dedicated-server startup and API example/runtime checks were not run for this internal input
  change. Packaging and unit tests do not prove in-game behavior.

## Next steps

1. Start a fresh client with the rebuilt version, enable V2, and lock on to a valid target.
2. Double-tap A and D separately, also with W held and an item in hand; check the side and counter.
3. Confirm no gesture activates without a lock, and old Step/Sonic shortcuts do nothing in V2.
4. Review this write set against existing user changes before any explicitly requested commit.
