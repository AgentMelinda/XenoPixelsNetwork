# My NPCs 1.5.0 — Reverse-Engineered Feature & GUI Reference

**Target artifact:** `mynpcs-neoforge-1.5.0.jar`  
**Minecraft:** 1.21.1  
**Loader:** NeoForge  
**Mod ID:** `mynpcs`  
**JAR version:** `1.5.0`  
**Analysis date:** 2026-09-22

> This document is intentionally evidence-driven. It was built from the uploaded JAR itself: bytecode (`javap`), bundled language files, registries, resources/data files, and GUI construction code. I do **not** treat a class name alone as proof that a feature is reachable from the normal UI. Where a feature exists in code but is hidden, WIP, broken, conditional, or only partially exposed, that is called out explicitly.

## Evidence / confidence legend

- **CONFIRMED — JAR:** behavior, field, class, registry entry, or constant exists in the uploaded build.
- **CONFIRMED — GUI:** visible order/label is taken from GUI construction bytecode and `en_us.json`, not guessed from enum ordering.
- **CONDITIONAL:** feature is gated by another mod/helper being detected or a config/runtime condition.
- **DORMANT / NOT NORMALLY SELECTABLE:** implementation exists, but this build does not expose it in the normal selector path I traced.
- **WIP / BROKEN:** the mod itself labels the button that way.
- **PUBLIC DESCRIPTION:** used only as a cross-check for intent; the uploaded JAR remains the primary truth source.

## Reverse-engineering coverage plan

1. Read mod metadata and all JAR entries.
2. Enumerate every GUI/editor/player screen and extract controls in construction order.
3. Trace main NPC menu navigation and role/job selector mappings, including non-contiguous IDs.
4. Inspect NPC data classes for fields/capabilities that may not have obvious labels.
5. Inspect role/job implementations and player interaction screens.
6. Inspect quest, dialog, faction, bank, transport, recipe, spawn, clone, schematic, mail, and linked-NPC systems.
7. Inspect scripting engines, public API interfaces/events, custom-GUI components, commands, config fields, registries, network packets, and bundled datapack/resource assets.
8. Cross-check conditional ecosystem integrations against helper classes and the public 1.5.0 description.
9. Mark unreachable/WIP/broken surfaces instead of presenting them as finished features.

## 1. Build identity and package facts

- The JAR contains **2,483 entries**.
- Detected GUI/menu-related classes: **332** (class-name inventory used during analysis).
- Extracted GUI screens with directly recoverable ordered labels: **123**.
- Bundled English localization keys: **964**.
- Bundled language files: **18** — `cs_cz`, `de_de`, `en_us`, `es_ar`, `es_cl`, `es_es`, `es_mx`, `fr_fr`, `hu_hu`, `id_id`, `ko_kr`, `nl_nl`, `pl_pl`, `pt_br`, `ru_ru`, `sv_se`, `zh_cn`, `zh_tw`.
- GUI texture/resource files under `assets/mynpcs/textures/gui`: **47**.
- Server packet classes: **119**; client packet classes: **41**. This is a strongly networked client/server editor, not a client-only cosmetic system.
- `META-INF/neoforge.mods.toml` requires NeoForge `21.1.0+` and Minecraft `[1.21.1, 1.22)`.
- The bundled metadata says `license="MIT"`. The current public CurseForge project page says **All Rights Reserved**. That is a real metadata/public-page discrepancy; this document does not resolve the legal meaning.

## 2. Main NPC editor — exact visible GUI order

**CONFIRMED — GUI:** `GuiNpcMenu` builds the top-level NPC editor in this visible order:

1. **Display**
2. **Stats**
3. **AI**
4. **Inventory**
5. **Advanced**
6. **Global**
7. **X / Close**
8. **Delete** (confirmation flow)

The internal `EnumMenuType` declaration order is different (`DISPLAY, STATS, INVENTORY, AI, ADVANCED, MODEL, TRANSFORM, MOVING_PATH, MARK`). Do not use that enum as the visual tab order; the list above is the actual editor construction order.

### 2.1 Display page

**Purpose:** identity, model/skin, scale, render layers, visibility, hitbox, glow, and boss-bar presentation.

Visible/configurable controls recovered from `GuiNpcDisplay` and `DataDisplay`, in page flow:

- **Name** field, with auxiliary name-generation controls. The NPC data contains a Markov name-generator setting and bundled name corpora.
- **Title** field.
- **Model → Edit** opens the model creation/customization system.
- **Living animation**: Yes / No.
- **Size**: UI range includes 1–30.
- **Tint / skin color**.
- **Glowing** and separate overlay/layer glow data.
- **Texture source mode:** Texture / Player / URL; **Select** opens texture selection where appropriate.
- **Cape → Select**.
- **Overlay → Select**.
- **Showing Layers:** Yes / No.
- **Hitbox:** Normal / None / Solid.
- **Visible:** Yes / No / Partially; visibility can have **Availability** conditions.
- **Boss Bar:** Hide / Show / Show when attacking.
- **Boss-bar color:** Pink, Blue, Red, Green, Yellow, Purple, White.
- Backing data also exposes model size, per-part scale data, skin/player profile/URL data, cloak, `showName`, `disableLivingAnimation`, and visibility state.

#### Model editor navigation

`GuiCreationScreenInterface` exposes: **Entity → Parts → Extra → Scale → Save → Load → X**.

- **Entity:** reset to NPC, select `customnpc`, or use the “Basic Limited Renderer” path; can substitute supported entity rendering/model data.
- **Parts:** custom part editor exists (`GuiCreationNewParts`). Texture-based parts can use Player Skin, direct Texture, selector, or URL. Eye configuration exposes pupil/size/mirror/direction/position/glint/lashes/blink/lid controls.
- **Extra:** entity-specific extra data adapter. Recovered keys include Age/Child, Color, Model, Cobblemon model data, Pixelmon data, dog/breed-style data, and legacy MorePlayerModels-style skin/hair/face/uniform/gemstone/visor/gloves/cape data fields.
- **Scale:** Width, Height, Depth, and Shared Yes/No.
- **Save / Load:** model preset persistence; load screen supports removal.

### 2.2 Stats page

Visible order from `GuiNpcStats`:

1. Health
2. Aggro Range
3. Creature Type: Normal / Undead / Arthropod
4. Respawn → Edit
5. Ranged Properties → Edit
6. Projectile Properties → Edit
7. Resistance → Edit
8. Immune to Fire
9. Burns in Sun
10. Can Drown
11. No Fall Damage
12. Potion Immune
13. Cobweb affected/ignore-cobweb control (backing field is `ignoreCobweb`; wording should be read carefully when configuring)
14. Health Regen
15. Combat Regen

#### Respawn submenu

- Respawn mode: Yes / Day / Night / No / Naturally.
- Respawn time.
- Hide dead body.
- Done.

#### Melee properties submenu

- Melee Strength, Melee Range, Melee Speed, Knockback.
- Melee Effect, duration/time, amplifier levels 0–10.
- Effect selection includes None and Fire paths.

#### Ranged properties submenu

- Accuracy %, Shot Count, Range, minimum/melee-range distance, Min Delay, Max Delay.
- Burst Count and Burst Rate.
- Fire Sound, Hit Sound, Ground Sound selectors.
- Aim While Shooting: No / When Distant / When Hidden.
- Shoot Indirect toggle.

#### Projectile properties submenu

- Strength, Knockback, Size, Speed.
- Gravity: No / Yes / Constant / Accelerate.
- Explosive: None / Small / Medium / Large.
- Projectile effect state (regular/amplified paths), trail type, 2D/3D, spins, sticks, glow/no-glow.

#### Resistance submenu

- Knockback Resistance, Arrow Resistance, Melee Resistance, Explosion Resistance.

### 2.3 AI page

Visible order from `GuiNpcAI`:

1. **On Found Enemy:** Retaliate / Panic / Retreat / Nothing
2. **Shelter From:** Darkness / Sunlight / Disabled
3. Must See Target: No / Yes
4. Door Interact: Disabled / Break / Open
5. Can Swim
6. Return To Start
7. Avoids Water
8. Leap At Target
9. Movement → Edit
10. Attack Invisible
11. Mount Control

Backing `DataAI` additionally tracks fire reaction, sunlight/water avoidance, sprint/leap behavior, direct line-of-sight, interaction stopping, movement/standing animation types, body orientation offsets, walking and active range, move speed, path/position state, pause behavior, and mount control.

#### Movement submenu

- Movement type: Standing / Wandering / Moving Path.
- Navigation: Ground / Flying / Swimming.
- Range and Active Range.
- Wander Interact Yes/No; pause behavior.
- Position offsets X/Y/Z.
- Animation presets: Normal / Sitting / Lying / Hug / Sneaking / Dancing / Aiming / Crawling.
- Animation mode: Body / Manual / Stalking / Head.
- Rotation controls with 0–359 values.
- Moving-path controls include Looping / Backtracking, path/movement name, pauses, Stop Interact, and Move Speed.

### 2.4 Inventory page

- **Min Exp** and **Max Exp** reward values (text fields cap at 32767 in this GUI).
- **Loot mode:** Normal / Auto.
- NPC equipment/inventory slots and player inventory container.
- Per-drop **Drop Chance** controls.
- `DataInventory` stores weapon/armor slots, projectile/offhand state, drop items/drop chances, min/max EXP and randomized EXP/drop behavior.

### 2.5 Advanced page — exact role/job order

This page is important because code contains roles that are **not** all exposed in the selector.

#### Role selector — exact visible order

1. No Role
2. Trader
3. Follower
4. Bank
5. Transporter
6. Mailman
7. Companion **(WIP)**
8. Dialog
9. Pokémon Trader
10. Move Tutor
11. Pokémon Trainer

The selector uses a non-contiguous role-ID map: `[0,1,2,3,4,5,6,7,8,10,11]`. **Role ID 9 (`RoleMoveRelearner`) exists in `DataAdvanced` and has its own GUI, but is deliberately skipped by this normal selector in 1.5.0.** Treat Move Relearner as **DORMANT / NOT NORMALLY SELECTABLE** from this page, despite code and UI classes existing.

#### Job selector — exact visible order

1. No Job
2. Bard
3. Healer
4. Guard
5. Item Giver
6. Follower
7. Spawner
8. Conversation
9. Chunk Loader
10. Puppet
11. Builder
12. Farmer

#### Advanced utility buttons — visible layout order

The page then lays out two-column controls in rows: **Lines / Factions → Dialogs / Sounds → Night / Linked → Scenes / Marks → FTB Quests**. The FTB Quests button is added only when the FTB Quests helper is enabled.

Advanced backing data includes interaction/world/attack/killed/kill/NPC-interact lines, ordered/random line behavior, sound set, faction behavior, scene references, FTB Quest requirement/completion fields, and day/night editing state.

#### Advanced → Lines

- Separate line groups for World, Attack, Interact, Killed, Kill, NPC Interact.
- Random/ordered playback control.

#### Advanced → Factions

- Attack hostile factions Yes/No.
- Defend faction members Yes/No.
- Death/faction-point behavior fields.

#### Advanced → Dialogs

- Per-NPC dialog option selection/editing; select/remove linked options.

#### Advanced → Sounds

- Living, Angry, Hurt, Death, Step sounds.
- Each has Select Sound; pitch behavior has Yes/No control.

#### Advanced → Night

- Day/night profile controls for Display, Stats, AI, Inventory, and Advanced role/job data.
- Editing Mode plus Load Day / Load Night operations.

#### Advanced → Linked

- Linked-NPC data can be associated/cleared; global Linked manager stores reusable linked data.

#### Advanced → Scenes

- Disabled/Enabled scene entries, Edit, remove (`X`), None/Add paths.

#### Advanced → Marks

- Add/remove marks and edit Availability conditions.

#### Advanced → FTB Quests (conditional)

- Required Quest / Quest ID.
- Complete Quest / Quest ID.
- Backing fields independently track required-role and required-job gating as well as completion behavior.

### 2.6 Global page — exact visible order

`GuiNPCGlobalMainMenu` builds these buttons top-to-bottom:

1. **Banks**
2. **Factions**
3. **Dialogs**
4. **Quests**
5. **Transport**
6. **PlayerData**
7. **Recipes (Broken)**
8. **Natural Spawns (WIP)**
9. **Linked**

The `(Broken)` and `(WIP)` suffixes are hard-coded by the mod itself in this build.

## 3. Quest system and Quest Log

### 3.1 Player Quest Log — actual runtime flow

`GuiQuestLog` is a real player-facing journal. It:

- Reads the player’s active quests from `PlayerQuestController`.
- Groups active quests by `QuestCategory.title`.
- Naturally sorts category names and renders them as left-side category buttons.
- Shows quest titles for the selected category in a scroll list.
- Shows `quest.noquests` when there are no active quests.
- Renders the selected quest’s log text through `TextBlockClient` and supports page forward/back controls.
- Renders an **Objectives** section and objective progress.
- If the quest has a completion NPC name, renders the **Complete with …** instruction.
- Sits in a player tab group with **Vanilla Inventory → Factions → Quests** tabs.

### 3.2 Quest editor — top-level fields

- Title and numeric ID.
- Completed text → text editor.
- Quest Log text → text editor.
- Reward → reward submenu.
- Type → Item / Dialog / Kill / Location / Area Kill / Manual.
- Repeat rule: None/nonrepeatable, Repeatable, MC Daily, MC Weekly, RL Daily, RL Weekly (`EnumQuestRepeat`).
- Completion mode: NPC or Instant (`EnumQuestCompletion`).
- Faction options.
- Command to run on completion.
- Mail setup.
- Next Quest link/chaining.

### 3.3 Quest type capabilities

- **Item:** required item objective; Take Items Yes/No; Ignore Damage; Ignore NBT.
- **Dialog:** objective is tied to dialog-option selection; supports selecting/removing linked dialog options.
- **Kill:** target NPC/player/entity objective with count.
- **Location:** named Location Block objective.
- **Area Kill:** distinct quest implementation exists (`QuestAreaKill`); editor selection exposes Area Kill even though the generic label extractor does not expose a dedicated standalone screen class for it.
- **Manual:** manually updated/completable objective target/count model.

### 3.4 Quest rewards

- Reward items inventory.
- Reward EXP.
- Random Item Yes/No.
- Faction effects are stored separately in quest faction options.
- Quest completion can execute a command and create/send configured mail.
- Quests can chain to `nextQuestid`.

### 3.5 Quest availability / gating engine

The reusable `Availability` object can gate content using:

- Up to four dialog checks: Always / After / Before.
- Up to four quest checks: Always / After / Before / Active / NotActive / Completed / CanStart.
- Daytime: Always / Night / Day.
- Two faction conditions with Friendly / Neutral / Hostile stance and Always / Is / IsNot relation logic.
- Two scoreboard conditions: Smaller / Equal / Bigger against objective/value.
- Minimum player level.

Availability is reused by multiple systems (visibility, marks, item giver, conversation, builder/border-like utility blocks, etc.), so it is a general conditional-content engine rather than quest-only logic.

## 4. Dialog system

- Global dialog manager separates **Categories / Dialogs**, with Add/New, Edit, Remove and delete confirmation.
- Dialog editor fields: Title, ID, Dialog Text editor, Availability, Faction options, Dialog options, linked quest select/remove, Select Sound, Mail Setup, Command, Hide NPC, Show Dialog Wheel, Disable Esc.
- Dialog options have: Title, Color, and type **Close / Dialog / Disabled / Role / Command Block**.
- An option can select another Dialog, create a New Dialog, or execute a Command. Command editor supports player selectors such as nearest/random/all and the mod’s dialog-player placeholder path.
- `RoleDialog` has a tree editor (`GuiRoleDialogTree`) with Start Text, nested Options, Edit, Add (`+`) and descend (`>`) navigation, allowing branch-structured conversations.
- Player dialog GUI includes wheel textures and keyboard navigation for Up / Down / Enter / Keypad Enter / Escape.

## 5. Factions

- Global faction manager: Add, Remove, Name, ID, Color, Points, Edit/New, Hidden Yes/No, Attacked behavior, Hostile Factions.
- Player faction screen shows standings and categorizes state as Friendly / Neutral / Unfriendly based on stored points/threshold logic.
- NPC advanced settings can attack hostile factions and defend faction members.
- Quest and reward systems can alter/check faction state.

## 6. Banks and economy

### NPC Bank role setup

- Withdraw Fee.
- Require Quest Yes/No and Quest ID.

### Player bank chest

- Tab selector.
- Unlock and Upgrade actions.
- Amount field.
- Deposit / Withdraw.
- Displays unlock costs, upgrade costs, Wallet, Vault and Fee.
- Server data includes MaxSlots, UnlockedSlots, BankMoney, WithdrawFee and Currency.
- `SlotNpcBankCurrency` and `CobbleDollarsHelper` show economy integration support; when Cobbledollars is detected the helper can read/add/remove balances.

