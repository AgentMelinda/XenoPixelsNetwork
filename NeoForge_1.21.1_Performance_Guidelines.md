# NeoForge 1.21.1 Performance & Efficiency Guidelines

**Target environment**

- Minecraft: `1.21.1`
- Mod loader: NeoForge
- Example NeoForge version: `21.1.x` (e.g. 21.1.248)
- Java: `21`

This document is a practical ruleset for writing Minecraft mods that stay efficient, stable, and performance-friendly on both:

- **Client** — FPS, frame pacing, memory, rendering, GUI/HUD smoothness
- **Server** — TPS, tick stability (MSPT), memory, chunk/entity/block-entity load

**Priority order**

```text
Correctness → Server stability → Compatibility → Measurable performance → Maintainability → Micro-optimization
```

---

## 1. Core Philosophy

The best performance strategy is simple:

1. **Do less work.**
2. **Delay work until it is necessary.**
3. **Cache work that repeats.**
4. **Avoid allocations in hot paths.**
5. **Use the correct logical side.**
6. **Prefer events, registries, capabilities, attachments, and data-driven systems over Mixins.**
7. **Use Mixins only when there is no safe alternative.**
8. **Profile before and after optimizing.**

A mod should never assume it owns the entire tick or frame budget.

**Fundamental cost formula**

```text
PERFORMANCE COST = WORK PER UPDATE × UPDATE FREQUENCY × ACTIVE OBJECT COUNT
```

Reduce any of those three factors and the mod becomes more scalable.

---

## 2. Performance Budgets

### Server / TPS

Minecraft targets:

```text
20 TPS = 50 ms per tick
```

Healthy targets for a modpack/server:

| MSPT          | Meaning                          |
|---------------|----------------------------------|
| < 25 ms       | Excellent headroom               |
| 25–40 ms      | Healthy under normal load        |
| 40–50 ms      | Danger zone; spikes cause lag    |
| > 50 ms       | Cannot sustain 20 TPS            |
| ~100 ms       | Roughly 10 TPS if sustained      |

Do **not** judge performance by TPS alone. A server can still report 20 TPS while suffering 100–500 ms spikes. Always inspect **MSPT, percentiles, and profiles**.

If your mod regularly adds several milliseconds per tick, it will break large servers.

### Client / FPS

Frame budget depends on target FPS:

```text
60 FPS  ≈ 16.6 ms per frame
120 FPS ≈  8.3 ms per frame
144 FPS ≈  6.9 ms per frame
```

Also track:

- 1% low FPS
- Frame-time spikes
- Chunk rebuild time
- Particle / entity render cost
- GUI / HUD cost
- VRAM usage
- GC pauses

A stable 90 FPS is usually better than 180 FPS with constant frame-time spikes.

Client-side code must avoid:

- Per-frame allocations
- Heavy text formatting
- Heavy entity/world queries
- Unnecessary render injections
- Excessive GUI redraw calculations

---

## 3. Project Structure for Performance

A clean structure prevents accidental side leaks and performance mistakes.

Recommended layout:

```text
com.example.examplemod
├── ExampleMod.java
├── init
│   ├── ModItems.java
│   ├── ModBlocks.java
│   ├── ModBlockEntities.java
│   ├── ModEntityTypes.java
│   ├── ModMenuTypes.java
│   ├── ModCreativeTabs.java
│   ├── ModDataComponents.java
│   └── ModNetwork.java
├── block
├── item
├── entity
├── menu
├── network
├── capability
├── component
├── data
├── event
├── client
│   ├── render
│   ├── screen
│   ├── model
│   └── overlay
├── server          (optional managers)
└── mixin
    ├── client
    └── common
```

**Rules**

- Keep all client-only code under `client`.
- Keep Mixins under `mixin` (split client/common if needed).
- Keep registration in `init`.
- Keep gameplay logic out of the mod constructor.
- Avoid large logic directly inside event classes; dispatch to managers.

---

## 4. Golden Rules for Server Performance

### Rule 1 — Do not run logic every tick unless required

Ask every time:

- Does this really need to run every tick?
- Can it run every 5 / 10 / 20 / 100 ticks?
- Can it run only when something changes?
- Can it be scheduled?
- Can it be event-driven?

