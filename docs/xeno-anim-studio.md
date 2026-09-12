# Xeno Anim Studio

An in-game keyframe animator for the DragonMineZ GeckoLib player rig. Open it with `/xenoanim
studio`. It poses bones, keys them on a timeline, plays the result back on your own character, and
exports a GeckoLib 1.8 animation JSON that Blockbench can open.

It is a **bone poser**, not Blockbench. There is no mesh editing, no IK and no bone creation. What it
gives you is the part that is painful to do outside the game: seeing the pose on the real
DragonMineZ model, at real scale, with the real hair and aura, while you set it.

## Quick start

1. `/xenoanim studio`
2. Click a bone in the left dock, drag the sliders in the right dock until the pose looks right.
3. Press **KEY**.
4. Drag the timeline rail forward, pose again, press **KEY** again.
5. Press **PLAY**.
6. Type a name in the top-left box and press **SAVE**.

The file lands in `config/xenopixelsmod-anims/<name>.animation.json`.

## The rig

Seven bones, which is exactly the bone set every clip in
`assets/xenopixelsmod/animations/entity/bt3_combat.animation.json` uses:

`root`, `waist`, `head`, `right_arm`, `left_arm`, `right_leg`, `left_leg`

A bone you never key is left out of the export entirely, and DragonMineZ keeps animating it. That is
deliberate: a clip that only keys `right_arm` is an arm animation layered over whatever the legs are
already doing, not a full-body animation with dead legs.

## How a clip is stored

A clip is **a track per bone, and four channels per track** — rotation, position, scale and
visibility — each keeping its own key times, in seconds. That is the shape a GeckoLib animation file
already has, and it means:

- Two bones can be keyed at genuinely different times. So can two channels of the same bone.
- A key is not pinned to a 1/20 s tick. **SNAP** keeps the playhead on whole ticks for ordinary work;
  turn it off to place a key between them.
- Keying a bone never disturbs another bone's keys, whatever time they sit at.

The four timeline rows under the rail (`R` `P` `S` `V`) show the selected bone's channels separately,
so it is visible at a glance that rotation and position are keyed independently.

## Buttons

### Transport (top row)

