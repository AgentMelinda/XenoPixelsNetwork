# Handoff — Hakai ghost fade packet + translucent render, silent Zanzoken input

**Date:** 2026-09-12  
**Repository:** C:\XenoPixelsNetwork_qwen  
**Branch:** 1.21.1 (ahead of origin/1.21.1 by 8)  
**HEAD:** 3fa456a20a43c56600a743e5cab332534af6a773 (nothing committed this session)

## Current state

- Dirty tree still belongs mostly to earlier sessions. Paths touched for this pass:
  - `src/main/java/net/bullettrain/xenopixelsmod/network/packet/HakaiFadePacket.java` (new)
  - `src/main/java/net/bullettrain/xenopixelsmod/client/combat/HakaiFade.java`
  - `src/main/java/net/bullettrain/xenopixelsmod/compat/npc/NpcDissolve.java`
  - `src/main/java/net/bullettrain/xenopixelsmod/network/ModNetwork.java` (PROTOCOL 64 → 65)
  - `src/main/java/net/bullettrain/xenopixelsmod/network/SyncServerConfigPacket.java`
  - `src/main/java/net/bullettrain/xenopixelsmod/config/XenoServerConfig.java` (`applySyncedKiCombat`)
  - `src/main/java/net/bullettrain/xenopixelsmod/client/ClientConnectionState.java`
  - `src/main/java/net/bullettrain/xenopixelsmod/client/combat/CombatBodyFade.java`
  - `src/main/java/net/bullettrain/xenopixelsmod/client/combat/AlphaMultiBufferSource.java`
  - `src/main/java/net/bullettrain/xenopixelsmod/mixin/client/LivingHakaiFadeMixin.java`
  - `src/main/java/net/bullettrain/xenopixelsmod/mixin/client/DmzZanzokenPlayerFadeMixin.java`
  - `src/main/java/net/bullettrain/xenopixelsmod/client/compat/npc/NpcFullDmzRenderer.java`
  - `src/main/java/net/bullettrain/xenopixelsmod/client/combat/Bt3CombatClient.java`
  - `src/test/java/net/bullettrain/xenopixelsmod/client/combat/HakaiFadeTest.java`
  - `docs/hakai-body-fade.md`
- No game client was launched. No process holds old classes from this workspace.

## Changes

### Protocol 65

`HakaiFadePacket(entityId, amplifier)` appended at the end of `ModNetwork`. Amplifier 0 clears.
`SyncServerConfigPacket` appended `hakaiFadeEnabled`, `hakaiFadeMinAlpha`, `hakaiFadeCurve`,
`hakaiFadeRestoreTicks`. Clients and servers must both be this build.

`NpcDissolve` still writes the hidden effect and vanilla-broadcasts it, but drawing reads the
packet map first via `HakaiFade.set`. Same lesson as Sparking (protocol 61).

### Translucent render

`LivingEntityRenderer.getRenderType` and `DMZPlayerRenderer.getRenderType` return
`entityTranslucent` while `CombatBodyFade` is fading that entity. Cutout types discard vertex
alpha; clones already did this.

`AlphaMultiBufferSource` no longer joins the outline generator with a remapped body (the
`Not building!` crash). Glow bodies fade through the inner `bufferSource` only. Wrap errors
cannot cancel the real render.

### Zanzoken input

`tickZanzoken` now runs next to `tickHakai`, before the BT3-combat-off return. The key still
ships unbound. First tick in a session says so on the actionbar. A press with combat off or
`/xenobind zanzoken` off also chats instead of eating the click silently.

## Verified

- `.\gradlew.bat test --tests "*HakaiFade*" --tests "*AlphaMultiBufferSource*" --tests "*NpcDissolve*" --tests "*XenoServerConfigKeys*" --offline` → exit 0
- `.\gradlew.bat test --offline` → BUILD SUCCESSFUL; 139 suites, **837** tests, 0 failures, 0 errors
- `.\gradlew.bat build jarJar serverJar -PofflineMcMeta --offline` → BUILD SUCCESSFUL
  - `xenopixelsmod-0.3.6-1.21.1.jar` 36,263,640 bytes
    SHA-256 728CC92A30723AA2B3EEFFCF94F4885CADD116330BBAF447EEDF1BA59B4F5506
  - `xenopixelsmod-Server-0.3.6-1.21.1.jar` 12,116,413 bytes
    SHA-256 85D13CF6457C0D0A8EDC895D77FB3759D2FF1BDBFEC358C4CD7B654D86B57B87
    **0** `META-INF/jarjar/` entries
  - `xenopixelsmod-0.3.6-1.21.1-sources.jar` 10,865,949 bytes
    SHA-256 D080BC1AC368A0F35DFC00E1C91C458978F641A28426C60AB672D3C1516F3DB7

## Not verified

- In-game Hakai ghost fade on a vanilla mob (glow on/off), player, FULL/OVERLAY CustomNPC
- In-game Zanzoken after binding a key (must get a chat line: bind hint, combat-off, or `reading…` / unlock / cooldown)
- Iris/Sodium/Veil outline path after the no-join change
- Mixed-protocol joins (64 vs 65) — they must refuse, by design
- Pre-existing `compat.mynpcs.ContainerNpcInvCuriosMixin` @Shadow `addSlot` miss: not touched

## Next steps

1. Install `build/libs/xenopixelsmod-0.3.6-1.21.1.jar` (SHA-256 `728CC92A…4F5506`). Server and client must both be this jar (protocol 65).
2. Controls → XenoPixels → bind Zanzoken. Press it. You must get a chat/actionbar line.
3. Hakai a vanilla mob. The body should ramp toward a ghost, then restore if you cancel.
4. No commit/tag/push unless you ask.
