# Codex handoff — developer API completion and AI repository guidance

**Date:** 2026-09-10  
**Timezone:** Asia/Jerusalem  
**Repository:** `C:\XenoPixelsNetwork_qwen`  
**Branch:** `1.21.1`  
**Recorded base HEAD:** `50fa20502441d3918d7a4087e29201794d8e3498`

This document follows `CODEX_HANDOFF_API_2026-09-10.md`. The earlier file is preserved as dated
historical input; its old “not done” and dirty-tree sections describe the state before the commits
below.

## Commits completed

| Commit | Change |
|---|---|
| `79e967e8496b92289a26aa7080537fbb5d7e6d2d` | Kept the PR #286 DragonMineZ jar and synchronized its enforced SHA-256. |
| `77f14c371cac422665e19701d98ee6205b173610` | Added the XenoPixels developer API, event hooks, DMZ helpers, rush registry, and tests. |
| `e984f1fcccd3e90d5f5f9c69cadbcdef17d20857` | Added a standalone example addon compiled against the packaged XenoPixels jar. |
| `b5fbaa878bbf3f5d2a12f4e5b1aff6310515eab0` | Added validated additive-only addon DMZ patches, backups, atomic writes, docs, and tests. |
| `6a2b833f8ddca2ce7428a1a00295dbbd7d7ace2c` | Added the collision-safe separate addon packet channel and example ping/pong. |
| `3adf47b3a51b8f5766fec1395b0ea59d5c6f2a58` | Added the automatic `runApiTestClient` quick-play verification profile. |
| `50fa20502441d3918d7a4087e29201794d8e3498` | Added canonical repository and per-agent/per-skill AI guidance. |

No `git add -A`, history rewrite, tag, or explicit push command was used. Immediately before this
handoff commit, `git rev-list --left-right --count HEAD...@{u}` reported `0 0`.

## Public interfaces added

- API generation 1 events: Sparking, rush, clone, Zanzoken, and strike interception.
- `RushRegistry` and public `Bt3RushDefinition` registration.
- `DmzAccess`, `DmzForms`, and `DmzSync`.
- Additive resource path `data/<namespace>/xenopixels/dmz_patch.json`.
- `AddonNetwork`, namespaced packet ids, explicit directions, Xeno-owned handler context, and
  client/server/tracking send helpers.
- Standalone `examples/xenopixels-api-addon` with event counters, sync commands, data patch, and
  login ping/pong.

The addon channel is `xenopixelsmod:addons`. Its protocol is generation 1 plus a SHA-256
fingerprint of sorted packet ids, directions, and message class names. The observed example
protocol was:

`1-2bc2aa87e5c7942d88cb2588ac147f1c54b32c9ddecc24019d1c5273b6319114`

## Verified on 2026-09-10

### Automated tests and packaging

The following command succeeded in 37 seconds:

```
./gradlew test build jarJar serverJar buildApiExampleAddon -PofflineMcMeta
```

- DragonMineZ expected and actual SHA-256 both equal
  `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`.
- Client jar: `xenopixelsmod-0.3.5-1.21.1.jar`, 32,315,839 bytes.
- Server jar: `xenopixelsmod-Server-0.3.5-1.21.1.jar`, 8,168,611 bytes.
- Example addon: `xenopixels-api-addon-1.0.0.jar`, 11,796 bytes.
- Server jar contains zero entries under `META-INF/jarjar/`.
- The example jar contains its independent mod classes and
  `data/xenopixels_api_example/xenopixels/dmz_patch.json`.

### Runtime startup, patches, and packets

- A fresh normal `runClient` loaded `XenoPixels API Example 1.0.0` and registered two addon
  packets with the protocol above.
- A fresh `runServer` reached `Done (9.231s)!` at 12:47:42.
- That server created
  `run/config/dragonminez/.xenopixels-backups/20260910-124742`, applied one additive addon patch,
  retained `api_example_forms` in `skills.json`, and completed DragonMineZ config reload.
- A fresh `runApiTestClient` quick-joined `New World (7)`. Player `Dev` joined at 12:52:12.
- At 12:52:15 the server logged `network.ping count=1`; the render thread logged
  `network.pong count=1` with the same nonce `3103870556300`.

The first quick-play attempt failed because the new profile lacked the original client's Sodium
additional runtime classpath. The profile was corrected to mirror the pinned Sodium dependency;
the subsequent run reached the world and completed ping/pong. Do not report the failed attempt as a
product regression.

## Not verified — do not claim otherwise

- The real gameplay actions for Sparking, rush start/impact/interrupt, clone split/reunite,
  Zanzoken dodge, strike interception, and DragonMineZ transform/untransform were not manually
  performed. The addon listeners loaded, but those event counters remain manual checks.
- The three `/xenoapitest sync_*` commands were not manually executed.
- A mismatched client/server addon packet registry was covered by deterministic registry tests, not
  by connecting two deliberately different installations.
- The produced slim server mod jar was inspected, but it was not installed into a separate packaged
  NeoForge server distribution. `runServer` verifies the dedicated development source-set run.
- No public release, tag, or remote GitHub status was changed or checked by this session.

## Known unrelated runtime noise

Current dev runs still emit pre-existing shader/Veil warnings, malformed empty resource identifier
diagnostics, optional-class mixin warnings, and client dependency warnings. The successful server
startup and quick-play packet proof occurred despite that noise. Diagnose those separately; do not
silently attribute them to the developer API work.

## Safe next checks

1. Run `./gradlew runApiTestClient` and perform each gameplay action listed in
   `examples/xenopixels-api-addon/README.md`.
2. Run `/xenoapitest` and record every event counter plus the three sync commands.
3. Test a deliberately mismatched addon packet set on separate client/server installations.
4. Install the generated slim jar into a clean NeoForge server with its required dependencies and
   record a real packaged-server startup.
