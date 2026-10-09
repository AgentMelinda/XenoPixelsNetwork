# Handoff — DMZ NPC Stats, Form Studio, and /stack

**Date:** 2026-09-11
**Repository:** `C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen`
**Branch:** `1.21.1`
**HEAD:** `3fa456a20a43c56600a743e5cab332534af6a773` ("Record developer API completion handoff")

## Current state

Nothing was committed, tagged, or pushed. The whole feature lives in the working tree on top of
`3fa456a`, continuing groundwork that Codex had already left uncommitted there.

Dirty paths (`git status --short`, 2026-09-11):

```
 M src/main/java/net/bullettrain/xenopixelsmod/XenoPixelsMod.java
 M src/main/java/net/bullettrain/xenopixelsmod/client/ClientPacketHandlers.java
 M src/main/java/net/bullettrain/xenopixelsmod/client/compat/npc/gui/GuiNpcDmz.java
 M src/main/java/net/bullettrain/xenopixelsmod/client/compat/npc/mynpcs/gui/GuiNpcDmz.java
 M src/main/java/net/bullettrain/xenopixelsmod/command/XenoPermissions.java
 M src/main/java/net/bullettrain/xenopixelsmod/compat/npc/NpcCombatProfile.java
 M src/main/java/net/bullettrain/xenopixelsmod/compat/npc/NpcFormLookup.java
 M src/main/java/net/bullettrain/xenopixelsmod/mixin/compat/customnpcs/GuiNpcStatsDmzAuthorityMixin.java
 M src/main/java/net/bullettrain/xenopixelsmod/mixin/compat/mynpcs/GuiNpcStatsDmzAuthorityMixin.java
 M src/main/resources/META-INF/neoforge.mods.toml
 M src/main/resources/xenopixelsmod.compat.mixins.json
 M src/main/resources/xenopixelsmod.mixins.json
 M src/test/java/net/bullettrain/xenopixelsmod/compat/npc/NpcCombatProfileKiWeaponTest.java
?? 9mm_mafioner_version_C_fixed_320.mp3                     (pre-existing, untouched)
?? src/main/java/net/bullettrain/xenopixelsmod/client/compat/npc/gui/GuiNpcDmz{Forms,FormEditor,TrainerPicker}.java
?? src/main/java/net/bullettrain/xenopixelsmod/client/compat/npc/mynpcs/gui/GuiNpcDmz{Forms,FormEditor,TrainerPicker}.java
?? src/main/java/net/bullettrain/xenopixelsmod/client/screen/DmzFormTrainerScreen.java
?? src/main/java/net/bullettrain/xenopixelsmod/command/StackCommands.java
?? src/main/java/net/bullettrain/xenopixelsmod/dmz/form/
?? src/main/java/net/bullettrain/xenopixelsmod/mixin/client/ClientLanguageFormNameMixin.java
?? src/main/java/net/bullettrain/xenopixelsmod/mixin/compat/dmz/DmzForm{SelectMetadata,TypeIcon}Mixin.java
?? src/main/java/net/bullettrain/xenopixelsmod/mixin/compat/dmz/DmzSkillsFormTypeIconMixin.java
?? src/main/java/net/bullettrain/xenopixelsmod/network/form/
?? src/main/resources/assets/xenopixelsmod/textures/gui/{icons,radial}/
?? scripts/diagnose_mod_cycles.py
?? scripts/mirror_mynpcs_form_studio.py
?? src/test/java/net/bullettrain/xenopixelsmod/ModMetadataOrderingTest.java
?? src/test/java/net/bullettrain/xenopixelsmod/command/StackCommandsTest.java
?? src/test/java/net/bullettrain/xenopixelsmod/dmz/form/
```

**Most of this feature is untracked.** Git cannot recover an untracked file that is deleted, so
nothing here should be cleaned, stashed, or reset before it is committed.

