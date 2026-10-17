# Server space chart

Owner correction, 2026-10-13: chart-driven HD billboard planets are disabled. The background
remains stars only. Actual planet motion belongs to Sable rigid bodies, independently of this
analytical chart. `/xenospace body ... move` edits chart metadata, not a physical planet.
The explicit `/xenospace physics-spike create` experiment assembles one radius-three stone
sphere (123 voxels) only in an empty, loaded `xenospace_spike:orbit` world. It seeds velocity
and spin once, then applies bounded central attraction through the existing physics scheduler;
it never teleports the body each tick. This experiment has no wand, destruction
protection or surface transfer yet. Use it only in the disposable test world.

The next slice attaches an HD 3D Earth surface to that actual Sable UUID. The client reads
`ClientSubLevel.renderPose(partialTick)` for both translation and quaternion rotation; it
does not receive chart positions or analytically animate the planet. One cached 4,608-vertex
mesh and one bounded 2048×1024 mipmapped texture are used. The rendered radius is 3.9 blocks
to enclose voxel corners; contact collision is still the small voxel approximation.
The current surface uses fullbright albedo; sun shading and atmosphere are future work.
The identity-only addon packet changes the protocol fingerprint, so update client/server together.

Fresh runtime evidence on 2026-10-13: the vacuum fixture's real Sable sphere completed
1.11 revolutions over 111.5 seconds, sampled radius 31.98–32.02 blocks at Y=128.5.
This verifies the small physical orbit, not production planet scale or long-term stability.
See `ai/handoffs/space-physics-2026-10-13.md` for exact commands and limitations.

Implemented slice: 2026-10-13. Java 21, Minecraft 1.21.1, NeoForge 21.1.248.

This loads and measures chart data only. It creates no dimensions, entities, chunk tickets,
ship forces, client packets, rendering, or travel. Existing flight callbacks remain unchanged.
The four bundled bodies are Sol, Earth, Moon (showcase), and Namek (`dragonminez:namek`).
Coordinates and periods are gameplay demonstration values, not astronomical measurements.

## Configuration and precedence

`config/xenopixelsmod-space.json` retains the groundwork settings. `enabled` and `realOrbits`
default to false. `gravityIntervalTicks` controls chart evaluation in **server ticks**, not
Sable physics ticks. The chart uses overworld game time, so `/time set` does not rewind it.
`workPerPhysicsTick` remains reserved for future physics consumers; it does not bound this
chart pass. `gravityContributors` now changes the query contributor capacity.

Optional `bodyDefaults` contains defaults for `kind`, `radius_blocks`, `gravity`,
`handoff_anchor_y`, `exit_altitude`, `orbital_motion`, `erasable`, and `danger_rating`.
Unspecified values use the schema defaults below. Precedence is global body defaults,
resolved datapack definitions, then YAML. Nested objects merge by key.

Datapack paths:

```
data/<namespace>/xenospace/planet/<path>.json
data/<namespace>/xenospace/chart_entry/<path>.json
```

Both produce `<namespace>:<path>` as their ID. Standard pack priority applies to the same
resource path; declaring the same body in both folders rejects the collision. `chart_entry`
requires `dimension`. Bundled JSON uses this same loader; no enum generator is involved.

## Version-one schema

```json
{
  "schema_version": 1,
  "kind": "planet",
  "display_name": "Example",
  "radius_blocks": 96,
  "position": {"x": 1000, "y": 0, "z": 0},
  "parent": "xenopixelsmod:sol",
  "orbit": {"semi_major": 1000, "period_seconds": 3600},
  "gravity": {"surface_ms2": 9.81, "falloff": "inverse_square"},
  "dimension": "minecraft:overworld",
  "spatial_anchor": {"x": 0, "z": 0},
  "handoff_anchor_y": 64,
  "exit_altitude": 700,
  "orbital_motion": true,
  "erasable": false,
  "danger_rating": 0
}
```

Every field is optional except `orbit.semi_major` and `orbit.period_seconds` when `orbit`
is present, and the dimension required for `chart_entry`. The resource path supplies the ID.
`spatial_anchor` accepts finite horizontal `x` and `z` coordinates and defaults to `(0, 0)`.
On outbound handoff, the player's X/Z displacement from this anchor is added to the current
chart/live-body X/Z position in the Voidsea. Return travel uses the saved planet-side position.
This is translation in aligned X/Z axes; the chart currently has no per-dimension orientation
transform, so it does not rotate the displacement for differently oriented surface worlds.
If the mapped coordinates exceed the supported world-coordinate range, the handoff is refused
instead of clamping the player's position.
Default kind is planet; name defaults to ID, radius to 96, position to zero, gravity to zero,
anchor Y to 64, exit altitude to 700, orbital motion to true, erasability to false, danger to 0.
Dimension and parent are optional. Unknown fields are rejected with diagnostics.

Kind: star, planet, moon, station, black_hole, showcase, pocket, ruins. These are chart
metadata; black-hole gameplay is not implemented. Unknown dimensions degrade to showcase
while retaining their authored binding for diagnostics. Missing parents become roots; cycles
are rejected. Parent chains deeper than 64 links are rejected before recursive ordering.
No dimension lookup requests new chunks.

Orbit supports `parent`, `eccentricity` (0 to less than 0.999), `inclination`,
`ascending_node`, `argument_of_periapsis`, `mean_anomaly`, and `epoch_seconds` (default zero).
Angles are radians, positions/radii are blocks, time is seconds. Position is parent-relative
and is used when real orbital motion is disabled. For inverse-square gravity, surface gravity
is converted to `mu = surface_ms2 * radius_blocks²`; linear/constant modes use it directly.
All numbers must be finite. Radius is in (0, 1000000], surface gravity in [0, 1000000], and
danger is an integer in [0, 10]. Altitudes and erasability are metadata for future consumers.

