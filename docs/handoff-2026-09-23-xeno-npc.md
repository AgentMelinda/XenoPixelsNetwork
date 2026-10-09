# Handoff — Xeno NPC: Bank, Jobs, Pather, Controlify rewrite, social clips

**Date:** 2026-09-23
**Repository:** `C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen`
**Branch:** `1.21.1`
**HEAD:** `53a025e45a4fecc2aeb1a2d062db550f88bbf571`

> Read this with `ai/README.md` step 5 in mind and verify its claims against the tree. Everything
> below was re-checked against the working copy immediately before writing; nothing is recalled.

---

## Current state

**HEAD is unchanged from before this session.** Nothing here is committed. All of the work is in
the working tree as **643 dirty paths** (`git status --short | wc -l`, counting this document). That is the pre-existing
condition of this worktree, not something this session created — but it means the usual warning
applies with force: **never `git add -A`, `git reset --hard`, or blanket checkout.** Untracked work
here is unrecoverable.

**Three `java` processes were running at the time of writing** — PIDs 16940, 69056, 76216, started
18:28. At least one is a Minecraft client. They hold classes loaded **before** the latest rebuild,
so per `AGENTS.md` §Handoffs this tree is *not* to be described as cleanly validated at runtime.
Anything you test in that client is testing old code until it is restarted.

Because a client holds `run/`'s world lock, every dedicated-server check below was run in an
isolated directory under the session scratchpad, never in `run/`.

**Dependencies** (unchanged): NeoForge 21.1.248, MC 1.21.1, Java 21, DragonMineZ 2.1.3
(`libs/dragonminez-2.1.3.jar`), Controlify pinned in `gradle.properties` as
`controlify_project_id=DOUdJVEm`, `controlify_version_id=RqsNKKLK`.

---

## Changes

No commits. Reviewed as one unit per feature.

### Protocol

`ModNetwork.PROTOCOL` went **85 → 86**, once, for the Bank packets (`XenoNpcBankPacket`,
`SyncBanksPacket`). Nothing since has changed wire shape: the Pather, Guard, social clips and
stay-home flags all ride the existing `XenoNpcSavePacket` `CompoundTag` whitelist.

### Bank role

New: `npc/bank/` — `BankDefinition`, `BankTab`, `BankAccount`, `Banks`, `NpcBankService`,
`NpcBankMoney`, `XenoNpcBankMenu`, `ModMenus`; `client/npc/bank/XenoNpcBankScreen`,
`ClientBanks`; `network/packet/XenoNpcBankPacket`, `SyncBanksPacket`.

- Bank definitions are world-store content under the `BANKS` category, following the same shape as
  `TransportNetworks` and the faction store; the NPC holds `bankId`. Player vaults are per-player on
  `XenoPlayerData`, bounded by `MAX_BANK_ACCOUNTS`.
- **The mod's first `MenuType`.** Registered via `IMenuTypeExtension.create`, bound in
  `ClientModEvents.registerMenuScreens`.
- MMO Econ is reachable from exactly one file (`NpcBankMoney`); a test fails if any other bank file
  names `MmoEconBridge`. With no economy installed the money controls are **absent, not disabled**.
- `MmoEconBridge.deposit` was added, wrapping `addBalance(UUID, long)` — a signature already
  recorded in `docs/shops-and-plots.md` from a `javap` run against MMO Econ 1.1.0.

### Jobs axis

New: `npc/XenoNpcJob` (12 entries, their visible order, id-keyed never ordinal-keyed),
`npc/job/NpcBardJob`, `npc/job/NpcGuardJob`, `npc/job/NpcSocialBehaviour`.

`implemented()` returns true for **`NONE`, `BARD`, `GUARD` only**. The other nine are names with
javadoc reservations; a test enforces that every unimplemented one carries one.

### Pather

New: `npc/path/NpcPath`, `npc/path/NpcPathWalker`, `item/custom/XenoNpcPathToolItem`,
`npc/StayHomeStrollGoal`, plus a hand-authored 16×16 item icon.

- Routes are **per-NPC** on the profile — a deliberate counter-example to the §0 ownership rule,
  recorded in `docs/xeno-npc-schema.md`.
- The leash yields while patrolling, and resumes when a route is deleted or a target is acquired.
- `XenoNpcEntity.registerGoals` now uses `StayHomeStrollGoal` instead of a plain
  `WaterAvoidingRandomStrollGoal`. It **gates** rather than removes, because `registerGoals` runs
  once and a goal omitted there could never come back.

### Controlify integration rewrite

