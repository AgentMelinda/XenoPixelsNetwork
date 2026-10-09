# Effekseer FX (Hakai, missiles, DMZ punches) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Punches, Hakai and missiles play Effekseer effects through AAA Particles, replacing the
vanilla particles for those uses, with a server config.

**Architecture:**
- **One server-side entry point, `fx/effek/XenoEffects`.** It asks the pure `EffectGate`
  (config, range, per-tick cap, punch de-duplication) whether to play, then sends through an
  `EffekSender`, which is AAA Particles in the game and a fake in tests.
- **Named slots.** `EffectSlot` maps each use to an effect file under
  `assets/xenopixelsmod/effeks/<slot>/`.
- **Hooks.** Existing call sites (`CombatFx`, `HakaiFx`, the missile entity, `ShipBallisticTicker`)
  call `XenoEffects`, and fall back to today's vanilla particles when it refuses.

**Tech Stack:** Java 21, NeoForge 21.1.248, MDG 2.0.143, AAA Particles 2.3.1 (local jar), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-28-effekseer-fx-design.md`

## Global Constraints

- **Dependency:** AAA Particles is **required**. `neoforge.mods.toml` declares `aaa_particles`
  version `[2.3.1,)`, `type="required"`, `side="BOTH"`.
- **Build:** `implementation files('libs/aaa_particles-neoforge-1.21.1-2.3.1.jar')`, following the
  DragonMineZ local-jar precedent; builds must work offline with `-PofflineMcMeta`.
- **Assets:** effects live at `assets/xenopixelsmod/effeks/<slot>/<slot>.efkefc`, and the asset id
  is `xenopixelsmod:<slot>/<slot>`.
- **Stand-ins:** CC-0 Effekseer 1.80.6 samples from `tools/new_particles/Effekseer1.80.6Win/Sample`;
  no HDR samples.
- **Config defaults:**

  | Key | Default |
  | --- | --- |
  | `effekseerEnabled`, `effekseerPunches`, `effekseerHakai`, `effekseerMissiles` | true |
  | `effekseerRange` | 64 |
  | `effekseerMissileRange` | 256 |
  | `effekseerPunchesPerTick` | 24 |
- **Replacement, not layering.** Vanilla particles return only when `XenoEffects` refuses: config
  off, or the cap was reached. Sounds are unchanged.
- **Repo rule:** work stays uncommitted. "Commit" steps below mean: append the ledger line instead.

## Review Focus

1. **Many hits in one tick** (Z-burst, rush, an AoE on a crowd): no more than
   `effekseerPunchesPerTick` punch effects, and no crash. Covered by a test in Task 3.
2. **A combo hit that goes through both `CombatFx.impact` and the damage hook** plays once.
   Covered by a de-duplication test in Task 3.
3. **Config off at runtime**: everything goes back to vanilla particles. Covered by a Task 4 test
   where a disabled gate means the fake sender receives nothing.
4. **A missing or renamed effect file**: the build test fails before shipping. Covered by the
   Task 1 asset test, which also checks the textures each file references.
5. **A dedicated server with AAA Particles on it**: our common code references only AAA
   *common* classes (`AAALevel`, `ParticleEmitterInfo`). Covered in Task 8 by checking that the
   server jar has no references to `mod.chloeprime.aaaparticles.client`.

---

### Task 1: Dependency and stand-in effect assets

**Files:**
- Create: `libs/aaa_particles-neoforge-1.21.1-2.3.1.jar`, copied from `tools/new_particles/`
- Modify: `build.gradle` (dependencies block, next to `implementation files(dragonMineZJar)`)
- Modify: `src/main/resources/META-INF/neoforge.mods.toml` (add a `[[dependencies.${mod_id}]]` block)
- Create: `src/main/resources/assets/xenopixelsmod/effeks/<slot>/...`, one folder per slot
- Create: `src/main/resources/assets/xenopixelsmod/effeks/CREDITS.txt`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/fx/effek/EffectAssetsTest.java`

