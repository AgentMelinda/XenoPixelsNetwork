# XenoAPI parity, sub-project 1: event foundation

Date: 2026-09-27
Status: written spec, awaiting owner review. Design approved in conversation on 2026-09-27.
No implementation claimed.

## Intent

The owner wants the full XenoAPI (`xenoapi.npcs.api`) to work on native Xeno NPCs. It serves
two audiences: script authors writing new scripts, and Java addons. "Done" for the whole
programme means three things:

- No contract method throws "unsupported" unless it is impossible here.
- Every event type is posted from a real call site.
- Each area has fresh in-game evidence.

Stated by the owner:
- Full API for new scripts, and for Java too.
- Scripts receive events as hooks plus a typed view.
- Add a Scripted Block and a Scripted Item, *and* global world scripts.
- Full custom-GUI component set.
- Sequencing A (a foundation, then vertical slices).
- **No reliance on MyNPCs or CustomNPCs.**

Assumed, not stated:
- Existing scripts and today's hook event objects stay unchanged.
- Unmodified CustomNPCs script packs are not a goal.

## Constraints

- **Native only.** Nothing may depend on, detect, reflect into, or call MyNPCs or CustomNPCs.
  The XenoAPI contracts in `XenoNPCsAPI/src/main/java` are the only reference for names and
  semantics. Where they are silent, the native behaviour decides and this spec says so.
- Java 21, Minecraft 1.21.1, NeoForge 21.1.248. NeoForge symbols below were verified with
  `javap` and the sources jar on 2026-09-27, against
  `build/moddev/artifacts/neoforge-21.1.248-merged.jar`
  (SHA-256 `78fccf5edc77e8f36aae4ec8e9c78cf39f2438c133ee2704cdaf7e8226bae604`).
- Additive only: no existing hook, binding, wrapper signature, stored-data key or packet id
  changes. New packets (later sub-projects) go through `AddonNetwork`, never `ModNetwork`.
- Server-authoritative; all dispatch runs on the logical server thread.
- No fake values: an event field with no native source is documented as such. It is never
  filled with a plausible constant.

## Roadmap (sequencing A)

Each sub-project gets its own spec, plan, build and review.

1. **Event foundation** (this spec): typed events for native NPC hooks and 19 player events,
   player timers, minimal `IBlock`/`IContainer`.
2. **Story systems:** quests, dialogues, factions and mail over the native quest book,
   dialogues and factions. Adds `QuestEvent`, `DialogEvent`, `PlayerEvent.FactionUpdateEvent`,
   and the matching handlers and player methods.
3. **NPC internals:** display, stats, AI, inventory and advanced settings; roles
   (trader, transport, bank, follower) with `RoleEvent`; jobs, clones, marks, `ILine`;
   `shootItem` with `IProjectile` and `ProjectileEvent`.
4. **World and blocks:** full `IBlock`/`IContainer`, plus `IScoreboard`, `IDimension` and
   raytrace. Adds a Scripted Block and a Scripted Item, and a Global → World Scripts tab.
   Block, Item, World and Forge events land here. The existing **NPC Scripting Tool**
   (`xenopixelsmod:xeno_npc_script_tool`, XenoPixels tab) is extended to open a Scripted
   Block's or held Scripted Item's script tab, with the same server-side permission and range
   checks. No second tool is added.
5. **Client-driven:** the full custom-GUI component set and `CustomGuiEvent`; key pressed and
   released; air left/right clicks; `PlaySoundEvent`. All need client↔server `AddonNetwork`
   packets.

## Sub-project 1 scope

In scope:
- Typed `NpcEvent` for all 12 contract types, from the existing native call sites.
- Typed `PlayerEvent` for 19 of 23 types (table below).
- Every typed event posted to `NpcAPI.events()`.
- An `event.xeno` view on today's hook objects.
- New player hooks.
- Native player timers.
- Read-mostly `IBlock` and `IContainer` adapters.

Out of scope, with the owning sub-project:
- `KeyPressedEvent`, `KeyReleasedEvent`, `PlaySoundEvent`, empty-hand right-click air, and
  left-click air (attack type 0): sub-project 5. NeoForge fires `LeftClickEmpty` and `RightClickEmpty`
  on the client only, per its own documentation. Server-side `PlayLevelSoundEvent` has no
  looping concept.
- `FactionUpdateEvent`: sub-project 2.
- `IProjectile` and `ILine` values: sub-project 3.

## Architecture

### One event per occurrence

