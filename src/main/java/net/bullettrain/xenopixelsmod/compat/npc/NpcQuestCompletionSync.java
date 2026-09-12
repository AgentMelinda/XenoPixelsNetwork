package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcQuestCompletionSync {
    private static final Map<String, Method[]> METHODS = new ConcurrentHashMap<>();
    private static final java.util.Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private NpcQuestCompletionSync() {}

    public static void flush(ServerPlayer player, String playerDataClassName) {
        if (player == null) return;
        try {
            Method[] methods = METHODS.computeIfAbsent(playerDataClassName,
                    NpcQuestCompletionSync::resolve);
            if (methods.length == 0) return;
            Object data = methods[0].invoke(null, player);
            methods[1].invoke(data, true);
            if (net.bullettrain.xenopixelsmod.config.XenoServerConfig.parallelQuestEnabled) {
                net.bullettrain.xenopixelsmod.capability.XenoCapabilities.get(player).ifPresent(xeno -> {
                    xeno.addSkillPoints(2);
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "§a§lCNPC quest complete §7(+2 skill points)"), false);
                });
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            if (WARNED.add(playerDataClassName)) {
                XenoPixelsMod.LOGGER.warn("Could not immediately persist/sync an NPC quest completion: {}",
                        failure.toString());
            }
        }
    }

    private static Method[] resolve(String className) {
        try {
            Class<?> type = Class.forName(className);
            return new Method[]{type.getMethod("get", net.minecraft.world.entity.player.Player.class),
                    type.getMethod("save", boolean.class)};
        } catch (ReflectiveOperationException failure) {
            return new Method[0];
        }
    }
}
