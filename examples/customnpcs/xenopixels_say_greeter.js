/**
 * Co-owner starter: NPC talks on right-click.
 *
 * 1. Wand the NPC → Scripts tab.
 * 2. Paste this entire file. Enable = Yes. Save.
 * 3. If no bubble appears: /xenopixels npcsay on
 *
 * Speech is npc.say() (CustomNPCs / My NPCs). XenoPixels.say(npc, text) is the
 * same bubble and honors /xenopixels npcsay. XenoPixels.say(player, text) is a
 * private system line, not a bubble.
 */
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