No process was left running. Dependency pin verified this session:
`libs/dragonminez-2.1.3.jar` SHA-256 `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`,
matching `dragonminez_sha256` in `gradle.properties`.

## Changes

No commits were made, so the units below are described by path.

### Stats tab (`mixin/compat/{customnpcs,mynpcs}/GuiNpcStatsDmzAuthorityMixin`)

The three authoritative DMZ values use `setEditable(false)` instead of `enabled = false`, so they
stay rendered and keep their `(DMZ)` labels. A KI Weapon Yes/No button sits in the unused lower
Stats area. Its payload is built by the new `NpcCombatProfile.withKiWeapon(CompoundTag, boolean)`,
which decodes the NPC's complete current profile and flips only that one flag — the earlier code
already read the live profile, but the helper makes the contract explicit and testable. The DMZ →
Customize toggle stays in step because the mixin calls `NpcAppearanceClient.applyProfile` and
`GuiNpcDmz.editorProfile()` overlays that state through `applyVisualOptions`.

### Form Studio (`dmz/form/**`, `client/compat/npc/**/gui/GuiNpcDmz{Forms,FormEditor,TrainerPicker}`)

Reached from a `Forms` button inside the existing DMZ tab; no new native top-level tab.

- `DmzFormDocument` holds the raw Gson tree of a `FormConfig`, so every field dragonminez-2.1.3
  serialises is editable without mirroring the schema. It now classifies each field
  (`TEXT`/`COLOR`/`BOOL`/`NUMBER`/`JSON`/`SCALE`), flattens `outlineShader` one level so its two
  colours get pickers, replaces the raw `modelScaling` array with a uniform control plus one field
  per axis, carries group display names, and exposes `identityError()` so a draft stays local
  until its race/group/form/formType ids validate and are not already owned by the studio.
- The editor gives colour fields a `Pick` button (reusing the existing `NpcColorPicker`) plus a
  live swatch, and still accepts typed hex.
- Saving is server-authoritative. `DmzFormSaveGate` coalesces keystrokes (600 ms) and refuses to
  send while a save is in flight; the editor advances its revision only to the value the server
  acknowledged, so a rejected save can be retried instead of wedging the session. Toggles and
  colour picks save immediately; typed fields save on focus loss or after the debounce.
- Preview drafts go through `DmzFormPreviewOverrides`, which now has a per-kind `clear`, so a
  stack draft can be previewed on top of a normal-form draft.
- The group/form pickers have search boxes backed by `DmzFormSearch.filter`.
- The trainer picker shares the editor's session id, so one editing session produces one backup
  per affected file rather than two.

### Transaction and validation (`dmz/form/DmzFormEditorService`)

Already transactional (remember → backup → temp file → atomic move, with multi-file rollback).
Changes this session:

- Item, item-tag and mob-effect ids are validated as `ResourceLocation`s before they reach a DMZ
  config, instead of only failing later at transform time.
- Ids must now start with `[a-z0-9]` and may not contain `..`, so `..` can never be an id.
- Locale maps are normalised in one place for both form and group names (`xx_yy` only, trimmed,
  ≤128 chars, ≤64 locales, mandatory `en_us`).
- The per-session backup folder name includes the session id; two sessions starting inside the
  same millisecond previously collided on one folder and the second `Files.copy` would have
  thrown.
- Pure JSON transforms (`patchSkillsJson`, `patchRaceCharacterJson`) and root-parameterised path
  helpers were split out from the FML-dependent wrappers so they are unit-testable.

### Localization (`mixin/client/ClientLanguageFormNameMixin`, `DmzFormMetadataRegistry`)

The registry keeps a translation-key index and `translate(key, locale)` resolving current locale →
`en_us` → `null`. The new client mixin answers `ClientLanguage#getOrDefault` / `#has` for keys the
studio owns. That single hook covers every DMZ screen that builds these keys, which the existing
`FormSelectNode` mixin alone did not. Both are kept; they are complementary, not competing.

