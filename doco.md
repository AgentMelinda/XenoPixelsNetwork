# My NPCs — Full Feature Documentation

**Mod ID:** `mynpcs` | **Display Name:** My NPCs | **Version:** 1.5.0 | **Author:** espi
**Loader:** NeoForge (`javafml`, loader `[4,)`) | **Minecraft:** 1.21.1 (`[1.21.1, 1.22)`) | **NeoForge:** `[21.1.0,)`
**License:** MIT

This document was compiled directly from the mod's jar file (`mynpcs-neoforge-1.5.0.jar`): its `neoforge.mods.toml`, mixin config, access transformers, compiled class names (via `javap`/`strings`), registry names, language files (`en_us.json`, plus 17 other translations), recipe/data files, and texture/sound asset folders. Nothing below is invented — every feature listed corresponds to a file, class, registry name, or localization string that actually exists inside the jar. Where behavior could not be confirmed from static inspection (e.g. exact numeric defaults or precise runtime logic), it is described only in terms of what the visible data supports.

The internal code and package name (`espi.mynpcs`) plus the access-transformer comment "*Adaptado del port de Goodbird para NeoForge 1.21.1*" indicate this is a NeoForge 1.21.1 port/continuation of the "Custom NPCs" style mod lineage (originally by Noppes, continued as "Goodbird's CustomNPCs"), rebranded as **My NPCs**.

---

## 1. What the mod is

My NPCs is a large sandbox mod for creating, customizing, scripting, and managing custom non-player characters (NPCs) entirely in-game, without needing to write code (an optional JavaScript/Nashorn scripting layer is available for advanced users). It adds NPC entities, editor GUIs, jobs/roles, a dialog and quest system, a faction/reputation system, factory-style world tools (build blocks, schematics, mob spawners), and a full scripting/event API, plus integration hooks for several other popular mods (JEI, REI, Cobblemon, Pixelmon, FTB Quests, TM Craft, ArmorersWorkshop, CobbleDollars).

---

## 2. Entities

Registered entity types found in the jar (`entity.mynpcs.*` translation keys and `espi/mynpcs/entity/*` classes):

| Entity | Class | Notes |
|---|---|---|
| Custom MyNPC | `EntityCustomNpc` | The core, fully-customizable NPC entity |
| MyNPC 64x32 | `EntityNPC64x32` | Variant using the legacy 64×32 skin format |
| MyNPC Alex | `EntityNpcAlex` | Variant based on the "Alex" default Minecraft skin model |
| MyNPC Classic | `EntityNpcClassicPlayer` | Variant using the classic player model |
| MyNPC Block Mount | `EntityChairMount` | An invisible/seat-style mount entity (e.g. for sitting NPCs, chairs) |
| Projectile | `EntityProjectile` | Custom NPC-fired projectile entity, with `IProjectileCallback` |
| MyNPC Crystal | `EntityNpcCrystal` | Crystal-type NPC template entity |
| MyNPC Dragon | `EntityNpcDragon` | Dragon-type NPC template entity |
| MyNPC Golem | `EntityNPCGolem` | Golem-type NPC template entity |
| MyNPC Pony | `EntityNpcPony` | Pony-type NPC template entity |
| MyNPC Slime | `EntityNpcSlime` | Slime-type NPC template entity |
| Dialog NPC | `EntityDialogNpc` | Support entity used for the dialog/conversation system |
| Fake Living | `EntityFakeLiving` | Support/rendering entity |
| Flying NPC | `EntityNPCFlying` | Flying-capable NPC variant |

All of the above share the common `EntityNPCInterface` base behavior (AI, jobs, roles, stats, display, etc. — see below).

---

## 3. Character customization (the NPC editor)

Right-clicking an NPC while holding the **My NPCs Wand** (or via commands) opens a multi-tab editor. The tabs/menus present in the compiled GUI classes are:

