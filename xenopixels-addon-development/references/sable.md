# Sable Ship Integration

## Evidence Rule

Sable APIs must be verified from the exact installed source or jar. Do not invent package names,
ship interfaces, seat events, force methods, control packets, or lifecycle hooks. Historical notes
about pilot seats, fleets, missiles, guidance, and ballistics identify feature areas only.

Build a XenoPixels-owned adapter around the smallest verified Sable surface. Keep generic targeting,
guidance, fleet policy, permissions, and networking outside Sable implementation classes.

## Pilot and Input

- Register client mappings only on the client.
- Send bounded intent values at a controlled rate.
- Confirm the sender currently owns or occupies the relevant pilot/control position.
- Clear input after dismount, disconnect, screen capture, focus loss, ship removal, or timeout.
- Keep server simulation authoritative and reject impossible control state transitions.
- Define conflict behavior when Create Aeronautics also handles the same ship or seat.

## Ship and Fleet Identity

Do not use transient entity or object references as durable fleet identity unless Sable explicitly
guarantees them. Store namespaced identifiers and resolve them through the current server state.
Validate owner, team, dimension, loaded state, and lifecycle before applying a fleet command.

Fleet operations should be bounded and permission-aware. Avoid scanning every loaded entity or ship
each tick when an indexed registry, attachment, or event-driven cache is available.

## Guidance and Ballistics

Server authority must cover target selection, lock validity, ammunition or cooldown, launch state,
trajectory updates, collision, damage, and detonation. Client HUD and lock indicators are derived
views.

For every guided or ballistic path define:

- target identity and invalidation conditions;
- world/ship coordinate conversions;
- tick delta and velocity units;
- maximum range, lifetime, turn rate, acceleration, and payload bounds;
- owner/allied-target exclusions;
- unloaded chunk and removed-ship behavior;
- synchronization frequency and interpolation;
- save/load expectations, if persistence is supported.

Do not apply both vanilla entity motion and Sable force/physics control to the same object unless the
exact integration explicitly requires it.

## Lifecycle Safety

Handle creation, assembly, load, unload, disassembly, dimension transfer, destruction, and server
stop. Remove listeners, inputs, target locks, fleet indexes, and temporary forces when ownership
ends. Use weak or lifecycle-owned references where appropriate; do not retain removed world or ship
objects in static collections.

## Networking

Sable-related XenoPixels packets belong in the addon namespace, not the main sequential channel.
Validate sender, ship identity, seat/control ownership, target, dimension, timing, and numeric
bounds. Treat unknown Sable ids or missing ships as normal rejection cases rather than crashes.

## Validation Matrix

| Scenario | Evidence |
| --- | --- |
| Sable absent | XenoPixels starts without resolving Sable classes. |
| Sable present | Adapter registers once against verified symbols. |
| Dedicated server | No key mapping, renderer, model, or client-screen class loads. |
| Pilot disconnect/dismount | Inputs clear within the defined timeout. |
| Ship unload/destruction | Locks, fleets, projectiles, and cached references clean up. |
| Rotated moving ship | Guidance and ballistics use the intended coordinate space. |
| Mismatched client/server | Dependency or protocol failure is explicit and safe. |
| Fresh runtime | Current log and observable ship behavior confirm the integration. |
