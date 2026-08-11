# Bundled fix: northstar moon blob placed features

## Why this mod ships `data/northstar/`

Dedicated servers running **northstar** fail to start during datapack load:

```
Unbound values in registry ResourceKey[minecraft:root / minecraft:worldgen/placed_feature]:
  [northstar:moon_andesite_blob, northstar:moon_basalt_blob, northstar:moon_blackstone_blob]
```

A northstar moon biome references those three `placed_feature` keys, but no JSON defines
them. The registry then cannot freeze, `RegistryDataLoader` aborts, and the server exits
before reaching world load. Nothing in XenoPixelsNetwork touches
`minecraft:worldgen/placed_feature` — this is a bug in northstar's shipped data.

northstar is missing **both** halves of these three features. Supplying only the
`placed_feature` definitions moved the identical error onto `configured_feature`, confirmed on
a live server. We therefore bundle both. Files:

```
src/main/resources/data/northstar/worldgen/placed_feature/moon_andesite_blob.json
src/main/resources/data/northstar/worldgen/placed_feature/moon_basalt_blob.json
src/main/resources/data/northstar/worldgen/placed_feature/moon_blackstone_blob.json
src/main/resources/data/northstar/worldgen/configured_feature/moon_andesite_blob.json
src/main/resources/data/northstar/worldgen/configured_feature/moon_basalt_blob.json
src/main/resources/data/northstar/worldgen/configured_feature/moon_blackstone_blob.json
```

## Where the definitions came from

These reproduce Redux's own definitions — they are not invented. Read from
`MoonPlacedFeatures.java` and `OreHelper.java` on the `1.21.1/dev` branch of
[Astronauts-of-Create/Northstar-Redux](https://github.com/Astronauts-of-Create/Northstar-Redux):

| Feature | Ore | Size | Count | Height range |
|---------|-----|------|-------|--------------|
| `moon_andesite_blob` | `minecraft:andesite` | 64 | 1 | absolute 0 → 72 |
| `moon_basalt_blob` | `minecraft:basalt` | 64 | 5 | bottom → absolute 8 |
| `moon_blackstone_blob` | `minecraft:blackstone` | 64 | 1 | bottom → absolute 8 |

All three replace the `northstar:base_stone_moon` tag with
`discard_chance_on_air_exposure` 0, and use the placement chain Redux's `uniformPlacement`
builds: `count` → `in_square` → `height_range` (uniform) → `biome`. The size of 64 comes from
`OreHelper.blobSize()`, which is `sized(64, 0)`.

## Why these ids do not match Redux's own

Redux 0.6.x registers these as **`northstar:blob_andesite`**, **`blob_basalt`** and
**`blob_blackstone`**. The `moon_*_blob` names supplied here are the **pre-0.6 names**.

That matters for diagnosis: Redux 0.6.3 is not what asks for the old ids. Something else in
the pack still references them — likely candidates are `northstar_structures`, `cnbridge`, or
a datapack or world folder left from an older Northstar. **Upgrading Redux to 0.6.4 will not
resolve it**, because the rename predates 0.6.3.

Worth tracking down the real referrer. Until then these definitions satisfy it with correct
terrain rather than a stub.

## Why not a mixin

The only interception point is `MappedRegistry.freeze`. Suppressing unbound holders there
applies to every registry and every mod, and converts a clear boot-time failure into an
unbound `Holder` that crashes later inside chunk generation, where it is far harder to trace.
Supplying the missing data is the supported fix and needs no bytecode.

## Assumptions and limits

- The feature parameters now match Redux's source, but were transcribed from the `1.21.1/dev`
  branch rather than from the exact 0.6.3 release tag; a later commit could have retuned them.
  They affect blob frequency and depth only, never whether the server starts.
- `northstar:base_stone_moon` is datagen'd rather than shipped as a tag JSON, so its id was
  read from the `NorthstarTags` enum. If the tag were ever renamed, these features would
  simply place nothing.

## Removal

Delete the three JSON files and this document once northstar ships the definitions.

Load-order caveat: if a future northstar version defines these keys itself, whichever mod
resource pack loads last wins, and mod pack ordering is not something we control. Leaving our
copies in place after upstream fixes the bug risks silently overriding northstar's real
placement values, so treat removal as required maintenance rather than optional cleanup.
