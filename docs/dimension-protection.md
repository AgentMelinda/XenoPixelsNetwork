# Dimension protection and LinearReader conversion

**Version:** XenoPixels 0.5.10-1.21.1

**Checked:** 2026-10-05

## LinearReader conversion controls

The integration supports the exact **LinearReader 1.3.0 NeoForge 1.21.1–1.21.4** jar.
LinearReader is optional and is not bundled. Commands require operator level 2.

Disable new MCA-to-linear conversion in Otherworld:

```mcfunction
/xenolinear dimension dragonminez:otherworld false
/xenolinear dimension dragonminez:otherworld
```

Other commands:

```mcfunction
/xenolinear dimension dragonminez:otherworld true
/xenolinear dimension dragonminez:otherworld toggle
/xenolinear dimension dragonminez:otherworld reset
/xenolinear default false
/xenolinear enabled true
/xenolinear status
/xenolinear reload
```

`dimension true` allows new conversions; `false` blocks them. `reset` removes the override and
uses the default. `enabled false` disables XenoPixels' conversion restrictions. Changes save to
`config/xenopixelsmod-linearreader.json`, which can also be edited directly and reloaded:

```json
{
  "enabled": true,
  "defaultConversionAllowed": true,
  "dimensions": {
    "dragonminez:otherworld": false
  },
  "allowOutsideYawpClaims": false,
  "outsideYawpClaimsDimensions": {}
}
```

Since 0.5.10, the safe default blocks conversion outside verified local YAWP claims. This also
applies to older configs without `allowOutsideYawpClaims`. Configure this file
before starting a new world if conversion must be blocked from its first open. The policy covers
chunk (`region`), entity (`entities`), and POI (`poi`) storage, including vanilla dimensions and
custom dimension paths. It gates lazy and bulk/startup MCA conversion.

Existing `.linear` regions continue using LinearReader, including regions already open in its
cache but not yet flushed. New regions in disabled dimensions use native Anvil IO. Anvil regions
opened under the restriction retain that format for the server session, even if the policy is
later disabled or the dimension re-enabled. Restart to release those retained regions. A conversion
already executing when a command changes the policy is not interrupted.

This control does not export existing linear worlds back to MCA, delete files, or allow removing
LinearReader while linear data remains. Backups/exports use LinearReader's own documented tools.
Malformed config retains the old policy on reload; malformed startup config aborts startup.
An installed unsupported LinearReader version cannot use dimension restrictions. Verified-version
storage hooks are required: an injection mismatch aborts startup instead of silently ignoring the
policy.

### Outside local YAWP claims

```mcfunction
/xenolinear outside-claims false
/xenolinear dimension dragonminez:otherworld outside-claims false
/xenolinear dimension dragonminez:otherworld outside-claims reset
```

`false` keeps unclaimed region files MCA. `true` explicitly allows conversion outside claims;
`enabled false` also disables this restriction. Dimension-wide `dimension false` still blocks
conversion everywhere in that dimension, including inside claims.

A storage region contains 32 × 32 chunks (512 × 512 blocks horizontally). Conversion is allowed
only when **one active local cuboid contains the entire file footprint and full world build
height**. Partial overlap, partial vertical coverage, unions of smaller claims and non-cuboid
claims conservatively keep the whole file MCA. Dimensional/global YAWP regions do not count as
local claims. Existing linear files remain readable and are never automatically exported to MCA
or switched back and forth as claims change.

Coverage is verified against **YAWP 0.6.3-beta3 NeoForge**. Missing/unsupported YAWP, unavailable
dimension data and startup before claims are loaded keep restricted files MCA. Server-thread
snapshots are immutable for IO workers. Native claim removal, area edits, activation changes,
delete-all and dimension reset/untracking invalidate them before and after mutation. Ordinary
refreshes run once per second; an invalidated snapshot refreshes on the next server tick.

Startup bulk conversion may skip even claimed files until claims are ready. Lazy conversion of
unopened files can then proceed inside fully covered claims. Native opens and conversions share
a per-file lock, and files opened as MCA stay pinned for the session. A conversion already in
progress when a claim or configuration changes is allowed to finish. This feature prevents new
outside-claim conversion; it does not repair previously damaged data or guarantee unrelated IO
operations. No production world was migrated for validation.

## YAWP and ki block destruction

Use a YAWP jar matching NeoForge and Minecraft 1.21.1, with its required Forge Config API Port.
The verified fixture used **YAWP 0.6.3-beta3 NeoForge** and **Forge Config API Port 21.1.6**.
The Modrinth Maven artifact selected by this repository's compile-only dependency has Forge
metadata; it is not the fixture's runtime NeoForge jar.

Track and activate Otherworld, then set YAWP's ordinary player block rules:

```mcfunction
/yawp global track dragonminez:otherworld
/yawp dim dragonminez:otherworld state enable true
/yawp dim dragonminez:otherworld add flag break-blocks denied
/yawp dim dragonminez:otherworld add flag place-blocks denied
```

If a flag already exists, use `/yawp flag dim dragonminez:otherworld break-blocks state denied`
and its `place-blocks` counterpart. Owners/members and configured bypass permissions affect
YAWP player checks; test as an ordinary non-member. A higher-priority active local region can
also be responsible instead of the dimensional region.

XenoPixels ki flags are explicit, separate controls. They do not change ordinary block permissions
or cancel ki damage to entities. To deny **ki block destruction** by players and mobs:

```mcfunction
/kiflag enabled true
/execute in dragonminez:otherworld run kiflag set dragonminez:otherworld players denied
/execute in dragonminez:otherworld run kiflag set dragonminez:otherworld mobs denied
/execute in dragonminez:otherworld positioned 0 100 0 run kiflag here
/execute in dragonminez:otherworld positioned 0 100 0 run kiflag check
```

`allowed` restores the YAWP integration's permission to grief; DragonMineZ's own gamerules still
apply. `default`/`remove` clears an explicit flag and uses the configured YAWP mapping.
`/kiflag enabled false` disables only the ki integration and persists in
`config/xenopixelsmod-server.json`. Region states persist in the world's
`xenopixels/ki_region_flags.json`. `check` evaluates the exact DMZ ki block-destruction gate at the
command's dimension/position; a console source tests mobs, a player source tests players.

The server config's `yawpPlayerKiFlags` and `yawpMobKiFlags` remain empty by default, preserving
the existing owner decision. To explicitly map ordinary protection to ki, configure for example
`["break-blocks", "explosions-blocks"]` and reload the combat/server config. An explicit ki region
state takes precedence over that mapping. Local, dimensional, and global responsible-region
fallbacks use YAWP's native evaluator. Explicit ki flags use the affected dimension as their key,
including when the responsible region is global.

## Evidence and limits

Exact descriptors were checked with `javap` against the downloaded official jars. Fresh isolated
server runs exercised command persistence, dimensional ki evaluation and mixed storage formats.
See `ai/handoff-dimension-protection-2026-10-04.md` for hashes, final checks, and runtime observations.
The user's affected live server and physical player interactions remain unverified pending its
instance path, YAWP version, region settings, and testing identity.

Primary references: [YAWP source](https://github.com/Z0rdak/Yet-Another-World-Protector),
[YAWP global commands](https://z0rdak.github.io/yawp-docs/docs/commands/region/global-commands),
[LinearReader source](https://github.com/Bugfunbug/LinearReader),
[LinearReader 1.3.0 NeoForge artifact](https://modrinth.com/mod/linearreader/version/1g14WmZU).
