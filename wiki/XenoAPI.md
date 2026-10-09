# XenoAPI — scripting and addons for native Xeno NPCs

**Updated:** 2026-09-28 · XenoPixels `0.5.0` · script API version `28` · Minecraft 1.21.1 / NeoForge 21.1.248

XenoAPI is the CustomNPCs-style scripting API (`xenoapi.npcs.api`, a 1.21.1 port of
Noppes/CustomNPCsAPI) that **native Xeno NPCs** implement. It needs no CustomNPCs or MyNPCs.
Scripts get it as the `XenoAPI` global; Java addons get it from `NpcAPI.Instance()`.

Alongside it sits the `XenoPixels` global: the DMZ-side API (forms, aura, techniques, skills,
bubbles, every editor setting). Both are available in every native script.

> Parity is not complete: 99 of 437 XenoAPI contract methods throw an error that names the
> method. The full list is in `docs/native-xenoapi-adapters.md` in the mod repository.

---

## Where scripts go

| Kind | Where to write it | Runs for | Globals |
| --- | --- | --- | --- |
| **NPC script** | NPC editor → Scripts, or the scripter tool on an NPC | that NPC | `npc`, `world`, `XenoPixels`, `XenoAPI`, `log`, `script`, `scriptName`, `event` |
| **Player script** | Scripter tool → right-click the **air** → *Player Scripts* | every online player (one scope per player) | `player`, `world`, `XenoPixels`, `XenoAPI`, `log` |
| **Forge script** | Scripter tool → right-click the air → *Forge Scripts* | the whole server | `world`, `XenoPixels`, `XenoAPI`, `log` |
| NPC script library | Scripter tool → right-click the air → *NPC Script Library* | shared scripts NPCs load | as NPC script |

The air menu is the blue DMZ-style **Scripts** screen. Each entry shows how many tabs it has;
press **+** in the editor to make a new tab. Saving a script needs operator level 4.

Saving a **player** script runs its `init` hook for everyone online straight away. `login` still
waits for a real login. Saving a **Forge** script reloads it and runs its `init`.

The sandbox is closed: `Java.type`, `getClass` and raw Minecraft objects are not reachable.
`getMCEntity()` and similar raw handles throw on purpose.

---

## Hooks

### NPC scripts

`init`, `tick` (every 10 ticks), `interact`, `damaged`, `died`, `kill`, `target`, `targetLost`,
`collide`, `meleeAttack`, `rangedLaunched`, `rangedAttack`, `timer`, `dialog`, `dialogOption`.

Existing hooks receive the classic event (`event.npc`, `event.player`, `event.damage`,
`event.setCanceled(true)`) **plus** the typed XenoAPI event as `event.xeno`.

### Player scripts

`init`, `tick` (every 10 ticks = 0.5 s), `interact`, `attack`, `broken`, `toss`, `pickedUp`,
`containerOpen`, `containerClosed`, `damagedEntity`, `rangedLaunched`, `died`, `kill`,
`damaged`, `timer`, `login`, `logout`, `levelUp`, `chat`.

- `init`, `login`, `logout` and `chat` get the classic event plus `event.xeno`.
- The other hooks get the **typed** XenoAPI event itself (`event.player` is an `IPlayer`).

Once a second, from `tick`:

```js
var halfSeconds = 0;
function tick(event) {
    if (++halfSeconds % 2 !== 0) return;
    XenoPixels.bubble(event.player, "Online " + (halfSeconds / 2) + "s", "gold");
}
```

### Forge scripts

| Hook | When | `cancel()` |
| --- | --- | --- |
| `init` | tab loaded or saved | – |
| `serverTick` | every server tick | – |
| `playerLogin`, `playerLogout`, `playerRespawn` | as named | – |
| `playerChangedDimension` | `event.message` = new dimension id | – |
| `livingDeath` | any living entity dies; `event.source` = killer | yes |
| `livingHurt` | any living entity is hurt; `event.damage` is writable | yes |
| `entityJoin` | a new entity is added (not chunk loads) | yes |
| `blockBreak`, `blockPlace` | by a player; `event.block` = block id | yes |
| `serverChat` | a chat line; `event.message` = text | yes* |
| `rightClickBlock`, `rightClickItem`, `leftClickBlock`, `entityInteract` | player interactions | yes |
| `explosion` | an explosion starts | yes |

\* Chat that arrives off the server thread is seen one moment later and cannot be cancelled.

