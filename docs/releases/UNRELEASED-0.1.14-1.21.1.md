# Superseded development draft — target 0.1.14-1.21.1

**Not a release.** Preserved as an earlier working-tree draft; its implementation claims and migration instructions are superseded by [Refactor and bug fixes](UNRELEASED-0.2.0-1.21.1.md). Do not use the older control/flight/persistence descriptions below as current behavior.

- **Status:** local working-tree development; not a tagged release
- **Base:** `v0.1.11`
- **Platform:** Minecraft 1.21.1 / NeoForge 21.1.238

These notes describe uncommitted development after `v0.1.11`. They become version-bound only after the work is committed and a release tag is created.

## Combat and controls

- Added Xeno-owned left/right punches based on DragonMineZ's native one-handed punch motion.
- Added server-authoritative BT3 combo routes, including `XXX`, `XXXX`, `XXK`, Cross, Uppercut, launchers, chase windows, and finishers.
- Added four Xeno rush strikes to DragonMineZ's native technique registry without automatically occupying technique slots.
- Changed rush activation to double-tap W and hold the second press, with repeating attacks while held.
- Added obstacle-aware chase routing and downward rush entry when the attacker is high above the target.
- Removed ordinary punch knockback while retaining authored kick, launcher, and finisher impacts.
- Moved Xeno fist/combo input to LMB and unbound vanilla Attack; held items and active ki weapons still use DragonMineZ's native attack path.
- Restored LMB block breaking/mining by giving a block under the crosshair priority over empty-hand Xeno combos.
- Added direct hold-Space chase while retaining dedicated Chase bindings and double-tap-then-hold W.
- Prevented DragonMineZ Search/Combat Fly movement from overwriting Xeno's server-authoritative chase velocity, while preserving and restoring the player's selected flight mode.
- Transferred DragonMineZ's Block key to Xeno Guard and unbound only the duplicate DMZ Block mapping.
- Kept Minecraft Use/Place on its existing key, allowing blocks and items to be used while guarding.
- Added Delete-key clearing for occupied DragonMineZ technique slots while in binding mode.

## HUD and debugging

- Updated Fist, Charge, and Combo HUD prompts to display `LMB` consistently.
- Added optional blue BT3 combat attack/hurt-box rendering.
- Added `/xenoclient set hitboxes true|false` for the client hitbox overlay.

## Compatibility

- Added persistent negative-effect snapshots for players and CustomNPCs across save/load, clone, respawn, and chunk reload lifecycles.
- Preserved DragonMineZ ki-weapon swing behavior under the new Xeno LMB ownership model.
- Added DragonMineZ strike unlock/select compatibility for Xeno-owned rush technique IDs.

## Hakai

- Hakai completion now performs unconditional erasure rather than falling back to ordinary stat-scaled damage.
- Players and CustomNPCs receive lethal generic-kill damage with a forced zero-health/death fallback; ordinary mobs are discarded.

## Tooling

- Added tag-range changelog generation with `tools/generate_changelog.py`.
- Added root and per-release changelog output.

## Notes

- Superseded: install the `0.2.0-1.21.1` build described in [Refactor and bug fixes](UNRELEASED-0.2.0-1.21.1.md), not a `0.1.14-1.21.1` build.
- Existing control profiles are migrated once: vanilla Attack and DMZ Block become unbound, while Xeno Fist and Guard inherit their intended mouse inputs.
