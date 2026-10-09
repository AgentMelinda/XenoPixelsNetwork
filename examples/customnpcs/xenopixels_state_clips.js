/**
 * Bind published studio clips to combat / transform states.
 *
 * Paste into the NPC Scripts tab. Enabled = Yes. Close the GUI so it saves.
 * Publish each clip first:  /xenoanim global push <name>
 *
 * Slots:
 *   TRANSFORM, PUNCH, CHARGE_PUNCH, CHARGE_PUNCH_FIRE,
 *   CHARGE_KICK, CHARGE_KICK_FIRE, CHARGE_KI, HAKAI_HOLD, HAKAI_FIRE
 *
 * Per-NPC binds persist on the profile. bindStateSlot is server-wide
 * (same map as /xenoanim bind) for every player without an override.
 */
"use strict";

function init(event) {
    var n = event.npc;
    if (typeof XenoPixels === "undefined") {
        n.say("XenoPixels is not loaded");
        return;
    }
    XenoPixels.setAppearanceMode(n, "FULL");
    XenoPixels.setStateClip(n, "TRANSFORM", "ssj3_pose");
    XenoPixels.setStateClip(n, "PUNCH", "my_jab");
    XenoPixels.setStateClip(n, "CHARGE_PUNCH", "my_charge");
    XenoPixels.setStateClip(n, "CHARGE_PUNCH_FIRE", "my_release");
    XenoPixels.setStateClip(n, "CHARGE_KICK", "my_kick_charge");
    XenoPixels.setStateClip(n, "CHARGE_KICK_FIRE", "my_kick_release");
}

function interact(event) {
    var n = event.npc;
    n.say("Slots: " + joinNames(XenoPixels.listStateSlots()));
    n.say("Punch clip: " + XenoPixels.getStateClip(n, "PUNCH"));
}

/** Nashorn Java arrays have .length, not .join. */
function joinNames(names) {
    if (typeof XenoPixels !== "undefined" && typeof XenoPixels.joinNames === "function") {
        return XenoPixels.joinNames(names);
    }
    if (names == null) return "";
    if (typeof names.join === "function") return names.join(", ");
    var out = [];
    for (var i = 0; i < names.length; i++) out.push(names[i]);
    return out.join(", ");
}

/**
 * Optional Global Player Scripts: chat `!charge` / `!kick` plays the bound state.
 */
function chat(event) {
    if (typeof XenoPixels === "undefined") return;
    var msg = String(event.message || "");
    if (msg === "!charge") {
        event.setCanceled(true);
        XenoPixels.playState(event.player, "CHARGE_PUNCH");
    } else if (msg === "!kick") {
        event.setCanceled(true);
        XenoPixels.playState(event.player, "CHARGE_KICK");
    } else if (msg === "!transformanim") {
        event.setCanceled(true);
        XenoPixels.playState(event.player, "TRANSFORM");
    }
}