`XenoEventDispatch` (new, `npc/script/api/xeno/event/`) is the only place a typed event is
built and delivered. For each occurrence it runs these steps:

1. Build the typed XenoAPI event with native adapters (`XenoApiAdapters.wrap`).
2. **Scripts.**
   - A hook that exists today receives today's `ScriptEvent`, which now carries the typed
     object as its new field `xeno`.
   - A hook that is new in this slice receives the typed object as its argument.
   - One tab defining a hook runs it once per occurrence.
3. **Java.** Post the same object to `NpcAPI.events()`. Listeners run after scripts and may
   override a script's cancel or mutation.
4. **Read-back.** The native call site reads the final cancel flag and mutable fields once,
   from the typed object, and applies them.

`ScriptEvent` gains one field and no new behaviour for old scripts:
`public final CustomNPCsEvent xeno`, which is null where no typed event applies (for example,
native `dialog` hooks). `setCanceled` and `setDamage` also write through to `xeno` when it is
cancellable or has `damage`. Old-style and typed-style edits to one occurrence therefore
cannot disagree. The last writer wins, in execution order.

### Hook names

Names come from the XenoAPI javadoc for each event class. Existing native names are kept.

- **NPC:** the existing `init`, `tick`, `interact`, `damaged`, `died`, `kill`, `target`,
  `targetLost`, `collide`, `meleeAttack`, `rangedLaunched` and `timer` gain `event.xeno`.
  The contract documents the ranged hook as `rangedAttack`. `rangedLaunched` keeps working
  unchanged, and `rangedAttack` is added as a new hook that receives the typed event directly.
  Existing `dialog` and `dialogOption` are unchanged; their typed `DialogEvent` arrives in
  sub-project 2.
- **Player:** the existing `init`, `login`, `logout` and `chat` gain `event.xeno`.
  The new hooks receive the typed event: `tick`, `interact`, `attack`, `broken`, `toss`,
  `pickedUp`, `containerOpen`, `containerClosed`, `damagedEntity`, `rangedLaunched`, `died`,
  `kill`, `damaged`, `timer` and `levelUp`.
- `NpcScriptHost.HOOKS` and the player host's hook list are extended. The editor's hook help
  lists them.

### Player event mapping

| XenoAPI event | Hook | NeoForge source (verified) | Cancel / mutation read-back |
| --- | --- | --- | --- |
| `InitEvent`, `LoginEvent` | `init`, `login` | existing `PlayerEvent$PlayerLoggedInEvent` path | none |
| `LogoutEvent` | `logout` | existing `PlayerLoggedOutEvent` path | none |
| `UpdateEvent` | `tick` | `PlayerTickEvent$Post`, every 10 ticks (`NpcScriptHost.TICK_INTERVAL`) | none |
| `ChatEvent` | `chat` | existing `ServerChatEvent` path | cancel; `message` as today |
| `InteractEvent` type 1 | `interact` | `PlayerInteractEvent$EntityInteract` | cancel → `setCanceled` |
| `InteractEvent` type 2 | `interact` | `PlayerInteractEvent$RightClickBlock` | cancel |
| `InteractEvent` type 0 (item in air) | `interact` | `PlayerInteractEvent$RightClickItem`; its NeoForge docs say it is not fired when a block or entity is targeted, so no dedup is needed | cancel |
| `AttackEvent` type 1 | `attack` | `AttackEntityEvent` | cancel |
| `AttackEvent` type 2 | `attack` | `PlayerInteractEvent$LeftClickBlock` (server firing) | cancel |
| `DamagedEntityEvent` | `damagedEntity` | `LivingIncomingDamageEvent`, attacker is a player | cancel; `damage` → `setAmount` |
| `DamagedEvent` | `damaged` | `LivingIncomingDamageEvent`, victim is a player | cancel; `damage` → `setAmount` |
| `DiedEvent` | `died` | `LivingDeathEvent`, entity is a player | cancel (see rules) |
| `KilledEntityEvent` | `kill` | `LivingDeathEvent`, source entity is a player | none |
| `TossEvent` | `toss` | `ItemTossEvent` | cancel (see rules) |
| `PickUpEvent` | `pickedUp` | `ItemEntityPickupEvent$Pre` | cancel → `setCanPickup(TriState.FALSE)`; the event is not `ICancellableEvent` |
| `RangedLaunchedEvent` | `rangedLaunched` | `ArrowLooseEvent` | cancel |
| `LevelUpEvent` | `levelUp` | `PlayerXpEvent$LevelChange`, `change = getLevels()` | none; XenoAPI's `LevelUpEvent` is not cancellable (verified 2026-09-27) |
| `BreakEvent` | `broken` | `BlockEvent$BreakEvent` | cancel; `exp` (see rules) |
| `ContainerOpen` / `ContainerClosed` | `containerOpen` / `containerClosed` | `PlayerContainerEvent$Open` / `$Close` | none |
| `TimerEvent` | `timer` | native player timers (below) | none |