**Interfaces:**
- Produces: seven asset folders:

  | Folder | Stand-in sample and textures |
  | --- | --- |
  | `punch_impact` | `ToonHit.efkefc` + `Parts/` |
  | `punch_heavy` | `ToonHit` + `Parts/` |
  | `punch_guard` | `ToonHit` + `Parts/` |
  | `hakai_channel` | `Simple_Ring_Shape2` + `Texture/`, with `Flame01.png` replaced by the swirl `example_for_hakai_particle (2).png` |
  | `hakai_erase` | `Simple_Ring_Shape2` + `Texture/`, with `Flame01.png` replaced by `example_for_hakai_particle (1).png` |
  | `missile_explosion` | `Simple_Turbulence_Fireworks` + `Texture/` |
  | `missile_thruster` | `Simple_Track1` |

  Each effect is renamed to `<slot>.efkefc`.

- [ ] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.fx.effek;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Every effect slot ships an .efkefc whose referenced textures, materials and models exist. */
class EffectAssetsTest {
    static final Path EFFEKS = Path.of(System.getProperty("xenopixels.projectDir"),
            "src/main/resources/assets/xenopixelsmod/effeks");
    static final String[] SLOTS = {"punch_impact", "punch_heavy", "punch_guard", "hakai_channel",
            "hakai_erase", "missile_explosion", "missile_thruster"};

    /** Relative asset paths stored in the file (Effekseer writes them as UTF-16LE strings). */
    static List<String> references(Path efk) throws Exception {
        String utf16 = new String(Files.readAllBytes(efk), StandardCharsets.UTF_16LE);
        Matcher m = Pattern.compile("[\\w./ -]+\\.(?:png|dds|tga|efkmat|efkmodel)", Pattern.CASE_INSENSITIVE)
                .matcher(utf16);
        List<String> out = new ArrayList<>();
        while (m.find()) out.add(m.group().trim());
        return out;
    }

    @Test
    void everySlotHasItsEffectAndEveryReferencedFile() throws Exception {
        for (String slot : SLOTS) {
            Path efk = EFFEKS.resolve(slot).resolve(slot + ".efkefc");
            assertTrue(Files.isRegularFile(efk), "missing " + efk);
            for (String ref : references(efk)) {
                assertTrue(Files.isRegularFile(efk.getParent().resolve(ref)), slot + " references missing " + ref);
            }
        }
    }

    @Test
    void theStandInsAreCreditedAsCcZero() throws Exception {
        String credits = Files.readString(EFFEKS.resolve("CREDITS.txt"));
        assertTrue(credits.contains("CC-0") && credits.contains("Effekseer"));
    }
}
```

- [ ] **Step 2: Run the test and see it fail**

Run: `./gradlew test --tests '*EffectAssetsTest' -PofflineMcMeta`
Expected: FAIL (`missing ...punch_impact.efkefc`).

- [ ] **Step 3: Add the dependency and assets**
  - Copy the jar into `libs/`.
  - In `build.gradle`, add `implementation files('libs/aaa_particles-neoforge-1.21.1-2.3.1.jar')`
    next to the DragonMineZ line.
  - In `neoforge.mods.toml`, append:

```toml
[[dependencies.${mod_id}]]
modId="aaa_particles"
type="required"
versionRange="[2.3.1,)"
ordering="NONE"
side="BOTH"
```

  - Copy each sample and its referenced files into its slot folder, keeping relative paths
    (`Texture/…` or `Parts/…`). Rename the `.efkefc` to `<slot>.efkefc`. Replace `Flame01.png` in
    the two Hakai folders with the swirl PNGs.
  - `CREDITS.txt`: "Stand-in effects are Effekseer 1.80.6 samples (CC-0). Thanks to Effekseer and
    the sample authors (Tktk03 for ToonHit)."

- [ ] **Step 4: Run the test and see it pass**

Run: `./gradlew test --tests '*EffectAssetsTest' -PofflineMcMeta`
Expected: PASS. Then `./gradlew compileJava -PofflineMcMeta` → BUILD SUCCESSFUL.

- [ ] **Step 5: Ledger line** (Task 1 complete, tests named).

---

### Task 2: Server config keys

**Files:**
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/config/XenoServerConfig.java`. Add the
  static fields near `missileTerminalGravityCompensation` (~line 258), in `snapshot()`
  (~line 1241), in `apply(Data)` (~line 1543), and in `Data` (~line 2850).
- Test: `src/test/java/net/bullettrain/xenopixelsmod/config/EffekseerConfigTest.java`

**Interfaces:**
- Produces: the public static fields `effekseerEnabled`, `effekseerPunches`, `effekseerHakai`,
  `effekseerMissiles` (boolean, true), `effekseerRange` (int, 64), `effekseerMissileRange`
  (int, 256) and `effekseerPunchesPerTick` (int, 24).

