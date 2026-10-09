# DragonMineZ — Mob & Quest-Mob AI ("Combat Brain") Deep Dive

This document is a companion to `doco.md`, focused entirely on how DragonMineZ's non-player-character mobs think and fight. It was produced by decompiling the mod's actual bytecode (`javap -p -c` on the relevant classes) rather than by reading documentation — every distance number, cooldown, probability, and branch condition below is taken directly from the compiled `.class` files inside `dragonminez-2_1_3.jar`. Where a number could not be recovered with certainty it is described qualitatively instead of guessed at.

DragonMineZ does **not** use vanilla Minecraft's `Brain`/`Sensor`/`MemoryModuleType` framework (the "Brain" API added for Villagers/Piglins) at all. Its custom mobs use plain, older-style `Goal`/`GoalSelector` AI (the same framework Zombies and Skeletons use) for movement and targeting, and layer a **hand-written, fully custom decision system** — a class literally named `SagasCombatBrain` — on top of it for combat *decision-making* (what to do, not how to path there). This is a rule-based "if/else" decision tree over a hand-computed snapshot of the fight, closer in spirit to a simple behavior tree or a GOAP-style utility system than to anything Mojang ships.

There are, in effect, **three distinct tiers of mob AI** in the mod, and which one a given mob gets depends entirely on its Java class and, for the flagship system, a runtime-configurable "AI Tier" field:

1. **Passive/decorative AI** — trainer NPCs (Master Roshi, Whis, King Kai, etc.) and the generic quest-giver NPC. No combat capability at all.
2. **Vanilla-goal AI** — the Red Ribbon Army mobs (bandits, soldiers, robots) and wild animals. Ordinary Minecraft `Goal` classes only, no custom brain.
3. **The `DBSagasEntity` combat brain** — every Dragon Ball character mob (Goku, Vegeta, Frieza, Cell, Buu, the Ginyu Force, and every one of their transformation stages — 130+ classes in total) plus every quest-spawned "boss" kill-target. This is the system described in depth below.

---

## 1. The three AI tiers, and why they exist

`DBSagasEntity` (the abstract base class every Saga character extends) has an internal enum:

```
DBSagasEntity.AiTier { SIMPLE, TACTICAL, ADVANCED }
```

Every `DBSagasEntity` defaults to **`SIMPLE`** at construction (confirmed directly in the constructor bytecode). A mob's tier can be changed at runtime via `setAiTierById(int)`, which maps a **1-based** id to the enum ordinal (id 1 → SIMPLE, 2 → TACTICAL, 3 → ADVANCED; an out-of-range id is silently ignored and the current tier is kept). This is exactly the field quest designers set per-boss: `KillObjective` (the quest system's "kill this mob" objective, see `doco.md` §3) carries its own `aiTier` integer, so a quest author can deliberately spawn an easy SIMPLE-tier Raditz for an early tutorial fight and a brutal ADVANCED-tier Raditz for a late-game rematch, using the very same Java entity class.

What changes between tiers, concretely:

- **`SIMPLE`**: The mob relies purely on ordinary `Goal`s. A dedicated goal, `SagasUseSkillGoal`, is gated with `entity.getAiTier() == AiTier.SIMPLE` — it is the *only* tier that goal will fire for. It has no positioning intelligence beyond vanilla pathfinding/melee-attack goals; it does not run `SagasCombatBrain` at all.
- **`TACTICAL`** and **`ADVANCED`**: The mob's `tick()` method calls `runBrainDecision()` every 6 game ticks (~0.3 s) whenever it has a living target, is not already mid-cast/mid-combo/mid-teleport/evading/stunned. This is where the real `SagasCombatBrain.decide(...)` logic (described in full below) takes over.
- Within the brain itself, `ADVANCED` unlocks two extra behaviors that `TACTICAL` does not get: **punishing a target's incoming attack with a stun combo when the brain owner is not itself critically low on HP**, and reacting differently to a target that is casting from very close range (see the branch analysis in §3). Both tiers otherwise share the same decision tree, ranges, and skill-selection logic — `ADVANCED` is `TACTICAL` plus a couple of sharper reflexes, not a different algorithm.