## 7. Transport / fast travel

- Global transport manager can add/edit/remove transport locations/categories.
- Transporter role setup includes Name, Discovered, Start, Interaction settings.
- Player transport screen lists available destinations and exposes **Travel**.
- Player transport discovery is stored separately in `PlayerTransportData`.

## 8. Player mail / Mailman

- Mailbox view shows mailbox name, sender, time sent, read action and delete action/confirmation.
- Mail composition exposes sender, username/recipient, subject, body pages, Done, Send, Delete, Cancel and page navigation.
- Validation strings exist for bad username and missing subject, plus success feedback.
- Mail data is persisted per player (`PlayerMail`, `PlayerMailData`).
- Quest/dialog configuration can attach mail data, and the Mailman role exposes the delivery/player-mail interaction surface.

## 9. Roles — detailed capabilities

### 9.1 Trader

- Standard item trader role with a player trading container.
- Every slot can expose **two item currencies** (`getCurrency1`, `getCurrency2`) and one sold/output item (`getSold`).
- Setup supports a reusable **Linked Market / Market Name**.
- Ignore Damage and Ignore NBT toggles.
- Payment Mode supports item-based and money/economy paths.
- Money path is backed by per-slot `long[] slotPrices`; player GUI displays Balance and sufficient/insufficient states.

### 9.2 Follower role

- Hireable owner-bound NPC.
- Stores owner UUID, following/waiting state, per-rate payment data, hire/farewell dialogs, days hired/time, inventory, and GUI/soulstone flags.
- Setup: rate row, hire text with `{days}`, days, farewell text with `{player}`, Infinite Days, GUI Disabled, Allow Soulstone, Reset.
- Player UI: Waiting / Following / Hire; follower panel renders health and days remaining/last-day state.

### 9.3 Bank role

- Connects an NPC to a global bank definition (`bankId`).
- Supports withdraw fee and quest requirement.
- Player-facing vault supports tab unlock/upgrade and currency deposit/withdraw as described above.

### 9.4 Transporter role

- Links to a transport location/name, controls discovery/unlock behavior, and opens travel selection.

### 9.5 Mailman / Postman role

- Opens and manages asynchronous player mail; integrates with mailbox and mail-writing screens.

### 9.6 Companion role — **WIP-labelled in selector**

- Owner-bound companion implementation with inventory, talents, aging/stage, food stats, following/defending behavior and companion-job logic.
- Setup exposes Stage, Update Yes/No and Age.
- Player stats screen exposes Name, Owner, Age, Strength, Level, Job.
- Player companion navigation includes Stats / Talent / Inventory / Job.
- Companion jobs enum: None / Soldier / Guard / Farmer / Miner / Shop / Robot.
- Companion talents enum: Inventory / Armor / Sword / Ranged / Acrobats / Intel.
- Talent screen spends/uses quest EXP through `+` controls.

### 9.7 Dialog role

- Branching role-driven dialog tree with start text and nested options, tied to dialog/quest IDs where configured.

### 9.8 Pokémon Trader — conditional ecosystem role

- Requires species and optional form criteria.
- Stores the Pokémon to give as NBT.
- Supports extra required item inventory, per-player or global limits, max trades, cooldown type/value, and persisted player trade counts/timestamps.
- Setup GUI: Requirements → required species/form → extra item slots and quantity → Limits Disabled/Enabled → Player/Global → Max Trades → Cooldown minutes/hours → To Give → Pokémon Editor → Level mode Fixed/Mirror.
- Player screen computes requirement status, item completion/missing states, received Pokémon details, and Trade action.

### 9.9 Move Tutor — conditional, new in 1.5.0

- GUI explicitly warns/references TM Craft requirement/integration.
- Modes: **Manual / Random / Species**.
- `SLOTS = 6`.
- Random rotation interval is `43,200,000 ms` = **12 hours**; rotation mode supports Fixed / 12H.
- Species mode supports up to **10 species**, with **4 manual moves** per species offer.
- Tier constants: Low / Mid / High / Status; separate normal/special tier arrays.
- Species matching can derive effective offers from the player’s party species.
- Player screen: Learn, No Offers, Back, Cancel.
- Move picker categories include All / TM / Tutor / Egg / Star.

### 9.10 Pokémon Trainer — conditional ecosystem role

- Configurable aggro/range and battle-on-interact / battle-on-sight behavior.
- Challenge, Win and Lose lines.
- Configurable party list with per-slot species/level data.
- Cooldown with seconds/minutes/hours UI paths and per-player cooldown map.
- Trainer classes exposed in GUI: Normal / Gym Leader / League / Elite / Champion / Legendary / Rocket / Custom.
- Optional automatic/custom Prefix.
- Normal and Special reward sets can hold multiple items, EXP, faction ID/points and multiple commands.
- Battle lifecycle methods track active target, win/loss, cooldown, reward delivery and reset.

### 9.11 Move Relearner — implementation exists but selector skips it

- `RoleMoveRelearner` exists and `DataAdvanced` can instantiate role type 9.
- A dedicated GUI exists and describes a Pokémon move-relearning interaction.
- **However, `GuiNpcAdvanced` maps its visible selector from role 8 directly to role 10, so role 9 is not normally selectable there in 1.5.0.** Do not document it as a normal exposed role without this caveat.

## 10. Jobs — detailed capabilities

### Bard
- Select Sound; Jukebox / Background modes; Loops; On Distance; Has Off Range; Off Distance. Data includes min/max range, streaming/background and looping state.

### Healer
- Configurable Range, Speed, target affect mode (Friendly / Unfriendly / All), Potency, Available Effects and Current Effects.
- Applies configured potion effects to nearby living targets according to faction/target mode.
- **Confirmed bytecode:** scans offsets from -6 to +6 around the NPC for either `cobblemon:healing_machine` or `pixelmon:healer`; only with such a block nearby does it call party `heal()` for supported Pokémon integrations.

### Guard
- Target presets Animals / Mobs / Creepers plus Available Targets / Current Targets lists and move-one/move-all controls.

### Item Giver
- Give methods: Random / All / Not Owned / When None Owned / Chained.
- Cooldown modes: Timer / Once / Daily, plus cooldown value.
- Items to Give inventory and Availability editor.

### Follower job
- Follows another named NPC; data stores target/following NPC and range/name.

### Spawner
- Six configured clone/spawn slots are exposed in setup.
- Spawn behavior includes One / All / Random.
- Options include spawned entity death behavior, despawn behavior, offsets/targeting, cooldown and tracked spawned entities.

### Conversation
- Two participant/name fields, delay, line timing, range, optional quest link, Availability, and mode Always / Player Nearby.
- Runtime data tracks participating NPCs, line sequence, delays, current/next line and range.

### Chunk Loader
- Dedicated job implementation tracks chunks and last-seen player/tick state. There is no rich dedicated configuration screen recovered like Bard/Guard; selection is exposed in the job dropdown.

### Puppet
- Static/animated posing system with body-part configuration for head, left/right arms, body, left/right legs.
- Each part has start/end pose data; setup exposes Standing / Walking / Attacking / Animation states, animation steps, speed, and body-part controls.

### Builder
- Drives schematic/build state through the Builder block/schematic system.
- Builder block GUI: Preview, Width, Length, Height, Enabled/Finished/Started, Y Offset, Rotation 0/90/180/270, Availability, Instant Build.

### Farmer
- Automated farming implementation tracks farmland/block state and pickup behavior.
- Item Picked action: Do Nothing / Chest / Drop.

## 11. Global managers

### Banks manager
- Add / Remove / New banks; upgrade-state strings include Can Upgrade / Can’t Upgrade / Upgraded. Banks have tab/slot/unlock/upgrade data used by the player vault.

### Factions manager
- Add/remove/new, Name, ID, Color, Points, Hidden, Attacked, Hostile Factions.

### Dialogs manager
- Category/dialog navigation, Add/New/Edit/Remove, delete confirmation.

### Quests manager
- Category/quest navigation, Add/New/Edit/Remove, delete confirmation.

### Transport manager
- Add/Edit/Remove/Open/Back around categories and transport locations.

### PlayerData manager
- All Players and Delete.
- Data categories exposed: Players / Quest / Dialog / Transport / Bank / Factions.
- Backing player data additionally has mail, item-giver and scripting-related records in dedicated data classes.

### Recipes manager — **Broken-labelled**
- Modes include Global / Carpentry Bench.
- Add/remove/new recipe flow; Ignore Damage and Ignore NBT controls.
- Player `GuiRecipes` and Carpentry Bench UI/classes exist, but the Global menu itself labels Recipes **(Broken)** in this build.

### Natural Spawns — **WIP-labelled**
- Add/remove/new spawn configuration UI and `SpawnController`/`SpawnData` exist.
- Global menu labels this feature **(WIP)** in 1.5.0.

### Linked NPC manager
- Add/remove/new reusable linked NPC data.
- Linked data serializes AI, Display, Stats, Advanced, Inventory, model and transform-related state, enabling shared/reusable NPC configuration payloads.

## 12. Creative/admin tools, blocks and utility GUIs

### Registered tool items

- `mynpcs:npcwand`
- `mynpcs:npcmobcloner`
- `mynpcs:npcscripter`
- `mynpcs:npcmovingpath`
- `mynpcs:npcmounter`
- `mynpcs:npcteleporter`
- `mynpcs:npcjarempty`
- `mynpcs:npcjarfilled`
- `mynpcs:scripted_item`
- `mynpcs:nbt_book`
- `mynpcs:npcscripteddoortool`

### Registered utility blocks

- `mynpcs:npcredstoneblock`
- `mynpcs:npcmailbox`
- `mynpcs:npcmailbox2`
- `mynpcs:npcmailbox3`
- `mynpcs:npcwaypoint`
- `mynpcs:npcborder`
- `mynpcs:npcscripted`
- `mynpcs:npcscripteddoor`
- `mynpcs:npcbuilderblock`
- `mynpcs:npccopyblock`
- `mynpcs:npccarpentybench`
- `mynpcs:nether_furnace`

Note: `npccarpentybench` is the literal registry spelling recovered from the JAR.

### Cloner / Mob Spawner tool
- Tabs 1–9.
- Sources/modes: Clones / Entities / Server.
- Spawn action and Mob Spawner handling.
- Add/save screen chooses tab 1–9, Client/Server and handles overwrite confirmation.
- Mounter variant has Mount and Mount Player actions.
- Selector variant provides Done/Cancel.

### Moving Path tool
- Path point control includes Down / Up / Delete; path data is also consumed by NPC movement AI.

### Teleporter tool
- Dimension selector lists Dimensions and provides TP.

### Remote Editor
- Edit / Delete / Reset / TP / Reset all / Freeze operations are present in GUI.

### NBT Book
- Edit / Close / Save; stores/edits NBT payload and entity/data identifiers.

### Builder / Copy / Border / Redstone / Waypoint blocks
- **Builder:** schematic dimensions/state, Y offset, rotation, availability, preview and instant build.
- **Copy:** Height/Width/Length/Name/Save/Cancel for structure capture.
- **Border:** Availability, Height, Message, Done.
- **Redstone:** Availability, Detailed Yes/No, on/off-distance-like coordinate/distance controls.
- **Waypoint / location block:** Name, Range, Done; used by location-style quest/world logic.

### NPC Jar / soulstone-style capture
- Empty and filled NPC Jar items are registered, and follower setup has an “Allow Soulstone” control. Config also contains `SoulStoneAnimals` and `SoulStoneNPCs` flags. The exact user-facing capture restrictions can therefore be configured; this document does not invent additional entity classes beyond what those flags/code paths establish.

## 13. Nether Furnace

- Registered `mynpcs:nether_furnace` block and bundled recipe `data/mynpcs/recipe/nether_furnace.json`.
- Public 1.5.0 description states the furnace uses its netherrack base for fuel-free continuous burning while smeltable input is present, can be extinguished with a shovel, and reignited with flint-and-steel. The block/recipe implementation is present in the JAR; this behavior is also explicitly documented by the author.

## 14. Scripting system

### Script editor GUI
- Settings, add (`+`), Hide Functions / Show Functions, Clear, Paste, Copy, Remove, Load Script.
- Language selector.
- Enabled Yes/No.
- Open Folder, Website, API Doc buttons.
- Script list separates Available Scripts and Loaded Scripts with `>`, `<`, `>>`, `<<`, Done.
- Global scripting GUI exposes Players and **Forge (BROKEN)**; the broken label is hard-coded in this build.

### Script engines
- Uses Java `javax.script.ScriptEngineManager`.
- Explicitly checks/uses `ecmascript` and attempts to register OpenJDK Nashorn (`org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory`) under JavaScript aliases/extensions.
- Code can detect/register Kotlin JSR-223 if `KotlinJsr223JvmLocalScriptEngineFactory` is available.
- Code also probes `noppes.scriptengines.ScriptEngines`, indicating optional external engine discovery.
- Default persisted script language strings include `ECMAScript`.
- Because engine factories are discovered dynamically, do **not** assume Kotlin/Lua/other languages are usable unless the corresponding engine is actually installed at runtime.

### Script persistence
- `world_data.json`
- `player_scripts.json`
- `forge_scripts.json`
- World/player/forge script containers have enabled/language/script lists and console/log data.

### Script/API event surface
The JAR exposes typed event classes for blocks, custom GUIs, dialogs, items, NPCs, players, projectiles, quests, roles and worlds. Important event names include:

- **event:** BlockEvent, CustomGuiEvent, DialogEvent, ForgeEvent, HandlerEvent, ItemEvent, MyNPCsEvent, NpcEvent, PlayerEvent, ProjectileEvent, QuestEvent, RoleEvent, WorldEvent

### Custom GUI API
The public API contains components/interfaces for: Button, Button List, Label, Text Field, Text Area, Scroll, Slider, Item Slot, Item Renderer, Entity Display, Textured Button, Textured Rectangle, Colored Line, generic component wrappers/scrollable wrappers and an Assets Selector. Custom GUI events include button, close, scroll and slot interactions.

## 15. AI engine and abilities

The NPC runtime includes goal/behavior classes for combat/targeting, abilities, animation, attack-target selection, avoidance, door breaking, shade seeking, following, job/role execution, looking, indoor movement, moving paths, panic, pounce, ranged attacks, return-to-home, sprint-to-target, transform, wander, watch-closest, water navigation and owner-target behavior.

Ability implementations present in the JAR:

- `AbilityBlock`
- `AbilityPull`
- `AbilityPush`
- `AbilitySmash`
- `AbilitySnare`
- `AbilityTeleport`

The ability trigger enum exposes `ATTACKED` and `UPDATE`. Presence of the ability implementation proves the engine capability; this analysis did **not** find a normal main-editor “Abilities” page, so these should not be presented as a clearly exposed end-user GUI feature without further runtime testing.

## 16. Pokémon / creature ecosystem integrations

### Cobblemon
- Dedicated helper, entity wrapper/player-data APIs, trainer battle actor, Pokémon editor, Pokémon Trader/Trainer, Move Tutor, healing integration, species/forms/abilities/moves/learnsets, party/PC access, battle state, exchange, move teaching and move PP-up application.
- Pokémon editor exposes Species, Level, Shiny, Form, Nature, Ability, Gender, Held Item, IVs, EVs, Moves, Move PP Up values, random/clear/min/max helpers, Done/Cancel.
- Nature selection includes the standard 25 natures in the GUI bytecode.
- Held item picker uses the Cobblemon held-item tag `cobblemon:held/is_held_item`.

### Pixelmon compatibility code
- A reflection-based `PixelmonHelper` exists with party/PC, Pokémon detection/data/model, battle, species/forms/abilities/moves/learnsets, exchange and move-data helpers.
- Healer recognizes `pixelmon:healer`.
- Move Relearner code references Pixelmon move-relearner packet class names. This is another reason to call that implementation “present” rather than claiming it is fully exposed in the normal 1.5.0 selector.

### TM Craft
- `TMCraftHelper` exposes teachable move discovery, category filtering, special-category detection and “can this Pokémon learn this move” logic.
- Move Tutor is the normal visible role tied to this integration in 1.5.0.

### Cobbledollars
- Helper exposes get balance, add balance, remove balance, and fallback command-based change logic; trader/bank money paths can use this integration when enabled.

### FTB Quests
- Helper exposes quest-completed check and quest completion.
- Advanced NPC page conditionally exposes FTB Quests configuration when integration is enabled.

### Armourer’s Workshop
- Helper hooks renderer layer add/remove events and registers integration behavior when detected.

## 17. Commands

All custom commands are registered beneath the root **`/mynpcs`**. The following literal branches/arguments were recovered from Brigadier registration bytecode. Optional ordering can depend on branch overloads; the names below are grounded, not invented.