- [ ] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffekseerConfigTest {
    @Test
    void defaultsRoundTripAndClampThroughTheSavedData() {
        XenoServerConfig.Data saved = XenoServerConfig.snapshot();
        try {
            XenoServerConfig.Data d = new XenoServerConfig.Data();
            assertTrue(d.effekseerEnabled && d.effekseerPunches && d.effekseerHakai && d.effekseerMissiles);
            assertEquals(64, d.effekseerRange);
            assertEquals(256, d.effekseerMissileRange);
            assertEquals(24, d.effekseerPunchesPerTick);
            d.effekseerHakai = false;
            d.effekseerRange = -5;
            d.effekseerMissileRange = 99_999;
            d.effekseerPunchesPerTick = 0;
            XenoServerConfig.apply(d);
            assertFalse(XenoServerConfig.effekseerHakai);
            assertEquals(8, XenoServerConfig.effekseerRange, "clamped to at least 8 blocks");
            assertEquals(2048, XenoServerConfig.effekseerMissileRange, "clamped to at most 2048 blocks");
            assertEquals(1, XenoServerConfig.effekseerPunchesPerTick, "at least one per tick");
            assertFalse(XenoServerConfig.snapshot().effekseerHakai);
        } finally {
            XenoServerConfig.apply(saved);
        }
    }
}
```

- [ ] **Step 2: Run and see it fail** (compile error: no `effekseerEnabled`).
- [ ] **Step 3: Implement**
  - Add the fields. Each gets a one-line doc comment, following the neighbouring fields' style.
  - In `snapshot()`, add `d.<key> = <key>;` for each.
  - In `apply(Data)`, add the booleans directly, and the clamps:
    - `effekseerRange = Math.max(8, Math.min(512, d.effekseerRange));`
    - `effekseerMissileRange = Math.max(8, Math.min(2048, d.effekseerMissileRange));`
    - `effekseerPunchesPerTick = Math.max(1, Math.min(512, d.effekseerPunchesPerTick));`
  - In `Data`, add public fields with the defaults.
- [ ] **Step 4: Run and see it pass**, plus `XenoServerConfigKeysTest` still passes.
- [ ] **Step 5: Ledger line.**

---

### Task 3: EffectGate (pure rules)

**Files:**
- Create: `src/main/java/net/bullettrain/xenopixelsmod/fx/effek/EffectSlot.java`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/fx/effek/EffectGate.java`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/fx/effek/EffectGateTest.java`

**Interfaces:**
- Produces: `enum EffectSlot { PUNCH_IMPACT, PUNCH_HEAVY, PUNCH_GUARD, HAKAI_CHANNEL, HAKAI_ERASE, MISSILE_EXPLOSION, MISSILE_THRUSTER }`,
  each with:
  - `String path()`: `"<slot>/<slot>"`, lower-case name
  - `Category category()`: `PUNCH`, `HAKAI` or `MISSILE`
  - `float defaultScale()`: 1.0, with 1.6 for `PUNCH_HEAVY`
- Produces: `final class EffectGate`, with:
  - `boolean allow(EffectSlot slot, long gameTick, int targetId)`. `targetId` is -1 when the
    effect has no target. This applies config, the per-tick cap for PUNCH, and per-target-per-tick
    punch de-duplication.
  - `double range(EffectSlot slot)`
  - `void reset()`

- [ ] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.fx.effek;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffectGateTest {
    private final XenoServerConfig.Data saved = XenoServerConfig.snapshot();
    private final EffectGate gate = new EffectGate();

    @AfterEach
    void restore() { XenoServerConfig.apply(saved); }

    @Test
    void slotsNameTheirFolders() {
        assertEquals("punch_heavy/punch_heavy", EffectSlot.PUNCH_HEAVY.path());
        assertEquals(EffectGate.Category.MISSILE, EffectSlot.MISSILE_THRUSTER.category());
    }

    @Test
    void configSwitchesEachCategoryAndTheWhole() {
        XenoServerConfig.effekseerHakai = false;
        assertFalse(gate.allow(EffectSlot.HAKAI_CHANNEL, 1, 7));
        assertTrue(gate.allow(EffectSlot.MISSILE_EXPLOSION, 1, -1));
        XenoServerConfig.effekseerEnabled = false;
        assertFalse(gate.allow(EffectSlot.MISSILE_EXPLOSION, 2, -1));
    }

    @Test
    void oneTargetGetsOnePunchPerTick() {
        assertTrue(gate.allow(EffectSlot.PUNCH_IMPACT, 10, 42));
        assertFalse(gate.allow(EffectSlot.PUNCH_HEAVY, 10, 42), "same target, same tick");
        assertTrue(gate.allow(EffectSlot.PUNCH_IMPACT, 10, 43));
        assertTrue(gate.allow(EffectSlot.PUNCH_IMPACT, 11, 42), "next tick is fine");
    }

    @Test
    void punchesStopAtThePerTickCap() {
        XenoServerConfig.effekseerPunchesPerTick = 3;
        int played = 0;
        for (int target = 0; target < 50; target++) if (gate.allow(EffectSlot.PUNCH_IMPACT, 5, target)) played++;
        assertEquals(3, played);
        assertTrue(gate.allow(EffectSlot.MISSILE_EXPLOSION, 5, -1), "the cap is for punches only");
        assertTrue(gate.allow(EffectSlot.PUNCH_IMPACT, 6, 0), "a new tick resets it");
    }

    @Test
    void rangesComeFromConfig() {
        assertEquals(64, gate.range(EffectSlot.PUNCH_IMPACT));
        assertEquals(64, gate.range(EffectSlot.HAKAI_ERASE));
        assertEquals(256, gate.range(EffectSlot.MISSILE_EXPLOSION));
    }
}
```

