# VS2: 30+ Player Optimization + Realistic Missile Blocks

Design notes for **Valkyrien Skies 2** on a public XenoPixels / DMZ server.  
Goal: keep ship physics fun, support **30+ concurrent players**, and add **realistic vertical / ballistic missiles** without melting the TPS.

Related: `wiki/Advanced-01-Build-and-VS2.md`, `README.md` (VS mixin notes),  
**Create multiplayer perf + VS2 coupled sleep:** `docs/create-vs2-optimization.md`.

---

## 1. Why VS2 hurts at 30+ players

VS2 cost scales with:

| Cost driver | What multiplies it |
|-------------|--------------------|
| **Active ships** | Each assembled ship is a physics body + mass properties |
| **Ship size (blocks)** | Collision / inertia / voxelization |
| **Tick rate of physics** | Every server tick (or substep) for every loaded ship |
| **Players on / near ships** | Transform world↔ship coords, mounting, chunk force-load |
| **Contraptions interacting** | Ship–ship, ship–terrain, ship–entity collisions |
| **Network** | Pose sync (pos/rot/vel) to every tracking client |

Gameplay we **must not break**: assembling ships, flying/sailing, boarding, combat between ships, sinking/destruction.

---

## 2. Optimization strategy (layered, non-destructive)

### A. Load / distance tiers (biggest win)

Do **not** run full physics for every ship in the overworld forever.

| Tier | Condition | Physics | Sync |
|------|-----------|---------|------|
| **Hot** | Player within R₁ or combat flag | Full VS2 step | Full pose @ 20 Hz |
| **Warm** | Player within R₂ | Full or ½ rate (every 2 ticks) | Pose @ 10 Hz |
| **Cold** | Loaded chunk, no players nearby | **Kinematic park**: freeze vel, zero forces, keep transform | Snapshot on approach |
| **Frozen** | Unloaded / far | No tick; persist NBT only | None |

Suggested defaults (config): `R1=96`, `R2=192` blocks (ship AABB center).  
**Combat override:** if any player damages ship or locks missile, force **Hot** for N seconds.

This preserves gameplay: when you fly over a fleet, ships “wake up”; parking lots of idle hulls stay cheap.

### B. Ship budget per player / server

Server config (ops):

```text
vs2.maxActiveShipsGlobal = 24
vs2.maxActiveShipsPerPlayer = 2
vs2.maxBlocksPerShip = 4000          # soft; warn + optional assemble reject
vs2.maxPhysicsSubsteps = 1           # never 4+ on public
vs2.sleepLinearSpeed = 0.05
vs2.sleepAngularSpeed = 0.02
```

When over budget: prefer **sleep oldest non-piloted** ship (Cold), not kick players.

### C. Sleep / assembly rules (gameplay-safe)

- Auto-sleep ship if `speed < sleep threshold` for **3s** and no pilot seat occupied.
- Pilot seat / helm block **wakes** ship immediately (feels responsive).
- Anchored / docked ships: force Cold (harbor mode).
- **Do not** delete blocks or void ships — only freeze simulation.

### D. Network (30 clients)

- Pose packets: **delta + quantization** (already partly in VS — ensure we don’t send full mesh).
- Only send ship updates to players who can see the ship (tracking range, not dimension-wide).
- Missile entities: high rate only for nearby players; far = low-rate trail or none.
- Avoid per-block particle spam on thrusters at distance LOD.

### E. Server tick scheduling

- Physics work queue: max **X ship-steps per server tick** (e.g. 8); remaining deferred one tick (smooth, no freeze).
- Stagger Cold→Warm wake-ups (not 12 ships same tick).
- Separate **missile sim** from **ship rigid body** (missiles = lightweight entities or 1-block “ammo ships” with special cheap collider).

### F. World / ops practices (public server)

- Dedicated **naval / air combat dimension** or region with ship cap.
- Build yards in claim where assemble is allowed; open ocean = flight only.
- Pre-built “class hulls” as schematics so players don’t assemble 8k-block lag machines mid-fight.
- TPS watchdog: if MSPT > 45 for 10s, force Cold on non-piloted ships and announce.

### G. What not to do

- Don’t disable collisions entirely (breaks boarding / ramming fantasy).
- Don’t reduce gravity weirdly only for some players (desync PvP).
- Don’t run client-only physics as authority (cheating / desync).

---

## 3. Missile system — block kit design

Concept: **assemble a vertical VLS / tube on a ship**, load a warhead, set guidance, fire. Ballistics feel real; implementation stays VS2-friendly.

### 3.1 Block list

| Block | Role |
|-------|------|
| **Missile Tube / Silo** | Multiblock vertical shaft (1×1×N). Detects payload stack below hatch. |
| **Guidance Computer** | BE: target mode, range, arming delay, friend-or-foe. |
| **Ballistic Computer** (your idea) | BE: simulates trajectory **before fire** (arc preview particles / client line). Placed on ship; uses ship pose + target world pos. |
| **Inertial Platform** | Optional: reduces guidance error while ship is maneuvering. |
| **Warhead Casing** | Crafted shell; accepts payload items. |
| **Payload modules** (items in casing) | HE, AP, cluster, smoke, EMP (ki-scramble soft CC), practice inert. |
| **Fuel / Booster unit** | Sets boost burn time + max Δv. |
| **Launch Console** | GUI: arm, set target (coords / lock-on entity / GPS beacon), fire. |
| **GPS Beacon** | Placeable / throwable target marker for ballistic aim point. |
| **VLS Hatch** | Animated open; prevents fire if blocked. |

