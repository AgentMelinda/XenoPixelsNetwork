# Codex handoff — XenoPixels session work (2026-08-30)

Repo: this worktree, branch `new2`, last commit `a8051e9` (2026-08-27). **Everything below is
uncommitted working-tree state** from one Claude Code session on 2026-08-30 — nothing in this
list has been `git commit`ed. Run `git status` / `git diff` first to see the real current state;
this file is a summary, not a source of truth.

**Verify everything in code before trusting this file.** Every claim below was checked one of
three ways, and each section says which: (a) **compiled** — `./gradlew compileJava --offline`
ran clean after the change, (b) **decompiled** — the external `libs/dragonminez-2.1.3.jar` and/or
`run/mods/CustomNPCs-Unofficial-*.jar` were decompiled with Vineflower or inspected with `javap`
to confirm a method/field/behavior genuinely exists before code was written against it, (c)
**not yet confirmed in-game** — compiles, but no `runClient`/live-server pass has verified the
actual runtime behavior. Nothing in this session was verified against a running client except
the two items explicitly marked "confirmed live" below. Do not describe anything as "working" to
the user beyond what these tags say.

This file is scoped to this session's changes only. `CLAUDE_CNPC_HANDOFF.md` (2026-08-30, earlier
in the day) covers the CNPC/DMZ background/constraints this session built on top of — read that
first if you need the wider system context (network protocol history, `NpcCombatProfile`
rationale, GUI tab wiring, etc.). Do not treat it as current for anything this file supersedes.

---

## 1. Humanoid ("Steve") NPC hair rotation — compiled, not yet confirmed in-game

**Root cause found (decompiled):** `NpcHairClient` (deleted this session) hooked
`RenderLivingEvent.Post`. By the time that event fires, vanilla `LivingEntityRenderer.render()`
has already `popPose()`d — body yaw, the `-1,-1,1` mirror scale, and every per-part transform are
gone. It only added a flat `translate(0, eyeHeight, 0)`, so DMZ's `HairRenderer` (which applies
its strand rotations relative to whatever pose stack it's handed, never reading entity yaw/pitch
itself — confirmed by decompiling `com/dragonminez/client/render/hair/HairRenderer.class`) drew
in a fixed world orientation.

**Fix, part 1 — real render layer instead of a post-render event:**
- New `src/main/java/net/bullettrain/xenopixelsmod/client/compat/npc/NpcHumanoidHairLayer.java`
  — a `RenderLayer<T extends LivingEntity, M extends EntityModel<T> & HeadedModel>`. Renders via
  `this.getParentModel().getHead().translateAndRotate(poseStack)` then `NpcHairVis.render(...)`.
- New `src/main/java/net/bullettrain/xenopixelsmod/mixin/compat/customnpcs/RenderCustomNpcHairMixin.java`
  — `@Mixin(RenderCustomNpc.class)`, injects `<init>` at `RETURN`, calls the inherited
  `addLayer(new NpcHumanoidHairLayer(renderer))`. Confirmed via `javap` on the CustomNPCs jar that
  `RenderCustomNpc<T extends EntityCustomNpc, M extends HumanoidModel<T>>` extends
  `RenderNPCInterface<T,M>` extends vanilla `LivingEntityRenderer<T,M>`, and that vanilla
  `LivingEntityRenderer.addLayer` is `public final` (not protected as first assumed — checked the
  decompiled/sources jar directly, do not trust a stale assumption about its visibility).
  Registered in `src/main/resources/xenopixelsmod.compat.mixins.json`'s `client` list.

**Fix, part 2 — coordinate-handedness bug found from the user's screenshots:** vanilla
`LivingEntityRenderer.setupRotations()` applies a global `poseStack.scale(-1.0F, -1.0F, 1.0F)`
before any model/layer renders (an old MC rendering convention). GeckoLib's own render pipeline
(used by the *working* gecko-NPC and real-player hair paths, `NpcDmzHairLayer`/`DMZHairLayer`)
does not apply this mirror. `HairRenderer`'s raw strand transforms were authored for that
unmirrored GeckoLib convention, so calling it from inside vanilla's mirrored pose stack flipped
the hair vertically — spikes that should read as growing up from the scalp instead draped down
over the chest, matching the user's screenshots exactly. Confirmed the general pattern (vanilla
content needing a compensating flip when attached to a head bone under this mirror) against
vanilla's own `CustomHeadLayer`, which does exactly this for skull rendering (a `-1.1875f` Y/Z
scale specifically chosen to cancel the ambient mirror for two axes). Fix: added
`poseStack.scale(-1.0f, -1.0f, 1.0f)` in `NpcHumanoidHairLayer.render()` right after the
`translateAndRotate` call, to hand `HairRenderer` a pose stack in the same handedness convention
it was authored for.

