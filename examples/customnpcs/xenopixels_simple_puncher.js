/**
 * XenoPixels + CustomNPCs — simple native-melee punch fighter.
 *
 * Paste this file into the NPC's Script tab.
 * In the CustomNPCs AI/Stats tabs, configure the NPC as an ordinary melee attacker
 * and choose who it should attack (Aggressive, Retaliate, factions, etc.).
 * In the XenoPixels appearance editor, select Full DragonMineZ appearance if you want
 * the alternating XenoPixels punch clips to be visible.
 *
 * This script deliberately does not call playAnimation or playComboBeat. Those methods
 * are cosmetic and do not deal damage. CustomNPCs performs the real melee hit; once it
 * commits an attack, XenoPixels plays the configured-generation punch animation; if damage lands,
 * XenoPixels also scales it and spends stamina.
 */
"use strict";

var RACE = "human";
var STRENGTH = 35;
var STRIKE_POWER = 20;
var RESISTANCE = 25;
var VITALITY = 30;
var KI_POWER = 10;
var ENERGY = 30;

function init(event) {
    var npc = event.npc;

    if (typeof XenoPixels === "undefined") {
        npc.say("XenoPixels API is not loaded on this server.");
        return;
    }

    // race, strength, strikePower, resistance, vitality, kiPower, energy
    XenoPixels.setProfile(
        npc,
        RACE,
        STRENGTH,
        STRIKE_POWER,
        RESISTANCE,
        VITALITY,
        KI_POWER,
        ENERGY
    );

    XenoPixels.setPowerRelease(npc, 100);
    XenoPixels.setAuthoritative(npc, true);

    // Keep XenoPixels' autonomous strike/ki brain off. CustomNPCs' normal melee AI
    // remains responsible for approaching the target and landing each real punch.
    XenoPixels.setCombatBrain(npc, false);

    // This is a real exported helper. Full DragonMineZ appearance must still be selected
    // in the XenoPixels appearance editor for the DMZ punch clips to render.
    if (!XenoPixels.setPlayerModel(npc, true)) {
        npc.say("Could not switch this CustomNPC to the player model.");
    }
}
