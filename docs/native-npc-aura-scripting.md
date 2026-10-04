# Native NPC aura sizing and script functions

**Version:** 0.5.10-1.21.1
**Date:** 2026-10-05

The HD aura variants, including v4's flame, inner shell and lean plume, now use the NPC's
display/profile size and explicit aura scale. The scale is applied once, with live emitters
updating it as appearance changes. Native fallback models use the same size rule as the normal
NPC aura. Full DragonMineZ appearances apply their explicit NPC size after the power-growth cap,
so a large display size is not clamped down to the player limit. The shared DMZ aura hook also
loads for native NPCs without CustomNPCs or MyNPCs.

Native script bindings now include display, stats, AI, advanced settings, inventory, role and job
views backed by the existing XenoAPI adapters, plus direct size, aura, sound and animation calls:

```javascript
function init(event) {
    var npc = event.npc;
    npc.getDisplay().setSize(10); // size 5 is normal; 10 is twice normal
    npc.setAuraScale(2);         // aura multiplier on top of body size
    npc.setAura(true);
}
```

`npc.getSize()`, `npc.setSize(size)`, `npc.getAuraScale()`, `npc.playSound(id, volume, pitch)` and
`npc.playAnimation(name[, speed])` use the native owners' validation and synchronization. The
Functions panel derives its calls from actual bound classes and includes the existing
`XenoPixels` effects, forms, aura-style and animation APIs. Unsupported sub-view capabilities
still report their existing named errors; this does not implement every CustomNPCs event or
every enum shown in the reference screenshots. A late script-fetch reply now preserves local
edits instead of replacing a draft while the Functions panel or editor rebuilds.
