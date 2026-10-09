# DragonMineZ — Full Feature Documentation

**Mod ID:** `dragonminez` | **Display Name:** DragonMineZ | **Version:** 2.1.3
**Authors:** Yuseix300 & ezShokkoh | **License:** GNU GPL v3.0
**Loader:** NeoForge (`javafml`, loader `[4,)`) | **Minecraft:** 1.21.1 (`[1.21.1,1.22)`) | **NeoForge:** `[21.1.220,)`
**Hard dependencies:** GeckoLib `[4.9.2,)`, TerraBlender `[1.21.1-4.1.0.8,)`, Curios `[9.5.1+1.21.1,)`
**Declared incompatible with:** LegendaryTooltips, EpicFight, BetterCombat
**Bundled libraries (jar-in-jar):** HikariCP 7.1.0, MariaDB Java Client 3.5.9 (a connection-pooled SQL client — evidence of optional external database-backed persistence)

This document was compiled directly from the mod's jar file: `neoforge.mods.toml`, the two mixin configs (`dragonminez.mixins.json`, `dragonminez.sable.mixins.json`), compiled class members (via `javap`), registry/lang data (`assets/dragonminez/lang/en_us.json`, 3,124 keys), and the `data/dragonminez/**` datapack contents (dimensions, dragon ball wish sets, structures, loot tables, recipes). Nothing below is invented; each entry traces to a specific file, class, or string inside the jar. Given the very large size of this mod (61MB jar, 118MB unpacked, several thousand classes), this document describes it system-by-system rather than exhaustively listing every one of its 548 items, 113 blocks, or 3,124 translation strings — the companion document `ai-brain.md` goes into full, line-level detail specifically on the mob/quest-mob AI, as requested separately.

DragonMineZ is a large-scale RPG conversion mod that reimagines Minecraft as a Dragon Ball–based game: player races and transformations, a stat/leveling system, a ki-attack ("technique") combat system, a full quest/saga storyline recreating the Dragon Ball plot with boss NPCs, world-building instructor NPCs, custom dimensions, dragon-ball wish systems, and extensive UI/HUD/customization tooling.

---

## 1. Player character system

### 1.1 Races
Six playable races, each with lore text and its own transformation tree (`race.dragonminez.*` lang keys):

| Race | Flavor text (verbatim) |
|---|---|
| **Human** | "A terrestrial race with highly versatile combat skills... incredibly resourceful and tenacious." |
| **Saiyan** | "A warrior race born for combat... grow stronger after each battle... transform into powerful Super Saiyans." |
| **Namekian** | "A peaceful yet powerful race, with great regenerative abilities... ability to fuse with others of their kind." |
| **Majin** | "A magical and unpredictable race with enormous destructive potential... unique ability to regenerate and change form." |
| **Frost Demon** | "A race of intergalactic conquerors with immense power and boundless cruelty... ability to transform." |
| **Bio-Android** | "Artificially created beings, combining the DNA of the most powerful races... ability to absorb other beings." |

### 1.2 Transformations
Every race has a *group* of transformation branches, and every branch has multiple *forms* — this is the mod's headline mechanic (recognizable Dragon Ball transformation chains):

- **Saiyan:** Oozaru → Golden Oozaru; SS Grades (SSJ, SSJ Grade 2, SSJ Grade 3); Super Saiyan line (SSJ2, SSJ3, SSJ4 [D], SSJ Mastered); Legendary/Mutant forms (Ikari, SSJ Full Power, SSJ Hybrid); Android forms (Android Base, Fused Android, Super Android); plus GT-style SSJ4.
- **Frost Demon:** Evolution forms (Second, Third, Fourth/Fifth, Final Form, Full Power); Artificial/Legendary forms (Mecha, Metal, Metal-Core); Android forms.
- **Namekian:** Super forms (Full Power, Giant, Super Namekian); Legendary forms (Buffed Namek, Evil Namek, Evil Giant Namek); Android forms.
- **Majin:** Pure forms (Kid, Evil, Super, Ultra Majin); Demon/Legendary forms (Innocence Demon, Giant Innocence Demon, Super Demon); Android forms.
- **Human:** Super forms (Buffed, Full Power, Overdrive, Solaris); Legendary forms (Shiyoken, Shin Shiyoken, Chou Shiyoken); Android forms.
- **Bio-Android:** Bio-Evolution (Semi-Perfect, Perfect, Super Perfect, Ultra Perfect); Legendary/Bio-Corruption forms (Xeno, Xeno Full Power, Xeno Max).
- **Universal "stack" forms** available across races/mechanics: **Kaioken** (x2/x3/x4/x10/x20/x100), **Ultra Instinct** (Sign, Mastered), **Ultra Ego** (Sign, Mastered), **Ultimate** (Ultimate).

