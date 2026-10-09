# XenoCombat v2

**Written:** 2026-10-05. **Revised:** 2026-10-07 (Search Fly chase and UltimateFinisher).
**Status:** builds and unit tests pass. Earlier builds were played; nothing in the last two
revisions (lock-on only, the keybind crash fix, the shared grab) has been observed in a running
game yet (see "Not verified").

v2 is a second combat controller that lives beside the existing one. The existing combat is v1 and
stays the default. A server that never changes `combatControllerMode` runs v1.

**v2 only works while you are locked on.** Everything below needs a DragonMineZ lock on a target,
and every move is made at that target. With nothing locked, v2 does nothing at all: left click is
DragonMineZ's ordinary punch, every key has its ordinary meaning, and no prompt is drawn.

**One v2 move is not v2's alone: the grab.** The `legacy` and `bt3_manual` controllers have it
too, with its throw, its break-out and its prompts, on their own guard and punch keys. See
"Grab and throw under legacy and bt3_manual". Nothing else of v2 runs under those controllers.

Design: `docs/superpowers/specs/2026-10-05-combat-v2-design.md`.

## Search Fly and UltimateFinisher (2026-10-07)

V2 Chase now selects DMZ Search Fly (flight mode 0) even when Combat Fly was already active.
The mover sends velocity toward the target and still uses its collision/detour checks. On arrival,
cancel or timeout, it restores the original flight mode and its own temporary flight grant, unless
the player has since disabled flight or selected another mode. Fly remains a learned skill; chase
does not unlock it. Search Fly is used for Chase regardless of the optional travel-pose setting.

`xenopixelsmod:ultimate_finisher` is a native DMZ Strike Attack named **UltimateFinisher**.
It unlocks with the existing rush kit (auto-unlock or an earned Rush skill), and can be equipped
and selected in any DMZ technique slot. Cast while locked onto a visible target within 48 blocks,
with empty hands, a created character, Fly, Ki Control and Ki Sense learned, and at least 5% power release.
It costs 40 ki up front, has a 400-tick cooldown, and uses the existing paid Surge sustain rules.

The server owns the sequence after a velocity-based Search Fly approach: six alternating native DMZ punches
at six-tick intervals; an eight-tick grab; a twenty-tick arc throw covering 15 blocks with a
four-block apex; forty ticks of native Kamehameha charge; then forty ticks of automatic Surge feed.
During the throw, held mobs keep their temporary NoAI flag. NoAI disables vanilla travel as well
as AI, so `UltimateFinisherThrow` consumes the arc velocity with `Entity.move(MoverType.SELF, ...)`
and clears the remaining impulse. This moves the victim once per step with collision handling;
writing velocity alone left XenoPixels NPCs stationary after the grab. Blocked paths and protected
NPCs still refuse the throw. The controller restores the original AI flag when it releases the victim.
Kamehameha creation and release call DMZ's `TechniqueDispatcher.executeKiAttack`, including its WAVE
type, native renderer/colors, charged damage and lifetime. The released ray keeps its firing direction.
Only the finisher's marked wave applies an additional push after accepted damage, with DMZ flight
synchronization and Saga knockback handling. The six punches do not push the victim out of the combo.
Surge obeys the server's enabled flag and drains ki/stamina through `BeamSurgeManager`.

Only the caster receives the cinematic camera: approach tracking, side shots for punches/grab,
a wide arc shot, then an over-shoulder charge/fire shot. Camera position clips against walls and
the previous view is restored on completion, cancellation, logout, dimension change or timeout.
The attack stops for refused damage, a blocked approach/throw, death, logout, dimension change,
or damage to the caster. Player strike locks, borrowed gravity, mob AI and flight state are released.
Grab and throw reuse the existing available poses; no new dedicated grab animation asset is shipped.

Gameplay presentation, multiplayer timing and the new camera mixin still require a fresh-client
playtest. Unit tests and builds do not verify that the sequence actually ran in a game.
Real server-level GameTests on 2026-10-07 verified native WAVE charge survival, firing damage/beam
growth, fixed firing direction, and knockback only after accepted damage. They do not render the
cinematic or exercise the entire six-punch/grab/throw sequence.
Fresh server GameTests also reproduced the stationary held Xeno NPC before the throw fix, then
verified actual 15-block displacement, an approximately four-block apex, preserved NoAI and no
queued second movement after the fix. Blocked paths and NPC knockback protection were checked.

