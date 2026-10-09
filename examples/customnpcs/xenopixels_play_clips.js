/**
 * Play studio / combat animations only. No forms, no ascend, no descend.
 *
 * Paste into the NPC Scripts tab. Enabled = Yes. Close the GUI so it saves.
 * Tick the Interact box. Tick Tick/Update if you want the playlist to continue
 * without the Timer box. Right-click starts. Right-click again stops.
 *
 * Do not leave a Dialog or Role on this NPC — those eat the right-click.
 *
 * Custom clips must be published:  /xenoanim global push <name>
 * Put those bare names in CLIPS below. Unknown names fall back to FALLBACKS
 * so a missing push does not silently do nothing.
 *
 * Needs a combat profile + Full DragonMineZ appearance + player model.
 *
 * HOLD = true freezes the last frame of each clip until the next clip,
 * another play, or right-click stop. The last clip stays posed.
 */
"use strict";

var CLIPS = ["ssj1_transform", "transformationanimationbyjacky", "newhakaipose"];
var FALLBACKS = [
    "combat.xeno_jab_right_v3",
    "combat.xeno_spin_kick_right_v3",
    "combat.xeno_heavy_finish_v3"
];
var HOLD = true;
var GAP_TICKS = 10;
var T_STEP = 9401;

function init(event) {
    dress(event.npc);
    event.npc.getStoreddata().put("xeno_clip_i", "-1");
    event.npc.getStoreddata().put("xeno_clip_due", "0");
}

function interact(event) {
    var n = event.npc;
    if (typeof XenoPixels === "undefined") {
        n.say("XenoPixels is not loaded");
        return;
    }
    var data = n.getStoreddata();
    if (clipIndex(data) >= 0) {
        stopShow(n);
        n.say("Stopped.");
        return;
    }
    if (!XenoPixels.canPlayClip(n)) {
        dress(n);
    }
    data.put("xeno_clip_i", "0");
    data.put("xeno_clip_due", "0");
    n.say("Playing clips…");
    // Do not wait for function timer — that box is often off, so the
    // old script only set the index and never posed.
    step(n, true);
}

function timer(event) {
    if (event.id != T_STEP) return;
    step(event.npc, true);
}

function tick(event) {
    step(event.npc, false);
}

function update(event) {
    step(event.npc, false);
}

function step(n, force) {
    if (typeof XenoPixels === "undefined") return;
    var data = n.getStoreddata();
    var i = clipIndex(data);
    if (i < 0) return;
    var now = worldTime(n);
    if (!force && now < dueAt(data)) return;
    if (i >= CLIPS.length) {
        if (HOLD) {
            data.put("xeno_clip_due", String(now + 1000000));
            n.say("Holding last pose. Right-click to release.");
            return;
        }
        stopShow(n);
        n.say("Done.");
        return;
    }
    var wait = playOne(n, CLIPS[i], FALLBACKS[i] || FALLBACKS[0]);
    data.put("xeno_clip_i", String(i + 1));
    data.put("xeno_clip_due", String(now + wait));
    try {
        n.getTimers().forceStart(T_STEP, wait, false);
    } catch (e) {
        // Tick/update will fire the next clip when due.
    }
}

function playOne(n, clip, fallback) {
    try {
        if (!XenoPixels.canPlayClip(n)) {
            dress(n);
            if (!XenoPixels.canPlayClip(n)) {
                n.say("Need Full DragonMineZ appearance. Open XenoPixels on this NPC and set Full.");
                return GAP_TICKS + 20;
            }
        }
        if (clip && XenoPixels.isClipAvailable(clip)) {
            var ticks = XenoPixels.clipDuration(clip);
            if (ticks < 1) ticks = 80;
            if (startClip(n, clip, HOLD)) {
                n.say(HOLD ? "Holding " + clip : "Playing " + clip);
                return ticks + GAP_TICKS;
            }
            n.say("playClip failed for " + clip);
            return GAP_TICKS + 20;
        }
        var library = XenoPixels.listLibraryClips();
        n.say(clip + " is not on this server. Library: "
            + (library && library.length ? joinNames(library) : "(empty — /xenoanim global push)"));
        if (fallback && startClip(n, fallback, HOLD)) {
            n.say(HOLD ? "Holding fallback " + fallback : "Fallback " + fallback);
        } else if (fallback && XenoPixels.playAnimation(n, fallback)) {
            n.say("Fallback " + fallback);
        }
        return 40;
    } catch (e) {
        n.say("clip error: " + e);
        return GAP_TICKS + 20;
    }
}

/** Hold uses playClipHold. Nashorn cannot reliably call playClip(..., true). */
function startClip(n, clip, hold) {
    if (hold && typeof XenoPixels.playClipHold === "function") {
        return XenoPixels.playClipHold(n, clip);
    }
    if (typeof XenoPixels.playClipOnce === "function") {
        return XenoPixels.playClipOnce(n, clip);
    }
    return XenoPixels.playClip(n, clip);
}

function dress(n) {
    if (typeof XenoPixels === "undefined") {
        n.say("XenoPixels is not loaded");
        return;
    }
    if (typeof XenoPixels.setProfile === "function") {
        XenoPixels.setProfile(n, "saiyan", 40, 40, 30, 40, 50, 40);
    }
    if (typeof XenoPixels.setPowerRelease === "function") XenoPixels.setPowerRelease(n, 100);
    if (typeof XenoPixels.setAuthoritative === "function") XenoPixels.setAuthoritative(n, true);
    if (typeof XenoPixels.setCombatBrain === "function") XenoPixels.setCombatBrain(n, false);
    XenoPixels.setAppearanceMode(n, "FULL");
    if (typeof XenoPixels.setPlayerModel === "function") {
        XenoPixels.setPlayerModel(n, true);
    }
}

function stopShow(n) {
    n.getStoreddata().put("xeno_clip_i", "-1");
    n.getStoreddata().put("xeno_clip_due", "0");
    try { n.getTimers().stop(T_STEP); } catch (e) {}
    if (typeof XenoPixels !== "undefined") {
        XenoPixels.stopClip(n);
    }
}

function died(event) {
    stopShow(event.npc);
}

function worldTime(n) {
    try {
        return n.getWorld().getTotalTime();
    } catch (e) {
        return 0;
    }
}

function dueAt(data) {
    var raw = data.get("xeno_clip_due");
    if (raw === null || raw === undefined || raw === "") return 0;
    var n = Number(raw);
    return isNaN(n) ? 0 : n;
}

/** CustomNPCs stores strings. Missing/blank must not look like "0" (running). */
function clipIndex(data) {
    var raw = data.get("xeno_clip_i");
    if (raw === null || raw === undefined || raw === "") return -1;
    var n = Number(raw);
    return isNaN(n) ? -1 : n;
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
