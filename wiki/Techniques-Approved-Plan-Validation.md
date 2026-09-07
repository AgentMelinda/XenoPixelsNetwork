# Techniques approved-plan implementation and validation

This records the continuation of the approved session plan and supersedes historical unfinished
clone-AI statements in the original handoff. It is not a claim of complete gameplay acceptance
or an exhaustive review of all 555 main Java files. Version `0.2.0-1.21.1` and protocol 59 are unchanged.

## Implemented contract

- The previously completed lifecycle foundation is retained: distribute **current** health over
  successfully spawned bodies, retain the initial power divisor, cap copy healing at its initial
  share, and refund only surviving health once on recall. Lost copies never refund health.
  Stationary Zanzoken decoys do not attack. Owner identity includes UUID, not just reusable ID.
- `CloneCombatPolicy` is original deterministic logic. `CloneCombatBridge` executes local
  collision-aware pursuit, formation return, melee, basic ki blasts, supported unlocked strikes,
  recovery and shared owner-resource deductions. There is one server movement controller;
  inherited velocity is cleared before ticking and formation does not overwrite combat movement.
  Obstructed close targets still cause approach rather than idle recovery.
- Copies require an active split and the owner's valid locked target. Owner/sibling copies,
  same-team allies, owned tame pets and DMZ masters are excluded. Player targets honor PvP,
  canHarmPlayer, creative/spectator and party restrictions. Damage is revalidated against the
  current lock and leash. Recall/removal and target changes cancel pending strikes.
- Effective melee/strike/ki come from the owner's StatsData rather than raw-stat approximation.
  Profiles are bounded-refresh transient adapters, not written generic NPC profiles. Ki includes
  the owner's ki attack modifier because DMZ only supplies that modifier for Player projectiles.
  Copy-owned melee/strike/projectile damage resolves to the owner and applies split scaling once
  in `LivingDamageEvent.Pre`; profile values themselves are unsplit.
- Shared ki/stamina are read and deducted from the owner on the logical server. Copies have no
  independent regeneration/refill pools. Basic melee costs at least one owner stamina-per-hit;
  basic blast costs at least one energy or 10% effective ki damage. Strike dispatcher charges its
  owned technique's calculated energy cost plus the existing NPC strike stamina surcharge.
  Basic melee recovery is 20 ticks, blasts 30; strikes also retain technique cooldowns and cast
  recovery. A failed/cancelled impact does not refund accepted-action cost.
- Positive accepted combat while split awards at most one mastery point per owner per 20 ticks,
  capped at 1000. NBT is clamped and respawn copying retains mastery. No client mastery-display
  consumer was found; no new client synchronization/UI was invented.
- Reliable chase defaults/fallbacks are 1.0, including Dragon Dash's shared helper. Explicit
  saved randomness and pay-on-attempt remain unchanged. Z-Burst is unbound for new settings;
  existing bindings remain. Configure it in Controls rather than occupying DMZ Stats' V key.

## Verified API and side-effect evidence

Target: Minecraft 1.21.1, NeoForge 21.1.238, Java 21, local DragonMineZ 2.1.3.
Signatures and behavior were inspected in repository source, the resolved DMZ jar and mapped
`build\moddev\artifacts\neoforge-21.1.238-merged.jar`; no decompiled AI implementation was copied.

| Integration | Verified behavior and design consequence |
| --- | --- |
| `NpcKiAttackDispatcher.fireKiBlast(LivingEntity, NpcCombatProfile, int, LivingEntity, int)` | Does not spend or cool down; bridge charges once and owns recovery. DMZ setup adds the projectile itself. |
| `NpcStrikeDispatcher.fire(String, LivingEntity, NpcCombatProfile, LivingEntity)` | Charges before scheduling, rereads profile at impact; clone adapter is recognized by `NpcCombatProfile.read/readCached`. Owned unlocked StrikeAttackData provides levels and calculated cost, reread at impact. |
| `NpcCombatProfile.write(Entity)` / `hasProfile(Entity)` | Generic setup has attribute/resource/brain effects. Clones are excluded, not repeatedly configured as ordinary NPCs. |
| `NpcResources` | Clone reads/spends delegate to owner's live budget; generic setters/tick do not refill copies. |
| `StatsData.getMeleeDamage/getStrikeDamage/getKiDamage/getKiAttackDamageModifier` | Effective damage is explicit; DMZ non-player projectile damage does not apply its Player-only ki modifier. |
| `StrikeAttackData.getActualDamageMultiplier()` | Includes technique level, not the additional technique-config multiplier; dispatcher retains that config factor. |
| `NpcMeleeDamage.setAnimation(LivingEntity, String)` | Animation only, not a damage or resource API. Clone renderer accepts queued DMZ intents. |
| `PoseStack.mulPose(Matrix4f)` and `last().normal()` | Copy renderer gets an isolated stack initialized with caller position/normal matrices; dependency failure cannot leave pushes on caller stack. Already emitted vertices cannot be undone. |
| DMZ renderer cache | Public API has whole-cache clear, not per-copy eviction. Verified private `TP_RENDERERS` is removed by copy UUID via guarded reflection, with one-time warning on incompatibility. |

