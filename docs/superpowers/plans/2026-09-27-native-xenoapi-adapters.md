# Native XenoAPI Adapters Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for native execution or superpowers:subagent-driven-development for delegated execution. Implement task-by-task and track the checkboxes.

**Goal:** Make the real XenoAPI contracts usable with native NPCs while preserving existing XenoPixels scripts.

**Architecture:** Add typed adapters beside existing wrappers and a registered NpcAPI implementation. Share native state and lifecycle ownership; expose XenoAPI as an additive script binding. Unsupported operations fail explicitly.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.248, Gradle 8.8, local XenoAPI 1.21.1-0.1.0.

**Spec:** `docs/superpowers/specs/2026-09-27-native-xenoapi-adapters-design.md` (approved 2026-09-27).

## Global Constraints

- Preserve existing wrapper signatures, script bindings, stored data and packet identifiers.
- No reflection-based method dispatch or dynamic proxies.
- Retain server authority, bounds, permissions and client synchronization.
- Package one copy of API classes per distribution; server jar has zero `META-INF/jarjar/` entries.
- No save conversion, commits, staging, dependency-jar replacement or unrelated cleanup.
- Runtime success requires fresh observed behavior; compilation alone is insufficient.
- Typed gameplay event posting is a subsequent slice, not promised by adapter registration.

## Review Focus

1. Overloaded extension calls with null targets must retain Nashorn resolution and null behavior (Task 4).
2. Timer/data access before a script host exists must not create competing state owners (Task 2).
3. Server stop and restart in the same JVM must not retain a stale world (Task 3).
4. Unsupported mutations must throw before changing any native state (Tasks 2–3).
5. External or wrong-side entity wrappers must not bypass native validation (Tasks 2–4).

## File map

Existing implementation base: `src/main/java/net/bullettrain/xenopixelsmod/`.
New adapter package under that base: `npc/script/api/xeno/`.
New tests mirror that package under `src/test/java/net/bullettrain/xenopixelsmod/`.
Paths below using these bases are relative to them.

## Task 1: Reproducible API dependency and packaging

Files: root `settings.gradle`, `build.gradle`; new `docs/native-xenoapi-adapters.md`.

- [ ] Include the local `XenoNPCsAPI` build as a composite dependency, using its actual Maven coordinates `net.bullettrain:xenoapi:1.21.1-0.1.0`; verify substitution against the included project's published identity.
- [ ] Add the dependency to compilation/runtime and a non-transitive packaging configuration. Expand only the library's classes/resources into both distribution jars, excluding manifests, signatures and module descriptors. Include API sources in the source artifact without editing the nested repository.
- [ ] Run `./gradlew compileJava -PofflineMcMeta`. Verify resolution builds the local source and never relies on an existing `XenoNPCsAPI/build/libs` artifact.
- [ ] Build the two jar tasks and inspect for `xenoapi/npcs/api/NpcAPI.class`, duplicate class paths, and zero server jarjar entries. Record results in the adapter document.

## Task 2: Typed wrappers with shared native state

Create in `npc/script/api/xeno/`: `XenoApiAdapters.java`, `XenoEntityAdapter.java`,
`XenoLivingAdapter.java`, `XenoNpcAdapter.java`, `XenoPlayerAdapter.java`,
`XenoWorldAdapter.java`, `XenoPosAdapter.java`, `XenoNbtAdapter.java`,
`XenoItemAdapter.java`, `XenoDataAdapter.java`, `XenoTimersAdapter.java`.
Modify `npc/script/NpcScriptHost.java` and `npc/script/api/ScriptNpc.java` only to expose shared state access needed by the adapters.
Test: `npc/script/api/xeno/XenoApiAdaptersTest.java`.

Interfaces produced by the factory:
`IEntity<?> wrap(Entity entity)`, `IWorld wrap(ServerLevel level)`,
`IItemStack wrap(ItemStack stack)`, `INbt wrap(CompoundTag tag)`,
`IPos position(double x, double y, double z)`, and `Entity unwrap(IEntity<?> entity)`.
Use actual generic bounds from the checked-in contracts when declaring adapter classes.

- [ ] Read every inherited contract for these types and inventory methods in `docs/native-xenoapi-adapters.md`: implemented delegation, implemented Minecraft operation, or unsupported. Include parameter/return semantics where they differ from existing wrappers.
- [ ] Add failing tests for null entity conversion, native NPC/player subtype selection, same underlying entity after round-trip, nested typed return values, and rejection of foreign adapters on mutation paths.
- [ ] Add failing data/timer tests proving writes through either interface are visible through the other, including access before host initialization, host rebuild and cleanup. Retain existing key/value bounds.
- [ ] Implement the concrete adapters and factory. Use existing native operations for speech, targeting, health, position, data and timers. Implement value/NBT/item operations against exact local source. Explicitly throw `UnsupportedOperationException` for remaining interface operations and list each in the capability document.
- [ ] Run `./gradlew test --tests '*XenoApiAdaptersTest' --tests '*ScriptNpcDataTest' --tests '*ScriptTimersTest' -PofflineMcMeta`; require all selected tests to pass.

