/**
 * XenoPixels — Global Player Scripts (My NPCs / CustomNPCs).
 *
 * Paste this into Global → Player Scripts, set Enabled to Yes, then close the GUI
 * so it saves. A clip that only exists in one player's studio folder will not play
 * for anyone else: publish it first with `/xenoanim global push`.
 *
 * Chat (the command is swallowed, it does not appear in public chat):
 *   !clip my_jab
 *   !clip my_jab 1.5
 *   !clip newhakaipose 1 60
 *   !clip newhakaipose 1 60 hold
 *   !clipstop
 *   !cliplist
 *   !cliphelp
 */
"use strict";

var PREFIX = "!";

function chat(event) {
    if (typeof XenoPixels === "undefined") {
        return;
    }

    var raw = typeof XenoPixels.getChatMessage === "function"
        ? XenoPixels.getChatMessage(event)
        : (event.message || "");
    raw = String(raw).trim();
    if (raw.indexOf(PREFIX) !== 0) {
        return;
    }

    var parts = raw.substring(PREFIX.length).split(/\s+/);
    var cmd = parts[0] ? parts[0].toLowerCase() : "";
    var player = event.player;

    if (cmd === "cliphelp") {
        hideChat(event);
        tell(player, "§7!clip <name> [speed] [ticks] [hold]  §8— play a published studio clip");
        tell(player, "§7!clipstop  §8— return to idle");
        tell(player, "§7!cliplist  §8— names this server can play");
        return;
    }

    if (cmd === "clipstop") {
        hideChat(event);
        if (XenoPixels.stopClip(player)) {
            tell(player, "§7Stopped.");
        }
        return;
    }

    if (cmd === "cliplist") {
        hideChat(event);
        listClips(player);
        return;
    }

    if (cmd !== "clip") {
        return;
    }

    hideChat(event);
    var name = parts[1];
    if (!name) {
        tell(player, "§cUsage: !clip <name> [speed] [ticks] [hold]");
        return;
    }
    if (!XenoPixels.isClipAvailable(name)) {
        tell(player, "§cUnknown clip §f" + name + "§c. Publish it with /xenoanim global push");
        return;
    }
    if (!XenoPixels.canPlayClip(player)) {
        tell(player, "§cThis player cannot show DragonMineZ clips.");
        return;
    }

    var speed = parts[2] ? Number(parts[2]) : 1.0;
    if (!isFinite(speed) || speed <= 0) {
        speed = 1.0;
    }
    var ticks = parts[3] ? parseInt(parts[3], 10) : 0;
    if (!isFinite(ticks) || ticks < 0) {
        ticks = 0;
    }
    var hold = String(parts[4] || "").toLowerCase() === "hold";
    if (XenoPixels.playClip(player, name, speed, ticks, hold)) {
        var length = XenoPixels.clipDuration(name);
        tell(player, "§aPlaying §f" + name
            + (hold ? " §7(hold)" : "")
            + (ticks > 0 ? " §7" + ticks + "t" : (length > 0 ? " §7" + length + "t" : "")));
    } else {
        tell(player, "§cCould not play §f" + name);
    }
}

function hideChat(event) {
    if (typeof XenoPixels.cancelChat === "function") {
        XenoPixels.cancelChat(event);
        return;
    }
    if (event && typeof event.setCanceled === "function") {
        event.setCanceled(true);
    }
}

function listClips(player) {
    var library = XenoPixels.listLibraryClips();
    if (library && library.length) {
        tell(player, "§7Library: §f" + library.join("§7, §f"));
        return;
    }
    tell(player, "§7No published library clips. Shipped combat.xeno_* names still work; push studio files with /xenoanim global push");
}

function tell(player, message) {
    if (player && typeof player.message === "function") {
        player.message(message);
    }
}
