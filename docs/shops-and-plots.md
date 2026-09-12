# Sign Shops + Plot System — Integration Research

**Research date:** 2026-09-12
**Branch:** `1.21.1` · MC 1.21.1 · NeoForge 21.1.248 · Java 21 · mod id `xenopixelsmod`

This document is the evidence base for the sign-shop and plot features. It is split into
`Verified` and `Not verified`. A claim appears under `Verified` only when the stated check was
actually performed. Everything unresolved stays under `Not verified` with the check that would
settle it. Nothing here is inferred from a mod name, a wiki, or a similar-looking API.

Evidence classes, strongest first:

1. `javap` / `jar tf` against the **exact resolved jar**
2. Official API returned by the vendor (Modrinth API)
3. Repository source in this tree
4. Tracked decompiled reference under `tools/generated/`
5. Official documentation for the pinned version

---

## 1. Verified — MMO Econ

**Evidence class 1 + 2.** Jar fetched from CurseMaven and inspected with `jar tf` and `javap`.

| Fact | Value |
|---|---|
| Coordinate | `curse.maven:mmo-econ-1553133:8829178` |
| Direct jar URL | `https://cursemaven.com/curse/maven/mmo-econ-1553133/8829178/mmo-econ-1553133-8829178.jar` |
| HTTP result | `200`, `application/java-archive`, 97 750 bytes |
| modId | `mmoecon` |
| Version | `1.1.0` |
| Display name | `MMO Econ` |
| Author | `Casp3rNZ` |
| License | `GPL-3.0-or-later` |
| Package | `com.casp3rnz.mmoecon` |
| Loader | `javafml`, `loaderVersion="[1,)"` |
| Description (declared) | "A Server-Side, MMO Style, Economy mod for Neoforge!" |

`maven-metadata.xml` and bare directory listings both return `404`, so the coordinate is
**pinned only** and cannot be version-range-resolved.

### Balance API — `com.casp3rnz.mmoecon.PlayerBalanceManager`

Every member is `public static`:

```
boolean hasAccount(UUID)
long    getBalance(UUID)
void    setBalance(UUID, long)
void    addBalance(UUID, long)
void    subtractBalance(UUID, long)
boolean hasFunds(UUID, long)
boolean transfer(UUID, UUID, long)
Map<UUID, Long> getBalances()
```

The class also exposes its own lifecycle handlers (`onServerAboutToStart`, `onPlayerJoin`,
`onServerTick`, `onServerStopping`). Those are MMO Econ's own event subscriptions and must
**not** be called by XenoPixels.

### Money helpers — `com.casp3rnz.mmoecon.Money`

```
long   ZERO
long   fromDouble(double)
double toDouble(long)
long   multiply(long, int)
String format(long)
```

All `public static`. `format(long)` is the correct way to render a price; do not hand-roll
currency formatting.

### Consequence for the design

`hasFunds(UUID, long)` plus `subtractBalance(UUID, long)` is enough to implement a purchase.
**`subtractBalance` returns `void`, and that is load-bearing.** A reflective invoke of a `void`
method yields `null`, so `withdraw` cannot decide success by testing the return value — it must
treat a completed invoke as success and a thrown exception as failure, with `hasFunds` as the
guard that runs first. `MmoEconBridge.withdraw` does exactly that through a package-private
`invokeStaticVoid` helper, so a purchase whose funds were confirmed is not then reported as
failed.
`transfer(UUID, UUID, long)` is available if a shop needs to pay a seller. Because the mod is
server-side only and its API is static, `MmoEconBridge` must:

- check the `mmoecon` mod id before touching anything,
- resolve the class reflectively so the mod loads when MMO Econ is absent,
- expose a documented "integration unavailable" state instead of guessing.

---

## 2. Verified — WorldEdit

**Evidence class 1 + 2.** Version list from the Modrinth API; jar inspected with `jar tf` and
`javap`.

| Fact | Value |
|---|---|
| Modrinth project | `1u6JkXh5` |
| Version id | `WTAFvuRx` |
| Version | `7.3.8` |
| Game versions | `1.21`, `1.21.1` |
| Loaders | `fabric`, `neoforge` |
| Environment | `server_only` |
| File | `worldedit-mod-7.3.8.jar`, 6 222 854 bytes |
| SHA-1 | `924fe181a29bd66be6783f6d589968ab0ee02250` |
| SHA-512 | `e039492df0b486e7ce76d0eaf8cb11eadad2e78220600b8498ab8eef4642a29e310a6bcb4378257553b3639c6d0bc1ebf0f73df3a7a677c6af6baf86716b0bc7` |
| modId | `worldedit` |

