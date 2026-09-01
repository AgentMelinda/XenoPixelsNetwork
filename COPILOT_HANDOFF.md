# Copilot handoff: seated flight — flaps, aero torque, camera, wrench

## What this mod is

Grounded in what was actually observed this session (mod-id/name from the live crash-log mod list,
package layout, lang file, class names) — not a full audit of the whole codebase, which is much
larger than what this session touched.

**`xenopixelsmod`**, displayed in-game as **"XenoPixels Network"** (currently version
`0.1.9-1.21.1`), is a NeoForge 1.21.1 mod built for a Minecraft server network ("DMZ") as an addon
on top of **DragonMineZ** (`dragonminez`, a real compile-time dependency, currently `2.1.3`) — a
Dragon Ball–inspired combat mod (ki blasts, transformations, techniques). XenoPixels layers two
mostly-separate feature sets on top of DragonMineZ:

1. **Combat/HUD extensions in the DragonBall FighterZ / Xenoverse style** — a technique/hotbar
   system, beam-surge (charged beam clash) mechanics, ki-guidance projectile steering, dash/vanish
   combat helpers, a party system with its own HUD, cooldown overlays, and a fair number of
   `compat/` mixins patching specific DragonMineZ classes (camera behavior, projectile lifetimes,
   damage/grief sourcing, etc.). **None of this was touched this session** — it's pre-existing,
   large, and mostly what `CLAUDE_HANDOFF.md`/`QWEN_HANDOFF.md` document.
2. **A block-ship building and flight system**, built on the **Sable** physics engine
   (`dev.ryanhcode.sable`, the spiritual successor to Valkyrien Skies 2 — build a ship out of
   blocks, it becomes its own rigid body). This is what this session's work is entirely about:
   pilot seats, flight controllers, wing panels with real aerodynamics, thrusters, missiles and
   ballistic guidance computers, fleet/fire-control management, and the aero-tuning config that
   drives all of it (`AeroConfig`).

It also carries a long list of soft-dependency compatibility shims for other mods in the pack it's
meant to run in — Create and Create: Aeronautics (ship parts, elevators), CustomNPCs (a Mixin
access-error workaround), Xaero's Minimap/Worldmap, YAWP (region protection), AeroStar, and others
— following a consistent pattern of never hard-depending on any of them (see "Standing conventions"
below).

Written: 2026-08-19 (session spanned roughly 2026-08-17 through 2026-08-19 wall-clock in the user's
environment). Scope: **only the work done in the Claude session that produced this file.** This
repo's working tree has a large amount of other uncommitted work (combat, HUD, party system,
DragonMineZ port fixes) from earlier, separate sessions — see `CLAUDE_HANDOFF.md` and
`QWEN_HANDOFF.md` for that. Do not assume this document describes the whole repo state; `git status`
shows far more modified/untracked files than are listed here, and most of them are not mine.

**Nothing in this document has been confirmed working in a live game by me.** Every item below was
compiled (`./gradlew compileJava compileTestJava test --offline`, clean each time) and verified
present in a freshly built jar (`jar tf`, checked against a fresh timestamp — this project has a
history this session of Gradle's UP-TO-DATE cache lying about what actually got built, so every
ship was gated on that check). The user tested some earlier pieces (see "What the user actually
confirmed" below) but never confirmed the later, larger batch of changes (real per-panel torque,
auto-mirroring, DMZ camera fix, wrench rotation, third-person toggle) in a running client. Treat
all of it as **implemented and believed-correct, not verified.**

## Stack facts (verified this session, not guessed)

- NeoForge 1.21.1 (21.1.238 compiled against; live instances report NeoForge 21.1.248), Java 21.
- Physics: `dev.ryanhcode.sable`, `sable-common-1.21.1-1.2.1.jar` + `sable-companion-common-1.21.1-1.6.0.jar`
  (real jar paths under `~/.gradle/caches/modules-2/files-2.1/dev.ryanhcode.sable*`). A **required**
  dependency (`implementation`, not optional) — direct `dev.ryanhcode.sable.*` imports are the norm
  throughout `aero/`, unlike Create/CustomNPCs which are soft dependencies.
- Create (`create-1.21.1-*`) is `compileOnly` and **optional** at runtime — `neoforge.mods.toml`
  declares it `type="optional"`. Any code that must work without Create either uses a
  `Class.forName`/reflection probe (`ContraptionControlCamera.java`) or matches on the item's
  registry name string with zero Create class references at all (`CreateWrenchHandler.java`). Never
  add a hard `import com.simibubi.create...` to always-active code.
- Ctrl key has no vanilla client→server sync; every "hold a modifier" interaction in this codebase
  uses Shift (`Player.isShiftKeyDown()`) instead, established and reused all session.

## What the user actually confirmed this session

- The wing-panel flap-animation rewrite (continuous angle + client chase + dynamic
  `BlockEntityRenderer`, replacing the old 3-state blockstate-swap) — no explicit bug report against
  it after it shipped, but also no explicit "this works" confirmation either.
- The `requirePower` stale-config problem: the user reported "chair still says no power" after I'd
  already flipped `AeroConfig.requirePower`'s default to `false` in code; the real cause was that
  their already-existing `config/xenopixelsmod-aero.json` (client instances *and* the `run/`
  dev-run folder) had `requirePower: true` saved from before, which `AeroConfig.load()` reads and
  which overrides the new code default. I patched those JSON files directly. **Not re-confirmed
  after the patch.**
- A real, user-caught crash investigation: a NeoForge parallel-mod-construction `NullPointerException`
  in `FMLModContainer.handleMixinError` on one launch of a ~150-mod pack (ModrinthApp "Forge 1.20.1"
  profile — the name is stale/inherited, it's actually NeoForge 1.21.1). A relaunch succeeded and the
  client ran stable for ~2 hours afterward. Traced as a known category of intermittent NeoForge
  parallel-construction race, not caused by anything in this session's code (none of it touches
  Mixin). Not something I "fixed" — just diagnosed and ruled out as unrelated.

## What was implemented (chronological, by request)

### 1. Wing-panel flap animation: discrete → continuous