The player-specific human one-shot ki bonus is not mirrored/consumed by copies. Xeno rush IDs
are excluded from generic strike selection because player launcher/combo semantics are distinct.
DMZ blasts may still collide/explode and affect terrain under DMZ gamerules; cancellation protects
living-target damage, not every possible projectile world side effect. These are parity/runtime
boundaries, not claims of exhaustive equivalence to player attacks.

## Staged confirmed-findings ledger

Line references identify the current repair sites; paths below are under
`src\main\java\net\bullettrain\xenopixelsmod` unless stated otherwise.

| Evidence / reproduction condition | Confidence | Repair | Validation and boundary |
| --- | --- | --- | --- |
| `client\compat\npc\NpcFullDmzRenderer.java:527,558`: modulo refresh could repeat each frame or miss the refresh tick | High, source-proven | Elapsed-tick stamp, including reset/missed-render cases | Existing renderer JUnit regression; no observed appearance assertion |
| Same file `:534,540,569-584`: copy has no owner equipment; shared identity conflicts with UUID cache; omitted queued animation | High, source-proven | Owner armor, queued intent, copy UUID/owner skin, per-copy eviction | Identity/refresh/pose tests; armor/skin/aura and reflection lifecycle need client observation |
| Same file `:548`: dependency could throw with an unbalanced pose stack | High for isolation guarantee, unknown streak causality | Render into independent initialized stack | Pose matrix/normal and caller-stack regression; does not erase emitted vertices or prove streak cure |
| `compat\npc\NpcKiAttackDispatcher.java:104-120`: case-insensitive lookup followed case-sensitive switch; unsupported registered IDs could spend before failure | High, source-proven | Finite supported-ID gate before spending; Locale.ROOT canonicalization | Dispatcher test covers accepted/unknown/null/uppercase IDs and rejection before caster access, not live cost instrumentation |
| `missile\MissileChunkLoadManager.java:69-79`: an already administrator-forced chunk was recorded and later unforced as if owned | High, source-proven | Skip pre-existing forced chunks; record only successful acquisition | Acquisition supplier regression. An admin subsequently claiming a missile-owned chunk is not separately representable by vanilla boolean ownership; full lifecycle untested |
| `network\packet\SeatFlightInputPacket.java:165-170`: static last tick could exceed a restarted server clock | High, source-proven | Clock rollback accepts first input; same-tick duplicate remains limited | Test covers first/duplicate/next tick/rollback. Original bug dropped one frame, not persistent starvation |
| `capability\XenoPlayerData.java`: mastery award overflow/load bounds and omitted respawn copy | High, source-proven | Saturating award, clamped load and copyFrom preservation | Existing clone/mastery tests; live relog/death still pending |

Coverage: traced the clone lifecycle/resource/damage/strike/render chain, related capability,
chase/defaults and input paths. Wider stage sampled guidance packet validation, missile tickets,
seat input, Create elevator entry points and packaging. Forms/effects/HUD, broad world/content
registration, commands, full Sable physics and all optional-mod/mixin combinations were **not**
fully reviewed. No speculative source-wide cleanup was performed.

## Fresh automated validation

Existing JUnit runner only; no test framework, dependency or version changes.

```powershell
.\gradlew.bat compileJava test --tests '*Clone*Test' --tests '*NpcFullDmzRendererTest' --tests '*NpcCombatProfile*Test' --tests '*NpcKiAttackDispatcherTest' --tests '*NpcStatMathTest' --tests '*XenoServerConfigKeysTest' --tests '*ChaseRoutingTest' --tests '*ChaseFlightOwnershipTest' --tests '*ClientChaseFlightStateTest' --tests '*FistInputPolicyTest' --tests '*ZanzokenWindowTest' --tests '*XenoRushTechniquesTest' --tests '*TechniqueSlotBindTest' --offline
.\gradlew.bat test --tests '*NpcFullDmzRendererTest' --tests '*MissileChunkLoadManagerTest' --tests '*SeatFlightInputPacketTest' --offline
.\gradlew.bat test --offline
.\gradlew.bat jar jarJar serverJar --offline
```

- Earlier combined targeted run: **82 tests**, zero failures/errors.
- Follow-up renderer/missile/seat selectors: passed; superseded by the final full run.
- Final full suite after the last pursuit adjustment: **303 tests, 53 classes, zero failures/errors**.
- Packaging: successful. Final client/server artifacts contain CloneCombatPolicy,
  CloneCombatBridge, CloneSplitState, XenoCloneEntity, metadata version 0.2.0-1.21.1,
  `xenopixelsmod.mixins.json` and `xenopixelsmod.compat.mixins.json`.
  Client jar contains nested `modern-ui-3.13.0.1.jar`; server jar does not.