- **clone** — `list`, `add`, `remove`, `spawn`, `grid`; arguments include `npc`, `tab`, `name`, optional `pos`, optional `display_name`, plus `length`/`width` for grid spawning.
- **config** — `leavesdecay <boolean>`, `vineinflateth <boolean>` (literal spelling in this JAR), `icemelts <boolean>`, `freezenpcs <boolean>`, `debug <boolean>`, `scripting <boolean>`, `chunkloaders <number>`, `font <font> [size]`.
- **dialog** — `reload`, `read`, `unread`, `show`; player and dialog/name arguments are registered.
- **faction** — player/faction point operations `add`, `set`, `reset`, `drop`.
- **mark** — `clear` plus mark operations using `entities`, `type`, `color`.
- **npc** — `home [pos]`, `visible <visibility>`, `delete`, `owner [player]`, `name`, `reset`, `create`; visibility suggestions include false / semi / true.
- **quest** — `start`, `finish`, `stop`, `remove`, `objective`, `reload`; player/quest/objective/value arguments are registered.
- **scene** — `time`, `reset`, `start`, `pause` with scene name and time handling.
- **schema** — `build <name> [pos] [rotation]`, `stop`, `info`, `list`; rotation suggestions 0/90/180/270.
- **script** — `reload`, `trigger <id> [args]`. Reload logic separately reports stored-data, Forge-script and player-script status.
- **slay** — types include all / mobs / animals / items / xporbs / npcs / monster / mob, with optional/ranged targeting branch.

Permission note: command registration contains permission-level checks (commonly levels 2 and 4 depending on root/subcommand). Exact authorization can also be affected by mod config (`EspiCommandOpOnly`, `OpsOnly`, `DisablePermissions`, `NpcUseOpCommands`), so do not reduce the entire tree to one universal permission level.

## 18. Configuration capabilities

Static config-backed fields recovered from `MyNpcs` include:

- `EnableScripting`
- `NashorArguments`
- `EnableChatBubbles`
- `NpcNavRange`
- `NpcNaturalSpawningChunkLimit`
- `NpcUseOpCommands`
- `EspiCommandOpOnly`
- `InventoryGuiEnabled`
- `FixUpdateFromPre_1_12`
- `DisablePermissions`
- `SceneButtonsEnabled`
- `EnableUpdateChecker`
- `FreezeNPCs`
- `OpsOnly`
- `DefaultInteractLine`
- `ChuckLoaders`
- `LeavesDecayEnabled`
- `VineGrowthEnabled`
- `IceMeltsEnabled`
- `SoulStoneAnimals`
- `SoulStoneNPCs`
- `ClonerSavePath`
- `HeadWearType`
- `FontType`
- `FontSize`
- `EnableInvisibleNpcs`
- `NpcSpeachTriggersChatEvent`
- `VerboseDebug`

Names are preserved exactly as compiled, including apparent typos such as `ChuckLoaders` and `NpcSpeachTriggersChatEvent`.

## 19. Persistence / server data

- Global data controller uses `global.dat` with `_old` / `_new` rotation names.
- Server clones use `clonednpcs.dat` / `_old` plus JSON clone storage paths.
- Linked NPC definitions use JSON files with `_new` replacement flow.
- Player data controller uses per-player JSON storage.
- Scripts use the JSON files listed in the scripting section.
- Schematic controller loads bundled and external `.schematic` and `.schem` files.
- 1.5.0 public changelog specifically states NPC persistence was overhauled to prevent disappearance/failure to save across chunk unloads, crashes or server reboots.

## 20. Schematics / builder capability

- Supports legacy `.schematic` and Sponge-style `.schem` loading paths.
- Bundles **28** named schematics: archery range, bakery, barn, building site, chapel, church, gate, glassworks, guard tower, guild house, house, small house, inn, library, lighthouse, mill, observatory, ship, shop, three stalls, three tier houses, tower, wall and wall corner.
- Builder/command systems can build by name at a position and rotation; builder block provides preview, dimensions, state and instant-build controls.

## 21. Resource-pack / datapack surface bundled in the mod

### Datapack-side resources

- `data/mynpcs/damage_type/npc.json`
- `data/mynpcs/legacy_blockids.json`
- `data/mynpcs/markovnames/ancient_greek_female.txt`
- `data/mynpcs/markovnames/ancient_greek_male.txt`
- `data/mynpcs/markovnames/aztec_given.txt`
- `data/mynpcs/markovnames/japanese_given_female.txt`
- `data/mynpcs/markovnames/japanese_given_male.txt`
- `data/mynpcs/markovnames/japanese_surnames.txt`
- `data/mynpcs/markovnames/mynpcs_classic.txt`
- `data/mynpcs/markovnames/old_norse_bothgenders.txt`
- `data/mynpcs/markovnames/roman_cognomina.txt`
- `data/mynpcs/markovnames/roman_nomina.txt`
- `data/mynpcs/markovnames/roman_praenomina.txt`
- `data/mynpcs/markovnames/saami_bothgenders.txt`
- `data/mynpcs/markovnames/slavic_given.txt`
- `data/mynpcs/markovnames/slavic_given_alt.txt`
- `data/mynpcs/markovnames/spanish_given_female.txt`
- `data/mynpcs/markovnames/spanish_given_male.txt`
- `data/mynpcs/markovnames/spanish_surnames.txt`
- `data/mynpcs/markovnames/welsh_female.txt`
- `data/mynpcs/markovnames/welsh_male.txt`
- `data/mynpcs/recipe/nether_furnace.json`
- `data/mynpcs/schematics/archery_range.schematic`
- `data/mynpcs/schematics/bakery.schematic`
- `data/mynpcs/schematics/barn.schematic`
- `data/mynpcs/schematics/building_site.schematic`
- `data/mynpcs/schematics/chapel.schematic`
- `data/mynpcs/schematics/church.schematic`
- `data/mynpcs/schematics/gate.schematic`
- `data/mynpcs/schematics/glassworks.schematic`
- `data/mynpcs/schematics/guard_tower.schematic`
- `data/mynpcs/schematics/guild_house.schematic`
- `data/mynpcs/schematics/house.schematic`
- `data/mynpcs/schematics/house_small.schematic`
- `data/mynpcs/schematics/inn.schematic`
- `data/mynpcs/schematics/library.schematic`
- `data/mynpcs/schematics/lighthouse.schematic`
- `data/mynpcs/schematics/mill.schematic`
- `data/mynpcs/schematics/observatory.schematic`
- `data/mynpcs/schematics/ship.schematic`
- `data/mynpcs/schematics/shop.schematic`
- `data/mynpcs/schematics/stall.schematic`
- `data/mynpcs/schematics/stall2.schematic`
- `data/mynpcs/schematics/stall3.schematic`
- `data/mynpcs/schematics/tier_house1.schematic`
- `data/mynpcs/schematics/tier_house2.schematic`
- `data/mynpcs/schematics/tier_house3.schematic`
- `data/mynpcs/schematics/tower.schematic`
- `data/mynpcs/schematics/wall.schematic`
- `data/mynpcs/schematics/wall_corner.schematic`

Notable datapack content: a custom NPC damage type, legacy block ID mapping, the Nether Furnace recipe, bundled schematics, and Markov name corpora.

### Markov/name-generation corpora

`ancient_greek_female.txt`, `ancient_greek_male.txt`, `aztec_given.txt`, `japanese_given_female.txt`, `japanese_given_male.txt`, `japanese_surnames.txt`, `mynpcs_classic.txt`, `old_norse_bothgenders.txt`, `roman_cognomina.txt`, `roman_nomina.txt`, `roman_praenomina.txt`, `saami_bothgenders.txt`, `slavic_given.txt`, `slavic_given_alt.txt`, `spanish_given_female.txt`, `spanish_given_male.txt`, `spanish_surnames.txt`, `welsh_female.txt`, `welsh_male.txt`

### Resource-pack-side surface

- Large GUI texture set under `assets/mynpcs/textures/gui`.
- NPC/entity/model textures, sounds/localization/assets are bundled under `assets/mynpcs`.
- Texture, sound and asset-selection GUIs exist, including texture selector, sound selector and generic assets selector APIs.

## 22. Registered entities

- `mynpcs:npcpony`
- `mynpcs:npccrystal`
- `mynpcs:npcslime`
- `mynpcs:npcdragon`
- `mynpcs:npcgolem`
- `mynpcs:customnpc`
- `mynpcs:customnpc64x32`
- `mynpcs:customnpcalex`
- `mynpcs:customnpcclassic`
- `mynpcs:customnpcchairmount`
- `mynpcs:customnpcprojectile`

## 23. Public API capability surface

The JAR contains **197 top-level public API classes/interfaces** (excluding inner classes in this count). Major capability groups:

- NPC/entity wrappers: generic entity/living/player/animal/monster/mob/villager/projectile/item plus custom NPC and Pokémon wrappers.
- NPC data interfaces: display, stats, AI, melee, ranged, inventory, advanced, role/job and line/mark data.
- Role/job APIs: Bard, Builder, Farmer, Follower, Puppet, Spawner, Dialog, Follower role, Trader, Transporter.
- Handler APIs for clones, dialogs, factions, recipes, quests, scoreboards and related global data.
- Item/block/world wrappers and scripted block/door/text-plane APIs.
- Custom GUI creation/component interfaces and sub-GUI asset/availability support.
- Event classes across Block, CustomGui, Dialog, Forge, Handler, Item, NPC, Player, Projectile, Quest, Role and World domains.

### API top-level class/interface inventory

- `espi.mynpcs.api.IContainer`
- `espi.mynpcs.api.IDamageSource`
- `espi.mynpcs.api.IDimension`
- `espi.mynpcs.api.INbt`
- `espi.mynpcs.api.IPlayerSkin`
- `espi.mynpcs.api.IPos`
- `espi.mynpcs.api.IRayTrace`
- `espi.mynpcs.api.IScoreboard`
- `espi.mynpcs.api.IScoreboardObjective`
- `espi.mynpcs.api.IScoreboardScore`
- `espi.mynpcs.api.IScoreboardTeam`
- `espi.mynpcs.api.ITimers`
- `espi.mynpcs.api.IWorld`
- `espi.mynpcs.api.MyNPCsException`
- `espi.mynpcs.api.NpcAPI`
- `espi.mynpcs.api.block.IBlock`
- `espi.mynpcs.api.block.IBlockScripted`
- `espi.mynpcs.api.block.IBlockScriptedDoor`
- `espi.mynpcs.api.block.ITextPlane`
- `espi.mynpcs.api.constants.AnimationType`
- `espi.mynpcs.api.constants.EntitiesType`
- `espi.mynpcs.api.constants.GuiComponentType`
- `espi.mynpcs.api.constants.ItemType`
- `espi.mynpcs.api.constants.JobType`
- `espi.mynpcs.api.constants.MarkType`
- `espi.mynpcs.api.constants.OptionType`
- `espi.mynpcs.api.constants.ParticleType`
- `espi.mynpcs.api.constants.PotionEffectType`
- `espi.mynpcs.api.constants.QuestType`
- `espi.mynpcs.api.constants.RoleType`
- `espi.mynpcs.api.constants.SideType`
- `espi.mynpcs.api.entity.IAnimal`
- `espi.mynpcs.api.entity.IArrow`
- `espi.mynpcs.api.entity.ICobblemon`
- `espi.mynpcs.api.entity.ICustomNpc`
- `espi.mynpcs.api.entity.IEntity`
- `espi.mynpcs.api.entity.IEntityItem`
- `espi.mynpcs.api.entity.IEntityLiving`
- `espi.mynpcs.api.entity.IMob`
- `espi.mynpcs.api.entity.IMonster`
- `espi.mynpcs.api.entity.IPixelmon`
- `espi.mynpcs.api.entity.IPlayer`
- `espi.mynpcs.api.entity.IProjectile`
- `espi.mynpcs.api.entity.IThrowable`
- `espi.mynpcs.api.entity.IVillager`
- `espi.mynpcs.api.entity.data.ICobblemonPlayerData`
- `espi.mynpcs.api.entity.data.IData`
- `espi.mynpcs.api.entity.data.ILine`
- `espi.mynpcs.api.entity.data.IMark`
- `espi.mynpcs.api.entity.data.INPCAdvanced`
- `espi.mynpcs.api.entity.data.INPCAi`
- `espi.mynpcs.api.entity.data.INPCDisplay`
- `espi.mynpcs.api.entity.data.INPCInventory`
- `espi.mynpcs.api.entity.data.INPCJob`
- `espi.mynpcs.api.entity.data.INPCMelee`
- `espi.mynpcs.api.entity.data.INPCRanged`
- `espi.mynpcs.api.entity.data.INPCRole`
- `espi.mynpcs.api.entity.data.INPCStats`
- `espi.mynpcs.api.entity.data.IPixelmonPlayerData`
- `espi.mynpcs.api.entity.data.IPlayerMail`
- `espi.mynpcs.api.entity.data.role.IJobBard`
- `espi.mynpcs.api.entity.data.role.IJobBuilder`
- `espi.mynpcs.api.entity.data.role.IJobFarmer`
- `espi.mynpcs.api.entity.data.role.IJobFollower`
- `espi.mynpcs.api.entity.data.role.IJobPuppet`
- `espi.mynpcs.api.entity.data.role.IJobSpawner`
- `espi.mynpcs.api.entity.data.role.IRoleDialog`
- `espi.mynpcs.api.entity.data.role.IRoleFollower`
- `espi.mynpcs.api.entity.data.role.IRoleTrader`
- `espi.mynpcs.api.entity.data.role.IRoleTransporter`
- `espi.mynpcs.api.event.BlockEvent`
- `espi.mynpcs.api.event.CustomGuiEvent`
- `espi.mynpcs.api.event.DialogEvent`
- `espi.mynpcs.api.event.ForgeEvent`
- `espi.mynpcs.api.event.HandlerEvent`
- `espi.mynpcs.api.event.ItemEvent`
- `espi.mynpcs.api.event.MyNPCsEvent`
- `espi.mynpcs.api.event.NpcEvent`
- `espi.mynpcs.api.event.PlayerEvent`
- `espi.mynpcs.api.event.ProjectileEvent`
- `espi.mynpcs.api.event.QuestEvent`
- `espi.mynpcs.api.event.RoleEvent`
- `espi.mynpcs.api.event.WorldEvent`
- `espi.mynpcs.api.function.gui.GuiComponentClicked`
- `espi.mynpcs.api.function.gui.GuiComponentUpdate`
- `espi.mynpcs.api.function.gui.GuiItemSlotUpdate`
- `espi.mynpcs.api.gui.DeathMenu`
- `espi.mynpcs.api.gui.DisplayMenu`
- `espi.mynpcs.api.gui.HealthMenu`
- `espi.mynpcs.api.gui.IAssetsSelector`
- `espi.mynpcs.api.gui.IButton`
- `espi.mynpcs.api.gui.IButtonList`
- `espi.mynpcs.api.gui.IColoredLine`
- `espi.mynpcs.api.gui.IComponentsScrollableWrapper`
- `espi.mynpcs.api.gui.IComponentsWrapper`
- `espi.mynpcs.api.gui.ICustomGui`
- `espi.mynpcs.api.gui.ICustomGuiComponent`
- `espi.mynpcs.api.gui.IEntityDisplay`
- `espi.mynpcs.api.gui.IItemRenderer`
- `espi.mynpcs.api.gui.IItemSlot`
- `espi.mynpcs.api.gui.ILabel`
- `espi.mynpcs.api.gui.IScroll`
- `espi.mynpcs.api.gui.ISlider`
- `espi.mynpcs.api.gui.ITextArea`
- `espi.mynpcs.api.gui.ITextField`
- `espi.mynpcs.api.gui.ITexturedButton`
- `espi.mynpcs.api.gui.ITexturedRect`
- `espi.mynpcs.api.gui.InventoryMenu`
- `espi.mynpcs.api.gui.LogicMenu`
- `espi.mynpcs.api.gui.MainMenuGui`
- `espi.mynpcs.api.gui.MeleeMenu`
- `espi.mynpcs.api.gui.ModelMenu`
- `espi.mynpcs.api.gui.MovementMenu`
- `espi.mynpcs.api.gui.subgui.AssetsGui`
- `espi.mynpcs.api.gui.subgui.AvailabilityGui`
- `espi.mynpcs.api.gui.subgui.SelectorGui`
- `espi.mynpcs.api.handler.ICloneHandler`
- `espi.mynpcs.api.handler.IDialogHandler`
- `espi.mynpcs.api.handler.IFactionHandler`
- `espi.mynpcs.api.handler.IQuestHandler`
- `espi.mynpcs.api.handler.IRecipeHandler`
- `espi.mynpcs.api.handler.data.IAvailability`
- `espi.mynpcs.api.handler.data.IDialog`
- `espi.mynpcs.api.handler.data.IDialogCategory`
- `espi.mynpcs.api.handler.data.IDialogOption`
- `espi.mynpcs.api.handler.data.IFaction`
- `espi.mynpcs.api.handler.data.IQuest`
- `espi.mynpcs.api.handler.data.IQuestCategory`
- `espi.mynpcs.api.handler.data.IQuestObjective`
- `espi.mynpcs.api.handler.data.IRecipe`
- `espi.mynpcs.api.item.IItemArmor`
- `espi.mynpcs.api.item.IItemBlock`
- `espi.mynpcs.api.item.IItemBook`
- `espi.mynpcs.api.item.IItemScripted`
- `espi.mynpcs.api.item.IItemStack`
- `espi.mynpcs.api.overlay.ILabel`
- `espi.mynpcs.api.overlay.IOverlay`
- `espi.mynpcs.api.overlay.IOverlayComponent`
- `espi.mynpcs.api.overlay.IRenderItemOverlay`
- `espi.mynpcs.api.overlay.ITexturedRect`
- `espi.mynpcs.api.wrapper.AnimalWrapper`
- `espi.mynpcs.api.wrapper.ArrowWrapper`
- `espi.mynpcs.api.wrapper.BlockPosWrapper`
- `espi.mynpcs.api.wrapper.BlockScriptedDoorWrapper`
- `espi.mynpcs.api.wrapper.BlockScriptedWrapper`
- `espi.mynpcs.api.wrapper.BlockWrapper`
- `espi.mynpcs.api.wrapper.CobblemonWrapper`
- `espi.mynpcs.api.wrapper.ContainerWrapper`
- `espi.mynpcs.api.wrapper.DamageSourceWrapper`
- `espi.mynpcs.api.wrapper.DimensionWrapper`
- `espi.mynpcs.api.wrapper.EntityItemWrapper`
- `espi.mynpcs.api.wrapper.EntityLivingBaseWrapper`
- `espi.mynpcs.api.wrapper.EntityLivingWrapper`
- `espi.mynpcs.api.wrapper.EntityWrapper`
- `espi.mynpcs.api.wrapper.ItemArmorWrapper`
- `espi.mynpcs.api.wrapper.ItemBlockWrapper`
- `espi.mynpcs.api.wrapper.ItemBookWrapper`
- `espi.mynpcs.api.wrapper.ItemScriptedWrapper`
- `espi.mynpcs.api.wrapper.ItemStackWrapper`
- `espi.mynpcs.api.wrapper.MonsterWrapper`
- `espi.mynpcs.api.wrapper.NBTWrapper`
- `espi.mynpcs.api.wrapper.NPCWrapper`
- `espi.mynpcs.api.wrapper.OverlayComponentWrapper`
- `espi.mynpcs.api.wrapper.OverlayLabelWrapper`
- `espi.mynpcs.api.wrapper.OverlayRenderItemWrapper`
- `espi.mynpcs.api.wrapper.OverlayTexturedRectWrapper`
- `espi.mynpcs.api.wrapper.OverlayWrapper`
- `espi.mynpcs.api.wrapper.PixelmonWrapper`
- `espi.mynpcs.api.wrapper.PlayerWrapper`
- `espi.mynpcs.api.wrapper.ProjectileWrapper`
- `espi.mynpcs.api.wrapper.RayTraceWrapper`
- `espi.mynpcs.api.wrapper.ScoreboardObjectiveWrapper`
- `espi.mynpcs.api.wrapper.ScoreboardScoreWrapper`
- `espi.mynpcs.api.wrapper.ScoreboardTeamWrapper`
- `espi.mynpcs.api.wrapper.ScoreboardWrapper`
- `espi.mynpcs.api.wrapper.ThrowableWrapper`
- `espi.mynpcs.api.wrapper.VillagerWrapper`
- `espi.mynpcs.api.wrapper.WorldWrapper`
- `espi.mynpcs.api.wrapper.WrapperEntityData`
- `espi.mynpcs.api.wrapper.WrapperNpcAPI`
- `espi.mynpcs.api.wrapper.gui.CustomGuiAssetsSelectorWrapper`
- `espi.mynpcs.api.wrapper.gui.CustomGuiButtonListWrapper`
- `espi.mynpcs.api.wrapper.gui.CustomGuiButtonWrapper`
- `espi.mynpcs.api.wrapper.gui.CustomGuiColoredLineWrapper`
- `espi.mynpcs.api.wrapper.gui.CustomGuiComponentWrapper`
- `espi.mynpcs.api.wrapper.gui.CustomGuiEntityDisplayWrapper`
- `espi.mynpcs.api.wrapper.gui.CustomGuiItemRendererWrapper`
- `espi.mynpcs.api.wrapper.gui.CustomGuiItemSlotWrapper`
- `espi.mynpcs.api.wrapper.gui.CustomGuiLabelWrapper`
- `espi.mynpcs.api.wrapper.gui.CustomGuiScrollWrapper`
- `espi.mynpcs.api.wrapper.gui.CustomGuiSliderWrapper`
- `espi.mynpcs.api.wrapper.gui.CustomGuiTextAreaWrapper`
- `espi.mynpcs.api.wrapper.gui.CustomGuiTextFieldWrapper`
- `espi.mynpcs.api.wrapper.gui.CustomGuiTexturedRectWrapper`
- `espi.mynpcs.api.wrapper.gui.CustomGuiWrapper`
- `espi.mynpcs.api.wrapper.gui.GuiComponentsScrollableWrapper`
- `espi.mynpcs.api.wrapper.gui.GuiComponentsWrapper`

