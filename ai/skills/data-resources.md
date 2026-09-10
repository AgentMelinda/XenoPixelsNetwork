# Skill: Data and Resources

- Put authored data under `src/main/resources/data/<namespace>` and generated output under
  `src/generated/resources`.
- Validate JSON shape and semantic traps before writing external configuration.
- Third-party `xenopixels/dmz_patch.json` files are additive-only: no deletes or overwrites.
- Preserve deterministic resource order, reject duplicate ownership, and keep repeated startup
  idempotent.
- Back up affected DragonMineZ files before mutation and use temporary/atomic replacement.
- Treat data generation and world/resource reloads as side-effectful validation; inspect output.
