# Skill: Client UI and Assets

- Keep client-only imports and subscribers isolated from dedicated-server class loading.
- Preserve existing HUD atlases, animation timing contracts, texture namespaces, and scaling rules.
- Do not delete or recompress tracked UI source bundles unless the task explicitly replaces them.
- Verify asset paths and dimensions from files rather than guessing.
- Test affected screens or animations in a fresh client and separate unrelated shader/Veil warnings
  from regressions caused by the change.
- For the generated panel atlas, its widget kit, and the ScaledScreen coordinate rules, read
  `docs/atlas-ui-doco.md` before touching `client/ui/atlas/` or any screen that uses it.
  Sprites there are blitted 1:1 and never stretched; a missing size is generated through
  `tools/atlas-panels/xeno_extra_specs.py`, not scaled at runtime.
