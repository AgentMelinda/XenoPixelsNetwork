# XenoPixels CustomNPC scripting guide for server co-owners

This guide documents the server-side `XenoPixels` global implemented by
`NpcXenoScriptApi` version 28 (updated 2026-09-28). It is intended for CustomNPCs JavaScript hooks such as
`init(event)`, `timer(event)`, `damaged(event)` and `died(event)`.

## Quick safety rules

- Pass a live CustomNPC, normally `event.npc`. There is no automatic global named `npc`.
- Most setters save the NPC profile immediately and return `true`. Invalid IDs or colors can
  return `false`.
- Use real DMZ IDs. Discover them with `listFormGroups`, `listForms`, `listStackGroups` and
  `listStackForms` instead of guessing.
- Colors may use `#RRGGBB`. Examples below use that format.
- Transform durations are ticks; 20 ticks are approximately one second.
- Check the installed bridge with `XenoPixels.getVersion()`; this document expects `"28"`.
  Charge moves need `23` or later, `XenoPixels.bubble` needs `28`.
- `XenoPixels.say(player, text)` is a private chat line. For a speech bubble over a player
  (or any entity) use `XenoPixels.bubble(player, text[, color[, shape]])`.
- Native Xeno NPCs (no CustomNPCs or MyNPCs needed) have more: see [XenoAPI](XenoAPI).
- Charged punch / kick (`startChargePunch` / `startChargeKick`) needs a combat
  profile (`setProfile`) on a live server-side NPC. It is not `setKiCharge`
  (that still scales KI techniques).
- Nashorn leaves Java `String[]` as a Java object. `.length` and `[i]` work;
  `.join` does not. Use `XenoPixels.joinNames(list)` or copy into a JS array first.

## Example scripts

Paste one file per tab. **Enabled = Yes**, then close the GUI so it saves.

**NPC Scripts tab** (`examples/customnpcs/`):

| File | What it does |
| --- | --- |
| `xenopixels_say_greeter.js` | Right-click bubbles (`npc.say`) |
| `xenopixels_simple_puncher.js` | Profile + native CustomNPCs melee |
| `xenopixels_custom_punches.js` | Picks a punch clip on `meleeAttack` |
| `xenopixels_combo_rush.js` | Authored rush: jabs → vanish → flying kick → kamehameha (real hits, no charge) |
| `xenopixels_charge_melee.js` | Right-click charged punch (v23) |
| `xenopixels_charge_kick.js` | Right-click charged kick; cycles up / down bias |
| `xenopixels_charge_counter.js` | Starts a charge when the NPC is hit |
| `xenopixels_charge_brain.js` | Combat Brain on — brain starts charges in melee |
| `xenopixels_state_clips.js` | Bind studio clips to transform / punch / charge slots |
| `xenopixels_play_clips.js` | Right-click playlist of `/xenoanim global push` clips |
| `xenopixels_ssj1_to_ssj3_clips.js` | Right-click SSJ1→2→3; binds TRANSFORM then `ascend` |
| `xenopixels_ssj_show.js` | Simple SSJ climb without custom clips |
| `xenopixels_full_saiyan.js` | Full fighter sample |

**Global → Player Scripts** (dedicated: `/xenopixels playerscripts` after save):

| File | What it does |
| --- | --- |
| `xenopixels_player_say.js` | Private welcome via `XenoPixels.say` |
| `xenopixels_player_chat.js` | `#` rewrite, `!ping` cancel, `!announce` broadcast |
| `xenoapi_player_bubbles.js` | Speech bubbles over the player on join, chat, death and level up (v28) |

These examples are not in-world proof. If `getVersion()` is below `23`, charge
and `broadcast` are missing on that JAR.

## New in v28 (2026-09-28)

**Speech bubbles over players.** `XenoPixels.say(player, text)` is unchanged: a private chat line
only that player sees. The new call puts a bubble over any entity, players included, that
everyone nearby sees:

```js
function login(event) {
    XenoPixels.say(event.player, "Welcome back!");                        // private line
    XenoPixels.bubble(event.player, "Hello everyone!", "gold", "shout");  // bubble
}
```

