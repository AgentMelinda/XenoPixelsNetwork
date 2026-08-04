# Ballistix + Voltaic + VS2 Compat (XenoPixels)

Optional integration: **Ballistix** missiles / silos work with **Valkyrien Skies 2** ships without requiring Ballistix or Voltaic to install XenoPixels.

## Load rules

| Mod | Required for XenoPixels? | Mixins apply when |
|-----|--------------------------|-------------------|
| VS2 (`valkyrienskies`) | Soft (already dep in this workspace) | Always available for ship sleep + wake |
| Voltaic | **No** | Present |
| Ballistix | **No** | Present **and** Voltaic present |

Gate: `ConditionalMixinPlugin` → `FMLLoader.getLoadingModList().getModFileById(...)`.

Mixin config: `xenopixelsmod.compat.mixins.json` (`required: false`, `defaultRequire: 0`).

## Build

```text
libs/Ballistix-1.20.1-1.0.9.jar
libs/Voltaic-1.20.1-1.0.11-1.jar
```

`compileOnly fg.deobf(files(...))` — not JiJ-bundled.  
Run with Ballistix on classpath:

```bash
./gradlew runClient -PxenoWithBallistix
```

## Mixins → hooks

| Mixin | Target | Hook |
|-------|--------|------|
| `EntityMissileMixin` | `EntityMissile.tick` | Wake ship under missile; fix shipyard coords |
| `VirtualMissileMixin` | `VirtualMissile.tick` | Project silo shipyard pos → world; wake path ships; **chunkload corridor** |
| `TileLauncherControlPanelMixin` | `TileLauncherControlPanelT1` (T2/T3 inherit) | **World-space range** (`calculateDistance` redirect) — fixes “won’t launch on ship” |
| `TileLauncherPlatformMixin` | `TileLauncherPlatformT1` (T2/T3 inherit) | Force Hot; **world spawn/target** for VirtualMissile |
| `TileVerticalLaunchSiloMixin` | **`TileVerticalLaunchSilo` (ballistix:vls)** | World range + world spawn + chunkloader on fire |
| `GenericTileOnLoadMixin` | `voltaic.prefab.tile.GenericTile` | Wake ship tile on load / player use |
| `ServerShipStaticMixin` | VS `ShipData.setStatic` | Observe static changes when perf on |

### Why radar gun worked but missiles didn’t

Radar gun stores a **world** XYZ target. The silo’s `BlockPos` on a VS ship is in **shipyard** space. Ballistix `calculateDistance(silo, target)` treated those as the same coordinate system → distance ≈ millions of blocks → range check failed and launch aborted with no feedback. Missiles that did spawn also started in shipyard void.

**Fix:** range + spawn + chunk tickets all convert through `BallistixVs2Compat` world transforms.

## XenoPixels ship modules (always registered)

| Block | Use |
|-------|-----|
| **Missile Chunk Loader** | Right-click toggle always-on / redstone; keeps chunk tickets for missile flight |
| **Ballistic Guidance Computer** (`ship_vls_guidance`) | **Native** XenoPixels ballistic system (not Ballistix). XYZ target, **missile speed 1–20** (Heavy/MAX for big hulls), pair thrusters, launch ship as warhead or fire **Missile Tubes**. |
| **Missile Tube** | Launches `BallisticMissileEntity` (eject→boost→coast→terminal). |
| **Ship Thruster** | Directional VS2 thruster (Create-style plume). Redstone or CC `setPower(0..1)`. |

### Fleet fire control

Guidance computers can now share a numbered fleet channel. Channel `0` is standalone;
channels `1–9999` link loaded computers in the same dimension. Set the same channel on
one guidance computer per vessel, choose a salvo stagger (`1–200` ticks), then use
**Fleet Salvo**. The source target is copied to the loaded channel members and launches
are staggered, with duplicate guidance computers on the same VS2 vessel de-duplicated.

Redstone rising edges on a channelled computer queue the same fleet salvo. The Target
Tool also broadcasts its stored target when used on a channelled guidance computer.
Unloaded vessels are intentionally skipped rather than force-loaded.

Loft Y and cruise Y have no artificial altitude ceiling; `0` retains its special
automatic behavior. Horizontal guidance defaults to a 1,000,000-block range, enough
for 1:1 regional maps. Existing servers keep their saved config value, so use
`/xenoperf set maxrange 1000000` (or `0` for unlimited) when upgrading an older world.
Flight timeout scales from the planned ETA so 300,000-block routes do not hit the old
fixed 30-minute fuse.

### Ballistic calculator and flight model

The guidance GUI now includes gravity and quadratic drag. **Earth** selects
`9.80665 m/s²` and `0.00002000 /m`. **Calculate / Set** reports:

- conventional azimuth (`0°` north, `90°` east);
- selected high-arc elevation;
- required and available boost-exit speed;
- predicted apex, time of flight, impact speed, and drag-aware miss distance;
- `SOLVED` for an unpowered ballistic solution or `POWERED-GUIDANCE` when the
  target needs the boost-glide/terminal controller after motor burnout.

The calculator and live VS2 controller use the same gravity, acceleration conversion,
and quadratic drag coefficient. Air density falls exponentially with altitude using an
8.5 km Earth-atmosphere scale height, so high ballistic arcs do not experience sea-level
drag for the whole route. Missile Tube entities inherit the same model, transition from
vertical ejection onto the computed firing vector, open terminal guidance farther out on
long routes, and use route-scaled lifetimes. A zero drag value provides a vacuum
calculation; it is useful for debugging but is not the realistic Earth preset.

CC:Tweaked additionally exposes:

```lua
guidance.setFlightModel(9.80665, 0.00002)
local result = { guidance.getBallisticCalculation() }
```

CC:Tweaked methods:

```lua
guidance.setFleet(12, 10) -- channel 12, half-second stagger
guidance.setTarget(1000, 80, -500)
guidance.broadcastTarget()
local queued = guidance.fleetSalvo()
local channel, stagger = table.unpack(guidance.getFleet())
```

Optional Ballistix mixins under `mixin.compat.ballistix` remain separate soft-compat only.

Place both on a VS ship next to Ballistix `vls` / platforms.

Logic lives in:

- `compat.ballistix.BallistixVs2Compat`
- `compat.voltaic.VoltaicCompatHooks`
- `perf.Vs2ShipSleepManager` (forceHot)

## Gameplay

- Launch from a silo **on a ship**: ship wakes (Hot); missile spawn/sim projected to **world** space.
- Missile flight near a ship: keeps that ship Hot briefly (no sleep mid-impact).
- Without Ballistix: zero cost, mixins not applied.

## Ops

`/xenoperf status` still reports VS2 sleep. Ballistix presence is independent.
