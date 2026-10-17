// Body Steal — wear the look of the NPC you clicked, then get your own one back.
//
// Needs XenoPixels API v29, and runs on native Xeno NPCs, My NPCs and CustomNPCs alike.
//
// The look is everything DragonMineZ draws: race, gender, class, body shape, colours and hair.
// Nothing else moves — ki, stats, skills, forms, masteries and quests stay the player's own.
// The borrow is remembered on the player rather than in memory, so a death, a relog or a server
// restart in the middle of it still ends with their own face.
//
// Right-click the NPC to take the look; right-click again to hand it back early.

var STEAL_TICKS = 600;      // 20 ticks is a second, so this is 30 seconds
var T_COUNTDOWN = 9001;     // the countdown is a courtesy line; the server expires the borrow itself
var borrower = null;        // NPC timers carry no player, so the one to report to is remembered

function interact(event) {
    var npc = event.npc;
    var player = event.player;
    if (typeof XenoPixels === "undefined") {
        npc.say("XenoPixels is not loaded");
        return;
    }
    if (typeof XenoPixels.stealAppearance !== "function") {
        npc.say("Body steal needs API v29");
        return;
    }
    if (XenoPixels.isAppearanceStolen(player)) {
        stopCountdown(npc);
        XenoPixels.restoreAppearance(player);
        XenoPixels.say(player, "And back to your own face.");
        return;
    }
    var look = XenoPixels.getNpcAppearance(npc);
    var answer = XenoPixels.stealAppearance(player, npc, STEAL_TICKS);
    if (answer !== "ok") {
        // Every refusal changes nothing. Say which one it was — a silent no-op looks like a success.
        XenoPixels.say(player, "The trade fails: " + answer);
        return;
    }
    borrower = player;
    npc.getTimers().forceStart(T_COUNTDOWN, 100, true);
    npc.say("This body is mine now!");
    XenoPixels.say(player, "You are wearing " + mapGet(look, "race") + " for "
        + (STEAL_TICKS / 20) + "s. Click again to give it back early.");
}

function timer(event) {
    if (event.id != T_COUNTDOWN || borrower == null) return;
    try {
        if (!XenoPixels.isAppearanceStolen(borrower)) {
            stopCountdown(event.npc);   // expired, or handed back while we were not looking
            return;
        }
        XenoPixels.say(borrower, (XenoPixels.getAppearanceStealTicksLeft(borrower) / 20)
            + " seconds of that body left.");
    } catch (e) {
        stopCountdown(event.npc);       // the player is gone; the snapshot on them is not
    }
}

function stopCountdown(npc) {
    borrower = null;
    try { npc.getTimers().stop(T_COUNTDOWN); } catch (e) {}
}

function mapGet(values, key) {
    if (values == null) return "";
    if (typeof values.get === "function") return values.get(key);
    return values[key];
}
