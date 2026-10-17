# Optional space handoff test worlds

Shipped in the mod jar since 2026-10-15 as the built-in pack
`mod/xenopixelsmod:datapacks/xeno_space` (copied from this folder at build time; README excluded).
It is selected automatically, including in existing worlds. No manual copy into `world/datapacks`
is needed; remove an old copied `xenospace-handoff-spike` folder to avoid loading the files twice.
Turn it off with `/datapack disable "mod/xenopixelsmod:datapacks/xeno_space"` and restart; it stays
off. `/datapack enable` plus a restart brings it back.

S8 update, 2026-10-15: Moon uses the mod's seeded `xenopixelsmod:lunar_height`
density codec, with circular crater radii 50, 250, 500, 1000 and 2500 blocks (one block
per metre). Bowls, rims, ejecta and age variation overlap across chunk boundaries.
Dark maria have less relief and fewer craters than the grey highlands. This is a
procedural lunar approximation, not a surveyed reconstruction of the real Moon.
The dedicated Moon type spans Y=-1024 through 1023; terrain is bounded to -900..560.
Moon exit is Y=700, rescue anchor Y=600, remembered re-entry Y=675.

Regenerate with `python tools/generate_lunar_spike.py`; optionally supply the pinned
Minecraft 1.21.1 server bundle to restore the original vanilla settings template.
The old geode candidate resources remain unused; the lunar biome has no features/carvers.
This pack requires XenoPixels and remains optional test content. Existing chunks keep
their terrain. Use a fresh disposable world; do not reset owner regions. Runtime crater
appearance and generation costs remain unverified until fresh evidence is recorded.

Altitude administration (operator level 2):

```mcfunction
/xenospace handoff status
/xenospace handoff on
/xenospace handoff exit xenopixelsmod:earth
/xenospace handoff exit xenopixelsmod:earth 240
```

The handoff also needs `enabled: true` in the space config. Exit overrides persist there
and are reapplied on chart reload. A climb triggers strictly above the chosen Y; standing
exactly on it does not cross. Default Earth exit remains Y=700 and its destination is
`xenopixelsmod:voidsea`. A build-height limit is not a flight-height limit.

Owner correction, 2026-10-13: previous HD visual-proxy instructions below are historical.
Their renderer is disabled; the sky is stars only. For the real Sable feasibility experiment,
move away from `0 128 0`, ensure the nearby orbit chunks are loaded and empty, then run:

```
/execute in xenospace_spike:orbit run xenospace physics-spike create
/execute in xenospace_spike:orbit run xenospace physics-spike
```

Creation is admin-only and refuses occupied blocks, nearby entities, unloaded chunks, or an
orbit world containing another Sable body. The sphere is a small stone collision prototype
with an HD Earth surface attached to its actual Sable pose. The smooth surface encloses its
voxel corners; collision is approximate. It has no wand, destruction protection or surface
travel yet. Do not use an inhabited test world. No cleanup/deletion command is added.

The Sable 2.0.3 `dimension_physics/orbit.json` definition sets gravity, universal drag and
air pressure to zero only in this optional orbit dimension. Restart the test server after
installing the updated pack before measuring the physics experiment.

Minecraft 1.21.1, datapack format 48. Implementation date: 2026-10-13.

This pack supplies a flat test surface and an empty orbit host for the seamless-travel
prototype. It uses ordinary manual teleports. Ship/rider continuity, velocity preservation,
camera continuity and loading-screen suppression are **not implemented or verified** here.
Sable sub-levels live inside a Minecraft dimension; they are not dimension-transfer targets.
Neither world is added to the default mod distribution.

## Install before creating a disposable world

Stop the test server. Choose a new test game directory, set `level-name=world`, then copy
this entire folder to `<test-game-directory>/world/datapacks/xenospace-handoff-spike/`.
Start the server normally. Do not use a production world. Dimensions must load at startup;
`/reload` cannot add these dimension keys to an already-running world.

The isolated repository proof uses:

