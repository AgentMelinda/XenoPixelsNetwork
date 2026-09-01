# Claude handoff: DragonMineZ 1.21.1 + XenoPixelsNetwork changes

Updated: 2026-08-12

This is the current engineering handoff for the related DragonMineZ and XenoPixelsNetwork worktrees. It describes the code as it exists in the working trees, not only committed history.

## Repository state

| Project | Directory | Branch | HEAD |
| --- | --- | --- | --- |
| DragonMineZ | parent directory, `../` | `1.21.1` | `e5893250` |
| XenoPixelsNetwork | this directory | `new2` | `67c68c2` |

Both worktrees are intentionally dirty. Some relevant changes are staged, some are unstaged, and `CharacterPortraitCache.java` is still untracked. Do not reset, clean, checkout, or overwrite either tree. There are also many unrelated screenshots, logs, extracted sources, temporary directories, and old plans; they are not part of this change set.

The repository-wide port audit used DragonMineZ commit `0e336bcd`, the immediate pre-port Minecraft 1.20.1 / Forge 47.4.10 revision, as its baseline. Commit `81234b1c` was used only for the focused Sokidan history. The target runtime is Minecraft 1.21.1 with NeoForge 21.1.238 (the requested compatibility floor was NeoForge 21.1.200+).

## Outcome at a glance

- Sokidan steering now transmits the real render-camera direction every active tick, predicts smoothly for the controlling client, and retains server authority.
- Giant transformations move the gameplay camera back and the HUD portrait scales down to fit, including DMZ Oozaru's intrinsic 3.8x model scale.
- The live character model is the default Xeno HUD portrait and is cached into a 128x128 render target once per player tick for substantially lower render cost.
- Xeno can raise DMZ KI creator size/speed ceilings. Defaults are size `320` and speed `32`, with finite runtime clamps and bounded block destruction.
- Beam-surge debug text displays the current wave distance in blocks.
- Several independent port/runtime regressions were fixed: looping-sound source exhaustion, oversized integer config loading, hot-path optional allocation, a dead synced-data callback, and a stale shader uniform.

## DragonMineZ changes

### 1. Sokidan camera steering and prediction

The original port regression was caused by replacing synchronized entity rotation with `CameraAimHelper.resolve(owner)` in code that simulated on both logical sides. The helper read persistent entity data populated only by client-to-server charge packets. Consequently, the server steered from stored camera aim while the client silently fell back to `getLookAngle()`. The stored aim also became stale 80 ticks after charging because nothing refreshed it during released control.

The final implementation is more complete than merely reverting those calls:

- `SokidanControlC2S` now carries an action (`TOGGLE` or `AIM`) and three float components for a normalized camera direction.
- The second-function toggle sends the current render-camera direction with the toggle request.
- `ClientTickEvent.Pre` samples `gameRenderer.getMainCamera().getLookVector()` and sends an `AIM` update every tick while a locally owned controllable Sokidan is active. Sampling in `Pre` ensures both sides have the intended direction before that tick's entity simulation.
- `CameraAimHelper.store` is updated on both the local client and server. Its documentation now states that persistent data is not generally synchronized and therefore cannot be used casually in shared prediction paths.
- `KiBlastEntity.isActivelyControlledSokidan()` requires firing, parked, and controllable state. Local entity IDs are tracked and cleared on login, clone, logout, or a missing client level/player.
- The controlling client ignores `lerpTo` and `lerpMotion` corrections only while it owns and actively controls the Sokidan. This prevents the normal `KI_BLAST` ten-tick tracking cadence from snapping immediate local prediction back to an older server path.
- The server sets `hasImpulse` during active control so remote viewers receive authoritative movement more frequently. Remote players still accept normal server interpolation.
- A parked-state update restores the local launch velocity when control ends, preventing packet ordering from leaving the projectile visually stationary.
- Collision, damage, removal, and the true entity state remain server-authoritative.
- Automatic `fireHability` calls in Blast, Laser, and Disk tick methods are server-only. This prevents the client from performing a one-shot launch calculation from server-only state.
- The charging-ball `updatePositionRelativeToOwner` path still runs on both sides and therefore deliberately uses synchronized owner rotation (`getLookAngle`, yaw, and pitch).

Important behavioral contract: one-shot server launch calculations may use `CameraAimHelper.resolve`. Shared per-tick code may use it only when that control path explicitly refreshes both local and server copies, as active Sokidan steering now does.

Do not globally change `KI_BLAST`'s entity type update interval. The higher-frequency behavior is conditional on active Sokidan control so ordinary KI blasts retain their existing network cost.

Files:

