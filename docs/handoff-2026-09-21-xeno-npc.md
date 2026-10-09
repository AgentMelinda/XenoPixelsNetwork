# Handoff — native Xeno NPC: editor, models, speech, and four live bugs

**Date:** 2026-09-21
**Repository:** `C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen`
**Branch:** `1.21.1`
**HEAD:** `53a025e45a4fecc2aeb1a2d062db550f88bbf571`

**Evidence rule for this document.** Every claim is repo source, the pinned decompiled 1.21.1
source, the DragonMineZ 2.1.3 jar read with `javap`, or a command run this session whose output was
checked. Anything unverified is labelled as such. Compilation and unit tests are **not** evidence
that something renders or behaves correctly in game, and that distinction is kept throughout.

---

## Current state

- **Nothing is committed.** 566 dirty paths, 347 untracked, 0 staged. All the NPC work is in
  untracked files under `npc/`, `client/npc/`, `client/ui/atlas/` and the texture atlas. **Git
  cannot recover these if overwritten** — back up before editing.
- Network protocol is **76**. Client and server need the same build; a mismatch drops packets.
- Atlas is 160 PNGs / 40 registered shapes.
- `gradlew.bat test`, `build jarJar serverJar` and `buildApiExampleAddon` all pass. Server jar has
  **0** `META-INF/jarjar/` entries.

---

## The four bugs — all fixed this session

### a. Speech bubbles and dialogue never resolved — FIXED

`SimpleJsonResourceReloadListener` strips the loader's directory prefix when building map keys.
Verified in `FileToIdConverter.fileToId`:
`path.substring(prefix.length() + 1, path.length() - extension.length())`.

```
file          data/xenopixelsmod/npcs/lines/default.json
loader prefix npcs/lines
registers as  xenopixelsmod:default
role looks up xenopixelsmod:npcs/lines/default      <- never matched
```

Every line set and dialogue resolved to nothing, so right-click fell through to the operator
readout. The full-path ref spelling predates this work (it was in `quest.json` already), so rather
than rewrite the datapack, `npc/XenoDataRefs.java` reduces a ref to the key the loader actually
registered. Both spellings now resolve. `XenoDialogues.get(String)` and
`XenoNpcLineSets.get(String)` go through it.

Covered by `npc/XenoDataRefsTest` — 9 methods, including the exact ref strings shipped in the role
JSONs. Nothing asserted the key shape before, which is why this went unnoticed.

### b. Unset display size rendered everything at 0.05x — FIXED

`NpcDisplayApply.getSize` reads a CustomNPCs Display object by reflection; a native Xeno NPC has
none, so it returns `0`, and `Math.max(0.05f, getSize(owner) / 5.0f)` gave a twentieth scale.

`NpcDisplayApply.sizeScale(entity, profile)` is now the single rule: size 0 means *unset*, falls
back to the profile's `baseSize`, else 1.0. Four sites were rewritten onto it:

| Site | Was |
|---|---|
| `NpcFullDmzRenderer:196` (model) | fixed earlier, now uses the shared helper |
| `NpcFullDmzRenderer:440` `worldAuraFactor` | aura at 0.05x |
| `NpcKiAttackDispatcher:361` | `0/5 = 0`, and the `abs(scale-1) < 1e-3` early-out did not catch it, so projectiles were repositioned by a zero scale |
| `NpcTransformSystem:411`, `:434` | transform particles at 0.05x |

`NpcAuraClient.cnpcSizeMul` already handled this correctly and was the in-repo precedent. A grep
confirms no raw `getSize(...) / 5` remains. Covered by `compat/npc/NpcDisplayApplySizeTest` — 5
methods, profile arm only, since a `LivingEntity` cannot be built without a client.

### c. Ki weapons and shaders — INVESTIGATED, no native-only defect found

The ki-projectile defect above was real and is fixed. Beyond that:

- Ki **weapons** sync through `NpcFullDmzRenderer.syncKiWeapon`, which sets DMZ's own
  `kimanipulation` skill and weapon type on the proxy, and `kiWeaponOn` is carried in
  `visualOptionsTag`, so it reaches clients.
