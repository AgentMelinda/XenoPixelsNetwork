# Handoff — XenoCombat v2, fixes after playtests, and the grab for the other controllers

**Date:** 2026-10-06
**Repository:** `C:\Users\Admin\.grok\worktrees\dragonminez\XenoPixelsNetwork_qwen`
(`C:\XenoPixelsNetwork_qwen` is a symbolic link to it)
**Branch:** `1.21.1`
**HEAD:** `e011e7bec8061d8c2228cfbdc63ee57468749d0d` (nothing committed; all v2 work is in the working tree)

Supersedes `ai/handoff-combat-v2-2026-10-05.md`.

## Current state

- The tree was dirty before any v2 work and those paths are untouched: the LinearReader conversion
  files, `gradle.properties`, `plan.md`, `docs/dimension-protection.md`,
  `scripts/mirror_mynpcs_form_studio.py`, `tools/gen_bt3_hud_atlas.py`,
  `tools/generated/xeno_bt3_hud_atlas.json`, the `tools/dmz-hair-builder-site` submodule.
- `tools/atlas-panels/` is the owner's untracked panel generator. `xeno_extra_specs.py` in it was
  extended with the combat-prompt shapes, as asked.
- No game process was started by this work. The owner runs `gradlew runClient` and reports back.
  Five rounds of work happened on 2026-10-06; each section below is one.
- The owner's profile (`run/options.txt`) still has Create's modifier bindings unbound. Nothing
  under `run/` was edited; the next launch repairs it in code (see round 4).
- `gradle.properties` says version `0.5.11-1.21.1`; artifacts below carry that name.

## Changes

Player-facing description: `docs/combat-v2.md`. Design, with a table of what changed since it was
approved: `docs/superpowers/specs/2026-10-05-combat-v2-design.md`.

### Round 1: the eleven playtest reports

| Report | Cause found | Change |
|---|---|---|
| Indicators look bad | Art cropped from a sheet, with stray and half-transparent pixels | New plates from `tools/atlas-panels` (25 shapes x 4 palettes under `src/generated/resources/.../gui/atlas/combat_prompt_*`); old tool, atlas and `XenoCombatPromptAtlas` deleted |
| Prompt should be at the bottom | Drawn under the crosshair | `CombatPromptOverlay` anchors bottom centre, 78 px up, above the hotbar and action bar |
| Vanish gets hit | v2 sent v1's vanish: 7 blocks, no i-frames | `V2Moves.vanish`: counter, vanish behind within 12 blocks, or blink; 8 i-frame ticks; usable while reeling |
| Combat values and detection | Damage added on top of a full DragonMineZ hit; feet-to-feet reach; Minecraft's hit cooldown dropping beats; no pacing | `V2Damage`, `V2Targeting`, `ReachRules`; `cancel` recovery in `ComboNode`/`ComboMachine` |
| Kicks gone | Heavy beats cost stamina that DragonMineZ zeroes on every melee hit | Heavy is kicks; no beat costs stamina; v2 strikes skip DragonMineZ's per-hit stamina charge |
| Lock-on through blocks | v2 lock key used its own pick and reflection | `LockOnEvent.toggleLock()`; `LockOnCycle.gather` filters by sight |
| Chase needs the aura | v2 mover had no pose | `V2TravelPose` (aura, Search Fly if learned) and the `ChaseRouting` detour in `V2Motion` |
| Rush finisher camera | Reaction and chase applied at strike start, while DragonMineZ holds both fighters | `V2State.STRIKE`: recorded at start, applied by `V2CombatServer.tickStrike` when the strike lock clears |
| Cannot open inventory | Stance drained every E press | E tap = inventory, hold = guard (`TapGesture`) |
| Space does sonic sway | Step was Space + direction | Step is a double-tap of A / D / S; step key unbound; one-time profile migration |
| V is the DMZ menu | Vanish defaulted to V | Vanish on B; one-time profile migration (`V2Keys.migrateLayout`, `XenoClientConfig.v2KeyLayout`) |