**Not yet confirmed:** the user reported the original bug with screenshots but has not yet
confirmed the fix looks correct in-game.

## 2. NPC ki aura invisible under shader packs when looking at the NPC — compiled, not confirmed

**Root cause (decompiled `libs/dragonminez-2.1.3.jar`):** `NpcAuraClient.onRenderLevel` drew at
`RenderLevelStageEvent.Stage.AFTER_ENTITIES` via a raw `VertexBuffer.drawWithShader(...)` call,
and unconditionally rebuilt its pose stack from `camera.getXRot()/getYRot()` every frame. DMZ's
own real per-player aura path (`com.dragonminez.client.events.PlayerEffectsRenderHandler`,
`com.dragonminez.client.render.effects.AuraRenderer.renderShaderAura/shaderpackViewStack`) instead
draws at `Stage.AFTER_LEVEL` and explicitly force-rebinds the main render target
(`minecraft.getMainRenderTarget().bindWrite(false)`) immediately before drawing, and only
reconstructs the camera-based pose when `IrisCompat.isShaderPackInUse()` is confirmed true —
otherwise it uses the event's real `event.getModelViewMatrix()`.

**Fix**, `src/main/java/net/bullettrain/xenopixelsmod/client/compat/npc/NpcAuraClient.java`:
moved the stage check to `AFTER_LEVEL`, added the `bindWrite(false)` call before the draw loop,
and gated the manual camera-pose reconstruction behind `IrisCompat.isShaderPackInUse()`
(`com.dragonminez.client.render.util.IrisCompat`, confirmed `public static boolean
isShaderPackInUse()` via `javap` — it's already a transitive dependency through `AuraRenderer`,
no new library dependency added).

## 3. NPC aura not turning off after descending from a transformed form — compiled, not confirmed

**Root cause:** `NpcTransformSystem.start()` sets `profile.auraOn = true` for every transform.
`descend()` cleared `formGroup`/`formId`/`formPower` and called `NpcAuraFx.sync(npc)`, but never
reset `profile.auraOn` to `false` — `sync()` just re-reads whatever `auraOn` currently is, so the
aura kept broadcasting as active after the NPC powered back down.

**Fix:** `src/main/java/net/bullettrain/xenopixelsmod/compat/npc/NpcTransformSystem.java`,
`descend()` now sets `profile.auraOn = false;` before `profile.write(npc)`. `descendOne()` (the
"step down one form" path, e.g. SSJ3→SSJ2) was not touched and needs no fix — it either
re-transforms via `start()` (aura correctly stays on) or falls through to `descend()` (now
correctly turns it off) depending on whether a lower form exists.

## 4. NPC combat-profile stats not visible in the DMZ wand GUI after a script `setProfile` call — compiled, not confirmed

**Root cause (traced, not decompiled — all XenoPixels code):** `GuiNpcDmz.editorProfile()` calls
`NpcCombatProfile.read(npc)` fresh every open, but `npc` there is the **client-side**
`EntityNPCInterface` object (the GUI opens purely client-side, no server round trip). NeoForge's
per-entity persistent-data tag (`NpcCombatProfile`'s storage) is per-Java-object and never synced
client↔server automatically. `NpcXenoScriptApi.setProfile` correctly writes the authoritative
**server** entity's persistent data (confirmed: `ICustomNpc.getMCEntity()` is a plain passthrough
in the CustomNPCs `EntityWrapper`, no client/server proxy indirection) — but the only S2C packet
that existed, `NpcAppearancePacket`, carried only `race/formGroup/form/hairEnabled/hairCode
/hairColor`, never the stat fields, so the client entity's own (never-updated) persistent data is
all `GuiNpcDmz` could ever see.

**Fix:**
- `network/packet/NpcAppearancePacket.java`: added `strength, strikePower, resistance, vitality,
  kiPower, energy` (6 ints, `writeVarInt`/`readVarInt`) to the constructor/`encode`/the
  `FriendlyByteBuf` constructor.