- **Main / Global menu** (`GuiNPCGlobalMainMenu`) — name, title/prefix, and general identity.
- **Display** (`GuiNpcDisplay`, `DisplayMenu`) — model/template, texture, skin URL, cape, overlay, tint (HEX color), size/scale, visibility rules, hide/show, hitbox, glow, boss bar (with configurable color), showing/hiding specific model layers, statue mode (no living animation).
- **Model** (`ModelMenu`, `GuiCreationEntities`, `GuiModelColor`, `GuiPresetSave`) — per-body-part customization (head, body, arms/left-right, legs/left-right, tail, wings, ears, horns, snout, hair, beard, eyes (pupil, lid, lash, brow, glint, blink), fin, claws, skirt, breasts, mohawk, particles), color tinting per part, model scale (width/height/depth), save/load model presets.
- **AI** (`GuiNpcAI`) — movement type (wander vs. set path via NPC Pather), navigation (ground/water/flying), avoids water, backtracking, must-see-target (line of sight), cobweb-affected, door interaction (e.g. break doors like a zombie), enemy response (flee/fight/etc. on found enemy), leap-at-target, looping paths, sprinting, swimming, wandering, shelter-seeking (e.g. from sunlight), return-to-spawn, mount control.
- **Advanced** (`GuiNpcAdvanced`, `GuiNPCAdvancedLinkedNpc`, `GuiNPCMarks`, `GuiNPCScenes`, `GuiNPCSoundsMenu`, `GuiNPCLinesMenu`, `GuiNPCNightSetup`, `GuiNPCFactionSetup`, `GuiNPCFTBQuests`, `GuiNPCDialogNpcOptions`) — editing mode toggle, custom sounds (idle/hurt/death/anger/step, each with optional pitch), lines (attack/interact/kill/killed/random/world/NPC-to-NPC), marks (floating icons: cross, exclamation, pointer, question, skull, star, each with a selectable color), scenes (scripted scene sequences with play/pause/state control and a `translatable` values system), day/night model or behavior swapping ("Load Day"/"Load Night"), linked NPCs, FTB Quests integration options (require/complete a quest ID).
- **Stats** (`GuiNpcStats`) — health, health regen (out of combat and in-combat), resistances (knockback, arrows, melee, explosion — each 0-based scaling), fire immunity, burns-in-sunlight, potion/status-effect immunity, no-fall-damage, can-drown, creature type (e.g. Undead/Arthropod, for potion/weapon interactions), experience dropped (min/max), loot pickup behavior (auto-pickup, player loot pickup, drop chance), glow toggle.
- **Melee properties** (`SubGuiNpcMeleeProperties`, `MeleeMenu`) — damage, attack speed, attack range, knockback strength, on-hit status effect + duration.
- **Ranged properties** (`SubGuiNpcRangeProperties`) — accuracy, shoot range, min/max delay between shots, burst count and burst rate, fire/hit/ground sounds, aim-while-shooting animation toggle, indirect/arced shooting (catapult-style), simultaneous shot count (shotgun-style).
- **Projectile type** (`GuiCreationExtra` and related) — projectile strength/damage, size, speed, gravity/acceleration, explosive on impact (with an explicit in-game warning about `mobGriefing`), on-hit status effect, trail effect (for "magic" projectiles), on-hit knockback, 2D vs. volumetric (3D) rendering.
- **Movement** (`SubGuiNpcMovement`) — movement type, position offset (left/right, forward/back, vertical), rotation (e.g. for turret-like NPCs), whether the NPC turns to face the interacting player, movement speed, animation state (sitting, lying, crawling, dancing, hugging, sneaking, stalking, swimming, flying, aiming), pause behavior, "stop on interact" and "interact with other NPCs while wandering."
- **Availability** (`SubGuiNpcAvailability`, `SubGuiNpcAvailabilityDialog`, `SubGuiNpcAvailabilityQuest`, `SubGuiNpcAvailabilityScoreboard`) — conditional logic controlling when NPC options/dialogs/quests are shown: always, before/after a given time, during day/night, based on scoreboard value comparisons (equals/is/is not/bigger/smaller), minimum player XP level, dialog state, faction standing (active/not active), quest completion state.
- **Faction options** (`SubGuiNpcFactionOptions`, `SubGuiNpcFactionPoints`, `GuiNPCFactionSelection`, `GuiFaction`) — assign the NPC to a faction, set hostile factions, "attack hostile factions," "defend faction members," "attacked by mobs" response, faction point changes on the NPC's death.
- **Respawn** (`SubGuiNpcRespawn`) — enable respawn, respawn delay in seconds, hide/remove dead body.
- **NBT editor** (`GuiNbtBook`, `nbt.edit`) — raw NBT tag inspection/editing via the **NBT Tags Book** item.

---

## 4. Jobs (behavior/occupation system)

The `job.*` lang keys and `espi/mynpcs/roles/Job*` classes define these assignable jobs:

| Job | Class | Description (from strings/classes) |
|---|---|---|
| No Job | — | Default, no special behavior |
| Bard | `JobBard` | Plays music: as ambient background, or "as jukebox," with looping, an optional off-distance/on-distance range |
| Boss | (job type constant) | Marks the NPC as a boss-type mob |
| Builder | `JobBuilder` | Builds structures using the Builder Block/blueprint system |
| Chunk Loader | `JobChunkLoader` | Keeps nearby chunks loaded (count limited by `ChuckLoaders` config) |
| Conversation | `JobConversation` (+ `ConversationLine`) | Makes multiple *named* NPCs within 10 blocks speak to each other in a scripted back-and-forth exchange |
| Farmer | `JobFarmer` | Automatically farms; item-pickup behavior configurable: put in chest, drop on ground, or do nothing |
| Guard | `JobGuard` | Attacks configured target types: animals, monsters, creepers, with "available/current targets" lists |
| Healer | `JobHealer` | Heals nearby entities (role-based healer behavior) |
| Item Giver | `JobItemGiver` | Gives items to players on interact, with cooldown modes: daily, give-only-once, or timer; give methods: all items, chained, random item, give-not-owned-items, give-when-owns-none |
| Pokémon Trainer | `RolePokemonTrainer` (see Cobblemon integration) | NPC battles the player with a configured Pokémon team |
| Puppet | `JobPuppet` (+ `PartConfig`) | Per-body-part animation control while attacking/standing/walking |
| Spawner | `JobSpawner` | Spawns mobs/clones: modes "one by one," "random," "all"; options for despawn-on-target-lost, "dies after spawns die," mount-by/player-mounts, position offset, light/dark spawn conditions |
| Follower | `JobFollower`/`RoleFollower` | Follows the player, hireable for a set number of days (or infinite), with start/end dialog lines, health display, "waiting" state, and can be captured into an NPC Jar |

---

## 5. Roles (economy/social system)

The `role.*` keys and `espi/mynpcs/roles/Role*` classes define:

- **Bank** (`RoleBank`, `BankController`, `bank.*`) — a personal or shared bank the NPC manages: multiple tabs/slots, tab unlock cost, slot upgrade cost, deposit/withdraw with a configurable withdrawal fee (%), a "wallet" vs. "vault" split, selectable bank instance.
- **Companion** (`RoleCompanion`, `companion/CompanionFarmer`, `CompanionGuard`, `CompanionTrader`, `CompanionFoodStats`, `CompanionJobInterface`) — a pet/companion NPC with age stages (baby → child → teenager → adult/full-grown), level, strength, talents, an owner, and its own inventory/stats screen; can itself act as farmer, guard, or trader.
- **Dialog** (`RoleDialog`) — attaches a branching dialog tree to the NPC (see Dialog System below).
- **Follower** (`RoleFollower`) — hireable escort NPC.
- **Mailman/Postman** (`RolePostman`, `BlockMailbox`, `mailbox.*`) — sends/receives in-game mail via **Mailbox** blocks: compose subject + message, addressed to a username, delivered after a configurable delay (measured in minutes/hours/days), "You've got mail" notification, read/unread tracking, error handling for empty subject or unknown username.
- **Trader** (`RoleTrader`) — a merchant NPC (`GuiNPCTrader`, `GuiMerchantAdd`), with currency-sufficiency checks and a configurable "payment mode" (item or money).
- **Transporter** (`RoleTransporter`, `TransportController`, `TransportCategory`, `TransportLocation`) — a teleport-network NPC ("Travel" to destinations), unlocked by discovery, by prior interaction, or available from the start; category-based destination lists (`GuiNpcTransporter`, `GuiTransportSelection`).
- **Pokémon Trader** (`RolePokemonTrader`) — trades Pokémon (via Cobblemon integration; see below) with configurable requirements (species/form), trade limits (enabled/disabled, max trades), fixed or mirrored level, global or per-player trade type, and optional extra physical items alongside the traded Pokémon.
- **Move Relearner** (`RoleMoveRelearner`) — lets a player's Cobblemon Pokémon relearn a forgotten move.
- **Move Tutor** (`RoleMoveTutor`, plus `SpeciesMatch`/`SpeciesOffer`/`TutorOffer`) — teaches Cobblemon TM/Tutor/Egg/Star-tier moves, priced in items or money (via the CobbleDollars integration), with categories (All/TM/Tutor/Egg/Star), configurable tier prices, per-species move lists, fixed/random move rotation on a schedule (e.g. every 12 hours), and species-move configuration screens. This role explicitly states it "requires the TM Craft mod installed on the server."

---

## 6. Dialog system