Also in round 1: `XenoCooldownHudOverlay` lists the v2 moves and keys while v2 runs; and four
things found in review and fixed (stopping the server mid-chase saved the player weightless with
a borrowed aura and flight on; grabs ignored the server's PvP switch; a grab or rush strike could
launch a target whose damage had been refused; a blink with no target went through walls).

### Round 2: the view during the four rush strikes

- Cause: Xeno Rush Left, Right, Breaker and Finisher run in DragonMineZ's `StrikeAttackHandler`.
  It teleports the attacker to `target - targetLook * 1.3` (the side the target is not facing),
  calls `ServerPlayer.lookAt` on the target's eyes, and calls it again in `faceStrikeTarget` on
  every tick. Each call snaps the client's view. Against a locked target,
  `LockOnEvent.onRenderTick` blends the same view 15 % per frame toward the middle of the
  target's body. At 1.3 blocks those aim points are about 29 degrees of pitch apart. The combos
  (`ComboRouteMachine`) never call `lookAt`; lock-on is their only camera.
- Change: `mixin/compat/dmz/StrikeAttackRushViewMixin` (registered in
  `xenopixelsmod.compat.mixins.json`, `require = 0`) hooks `requestStrike` (read only),
  `teleportToTargetFront`, `teleportToPartFront` and `faceStrikeTarget`.
  `combat/technique/XenoRushStrikeView` lands the attacker on their own side of the target
  (`RushStrikeArrival`, pure) and replaces the facing with a body turn. If the facing hook never
  fires for a tracked strike, a warning starting `Xeno rush strike view:` is logged once.
- `XenoRushTechniqueEvents`: under the legacy controller the automatic chase after a breaker or
  finisher starts when the strike lock clears, not when the strike fires. **This changes v1.**
  The fire-time knockback in v1 is unchanged.

### Round 3: "everything should only work when a DMZ lock-on is used on a target"

- `client/combat/v2/CombatStance`: the stance is now one rule, a live DragonMineZ lock (plus
  empty hands). The "recently engaged" timer and the hostile-mob clause are gone.
- `client/combat/v2/V2InputLayer`: every move is sent at the locked target and only while there
  is one. No look-target fallback. Vanish, step and Dragon Dash need the lock too.
- `Bt3CombatClient.fistsActive`: under v2, Xeno claims left click only while locked (or while
  held in a grab). Unlocked, the click is DragonMineZ's own punch.
- `combat/v2/LockRules` (pure) + `V2Lock`: `V2CombatServer.handleInput` refuses any move whose
  named target the fighter could not have locked (Ki Sense level above 0; within
  `NpcKiAim.LOCK_RANGE + 5 * level`, plus 25 for an upgraded android, plus 4 slack). The lock is
  client-side and DragonMineZ does not sync it, so this is plausibility, not proof.
- No auto-targeting: `V2Targeting.sweep`, `ReachRules.score`, the `strikeAutoTarget` setting and
  the grab's nearest-target fallback were removed. A swing lands on the lock or on nobody.
- A super counter only answers the locked target. `CombatV2StatePacket` gained
  `counterAttackerId` so the prompt is shown only then.
- Rush strikes: the round-2 view handling, and v2's deferred reaction and chase, apply only when
  the strike was thrown at the caster's lock. The server reads that from DragonMineZ's own
  `StrikeAttackC2S`, whose id is the locked entity or -1. The client-side easing for unlocked
  players that round 2 added (`RushStrikeViewPacket`, `RushStrikeViewCamera`,
  `ClientRushStrikeView`) was deleted: with a lock, lock-on is the camera; without one the strike
  is stock DragonMineZ.
- Prompt and HUD: `CombatPromptOverlay` draws nothing unlocked; the COMBAT strip leads with a
  Lock chip and greys the rest until locked.
- Exceptions, both things the fighter does not start: breaking out of a grab, stopping a chase.

### Round 4: client crash, `run/crash-reports/crash-2026-10-06_13.52.33-client.txt`

