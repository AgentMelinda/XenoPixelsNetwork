# My NPCs 1.5.0 — Full Feature & Capability Catalog

**Artifact:** `mynpcs-neoforge-1.5.0.jar`  
**Minecraft:** 1.21.1  
**Loader:** NeoForge `21.1.0+`  
**Game version range:** `[1.21.1, 1.22)`  
**Mod ID:** `mynpcs`  
**Version:** `1.5.0`  
**Package root:** `espi.mynpcs`  
**JAR metadata license field:** `MIT`  
**Public CurseForge page license (external cross-check only):** All Rights Reserved — this catalog does not resolve that discrepancy.

This catalog is evidence-driven. Features are listed only when they exist in the uploaded JAR (registries, classes, GUI construction order, language keys, packets, datapack assets, or bytecode fields). A class name alone is not treated as proof that a feature is reachable from the normal UI.

## How to read status labels

| Label | Meaning |
|---|---|
| **Implemented** | Present in this build and exposed through a normal editor, player GUI, command, block, item, or runtime path that was traced. |
| **Conditional** | Implementation exists, but it only activates when another mod/helper is detected or a runtime/config gate is true. |
| **WIP** | The mod itself labels the control `(WIP)` in this build. |
| **Broken** | The mod itself labels the control `(Broken)` or `(BROKEN)` in this build. |
| **Dormant** | Implementation and often a dedicated GUI exist, but the normal selector path skips them in 1.5.0. |
| **Code-only / not a normal editor page** | Classes prove the engine can do it; no normal main-editor page was recovered for it. |

Nothing below is inferred from CustomNPCs folklore, wikis, or older versions unless the same thing is also present in this JAR.

---

## 1. What this mod is

My NPCs is a **networked client/server NPC authoring suite**, not a client-only cosmetic pack.

Confirmed package facts from the JAR:

- **2,483** JAR entries
- **332** GUI/menu-related classes by name inventory
- **123** GUI screens with recoverable ordered labels
- **964** English localization keys
- **18** bundled language files: `cs_cz`, `de_de`, `en_us`, `es_ar`, `es_cl`, `es_es`, `es_mx`, `fr_fr`, `hu_hu`, `id_id`, `ko_kr`, `nl_nl`, `pl_pl`, `pt_br`, `ru_ru`, `sv_se`, `zh_cn`, `zh_tw`
- **47** GUI textures under `assets/mynpcs/textures/gui`
- **119** server packet classes and **41** client packet classes
- **197** top-level public API classes/interfaces (excluding inner classes)

It lets a world author create custom NPCs, give them appearance/stats/AI/inventory, assign roles and jobs, author quests and dialogs, manage factions, banks, mail, transport, clones, schematics, and scripts, and optionally hook Cobblemon / Pixelmon / TM Craft / Cobbledollars / FTB Quests / Armourer’s Workshop when those mods are present.

---

## 2. Main NPC editor

Visible top-level tabs from `GuiNpcMenu` construction order (this is the real UI order, not `EnumMenuType` declaration order):

1. Display
2. Stats
3. AI
4. Inventory
5. Advanced
6. Global
7. X / Close
8. Delete (confirmation flow)

Internal `EnumMenuType` also contains `MODEL`, `TRANSFORM`, `MOVING_PATH`, and `MARK`. Those exist as systems/screens, but they are **not** the top-level tab order of the main editor.

### 2.1 Display — identity, model, visibility

**Status:** Implemented, rich GUI (`GuiNpcDisplay`, `DataDisplay`)

Configurable surface:

- **Name**, with auxiliary name-generation controls. Backing data includes a Markov name generator and bundled name corpora.
- **Title**
- **Model → Edit** opens the model creation system
- **Living animation:** Yes / No
- **Size:** UI range includes 1–30
- **Tint / skin color**
- **Glowing**, plus separate overlay/layer glow data
- **Texture source:** Texture / Player / URL, with Select where appropriate
- **Cape → Select**
- **Overlay → Select**
- **Showing Layers:** Yes / No
- **Hitbox:** Normal / None / Solid
- **Visible:** Yes / No / Partially, with optional **Availability** conditions
- **Boss Bar:** Hide / Show / Show when attacking
- **Boss-bar color:** Pink, Blue, Red, Green, Yellow, Purple, White

Backing fields also include model size, per-part scale, skin/player profile/URL data, cloak, `showName`, `disableLivingAnimation`, and visibility state.

#### Model editor

Navigation from `GuiCreationScreenInterface`: **Entity → Parts → Extra → Scale → Save → Load → X**

- **Entity:** Reset To NPC; `entity.mynpcs.customnpc` / Custom MyNPC; Basic Limited Renderer; can substitute supported entity rendering/model data
- **Parts:** custom part editor (`GuiCreationNewParts`). Texture-based parts can use Player Skin, Texture, selector, or URL. Eye configuration includes pupil, size, mirror, direction, position, glint, lashes, blink, and lid controls
- **Extra:** entity-specific extra-data adapter. Recovered keys include Age/Child, Color, Model, Cobblemon model data, Pixelmon data, dog/breed-style data, and legacy MorePlayerModels-style skin/hair/face/uniform/gemstone/visor/gloves/cape fields
- **Scale:** Width, Height, Depth, Shared Yes/No
- **Save / Load:** model preset persistence; load screen supports removal

### 2.2 Stats — combat body

**Status:** Implemented (`GuiNpcStats`)

Visible page order:

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
13. Cobweb affected / ignore-cobweb control (backing field is `ignoreCobweb`; read the label carefully when configuring)
14. Health Regen
15. Combat Regen

**Respawn submenu**

- Mode: Yes / Day / Night / No / Naturally
- Respawn time
- Hide dead body
- Done

**Melee properties submenu**

- Melee Strength, Melee Range, Melee Speed, Knockback
- Melee Effect, duration/time, amplifier 0–10
- Effect selection includes None and Fire paths

**Ranged properties submenu**

- Accuracy %, Shot Count, Range, minimum/melee-range distance, Min Delay, Max Delay
- Burst Count and Burst Rate
- Fire Sound, Hit Sound, Ground Sound selectors
- Aim While Shooting: No / When Distant / When Hidden
- Shoot Indirect toggle