| Call | Result |
| --- | --- |
| `XenoPixels.bubble(entity, text)` | bubble in the default look |
| `XenoPixels.bubble(entity, text, color)` | `blue`, `gold`, `green`, `red` |
| `XenoPixels.bubble(entity, text, color, shape)` | `rounded`, `thought`, `shout`, `banner` |

It respects `/xenopixels npcsay` like the NPC bubbles. Press F5 to see your own bubble. A server
and client older than v28 do not have it; check first:

```js
if (typeof XenoPixels.bubble !== "function") XenoPixels.say(event.player, "Need API v28");
```

**Once a second from a player script.** The player `tick` hook fires every 10 game ticks
(half a second). Act on every second call:

```js
var halfSeconds = 0;
function tick(event) {
    if (++halfSeconds % 2 !== 0) return;
    XenoPixels.bubble(event.player, "Online " + (halfSeconds / 2) + "s", "gold");
}
```

**Native Xeno NPCs only** (not in the CustomNPCs / MyNPCs bridge): set or toggle any editor
setting by key (`setDmz`, `toggleDmz`, `listDmz`), Ki Sense (`setKiSense`,
`setKiSenseLockOn`, `isKiSenseLockedOn`), identity and respawn (`setTitle`, `setRole`,
`setHome`, `setRespawn`), colored names and titles (`&6Goku`, `&#FF8800Title`), Forge
scripts, and red chat for script errors. All of it is on the [XenoAPI](XenoAPI) page.

## Player script: say on init (paste this first)

`XenoPixels.say(player, text)` is a **private literal system line** to that
player. Do not use `player.message()` — My NPCs / CustomNPCs treat that text as
a translation key and the line does not show. It is not public chat and not an
NPC bubble. Public chat is `XenoPixels.broadcast(player, text)`. Public rewrite
is `setChatMessage` in `chat(event)`. Hide the typed line with
`XenoPixels.cancelChat(event)` (CustomNPCs `ChatEvent` is cancellable; the
signed `ServerChatEvent` is cancelled by the chat mixin or the LOWEST fallback).

On a dedicated server, after pasting player scripts, run
`/xenopixels playerscripts` (op) and confirm `HasStart=true`,
`serverStarted=true`, `enabled=true`, and `XenoPixels=true`. `enable` writes
the global Enabled flag if the GUI copy did not.

Paste `examples/customnpcs/xenopixels_player_say.js` into **Global → Player
Scripts**, set **Enabled = Yes**, and close the GUI so it saves. Public chat
demo: `examples/customnpcs/xenopixels_player_chat.js`.

```js
function init(event) {
    speak(event.player);
}

function login(event) {
    speak(event.player);
}

function logout(event) {
    if (event.player) {
        event.player.getTempdata().remove("xeno_said");
    }
}

function speak(player) {
    if (player == null) return;
    if (typeof XenoPixels === "undefined" || typeof XenoPixels.say !== "function") {
        return;
    }
    var temp = player.getTempdata();
    if (temp.has("xeno_said")) return;
    temp.put("xeno_said", 1);

    XenoPixels.say(player, "XenoPixels v" + XenoPixels.getVersion() + " — scripts loaded.");
    XenoPixels.say(player, "Welcome back, " + player.getName() + ".");
    XenoPixels.say(player, "These lines are private. Public chat is unchanged.");
}
```

NPC bubbles stay `npc.say("...")` on the NPC Script tab, or
`XenoPixels.say(npc, "...")` which honors `/xenopixels npcsay`.

## Right-click speech (NPC tab)

NPC bubbles are `npc.say("...")` or `XenoPixels.say(npc, "...")`. They show to
players within about 20 blocks. XenoPixels can mute them with
`/xenopixels npcsay off`; leave them on with `/xenopixels npcsay on`.

Paste the whole script from `examples/customnpcs/xenopixels_say_greeter.js` into
the NPC **Scripts** tab, set **Enabled = Yes**, and save. Right-click is
`interact(event)`.

