# Handoff — 0.2.0 refactor and bug fixes (restored from a dead Copilot session)

> Written 2026-09-06 by Claude (Opus 5) in `XenoPixelsNetwork_qwen`, branch `new2`,
> Minecraft 1.21.1 / NeoForge 21.1.238 / Java 21, DragonMineZ 2.1.3.
> This document records a session recovery plus the audit that followed it. Everything marked
> "verified" was read out of the actual source, the DMZ jar, or a build/test result in this
> session. Nothing here was reproduced in game.

## 1. Why this document exists

The work that produced the current `0.2.0-1.21.1` working tree was done in a GitHub Copilot CLI
session that died mid-task:

- Session: `C:\Users\Admin\.copilot\session-state\d5b5a6e5-0aab-49d9-879e-e66bd3389e6b`
- Title: *"Create Custom DMZ Punch Animations"*, 61 user turns, 2026-09-05 17:16–22:42 UTC
- Cause of death: **provider failures, not a code failure** — `403` authentication against the
  configured provider, `429 Plan quota exhausted for the 5-hour window`, and repeated *"This model
  is temporarily at capacity"*. Nine `continue..` attempts between 22:32 and 22:42 UTC each failed
  before the model emitted a token.

Its plan lives at `<session-state>/plan.md`. That is **not** this repository's `plan.md`, which is
an unrelated Ki-weapons / Iris shader-compat handoff and was left untouched.

### Exact stopping point

The last successful tool call (22:24:31 UTC) ran a Python edit batch that bumped
`gradle.properties` to `0.2.0-1.21.1`, rewrote the `Unreleased` section of `CHANGELOG.md` as
*Refactor and bug fixes*, relabelled the 0.1.14 draft as superseded, and tidied `build.gradle`
comments. The Gradle command chained immediately after it failed on **command-line syntax**:

```
.\gradlew.bat compileJava test jar jarJar serverJar --tests '*FistInputPolicyTest' ... --offline
> Problem configuring task :serverJar from command line.
> Unknown command-line option '--tests'.
```

`--tests` is a `Test`-task option and cannot be applied to `serverJar`. The fix is to split the
invocation; nothing was wrong with the code. Everything after that point was provider errors, so
the tree was left holding an **unvalidated** 0.2.0: version bumped, changelog rewritten, no
compile, no test run, no packaging, and a `CHANGELOG.md` link pointing at a release page that had
never been written.

## 2. What the 0.2.0 work is

An evidence-based correctness pass over the features listed in `CHANGELOG.md` — twelve defects,
no new gameplay. User framing: *"dont invet api's dont invet librari's dont lie analyze our
features code from changelog.md and check thier code for mistakes.. bugs.. gaps... duplication's
etc"*, then *"bump afterwards it to version 0.2.0 and changelog to say refactor and bug fixes"*.

Reader-facing scope, regressions and limitations are in
[`docs/releases/UNRELEASED-0.2.0-1.21.1.md`](docs/releases/UNRELEASED-0.2.0-1.21.1.md).

## 3. Re-audit results

All twelve defects were re-verified against current source rather than trusted from the dead
session's own completion markers.

