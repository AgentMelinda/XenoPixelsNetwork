# Claude handoff: Aero guidance and Copycat Glowstone

Last updated: 2026-08-11 (third session appended — see "Session 3" at the end)

This document summarizes the work completed by Codex after Claude's previous session, the
important implementation decisions, verification performed, and the recommended next steps.

## Repository state warning

The working tree was already heavily modified when this work began. Many edited and untracked
files are earlier user/Claude work and were deliberately preserved. Do not reset, clean, or
discard the worktree wholesale. Review overlapping changes before editing.

## Completed: Sable Aero flight control

The old Aero rotation inputs were incorrectly interpreted as body angular rates. They now
represent absolute Minecraft-world attitude:

- Yaw `0` points north (`-Z`).
- Yaw `90` points east (`+X`).
- Pitch `0` is level and positive pitch raises the calibrated nose.
- Roll `0` keeps the calibrated hull-up axis upright.
- Sable logical-pose orientation is consistently treated as body-to-world.

`aero/control/SableAttitudeMath.java` builds the desired quaternion from the calibrated body
nose/up axes and returns shortest-path world-space quaternion errors.

`aero/control/AeroStabilizerSystem.java` now runs quaternion PD attitude control in the Sable
physics callback. It computes world angular acceleration, applies world angular-velocity
damping, clamps authority, transforms the result into body axes, applies the body inertia
tensor, and sends a body-space torque impulse to Sable.

The missile controller intentionally remains nose-only. Full roll locking was not added to
missile guidance because its existing nose-only controller avoids roll shaking while tracking.

## Completed: target and route autopilot

Runtime modes are defined by `AeroAutopilotMode`:

- `MANUAL`: throttle along the calibrated hull nose and hold the absolute attitude setpoints.
- `TARGET`: fly directly to the existing controller target.
- `ROUTE`: follow the existing ordered ballistic planner waypoints, then the final target.

`aero/control/AeroFlightDirector.java` provides world-space guidance with:

- Three-dimensional route lookahead.
- Monotonic waypoint progress.
- Turn-based speed reduction.
- Stopping-distance speed limits and arrival braking.
- Velocity/cross-track correction.
- Gravity compensation.
- Final brake-and-hover behavior.
- Level roll during automatic flight.

`ShipVlsGuidanceBlockEntity#aeroFlightControl` converts the director's world acceleration back
into body axes before calling `VectorMixer`. Flight/autopilot state is runtime-only and is
cleared on reload, power loss, missing ship, missing target, mode change, block removal, or
emergency stop.

## Completed: controls and telemetry

The Easy tab in `FlightPlannerScreen` is mode-aware:

- Missile mode retains the original beginner missile workflow.
- Flight mode displays Manual, Target Auto, Route Auto, Engage/Disengage, Pair Engines,
  Save Route, and Stop.
- The client polls an authoritative Aero snapshot while the flight UI is open.
- Distance, speed, autopilot mode, and waypoint progress are displayed.

Network additions are in `AeroControlPacket`, `AeroStatePacket`, and `AeroStateSnapshot`.
The channel protocol was bumped from `11` to `12`; clients and servers must use the same jar.

ComputerCraft additions in `VlsGuidancePeripheral` include:

- `setAeroMode(mode)`
- `setAeroAutopilot(mode)`
- `engageAero(enabled)`
- `setAeroThrottle(throttle)`
- `setAeroAttitude(yaw, pitch, roll)`
- `stopAero()`
- `getAeroState()`

## Completed: Copycat Glowstone

The block is registered as `xenopixelsmod:copycat_glowstone` and emits vanilla light level 15
while active. Its material is stored in `CopycatGlowstoneBlockEntity` as a complete
`BlockState`.

### Create-style rendering optimization

The first implementation used a custom world wrapper and performed repeated neighboring block
entity lookups while connected-texture models gathered data. It also sent broad updates and
extra explicit updates to all neighboring copycats.

This was replaced with the Create 1.21.1 pattern:

- `CopycatGlowstoneBlock#getAppearance` exposes the copied state to connected-texture models.
- The copied model's `ModelData` is gathered once per chunk rebuild and stored separately.
- Quad/render-layer/particle/AO calls receive the wrapped material's own model data.
- A material change sends one block-entity sync.
- The client performs one local model-data update/redraw when authoritative NBT arrives.
- No six-neighbor packet/update storm remains.
- Initial material orientation is derived from the clicked face for facing/axis properties.
- Adjacent copycats using the same material inherit its complete state.

