/**
 * Autonomous combat. Paste into Scripts, Enabled = Yes, close to save.
 *
 * Needs XenoPixels API v26 and a combat profile.
 * Turns Combat Brain on. Close range is melee-first (strike / charge / punch).
 * Named specials (Flying Fist, Heavy Hit, Bone Crusher, Kiai, random ki) only fire
 * when their Brain-screen flags are on — do not hand-roll player teleports or
 * playAnimation("bonecrusher"). Bind clips on the profile / Atk field instead.
 *
 * Toggling the brain off (wand, Stats, or setCombatBrain(n, false)) cancels charge,
 * ki hold, guard, and an in-progress combo. It does not clear a script lockOn.
 *
 * CustomNPCs still walks and picks targets (Aggressive / Retaliate / faction).
 * This file only installs the profile and the brain.
 */
"use strict";

function init(event) {
    var n = event.npc;
    if (typeof XenoPixels === "undefined") {
        n.say("XenoPixels is not loaded");
        return;
    }
    XenoPixels.setProfile(n, "saiyan", 45, 28, 30, 35, 20, 40);
    XenoPixels.setPowerRelease(n, 100);
    XenoPixels.setAuthoritative(n, true);
    XenoPixels.setCombatBrain(n, true);
    XenoPixels.setBrainFlag(n, "flyingFist", true);
    XenoPixels.setBrainFlag(n, "heavyHit", true);
    XenoPixels.setBrainFlag(n, "boneCrusher", true);
    XenoPixels.setBrainFlag(n, "kiai", true);
    XenoPixels.setBrainFlag(n, "randomKi", true);
    XenoPixels.setBrainFlag(n, "fly", true);
    XenoPixels.setBrainFlag(n, "land", true);
    XenoPixels.setAppearanceMode(n, "FULL");
}

function died(event) {
    if (typeof XenoPixels !== "undefined") {
        XenoPixels.cancelCharge(event.npc);
    }
}