Prefer this order:

```text
EVENT-DRIVEN
    ↓
SCHEDULED / INTERVAL UPDATE
    ↓
DIRTY-FLAG UPDATE
    ↓
BATCHED / BUDGETED TICKING
    ↓
EVERY-TICK LOGIC   ← last resort
```

### Rule 2 — Prefer scheduled ticks over constant tickers

For blocks, prefer:

- `Level#scheduleTick`
- Block-entity ticker only when truly needed
- Event-driven or neighbor/redstone updates

Avoid:

- Ticking a block entity every tick when it only needs to act every few seconds
- Polling the world for changes
- Repeated large-area scans

### Rule 3 — Avoid scanning the world every tick

Expensive operations:

- `level.getBlockState(...)`
- `level.getBlockEntity(...)`
- `level.getEntitiesOfClass(...)` / `level.getEntities(...)`
- Large `AABB` queries
- Large `BlockPos` loops
- Chunk lookups

If you must scan:

- Limit the area
- Throttle the scan
- Cache results and invalidate correctly
- Use `BlockPos.MutableBlockPos`
- Skip unloaded chunks

```java
BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

for (int x = minX; x <= maxX; x++) {
    for (int y = minY; y <= maxY; y++) {
        for (int z = minZ; z <= maxZ; z++) {
            cursor.set(x, y, z);
            // use cursor — do not allocate a new BlockPos each iteration
        }
    }
}
```

### Rule 4 — Avoid allocations in hot paths

Hot paths include:

- Server tick / Level tick
- Entity tick / Block-entity tick
- Chunk loading
- Networking handlers
- Rendering / GUI / HUD / Tooltip construction

Avoid creating in those paths:

- New lists / maps
- New strings / components
- New `BlockPos` / `AABB` / `ResourceLocation`
- New random instances
- Streams

Reuse or cache where possible.

### Rule 5 — Cache expensive values, invalidate correctly

Good cache targets:

- Config values
- Tag lookups
- Registry objects
- Parsed data / recipes
- Frequently used `ResourceLocation` or text components

**Invalidation triggers**

- Server start/stop
- Level unload
- Data-pack / tag / config reload
- Block-entity / entity removal
- Player logout

**Never** keep static references to:

- `Level` / `ServerLevel`
- `Player` / `Entity`
- `BlockEntity` / `ChunkAccess`

unless they are cleaned up on the correct lifecycle events.

### Rule 6 — Do not touch unloaded chunks

```java
if (!level.isLoaded(pos)) {
    return;
}
```

Avoid:

- Accidentally forcing chunks to load
- Accessing block entities across unloaded chunks
- Running large operations across chunk borders
- Triggering chunk generation from normal mod logic

### Rule 7 — Use the correct side

Always check:

```java
level.isClientSide
```

- Server logic must not run on the client
- Client logic must not run on a dedicated server
- Do not mutate authoritative server state from client code
- Separate client and server event handlers
- Use `Dist.CLIENT` / `Dist.DEDICATED_SERVER` carefully

---

## 5. Registration & Startup

### Use DeferredRegister

```java
DeferredRegister.create(Registries.ITEM, MODID);
DeferredRegister.create(Registries.BLOCK, MODID);
DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
// etc.
```

Rules:

- Register only during the proper registration phases
- Do not modify registries after they are frozen
- Do not call `.get()` on suppliers too early
- Avoid heavy logic or IO in static initializers or the mod constructor

### Keep the mod constructor lightweight

The constructor should mainly:

- Create deferred registers
- Register event listeners / setup listeners

Move heavy work to lifecycle events.

---

## 6. Event Performance

### Use the correct bus

- **Mod bus** — registration & lifecycle events
- **Game bus** — gameplay / world / player events

Do not mix them.

### Avoid high-frequency event abuse

Events that fire every tick, every frame, every entity tick, or every render pass are dangerous.

If you subscribe to one:

- Return early when inactive
- Throttle work
- Avoid allocations and expensive queries
- Prefer a more specific event or a scheduler when possible

Simple throttle pattern (prefer per-object or per-level counters in real code):

```java
private int tickCounter = 0;

@SubscribeEvent
public void onServerTick(ServerTickEvent.Post event) {
    if (++tickCounter % 20 != 0) {
        return;
    }
    // work once per second
}
```

