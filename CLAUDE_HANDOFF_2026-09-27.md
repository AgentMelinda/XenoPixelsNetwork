# Handoff — Qwen audit fixes, flight, Nashorn scripting, 1:1 script screen, inline picker

**Date:** 2026-09-27 · **Branch:** `1.21.1` · **HEAD:** `53a025e` (nothing committed; dirty tree)
Follows `QWEN_HANDOFF_2026-09-27.md`. Plan: `~/.claude/plans/read-qwen-handoff-2026-09-27-md-and-also-inherited-feather.md`.

## Verified (fresh, 2026-09-27)
- `./gradlew test -PofflineMcMeta` — **2,689 tests, 0 failures**.
- `./gradlew build jarJar serverJar -PofflineMcMeta` — client jar nests ModernUI + `nashorn-core-15.4`;
  server jar nests **only** `nashorn-core-15.4` (SHA-256 `6f816e84…dc6f`). The old "0 jarjar entries in
  the server jar" rule is now "Nashorn only".
- `./gradlew buildApiExampleAddon -PofflineMcMeta` — SUCCESS.
- Real Nashorn in unit tests (`BundledNashornTest`): named entrypoints, per-tab globals, hook args,
  bound API objects callable, `Java.type` / `Packages` / `getClass().forName` refused.

## Not verified
- Nothing ran in a live game. The dedicated-server probe crashed at startup with Sable's
  `AccessDeniedException` on `run/.sable/natives/…dll`: a dev **client** (PID 54840, started 02:44,
  not by this session) holds it. A probe script is left at
  `run/profile-world/XenoNpcs/scripts/runtime_probe.json` (run/ is gitignored). To finish: close the
  client, `./gradlew runServer`, look for `NPC scripts: script engine: nashorn`, then over RCON
  (`xeno123`, 25575) summon `xenopixelsmod:xeno_npc_humanoid` with
  `{NeoForgeData:{"xenopixels:npc_combat_profile":{ScriptTabs:[{Id:"runtime_probe"}],ScriptsEnabled:1b}}}`
  and expect `[probe] init ok`, `[probe] java blocked`, `[probe] tick ok` in the log.
- In-game look of: flight steering, script screen, inline picker, new bubble shapes, cape/overlay.

## What changed (see CHANGELOG 2026-09-27 for detail)
- **Qwen audit fixes:** `XenoNpcEntity.hasBakedClip` (per-rig lookup via `noteAnimationFile`),
  `NpcCombatBrain.swingBeforeHit`, `NpcCombatProfile.readCached` (per-side cache, shared defaults),
  `NpcFormDisplayTuning` (miss cache), `Jsr223ScriptEngine` (context-swap invoke), `NpcFlightBridge`
  (debounce + Can Use Flight, idle = ground), `GuiNpcDmzSkills` twins (labels behind sub-GUIs),
  Aim* honesty labels, reward message only when points were paid.
- **Flight:** `NpcFullDmzRenderer.syncWorldEntity` sets `xOld/yOld/zOld`; per-frame slewed fly yaw;
  `NpcKiAim.trackKiSenseTarget` slews in flight; `airChaseVelocity` deadband + min cruise;
  `NpcFlightOwnership` covers both brains for nav/leash/follower/look goals; yaws wrapped.
- **Scripting:** `NashornSandbox`, `NpcScriptContainer` (tabs), `NpcScriptHost` (hooks, console),
  `npc/script/api/*`, permission 4 for script writes/runs, container open/bind packets.
- **UI:** `XenoNpcScriptScreen` rewritten (`XenoScriptLayout`, `XenoCodeArea`); `InlineColorPicker`,
  `ColorPickerModel`, `ColorSwatch`; old `XenoColorPickerScreen` removed (backup in the session
  scratchpad only; it was untracked).
- **Art:** `tools/atlas-panels/xeno_extra_specs.py` gained `install_renderers` (thought/shout/banner
  bubbles, HSV chart), script frame ladder + panels, buttons at CNPC sizes, picker frames — 328 new PNGs (344 total)
  in `src/generated/resources/.../gui/atlas`.
- **Todolist:** #1 flight, #2 picker everywhere + reward display, #4 text 0.8, #5/#12 NPC lines,
  #8/#12 trade Ignore damage/NBT and Cape/Overlay/Layers now live, #11 script parity, #13 importer
  keeps all tabs, #14 `target_hunts` quests (`QuestHunts`). #3/#7/#9/#10 checked, already present.
- Protocol **97**.

## Still honestly disabled (no backing store/runtime)
Recipes bench, Display Availability, mark catalog + per-mark availability, Night alternate profile,
Living animation, Linked Marketname, Line Selector (shared line sets).

## Constraints unchanged
No commits without approval; stage reviewed paths only; `run/` stays uncommitted.