> **Xeno parity:** all present. Shot Count and Burst Count are kept as the two separate counts
> `DataRanged` has — a shot is a spread released together, a burst repeats that spread `fireRate`
> ticks later — and `NpcKiAttackDispatcher` fires the product of the two.

**Projectile properties submenu**

- Strength, Knockback, Size, Speed
- Gravity: No / Yes / Constant / Accelerate
- Explosive: None / Small / Medium / Large
- Projectile effect state (regular/amplified paths), trail type, 2D/3D, spins, sticks, glow/no-glow

> **Xeno parity:** all present except 2D/3D, which has no counterpart and is shown as a note rather
> than a control. Their switch picks between a flat sprite and an item model; a DragonMineZ ki
> projectile draws its own layered sphere and has neither, so the toggle would select nothing.
> The effect now carries its own duration and amplifier (`pDur` / `pEffAmp`) instead of borrowing
> the melee pair. Trail offers vanilla particles rather than their numbered trail list, which
> indexes textures that ship with their mod.

**Resistance submenu**

- Knockback Resistance, Arrow Resistance, Melee Resistance, Explosion Resistance

### 2.3 AI — behavior and movement

**Status:** Implemented (`GuiNpcAI`, `DataAI`)

Visible page order:

1. On Found Enemy: Retaliate / Panic / Retreat / Nothing
2. Shelter From: Darkness / Sunlight / Disabled
3. Must See Target: No / Yes
4. Door Interact: Disabled / Break / Open
5. Can Swim
6. Return To Start
7. Avoids Water
8. Leap At Target
9. Movement → Edit
10. Attack Invisible
11. Mount Control

Backing `DataAI` also tracks fire reaction, sunlight/water avoidance, sprint/leap behavior, direct line-of-sight, interaction stopping, movement/standing animation types, body orientation offsets, walking and active range, move speed, path/position state, pause behavior, and mount control.

**Movement submenu**

- Movement type: Standing / Wandering / Moving Path
- Navigation: Ground / Flying / Swimming
- Range and Active Range
- Wander Interact Yes/No; pause behavior
- Position offsets X/Y/Z
- Animation presets: Normal / Sitting / Lying / Hug / Sneaking / Dancing / Aiming / Crawling
- Animation mode: Body / Manual / Stalking / Head
- Rotation 0–359
- Moving-path controls: Looping / Backtracking, path/movement name, pauses, Stop Interact, Move Speed

### 2.4 Inventory — gear, drops, EXP

**Status:** Implemented (`GuiNPCInv`, `DataInventory`)

- Min Exp and Max Exp (GUI text fields cap at 32767)
- Loot mode: Normal / Auto Pickup
- NPC equipment/inventory slots and player inventory container
- Per-drop Drop Chance
- Backing data stores weapon/armor slots, projectile/offhand state, drop items/chances, min/max EXP, and randomized EXP/drop behavior

### 2.5 Advanced — roles, jobs, lines, scenes

**Status:** Implemented, with one dormant role and one WIP-labelled role (`GuiNpcAdvanced`)

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

The selector uses a non-contiguous ID map: `[0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11]`.  
**Role ID 9 (`RoleMoveRelearner`) exists and has a GUI, but this page jumps from 8 to 10.** Treat Move Relearner as **Dormant / not normally selectable** in 1.5.0.

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

#### Advanced utility buttons

Visible two-column layout: **Lines / Factions → Dialogs / Sounds → Night / Linked → Scenes / Marks → FTB Quests**.  
FTB Quests is added only when the FTB Quests helper is enabled.

**Lines:** World, Attack, Interact, Killed, Kill, NPC Interact; Random / ordered playback.

**Factions:** Attack hostile factions Yes/No; Defend faction members Yes/No; death/faction-point behavior fields.

**Dialogs:** per-NPC dialog option select/remove.

**Sounds:** Living, Angry, Hurt, Death, Step; each has Select Sound; Has Pitch Yes/No.

**Night:** day/night profiles for Display, Stats, AI, Inventory, Advanced role/job data; Editing Mode; Load Day / Load Night.

**Linked:** associate or clear reusable linked-NPC data.

**Scenes:** Disabled/Enabled entries, Edit, remove (`X`), None/Add.

**Marks:** add/remove marks and edit Availability.

**FTB Quests (conditional):** Required Quest / Quest ID; Complete Quest / Quest ID; backing fields also track required-role and required-job gating.

### 2.6 Global managers

Visible order from `GuiNPCGlobalMainMenu`:

1. Banks
2. Factions
3. Dialogs
4. Quests
5. Transport
6. PlayerData
7. Recipes **(Broken)**
8. Natural Spawns **(WIP)**
9. Linked

The `(Broken)` and `(WIP)` suffixes are hard-coded by this build.

---

## 3. Quest system

**Status:** Implemented authoring + player quest log

### Player Quest Log (`GuiQuestLog`)

- Reads active quests from `PlayerQuestController`
- Groups by `QuestCategory.title`, naturally sorts category names, left-side category buttons
- Scroll list of quest titles in the selected category
- Shows `quest.noquests` when empty
- Renders quest log text through `TextBlockClient` with page forward/back
- Objectives section with progress
- If a completion NPC name exists, shows **Complete with …**
- Player tab group: Vanilla Inventory → Factions → Quests

### Quest editor fields

- Title and numeric ID
- Completed text editor
- Quest Log text editor
- Reward submenu
- Type: Item / Dialog / Kill / Location / Area Kill / Manual
- Repeat: None/nonrepeatable, Repeatable, MC Daily, MC Weekly, RL Daily, RL Weekly (`EnumQuestRepeat`)
- Completion: NPC or Instant (`EnumQuestCompletion`)
- Faction options
- Command on completion
- Mail setup
- Next Quest chaining (`nextQuestid`)

### Quest type capabilities

| Type | What it can require |
|---|---|
| Item | Required item; Take Items Yes/No; Ignore Damage; Ignore NBT |
| Dialog | Dialog-option selection; select/remove linked options |
| Kill | Target NPC/player/entity plus count |
| Location | Named Location Block |
| Area Kill | Distinct `QuestAreaKill` implementation; editor exposes Area Kill even though a dedicated standalone screen class was not recovered by the generic label extractor |
| Manual | Manually updated target/count model |

### Rewards

- Reward items inventory
- Reward EXP
- Random Item Yes/No
- Faction effects via quest faction options
- Completion command
- Configured mail send
- Chain to next quest

