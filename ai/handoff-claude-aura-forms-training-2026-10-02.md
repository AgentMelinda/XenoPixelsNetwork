# Handoff — HD aura v2, god forms, training NPCs, AAA bundling (Claude session, 2026-10-02)

**Date:** 2026-10-02  
**Repository:** `C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen`  
**Branch:** `1.21.1`  
**HEAD:** `53a025e45a4fecc2aeb1a2d062db550f88bbf571` (unchanged; nothing from this session is committed)

Read this first: **I never ran the game.** Everything below was built and unit-tested only. The
only runtime evidence is what the owner reported, their screenshots, and the log of their own
client. Where a section says "owner saw", that is their observation, not mine.

## Current state

- Dirty tree: 927 paths (298 modified, 629 untracked). Most of that predates this session. Nothing
  was staged, committed or pushed. Do not `git add -A`.
- `libs/aaa_particles-neoforge-1.21.1-2.3.1.jar` and `libs/architectury-13.0.8-neoforge.jar` are
  **untracked** and the build now depends on the first one for packaging too. They must be
  committed before CI or another machine can build.
- The owner tests in a Modrinth profile, not in `run/`:
  `C:\Users\Admin\AppData\Roaming\ModrinthApp\profiles\Forge 1.20.1` (it is NeoForge 21.1.250 /
  MC 1.21.1 despite the name; 167 mods; Sodium + Iris with a shader pack **enabled**). Its client
  log is `logs/latest.log` there. It connects to a server at `192.168.1.57:25565` that I cannot
  reach, so I do not know which server jar is installed.
- That profile's client jar at the time of writing: SHA-256 `c96edeb8…1011b9e32`, written 05:27.
  That is the 05:25 build. It does **not** contain the first-person aura, the 0-130 brightness
  levels or the v2 layer change (those are in the 05:44 build below).
- At about 05:21 the two 0.5.0 jars disappeared from `build/libs`. I did not remove them. I rebuilt.
- A game process (`javaw.exe`) was running during most of the session.

## Artifacts (built 2026-10-02 05:44, full `build` passed)

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `build/libs/xenopixelsmod-0.5.0-1.21.1.jar` | 58,944,872 | `734f4dd3b2bd97d99d7ecd37c8e62916913cdac4ca12434379a66bdde908f9bf` |
| `build/libs/xenopixelsmod-Server-0.5.0-1.21.1.jar` | 34,798,022 | `a69766ceded7cac46613e5a67f0273f0aefe457c979814aec279bb81c964e418` |

Client jar nests AAA Particles 2.3.1 and Modern UI 3.13.0.1. Server jar nests AAA Particles only.

## Changes (all uncommitted; `CHANGELOG.md` has a dated entry for each)

### Build / packaging
- AAA Particles is jar-in-jar in both distributions. Client: `jarJar('local.libs:aaa_particles-neoforge-1.21.1:2.3.1')`
  through a `flatDir` repo over `libs/` (a `jarJar files(...)` embed is refused: the jar has no
  `Automatic-Module-Name`). Server: `serverJarJarMetadata` task writes a one-entry `metadata.json`.
- **Contract change:** `AGENTS.md`, `ai/skills/build-release.md`, `ai/repo-facts.md` now say the
  server jar may hold `metadata.json` + the AAA jar under `META-INF/jarjar/` (was: zero entries).
  The owner asked for the server bundling ("also on server?"); I edited the docs to match.

### Network (compatibility)
- Main channel protocol **100 → 101**: appended `SecondAuraStatePacket` (S2C). Client and server
  must both be on a protocol-101 build.

### HD aura (client, `client/aura/`, `fx/aura/`)
- `HdAuraPlan`: stack form and transformation target are separate auras (v1), not one aura's outer
  colour. `/xenoaura layers on|off`.
