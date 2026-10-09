# Budokai Tenkaichi 3 → DragonMineZ + Controlify

**Mods checked:** `dragonminez-2.1.3.jar`, `controlify-dev.jar`  
**Controller:** Xbox layout (A/B/X/Y, LT/RT, LB/RB, D-pad)  
**Pack:** `dmz-bt3-controlify.zip`

This is a Controlify default-bind resource pack plus an in-game radial setup. It does **not** add new DragonMineZ code. It only remaps buttons onto keybinds that already exist in DMZ 2.1.3.

---

## Hard limits (do not skip)

Controlify 2.x default-bind JSON only accepts one of:

- `button`
- `axis`
- `hat`
- `empty`

It does **not** have:

- a hold-duration gate (“held longer than 1.5 seconds”)
- a chord type (“LT + RT together” as one bind)
- two physical inputs on one bind ID

DragonMineZ 2.1.3 also does **not** have an “open ki attack menu” key.  
`Left Alt + 1/2/3/4` and `Ctrl + 1/2/3/4` **fire the slot immediately**. They do not open a picker.

So the requested rules are implemented like this:

| You asked for | What is actually possible | What this pack does |
|---|---|---|
| Hold **LT > 1.5s** = first ki menu (keyboard **Alt**) | No hold timer in Controlify; Alt is not a menu | **Hold LT** = Charge Ki (`key.dragonminez.ki_charge`, keyboard **C**) |
| **D-pad** selects the four bar-1 attacks | Yes. Those are already full KeyMappings | D-pad Up/Right/Down fire slots **2/3/4**; **Y** fires slot **1** |
| **LT + Y** fires a ki blast | No native chord; both can be held at once | Hold **LT** (charge) and press **Y** (Technique Slot 1). Slot 1 is the blast if you put a blast there |
| Hold **LT + RT > 1.5s** = second ki menu (keyboard **Ctrl**) | No chord + no hold timer | **Hold RT** opens Controlify’s **radial menu**. Put slots **5–8** on that wheel |

If you need a literal 1.5-second hold gate, that requires a small extra client mod. Controlify cannot do it with a resource pack.

---

## DragonMineZ keys this pack talks to

From `com.dragonminez.client.util.KeyBinds` in `dragonminez-2.1.3.jar`:

| Field | Translation | Default keyboard | Controlify bind ID |
|---|---|---|---|
| `ki_charge` | Charge Ki | C | `fabric-key-binding-api-v1:key.dragonminez.ki_charge` |
| `technique_slot_1` … `_4` | Technique Slot 1–4 (Bar 1) | Alt+1 … Alt+4 | `fabric-key-binding-api-v1:key.dragonminez.technique_slot_N` |
| `technique_slot_5` … `_8` | Technique Slot 5–8 (Bar 2) | Ctrl+1 … Ctrl+4 | same pattern |
| `block_key` | Block | Right mouse | `…block_key` |
| `dash_key` | Dash | R | `…dash_key` |
| `lock_on` | Lock on | Z | `…lock_on` |
| `utility_menu` | Utility Menu (combat radial) | X | `…utility_menu` |
| `action_key` | Action (transform / power) | G | `…action_key` |
| `descend` | Descend (Lower Form) | Alt+G | `…descend` |
| `second_function_key` | Second Function | Left Alt | `…second_function_key` |
| `fly_key` | Fly | F | `…fly_key` |
| `stats_menu` | Stats Menu | V | `…stats_menu` |

Controlify auto-registers every unmatched `KeyMapping` under the namespace `fabric-key-binding-api-v1`, even on NeoForge. That ID prefix is required.

---

## Xbox layout this pack applies

Face buttons use Controlify names: **south = A**, **east = B**, **west = X**, **north = Y**.

