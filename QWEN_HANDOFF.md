# Handoff to qwen — seated flight, wing panels, and an unfinished bug batch

Written: 2026-08-18. Branch `new2`, **everything uncommitted**. Repo:
`C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen`.

This continues the flight-controller work you left half-finished (`AeroAeroModel` /
`AeroAeroForceSystem` written but never called, and `AeroAeroForceSystem` did not compile).
`CLAUDE_HANDOFF.md` sections 1–9 are the full engineering record; this file is the short version
plus **exactly where I stopped mid-task**.

## Read this first: what is actually verified

**I never launched a client. Nothing in this document has been played.** Every "done" below means
*written, compiles, unit tests pass, and a dedicated server boots with it registered* — nothing
more. No lift value, no flap feel, no HUD layout, no seat behaviour, no bug fix has been seen
working in a running game. Treat all of it as unproven until you fly it.

What I did verify, concretely:

- `gradlew build` (compile + tests) passes.
- `gradlew runGameTestServer` boots to `"No test functions were given!"` — registries load, mixins
  apply without error, `ModNetwork` reports 22 packets at protocol 26.
- Create, Create Aeronautics, Create Simulated, Offroad, Flywheel and Ponder all load in that dev
  run (seen by name in the log).

## Status at a glance

| Area | State |
|---|---|
| Aerodynamics + flaps wired into the flight tick | Written, builds, boots. **Never flown.** |
| Pilot seat (entity + block, bound + standalone) | Written, builds, boots. **Never flown.** |
| Seated pilot input + flight HUD | Written. **Never flown, HUD never seen.** |
| Seat hull-orientation (Sable tag) | Written. **Fix is inferred from Sable's code, not observed.** |
| Wing Panel block (per-block lift via Sable) | Written. **Lift values are guesses; never flown.** |
| Create + Create: Aeronautics in dev runtime | Done — this one *is* verified (they load). |
| Ki guidance stealing velocity | Changed in code. **Unverified in game.** |
| Ki disk not despawning on hit | Changed in code. **Unverified, and see the caveat in §2.2.** |
| Thruster does not connect to the chair | **Not started.** Only a hypothesis below, not a diagnosis. |
| Per-panel pitch/roll/yaw roles + keybinds | **Not started.** Design sketch only. |

---

## Part 1 — What is finished

### 1. Aerodynamics and flaps are connected

- Fixed the compile error in `aero/control/AeroAeroForceSystem.java` (`double ax = ax`, a
  self-referential local).
- `AeroBus.advanceFlaps(deltaSeconds, airspeed)` eases flaps at `AeroConfig.flapSpeedPerSec` and,
  under auto-flap, derives the setpoint from airspeed (`autoFlapExtendSpeed` 8,
  `autoFlapRetractSpeed` 20 — new config fields).
- **`aero/control/AeroFlightCore.java` is new and is the shared per-tick flight maths** (flaps →
  lift/drag → attitude → thrust). Both the flight controller and the pilot seat run it, so the two
  cannot diverge. It owns its scratch vectors; the tick allocates nothing.
- Every disengage path calls `AeroFlightCore.release(ship)`, which clears the stabilizer **and**
  the aero force. Both latch on the physics thread; releasing one alone left a disengaged ship
  generating lift.
- `FlightPlannerScreen` gained throttle / flap / auto-flap controls. Before this, `SET_THROTTLE`,
  `SET_ATTITUDE` and `SET_FLAP` were wire-reachable with **no client sender at all**.

### 2. `AeroControlHost` — the dispatcher is no longer tied to one block

`AeroActionDispatcher` now takes an `AeroControlHost` instead of `ShipVlsGuidanceBlockEntity`.
That is the only change it needed; validation, clamping, power gating and the authoritative echo
are untouched and still the single choke point for GUI, panel, CC peripheral and seat.
`dispatchQuiet` is the same validation without the trailing `syncAero()`, for the per-tick pilot
stream. Engine pairing moved into `AeroLinkManager.pairNearby` / `clearPaired` so both hosts claim
engines identically.

### 3. Pilot seat

`block/custom/PilotSeatBlock.java`, `block/entity/PilotSeatBlockEntity.java`,
`aero/seat/XenoPilotSeatEntity.java`, `aero/seat/AeroSeatInput.java`.