- [ ] **Step 2: Run and see it fail** (compile: no `EffectSlot`).
- [ ] **Step 3: Implement**
  - `EffectGate` keeps `long tick` and `int punchesThisTick`, plus an `IntOpenHashSet` (fastutil,
    already on the classpath) of targets punched this tick. All of them reset when `gameTick`
    changes.
  - `allow` returns false when `!effekseerEnabled` or the category switch is off. For PUNCH it
    also refuses when the target is already in the set, or `punchesThisTick >= effekseerPunchesPerTick`.
    Otherwise it records the target and returns true.
  - `range` gives MISSILE → `effekseerMissileRange` and everything else → `effekseerRange`.
- [ ] **Step 4: Run and see it pass.**
- [ ] **Step 5: Ledger line.**

---

### Task 4: XenoEffects (the one place that talks to AAA Particles)

**Files:**
- Create: `src/main/java/net/bullettrain/xenopixelsmod/fx/effek/EffekSender.java`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/fx/effek/AaaEffekSender.java`
- Create: `src/main/java/net/bullettrain/xenopixelsmod/fx/effek/XenoEffects.java`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/fx/effek/XenoEffectsTest.java`

**Interfaces:**
- Consumes: `EffectGate`, `EffectSlot` (Task 3).
- Produces:
  - `interface EffekSender { void send(ServerLevel level, Request request); }`
  - `record Request(ResourceLocation id, Vec3 pos, Vec3 forward, float scale, double range, Entity bound, boolean velocityRotation)`
  - `XenoEffects.play(ServerLevel level, EffectSlot slot, Vec3 pos, Vec3 forward, float scale, int targetId)`
    and `XenoEffects.playBound(ServerLevel level, EffectSlot slot, Entity entity, Vec3 offset, float scale, boolean velocityRotation)`.
    Both return a boolean: true when the effect was sent, so the caller skips its vanilla particles.
  - `static void useSender(EffekSender)` (tests), and `static void resetForTest()`.
- **AAA symbols** (javap-check them against the jar in Step 3):
  - `mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo.create(Level, ResourceLocation)`
  - `.position(Vec3)`, `.rotationFromForward(Vec3)`, `.scale(float)`, `.bindOnEntity(Entity)`,
    `.entitySpaceRelativePosition(Vec3)`, `.useEntityVelocityAsRotation()`
  - `mod.chloeprime.aaaparticles.api.common.AAALevel.addParticle(Level, double, ParticleEmitterInfo)`

