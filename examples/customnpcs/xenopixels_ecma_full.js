/**
 * XenoPixels Network — CustomNPCs ECMA script (Nashorn / Graal).
 *
 * Paste this entire file into the NPC Script tab.
 * Enable command blocks (executeCommand). Aggressive/Retaliate stay on the AI tab.
 *
 * Hair MESH is too long to paste here. After this NPC spawns, look at it as OP:
 *   /xenopixels genhaircode white apply
 * Color is set in-script (white). Do not paste the giant code into the DMZ GUI.
 */
"use strict";

var RACE = "saiyan";
var SSJ_GROUP = "supersaiyan";
var SSJ = ["supersaiyanmastered", "supersaiyan2", "supersaiyan3"];
var GOD_GROUP = "xenopixels_gods_forms";
var GOD_FORM = "ssb";

var HAIR_COLOR = "white";
var HAIR_CODE = "";

var KI_HEX = "FF3300";
var AURA_HEX = "FFAA00";
var AURA_SCALE = 2.0;
var CHARGE = 100;
var TRANSFORM_TICKS = 40;

var T_COMBAT = 9101;
var T_AFTER_TRANSFORM = 9102;

var TECHS = ["kamehameha", "masenko", "galick_gun", "final_flash", "sokidan"];

function init(event) {
    var npc = event.npc;
    if (typeof XenoPixels === "undefined") {
        npc.say("XenoPixels global missing — is xenopixelsmod on the server?");
        return;
    }

    npc.say("XenoPixels ECMA v" + XenoPixels.getVersion());

    XenoPixels.setProfile(npc, RACE, 45, 45, 35, 45, 55, 45);
    XenoPixels.setKiColor(npc, KI_HEX);
    XenoPixels.setKiCharge(npc, CHARGE);
    XenoPixels.setAuraColor(npc, AURA_HEX);
    XenoPixels.setAuraScale(npc, AURA_SCALE);
    XenoPixels.setAura(npc, true);

    XenoPixels.setHairEnabled(npc, true);
    XenoPixels.setHairColor(npc, HAIR_COLOR);
    if (HAIR_CODE && HAIR_CODE.length > 16) {
        XenoPixels.setHairCode(npc, HAIR_CODE);
    }

    var i;
    for (i = 0; i < SSJ.length; i++) {
        XenoPixels.setMastery(npc, SSJ_GROUP, SSJ[i], 100);
    }
    XenoPixels.setMastery(npc, GOD_GROUP, GOD_FORM, 100);

    XenoPixels.addTechnique(npc, "taiyoken");
    XenoPixels.removeTechnique(npc, "taiyoken");
    for (i = 0; i < TECHS.length; i++) {
        XenoPixels.addTechnique(npc, TECHS[i]);
    }
    XenoPixels.clearTechniqueCooldown(npc, "kamehameha");

    cmd(npc, "xenopixels npcprofile ai enable");
    cmd(npc, "xenopixels npcprofile aura on");
    cmd(npc, "xenopixels npcprofile aura color " + AURA_HEX);
    cmd(npc, "xenopixels npcprofile aura scale " + AURA_SCALE);
    cmd(npc, "xenopixels npcprofile color " + KI_HEX);
    cmd(npc, "xenopixels npcprofile charge " + CHARGE);
    cmd(npc, "xenopixels npcprofile hair on");
    cmd(npc, "xenopixels npcprofile hair color " + HAIR_COLOR);
    cmd(npc, "xenopixels npcprofile mastery " + SSJ_GROUP + " " + SSJ[0] + " 100");
    cmd(npc, "xenopixels npcprofile tech add kamehameha");

    var state = npc.getStoreddata();
    state.put("xeno_stage", 0);
    state.put("xeno_busy", 0);
    npc.getTimers().forceStart(T_COMBAT, 40, true);

    var hair = XenoPixels.getHair(npc);
    if (!mapGet(hair, "code") || String(mapGet(hair, "code")).length < 16) {
        npc.say("Look at me and run: /xenopixels genhaircode white apply");
    }
}

function damaged(event) {
    var npc = event.npc;
    if (!xp() || busy(npc)) return;

    var hp = npc.getHealth() / npc.getMaxHealth();
    var stage = num(npc, "xeno_stage");
    var next = -1;
    var group = SSJ_GROUP;
    var form = "";

    if (hp <= 0.18 && stage < 4) {
        next = 4; group = GOD_GROUP; form = GOD_FORM;
    } else if (hp <= 0.32 && stage < 3) {
        next = 3; form = SSJ[2];
    } else if (hp <= 0.52 && stage < 2) {
        next = 2; form = SSJ[1];
    } else if (hp <= 0.82 && stage < 1) {
        next = 1; form = SSJ[0];
    }

    if (next < 0) {
        cmd(npc, "xenopixels npcprofile kiattack kiblast color " + KI_HEX);
        return;
    }

    markBusy(npc, TRANSFORM_TICKS + 6);
    npc.getStoreddata().put("xeno_stage", next);
    XenoPixels.setAura(npc, true);
    if (!XenoPixels.ascend(npc, group, form, TRANSFORM_TICKS)) {
        cmd(npc, "xenopixels npcprofile transform " + group + " " + form + " " + TRANSFORM_TICKS);
    }
    npc.say("Haaaa! " + group + "/" + form);
    npc.getTimers().forceStart(T_AFTER_TRANSFORM, TRANSFORM_TICKS + 2, false);
}

