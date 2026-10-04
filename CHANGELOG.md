# Changelog

## Unreleased

## 0.5.8-1.21.1 — 2026-10-04

- Fix Taotto dragging being pinned by transparent canvas margins. Placement bounds now
  follow the painted area, with negative offsets supported and oversized paint allowed to pan.
- New tattoo canvases start fitted to the torso. **Fit paint** centers and scales the painted
  area on the selected part; scaling preserves its center. The placement panel is centered
  and shows a selection outline for movement between skin pixels.
- Fix fractional-scale paint disappearing at the bottom/right placement boundary and ignore
  horizontal-only wheel events. Interactive feel remains unverified in game.

## 0.5.7-1.21.1 — 2026-10-04

- Race Maker: per-race hair catalog and authored body types. Hair Editor **Save Pack** writes
  CustomHair JSON under `config/dragonminez/races/<id>/catalog/`. **Add Body** writes a
  64×64 layered PNG and copies it into a generated `dragonminez`-namespace resource pack
  so `TextureCounter` can count it. Tattoo is its own category (DMZ presets + Taotto).
- Taotto: per-pixel tattoo painter (`/xenomaker taotto`). Scale, body-part select, and drag
  place an additive overlay. DMZ `tattooType` is unchanged. **Not verified in a running game.**
- Live maker previews use the selected race and a temporary unsaved form definition; race,
  hair, and transformation state are restored after drawing. Taotto drafts update before Apply.
- DMZ character creation's Hair tab can cycle the race's authored hair catalog. Generated body
  textures use the race's own namespace and reload resource packs after Add Body.
- Form Maker: set each DragonMineZ form stat multiplier that exists on FormData (STR, SKP, STM,
  DEF, VIT, PWR, ENE, SPD). HUD RES stays (DEF+STM)/2; no invented `resMultiplier`.

## 0.5.6-1.21.1 — 2026-10-04

- HD aura **v4**: live follow no longer treats `aura4` as a silhouette. The smoke wall and inner
  shell sit on the v1 box (drop 0.44) like `aura_out_*` / `aura_in_*`. Spikes stay on aura3.

## 0.5.5-1.21.1 — 2026-10-04

- HD aura **v4** plume: `aura4` plays OuterA + Edge smoke again (the v1 wall), with normal blend,
  one column instead of OuterA+OuterB, no DustRing, slower spawn and smaller sprites. Still does
  not play dense `aura_out_*`. v1–v3 unchanged. **FPS not verified in a running game.**

## 0.5.4-1.21.1 — 2026-10-04

- HD aura **v4** (new client default): v3 spiked silhouette + punch edges + sparking, plus a
  **lean** fire column instead of the dense v1 outer billow (`aura_out_*` OuterA/OuterB/Edge
  smoke puffs). That billow was the FPS drop in built areas. `/xenoaura v1|v2|v3|v4`. v1–v3
  keep their old look. No combat/entity hitbox changes. **FPS not verified in a running game.**

## 0.5.3-1.21.1 — 2026-10-04

- Race Maker Display name and Description fields. Custom packs store those literals in
  `config/dragonminez/races/<id>/xeno_labels.json` (not `en_us.json`). The language mixin
  serves `race.dragonminez.<id>` and `.desc` so the DMZ race picker shows the name.
- Server: `/xenorace name <id> <display name>`, `/xenorace desc <id> <description>`,
  `/xenorace give <players> <id>`, `/xenorace list`. Labels sync to clients on login.

## 0.5.2-1.21.1 — 2026-10-04

- Race Character Maker studio: 22px gutter pagers, category-scoped colours
  (Body Skin/Skin2/Skin3, Eyes Eye1/Eye2, Hair, Aura), seven-channel live preview,
  merge-save for custom packs, and world HUD hidden while `/xenomaker` is open.
- CI: compile and package `jar` + `jarJar` + `serverJar` without the full `build`
  test gate. That gate currently requires untracked generator sources. Tag `v*.*.*`
  still publishes GitHub Releases.

## 0.5.1-1.21.1 — 2026-10-04

- Unified Maker Studio (`/xenomaker`): Race Character Maker, Form Maker, Hair Studio.
  Race Maker Gender / Category / Race cyclers no longer share pixels. Created custom
  races write `config/dragonminez/races/<id>/character.json`, show on the Race cycler
  and green cards (`*`), load into the editor, and Save overwrites the pack.
  Form Maker lists custom races first; Hair Studio compact chrome and live preview.
- HD aura Motif Phase 1, tournament/roles Track A, and the rest of the unreleased
  2026-10-03 maker work below.

## Unreleased — NPC saga combat and quest parity

- 2026-10-03: Advanced Hair Editor (PR-D7, KD15 r3 Images 5–6). Client `HairMakerScreen`
  supersedes the text-summary shell under `/xenomaker` / `/xenomaker hair` and `/xenohairui`.
  Gold `banner_top` chrome; green Task-9 panels `xeno_maker_form_list` (style list:
  Default Hair Style / Super Saiyan / …), `xeno_maker_form_settings` (length / curve /
  scale / rotation + Hair Color / Extra Color), `xeno_maker_hair_preview` with true-player
  `MakerPreviewController` (`GlowTarget.HAIR_SEGMENT`, `markDirty` ≤50 ms). `AtlasCycle` for
  face / strand / DMZ creation hair presets (`MakerPresetCatalog` live or FALLBACK).
  Connected = parenting label only (no boolean). Export `xenopixels.hair.export.v1`; Apply
  via existing `HairApplyService` → `UpdateCustomHairC2S#handle`
  (`path_ready_runtime_unverified`). Units: `HairMakerAtlasTest`, `HairApplyServiceTest`,
  `HairCodecVectorsTest` / fixtures. **Not verified in a running game.**
- 2026-10-03: Form Maker (PR-D6e, KD15 r3 Images 2–4). Client `FormMakerScreen` +
  `FormMakerPartsScreen` under `/xenomaker` / `/xenomaker forms` (hub Forms button). Gold
  `banner_top` chrome; green Task-9 panels `xeno_maker_form_list` / `form_settings` /
  `preview_sm`. Form list glow (`GlowTarget.FORM_ROW`); Form Settings from verified
  `DmzFormDocument` keys only (Aura Color/Type, Form Type, Model Scale, Race, Transformation
  Animation, Hair Color — no invented schema keys; unknown fields ignored). Dual preview:
  aura/form summary + full-body `MakerPreviewController` (`markDirty` ≤50 ms). Parts
  sub-screen reuses RaceMakerParts-style categories over verified FormData appearance
  keys. Save via existing `FormEditorNetwork.save`. `/xenoraceformui` unchanged. Units:
  `FormMakerAtlasTest`. **Not verified in a running game.**
- 2026-10-03: Unified Maker Studio chrome upgrade (owner). Gold frames/banners for hub +
  Race editor title (`banner_top` / Theme.GOLD); green Task-9 maker panels stay green
  inside Race. Advanced controls: `AtlasCycle` (category + presets + gender),
  `ColorSwatch` + `InlineColorPicker` for Skin/Eyes/Hair (preview-local only — no invented
  DMZ colour write-back). `MakerPresetCatalog.labels` — live from `RaceMakerParts` /
  `TextureCounter` / `HairManager` when READY; documented FALLBACK (Body 1–8, Eyes 1–6,
  Hair Default/SSJ-style names, etc.) when counts are 0; Aura/Clothes stay empty. Units:
  `MakerPresetCatalogTest`, `RaceCharacterMakerAtlasTest`. **Not verified in a running game.**
- 2026-10-03: Race Character Maker (PR-D6d, KD15 r3 Image 1). Client
  `RaceCharacterMakerScreen` + hub `XenoMakerHubScreen` under `/xenomaker` (+ `race` /
  `forms` / `hair`). Green atlas race cards / category column / part grid / full-body
  `MakerPreviewController` (markDirty on change; PART_CATEGORY / RACE_CARD glow). Part
  indices from `NpcAppearanceParts` / `TextureCounter` + `HairManager.getPresetCount`
  via `RaceMakerParts`; Aura/Clothes grids empty with citation gaps. Create Race enabled
  (Task 7 READY) via `RacePackService` then `ConfigManager.reload()`. Permissions
  `MAKER_OPEN` / `MAKER_RACE_CREATE`. Hair hub button stubs to Task 12; Forms is Task 11
  Form Maker. Existing `/xenoraceformui` and `/xenohairui` unchanged. Units:
  `RaceCharacterMakerAtlasTest`. **Not verified in a running game.**
- 2026-10-03: Maker preview controller (PR-D6b, KD20). Evidence
  `docs/superpowers/evidence/2026-10-03-maker-preview-player-model.md` Verdict **READY** —
  true local-player DMZ model via `EntityPreviewRenderContext#renderEntityInInventory`
  (→ `InventoryScreen#renderEntityInInventory`), same path as DMZ
  `HairEditorScreen#renderPlayerModel` (~L1091) and in-repo `CharacterPortraitCache` /
  `XenoNeonStatsScreen`. `MakerPreviewController` binds `Minecraft.player`, glow target+id
  (green rect chrome), `markDirty` → `PreviewDebounce` ≤50 ms. Units:
  `PreviewDebounceTest.firesAfterFiftyMs`, `MakerPreviewControllerTest`. **Not verified in a
  running game.**
- 2026-10-03: HD-only blank-body fix — cancel DMZ only while `replacesDmzAura()` (HD healthy);
  if HD fails this session the classic sheet returns. Local aura-active marks SEEN in TP too;
  HD-only `plays()` includes recent sightings when second-aura is off. **Not verified in a running game.**
- 2026-10-03: Tournament fight cells — DMZ `cell_arena` + WorldEdit bounds. SavedData
  `TournamentArenaRegion` (spawn + AABB) preferred over config point list. Commands:
  `/xenotourney arena locate [cell_arena]`, `bindcell`, `setfromwe`, `setspawn`,
  `clear`/`clearall`; `/xenotourney arenas` lists cells. Default cell pad matches
  structure clear (±28 xz). Config `tournamentOutOfBoundsLose=false` (geometry only;
  auto leave-arena lose is follow-on). Units: `TournamentArenaRegionTest`.
  **Not verified in a running game.**
- 2026-10-03: Xeno Hair Studio chrome (from scratch, DMZ 2.1.3 pin) — Outliner | Viewport
  (Grow/Rotate/Curve tools + XYZ pick + Alt-drag yaw) | Inspector; `HairEditHistory` Ctrl+Z/Y;
  dirty-gated live preview. Design matched to DMZ 2.2-alpha ideas; **no copied 2.2 code/assets**.
  Pixel paint deferred (next PR). **Not verified in a running game.**
- 2026-10-03: Hair click-pick upgraded from flat 2D face-grid to projected strand XYZ —
  `CustomHair.getStrandBasePosition` ×0.0625 (HairRenderer), head offset, body yaw, inventory
  `scale`+`rotateZ(PI)`; tip estimate from length/cubeHeight; searches all faces. Still not a
  full cube mesh raycast. **Not verified in a running game.**
- 2026-10-03: Maker live visualizer + hair segment studio (Pass 1–2) — shared
  `MakerPreviewLayout` right-column well (Form Maker no longer stacks preview over the
  form list); `MakerPreviewAppearance` snapshot/apply/restore so hair/skin/eyes/aura/form
  edits update the 3D model without Apply; Hair Editor gains Create / Weld (offset copy) /
  Dup / Del, cube W/H/D editors, list + click-on-preview strand pick (`HairStrandPick`),
  drag-yaw. Freeform mesh weld deferred (Pass 3). **Not verified in a running game.**
- 2026-10-03: `/xenomaker` missing in-game — Brigadier `.requires(MAKER_OPEN)` hid the
  client command when the source entity is a `LocalPlayer` (OP PermissionAPI path never
  runs). Registration now matches `/xenohairui` (no `.requires`); `MAKER_OPEN` is
  client/everyone default; Create Race stays OP via `MAKER_RACE_CREATE`. **Not verified in a running game.**
- 2026-10-03: Maker Studio live visualizer visibility fix — empty Preview wells across
  `/xenomaker` editors. `MakerPreviewController` now matches DMZ `HairEditorScreen#renderPlayerModel`
  exactly (empty translation, z=150, scale ~h*0.55); draws a dark well always so a failed
  entity draw is obvious; `DMZSkinLayer.PREVIEW_MODE` kept on. Hub/Race/Form/Hair/Parts all
  draw `preview.render` **after** `super.render` so atlas widgets never cover the model;
  hub + Parts upgraded to `xeno_maker_hair_preview` (280×240). **Not verified in a running game.**
- 2026-10-03: Maker Studio hub/race/form UX pass — nav buttons no longer ghost-overlap
  (`mynpcs_button_row_w128` / `_w96` with clear gaps instead of stacked `pill_button`);
  hub + race/form use large green `xeno_maker_hair_preview` wells; `MakerPreviewController`
  pose/scale aligned to Neon/HairEditor so the live player model is visible.
  **Not verified in a running game.**
- 2026-10-03: New race registration (PR-D6a, KD15 r3). Evidence
  `docs/superpowers/evidence/2026-10-03-race-registration-path.md` Verdict **READY** —
  DMZ `ConfigManager#loadAllRaces` scans `config/dragonminez/races/` for non-default
  folders (`Custom race detected: {}`); UI uses `getLoadedRaces()`. `RacePackService`
  writes `races/<id>/character.json` + `forms/` (setupDefaultCharacter-shaped skeleton).
  Callers reload via `ConfigManager.reload()`. Units: `RacePackServiceTest`.
  **Not verified in a running game.**
- 2026-10-03: `/xenoaura hd` no longer lets classic DragonMineZ aura return in third person
  or first person. `DmzHdAuraQueueMixin` used to cancel `addAura` only when `plays()` was
  true (DMZ sheet could still queue); FP queue was never cancelled; AuraRenderer also had a
  FP fallback draw that bypassed the queue. Fix: HD-only always cancels `addAura` +
  `addFirstPersonAura`, and `DmzHdAuraFpDrawMixin` cancels `renderShaderFirstPersonAura`
  (sparks unchanged). **Not verified in a running game.**
- 2026-10-03: HD aura no longer slides a tick behind the body when flying or moving fast
  (first person and third person). Root cause: third-person follow lerped from
  `xOld`/`yOld`/`zOld`, which `absMoveTo` can leave stale while `xo`/`yo`/`zo` stay current;
  AAA PreDraw also runs after Effekseer update, so a late root alone still reads one frame
  behind. Fix: `HdAuraPlan.entityRenderPos` uses `xo` (same as `Entity.getPosition` /
  Camera / DMZ), early `AFTER_ENTITIES` `onRenderFollow` before AAA draw, and PreDraw
  refresh with the real render partial. FP still locks to the main camera each frame.
  Units: `HdAuraPlanTest.entityRenderPosUsesXoNotStaleXOld`. **Not verified in a running game.**