- `../src/main/java/com/dragonminez/client/events/ForgeClientEvents.java`
- `../src/main/java/com/dragonminez/common/compat/CameraAimHelper.java`
- `../src/main/java/com/dragonminez/common/init/entities/ki/KiBlastEntity.java`
- `../src/main/java/com/dragonminez/common/init/entities/ki/KiDiskEntity.java`
- `../src/main/java/com/dragonminez/common/init/entities/ki/KiLaserEntity.java`
- `../src/main/java/com/dragonminez/common/network/C2S/SokidanControlC2S.java`

### 2. Giant-form gameplay camera

`OverShoulderCamera` reads the character's resolved model scaling and uses the largest finite axis, never less than 1.0, to scale camera-back distance. This applies to vanilla third-person distance and the configurable back/up/side offsets. Normal-sized forms remain unchanged; Oozaru, giant Namekian, large Frost Demon/Arcosian, Metal, and other enlarged models move the camera far enough back to remain usable.

File: `../src/main/java/com/dragonminez/client/render/camera/OverShoulderCamera.java`

### 3. Lightweight HUD portrait render contract

`EntityPreviewRenderContext` now has a float-scale overload while retaining the old integer overload for source/binary callers. Fractional scale is required to fit giant forms; rounding a value below 1 to an integer made the old path unable to scale them down.

It also has a nested `hudPortraitDepth` context and `renderHudPortrait(...)` entry point. While that context is active, `DMZPlayerRenderer`:

- skips the Iris shader-pack query and transformation-mask capture;
- skips fast-flight pose rotation;
- skips aura/stencil work;
- renders only armor, custom armor, race parts, skin, and hair layers.

Held items, capes, unrelated third-party layers, transformation masks, and aura effects are intentionally omitted from the tiny HUD render. Normal world rendering and DMZ menu previews remain unaffected.

Files:

- `../src/main/java/com/dragonminez/client/render/EntityPreviewRenderContext.java`
- `../src/main/java/com/dragonminez/client/render/DMZPlayerRenderer.java`

### 4. Looping sound allocation regression

Aura and flight sounds previously tested `SoundManager.isActive()` immediately after queueing a sound. The audio thread had not activated it yet, so the client created another looping instance every tick. One retest logged about 3,342 OpenAL source-pool failures in roughly fifteen seconds.

Ownership now uses `instance == null || instance.isStopped()`. A queued but not-yet-active sound remains owned, eliminating the allocation storm and its unrelated contribution to perceived client lag.

Files:

- `../src/main/java/com/dragonminez/client/events/ClientStatsEvents.java`
- `../src/main/java/com/dragonminez/client/events/SoundClientHandler.java`

### 5. Config, attachment, entity-data, and shader cleanup

- `GeneralServerConfig.GameplayConfig.maxValue` is now a nullable-safe `Long`; its public getter remains `Integer` and clamps to `[1000, Integer.MAX_VALUE]`. This allows JSON values such as `10000000000` to load instead of invalidating the full config.
- `StatsProvider.get(...)` delegates to the attachment-backed provider's existing cached optional rather than allocating a new `LazyOptional` wrapper on every lookup. This benefits tick, camera, and HUD hot paths.
- The invalid bulk `onSyncedDataUpdated(List<DataValue<?>>)` override in `AbstractKiProjectile` was removed. It compared the `SIZE` accessor to an entire list and could never refresh dimensions. The valid single-accessor callback remains.
- The unused `InSize` declaration was removed from `ki_composite.json`, matching the uniforms actually consumed by the current shader.

Files:

- `../src/main/java/com/dragonminez/common/config/GeneralServerConfig.java`
- `../src/main/java/com/dragonminez/common/stats/StatsProvider.java`
- `../src/main/java/com/dragonminez/common/init/entities/ki/AbstractKiProjectile.java`
- `../src/main/resources/assets/dragonminez/shaders/program/ki_composite.json`

### 6. Optional Sable UDP compatibility experiment

There is untracked optional compatibility source under:

- `../src/main/java/com/dragonminez/mixin/sable/`
- `../src/main/resources/dragonminez.sable.mixins.json`
- `../third_party/sable/README.md`

It detects official Sable through `dev.ryanhcode.sable.Sable`, uses Sable's packet enum length rather than a hard-coded ID bound, and suppresses the repeated invalid-packet exception path in client/server UDP handlers. It was designed for legacy ping/noise such as packet `0xFE` landing on Sable's UDP port.

Current status: this is experimental and **not active in the packaged mod**. `build.gradle.kts` currently registers only `dragonminez.mixins.json` in the `MixinConfigs` manifest entry. Do not claim the Sable fix ships unless `dragonminez.sable.mixins.json` is deliberately registered and retested with and without Sable installed.

Licensing warning: official Sable uses PolyForm Shield 1.0.0. Do not redistribute a competing patched Sable fork. If this compatibility work is retained, ship only the optional DMZ-side mixins and keep official Sable as the dependency.

## XenoPixelsNetwork changes

### 1. Character portrait is now the default