```js
"use strict";

var T_FOLLOWUP = 9301;

function init(event) {
    var npc = event.npc;
    var data = npc.getStoreddata();
    data.put("xeno_clicks", 0);
    data.put("xeno_talking", 0);

    if (typeof XenoPixels === "undefined") {
        npc.say("XenoPixels is not loaded on this server.");
        return;
    }

    npc.say("XenoPixels v" + XenoPixels.getVersion() + " — right-click me.");
}

function interact(event) {
    var npc = event.npc;
    var player = event.player;
    if (player == null) return;

    var data = npc.getStoreddata();
    if (Number(data.get("xeno_talking")) === 1) {
        npc.say("Hold on, I am still talking.");
        return;
    }

    var clicks = Number(data.get("xeno_clicks")) + 1;
    data.put("xeno_clicks", clicks);
    data.put("xeno_talking", 1);

    var name = player.getName();
    npc.say("Hey, " + name + ". That is click " + clicks + ".");

    npc.getTimers().forceStart(T_FOLLOWUP, 40, false);
}

function timer(event) {
    if (event.id != T_FOLLOWUP) return;

    var npc = event.npc;
    npc.say("Come back if you want to hear it again.");
    npc.getStoreddata().put("xeno_talking", 0);
}

function died(event) {
    var data = event.npc.getStoreddata();
    data.put("xeno_clicks", 0);
    data.put("xeno_talking", 0);
}
```

`init` runs when the NPC loads. Right-click greets the player by name and counts
clicks. After 40 ticks the timer says the follow-up. A second click during that
wait gets “still talking.”

## Minimal fighter setup

```js
function init(event) {
    var npc = event.npc;

    // race, strength, strikePower, resistance, vitality, kiPower, energy
    XenoPixels.setProfile(npc, "saiyan", 120, 110, 95, 140, 125, 160);
    XenoPixels.setKiCharge(npc, 100);
    XenoPixels.setPowerRelease(npc, 100);

    XenoPixels.setKiColor(npc, "#55CCFF");
    XenoPixels.setAuraColor(npc, "#FFD84A");
    XenoPixels.setAuraScale(npc, 1.0);
    XenoPixels.setAura(npc, true);
}
```

## Profiles and resources

```js
var profile = XenoPixels.getProfile(event.npc);
var energy = XenoPixels.getCurrentEnergy(event.npc);
var maxEnergy = XenoPixels.getMaxEnergy(event.npc);
var stamina = XenoPixels.getCurrentStamina(event.npc);
var maxStamina = XenoPixels.getMaxStamina(event.npc);

XenoPixels.setCurrentEnergy(event.npc, maxEnergy);
XenoPixels.setCurrentStamina(event.npc, maxStamina);
```

`getProfile` returns the race and six DMZ stats, current and selected normal/stack forms,
aura state/color/scale, halo and effect toggles, tail state/color, current/max resources,
techniques and hair data.

## Discovering and using forms

```js
var race = "saiyan";
var groups = XenoPixels.listFormGroups(race);
var forms = XenoPixels.listForms(race, "supersaiyan");

XenoPixels.selectForm(event.npc, "supersaiyan", "supersaiyanmastered");
XenoPixels.setMastery(event.npc, "supersaiyan", "supersaiyanmastered", 100);

// Transform over 40 ticks.
XenoPixels.ascend(event.npc, "supersaiyan", "supersaiyanmastered", 40);

// Later:
XenoPixels.descendOne(event.npc, 30);
// XenoPixels.descend(event.npc, 30);
```

Prevent overlapping transform requests:

```js
if (!XenoPixels.isTransforming(event.npc)) {
    XenoPixels.ascend(event.npc, "supersaiyan", "supersaiyanmastered", 40);
}
```

## Stack forms such as Kaioken

```js
var stackGroups = XenoPixels.listStackGroups();
var kaiokenForms = XenoPixels.listStackForms("kaioken");

XenoPixels.selectStack(event.npc, "kaioken", "x20");
XenoPixels.setStackMastery(event.npc, "kaioken", "x20", 100);
XenoPixels.stack(event.npc, "kaioken", "x20", 30);

// Remove only the active stack:
// XenoPixels.unstack(event.npc);
```

The IDs available on your server come from its loaded DMZ configuration. Always inspect the
lists if an example ID returns `false`.

## Native DMZ aura, halo and independent effects

FULL-appearance NPCs use DMZ's native aura shader. The NPC aura scale is applied after DMZ's
race/form model scale and CustomNPC Display size.

```js
XenoPixels.setAura(event.npc, true);
XenoPixels.setAuraColor(event.npc, "#FFD84A");
XenoPixels.setAuraScale(event.npc, 1.0);
XenoPixels.setHalo(event.npc, true);

XenoPixels.setAuraRocks(event.npc, true);
XenoPixels.setAuraSparking(event.npc, true);
XenoPixels.setAuraLightning(event.npc, true);
```