The implementation is adapted from Create's MIT-licensed copycat code. Attribution is in
`THIRD_PARTY_NOTICES.md`; no Create assets were copied.

### Material locking and wrench removal

Once a texture has been copied, all further block-item applications are refused. Reapplying the
same material cannot rotate or replace it. Empty-hand and sneak-empty-hand reset behavior was
removed.

Only the actual `create:wrench` item unbinds the copied texture. This is handled both by the
block interaction and the existing high-priority `CreateWrenchHandler`, ensuring the unbind
runs before Create's normal wrench behavior. After unbinding, a new material can be applied.

### Optional Forge Energy

`CopycatEnergyStorage` exposes a receive-only Forge Energy capability on every side. Energy is
persisted in the block entity and marked dirty on a bounded cadence rather than every tick.
When the power requirement is enabled, the block consumes configured FE/t and its vanilla
`powered` state controls whether it emits light level 15 or 0.

Server configuration is written to `config/xenopixelsmod-server.json`:

```json
"copycatForgeEnergyEnabled": false,
"copycatEnergyCapacity": 100000,
"copycatMaxReceiveFePerTick": 1000,
"copycatEnergyUseFePerTick": 10
```

The master switch defaults to `false`, preserving always-on/free light. Set it to `true` to
require FE. Setting `copycatEnergyUseFePerTick` to `0` also makes an enabled power system free.

## Verification completed

The following succeeded after the final changes:

- `./gradlew compileJava --no-daemon --rerun-tasks`
- `./gradlew test --no-daemon`
- `./gradlew build --no-daemon`
- `git diff --check`

Focused tests in `SableAttitudeMathTest` cover north/east/pitch conventions, shortest quaternion
error, route progress, arrival braking, and gravity-compensated hover.

Latest built artifacts are under `build/libs/`, including:

- `xenopixelsmod-0.1.9-1.21.1.jar`
- `xenopixelsmod-Server-0.1.9-1.21.1.jar`

## Recommended next plan

1. Playtest Copycat Glowstone without Create installed:
   confirm normal materials, locking, persistence, light level, and no render errors.
2. Playtest with Create and a connected-texture material:
   confirm seams connect between adjacent copycats and that `create:wrench` alone unbinds.
3. Enable `copycatForgeEnergyEnabled`, connect several common FE cables/generators, and verify
   capacity/input limits, light shutdown on empty buffer, persistence, and config reload.
4. Test directional copied blocks from every clicked face. The applied state is intentionally
   fixed after the first copy; changing orientation requires wrench-unbind and reapplication.
5. Build a small calibrated Sable ship and tune attitude PD gains if heavy/asymmetric hulls
   oscillate. Current constants are in `AeroStabilizerSystem`.
6. Test Target Auto and Route Auto with forward, lateral, vertical, sharp-turn, and overspeed
   approaches. Verify final hover and monotonic waypoint advancement.
7. Profile a large wall of copycats during mass application and chunk rebuild. If rendering is
   still expensive, add a bounded quad/model-data cache keyed by material state and render layer,
   but do not cache connected-texture data without including neighborhood connectivity.
8. Before committing, separate or carefully review unrelated pre-existing dirty-tree changes.

## Known limitations

- No automated in-game Sable physics integration test exists; attitude/autopilot still needs a
  real ship playtest.
- Connected-texture behavior depends on the copied material mod honoring NeoForge appearance and
  model-data hooks.
- The copycat accepts broad block types. Exotic animated/block-entity-based models may not be
  meaningful as copied materials.
- Runtime config reload changes power behavior on the next server tick, but already connected
  energy networks may cache capability behavior until they naturally refresh.

---

# Session 2

Everything below was done after the section above. **Nothing here has been verified in-game** —
it compiles, `./gradlew build` passes and the 7 unit tests pass, but no `runClient` pass was
made. Rendering and physics changes in particular still need eyes on them.

## Review: bugs found and fixed

- **Copycat material never rendered in the default configuration.**
  `ClientModEvents.replaceCopycatModel` wrapped only `defaultBlockState()`, i.e.
  `powered=false`. With Forge Energy disabled (the default) the block sits at `powered=true`
  from its first server tick, and that variant is a separate map key holding the unwrapped
  model. Now wraps every state from `getStateDefinition().getPossibleStates()`.
- **HUD backing plate sampled unpainted atlas.** `XenoHudLayout.PANEL` declared
  `(310, 68, 96, 56)`; the generator paints `(310, 68, 48, 48)`. Corrected, and `drawPanel`
  now nine-slices via the new `HudDraw.blitNineSlice` instead of stretching the whole plate.