- **Bound mode:** a seat within 8 blocks of a flight controller flies that controller.
- **Standalone mode:** with no controller in range the seat block is itself the host — own bus, FE
  buffer, engine pairings. Manual only; engages on mount, emergency-stops on dismount.
- Input goes stale after 10 ticks without a frame and the seat commands idle, so a pilot who
  disconnects at full throttle cannot leave a ship climbing.

### 4. Input and HUD

- `network/packet/SeatFlightInputPacket.java` (C2S, protocol **25 → 26**). ~10 bytes. Validated by
  "you are this seat's controlling passenger", a per-player interval limit, clamps, then the
  dispatcher's own rules.
- `FLAG_FLAP_SET` marks frames where the pilot actually moved the flap control — without it the
  per-tick push cancels auto-flap permanently, since a manual set turns it off.
- `client/flight/XenoFlightControls.java`: Q/E roll, G flap stage, K mouse-aim toggle, X centre,
  Z air brake, vanilla W/S/A/D for throttle and yaw. Mouse-aim commands the heading you are
  **looking** at and lets the existing PD stabilizer supply the swing — no cursor capture, no mouse
  mixin. With a screen open, controls freeze but the stream keeps running.
- `client/hud/XenoFlightHudOverlay.java` renders only the authoritative snapshot cached in
  `client/flight/ClientFlightState.java`.

### 5. Seat vs. hull orientation — probably a missing tag, not our code

Sable decides this per **entity type**: `EntitySubLevelUtil.shouldKick` is
`!type.is(SableTags.RETAIN_IN_SUB_LEVEL)`, and a "kicked" entity is ejected into world space, so it
stops moving with the hull and its rider renders world-upright. Sable's own copy of that tag lists
`create:seat`; DragonMineZ ships its own copy too. Added:

- `data/sable/tags/entity_type/retain_in_sub_level.json`
- `data/sable/tags/entity_type/destroy_with_sub_level.json`

Why I believe this is the cause: the tag check is the only gate on whether Sable keeps an entity in
the sub-level, and Sable's own tag file lists `create:seat` for exactly this reason. But I never saw
a seat tilt with a hull, before or after. If it still does not rotate after the tag, the next thing
to check is whether `getContaining(seatEntity)` resolves the ship at all — the seat entity must be
positioned inside the plot for Sable's `entity_rotations_and_riding` render path to pick it up.

**Invariant (if the tag works):** the seat's rotation is stored **hull-relative** (the block's facing) and Sable
applies the hull pose at render time. Never add world-space rotation to the seat entity — it would
be applied twice.

### 6. Wing Panel — real per-block lift

`block/custom/WingPanelBlock.java` implements `dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider`.
`ServerLevelPlot` tracks providers as blocks are placed/broken; `ServerSubLevel` groups them and
applies lift and drag at each group's centre. Same mechanism Sable uses for Create's sails.

Modelled on the shipped reference, `SymmetricSailBlock` in Create Simulated:

- **Axis, not facing** (`RotatedPillarBlock`, normal = positive direction along `AXIS`). An
  aerodynamic surface is symmetric; a six-way facing was over-specified.
- **Placed by look direction** (`getNearestLookingDirection().getAxis()`), so a row placed while
  walking along a wing comes out parallel.
- Deliberately *not* copied: the sail returns lift `0` and parallel drag `1.75` — it is a drag
  surface. A wing keeps Sable's defaults (lift 0.475).

**No double lift:** `AeroFlightCore.baseLiftFactor` drops the lumped hull lift to zero as soon as a
ship has any lift provider. Flap lift is *not* scaled away (flaps are a high-lift device on top of
whatever wing exists). Config `aeroModelBaseLiftWithWings` restores the old behaviour.

### 7. Create + Create: Aeronautics in the dev run

`scripts/setup_dev_mods.ps1` copies `create-1.21.1-6.0.10.jar` and
`create-aeronautics-bundled-1.21.1-1.3.0.jar` from the CurseForge instance into `run/mods`, which
the NeoForge dev run scans. Create jar-in-jars Flywheel/Ponder/Registrate; the Aeronautics bundle
jar-in-jars Aeronautics/Offroad/Simulated. Verified: all load, no mixin failures, despite compiling
against Create 6.0.11 and running 6.0.10. `run/` is gitignored — re-run the script after a clean.

