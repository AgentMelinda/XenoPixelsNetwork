# Create Aeronautics Compatibility

## Scope

“Aero” in this skill means Create Aeronautics compatibility. It may interact with Create,
Flywheel, Ponder, Colorwheel, Valkyrien Skies, and Sable, but each installed artifact is a separate
versioned dependency. Inspect the exact build and jars before selecting any class, event, registry,
attachment, ship, force, rendering, or contraption API.

Do not copy modern Create or Valkyrien Skies examples into Minecraft 1.21.1 without verifying the
matching release. Do not infer Create Aeronautics APIs from Create APIs with similar names.

## Integration Boundary

Prefer, in order:

1. A published Create Aeronautics integration interface or event.
2. A published Create or ship-framework interface explicitly used by Aeronautics.
3. A XenoPixels-owned adapter around exact dependency symbols.
4. A narrow verified mixin when no supported hook exists.

Keep XenoPixels gameplay rules outside third-party implementation classes. The adapter should
translate ship identity, pose, velocity, forces, seats, controls, or contraption state into a small
XenoPixels-owned model.

## Optional Dependency Safety

- Keep Aeronautics classes out of unconditional static fields and common entrypoints.
- Gate mixins and registrations by mod presence and exact side.
- Avoid method descriptors containing optional classes in code that loads without the dependency.
- Test Create without Aeronautics, Aeronautics without XenoPixels features enabled, and the complete
  supported stack.
- Treat sorting-cycle or dependency-order warnings as evidence to investigate, not proof that the
  XenoPixels patch caused them.

## Coordinate and Motion Rules

Before moving an entity, projectile, effect, target, or attachment associated with a ship, identify:

- world-space versus ship-space position;
- world-space versus ship-space direction and velocity;
- ship transform interpolation used for rendering versus authoritative simulation;
- whether force, impulse, torque, or direct velocity is the supported operation;
- logical server ownership and simulation tick phase;
- behavior while the ship is assembling, disassembling, unloaded, or transferring dimensions.

Never mix coordinate spaces implicitly. Give conversion helpers semantic names and test round trips
with translated and rotated ships.

## Controls and Seats

Client key state is a request, not authority. The server must confirm the player is in the intended
seat or control role, the ship and assembly still exist, the requested action is allowed, and input
values are bounded. Define timeout behavior so stale input does not continue after disconnect,
dismount, focus loss, or packet loss.

Avoid registering duplicate keys or consuming inputs that belong to another installed controller.
Document priority when Create Aeronautics and Sable both expose pilot behavior.

## Rendering

- Keep Flywheel and renderer classes client-only.
- Use authoritative simulation data plus the framework's supported interpolation path.
- Do not mutate server state from a renderer or Ponder scene.
- Test shader/resource reloads, GUI transitions, first/third person, and ship unload.
- Verify model and material registration against the exact Flywheel/Create versions.

## Focused Tests

- Optional dependency absent/present startup.
- Dedicated-server classloading.
- Rotated and translated ship-space conversions.
- Seat ownership and stale-input timeout.
- Assembly/disassembly and chunk unload.
- Duplicate registration when Sable is also present.
- Fresh runtime proof for force, control, or rendering behavior.