- [ ] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.fx.effek;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XenoEffectsTest {
    private final XenoServerConfig.Data saved = XenoServerConfig.snapshot();
    private final List<EffekSender.Request> sent = new ArrayList<>();

    @AfterEach
    void restore() { XenoServerConfig.apply(saved); XenoEffects.resetForTest(); }

    @Test
    void playsTheSlotsFileAtThePointWithRangeAndScale() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        assertTrue(XenoEffects.play(null, EffectSlot.PUNCH_HEAVY, new Vec3(1, 2, 3), new Vec3(0, 0, 1), 1.0f, 9));
        EffekSender.Request r = sent.get(0);
        assertEquals("xenopixelsmod:punch_heavy/punch_heavy", r.id().toString());
        assertEquals(new Vec3(1, 2, 3), r.pos());
        assertEquals(1.6f, r.scale(), 1e-6, "slot default scale x requested scale");
        assertEquals(64, r.range());
    }

    @Test
    void aClosedGateSendsNothingSoTheCallerDrawsVanilla() {
        XenoEffects.useSender((level, r) -> sent.add(r));
        XenoServerConfig.effekseerEnabled = false;
        assertFalse(XenoEffects.play(null, EffectSlot.MISSILE_EXPLOSION, Vec3.ZERO, new Vec3(0, 1, 0), 2f, -1));
        assertTrue(sent.isEmpty());
    }

    @Test
    void aSenderFailureFallsBackToVanilla() {
        XenoEffects.useSender((level, r) -> { throw new IllegalStateException("library changed"); });
        assertFalse(XenoEffects.play(null, EffectSlot.MISSILE_EXPLOSION, Vec3.ZERO, new Vec3(0, 1, 0), 1f, -1));
    }
}
```

- [ ] **Step 2: Run and see it fail.**
- [ ] **Step 3: Implement**
  - Check the symbols first:
    `javap -cp libs/aaa_particles-neoforge-1.21.1-2.3.1.jar mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo mod.chloeprime.aaaparticles.api.common.AAALevel`
  - `XenoEffects` holds one `EffectGate` and one `EffekSender` (default `AaaEffekSender`).
    - `play` gets the game tick from `level == null ? 0 : level.getGameTime()` and asks the gate.
    - It builds the request with `ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, slot.path())`
      and `scale * slot.defaultScale()`.
    - It calls `send` inside `try/catch (RuntimeException | LinkageError)` and logs a failure once
      per slot.
  - `AaaEffekSender.send` does:
    `var info = ParticleEmitterInfo.create(level, id);`, then either
    `info.bindOnEntity(bound).entitySpaceRelativePosition(offset)` (plus
    `.useEntityVelocityAsRotation()` when requested) or `info.position(pos).rotationFromForward(forward)`,
    then `info.scale(scale); AAALevel.addParticle(level, range, info);`.
- [ ] **Step 4: Run and see it pass.**
- [ ] **Step 5: Ledger line.**

---

### Task 5: Punch effects (player and NPC)

**Files:**
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/combat/fx/CombatFx.java`
  (`impact(ServerLevel, Vec3, Vec3, Weight)`, ~line 83)
- Create: `src/main/java/net/bullettrain/xenopixelsmod/fx/effek/PunchEffectEvents.java`
- Test: `src/test/java/net/bullettrain/xenopixelsmod/fx/effek/PunchEffectRulesTest.java`

**Interfaces:**
- Consumes: `XenoEffects.play` (Task 4).
- Produces:
  - `CombatFx.impact` plays `PUNCH_GUARD` for GUARD, `PUNCH_HEAVY` for HEAVY/ULTIMATE (ULTIMATE
    scale 1.6), and `PUNCH_IMPACT` for LIGHT. It skips `shockwave` and `sparks` when the effect was
    sent, and always keeps `broadcast`.
  - New `impact(ServerLevel, Entity, Vec3, Weight)` overload passes `target.getId()`.
  - `PunchEffectRules.isDmzMelee(DamageSource, LivingEntity attacker)` is pure: true when the direct
    entity is the attacker (not a projectile), the damage type is `minecraft:player_attack` or
    `minecraft:mob_attack`, and the attacker is a player or has a DMZ profile.
  - `PunchEffectEvents` listens to `LivingDamageEvent.Post` (only when damage > 0). It calls
    `CombatFx.impact(level, target, target.position().subtract(attacker.position()), Weight.LIGHT)`.
    The gate de-duplicates hits that a combo already drew.

- [ ] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.fx.effek;

