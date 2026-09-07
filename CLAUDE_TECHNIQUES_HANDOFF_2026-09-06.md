# Handoff — combat techniques, 2026-09-06

> Written by Claude (Opus 5) in `XenoPixelsNetwork_qwen`, branch `new2`.
> Minecraft 1.21.1 / NeoForge 21.1.238 / Java 21, against DragonMineZ 2.1.3.
> Companion to [`CLAUDE_HANDOFF_2026-09-06.md`](CLAUDE_HANDOFF_2026-09-06.md), which covers the
> earlier session recovery and the twelve-defect audit of the same day.

## Approved-plan continuation — current status

This section supersedes the historical unfinished-AI/defaults claims below; the original handoff
is retained as evidence, not rewritten as a claim that its author observed gameplay.

- Original `CloneCombatPolicy` / `CloneCombatBridge` now drive bounded local pursuit, formation
  return, melee, basic ki blasts and supported unlocked DMZ strikes. Xeno rush strikes are
  deliberately excluded: their player launcher/combo prerequisites are not the NPC strike path.
- The prior lifecycle foundation is preserved: actual successful spawn count, current-health
  conservation, surviving-health recall, stationary decoys, authoritative ownership and cleanup.
- Copies read effective owner damage (including the player-only ki damage modifier), share the
  owner's live ki/stamina budget and never enter generic NPC profile/refill/brain setup. Delayed
  strikes reread the clone adapter and the owner's unlocked strike instance. Target changes,
  recall and removal cancel pending casts. Split scaling occurs once in the damage event.
- Positive accepted combat awards at most one mastery point per owner per 20 ticks, capped at
  1000. NBT loading is clamped, large awards cannot overflow, and respawn copying retains mastery.
- Copy rendering now refreshes by elapsed ticks, copies owner armor, delivers queued animation,
  uses copy render UUID / owner skin, evicts its own DMZ cache entries, and isolates the render
  pose stack from failed dependency rendering. These are source repairs, **not a streaking cure**.
- New chase defaults are reliable (`chaseSuccessChance=1.0`), including Dragon Dash's shared roll.
  Explicit saved probabilities and pay-on-attempt remain. Z-Burst now ships unbound; existing
  options are untouched. Bind Z-Burst/Chase in Controls; leave V available for DMZ Stats.
- No third-party AI implementation was copied. See `THIRD_PARTY_NOTICES.md` for verified GPL
  dependency metadata and unresolved distribution/licensing review boundaries.

**Fresh evidence and remaining acceptance:** see
[`wiki/Techniques-Approved-Plan-Validation.md`](wiki/Techniques-Approved-Plan-Validation.md)
for exact commands, modified-file inventory, staged review findings, artifacts and limitations.
The full existing JUnit suite passed **303 tests across 53 classes, zero failures/errors**.
An isolated dedicated server reached `Done` and answered a Minecraft status ping; it emitted
compatibility warnings and was terminated after the smoke check. This is not a clean-shutdown,
player-combat, reconnect, client, or exhaustive mixin-application test. No graphical client was
launched. BSL_v10.1.3.zip is available and selected in the existing Iris config; the required
copies absent/present × shaders off/on matrix remains pending. Pink streaking remains unresolved.

## Read this first (historical handoff)

**Nothing in this document was verified in a running game by me.** Not one behavioural claim. The
client was never launched during this work. Everything below is one of:

| Level | Meaning |
|---|---|
| **Built** | Compiles, and where the logic is pure, covered by unit tests |
| **Read** | Verified against actual source: this repo, `libs/dragonminez-2.1.3.jar`, its decompile under `build/plan-dmz-decompile`, or the NeoForge sources jar |
| **Unverified** | Implemented, never observed working. Most of this document. |
| **Reported broken** | The user saw it fail after the fix shipped |

Current state: `mod_version=0.2.0-1.21.1`, network `PROTOCOL = "59"`, **281 tests across 48 classes,
0 failures**, working tree at 308 entries (mostly pre-existing untracked work, unrelated to this), no commit and no tag.