`XenoHudConfig` is at config schema version 3. New installs default to `PortraitMode.CHARACTER`, and migration from versions below 3 sets `portraitMode` to `character`. `reset()` and the serialized default do the same. The flat skin face remains available as an explicit option.

Config file: `config/xenopixelsmod-hud.json`

Files:

- `src/main/java/net/bullettrain/xenopixelsmod/client/config/XenoHudConfig.java`
- `src/main/java/net/bullettrain/xenopixelsmod/client/hud/XenoModernHudView.java`

### 2. FPS-friendly cached model portrait

`XenoModernHudView` delegates character drawing to the new `CharacterPortraitCache`.

The cache owns one 128x128 transparent `TextureTarget`. It renders the DMZ player model at most once per player tick or when its appearance hash changes, then performs one textured quad blit on other render frames. This changes the expensive animated model/layer render from display-frame frequency to at most 20 Hz while preserving normal HUD frame rate.

The appearance hash covers:

- skin texture;
- portrait scale and offset;
- all equipped non-hand armor/body slots;
- race, active form, render-logic key, hair ID/color;
- resolved model scale on all axes.

The target is destroyed on logout, client-player clone, and resource reload. A render failure is logged once and switches to the clipped direct-render fallback instead of repeatedly throwing or allocating resources.

The portrait saves, pins, and restores both previous and current body/yaw/pitch/head rotations. Pinning both interpolation endpoints fixes the flicker that disappeared only while chat captured the mouse. The `finally` restore prevents the temporary portrait pose from leaking into world/F5 rendering.

Files:

- `src/main/java/net/bullettrain/xenopixelsmod/client/hud/CharacterPortraitCache.java` (currently untracked; must be added before committing)
- `src/main/java/net/bullettrain/xenopixelsmod/client/hud/XenoModernHudView.java`

### 3. Giant-form portrait fitting, including Oozaru

The fit calculation uses DMZ's resolved X/Y/Z model scale and divides portrait scale by the largest axis. Vertical translation uses a stable base model height of `1.9` rather than the already-scaled entity bounding-box height, avoiding double application of form scale.

DMZ Oozaru has additional intrinsic geometry scaling that is not represented like ordinary forms. Detection uses the render-logic key and Saiyan Oozaru/Golden Oozaru form IDs. The implementation removes the resolved scale's `+2.8` contribution and applies the renderer's intrinsic `3.8x` scale before fitting. This is the final follow-up specifically intended to stop Oozaru's chest from filling the portrait well.

The float-scale DMZ preview API is essential here because the correct fitted scale can be below 1.

File: `src/main/java/net/bullettrain/xenopixelsmod/client/hud/CharacterPortraitCache.java`

### 4. KI size and speed ceilings

New common mixins override DMZ `KiAttackData.getMaxSizeForType` and `getMaxSpeedForType` so creator/debug values are no longer restricted by DMZ's smaller technique-specific maxima. The limits come from synchronized Xeno server config:

| Field | Default | Meaning |
| --- | ---: | --- |
| `kiProjectileMaxSize` | `320.0` | absolute creator/runtime visual-size ceiling |
| `kiProjectileMaxSpeed` | `32.0` | absolute creator/runtime speed ceiling |
| `kiOverchargeSpeedPerPercent` | `0.004` | speed growth per release point above threshold |
| `kiFullGameplayScaling` | `false` | opt-in uncapping of damage/explosion growth |
| `kiDestructionMaxRadius` | `32.0` | independent explicit block-destruction radius cap |
| `kiDestructionBlocksPerTick` | `4096` | synchronous cube-check budget, minimum 64 |

All size and speed inputs are checked for finiteness and clamped to at least `0.1` and at most the configured ceiling. This applies both to overcharge and beam-surge growth, so debug value `220` is supported without allowing NaN/infinite values or unbounded runtime growth.

Overcharge now scales existing projectile velocity as well as projectile size. Damage and explosion growth keep the existing `kiOverchargeMaxScale` cap unless `kiFullGameplayScaling=true`; the toggle defaults off to separate large visuals from unexpectedly extreme combat balance.

Files:

- `src/main/java/net/bullettrain/xenopixelsmod/config/XenoServerConfig.java`
- `src/main/java/net/bullettrain/xenopixelsmod/event/KiOverchargeHandler.java`
- `src/main/java/net/bullettrain/xenopixelsmod/combat/beam/BeamSurgeManager.java`
- `src/main/java/net/bullettrain/xenopixelsmod/mixin/common/KiAttackDataLimitsMixin.java`
- `src/main/resources/xenopixelsmod.mixins.json`

Server config file: `config/xenopixelsmod-server.json`

### 5. Bounded block destruction

Visual size, collision, and optional gameplay scaling must not imply an enormous synchronous block scan. DMZ scans a cube, so Xeno converts the configured check budget back into a safe radius:

```text
budgetRadius = (cuberoot(kiDestructionBlocksPerTick) - 1) / 2
effectiveRadius = min(kiDestructionMaxRadius, budgetRadius)
```

