# XenoPixels Project Profile

## Supported Baseline

The exact repository contract is:

| Setting | Value |
| --- | --- |
| Java | 21 |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248 |
| XenoPixels mod id | `xenopixelsmod` |
| DragonMineZ jar | `libs/dragonminez-2.1.3.jar` |
| Published Java API | `net.bullettrain.xenopixelsmod.api/**` |

Read `gradle.properties`, build scripts, mod metadata, dependency jars, and source before relying on
this table. If the repository intentionally changes a pin, update its contract and this reference
together rather than silently forcing the old value.

## Evidence Hierarchy

Use the first available source that answers the question:

1. Current repository source and tests.
2. Current tracked generated or decompiled references.
3. `javap -p -c` or archive inspection against the exact configured jar.
4. Official documentation for the exact dependency and Minecraft version.
5. Historical handoffs and changelogs as search indexes, not current proof.

Do not generalize a method name, field, event, packet, mixin target, or registry path from a nearby
version. Preserve an explicit “not verified” note until the required evidence exists.

## Repository Profiles

### Exact XenoPixels repository

- Preserve the mod id and Java/Minecraft/NeoForge pins.
- Treat the API package as compatibility-sensitive.
- Preserve the main sequential network channel.
- Keep third-party compatibility code isolated by integration and side.
- Treat tracked dependency jars, UI archives, generated references, and source assets as owned
  material rather than disposable outputs.
- Put generated resources under `src/generated/resources` and authored resources under
  `src/main/resources`.

### External addon

- Use a distinct mod id and package root.
- Import only `net.bullettrain.xenopixelsmod.api` from XenoPixels.
- Use the addon's own packet namespace and protocol version.
- Do not mix into XenoPixels internals merely to avoid requesting an additive API hook.
- Keep third-party integrations optional unless the addon's core purpose makes one mandatory.
- Test against the exact released XenoPixels artifact the addon declares.

## API Ownership

Everything outside `net.bullettrain.xenopixelsmod.api/**` is internal unless a maintained
integration contract explicitly states otherwise. Public API work should:

1. Add the smallest interface, event, record, registry, or service required by the use case.
2. Avoid exposing mutable internal objects when an immutable view or command interface is enough.
3. Define logical-side and thread expectations.
4. Bound packet and data inputs at the API boundary.
5. Keep previous signatures and meanings stable whenever possible.
6. Update and build the standalone example addon.

An external addon must not copy internal source to manufacture an unsupported API.

## Networking Ownership

The main `ModNetwork` channel is sequential and compatibility-sensitive. Do not insert addon
payloads into it or reorder registrations. XenoPixels addon payloads belong in the namespaced
`AddonNetwork` registry; independent external mods should use their own channel and namespace.

For every serverbound payload validate:

- sender and login state;
- current logical side and thread;
- ownership or permission;
- distance and world/dimension relationship when applicable;
- registry identifiers and current server data;
- collection sizes, strings, NBT, and numeric bounds;
- replay, duplicate, or stale editor state where relevant.

## Dependency Artifact Changes

`libs/dragonminez-2.1.3.jar` and `dragonminez_sha256` are one atomic contract. If an authorized task
changes the jar:

1. Preserve the previous artifact until the new source and license status are understood.
2. Record the old and new full SHA-256 values.
3. Update the configured dependency name and hash together.
4. Re-run decompilation or `javap` checks for every touched compatibility symbol.
5. Re-run compile, tests, distribution, example-addon, and runtime checks.

Do not update a binary merely because a newer file exists.

## Working-Tree Safety

- Inspect `git status --short` before and after work.
- Never reset, overwrite, or delete unrelated dirty paths.
- Review build scripts, properties, mixin configurations, workflows, and binary changes
  individually.
- Do not edit `build/`, `run/`, Gradle caches, or example outputs as source.
- Do not claim a clean or current runtime while an old process is still running.

## Quick Review

| Question | Required evidence |
| --- | --- |
| Is this class public API? | Current path or maintained API documentation |
| Is this third-party method present? | Exact jar source or `javap` output |
| Is a packet compatible? | Registration/protocol review plus mismatched-set test |
| Did an event fire? | Fresh runtime log or observable result |
| Is a generated file authoritative? | Repository ownership rule and generating task |
| Is an old handoff still accurate? | Confirmation in current source and dependencies |
