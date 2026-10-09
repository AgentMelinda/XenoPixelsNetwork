# Native XenoAPI adapters

**2026-10-09 update:** Native wrapper `getAPI()` bridges and live item custom-data NBT
are now implemented. Typed item projectiles, path pauses, name/dead-body display settings,
conditional boss bars, and bounded transporter views were added on 2026-10-09.
The historical capability table below is a sample, not full API coverage.
See [the official API inventory](combat-v3/customnpcs-api-audit-2026-10-08.md) and
[typed bridge usage](customnpcs-typed-bridge-2026-10-08.md) for current scope and remaining gaps.

**Checked:** 2026-09-30. Native Xeno NPCs implement the XenoAPI contracts in
`XenoNPCsAPI/src/main/java/xenoapi/npcs/api`. Existing scripts, the `XenoPixels`
binding and every existing wrapper signature are unchanged. 25 top-level contract methods
still throw `UnsupportedOperationException` naming themselves and this page: the raw
Minecraft handles, the custom GUI, the client screen size, Pixelmon, recipes, random names,
`registerScriptEvent`, `IItemStack.getNbt`, `IPlayer.setName` and `ICustomNpc.shootItem`.
Individual NPC sub-object settings with no native counterpart are listed under
[NPC sub-objects](#npc-sub-objects-2026-09-30).

Code: `src/main/java/net/bullettrain/xenopixelsmod/npc/script/api/xeno/`.

## How it is wired

- **Build.** The API source compiles as part of the mod
  (`sourceSets.main.java.srcDir 'XenoNPCsAPI/src/main/java'` in `build.gradle`).
  The first slice on 2026-09-27 used a Gradle composite build and expanded the API jar into
  the distributions. That packaged correctly, but in dev runs and unit tests the API jar
  loaded in the `app` class loader while Minecraft loads in the game layer. Every API
  signature naming a Minecraft type then failed to link (`LinkageError: loader constraint
  violation ... net.minecraft.world.item.ItemStack`, observed in a unit test). MDG 2.0.143's
  `ModModel` accepts only source sets, so the source is now part of the mod's own source set.
- **Registration.** `NativeNpcApi.register()` runs once in the `XenoPixelsMod` constructor.
  It holds no server or level; world-dependent calls resolve the running server per call and
  throw `IllegalStateException` ("needs a running server") before startup and after shutdown.
  A second call is a no-op. If another implementation is already registered it is left alone
  and a warning is logged.
- **Shared state.** `NpcScriptHost.sharedState(npc)` returns the same temporary-data map and
  timers the script host uses. Before the NPC's first host exists, the state is held as
  pending and adopted by the first build, so there is only ever one owner. A host rebuild
  starts fresh temporary data, as before; adapters look the owner up on every call.
  `forget` and server stop clear pending state.
- **Scripts.** NPC and player script scopes gain an additive `XenoAPI` binding (the
  registered `NpcAPI`), listed in `NpcScriptHost.BINDINGS`. `XenoPixels.toXeno(wrapper)`
  and `XenoPixels.fromXeno(adapter)` convert between the two worlds.
  The plan proposed `ICustomNpc` overloads of all ~120 `XenoPixels` extension methods. That
  was rejected because an overload pair makes every `XenoPixels.x(null, ...)` call ambiguous
  in Nashorn. The plan names explicit conversion entry points as the fallback; they take
  one parameter type each and accept null.
- **Authority.** Mutations require the logical server's own thread
  (`XenoApiAdapters.requireServerThread`). Inputs are validated before any change: finite
  numbers, bounded strings, lists, arrays, ranges, speeds and counts. Adapters from any other
  implementation are refused on every unwrap path.
- **Raw handles.** `getMCEntity`, `getMCLevel`, `getMCItemStack` and `getMCDamageSource`
  throw, because the Nashorn sandbox deliberately never exposes live entities or levels.
  Immutable or plain-data handles (`IPos.getMCBlockPos`, `INbt.getMCNBT`, `mcGetTag`)
  are returned.
- **Events.** `NpcAPI.events()` is a dedicated `BusBuilder` bus. Gameplay does **not** post
  typed XenoAPI events to it yet. Native script hooks keep their existing dispatch.

## Semantics that differ from CustomNPCs

- **Numbers.** CustomNPCs addresses factions, dialogs and quests by int. Only content imported
  from CustomNPCs or My NPCs has one: the import writes `SourceMod`/`SourceSlot` on the store
  entry (`XenoScriptIds`), and entries imported before that are recognised by the importer's
  ids (`<group>_q<n>`, `dialog_<n>` in a `mynpcs_`/`customnpcs_` group). Xeno-made content
  answers `getId()` with -1 and is reached through the handler lists, or by native id through
  the handlers' `get(String)`. A number two entries claim resolves to nothing. Saving an
  imported entry from the editor keeps its number.
- **Dialogs.** An `IDialog` is one node of a native conversation tree; imported dialog N is the
  tree `dialog_N` at its start node. An option leads only to a node of the same tree. Commands
  run only from an option the player picks (and only with `xenoNpcDialogueCommands` on), so
  `IDialog.setCommands` makes "Continue" command options. A quest is offered through a quest
  option. Native dialogs have no availability of their own: `getAvailability` is always
  available and its setters are refused. `IPlayer.showDialog` opens the tree anchored on the
  player; the option packet answers from the tree the script sent (`ScriptShownDialogues`).
- **Quests.** Setters edit a view; `save()` validates as the editor does and writes the store,
  which shadows a datapack or built-in quest. Rewards are item ids and counts (components are
  not kept). `setType` replaces the objectives with one of the new kind.
- **Factions.** `save()` writes the store copy (shadowing a pack faction) and re-syncs clients.
  Status is -1/0/1 from the player's standing against the native thresholds.
- **Mail** is delivered at once: chat, items (or dropped), and the attached quest started.
- **`clearData`** resets quests, read dialogs, faction points, transport and item-giver history,
  and keeps bank vaults.
- **Music** plays once on the music channel; `loops`/`background` are ignored.
- **`hasPermission`** asks NeoForge's `PermissionAPI` for a registered boolean node; unknown is false.
- **`trigger(id, args)`** fires hook `trigger` on forge tabs, then the entity's own tabs, then Java
  listeners, with `event.id`, `event.arguments` and `event.xeno` (a `ScriptTriggerEvent`).
  Nesting stops at 4 levels.
- **Marks** exist on native NPCs only (one mark each; the editor's Mark setting).
- Player quest ids are the native quest **slots** the `player` binding already uses.
- `ITimers.start` / `reset` throw `CustomNPCsException` when the native owner refuses
  (existing id, ticks outside 1-1200000, over 64 timers, unknown id); nothing is silently ignored.
- `IEntity.getNbt` returns the entity's live persistent data, as CustomNPCs does.
  `getEntityNbt` / `IItemStack.getItemNbt` return detached snapshots; `setEntityNbt` is refused
  for players.
- 1.21.1 items have components, not NBT: `IItemStack.getNbt` is unsupported, while
  `hasNbt`/`removeNbt` act on the custom-data component.
- `IWorld.setBlock(x, y, z, name, meta)` places the default state. `meta` has no 1.21
  meaning and is ignored.
- `ICustomNpc.executeCommand` and `NpcAPI.executeCommand(Silent)` run at permission level 2,
  like the native `npc.executeCommand`. They return captured output and never send it to
  chat or operators.
- Some natively backed methods are unsupported for particular entities. For example,
  `isAttacking`/`get/setAttackTarget` exist only for mobs, and `setMaxHealth` only where the
  attribute exists. Those calls throw the same named error.

## Events (sub-project 1, 2026-09-27)

Spec: `docs/superpowers/specs/2026-09-27-xenoapi-event-foundation-design.md`.

Each occurrence builds one typed XenoAPI event and delivers it in this order:

1. **Scripts.** An existing hook receives today's event, with the typed object as `event.xeno`.
   A hook new in this slice receives the typed object as its argument.
2. **Java.** Listeners on `NpcAPI.events()` run next.
3. **Read-back.** The native call site applies the final cancel and damage values once.

`event.setCanceled`/`setDamage` on the old object write through to `event.xeno`, and a Java
listener's change wins because it runs last. A typed event is built only when a tab defines the
hook or a Java listener is registered. `events()` counts registrations, because `IEventBus` has
no listener query.

| Scope | Hook | Source | Read-back |
| --- | --- | --- | --- |
| NPC init | `init` | script host build (or first dispatch, for Java, when the NPC has no scripts) | none |
| NPC update | `tick` | every 10 ticks | none |
| NPC interact / damaged / target / targetLost / meleeAttack | existing hooks | existing call sites | cancel; damage for damaged and meleeAttack; `clearTarget` on damaged |
| NPC died / kill / collide / timer | existing hooks | existing call sites | none (typed events not cancellable; the old flag keeps working) |
| NPC ranged | `rangedLaunched` (native) and `rangedAttack` (contract name, typed) | ki dispatcher | none |
| Player init / login / logout / chat | existing hooks + `event.xeno` | `PlayerScriptHost` | chat: cancel and a listener's message rewrite |
| Player tick | `tick` | `PlayerTickEvent.Post`, every 10 ticks | none |
| Player interact | `interact` | type 0 `RightClickItem` (item in air), 1 `EntityInteract`, 2 `RightClickBlock`; main hand only | cancel |
| Player attack | `attack` | 1 `AttackEntityEvent`, 2 `LeftClickBlock` (`Action.START`) | cancel |
| Player damagedEntity, then damaged | `damagedEntity`, `damaged` | `LivingIncomingDamageEvent` | cancel; damage → `setAmount`; `clearTarget` clears a mob targeting the victim |
| Player died / kill | `died`, `kill` | `LivingDeathEvent` | died: cancel cancels the death and sets health to 1.0 |
| Player toss | `toss` | `ItemTossEvent` | cancel puts the stack back (NeoForge already removed it); a full inventory lets the toss happen |
| Player pickedUp | `pickedUp` | `ItemEntityPickupEvent.Pre` | cancel → `setCanPickup(FALSE)` |
| Player rangedLaunched | `rangedLaunched` | `ArrowLooseEvent` | cancel |
| Player levelUp | `levelUp` | `PlayerXpEvent.LevelChange` (`change` = levels) | none (not cancellable in XenoAPI) |
| Player broken | `broken` | `BlockEvent.BreakEvent`; `exp` from `getExpDrop` | cancel; a changed `exp` is applied by `BlockDropsEvent` |
| Player containerOpen / containerClosed | same | `PlayerContainerEvent.Open/Close` | none |
| Player timer | `timer` | native player timers (`IPlayer.getTimers`, saved in `XenoScriptTimers`) | none |

**Not posted yet.** Key pressed/released, empty-hand right-click air, left-click air, and
`playSound` wait for sub-project 5 (they need client packets). `FactionUpdateEvent`, quest and
dialog events wait for sub-project 2. Block, item, projectile, role, GUI and world events wait
for sub-projects 3-5.

**Fields with no native source this slice.**
- `NpcEvent.DiedEvent.line` is null, and `droppedItems`/`expDropped` keep their constructor
  values (drops are computed after the hook).
- `NpcEvent.RangedLaunchedEvent.projectiles` is empty (ki attacks are not `IProjectile`s), and
  its `damage` is 0 and not read back.

## Alignment with the reference documentation (2026-09-27)

The owner named `https://www.kodevelopment.nl/customnpcs/api/1.16.5/` as a reading reference. It
is not a dependency. These adapters were adjusted to match its documented behaviour:

- **Potions.** `addPotionEffect`'s flag hides particles.
- **Light.** `getLightValue` returns 0-1.
- **Stack size.** `setStackSize` takes 1..max.
- **Missing items.** `removeItem(String)` returns false and `inventoryItemCount(String)` returns
  0 for an unknown id.
- **Creative give.** NPC `giveItem` does not drop for creative players.
- **Owner.** `getOwner` returns the followed entity: the follower-job target, or the owner for
  the COMPANION role.
- **Animations.** `playAnimation` 0/2/3 sends vanilla `ClientboundAnimatePacket`.
- **Script data.** Temp and stored data work for every entity (stored in persistent data
  `XenoScriptData`), for the world (overworld SavedData, with shared temp data), and for items
  (custom data `XenoScriptData`, with per-instance temp data).
- **Item attributes.** `getAttackDamage` sums main-hand `ADD_VALUE` attack modifiers, and
  `set/get/hasAttribute` use slot codes -1..5.
- **Kept deviation.** `IItemStack.getNbt` (1.21 components).

## Capability table

Generated on 2026-09-30 by `python scripts/xenoapi_capabilities.py`. Each row counts the
methods that class declares, so inherited rows are not repeated: the NPC and player adapters
inherit the `IEntity` and `IEntityLiving` rows. The NPC sub-objects are in one file and are
listed by hand below.

| Contract | Native | Unsupported |
| --- | ---: | ---: |
| `IEntity` | 73 | 1 |
| `IEntityLiving` | 31 | 1 |
| `ICustomNpc / IMob` | 33 | 3 |
| `IPlayer` | 56 | 7 |
| `IWorld` | 46 | 1 |
| `IItemStack` | 41 | 2 |
| `INbt` | 37 | 0 |
| `IPos` | 24 | 0 |
| `IData` | 6 | 0 |
| `ITimers` | 6 | 0 |
| `IDamageSource` | 5 | 1 |
| `IBlock` | 25 | 3 |
| `IContainer` | 5 | 2 |
| `NpcAPI` | 27 | 4 |
| `IFaction` | 17 | 0 |
| `IFactionHandler` | 4 | 0 |
| `IQuest` | 21 | 0 |
| `IQuestCategory` | 4 | 0 |
| `IQuestHandler` | 2 | 0 |
| `IQuestObjective` | 6 | 0 |
| `IDialog` | 14 | 0 |
| `IDialogOption` | 11 | 0 |
| `IDialogCategory` | 4 | 0 |
| `IDialogHandler` | 2 | 0 |
| `IAvailability` | 14 | 0 |
| `ICloneHandler` | 4 | 0 |
| `IPlayerMail` | 10 | 0 |
| `IMark` | 6 | 0 |
| `IEntityItem` | 10 | 0 |
| **Total** | **544** | **25** |

### `IEntity`

Native: `getX()`, `getY()`, `getZ()`, `setX(double)`, `setY(double)`, `setZ(double)`, `getBlockX()`, `getBlockY()`, `getBlockZ()`, `getPos()`, `setPos(IPos)`, `setPosition(double, double, double)`, `getRotation()`, `setRotation(float)`, `getPitch()`, `setPitch(float)`, `getHeight()`, `getEyeHeight()`, `getWidth()`, `getMount()`, `setMount(IEntity)`, `getRiders()`, `getAllRiders()`, `addRider(IEntity)`, `clearRiders()`, `knockback(int, float)`, `isSneaking()`, `isSprinting()`, `getMotionX()`, `getMotionY()`, `getMotionZ()`, `setMotionX(double)`, `setMotionY(double)`, `setMotionZ(double)`, `inWater()`, `inLava()`, `inFire()`, `isAlive()`, `getAge()`, `isBurning()`, `setBurning(int)`, `extinguish()`, `despawn()`, `spawn()`, `kill()`, `damage(float)`, `getWorld()`, `getTypeName()`, `getType()`, `typeOf(int)`, `getUUID()`, `getName()`, `setName(String)`, `hasCustomName()`, `getEntityName()`, `getTags()`, `addTag(String)`, `hasTag(String)`, `removeTag(String)`, `getTempdata()`, `getStoreddata()`, `getEntityNbt()`, `getNbt()`, `setEntityNbt(INbt)`, `dropItem(IItemStack)`, `generateNewUUID()`, `storeAsClone(int, String)`, `rayTraceBlock(double, boolean, boolean)`, `getPos()`, `getBlock()`, `getSideHit()`, `rayTraceEntities(double, boolean, boolean)`, `playAnimation(int)`.

Unsupported: `getMCEntity()`.

### `IEntityLiving`

Native: `getHealth()`, `setHealth(float)`, `getMaxHealth()`, `setMaxHealth(float)`, `isAttacking()`, `setAttackTarget(IEntityLiving)`, `getAttackTarget()`, `getLastAttacked()`, `getLastAttackedTime()`, `canSeeEntity(IEntity)`, `swingMainhand()`, `swingOffhand()`, `getMainhandItem()`, `getOffhandItem()`, `setMainhandItem(IItemStack)`, `setOffhandItem(IItemStack)`, `getArmor(int)`, `setArmor(int, IItemStack)`, `addPotionEffect(int, int, int, boolean)`, `clearPotionEffects()`, `getPotionEffect(int)`, `isChild()`, `getMoveForward()`, `getMoveStrafing()`, `getMoveVertical()`, `setMoveForward(float)`, `setMoveStrafing(float)`, `setMoveVertical(float)`, `addMark(int)`, `removeMark(IMark)`, `getMarks()`.

Unsupported: `getMCEntity()`.

### `ICustomNpc / IMob`

Native: `getName()`, `setName(String)`, `say(String)`, `sayTo(IPlayer, String)`, `getTempdata()`, `getStoreddata()`, `getTimers()`, `isNavigating()`, `clearNavigation()`, `navigateTo(double, double, double, double)`, `getNavigationPath()`, `jump()`, `getHomeX()`, `getHomeY()`, `getHomeZ()`, `setHome(int, int, int)`, `getOwner()`, `giveItem(IPlayer, IItemStack)`, `executeCommand(String)`, `getDisplay()`, `getInventory()`, `getStats()`, `getAi()`, `getAdvanced()`, `getRole()`, `getJob()`, `getFaction()`, `setFaction(int)`, `setDialog(int, IDialog)`, `getDialog(int)`, `reset()`, `updateClient()`, `trigger(int, Object...)`.

The two `shootItem` overloads now create native item projectiles; see
[projectile behavior and limits](native-item-projectiles-2026-10-09.md).
Unsupported: `getMCEntity()`.

### `IPlayer`

Native: `getDisplayName()`, `message(String)`, `isOp()`, `kick(String)`, `hasFinishedQuest(int)`, `hasActiveQuest(int)`, `startQuest(int)`, `finishQuest(int)`, `stopQuest(int)`, `getTempdata()`, `getStoreddata()`, `getGamemode()`, `setGamemode(int)`, `getExpLevel()`, `setExpLevel(int)`, `getHunger()`, `setHunger(int)`, `hasAdvancement(String)`, `setSpawnpoint(int, int, int)`, `resetSpawnpoint()`, `inventoryItemCount(IItemStack)`, `inventoryItemCount(String)`, `removeItem(IItemStack, int)`, `removeItem(String, int)`, `removeAllItems(IItemStack)`, `getInventoryHeldItem()`, `giveItem(IItemStack)`, `giveItem(String, int)`, `giveOrDropItems(IItemStack[])`, `updatePlayerInventory()`, `closeGui()`, `playSound(String, float, float)`, `factionStatus(int)`, `addFactionPoints(int, int)`, `getFactionPoints(int)`, `removeQuest(int)`, `getActiveQuests()`, `getFinishedQuests()`, `canQuestBeAccepted(int)`, `hasReadDialog(int)`, `showDialog(int, String)`, `removeDialog(int)`, `addDialog(int)`, `getInventory()`, `hasPermission(String)`, `getTimers()`, `getSpawnPoint()`, `setSpawnPoint(IBlock)`, `sendNotification(String, String, int)`, `sendMail(IPlayerMail)`, `clearData()`, `playMusic(String, boolean, boolean)`, `stopMusic()`, `openWebsite(String)`, `trigger(int, Object...)`, `getOpenContainer()`.

Unsupported: `setName(String)`, `getPixelmonData()`, `showCustomGui(ICustomGui)`, `getCustomGui()`, `getScreenWidth()`, `getScreenHeight()`, `getMCEntity()`.

### `IWorld`

Native: `getNearbyEntities(int, int, int, int, int)`, `getNearbyEntities(IPos, int, int)`, `getClosestEntity(int, int, int, int, int)`, `getClosestEntity(IPos, int, int)`, `getAllEntities(int)`, `getPlayer(String)`, `getAllPlayers()`, `getEntity(String)`, `createEntity(String)`, `createEntityFromNBT(INbt)`, `spawnEntity(IEntity)`, `getTime()`, `getTotalTime()`, `setTime(long)`, `isDay()`, `isRaining()`, `setRaining(boolean)`, `thunderStrike(double, double, double)`, `setBlock(int, int, int, String, int)`, `removeBlock(int, int, int)`, `removeBlock(IPos)`, `getLightValue(int, int, int)`, `getRedstonePower(int, int, int)`, `triggerBlockUpdate(IPos)`, `getBiomeName(int, int)`, `getMCBlockPos(int, int, int)`, `playSoundAt(IPos, String, float, float)`, `spawnParticle(String, double, double, double, double, double, double, double, int)`, `broadcast(String)`, `explode(double, double, double, float, boolean, boolean)`, `createItem(String, int)`, `createItemFromNbt(INbt)`, `getName()`, `getBlock(int, int, int)`, `getBlock(IPos)`, `setBlock(IPos, String)`, `getDimension()`, `getScoreboard()`, `getTempdata()`, `getStoreddata()`, `spawnClone(double, double, double, int, String)`, `getClone(int, String)`, `getSpawnPoint()`, `setSpawnPoint(IBlock)`, `trigger(int, Object...)`, `spawnParticleBlock(String, double, double, double, double, double, double, double, int)`.

Unsupported: `getMCLevel()`.

### `IItemStack`

Native: `getStackSize()`, `setStackSize(int)`, `getMaxStackSize()`, `isDamageable()`, `getDamage()`, `setDamage(int)`, `getMaxDamage()`, `isEnchanted()`, `hasEnchant(String)`, `addEnchantment(String, int)`, `removeEnchant(String)`, `isBlock()`, `isWearable()`, `isBook()`, `isEmpty()`, `getType()`, `hasCustomName()`, `setCustomName(String)`, `getDisplayName()`, `getItemName()`, `getName()`, `getLore()`, `setLore(String[])`, `getFoodLevel()`, `hasNbt()`, `removeNbt()`, `getItemNbt()`, `copy()`, `split(int)`, `compare(IItemStack, boolean)`, `compare(IItemStack, boolean, boolean)`, `compare(ItemStack, boolean, boolean)`, `getAttackDamage()`, `setAttribute(String, double)`, `setAttribute(String, double, int)`, `getAttribute(String)`, `hasAttribute(String)`, `damageItem(int, IMob)`, `getTempdata()`, `getStoreddata()`, `use(IEntityLiving, boolean)`.

Unsupported: `getMCItemStack()`, `getNbt()`.

### `INbt`

Native: `remove(String)`, `has(String)`, `getBoolean(String)`, `setBoolean(String, boolean)`, `getShort(String)`, `setShort(String, short)`, `getInteger(String)`, `setInteger(String, int)`, `getByte(String)`, `setByte(String, byte)`, `getLong(String)`, `setLong(String, long)`, `getDouble(String)`, `setDouble(String, double)`, `getFloat(String)`, `setFloat(String, float)`, `getString(String)`, `putString(String, String)`, `getByteArray(String)`, `setByteArray(String, byte[])`, `getIntegerArray(String)`, `setIntegerArray(String, int[])`, `getList(String, int)`, `getListType(String)`, `setList(String, Object[])`, `getCompound(String)`, `setCompound(String, INbt)`, `getKeys()`, `getType(String)`, `getMCNBT()`, `toJsonString()`, `isEqual(INbt)`, `clear()`, `isEmpty()`, `merge(INbt)`, `mcSetTag(String, Tag)`, `mcGetTag(String)`.

Unsupported: none.

### `IPos`

Native: `getX()`, `getY()`, `getZ()`, `up()`, `up(int)`, `down()`, `down(int)`, `north()`, `north(int)`, `east()`, `east(int)`, `south()`, `south(int)`, `west()`, `west(int)`, `add(int, int, int)`, `add(IPos)`, `subtract(int, int, int)`, `subtract(IPos)`, `normalize()`, `getMCBlockPos()`, `offset(int)`, `offset(int, int)`, `distanceTo(IPos)`.

Unsupported: none.

### `IData`

Native: `put(String, Object)`, `get(String)`, `remove(String)`, `has(String)`, `getKeys()`, `clear()`.

Unsupported: none.

### `ITimers`

Native: `start(int, int, boolean)`, `forceStart(int, int, boolean)`, `has(int)`, `stop(int)`, `reset(int)`, `clear()`.

Unsupported: none.

### `IDamageSource`

Native: `getType()`, `isUnblockable()`, `isProjectile()`, `getTrueSource()`, `getImmediateSource()`.

Unsupported: `getMCDamageSource()`.

### `IBlock`

Native: `getX()`, `getY()`, `getZ()`, `getPos()`, `getName()`, `getDisplayName()`, `isAir()`, `isRemoved()`, `getWorld()`, `hasTileEntity()`, `isContainer()`, `getContainer()`, `getProperty(String)`, `getProperties()`, `getBlockEntityNBT()`, `setProperty(String, Object)`, `remove()`, `setBlock(String)`, `setBlock(IBlock)`, `getTempdata()`, `getStoreddata()`, `setTileEntityNBT(INbt)`, `setChanged()`, `blockEvent(int, int)`, `interact(int, IEntityLiving)`.

Unsupported: `getMCTileEntity()`, `getMCBlock()`, `getMCBlockState()`.

### `IContainer`

Native: `getSize()`, `getSlot(int)`, `setSlot(int, IItemStack)`, `count(IItemStack, boolean, boolean)`, `getItems()`.

Unsupported: `getMCInventory()`, `getMCContainer()`.

### `NpcAPI`

Native: `createNPC(Level)`, `spawnNPC(Level, int, int, int)`, `getIEntity(Entity)`, `getIBlock(Level, BlockPos)`, `getIItemStack(ItemStack)`, `getIWorld(ServerLevel)`, `getINbt(CompoundTag)`, `getIPos(double, double, double)`, `getIDamageSource(DamageSource)`, `getIWorld(String)`, `getIWorld(DimensionType)`, `getIWorlds()`, `stringToNbt(String)`, `executeCommand(IWorld, String)`, `executeCommandSilent(IWorld, String)`, `getLevelDir()`, `events()`, `getIContainer(Container)`, `getIContainer(AbstractContainerMenu)`, `getFactions()`, `getQuests()`, `getDialogs()`, `getClones()`, `createMail(String, String)`, `getRawPlayerData(String)`, `getGlobalDir()`, `hasPermissionNode(String)`.

Unsupported: `getRecipes()`, `registerScriptEvent(Class)`, `getRandomName(int, int)`, `createCustomGui(String, int, int, boolean, IPlayer)`.

### `IFaction`

Native: `getId()`, `getName()`, `getDefaultPoints()`, `getColor()`, `setDefaultPoints(int)`, `playerStatus(IPlayer)`, `hostileToNpc(ICustomNpc)`, `hostileToFaction(int)`, `getHostileList()`, `addHostile(int)`, `removeHostile(int)`, `hasHostile(int)`, `getIsHidden()`, `setIsHidden(boolean)`, `getAttackedByMobs()`, `setAttackedByMobs(boolean)`, `save()`.

Unsupported: none.

### `IFactionHandler`

Native: `list()`, `get(int)`, `delete(int)`, `create(String, int)`.

Unsupported: none.

### `IQuest`

Native: `getId()`, `getName()`, `setName(String)`, `getType()`, `setType(int)`, `getLogText()`, `setLogText(String)`, `getCompleteText()`, `setCompleteText(String)`, `getNextQuest()`, `setNextQuest(IQuest)`, `getObjectives(IPlayer)`, `getCategory()`, `getRewards()`, `setRewards(IItemStack[])`, `getNpcName()`, `setNpcName(String)`, `getIsRepeatable()`, `getCommands()`, `setCommands(String...)`, `save()`.

Unsupported: none.

### `IQuestCategory`

Native: `quests()`, `getName()`, `setName(String)`, `create()`.

Unsupported: none.

### `IQuestHandler`

Native: `categories()`, `get(int)`.

Unsupported: none.

### `IQuestObjective`

Native: `getProgress()`, `setProgress(int)`, `getMaxProgress()`, `isCompleted()`, `getText()`, `getMCText()`.

Unsupported: none.

### `IDialog`

Native: `getId()`, `getName()`, `setName(String)`, `getText()`, `setText(String)`, `getQuest()`, `setQuest(IQuest)`, `getCommands()`, `setCommands(String...)`, `getOptions()`, `getOption(int)`, `getAvailability()`, `getCategory()`, `save()`.

Unsupported: none.

### `IDialogOption`

Native: `getSlot()`, `getName()`, `setName(String)`, `getText()`, `setText(String)`, `getType()`, `setType(int)`, `getCommands()`, `setCommands(String...)`, `getDialog()`, `setDialog(IDialog)`.

Unsupported: none.

### `IDialogCategory`

Native: `dialogs()`, `getName()`, `setName(String)`, `create()`.

Unsupported: none.

### `IDialogHandler`

Native: `categories()`, `get(int)`.

Unsupported: none.

### `IAvailability`

Native: `isAvailable(IPlayer)`, `getDaytime()`, `setDaytime(int)`, `getMinPlayerLevel()`, `setMinPlayerLevel(int)`, `getDialog(int)`, `setDialog(int, int, int)`, `removeDialog(int)`, `getQuest(int)`, `setQuest(int, int, int)`, `removeQuest(int)`, `setFaction(int, int, int, int)`, `removeFaction(int)`, `setScoreboard(int, String, int, int)`.

Unsupported: none.

### `ICloneHandler`

Native: `spawn(double, double, double, int, String, IWorld)`, `get(int, String, IWorld)`, `set(int, String, IEntity)`, `remove(int, String)`.

Unsupported: none.

### `IPlayerMail`

Native: `getSender()`, `setSender(String)`, `getSubject()`, `setSubject(String)`, `getText()`, `setText(String)`, `getQuest()`, `setQuest(int)`, `getItem(int)`, `setItem(int, IItemStack)`.

Unsupported: none.

### `IMark`

Native: `getAvailability()`, `getColor()`, `setColor(int)`, `getType()`, `setType(int)`, `update()`.

Unsupported: none.

### `IEntityItem`

Native: `getOwner()`, `setOwner(String)`, `getPickupDelay()`, `setPickupDelay(int)`, `getAge()`, `setAge(long)`, `getLifeSpawn()`, `setLifeSpawn(int)`, `getItem()`, `setItem(IItemStack)`.

Unsupported: none.

## NPC sub-objects (2026-09-30)

`ICustomNpc.getDisplay/getStats/getAi/getInventory/getAdvanced/getRole/getJob` read the NPC's
live profile and write through `NpcCombatProfile.write`, as the editor does (`XenoNpcViews`).
Conversions: melee delay ticks = `20 / attack speed`; resistance `v` (1 normal) = native
`(v - 1) * 100` percent, as the importer converts; accuracy is a percent; effect times are
seconds; projectile speed and size are 10 and 5 CustomNPCs units per native unit; path walking
speed is 5 units per native 1.0. `getRole` is an `IRoleTrader` for traders (item ids and counts).
`getJob().getType()` follows `JobType`, which the native job list matches.

Current additions: show-name modes, conditional boss bars, hiding dead bodies and path pauses
have native persisted behavior. Navigation type reads the actual navigation class; changing
the navigator remains refused. Refused settings include living animation, per-part model
scale, hitbox state, respawn types 1/2/4, ranged 3D rendering, AI animation/standing types,
stop on interact, inventory projectile prototypes and trader markets. Semi-invisible still
approximates invisible; fire types 1 and 2 map to native indirect fire.

## Clean checkout setup

`XenoNPCsAPI/` is a separate source checkout, not a tracked directory or submodule of this
repository. A clean root checkout needs it at the exact relative path before any Gradle task
can configure. The source identity used for this integration is
`https://github.com/AgentMelinda/xenoapi.git` at commit
`b268e63f667bce504921de7ab75e30ff9e840e8e`. On 2026-09-27, read-only `git ls-remote`
confirmed that the remote's `HEAD` and `main` both pointed to that commit. Pin the full hash,
because the branch can move.

```powershell
git clone https://github.com/AgentMelinda/xenoapi.git XenoNPCsAPI
git -C XenoNPCsAPI checkout --detach b268e63f667bce504921de7ab75e30ff9e840e8e
git -C XenoNPCsAPI rev-parse HEAD
```

`settings.gradle` fails with a link to this section when `NpcAPI.java` is missing from that
path. The nested repository is not modified by this integration.

## Verification (2026-09-27)

| Check | Result |
| --- | --- |
| Focused tests: `XenoApiAdaptersTest` 11, `NpcScriptSharedStateTest` 3, `NativeNpcApiTest` 5, `XenoApiScriptCompatibilityTest` 7 (real sandboxed Nashorn), `ExampleScriptsCompileTest` 1, plus `ScriptNpcDataTest`, `ScriptTimersTest`, `PlayerScriptDataTest`, `BundledNashornTest`, `NpcScriptEngineSeamTest` | All passed |
| `./gradlew test -PofflineMcMeta` | 2735 run, 2734 passed. `SceneTriggersTest.damageThatDidNotLandDoesNotFire` failed. That source-text check reads 700 characters after `hurt(` in `XenoNpcEntity.java`, a file this work did not touch; an earlier `damaged`-hook block pushed `boolean landed = super.hurt(` past the window. Unrelated; left for its owner. |
| `./gradlew build jarJar serverJar -PofflineMcMeta -x test` | Passed (`-x test` because the suite ran separately) |
| `./gradlew buildApiExampleAddon -PofflineMcMeta` | Passed; the consumer compiles against the packaged jar using XenoAPI types only |
| All 22 `xenopixels_*.js` + 2 `xenoapi_*.js` examples | Compile in the bundled sandboxed engine (syntax only) |

| Artifact (`build/libs`) | Bytes | SHA-256 | `xenoapi` classes | Adapter classes | Duplicates | `META-INF/jarjar/` files |
| --- | ---: | --- | ---: | ---: | ---: | ---: |
| `xenopixelsmod-0.5.0-1.21.1.jar` | 42,401,733 | `6BF978462833E2D5E0072C5F10BCDA4B5B996DB23AEF9BF90811EFD9565CCE3C` | 217 | 17 | 0 | 3 |
| `xenopixelsmod-Server-0.5.0-1.21.1.jar` | 18,413,171 | `F6148F1A6F2B250C66A7B123D4A09297747EBCE1E4611712DCEE6B45DA6CD87D` | 217 | 17 | 0 | 0 |
| `xenopixelsmod-0.5.0-1.21.1-sources.jar` | 14,140,571 | `3C2BDAD7923BA8551DFBBBA73E4CD04402BEEBBBAE6D4E1AD37F7802D98DB5F4` | one `NpcAPI.java` | - | 0 | 0 |

**Runtime, verified in a fresh `./gradlew runApiTestClient` process**
(`run/logs/latest.log`, 2026-09-27):

- `20:33:43.031`: `XenoAPI: native Xeno NPC implementation registered`, during mod construction.
- `20:33:43.310`: the separate example addon logged `XenoAPI available=true`.
- `20:33:51.092`: sound engine started (client reached the menu). No XenoAPI error. The log's
  5 `ERROR` lines are Curios reporting absent `customnpcs:*` entity types, which predates this work.

**Not verified in game:** spawning through `NpcAPI.spawnNPC`, speech, targeting, navigation,
a XenoAPI timer firing the `timer` hook, `XenoPixels.fromXeno` extension calls, the two
`xenoapi_*.js` examples, and `/xenoapitest xenoapi`. These need a player in a world. To
check them, join a world, place a native Xeno NPC, paste
`examples/customnpcs/xenoapi_native_greeter.js`, right-click it, then run
`/xenoapitest xenoapi` within 16 blocks. Expect a hello bubble and, one second later, the
greeter's follow-up line after the right-click.

## Rollback

Additive; no save conversion. Remove the `xeno/` adapter package, the `NativeNpcApi.register()`
line, the `XenoAPI` bindings and `toXeno`/`fromXeno`, the `sharedState` seam in
`NpcScriptHost`, and the `srcDir` line. Existing wrappers, stored NPC data and scripts stay
usable.