- 2026-10-03: First-person HD aura (v3) start/depth aligned to DragonMineZ
  `AuraRenderer.executeAuraShaderDraw` FP path. Root cause: `0.45` is DMZ **alpha**, not
  scale — FP scale is `normalizedScale * 3`. Placement now uses AAA head-space Basis
  (`+Z` behind eyes; offset `{0,-0.6,-0.7}` = 0.7 in front), camera-lock `setRotation`, and
  silhouette `CENTRE_Y=1.5` emitter nudge so the sprite centre hits the DMZ billboard centre.
  Units: `HdAuraPlanTest.firstPersonUsesTheDmzCameraOverlay`. **Not verified in a running game.**
- 2026-10-03: Hair in-game apply (PR-D7c, KD9). Evidence
  `docs/superpowers/evidence/2026-10-03-hair-apply-path.md` Verdict **READY** (path cited;
  runtime unverified) — `UpdateCustomHairC2S#handle` via `NetworkHandler#sendToServer` (DMZ
  `HairEditorScreen#syncHairToServer` pattern). `HairApplyService` maps maker strands →
  `CustomHair` (cube dims via `HairStrand#load` NBT `cw`/`ch`/`cd`); Apply enabled as
  **replace-current-style** full slot overwrite (no load/merge from Character). **Not**
  `CustomizationManager` `hair_style_*`. Export `apply=path_ready_runtime_unverified`; codes
  still empty in-game. Units: `HairApplyServiceTest` (fake transport). **Not verified in a
  running game.**
- 2026-10-03: Hair green UI + visualizer (PR-D7b, KD9). Client `HairMakerScreen`
  (`client.maker`) on Theme.GREEN atlas; strand list + length/rotation/scale/curve editors
  map to lab `HairStrand` fields. Live preview depth: **text/summary** via reused
  `PreviewDebounce` ≤50 ms — **not** a 3D hair renderer. Export writes Task 9
  `xenopixels.hair.export.v1` JSON under `xenopixelsmod/hair-exports/` (DMZ codes empty
  in-game; lab `npm run export:hair` encodes). Apply gated by PR-D7c (now READY — see above).
  Connected = “Connected (parented cubes)” label only — **no** connected boolean. Open:
  client `/xenohairui`. Units: `HairMakerDocumentTest`, `HairMakerAtlasTest`. **Not verified
  in a running game.**
- 2026-10-03: Hair codec / export (PR-D7a, KD9). Lab `dmz-codec.ts` golden vectors
  (empty `DMZ1` + one-strand `DMZ1` / full `DMZF1`) under
  `tools/dmz-hair-builder-site/fixtures/hair/` and `src/test/resources/hair/`. Export envelope
  `xenopixels.hair.export.v1` via `lib/hair-export.ts` + `npm run export:hair` (file write /
  download only). **No** new `connected` boolean; **no** CustomizationManager apply in D7a
  (D7c cites `UpdateCustomHairC2S#handle`). Node: `cd tools/dmz-hair-builder-site && npm test`.
  Java smoke: `HairCodecFixtureTest`. Evidence:
  `docs/superpowers/evidence/2026-10-03-hair-codec-vectors.md`.
- 2026-10-03: Race form-group green UI (PR-D6b, KD15). Client `RaceFormGroupMakerScreen`

  (`client.maker`) on Theme.GREEN atlas (`xeno_editor_panel`, `panel_wide`, `pill_button`);
  refuses unknown race via `RaceFormGroupGuard` message; **Open Editor** links to existing
  `DmzFormMakerScreen` / `FormEditorNetwork.save` (does not rewrite `GuiNpcDmzFormEditor`).
  Live preview depth: **document field summary** (auraColor / extraAuraColor / hairColor) with
  `PreviewDebounce` ≤50 ms — **not** a Gecko/DMZ entity stand-in (no invented renderer API).
  Open: client `/xenoraceformui [race] [group]`. Units: `PreviewDebounceTest`,
  `RaceFormGroupMakerAtlasTest`. **Not verified in a running game.**
- 2026-10-03: Race form-group maker IO (PR-D6a, KD15). `RaceFormGroupGuard` validates race /
  group against known DMZ/Xeno races under `data/xenopixelsmod/dmz/races/` (saiyan, human,
  namekian, frostdemon, majin, bioandroid) and installed form-group stems (or
  `DmzFormMetadataRegistry` ownership). Rejects unknown race ids; does **not** create new race
  folders or register new races. Editor saves still go through `FormEditorNetwork.save` →
  `DmzFormEditorService.save` (bootstrap backup unchanged). Unit: `RaceFormGroupMakerIoTest`
  (unknown race rejected; saiyan/`xenopixels_gods_forms` accepted; gods JSON FormConfig
  round-trip). **Not verified in a running game.**
- 2026-10-03: Angel as gods master (PR-D5). Evidence appendix
  `docs/superpowers/evidence/2026-10-03-angel-trainer-offerings.md` confirms a player UUID
  **can** be a `DmzFormMetadataRegistry.trainerOfferings` key (no invented DMZ unlock API).
  Wire: `AngelTrainerGate` constrains offerings to group `xenopixels_gods_forms` / skill
  formType `xenopixels_divinity`; `AngelTrainerEvents` opens `FormEditorNetwork.sendTrainerMenu`
  on angel player interact; `TrainerPurchasePacket.purchase` accepts angel `ServerPlayer`
  trainers and still applies evaluate → removeTrainingPoints → setSkillLevel → sync (never
  `CombatSkills.grant` for forms). Unit: `AngelGodsOfferingsTest`. **Not verified in a running
  game.**
- 2026-10-03: Tournament green atlas UI (PR-D4). Client
  `TournamentQueueScreen` (package `client.tournament`) shows queued names + active match
  from the last S2C `QueueSnapshotPacket` on channel `tournament` protocol `"1"`. Reuses
  existing green panels (`xeno_editor_panel`, `header_strip`, `panel_wide`, `pill_button`) —
  no new PanelSpecs. Empty queue shows a clear empty state. **Open path:** server
  `/xenotourney status` syncs the snapshot then sends S2C `OpenQueueScreenPacket` (msg id 1,
  same channel/protocol — not a second protocol); dedicated client `/xenotourneyui` opens from
  the cached snapshot without shadowing `/xenotourney join|leave|…`. No client win packet.
  Unit smoke `TournamentQueueAtlasTest`. **Not verified in a running game.**
- 2026-10-03: Tournament → Angel awards (PR-D3). Server `TournamentService.reportResult`
  grants `PlayerRoleId.ANGEL` with source `tournament:<matchId>` only after match validity
  checks succeed and when `tournamentEnabled=true`. Offline winners skip grant until online
  (uses `PlayerRoleService.grant`). No client win path; auto-KO still not shipped. Unit-tested
  in `TournamentAwardTest`. **Not verified in a running game.**
- 2026-10-03: Tournament core (queue + KotH, static arenas, admin results).
  `TournamentSavedData` (`xenopixels_tournament`) + `TournamentService` (MAX_QUEUED=12) +
  dedicated channel `tournament` protocol `"1"` (not ModNetwork `"101"`). Commands:
  `/xenotourney join|leave|status|result|arenas|start`. Config defaults
  `tournamentEnabled=false`, arena dimension/positions. Admin `/xenotourney result` only —
  **auto-KO not shipped** (follow-on despite D2a READY). Unit-tested queue cap + result
  recording. **Not verified in a running game.**
- 2026-10-03: Tournament KO evidence appendix
  (`docs/superpowers/evidence/2026-10-03-tournament-ko-events.md`): NeoForge
  `LivingDeathEvent` + `DamageSource.getEntity()`/`getDirectEntity()` verified for a later
  auto-KO PR; verdict READY with match filters. No auto-KO shipped; admin `/xenotourney result`
  remains until a follow-on implements the subscriber. **Not verified in a running game.**
- 2026-10-03: Player roles foundation (`PlayerRoleSavedData` / `PlayerRoleService` /
  `/xenorole get|set|clear`). Persists UUID → `none|angel` in overworld SavedData
  `xenopixels_player_roles`; S2C sync on channel `player_roles` protocol `"1"` (not ModNetwork
  `"101"`). Angel is cosmetic + future trainer gating only — no combat modifiers. Unit-tested
  persistence round-trip. **Not verified in a running game.**
- 2026-10-03: Client jar `xenopixelsmod-0.5.0-1.21.1.jar` rebuilt after Motif Phase 1 Java.
  SHA-256 `161857ECBBE62B68E7CD401C1E016528F9C1433B5747A7315DECD4A7A86F8572`, 64,869,850 bytes,
  LastWriteTime 2026-10-03 05:12:04. **Not verified in a running game.**
- 2026-10-03: HD aura Motif Phase 1 (`AuraMotifTable`): per-form outer/inner brightness multipliers
  for SSB, SSRose, UI, and Trunks Ikari (`race/formGroup/formName` keys). Wired in
  `HdAuraClient.emit` only — silhouette × outer, `aura_out` × outer, `aura_in` × inner on top of
  `/xenoaura inner`. Unknown forms stay 1.0. Edge lick rate and thunder density remain Phase 2
  (authored in `aura3.py`); this change does **not** alter edge/thunder. **Not verified in a
  running game.**
- 2026-10-03: Client jar `xenopixelsmod-0.5.0-1.21.1.jar` rebuilt with on-disk aura3 set (1080
  `.efkefc`; regen skipped — definitions already matched disk). SHA-256
  `DFE34ACFD6660DF153FB5FD4A8FB4B0522206D017B1396E77C6BA3FD846FB68C`, 64,867,385 bytes,
  LastWriteTime 2026-10-03 05:04:38. **Not verified in a running game.**
- 2026-10-03: HD aura v3 (`/xenoaura v3`, Ki Actions cycles v1 → v2 → v3). The spiked silhouette
  with punch / ki-blast Glow edges (`RGB(255,92,32)` to `(255,64,16)`) and form-coloured Sparking,
  **plus the full v1 aura** (outer billow column and inner body shell). Generated by
  `python tools/effekseer/gen_effects.py aura3`. Not yet seen in a running game.
- 2026-10-03: v3 was missing v1's outer and (for some looks) reading as silhouette-only; it now
  always plays `aura_out_*` and `aura_in_*` beside `aura3_*` at the v1 box.
- 2026-10-03: HD aura in first person follows DragonMineZ's camera overlay (`translate(0, -0.6,
  -0.7)` eye-space, 0.45 scale factor) instead of being hidden. Not yet seen in a running game.
- 2026-10-03: v3 thunder bolts and crackle follow the form aura colour (no fixed cyan). Needs a
  rebuilt `aura3` set from `gen_effects.py aura3`.
- 2026-10-02: Blue form auras (SSB / SSB3) no longer render as a white wall, and the extra
  layer stacks as a darker blue (`#01579B`) around the cyan instead of ice (`#E1F5FE`). v2 no
  longer plays near-white extras as their own silhouette, and the silhouette tint stays on the
  form colour instead of `hot()` bleaching it. Needs the aura2 rebuild in the jar. Not yet
  verified in a running game.
- 2026-10-02: First-person HD aura no longer sits in front of the camera. A 4.5-block world-space
  billboard 1.6 blocks ahead filled the view (SSRose3 screenshot). Copies already playing are
  scaled to zero in first person; new ones are not spawned. DragonMineZ's own first-person overlay
  still draws. `/xenoaura firstperson` only keeps the sighting so the aura does not reset when
  going back to third person. Not yet verified in a running game.
- 2026-10-02: HD aura v2 flame licks now rise from the feet, pass the silhouette, and fade out
  above it, in the punch / ki-impact glow (`RGB(255,92,32)` to `(255,64,16)`, ToonHit
  `punch_impact` Glow). The spiked silhouette stays the form colour. Rebuild with
  `python tools/effekseer/gen_effects.py aura2` (full set; `--missing` does not rebuild
  changed effects). Not yet seen in a running game.
- 2026-10-02: The WorldEdit selection bridge passed Minecraft's player and level where WorldEdit
  takes its own, so every read threw "argument type mismatch": plot claims from a selection never
  worked, and the plot HUD logged a stack trace every frame (7,198 in four minutes of play). They
  now go through WorldEdit's `NeoForgeAdapter`; the HUD reads the selection from the integrated
  server in single player and shows nothing on a remote server. Not yet verified in a running game.
- 2026-10-02: A Hakaishin in the mantle is no longer knocked back by punches or kicks. The mantle
  cancelled the damage, but the BT3 launch arcs set the victim's velocity directly and still sent
  the wearer flying. `/xenoset hakaiMantleNoKnockback false` restores the push. Not yet verified
  in a running game.
- 2026-10-02: HD aura v2, an alternative look. Not yet seen in a running game.
  - A cel-shaded, spiked flame silhouette after DragonMineZ's own aura sheet and Jiren's flames:
    four flickering frames, flame licks racing up off the body, tips that burn out red, embers.
    One effect per colour and brightness level (`effeks/aura2/`, 356 effects, generated by
    `python tools/effekseer/gen_effects.py aura2`).
  - `/xenoaura v2` chooses it and `/xenoaura v1` goes back; it works with `dmz`, `hd` and
    `both`, on players and NPCs. DragonMineZ's X menu has it under Actions, Ki Actions as
    "Aura V2". v1 stays the default.
  - v2 is authored to the box DragonMineZ's flame occupies, read off its renderer: from 0.44
    below the feet to 3.61 above them and 3.22 wide for a player at rest. Its first builds were
    half that height with the centre left where the full height needed it, so the flame started
    at the shoulders: an Effekseer sprite of scale s is s across, not 2 s.
  - Both variants stretch with the ki aura: taller and wider with battle power, and rising while
    transforming or charging ki, by the same curve as DragonMineZ's aura (`/xenohud aura`).
  - The playing aura is resized every frame, so it rises with the ki aura height as a charge
    ramps instead of jumping when each copy is replaced. `/xenoaura live off` fixes the size at
    spawn again, and that is also what happens if the live path fails.
  - Both variants follow DragonMineZ's aura scale (its own `getAuraScale`): the form's model
    scale, battle power and the full `/xenohud aura` ki charge height, so the whole aura rises
    while charging. `/xenoaura maxheight <1-10>` caps the height (default 10).
  - `/xenoaura size 1` is DragonMineZ's own aura size and is the default; sizes saved earlier
    that night are not carried over. v1 is scaled and lowered onto the same box, from its
    authored figures rather than from a running game.
  - Your own v2 aura in third person ignores scene depth, as DragonMineZ's own aura does, so
    charge rocks, dust and other particles no longer cut holes in it. Other players' auras stay
    depth-tested. `/xenoaura overlay off` turns it off. v1 is not covered.
  - The aura no longer restarts on a slow or stuttering client (a big modpack, a shader pack).
    An aura counted as switched off after three game ticks without DragonMineZ drawing it, and a
    client below about seven frames a second, or one catching up on missed ticks after a freeze,
    runs three ticks with no frame between them. Ticks with no frame drawn no longer count.
  - After a client freeze of more than a quarter of a second the aura is sent again at once,
    instead of staying gone until the next half-second pulse.
  - `/xenoaura brightness <0-130>` is a percentage now and reaches down to 0: 0 plays no HD aura,
    and 10% and 25% levels were baked beside 50, 75, 100 and 130 (the old 0.5-1.3 fractions are
    still accepted).
  - v2 shows every DragonMineZ aura layer as its own silhouette, each 15% larger than the one
    below, as DMZ draws them. It used to play only the main colour, so a form's extra aura
    colour never appeared.
  - `/xenoaura box on` draws a yellow wire box at DragonMineZ's flame bounds round your own
    character, to compare the two auras against (`/xenoaura both`).
  - The body's outline glow follows the power release - faint at 0%, full at 100% - and shows
    whenever the HD aura is on, not only in a form. The aura's size no longer follows the
    release, since DragonMineZ's does not.