New package `client/pad2/` — `PadLayout` (the BT3 layout as a table, no Controlify types in it),
`PadInput`, `PadBinds`, `PadRadialSlots`, `PadWideRadial`, `PadKiMenu`, `PadTextIcon`, `PadVanish`.
New mixin `mixin/compat/controlify/ControlifyWideRadialMixin`.

The old `client/pad/` is **intact and reachable**; `XenoClientConfig.padRewrite` (default `true`)
picks which registers, decided once in pre-init and recorded via `XenoPadInput.useRewrite` so later
reads route to whichever package actually holds the bindings.

Full rationale in **`docs/controlify-integration-rewrite.md`**, including the measured
key-reading table that explains every gamepad gap.

### Social clips

New: five `.animation.json` files under
`src/main/resources/assets/xenopixelsmod/animations/social/` — `wave`, `hi_wave`, `nod`, `spin`,
`idle_shift` — seeded into `XenoClipLibrary` on load when absent.

Authored against the schema read out of `XenoAnimClip.toGeckoJson` and the bone list in
`XenoRig.COMBAT` (`root, waist, head, right_arm, left_arm, right_leg, left_leg`).

### Documentation

- `docs/controlify-integration-rewrite.md` — new
- `docs/xeno-npc-schema.md` — §0 gains bank and patrol-route worked examples; new **§0.2** on
  default asymmetry; `SyncBanksPacket` added to the sync contract table

---

## Verified

Exact commands, in this worktree:

```
gradlew.bat test -PofflineMcMeta              → 2176 tests, 0 failures, 0 errors
gradlew.bat build jarJar serverJar -PofflineMcMeta   → BUILD SUCCESSFUL
gradlew.bat buildApiExampleAddon -PofflineMcMeta     → BUILD SUCCESSFUL
```

Artifacts in `build/libs/`:

| Jar | Bytes |
|---|---|
| `xenopixelsmod-0.3.7-1.21.1.jar` | 38,893,164 |
| `xenopixelsmod-Server-0.3.7-1.21.1.jar` | 14,745,937 |
| `xenopixelsmod-0.3.7-XenoNpc_Alpha-1.21.1.jar` | 38,502,164 |
| `xenopixelsmod-0.3.7-1.21.1-sources.jar` | 13,004,023 |

Server jar SHA-256 `FB364B108A867A13248095102D58F829D29428530DBCCBBA42D30769B5C9566A`, and
`unzip -l | grep -c META-INF/jarjar/` → **0**, as `AGENTS.md` requires.

**Dedicated-server starts**, each in an isolated run directory, each read from its own log:

| After | Observed |
|---|---|
| Bank | `Done`, `ModNetwork: registered 59 packet types (protocol 86)`, `Loaded 8 native NPC role definitions` |
| Controlify rewrite | `Done`, **no `pad2` class named anywhere in the log**, 0 mixin apply failures |
| Ki menu / `PadKiMenu` | `Done (18.201s)`, same |
| Pather | `Done (16.593s)`, no registry or model complaints for `xeno_npc_path_tool` |
| Social clips | `Done (14.876s)`, then `Animation library: 5 clip(s)` |

The clip line is meaningful because **the library directory was deleted before that run** — it
proves the seeding path, not leftover files.

One measured performance number, taken because the plan required a number rather than an assertion:

```
respawn per-tick cost, 4096 entries, none due: before 39.3 us/tick, after 0.0455 us/tick
```

Read it honestly: 39.3 µs is **0.08 % of a 50 ms tick**. This was never a visible TPS problem and
fixing it is not a visible TPS win. It is a microbenchmark of the handler's own work on the real
`XenoNpcRespawnData`, not a server profile, and it says nothing about MSPT.

---

## Not verified

**No client has run any of this.** The client that was running predates every change here. So every
runtime behaviour below is unobserved:

- Bank: opening a vault, items surviving a relog, a locked slot refusing a drag or shift-click, an
  item cost actually being taken, two tellers sharing one bank, a deleted bank falling back to speech
- Pather: an NPC visibly walking a route, `LOOP` closing the circuit, a wedged NPC advancing after
  the 20-second timeout, the leash genuinely yielding mid-patrol, Stay Home holding the spot
- Guard: ignoring a cow with Attack Animals off while still answering a zombie
- Controlify: a radial drawing more than 8 entries evenly, slot 9+ firing, Controlify's **own**
  config screen still editing its 8, startup with Controlify absent, `padRewrite=false` restoring
  the old package. Note the server evidence is an *absence* of `pad2` in the log, which is good
  evidence of correct side-safety but is not the same as proving no class loaded
- Ki bars: LT/RT raising the technique bar, a charge winding up, release firing
- Social: an NPC waving on approach, `spin` turning without moving off its block
- **`particle_effects` in the `nod` clip.** It parses and is in the studio's own export format, but
  a particle has not been seen on screen. If it does not resolve, the fallback is a server-side
  `ServerLevel.sendParticles` at the trigger.

