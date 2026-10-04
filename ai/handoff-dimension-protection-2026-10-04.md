# Handoff — dimensional YAWP protection and LinearReader controls

**Date:** 2026-10-04

**Repository:** `C:\XenoPixelsNetwork_qwen`

**Branch:** `1.21.1`

**Implementation HEAD:** `2a1dc605c77f47a5cb282fabbb28acedd291a4c3`

**Release tag commit:** `bada37940d931839fbca9dd66efc29aefe177561`

## Current state

- Baseline/upstream before work: `b137cfb09507ebc97e1d2c3ac7f1fea0cab5545f`.
- User authorized the parent repository and excluded both XenoNPCs folders. Neither excluded
  folder was operated on. Commit/tag/push/release authorization persists from the task.
- Git identity: `jacky yuval <gitlab_admin_263562@gitlab.xpn.co.il>`.
- Origin: `https://github.com/AgentMelinda/XenoPixelsNetwork.git`.
- Release version/tag: `0.5.9-1.21.1` / `v0.5.9-1.21.1`.
- Existing unrelated dirt remains: `plan.md`, `scripts/mirror_mynpcs_form_studio.py`,
  `tools/dmz-hair-builder-site` submodule, `tools/gen_bt3_hud_atlas.py`,
  `tools/generated/xeno_bt3_hud_atlas.json`, and hundreds of pre-existing untracked assets,
  archives, documents, fixtures, and integrations. Full local inventory is retained in
  `build/dimension-protection-status.txt`. Stage only the task's reviewed paths.
- Pre-existing untracked `docs/reflection-evidence.md` was left out of the commit; this handoff
  records the newly verified optional-mod evidence independently.
- Owned proof servers stopped gracefully. Their worlds/configs/logs are isolated under `build/`.
  Owner game processes were not stopped. Existing owner processes may still hold older classes;
  no claim of a clean working tree or updated owner runtime is made.

## Changes

Implementation commit `2a1dc605c77f47a5cb282fabbb28acedd291a4c3`:

- Native YAWP responsible-region lookup replaces the local-only lookup, fixing dimensional and
  global fallback for explicit ki flags. Console `/execute in` uses the command source context.
- Ki region parser accepts namespaced and quoted names; its serializer is registered for command
  tree sync. `/kiflag enabled` persists the integration toggle; `/kiflag check` evaluates DMZ's
  actual block-destruction permission gate without firing a projectile.
- LinearReader per-dimension overrides, default policy, master switch, status/reload/toggle/reset
  commands and atomic JSON persistence. Defaults retain existing behavior until configured.
- Disabled new regions use vanilla Anvil read/write/scan/flush/close. Existing and cached unsaved
  linear regions keep LinearReader IO. Opened Anvil regions retain their format until restart.
  Converter and bulk candidate hooks enforce the same policy. No automatic export/delete/migration.
- Storage mixins have their own required config with optional-mod/version gates. Installed
  unsupported versions cannot enable restrictions. Exact-version injection failures abort startup.
- No published API or main/addon packet identifiers changed. A command argument registry entry
  `xenopixelsmod:region_name` is added. Client command-tree/login behavior is not runtime verified.
- Version, manifest/NeoForge mixin registration, changelog, operator docs and focused tests updated.

## Verified

### Exact dependencies and symbols

| Artifact | SHA-256 |
|---|---|
| Tracked `libs/dragonminez-2.1.3.jar` (unchanged) | `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581` |
| Official `yawp-1.21.1-neoforge-0.6.3-beta3.jar` | `2211f690d8d34500f4dd6a183fb0dff2749357c870baa9d98799be21d4d2a244` |
| `ForgeConfigAPIPort-v21.1.6-1.21.1-NeoForge.jar` | `1fffe12e3e8343ef52d306ee29f7d6f6724d47a185a375c4a3c75aab3894c68d` |
| Official `linearreader-1.3.0-neoforge-1.21.1-1.21.4.jar`, Modrinth `1g14WmZU` | `0f6041a9a6d523c04e79cf7c3ad882c5a9baf8a07e29d70b5699f8e9f925ad34` |