- **NaN from duplicate route waypoints.** `AeroFlightDirector` normalised a zero-length
  segment when two consecutive waypoints matched, poisoning the whole acceleration command.
  Guarded; regression test added.
- **Ship yawed to north on arrival.** Hovering on the target left no derivable heading and yaw
  fell through to `0.0`. `Command` now carries `headingValid`, and
  `ShipVlsGuidanceBlockEntity` holds its last commanded heading. Regression test added.
- **Power-budget bootstrap deadlock.** `AeroPowerBudget`'s OFFLINE branch drained the whole
  buffer every tick, so any supply slower than the baseline could never accumulate enough to
  come back online. It no longer drains — an offline controller runs nothing, so it costs
  nothing.
- **Per-frame reflection.** `XenoModernHudView.sparkMeter` called `getField("sparking")` every
  frame; resolved once and cached, including the failure.

## Review: reported, not fixed

All but the last of these were fixed in Session 3 below.

- `AeroFlightDirector` gravity compensation is `Math.max(0.0, gravity)`, so a negative-gravity
  (orbital / anti-grav) ship gets none rather than downward correction.
- Braking uses straight-line distance to the final target, not remaining path length, so a
  route that loops near its own start brakes early.
- `AeroStabilizerSystem.ENTRIES` only clears explicitly, and `lastError` is written on the
  physics thread while `lastError()` reads it on the game thread (diagnostics only).
- `SableAttitudeMath` falls back to the right vector at pitch +/-90, so roll jumps through
  vertical.
- Bar art skew overruns the declared region width by up to `SKEW` px.
- `chip_ready/cool/locked`, `slot` and `banner` are generated but still undrawn.

## Textures

All generated, all regenerable, all sharing `flight_controller_layout.PALETTE`.

- `tools/gen_copycat_textures.py` — unbound copycat plate. Deliberately does **not** imitate
  glowstone: applying a material is one-shot, so an unbound block must be identifiable in a
  wall at a glance.
- `tools/gen_vs2_block_textures.py` — **all 18** ship-block faces moved from the old
  brass/gold Create palette onto gunmetal + cyan. Colour carries function: cyan avionics,
  amber heat (thruster only), green chunk-loader node, red hazard banding.
  **Supersedes `gen_ballistic_textures.py` and `gen_create_propulsion_textures.py`**, which
  write the same filenames — running either reverts the art. Both now carry a SUPERSEDED
  header.
- `tools/gen_hud_textures.py` — added `bar_tip` (the old lit tip was baked at the right end of
  the fill sprite, so any bar under 100% lost it) and the seven `cd_*` cooldown sprites.
  Wired up `hp_crit` and `stm_tip`, which were generated and ignored.

Two invariants worth re-checking after any atlas edit: all 26 regions must be non-overlapping
and in-bounds, and `XenoHudLayout.java` must match `tools/hud_layout.py` exactly — they are
paired by hand.

## HUD

- **DMZ release percentage** now drawn beside the player name in the modern view.
  `XenoHudSnapshot.releaseText` already existed and both legacy views drew it; the modern view
  was the only one dropping it.
- **Modern cooldown strip.** `XenoCooldownHudOverlay.drawModern` blits the atlas instead of
  stacking `fillPara` rectangles. Gated on the same `legacyHudRenderer` flag. The new sprites
  are authored square, so `XenoCooldownHudConfig.squareShape` is a no-op in the modern path;
  the legacy path is untouched.
- **`modernunified` renderer.** `/xenohud renderer modernunified` draws the stat cluster and
  the combat strip under one nine-sliced plate at the main HUD's x/y/scale. The cooldown HUD's
  own position and scale are ignored by design; its content settings are all still honoured.
  Implemented as a second `unifiedHudRenderer` flag so older configs land on plain modern.
  Known gap: `/xenohud edit` clamps using `BASE_HEIGHT` (90) while the unified panel is ~130
  tall, so it can overhang slightly.

## Vanish

- `combat/VanishShadeFx` — black humanoid silhouette at the departure point, oriented to the
  fighter's facing, with electric arcs and a thunder crack. Duration comes from dust particle
  lifetime, not a timer. Server-side so every client sees it with no client state or mixin.
  Fires from `handleVanish` and `handleSuperCounter`.
