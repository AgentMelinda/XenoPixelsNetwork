# Create + VS2: Heavy-Player Optimization (Gameplay-Safe)

Design for a **public server** running **Create** with **Valkyrien Skies 2** and **30+ concurrent players**.  
Goals: keep factories, trains, and ship-mounted Create fun; stop idle bases and giant contraptions from melting TPS; stay compatible with VS2 (ships, Clockwork-class addons optional).

Related: `docs/vs2-optimization-and-missiles.md`, `wiki/Advanced-01-Build-and-VS2.md`.

**Version note (1.20.1 public stack):** pin Create and VS2 to a known-good pair from the [VS compatibility list](https://wiki.valkyrienskies.org/wiki/Compatibility_list) (Create 6.0.7+ / VS ~2.3–2.4 range). Clockwork / Create Interactive are **not** required for base Create+VS2; only add them if tested on your exact versions.

---

## 1. Why Create hurts at high player counts

| Cost driver | Why it scales badly |
|-------------|---------------------|
| **Kinetic networks** | Every connected shaft/cog/belt is part of a graph that revalidates on change and ticks stress/speed |
| **Contraptions** | Moving structures (bearings, elevators, trains, gantries) are entities + block transforms every tick |
| **Block entities** | Depots, funnels, basins, crushers, fans, encased fans, etc. tick even when “idle” |
| **Item / fluid pipelines** | Belt segments + funnel scans; long lines = lots of entity item hops |
| **Trains** | Track graphs + schedule AI + multi-carriage contraptions |
| **On VS ships** | Create kinetics **×** ship physics: transform frames, reparenting, extra collision |

Gameplay we **must not break**:

- Building and powering factories while the owner is online nearby  
- Trains that run useful routes  
- Ship thrusters / ship-mounted Create (Clockwork or pure kinetic on hull) when **piloted or in combat**  
- Crafting, processing, and storage while the player is interacting  

We **may** throttle or sleep:

- Far-away bases with no players  
- Infinite AFK farms nobody is near  
- Unpiloted ship factories spinning forever in loaded chunks  

---

## 2. Layered optimization (same spirit as VS2 Hot/Warm/Cold)

### A. Chunk / distance tiers for Create

| Tier | Condition | Create behavior |
|------|-----------|-----------------|
| **Hot** | Player within R₁ of network AABB **or** open GUI / seat / train passenger | Full kinetic tick, full contraption tick |
| **Warm** | Player within R₂ | Full kinetics **or** every-2nd-tick for pure process machines (optional) |
| **Cold** | Chunk loaded, no players in R₂ for T seconds | **Sleep network**: freeze speed at 0 display, stop processing, keep block state |
| **Frozen** | Chunk unloaded | Natural — nothing ticks |

Suggested: `R1=64`, `R2=128`, `T=30s`.  
**Wake rules (feel instant):**

- Player enters R₁  
- Redstone pulse / lever used by a player  
- Train schedule station request that has a waiting passenger nearby  
- Ship becomes VS **Hot** (see VS2 doc) → all Create on that ship goes Hot  

This preserves gameplay: walk up → factory spins; leave → sleeps. No deleted builds.

### B. Contraption budget (global + per player)

```text
create.maxMovingContraptionsGlobal = 48
create.maxMovingContraptionsPerPlayer = 4
create.maxContraptionBlocks = 2048          # soft warn; hard optional
create.maxTrainCarriagesPerTrain = 12
create.maxActiveTrainsGlobal = 16
create.sleepIdleContraptionTicks = 100      # ~5s no passenger / no load
```

When over budget:

1. Prefer sleep **idle** bearings/elevators with no passengers  
2. Prefer freeze **unowned** AFK farms (no online owner within R₂)  
3. **Never** stop a train a player is riding or a ship a player is piloting  

### C. Kinetic network sleep (biggest Create win)

Idle factory pattern that looks fine in-game:

1. If network RPM is stable and **no item/fluid moved for N ticks** and **no player nearby** → mark network **asleep**.  
2. Asleep: skip stress/speed propagation and machine BE process ticks.  
3. On any inventory interaction, redstone, or player enter range → **wake** and recompute network once.

Optional public-server rule: **water wheels / windmills** far from players produce power only when Warm/Hot (document as “weather engines idle when you’re away” — still fair PvE).

### D. Belts, funnels, and item entities

| Rule | Reason |
|------|--------|
| Cap **item entities** on belts per chunk | Prevents lag machines |
| Prefer **depot + chute / pipe** over 100+ belt loops for bulk | Fewer entities |
| Funnel scan rate: full rate Hot; Warm every 2 ticks | Saves BE time |
| Disable **infinite** crushing wheels on public if abused | Ops config |

Gameplay-safe messaging: “bulk logistics: use trains/vaults; belt spam is rate-limited.”

### E. Trains (keep routes, cut empty traffic)

- **Schedule trains** only tick navigation fully if: player passenger **or** cargo changed recently **or** near station with player.  
- Empty trains on long loops: reduce path repath frequency when no player in R₂ of the route.  
- Cap concurrent assembled trains; queue spawns rather than deny craft permanently.  
- Track graph: rebuild async / throttled after large edits (avoid full recompute every block place in creative spam).

### F. Fans, encased fans, bulk processing

- Processing fans: only process entities in Hot range.  
- Encased fan air columns: skip particle-heavy client FX beyond distance LOD (client config).  
- Server: don’t re-scan full column every tick if empty for N ticks.

### G. Rendering / network (client + server)

- Contraption entity tracking range: don’t send far contraptions to all 30 clients.  
- Goggle overlay / ponder: client-only; never server cost.  
- Ship-mounted Create: pose updates follow VS2 Hot/Warm rates (no double full-rate sync).

---

## 3. VS2 compatibility rules (do not break ships)

### 3.1 Authority split

| Domain | Owner |
|--------|--------|
| Ship rigid body, mass, assemble/disassemble | **VS2** |
| Kinetic speed, stress, processing, contraptions **on ship blocks** | **Create** (in ship space) |
| World↔ship transforms for entities on deck | **VS2** |

Optimization must **not**:

- Zero Create kinetics on a ship while a pilot is active (breaks thrusters / Clockwork feel)  
- Sleep VS ship while Create is still applying forces that expect a live body  
- Run Create contraptions as world-space entities when they should be ship-parented (desync, dupes)

### 3.2 Coupled sleep (Create × VS)

When a ship goes **Cold** (VS2 doc):

1. Freeze ship velocity (VS)  
2. **Also** sleep Create networks whose assembly root is that ship  
3. On pilot seat / helm / ship damage → VS Hot **and** Create Hot together  

When a player stands on a Cold ship factory:

- Wake **Create first** (so machines work)  
- If they start engines that apply force, promote ship to Hot  

### 3.3 Contraptions vs ships

| Pattern | Prefer | Avoid |
|---------|--------|--------|
| Moving part of a hull that flies | VS ship assemble | Create bearing “flying machine” + VS double physics |
| Door / crane on ship | Small Create contraption **relative to ship** | Detach into world contraption mid-flight |
| Missile / ammo | XenoPixels **entity** missile (VS2 doc) | Create contraption as warhead every shot |
| Train on land | Create train | Assembling entire train as VS ship |

**Rule of thumb:** one physics owner per moving object. Create moves relative; VS moves the hull.

### 3.4 Clockwork / Eureka / Interactive (optional addons)

- Only enable if version matrix is green on your Create+VS pin.  
- Thruster force: cap max force per ship and global thruster count (same budget idea as missiles).  
- If addon missing: pure Create on ships still works for non-physics machines (mills, crushers on deck).

### 3.5 Multiblock / BE caching on ships

- Never scan entire ship for Create BEs every tick.  
- Cache kinetic graph nodes on assemble / block update / ship load.  
- Invalidate cache on place/break only in neighborhood (Create already does graphs — don’t add O(n) full ship scans).

---

## 4. Public server policy (ops, not code)

| Policy | Effect |
|--------|--------|
| Claim / plot build limits for kinetic block count | Stops mega-lag bases |
| AFK farm dimension or “process only when online” | Fair + cheap |
| Creative world for testing 5k-block contraptions | Keeps survival TPS clean |
| Schematicannon / printer cooldowns | Stops assemble spam lag spikes |
| Clear invalid contraption entities on restart | Fixes “invalid carriage” leftovers |

Suggested soft warnings (action bar / mail):

- “Your kinetic network is very large (N blocks). Consider splitting factories.”  
- “Ship has Create network asleep (no pilot nearby).”  

---

## 5. Config surface (XenoPixels or server pack)

Optional central config so ops tune without rebuilding Create:

```text
# Create performance (server)
create.hotRange = 64
create.warmRange = 128
create.idleSleepSeconds = 30
create.maxContraptionsGlobal = 48
create.maxContraptionsPerPlayer = 4
create.maxContraptionBlocks = 2048
create.maxActiveTrains = 16
create.sleepFarNetworks = true
create.coupledSleepWithVs2Ships = true

# VS2 coupled (see vs2-optimization-and-missiles.md)
vs2.hotRange = 96
vs2.warmRange = 192
vs2.coupledCreateSleep = true
```

Implementation options later:

1. **Server pack only** — Create / VS config TOMLs + Spark reports (no mod code).  
2. **XenoPixels thin hooks** — distance sleep + budgets if APIs/mixins allow.  
3. **Dedicated perf addon** — only if Create/VS APIs are insufficient.

Do **not** ship aggressive mixins that break Create’s kinetic math (desync, dupe stress, wrong recipes).

---

## 6. What to measure (prove it works)

| Metric | Target (30 players, mixed play) |
|--------|----------------------------------|
| MSPT average | &lt; 40 ms |
| MSPT spike assemble | &lt; 100 ms rare |
| Active Create networks Hot | &lt; ~20 simultaneous |
| Moving contraptions | under global cap |
| VS active ships Hot | under `maxActiveShipsGlobal` |
| Entity count (items on belts) | watch chunk hotspots |

Tools: Spark profiler, Carpet `/tick health`, VS debug ship count, Create ponder off on server.

---

## 7. Implementation phases

| Phase | Work | Gameplay risk | Perf gain |
|-------|------|---------------|-----------|
| **C0** | Pin Create+VS versions; document ops TOML; Spark baseline | None | Medium (ops) |
| **C1** | Coupled sleep: VS Cold ⇒ Create sleep on ship; pilot wake | Low | High |
| **C2** | Distance sleep for land factories (Hot/Warm/Cold) | Low if wake is instant | High |
| **C3** | Contraption / train global budgets | Low if riding always Hot | Medium–High |
| **C4** | Belt/item entity caps + funnel throttle | Low–medium (msg players) | Medium |
| **C5** | Missile entity path (no Create warheads) | None | Protects VS+Create |

### LIVE in XenoPixels (P0 / C0–C1 soft)

| Piece | Code | Notes |
|-------|------|--------|
| Perf config | `config/xenopixelsmod-perf.json` + `XenoPerfConfig` | hot/warm ranges, ship cap, MSPT, Create budgets |
| VS2 ship sleep | `Vs2ShipSleepManager` | `ServerShip.setStatic` when far+slow; wake on player/combat |
| MSPT watchdog | `MsptWatchdog` | shrinks hot range under stress |
| Create soft budget | `CreatePerfHooks` | if Create loaded: freeze farthest excess contraption entities |
| Commands | `/xenoperf status\|reload\|set` | op level 2 |

Does **not** yet sleep Create kinetic graphs on land (needs deeper Create API/mixin). Ship-mounted Create benefits immediately from VS `setStatic` Cold.

---

## 8. Anti-patterns (hurt gameplay or break VS2)

| Don’t | Why |
|-------|-----|
| Global “Create tick every 5 ticks” always | Factories feel broken; recipes desync |
| Disable collisions on ships to save FPS | Breaks boarding / Create on deck |
| Convert all Create contraptions to VS ships | Double physics, 30-player death |
| Unload chunks under players on trains | Train void / desync |
| Hard-delete oversized builds | Trust / gameplay loss — sleep + warn instead |
| Force Clockwork if versions mismatch | Crashes &gt; lag |

---

## 9. Tie-in with XenoPixels combat / missiles

- **Super Souls / Sparking effects** — inventory only; no Create/VS cost.  
- **Missiles** — entity path from VS2 doc; do not fire Create contraption “missiles” in PvP.  
- **Ballistic computer on ship** — plain BE; may use Create rotation as optional “power required” later without full kinetic graph dependency.

---

## 10. Open questions

1. Land factories: sleep after 30s away, or only when chunk would unload?  
2. Are water-wheel AFK farms allowed if player is offline but chunk stays loaded (claims)?  
3. Train cargo lines: always Warm, or Cold empty loops?  
4. Will we depend on Clockwork for thrusters, or pure VS Eureka-style ships + Create cosmetics only?

---

*Design for Create + VS2 multiplayer. Prefer config and coupled sleep before invasive mixins. Cross-check any code change against a Create factory on a moving VS ship before shipping to public.*