### Rules

- **Hands.** Interact events are posted for the main hand only. The off-hand firing of the
  same NeoForge event is ignored, so one click is one event.
- **Toss cancel.** `CommonHooks.onPlayerTossEvent` removes the stack before posting
  `ItemTossEvent`, and a cancelled event never spawns it, so a plain cancel would delete the
  item. A cancelled `TossEvent` returns the stack to the player's inventory and syncs it. If
  the inventory cannot take it, the toss is not cancelled and the item drops; this is logged.
- **Damage order** within one `LivingIncomingDamageEvent`: the attacker's
  `DamagedEntityEvent` first, then the victim's `DamagedEvent`. The second sees the first's
  damage. Either cancel cancels the NeoForge event. A native NPC victim keeps its existing
  `damaged` hook in `XenoNpcEntity.hurt`, which runs before NeoForge's event.
- **Player death cancel.** A cancelled `DiedEvent` cancels `LivingDeathEvent` and sets the
  player's health to 1.0, so the death does not immediately repeat. This is documented as a
  native decision; the contract is silent on it.
- **Break experience.** 21.1's `BreakEvent` carries no experience value. `exp` is read with
  `IBlockStateExtension.getExpDrop(level, pos, blockEntity, player, tool)`. A changed value is
  applied through `BlockDropsEvent.setDroppedExperience` for the same position and player in
  the same tick. Both symbols were verified in the 21.1.248 jar.
- **Air clicks.** Right-clicking air *with an item* is posted as interact type 0 with a null
  target. Empty-hand right-click air and every left-click air are client-only in NeoForge.
  They are never posted in this slice, and no placeholder is posted.

### NPC event data

`XenoEventDispatch` builds `NpcEvent` types from the arguments the existing call sites already
have. Fields without a native source this slice:

- `DiedEvent.line` is null, until `ILine` arrives in sub-project 3.
- `DiedEvent.droppedItems` is a read-only snapshot of the native drop list where it is known
  before drops spawn, otherwise empty. Changing it has no effect until sub-project 3.
- `RangedLaunchedEvent.projectiles` is empty: native ki attacks are not `IProjectile`s.
- `MeleeAttackEvent.damage` and `DamagedEvent.damage` read back into the existing native
  damage paths (`NpcMeleeDamage`, `XenoNpcEntity.hurt`). `clearTarget` is honoured by
  clearing the NPC's target.

Each of these is listed in `docs/native-xenoapi-adapters.md`.

### Player timers

A player-scoped `ScriptTimers`, saved in the player's existing script data
(`XenoCapabilities` `scriptData()`) under its own key. It uses the same bounds as NPC
timers (64 timers, 1-1200000 ticks). It is ticked by the player host and fires the player
`timer` hook with `PlayerEvent.TimerEvent`. `XenoPlayerAdapter.getTimers` returns it
through `XenoTimersAdapter`. It is dropped with the player host on logout, and restored on
login.

### Minimal `IBlock` / `IContainer`

These cover only what the three events need; everything else throws the named unsupported
error until sub-project 4.

- `XenoBlockAdapter` (`IBlock`) native: `getX`, `getY`, `getZ`, `getPos`, `getName`,
  `getDisplayName`, `isAir`, `isRemoved`, `getProperty`, `getProperties`, `hasTileEntity`,
  `isContainer`, `getContainer`, `getWorld`, and `getBlockEntityNBT` (a detached snapshot).
  The raw handles `getMCBlock`, `getMCBlockState` and `getMCTileEntity` throw, following the
  existing raw-handle rule.
- `XenoContainerAdapter` (`IContainer`) native: `getSize`, `getSlot`, `getItems` and `count`.
  `setSlot` is native only on the server thread and within `0..size-1`. `getMCInventory` and
  `getMCContainer` throw (raw handles).

## Cost controls

- A typed event is built only when at least one of these holds:
  - a script tab for that entity defines the hook, or
  - `NpcAPI.events()` has had any listener registered since startup. `IEventBus` exposes no
    listener query (verified against `bus-8.0.5`), so `events()` returns a delegating
    `IEventBus` that counts `register`/`addListener` calls and forwards every method
    unchanged. This is explicit delegation, not a dynamic proxy.
