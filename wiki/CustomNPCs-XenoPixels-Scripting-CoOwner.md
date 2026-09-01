# XenoPixels CustomNPC scripting guide for server co-owners

This guide documents the server-side `XenoPixels` global implemented by
`NpcXenoScriptApi` version 4. It is intended for CustomNPCs JavaScript hooks such as
`init(event)`, `timer(event)`, `damaged(event)` and `died(event)`.

## Quick safety rules

- Pass a live CustomNPC, normally `event.npc`. There is no automatic global named `npc`.
- Most setters save the NPC profile immediately and return `true`. Invalid IDs or colors can
  return `false`.
- Use real DMZ IDs. Discover them with `listFormGroups`, `listForms`, `listStackGroups` and
  `listStackForms` instead of guessing.
- Colors may use `#RRGGBB`. Examples below use that format.
- Transform durations are ticks; 20 ticks are approximately one second.
- Check the installed bridge with `XenoPixels.getVersion()`; this document expects `"4"`.

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
```

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
