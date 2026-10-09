# Hair codec / export vectors (PR-D7a)

**Date:** 2026-10-03  
**Scope:** Golden encode vectors and export format for the DMZ hair lab codec. **Export /
download / file write only.** In-game apply is **blocked** until PR-D7c evidence cites a real
DMZ/Xeno write path (not `CustomizationManager` `hair_style_*` slots).  
**Pinned stack:** Minecraft 1.21.1, NeoForge 21.1.248, DragonMineZ 2.1.3.

Cross-links: design § hair maker / KD9 / PR-D7a in
`docs/superpowers/specs/2026-10-03-hd-aura-tournament-makers-design.md`; plan Task 9 in
`docs/superpowers/plans/2026-10-03-tournament-roles-makers-phase2.md`.

## Verdict

| Item | Status |
| --- | --- |
| Lab codec source of truth | `tools/dmz-hair-builder-site/lib/dmz-codec.ts` |
| Java codec port | **Not shipped** this PR (thin fixture parse only) |
| Golden vectors (empty + one strand) | Captured 2026-10-03; Node `npm test` asserts re-encode |
| Export path | Lab download + `scripts/export-hair-project.mjs` file write |
| In-game apply | **Blocked** (PR-D7c) |
| New `connected` / `movable` boolean | **Forbidden** — parenting stays geometry-only |

## HairStrand fields (lab model)

Source: `tools/dmz-hair-builder-site/lib/hair-model.ts`.

| Field | Role |
| --- | --- |
| `id` | Slot id (`faceIndex * 100 + index`) |
| `visible` | Derived from `length > 0` for editing UI |
| `length` | Cube count along strand (0 = omitted from DMZ payload) |
| `lengthScale` | Per-cube length multiplier |
| `rotationX/Y/Z` | Strand rotation degrees |
| `scaleX/Y/Z` | Strand scale |
| `cubeWidth/Height/Depth` | Cube size (defaults 2) |
| `curveX/Y/Z` | Shared bend across cubes |
| `color` | Optional per-strand override (`null` = use `globalColor`) |

**Not present:** `connected`, `movable`. “Connected” in makers UI means parented cubes from
`hair-geometry.ts` `createStrandGroup` — label only, not a schema flag.

## Codec wire format

| Prefix | Meaning | Alphabet |
| --- | --- | --- |
| `DMZ1:` | Single `CustomHair` (version 5 NBT compound, deflate level 9) | Base62 |
| `DMZF1:` | Full set Base / SSJ / SSJ2 / SSJ3 compounds (`B`/`S`/`S2`/`T`) | Base62 |

Decode also accepts legacy `DMZ4:` / `DMZ5:` / `DMZF4:` / `DMZF5:` (Base64URL). Encode path used
for goldens is `DMZ1` / `DMZF1` only.

Strand NBT short keys: `i`, `l`, `ls`, `rx`/`ry`/`rz`, `sx`/`sy`/`sz`, `cw`/`ch`/`cd`,
`cx`/`cy`/`cz`, `c`. Defaults omitted. Faces: `F`/`B`/`L`/`R`/`T`. Hair: `v`=5, `n`, `gc`.

## Golden vectors

Captured by `npx tsx scripts/gen-golden-vectors.mjs` from the lab codec.

| Id | Description | Code prefix |
| --- | --- | --- |
| `empty_single` | `CustomHair` name `Empty Base`, `#171717`, all length 0 | `DMZ1:` |
| `one_strand_single` | TOP[0] id 400, length 4, lengthScale 1.25, rot (10,-5,15), scale (1.1,1,0.9), cube (2.5,2,1.5), curve (2,-1,3), color `#ffaa00` | `DMZ1:` |
| `empty_full` | Empty `HairSet` | `DMZF1:` |
| `one_strand_full` | One-strand Base cloned across styles with style colours | `DMZF1:` |

Exact strings: `tools/dmz-hair-builder-site/fixtures/hair/golden-vectors.json` and
`src/test/resources/hair/golden-vectors.json` (same vectors).

Float note: IEEE float32 round-trip may show ±1 ULP on decode (e.g. scaleX `1.100000023841858`);
Node tests use ±1e-5 tolerance for float fields while asserting exact Base62 codes.

## Export envelope (`xenopixels.hair.export.v1`)

Produced by `lib/hair-export.ts` / `scripts/export-hair-project.mjs`:

```json
{
  "schema": "xenopixels.hair.export.v1",
  "capturedAt": "2026-10-03",
  "apply": "blocked",
  "applyNote": "PR-D7c blocked: do not write CustomizationManager hair_style_* slots as DMZ codec apply.",
  "project": { "version": 2, "hairSet": { "...": "HairProjectV2" } },
  "codes": {
    "single": "DMZ1:…",
    "singleStyle": "Base",
    "full": "DMZF1:…"
  }
}
```

Browser lab already downloads editable `HairProjectV2` JSON via
`downloadText('dmz-hair-project.json', …)` in `app/page.tsx`. CLI writes the envelope plus
`.dmz.txt` sidecars — still no apply.

## How to run tests

```powershell
# Node golden encode/decode (authoritative for codec bytes)
cd tools/dmz-hair-builder-site
npm test

# Optional: regenerate goldens after intentional codec changes
npm run gen:golden

# Optional: file-write export from a fixture project
npm run export:hair -- --fixture one-strand

# Thin Java: fixtures exist + JSON parse + apply=blocked + no connected
./gradlew.bat test --offline -PofflineMcMeta --tests net.bullettrain.xenopixelsmod.hair.HairCodecFixtureTest
```

## Explicit non-claims

- No Java NBT/Base62 port of `dmz-codec.ts` in this appendix (codes stay lab-encoded).
- No call into `CustomizationManager` `hair_style_*` as the codec apply path.
- Hair Editor UI + Apply path landed under Task 12 / PR-D7c (`HairMakerScreen`,
  `HairApplyService` → `UpdateCustomHairC2S#handle`) with status
  `path_ready_runtime_unverified` — see `2026-10-03-hair-apply-path.md`. Live Apply
  success in a running game is **still not verified** here.
- Runtime paste of golden codes into a live DMZ client **not verified** here — vectors
  are lab encode stable only.