- Second aura switch, per player, synced: `/secondaura [on|off]`, `SecondAura` (server),
  `SecondAuraClientState`, Ki Actions entry `SecondAuraNode` ("Toggle Second Aura" / "Toggle Hakai
  no Energy" in the Hakaishin form), mixin `DmzKiActionsSecondAuraMixin`.
- **Aura v2** (`/xenoaura v2`, Ki Actions "Aura V2"): `tools/effekseer/efkgen/effects/aura2.py`,
  `textures.spiked_flame`, output `effeks/aura2/` (1080 effects: 90 colours × 6 brightness levels ×
  depth-tested and `_nz`). Sized from DragonMineZ's own `getAuraScale` via two new invokers on
  `DmzAuraLayersInvoker`.
- Per-frame resize ("live", `/xenoaura live on|off`) through AAA's client API
  (`EffectRegistry.tryLoad` → `EffectDefinition.playRouted` → `addPreDrawCallback`), verified with
  `javap` against the 2.3.1 jar. Falls back to `ParticleEmitterInfo` if it throws.
- Other commands added: `/xenoaura inner`, `follow`, `maxheight`, `box`, `overlay`, `firstperson`,
  `debug`; `/xenoaura brightness` now takes 0-130 (percent).
- NPC auras: the HD aura plays on every NPC with its aura on; a Full-appearance Xeno NPC on a
  GeckoLib/mimic model no longer loses its fallback aura (`NpcAuraClient.drawnByDmz`); NPC aura
  drawn at `EventPriority.HIGH` so bubbles cover it.
- `HdAuraClient` ages "aura seen" on a render-aware clock (frame-less ticks do not count) and
  re-sends after a >250 ms freeze.

### Forms / data (server + client resources)
- `xenopixels_gods_forms.json`: new forms `ssb3`, `ssrose3` (SSJ3 hair), **appended after `ue`** as
  divinity skill levels 9 and 10. `skills_patch.json` costs and `form_skill_prices.json` extended.
  Icons from the new `tools/gen_god_form_icons.py`; `BundledFormIcons` + `DmzFormSelectMetadataMixin`.
- New stack group `forms/xenopixels_ikari.json` (skill `xenopixels_ikari`, added to `stackSkills`
  and to Beerus/Whis offerings by `DmzContentBootstrap`). Allowed only on SSJ1 grades, mastered
  SSJ, SSJ2, SSJ3: `IkariStackRules`, mixins `DmzIkariStackCompatMixin`, `DmzIkariStackChargeMixin`.

### Combat / NPCs (server)
- `hakaiMantleNoKnockback` (default true): `CombatKnockback.canKnockBack` and a
  `LivingKnockBackEvent` handler in `FormPassiveEvents`.
- `NpcKnockbackGrace`: both brain `steer` methods skip for 10 ticks after an NPC is pushed.
- `/xenotrain shadow` and new `/xenotrain dummytrain` spawn a `XenoNpcEntity` on the V9 brain
  (`TrainingNpc`), copying the trainer's DMZ character. Old clone versions: `/xenotrain classic …`.
  `/xenotrain dismiss` is own-only; `dismiss all` needs permission 2.
- `trainingDummySkillPoints` (default **false**): dummy hit milestones no longer pay skill points.
- `ClipPoseBlend`: 5-tick ease at both ends of a scripted clip on GeckoLib-model NPCs.
- `WorldEditBridge` now adapts through `com.sk89q.worldedit.neoforge.NeoForgeAdapter`
  (`adaptPlayer(ServerPlayer)`, `adapt(ServerLevel)`, checked with `javap` on worldedit-mod-7.3.8).
- `AeroConfig` + `/xenoaerotune`: `linkradius`, `unlinkradius`, `controllerradius`, `engineradius`.

## What I used

- **Effekseer 1.80.6 editor, headless**, the copy bundled in the repo:
  `tools/new_particles/Effekseer1.80.6Win/Tool/bin/Effekseer.exe -cui -in <proj> -o <efkefc>`.
  One effect takes about 1.75 s; the editor runs one copy at a time.
- **The repo's own generator**, `python tools/effekseer/gen_effects.py <set>` (`efkgen/`), with
  Python, numpy 2.4.6 and Pillow 12.3.0 for the procedural textures. I added a `--missing` flag
  and `build.build_missing` (compiles only effects not in the folder yet).
- **AAA Particles 2.3.1** (`libs/aaa_particles-neoforge-1.21.1-2.3.1.jar`) to play them. Its API
  was read with `javap`, never from memory.
- `javap` and `unzip` on the DragonMineZ 2.1.3, GeckoLib 4.9.2, WorldEdit 7.3.8 and MyNPCs 1.5.0
  jars; the tracked decompile in `tools/generated/dmz_decompiled_full`.
- `tools/gen_god_form_icons.py` (new, Pillow) for the three icons.
- One-off Python patch scripts in the session scratchpad to apply edits. They are not in the repo.
- I did **not** use the Effekseer GUI, the preview script (`preview.ps1`), or any screenshot of my
  own. I looked at DMZ's `kakarot_aura.png` and at my own generated textures as image files.

## Which AAA / Effekseer effects I made or changed

Everything under `assets/xenopixelsmod/effeks/` and `tools/effekseer/` is **untracked** in git.

| Set | What I did | State now |
| --- | --- | --- |
| `effeks/aura2/` (**new**, "v2") | Designed and generated from scratch: `aura2.py` + new texture generator `textures.spiked_flame`. Four cel-shaded spiked-flame silhouette frames after DMZ's `kakarot_aura.png`, flame licks (`BodyLicks`, `OuterLicks`, `Crown`), embers. Re-authored once to fix the sprite-size error, then `_nz` (no depth test) copies and 10%/25% levels added. | 1080 `.efkefc` (90 colours × 6 levels × 2), 6 textures, ~14 MB |
| `effeks/aura/` ("v1", made 2026-09-29, before this session) | **Not redesigned.** Its `aura_in/out/rim` definitions are untouched. I added two brightness levels (`_b10`, `_b25`) and, because the generator's colour scan now finds it, one colour `6a1b9a` (Hakaishin) at all levels. `palette.txt` went 89 → 90 lines. | 1620 `.efkefc`, 8 textures, ~18 MB |
| `punch_impact`, `punch_heavy`, `punch_guard` | **Not touched.** I only decoded `punch_impact`/`punch_heavy` to read their colours. They are the Effekseer "ToonHit" sample (see `effeks/credits.txt`), not something this repo generates. The ki impact slot reuses `punch_heavy`. | unchanged |
| `sparking_*`, `hakai_*`, `missile_*`, `ship_thruster` | **Not touched.** | unchanged |

Game-side, how v1 is *played* did change (layers, size from DMZ's aura scale, dimmer inner
effect, live resize), but its effect files are the same ones as before plus the additions above.

The owner's request to restyle the aura flames in the punch/ki-impact colour (open item 1) is
**not done**; no effect has been redesigned toward that yet.

## Verified

- `./gradlew build jarJar serverJar -PofflineMcMeta --offline` → exit 0 at 05:44; 3046 tests,
  0 failures, 0 errors. (`buildApiExampleAddon` and `runApiTestClient` were **not** run.)
- Jar contents checked with `unzip -l`: nested entries as listed above.
- Effect generation: `gen_effects.py aura2` exit 0; every authored value was kept by the Effekseer
  1.80.6 editor (`check_saved`), including `ZTest = False` on the `_nz` copies.
- DMZ / AAA / GeckoLib / WorldEdit symbols used were read from `tools/generated/dmz_decompiled_full`
  or `javap` on the exact jars.

**Owner-observed (their game, not mine):**
- Their client log shows protocol 101 connecting and `aura2/…` effects loading, with no
  "HD aura disabled" or "live scaling off" warning in the sessions I read.
- A singleplayer screenshot after the v2 re-author showed the v2 silhouette starting at ground
  level and enclosing the character. The owner has not confirmed it matches DMZ's size.
- The same screenshot showed rectangular holes in the aura where dust/rock particles were; the
  `_nz` fix for that was built afterwards and **nobody has seen it run**.

## Not verified (nothing below was observed running)

- First-person aura (placed 1.6 blocks ahead, dimmed). Built after the owner's last test.
- Brightness 10%/25% levels and 0 = off. v2 showing each DMZ layer as its own silhouette.
- `_nz` overlay, freeze re-send, render-aware clock.
- `/xenoaura box` overlay (never drawn by anyone yet).
- v1's scale/offset onto DMZ's box: `HdAuraPlan.shape(false) = {0.72, 0.92}` and
  `drop(false) = 0.44` are **estimates from authored values**, not measurements.
- SSB3 / SSRose3 in game (hair, unlock, icons). The Ikari stack in game, including whether the
  skill appears at the masters and whether the two mixins apply.
- Training NPCs: spawning, look copy, brain behaviour, lock-on, knockback grace, dismiss.
- Hakaishin no-knockback, WorldEdit bridge, clip ease, aero radii, AAA nested-jar loading on a
  client and on a dedicated server.
- Anything under the owner's Iris shader pack. `NpcAuraClient` takes a different path there.

## Mistakes I made that the next agent should not repeat

- **Effekseer sprite size.** A sprite of scale `s` is `s` across (quad ±0.5), not `2s`. I authored
  v2 assuming `2s`, so it stood at the shoulders. Fixed in `aura2.py`; do not reintroduce.
- **Changed a working default.** I made the HD aura play only with `/secondaura on` (default off),
  so the aura vanished for the owner. Reverted: it follows DMZ's aura by default (`auraFollowDmz`
  true, stored as `auraWithDmzAura`).
