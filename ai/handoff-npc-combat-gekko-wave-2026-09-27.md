# Handoff — NPC GeckoLib attacks, targets, and wave defaults

**Date:** 2026-09-27  
**Repository:** `C:\XenoPixelsNetwork_qwen`  
**Branch:** `1.21.1` (0 ahead, 0 behind `origin/1.21.1`)  
**HEAD:** `53a025e45a4fecc2aeb1a2d062db550f88bbf571`

## Current state

- The tree was already very dirty before this continuation and has 753 changed or untracked paths
  afterward. No commit, tag, push, reset, or cleanup was made. The paths touched in this slice are
  `CHANGELOG.md`, `docs/xeno-anim-studio.md`, `docs/xeno-npc-geckolib.md`,
  `docs/npc-animation-combat-flow.html`,
  `src/main/java/net/bullettrain/xenopixelsmod/anim/XenoClipLibrary.java`,
  `src/main/java/net/bullettrain/xenopixelsmod/client/npc/{XenoNpcEditorScreen,XenoNpcGeoModel}.java`,
  `src/main/java/net/bullettrain/xenopixelsmod/compat/npc/{NpcCombatProfile,NpcKiAim,NpcProfileLifecycle,NpcTargetKeeper}.java`,
  `src/main/java/net/bullettrain/xenopixelsmod/npc/{NpcAttackClipSelector,XenoNpcEntity}.java`,
  `src/main/java/net/bullettrain/xenopixelsmod/npc/brain/XenoNpcBrainV5.java`,
  `src/main/java/net/bullettrain/xenopixelsmod/npc/importer/{NpcSourceKeys,PlacedNpcProfileMigrator}.java`,
  `src/main/java/net/bullettrain/xenopixelsmod/network/packet/XenoNpcSavePolicy.java`,
  `src/main/resources/assets/xenopixelsmod/animations/social/hi_wave.animation.json`,
  `src/test/java/net/bullettrain/xenopixelsmod/anim/WaveClipContentTest.java`, and
  `src/test/java/net/bullettrain/xenopixelsmod/{client/npc/XenoNpcGeoModelTest,network/packet/XenoNpcSavePolicyTest,npc/NpcAttackClipSelectorTest,npc/importer/PlacedNpcProfileMigratorTest}.java`.
- A `runclient` Java process was running during the final build (PID 91508 observed, started
  2026-09-27 04:08:41 local). Do not treat it as proof of code rebuilt after it started.
- `libs/dragonminez-2.1.3.jar` is 61,672,772 bytes, SHA-256
  `5A6E33EF5B992E64FCCCCAED895B105318B2CE1AB8039D8193107D40D203B581`, matching
  `gradle.properties`.

## Changes

- Replaced the shipped `hi_wave` with the run-client version; SHA-256
  `090B8BACF2987366C6F02C5753140D79663D019E97F550A79482C67F97A32665` matches the
  run-client source exactly. Shipped `wave` already matched its run-client source and was not
  changed. Added the old shipped `hi_wave` hash to the exact-hash upgrade list so edited operator
  files are preserved. Only `wave` and `hi_wave` were promoted.
- Native GeckoLib NPC attack selection now uses animation names in that NPC's own baked file,
  preferring the configured attack clip, then conventional names, then another name containing
  `attack`. A new swing restarts the one-shot; completion releases the attack controller.
- The NPC editor accepts an optional `ModelAnimation` resource id, sent through the existing
  visual-options packet and persisted in profile NBT. This loads a custom rig's animation JSON
  when it does not share the model's basename. A missing override falls back to the derived path.
  The placed-NPC importer copies that id when the source has a `ModelAnimation` key; assets still
  need to exist in a client resource pack or mod.
- Native NPCs with combat brain off now have a gated basic melee goal whose attack interval follows
  Melee Speed. This closes a gap where they could acquire a target but had no attack goal.
  Fighting-role and target-validity gates remain.
- Creative and Spectator players are invalid combat targets. Existing target, navigation, memory,
  and hard lock are released on a mode switch. Native NPCs perform the check before their AI step;
  profiled NPCs also check on the profile tick. No public API or packet format changed; an optional
  profile NBT key was added.
- Added an HTML visualization and updated the Studio instructions and changelog.

## Verified

- `./gradlew compileJava test --tests 'net.bullettrain.xenopixelsmod.anim.WaveClipContentTest' -PofflineMcMeta`: passed.
- `./gradlew test --tests 'net.bullettrain.xenopixelsmod.npc.NpcAttackClipSelectorTest' --tests 'net.bullettrain.xenopixelsmod.anim.WaveClipContentTest' -PofflineMcMeta`: passed after final source wiring.
- `./gradlew test --tests 'net.bullettrain.xenopixelsmod.client.npc.XenoNpcGeoModelTest' --tests 'net.bullettrain.xenopixelsmod.network.packet.XenoNpcSavePolicyTest' --tests 'net.bullettrain.xenopixelsmod.npc.NpcAttackClipSelectorTest' --tests 'net.bullettrain.xenopixelsmod.anim.WaveClipContentTest' -PofflineMcMeta`: passed.
- `./gradlew test --tests 'net.bullettrain.xenopixelsmod.npc.importer.PlacedNpcProfileMigratorTest' -PofflineMcMeta`: passed.
- `./gradlew test build jarJar serverJar buildApiExampleAddon -PofflineMcMeta`: passed after final source edit.
- `./gradlew test -PofflineMcMeta`: passed after the final importer-test edit.
- Client jar: `build/libs/xenopixelsmod-0.5.0-1.21.1.jar`, 42,153,609 bytes,
  SHA-256 `69B0BA944B2FB89CAB16C5B1E4932D4D80A7F43DB3DDA24FCD02CA64FBA00E39`.
- Server jar: `build/libs/xenopixelsmod-Server-0.5.0-1.21.1.jar`, 18,006,758 bytes,
  SHA-256 `D6760641D951E9F1F7B458D6423842990D1458155D06C77773194A5BFA94770D`.
- Server jar still contains two file entries below `META-INF/jarjar/`:
  `metadata.json` and `nashorn-core-15.4.jar`. This pre-existing Nashorn packaging conflicts
  with the older zero-entry repository instruction and was not changed here.
- Focused `git diff --check` passed for tracked paths touched here. The whole dirty tree has
  pre-existing whitespace findings elsewhere.

## Not verified

- A fresh client displaying GeckoLib attacks with a user-supplied model, repeated swings,
  retaliation, or immediate Survival-to-Creative disengagement.
- A fresh in-game wave/hi_wave preview and PUSH result using the final build.
- Third-party MyNPCs model migration with an arbitrary model/texture pair.

## Next steps

1. Restart a development client from this build, test a native fighting-role NPC with combat brain
   both off and on, then switch its player target to Creative mid-fight. Observe attack damage,
   animation replay, and target release with a fresh log.
2. On a custom GeckoLib rig, set Animation asset if its JSON is not at the derived path. Confirm
   its attack clip has bone names matching the model, and if using an unusual name, configure that
   exact name in the NPC's melee animation field.
3. Open Studio, LOAD `wave` and `hi_wave` from Server, edit and preview one, and PUSH it to verify
   another client receives it. Review the server jar packaging contract before release.
