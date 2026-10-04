# Handoff — Grok makers and 0.5.7 release

**Date:** 2026-10-04
**Repository:** C:\XenoPixelsNetwork_qwen
**Branch:** 1.21.1
**Implementation HEAD:** e7969c4e2492de10b1d7952098e9cf351214ac44

## Current state

- Resumed Grok session `01a0fec8-3563-7301-9f26-05b388a7b99b`. The owner explicitly
  authorized the parent repository and excluded both XenoNPCs folders.
- Baseline: `6f4a440844ec8746bb49de7dfb466eb1d1cb53ce`; initially matched origin/1.21.1.
- Commit author: `jacky yuval <gitlab_admin_263562@gitlab.xpn.co.il>`.
- Origin: `https://github.com/AgentMelinda/XenoPixelsNetwork.git`.
- Preserved unrelated tracked changes: `plan.md`, `scripts/mirror_mynpcs_form_studio.py`,
  `tools/dmz-hair-builder-site`, `tools/gen_bt3_hud_atlas.py`,
  `tools/generated/xeno_bt3_hud_atlas.json`. Many pre-existing untracked handoffs,
  assets, archives, generator tools, integration projects and documents remain.
  This is deliberately not a clean working tree.
- No smoke-test Java processes remain. The test server/client were stopped by
  their verified process IDs after fresh logs were inspected.
- Pinned DMZ SHA-256 unchanged:
  `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`.

## Changes

- `e7969c4e2492de10b1d7952098e9cf351214ac44`: finish Grok's race body/hair catalogs,
  Taotto painter, form multiplier controls, and restore live maker previews.
  Fix 1.21.1 resource-pack APIs and resource cache reloads; add three exact-target
  client mixins and tests for state restoration, catalog paths, placement and NBT.
- `41cfea6322f0e32db93ffdff085848bdd6aff709`: release preparation sets `mod_version=0.5.7-1.21.1` and checks exact matching
  client/server artifacts before publishing. Server nesting is restricted to
  metadata plus AAA Particles. The release tag must match the version property.
- Public API and sequential main ModNetwork packet IDs are unchanged. Taotto
  uses its own `xenopixelsmod:taotto` channel, protocol 1, sender-owned apply requests,
  and additive player capability NBT. Existing DMZ tattooType is not overwritten.
- See `docs/race-maker-taotto.md` for the user workflow and current limitations.

## Verified

- Focused tests, exit 0:
  `.\gradlew.bat test --tests '*maker.*' --tests '*RaceAppearanceCatalogTest' --tests '*RaceCatalogPathsTest' --tests '*HairMakerDocumentCatalogTest' --tests '*TaottoDocumentTest' -PofflineMcMeta --console=plain`.
- Final full validation, exit 0:
  `.\gradlew.bat test build jarJar serverJar buildApiExampleAddon -PofflineMcMeta --console=plain`.
  JUnit XML totals: 3,213 tests, zero failures, zero errors. Standalone API addon
  also built successfully.
- `build/release-tools/actionlint/actionlint.exe .github/workflows/gradle-publish.yml`,
  exit 0; actionlint 1.7.12 downloaded from rhysd/actionlint's official release.
- Local client artifact: `xenopixelsmod-0.5.7-1.21.1.jar`, 69,223,484 bytes,
  SHA-256 `62697a9c56d5469a6e8d35719f9b4329a5069415958a88b58a62e6aef51129a3`.
  Nested: AAA Particles, Modern UI 3.13.0.1, metadata.
- Local server artifact: `xenopixelsmod-Server-0.5.7-1.21.1.jar`, 45,076,635 bytes,
  SHA-256 `46e0180b3e7afbe2f4c3a2a2433e8343a60e8869a5a570b511bf987fc3e7fb30`.
  Its only file entries below META-INF/jarjar are metadata.json and
  aaa_particles-neoforge-1.21.1-2.3.1.jar. No Modern UI or Nashorn nested jar.
- Fresh runtime commands:
  `.\gradlew.bat runServer -I build/maker-release-smoke/isolated.init.gradle -x uninstallDevShaderMods -x uninstallDevPadMods -PofflineMcMeta --console=plain`
  and `.\gradlew.bat runClient -I build/maker-release-smoke/isolated.init.gradle -x installDevShaderMods -x installDevPadMods -PofflineMcMeta --console=plain`.
  The local-only init script relocated game directories into build. Because its
  relative paths resolve against the init directory, actual logs are under
  `build/maker-release-smoke/build/maker-release-smoke/{client,server}/logs/latest.log`.
  No existing run-world was used. Both processes were intentionally terminated
  after observation; their task exit status is not a clean shutdown claim.
- Fresh server log: 2026-10-04 16:26:19.800, `Done (18.140s)!`; XenoPixels server
  configuration and scripts initialized. Fresh client log: 16:26:40.877 resource
  reload included `xenopixels_race_catalogs`; 16:26:48.462 sound engine started;
  rendering/resource initialization completed. No new mixin injection failure
  observed. This proves startup and pack registration, not maker interaction.
- Exact new mixin target descriptors/field validated against pinned DMZ jar by
  javap and `MakerMixinTargetTest`.

## Not verified

- Interactive live-preview changes, DMZ creation hair selection, generated-body
  appearance, Taotto paint/drag/apply/reopen, multiplayer sync and reconnect,
  and all custom race model UV layouts. Front-face placement and one overlay
  per player are the current scope.
- `runApiTestClient` was not run against the owner's existing quick-play world;
  API boundary build passed, but packet delivery remains unverified.
- Skill strict audit exits 1 on existing repository-wide reflection/string-based
  integration warnings (582 reflective symbols). Version/hash/channel/API checks
  passed; the broad audit is not claimed clean.
- Smoke logs retain unrelated upstream warnings, including dedicated-server
  ClientLevel dist checks and a Sable tag referring to absent create:flywheel;
  they did not stop startup and were not changed here.
- Existing release workflow skips test execution because clean-checkout tests
  reference untracked generator inputs. This task preserves that existing policy;
  local full tests passed. CI publication evidence must be checked separately.

## Next steps

1. Commit reviewed release metadata, push 1.21.1, create and push annotated
   `v0.5.7-1.21.1`. Its push triggers Build And Release; inspect its conclusion
   and verify the two published jars. CI jar hashes can differ from local Windows
   output; record CI asset hashes separately.
2. Exercise the unverified interactive and multiplayer cases in a disposable
   world before claiming those runtime behaviors verified.
3. Preserve the unrelated dirty paths and both excluded XenoNPCs directories.