At the default budget of 4,096 checks, the effective limit is approximately radius 7.5 even though the explicit radius maximum defaults to 32. The mixin caps DMZ's returned destruction radius independently, including when full gameplay scaling is enabled.

Files:

- `src/main/java/net/bullettrain/xenopixelsmod/mixin/common/KiDestructionRadiusMixin.java`
- `src/main/java/net/bullettrain/xenopixelsmod/config/XenoServerConfig.java`
- `src/main/resources/xenopixelsmod.mixins.json`

### 6. Beam distance debug display

The existing beam-surge debug overlay now appends the live wave length from `KiWaveEntity.getClashBeamLength()` as:

```text
distance=N.N blocks
```

It shows `-` when no current wave exists.

File: `src/main/java/net/bullettrain/xenopixelsmod/client/combat/beam/BeamSurgeClient.java`

### 7. Config synchronization and wire compatibility

The six new KI fields are serialized and decoded in the same order by `SyncServerConfigPacket`. Xeno's strict network protocol changed from `16` to `17`, so clients and servers with the old packet layout are rejected rather than silently reading shifted data.

Files:

- `src/main/java/net/bullettrain/xenopixelsmod/network/ModNetwork.java`
- `src/main/java/net/bullettrain/xenopixelsmod/network/SyncServerConfigPacket.java`

## Seated flight: aerodynamics, flaps and the pilot seat (2026-08-17)

This is the flight-controller work that was left half-finished, now completed and extended.

### 1. The aerodynamic layer is connected

`AeroAeroModel` (lift/drag/angle of attack/stall with flap coefficients) and `AeroAeroForceSystem`
(the Sable physics-tick system that applies it) existed but were never called, and the force system
did not compile — line 101 declared `double ax = ax`, a self-referential local. Both are fixed and
wired in:

- `AeroBus.advanceFlaps(deltaSeconds, airspeed)` moves flaps toward their commanded setting at
  `AeroConfig.flapSpeedPerSec` and, with auto-flap on, derives that setting from airspeed
  (`autoFlapExtendSpeed` 8.0, `autoFlapRetractSpeed` 20.0 — new config fields).
- `AeroFlightCore` is the shared per-tick flight maths: advance flaps, publish the world-space
  aerodynamic acceleration, command attitude, distribute thrust. It owns the scratch vectors, so the
  tick still allocates nothing. Both the flight controller and the pilot seat run it, so ship
  behavior cannot diverge between them.
- Every disengage path (`AeroFlightCore.release`) now clears the aerodynamic force as well as the
  stabilizer. Both are latched on the physics thread; releasing one without the other left a
  disengaged ship generating lift.
- `AeroAeroModel.angleOfAttackDeg` was added so a stall can be reported without re-deriving geometry.
- The flight tab of `FlightPlannerScreen` gained throttle, flap and auto-flap controls, and shows
  live throttle/flap in telemetry. Before this, `SET_THROTTLE` / `SET_ATTITUDE` / `SET_FLAP` were
  wire-reachable with no client sender at all.

### 2. `AeroControlHost` — the dispatcher is no longer tied to one block

`AeroActionDispatcher` now takes an `AeroControlHost` instead of `ShipVlsGuidanceBlockEntity`. That
is the only change it needed: validation, clamping, power gating and the authoritative echo are
untouched and still the single choke point for the GUI, the physical panel, the CC peripheral and
now the seat. `dispatchQuiet` is the same validation without the trailing `syncAero()`, for the
per-tick pilot stream; the seat pushes state to its own pilot on a cheaper cadence instead.

Engine pairing moved to `AeroLinkManager.pairNearby` / `clearPaired` so both hosts claim engines
identically. Ownership is recorded on the thruster as a `BlockPos`, which is why any positioned host
can own engines.

### 3. The pilot seat, in both modes

- `PilotSeatBlock` + `PilotSeatBlockEntity` + `XenoPilotSeatEntity` (`aero/seat/`). Right-click to
  sit; the block spawns an invisible, physics-less seat entity the player rides. Sable already
  carries entities on moving sub-levels, so the seat has no transform code of its own — **this is
  the assumption most in need of in-game confirmation.**
- **Bound mode:** a seat within 8 blocks of a flight controller flies that controller. It does not
  touch its engagement — an operator may have engaged flight or an autopilot from the GUI, and
  sitting down must not silently re-arm or cancel it.
- **Standalone mode:** with no controller in range the seat block itself is the host — its own bus,
  FE buffer (exposed as an energy sink) and engine pairings. Manual flight only, no autopilot or
  routes; it engages when a pilot sits and emergency-stops when they leave.
- Safety: `AeroSeatInput` goes stale after 10 ticks without a frame and the seat then commands idle,
  so a pilot who disconnects at full throttle does not leave a ship climbing.

