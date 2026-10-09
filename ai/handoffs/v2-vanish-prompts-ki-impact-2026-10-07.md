# Handoff — V2 Vanish range, combat prompt visibility and wave impact height

**Date:** 2026-10-07  
**Repository:** C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen  
**Branch:** 1.21.1  
**HEAD:** e011e7bec8061d8c2228cfbdc63ee57468749d0d

## Current state

- User requested V2 Vanish behind distant/different-height targets, a client configuration for combat indicators with/without DMZ lock, and corrected AAA Kamehameha explosion height. They clarified that indicators mean XenoPixels key prompts (Grab/Counter/Chase), and explosions appear too low/in terrain.
- Upstream divergence 0/0. Existing extensive dirty work preserved, no commits/staging/push. Full dirty-path inventory: `v2-vanish-prompts-ki-impact-2026-10-07-status-after.txt`.
- Owned validation/build JVMs exited. Remaining user Java PIDs: 47464, 72000, 93172, 111052, 122388. The latter three started at 08:14, before this correction; they may hold older classes. None were stopped.
- DMZ 2.1.3 SHA-256 unchanged and property-matched: `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`.
- New artifact hashes below supersede earlier UltimateFinisher handoff artifact hashes. The earlier native Kamehameha/punch/throw fixes remain included.

## Changes

- V2 vanish previously selected a target only inside twelve 3D blocks; other targets became a short caster-relative blink. New `vanishUsesLockRange=true` defaults even for an older JSON that still has `vanishRange:12`. `V2VanishLanding` uses the same validated lock range/slack and existing BT3 collision/side geometry, at target height. Lock/protection/visibility/cost/cooldown validation remains. `false` opts into `vanishRange` as a shorter limit; an out-of-range target refuses rather than silently blinking.
- Source: `combat/v2/V2Config.java`, `V2Lock.java`, `V2Moves.java`, new `V2VanishLanding.java`, and the input layer's explanatory comment. No legacy/shared BT3 landing geometry changes.
- Client `combatPromptsWithLockOn` and `combatPromptsWithoutLockOn` are independently serialized/applied, both default true. Config version 7 writes new fields into older client files. The early gate in CombatPromptOverlay covers every key plate, including lockless grabbed/throw states. The legacy counter action-bar prompt uses the same gate. Combat input is unchanged.
- Source: `client/config/XenoClientConfig.java`, `client/combat/v2/CombatPromptOverlay.java`, and `client/combat/Bt3CombatClient.java`.
- Exact DMZ KiWaveEntity.explodeAndDie sets its visual to `pos.y - 0.5`. New common KiWaveImpactOriginMixin tags only that method's visual argument before Level.addFreshEntity. KiImpactEvents/KiImpactRules restore 0.5 Y only for the AAA replacement of tagged wave visuals. Ordinary explosion origins, native fallback rendering positions, effect assets, damage and blast radii are unchanged.
- Reviewed new mixin entry individually in `src/main/resources/xenopixelsmod.mixins.json`. Native addFreshEntity descriptor and private explodeAndDie method were verified with javap against the exact dependency. No published API, channel ordinals/protocol, AddonNetwork, dependency or build logic changes.
- Focused tests: new CombatPromptVisibilityTest and V2VanishLandingTest; KiImpactTest extended. Runtime: UltimateFinisherGameTests gained distant/higher/lower landing coverage; new fx/effek/KiImpactGameTests captures AAA requests while exercising the actual native wave termination/spawn/mixin/join path. The latter uses reflection only in the test to invoke the pinned private termination method.
- Docs: `docs/combat-v2.md`, `docs/effekseer-fx.md`.

## Verified