## Controller commands

| Command | Effect |
|---|---|
| `/xenocombat mode v2` | Switch to v2. Live combat state on every player is swept first, then clients are told. |
| `/xenocombat mode legacy` | Back to v1. Same sweep. This is the rollback. |
| `/xenocombat status` | Which controller is running, and which controllers have the grab. |
| `/xenocombat reload` | Re-read `xenopixelsmod-server.json`, `xenopixelsmod-combat-v2.json` and the combo graph. |

Permission to use v2 once it is on: `combat.v2.use` (everyone by default). The grab under the
other controllers has its own: `combat.grab.use` (everyone by default). Breaking out of a grab
needs neither.

Client and server must both use the same network protocol. The 2026-10-07 Dragon Dash
continuation build uses protocol **106**; earlier protocol-105 jars cannot connect to it.

## What v2 replaces

| Move | v1 | v2 |
|---|---|---|
| Combo | Jab-jab mash, finisher every 4th hit | Authored graph: light and heavy branch from every beat |
| Kicks | Charge kick on its own key | The heavy attack. W + heavy launches, S + heavy slams, a held heavy kicks the target away in an arc |
| Directional styling | A = cross, D = uppercut | W/A/S/D each pick a different pose on every light beat, and a different result on kicks |
| 4th beat | Automatic | Player picks: light = kick finisher, heavy = smash, guard + light = grab, dash key = rush |
| Chase / dragon homing | Separate systems | One travel mover with v1's look: aura lit, flight pose if Fly is learned, detours over obstacles |
| Z-Burst | Its own key, mid-combo only | Automatic: a swing at a target out of reach closes the gap |
| Dragon Dash | Teleport, hit, coin-flip chase | Flies in, hits and launches; another N crosses to the target's other side during approach or the post-hit window |
| Sonic sway | `,` and `.` | Removed from v2; no Step move |
| Vanish | 7 blocks, no protection | Double-tap A/D, or B. Counter or vanish behind the target throughout the validated DMZ lock range, at its height; attacks miss for 8 ticks after |
| Super counter | Window on being hit, vanish upgrades | Same idea, own window, a 16-block limit and a lockout so counters cannot be traded forever |
| Cinematic rush | After 3 hits + dash key | Same input, same `RushRegistry` choreography and `RushEvent`s |
| Four rush strikes | Knockback and chase applied when the strike starts | Thrown at the lock: applied when DragonMineZ releases the strike, and the view is the lock-on camera's alone (see "Rush strike view") |
| Grab / throw | None | New |

Still v1 underneath, reached from the v2 keys: guard, ki blast, ultimate, Sparking, Hakai,
Zanzoken, Multi-Form, rush combo, lift combo, and every ki mixin.

## Controls (default v2 layout)

