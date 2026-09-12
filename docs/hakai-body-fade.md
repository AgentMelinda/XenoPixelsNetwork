# Hakai body fade — how the victim's body turns transparent

Updated: 2026-09-12 · Target: MC 1.21.1 / NeoForge 21.1.248 / mod id `xenopixelsmod`

The owner asked for the Hakai victim's body to fade out gradually with the charge, the way a
Zanzoken user's body does. This documents what is verified, what was fixed, and what is still
open. Claims carry an evidence class: **repo source** → **tracked decompiled reference** →
**`javap` against the exact jar** → **dev run**.

## What the fade is

A body renders at an alpha taken from the hidden `hakai_dissolve` effect's amplifier:

```
HakaiFade.alphaFor(progress, minAlpha, curve) = max(minAlpha, 1 - progress ^ curve)
```

With the defaults (`minAlpha 0.02`, `curve 1.0`) that is exactly the original ramp
`max(0.02, 1 - amp/255)`:

- amplifier `0` → alpha `1.0` (solid)
- amplifier `128` → alpha `~0.498`
- amplifier `255` → alpha `0.02` (nearly gone, never fully invisible)

`NpcDissolve.apply(target, progress)` writes `round(progress * 255)` into that amplifier every
channel tick while `HakaiChannelSystem` channels (dust / silhouette particles stay on every
second tick). The number is charge-proportional on the server; the defect was that it never
reached the caster's client (next section).

## Root cause — the effect was the wrong client signal (2026-09-12)

The dissolve amplifier is now sent as `HakaiFadePacket` via
`ModNetwork.sendToTrackingAndSelf` (protocol 66), the same pattern Sparking already
used when vanilla effect sync proved unreliable. The hidden effect is still written as a
fallback. Drawing reads the packet map first.

Cutout shaders discard vertex alpha, so a fade wrap that leaves `entityCutout*` in
place is invisible even when the amplifier arrives. `LivingEntityRenderer.getRenderType`
and `DMZPlayerRenderer.getRenderType` now return `entityTranslucent` on the **body**
pass while a body is fading — the same trick `XenoCloneRenderer` already used for
Zanzoken copies. When `glowing == true` the mixin returns and leaves vanilla's outline
type alone, so stencil glow and fade can run together.

## Earlier note — the effect was never sent to tracking clients (`javap`, 2026-09-12)

Against `build/moddev/artifacts/neoforge-21.1.248-merged.jar`:

- `LivingEntity.onEffectAdded`, `onEffectUpdated` and `onEffectRemoved` only call
  `sendEffectToPassengers`. Nothing goes to the players tracking the entity.
- Only `ServerPlayer` overrides those hooks to send `ClientboundUpdateMobEffectPacket` /
  `ClientboundRemoveMobEffectPacket` — and only to **itself**.
- `ServerEntity.sendPairingData` sends a `LivingEntity`'s current effects once, when a player
  starts tracking it. Later changes are not forwarded.

So for a vanilla mob or a CustomNPC target, `hakai_dissolve` existed only on the server. The
caster's client had no instance, `HakaiFade.dissolving` was false, no render wrap was applied,
and the body stayed solid regardless of what `AlphaMultiBufferSource` did. Every render-path
investigation above and below this point was downstream of that.

Second, `LivingEntity.addEffect` merges through `MobEffectInstance.update`, which only ever
**raises** an amplifier. The restore ramp in `HakaiChannelSystem.tickRestore` calls
`NpcDissolve.apply` with a falling progress, so even where the effect was visible it could not
fade back in until the 10-tick duration expired.

`NpcDissolve` now:

- writes the instance with `LivingEntity.forceAddEffect(instance, null)` (public; replaces the
  instance outright so the amplifier can go down);
- broadcasts `new ClientboundUpdateMobEffectPacket(target.getId(), instance, false)` with
  `ServerChunkCache.broadcast(target, packet)`, which reaches every tracker and excludes the entity
  itself — a player target already self-sends, so it is not sent twice;
- in `clear`, broadcasts `new ClientboundRemoveMobEffectPacket(target.getId(), HAKAI_DISSOLVE)`
  only when `removeEffect` returned true.

On the client, `LivingEntity.tickEffects` counts a duration down but never removes an expired
instance (only the server removes). `HakaiFade` therefore treats an instance with
`!isInfiniteDuration() && getDuration() <= 0` as gone, so a missed remove packet cannot leave a
body faded.

