# Handoff — claim conversion policy and native NPC aura/scripts

**Date:** 2026-10-05
**Repository:** C:\XenoPixelsNetwork_qwen
**Branch:** 1.21.1
**Implementation HEAD:** ad895e5b313f561b770f6b8e1100bbe49c6abb42

## Current state

- Baseline: a0f125adcf9eea22da26251eb1d7185d2f185db0. Owner authorized the parent repository,
  commits, branch/tag pushes and release workflow. Separate XenoNPCs folders were excluded.
- Commit author/committer: jacky yuval <gitlab_admin_263562@gitlab.xpn.co.il>.
- Origin: https://github.com/AgentMelinda/XenoPixelsNetwork.git. Release version: 0.5.10-1.21.1.
- Preserved tracked dirt: plan.md, scripts/mirror_mynpcs_form_studio.py,
  tools/dmz-hair-builder-site, tools/gen_bt3_hud_atlas.py, tools/generated/xeno_bt3_hud_atlas.json.
  Preexisting untracked archives, assets, integration work and docs remain unstaged.
  The companion evidence JSON records the complete remaining status.
- Owned fixture server/client processes stopped normally. No production world was migrated.
- Exact unchanged DMZ jar SHA-256:
  5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581.
- LinearReader 1.3.0 fixture SHA-256:
  0f6041a9a6d523c04e79cf7c3ad882c5a9baf8a07e29d70b5699f8e9f925ad34.
- YAWP 0.6.3-beta3 NeoForge fixture SHA-256:
  2211f690d8d34500f4dd6a183fb0dff2749357c870baa9d98799be21d4d2a244.
- Forge Config API Port 21.1.6 fixture SHA-256:
  1fffe12e3e8343ef52d306ee29f7d6f6724d47a185a375c4a3c75aab3894c68d.

## Changes

- ad895e5b313f561b770f6b8e1100bbe49c6abb42 adds safe-default outside-local-claim conversion
  restrictions, dimension overrides and persistent commands. Old configs inherit the safe default.
- One active cuboid must cover a full 512x512 file footprint and full world build height.
  Partial overlap, unions, unsupported shapes, missing/unsupported YAWP and unknown paths deny.
  Immutable server-thread snapshots are invalidated on claim and dimension mutations.
  Conversion and native MCA opens share locks; opened MCA files remain pinned until restart.
  Existing linear files retain their reader/writer; there is no automatic reverse export.
- Native NPC v4 layers use display/profile size multiplied by explicit aura scale once, and update
  live emitters. Full DMZ sizing restores NPC size after the player power-growth cap.
  The native DMZ aura mixin now loads without either optional NPC mod.
- Native scripts gain display/stats/AI/advanced/inventory/role/job adapter views and direct
  size/aura/sound/animation calls. The Functions catalog derives 292 templates from real bindings.
  Late fetch replies preserve local drafts. This is an additive parity slice, not all reference enums/events.
- Public API package and sequential main network unchanged. No dependency binaries changed.

## Verified

- Final command, exit 0: `.\gradlew.bat test build jarJar serverJar buildApiExampleAddon -PofflineMcMeta --console=plain`.
  XML aggregate: 3236 tests, 0 failures/errors/skips. `git diff --cached --check` passed.
- Server packaging contains only `META-INF/jarjar/metadata.json` and
  `aaa_particles-neoforge-1.21.1-2.3.1.jar` below jarjar (excluding directory).
  Client embeds Modern UI 3.13.0.1. No Modern UI or Nashorn nested in the server jar.
- Local final client jar: xenopixelsmod-0.5.10-1.21.1.jar, 69,265,552 bytes,
  SHA-256 607e0cf2516f58570caa8a0507bfe8379cda73ecac366e1943e8caee44576ddf.
- Local final server jar: xenopixelsmod-Server-0.5.10-1.21.1.jar, 45,118,703 bytes,
  SHA-256 dac05eefd9d495834f1c90dbc3e5fe2350bec49306fc75e12a2232cc279b63d5.
- Local sources jar: 37,112,088 bytes,
  SHA-256 7b9807e7c0459fe17ccb738547546ad813adf3b5035ebcd695575267d0c207a1.
- Isolated server command, three sequential runs, each exit 0:
  `.\gradlew.bat runServer -I build/yawp-proof/isolated.init.gradle -x uninstallDevShaderMods -x uninstallDevPadMods --console=plain`.
  Fixtures are under build/yawp-proof/server; temporary probe is not a shipped mod.
- 2026-10-05 01:38:02 Asia/Jerusalem, `claims-convert.log`: actual region/entity/POI files for
  r.20.0 (unclaimed) and r.21.0 (partial claim) deny conversion; r.22.0 (full-height cuboid) converts
  all three. Native Nashorn script reports `ok=true result=10:2` after display size/aura mutations.
  Otherworld outside-claims command persists its false override.
- 01:38:10: disabling/resizing/removing the full claim and clearing all claims block conversion.
  01:38:26: block, bell and pig markers readable after conversion.