- `IllegalStateException: Encountered GL error off-thread @ Render: 65539: Invalid key -1`, in
  `Create AllKeys.shiftDown -> AllKeys.isKeyDown -> InputConstants.isKeyDown -> glfwGetKey`,
  called from `FilterItem.appendHoverText` on the creative search index's worker thread.
- Cause: this work. `XenoKeybinds.clean` (run automatically on 2026-10-05 21:56, backup
  `run/config/xenopixelsmod/keybind-backups/keybinds-20261005-215656.json`) unbound
  `create.keyinfo.shift_modifier`, `ctrl_modifier`, `alt_modifier`, `toolmenu` and `toolbelt`.
  Create 6.0.10 passes the mapping's key code straight to GLFW (checked with `javap -c` on
  `run/mods/create-1.21.1-6.0.10.jar`); unbound, that is -1.
- Fix, three layers:
  1. `KeybindRules.heldModifier` / `mayUnbind`: a binding whose default key is Shift, Ctrl, Alt
     or Super is never unbound. Create's five default to 340, 341 or 342 (same `javap`).
  2. `XenoKeybinds.repairOnce` + `XenoKeybinds.Repair`: once per profile, on the first tick with
     a player, in any combat mode, re-binds such bindings from the newest backup (or their
     default). Flag: `XenoClientConfig.keybindRepair`.
  3. `mixin/client/InputConstantsUnboundKeyMixin` (client list of `xenopixelsmod.mixins.json`,
     `require = 0`): `InputConstants.isKeyDown` returns false for a negative key instead of
     asking GLFW. Same answer GLFW gives, without the error.

### Round 5: "add grab throw and indicators to legacy/bt3_manual"

The grab was the one v2 move with no v1 counterpart. It is now shared: the same server code, the
same rules, the same lock-on requirement, reached from the other controllers' own keys.

- **Server.** `V2CombatGate.decideGrabOnly`: under a controller other than v2, exactly one input
  is admitted, `GRAB` (start, aim the throw, break out), under its own permission
  `XenoPermissions.COMBAT_GRAB_USE` (`combat.grab.use`, everyone) and only while
  `V2Config.grabEnabled && grabOutsideV2` (new, default `true`). `V2CombatServer.handleInput`
  picks the gate by mode and, outside v2, calls `V2Grab.start` directly instead of going through
  the combo graph. Breaking out of a grab needs no permission under any controller.
- **Ticking.** `V2CombatServer.onServerTick` used to sweep every fighter whenever the mode was not
  v2. It now ticks fighters under every controller and sweeps when the mode it last ticked under
  differs from the current one (`tickedMode`). `blocksLegacyAction`, the i-frame check and the
  "hit during startup cancels the grab" check run under every controller; draining the legacy
  counter window and opening the v2 one stay v2-only.
- **What the client is told.** `CombatV2StatePacket` is now sent under every controller: on
  login, from `clear` (every mode switch), after `/xenocombat reload` (`resyncAll`), and when a
  fighter is dropped by death or a dimension change. `grabReady` in it now means
  `grabAvailable()` and ready. No field changed.
- **Client.** `V2InputLayer.tickSharedGrab(mc, guardUp, attackDown)`, called from
  `Bt3CombatClient`'s legacy tick after `tickGuard` with that controller's own keys: guard +
  punch sends `GRAB` at the locked target; punch alone while `GRABBED` breaks out; the held
  direction aims the throw. While `V2ClientState.inGrab()` the legacy tick reads no other combat
  key (`yieldToGrab`: guard dropped, charge and chase reset, clicks drained) and
  `suppressesNativeAttack` is true, so under `bt3_manual` DragonMineZ's own punch does not fire.
  Mining with the punch key and placing with the guard key are not taken away during a grab:
  the dig is still reissued, and only the guard state is dropped, not the use key.
- **The chord is forgiving** (`GrabRules.chord`, pure): two mouse buttons pressed "together"
  land on different ticks about as often as not, in either order. A punch into a raised guard
  grabs, and so does a guard raised within `CHORD_TICKS` (3) of the punch going down. A guard
  raised into a punch key held longer than that is only a guard, so holding left mouse for a
  legacy string and then blocking still blocks. A punch key held past the end of a grab goes
  straight back to punching; nothing has to be released first.