## What was done

### Input ownership and mining

- Left click serves fists **and** block breaking. `FistInputPolicy` splits two questions that were
  wrongly one: whether Xeno fists run (blind to the crosshair) and whether the *native* attack is
  suppressed (only away from a block). Previously any block under the crosshair killed fists and
  cancelled a charge in progress. **Built.**
- DragonMineZ's melee is stood down at `PlayerAttackHelper.canAttack`, which DMZ consults in
  exactly two places, both in its client `MinecraftMixin`. Returning false there makes both fall
  through *without cancelling*, so Minecraft's own attack lifecycle — and therefore mining, tools
  included — runs untouched. **Read + Built.**
- Guard only suppresses the attack with both hands empty and no ki weapon, matching DMZ's own rule
  for its block. Guard is on right mouse, so right-clicking with any item used to make you unable
  to attack. **Built.**
- Punches read Xeno's own `CHARGE_FIST` binding rather than `options.keyAttack`, so unbinding
  Minecraft Attack no longer kills them. **Built.**

### Rush strikes

- The four strikes unlock for **every** player. `applyUnlockBypass` was the only unlock path and was
  gated behind an OP permission, so in single-player without cheats they were permanently locked.
  **Read + Built.**
- `StrikeAttackCostMixin` gives them Xeno-owned cost and cooldown. DMZ derives strike cost from the
  caster's own power and ignores the declared base cost — the user's capture showed **2924.3 ki**
  per press — and `getActualCooldown` re-reads DMZ's config on every call, so a cooldown set at
  registration never survived. Applies to the four Xeno ids only. **Read + Built.**
- A rush attempted at a grounded target now says "launch them first" instead of failing silently.
  **Built.**

### Chase

- `ChaseRouting` fixes a chase started directly above its target climbing away instead of diving:
  the landing point sits at the target's feet, so the path test clipped into the ground and read a
  clear drop as blocked, and the detour then measured height from the player's already-high
  position and looped. **Built + tested.**
- A chase no longer ends by dropping you to your death. It stops wherever it stops, often high up,
  and gravity came straight back; fall distance is now reset and fall damage waived briefly.
  **Built.**
- Hold-Space and hold-W chase gestures are **off by default** behind `bt3ChaseSpaceGesture` and
  `bt3ChaseWGesture`, kept in the tree rather than deleted. Space is DMZ's flight ascend, so every
  ascent with a lock-on was firing a chase attempt and rolling the success chance. Chase now needs
  its own binding, which ships unbound, with a one-shot notice so it cannot go silently dead.
  **Built.**
- Holding W through a combo beat launches the enemy and dashes after them on Search Fly. It rides
  the launcher's existing `DragonHoming` window, and a homing chase skips both the near-range guard
  and the success roll. No protocol change was needed. **Read + Built.**

### Zanzoken

A timed read on an unbound key: the press opens a window (8 ticks) and costs ki whether or not
anything arrives; a melee hit landing inside it is cancelled, i-frames are granted, a **ring of
copies encircles the attacker**, and you reappear behind them. Uses DMZ's own `zanzoken` sound,
which ships in its jar and nothing had been playing. **Read + Built.**

### Shi Shin No Ken

Divides you into four bodies (`multiFormBodies`) that burst out of yours, travel to their slots,
and hold formation aiming at your locked-on target. You are swapped into the **back slot** in the
same motion. Each body carries a share of your health. Reuniting draws them home along the arc they
came out on. Power division is applied once, on the damage event, so it covers this mod's ten melee
paths and DMZ's own attacks. **Built.**

**Superseded 2026-09-07.** When this was written the copies had no combat AI and nothing awarded
mastery. Both were implemented later the same day by other work in this tree: copies now pursue and
use melee, basic ki and unlocked DragonMineZ strikes via `CloneCombatBridge`, and split combat
awards mastery up to 1000. **Live combat acceptance is still pending** — see the ledger at
`wiki/Techniques-Approved-Plan-Validation.md`.