A separate goal, `SagasUseSkillGoal`, exists purely as the **fallback skill-firing behavior for `SIMPLE`-tier mobs** — it does nothing but call `startFirstAvailableSkill()` when the mob `hasSkillReady()` and is not casting/comboing/evading/stunned. It carries `Goal.Flag.MOVE` and `Goal.Flag.LOOK`, so while a SIMPLE mob is winding up a skill it also stops its own independent movement/look goals from fighting it for control, exactly like a normal vanilla attack goal would.

---

## 2. What the brain "sees": `CombatContext.snapshot()`

Every brain decision starts by building a `CombatContext` — a single immutable snapshot of everything relevant about the current fight, computed fresh each decision tick from `CombatContext.snapshot(self, target)`. This is the brain's entire sensory input; it has no persistent world model or memory beyond this snapshot (plus a handful of cooldown counters stored on the mob itself). The fields it fills, and exactly how each is computed:

| Field | How it's computed |
|---|---|
| `dist3D` | Full 3D Euclidean distance between self and target positions. |
| `horizontalDist` | Distance ignoring Y (X/Z only). |
| `verticalDiff` | Difference in Y position. |
| `hasLineOfSight` | `self.getSensing().hasLineOfSight(target)` — vanilla Minecraft LOS raycast. |
| `closingSpeed` | The target's velocity vector dotted against the normalized direction from target to self, divided by distance — i.e. a signed scalar of how fast the target is moving *toward or away from* the mob (positive = approaching). |
| `selfHpPct` / `targetHpPct` | `getHealth() / getMaxHealth()` for each side. |
| `canFly` | `self.canFly()`. |
| `zanzokenReady` | `self.isZanzokenReady()` — is the "Zanzoken" instant-teleport-dodge off cooldown. |
| `wildSenseReady` | `self.isWildSenseReady()` — is the "Wild Sense" evasive-read ability off cooldown. |
| `comboReady` | `self.isComboReady()` — is a melee combo currently off cooldown. |
| `dashReady` | `self.isDashReady()` — is a burst-speed dash off cooldown. |
| `readySkills` | The subset of `self.getSkillPool()` (its assigned ki-skill loadout) whose individual `currentCooldown == 0`. |
| `targetBlocking` / `targetStunned` / `targetKnockedDown` | Read from the target's `StatsData.getStatus()` (`Status.isBlocking()`, `isStunned()`, `isKnockedDown()`) — **only available if the target is a player with the mod's stats capability attached**; for non-player targets these simply read as `false`. |
| `targetTransforming` | `Status.isActionCharging()`. |
| `targetCasting` | `Techniques.isTechniqueCharging()` on the target's `StatsData`. |
| `targetChargePercent` | `Techniques.getTechniqueChargePercent()`. |
| `targetCommittedCast` | Derived from `TechniqueDispatcher.restrictsMovementWhileCharging(chargingKiType)` — whether the specific ki attack the target is charging locks their movement (i.e. they're "committed" and can't easily bail out of the cast). |
| `targetFiring` | `TechniqueDispatcher.isFiringKiAttack(player)` — is the target a player actively in the firing/beam phase of a ki attack right now. |

Two derived helper methods round this out:
- `targetApproaching()` / `targetRetreating()` — sign-checks on `closingSpeed` against a constant `APPROACH_THRESHOLD = 0.05`.
- `targetHelpless()` — true if the target is stunned or knocked down.
- `readyByRole(SkillRole...)` / `hasReadyRole(SkillRole...)` — filters `readySkills` down to only the skills tagged with one of the given roles (see §4 for the role list). This is the mechanism the brain uses to ask "do I have anything ready that can punish a blocking target," "do I have anything ready that hits from range," etc., without hardcoding which named attack that is per-character.