---

## Part 2 — The bug batch I was working through when I stopped

Four issues were reported together. **Two are fixed, two were never started.**

### 2.1 CHANGED IN CODE (unverified) — Ki guidance was stealing the attack's speed

`combat/technique/KiGuidance.java`, `applyCameraSteer`. It blended the two velocity **vectors**
linearly (`KiGuidanceMath.lerpVec`). The straight line between two equal-length vectors is shorter
than either, so every steering tick shaved speed off; a shot under continuous guidance slowed to a
crawl. Now it slerps the **direction** and re-applies the original speed, matching what the lock-on
path (`steerToward`) already did correctly.

**Rule to preserve:** guidance is an aim device. It decides where an attack points, never how fast
it travels — the user stated this as a requirement, not just a bug report.

Confidence: high that this was *a* real bug (the maths is plainly wrong), but I have not watched a
guided shot keep its speed in game, and I do not know whether this was the whole of what the user
saw.

`mixin/common/KiBlastSokidanSmoothMixin.java` still uses `lerpVec` **on purpose** — that is the
parked Sokidan position-chase, where DMZ computes a velocity whose magnitude encodes distance to
the parked point. Do not "fix" it to match.

### 2.2 CHANGED IN CODE (unverified, and partly an interpretation) — Ki disk (Kienzan) on hit

DMZ's `getMaxHits()` is used *only* to divide damage
(`getDamagePerHit = kiDamage / maxHits`); nothing counts hits and nothing discards on contact. The
only `discard()` in `AbstractKiProjectile.tick` is for an owner dimension change. So a Kienzan that
had already delivered its whole damage pool kept hanging in the target dealing nothing.

- `mixin/common/KiDiskHitDespawnMixin.java` (new) counts successful hits on disks only and discards
  once the budget is spent. Adds and removes no damage — by DMZ's own accounting the attack is
  over at that point.
- Registered in `xenopixelsmod.mixins.json` (this was the loose end I closed last).
- Config `XenoServerConfig.kiDiskDespawnOnHitBudget` (default true), plus a
  `XenoServerConfigKeys` entry so `/xenoserver set` can toggle it. **Deliberately not added to
  `SyncServerConfigPacket`** — it is server-side behaviour only, so the protocol stays at 26.

