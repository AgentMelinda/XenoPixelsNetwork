package net.bullettrain.xenopixelsmod.combat;

import net.neoforged.fml.common.EventBusSubscriber;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When a player's DMZ active form changes, play a short impact ring + light knockback.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class Bt3TransformImpact {
    private static final Map<UUID, String> LAST_FORM = new ConcurrentHashMap<>();

    private Bt3TransformImpact() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!XenoServerConfig.bt3CombatEnabled || !XenoServerConfig.bt3TransformImpactEnabled) return;
        // Keep the same 5-tick cadence while distributing players across ticks.
        if ((player.tickCount + player.getId()) % 5 != 0) return;

        String formKey = readFormKey(player);
        String prev = LAST_FORM.get(player.getUUID());
        if (prev == null) {
            LAST_FORM.put(player.getUUID(), formKey);
            return;
        }
        if (prev.equals(formKey)) return;
        LAST_FORM.put(player.getUUID(), formKey);

        // Only impact when entering a real form (not returning to base/empty)
        if (formKey == null || formKey.isEmpty() || formKey.endsWith(".base") || "base".equals(formKey)) {
            return;
        }
        doImpact(player);
    }

    private static void doImpact(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel sl)) return;
        double r = XenoServerConfig.transformImpactRadius;
        float knock = XenoServerConfig.transformImpactKnock;

        sl.sendParticles(ParticleTypes.FLASH,
                player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 0.2, player.getZ(), 40, r * 0.4, 0.15, r * 0.4, 0.02);
        sl.sendParticles(ParticleTypes.CRIT,
                player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.6, 0.8, 0.6, 0.15);
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.55f, 1.4f);

        if (knock <= 0f || r <= 0) return;
        AABB box = player.getBoundingBox().inflate(r);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box,
                ent -> ent != player && ent.isAlive())) {
            Vec3 away = e.position().subtract(player.position());
            Vec3 flat = new Vec3(away.x, 0, away.z);
            if (flat.lengthSqr() < 1.0e-4) continue;
            double dist = flat.length();
            if (dist > r) continue;
            double scale = knock * (1.0 - dist / r);
            // Masters are filtered here rather than in the predicate above, so that one switch
            // governs every impulse our combat applies instead of this shockwave having its own.
            CombatKnockback.set(e, flat.normalize().scale(scale).add(0, 0.25 * scale, 0));
        }
        DmzAnimHelper.broadcastMelee(player, DmzAnimHelper.CHARGE_HEAVY_FIRE, false, 1.0f);
    }

    private static String readFormKey(ServerPlayer player) {
        try {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
            if (data == null || data.getCharacter() == null) return "";
            var ch = data.getCharacter();
            String g = ch.getActiveFormGroup();
            String f = ch.getActiveForm();
            if (f == null || f.isEmpty()) return "";
            if (g != null && !g.isEmpty()) return g + "." + f;
            return f;
        } catch (Throwable t) {
            return "";
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            LAST_FORM.remove(event.getEntity().getUUID());
        }
    }

    /** Avoid hard dependency cycle if MastersEntity class load fails. */
}