**Problem:** flaps only ever had `NONE/UP/DOWN`, swapped via blockstate, which can only ever show an
instant snap — no way to render an in-between angle with static blockstate/model JSON alone.

**Fix:**
- `block/entity/WingPanelBlockEntity.java` (new) — server-authoritative `targetDeflectDeg` (double,
  degrees), plus client-only exponential-chase animation state (`clientDeflectDeg`/
  `clientPrevDeflectDeg`, `CHASE_RATE=0.35`), and `getAnimatedDeflectDeg(partialTicks)` for
  sub-tick-smooth rendering via `Mth.lerp`.
- `block/custom/WingPanelBlock.java` — converted from `RotatedPillarBlock` to `BaseEntityBlock`
  (needed the block entity; Java has no multiple inheritance, so `rotate`/`mirror` were hand-written
  to replicate `RotatedPillarBlock`'s WorldEdit/structure-rotation behavior). `getRenderShape`
  returns `RenderShape.MODEL` when `ROLE == NONE` (baked into the chunk mesh, costs nothing) and
  `RenderShape.ENTITYBLOCK_ANIMATED` otherwise (drawn per-frame by the renderer below) — this split
  is why hundreds of structural panels don't cost anything, only the handful actually assigned a
  control role do.
- `client/render/WingPanelBlockEntityRenderer.java` (new) — reads the live angle, applies an
  axis-dependent outer rotation (matching the block's `AXIS` property) plus the deflection rotation
  via `PoseStack`, then tesselates the block's own baked model directly via
  `BlockRenderDispatcher.getBlockModel(state)` + `getModelRenderer().renderModel(...)` — **not**
  `renderSingleBlock`, which was tried first and found to route to the item renderer for
  `ENTITYBLOCK_ANIMATED` states instead of the block model; this was caught and fixed before
  shipping.
- `blockstates/wing_panel.json` — simplified; role-assigned variants deliberately carry **no** baked
  `x`/`y` rotation (the renderer supplies 100% of it), while `ROLE=NONE` variants still bake it in
  (they use the plain chunk-mesh path with no renderer to apply it). Getting this backwards was a
  real bug caught mid-session (a role-panel's blockstate variant and the renderer's own PoseStack
  rotation would have doubled up) — if you see wing panels rendering at double/wrong angles, check
  this split first.
- `aero/control/AeroFlightCore.java`'s `updatePanelDeflections`/`deflectFor`/`fromError` now compute
  a continuous degree value (role-scaled: `FLAP` 0–30° from throttle-independent flap%, `PITCH`/
  `ROLL`/`YAW` scale with attitude error past a deadband, `BRAKE` on/off at 30°) instead of picking
  an enum, and call `WingPanelBlockEntity.setTargetDeflectDeg` instead of `level.setBlock`.
- `PanelDeflect.java` (the old enum) deleted; `PanelConfiguratorItem.java`'s `DEFLECT` reference
  fixed to reset `WingPanelBlockEntity.setTargetDeflectDeg(0.0)` instead.

### 2. Keybind fixes: discoverable throttle, Q/E → roll

- `THROTTLE_UP`/`THROTTLE_DOWN` `KeyMapping`s added in `client/flight/XenoFlightControls.java`
  (unbound by default, alongside the pre-existing scroll-wheel throttle, not replacing it) so
  throttle has a real entry in the Controls menu — the scroll wheel itself can't be bound as a
  `KeyMapping` at all.
- **Q/E now roll** (a second, additive roll input alongside A/D), not yaw. This was a genuine
  back-and-forth: the code originally had Q/E as roll, was changed to yaw at some point before this
  session (with a comment explaining yaw had no keyboard control otherwise), and the user explicitly
  asked to revert to Q/E=roll, accepting that yaw now has **no default keyboard binding at all** —
  mouse-aim is the only way to command heading directly. `ROLL_LEFT`/`ROLL_RIGHT` fields (renamed
  from `YAW_LEFT`/`YAW_RIGHT`), translation keys `key.xenopixelsmod.flight_roll_left`/`_right`
  (these already existed, unused, in `lang/en_us.json` — leftover from an earlier iteration of this
  exact layout).

### 3. Per-panel deflection invert + auto-mirroring

- `WingPanelBlock.INVERT` (new `BooleanProperty`) — shift+right-click with the Panel Configurator
  toggles it, independent of the plain-click role cycle. `AeroFlightCore` negates the computed
  degree when set.
- Later extended to **auto-mirror**: for `ROLE == ROLL` only, `updatePanelDeflections` computes
  which side of the hull the panel is on via `ship.getMassTracker().getCenterOfMass()` (verified
  real API: `ServerSubLevel.getMassTracker()` → `MassData.getCenterOfMass()` → `Vector3dc`, already
  used elsewhere in this codebase, e.g. `ShipBallisticController.java`) dotted against the
  **un-rotated, ship-local** right axis (`bodyNose × bodyUp`, computed before the existing
  world-space transform overwrites those scratch vectors — panel `BlockPos` and Sable's CoM are
  both already in that same ship-local frame). The manual `INVERT` toggle still works as an override
  on top. `PITCH`/`YAW` panels deliberately do **not** auto-mirror — real elevators/rudders are
  same-sign surfaces, not left/right pairs.

### 4. Real per-panel control-surface torque (the big one)

**Problem:** the ship's actual rotation came entirely from `AeroStabilizerSystem`, one lumped PD
attitude-hold torque impulse applied at the ship's center of mass — a genuine torque impulse
(`RigidBodyHandle.applyTorqueImpulse`, not a snap/teleport), but with zero connection to individual
panel positions or deflection. Wing-panel deflection was computed *from* that controller's error,
purely for visual display, and fed nothing back into the physics.

**Fix (hybrid, by explicit user choice over a full flight-sim replacement):** new
`aero/control/AeroControlSurfaceTorque.java`. Registration shape, per-ship map, and physics-thread
hookup deliberately mirror `AeroStabilizerSystem`/`vs/XenoThrusterControl` exactly
(`SableEventPlatform.INSTANCE.onPhysicsTick`, `ConcurrentHashMap<UUID, ...>`, stale-sweep cleanup).
Each linked, role-assigned panel applies its own impulse at its own body-space block-center point
via `RigidBodyHandle.applyImpulseAtPoint(point, impulse)` — verified real two-overload method on
`dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle` via `javap` against the actual compiled
jar. Sable's own impulse API subtracts the ship's center of mass internally (confirmed by
`XenoThrusterControl`'s own existing comment), so the lever arm — and therefore the resulting torque
— falls out of the physics for free from where the point is; nothing here computes torque by hand.

- Impulse direction = the panel's own aerodynamic normal (`AXIS`-derived unit vector, the same
  direction `WingPanelBlock.sable$getNormal` already uses for Sable's separate lift/drag pass),
  scaled by `dynamicPressure * (deflectionDeg / MAX_DEFLECT_DEG) * AeroConfig.controlSurfaceTorqueScale`.
  `dynamicPressure = AeroConfig.airDensity * speed²`, the same shape `AeroAeroModel` already uses
  (no separate area term — `controlSurfaceTorqueScale` stands in for that).
- **`AeroConfig.controlSurfaceTorqueScale` defaults to 50,000 — this is an explicit, documented
  starting guess**, order-of-magnitude-derived by comparison against
  `ShipThrusterBlockEntity.DEFAULT_MAX_FORCE` (1,200,000) at a typical cruise dynamic pressure, not
  a measured or tested value. Too small: panels do nothing extra, it silently degrades to the old
  stabilizer-only feel. Too large: the ship becomes twitchy or the stabilizer fights it visibly.
- **Real bug, found and fixed after initial ship (user report: "something breaking the world when
  pressing s").** The first version of `physicsTick` had no cap on the impulse magnitude at all —
  `dynamicPressure = airDensity * speed²` grows with speed *squared*, so a fast-moving ship
  commanding pitch (W/S) could hand Sable an unbounded impulse. This codebase already has an
  explicit guard for exactly this failure mode (`XenoServerConfig.thrusterMaxImpulse`, default
  50,000, whose own javadoc says an unbounded impulse makes "the sub-level tear itself apart in a
  way that looks like an explosion" — reused directly for `AeroControlSurfaceTorque` rather than a
  second config knob) via `XenoThrusterControl.sane()`. The new torque system was shipped without
  it. Fixed by adding the identical `sane()` guard (finite-check + clamp-to-`thrusterMaxImpulse`,
  same warn-once-per-ship logging) to `AeroControlSurfaceTorque.physicsTick`, which now also takes
  the `ServerSubLevel` parameter it needs for that logging. **This means, in practice, most panels
  under real flight speed will now simply saturate the 50,000 cap rather than scaling smoothly with
  deflection/speed** — the crash risk is gone, but the "feels proportional" tuning goal from the
  original design is now secondary to "won't explode the ship," and revisiting
  `controlSurfaceTorqueScale`'s default downward (so the cap stops dominating every tick) is a
  reasonable next tuning step, not yet done.
- `AeroStabilizerSystem` was deliberately left completely unchanged — it's the "stability assist"
  layer on top of the new real per-panel torque, not replaced by it. Its `KP`/`KD` gains
  (`AeroStabilizerSystem.java`, hardcoded `5.0`/`3.2`) were considered for promotion to `AeroConfig`
  but were **not** touched, to keep this change's blast radius smaller — do that later if the panel
  torque needs the assist turned down to be felt.
- Wired into `AeroFlightCore.updatePanelDeflections` (same per-panel loop that already computes the
  visual angle — one extra list build, `AeroControlSurfaceTorque.sync(ship, list)` called once per
  scan) and into `AeroFlightCore.release` (`AeroControlSurfaceTorque.clearShip`, alongside the
  existing `AeroStabilizerSystem.clear`/`AeroAeroForceSystem.clear` — confirmed via grep that
  `release()` is the single shared disengage point both `PilotSeatBlockEntity` and
  `ShipVlsGuidanceBlockEntity` already call, so no per-host wiring was needed beyond that one method).

### 5. Redstone-driven thruster: was all-or-nothing, now proportional

Found while investigating a "thruster too powerful with redstone" report:
`ShipThrusterBlockEntity.tick()` treated *any* nonzero redstone signal as unconditional full power
(`desired = 1.0`), regardless of actual signal strength. Fixed to read
`Level.getBestNeighborSignal(pos)` (0–15, verified real vanilla `SignalGetter` method) instead of
the old boolean `hasNeighborSignal`, and scale `desired = strength / 15.0`. Touches
`ShipThrusterBlockEntity.java` (`cachedRedstoneStrength` replacing the old `cachedRedstone` boolean)
and `ShipThrusterBlock.java`'s `neighborChanged`.

### 6. Power requirement: default flipped, runtime toggle added

- `AeroConfig.requirePower` default changed `true` → `false` (both the field declaration and the
  `Data` holder's default) — flight now works with no FE source wired up, out of the box.
- New `command/AeroPowerCommands.java` (modeled directly on the existing `ShipGravityCommands.java`
  pattern): `/xenoaeropower <on|off|status>`, op-gated (`hasPermission(2)`), persists via
  `AeroConfig.save()`.
- **Important:** flipping the code default does *not* retroactively change an already-existing
  `config/xenopixelsmod-aero.json` on disk — `AeroConfig.load()` only calls `save()` (writing fresh
  defaults) if the file doesn't exist yet; if it exists, whatever's saved wins regardless of the
  code default. I directly patched `requirePower: true → false` in every config file I could find
  this session (three CurseForge/Modrinth instance configs + `run/config/xenopixelsmod-aero.json`).
  **Any other existing install (including whatever dedicated server this project has — see
  "Unresolved" below) will still have the old `true` on disk and needs the same manual edit or a
  `/xenoaeropower off` run, until/unless someone deletes the file and lets it regenerate.**

### 7. Linker (`TargetToolItem`) gap: chair shift-click did nothing distinct

Shift+right-click on a wing panel or thruster already unlinked it; shift+right-click on the chair
itself did the exact same thing as a plain click (re-select it as the armed chair). Added a
distinct shift-click behavior on the chair branch: clears the tool's stored/armed chair
(`CustomData` tag `LinkedChair`).

**Not resolved:** the user also reported thrust and shift-unlink "not working" in the same message
where this was diagnosed. The panel/thruster link/unlink logic itself was read end-to-end and found
correct (`AeroLinkManager.pairOne` does call `thruster.setPairedGuidance(owner)`, so the unlink
lookup has something to find). The likely explanation — never confirmed either way — is a stale jar
on whatever dedicated server the user actually tests against, matching this session's established
recurring pattern of "code in the repo/compiles ≠ code that reached the running game." **I never
obtained a path to that dedicated server. This was asked for explicitly, more than once, and never
answered.** If thrust/linking still doesn't work after confirming the client jar is current, that
server is the next thing to check — get its actual mods folder and update it.

### 8. DMZ camera fighting the pilot seat

DragonMineZ repositions the camera (shoulder-cam, first-person offset) for its own combat features.
`client/camera/ContraptionControlCamera.java` (mod-owned, not DMZ's) is the single condition both
`mixin/client/DmzShoulderCamOnControlMixin.java` and `DmzFirstPersonCamOnControlMixin.java` already
check before DMZ's camera code runs. Added a third clause to its `active()`:
`XenoFlightControls.seated()` (an existing helper, `mc.player.getVehicle() instanceof
XenoPilotSeatEntity`) — so DMZ's camera offset is now suppressed while seated, the same way it
already was for Create/Sable control cameras. No mixin changes needed. The separate camera-*shake*
mixins (`DmzCameraShakeViewMixin`/`DmzKiCameraShakeMixin`) were deliberately left alone — different
concern, already independently config-toggleable, and not what "camera position change" described.

### 9. Create Wrench rotates a wing panel's axis

`compat/create/CreateWrenchHandler.java` (existing, dependency-free — matches Create's wrench by
registry-name string, `create:wrench`, never imports a Create class) got a new branch: right-click a
`WingPanelBlock` with the wrench and its `AXIS` cycles X→Y→Z→X. Confirmed first that
`WingPanelBlock.rotate(BlockState, Rotation)` (added earlier for WorldEdit/structure rotation) does
**not** get called by this project's existing Create-Wrench support at all — the existing wrench
handling for other blocks is bespoke `FACING`-cycling code in this same file, not routed through
`rotate()` — so a new explicit branch was required rather than something "just working" for free.

### 10. Third-person seat camera with scroll-to-zoom

Investigated "scroll to zoom like contraption cam controls." Finding: Sable already ships a
complete scroll-wheel camera-zoom system (`dev.ryanhcode.sable.mixin.camera.camera_zoom.*`,
`mixinterface...CameraZoomExtension`) for its own third-person camera type,
`dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes.SUB_LEVEL_VIEW` — the
exact same camera type `ContraptionControlCamera.sableSubLevelView()` already special-cases. The
pilot seat only ever ran first-person, so that zoom system was simply never reachable from it.

Added `THIRD_PERSON_TOGGLE` (`XenoFlightControls.java`, unbound by default): toggles
`mc.options.setCameraType(...)` between `CameraType.FIRST_PERSON` and `SableCameraTypes.SUB_LEVEL_VIEW`.
`onMouseScroll` now returns early (leaves the event alone) while in third-person, so Sable's own
zoom mixin gets the scroll input instead of it being consumed for throttle. A `thirdPersonActive`
flag restores first-person on dismount/logout, but only if this toggle is what changed it (never
clobbers a camera type the player set some other way).

**Caveat, not fully verified:** `SableCameraTypes.SUB_LEVEL_VIEW` is set directly via
`Options.setCameraType(CameraType)` (a real, public vanilla method) rather than through vanilla's F5
perspective-cycle path. Sable also has `MinecraftMixin.sable$preCycleCameraType`/
`sable$postCycleCameraType` hooks specifically around the F5 cycle action, whose exact purpose was
not fully decompiled — only inferred, from `GameRendererMixin.sable$setupCamera` being a per-frame
(not per-cycle-event) hook, that camera setup is re-derived every frame regardless of how the type
was set, making a direct `setCameraType` call *probably* safe. This was judged reasonable rather
than exhaustively proven. If the third-person view renders wrong (blank, wrong target, wrong
origin) when reached via this toggle instead of F5, that inference was wrong and the pre/post-cycle
mixin hooks likely need to be triggered too, or reverse-engineered further.

### 11. Panel configurator: role + sign in one cycle, and a false-stall fix

Two more fixes after the torque-safety one above, from the same round of feedback:

- **False "STALL" while taxiing on the ground.** `AeroFlightCore.applyAeroModel` reported stall
  whenever `AeroAeroModel.angleOfAttackDeg` (angle between nose and *velocity* direction) exceeded
  `AeroConfig.stallAoADeg`. At near-zero ground speed, a velocity vector's direction is close to
  noise — taxiing a couple blocks/s while turning was enough to point "velocity" almost anywhere
  relative to the nose, reading as a stall. Added `MIN_STALL_SPEED = 3.0` blocks/s: below that,
  `applyAeroModel` returns `false` outright rather than computing a meaningless angle. The user
  separately theorized this was caused by the mouse moving the plane in keyboard mode — I could not
  find any code path where that's true (`updateAttitude`'s keyboard branch never reads
  `player.getYRot()`/`getXRot()`), and the ground-speed theory fully explains the reported symptom
  without it, so I did not change anything mouse-related. If stall still misfires with this fix in
  place, the mouse claim deserves a second, more literal look (e.g. does anything else — targeting,
  a DMZ mixin — feed head look into ship attitude that I didn't find).
- **Panel configurator redesign, at explicit user request.** Previously: plain right-click cycled
  `ROLE` (`PanelRole.next()`), shift+right-click toggled `WingPanelBlock.INVERT` — functionally a
  "+/-" for every role already, but the user didn't know it existed and asked for it without a
  modifier key. `PanelConfiguratorItem.useOn` now walks one flat sequence with a plain click only:
  `NONE → FLAP+ → FLAP- → PITCH+ → PITCH- → ROLL+ → ROLL- → YAW+ → YAW- → BRAKE+ → BRAKE- → NONE`,
  computed from `ROLE`/`INVERT`'s current values rather than a new enum (both blockstate properties
  are unchanged underneath). The action-bar message and tooltip now show the sign explicitly. Shift
  no longer does anything special on this item.

### 12. Thruster force was catastrophically miscalibrated — corrected using real project numbers

User report: "thruster on by pressing = flew the ship away... ship got fucked and deleted." User
pointed at `Propulsion-Team/create-propulsion-simulated` (verified MIT-licensed via `gh api
repos/.../license` before touching anything) as a reference for correct scale.

**What I did *not* do:** port that mod's thruster block/entity wholesale. Its
`AbstractThrusterBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity`
— a hard Create dependency — and its force path goes through a different Sable API
(`ServerSubLevel.getOrCreateQueuedForceGroup(...).applyAndRecordPointForce(...)`, verified by
fetching `SimulatedThrustAdapter.java`) than this project's direct
`RigidBodyHandle.applyImpulseAtPoint`/`applyTorqueImpulse` calls. Copying its exact numeric
constants would have been copying numbers calibrated for a different API surface, dressed up as
"verified" when it wasn't actually confirmed to share units. Their thruster block architecture was
also not ported, since doing so would make Create a hard requirement, directly against this
project's own established optional-Create rule.

**What the reference *did* provide, honestly:** their config (`PropulsionConfig.java`) documents a
standard single-block thruster producing about 0.53 "kN-equivalent" of force at full power —
confirming, structurally, that a reasonable single-block thruster force should be a small,
mass-proportional number, not an arbitrary large one. That prompted actually checking this
project's own real numbers instead of guessing again:

- This project's own `datapacks/xeno_ship_masses/data/sable/physics_block_properties/*.json`
  gives every block a `sable:mass` of 0.6–3.5. A modest few-hundred-block ship therefore has a
  total mass in the low hundreds.
- `RigidBodyHandle.applyImpulseAtPoint` is a genuine physics impulse — Δv = impulse/mass, standard
  rigid-body integration, not a "feel" slider.
- `ShipThrusterBlockEntity.DEFAULT_MAX_FORCE` was **1,200,000** (before that, **80,000** — see
  `LEGACY_DEFAULT_MAX_FORCE`). Against a mass in the low hundreds, either produces a velocity
  change of hundreds to thousands of blocks/s in a single tick. That is "flew away and got
  deleted," not a bug specific to anything added this session — this default predates this
  session, but I reused it (via `XenoServerConfig.thrusterMaxImpulse`) as the safety cap for the
  new `AeroControlSurfaceTorque` system without checking whether *it* was sanely calibrated
  either, which was a mistake on my part.

**Fix:** `DEFAULT_MAX_FORCE` corrected to **3,000** (`ShipThrusterBlockEntity.java`), reasoned from
mass-in-the-low-hundreds × a target ~10–15 blocks/s² acceleration ≈ 1,000–4,500. `setMaxForce`'s
clamp range tightened from `[1,000, 2,000,000]` to `[100, 50,000]` — the old upper bound was itself
in the catastrophic range. `XenoServerConfig.thrusterMaxImpulse` corrected from 50,000 to **6,000**
(same proportional headroom over the new default that 50,000 had over the old one).
`AeroConfig.controlSurfaceTorqueScale` corrected from 50,000 to **125** (same ratio applied, since
it was originally derived by direct comparison to the wrong `DEFAULT_MAX_FORCE`). A migration was
added to `ShipThrusterBlockEntity.loadAdditional` so thrusters already placed and saved at either
old default get corrected on next load, not just newly-placed ones. Existing `xenopixelsmod-server.json`
files on all four known instances already had the old `thrusterMaxImpulse: 50000.0` persisted (same
issue as the `requirePower` config-staleness problem earlier this session) and were patched
directly; `xenopixelsmod-aero.json` did not yet have `controlSurfaceTorqueScale` persisted anywhere,
so the code-default correction applies automatically there.

**Honesty check on the new numbers:** these are a reasoned, evidence-grounded correction — not a
measured, flight-tested final calibration. I do not know Sable's exact physics tick rate/substep
convention with certainty, so the "at a 60 Hz step" framing in the code comments is an assumption,
not a verified fact. If thrust still feels too strong or too weak after this, that's expected to
need a further, live-tested pass — say so plainly rather than assuming 3,000/6,000/125 are now
correct.

**Not done, deferred:** the user also asked to copy the reference's particle effects and textures
outright. Given the safety-critical nature of the force bug, I prioritized the magnitude fix and did
not get to the particle/texture port in this pass — that's still open, and should go through the
same "verify the license, read the real files, don't blindly copy something calibrated for a
different code path" discipline used above.

### 13. Wrench rotation on wing panels made click-face-aware

User: "wrench rotation on wings should let all rotations on the same default axis" (terse — this is
my best-effort interpretation, stated plainly so it can be corrected). Previously, wrenching a wing
panel always cycled `AXIS` in a fixed X→Y→Z→X order regardless of which face was clicked. Changed
`CreateWrenchHandler.java`'s `WingPanelBlock` branch so the **clicked face's axis becomes the new
axis directly** — matching the face-aware convention the same file already uses for the
`FACING`-property blocks below it (thruster/tube/guidance). Clicking a face on the axis the panel
is already on falls back to the fixed cycle (so a click always does something, never a no-op),
mirroring that same branch's own same-axis-flip fallback.

### 14. Thruster plume: real ship-pose bug found and fixed, effect technique adapted (not textures)

Follow-up to item 12 — user clarified: "just copy its effects not its textures and dont invent
api's please." `ShipThrusterBlockEntity.clientPlume()` (the exhaust-particle method) spawned
particles at raw `worldPosition` coordinates, which is only correct for a thruster on a ship
sitting unrotated at the world origin — `worldPosition` is the block's position in the ship's own
local/model space, not where it actually renders. Any ship that has moved or turned had its plume
appear in the wrong place. This is a real, independently-found bug, not something invented to
justify the port.

Fixed using only APIs verified via `javap` against the real compiled jars (never guessed): client
code resolves the containing ship via `SableCompanion.INSTANCE.getContainingClient(BlockEntity)`
(same `SableCompanion.INSTANCE` this codebase's own `VsShipHelper.getShipAt` already uses
server-side-agnostically), which returns `ClientSubLevelAccess` — its `renderPose()` transforms the
local spawn position and exhaust direction into the ship's actual current world pose
(`Pose3dc.transformPosition`/`transformNormal`, both confirmed real methods). Particle count now
scales with `power` (denser stream near full throttle) instead of a fixed cadence — the one idea
actually adapted from `create-propulsion-simulated`'s thruster (its density-scales-with-speed
approach), reimplemented from scratch against this project's own vanilla
`ParticleTypes.FLAME`/`ParticleTypes.SMOKE` — no textures, custom particle types, or literal code
from that mod were copied. One thing from the reference deliberately **not** attempted: inheriting
the ship's own velocity into the particle's initial velocity (so a fast-moving ship's exhaust
doesn't look like it's being left behind) — that data isn't exposed on the public
`ClientSubLevelAccess` interface this project already uses, and reaching into Sable's internal
`ClientSubLevel` implementation for it would have meant guessing at an API surface not confirmed to
be intended for external use, which is exactly what was asked not to do.

### 15. Direct stick control in keyboard mode; PD auto-pilot now mouse-aim only

User, after already having real per-panel torque (item 4): "wasd qe should only control the
flaps" — clarified via question into "direct stick → flap, autopilot off for manual flight" —
then further clarified mid-implementation: "pd auto pilot only on mouse mode" (i.e. scoped to
keyboard vs. mouse-aim, not touching mouse-aim's existing behavior at all).

**Before:** W/S/A/D/Q/E integrated into an absolute commanded attitude
(`XenoFlightControls.yawDeg/pitchDeg/rollDeg`), sent to the server, which
`AeroStabilizerSystem`'s PD controller flew the ship toward via real torque impulses.
PITCH/ROLL/YAW-role wing panels visualized (and, since item 4, applied torque from) the
*attitude error* between commanded and actual — never the raw stick.

**After:** a `mouseAim` mode flag and raw `pitchStick`/`rollStick` values (-1..1) now travel the
whole pipeline alongside the existing attitude fields: `XenoFlightControls` (computes
`stickPitch` (existing) and a new combined `rollStick` = A/D + Q/E) → `SeatFlightInputPacket`
(two new signed-byte fields + one new flag bit — **protocol bumped 29 → 30**, `ModNetwork.java`)
→ `AeroSeatInput.accept` → `AeroAction.SetAttitude` (extended to a 6-arg canonical constructor;
a 3-arg compact constructor defaults `pitchStick=0, rollStick=0, mouseAim=true` so the three
other call sites — GUI `AeroControlPacket`, CC `VlsGuidancePeripheral`, physical-panel
`AeroPanelActions` — are unchanged and always behave as mouse-aim-equivalent) → `AeroBus`
(new fields + `mouseAim()` default `true`) → `AeroFlightCore`:
- `tick()` now calls `AeroStabilizerSystem.setTargetAttitude(..., bus.mouseAim())` — the PD
  stabilizer sits idle (no torque) whenever `mouseAim()` is false. Since every non-seat source
  always reports `mouseAim()==true`, this only ever disables the stabilizer for a seat's own
  keyboard-mode flight.
- `deflectFor` now takes `mouseAim` and, in mouse-aim mode, behaves exactly as before
  (attitude-error-driven); in keyboard mode, PITCH/ROLL read `bus.pitchStick()`/`bus.rollStick()`
  directly (proportional, real-stick-like) instead of any error. YAW has no keyboard stick at
  all (no default key was ever bound to it — see item 2), so a YAW panel shows zero deflection in
  keyboard mode rather than reading input that was never sent.
- Rotation in keyboard mode therefore comes **entirely** from `AeroControlSurfaceTorque`'s real
  per-panel torque (item 4) reacting to stick-driven deflection — there is no second, parallel
  torque source fighting it anymore.
- The ROLL auto-mirror sign (item 3) still applies in both modes — it's about which side of the
  hull a panel is on, independent of whether its base deflection came from error or from a stick.

**Protocol version note:** any dedicated server still not on today's build (see "still unknown
dedicated server" below) will now have its connection refused outright by an updated client,
rather than silently desyncing — that's the existing, correct contract for a wire-format change
in this project, not a new problem introduced here.

**Not independently verified as "feels right" in play** — this is a real architecture change
verified only by compile + the existing unit tests; whether keyboard-mode flight actually feels
good (stick response curve, whether `AeroControlSurfaceTorque`'s still-untuned force scale is
adequate now that it's the *only* source of manual rotation) needs an actual flight test.

### 16. Wing-panel YAW rotation axis bug (found alongside the above)

User: "flaps/wing direction block are off they are going down on the pitch axis instead of yaw."
Real bug in `WingPanelBlockEntityRenderer`: the renderer applied its axis-alignment transform
(re-orienting the model to match a panel's mounted `AXIS`) *before* the YAW-role rotation, which
was hard-coded to rotate around local Y. For a panel mounted with `AXIS=Y` (default/flat, e.g. a
wing on top of the hull) that's harmless since the alignment step is a no-op there — but a
*realistic* yaw fin/rudder is mounted with `AXIS=X` or `Z` (normal pointing sideways, matching how
`WingPanelBlock`'s own javadoc defines `AXIS` as the surface's normal direction), and for those
mountings the preceding alignment transform reframes what "local Y" even means, so the rotation
ended up around whatever world axis the fin's own normal happened to point along — reading as a
pitch-like tilt instead of a true vertical swing. Fixed by applying the YAW rotation first, in the
still world-aligned frame, before the axis-alignment switch runs — at the cost of losing the
edge-hinge pivot point for YAW specifically (it now pivots from the block center; getting the
swing axis right was prioritized over the exact pivot point, and "which local edge is the hinge"
isn't well-defined once the rotation has to happen before axis alignment). `AeroControlSurfaceTorque`
was checked too: its impulse direction is the panel's raw `AXIS` unit vector regardless of role,
which does not have this bug — real torque naturally emerges from force × lever arm regardless of
which conceptual "role" a panel has, so no fix was needed there.

### 17. Full axis remap: A/D pitch, Q/E roll, W/S yaw; wrench simplified back to a fixed cycle

Two fast follow-ups after items 15/16 shipped:

- User: "noooo ad pitch qe roll ws yaw" — item 15 kept the pre-existing A/D=roll, W/S=pitch
  mapping and only changed *what* the keys drive (stick vs. attitude), not *which* keys drive
  *which* axis. This request swaps the axes themselves. Implemented as a real rewire, not a
  rename: `XenoFlightControls.updateWasdStick` now reads `keyLeft/keyRight` (A/D) into
  `stickPitch`, `ROLL_LEFT/ROLL_RIGHT` (Q/E) into `stickRoll`, and `keyUp/keyDown` (W/S) into a
  new `stickYaw` — all three smoothed identically via the existing `approach()` stepping. The old
  standalone "Q/E adds an extra roll rate on top of A/D" block is gone; Q/E is now roll's only
  keyboard source. `stickPitch` naturally keeps trimming pitch under mouse-aim (it already just
  read whichever key fed it) with zero extra code; **W/S deliberately does not trim yaw under
  mouse-aim** — that would need a second `pitchAimDeg`-style chase-vs-trim split for yaw that
  doesn't exist yet, and the user's request read as being about keyboard-mode flap control
  specifically, not a new mouse-aim feature, so it was left out rather than guessed at.
  `yawStick` threads through the exact same pipeline item 15 built for pitch/roll (packet →
  `AeroSeatInput` → `AeroAction.SetAttitude` → `AeroBus` → `AeroFlightCore.deflectFor`'s YAW
  case) — **protocol bumped again, 30 → 31**, for the packet's new third stick byte.
- User: "i cant use the wrench to properly align them" (re: item 9's click-face-aware wrench
  design) — a wing panel is a 3px-thick slab, so aiming at a specific thin side face to select an
  axis is unreliable in practice. Reverted to the original design: **any** click on the block
  cycles `AXIS` X→Y→Z→X regardless of which face was hit, guaranteeing every axis is reachable
  within two clicks with no aim precision required. This is a real usability finding from actual
  play, not a preference call — the face-aware version was a reasonable idea that didn't survive
  contact with the block's actual geometry.

### 18. Wrench axis: set directly from look direction, not cycled

User: "flaps axies still not rotateable with wrench right why not replacing the direction
instead?" — after item 17's cycle-based wrench fix, still not working for them. Re-read the
wrench code; found no logic bug in it. Implemented the user's own suggested fix instead of
guessing further: `CreateWrenchHandler`'s `WingPanelBlock` branch now sets `AXIS` directly from
`Direction.getNearest(player.getLookAngle()).getAxis()` — the exact same construction
`WingPanelBlock.getStateForPlacement` already uses when a panel is first placed — rather than
cycling through a fixed sequence. One click, correct axis, based on wherever the player is
actually looking, no cycling needed.

**Honest caveat, not fixed by this or any of the last three attempts:** if the actual root cause
is that the wrench's right-click raycast never registers a hit on the panel at all — plausible,
since a wing panel's collision/interaction shape is only 3 pixels thick, and vanilla targeting
needs the crosshair ray to actually pass through that thin box — then no change to *which axis
gets picked* can fix it, because the interaction event never fires with the panel as its target
in the first place; the fix for that would be about the panel's hitbox/outline shape, not this
handler. This was not attempted, because it wasn't confirmed to be the actual cause — it's a
plausible one, not a verified one. If this still doesn't work, that's the next thing to check by
actually testing whether right-clicking a wing panel with *any* item ever successfully targets it
at all (e.g. does `useOn` in `PanelConfiguratorItem` fire reliably against it, since that's a
useful independent way to test whether the panel is being *reliably targeted at all*, separate
from anything wrench-specific). Also worth confirming plainly with the user whether they
relaunched the client after each of these jars shipped — this session had multiple cases of a fix
being real but not yet reaching a running game.

## Known-unresolved items for whoever picks this up

1. **Dedicated server path still unknown.** Repeatedly asked for, never provided. Several reported
   "doesn't work" symptoms this session likely trace to a stale server jar rather than a code bug.
   Get this before debugging further reports of "X still doesn't work" against a server (as opposed
   to the CurseForge/Modrinth client instances, which were kept current).
2. **`AeroConfig.controlSurfaceTorqueScale` (50,000 default) is an untested starting guess**, not a
   tuned value — and now that the impulse cap fix (below) exists, most panels at real flight speed
   will simply saturate that cap rather than scaling smoothly, so the "feels proportional" goal is
   still unmet even though the crash risk is gone. Expect to lower this once someone actually flies
   a test ship and it still overshoots the cap constantly.
3. **Nothing in items 3–10 above has been flown**, except item 4 (control-surface torque), which
   got exactly one round of user feedback ("something breaking the world when pressing s") that led
   to finding and fixing a real missing-safety-cap bug (see that section) — still not confirmed
   *good* after the fix, only confirmed the specific reported failure mode should no longer be
   possible. Everything else compiles, passes the existing unit tests
   (`AeroAeroModelTest.java` and whatever else lives under `src/test/`), and was verified present in
   the built jar — none of it has a confirmed correct in-game behavior report.
4. **`SableCameraTypes.SUB_LEVEL_VIEW` via direct `setCameraType`** — see the caveat in section 10
   above. Worth confirming the view actually renders sensibly before trusting it.
5. The two now-orphaned placeholder texture files this session's flap work replaced
   (`wing_panel_deflected.png`/`wing_panel_deflected_down.png`, from an earlier iteration of the flap
   system that used discrete blockstate models) were deleted along with the models that referenced
   them, but if any stray reference to them turns up elsewhere, that's leftover from before this
   session's rewrite, not new.
6. `wing_panel.png` (the current block texture) is a **hand-authored placeholder PNG**, built with a
   small local Python script (zlib PNG encoder), not real generated art — no image-generation tool
   was available in this environment. It reads as a plausible metal panel with a diagonal accent
   stripe but was never run past the user for approval as final art.

## Build/verify commands used throughout

```
./gradlew compileJava compileTestJava test --offline
./gradlew jar jarJar serverJar --offline    # client (ModernUI-embedded) + slim server jar
jar tf build/libs/xenopixelsmod-0.1.9-1.21.1.jar | grep <ClassName>   # confirm before shipping
```

Client jars were copied to (all four, this session):
- `C:\Users\Admin\curseforge\minecraft\Instances\XenoPixels - DMZ\mods\`
- `C:\Users\Admin\curseforge\minecraft\Instances\XenoPixels - DMZ (2)\mods\`
- `C:\Users\Admin\Downloads\Programs\instances\XenoPixelsDMZ\mods\`
- `C:\Users\Admin\AppData\Roaming\ModrinthApp\profiles\Forge 1.20.1\mods\` (a real, active instance
  despite the stale "Forge 1.20.1" folder name — confirmed via its own mod list, which is NeoForge
  1.21.1 with ~150 mods)

The dev `run/` folder (`./gradlew runClient`) runs directly from compiled source, not a packaged
jar, so no copy step applies there — only a restart is needed to pick up new compiled classes and
the `run/config/xenopixelsmod-aero.json` patch.

The server jar (`xenopixelsmod-Server-0.1.9-1.21.1.jar`) was built successfully multiple times this
session but **has never been shipped anywhere** — no destination path was ever provided.

## Files touched this session (new)

```
src/main/java/net/bullettrain/xenopixelsmod/aero/control/AeroControlSurfaceTorque.java
src/main/java/net/bullettrain/xenopixelsmod/block/entity/WingPanelBlockEntity.java
src/main/java/net/bullettrain/xenopixelsmod/client/render/WingPanelBlockEntityRenderer.java
src/main/java/net/bullettrain/xenopixelsmod/command/AeroPowerCommands.java
src/main/resources/assets/xenopixelsmod/textures/block/wing_panel.png
```

Files touched this session (modified; note many of these also carry unrelated changes from earlier
sessions per the top-of-file caveat — this list is what *I* touched, not the full diff):

```
src/main/java/net/bullettrain/xenopixelsmod/aero/AeroConfig.java
src/main/java/net/bullettrain/xenopixelsmod/aero/control/AeroFlightCore.java
src/main/java/net/bullettrain/xenopixelsmod/block/custom/WingPanelBlock.java
src/main/java/net/bullettrain/xenopixelsmod/block/custom/ShipThrusterBlock.java
src/main/java/net/bullettrain/xenopixelsmod/block/entity/ShipThrusterBlockEntity.java
src/main/java/net/bullettrain/xenopixelsmod/client/ClientModEvents.java
src/main/java/net/bullettrain/xenopixelsmod/client/camera/ContraptionControlCamera.java
src/main/java/net/bullettrain/xenopixelsmod/client/flight/XenoFlightControls.java
src/main/java/net/bullettrain/xenopixelsmod/compat/create/CreateWrenchHandler.java
src/main/java/net/bullettrain/xenopixelsmod/item/custom/PanelConfiguratorItem.java
src/main/java/net/bullettrain/xenopixelsmod/item/custom/TargetToolItem.java
src/main/resources/assets/xenopixelsmod/blockstates/wing_panel.json
src/main/resources/assets/xenopixelsmod/lang/en_us.json
src/main/resources/assets/xenopixelsmod/models/block/wing_panel.json
```

Deleted:

```
src/main/java/net/bullettrain/xenopixelsmod/block/custom/PanelDeflect.java
src/main/resources/assets/xenopixelsmod/models/block/wing_panel_deflected.json
src/main/resources/assets/xenopixelsmod/models/block/wing_panel_deflected_down.json
```

## Standing conventions this session followed (and you should too)

- Verify every third-party API via `javap` against the real compiled jar before using it — this
  session caught real mistakes this way (`renderSingleBlock` routing to the item renderer for
  `ENTITYBLOCK_ANIMATED`, `hasNeighborSignal` vs `getBestNeighborSignal`, confirming
  `applyImpulseAtPoint`'s real signature before designing the torque system around it). Never guess
  a method name and hope it compiles later.
- Server-authoritative: client input is a request, never trusted directly for anything that changes
  world/physics state.
- No per-tick/per-frame allocations in hot paths — `AeroFlightCore`'s scratch vectors,
  `AeroControlSurfaceTorque`'s pre-allocated static axis-unit vectors, etc.
- Before shipping any jar, verify the target class/method actually landed in it via `jar tf` —
  Gradle's UP-TO-DATE cache lied at least once earlier this session.

## Prompt for Copilot: diagnose this session's work

Use this as a starting brief, not a checklist to rubber-stamp:

> Review the files listed under "Files touched this session" in this handoff — the wing-panel
> flap-animation rewrite, the new `AeroControlSurfaceTorque` per-panel torque system, the
> auto-mirroring logic in `AeroFlightCore.updatePanelDeflections`, the redstone-proportional
> thruster fix, the DMZ camera suppression change, the Create-Wrench axis rotation, and the
> third-person camera toggle in `XenoFlightControls`. Read the actual current code, not just this
> summary — treat every claim above as something to verify against the real files, not as ground
> truth. Specifically:
>
> 1. Check every claim in this document against the actual code. If something here is wrong,
>    outdated, or doesn't match what the file actually does, say so plainly — do not silently
>    "correct" the narrative or assume the handoff is right.
> 2. Look for correctness bugs, not style preferences: frame-consistency issues in the
>    ship-local-vs-world-space vector math (`AeroFlightCore`'s `rightLocalScratch` vs `rightScratch`
>    split is exactly the kind of thing that's easy to get backwards), thread-safety issues in the
>    new `AeroControlSurfaceTorque` snapshot swap, edge cases in the redstone-strength change (e.g.
>    a signal that drops to 0 vs. one that was never set), and whether `AeroControlSurfaceTorque`
>    is ever left registered for a ship that no longer exists.
> 3. Do not claim you tested anything in a running game unless you actually launched one and
>    watched it happen. If you can only read the code, say "read but not run," not "verified" or
>    "confirmed working."
> 4. Do not invent numbers. If you think `controlSurfaceTorqueScale`'s default (50,000) is wrong,
>    say why you think so and what you'd change it to and on what basis — don't state a "correct"
>    value as if it were measured.
> 5. If you can't find something this document references (a file, a method, a config key), say
>    you couldn't find it rather than assuming it exists as described.
>
> Report back concretely: file, line, what's actually wrong or worth improving, and why — the same
> standard this handoff tried to hold itself to.