### Keep event handlers cheap

Handlers should:

- Check conditions quickly
- Mark dirty flags or schedule work
- Modify minimal state

Avoid large loops, world scans, reflection, config-file reading, string formatting, logging, or stream pipelines inside the handler itself.

---

## 7. Block Entity Performance

Block entities are one of the most common sources of TPS problems.

### Do not give every block entity a ticker

If the block entity does not need to tick, do **not** provide a ticker.

Prefer:

- Scheduled ticks
- Redstone / neighbor signals
- Capability interaction
- On-demand processing

When a ticker is required:

- Keep it tiny
- Avoid allocations
- Throttle where possible
- Do not scan large areas every tick
- Call `setChanged()` **only** when data actually changed

### Sync only required data

- Send minimal data
- Avoid full NBT when only one value changed
- Do not sync client-only caches or transient values
- Trigger client updates only when the visual state changes

### VoxelShapes

- Cache `VoxelShape` instances
- Prefer simple shapes
- Avoid creating shapes on every call
- Test pathfinding around complex shapes

---

## 8. Entity Performance

### Avoid logic that touches every entity

Ask:

- Does it affect all entities or only specific types?
- Can it run only when needed / on an event?
- Can it be delayed?

Avoid:

- Checking all entities every tick
- Expensive AI on many entities
- AABB searches every tick
- Reflection during entity tick

### AI goals

- Keep `canUse()` cheap
- Avoid expensive world scans in goal checks
- Use sensible goal tick rates
- Do not recalculate pathfinding every tick unless required

### SynchedEntityData

Use only for small synchronized state (ints, booleans, floats, optional IDs).

Do **not** put large inventories or complex structures into it. Update only when the value actually changes.

---

## 9. World, Chunk & Block Updates

### Be careful with `setBlock`

Block updates can trigger neighbor updates, redstone, observers, client sync, re-rendering, pathfinding invalidation, and block-entity changes.

Use the correct update flags. Avoid unnecessary neighbor updates.

### Avoid cascading updates

Dangerous patterns:

- Updating many blocks at once
- Recursive block changes
- Frequent redstone toggling
- Large structure changes during active gameplay

When modifying many blocks:

- Batch changes
- Limit work per tick
- Use queues
- Never update unloaded chunks

### Never force-load chunks casually

If a feature does not explicitly require a chunk to be loaded, do not load it just to inspect it.

---

## 10. Memory & Leak Prevention

### Avoid static maps that hold world objects

Bad:

```java
static Map<UUID, PlayerData> playerData = new HashMap<>();
static Map<BlockPos, BlockEntity> machines = new HashMap<>();
```

If you must use static caches:

- Remove entries on logout / unload
- Clear on server stop / level unload
- Prefer weak references only with extreme care
- Prefer NeoForge capabilities, attachments, `SavedData`, or the object’s own storage

### Clean up on unload

Listen for the appropriate unload / logout events and remove cached entries for:

- Players
- Levels
- Chunks
- Block entities
- Worldgen / structure caches
- Data-pack caches

---

## 11. Items, ItemStacks & Data Components (1.21)

Minecraft 1.21 uses data components heavily.

### Prefer data components over ad-hoc NBT

```java
DataComponentType
```

Rules:

- Prefer immutable component values
- Use codecs correctly
- Register component types once; never create them dynamically
- Avoid storing huge data structures in item components
- Avoid updating components every tick

### Avoid unnecessary ItemStack mutation

- Do not mutate every stack in an inventory each tick
- Avoid repeated copying
- Prefer dirty flags and minimal mutations
- Use `stack.is(item)` when only the item type matters

---

## 12. Capabilities & Attachments

Use NeoForge systems instead of inventing global static storage.

### Capabilities

- Expose only what is necessary
- Cache handlers when safe; invalidate when the provider changes
- Avoid querying capabilities in tight loops
- Keep capability providers lightweight
- Register via the proper NeoForge events

### Attachments

- Store only persistent or necessary data
- Keep data small
- Do not save transient cache values
- Serialize efficiently

---

## 13. Networking Performance

### Send less data

