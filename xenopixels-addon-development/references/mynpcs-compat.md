# MyNPCs and CustomNPCs Compatibility

## Baseline and Parity

The documented MyNPCs baseline is mod id `mynpcs`, version 1.5.0, with Java packages under
`espi.mynpcs`. Existing CustomNPCs compatibility uses packages under `noppes.npcs`. Verify the
installed jars and metadata before assuming either baseline remains current.

MyNPCs-first development must preserve CustomNPCs parity where the repository already supports
both. Parity means equivalent user-visible behavior and data ownership, not necessarily identical
source. A package-name mirror is acceptable only after signatures, inheritance, descriptors,
resources, and side behavior are verified for both targets.

## Compatibility Layout

Keep these concerns separate:

- shared XenoPixels NPC-to-DMZ profile and behavior logic;
- CustomNPCs-specific adapters, mixins, screens, packets, and scripts;
- MyNPCs-specific adapters, mixins, screens, packets, and scripts;
- migration or clone conversion between namespaces;
- conditional activation when either, both, or neither mod is installed.

Do not reference third-party classes from unconditional common entrypoints. A conditional mixin
plugin can gate mixins, but constructors, static fields, event subscribers, configuration screens,
and packet registration also need safe classloading.

## Verify Before Mirroring

For every mirrored class confirm:

1. Fully qualified target and superclass.
2. Method and field descriptors, including inherited members.
3. Generic erasure and access level.
4. Screen dimensions, widget ownership, and container relationships.
5. Packet direction and actual packet superclass.
6. Resource namespaces and registry identifiers.
7. Client-only annotations or distribution boundaries.

If one fork differs, keep an explicit fork-specific implementation rather than hiding the
difference behind reflection or unsafe casts.

## Editor and Profile Integration

- Keep authoritative combat/profile data on the server.
- Treat third-party GUI tabs as presentation and request surfaces.
- Re-read the targeted NPC and current profile when handling a save.
- Validate permissions, editor ownership, range, entity type, and payload bounds.
- Preserve third-party fields that XenoPixels does not own.
- Keep form, trainer, stats, inventory, dialogue, and role screens independent enough that one
  fork-specific failure does not disable every compatibility feature.

## Clone and Data Migration

Namespace migration must walk structured data rather than replace arbitrary text. Rewrite only
verified registry or type identifiers such as a confirmed `customnpcs:` to `mynpcs:` mapping.
Preserve unknown tags, make conversion idempotent, retain a backup or source copy, and report items
that cannot be translated.

Test nested compounds and lists, mixed namespaces, already-migrated data, malformed identifiers,
and data from installations where only one NPC mod is present.

## Scripting and Reflection

Use the installed script API and wrapper classes as the contract. Before invoking a method, verify
its current signature and return type from source or bytecode. Reflection adapters should:

- cache verified lookups without making absence fatal;
- distinguish a missing mod from a changed API;
- surface actionable errors once rather than spamming every tick;
- avoid broad access to unrelated internals;
- run gameplay mutations on the logical server.

Do not expose a XenoPixels script method merely because a similar method existed in another fork.

## Conditional Test Matrix

| Installation | Expected result |
| --- | --- |
| Neither NPC mod | XenoPixels starts; compatibility mixins and classes remain unloaded. |
| MyNPCs only | MyNPCs integration starts and supported screens/actions work. |
| CustomNPCs only | Existing parity path starts and supported screens/actions work. |
| Both installed | Behavior is explicitly defined; no duplicate registrations or ambiguous adapters. |
| Dedicated server | No client GUI or renderer classloading failure. |
| Client without server compatibility | Protocol/dependency mismatch is reported cleanly. |

## Common Failures

- Assuming `noppes.npcs` to `espi.mynpcs` replacement is sufficient.
- Shadowing a member on the wrong class when it is inherited from a packet or screen superclass.
- Registering two copies of a shared mixin when one `customnpcs || mynpcs` gate is intended.
- Allowing a GUI save to overwrite third-party fields outside XenoPixels ownership.
- Converting clone NBT with unbounded string replacement.
- Testing only an integrated client and missing dedicated-server classloading.