- **Chase after a throw.** `V2Grab.throwVictim` also opens the legacy `DragonHoming` window, and
  the legacy client arms `DragonHomingClient` when the server's window opens on the target it is
  still locked on. One tap of forward then goes down the existing legacy path (`CHASE_DASH`).
- **Indicators.** `CombatPromptOverlay` draws under the other controllers too, grab prompts only,
  with their key labels: `RMB+LMB Grab`, `Throw` + directions, `W Chase`, `LMB Break free` /
  `Grabbed`. `XenoCooldownHudOverlay.buildChips` adds a Grab chip after Guard (nine chips; the
  BT3 HUD rail is four wide). `/xenocombat status` reports `grab=every controller | v2 only | off`.
- **Found while doing it, fixed in every mode** (all in `V2Grab` / `V2CombatServer` unless noted):
  1. A fighter killed while held was dropped without their client being told, so it stayed
     `GRABBED`. Under v2 that froze left click until the next lock; with the legacy tick now
     yielding to a grab it would have frozen every combat key. The server now sends the idle
     state on death and dimension change, and `V2ClientState` gives up on a grab state after
     `GrabRules.MAX_STATE_TICKS` (200) regardless.
  2. The defender's guard came down after the connect hit, so the hit that "goes through a block"
     was a blocked hit. It now comes down first, and goes back up if the hit is refused.
  3. The grab played no pose. `V2Strikes.begin` returned to `V2Grab.start` before the pose was
     sent, so the step-in pose the docs claimed was never shown. The grab now sends its node's
     pose (`STEP_IN_DASH` outside v2) and the throw a borrowed strike pose (`V2Grab.throwPose`).
  4. Whoever is held, player or mob, could keep hitting the fighter holding them. A blow struck
     directly by a held entity is now cancelled in `LivingIncomingDamageEvent`.
  5. A defender's cinematic rush, rush/lift combo route and legacy chase are stopped on connect.
  6. Riders, mounts and seated pilots can no longer be grabbed, and nobody grabs from a seat.
  7. The grab cannot start during a cinematic rush or a Hakai channel.
  8. v2 client: a guard key still held when a grab ends is re-sent, so the guard the fighter
     believes in exists on the server again (`V2InputLayer.tickGuard`).
- Design choices worth knowing: the chord was free (guard up + empty hands already suppressed
  left click under both controllers); a counter or rush plate was not added to the other
  controllers because they already have indicators for those; the availability of the grab is
  not a new wire field, so a server with it turned off shows the Grab chip greyed, like any other
  disabled feature.

### Round 6: "R should not reload the chunks, in any combat mode"

- Request: R rebuilds the chunks; move whatever does it to Page Up.
- What was found: the only binding in the pack whose default is R and which rebuilds chunks is
  Iris's `iris.keybind.reload`. Nothing in XenoPixels or DragonMineZ reloads the renderer on R
  (searched both). **Not confirmed as the cause:** in the owner's profile (`run/options.txt`,
  written 14:58) that binding already read "unbound", and the log shows no shader reload on a
  key press. So either the profile differed while they played, or something else does it.
- Change: `XenoKeybinds.moveShaderReload` (rule: `KeybindRules.moveShaderReload`, pure), run from
  the existing first-tick hook in every mode. On R it is moved to Page Up on every launch; unbound
  it is put on Page Up once per profile (`XenoClientConfig.shaderReloadKey`). A chat line says so.
- The addon-build check was not re-run for this round; nothing under `api/**` changed.

### Compatibility

- `api/**`: unchanged since the first pass (`GrabEvent`).
- Wire: protocol 103. No packet added since 102. `CombatV2StatePacket` gained a trailing
  `counterAttackerId`; `V2Input.VANISH` and `V2State.STRIKE` appended; the branch-mask byte
  carries two 2-bit labels; the target id in `CombatV2InputPacket` now means the sender's lock.
  Round 5 changed no packet shape: `GRAB` is now accepted outside v2, and the state packet is
  sent under every controller.