**Be honest with the user about this one.** Their report was explicitly uncertain ("or don't know
exactly ... maybe it's also losing its velocity"). What I proved by reading DMZ's bytecode is that
nothing in DMZ discards a disk on contact. What I did **not** prove is that this is what they saw —
the velocity bug in §2.1 alone could produce a crawling disk that looks like it never goes away, and
`KiDiskLifetimeMixin` (ours, pre-existing) is another suspect if its window maths misses a case.
This change is a deliberate behaviour change based on my reading, not a confirmed diagnosis. If the
user only wanted the velocity fix, `kiDiskDespawnOnHitBudget false` turns this off without a rebuild.

### 2.3 NOT STARTED — "thruster don't connect to chair"

Nothing was written for this. I read the code but never reproduced the problem in game, so the
below is a hypothesis with supporting evidence, **not** a diagnosis. What I established by reading:

- `AeroLinkManager.pairNearby` sets `thruster.setPairedGuidance(owner)` and never
  `setGuidanceOwned(true)`. **That is correct** — ownership is the *ballistic* path's flag, and
  `ShipThrusterBlockEntity.tick` returns early without applying its own force when owned, because
  ballistic applies thrust at the centre of mass instead. A non-owned thruster applies its own
  physics force from `ccPower`, which is what `VectorMixer` sets. So pairing is not the bug.
- The likely real cause: **the seat only pairs engines in standalone mode.** Any flight controller
  within 8 blocks puts the seat in bound mode, where it commands that controller and never claims
  engines — and if that controller is in `MISSILE` mode or simply not engaged, the dispatcher
  rejects every frame *silently*. The pilot sits down, presses W, and nothing happens.

Planned fix (decided, not implemented) in `XenoPilotSeatEntity.addPassenger` / `PilotSeatBlock`:

1. If bound and the controller is in FLIGHT mode but not engaged → engage it, remember the seat did
   so, and disengage on dismount **only if the seat was the one that engaged it** (an operator's
   autopilot must not be cancelled by someone sitting down).
2. If the bound controller has no paired engines → call `host.pairNearbyThrusters()`.
3. If the controller is in `MISSILE` mode → say exactly that in the mount message; do not switch
   modes silently.
4. Report the engine count in the mount message either way. Right now the pilot gets no feedback at
   all, which is most of why this reads as "doesn't connect".

### 2.4 NOT STARTED — per-panel pitch/roll/yaw roles and keybinds

Requested: configure each flap with a pitch / roll / yaw role and a targeted keybind. Design
decided, nothing written.

- Add a `ROLE` property to `WingPanelBlock`: `NONE / FLAP / PITCH / ROLL / YAW / BRAKE`, cycled by
  right-clicking the panel with an empty hand, with an actionbar confirmation.
- Replace the current boolean `DEFLECTED` with a tri-state (`NONE / UP / DOWN`) so surfaces visibly
  move with the stick rather than only with flaps.
- Drive it from the attitude error the stabilizer is already computing.
  `AeroStabilizerSystem.lastError(ship)` is public and returns the world-space axis-angle error.
  Decompose onto body axes using the world nose and up vectors `AeroFlightCore` already has:
  - `pitchErr = dot(err, rightWorld)` where `rightWorld = normalize(cross(noseWorld, upWorld))`
  - `yawErr   = dot(err, upWorld)`
  - `rollErr  = dot(err, noseWorld)`
  Apply a deadband, then map sign → deflection. `FLAP` role keeps using `bus.flap()`; `BRAKE` uses
  the air-brake flag.
- **Keybinds already exist** — mouse-aim drives pitch/yaw, Q/E roll, G flaps, Z air brake. The role
  chooses which existing control drives that panel, so no new binding is strictly required. Say so
  in the block's tooltip; add new keys only if the user still wants per-panel groups.
- Needs new model variants for up/down deflection, and blockstate entries for
  `axis × role × deflect` (keep the count sane — role need not change the model).

Current behaviour to replace: `AeroFlightCore.updateFlapDeflection` toggles the boolean when
`bus.flap()` crosses 0.5, bounded to 512 panels, writing block states **only on a crossing**, and
copying positions before writing because `setBlock` mutates the plot's lift-provider map being
iterated. **Keep both of those properties** in whatever replaces it — a per-tick block write on a
ship is a chunk re-render storm.

---

## Part 3 — Things that will bite you

- **Packet ids are positional** (`ModNetwork.register`, `id++`). Append only, never insert. Bump
  `PROTOCOL` and add a changelog line in the javadoc when the wire changes.
- **Do not add world-space rotation to the seat entity** (§5).
- **Do not restore the lumped hull lift alongside wing panels** (§6) — that is what
  `baseLiftFactor` exists to prevent.
- **Guidance must never change projectile speed** (§2.1).
- `AeroBus` mutators are package-private on purpose: only the dispatcher and the power budget write
  controller state. `advanceFlaps` and `reportAutopilot` are the deliberate public exceptions for
  the authoritative flight tick.
- The dedicated-server boot emits `ClientLevel` dist-cleaner warnings and JEI/annotation class-load
  warnings. Those are pre-existing and non-fatal; do not chase them.

## Part 4 — Build and test

```powershell
# once per fresh clone / after a clean, to get Create + Aeronautics into the dev run
powershell -ExecutionPolicy Bypass -File scripts\setup_dev_mods.ps1

.\gradlew.bat build                 # compile + tests
.\gradlew.bat runGameTestServer     # dedicated boot smoke test
# expected tail: "No test functions were given!" then BUILD SUCCESSFUL — that is success
.\gradlew.bat runClient             # the actual flight test, never yet done
```

Build DragonMineZ first if its jar changed; Xeno compiles against it.

The in-game checklist lives at the end of `CLAUDE_HANDOFF.md` ("Still unverified"). The single
riskiest assumption to test first: **a ridden seat stays put, and tilts, on a moving and inverted
hull.** Everything else is built on top of that.