function meleeAttack(event) {
    strike(event.npc, event.target);
}

function rangedLaunched(event) {
    strike(event.npc, event.target);
}

function strike(npc, target) {
    if (!xp() || busy(npc)) return;
    var aim = target != null ? target : npc.getAttackTarget();
    if (aim == null) return;
    if (XenoPixels.isTechniqueReady(npc, "masenko")) {
        XenoPixels.fireTechnique(npc, "masenko", aim, 40);
        return;
    }
    cmd(npc, "xenopixels npcprofile kiattack kiblast color " + KI_HEX);
}

function target(event) {
    if (!xp()) return;
    XenoPixels.setAura(event.npc, true);
}

function timer(event) {
    var npc = event.npc;
    if (!xp()) return;

    if (event.id == T_AFTER_TRANSFORM) {
        var aim = npc.getAttackTarget();
        if (aim != null && XenoPixels.isTechniqueReady(npc, "kamehameha")) {
            XenoPixels.fireTechnique(npc, "kamehameha", aim, 80);
        } else {
            cmd(npc, "xenopixels npcprofile kiattack kiwave color " + KI_HEX);
        }
        return;
    }

    if (event.id != T_COMBAT || busy(npc)) return;
    var target = npc.getAttackTarget();
    if (target == null) return;

    var stage = num(npc, "xeno_stage");
    var id = pickTech(npc, stage);
    if (id == null) {
        cmd(npc, "xenopixels npcprofile kiattack kiblast color " + KI_HEX);
        return;
    }
    XenoPixels.fireTechnique(npc, id, target, 60);
}

function interact(event) {
    var npc = event.npc;
    if (!xp()) {
        npc.say("XenoPixels missing");
        return;
    }

    if (event.player != null && event.player.isSneaking()) {
        if (num(npc, "xeno_stage") > 0) {
            XenoPixels.descendOne(npc, TRANSFORM_TICKS);
            npc.getStoreddata().put("xeno_stage", Math.max(0, num(npc, "xeno_stage") - 1));
            npc.say("Descended one form");
        } else {
            XenoPixels.descend(npc, 20);
            npc.say("Base form");
        }
        return;
    }

    var p = XenoPixels.getProfile(npc);
    var hair = XenoPixels.getHair(npc);
    var techs = jsList(XenoPixels.listTechniques(npc));
    npc.say("v" + XenoPixels.getVersion()
        + " profile=" + XenoPixels.hasProfile(npc)
        + " race=" + mapGet(p, "race")
        + " form=" + mapGet(p, "formGroup") + "/" + mapGet(p, "form")
        + " aura=" + mapGet(p, "auraOn")
        + " stage=" + num(npc, "xeno_stage"));
    npc.say("hair on=" + mapGet(hair, "enabled")
        + " color=" + mapGet(hair, "color")
        + " code=" + (String(mapGet(hair, "code") || "").length > 16 ? "set" : "NEED /xenopixels genhaircode white apply"));
    npc.say("techs " + techs.join(",")
        + " kameReady=" + XenoPixels.isTechniqueReady(npc, "kamehameha")
        + " kameCd=" + XenoPixels.getTechniqueCooldown(npc, "kamehameha"));
}

function died(event) {
    resetForm(event.npc);
}

function kill(event) {
    resetForm(event.npc);
}

function resetForm(npc) {
    if (!xp() || npc == null) return;
    XenoPixels.descend(npc, 20);
    cmd(npc, "xenopixels npcprofile descend");
    npc.getStoreddata().put("xeno_stage", 0);
    npc.getStoreddata().put("xeno_busy", 0);
}

function pickTech(npc, stage) {
    var order;
    if (stage >= 4) order = ["final_flash", "sokidan", "kamehameha", "galick_gun"];
    else if (stage >= 2) order = ["kamehameha", "galick_gun", "masenko"];
    else order = ["masenko", "kamehameha", "sokidan"];
    var i;
    for (i = 0; i < order.length; i++) {
        if (XenoPixels.isTechniqueReady(npc, order[i])) return order[i];
    }
    return null;
}

function xp() {
    return typeof XenoPixels !== "undefined";
}

function cmd(npc, line) {
    try {
        npc.executeCommand(line);
    } catch (e) {}
}

function num(npc, key) {
    var v = npc.getStoreddata().get(key);
    var n = Number(v);
    return isNaN(n) ? 0 : n;
}

function busy(npc) {
    return num(npc, "xeno_busy") > npc.getWorld().getTotalTime();
}

function markBusy(npc, ticks) {
    npc.getStoreddata().put("xeno_busy", npc.getWorld().getTotalTime() + ticks);
}

function mapGet(m, key) {
    if (m == null) return "";
    if (typeof m.get === "function") return m.get(key);
    return m[key];
}

function jsList(javaList) {
    var out = [];
    if (javaList == null) return out;
    if (typeof javaList.size === "function") {
        var i;
        for (i = 0; i < javaList.size(); i++) out.push(String(javaList.get(i)));
        return out;
    }
    var j;
    for (j = 0; j < javaList.length; j++) out.push(String(javaList[j]));
    return out;
}
