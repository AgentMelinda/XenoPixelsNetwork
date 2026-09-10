# Claude Handoff — XenoPixels Network

**Verified at:** September 9, 2026
**Workspace:** `C:\XenoPixelsNetwork_qwen`
**Current version:** `0.3.4-1.21.1`

## Your Role

Work as a senior, premium Minecraft developer and visual specialist across NeoForge Java, mixins,
networking, gameplay systems, GUI/HUD design, pixel-art atlases, models, GeckoLib animation, NPC
compatibility, testing, and release engineering. Be confident and ambitious, but earn that confidence
through inspection, reproduction, and validation rather than guesses.

You can solve difficult Minecraft integration problems. Slow down around bytecode descriptors, UV
contracts, inherited fields, optional-mod classes, and client/server separation. Those are engineering
problems that become manageable when you inspect the real target before editing it.

## Non-Negotiable Accuracy Rules

1. Never invent a Minecraft, NeoForge, DragonMineZ, My NPCs, CustomNPCs, GeckoLib, Mixin, or Xeno API.
2. Never claim a feature is fixed, tested, visually verified, or deployed unless the corresponding
   command or manual check was actually completed.
3. If a result was only compiled or unit-tested, say exactly that. Do not call it an in-game test.
4. Inspect real classes with source, decompiled output, or `javap -p -c -s` before writing mixin
   descriptors, shadows, redirects, wrapped operations, or method arguments.
5. Use the exact installed mod versions. This workspace currently compiles against DragonMineZ 2.1.3,
   NeoForge 21.1.238, Minecraft 1.21.1, GeckoLib 4.9.2, and My NPCs 1.5.0.
6. Do not use `Object` as a shortcut when a Mixin callback or MixinExtras wrapper requires an exact
   descriptor. Mixin validates handler types against bytecode, not intent.
7. Do not `git reset`, clean, checkout, or revert the dirty worktree. It contains substantial user and
   previous-agent work. Make focused edits and inspect `git diff` for only the files you touch.
8. Do not replace working systems merely to make code look cleaner. Fix the root cause with the
   smallest compatible change.
9. Generated PNGs must keep the target atlas dimensions, UV positions, transparent footprint, and
   filtering assumptions. Never eyeball or stretch a sprite into an unknown UV cell.
10. When uncertain, say what is unknown and run a targeted inspection. Honest uncertainty is better
    than a fabricated answer.

## Latest Completed Work

### Client crash repairs

- `DmzSkillsInteractionMixin` wrapped two `SkillsMenuScreen` superclass calls with a
  `BaseMenuScreen` receiver. MixinExtras required the exact `SkillsMenuScreen` receiver and rejected
  the mixin, which then produced a `VerifyError` in `SkillsMenuScreen.keyPressed`.
- Both wrapper receivers now use `SkillsMenuScreen`.
- The reported `DmzAuraLayerSparkingMixin` log showed old `Object` parameters. Current source and the
  built bytecode already use the exact `BakedGeoModel`, `RenderType`, `MultiBufferSource`, and
  `VertexConsumer` parameters. Do not reintroduce `Object` parameters.
- My NPCs quest completion failed because the mixin shadowed `player` on
  `SPacketQuestCompletionCheck`, while the field is actually inherited from `PacketServerBasic`.
  The My NPCs and parked CustomNPCs mixins now extend their real packet superclass and use the
  inherited field.

### Xeno menu and HUD artwork

- Approved source artwork came from:
  `C:\Users\Admin\Downloads\Compressed\dragonminez_our_style_full.zip`.
- Only the eight required exact-UV atlases were vendored under
  `tools/source/dmz_our_style/dragonminez/textures/gui/`.
- `gen_dmz_menu_themes.py` now uses the ZIP's complete `characterbuttons.png` and
  `menubuttons.png` atlases, scaled 4× with nearest-neighbour sampling. This replaces the remaining
  green rows and white stock controls while preserving every DMZ UV cell.
- `gen_dmz_hud_theme.py` deterministically emits Xeno lock-on, radar, and blue/green/purple/red
  scouter resources plus a SHA-256 manifest.
- Lock-on, radar, and scouter render mixins remap only the six known DragonMineZ resource paths and
  only while `XenoHudConfig.dmzMenusThemed()` is true. `/xenohud menus stock` restores stock DMZ
  resources; `theme` and `screen` enable the Xeno resources.