### 4. Pilot input and HUD

- `SeatFlightInputPacket` (C2S, protocol `25` → `26`): seat entity id, attitude as hundredths of a
  degree in shorts, throttle/flap percent bytes, one flag byte. About ten bytes. Validated by
  "you are the seat's controlling passenger" (stronger than a reach check), a per-player one-frame
  interval, finite/range clamps, then the dispatcher's own rules.
- `FLAG_FLAP_SET` marks a frame where the pilot actually moved the flap control. Without it the
  per-tick push would cancel auto-flap permanently, since setting flaps by hand turns it off.
- `XenoFlightControls`: Q/E roll, G flap stage, K mouse-aim toggle, X centre, Z air brake, vanilla
  W/S/A/D for throttle and yaw. Mouse-aim commands the heading you are *looking* at and lets the
  existing PD stabilizer fly the ship there — no cursor capture and no mouse mixin, so chat,
  inventory and death cannot leak mouse state. With a screen open the controls freeze but the
  stream keeps running, so typing in chat neither flies the ship nor drops the throttle.
- `XenoFlightHudOverlay` renders only the authoritative snapshot cached in `ClientFlightState`:
  throttle, flap travel with a target tick, speed, altitude, mode and the single worst warning.

### 5. Sub-level tagging — why the seat was not hull-relative

First live test showed the seat ignoring ship orientation. The cause is not our code: Sable decides
per **entity type**, through the `sable:retain_in_sub_level` tag. `EntitySubLevelUtil.shouldKick`
is exactly `!type.is(SableTags.RETAIN_IN_SUB_LEVEL)`, and an entity that should be kicked is ejected
into world space instead of living in the sub-level's own coordinate frame — so it stops moving
with the hull and its rider renders world-upright however the ship is banked or inverted.

Sable's own copy of that tag lists `create:seat` and `blockbox:seat`; DragonMineZ ships its own
copy of the same tag file. Xeno now does too:

- `data/sable/tags/entity_type/retain_in_sub_level.json` — keeps the seat in the sub-level, which
  is what makes Sable's `entity_rotations_and_riding` render path apply the hull orientation to
  the seat and, by vehicle inheritance, to the pilot.
- `data/sable/tags/entity_type/destroy_with_sub_level.json` — the seat does not outlive its craft.
- `datapacks/xeno_ship_masses/.../pilot_seat.json` — `sable:mass` 1.5, matching the other blocks.

Consequence for future work: the seat's rotation is stored **hull-relative** (the seat block's
facing) and Sable applies the hull pose at render time. Do not add world-space rotation to the
seat entity — it would be applied twice.

### 6. Flight HUD warning row

The warning shared the speed row and was right-aligned, so a four-digit altitude and a word like
`DISENGAGED` drew through each other (`ALTDI3000AGED`). The warning now has its own line and the
panel is sized for it.

### 7. Wing Panel — real per-block lift (`xenopixelsmod:wing_panel`)

Flaps are a whole-ship coefficient and were never a placeable object, which is why nothing flap-shaped
appeared in the creative tab. There is now a block that *is* an aerodynamic surface.

`WingPanelBlock implements dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider`. That interface is
the entire integration and it is Sable's own supported extension point — `ServerLevelPlot` keeps a
`Long2ObjectMap` of lift providers, updated as blocks are placed and broken, and `ServerSubLevel`
groups them and applies combined lift and drag at each group's centre every physics tick. Sable uses
exactly the same mechanism to make Create's sails lift (`sails_providing_lift/SailBlockMixin`, which
likewise implements only `sable$getNormal` and keeps the default scalars — so this block does too:
lift 0.475, parallel drag 0.75, directionless drag 0.0689).

- `FACING` is the surface normal, taken from the clicked face, so laying panels along the top of a
  hull gives upward lift without thinking about it. A panel on the side is a fin.
- **No double lift.** `AeroFlightCore.baseLiftFactor` drops the lumped hull lift to zero as soon as a
  ship has any lift provider (ours, Create's, anyone's) — otherwise the same lift is counted twice
  and winged craft float absurdly. Flap lift is deliberately *not* scaled: flaps are a high-lift
  device on top of whatever wing exists, so they still work on a winged craft. Config
  `aeroModelBaseLiftWithWings` (default false) restores the old behavior for A/B comparison.
- `DEFLECTED` is **visual only**, toggled when the ship's flaps cross halfway, bounded to 512 panels
  and written only on a crossing — block writes re-render chunks of the ship. Positions are copied
  before writing because `setBlock` mutates the plot's lift-provider map that is being iterated.
  The aerodynamic effect of flaps stays in the controller's model: Sable's lift scalars are
  per-block-type and cannot vary per ship or per tick.

### 8. Create and Create: Aeronautics in the dev run

