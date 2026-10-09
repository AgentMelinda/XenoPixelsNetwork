# Combat V3: Budokai Tenkaichi 3 behavior and attack animations

Date: 2026-10-07. Status: written design and implementation plan approved by the owner on 2026-10-07; subagent-driven execution with per-stage review is authorized.

## Intent and decisions supplied by the owner

Build an independently selectable V3 combat controller based on **Budokai Tenkaichi 3**. The owner corrected the earlier reference to BT4; neither Team BT4 nor Sparking! ZERO is the reference.

- Configure Dragon Dash distance through `/xenoset`, including 999 blocks.
- Left mouse tap performs native DMZ melee; right mouse tap performs a heavy attack with stamina drain.
- Holding left/right mouse retains Charged Punch/Charged Kick respectively.
- Ordinary melee must not automatically launch a victim high into the air after a fixed number of punches.
- Include every attack in the supplied 58:15 video as a selectable DMZ Strike Attack, including energy attacks and cinematic finishers.
- Match each attack's animation to its reference, including whole-body poses, motion, timing and camera. A shared generic rush animation is insufficient.
- Chase, Dragon Dash, crossing to the other side with N, and combat follow-ups use BT3 as their reference.

References:

- Long attack showcase: https://www.youtube.com/watch?v=K5zbCF1HMZo
- Dragon Dash example: https://www.youtube.com/watch?v=eyvr1_PQIHc

The earlier charged-strike decisions remain: progressive golden glow, every fourth eligible fully charged release at 7–20 blocks teleports a punch in front or a kick behind; the kick deliberately launches in a roughly 20-block arc. This is a deterministic count, not a random 25% roll. Removing ordinary periodic launch does not remove these explicitly requested charged attacks.

## Evidence and current behavior inventory

Research is against the current dirty checkout, branch `1.21.1`, HEAD `e011e7bec8061d8c2228cfbdc63ee57468749d0d`. Existing modifications belong to the owner. No code, binary, config or authored game resource has been changed for this V3 request.

| Boundary | Current evidence | V3 decision |
|---|---|---|
| Controller selection | `CombatControllerMode` exposes legacy, bt3_manual and v2; `CombatControllerService` clears live combat state on switching | Append v3; keep existing values and rollback paths |
| Native DMZ melee | Exact jar exposes `CombatAttackRequestC2S(int, boolean, int, int[])` and `processAttackRequest(ServerPlayer, CombatAttackRequestC2S)`; reference source checks slot, cooldown, attack hand and target range, broadcasts native melee animation and invokes `player.attack` | Use this native route for accepted left taps, with existing protection and stun checks |
| V2 strikes | `V2Damage` uses DMZ damage events but suppresses native launch/stamina behavior; this is not the native DMZ attack controller | Do not present reuse of V2 damage as native left-click DMZ melee |
| Input ownership | `Bt3CombatClient`, fist ownership and DMZ helper gate mixins arbitrate custom/native input | One gesture owner selects exactly one tap or charged attack |
| Dragon Dash | `V2Config.dragonDashRange` defaults to 24 and clamps to 128; `V2Moves` and `V2Lock` impose additional validation | Separate V3 range, targeting and movement authority; a config clamp alone is insufficient |
| Lock-on | `NpcKiAim` and client DMZ lock patches use a 128 base; V2 requires valid ki-sense lock | V3 lock acquisition/retention must support the configured dash distance |
| Existing lock config | `LockOnConfig` explicitly describes seat-mounted targeting | Do not widen aircraft/seat lock behavior to implement fist combat |
| Configuration | `XenoConfigRegistry` has typed entries, aliases and stores; `XenoConfigCommands` uses `XENOSERVER_SET` | Add a V3 store and typed entries through the same permission/persistence path |
| Stamina | Exact DMZ `character.Resources` exposes current/set/remove stamina; Xeno NPCs have `NpcResources` | Use the resource owner appropriate to players or Xeno NPCs; do not create a second stamina pool |
| Techniques | `XenoRushTechniques` registers owned IDs additively in DMZ `STRIKE_REGISTRY` | Add unique owned attack IDs; preserve existing DMZ and addon definitions |
| Animation generations | `Bt3AnimationCatalog` already has generations 1–4 | Controller v3 is distinct from animation generation 3; existing names do not prove video parity |
| Published API/network | `api/**` is public; main channel is sequential, currently protocol 106 | Preserve published signatures and existing packet order; append native packets and update exact protocol when wire changes |

Exact DMZ dependency SHA-256: `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`. Fresh addon development audit returned exit 0, zero failures and six warnings. This proves neither gameplay nor animation accuracy.

The YouTube page identifies the long reference as **Dragon Ball Budokai Tenkaichi 3 - All Attacks And Transformations [Part 1]**, by simorollo91, length 58:15. No transcript or attack chapters were available. A complete attack inventory, count, names and animation comparisons are **not verified**. Sparse observations are not a catalog. Transformations in the showcase are classified as non-attack segments; transformation mechanics remain under DMZ ownership.