## 24. Known status caveats / do-not-overclaim list

1. **Recipes is explicitly labeled `(Broken)`** in the Global menu.
2. **Natural Spawns is explicitly labeled `(WIP)`** in the Global menu.
3. **Companion is explicitly labeled `(WIP)`** in the Advanced role selector.
4. **Forge scripting is explicitly labeled `(BROKEN)`** in `GuiScriptGlobal`.
5. **Move Relearner code exists but the visible role selector skips role ID 9.** It should be treated as dormant/not normally selectable in this build.
6. Role/job API constants are older/narrower than the current runtime selector: public `RoleType` constants only enumerate classic roles through Dialog, while runtime code adds Pokémon Trader / Move Relearner / Move Tutor / Pokémon Trainer. Do not use the old API constant class alone as a full 1.5.0 role list.
7. Optional integrations are detected at runtime. Presence of helper code does not mean the external mod is bundled or active.
8. A class or API path can prove capability exists in code, but not necessarily that every path is exposed in the normal GUI. This document labels such cases instead of pretending otherwise.

## 25. GUI screen appendix — ordered controls recovered from bytecode

This appendix is intentionally exhaustive for every screen where the automated bytecode pass recovered construction labels. Values are shown in method order. Localization keys are followed by their English translation when available. Repeated literals are retained when they reflect multiple controls.

### `espi.mynpcs.client.gui.GuiBlockBuilder`

**`public void init();`**

1. `schematic.preview` → **Preview**
2. `schematic.width` → **Width**
3. `schematic.length` → **Length**
4. `schematic.height` → **Height**
5. `gui.enabled` → **Enabled**
6. `gui.finished` → **Finished**
7. `gui.started` → **Started**
8. `gui.yoffset` → **Y Offset**
9. `0`
10. `90`
11. `180`
12. `270`
13. `movement.rotation` → **Rotation**
14. `availability.options` → **Availability Options**
15. `schematic.instantBuild` → **Instant build**

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `schematic.instantBuildText` → **Are you sure you want to build this? You can not undo this action!**

**`public void setGuiData(net.minecraft.nbt.CompoundTag);`**

1. `Width`
2. `Data`

### `espi.mynpcs.client.gui.GuiBlockCopy`

**`public void init();`**

1. `schematic.height` → **Height**
2. `schematic.width` → **Width**
3. `schematic.length` → **Length**
4. `gui.name` → **Name**
5. `gui.save` → **Save**
6. `gui.cancel`

### `espi.mynpcs.client.gui.GuiBorderBlock`

**`public void init();`**

1. `availability.options` → **Availability Options**
2. `Height`
3. `Message`
4. `Done`

### `espi.mynpcs.client.gui.GuiNPCFactionSelection`

**`public void init();`**

1. `gui.back` → **Back**
2. `mco.template.button.select`

### `espi.mynpcs.client.gui.GuiNPCLinesEdit`

**`public void init();`**

1. `mco.template.button.select`

### `espi.mynpcs.client.gui.GuiNPCTransportCategoryEdit`

**`public void init();`**

1. `Title:`
2. `gui.back` → **Back**
3. `Save`

### `espi.mynpcs.client.gui.GuiNbtBook`

**`public void init();`**

1. `nbt.edit` → **Edit NBT data**
2. `gui.close` → **Close**
3. `gui.save` → **Save**
4. `at:`

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `Saved`

**`public void setGuiData(net.minecraft.nbt.CompoundTag);`**

1. `EntityId`
2. `EntityId`
3. `Data`

### `espi.mynpcs.client.gui.GuiNpcDimension`

**`public void init();`**

1. `gui.dimensions` → **Dimensions**
2. `remote.tp` → **Tp to**

### `espi.mynpcs.client.gui.GuiNpcMobSpawner`

**`public void init();`**

1. `spawner.clones` → **Clones**
2. `spawner.entities` → **Entities**
3. `gui.server` → **Server**
4. `gui.spawn` → **Spawn**
5. `spawner.mobspawner` → **Mob Spawner**
6. `Tab 1`
7. `Tab 2`
8. `Tab 3`
9. `Tab 4`
10. `Tab 5`
11. `Tab 6`
12. `Tab 7`
13. `Tab 8`
14. `Tab 9`
15. `gui.remove` → **Remove**

**`public void setGuiData(net.minecraft.nbt.CompoundTag);`**

1. `List`

### `espi.mynpcs.client.gui.GuiNpcMobSpawnerAdd`

**`public void init();`**

1. `mobspawner.saveas` → **Save as**
2. `gui.tab` → **Tab**
3. `1`
4. `2`
5. `3`
6. `4`
7. `5`
8. `6`
9. `7`
10. `8`
11. `9`
12. `clone.client` → **Client Side**
13. `clone.server` → **Server Side**
14. `gui.save` → **Save**
15. `gui.cancel`

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `clone.overwrite` → **You are about to overwrite a clone**

**`public void setGuiData(net.minecraft.nbt.CompoundTag);`**

1. `NameExists`
2. `NameExists`
3. `clone.overwrite` → **You are about to overwrite a clone**

### `espi.mynpcs.client.gui.GuiNpcMobSpawnerMounter`

**`public void init();`**

1. `spawner.clones` → **Clones**
2. `spawner.entities` → **Entities**
3. `gui.server` → **Server**
4. `spawner.mount` → **Mount by**
5. `spawner.mountplayer` → **Player mounts**
6. `Tab 1`
7. `Tab 2`
8. `Tab 3`
9. `Tab 4`
10. `Tab 5`
11. `Tab 6`
12. `Tab 7`
13. `Tab 8`
14. `Tab 9`

**`public void setGuiData(net.minecraft.nbt.CompoundTag);`**

1. `List`

### `espi.mynpcs.client.gui.GuiNpcMobSpawnerSelector`

**`public void init();`**

1. `gui.done` → **Done**
2. `gui.cancel`
3. `Tab 1`
4. `Tab 2`
5. `Tab 3`
6. `Tab 4`
7. `Tab 5`
8. `Tab 6`
9. `Tab 7`
10. `Tab 8`
11. `Tab 9`

**`public void setGuiData(net.minecraft.nbt.CompoundTag);`**

1. `List`

### `espi.mynpcs.client.gui.GuiNpcPather`

**`public void init();`**

1. `gui.down`
2. `gui.up`
3. `selectServer.delete`

### `espi.mynpcs.client.gui.GuiNpcRedstoneBlock`

**`public void init();`**

1. `availability.options` → **Availability Options**
2. `gui.detailed` → **Detailed**
3. `gui.no`
4. `gui.yes`
5. `bard.ondistance` → **On Distance**
6. `Y:`
7. `Z:`
8. `bard.offdistance` → **Off Distance**
9. `Y:`
10. `Z:`
11. `bard.ondistance` → **On Distance**
12. `bard.offdistance` → **Off Distance**
13. `Done`

### `espi.mynpcs.client.gui.GuiNpcRemoteEditor`

**`public void init();`**

1. `remote.title` → **Nearby NPCs**
2. `selectServer.edit`
3. `selectServer.delete`
4. `gui.reset` → **Reset**
5. `remote.tp` → **Tp to**
6. `remote.resetall` → **Reset All**
7. `remote.freeze` → **Freeze NPCs**

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `gui.deleteMessage` → **Are you sure you want to delete %s?**

### `espi.mynpcs.client.gui.GuiNpcWaypoint`

**`public void init();`**

1. `gui.name` → **Name**
2. `gui.range` → **Range**
3. `Done`

### `espi.mynpcs.client.gui.SubGuiColorSelector`

**`public void init();`**

1. `gui.done` → **Done**

### `espi.mynpcs.client.gui.SubGuiEditText`

**`public void init();`**

1. `gui.done` → **Done**
2. `gui.cancel`

### `espi.mynpcs.client.gui.SubGuiMailmanSendSetup`

**`public void init();`**

1. `mailbox.subject` → **Subject**
2. `mailbox.sender` → **Sender**
3. `mailbox.write` → **Write Message**
4. `quest.quest` → **Quest**
5. `gui.select` → **Select**
6. `X`
7. `gui.done` → **Done**
8. `gui.cancel`

### `espi.mynpcs.client.gui.SubGuiNpcAvailability`

**`public void init();`**

1. `availability.available` → **Available**
2. `availability.selectdialog` → **Select Dialog**
3. `availability.selectquest` → **Select Quest**
4. `availability.selectscoreboard` → **Select Scoreboard**
5. `availability.always` → **Always**
6. `availability.is` → **Is**
7. `availability.isnot` → **Is not**
8. `faction.friendly` → **Friendly**
9. `faction.neutral` → **Neutral**
10. `faction.unfriendly` → **Unfriendly**
11. `availability.selectfaction` → **Select Faction**
12. `X`
13. `availability.always` → **Always**
14. `availability.is` → **Is**
15. `availability.isnot` → **Is not**
16. `faction.friendly` → **Friendly**
17. `faction.neutral` → **Neutral**
18. `faction.unfriendly` → **Unfriendly**
19. `availability.selectfaction` → **Select Faction**
20. `X`
21. `availability.daytime` → **Daytime**
22. `availability.wholeday` → **Whole day**
23. `availability.night` → **During the night**
24. `availability.day` → **During the day**
25. `availability.minlevel` → **Exp lvl**
26. `gui.done` → **Done**

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `availability.selectfaction` → **Select Faction**
2. `availability.selectfaction` → **Select Faction**

**`public void setGuiData(net.minecraft.nbt.CompoundTag);`**

1. `Slot`

### `espi.mynpcs.client.gui.SubGuiNpcAvailabilityDialog`

**`public void init();`**

1. `availability.available` → **Available**
2. `availability.always` → **Always**
3. `availability.after` → **After**
4. `availability.before` → **Before**
5. `availability.selectdialog` → **Select Dialog**
6. `X`
7. `availability.always` → **Always**
8. `availability.after` → **After**
9. `availability.before` → **Before**
10. `availability.selectdialog` → **Select Dialog**
11. `X`
12. `availability.always` → **Always**
13. `availability.after` → **After**
14. `availability.before` → **Before**
15. `availability.selectdialog` → **Select Dialog**
16. `X`
17. `availability.always` → **Always**
18. `availability.after` → **After**
19. `availability.before` → **Before**
20. `availability.selectdialog` → **Select Dialog**
21. `X`
22. `gui.done` → **Done**

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `availability.selectdialog` → **Select Dialog**
2. `availability.selectdialog` → **Select Dialog**
3. `availability.selectdialog` → **Select Dialog**
4. `availability.selectdialog` → **Select Dialog**

### `espi.mynpcs.client.gui.SubGuiNpcAvailabilityQuest`

**`public void init();`**

1. `availability.available` → **Available**
2. `availability.always` → **Always**
3. `availability.after` → **After**
4. `availability.before` → **Before**
5. `availability.whenactive` → **When Active**
6. `availability.whennotactive` → **When Not Active**
7. `availability.completed` → **Completed**
8. `availability.canStart` → **Can Start**
9. `availability.selectquest` → **Select Quest**
10. `X`
11. `availability.always` → **Always**
12. `availability.after` → **After**
13. `availability.before` → **Before**
14. `availability.whenactive` → **When Active**
15. `availability.whennotactive` → **When Not Active**
16. `availability.completed` → **Completed**
17. `availability.canStart` → **Can Start**
18. `availability.selectquest` → **Select Quest**
19. `X`
20. `availability.always` → **Always**
21. `availability.after` → **After**
22. `availability.before` → **Before**
23. `availability.whenactive` → **When Active**
24. `availability.whennotactive` → **When Not Active**
25. `availability.completed` → **Completed**
26. `availability.canStart` → **Can Start**
27. `availability.selectquest` → **Select Quest**
28. `X`
29. `availability.always` → **Always**
30. `availability.after` → **After**
31. `availability.before` → **Before**
32. `availability.whenactive` → **When Active**
33. `availability.whennotactive` → **When Not Active**
34. `availability.completed` → **Completed**
35. `availability.canStart` → **Can Start**
36. `availability.selectquest` → **Select Quest**
37. `X`
38. `gui.done` → **Done**

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `availability.selectquest` → **Select Quest**
2. `availability.selectquest` → **Select Quest**
3. `availability.selectquest` → **Select Quest**
4. `availability.selectquest` → **Select Quest**