`javap -p -c -classpath <exact-jar>` inspected:

- YAWP `FlagEvaluator.findResponsibleRegion(BlockPos,ResourceKey)` and `getName()` on the
  responsible region. Resolver descriptor also verified in official NeoForge 0.6.2-beta1.
- LinearReader `MCAConverter.convertRegionIfNeeded(Path,int,int)`, private `convertOne(Path)`,
  `BulkMcaConverter.lambda$doConvert$2(Path):boolean`, and its shared converter invocation.
- LinearReader's mixin overwrites vanilla RegionFileStorage read/write/scan/getRegionFile/flush/
  close and adds `linearCache:Long2ObjectLinkedOpenHashMap`. Reflection resolves that field after
  application and throws on mismatch. Close clears regionCache without closing Anvil handles,
  so the integration explicitly closes native handles before LinearReader's close.
- Existing flush integration symbols `LinearRegionFile.ALL_OPEN`, `isDirty()` and
  `LinearRuntime.flushRegionsBlocking(List)` exist in this exact jar. The blocking flush command
  itself was not exercised by this task.
- NeoForge/Minecraft symbols came from `build/moddev/artifacts/neoforge-21.1.248-sources.jar`:
  vanilla RegionFileStorage IO and DimensionType storage layout; BlockEvent/FakePlayer/BlockSnapshot
  constructors used only by the isolated runtime probe.

Reference jars/bytecode are retained under `build/yawp-reference` and `build/linearreader-reference`.
The compile-only Modrinth Maven YAWP jar has Forge metadata and was skipped by NeoForge at runtime.
The proof used the official NeoForge variant plus required Forge Config API Port; this does not
establish which jar is installed on the user's affected server.

### Commands and builds

- Focused `test --tests '*LinearConversionPolicyTest' --tests '*YawpRegionLookupTest'
  --tests '*PlotYaWPTest' -PofflineMcMeta --console=plain`: exit 0.
- Final `.\gradlew.bat test build jarJar serverJar buildApiExampleAddon -PofflineMcMeta
  --console=plain`: exit 0; **3,227 tests**, no failures/errors/skips; standalone addon build passed.
- `git diff --cached --check`: passed before implementation commit.
- Skill `audit-project.mjs --strict-evidence`: exits 1 with the repository-wide six warnings and
  596 string constructs; scoped target evidence above does not clear unrelated audit warnings.
- Both jars contain the new storage config/classes. Client nests Modern UI. Server jar's only
  files below `META-INF/jarjar/` are `metadata.json` and AAA Particles; no Modern UI/Nashorn.

| Local artifact | Bytes | SHA-256 |
|---|---:|---|
| `xenopixelsmod-0.5.9-1.21.1.jar` | 69,249,032 | `28b17b91317c2cf88f6c41f7aa70964c9451fd447708c3a70d64f980d81390e5` |
| `xenopixelsmod-Server-0.5.9-1.21.1.jar` | 45,102,183 | `02ec3837d65d0ee3d0555c60503ea622791bffb3131ae58250f413e1dc3c83b6` |
| `xenopixelsmod-0.5.9-1.21.1-sources.jar` | 37,104,237 | `2812e10a809fdc5180e69ef7d683c49bbfc167b39ab9f74f9a589957d1cd4fa4` |

Local hashes describe the verified local build, not a claim of byte equality with CI artifacts.

### Publication completed

- Branch and annotated tag pushed under the configured identity. Tag object:
  `1c84685a90c8ea26d1a2c418745a8e0944398247`; peeled commit is the release tag commit above.
