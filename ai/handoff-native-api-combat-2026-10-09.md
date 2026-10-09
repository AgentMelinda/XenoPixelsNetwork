# Handoff — native scripting API and combat continuation

**Date:** 2026-10-09  
**Repository:** C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen  
**Branch:** 1.21.1  
**HEAD:** aac595cb44d0f91b1845b43682c92d02bfa1a117

## Current state

- No commits, staging, push or history changes. HEAD/upstream divergence was 0/0.
- Dirty paths are recorded in `.superpowers/sdd/2026-10-07-combat-v3-bt3/dirty-paths-2026-10-09-api.txt`.
  Existing owner changes and dirty submodules remain intact.
- Fresh API client PID 311148 was identified by its parent 308928, creation time and Minecraft
  window, then gracefully closed. Its Gradle task exited 0. No Java/javaw process remained
  at the final process check. Never assume this remains true on the next turn.
- DMZ dependency SHA-256 remains
  `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`.
- User explicitly canceled Claude transfer. Continue locally. Subagents failed at the usage
  limit; their last partial item edits were completed and tested by the root agent.

## Changes

- Native `getAPI()` bridges on NPC, world, player and event wrappers expose the existing typed
  contracts without changing legacy return types or `event.API`. Nested calls are discoverable
  in the script Functions catalog; raw handles/reflection/filesystem types are filtered there.
- Native `world.spawnClone` now delegates to the actual clone handler and shared script state.
  Added validated health/max-health, death-state and despawn helpers. Adapted Lord Slug example
  uses native hook names, elapsed-time regen and guarded one-shot replacement.
- Live item custom-data NBT works through copied 1.21 components, including nested compounds
  and compound lists. Typed armor/book/block item views retain specialization through copies
  and splits. Books validate before mutation and preserve unrelated metadata.
- Ordinary mobs expose navigation and animal/monster/villager marker views; arrows and vanilla
  throwables expose their correct marker interfaces. Bard/Follower views use native job fields.
- Earlier continuation also added exact DMZ heavy/charge sounds, manual N2/N3 Dash reposition,
  refusal-safe lock retention, callback/session guards, green PauseScreen music controls,
  pinned Saga/Goku model aliases, and air hit-recovery AI/rotation/Hakai interruption.
- Main protocol is 111, preserving registration order. Client and server must match.
  Server configuration version is 28. `npcHitRecoveryTicks` is configurable through xenoset;
  `v3.attackSoundVolume` controls DMZ attack cues. `v3.dashCamera` is explicitly legacy/no-op.
- No published `net.bullettrain.xenopixelsmod.api/**` signatures changed.

## Verified

Focused final script/component/recovery command exited 0:

```powershell
.\gradlew.bat test --tests '*ScriptFunctionCatalogTest' --tests '*ExampleWrapperCallsTest' --tests '*BundledNashornTest' --tests '*LordSlugScriptTest' --tests '*XenoApiAdaptersTest' --tests '*XenoItemNbtTest' --tests '*XenoSpecializedItemTest' --tests '*XenoMobAdapterTest' --tests '*NpcKnockbackGraceTest' -PofflineMcMeta
```

Final full command exited 0, after correcting a local-variable shadowing compilation error
and rebuilding the later cached-job-type compatibility adjustment:

```powershell
.\gradlew.bat test build jarJar serverJar buildApiExampleAddon -PofflineMcMeta
```

JUnit XML: 562 suites, 3,622 tests, 0 failures, 0 errors, 0 skipped.
`git -c core.whitespace=cr-at-eol diff --check` passed. The existing CRLF configuration file
was preserved; its diff is 6 added lines and 1 removed line, not a file-wide normalization.

| Final artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| build/libs/xenopixelsmod-0.5.11-1.21.1.jar | 76244139 | a4f7a5ff04785aeb90b147882f58742506d499b74b38449d49e8b4b61cd07e2c |
| build/libs/xenopixelsmod-Server-0.5.11-1.21.1.jar | 52097290 | bdb18acffd0b1199128bfe22a895ef76e6c96748dd6c20167d97c4da58e76310 |

Server META-INF/jarjar contains only its directory entry, metadata.json and
aaa_particles-neoforge-1.21.1-2.3.1.jar. Neither Modern UI nor Nashorn is embedded there.

Fresh `.\gradlew.bat runApiTestClient` exited 0 after graceful closure. Log on 2026-10-09:
05:07:56.917 XenoAPI event listener registered; 05:08:09.005 integrated server started;
05:08:19.517 Dev logged in; 05:08:25.685 ping and 05:08:25.719 pong share nonce
156932893839500. Native Init/Update typed events also fired. Log snapshot is
`ai/evidence/api-test-client-2026-10-09.log`.

The process reported a Controlify remappable-Shadow mixin error, optional class warnings,
remote-service timeout, repeated GL_INVALID_ENUM renderer messages and shutdown fsync warnings.
They did not prevent login/packet proof. They were not repaired as unrelated validation issues.

## Not verified

- All-function behavioral compatibility is NOT complete. All 216 official types and 1,189
  interface method shapes are declared under the remapped namespace; declaration coverage
  does not prove behavior. Audit JSON records the bounded comparison and pre-change refusals.
- Custom GUI, reserved/specialized jobs, non-trader roles, scripted block/item/projectile
  functionality, recipes, Pixelmon and several NPC settings remain gaps. Raw handles remain
  refused by the existing sandbox contract. See the full audit's current update section.
- Actual Lord Slug death/clone replacement, live Goku model selection, air hit recovery,
  book inventory synchronization, charged/heavy audibility and music widget interaction
  were not manually exercised. Nashorn book/NBT methods did execute in focused tests.
- All 302 Strike choreographies still await in-game comparison. Do not claim one-to-one
  reconstruction; local reference-video choreography work remains incomplete.
- Full DBZ OST requested from YouTube was not downloaded or implemented. The explicit source
  URL/playlist question is unanswered. Existing music uses 39 verified native DMZ tracks.
- Some requested xenoset tuning parameters are still constants/unwired. Do not claim every
  new value is configurable. In particular the owner heavyChargeLaunchDistance field is
  retained/serialized but is not proof of a working launch setting.
- External multiplayer client/server deployment has not occurred.

## Next steps

1. Continue API gap implementation from `docs/combat-v3/customnpcs-api-audit-2026-10-08.md`,
   preserving server authority and native semantics instead of adding successful no-ops.
2. Exercise native Lord Slug and model/air-recovery scenarios in a fresh game.
3. Continue choreography from the local Downloads MP4, never open YouTube for that reference.
4. Resolve the pending OST source question; do not claim absent audio files exist.
5. Rebuild and repeat fresh runtime checks after further code changes. Preserve current dirty
   paths; do not commit/push unless explicitly requested.