### Icons, network, `/stack`

Icon resolution (per-form → form-type → DMZ native → nothing) and the three DMZ mixins are Codex's
groundwork, verified rather than rewritten. `/stack` is likewise groundwork; `StackCommands.register`
was split out of the event handler so the parse tree can be tested.

### Mod-sort cycle at boot (`META-INF/neoforge.mods.toml`)

A launch on 2026-09-11 aborted in `ModSorter` before any mod loaded, reporting three cycles:
`[xenopixelsmod, dragonminez, dmzcustomforms]`, `[architectury, dragonlib]` and
`[ponder, sable, create, colorwheel, flywheel]`. Only the first involves this mod.

Facts established against fancymodloader 4.0.x source and the pinned DMZ jar:

- `ModSorter#addDependency` turns `ordering="AFTER"` into an edge *dependency to owner* and
  `BEFORE` into *owner to dependency*; `NONE` adds none, and a dependency on an absent mod adds
  none.
- `ModInfo` builds its dependency list from `getConfigList("dependencies", this.modId)`, so a jar
  can only declare ordering for mods it actually ships. No third-party jar can inject an edge
  between two other mods.
- DragonMineZ 2.1.3 declares ordering only toward `geckolib`, `terrablender` and `curios` - never
  toward `xenopixelsmod` or `dmzcustomforms`.
- Therefore the only edge in cycle 1 that this repository owns is the one from our
  `ordering="AFTER" dragonminez`; the other two edges must come from `dmzcustomforms` 0.4.0's own
  mods.toml (it has to be declaring itself after this mod and ahead of DragonMineZ).

That dependency is now `ordering="NONE"`, still `type="required"`. Nothing needed the edge: both
couplings are ordered by FML's phase structure rather than by mod sort order - `ConfigManager
.initialize()` runs in DragonMineZ's *constructor* while this mod's constructor reads no DMZ
config, and `ModCommonEvents.commonSetup` runs `new PredefinedTechniques().init()` synchronously
during parallel dispatch while `XenoRushTechniques`/`XenoSlotTechniques` register from inside
`FMLCommonSetupEvent#enqueueWork`, which FML drains only after that phase ends.

`ModMetadataOrderingTest` locks this in: no dependency may use `BEFORE`, `dragonminez` must stay
unordered, and the remaining `AFTER` list is asserted exactly.

`scripts/diagnose_mod_cycles.py` rebuilds the same graph offline for a mods folder and prints the
exact declaration behind every cycle edge, plus mod ids shipped by more than one file.

### Compatibility surface

- `src/main/java/net/bullettrain/xenopixelsmod/api/**` — unchanged (`git status` clean).
- `ModNetwork` — unchanged; still protocol `63` with sequential registration.
- Form-editor traffic uses the separate `xenopixelsmod:form_editor` channel at protocol `2`.
- Third-party DMZ patches remain additive; no delete or overwrite semantics were added.

## Verified — trainer purchase routing (2026-09-11)

The DMZ skill-master purchase path is wired end to end and re-verified this session on top of
`3fa456a20a43c56600a743e5cab332534af6a773` (branch `1.21.1`):

- Client affordance: `DmzFormTrainerScreen.mouseClicked` calls
  `FormEditorNetwork.purchase(trainerEntityId, entry.formType())` for the clicked row.
- `FormEditorNetwork.TrainerPurchasePacket.handle` runs `purchase(ServerPlayer, packet)` on the
  main thread; that handler re-resolves the trainer by entity id, requires
  `NpcCounterpartSync.isCustomNpc`, enforces an 8-block range, re-matches the form type against
  `DmzFormMetadataRegistry.trainerOfferings(trainer.getUUID())`, and re-evaluates
  `DmzTrainerPurchase.evaluate(metadata, race, currentLevel, trainingPoints)` server-side.
  On success it removes the TP, sets the next skill level, calls
  `updateTransformationSkillLimits(race)`, and broadcasts `StatsSyncS2C` + `ProgressionSyncS2C`;
  on denial it only messages the player. No client-supplied cost or level is trusted.