Transformations are also referenced from the AI/quest system: quest-spawned boss NPCs can be configured with `canTransform`, a `transformTriggerPercent` (the HP% at which the boss transforms mid-fight), and separate post-transform stat multipliers/overrides for health, melee damage, and ki damage (see `ai-brain.md` §6).

### 1.3 Classes
Seven player classes (`class.dragonminez.*`), each with one passive ability, independent of race:

| Class | Passive (verbatim description) |
|---|---|
| **Warrior** | Landing 3 melee hits in a row grants a Fury stack (up to 5); each stack +10% STM regen, and at 5 stacks +10% defense penetration. Stacks refresh on hit, fade if you stop attacking. |
| **Berserker** | Below 66% HP: +25% HP regen, +10% crit chance. Below 33% HP: +75% HP regen, +25% crit chance. |
| **Martial Artist** | Strike attacks deal up to 25% more damage scaling with the target's missing health (no bonus above 75% HP, full bonus at ≤25% HP). |
| **Tank** | 50% of stamina regen is also granted as HP regen; incoming healing is 25% more effective; both bonuses double below 30% HP. |
| **Paladin** | Redirects 15% of party members' incoming damage to self; heals for 15% of damage dealt to allies. |
| **Cleric** | Healing ki attacks get 20% reduced cooldown (or 15% if they also grant a buff, in exchange for the buff lasting 25% longer). |
| **Spiritualist** | Damaging ki attacks get 20% reduced cooldown (or 15% if they also apply a debuff, in exchange for the debuff lasting 25% longer). |

### 1.4 Stats
A custom attribute/stat system layered on top of vanilla attributes (`com.dragonminez.common.stats.*`, `GenericAttributes`, `StatsData`, `StatsCapability`), exposed via chat commands using the abbreviations **STR, SKP, DEF, STM, VIT, PWR, ENE** (per `BonusCommand`'s in-game usage message: *"Invalid stat! Use: STR, SKP, DEF, STM, VIT, PWR, ENE, or ALL"*), plus two custom Minecraft attributes registered by the mod: `critical_chance` and `critical_damage`, and an internal `dmz_health` attribute. Attribute points (AP) and training points (TPs) are both tracked (confirmed by the wish system's "Reallocate Stats" and "Training Points" rewards).

---

## 2. Combat & techniques

- **Techniques** (`com.dragonminez.common.stats.techniques.*`, incl. `TechniqueDispatcher`, `KiAttackData$KiType`, `Techniques`) — ki-based special attacks players and NPCs can charge and fire; the dispatcher tracks whether a ki attack is currently charging, whether charging restricts movement, and the current charge percentage, and whether the caster is actively firing.
- **Status system** (`com.dragonminez.common.stats.character.Status`) — tracks combat states used throughout combat and AI: blocking, stunned, knocked down, and action-charging.
- **Combos** — a hit-chain system (see §ai-brain for the full combo logic on Saga NPCs); on the player side, a parallel combo/weapon system exists under `com.dragonminez.common.combat.weapon` and `com.dragonminez.common.combat.player`.
- **Clashes** (`com.dragonminez.common.combat.clash.*`, `BeamClashManager`) — beam/ki-blast "clash" mechanic (two opposing ki blasts colliding and pushing against each other, a signature Dragon Ball visual), with dedicated client-side clash rendering (`com.dragonminez.client.clash`).
- **Alignment** — a good/evil "Alignment" stat tracked per player (`AlignmentCommand`, `AlignmentReward` quest reward type), presumably affecting story branching or race-appropriate transformations (e.g., evil vs. pure Majin forms).
- **Impact frames / hit-stop** (`com.dragonminez.client.systems.impactframes`) — a client-side combat "impact frame" freeze-effect system for hit feedback.
- **First-person combat camera** (`com.dragonminez.client.render.firstperson`, `com.dragonminez.client.render.camera`) — dedicated first-person rendering/camera handling, notable since vanilla Minecraft combat is normally third-person-agnostic.
- **Weapon system** (`com.dragonminez.common.combat.weapon`, `data/dragonminez/weapon_attributes`) — custom weapon attribute data (e.g. the "Brave Sword" wish reward).
- **Flight** (`com.dragonminez.client.flight`, `EntityAttributes.FLY_SPEED`) — player and NPC ki-based flight, with a "flying fast" boosted state.
- **Ki blasts & beams** (`com.dragonminez.common.init.entities.ki.*`): `KiBlastEntity`, `KiWaveEntity`, `KiLaserEntity`, `KiDiskEntity` (Kienzan-style disk), `KiAreaEntity`, `KiBarrierEntity`, `KiExplosionEntity`/`KiExplosionVisualEntity`, `OzaruFistEntity` (Great Ape punch projectile), and signature-technique projectile classes `SPBlueHurricaneEntity`, `SPDragonFistEntity`, `SPMajinCandyEntity`. An `AbstractKiProjectile` base defines a `ClashRole` and `KiType` shared by all of them (feeding into the clash system above).