### Availability engine (shared, not quest-only)

Reusable `Availability` object can gate content with:

- Up to four dialog checks: Always / After / Before
- Up to four quest checks: Always / After / Before / Active / NotActive / Completed / CanStart
- Daytime: Always / Night / Day
- Two faction conditions: Friendly / Neutral / Hostile stance and Always / Is / IsNot
- Two scoreboard conditions: Smaller / Equal / Bigger against objective/value
- Minimum player level

Reused by visibility, marks, item giver, conversation, builder/border-like utility blocks, and other gated surfaces.

---

## 4. Dialog system

**Status:** Implemented

Global manager: Categories / Dialogs with Add/New, Edit, Remove, delete confirmation.

Dialog editor fields:

- Title, ID
- Dialog Text editor
- Availability
- Faction options
- Dialog options
- Linked quest select/remove
- Select Sound
- Mail Setup
- Command
- Hide NPC
- Show Dialog Wheel
- Disable Esc

Dialog option fields:

- Title, Color
- Type: Close / Dialog / Disabled / Role / Command Block
- Can select another Dialog, create New Dialog, or run a Command
- Command editor supports nearest/random/all player selectors and the dialog-player placeholder (`@dp`)

`RoleDialog` tree editor (`GuiRoleDialogTree`): Start Text, nested Options, Edit, Add (`+`), descend (`>`).

Player dialog GUI: wheel textures; keyboard Up / Down / Enter / Keypad Enter / Escape.

---

## 5. Factions

**Status:** Implemented

Global manager:

- Add, Remove, New
- Name, ID, Color, Points
- Hidden Yes/No
- Attacked by mobs Yes/No
- Hostile Factions list

Player faction screen:

- Shows standings
- Categorizes Friendly / Neutral / Unfriendly from stored points/threshold logic

NPC Advanced can attack hostile factions and defend faction members. Quests and rewards can alter or check faction state. `/mynpcs faction` can add/set/reset/drop points.

---

## 6. Banks and economy

**Status:** Implemented; money path **Conditional** on Cobbledollars / currency helper

### Bank role setup

- Withdraw Fee (%)
- Require Quest Yes/No and Quest ID
- Connects the NPC to a global bank definition (`bankId`)

### Player bank chest (`GuiNPCBankChest`)

- Tab selector
- Unlock / Upgrade
- Amount
- Deposit / Withdraw
- Displays unlock costs, upgrade costs, Wallet, Vault, Fee
- Server data: MaxSlots, UnlockedSlots, BankMoney, WithdrawFee, Currency

`SlotNpcBankCurrency` and `CobbleDollarsHelper` exist. When Cobbledollars is detected the helper can get/add/remove balances, with a fallback command-based change path.

### Global Banks manager

- Add / Remove / New
- Upgrade-state strings: Can Upgrade / Can't Upgrade / Upgraded
- Tab/slot/unlock/upgrade data used by the player vault

---

## 7. Transport / fast travel

**Status:** Implemented

- Global transport manager: Add / Edit / Remove / Open / Back around categories and locations
- Transporter role setup: Name; Available when discovered; Available from the start; Available after interaction
- Player screen lists destinations and exposes **Travel**
- Discovery stored in `PlayerTransportData`

---

## 8. Mail / Mailman

**Status:** Implemented

Mailbox view:

- Mailbox name, sender, time sent (`%s ago`)
- Read
- Delete with confirmation

Mail composition:

- Sender, Username/recipient, Subject
- Body pages
- Done, Send, Delete, Cancel, page navigation
- Validation strings for bad username and missing subject
- Success feedback strings

Persistence: `PlayerMail`, `PlayerMailData`.  
Quest and dialog editors can attach mail. Mailman/Postman role opens the delivery surface. Registered mailbox blocks: `npcmailbox`, `npcmailbox2`, `npcmailbox3`.

---

## 9. Roles — capabilities

Implementation classes present: `RoleBank`, `RoleCompanion`, `RoleDialog`, `RoleFollower`, `RoleInterface`, `RoleMoveRelearner`, `RoleMoveTutor`, `RolePokemonTrader`, `RolePokemonTrainer`, `RolePostman`, `RoleTrader`, `RoleTransporter`.

### 9.1 Trader — Implemented

- Player trading container
- Every slot: two item currencies (`getCurrency1`, `getCurrency2`) and one sold item (`getSold`)
- Linked Market / Market Name
- Ignore Damage, Ignore NBT
- Payment Mode: Item / Money
- Money path uses per-slot `long[] slotPrices`
- Player GUI shows Balance and sufficient/insufficient states

### 9.2 Follower role — Implemented

Hireable owner-bound NPC.

Stores: owner UUID, following/waiting state, per-rate payment, hire/farewell dialogs, days hired/time, inventory, GUI/soulstone flags.

Setup:

- Rate row
- Hire text with `{days}`
- Days
- Farewell text with `{player}`
- Infinite Days
- GUI Disabled
- Allow Soulstone / Allow NPC Jar
- Reset

Player UI: Waiting / Following / Hire; panel renders health and days remaining / last-day state.

### 9.3 Bank — Implemented

See section 6.

### 9.4 Transporter — Implemented

See section 7.

### 9.5 Mailman / Postman — Implemented

See section 8.

### 9.6 Companion — WIP in selector

Owner-bound companion implementation exists with inventory, talents, aging/stage, food stats, following/defending, and companion-job logic. The Advanced selector still labels it **(WIP)**.

Setup GUI: Stage, Update Yes/No, Age.

Player stats: Name, Owner, Age, Strength, Level, Job.

Player navigation: Stats / Talent / Inventory / Job.

Companion jobs enum: None / Soldier / Guard / Farmer / Miner / Shop / Robot.  
Companion talents enum: Inventory / Armor / Sword / Ranged / Acrobats / Intel.  
Talent screen spends/uses quest EXP through `+` controls.

### 9.7 Dialog role — Implemented

Branching role-driven dialog tree with start text and nested options, tied to dialog/quest IDs where configured.

### 9.8 Pokémon Trader — Conditional

Requires the Cobblemon (and related helper) ecosystem at runtime.

