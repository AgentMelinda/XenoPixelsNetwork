# Hair in-game apply path evidence (PR-D7c)

**Date:** 2026-10-03  
**Scope:** Evidence gate for HairMaker **Apply** — cite a real DMZ/Xeno write path that
persists `CustomHair` on the player. **Forbidden:** treating Xeno
`CustomizationManager` `hair_style_*` cosmetics as the DMZ hair codec apply path.  
**Pinned stack:** Minecraft 1.21.1, NeoForge 21.1.248, DragonMineZ 2.1.3
(`libs/dragonminez-2.1.3.jar`, SHA-256 matches `dragonminez_sha256` in `gradle.properties`:
`5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`).

Cross-links: design §D.4 / KD9 / PR-D7c in
`docs/superpowers/specs/2026-10-03-hd-aura-tournament-makers-design.md`; plan Task 11 in
`docs/superpowers/plans/2026-10-03-tournament-roles-makers-phase2.md`; codec export appendix
`docs/superpowers/evidence/2026-10-03-hair-codec-vectors.md`.

## Method

1. Searched decompiled DMZ for `*Hair*` / `UpdateCustomHair` / `HairManager` /
   `CustomHair` under `tools/generated/dmz_decompiled_full/com/dragonminez`.
2. Confirmed the same classes exist in `libs/dragonminez-2.1.3.jar` via `jar tf` + `javap -public`.
3. Searched `src/main/java` for `CustomizationManager` / `hair_style_` / existing hair write usage.
4. Compared HairMaker lab strand fields to DMZ `HairStrand` public setters.

**Not claimed:** in-game HairMaker Apply runtime proof on a live client. This appendix is
jar + decompiled + in-repo citation only.

## Candidate inventory

| Candidate | Verified where | Role | Safe for HairMaker Apply? |
| --- | --- | --- | --- |
| `UpdateCustomHairC2S#handle` | Decompiled `…/network/C2S/UpdateCustomHairC2S.java:42-68`; `javap` public ctor / `encode` / `decode` / `handle` on jar | **Server write.** `ServerPlayer` → `StatsData` → `Character.setHairBase` / `setHairSSJ` / `setHairSSJ2` / `setHairSSJ3` by index (0=Base default, 1–3 variants); `setHairId(0)`; `AppearanceSyncS2C` to tracking + self. | **Yes — primary apply target.** |
| `NetworkHandler#sendToServer` + `new UpdateCustomHairC2S(int, CustomHair)` | Decompiled `HairEditorScreen.java:736-755` (`syncHairToServer`); `javap` `NetworkHandler.sendToServer`; jar entry present | **Client send** used by DMZ’s own hair editor after local `Character` mirror. Xeno already calls `NetworkHandler.sendToServer` for other C2S packets (e.g. pad / stats screens). | **Yes — client invoke path.** |
| `HairEditorScreen#syncHairToServer` | Decompiled `HairEditorScreen.java:736-755` | Reference pattern: set local style slots, then four `UpdateCustomHairC2S` sends. Also called after `HairManager.fromFullSetCode` / `fromCode` import (`:270-276`, `:333-345`). | Cite as pattern; HairMaker sends **current style index only** (document holds one editable geometry). |
| `HairManager#fromCode` / `#fromFullSetCode` | Decompiled `HairManager.java:246` / `:325`; `javap` public | Decode `DMZ1:` / `DMZF1:` → `CustomHair` / `CustomHair[4]`. Used by DMZ import UI and Xeno `NpcHairVis`. | Supporting input only. HairMaker v1 builds `CustomHair` from strand fields (same object the codec would decode to); codes remain lab-encoded for export. |
| `HairManager#toCode` / `#toFullSetCode` | Decompiled `:226` / `:290` | Encode `CustomHair` → Base62 codes. | Not required for Apply write-back. |
| `Character#setHairBase` / `#setHairSSJ` / `#setHairSSJ2` / `#setHairSSJ3` | Decompiled `Character.java:914-926`; `javap` public | Direct capability fields written by `UpdateCustomHairC2S#handle` and locally by `syncHairToServer`. | Server path goes through the packet; local client mirror may call these like DMZ editor. |
| `HairStrand` setters + `load(CompoundTag)` | `javap` public: `setLength`, `setLengthScale`, `setRotation`, `setScale`, `setCurve`, `setColor`, `save`, `load`. **No** public `setCubeWidth/Height/Depth` — cubes only via `load` (`cw`/`ch`/`cd`). | Populate `CustomHair` from HairMaker strand models. | Yes, using verified APIs only (cube dims via save→putFloat→load). |
| `CustomizationManager` `hair_style_*` | `src/…/features/customization/CustomizationManager.java:89-95` | Xeno cosmetic slot ids (`Category.HAIR`), not DMZ `CustomHair` NBT/codec. | **No — explicitly rejected by design KD9 / PR-D7c.** |
| `/xenopixels genhaircode … apply` | `HairCodeCommands.java` | Writes `NpcCombatProfile.hairCode` / colour onto a looked-at **NPC** — not player `Character` custom hair. | **No** for player HairMaker Apply. |
| `HairCommand` `/dmzhair reset|resync` | Decompiled `server/commands/HairCommand.java` | Reset to race defaults or resync appearance — not maker write-back. | No. |

