# Native MyNPCs script counterparts (2026-09-27)

This page covers **script calls and hooks** on Xeno NPCs. It does not promise Java binary compatibility with `espi.mynpcs.api`. The inventory was checked against `run/mods/mynpcs-neoforge-1.5.0.jar` and the unchanged files in `examples/customnpcs/`.

## Run the examples

The 19 NPC scripts can be pasted unchanged into a native Xeno NPC's **Scripts** editor. Each tab has its own globals. The 3 `xenopixels_player_*.js` files can be pasted unchanged into **Global → Player Scripts**. Player tabs run on `init`, `login`, `logout`, and `chat`. If you also put `xenopixels_state_clips.js` in Player Scripts for its optional chat commands, set **Chat hook only: Yes** for that tab; its NPC `init` needs `event.npc` and belongs on an NPC.

Scripts run on the server with bundled Nashorn, `--no-java`, and a deny-all class filter. Publishing/editing scripts requires permission level 4. Saved scripts live in `<world>/XenoNpcs/scripts/` or `<world>/XenoNpcs/player_scripts/`. The separate player category was appended after existing store categories; the main network protocol is `98`.

`XenoPixels` is bound in native NPC and player scopes. The methods used by all 22 examples have native counterparts backed by Xeno profile, transformation, combat, animation, speech, and progression services. The wider facade also covers the MyNPCs bridge's methods with native backing, including skills, forms, brain settings, aura styles, sounds, movement, and visual locks. `setPlayerModel(npc, true)` selects the native NPC's full DragonMineZ appearance. `setAimAccuracy` is omitted because the pinned bridge only stores a field that no system reads; `setSkinUrl` is omitted because the native Xeno NPC skin renderer has no verified URL path. `event.API.getQuests().get(number)` resolves an imported numeric source slot only when it uniquely identifies one quest. New imports record `SourceMod` and `SourceSlot`; older imports use the unique `*_q<number>` id fallback. Missing or ambiguous slots return `null`. `player.hasActiveQuest(number)`, `hasFinishedQuest(number)`, `startQuest(number)`, `finishQuest(number)`, and `stopQuest(number)` use that same mapping. The other MyNPCs numeric quest methods have no native counterpart yet. Player stored data lives in `XenoPlayerData` and survives death; player temporary data clears on logout.

The `xenopixels_combo_rush.js` example can run on a native NPC using the DragonMineZ Master Gohan GeckoLib model and texture. The native renderer keeps the selected GeckoLib model even when the script sets Full appearance, and searches the XenoPixels combat animation file for the named `combat.xeno_*` clips. The pinned Master Gohan animation file itself contains only `idle` and `walk`; its geometry has the arm and leg bones used by the combat clips. A fresh gameplay check of the visible poses remains pending.

NPC timers expose `start`, `forceStart`, `has`, `stop`, `reset`, and `clear` through `npc.getTimers()`. They are bounded and saved on the NPC. `timer(event)` receives `event.id`. The native `rangedLaunched` hook is dispatched from the Xeno ki dispatcher after a launched blast, wave, or predefined projectile; a guard prevents recursive dispatch if the hook itself fires another technique. The `kill` hook runs when this Xeno NPC is the source of a living-entity death.

## Event inventory

The pinned MyNPCs jar declares these event families. This table describes native script dispatch, not all Java game events.

| MyNPCs family | Native script counterpart |
| --- | --- |
| `NpcEvent` | `init`, `tick`, `interact`, `damaged`, `died`, `kill`, `target`, `targetLost`, `collide`, `meleeAttack`, `rangedLaunched`, `timer`; `dialog` and `dialogOption` are Xeno dialogue hooks. |
| `PlayerEvent` | Global `init`, `login`, `logout`, `chat`. Chat cancellation and rewrite are supported through `event` and `XenoPixels`. |
| `DialogEvent` | NPC dialogue hooks only; there is no standalone MyNPCs dialog event object. |
| `QuestEvent` | Quest state and objective lookup for scripts; no global quest event hook. |
| `BlockEvent`, `CustomGuiEvent`, `ForgeEvent`, `HandlerEvent`, `ItemEvent`, `ProjectileEvent`, `RoleEvent`, `WorldEvent` | No native global script dispatcher for these domains. |

The MyNPCs entity interfaces, custom GUI APIs, arbitrary item/projectile scripts, event-bus registration, Java `espi.mynpcs.api` types, and plugin-specific methods are not exposed by this native host. Use the existing Xeno NPC editor, world store, and published Xeno Java API for those tasks where a counterpart exists. A script that calls an unsupported method fails visibly in the script log; it is not silently ignored.

## Verification boundary

The unchanged examples are syntax-checked against bundled Nashorn in tests. Compilation and unit tests do not prove an event fires in game. Runtime behavior requires a fresh game process and its current log or observation.