### Primary, secondary and lightning styles

The `scope` argument is `"base"`, `"form"` or `"stack"`. For base scope, group and form
may be empty strings.

```js
XenoPixels.setAuraStyle(
    event.npc,
    "stack", "kaioken", "x20",
    true,                 // style enabled
    "#CC0000", "kakarot", 1, // primary color, type, layer
    true, true,           // secondary configured, secondary enabled
    "#FF6600", "kakarot", 2,
    true, true, "#FFD0D0" // lightning configured, enabled, color
);
```

Inspection and reset:

```js
var saved = XenoPixels.getAuraStyle(event.npc, "stack", "kaioken", "x20");
var effective = XenoPixels.getResolvedAura(event.npc);
XenoPixels.clearAuraStyle(event.npc, "stack", "kaioken", "x20");
```

`getResolvedAura` returns ordered `layers` plus `lightning`, `lightningColor`, `rocks` and
`sparking`.

## Tail controls

```js
// The separate Saiyan tail visibility toggle:
XenoPixels.setSaiyanTail(event.npc, true);

// A custom tail color:
XenoPixels.setTailColor(event.npc, "#6A351D");

// Restore native DMZ race/body/form/stack inheritance:
XenoPixels.clearTailColor(event.npc);
// An empty value also clears it:
// XenoPixels.setTailColor(event.npc, "");
```

Tail color overrides apply to Saiyan, Bio-Android/Cell and Frost Demon/Frieza tails. Cell and
Frost Demon tails are embedded in their body models, so `setSaiyanTail` is not their
visibility control.

## Hair

```js
XenoPixels.setHairEnabled(event.npc, true);
XenoPixels.setHairColor(event.npc, "#111111");
XenoPixels.setHairCode(event.npc, savedHairCode);

var hair = XenoPixels.getHair(event.npc);
```

Hair codes can be very long. Keep them in a script variable or generated server data rather
than repeatedly pasting them into small GUI fields.

## Techniques and cooldowns

```js
function timer(event) {
    var npc = event.npc;
    var target = npc.getAttackTarget();
    if (target == null) return;

    if (XenoPixels.isTechniqueReady(npc, "kamehameha")) {
        XenoPixels.fireTechnique(npc, "kamehameha", target, 60);
    }
}
```

One-shot attack color without changing future attacks:

```js
XenoPixels.fireTechnique(event.npc, "kamehameha",
    event.npc.getAttackTarget(), 60, "#AA66FF");
```

Technique management:

```js
XenoPixels.addTechnique(event.npc, "kamehameha");
var known = XenoPixels.listTechniques(event.npc);
var remaining = XenoPixels.getTechniqueCooldown(event.npc, "kamehameha");
XenoPixels.clearTechniqueCooldown(event.npc, "kamehameha");
XenoPixels.removeTechnique(event.npc, "kamehameha");
```

## Combat animations

An NPC can play any of this mod's DragonMineZ combat clips - the same ones a player's held-mash
string uses.

```js
function timer(event) {
    var npc = event.npc;

    XenoPixels.playAnimation(npc, "combat.xeno_spin_kick_right_v3");
    XenoPixels.playAnimation(npc, "combat.xeno_heavy_finish_v3", 1.5);  // 1.5x playback

    XenoPixels.playComboBeat(npc, 3);   // beat 3 of the BT3 rush string

    var names = XenoPixels.listAnimations();   // every name playAnimation accepts
}
```

**Only NPCs set to the Full DragonMineZ appearance can show these.** That mode draws the NPC
through a synthetic player, which is the only thing DragonMineZ's animation system will pose. A
humanoid ("Steve") or Gecko custom-model NPC returns `false` and keeps using its own model's
animations. An unknown clip name also returns `false`, so a typo is visible rather than silent.

Three generations of the clips ship side by side and the server config `comboAnimGeneration`
picks which one `playComboBeat` uses: `1` is the original set, `2` the yaw-scaled twins, `3` the
set posed for Budokai Tenkaichi 3 and authored to fit one mash beat. `playAnimation` takes a full
name, so it can reach any generation regardless of that setting.

