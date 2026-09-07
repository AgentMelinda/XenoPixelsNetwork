# XenoPixels player animations (PlayerAnimationLibrary)

## NEW XENOPIXELS ANIMATION ASSET TO CREATE (placeholder)

`bt3_placeholder.json` holds three **throwaway proof clips** — `bt3_jab_left`,
`bt3_cross_right`, `bt3_low_kick_left`. They exist so Phase 1 of the BT3/PAL work is verifiable in
game: they prove the layer registers, the resource loads, and the trigger reaches the model. They
are hand-typed keyframes, not authored choreography, and they are **not** the real animation
assets. Replace them.

## Previewing a clip without launching the game

```
python tools/pal_preview.py src/main/resources/assets/xenopixelsmod/player_animations/bt3_placeholder.json
```

Writes a contact sheet per clip to `build/pal-preview/` — one row per camera (three-quarter and
side), one column per sampled frame. It poses the same skeleton PAL does, from the same JSON, so a
wrong rotation sign or a limp timing curve is visible in seconds instead of after a client launch.
`--name` renders a single clip, `--frames` changes the sample count.

The fighter faces **+Z**, drawn with a red nose and a ground arrow. That cue matters: without it a
forward punch and a backward one look identical in a side view, which is exactly how the first
draft of these clips got misjudged.

## Editing in Blockbench

Per the PAL docs, authoring uses Blockbench with the **GeckoLib Animation Utils** plugin, exporting
GeckoLib-format JSON. Blockbench 5.1.6 is installed on this machine and both `animation_utils.js`
and `geckolib.js` are staged in `%APPDATA%\Blockbench\plugins\`; enable them once via
*File → Plugins* (Blockbench keeps its plugin registry in an internal database, so they cannot be
switched on by dropping the file alone).

Open `tools/xenopixels_player_template.bbmodel` and animate the bones. It already carries the
correct bone names, PAL's pivots, and PAL's runtime hierarchy — `body` moves the whole fighter,
`torso` carries the head and arms, the legs hang off `body` — so a rotation previewed in Blockbench
matches what the game does. Regenerate it with `python tools/make_player_template.py`.

PAL's docs mention a player template but none exists in any PlayerAnimationLibrary repository, so
this one is generated. Its container schema is taken from the `animated_entity_model` template the
Animation Utils plugin itself embeds, not guessed.

The cubes are locked and flagged `export: false` on purpose: PAL animates the real player model and
never reads this file's geometry, so the boxes are only there to see what is being posed. Animate
the **groups**, not the cubes.

## Format

Verified from PAL 1.1.6+mc.1.21.1's own `UniversalAnimLoader` / `AnimationLoader`, not from
documentation:

- Files live in `assets/<namespace>/player_animations/` and must end in `.json`. Subfolders are
  scanned too.
- A file is `{"animations": {"<name>": { ... }}}`. A file with no `animations` key is instead read
  as a single bare player-animator animation.
- **The animation id is `<namespace>:<name>`, taken from the key inside the file — not from the
  filename.** So `bt3_placeholder.json` above supplies `xenopixelsmod:bt3_jab_left` and friends,
  and one file can carry any number of clips.
- Per clip: `animation_length` in **seconds** (PAL multiplies by 20 for ticks), optional `loop`,
  and `bones`.
- Per bone: `rotation`, `position`, `scale`, `bend`, each a map of time-in-seconds to a
  `[x, y, z]` vector. Rotations are degrees. Bedrock `pre`/`post`/`lerp_mode` keyframe objects and
  an `easing` field are also accepted.

## Bone names

From `PlayerAnimationController.BONE_POSITIONS`:

`head`, `body`, `torso`, `left_arm`, `right_arm`, `left_leg`, `right_leg`, `cape`, `elytra`.

`body` is the whole lower body pivot (hips) and `torso` is the upper body — rotating both is what
makes a strike read as full-body rather than an arm swing.
