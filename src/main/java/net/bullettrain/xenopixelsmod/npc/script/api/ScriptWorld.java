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
