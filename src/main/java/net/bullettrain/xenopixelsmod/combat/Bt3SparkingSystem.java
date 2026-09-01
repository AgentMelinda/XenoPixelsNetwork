package net.bullettrain.xenopixelsmod.combat;

import net.neoforged.fml.common.EventBusSubscriber;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BT3 Sparking-style meter: build on hits, activate for temporary damage buff + i-frame frames on dash.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class Bt3SparkingSystem {
    private static final Map<UUID, Float> METER = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVE_UNTIL = new HashMap<>();
    private static final Map<UUID, Integer> IFRAMES_UNTIL = new HashMap<>();

    private Bt3SparkingSystem() {}

    /**
     * Global monotonic server tick — the one clock every window is written, read, and pruned
     * against. {@code player.tickCount} is per-entity and is small for a player who just joined a
     * long-running server, so the old per-entity stamps expired on the first server-tick prune
     * (the 100-tick buff vanished instantly for newer players). Defensive for client callers.
     */
    private static int serverTick(ServerPlayer p) {
        if (p == null || p.level() == null) return 0;
        MinecraftServer server = p.level().getServer();
        return server != null ? server.getTickCount() : 0;
    }

    public static float getMeter(UUID id) {
        return METER.getOrDefault(id, 0f);
    }

    public static boolean isSparking(ServerPlayer player) {
        if (player == null) return false;
        Integer until = ACTIVE_UNTIL.get(player.getUUID());
        return until != null && serverTick(player) < until;
    }

    /** Ticks left on active Sparking, or 0 if inactive. */
    public static int remainingSparkingTicks(ServerPlayer player) {
        if (player == null) return 0;
        Integer until = ACTIVE_UNTIL.get(player.getUUID());
        if (until == null) return 0;
        return Math.max(0, until - serverTick(player));
    }

    public static boolean hasIFrames(ServerPlayer player) {
        if (player == null) return false;
        Integer until = IFRAMES_UNTIL.get(player.getUUID());
        return until != null && serverTick(player) < until;
    }

    public static void grantIFrames(ServerPlayer player, int ticks) {
        if (player == null) return;
        IFRAMES_UNTIL.put(player.getUUID(), serverTick(player) + Math.max(1, ticks));
    }

    public static void addMeter(ServerPlayer player, float amount) {
        if (player == null || !XenoServerConfig.bt3SparkingEnabled) return;
        if (amount <= 0f) return;
        float cur = getMeter(player.getUUID());
        METER.put(player.getUUID(), Math.min(100f, cur + amount));
    }

    /** @return true if activated */
    public static boolean tryActivate(ServerPlayer player) {
        if (player == null || !XenoServerConfig.bt3SparkingEnabled) return false;
        if (isSparking(player)) return false;
        float m = getMeter(player.getUUID());
        if (m < 100f) {
            player.displayClientMessage(Component.literal(
                    "§7Sparking: " + Math.round(m) + "% — need full meter"), true);
            return false;
        }
        METER.put(player.getUUID(), 0f);
        int dur = Math.max(20, XenoServerConfig.sparkingDurationTicks);
        ACTIVE_UNTIL.put(player.getUUID(), serverTick(player) + dur);
        player.displayClientMessage(Component.literal("§6§lSPARKING!"), true);
        // Inventory (E) + HUD status icon
        try {
            net.bullettrain.xenopixelsmod.effect.XenoStatusEffectSync.ensure(
                    player,
                    net.bullettrain.xenopixelsmod.effect.ModEffects.SPARKING,
                    0,
                    dur);
            net.bullettrain.xenopixelsmod.effect.XenoStatusEffectSync.remove(
                    player,
                    net.bullettrain.xenopixelsmod.effect.ModEffects.SPARKING_READY);
        } catch (Throwable ignored) {
        }
        return true;
    }

    public static float damageMult(ServerPlayer player) {
        return isSparking(player) ? Math.max(1f, XenoServerConfig.sparkingDamageMult) : 1f;
    }

    @SubscribeEvent
    public static void onHurt(LivingDamageEvent.Pre event) {
        if (!XenoServerConfig.bt3CombatEnabled || !XenoServerConfig.bt3SparkingEnabled) return;

        // i-frames during sonic sway / sparking dash
        if (event.getEntity() instanceof ServerPlayer def && hasIFrames(def)) {
            event.setNewDamage(0f);
            return;
        }

        if (event.getSource().getEntity() instanceof ServerPlayer atk) {
            addMeter(atk, XenoServerConfig.sparkingBuildPerHit);
            // Apply sparking damage mult
            float mult = damageMult(atk);
            if (mult > 1.001f) {
                event.setNewDamage(event.getNewDamage() * mult);
            }
        }
        if (event.getEntity() instanceof ServerPlayer def && event.getNewDamage() > 0.05f) {
            addMeter(def, XenoServerConfig.sparkingBuildOnHurt);
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        int t = event.getServer().getTickCount();
        if (t % 20 != 0) return;
        ACTIVE_UNTIL.entrySet().removeIf(e -> e.getValue() < t - 5);
        IFRAMES_UNTIL.entrySet().removeIf(e -> e.getValue() < t - 5);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player p = event.getEntity();
        if (p == null) return;
        UUID id = p.getUUID();
        METER.remove(id);
        ACTIVE_UNTIL.remove(id);
        IFRAMES_UNTIL.remove(id);
    }
}