This snapshot/query design is exactly why the same `SagasCombatBrain` code can drive Goku, a Saibaman, and Majin Buu identically: the brain never references a specific attack by name, only by its abstract `SkillRole`.

---

## 3. The decision tree: `SagasCombatBrain.decide(CombatContext)`

`decide()` is a pure static function: `CombatContext → Intent`. It never mutates anything itself — it hands back an `Intent` (one of six types: `MELEE`, `APPROACH`, `TELEPORT`, `CAST`, `COMBO`, `HOLD`) which the entity's own `runBrainDecision()` then executes. This separation (pure decision function vs. the stateful entity that carries it out) is deliberate — `decide()` can be reasoned about, and presumably tested, without spinning up a real Minecraft world.

The three "band" constants that structure almost every branch:

```
MELEE_RANGE = 4.5 blocks
MID_RANGE   = 12.0 blocks
OUT_RANGE   = 28.0 blocks
```

Reconstructing the exact branch order from the disassembled bytecode (`isAdvanced = aiTier == ADVANCED`):

1. **Emergency stun-combo (ADVANCED only).** If `selfHpPct < 25%` **and** a combo is ready **and** the target is *not* approaching **and** the mob has combo id `7` unlocked → immediately fire combo `7`. This is a "panic button" available only to the sharper AI tier: when critically low on health against a target that isn't closing the gap, it spends its combo resource on a specific combo (id 7, part of the reserved combo-id space; the general-purpose `STUN_COMBOS` set below does not include id 7, so this looks like a dedicated "desperation" combo separate from the normal punish combos).
2. **Punish an incoming cast, at close range (ADVANCED only).** If the target is casting and `dist3D ≤ 4.5`: if combo is ready, pick a random entry from `STUN_COMBOS = {1, 3, 8}` and fire that combo; otherwise fall back to plain `MELEE`. If the target is casting but still farther than melee range, the mob instead `APPROACH`es using `WALK_SLOW` locomotion (a deliberately unhurried approach — closing in on a caster without over-committing).
3. **Finish a helpless or transforming target, at close range.** If the target is helpless (stunned/knocked-down) or transforming, and `dist3D ≤ 4.5`: if combo is ready, roll a random entry from `HEAVY_COMBOS = {3, 1}` and fire it — a dedicated "execute" punish. Otherwise, look for any ready skill tagged `HITSCAN` or `GUARD_BREAK` and `CAST` it if one exists.
4. **Punish a blocking target.** If the target is blocking: look for a ready `GUARD_BREAK`-role skill; if one exists **and** distance ≤ 28 (`OUT_RANGE`), cast it. Otherwise, at close range with a combo ready, fire a random `PRESSURE_COMBOS = {0, 8}` combo. Otherwise fall back to plain `MELEE`.
5. **Way out of range (> 28 blocks).** Delegate to `reposition()` (see below) — the mob isn't going to accomplish anything from this far away except close the gap.
6. **Mid range (12–28 blocks).**
   - If the target is approaching: prefer a ready `HITSCAN` skill; failing that, a ready `RANGED_TRAVEL` skill with a 60% chance to actually use it (`roll(random, 0.6f)`); failing either, just `APPROACH` at `RUN` speed.
   - Otherwise (target holding position or retreating): look for a ready `RANGED_TRAVEL` or `ZONING` skill and cast it if found; otherwise `reposition()`.
7. **Close-to-mid range (4.5–12 blocks).** Look for a ready `HITSCAN` or `PROJECTILE_FAST` skill; the chance of actually firing it if one is available is **55% for ADVANCED, 65% for TACTICAL** (a deliberately *lower* proc chance for the "smarter" tier here — ADVANCED mobs favor melee/combo pressure over spamming ranged skills at this range, while TACTICAL mobs are comparatively more trigger-happy). Failing that roll or with nothing ready, `APPROACH` at `RUN` speed.
8. **Melee range default (≤ 4.5 blocks, none of the above matched).** If combo is ready, 60% chance to fire a random `PRESSURE_COMBOS` combo; otherwise, or on the 40% miss, fall back to plain `MELEE`.