- `DmzTrainerPurchaseTest` covers the denial matrix (price-by-level, insufficient TP, race lock,
  `buyFromMaster`, configured maximum, master-learning off, null metadata) plus trainer offering
  selection.

Exact commands, all run on 2026-09-11 from the repository root:

- `./gradlew compileJava -PofflineMcMeta` — BUILD SUCCESSFUL.
- `./gradlew test -PofflineMcMeta` — BUILD SUCCESSFUL. 97 test classes, **551 tests, 0 failures,
  0 errors, 0 skipped** (counted from `build/test-results/test/TEST-*.xml`). 67 of those are the
  focused form-studio / `/stack` / KI-weapon tests.
- `./gradlew build jarJar serverJar -PofflineMcMeta` — BUILD SUCCESSFUL.
  - `build/libs/xenopixelsmod-Server-0.3.5-1.21.1.jar` — 9,101,531 bytes, SHA-256
    `ae9a85551c02bad7554dd8e939545b34e168d367dd90cc12818e1845008076e1`,
    **0 entries under `META-INF/jarjar/`**.
  - `build/libs/xenopixelsmod-0.3.5-1.21.1.jar` — 33,248,759 bytes, SHA-256
    `ca10e6074252a6fef63dfd8f06c2e62a5c23b551fd0aa540bd89f658e542a7c6` (3 jarjar entries, expected
    for the client artifact).
- `./gradlew buildApiExampleAddon -PofflineMcMeta` — BUILD SUCCESSFUL; published API boundary
  unchanged.

Bytecode/source verification against the pinned `libs/dragonminez-2.1.3.jar`:

- `FormSelectNode` — fields `race`/`group`/`form`/`stack` are `private final`; `label(StatsData)`
  and `icon(StatsData)` exist. Matches `DmzFormSelectMetadataMixin`.
- `AbstractRadialNode.iconForFormType(String)` exists as `protected static`.
- `SkillsMenuScreen.renderFormsTree(GuiGraphics,int,int)` exists and contains **exactly one**
  `ResourceLocation.fromNamespaceAndPath` call, building `dragonminez` +
  `textures/gui/icons/<lowercase formType>.png` (bootstrap-method constant
  `textures/gui/icons/\u0001.png`). `require = 1` on the `@Redirect` is therefore satisfiable.
- `ClientLanguage` declares `getOrDefault(String,String)` and `has(String)` (checked with `javap`
  on the NeoForge 21.1.248 neoform output).
- Config path keys `skills`, `races/<race>/character`, `races/<race>/forms/<group>` and
  `forms/<group>` match `ConfigManager.reloadSpecificConfig`; `CONFIG_DIR` is
  `FMLPaths.CONFIGDIR.get().resolve("dragonminez")`.
- `SkillsConfig` serialises `formSkills`, `stackSkills`, `skills.<id>.{costs,allowedRaces}` and
  `skillOfferings.<master>` with `default` as a real master key.
- `RaceCharacterConfig.FormSkillCost.Adapter` serialises `{"buyFromMaster":bool,"prices":[int]}` —
  the shape `patchRaceCharacterJson` writes.
- DMZ's label key shapes are `race.dragonminez.<race>.form.<group>.<form>`,
  `race.dragonminez.stack.form.<group>.<form>` and the matching `.group.` forms.

Re-run 2026-09-11 (post-purchase work, current working tree):

