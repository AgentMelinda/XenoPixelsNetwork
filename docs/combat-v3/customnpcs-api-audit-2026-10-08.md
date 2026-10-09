# CustomNPCs 1.18.2 API contract and native-runtime audit

Inventory captured on 2026-10-08; review completed on 2026-10-09. This is a source audit,
not a claim that every documented function works in a running XenoPixels game.

## Implemented after the inventory snapshot, 2026-10-09

- Native NPC, world, player and event wrappers expose the existing typed adapters through
  `getAPI()`. The editor catalogs nested interface calls while preserving legacy contracts.
- `world.spawnClone(double,double,double,int,String)` is available directly on the native
  script wrapper. Clone state shares the normal NPC script host.
- `IItemStack.getNbt()` now provides a live, component-backed custom-data view; nested compound
  and compound-list writes persist. Legacy built-in name/enchantment tags remain separate
  1.21 components. See [item NBT semantics](../item-script-nbt-2026-10-08.md).
- Item wrapping now returns `IItemArmor`, `IItemBook`, or `IItemBlock` when applicable. Copies
  and splits retain specialization. Book setters operate on real written/writable components,
  preserve unrelated metadata, and reject invalid input before committing.
- Entity wrapping now supplies `IMob`, `IAnimal`, `IMonster`, `IVillager`, `IArrow`, and
  `IThrowable` views. Ordinary mob navigation uses server-thread guards, a 256-block maximum
  destination distance and speed in `(0,3]`. Typed `IProjectile` now wraps a registered
  native item throwable created by both `shootItem` overloads. It supplies ordinary damage,
  collision, persistence, item/gravity/accuracy/heading controls and a client item renderer;
  `enableEvents` captures the exact originating NPC script tab for projectile tick/impact
  delivery with reload/world invalidation and post-callback collision revalidation. Player
  and Forge script-container capture and inventory prototypes remain explicit gaps.
- Name visibility modes, hiding dead bodies and boss-bar visibility while attacking now
  persist and synchronize through the native profile. Path pause mode now waits 20 ticks
  at reached waypoints; failed waypoint timeouts do not create a pause. Navigation type
  reads the actual navigator class, while replacing that navigator remains refused.
- Active Bard and Follower jobs now expose `IJobBard` and `IJobFollower` views over their real
  native profile fields. Bard ranges are bounded to 0-128; its background toggle maps to the
  native RECORDS versus AMBIENT sound category. Its looping behavior retains the native
  200-tick replay interval. Follower lookup uses the native 20-block named-NPC search;
  `getFollowingNpc()` returns only a native Xeno NPC, while `isFollowing()` also recognizes
  supported external NPC targets.
- Transporter roles now expose `IRoleTransporter` and a read-only `ITransportLocation` for a
  network with exactly one usable destination. Each `getLocation()` reads the current native
  network on the server thread; absent/unfinished networks return null, and multiple usable
  destinations explicitly refuse selection. Native networks store no origin location, so this
  is a bounded destination view rather than complete CustomNPCs origin-location equivalence.
  Dimension/name and floored block coordinates come from the actual destination. `VISITED`
  maps to discover (0), `ALWAYS` to from-start (1); there is no distinct native type 2.
  Nonnegative integer destination IDs retain their value; string/negative/overflow IDs return
  -1 without hashing or allocating fake IDs. Four focused tests passed on 2026-10-09,
  followed by the full 3,649-test suite; in-game transporter behavior remains unverified.

The refusal count and factory descriptions below are the **pre-change audit snapshot**, not
current operational coverage. Custom GUI, reserved job types, remaining specialized roles, scripted blocks,
Pixelmon and the other documented native gaps have not been implemented by these changes.

## Evidence and comparison limits

