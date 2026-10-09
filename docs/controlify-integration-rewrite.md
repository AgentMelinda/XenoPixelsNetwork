# Controlify integration rewrite, and the 20-slot radial

Written 2026-09-23. Requested as *"ControlIfy-Intergation-rewrite-from-scratch"* — a Java package
cannot contain dashes, so the code lives in `client/pad2/` and this file carries the name.

## Why

The gamepad integration in `client/pad/` works, but it could only ever show **8 radial entries**
while the mod registers roughly **22 radial candidates**:

- 11 explicit actions (dashes, ki-blast cancel, lock-prev, ki guidance, four targeting, two party)
- one per entry in DragonMineZ's `KeyBinds.TECHNIQUE_SLOTS`
- 3 mode actions (normal / BT3 / flight)

DragonMineZ reaches its technique slots with `Alt+1..4` and `Ctrl+1..4` — chords no gamepad can
produce — so for a controller player those slots were the radial or nothing.

## The finding that made 20 cheap

Measured against the pinned Controlify jar
(`controlify_project_id=DOUdJVEm`, `controlify_version_id=RqsNKKLK`) with `javap`:

| What | Shape |
|---|---|
| `RadialItems.createBindings` | `bipush 8; anewarray RadialItem`, loop `i < 8` — **the only hard 8** |
| `RadialMenuScreen.init` | `arraylength`, `2π / N`, `Math.max(…, 43f)` — **already generic** |

The screen never assumed 8. Hand it a longer array and it spaces the ring correctly and grows the
radius. No screen patch was needed.

## The constraint that shaped the design

`createBindings` has two callers:

| Caller | `EditMode` | Widened? |
|---|---|---|
| `InGameInputHandler.handleKeybinds` | `null` | **yes** |
| `ControllerConfigScreenFactory` | `RadialItems$BindingEditMode` | **no** |

`BindingEditMode.setRadialItem` is:

```java
radialActions.set(i, ((RadialItemRecord) item).id())
```

That **casts to a package-private type** and **indexes the eight-entry list**. Widening that path
would be a `ClassCastException` on our own item type and an `IndexOutOfBoundsException` past slot
eight. So `ControlifyWideRadialMixin` redirects the **call site inside `handleKeybinds`**, not the
method. Controlify's own radial configuration screen is untouched and still edits its eight.

`PadRewriteWiringTest.theRadialWideningNeverReachesControlifysOwnConfigScreen` is what keeps that
true.

## Public surface used

All verified with `javap`; none of it invented.

| Symbol | Why it is safe to use |
|---|---|
| `RadialMenuScreen$RadialItem` | `public interface` — `name()`, `icon()`, `playAction()` |
| `InputBinding.fakePress()` | public; how Controlify's own item fires its action |
| `InputBinding.radialIcon()` | public; `Optional<ResourceLocation>` |
| `InputComponent.getBinding(ResourceLocation)` | public |
| `RadialIcons.getIcons()` | public; id → `RadialIcon` |
| `settings().input.radialMenu.radialActions` | public fields the whole way down |

**`RadialItems`, `RadialMenuScreen` and `InGameInputHandler` are outside
`dev.isxander.controlify.api`.** The version pin in `gradle.properties` is load-bearing: a
Controlify bump means re-running `javap` before trusting any of the above. The mixin carries
`require = 0` and `PadWideRadial` catches `LinkageError`, so the failure mode is "the stock
eight-slot radial" rather than a client that will not start.

## What the rewrite changed

`client/pad2/`:

| File | Role |
|---|---|
| `PadLayout` | the BT3 layout **as a table** — one row per binding. No Controlify types. |
| `PadInput` | the physical inputs, named symbolically so the table stays Controlify-free |
| `PadBinds` | registers the table with Controlify, and answers for it afterwards |
| `PadRadialSlots` | which 20 entries and in what order. Pure — no Controlify, no Minecraft. |
| `PadWideRadial` | resolves those ids to real radial items |
| `PadTextIcon`, `PadVanish` | copies of two package-private helpers from `client/pad/` |

Reused rather than duplicated, because all three are public and layout-neutral:
`PadChords`, `TechniqueSlotIcon`, `Bt3ControllerInput`. Rumble was not part of this change and
still runs through `client/pad/XenoPadRumble`.

The old package is **intact and reachable**. `XenoClientConfig.padRewrite` picks which registers —
default `true`.

## The switch, and why it needs a restart

Controlify reads its saved bind configuration immediately after pre-init, so bindings must be
declared there. Both packages declare the same binding ids, so only one may register.

`XenoControlifyEntrypoint` decides once, then calls `XenoPadInput.useRewrite(...)`. **Every later
read routes on that recorded decision, not on the live config value** — a player flipping the
config mid-session would otherwise start asking a package that registered nothing, and the pad
would appear to go dead rather than to need a restart. `ControlifyBt3ModeMixin` routes its conflict
check the same way.

