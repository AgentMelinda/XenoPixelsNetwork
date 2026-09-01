// =============================================================================
// XenoPixels + CustomNPCs — full saiyan fighter
// Paste this into the NPC Script tab (Init / Update / Interact / Damaged / …).
//
// Requirements:
//   - Command blocks enabled (executeCommand)
//   - customnpcs + xenopixelsmod on the server
//   - Hair mesh: as OP run  /xenopixels genhaircode white
//     click-copy Code, paste it into HAIR_CODE below (quotes). Color is set in-script.
// =============================================================================

var RACE = "saiyan";
var GROUP = "supersaiyan";
var FORMS = ["supersaiyanmastered", "supersaiyan2", "supersaiyan3"];
var GOD_GROUP = "xenopixels_gods_forms";
var GOD_FORM = "ssb";

var HAIR_COLOR = "white";
var HAIR_CODE = "";

var KI_COLOR = "FF3300";
var AURA_COLOR = "FFAA00";
var AURA_SCALE = 2.0;

var TRANSFORM_TICKS = 40;
var COMBAT_TIMER = 9101;
var WAVE_TIMER = 9102;

var TECHS = ["kamehameha", "masenko", "galick_gun", "final_flash"];

function init(event) {
    var npc = event.npc;
    if (typeof XenoPixels === "undefined") {
        npc.say("XenoPixels API is not loaded");
        return;
    }

    XenoPixels.setProfile(npc, RACE, 40, 40, 30, 40, 50, 40);
    XenoPixels.setKiColor(npc, KI_COLOR);
    XenoPixels.setKiCharge(npc, 100);
    XenoPixels.setAuraColor(npc, AURA_COLOR);
    XenoPixels.setAuraScale(npc, AURA_SCALE);
    XenoPixels.setAura(npc, true);

    XenoPixels.setHairEnabled(npc, true);
    XenoPixels.setHairColor(npc, HAIR_COLOR);
    if (HAIR_CODE && HAIR_CODE.length > 8) {
        XenoPixels.setHairCode(npc, HAIR_CODE);
    }

    var i;
    for (i = 0; i < FORMS.length; i++) {
        XenoPixels.setMastery(npc, GROUP, FORMS[i], 100);
    }
    XenoPixels.setMastery(npc, GOD_GROUP, GOD_FORM, 100);

    for (i = 0; i < TECHS.length; i++) {
        XenoPixels.addTechnique(npc, TECHS[i]);
    }

    // Commands the JS API does not wrap.
    npc.executeCommand("xenopixels npcprofile ai enable");
    npc.executeCommand("xenopixels npcprofile aura on");
    npc.executeCommand("xenopixels npcprofile hair on");
    npc.executeCommand("xenopixels npcprofile hair color " + HAIR_COLOR);
    npc.executeCommand("xenopixels npcprofile charge 100");

    var state = npc.getStoreddata();
    state.put("xeno_stage", 0);
    state.put("xeno_busy", 0);
    npc.getTimers().forceStart(COMBAT_TIMER, 40, true);
}

function damaged(event) {
    var npc = event.npc;
    if (typeof XenoPixels === "undefined") return;
    if (busy(npc)) return;

    var hp = npc.getHealth() / npc.getMaxHealth();
    var stage = Number(npc.getStoreddata().get("xeno_stage"));
    var next = -1;
    var group = GROUP;
    var form = "";

    if (hp <= 0.20 && stage < 4) {
        next = 4;
        group = GOD_GROUP;
        form = GOD_FORM;
    } else if (hp <= 0.35 && stage < 3) {
        next = 3;
        form = FORMS[2];
    } else if (hp <= 0.55 && stage < 2) {
        next = 2;
        form = FORMS[1];
    } else if (hp <= 0.85 && stage < 1) {
        next = 1;
        form = FORMS[0];
    }
    if (next < 0) {
        // Between transforms: ki blast counter (command path, HEX with no #).
        npc.executeCommand("xenopixels npcprofile kiattack kiblast color " + KI_COLOR);
        return;
    }

    markBusy(npc, TRANSFORM_TICKS + 4);
    npc.getStoreddata().put("xeno_stage", next);
    XenoPixels.setAura(npc, true);
    XenoPixels.ascend(npc, group, form, TRANSFORM_TICKS);
    npc.say("Haaaa! " + group + "/" + form);
    npc.getTimers().forceStart(WAVE_TIMER, TRANSFORM_TICKS + 2, false);
}

