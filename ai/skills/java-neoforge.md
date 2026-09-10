# Skill: Java and NeoForge

- Use Java 21 features only; keep style consistent with the surrounding package.
- Confirm lifecycle bus and logical/physical side before registering listeners or loading classes.
- Keep optional integrations behind mod/class presence checks so absent mods do not link classes.
- Prefer existing registries, configuration loaders, and event patterns over new frameworks.
- Compile focused changes before broad tests; a successful compile does not prove game behavior.