- 01:39:34, `claims-restart.log`: all six r.20/r.21 MCA hashes unchanged across startup and blocked
  conversion (restart-baseline.json/restart-hash-proof.json); r.22 existing linear stays linear
  while policy denies new conversion after claim removal.
- 01:40:41, `claims-marker-restart.log`: emerald/gold/diamond blocks, bell and pig marker readable
  in a fresh process with the claims removed. Normal flush and shutdown complete.
- Client command, exit 0:
  `.\gradlew.bat runApiTestClient -I build/npc-aura-proof/isolated.init.gradle -x installDevShaderMods -x installDevPadMods -x installApiExampleAddon --console=plain`.
  Fixture excludes MyNPCs/CustomNPCs and YAWP. Native optional-mod gating starts successfully.
- 01:43:18/20, `build/npc-aura-proof/client-final.log`: actual live-follow method sends 4.0 then
  0.5 on the same recording emitter; 11/9 real emitters are also present. This log precedes only the
  final editor-only source fix; aura source is identical. No native GPU size measurement is claimed.
- Final client 01:47:29/31, `client-editor-final.log`: same live-follow method again sends 4.0 then
  0.5. This run has zero real aura emitters at the sampling times, so it proves the update method
  via a recording receiver, not real GPU emission. At 01:47:31 it preserves a draft after a forced
  late server payload; 292 catalog calls are available. Screenshot at 01:47:32 visually inspected:
  code and Functions panel render, replacing the blank draft seen before the fix.
- Example addon network ping/pong observed in fresh clients. All owned fixture processes stopped.

## Not verified

- The user's production server, physical non-member break/place interactions and live ki attacks
  remain unverified; earlier protection implementation is documented separately in
  ai/handoff-dimension-protection-2026-10-04.md. No live installation or world conversion was performed.
- All native model variants, multiplayer appearance changes and pixel-exact GPU aura bounds were
  not manually verified. The Full DMZ cap rule has pure tests and startup evidence, not all-form visual proof.
- Every CustomNPCs reference event/enum or adapter capability is not implemented by this slice.
- Directory fsync reports Windows AccessDenied warnings in fixture shutdowns; existing behavior,
  not a guarantee against unrelated disk failures. Client logs also contain existing OpenGL depth-format
  warnings. Saved marker reads and normal shutdown still completed.
- Strict skill audit: `node C:/Users/Admin/.agents/skills/xenopixels-addon-development/scripts/audit-project.mjs C:/XenoPixelsNetwork_qwen --strict-evidence`.
  Audit reports 1 failure/6 repository-wide warnings (602 string/reflection constructs), preserved in
  build/claims-aura-release-audit.txt. No clean security-audit claim. New YAWP/LR targets verified
  against exact jars with javap and exercised in fresh processes; unrelated audit scope not repaired.

## Published release

- Annotated tag object: dc702d863ea4dad744022f1f46b6a32db8bb5384.
  Tag target/handoff commit: 8ff19775dbb1233d9748d573589c48fa02536354.
  Both branch and tag pushed to origin.
- Build succeeded: https://github.com/AgentMelinda/XenoPixelsNetwork/actions/runs/37241481030.
- Build And Release succeeded: https://github.com/AgentMelinda/XenoPixelsNetwork/actions/runs/37241482795.
  Published at 2026-10-04T22:51:18Z (2026-10-05 01:51:18 Asia/Jerusalem):
  https://github.com/AgentMelinda/XenoPixelsNetwork/releases/tag/v0.5.10-1.21.1.
- Downloaded published client: 69,264,796 bytes,
  SHA-256 62c4ecb0f7f1cfb67204db5f2ae389444271bfd85db4c95fd369175b1ad9448a.
- Downloaded published server: 45,117,952 bytes,
  SHA-256 ac3e65224812b96400cbd52aa2883c2023df3cb62e2b3867e47c0be5d2d9e951.
  Download hashes match GitHub's reported asset digests. Rechecked client Modern UI and server
  metadata/AAA-only jarjar contents. Downloads retained in build/release-verification-v0.5.10.
- Published task-changed class files match local artifact bytes. Overall jars differ because text
  line endings/empty directories and two unrelated class files differ. XenoNpcEntity$5 normalized
  javap instructions match; published XenoNpcRenderer has additional synthetic generic bridge methods
  for inherited MobRenderer methods. This is not a byte-identical artifact claim.

## Next steps

- Release and downloaded packaging checks complete. A documentation-only follow-up records these
  results without moving the immutable release tag. Preserve unrelated owner dirt.
- Keep Gradle invocations sequential. Concurrent dev/build runs previously replaced launch files/classes
  under running fixtures; stable sequential reruns passed. Never use the failed runs as runtime proof.
- Keep default outside-claims false. Use `/xenolinear dimension dragonminez:otherworld false` to
  block all new conversion there, or `/xenolinear dimension dragonminez:otherworld outside-claims false`
  to allow only provably fully claimed files. Existing linear files remain readable.