- Sound is wired: `ModSounds` registers `xenopixelsmod:vanish`, `sounds.json` maps it to
  `sounds/vanish_out.ogg`, and both config keys default to it, with fallback to DMZ
  `evasion1`/`evasion2`.
  **The bundled `vanish_out.ogg` is an MP3 renamed to `.ogg`** (ID3v2.4, MPEG-1 Layer III,
  44.1 kHz, ~6.3 s, zero `OggS` pages). Minecraft cannot decode it, so vanish is silent until
  it is genuinely converted. See `docs/vanish-sound.md`. It is also ripped game audio — a
  licensing decision for the project owner, since this mod is All Rights Reserved and public.

## YAWP

Diagnosed by decompiling `yawp-1.21.1-0.6.3-beta3.jar`. Two bugs in `IFlagArgumentTypeMixin`:

1. The region name was **guessed** from argument keys `local`/`region`/`name`. When all three
   missed, the flag was built with an empty region and stored under a key nothing reads — the
   command reported success and changed nothing. Now resolves through
   `RegionArgumentType.getRegionType` then `getRegion` then `getName()`, exactly as the
   injected method does two instructions later.
2. Dimension came from `context.getSource().getLevel()` — the executor's dimension, not the
   region's. Now `region.getDim()`.

`/kiflag remove <region> [target]` added; `masters` was missing from both the `here` and
`list` readouts and from the error message. **`/wp ... flag add|remove` for our flags is
impossible** — YAWP's add/remove paths are typed on the `RegionFlag` enum end to end, and
injecting a synthetic constant would be serialized into YAWP's region files and corrupt them.
Editing works, because `FlagCommands` reaches it through `IFlagArgumentType`.

Note: every injector there is `require = 0`, so a YAWP update silently reverts this with
nothing in the log.

## Missiles, thrusters, Sable

- **Terminal guidance** was pure pursuit with uncompensated gravity — it sags below the line
  of sight faster than the 0.12 rad/tick turn limit can recover, landing short and low by a
  consistent margin (user reports ~5 blocks). Now aims at a gravity-compensated point,
  `0.5*g*t^2` high, with time-to-go capped at 60 ticks.
  **Rolled back after playtesting — it was worse than the uncompensated path.**
  `missileTerminalGravityCompensation` now defaults to **false**, which is arithmetically
  identical to the original code. The branch is kept so the idea can be retried with better
  constants; the `0.5*g*t^2` term almost certainly over-corrects at short time-to-go, since the
  missile is already diving by then. The uncompensated path lands within a few blocks.
- **Sable mass properties** — 12 files, now inside the optional `xeno_ship_masses` datapack
  rather than always-on. We previously shipped **none**, so every XenoPixels block on a hull ran
  on Sable defaults. Format confirmed from Sable's own data; its baseline is `heavy` 2.0 /
  `light` 0.5, so 1.0 is the implicit default. Values are 2.0-3.5, i.e. modest.
  Made optional because mass is not inert: it changes draft and inertia, and with Waterworks
  installed it feeds `pressureDepthScale` and therefore `compartmentFailurePressure`, which is a
  documented route to a compartment-failure explosion. Off by default means the shipped
  behaviour is unchanged from before this session, and a ship can be tested both ways without a
  rebuild.
- **Thruster impulse guard** — `thrusterImpulseGuardEnabled` (default true) +
  `thrusterMaxImpulse` (50000). Rejects non-finite impulses, clamps runaways, warns once per
  ship. Rapier integrates a bad impulse straight into the body, so guarding the input is the
  only recoverable point.

## Northstar: missiles no longer teleported to space

Northstar teleports entities that cross `atmosphereTeleportHeight` (default 1000) into a space
dimension. A lofted ballistic missile crosses that on the way up, so it left the world mid-arc
instead of coming down on the target.

Northstar gates this on the entity tag `northstar:ignore_world_bounds_teleport` (it ships with
just `northstar:rocket_contraption` in it). We now add `xenopixelsmod:ballistic_missile` to that
tag from `data/northstar/tags/entity_type/ignore_world_bounds_teleport.json`.

Tags merge rather than replace, so Northstar's own entry is preserved. The entry is marked
`"required": false`, and if Northstar is absent the file is simply an unused tag — safe either
way, which is why this is always-on rather than an optional pack.

## Ship roll reference: body calibration cannot define it

Symptom: hull holds pitch and yaw fine (no wobble) but sits at the wrong roll.

