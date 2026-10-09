---
name: xenopixels-addon-development
description: "Use when developing or reviewing Minecraft 1.21.1 NeoForge code for XenoPixels, its public addon API, DragonMineZ, native XenoPixels NPCs, MyNPCs or CustomNPCs compatibility, Create Aeronautics, or Sable ship systems."
---

# XenoPixels Addon Development

## Overview

Use this skill for the exact XenoPixels repository or for a separate addon that consumes its
published API. The supported baseline is Java 21, Minecraft 1.21.1, NeoForge 21.1.248, and
DragonMineZ 2.1.3. Verify every version from the project before editing; do not migrate a project
to this baseline merely because this skill documents it.

Core principle: **source and exact dependency artifacts outrank recollection, examples, and old
handoffs**. Never invent a XenoPixels, DragonMineZ, MyNPCs, CustomNPCs, Create Aeronautics, Sable,
Valkyrien Skies, or NeoForge API.

### Routing Boundaries
- `Use when`: the task touches XenoPixels 1.21.1 source, its public API, addon networking, DMZ compatibility, XenoPixels NPCs, MyNPCs/CustomNPCs parity, Create Aeronautics, or Sable ships.
- `Use when`: an external mod depends on `xenopixelsmod` and needs compatibility-safe events, packets, commands, data, or optional integrations.
- `Do not use when`: the task is generic loader work with no XenoPixels integration; use `minecraft-modding` instead.
- `Do not use when`: the task is only server administration, resource-pack authoring, or vanilla commands.

## Start Here

1. Read repository instructions and inspect `git status --short`.
2. Run `node ./scripts/audit-project.mjs <project-root>` from this skill directory when Node is
   available. Treat warnings as investigation prompts, not proof of a defect.
3. Classify the project:
   - **Exact repository:** mod id `xenopixelsmod` or source under
     `net/bullettrain/xenopixelsmod`.
   - **External addon:** a separate mod consuming XenoPixels.
4. Identify the affected integration and load only its reference below.
5. Verify symbols in this order: repository source, tracked decompiled source, `javap` against the
   exact jar, then official documentation for the pinned version.
6. Define focused static, build, dedicated-server, and runtime evidence before editing.

## Non-Negotiable Contracts

| Area | Contract |
| --- | --- |
| Published API | Only `net.bullettrain.xenopixelsmod.api/**` is public unless another maintained integration explicitly says otherwise. |
| Networking | Keep the main sequential `ModNetwork` channel stable; addon packets use the namespaced `AddonNetwork` registry. |
| DragonMineZ | Keep third-party patching additive. Delete or overwrite behavior requires an owner decision plus migration and backup design. |
| Optional mods | Gate classloading, mixins, client code, and registrations so absent integrations do not crash startup. |
| Mixins | Verify the exact target bytecode, use the narrowest stable injection, and preserve client/server side safety. |
| Runtime claims | Compilation is not proof that an event, packet, renderer, NPC behavior, or ship force worked in game. |
| Existing work | Preserve unrelated dirty paths, assets, generated references, archives, and dependency jars. |

External addons must not import internal XenoPixels packages. If required capability is absent from
the public API, propose the smallest additive API extension in XenoPixels and validate the example
addon boundary before consuming it.

## Integration Router

| Task | Read |
| --- | --- |
| Version pins, API ownership, packet rules, exact-repo layout | [Project profile](./references/project-profile.md) |
| DMZ combat, UI, quests, techniques, stats, or mixins | [DragonMineZ integration](./references/dragonminez.md) |
| Native XenoPixels NPC editor, dialogue, quests, roles, combat, or scripts | [XenoPixels NPCs](./references/xenopixels-npcs.md) |
| MyNPCs integration or CustomNPCs parity | [MyNPCs compatibility](./references/mynpcs-compat.md) |
| Create Aeronautics integration | [Create Aeronautics](./references/create-aeronautics.md) |
| Sable pilot, fleet, guidance, ballistics, or physics work | [Sable ships](./references/sable.md) |
| Choosing tests and interpreting evidence | [Validation matrix](./references/validation-matrix.md) |

