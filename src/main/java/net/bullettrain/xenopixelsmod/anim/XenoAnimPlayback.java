package net.bullettrain.xenopixelsmod.anim;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAnim;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side play / stop / timed cutoff for scripted studio clips.
 *
 * <p>A new play on the same entity cancels a stop that has not fired yet, so a script that
 * chains clips does not have the previous timer cut the new one short.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoAnimPlayback {

    private record PendingStop(MinecraftServer server, ServerLevel level, int entityId,
                               long dueTick) {}
    private static final Map<UUID, PendingStop> PENDING_STOPS = new ConcurrentHashMap<>();

    private XenoAnimPlayback() {}

    public static boolean playHold(LivingEntity target, String animation, float speed) {
        cancelStop(target.getUUID());
        return NpcDmzAnim.broadcast(target, animation, speed, NpcDmzAnim.FLAG_HOLD);
    }

    /** One-shot melee packet — the path Full NPC punches already use. */
    public static boolean playOnce(LivingEntity target, String animation, float speed) {
        cancelStop(target.getUUID());
        return NpcDmzAnim.broadcast(target, animation, speed, 0);
    }

    public static boolean stop(LivingEntity target) {
        cancelStop(target.getUUID());
        return NpcDmzAnim.stop(target);
    }

    public static void cancelStop(UUID id) {
        if (id != null) {
            PENDING_STOPS.remove(id);
        }
    }

    /** After {@code delayTicks}, stop unless a newer play or stop replaced this timer. */
    public static void scheduleStop(LivingEntity target, int delayTicks) {
        if (target == null || !(target.level() instanceof ServerLevel level)
                || level.getServer() == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        PENDING_STOPS.put(target.getUUID(), new PendingStop(server, level, target.getId(),
                (long) server.getTickCount() + Math.max(1, delayTicks)));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        for (var entry : PENDING_STOPS.entrySet()) {
            PendingStop pending = entry.getValue();
            if (pending.server() != server || now < pending.dueTick()
                    || !PENDING_STOPS.remove(entry.getKey(), pending)) {
                continue;
            }
            if (pending.level().getEntity(pending.entityId()) instanceof LivingEntity live
                    && live.getUUID().equals(entry.getKey()) && live.isAlive()) {
                NpcDmzAnim.stop(live);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_STOPS.entrySet().removeIf(entry -> entry.getValue().server() == event.getServer());
        // Script instances and run throttles belong to the world that just closed.
        net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.clearAll();
        net.bullettrain.xenopixelsmod.network.packet.NpcScriptPacket.clearCooldowns();
    }
}