## Dimension YAML

Files live in `config/xenopixelsmod-space/dimensions/*.yml`. The filename is descriptive;
the explicit dimension field is authoritative, so dotted IDs and slash paths are unambiguous.

```yaml
dimension: minecraft:overworld
charted_as: xenopixelsmod:earth
overrides:
  exit_altitude: 800
  gravity:
    surface_ms2: 8.5
```

Or add a body for an existing dimension:

```yaml
dimension: example:pocket
implicit_body:
  id: example:pocket_body
  kind: pocket
  radius_blocks: 32
  position: {x: 5000, y: 200, z: 0}
```

Choose exactly one of `charted_as` and `implicit_body`. Namespaced IDs are required.
Duplicate dimensions/targets, duplicate YAML keys, custom object tags, collection aliases,
symlink files, files larger than 64 KiB, and nesting beyond 16 are rejected. At most 1024
datapack definitions and 1024 sidecars are read per reload. Malformed rows report their source
and are skipped; failed reload operations retain the previous live chart. Deleting a valid
definition or sidecar takes effect on the next successful reload.

## Commands and measurements

All commands require permission level 2, including console access:

```
/xenospace status
/xenospace list bodies
/xenospace list dimensions
/xenospace validate
/xenospace probe <x> <y> <z>
/xenospace reload
/xenospace reload dimensions
/xenospace perf
/xenospace perf reset
```

Full reload uses normal selected server resource packs and rereads global config. Dimension
reload reads YAML against the last successful datapack definitions. Validation builds a dry-run
candidate and leaves the active chart unchanged. Status reports enabled state, revision,
effective global config and diagnostics; body listing shows effective radius, exit altitude,
binding and enterability. Dimensions not referenced by the chart are labelled inert.

`list bodies` includes each body's evaluated `xyz` position in the space chart frame (Voidsea),
alongside its target dimension and metadata. These are chart coordinates, not surface-dimension
coordinates. `probe` previews one hypothetical observer at absolute **chart-space** coordinates, bounded
to ±30000000 on each axis. These are not player coordinates in a planet dimension. It uses
the current evaluated positions and effective settings, even when periodic evaluation is
disabled. It reports the proxy cap, predicted LOD stage counts, net mathematical acceleration,
contributors, and range culls. All bodies are treated as visible and the observer starts at
ABSENT; this is not a client frustum test or a continuation of another observer's hysteresis.
The command uses independent scratch and does not change the chart, its revision, live gravity
counters, or performance samples. ANCHOR/CHUNKS are distance predictions, not proof of
destination readiness, enterability, or loaded terrain. It creates no tickets or forces.

The LOD ladder now preserves simulation relevance off-screen, applies demotion hysteresis at
every boundary including CHUNKS, and enforces the proxy cap even for equal angular sizes.
Equal-sized candidates break ties by stable chart slot; strictly larger candidates win first.

Performance output measures **chart evaluation**, not actual ship gravity, chunk generation,
or total MSPT. It includes sample count, EMA, peak, and p50/p95/p99 of the last 2048 raw samples.
The tick path neither sorts samples nor formats output. Reset after warming the scenario.

For comparison, use a fresh dedicated server with fixed Java/hardware/mod list/world/player/
ship counts. Capture disabled, four-body and 50-body workloads with the same warm-up and
duration. Capture server profiler/MSPT separately. A passing unit test with its 10 ms ceiling
does not establish the concept's 1 ms runtime goal.

The YAML parser is privately relocated into both distributions. It adds no `META-INF/jarjar`
entry; the server retains only AAA Particles and its metadata there.

## Manual body placement groundwork (2026-10-13)

Permission level 2, logical server only:

```mcfunction
/xenospace body xenopixelsmod:earth
/xenospace body xenopixelsmod:earth move 5000 100 0
/xenospace body xenopixelsmod:earth resume
```

`body <id>` reads mode, evaluated position/velocity, dimension binding and `erasable` metadata.
`move` uses absolute chart-space coordinates, finite and within +/-30000000 per axis. It
temporarily decouples the body from its authored parent/orbit and sets chart velocity to zero.
Children that retain authored motion follow the moved parent; independently moved children
retain their absolute positions. `resume` restores the authored pinned/orbital behavior at
the current universal time, respecting the existing real-orbits configuration.

Changes invalidate evaluation even at the same timestamp. Gravity/LOD inspection reads the
moved position. Resource/dimension reload preserves manual placements by body ID; entries
removed from the new chart are not resurrected. Moves are **session-only**, reset after a
server restart, and are not written into authored resources or player/world save data.

This changes chart state only: no chunks, terrain, entities, dimension transfers, ship forces
are created. The subsequent visual-sync slice sends a bounded three-body client snapshot
through `xenopixelsmod:space_visual_snapshot_v1` on the separate addon channel.
Earth, Moon and Namek HD visuals in `xenospace_spike:orbit` now use the evaluated chart
positions and radii. Other chart IDs have no assigned artwork in this slice.
`erasable=false` remains metadata, not a newly implemented destruction hook. Physics entities,
wand input and persistence remain pending.

Visual snapshots are sent on login/dimension changes and chart revisions. With active live
orbits enabled, one snapshot per second supplies the evaluated positions; clients do not yet
interpolate or solve ephemerides between these samples. The render frustum still limits
distant body visibility. Nothing is sent/built on periodic sync when no orbit players exist.
Disconnect clears the client snapshot; resource reload clears GPU buffers while retaining
the latest received positions. The addon fingerprint changed: restart/update both server and
client together. Main `ModNetwork` numbering/protocol and public API signatures are unchanged.