## Change Workflow

### Exact XenoPixels repository

1. Locate the current owning subsystem and its tests before using historical handoffs.
2. Verify dependency symbols against the exact configured jar.
3. Preserve public API signatures and packet identifiers unless the task explicitly authorizes a
   compatibility change.
4. Add focused tests for pure logic, serialization, permissions, packet validation, and data
   migration.
5. Compile the smallest affected source set, then run the repository validation ladder.
6. Use a fresh process for runtime checks and inspect the current log.

### External addon

1. Depend only on the published XenoPixels API and documented NeoForge interfaces.
2. Give the addon its own mod id, packet namespace, configuration, data namespace, and permissions.
3. Declare dependency requirements deliberately: mandatory XenoPixels, optional third-party mods
   unless the feature cannot function without them.
4. Keep compatibility entrypoints isolated so optional jars are never resolved on an unsupported
   side or absent installation.
5. Test matching, missing, and deliberately incompatible client/server addon sets.

## Side and Authority Rules

- Keep decisions that affect combat, stats, inventory, quests, NPC saves, ship forces, and
  permissions authoritative on the logical server.
- Client packets request actions; the server validates identity, range, state, ownership,
  permissions, payload bounds, and current data before applying them.
- Client-only screens, renderers, key mappings, and model classes must not load on a dedicated
  server.
- Do not trust an entity id, dialogue id, quest id, profile tag, ship id, or registry name merely
  because the client sent it.
- Bound collections, strings, NBT, packet payloads, and editor save keys before allocation or use.

## Worked Example: External Trainer Addon

Suppose an external addon wants a MyNPCs trainer interaction that grants XenoPixels-owned training
progress:

1. Confirm the addon is external and inspect the current XenoPixels API package for a supported
   training or progression hook.
2. Do not import the internal NPC compatibility, profile, network, or command packages.
3. If the public hook is missing, add the smallest additive server-side API capability in the exact
   XenoPixels repository, document its side and validation contract, and keep old consumers valid.
4. Keep MyNPCs and CustomNPCs class access inside their verified XenoPixels adapters; the external
   addon consumes only the public XenoPixels result.
5. Use the external addon's own packet namespace for its UI request. The server rechecks player,
   NPC, distance, permission, trainer eligibility, and requested training id.
6. Compile the standalone API example, then test neither NPC mod, MyNPCs only, CustomNPCs only,
   dedicated server, and a fresh in-game grant.

This pattern keeps third-party symbols, XenoPixels internals, and addon ownership from leaking into
one another.

## Stop and Verify

| Rationalization | Required response |
| --- | --- |
| “The nearby version probably has the same API.” | Inspect the exact configured source or jar. |
| “It compiles, so the mixin or event works.” | Start a fresh process and exercise the path. |
| “The optional mod is always in the dev pack.” | Test startup without it and on a dedicated server. |
| “The client already validated the editor value.” | Validate it again against current server state. |
| “Using an internal class is faster.” | Add or request the smallest stable public API instead. |
| “More homing strength will fix the ship bug.” | Verify coordinate spaces and simulation ownership first. |

## Common Mistakes

- Copying a symbol from an old handoff without checking the configured jar.
- Importing `net.bullettrain.xenopixelsmod` internals from an external addon.
- Appending addon packets to `ModNetwork` and shifting existing sequential packet ids.
- Loading MyNPCs, CustomNPCs, Create, Aeronautics, or Sable classes before confirming the mod is
  present and the current side is valid.
- Treating MyNPCs as a simple text replacement when signatures or inheritance differ.
- Applying world-space movement directly to a ship-managed entity without confirming coordinate
  and force semantics.
- Claiming runtime success from `compileJava`, unit tests, or a client task exit code alone.

## Completion Evidence

Record exact commands, dependency versions and hashes, focused test results, distribution artifact
details, fresh runtime observations, unverified items, and safe next steps. Use explicit dates in
durable handoffs.