Official sources: [all documented types](https://www.kodevelopment.nl/customnpcs/api/1.18.2/allclasses-index.html),
[type index](https://www.kodevelopment.nl/customnpcs/api/1.18.2/type-search-index.js), and
[member index](https://www.kodevelopment.nl/customnpcs/api/1.18.2/member-search-index.js).
The documentation uses split `index-files/index-1.html` navigation; `index-all.html` returns 404.

The [machine inventory](customnpcs-api-audit-2026-10-08.json) records source-index SHA-256 hashes,
every documented type, each interface's declared method signatures, and explicit refusal source lines.
It also inventories all 1243 documented method signatures and 117 constructor signatures across
all types, including `NpcAPI`. Class methods and constructors are inventoried rather than claimed
equivalent by the interface comparison.
It compares `noppes.npcs.api` to `xenoapi.npcs.api`, erasing parameter generic arguments and package
qualifiers. Nested interfaces and default methods are included. Return types, generic bounds,
behavioral equivalence, event firing, factory reachability and runtime success require separate checks.

| Contract inventory | Result |
| --- | ---: |
| Official documented types | 216 |
| Those types represented in local contracts | 216 |
| Local contract types | 217 |
| Official interfaces represented locally | 101 |
| Documented methods across all types inventoried | 1243 |
| Constructor signatures inventoried | 117 |
| Declared official interface method signatures compared | 1189 |
| Missing signatures under the bounded comparison | 0 |

The existing contracts already provide the broad declaration surface. Copying more interface
declarations will not fill the native implementation gaps below.

## Native source gap snapshot from 2026-10-08

The 2026-10-08 source snapshot of
`src/main/java/net/bullettrain/xenopixelsmod/npc/script/api/xeno/` contained 64 explicit refusal
throw sites, including 28 in `XenoNpcViews`. This historical count is not a current-state assertion.
It is a **callsite count**, not 64 missing methods:
some paths are conditional, some methods have multiple overloads, and shared helpers reject many
different calls. The JSON includes each exact source line.

| Area | Confirmed boundary |
| --- | --- |
| Custom GUI | `NpcAPI.createCustomGui(String,int,int,boolean,IPlayer)`, `IPlayer.showCustomGui(ICustomGui)`, `getCustomGui()`, `getScreenWidth()` and `getScreenHeight()` refuse. The 16 GUI interface contracts exist but the native GUI factory is unavailable. |
| Jobs and roles | `XenoNpcViews.job` returns only an `INPCJob.getType()` view. Its six specialized job interfaces, plus puppet-part data, are not supplied. `role` returns a specialized trader or generic type-only role; dialog, follower, transporter and transport-location views are not supplied. |
| Specialized wrappers | Entity wrapping supplies native NPC, player, living, dropped-item or generic entity adapters. It does not supply `IAnimal`, `IArrow`, `IMonster`, `IPixelmon`, `IProjectile`, `IThrowable` or `IVillager` views. Ordinary mobs use the living adapter. Item wrapping supplies `IItemStack`, rather than the four specialized item views. Block wrapping supplies `IBlock`, rather than the four specialized block/script/text-plane views. |
| Item projectile attacks | Both `ICustomNpc.shootItem` overloads refuse; native ki attacks are not implementations of item-projectile semantics. `INPCInventory.getProjectile()` and `setProjectile(IItemStack)` also refuse. |
| Recipes and naming | `NpcAPI.getRecipes()`, `registerScriptEvent(Class)` and `getRandomName(int,int)` refuse. A fixed native hook list does not implement arbitrary event registration. |
| Raw Minecraft handles | Entity, living, NPC, player, world, block, container, item and damage-source raw-handle getters refuse by design. These require an explicit decision about exposing raw internals, rather than being counted as implemented. |
| Legacy item NBT | `IItemStack.getNbt()` refuses because 1.21 item components differ from legacy item NBT. `getItemNbt()` is the provided alternative; behavioral interchangeability is not established. |
| Dialog availability | Availability reads report no conditions; six condition setters refuse. Removal methods do nothing. This does not implement independent dialog gating. |
| Pixelmon and player identity | `IPlayer.getPixelmonData()` and `setName(String)` refuse. Pixelmon declarations do not establish a native integration. |

## NPC view signatures needing implementation or an explicit capability decision

The following names were refusal paths in the 2026-10-08 snapshot of `XenoNpcViews`; exact source
lines from that snapshot are in the JSON:

- `INPCDisplay`: `getHasLivingAnimation()`, `setHasLivingAnimation(boolean)`, `getShowName()`,
  `setShowName(int)`, `setModelScale(int,float,float,float)`, `getModelScale(int)`,
  `setHitboxState(byte)`, `getHitboxState()`.
- `INPCStats`: `getHideDeadBody()`, `setHideDeadBody(boolean)`; `setRespawnType(int)` supports
  only types 0 and 3; `setAggroRange(int)` conditionally refuses a missing attribute.
- `INPCRanged`: `getRender3D()`, `setRender3D(boolean)`.
- `INPCAi`: `getAnimation()`, `setAnimation(int)`, `getCurrentAnimation()`,
  `getNavigationType()`, `setNavigationType(int)`, `getStandingType()`, `setStandingType(int)`,
  `getStopOnInteract()`, `setStopOnInteract(boolean)`, `getMovingPathPauses()`.
- `INPCInventory`: the projectile getter/setter above.
- `IRoleTrader`: `setMarket(String)`, `getMarket()`.

Conditional constraints need their own status. Max-health mutation requires a real attribute;
attack-target operations require a mob; marks require a native NPC; timer admission can refuse
duplicate IDs or capacity. Such guards are not proof that an entire interface is unimplemented.
Trader stacks also retain item IDs/counts, rather than all item components.

## Implementation priorities

1. Make the complete typed API reachable from the native scripting facade and show precise
   unsupported capabilities. Do not advertise a method as operational solely because it appears
   in a interface or generated function picker.
2. Add specialized job/role views over the existing native jobs and roles, followed by unsupported
   NPC display/AI/ranged settings where a native counterpart can be established.
3. Implement the GUI factory, widgets, synchronization and actual GUI events as one coherent
   capability; declaration coverage alone cannot provide usable custom screens.
4. Add specialized entity/item/block wrappers and genuine item projectile behavior. Treat recipe
   systems, Pixelmon, raw handles and 1.18-to-1.21 semantic differences as separate decisions.
5. Verify event families and callbacks in fresh processes. This audit does not count an event
   payload class as evidence that its hook fires.

No Gradle or game process was launched for this audit. No percentage of operational API support
is claimed. `scripts/xenoapi_capabilities.py` samples selected concrete classes and labels bodies
that do not immediately throw as native; it misses nested NPC views, omitted interface families
and conditional semantics, so its totals must not be presented as full API runtime coverage.