- Required species and optional form
- Pokémon to give stored as NBT
- Extra required item inventory and quantity
- Limits Disabled/Enabled; Type Player/Global; Max Trades
- Cooldown minutes/hours
- Persisted player trade counts/timestamps
- Level mode: Fixed / Mirror
- Pokémon Editor for the given Pokémon
- Player screen computes requirement status, item OK/missing, received Pokémon details, and Trade

### 9.9 Move Tutor — Conditional, visible in 1.5.0

GUI explicitly warns: this role requires **TM Craft** installed on the server.

- Modes: Manual / Random / Species
- `SLOTS = 6`
- Random rotation interval: `43,200,000 ms` = 12 hours; rotation Fixed / 12H
- Species mode: up to **10 species**, **4 manual moves** per species offer
- Tier constants: Low / Mid / High / Status; separate normal/special tier arrays
- Species matching can derive offers from the player’s party
- Player screen: Learn, No Offers, Back, Cancel
- Move picker categories: All / TM / Tutor / Egg / Star
- Additional setup screens for species moves and tier prices, including Items / Money payment modes

### 9.10 Pokémon Trainer — Conditional

- Aggro Range
- Cooldown Secs / Mins / Hrs; per-player cooldown map
- Right Click fight Yes/No
- Fight On Sight Yes/No
- Class: Normal / Gym Leader / Pokémon League / Elite Four / Champion / Legendary Trainer / Team Rocket / Custom
- Prefix (automatic/custom)
- Party: 6 slots with species/level; empty-slot labelling
- Challenge / Victory / Defeat messages
- Normal and Special reward sets: multiple items, EXP, faction ID/points, sequential commands
- Battle lifecycle tracks active target, win/loss, cooldown, reward delivery, reset

### 9.11 Move Relearner — Dormant in this build

- `RoleMoveRelearner` exists
- `DataAdvanced` can instantiate role type 9
- Dedicated GUI exists and describes a Pokémon move-relearning interaction (recovered strings are Spanish in that screen: recordador de movimientos)
- Code references Pixelmon move-relearner packet class names
- **`GuiNpcAdvanced` skips role 9 in the normal selector.** Do not treat this as a normally selectable 1.5.0 role.

---

## 10. Jobs — capabilities

Implementation classes present: `JobBard`, `JobBuilder`, `JobChunkLoader`, `JobConversation`, `JobFarmer`, `JobFollower`, `JobGuard`, `JobHealer`, `JobInterface`, `JobItemGiver`, `JobPuppet`, `JobSpawner`.

### Bard — Implemented

- Select Sound
- Play as jukebox / Play as background
- Loops Yes/No
- On Distance
- Has Off Distance Yes/No
- Off Distance
- Data includes min/max range, streaming/background, looping state

### Healer — Implemented; Pokémon party heal is Conditional

- Effect Range, Speed
- Affect: Friendly / Unfriendly / All
- Potency
- Available Effects / Current Effects with move-one and move-all controls
- Applies configured potion effects to nearby living targets by faction/target mode
- **Bytecode:** scans offsets -6 to +6 around the NPC for `cobblemon:healing_machine` or `pixelmon:healer`. Only with such a block nearby does it call party `heal()` for supported Pokémon integrations

### Guard — Implemented

- Attack Animals / Attack Monsters / Attack Creepers
- Available Targets / Current Targets
- `>`, `<`, `>>`, `<<` move controls

### Item Giver — Implemented

Give methods:

- Random Item
- All Items
- Give Not Owned Items
- Give When Doesnt Own Any
- Chained

Cooldown modes: Timer / Give Only Once / Daily, plus cooldown value.  
Items to give inventory. Availability editor.

### Follower job — Implemented

Follows another named NPC. Data stores target/following NPC and range/name. Distinct from the Follower **role**.

### Spawner — Implemented

- Six clone/spawn slots
- Dies after spawns die Yes/No
- Despawn Spawns On Target Lost Yes/No
- Position Offset X/Y/Z
- Spawn Type: One by One / All / Random
- Cooldown and tracked spawned entities in backing data

### Conversation — Implemented

- Two participant name fields (must exactly match, case-insensitive, a real NPC within 10 blocks)
- This job makes several named NPCs talk in turn; it does not make this NPC speak its own lines alone
- Delay, line timing, range
- Optional quest link
- Availability
- Mode: Always / Player nearby
- Runtime tracks participating NPCs, line sequence, delays, current/next line, range
- Per-line editor: Line text, Select Sound

### Chunk Loader — Implemented, thin GUI

Dedicated job tracks chunks and last-seen player/tick state. Exposed in the job dropdown. No rich dedicated configuration screen like Bard/Guard was recovered.

### Puppet — Implemented

Static/animated posing:

- While standing / walking / attacking Yes/No
- Animation steps 1–8, Speed
- Parts: Head, Body, L. Arm, R. Arm, L. Leg, R. Leg
- Each part has Start / End pose data

### Builder — Implemented

Drives schematic/build state through the Builder block:

- Preview
- Width, Length, Height
- Enabled / Finished / Started state
- Y Offset
- Rotation 0 / 90 / 180 / 270
- Availability
- Instant Build

### Farmer — Implemented

Automated farming tracks farmland/block state and pickup behavior.

Item Picked up:

- Do Nothing
- Put in chest
- Drop on the ground

---

## 11. Global data managers

| Manager | Status | Capabilities |
|---|---|---|
| Banks | Implemented | Add/Remove/New; upgrade states; tab/slot/unlock/upgrade data |
| Factions | Implemented | Add/Remove/New; Name, ID, Color, Points, Hidden, Attacked, Hostile Factions |
| Dialogs | Implemented | Category/dialog navigation; Add/New/Edit/Remove; delete confirmation |
| Quests | Implemented | Category/quest navigation; Add/New/Edit/Remove; delete confirmation |
| Transport | Implemented | Add/Edit/Remove/Open/Back for categories and locations |
| PlayerData | Implemented | All Players; Delete; categories Players / Quest / Dialog / Transport / Bank / Factions. Backing player data also has mail, item-giver, and scripting records |
| Recipes | **Broken-labelled** | Modes Global / Carpentry Bench; Add/Remove/New; Ignore Damage / Ignore NBT. Player `GuiRecipes` and Carpentry Bench classes exist, but the Global menu itself labels Recipes `(Broken)` |
| Natural Spawns | **WIP-labelled** | Add/Remove/New UI plus `SpawnController` / `SpawnData` exist. Global menu labels it `(WIP)` |
| Linked NPCs | Implemented | Add/Remove/New reusable linked NPC data. Serializes AI, Display, Stats, Advanced, Inventory, model and transform-related state |