| # | Defect | Landed as | Verdict |
|---|---|---|---|
| 1 | Weapon/fist input overlap, no server exclusion | `combat/FistInputPolicy.java`; server gate `network/Bt3CombatPacket.java:157` | Confirmed fixed |
| 2 | Disabled-combat native fallback missing | `FistInputPolicy.ownsAttack` requires `combat && (combo \|\| charge)` | Confirmed fixed |
| 3 | Conflicting mining lifecycle | `mixin/client/MinecraftFistOwnershipMixin.java` (priority 1100, `startAttack`/`continueAttack` HEAD) | Confirmed fixed |
| 4 | Failed chase falsely latches active | `ClientChaseFlightState` pending/active split; `ChaseFlightStatePacket` is the only thing that sets active | Fixed, **plus one residual defect found — see §4** |
| 5 | Chase survives client disconnect | `Bt3CombatClient.resetConnectionState` on `ClientPlayerNetworkEvent.LoggingIn/LoggingOut` | Confirmed fixed |
| 6 | GUI/seat/disabled-combat cleanup missing | `Bt3CombatClient` screen/seat/death/disabled branches; server handles `CHASE_STOP` before the feature gate (`Bt3CombatPacket.java:140`) | Confirmed fixed |
| 7 | Stale flight snapshot overrides legitimate disable | `network/ChaseFlightOwnership.java`, used at `ChaseFlightSystem.java:181` (`restoreFly`) and `:227` (tick `mustStop`) | Confirmed fixed |
| 8 | Expired/cured player effects resurrect | `mixin/common/EntityEffectSaveMixin.java` at `Entity.saveWithoutId` HEAD; `NpcNegativeEffectPersistence.consume` | Fixed, **plus a performance regression — see §4** |
| 9 | Native NPC reset skips effect restoration | `mixin/compat/{customnpcs,mynpcs}/NpcEffectResetMixin.java`, `@Pseudo`, `reset` HEAD + RETURN | Confirmed fixed |
| 10 | Invitations reset party friendly fire | `PartyManager.applyCreationDefaults`, applied only when `creatingParty` | Confirmed fixed |
| 11 | Cockpit yaw sends late | `client/flight/FlightInputChanges.java`, used in `XenoFlightControls.maybeSend` | Confirmed fixed |
| 12 | Hakai restart leaks glow ownership | `combat/GlowLease.java`; previous lease released in `HakaiChannelSystem.start` before the new one is taken | Confirmed fixed |

External signatures were checked rather than assumed:
`com.dragonminez.common.combat.logic.player.PlayerAttackHelper.isKiWeaponActive` exists in
`libs/dragonminez-2.1.3.jar`; `ChaseFlightSystem.start(player, target)` and `startAutomatic` have
no caller still passing the removed third argument; the string-surgery re-indent of the `try`
block in `Bt3CombatPacket.java` left the braces intact (compile confirms).

## 4. Two residual defects found and fixed in this session

1. **A rejected chase request deadened the held key.**
   `Bt3CombatClient.tickChase` set `chaseInput = requested` *before* calling `tryMove`, so a
   rejection (move cooldown, target closer than 2.5 blocks, out of range, insufficient ki) latched
   the hold: the key stayed "in use" while held but no chase was ever attempted again until it was
   released and pressed. The `active` half of defect 4 had been fixed; this input half had not.
   Now the latch is set only on acceptance, so Space and a dedicated Chase binding retry while
   held and the chase starts the instant the reason clears. W keeps its deliberate
   tap-then-hold arming — its rising-edge gate means it still waits for a new tap.

2. **A reflective class lookup landed on the chunk-save path.**
   `EntityEffectSaveMixin` calls `NpcNegativeEffectPersistence.beforeSave` for *every* entity the
   game serializes, which reached `NpcCounterpartSync.isCustomNpc`. That method called
   `Class.forName` per candidate class per call, constructing a `ClassNotFoundException` on every
   miss when neither CustomNPCs nor My NPCs is installed. The resolution is now done once and
   cached; both NPC mods load long before any world save, so a class missing at first call stays
   missing.

## 5. Validation performed

```
.\gradlew.bat compileJava                                    # BUILD SUCCESSFUL
.\gradlew.bat compileJava test --tests '*FistInputPolicyTest' \
  --tests '*ClientChaseFlightStateTest' --tests '*ChaseFlightOwnershipTest' \
  --tests '*GlowLeaseTest' --tests '*NpcNegativeEffectPersistenceTest' \
  --tests '*PartyInviteDefaultsTest' --tests '*FlightInputChangesTest' \
  --tests '*XenoRushTechniquesTest' --tests '*Bt3ComboBeatTest'
.\gradlew.bat jar jarJar serverJar                           # split from the tests, see §1
```

21 tests across 9 classes, 0 failures, 0 skipped. Artifacts produced and inspected:

- `build/libs/xenopixelsmod-0.2.0-1.21.1.jar` — `version="0.2.0-1.21.1"` in `neoforge.mods.toml`,
  `META-INF/jarjar/modern-ui-3.13.0.1.jar` nested, both mixin configs present.
- `build/libs/xenopixelsmod-Server-0.2.0-1.21.1.jar` — same version and mixin configs, no nested
  ModernUI (client-only, by design).

**No commit, no tag, no publish.** The working tree remains dirty with a large amount of unrelated
work, most of it untracked.

## 6. What is still unverified

