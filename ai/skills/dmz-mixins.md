# Skill: DragonMineZ and Mixins

- Verify the exact `libs/dragonminez-2.1.3.jar` hash before reasoning from its behavior.
- Prefer source in `C:\dmz1` only when it exists and matches the jar; otherwise use
  `tools/generated/dmz_decompiled_full` and `javap` against the jar.
- Choose the narrowest injector that captures every intended route and no unrelated route.
- Document why the injection point is correct, including cancellation handoff semantics.
- Restart the game after reverting instrumentation; old JVMs continue executing loaded classes.
- Validate mixins in a fresh client/server run, not only through compilation.
