/**
 * Player speech bubbles (native Xeno player script).
 *
 * Scripter tool: right-click the air > Player Scripts > "+", paste this file, enable it, close.
 * Saving runs init for everyone online, so it works without re-logging.
 *
 *   XenoPixels.say(player, text)                      private chat line, only that player sees it
 *   XenoPixels.broadcast(player, text)                public chat line
 *   XenoPixels.bubble(player, text)                   speech bubble over the player, everyone sees it
 *   XenoPixels.bubble(player, text, color)            color: blue, gold, green, red
 *   XenoPixels.bubble(player, text, color, shape)     shape: rounded, thought, shout, banner
 *
 * Your own bubble floats above your head: press F5 to see it.
 */
"use strict";

function init(event) {
    XenoPixels.say(event.player, "Player bubbles are on. Type in chat to talk in a bubble.");
    XenoPixels.bubble(event.player, "I'm here!", "blue");
}

function login(event) {
    XenoPixels.say(event.player, "Welcome back!");
    XenoPixels.bubble(event.player, "Hello everyone!", "gold", "shout");
}

// Every chat line also shows as a bubble over the speaker. Lines starting with "..." become a
// thought bubble; lines ending with "!" a red shout.
function chat(event) {
    var text = XenoPixels.getChatMessage(event);
    if (!text) return;
    if (text.indexOf("...") === 0) {
        XenoPixels.bubble(event.player, text.substring(3), "blue", "thought");
    } else if (text.charAt(text.length - 1) === "!") {
        XenoPixels.bubble(event.player, text, "red", "shout");
    } else {
        XenoPixels.bubble(event.player, text, "green");
    }
}

function died(event) {
    XenoPixels.bubble(event.player, "I'll be back...", "red", "thought");
}

function levelUp(event) {
    if (event.change > 0) XenoPixels.bubble(event.player, "Level up!", "gold", "banner");
}
