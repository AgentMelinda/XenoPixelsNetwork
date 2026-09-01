# Claude handoff — CustomNPCs + DragonMineZ (XenoPixels)

Date: 2026-08-30  
Repo: this worktree, branch `new2`.  
This file is only about the CNPC/DMZ NPC work. Do not treat `CLAUDE_HANDOFF.md` (Sokidan / HUD / 2026-08-12) as current for this.

**Verify in code, not in this document.** `javap` `libs/dragonminez-2.1.3.jar` and CustomNPCs-Unofficial before adding APIs. Last `./gradlew compileJava --offline` after the gold/picker edits **exited 0**. The gold lerp and form-picker fixes were **not** re-checked in `runClient` after that compile.

---

## Hard constraints (still true)

- `StatsData` constructor is **Player-only**. Do not construct it for NPCs.
- `DMZHairLayer` is `AbstractClientPlayer` only. Do not fake a player to attach it.
- `HairRenderer.render` is called with `Character=null`, `StatsData=null`, `AbstractClientPlayer=null`. Physics/sway **does not run** (gated on those + `PHYSICS_ENABLED`). Morph `progress` still works.
- `TransformationsHelper` methods that take `StatsData` are unused for NPCs.
- Form data: `ConfigManager.getForm` / `getFormGroup` / `getAllFormsForRace` and `FormConfig.FormData` only.
- CustomNPCs scripts: no global `npc`. Hooks are `function init(event)` etc. Caster is `event.npc`. `executeCommand` needs command blocks on.
- HEX in **Brigadier** (`executeCommand`): `FF00AA` or `0xFF00AA`. `#` is not a word. `NpcCombatProfile.parseHexColor` also accepts `#RRGGBB` and names (`white`, `black`, `gold`, `ssj`, …).
- Mixins for exploded `runClient`: `[[mixins]]` in `src/main/resources/META-INF/neoforge.mods.toml` (not only the JAR manifest). Compat mixins gated by `ConditionalMixinPlugin` `isModLoaded("customnpcs")` / `cnpcgeckoaddon`.

---

## Network

`ModNetwork.PROTOCOL` is **`"41"`**. Mismatched client/server refuse the channel.

| Ver | What |
|-----|------|
| 38 | `NpcAppearancePacket` hair enabled/code/color |
| 39 | `NpcTransformHoldPacket` (hold target group/form + duration + startGameTime) |
| 40 | appearance hair **code in chunks** (`HAIR_CODE_CHUNK` = 30000). Full-set `DMZF*` codes exceed 32767 UTF. |
| 41 | `NpcAppearancePacket` also carries `strength/strikePower/resistance/vitality/kiPower/energy` so `GuiNpcDmz` can show script-set (`XenoPixels.setProfile`) stats — the client's own `getPersistentData()` copy is never server-synced otherwise. |

Profile NBT `xenopixels:npc_combat_profile` also chunks long hair (`HairCodeChunks` list). `NpcProfileSavePacket` is C2S NBT write of that tag.

---

## What exists (in source)

### Profile / aura / transform

- `NpcCombatProfile` — NBT on the entity. Not a fake `StatsData`.
- `NpcAuraClient` — `kakarot_aura` AFTER_ENTITIES, yaw-only billboard (no lookPitch; that flattened it into a ground fan).
- `NpcTransformSystem` — hold then commit. `canStart` is FormData exists (mastery granted on start). Particles: `MainParticles` AURA/DUST/KI_FLASH/KI_SHEDDING/KI_LIGHTNING, `transform_on`/`off` sounds, ground ROCK+DUST via `NpcKiGroundFx`. Gecko `getTransformationAnimation()` if custom model. Hair morph is client-only via hold packet.
- `NpcFormLookup.groups/forms/step/grantMastery` — live `ConfigManager`. `groups()` is now **sorted**.

### Hair render

- Source of truth for drawing: `NpcAppearanceClient` then profile (`NpcHairVis.spec`).
- Gecko: `NpcDmzHairLayer` on head bone. Steve: `NpcHairClient` `RenderLivingEvent.Post` (skips if gecko `NpcHairBridge.data != null`).
- Transform morph: `NpcTransformHairClient` hold; `HairRenderer.render(from, to, progress, null, null, null, fromRgb, toRgb, …)`. progress 0 = param3, 1 = param4 (from javap).
- Form hair type `ssj`/`ssj2`/`ssj3` indexes the full set. `forcedHairCode` via `HairManager` / `parseSet`.
- Invalid truncated `DMZF*` is cached as empty (stops ZipException spam). Compressed full-set skipped if length &lt; 64 unless `DMZF4:`.
- **Color:** profile Color is **base**. If `FormData.hasHairColorOverride()`, render uses `getRgbHairColor()` (SSJ `#FFE89E` in `previousConfigs/.../supersaiyan.json`). That override used to be skipped when Color was set, so black hair stayed black through SSJ. Current `choose()` applies the form override whenever the form has one. **In-game after this change: not re-verified here.**

### DMZ wand tab

