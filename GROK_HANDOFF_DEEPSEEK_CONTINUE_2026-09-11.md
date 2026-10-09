# Handoff — Continue DeepSeek unfinished DMZ NPC / Form Studio work

**Date:** 2026-09-11
**Repository:** `C:\XenoPixelsNetwork_qwen`
**Branch:** `1.21.1`
**HEAD:** `3fa456a20a43c56600a743e5cab332534af6a773` ("Record developer API completion handoff")

## Current state

Nothing was committed, tagged, or pushed. Work is in the dirty tree on top of `3fa456a`, continuing DeepSeek's unfinished follow-ups from `deepseek_request_1` / `deepseek_request_2`.

The Form Studio / Stats / `/stack` spec from the same request file was already present (see `CLAUDE_DMZ_FORM_STUDIO_HANDOFF_2026-09-11.md`). That was not rebuilt.

`src/main/java/net/bullettrain/xenopixelsmod/api/**` is clean. `ModNetwork` protocol is unchanged. Form-editor channel remains protocol `3`. Untracked MP3 was not touched.

No process was left running.

## Changes

### Role picker and Edit button

- `DmzSkillMaster.shownSelectorIndex` — MyNPCs passes a stock-table **index** (0 when the sentinel is missing); CustomNPCs passes the stored role id. The stored sentinel now always shows the appended picker slot.
- `GuiNpcAdvancedRoleMixin` (both mods) uses that helper and intercepts role Edit (button id 3) to open `GuiNpcDmzSkillMaster`.
- MyNPCs `ROLE_TYPE_IDS` GETSTATIC redirect now covers `init` and `buttonEvent`, so the lookup finds sentinel `9001`.

### Skill-master menu editor

- New `GuiNpcDmzSkillMaster` (CNPC + mirrored MyNPCs): bind/unbind this NPC on a form group, title, `en_us` body, offered forms. Saves through existing `FormEditorNetwork.save`.

### Autobind wand NPC

- `DmzFormAutobind.ensureMaster` inserts/promotes the wand NPC as `skillMaster` and turns `masterLearningEnabled` on.
- `GuiNpcDmzFormEditor.saveNow` calls it when `npc.role` is the sentinel, so a newly created form is offered from that master.

### Visualizer on every wand tab

- `NpcPreviewPanel` mixin on `GuiNPCInterface2` (Display/Stats/AI/Advanced/Global) and `GuiNPCInv` (Inventory).
- Drag/scroll/release forwarded from `GuiBasic`.
- DMZ screens implement `NpcPreviewOwner` so they are not double-drawn.
- Aura stays visualizer-local.

### Form studio hairType

- Cycle button next to the `hairType` field walks verified values `base` / `ssj` / `ssj2` / `ssj3` (`DmzHairTypes`).
- Preview-panel hair label is `"H" + id` with no space, matching Appearance.

### Exact stats

- `StatText.format` prints the exact integer (or one decimal). `condense` still abbreviates for measured overflow.
- `XenoClientConfig.hudCompactNumbers` default is now `false`.

### Sparking options row

- `DmzConfigMenuSparkingMixin` appends a boolean to DMZ `ConfigMenuScreen`. Writes `XenoClientConfig.sparkingEnabled` (client opt-out; server `bt3SparkingEnabled` still required).
- `Bt3CombatClient` Sparking key respects the client flag.

### Rush camera vs lock-on

- `Bt3CombatClient.lockRushView` returns immediately when `LockOnEvent.getLockedTarget()` is already that victim, so only lock-on writes yaw/pitch while locked.

### Curios

- Deleted `data/xenopixelsmod/curios/slots/*.json` (they redeclared Curios' own global slots).
- `entities/npcs.json` now lists humanoid CNPC/MyNPCs types only (`customnpc`, `64x32`, `alex`, `classic`). Crystal/dragon/golem/pony/slime were not added.

## Verified

Exact commands, all run 2026-09-11 from `C:\XenoPixelsNetwork_qwen`:

- `./gradlew test -PofflineMcMeta` — BUILD SUCCESSFUL. 105 test classes, **603 tests, 0 failures, 0 errors, 0 skipped** (counted from `build/test-results/test/TEST-*.xml`).
- `./gradlew build jarJar serverJar buildApiExampleAddon -PofflineMcMeta` — BUILD SUCCESSFUL.
  - `build/libs/xenopixelsmod-Server-0.3.5-1.21.1.jar` — 9,210,606 bytes, SHA-256 `d0282386dac7fd1434e0ee2876a9b0197cb4edf72faf51d194f67b702789ae99`, **0 entries under `META-INF/jarjar/`**.
  - `build/libs/xenopixelsmod-0.3.5-1.21.1.jar` — 33,357,834 bytes, SHA-256 `758daaf7b87d042b6edebaec28d5a0068c7604b3131219baf6b125499eac3af8`.
  - API example addon built; published API tree unchanged.

Bytecode facts used (not gameplay):

- CustomNPCs `GuiNpcAdvanced.m_7856_` / `buttonEvent`; Edit is id 3; picker is id 8; `getType()` is the constructor value.
- MyNPCs `init` looks `getType()` up in `ROLE_TYPE_IDS` and passes the index (0 on miss). Edit id 3 sends `SPacketNpcRoleGet`.
- `FormConfig.FormData.hairType` values in this repo: `base`, `ssj`, `ssj2`, `ssj3`.
- `ConfigMenuScreen$ConfigType.BOOLEAN` and package-private `ConfigOption` ctor exist in dragonminez-2.1.3.
- `LockOnEvent.onRenderTick` calls `Player.setYRot` / `setXRot`.

## Not verified

Everything below is **manual pending**. No client was launched this session.

- Role appears and sticks in CustomNPCs and MyNPCs Advanced pickers.
- Edit opens the skill-master menu and a right-click on the NPC opens `DmzFormTrainerScreen`.
- MyNPCs Inventory tab stays open (`MyNpcsMenuTypeCreateBridgeMixin` was already present; still unplayed).
- Visualizer visible on Display / Stats / Inventory / AI / Advanced, not only DMZ.
- hairType cycle on the form-studio page that shows that field.
- Exact stat digits on the themed Character screen.
- Sparking row in DMZ options, and that it persists in `xenopixelsmod-client.json`.
- Rush + lock-on camera no longer fights.
- Curios cosmetic slots actually appear in the NPC Inventory GUI. Entity JSON attaches Curios' entity slots; this session did **not** add `Slot` widgets to `ContainerNPCInv`. If they do not show in-game, that container mixin is the next step.

## Next steps

1. `./gradlew runClient` **without** `-PofflineMcMeta`. Work the manual list above against a fresh `run/logs/latest.log`.
2. If NPC Inventory has no Curios slots, mixin `ContainerNPCInv` / `GuiNPCInv` using `CuriosApi.getCuriosInventory(npc)` — do not redeclare Curios slot JSON.
3. Commit reviewed paths explicitly. Never `git add -A`. The form-studio / role / texture trees are still largely untracked.