- Shader handling exists: `client/compat/dmz/KiWeaponRenderTypes.select` picks a shader-pack-safe
  pipeline when Iris is active, applied through `DmzWeaponsLayerIrisMixin` and
  `DmzRenderHandIrisMixin`. Those target `com.dragonminez.client.render.layer.DMZWeaponsLayer`,
  which the native FULL path goes through, so they cover native NPCs too.

**No fix was made here and none is claimed.** If ki weapons still look wrong after (b), it is a
different defect and needs a fresh trace.

### d. The Brain tab's Vanish toggle did nothing — FIXED

**My earlier diagnosis in this file was wrong and is corrected here.** I had written that nothing
consults the brain flags for a native NPC. That is not true: `NpcProfileLifecycle:150-197` ticks
`NpcCombatBrain` (or `NpcSagaCombatBrain` when the profile is v2) for **any** entity with a combat
profile, gated on `profile.combatBrain`. A native Xeno NPC has a profile, so it is ticked. The flags
are read.

The real bug was narrower: of four `NpcCombatMoves.vanish` call sites in `NpcCombatBrain`, only one
(line 338) checked `allowBrainAction("vanish", …)`. The other three did not:

- `case VANISH` — the action named after the toggle performed it regardless
- `case REPOSITION` — a reposition is a vanish
- `heavyHit` — opens by vanishing behind the target, a back door to the same teleport

All three are now guarded. `NpcSagaCombatBrain:283,365` were checked and **already** guarded on
`profile.brainVanish`, which is consistent with the toggle failing only on the default brain.

`npc/brain/XenoNpcBrainV5` is 35 lines of pathfinding and reads no flags — but it is not the only
brain running, which is what I previously got wrong. The unused `npc/brain/v6/` package
(`XenoNpcActionKind`, `XenoNpcCooldowns`, `XenoNpcDecision`, `XenoNpcScheduler`) remains unused;
whether it should become the native brain is still an open design question.

---

## Earlier work this session (all uncommitted)

### Editor

- `client/npc/editor/EditorRow`, `EditorLayout`, `EditorFooter` — rows as data, two per line,
  auto-paginated against a real body-height budget with the footer reserved out of it.
- **The original "values not saving" was two faults.** `EditBox` was constructed at width 1 and
  resized after `setValue`; `setValue` runs `scrollTo` immediately and at width 1 the inner width is
  negative, so `displayPos` was pushed past the end and every populated field drew blank. Separately,
  `XenoNpcData.toTag()` carries no `Profile` key but the editor read `data.getCompound("Profile")` —
  a missing key answers as an empty tag, so the editor rebuilt a **default profile on every open**.
  Fixed by `XenoNpcData.editorPayload`, used by both open sites.
- Title had no backing field and wrote `editedFaction`; `XenoNpcData` now has a real `title`.
- Tabs re-laid to the MyNPCs order; DMZ's six stats moved to the DMZ tab; hair moved to the
  appearance sub-screen (and its cycler was then missing entirely until re-added).

### Models and appearance

- `client/npc/XenoNpcRenderer` dispatches on `modelKind`: `VANILLA` / `GECKOLIB` / `ENTITY` (mimics
  any registered `EntityType`, guarding against mimicking a Xeno NPC, which would recurse).
- **Appearance was editable but inert**: `NpcDmzAppearance.mode` ships `OFF` and both
  `NpcFullDmzRenderer:100` and `NpcDmzAnim:52` require `FULL`. A Mode row was added; new humanoids
  spawn `FULL`; existing NPCs are deliberately not migrated.
- **The world model stayed Steve while the preview was right** because the world path reaches
  `NpcFullDmzRenderer.render` only through CustomNPCs/CNPC-Gecko mixins, and a native NPC has no
  mixin to route it. `XenoNpcRenderer` now asks `isFull()` and delegates; saving also calls
  `NpcAppearanceFx.sync(npc)`, without which an edit only appeared after re-tracking.
- `client/npc/NpcAppearanceParts` — per-race part counts from DMZ's `TextureCounter` and the
  race→model-base mapping. **This corrected an earlier claim of mine that part counts could not be
  enumerated**; they can, and `GuiNpcDmzAppearance` had been using that API all along.

### Behaviour, lifecycle, protection

- `npc/XenoNpcBehaviour` plus nine entity overrides for the reference's Stats/Display flags. Hooks
  were read from source first: `getDimensions` is `final` (use `getDefaultDimensions`) and
  `canBreatheUnderwater` is `final` and tag-driven (so "Can Drown" is enforced in
  `decreaseAirSupply`). Three flags default true and check key **presence**, or existing NPCs would
  silently stop drowning.