- `GuiNpcDmz` extends `GuiNPCInterface2(npc, 7)`. Tab insert: `GuiNpcMenuDmzTabMixin` after Global **id 6** (not array index). CNPC tab ids (javap `GuiNpcMenu.initGui`): Display=1, Stats=2, AI=3, Inventory=4, Advanced=5, Global=6, **X=0**, Delete=66. DMZ tab id **7**.
- Shell 420×200. Layout is cramped (row 18).
- `GuiTextFieldNop` constructor `setMaxLength(500)` then `setValue`. Hair Code field must `new …("")`, **then** `setMaxLength(262144)`, **then** `setValue`. Paste of full-set codes into the GUI is still a bad idea; use `/xenopixels genhaircode <color> apply` while looking at the NPC.
- `editorProfile()` must **not** copy appearance `formGroup`/`form` into the picker. That was snapping Group/Form to the committed transform when Hair/Aura saved, so Xeno groups looked missing. Hair code/color from appearance is still overlaid if longer/non-blank.
- `pullFromFields` does not read `ID_GROUP`/`ID_FORM` as text fields (those are cycle buttons 30–33).

### Commands (NPC as command source)

See `NpcProfileCommands`. All under `xenopixels npcprofile …` plus `xenopixels npcsay`.  
`/xenopixels genhaircode <color> [preset] [apply]` is **player** OP (`HairCodeCommands`). `apply` raycasts a living entity and writes hair on/code/color via `NpcCombatProfile.write`.

### Script global `XenoPixels`

Installed with `ScriptContainer.Data.put("XenoPixels", INSTANCE)`. Methods in `NpcXenoScriptApi` (version string `"1"`):

`getVersion`, `hasProfile`, `getProfile`, `setProfile`,  
`setKiCharge`, `setAura`, `setAuraColor`, `setKiColor`, `setAuraScale`,  
`setMastery`, `addTechnique`, `removeTechnique`, `listTechniques`,  
`ascend`, `descend`, `descendOne` (`descend` ignores ticks in Java),  
`fireTechnique`, `getTechniqueCooldown`, `isTechniqueReady`, `clearTechniqueCooldown`,  
`getHair`, `setHairEnabled`, `setHairCode`, `setHairColor`.

There is **no** `setForm` on the Java global (command `npcprofile form` exists). `getProfile` returns a Java `Map` — scripts should use `.get("race")`, not assume a JS object.

Example scripts (paste into CNPC Script tab; not in-game tested in this session except as files):

- `examples/customnpcs/xenopixels_ssj_show.js` — black tint, SSJ1→2→3 say, kiblast, descend
- `examples/customnpcs/xenopixels_ecma_full.js`
- `examples/customnpcs/xenopixels_full_saiyan.js`
- `examples/customnpcs/xenopixels_ssj_steps.js`
- `examples/customnpcs/xenopixels_ssj_counter.js`

Ki ids wired in `NpcKiAttackDispatcher`: `kiblast`, `kiwave`/`kihame`/`kamehame`, plus DMZ ids `kamehameha`, `galick_gun`, `final_flash`, `masenko`, `sokidan`, `burning_attack`/`big_bang`, `spiritbomb`, `supernova`, `ki_barrage`, `taiyoken`, `kienzan`/`kienzan_doble`, `death_beam`/`emperor_death_beam`, `makkanko`, `final_explosion`, `soul_punisher`, `fake_moon`, `supernova_cooler`. Other ids only work if `PredefinedTechniques.isPredefinedTechniqueId`.

### Xeno form JSON (datapack)

Under `src/main/resources/data/xenopixelsmod/dmz/races/`:

- saiyan `xenopixels_gods_forms` (`ssg`, `ssb`, `ssbe`, `ssrose`, `ssrose_evolution`, `ui_sign`, `ui`, `ue`)
- saiyan `xenopixels_fan_ss` / `supersaiyan_legend` (`ssj5`–`ssj10`)
- saiyan `xenopixels_saga_forms` (`trunks_ikari`)
- frostdemon `xenopixels_dark_frieza` (`dark`)

Picker uses `ConfigManager.getAllFormsForRace(raceId)`. Race field must be `saiyan` (or `frostdemon`) or those groups are not in the map. Whether the running client’s ConfigManager actually loaded the datapack was **not** re-checked after the picker fix.

---

## Gecko packet crash (already in tree)

`cnpcgeckoaddon` `NetworkWrapper.register` is `@EventBusSubscriber(modid="customnpcs")` so it never ran; `type()` used `minecraft:cnpcgeckoaddonpacketsyncanimation`. Kick: `EncoderException` / `DiscardedPayload`. Fix in tree: `CnpcGeckoPayloads` `playToClient` `cnpcgeckoaddon:sync_animation` and `sync_tile_animation`; mixins on packet `type()` HEAD; gecko `register` cancelled. Do not re-register `minecraft:` names.

---

## What is **not** done / do not claim

- Hair **physics/sway** on NPCs (needs real `AbstractClientPlayer` + `StatsData`).
- GUI paste of a 20k+ `DMZF*` code as the supported path (`apply` command is).
- In-game confirmation of: SSJ gold lerp after the `!force` removal; Xeno groups after appearance overlay removal.
- `StatsData` on NPCs, fake GeckoLib player hair via `DMZHairLayer`, attaching the player aura shader mesh as the NPC aura (NPC uses `kakarot_aura` column).
- `descend(npc, ticks)` does not use `ticks` in `NpcTransformSystem.descend`.

---

## If you continue

1. `runClient` protocol **40**. Look at NPC: `/xenopixels genhaircode black apply`.
2. Confirm SSJ transform: spikes **and** black→gold; Color field still shows black; Descend restores black.
3. Race `saiyan`, Group `>`: `xenopixels_gods_forms` etc. Hair ON must not snap Group back.
4. javap before new DMZ/CNPC calls.
