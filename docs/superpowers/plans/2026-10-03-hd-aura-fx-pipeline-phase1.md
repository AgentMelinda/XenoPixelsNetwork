# HD Aura v3 + FX Pipeline (Phase 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the first mergeable aura/pipeline PRs from the mega design: docs/tests for v3 playback (PR-A1), Effekseer tooling stamps + `Aura3EffectsTest` (PR-C1), a path-scoped aura3 asset refresh procedure (PR-A2), and client MotifTable Phase 1 brightness-only identity (PR-B1).

**Architecture:** Keep shipped v3 playback (aura3 silhouette + full v1 underlay + FP camera overlay). Harden evidence with tests. Motif Phase 1 only adjusts brightness/inner multipliers by form key — edge/thunder density stays generation-time (Phase 2 / later plan). Tournament, roles, race/hair makers are **Phase 2** (`docs/superpowers/plans/` follow-on) per design Chapter D.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.248, DragonMineZ 2.1.3, AAA Particles, Python Effekseer generator (`tools/effekseer/gen_effects.py`), JUnit 5 via Gradle.

**Spec:** `docs/superpowers/specs/2026-10-03-hd-aura-tournament-makers-design.md` (KD1–KD19, PR-A1/C1/A2/B1)

## Global Constraints

- NeoForge-only; mod id `xenopixelsmod`; never invent DMZ/Xeno APIs — verify in source first.
- Dirty tree: never `git add -A`; stage reviewed paths only; commit **only if the owner explicitly asks**.
- Default `auraVariant` stays `v1` (KD19); v3 is opt-in.
- Motif Phase 1 must **not** claim edge/thunder density changes (KD5 / KD13).
- Runtime claims need a fresh process + current log; build/tests ≠ in-game proof.
- Full aura look changes require full `gen_effects.py aura3` (no `--missing` for definition edits).
- Public API stays `api/**`; do not append packets to `ModNetwork` `"101"`.

## Review Focus

- First-person local player: HD aura must follow camera eye offset `{0,-0.6,-0.7}` at 0.45 scale, not vanish and not sit as a world egg at the feet.
- v3 emit: silhouette uses `plan(plan, Set.of(), true)`; underlay uses `plan(plan, extras, auraLayers)` with v1 shape/drop.
- Motif Phase 1: unknown form keys fall back to neutral multipliers (1.0 / existing brightness) — never throw.
- Aura3 matrix: every palette hex × six brightness × depth/_nz must exist after A2.
- Dirty-tree mass binary commit: A2 must use an explicit path list after `git status --short`.

## File map (Phase 1)

| File | Responsibility |
| --- | --- |
| `src/main/java/.../fx/aura/HdAuraPlan.java` | Variant parse, FP math, silhouette helpers (already mostly shipped) |
| `src/main/java/.../client/aura/HdAuraClient.java` | Emit / spawnLive; Motif Phase 1 call sites |
| `src/main/java/.../fx/aura/AuraMotifTable.java` | **Create** — MotifKey + brightness/inner multipliers |
| `src/test/java/.../fx/aura/HdAuraPlanTest.java` | FP + variant + playback helper tests |
| `src/test/java/.../fx/aura/AuraMotifTableTest.java` | **Create** — Phase 1 motif tests |
| `src/test/java/.../fx/aura/Aura3EffectsTest.java` | **Create** — 90×6×2 matrix like Aura2EffectsTest |
| `tools/effekseer/gen_effects.py` | `--missing` help lists aura3; optional BUILD_STAMP write |
| `docs/effekseer-fx.md` / `tools/effekseer/README.md` | Pipeline docs |
| `src/main/resources/.../effeks/aura3/` | Generated assets (A2 only) |

---

### Task 1: PR-A1 — Aura v3 docs/tests only (no behaviour change)

**Files:**
- Modify: `src/test/java/net/bullettrain/xenopixelsmod/fx/aura/HdAuraPlanTest.java`
- Modify (comments only if needed): `src/main/java/net/bullettrain/xenopixelsmod/client/aura/HdAuraClient.java` emit block
- Test: `HdAuraPlanTest`

**Interfaces:**
- Consumes: existing `HdAuraPlan.parseVariant`, `playsV1Outer`, `playsV1Inner`, `playsSilhouette`, `cameraSpaceInFirstPerson`, `firstPersonWorldPos`
- Produces: locked test coverage for KD1/KD2/KD19; no new public types

- [ ] **Step 1: Write the failing test for default variant and dual playback helpers**

Add to `HdAuraPlanTest` (or extend existing tests if already present — if already green, skip to Step 4 and only add missing assertions):

```java
@Test
void defaultVariantStaysV1AndV3PlaysFullStack() {
    assertEquals("v1", HdAuraPlan.parseVariant("v1"));
    assertEquals("v1", HdAuraPlan.parseVariant(null));
    assertTrue(HdAuraPlan.playsV1Outer("v3"));
    assertTrue(HdAuraPlan.playsV1Inner("v3"));
    assertTrue(HdAuraPlan.playsSilhouette("v3"));
    assertFalse(HdAuraPlan.playsV1Outer("v2"));
}
```