`aeroBodyUp` derived "up" from `centre - base`. But base, centre and nose are three points
**along the hull axis** — collinear by construction, and the default calibration is literally
`pos.below()`, `pos`, `pos.above()`. Projecting that perpendicular to the nose gives the zero
vector, so `SableAttitudeMath.orthogonalUp` falls through to an arbitrary fallback axis:

```
body nose        = (0, 1, 0)
up candidate     = (0, 1, 0)   (centre - base, collinear with the nose)
after projection = (0, 0, 0)   -> degenerate
fallback chosen  = (1, 0, 0)   -> body up = +X, arbitrary
```

Meanwhile `AeroStabilizerSystem`'s own default `bodyUp` is `(0, 0, -1)` — a 90 degree
disagreement. The roll reference was never derived from the ship at all.

Now uses the controller block's `FACING` (a real ship-local direction, stored in the block
state), falling back to the old calibration vector and then world up. Covered by
`collinearCalibrationCannotDefineRoll`.

**This changes roll behaviour for existing ships** — roll 0 now means something consistent,
tied to how the controller block is oriented, so hulls may need re-trimming. It makes roll
deterministic and operator-controllable, but the proper fix is a fourth "dorsal" calibration
point, which needs a UI and packet change.

## Resolved: Sable ships exploding — it was Waterworks

**Cause: `waterworks-server.toml` `compartmentFailureEnabled = true`.** Waterworks scans every
Sable ship every 5 ticks for sealed compartments and detonates them
(`compartmentFailureExplosionPower = 3.75`) once pressure crosses
`compartmentFailurePressure = 1.95`. Confirmed by the server owner. Fix is that one config line;
to keep the mechanic instead, raise the pressure threshold or `compartmentFailureMinVolume`.

Placing a thruster was a red herring — sealing an air pocket merely creates a compartment for
the scanner to find, so it made a timer-driven explosion fire sooner. The tell was that it also
happened spontaneously with no block placement.

Ruled out along the way, recorded so they are not re-suspected:

- **The impulse application point.** `worldPosition` looks wrong because sub-levels live in plots
  at large world offsets, but `MassTracker.addBlockMass` uses raw `BlockPos.getX/Y/Z`, so the
  centre of mass is in the same space and the lever arm is correct.
- **`fuck-sable`'s `panic-guard`.** `RigidBodyHandleMixin` and `RapierPhysicsPipelineMixin` are
  purely null/removed-body guards that cancel operations on invalid bodies and log throttled
  warnings. No clamping, no NaN handling, nothing destructive.
- **Our thruster force.** It reproduced with no redstone and no guidance block, so power was 0
  and the physics tick skipped the thruster entirely.

The thruster impulse guard added during this investigation is kept — it is cheap insurance
against a genuine runaway and turns a silent blow-up into a log line — but it was not the fix.

## Open: server world-height mismatch (not this mod)

`Ignoring heightmap data ... expected: 52, got: 37` then an `ArrayIndexOutOfBoundsException`
in `ThreadedLevelLightEngine.initializeLight`.

37 longs = 9 bits = standard 384-tall world; 52 longs = 12 bits = a world over 1024 tall. The
server believes the overworld is tall while the saved chunks are standard.

**`fuck-sable`'s `world-height-override` is not the fix, and enabling it makes things worse.**
`WorldHeightOverrideMixin` only overrides `getMinBuildHeight()` and injects on
`getMaxBuildHeight()`. It does not touch `getHeight()` or `getSectionsCount()`, which are what
actually determine heightmap bit width and chunk section count. So it clamps *building* to 320
— the reported "cannot build over y 319" — while chunk storage still uses the tall height, and
the mismatch and the light-engine crash both survive. It is a build-limit hack, not a storage
fix.

The real fix is to remove whatever is raising the overworld height at runtime so the dimension
matches the save. No mod jar ships a `minecraft:overworld` dimension_type override, so it is
runtime code or a world datapack; Northstar is the prime suspect
(`atmosphereTeleportHeight = 1000` needs an overworld taller than 1000). Check
`IsraelLinear/datapacks/` too.

If the tall world is actually wanted, the pre-existing chunks are the problem rather than the
config, and they need regenerating — there is no in-place migration for heightmap bit width.
**Back up the world first** either way: loading chunks under a mismatched height can rewrite
them wrongly. Our `data/northstar/` is only moon ore features, so this is not us.

## World height override (our own, optional)

`data/ModDatapacks` registers two built-in datapacks, both with `alwaysActive = false` so they
are inert until an operator runs `/datapack enable "file/<name>"`:

