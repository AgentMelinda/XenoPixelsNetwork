# Xeno Ki Profiles (BT3-closer DMZ projectiles)

Scratch-owned presentation layer for Combat V3 Strike Ki. **Does not copy dmzrevamp.**
DragonMineZ projectiles remain the hitbox authority in `/xenokiattacks dmz`.

## Persistence

- File: `config/xenopixelsmod/ki_profiles.json`
- Created with archetype defaults on first load
- Reload: `/xenokiprofile reload` (refreshes owned DMZ copies)

## Commands

| Command | Effect |
|---------|--------|
| `/xenokiprofile status` | Counts techniques + archetypes |
| `/xenokiprofile reload` | Reload file + refresh owned `bt3_*` registry copies |
| `/xenokiprofile get <techniqueId>` | Print merged profile JSON |

## Maker Studio

`/xenomaker` → **Ki Profiles** (or `/xenomaker ki`): choose a technique. The **Stats**
page edits colors, size, speed, damage, armor penetration, offsets and `centerMuzzle`.
The **Effects** page edits the native render type and the charge, flight trail and impact
effect assets. Save a page before switching; saving one page preserves the other page's
values. Effect names are bundled `.efkefc` basenames such as `ki_charge_0000ff`,
`ki_wave_body_0000ff`, and `ki_impact_0000ff`. Blank fields inherit the normal visual.

### Access
| Context | Race / Hair / Taotto / Ki | Form Maker |
|---------|---------------------------|------------|
| Singleplayer | No OP | SP only (cheats not required) |
| Multiplayer / dedicated | OP level 2 | Blocked |

## Knobs (schema 1)

`colorInterior` / `colorExterior` / `colorOutline`, `size`, `speed`, `damageMultiplier`,
`armorPenetration`, `castOffsetX/Y/Z`, `centerMuzzle`, `fx.trail|impact|charge`, `nativeId`.

Resolve order: `type:nativeId` archetype → exact technique id.

## Modes

- **DMZ** — profile applied onto copied `KiAttackData` before `TechniqueDispatcher`
- **AAA / neweffects** — colors, size, speed, damage multiplier and the three optional
  bundled effect assets are applied by `V3KiStyle.of(technique)`; unsupported or missing
  effect names fall back to the normal color-selected asset. Native render type is sent
  through the existing Ki visual packet when that renderer is active. Armor penetration
  and cast offsets remain DMZ projectile settings.
