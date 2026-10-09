# Guidance V3 — missile guidance that keeps correcting

**Added:** 2026-09-28. **Select:** `/xenoguidance system v3` (server-wide, op level 2).
**Roll back:** `/xenoguidance system v1`.

V3 keeps V1's flight controller, HUD and planner screen. Only missile guidance changes, for both
**ship missiles** (Sable hulls launched by the VLS guidance computer) and **tube missiles**. Each
missile records the version when it launches, so switching versions never changes a missile
already in the air.

## Why

V1's `PURE_BALLISTIC` mode flew uncorrected, which the planner button labels "BALLISTIC (NO CORRECTION)":

- **Boost.** It aimed at a geometric apex point. Thrust was multiplied by factors the planner did
  not model, so when the motor cut off the arc did not lead to the target.
- **Coast.** It had zero thrust, and the nose only followed the velocity. Nothing corrected the miss.
- **Short shots.** A short ship never registered hitting the ground; it waited for the timeout
  (30 minutes or more).
- **Server lag.** A stall of more than 300 ms braked the hull and cancelled the arc.

## How V3 flies

Code lives in `missile/v3/` (pure Java, no `Level`), `vs/ShipV3Driver.java` (ships) and
`missile/v3/TubeV3.java` (tubes). Every tick it works from the missile's **actual world position
and velocity**.

| Phase | What happens |
| --- | --- |
| Rail | Out of the launcher along its axis until clear (tubes keep their existing rail) |
| Boost | Works out the velocity that carries the missile over the planned apex and down onto the target, with drag folded in by `ArcPredictor`, and pushes toward it. The motor cuts off when that velocity is reached; there is no fuel limit. |
| Coast | Predicts where the current arc lands and applies a bounded nudge that closes the gap in the time left. **Ballistic:** motor off, nudge up to half the engine's acceleration. **Guided:** full engine authority; a cruise altitude is held, then the missile dives. |
| Terminal | The same correction at full authority, inside 96 blocks or 60 ticks of impact (when terminal guidance is on) |

Settings still honoured: flight mode (Pure ballistic → V3 Ballistic, Guided boost-glide → V3
Guided), arc preference (Auto / High / Low), loft Y and cruise Y, terminal on/off, speed level
and boost, paired-thruster bonus, gravity, drag, the altitude ceiling (`missileMaxApexY`), and
retargeting by the auto-planner. No saved block data changed.

**Ships only:**
- A ship that touches terrain after its boost, or falls below the target height, detonates
  (reported as "V3 ground impact" or "V3 came down short").
- While coasting, a server stall keeps the last command instead of braking.
- The status line reads `V3 <phase> fuel=<ticks> a=<m/s²>`.

**Engine and reach (updated 2026-09-28).** There is **no fuel limit**: the engine burns as long
as the boost needs.

- **Speed level:** under V3 each level pushes twice as hard as V1's base value.
- **Paired thrusters:** each adds +25 %, with no cap.
- **Floor:** the engine never drops below 3 × gravity, so every shot at every speed reaches the
  target. The speed level only changes how fast it gets there.

**Commands** act on the guidance computer you are looking at, within 8 blocks, using the same
settings as its screen and CC `setSpeed` / `setFlightModel`:

| Command | What it does |
| --- | --- |
| `/xenoguidance computer` | Shows speed, gravity, drag and the V3 engine strength in m/s² and g |
| `/xenoguidance speed <1-20>` | Sets the speed level |
| `/xenoguidance gravity <m/s²>` | Sets gravity (0.01 to 100) |
| `/xenoguidance drag <k>` | Sets drag (0 to 0.01) |

## Tests

| Test | What it checks |
| --- | --- |
| `ArcPredictorTest` | Predicted landings match a tick-by-tick flight (short, lofted, 6.8 k-block, low-gravity arcs) |
| `GuidanceV3Test` | All 72 shots (200 to 30,000 blocks, speed 1, 10 and 20, both modes, all arcs) hit directly and none land near the launcher. A ±10 % velocity kick mid-coast still hits, while the same kick without correction misses by more than 50 blocks. Low gravity, high targets and low targets also hit. |
| `ShipV3DriverTest` | Ship shots at 300, 2,000 and 6,000 blocks in both modes, simulated with 3 physics substeps per tick. A 400 ms stall mid-coast still hits. An unreachable shot reports a ground impact. |
| `TubeV3Test` | Silo shots from 120 to 8,000 blocks, a deep silo and a cruise-altitude shot |
| `GuidanceVersionTest`, `GuidanceConfigPersistTest` | V3 is storable, is not the V2 flight stack, and flies V3 missiles |

V1 tests (`ShipBallisticControllerTest`, `MissileGuidanceTest`, the planner tests) are unchanged
and still pass.

## Not verified

These have not been checked in game:

- Sable's world gravity (assumed 10 m/s², as V1 assumes)
- How Sable substeps physics
- The ground probe under real hulls
- How V3 feels on very heavy ships

Before relying on it, fire a ship and a tube missile on a test world under V3.