## Approaches and recommendation

1. **Recommended: V3 controller beside V2, with small shared adapters.** Reuse verified DMZ melee, resource, animation and targeting boundaries; introduce V3 gesture, motion and technique timeline owners. This permits native taps and custom heavy/charged attacks while retaining rollback.
2. Extend V2 with pervasive V3 branches. Smaller initial patch, but V2 strike damage and input ownership conflict with native DMZ taps; coupling grows through every move.
3. Replace all combat modes immediately. Removes rollback before new targeting and hundreds of potential animation variants have runtime evidence. This is not recommended.

The first option replaces only the controller that cannot satisfy the new ownership rules. It does not rewrite native DMZ combat or unrelated NPC, aircraft, UI or addon systems.

## Input and combat ownership

V3 owns empty-hand gestures while a valid combat target is selected. Items, UI interaction and block interaction retain their appropriate existing handling.

| Gesture | Result |
|---|---|
| Left tap | Exactly one native DMZ melee request and native animation |
| Right tap | Exactly one heavy attack; resource transaction and hit result are server-owned |
| Hold left / release | Charged Punch; no extra tap attack from the same gesture |
| Hold right / release | Charged Kick; no extra heavy tap from the same gesture |
| Double A/D | Existing directional vanish intent, retargeted to BT3 movement/pose references |
| R | Retained guard/grab handling |
| W chase input | Approach or intentional launch follow-up; no accidental attack-loop launcher |
| N | Dragon Dash; a fresh N in its continuation window crosses to the other side once |

A tap commits on release before the hold threshold. Crossing the threshold transfers the gesture to charging without having fired a tap. Keep the existing hold/charge thresholds initially; expose tuning only where it has a clear gameplay purpose. Lost target, screen opening, stun, death, dimension change or mode switch cancels both server and client pending intent.

Heavy attacks need a resource transaction that refuses an unaffordable action, charges the attacker once when the action begins, and applies victim drain once only on an accepted hit. The approved draft proposed attacker cost and victim drain both enabled; use that as the default interpretation. The earlier pool clarification has no separate answer yet, and a later owner correction takes precedence. Amounts are separate tunable values, not inferred BT3 damage formulas. DMZ damage/resource hooks must be inspected to prevent duplicate drain.

Normal native impacts may have their ordinary DMZ knockback. Remove Xeno's fixed-count automatic high launch from the V3 normal route. Intentional launch is an explicit heavy-direction, charged-strike or technique beat, with a named follow-up window.

## Range, targeting and motion

Proposed canonical command: `/xenoset v3.dragonDashRange 999`; alias `dragonDashRange`. Default 24 blocks, finite supported range 2–999. The command reports the effective stored value, persists it and synchronizes it to V3 clients. Existing V2 configuration stays intact.

V3 uses a server-owned target UUID plus synchronized position/velocity when the target is outside ordinary client entity tracking. A client entity ID alone cannot support 999-block retention. Validate same dimension, living/attackable target, configured distance, ki-sense eligibility and existing visibility/protection rules. Acquisition searches only server-loaded entities, on a bounded rate-limited request, rather than scanning every tick. No entity or chunk is fabricated to satisfy a target request.

Motion has one owner at a time: approach, cross, strike alignment, grab, throw or cinematic. DMZ flight/search-to-fly integrates through a verified adapter; simultaneous DMZ and Xeno corrections must not fight each other. Preserve the earlier UltimateFinisher flight-jitter fix. Stop close to the target and face it without repeatedly teleporting during approach. Cross uses the approach line and collision-safe landing at the target's current height.

For long dash travel, evaluate a bounded look-ahead policy for existing chunks, releasing temporary ownership on every stop path. Do not keep a 999-block corridor permanently loaded or generate terrain for combat. Chunk ticket APIs and dedicated/client tracking behavior require exact-version verification before implementation. Acceptance at 999 is with a real server-loaded target and a traversable existing-world path; rejection of absent targets or blocked landing is explicit and costs no resources.

## Attack catalog and animation parity

The catalog is a deliverable, not an assumed list from memory. Map the complete supplied Part 1 video before claiming coverage. Each attack entry records:

- source URL, start/end timestamps, displayed name and character/form;
- distinct variant identity, physical/energy/cinematic classification;
- ordered beats: preparation, approach, each hit, reaction, grab/throw, charge, projectile and recovery;
- attacker/victim animation IDs, durations, camera beats, projectile identity, costs and cooldown;
- proof state: observed reference, authored, automated validation, in-game comparison, accepted.

Repeated identical appearances may share an implementation only when the timeline and motion match; distinct character/form choreography keeps a separate entry. Every attack is selectable from the DMZ Strike Attack bar, including beams and energy balls. Registrations remain additive and idempotent. Existing DMZ energy/projectile behavior must be invoked through verified adapters for energy entries; a physical rush renamed after a beam is insufficient.

