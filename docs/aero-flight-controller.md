# Aero: Flight Controller & Navigation

Design notes and roadmap for the **Aero** flight-controller system on Sable ships.
Goal: a powered, modular controller block that owns flight modes, navigation, and an
in-game map, driven through a physical control interface and a GUI.

Related: `docs/vs2-optimization-and-missiles.md`, `docs/external-thruster-compat.md`.
Code entry points: `vs/ShipBallisticController.java`, `client/gui/FlightPlannerScreen.java`,
`compat/xaero/XaeroWaypointPicker.java`.

---

## 1. Scope

The controller is a block-hosted system, not a per-item gadget. Components:

| Component | Role |
|-----------|------|
| **Modular Flight Controller** | Host block; owns state, power draw, and module slots |
| **Navigation Console** | Route planning, waypoints, flight director |
| **Interactive Controller GUI** | Screen-side control surface and map viewport |
| **Physical Controller Interface** | In-world clickable model geometry (levers, buttons) |
| **Animation System** | GeckoLib-driven controller and interface animation |
| **Internal Action & State System** | Action dispatcher + avionics bus between modules |
| **Modular Link Management** | Binds controller to thrusters, sensors, and peripherals |
| **Flight Mode Framework** | Swappable modes (manual, stabilized, director, autopilot) |
| **Navigation Infrastructure** | Waypoints, waystones, landing pads, sub-levels |
| **Autopilot Framework** | Direct-target and ordered waypoint-route guidance with arrival hover |
| **Active Terrain Generation** | Toggleable; cached to disk for map rendering |

---

## 2. Roadmap

Developed in milestone releases. The roadmap is expected to shift as the design settles.

### 0.5.x — Core Flight Controller
- Action Dispatcher
- Avionics Bus
- Link Manager
- Physical Controls
- GUI Framework

### 0.6.x — Navigation
- Map
- Route Planning
- Waypoints
- Flight Director

### 0.7.x — Flight Control
- Absolute world-attitude quaternion PD controller
- Sable body/world frame-safe stabilization
- Vector mixing
- Target and route autopilot with braking and arrival hover
- Diagnostics

### 0.8.x — Advanced Navigation
- Model Predictive Control
- Route Prediction
- Live Vessel Tracking

### 1.0 — Flight Management System
- Full Autopilot
- Docking
- Cargo Integration
- Communications
- Multiplayer Fleet Support

---

## 3. Power budget

The controller requires a substantial supply, and degrades function as power drops rather
than hard-failing. Draw scales with which subsystems are active — disabling the map and
other unused features drops consumption.

Baseline sizing assumes **10 Mekanism Advanced Solar Generators**. Mekanism's wiki lists an
Advanced Solar Generator at 300 J/t = 120 FE/t, so 10 provide roughly **1,200 FE/t** under
default direct-sun output.

| Load | Draw |
|------|------|
| Controller baseline | 1,200 FE/t |
| Engaged flight | +2,400 FE/t |
| Terrain map enabled | +600 FE/t |
| Advanced cooling while operating | +1,200 FE/t |

| Buffer | Value |
|--------|-------|
| Capacity | 5,000,000 FE |
| Input rate | 12,000 FE/t |

Capacity and input rate were raised together so the buffer is not emptied instantly by the
combined load, and so real power infrastructure can actually saturate the input.

Unplugged, an active controller draws on the order of **30,000 FE/s**, sustainable
indefinitely given enough solar or other FE generation.

---

## 4. Map system

Terrain rendering goes through **Xaero's World Map API** rather than decoding Xaero's cache
directly. This was a large FPS and detail win, and it opens the door to porting Sable × Xaero
integration into the mod so the ship itself appears on the map.

The mod bundles its own port of the
[Xaero World Map Bridge](https://modrinth.com/mod/xaero-world-map-bridge) — a client-side
compatibility library providing loader-neutral map and UI overlay APIs — to act as the
rendering bridge.

Terrain generation caches chunks to a folder so surroundings can be viewed from the
controller block. The map doubles as a radar for locating waystones, landing pads, client
waypoints, and sub-levels.

### Map GUI features

**Viewport and chrome**
- Actual map viewport with terrain rendering area
- Cyan/blue UI border, cyan active-tab accent
- Dark grid-style map background, map grid overlay

**Camera**
- 5 zoom levels: 1× / 2× / 4× / 8× / 16×
- `+` / `−` zoom controls and mouse-wheel zoom
- Click-and-drag panning
- `CENTRE PLAYER` and `CENTRE CTRL` buttons
- Current map centre coordinates readout

**Layers**
- Player marker
- Flight Controller marker
- Xaero waypoint layer
- Flight waypoint layer
- Waystone layer
- Claimed sub-level layer
- Landing pad layer

**Status and affordances**
- Terrain toggle button with online/hidden status
- Explicit `DRAG TO PAN | SCROLL TO ZOOM` hint for new users

---

## 5. Open TODO

### Controller polish
Once every button works reliably:
- Derive click detection from the model geometry instead of hard-coded percentages
- Remove the remaining magic numbers from the hit-test
- Add a developer debug overlay visualizing clickable regions during testing

### Known friction
- GeckoLib animations have repeatedly broken after patches; they need a stable rig.
- Terrain chunk loading still needs work before moving on to locations, waystones,
  sub-levels, and a possible built-in contraption diagram screen.

### Candidate next work
- Waypoint detection, paired with Xaero × Waystone, to allow navigating to waystones
- Possibly porting the Waystone integration into the mod directly

---

## 6. Compatibility

- **Mekanism** and generic **Forge Energy** power integration
- **Xaero's World Map** (via the bundled bridge port)
- **Xaero × Waystone** for waystone waypoints

---

## 7. Process notes

Playtests are gated on the controller reaching a reliably working state; recruitment happens
through the project Discord. Development is currently two people, with the possibility of
outsourcing to more developers — hence the roles set up in Discord.

Backups have grown aggressively (multiple GB); prune them periodically.

---

## 8. Flight controls

In **Flight** controller mode, the Easy tab offers Manual, Target Auto, and Route Auto.
Target Auto uses the controller's target coordinate. Route Auto follows the ordered points
from the Points tab and then the final target. Both modes reduce speed for turns, brake using
remaining stopping distance, and hold position against gravity after arrival.

Manual attitude values are absolute Minecraft-world angles: yaw `0` faces north (`-Z`), yaw
`90` faces east (`+X`), pitch `0` is level, positive pitch is nose-up, and roll `0` keeps the
calibrated hull-up axis upright. Body calibration defines the local nose and roll-up axes.
Flight engagement and autopilot progress are runtime-only and never resume after reload.