- `UpdateEvent` is posted at most once per 10 ticks per player and per NPC.
- Re-entrancy guard per entity and hook, like the existing target and `rangedLaunched`
  guards, so a hook that triggers its own event does not recurse.
- No TPS claim is made. Acceptance records a profiler sample with 20 scripted NPCs and one
  Java listener, and reports it as measured.

## Errors

- A script error is reported through the existing console and chat path. It does not stop
  Java listeners or the native action.
- A listener exception is logged once per listener class per minute and does not stop the
  native action.
- Invalid mutations are refused and logged: non-finite or negative damage, or a message over
  the chat limit. The native action then proceeds with the last valid value.

## Testing and acceptance

- Unit tests:
  - Dispatcher ordering (scripts, then bus, then read-back).
  - `ScriptEvent`/`xeno` write-through.
  - Main-hand filtering of interacts.
  - `PickUp` TriState mapping.
  - Damage-order mutation.
  - Player timer bounds and persistence.
  - The build-only-when-needed gate.
  - The re-entrancy guard.
- Nashorn tests: old hooks still receive `ScriptEvent`; `event.xeno` is present and typed;
  new hooks receive typed objects.
- The example addon gains listeners that count each event class and `/xenoapitest events` to
  print the counts.
- A fresh-runtime checklist: each of the 12 NPC and 19 player events observed once in a live
  world, with log timestamps. Any event not observed stays **not verified**.
- `./gradlew test`, `build jarJar serverJar`, `buildApiExampleAddon`, and a
  dedicated-server start.

## Adapter alignment (added 2026-09-27, owner request)

The owner named the CustomNPCs 1.16.5 API documentation
(`https://www.kodevelopment.nl/customnpcs/api/1.16.5/`) as a reference. Where our adapters
differ from its documented behaviour, ours are adjusted. It is a reading reference only, not
a dependency. Findings checked on 2026-09-27:

- **Potion particles.** `IEntityLiving.addPotionEffect`'s last flag means **hide** particles.
  Both the reference and XenoAPI's own javadoc say so, despite the parameter name
  `showParticles`. Ours inverted it; fix it.
- **Light.** `IWorld.getLightValue` returns a value between 0 and 1 (ours returned 0-15).
- **Stack size.** `IItemStack.setStackSize` takes 1 to the maximum stack size (ours allowed 0).
- **Missing items.** `IPlayer.removeItem(String, int)` returns false for an unknown item id
  (ours threw), and `inventoryItemCount(String)` returns 0 for one.
- **Creative give.** `ICustomNpc.giveItem` drops the item when the inventory is full, unless
  the player is in creative.
- **Owner.** `ICustomNpc.getOwner` returns who the NPC follows. With an active native
  follower job, that is the followed NPC. For the COMPANION role
  (`XenoNpcRoleBehaviour.followsOwner`), it is the owner. Otherwise it is null.
- **Animations.** `IEntity.playAnimation` sends vanilla `ClientboundAnimatePacket`
  (0 swing main hand, 2 wake up for players only, 3 swing off hand) instead of throwing.
- **Script data everywhere.** `getTempdata`/`getStoreddata` exist for every entity, for the
  world, and for items, not just for NPCs and players. Stored data keeps the native bounds
  (64 keys, keys up to 64 characters, values up to 1024) and saves only strings and numbers.
  - Entity data lives in its persistent data under `XenoScriptData`, the key native NPCs use.
  - World data lives in one overworld `SavedData`. World temp data is shared across
    dimensions, as the reference documents.
  - Item data lives in the custom-data component, and item temp data is per stack instance.
- **Item attributes.** `IItemStack.getAttackDamage` is the sum of main-hand `ADD_VALUE`
  attack-damage modifiers. `setAttribute`/`getAttribute`/`hasAttribute` use the reference's
  slot codes (-1 all, 0 main hand, 1 off hand, 2 feet, 3 legs, 4 chest, 5 head), stored in the
  attribute-modifiers component.
- **Kept deviations.** `IEntity.getNbt` still refuses live persistent data, because it would
  bypass the bounds above. `IItemStack.getNbt` stays unsupported (1.21 items use components).
  `updatePlayerInventory` quest checks arrive with sub-project 2.

## Rollback

Additive and data-compatible. Removing the dispatcher, the `xeno` field, the new hooks and
player timers leaves existing scripts working. Saved player timers are an extra key in the
player's script data and are ignored when absent.
