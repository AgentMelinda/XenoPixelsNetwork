package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sparking Zero / BT3 window after a charged kick launches someone:
 * tap W once to dragon-home onto that victim.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class DragonHoming {
    public static final int WINDOW_TICKS = 40;

    private record Window(int victimId, int untilTick) {}

    private static final Map<UUID, Window> WINDOWS = new ConcurrentHashMap<>();

    private DragonHoming() {}

    public static void open(ServerPlayer player, LivingEntity victim) {
        if (player == null || victim == null || !victim.isAlive() || player.getServer() == null) {
            return;
        }
        WINDOWS.put(player.getUUID(), new Window(victim.getId(), player.getServer().getTickCount() + WINDOW_TICKS));
    }

    public static LivingEntity victim(ServerPlayer player) {
        Window window = live(player);
        if (window == null) {
            return null;
        }
        Entity raw = player.level().getEntity(window.victimId);
        if (raw instanceof LivingEntity living && living.isAlive() && living != player) {
            return living;
        }
        WINDOWS.remove(player.getUUID());
        return null;
    }

    public static boolean isLive(ServerPlayer player, LivingEntity target) {
        Window window = live(player);
        return window != null && target != null && target.getId() == window.victimId;
    }

    public static void close(ServerPlayer player) {
        if (player != null) {
            WINDOWS.remove(player.getUUID());
        }
    }

    private static Window live(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return null;
        }
        Window window = WINDOWS.get(player.getUUID());
        if (window == null) {
            return null;
        }
        if (player.getServer().getTickCount() > window.untilTick) {
            WINDOWS.remove(player.getUUID());
            return null;
        }
        return window;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            WINDOWS.remove(event.getEntity().getUUID());
        }
    }
}
