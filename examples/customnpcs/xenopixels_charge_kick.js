/**
 * Charged kick on an NPC. Paste into Scripts, Enabled = Yes, close to save.
 *
 * Needs XenoPixels API v23 and a combat profile.
 * Right-click starts a kick that auto-releases near full. Each start cycles
 * vertical bias: 0 neutral, +1 up, -1 down. Right-click while holding to
 * release early.
 *
 * Optional:
 *   XenoPixels.setStateClip(npc, "CHARGE_KICK", "my_kick_charge");
 *   XenoPixels.setStateClip(npc, "CHARGE_KICK_FIRE", "my_kick_release");
 */
"use strict";

var BIAS_KEY = "xeno_kick_bias";
var BIASES = [0, 1, -1];
var BIAS_NAME = { "0": "neutral", "1": "up", "-1": "down" };

function init(event) {
    var n = event.npc;
    if (typeof XenoPixels === "undefined") {
        n.say("XenoPixels is not loaded");
        return;
    }
    XenoPixels.setProfile(n, "human", 35, 20, 25, 30, 10, 30);
    XenoPixels.setPowerRelease(n, 100);
    XenoPixels.setAuthoritative(n, true);
    XenoPixels.setCombatBrain(n, false);
    XenoPixels.setAppearanceMode(n, "FULL");
    n.getStoreddata().put(BIAS_KEY, 0);
}

function interact(event) {
    var n = event.npc;
    if (typeof XenoPixels === "undefined") {
        return;
    }
    if (XenoPixels.isCharging(n)) {
        var pct = XenoPixels.getChargePercent(n);
        var style = XenoPixels.getChargeStyle(n);
        XenoPixels.releaseCharge(n);
        n.say("Released " + style + " at " + pct + "%");
        return;
    }
    var data = n.getStoreddata();
    var index = Number(data.get(BIAS_KEY));
    if (!isFinite(index) || index < 0) {
        index = 0;
    }
    var bias = BIASES[index % BIASES.length];
    data.put(BIAS_KEY, (index + 1) % BIASES.length);
    if (XenoPixels.startChargeKick(n, 28, bias)) {
        n.say("Charging kick " + (BIAS_NAME[String(bias)] || "neutral"));
    } else {
        n.say("Could not start kick charge");
    }
}