## Task 3: Register the server API implementation

Create `npc/script/api/xeno/NativeNpcApi.java` extending `NpcAPI` and
`npc/script/api/xeno/XenoDamageSourceAdapter.java` implementing `IDamageSource`.
Modify `XenoPixelsMod.java` for one registration call.
Test: `npc/script/api/xeno/NativeNpcApiTest.java`.

Interfaces: `NativeNpcApi` implements the exact abstract signatures in
`XenoNPCsAPI/src/main/java/xenoapi/npcs/api/NpcAPI.java`; it consumes Task 2's factory.

- [ ] Add failing tests for singleton availability, duplicate registration behavior, world-dependent access without an active server, and stale-server rejection after shutdown. Isolate singleton tests so global registration does not contaminate other tests.
- [ ] Implement entity/world/item/NBT/position/damage-source conversion and native NPC creation/spawning through the existing entity registry. Resolve the current server per call. Validate side/thread and inputs before mutation.
- [ ] Implement a dedicated NeoForge API event bus using the exact configured bus API. Do not advertise gameplay event delivery yet. Unsupported handlers, GUI/mail and other facilities throw with the API method name; document each.
- [ ] Register once during mod construction without requiring a world or loading client classes. Add tests that unsupported operations have no partial side effects.
- [ ] Run `./gradlew test --tests '*NativeNpcApiTest' --tests '*XenoApiAdaptersTest' -PofflineMcMeta` and `./gradlew compileJava -PofflineMcMeta`.

## Task 4: Add script access without changing old calls

Modify `npc/script/NpcScriptHost.java`, `npc/script/PlayerScriptHost.java`,
`npc/script/api/NativeXenoScriptApi.java` and the existing editor binding-help owner located through `NpcScriptHost.BINDINGS` call sites.
Create test `npc/script/api/xeno/XenoApiScriptCompatibilityTest.java`.

Interfaces: scripts gain `XenoAPI` bound to the registered `NativeNpcApi`.
Add `ICustomNpc`/`IEntity`/`IPlayer` overloads to existing extension methods where
their current parameters are `ScriptNpc`/`ScriptEntity`/`ScriptPlayer`.
Preserve existing overloads; delegate new calls through explicit native conversion.

- [ ] Add failing Nashorn tests that old wrappers and typed adapters both resolve representative extension calls, including omitted/default parameters and null targets. Assert existing event fields and `event.API` remain available.
- [ ] Add the binding to NPC and player scopes and editor help. Implement typed overloads, preserving argument order, return values and server checks. Resolve any overload ambiguity with explicit conversion entry points rather than changing old signatures.
- [ ] Run `./gradlew test --tests '*XenoApiScriptCompatibilityTest' --tests '*NpcScriptEngineSeamTest' -PofflineMcMeta`.
- [ ] Check all 22 existing JavaScript examples through available syntax/engine checks. Report unavailable gameplay fixtures honestly; do not equate parsing with behavior verification.

## Task 5: Consumer proof and completion evidence

Modify the existing consumer under `examples/xenopixels-api-addon/src/main/java/`
after locating its production API exercise entry point; add an availability and
typed conversion probe there. Update `examples/xenopixels-api-addon/README.md`
and `docs/native-xenoapi-adapters.md`.
Create `ai/handoff-native-xenoapi-adapters-2026-09-27.md` using the repository template.

- [ ] Compile the separate consumer using only packaged public contracts; use `NpcAPI.IsAvailable()` and typed `getIEntity` results, with no native internal imports.
- [ ] Run `./gradlew buildApiExampleAddon -PofflineMcMeta`, `./gradlew test`, and `./gradlew build jarJar serverJar -PofflineMcMeta`. Record exact failures without fixing unrelated tests.
- [ ] Inspect final class contents and server jarjar count; record artifact byte sizes and SHA-256 hashes.
- [ ] Run a fresh `./gradlew runApiTestClient`; exercise native adapter conversion, speech, target access, timers and one XenoPixels extension call. Record log timestamps and observed actions individually. Leave unexercised gameplay as not verified.
- [ ] Review only this task's changes, inspect `git status --short`, and write the handoff with branch/hash, changed paths, commands, results, limitations and safe next steps. Do not commit.

## Self-review

The tasks cover all eight acceptance points in the approved spec. Existing
script signatures are explicitly preserved, and timer ownership is handled
before exposing the entry point. Unsupported operations and unposted events
are disclosed separately from implemented behavior. No runtime proof or
full API parity is assumed. Execution method and plan review are pending.
