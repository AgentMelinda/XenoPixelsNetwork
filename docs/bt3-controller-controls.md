# BT3 Controller Controls

Requires the optional Controlify mod. Button names below use the Xbox layout. BT3 action detection and pilot-seat axes read Controlify's controller state directly; existing DragonMineZ key mappings are retained only where DragonMineZ provides no direct public action API.

## Controller Modes

- **BT3 Mode** enables the combat layout below.
- **Normal Mode** restores Controlify's standard Minecraft controls for mining, placing,
  inventory management, hotbar selection, and general building.
- Add **Activate BT3 Controller Mode** and **Activate Normal Controller Mode** to Controlify's
  radial menu. The selected mode is saved between launches and defaults to BT3 on first use.
- D-pad right remains Controlify's radial-menu button in BT3 Mode.

## BT3 Base Layout

| Input | Action |
|---|---|
| Left stick | Move / fly direction |
| Right stick | Camera |
| X | Melee combo / charged fist |
| Y | Ki blast |
| A | Dragon Dash |
| B | Guard |
| LB | Lock-on / cycle target |
| LT | Ki charge |
| RB | Activate flight; ascend while flight is active |
| RT | Descend |
| L3 | Toggle Search Fly / Combat Fly |
| R3 | Transform |
| View | DragonMineZ stats |
| Menu | Pause |
| D-pad up | Chase |
| D-pad down | Backstep |
| D-pad left | Sonic Sway Left |
| D-pad right | Controlify radial menu |

## Combat Chords

| Input | Action |
|---|---|
| LT + Y | Kick — tap for a normal kick, hold to charge it |
| RT + Y | Kick — tap for a normal kick, hold to charge it |
| Hold B + flick left stick left/right | Xeno left/right vanish |

The guard-stick vanish calls the existing server-authoritative Xeno vanish, including its KI
cost, range, cooldown, target lock, prediction, collision-safe landing, and animations. Keyboard
double-tap A/D vanish remains available.

## Moves that come out of combat, not buttons

Budokai Tenkaichi 3 earns its signature moves out of a string rather than giving each one a button,
and these now do the same. None of them occupies a chord any more.

| Move | How it comes out |
|---|---|
| Ultimate | The finisher beat of a combo on a locked target, when that beat is not already launching them |
| Cinematic Rush | Land X three times on the same target, then press A during the follow-up window |
| Z-Burst Dash | A combo swing at a locked target you cannot reach — it closes the gap instead of whiffing |
| Sonic Sway Left / Right | Step left or right while guarding |
| Sparking | Fill your ki, then keep charging through the red-to-gold Max Power stage |

Their keys and chords are switched off, not removed: `/xenobind list` shows them and
`/xenobind ultimate true` puts one back.

Z-Burst deliberately does not sit on "forward during a combo" — forward already means launch the
target, so one press would have fired both.

## Sparking

Sparking is entered by filling the normal ki bar, then continuing to charge for five seconds by
default. The eight full ki segments turn red, then change to gold from left to right; releasing the
charge early resets the whole stage. Sparking activates immediately when the last segment becomes
gold. It then lasts until that ki drains: the bar is the timer, so spending ki on techniques
shortens it and holding the charge spends the whole bar's worth. The ki bar stays gold while it is
up.

While Sparking, the release ceiling is lifted from DragonMineZ's normal cap of 115% (which is
`50 + potentialunlock_level * 5`) to `sparkingReleaseLimit`, and the player moves and attacks
faster. When it ends, the ceiling and speed are restored and a cooldown runs.

Everything is settable in game, for example `/xenoserver set sparkingduration 200`:

| Key | Meaning |
|---|---|
| `sparkingDurationTicks` | How long a full bar lasts — this also sets the drain speed |
| `sparkingChargeTicks` | Full-ki Max Power charge time before Sparking activates; default `100` |
| `sparkingCooldownTicks` | Wait before Sparking can be used again |
| `sparkingReleaseLimit` | Release ceiling while Sparking, in percent |
| `sparkingMoveSpeedMult` | Movement speed multiplier |
| `sparkingAttackSpeedMult` | Attack speed multiplier |

`sparkingFromKiCharge` switches back to the old hit-built meter, which is kept, not removed.

Sparking is a DragonMineZ skill, so it appears in the skill list and is saved by DMZ. It is granted
automatically once Potential Unlock is maxed at level 13 — the point where the normal release
ceiling tops out at 115% — and Goku, Vegeta, Gohan, King Kai, Old Kai and Piccolo also teach it.
While it is up the aura turns gold in every form.

## Radial menu entries

The BT3 layout spends every face button, both bumpers, both triggers, both sticks and the d-pad, so
the rest of what XenoPixels owns is offered to Controlify's radial menu instead. All of these ship
**unbound** — they cost no button, and they still appear in Controlify's binding config if you would
rather give one a button of your own.

| Radial entry | Action |
|---|---|
| 1-8 | DragonMineZ technique slots |
| `<` / `>` | Dash left / dash right |
| KC | Ki blast cancel |
| LP | Lock previous target (LB cycles forward) |
| KG | Ki guidance |
| LK / CL / CY | Target lock toggle / clear lock / cycle target |
| LD | Toggle target lead |
| PT / PG | Party screen / ping target |

Each one drives the same key binding the keyboard uses, so there is no second implementation of any
of these actions to keep in step.

## DragonMineZ technique slots

Hakai, Zanzoken and Shi Shin No Ken are registered as DragonMineZ techniques rather than living on
a chord. Equip one to a DMZ technique slot and cast it from that slot — on a gamepad, through the
technique-slot entries in Controlify's radial menu, which is what the slots are there for. They are
granted automatically, so they appear in the technique list without being unlocked first.

Their old chords (LB + X, LB + Y, LB + B) and their keyboard bindings are switched off rather than
removed. `/xenobind list` shows every such route, and `/xenobind hakai true` turns one back on if
you prefer the direct input or hit a problem with the slot route. The change applies to the next
press; no restart.

## Kick

There is one kick, and holding its button makes it heavier — it is not a separate charged move.
Releasing immediately throws an ordinary kick; the charge builds from the moment the button goes
down, with no arming tap and no delay before it engages. Holding W or S during the charge biases
the launch up or down. The keyboard binding is its own key (middle mouse by default), and on the
gamepad it currently lives on the Y chords above.

The same is not true of the fist: a tap there is already the ordinary combo punch on the same
button, so only a real charge releases the heavy version.