Create was only ever a `compileOnly` dependency, so a dev run had no Create, no Aeronautics and no
sails to compare against. NeoForge's dev run *does* scan `<runDir>/mods`, so the real jars are now
dropped there by `scripts/setup_dev_mods.ps1`:

- `create-1.21.1-6.0.10.jar` — jar-in-jars Flywheel 1.0.6, Ponder 1.0.82, Registrate
- `create-aeronautics-bundled-1.21.1-1.3.0.jar` — jar-in-jars Aeronautics, Offroad, Simulated

Both versions match the live server's list in `servermods.txt`. Note `build.gradle` compiles against
Create `6.0.11-295` while the runtime is `6.0.10`; the dedicated server boots clean with both present
and no mixin failures, including our Create-targeting `SableContraptionColliderMixin`. `run/` is
gitignored, so the script must be re-run on a fresh clone.

### 9. What the shipped sail implementations taught, and what changed because of it

Create Simulated's `SymmetricSailBlock` is the same Sable integration written by the physics
engine's own authors, and reading it corrected two things in the wing panel:

- **Axis, not facing.** It extends `RotatedPillarBlock` and returns
  `Direction.get(POSITIVE, state.getValue(AXIS))` as its normal. An aerodynamic surface is
  symmetric — which of the two opposite normals is "the" normal has no meaning — so a six-way
  facing was over-specified and let two identical panels disagree about their own geometry. The
  wing panel now carries `AXIS`, halving its state count.
- **Placed by look direction**, via `context.getNearestLookingDirection().getAxis()`, not by the
  clicked face. Panels line up with how the builder is looking, so a row placed while walking along
  a wing comes out parallel. The shape is also centred in the block, as a symmetric surface should be.

Deliberately *not* copied: the symmetric sail returns `sable$getLiftScalar() = 0` with
`sable$getParallelDragScalar() = 1.75`. It is a sail — a drag surface that catches wind. A wing is
the opposite instrument, so the panel keeps Sable's defaults (lift 0.475), which is what Sable's own
Create-sail lift mixin uses for a lifting surface.

Not implemented, and worth considering later: Create's `IWrenchable` (wrench rotation) and catnip's
`IPlacementHelper` (click-drag to lay a whole row), both of which the sail blocks have. Both would
make Create a hard requirement at class-load unless gated behind a `ModList.isLoaded("create")`
compat class, the way the ComputerCraft peripheral already is.

### Still unverified — needs a `runClient` session

Build, tests and dedicated `runGameTestServer` init all pass, but none of this has been flown:

1. A ridden seat stays put on a moving ship (the Sable assumption above).
2. Flaps ease rather than snap; auto-flap extends slow and retracts at cruise.
3. Lift/drag actually helps: less throttle needed to hold altitude with speed, stall past
   `stallAoADeg`, and `airDensity=0` returns exactly to the old thrust-vectoring feel.
4. Disengage, power loss and emergency stop each release lift as well as attitude.
5. Mouse-aim and keyboard flight both fly; dismount mid-throttle stops the ship.
6. Dedicated server: no client class loading, no packet spam, old clients refused at protocol 26.
7. Wing Panel: appears in the creative tab, places with the surface facing the clicked face, and a
   hull with panels on top actually flies differently from a bare one (base hull lift steps aside,
   so a panel-less brick should now be noticeably worse at holding altitude than a winged craft).
8. Extending flaps past 50% visibly tilts the panels, and retracting returns them.
9. Side by side with Create Simulated's symmetric sails on the same hull: sails should add drag and
   no lift, wing panels should add lift. Both feed the same Sable pass, so a hull carrying either
   drops the lumped hull-lift term.

## Port-audit conclusions

The full `0e336bcd..HEAD` changed-file screen covered roughly 801 DragonMineZ files. Manual review concentrated on migrations most likely to compile while changing behavior:

- Forge tick phases versus NeoForge `Pre`/`Post`; migrated client, player, level, server, and render listeners preserve their intended START/END ordering.
- SimpleChannel versus NeoForge payload networking; packet work is enqueued appropriately and the changed Xeno config layout is protocol-gated.
- Forge capabilities versus NeoForge data attachments; stats serialization uses the holder lookup and hot access now reuses the cached provider optional.
- Synced entity data and dimension refresh; the correct accessor callback remains and the impossible bulk comparison is gone.
- Logical-side behavior; every `CameraAimHelper.resolve` call was classified as shared tick code or server-authoritative launch code.
- Current logs; actionable config, OpenAL allocation, and KI shader warnings were fixed. Development refmap warnings, optional Iris/Veil messages, existing texture warnings, and vanilla command ambiguity were not treated as DMZ port regressions without evidence.

The dedicated-server smoke test emitted non-fatal `ClientLevel` dist-cleaner warnings after Sable selected its renderer mixins. Xeno's common KI mixins do not reference `ClientLevel`, both applied, and server loading continued. Treat this as a Sable/dependency warning unless a clean reproduction proves otherwise.

