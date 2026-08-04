# XenoPixels module performance (no global VS2 sleep)

Global VS2 ship Hot/Cold parking and Create freeze were **removed**.
Only **module** systems remain:

| System | Config / command |
|--------|------------------|
| Missile chunk force-load | `forceChunks*`, `/xenoperf set forcechunks` |
| Thruster phys force gate | `thrusterForceAlways` / `thrrange` |
| Stats HUD sync | `statsync` |

## Defaults: forces OFF

| System | Default |
|--------|---------|
| Thruster phys force | **OFF** (`thrforce false`) — plumes still work |
| Chunk force-load | **OFF** (`forcechunks false`) |

## Enable thruster force (when you want ships to move)

```text
/xenoperf set thrforce true
/xenoperf set thralways true
/xenoperf set thrrange 128
```

- `thrforce` — master for thruster `applyModelForce`  
- `thralways true` — any powered thruster  
- `thralways false` — only if a player is within `thrrange`  

## Force-load (chunks)

```text
/xenoperf set forcechunks true
/xenoperf set targetonly true
/xenoperf set radius 1
```

| Key | Meaning |
|-----|---------|
| `forcechunks` | Master on/off for `setChunkForced` (default off) |
| `targetonly` | Only impact/target chunks |
| `radius` | 0–2 |
| `playerange` | If &gt;0, only when a player is near that point |

## Config file

`config/xenopixelsmod-perf.json` — delete old keys (vs2sleep, create, mspt) or leave them; they are ignored after upgrade.
