# Handoff — NPC wave editing and script errors

**Date:** 2026-09-27  
**Repository:** `C:\XenoPixelsNetwork_qwen`  
**Branch:** `1.21.1`  
**HEAD:** `53a025e45a4fecc2aeb1a2d062db550f88bbf571`

## Current state

- Dirty tree before this continuation: about 745 paths; after: about 750. All prior changes remain
  in place. This continuation made no commit, tag, push, reset, or archive cleanup.
- `libs/dragonminez-2.1.3.jar` SHA-256 is
  `5A6E33EF5B992E64FCCCCAED895B105318B2CE1AB8039D8193107D40D203B581`, matching
  `dragonminez_sha256` in `gradle.properties`.
- A development client was already running before the final rebuild. It cannot prove this build's
  in-game behaviour. No fresh runtime claim is made here.

## Changes

- `client/anim/XenoClipSources.java`: Studio LOAD now lists and imports received server clips.
  `wave` and `hi_wave` become editable drafts without modifying shipped resources.
- `client/screen/XenoAnimStudioScreen.java`: PUSH saves the draft and asks the server to publish it;
  chat carries the authoritative result. Combat BIND remains available for combat slots.
- `network/AnimClipsNetwork.java`: even an empty library is sent on join or clear, removing a stale
  server library from the client's previous world.
- `npc/script/NpcScriptHost.java`, `network/packet/NpcScriptPacket.java`, and
  `client/npc/XenoNpcScriptScreen.java`: automatic NPC script failures appear for nearby permission-4
  operators in red chat with a five-second per-NPC repeat interval; manual Run failures go to the
  requester's chat and editor console.
- `docs/xeno-anim-studio.md` and `CHANGELOG.md` explain the wave workflow and changes. The smooth
  `wave` and `hi_wave` resource files and render code were not changed after user confirmation.
- No public API signature, profile schema, packet id, or animation resource was changed.

## Verified

- `./gradlew test --tests 'net.bullettrain.xenopixelsmod.anim.WaveClipContentTest' --tests
  'net.bullettrain.xenopixelsmod.anim.XenoClipLibraryTest' --tests
  'net.bullettrain.xenopixelsmod.network.NpcScriptPacketContractTest' -PofflineMcMeta`: passed.
- `./gradlew test -PofflineMcMeta`: passed.
- `./gradlew test build jarJar serverJar buildApiExampleAddon -PofflineMcMeta`: passed after the
  final source edit on 2026-09-27.
- Client jar: `build/libs/xenopixelsmod-0.5.0-1.21.1.jar`, 42,149,502 bytes,
  SHA-256 `FBFBD2B2ACFC8F19E93D709D23E94375CCD29898662C30DA5488EDACE0933C99`.
- Server jar: `build/libs/xenopixelsmod-Server-0.5.0-1.21.1.jar`, 18,002,651 bytes,
  SHA-256 `123600CAD43F7ADFFB22E5162A4C1845E8F709F3BCAD9F5599241986ED493DE0`.
- The server jar has two file entries below `META-INF/jarjar/`: `metadata.json` and
  `nashorn-core-15.4.jar`. This is consistent with the prior Nashorn scripting handoff, but conflicts
  with the older AGENTS.md instruction requiring zero entries. It was not changed in this slice.

## Not verified

- Fresh in-game LOAD/preview/PUSH of both social clips and another player's receipt.
- Fresh gameplay proof of nearby automatic script-error chat and manual Run chat.
- Running client animation, NPC migration of a custom third-party rig, and sprint/combat behaviour.
- The 73-line pasted next plan and reference screenshots were unavailable as readable attachments;
  no claims are made about those hidden requirements.

## Next steps

1. Start a fresh client built from the final jar, open `/xenoanim studio`, select SRC Server, LOAD
   `wave` and `hi_wave`, preview a key, make a reversible edit, PUSH, and confirm server chat and
   another client receive the update.
2. Exercise a failing NPC script and manual Run as a permission-4 operator; inspect a fresh log.
3. Review the server jar packaging contract with the scripting owner before any release decision.
