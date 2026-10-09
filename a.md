# NPC Import and DMZ Data-Only Inventory

**Evidence date:** 2026-09-24  
**Scope:** placed MyNPCs/CustomNPCs migration, native Xeno NPC profile, DragonMineZ 2.1.3 integration, and editor controls.

This inventory distinguishes four separate claims: source data exists, Xeno persistence exists, a native server schema exists, and a runtime consumer has been observed. A copied tag or passing unit test is not gameplay proof.

## Active native owners

| Data / feature | Current owner | Native server schema | Runtime consumer | Status |
|---|---|---:|---|---|
| Six DMZ primary stats (`STR`, `SKP`, `RES`, `VIT`, `PWR`, `ENE`) | `NpcCombatProfile` -> `NpcDmzStats` | Yes | DMZ `StatsData` formulas, attributes, health, melee/strike paths | Implemented in source; fresh gameplay still requires runtime verification |
| Race, class, active form, stack form | `NpcCombatProfile`, `NpcFormLookup`, `NpcDmzStats` | Yes | DMZ stat scaling, form attributes, renderer | Implemented in source; runtime proof boundary remains |
| DMZ appearance parts and hair | `NpcDmzAppearance`, `NpcCombatProfile` | Yes | `NpcAppearancePacket`, `NpcFullDmzRenderer` | Implemented in source; runtime proof boundary remains |
| Player skin identity (`SkinPlayer`, `SkinUrl`) | `NpcCombatProfile`, `NpcAppearancePacket` | Yes | Packet state and proxy skin contract; URL loading depends on existing client resolver | Transported; verify actual skin appearance in a fresh client |
| Native gear | `NpcGear` | Yes | `Mob` equipment slots and inventory menu | Implemented; source armor/hand lists now migrate |
| Source snapshot | `NeoForgeData/XenoPixelsSource` | Yes | Migration audit/rollback data only | Preserved data; intentionally not a gameplay consumer |
| DMZ health | `NpcVitalitySync` / `NpcVitalityMath` | Yes | `MAX_HEALTH` attribute and counterpart health | Formula covered by tests; fresh runtime observation remains required |
| Form movement and attack speed | `NpcFormAttributeSync` | Yes | Native attributes | Implemented in source; runtime proof boundary remains |

## Imported but inactive or partially emulated values

| Source data | Intended feature | Current consumer | Persistence | Native schema | Runtime behavior | Required implementation |
|---|---|---|---:|---:|---:|---|
| DMZ `BonusStats`, effects, and secondary-stat modifiers | Player bonus/stat effects | Opaque `XenoPixelsSource` snapshot only | Yes | No NPC-safe owner proven | None | Add an NPC-safe, server-authoritative modifier owner only after exact DMZ API and null-safety verification |
| DMZ current resources (`CurrentEnergy`, stamina, poise, release state) | Resource bars and technique costs | `Energy`/power-release profile fields only; current pools are not imported as active state | Partial | No complete NPC resource schema proven | Not claimed | Define NPC resource attachment, tick rules, costs, persistence, packet sync, and consumers |
| DMZ player quest data and progression | Player quests/saga progression | Source snapshot only | Yes | No NPC quest owner | None | Keep player-only; do not emulate on NPCs without an explicit owner and lifecycle |
| DMZ effects/cooldowns/skills | Player status and skill state | Source snapshot only; Xeno technique/profile fields are separate | Yes | No complete NPC-safe mapping | None | Map only verified NPC-safe effects with explicit ownership and cleanup |
| DMZ defense and armor-derived regeneration | Defense/regen formulas | `NpcCombatProfile` has native resistance/regen controls, not player armor APIs | Yes for Xeno fields | Player-only DMZ methods are refused by `NpcDmzStats.safeForNpc` | Not claimed as player parity | Add a native armor/stat adapter or leave this field inactive |
| Fusion, party, gravity, time-chamber, mount, shelter, and player-only status values | DMZ player systems | Source snapshot only or separate Xeno fields where available | Snapshot preserved | No native schema for imported player state | None unless an Xeno owner is named | Implement as separate features; never infer behavior from copied NBT |
| Full source model metadata not represented by `modelKind/modelId/modelTexture` | Source model fidelity | Source snapshot; known texture maps to `modelTexture` | Yes | Partial | Texture path only where renderer accepts it | Add an explicit model adapter per source model type and renderer contract |
| Curios source slots | Curios equipment | Native `NpcCurios` owns Xeno Curios routing; source Curios payload is not translated by this migration | Source snapshot | Xeno Curios schema exists, source-to-slot mapping not verified | Not claimed | Verify slot identifiers and item serialization, then map with server validation |

## Editor controls that must remain unavailable

| Control | Data/schema evidence | Runtime consumer | Why unavailable | Required implementation |
|---|---|---|---|---|
| Display: Select Texture | `modelTexture` string exists | Direct field editing already works | No native texture picker/catalog registry | Add an allow-listed server-known texture catalog and packet validation |
| Display: Cape / Overlay / Showing Layers | Source fields may exist | No native Xeno renderer layer owner | No complete schema and renderer behavior | Define layer schema and renderer consumers |
| Display: Availability | MyNPCs condition concept | No native availability evaluator | No server schema/runtime predicate | Add server-owned condition model, evaluator, persistence, and editor |
| Alternate/night profile | `nightTexture` is active; alternate profile rows are not | No complete alternate profile state | Only night texture has a consumer | Add versioned alternate profile schema and all state consumers |
| Marks: catalog and per-mark availability | Single mark fields exist | Single native mark renderer only | List/condition owner absent | Add mark catalog, availability evaluator, sync, and renderer list semantics |
| Role: Job Enabled | Job enum and specific job consumers exist | No separate enable flag consumer | A toggle would be silently ignored | Either remove the row or add a server-owned job enable policy |
| Guard per-type target lists | Guard role/faction targeting exists | No per-type allow-list consumer | Source control has no native equivalent | Add validated entity-type allow/deny schema and target selector |
| Inventory: Projectile slot | Ranged profile fields exist | No generic inventory projectile slot consumer | No ranged attack goal/slot owner | Add a projectile item schema and firing owner, or keep explanatory text |
| Global recipes/natural spawns | Editor placeholders | No complete native registry/runtime owner | Persistence/runtime contract incomplete | Add server registries, validation, sync, and spawn/recipe consumers |
| NPC-to-NPC conversation lines | Line storage exists | Conversation job/runtime path absent | No runtime conversation owner | Implement conversation job and authoritative interaction state |

## Historical documentation boundaries

`docs/xeno-npc-parity-2026-09-23.md`, `docs/xeno-npc-mynpcs-feature-boundary.md`, older handoffs, and plans are historical evidence unless confirmed against current source and a fresh runtime. The importer design explicitly marked unloaded entities, banks, transport, spawns, recipes, linked data, unsupported quest kinds, and unsupported dialogue actions as out of scope; this file does not relabel those planned or copied fields as active.

The current importer now creates a native `NpcCombatProfile` during placed conversion, maps verified DMZ/appearance/model/gear fields, and stores the complete pre-conversion entity tag under `NeoForgeData/XenoPixelsSource`. Unsupported values remain preserved and are listed above rather than silently discarded.
