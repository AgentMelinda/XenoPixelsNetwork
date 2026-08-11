# Vanish: shade effect and custom sound

## What ships

The black afterimage is implemented and on by default. `combat/VanishShadeFx` stamps a humanoid
silhouette of near-black dust at the point a fighter vanished from, oriented to the way they were
facing, wrapped in electric arcs and a thunder crack. Dust particles live roughly a second, which
is where the effect's duration comes from — it is not on a timer.

It fires from both vanish paths in `Bt3CombatPacket`: `handleVanish` and `handleSuperCounter`.

Server config, in `config/xenopixelsmod-server.json`:

```json
"vanishShadeEnabled": true,
"vanishShadeDensity": 1.0,
"vanishThunderVolume": 0.35,
"vanishSoundOut": "",
"vanishSoundIn": ""
```

`vanishShadeDensity` runs 0–3. At `1.0` the silhouette is about fifty particles; lowering it thins
the figure evenly rather than dropping a whole limb. `0` disables the silhouette while leaving the
arcs and thunder. `vanishThunderVolume` is `0`–`1`; `0` silences the crack without touching the
visual.

## Current wiring

`ModSounds` registers one event, `xenopixelsmod:vanish`, backed by
`assets/xenopixelsmod/sounds/vanish_out.ogg` through `sounds.json`. One clip serves both ends of
the teleport, which is why the event is named `vanish` rather than after either end — `sounds.json`
maps event id to file path, so the file keeps its original name.

Both config keys default to it:

```json
"vanishSoundOut": "xenopixelsmod:vanish",
"vanishSoundIn": "xenopixelsmod:vanish"
```

> **The bundled `vanish_out.ogg` is not an Ogg Vorbis file.** It is an MPEG-1 Layer III (MP3)
> stream with an ID3v2.4 tag and an `.ogg` extension — zero `OggS` pages. Minecraft's sound engine
> cannot decode it, so vanish is silent until the file is genuinely converted. Renaming does not
> convert. See "Fixing the bundled file" below.

## Why the sound is a config id and not a bundled file

`vanishSoundOut` / `vanishSoundIn` take a sound id — for example `xenopixelsmod:vanish_out`, or any
sound from an installed mod. Empty means the built-in behaviour: DragonMineZ's `evasion1` /
`evasion2`, falling back to the vanilla enderman teleport. An id that does not resolve falls
through the same chain rather than silencing the move.

Two reasons it works this way rather than shipping a `.ogg`:

1. **Dangling sound entries are not free.** Declaring a sound in `sounds.json` whose `.ogg` is
   absent makes every client log a missing-asset error on resource load. The mod must not ship an
   entry it cannot back with a file.
2. **Licensing.** The sound this was requested from is ripped Dragon Ball game audio. This mod is
   All Rights Reserved and distributed publicly, so third-party audio pulled from a sound-effect
   site cannot go in the jar. Pointing the config at a file the server owner supplies keeps that
   decision — and that liability — with the person who has the rights.

## Fixing the bundled file

Convert to real Ogg Vorbis and overwrite `assets/xenopixelsmod/sounds/vanish_out.ogg`. No code or
JSON changes are needed — the wiring already points at that path.

With ffmpeg:

```sh
ffmpeg -i vanish.mp3 -ac 1 -ar 44100 -c:a libvorbis -q:a 4 -t 0.5 vanish_out.ogg
```

In Audacity: open the MP3, Tracks → Mix → Mix Stereo down to Mono, then File → Export → Export as
OGG.

Trim it hard. The current clip is about **6.3 seconds**; a vanish fires one sound at departure and
another at arrival, so anything past roughly half a second overlaps itself in a fast exchange and
stacks into noise. The `-t 0.5` above does the trim, but picking the best half-second by ear is
better than taking the first.

Verify it worked — a real Ogg Vorbis file starts with `OggS`:

```sh
head -c 4 src/main/resources/assets/xenopixelsmod/sounds/vanish_out.ogg   # -> OggS
```

## Adding a different vanish sound

1. Convert your clip to **OGG Vorbis**, mono, 44.1 kHz. Keep it under about half a second; the
   vanish plays one at departure and one at arrival, and anything longer overlaps itself in a
   fast exchange.

2. Put it at `src/main/resources/assets/xenopixelsmod/sounds/vanish_out.ogg` (and
   `vanish_in.ogg`).

3. Create `src/main/resources/assets/xenopixelsmod/sounds.json`:

   ```json
   {
     "vanish_out": {
       "category": "player",
       "sounds": [{ "name": "xenopixelsmod:vanish_out", "stream": false }]
     },
     "vanish_in": {
       "category": "player",
       "sounds": [{ "name": "xenopixelsmod:vanish_in", "stream": false }]
     }
   }
   ```

4. Register the events. There is no `SoundEvent` `DeferredRegister` in the mod yet, so add one
   alongside the other registries in `XenoPixelsMod` — the same pattern `ModBlocks` and
   `ModBlockEntities` use — creating `SoundEvent.createVariableRangeEvent` for each id.

5. Set the config:

   ```json
   "vanishSoundOut": "xenopixelsmod:vanish_out",
   "vanishSoundIn": "xenopixelsmod:vanish_in"
   ```

Steps 1–4 are the only part that needs the audio file to exist. Step 5 alone is enough if you want
to point vanish at a sound that some other installed mod already registers.

## Not to be confused with

`AfterimageFx` is the sparse magenta/cyan dust *trail* drawn between the departure and arrival
points. It is unchanged. The shade is the thing left standing at the origin.
