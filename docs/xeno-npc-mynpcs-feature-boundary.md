# Native Xeno NPC and MyNPCs Feature Boundary

**Reviewed:** 2026-09-21

**Historical snapshot.** Native dialogues, quests, factions, trades, transport, banks, jobs, and
path tooling were added after this review. Use the dated current-status document for implementation
decisions; the deferred list below describes the 2026-09-21 boundary only.

The root `doco.md` describes MyNPCs 1.5.0. Native Xeno NPCs are an independent implementation and do not claim full MyNPCs/CustomNPCs parity.

## Supported native foundation

| MyNPCs capability area | Native Xeno NPC status |
|---|---|
| Entity creation and role selection | Supported by the Xeno NPC Wand and six native roles. |
| Identity | Name, title, and faction are editable through one revision-checked transaction. Role, owner, and import source are displayed. |
| DMZ appearance | Hair enabled/code/color/style, race/form selection, aura state/color/scale, and renderer integration use the bounded Xeno/DMZ profile. |
| Combat statistics | STR, SKP, DEF, VIT, KI, and ENE are editable through the server-authoritative profile save path. |
| AI and combat brain | Brain v5 native scheduling and combat-brain enablement are supported. Brain v6 is limited to a verified decision framework. |
| Custom sounds | Five entity sound slots and pitch behavior are persisted; the editor picker reads the live sound-event registry. |
| Editor security | Ordinary saves use an explicit field/type allowlist. Lock/unlock uses a separate permission-, distance-, and revision-checked packet. |
| Persistence | Native entity/profile persistence and dimension-qualified death respawn anchors are supported. Respawn is deferred while the destination chunk is unloaded. |
| Legacy import | Additive import creates a separate Xeno NPC and does not mutate the source CustomNPCs/MyNPCs entity. |
| Quest/saga integration | Only existing schema-backed role metadata is supported. The editor must present this as informational unless a native writable schema exists. |

## Explicitly deferred

These `doco.md` feature families remain owned by MyNPCs/CustomNPCs and must not appear as editable native controls until a native schema, server validation, persistence, and runtime implementation all exist:

- Dialog trees, dialog availability, and dialog command execution.
- Quest creation/editing, quest objectives/rewards, and external quest start/completion mutation.
- Jobs: farmer, guard, builder, healer, follower, spawner, bard, item giver, and related advanced job configuration.
- Roles: trader inventories, banks, transporters, companions, mail, and Pokémon-specific integrations.
- Faction editing, faction standing, hostility matrices, and availability rules.
- Script authoring, script hooks, custom GUIs, overlays, and raw NBT editing.
- Path editor tooling, waypoint authoring, advanced navigation, model-part geometry, skins, animations, and equipment/drop editors.
- Cloning, global transport/database tooling, and administrative MyNPCs utilities.

## UI rule

Unsupported families may be shown as disabled reference controls when needed for screenshot-order
parity, or summarized as **Unavailable — requires native schema**. They must never produce save
payload fields, silently write placeholder data, or imply that MyNPCs data is being edited.

The screen/order contract is `XenoMyNpcsGuiOrderImages/docs.md`. The atlas rendering contract is
`docs/atlas-ui-doco.md`.

## Compatibility rule

CustomNPCs/MyNPCs retain ownership of their entities, wands, editors, data, and respawn semantics. The Xeno Wand edits only native `XenoNpcEntity` instances; legacy import remains explicit and additive.
