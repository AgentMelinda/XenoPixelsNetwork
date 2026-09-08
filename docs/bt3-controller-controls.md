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
| LT + A | Z-Burst Dash |
| LT + X | Ultimate |
| LT + B | Sparking Mode |
| LT + Y | Charged kick |
| RT + Y | Charged kick |
| LB + X | Zanzoken |
| LB + Y | Shi Shin No Ken / Multi-Form |
| LB + B | Hakai |
| LB + A | Sonic Sway Right |
| Hold B + flick left stick left/right | Xeno left/right vanish |

The guard-stick vanish calls the existing server-authoritative Xeno vanish, including its KI
cost, range, cooldown, target lock, prediction, collision-safe landing, and animations. Keyboard
double-tap A/D vanish remains available.