- **Uncapped stretch** produced a ~30-block aura; I then capped it at 1.8×, then the owner asked
  for the full ki height, so the cap default is 10 (`/xenoaura maxheight`).
- **Guessed sizes repeatedly.** Use `/xenoaura box on` with `/xenoaura both` and a screenshot.
- I several times assumed the owner was on a newer jar than they were. Check the profile jar's
  hash before reasoning about what they see.

## Open (reported by the owner, not resolved)

1. **Requested, not started:** flames rising from the feet to above the aura and then vanishing,
   flame-shaped, in the colour of the punch / ki impact effect. I only decoded that effect: it is
   the Effekseer "ToonHit" sample (`effeks/punch_impact`, `punch_heavy`), glow `RGB(255,92,32)`
   fading to `(255,64,16)`. Any change to `aura2.py` needs a full `gen_effects.py aura2` rebuild
   (~1080 effects, about 30 minutes); `--missing` does not rebuild changed effects.
2. "Aura wrong size or place" and "aura resets" in the modded profile. The reset has a plausible
   cause that is now patched (render-aware clock) but is unconfirmed. The size report came while
   the profile ran a build older than the placement fix; unconfirmed since.
3. "Inner aura flickers when moving" (v1). Cause not found. `/xenoaura debug on` logs each pulse
   to `latest.log`; the owner has not run it.
