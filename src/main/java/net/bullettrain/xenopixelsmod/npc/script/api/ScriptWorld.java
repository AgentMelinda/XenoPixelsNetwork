package net.bullettrain.xenopixelsmod.npc.script.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** The NPC's level as a script sees it: time, weather and players. */
public final class ScriptWorld {
    private final ServerLevel level;

    public ScriptWorld(ServerLevel level) {
        this.level = level;
    }

    /** Typed native world API; legacy getDimension() still returns its resource-id string. */
    public xenoapi.npcs.api.IWorld getAPI() {
        if (level == null) throw new IllegalStateException("World API functions require a live server level");
        return net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoApiAdapters.wrap(level);
    }

    /** Places a saved native clone from tabs 1-9, or returns null when its name is absent. */
    public ScriptNpc spawnClone(double x, double y, double z, int tab, String name) {
        var clone = new net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoCloneHandler()
                .spawn(x, y, z, tab, name,
                        net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoApiAdapters.wrap(level));
        if (clone == null) return null;
        var entity = net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoApiAdapters.unwrap(clone);
        if (!(entity instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity npc)) {
            throw new IllegalStateException("The native clone library returned a non-NPC entity");
        }
        var shared = net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.sharedState(npc);
        return new ScriptNpc(npc, shared.temp(), shared.timers());
    }

    /** Game time in ticks. */
    public long getTotalTime() {
        return level.getGameTime();
    }

    /** Time of day in ticks, 0-23999. */
    public long getTime() {
        return level.getDayTime() % 24000L;
    }

    public boolean isDay() {
        return level.isDay();
    }

    public boolean isRaining() {
        return level.isRaining();
    }

    public String getDimension() {
        return level.dimension().location().toString();
    }

    /** An online player in this level by name, or null. */
    public ScriptPlayer getPlayer(String name) {
        if (name == null) return null;
        for (ServerPlayer player : level.players()) {
            if (player.getGameProfile().getName().equalsIgnoreCase(name)) {
                return (ScriptPlayer) ScriptEntity.of(player);
            }
        }
        return null;
    }

    /** Every player in this level. */
    public ScriptPlayer[] getAllPlayers() {
        return level.players().stream().map(p -> (ScriptPlayer) ScriptEntity.of(p))
                .toArray(ScriptPlayer[]::new);
    }

    /** A chat line to everyone on the server. */
    public void broadcast(String text) {
        String line = net.bullettrain.xenopixelsmod.compat.npc.NpcScriptSay.sanitize(text);
        if (line != null) {
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(line), false);
        }
    }
}