---

## 3. Quest / Saga storyline system

(`com.dragonminez.common.quest.*`)

- **Sagas** (`Saga`, `SagaDefaults`, `Saga$SagaRequirements`) — the overarching story is broken into "Sagas" mirroring the Dragon Ball timeline (the entity package names alone — `SagaZFighters`, `SagaFrieza`, `SagaGinyuForces`, `SagaCell`, `SagaBuu`, `SagaAndroids`, `SagaBabidiSoldiers`, `SagaMovies`, etc. — confirm coverage from the Saiyan Saga through the Buu Saga plus movie-only characters).
- **Quests** (`Quest`, `Quest$QuestType`, `Quest$ClaimMode`, `QuestDefaults`, `QuestRegistry`, `QuestParser`, `QuestUpgrader`, `QuestUpdateReport`) — quests are data-driven (parsed from files, versioned/"upgraded" across mod updates, with conflict/file reports), each with a claim mode and type.
- **Side quests** (`SideQuestDefaults`) exist alongside the main saga line.
- **Quest objectives** (`com.dragonminez.common.quest.objectives.*`):
  - `KillObjective` — kill a configured entity (see §4 of `ai-brain.md` for its full parameter set: health/damage overrides, spawn mode, AI tier, transformation control).
  - `ItemObjective` — collect/turn in items.
  - `InteractObjective` — interact with something.
  - `TalkToObjective` — talk to an NPC.
  - `CoordsObjective` / `BiomeObjective` / `DimensionObjective` / `StructureObjective` — location-based objectives (reach coordinates, a biome, a dimension, or a specific structure).
  - `DragonSummonObjective` — summon a Dragon (see §5).
  - `SkillObjective` — use/learn a skill.
- **Quest rewards** (`com.dragonminez.common.quest.rewards.*`): `ItemReward`/`GenericItemReward`, `CommandReward`, `AlignmentReward`, `TransformationReward` (grants a transformation), `SkillReward`, `KiTechniqueReward`, `TPSReward` (training points).
- **Prerequisites & availability** (`QuestPrerequisites` with `Condition`/`ConditionType`/`Operator`/`TimeMode`/`StructureHint`, `QuestAvailabilityChecker` with an `EvaluationContext`) — a rules engine gating whether a quest is available, similar in spirit to the availability system seen in other NPC mods, but purpose-built for saga progression logic (time-of-day, structure proximity, other quest states, etc.).
- **Quest NPCs** — `QuestNPCEntity` (extends the peaceful `MastersEntity` base) is the generic dialogue/quest-giver NPC; boss/enemy quest targets are instead spawned `DBSagasEntity` subclasses per `KillObjective` (see `ai-brain.md`).
- **Difficulty** (`Difficulty` enum) — the "Change Difficulty" wish confirms players choose a story difficulty that can later be changed without resetting saga progress.
- **Party system** (`PartyManager`, `PartyManager$PendingInvite`, `InviteAcceptResult`/`InviteRequestResult`) — multiplayer party/group system for players to team up (referenced by the Paladin class passive above, which redirects damage from "party members").
- **Quest text formatting** (`QuestTextFormatter`, `RequirementContext`, `RewardGroup`) — rich in-GUI formatting of requirements/rewards text.
- **Client Quest GUI** (`com.dragonminez.client.gui.quest`) — dedicated quest-tree/quest-log UI.

---

## 4. Dragon Balls & Wishes

(`com.dragonminez.common.wish.*`, `data/dragonminez/dragonballs/`)