- **Nothing was reproduced in game.** No client or server was launched. Mixin application, real
  mining and attack sequences, live DragonMineZ Search/Combat Fly interaction, actual NPC resets,
  reconnects and respawn clones are all untested at runtime.
- The unit tests exercise extracted decision logic and real NBT/party data structures. They are
  not integration coverage of the lifecycles those decisions sit in.
- Audit coverage was unreleased combat/input/chase/flight/effects/Hakai/technique unbinding plus
  released NPC lifecycle, HUD, party and cockpit paths. **Not** reviewed, and not implied correct:
  NPC scripting strategies and the full appearance surface, Sable physics, fleet/missiles/guidance,
  world generation, Create and ComputerCraft integration, permissions, the full forms and
  progression systems, packaging automation, and shaders.

## 6a. Follow-up round: left click, rush and camera

Four in-game reports after the audit landed, all fixed in the same 0.2.0 working tree.

- **Charged fist had stopped working.** `ownsFistInput` folded "is a block under the crosshair"
  into the same predicate that decides whether Xeno fists run, so fists died against terrain and
  `tickCharge` cancelled a charge in progress the moment the view swept across a block. The
  predicate is now split: `FistInputPolicy.fistsActive` (blind to the crosshair) drives fists and
  charge; `FistInputPolicy.ownsAttack` (= `fistsActive && !blockTarget`) stays the native-attack
  question. `FistInputPolicyTest` pins the split.
- **Punches now break blocks, and so does guard.** `Minecraft.startAttack` and
  `Minecraft.continueAttack` both fire `ClientHooks.onClickInput` and return early when it is
  cancelled, so any suppression killed the dig outright. Simply not suppressing on a block target
  is not safe either: DMZ's `shouldUseCombatAttack` yields a block to mining *only* when nothing
  is in front of it (`hasTargetInFrontOfBlock`), so a mob against a wall would fire a DMZ punch
  and an Xeno fist from one click. `MinecraftFistOwnershipMixin` therefore cancels every case —
  keeping DMZ's melee state machine out of it — and reissues the dig through
  `Bt3CombatClient.digWhileSuppressed`, mirroring what vanilla does for a block hit result.
- **Rush was unusable for non-operators.** The only unlock path for the four Xeno strikes was
  gated on an OP permission and DMZ progression cannot grant them, so they were permanently locked
  in single-player without cheats. `XenoRushTechniques.unlockRushTechniques` now grants them to
  everyone; `BT3_RUSH_UNLOCK_BYPASS` stays declared but unused. A rush attempted at a grounded
  target also says "launch them first" instead of failing silently.
- **Rush/chase camera oscillated.** `lockRushView` eased at `rotLerp(0.45f, …)` per tick, ran
  twice per tick during a rush (caller plus `tryRushChain`), and overwrote `yBodyRotO`, which
  removes the renderer's interpolation and snaps the body every tick. It is now a single per-tick
  writer with a deadzone, a per-tick step cap, slower pitch than yaw, and no `yBodyRotO` write.

Re-validated: `compileJava`, `FistInputPolicyTest`/`XenoRushTechniquesTest`/`Bt3ComboBeatTest`/
`ClientChaseFlightStateTest`/`ChaseFlightOwnershipTest` (14 tests, 0 failures), and
`jar jarJar serverJar`. Still no in-game reproduction.

## 6b. Later that day: combat techniques

A second, larger body of work followed on the same date — left-click ownership, rush strikes,
chase, Zanzoken, Shi Shin No Ken, the copy system and the rush camera. It has its own handoff,
including what is still broken and what remains to be done:
[`CLAUDE_TECHNIQUES_HANDOFF_2026-09-06.md`](CLAUDE_TECHNIQUES_HANDOFF_2026-09-06.md).

## 7. If you pick this up next

- The 0.2.0 work is code-complete, compiled, unit-tested and packaged. The obvious next step is
  **in-game validation**, especially: left-click mining with empty hands, attacking with a ki
  weapon active, chase over obstacles and while Search Fly is already on, ki depletion mid-chase,
  reconnecting after a cured effect expires, and a Hakai retarget.
- Do not reset, revert or stash anything. Much of this tree is untracked and unrecoverable if
  deleted. Before removing anything, confirm it exists in a commit.
- `plan.md` at the repo root belongs to the separate Ki-weapons / Iris effort. Leave it alone.