- DragonMineZ: four hooks into `StrikeAttackHandler`, inert for anything but a Xeno rush strike
  at the caster's lock. Signatures checked with `javap -p -s` against `libs/dragonminez-2.1.3.jar`
  (sha256 `5a6e33ef5b992e64fccccaed895b105318b2ce1ab8039d8193107d40d203b581`);
  `RushStrikeViewTest` reads the same class with ASM so a changed jar fails the suite.
- Minecraft: one hook into `InputConstants.isKeyDown`.
- `config/xenopixelsmod-combat-v2.json`: `version` 2; `strikeAutoTarget` is no longer read;
  `grabOutsideV2` added (default `true`, written into an existing file on the next load).
- Permissions: `combat.grab.use` added (everyone by default).
- Default mode is still `legacy`. **Legacy is no longer byte-for-byte v1**: it has the grab
  unless `grabOutsideV2` is set to `false`.

### Reaches v1 too

- `ComboRouteMachine` clears the hit cooldown before each route hit (rush and lift combos were
  dropping every second hit).
- `LockOnCycle` sight filter (no change while "lock through blocks" is on, the default).
- Legacy automatic chase after a breaker or finisher starts at strike end.
- The rush strike view at a locked target, the keybind rule, its repair and the unbound-key
  guard apply in every mode.
- Round 5, all of it: the grab under `legacy` and `bt3_manual`, its prompts and chip, the legacy
  tick standing down during a grab, the legacy packet handler refusing actions from a fighter in
  a grab, and the fighter ticker and state packets running under every controller.

### Not done

- No grab or throw clips, and no "held" pose for the victim. The poses sent are stand-ins.
- No gamepad binding for the shared grab; `XenoPadInput` was not touched.
- No client toggle for the Grab chip or the grab prompts, and no `/xenoset` key for
  `grabOutsideV2`: it is the config file plus `/xenocombat reload`.
- The other controllers got the grab's prompts only. Their counter and rush follow-up keep the
  indicators they had; nothing was restyled.
- No command to move the prompt. Its position is `BOTTOM_OFFSET` in `CombatPromptOverlay`.
- v1's rush strikes still apply their knockback at strike start.
- A player who is the target of a rush strike is still turned by the server every tick.
- The keybind cleanup still unbinds other mods' ordinary action keys, as designed. Whether any of
  those mods misbehaves with its key unbound is not known; the guard removes the crash, not a
  mod's own assumption that the key exists.

## Verified

Build and unit tests only, on the final tree:

- `./gradlew test build jarJar serverJar -PofflineMcMeta --offline` → BUILD SUCCESSFUL, exit 0
  (2026-10-06, after round 6). 512 suites, 3363 tests, 0 failures, 0 errors, 0 skipped.
  v2-related: `ComboGraphTest` 33, `V2RulesTest` 43 (10 added in round 5: the grab-only gate, the
  config default, the committed states, which throws open a chase, the chord in both orders and
  what it must not catch, the poses, the give-up rule and its tie to the config clamps),
  `TapGestureTest` 11, `CombatPromptPlatesTest` 8, `RushStrikeViewTest` 10,
  `CombatV2ProtocolTest` 4, `KeybindRulesTest` 14, `UnboundKeyGuardTest` 3.
- `./gradlew buildApiExampleAddon -PofflineMcMeta --offline` → BUILD SUCCESSFUL, exit 0.
- `build/libs/xenopixelsmod-0.5.11-1.21.1.jar` — 69,496,228 bytes,
  sha256 `9c8375c783cf841f5303845821fbd2517c21423cab6f20b17a7dc15fa5d31a51`.
- `build/libs/xenopixelsmod-Server-0.5.11-1.21.1.jar` — 45,349,379 bytes,
  sha256 `ef54a9559bb3a9de9f55854260bce77d227230f6c54e5dc888d57a0d4087c33c`.
  `META-INF/jarjar/` holds only `metadata.json` and `aaa_particles-neoforge-1.21.1-2.3.1.jar`.