- `network/ModNetwork.java`: bumped `PROTOCOL` `"40"` → `"41"` (this field gates client/server
  channel compatibility — both sides must rebuild together).
- `client/compat/npc/NpcAppearanceClient.java`: `State` record gained the same 6 fields; `apply(...)`
  overload updated to match.
- `compat/npc/NpcAppearanceFx.java`: `packet(...)` now reads the 6 fields off the already-loaded
  `NpcCombatProfile` and includes them.
- `client/compat/npc/gui/GuiNpcDmz.java`: `editorProfile()` now overlays
  `p.strength/strikePower/resistance/vitality/kiPower/energy` from `NpcAppearanceClient.State`,
  the same way `race`/`hairEnabled` were already overlaid.
- `CLAUDE_CNPC_HANDOFF.md`'s network protocol table was updated with a `41` row documenting this.

**Known, separate, unfixed gap (do not conflate with the above):** CustomNPCs' spawner-style
"Naturally" respawn mode (`stats.spawnCycle == 4`, `NPCSpawning.spawnData()` — decompiled,
confirmed) constructs a genuinely new `Entity` from a stored NBT template with no hook anywhere
in this repo to copy a live `NpcCombatProfile` onto that new instance. Ordinary per-NPC
death/respawn (`spawnCycle` 0/1/2 — "Yes/Day/Night") revives the *same* entity instance and
preserves persistent data correctly (`EntityNPCInterface`/`EntityCustomNpc`'s
`readAdditionalSaveData`/`addAdditionalSaveData` both call `super`, confirmed via decompile) — not
a bug. Only the spawner-template path is a real, still-open gap.

## 5. Vanish sound + `/xenochase speed` — compiled, not confirmed

- `network/Bt3CombatPacket.java`, `playItSound(...)`: hardcoded fallback `SoundEvent` changed from
  `dragonminez:evasion1`/`evasion2` to `dragonminez:tp` (confirmed as a real registered sound in
  the DMZ jar's `assets/dragonminez/sounds.json` before using it — do not assume a DMZ sound id
  exists without checking that file). The existing `vanishSoundIn`/`vanishSoundOut` config
  override mechanism (checked first, before this fallback) was left untouched.
- `config/XenoServerConfig.java`: added `setChaseFlightSpeed(double)` (mirrors the existing
  `setChaseMaxRange(double)`).
- `command/ChaseFlightCommands.java`: added a `speed` subcommand to `/xenochase`
  (`FloatArgumentType.floatArg(0.1f, 20.0f)`), mirroring the existing `range` subcommand's shape
  exactly (same permission node, `DmzHudCommands.broadcast()` after the change, updated
  `currentLine()`/usage text).

## 6. Hakai erasure ability (Ctrl + left click) — compiled clean; permission-rejection path confirmed live; full success path NOT confirmed

A new BT3-style ability: hold Ctrl and left-click a target to channel an escalating purple
particle dissolve on it for a few seconds, ending in the target's outright erasure. Slots into the
existing `Bt3CombatPacket`/`Bt3CombatClient`/`Bt3CombatLimiter` system as one more move, the same
family vanish/ultimate/chase already live in.

**Trigger (client), `client/combat/Bt3CombatClient.java`:** no Ctrl+mouse input handling of any
kind existed anywhere in this codebase before this session (confirmed by search) — this is new
infrastructure, not a reuse of an existing pattern. `InputConstants.isKeyDown` only wraps
`GLFW.glfwGetKey` (checked the decompiled/sources `InputConstants.java` — it does **not** support
mouse buttons), so:
- `ctrlHeld()` — new helper, raw `InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL |
  GLFW.GLFW_KEY_RIGHT_CONTROL)`, the same raw-poll technique already used for the Ki Guidance
  Right-Alt key (`guidanceKeyDown()`).
- `leftMouseHeld(mc)` — new helper, calls `GLFW.glfwGetMouseButton(window,
  GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS` directly (LWJGL, not `InputConstants`), since
  the latter can't check mouse buttons.
- `onAttackStart` (the existing `DMZClientEvent.PlayerAttackStart` handler that turns left-click
  into a combo hit): if `ctrlHeld()`, resolves the target via the same two-tier lock-on-then-
  crosshair-raycast pattern every other ability already uses (`LockOnEvent.getLockedTarget()`,
  falling back to `findLookTarget(mc, range)`), sets `hakaiChanneling = true`, sends
  `Bt3CombatPacket(Action.HAKAI_START, target.getId(), 0)`, and returns before the combo logic
  runs.