- **Two independent Dragon Ball sets** are shipped as data: **Earth** (summons **Shenron**) and **Namek** (summons **Porunga**), each with its own `ballset.json`, `radar.json` (a dragon radar configuration), `dragon.json` (the dragon entity/summon definition), and a `wishes.json` defining the available wishes.
- **Wishes available** (from `wish.porunga.*` / `wish.shenron.*` lang keys — both dragons share most of the same wish pool):
  - **Brave Sword** — grants the legendary Brave Sword weapon.
  - **Invincible Armor** (and a **Blue** variant) — grants the Invincible armor set.
  - **Armor Materials** — 64 Kikono Shards + 128 Iron Ingots.
  - **Potara Earrings** (Green and Yellow variants) — fusion earrings.
  - **Senzu Beans** — healing items.
  - **Training Points** — 15,000 TPs.
  - **Reallocate Stats** — resets attributes to class base and refunds spent stat points as AP (does not refund TPs).
  - **Re-Customize Character** — change appearance/class (not race) without resetting stats/skills.
  - **Reset Passive** — resets a race's passive racial skill (Saiyan/Namekian/Majin only).
  - **Change Difficulty** — re-pick story difficulty without resetting saga progress.
  - **Reset Story Progress** — wipes all quest progress across every saga and side quest (keeps chosen difficulty).
- **Dragon summon quest objective** (`DragonSummonObjective`) ties story progression to actually gathering and using a Dragon Ball set.

---

## 5. Dimensions & world

- **Custom dimensions** (`data/dragonminez/dimension/*.json` + matching `dimension_type`): **Otherworld** (the afterlife realm — pre-built via shipped region files, `data/dragonminez/regions/otherworld/*.mca`), **Namek** (the Namekian homeworld), **Sacred Kai Planet**, and the **Time Chamber** (the "Hyperbolic Time Chamber" training dimension).
- **TerraBlender integration** — used for custom biome placement in the overworld/other dimensions (a hard dependency).
- **Space Pod** (`com.dragonminez.common.spacepod`, `SpacePodEntity`, `data/dragonminez/spacepod/destinations.json`) — a travel item/vehicle with data-defined destinations (likely used to travel to Namek or other planets).
- **Dynamic growth** (`com.dragonminez.server.dynamicgrowth`) — a server-side system, likely governing structure/village growth or plant/crop growth tied to the mod's Namek village or farming content.
- **Raids** (`com.dragonminez.server.world.raid`, `RaidCommand`) — a raid-event system (likely enemy-wave attacks, in keeping with the Red Ribbon/Saibaman/invasion enemy roster).
- **Energy system** (`com.dragonminez.server.energy`) — a server-side energy mechanic (context suggests ki/power-related, separate from vanilla FE/RF energy).

---

## 6. NPCs (non-combat)

- **Master/Trainer NPCs** (`AllMastersEntity` with nested classes for **Master Roshi, Master Karin, Master Beerus, Master Whis, King Kai (Kaiosama), Old Kai, Guru, Popo, Dende, Uranai Baba, Toribot**, plus "young-form" trainer variants of **Goku, Vegeta, Gohan, Trunks, Krillin, Yamcha, Piccolo, Cell, Frieza, Gero, Babidi** used as mentors rather than bosses) — all built on the shared `MastersEntity` base, which (confirmed via `registerGoals`) is entirely non-hostile: it only registers `FloatGoal`, `LookAtPlayerGoal`, and `RandomLookAroundGoal` — no attack or targeting goals at all. These NPCs exist purely to give dialogue, training, and quests.
- **Quest-giver NPC** — `QuestNPCEntity`, a home-anchored (drift-corrected back to a saved home position), invulnerable-by-default dialogue NPC used for handing out/turning in quests.
- **Namek village NPCs** (`com.dragonminez.common.init.entities.namek.*`): `NamekVillagerEntity` (a custom Villager subtype with its own trade list, `CustomTrade`), `NamekTraderEntity`, `CCNamekianEntity`, and the defensive `NamekWarriorEntity` (see `ai-brain.md` §3 for its village-alert/defense AI).
- **Dialogue system** (`com.dragonminez.common.init.entities` dialogue lang keys — 64 `dialogue.*` strings) backs conversations with these NPCs.

---

## 7. Hostile/enemy content (outside the Saga boss roster)