All four signatures (`forceAddEffect(MobEffectInstance, Entity)`, `removeEffect(Holder)`,
`ServerChunkCache.broadcast(Entity, Packet)`, both packet constructors) verified with `javap`
against the exact jar. **Whether the fade renders in game after this change is not verified** —
no fresh client run was made on 2026-09-12; the user tests the packaged jar.

The exponent is on the **charge** fraction, not its complement. `curve > 1` therefore holds the
body opaque longer and fades it late; `curve < 1` front-loads the fade. Exponentiating
`(1 - progress)` instead inverts that meaning.

## Configurable surface (repo source, 2026-09-12)

| Key | Default | Range | Alias | Effect |
|---|---|---|---|---|
| `hakaiFadeEnabled` | `true` | bool | `hakaifade` | Master off-switch; `false` renders every body at `1.0` |
| `hakaiFadeMinAlpha` | `0.02` | `0.0`..`1.0` | `hakaifademin` | Floor. `0.0` lets the body reach fully invisible |
| `hakaiFadeCurve` | `1.0` | `0.25`..`4.0` | `hakaifadecurve` | Ramp exponent; `1.0` is linear |
| `hakaiFadeRestoreTicks` | `40` | `0`..`6000` | `hakaifaderestore` | Ticks to fade back to solid; `0` clears at once |
| `hakaiFadeSpeed` | `1.0` | `0.25`..`4.0` | `hakaifadespeed` | Wipe versus channel. `2` finishes the ghost at half charge |
| `hakaiFadeBand` | `0.30` | `0.04`..`0.5` | `hakaifadeband` | Soft height of the dissolve line |
| `hakaiFxColor` | `0xF233F2` | packed RGB | `hakaicolor` | Dust fill. `/xenoset hakaicolor 0xFF00AA` |
| `hakaiFxRimColor` | `0xFF73FF` | packed RGB | `hakairim` | Outline / sparks |
| `hakaiFxEnabled` | `true` | bool | `hakaifx` | Master off-switch for dust + silhouette particles |
| `hakaiDustEnabled` | `true` | bool | `hakaidust` | Dust / sparks / caster aura |
| `hakaiSilhouetteEnabled` | `true` | bool | `hakaisilhouette` | Particle silhouette fill |
| `hakaiSilhouetteColor` | `0xF233F2` | packed RGB | `hakaisilhouettecolor` | Silhouette fill colour |
| `hakaiGlowColor` | `0xFF73FF` | packed RGB | `hakaiglowcolor` | Target outline colour. `/xenoset hakaiglowcolor 0xAA00FF` |

`hakaiFadeEnabled` is also honoured by `CombatBodyFade.alpha`, which returns `1.0` for a
disabled fade rather than only relying on `HakaiFade.dissolving`.

The body wipe is head-first, feet-last: `localY` is 0 at the feet and 1 at the head.
`CombatBodyFade.wrapHakai` rotates each camera-space vertex by the camera pose and
scales alpha from **world Y**, so a pig or cow still wipes top-to-bottom when the
camera is looking down. Zanzoken stays a uniform multiplier. Client amplifiers lerp
across the one-tick gap between packets. Particle `keepBelow` uses the same
`charged(progress, speed, curve)` sweep. Runtime wipe/colour is **not verified** in a
client run.

## Verified — the amplifier is not truncated on the wire (`javap`, 2026-09-12)

Against the resolved 1.21.1 merged jar:

- `MobEffectInstance.MIN_AMPLIFIER = 0`, `MAX_AMPLIFIER = 255` — the full 0..255 range is legal.
- `ClientboundUpdateMobEffectPacket` writes `effectAmplifier` with **`writeVarInt`** and reads it
  with **`readVarInt`** (not `writeByte`). So an amplifier above 127 is **not** truncated and does
  **not** arrive negative.

A signed-byte truncation was the leading suspect for "the fade snaps back to solid" and is
**refuted** by the above. Note this only says the packet *would* carry the value intact; before
the root-cause fix the packet was never sent for non-player targets at all.

## Fixed — the curve was non-monotonic (repo source, 2026-09-12)

`HakaiFade.alphaFromAmplifier` applied its `0.02` floor only when the raw value hit exactly zero.
Amplifier `254` produces `1/255 ≈ 0.0039`, which is below the floor and was returned as-is, so
amplifier `255` then stepped back **up** to `0.02`. The fade got more opaque on its last tick.

The floor is now a true `max`:

```java
return Math.max(0.02f, Math.min(1.0f, 1.0f - amp / 255.0f));
```

The curve is now monotonic non-increasing across 0..255. Covered by `HakaiFadeTest`:
`alphaDecreasesMonotonicallyAcrossCharge`, `amplifierBelowRangeClampsToSolid`,
`amplifierAboveRangeClampsToFloor`, plus the three existing endpoint tests.

