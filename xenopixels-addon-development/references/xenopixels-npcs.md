# Native XenoPixels NPCs

## Keep Native NPCs Separate from Compatibility NPCs

XenoPixels-owned NPC entities, data, editor flows, dialogue, quests, roles, and combat behavior form
a native subsystem. MyNPCs and CustomNPCs bridges are compatibility layers. Do not make native NPC
data depend on a third-party editor or assume a compatibility wrapper is the source of truth.

Current project notes identify subsystem names such as `XenoNpcEntity`, `XenoNpcSavePolicy`,
`XenoNpcDialoguePacket`, `XenoNpcRoleBehaviour`, `XenoNpcLinkPropagation`, and
`NpcXenoScriptApi`. Locate and verify the current classes before changing them; names in this
reference are navigation hints, not a substitute for source inspection.

## Server Authority

The server owns:

- entity identity, ownership, editor locks, and permissions;
- accepted profile keys and value types;
- dialogue and quest definitions;
- quest progress and rewards;
- role behavior and targeting decisions;
- combat stats, forms, techniques, cooldowns, and damage;
- linked-NPC propagation and persistent saves.

The client may render an editor and request changes. It must not send an arbitrary profile blob that
the server stores without per-key validation.

## Editor and Save Policy

Use an allow-list such as the current save-policy layer rather than a deny-list. For every editable
key define:

- accepted tag or wire type;
- numeric, string, list, and nesting bounds;
- permission and ownership requirements;
- whether the key is visual, behavioral, combat-sensitive, or server-only;
- normalization and migration behavior;
- whether a linked-NPC save propagates it.

Keep editor lock/unlock as an explicit server operation. Handle disconnects, entity removal, stale
screens, dimension changes, and timeout recovery so an abandoned lock cannot strand an NPC.

## Dialogue

- Send stable identifiers, not authoritative dialogue actions authored by the client.
- Re-read the selected dialogue and option from the server's current datapack or persistent data.
- Validate NPC identity, player distance, visibility rules, conditions, cooldowns, and permissions.
- Execute commands, quest operations, and rewards with the intended server source and permission
  level.
- Bound text, option counts, nesting, and any placeholder expansion.
- Keep fallback rules explicit when an NPC-specific dialogue and a role/default dialogue coexist.

Test dedicated-server use; a dialogue UI working in an integrated client does not prove server-safe
packet handling.

## Quests

Quest definitions should declare requirements and rewards while the server owns progression.
Validate objective identifiers and counts, make reward application idempotent, and define behavior
for missing or changed datapack entries. Never trust a client completion flag.

For bridges to DMZ or MyNPCs quests, preserve the owning system's acceptance and completion rules.
Use adapters that translate identifiers and observations rather than duplicating the other mod's
entire quest state.

## Roles, Brain, and Targeting

Centralize predicates shared by AI decisions and target selection so an NPC cannot decide one role
state while its target goals assume another. Role changes should refresh the minimum necessary goals
or state and must not leak old hostile targets, cooldowns, owners, or navigation modes.

Test:

- peaceful, defensive, hostile, trainer, quest-giver, and scripted roles used by the project;
- owner/allied-player exclusions;
- invalid or removed targets;
- dimension and range changes;
- save/load and role migration;
- server restart behavior.

## Combat Profiles and DragonMineZ

- Keep profile persistence separate from live DMZ-derived stats when both exist.
- Synchronize through one authoritative conversion path with clear invalidation/fingerprint rules.
- Use verified DMZ formulas and technique dispatch paths.
- Clamp release percentages, multipliers, colors, sizes, speeds, ranges, and cooldowns.
- Clear transformed, charging, aura, target, and temporary combat state when the owning condition
  ends.
- Do not let a renderer or editor value directly apply server damage.

## Scripting Surface

Treat the script API as public input even when scripts are server-owned. Verify overloads against
the current source, validate entity/world state, clamp arguments, and return useful failures rather
than throwing from a game tick. Avoid exposing mutable internals or unrestricted packet, command,
filesystem, reflection, or classloading access.

When adding an overload, keep previous signatures functional and add tests for dispatch ambiguity,
wrong argument types, null entities, unloaded worlds, and dedicated-server execution.

## Packet Review

For each NPC packet verify:

1. Direction and registration namespace.
2. Payload bounds and decode failures.
3. Sender, permission, distance, dimension, and entity type.
4. Editor lock or current interaction state.
5. Server-side lookup of dialogue, quest, role, profile, and registry data.
6. Atomic persistence and synchronization.
7. Behavior when the NPC disappears before handling.

## Focused Tests

- Save-policy accepted/rejected keys and maximum counts.
- Editor lock acquisition, conflict, disconnect, and release.
- Dialogue identifier replay and stale-option rejection.
- Quest completion idempotency and missing-definition behavior.
- Linked-NPC propagation boundaries.
- Role predicate agreement with target selection.
- Combat-profile serialization, clamping, and DMZ conversion.
- Script overload resolution and bad arguments.
- Dedicated-server classloading for every common packet path.
