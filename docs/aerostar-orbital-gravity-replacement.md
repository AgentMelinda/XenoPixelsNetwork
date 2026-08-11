# Replacing AeroStar's orbital gravity

## The crash

Dedicated servers with Sable ships died every tick:

```
java.lang.NoClassDefFoundError: com/lightning/northstar/world/dimension/NorthstarDimensions
  at com.bega.aerostarcomp.physics.OrbitGravitySystem.onPrePhysicsTick(OrbitGravitySystem.java:44)
  at dev.ryanhcode.sable...SableEventPublishPlatformImpl.prePhysicsTick
  at dev.ryanhcode.sable...SubLevelPhysicsSystem.tickPipelinePhysics
```

`OrbitGravitySystem` runs inside Sable's pre-physics-tick event, so the throw kills the server
thread on the first tick any sub-level exists — an unrecoverable crash loop.

## Why it happens

Version pinning, not a code bug:

| Mod | Installed | What AeroStar expects |
|-----|-----------|-----------------------|
| AeroStar (`aerostarcomp`) | 1.0.1 — the newest release, 2026-07-08 | — |
| Northstar Redux | 0.6.3+1.21.1 | **0.5.4+1.21.1**, pinned by version id |

Redux shipped 0.6.0 through 0.6.4 after AeroStar 1.0.1, and moved
`com.lightning.northstar.world.dimension.NorthstarDimensions`. AeroStar was never updated for
the 0.6 line, and no compatible AeroStar build exists.

The same mismatch explains `docs/northstar-worldgen-fix.md`: 0.6 biome data references
`moon_*_blob` features whose definitions are missing.

## What we do instead

Rather than shim the missing class — which would break again on the next Redux release and
collide if anyone installed 0.5.4 — orbital gravity is reimplemented on our own stack.

- `aero/gravity/OrbitalGravityConfig.java` — `config/xenopixelsmod-orbital-gravity.json`,
  a plain map of **dimension id string → gravity in m/s²**. No space-mod classes are
  referenced anywhere, so Northstar can rename or repackage freely and server owners can add
  dimensions we have never heard of.
- `aero/gravity/OrbitalGravitySystem.java` — a Sable physics-tick pass (every 20 ticks) that
  sets each ship's target gravity from its dimension. The force itself is left to the existing
  `vs/ShipGravityControl.java`.
- `mixin/compat/aerostar/OrbitGravitySystemMixin.java` — cancels AeroStar's handler at HEAD so
  the two systems cannot fight and the crash cannot fire. Gated by `ConditionalMixinPlugin` on
  AeroStar being installed, and at runtime on the `overrideAeroStar` config flag.

### The mixin is not the crash fix — the shim is

Two mixin-only attempts both failed, across crash reports at 18:07, 18:11 and 18:16:

1. `require = 0` with a handler taking only `CallbackInfo`. An `@Inject` handler must repeat
   the target's parameters before the `CallbackInfo`, so against an event consumer that
   signature matches nothing. `require = 0` swallowed the non-match **silently** — and the
   crash report still listed the mixin as applied to the class, because class-level
   application and injector matching are different things.
2. `require = 1` with `(SablePrePhysicsTickEvent, CallbackInfo)`. The server still booted and
   still crashed at the same line, which means the injector matched something other than the
   method actually being invoked.

Both depend on guessing a method shape inside a jar we have never been able to inspect. The
crash is therefore fixed elsewhere — see the shim below — and the mixin is demoted to an
optimization with `require = 0`: if it matches, we prefer our config-tunable gravity; if it
does not, AeroStar simply keeps its own, which now works.

## The `NorthstarDimensions` shim

`src/main/java/com/lightning/northstar/world/dimension/NorthstarDimensions.java` supplies the
class at the package path AeroStar expects, resolving the `NoClassDefFoundError` at its source
regardless of how AeroStar structures its listeners.

This is safe to reproduce exactly because the original contains **only** `ResourceKey` and
`ResourceLocation` constants over the `northstar` namespace, plus a no-op `register()` —
nothing northstar-internal. It was taken from Redux commit `4f430cd1`; that file was last
modified 2025-09-29 and untouched until the 2026-05-21 "Data-driven planets" move, so it is
exactly what shipped in the 0.5.4 release AeroStar pins. Keys are built with
`ResourceLocation.fromNamespaceAndPath` rather than northstar's `asResource` helper, so the
shim depends only on vanilla types.

**Delete the shim when** AeroStar ships a build supporting Redux 0.6+, **or** Redux restores a
class at that package path.

**Known hazard:** installing Redux **0.5.4** alongside this mod puts two classes at the same
fully-qualified name, since 0.5.4 still provides it. On the 0.6 line there is no conflict.

### Only one system applies gravity

With the shim in place AeroStar's gravity works again, so both it and ours could apply
corrections and fight. `OrbitalGravitySystem.aeroStarOwnsGravity()` resolves this: if AeroStar
is installed and `AeroStarState.hasCancelledHandler()` has never become true, our pass yields
and logs once.

The decision is keyed on an **observed cancel**, not on whether the mixin is present — the
crash reports established that presence is no evidence at all.

Everything else AeroStar does — the dimensional drive, ship save/restore across dimensions,
super glue joint fixes — is untouched. Only its gravity handler is disabled.

### Manual overrides are respected

`OrbitalGravitySystem` records the last value it wrote per sub-level and only overwrites a
value it set itself, or one still at the `ShipGravityControl.NORMAL_GRAVITY` baseline. A
gravity set by hand through the ship gravity command therefore sticks.

## Defaults

Real surface gravity, as a starting point to tune:

| Dimension | m/s² |
|-----------|------|
| `minecraft:overworld` | 10.0 |
| `northstar:moon` | 1.62 |
| `northstar:mars` | 3.72 |
| `northstar:venus` | 8.87 |
| `northstar:mercury` | 3.70 |
| `northstar:earth_orbit` | 0.0 |

**The dimension ids are unverified** — they were not read from a Northstar Redux 0.6.3 jar.
Check them against `/execute in <tab-complete>` on the server and correct the config if any
differ. An unlisted dimension is simply left at the baseline, so a wrong id means "no orbital
gravity there", never a crash.

## If AeroStar updates

Should a future AeroStar support Redux 0.6+, set `overrideAeroStar` to `false` to hand gravity
back to it, or delete the mixin entry from `xenopixelsmod.compat.mixins.json`.
