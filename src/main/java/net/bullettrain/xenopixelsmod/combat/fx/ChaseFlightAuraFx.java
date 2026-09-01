package net.bullettrain.xenopixelsmod.combat.fx;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aura for chase-flight: purely visual, no combat side effects. Decoupled from
 * {@link net.bullettrain.xenopixelsmod.combat.Bt3SparkingSystem} on purpose — sparking bundles a
 * damage multiplier, meter, and i-frames with its aura, none of which belong to a chase move.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class ChaseFlightAuraFx {

    /** Cool ki-blue core. */
    private static final Vector3f AURA_CORE = new Vector3f(0.55f, 0.80f, 1.0f);
    /** Near-white edge so the shell has depth. */
    private static final Vector3f AURA_EDGE = new Vector3f(0.85f, 0.95f, 1.0f);

    private static final int RING_POINTS = 3;
    private static final double RING_SCALE = 0.85;
    private static final double SPIN = 1.1;

    private static final Set<UUID> ACTIVE = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private ChaseFlightAuraFx() {
    }

    public static void activate(UUID playerId) {
        ACTIVE.add(playerId);
    }

    public static void deactivate(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    public static boolean isActive(UUID playerId) {
        return ACTIVE.contains(playerId);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!isActive(player.getUUID())) return;

        shell(level, player);
    }

    private static void shell(ServerLevel level, ServerPlayer player) {
        float height = Math.max(0.5f, player.getBbHeight());
        double radius = Math.max(0.3f, player.getBbWidth()) * RING_SCALE;

        for (int i = 0; i < RING_POINTS; i++) {
            double angle = player.tickCount * SPIN + (Math.PI * 2.0 / RING_POINTS) * i;
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;
            double y = player.getY() + height * 0.1;

            boolean core = (i & 1) == 0;
            DustParticleOptions dust =
                    new DustParticleOptions(core ? AURA_CORE : AURA_EDGE, core ? 1.4f : 1.1f);
            level.sendParticles(dust, x, y, z, 0, 0.0, 0.55, 0.0, 1.0);
        }

        level.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 0.05, player.getZ(), 1, 0.12, 0.02, 0.12, 0.01);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player p = event.getEntity();
        if (p == null) return;
        ACTIVE.remove(p.getUUID());
    }
}
