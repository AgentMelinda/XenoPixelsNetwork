# New race id registration path evidence (PR-D6a / KD15 r3)

**Date:** 2026-10-03  
**Scope:** Inventory how DragonMineZ 2.1.3 discovers races and whether a **new** race id can be
registered by writing config files (no invented registrar).  
**Pinned stack:** Minecraft 1.21.1, NeoForge 21.1.248, DragonMineZ 2.1.3
(`libs/dragonminez-2.1.3.jar`, SHA-256 matches `dragonminez_sha256` in `gradle.properties`:
`5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`).

Cross-links: design §D.3 / KD15 r3 / PR-D6a in
`docs/superpowers/specs/2026-10-03-hd-aura-tournament-makers-design.md`; plan Task 7 in
`docs/superpowers/plans/2026-10-03-tournament-roles-makers-phase2.md`.

## Method

1. `jar tf` + `javap -public/-p` on `libs/dragonminez-2.1.3.jar` for
   `com.dragonminez.common.config.ConfigManager` and `RaceCharacterConfig`.
2. Read decompiled
   `tools/generated/dmz_decompiled_full/com/dragonminez/common/config/ConfigManager.java`
   (`loadAllRaces`, `createOrLoadRace`, `createDefaultCharacterConfig`, `setupDefaultCharacter`)
   and `RaceSelectionScreen#getAvailableRaces`.
3. Compared in-repo bootstrap: `DmzContentBootstrap` installs form JSON under **existing**
   races and patches `character.json` prices — not a race registrar.
4. Confirmed jar bytecode contains the string `Custom race detected: {}` on
   `ConfigManager.lambda$loadAllRaces$7`.

**Not claimed:** live-process proof that a freshly created folder appears in Race Selection
on a running client. This appendix is jar + decompiled + in-repo citation only.

## Discovery model

| Mechanism | Citation | Role |
| --- | --- | --- |
| Hardcoded defaults | `ConfigManager.DEFAULT_RACES` = `{human, saiyan, namekian, frostdemon, bioandroid, majin}` (`javap` field; decompiled `:64`) | Always loaded first via `createOrLoadRace(name, true)` |
| **Folder scan (custom races)** | `ConfigManager#loadAllRaces` (`:973-998`); jar string `Custom race detected: {}` | After defaults, `Files.list(RACES_DIR)` — every **directory** under `config/dragonminez/races/` whose name is **not** a default race is loaded with `createOrLoadRace(name, false)` |
| Race dir layout | `ConfigManager#createOrLoadRace` (`:785-889`) | Ensures `races/<id>/`, `forms/`, loads/creates `character.json` + `stats.json`, merges form JSON from `forms/` |
| Custom default character | `createDefaultCharacterConfig(name, false)` → `setupDefaultCharacter` (`:1042-1044`, `:1191-1212`) | `useVanillaSkin=true`, `isLayered=true`, `racialSkill="human"`, empty form skill price lists |
| UI list | `RaceSelectionScreen#getAvailableRaces` → `ConfigManager.getLoadedRaces()` (`RaceSelectionScreen.java:80-82`) | Selection UI uses **loaded** races, not defaults-only |
| Reload hook | `ConfigManager#reload` → `loadAllRaces` (`javap` public `reload`) | Same scan path as init; used by `DmzContentBootstrap.reloadDmzConfigs` after form installs |

`RACES_DIR` = `FMLPaths.CONFIGDIR` / `dragonminez` / `races` (decompiled `:61-63`).

### Exact citations

```text
ConfigManager#loadAllRaces
  tools/generated/dmz_decompiled_full/com/dragonminez/common/config/ConfigManager.java:973-998
  jar: com/dragonminez/common/config/ConfigManager.class
  bytecode: ldc "Custom race detected: {}"

ConfigManager#createOrLoadRace
  …/ConfigManager.java:785-889

ConfigManager#setupDefaultCharacter
  …/ConfigManager.java:1191-1212

RaceCharacterConfig fields / FormSkillCost.Adapter
  …/RaceCharacterConfig.java (CURRENT_VERSION "2.1.3"; buyFromMaster+prices object shape)

RaceSelectionScreen#getAvailableRaces
  …/client/gui/character/RaceSelectionScreen.java:80-82

DmzContentBootstrap (form install only — not a race registrar)
  src/main/java/net/bullettrain/xenopixelsmod/dmz/DmzContentBootstrap.java
  BUNDLED_FORMS under races/<known>/forms/; PRICED_RACES patches character.json prices
```

## What is **not** required / not invented

- No public `ConfigManager.registerRace(…)` API exists (`javap -public`).
- Do **not** invent a Xeno race registrar; registration **is** the folder scan in `loadAllRaces`.
- `DmzContentBootstrap` need not grow a hardcoded custom-race install list for create; writing
  `config/dragonminez/races/<id>/` is the install step. Bootstrap continues to install form
  groups into known races only.
- Full geo/texture authoring for non–vanilla-skin races is out of scope for this gate; custom
  races inherit `setupDefaultCharacter` (human-like vanilla skin) until makers supply more.

## READY create / install steps

1. Validate race id (`[a-z0-9][a-z0-9_.-]*`, no `..`; reject default race ids).
2. Create `config/dragonminez/races/<id>/character.json` using the `RaceCharacterConfig` field
   set mirrored from `setupDefaultCharacter` (`configVersion` `"2.1.3"`,
   `formSkillsCosts` as `{buyFromMaster, prices}` objects per `FormSkillCost.Adapter`).
3. Create empty `config/dragonminez/races/<id>/forms/` (DMZ also creates this on load).
4. Call **`ConfigManager.reload()`** so `loadAllRaces` logs `Custom race detected: <id>` and
   adds the race to `LOADED_RACES` (also creates `stats.json` defaults if missing).
5. Optional later: lang `race.dragonminez.<id>` / textures — not required for registration.

Implemented by `net.bullettrain.xenopixelsmod.dmz.race.RacePackService` (filesystem skeleton;
reload left to caller / server bootstrap that already calls `ConfigManager.reload`).

## Verdict

New race id registration: **READY**

If READY: create/install steps above (files under `config/dragonminez/races/<id>/` +
`ConfigManager#reload` → `loadAllRaces` scan). Create Race UI may call `RacePackService`.

**Runtime unverified** on a live client Race Selection pass.