- [ ] **Step 2: Run test**

```powershell
./gradlew.bat test --offline -PofflineMcMeta --tests net.bullettrain.xenopixelsmod.fx.aura.HdAuraPlanTest
```

Expected: PASS if helpers already shipped; FAIL only if a helper is missing — then implement minimal helpers in `HdAuraPlan` (do not change emit behaviour).

- [ ] **Step 3: Document emit `plan(...)` shapes in a short comment on `HdAuraClient.emit`**

Exact shapes (must match source):

```java
// v2/v3 silhouette: HdAuraPlan.plan(plan, Set.of(), true)
// v3 underlay only: HdAuraPlan.plan(plan, extras, XenoClientConfig.auraLayers)
```

- [ ] **Step 4: Re-run HdAuraPlanTest — expect PASS**

- [ ] **Step 5: Commit only if owner asks**

```powershell
git add src/test/java/net/bullettrain/xenopixelsmod/fx/aura/HdAuraPlanTest.java src/main/java/net/bullettrain/xenopixelsmod/client/aura/HdAuraClient.java
git commit -m "test: lock HD aura v3 playback helpers and emit plan shapes"
```

---

### Task 2: PR-C1 — Aura3EffectsTest + generator help + pipeline docs

**Files:**
- Create: `src/test/java/net/bullettrain/xenopixelsmod/fx/aura/Aura3EffectsTest.java`
- Modify: `tools/effekseer/gen_effects.py` (argparse `--missing` help)
- Modify: `tools/effekseer/README.md` and/or `docs/effekseer-fx.md`
- Optional: write `tools/effekseer/BUILD_STAMP` text file with ISO date + set name when generating (document format; implement stamp write in gen_effects if trivial)

**Interfaces:**
- Consumes: `AuraPalette.colours()`, `AuraPalette.hex`, `AuraPalette.brightnessSuffix`
- Produces: `Aura3EffectsTest` matrix gate used by A2

- [ ] **Step 1: Write failing Aura3EffectsTest**

```java
package net.bullettrain.xenopixelsmod.fx.aura;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class Aura3EffectsTest {
    static final Path AURA3 = Path.of(System.getProperty("xenopixels.projectDir"),
            "src/main/resources/assets/xenopixelsmod/effeks/aura3");

    @Test
    void everyPaletteColourHasVariantThreeAtEveryBrightness() {
        assertTrue(Files.isDirectory(AURA3), "missing aura3 folder");
        for (int rgb : AuraPalette.colours()) {
            String hex = AuraPalette.hex(rgb);
            for (float brightness : new float[] {0.1f, 0.25f, 0.5f, 0.75f, 1.0f, 1.3f}) {
                String name = "aura3_" + hex + AuraPalette.brightnessSuffix(brightness) + ".efkefc";
                assertTrue(Files.isRegularFile(AURA3.resolve(name)), name);
                String own = "aura3_" + hex + "_nz" + AuraPalette.brightnessSuffix(brightness) + ".efkefc";
                assertTrue(Files.isRegularFile(AURA3.resolve(own)), own);
            }
        }
    }
}
```

- [ ] **Step 2: Run test**

```powershell
./gradlew.bat test --offline -PofflineMcMeta --tests net.bullettrain.xenopixelsmod.fx.aura.Aura3EffectsTest
```

Expected: PASS if 1080 files already on disk from prior regen; FAIL if folder incomplete — then run Task 3 before claiming C1 done.

- [ ] **Step 3: Update `gen_effects.py` `--missing` help to list aura3**

Change help from shared sets `(aura, aura2)` to include `aura3` (and any other `FOLDER` sets). Do not change `--missing` behaviour.

- [ ] **Step 4: Document in `tools/effekseer/README.md`**

Add a short note: look changes require full `python tools/effekseer/gen_effects.py aura3`; `--missing` only fills holes; Motif density needs generator Phase 2 + full rebuild.

- [ ] **Step 5: Re-run Aura3EffectsTest + HdAuraPlanTest — expect PASS**

- [ ] **Step 6: Commit only if owner asks** (explicit paths: test, gen_effects.py, README)

---

### Task 3: PR-A2 — Aura3 asset refresh (operational)

**Files:**
- Regenerate: `src/main/resources/assets/xenopixelsmod/effeks/aura3/**`
- Editable projects: `tools/effekseer/aura3/**`
- Verify: `Aura3EffectsTest`

**Interfaces:**
- Consumes: `aura3.py` definitions; Effekseer 1.80.6 under `tools/new_particles/Effekseer1.80.6Win`
- Produces: 1080 `.efkefc` + textures; client jar with new SHA for handoff

- [ ] **Step 1: Record dirty state before touching assets**

```powershell
git status --short | Select-Object -First 40
git status --short | Measure-Object -Line
```

- [ ] **Step 2: Full regenerate (no `--missing`)**

```powershell
python tools/effekseer/gen_effects.py aura3
```

Expected console: `aura3: 1080 effects, …`. Duration ~10 minutes. Watch with a file-count watcher; do not use `--missing`.

- [ ] **Step 3: Verify matrix**