A Full-appearance NPC also throws alternating left and right punch clips on its own ordinary melee
attacks, with no script involved. The DMZ wand **Atk** field (or `XenoPixels.setMeleeAnimation`)
replaces that default with a published studio clip; empty restores the punches.

Combat and transform **states** can use a published studio clip (script API v22). Bind per NPC
(persists on the profile) or server-wide (same map as `/xenoanim bind`):

```js
XenoPixels.setStateClip(npc, "TRANSFORM", "ssj3_pose");
XenoPixels.setStateClip(npc, "PUNCH", "my_jab");
XenoPixels.setStateClip(npc, "CHARGE_PUNCH", "my_charge");
XenoPixels.setStateClip(npc, "CHARGE_PUNCH_FIRE", "my_release");
XenoPixels.setStateClip(npc, "CHARGE_KICK", "my_kick_charge");
XenoPixels.setStateClip(npc, "CHARGE_KICK_FIRE", "my_kick_release");
XenoPixels.setStateClip(event.player, "CHARGE_PUNCH", "my_charge");
XenoPixels.bindStateSlot("CHARGE_PUNCH", "my_charge"); // everyone
XenoPixels.playState(npc, "CHARGE_PUNCH");             // play now
XenoPixels.listStateSlots();
XenoPixels.joinNames(XenoPixels.listStateSlots()); // Nashorn: Java arrays have no .join
```

`TRANSFORM` plays for the hold when the NPC (or a player with a bind) changes form.
`PUNCH` is the same store as `setMeleeAnimation`. Charge slots replace the stock DMZ
charge-hold / release poses when that move actually happens. `playState` fires the bound
clip without waiting for the move.

Charged punch / kick (script API v23) is a real hold clock, not only a pose:

```js
XenoPixels.startChargePunch(npc, 24);           // auto-release at 24 ticks
XenoPixels.startChargeKick(npc, 0, 1);          // hold until release; +1 up / -1 down
XenoPixels.releaseCharge(npc);
XenoPixels.getChargePercent(npc);               // 0-100 while holding
XenoPixels.getChargeStyle(npc);                 // "punch" | "kick" | ""
XenoPixels.cancelCharge(npc);
```

`durationTicks <= 0` holds until `releaseCharge` or the server `chargeMaxTicks` cap.
A new start replaces the old hold. Percent is `0–100` against `chargeMaxTicks`;
the cone fires on release (or auto-release) and uses the locked or attack target
when present. `setKiCharge` is unrelated KI technique scaling.

The NPC must already have `setProfile`. Without a profile, start returns `false`.
Combat Brain (`setCombatBrain(npc, true)`) starts the same punch/kick in the melee
band when a strike does not fire. Leave the brain off if the script should own
every charge.

NPC-tab samples: `xenopixels_charge_melee.js` (punch), `xenopixels_charge_kick.js`
(kick + bias), `xenopixels_charge_counter.js` (on `damaged`),
`xenopixels_charge_brain.js` (brain only).

Studio clips on an NPC or a player:

```js
XenoPixels.playClip(npc, "my_jab", 1.0, 40);
XenoPixels.playClip(event.player, "newhakaipose", 1.0, 60, true);
XenoPixels.stopClip(event.player);
```

The clip must be shipped or published with `/xenoanim global push`. `clipDuration(name)` is the
authored length in ticks, or `-1` when the server does not know it. One-shots (`hold` false)
use the same melee packet as Full NPC punches; `hold` true keeps the last frame until
`stopClip`. The NPC needs `setProfile` plus Full appearance (and the player model).

`speed` is clamped to 0.15-4.0. Calling `playAnimation` again before the previous clip finishes
restarts it, so pace the calls rather than firing one every tick.

## Movement moves: vanish, chase, backstep, Z-Burst

These give a scripted NPC the same repositioning moves a player gets from the BT3 combat
bindings. They are server-side and take the NPC plus a target entity.

```js
function timer(event) {
    var npc = event.npc;
    var target = npc.getAttackTarget();
    if (target == null) return;

    XenoPixels.vanishBehind(npc, target);   // teleport to the target's back
    XenoPixels.vanishLeft(npc, target);     // back-left  (player double-tap A)
    XenoPixels.vanishRight(npc, target);    // back-right (player double-tap D)

    XenoPixels.chase(npc, target);          // high-speed dash in from mid range
    XenoPixels.backstep(npc, target);       // step away, still facing them
    XenoPixels.zBurst(npc, target);         // burst step-in
}
```