import net.minecraft.world.damagesource.DamageTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PunchEffectRulesTest {
    @Test
    void onlyDirectMeleeDamageTypesCount() {
        assertTrue(PunchEffectRules.isMeleeType(DamageTypes.PLAYER_ATTACK));
        assertTrue(PunchEffectRules.isMeleeType(DamageTypes.MOB_ATTACK));
        assertFalse(PunchEffectRules.isMeleeType(DamageTypes.ARROW));
        assertFalse(PunchEffectRules.isMeleeType(DamageTypes.EXPLOSION));
        assertFalse(PunchEffectRules.isMeleeType(DamageTypes.MAGIC));
    }
}
```

- [ ] **Step 2: Run and see it fail.**
- [ ] **Step 3: Implement**
  - Check the event name first:
    `unzip -p build/moddev/artifacts/neoforge-21.1.248-sources.jar net/neoforged/neoforge/event/entity/living/LivingDamageEvent.java | grep -n "class Post"`
  - `PunchEffectRules.isMeleeType(ResourceKey<DamageType>)` checks membership in those two keys.
  - `isDmzMelee(source, attacker)` is `source.getDirectEntity() == attacker && source.typeHolder().unwrapKey().map(PunchEffectRules::isMeleeType).orElse(false) && (attacker instanceof Player || NpcCombatProfile.hasProfile(attacker))`.
  - `PunchEffectEvents` uses `@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)`. On
    `LivingDamageEvent.Post`, when the level is a `ServerLevel`, the attacker is a `LivingEntity`,
    `event.getNewDamage() > 0` and `isDmzMelee`, it calls `CombatFx.impact(...)` with `Weight.LIGHT`.
  - In `CombatFx.impact(ServerLevel, Vec3, Vec3, Weight)`, add a `targetId` parameter (-1 from the
    position overload). Before `shockwave`: `boolean effek = XenoEffects.play(level, slotFor(weight), pos, dir, weight == Weight.ULTIMATE ? 1.6f : 1.0f, targetId);`
    Then `if (!effek) { shockwave(...); sparks(...); }` and keep `broadcast(...)` unconditional.
- [ ] **Step 4: Run and see it pass**, plus the full suite (combat tests still green).
- [ ] **Step 5: Ledger line.**

---

### Task 6: Hakai effects

**Files:**
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/combat/fx/HakaiFx.java` (`tick`, `burst`)
- Test: `src/test/java/net/bullettrain/xenopixelsmod/fx/effek/HakaiEffectCadenceTest.java`

**Interfaces:**
- Consumes: `XenoEffects.playBound`, `XenoEffects.play`.
- Produces: `HakaiFx.channelPulseDue(long gameTick, int targetId)`, true every 10 ticks per target,
  staggered by target id.
  - In `tick`, when the pulse is due, it calls
    `XenoEffects.playBound(level, HAKAI_CHANNEL, target, (0, height*0.55, 0), 0.6f + 0.8f*progress, false)`.
    If that returns true it skips `splat` and `casterAura` (the silhouette `dissolve` stays: it is
    the body fade, not particles). If it returns false, today's dust runs.
  - In `burst`, it calls `XenoEffects.play(level, HAKAI_ERASE, centre, up, 1.4f, target.getId())`
    and uses the vanilla `splat` only when that returns false.

- [ ] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.fx.effek;

