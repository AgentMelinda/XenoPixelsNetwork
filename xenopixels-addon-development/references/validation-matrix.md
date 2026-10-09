# Validation Matrix

## Evidence Levels

| Level | Proves | Does not prove |
| --- | --- | --- |
| Static inspection | Symbol, descriptor, path, metadata, or source ownership exists. | Code compiles or runs. |
| Unit test | Pure logic, bounds, migration, serialization, or policy behavior. | Loader wiring or in-game events. |
| Compilation | Current source type-checks against configured dependencies. | Startup, packet flow, or gameplay behavior. |
| Distribution build | Packaging and configured artifact tasks complete. | Dedicated-server safety or runtime behavior. |
| Startup test | Loader, mixins, registries, and initial classloading succeed. | The target gameplay action works. |
| Fresh runtime observation | The exercised action produced current logs or visible behavior. | Untested branches or other mod combinations work. |

Always describe the achieved level precisely.

## Exact XenoPixels Validation Ladder

Start narrowly, then expand:

1. Focused unit tests for changed logic.
2. `./gradlew test`.
3. `./gradlew build jarJar serverJar -PofflineMcMeta`.
4. `./gradlew buildApiExampleAddon -PofflineMcMeta` for public API or networking work.
5. Inspect the server jar and confirm it contains no entries below `META-INF/jarjar/`.
6. `./gradlew runApiTestClient` for API startup or packet proof when relevant.
7. Inspect a fresh `run/logs/latest.log` and exercise the exact gameplay action.
8. Run a dedicated server for common code, packet, NPC, or optional-mod classloading changes.

Do not fix unrelated failures while validating a focused change. Record them separately.

## External Addon Matrix

At minimum test:

- expected XenoPixels version on client and server;
- addon absent on one side when the declared dependency allows it;
- deliberately mismatched addon protocol;
- every optional third-party integration absent;
- each supported integration present alone;
- supported combinations such as MyNPCs plus CustomNPCs policy, or Create Aeronautics plus Sable;
- dedicated server with no client classes;
- configuration defaults and upgrade from the previous addon version.

## Focused Test Ownership

| Change | Focused tests |
| --- | --- |
| Public API | Example addon compilation, old consumer compatibility, side/thread contract |
| Packet | Codec bounds, invalid sender state, stale ids, mismatched protocol |
| NPC editor | Save allow-list, locks, permissions, entity removal |
| Dialogue/quest | Server lookup, stale option, idempotency, missing data |
| DMZ mixin | Exact target inspection, startup, exercised injection path |
| MyNPCs parity | MyNPCs-only, CustomNPCs-only, neither, dedicated server |
| Ship controls | Seat ownership, timeout, disconnect, unload |
| Guidance/ballistics | Coordinate transforms, invalid target, lifetime, collision |
| Data migration | Old format, malformed data, repeat run, rollback/source preservation |

## Runtime Log Review

Use a process started after the latest rebuild. Search the current log for:

- mixin target, descriptor, injection, or classloading failures;
- registry conflicts and missing identifiers;
- packet registration, decode, direction, or disconnect errors;
- optional dependency linkage errors;
- dedicated-server attempts to load client classes;
- rejected NPC saves, dialogue actions, ship controls, or permissions;
- new warnings adjacent to the exercised feature.

An old process can continue running old bytecode after a successful rebuild. Stop it before claiming
the latest artifact was tested.

## Handoff Record

Record:

- explicit date and branch;
- full commit hashes when relevant;
- dirty paths before and after;
- exact dependency versions and artifact SHA-256 values;
- exact commands and exit results;
- artifact names, sizes, and hashes;
- observed runtime actions and matching log evidence;
- items not verified;
- safe next steps.
