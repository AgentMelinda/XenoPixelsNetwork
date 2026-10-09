/**
 * Global Player Scripts — private welcome lines on init / login.
 *
 * 1. Global → Player Scripts.
 * 2. Paste this entire file. Enabled = Yes. Close the GUI so it saves.
 * 3. Needs XenoPixels API v23 (say + broadcast). /xenopixels playerscripts
 *    /xenopixels scriptapi search say
 *
 * XenoPixels.say(player, text) is a private literal system line.
 * Do not use player.message() — My NPCs / CustomNPCs treat that as a translation
 * key and the line does not show. Not public chat and not an NPC bubble.
 * Public line: XenoPixels.broadcast(player, text).
 * Public rewrite: XenoPixels.setChatMessage(event, "...") in chat(event).
 */
"use strict";

function init(event) {
    speak(event.player);
}

function login(event) {
    speak(event.player);
}

function logout(event) {
    if (event.player) {
        event.player.getTempdata().remove("xeno_said");
    }
}

function speak(player) {
    if (player == null) return;

    if (typeof XenoPixels === "undefined" || typeof XenoPixels.say !== "function") {
        return;
    }

    // init and login can both fire on join; tempdata lasts for this session only.
    var temp = player.getTempdata();
    if (temp.has("xeno_said")) return;
    temp.put("xeno_said", 1);

    var name = player.getName();
    XenoPixels.say(player, "XenoPixels v" + XenoPixels.getVersion() + " — scripts loaded.");
    XenoPixels.say(player, "Welcome back, " + name + ".");
    XenoPixels.say(player, "These lines are private. Public chat is unchanged.");
}