Author per-attack clips against the Minecraft/DMZ skeleton. Match whole-body anticipation, supporting foot, hip/torso twist, arms, head, root movement and recovery. Root displacement belongs to the movement timeline so authored animation cannot double-apply travel. Victim reactions and camera are timed to the same accepted hit beats. Preserve separate left/right clips where the reference is not a simple mirror.

Animation acceptance requires comparison at preparation, peak wind-up, contact, follow-through and recovery, plus a real-time playback comparison in game. Record timing deviations in ticks and review pose/motion differences openly. No clip is called an exact match solely because its JSON parses or its name exists. Visual retargeting to Minecraft is **not verified** until that comparison is observed. Shared generic rush fallback cannot be marked finished.

AAA impact effects anchor to the actual collision/body point and surface clearance, preserving the earlier request to avoid explosions inside the ground. Kamehameha entries use the real charge/release/projectile route and accepted-hit knockback. Xeno NPC throws must release the grab owner before applying authoritative launch motion.

## Compatibility, rollout and rollback

- Append the V3 controller selector; do not reorder existing persisted enum values or packet discriminators.
- New configuration lives separately; no destructive conversion of V2 JSON or third-party DMZ patches.
- Keep unknown addon/NBT values, published API signatures, existing attack IDs and saved technique selections.
- Validate selected technique IDs on load; additions must not shift existing slot identity.
- New native packet shapes use explicit bounded decoding and a matching protocol update; addon packets remain in `AddonNetwork`.
- Keep common controllers free of client rendering classes. Optional animation/particle/NPC integrations have explicit absence handling.
- Activate V3 explicitly during validation; preserve existing controller defaults until rollout is reviewed.
- Rollback selects the previous controller, cancels active V3 state and releases movement/chunk/camera ownership. V3 config and catalog are retained for later return. No old path is deleted before parity and runtime evidence.

## Staged delivery and acceptance

1. Complete the timestamped video inventory and exact DMZ integration verification. Publish total distinct attacks only after mapping; record every non-attack segment.
2. Add V3 selection/config, gesture ownership and native left taps. Test all tap/hold/cancel paths and switching back to each old controller.
3. Add heavy/charged resource ownership and intentional launches. Preserve golden charging and deterministic fourth eligible teleport behavior; prove Xeno NPC throw/knockback.
4. Implement target retention and Dragon Dash at 24, 128, 512 and 999 blocks, including height differences, moving targets, collision refusal and N crossing. Follow with BT3 Chase timing and animation comparisons.
5. Implement the entire mapped Strike Attack catalog in dependency-ordered animation/timeline families. Per-entry coverage remains visible; family reuse must not erase choreography.
6. Perform per-attack in-game animation review, multiplayer resource/movement proof, distribution and addon checks, then review V3 default rollout.

Required checks: focused meaningful tests, `./gradlew test`, `./gradlew build jarJar serverJar -PofflineMcMeta`, and `./gradlew buildApiExampleAddon -PofflineMcMeta`. Use the approved local Gradle home on this host. Confirm server jarjar contains only metadata and AAA Particles. Verify startup and packets using a fresh client/server process and current logs; unit/build success is not in-game proof.

Runtime matrix includes players and Xeno NPCs; floor/air/unequal heights; DMZ lock present/absent as supported; optional integrations absent; insufficient stamina; protected targets; 999 range boundary; repeated/late/duplicate inputs; switch/death/disconnect/dimension cancellation; saved selections and unknown addon data; rollback after an active cinematic. Indicator visibility preference and earlier charged/finisher fixes must remain available.

The preceding Dragon Dash server test run was deliberately short. A separate DMZ `RadarSyncS2C` payload-binding crash was observed in an earlier fresh run. Long-running server stability is not verified; this V3 design does not authorize silently repairing that unrelated networking issue.

## Xeno Notes

Verified: current controller/config/damage/lock ownership, native melee entrypoint and stamina signatures against the pinned jar, video title/length, audit result. Approved: V3 design above, including the proposed default of heavy attacker cost and victim drain. Not verified: full video inventory, exact animation parity, long-range client retention/chunk policy, new V3 gameplay or performance.

## Xeno TODOs

Complete video mapping; verify movement/projectile/chunk adapter symbols; record baseline protection/resource hooks; add old/new parity fixtures; inspect saved selections/addon compatibility; exercise rollback; collect fresh client animation evidence and multiplayer range proof. Incorporate any later owner correction to heavy stamina interpretation.

## Xeno To Implement

Approved design slices are listed above and in `docs/superpowers/plans/2026-10-07-combat-v3-bt3.md`. The owner approved the plan and selected subagents with per-stage review. Execute the approved slices continuously; do not ask for these approvals again.