## Which render path wraps which body

| Target | Path | Status |
|---|---|---|
| Any living mob | `EntityRenderDispatcherHakaiFadeMixin` wraps `EntityRenderDispatcher#render` | GeckoLib / other-mod renderers that never enter `LivingEntityRenderer` |
| DMZ player | `DmzZanzokenPlayerFadeMixin` wraps `DMZPlayerRenderer#render` | shares the working Zanzoken wrap; skips outline `getRenderType` |
| CustomNPC | `NpcFullDmzRenderer.renderPlayerCopy` wraps via `CombatBodyFade.wrap(..., HakaiFade.alpha(owner))` | repo source |
| Vanilla mob | `LivingHakaiFadeMixin` wraps `LivingEntityRenderer#render` | body translucent only; glow pass left alone |

`CombatBodyFade.isWrapped` keeps those paths from stacking a second alpha wrapper.
`HakaiFadePacket` already goes to tracking + self. Other players **with this mod**
see the fade. Glow is vanilla `setGlowingTag`, so they see the outline even without
the mod once the body mixin stops stealing the outline pass.

`LivingHakaiFadeMixin`'s old `entity instanceof Player` branch was **dead**: `DMZPlayerRenderer`
is a GeckoLib `GeoEntityRenderer`, not a `LivingEntityRenderer`, so a DMZ player never reaches
`LivingEntityRenderer#render`. The branch only ever served a vanilla `PlayerRenderer`, which this
mod does not use. It has been removed; the mob branch is the real payload.

## Verified — body-layer vertex formats (`javap`, 2026-09-12)

`AlphaMultiBufferSource.createTranslucent` only substitutes a translucent `RenderType` when the
original's vertex format is `DefaultVertexFormat.NEW_ENTITY`; any other format is returned
unchanged and the wrapped alpha is discarded. Every render type a DMZ/GeckoLib body layer actually
uses resolves to `NEW_ENTITY`, so the gate admits all of them. Disassembled against the resolved
1.21.1 merged jar:

- `RenderType.entityCutoutNoCull` and `entityCutoutNoCullZOffset` — `NEW_ENTITY`. This is the
  GeckoLib `GeoModel.getRenderType` default, so it is the type the body model itself draws with.
- `RenderType.entityTranslucent`, `entityTranslucentCull`, `entityTranslucentEmissive` —
  `NEW_ENTITY`.
- `RenderType.armorCutoutNoCull` (private `createArmorCutoutNoCull`) — `NEW_ENTITY`.

The reflection `createTranslucent` relies on was checked at the same time and is sound on this
build:

| Reflected path | Field | Exists |
|---|---|---|
| `CompositeRenderType` → `state` | private `CompositeState state` | yes |
| `CompositeState` → `textureState` | package-private `EmptyTextureStateShard textureState` | yes |
| `TextureStateShard` → `texture` | private `Optional<ResourceLocation> texture` | yes |

**Consequence.** The format gate is not the defect. If a fade is missing on a path that otherwise
looks wired, the cause is elsewhere — the effect reaching the client, the wrap being applied, or
the mixin firing — not `supportsTranslucentRemap`.

## Fixed — the fade-back is configurable and survives a caster leaving

`beginRestore` hard-coded 40 steps and a pending restore was only ever advanced by its caster's
own tick. If the caster disconnected or the server stopped mid-ramp, the restore record was
dropped and the `hakai_dissolve` effect was left to expire on its own 10-tick duration — so the
body continued fading **out** instead of fading back. Both paths now call `dropRestore`, which
reveals the target and clears the effect. `hakaiFadeRestoreTicks = 0` skips the ramp entirely and
clears immediately.

## Fixed — OVERLAY-mode CustomNPCs were never faded

`LivingHakaiFadeMixin` skipped every CustomNPC. That is only correct for FULL-appearance NPCs,
which are faded inside `NpcFullDmzRenderer.renderPlayerCopy`; an OVERLAY NPC never reaches that
method, so nothing wrapped its buffer and the body never faded. The exclusion is now
`NpcCounterpartSync.isCustomNpc(entity) && NpcFullDmzRenderer.isFull(entity)` — only the FULL
path is skipped, because that path fades itself.

**Not verified:** whether CNPC's renderer extends `LivingEntityRenderer` on this build, which is
what makes an OVERLAY NPC reach this mixin at all. A dev run settles it.

## Fixed — a failed translucent remap was silent