- `xeno_standard_overworld` — y -64 to 320, vanilla geometry. Forces an overworld back to
  standard when another mod has raised it and the existing chunks were saved at 384.
- `xeno_max_overworld` — y -2032 to 2032, Minecraft's hard maximum (`min_y >= -2032`,
  `min_y + height <= 2032`). 254 sections per chunk against vanilla's 24.

Both fully replace `minecraft:overworld`'s dimension_type, which is the point: build limits,
`getHeight()` and `getSectionsCount()` all derive from it, so they move together. Overriding
only the build limits — `fuck-sable`'s approach — clamps placement while leaving chunk storage
on the old geometry, which yields a world you cannot build in *and* the original mismatch.

Worth noting: -2032..2032 packs heightmaps at 12 bits, 5 entries per long, **52 longs** — the
exact figure in the server's `expected: 52` error. The server's overworld is already at a
12-bit height while `IsraelLinear` was created at 384.

**Enabling one on an existing world does not migrate it** and reproduces the crash. Enable at
world creation, or use `xeno_standard_overworld` to force a world back to the geometry it was
actually made with.

## Config keys added this session

Server (`xenopixelsmod-server.json`): `missileTerminalGravityCompensation`,
`thrusterImpulseGuardEnabled`, `thrusterMaxImpulse`, `vanishShadeEnabled`,
`vanishShadeDensity`, `vanishThunderVolume`, `vanishSoundOut`, `vanishSoundIn`.

Client (`xenopixelsmod-hud.json`): `unifiedHudRenderer`.

## Still true from Session 1

The tree is heavily dirty with mixed work; `build_jij*.log` and `compile_xeno.log` are
untracked in the repo root and should not be committed. Separating unrelated changes before
committing is still outstanding.

---

# Session 3

Closes the Session 2 "reported, not fixed" list except the undrawn sprites. `./gradlew build`
passes and all 14 unit tests pass. **Still no in-game pass** — as with Session 2, the physics
and rendering changes have not been seen running.

## Flight director

- **Negative gravity is now compensated.** `acceleration.y += gravity` with the clamp on
  `Math.abs(gravity)`, rather than clamping the compensation itself at zero. An orbital or
  anti-grav body that pulls the hull upward gets a downward correction instead of none.
  `ShipVlsGuidanceBlockEntity`'s throttle normalisation uses `Math.abs(gravity)` to match —
  otherwise it saturates against a ceiling smaller than the acceleration being commanded.
- **Braking uses remaining path length.** New `remainingPathLength` sums the leg to the current
  waypoint plus every remaining leg. Identical to the old behaviour for a single-point route;
  for a route that doubles back near its own start, the ship no longer brakes to a crawl on the
  outbound leg. Regression test: a hairpin three blocks from the ship in a straight line but
  118 blocks along the path commands ~9.8 b/s where it used to command ~2.

## Attitude

- **Roll no longer jumps through vertical.** At pitch +/-90 world up projects to nothing, and
  the fallback was `(cos yaw, 0, sin yaw)` — perpendicular to the limit the non-degenerate
  branch actually approaches, so the reference snapped 90 degrees as the nose passed vertical.
  Now takes that limit, `(-s*sin yaw, 0, s*cos yaw)` for `s = sign(sin pitch)`. Regression test
  asserts continuity at 89.999 -> 90 and -89.999 -> -90, both under one degree.
- **`lastError` publication.** The vector is rewritten in place on the physics thread every
  tick, so reading its three components from the game thread could mix two ticks. Three volatile
  doubles are now written once the vector is complete, and `lastError()` reads those.
- **Stale stabilizer entries are swept.** `ENTRIES` was only ever emptied by an explicit
  `clear()`, so a sub-level deleted while engaged leaked for the session. A live entry is
  re-commanded every server tick, so the physics pass timestamps what it sees and drops anything
  untouched for 60 s, sweeping at most every 30 s.

## HUD

- **Skewed art is contained.** `body_width(w) = w - SKEW` in `tools/gen_hud_textures.py`, applied
  to `bar`, `bar_tip`, `frame` and `segment`. This was not only untidy: `stm_off`, `stm_on` and
  `stm_tip` sit 14 px apart, and the lean painted 20 px of each segment into the next one's
  region. Verified after regenerating that all 26 regions are in-bounds and non-overlapping and
  that no painted pixel in the atlas falls outside a declared region.