**Lock on first.** Middle mouse (or DragonMineZ's own lock key) locks the target under the
crosshair. That is the switch. It is DragonMineZ's lock, so it needs the Ki Sense skill and reaches
as far as Ki Sense lets it.

While a target is locked and both hands are empty, these keys are the v2 moves, all made at the
locked target:

| Input | Action |
|---|---|
| Left mouse | Tap for a light punch. Hold, then release, for a dedicated charged punch |
| Right mouse | Tap for a heavy kick. Hold, then release, for a dedicated charged kick |
| W / A / S / D + attack | Directional variant of the beat. W + kick = launcher, S + kick = slam |
| R (hold) | Guard |
| E | Inventory, as always |
| R + left mouse | Grab |
| B | Vanish; super counter when the locked target has just hit you. Works with an item in hand too |
| Q | Ki blast |
| Double-tap A / D | Vanish left / right; super counter when its window is open. Works with an item in hand too |
| Double-tap W | Chase |
| W once after a launch | Dragon homing |
| N (hold to charge, release) | Dragon Dash; cinematic rush when the rush prompt is up |
| N again during Dragon Dash / its post-hit window | Immediately vanish across the same locked target, face it, then continue with a mouse attack |
| Mouse wheel | Next / previous target |

Dragon Dash continuation (2026-10-07): after launching a dash, a fresh N press crosses the
approach line to the other side of the same target, including at melee range and at a different
height. It stops the approach and faces the target at its height. After a successful dash hit,
the same option remains open for `dragonDashFollowupTicks` (24 by default, 1.2 seconds;
clamped to 0–100). Setting 0 disables only the post-hit window. The prompt reads **N: Dash vanish**
while the server reports a valid window. The swap is immediate on press, once per dash; holding
or releasing that press does not launch another dash. Continue the attack with left/right mouse.

The swap uses the existing Vanish ki/stamina cost, cooldown and invulnerability window. It
still needs a permitted lock in Dragon Dash range, a clear destination inside the world border,
and visibility when `lockOnThroughBlocks` is disabled. Failed validation spends nothing. Damage,
guard, a screen, loss/change of lock or leaving combat closes the continuation and cancels an
active dash. Another combat input closes the post-hit window. The initial launch retains its own
Dragon Dash cooldown. The server owns
the target/window; a client percentage cannot open one.

The N gesture, target/window expiry and packet round-trip checks pass in JUnit. Native server
GameTests on 2026-10-07 verified opposite-side geometry across target yaws, near/far range and
height differences, plus solid-destination and out-of-range rejection. These tests exercise the
world collision calculation, not an actual client N packet or rendered teleport. The first
isolated server attempt crashed in DMZ's radar synchronization (`RadarSyncS2C` missing payload
binding); the retry completed all fifteen tests and was stopped normally. Long-running server
stability after that radar failure remains unverified.

Charged strikes (2026-10-07): a tap resolves on release; an eight-tick hold starts a dedicated
charge pose, followed by twenty server ticks to full power. Holding either mouse button builds
a golden body glow and feet ring, brightest and pulsing at full power. Release fires one
whole-body punch or kick, rather than adding a second ordinary combo beat. Screens, lock loss,
guard, damage and controller changes cancel the charge. Client percentages cannot grant full power.

Fully charged, visible targets **7–20 blocks away, inclusive**, advance a separate persistent
four-release counter for punches and kicks. The fourth eligible release resets its counter and
attempts a safe teleport: the kick lands behind the target's body, the punch in front, both at
the target's height. This is a deterministic 25% cadence, not random chance. Other releases
strike from the fighter's current position and may miss when outside melee reach. A blocked
landing consumes that fourth attempt and never teleports into terrain. Existing protection,
lock permission, accepted damage and guard rules still apply.

A connected fourth kick sends the victim twenty horizontal blocks along a six-block-high arc
over twenty-four ticks, unless a collision or protection ends it sooner. NoAI Xeno NPCs consume
this movement explicitly instead of waiting for vanilla travel. A fully charged punch produces
an impact explosion at the victim's upper body after accepted damage, without a second damage
event or terrain destruction. A/D hooks and ordinary kicks now transfer weight through the
waist, supporting leg and counterbalancing arms. The charged hold/fire slots retain studio
overrides and also supply the new default poses to Legacy/BT3.

UltimateFinisher flight jitter (2026-10-07): native Search/Combat Fly suppression now follows
the entire cinematic, including the stationary combo/throw/charge/beam after chase arrival.
Movement input also yields throughout the sequence and resumes after its STOP packet or camera
timeout. Source diagnosis found chase suppression previously ended at arrival while the server
continued zeroing velocity. Compilation passed after validation resumed on 2026-10-07.
Fresh client gameplay and visual resolution of the shaking remain **not verified**.
The finisher beam's accepted-damage impulse is now applied after the native damage method
returns, because vanilla hurt can overwrite an impulse applied inside LivingDamageEvent.Post.

Fresh server GameTests on 2026-10-07 ran thirteen tests with no failures. They verified actual
NoAI Xeno NPC twenty-block charged arc movement, target-height front/back landings and solid
obstacle refusal, plus eight-direction native beam damage/knockback and the finisher throw.
This does not verify mouse packets, live fourth-release behavior or client visuals.

