# Effekseer effects for Hakai, missiles and DMZ punches — design

**Status:** proposed 2026-09-28, awaiting owner review. **Visual summary:**
`~/Downloads/xenopixels-effekseer-fx-plan.html`.

## Goal

Hakai, missile explosions, missile thrusters and DMZ punch hits (player and NPC) play real
Effekseer effects (`.efkefc`) through the AAA Particles library. These effects **replace** today's
vanilla sparks, flames and clouds for those four uses. The look targets the source material: the
Dragon Ball Budokai Tenkaichi 3 style for punches and Hakai, and realistic fire and smoke for missiles.

## Owner decisions (2026-09-28)

1. **Effect files.** Stand-ins now, the owner's own effects later. Each effect is a named slot,
   and a new file with the same name replaces the stand-in with no code change.
2. **AAA Particles is required.** It must be installed on the server and on every client.
3. **Replacement.** New effects replace the vanilla particles for these uses; they are not layered
   on top.
4. **Server config.** A server config turns the effects on or off and limits how far they are sent.

## Library facts (verified in `tools/new_particles`, 2026-09-28)

- AAA Particles `aaa_particles-neoforge-1.21.1-2.3.1.jar`, MIT, mod id `aaa_particles`. It needs
  NeoForge 21.1.169 or later and Minecraft 1.21 to before 1.21.2, on both sides.
  CurseMaven: `curse.maven:aaa-particles-979809:8995061`. **Not verified:** that this file id is
  the same 2.3.1 jar.
- **Server API.** Build the request with `ParticleEmitterInfo.create(level, effekId[, emitterName])`,
  then chain `.position` / `.rotation` / `.rotationFromForward` / `.scale` / `.speed` /
  `.parameter(i, v)` / `.trigger(i)` / `.bindOnEntity` / `.useEntityVelocityAsRotation`. Send it with
  `AAALevel.addParticle(level, distance, info)`, which reaches players within `distance`.
- **Assets.** Effects load from `assets/<ns>/effeks/<path>.efkefc`. Textures, materials and models
  resolve relative to the effect file.
- **Stand-ins** are Effekseer 1.80.6 samples, all CC-0 (credit appreciated). They name their
  textures by file (for example the ring sample uses `Texture/Flame01.png`), so a texture can be
  swapped without the editor. The Hanmado sample needs HDR and is excluded.

## Effect slots

All slots live under `assets/xenopixelsmod/effeks/`.

| Slot | Plays | Hook | Placement | Stand-in |
| --- | --- | --- | --- | --- |
| `punch_impact` | Every DMZ melee hit that lands (player or NPC) | `CombatFx.impact` (combos, rushes, charge moves, BT3 packets); a new melee-damage hook for basic DMZ punches from players and native NPCs | Target mid-height, facing along the blow | `ToonHit` |
| `punch_heavy` | Finisher, charged, slam and ultimate hits | `CombatFx.impact` with HEAVY/ULTIMATE weight | Same, larger | `ToonHit`, scaled |
| `punch_guard` | A hit absorbed by guard | `CombatFx.impact` with GUARD weight | Same | `ToonHit`, tinted cyan through a texture swap |
| `hakai_channel` | While Hakai channels (player and NPC) | `HakaiFx.tick` | Bound to the target, grows with progress (parameter 0) | Ring sample with the owner's swirl texture |
| `hakai_erase` | The erase | `HakaiFx.burst` | Target centre, one shot | Same ring, faster and larger |
| `missile_explosion` | Tube missile detonation (every warhead) and ship missile arrival | `BallisticMissileEntity.detonate`, `ShipBallisticTicker.softArrive` | Impact point, scaled with blast power | `Simple_Turbulence_Fireworks` |
| `missile_thruster` | While the motor burns | Tube missiles: at boost start, bound to the missile and pointed along its velocity. Ships: re-played at the tail every few ticks. | Nozzle, opposite the flight direction; stopped at cutoff | `Simple_Track1` |

**Replaced vanilla particles** (they stay only as a fallback when an effect file fails to load):

| Use | Vanilla particles replaced |
| --- | --- |
| Punches | The `CombatFx` shockwave ring and sparks (`DmzHitParticles`) |
| Hakai | `HakaiFx` sparks |
| Missiles | The entity flame trail and detonation particles |
| Ships | Flame and smoke puffs; the cloud on arrival |

Sounds are unchanged.

**No double punches.** A target gets at most one punch effect per tick, so a combo hit that goes
through `CombatFx` and also through the damage hook plays once.

## Server config

The settings live in `XenoServerConfig`, under the existing server config file.

| Key | Default | Meaning |
| --- | --- | --- |
| `effekseerEnabled` | `true` | Master switch. Off means the vanilla particles return. |
| `effekseerPunches` / `effekseerHakai` / `effekseerMissiles` | `true` | Per-category switches |
| `effekseerRange` | `64` | Blocks: players farther away are not sent punch or Hakai effects |
| `effekseerMissileRange` | `256` | Blocks, for explosions and thrusters |
| `effekseerPunchesPerTick` | `24` | Server-wide cap on punch effects per tick; the rest are skipped |

## Components

- **`fx/effek/XenoEffects`** (server): `play(ServerLevel, Slot, Vec3 pos, Vec3 forward, float scale)`,
  plus bound variants. It checks the config, the range and the per-tick cap, then builds the AAA
  request. It is the single place that touches the library.
- **`fx/effek/Slot`**: an enum of the slots, each with its asset id, default scale, category and
  range.
- Hook changes in `CombatFx`, `HakaiFx`, the missile entity, `ShipBallisticTicker`, and one new
  melee-damage listener.
- Build: `implementation files('libs/aaa_particles-neoforge-1.21.1-2.3.1.jar')`, following the
  DragonMineZ local-jar precedent so offline builds work.
- `neoforge.mods.toml`: a required dependency `aaa_particles` `[2.3.1,)` on BOTH sides.

## Authoring guide for the owner's own effects

Save each effect under the same slot name.

- **Punches (BT3 style).** A short white-gold core flash (2–4 frames), radial spikes, and a thin
  expanding shockwave ring facing the camera. Total length about 0.3 s. `punch_heavy` adds a
  second ring and a brief screen-space distortion.
- **Hakai.** A dark-violet swirl that tightens around the target (your two swirl textures), with
  flecks drifting upward. Parameter 0 is channel progress, 0 to 1. The erase is a fast implosion,
  then an outward puff.
- **Missile explosion.** A bright flash, a fireball, then turbulent grey smoke, as in your
  reference. Sparks with gravity, about 2–4 s.
- **Missile thruster.** An exhaust pulse about 0.5 s long: a hot white core, an orange flame cone
  and a grey smoke trail. The game plays it again every 8 ticks while the motor burns, so it
  overlaps into a steady plume and stops on its own at motor cutoff.
- Keep textures at power-of-two sizes, 512 px or less, and avoid HDR-only materials.

## Testing

- Unit tests:
  - Slot ids resolve to files that exist in resources.
  - The config gates each category.
  - The per-tick cap.
  - Punch de-duplication per target per tick.
  - The range rule.
- Build: the jar builds, and `aaa_particles` is declared.
- **Not verifiable by me:** how effects look and render, which needs an in-game client with
  AAA Particles.

## Out of scope

- Authoring the final Budokai Tenkaichi 3 style effects (owner, in the Effekseer editor).
- DMZ aura effects.
- Ki attacks.