Suggested Gradle coordinate (Modrinth maven is already configured in `build.gradle`):

```gradle
compileOnly "maven.modrinth:1u6JkXh5:WTAFvuRx"
```

### Selection API — `com.sk89q.worldedit.LocalSession`

```
RegionSelector getRegionSelector(World)
void           setRegionSelector(World, RegionSelector)
boolean        isSelectionDefined(World)
Region         getSelection()            throws IncompleteRegionException
Region         getSelection(World)       throws IncompleteRegionException
World          getSelectionWorld()
```

`getSelection(World)` is the call a plot claim uses. It throws `IncompleteRegionException` when
the selection is not finished — that exception is a normal control path, not an error.

### Region points — `com.sk89q.worldedit.regions.Region`

```
BlockVector3 getMinimumPoint()
BlockVector3 getMaximumPoint()
World        getWorld()
int          getWidth()
int          getLength()
int          getArea()   // default
```

This is exactly what the plot system needs. **Y is taken from these points and then discarded**;
a plot is stored as a 2-D X/Z rectangle only.

### Player input — `com.sk89q.worldedit.event.platform.PlayerInputEvent`

```
PlayerInputEvent(Player, InputType)
Player    getPlayer()
InputType getInputType()
boolean   isCancelled()
void      setCancelled(boolean)
```

Extends `Event`, implements `Cancellable`.

### Wand-click entry points — `com.sk89q.worldedit.WorldEdit`

```
static WorldEdit getInstance()
PlatformManager   getPlatformManager()
EventBus          getEventBus()
SessionManager    getSessionManager()
boolean handleArmSwing(Player)
boolean handleRightClick(Player)
boolean handleBlockRightClick(Player, Location[, Direction])
boolean handleBlockLeftClick(Player, Location[, Direction])
```

`handleArmSwing` / `handleRightClick` return whether WorldEdit consumed the interaction. This is
how XenoPixels can delegate a wand click without reimplementing selection logic.

### Correction recorded

The event bus lives at `com.sk89q.worldedit.util.eventbus.EventBus`.
`com.sk89q.worldedit.event.EventBus` **does not exist** — a first `javap` attempt against that
name failed with `Error: class not found`. `WorldEdit.getEventBus()` returns the `util.eventbus`
type.

### Platform events available

`PlatformReadyEvent`, `PlatformUnreadyEvent`, `PlatformInitializeEvent`, `PlatformsRegisteredEvent`,
`SessionIdleEvent`, `BlockInteractEvent`, `CommandEvent`, `CommandSuggestionEvent`,
`ConfigurationLoadEvent`, `EditSessionEvent` (under `event.extent`).

---

## 3. Verified — repository state

- `build.gradle` declares CurseMaven as an exclusive-content repository
  (`exclusiveContent { ... filter { includeGroup 'curse.maven' } }`) and already uses
  `runtimeOnly "curse.maven:..."` for My NPCs.
- Existing soft-dependency pattern: `ConditionalMixinPlugin` gates mixins on mod/class presence.
- `ResourceLocation.tryParse(String)` returns `null` on malformed input and
  `ResourceLocation.fromNamespaceAndPath` is the constructor form; both are used across the tree
  and confirmed by `compileJava` on 2026-09-12.
- `SavedData` persistence uses `new Factory<>(Ctor, Loader)` plus
  `save(CompoundTag, HolderLookup.Provider)` and `setDirty()`, matching
  `features/party/PartyMetadataSavedData`.

---

## 3a. Verified — YAWP region creation API

**Evidence class 1.** `javap` against
`~/.gradle/caches/modules-2/files-2.1/maven.modrinth/yawp/1.21.1-0.6.3-beta3/4aa1e115f15475d8fa9252a606071ba3b4b40d34/yawp-1.21.1-0.6.3-beta3.jar`
on 2026-09-12. The dependency is already declared at `build.gradle:264` as
`compileOnly "maven.modrinth:yawp:${yawp_version}"`.

Region **creation** — new ground, since the pre-existing compat only *reads* a region name:

```
RegionManager.get()                                  → RegionManager (singleton)
RegionManager.getDimRegionApi(ResourceKey<Level>)    → Optional<ILevelRegionApi>
ILevelRegionApi.addLocalRegion(IMarkableRegion)      → boolean     // creation
ILevelRegionApi.removeLocal(IMarkableRegion)         → boolean
ILevelRegionApi.removeLocalRegion(String)            → boolean
ILevelRegionApi.getLocalRegion(String)               → Optional<IMarkableRegion>
RegionManager.save(IProtectedRegion)                 → void
RegionManager.save(ResourceKey<Level>)               → void
RegionManager.save(ServerLevel)                      → void
```

