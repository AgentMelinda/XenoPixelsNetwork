# Reflection and string-symbol evidence

**Reviewed:** 2026-09-22 · **Branch:** `1.21.1` · **Mode:** `audit-project.mjs --strict-evidence`

Every reflective string that crosses a trust boundary, with the artifact it was checked against.
Per the project's security-evidence rule, "verified in jar" without an artifact hash and an
inspected symbol is not evidence — so each verified row below names the exact SHA-256 and the
`javap` output it came from, and each unverifiable row says so plainly rather than being assumed.

## Scope

The strict audit reports **531 string-based external symbols**. That headline is misleading and
should not be quoted on its own:

| Kind | Count | Fails when |
|---|---:|---|
| `@Inject` / `@Redirect` / … `method="…"` | 312 | classload, loudly |
| `@Mixin(targets="…")` | 70 | classload, loudly |
| `getMethod` / `getField` / `getConstructor("…")` | 125 | **runtime** |
| `Class.forName("…")` | 11 | **runtime** |

382 of the 531 are mixin declarations that Mixin itself resolves at startup — a wrong target is a
startup failure, not a silent one. The set that can rot unnoticed is the **136 reflective lookups**,
and of those only the boundary-crossing ones are recorded here.

## Verified

### MyNPCs clone controller

| Field | Content |
|---|---|
| **Symbol** | `espi.mynpcs.controllers.ServerCloneController`, `public static ServerCloneController Instance`, `public java.io.File getDir()` |
| **Artifact** | `run/mods/mynpcs-neoforge-1.5.0.jar`, SHA-256 `6bbfc44e883e197719c061ae50d1a94d565141acadfe5312524b4d1f3e0a1aaf` |
| **Evidence** | `javap -p -cp <extracted> espi.mynpcs.controllers.ServerCloneController` |
| **Side** | Server |
| **Authority** | None granted. Used only to locate the world folder for a migration that **never overwrites** an existing destination file and **never modifies** the source. |
| **Failure** | Fail-closed: `catch (ReflectiveOperationException \| RuntimeException \| LinkageError)` returns a failure `Result` and the migration does nothing. |
| **Runtime proof** | **None.** Not exercised in a running game. |

Both member shapes match how they are used: `Instance` is `static`, so `getField("Instance").get(null)`
is correct, and `getDir()` returns `java.io.File`, so the `(File)` cast is correct.

Used by `compat/npc/clone/NpcWorldMigrator.java` and `NpcCloneImport.java`.

### Create contraption controller position

| Field | Content |
|---|---|
| **Symbol** | `com.simibubi.create.content.contraptions.ControlledContraptionEntity`, `protected net.minecraft.core.BlockPos controllerPos` |
| **Artifact** | `run/mods/create-1.21.1-6.0.10.jar`, SHA-256 `ef87fe5709f1ba1f5b8bb20a2925b5afb4669e178fd6d8bf10c167759eefe37a` |
| **Evidence** | `javap -p -cp <extracted> com.simibubi.create.content.contraptions.ControlledContraptionEntity` |
| **Side** | Server |
| **Authority** | None. A lookup shortcut; the documented fallback is a bounded entity scan. |
| **Failure** | Fail-safe: the static initialiser leaves the field null and callers degrade to "no pulley", which every caller already handles. |
| **Runtime proof** | **None.** |

The member is `protected`, so `getDeclaredField` is the correct call — `getField` would have failed.
Used by `compat/create/elevator/ElevatorHelpers.java:58`.

## Not verifiable — artifact absent

None of these jars are present in the repository or `run/mods`, so their symbols **cannot** be
verified here and are recorded as unverified rather than assumed correct.

| Symbol | Mod | Authority on failure |
|---|---|---|
| `com.bugfunbug.linearreader.LinearRuntime`, `…linear.LinearRegionFile`, `isDirty()`, `flushRegionsBlocking(List)` | LinearReader | **None.** Fails closed and loudly: returns *"LinearReader is installed but its flush API did not match; regions were NOT flushed"* rather than reporting success. A previous version of this path returned a null error so `Result.ok()` stayed true and the command printed "World saved" having flushed nothing; that was fixed. |
| `eu.avalanche7.paradigm.Paradigm`, `getServices()`, `getHologramService()` | Paradigm | None. Cosmetic holograms. |
| `dev.devce.rocketnautics.api.FreeMotionEntity`, `…api.orbit.DeepSpaceHelper`, `…content.orbit.universe.PlanetDimensionData` | RocketNautics | None. Optional integration only. |
| `com.goodbird.cnpcgeckoaddon.mixin.IDataDisplay` | cnpc-gecko-addon | None. |
| `dev.egg.registries.BlockEntityRegistry$MoveInfo` | egg | None. |
| `dev.ryanhcode.sable.api.SubLevelHelper` | Sable | None. Used as a classpath probe in `ConditionalMixinPlugin`; absence resolves to `false`, so the mixin is **not** applied. |

**Conclusion for the unverified set:** none of them grants access, accepts a save, executes a
command, applies damage or enables a privileged feature on failure. Each either disables its own
isolated integration or reports an explicit error. That satisfies the fail-closed rule even though
the symbols themselves are unproven.

## Fail-open review

Four broad catches around external calls were inspected by hand. Three already failed closed:

- `server/WorldSaveFlush` — explicit error, never a false success.
- `mixin/ConditionalMixinPlugin` — unresolvable mod → `false` → mixin not applied.
- `compat/npc/NpcScriptSay`, `mixin/client/MinecraftFistOwnershipMixin` — both fail toward
  *suppressing* (chat not sent, attack withheld).

One did not, and was fixed on 2026-09-22:

- `compat/npc/NpcFormLookup.compatible()` returned **`true`** — "these forms may stack" — on a
  throw, with the exception swallowed unnamed. It gates `NpcTransformSystem:138` and `:330`, so a
  DragonMineZ change breaking `isIncompatibleWith` would have let an NPC stack two forms a pack
  declared incompatible, silently. It now refuses and logs. A null form on either side still
  answers yes: that is "nothing to be incompatible with", not a failed check.
  Covered by `NpcFormCompatibilityTest`.

## Outstanding

- **No runtime proof for any row above.** Every claim here is static: `javap` against a hashed
  artifact, plus source inspection of the failure path. Nothing in this document has been exercised
  in a running game, and per the project's own rule compilation is not proof that a path ran.
- The six optional mods without artifacts cannot be verified until their jars are available. They
  are safe by *failure behaviour*, not by *symbol evidence*.