```powershell
if (Test-Path -LiteralPath 'build/space-handoff-proof/server/world/datapacks/xenospace-handoff-spike') { throw 'Fixture already exists; choose a new test directory.' }
New-Item -ItemType Directory -Path 'build/space-handoff-proof/server/world/datapacks' -Force
Copy-Item -LiteralPath 'examples/xenospace-handoff-spike' -Destination 'build/space-handoff-proof/server/world/datapacks/xenospace-handoff-spike' -Recurse
.\gradlew.bat runServer -I build/space-handoff-proof/proof.init.gradle --console=plain
```

The task-owned init script selects that game directory and skips dev-mod removal tasks.
It is a local validation helper, not a distributed mod resource. Use a new suffix if a
previous fixture world already exists. Never replace its contents to simulate a fresh run.
For standalone servers, copy the pack before the first ordinary server startup instead.

## Manual test commands

Native function invocation requires operator permission level 2. Use a test player in
creative or spectator mode, without a vehicle or passengers. The functions do not change
gamemode, grant flight, or carry a ship. Empty terrain still has normal entity gravity.

```mcfunction
/datapack list enabled
/function xenospace_spike:diagnostics
/function xenospace_spike:to_surface
/function xenospace_spike:to_orbit
/function xenospace_spike:to_surface
```

Surface arrivals are (0,65,0), above the grass at Y=63. Orbit arrivals are (0,128,0) with
no ground. Orbit entry is rejected for survival/adventure players. Console and non-player
function calls do not choose a nearby player. Return to the overworld before ending a test:

```mcfunction
/execute in minecraft:overworld run tp @s 0 100 0
```

In creative/spectator mode this is a return to the overworld, not a guaranteed safe landing
for a survival player in an arbitrary world. Do not change mode until a safe landing is found.

Both dimensions share a fixed-time custom `xenopixelsmod:space_sky` effect and an empty
black-sky biome. The updated XenoPixels client renders a black orbit sky with 1,000 cached
stars only. HD Earth, Moon and Namek visual proxies render separately at world positions;
they grow as you approach rather than following the camera as skybox decorations.
They have no collision, physics or wand controls and do not represent enterable planetary
terrain. The subsequent sync slice now supplies their positions/radii from the server chart.
The surface
retains vanilla End sky rendering. Without the updated client, the custom effect falls
back to Minecraft's default sky and these visuals will not appear.

Restart the client after rebuilding the mod. Install the updated pack before creating
a new disposable world; `/reload` alone does not replace loaded dimension types.
The downloadable `xenospace-handoff-spike-v3.zip` includes this custom effect reference
and the current HD test instructions. The previous v2 dimension definitions also work with
the new client; the HD atlas is part of the mod rather than a separate resource pack.

The earlier fixed fixture positions are superseded by chart-space positions. Both client
and server need the updated mod because the separate addon-channel fingerprint changed.
For a disposable visual-movement test, use spectator mode and operator permissions:

```mcfunction
/xenospace body xenopixelsmod:earth move 0 128 256
/execute in xenospace_spike:orbit run tp @s 0 128 210 0 0
/xenospace body xenopixelsmod:earth move 40 128 256
/xenospace body xenopixelsmod:earth resume
```

Earth's authored radius is used; Moon follows its parent. Positions/radii come from the
three mapped chart IDs, not arbitrary planet surface coordinates. Frustum/render distance
limits visibility. Live orbital samples are not interpolated yet. Background sky remains
black with stars only. Session moves reset after a server restart.
This close-up test places the player inside Earth's chart radius; there is no automatic
surface handoff yet. A farther test at Z=400 produced no visible globe in the current
graphics stack, although the snapshots arrived. Distant visibility needs further verification.

The surface has 64 authored terrain layers; orbit has none. Features, lakes, structures,
spawns and carvers are disabled in these definitions. The pack adds no load/tick function
tags, automatic transfers or force-load tickets. Explicit player movement can load chunks.

With XenoPixels installed:

```mcfunction
/xenospace list dimensions
/xenospace list bodies
/xenospace validate
```