### Camera

`RushCamera` fades the aim assist to **zero** inside contact range. Tracking a target is angular,
and the rate needed grows without bound as distance shrinks — at half a block a half-block sidestep
is ~45° of yaw, and the pitch term had horizontal distance as its denominator so it ran to ±90°.
An earlier attempt at damping bounded how fast a correction applied but not the error demanding it,
so the assist sat on its cap and never settled. **Built + tested**, including tests that pin the
divergence rather than the happy path.

### Other

Ki guidance follows the crosshair instead of auto-acquiring (an explicit lock-on still homes);
technique slots can actually be unbound, via a Xeno packet — DMZ's `equipOrSwapTechnique` treats an
empty id as "already equipped" and *relocates* the technique to the first vacant slot instead of
clearing it, which is why the old Delete key appeared to do nothing. **Read + Built.**

## Known broken, and not fixed

- **Pink streaking during Multi-Form.** The user confirmed it persists *after* the renderer fix
  (their capture at 14:09 postdates the 13:46 jar). I originally blamed `XenoCloneRenderer`
  mutating the live owner entity mid-frame — that was a real bug and removing it was right, but it
  was **not** the whole cause and I should not have implied it was. Current suspicion, **not
  confirmed**: DMZ emissive geometry under Iris. This repo already carries
  `DmzWeaponsLayerIrisMixin` for exactly that family — `ModRenderTypes.energy2` maps to
  `gbuffers_spidereye`, which shader packs override badly — and the user runs Iris with a pack.
  **Next step is isolation, not another fix:** reproduce with the shader pack off, and with no
  copies spawned. Those two tests separate the causes.
- **Copies had no DMZ hair, skin, name or aura** as of the user's last report. The name and lang
  entry are fixed. The appearance bridge (`NpcFullDmzRenderer.renderPlayerCopy`) is written and
  compiles but has **never been run**.

## Gaps found while writing this — both since closed

Recorded here because the record matters, and both were fixed later on 2026-09-07 by other work in
this tree:

- ~~Nothing awards Multi-Form mastery.~~ Split combat now awards it, rate-limited, up to 1000, and
  it survives save and respawn.
- ~~The clone AI does not exist.~~ `combat/clone/` now holds `CloneCombatBridge`,
  `CloneCombatPolicy`, `CloneSplitState` and `CloneTargetTracker` alongside the original three.
  Copies pursue and fight. Unproven in live combat.

## Still to do

1. **Clone AI.** The user chose porting DMZ's `SagasCombatBrain` (~175 lines) and `CombatContext`
   (~120) into our tree, retyped for `XenoCloneEntity` — they cannot be called directly because
   `CombatContext.self` is hard-typed to `DBSagasEntity`. **The profile bridge comes first and is
   the real work:** `NpcKiAttackDispatcher.fireKiBlast`, `NpcStrikeDispatcher.fire` and
   `NpcMeleeDamage.setAnimation` all take a `LivingEntity` plus an `NpcCombatProfile`, so one must
   be synthesised from the fighter's DMZ stats. It is testable in isolation; the brain is inert
   without it.
   **Licensing:** porting decompiled third-party source into this tree is a redistribution
   question. Record it in `THIRD_PARTY_NOTICES.md` and check DragonMineZ's licence before
   distributing.
2. **Verify or repair copy appearance and aura.** The bridge deliberately sets no `RENDER_CONTEXT`
   and writes no `NATIVE_AURAS` / `AURA_FACTORS` entry, on the theory that those exist to override
   appearance for NPCs with no real data while a copy has real data. Whether the aura arrives
   natively cannot be settled by reading. If it does not, those maps are the next lever.
