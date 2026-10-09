# Maker preview local-player model path evidence (PR-D6b / KD20)

**Date:** 2026-10-03  
**Scope:** Cite a real DragonMineZ / Minecraft path that draws the **local player** entity
(full DMZ body / hair / clothes stack) inside a GUI — the only acceptable preview for
`MakerPreviewController`. Text summaries and disposable dummy entities are forbidden.  
**Pinned stack:** Minecraft 1.21.1, NeoForge 21.1.248, DragonMineZ 2.1.3
(`libs/dragonminez-2.1.3.jar`, SHA-256 matches `dragonminez_sha256` in `gradle.properties`:
`5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`).

Cross-links: design §D.3 / KD20 / PR-D6b in
`docs/superpowers/specs/2026-10-03-hd-aura-tournament-makers-design.md`; plan Task 8 in
`docs/superpowers/plans/2026-10-03-tournament-roles-makers-phase2.md`.

## Method

1. Searched decompiled DMZ character / hair GUIs under
   `tools/generated/dmz_decompiled_full/com/dragonminez/client/gui` for entity preview draws.
2. Confirmed `com.dragonminez.client.render.EntityPreviewRenderContext` public API via
   `javap -public -classpath libs/dragonminez-2.1.3.jar`.
3. Cross-checked in-repo callers that already draw the live local player the same way.

**Not claimed:** live-process proof that `MakerPreviewController` appeared correctly in a
running maker screen. This appendix is jar + decompiled + in-repo citation only.

## Candidate inventory

| Candidate | Verified where | Role | Safe for MakerPreview? |
| --- | --- | --- | --- |
| `EntityPreviewRenderContext#renderEntityInInventory` | Decompiled `EntityPreviewRenderContext.java:20-35`; `javap` public int+float overloads | Sets DMZ preview depth, then calls `InventoryScreen.renderEntityInInventory` with the given `LivingEntity` | **Yes — primary path.** Pass `minecraft.player`. |
| `InventoryScreen#renderEntityInInventory` | Vanilla / NeoForge GUI API; wrapped by DMZ context above | Actual entity draw used by inventory and DMZ character UIs | Yes, via the DMZ wrapper so hair/FP mixins see `isRendering()`. |
| `HairEditorScreen#renderPlayerModel` | Decompiled `HairEditorScreen.java:1060-1105` (~L1091 call) | Saves/restores player pose, temporarily swaps hair slots, draws **local** `minecraft.player` through `EntityPreviewRenderContext.renderEntityInInventory` | Cite as DMZ Hair Editor precedent. |
| `CharacterCustomizationScreen` / `RaceSelectionScreen` / `ModelFormPreview` | Decompiled character GUIs + `radial/ModelFormPreview.java:48` | Same `renderEntityInInventory` with the local player | Supporting DMZ GUI precedents. |
| `CharacterPortraitCache` / `XenoNeonStatsScreen` | `src/…/hud/CharacterPortraitCache.java` (`renderHudPortrait`); `src/…/screen/neon/XenoNeonStatsScreen.java:469` | In-repo Xeno usage of the same DMZ context on the live local player | Supporting in-repo precedent. |
| Text / document field summary | Prior `HairMakerScreen` / `RaceFormGroupMakerScreen` shells | Not a player model | **Rejected** for PR-D6b. |
| Disposable dummy / proxy-only entity without local stack | N/A | Would not show the player's DMZ appearance | **Rejected.** |

### Exact citations

```text
EntityPreviewRenderContext#renderEntityInInventory
  tools/generated/dmz_decompiled_full/com/dragonminez/client/render/EntityPreviewRenderContext.java:20-35
  jar javap: public static void renderEntityInInventory(GuiGraphics, int, int, int|float,
             Vector3f, Quaternionf, Quaternionf, LivingEntity)
  wraps: InventoryScreen.renderEntityInInventory(...)

HairEditorScreen#renderPlayerModel → EntityPreviewRenderContext.renderEntityInInventory
  tools/generated/dmz_decompiled_full/com/dragonminez/client/gui/HairEditorScreen.java:1060-1093
  (~L1091) LivingEntity player = this.minecraft.player;

In-repo precedents
  src/main/java/net/bullettrain/xenopixelsmod/client/hud/CharacterPortraitCache.java
    EntityPreviewRenderContext.renderHudPortrait(...)
  src/main/java/net/bullettrain/xenopixelsmod/client/screen/neon/XenoNeonStatsScreen.java:469
    EntityPreviewRenderContext.renderEntityInInventory(..., player)
```

## Recommended MakerPreviewController contract

1. `bindLocalPlayer(Minecraft)` stores `mc.player` (true local stack — no dummy).
2. `render(...)` calls `EntityPreviewRenderContext.renderEntityInInventory` with that player,
   pose `rotateZ(π)`, identity camera quat, scale fitted to the preview well (Hair Editor /
   neon stats pattern: save/restore body/head yaw+pitch).
3. `markDirty()` → `PreviewDebounce.markChanged` ≤50 ms; screens call it on widget change.
4. `setGlow(GlowTarget, id)` stores selection; controller may draw a simple green rect outline;
   screens own richer chrome later.

## Verdict

Maker local-player preview path: **READY** (path cited; **runtime unverified**)

Cited draw path:
**`com.dragonminez.client.render.EntityPreviewRenderContext#renderEntityInInventory`**
(→ `InventoryScreen#renderEntityInInventory`), as used by
**`com.dragonminez.client.gui.HairEditorScreen#renderPlayerModel`** (~L1091).