import net.bullettrain.xenopixelsmod.combat.fx.HakaiFx;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HakaiEffectCadenceTest {
    @Test
    void theChannelEffectPulsesEveryTenTicksPerTarget() {
        int pulses = 0;
        for (long t = 0; t < 100; t++) if (HakaiFx.channelPulseDue(t, 7)) pulses++;
        assertEquals(10, pulses);
        assertNotEquals(HakaiFx.channelPulseDue(3, 3), HakaiFx.channelPulseDue(3, 4), "staggered by target");
    }
}
```

- [ ] **Step 2: Run and see it fail.**
- [ ] **Step 3: Implement** as described: `channelPulseDue` returns `Math.floorMod(gameTick + targetId, 10) == 0`.
- [ ] **Step 4: Run and see it pass**, plus the Hakai tests (`HakaiFade*`, `HakaiChannel*`).
- [ ] **Step 5: Ledger line.**

---

### Task 7: Missile effects (tube and ship)

**Files:**
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/missile/BallisticMissileEntity.java`
  (`tick` server branch, `detonate`, `clientTrail`)
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/vs/ShipBallisticTicker.java` (the plume
  every 10 ticks at ~line 120, and `softArrive`)
- Test: `src/test/java/net/bullettrain/xenopixelsmod/fx/effek/MissileEffectRulesTest.java`

**Interfaces:**
- Consumes: `XenoEffects`.
- Produces: `MissileEffectRules`, pure:
  - `boolean thrusterPulseDue(long tick)`: true every 8 ticks.
  - `float explosionScale(float power)`: `clamp(0.5 + power / 4, 0.5, 6)`.
  - `Vec3 nozzle(Vec3 centre, Vec3 velocity, double length)`: behind the centre along
    `-velocity`, or below when the velocity is about zero.
- Tube missile:
  - While in EJECT or BOOST, every 8 ticks on the server, it calls
    `XenoEffects.playBound(level, MISSILE_THRUSTER, this, Vec3.ZERO, sizeScale, true)`, which follows
    the missile and points along its velocity.
  - It sets a synced flag `DATA_EFFEK_TRAIL` to the result, and `clientTrail` skips the vanilla
    flame while the flag is true.
  - `detonate` calls `XenoEffects.play(sl, MISSILE_EXPLOSION, position(), up, explosionScale(power), -1)`
    for every warhead path (native, vanilla and canceled-by-addon), in addition to the explosion
    itself.
- Ship missile:
  - The 10-tick plume becomes `XenoEffects.play(level, MISSILE_THRUSTER, nozzle(...), -velocity, 1.5f, -1)`
    every 8 ticks, with the vanilla FLAME and SMOKE only when that returns false.
  - `softArrive` plays `MISSILE_EXPLOSION` at scale 2, with the vanilla CLOUD only when that
    returns false.

- [ ] **Step 1: Write the failing test**

```java
package net.bullettrain.xenopixelsmod.fx.effek;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MissileEffectRulesTest {
    @Test
    void thrusterPulsesEveryEightTicks() {
        int n = 0;
        for (long t = 0; t < 80; t++) if (MissileEffectRules.thrusterPulseDue(t)) n++;
        assertEquals(10, n);
    }

    @Test
    void explosionScaleFollowsBlastPowerWithinBounds() {
        assertEquals(0.5f, MissileEffectRules.explosionScale(0f), 1e-6);
        assertEquals(1.5f, MissileEffectRules.explosionScale(4f), 1e-6);
        assertEquals(6f, MissileEffectRules.explosionScale(1_000f), 1e-6);
    }

    @Test
    void theNozzleIsBehindTheFlightDirection() {
        Vec3 n = MissileEffectRules.nozzle(new Vec3(0, 100, 0), new Vec3(0, 0, 10), 3);
        assertEquals(new Vec3(0, 100, -3), n);
        assertEquals(new Vec3(0, 97, 0), MissileEffectRules.nozzle(new Vec3(0, 100, 0), Vec3.ZERO, 3));
    }
}
```

- [ ] **Step 2: Run and see it fail.**
- [ ] **Step 3: Implement** as described.
  - The synced flag follows the entity's existing `EntityDataAccessor` pattern
    (`DATA_PHASE`/`DATA_SIZE`); define it in `defineSynchedData`.
  - Ruling to record: the vanilla explosion's own particles come from the explosion packet and
    stay. Only our extra particles are replaced.
- [ ] **Step 4: Run and see it pass**, plus `MissileGuidanceTest`, `MissileWarheadCompatTest` and
  the V3 tests.
- [ ] **Step 5: Ledger line.**

---

### Task 8: Docs, full verification, distribution check

**Files:**
- Create: `docs/effekseer-fx.md` (slots, config keys, how to replace an effect, the authoring
  guide from the spec)
- Modify: `CHANGELOG.md` (one Unreleased entry)

- [ ] **Step 1:** Write the docs.
- [ ] **Step 2:** Run the full suite with `./gradlew test -PofflineMcMeta`. Expected: all pass;
  record the count.
- [ ] **Step 3:** Run `./gradlew build jarJar serverJar -x test -PofflineMcMeta`. Expected:
  BUILD SUCCESSFUL.
- [ ] **Step 4:** Distribution checks.
  - The client jar contains `assets/xenopixelsmod/effeks/*/*.efkefc` (7 files).
  - `neoforge.mods.toml` in the jar declares `aaa_particles`.
  - The server jar's classes reference no `mod/chloeprime/aaaparticles/client`:
    `unzip -p build/libs/xenopixelsmod-Server-*.jar ... | grep -c` via `javap -c` on
    `AaaEffekSender`.
  - The server jar still has 0 `META-INF/jarjar/` files.
- [ ] **Step 5:** Ledger lines. "Not verified in game" covers how the effects look, the thruster
  following the missile, and a dedicated server run with AAA Particles installed.
