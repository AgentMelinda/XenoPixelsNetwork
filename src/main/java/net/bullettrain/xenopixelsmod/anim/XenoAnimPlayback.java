package net.bullettrain.xenopixelsmod.anim;

import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAnim;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-side play / stop / timed cutoff for scripted studio clips.
 *
 * <p>A new play on the same entity cancels a stop that has not fired yet, so a script that
 * chains clips does not have the previous timer cut the new one short.
 */
public final class XenoAnimPlayback {

    private static final Map<UUID, AtomicInteger> GENERATION = new ConcurrentHashMap<>();

    private XenoAnimPlayback() {}

    public static boolean playHold(LivingEntity target, String animation, float speed) {
        cancelStop(target.getUUID());
        return NpcDmzAnim.broadcast(target, animation, speed, NpcDmzAnim.FLAG_HOLD);
    }

    public static boolean stop(LivingEntity target) {
        cancelStop(target.getUUID());
        return NpcDmzAnim.stop(target);
    }

    public static void cancelStop(UUID id) {
        if (id != null) {
            GENERATION.computeIfAbsent(id, ignored -> new AtomicInteger()).incrementAndGet();
        }
    }

    /** After {@code delayTicks}, stop unless a newer play or stop replaced this timer. */
    public static void scheduleStop(LivingEntity target, int delayTicks) {
        if (target == null || !(target.level() instanceof ServerLevel level)
                || level.getServer() == null) {
            return;
        }
        UUID id = target.getUUID();
        int generation = GENERATION.computeIfAbsent(id, ignored -> new AtomicInteger())
                .incrementAndGet();
        int when = level.getServer().getTickCount() + Math.max(1, delayTicks);
        level.getServer().tell(new TickTask(when, () -> {
            AtomicInteger current = GENERATION.get(id);
            if (current == null || current.get() != generation) {
                return;
            }
            LivingEntity live = level.getEntity(target.getId()) instanceof LivingEntity found
                    ? found : target;
            if (live.isAlive() && live.level() == level) {
                NpcDmzAnim.stop(live);
            }
        }));
    }
}
