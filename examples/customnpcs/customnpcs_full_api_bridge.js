// Native Xeno NPC JavaScript tab: typed API access without changing legacy wrappers.
// This does not claim that every CustomNPCs 1.18.2 subsystem has native semantics.
function init(event) {
    var npc = event.npc.getAPI();
    var world = event.npc.getWorld().getAPI();
    npc.getDisplay().setSize(5);
    npc.getStats().setMaxHealth(100);
    npc.setHealth(npc.getMaxHealth());
    npc.getTimers().forceStart(41, 100, true);
    npc.say("Typed NPC API ready in " + world.getDimension().getId());
}

function timer(event) {
    if (event.id !== 41) return;
    var npc = event.npc.getAPI();
    npc.setHealth(npc.getMaxHealth());
}