- `./gradlew test -PofflineMcMeta` — BUILD SUCCESSFUL in 58s. 103 test classes, **585 tests,
0 failures, 0 errors** (counted from `build/test-results/test/TEST-*.xml`).
- `./gradlew build jarJar serverJar -PofflineMcMeta` — BUILD SUCCESSFUL in 26s.
`build/libs/xenopixelsmod-Server-0.3.5-1.21.1.jar` — 9,160,169 bytes, SHA-256
`db3cbc48fa46f66b0568f0f7c70283858fb6bcb9973ee4d2d320440f4f6d6f10`, **0 entries under
`META-INF/jarjar/`**.
- `./gradlew buildApiExampleAddon -PofflineMcMeta` — BUILD SUCCESSFUL; published API boundary
unchanged.

Icons: all 50 files exist in both `textures/gui/icons/` and `textures/gui/radial/`, all 64×64 RGBA
PNGs, all 100 files byte-distinct, all with both transparent and opaque pixels. A 10×5 contact
sheet was rendered and visually inspected — the glyphs are distinct neon energy marks and read
clearly at icon size.

## Not verified

Nothing below was exercised in a running game; all of it is **manual pending**.

- Every client runtime check: visible DMZ Stats values, KI toggle persistence across screen
  reopen and relog, the Forms button, live form editing, normal/stack preview, hot reload, and
  second-client synchronisation. The 2026-09-11 client reached a loaded world but no screen was
  opened and no gameplay action performed.
- The three `compat.dmz` mixins. `FormSelectNode`, `AbstractRadialNode` and `SkillsMenuScreen`
  only class-load when the radial or the skills screen is opened, which did not happen, so none
  of them has been applied yet. They now carry `require = 1` (added 2026-09-11) so a target
  mismatch fails loudly instead of silently dropping custom names and icons - previously
  `xenopixelsmod.compat.mixins.json`'s `defaultRequire: 0` would have let them no-op in silence.
- Gameplay: native-master learning, CustomNPC/MyNPC trainer learning and TP deduction, and all
  `/stack player|npc on|off|toggle` variants against live DragonMineZ stats.
- MyNPCs parity. The three MyNPCs screens are generated from their CustomNPCs originals by
  `scripts/mirror_mynpcs_form_studio.py` (package + `noppes` → `espi` swap only) and compile, but MyNPCs
  was not loaded in a run.
- The transactional `save()` entry point itself: its pure pieces (validation, JSON patching, path
  construction, backup, atomic write, rollback) are covered by tests, but the full method needs
  `FMLPaths` and a `ServerPlayer`, so the revision-conflict rejection and the reload-failure
  rollback path have not been observed end to end.
- Colour-picker sub-screen layout and the new search rows were never rendered; the widget
  coordinates are reasoned about against the 420×200 `GuiNPCInterface2` shell, not seen.

## Startup verified (dev client, 2026-09-11)

`./gradlew runClient` - **no `-PofflineMcMeta`**. The vendored Minecraft metadata descriptor that
flag selects carries no LWJGL natives variant (see the comment at `build.gradle:109` and the rule
already written in `ai/validation.md`), so two launches with it died at
`UnsatisfiedLinkError: Failed to locate library: lwjgl.dll` inside Sodium's graphics probe before
any mod loaded. That was a wrong flag, not a code fault.

Fresh `run/logs/latest.log`, first line `[11Sep2026 03:47:49.425]`:

- `03:48:16.025` `ModNetwork: registered 40 packet types (protocol 63)` - the main channel is
  intact and the separate `xenopixelsmod:form_editor` channel registered beside it without a
  duplicate-id or "Invalid message" failure.
- `03:47:55.743` `ConditionalMixinPlugin ... (customnpcs=false, mynpcs=true)` - compat gating
  works; the MyNPCs screens are the live ones in this run and the CustomNPCs mixins are skipped.
- `03:48:27.976` `Sound engine started`, then a full world load: `ServerLevel[New World]` plus
  `dragonminez:namek` and `dragonminez:time_chamber`.
- No mixin failure attributable to this mod. Every `Error loading class` / `was not found` /
  `InvalidMixinException` line belongs to controlify, mekanism, JEI, Immersive Engineering or
  Sodium's own optional targets.