| Xbox | Binding | BT3 idea | DMZ action |
|---|---|---|---|
| Left stick | Move | Move | Vanilla walk |
| Right stick | Look | Camera | Vanilla look |
| **A** | Jump | Step / confirm | Jump / fly up while airborne |
| **B** | Block | Guard | `block_key` |
| **X** | Attack | Melee / rush | Vanilla attack (left click) |
| **Y** | Technique slot 1 | Ki blast | Bar 1 slot 1. Hold **LT** at the same time to charge while firing |
| **LB** | Sneak + Descend | Fly down | Vanilla sneak and `descend` (Alt+G) |
| **RB** | Dash | Short dash / Z-burst stand-in | `dash_key` |
| **LT** | Charge Ki | Ki charge / Blast modifier | Hold to charge. This replaces vanilla Use-on-LT |
| **RT** | Controlify radial | Second blast menu | Hold and flick right stick. Put slots 5–8 here |
| **L3** | Sprint | — | Vanilla sprint |
| **R3** | Lock on | Lock-on | `lock_on` |
| **View / Back** | Utility Menu | Pause-adjacent combat wheel | DMZ combat radial (**X** on keyboard) |
| **Menu / Start** | Pause | Pause | ESC |
| **D-pad Up** | Technique slot 2 | Blast 1 menu slot | Bar 1 slot 2 (Alt+2) |
| **D-pad Right** | Technique slot 3 | | Bar 1 slot 3 (Alt+3) |
| **D-pad Down** | Technique slot 4 | | Bar 1 slot 4 (Alt+4) |
| **D-pad Left** | unbound in pack | | Bind in-game to slot 1 if you want all four on the D-pad |

Vanilla Use (right-click), hotbar bumpers, drop, and chat were cleared off these buttons so they stop stealing DMZ inputs.

---

## Install

1. Put `dmz-bt3-controlify.zip` in `resourcepacks/`.
2. Enable it in **Options → Resource Packs**.
3. Connect the Xbox pad with Controlify loaded.
4. Open **Options → Controls → Controller** and **Reset All Binds** once so the pack defaults load.
5. Configure the RT radial (required for bar 2):

   Controller settings → **Radial Menu → Configure**

   Add these four actions:

   - Technique Slot 5 (Bar 2)
   - Technique Slot 6 (Bar 2)
   - Technique Slot 7 (Bar 2)
   - Technique Slot 8 (Bar 2)

   Also add Fly, Action, Stats Menu, Instant Transmission if you still need them. Those have no free face button left.

6. In the DMZ skill/technique UI, save a basic blast into **slot 1**. **Y** (and LT+Y) only fires whatever is in slot 1.

---

## If you want all four bar-1 attacks on the D-pad

Controlify will not store two defaults on `technique_slot_1`. Do this in the controller Controls screen:

1. Bind **Technique Slot 1** to **D-pad Up**.
2. Leave **Y** either unbound or bind it again to Slot 1 if the UI lets you override.

That gives:

- D-pad Up / Right / Down / Left = slots 1–4
- Y free, or a second blast alias if you rebind it

---

## BT3 moves that do not exist as DMZ keys

Do not expect these to appear as 1:1 buttons:

| BT3 | Why it is missing |
|---|---|
| Charged smash (hold X) | DMZ melee is vanilla attack + DMZ combo system, not a “hold until flash” KeyMapping |
| Sonic Sway / Vanish / Z-Counter | No matching KeyMapping in 2.1.3 |
| Max Power + Ultimate (LT + Down + Y) | No max-power KeyMapping; ultimates are technique slots |
| Transform on right-stick flick | DMZ transform is **G** (`action_key`) plus the **X** utility radial |
| Tag out L3+R3 | No tag-out KeyMapping; party tab is unbound by default |

Use the **View** combat radial for transform / power release / ki weapons. That is the real DMZ “specials menu”.

---

## Suggested in-game radial contents (RT)

1. Technique Slot 5
2. Technique Slot 6
3. Technique Slot 7
4. Technique Slot 8
5. Action (G)
6. Fly (F)
7. Stats Menu (V)
8. Instant Transmission (H)

Hold **RT**, flick the right stick, release. That is the closest legal stand-in for “second ki menu (Ctrl)”.

---

## Optional: make LT+Y feel more like a blast

1. Put your weakest / fastest ki shot in **Technique Slot 1**.
2. Hold **LT** to fill Ki.
3. Press **Y** without releasing LT.

Both bindings can be down together. That is a player chord, not a Controlify chord, and it is the closest match to “LT + Y = ki blast”.