The event has `player`, `entity`, `source`, `world`, `x`, `y`, `z`, `block`, `item`,
`message`, `damage` (plus getters), `cancel()`, `setCanceled(b)`, `isCancelable()`. It only
ever holds safe wrappers, never the raw NeoForge event.

A hook that triggers itself (a `livingHurt` that hurts) stops at 3 levels deep. A tab that
fails 20 times in a row is paused until you save it again.

```js
function blockBreak(event) {
    if (event.block == "minecraft:diamond_ore" && Math.abs(event.x) < 64 && Math.abs(event.z) < 64) {
        event.player.message("Spawn diamonds are protected.");
        event.cancel();
    }
}
function livingHurt(event) {
    if (event.message == "fall") event.setDamage(event.damage / 2);
}
```

---

## XenoAPI in scripts

```js
function interact(event) {
    var api = XenoPixels.toXeno(event.npc);          // ICustomNpc over the same NPC
    api.getStoreddata().put("visits", (api.getStoreddata().get("visits") || 0) + 1);
    api.sayTo(event.player, "Visit #" + api.getStoreddata().get("visits"));
    api.getTimers().forceStart(1, 20, false);         // fires timer(event) in 1 s
}
function timer(event) {
    if (event.id == 1) XenoPixels.say(event.npc, "One second later.");
}
```

- `XenoPixels.toXeno(wrapper)` turns `npc` / `player` into the XenoAPI object.
- `XenoPixels.fromXeno(apiEntity)` goes back, so XenoAPI entities can use every `XenoPixels` call.
- `XenoAPI` is the registered `NpcAPI`: `XenoAPI.getIWorlds()`, `XenoAPI.getIPos(x, y, z)`,
  `XenoAPI.executeCommand(world, "...")` (level 2, output returned, not sent to chat).

Differences from CustomNPCs worth knowing:

- Quest ids are the native quest slots.
- `ITimers.start` throws when the timer already exists, when ticks are outside 1–1,200,000, or
  when more than 64 timers are used. `forceStart` replaces.
- `IItemStack.getNbt` is unsupported (1.21 uses components); `getItemNbt` returns a copy.
- `IWorld.setBlock(..., meta)` ignores `meta`.

---

## XenoPixels additions (API v28)

`XenoPixels.getVersion()` returns `"28"` on native, MyNPCs and CustomNPCs.

### Speech bubbles for anyone

| Call | Result |
| --- | --- |
| `XenoPixels.say(player, text)` | private chat line to that player (unchanged) |
| `XenoPixels.broadcast(player, text)` | public chat line |
| `XenoPixels.bubble(entity, text)` | speech bubble over any entity, **players too**; everyone nearby sees it |
| `XenoPixels.bubble(entity, text, color)` | color: `blue`, `gold`, `green`, `red` |
| `XenoPixels.bubble(entity, text, color, shape)` | shape: `rounded`, `thought`, `shout`, `banner` |
| `player.say(text[, color[, shape]])` | native only: the same bubble from the wrapper |

`bubble` works on native, MyNPCs and CustomNPCs scripts. In native typed hooks it accepts the
XenoAPI `IPlayer` directly. Press F5 to see your own bubble.

### Every editor setting by key (native NPCs)

Every setting the NPC editor saves — all DMZ tabs, AI, sounds, bubbles, jobs — can be read, set
or toggled by its key:

```js
log(XenoPixels.listDmz(npc).join(", "));          // print every key once
XenoPixels.setDmz(npc, "AuraOn", true);
XenoPixels.setDmz(npc, "AuraScale", 1.5);
XenoPixels.setDmz(npc, "DmzAppearance.SaiyanTail", true);   // dots reach nested settings
XenoPixels.toggleDmz(npc, "AuraLightning");
var on = XenoPixels.getDmz(npc, "BrainKiBlast");
XenoPixels.setNpcSetting(npc, "AiCanSwim", false);   // same calls, NPC-wide name
```

- Keys are case-insensitive. A value keeps its stored type; `setDmz(npc, "Strength", "lots")`
  returns `false`.
- Unknown keys and a few internal ones (`Schema`, `DmzStatSnapshot`) are refused.
- The editor's limits still apply, and an open editor shows the new values.

### Skills and Ki Sense

```js
XenoPixels.setSkill(npc, "fly", true, 3);
XenoPixels.setKiSense(npc, true);              // the Ki Sense skill
XenoPixels.setKiSenseLockOn(npc, true);        // lock on to whoever hits it
if (XenoPixels.isKiSenseLockedOn(npc, event.target)) npc.say("I can sense you.");
```