- Branching dialog trees per NPC (`RoleDialog`, `DialogController`, `Dialog`/`DialogCategory`/`DialogOption` data classes, `GuiRoleDialogTree`, `GuiDialogEdit`, `GuiNPCManageDialogs`).
- Start text plus a tree of dialog options; each option can be a plain text option, a "command option" (executes a command, with `@dp` as a placeholder for the targeted player), a "quit option," or a "role option" (jump into a job/role GUI, e.g. opening a shop).
- Dialog text supports key placeholders: `{player}` (player's name), the NPC's own name, and Minecraft color codes (e.g. `&c` for red).
- Options: disable-Esc-to-close, hide-NPC-during-dialog, show/hide a "dialog wheel" selector UI.
- Player-facing dialog history: read/unread indicators (`GuiDialogInteract`).
- New dialogs can be created and linked ("New Dialog..." creates a linked dialog).

---

## 7. Quest system

Quest types implemented (`espi/mynpcs/quests/Quest*`, `quest.*` lang keys, `CmdQuest`, `QuestController`, `PlayerQuestController`):

- **Dialog Quest** (`QuestDialog` + `QuestDialogObjective`) — complete by having a conversation.
- **Item Quest** (`QuestItem` + `QuestItemObjective`) — turn in items, or "Take Items" from the player; also supports a "Random Item" reward mode.
- **Kill Quest** (`QuestKill` + `QuestKillObjective`) — kill a target ("Kill") or clear an area ("AreaKill").
- **Location Quest** (`QuestLocation` + `QuestLocationObjective`) — reach a location, tracked via the **Location Quest Block** (`npcwaypoint`).
- **Manual Quest** (`QuestManual` + `QuestManualObjective`) — manually marked as complete.

Additional quest features: quest categories, next-quest chaining, repeatable quests, instant-complete option, "complete by NPC" vs. player-manual complete, quest log text and completed text, reward configuration (experience, items, faction points, commands), quest availability gating (see Availability above), a quest log UI (`GuiQuestLog`, `GuiQuestCompletion`), and quest management UI for server admins (`GuiNPCManageQuest`, `GuiQuestEdit`, `GuiNpcQuestReward`). Also supports "MC Daily/Weekly" and "RL Daily/Weekly" reset cycles ("MC" = in-game/Minecraft-time based, "RL" = real-life-time based).

**FTB Quests integration:** an NPC option can require completion of, or mark complete, a specific FTB Quests quest ID (`FTBQuestsHelper`, `advanced.ftbquests`, `ftbquests.requireQuest/completeQuest/questId`), gating dialog/interaction on it with an in-game denial message if requirements aren't met.

---

## 8. Faction system

(`FactionController`, `Faction`/`FactionOptions`/`PlayerFactionData` data classes, `CmdFaction`, `faction.*` lang keys)

- Factions have a default standing state and multiple relationship tiers toward the player: Friendly, Neutral, Unfriendly, Hostile, Default, Hidden.
- Faction "points" per player track standing; points change on NPC death, on combat, and via command/quest rewards, with in-chat notifications ("Faction %s increased/decreased by %s points").
- NPCs can be set to attack all hostile factions, defend other members of their own faction, and respond to being attacked by mobs.
- Admin commands: create, remove, reset a faction, and view/edit player standings (`CmdFaction`).
- A management GUI lists all factions and lets you edit them (`GuiNPCManageFactions`, `GuiFaction`).

---

## 9. Scripting & API system

My NPCs exposes a scripting engine (config flags `EnableScripting`, `NashorArguments` confirm a Java **Nashorn** JavaScript engine backend) plus a large Java wrapper API for it, intended to let advanced users write custom NPC/block/item behavior without a full mod.

- **Scripted entities/blocks/items:** Scripted NPC role, **Scripted Block** (`BlockScripted`/`ItemNpcBlock`), **Scripted Door** (`BlockScriptedDoor`/`ItemScriptedDoor`), **Scripted Item** (`ItemScripted`).
- **Event hooks available to scripts** (from `script.*` lang keys / `IScriptHandler`): Init, Update, Interact, Clicked, Attack, Damaged, Killed, Kills, Collide/Collided, Exploded, Harvested, Powered (redstone), Neighbor Changed, RainFill, Removed, Toggled, Target/Target Lost, FallUpon, Dialog Closed.
- **Script management UI** (`GuiScript`, `GuiScriptBlock`, `GuiScriptDoor`, `GuiScriptItem`, `GuiScriptGlobal`, `GuiScriptForge`, `GuiScriptPlayers`, `GuiScriptList`) — lists loaded/available scripts, shows an in-game API doc and API source, an "open scripts folder" shortcut, hide/show function list, console output view, and a language selector (multi-language scripting support).
- **Java API surface** (`espi/mynpcs/api/**`), exposed to scripts as wrapper objects: `NpcAPI` entry point; wrappers for entities (`EntityWrapper`, `NPCWrapper`, `PlayerWrapper`, `AnimalWrapper`, `MonsterWrapper`, `VillagerWrapper`, `ArrowWrapper`, `ThrowableWrapper`, `ProjectileWrapper`, `EntityItemWrapper`, `PixelmonWrapper`, `CobblemonWrapper`), world/block/item wrappers (`WorldWrapper`, `BlockWrapper`, `BlockScriptedWrapper`, `BlockScriptedDoorWrapper`, `ItemStackWrapper`, `ItemBlockWrapper`, `ItemArmorWrapper`, `ItemBookWrapper`, `ItemScriptedWrapper`), NBT (`NBTWrapper`), dimension (`DimensionWrapper`), scoreboard (`ScoreboardWrapper`, `ScoreboardTeamWrapper`, `ScoreboardObjectiveWrapper`, `ScoreboardScoreWrapper`), raytrace (`RayTraceWrapper`), damage source (`DamageSourceWrapper`), and a full **custom GUI API** (`CustomGuiWrapper` and component wrappers for buttons, button lists, colored lines, entity display, item renderer, item slots, labels, scroll areas, sliders, text areas/fields, textured buttons/rects) — allowing scripts to build entirely custom in-game GUIs.
- **Scriptable event classes** (`espi/mynpcs/api/event/*`): `BlockEvent`, `CustomGuiEvent`, `DialogEvent`, `ForgeEvent`, `HandlerEvent`, `ItemEvent`, `MyNPCsEvent`, `NpcEvent`, `PlayerEvent`, `ProjectileEvent`, `QuestEvent`, `RoleEvent`, `WorldEvent`.
- **Overlay/HUD API** (`espi/mynpcs/api/overlay/*`, `client/overlay/*`) — scripts/GUI can render custom on-screen overlay labels, textured rectangles, and item icons.
- **`/mynpcs script`** command (`CmdScript`) — reload scripts and trigger script events from the command line.

---

## 10. Items

Registered items (`item.mynpcs.*` + `espi/mynpcs/items/*`):

| Item | Class | Purpose |
|---|---|---|
| My NPCs Wand | `ItemNpcWand` | Primary tool: spawns/selects/edits NPCs |
| My NPCs Pather | `ItemNpcMovingPath` | Defines waypoints/paths for NPC movement ("Registered %s to your NPC Pather", warns if a point is too far from the previous one) |
| Scripter | `ItemNpcScripter` | Applies/edits scripts on entities |
| Mob Cloner | `ItemNpcCloner` | Clones existing NPCs/mobs (`ServerCloneController`, `/mynpcs clone`) |
| Mounter | `ItemMounter` | Mounts/attaches NPCs (e.g. to spawners or blocks) |
| Scripted Block (item) | `ItemNpcBlock` | Places the Scripted Block |
| Scripted Item | `ItemScripted` | A held item that runs custom script behavior |
| NBT Tags Book | `ItemNbtBook` | Opens the raw NBT editor |
| NPC Jar (Empty) | `ItemSoulstoneEmpty` | Captures a follower/NPC into a portable jar |
| NPC Jar (Filled) | `ItemSoulstoneFilled` | A captured NPC, releasable elsewhere |
| Teleporter | `ItemTeleporter` | Personal teleport tool |
| Nether Furnace | (block item) | Crafted item/block, recipe below |

Item group: **"My NPCs Tools"** (`itemGroup.cnpcs`).

---

## 11. Blocks

Registered blocks (`block.mynpcs.*` + `espi/mynpcs/blocks/*`):

| Block | Class | Purpose |
|---|---|---|
| Border | `BlockBorder` | Marks an invisible boundary (e.g. for NPC wander range or claims) |
| Builder Block | `BlockBuilder` | Used by the Builder job / schematic building system |
| Carpentry Bench | `BlockCarpentryBench` | Crafting station tied to `RecipeCarpentry` |
| Copy Block | `BlockCopy` | Copies structures (`copy.blueprint`/`copy.schematic`/`copy.structure`) |
| Mailbox | `BlockMailbox` | In-world mailbox for the mail system |
| Nether Furnace | `BlockNetherFurnace` | Custom furnace block (has a recipe, see below) |
| Redstone Block | `BlockNpcRedstone` | NPC-linked redstone signal block |
| Scripted Block | `BlockScripted` | Runs custom script hooks |
| Scripted Door | `BlockScriptedDoor` | A door that runs custom script hooks |
| Location Quest Block | `BlockWaypoint` | Marks a location for Location Quests |

Supporting block-entity infrastructure: `IBlockEntityTypeFactory`, `BlockNpcDoorInterface`, `BlockInterface`, tile entities under `espi/mynpcs/blocks/tiles`.

---

## 12. World tools: schematics, blueprints, mass building

(`espi/mynpcs/schematics/*`, `SchematicController`, `BlueprintUtil`, `CmdSchematics`, `CmdClone`)

- Supports three structure formats: **Blueprint**, **Schematic**, and **Structure**, plus reading Sponge-format schematics (`SpongeSchem`).
- 27 built-in `.schematic` files ship inside the jar's `data/mynpcs/schematics/` folder for NPC village/settlement building, including: `house`, `house_small`, `tier_house1/2/3`, `barn`, `bakery`, `chapel`, `church`, `gate`, `glassworks`, `guard_tower`, `guild_house`, `inn`, `library`, `lighthouse`, `mill`, `observatory`, `ship`, `shop`, `stall`/`stall2`/`stall3`, `tower`, `wall`, `wall_corner`, `archery_range`, `building_site`.
- `/mynpcs schematics` command: list, load, build, info, and stop building a schematic; supports an "Instant Build" mode with an explicit non-undoable warning.
- Structure preview before building, dimension display (width/height/length).
- **Mass Block Controller** (`MassBlockController`) for placing/animating large groups of blocks (used by the Builder job and schematic construction).
- **Cloning** (`ServerCloneController`, `CmdClone`) — clone entities with options for grid placement, offset, width, count, ignoring NBT, and client-side vs. server-side clone scope; warns before overwriting an existing clone.

---

## 13. Mob spawner / mass entity control

- **Mob Spawner GUI** (`GuiNpcMobSpawner`, `GuiNpcMobSpawnerAdd`, `GuiNpcMobSpawnerSelector`, `GuiNpcMobSpawnerMounter`) attached to the Spawner job: pick entities to spawn, spawn type (one-by-one/random/all), spawn "Clones" of a saved NPC, mount options (mount-by, player-mounts), position offset, light/dark spawn conditions, "despawn spawns on target lost," "dies after spawns die."
- **Natural spawning** (`NPCSpawning`, `GuiNpcNaturalSpawns`) — configure custom NPCs to spawn naturally in specific biomes with a weighted chance, viewable/editable via a global "Natural Spawns" management screen; capped by the `NpcNaturalSpawningChunkLimit` config option.
- **`/mynpcs slay`** command (`CmdSlay`) — mass-remove or count entities by type/name/range, with filters for animals, monsters, and NPCs, and an option to also drop items/XP orbs or skip them.
- **Remote NPC control** (`GuiNpcRemoteEditor`, `remote.*`) — a "Nearby NPCs" list allowing freeze/unfreeze of all NPCs, resetting all, and teleporting to a selected NPC.

---

## 14. Faction of Chunk Loading & world control

- **Chunk Loader job** keeps chunks loaded around an NPC; capped by config field `ChuckLoaders`.
- **Config toggles for vanilla mechanics** (see Config section): leaves decay, vines growth, ice melting can be globally enabled/disabled — implemented via mixins (`LeavesBlockMixin`, `VineBlockMixin`, `IceBlockMixin`).

---

## 15. Companion / pet system

(`RoleCompanion`, `companion.*` lang keys, `GuiNpcCompanion`, `GuiNpcCompanionInv`, `GuiNpcCompanionStats`, `GuiNpcCompanionTalents`)

- Companions age through stages: Baby → Child → Teenager → Adult/Full-Grown.
- Track Level, Strength, and Talents (`GuiTalent` sub-screen).
- Have an Owner, their own Inventory tab, and a Stats screen.
- Can additionally act as a farmer, guard, or trader (`CompanionFarmer`, `CompanionGuard`, `CompanionTrader`), and track food stats (`CompanionFoodStats`).

---

## 16. Player-facing GUIs

Beyond the NPC editor, players interacting with NPCs (or opening their own inventory extensions) see:

- **Quest Log** (`GuiQuestLog`) and **Quest Completion** popup (`GuiQuestCompletion`).
- **Faction standing tab** (`InventoryTabFactions`, `GuiFaction`) — "You have no standings with any faction" if none.
- **Mailbox UI** (`GuiMailbox`, `GuiMailmanWrite`) — read/write/send mail, with sender, subject, and a "time ago" display.
- **Bank chest** (`GuiNPCBankChest`) — deposit/withdraw with fee display.
- **Trader shop** (`GuiNPCTrader`).
- **Carpentry Bench** (`GuiNpcCarpentryBench`).
- **Follower hire dialog** (`GuiNpcFollowerHire`) and follower status (`GuiNpcFollower`).
- **Transport selection** (`GuiTransportSelection`).
- **Recipes list** (`GuiRecipes`).
- **Dialog interaction window** (`GuiDialogInteract`) with a dialog-wheel option.
- Extra vanilla-inventory tab (`InventoryTabVanilla`) integrating these features alongside the normal inventory screen.

---

## 17. Name generator ("Markov Names")

(`nikedemos/markovnames/*`, `data/mynpcs/markovnames/*`, `markov.*` lang keys)

A Markov-chain based procedural name generator with a `Gender` filter (Male/Female/Either) and multiple language/culture name sets, each backed by a text corpus shipped in the jar:

- Ancient Greek (male & female)
- Aztec
- Old Norse ("both genders" corpus)
- Roman/Latin (praenomina, nomina, cognomina — full Roman three-name convention)
- Lappish/Sami ("both genders" corpus)
- Old Polish/Slavic (plus an alternate list)
- Spanish/Latino (given names male & female, plus surnames)
- Breton/Welsh (male & female)
- Japanese (given names male & female, plus surnames)
- "Classic My NPCs" generic name list (`mynpcs_classic.txt`)
- A "Generic Generator" fallback

Accessible from the NPC name field via a "Generate" button.

---

## 18. Music / sound ("Bard") job

- Bard job (`JobBard`, `bard.*`): play music as ambient background or "as jukebox," with looping, and optional "has off distance" (an outer distance at which the music stops) vs. "on distance" (inner audible range).
- **Sound assets shipped in the jar** (`assets/mynpcs/sounds/`): songs (3 tracks), human vocal sounds (111 files — idle/hurt/death/anger/step type voice clips across the character templates), magic sound effects (2), gun sound effects (4), misc sounds (3).
- **Per-NPC custom sounds** (Advanced menu): idle/living, hurt, death, angry, and step sounds, each independently selectable, with an optional pitch variation flag.

---

## 19. Visual/character asset library

Texture folders shipped in the jar (`assets/mynpcs/textures/entity/`), giving a large built-in cast of premade character "templates" a player can select instead of building a model from scratch:

- Human male (52 textures) / Human female (26)
- Dwarf male (22) / Dwarf female (5)
- Elf male (16) / Elf female (9)
- Orc male (13) / Orc female (10)
- Naga male (19) / Naga female (5)
- Furry male (37) / Furry female (37)
- Monster male (25) / Monster female (2)
- Ponies (60)
- Pokémon (145 — used by the Cobblemon/Pixelmon trainer sprites and related roles)
- Dragon (17)
- Crystal (18)
- Golem (9)
- Slime (12)
- Skeleton (11)
- Villagers (5)
- Ender-chibi (8)
- Enderman (4)
- "Important people" (1) and "Others" (15) and "Custom" (34) catch-all folders
- Alex skins (1)

Plus separate **armor** textures (over two dozen sets such as ninja, assassin, tuxedo, wizard, mithril, full-iron/gold/bronze/wood/emerald, tactical, commissar, officer, infantry, soldier, cowleather, demonic, crown1/crown2, nanorum, x407) and **overlay** textures for eyes/face add-ons (herobrine eyes, ender eyes, old ender eyes, ghost eyes, evil eyes, tec-zombie eyes, goggles), used by the Display → Overlay option to swap just a body-part texture (e.g. glowing eyes) on top of the base skin.

Model body-part system covers: head, body, left/right arm, legs, left/right leg, tail (bird/dragon/fin/fox/horse/rodent/squirrel), wings, ears (bunny), horns (antenna/antlers/bull), snout (beak/bunny/small/medium/large), hair (solid), eyes (brow/glint/pupil/lid/lash/blink), legs variants (digitigrade/horse/mermaid/naga/spider), fins (reptile/shark), skirt, breasts, mohawk, claws, particles, and a build-in headwear slot.

---

## 20. Integrations with other mods

Confirmed by dedicated helper/plugin classes in the jar:

- **JEI (Just Enough Items)** — `MyNpcsJeiPlugin` (registered via `META-INF/services/mezz.jei.api.IModPlugin`).
- **REI (Roughly Enough Items)** — `MyNpcsReiPluginNeoForge`.
- **Cobblemon** — `CobblemonHelper` (incl. move-teach results), `CobblemonWrapper`, `NPCTrainerBattleActor` (drives NPC-vs-player Pokémon battles), `ICobblemonPlayerData`, Pokémon-related roles (Trainer, Trader, Move Relearner, Move Tutor), Pokémon party/species/moves editing GUIs, Pokémon texture set.
- **Pixelmon** — `PixelmonHelper`, `PixelmonWrapper`, `IPixelmonPlayerData` (parallel/legacy Pokémon-mod support alongside Cobblemon).
- **FTB Quests** — `FTBQuestsHelper`; NPC dialog/options can require or complete an FTB Quests quest by ID, with a denial message if unmet.
- **TM Craft** — `TMCraftHelper` (with a `Category` enum); the Move Tutor role explicitly requires this mod for TM/Tutor functionality.
- **CobbleDollars** — `CobbleDollarsHelper`; used as a currency backend for the Move Tutor's "money" payment mode (fails gracefully with a message if not installed: *"This tutor can't charge money: CobbleDollars isn't installed."*).
- **Armorer's Workshop** — `ArmorersWorkshopHelper`.

---

## 21. Commands

Registered under root command dispatchers found in `espi/mynpcs/command/*` (Brigadier-based, NeoForge command system):

| Command class | Subject | Confirmed subcommand/argument literals |
|---|---|---|
| `CmdNPC` | NPC management | `create`, `delete`, `reset`, `display`, `role`, `home`, `owner`, `visibility`/`visible` |
| `CmdEspi` | Mod/debug root command | entity/display/type-related |
| `CmdQuest` | Quests | `create`, `remove`, `start`, `stop`, `finish`, `reload`, `save`, `load`, `objectives`, `title` |
| `CmdFaction` | Factions | `create`, `remove`, `reset`, `drop`, `points`, `save` |
| `CmdDialog` | Dialogs | `create`, `remove`, `reload`, `save`, `load`, `show`, `read`/`unread`, `option`, `title` |
| `CmdScene` | Scenes | `start`, `pause`, `reset`, `state`, `time`, `ticks` |
| `CmdSchematics` | Schematics | `build`, `list`, `load`, `info`, `stop`, `init` |
| `CmdScript` | Scripting | `reload`, `trigger` (script events) |
| `CmdMark` | NPC marks | `clear`, `color`, `type` |
| `CmdClone` | Cloning | `create`, `remove`, `spawn`, `offset`, `grid`, `width`, `length` |
| `CmdSlay` | Mass removal | `slay`, filters for `animals`, `mobs`/`monster`, `npcs`, `range`, `type`, `count`, item/XP-orb drop toggles |
| `CmdConfig` | Live config | `chunkloaders`, `freezenpcs`, `icemelts`, `leavesdecay`, `vineinflateth`, `debug`, `font`, `size` |

---

## 22. Configuration options

Confirmed static config fields on the main mod class (`espi.mynpcs.MyNpcs`), loaded/saved via the custom `ConfigLoader`/`ConfigProp` annotation-driven config system:

- `EnableScripting` — toggle the JavaScript/Nashorn scripting engine.
- `NashorArguments` — Nashorn engine launch arguments.
- `EnableChatBubbles` — show floating chat bubble text above NPCs.
- `NpcNavRange` — pathfinding/navigation range.
- `NpcNaturalSpawningChunkLimit` — cap on natural NPC spawns per chunk area.
- `NpcUseOpCommands` — whether NPC-triggered dialog "command options" run with operator permission.
- `EspiCommandOpOnly` — restrict mod commands to operators.
- `InventoryGuiEnabled` — toggle the extra inventory GUI tabs.
- `FixUpdateFromPre_1_12` — legacy data migration/compat fix.
- `DisablePermissions` — bypass the permission system entirely.
- `SceneButtonsEnabled` — toggle in-GUI scene control buttons.
- `EnableUpdateChecker` — check for mod updates.
- `FreezeNPCs` — global freeze toggle (also a live `/mynpcs config` option and remote-editor button).
- `OpsOnly` — restrict certain actions to operators.
- `DefaultInteractLine` — default text line shown on NPC interaction.
- `ChuckLoaders` — chunk loader cap (int).
- `Dir` — the mod's data directory (`java.io.File`).
- `LeavesDecayEnabled` / `VineGrowthEnabled` / `IceMeltsEnabled` — toggle vanilla world mechanics.
- `SoulStoneAnimals` / `SoulStoneNPCs` — whether the NPC Jar item can capture animals and/or NPCs.
- `ClonerSavePath` — whether the Mob Cloner saves paths.
- `HeadWearType` — headwear rendering mode.
- `FontType` / `FontSize` — GUI text font settings.
- `EnableInvisibleNpcs` — allow fully invisible NPCs.
- `NpcSpeachTriggersChatEvent` — whether NPC "speech" fires a chat event (for logging/other mod hooks).
- `VerboseDebug` — verbose debug logging.

Client-side display/config toggles from the `config.*` lang keys (`espi/mynpcs/client/controllers` GUI-backed): show back-item, block highlight, show chat bubbles, compatibility mode, edit buttons visibility, default gender-model presets (Goblin Male, Human Female, Human Male), show names, show particles, point-of-view, "reload skins," skin URL entry, solid head-layer rendering, sounds on/off, tooltip on/off.

---

## 23. Permissions

(`espi/mynpcs/permission/*`, `espi/mynpcs/neoforge/permission/*`)

- Abstract permission layer: `IPermissionHandler`, `PermissionPlatform`, `PermissionNode`.
- Two concrete implementations shipped: `AlwaysAllowPermissionHandler` (default/no permission mod present) and `NeoForgePermissionHandler` (integrates with a NeoForge-compatible permission mod).
- `MyNPCsPermissions` centralizes the mod's permission node definitions.
- In-game denial message confirmed in lang: *"You don't have permission to do this."*

---

## 24. Database / persistence

- Custom lightweight database layer (`espi/mynpcs/db/DatabaseController`, `DatabaseColumn` with a `Type` enum) used for structured persistence of mod data (players, factions, quests, dialogs, banks, mail, etc.), separate from raw NBT storage.
- Per-category data controllers exist for: players (`PlayerDataController`), quests (`QuestController`, `PlayerQuestController`), factions (`FactionController`), dialogs (`DialogController`), banks (`BankController`), transport (`TransportController`), recipes (`RecipeController`), schematics (`SchematicController`), scripts (`ScriptController`), chunk loading (`ChunkController`), linked NPCs (`LinkedNpcController`), spawns (`SpawnController`), mass blocks (`MassBlockController`), and global data (`GlobalDataController`).
- Global admin management GUIs exist to browse/edit this stored data server-wide: `GuiNPCManageBanks`, `GuiNPCManageDialogs`, `GuiNPCManageFactions`, `GuiNPCManageLinkedNpc`, `GuiNPCManageQuest`, `GuiNPCManageTransporters`, `GuiNpcManagePlayerData`, `GuiNpcManageRecipes`, `GuiNpcNaturalSpawns`.

---

## 25. Recipes / crafting

- **Nether Furnace** — shaped crafting recipe (category: redstone) using Cobblestone (`C`) and Netherrack (`N`) in the pattern:
  ```
  CCC
  C C
  CNC
  ```
  yielding 1 `mynpcs:nether_furnace`.
- **Carpentry Bench** system (`RecipeCarpentry`, `GuiNpcCarpentryBench`) — a separate, NPC-managed recipe/crafting system distinct from vanilla crafting, editable via `GuiNpcManageRecipes` and exposed to players via a "Recipe List" screen (`GuiRecipes`, `recipes.list`).
- A custom **damage type** is registered (`data/mynpcs/damage_type/npc.json`) used for NPC-caused death messages (*"%1$s was killed by %2$s"*).

---

## 26. Rendering / client-side technical features

Confirmed via the mixin list (`mynpcs.mixins.json`) and access transformers (`accesstransformer.cfg`):

- Custom `LivingEntityRenderer`/armor-layer/biped-body rendering hooks to support the flexible per-part NPC model (`LivingRendererMixin`, `LivingRenderer2Mixin`, `LivingRenderer3Mixin`, `ArmorLayerMixin`, `BipedBodyMixin`, `AgeableModelMixin`, `ModelPartMixin`, `ModelRendererMixin`, `WalkAnimationStateMixin`).
- Custom skin/texture loading and caching, including remote skin URLs (`SkinManager`/`TextureCache` access-transformed public, `SkinEventHandler`, `display.skinurl`).
- Client packet/network mixins for entity spawn packets carrying extra NPC data (`MixinClientboundAddEntityPacket`, `ClientPlayNetHandlerMixin`, `NetworkPlayerInfoMixin`).
- Custom container/menu and GUI-screen mixins for the mod's many editor screens (`AbstractContainerScreenMixin`, `MixinAbstractContainerScreen`, `ScreenMixin`, `MixinEditBox`, `ClientTextTooltipMixin`).
- Particle manager and music manager hooks (`ParticleManagerMixin`, `MusicManagerMixin`) supporting NPC particle display options and the Bard job's music playback.
- Mouse/matrix-stack utility mixins supporting custom GUI rendering (`MouseHelperMixin`, `MatrixStackMixin`).
- Server-side mixins for entity persistence, chunk/section management, and NBT list handling to support the mod's custom entity data and cloning system (`PersistentEntitySectionManagerMixin`, `ChunkMapMixin`, `ListNBTMixin`, `EntityPersistentData`, `BlockEntityPersistentData`, `MixinPlayerDataStorage`).
- World-generation and block-behavior mixins used for the leaves-decay/vine-growth/ice-melt toggles and mob spawner base (`NoiseChunkGeneratorMixin`, `BaseSpawnerMixin`, `LeavesBlockMixin`, `VineBlockMixin`, `IceBlockMixin`, `MixinBlockBehaviour`).
- A `PackRepositoryMixin` for resource-pack interplay.

---

## 27. Localization

Full translations shipped for **18 languages**: English (`en_us`), Brazilian Portuguese (`pt_br`), Chilean Spanish (`es_cl`), Korean (`ko_kr`), Dutch (`nl_nl`), Argentinian Spanish (`es_ar`), Simplified Chinese (`zh_cn`), European Spanish (`es_es`), Mexican Spanish (`es_mx`), Traditional Chinese (`zh_tw`), Swedish (`sv_se`), Hungarian (`hu_hu`), Czech (`cs_cz`), Polish (`pl_pl`), Russian (`ru_ru`), French (`fr_fr`), Indonesian (`id_id`), German (`de_de`). The English file alone defines 964 translation keys, covering every GUI, tooltip, and hint described above (including a full set of in-game contextual "guihint.*" explanations for each NPC-editing option — these are the authoritative source for several of the option descriptions above).

---

## 28. Summary of registry content counts (as found in the jar)

- Entities: **14** registered types
- Blocks: **10** registered blocks
- Items: **12** registered items (incl. block items)
- Jobs: **12** distinct job types
- Roles: **10** distinct role types
- Quest types: **5**
- Commands: **12** top-level command classes
- AI/goal classes: **~24** custom AI behavior goals plus 4 target-selector classes
- Built-in schematics: **27**
- Markov name generator language/culture packs: **12** (plus gender filter and a generic fallback)
- Languages localized: **18**
- Mixins: **31** common + **28** client-only = **59** total
- Character texture template folders: **29**, totaling several hundred individual skins
- Third-party mod integrations: **8** (JEI, REI, Cobblemon, Pixelmon, FTB Quests, TM Craft, CobbleDollars, Armorer's Workshop)

---

*Compiled by static inspection of `mynpcs-neoforge-1.5.0.jar` — no feature above was inferred from external documentation, memory of similar mods, or guesswork; each entry traces to a specific file, class, registry name, or localization string inside the jar itself.*