`vanish(npc, target)` is the same as `vanishBehind`. The raw form `vanish(npc, target, side)`
takes the side as a number and only looks at its sign: negative is back-left, `0` is directly
behind, positive is back-right.

**Every one of these returns a boolean, and `false` is normal.** A move is refused when:

- its cooldown is still running (vanish is 40 ticks, so roughly two seconds),
- the NPC cannot pay the move's energy cost — check `getCurrentEnergy(npc)` first,
- the target is further away than the server's `vanishMaxRange`.

So drive them from the return value rather than assuming they fired:

```js
if (!XenoPixels.vanishRight(npc, target)) {
    XenoPixels.zBurst(npc, target);   // fall back to something with its own cooldown
}
```

Guard state is separate and has no cooldown:

```js
XenoPixels.setGuard(npc, true);
var blocking = XenoPixels.isGuarding(npc);
```

If you would rather the NPC pick these moves on its own, turn on the built-in brain with
`XenoPixels.setCombatBrain(npc, true)` instead of scripting them — it is off by default so a
scripted NPC does exactly what its script says. Missing `BrainVersion` is v1. `setBrainVersion(npc, "v2")`
selects the saga-style stack; look-at `/xenobrain v1|v2` and `/xenopixels npcprofile brain v1|v2`
do the same.

## Complete public method reference

```text
getVersion()
hasProfile(npc)
getProfile(npc)
setProfile(npc, race, strength, strikePower, resistance, vitality, kiPower, energy)
setKiCharge(npc, percent)
setPowerRelease(npc, percent)
setAura(npc, on)
setAuraColor(npc, hex)
setKiColor(npc, hex)
setAuraScale(npc, scale)
setHalo(npc, on)
setAuraRocks(npc, on)
setAuraSparking(npc, on)
setAuraLightning(npc, on)
setSaiyanTail(npc, on)
setTailColor(npc, hex)
clearTailColor(npc)
listFormGroups(race)
listForms(race, group)
listStackGroups()
listStackForms(group)
selectForm(npc, group, form)
selectStack(npc, group, form)
setMastery(npc, group, form, percent)
setStackMastery(npc, group, form, percent)
addTechnique(npc, id)
removeTechnique(npc, id)
listTechniques(npc)
ascend(npc, group, form, ticks)
stack(npc, group, form, ticks)
unstack(npc)
descend(npc, ticks)
descendOne(npc, ticks)
isTransforming(npc)
getAuraStyle(npc, scope, group, form)
setAuraStyle(npc, scope, group, form,
             enabled, primaryColor, primaryType, primaryLayer,
             extraConfigured, extraEnabled, extraColor, extraType, extraLayer,
             lightningConfigured, lightningEnabled, lightningColor)
clearAuraStyle(npc, scope, group, form)
getResolvedAura(npc)
fireTechnique(npc, id, target, durationTicks)
fireTechnique(npc, id, target, durationTicks, hex)
getTechniqueCooldown(npc, id)
isTechniqueReady(npc, id)
clearTechniqueCooldown(npc, id)
vanish(npc, target)
vanish(npc, target, side)
vanishBehind(npc, target)
vanishLeft(npc, target)
vanishRight(npc, target)
chase(npc, target)
meleeHit(npc, target)
meleeHit(npc, target, scale)
backstep(npc, target)
zBurst(npc, target)
setGuard(npc, on)
isGuarding(npc)
setCombatBrain(npc, on)
setBrainVersion(npc, version)
getBrainVersion(npc)
setStateClip(npc, slot, clip)
setStateClip(player, slot, clip)
getStateClip(npc, slot)
clearStateClip(npc, slot)
playState(npc, slot)
listStateSlots()
joinNames(names)
joinNames(names, separator)
bindStateSlot(slot, clip)
startChargePunch(npc, durationTicks)
startChargeKick(npc, durationTicks)
startChargeKick(npc, durationTicks, verticalBias)
releaseCharge(npc)
cancelCharge(npc)
isCharging(npc)
getChargePercent(npc)
getChargeStyle(npc)
playClip(npc, name)
playClip(npc, name, speed)
playClip(npc, name, speed, ticks)
playClip(npc, name, speed, ticks, hold)
playClip(player, name)
playClip(player, name, speed)
playClip(player, name, speed, ticks)
playClip(player, name, speed, ticks, hold)
stopClip(npc)
stopClip(player)
clipDuration(name)
getCurrentEnergy(npc)
getMaxEnergy(npc)
getCurrentStamina(npc)
getMaxStamina(npc)
setCurrentEnergy(npc, value)
setCurrentStamina(npc, value)
getHair(npc)
setHairEnabled(npc, enabled)
setHairCode(npc, code)
setHairColor(npc, color)
getAppearanceMode(npc)
setAppearanceMode(npc, mode)
getAppearanceColors(npc)
setBodyColor(npc, hex)
setBodyColor2(npc, hex)
setBodyColor3(npc, hex)
setEyeColor1(npc, hex)
setEyeColor2(npc, hex)
teleport(npc, x, y, z)
teleportToEntity(npc, target)
teleportToPlayer(npc, player)
playSound(npc, sound, volume, pitch)
playSoundAt(npc, x, y, z, sound, volume, pitch)
playSoundFor(player, sound, volume, pitch)
getChatMessage(event)
setChatMessage(event, message)
cancelChat(event)
isChatCancelled(event)
say(player, message)
broadcast(player, message)
say(npc, message)
```