`xenospace_spike:surface` is a charted planet at chart position (20000,0,0); the orbit host
is deliberately uncharted. Chart coordinates are distinct from coordinates inside either
test dimension. The chart's exit altitude and zero gravity are metadata; they do not trigger
handoff or alter the player's gravity.

## Planet-to-planet test body (added 2026-10-14)

`xenospace_spike:moon_surface` is a second enterable handoff body, charted at (22000,0,0)
with `handoff_anchor_y 600`, `exit_altitude 700` and `void_damage true`. It exists only to make
a planet→planet→planet trip demonstrable: the shipped Moon (`xenopixelsmod:moon`) is a
**showcase** body with no bound dimension, so it is not a travel target and no fake Earth-Moon
content belongs in the mod jar. This fixture is the honest test stand instead.

With the production altitude handoff on (`/xenospace handoff on`), planet↔planet is the same
column rule as a round trip and needs no separate code: descending inside the host picks the
nearest enterable body whose chart column reaches you, so flying from above the surface column
to above the moon column and dropping lands on the moon, not back on the surface. An explicit
admin hop is `/xenospace travel <body>` (permission level 2, must be run as a player, not from
console); it moves you straight into that body's dimension at its surface anchor and is the
fallback when the two columns are too far apart to glide between.

This is datapack test content and follows the manual-teleport caveats above: player transfer
through the moon fixture, and the altitude trigger firing on a real climb, remain **not
verified** until a live run shows them.

## Validation and rollback

Validate JSON/references with the installed Minecraft datapack/worldgen validators and
confirm actual dimension/function loading in a fresh server log. Static validation alone
does not establish codec acceptance, player transfer, multiplayer or rendering behavior.

To end the experiment, return test players to the overworld and stop the server. Preserve
the fixture world and logs. Start a different fresh world without the pack for subsequent
ordinary play; do not remove dimension definitions from a populated production save.

## Verified observations (2026-10-13)

A fresh dedicated NeoForge 21.1.248 server loaded both dimensions and parsed all three
functions. XenoPixels loaded five chart bodies with zero diagnostics, including the test
surface; orbit remained uncharted. Explicitly loaded sample chunks contained grass at
surface Y=63, air above it, and orbit air at Y=0,64,128,383. Temporary test tickets were
removed; both dimensions had no force-loaded chunks, including after a fresh restart.

Console invocations and a task-created armor stand invoking both transfer functions left
the stand in place; the test stand was removed afterwards. No test player connected, so
actual player transfer and rejection of survival/adventure players remain **not verified**
at runtime. Ship/rider continuity, zero-gravity flight, matched rendered views and absence
of loading screens remain **not verified**. These results establish a test host, not
seamless gameplay. Evidence is in `ai/handoffs/space-handoff-worlds-2026-10-13.md`.

The earlier skybox-globe version was verified in a separate fresh client and test server:
a spectator test player was teleported into orbit, and a native Minecraft screenshot showed
a black background with all three round shaded demo globes. Sodium and Veil were present;
Iris and external shader packs were absent. The probe closed DragonMineZ's initial character
screen solely for this screenshot. This verifies the orbit visual and that manual teleport,
not the guarded transfer functions, ship transfer, shader-pack compatibility or seamlessness.
See `ai/handoffs/space-sky-2026-10-13.md` for the screenshot and exact evidence.

That skybox-globe version is superseded by the owner's stars-only sky decision. A new
client verified stars only in the background, HD Earth/Moon visual proxies after approaching,
and successful resource reload. The proxies may be outside the render frustum at the initial
orbit arrival; use the near-Earth command above to view them. Short 1,500-frame samples at
1280x720 on an RTX 2080 SUPER averaged 1.85 ms between frames with near HD visuals versus
1.87 ms in the stars-only view. This is a local scene observation, not a hardware-independent
FPS guarantee or an isolated GPU benchmark. Iris/shader packs remain untested.
See `ai/handoffs/space-sky-hd-2026-10-13.md` for the current evidence and limitations.