- Output: `build\libs\xenopixelsmod-0.2.0-1.21.1.jar` and
  `build\libs\xenopixelsmod-Server-0.2.0-1.21.1.jar`.
  Existing older jars/sources artifacts were not deleted; the sourcesJar task was not rerun.

Ignored local evidence: `build\approved-plan-dirty-before.txt`,
`approved-plan-compile.log`, `approved-plan-compile-latest.log`,
`approved-plan-targeted.log`, `approved-plan-targeted-latest.log`,
`approved-plan-review-targeted.log`, `approved-plan-full-test.log`,
`approved-plan-package.log`, `approved-plan-artifact-inspection.txt` and
`approved-plan-server-boot.log` (all prefixed by `build\`). XML/HTML reports remain under
`build\test-results\test` / `build\reports\tests\test`.

## Actual server smoke check and pending runtime acceptance

`runServer --offline -I .\build\approved-plan-runtime.init.gradle` was run in an isolated
new development directory. The init script disabled `uninstallDevShaderMods`, preserving existing
`run\mods`; its relative-path resolution placed files under **`build\build\approved-plan-server`**.
The session-only init script and empty intended run directory were removed after validation;
the generated isolated server files and boot log remain as evidence. For reproduction, configure
`neoForge.runs.getByName('server').gameDirectory` with `project.file(...)` rather than the init
script's `file(...)`, and disable `uninstallDevShaderMods` for that isolated launch.
Existing worlds were not opened. Server reached **Done (15.600s)** and answered a Minecraft
status request on localhost:25565 (1.21.1, protocol 767, zero players). The attached process tree
was stopped and the server PID confirmed gone. This was process termination, not a graceful
save/restart acceptance test. The generated default server briefly listened on all interfaces;
future smoke runs should preconfigure loopback and a dedicated unused port.

Startup log is not clean: invalid dedicated-distribution ClientLevel warnings, missing
CustomNPCs targets/addon payload warning, and Sable unknown `create:flywheel` errors occurred.
CustomNPCs/Create/ComputerCraft were absent from this dev launch. The server nevertheless became
responsive. This is **not** proof that every client or optional-mod mixin applies successfully.

No graphical client was launched and no gameplay/shader frames were observed. Pending:

1. Copy pursuit/melee/ki/strike cost and output, protection, depleted budgets, damage scaling and
   mastery against live targets; local steering in complicated obstacles/mazes.
2. Repeated split/recall, losses, dimension changes, logout/reconnect, death and graceful restart
   health/resource/mastery persistence and no stale ownership.
3. Copy race/hair/skin/name/equipment/form/aura, animation and repeated cache lifecycles.
4. Copies absent/present × shaders off/on, plus no Iris where practical. User pack
   `BSL_v10.1.3.zip` is available and selected in existing Iris configuration. Pink streaks remain
   unresolved; no speculative global renderer fix was made.
5. In-game keybinding/chase/mining/fist/guard behavior and optional-mod mixin combinations.
6. GPL/ARR dependency distribution review; the third-party notice does not resolve obligations.

## Continuation-owned changed files

These are this continuation's edits/additions, not the whole dirty tree. Prior lifecycle files
were preserved (some were extended here); hundreds of unrelated initial entries remain untouched.
No commit, reset, stash, version bump or publication was performed.

Under `src\main\java\net\bullettrain\xenopixelsmod\`:

- `combat\clone\CloneCombatPolicy.java` (new), `CloneCombatBridge.java` (new),
  `XenoCloneEntity.java`, `XenoCloneSystem.java`
- `compat\npc\NpcCombatProfile.java`, `NpcResources.java`, `NpcStrikeDispatcher.java`,
  `NpcDmzAnim.java`, `NpcKiAttackDispatcher.java`
- `capability\XenoPlayerData.java`
- `client\compat\npc\NpcFullDmzRenderer.java`, `client\combat\Bt3CombatClient.java`
- `config\XenoServerConfig.java`, `network\Bt3CombatPacket.java`
- `missile\MissileChunkLoadManager.java`, `network\packet\SeatFlightInputPacket.java`

Under `src\test\java\net\bullettrain\xenopixelsmod\`:

- `combat\clone\CloneCombatPolicyTest.java` (new)
- `compat\npc\NpcKiAttackDispatcherTest.java` (new)
- `client\compat\npc\NpcFullDmzRendererTest.java`
- `network\ChaseRoutingTest.java`, `config\XenoServerConfigKeysTest.java`
- `network\packet\SeatFlightInputPacketTest.java` (new)
- `missile\MissileChunkLoadManagerTest.java` (new)

Documentation: `CHANGELOG.md`, `CLAUDE_TECHNIQUES_HANDOFF_2026-09-06.md`,
`THIRD_PARTY_NOTICES.md`, and this new validation ledger.