## How DragonMineZ reads a key, and what a pad can reach

Measured 2026-09-23. This one table explains every gamepad gap that was reported, and it is the
first thing to check before adding any new binding.

| DMZ reads a key via | Pad emulation reaches it? | Why |
|---|---|---|
| `KeyMapping.isDown()` | **yes** | Controlify's `KeyMappingMixin` sets `isDown` |
| `KeyMapping.consumeClick()` | **yes** | the same mixin also increments `clickCount` |
| `InputConstants.isKeyDown(window, key)` | **no** | raw GLFW; nothing but a real key sets it |
| `InputEvent.Key` event | **no** | fired only by the real keyboard callback |

Worked consequences:

- **Flight steering and descend** read `Options.key*.isDown()` → emulation works. Flight descend is
  `Options.keyShift`; `KeyBinds.DESCEND` never appears in `FlySkillEvent.handleFlightMovement`,
  which is why RT descended on the ground and did nothing in the air.
- **DMZ lock-on** is `LOCK_ON.consumeClick()` → emulation works, so it rides our lock-on button.
- **Flight toggle** is `consumeClick()` inside an `InputEvent.Key` handler → unreachable, which is
  why the pad calls `FlySkillEvent.toggleFlightFromMenu()` instead.
- **Technique slots** go through `KeyBinds.isChordDown`, whose *both* halves are GLFW —
  `isBarModifierActive` → `KeyModifier.isActive()` → `Screen.hasAltDown()`, and `isPhysicallyDown`
  → `InputConstants.isKeyDown` whenever the slot has a key bound. **Unreachable by any emulation.**

## Ki technique bars on the triggers

Because that chord cannot be reproduced, `PadKiMenu` goes around it entirely:

| Input | Effect |
|---|---|
| hold **LT** | raises the bar for technique slots 1-4 |
| hold **RT** | raises slots 5-8 |
| **+ d-pad** (up/right/down/left) | `SelectTechniqueSlotC2S` then `TechniqueChargeC2S.start` |
| keep holding the trigger | `setHolding(true)` each tick — **the overcharge** |
| release the trigger | `setHolding(false)` — fires |

The bar is drawn by this mod's own `XenoTechniqueHotbarOverlay`, which ORs in
`XenoPadInput.kiBarOffset()` alongside the keyboard's Alt/Ctrl check. Firing uses DragonMineZ's own
packets — the same ones its GUI uses — so there is no second implementation of a technique.

`chase`, `backstep` and `sonic_left` moved from unlayered to the BASE chord layer, so a held
trigger stands them down and the d-pad becomes the selector. Unmodified they behave as before.

Standing down (leaving BT3 mode, opening a screen, unplugging the pad) **abandons** a charge rather
than releasing it: none of those is a player choosing to shoot.

## Button changes

| Button | Was | Now |
|---|---|---|
| **L3** | flight-mode toggle | descend while flying (`Options.keyShift`) |
| flight mode | L3 | radial only (it already had a "FLY" icon) |
| **LB** | our lock-on | our lock-on **+ DMZ's `LOCK_ON`** |
| **RT** | descend | unchanged — ground descend, and the ki bar for slots 5-8 |

## Editing the extra slots

Controlify's own screen still owns slots 1–8. Slots 9–20 are ours:

```
/xenobind radial                  list them
/xenobind radial add <id>         append a binding id
/xenobind radial remove <id>
/xenobind radial move <from> <to> one-based, as printed
/xenobind radial reset            back to the defaults
```

An empty stored list means *never configured* and takes `PadRadialSlots.DEFAULT_EXTRAS`; the first
edit starts from those defaults rather than from nothing, so adding one entry does not silently
delete the eleven that were already showing.

## Verified

```
gradlew.bat test -PofflineMcMeta            2123 tests, 0 failures
gradlew.bat build jarJar serverJar          BUILD SUCCESSFUL
gradlew.bat buildApiExampleAddon            BUILD SUCCESSFUL
dedicated server (isolated run dir)         started clean
```

## Not verified — needs a controller

A green build proves none of this:

1. The radial opens with more than 8 entries, evenly spaced and not overlapping.
0. Holding LT/RT raises the ki bar; d-pad starts a charge; holding overcharges; release fires.
0. Descend while flying works on L3, and ground descend still works on RT.
0. DMZ's lock-on engages from LB alongside ours.
2. Selecting a slot past 8 fires the right action.
3. Controlify's own radial config screen still opens, still shows 8, and still edits.
4. With Controlify absent the client starts normally and no `client/pad2` class loads.
5. `padRewrite=false` restores the old integration after a restart, binds intact.
6. BT3 mode still owns only its own inputs.
