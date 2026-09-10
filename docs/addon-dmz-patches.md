# Addon DragonMineZ patches

XenoPixels addons may contribute one resource at:

`data/<addon_namespace>/xenopixels/dmz_patch.json`

The loader reads active server resources in deterministic resource-id and pack-id order. A malformed
patch stops the addon patch operation before XenoPixels reloads DragonMineZ configuration.

## Additive-only fields

- `formSkillsAdd`: new form-skill ids. Each id must have a `skillCosts` entry in the same patch.
- `skillOfferings`: master ids mapped to skills appended to their existing offerings.
- `exclusiveFormSkills`: masters for a form skill introduced by the same patch.
- `skillCosts`: definitions added only when DragonMineZ does not already have that skill id.
- `nonFormSkillOfferings`: ordinary skills appended to master offerings.

`formSkillsRemove`, unknown fields, overwrites, and cross-addon duplicate ownership are rejected.
Form-skill ids containing `super`, `god`, `legendary`, or `android` are rejected because
DragonMineZ remaps those form types. Every skill-cost definition needs a non-empty numeric `costs`
array; without it DragonMineZ silently calculates a maximum level of zero.

Before any startup containing third-party patches modifies DragonMineZ files, XenoPixels copies all
existing affected files under:

`config/dragonminez/.xenopixels-backups/yyyyMMdd-HHmmss/`

The final addon merge is written through a temporary file and an atomic replacement when the file
system supports it. Applying the same patch again is idempotent.
