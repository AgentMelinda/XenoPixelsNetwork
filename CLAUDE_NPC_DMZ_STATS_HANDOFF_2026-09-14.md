# Handoff — NPC real StatsData, and the build break it left behind

**Date:** 2026-09-14
**Branch:** `1.21.1`
**Plan of record:** `C:\Users\Admin\.cursor\plans\NPC StatsData and ki crush-bc3188cc.plan.md`

## What was already done before this session

Phase 1 of the plan — the actual crush fix — was complete on arrival and is untouched here:

- `compat/npc/NpcKiProjectileMath.java` replaces the linear `0.5 + kiPower * 0.01` size and
  `1.2 + kiPower * 0.02` speed with `KiAttackData.getDefaultSizeForType/getDefaultSpeedForType`
  times charge, clamped to `XenoServerConfig.kiProjectileMaxSize` / `kiProjectileMaxSpeed`
  (320 / 32).
- `NpcKiAttackDispatcher.fireKiBlast`, `fireKiWave` and `fireClashWave` all call it.
  `fireMirroredWave` and the named techniques were correctly left on `KiAttackData`'s own sizes.
- `NpcKiProjectileMathTest` covers the `Integer.MAX_VALUE` case.

`NpcKiAttackDispatcher.kiDamage` and `mixin/common/StatsProviderNpcGetMixin.java` had also been
written, both calling a `compat.npc.NpcDmzStats` that **did not exist anywhere in the tree or in any
build output**. `./gradlew compileJava` failed with three errors because of it, so nothing in the
repository could be compiled or tested at all.

## What this session added

| File | Change |
|---|---|
| `compat/npc/NpcDmzStats.java` | **new.** The attachment, the lookups the two in-flight callers expected (`stats`, `optional`), `getOrCreate`, `clear`, and `syncFromProfile`. |
| `XenoPixelsMod.java` | registers the attachment's `DeferredRegister` on the mod bus. |
| `xenopixelsmod.mixins.json` | registers `common.StatsProviderNpcGetMixin`, which was written but never listed and so would never have applied. |
| `compat/npc/NpcCounterpartSync.java` | `apply` syncs the profile into the blob behind the existing authoritative + fingerprint gate; `restoreAfterAuthority` drops the blob. |
| `src/test/.../NpcDmzStatsTest.java` | **new**, 6 tests. |
| `src/test/.../RepoRoot.java` | **new.** Locates the repository root for tests that read files from the tree. |

### The design decision that differs from the plan

The plan's Phase 2 step 2 called for mixins widening `Stats.setPlayer` to accept a `LivingEntity`,
plus registering DMZ's attributes onto the NPC entity types, so that a `StatsData` could be hosted
without a `FakePlayer`. That was not done. Reading the decompiled 2.1.3 source showed a much smaller
route: **`StatsData` already null-checks its player almost everywhere**, and `new StatsData(null)`
is a working blob for stats, resources, forms, skills, techniques and NBT persistence.

Exactly fourteen methods dereference the player without a guard, and they are all ones an NPC has no
business calling anyway — worn armour (`getDefense`, `getMaxDefense`), armour enchantments
(`getHealthRegenPerSecond`, `getEnergyRegenPerSecond`), the gravity/HTC family, and `getPlayer`.
`NpcDmzStats.safeForNpc` names that list, and `NpcDmzStatsTest` re-derives it from the decompiled
source and asserts set equality, so a DMZ update that moves a method across the line fails a test
instead of throwing at an NPC mid-fight.

The cost of this route, stated plainly: DMZ's secondary attributes are **not** registered on NPC
entity types, so `getSecondaryAttributeValue` returns its fallback for an NPC. Ki damage still comes
out of `kiPower`, scaling, the form multiplier and power release — the whole of `getKiDamage()`
except the secondary-attribute term, which is zero. If a later pass wants that term, registering the
attributes with `EntityAttributeModificationEvent` is still the way, and the blob does not have to
change to get it.

## Verified

All run 2026-09-14 from the repository root.

| Command | Result |
|---|---|
| `./gradlew compileJava -PofflineMcMeta` | BUILD SUCCESSFUL (was failing with 3 errors before this session) |
| `./gradlew test -PofflineMcMeta` | BUILD SUCCESSFUL — **193 classes, 1095 tests, 0 failures, 0 errors, 0 skipped** |
| `./gradlew build jarJar serverJar -PofflineMcMeta` | BUILD SUCCESSFUL |

Every DMZ symbol this depends on was checked with `javap` against
`libs/dragonminez-2.1.3.jar`, not only against the decompiled sources:

- `public static <T> LazyOptional<T> get(Capability<T>, Entity)` — the mixin's target, erased
  descriptor matches the injector.
- `public static final Capability<StatsData> INSTANCE` on `StatsCapability`.
- `public StatsData(Player)`, `public double getKiDamage()`, `public CompoundTag save()`,
  `public void load(CompoundTag) throws ClassNotFoundException`.

## Not done, and not verified

- **No client was launched.** The plan's `validate-live-kiblast` step is open: a max-stat
  authoritative NPC with brain v3 firing a kiblast has not been run. The crush fix is unit-tested
  arithmetic, not observed behaviour, and neither is the blob — no NPC has actually carried one.
- **The mixin is not proven to apply.** It is registered and the descriptor matches, but
  `StatsProvider` is loaded lazily and the test run does not load it. First in-game check should be
  that `StatsProvider.get(npc)` is present after an authoritative profile apply.
- **Skills and techniques are not copied** by `syncFromProfile`. Only STR/SKP/RES/VIT/PWR/ENE, race,
  active form, active stack form and power release are. The plan asks for skills and techniques too.
- **HP is unchanged.** `NpcVitalitySync` still owns max health with its own parity math against
  `getHealthBonus()` float rounding. The plan's step 5 — driving it through the same `DMZ Health
  Bonus` attribute modifier `StatsEvents` uses, and persisting via StatsData NBT — is not done.
- **The client `ProxyPlayer`** in `NpcFullDmzRenderer` still fills its StatsData from the profile
  rather than from synced blob NBT (plan step 6).
- The `forge:step_height_addition` / `forge:entity_gravity` AttributeMap warnings were not
  investigated. Per the plan they are a follow-up, and the quiet-spawn test it asks for has not been
  run.

## Unrelated finding, not acted on

`DmzMenuArtTest.everySheetTheMappingNamesWasGenerated` and its neighbour begin with
`if (!Files.isDirectory(GENERATED)) return;`, and `GENERATED` is a working-directory-relative path.
Under Gradle the test task does not run from the repository root, so **both checks return
immediately and assert nothing** while reporting as passing. Run from the repository root they fail:
`textures/gui/dmz_menus/neon/character_left.png` is named by the mapping but was never generated.
`RepoRoot` added here is what those tests would need to become real again. Left alone because it is
the neon menu work, not this plan.