### `espi.mynpcs.client.gui.SubGuiNpcAvailabilityScoreboard`

**`public void init();`**

1. `availability.available` → **Available**
2. `availability.smaller` → **Smaller**
3. `availability.equals` → **Equals**
4. `availability.bigger` → **Bigger**
5. `availability.smaller` → **Smaller**
6. `availability.equals` → **Equals**
7. `availability.bigger` → **Bigger**
8. `gui.done` → **Done**

### `espi.mynpcs.client.gui.SubGuiNpcBiomes`

**`public void init();`**

1. `spawning.availableBiomes` → **Available Biomes**
2. `spawning.spawningBiomes` → **Spawning Biomes**
3. `>`
4. `<`
5. `>>`
6. `<<`
7. `gui.done` → **Done**

### `espi.mynpcs.client.gui.SubGuiNpcCommand`

**`public void init();`**

1. `advMode.command`
2. `advMode.nearestPlayer`
3. `advMode.randomPlayer`
4. `advMode.allPlayers`
5. `dialog.commandoptionplayer` → **"@dp" to target the player using this**
6. `gui.done` → **Done**

### `espi.mynpcs.client.gui.SubGuiNpcFactionOptions`

**`public void init();`**

1. `1:`
2. `gui.decrease` → **Decrease**
3. `gui.increase` → **Increase**
4. `faction.points` → **Points**
5. `X`
6. `2:`
7. `gui.decrease` → **Decrease**
8. `gui.increase` → **Increase**
9. `faction.points` → **Points**
10. `X`
11. `gui.increase` → **Increase**
12. `gui.decrease` → **Decrease**
13. `10`
14. `gui.add` → **Add**
15. `gui.done` → **Done**

### `espi.mynpcs.client.gui.SubGuiNpcFactionPoints`

**`public void init();`**

1. `faction.default` → **Default**
2. `faction.unfriendly` → **Unfriendly**
3. `faction.neutral` → **Neutral**
4. `faction.neutral` → **Neutral**
5. `faction.friendly` → **Friendly**
6. `gui.done` → **Done**

### `espi.mynpcs.client.gui.SubGuiNpcMeleeProperties`

**`public void init();`**

1. `stats.meleestrength` → **Melee Strength**
2. `guihint.npcdamage` → **Set any value for the damage your character deals (damage does not depend on the weapon you give the NPC)**
3. `stats.meleerange` → **Melee Range**
4. `guihint.npcattackrange` → **If your character attacks with a long weapon, specify a value greater than 1 here (attack distance is measured in blocks)**
5. `stats.meleespeed` → **Melee Speed**
6. `guihint.npcattackspeed` → **Adjust the attack speed of your character (higher values result in slower attacks)**
7. `enchantment.minecraft.knockback`
8. `guihint.npcknockback` → **The character can knock back the target if you specify a number no greater than 4**
9. `stats.meleeeffect` → **Melee Effect**
10. `guihint.npceffect` → **Choose any of the provided effects and set its duration in seconds**
11. `gui.time` → **Time**
12. `stats.amplify` → **Amplified**
13. `0`
14. `1`
15. `2`
16. `3`
17. `4`
18. `5`
19. `6`
20. `7`
21. `8`
22. `9`
23. `10`
24. `gui.done` → **Done**

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `gui.none` → **None**
2. `block.minecraft.fire`

### `espi.mynpcs.client.gui.SubGuiNpcMovement`

**`public void init();`**

1. `movement.type` → **Moving type**
2. `guihint.movement.movetype` → **Make the character either wander around or set your own route (you will need a <Npc Pather>)**
3. `ai.standing` → **Standing**
4. `ai.wandering` → **Wandering**
5. `ai.movingpath` → **Moving Path**
6. `movement.ground` → **Ground**
7. `movement.flying` → **Flying**
8. `movement.swimming` → **Swimming**
9. `movement.navigation` → **Navigation**
10. `guihint.movement.navigation` → **If you're creating a character that should swim or fly, this option will allow you to experiment with their behavior**
11. `gui.range` → **Range**
12. `gui.activerange` → **Active Range**
13. `gui.no`
14. `gui.yes`
15. `movement.wanderinteract` → **Interact with npcs**
16. `gui.no`
17. `gui.yes`
18. `movement.pauses` → **Pauses**
19. `spawner.posoffset` → **Position Offset**
20. `guihint.movement.pos` → **You can shift the NPC left-right, forward-backward, and also raise them slightly upward**
21. `X:`
22. `Y:`
23. `Z:`
24. `stats.normal` → **Normal**
25. `movement.sitting` → **Sitting**
26. `movement.lying` → **Lying**
27. `movement.hug` → **Hugging**
28. `movement.sneaking` → **Sneaking**
29. `movement.dancing` → **Dancing**
30. `movement.aiming` → **Aiming**
31. `movement.crawling` → **Crawling**
32. `movement.animation` → **Animation**
33. `guihint.movement.animation` → **Choose one of the provided animations so your character can sit, lie down, crawl, etc.**
34. `movement.body` → **Body**
35. `movement.manual` → **Manual**
36. `movement.stalking` → **Stalking**
37. `movement.head` → **Head**
38. `movement.rotation` → **Rotation**
39. `guihint.movement.rotation` → **Set a specific rotation angle for the NPC (e.g., if they are acting like a turret)**
40. `movement.rotation` → **Rotation**
41. `(0-359)`
42. `(0-359)`
43. `stats.normal` → **Normal**
44. `movement.sneaking` → **Sneaking**
45. `movement.aiming` → **Aiming**
46. `movement.dancing` → **Dancing**
47. `movement.crawling` → **Crawling**
48. `movement.hug` → **Hugging**
49. `movement.animation` → **Animation**
50. `ai.looping` → **Looping**
51. `ai.backtracking` → **Backtracking**
52. `movement.name` → **Movement**
53. `gui.no`
54. `gui.yes`
55. `movement.pauses` → **Pauses**
56. `gui.no`
57. `gui.yes`
58. `movement.stopinteract` → **Stop on interact**
59. `guihint.movement.isignore` → **Does your character rotate to you when you interact with it?**
60. `stats.movespeed` → **Move Speed**
61. `guihint.movement.speed` → **Set any speed for the NPC to make them slow or fast**
62. `gui.done` → **Done**

### `espi.mynpcs.client.gui.SubGuiNpcName`

**`public void init();`**

1. `X`
2. `markov.roman.name` → **Roman (Latin)**
3. `markov.japanese.name` → **Japanese**
4. `markov.slavic.name` → **Old Polish**
5. `markov.welsh.name` → **Breton (Welsh)**
6. `markov.sami.name` → **Lappish (Sami)**
7. `markov.oldNorse.name` → **Old Norse**
8. `markov.ancientGreek.name` → **Ancient Greek**
9. `markov.aztec.name` → **Aztec**
10. `markov.classicCNPCs.name` → **Classic My NPCs**
11. `markov.spanish.name` → **Spanish (Latino)**
12. `markov.gender.either` → **Either**
13. `markov.gender.male` → **Male**
14. `markov.gender.female` → **Female**
15. `markov.gender.name` → **Gender**
16. `markov.generate` → **Generate**

### `espi.mynpcs.client.gui.SubGuiNpcProjectiles`

**`public void init();`**

1. `effect.minecraft.strength`
2. `guihint.npcstrength` → **Here you can adjust the damage that your projectile will deal**
3. `enchantment.minecraft.knockback`
4. `guihint.npcknockbackprojectile` → **For projectile power, you can set the knockback power on hitting a target**
5. `stats.size` → **Size**
6. `guihint.npcsizeprojectile` → **You can set any size for the projectile launched by the character (maximum value is 20)**
7. `stats.speed` → **Speed**
8. `guihint.npcspeedprojectile` → **Set the speed at which your projectile should fly (you can create bullet behavior)**
9. `stats.hasgravity` → **Gravity**
10. `guihint.npcgravity` → **Set the gravity for your projectile (it can either fall at a constant rate or accelerate over time)**
11. `gui.no`
12. `gui.yes`
13. `gui.constant` → **Constant**
14. `gui.accelerate` → **Accelerate**
15. `stats.explosive` → **Explodes**
16. `guihint.npcexplodes` → **Attention! It is strongly not recommended to enable this option if you have not set the mobGriefing rule to false!**
17. `gui.none` → **None**
18. `gui.small` → **Small**
19. `gui.medium` → **Med**
20. `gui.large` → **Large**
21. `stats.rangedeffect` → **Effect**
22. `guihint.npceffectprojectile` → **You can give your projectile any effect on hitting a target (for example, poison, slowness, etc.)**
23. `stats.regular` → **No Amp**
24. `stats.amplified` → **Amp**
25. `stats.trail` → **Trail Type**
26. `guihint.npctrailprojectile` → **If you are creating a magic projectile, this option can help you**
27. `2D`
28. `3D`
29. `stats.spin` → **Spins**
30. `gui.no`
31. `gui.yes`
32. `stats.stick` → **Sticks**
33. `gui.no`
34. `gui.yes`
35. `stats.noglow` → **No Glow**
36. `stats.glows` → **Glows**
37. `gui.done` → **Done**

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `gui.none` → **None**
2. `block.minecraft.fire`

### `espi.mynpcs.client.gui.SubGuiNpcRangeProperties`

**`public void init();`**

1. `stats.accuracy` → **Accuracy(%%)**
2. `guihint.npcaccuracy` → **Here you can specify the shooting accuracy of your character (higher values result in more accurate ranged attacks)**
3. `stats.shotcount` → **Shot Count**
4. `guihint.npcshotcount` → **With this parameter, you can make the character fire more than one projectile simultaneously (if the value is greater than 1, it will resemble a shotgun)**
5. `gui.range` → **Range**
6. `guihint.npcrange` → **Specify the distance in blocks from which your character can shoot (this also depends on the aggro radius)**
7. `stats.meleerange` → **Melee Range**
8. `guihint.npcmeleerange` → **At what distance in blocks will your character start attacking in melee?**
9. `stats.mindelay` → **Min Delay**
10. `guihint.npcmindelay` → **If you want to set the cooldown between shots fired by the character, specify a number here that will not exceed the maximum delay**
11. `stats.maxdelay` → **Max Delay**
12. `guihint.npcmaxdelay` → **This parameter is similar to the low delay parameter (specify a number that will not be less than the minimum delay)**
13. `stats.burstcount` → **Burst Count**
14. `guihint.npcburstcount` → **With this parameter, you can make the character fire multiple projectiles separately**
15. `stats.burstspeed` → **Burst Rate**
16. `guihint.npcburstrate` → **If you want to simulate a machine gun, set the lowest possible value here (this also depends on delay parameters)**
17. `stats.firesound` → **Fire Sound**
18. `guihint.npcfiresound` → **What sound will play during shooting?**
19. `mco.template.button.select`
20. `stats.hitsound` → **HitSound**
21. `guihint.npchitsound` → **What sound will play after the projectile hits the target?**
22. `mco.template.button.select`
23. `stats.groundsound` → **Ground Sound**
24. `guihint.npcgroundsound` → **What sound will play after the projectile hits the ground?**
25. `mco.template.button.select`
26. `stats.aimWhileShooting` → **Aim While Shooting**
27. `guihint.npcwhileshooting` → **Will your character play aiming animation after seeing a target?**
28. `gui.no`
29. `gui.whendistant` → **When Distant**
30. `gui.whenhidden` → **When Hidden**
31. `stats.indirect` → **Shoot Indirect**
32. `guihint.npcshootindirect` → **What shooting style does your character prefer? (for example, you can simulate a catapult)**
33. `gui.done` → **Done**

### `espi.mynpcs.client.gui.SubGuiNpcResistanceProperties`

**`public void init();`**

1. `enchantment.minecraft.knockback`
2. `guihint.npcknockbackresist` → **Your character will be immune to knockback (higher values make the NPC less pushable)**
3. `item.minecraft.arrow`
4. `guihint.npcarrowsresist` → **Your character will be immune to arrows (higher values make arrows less effective against your NPC)**
5. `stats.melee` → **Melee**
6. `guihint.npcmeleeresist` → **Your character will be immune to melee attacks (higher values make melee attacks less effective against your NPC)**
7. `stats.explosion` → **Explosion**
8. `guihint.npcexplosionresist` → **Your character will be immune to explosive objects (higher values make explosions less effective against your NPC)**
9. `gui.done` → **Done**

### `espi.mynpcs.client.gui.SubGuiNpcRespawn`

**`public void init();`**

1. `stats.respawn` → **Respawn**
2. `guihint.npcrespawn` → **Will your character respawn after death?**
3. `gui.yes`
4. `gui.day` → **Day**
5. `gui.night` → **Night**
6. `gui.no`
7. `stats.naturally` → **Naturally**
8. `gui.time` → **Time**
9. `guihint.npcrespawntime` → **Specify the time after which the character should respawn each time (time is measured in seconds)**
10. `stats.deadbody` → **Hide Dead Body**
11. `guihint.npchidebody` → **U se this option to remove the body of the character after death**
12. `gui.no`
13. `gui.yes`
14. `gui.done` → **Done**

### `espi.mynpcs.client.gui.advanced.GuiNPCAdvancedLinkedNpc`

**`public void init();`**

1. `gui.clear` → **Clear**

### `espi.mynpcs.client.gui.advanced.GuiNPCDialogNpcOptions`

**`public void init();`**

1. `X`
2. `dialog.selectoption` → **Select Option**

**`public void setGuiData(net.minecraft.nbt.CompoundTag);`**

1. `Position`

### `espi.mynpcs.client.gui.advanced.GuiNPCFTBQuests`

**`public void init();`**

1. `ftbquests.requireQuest` → **Require Quest**
2. `ftbquests.questId` → **Quest ID**
3. `ftbquests.completeQuest` → **Complete Quest**
4. `gui.no`
5. `gui.yes`
6. `ftbquests.questId` → **Quest ID**

### `espi.mynpcs.client.gui.advanced.GuiNPCFactionSetup`

**`public void init();`**

1. `faction.attackHostile` → **Attack hostile factions**
2. `gui.no`
3. `gui.yes`
4. `faction.defend` → **Defend faction members**
5. `gui.no`
6. `gui.yes`
7. `faction.ondeath` → **On death**
8. `faction.points` → **Points**

### `espi.mynpcs.client.gui.advanced.GuiNPCLinesMenu`

**`public void init();`**

1. `lines.world` → **World Lines**
2. `lines.attack` → **Attack Lines**
3. `lines.interact` → **Interact Lines**
4. `lines.killed` → **Killed Lines**
5. `lines.kill` → **Kill Lines**
6. `lines.npcinteract` → **NPC Lines**
7. `lines.random` → **Random Lines**

### `espi.mynpcs.client.gui.advanced.GuiNPCMarks`

**`public void init();`**

1. `availability.options` → **Availability Options**
2. `X`
3. `gui.add` → **Add**

### `espi.mynpcs.client.gui.advanced.GuiNPCNightSetup`

**`public void init();`**

1. `menu.display` → **Display**
2. `gui.no`
3. `gui.yes`
4. `menu.stats` → **Stats**
5. `gui.no`
6. `gui.yes`
7. `menu.ai` → **AI**
8. `gui.no`
9. `gui.yes`
10. `menu.inventory` → **Inventory**
11. `gui.no`
12. `gui.yes`
13. `menu.advanced` → **Advanced**
14. `gui.no`
15. `gui.yes`
16. `role.name` → **Role**
17. `gui.no`
18. `gui.yes`
19. `job.name` → **Job**
20. `gui.no`
21. `gui.yes`
22. `advanced.editingmode` → **Editing Mode**
23. `gui.no`
24. `gui.yes`
25. `advanced.loadday` → **Load Day**
26. `advanced.loadnight` → **Load Night**

### `espi.mynpcs.client.gui.advanced.GuiNPCScenes`

**`public void init();`**

1. `gui.button` → **Button**
2. `gui.disabled` → **Disabled**
3. `gui.enabled` → **Enabled**
4. `selectServer.edit`
5. `X`
6. `gui.none` → **None**
7. `gui.add` → **Add**