Builders:

```
new CuboidBuilder().area(BlockPos, BlockPos).build() → CuboidArea
new CuboidRegionBuilder(String name)
    .setArea(CuboidArea)                             → CuboidRegionBuilder
    .inDim(ResourceKey<Level>)                       → LocalRegionBuilder<CuboidRegion>
    .withPriority(int) / .withDefaultPriority()      → LocalRegionBuilder
    .on() / .off() / .active(boolean)                → LocalRegionBuilder
    .addPlayer(String group, Player)                 → LocalRegionBuilder
    .withFlags(RegionFlags) / .addFlag(IFlag)        → LocalRegionBuilder
    .build()                                         → CuboidRegion
```

`IProtectedRegion` supplies `getName()`, `getDim()`, `getRegionType()`, `isActive()`,
`setIsActive(boolean)`, `addPlayer(Player, String)`, `permits(Player)`, `getChildren()`.
`RegionType` is an enum: `GLOBAL`, `DIMENSION`, `LOCAL`, `TEMPLATE` — plots are `LOCAL`.

Also present: `RegionManager.getRegionsIn(int×6)`, `getIntersectingRegions(IMarkableRegion)`,
`getContainingRegions(IMarkableRegion)` and `findResponsibleRegion(BlockPos)`, so an overlap can
be double-checked against YAWP's own view rather than only against our `PlotArea`.

### Consequence for the bridge

`ILevelRegionApi` is the **renamed** interface — `YawpRegionLookup` records that
`getDimRegionApi` returned `Optional<IDimensionRegionApi>` on 0.6.2-beta1 and
`Optional<ILevelRegionApi>` on 0.6.3-beta3. Linking a new class directly against
`ILevelRegionApi` would repeat the `NoClassDefFoundError` failure `YawpRegionLookup` exists to
avoid, so `YawpRegionBridge` holds **no YAWP imports** and resolves everything reflectively.
Overloads are matched by **parameter type names**, not counts, because `RegionManager.save` has
three one-argument forms and `inDim` has two.

Region names are deterministic — `xenoplot_<ns>_<path>_<minX>_<minZ>` — so a restart re-matches
the existing region instead of creating a second one. Coordinates are encoded alphanumerically,
a negative as a leading `m` (`-5` → `m5`), so the whole name stays inside a conservative
character set for a name that is also typed into commands.

---

## 3b. Verified — plot sign grammar (`[XPLOT]`)

**Evidence class 3.** Repository source, 2026-09-12: `plot/PlotSignSyntax`, `plot/PlotSignData`,
`plot/PlotSignReader`, `plot/PlotSignInteraction`, `client/shop/SignShopRender.plotRows` and
`mixin/client/SignRendererShopMixin`. Compiled by `compileJava` (exit 0) and covered by
`PlotSignSyntaxTest` (9 tests, exit 0) on 2026-09-12. Runtime behaviour is **not** claimed here;
see section 4.

A second marker alongside `[XPSHOP]`, following the rule that the grammar version is implicit in
the marker token. Four lines:

```
Line 1: [XPLOT]          activation marker, case-insensitive, surrounding whitespace allowed
Line 2: <minX>,<minZ>    plot origin
Line 3: <maxX>,<maxZ>    plot far corner
Line 4: <price>          decimal; an optional trailing currency tag is discarded
```

- The **dimension is not on the sign** — it is the level the sign stands in, which both sides
  already know. That keeps every line inside the client-typing length and lets the client render
  without ever reading server plot state, exactly as `[XPSHOP]` does.
- Coordinates are limited to `|c| <= 30_000_000` (the vanilla world border) purely so
  width/length arithmetic cannot overflow; the limit is not a validity claim about the plot.
- `PlotSignData`'s compact constructor normalises the corners, so `width()`/`length()` and any
  overlap arithmetic never depend on which corner was typed first. Y is absent because a plot is
  a 2-D column extent, matching `PlotArea`.
- Price is stored as a `double`, exactly as typed; `MmoEconBridge` owns the unit scale.
  Negative, non-finite, and out-of-range values are rejected at parse time, so an invalid sign is
  inert rather than half-parsed.
- Serialisation reuses `SignShopSyntax.formatPrice`, so a price written back through the grammar
  gets the same trailing-zero trimming as a shop sign.