- 2026-10-02: The inner HD aura (the shell and glow on the body) is dimmer and more see-through:
  it plays one baked brightness level below the outer flame. `/xenoaura inner <0.4-1>` sets it
  (default 0.7; 1 is the earlier look). With `/xenoaura brightness` already at 0.5 there is no
  lower level to drop to.
- 2026-10-02: The ranges the chair, flaps and engines find each other at are configurable, for
  big ships (`config/xenopixelsmod-aero.json`, `/xenoaerotune`). Not yet verified in a running game.
  - `linkradius` (32, up to 512): how far a flap or thruster click looks for the nearest chair or
    flight computer when none is selected on the ship tool. Clicking the chair first never had a
    range, and still has none.
  - `unlinkradius` (48, up to 512): how far a flap looks for the chair that has it, to unlink.
  - `controllerradius` (8, up to 64): how far a chair looks for a flight computer to bind to.
  - `engineradius` (12, up to 96): the area a chair sweeps when pairing nearby engines.
- 2026-10-02: NPCs flown by the combat brain can be knocked back. The brain set a flying NPC's
  velocity every tick, so a knockback was overwritten on the next one; it now leaves the velocity
  alone for 10 ticks after a push (`NpcKnockbackGrace`). This applies to every brain NPC, not only
  the training partners. Not yet verified in a running game.
- 2026-10-02: Training partners that really fight. Not yet verified in a running game.
  - `/xenotrain shadow [25-100]` and the new `/xenotrain dummytrain [25-100]` now spawn a native
    Xeno NPC running the V9 combat brain (`TrainingNpc`). It wears your DragonMineZ character -
    race, body, face, colours, tail, hair and the form you are in - through Full appearance, or
    your plain skin if you have no DMZ character. It chases, flies and can
    be locked on to with DragonMineZ's lock-on. `shadow` fights with melee and ki blasts,
    `dummytrain` with melee only; waves, disks and named ki attacks are off for both.
  - It stays on its trainer, and is removed when the trainer leaves, moves 96 blocks away or
    runs `/xenotrain dismiss`. No NPC targets a player in creative or spectator, so it only
    fights you in survival or adventure; the spawn message says so.
  - The standing copies are still there as `/xenotrain classic shadow` and
    `/xenotrain classic dummytrain`.
  - Training dummies no longer pay skill points. They paid one at 25, 100 and 250 hits, and every
    spawn restarted the count, so each new dummy paid again. `/xenoset trainingDummySkillPoints
    true` brings the milestones back.
  - `/xenotrain dismiss` removes only your own dummies, and spawning one replaces only your own.
    Being an operator used to make both take everyone's; that is now `/xenotrain dismiss all`.
- 2026-10-02: Ikari as a stack form (`forms/xenopixels_ikari.json`, skill `xenopixels_ikari`,
  Saiyan only, taught by Beerus and Whis). It goes on Super Saiyan 1 (any grade), mastered Super
  Saiyan, Super Saiyan 2 and Super Saiyan 3, and nowhere else: not in base, not on Super Saiyan 4
  and not on the god forms (`IkariStackRules`; changing to another form removes it). The two
  existing Ikari forms, DragonMineZ's and the saga one, are unchanged. Not yet verified in a
  running game.
- 2026-10-02: Two new Saiyan god forms, Super Saiyan Blue 3 and Super Saiyan Rose 3. Not yet
  verified in a running game.
  - Super Saiyan 3 hair in the Blue and Rose colours, lightning, STR 7.4 and a heavy ki drain
    (0.5). Each needs mastery 30 of its Evolution form.
  - They are divinity skill levels 9 and 10 (340,000 TP each), listed after Ultra Ego. DMZ walks
    a group in file order and stops at the first form a player cannot take, so placing them
    directly after the Evolution forms would have cut the later forms off.
  - Each has its own radial icon, generated by `tools/gen_god_form_icons.py`
    (`BundledFormIcons`).
- 2026-10-02: NPC auras. Not yet verified in a running game.
  - The HD aura now plays on every NPC whose aura is on, not only Full-appearance ones drawn by
    DragonMineZ, and follows `/xenoaura dmz|hd|both` like a player's (both is the default; `hd`
    hides the shader column).
  - A Xeno NPC on a GeckoLib or mimic model with Full appearance had no aura at all: the
    fallback column was skipped for Full NPCs although DragonMineZ does not draw those models.
  - The NPC aura is drawn before the speech and dialogue bubbles, so it no longer shows through
    an answer bubble.
- 2026-10-02: GeckoLib-model NPCs ease into and out of a scripted clip (wave, nod) over 5 ticks.
  The idle keeps moving the head and waist, and the clip replaced them with no transition, so
  the head jumped when a wave started and again when it ended. Not yet verified in a running game.
- 2026-10-02: HD aura layers and a menu toggle. Not yet verified in a running game.
  - A stack form is its own, larger HD aura again instead of only tinting the outer flame, and
    the form being transformed into fades in instead of showing at full strength at once
    (`HdAuraPlan`). `/xenoaura layers off` brings back the first behaviour.
  - Second aura switch, per player and seen by everyone: on, the HD aura plays on you all the
    time, whether or not DragonMineZ's aura is showing; off (the default), it plays only while
    DragonMineZ's aura shows, as it always did. `/secondaura` toggles it, `/secondaura on|off` sets it, and DragonMineZ's X menu has it
    under Actions, Ki Actions as "Toggle Second Aura" ("Toggle Hakai no Energy" in the Hakaishin
    form). It is kept through death. Main protocol 101 (`SecondAuraStatePacket` appended).
  - `/xenoaura follow off` makes the switch strict on this client: the HD aura then plays only on
    players whose second aura is on. `follow` is on by default; for a few hours on 2026-10-02 it
    was off, and the HD aura never showed until `/secondaura` was run.
  - Your own HD aura also plays in first person. It used to stop there - nothing was played, and
    because DragonMineZ does not draw your own model in first person the aura then counted as
    switched off and started again on returning to third person. In first person it is placed
    1.6 blocks ahead along the view and played dimmer, the way DragonMineZ places its own
    first-person aura. `/xenoaura firstperson off` gives third person only.
- 2026-10-02: AAA Particles 2.3.1 is bundled (Jar-in-Jar) in both the client jar and the server
  jar, so neither players nor server owners install it separately; Architectury 13.0.8 rides along
  nested inside it. It is the only nested jar the server jar carries. Not yet verified in a running
  game or on a dedicated server.
- 2026-09-29: XenoNPCs, the NPC system as a separate public mod.
  - `tools/xenonpcs/export_xenonpcs.py` generates it into `XenoNPCs/` (its own git repository,
    ignored by this one).
  - Mod id `xenonpcs`; DragonMineZ and GeckoLib are required; CustomNPCs, My NPCs and Curios are
    optional. It is declared incompatible with this mod.
  - It offers the V9 combat brain only, through a new `NpcBrainPolicy` seam. In XenoPixels the seam
    allows every brain, so nothing changes here.
  - It has the owner's logo and creative-tab icon, and a newcomer wiki.
  - It builds, passes its own tests (878) and boots a dedicated server. The CHANGELOG, README and
    wiki live in `tools/xenonpcs/`.
- 2026-09-29: God-form passives (`form_passives.json`, `FormPassiveEvents`).
  - **Ultra Ego:**
    - Attackers weaker by the battle-power ratio (0.8) deal no damage.
    - Its ki and melee get +35% defense penetration on top of DMZ's own (a mixin on
      `CombatEvent.computeDefensePenetration`).
    - Punching a weaker ki blast destroys it.
    - The aura deletes weaker projectiles and ki that reach you.
  - **Hakaishin:** a new form for every race (skill `xenopixels_destroyer`).
    - No master teaches it; an admin grants it with `/dmzform set <player> xenopixels_destroyer <level>`.
    - `hakaishinNeedsHakai` (off) optionally also requires Hakai.
    - It has its own group icon, generated by `tools/gen_hakaishin_icon.py`.
    - In the form, the HP and ki bars take the aura's purple, as Sparking turns the ki bar gold.
      Sparking's gold still wins on the ki bar (it is the timer). This comes from the passive
      `hudTint`, drawn over a greyscale atlas copy made by `tools/gen_hud_neutral_atlas.py`.
    - Its Hakai mantle erases every attack and ki blast aimed at you, however strong.
    - Falling, fire, `/kill` and the void still apply.
  - **Ultra Instinct Sign and Mastered UI:** auto-dodge by form mastery (Sign 10→35%,
    Mastered 20→60%), with an afterimage and a sidestep.
    - No dodge against an attacker who is also in UI.
  - **Groundwork:** any race's form gains these passives by adding an entry to
    `config/xenopixelsmod/form_passives.json`.
  - **"Weaker"** means the DMZ power level ki sense shows, for players, NPCs and mobs alike
    (`DmzAccess.powerLevel`: DMZ's `IBattlePower` on every living entity).
  - **Switches:** `formPassives`, `ueImmunity`, `uePenetration`, `ueProjectileAura`,
    `uePunchBreak`, `hakaiMantle`, `uiDodge`, and `uiDodgeScale`.
- 2026-09-29: Area Hakai erases evenly and its effect always shows.
  - Layer after layer: each height layer is cleared across the whole area (nearest chunks first)
    before the next one down. Before, it went chunk by chunk and left steps at chunk borders.
  - Raze: everything above the caster's feet (or the aim point, if lower) is building, even plain
    stone. Only natural ground at or below that line stops a column. Stone in a build had read as
    ground and left layers standing.
  - The veil and erase burst are sent as far as the sphere reaches (`XenoEffects.playWide`). The
    veil's anchor sits a radius below the aim point, so for a big Hakai it was past the 64-block
    effect range and often never reached the player.
- 2026-09-29: Area Hakai radius up to 4096 (was capped at 32), so 500 works.
  - The block scan is lazy (`HakaiBlockScan`): it walks chunks nearest first, never loads or
    generates a chunk, skips air-only sections, and looks at at most 32768 positions per tick.
    Listing a radius-500 sphere up front would be about 500 million positions.
  - `hakaiBlockLimit` can go to 100,000,000 (default still 4096).
  - The Hakai veil stays over the sphere, re-played every 10 ticks, until the last block is
    erased. It is sized to the full radius.
- 2026-09-29: Area Hakai no longer erases blocks in DragonMineZ's Otherworld (King Yemma's
  palace, Snake Way), Time Chamber or Sacred Kai planet.
  - Those are placed builds, not worldgen structures, so the structure check missed them.
  - `/xenoset hakaiSparedDimensions` holds the list (comma-separated dimension ids).
  - The Otherworld cloud block is never erased anywhere (`hakaiSparedBlocks`, a comma list of
    block ids).
  - Every block Hakai erases now goes through DMZ's own `MainGameRules.canKiGrief`, as a ki blast
    does. That covers DMZ protected areas, master structures, its ki-griefing gamerules,
    WorldGuard, and YAWP regions (through the existing YawpKiGriefing hook).
- 2026-09-29: Area Hakai could erase nothing ("no blocks to erase (4096 found...)").
  - The block limit was taken before air was dropped, so a big sphere spent it all on the empty
    sky above the building, and a Sable ship's blocks were never reached.
  - The limit now counts real blocks only.
- 2026-09-29: Area Hakai, with no lock-on.
  - `/xenoset hakaiMode area`: J erases everything alive in a sphere where you look
    (`hakaiAreaRadius`, default 5; up to `hakaiAreaMaxTargets`, default 16).
  - Afterwards the blocks in the sphere fade out from the roof down: a crack overlay and
    violet puffs, no drops. This covers Sable ship and plane blocks inside the sphere.
  - Plots, YAWP and claims can refuse any block through the normal break event.
  - Keys: `hakaiBlocks`, `hakaiShips`, `hakaiBlockLimit`, `hakaiBlocksPerTick` and
    `hakaiBlockFadeTicks`.
  - `single`, the original one-target Hakai, stays the default and is unchanged.
    `finish` was only moved into the shared `HakaiErase`.
  - Every block can be erased except bedrock, including barriers and command blocks
    (`hakaiBlocksUnbreakable`). `false` spares every unbreakable block, the first rule.
  - Blocks obey the `mobGriefing` game rule (as missiles do), the player's own break rules
    (adventure/spectator mode, spawn protection) and plots, claims and YAWP.
  - DragonMineZ structures (Kami's Lookout, Goku's house, the Cell arena and the rest) are never
    erased (`hakaiSpareDmzStructures`).
  - `/xenoset hakaiBlockShape raze`: each column around the look point is cleared from the roof
    down to the ground, and the ground stays (`hakaiRazeHeight`, 48 above and below).
    `sphere` stays the default.
  - The area effect is sized to the sphere (`hakaiAreaFxScale`).
  - Chat now says how many blocks were erased and what refused the rest.
- 2026-09-29: NPC melee across height differences, and new-NPC defaults.
  - The V6–V9 brains chose melee anywhere inside DMZ's 4.5-block band, but the hit gate needed
    a straight foot-to-foot distance of about 1.2 blocks. A target a block or two up or down
    (a step, a slab, mid-jump, standing on the NPC) was never hit, and the NPC did not move.
  - NPC melee now measures reach across the ground and allows 1.5 blocks of air between the
    two hitboxes (`/xenoset npcMeleeHeightReach`, 0–8). `/xenoset npcMeleeHeightRule false`
    restores the old straight-line rule.
  - When the saga tree picks melee and the swing can't reach, the NPC closes in.
  - An NPC with no saved profile now defaults to the V9 brain, with every DMZ skill from the
    skills tab switched on at DMZ's max level. The combat brain itself stays opt-in.
  - Saved NPCs keep what they saved (an old save still reads V1).
- 2026-09-29: Aura brightness and ki explosion impacts.
  - `/xenoaura brightness <0.5-1.3>`: the aura effects are baked at 50/75/100/130% and the
    nearest level is used. AAA's native binding has no colour or alpha control, so brightness
    cannot be applied at run time. 1068 aura effects in all.
  - A ki attack exploding (ki blasts, ki waves, strike attacks) plays the punch impact effect,
    sized to the blast, instead of DragonMineZ's explosion visual (`KiImpactEvents`).
  - The damage and block breaking are unchanged, and DMZ's visual is the fallback.
  - `/xenoset kiimpact true|false`, `kiimpactsize`, and the per-effect key `ki_impact`.
