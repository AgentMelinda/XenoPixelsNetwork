/**
 * XenoAPI guard for NATIVE Xeno NPCs: typed world queries, targeting and navigation.
 *
 * Every few ticks the guard looks for the closest monster near its home, attacks it, and
 * walks back home when nothing is left. Uses only XenoAPI calls that are natively backed:
 *   IWorld.getClosestEntity, ICustomNpc.setAttackTarget / navigateTo / getHome*,
 *   IEntity.distanceTo via IPos, ICustomNpc.getTempdata.
 *
 * Paste into a native Xeno NPC's Scripts tab. Search range is capped at 128 blocks by the
 * native adapter; navigation is capped at 256 blocks and speed 3.
 */
"use strict";

var RANGE = 16;
var MONSTER = 3;   // xenoapi.npcs.api.constants.EntitiesType.MONSTER

function guard(event) {
    if (typeof XenoAPI === "undefined" || XenoAPI == null) return null;
    return XenoPixels.toXeno(event.npc);
}

function home(npc) {
    return XenoAPI.getIPos(npc.getHomeX(), npc.getHomeY(), npc.getHomeZ());
}

function tick(event) {
    var npc = guard(event);
    if (npc == null) return;

    var world = npc.getWorld();
    var threat = world.getClosestEntity(home(npc), RANGE, MONSTER);
    if (threat != null) {
        if (npc.getAttackTarget() == null) {
            npc.setAttackTarget(threat);
            npc.say("Intruder: " + threat.getName() + "!");
        }
        npc.getTempdata().put("guard_engaged", 1);
        return;
    }

    if (npc.getTempdata().has("guard_engaged")) {
        npc.getTempdata().remove("guard_engaged");
        npc.say("Area clear. Returning to post.");
    }
    if (npc.getPos().distanceTo(home(npc)) > 3 && !npc.isNavigating()) {
        npc.navigateTo(npc.getHomeX() + 0.5, npc.getHomeY(), npc.getHomeZ() + 0.5, 1.0);
    }
}

function targetLost(event) {
    var npc = guard(event);
    if (npc != null) npc.clearNavigation();
}