4. NPC aura with appearance Off: seen only through bubbles / in parts / not from a distance, and
   "fixed for a second after reloading chunks". Not diagnosed. Shader pack is the first suspect.
5. "DMZ aura sometimes misaligned to the sides" — not looked at.
6. "Sparking aura loses itself at high speed" — not looked at.
7. MyNPCs "ServerPlayer … script errored": it is `xenopixels_player_say.js` in that world's Global
   Player Scripts. The exception text is only in the MyNPCs script console; I never saw it.
8. Client log startup error: `CosmoSubLevelTemplateLoadMixin … target dev.egg.SubLevelTemplate was
   loaded too early`. Pre-existing, not investigated.
9. I could not find how DMZ's own Ikari "stacks with god forms" as the owner described; in the
   configs I read it is an ordinary form. The new Ikari stack was added beside it.
10. "wtf they brought back ?!" — I asked what came back and got no answer.

## Next steps

1. Confirm which jars are installed (hash) on the Modrinth profile **and** the server before
   interpreting any report.
2. Ask the owner for one third-person screenshot with `/xenoaura both`, `/xenoaura v2`,
   `/xenoaura box on`, `/xenoaura size 1`. Fix v2/v1 size from the box, not from arithmetic.
3. Do open item 1 only after 2 is settled, so the regeneration is not wasted.
4. Before any commit: review `build.gradle`, the three contract docs, the mixin json and the new
   binaries individually; commit under the owner's identity only, no co-author trailer.
5. Run `buildApiExampleAddon` and a dedicated-server start; neither was done.
