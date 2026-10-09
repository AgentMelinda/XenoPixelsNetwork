// Xeno NPC script: every editor/DMZ setting from a script (native Xeno NPCs).
//
// Any saved setting can be read, set or toggled by its key. Print the keys once with
//   log(XenoPixels.listDmz(npc).join(", "))
// Keys are case-insensitive; dots reach nested settings ("DmzAppearance.SaiyanTail").
// A value keeps its stored type (flag, number or text) and the editor's limits still apply.

function init(event) {
    var npc = event.npc;

    // Skills on/off and levels (ids as in the DMZ Skills tab).
    XenoPixels.setSkill(npc, "fly", true, 3);
    XenoPixels.setKiSense(npc, true);          // the Ki Sense skill
    XenoPixels.setKiSenseLockOn(npc, true);    // "lock on to whoever hit me"

    // Any DMZ tab setting by key.
    XenoPixels.setDmz(npc, "AuraOn", true);
    XenoPixels.setDmz(npc, "AuraLightning", true);
    XenoPixels.setDmz(npc, "AuraScale", 1.5);
    XenoPixels.setDmz(npc, "DmzAppearance.SaiyanTail", true);
    XenoPixels.setDmz(npc, "BrainKiBlast", true);

    // The rest of the editor lives in the same settings: AI, sounds, bubbles, jobs...
    XenoPixels.setNpcSetting(npc, "AiCanSwim", false);
    XenoPixels.setNpcSetting(npc, "BubbleHeight", 0.4);

    // Native identity and world settings.
    XenoPixels.setTitle(npc, "Elite Warrior");
    XenoPixels.setLeashRadius(npc, 24);
    XenoPixels.setRespawn(npc, true);
}

function interact(event) {
    var npc = event.npc;
    // Right-click flips the aura; the reply reads the value back.
    XenoPixels.toggleDmz(npc, "AuraOn");
    npc.say("Aura " + (XenoPixels.getDmz(npc, "AuraOn") ? "on" : "off")
            + ", Ki Sense " + (XenoPixels.isKiSenseOn(npc) ? "on" : "off"));
}

function target(event) {
    var npc = event.npc;
    if (event.target && XenoPixels.isKiSenseLockedOn(npc, event.target)) {
        npc.say("I can sense you, " + event.target.getName() + ".");
    }
}