- `ClientLanguageFormNameMixin` necessarily applied: it lives in `xenopixelsmod.mixins.json`,
  which is `"required": true` with `injectors.defaultRequire = 1`, and both its injections
  declare `require = 1`. `ClientLanguage` is loaded during the startup language reload, so a
  failed match there would have aborted the launch instead of reaching a world.
- `DmzContentBootstrap` ran normally: backup to
  `run/config/dragonminez/.xenopixels-backups/20260911-034845`, four form files installed, race
  `character.json` TP prices patched, `skills.json` patched, DMZ configs reloaded.

Known unrelated noise in the same log, recorded so it is not mistaken for new breakage: a
controlify `InvalidMixinException` on `AbstractRecipeBookScreenMixin`, and this mod's own
pre-existing `XenoIdentifierDiagnostics` WARN (a synthetic trace, not a thrown error) fired by
MyNPCs passing an empty resource path from `Model2DRenderer.init`.

### Interaction with the existing form installer

`DmzContentBootstrap` already installs XenoPixels forms and patches `skills.json`,
`skillOfferings` and per-race `formSkillsCosts` on every server start - the same files
`DmzFormEditorService` writes. Its `formSkills` handling is purely additive (it only drops ids in
its own `formSkillsRemove` patch list or the fixed `LEGACY_FORM_SKILLS` list), so form types
created in the Form Studio survive a restart. The two subsystems overlap by design and should be
reconciled deliberately rather than left to coincidence.

## Still blocking a boot

Fixing our edge removes cycle 1 only. `[architectury, dragonlib]` and
`[ponder, sable, create, colorwheel, flywheel]` are between third-party mods, and FML aborts on
any cycle, so the instance will keep failing until one of those jars is changed or removed.
`dependencyOverrides` in `config/fml.toml` cannot help: a `-dep` entry only drops a version or
incompatibility constraint, and `+dep` only adds another AFTER edge.

The crashing instance was not located on this machine. Every mods folder under the user profile
was scanned and none contains `dmzcustomforms`. Notably
`ModrinthApp/profiles/Forge 1.20.1/mods` holds the same versions of architectury, dragonlib,
create 6.0.10, flywheel 1.0.6, ponder 1.0.82, colorwheel 1.2.9 and sable 2.0.3 and sorts with no
cycle at all, so cycles 2 and 3 come from something extra in the crashing set.

Next action for those two: run
`python scripts/diagnose_mod_cycles.py "<crashing instance>/mods"`, which names the offending
declarations directly.

## Known limitations worth deciding on

- `SkillsConfig.formSkills` and `stackSkills` are annotated `@ConfigNonPreservable` in
  dragonminez-2.1.3. A DragonMineZ config-version upgrade regenerates those lists from defaults,
  which would drop custom form types registered by the studio. Re-saving each form restores them,
  but an explicit re-registration pass on load would be better.
- The icon set spans more hues than the blue/magenta/cyan/gold the plan named — there are clearly
  green and orange/red glyphs in the sheet as well.

## Next steps

1. Commit the reviewed paths explicitly — never `git add -A` — starting with the untracked
   `dmz/form/`, `network/form/`, texture and test trees, which git cannot recover if lost.
2. Launch a dev client with DragonMineZ, CustomNPCs and MyNPCs, and work the manual list above,
   checking a fresh `run/logs/latest.log` for mixin-apply failures on
   `ClientLanguageFormNameMixin` and `DmzSkillsFormTypeIconMixin` before trusting either.
3. Create one normal and one stack form end to end, confirm the names appear in the radial, the
   Skills tree, the Character screen and the quest tree, then relog a second client and confirm
   it sees the same names and icons with no resource pack.
4. Exercise each `/stack` variant and each trainer path, then update this file's "Not verified"
   list with what actually ran.
5. Decide on the `@ConfigNonPreservable` re-registration pass before shipping to a live server.