**Rendering shares the shop renderer.** `SignRendererShopMixin` keeps its single redirect on
`SignText.getRenderMessages`, tries the shop grammar first, then the plot grammar, and lets
vanilla perform all layout. `plotRows` uses the same `rainbow(...)` and `price(...)` helpers:
row 0 empty, row 1 rainbow `Plot`, row 2 white `WxL` size, row 3 gold→amber bold `$` + price. The hue
seed derives from the plot bounds plus `Double.hashCode(price)`, so colour is stable per sign and
does not flicker frame to frame.

**Permissions.** Shop and plot listings use NeoForge nodes (LuckPerms ids
`xenopixelsmod.<path>`). `shop.use` / `plot.use` default to everyone and gate a right-click
purchase. `shop.edit` / `plot.edit` default to OP level 2 (and `xenopixelsmod.admin`) and are
required to create, sneak-edit, or break a finished listing. A sign-update packet that would
write `[XPSHOP]` or `[XPLOT]` without the edit node is dropped, so a player cannot turn a
plain sign into a shop or vandalize one that is already set up.

**Purchase reuses the sale path.** A plain right-click resolves the sign's tuple to a `PlotArea`
reference and calls the already-verified `PlotSale.buy`; seller and price come from the server's
own listing, so editing the sign cannot change what is charged. `PlotSignInteraction.describe` is
an exhaustive switch over `PlotSale.Result` (`SUCCESS`, `NO_ECONOMY`, `INSUFFICIENT_FUNDS`,
`TRANSFER_FAILED`, `NOT_FOR_SALE`, `NO_PLOT`, `ALREADY_OWNER`).

---

## 4. Verification status

Three items that were open in the first draft are now **resolved**: the code that settles each one
exists, compiles, and is covered by the focused test run. They are kept below with their evidence
rather than deleted, so a later reader can see what was checked. Everything after the
`Still not verified` heading remains **unknown** and must not be assumed.

### Resolved 2026-09-12

1. **Sign edit-finalize hook.** `common.SignShopUpdateMixin` now exists and
   is registered in `xenopixelsmod.mixins.json` (line 40), so the serverbound sign-update path is
   the chosen injection point. The mixin fires on finalize and re-parses the sign's text into the
   shop registry. Whether the hook also fires on a *programmatic* server-side text set (as opposed
   to a player edit) is still unverified — **check:** set sign text from a command or plugin in a
   dev run and confirm the shop registers.

### Still not verified

1. **Sign line-length enforcement in 1.21.1.** Whether a *server-set* text component is capped at
   the client-typing limit is unknown. **Check:** set a long component server-side on a real
   sign in a dev run and read it back.
2. **Client render hook.** `client.SignRendererShopMixin` exists and is
   registered in `xenopixelsmod.mixins.json` (line 75), so the sign renderer is the confirmed
   target. The rainbow/gradient render is deterministic: hue is derived from
   `targetId.hashCode() * 31 + Double.hashCode(price)`, so it is stable per sign and does not
   flicker frame to frame. The remaining unknown is cosmetic — how the re-rendered text interacts
   with a dye or glowing-text sign in a dev run.
3. **`BuiltInRegistries` accessors.** `BuiltInRegistries.ITEM` and
   `BuiltInRegistries.BLOCK` are confirmed by existing tree usage
   (`compat/thruster/ExternalThrusterCompat`, `block/custom/CopycatGlowstoneBlock`) and by
   `compileJava` of `shop/SignShopTarget`. `Block#asItem()` and `Items.AIR` as the
   "no item form" sentinel are likewise compiled. `Registry.containsKey` is the guard used
   before every lookup. Runtime behaviour of `asItem()` on technical blocks is still
   unverified in game. **Check:** a dev run reading a sign targeting a no-item block.
4. **`SessionManager` accessor.** `WorldEdit.getSessionManager()` is verified, but the method that
   returns a `LocalSession` for a player (name, and whether it takes a configuration or a player)
   was **not** inspected. **Check:** `javap com.sk89q.worldedit.session.SessionManager`.
5. **How WorldEdit receives NeoForge input.** The `PlayerInputEvent` type is verified; whether the
   NeoForge platform actually fires it on a wand click in this build is not. **Check:** a dev run
   with WorldEdit present, logging the event.
6. **MMO Econ persistence and load order.** `getBalances()` returns a `Map`, but when balances are
   loaded/saved relative to `ServerAboutToStart` is unknown. **Check:** read MMO Econ's own
   `Config` / lifecycle behaviour in a dev run.