### 3.2 Missile as entity (recommended) vs mini-ship

| Approach | Pros | Cons |
|----------|------|------|
| **Missile entity** (custom projectile + rigid trail) | Cheap, 50+ in flight OK, simple collision | Less “VS ship” feel |
| **1-block VS ship ammo** | Cool physics | **Expensive** at 30 players — avoid for combat spam |
| **Hybrid** | Entity for boost+coast; rare “bus” ship for capital missiles only | More code |

**Public server default: entity missiles.** Cap: `maxLiveMissilesGlobal=40`, `perPlayer=4`.

### 3.3 Ballistic trajectory (realistic vertical launch)

Phases (server authoritative):

1. **Rail / eject** (0.2–0.5s) — vertical relative to **silo axis** (ship-local up), ignore aero.
2. **Boost** — constant accel along velocity + gravity; short fuel timer from Fuel module.
3. **Coast / midcourse** — gravity + optional loft; Ballistic Computer set pitch program:
   - pure ballistic loft to impact point, or
   - loft-then-dive (terminal).
4. **Terminal guidance** (if seeker unlocked) — steer limited g’s toward target entity / beacon; no 180° magic turns.
5. **Impact / airburst** — payload effect; remove entity.

Equations (simplified, tick-based):

```text
v += g * dt + thrust_dir * a_thrust * dt
pos += v * dt
drag: v *= (1 - k_drag * dt)   // optional, altitude band
```

**Ballistic Computer block** (pre-fire):

- Input: target world pos (beacon / lock / typed coords), silo world transform from VS ship.
- Solves approximate elevation for boost+coast (binary search loft angle).
- Outputs: `pitch_program[]`, `boost_time`, `eta_ticks`, `hit_probability` (shown in GUI).
- Client: draws predicted arc in ship-local or world space (only for operator, low particle count).

### 3.4 Payload behavior (examples)

| Payload | Effect |
|---------|--------|
| HE | Explosion radius scaled by yield item; terrain damage optional flag |
| AP | Smaller radius, high damage to **ship mass / assembly** (if VS damage API) + entities in cone |
| Cluster | Spawn N submunitions on airburst height |
| Smoke | Particles + short blindness / scouter noise |
| Practice | Dye splash + score for training dummy ships |

### 3.5 Multiblock rules (anti-lag)

- Silo max height 16; one missile per tube.
- Guidance + Ballistic Computer: **one per ship assembly** (or per battery of 4 tubes).
- No per-tick block entity scans of whole ship — cache silo list on assemble / neighbor update.
- Warhead craft offline in GUI, not entity-in-block physics.

### 3.6 Integration with XenoPixels / DMZ

- Optional **Ki warhead** payload later (DMZ damage type).
- Lock-on: reuse combat lock target as GPS for Ballistic Computer.
- Super Souls / forms **do not** change physics; only payload damage mult if desired (server flag).

---

## 4. Implementation phases (suggested)

| Phase | Deliverable | Ship load impact |
|-------|-------------|------------------|
| **P0** | Config: ship sleep radii, global active ship cap, MSPT watchdog | **LIVE** — `XenoPerfConfig` + `Vs2ShipSleepManager` + `/xenoperf` |
| **P1** | Missile entity + silo BE + HE payload + vertical launch | Low–medium |
| **P2** | Ballistic Computer aim solve + client arc preview | Low |
| **P3** | AP/cluster payloads, GPS beacon, console GUI | Low |
| **P4** | Terminal seeker, ship-mass damage hooks | Medium |
| **P5** | Hot/Warm/Cold ship tiers hooked into VS APIs / mixins | High win |

---

## 5. Super combat ↔ inventory effects (related UX)

Vanilla **status effects** now mirror Super Souls / Sparking / combat training so opening **E** (inventory) shows them in the effects panel. That is independent of VS2, but same “readability in multiplayer” goal: players and admins see buffs without custom GUI only.

---

## 6. Open questions

1. Soft or hard reject assemble over `maxBlocksPerShip`?
2. Can VS2 expose ship sleep API cleanly on our version, or do we need a thin mixin?
3. Should capital missiles be rare craftable mini-ships for events only?
4. PvP: FF on missiles default on or off?

---

## 7. Create interaction (summary)

Idle Create on ships must **sleep with** Cold ships; pilot/helm wakes **both** VS and Create.  
Land factories use the same Hot/Warm/Cold idea with Create-specific budgets (contraptions, trains, belts).  
Full design: **`docs/create-vs2-optimization.md`**.

---

*Doc only for VS2 missiles/perf until P0–P1 are scheduled. Super status effects are implemented in code separately.*