- **Unified panel clamps to its real size.** The plate's height depends on how many chip rows
  wrap into it, so there is no constant to clamp against; `BASE_HEIGHT` (90) let a ~130-tall
  panel hang off the screen and cut the drag bounds short. `XenoUnifiedHudView` reports the
  measured plate size to `XenoHudConfig.reportUnifiedSize`, and `scaledWidth`/`scaledHeight` use
  it while the unified renderer is active. The edit screen's hit test and resize handle both go
  through those, so they follow automatically.

## Transform charge indicator restored to the modern renderers

The red ring that fills clockwise around the portrait while transform is held (hold G) existed
only in the legacy overlay — the same class of omission as the release percentage found in
Session 2. `XenoModernHudView` drew the portrait and never the charge, so `modern` and
`modernunified` showed no transform progress at all.

The perimeter walk moved from `XenoHudOverlay` into `HudDraw.transformChargeBorder`, unchanged,
and both call it. Legacy geometry is identical (a ring 4 px outside the portrait square). The
modern view draws it on the portrait frame's own footprint instead: that cluster starts at x=2
inside a plate, so an outset ring would hang off the left edge, and with the face inset 6 px a
4 px ring reads as the frame lighting up without covering it. The unified renderer inherits it
through `renderContent`.

Left alone: `XenoHudView` (the LDLib renderer) has its own take — an octagon ring that thickens
with charge rather than filling round. It does indicate charge, so changing it to match is a
design call rather than a missing-feature fix.

## Still open

- `chip_ready/cool/locked`, `slot` and `banner` remain generated but undrawn. Drawing them is a
  feature decision (a KO banner in particular), not a defect, so they were left alone.
- Everything in "Recommended next plan" above is still a playtest list.

---

# Session 4

Verification, the undrawn-sprite loose end, and the first commits. **Still no in-game pass** —
everything from Sessions 2 and 3 remains unseen at runtime.

## Verification

`./gradlew build` passes. Tests were re-run with `cleanTest` rather than trusted up-to-date:
34 tests across `SableAttitudeMathTest` (11), `ShipBallisticControllerTest` (11),
`BallisticFlightPlannerTest` (10) and `ExternalThrusterCompatTest` (2), zero failures.

## The five undrawn sprites are retired, not drawn

`chip_ready`, `chip_cool` and `chip_locked` are 24 px flat discs from an earlier cooldown
design. The `cd_*` family that shipped in Session 2 supersedes them completely and is strictly
better: `cd_chip` / `cd_chip_hot` / `cd_chip_off` are nine-sliceable to the real 52x34 chip,
carry the accent through a tinted rail and meter, and are what `drawModernChip` already blits.
Drawing the old discs would put a second, worse status chip beside the real one.

`banner` is a KO banner with nothing to trigger it: the codebase has no KO or downed state, and
on ordinary death vanilla opens the death screen, which hides the HUD entirely. Inventing a
downed state to justify existing art is a gameplay feature, not a HUD fix.

`slot` is a technique-slot frame for a technique slot the HUD does not have.

So all five are gone from `tools/hud_layout.py`, `tools/gen_hud_textures.py` and
`XenoHudLayout.java`, along with the now-unused `chip()` and `banner()` generators and the
`banner_dark` / `banner_mid` palette entries. The atlas is regenerated: 21 regions, all
in-bounds, none overlapping, no painted pixel outside a declared region. `compileJava` passes.

If a KO banner is wanted later, `git show` on this commit has the generator back.

## Commits

The tree had been dirty across four sessions. It is now committed on `new2`:

1. `chore: ignore build transcripts and python bytecode` — `*.log` and `__pycache__` were
   accumulating in the repo root and drowning `git status`.
2. `feat: Sable flight control, Copycat Glowstone, textured HUD, compat fixes` — the code and
   assets, in one commit. A per-feature split was considered and rejected: `XenoPixelsMod`,
   `XenoServerConfig`, `ClientModEvents`, `ModNetwork` and `en_us.json` each carry hunks from
   three or four features, so the split would need hunk surgery and would still yield commits
   that do not build in isolation.
3. `docs: session notes` — this file and its siblings.

`stuff to make for aero` is deliberately left untracked; it reads as personal design notes
rather than a repo document.

## Still open

- The whole playtest list above. Nothing in Sessions 2-4 has been seen running.
- `vanish_out.ogg` is still an MP3 renamed to `.ogg`, so vanish is silent. See
  `docs/vanish-sound.md`.
- The server world-height mismatch is not this mod's; see the Session 2 section.

---

# Session 5