Also unverified: multiplayer of any kind, and any platform other than this Windows dev machine.

### A known-inert feature, stated plainly

**The technique-slot entries in the widened radial do nothing.** `KeyBinds.isChordDown` is
`isBarModifierActive(...)` → `KeyModifier.isActive()` → `Screen.hasAltDown()` **and**
`isPhysicallyDown(...)` → `InputConstants.isKeyDown(window, key)` — both raw GLFW reads. Controlify's
key emulation sets `isDown` and increments `clickCount`; it never touches physical keyboard state.
No emulation can reach that chord. `PadKiMenu` goes around it with DMZ's own packets
(`SelectTechniqueSlotC2S`, `TechniqueChargeC2S.start/setHolding`), which is also unobserved.

---

## Next steps

Ordered, and each preserves the current tree.

1. **Verify in game.** Restart the client first — it is running pre-rebuild classes. This is the
   largest real risk in the handoff: a great deal compiles and is tested, and almost none of it has
   been watched. The per-feature lists above are the checklists.
2. **Scenes — not started.** There is no `npc/scene/` package and no `SCENES` store category; the
   only scene work present is `XenoNpcSpeech.say`, added for it. `ADVANCED_SCENES` is still
   `unavailableListRows("Scenes")`. The agreed design: `SCENES("scenes", false)` **appended after
   `PLAYERDATA`**, a transient runtime clock on `ServerTickEvent.Post`, `/xenoscene
   start|pause|reset|time|list|addstep`, steps of kind `SAY` (via `XenoNpcSpeech.say`) and `ANIM`
   (via `XenoAnimApi.playClip`), per-NPC Enabled/Disabled entries, and the editor page.
3. **Roles still missing against their list:** Mailman, Dialog, Follower. (Ours today: humanoid,
   creature, trader, guard, companion, quest, transporter, bank. The Pokémon-conditional roles are
   out of scope.)
4. **Jobs still reserved:** Healer, Item Giver, Follower, Spawner, Conversation, Chunk Loader,
   Puppet, Builder, Farmer. Each javadoc says what unblocks it; Farmer's says "a work area, which
   the Pather defines" — that is now unblocked.
5. **Store categories with no reader:** `CLONES`, `QUESTS`, `LINKED`, `SPAWNS`, `RECIPES`. Each has
   a documented reservation. `PLAYERDATA` is permanently empty by design.
6. **Editor placeholders remaining:** `ADVANCED_LINE_SELECTOR`, `ADVANCED_SCENES`, `GLOBAL_QUESTS`,
   `GLOBAL_RECIPES`, `GLOBAL_LINKED`, plus disabled rows on Display (textures, cape, overlay),
   Stats (melee/ranged props, regen) and AI.

### Traps worth not rediscovering

- **`PROFILE_SHAPE`.** A whitelisted key that is written *conditionally* must also be populated in
  `XenoNpcSavePolicy.buildShape()`, or the save passes the whitelist and is then rejected at the
  type check. This bit three times — `NpcLines`, `Trades`, `BardSound`. The test now reads the
  policy's own shape rather than a second hand-maintained copy, so one edit covers both.
- **Store category ordinals go on the wire** (`SyncNpcStoreIndexPacket`, `XenoNpcStoreWritePacket`).
  **Append only** — inserting silently repoints every category after it.
- **`NpcCombatProfile.fromTag` short-circuits an empty tag** to fresh defaults, so "no profile at
  all" and "an old profile missing this key" are different cases. A flag that changes autonomous
  behaviour should read with `tag.getBoolean` so existing worlds do not change underneath their
  owner. Written up as `docs/xeno-npc-schema.md` §0.2.
- **Do not pin protocol literals in tests.** Three tests asserted `PROTOCOL = "85"` and all three
  broke on one unrelated bump; they now use the `ProtocolVersion` floor helper, which exists for
  exactly this and documents why a floor beats a pin.
- **Controlify internals are version-pinned.** `RadialItems`, `RadialMenuScreen$RadialItem` and
  `InGameInputHandler` are outside `dev.isxander.controlify.api`. A Controlify bump means re-running
  `javap` before trusting anything in `PadWideRadial` or the mixin.
- **Clip bones come from `XenoRig.COMBAT`, not from DragonMineZ's `races/combat.animation.json`.**
  `waist` is in both; `bone3` and the `*_hand_item` bones are DMZ's only, and a clip using one loads
  fine and animates nothing. `SocialClipsTest` fails on any bone outside the rig.
