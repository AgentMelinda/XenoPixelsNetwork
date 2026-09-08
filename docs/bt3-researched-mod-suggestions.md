# Research-backed Budokai Tenkaichi 3 suggestions for XenoPixels

## Purpose

This document translates documented *Dragon Ball Z: Budokai Tenkaichi 3* (BT3) mechanics into practical ideas for XenoPixels, a NeoForge 1.21.1 companion to DragonMineZ. It is a design proposal, not a claim that every feature below is currently absent.

BT3 is most useful as a reference for a **locked-on, three-dimensional arena-combat loop**. Its identity comes from movement, pursuit, branching melee, layered defense, and shared resources working together—not merely from matching its controller buttons.

## Evidence and source quality

- **Primary source:** the official North American PS2 manual documents controls, movement, basic attacks, defense, resources, HUD, modes, training, and options: [Internet Archive page](https://archive.org/details/ps2_Dragon_Ball_Z-_Budokai_Tenkaichi_3_USA), [manual PDF](https://archive.org/download/ps2_Dragon_Ball_Z-_Budokai_Tenkaichi_3_USA/Dragon_Ball_Z-_Budokai_Tenkaichi_3_USA.pdf), and [searchable text](https://archive.org/download/ps2_Dragon_Ball_Z-_Budokai_Tenkaichi_3_USA/Dragon_Ball_Z-_Budokai_Tenkaichi_3_USA_djvu.txt).
- **Secondary source:** Palen92's community-authored GameFAQs battle guide provides a detailed taxonomy of melee branches, pursuit attacks, techniques, transformations, and character differences: [basics](https://gamefaqs.gamespot.com/ps2/939644-dragon-ball-z-budokai-tenkaichi-3/faqs/81504/starting-from-the-bottom), [attack types](https://gamefaqs.gamespot.com/ps2/939644-dragon-ball-z-budokai-tenkaichi-3/faqs/81504/kinds-of-attacks), [Blast 1](https://gamefaqs.gamespot.com/ps2/939644-dragon-ball-z-budokai-tenkaichi-3/faqs/81504/blast-1), [Blast 2 and Ultimate](https://gamefaqs.gamespot.com/ps2/939644-dragon-ball-z-budokai-tenkaichi-3/faqs/81504/blast-2-ultimate-blast), and [roster differences](https://gamefaqs.gamespot.com/ps2/939644-dragon-ball-z-budokai-tenkaichi-3/faqs/81504/the-roster-tips-and-tricks).
- **Secondary source:** Dragon Ball Wiki summaries corroborate named mechanics such as Z Burst Dash and Sonic Sway: [BT3](https://dragonball.fandom.com/wiki/Dragon_Ball_Z:_Budokai_Tenkaichi_3) and [Sonic Sway](https://dragonball.fandom.com/wiki/Sonic_Sway).

The official manual is authoritative for documented behavior. Community references are useful for detail but are labelled accordingly. Exact frame windows, hidden formulas, and CPU decision logic are not treated as verified facts.

## Verified BT3 design findings

### Three-dimensional movement is a combat system

The official manual documents ground movement, jumping into flight, dedicated ascent and descent, normal dash, and a Ki-draining Dragon Dash. Secondary documentation describes Z Burst Dash as a faster pursuit/evasion extension capable of reaching the opponent's rear. Movement therefore has distinct states, costs, steering, and cancel rules rather than one universal speed boost.

**Lesson for XenoPixels:** flight should expose readable tactical states: ordinary flight, ascent/descent, Dragon Dash, Z Burst Dash, launch pursuit, braking, and recovery. Collision and server reconciliation matter as much as speed.

### Lock-on and free look coexist

BT3 has a dedicated lock action. The manual distinguishes locked attacks from free-look camera control, while secondary documentation records effects that can disrupt lock.

**Lesson:** store a server-validated target while keeping client camera smoothing separate. Acquisition cone, range, line of sight, cycling, manual unlock, loss, and reacquisition should be explicit policies. Camera selection alone must never authorize damage.

### Melee is a branching graph

The GameFAQs guide documents rush strings that branch into character-dependent rushing techniques. Examples include Heavy Finish, Rolling Hammer, Flying Kick, Lift Strike, Ground Slash, Rush Ki Wave, and Blaster Wave. Directional charged attacks can launch targets in different directions, followed by pursuit actions such as Dragon Smash, Vanishing Attack, and Lightning Attack.

**Lesson:** directional input should choose a tactical result—stun, rear position, upward launch, ground slam, or distance—not only an animation. A data-driven attack graph should represent startup, active frames/ticks, recovery, hit reaction, branch window, launch state, and permitted pursuit.

### Defense has several different answers

The manual documents guard, lateral evasion, and a timed guard-plus-direction teleport. Secondary sources distinguish Sonic Sway, Z-Counter, Afterimage, barriers, and Explosive Wave. These mechanics answer different attacks and use different timing or resources.

**Lesson:** do not collapse all defense into one invulnerable dodge. Preserve separate roles for sustained guard, step evade, timed vanish, anti-light-string Sonic Sway, high-risk reversal, and equipped defensive skills.

### Ki and Blast Stock create choices

The manual separates Ki, gained through attacking or charging, from automatically filling Blast Stock. Secondary documentation divides major abilities into utility-oriented Blast 1, damaging Blast 2, and Full-Power-only Ultimate Blast. Standard Ki shots can be rapid or charged, and compatible large energy attacks can clash.

**Lesson:** avoid making every action a cooldown-only ability. Techniques need explicit cost type, charge behavior, tracking, blockability, clash class, terrain interaction, self-cost, and cancellation rules.

### Forms change the fighter, not just damage

Secondary documentation describes in-battle transformations paid with Blast Stock, branching/reversible form paths, and form-specific differences in health, Ki, stock capacity, offense, defense, melee speed, charge rate, and movesets.

**Lesson:** form identity should eventually include movement, armor/flinch thresholds, combo branches, charge behavior, techniques, and presentation alongside DragonMineZ stat multipliers.

### Training and modes teach the system

The manual documents passive and reactive training opponents, CPU reaction levels, reset controls, damage information, skill lists, guided Battle Training, team/DP battles, survival content, missions, and tournament rules. Published sources verify selectable CPU difficulty but do not expose BT3's internal AI algorithm.

**Lesson:** build tools that make combat measurable before adding a broad roster. Difficulty should adjust reaction delay, prediction, counter frequency, combo depth, resource planning, and mistakes—not secretly multiply damage.

### Presentation supports readability

The manual documents selectable camera distance, screen shake, zoom, health/Ki/Blast Stock HUD elements, and separate audio options. Presentation is functional feedback, not just decoration.

**Lesson:** lock, charge, vanish, launch direction, resource state, and heavy-hit tier need distinct visual/audio cues and accessibility options.

## XenoPixels current-state comparison

This table is based on `FeatureStatus.java`, `docs/bt3-controller-controls.md`, and the existing BT3/XV2 roadmap documents. “Partial” does not mean broken; it means BT3's broader design contains room for expansion.

| BT3 area | XenoPixels state | Evidence and remaining opportunity |
|---|---|---|
| Controller layout and modes | **Live** | Controlify BT3/Normal modes, direct state reads, lock, charge, flight, guard, dash, transformations, mode-switching radial candidates, and modifier chords are documented. Avoid another input rewrite unless runtime evidence requires it. |
| Lock-on combat | **Live / partial** | Existing combat uses DragonMineZ lock-on and supports cycling. Formal lock-break effects, reacquisition policies, and training diagnostics remain useful. |
| Flight and Dragon Dash | **Live / partial** | Flight activation, ascent/descent, Search/Combat Fly, Dragon Dash, Z-Burst, airborne chase, and backstep exist. Directional launch-to-finisher routes and explicit movement-state diagnostics are the larger opportunities. |
| Basic combo and heavy attacks | **Live / partial** | Combo, finisher, charged fist/kick, rush strikes, airborne chase, and animation intents exist. Direction-selected finish outcomes and a general combo graph are not documented as complete. |
| Vanish and afterimage defense | **Live / partial** | Guard/stamina break, super-counter vanish, collision-safe guard-stick vanish, Sonic Sway, Zanzoken, and afterimage systems exist. A consolidated eligibility matrix and defensive training scenarios could be clearer. |
| Ki techniques | **Live through DMZ / partial** | `FeatureStatus.java` lists KI overcharge and the Xeno HUD/technique hotbar as live; the existing handoff/changelog records guidance and clone synchronization. Technique metadata/presentation should extend verified DMZ hooks instead of replacing them. |
| Transformations | **Live / partial** | Custom DMZ forms and multiplier configuration are live. Form-specific combat feel and transformation graphs are stronger future differentiators. |
| Training | **Live lite** | `/xenotrain` dummy and damage meter exist. Reactive dummy modes, instant full-state reset, move prompts, and branch timing feedback are not listed as live. |
| AI | **Partial** | Repository combat packages and the current changelog contain NPC and clone combat implementations. A shared legal-action model with configurable reaction/mistake profiles would improve BT3-like sparring without claiming to reproduce BT3 internals. |
| Team/mission content | **Live lite / partial** | Parallel Quest Lite, mentor pairing, party HUD, and related systems exist. DP-style team budgets and condition-based arena rules are possible expansions. |
| Presentation | **Live / partial** | XV2-style HUD, technique bar, cooldown strip, animations, and effects exist. Camera-distance, shake/reduced-motion settings, directional combat audio, and training telemetry are valuable gaps. |
| Full skill-tree/customization/dojo packages | **Stubbed** | `FeatureStatus` explicitly identifies several old in-memory scaffolds as stubs. Rebuild only when a concrete gameplay milestone needs them; do not advertise them as implemented. |

## Recommended roadmap

### Priority 0 — Combat-state inspector and advanced training

Build on `/xenotrain` rather than introducing another disconnected mode.

**Behavior**

- Dummy policies: idle, guard light attacks, guard everything legal, side evade, timed vanish, Sonic Sway eligible rushes, counter, and CPU sparring profiles 1–5.
- One command/UI action resets positions, health, Ki, stamina, relevant Xeno meters, target lock, cooldowns, and transformation state using only verified APIs.
- Display the current combo branch, launch vector, hit tier, damage, resource cost, recovery, and why a defensive action succeeded or failed.
- Add guided drills for currently live guard, vanish, Sonic Sway, airborne chase, and beam-clash behavior. Add directional-launch drills only when Priority 1 lands.

**Why first:** it provides objective feedback for every later combat change and exposes regressions without guessing from visuals.

**Acceptance criteria**

- Reset is server-authoritative and cannot duplicate resources or preserve stale combat state.
- Every dummy policy is deterministic at a fixed setting and uses legal player/NPC actions.
- Telemetry agrees with applied server damage and resource changes.

### Priority 1 — Directional melee branches and a complete launch-to-finisher route

Extend the live combo and airborne-chase systems instead of adding another pursuit implementation or more standalone rush skills.

**Behavior**

- At one clearly documented combo branch, target-relative directional input chooses heavy stun, upward launch, ground slam, or distance launch.
- A successful eligible launch feeds the existing bounded airborne-chase path.
- Extend that live chase so it reaches the launched target collision-safely, permits one follow-up hit, then either allows one finisher or returns both fighters to ordinary combat.
- Use existing Xeno/DMZ animations and dispatch paths where their contracts fit; do not invent dispatcher overloads.

**Risks:** Minecraft terrain, unloaded chunks, high latency, target dimension changes, and simultaneous knockback writers.

**Acceptance criteria**

- No infinite pursuit, wall clipping, duplicate damage, or client-authoritative teleport.
- Directional choices remain consistent under keyboard and controller input.
- Losing the target or failing collision checks exits safely without charging resources twice.

### Priority 2 — Formal defensive eligibility matrix

Consolidate the existing guard, vanish, Sonic Sway, and Zanzoken rules into one testable policy layer.

**Behavior**

- Classify incoming attacks as light rush, heavy/smash, throw, standard Ki, beam, explosion, or unblockable where verified source data permits.
- Guard mitigates eligible sustained damage and has a defined stamina-break result.
- Timed vanish avoids eligible hits at a Ki/stamina cost.
- Sonic Sway answers light rush pressure but not heavy/smash attacks.
- Zanzoken remains its distinct prepared afterimage behavior rather than becoming a generic dodge.

**Acceptance criteria**

- One incoming hit can be accepted, guarded, vanished, swayed, or countered exactly once.
- Server tests cover each attack/defense pairing and exhausted-resource cases.
- Failed defense never grants hidden invulnerability.

### Priority 3 — Per-form combat-feel profiles

Keep DragonMineZ responsible for form data and transformation state; add optional Xeno-owned feel modifiers through verified reads.

**Behavior**

- Data-driven values for flight acceleration, dash turn rate, melee speed tier, charge-rate modifier, armor/flinch threshold, vanish cost/range, and permitted combo branches.
- Extend the live transformation-impact behavior with per-form configurable pose, aura transition, sound cue, and bounded push profiles rather than adding a second impact system.
- Explicit inheritance/fallback rules so unknown DMZ or server-added forms retain normal behavior.

**Acceptance criteria**

- Missing/unknown profile data produces unchanged DMZ behavior.
- Modifiers are bounded and server-authoritative where gameplay-affecting.
- Entering/leaving a form restores state without permanent stacking.

### Priority 4 — Technique metadata and clash presentation adapter

Do not replace DragonMineZ's technique system. Add Xeno metadata only for behaviors that DMZ exposes safely.

**Behavior**

- Describe eligible technique category, blockability, clash compatibility, charge presentation, lock-break cue, self-cost warning, and camera/effect profile.
- Improve existing beam-clash feedback with clear participant, resource, progress, and result cues.
- Standardize rapid versus charged basic Ki-shot feedback and movement cancellation.

**Acceptance criteria**

- Unknown/modded techniques fall back to DMZ behavior.
- Metadata never changes server damage or resource costs unless a verified server hook owns that change.
- Clash UI appears only for an actual DMZ clash and cleans up on cancellation/disconnect.

### Priority 5 — BT3-inspired AI difficulty profiles

This should be an original legal-action policy, not a claimed recreation of BT3's private internals.

**Behavior**

- Difficulty changes reaction delay, aim/prediction error, counter likelihood, combo depth, pursuit use, resource reserve, aggression, and deliberate mistake rate.
- AI follows the same costs, cooldowns, targeting, collision, and defensive eligibility as players.
- Personality presets may emphasize pressure, zoning, defense, transformation, or resource conservation.

**Acceptance criteria**

- Difficulty does not secretly increase damage or health unless a separate visible ruleset says so.
- Fixed seeds and inputs produce deterministic policy tests.
- AI cannot attack allies, invalid targets, unloaded entities, or through invalid lifecycle states.

### Priority 6 — Match rules after combat foundations

Add DP-style bounded team composition, condition-based missions, survival, and tournament/ring-out rules only after training and deterministic combat states are stable.

**Acceptance criteria**

- Rules are data-driven, announce win/loss conditions, and cleanly restore player state.
- Public-server permissions and world boundaries are explicit.
- No mode requires rewriting ordinary survival combat.

## Post-Priority-3 integration milestone

After the training foundation and Priorities 1–3 are complete, the most useful integration milestone is one end-to-end drill:

1. Lock a training target.
2. Enter flight and Dragon Dash into range.
3. Perform a basic rush string.
4. Choose an upward directional launch.
5. Pursue once and perform a ground finisher.
6. Reset both fighters and resources instantly.
7. Repeat with the dummy set to guard, timed vanish, and Sonic Sway.
8. Transform and repeat, with telemetry proving which profile values changed.

This vertical slice exercises input, lock-on, movement, collision, combo branching, server damage, defense, forms, telemetry, and reset behavior before adding many techniques or modes.

## What not to do

- **Do not duplicate live systems.** Improve the existing lock-on, flight, combat, training, HUD, and DragonMineZ technique bridges.
- **Do not auto-equip attacks or reclaim player bindings.** Unlocking content and choosing a technique slot are separate actions.
- **Do not add more modifier chords by default.** The controller already has layered inputs; prefer radial/context actions and remappable dedicated abstractions.
- **Do not trust client camera or input as damage authority.** The server validates targets, costs, cooldowns, collision, and outcomes.
- **Do not turn every mechanic into cooldown-only combat.** Preserve meaningful Ki, stamina, stock, position, timing, and transformation choices.
- **Do not advertise scaffolds as features.** Stub packages remain internal until connected to persistence, events, UI, and gameplay.
- **Do not claim exact BT3 frame data or AI reproduction.** Published sources used here do not establish those internals.
- **Do not copy decompiled game code or proprietary assets.** Recreate high-level behavior with original implementation and project-owned/licensed assets.

## Suggested implementation order

1. Training reset and combat-state telemetry.
2. One directional launch and one bounded pursuit/finisher route.
3. Unified defensive eligibility tests and stamina-break behavior.
4. Optional per-form combat-feel profiles with safe fallback.
5. Technique/clash presentation metadata over verified DMZ hooks.
6. Original AI difficulty/personality profiles.
7. Team budgets, survival, mission, and tournament rules.

This order favors measurable core combat over roster breadth and presentation-only additions.