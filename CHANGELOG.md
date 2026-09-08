# Changelog

Version-bound history for commits reachable from [`origin/1.21.1`](https://github.com/AgentMelinda/XenoPixelsNetwork/tree/1.21.1). Detailed pages preserve exact Git ranges and commit subjects; summaries are based on repository diffs rather than commit titles alone.

## History notes

- `v0.0.5` through `v0.1.6` are the **Minecraft 1.20.1 / Forge** history inherited by the current branch.
- `v0.1.8-1.21.1` aliases `v0.1.6`; no `v0.1.7` tag exists, and the alias still contains the `0.1.6-1.20.1` build.
- The actual **NeoForge 1.21.1 / Sable port** is commit `aeb771d` in the `v0.1.9` range.
- Untagged local work is not attributed to a released version.

## v0.2.3-1.21.1 — 2026-09-08 — BT3 controls, scripting and saga recovery

**Verification status:** JUnit suite **330 tests across 61 classes, zero failures/errors**. The
client reached an integrated world with Controlify and YetAnotherConfigLib installed, registered all
42 Xeno gamepad bindings, and detected an Xbox controller. The new common and optional Controlify
mixins applied without injection errors. A dedicated server also reached
`Done` after the client-only pad jars were removed. In-world button-by-button combat and building
acceptance still requires a player session. Pink aura streaking during Multi-Form remains unresolved.

### Fixed

- DragonMineZ saga combat quests now audit their quest-spawned enemies after both normal start and resummon. Missing enemies are recreated with DMZ-compatible ownership, objective, party-scaling, difficulty, AI, health, damage and transformation metadata without duplicating valid spawns.
- CustomNPCs quest rewards using `xenopoints add 5000 {RefPlayer}` now work on dedicated servers. The compatibility hook narrowly maps `{RefPlayer}` to CustomNPCs' verified completing-player token before its existing command dispatch; unrelated commands and valid selectors remain unchanged.
- XenoPixels status effects now render in their own vertical rail to the right of the player inventory instead of sharing the vanilla effect list or colliding with inventory tabs. The rail wraps into additional columns on short screens and retains hover names, levels, and durations; other mods' effects remain unchanged.
- Xeno Rush Left, Right, Breaker, and Finisher are unlocked without being automatically inserted into DragonMineZ's Alt/Ctrl technique slots; manually unbound slots now remain empty across login and reload, including when the legacy auto-equip config was enabled.
- Controlify arbitration now suppresses only physical inputs owned by BT3 mode instead of blanket-blocking built-in actions; normal movement/camera values remain available. Guard now owns sneak suppression, and LT+Y exclusively triggers charged kick.
- Multi-Form clones now mirror successful DragonMineZ ki-wave releases on the same server tick using the real technique data and charge multiplier; obsolete melee-charge packets and autonomous charged-wave behavior were removed.
- Replacing a Zanzoken ring now disperses the previous ring, and the player's ring landing searches for an open collision-safe slot before falling back to the current position.
- Fixed a client crash at the title screen. `resetCharge()` sent the clone ki-charge sync packet
  without checking for a connection, from the client-tick branch that runs every tick while no
  world is loaded; `PacketDistributor.sendToServer` rejects a null connection. **This crash is
  present in the code tagged `v0.2.1`,** which was never pushed. Every packet send in
  `Bt3CombatClient` now goes through one connection-guarded method, so a new call site cannot
  reintroduce it, and the charge sync no longer fires when there was no charge to clear.

### Added

- Adds `/xenodmz saga diagnose [player]` and `/xenodmz saga respawn [player]` for operator-visible saga spawn diagnostics and duplicate-safe manual recovery.
- Adds `/xenopoints <add|set|remove> <amount> <targets>` as a command-block and CustomNPC quest friendly counterpart to DragonMineZ training points. For example, `/xenopoints add 500 @p` updates the nearest player's real DMZ points and synchronizes the resource HUD.
- Adds `/xenoki clear all` and `/xenoki clear radius <blocks>` for operators to remove loaded
  DragonMineZ KI attacks and orphaned explosion visuals globally or around the command source.
- Gamepad support through [Controlify](https://modrinth.com/mod/controlify), laid out to match
  Budokai Tenkaichi 3 and written in Xbox button names: melee, ki blast, dash and guard on the
  face buttons, ki charge and lock-on on the left trigger and bumper, fly and descend on the right
  pair, transform on the right stick, and chase/backstep/Sonic Sway on the remaining chords and
  d-pad inputs. Guard plus a left/right stick flick directly drives Xeno's existing side vanish.
- Adds persistent Normal and BT3 controller modes as Controlify radial candidates. Normal mode
  restores unmodified Minecraft mining, placing, inventory and hotbar controls; BT3 mode filters
  Controlify's overlapping Xbox defaults while retaining Pause and the radial menu.
- Left-stick click toggles DragonMineZ Search Fly and Combat Fly. Right bumper activates flight
  when needed and becomes ascend while flight is active; right trigger descends without attacking.
- Chorded moves, as in the original: hold ki charge for Z Burst, Ultimate and Sparking; hold
  lock-on for Zanzoken, Multi-Form and Hakai. Holding a modifier withholds the plain move, so one
  button never fires two.
- Controller state is read from Controlify's verified raw current/previous state for BT3 action detection and analogue flight axes. Existing DragonMineZ key mappings remain the compatibility bridge for actions that expose no direct public invocation API.
- The eight DragonMineZ technique slots are offered to Controlify's radial menu. They are unbound
  by default: their keyboard bindings are Alt+1-4 and Ctrl+1-4, chords no gamepad can produce, and
  a radial keeps working if the number of slots grows.
- The pilot seat reads the left stick as a real analogue stick, assigning its position rather than
  running it through the key ramp, and hands control back to the keyboard on release.
- Controlify is optional. Everything touching it lives in `client/pad` and is reached only through
  Controlify's own `ServiceLoader` entrypoint, so with Controlify absent none of those classes is
  loaded and input behaves exactly as before. A `padEnabled` client config flag switches the layer
  off without uninstalling anything.

See [`docs/releases/v0.2.3-1.21.1.md`](docs/releases/v0.2.3-1.21.1.md) for release scope and verification.

## v0.2.1 — 2026-09-07 — Refactor, bug fixes and techniques

### Refactor and bug fixes

First `0.2.x` release. **There was no 0.2.0** — this work carried a `0.2.0` working version but
was never tagged, so it ships as `v0.2.1`.

**Verification status:** fresh existing JUnit suite: **303 tests across 53 classes, zero failures/errors**.
Dependency APIs were checked against DragonMineZ 2.1.3 and mapped NeoForge artifacts. An isolated
dedicated server reached `Done` and answered a status ping, with compatibility warnings; no
player-combat or graphical client acceptance was performed. Pink aura streaking remains
unresolved. See the [validation and staged review ledger](wiki/Techniques-Approved-Plan-Validation.md)
and [continued handoff](CLAUDE_TECHNIQUES_HANDOFF_2026-09-06.md).

- Retains the pending DMZ-faithful punches, combos, custom rush strikes, obstacle-aware chase, Hakai erasure, HUD and compatibility work.
- Separates native weapon/mining input from Xeno fists, preserving configured Attack and disabled-feature fallback.
- Separates "Xeno fists run" from "the native attack is suppressed", so a block under the crosshair no longer switches fists or a charge in progress off, and punching, holding a charge and guarding all leave block breaking intact.
- Unlocks the four custom rush strikes for every player and answers a rush attempted at a target still on the ground.
- Softens the rush and chase camera to a single gentle writer with a deadzone, so it tracks without oscillating or snapping.
- Fades the rush and chase aim assist to nothing inside contact range, where tracking angles diverge and the camera thrashed.
- Fixes Multi-Form + lock-on crash: NpcGeckoAnim.playAttack now guards against non-NPC entities before reflective field access.
- Fixes clone facing: when no target is locked, clones face outward from formation center instead of all staring at the owner's yaw.
- Adds Multi-Form ki-wave synchronization through DragonMineZ's actual server-side attack lifecycle; clones fire on the owner's successful wave release with the same technique tuning and charge multiplier.
- Fixes a chase started from directly above its target climbing away instead of diving onto it.
- Retires the hold-Space and hold-W chase gestures (off by default, still switchable); chase runs from its own binding.
- Holding W through a combo beat launches the enemy and dashes after them on Search Fly, skipping the success roll.
- Resets fall distance and waives fall damage briefly when a chase ends, so ending one at altitude is survivable.
- Gives Xeno rush strikes their own configurable ki cost and cooldown instead of DragonMineZ's power-scaled thousands.
- Ki guidance follows the crosshair rather than auto-acquiring a nearby target; a deliberate lock-on still homes.
- Adds a working Unbind for techniques bound to DragonMineZ slots, which DMZ's own empty-slot path cannot do.
- Adds Zanzoken, the afterimage dodge: a timed read that cancels the hit and rings the attacker with copies of you. You stand in the ring yourself and their lock-on is redirected onto an image, so neither position nor the lock marker identifies the real body; striking any image disperses the ring. Fools players, not NPC AI. Ships unbound.
- Adds Shi Shin No Ken with current-health-conserving split/recall, original collision-aware local pursuit, melee/basic ki/unlocked DMZ strike combat, shared owner resources, protected lock-on targets, and exactly-once power division. Positive split combat awards rate-limited mastery up to 1000; mastery survives save/respawn. Ships unbound; live combat acceptance remains pending.
- Repairs copy refresh timing, owner armor and queued animations, distinct render identity/cache cleanup, and exception-path pose isolation without claiming to resolve shader streaks.
- Makes chase reliable by default (also Dragon Dash's shared roll), preserves explicitly saved randomness/pay-on-attempt, and ships Z-Burst unbound to avoid DMZ's V Stats binding.
- Rejects unsupported NPC ki dispatch before spending and canonicalizes supported IDs; preserves existing administrator-forced chunks when missile tickets expire; accepts the first seat-control frame after server-clock restart.
- Adds a render path giving each copy its own proxy identity, so it can draw with your DragonMineZ appearance and carry its own aura instead of a plain player model. Switchable off (`cloneDmzAppearance`); unverified in game.
- Makes chase acknowledgments, disconnect/context cleanup and temporary flight ownership authoritative and depletion-safe.
- Refreshes and consumes negative-effect transfers at save/clone/native NPC reset boundaries; cured effects must not return on reconnect.
- Preserves party friendly-fire choices on invitations, detects every effective cockpit input field and releases Hakai-owned glow before restart.
- Repairs the Create Propulsion plasma-particle mixin, which never applied: it shadowed fields that live on vanilla `Particle` rather than on the target, so plasma kept colliding with ships.
- Blue combat boxes are illustrative fixed geometry, not authoritative range or collision volumes.

See [`docs/releases/v0.2.1.md`](docs/releases/v0.2.1.md) for scope, executable regressions and runtime limitations.

## Released versions

### [v0.2.3-1.21.1](docs/releases/v0.2.3-1.21.1.md) — 2026-09-08

**1.21.1 / NeoForge.** BT3-style Controlify controls, CustomNPC quest rewards, ki cleanup commands, clone/charge fixes, and DMZ saga enemy spawn recovery.

### [v0.2.1](docs/releases/v0.2.1.md) — 2026-09-07

**1.21.1 / NeoForge.** Refactor, bug fixes, and the BT3-style technique work: left-click ownership, rush strikes, chase, Zanzoken, Shi Shin No Ken and the copy system.
### [v0.1.11](docs/releases/v0.1.11.md) — 2026-09-01

**1.21.1 / NeoForge.** NPC appearance/combat expansion, Hakai, targeting, aerodynamic flight, HUD and compatibility.

### [v0.1.10](docs/releases/v0.1.10.md) — 2026-08-27

**1.21.1 / NeoForge.** Sable flight controls, expanded combat effects, DMZ parties, and My NPCs/CustomNPCs scripting.

### [v0.1.9](docs/releases/v0.1.9.md) — 2026-08-10

**1.21.1 / NeoForge.** Actual port from the inherited 1.20.1 codebase to NeoForge 1.21.1 and Sable, plus ship systems and release fixes.

### [v0.1.8-1.21.1](docs/releases/v0.1.8-1.21.1.md) — 2026-07-26

**actually 1.20.1 / Forge.** Alias of v0.1.6 with no unique commits; it does not contain the 1.21.1 port.

### [v0.1.6](docs/releases/v0.1.6.md) — 2026-07-26

**1.20.1 / Forge.** Major BT3 combat, progression, ship guidance, compatibility, and performance expansion.

### [v0.1.5](docs/releases/v0.1.5.md) — 2026-07-25

**1.20.1 / Forge.** Live DragonMineZ form-stat scaling and administration.

### [v0.1.4](docs/releases/v0.1.4.md) — 2026-07-25

**1.20.1 / Forge.** Cooldown HUD/editor, technique assistance, combat animation integration, and form scaffolding.

### [v0.1.3](docs/releases/v0.1.3.md) — 2026-07-24

**1.20.1 / Forge.** Simplified square portrait presentation.

### [v0.1.2](docs/releases/v0.1.2.md) — 2026-07-24

**1.20.1 / Forge.** Animated HUD snapshots, movable technique UI, and early scoreboard teammate display.

### [v0.1.1](docs/releases/v0.1.1.md) — 2026-07-23

**1.20.1 / Forge.** Expanded technique charge display to 1000%.

### [v0.1.0](docs/releases/v0.1.0.md) — 2026-07-22

**1.20.1 / Forge.** DragonMineZ technique hotbar and charge meter.

### [v0.0.9](docs/releases/v0.0.9.md) — 2026-07-22

**1.20.1 / Forge.** Modern UI Jar-in-Jar packaging.

### [v0.0.8](docs/releases/v0.0.8.md) — 2026-07-22

**1.20.1 / Forge.** Configurable ki overcharge scaling and catalog expansion.

### [v0.0.7](docs/releases/v0.0.7.md) — 2026-07-22

**1.20.1 / Forge.** XenoPixels identity, initial BT3 combat, DMZ HUD, and custom forms.

### [v0.0.6](docs/releases/v0.0.6.md) — 2026-07-22

**1.20.1 / Forge.** Strawberry Senzu and DMZ regeneration diagnostics.

### [v0.0.5](docs/releases/v0.0.5.md) — 2026-07-22

**1.20.1 / Forge.** First reachable baseline: ores, items, recipes, VS/DMZ hooks, wiki, and release automation.
