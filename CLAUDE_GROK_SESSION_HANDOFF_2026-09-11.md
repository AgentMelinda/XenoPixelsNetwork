# Handoff — Grok session (HUD, combat fade, party, train, animator)

**Date:** 2026-09-11  
**Repo:** `C:\XenoPixelsNetwork_qwen` (not `C:\xenopixelsmod`)  
**Branch:** `1.21.1`  
**HEAD:** `3fa456a` — *Record developer API completion handoff*  
**Nothing from this session was committed, tagged, or pushed.** Working tree is dirty and **ahead 8** of `origin/1.21.1`. The tree also contains **older uncommitted work** (Form Studio, `/stack`, script API, flight-plan packets, etc.) that this session did **not** author. Do not treat every dirty path as today’s work.

**Constraints still in force:** `AGENTS.md` — no invented Minecraft/NeoForge/DMZ APIs; do not edit `api/**`; `ModNetwork` protocol string is `"64"` (do not bump); form-editor channel is `xenopixelsmod:form_editor` protocol `3`; do not `git add -A`; do not delete untracked assets (e.g. MP3); do not redeclare Curios `slots/*.json`.

**User instruction still in force:** do **not** change NPC HP / VIT calculator code. The user checked HP on `runClient` and said it was fixed.

---

## Verified in-game (user)

- NPC VIT → HP is no longer 1:1. User reported HP is correct after the DMZ calculator path (`NpcVitalityMath` / `NpcVitalitySync` / locked Stats-tab Health). **Do not retouch this.**

## Unit tests this session actually ran

Multiple `./gradlew test --offline` (and some focused `--tests`) completed **BUILD SUCCESSFUL** after compile fixes. Last focused run in this session: `XenoAnimClipTest` + `NpcProfilePersistenceTest` (success). A full `test` + `build jarJar serverJar -PofflineMcMeta` was run earlier in the session after HUD/hide/zanzoken/hakai work (success; server jar reported 0 `META-INF/jarjar/` entries at that time). **Not re-run after the last persist + xenobt3 play-clip edits.**

## Explicitly not verified in-game

Almost everything below except the HP check. Includes Zanzoken host fade, Hakai ghost, `/xenoparts global`, quest-share, dummy/shadow, animator play, and NPC stats-after-relog (code was changed; user has not confirmed a relog yet).

---

## What this session added or changed (honest list)

### 1. Master menu parts editor extras

- Header split into bar / icon / label (`masterHeader*`, `formsHeader*`) in `XenoMasterMenuConfig`.
- Close button split: `closeBox`, `closeLabel`, `closeHint`.
- Hide/Show on `/xenohud` parts editor (`PartLayout.hidden()`, per-surface `partHidden`).
- `/xenoparts` opens the full-tab editor (Panel + Master + others). `/xenohud parts edit` was pointed at the same full editor.

### 2. Server-global HUD parts (join sync)

- **New channel** `xenopixelsmod:hud_parts` protocol `"1"` — `HudPartsNetwork`. **Not** a ModNetwork packet.
- `/xenoparts global push` (OP, `xenopixelsmod.xenoparts.global`) uploads **this client’s** live layouts; stored as `config/xenopixelsmod-hud-parts-global.json`.
- `/xenoparts global clear` stops sending on new joins.
- On `PlayerLoggedInEvent`, server sends the blob; client `HudPartsClient.apply` writes local HUD JSON.

### 3. NPC inventory Curios

- Inventory overlay now uses all 10 mapped types from `data/xenopixelsmod/curios/entities/npcs.json`: head, necklace, back, body, bracelet, **curio, hands, ring, belt, charm** — equip + cosmetic = 20 wells, 4×5 grid above player-inv y=113.
- **Did not** add Curios slot JSON.

### 4. DMZ appearance Mode snapping to OFF

- `GuiNpcDmz.editorProfile()` was overwriting `appearance.mode` from a stale `NpcAppearanceClient` snapshot (same class of bug as form-group snap). Local FULL is preserved if the snapshot is still OFF.

### 5. Zanzoken

- Config `XenoServerConfig.zanzokenRequireTiming` (default **true**). When **false**, `/` press builds the ring immediately (lock-on or self). **Not** added to `SyncServerConfigPacket`.
- Ring copies stay put, mimic pose/swing, copy look; copies behind a look-into-the-ring face the center (`ZanzokenLook`).
- Host fade: `LivingHakaiFadeMixin` now also wraps **players** via `CombatBodyFade`. `ZanzokenFade` starts at ghost alpha (mode 1) and lerps back instead of snapping to 1 when the ring dies.
- **`DmzZanzokenPlayerFadeMixin` `@WrapOperation` on `GeoEntityRenderer` was removed.** MixinExtras 0.5.3 treated a bad first-arg type as **FATAL** during `EntityRenderDispatcher` reload. Symptom: Mojang splash bar full, DMZ menu music playing, title screen never appears. Log: `xeno$fadeMaskedBodyEntity has an invalid signature`. Outer `@WrapMethod` remains. **Do not re-add those WrapOperations without the mixin-target type as argument 0.**

### 6. Hakai dissolve (code only)

- Hidden effect `xenopixelsmod:hakai_dissolve` (amplifier 0–255 = erase progress). Vanilla effect sync; **no new ModNetwork packet**.
- `HakaiFade` / `NpcDissolve` / `NpcFullDmzRenderer` wrap / first-person `shouldRenderFirstPerson` forced on while dissolving.
- User asked if it fades like the series: **the code is written to do that.** Runtime still **manual pending**.