## Teleport, sound, appearance and chat

```js
// Absolute position, with no vanish cooldown or energy cost.
XenoPixels.teleport(event.npc, 120.5, 64.0, -330.25);
XenoPixels.teleportToEntity(event.npc, event.npc.getAttackTarget());
XenoPixels.teleportToPlayer(event.npc, somePlayer);

// Loud sounds reach every player inside the volume-scaled radius. CustomNPCs' own
// world.playSoundAt always stops at 16 blocks, so it cannot carry a boss roar.
XenoPixels.playSound(event.npc, "minecraft:entity.ender_dragon.death", 4.0, 1.0);
XenoPixels.playSoundAt(event.npc, 100, 70, 100, "minecraft:entity.generic.explode", 2.0, 1.0);
XenoPixels.playSoundFor(somePlayer, "minecraft:ui.toast.challenge_complete", 1.0, 1.0);

// Appearance mode is "OFF", "OVERLAY" or "FULL".
XenoPixels.setAppearanceMode(event.npc, "FULL");
var colors = XenoPixels.getAppearanceColors(event.npc);

XenoPixels.setBodyColor(event.npc, "#4A90D9");
XenoPixels.setBodyColor2(event.npc, "#2C5F91");
XenoPixels.setBodyColor3(event.npc, "#1B3A5C");
XenoPixels.setEyeColor1(event.npc, "#FFFFFF");
XenoPixels.setEyeColor2(event.npc, "#111111");

// getChatMessage is the typed line (raw text, not "<Name> rest").
// setChatMessage rewrites the public line via chat.type.text, not an empty key.
// cancelChat hides the typed line. broadcast sends a new public line.
function chat(event) {
    var raw = XenoPixels.getChatMessage(event);
    if (raw.indexOf("!") === 0) {
        XenoPixels.cancelChat(event);
        return;
    }
    if (raw.indexOf("#") === 0)
        XenoPixels.setChatMessage(event, "[NPC] " + raw.substring(1).trim());
}
XenoPixels.broadcast(event.player, "Public line to every player.");
```

`setAppearanceMode` accepts `OFF`, `OVERLAY` or `FULL` and falls back to `OFF` for an
unrecognized name. Every color setter takes `#RRGGBB`, stores the canonical form, and returns
`false` for a value it cannot parse. The sound methods return `false` for an unknown sound ID, a
non-finite position, or an NPC that is not a live server-side entity. `teleport` keeps the NPC's
current facing; use `vanishBehind` when the point is to arrive behind a target.

## In-game API inspection

These commands inspect the API from the loaded server JAR:

```text
/xenopixels scriptapi globals [page]
/xenopixels scriptapi all [page]
/xenopixels scriptapi search <text>
/xenopixels scriptapi show <class> [page]
/xenopixels scriptapi dump
```

If this guide and the loaded server disagree, trust `XenoPixels.getVersion()` and the
in-game API inspection output from the server that is actually running.
