# Change KI Technique Charge Meter Overcharge from 200% to 1000%

## Goal
Increase the maximum overcharge range of the KI technique charge meter from `200%` to `1000%` so the UI correctly scales when DMZ reports `techniqueChargePercent` values above 200.

## Affected Boundaries
- **File:** `src/main/java/net/bullettrain/xenopixelsmod/client/XenoTechniqueHotbarOverlay.java`
- **Method:** `drawChargeMeter(GuiGraphics, Font, int, int, float, String)`
- No other files reference the 0..200 normalization for this meter.

## Data Flow
1. DMZ provides `techniques.getTechniqueChargePercent()` (currently documented as 0..200, expected to become 0..1000).
2. `XenoTechniqueHotbarOverlay` normalizes and clamps `rawPercent`.
3. Main fill maps `0..100%`; overcharge layer maps `100..newMax%`.

## Change Summary
| Before | After |
|---|---|
| Clamp max = `200f` | Clamp max = `1000f` |
| Overcharge fill = `(pct - 100f) / 100f` | Overcharge fill = `(pct - 100f) / 900f` |
| Tick mark / comments reference 200 | Update to 1000 |

## Tasks
1. Update `drawChargeMeter` hardcoded cap from `200f` to `1000f`.
2. Update overcharge normalization divisor from `100f` to `900f`.
3. Update inline comments and Javadoc that mention `0..200` or `100→200` to `0..1000` and `100→1000`.
4. Leave tick marks at 50% / 100% (relative to total bar width) — they remain meaningful for the normal-fill portion. The right-edge marker stays as the hard cap.

## Failure Modes / Edge Cases
- **DMZ still caps at 200:** The meter will never visually exceed 200% because `rawPercent` won't exceed 200. This is expected; the overlay change only matters if DMZ supplies larger values.
- **DMZ sends 0..1:** Existing safeguard (`if (pct > 0f && pct <= 1.0001f) pct *= 100f;`) remains unchanged and still works.
- **Extreme values (≥1000):** Still clamped to 1000, matching the new hard cap.

## Validation
1. Run `./gradlew runClient`.
2. Charge a DMZ KI technique beyond 100% and verify the main bar fills to 100%, then the overcharge strip extends toward the right edge as percentage grows (e.g., at 550% overcharge strip should be ~50% of bar width).
3. Confirm label updates correctly (e.g., `OVERCHARGE 550%`).

## Rollout / Migration
- No save migration required.
- Existing config files unaffected.
- Change is purely client-side rendering.
