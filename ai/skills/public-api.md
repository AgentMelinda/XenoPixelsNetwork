# Skill: Public Addon API

- Everything under `net.bullettrain.xenopixelsmod.api` is compatibility-sensitive.
- Make additive changes within API generation 1; increment only for a real breaking change.
- Expose XenoPixels-owned types where possible and state unavoidable DMZ dependencies plainly.
- Keep cancellation timing, thread/side, nullability, fallback, and synchronization behavior in
  Javadoc and tests.
- Build `examples/xenopixels-api-addon` against the packaged jar after every API change.
- Do not claim an event works in game until the example observes the real production hook.
