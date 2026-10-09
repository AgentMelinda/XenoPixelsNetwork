// Global Player Scripts chat demo. One tab only. Delete other tabs that error.
// Hold wand/scripter. Enabled = Yes. ESC to save. /xenopixels playerscripts
// hello unchanged. Type hash then hello to rewrite. !ping is private.
// !announce <text> is a public chat.type.text line (XenoPixels.broadcast).
// If the console says ReferenceError, that tab's JS is broken. Fix or delete it.
"use strict";

function login(event) {
    note("Chat script loaded. Type hash-hello, !ping, or !announce hi");
    tell(event.player, "Chat script loaded. Type hash-hello, !ping, or !announce hi");
}

function chat(event) {
    var raw = event && event.message != null ? String(event.message) : "";
    if (typeof XenoPixels !== "undefined" && typeof XenoPixels.getChatMessage === "function") {
        raw = String(XenoPixels.getChatMessage(event) || raw);
    }
    raw = raw.trim();
    if (!raw) {
        return;
    }

    var player = event.player;

    if (raw.indexOf("!") === 0) {
        hideChat(event);
        var parts = raw.substring(1).split(/\s+/);
        var cmd = parts[0].toLowerCase();
        if (cmd === "ping") {
            tell(player, "Pong. This line is private.");
            return;
        }
        if (cmd === "announce") {
            var rest = raw.substring(1 + cmd.length).trim();
            if (!rest) {
                tell(player, "Usage: !announce <text>");
                return;
            }
            shout(player, rest);
            return;
        }
        tell(player, "Chat API: hello, hash-hello, !ping, !announce, !chathelp");
        return;
    }

    if (raw.indexOf("#") === 0) {
        var rewritten = "[Xeno] " + raw.substring(1).trim();
        if (typeof XenoPixels !== "undefined" && typeof XenoPixels.setChatMessage === "function") {
            XenoPixels.setChatMessage(event, rewritten);
        } else if (event) {
            event.message = rewritten;
        }
    }
}

function hideChat(event) {
    if (typeof XenoPixels !== "undefined" && typeof XenoPixels.cancelChat === "function") {
        XenoPixels.cancelChat(event);
        return;
    }
    if (event && typeof event.setCanceled === "function") {
        event.setCanceled(true);
    }
}

function tell(player, message) {
    if (player == null) return;
    if (typeof XenoPixels !== "undefined" && typeof XenoPixels.say === "function") {
        XenoPixels.say(player, message);
    }
}

function shout(player, message) {
    if (player == null) return;
    if (typeof XenoPixels !== "undefined" && typeof XenoPixels.broadcast === "function") {
        XenoPixels.broadcast(player, message);
    }
}

function note(message) {
    if (typeof log === "function") {
        log(message);
    } else if (log && typeof log.apply === "function") {
        log.apply(message);
    }
    if (typeof print === "function") {
        print(message);
    }
}