With nothing locked, none of the above does anything: left click punches the DragonMineZ way,
right click uses and places, E opens the inventory, Q drops, B and N are idle, and a double-tap is
just two taps. Two things work without a lock, because neither is a move you start:

- **R + left mouse breaks a grab** that has caught you.
- **S stops a chase** that is already carrying you.

The server holds every input to the same rule: it must name a target the fighter could have locked
(Ki Sense learned, inside lock range), or it is refused. The lock itself lives on the client and
DragonMineZ does not send it to the server, so that is as far as the server can check.

Notes:

- **Double-tap A/D for Vanish.** The second press must be within 280 ms of the first, matching
  BT3 and Legacy. The movement bindings are used, so remapping A/D also remaps this gesture.
  Walking without a lock does not arm it. B remains a dedicated alternative; V is DragonMineZ's
  stats menu. A profile still on the old V default is moved to B once.
- **Step was removed on 2026-10-06.** There is no Step key, sideways Sonic Sway shortcut, back
  double-tap dodge, or Step configuration in v2. S still stops an active chase; jump stays jump.
- **Guard and Grab use R from 2026-10-06.** A saved plain E guard binding migrates to R once;
  custom bindings are preserved. E opens the inventory normally, including during a fight.
  While v2 owns R, its Guard suppresses DragonMineZ's Dash on the same key. Outside the stance,
  the Dash mapping resumes its ordinary behavior. If Guard is deliberately rebound to the
  inventory key, the optional tap-for-inventory behavior still applies.
- While holding a grabbed target, the direction held at the end of the hold sets the throw:
  forward (default), back (S), up (Space), down (Shift).

The keys are in Controls under **XenoPixels Combat v2**. The COMBAT strip in the HUD lists the v2
moves and keys while v2 is running: **Lock** first, lit while a target is locked, and the rest
greyed out until then.

### Vanish in detail

Double-tapping A/D or pressing B does the first of these that applies:

1. **Super counter**, if the locked target landed a hit in the last 10 ticks and is within 16
   blocks: appear behind it and strike. A hit from anyone else does not open a counter; pressing
   vanish then is a vanish.
2. **Vanish behind** the locked target throughout the validated DMZ lock range, at the target's
   height even when it is far above or below you. The double-tapped key picks the side;
   with B, A or D held picks it. Through a
   wall only when the server's lock-on "through blocks" setting is on.

`vanishUsesLockRange` defaults to `true` in `config/xenopixelsmod-combat-v2.json`, including files
that still contain the older `vanishRange: 12`. To impose a shorter server limit, set it to
`false` and set `vanishRange`. An out-of-range target then refuses the vanish. Lock validation,
protection, visibility rules, open landing spots, cost and cooldown still apply.
Fresh server GameTests on 2026-10-07 checked the production landing helper against distant,
elevated and lower targets, left/right offsets and an explicit shorter range. Full player input
and client teleport presentation remain manual checks.

It works while reeling from a hit: it is the way out of a string. Attacks pass through the fighter
for 8 ticks afterwards, and it cannot be used again for 24 ticks. It does not work out of a grab
(break free instead), out of your own rush, or while DragonMineZ has you stunned.

## The combat prompt

A row of green plates at the bottom of the screen, centred, just above the hotbar and the
action-bar text. It is only drawn while a target is locked, with two exceptions that are not moves
you start: caught in a grab it says how to break out, and holding someone it shows the throw.
Each plate is one line: the key, then what it does. The outline says what kind of thing it is:

The client file `config/xenopixelsmod-client.json` has independent switches
`combatPromptsWithLockOn` and `combatPromptsWithoutLockOn` (both default `true`). Set both to
`false` to hide the key-prompt plates in every lock state, including Grab, Throw, Break free,
Counter and Chase. They also gate the legacy counter action-bar prompt. They affect display
only; combat input remains active. Restart the client after editing its file.

| Outline | Means | Examples |
|---|---|---|
| Pill | A punch that keeps the string going | `LMB Punch` |
| Hex | A hit that ends it | `LMB Kick`, `RMB Launch`, `RMB Smash` |
| Tray | Grab and throw | `E+LMB Grab`, `Throw` |
| Arrow | Anything that travels | `N Rush`, `W Chase` |
| Burst | Getting out of trouble | `B Counter`, `E+LMB Break free` |