- `/kill` refused via a `kill()` override — **not** damage immunity, combat still kills. Real
  deletion through `XenoNpcDeletePacket`, which cancels the respawn **before** discarding.
- **Hakai erased NPCs from two separate sites.** `NpcHakai:186` was fixed first; the player-cast path
  at `HakaiChannelSystem:300` was missed and only fixed after the user reported it still happening.
- **Respawn lost all settings** for the same reason as the editor bug — `die()` scheduled with
  `npcData.toTag()`, identity only. Now uses `editorPayload`.
- Home anchor and leash in `XenoNpcData`; respawn at home; `tickLeash` walks a strayed NPC back.

### Speech, dialogue, sound

- `npc/lines/` — `XenoNpcLines`, `XenoNpcLineSets`, `XenoNpcSpeech` (server-side per-category cycle).
- `client/npc/speech/` — `SpeechBubbleQueue` and `SpeechBubbleRenderer`, ported from the supplied
  `java.zip`. **Keep the renderer's provenance-by-confidence-tier class doc**: it records what was
  copied from the decompiled DMZ jar versus composed. The `speech_bubble` sprite is an **original**
  shape; neither MyNPCs nor DMZ ships a dialogue bubble.
- `npc/dialog/` and `client/npc/XenoNpcDialogueScreen`. Text navigation is client-local; a
  consequential choice sends an option **index** and the server re-reads its own dialogue. Commands
  run as the NPC at permission 2, behind a config defaulting **off**.
- `client/ui/atlas/AtlasSound` — DMZ's `ui_menu_switch` / `confirm_menu`, verified in
  `dragonminez/sounds.json`. All five atlas widgets override `playDownSound` empty to suppress the
  vanilla click, the same technique vanilla's own `EditBox` uses.

---

## Verified

- `gradlew.bat compileJava -PofflineMcMeta` — passed.
- `gradlew.bat test -PofflineMcMeta` — passed.
- `gradlew.bat build jarJar serverJar -PofflineMcMeta` — passed; `jarjarEntries=0`.
- `gradlew.bat buildApiExampleAddon -PofflineMcMeta` — passed.
- The atlas generator reproduces all 96 original PNGs **pixel-identically** (per-pixel
  `ImageChops.difference`); 64 shapes were added that way.
- All 24 original sprite dimensions cross-checked against the supplied `PanelTexture` enum.

## Not verified

- **Everything visual.** No claim here that a screen or model renders correctly is backed by a
  running game unless the user confirmed it in session. The user did confirm: the live preview
  renders, text fields show values, the editor opens, and body type works in the preview.
- **All four bug fixes are unconfirmed in game.** They are source-correct and tested where testable;
  bubbles, aura scale, ki projectile origin and the Vanish toggle all still need a live check.
- Multiplayer: no second client was used, so the S2C speech broadcast is unproven.
- GeckoLib and ENTITY model kinds have never rendered a frame.
- Whether (b) fully explains the reported ki-weapon appearance.

## Next steps

1. **Check the four fixes in game**, in this order:
   - Plain right-click (not sneak) an empty-handed humanoid NPC → a bubble appears; click again and
     the line advances. Sneak-click always shows the operator readout by design and is not a symptom.
   - A second player nearby sees the same bubble.
   - The aura renders full size; transform particles are full size; NPC ki attacks fire from the
     right place.
   - Turn Vanish off in the Brain tab → the NPC stops teleporting behind its target, including via
     Heavy Hit.
2. Then the Advanced-tab set — Sounds, Marks, Night, Linked NPCs, Editing Mode. See `plan.md` §2;
   note that Marks needs a `mark` shape generated, as the icons do not exist.
3. Do not stage or commit without explicit instruction. Never `git add -A`.

## Reference

- `plan.md` — the current plan.
- `docs/atlas-ui-doco.md` — the atlas UI contract: 1:1 blits, the `ScaledScreen` virtual canvas, how
  to generate a new sprite size, and its own "what is not verified" section.
- `doco.md` — MyNPCs feature reference; §§4–19 for what remains.
- `ai/handoff-template.md` — the template this follows.