- `onClickInput` (`InputEvent.InteractionKeyMappingTriggered`): added a Ctrl-held check that
  cancels/suppresses the vanilla attack swing, next to the existing charge/guard suppression.
- `tickHakai(mc)`, called from the main `ClientTickEvent.Post` handler: while `hakaiChanneling`,
  if Ctrl or the left mouse button is no longer down, sends `HAKAI_CANCEL` and clears the flag.
  `cancelHakaiIfChanneling()` was also added to the three existing "reset all combat input state"
  early-return blocks in that same tick handler (GUI open / seated in a pilot seat / combat
  disabled), so a channel can't get stuck if one of those conditions interrupts it.
- `network/Bt3CombatPacket.java`: `Action.HAKAI_START` / `HAKAI_CANCEL` **appended** to the
  `Action` enum (it's wire-encoded via `writeEnum`/`readEnum`, i.e. by ordinal — appending only is
  required, matching this file's own established convention for that enum).

**Server gating, `Bt3CombatPacket.handle()`'s `HAKAI_START` case**, in this exact order (order
matters — see the cooldown/cost bug below):
1. `target == null` → return (added to the outer method's target-required exemption list is
   `HAKAI_CANCEL`, not `HAKAI_START` — the start genuinely requires a target).
2. `DmzMasterProtection.isDmzMaster(target)` block — this pre-existing check runs for
   `HAKAI_START` same as every other action except `GUARD`/`SPARKING`/`HAKAI_CANCEL` (added the
   cancel exemption so a release-signal can never itself be blocked by master-protection).
3. `XenoServerConfig.hakaiEnabled` config toggle.
4. `XenoPermissions.hasPermission(player.createCommandSourceStack(), XenoPermissions.HAKAI_USE)`
   — **this is the first BT3 packet-driven combat action gated by a permission node** in this
   codebase; confirmed via search that every other BT3 action (vanish, ultimate, etc.) is gated
   only by a config bool, never `XenoPermissions`. This is new wiring, not a reused pattern.
5. `CombatSkills.hakaiUnlocked(player)` — new skill-tree gate, see below. This was added on
   explicit user request ("Hakai should be under xenoskill... required to unlock, not just a
   config toggle").
6. `player.distanceTo(target) > XenoServerConfig.hakaiMaxRange` → return.
7. `Bt3CombatLimiter.canHakai(player)` (cooldown) — **checked before** the ki-cost check. This
   was originally the other way around (cost spent before cooldown checked) and was a real bug:
   a cooldown-blocked attempt would silently waste ki for nothing. Fixed to match `ULTIMATE`'s
   established order (`canUltimate` checked before its handler spends ki).
8. `trySpendKi(res, XenoServerConfig.hakaiKiCost)` — the existing helper.
9. `HakaiChannelSystem.start(player, target)`.

**`combat/HakaiChannelSystem.java` (new)** — there is no pre-existing multi-tick "runs over
several seconds" ability system anywhere in this codebase to extend (confirmed: every existing FX
call, `VanishShadeFx`/`AfterimageFx`, fires all its particles in a single server tick). This
mirrors `network/ChaseFlightSystem.java`'s shape instead (the only other system here that tracks
per-player state across ticks): a `ConcurrentHashMap<UUID, Channel>`, driven by
`PlayerTickEvent.Post`, re-validating every tick (target alive, in `hakaiMaxRange`, line-of-sight
via a `Level.clip(new ClipContext(...))` check — the same technique `LockOnValidator`'s private
`hasLineOfSight` uses, reimplemented here without its hard seated-pilot requirement since that
class is scoped to the aero dogfight lock-on system, not ground combat; caster hasn't drifted more
than 1 block² from where the channel started). Also hooks `LivingDamageEvent.Pre` (same event
class/priority style as the pre-existing `Bt3CombatEvents.onHurt`) to cancel the channel the
instant the caster takes any damage, and `PlayerEvent.PlayerLoggedOutEvent` for cleanup. On
completion, calls `HakaiFx`'s final cue and then:

```java
target.hurt(target.level().damageSources().genericKill(), Float.MAX_VALUE);
```