`AlphaMultiBufferSource.createTranslucent` returns the original type when its reflection cannot
resolve the texture. A cutout type discards fractional alpha, so the fade becomes an invisible
no-op with no signal. It now logs a one-time warning per distinct render-type class. The identity
cache keeps this off the per-frame path.

## Fixed — Hakai glow skipped the whole fade wrap (R5)

When `hakaiTargetGlow` is on, the victim is drawn through `OutlineBufferSource`. The old
`supportsAlphaFade` guard returned false for that type, so **no alpha wrap ran** and the body
stayed opaque while Zanzoken (non-outline source) still faded.

`OutlineBufferSource.getBuffer` (NeoForge 21.1.248 / MC 1.21.1, extracted source):

- outline types → `EntityOutlineGenerator` only
- otherwise → `VertexMultiConsumer.create(outlineGenerator, body)` when the type has an outline

Remapping the type into the outline builder caused `IllegalStateException: Not building!`.
`EntityOutlineGenerator.setColor` is a no-op (team colour is stamped in `addVertex`).

R5 kept wrapping on outline sources but still asked `OutlineBufferSource.getBuffer(original)`.
Vanilla then does `bufferSource.getBuffer(original)` — the cutout type. Cutout shaders discard
fractional alpha, so `setColor(..., scaledAlpha)` never reached the framebuffer. That is **R6**.

## Fixed — glow body must remap through the inner `bufferSource` (R6)

Verified from extracted `OutlineBufferSource` (NeoForge 21.1.248 / MC 1.21.1): field
`bufferSource` is the real body `MultiBufferSource.BufferSource`. Remapping a type *through*
`OutlineBufferSource.getBuffer` caused `IllegalStateException: Not building!`.

The wrap now:

- leaves `isOutline()` types unwrapped (generator stamps team colour)
- fetches the body from the inner `bufferSource` as `entityTranslucent`, then alpha-wraps it
- rejoins that faded body with the unfaded outline generator

If the inner field cannot be read, it falls back to the cutout join body and logs once.

## Fixed — glow and fade on the same target (2026-09-12)

Vanilla mobs faded but lost glow because `LivingHakaiFadeMixin.getRenderType` always
returned `entityTranslucent`, including when `glowing == true`. That replaced Minecraft's
outline pass, so the stencil never drew. The inject now returns when `glowing` is true.

Other-mod / Geo mobs glowed but never faded: their renderers override
`EntityRenderer.render` and never enter `LivingEntityRenderer`.
`EntityRenderDispatcherHakaiFadeMixin` wraps every dissolving `LivingEntity` at the
dispatcher. `fadeOutlineBodyOnly` already leaves `isOutline()` types unwrapped.

Looking down at a short pig/cow used to wipe from the waist because camera-space Y was
treated as world up. The wipe now uses `worldY = cameraPos.y + rotate(cameraSpace).y`.

In-game glow+fade on zombie / pig / other-mod mob remains **not verified** until a
fresh client run.

## Cleanup (2026-09-12)

`AlphaMultiBufferSource` no longer carries `isOutlineGenerator`, `bodyConsumer`,
`outlineConsumer`, `dualField` or the always-true `supportsAlphaFade`; they were left over from R5
and referenced only by tests. `scalePacked` now has direct unit coverage (alpha byte scaled, RGB
untouched, multiplier clamped, alpha 1 identity).

## Still not verified

- **Whether the fade actually renders in game.** A build and unit tests are not proof an effect
  synced and a wrap applied. This needs a dev run: channel Hakai on each target kind (player,
  FULL CustomNPC, OVERLAY CustomNPC, mob) and watch the body. This is the item that would confirm
  or reopen the report. The root-cause fix above makes the effect reach the client; what the
  renderer then does with it is still an inference from code.
- **Whether an OVERLAY CustomNPC reaches `LivingEntityRenderer#render`.** The mixin now stops
  excluding it, but that only helps if CNPC's renderer is a `LivingEntityRenderer` subclass on
  this build. Check by `javap` against the resolved CustomNPCs jar, then in a dev run.
- **Whether cancelling mid-channel fades back smoothly.** `dropRestore` and the configured
  `hakaiFadeRestoreTicks` are repo-source only; watch an interrupted cast.
- **Whether the DMZ player path fades at all.** It uses the same `CombatBodyFade.wrap` /
  `AlphaMultiBufferSource` that Zanzoken uses successfully, so it is *expected* to work, but that
  is an inference from shared code, not an observation.
- **Vertex-format gate.** Resolved — see "Verified — body-layer vertex formats" above. No
  DMZ/GeckoLib body layer uses a non-`NEW_ENTITY` format, so the remap gate does not drop any.