3. **Isolate the streaking**, as above.
4. **Wire mastery accrual**, or the ramp is dead code.
5. **`chaseSuccessChance` defaults to 0.50**, and the ki is spent *before* the roll. This is the
   likeliest reason chase "doesn't behave like BT3" — half of all standalone chases fail after
   paying. Flagged twice, never changed, because it is a design decision.
6. **`Z_BURST` still defaults to `V`**, which is also DMZ's stats menu (`KeyBinds` registers
   `stats_menu` on keycode 86). Pre-existing, not introduced here.

## Mistakes worth not repeating

- **`@Shadow` does not walk the class hierarchy.** This bit twice in two days, so treat it as the
  house rule: a shadowed member must be declared on the *named target class*, never on an ancestor.
  - `DmzTechniqueUnbindMixin extends Screen` shadowing `getUiWidth`, which lives on the
    `ScaledScreen` ancestor, crashed the client the instant anything opened DMZ's skills menu. It
    now declares the real ancestry, needs no shadow, and moved to the DMZ-gated compat config so a
    future DMZ rename costs a button rather than the game.
  - `PlasmaParticleNoShipCollideMixin` shadowed `x`/`y`/`z`, which are `protected` on vanilla
    `Particle` rather than on Create Propulsion's `PlasmaParticle`. Because that config is
    `required: false` this only logged a warning — so the mixin **never applied and plasma
    particles went on colliding with ships, silently, for as long as it existed**. Found in a
    user's log, not by us. Now `extends Particle` with no shadows.
  - The silent-failure case is the dangerous one: in a `required: true` config this class of bug
    crashes loudly, in a compat config it disables the feature and says nothing.
  - **Audit performed 2026-09-07:** 18 mixin classes use `@Shadow`. Every member resolvable against
    a locally available jar (Create, CustomNPCs, DragonMineZ) was checked with `javap` and resolves
    on its own target. Not checkable locally, so still unverified: the Sable, Create Propulsion and
    Xaero targets.
- **Do not blind string-replace into a large file.** Adding a key drain "next to the HAKAI one"
  inserted it into `tickHakai`, which runs every tick *before* `tickZanzoken`, so every Zanzoken
  press was consumed and discarded before its handler saw it. The user reported "I bind it and it
  does nothing" and they were exactly right.
- **Never mutate a live entity to render something else.** Moving the owner to each copy's position
  for one draw call corrupted DMZ's *deferred* aura pass, which reads the entity again later in the
  frame.
- **Check keybind defaults against DMZ and this mod before choosing one.** `V` was DMZ's stats menu
  *and* our own `Z_BURST`. New bindings now ship unbound.
- **`MinecraftServer.getTickCount()` restarts at zero on every world load**, while the static maps
  in `Bt3CombatEvents` outlive the server in a single-player client. A cooldown stamped late in one
  session sat far ahead of the next session's clock and left Zanzoken reporting "not ready" for as
  long as the previous session had run. Now cleared on server stop and logout, with a staleness
  guard. `COUNTER_UNTIL_TICK` and `GUARD_STUN_UNTIL` had the same latent fault.

## Classes added this session

`combat/FistInputPolicy`, `combat/ZanzokenWindow`, `combat/RushCamera`, `combat/AfterimageGhost`,
`combat/clone/{XenoCloneEntity, XenoCloneSystem, CloneFormation}`, `network/ChaseRouting`,
`network/ChaseFlightOwnership`, `network/packet/{UnbindTechniqueSlotPacket, AfterimageGhostPacket}`,
`client/combat/{XenoCloneRenderer, XenoCloneProxyCleanup, AfterimageGhostRenderer}`,
`mixin/common/StrikeAttackCostMixin`, `mixin/client/PlayerAttackHelperGateMixin`.

Pure decision classes are deliberately Minecraft-free and unit-tested — `FistInputPolicy`,
`ZanzokenWindow`, `RushCamera`, `ChaseRouting`, `ChaseFlightOwnership`, `CloneFormation`. That is
the pattern to follow: this session's bugs were overwhelmingly arithmetic and lifecycle, and those
are the parts a test can hold.