This was chosen deliberately over raw `target.remove(RemovalReason.KILLED)` because the ability
must work correctly on **both mobs and real players** (per explicit user choice) — removing a
`ServerPlayer` entity outright would corrupt the respawn flow instead of running it. Verified via
the DMZ-adjacent vanilla data (`data/minecraft/tags/damage_type/bypasses_invulnerability.json`,
`bypasses_armor.json`, `bypasses_resistance.json` — extracted directly from the NeoForge/vanilla
resources jar, not guessed) that `minecraft:generic_kill` (the same damage type vanilla's own
`/kill` command uses) is tagged in all three, and that `LivingEntity.checkTotemDeathProtection`
(checked in the decompiled/sources `LivingEntity.java`) skips the totem-of-undying save
specifically for any damage source tagged `bypasses_invulnerability`. So this single call gets
"bypasses armor/absorption/totems/invulnerability" (the user's own explicit requirement) for free,
while still running the real, normal `hurt()`/`die()` flow for a player. No custom `DamageType`
datapack entry was added — this reuses a real, already-registered vanilla damage source.

**`combat/fx/HakaiFx.java` (new)** — reuses `SilhouetteFx.stamp(...)` (the existing body-shaped
particle stamper `VanishShadeFx` already uses for its vanish-departure shade), called every 2
ticks at the **target's** position with `density` ramping `0.15→1.0` and `scale` growing slightly
across the channel, violet/magenta `Vector3f` colors, plus a small ring of
`ParticleTypes.REVERSE_PORTAL`/`PORTAL` particles. `combat/fx/CombatFxKind.java` gained one new
**appended** constant `HAKAI_ERASE` (this enum's own doc comment states it's sent as a wire
ordinal — appending only, confirmed before touching it) — used for a final `CombatFx.cue(...)`
flash on completion. `client/combat/fx/CombatFxClient.java`'s `Profile.of(CombatFxKind)` is an
**exhaustive** switch expression (would not compile without a matching case), so a new
`HAKAI_ERASE` case (violet flash, low shake) was added there too.

**Config, `config/XenoServerConfig.java` / `XenoServerConfigKeys.java`:** `hakaiEnabled` (bool,
default `true`, also registered via the existing `XenoServerConfigKeys.bool(...)` helper so it's
also toggleable via `/xenoserver set`), `hakaiKiCost` (float, default `60.0f` — chosen above
Ultimate's existing `35.0f` default since Hakai is meant as the more extreme move, not measured
against any real balance data), `hakaiMaxRange` (double, default `15.0`), `hakaiCooldownTicks`
(int, default `600` = 30s, clamped `[40, 2400]`), `hakaiChannelTicks` (int, default `80` ≈ 4s at
20 TPS, per the user's explicit "~4 seconds — cinematic" choice). All five touched in this file's
own established 5-place pattern (field declaration / `copyTo` sync block / `copyFrom` block with
clamping / the `Defaults` inner class / registration) — the same pattern `ultimateKiCost`/
`ultimateCooldownTicks` already use, used here as the template since Hakai is meant to be a
heavier, rarer ability than Ultimate.

**Permission, `command/XenoPermissions.java`:** `HAKAI_USE` (`op(...)`-default — OP level 2,
same helper `XENOCHASE_SET` uses — chosen over the everyone-allowed `client(...)` helper because
this is a PvP-capable instant-kill and should require an admin to explicitly grant it) and
`HAKAI_SET` (also `op(...)`-default, gates the `/xenohakai` admin command below).

**Skill gate, `features/progression/CombatSkills.java`:** added `HAKAI` skill id (title "Hakai",
description mentions the Ctrl+left-click trigger, cost `3` points). This required a real,
non-cosmetic change to `SkillDef`: it was previously `record SkillDef(String id, String title,
String desc, int pointCostPerLevel)` with `tryUnlock` hardcoding `if (cur >= 3) return ... "max
level (3)"` for every skill. Hakai needed to be a binary unlock (max level 1), not a stacking
buff, so `SkillDef` gained a `maxLevel` component and all 7 pre-existing skills were updated to
explicitly pass `maxLevel=3` (preserving their exact prior behavior) while Hakai passes `1`.
`tryUnlock` now reads `def.maxLevel` instead of the hardcoded `3`. New helper:
`CombatSkills.hakaiUnlocked(ServerPlayer)` → `level(player, HAKAI) >= 1`. Players unlock it via
the pre-existing `/xenoskill unlock hakai` command path (`ProgressionCommands.java`, unchanged —
it already reads `CombatSkills.DEFS` generically).

**Command (new), `command/HakaiCommands.java`:** `/xenohakai status|toggle|kicost|range|cooldown`,
structured identically to the pre-existing `ChaseFlightCommands.java` (same subcommand/argument
shape, `XenoPermissions.require(HAKAI_SET)` on the mutating subcommands,
`DmzHudCommands.broadcast()` after any change). New setters
`setHakaiEnabled/setHakaiKiCost/setHakaiMaxRange/setHakaiCooldownTicks` added to
`XenoServerConfig.java` to back it, mirroring the existing `setChaseFlightSpeed`/
`setChaseMaxRange` shape.

**Audio (new files):** both cut from user-supplied source clips (both **copyrighted anime audio**
— confirmed fine to bundle for a private build by the user, but flag before any public
distribution of this mod):
- `assets/xenopixelsmod/sounds/hakai_charge.ogg` — from a user-provided
  "Dragon Ball Super Hakai Sound Effect.mp3", the 15.0–24.1s segment (identified via
  `ffmpeg -af silencedetect`/`volumedetect` analysis, confirmed with the user which segment to
  use), time-compressed ~2.25x via chained `ffmpeg -af atempo=1.5,atempo=1.5` (chosen because a
  single `atempo` filter instance only supports 0.5–2.0x) to land at ~4.04s to fit
  `hakaiChannelTicks`, with a short fade-out.
- `assets/xenopixelsmod/sounds/hakai_voice.ogg` — from a separate user-provided, already-trimmed
  "hakai.mp3" (2.376s, a spoken "Hakai" line) — used as-is, just re-encoded to Vorbis.
- Registered: `sound/ModSounds.java` (`HAKAI_VOICE`, `HAKAI_CHARGE`, using the existing
  `register(String)` helper), `assets/xenopixelsmod/sounds.json` (two new top-level entries,
  same shape as the pre-existing `vanish` entry), `assets/xenopixelsmod/lang/en_us.json`
  (subtitle keys, same shape as the pre-existing `subtitles.xenopixelsmod.vanish` line). Played
  once each — the voice line and the charge hum, both starting at `HakaiChannelSystem.start()` —
  directly via `ModSounds.HAKAI_VOICE.get()`/`HAKAI_CHARGE.get()`, **not** through vanish's
  config-override indirection (that indirection exists specifically so an admin can swap vanish's
  sound without a rebuild; there's no equivalent user-facing need here yet).

**Confirmed live (the only two things in this whole session actually observed on a running
server):** (a) the permission-rejection path — a non-permitted account tried Ctrl+left-click and
correctly got the "§7You do not have permission to use Hakai" chat message, confirming the
input-detection → packet → server-gate chain works end to end up through that check; (b) after
adding the `/xenoskill unlock hakai` requirement, the user has not yet retested past that gate.
**Not yet confirmed:** the full success path (unlocked + permitted + off cooldown + affordable →
channel completes → target actually dies) has never been observed on a live server.

## 7. NPC damage now scaled from DMZ's real formulas instead of an invented approximation — compiled, not confirmed

**DMZ's real formulas, decompiled from `com/dragonminez/common/stats/StatsData.class`,
`com/dragonminez/common/stats/techniques/TechniqueDispatcher.class`,
`com/dragonminez/common/stats/techniques/KiAttackData.class`** (default/no-form/no-bonus
collapse — the full formulas have additional form/mastery/bonus-stat terms this NPC system does
not attempt to replicate):
- Melee (`getMeleeDamage()`): `1.0 + strength * releaseMultiplier`. Uses `strength`, **not**
  `strikePower`.
- Named strike techniques (`getStrikeDamage()`, e.g. Dragon Fist/Wolf Fang — **no XenoPixels NPC
  code path fires any of these**, confirmed by checking that every id `NpcKiAttackDispatcher`
  dispatches is a ki-type attack, never a `StrikeAttackData` id): `1.0 + (strikePower +
  strength*0.25) * releaseMultiplier`.
- Ki attacks (`getKiDamage()`, consumed in `TechniqueDispatcher.executeKiAttack`): `kiPower *
  releaseMultiplier`, then multiplied by the technique's own `getDamageMultiplier()`.
- `releaseMultiplier` = `Resources.getPowerRelease() / 100.0`, a real player's manually-raised
  combat-stance stat that **defaults to 5%** (`Resources.release` default, decompiled). No NPC
  equivalent existed before this session.

**What was wrong before this session (all confirmed by reading the pre-existing
`NpcKiAttackDispatcher.java`):** ki-attack damage was computed as
`(2.0f + profile.strikePower * 0.5f) * charge` (and a similar `3.0f + strikePower*0.75f` variant
for the generic wave) — scaled from the **wrong stat** (`strikePower` instead of `kiPower`) with
flat/linear constants that don't correspond to any real DMZ formula. NPC melee damage did not
exist at all anywhere in the repo — CustomNPCs' own vanilla attack-damage attribute was used,
completely independent of the profile stats shown in the wand GUI.

**Fix:**
- `compat/npc/NpcCombatProfile.java`: added `powerReleasePercent` (int, default `100` — chosen on
  explicit user instruction: "add a profile field," defaulting to full power since NPCs have no
  UI to raise a release stance manually), persisted as NBT tag `PowerReleasePercent`. Added
  `releaseMultiplier()` (`powerReleasePercent/100.0`), `meleeDamage()` (`1.0 + strength *
  releaseMultiplier()`), `kiDamage()` (`kiPower * releaseMultiplier()`).
- `compat/npc/NpcKiAttackDispatcher.java`: `fireKiBlast`, `fireKiWave`, and `baseDamage(profile)`
  (used by every `PredefinedTechniques` id) all switched their base-damage line to
  `profile.kiDamage()`. The existing `formPower`/`chargeFactor()` multipliers layered on top were
  left in place — these are NPC-only extensions (a "how strong is the current form" multiplier
  and an NPC-only ki-charge-percent mechanic) with no DMZ equivalent, not something this fix
  removes.
- New `compat/npc/NpcMeleeDamage.java`: `@SubscribeEvent LivingDamageEvent.Pre`, applies
  `profile.meleeDamage()` (blended with a residual of the original vanilla-attribute damage, the
  same `finalDamage + Math.max(0, original - 1.0)` shape DMZ's own `CombatEvent` uses for real
  players) to hits where the attacker has an `NpcCombatProfile` **and**
  `event.getSource().getDirectEntity() == attacker` — that last check is what excludes ki-blast
  damage (a projectile's direct entity is the projectile, not its owner) from being
  double-touched by this melee-only hook.
- `compat/npc/NpcXenoScriptApi.java`: added `setPowerRelease(npc, percent)` (clamped 1–100).

**Deliberate, known non-fix:** `strikePower` now has genuinely no effect on anything an NPC
currently does. This is not a bug — DMZ only reads `strikePower` for strike techniques, and no
XenoPixels NPC code path fires a strike technique (only ki-type ones). It will start mattering
only if NPC strike-technique support is added later; that was explicitly out of scope this
session.

## 8. `NpcXenoScriptApi.fireTechnique` gained a 5-arg color overload — compiled, NOT confirmed (live TypeError, see below)

Pre-existing `fireTechnique(ICustomNpc npc, String id, IEntity target, int durationTicks)` had no
way to set a custom ki color for a single shot — the only color control was
`setKiColor(npc, hex)`, which is **persistent** (recolors every future attack). Added a 5-arg
overload `fireTechnique(ICustomNpc npc, String id, IEntity target, int durationTicks, String hex)`
that parses `hex` via the pre-existing `NpcCombatProfile.parseHexColor(String)` utility and passes
it as a one-shot `colorOverride` into `NpcKiAttackDispatcher.fire(..., int colorOverride)` — an
overload that **already existed** and was already used by the `/xenopixels npcprofile kiattack
... color <hex>` command path; this session did not invent that lower-level plumbing, only
exposed it to scripts.

**Live-tested and failed, but almost certainly a stale-build issue, not a code bug:** the user
tried this from a live server script and got a Nashorn `TypeError: Can not invoke method boolean
...fireTechnique(ICustomNpc,String,IEntity,int) with the passed arguments` — the error names only
the **old 4-arg** signature, while the script's call site had 5 arguments (confirmed by reading
the actual pasted script). This is the exact symptom of a running server jar that predates this
session's change (Nashorn's Java-bean linker can only report signatures that exist in the classes
actually loaded at runtime). **This has not been re-tested after a rebuild** — do not assume it's
fixed just because the reasoning is sound; get a fresh test after the user rebuilds and restarts.

## 9. Investigated this session but NOT implemented — do not claim these are done

- **NPC ki-attack real charge/duration windows for `soul_punisher`/`supernova_cooler`/
  `fake_moon`.** Decompiled `KiBlastEntity.setupSoulPunisherPlayer`/`setupKiNovaCoolerPlayer`/
  `setupFakeMoonPlayer` and confirmed DMZ hardcodes real per-technique charge windows inside those
  Java methods (100/100/60 ticks respectively, via `setCastTime(...)`) for real players.
  `NpcKiAttackDispatcher.firePredefinedTechnique`'s `releasePlayerAttack` currently calls
  `fireHability(life)` on the **same tick** as `setup*Player`/`addFreshEntity`, collapsing that
  charge window to instant for NPCs. Also found, while investigating: a redundant double
  `level.addFreshEntity` call (once inside DMZ's own `setup*Player`, once again in
  `releasePlayerAttack`), and that the dispatcher's `applyDuration(projectile, durationTicks)`
  overwrites `maxLife` in a way that drops the `tickCount +` offset `fireHability` had just set.
  `taiyoken` was confirmed to have **no real duration/debuff at all** in DMZ 2.1.3 — it's an
  instant, 0-damage `SMALL_BALL` (`KiAttackData.isInstantCast()` returns true for `SMALL_BALL`/
  `LASER`, `getActualCastTime()` returns 0) — there is nothing to "add duration to" for it; do not
  invent a blind/debuff effect for it, DMZ's own data model has none. **No code was written for
  any of this** — it needs a new per-caster ticking "charging" state (shaped like
  `HakaiChannelSystem`) that defers the `fireHability` call, plus fixing the double-spawn and the
  `applyDuration` overwrite bug.
- **Autonomous NPC AI use of ki attacks.** Confirmed via search (`Goal`/`ai.goal`/
  `noppes.npcs.ai` — zero hits in `src/`) that no such system exists anywhere in this repo. Every
  ki attack today requires an explicit script call (`fireTechnique`) or OP command
  (`NpcProfileCommands.techFire`/`fireOne`). Building "NPCs autonomously decide to use a
  technique during combat" is new work, not an extension of anything that exists.

## 10. External bug, not fixable from this codebase

CustomNPCs-Unofficial's own NPC-script text box (`noppes.npcs.shared.client.gui.components
.GuiTextArea`) has been producing scattered mid-line text corruption on the user's server across
multiple paste attempts of the same script (different random chunks missing each time — never
whole lines/functions). Decompiled the actual paste-handling code
(`GuiTextArea.keyPressed`'s `Screen.isPaste(keyCode)` branch: a single atomic
`this.addText(NoppesStringUtils.getClipboardContents())` call, no per-character loop, no length
cap) and found no bug in it. Leading theory, not proven: the corruption is happening upstream of
Minecraft entirely — e.g. copying the script text out of a word-wrapped terminal's rendered
output, where click-drag selection across soft-wrapped lines is a well-known source of exactly
this kind of scrambled-copy artifact. Workaround given to the user: save the script to a real
`.js` file and copy from a real text editor instead of the terminal (a
`examples/customnpcs/xenopixels_ssj_show_fixed.js` file was created and sent to the user directly
via file transfer for this purpose). **Not confirmed resolved** — the user had not reported back
a clean paste as of this handoff.

---

## If you continue

1. `./gradlew compileJava --offline` should currently succeed clean (it did as of the last change
   in this session) — verify that's still true before assuming any of the above compiles.
2. Nothing in sections 1–8 has a confirmed-working `runClient`/live-server pass except the two
   narrow items called out in section 6 ("Confirmed live"). Treat everything else as
   compile-verified only until proven otherwise.
3. Both the server and client jar need to be rebuilt and the server restarted before any of this
   session's script-API or packet-protocol changes (protocol bumped to `41`) will actually be
   live for a running server — a stale jar produces exactly the kind of "method doesn't exist"
   errors seen in section 8.
4. Section 9's two items (real NPC ki-attack charge windows, autonomous NPC AI) are the most
   likely next asks — they were investigated in depth but zero code was written for either.