- From the owner's run of 2026-10-06 12:10 to 13:52 (`run/logs/latest.log`): the round-2 build
  loaded and registered 80 packet types under protocol 103, and ran for 102 minutes before the
  round-4 crash. No `Xeno rush strike view:` warning was logged in it; whether a rush strike was
  cast in that run is not known, so that is not evidence the hooks applied.
- The crash's cause was confirmed against artifacts, not inferred: the profile's `options.txt`
  shows the five Create bindings unbound, the backup shows what they were, and Create's bytecode
  shows the unguarded GLFW call.

## Not verified

- Rounds 3, 4 and 5 have not been run at all. Rounds 1 and 2 were run by the owner, who reported
  problems but not which parts worked.
- Round 5 in particular is unit tests on its pure rules and nothing else. Not one grab has been
  thrown under `legacy` or `bt3_manual`. Unseen: the chord, the throw directions, the chase tap,
  the break-out, the guard coming back, the plates and the ninth chip, the poses, and whether a
  held mob really stops hitting. `docs/combat-v2.md` items 17 and 18 list them.
- That the changes to how the grab behaves in v2 (round 5, "found while doing it") are
  improvements in play and not only on paper.
- That the crash is gone. The fix follows from the cause, but no client has been started on it.
  On the next launch look for the chat line "put back N modifier key binding(s)" (expected: 5)
  and type in the creative search.
- That `InputConstantsUnboundKeyMixin` and `StrikeAttackRushViewMixin` apply at runtime. Unit
  tests prove the target methods exist with those descriptors; they do not load the game.
- That with nothing locked, left click really falls through to DragonMineZ's punch.
- These rest on reading DragonMineZ 2.1.3's decompiled source: that setting the amount in
  `DMZEvent.DamageModifyEvent` at highest priority gives melee damage times the scale; that
  writing `dmz_first_hit=false` and `dmz_swing_stamina_ratio=1` for the length of the hit skips
  the stamina charge; that zeroing `dmz_server_speed` stops the momentum knockback; that
  `Status.isStrikeLocked()` clears on the tick the strike ends.
- Dedicated-server start with the new classes (not started, to avoid touching the dev world while
  LinearReader conversion changes are uncommitted in the tree).
- Tick cost. No profiler capture exists; no TPS claim.
- Gamepad: no v2 rows in `client/pad` or `client/pad2`.

## Next steps

1. `gradlew runClient`. First: the repair chat line, Create's modifier bindings in Controls, and
   the creative search. Then walk "Not verified" in `docs/combat-v2.md` top to bottom.
   For round 5, in the default `legacy` mode: lock on something, stand next to it, hold right
   mouse and click left. `/xenocombat status` should say `grab=every controller`.
2. If anything of another mod's still misbehaves with its key unbound, add its translation-key
   prefix to `keybindKeepPrefixes`, or run `/xenokeybind restore`.
3. If the prompt overlaps a HUD element, change `BOTTOM_OFFSET`.
4. Tune from play: `strikeRange`, the kick reactions, `vanishIFrameTicks` / `vanishCooldownTicks`,
   `TapGesture.DOUBLE_TAP_TICKS`.
5. Decide whether v1's rush strike knockback should also wait for the end of the strike, and
   whether the target of a rush strike should be left to their own camera as the attacker is.
6. Start a dedicated server and read a fresh `run/logs/latest.log` for protocol 103 and no
   class-loading errors.
7. Still open from the design: grab and throw clips, local pose prediction, Just Guard, stamina
   break, gamepad rows, a profiler comparison.
8. If the grab should not be in the default controller after all: `"grabOutsideV2": false` in
   `config/xenopixelsmod-combat-v2.json`, then `/xenocombat reload`. If nine chips make the BT3
   HUD rail too tall, the Grab chip is one block in `XenoCooldownHudOverlay.buildChips`.