After a landed beat the open branches are shown with a bar that closes in as the time to pick runs
out. The labels are what the branch does, not which button it is: after three punches the light
attack reads **Kick**.

Art: `textures/gui/atlas/combat_prompt_<kind>_w<width>_<palette>.png`, 25 shapes in four palettes,
generated by `tools/atlas-panels/xeno_extra_specs.py` (the DMZ-style panel generator) and blitted
1:1 at the width the text needs. To regenerate:

```
cd tools/atlas-panels
python xeno_extra_specs.py --only <names> --out ../../src/generated/resources/assets/xenopixelsmod/textures/gui/atlas
```

`CombatPromptPlatesTest` fails if a plate has stray or half-transparent pixels, or if a label would
sit on its frame.

## How a v2 hit is worked out

- **Damage** is the fighter's DragonMineZ melee damage times the beat's `damage` scale, set through
  `DMZEvent.DamageModifyEvent`. Everything after that (form multipliers, the target's defence,
  blocking, Sparking, skills) is DragonMineZ's and v1's, unchanged. A held attack adds up to 35 %.
- **Stamina.** DragonMineZ charges the attacker stamina on every melee hit and empties the pool
  when it runs short. v2 strikes skip that (`strikesDrainStamina`, off by default), and no shipped
  beat costs stamina. Stamina is spent on the grab and guarding.
- **Hit cooldown.** Minecraft ignores a hit landing within half a second of the last unless it is
  bigger. v2 clears it per strike; the pace of a string is the graph's timings instead.
- **Reach** is 3.75 blocks from the attacker's eyes to the nearest point of the target's hitbox,
  in the front half of the view, with no solid block in between.
- **One target.** A swing lands on the locked target or on nobody. It never picks someone else
  to hit, so a punch thrown beside a crowd touches nobody in it.
- **Pace.** A beat is `startup` ticks to the hit, `cancel` ticks of recovery, then a `window` to
  continue. An input during startup or recovery is held (up to 8 ticks) and played when the beat
  opens, so mashing neither drops hits nor speeds a string up.
- **Reactions** come from one table (`HitReaction`). Kicks use the same launch functions and server
  tuning as v1 kicks (`kickKnockbackScale`, `comboLauncherUp`, `kickDownLaunch`...). A guarded hit
  still lands but does not move the defender.

## Grab and throw

The same grab under every controller. What differs between them is only which keys reach it.

- Guard + light, with the locked target in reach (2.6 blocks, body to body) and in front. Only
  the locked target can be grabbed.
- 6 ticks of startup. A hit on the grabber during startup cancels it.
- Ignores guard: the defender's guard comes down as the grab lands, so the hit it lands with is
  not a blocked one. Does not catch a target just out of a vanish.
