# XenoPixels API Example

This is a real, separately compiled NeoForge addon. It depends on the packaged XenoPixels jar and
does not share the main project's source set.

## Build and install

From the repository root:

```
./gradlew buildApiExampleAddon -PofflineMcMeta
./gradlew installApiExampleAddon -PofflineMcMeta
```

The install task copies only `xenopixels-api-addon-*.jar` into `run/mods`.

## Runtime verification

Start a fresh client after installation. The log must contain
`XenoPixels API example loaded against API version 1`.

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