- [Build run 37230334935](https://github.com/AgentMelinda/XenoPixelsNetwork/actions/runs/37230334935): success.
- [Build And Release run 37230336990](https://github.com/AgentMelinda/XenoPixelsNetwork/actions/runs/37230336990): success.
- [Published v0.5.9-1.21.1](https://github.com/AgentMelinda/XenoPixelsNetwork/releases/tag/v0.5.9-1.21.1):
  public, non-draft release with both client/server artifacts.
- Downloaded published artifacts to `build/release-verification-v0.5.9` and computed their hashes:

| Published artifact | Bytes | SHA-256 |
|---|---:|---|
| `xenopixelsmod-0.5.9-1.21.1.jar` | 69,248,274 | `0f244abe0254a8609b75214cc614e819e9ce9cdae478e40ec879f199123401c1` |
| `xenopixelsmod-Server-0.5.9-1.21.1.jar` | 45,101,430 | `259e0a746e96594116e57bdb41d94d5ed6469b6776f8b8f25153f02e23e26490` |

Published server content verified: new conversion config and command argument registration exist;
only AAA Particles and metadata are nested. Client/server compilation and release artifact checks
passed in CI. No matching owner client/server was restarted or updated by this task.

### Fresh runtime

Command: `.\gradlew.bat runServer -I build/yawp-proof/isolated.init.gradle
-x uninstallDevShaderMods -x uninstallDevPadMods --console=plain` (forwarded console stdin).
Logs: `build/yawp-proof/final-runtime-proof.log`, final startup `2026-10-04 22:47:50`.

- `22:48:26`: bulk conversion found no allowed legacy candidates despite disabled-dimension MCA
  files remaining. Protected fresh region `r.10.0.mca` remained in chunk/entity/POI directories.
- `22:49:39`: Otherworld diamond/bell blocks and pig reloaded after restart; existing Overworld
  emerald block reloaded from linear data after conversion was disabled.
- `22:49:40`: fresh FakePlayer NeoForge break/place events canceled in Otherworld; DMZ player/mob
  ki checks denied. Same probe in Overworld: events not canceled, ki checks allowed.
- Disabling ki integration or setting explicit states allowed restored ki permission while ordinary
  Otherworld block rules continued denying. Native DIM tracking/state and flag commands exercised.
- Linear commands saved Otherworld/Overworld overrides, toggled/reset End, and reloaded them.
- `22:49:58`: LinearReader shutdown reported all region flushes completed. All owned servers stopped.
- Absent-mod fresh startup used `-I build/dimension-absent-proof/isolated.init.gradle`. Its log
  `build/dimension-absent-proof/final-runtime-proof.log` records Done at `22:55:09`, optional-mod
  absence and successful policy commands at `22:56:10`, and graceful stop at `22:56:12`.

The small runtime probe lives only in the isolated build fixture and is not shipped. An early
fixture attempt failed a merged-field shadow check; it was corrected to post-application
reflection and a required storage config. Earlier logs are retained as failures, not proof.

## Not verified

- Actual affected server/instance path, YAWP version/region configuration, owner/member/bypass
  identity, and physical player block break/place/ki projectiles. An async clarification is pending.
- Client login/command tree sync, actual multiplayer projectile ownership, or older YAWP runtime.
- Unsupported LinearReader versions, conversion already executing during a toggle, unusual
  third-party storage layouts, and full export/removal scenarios. No user world was migrated.
- Runtime API packet test was not repeated: published API/network behavior is unchanged.
- Both excluded XenoNPCs folders and Minecraft 1.20.1 releases are outside current authorization.

## Next steps

1. Keep `v0.5.9-1.21.1` fixed on its released commit. Publication and artifact verification are
   complete; this follow-up handoff update belongs on the branch without moving the tag.
2. Install matching client/server release artifacts and use `docs/dimension-protection.md`.
3. Obtain the affected server details and inspect its native YAWP tracking/active/flag/bypass state
   before claiming that live ordinary block-breaking/placing report is resolved.
4. Preserve existing linear data and installed LinearReader. Configure exclusions before first
   startup when conversion must never occur; restart to release retained Anvil format selections.