## Verification already completed

- DragonMineZ `gradlew.bat build` passed, including `runData`.
- XenoPixelsNetwork `gradlew.bat build` passed against the freshly rebuilt DragonMineZ artifact.
- Xeno tests passed.
- Dedicated `runGameTestServer` initialization passed for DragonMineZ and for the combined mods. The final “No test functions were given” message is expected because there are no registered game tests.

These builds were completed after the final Sokidan active-tracking changes and after the final Oozaru intrinsic-scale portrait fix.

## Manual client tests still required

Run a fresh XenoPixelsNetwork `runClient`; do not reuse an already-running client after rebuilding dependencies.

1. Cast and control Sokidan while turning the camera quickly. Steering should follow the crosshair every tick without half-second snaps, stale four-second aim, or rubber-banding.
2. Test Sokidan near walls and at range. Confirm server collision/removal wins and there is no release-frame freeze or obvious launch pop.
3. Observe another player's controlled Sokidan to confirm remote movement is acceptably smooth.
4. Watch a charging KI ball while turning and confirm it stays synchronized with the owner.
5. Test normal Blast, Laser, Disk, and Wave release direction, with Aero Cam Sync if available.
6. Use the character portrait, turn rapidly, open/close chat, and switch F5 modes. The portrait must remain static and the world model must retain its normal rotations afterward.
7. Transform through normal, giant Namekian, large Arcosian/Frost Demon/Metal, Oozaru, and Golden Oozaru forms. The entire relevant model should fit the portrait rather than showing only the chest.
8. Compare HUD FPS and frame timing with the character portrait enabled. The 3D render should refresh once per player tick while the cached texture blits every frame.
9. Enter KI size `220`, test speed growth, and verify the configured size/speed ceilings on both integrated and dedicated servers.
10. Enable beam-surge debug and confirm `distance=N.N blocks` changes with live beam length.
11. Exercise large attacks with block destruction and confirm server tick time stays bounded.

## Build order

From the parent DragonMineZ directory:

```powershell
.\gradlew.bat build
```

Then from this XenoPixelsNetwork directory:

```powershell
.\gradlew.bat build
```

Build DragonMineZ first whenever its public preview API or projectile behavior changes, because Xeno compiles against the rebuilt DMZ artifact.

## Commit/push cautions

- Nothing in this handoff has been committed or pushed as part of creating this document.
- Add `CharacterPortraitCache.java` explicitly; it is untracked and otherwise will be omitted from a commit.
- Decide separately whether to keep and register the experimental Sable files. They are not required for the Sokidan, portrait, KI-limit, or performance fixes.
- Do not include screenshots, logs, archives, `plans/`, extracted source trees, or temporary build/debug files in either commit.
- Keep DMZ and Xeno changes in their respective repositories/branches. A parent `git status` shows the entire Xeno directory as untracked because it is a nested worktree/repository; do not add it to the DMZ commit.

## Complete task-related file manifest

DragonMineZ tracked modifications:

```text
src/main/java/com/dragonminez/client/events/ClientStatsEvents.java
src/main/java/com/dragonminez/client/events/ForgeClientEvents.java
src/main/java/com/dragonminez/client/events/SoundClientHandler.java
src/main/java/com/dragonminez/client/render/DMZPlayerRenderer.java
src/main/java/com/dragonminez/client/render/EntityPreviewRenderContext.java
src/main/java/com/dragonminez/client/render/camera/OverShoulderCamera.java
src/main/java/com/dragonminez/common/compat/CameraAimHelper.java
src/main/java/com/dragonminez/common/config/GeneralServerConfig.java
src/main/java/com/dragonminez/common/init/entities/ki/AbstractKiProjectile.java
src/main/java/com/dragonminez/common/init/entities/ki/KiBlastEntity.java
src/main/java/com/dragonminez/common/init/entities/ki/KiDiskEntity.java
src/main/java/com/dragonminez/common/init/entities/ki/KiLaserEntity.java
src/main/java/com/dragonminez/common/network/C2S/SokidanControlC2S.java
src/main/java/com/dragonminez/common/stats/StatsProvider.java
src/main/resources/assets/dragonminez/shaders/program/ki_composite.json
```

XenoPixelsNetwork task files:

```text
src/main/java/net/bullettrain/xenopixelsmod/client/combat/beam/BeamSurgeClient.java
src/main/java/net/bullettrain/xenopixelsmod/client/config/XenoHudConfig.java
src/main/java/net/bullettrain/xenopixelsmod/client/hud/CharacterPortraitCache.java
src/main/java/net/bullettrain/xenopixelsmod/client/hud/XenoModernHudView.java
src/main/java/net/bullettrain/xenopixelsmod/combat/beam/BeamSurgeManager.java
src/main/java/net/bullettrain/xenopixelsmod/config/XenoServerConfig.java
src/main/java/net/bullettrain/xenopixelsmod/event/KiOverchargeHandler.java
src/main/java/net/bullettrain/xenopixelsmod/mixin/common/KiAttackDataLimitsMixin.java
src/main/java/net/bullettrain/xenopixelsmod/mixin/common/KiDestructionRadiusMixin.java
src/main/java/net/bullettrain/xenopixelsmod/network/ModNetwork.java
src/main/java/net/bullettrain/xenopixelsmod/network/SyncServerConfigPacket.java
src/main/resources/xenopixelsmod.mixins.json
```