### `espi.mynpcs.client.gui.advanced.GuiNPCSoundsMenu`

**`public void init();`**

1. `advanced.idlesound` → **Living Sound**
2. `gui.selectSound` → **Select Sound**
3. `advanced.angersound` → **Angry Sound**
4. `gui.selectSound` → **Select Sound**
5. `advanced.hurtsound` → **Hurt Sound**
6. `gui.selectSound` → **Select Sound**
7. `advanced.deathsound` → **Death Sound**
8. `gui.selectSound` → **Select Sound**
9. `advanced.stepsound` → **Step Sound**
10. `gui.selectSound` → **Select Sound**
11. `advanced.haspitch` → **Has Pitch**
12. `gui.no`
13. `gui.yes`

### `espi.mynpcs.client.gui.global.GuiDialogEdit`

**`public void init();`**

1. `gui.title` → **Title**
2. `ID`
3. `dialog.dialogtext` → **Dialog Text**
4. `selectServer.edit`
5. `availability.options` → **Availability Options**
6. `selectServer.edit`
7. `faction.options` → **Faction Options**
8. `selectServer.edit`
9. `dialog.options` → **Dialog Options**
10. `selectServer.edit`
11. `availability.selectquest` → **Select Quest**
12. `X`
13. `gui.selectSound` → **Select Sound**
14. `mco.template.button.select`
15. `mailbox.setup` → **Setup mail**
16. `X`
17. `selectServer.edit`
18. `advMode.command`
19. `dialog.hideNPC` → **Hide NPC**
20. `dialog.showWheel` → **Show Dialog Wheel**
21. `dialog.disableEsc` → **Disable Esc**
22. `X`

### `espi.mynpcs.client.gui.global.GuiNPCManageBanks`

**`public void init();`**

1. `gui.add` → **Add**
2. `gui.remove` → **Remove**
3. `bank.canUpgrade` → **Can Upgrade**
4. `bank.cantUpgrade` → **Can't Upgrade**
5. `bank.upgraded` → **Upgraded**

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `New`

### `espi.mynpcs.client.gui.global.GuiNPCManageDialogs`

**`public void init();`**

1. `gui.categories` → **Categories**
2. `dialog.dialogs` → **Dialogs**
3. `dialog.dialogs` → **Dialogs**
4. `selectServer.edit`
5. `gui.remove` → **Remove**
6. `gui.add` → **Add**
7. `gui.categories` → **Categories**
8. `selectServer.edit`
9. `gui.remove` → **Remove**
10. `gui.add` → **Add**

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `gui.new` → **New**
2. `gui.deleteMessage` → **Are you sure you want to delete %s?**
3. `gui.new` → **New**
4. `gui.deleteMessage` → **Are you sure you want to delete %s?**

### `espi.mynpcs.client.gui.global.GuiNPCManageFactions`

**`public void init();`**

1. `gui.add` → **Add**
2. `gui.remove` → **Remove**
3. `gui.name` → **Name**
4. `ID`
5. `gui.color` → **Color**
6. `faction.points` → **Points**
7. `selectServer.edit`
8. `faction.hidden` → **Hidden**
9. `gui.no`
10. `gui.yes`
11. `faction.attacked` → **Attacked by mobs**
12. `gui.no`
13. `gui.yes`
14. `faction.hostiles` → **Hostile Factions**

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `gui.new` → **New**

### `espi.mynpcs.client.gui.global.GuiNPCManageLinkedNpc`

**`public void init();`**

1. `gui.add` → **Add**
2. `gui.remove` → **Remove**

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `New`

### `espi.mynpcs.client.gui.global.GuiNPCManageQuest`

**`public void init();`**

1. `gui.categories` → **Categories**
2. `quest.quests` → **Quests**
3. `quest.quests` → **Quests**
4. `selectServer.edit`
5. `gui.remove` → **Remove**
6. `gui.add` → **Add**
7. `gui.categories` → **Categories**
8. `selectServer.edit`
9. `gui.remove` → **Remove**
10. `gui.add` → **Add**

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `gui.new` → **New**
2. `gui.deleteMessage` → **Are you sure you want to delete %s?**
3. `gui.new` → **New**
4. `gui.deleteMessage` → **Are you sure you want to delete %s?**

### `espi.mynpcs.client.gui.global.GuiNPCManageTransporters`

**`public void init();`**

1. `gui.add` → **Add**
2. `selectServer.edit`
3. `gui.remove` → **Remove**
4. `gui.open` → **Open**
5. `gui.back` → **Back**

### `espi.mynpcs.client.gui.global.GuiNpcManagePlayerData`

**`public void init();`**

1. `playerdata.allPlayers` → **All Players**
2. `selectServer.delete`
3. `playerdata.players` → **Players**
4. `quest.quest` → **Quest**
5. `dialog.dialog` → **Dialog**
6. `global.transport` → **Transport**
7. `role.bank` → **Bank**
8. `menu.factions` → **Factions**

### `espi.mynpcs.client.gui.global.GuiNpcManageRecipes`

**`public void init();`**

1. `menu.global` → **Global**
2. `block.mynpcs.npccarpentybench` → **Carpentry Bench**
3. `gui.add` → **Add**
4. `gui.remove` → **Remove**
5. `gui.ignoreDamage` → **Ignore damage**
6. `gui.ignoreNBT` → **Ignore NBT**

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `gui.new` → **New**

### `espi.mynpcs.client.gui.global.GuiNpcNaturalSpawns`

**`public void init();`**

1. `gui.add` → **Add**
2. `gui.remove` → **Remove**

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `gui.new` → **New**

### `espi.mynpcs.client.gui.global.GuiNpcQuestReward`

**`public void init();`**

1. `quest.randomitem` → **Random Item**
2. `gui.no`
3. `gui.yes`
4. `gui.back` → **Back**
5. `quest.exp` → **Exp**

### `espi.mynpcs.client.gui.global.GuiQuestEdit`

**`public void init();`**

1. `gui.title` → **Title**
2. `ID`
3. `quest.completedtext` → **Completed text**
4. `selectServer.edit`
5. `quest.questlogtext` → **Questlog text**
6. `selectServer.edit`
7. `quest.reward` → **Reward**
8. `selectServer.edit`
9. `gui.type` → **Type**
10. `quest.item` → **Item**
11. `quest.dialog` → **Dialog**
12. `quest.kill` → **Kill**
13. `quest.location` → **Location**
14. `quest.areakill` → **AreaKill**
15. `quest.manual` → **Manual**
16. `selectServer.edit`
17. `quest.repeatable` → **Repeatable**
18. `gui.no`
19. `gui.yes`
20. `quest.mcdaily` → **MC Daily**
21. `quest.mcweekly` → **MC Weekly**
22. `quest.rldaily` → **RL Daily**
23. `quest.rlweekly` → **RL Weekly**
24. `quest.npc` → **Complete by npc**
25. `quest.instant` → **Instant Complete**
26. `faction.options` → **Faction Options**
27. `selectServer.edit`
28. `advMode.command`
29. `selectServer.edit`
30. `mailbox.setup` → **Setup mail**
31. `X`
32. `quest.next` → **Next Quest**
33. `X`
34. `X`

### `espi.mynpcs.client.gui.global.SubGuiNpcDialogOption`

**`public void init();`**

1. `dialog.editoption` → **Edit Dialog Option**
2. `gui.title` → **Title**
3. `gui.color` → **Color**
4. `dialog.optiontype` → **Option type**
5. `gui.close` → **Close**
6. `dialog.dialog` → **Dialog**
7. `gui.disabled` → **Disabled**
8. `menu.role` → **Role**
9. `block.minecraft.command_block`
10. `availability.selectdialog` → **Select Dialog**
11. `dialog.newlinkeddialog` → **New Dialog...**
12. `advMode.command`
13. `advMode.nearestPlayer`
14. `advMode.randomPlayer`
15. `advMode.allPlayers`
16. `dialog.commandoptionplayer` → **"@dp" to target the player using this**
17. `gui.done` → **Done**

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `New Dialog`

### `espi.mynpcs.client.gui.global.SubGuiNpcDialogOptions`

**`public void init();`**

1. `dialog.options` → **Dialog Options**
2. `selectServer.edit`
3. `gui.done` → **Done**

### `espi.mynpcs.client.gui.mainmenu.GuiNPCGlobalMainMenu`

**`public void init();`**

1. `global.banks` → **Banks**
2. `menu.factions` → **Factions**
3. `dialog.dialogs` → **Dialogs**
4. `quest.quests` → **Quests**
5. `global.transport` → **Transport**
6. `global.playerdata` → **PlayerData**
7. `global.recipes` → **Recipes**
8. `(Broken)`
9. `global.naturalspawn` → **Natural Spawns**
10. `(WIP)`
11. `global.linked` → **Linked**

### `espi.mynpcs.client.gui.mainmenu.GuiNPCInv`

**`public void init();`**

1. `inv.minExp` → **Min Exp**
2. `guihint.minXP` → **Set the minimum amount of experience dropped by your NPC**
3. `inv.maxExp` → **Max Exp**
4. `guihint.maxXP` → **Set the maximum amount of experience dropped by your NPC (if you set, for example, the number 50 everywhere, then you will always receive 50 XP)**
5. `stats.normal` → **Normal**
6. `inv.auto` → **Auto Pickup**
7. `inv.npcInventory` → **NPC Inventory**
8. `inv.inventory` → **Inventory**
9. `inv.dropChance` → **Drop chance**

### `espi.mynpcs.client.gui.mainmenu.GuiNpcAI`

**`public void init();`**

1. `ai.enemyresponse` → **On Found Enemy**
2. `guihint.npconfoundenemy` → **What action will your character take upon seeing a target? (for example, they may flee in panic)**
3. `gui.retaliate` → **Retaliate**
4. `gui.panic` → **Panic**
5. `gui.retreat` → **Retreat**
6. `gui.nothing` → **Nothing**
7. `ai.shelter` → **Shelter From**
8. `guihint.npcshelterfrom` → **This parameter will make your character seek shelter indoors (for example, during the night)**
9. `gui.darkness` → **Darkness**
10. `gui.sunlight` → **Sunlight**
11. `gui.disabled` → **Disabled**
12. `ai.clearlos` → **Must See Target**
13. `guihint.npcmustseetarget` → **Is it mandatory for your character to see the target, or can it detect the target through walls?**
14. `gui.no`
15. `gui.yes`
16. `ai.door` → **Door Interact**
17. `guihint.npcdoorinteract` → **How should the character interact with a door (for example break it, like a zombies)**
18. `gui.break` → **Break**
19. `gui.open` → **Open**
20. `gui.disabled` → **Disabled**
21. `ai.swim` → **Can Swim**
22. `guihint.npccanswim` → **Make your character drown as if it is too heavy**
23. `gui.no`
24. `gui.yes`
25. `ai.return` → **Return To Start**
26. `guihint.return` → **Should the NPC return to their spawn point?**
27. `gui.no`
28. `gui.yes`
29. `ai.avoidwater` → **Avoids Water**
30. `guihint.avoidwater` → **If the NPC is following you, you can make them afraid of water (e.g., like an enderman)**
31. `gui.no`
32. `gui.yes`
33. `ai.leapattarget` → **Leap At Target**
34. `guihint.leapontarget` → **The character can constantly leap at enemies with small jumps (imitating wolf behavior)**
35. `gui.no`
36. `gui.yes`
37. `ai.movement` → **Movement**
38. `guihint.movement` → **Adjust the parameters of your NPC's movement**
39. `selectServer.edit`
40. `stats.attackInvisible` → **Attack Invisible**
41. `ai.mountcontrol` → **Mount Control**
42. `gui.no`
43. `gui.yes`

### `espi.mynpcs.client.gui.mainmenu.GuiNpcAdvanced`

**`public void init();`**

1. `role.name` → **Role**
2. `role.none` → **No Role**
3. `role.trader` → **Trader**
4. `role.follower` → **Follower**
5. `role.bank` → **Bank**
6. `role.transporter` → **Transporter**
7. `role.mailman` → **Mailman**
8. `role.companion` → **Companion**
9. `(WIP)`
10. `dialog.dialog` → **Dialog**
11. `role.pokemon_trader` → **Pokémon Trader**
12. `role.move_tutor` → **Move Tutor**
13. `role.pokemon_trainer` → **Pokémon Trainer**
14. `selectServer.edit`
15. `job.name` → **Job**
16. `job.none` → **No Job**
17. `job.bard` → **Bard**
18. `job.healer` → **Healer**
19. `job.guard` → **Guard**
20. `job.itemgiver` → **Item Giver**
21. `job.follower`
22. `job.spawner` → **Spawner**
23. `job.conversation` → **Conversation**
24. `job.chunkloader` → **Chunk Loader**
25. `job.puppet` → **Puppet**
26. `job.builder` → **Builder**
27. `job.farmer` → **Farmer**
28. `selectServer.edit`
29. `advanced.lines` → **Lines**
30. `menu.factions` → **Factions**
31. `dialog.dialogs` → **Dialogs**
32. `advanced.sounds` → **Sounds**
33. `advanced.night` → **Night**
34. `global.linked` → **Linked**
35. `advanced.scenes` → **Scenes**
36. `advanced.marks` → **Marks**
37. `advanced.ftbquests` → **FTB Quests**

**`public void setGuiData(net.minecraft.nbt.CompoundTag);`**

1. `RoleData`
2. `JobData`

### `espi.mynpcs.client.gui.mainmenu.GuiNpcDisplay`

**`public void init();`**