---

## 12. Tools, blocks, and admin surfaces

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

### Xeno counterparts — status

| Theirs | Ours | State |
|---|---|---|
| `npcwand` | `xeno_npc_wand` | **live** |
| `npcmovingpath` | `xeno_npc_path_tool` | **live** |
| `npcmobcloner` | `xeno_npc_cloner` | **live** — copies a whole NPC, original untouched |
| `npcjarempty` / `npcjarfilled` | `xeno_npc_jar` | **live** — one item, two states via components |
| `npcmounter` | `xeno_npc_mounter` | **live** |
| `npcteleporter` | `xeno_npc_teleporter` | **live** — no screen; sneak-use cycles dimensions |
| `npcscripter` | — | **blocked**, see below |
| `scripted_item` | — | **blocked**, see below |
| `npcscripteddoortool` | — | **blocked**, see below |
| `nbt_book` | — | not started |

All six live tools implement `XenoNpcTool`, so `XenoNpcEntity.mobInteract` reaches them through one
dispatch rather than a per-tool `if`. That was not cosmetic: the Cloner shipped registered, textured
and compiling but **absent from the old if-chain**, so right-clicking an NPC opened the editor
instead — a tool that looks broken rather than missing. `NpcToolsTest` now fails if a tool is
unregistered, untextured, untranslated, missing from the creative tab, or unrouted.

**The three script tools are blocked, and not on effort.** We own no script engine. Scripts today run
inside *MyNPCs' and CustomNPCs'* `javax.script.ScriptEngine` — `ScriptContainerXenoBindingMixin`
injects a `XenoPixels` binding into their scope (`engine.put("XenoPixels", api)`). So NPC scripting
works only when another NPC mod is installed, which is the dependency the native system exists to
avoid.

A native runtime means a **new engine dependency** (Java 21 removed Nashorn, so GraalJS or Rhino) plus
a sandbox, and it is the largest security surface this mod could add — `xenoNpcDialogueCommands` is
already off by default because *"anyone who can add a datapack could hand players arbitrary command
access through an NPC"*, and arbitrary code is that concern several orders larger. No stub items are
registered in the meantime: a tool that registers and does nothing is a dead control in item form.

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
- `mynpcs:npccarpentybench` — literal registry spelling in this JAR
- `mynpcs:nether_furnace`

### Cloner / Mob Spawner

- Tabs 1–9
- Sources: Clones / Entities / Server
- Spawn action and Mob Spawner handling
- Add/save chooses tab 1–9, Client/Server, overwrite confirmation
- Mounter: Mount and Mount Player
- Selector: Done / Cancel

### Moving Path tool

Path point controls: Down / Up / Delete. Path data is consumed by NPC movement AI.

### Teleporter tool

Dimension list and TP.

### Remote Editor

Edit / Delete / Reset / TP / Reset all / Freeze.

### NBT Book

Edit / Close / Save; stores/edits NBT payload and entity/data identifiers.

### Builder / Copy / Border / Redstone / Waypoint

- **Builder:** schematic dimensions/state, Y offset, rotation, availability, preview, instant build
- **Copy:** Height / Width / Length / Name / Save / Cancel for structure capture
- **Border:** Availability, Height, Message, Done
- **Redstone:** Availability, Detailed Yes/No, on/off-distance-like coordinate/distance controls
- **Waypoint:** Name, Range, Done; used by location-style quest/world logic

### NPC Jar / soulstone-style capture

Empty and filled jar items are registered. Follower setup has Allow Soulstone / Allow NPC Jar. Config flags: `SoulStoneAnimals`, `SoulStoneNPCs`. Exact capture restrictions are those flags plus the code paths behind them; this catalog does not invent extra capturable entity classes.

---

## 13. Nether Furnace

**Status:** Implemented block + bundled recipe. Author-described burn behavior is noted as public description, not independently re-simulated here.

- Block: `mynpcs:nether_furnace`
- Recipe: `data/mynpcs/recipe/nether_furnace.json`
- Public 1.5.0 description: uses its netherrack base for fuel-free continuous burning while smeltable input is present; can be extinguished with a shovel; reignited with flint-and-steel. The block and recipe are in the JAR; that specific interaction sequence is the author’s stated behavior.

---

## 14. Scripting

**Status:** Implemented editor and engines; global Forge script page labelled **BROKEN**

### Script editor GUI

- Settings
- Add (`+`)
- Hide Functions / Show Functions
- Clear, Paste, Copy, Remove
- Load Scripts
- Language selector
- Enabled Yes/No
- Open scripts folder
- Website
- API Doc
- Available Scripts / Loaded Scripts with `>`, `<`, `>>`, `<<`, Done

Global scripting GUI: Players and **Forge (BROKEN)**. The broken label is hard-coded.

Website buttons recovered in bytecode point at `https://www.patreon.com/cw/Le_Espii`.

### Engines

- `javax.script.ScriptEngineManager`
- Explicit `ecmascript` path
- Attempts to register OpenJDK Nashorn (`org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory`) under JavaScript aliases/extensions
- Can detect/register Kotlin JSR-223 if `KotlinJsr223JvmLocalScriptEngineFactory` is available
- Probes `noppes.scriptengines.ScriptEngines` for optional external engines
- Default persisted language strings include `ECMAScript`

Do **not** assume Kotlin, Lua, or other languages work unless that engine is actually installed at runtime.

### Persistence files

- `world_data.json`
- `player_scripts.json`
- `forge_scripts.json`

Containers store enabled flag, language, script lists, and console/log data.

### Event domains present

`BlockEvent`, `CustomGuiEvent`, `DialogEvent`, `ForgeEvent`, `HandlerEvent`, `ItemEvent`, `MyNPCsEvent`, `NpcEvent`, `PlayerEvent`, `ProjectileEvent`, `QuestEvent`, `RoleEvent`, `WorldEvent`.

### Custom GUI API components

Button, Button List, Label, Text Field, Text Area, Scroll, Slider, Item Slot, Item Renderer, Entity Display, Textured Button, Textured Rectangle, Colored Line, component wrappers, scrollable wrappers, Assets Selector.

