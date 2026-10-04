# XenoPixels API Example

This is a real, separately compiled NeoForge addon. It depends on the packaged XenoPixels jar and
does not share the main project's source set.

## Build and install

From the repository root:

```
./gradlew buildApiExampleAddon -PofflineMcMeta
./gradlew installApiExampleAddon -PofflineMcMeta
./gradlew runApiTestClient
```

The install task copies only `xenopixels-api-addon-*.jar` into `run/mods`.
`runApiTestClient` installs the addon and client-only development dependencies, then quick-joins
`New World (7)` so the login ping/pong and server-start patch path can be checked from a fresh log.

## Runtime verification

Start a fresh client after installation. The log must contain
`XenoPixels API example loaded against API version 1`.

On login the client sends `xenopixels_api_example:ping`; the server replies with
`xenopixels_api_example:pong`. Both sides log their observed packet counter.

Use `/xenoapitest` to display DMZ readiness, race/form reads, available form groups, and counters
for events observed through real XenoPixels hooks. The addon never reposts those events itself.

Exercise the corresponding gameplay feature for each counter:

- activate/deactivate Sparking and change its meter;
- start, land, and interrupt a BT3 cinematic rush;
- split and reunite clones;
- successfully dodge with Zanzoken;
- trigger a XenoPixels technique through a DragonMineZ strike slot;
- transform and return to base through DragonMineZ.

The sync helpers are exposed as `/xenoapitest sync_stats`, `sync_progression`, and
`sync_resources`. Use resource sync only after the player has received full DMZ data.

## XenoAPI probe

`/xenoapitest xenoapi` uses only the packaged `xenoapi.npcs.api` contracts: `NpcAPI.IsAvailable()`,
`NpcAPI.Instance().getIEntity(player)`, `IWorld.getClosestEntity(pos, 16, EntitiesType.NPC)`, and,
for the closest native NPC, `say`, a one-shot `ITimers.forceStart(9501, 20, false)` and a temporary
data write. It imports no XenoPixels internals. The addon also logs `XenoAPI available=` at
construction. See `docs/native-xenoapi-adapters.md` in the XenoPixels repository for the capability table.
