# NPC Compatibility and Native DMZ Editor Design

## Intent

Repair the DMZ hair regression for CustomNPCs and MyNPCs, restore their native wand creation behavior, and give the Xeno NPC wand a complete, independent editor for native Xeno NPCs. The native editor should be organized like CustomNPCs/MyNPCs (categories, orders, settings, and NPC controls) while using a DMZ-style visual language.

## Constraints

- Minecraft 1.21.1, NeoForge 21.1.248, Java 21, DragonMineZ 2.1.3.
- CustomNPCs, MyNPCs, and the Gecko addon remain optional integrations.
- Existing dirty working-tree changes are preserved.
- `api/**` remains compatible.
- Existing packet/channel invariants remain stable.
- Runtime claims require fresh client/server evidence.

## Selected approach: compatibility-first adapters plus an independent native editor

The narrowest safe compatibility strategy is to preserve host-mod wand and editor ownership, repair shared appearance state through existing optional adapters, and build the Xeno editor as its own screen and data contract. A unified replacement editor would risk host compatibility; a thin wrapper would not meet the requested organization.

## Components

### Hair state and rendering

- Use the bounded `NpcCombatProfile` plus appearance synchronization as the Xeno source of truth for Xeno-managed DMZ values.
- Treat CustomNPCs/MyNPCs model data as optional mirrors, never destructive replacements of host appearance.
- Sanitize hair code, color, style, and enabled state before persistence or packet emission.
- Keep separate, verified CustomNPCs and MyNPCs renderer adapters, and keep the Gecko custom-model path separate from humanoid paths.
- If Xeno data is absent, invalid, or cannot render, preserve the host/default hair and leave the host renderer running.

### Third-party wand compatibility

- Do not intercept, cancel, or replace CustomNPCs/MyNPCs wand interactions.
- Restrict the Xeno wand editor action to native `XenoNpcEntity` targets.
- Preserve additive legacy import: importing creates a new Xeno NPC and never mutates the source NPC.
- Optional integrations fail closed when absent or when an exact verified target is unavailable.

### Independent native Xeno editor

The Xeno NPC wand opens an independent editor screen through the existing open-editor packet. It is not a host-mod screen and does not route native NPCs through CustomNPCs/MyNPCs internals.

The editor uses a DMZ-style panel, tabs, colors, and button treatment, but follows CustomNPCs/MyNPCs-style organization:

- **General:** name, faction, role, owner, display options.
- **Appearance:** hair, skin/model selection, colors, aura, form preview.
- **Stats/Combat:** combat profile, attributes, targeting and behavior toggles.
- **Orders/Brain:** follow, guard, wander, attack/defend, schedules, priorities, cooldowns, and Brain v5 controls.
- **Dialogue/Quests:** dialogue hooks and quest/saga references only where native schema exists; otherwise explicit unavailable/read-only state.
- **Inventory/Equipment:** native equipment and drop/order settings where supported.
- **Settings:** permissions, interaction range, persistence, import provenance, reset-safe defaults.

The first implementation must make General and Appearance functional, establish the tab shell and navigation for all categories, and add Orders/Brain using the existing native brain data contract where it is already verified. Unsupported fields must not be fake-saved.

### Server authority

- Client edits a local draft and previews it.
- Save packets contain bounded fields and the entity revision.
- The server checks permission, entity type, distance/ownership rules, payload lengths, and revision before applying changes.
- Stale or unauthorized saves are rejected without partial writes.
- Successful appearance changes synchronize to clients and optional host mirrors.

## Data flow

1. Player uses the Xeno wand on a native Xeno NPC.
2. Server sends the current bounded native profile and revision.
3. Client opens the independent DMZ-styled, CustomNPCs/MyNPCs-organized editor.
4. Client edits a local draft in the selected category.
5. Server validates and persists a save packet.
6. Server synchronizes appearance/brain state and optional mirrors.

## Failure handling

- Missing optional mod: native Xeno functionality continues; adapter is skipped.
- Missing/changed host target: skip only that adapter and log concise debug information.
- Invalid hair value: ignore it and retain host/default appearance.
- Missing DMZ asset: use the existing native panel fallback without breaking interaction.
- Unsupported editor field: render as unavailable/read-only, never claim persistence.
- Stale/unauthorized save: reject and notify the player.

## Validation

- Focused tests for hair sanitization, profile fallback, style bounds, packet limits, native editor serialization, and revision rejection.
- `compileJava`, focused tests, full tests, distribution/API builds as applicable.
- Fresh startup and packet-opening runtime evidence.
- Manual matrix: CustomNPCs wand creation, MyNPCs wand creation, hair on both humanoid paths and Gecko path where installed, native Xeno editor opening, General/Appearance save, Orders/Brain behavior, and startup with optional mods absent.

## MyNPCs feature boundary

The audited support/defer matrix is maintained in `docs/xeno-npc-mynpcs-feature-boundary.md`. Native controls are enabled only where Xeno has a bounded schema, server validation, persistence, and runtime behavior. Unsupported MyNPCs/CustomNPCs families are omitted or labeled unavailable and never serialized as placeholder fields.

## Non-goals

- Replacing CustomNPCs/MyNPCs editors or wand behavior.
- Inventing DragonMineZ entity, quest, saga, or host APIs.
- Persisting unsupported quest/forms/skills fields without a verified native schema and packet contract.
- Broad reflective compatibility with unknown versions.
