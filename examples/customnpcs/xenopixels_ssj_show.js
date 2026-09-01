/**
 * Simple SSJ showcase. Paste into the NPC Script tab.
 * Look at the NPC once as OP:  /xenopixels genhaircode black apply
 */
"use strict";

var GROUP = "supersaiyan";
var SSJ1 = "supersaiyanmastered";
var SSJ2 = "supersaiyan2";
var SSJ3 = "supersaiyan3";
var HOLD = 40;
var T_SSJ1 = 9201;
var T_SSJ2 = 9202;
var T_SSJ3 = 9203;
var T_BLAST = 9204;
var T_BASE = 9205;

function init(event) {
    var n = event.npc;
    if (typeof XenoPixels === "undefined") {
        n.say("XenoPixels is not loaded");
        return;
    }
    XenoPixels.setProfile(n, "saiyan", 40, 40, 30, 40, 50, 40);
    XenoPixels.setHairEnabled(n, true);
    XenoPixels.setHairColor(n, "black");
    XenoPixels.setAuraColor(n, "FFAA00");
    XenoPixels.setAura(n, true);
    XenoPixels.setMastery(n, GROUP, SSJ1, 100);
    XenoPixels.setMastery(n, GROUP, SSJ2, 100);
    XenoPixels.setMastery(n, GROUP, SSJ3, 100);
    n.executeCommand("xenopixels npcprofile ai enable");
    n.getStoreddata().put("xeno_show", 0);
}

function interact(event) {
    startShow(event.npc);
}

function damaged(event) {
    startShow(event.npc);
}

function startShow(n) {
    if (typeof XenoPixels === "undefined") return;
    if (Number(n.getStoreddata().get("xeno_show")) === 1) return;
    n.getStoreddata().put("xeno_show", 1);
    n.say("This power... it has been sleeping.");
    n.getTimers().forceStart(T_SSJ1, 20, false);
}

function timer(event) {
    var n = event.npc;
    if (event.id == T_SSJ1) {
        n.say("HAAAAA! Super Saiyan!");
        XenoPixels.setAura(n, true);
        XenoPixels.ascend(n, GROUP, SSJ1, HOLD);
        n.getTimers().forceStart(T_SSJ2, HOLD + 30, false);
    } else if (event.id == T_SSJ2) {
        n.say("Still not enough... Super Saiyan 2!");
        XenoPixels.ascend(n, GROUP, SSJ2, HOLD);
        n.getTimers().forceStart(T_SSJ3, HOLD + 30, false);
    } else if (event.id == T_SSJ3) {
        n.say("This is it... Super Saiyan 3!!");
        XenoPixels.ascend(n, GROUP, SSJ3, HOLD);
        n.getTimers().forceStart(T_BLAST, HOLD + 25, false);
    } else if (event.id == T_BLAST) {
        n.say("Take this!");
        XenoPixels.fireTechnique(n, "kiblast", n.getAttackTarget(), 40);
        n.executeCommand("xenopixels npcprofile kiattack kiblast color FFAA00");
        n.getTimers().forceStart(T_BASE, 30, false);
    } else if (event.id == T_BASE) {
        n.say("Hahh... back to base.");
        XenoPixels.descend(n, 20);
        n.getStoreddata().put("xeno_show", 0);
    }
}

function died(event) {
    if (typeof XenoPixels === "undefined") return;
    XenoPixels.descend(event.npc, 20);
    event.npc.getStoreddata().put("xeno_show", 0);
}