function meleeAttack(event) {
    var npc = event.npc;
    if (typeof XenoPixels === "undefined" || busy(npc)) return;
    var target = npc.getAttackTarget();
    if (target == null) target = event.target;
    if (!XenoPixels.isTechniqueReady(npc, "masenko")) return;
    XenoPixels.fireTechnique(npc, "masenko", target, 40);
}

function rangedLaunched(event) {
    meleeAttack(event);
}

function target(event) {
    var npc = event.npc;
    if (typeof XenoPixels === "undefined") return;
    XenoPixels.setAura(npc, true);
}

function timer(event) {
    var npc = event.npc;
    if (typeof XenoPixels === "undefined") return;

    if (event.id == WAVE_TIMER) {
        var target = npc.getAttackTarget();
        if (target != null && XenoPixels.isTechniqueReady(npc, "kamehameha")) {
            XenoPixels.fireTechnique(npc, "kamehameha", target, 80);
        } else {
            npc.executeCommand("xenopixels npcprofile kiattack kiwave color " + KI_COLOR);
        }
        return;
    }

    if (event.id != COMBAT_TIMER) return;
    if (busy(npc)) return;
    var aim = npc.getAttackTarget();
    if (aim == null) return;

    var stage = Number(npc.getStoreddata().get("xeno_stage"));
    var id = pickTech(npc, stage);
    if (id == null) {
        npc.executeCommand("xenopixels npcprofile kiattack kiblast color " + KI_COLOR);
        return;
    }
    XenoPixels.fireTechnique(npc, id, aim, 60);
}

function interact(event) {
    var npc = event.npc;
    if (typeof XenoPixels === "undefined") {
        npc.say("XenoPixels missing");
        return;
    }
    var p = XenoPixels.getProfile(npc);
    var hair = XenoPixels.getHair(npc);
    var techs = jsList(XenoPixels.listTechniques(npc));
    npc.say("XenoPixels v" + XenoPixels.getVersion()
        + " race=" + mapGet(p, "race")
        + " form=" + mapGet(p, "formGroup") + "/" + mapGet(p, "form")
        + " aura=" + mapGet(p, "auraOn")
        + " stage=" + npc.getStoreddata().get("xeno_stage"));
    npc.say("hair on=" + mapGet(hair, "enabled")
        + " color=" + mapGet(hair, "color")
        + " code=" + (String(mapGet(hair, "code")).length > 8 ? "set" : "PASTE HAIR_CODE"));
    npc.say("techs " + techs.join(",")
        + " kame cd=" + XenoPixels.getTechniqueCooldown(npc, "kamehameha"));
}

function died(event) {
    resetForm(event.npc);
}

function kill(event) {
    resetForm(event.npc);
}

function resetForm(npc) {
    if (typeof XenoPixels === "undefined" || npc == null) return;
    XenoPixels.descend(npc, 20);
    npc.getStoreddata().put("xeno_stage", 0);
    npc.getStoreddata().put("xeno_busy", 0);
}

function pickTech(npc, stage) {
    var order;
    if (stage >= 4) order = ["final_flash", "kamehameha", "galick_gun", "masenko"];
    else if (stage >= 2) order = ["kamehameha", "galick_gun", "masenko"];
    else order = ["masenko", "kamehameha"];
    var i;
    for (i = 0; i < order.length; i++) {
        if (XenoPixels.isTechniqueReady(npc, order[i])) return order[i];
    }
    return null;
}

function busy(npc) {
    return Number(npc.getStoreddata().get("xeno_busy")) > npc.getWorld().getTotalTime();
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