Custom GUI events include button, close, scroll, and slot interactions.

Config gate: `EnableScripting`. Related: `NashorArguments` (compiled spelling).

---

## 15. AI engine and abilities

Runtime goal/behavior classes exist for: combat/targeting, abilities, animation, attack-target selection, avoidance, door breaking, shade seeking, following, job/role execution, looking, indoor movement, moving paths, panic, pounce, ranged attacks, return-to-home, sprint-to-target, transform, wander, watch-closest, water navigation, owner-target behavior.

Ability implementations present:

- `AbilityBlock`
- `AbilityPull`
- `AbilityPush`
- `AbilitySmash`
- `AbilitySnare`
- `AbilityTeleport`

Trigger enum exposes `ATTACKED` and `UPDATE`.

**Caveat:** presence proves engine capability. No normal main-editor “Abilities” page was recovered. Do not present abilities as a clearly exposed end-user GUI feature without further runtime testing.

---

## 16. Ecosystem integrations

Presence of helper code does **not** mean the other mod is bundled.

### Cobblemon — Conditional

Dedicated helper plus:

- Entity wrapper and player-data APIs
- Trainer battle actor
- Pokémon editor
- Pokémon Trader / Trainer roles
- Move Tutor
- Healing integration
- Species / forms / abilities / moves / learnsets
- Party / PC access
- Battle state, exchange, move teaching, move PP-up

Pokémon editor fields: Species, Level, Shiny, Form, Nature, Ability, Gender (Male / Female / Genderless), Held Item, IVs (HP/Atk/Def/SpA/SpD/Spe) with Min/Max/Random, EVs with Clear/Random, Moves (up to 4), Random Moves, Move PP Up values.

Nature picker includes the standard 25 natures: Hardy, Lonely, Brave, Adamant, Naughty, Bold, Docile, Relaxed, Impish, Lax, Timid, Hasty, Serious, Jolly, Naive, Modest, Mild, Quiet, Bashful, Rash, Calm, Gentle, Sassy, Careful, Quirky.

Held item picker uses tag `cobblemon:held/is_held_item`.

### Pixelmon — Conditional / reflection compatibility

`PixelmonHelper` uses reflection for party/PC, Pokémon detection/data/model, battle, species/forms/abilities/moves/learnsets, exchange, move-data.

Healer recognizes `pixelmon:healer`.  
Move Relearner code references Pixelmon packet class names.

### TM Craft — Conditional

`TMCraftHelper`: teachable move discovery, category filtering, special-category detection, “can this Pokémon learn this move”.  
Move Tutor is the normal visible role tied to this integration.

### Cobbledollars — Conditional

Helper: get balance, add balance, remove balance, fallback command-based change. Used by trader/bank money paths when enabled.

### FTB Quests — Conditional

Helper: quest-completed check and quest completion.  
Advanced NPC page exposes FTB Quests configuration only when the helper is enabled.

### Armourer’s Workshop — Conditional

Helper hooks renderer layer add/remove events and registers integration behavior when detected.

---

## 17. Commands

Root: **`/mynpcs`**. Names below are recovered from Brigadier registration bytecode.

| Branch | Recovered literals / arguments |
|---|---|
| `clone` | `list`, `add`, `remove`, `spawn`, `grid`; `npc`, `tab`, `name`, optional `pos`, optional `display_name`, `length`/`width` for grid |
| `config` | `leavesdecay <boolean>`, `vineinflateth <boolean>` (literal spelling), `icemelts <boolean>`, `freezenpcs <boolean>`, `debug <boolean>`, `scripting <boolean>`, `chunkloaders <number>`, `font <font> [size]` |
| `dialog` | `reload`, `read`, `unread`, `show`; player and dialog/name arguments |
| `faction` | `add`, `set`, `reset`, `drop` on player/faction points |
| `mark` | `clear` plus operations using `entities`, `type`, `color` |
| `npc` | `home [pos]`, `visible <visibility>`, `delete`, `owner [player]`, `name`, `reset`, `create`; visibility suggestions false / semi / true |
| `quest` | `start`, `finish`, `stop`, `remove`, `objective`, `reload`; player/quest/objective/value arguments |
| `scene` | `time`, `reset`, `start`, `pause` with scene name and time |
| `schema` | `build <name> [pos] [rotation]`, `stop`, `info`, `list`; rotation 0/90/180/270 |
| `script` | `reload`, `trigger <id> [args]`. Reload reports stored-data, Forge-script, and player-script status |
| `slay` | types `all` / `mobs` / `animals` / `items` / `xporbs` / `npcs` / `monster` / `mob`, with optional/ranged targeting |

Permission checks commonly use levels 2 and 4 depending on branch. Config can further restrict: `EspiCommandOpOnly`, `OpsOnly`, `DisablePermissions`, `NpcUseOpCommands`. There is no single universal permission level for the whole tree.

---

## 18. Configuration fields

Static config-backed fields recovered from `MyNpcs`, names preserved exactly as compiled:

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
- `ChuckLoaders` (compiled spelling)
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
- `NpcSpeachTriggersChatEvent` (compiled spelling)
- `VerboseDebug`

---

## 19. Persistence / server data

- Global data: `global.dat` with `_old` / `_new` rotation
- Server clones: `clonednpcs.dat` / `_old` plus JSON clone storage paths
- Linked NPC definitions: JSON files with `_new` replacement flow
- Player data: per-player JSON
- Scripts: `world_data.json`, `player_scripts.json`, `forge_scripts.json`
- Schematics: bundled and external `.schematic` and `.schem`

Public 1.5.0 changelog (external cross-check only) states NPC persistence was overhauled to prevent disappearance / failure to save across chunk unloads, crashes, or server reboots. The persistence files and controllers are in the JAR.

---

## 20. Schematics / builder

- Loads legacy `.schematic` and Sponge-style `.schem`
- Builder block and `/mynpcs schema build <name> [pos] [rotation]`
- Rotation suggestions: 0 / 90 / 180 / 270

Bundled schematics (28):

