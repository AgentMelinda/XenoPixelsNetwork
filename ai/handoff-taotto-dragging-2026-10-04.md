# Handoff — Taotto placement correction

**Date:** 2026-10-04

**Repository:** C:\XenoPixelsNetwork_qwen

**Branch:** 1.21.1

**Baseline HEAD:** 8663b51594582456e78db6b612f9c9847a8b8e02

## Current state

- Owner confirmed the complaint concerns Taotto tattoo placement. Parent-only
  authorization continues; both XenoNPCs folders remain excluded.
- Existing unrelated dirty paths preserved: plan.md,
  scripts/mirror_mynpcs_form_studio.py, tools/dmz-hair-builder-site,
  tools/gen_bt3_hud_atlas.py, tools/generated/xeno_bt3_hud_atlas.json and the
  pre-existing untracked assets/handoffs/tools. Working tree is not clean.
- An existing game process (PID 38160 observed during validation) was left alone.
  It cannot prove the latest rebuilt controls; restart with the corrected jar.
- DMZ jar and its pinned SHA-256 unchanged:
  5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581.

## Changes

- Placement was pinned because offsets were clamped against the entire 32x32
  canvas, including transparent margins. Bounds now follow opaque paint. Fitting
  paint stays inside the part; oversized paint can pan. Negative offsets support
  drawing in the center of the canvas and persist through NBT.
- New canvases fit the torso. Fit paint centers and scales opaque bounds on the
  selected part. Scaling preserves the paint center until placement bounds apply.
- Center the placement island; show a fractional-position selection outline.
  Ignore horizontal-only scrolling; scale proportionally to vertical wheel steps.
- Floor skin pixel positions so fractional placement does not round outside the
  bottom/right edge. No published API, packet IDs, channel or mixin targets changed.
- Release version bumped to 0.5.8-1.21.1. Existing commit/tag/push authorization
  from the parent release task applies to this follow-up correction.

## Verified

- Focused command, exit 0, 14 placement tests:
  `.\gradlew.bat test --tests '*TaottoDocumentTest' -PofflineMcMeta --console=plain`.
  Regression cases cover sparse paint moving on a larger transparent canvas,
  negative-offset persistence, oversized panning and reversal, fit/scale-center
  preservation, and boundary visibility at fractional scale.
- Full command, exit 0:
  `.\gradlew.bat test build jarJar serverJar buildApiExampleAddon -PofflineMcMeta --console=plain`.
  3,218 tests; zero failures/errors. Standalone API addon also built.
- Local client: xenopixelsmod-0.5.8-1.21.1.jar, 69,225,858 bytes,
  SHA-256 1920142d701d54ddb80fb4d0817da8185b2cb830fb02e7d6298000f8e0292822.
  Nested libraries: AAA Particles and Modern UI, with metadata.
- Local server: xenopixelsmod-Server-0.5.8-1.21.1.jar, 45,079,009 bytes,
  SHA-256 08bd2820591e43336fac73a32d65d4564763a9086d808cb0a78d925be199c932.
  Only entries below META-INF/jarjar are metadata.json and AAA Particles.
- `git diff --check` passed for reviewed task paths.

## Not verified

- The corrected drag's interactive feel, every GUI scale and model, and runtime
  apply/sync/reconnect are manual pending. No fresh process exercised this
  correction; prior release startup evidence is not proof of these controls.
- Repository-wide strict audit warnings from the preceding release remain.
- Existing CI packaging policy skips tests; local full tests are separate evidence.

## Next steps

1. Push reviewed commit and annotated v0.5.8-1.21.1 tag, check tag workflow, and
   download published client/server assets to verify their hashes and nesting.
2. Restart with the corrected client jar. Test a small design drawn in the middle
   of the canvas, drag it on torso/limbs, zoom and Fit paint, then Apply/reopen.
3. Preserve the excluded folders and unrelated dirty work.