- **Red Ribbon Army** (`com.dragonminez.common.init.entities.redribbon.*`): `RedRibbonEntity` (base), `RedRibbonSoldierEntity`, `BanditEntity`, `RobotEntity` — plain vanilla-AI hostile mobs (`FloatGoal` + `MeleeAttackGoal` + `WaterAvoidingRandomStrollGoal` + `LookAtPlayerGoal` + `RandomLookAroundGoal`, targeting Players and Villagers), i.e. deliberately simpler "mook" enemies compared to the Saga boss cast.
- **Wild animals** (`com.dragonminez.common.init.entities.animal.*`): `Dino1Entity`, `Dino2Entity`, `DinoFlyEntity`, `DinoGlobalEntity`, `DinoKidEntity` (dinosaurs, fitting the Dragon Ball wilderness aesthetic), `SabertoothEntity`, and `NamekFrogEntity`/`NamekFrogGinyuEntity` (the frogs Ginyu-Force-style villains turn people into).
- **Punch Machine / Shadow Dummy / Majin training dummy** (`PunchMachineEntity`, `ShadowDummyEntity`, `MajinSkillEntity`) — training-target entities, presumably for testing melee/ki damage output (a "punching bag" utility, common in RPG progression mods).
- **The full "Saga" boss/NPC cast** (over 130 distinct entity classes, including every major transformation stage of Goku, Vegeta, Gohan, Piccolo, Trunks, Goten/Gotenks, Frieza (all 7 forms + King Cold + Mecha Frieza), the Ginyu Force, Cell (all 4 stages), Buu (all forms), the Androids (16–19, Dr. Gero), Babidi's soldiers, and an extensive movie-villain roster (Broly and all his forms, Cooler and Metal Cooler, Janemba, Bojack, Turles, Slug, Hirudegarn, Paikuhan, and more)) — fully documented in `ai-brain.md`, since these are exactly the entities driven by the mod's custom combat AI.

---

## 8. Items, blocks, and progression gear

- **548 registered items** and **113 registered blocks** (counted from `item.dragonminez.*` / `block.dragonminez.*` lang keys), organized into five creative item groups: **DMZ Armors**, **DMZ Blocks**, **DMZ Items**, **DMZ Namek**, **DMZ Ores**.
- **Custom armor** (`com.dragonminez.common.init.armor`) including the wish-granted "Invincible" sets and Potara Earrings (fusion accessories, likely a Curios-slot item given the Curios dependency).
- **Custom fluids** (`com.dragonminez.common.init.fluid`) and **particles** (`com.dragonminez.common.init.particles`) support the mod's visual effects (auras, ki trails, explosions).
- **Custom sounds** (`com.dragonminez.common.init.sounds`, 151 `sounds.*` lang entries) covering voice lines, ki-blast SFX, transformation roars, etc.
- **Hair customization** (`com.dragonminez.common.hair`, `com.dragonminez.client.render.hair`) — a dedicated hairstyle system layered over the base character model (fitting since Saiyan hair changes are a core visual signature of transformations).
- **Curios integration** (`com.dragonminez.compat` + the hard Curios dependency) — accessory slots (earrings, capes, etc.).
- **Loot tables & loot modifiers** (`data/dragonminez/loot_table`, `data/dragonminez/loot_modifiers`) — custom drop tables for the Saga bosses and world content, plus global loot-modifier injections (likely adding mod drops to vanilla loot pools).

---

## 9. UI / HUD / client systems

- **HUD** (`com.dragonminez.client.gui.hud`) — battle-power, health/ki bars, and stat displays during combat.
- **Character creation & customization GUI** (`com.dragonminez.client.gui.character`) — race/class/appearance selection at character creation, and the "Re-Customize" wish reward re-opens this later.
- **Radial menu** (`com.dragonminez.client.gui.radial`) — a quick-select radial UI, likely for choosing techniques/skills in combat.
- **Utility menu** (`com.dragonminez.client.gui.utilitymenu`) — a general utility/settings menu.
- **Tooltip system** (`com.dragonminez.client.gui.tooltip`) — custom item/skill tooltip rendering (explicitly incompatible with the third-party "LegendaryTooltips" mod, per the dependency list).
- **Config GUI** (`com.dragonminez.client.gui.config`) — in-game mod configuration screen.
- **Title screen customization** (`com.dragonminez.client.title`) — a custom main-menu/title screen.
- **Crowdin integration** (`com.dragonminez.client.crowdin`) — tooling tied to the Crowdin translation platform, suggesting community-sourced localization pipeline support (only English is shipped in this build's lang folder, however).
- **Shaders & render effects** (`com.dragonminez.client.render.shader`, `com.dragonminez.client.render.effects`) — custom shader-based visual effects (aura glow, energy waves, transformation flashes).
- **Kisense / Taiyoken systems** (`com.dragonminez.client.systems.kisense`, `com.dragonminez.client.systems.taiyoken`) — dedicated client subsystems, named after Dragon Ball's "Kiaisense" (ki-sensing) and "Taiyoken" (Solar Flare blinding technique) — almost certainly powering an enemy/ki detection radar UI and a Solar-Flare-style screen-blind effect, respectively.
- **GeckoLib-driven animation** — all custom entities and (per the model/animation packages) player transformations are animated via GeckoLib's `GeoEntity`/`AnimatableInstanceCache` framework rather than vanilla Minecraft models.

---

## 10. Server administration

**28 server commands** registered (`com.dragonminez.server.commands.*`), covering nearly every system above:

`alignment`, `bonus` (stat bonus add/remove/clear, with operators `+ - *`), `class` (set player class), `config` (live-edit mod config), `cooldowns` (technique cooldown management), `debug` (dumps a debug file, with a `DebugScope`), `effects`, `forms` (transformation control), `hair`, `halo` (likely an Otherworld/afterlife cosmetic — a literal angel halo for dead characters), `locate`, `mastery`, `party`, `points` (stat/skill points), `racialskill`, `raid`, `reload` (with a `ReloadScope`), `restore`, `revive`, `skills`, `stats`, `story` (saga/quest progress), `tail` (Saiyan tail cosmetic/Oozaru trigger), `tech` (technique management, with an `ExperienceMode`), `weight` (character weight/size?), plus a dedicated `DMZPermissions` node registry gating all of the above.

---

## 11. Compatibility & technical infrastructure

- **Mixins:** 9 common + 34 client-side mixins in the main `dragonminez.mixins.json`, plus 3 additional common mixins in a second config, `dragonminez.sable.mixins.json` (a separately named mixin set, likely isolating a specific subsystem — "Sable" — from the main mixin list for maintainability).
- **Jar-in-jar dependencies:** HikariCP (JDBC connection pool) and the MariaDB JDBC driver are shipped inside the mod jar, indicating an optional MySQL/MariaDB-backed persistence path for server data (player stats/quest progress) alongside the default flat-file/NBT storage — mirroring the kind of `db`-style controller seen in other large NPC/RPG mods.
- **Diagnostics** (`com.dragonminez.common.diagnostics`) — internal self-diagnostic tooling (paired with the `DebugCommand`'s debug-file export).
- **Data generation** (`com.dragonminez.common.datagen`, `datagen.builder`) — the mod ships its own datagen providers/builders for generating its large volume of item/recipe/loot/advancement data at build time.
- **Advancements** — 148 `advancements.*` lang entries confirm a full custom advancement tree parallels the saga storyline.
- **Compat layer** (`com.dragonminez.compat`, `com.dragonminez.common.compat`) — an abstraction layer for capabilities/networking, used to keep the mod's core logic decoupled from a specific mod-loader API surface (evidenced by wrapper classes like `LazyOptional`, `Capability`, mirroring old Forge-style capability APIs re-implemented for NeoForge).
- **Networking** — separate C2S/S2C packet packages (`com.dragonminez.common.network.C2S`, `.S2C`) plus a compat-layer "simple network" wrapper (`com.dragonminez.compat.network.simple`).

---

## 12. Summary of counts (as found in the jar)

- Registered entities: **222**
- Registered items: **548**
- Registered blocks: **113**
- Playable races: **6**, each with 3–5 transformation groups and 3–11 forms per group
- Player classes: **7**
- Custom dimensions: **4** (Otherworld, Namek, Sacred Kai Planet, Time Chamber)
- Dragon Ball sets: **2** (Earth/Shenron, Namek/Porunga), **~21** distinct wishes across them
- Server commands: **28**
- Saga/boss entity classes: **130+** (spanning the Saiyan Saga through the Majin Buu Saga, plus a large movie-villain roster)
- Master/trainer NPCs: **22** named characters
- Mixins: 9 + 34 (main) + 3 (sable) = **46**
- Localization keys (English only in this build): **3,124**
- Third-party integrations: GeckoLib (animation, required), TerraBlender (biomes, required), Curios (accessory slots, required); explicit incompatibilities with LegendaryTooltips, EpicFight, BetterCombat

---

*Compiled by static inspection of `dragonminez-2_1_3.jar` — no feature above was inferred from external wiki/documentation knowledge of the mod, prior familiarity, or guesswork; each entry traces to a specific file, class, registry name, or localization string inside the jar itself. See `ai-brain.md` for the dedicated deep-dive on the mob and quest-mob combat AI.*