### In-repo / jar citations (exact)

```text
UpdateCustomHairC2S#handle
  tools/generated/dmz_decompiled_full/com/dragonminez/common/network/C2S/UpdateCustomHairC2S.java:42-68
  jar: com/dragonminez/common/network/C2S/UpdateCustomHairC2S.class

HairEditorScreen#syncHairToServer
  tools/generated/dmz_decompiled_full/com/dragonminez/client/gui/HairEditorScreen.java:736-755
  NetworkHandler.sendToServer(new UpdateCustomHairC2S(i, this.workingHairs[i]));

NetworkHandler#sendToServer
  jar javap: public static <MSG> void sendToServer(MSG)

Character hair setters
  tools/generated/dmz_decompiled_full/com/dragonminez/common/stats/character/Character.java:914-926

Rejected cosmetics
  src/main/java/net/bullettrain/xenopixelsmod/features/customization/CustomizationManager.java:89-95
```

## Recommended HairMaker Apply (implementation contract)

1. Map `HairMakerDocument` current style → hair index (`Base=0`, `SSJ=1`, `SSJ2=2`, `SSJ3=3`).
2. Build one `com.dragonminez.common.hair.CustomHair` from document face strands using
   `CustomHair#getStrand` + `HairStrand` setters; cube size via `save` / `putFloat(cw|ch|cd)` /
   `load` (no invented setter).
3. Optionally mirror onto local client `Character` (same shape as `syncHairToServer`).
4. Call `NetworkHandler.sendToServer(new UpdateCustomHairC2S(hairIndex, customHair))`.
5. Server persistence is **only** `UpdateCustomHairC2S#handle` — do not invent a Xeno C2S.

**Out of scope for this gate:** Java Base62 codec port; writing all four styles when the
document only edits one; NPC profile apply; `CustomizationManager` cosmetics.

## Verdict

HairMaker in-game Apply path: **READY** (path cited; **runtime unverified**)

Cited write path: **`com.dragonminez.common.network.C2S.UpdateCustomHairC2S#handle`**
(client invoke via **`com.dragonminez.common.network.NetworkHandler#sendToServer`** with
`new UpdateCustomHairC2S(hairIndex, CustomHair)`, matching
**`com.dragonminez.client.gui.HairEditorScreen#syncHairToServer`**).

**Replace-current-style:** Apply builds a full `CustomHair` from the maker document and
overwrites that style slot only. It does **not** load or merge the player's existing
`Character` hair into the document — empty/default strands in the document wipe that slot's
prior geometry. User-facing tooltip/status must state this wipe risk. Load-from-Character is
optional follow-on (verified getters `getHairBase` / `getHairSSJ` / … exist) and was not
required for this gate.

Until a follow-on runtime client proof logs Apply → appearance sync, treat in-game success as
**not verified**; unit coverage uses a fake transport around the plan/build steps. Export /
UI label: `path_ready_runtime_unverified` — not “Apply works”.