**The first in-game screenshot arrived**, which found a real HUD bug. Also ports CC:LiftLink's
elevator ComputerCraft methods.

## First in-game look at the modern HUD

Working as intended: the bevelled plate, gradient HP/KI bars with the lit leading tip, the 16
stamina segments, the DMZ release percentage beside the name (the Session 2 fix), and the combat
strip with key badges, per-move accent rails, meters and the combo counter.

**The sparking pip column was painting over the bars.** `SPARK_X` was `PORTRAIT_X + PORTRAIT + 2`
= 68 with a 12px sprite, so the lane spanned 68..80, while `CONTENT_LEFT` was
`PORTRAIT_X + PORTRAIT + 8` = 74. `drawSparkPips` runs after `drawBar`, so the pips landed on top
of the first 6px of every bar. Four of them are dark `spark_off` art over bright bars and read as
notches; the fifth sits below the stamina row over bare plate, which is the stray dot in the
screenshot — scaling back from it puts the dot at unscaled y≈72, exactly pip 5 at
`SPARK_Y + 4*13 = 64..76`.

`CONTENT_LEFT` is now derived from the pip lane (`SPARK_X + SPARK_W + SPARK_GUTTER` = 84) rather
than sharing an origin with it, so the collision is unrepresentable. Cluster width 378 -> 388;
the unified renderer recomputes `perRow` from it. The 13px pitch is now `SPARK_STEP` rather than
hardcoded in the view.

Two things noted and deliberately left: the HP/KI numerals sit on top of the bar frame and lit
tip at the right end, and the screenshot is the plain `modern` renderer — `modernunified` has
still never been looked at.

## Create elevator ComputerCraft methods

`compat/create/elevator/{ElevatorMethods,ElevatorHelpers}.java`, registered from `CcCompat`.
Full API reference in `docs/create-elevator-cc.md`.

Ported from CC:LiftLink, which targets 1.20.1/Forge. The peripheral type (`create_elevator`),
method names and returned table keys are identical, so existing Lua runs unchanged.

**Create became a compile dependency for the first time.** Everything else Create-related in this
repo works off registry names — `CreateWrenchHandler` looks up `create:wrench` as a
`ResourceLocation` — but the elevator API cannot be reached that way. Create `6.0.11-295` is now
`compileOnly`, along with the four libraries javac needs to resolve the elevator classes'
supertypes: Ponder, Catnip, Flywheel API and Registrate. `transitive = false` on Create itself,
because its POM pulls a runtime Flywheel and an open-ended Ponder range that would otherwise
decide our build. Repositories added: `maven.createmod.net` and `maven.ithundxr.dev/snapshots`
(Registrate is not on Create's own maven). Verified that no Create class ends up in the jar.

The 1.21.1 API was checked member by member with `javap` against the real jar rather than assumed
from the 1.20.1 original. Everything upstream uses is unchanged, including the public
`shortName` / `longName` / `lastReportedCurrentFloor` / `offset` fields and the still-protected
`ControlledContraptionEntity.controllerPos` that forces the reflection.

Gating follows the rule the rest of this repo's optional compat uses: a `[6.0,)` range in
`mods.toml` wide enough that a Create bump cannot become a boot failure, plus a `Class.forName`
probe on `ElevatorContactBlockEntity` before anything names `ElevatorMethods` — the registration
call lives in its own one-line method so the guards have all returned before the JVM has any
reason to resolve Create's types. It also stands down if `createelevatorcc` is ever installed
alongside, since CC rejects duplicate method names on one peripheral.

Three deviations from upstream, all commented in place and listed in the doc: the fallback pulley
scan is bounded to the contact Y range plus 64 rather than the whole world height across a 7x7
column (~19k lookups per call on a standard world, ~200k on `xeno_max_overworld`, on the server
thread, every poll); `bestFloorName` no longer uses `Objects.requireNonNullElse`, which throws
when both names are null; and `modName` reports this mod.

**Licence:** CC:LiftLink is MPL-2.0 and this is a derivative. Under MPL-2.0 section 3.3 the two
ported files stay MPL — headers in both, entry in `THIRD_PARTY_NOTICES.md` — while the rest of
the mod keeps All Rights Reserved. That is permitted but means their source ships with the
project. If carrying MPL files is unwanted, the alternative is a clean-room reimplementation
against Create's API without reference to theirs.

## Verification

`./gradlew build` passes; 34 tests, zero failures. The elevator methods have **not** been run in
game — there is no Create install in this environment to attach a modem to.
