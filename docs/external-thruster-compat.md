# External thruster compatibility

The VLS guidance computer can pair propulsion blocks from other mods with **Pair Nearby Thrusters**.
Recognition is dependency-free: removing an optional propulsion mod does not leave class-loading links or crash the game.

Built-in namespace/path matching covers Create: Propulsion (`createpropulsion`), Starlance (`vsch`), Clockwork,
Genesis, Zero Point Systems/ZPS, and Warium. Recognized block paths contain an engine term such as `thruster`,
`propeller`, `rocket_engine`, `jet_engine`, `ion_engine`, `engine_nozzle`, or `propulsion`.

## Adding any other engine

Add its blocks to the standard block tag `xenopixelsmod:compatible_thrusters` in a datapack:

```json
{
  "replace": false,
  "values": [
    "another_mod:plasma_drive",
    "another_mod:large_plasma_drive"
  ]
}
```

Place that file at `data/xenopixelsmod/tags/blocks/compatible_thrusters.json`, reload datapacks, and run
**Pair Nearby Thrusters** again.

## Control model

- The source mod retains ownership of its native VS force and redstone behavior.
- Guidance uses its center-of-mass force and quaternion attitude controller for accuracy and stability.
- A public block-entity `setThrottle`, `setThrottleLevel`, `setPower`, or `setPowerLevel` method is used when one exists.
- If no public throttle method exists, pairing still supplies engine count and launch direction; native redstone remains usable.
- Direction is read from a normal `facing`, `direction`, or `orientation` block-state property. Missile body calibration
  remains the authoritative nose axis after launch.