- `archery_range.schematic`
- `bakery.schematic`
- `barn.schematic`
- `building_site.schematic`
- `chapel.schematic`
- `church.schematic`
- `gate.schematic`
- `glassworks.schematic`
- `guard_tower.schematic`
- `guild_house.schematic`
- `house.schematic`
- `house_small.schematic`
- `inn.schematic`
- `library.schematic`
- `lighthouse.schematic`
- `mill.schematic`
- `observatory.schematic`
- `ship.schematic`
- `shop.schematic`
- `stall.schematic`
- `stall2.schematic`
- `stall3.schematic`
- `tier_house1.schematic`
- `tier_house2.schematic`
- `tier_house3.schematic`
- `tower.schematic`
- `wall.schematic`
- `wall_corner.schematic`

---

## 21. Bundled datapack / resource-pack surface

### Datapack

- `data/mynpcs/damage_type/npc.json` — custom NPC damage type
- `data/mynpcs/legacy_blockids.json`
- `data/mynpcs/recipe/nether_furnace.json`
- Markov name corpora under `data/mynpcs/markovnames/`
- Schematics listed in section 20

### Markov corpora files

`ancient_greek_female.txt`, `ancient_greek_male.txt`, `aztec_given.txt`, `japanese_given_female.txt`, `japanese_given_male.txt`, `japanese_surnames.txt`, `mynpcs_classic.txt`, `old_norse_bothgenders.txt`, `roman_cognomina.txt`, `roman_nomina.txt`, `roman_praenomina.txt`, `saami_bothgenders.txt`, `slavic_given.txt`, `slavic_given_alt.txt`, `spanish_given_female.txt`, `spanish_given_male.txt`, `spanish_surnames.txt`, `welsh_female.txt`, `welsh_male.txt`

### Resource pack

- GUI textures under `assets/mynpcs/textures/gui` (47 files)
- NPC/entity/model textures, sounds, localization under `assets/mynpcs`
- In-game texture selector, sound selector, and generic assets selector APIs

---

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

---

## 23. Public API capability surface

**197** top-level public API classes/interfaces (excluding inner classes).

Major groups:

- World/player/entity wrappers, including custom NPC, Cobblemon, and Pixelmon wrappers
- NPC data interfaces: display, stats, AI, melee, ranged, inventory, advanced, role/job, line, mark
- Role/job APIs for classic Bard, Builder, Farmer, Follower, Puppet, Spawner, Dialog, Follower role, Trader, Transporter
- Handlers for clones, dialogs, factions, recipes, quests, scoreboards
- Item/block/world wrappers and scripted block/door/text-plane APIs
- Custom GUI creation/components and sub-GUI asset/availability support
- Overlay APIs
- Event classes across Block, CustomGui, Dialog, Forge, Handler, Item, NPC, Player, Projectile, Quest, Role, World

**Important API caveat:** public `RoleType` constants only enumerate classic roles through Dialog. Runtime 1.5.0 adds Pokémon Trader, Move Relearner, Move Tutor, and Pokémon Trainer. Do not use the old constant class alone as the full 1.5.0 role list.

### API inventory (top-level)

**Core**

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

**Blocks**

- `espi.mynpcs.api.block.IBlock`
- `espi.mynpcs.api.block.IBlockScripted`
- `espi.mynpcs.api.block.IBlockScriptedDoor`
- `espi.mynpcs.api.block.ITextPlane`

**Constants**

- `AnimationType`, `EntitiesType`, `GuiComponentType`, `ItemType`, `JobType`, `MarkType`, `OptionType`, `ParticleType`, `PotionEffectType`, `QuestType`, `RoleType`, `SideType`

**Entities**

- `IAnimal`, `IArrow`, `ICobblemon`, `ICustomNpc`, `IEntity`, `IEntityItem`, `IEntityLiving`, `IMob`, `IMonster`, `IPixelmon`, `IPlayer`, `IProjectile`, `IThrowable`, `IVillager`

**Entity data**

- `ICobblemonPlayerData`, `IData`, `ILine`, `IMark`, `INPCAdvanced`, `INPCAi`, `INPCDisplay`, `INPCInventory`, `INPCJob`, `INPCMelee`, `INPCRanged`, `INPCRole`, `INPCStats`, `IPixelmonPlayerData`, `IPlayerMail`
- Role/job data: `IJobBard`, `IJobBuilder`, `IJobFarmer`, `IJobFollower`, `IJobPuppet`, `IJobSpawner`, `IRoleDialog`, `IRoleFollower`, `IRoleTrader`, `IRoleTransporter`

**Events**

- `BlockEvent`, `CustomGuiEvent`, `DialogEvent`, `ForgeEvent`, `HandlerEvent`, `ItemEvent`, `MyNPCsEvent`, `NpcEvent`, `PlayerEvent`, `ProjectileEvent`, `QuestEvent`, `RoleEvent`, `WorldEvent`
- Function callbacks: `GuiComponentClicked`, `GuiComponentUpdate`, `GuiItemSlotUpdate`

**GUI interfaces**

- `DeathMenu`, `DisplayMenu`, `HealthMenu`, `IAssetsSelector`, `IButton`, `IButtonList`, `IColoredLine`, `IComponentsScrollableWrapper`, `IComponentsWrapper`, `ICustomGui`, `ICustomGuiComponent`, `IEntityDisplay`, `IItemRenderer`, `IItemSlot`, `ILabel`, `IScroll`, `ISlider`, `ITextArea`, `ITextField`, `ITexturedButton`, `ITexturedRect`, `InventoryMenu`, `LogicMenu`, `MainMenuGui`, `MeleeMenu`, `ModelMenu`, `MovementMenu`
- Sub-GUI: `AssetsGui`, `AvailabilityGui`, `SelectorGui`

**Handlers**

- `ICloneHandler`, `IDialogHandler`, `IFactionHandler`, `IQuestHandler`, `IRecipeHandler`
- Handler data: `IAvailability`, `IDialog`, `IDialogCategory`, `IDialogOption`, `IFaction`, `IQuest`, `IQuestCategory`, `IQuestObjective`, `IRecipe`

**Items**

- `IItemArmor`, `IItemBlock`, `IItemBook`, `IItemScripted`, `IItemStack`

**Overlays**

- `ILabel`, `IOverlay`, `IOverlayComponent`, `IRenderItemOverlay`, `ITexturedRect`

**Wrappers**

