# Create elevators from ComputerCraft

Drives a Create elevator from Lua. Ported from
[CC:LiftLink](https://github.com/tiktop101/CC-LiftLink) (MPL-2.0, 1.20.1/Forge) to 1.21.1 /
NeoForge; the peripheral type, method names and returned table keys are identical, so programs
written against CC:LiftLink run here unchanged.

Implementation: `compat/create/elevator/ElevatorMethods.java` (the Lua surface) and
`ElevatorHelpers.java` (column, contact and pulley lookups). Registered from `CcCompat`.

## Wiring it up

Attach a wired modem to an **elevator contact** — one of the redstone contact blocks on the
shaft, not the pulley. Any contact on the column works and they all report the same elevator, so
in practice you attach to whichever one is nearest the computer.

```lua
local lift = peripheral.find("create_elevator")
if not lift then error("no elevator contact on the network") end
```

The peripheral appears only when Create is installed. With Create absent the methods are simply
not registered — nothing errors at boot.

## Methods

Everything runs on the server thread, so calls are safe against live block entities but do cost
a tick's scheduling like any other main-thread CC call.

| Method | Returns |
| --- | --- |
| `getRole()` | `"contact"`. Present so a program can tell what it attached to. |
| `listFloors()` | Array of floor tables, bottom floor first. |
| `getState()` | One table with everything below. Use this when polling. |
| `getCabY()` | Live cab height as a number, interpolated between floors. |
| `getSpeed()` | Rope speed. `0` when parked. |
| `getOffset()` | Raw rope offset, or `nil` with no pulley. |
| `getTargetY()` | The Y being travelled to, or `nil`. |
| `isMoving()` | Boolean. |
| `getNearestFloor()` | Nearest floor to the cab plus its `distance`, or `nil`. |
| `callToY(y)` | Calls the cab to a Y. Returns `true`. |
| `callToFloor(name)` | Calls the cab to a named floor. Returns `true`. |

### Floor tables

`listFloors()` returns one of these per contact:

| Key | Meaning |
| --- | --- |
| `y` | The contact's world Y. This is the floor's identity everywhere in this API. |
| `shortName` | Create's short name, verbatim — an empty string when unnamed. |
| `longName` | Create's long name, verbatim. |
| `name` | Short name if set, else long name, else `nil`. |
| `currentFloorName` | What this contact is currently displaying, or `nil`. |
| `isTarget` | The cab is heading here. |
| `isCurrent` | The cab is reported to be here. |

`getNearestFloor()` returns `y`, `shortName`, `longName`, `name` and `distance` (blocks from the
cab; `0` when the cab height is unknown).

### `getState()`

| Key | Meaning |
| --- | --- |
| `modName` | Which mod is providing these methods. |
| `role` | `"contact"`. |
| `active` | Create's column-active flag. |
| `targetAvailable` | Create has a target set. |
| `targetY` | `currentTargetY` if the elevator is assembled, else the column's target. |
| `currentTargetY` | The contraption's live target, which leads the column's while moving. |
| `speed` | Rope speed; `0` when parked. |
| `offset` | Raw rope offset, or `nil`. |
| `cabY` | Live cab height, falling back to the reported floor's Y when parked. |
| `moving` | Rope speed when there is a pulley, else target differing from current floor. |
| `floorCount` | Number of loaded contacts. |
| `blockX`, `blockY`, `blockZ` | Position of the contact you are talking to. |
| `hasPulley` | Whether a pulley was found. |
| `trackingMode` | `"live_pulley"` or `"single_contact"`. |
| `currentFloorName`, `currentFloorY` | The floor the cab is reported at, or `nil`. |

## nil versus error

State that is merely **absent** returns `nil`. A disassembled elevator has no contraption, so no
speed, offset or interpolated cab height — that is a normal condition, not a fault, and a polling
program should not have to wrap every call in `pcall`.

An **error** means the request itself was bad:

- `No elevator column found for this contact` — the contact is not part of a column with any
  contacts on it.
- `No floor named 'X'` — no contact on the column carries that short or long name.
- `Y=N is not a real floor and is outside the elevator travel range` — see below.
- `Elevator contact is not in a loaded world`.

## Calling to a Y with no contact

`callToY` prefers the contact on that floor, so the call behaves exactly like pressing its
button — redstone output, floor display and target lock all update with it.

A Y with no contact also works, but only while the elevator is **assembled**: the contraption is
what knows the travel range, and without it a bad target would strand the cab. Create's own
bounds check decides. Outside the range you get the error above rather than a silent no-op.

## Example: call the lift and wait for it

```lua
local lift = peripheral.find("create_elevator")

local function callAndWait(floorName)
  lift.callToFloor(floorName)
  repeat
    os.sleep(0.25)
    local s = lift.getState()
  until not s.moving and s.currentFloorName == floorName
end

for _, floor in ipairs(lift.listFloors()) do
  print(("%3d  %-12s %s"):format(
    floor.y, floor.name or "(unnamed)", floor.isCurrent and "<- cab" or ""))
end

callAndWait("Lobby")
```

Note the `not s.moving` guard as well as the name check: the cab reports a floor as current the
moment it arrives at it, and a program that watches only the name can act while the lift is still
settling.

## Differences from CC:LiftLink

The Lua surface is identical. Three things behave differently underneath:

1. **The fallback pulley scan is bounded.** Upstream walks every Y in the world across a 7x7
   column of positions when the elevator is disassembled — roughly 19k block-entity lookups per
   call on a standard world, and around 200k on one using this repo's own `xeno_max_overworld`
   datapack, on the server thread, on every poll. The pulley always hangs above the cab and
   therefore above the topmost contact, so the scan is limited to the contact Y range plus 64.
2. **An entirely unnamed floor no longer throws.** Upstream builds `name` with
   `Objects.requireNonNullElse`, which throws when both names are null — the state a freshly
   placed contact is in. `name` is `nil` there instead.
3. **`getState().modName`** reports this mod rather than `CC:LiftLink`, since that field
   identifies which mod answered.

## Licence

The two ported files are MPL-2.0, like their upstream, and carry headers saying so; the rest of
XenoPixels Network is unaffected. See `THIRD_PARTY_NOTICES.md`.
