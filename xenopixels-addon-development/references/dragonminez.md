# DragonMineZ Integration

## Baseline

The exact XenoPixels profile compiles and runs against DragonMineZ 2.1.3 from
`libs/dragonminez-2.1.3.jar`. Confirm its configured SHA-256 before using any symbol. Package names,
inheritance, private fields, packet superclasses, screen layouts, and combat formulas must come
from the exact jar or tracked references.

## Additive-Only Rule

Third-party DMZ patches are additive by default. Safe additions include new content, gated behavior,
extra UI presentation, compatibility events, or data owned by XenoPixels. Deleting or overwriting
DMZ-owned data requires explicit owner approval and a migration, backup, conflict, and rollback
design.

Do not silently replace DMZ validation, progression, wishes, race selection, technique unlock math,
quest ownership, or inventory authority with client-authored XenoPixels data.

## Integration Shapes

Choose the least invasive supported shape:

1. Published XenoPixels or DMZ API.
2. NeoForge event or registry integration.
3. Additive data/resource integration.
4. Access through a verified compatibility adapter.
5. A narrow mixin against exact bytecode only when no stable hook exists.

Reflection is not automatically safer than a mixin. It still needs exact signatures, failure
handling, side gating, and tests.

## Mixins

Before writing or changing a DMZ mixin:

1. Inspect the target class in the exact jar.
2. Confirm owner, name, descriptor, inheritance, and side.
3. Prefer a semantic invocation or field access over a broad method head/tail injection.
4. Avoid ordinal-only targeting when a more stable slice or descriptor exists.
5. Decide whether failure should be required or optional and configure it deliberately.
6. Test startup with DMZ present and, when optional, absent.
7. Exercise the behavior in a fresh client or server process.

An inherited field or method may belong to a packet superclass rather than the named target class.
Verify the hierarchy instead of adding shadows until compilation stops failing.

## Combat and Stats

- Use DMZ's current server-owned stats and formulas when compatibility requires DMZ-equivalent
  damage, ki, stamina, defense, mastery, or transformation behavior.
- Do not invent approximations when the exact calculation is available in source or bytecode.
- Keep client HUD values descriptive; the server computes and applies gameplay results.
- Bound custom multipliers and reject non-finite values.
- Preserve reaction locks, cooldowns, target validity, and other fairness gates owned by the current
  combat path.

When XenoPixels NPCs or external addons trigger a DMZ technique, verify the technique category,
ownership, target requirements, resource cost, server execution path, and resulting synchronization.

## UI and Resources

- Preserve DMZ ownership of its gameplay validation even when XenoPixels replaces presentation.
- Use allow-listed bindings and actions for data-driven UI documents.
- Keep a stock-mode escape path when replacing DMZ screens.
- Remap only verified resource locations.
- Check GUI scale, mouse bounds, focus, narration, server packet authority, and missing-resource
  fallback.

Do not treat a screen that opens as proof that its save, purchase, quest, technique, or permission
packet is correct.

## Quests and Content

- Use namespaced identifiers for XenoPixels-owned quests and content.
- Re-read authoritative server data when a client selects a dialogue or quest option.
- Do not fake acceptance of a DMZ- or NPC-owned quest when the owning NPC or system must grant it.
- Preserve existing player progress during format changes; use additive migration and explicit
  fallback behavior.

## Validation Checklist

- Exact DMZ jar name and SHA-256 confirmed.
- Target class and descriptor verified from the jar.
- Client-only references absent from dedicated-server paths.
- DMZ-present startup checked.
- DMZ-absent startup checked when the dependency is optional.
- Focused unit or serialization tests pass.
- Distribution tasks pass.
- The gameplay action is observed in a fresh runtime.
- Logs contain no new mixin target, packet decode, registry, or classloading failures.
