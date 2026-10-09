/**
 * XenoAPI player events for NATIVE Xeno player scripts (Global -> Player Scripts).
 *
 * New hooks (broken, damaged, toss, levelUp, timer, ...) receive the typed XenoAPI event.
 * Existing hooks (init, login, logout, chat) keep today's event and add event.xeno.
 * Hook names follow the XenoAPI javadoc; see docs/native-xenoapi-adapters.md.
 */
"use strict";

var T_AFTER_LEVEL = 1;

// init runs on join and again whenever this tab is saved; login only on a real login.
function init(event) {
    if (event.xeno != null) event.xeno.player.message("XenoAPI events are on.");
}

function login(event) {
    if (event.xeno != null) event.xeno.player.message("Welcome back - XenoAPI events are on.");
    XenoPixels.bubble(event.player, "Hello everyone!", "gold");   // a speech bubble over the player
}

function broken(event) {
    event.player.message("Broke " + event.block.getName() + " for " + event.exp + " xp.");
}

function damaged(event) {
    if (event.damage > 10) event.damage = 10;       // cap incoming hits at 5 hearts
}

function toss(event) {
    if (event.item.getName() === "minecraft:diamond") {
        event.setCanceled(true);                     // the diamond goes back to the inventory
        event.player.message("Diamonds stay with you.");
    }
}

function levelUp(event) {
    event.player.message("Level change: " + event.change);
    event.player.getTimers().forceStart(T_AFTER_LEVEL, 40, false);
}

function timer(event) {
    if (event.id === T_AFTER_LEVEL) event.player.message("Two seconds after your level change.");
}