- Entity/world/item wrappers: `AnimalWrapper`, `ArrowWrapper`, `BlockPosWrapper`, `BlockScriptedDoorWrapper`, `BlockScriptedWrapper`, `BlockWrapper`, `CobblemonWrapper`, `ContainerWrapper`, `DamageSourceWrapper`, `DimensionWrapper`, `EntityItemWrapper`, `EntityLivingBaseWrapper`, `EntityLivingWrapper`, `EntityWrapper`, `ItemArmorWrapper`, `ItemBlockWrapper`, `ItemBookWrapper`, `ItemScriptedWrapper`, `ItemStackWrapper`, `MonsterWrapper`, `NBTWrapper`, `NPCWrapper`, `OverlayComponentWrapper`, `OverlayLabelWrapper`, `OverlayRenderItemWrapper`, `OverlayTexturedRectWrapper`, `OverlayWrapper`, `PixelmonWrapper`, `PlayerWrapper`, `ProjectileWrapper`, `RayTraceWrapper`, `ScoreboardObjectiveWrapper`, `ScoreboardScoreWrapper`, `ScoreboardTeamWrapper`, `ScoreboardWrapper`, `ThrowableWrapper`, `VillagerWrapper`, `WorldWrapper`, `WrapperEntityData`, `WrapperNpcAPI`
- Custom GUI wrappers: `CustomGuiAssetsSelectorWrapper`, `CustomGuiButtonListWrapper`, `CustomGuiButtonWrapper`, `CustomGuiColoredLineWrapper`, `CustomGuiComponentWrapper`, `CustomGuiEntityDisplayWrapper`, `CustomGuiItemRendererWrapper`, `CustomGuiItemSlotWrapper`, `CustomGuiLabelWrapper`, `CustomGuiScrollWrapper`, `CustomGuiSliderWrapper`, `CustomGuiTextAreaWrapper`, `CustomGuiTextFieldWrapper`, `CustomGuiTexturedRectWrapper`, `CustomGuiWrapper`, `GuiComponentsScrollableWrapper`, `GuiComponentsWrapper`

---

## 24. Capability map (1.5.0)

| Area | Status in this build |
|---|---|
| NPC appearance / model / skin / URL / cape / overlay / glow / boss bar | Implemented, rich GUI |
| Stats / melee / ranged / projectiles / resistances / regen / immunities | Implemented, rich GUI |
| AI / navigation / moving paths / animations / mount control | Implemented, rich GUI |
| Inventory / equipment / drops / EXP | Implemented |
| Roles | Implemented selector; Companion labelled WIP; Move Relearner hidden from normal selector |
| Jobs | Implemented selector with 11 concrete jobs plus No Job |
| Quest authoring + player quest log | Implemented |
| Shared Availability gating engine | Implemented and reused across systems |
| Dialog tree authoring + wheel interaction | Implemented |
| Factions | Implemented |
| Banks / vault unlock-upgrade / fees | Implemented |
| Economy money path | Implemented when Cobbledollars/helper is present |
| Transport network | Implemented |
| Mail + mailbox blocks | Implemented |
| Trader item + money modes | Implemented |
| Pokémon Trader / Trainer / Editor | Conditional |
| Move Tutor / TM Craft | Conditional, visible role |
| Move Relearner | Code/UI present, not normally selectable |
| Healer potions | Implemented |
| Healer Pokémon party heal | Conditional; requires nearby Cobblemon healing machine or Pixelmon healer |
| FTB Quests hooks | Conditional |
| Armourer’s Workshop renderer hooks | Conditional |
| Scripts / custom GUI API / events | Implemented |
| Forge scripting page | Labelled BROKEN |
| Cloner / spawner / mounter / path / teleport / NBT / wand / scripter | Implemented |
| Schematics / Builder / Copy | Implemented; 28 bundled schematics |
| Recipes manager | Present but labelled Broken |
| Natural Spawns manager | Present but labelled WIP |
| Nether Furnace | Implemented + bundled recipe |
| NPC Jar / soulstone flags | Implemented items + config flags |
| Chat bubbles | Config flag `EnableChatBubbles` present |
| Scene system + `/mynpcs scene` | Implemented |
| Marks + `/mynpcs mark` | Implemented |
| Remote editor freeze/reset/TP | Implemented |
| Public scripting/entity/gui/event API | Large implemented surface |
| Ability engine (`Block/Pull/Push/Smash/Snare/Teleport`) | Code present; no recovered main-editor Abilities page |
| Resource/data assets, languages, Markov names | Bundled |

---

## 25. Do not overclaim

1. **Recipes** is explicitly labelled `(Broken)` in the Global menu.
2. **Natural Spawns** is explicitly labelled `(WIP)` in the Global menu.
3. **Companion** is explicitly labelled `(WIP)` in the Advanced role selector.
4. **Forge scripting** is explicitly labelled `(BROKEN)` in `GuiScriptGlobal`.
5. **Move Relearner** exists but the visible role selector skips role ID 9.
6. Public `RoleType` constants are older/narrower than the 1.5.0 runtime selector.
7. Optional integrations are detected at runtime. Helper classes are not proof the external mod is installed.
8. A class or API type proves code capability, not necessarily a finished normal-UI feature.
9. Ability classes exist; they were not recovered as a normal Display/Stats/AI/Inventory/Advanced page.
10. Nether Furnace interaction details beyond “block and recipe exist” come from the author’s public description, not from a live in-game test in this catalog.
11. Chunk Loader is selectable as a job; a rich dedicated setup screen like Bard/Guard was not recovered.
12. License metadata in the JAR (`MIT`) disagrees with the current public CurseForge page (All Rights Reserved). This catalog records the discrepancy only.

---

## 26. Sources

Primary:

- Uploaded `mynpcs-neoforge-1.5.0.jar`
- Companion reverse-engineering notes derived from that JAR: metadata, `en_us.json`, `data/mynpcs/**`, `espi.mynpcs.client.gui.*`, `espi.mynpcs.roles.*`, `espi.mynpcs.controllers.*`, `espi.mynpcs.entity.data.*`, `espi.mynpcs.api.*`, packet and registry classes, GUI construction bytecode

External text was used only as a cross-check for stated intent (Nether Furnace description, persistence changelog, CurseForge license line). It was not used to invent missing GUI behavior.

This catalog does not include live multiplayer testing, world-save round-trip tests, or confirmation that every conditional helper succeeds against a specific third-party mod version.