- Send only what changed
- Send only to players who need it
- Do not broadcast globally unless required
- Do not send full state every tick
- Avoid large NBT / component blobs
- Never send client-only data to the server or server-only data to the client

### Custom payloads (NeoForge 1.21)

- Use `CustomPacketPayload` + `StreamCodec`
- Keep payloads small
- Use varints where appropriate
- Register handlers correctly
- Validate all client-provided values and bounds
- Ensure world mutations run on the server thread

### GUI / menu syncing

- Prefer vanilla container syncing and data slots
- Avoid custom packets for values vanilla can already sync
- Sync only changed values

### Client prediction & interpolation

For HUDs and smooth visuals, let the client interpolate between authoritative server values instead of sending updates every frame.

---

## 14. Client FPS Performance

Client performance includes HUD, GUI, models, textures, particles, sounds, entity/block-entity renderers, shaders, text, and input.

### Avoid per-frame allocations

In render / GUI code avoid:

- New strings / components / lists
- New matrices when reuse is possible
- New `ResourceLocation` or random instances
- Heavy formatting or streams

Cache text components, texture locations, widget layouts, and tooltip data where safe.

### GUI rules

- Rebuild widgets only when data changes
- Cache text
- Avoid expensive queries inside `render`
- Avoid sorting large lists every frame
- Use scissor / clipping appropriately

### HUD / overlay rules

- Render only when necessary
- Hide when irrelevant
- Cache text and icons
- Never query all entities or large world data every frame
- Respect debug screens and GUI scale

### Rendering rules

- Use correct render types
- Avoid excessive translucent geometry
- Cache baked quads
- Prefer blockstate / model JSON over runtime generation
- Avoid binding textures repeatedly

### Particles & sounds

- Limit emission
- Respect distance and particle settings
- Throttle repeated sounds
- Never run client particle logic on a dedicated server

---

## 15. Assets & Resource Packs

### Textures

- Prefer reasonable sizes
- Compress
- Avoid unnecessary animation and large transparent areas
- Prefer atlas-friendly designs

### Models

- Keep JSON models simple
- Avoid excessive cube counts
- Prefer blockstate variants over runtime model hacks
- Cache custom baked model data

### Language / text

- Cache commonly used `Component` values
- Avoid constructing translated strings every frame

---

## 16. Data-Driven Design

Prefer data-driven systems over hardcoded behavior:

- Recipes, advancements, loot tables, tags
- Worldgen / biome modifiers / structures where applicable
- Predicate-based behavior
- JSON configuration where appropriate

Benefits: better compatibility, fewer Mixins, easier datapack support, better maintainability.

### Recipes & tags

- Prefer data-driven recipes
- Cache matching results when safe
- Avoid scanning all recipes every tick
- Cache tag lookups and invalidate on reload
- Never use string-based tag lookups in hot paths

---

## 17. Config, Logging & Debugging

### Config

- Use NeoForge `ModConfigSpec` (or equivalent)
- Cache values; never read files every tick
- Refresh on config load / reload

### Logging

Never log in hot paths at INFO level:

```java
// BAD
LOGGER.info("Ticking machine at {}", pos);
```

Prefer:

- Debug level
- Guarded expensive message construction
- Rate-limiting
- Periodic summary counters

### Exceptions

Do not throw exceptions as normal control flow in hot paths. A rare error is fine; thousands of exceptions per tick are not.

---

## 18. Correct Approach to Mixins (NeoForge 1.21.1)

Mixins are powerful but should be a **last resort**. Most performance and compatibility problems come from using Mixins where events, capabilities, data components, registries, or Access Transformers would be better.

### Mixin Decision Tree

Before writing a Mixin, ask in this order:

1. **Does a NeoForge event already exist?**  
   → Use the event.

2. **Is the problem only accessing a private field/method?**  
   → Prefer Access Transformers.

3. **Can the data be stored with capabilities / attachments / data components?**  
   → Use those systems.

4. **Can the behavior be achieved with data-driven systems (tags, recipes, loot, predicates, biome modifiers, etc.)?**  
   → Use data.

5. **Is there truly no supported hook?**  
   → Consider a minimal, well-documented Mixin.

### Preferred order of solutions

