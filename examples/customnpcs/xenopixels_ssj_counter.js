// CustomNPCs script. XenoPixels is injected by the server as a trusted global.
// Only immutable IDs are global; per-NPC state stays in storeddata.
var GROUP = "supersaiyan";
var SSJ1 = "supersaiyanmastered";
var FIRE_TIMER = 9101;
var DESCEND_TIMER = 9102;
var TRANSFORM_TICKS = 40;

function init(event) {
    var state = event.npc.getStoreddata();
    if (!state.has("xeno_counter_ready")) {
        XenoPixels.setMastery(event.npc, GROUP, SSJ1, 100);
        XenoPixels.addTechnique(event.npc, "kamehameha");
        XenoPixels.setHairEnabled(event.npc, true);
        state.put("xeno_counter_ready", true);
    }
}

function damaged(event) {
    var attacker = event.source;
    var state = event.npc.getStoreddata();
    if (state.has("xeno_counter_busy") || attacker == null) return;
    if (!XenoPixels.ascend(event.npc, GROUP, SSJ1, TRANSFORM_TICKS)) return;
    state.put("xeno_counter_busy", true);
    state.put("xeno_counter_attacker", attacker.getUUID());
    event.npc.getTimers().forceStart(FIRE_TIMER, TRANSFORM_TICKS + 2, false);
}

function timer(event) {
    if (event.id == FIRE_TIMER) {
        var target = event.npc.getAttackTarget();
        XenoPixels.fireTechnique(event.npc, "kamehameha", target, 60);
        event.npc.getTimers().forceStart(DESCEND_TIMER, 40, false);
    } else if (event.id == DESCEND_TIMER) {
        XenoPixels.descend(event.npc, 20);
        event.npc.getStoreddata().remove("xeno_counter_busy");
    }
}