- Connects as a hit (a quarter of the grab's damage). A grab that cannot hurt its target, because
  a claim, a safe zone or PvP being off refuses the damage, does not hold it either, and a
  defender whose guard was up keeps it.
- Holds for 14 ticks, then throws for the rest of the damage. A player victim can break free in
  the first 6 ticks.
- Whatever the defender had under way stops when the grab connects: a cinematic rush, a rush or
  lift combo, a chase. While held, a player or a mob cannot strike: a blow struck directly by
  whoever is being held is refused (a projectile already in the air still arrives).
- Refused against DragonMineZ masters, party members with friendly fire off, players the server's
  PvP rules protect, anything taller than 3.2 blocks, anything `CombatKnockback` will not move,
  and anyone riding or being ridden (a saddle, a boat, a ship's chair).
- Cannot be started while the grabber is in a cinematic rush or channelling Hakai.
- What it looks like: the grabber plays the step-in pose as they reach, and a strike pose as the
  victim leaves their hands (the string-ending blow for a throw forward, the uppercut for a throw
  up, a hook for a throw back, a body blow for a throw down). These are stand-ins: there are no
  grab or throw clips yet, and the victim has no "held" pose at all.
- A hold is re-checked every tick and released if the grabber or victim dies, logs out or changes
  dimension.
- Addons: `GrabEvent.Connect` (cancellable), `GrabEvent.Throw`, `GrabEvent.Tech`.

### Grab and throw under legacy and bt3_manual

On by default (`grabOutsideV2` in `xenopixelsmod-combat-v2.json`; `grabEnabled` turns the grab off
everywhere). Permission: `combat.grab.use`.

| Input | Action |
|---|---|
| Guard (right mouse) + punch (left mouse) | Grab the locked target |
| W / S / Space / Shift while holding them | Throw forward / back / up / down |
| W once after the throw | Chase whoever was thrown (dragon homing) |
| Punch (left mouse) while held | Break free, in the first 6 ticks |

- **It needs a lock**, like the grab in v2: guard + punch with nothing locked does nothing new.
  Guard has to be up, so a server with the guard turned off has no grab under these controllers.
- **The two buttons may land up to 2 ticks apart, in either order.** A punch into a raised guard
  grabs, and so does a guard raised just after the punch. A guard raised into a punch key that
  has been held longer than that is a block, as before, so holding left mouse for a string and
  then blocking does not grab. Under bt3_manual, left mouse first means the DragonMineZ punch
  comes out before the grab.
- **Guard + punch was a free chord.** With the guard up and both hands empty, left click already
  did nothing under either controller, so no existing move was taken away to make room.
- **Nothing else is read during a grab**, at either end: no punch, kick, charge, vanish, chase or
  technique key, exactly as with a screen open. The legacy packet handler also refuses every
  legacy action from a fighter in a grab, so a modified client gains nothing by sending them.
- **The guard goes down for the length of the grab** and comes back up when it ends, if the key
  is still held.
- **The chase after a throw is the legacy dragon homing**, the window a launching kick already
  opens there: one tap of forward within 2 seconds, at the usual chase cost, while still locked
  on whoever was thrown. A throw down opens none.
- **Prompts** are the same green plates as v2, labelled with these controllers' keys:
  `RMB+LMB Grab` when the locked target is in reach and a grab is ready, `Throw` with the four
  directions while holding, `W Chase` after the throw, and `LMB Break free` or `Grabbed` when
  caught. Their other windows keep the indicators they already had (the rush follow-up prompt in
  the BT3 HUD, `Counter!` on the COMBAT strip).
- **The COMBAT strip gains a Grab chip** beside Guard: lit during a grab, greyed while none can be
  started (cooling down, or the server keeps the grab to v2).
- **Not there:** combo branches, the v2 counter, v2 chase, v2 vanish. Those are v2.

## Combo routes

Shipped: `data/xenopixelsmod/combat_v2/combo_graph.json`. To author your own, put a complete
replacement at `config/xenopixelsmod/combat_v2_combo_graph.json` and run `/xenocombat reload`.
A file that does not parse, or that points at a node it does not define, is refused with the
reason in the log and the shipped routes stay in force.

Each node:

| Field | Meaning |
|---|---|
| `intent` | The pose, a `Bt3AnimationIntent` name |
| `variants` | Per held direction: a pose name, or `{ "intent": ..., "reaction": ... }` to also change what the hit does |
| `startup`, `cancel`, `window` | Ticks to the hit, ticks of recovery, ticks a follow-up is then accepted |
| `damage` | Multiple of DragonMineZ melee damage |
| `ki`, `stamina` | Cost when the beat starts (the shipped routes cost neither) |
| `reaction` | A `HitReaction` name |
| `charged` | The reaction when the attack was held |
| `action` | `grab` or `rush` hands the beat off |
| `next` | Which node each of `light`, `heavy`, `grab`, `rush` leads to |

## Tuning

`config/xenopixelsmod-combat-v2.json`, written with defaults on first server start. Every value is
clamped on load. It covers input buffering, strike reach and facing, line of sight, stamina,
chase and its look, Z-Burst, Dragon Dash, rush, counter, vanish and grab.

The file carries a `version`. One written by the first build (no version) has its damage scales,
strike range and input buffer put back to defaults once, because those numbers meant something
else then. Everything else in it is kept.

Ki and stamina costs are absolute, as in v1, so they are small beside a trained fighter's pools.
What paces a move is its cooldown.

## Keybind system (`/xenokeybind`, client side)

| Command | Effect |
|---|---|
| `status` | Lists the bindings from other mods that are currently bound, and the backups on disk. |
| `backup` | Saves every binding to `config/xenopixelsmod/keybind-backups/`. |
| `clean` | Backs up, then unbinds every binding that is not Minecraft's, DragonMineZ's or XenoPixels'. |
| `restore [file]` | Restores a backup; the newest when no file is named. |
| `preset xv2` | Backs up, then applies the layout above plus the technique palettes below. |
| `auto <true/false>` | Whether `clean` runs by itself the first time v2 is active. On by default; runs once. |

If the backup cannot be written, nothing is unbound. To keep another mod's keys through a clean,
add its translation-key prefix (for example `key.controlify.`) to `keybindKeepPrefixes` in
`xenopixelsmod-client.json`.

**Held modifiers are never unbound.** A binding whose own default key is Shift, Ctrl or Alt
(Create's "Shift Modifier", "Ctrl Modifier", "Alt Modifier", its tool menu and toolbelt) is not an
action; it is how that mod asks whether the key is held, and it reads it straight from the
keyboard. The first build unbound those too, and Create then asked the keyboard for key -1 while
Minecraft was building the creative search index on a worker thread, which crashes the game on the
first letter typed into the creative search (`Encountered GL error off-thread ... Invalid key -1`).
Three things now prevent that:

- the cleanup leaves such bindings alone;
- a profile an earlier build already cleaned has them put back once, on the next start, from the
  backup the cleanup wrote;
- asking whether an unbound key is held is no longer an error for any mod
  (`InputConstantsUnboundKeyMixin`), so unbinding a key in Controls cannot cause this either.

`preset xv2` also rebinds DragonMineZ's own keys: technique slots 1 to 4 to Ctrl + left mouse,
right mouse, Q and Space; slots 5 and 6 to Alt + left and right mouse; instant transform to Alt + Q.
Zanzoken goes to Alt + Space.

## Rush strike view

This applies to the four rush strikes (left, right, breaker, finisher) in both v1 and v2, **when
the strike is thrown at the target you are locked on**.

DragonMineZ runs every strike the same way: it teleports the attacker to the side of the target
that the target is not facing, snaps the attacker's view onto it, and then snaps the view onto the
target's eyes again on every tick of the strike. Against a locked target the lock-on camera is
easing the same view toward the target's body every frame, so the two took turns for the whole
strike. That was the twitch, and the half-turn at the start was the reposition.

The rush and lift combos never had either, because nothing on the server touches the view there.
A rush strike at the lock now works the same way:

- **Arrival.** The attacker lands 1.3 blocks from the target on their own side of it, on the line
  they were already looking along, so the target is where it was in the view, only closer. If
  they are already at arm's length they are not moved at all.
- **No server snap.** The server does not aim the attacker, at the start or per tick. It only
  turns the body other players see. The lock-on camera is the one thing moving the view.

A rush strike thrown with nothing locked, or one that lands on something other than the lock, is
run exactly as DragonMineZ runs it, snap included: there is no lock-on camera for the snap to
fight, and nothing else that would turn you to face what you are hitting. Under v2 it is also not
a v2 move, so what it does to the target is v1's.

The server knows whether a strike was thrown at a lock because DragonMineZ's own strike request
carries the id of the locked entity, or -1.

Cost, cooldown, damage, animation and the freeze are still DragonMineZ's. DragonMineZ's own strikes
are untouched. A player who is the *target* of a rush strike is still turned by the server, as
before.

## Changes that also reach v1

- **The grab and throw**, above. This one is a new move in the default controller, not a fix: a
  server that updates and changes nothing gets it. Set `grabOutsideV2` to `false` to keep it to v2.
- Because a grab can be in progress under any controller, the v2 fighter ticker now runs under all
  of them (it does nothing while nobody has grabbed), every client is sent its grab state on
  login, and a fighter dropped by death or a dimension change is told so.

Made while fixing v2:

- Rush combo and lift combo routes (`ComboRouteMachine`) clear the hit cooldown before each route
  hit. Their hits are equal-sized and 6 ticks apart, so every second one was being dropped.
- Lock cycling (`[` and `]`, and the wheel in v2) skips targets the lock key itself would refuse.
  With "lock through blocks" on, which is the default, nothing changes.
- `ChaseRouting`, `ChaseFlightOwnership` and the vanish sound helper became public so v2 uses the
  same rules. No behaviour change.

- The four rush strikes' view, above.
- The keybind cleanup rule, its one-time repair and the unbound-key guard are client-wide:
  they apply whichever controller the server runs.
- After a breaker or finisher, v1's automatic chase now starts when DragonMineZ releases the
  strike. It used to start with the strike, while both fighters were held still, so it spent the
  strike pulling against that hold and had usually stopped by the time the target was launched.

v1's rush strikes still apply their knockback when the strike starts; v2 applies its own when the
strike ends.

## Not verified

The 2026-10-07 build passed unit tests and the focused native server checks described above.
These broader scenarios still have to be watched in a real client, and the server-side ones
on a dedicated server too:

1. **Lock-on only**: that with nothing locked left click is DragonMineZ's own punch and every v2
   key is idle; that locking on turns all of it on and unlocking turns it off mid-fight with
   nothing left held; and that a move is refused when Ki Sense is not learned.
2. **The keybind crash**: that the game starts, that Create's Shift, Ctrl and Alt modifier
   bindings are back (Controls, Create section) after one launch, and that typing in the creative
   search no longer crashes. The chat line "put back N modifier key binding(s)" confirms the
   repair ran.
3. Every row under "Controls": R guard and grab, E inventory, B, the double-taps, and that the
   one-time guard key migration moves plain E to R without changing custom bindings.
4. Damage: that a v2 hit deals melee damage times the beat's scale, costs no stamina, and that
   every beat of a fast string lands.
5. Kicks: that W, S and held kicks launch, slam and arc as described.
6. Vanish: that the blow already in flight misses, and that a blink stops at a wall.
7. Chase: aura on and off again, the flight pose with and without Fly learned, detours.
8. Rush strikes at a locked target: that the launch and chase start when the strike ends; that
   the attacker lands on their own side of the target; and that the view no longer snaps or
   twitches. This depends on four mixin hooks into DragonMineZ's strike handler actually applying;
   a log line starting `Xeno rush strike view:` means the facing hook did not. And with nothing
   locked: that the strike is exactly DragonMineZ's own.
9. The prompt: its position against your HUD, and that long key names still fit.
10. Lock-on with "through blocks" on and off.
11. Animations: v2 sends the pose from the server and does not predict it locally, so a beat's
    animation starts one round trip after the click. Grab and throw have no clips of their own yet.
    Until 2026-10-06 the grab played no pose at all, in any mode: the step-in pose it was meant to
    reuse was never sent. It is sent now, with a borrowed strike pose for the throw; how either
    reads in motion has not been seen.
12. That DragonMineZ fires a technique slot bound to Ctrl/Alt + a mouse button.
13. Grab: the hold on players and on mobs, the tech window under real latency, and that nobody is
    left pinned after a logout mid-hold.
14. That stopping the server mid-chase does not leave a player weightless on their next login.
15. Tick cost with many fighters. No profiler capture was taken; no TPS claim is made.
16. Gamepad: the Controlify layouts have no v2 rows yet.
17. **The grab under legacy and bt3_manual**, all of it: that guard + punch grabs the locked
    target and nothing else changes when there is no lock; the throw in each direction; one tap
    of W chasing after it; breaking free with punch; that the guard comes back up after the grab
    with the key still held; that the two buttons pressed "together" grab whichever lands
    first; that a punch key held past the end of a grab goes back to punching without being
    released; and, under bt3_manual, that DragonMineZ's own punch does not fire during a hold. Also that
    the `RMB+LMB Grab`, `Throw`, `W Chase` and `Break free` plates appear when they should, and
    how the COMBAT strip looks with a ninth chip (the BT3 HUD rail is four wide, so it takes a
    third row).
18. That a fighter who dies while held is not left believing they are still held. The server now
    says so, and the client gives up on a grab state after 200 ticks regardless.
19. Charged mouse gestures, golden glow and whole-body poses; fourth full punch/kick releases
    under live latency; counter survival across death/logout; and UltimateFinisher flight
    shaking through every cinematic phase and its cleanup.
