/**
 * XenoAPI starter for NATIVE Xeno NPCs (not CustomNPCs / My NPCs).
 *
 * The same greeter as xenopixels_say_greeter.js, written against the typed XenoAPI
 * (xenoapi.npcs.api) instead of the native wrappers. Both views share one NPC: data
 * written through XenoAPI is what npc.getStoreddata() reads, and a XenoAPI timer fires
 * this script's timer hook.
 *
 * 1. Wand a native Xeno NPC -> Scripts tab.
 * 2. Paste this file. Enable = Yes. Save.
 *
 * Bindings used: XenoAPI (the registered NpcAPI), XenoPixels (conversion + extras).
 *   XenoPixels.toXeno(event.npc)   -> ICustomNpc over the same NPC
 *   XenoPixels.fromXeno(apiNpc)    -> back to the wrapper every other XenoPixels call takes
 *
 * Unsupported XenoAPI methods throw with their name; see docs/native-xenoapi-adapters.md.
 */
"use strict";

var T_FOLLOWUP = 9401;

function api(event) {
    if (typeof XenoAPI === "undefined" || XenoAPI == null) return null;
    return XenoPixels.toXeno(event.npc);
}

function init(event) {
    var npc = api(event);
    if (npc == null) {
        event.npc.say("XenoAPI is not available here (native Xeno NPCs only).");
        return;
    }
    var data = npc.getStoreddata();
    data.put("xeno_clicks", 0);
    data.put("xeno_talking", 0);
    npc.say("XenoAPI online - home at " + npc.getHomeX() + ", " + npc.getHomeY() + ", " + npc.getHomeZ() + ".");
}

function interact(event) {
    var npc = api(event);
    if (npc == null || event.player == null) return;
    var player = XenoPixels.toXeno(event.player);

    var data = npc.getStoreddata();
    if (Number(data.get("xeno_talking")) === 1) {
        npc.sayTo(player, "Hold on, I am still talking.");
        return;
    }

    var clicks = Number(data.get("xeno_clicks")) + 1;
    data.put("xeno_clicks", clicks);
    data.put("xeno_talking", 1);

    npc.say("Hey, " + player.getDisplayName() + ". That is click " + clicks + ".");
    npc.getTimers().forceStart(T_FOLLOWUP, 40, false);

    // The typed and native views are the same NPC: the XenoPixels extras still apply.
    XenoPixels.setAura(XenoPixels.fromXeno(npc), clicks % 2 === 1);
}

function timer(event) {
    if (event.id != T_FOLLOWUP) return;
    var npc = api(event);
    if (npc == null) return;
    npc.say("Come back if you want to hear it again.");
    npc.getStoreddata().put("xeno_talking", 0);
}

function died(event) {
    var npc = api(event);
    if (npc == null) return;
    npc.getStoreddata().put("xeno_clicks", 0);
    npc.getStoreddata().put("xeno_talking", 0);
}