### `reposition()` — what happens when there's nothing better to do
Called both when the target is far outside effective range and as the final fallback inside the mid-range branch. It has its own small independent priority list:
1. If Wild Sense is ready, 40% chance to `TELEPORT` (a proactive repositioning teleport, distinct from the panic/dodge Zanzoken).
2. Else, if Dash is ready, 50% chance to `APPROACH` using `DASH` locomotion.
3. Otherwise, plain `APPROACH` at `RUN` locomotion.

### Combo pools referenced by id
```
STUN_COMBOS     = {1, 3, 8}   // used to punish a close-range cast
PRESSURE_COMBOS = {0, 8}      // used for general offensive pressure
HEAVY_COMBOS    = {3, 1}      // used to execute a helpless/transforming target
```
`hasCombo(entity, id)` / `chooseCombo(entity, pool, random)` filter these pools down to whatever combo ids that specific mob actually has unlocked (`getAllowedCombos()` — set per-character in each Saga entity's constructor, see §6) before picking a random one; if the mob has none of the pool's ids unlocked, `chooseCombo` returns `-1` and (per the branches above) the brain falls back to melee instead.

---

## 4. What a "skill" is, and how skills are picked

Ki-skills are represented per-instance as a small `KiSkill` record: `id`, `cooldownMax`, `currentCooldown`, `size`, three colors (main/border/outline — for the projectile's visual), and a `SkillRole`. A mob's full loadout is `List<KiSkill> skillPool`, assigned in its subclass constructor via `addKiSkill(KiSkillType, cooldown, size[, colorMain, colorBorder[, colorOutline]])` overloads.

**`KiSkillType`** — the roster of named techniques a skill can be an instance of (21 total): `KAMEHAMEHA`, `GALICK_GUN`, `MAKANKOSAPPO`, `KI_LASER`, `KI_EXPLOSION`, `KI_BARRIER`, `OOZARU_ROAR`, `GENERIC_KI_WAVE`, `OOZARU_BEAM`, `KI_VOLLEY`, `KI_SMALL`, `BLUE_HURRICANE`, `TRIPLE_LASER`, `KIENZAN`, `DEATH_BALL`, `MASENKO`, `BIG_BANG`, `FINAL_FLASH`, `MAJIN_CANDY`, `KI_AIR_VOLLEY`, `DOUBLE_SUNDAY`. Each `KiSkillType` has a fixed `id`, a fixed `SkillRole`, and a fixed `Tier` baked into the enum itself — the *type* of an attack determines its role in the brain's decision-making, not anything the level designer configures per-instance.

**`SkillRole`** (7 values) — this is the abstraction the entire decision tree in §3 is built on:
- `HITSCAN` — instant-hit attacks (used at melee/mid range and to punish helpless targets)
- `GUARD_BREAK` — attacks specifically meant to beat a blocking target
- `RANGED_TRAVEL` — projectiles meant to close distance / hit an approaching or retreating target from mid-to-long range
- `PROJECTILE_FAST` — fast projectiles usable at close-to-mid range alongside HITSCAN
- `ZONING` — area-control attacks used when the target is holding ground at range
- `DEFENSIVE` — (present in the enum; not referenced by any branch shown in `decide()`'s disassembly — likely consumed elsewhere, e.g. a reactive block/counter path outside the brain proper)
- `AOE_BURST` — (present in the enum; likewise not directly branched on inside `decide()` — probably used by the combo/transformation systems or by specific character overrides rather than the generic brain)

**`Tier`** (`WEAK`, `MEDIUM`, `STRONG`) carries a `damageMultiplier` and `cooldownFactor` — a global power/cooldown scalar independent of role, presumably used when computing actual damage/cooldown numbers at cast time (outside the brain's own decision logic, which only cares about role and readiness).

**Selection mechanics:**
- `pick(list, random)` — uniform random choice among the candidates a role-filter produced. The brain never ranks skills by damage or "best answer" — once it has decided *what kind* of skill it wants (by role), which specific one fires is chosen uniformly at random from whatever's off cooldown.
- Every `CAST` intent that actually reaches execution still only has a probabilistic chance of firing rather than being guaranteed (see `runBrainDecision` below) — the brain's `decide()` output is an *intent*, and the entity applies its own randomness on top before committing.

---

## 5. From `Intent` to action: `runBrainDecision()`

The `Intent` returned by `decide()` is executed inside the entity itself, in `runBrainDecision()`, via a `switch` on `Intent.type`:

- **`CAST`** — only actually starts the skill (`startSkill(intent.skill)`) **50% of the time** (`random.nextFloat() < 0.5f`). On the other 50%, the mob instead falls back to melee: sets `meleeAllowed = true`, locomotion to `RUN`, and (if not currently dashing) resets its movement speed attribute back to `defaultMovementSpeed`. This means every "cast" decision from §3 is really only a *coin flip* to actually cast — the deterministic-looking decision tree is deliberately softened with randomness at the execution layer, which is presumably what keeps these fights from feeling like a scripted pattern.
- **`COMBO`** — only starts (`startCombo(intent.comboId)`) if the mob's `comboEnabled` flag is set **and** its shared combo cooldown (`currentComboCooldown`) is at zero; otherwise the intent is silently dropped (no fallback branch here — the mob just does nothing new that tick and continues whatever it was already doing).
- **`TELEPORT`** — calls `performProactiveTeleport(target)` unconditionally.
- **`APPROACH`** — calls `applyApproach(intent.locomotion, target)`, handing off to the mob's own movement-speed/locomotion-mode logic (see §7) for whichever of `WALK`, `WALK_SLOW`, `RUN`, or `DASH` the brain asked for.
- **`MELEE`** (and the default/`HOLD` fallthrough) — sets `meleeAllowed = true`, locomotion to `RUN`, and resets movement speed to default if not dashing — functionally identical to the CAST-intent's 50% failure path. This is what actually re-enables the vanilla `MeleeAttackGoal` override described in §7 to take over.

`runBrainDecision()` itself is only invoked from `tick()`, gated by all of the following simultaneously: the mob has a living target, its `aiTier != SIMPLE`, its 6-tick `decisionCooldown` has elapsed, and it is not currently casting, comboing, mid-Zanzoken, evading, or stunned. Separately and unconditionally of tier, `tick()` also auto-triggers `startComboAuto()` any time the distance to target drops under 6 blocks — a baseline "if you're in my face, I combo you" reflex that even `SIMPLE`-tier mobs get, on top of (not instead of) whatever the brain/goal-based systems are doing.

---

## 6. Mob-specific configuration — a worked example

Individual Saga character classes don't contain any combat *logic* of their own — they only call a handful of `DBSagasEntity` setup methods in their constructor to declare their loadout, and the shared brain/goal system does the rest. Disassembling **`SagaGokuEntity.SagaGokuEndSSJ3Entity`** (Goku, Super Saiyan 3, from the Cell/Buu-era model) as a concrete example, its constructor:

1. `setCanFly(true)`
2. `setAuraColor(...)`
3. `setLightning(true)` — SSJ3's signature aura lightning-crackle visual flag
4. `setKiBlastSpeed(...)`
5. `setDBZStyle(...)` — a rendering/animation-style selector
6. `setEvade(true, cooldown)` — enables the evasion sidestep ability with a cooldown
7. `setAllowedCombos(baseId, ComboType.KI_CHARGE_ATTACK, ComboType.AIR, ComboType.METEOR_COMBINATION, ComboType.BASIC)` — this is what populates `getAllowedCombos()`, which §3's combo-pool filtering checks against
8. `addKiSkill(KiSkillType.KAMEHAMEHA, cooldown, size)`
9. `addKiSkill(KiSkillType.KI_SMALL, cooldown, size, colorMain, colorBorder)`
10. `addKiSkill(KiSkillType.KI_VOLLEY, cooldown, size, colorMain, colorBorder)`
11. `setWildSense(true, cooldown)`
12. `setZanzoken(true, cooldown)`

Notably, **no `setAiTier()` call appears** — this specific transformation stage is left at the class-wide default (`SIMPLE`) unless something external (a quest's `KillObjective.aiTier`, or an admin command) raises it. This confirms the design intent described in §1: the *character* only declares *what it can do* (its skill/combo kit and its evade/dash/wild-sense/zanzoken toggles); *how smart it plays* is a separate, independently tunable dial, set per-spawn rather than per-class.

`ComboType` itself is a small enum with an `id` and a `Tier` (`WEAK`/`MEDIUM`/`STRONG`) per entry: `BASIC`, `AIR`, `KI_CHARGE_ATTACK`, `METEOR_COMBINATION`, `ANDROID_ABSORPTION`, `GUM_PUNCH`, `GUM_EXPAND`, `SLEEP_RECOVERY`, `RAPID_KICKS` — a mix of generic combos (Basic, Air) and character-specific named ones (Android Absorption for the Androids/Cell line, Gum Punch/Gum Expand/Sleep Recovery for Majin Buu's rubber-body and regeneration gimmicks).

---

## 7. Movement layer: how "Locomotion Mode" and vanilla goals cooperate

`DBSagasEntity` also defines `LocomotionMode { IDLE, WALK, WALK_SLOW, RUN, DASH }`. This is a *presentation/speed* layer sitting underneath the brain's `APPROACH`/`MELEE` intents — the brain picks a mode, and `applyApproach(mode, target)` / the mob's own attribute handling translate that into an actual movement-speed attribute value and a movement/animation state (used by the GeckoLib animation controller to pick a walk/run/dash animation).

Critically, the brain does **not** replace vanilla pathfinding — `registerGoals()` still adds the standard priority-ordered goal stack:

| Priority | Goal | Notes |
|---|---|---|
| 1 | Custom `FloatGoal` subclass | Overridden `canUse`/`canContinueToUse` (swim-up-for-air, standard vanilla water-avoidance behavior, specialized per this mob) |
| 2 | `SagasUseSkillGoal` | Only actually activates for `AiTier.SIMPLE` mobs (see §1) |
| 3 | Custom `MeleeAttackGoal` subclass (speed 1.8, don't-require-line-of-sight-to-continue = false) | Overridden so `canUse()`/`canContinueToUse()` additionally require `isMeleeAllowed() && !isStunned()` on top of the normal vanilla melee-goal conditions; its `getAttackInterval()` is derived from the live `ATTACK_SPEED` attribute (`max(2, 20/attackSpeed)` ticks) rather than a fixed vanilla constant |
| 4 | `WaterAvoidingRandomStrollGoal` | Standard vanilla idle wander |
| 5 | `LookAtPlayerGoal` (Player, 45°) | Standard vanilla |
| 6 | `RandomLookAroundGoal` | Standard vanilla |

Target selector priorities: `HurtByTargetGoal`, then `NearestAttackableTargetGoal(Player)`, then `NearestAttackableTargetGoal(Villager)`, then `NearestAttackableTargetGoal(IronGolem)` — so a Saga boss will retaliate against whoever hurt it, and will otherwise hunt players, then villagers, then iron golems, in that priority order.

The `meleeAllowed` boolean is the hinge between the brain and this goal stack: the brain's `CAST`/`COMBO`/`TELEPORT`/`APPROACH` outcomes leave it however it was, but every `MELEE` outcome (and every probabilistic fallback to melee described in §5) explicitly sets it `true`, which is what lets the priority-3 melee goal actually engage; nothing in the disassembly ever sets it back to `false` except construction (`true` by default) — the goal's own `isStunned()` check is what actually suppresses melee when needed, rather than the brain toggling the flag off.

Vanilla attribute *base values* set in `createAttributes()` (before any per-character/per-quest override): `MAX_HEALTH 300`, `MOVEMENT_SPEED 0.25`, `ATTACK_DAMAGE 15`, `FOLLOW_RANGE 64`, `KNOCKBACK_RESISTANCE 0.6`, `ATTACK_SPEED 4.0`, plus the mod's own custom attributes `KI_BLAST_DAMAGE 20`, `FLY_SPEED 0.35`, `KI_BLAST_SPEED 0.6`.

---

## 8. Reaction locks and "fairness" gating: `hasSkillReady()`

Both the `SIMPLE`-tier goal (`SagasUseSkillGoal`) and the brain's own cast logic ultimately depend on `hasSkillReady()`, which enforces a shared set of gates before a mob is even allowed to consider casting anything:

- Not currently in the post-attack "skill grace period" (`isInSkillGracePeriod()`).
- `postCastCooldown <= 0` and `globalActionCooldown <= 0` (a global action lock, separate from any individual skill's own cooldown, that momentarily blocks starting a *new* action right after finishing one).
- Not already combo-ing and not mid-Zanzoken.
- Not currently locked in a beam clash (`BeamClashManager.isClashing(uuid)`) — a mob actively pushing a ki-blast clash cannot also start a new skill.
- The target must exist, be alive, and be farther than 4.0 blocks away (skills are for ranged/mid-range use; at true melee range the mob is expected to rely on melee/combos instead).
- The `skillPool` must be non-empty.

This is the layer that keeps the system "fair"/readable in practice: it's not enough for the decision tree to *want* to cast something — a whole independent stack of cooldown/state locks has to agree first, and the same locks apply uniformly regardless of AI tier.

---

## 9. Village-defense AI (Namekian warriors) — a second, independent custom system

Separate from the `DBSagasEntity` brain entirely, `NamekWarriorEntity` (a plain `PathfinderMob`, not a `DBSagasEntity`) implements a much smaller bespoke system for defending the Namek village:

- **Goals:** standard `FloatGoal`, `MeleeAttackGoal`, `WaterAvoidingRandomStrollGoal`, `LookAtPlayerGoal`, `RandomLookAroundGoal`, `MoveBackToVillageGoal` (vanilla — the same goal Villagers use to return home), plus a custom **`NamekDefendVillageGoal`** (extends vanilla's `TargetGoal`).
- **Target selector:** `HurtByTargetGoal`, then a `NearestAttackableTargetGoal` scoped to `Monster`-class entities with an extra predicate filter (i.e., it hunts *hostile mobs threatening the village*, not players by default — a guard, not an aggressor).
- **`NamekDefendVillageGoal`** carries its own `TargetingConditions` and a `villageAggressor` field; on `start()` it commits to defending against whichever hostile has been flagged as attacking the village.
- **`VillageAlertSystem`** is a small static registry (`Set<NamekWarriorEntity> warriors`) with `registerWarrior`/`unregisterWarrior`/`alertAll(Player)`. Every warrior entity registers itself into this shared set on spawn. `alertAll(player)` is the "call for backup" mechanic — something (presumably a villager being attacked) invokes it, and every currently-registered warrior in the world reacts to that same player, functioning as a simple broadcast alarm rather than anything spatial/proximity-based at the call site itself.

This is a much simpler, purpose-built system than the Saga combat brain — no ranges, no skills, no combos, no probabilistic branching — it exists purely to make an attack on the Namek village mobilize every warrior currently loaded, not to make any individual warrior fight cleverly.

---

## 10. How quests plug into all of this

The quest system's `KillObjective` (`com.dragonminez.common.quest.objectives.KillObjective`) is the bridge between the story/quest layer and the AI described above. Its constructor takes, among others:

- `entityId` — which `DBSagasEntity` subclass to spawn (resolved to an `EntityType` via `resolveEntityType()`; `matches(EntityType)`/`isTag()` support matching by entity-type tag as well as exact type).
- `spawnMode`: `QUEST` (the objective spawns its own dedicated instance) or `NATURAL` (it counts kills of naturally-spawned instances instead of spawning one itself).
- `countMode`: `QUEST_SPAWNED_ONLY` or `ANY_MATCHING` — whether kills only count if the specific quest-spawned instance died, or any matching mob anywhere counts.
- `health`, `meleeDamage`, `kiDamage` — per-quest stat overrides for the spawned instance, independent of whatever that Saga class's own defaults are.
- `textureVariant` — which skin/texture variant to render (ties into `ITextureVariant`, used broadly across the mod for character skin variants).
- **`aiTier`** — the exact 1/2/3 → SIMPLE/TACTICAL/ADVANCED selector from §1, set per quest.
- `canTransform`, plus `transformHealth`/`transformMeleeDamage`/`transformKiDamage` (absolute post-transform overrides) and/or `transformHealthMultiplier`/`transformMeleeMultiplier`/`transformKiMultiplier` (relative post-transform multipliers), and `transformTriggerPercent` — the HP fraction at which the boss transforms mid-fight into its next stage.

In other words: **the same underlying combat brain and the same Java entity classes serve every difficulty of every fight in the game.** A "quest mob" is not a special AI variant — it is an ordinary `DBSagasEntity` instance whose stats, texture, transformation behavior, and AI tier have been dialed in by whichever `KillObjective` spawned it, on top of the fixed skill/combo kit that entity's own constructor declared and the shared `SagasCombatBrain` decision tree that every non-`SIMPLE` instance of that class runs.

---

## Summary table: which AI system governs which mobs

| Mob category | Base class | AI system |
|---|---|---|
| Master/trainer NPCs (Roshi, Whis, King Kai, young-form mentors, etc.) | `MastersEntity` / `AllMastersEntity.*` | None — Float + LookAtPlayer + RandomLookAround only; never attacks |
| Generic quest-giver NPC | `QuestNPCEntity` (extends `MastersEntity`) | None — passive, home-anchored, invulnerable by default |
| Red Ribbon soldiers/bandits/robots | `RedRibbonEntity` and subclasses | Pure vanilla `Goal` AI (Float/Melee/Stroll/LookAtPlayer/RandomLookAround + HurtByTarget/NearestAttackableTarget) |
| Wild animals (dinosaurs, sabertooth, Namek frogs) | (own base classes, not inspected in depth) | Presumed vanilla-style `Goal` AI, consistent with the Red Ribbon pattern |
| Namekian village warriors | `NamekWarriorEntity` (`PathfinderMob`) | Vanilla goals + custom `NamekDefendVillageGoal` + static `VillageAlertSystem` broadcast alarm |
| **All Dragon Ball character mobs & every quest boss** (Goku, Vegeta, Frieza, Cell, Buu, Ginyu Force, Androids, movie villains, and every transformation stage of each) | `DBSagasEntity` and its 130+ subclasses | The full `SagasCombatBrain` system: `AiTier`-gated (`SIMPLE`/`TACTICAL`/`ADVANCED`), range-banded decision tree over a `CombatContext` snapshot, role-tagged ki-skill selection, id-pooled melee combos, Zanzoken/Wild-Sense/Dash utility abilities, and per-quest stat/AI-tier/transformation overrides via `KillObjective` |

---

*Compiled entirely from decompiled bytecode (`javap -p -c`) of `DBSagasEntity`, `SagasCombatBrain`, `CombatContext`, `SagasUseSkillGoal`, `NamekDefendVillageGoal`, `VillageAlertSystem`, `KillObjective`, and related classes inside `dragonminez-2_1_3.jar`. Every distance, cooldown, percentage, and probability cited above is a literal constant found in that bytecode, not an estimate.*