- 2026-09-29: HD aura changes.
  - Pointed flame licks along the outline and a crown of taller ones on top, like Jiren's aura
    (a sharper `flame_tongue` texture).
  - The space round the body is hollowed out (the middle layer is pushed out, the inner shell and
    core glow are fainter), so the fighter stays visible.
  - The outline glow now stays on while charging, or whenever the aura is on, for as long as a
    form is active.
- 2026-09-29: HD aura rebuilt from scratch after the Jiren reference.
  - The aura is a tall column of billowing fire made of hundreds of overlapping fire puffs (new
    `fire_billow` textures). It is hottest near the body (warm hues burn yellow), the form's colour
    further out, with deep ragged edges, flame fibres, embers and star sparkles.
  - Each copy emits exactly one re-send period's puffs, so the density is constant.
  - New: while a form is on and the aura is off, a thin flickering flame outline plus vanilla's
    outline glow in the aura colour (`aura_rim_*`).
  - `/xenoaura size <0.2-5>`.
  - 89 colours × 3 effects = 267 effects (2.9 MB).
- 2026-09-29: HD ki aura, switchable per client with `/xenoaura dmz|hd|both` (default `dmz`,
  DragonMineZ's own).
  - The look, after the Super Saiyan Rose reference: a bright aura hugging the body with star
    sparkles, inside a big vivid flame of licking tongues with darker ragged edges, plus embers
    and a dust ring.
  - Generated by `python tools/effekseer/gen_effects.py aura`: two effects per colour (inner and
    outer) for all 43 aura colours in the DMZ and XenoPixels form data, plus a hue wheel. That is
    89 colours and 178 effects in `effeks/aura/`, with 8 shared textures (2.2 MB).
  - Colours come straight from DMZ's own aura layers (form, stack form, transformation blend).
    The outer flame takes the form's extra aura colour when it has one.
  - Works for players and Full-mode NPCs. It plays on each client only, and hides DMZ's aura in
    `hd` mode. It is skipped for your own view in first person.
- 2026-09-29: Sparking crackles with blue electricity through the gold, as in Budokai
  Tenkaichi 3: blue lightning and fast blue sparks in the ground aura, the smooth aura, the flight
  aura and the start burst.
- 2026-09-29: Punch effects (player and NPC: combos, rushes, charged hits, guards and basic melee)
  play at the height the attacker's crosshair meets the target, kept between its feet and head.
  They were at a fixed mid-height (`PunchEffectRules.crosshairY`, `CombatFx.crosshairPoint`).
- 2026-09-29: Combat input and knockback.
  - Punch + W no longer launches the target (the W-tap mash launcher and the held-W pursue dash).
    Launching is W + the charged kick. The old route is `/xenobind mashlauncher on` (off by
    default).
  - One click of the charged-kick key (mouse wheel) always kicks, even a click shorter than a
    tick, at `kickTapCharge` strength (default 0.5). Holding still charges.
  - The charged punch's knockback distance is configurable (`chargePunchKnockback`, default 1 =
    as before), with an optional parabolic arc (`chargePunchParabolic`, `chargePunchArcHeight`).
- 2026-09-29: The Sparking flight aura now follows DMZ's own fast-flight rule
  (`FlySkillEvent.isFlyingFast`: fly skill on, flight mode not 1, faster than 0.55 blocks a tick,
  with speed measured on the server). It switches back to the ground aura when you hover or turn
  fast flight off, and changes at once when the pose changes.
- 2026-09-29: Per-effect sizes with `/xenoset <effect> <scale>` (for example
  `/xenoset missile_explosion 2`; also `fxscale_<effect>`), 0.05-50, saved with the server config.
  They multiply on top of the category sizes. `sparking_flight` defaults to 3.5. The flight aura is
  anchored at the body's centre (AAA head-space offset, direction measured in
  `AaaHeadSpaceOffsetTest`), so it no longer trails behind the flyer and grows around them.
- 2026-09-29: The classic bright Sparking aura is the default again (the constant-sum version
  looked washed out). The steadier one is kept as `sparking_aura_smooth`
  (`/xenoset sparkingsmooth true`). The flight aura uses the classic brightness too.
- 2026-09-29: Effects follow smoothly. The Sparking aura and missile plume are re-sent with
  overlapping fades, so their brightness stays constant with no pulsing. While DMZ flying, the new
  `sparking_flight` aura lies along the body, bound to the look; the start burst follows the
  player. `/xenofx` previews the entity-bound effects riding on you. The strawberry senzu now
  restores DMZ ki and stamina to full as well as health (`SenzuRestore`; DMZ
  `Resources.setCurrentEnergy`/`setCurrentStamina`, synced to the HUD).
- 2026-09-29: The Sparking aura is bound to the player (it follows position only and stays
  upright), so it moves smoothly instead of lagging behind. The missile explosion is size 15 by
  default (`/xenoset effekseerExplosionScale`, alias `explosionsize`, 0.05-50). The bound missile
  plume is built at 5x (`MISSILE_PLUME_SIZE` in `tools/effekseer/efkgen/effects/thruster.py`); it
  is not rescaled at run time, which would move the flame off the tail. Bound effects are sent
  through Architectury's NetworkManager, as AAA sends them; the raw NeoForge send disconnected
  players. Architectury 13.0.8 is compile-only (`libs/`).
- 2026-09-29: A new generated missile explosion after the owner's reference picture: flash,
  fireball, grey smoke cloud, flame rays, streaking sparks and multicoloured embers. It is centred
  and upright; the stand-in was tipped sideways. The tube missile plume is now bound to the
  missile, so it stays at the tail and turns with the flight while the smoke leaves a trail. Ship
  missiles use the positional ship plume.
- 2026-09-29: New generated Effekseer effects replace vanilla particles.
  - A realistic rocket plume for missiles and ship thruster blocks: white-hot core, orange-red
    flame, Mach diamonds, sparks and a reddish smoke trail. Thruster blocks previously drew
    vanilla flames.
  - A Budokai Tenkaichi 3 style Sparking aura (gold ki veil, lightning, embers, ground dust and
    rocks) and a start burst.
  - New generator `tools/effekseer/gen_effects.py` (package `efkgen`, see its README).
  - New `/xenofx play <slot>` test command and `/xenoset` keys `effekseerSparking`,
    `effekseerShipThrusters`, `effekseerSparkingScale` and `effekseerThrusterScale`.
  - Vanilla returns when a switch is off. Network protocol 100.
  - Automated tests pass; not verified in game.