- The new palette follows the approved rule: cyan normal/hover framing, gold important accents, and
  red only for destructive or warning states.

## Verification Performed

The following checks actually ran and passed:

```powershell
python tools/gen_dmz_hd_atlas.py --check
python tools/gen_dmz_menu_themes.py --check
python tools/gen_dmz_hud_theme.py --check
python tools/gen_bt3_menu_atlas.py --check
python tools/gen_bt3_hud_atlas.py --check
.\gradlew.bat test build serverJar --no-daemon
```

- JUnit: **456 tests, 0 failures, 0 errors, 0 skipped**, across 83 test classes.
- `runClient` reached the singleplayer world twice during this work.
- Final boot evidence: `Dev joined the game` at `2026-09-09 14:44:43` in `run/logs/latest.log`.
- Final boot contained no `Mixin apply for mod xenopixelsmod failed`,
  `InvalidInjectionException`, or `VerifyError` entries.
- The generated character and menu button atlases were visually inspected as PNGs.
- The client was shut down normally after the final boot.

These checks were **not** completed manually in-game and must not be described as completed:

- Triggering and visually inspecting the lock-on replacement on a live target.
- Opening both Earth and Namek radar variants and checking every marker state.
- Equipping all four scouter colours and inspecting every scan/info state.
- Activating Sparking and visually confirming the aura colour through its complete lifecycle.
- Reviewing every themed menu at every GUI scale and window size after the final button-atlas swap.

## Built Artifacts

- `build/libs/xenopixelsmod-0.3.4-1.21.1.jar`
  - Size: 30,728,615 bytes
  - SHA-256: `584AADEEAFD87E751F5E074DD4589D9AB35302213EBDE75AB69DE04DC4D4F334`
- `build/libs/xenopixelsmod-Server-0.3.4-1.21.1.jar`
  - Size: 6,581,387 bytes
  - SHA-256: `2EA20E7E92E0CE58F218D586D95ECB16A2A061D8B6AC5DEEC0ECC262B82DEFBC`
- `build/libs/xenopixelsmod-0.3.4-1.21.1-sources.jar`
  - Size: 5,615,473 bytes
  - SHA-256: `5CE0A38220B3D09632EBCA57926F312BC4F1EEA4112AF5136442FC042C99B564`

## Important Files

- `src/main/java/net/bullettrain/xenopixelsmod/client/hud/DmzHudThemeTextures.java`
- `src/main/java/net/bullettrain/xenopixelsmod/mixin/compat/dmz/DmzSkillsInteractionMixin.java`
- `src/main/java/net/bullettrain/xenopixelsmod/mixin/compat/dmz/DmzLockOnThemeMixin.java`
- `src/main/java/net/bullettrain/xenopixelsmod/mixin/compat/dmz/DmzRadarThemeMixin.java`
- `src/main/java/net/bullettrain/xenopixelsmod/mixin/compat/dmz/DmzScouterThemeMixin.java`
- `src/main/java/net/bullettrain/xenopixelsmod/mixin/compat/mynpcs/QuestCompletionCommandMixin.java`
- `tools/gen_dmz_menu_themes.py`
- `tools/gen_dmz_hud_theme.py`
- `CHANGELOG.md`

## Known Non-Blocking Runtime Warnings

The final development boot still printed existing Veil `Unsupported Uniform Type: bool` messages,
cloud-shader missing-uniform warnings, Sodium optimized-vertex warnings, and a Flywheel backend
fallback. They did not prevent world entry. Do not attribute them to this patch without reproducing
and isolating them.

## Recommended Next Actions

1. Start with `git status --short`; do not assume the dirty files all belong to this latest change.
2. Run `runClient`, set `/xenohud menus theme`, and complete the five manual visual checks listed
   above while saving screenshots at the user's normal resolution and at one smaller GUI scale.
3. If a mixin fails, copy its exact expected and found descriptors from `latest.log`, inspect the
   target jar with `javap`, and change only the mismatched handler.
4. If a texture is clipped, inspect the exact DMZ blit width, height, UV, atlas size, and widget
   hitbox before touching pixels or coordinates.
5. Re-run all five generator checks and `test build serverJar` after any change, then report the new
   artifact hashes instead of reusing the hashes above.

Keep going carefully. This is a large integration, but the hard parts are already yielding to exact
inspection and disciplined testing. Treat every crash log, bytecode descriptor, UV rectangle, and
render screenshot as usable evidence, and the remaining polish will be straightforward.