- Focused command: `.\gradlew.bat -g C:/Users/Admin/.gradle test --tests '*V2VanishLandingTest' --tests '*CombatPromptVisibilityTest' --tests '*KiImpactTest' --tests '*V2RulesTest' --tests '*Bt3LandingTest' -PofflineMcMeta`, exit 0, BUILD SUCCESSFUL in 1m 32s.
- Prompt tests exercise all four lock/unlock visibility combinations across snapshot/Gson/apply, and missing fields in version-6 data. Vanish tests check older twelve-block config now uses full lock reach and that an explicit shorter option remains enforced.
- Audit: `node C:/Users/Admin/.agents/skills/xenopixels-addon-development/scripts/audit-project.mjs .`, exit 0, zero failures and six existing integration/reflection advisories (611 string constructs, including verified new mixin/test descriptors).
- Runtime: `.\gradlew.bat -g C:/Users/Admin/.gradle --init-script ai/handoffs/ultimate-finisher-2026-10-07-server.init.gradle runServer`, isolated game directory, localhost port 25575. ModLauncher 08:25:27.184; server Done 08:26:09.398. No new mixin application failure.
- `ai/handoffs/ultimate-finisher-2026-10-07-rcon.ps1 -Command 'test runall'` ran ten tests. New landing helper pass at 08:26:21.595: target more than 30 horizontal blocks away and 28 blocks higher, left/center/right landings; lower target 20 blocks below caster; explicit short range refusal. At 08:26:22.092 native wave explosion's AAA request exactly matched impact `(-446.0,81.0,117.0)`; ordinary origins and disabled-AAA native fallback preserved. Earlier finisher beam/throw protections/native wave regressions also passed. No LogTestReporter failures.
- Evidence: `v2-vanish-prompts-ki-impact-2026-10-07-runtime.log`. Graceful RCON `-Command 'stop'` saved all dimensions; RCON listener stopped 08:26:43.587. runServer exit 0, BUILD SUCCESSFUL in 1m 51s. Generated isolated RCON is disabled again and password blank.
- Full command: `.\gradlew.bat -g C:/Users/Admin/.gradle test build jarJar serverJar buildApiExampleAddon -PofflineMcMeta`, exit 0, BUILD SUCCESSFUL in 2m 6s; standalone example addon exited 0. XML totals: 3,386 tests, 519 suites, zero failures/errors/skips.
- `git diff --check` exit 0 with existing LF/CRLF warnings.
- Client jar: `build/libs/xenopixelsmod-0.5.11-1.21.1.jar`, 69,536,547 bytes, SHA-256 `622e82471e203962c29237fdcab5e509bcf51c0a373786dcd1b5d1e5ed77d6fd`.
- Server jar: `build/libs/xenopixelsmod-Server-0.5.11-1.21.1.jar`, 45,389,698 bytes, SHA-256 `c176e2783802dc3aa71a5bb01596b388010cd07957d946b4b0d994e85488e500`.
- Both jars contain V2VanishLanding and KiWaveImpactOriginMixin. Server META-INF/jarjar contains only metadata.json and the AAA Particles jar; no Modern UI/Nashorn. Metadata: `v2-vanish-prompts-ki-impact-2026-10-07-artifacts.json`.

## Not verified

- Fresh-client key-prompt pixels under each lock/grab state and actual input-to-teleport presentation. Server tests verify production landing calculation/collision eligibility, not the complete network input/teleport controller.
- AAA/Effekseer rendering and how the explosion visually clips at ground/craters. The runtime test captures the exact request coordinates via a fake sender; it does not render or prove a real AAA packet reached a client.
- Multiplayer timing/latency, optional-mod absence matrix and the complete cinematic sequence were not freshly played this turn.
- No TPS/MSPT measurements or performance-gain claims.

## Next steps

1. Preserve dirty files/assets and restart the older user JVM with the latest jar/classes.
2. In the client game's `config/xenopixelsmod-client.json`, set either prompt switch false as desired; both false hides all key prompts. Restart after editing. The actual dev config is currently under run/config; it was read only, not edited as source.
3. Test double A/D at distant/higher/lower locked targets, the with/without-lock prompt switches, and Kamehameha ground impact rendering. Keep unverified visual claims pending until observed.
4. Keep isolated RCON disabled. Use fresh logs/profile/positions for another issue; do not repair unrelated native integration warnings speculatively.