### 7. `/xenoskillconfig` alias cleanup

- `suggest()` lists **canonical** ids only (`sparkingCooldownTicks`, not `sparkingcd` + `sparkingcooldown`).
- Aliases still `get`/`set`.
- Load migrator `XenoServerConfigKeys.promoteCanonicalFields`: unused JSON key copied onto canonical if canonical missing, then dropped. Canonical wins if both present. `CURRENT_CONFIG_VERSION` **15**.

### 8. XenoParty + CNPC quests

- `@dp` / `{RefPlayer}` NPC commands fan out to online party members **only if quest sharing is ON**.
- **Default OFF** (`XenoPartyConfig.questShareDefault`, per-party `PartyMetadataSavedData.ShareQuests`). Leader: party screen **Quest share** or `/xenoparty questshare`.
- `PartyActionPacket.Action.TOGGLE_SHARE_QUESTS` **appended** at end of enum. `PartySyncPacket` **appended** `shareQuests` boolean at end of encode/decode (`buf.isReadable()` on read). Protocol string still `"64"`.
- CNPC quest complete: `+2` skill points to **completer only** (`NpcQuestCompletionSync`).
- `/xenoquest list|status` lists CNPC active quests as `npc:<id>` via reflection on `PlayerData.questData.activeQuests`. `start npc:` tells the player to take it from the NPC (no fake accept). `javap` showed `getId()` / `getName()` on MyNPCs `Quest`.

### 9. Training dummy / soul / shadow

- `/xenotrain dummy` spawns `XenoCloneEntity.SLOT_TRAINING` (translucent player copy), **not** an armor stand. Different slot from Zanzoken `SLOT_STATIONARY` so the trainer does not fade.
- `/xenotrain shadow` now also a clone (`SLOT_SHADOW_FIGHT`): player look + DMZ hair via existing `NpcFullDmzRenderer.renderPlayerCopy`, ki blast at range / melee close (`ShadowDummyTraining.tickFight` → `NpcKiAttackDispatcher.fireKiBlast`). Replaced spawning DMZ `ShadowDummyEntity` for this command path.
- `/xenosoul`: `SuperSoulItem`s were **unregistered**; now registered + creative tab + `/xenosoul give <id> [player]`. Combat already read `SuperSoulCatalog` on the server.

### 10. In-game animator (studio)

Client-only. Files under `src/main/java/net/bullettrain/xenopixelsmod/client/anim/` and `XenoAnimStudioScreen`.

- `/xenoanim studio` — viewport, bone list (`XenoRig.COMBAT`: root, waist, head, right_arm, left_arm, right_leg, left_leg), rot sliders, KEY, REC/STOP/PLAY, timeline.
- `/xenoanim record start|stop|save|list`
- Saves GeckoLib 1.8 JSON to `config/xenopixelsmod-anims/<name>.animation.json`
- Playback on the local player via `StudioPoseBuffer` + `DmzStudioPoseMixin` (`DMZPlayerModel.setCustomAnimations` RETURN, `GeoBone.setRot*`, `require=0`):
  - `/xenoanim play <name> [loop]` / `stopplay`
  - `/xenobt3 play-clip <name> [loop]` / `stop-clip`
  - `/xenobt3 play <name>` falls through to a studio clip if it is not a `Bt3AnimationIntent`
- **Does not** inject those JSON files into DragonMineZ’s baked GeckoLib cache. In-world play is the pose-buffer mixin, not `CombatAnimationResolver`.
- Not a full Blockbench / IK editor.

### 11. NPC DMZ profile persist (last change this session)

CustomNPCs **Unofficial** `EntityNPCInterface` bytecode uses intermediary `m_7380_` / `m_7378_` for add/read additional save data (`javap` on `customnpcs-unofficial-1052708-8414335.jar`). The persist mixin only named `addAdditionalSaveData` with `remap = false`, so it **did not apply** on CNPC chunk save.

- Mixin now lists `{addAdditionalSaveData, m_7380_}` and `{readAdditionalSaveData, m_7378_}`.
- MyNPCs copy still uses official names (javap confirmed).
- `NpcCombatProfile.write` also stores an unnamespaced `XenoPixelsProfile` copy in persistent data; `hasProfile` / `read` accept either key.
- User still needs to **save the NPC with the wand once** after this build; old worlds without the tag cannot be recovered. **Relog not confirmed by the user.**

---

## Known landmines

- Re-adding `WrapOperation` on `GeoEntityRenderer.render` from `DmzZanzokenPlayerFadeMixin` with a `GeoEntityRenderer` first argument **hard-stuck `runClient` on the Mojang splash**.
- Do not bump ModNetwork `"64"`. Party packets were **append-only**. Global HUD uses a **separate** channel.
- Do not touch `NpcVitality*` / HP readout unless the user asks.
- `require = 0` does **not** skip an invalid MixinExtras WrapOperation signature.
- Working tree mixes this session with Form Studio / `/stack` / `network/form/` / `assets/customnpcs/` leftovers. Do not commit those unless asked, and do not `git add -A`.

## Suggested next checks (not done)

1. `runClient`: title screen after the splash fix; `/xenoanim play` and `/xenobt3 play-clip` on a saved clip.
2. Wand-save an NPC, stop the world, relog: VIT/form/hair still there.
3. Zanzoken host actually dims (WrapMethod + LivingEntityRenderer only).
4. Hakai target fade + first-person ghost.
5. Full `./gradlew test` then `build jarJar serverJar -PofflineMcMeta` after the persist + xenobt3 play-clip edits.
)