7. **Whether MMO Econ's API is stable.** It is a third-party mod with no documented API contract.
   The signatures above are true for `1.1.0` only. **Check:** re-run `javap` on any version bump.
8. **Plot sign render and update behaviour.** The `[XPLOT]` grammar and its render entry point
   compile and pass unit tests, but no dev run has exercised them. Whether the client render hook
   cooperates with a dyed or glowing-text plot sign, and whether the server-side update hook fires
   on a *programmatic* text set (as opposed to a player edit), are **unverified** for plot signs
   exactly as for shop signs. **Check:** a dev run placing a `[XPLOT]` sign, reading it back, and
   setting its text from a command.
9. **Rent/lease settlement and the region index have no dev-run evidence.** `PlotLease`,
   `PlotLeaseTicker`, `PlotRegionIndex` and the `/plot rent|unrent|lease` verbs compile, and the
   lease's pure decision table and the index's key encoding are unit-tested, but no running server
   has charged a period or reported a drifted region. **Check:** a dev run that rents a plot, lets
   a period come due, and confirms both the debit and the
   `never-synced` / `present` / `absent (drift)` transitions.

---

## 5. Design consequences

- **MMO Econ is `runtimeOnly`, never `implementation`.** It is server-side only and must never
  become a compile or required dependency. `MmoEconBridge` is reflective and gated.
- **WorldEdit is `compileOnly` + soft runtime.** `WorldEditBridge` is gated the same way.
- **Both bridges degrade.** With either mod absent the mod loads and the feature reports
  "unavailable" rather than throwing.
- **No new `ModNetwork` packets.** The sign's vanilla block-entity text is the transport, and
  MMO Econ state is read on the server only. Main channel protocol `63` stays stable.
- **Y is discarded for plots.** `Region.getMinimumPoint()/getMaximumPoint()` supply X/Z; Y is
  read and dropped so a plot is a 2-D column extent.
- **Item targets accept items *and* blocks.** Resolution is `ITEM` → `BLOCK` (via `Block.asItem()`)
  → unresolved, so both `minecraft:stone` and `xenopixelsmod:wing_panel` work. The unresolved
  case is detected with `Registry.containsKey(ResourceLocation)` before any lookup.
- **Plots mirror into exactly one YAWP region.** `PlotFlags` is authoritative for the five
  behaviours it names (`ALLOW_BUILD`, `ALLOW_CONTAINERS`, `ALLOW_INTERACT`, `ALLOW_PVP`,
  `ALLOW_ENTRY`); YAWP is the block-protection backstop and can only be *more* restrictive, never
  less. `PlotFlags` is not mirrored into YAWP flags. `PlotArea` stays authoritative for ownership,
  flags and sale state. They are reconciled only at the three mutation points — claim, release and
  sale — never derived from each other at read time. With YAWP absent the mirror is a no-op and
  plots still claim and sell, just unprotected.
- **A sale settles money before ownership.** `PlotSale.buy` calls
  `MmoEconBridge.transfer(buyer, seller, price)` and reassigns the plot only when the transfer
  reports success, so a plot is never handed over without payment. With MMO Econ absent the sale
  fails closed with `NO_ECONOMY`, matching `SignShopPurchase.Result`.
- **The region name is deterministic and alphanumeric.** `xenoplot_<ns>_<path>_<minX>_<minZ>`,
  with a negative coordinate written as a leading `m` (`-5` → `m5`). Determinism means a restart
  re-matches the existing region instead of creating a second one; the encoding keeps a name that
  is also typed into commands free of punctuation.
- **`PlotFlags` bits are persisted and never renumbered.** `ALLOW_BUILD`, `ALLOW_CONTAINERS` and
  `ALLOW_INTERACT` are bits 0-2; `ALLOW_PVP` (1<<3) and `ALLOW_ENTRY` (1<<4) were appended, not
  inserted.
- **The `/plot` command tree uses no YAWP types.** It registers whether or not YAWP is installed,
  mirroring `KiRegionFlagCommands`' structure.
- **The syntax layer stores a decimal, the bridge converts.** `SignShopData.price` is a `double`,
  exactly as typed on the sign, because MMO Econ owns the unit scale through
  `Money.fromDouble(double)`. `MmoEconBridge.toUnits(double)` performs that conversion at the
  boundary, and `MmoEconBridge.format(long)` renders with MMO Econ's own formatter. The grammar
  never assumes the unit, so a changed MMO Econ scale cannot silently reinterpret old signs.
</description>