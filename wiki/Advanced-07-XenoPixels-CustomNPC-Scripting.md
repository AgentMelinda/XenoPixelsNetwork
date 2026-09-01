<div dir="rtl">

# מתקדם 07 — CustomNPCs ו־XenoPixels Scripting API

הגשר מופעל רק כאשר `customnpcs` מותקן. בגרסת CustomNPCs זו הוא מוסיף לכל סקריפט גלובלי
אחד בשם `XenoPixels`; ה־API קורא ל־NPC החי דרך `event.npc.getMCEntity()` ואינו משנה את
מנגנון ה־Aggressive/Retaliate של CustomNPCs.

## בדיקת ה־API המותקן

הפקודות משקפות את ה־JAR שנטען בפועל, ולכן אינן מבטיחות שמות שאינם קיימים:

```text
/xenopixels scriptapi globals [page]
/xenopixels scriptapi all [page]
/xenopixels scriptapi search <text>
/xenopixels scriptapi show <class> [page]
/xenopixels scriptapi dump       # OP; logs/xenopixels-customnpcs-script-api.md
```

## דוגמה קצרה

```js
function damaged(event) {
    if (XenoPixels.ascend(event.npc, "supersaiyan", "supersaiyanmastered", 40)) {
        event.npc.getTimers().forceStart(9101, 42, false);
    }
}
function timer(event) {
    if (event.id == 9101)
        XenoPixels.fireTechnique(event.npc, "kamehameha", event.npc.getAttackTarget(), 60);
}
```

הקירור של טכניקות מוכרות מחושב לפי `KiAttackData.getActualCooldown()` וגורם הטעינה של
ה־NPC. `isTechniqueReady` ו־`getTechniqueCooldown` זמינים לסקריפט; ירייה בזמן קירור נדחית.

הערכים הקבועים של השיער נשמרים ב־NBT של ה־NPC ומסונכרנים גם ל־CustomModelData של
CNPC-Gecko. שינוי דרך Model Editor או דרך `setHairCode`, `setHairColor`, `setHairEnabled`
נשאר לאחר שמירה, טעינת צ'אנק ו־respawn.

הקבצים המלאים נמצאים ב־`examples/customnpcs/` (`xenopixels_full_saiyan.js` is the complete fighter).

## Aura, halo, tails and stack forms (API v4)

The NPC bridge now exposes the same state used by the DMZ wand screens:

```js
// Independent persistent effects
XenoPixels.setHalo(event.npc, true);
XenoPixels.setAura(event.npc, true);
XenoPixels.setAuraRocks(event.npc, false);
XenoPixels.setAuraSparking(event.npc, true);
XenoPixels.setAuraLightning(event.npc, true);

// Saiyan tail visibility and color. An empty/cleared color inherits the
// DMZ race/body color, including active normal-form and stack-form colors.
// This applies to Saiyan, Cell/Bio-Android and Frieza/Frost Demon tails.
XenoPixels.setSaiyanTail(event.npc, true);
XenoPixels.setTailColor(event.npc, "#663311");
XenoPixels.clearTailColor(event.npc);

// Discover and use live DMZ stack configurations (for example Kaioken)
var groups = XenoPixels.listStackGroups();
var forms = XenoPixels.listStackForms("kaioken");
XenoPixels.setStackMastery(event.npc, "kaioken", "x20", 100);
XenoPixels.selectStack(event.npc, "kaioken", "x20");
XenoPixels.stack(event.npc, "kaioken", "x20", 40);
// XenoPixels.unstack(event.npc);

// scope is "base", "form", or "stack". Empty color/type and layer -1 inherit DMZ.
XenoPixels.setAuraStyle(event.npc, "stack", "kaioken", "x20",
    true, "#CC0000", "kakarot", 1,
    true, true, "#FF6600", "kakarot", 2,
    true, true, "#FFCCCC");
```

`getAuraStyle(...)` returns the saved override, while `getResolvedAura(npc)` returns the
effective ordered layers and effect state after base, normal-form, and stack-form precedence.
`getProfile(npc)` now also includes selected/active normal and stack forms, halo, aura color,
scale, Saiyan-tail visibility and color, and each independent effect toggle. `listFormGroups`,
`listForms`, `selectForm`, `selectStack`, `isTransforming`, and `clearAuraStyle` are also
available.

<div dir="ltr">

⬅️ [Advanced 06 — Forge Events](Advanced-06-Forge-Events) · [Kaupenjoe NeoForge tutorial branches](https://github.com/Tutorials-By-Kaupenjoe/NeoForge-Tutorial-1.21.X)

</div>
</div>