1. `gui.name` → **Name**
2. `guihint.npcname` → **Here you can write any name for your character**
3. `?`
4. `?`
5. `??`
6. `gui.title` → **Title**
7. `guihint.npctitle` → **If you want to specify the clan, class, or origin of the character, you can give it a prefix using this parameter**
8. `display.model` → **Model**
9. `guihint.npcmodel` → **You can choose one of the provided templates to quickly change your character appearance to any creature**
10. `selectServer.edit`
11. `display.livingAnimation` → **Has a living animation**
12. `guihint.npchasliving` → **If your character is a statue, this option can help you**
13. `gui.yes`
14. `gui.no`
15. `display.size` → **Size**
16. `guihint.npcsize` → **Specify any value that does not exceed 30 (you can configure the maximum value for this parameter in the mod configuration)**
17. `(1-30)`
18. `display.tint` → **Tint**
19. `guihint.npctint` → **Here you can change the tint of your character using the HEX color code (copy the HEX color without this symbol: #)**
20. `display.isglowing` → **Glowing**
21. `display.texture` → **Texture**
22. `guihint.npctexture` → **Choose a texture that suits your character (you can upload your own textures via .minecraft/mynpcs folder)**
23. `display.texture` → **Texture**
24. `display.player` → **Player**
25. `display.url` → **Url**
26. `mco.template.button.select`
27. `display.cape` → **Cape**
28. `guihint.npccape` → **If your character should have a cape, you can choose any of the provided options**
29. `mco.template.button.select`
30. `display.overlay` → **Overlay**
31. `guihint.npcoverlay` → **Change the texture of a specific part of the body (for example, you can change the eyes)**
32. `mco.template.button.select`
33. `display.showlayers` → **Showing Layers**
34. `display.hitbox` → **Hitbox**
35. `guihint.npchitbox` → **You can remove the hitbox of the character or add physics to it so that your character collides with objects**
36. `stats.normal` → **Normal**
37. `gui.none` → **None**
38. `hair.solid` → **Solid**
39. `display.visible` → **Visible**
40. `guihint.npcvisible` → **This parameter allows you to make your character invisible or set conditions under which the character becomes invisible**
41. `gui.yes`
42. `gui.no`
43. `gui.partly` → **Partially**
44. `availability.name` → **Availability**
45. `display.bossbar` → **Boss Bar**
46. `guihint.npcbossbar` → **If you are creating a boss, with this parameter you can display the health bar of your character**
47. `display.hide` → **Hide**
48. `display.show` → **Show**
49. `display.showAttacking` → **Show when attacking**
50. `gui.color` → **Color**
51. `guihint.npcbossbarcolor` → **Change the color of the health bar of your character (first display it using the option on the left)**
52. `color.pink` → **Pink**
53. `color.blue` → **Blue**
54. `color.red` → **Red**
55. `color.green` → **Green**
56. `color.yellow` → **Yellow**
57. `color.purple` → **Purple**
58. `color.white` → **White**

### `espi.mynpcs.client.gui.mainmenu.GuiNpcStats`

**`public void init();`**

1. `stats.health` → **Health**
2. `guihint.npchealth` → **Set any value for the health of your character (floating point numbers are not supported)**
3. `stats.aggro` → **AggroRange**
4. `guihint.npcaggrorange` → **Set the distance at which your character will notice a target**
5. `stats.creaturetype` → **Creature Type**
6. `guihint.npccreaturetype` → **Specify the creature type: if you are making a skeleton, for example, it's better to choose undead here so that the NPC has weaknesses to certain potions (a spider could have an arthropod type)**
7. `stats.normal` → **Normal**
8. `stats.undead` → **Undead**
9. `stats.arthropod` → **Arthropod**
10. `stats.respawn` → **Respawn**
11. `selectServer.edit`
12. `selectServer.edit`
13. `stats.rangedproperties` → **Ranged Props**
14. `guihint.npcrangedprops` → **This category enables your character to perform ranged attacks**
15. `selectServer.edit`
16. `stats.projectileproperties` → **Projectile Type**
17. `guihint.npcprojectiletype` → **Adjust projectile properties to achieve the desired effect**
18. `selectServer.edit`
19. `effect.minecraft.resistance`
20. `guihint.npcresistance` → **This category is for setting resistance to one of the four sources of damage**
21. `selectServer.edit`
22. `stats.fireimmune` → **Immune To Fire**
23. `guihint.npcimmunetofire` → **You can make your character resistant to fire (even if you pour a bucket of lava on it, it won't burn)**
24. `gui.no`
25. `gui.yes`
26. `stats.burninsun` → **Burns In Sun**
27. `guihint.npcburnsinsun` → **Make your character burn in the sun, just like a zombie!**
28. `gui.no`
29. `gui.yes`
30. `stats.candrown` → **Can Drown**
31. `guihint.npccandrown` → **Does your character drown underwater? (this depends on whether your NPC can swim)**
32. `gui.no`
33. `gui.yes`
34. `stats.nofalldamage` → **No Fall Damage**
35. `guihint.npcnofalldamage` → **You can also prevent the character from taking fall damage from great heights**
36. `gui.no`
37. `gui.yes`
38. `stats.potionImmune` → **Potion Immune**
39. `guihint.npcpotionimmune` → **This parameter ensures that your character does not receive any negative effects (such as poison, weakness, etc.)**
40. `ai.cobwebAffected` → **Cobweb Affected**
41. `guihint.npccobwebaffected` → **If you are creating a spider, this parameter will help you make the NPC pass through cobwebs without slowing down**
42. `gui.no`
43. `gui.yes`
44. `stats.regenhealth` → **Health Regen**
45. `guihint.npchealthregen` → **If your character should regenerate health after combat, specify any value here**
46. `stats.combatregen` → **Combat Regen**
47. `guihint.npccombatregen` → **If your character should regenerate health during combat, specify any value here**

### `espi.mynpcs.client.gui.model.GuiCreationEntities`

**`public void init();`**

1. `Reset To NPC`
2. `entity.mynpcs.customnpc` → **Custom MyNPC**
3. `gui.simpleRenderer` → **Basic Limited Renderer**

**`public void scrollClicked(double, double, int, espi.mynpcs.shared.client.gui.components.GuiCustomScrollNop);`**

1. `entity.mynpcs.customnpc` → **Custom MyNPC**
2. `minecraft:missingno`
3. `mynpcs:textures/entity/humanmale/steve.png`
4. `mynpcs:textures/entity/humanmale/steve.png`

### `espi.mynpcs.client.gui.model.GuiCreationLoad`

**`public void init();`**

1. `gui.remove` → **Remove**

### `espi.mynpcs.client.gui.model.GuiCreationScale`

**`public void init();`**

1. `scale.width` → **Width**
2. `scale.height` → **Height**
3. `scale.depth` → **Depth**
4. `scale.shared` → **Shared**
5. `gui.no`
6. `gui.yes`

### `espi.mynpcs.client.gui.model.GuiCreationScreenInterface`

**`public void init();`**

1. `gui.entity` → **Entity**
2. `gui.parts` → **Parts**
3. `gui.extra` → **Extra**
4. `gui.scale` → **Scale**
5. `gui.save` → **Save**
6. `gui.load` → **Load**
7. `X`

### `espi.mynpcs.client.gui.model.GuiPresetSave`

**`public void init();`**

1. `Save`
2. `Cancel`

### `espi.mynpcs.client.gui.player.GuiDialogInteract`

**`public boolean keyPressed(int, int, int);`**

1. `key.keyboard.up`
2. `key.keyboard.down`
3. `key.keyboard.enter`
4. `key.keyboard.keypad.enter`
5. `key.keyboard.escape`

### `espi.mynpcs.client.gui.player.GuiMailbox`

**`public void init();`**

1. `mailbox.name` → **Mailbox**
2. `mailbox.sender` → **Sender**
3. `mailbox.timesend` → **%s ago**
4. `mailbox.read` → **Read**
5. `selectServer.delete`

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `gui.deleteMessage` → **Are you sure you want to delete %s?**

### `espi.mynpcs.client.gui.player.GuiMailmanWrite`

**`public void init();`**

1. `mailbox.sender` → **Sender**
2. `mailbox.username` → **Username**
3. `mailbox.subject` → **Subject**
4. `gui.done` → **Done**
5. `mailbox.send` → **Send**
6. `selectServer.delete`
7. `gui.cancel`

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `pages`
2. `gui.deleteMessage` → **Are you sure you want to delete %s?**

### `espi.mynpcs.client.gui.player.GuiNPCBankChest`

**`public void init();`**

1. `gui.tab` → **Tab**
2. `bank.unlock` → **Unlock**
3. `bank.upgrade` → **Upgrade**
4. `bank.amount` → **Amount**
5. `0`
6. `bank.deposit` → **Deposit**
7. `bank.withdraw` → **Withdraw**

**`public void setGuiData(net.minecraft.nbt.CompoundTag);`**

1. `MaxSlots`
2. `UnlockedSlots`
3. `BankMoney`
4. `WithdrawFee`
5. `Currency`
6. `Currency`

### `espi.mynpcs.client.gui.player.GuiNpcCarpentryBench`

**`public void init();`**

1. `...`

### `espi.mynpcs.client.gui.player.GuiNpcFollower`

**`public void init();`**

1. `follower.waiting` → **Waiting**
2. `follower.following` → **Following**
3. `follower.hire` → **Hire**

### `espi.mynpcs.client.gui.player.GuiNpcFollowerHire`

**`public void init();`**

1. `follower.hire` → **Hire**

### `espi.mynpcs.client.gui.player.GuiQuestCompletion`

**`public void init();`**

1. `quest.complete` → **Complete**

### `espi.mynpcs.client.gui.player.GuiRecipes`

**`public void init();`**

1. `recipes.list` → **Recipe List**

### `espi.mynpcs.client.gui.player.GuiTransportSelection`

**`public void init();`**

1. `transporter.travel` → **Travel**

### `espi.mynpcs.client.gui.player.companion.GuiNpcCompanionStats`

**`public void init();`**

1. `gui.name` → **Name**
2. `:`
3. `companion.owner` → **Owner**
4. `:`
5. `companion.age` → **Age**
6. `:`
7. `)`
8. `companion.strength` → **Strength**
9. `:`
10. `companion.level` → **Level**
11. `:`
12. `job.name` → **Job**
13. `:`
14. `gui.none` → **None**

**`public void setGuiData(net.minecraft.nbt.CompoundTag);`**

1. `textures/gui/icons.png`

### `espi.mynpcs.client.gui.player.companion.GuiNpcCompanionTalents`

**`public void init();`**

1. `quest.exp` → **Exp**
2. `:`

### `espi.mynpcs.client.gui.questtypes.GuiNpcQuestTypeDialog`

**`public void init();`**

1. `dialog.selectoption` → **Select Option**
2. `X`
3. `gui.back` → **Back**

**`public void setGuiData(net.minecraft.nbt.CompoundTag);`**

1. `1`
2. `1`
3. `2`
4. `2`
5. `3`
6. `3`

### `espi.mynpcs.client.gui.questtypes.GuiNpcQuestTypeItem`

**`public void init();`**

1. `quest.takeitems` → **Take Items**
2. `gui.yes`
3. `gui.no`
4. `gui.ignoreDamage` → **Ignore damage**
5. `gui.ignoreNBT` → **Ignore NBT**
6. `gui.back` → **Back**

### `espi.mynpcs.client.gui.questtypes.GuiNpcQuestTypeKill`

**`public void init();`**

1. `questtype.fillnpcplayer` → **You can fill in npc or player names too**
2. `1`
3. `gui.back` → **Back**

### `espi.mynpcs.client.gui.questtypes.GuiNpcQuestTypeLocation`

**`public void init();`**

1. `questtype.locationblockname` → **Fill in the name of your Location Quest Block**
2. `gui.back` → **Back**

### `espi.mynpcs.client.gui.questtypes.GuiNpcQuestTypeManual`

**`public void init();`**

1. `questtype.fillnpcplayer` → **You can fill in npc or player names too**
2. `1`
3. `gui.back` → **Back**

### `espi.mynpcs.client.gui.roles.GuiJobFarmer`

**`public void init();`**

1. `farmer.itempicked` → **Item Picked up**
2. `farmer.donothing` → **Do Nothing**
3. `farmer.chest` → **Put in chest**
4. `farmer.drop` → **Drop on the ground**

### `espi.mynpcs.client.gui.roles.GuiMoveTutorSpeciesMoves`

**`public void init();`**

1. `gui.movetutor.species_moves_title` → **%s's moves**
2. `...`
3. `gui.movetutor.mode_items` → **Items**
4. `gui.movetutor.mode_money` → **Money**
5. `...`
6. `X`
7. `gui.done` → **Done**

### `espi.mynpcs.client.gui.roles.GuiMoveTutorTiers`

**`public void init();`**

1. `gui.movetutor.tiers_title` → **TIER PRICES**
2. `gui.movetutor.tier.normal_header` → **Normal tiers (TM/Tutor)**
3. `gui.movetutor.tier.special_header` → **Special tiers (Egg/Star)**
4. `gui.done` → **Done**

### `espi.mynpcs.client.gui.roles.GuiNpcBankSetup`

**`public void init();`**

1. `bank.withdrawFee` → **Withdraw Fee (%)**
2. `ftbquests.requireQuest` → **Require Quest**
3. `gui.no`
4. `gui.yes`
5. `ftbquests.questId` → **Quest ID**

### `espi.mynpcs.client.gui.roles.GuiNpcBard`

**`public void init();`**

1. `X`
2. `gui.selectSound` → **Select Sound**
3. `bard.jukebox` → **Play as jukebox**
4. `bard.background` → **Play as background**
5. `bard.loops` → **Loops**
6. `gui.no`
7. `gui.yes`
8. `bard.ondistance` → **On Distance**
9. `bard.hasoff` → **Has Off Distance**
10. `gui.no`
11. `gui.yes`
12. `bard.offdistance` → **Off Distance**

### `espi.mynpcs.client.gui.roles.GuiNpcCompanion`

**`public void init();`**

1. `companion.stage` → **Stage**
2. `gui.update` → **Update**
3. `gui.no`
4. `gui.yes`
5. `companion.age` → **Age**

### `espi.mynpcs.client.gui.roles.GuiNpcConversation`

**`public void init();`**

1. `gui.name` → **Name**
2. `conversation.namehint` → **Must exactly match (case-insensitive) the name of a real NPC entity standing within 10 blocks. This job makes several named NPCs talk to each other in turn - it will not make this NPC speak its own lines on its own.**
3. `gui.name` → **Name**
4. `conversation.namehint` → **Must exactly match (case-insensitive) the name of a real NPC entity standing within 10 blocks. This job makes several named NPCs talk to each other in turn - it will not make this NPC speak its own lines on its own.**
5. `conversation.delay` → **Delay**
6. `conversation.delay` → **Delay**
7. `conversation.line` → **Line**
8. `conversation.delay` → **Delay**
9. `gui.range` → **Range**
10. `quest.quest` → **Quest**
11. `gui.select` → **Select**
12. `X`
13. `availability.name` → **Availability**
14. `selectServer.edit`
15. `gui.always` → **Always**
16. `gui.playernearby` → **Player nearby**

### `espi.mynpcs.client.gui.roles.GuiNpcFollowerJob`

**`public void init();`**

1. `gui.name` → **Name**

### `espi.mynpcs.client.gui.roles.GuiNpcFollowerSetup`

**`public void init();`**

1. `1`
2. `follower.hireText` → **Thank you for hiring me for**
3. `{days}`
4. `follower.days` → **Days**
5. `follower.farewellText` → **My days with you are over. Farewell %s**
6. `{player}`
7. `follower.infiniteDays` → **Infinite Days**
8. `follower.guiDisabled` → **Gui Disabled**
9. `follower.allowSoulstone` → **Allow NPC Jar**
10. `gui.reset` → **Reset**

### `espi.mynpcs.client.gui.roles.GuiNpcGuard`

**`public void init();`**

1. `guard.animals` → **Attack Animals**
2. `guard.mobs` → **Attack Monsters**
3. `guard.creepers` → **Attack Creepers**
4. `guard.availableTargets` → **Available Targets**
5. `guard.currentTargets` → **Current Targets**
6. `>`
7. `<`
8. `>>`
9. `<<`

### `espi.mynpcs.client.gui.roles.GuiNpcHealer`

**`public void init();`**

1. `beacon.range` → **Effect Range**
2. `stats.speed` → **Speed**
3. `beacon.affect` → **Affect**
4. `faction.friendly` → **Friendly**
5. `faction.unfriendly` → **Unfriendly**
6. `spawner.all` → **All**
7. `beacon.potency` → **Potency**
8. `beacon.availableEffects` → **Available Effects**
9. `beacon.currentEffects` → **Current Effects**
10. `>`
11. `<`
12. `>>`
13. `<<`

### `espi.mynpcs.client.gui.roles.GuiNpcItemGiver`

**`public void init();`**

1. `itemgiver.method_random` → **Random Item**
2. `itemgiver.method_all` → **All Items**
3. `itemgiver.method_notowned` → **Give Not Owned Items**
4. `itemgiver.method_whennoneowned` → **Give When Doesnt Own Any**
5. `itemgiver.method_chained` → **Chained**
6. `itemgiver.cooldown_timer` → **Timer**
7. `itemgiver.cooldown_once` → **Give Only Once**
8. `itemgiver.cooldown_daily` → **Daily**
9. `gui.cooldown` → **Cooldown:**
10. `itemgiver.itemstogive` → **Items to give**
11. `availability.options` → **Availability Options**
12. `selectServer.edit`

### `espi.mynpcs.client.gui.roles.GuiNpcMoveRelearner`

**`public void init();`**

1. `?a?lRECORDADOR DE MOVIMIENTOS POK?MON?r`
2. `Este NPC actuar? como Recordador de Movimientos Pok?mon.`
3. `Al interactuar con ?l, los jugadores podr?n recordar`
4. `los movimientos olvidados de sus Pok?mon.`
5. `gui.done` → **Done**

### `espi.mynpcs.client.gui.roles.GuiNpcMoveTutor`

**`public void init();`**

1. `gui.movetutor.title` → **MOVE TUTOR**
2. `gui.movetutor.requires_tmcraft` → **This role requires the TM Craft mod installed on the server.**
3. `gui.done` → **Done**
4. `gui.movetutor.mode_manual` → **Manual**
5. `gui.movetutor.mode_random` → **Random**
6. `gui.movetutor.mode_species` → **Species**
7. `gui.done` → **Done**

### `espi.mynpcs.client.gui.roles.GuiNpcMoveTutorPlayer`

**`public void init();`**

1. `gui.movetutor.player_title` → **Move Tutor of %s**
2. `gui.movetutor.learn` → **Learn**
3. `gui.movetutor.no_offers` → **This tutor doesn't have any moves configured yet.**
4. `gui.back` → **Back**
5. `gui.cancel`

### `espi.mynpcs.client.gui.roles.GuiNpcPokemonEdit`

**`public void init();`**

1. `pokemonedit.species` → **Species:**
2. `Species`
3. `...`
4. `pokemonedit.level` → **Level:**
5. `Level`
6. `Level`
7. `Shiny`
8. `pokemonedit.shiny` → **Shiny:**
9. `gui.no`
10. `gui.yes`
11. `pokemonedit.form` → **Form:**
12. `Form`
13. `pokemonedit.nature` → **Nature:**
14. `Nature`
15. `...`
16. `pokemonedit.ability` → **Ability:**
17. `Ability`
18. `...`
19. `pokemonedit.gender` → **Gender:**
20. `Species`
21. `MALE`
22. `FEMALE`
23. `GENDERLESS`
24. `Gender`
25. `Gender`
26. `MALE`
27. `pokemonedit.gender_male` → **Male**
28. `FEMALE`
29. `pokemonedit.gender_female` → **Female**
30. `GENDERLESS`
31. `pokemonedit.gender_genderless` → **Genderless**
32. `pokemonedit.helditem` → **Held Item:**
33. `HeldItem`
34. `...`
35. `X`
36. `IVs`
37. `pokemonedit.ivs` → **IVs (HP/Atk/Def/SpA/SpD/Spe):**
38. `gui.min_abbrev` → **Min**
39. `gui.max_abbrev` → **Max**
40. `gui.random` → **Random**
41. `EVs`
42. `pokemonedit.evs` → **EVs (HP/Atk/Def/SpA/SpD/Spe):**
43. `gui.clear` → **Clear**
44. `gui.random` → **Random**
45. `pokemonedit.moves` → **Moves (up to 4):**
46. `pokemonedit.randommoves` → **Random Moves**
47. `Moves`
48. `MovesPPUp`
49. `...`
50. `0`
51. `1`
52. `2`
53. `3`
54. `gui.done` → **Done**
55. `gui.cancel`

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `Hardy`
2. `Lonely`
3. `Brave`
4. `Adamant`
5. `Naughty`
6. `Bold`
7. `Docile`
8. `Relaxed`
9. `Impish`
10. `Lax`
11. `Timid`
12. `Hasty`
13. `Serious`
14. `Jolly`
15. `Naive`
16. `Modest`
17. `Mild`
18. `Quiet`
19. `Bashful`
20. `Rash`
21. `Calm`
22. `Gentle`
23. `Sassy`
24. `Careful`
25. `Quirky`
26. `cobblemon:held/is_held_item`
27. `HeldItem`
28. `0`
29. `31`
30. `0`

**`public void subGuiClosed(net.minecraft.client.gui.screens.Screen);`**

1. `(`
2. `)`
3. `(`
4. `Nature`
5. `Ability`
6. `(`
7. `)`
8. `(`
9. `HeldItem`

### `espi.mynpcs.client.gui.roles.GuiNpcPokemonTrader`

**`public void init();`**

1. `pokemontrader.requirements` → **TRADE REQUIREMENTS**
2. `pokemontrader.requiredspecies` → **Required Pokémon Species:**
3. `...`
4. `pokemontrader.requiredform` → **Required Pokémon Form:**
5. `pokemontrader.extraitems` → **Additional Physical Items (Optional):**
6. `pokemontrader.itemslot` → **Item**
7. `...`
8. `X`
9. `gui.quantity` → **Qty:**
10. `pokemontrader.limits` → **TRADE LIMITS**
11. `pokemontrader.limit_disabled` → **Limit: Disabled**
12. `pokemontrader.limit_enabled` → **Limit: Enabled**
13. `pokemontrader.type_player` → **Type: Player**
14. `pokemontrader.type_global` → **Type: Global**
15. `pokemontrader.maxtrades` → **Max Trades:**
16. `gui.cooldown` → **Cooldown:**
17. `gui.mins` → **Mins**
18. `gui.hrs` → **Hrs**
19. `pokemontrader.togive` → **POKÉMON TO GIVE**
20. `Species`
21. `pokemontrader.editpokemon` → **Edit Pokémon: **
22. `gui.notdefined` → **Not Defined**
23. `LevelMode`
24. `pokemontrader.level_fixed` → **Level: Fixed**
25. `pokemontrader.level_mirror` → **Level: Mirror**

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `LevelMode`

### `espi.mynpcs.client.gui.roles.GuiNpcPokemonTraderPlayer`

**`public void init();`**

1. `?a?lLO QUE ENTREGAS (REQUISITOS):?r`
2. `Cualquiera`
3. `?a[COMPLETADO]`
4. `?c[NO TIENES]`
5. `? Items requeridos:`
6. `?a[OK]`
7. `?c[FALTA]`
8. `? Sin requisitos de ?tems f?sicos adicionales.`
9. `?b?lLO QUE RECIBES:?r`
10. `Species`
11. `No configurado`
12. `Shiny`
13. `Form`
14. `LevelMode`
15. `? Nivel: ?e`
16. `Level`
17. `Level`
18. `Nature`
19. `Ability`
20. `? Stats:`
21. `Moves`
22. `? Movs: ?a`
23. `?r, ?a`
24. `?Intercambiar!`
25. `gui.cancel`

### `espi.mynpcs.client.gui.roles.GuiNpcPokemonTrainer`

**`public void init();`**

1. `pokemontrainer.aggrorange` → **Aggro Range (Blocks):**
2. `gui.cooldown` → **Cooldown:**
3. `gui.secs` → **Secs**
4. `gui.mins` → **Mins**
5. `gui.hrs` → **Hrs**
6. `pokemontrainer.cooldown_combat` → **Fight**
7. `pokemontrainer.rightclick` → **Right Click:**
8. `gui.no`
9. `gui.yes`
10. `pokemontrainer.fightonsight` → **Fight On Sight:**
11. `gui.no`
12. `gui.yes`
13. `pokemontrainer.class` → **Class:**
14. `pokemontrainer.class_normal` → **Normal**
15. `pokemontrainer.class_gymleader` → **Gym Leader**
16. `pokemontrainer.class_league` → **Pokémon League**
17. `pokemontrainer.class_elite` → **Elite Four**
18. `pokemontrainer.class_champion` → **Champion**
19. `pokemontrainer.class_legendary` → **Legendary Trainer**
20. `pokemontrainer.class_rocket` → **Team Rocket**
21. `pokemontrainer.class_custom` → **Custom**
22. `pokemontrainer.prefix` → **Prefix:**
23. `pokemontrainer.rewards` → **Rewards...**
24. `pokemontrainer.party` → **Pokémon Team (6 Slots):**
25. `Species`
26. `Level`
27. `Level`
28. `pokemontrainer.slot` → **Slot**
29. `pokemontrainer.empty` → **Empty**
30. `pokemontrainer.level_abbrev` → **Lvl.**
31. `pokemontrainer.challengeline` → **Challenge Message:**
32. `pokemontrainer.winline` → **Victory Message:**
33. `pokemontrainer.loseline` → **Defeat Message:**

### `espi.mynpcs.client.gui.roles.GuiNpcPuppet`

**`public void init();`**

1. `gui.yes`
2. `gui.no`
3. `puppet.standing` → **While standing**
4. `gui.yes`
5. `gui.no`
6. `puppet.walking` → **While walking**
7. `gui.yes`
8. `gui.no`
9. `puppet.attacking` → **While attacking**
10. `gui.yes`
11. `gui.no`
12. `puppet.animation` → **Animation**
13. `1`
14. `2`
15. `3`
16. `4`
17. `5`
18. `6`
19. `7`
20. `8`
21. `stats.speed` → **Speed**
22. `model.head` → **Head**
23. `model.body` → **Body**
24. `model.larm` → **L. Arm**
25. `model.rarm` → **R. Arm**
26. `model.lleg` → **L. Leg**
27. `model.rleg` → **R. Leg**
28. `model.head` → **Head**
29. `model.body` → **Body**
30. `model.larm` → **L. Arm**
31. `model.rarm` → **R. Arm**
32. `model.lleg` → **L. Leg**
33. `model.rleg` → **R. Leg**
34. `X`
35. `gui.start` → **Start**
36. `gui.end` → **End**

### `espi.mynpcs.client.gui.roles.GuiNpcSpawner`

**`public void init();`**

1. `X`
2. `1:`
3. `X`
4. `2:`
5. `X`
6. `3:`
7. `X`
8. `4:`
9. `X`
10. `5:`
11. `X`
12. `6:`
13. `spawner.diesafter` → **Dies after spawns die**
14. `gui.yes`
15. `gui.no`
16. `spawner.despawn` → **Despawn Spawns On Target Lost**
17. `gui.no`
18. `gui.yes`
19. `spawner.posoffset` → **Position Offset**
20. `Y:`
21. `Z:`
22. `spawner.type` → **Spawn Type**
23. `spawner.one` → **One by One**
24. `spawner.all` → **All**
25. `spawner.random` → **Random**

### `espi.mynpcs.client.gui.roles.GuiNpcTraderSetup`

**`public void init();`**

1. `role.marketname` → **Linked Marketname**
2. `gui.ignoreDamage` → **Ignore damage**
3. `gui.ignoreNBT` → **Ignore NBT**
4. `trader.paymentMode` → **Payment Mode**
5. `gui.item` → **Item**
6. `gui.money` → **Money**

### `espi.mynpcs.client.gui.roles.GuiNpcTransporter`

**`public void init();`**

1. `gui.name` → **Name**
2. `transporter.discovered` → **Available when discovered**
3. `transporter.start` → **Available from the start**
4. `transporter.interaction` → **Available after interaction**

### `espi.mynpcs.client.gui.roles.GuiRoleDialogTree`

**`public void init();`**

1. `dialog.tree.loading` → **Loading...**
2. `dialog.tree.loading` → **Loading...**
3. `dialog.starttext` → **Start Text**
4. `selectServer.edit`
5. `<-`
6. `dialog.tree.depth` → **Dialog %s**
7. `dialog.options` → **Dialog Options**
8. `selectServer.edit`
9. `+`
10. `>`
11. `+`

### `espi.mynpcs.client.gui.roles.SubGuiNpcConversationLine`

**`public void init();`**

1. `Line`
2. `gui.selectSound` → **Select Sound**
3. `X`
4. `gui.done` → **Done**

### `espi.mynpcs.client.gui.roles.SubGuiNpcPokemonRewards`

**`public void init();`**

1. `pokemonrewards.normal` → **Normal Rewards**
2. `pokemonrewards.special` → **Special Rewards**
3. `pokemonrewards.title` → **Reward Settings**
4. `pokemonrewards.items` → **Reward Items:**
5. `+`
6. `-`
7. `gui.quantity` → **Qty:**
8. `pokemonrewards.exp` → **Experience (EXP):**
9. `pokemonrewards.factionid` → **Faction ID:**
10. `pokemonrewards.factionpoints` → **Points:**
11. `pokemonrewards.commands` → **Reward Commands (Sequential):**
12. `+`
13. `-`
14. `?`
15. `?`
16. `gui.done` → **Done**
17. `gui.cancel`

**`public void subGuiClosed(net.minecraft.client.gui.screens.Screen);`**

1. `\\.`

### `espi.mynpcs.client.gui.script.GuiScriptGlobal`

**`public void init();`**

1. `Players`
2. `Forge (BROKEN)`

### `espi.mynpcs.client.gui.script.GuiScriptInterface`

**`public void init();`**

1. `gui.settings` → **Settings**
2. `+`
3. `script.hideFunctions` → **Hide Functions**
4. `script.showFuncions` → **Show Functions**
5. `gui.clear` → **Clear**
6. `gui.paste` → **Paste**
7. `gui.copy` → **Copy**
8. `gui.remove` → **Remove**
9. `script.loadscript` → **Load Scripts**
10. `gui.copy` → **Copy**
11. `gui.clear` → **Clear**
12. `script.language` → **Language**
13. `gui.enabled` → **Enabled**
14. `gui.no`
15. `gui.yes`
16. `script.openfolder` → **Open scripts folder**
17. `gui.website` → **Website**
18. `script.apidoc` → **API Doc**

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `https://www.patreon.com/cw/Le_Espii`
2. `https://www.patreon.com/cw/Le_Espii`
3. `gui.deleteMessage` → **Are you sure you want to delete %s?**

**`public void setGuiData(net.minecraft.nbt.CompoundTag);`**

1. `Languages`
2. `Scripts`
3. `Language`
4. `Methods`

**`private void lambda$buttonEvent$2(boolean);`**

1. `https://www.patreon.com/cw/Le_Espii`

**`private void lambda$buttonEvent$1(boolean);`**

1. `https://www.patreon.com/cw/Le_Espii`

### `espi.mynpcs.client.gui.script.GuiScriptList`

**`public void init();`**

1. `script.availableScripts` → **Available Scripts**
2. `script.loadedScripts` → **Loaded Scripts**
3. `>`
4. `<`
5. `>>`
6. `<<`
7. `gui.done` → **Done**

### `espi.mynpcs.client.gui.select.GuiDialogSelection`

**`public void init();`**

1. `gui.categories` → **Categories**
2. `dialog.dialogs` → **Dialogs**
3. `X`

### `espi.mynpcs.client.gui.select.GuiListSelection`

**`public void init();`**

1. `gui.done` → **Done**
2. `gui.cancel`

### `espi.mynpcs.client.gui.select.GuiMoveSelection`

**`public void init();`**

1. `gui.search` → **Search**
2. `X`
3. `gui.select` → **Select**
4. `gui.cancel`

### `espi.mynpcs.client.gui.select.GuiMoveTutorPicker`

**`public void buttonEvent(espi.mynpcs.shared.client.gui.components.GuiButtonNop);`**

1. `gui.movetutor.category.all` → **All**
2. `gui.movetutor.category.tm` → **TM**
3. `gui.movetutor.category.tutor` → **Tutor**
4. `gui.movetutor.category.egg` → **Egg**
5. `gui.movetutor.category.star` → **Star**

### `espi.mynpcs.client.gui.select.GuiPokemonSelection`

**`public void init();`**

1. `gui.search` → **Search**
2. `X`
3. `gui.select` → **Select**
4. `gui.cancel`

### `espi.mynpcs.client.gui.select.GuiQuestSelection`

**`public void init();`**

1. `gui.categories` → **Categories**
2. `quest.quests` → **Quests**
3. `X`

### `espi.mynpcs.client.gui.select.GuiSoundSelection`

**`public void init();`**

1. `X`
2. `gui.play` → **Play**
3. `gui.copy` → **Copy**

### `espi.mynpcs.client.gui.select.GuiTextureSelection`

**`public void init();`**

1. `gui.done` → **Done**
2. `gui.cancel`

### `espi.mynpcs.client.gui.util.GuiInventoryItemPicker`

**`public void init();`**

1. `gui.itempicker.selected` → **Selected Items:**
2. `gui.itempicker.inventory` → **Your Inventory:**
3. `gui.done` → **Done**
4. `gui.cancel`

## 26. Raw role/job implementation inventory

### Role implementation classes

- `RoleBank`
- `RoleCompanion`
- `RoleDialog`
- `RoleFollower`
- `RoleInterface`
- `RoleMoveRelearner`
- `RoleMoveTutor`
- `RolePokemonTrader`
- `RolePokemonTrainer`
- `RolePostman`
- `RoleTrader`
- `RoleTransporter`

### Job implementation classes

- `JobBard`
- `JobBuilder`
- `JobChunkLoader`
- `JobConversation`
- `JobFarmer`
- `JobFollower`
- `JobGuard`
- `JobHealer`
- `JobInterface`
- `JobItemGiver`
- `JobPuppet`
- `JobSpawner`

## 27. Final capability map

| Area | Capability status in 1.5.0 |
|---|---|
| NPC appearance/model/skin | Implemented, rich GUI |
| Stats/combat/projectiles/resistances | Implemented, rich GUI |
| AI/navigation/moving paths | Implemented, rich GUI |
| Inventory/equipment/drops/EXP | Implemented |
| Roles | Implemented; Companion WIP; Move Relearner hidden from normal selector |
| Jobs | Implemented selector with 11 concrete jobs |
| Quest authoring + player quest log | Implemented |
| Dialog tree authoring + wheel interaction | Implemented |
| Factions | Implemented |
| Banks/economy | Implemented; optional Cobbledollars integration |
| Transport network | Implemented |
| Mail | Implemented |
| Trader | Implemented; item and money modes |
| Pokémon Trader / Trainer / Editor | Implemented conditionally |
| Move Tutor / TM Craft | Implemented conditionally |
| Move Relearner | Code/UI present, not normally selectable |
| Healer Pokémon party integration | Implemented conditionally with nearby healer-block requirement |
| FTB Quests hooks | Implemented conditionally |
| Scripts | Implemented; Forge scripting page labeled BROKEN |
| Custom GUI scripting API | Implemented |
| Cloner / spawner / mounter / path / teleport / NBT tools | Implemented |
| Schematics / Builder / Copy | Implemented |
| Recipes manager | Present but explicitly labeled Broken |
| Natural Spawns manager | Present but explicitly labeled WIP |
| Nether Furnace | Implemented and recipe bundled |
| Resource/data assets | Bundled |
| Public scripting/entity/gui/event API | Large implemented surface |

## 28. Source notes

- **Primary:** uploaded `mynpcs-neoforge-1.5.0.jar`.
- **Primary evidence paths:** `META-INF/neoforge.mods.toml`, `assets/mynpcs/lang/en_us.json`, `data/mynpcs/**`, `espi.mynpcs.client.gui.*`, `espi.mynpcs.roles.*`, `espi.mynpcs.controllers.*`, `espi.mynpcs.entity.data.*`, `espi.mynpcs.api.*`, packet/registry classes.
- **External cross-check:** current CurseForge “My NPCs” project and `mynpcs-neoforge-1.5.0.jar` file/changelog pages, checked 2026-09-22. External text was not used to manufacture missing GUI behavior.

---

### Practical reading order
If you are using this as a design/implementation reference, read Sections **2 → 3 → 9 → 10 → 11 → 14 → 16 → 24**, then use Section 25 as the exhaustive GUI-label appendix.