```text
1. Vanilla / public API
2. NeoForge event
3. Loader extension point / interface
4. Access Transformer (when access is the only problem)
5. Small targeted @Inject / @ModifyVariable / @ModifyArg
6. @Redirect (only when replacement is required)
7. @Overwrite (last resort, heavily documented)
```

### Mixin Performance Facts

- Mixins increase **startup** cost (classloading / patching).
- Runtime cost comes almost entirely from **what the injected code does and where it runs**.
- An injection into a method called once at startup is cheap.
- An injection into entity tick, block-entity tick, render loop, collision, or packet handling can become a major problem.

### Mixin Rules

**Rule 1 — Least invasive injector**

Prefer Access Transformer → Event → `@ModifyVariable` / `@ModifyArg` → `@Inject` → `@Redirect` → `@Overwrite`.

**Rule 2 — Avoid hot paths**

Do not Mixin into render methods, game/level renderer, main game loop, entity/level/server tick, chunk build, packet handlers, collision, or VoxelShape methods unless there is a very strong reason. If you must:

- Return early
- Allocate nothing
- Log nothing
- Reflect nothing
- Capture locals only if unavoidable
- Keep the body tiny

**Rule 3 — Avoid `@Local` captures when possible**

Prefer `@ModifyVariable` or `@ModifyArg`.

**Rule 4 — No reflection inside Mixins**

Use Access Transformers, `@Shadow`, or proper accessor Mixins.

**Rule 5 — Use `@Unique` for added members**

```java
@Unique
private int examplemod$customCounter;
```

Prefix with your mod ID. Never store large object graphs or level/entity references in injected static fields.

**Rule 6 — Never use `@Overwrite` casually**

It replaces the entire method, breaks easily across versions, and conflicts with other mods. Use only when no alternative exists, document it thoroughly, and re-audit every Minecraft update.

**Rule 7 — Separate client and server Mixins**

```json
"client": [],
"server": [],
"mixins": []
```

Client-only Mixins must not load on a dedicated server.

**Rule 8 — Keep Mixins small and documented**

Every Mixin should document:

- Target class / method
- Reason it exists
- Minecraft / NeoForge version it was written for
- Compatibility concerns
- Side (client / server / common)

```java
/**
 * Target: SomeVanillaClass#someMethod
 * Reason: NeoForge exposes no event at this exact point.
 * Version: NeoForge 1.21.1
 * Notes: Early-return only; no allocations.
 */
```

### Good Mixin style

Keep business logic **out** of the Mixin class. The Mixin should only locate the hook and call ordinary mod code:

```java
@Inject(method = "someMethod", at = @At("HEAD"), cancellable = true)
private void examplemod$earlyReturn(CallbackInfo ci) {
    if (!ExampleModConfig.enabled) {
        return;
    }
    ModHooks.onSomeMethod(...); // real logic lives here
}
```

### Example: cheap early-return

```java
@Mixin(TargetClass.class)
public abstract class TargetClassMixin {

    @Inject(method = "someHotMethod", at = @At("HEAD"), cancellable = true)
    private void examplemod$earlyReturn(CallbackInfo ci) {
        if (!ExampleModConfig.enabled) {
            return;
        }
        // minimal logic only
    }
}
```

### Example: modify variable instead of capturing locals

```java
@ModifyVariable(method = "someMethod", at = @At("HEAD"), ordinal = 0)
private int examplemod$modifyDelay(int originalDelay) {
    return originalDelay * 2;
}
```

---

## 19. Main-Thread & Async Rules

Minecraft world state is largely main-thread sensitive.

**Never** casually mutate from another thread:

- Blocks, block entities, entities
- Player inventory / containers
- Chunk state, registries, game rules
- Capability / attachment state (unless the API explicitly guarantees safety)

**Safe async work**

- Parsing files, compression, image work
- Pure mathematical calculations on immutable copies
- Database / network requests

Pattern:

```text
MAIN THREAD
  copy minimal immutable input
        ↓
BACKGROUND THREAD
  pure calculation
        ↓
MAIN THREAD
  validate state is still applicable
  apply small result
```

Never assume the world is unchanged when the background work finishes.

---

## 20. Tick Budgeting & Managers

For large systems, introduce a work budget instead of processing everything in one tick:

```java
private static final int MAX_WORK_PER_TICK = 200;

void processQueue() {
    int processed = 0;
    while (!queue.isEmpty() && processed < MAX_WORK_PER_TICK) {
        process(queue.removeFirst());
        processed++;
    }
}
```

Prefer explicit managers that track **only active / dirty** work:

```text
ServerTick
  ├── MissileManager.tickBudgeted()
  ├── NetworkManager.tickDirtyNetworks()
  └── CombatManager.tickActiveSessions()
```

Remove entries promptly on unload / death / logout / inactivity.

---

## 21. Spatial Indexing

For systems that repeatedly query nearby custom objects, maintain a spatial index (e.g. by dimension → ChunkPos → set of IDs) instead of scanning the entire world.

Useful for radars, ships, missiles, machines, zones, sensors, etc.

---

## 22. Profiling — Never Guess

Useful tools:

- Minecraft / NeoForge debug profiler
- spark
- Java Flight Recorder (JFR)
- IDE allocation / CPU profiler

Test under realistic load (multiple players, many entities/machines, multiple dimensions, combat, chunk loading).

Record average MSPT, percentiles, worst spikes, hot methods, allocation rate, and GC pauses. Optimize the measured bottleneck, then profile again.

---

## 23. Testing Requirements

Always test:

1. `runClient`
2. Dedicated server (`runServer` or equivalent)
3. Real multiplayer (separate client + dedicated server)
4. High entity / machine counts
5. Production / reobfuscated JAR

A mod that works in single-player is not automatically server-safe. Look for `NoClassDefFoundError` on client classes, client-only event registration in common code, and static initializers that touch the client.

---

## 24. Short Ruleset for Contributors & AI Assistants

```text
DO NOT scan the world every tick.
DO NOT force-load chunks casually.
DO NOT block the server thread with I/O or HTTP.
DO NOT mutate Minecraft world state asynchronously.
DO NOT trust client packets.
DO NOT send large / full-state packets every tick.
DO NOT put simulation inside render code.
DO NOT use Mixins when an official API / event is sufficient.
DO NOT use @Overwrite unless there is no reasonable alternative.
DO NOT claim an optimization without profiling it.

DO use events and dirty flags.
DO use scheduled / interval updates.
DO process large jobs with budgets.
DO keep the server authoritative.
DO separate client-only code.
DO cache stable data with explicit invalidation.
DO use spatial indexes for scalable searches.
DO send deltas to relevant players only.
DO keep Mixins narrow and delegate to normal code.
DO test dedicated server and multiplayer.
DO profile MSPT, frame time, allocations, and spikes.
```

---

## 25. Performance Checklist Before Release

- [ ] Dedicated server starts without client-only class errors
- [ ] Server stays near 20 TPS under representative load
- [ ] Average MSPT has safe headroom below 50 ms
- [ ] No major recurring tick spikes caused by the mod
- [ ] No world-wide scans in tick handlers
- [ ] No accidental chunk forcing in normal gameplay
- [ ] Block entities perform no unnecessary idle work
- [ ] Entities do not reacquire targets / pathfind every tick without need
- [ ] Network packets are bounded and validated
- [ ] Packets are sent only when necessary
- [ ] Cosmetic effects are client-side where appropriate
- [ ] HUD / render code does not perform world scans or networking every frame
- [ ] No INFO log spam from tick / render loops
- [ ] No blocking disk / HTTP / database work on the server thread
- [ ] Async workers never mutate live world state directly
- [ ] Caches have explicit invalidation / lifecycle behavior
- [ ] Static collections do not leak players / worlds / entities
- [ ] Mixins are used only where APIs / events are insufficient
- [ ] Any `@Overwrite` has explicit justification and documentation
- [ ] Every Mixin target was re-verified for Minecraft 1.21.1
- [ ] Production / reobfuscated JAR was tested
- [ ] Performance was measured with a profiler, not guessed

---

## 26. Final Principle

The best-performing Minecraft mod is not the one with the most clever low-level tricks.

It is the one that **avoids unnecessary work**.

```text
less work
less often
on fewer objects
on the correct side
with smaller packets
with bounded algorithms
with measured evidence
```

---

*Document refined for NeoForge 1.21.1 / Java 21. Re-verify all Mixins and APIs against the exact NeoForge version you target.*
