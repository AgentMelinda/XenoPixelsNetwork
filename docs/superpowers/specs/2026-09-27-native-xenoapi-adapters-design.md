# Native XenoAPI adapters

Date: 2026-09-27
Status: proposed written spec; awaiting review. No implementation claimed.

## Outcome

Expose native Xeno NPC functionality through the interfaces in
`XenoNPCsAPI/src/main/java/xenoapi/npcs/api`, preserving existing scripts and
their `XenoPixels` binding. The user selected this adapter integration on
2026-09-27. This document covers that integration; example refresh and visual
API documentation remain subsequent work in the original request.

## Verified starting point

- `NpcAPI` is abstract, server-side, and requires explicit singleton registration.
- The API project supplies contracts and events, not their native implementation.
- `NpcScriptHost` supplies native wrappers and `NativeXenoScriptApi.INSTANCE`.
- `ScriptNpc` extends `ScriptEntity`; it does not implement `ICustomNpc`.
- Directly adding the interface is incompatible with the existing speech method:
  `ScriptNpc.say(String)` returns boolean; `ICustomNpc.say(String)` returns void.
- Existing timers and temporary data belong to the script host. Creating another
  independent timer or temporary-data owner would change behavior.

## Architecture

Introduce concrete typed adapters in the native NPC implementation package.
Keep existing script wrapper signatures intact. Centralize conversions between
Minecraft entities, existing wrappers, and XenoAPI interfaces in one factory.
Use explicit delegation, not reflective name matching or dynamic proxies.

The adapter hierarchy covers entities, living entities, native NPCs and players,
plus the world, position, NBT, item, stored-data and timer objects required by
those contracts. Return typed adapters for nested values. Preserve the same
underlying entity and authoritative data owner across conversions.

Implement `NpcAPI` as the server-side entry point and register it once during mod
construction. Methods needing a server resolve the active server at call time;
do not capture a world during construction or retain a stopped server.
Native NPC creation uses the existing entity registration and spawn lifecycle.

Compile against the API project's actual source through a reproducible Gradle
dependency. Package one copy of the API classes in client and server distributions;
the server jar must retain its zero `META-INF/jarjar/` invariant. Do not copy an
untracked build-output jar into `libs` as the source of truth.

## Compatibility and limits

Keep `npc`, `world`, hook arguments, existing `event.API`, and `XenoPixels`
behavior compatible for existing scripts. Expose the new entry point as an
additive `XenoAPI` script binding and list it in the editor's binding help.
Adapters must be accepted by XenoPixels extension methods through explicit
unwrapping where those methods accept wrapped entities.

Implement methods backed by existing native behavior with matching semantics.
For contracts with no native equivalent, throw an actionable
`UnsupportedOperationException` naming the method. Document these methods in
a capability table. Never return success, empty collections or zero merely to
make an unimplemented operation appear to work. Legitimate missing-object
results follow each interface's nullability contract.

The API event bus exists for consumers, but this adapter slice must not claim
that declaring or registering event classes connects them to gameplay. Posting
typed production events and applying their mutable results is a separate slice
of the original integration task. Existing script hook dispatch is preserved.

## Authority and lifecycle

Mutations execute on the logical server and retain existing permissions, bounds,
save ownership and synchronization paths. No client renderer types may be loaded
by adapter initialization. Temporary data and timers share the script host owner;
unload, script reload and server stop follow that owner's cleanup behavior.
Invalid arguments fail before partial mutation.

## Acceptance

1. The mod and a separate consumer compile against the real API types.
2. Singleton registration is deterministic; access before server startup has a
   documented failure for world-dependent operations.
3. Entity conversion preserves identity, subtype and nested wrapper contracts.
4. NPC data and timers remain shared with existing script wrappers.
5. Existing example scripts retain their bindings and pass available script checks.
6. Unsupported methods are explicit and listed, with no full-parity claim.
7. Both distributions contain the API; server jar has no jarjar entries.
8. Fresh runtime evidence is required before claiming spawning, speech, targeting,
   timers or extension calls work in game.

## Validation and rollback

Run focused adapter, lifecycle and script regression tests, then `./gradlew test`.
Run `./gradlew buildApiExampleAddon -PofflineMcMeta` and
`./gradlew build jarJar serverJar -PofflineMcMeta`; inspect archive contents.
Exercise the adapter path in a fresh `./gradlew runApiTestClient` process and
inspect its log. Record unrelated failures separately and preserve unverified
runtime items until exercised.

The integration is additive and requires no save conversion. Rollback removes
the new binding, adapters, registration and build dependency together; existing
wrapper APIs, stored NPC data and script files remain usable.