| Button | What it does |
|---|---|
| name box | The clip name. Changing it and pressing SAVE writes a new file under the new name. |
| REC / STOP | Record the live player. Captures the **whole body pose** off the model each tick. |
| PLAY / PAUSE | Play the scene. Space does the same. |
| PV POSE / BAKED | What PLAY does — see [Two previews](#two-previews). |
| LOOP ON / OFF | Wrap at the end during playback, and write `loop: true` into the export. |
| SAVE | Write the clip, then re-bake it so a bound combat move picks it up at once. |
| LOAD | Open a clip — from saved clips, the shipped BT3 set, or DragonMineZ's own. |
| HELP | The same tables as this section, in game. |
| X | Close. |

### Keying (second row)

| Button | Key | What it does |
|---|---|---|
| KEY / UPD | `K` | Key at the playhead. Reads `UPD` when a key is already there, because that press is an edit. |
| ALL / BONE | | `ALL` keys every posed bone. `BONE` keys only the selected one. |
| DEL | `Delete` | Remove every key at the playhead. |
| `\|<` / `>\|` | `Shift`+`Left` / `Right` | Jump to the previous / next key. |
| E … | | Cycle the easing of the keys at the playhead. |
| CPY / PST | | Copy and paste the whole live pose. |
| MIR | | Paste it mirrored: `right_*` and `left_*` swap, and yaw, roll and sideways translation flip. |
| BIND | | Bind this clip to a BT3 combat move. |
| UNDO / REDO | `Ctrl+Z` / `Ctrl+Y` | 32 steps. |

### Shaping (third row)

| Button | What it does |
|---|---|
| SNAP ON / OFF | Whether the playhead lands on whole ticks. |
| SETTLE | Ease the selected bone's current channel from where it is now back to rest, over half a second. The smooth-return button. |
| SMOOTH | Three-point average across the channel, first and last key pinned. Cleans up a recording. |
| BRIDGE | Turn the curve between the two keys around the playhead into real keys. |
| THIN | Drop keys the neighbours already describe (Ramer–Douglas–Peucker), with a per-channel tolerance. |
| MOTION | Continuous motion — see [Continuous motion](#continuous-motion). |
| SRC | Where LOAD reads from: `Saved`, `Shipped BT3`, `DragonMineZ`. |
| EXPORT | Development runs only — see [Editing the shipped clips](#editing-the-shipped-clips). |

### Inspector (right dock)

| Control | What it does |
|---|---|
| ROT / POS / SCALE | Which channel the three sliders drive. |
| X / Y / Z sliders | Drag them, or use the `-` / `+` button at each end. Steps are 5° for rotation, 0.5 for position, 0.1 for scale. |
| RESET BONE | Back to rest on every channel: rotation 0, position 0, scale 1, visible. |
| ONION ON / OFF | Ghost the previous and next key behind the live pose. |
| RESET VIEW | Camera back to straight on. |

Ranges are ±180° for rotation, ±16 model units for position, and 0–4 for scale.

### The view

Drag to orbit, wheel to zoom, shift-drag or middle-drag to pan. `F` re-frames, `R` resets.

This is an explicit orbit camera, not the vanilla inventory preview. The vanilla
`renderEntityInInventoryFollowsMouse` derives the pose from the cursor —
`yBodyRot = 180 + atan((centre - mouseX)/40)*20` against `yRot = 180 + …*40` — which over a viewport
several hundred pixels wide saturates into a ±63° yaw with the body at half the head's angle, and
arrives visibly twisted. It also restores only five of the entity's eight rotation fields, leaving
`yBodyRotO`, `yRotO` and `xRotO` modified on the **real local player**, which showed up as the
player's own view lurching after using the studio. The studio saves and restores all eight.

### Timeline

- Click or drag the rail to scrub. `Left` / `Right` step one tick.
- `-20` / `+20` change the scene length. The scene is never shorter than the last key.
- Tick marks are one second apart. Timecodes read `mm:ss:ticks` at 20 ticks per second.

## Two previews

`PV POSE` scrubs the studio's own pose buffer. Instant, seekable, and what editing wants.

`PV BAKED` saves a scratch clip, bakes it through GeckoLib, and hands it to DragonMineZ's animation
controller — the same path a bound combat move takes. What you see is what the move will render.
It turns the pose buffer off while active (the two would fight over the same bones) and stops the
screen pausing the world, because a paused world runs no animation controller.

Use POSE to build the clip and BAKED to check it before binding.

Both previews read the same numbers. The studio holds what the animation file holds — degrees,
Bedrock sign convention, relative to the bone's rest pose — and GeckoLib 4.9.2 plays a file as
`bone.rot = initialSnapshot + toRadians(-x | -y | z)` (`BakedAnimationsAdapter` negates X and Y,
`AnimationProcessor` adds the snapshot; position and scale are absolute). The live preview applies
exactly that through `StudioBoneSpace` in `DmzStudioPoseMixin`, and the recorder inverts it.
Until 2026-09-12 the preview wrote `toRadians(value)` straight into the bone, so PV POSE showed
X and Y rotation mirrored against PV BAKED and against a shipped clip playing in game. Parity in a
running client is **not verified** — no fresh client run was made for the change.

## Blending

A clip carries `blendInTicks` and `blendOutTicks`, 3 each by default. Playback ramps the pose
buffer's strength up at the start and down at the end, and stopping ramps out rather than cutting, so
bones ease into the first key and back to whatever DragonMineZ was doing. At full strength the
behaviour is identical to a straight overwrite.

## Continuous motion

`MOTION` opens a panel of oscillators — a wave on one bone, one channel, one axis, added on top of
whatever the keys say. Amplitude, period, phase and shape (`sine`, `triangle`, `pendulum`, `orbit`).

- **Live**, it is evaluated during playback, so it stays one editable knob. This works on the
  playback paths this mod owns.
- **Baked** (`* bake every motion into keys`), it is sampled into real keys, so it survives into a
  plain GeckoLib file and into a bound combat move, where none of our code is running. Baking clears
  the generators, because leaving them on would apply the wave twice.

`orbit` is a quarter-cycle ahead of `sine`: one of each, on two axes, traces a circle.

## Easing

Easing belongs to a key and governs the segment **arriving** at it, which is the Bedrock convention
GeckoLib follows. The cycle is:

`linear` → `step` → `easeinsine` → `easeoutsine` → `easeinoutsine` → `easeinoutcubic` → `catmullrom`

Those ids are written straight into the keyframe as `"easing": "<id>"`, and they are the ids GeckoLib
4.9.2 actually registers, so a clip eases the same way in the studio as it does once baked. `linear`
is the default and is left out of the file.

`catmullrom` is a spline through neighbouring keys rather than a curve over one segment. It is
exported faithfully and GeckoLib honours it, but the studio previews it as linear — `BRIDGE` is how
you turn it into something that looks the same everywhere.

## File format

A clip is a normal GeckoLib 1.8 animation file holding one animation named
`combat.xeno_<clip name>`:

```json
{
  "format_version": "1.8.0",
  "animations": {
    "combat.xeno_my_jab": {
      "loop": false,
      "animation_length": 0.5,
      "bones": {
        "right_arm": {
          "rotation": {
            "0.000": { "vector": [0, 0, 0] },
            "0.250": { "vector": [-80, 10, 0], "easing": "easeoutsine" }
          },
          "position": { "0.250": { "vector": [0, 0, 1.5] } }
        }
      },
      "sound_effects": { "0.250": { "effect": "minecraft:entity.player.attack.strong" } },
      "particle_effects": { "0.250": { "effect": "minecraft:crit", "locator": "right_arm" } },
      "timeline": { "0.250": "xeno:impact" },
      "xeno:visibility": { "left_arm": { "0.000": true, "0.300": false } },
      "xeno:blend": { "in_ticks": 3, "out_ticks": 3 },
      "xeno:motion": [
        { "bone": "waist", "channel": "ROTATION", "axis": 1,
          "amplitude": 8.0, "period": 1.5, "phase": 0.0, "wave": "SINE" }
      ]
    }
  }
}
```

Times are seconds. The three `xeno:` blocks are this mod's own; GeckoLib ignores keys it does not
recognise, so the file stays a valid animation everywhere else.

## Event keyframes

Sound, particle and instruction keyframes are authored on the clip and written in GeckoLib's own
`sound_effects`, `particle_effects` and `timeline` blocks.

**They fire on the playback paths this mod owns** — `/xenoanim play`, the studio's POSE preview, and
NPC clip playback. Sounds go through `playLocalSound`, particles spawn at the entity, and
instructions are posted as `AnimInstructionEvent` on the NeoForge event bus for scripts to hook.

**They do not fire through a bound combat move.** That goes through DragonMineZ's own
`AnimationController`, which registers no keyframe handlers of ours. This is a real limit, not an
oversight — if you need an effect on a bound move, drive it from the code that triggers the move.

Two smaller notes: the `locator` bone is stored and round-trips, but a bone's world position is only
known inside the renderer, so particles currently spawn at the entity. And only particle types that
need no extra data can be spawned from an id alone.

## Bone visibility

GeckoLib 1.8 has no visibility track, so the studio keeps one in the `xeno:visibility` sidecar and
applies it with `GeoBone.setHidden`. Like event keyframes, that works on this mod's playback paths
and not through a baked GeckoLib controller.

## Recording

`REC` samples the finished pose off the model every tick — every bone's rotation, position and scale
after DragonMineZ has run its own animations. That is a real body pose, not an approximation.

It lays down a key every tick, which is a lot. `THIN` is the companion: it drops what the neighbours
already describe and leaves something a person can edit.

If the player has no DragonMineZ model to sample, recording falls back to body yaw, head pitch and
swing progress — three values read straight off the entity.

## Editing the shipped clips

`SRC` switches what `LOAD` lists:

| Source | What it holds |
|---|---|
| `Saved` | `config/xenopixelsmod-anims/` — the studio's own output. |
| `Shipped BT3` | All 106 `combat.xeno_*` clips this mod ships. |
| `DragonMineZ` | DragonMineZ's own combat animations, for reference. |

Both file sources are read through the resource manager, so a resource pack that overrides either
file is what you open. (That is why there is no separate "baked" source: `GeckoLibCache` holds
compiled keyframes whose values may be molang expressions rather than editable constants, while the
file gives the same override-aware content in the form it was authored in.)

Loading from a file source gives you a normal draft. SAVE writes it to the config directory.

**You do not have to EXPORT back into** `bt3_combat.animation.json` **to play an edit on the
server.** SAVE, then BIND (or `/xenoanim global push`). The studio/library cache wins over the
shipped clip of the same `combat.xeno_*` name. Joiners receive the library and the slot map
automatically.

BIND publishes the saved clip and attaches a **live slot** — every BT3 intent plus `HAKAI_HOLD` /
`HAKAI_FIRE`. That is server-wide (`xenoanim.global`). Combo BIND used to be this-client-only;
it now travels with the clip library.

**`EXPORT` is the development-only exception.** When
`src/main/resources/assets/xenopixelsmod/animations/entity/bt3_combat.animation.json` is reachable
from the run directory — which it is under `runClient` and is not in a shipped game — EXPORT merges
the edited clip back into that file. It backs the file up first, into
`config/xenopixelsmod-anims/.source-backups/`, then writes through a temporary file and an atomic
move. It edits tracked repository source from inside a running game, so the backup path is logged.

## Playing a clip

| Command | What it does |
|---|---|
| `/xenoanim play <name> [loop]` | Play a saved clip on yourself. |
| `/xenoanim stopplay` | Stop it, easing out over the clip's blend-out. |
| `/xenobt3 play-clip <name> [loop]` | The same, from the BT3 debug command. |
| `/xenoanim list` | List saved clips. |

## Binding a clip to a combat move

| Command | What it does |
|---|---|
| `/xenoanim bind <clip> <SLOT>` | Publish and attach. e.g. `/xenoanim bind my_jab JAB_RIGHT` or `hakai_hold HAKAI_HOLD`. |
| `/xenoanim unbind <SLOT>` | Server slot back to the shipped clip. |
| `/xenoanim bindings` | List the bindings, flagging any clip that failed to bake. |
| `/xenoanim reload` | Re-read every clip and report why any of them failed. |

Studio clips live in `config/`, outside any resource pack, so GeckoLib never bakes them.
`XenoStudioClipCache` bakes them with GeckoLib's own loader (`KeyFramesAdapter.GEO_GSON` →
`BakedAnimations`, the same call `FileLoader.loadAnimationsFile` makes), and
`DmzGeoModelBt3AnimationMixin` prefers that cache over a shipped clip of the same name.

- **Server bindings.** `/xenoanim bind <clip> <SLOT>` (and studio BIND) publishes the clip and
  stores the slot in `config/xenopixelsmod-anim-technique-bindings.json`. Every joiner gets both.
  Slots are `HAKAI_HOLD`, `HAKAI_FIRE`, and every BT3 intent (`JAB_LEFT`, `HOOK_RIGHT`, …).
- **Same-name rewrite.** Saving `hakai_hold` or `hook_left_v4` and pushing it is enough: combat
  still asks for that `combat.xeno_*` name, and the published bake wins.
- **A broken clip falls back.** A missing or malformed bound clip means the shipped animation plays
  and the reason is logged. A bad file cannot stop you punching.
- **Local leftover.** `config/xenopixelsmod-anim-bindings.json` still exists as this-client fallback
  before the server map arrives. After join, the server map wins.

## Clips on NPCs, and the server library

An NPC has to animate for *everyone* in range, and the other clients have never seen your file. So
the server keeps a library and hands it to each client on join.

| Command | What it does |
|---|---|
| `/xenoanim global push [name\|all]` | Upload your saved clips to the server (OP: `xenopixelsmod.xenoanim.global`). |
| `/xenoanim global clear` | Empty the server library. |
| `/xenoanim global list` | What this client has received from the server. |

It travels on its own channel, `xenopixelsmod:anim_clips`, so `ModNetwork` stays frozen — the same
arrangement `/xenoparts global` uses for HUD layouts. The server stores clips in
`config/xenopixelsmod-anim-library/`; clients keep their copy in `config/xenopixelsmod-anims/server/`,
apart from your own work, and a server clip wins a name clash because on a server everyone has to be
watching the same animation.

Caps, because this is player-authored content sent to every player: 64 clips, 256 KB each, 2 MB
total, and anything that will not parse is refused at the door.

## Scripting

**CustomNPCs / MyNPCs scripts** reach it through the `XenoPixels` object:

```js
XenoPixels.playClip(npc, "my_jab");          // bare name or combat.xeno_my_jab
XenoPixels.playClip(npc, "my_jab", 1.5);     // with a speed multiplier
XenoPixels.playClip(event.player, "my_bow", 1.0, 40);           // cut after 40 ticks
XenoPixels.playClip(event.player, "newhakaipose", 1.0, 60, true); // hold last pose
XenoPixels.stopClip(npc);                    // stops a scripted clip, including one already playing
XenoPixels.stopClip(event.player);
XenoPixels.canPlayClip(npc);
XenoPixels.clipDuration("my_jab");           // authored ticks, or -1
XenoPixels.listClips();                      // everything playClip accepts
XenoPixels.listLibraryClips();               // just what this server published
XenoPixels.isClipAvailable("my_jab");
XenoPixels.setMeleeAnimation(npc, "my_jab"); // wand Atk field; persists on the NPC
```

**Other mods** use `net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi`, which the script object
delegates to, so the two cannot drift:

```java
if (XenoAnimApi.isClipAvailable("my_jab")) {
    XenoAnimApi.playClip(npc, "my_jab", 1.0f);
}
```

Both are server-side calls: the server names the clip and tells every client in range to draw it.
Full DragonMineZ NPCs and live players can show them — `canPlay` / `canPlayClip` say so in advance,
and `playClip` returns false rather than pretending. A clip that only exists in one player's
studio folder must be published with `/xenoanim global push` first.

The DMZ wand **Atk** field (and `setMeleeAnimation`) picks the clip used on a committed melee
hit. Empty keeps the built-in alternating punches.

`AnimInstructionEvent` (in `api/event`) fires on the client when a clip reaches an instruction
keyframe. Both additions are purely additive, so `XenoPixelsApi.API_VERSION` did not move; test for
the `XenoAnimApi` class itself.

## Limitations

- `stopClip` now stops a scripted KI-hold that is already on the controller. Mid-clip freeze is
  not available; `hold` keeps the last authored frame.
- Event keyframes and bone visibility do not fire through a bound combat move (above).
- Particles spawn at the entity, not at the authored locator bone (above).
- The onion-skin ghosts are faded by compositing — both ghosts are drawn, a scrim of the viewport
  background is laid over them, and the live pose goes on top. The vanilla inventory-entity helper
  takes no alpha and `RenderSystem.setShaderColor` does not reliably reach GeckoLib's render types,
  so this is the approach that actually works rather than the one that looks most direct.
- Baked motion is sampled every 0.05 s. Fine for a body; not a substitute for authoring a fast wave
  by hand.

## Where the code lives

| Concern | File |
|---|---|
| Screen, buttons, timeline drawing | `client/screen/XenoAnimStudioScreen.java` |
| Orbit camera and entity rendering | `client/screen/StudioViewport.java` |
| Clip model, GeckoLib JSON in and out | `client/anim/XenoAnimClip.java` |
| One channel's keys | `client/anim/studio/AnimChannel.java`, `AnimKey.java` |
| One bone's four channels | `client/anim/studio/AnimBoneTrack.java` |
| Playhead, scene length, snapping | `client/anim/studio/AnimTimeline.java` |
| Settle / smooth / bridge / thin | `client/anim/studio/AnimMotionOps.java` |
| Continuous motion | `client/anim/studio/AnimOscillator.java` |
| Easing ids and curves | `client/anim/studio/AnimEasing.java` |
| Copy / paste / mirror, undo | `client/anim/studio/AnimPoseClipboard.java`, `AnimUndoStack.java` |
| Live bone override and blending | `client/anim/studio/StudioPoseBuffer.java`, `mixin/client/DmzStudioPoseMixin.java` |
| Recording the finished pose | `client/anim/studio/StudioBoneSampler.java`, `client/anim/XenoAnimRecorder.java` |
| Firing event keyframes | `client/anim/AnimEventPlayer.java` |
| Baking clips for combat | `client/anim/XenoStudioClipCache.java` |
| Intent bindings | `client/anim/StudioClipBindings.java` |
| Load sources and dev export | `client/anim/XenoClipSources.java`, `XenoClipSourceExport.java` |
| Server clip library | `anim/XenoClipLibrary.java`, `network/AnimClipsNetwork.java` |
| Published API | `api/anim/XenoAnimApi.java`, `api/event/AnimInstructionEvent.java` |
| Commands | `client/command/XenoAnimCommands.java` |