Also: `isKiSenseOn`, `isKiSenseLockOn`, `setSkillLevel`, `isSkillOn`, `getSkillLevel`,
`removeSkill`, `clearSkills`, `listSkills`.

### Identity, home and respawn (native NPCs)

`setDisplayName`, `setTitle`, `setFaction`, `setRole`, `getRole`, `setHome(x, y, z)`,
`setLeashRadius`, `setRespawn(on)`, `setRespawnDelay(ticks)`.

---

## Colored names and titles

The title now shows on its own line **under** the NPC's name, and the nameplate sits higher so
tall DMZ hair no longer covers it. Colors go straight into the text, in the editor or a script:

| Code | Meaning |
| --- | --- |
| `&0`–`&9`, `&a`–`&f` | colors (`&6` gold, `&b` aqua, `&c` red …) |
| `&l` `&o` `&n` `&m` `&k` | bold, italic, underline, strike, obfuscated |
| `&r` | reset |
| `&#RRGGBB` | any hex color |
| `§` | works in place of `&` |

```js
XenoPixels.setDisplayName(npc, "&6&lGoku");
XenoPixels.setTitle(npc, "&#55EFFFEarth's Hero");
```

Codes are lower-case only after `&`, so text like "R&D" stays as written. Chat, bubbles and the
Nearby list show the name without codes.

---

## Seeing script errors

- An NPC script error is posted in red chat — `[Xeno NPC script] Name — hook: error` — to
  operators (level 2+) within 64 blocks, at most once every 5 seconds per NPC.
- The **Scripts** screen, from the scripter tool **or** the NPC editor, shows the NPC's recent
  prints and errors. It shows what was there when you opened it; reopen it to refresh.
- Player and Forge script errors go to the server log (`Player script …` / `Forge script …`).
- MyNPCs / CustomNPCs scripts report errors in that mod's own script console.

---

## Java addons

```java
if (NpcAPI.IsAvailable()) {
    NpcAPI api = NpcAPI.Instance();
    api.events().register(new MyListener());   // typed events: NpcEvent.*, PlayerEvent.*
}
```

```java
public class MyListener {
    @SubscribeEvent
    public void damaged(NpcEvent.DamagedEvent event) {
        if (event.damage > 20) event.damage = 20;   // runs after the scripts; your value wins
    }
}
```

Order for each occurrence: scripts first, then Java listeners, then the game applies the final
cancel and damage once. A typed event is only built when a script defines the hook or a listener
is registered. Mutations must happen on the server thread; objects from another API
implementation are refused.

An external addon depends only on XenoAPI and the public `net.bullettrain.xenopixelsmod.api`
package. The example addon is `examples/xenopixels-api-addon`.

---

## Examples

In `examples/customnpcs/`:

| File | Kind | Shows |
| --- | --- | --- |
| `xenoapi_native_greeter.js` | NPC | typed XenoAPI, stored data, `sayTo`, timers, home |
| `xenoapi_guard_patrol.js` | NPC | `getClosestEntity`, `setAttackTarget`, navigation |
| `xenoapi_npc_settings.js` | NPC | `setDmz`/`toggleDmz`/`listDmz`, skills, Ki Sense, identity |
| `xenoapi_player_events.js` | Player | typed `broken`, `damaged`, `toss`, `levelUp`, timers |
| `xenoapi_player_bubbles.js` | Player | `XenoPixels.bubble` on join, chat, death, level up |
| `xenoapi_forge_events.js` | Forge | cancel a break, scale damage, announce kills, stop explosions |

The 22 `xenopixels_*.js` files run on CustomNPCs, MyNPCs **and** native NPCs; see
[CustomNPCs scripting (co-owner)](CustomNPCs-XenoPixels-Scripting-CoOwner).

---

## Missile guidance V3 (server setting)

`/xenoguidance system v3` keeps V1's flight controller, HUD and planner, and flies ship and tube
missiles with guidance that keeps correcting from the missile's live position. A **Pure
ballistic** shot cuts its motor after the boost but still steers onto the target while it
coasts, which V1 did not do. `/xenoguidance system v1` switches back; missiles already flying
keep the version they launched with. Details: `docs/guidance-v3.md` in the mod repository.

## Not there yet

Key presses, empty-hand clicks in the air, quest/dialog/faction events, custom GUIs, scripted
blocks and items are planned but not posted yet. Calling an unsupported XenoAPI method throws an
error that names it.