- 2026-09-29: Script files deleted, added or edited on disk under `<world>/XenoNpcs/scripts`,
  `player_scripts` or `forge_scripts` now take effect within about two seconds, the same as a
  save from the scripter. Before, they took effect only at the next world load. The Hakai
  effects are now sent upright (AAA's facing for "up" tipped them 90°).
- 2026-09-28: New Hakai effects in the Dragon Ball Super style, generated by
  `tools/effekseer/gen_hakai_effects.py` and compiled with the Effekseer editor. A purple veil
  clings to the victim, violet shards tear away where the body vanishes from the head down, the
  erase is a soft flash with the last shards drifting away, and the caster's palm holds a purple
  flame. New slots `hakai_crumble` and `hakai_palm`. Effect sizes and switches are `/xenoset`
  keys (`effekseerPunchScale`, new `effekseerHakaiScale` and `effekseerMissileScale`, ranges and
  switches). Checked in the Effekseer editor preview; not verified in game.
- 2026-09-28: Effekseer effects through AAA Particles 2.3.1 (now a required mod on server and
  client). DMZ punch hits (player and NPC, including basic melee), Hakai channel/erase and missile
  thrusters/explosions (tube and ship) play `.efkefc` effects that replace the vanilla particles for
  those uses, with CC-0 Effekseer samples as stand-ins in named slots. New server config:
  `effekseerEnabled`, per-category switches, ranges and a per-tick punch cap. See
  `docs/effekseer-fx.md`. Automated tests and builds pass; the effects are not verified in game.
- 2026-09-28: Guidance V3 (`/xenoguidance system v3`): V1's flight controller, HUD and planner
  with new missile guidance for ship and tube missiles that keeps correcting from the missile's
  live position after the motor cuts off. Boost solves the arc over the planned apex, coast nudges
  the predicted landing onto the target (bounded in Ballistic mode), ships detonate on terrain or
  when they come down short, and a server stall no longer brakes a coasting ship. V1 is unchanged
  (`/xenoguidance system v1`). See `docs/guidance-v3.md`. Automated simulations pass; in-game
  Sable flight is not verified.
- 2026-09-27: Added MyNPCs-compatible `event.npc.getStoreddata()` and `getTempdata()` objects
  with `put/get/remove/has/getKeys/clear`; stored numbers round-trip as numbers. Native NPC
  `meleeAttack` scripts can change or cancel per-hit damage, `damaged` scripts can change incoming
  damage, and `target` cancellation now prevents target acquisition. The script editor includes
  the compatible API and persistent visit-count example. The Full-DMZ flight proxy selects the
  forward fly clip while its body turns; the inline picker uses blue atlas framing and paints its
  value bar at the selected saturation. Automated tests and builds passed; in-game behavior is
  not verified in this pass.
- 2026-09-27: Promoted the edited run-client `hi_wave` to the shipped social default; the run-client
  `wave` already matched the shipped default byte for byte. Exact old shipped `hi_wave` files
  upgrade automatically while operator-edited clips remain intact. Native GeckoLib NPC attacks
  now select a clip from their own baked animation file, restart for a new swing, and release the
  one-shot controller after it finishes. Native NPCs with the optional combat brain disabled gain
  a basic melee goal whose interval follows Melee Speed. The NPC editor now accepts an explicit
  GeckoLib animation JSON asset for rigs whose animation path differs from the model path. Combat
  targeting drops players who enter Creative or Spectator and clears target memory, navigation,
  and hard locks.
- 2026-09-27: XenoAnim Studio can now load clips received from the server, including NPC social
  `wave` and `hi_wave`, as editable timelines. Its new PUSH button saves the draft and requests
  server publication; the server still checks operator permission. An empty library now syncs on
  join and clear so stale clips from another world are removed. NPC script failures appear in red
  chat for nearby operators with a per-NPC repeat limit; manual Run failures also appear in chat
  and the script editor console. The shipped smooth wave and its renderer were not changed.
- 2026-09-27 (Claude follow-up to the Qwen session): audit fixes, flight steering, bundled
  Nashorn with CustomNPCs-style script tabs and hooks, a 1:1 script screen on generated blue atlas
  panels, an inline colour picker, script speech bubbles with palette and shape, and todolist items.
  - Audit fixes: the saga attack-clip fallback looked for an `attack` clip in every loaded GeckoLib
    file (nine DMZ rigs have one) so it never fired; it now checks the NPC's own animation file.
    Heavy Hit / Bone Crusher no longer start the GeckoLib attack clip twice in one tick. The
    `readCached` profile cache keeps one map per logical side (entity ids collide in single
    player). Missing form-display tuning files are cached. JSR-223 named entrypoints were invoked in
    the wrong context and never found. The flight bridge honours Can Use Flight and debounces a
    hopping target. DMZ skill labels no longer draw over sub-GUIs. The dead AimAccuracy field is
    labelled in the CustomNPCs/MyNPCs brain twins and the script API. Completing a quest no
    longer prints the skill-point total when it paid none.
  - Flight: the Full-DMZ proxy now carries `xOld/zOld` (DMZ picks fly clips from `x - xOld`; the
    stale spawn position chose FLY_LEFT/RIGHT/BACK: the sideways look). Rendered flight yaw is
    slewed per frame; Ki Sense lock slews the body at 30 deg/tick while flying; the air chase has
    an arrival deadband and minimum cruise; the legacy brain's flight now blocks ground navigation,
    leash, follower and idle look goals like the saga brain's. An idle NPC with Fly no longer
    hovers: it takes off only against an airborne target.
  - Scripting: `nashorn-core` 15.4 is nested in both jars (server jar: Nashorn only), built with
    `--no-java` and a deny-all class filter. NPCs hold up to 40 script tabs (`ScriptTabs`,
    `ScriptsEnabled`, `ScriptLanguage`), each with its own globals and loaded library scripts, and
    hooks fire from real call sites: init, tick (10 t), interact, damaged, died, kill, target,
    targetLost, collide, dialog, dialogOption. Script API: npc/world/player/event wrappers.
    Script writes and runs need permission 4. MyNPCs/CustomNPCs import keeps every script tab.
  - Script screen rewritten 1:1 with GuiScriptInterface (Settings / 1..n / + tabs, code area with
    highlighting, undo, bracket match; Functions, Clear/Paste/Copy/Remove, Load Scripts; console,
    Language, Enabled, Open folder, API Doc, Examples) on a generated frame ladder.
  - Speech bubbles: `npc.say(text, palette, shape)` and the bridge's
    `XenoPixels.say(npc, text, palette, shape)`; shapes rounded/thought/shout/banner in all four
    palettes; per-NPC default Bubble Shape. Protocol 97.
  - Inline colour picker (gold, over the editor) replaces the separate picker screen; HSV is the
    source of truth, hex is strict, swatches turn gold while open. Tint row gained a swatch.
  - Todolist: quest "Kill target hunts player", trader Ignore damage/NBT, Cape/Overlay/Showing
    Layers on the humanoid model, NPC-to-NPC lines, DMZ skills/techniques text at 0.8, quest
    edits keep their declared skill points.
  - Verified: full suite 2,689 tests green; `build jarJar serverJar`, server jar jarjar =
    nashorn-core only; `buildApiExampleAddon`. NOT verified in a live game (a dev client held
    Sable's natives, so the dedicated-server probe could not start).
- 2026-09-26: Flight grounding fix ("player pressed F" emulation). `NpcCounterpartSync` re-pushed
  `flySkillOn` through `NpcFlightBridge.apply` every four ticks even after a combat brain had
  landed the NPC on a grounded retaliation target, so the navigator flip-flopped FLYING↔GROUND
  (bobbing up/down near the target, sideways drift, never staying grounded when the target lands
  or switches to a walking mob, and `updateAI` navigator-rebuild churn costing client FPS and
  server TPS). The bridge now consults the NPC's live combat target: engaged + grounded target
  keeps the ground navigator (idle skill-hover is untouched; a brain actively directing flight
  still owns it). `NpcCombatBrain.land` also damps residual air-chase momentum like the saga
  brain's, so touchdown stops instead of gliding. New `NpcFlightBridgeTest` pins the decision
  table. Live-client re-test pending.
- 2026-09-26: `XenoNpcScriptScreen` live-client layout fix: the meta field rows (20 px) and the
  right-rail rows (32 px, forced by the 108x22 `AtlasCycle` plus its label) sat on different
  vertical grids, so the two columns visually interleaved, and the Id/Name/Run captions were never
  drawn at all (`EditBox` only uses its component for narration), leaving bare black boxes. The
  meta zone is now one shared 32-px row grid — row 1 Id/Name fields beside +New/Funcs+, row 2 Run
  beside the Language cycle, row 3 the Copy/Paste/Clear/Remove toolbar beside the Enabled cycle,
  row 4 the editor pane beside Load and the list — and the field captions are rendered each frame.
- 2026-09-26: New **NPC Scripting Tool** (`xeno_npc_script_tool`): a 3D HD bluish item built with
  the house tool pipeline — `tools/gen_npc_tool_models.py` now emits a true cuboid model (obsidian
  shaft, gold/steel rings, navy bezel, glass-blue script display with neon-cyan code lines and an
  ice-white caret, gold stylus tip) sharing the `xeno_npc_tool_materials.png` atlas, plus a
  procedurally drawn 16x16 inventory icon. Right-clicking an NPC while holding it (server side,
  op 2) sends the NPC's bound script id and opens `XenoNpcScriptScreen` for that NPC; saving binds
  the script back onto the NPC's combat profile. Two new `ModNetwork` packets
  (`OpenXenoNpcScript`, `BindXenoNpcScript`) append at the end; protocol 95 → 96. Registered in
  `ModsItems`, the creative tab, and `NpcToolsTest` (model/texture/lang/stacks checks). Full suite
  green (2652 tests); not exercised in a live client.
- 2026-09-26: `XenoNpcScriptScreen` rebuilt to the My NPCs script GUI's 1:1 layout (decoded from
  the tracked `GuiScriptInterface` decompile): Copy/Paste/Clear/Remove toolbar above the code
  editor, a 108-px right rail (+New / Funcs toggle, Language cycle, Enabled cycle, Load, and a
  scrolling Scripts⇄Methods list where clicking a function inserts a `Name(` stub at the caret),
  and a console strip with its own [Copy]/[Clear] and scrollable transcript. The reference's
  Website/Examples/API-Doc/Open-Folder links are deliberately omitted: Xeno has no such URLs and
  the repo forbids inventing backends. Remove arms on a second "Sure?" press like the reference's
  confirm. Full suite green (2652 tests); not exercised in a live client.
- 2026-09-26: Fixed the flight regression: the Brain-tab Fly action (`brainFly`) defaulted **off**
  for profiles saved before its key existed, so decoupling the hub Fly skill row from it silently
  grounded every configured flyer (air chase dead: player airborne, NPC on the ground). It now
  defaults on like every other movement action and like `CanUseFlight` already did — an absent key
  keeps the pre-key behavior, an explicit operator off-switch is still honored. Three flight-policy
  tests updated to pin the decoupling with an explicit `false` instead of relying on the old
  default, plus a new absent-key regression test. Not yet re-verified in a live client.
- 2026-09-26: The script editor takes whole-script clipboard pastes: Ctrl+V inside the code pane
  and a new Paste button splice the clipboard as one source line per newline (vanilla `EditBox`
  paste strips newlines and would collapse a copied script into one line). Replaces the focused
  line's selection, normalizes CRLF, and trims to the 32 768-character store budget with a visible
  warning instead of a doomed save. Client-side only; not exercised in a live client.
- 2026-09-26: The native color picker is no longer blurry: `XenoColorPickerScreen` now extends the
  house `UnblurredScreen`, skipping the 1.21 post-process blur that smeared the procedurally drawn
  palette, cursor, and hex field painted before `super.render`. The CNPC sub-GUI pickers inside the
  DMZ-port screens keep their parent canvas scaling and are unaffected.
- 2026-09-26: Scripting groundwork (todolist 11): a JSR-223 engine seam (`npc/script/`) with an
  honest no-op fallback, server-owned script storage (`scripts/<id>.json`, protocol 95), per-NPC
  `ScriptId` binding, op-2 gated fetch/run packets, and a line-numbered script editor screen
  (`XenoNpcScriptScreen`) wired into the Advanced and Global tabs. **No engine executes scripts
  yet** — bundling GraalJS or Nashorn-standalone is an owner decision; the seam auto-discovers
  either with no code change. 49 new tests; nothing exercised in a live client.
- 2026-09-26: Global tab (todolist 6): Natural Spawns are real — a world-store rule set
  (`spawns/<id>.json`), a server-side spawner (player ring 24–48 blocks, biome/time gates, per-rule
  caps, despawn), client mirror + sync packet, and fully wired editor rows. Linked (global) now
  points at the live per-NPC linked editor instead of a dead list. Recipes stay disabled,
  relabelled `Recipes (no bench)`: no recipe consumer exists, so authoring would be fake.
  35 new tests. Also fixed two editor save paths (bank edits, scene draft saves) that claimed
  revision 0 and so were refused on every save after the first.
- 2026-09-26: Unavailable-row sweep (todolist 8) completed: every remaining disabled control in the
  native editor is now either a correct conditional (limit reached, nothing selected) or an honest
  unbuilt feature with an accurate on-screen reason (cape/overlay/layers, night alternate profile,
  mark availability, NPC-to-NPC lines, trader ignore-damage/NBT/marketname). The stale
  "no native writable registry" reason on the Line Selector page was corrected. Those unbuilt
  clusters are the remaining Phase M schema work.
- 2026-09-26: The Forms tab of the native XenoNPC editor now opens a real DMZ Form Maker
  (`DmzFormMakerScreen`) instead of only selecting definitions: Edit/Create for normal forms and
  Edit Stack/New Stack for stack forms, every DragonMineZ form field editable with the matching
  row (text, number, scale, yes/no, colour with the shared picker, JSON), debounced revision-checked
  auto-save over the existing form editor channel with a Save Now button, and a New Copy action that
  duplicates the draft. Operator level 2 is still required to save; server refusals show on the
  status line. Main and test compilation and the appearance/quest suites pass; the screen has not
  been exercised in a fresh client.
- 2026-09-26: Fixed a real leak behind the "quests still give skill points" report: completing a
  CNPC/CustomNPCs quest paid a hard-coded +2 XenoSkill points, bypassing the native-giver-only
  origin policy. The completion sync now only persists the third-party save and posts an
  informational message; a regression test pins that the grant is gone. Native quest paths were
  already proven leak-free by 18 new reward-toggle tests. In-game third-party completion not
  re-verified.
- 2026-09-26: Remade the `wave` and `hi_wave` social clips: the arm swings forward, holds, performs
  exactly three eased side-to-side oscillations, then returns to rest (2.5 s / 3.0 s), authored on
  the real XenoAnim part positions with ease-in-out-sine between every key. Old clip hashes moved
  to the upgrade set so shipped copies re-seed; operator-edited files are preserved. Clip content
  tests (9 new) pass; on-screen motion is not yet verified in a client.
- 2026-09-26: `/xenoset npcAttackStartRadius` (aliases `npcattackradius`, `attackradius`,
  `attackstartradius`) now controls how close an NPC must be before melee attacks start, in blocks
  centre to centre. Default 1.0 replaces DragonMineZ's 4.5-band fallback, so an NPC only starts
  hitting once it is essentially touching its opponent (hitbox widths still floor the reach, so a
  standard 0.6 + 0.6 pair resolves to 1.2). A per-NPC "Melee Range" above zero keeps overriding it,
  and setting the key to 4.5 restores the old global reach. Focused Combat and Config tests pass;
  in-game melee feel is not yet verified.
- 2026-09-26: Item Giver now checks editable player level, time, quest, dialogue, faction and
  scoreboard availability before giving items. Shared Xeno dialogue-view state persists per player
  and survives death, enabling before/after dialogue checks. All native roles now have an Edit Role
  route to the relevant existing page. Focused tests and all 2,509 tests passed; distribution and
  API example addon builds passed. The 0.5.0 server jar has no nested jarjar entries. In-game
  editor and delivery behavior remain unverified; optional FTB Quests gating is still open.
- 2026-09-26: Added native XenoNPC Item Giver delivery. Its nine authored stack slots, five
  selection methods, per-player seconds/once/Minecraft-day cooldowns and configurable gift lines
  are server-owned and editable under Advanced. Item Giver state persists across player death;
  editor saves validate slot count, item NBT size, modes and line lengths. The full 2,506-test suite,
  distribution and API example addon builds pass; the server jar has no nested jarjar entries.
  In-game delivery is unverified. The follow-up above adds the native availability conditions;
  optional FTB Quests gating remains open.
- 2026-09-26: Added the native Follower job. It selects a nearby XenoNPC, MyNPCs NPC or CustomNPCs
  NPC by visible name, uses the native navigation and movement arbitration, yields to combat and
  scenes, and has an editable target name with bounded server validation. Patrol and home-leash
  movement stand down while following is active. The full 2,502-test suite, distribution and API
  example addon builds pass; the 0.5.0 server jar has zero nested jarjar entries. In-game pursuit
  remains unverified.
- 2026-09-26: Implemented the native XenoNPC Healer job with a server-side timed scan, configurable
  range, target filter, interval and up to sixteen namespaced potion effects. Added the Advanced
  Healer editor page, bounded profile persistence and save validation. The job obeys Job Enabled
  and remains independent of role. The 2,500-test suite, distribution and API example addon builds
  pass; the current 0.5.0 server jar has zero nested jarjar entries. Gameplay behavior still needs
  a fresh in-game check.
- 2026-09-26: Native XenoNPC Display Size now scales the hitbox by default; the existing Hitbox
  multiplier remains editable. `/xenoset xenoNpcSizeScalesHitbox off` makes size visual-only, and
  `on` restores linked collision. The setting updates loaded NPCs and uses existing entity-data
  sync, so protocol remains 93. Fixed the client crash when a texture-only DMZ Slug variant such
  as `saga_slug_giant.png` is selected as a GeckoLib model: the renderer uses the existing base
  Slug rig and shared saga animation, with a safe model fallback for missing assets. The Size row
  also scales these textured models. The fresh 2026-09-26 13:10:27 crash report established the
  cause; post-fix in-game behavior is not yet verified. Focused tests, all 2,498 tests,
  distribution and API example addon builds pass.
- 2026-09-26: Native XenoNPC appearances now sync model kind, GeckoLib asset, texture, skin and
  visual size; the renderer resolves pinned DragonMineZ master geo and animation resources.
  The editable Hitbox multiplier is synced and applied to entity dimensions. Added a native boss bar
  tied to NPC visibility, health, name and selected color.
- Added nine Zeni coins and three notes, generated 256-pixel art, physical deposit and exact-change
  withdrawal at each native NPC bank, and a separate optional MMO Econ wallet mode. Bank withdrawal
  checks inventory capacity before debiting the vault. Generated cuboid neon models replace the
  flat item models for the wand, Pather, Cloner, JAR, Mounter and Teleporter.
- Added editable faction aggression toward selected ordinary mob IDs; native XenoNPC and DMZ master
  targets are excluded. Faction settings persist and sync to the client. Main protocol is 93.
  The 2,494-test suite, distribution build and API example addon build pass. Server jars have zero
  `META-INF/jarjar/` entries. These additions still need fresh in-game checks. Native scripting,
  remaining Advanced roles/jobs, layers, availability, global recipes and natural spawns remain
  incomplete.

- 2026-09-26: The native XenoNPC editor now waits for a revision-scoped server save result before
  closing, retains the draft on rejection, and refreshes dependent choices after toggles, cycles
  and new store IDs change. Text fields keep partial values while being edited. Added a searchable,
  scrollable atlas picker for loaded vanilla, mod and enabled resource-pack PNG textures on the
  Display and Night pages. The Display page now accepts a Minecraft player-skin name, validated
  by the server and resolved from its synced UUID on the native humanoid and GeckoLib paths.
  Added a persisted Job Enabled switch for Bard and Guard, defaulting
  existing jobs to enabled. Protocol 92 appends the native editor save-result packet. The 2,487-test
  suite and client/server distribution build pass; picker and save behavior still need a fresh
  in-game interaction check. URL and server-shared local skins remain pending; player-name skin
  rendering also needs a live check.

- 2026-09-26: Native XenoNPC Ki Sense retaliation lock now smoothly aims toward the target's
  midpoint every server tick. Ki projectiles use exact motion lead while the lock is valid at
  launch; they do not home after launch. Native flight closes to a one-block center gap for normal
  hitboxes and a collision-safe gap for large hitboxes. Saga and older combat brains land when a
  target switches DMZ Fly off and walks on supporting blocks, including a transient false
  `onGround` tick. Other NPC and Multi-Form chase gaps remain at three blocks. Focused and full
  tests passed (2,483 tests, 0 failures), the distribution build passed, and the server jar has
  zero `META-INF/jarjar/` entries. In-world pursuit and tracking remain not verified.

- Corrected XenoNPC Ki Sense retaliation targeting: acquiring an attacker now faces and tracks
  that entity while the skill, Retaliate mode, line of sight, and DMZ Ki Sense range remain valid.
  This is an NPC server-side equivalent; DMZ's Z-key lock-on is client-only and player-owned.
- Kept Saga flight active when an airborne target reaches the NPC's height, ended flight as soon
  as that target lands (including during a combo), and widened the close melee hover gap to avoid
  crossing and sideways correction next to the target. Automated tests pass; visual flight behavior
  still needs a fresh gameplay check.
- Added native XenoNPC DMZ-tab toggles for knockback immunity and damage immunity, backed by the
  existing `Knockable`/`Punchable` profile fields and save-policy keys.
- Added an opt-in native XenoNPC "Lock retaliator" setting under the DMZ Ki Sense skill. It
  acquires the current attacker when Ki Sense and Retaliate are enabled; other NPC integrations
  keep their existing target rules.
- Refined Saga flight to start only toward an airborne target above the NPC, switch flight off
  when its target is grounded, cancel upward carry on landing, separate altitude correction from
  horizontal standoff steering, and yield the vanilla leap goal while DMZ flight owns movement.
- Scaled text on the native DMZ Skills and Techniques pages down uniformly by 10% so long labels
  and controls fit the atlas rows more comfortably.
- Follow-up validation on 2026-09-25 passed: 2,481 tests, 0 failures. This verifies source and
  regression behavior; it does not verify flight or Ki Sense lock-on in a live world.
- The user subsequently confirmed dialogue bubble colors now render as selected. Flight and Ki
  Sense target-lock behavior still need gameplay verification.
- Added native XenoNPC DMZ controls for Fly, configured skill levels, selectable DMZ techniques,
  per-technique damage/cooldown levels, and form/stack mastery. Combat flight checks both the
  brain's Fly action and the NPC's DMZ Fly skill; the DMZ hub Fly control enables both together.
- Added the opt-in V9 DMZ saga combat profile with configurable flight, ordered attacks, ki waves,
  and clash behavior; deflection defaults off for V9. Melee now remains range-gated by the shared
  hit path.
- Existing native XenoNPC profiles without authored attack slots now default to the four requested
  ordered clips: right, left, right, left `combat.xeno_dmz_punch_*_v4`. The sequence takes priority
  over the generic punch and suppresses its vanilla swing; MyNPCs and CustomNPCs profiles are left
  alone.
- Native Saga flight now holds the movement claim, stops the ground navigator and leash while it
  owns flight, and releases gravity and the claim when flight ends. It still requires both the V9
  Brain Fly action and the enabled DMZ Fly skill.
- Smoothed Saga flight near its target: it brakes toward a three-block horizontal gap, corrects
  altitude independently, starts at a four-block height difference, stays airborne while its
  target is airborne, and lands when the target is grounded.
- Unified speech and dialogue palette resolution so a stored lowercase `red` value resolves to the
  generated red bubble atlas. Automated coverage follows the profile palette through its visual
  options and checks the shipped red sprite.
- Fixed Advanced Dialogue answer colors being lost while quest options are filtered and sent to
  clients. Filtering keeps each answer palette alongside its server option index, and the packet
  that opens dialogue carries speaker-line and per-answer palettes so a saved red answer resolves
  to the red answer-bubble atlas. Main protocol is now 91.
- Expanded kill-NPC objectives and the editor picker to Xeno NPCs and optional MyNPCs/CustomNPCs
  entities by full visible name. Quest reward commands support `@dp` for the claimant and `@p`
  from the quest-giver position, with claimant-position fallback.
- Added repeat-rule, skill-point, reward-action, completion-palette, and completion-frame controls
  to global quest authoring. The default skill-point reward remains two; reward actions are opt-in.
- Added a scrollable quest journal beside inventory on the left and a server-confirmed completion
  card. Both keep the world sharp instead of invoking Minecraft's blur. Generated blue, gold,
  green, and red atlas art provides journal, toast, rounded, and banner frames.
- Kept zero as a valid dialogue-bubble head offset while restoring the prior 0.7-block default for
  NPCs whose saved profile predates that setting.
- Added the completion-popup packet. Release artifact version is
  `0.5.0-1.21.1`.
- Automated validation on 2026-09-25 passed: 2,479 tests with 0 failures, distribution build,
  server jar, and API example addon build. The server jar contains no `META-INF/jarjar/` entries;
  the DragonMineZ jar hash matches `gradle.properties`. A fresh dev client loaded after running
  `runClient` without `-PofflineMcMeta`; an earlier offline-metadata launch lacked LWJGL natives.
  The loaded client's log reported a Controlify mixin error. No world was opened, so combat flight,
  attack appearance and bubble color remain unverified in gameplay; no screenshot was taken.

Version-bound history for commits reachable from [`origin/1.21.1`](https://github.com/AgentMelinda/XenoPixelsNetwork/tree/1.21.1). Detailed pages preserve exact Git ranges and commit subjects; summaries are based on repository diffs rather than commit titles alone.

## History notes

- `v0.0.5` through `v0.1.6` are the **Minecraft 1.20.1 / Forge** history inherited by the current branch.
- `v0.1.8-1.21.1` aliases `v0.1.6`; no `v0.1.7` tag exists, and the alias still contains the `0.1.6-1.20.1` build.
- The actual **NeoForge 1.21.1 / Sable port** is commit `aeb771d` in the `v0.1.9` range.
- Untagged local work is not attributed to a released version.

## Unreleased — neon DragonMineZ character screen, Zanzoken disguise fixes

### Changed

- **The themed DragonMineZ menus and HUD are now the default.** `dmzMenuMode` ships as `theme`
  instead of `stock`, so DragonMineZ's own screens — its widgets, scrolling, packets and validation
  — come dressed in Xeno chrome out of the box, and the lock-on, radar and four scouter atlases
  follow the same setting. Config version 11 moves anything written before it; a mode chosen at 11
  or later is kept, so nobody who picks a mode is overridden. Every other route is still one command
  away: `/xenohud menus stock` for DragonMineZ untouched, `screen` and `neon` for the two rebuilds
  of the character page.

- **`/xenostats` — DragonMineZ stats past DragonMineZ's cap, and control of that cap.**
  - `/xenostats limit <1000-2147483647>` raises the per-stat ceiling, `limit off` lifts it as far as
    DragonMineZ can physically hold, `limit dmz` hands the setting back to DragonMineZ's own config
    (the default — a server that has not asked for this keeps DMZ's exact behaviour).
  - `/xenostats set <players> <str|skp|res|vit|pwr|ene|all> <value>`, `add` for a delta (saturating,
    so a large `add` lands on the ceiling rather than wrapping negative), and `get <player>`.
  - The writes are DragonMineZ's own: every value goes through `Stats#setStat`, so DMZ's stat-change
    events fire, its attributes are reapplied and its clamp still runs — it just has a higher number
    to clamp to. Nothing is written behind DragonMineZ's back, so a value set here survives a reload
    exactly as one set with DMZ's own command does.
  - **2,147,483,647 is a wall, not a policy.** DragonMineZ stores each stat in an `int` field on
    `Stats`, so nothing either mod configures raises it; `limit off` says so rather than implying
    "unlimited".
  - Implemented as one `@ModifyReturnValue` on `GeneralServerConfig$GameplayConfig#getMaxValue()`,
    the single getter every DMZ cap funnels through — `Stats#clampStatValue` (the setters),
    `StatsData#getConfiguredMaxValue`, `clampStatToConfiguredMax`, and the `+` button's budget in
    `getMaxAllowedIncreaseForStat`.

- **`/xenopixels npcprofile combat`** — sets `punchable` / `knockable` on the NPC you are looking at
  directly on the server's copy of the entity, and with no argument reports whether a profile is
  actually stored there. Added as the way to tell an editor-path problem from a storage problem when
  a flag does not survive a restart: the editor route is client-driven, and a parsed profile cannot
  distinguish "no profile stored" from "profile stored with defaults", because both flags default to
  true when absent.

### Fixed

- **NPC quest and dialog rewards ran nothing on a server with command blocks disabled.** My NPCs'
  `EspiUtilServer.runCommand` returns "Cant run commands if CommandBlocks are disabled" *before* it
  substitutes `@dp` and before it dispatches, so a reward of `xenopoints add 5000 @dp` silently did
  nothing. This was never a difference between the NPC mods — CustomNPCs carries the identical
  guard. The real fix is `enable-command-block=true` in `server.properties`; for servers that want
  command blocks to stay off, the new `npcCommandsIgnoreCommandBlockSetting` (default **off**,
  `/xenoserver set npccommands true`) answers that one check differently for the NPC command path
  only, leaving real command blocks as disabled as the server owner set them. Off by default because
  NPC commands run at permission level 2 — or 4 with My NPCs' own `NpcUseOpCommands` — so it is the
  operator's call.
- **`{RefPlayer}` is now rewritten in dialog commands too, not just quest rewards.** Five My NPCs
  classes run NPC commands and the rewrite covered exactly one of them; a dialog option is the other
  route a server actually uses to hand out points.
- **A Zanzoken ring no longer collapses on the first hit.** Every image had one point of health and
  *any* removal of *any* image dispersed the whole ring, so with `ZanzokenConfusion` now drawing
  attackers onto the images, the first mob swing ended a ten-second disguise instantly. Only a
  player can destroy an image now — that is the read the technique is built around, and it still
  resolves the whole trick — while a mob's swing, splash damage and the environment leave it
  standing. Losing an image any other way re-homes whoever was hunting it onto another image instead
  of handing them back the real fighter, and the ring ends when nothing is left standing.
- **Stat values just under a unit no longer read as a thousand of the last one.** `StatText.format`
  printed 999,999,999 as `1000.00M`, so the themed panel showed `1000.00M` where the stock panel
  beside it showed `999,999,999` — it looked like the two panels disagreed about the number. Values
  that round into the next unit now roll into it (`1.00B`, `1.00M`), which affects the themed
  multiplier column, the neon screen and the first rebuild alike.
- **The dodger's own body now fades with their Zanzoken images.** Every copy in the ring dimmed as
  it aged while the real player rendered fully solid in one of the slots, so the one body an
  attacker had to pick was the one that announced itself. The real body now draws through the same
  alpha curve and the same configuration as the images, so all of them dim together. The copies and
  the HUD portrait are excluded, so neither double-fades.
- **Zanzoken now actually confuses AI for as long as the ring stands.** The images were marked as
  standing for `zanzokenAfterimageTicks` (40 by default) when the key was pressed, and nothing
  re-marked them when a landed read put the ring up for `zanzokenRingTicks` (200). The mark
  therefore expired four fifths of the way before the ring did, and vanilla mobs, DragonMineZ saga
  enemies and CustomNPCs / My NPCs went back to tracking the real body through a ring that was still
  plainly on screen. The mark now covers whichever duration is longer.
- **Attackers already swinging at the dodger are now moved onto an image.** Only target
  *acquisition* was blocked before, so anything mid-fight simply kept hitting the real body — which
  is most of what is attacking you when you press the key. Each attacker within 32 blocks that had
  the dodger is pointed at one of the images instead, drawn independently so a crowd does not
  converge on one body. Redirecting rather than dropping keeps them fighting rather than stunned,
  and it resolves itself: striking any image disperses the ring.
- **Every DragonMineZ lock-on naming the dodger is redirected**, not just the one belonging to
  whoever swung. A second hunter standing off to the side kept a marker on the real body, and one
  correct marker tells the whole room which body to hit.
- **The disguise now ends with the bodies.** Striking an image drops the whole ring early, but the
  "images are standing" mark ran to its full lifetime regardless, leaving a fighter who had already
  been found untargetable for the remainder.

### Added

- Native Xeno NPC Quest-role authoring now points operators to dialogue-linked quest offers and
  configured NPC hand-in, while keeping the role passive. Advanced > Dialogs can open the existing
  shared-dialog world-store editor and return to its assignment slots. Filtered quest choices retain
  their server option identity so clicking a later visible option cannot dispatch an earlier hidden
  choice; unsupported Job Enabled and NPC-to-NPC line controls remain disabled with accurate reasons.
- `/xenohud menus neon` opens a second rebuild of DragonMineZ's character page, built from
  `dragonminez_our_style_clean_example_dimensions_2.zip`: whole-slab INFORMATION and STATISTICS
  panels, the top nameplate, three orb icons, six captioned navigation buttons, and the live
  DragonMineZ character rendered inside the scan ring between the panels.
- `tools/gen_dmz_neon_atlas.py` builds that screen's atlas from the vendored bundle under
  `tools/source/dmz_our_style_v2/`. Because the panels are single slabs with their rows drawn into
  the art, the generator also *measures* the row interiors off the art and emits them as constants,
  and samples the readout palette from the bundle's own reference render rather than approximating
  it. It writes a contact sheet and an anchor overlay so a bad crop or a misplaced row is visible
  without launching the game.
- `tools/preview_neon_screen.py` composites the screen from the generated atlas using the same
  layout arithmetic the screen uses, on DragonMineZ's guaranteed 320x240 canvas, so an overflowing
  panel or a colliding readout is caught before a client boot.

### Notes

- Nothing existing changed behaviour: `stock`, `theme` and `screen` all work exactly as before, the
  shipped default is still `stock`, and the first rebuild (`XenoDmzStatsScreen`, from the older HD
  kit) is untouched. The two rebuilds are meant to be compared in play.
- The neon rebuild currently covers the character page. The other five menus still open
  DragonMineZ's own screens in Xeno chrome, so nothing behind V is unreachable.
- Stat spending still sends DragonMineZ's own `IncreaseStatC2S`; the seventh row in the art is the
  training-point total, which has no DMZ packet, so its `+` is drawn dimmed and does nothing.

## v0.3.4-1.21.1 — 2026-09-09 — themed DMZ controls and HUD assets

### Fixed

- The skills interaction mixin now uses DragonMineZ's exact `SkillsMenuScreen` receiver for both
  wrapped superclass calls, preventing the MixinExtras signature failure and follow-on verifier
  crash reproduced during client startup.
- Quest-completion compatibility now reads the inherited packet player from the real My NPCs or
  CustomNPCs packet superclass instead of shadowing a field that is not declared on the target.
- Xeno-themed menu buttons now use the approved exact-UV cyan, gold, and red atlases from
  `dragonminez_our_style_full.zip`, replacing the remaining green stock rows and white controls.
- Lock-on, radar, and blue, green, purple, and red scouter HUD textures follow the existing Xeno
  menu-theme setting. Stock mode continues to use DragonMineZ's original resources.

## v0.3.3-1.21.1 — 2026-09-09 — Xeno DMZ menu interaction polish

### Fixed

- DragonMineZ increment buttons now use the supplied Xeno `+` glyph and are painted into DMZ's
  actual `10x10` normal/hover UV cells, so the right side is no longer cut off in the character or
  settings screens. The replacement character screen shares one render/click rectangle and adds
  cyan hover feedback.
- The skills screen's three compact information regions use label-free dark Xeno frames instead of
  falling back to DragonMineZ's green stock panels, while unrelated `menusmall.png` UVs stay intact.
- Hidden, scissored skills category tabs can no longer steal clicks or hover from the visible list.
  Skill rows now remain interactive through the level column, stop before the scrollbar, and show
  the selected row in gold with cyan hover feedback.

## v0.3.2-1.21.1 — 2026-09-09 — sharp DMZ menus and stable Zanzoken ghosts

### Fixed

- Zanzoken afterimages no longer remap name-tag font buffers to DragonMineZ entity translucency.
  Only the compatible `NEW_ENTITY` vertex format is replaced, preventing the confirmed client crash
  reporting missing `UV1` and `Normal` elements. Ghost rendering now also restores its pose stack
  through `finally` when an entity renderer fails.
- The rebuilt DragonMineZ character screen now uses DragonMineZ's integer-scaled virtual UI and
  matching mouse coordinates instead of rendering directly in fractional screen space.
- The rebuilt character atlas now keeps supplied PNG artwork at four times its logical draw density
  and records separate source and destination dimensions, avoiding the permanently blurred
  pre-shrunk sprites used by the previous atlas.
- Xeno replacement and themed DragonMineZ menus use the transparent dark background without
  Minecraft's post-process world blur. Stock DragonMineZ menu mode is unchanged.

## v0.3.1-1.21.1 — 2026-09-09 — controllable NPC combat, migrated content and HUD fixes

**Verification status:** JUnit suite **448 tests, zero failures/errors**. The rebuilt DMZ menu
atlases pass their generator checks without runtime blur metadata, and the client, server, sources
and jar-in-jar release artifacts build against DragonMineZ 2.1.3.

### Fixed

- The CustomNPCs and My NPCs DMZ wand pages now save independent Knockable and Damage controls.
  Damage-off cancels all incoming damage, while Knockable-off blocks vanilla, DragonMineZ and
  XenoPixels combat displacement without freezing ordinary NPC navigation. Both flags default on
  for existing profiles and are available to both scripting APIs.
- Quest, dialog and script world data now goes through a conservative CustomNPCs-to-My NPCs
  converter. It preserves IDs and structure, rewrites only the known resource/package namespaces,
  normalizes XenoPoints/DMZPoints player reward tokens, writes atomically, and never replaces an
  existing My NPCs file.
- The right-side inventory effect rail is now the sole inventory renderer for active effects;
  Minecraft's competing effect panel is canceled so the same effects are not drawn twice.
- Sparking charge duration and cooldown settings are included in server-config synchronization,
  keeping client feedback aligned with the server's configured timers.
- The client afterimage command now also rejects server-sent Zanzoken body copies and clears
  existing mirages immediately instead of disabling only locally predicted trails.

- Profiled CustomNPCs and My NPCs fighters now use their DMZ/Xeno profile as the sole combat stat
  source when `npcDmzStatsAuthoritative` is enabled. Native melee/ranged damage, knockback, regen,
  explosion strength and all four native resistance channels are neutralized, so a saved
  `Resistance=2.0` can no longer make a DMZ-profiled NPC immune to attacks or ki blasts and native
  melee damage is no longer added a second time.
- Both NPC mods' scripted tick events now run at the configurable `npcScriptTickInterval` (default
  every tick) instead of being hard-wired to once per ten ticks. The original ten-tick invocation
  is replaced rather than duplicated. Quest completions also request immediate persistence/client
  synchronization after rewards and next-quest assignment.
- `/xenopoints` and DragonMineZ's `/dmzpoints` accept CustomNPCs/My NPCs `@dp`, `{RefPlayer}` and
  formatted display-name targets. Literal dialog-player tokens resolve either the real command
  source or the real player at the NPC fake player's command position, and both commands send the
  normal DragonMineZ resource sync immediately.
- Zanzoken stationary body copies now carry alpha through translucent textured render types. Fade mode
  1 starts semi-transparent and fades, mode 2 starts solid and fades, and mode 3 stays at a
  configurable semi-transparent alpha before vanishing. Mode and alpha are synchronized server
  settings, while the existing `zanzokenRingTicks` controls the live ring copies' lifetime.
- DragonMineZ and Xeno status effects no longer stack as oversized vanilla cards beside the
  inventory. They share a compact, spaced icon rail with DragonMineZ/Xeno color accents and hover
  tooltips, clamped inside narrow screens.
- Corrected the DragonMineZ aura-layer mixin callback descriptor to use the renderer's real
  `BakedGeoModel`, `RenderType`, `MultiBufferSource` and `VertexConsumer` parameters, fixing the
  confirmed `InvalidInjectionException` from the runtime log.
- Rebuilt the supplied DMZ menu assets at 4x with no texture blur flag and aspect-preserving
  contain fitting, so supplied frames are no longer cropped to mismatched stock rectangles.
- Replaced the DMZ menu showcase/mockup panels with the bundle's genuinely clean Xeno panels, so
  baked labels such as `Clean (Empty)`, `ELEMENTS (EXTRA)` and duplicate menu titles cannot overlap
  live menu content. The quest atlas now preserves DMZ's node sprites and the skills screen keeps
  DMZ's functional three-region small-panel sheet instead of stretching one decorative strip over it.
- Tightened the replacement stats screen layout: the section header no longer covers the character
  rows, all six statistic rows fit inside their panel, and the detached decorative color orbs were removed.

### Added

- Configurable `npcDmzStatsAuthoritative`, `npcScriptTickInterval`, `zanzokenGhostFadeMode` and
  `zanzokenGhostAlpha` server keys.
- Completed and retained Claude's CustomNPCs-to-My NPCs live entity, world-data and clone migration
  work, including non-destructive conversion and its existing regression coverage.

## v0.2.4-1.21.1 — 2026-09-09 — My NPCs port, world migration and the DMZ menu rebuild

**Verification status:** JUnit suite **440 tests across 77 classes, zero failures/errors**, and all
four asset generators (`gen_dmz_hd_atlas`, `gen_dmz_menu_themes`, `gen_bt3_menu_atlas`,
`gen_bt3_hud_atlas`) report their output up to date. Client, server and jarJar artifacts build.

Not yet exercised in a play session, and worth doing in this order: the world migration against a
**copy** of a server world (it is one-way, and it cannot recover NPCs from a chunk that has already
loaded and re-saved without CustomNPCs); the rebuilt character screen behind `/xenohud menus screen`;
the aura holding a steady size while taking hits; and NPC attack animations no longer competing with
the vanilla swing. The Xeno HUD, its overlay and the modernunified renderer were deliberately left
untouched throughout.

### Fixed


- CustomNPCs NPCs already placed in a world now survive the move to My NPCs. They are stored in
  chunks as entities with `id: customnpcs:customnpc`, and with CustomNPCs uninstalled vanilla cannot
  resolve that id: it logs "Skipping Entity with id" and **discards the entity**, permanently once
  the chunk saves again. `EntityType.by` - the one point every load path funnels through - now
  rewrites that id first, so My NPCs loads them instead and each chunk migrates once and stays
  migrated. Unlike the clone converter this keeps the CustomNPCs-only keys: a clone is a template
  being re-saved, a live entity is somebody's actual NPC.
- Quest and other CustomNPCs world data is copied across to My NPCs on server start. No conversion
  was needed: both mods write the same folder layout, and comparing a world opened under each, the
  dialog files differed only in a timestamp, the player data was byte-identical, and no world-data
  file carries a `customnpcs:` namespace at all. Clones are the exception and are converted on the
  way. The migration never overwrites a file My NPCs already has and never modifies the CustomNPCs
  folder, so re-running it is a no-op and reinstalling the old mod undoes it. Controlled by
  `migrateCustomNpcsWorldData` (default on) and runnable on demand with `/xenopixels migratenpcs`.

- Auras no longer jump up and down. The stat-driven scaling keyed its power-up ramp off the
  "who is being drawn" marker that only DragonMineZ's aura *layer* sets, while `getAuraScale` is
  also called from the world aura, the first-person aura and the character-screen preview - so the
  height multiplier applied on some frames and not others, and could be attributed to whichever
  entity had been drawn last. The ramp is now keyed on `StatsData.getPlayer()`, the object DMZ hands
  the method itself, which also makes the scaling work in first person where it never did. The
  bookkeeping moved into a `RampTable` with no Minecraft types and eight tests, since ownership is
  the part that broke rather than the curve.
- `/xenohud menus` defaults to `stock` again, so DragonMineZ's menus behave exactly as DMZ ships
  them. The rebuilt character screen and the theming both remain; `/xenohud menus screen` or `theme`
  brings either back.

- The DragonMineZ character screen is rebuilt from the master bundle's HD element kit and is now what
  `V` opens by default. Separate panels, header shells, row shells, navigation buttons and icons are
  each drawn at their own proportions instead of being forced through DMZ's 141x213 rectangle, with
  the numbers still read live from `StatsData`, the `+` buttons still sending DMZ's own
  `IncreaseStatC2S`, and the multiplier column in the bundle's `#FECC22`. `/xenohud menus` still
  offers `theme` and `stock`.
- Every piece of that screen is editable through the existing elements editor, as a fifth surface
  beside Panel, Chips, Ki Menu and Party - position, scale, colour, bold, font and per-part reset for
  all 21 parts, in its own config file so nothing in the HUD's layout moves.
- The other five menus now theme from the bundle's named per-menu panels rather than rectangles cut
  out of a full-screen mockup, which is what previously cost 20-38% of several panels. A slot whose
  art would lose more than 35% to the aspect crop is now left as DragonMineZ drew it - the redesign's
  header bars are about 2.2-2.9 wide for their height against DMZ's 5.1 strip, and no crop makes that
  anything but a smear - and the generator reports every skip.
- The new atlas generator refuses to build if a source asset is fully transparent, and writes a
  contact sheet of every sprite at the size the game samples it. Both earn their place: all six
  `clean_icon_*.png` in this kit are empty and the clean orbs are hollow rings, which is exactly how
  six blank navigation buttons shipped from the previous bundle.

- Attacking NPCs no longer look like their limbs snap. Both NPC mods' melee goal calls
  `swing(hand)` and then `doHurtTarget`, and we hook the second of those to start the configured
  attack clip - so the vanilla arm swing and our clip landed on the same model in the same tick, a
  DragonMineZ punch playing underneath a humanoid arm swing. The swing is now suppressed for exactly
  those NPCs whose attack we animate ourselves, and left alone for every other entity in the game,
  which keeps it as the NPC mods' own melee animation where nothing better exists.

- CustomNPCs clones can now be used in the My NPCs cloner. The two formats are near-identical - real
  saves share 170 of ~190 keys, and only two values in the whole tree carry the mod's own namespace -
  so `NpcCloneConverter` rewrites `customnpcs:` to `mynpcs:` by walking the tag tree (leaving
  `dragonminez:` and `minecraft:` ids alone), drops the 17 keys the fork has no field for, and seeds
  the 7 it expects at the values a native clone carries. Anything it does not recognise is kept
  rather than dropped, so a My NPCs update costs fidelity, not data. Pasted files convert on read
  and the originals are never rewritten; `/xenopixels importclones [tab]` makes it permanent by
  writing through My NPCs' own saveClone. Covered by 13 tests against real clone files from both
  mods.
- An NPC with no usable attack animation now performs a plain vanilla swing. Both authored paths can
  be unavailable at once - the Gecko addon is CustomNPCs-only and off the runtime, and DragonMineZ
  clips need FULL appearance mode - which previously meant an attacking NPC showed nothing at all.

- `/xenopoints` now understands the NPC mods' own player tokens. CustomNPCs and My NPCs rewrite
  `@dp` to the interacting player's *display name*, and skip the rewrite altogether when no player is
  in scope, so what reached the command was either a literal `@dp` or a nickname - neither of which
  vanilla's entity selector can resolve, and both of which failed. The command now falls back to
  resolving `@dp` and `{RefPlayer}` against the player it is running for, and matches a plain name
  against usernames first and display names second, ignoring any formatting codes baked into them.
  Vanilla selectors are untouched: brigadier only reaches the fallback when the selector fails to
  parse, so `@a`, `@p`, a username and a UUID all take exactly the path they did before.

- The NPC integration now runs against My NPCs (`mynpcs`), the renamed CustomNPCs fork, which is on
  the dev runtime in its place. My NPCs is CustomNPCs with `noppes.npcs` renamed to `espi.mynpcs`,
  and 41 of the 44 types this mod touches kept their names, so most touchpoints were widened rather
  than duplicated: a new `NpcTypes` resolver looks each class up under whichever root is installed,
  and eight mixins that name no NPC type at all moved to the `customnpcs || mynpcs` gate so one copy
  serves both. Only the parts with real compile-time coupling are twinned - the seven NPC screens,
  which extend `GuiNPCInterface2`, the scripting bridge, and ten mixins.
- The CustomNPCs side is parked, not removed. Its jar stays on the compile classpath so that code
  keeps compiling, every piece of it remains gated on the `customnpcs` mod id, and restoring it is a
  matter of swapping which NPC jar is installed. The CNPC GeckoLib addon is compile-only for now:
  18 of its classes reference `noppes/npcs` and none reference `espi/mynpcs`, so it cannot load
  beside the fork, and its compat mixin already required both mods.

- The DragonMineZ menu rework is parked: `/xenohud menus` now defaults to `stock`, and any existing
  config is migrated to it, so the V menus behave exactly as DragonMineZ ships them. Nothing was
  removed - both reworks, their generated art and their generators are all still in the tree, and
  either comes back with `/xenohud menus theme` or `/xenohud menus screen`. While parked the texture
  hook costs a single field read per draw rather than a thread-local lookup.

- The six DragonMineZ navigation buttons were themed as empty frames. The redesign bundle ships each
  button twice and the `clean_` half has no icon in it at all; the generator now takes the glyph from
  the `example_` half, cuts off its caption pill, and writes a contact sheet of every cell and hover
  state so an empty button is caught without launching the game.
- Content no longer overruns the themed panels. The redesign art paints its own furniture inside the
  frame - row slots, chevrons, a painted STATS button - which DragonMineZ then drew its real widgets
  on top of, at coordinates that had nothing to do with it. Panel interiors are now repainted with
  their own background before the frame is kept, so DMZ's widgets sit on a clear field the way they
  do on stock art. Shipped textures dropped from 5.2 MB to 3.2 MB as a side effect.
- The nearby-party card is now a full editor surface like the Panel, Chips and Ki Menu HUDs: the
  portrait, name, level, leader star, form, sparking label and all three gauges can each be moved,
  scaled, recoloured, bolded and re-fonted, and reset individually.
- Auras now grow with the character and tower while powering up. Size comes from a logarithmic curve
  over battle power, so a billion-power character reads as far larger than a fresh one without
  filling the sky, and while `isActionCharging` is set a ramp lifts the aura into a tall column and
  eases it back down afterwards. Both read from the `StatsData` DragonMineZ already hands its own
  sizing method, so every player's aura is affected, not just your own. Tunable live with
  `/xenohud aura` and switchable off back to DMZ's own sizing.

- The themed DragonMineZ menus are no longer blurry. They were generated at DMZ's own 256x256, so the
  big panel was stored as 141x213 texels and stretched across as many as 564x852 physical pixels at
  high GUI scales. They are now generated at 4x with linear filtering requested through a
  `.png.mcmeta`, which is sound because every `GuiGraphics.blit` overload normalises UVs by the
  texture size its caller passes rather than the size of the file. Art is also resampled once instead
  of twice, and how much each sprite loses to its aspect crop is now reported instead of silent.
- The DragonMineZ menu icons are themed for the first time. The six navigation buttons and the stat
  `+` were still stock art and were in fact unreachable: buttons draw through the six-argument
  `blit`, which reaches neither overload the theme mixin hooked. The mixin now hooks the single
  terminal blit every overload funnels into, and generated `menubuttons.png` /
  `characterbuttons.png` supply the redesigned icons with hover states.
- The stat multiplier column now uses `#FECC22`, sampled from the redesign bundle's own multiplier
  glyphs, instead of a hand-picked gold, and writes whole multipliers as `x1` rather than `x1.0` to
  match that art. Both the themed panel and XenoPixels' own stats screen read the one constant.

- The themed DragonMineZ stats panel now shows each stat's multiplier as its own gold column instead
  of DMZ's inline `x1.5` suffix, and shows `x1.0` on unboosted rows rather than hiding them, so all
  six rows read as a column. The values are still DMZ's own `getTotalMultiplier`; only the layout
  changes, and stock rendering is untouched.
- XenoPixels combat can no longer launch DragonMineZ masters. The existing master guard only caught
  knockback that goes through vanilla `LivingEntity.knockback`, which our BT3 charged kicks, launch
  arcs, rush finishers and transform shockwaves bypass by writing velocity directly. Those now route
  through one chokepoint, governed by the new `protectMastersFromCombatKnockback` server key
  (default on, separate from `protectDmzMasters`). Saga quest enemies are unaffected.
- The DragonMineZ menu rework is switchable again with `/xenohud menus <stock|theme|screen>`. It had
  shipped always-on with no way back to DMZ's own screens. `theme` (the default) keeps DMZ's real
  screens in Xeno chrome; `stock` restores them untouched; `screen` opens XenoPixels' own stats
  screen, which is restored alongside the theming rather than replaced by it.

## v0.2.3-1.21.1 — 2026-09-08 — BT3 controls, scripting and saga recovery

**Verification status:** JUnit suite **330 tests across 61 classes, zero failures/errors**. The
client reached an integrated world with Controlify and YetAnotherConfigLib installed, registered all
42 Xeno gamepad bindings, and detected an Xbox controller. The new common and optional Controlify
mixins applied without injection errors. A dedicated server also reached
`Done` after the client-only pad jars were removed. In-world button-by-button combat and building
acceptance still requires a player session. Pink aura streaking during Multi-Form remains unresolved.

### Fixed

- The six DragonMineZ menus opened through `V` now use screen-specific redesign assets from
  `new_menus_redisgn.zip` without replacing DMZ's real screens, packets, widgets, scrolling, or
  validation. Character, Skills, Quests, Minigames, Party/Server, and Settings keep their stock
  behavior while XenoPixels remaps only their verified menu texture calls.
- The packaged NeoForge dependency minimum is lowered from `21.1.238` to `21.1.233`; development
  and compilation continue to use NeoForge `21.1.238`.
- DragonMineZ saga combat quests now audit their quest-spawned enemies after both normal start and resummon. Missing enemies are recreated with DMZ-compatible ownership, objective, party-scaling, difficulty, AI, health, damage and transformation metadata without duplicating valid spawns.
- CustomNPCs quest rewards using `xenopoints add 5000 {RefPlayer}` now work on dedicated servers. The compatibility hook narrowly maps `{RefPlayer}` to CustomNPCs' verified completing-player token before its existing command dispatch; unrelated commands and valid selectors remain unchanged.
- XenoPixels status effects now render in their own vertical rail to the right of the player inventory instead of sharing the vanilla effect list or colliding with inventory tabs. The rail wraps into additional columns on short screens and retains hover names, levels, and durations; other mods' effects remain unchanged.
- Xeno Rush Left, Right, Breaker, and Finisher are unlocked without being automatically inserted into DragonMineZ's Alt/Ctrl technique slots; manually unbound slots now remain empty across login and reload, including when the legacy auto-equip config was enabled.
- Controlify arbitration now suppresses only physical inputs owned by BT3 mode instead of blanket-blocking built-in actions; normal movement/camera values remain available. Guard now owns sneak suppression, and LT+Y exclusively triggers charged kick.
- Multi-Form clones now mirror successful DragonMineZ ki-wave releases on the same server tick using the real technique data and charge multiplier; obsolete melee-charge packets and autonomous charged-wave behavior were removed.
- Replacing a Zanzoken ring now disperses the previous ring, and the player's ring landing searches for an open collision-safe slot before falling back to the current position.
- Fixed a client crash at the title screen. `resetCharge()` sent the clone ki-charge sync packet
  without checking for a connection, from the client-tick branch that runs every tick while no
  world is loaded; `PacketDistributor.sendToServer` rejects a null connection. **This crash is
  present in the code tagged `v0.2.1`,** which was never pushed. Every packet send in
  `Bt3CombatClient` now goes through one connection-guarded method, so a new call site cannot
  reintroduce it, and the charge sync no longer fires when there was no charge to clear.

### Added

- Adds a server-authoritative `X X X -> A` cinematic rush with universal, race, and iconic-form GeckoLib profiles; four gameplay impacts use fixed server ticks while animation keyframes drive cosmetic sound, particles, and controller rumble.
- Sparking now requires a five-second full-ki Max Power charge: the eight ki segments turn red and convert to gold one by one, reset immediately if charging stops, and activate Sparking when the final segment lights.
- Adds `/xenodmz saga diagnose [player]` and `/xenodmz saga respawn [player]` for operator-visible saga spawn diagnostics and duplicate-safe manual recovery.
- Adds `/xenopoints <add|set|remove> <amount> <targets>` as a command-block and CustomNPC quest friendly counterpart to DragonMineZ training points. For example, `/xenopoints add 500 @p` updates the nearest player's real DMZ points and synchronizes the resource HUD.
- Adds `/xenoki clear all` and `/xenoki clear radius <blocks>` for operators to remove loaded
  DragonMineZ KI attacks and orphaned explosion visuals globally or around the command source.
- Gamepad support through [Controlify](https://modrinth.com/mod/controlify), laid out to match
  Budokai Tenkaichi 3 and written in Xbox button names: melee, ki blast, dash and guard on the
  face buttons, ki charge and lock-on on the left trigger and bumper, fly and descend on the right
  pair, transform on the right stick, and chase/backstep/Sonic Sway on the remaining chords and
  d-pad inputs. Guard plus a left/right stick flick directly drives Xeno's existing side vanish.
- Adds persistent Normal and BT3 controller modes as Controlify radial candidates. Normal mode
  restores unmodified Minecraft mining, placing, inventory and hotbar controls; BT3 mode filters
  Controlify's overlapping Xbox defaults while retaining Pause and the radial menu.
- Left-stick click toggles DragonMineZ Search Fly and Combat Fly. Right bumper activates flight
  when needed and becomes ascend while flight is active; right trigger descends without attacking.
- Chorded moves, as in the original: hold ki charge for Z Burst, Ultimate and Sparking; hold
  lock-on for Zanzoken, Multi-Form and Hakai. Holding a modifier withholds the plain move, so one
  button never fires two.
- Controller state is read from Controlify's verified raw current/previous state for BT3 action detection and analogue flight axes. Existing DragonMineZ key mappings remain the compatibility bridge for actions that expose no direct public invocation API.
- The eight DragonMineZ technique slots are offered to Controlify's radial menu. They are unbound
  by default: their keyboard bindings are Alt+1-4 and Ctrl+1-4, chords no gamepad can produce, and
  a radial keeps working if the number of slots grows.
- The pilot seat reads the left stick as a real analogue stick, assigning its position rather than
  running it through the key ramp, and hands control back to the keyboard on release.
- Controlify is optional. Everything touching it lives in `client/pad` and is reached only through
  Controlify's own `ServiceLoader` entrypoint, so with Controlify absent none of those classes is
  loaded and input behaves exactly as before. A `padEnabled` client config flag switches the layer
  off without uninstalling anything.

See [`docs/releases/v0.2.3-1.21.1.md`](docs/releases/v0.2.3-1.21.1.md) for release scope and verification.

## v0.2.1 — 2026-09-07 — Refactor, bug fixes and techniques

### Refactor and bug fixes

First `0.2.x` release. **There was no 0.2.0** — this work carried a `0.2.0` working version but
was never tagged, so it ships as `v0.2.1`.

**Verification status:** fresh existing JUnit suite: **303 tests across 53 classes, zero failures/errors**.
Dependency APIs were checked against DragonMineZ 2.1.3 and mapped NeoForge artifacts. An isolated
dedicated server reached `Done` and answered a status ping, with compatibility warnings; no
player-combat or graphical client acceptance was performed. Pink aura streaking remains
unresolved. See the [validation and staged review ledger](wiki/Techniques-Approved-Plan-Validation.md)
and [continued handoff](CLAUDE_TECHNIQUES_HANDOFF_2026-09-06.md).

- Retains the pending DMZ-faithful punches, combos, custom rush strikes, obstacle-aware chase, Hakai erasure, HUD and compatibility work.
- Separates native weapon/mining input from Xeno fists, preserving configured Attack and disabled-feature fallback.
- Separates "Xeno fists run" from "the native attack is suppressed", so a block under the crosshair no longer switches fists or a charge in progress off, and punching, holding a charge and guarding all leave block breaking intact.
- Unlocks the four custom rush strikes for every player and answers a rush attempted at a target still on the ground.
- Softens the rush and chase camera to a single gentle writer with a deadzone, so it tracks without oscillating or snapping.
- Fades the rush and chase aim assist to nothing inside contact range, where tracking angles diverge and the camera thrashed.
- Fixes Multi-Form + lock-on crash: NpcGeckoAnim.playAttack now guards against non-NPC entities before reflective field access.
- Fixes clone facing: when no target is locked, clones face outward from formation center instead of all staring at the owner's yaw.
- Adds Multi-Form ki-wave synchronization through DragonMineZ's actual server-side attack lifecycle; clones fire on the owner's successful wave release with the same technique tuning and charge multiplier.
- Fixes a chase started from directly above its target climbing away instead of diving onto it.
- Retires the hold-Space and hold-W chase gestures (off by default, still switchable); chase runs from its own binding.
- Holding W through a combo beat launches the enemy and dashes after them on Search Fly, skipping the success roll.
- Resets fall distance and waives fall damage briefly when a chase ends, so ending one at altitude is survivable.
- Gives Xeno rush strikes their own configurable ki cost and cooldown instead of DragonMineZ's power-scaled thousands.
- Ki guidance follows the crosshair rather than auto-acquiring a nearby target; a deliberate lock-on still homes.
- Adds a working Unbind for techniques bound to DragonMineZ slots, which DMZ's own empty-slot path cannot do.
- Adds Zanzoken, the afterimage dodge: a timed read that cancels the hit and rings the attacker with copies of you. You stand in the ring yourself and their lock-on is redirected onto an image, so neither position nor the lock marker identifies the real body; striking any image disperses the ring. Fools players, not NPC AI. Ships unbound.
- Adds Shi Shin No Ken with current-health-conserving split/recall, original collision-aware local pursuit, melee/basic ki/unlocked DMZ strike combat, shared owner resources, protected lock-on targets, and exactly-once power division. Positive split combat awards rate-limited mastery up to 1000; mastery survives save/respawn. Ships unbound; live combat acceptance remains pending.
- Repairs copy refresh timing, owner armor and queued animations, distinct render identity/cache cleanup, and exception-path pose isolation without claiming to resolve shader streaks.
- Makes chase reliable by default (also Dragon Dash's shared roll), preserves explicitly saved randomness/pay-on-attempt, and ships Z-Burst unbound to avoid DMZ's V Stats binding.
- Rejects unsupported NPC ki dispatch before spending and canonicalizes supported IDs; preserves existing administrator-forced chunks when missile tickets expire; accepts the first seat-control frame after server-clock restart.
- Adds a render path giving each copy its own proxy identity, so it can draw with your DragonMineZ appearance and carry its own aura instead of a plain player model. Switchable off (`cloneDmzAppearance`); unverified in game.
- Makes chase acknowledgments, disconnect/context cleanup and temporary flight ownership authoritative and depletion-safe.
- Refreshes and consumes negative-effect transfers at save/clone/native NPC reset boundaries; cured effects must not return on reconnect.
- Preserves party friendly-fire choices on invitations, detects every effective cockpit input field and releases Hakai-owned glow before restart.
- Repairs the Create Propulsion plasma-particle mixin, which never applied: it shadowed fields that live on vanilla `Particle` rather than on the target, so plasma kept colliding with ships.
- Blue combat boxes are illustrative fixed geometry, not authoritative range or collision volumes.

See [`docs/releases/v0.2.1.md`](docs/releases/v0.2.1.md) for scope, executable regressions and runtime limitations.

## Released versions

### [v0.2.3-1.21.1](docs/releases/v0.2.3-1.21.1.md) — 2026-09-08

**1.21.1 / NeoForge.** BT3-style Controlify controls, CustomNPC quest rewards, ki cleanup commands, clone/charge fixes, and DMZ saga enemy spawn recovery.

### [v0.2.1](docs/releases/v0.2.1.md) — 2026-09-07

**1.21.1 / NeoForge.** Refactor, bug fixes, and the BT3-style technique work: left-click ownership, rush strikes, chase, Zanzoken, Shi Shin No Ken and the copy system.
### [v0.1.11](docs/releases/v0.1.11.md) — 2026-09-01

**1.21.1 / NeoForge.** NPC appearance/combat expansion, Hakai, targeting, aerodynamic flight, HUD and compatibility.

### [v0.1.10](docs/releases/v0.1.10.md) — 2026-08-27

**1.21.1 / NeoForge.** Sable flight controls, expanded combat effects, DMZ parties, and My NPCs/CustomNPCs scripting.

### [v0.1.9](docs/releases/v0.1.9.md) — 2026-08-10

**1.21.1 / NeoForge.** Actual port from the inherited 1.20.1 codebase to NeoForge 1.21.1 and Sable, plus ship systems and release fixes.

### [v0.1.8-1.21.1](docs/releases/v0.1.8-1.21.1.md) — 2026-07-26

**actually 1.20.1 / Forge.** Alias of v0.1.6 with no unique commits; it does not contain the 1.21.1 port.

### [v0.1.6](docs/releases/v0.1.6.md) — 2026-07-26

**1.20.1 / Forge.** Major BT3 combat, progression, ship guidance, compatibility, and performance expansion.

### [v0.1.5](docs/releases/v0.1.5.md) — 2026-07-25

**1.20.1 / Forge.** Live DragonMineZ form-stat scaling and administration.

### [v0.1.4](docs/releases/v0.1.4.md) — 2026-07-25

**1.20.1 / Forge.** Cooldown HUD/editor, technique assistance, combat animation integration, and form scaffolding.

### [v0.1.3](docs/releases/v0.1.3.md) — 2026-07-24

**1.20.1 / Forge.** Simplified square portrait presentation.

### [v0.1.2](docs/releases/v0.1.2.md) — 2026-07-24

**1.20.1 / Forge.** Animated HUD snapshots, movable technique UI, and early scoreboard teammate display.

### [v0.1.1](docs/releases/v0.1.1.md) — 2026-07-23

**1.20.1 / Forge.** Expanded technique charge display to 1000%.

### [v0.1.0](docs/releases/v0.1.0.md) — 2026-07-22

**1.20.1 / Forge.** DragonMineZ technique hotbar and charge meter.

### [v0.0.9](docs/releases/v0.0.9.md) — 2026-07-22

**1.20.1 / Forge.** Modern UI Jar-in-Jar packaging.

### [v0.0.8](docs/releases/v0.0.8.md) — 2026-07-22

**1.20.1 / Forge.** Configurable ki overcharge scaling and catalog expansion.

### [v0.0.7](docs/releases/v0.0.7.md) — 2026-07-22

**1.20.1 / Forge.** XenoPixels identity, initial BT3 combat, DMZ HUD, and custom forms.

### [v0.0.6](docs/releases/v0.0.6.md) — 2026-07-22

**1.20.1 / Forge.** Strawberry Senzu and DMZ regeneration diagnostics.

### [v0.0.5](docs/releases/v0.0.5.md) — 2026-07-22

**1.20.1 / Forge.** First reachable baseline: ores, items, recipes, VS/DMZ hooks, wiki, and release automation.