```powershell
./gradlew.bat test --offline -PofflineMcMeta --tests net.bullettrain.xenopixelsmod.fx.aura.Aura3EffectsTest
```

Expected: PASS

- [ ] **Step 4: Rebuild client jar and record SHA**

```powershell
./gradlew.bat jar -PofflineMcMeta --offline
Get-FileHash build\libs\xenopixelsmod-0.5.0-1.21.1.jar -Algorithm SHA256
```

Write SHA + size + timestamp into the handoff / CHANGELOG Unreleased as **not verified in a running game**.

- [ ] **Step 5: Stage only aura3 paths if owner requests commit**

```powershell
git add src/main/resources/assets/xenopixelsmod/effeks/aura3 tools/effekseer/aura3
# never: git add -A
```

---

### Task 4: PR-B1 — AuraMotifTable Phase 1 (brightness / inner only)

**Files:**
- Create: `src/main/java/net/bullettrain/xenopixelsmod/fx/aura/AuraMotifTable.java`
- Create: `src/test/java/net/bullettrain/xenopixelsmod/fx/aura/AuraMotifTableTest.java`
- Modify: `src/main/java/net/bullettrain/xenopixelsmod/client/aura/HdAuraClient.java` (multiply inner/outer brightness by motif factors)
- Modify: `CHANGELOG.md` Unreleased (Phase 1 limits; not verified in game)

**Interfaces:**
- Consumes: form identity string `raceId/formGroup/formName` (MotifKey); existing brightness floats
- Produces:

```java
public final class AuraMotifTable {
  public record Motif(float outerBrightness, float innerBrightness) {}
  public static String key(String raceId, String formGroup, String formName);
  public static Motif motif(String key); // unknown -> Motif(1f, 1f)
}
```

Hardcode initial rows for SSB / Rose / UI / Ikari using values near 1.0 (e.g. Rose inner 1.05f, Ikari outer 1.08f) — **no** lick rate / bolt every-N fields.

- [ ] **Step 1: Write failing AuraMotifTableTest**

```java
@Test
void unknownKeysAreNeutralAndKnownKeysAreStable() {
    assertEquals("saiyan/xenopixels_gods_forms/ssb",
            AuraMotifTable.key("saiyan", "xenopixels_gods_forms", "ssb"));
    AuraMotifTable.Motif neu = AuraMotifTable.motif("nope/nope/nope");
    assertEquals(1.0f, neu.outerBrightness(), 1e-6f);
    assertEquals(1.0f, neu.innerBrightness(), 1e-6f);
    AuraMotifTable.Motif rose = AuraMotifTable.motif(
            AuraMotifTable.key("saiyan", "xenopixels_gods_forms", "ssrose"));
    assertTrue(rose.innerBrightness() >= 1.0f);
}
```

(Adjust form name strings to match actual JSON form ids in `xenopixels_gods_forms.json` after reading that file.)

- [ ] **Step 2: Run test — expect FAIL (class missing)**

```powershell
./gradlew.bat test --offline -PofflineMcMeta --tests net.bullettrain.xenopixelsmod.fx.aura.AuraMotifTableTest
```

- [ ] **Step 3: Implement AuraMotifTable minimal map**

- [ ] **Step 4: Wire HdAuraClient.emit**

When computing brightness for v1 underlay / silhouette spawns, multiply by `motif.outerBrightness()` / `motif.innerBrightness()` from the active form key. If form ids are unavailable, use neutral motif. **Do not** change aura3.py.

- [ ] **Step 5: Run AuraMotifTableTest + HdAuraPlanTest — expect PASS**

- [ ] **Step 6: CHANGELOG note** — Motif Phase 1 brightness only; edge/thunder still Phase 2; not verified in game.

- [ ] **Step 7: Commit only if owner asks**

---

### Task 5: Phase 1 handoff checklist

- [ ] **Step 1: Run focused suite**

```powershell
./gradlew.bat test --offline -PofflineMcMeta --tests net.bullettrain.xenopixelsmod.fx.aura.HdAuraPlanTest --tests net.bullettrain.xenopixelsmod.fx.aura.Aura3EffectsTest --tests net.bullettrain.xenopixelsmod.fx.aura.AuraMotifTableTest
```

- [ ] **Step 2: Record jar SHA, dirty path count, unverified runtime items**

- [ ] **Step 3: Copy jar to Modrinth profile only if owner asks; then in-game `/xenoaura v3` third + first person**

- [ ] **Step 4: Stop — do not start Chapter D until Phase 2 plan exists**

---

## Out of scope (Phase 2 plan later)

PR-B3 generator motifs, PR-D1–D7 tournament/roles/race/hair, NPC todolist 1–14, Fabric/multiloader, Superdesign boards (optional parallel).

## Spec coverage (self-check)

| Spec item | Task |
| --- | --- |
| KD1 v3 stack | Task 1 |
| KD2 FP overlay | Task 1 (existing tests) |
| KD4/C pipeline / Aura3EffectsTest | Task 2–3 |
| KD5/KD13 Motif Phase 1 | Task 4 |
| KD19 default v1 | Task 1 |
| Chapter D | deferred Phase 2 |