`magical-wobbling-lake.md` contains the earlier investigation chronology. Use this file as the authoritative current-state handoff.

## Update 2026-08-30 — CustomNPC scripting/animation follow-up and Ki Clash investigation

This section records only repository evidence checked during this follow-up. The worktree is
already dirty with unrelated changes; do not reset, clean, or commit unrelated files as part of
this handoff.

### Verified locally

- `./gradlew compileJava test` completed successfully (8 tasks). This is compile/unit-test
  evidence only; it is not a live server or client validation.
- `NpcGeckoAnim.playAttack(LivingEntity)` reads the Gecko CustomNPC add-on's configured
  `CustomModelData.getAttackAnim()` and uses the add-on's existing
  `NetworkWrapper.sendAll(new PacketSyncAnimation(...))` route. If no attack clip is configured,
  it returns false.
- `NpcKiAim.startCastAnimation` now calls `swing(MAIN_HAND, true)`, then the Gecko attack clip,
  then the existing short `AIM` pose. The Model Editor must actually contain an attack animation
  for the custom hand motion to be visible.
- `NpcXenoScriptApi` is installed reflectively only when `customnpcs` is loaded and is exposed to
  CustomNPC scripts through the verified public `ScriptContainer.Data` map as `XenoPixels`.
  Its methods and signatures are in the source; do not assume additional CustomNPC or DMZ APIs.
- `/xenopixels scriptapi globals`, `all`, `search`, `show`, and permission-2 `dump` were added.
  The catalog scans the loaded CustomNPC jar; production class-loader behavior still needs an
  in-game check.
- `NpcKiCooldowns` uses DMZ `KiAttackData.getActualCooldown()` and the same ceil/charge-factor
  calculation observed in DMZ's player cooldown path. NPC cooldowns are currently in-memory and
  are **not NBT-persistent**.
- `NpcTransformSystem.start` no longer writes the target form immediately; the target is written
  on transition commit. This addresses the observed instant-SSJ appearance, but the complete
  visual sequence still needs live verification.
- Hair persistence currently uses the Gecko add-on `CustomModelData` NBT keys handled by
  `NpcHairBridge`; verify save/reload and client synchronization in-game.
- Chase-flight smoothing was changed to server velocity/interpolation with no-gravity restore;
  moving targets, collisions, latency, and logout/death paths still need a client/server test.

The attempted dedicated-server smoke test was stopped by an unrelated Sable native DLL
`java.nio.file.AccessDeniedException` (`.sable/natives/sable_rapier_x86_64_windows.dll`), so no
claim of a clean full-server boot is made here.

### Required investigation: Ki Deflection causing an incorrect Ki Clash

Please reproduce and diagnose the report: when a player uses the Ki Deflection skill against an
incoming ki attack, a Ki Clash may start or behave incorrectly. Treat the following as a test
plan, not as a claimed root cause:

1. Establish a beam-vs-beam clash baseline with no deflection.
2. Deflect an incoming `AbstractKiProjectile` before any clash begins.
3. Deflect while the projectile is near/inside the clash window.
4. Let the deflected projectile cross its original caster and a second beam.
5. Check for a false clash, missing clash, frozen/immortal beam, vanishing beam, wrong damage
   owner, stale `clashLocked` state, or server exceptions.
6. Capture the exact projectile class, tick order, owner, homing target, and clash-manager state
   before and after deflection. Add a regression test or GameTest/integration reproduction if the
   test harness permits it.

Inspect the actual DMZ 2.1.3 bytecode/source for `BeamClashManager`, `BeamClash`, and
`AbstractKiProjectile`, plus Xeno's `KiDeflect` and `BeamSurgeManager`. `KiDeflect` currently
filters `isClashLocked()`/`isClashableBeam()`, then changes velocity, owner, and homing target;
verify whether that leaves a registered clash entry or changes identity in a way DMZ does not
expect. Only use a detach/reset API if it is confirmed in the jar/source. If no safe public API
exists, document that and choose a guarded behavior rather than inventing a method.

Do not call CustomNPC `onAttack` as an animation shortcut: its side effects can alter aggro/target
state. Do not claim cooldown persistence, live animation visibility, complete chase smoothness,
or a fixed Ki Clash until those behaviors are reproduced and tested. Use `javap`, the checked-in
sources, or the actual dependency jar to verify every external symbol before changing code.
