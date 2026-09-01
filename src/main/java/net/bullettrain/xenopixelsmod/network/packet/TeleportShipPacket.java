package net.bullettrain.xenopixelsmod.network.packet;

import net.bullettrain.xenopixelsmod.vs.VsShipHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.dragonminez.compat.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → Server packet.
 * Asks the server to teleport the VS2 ship the player is currently standing on
 * to the given world coordinates.
 *
 * <p>This is a legitimate player action (Target tool), so it is not OP-gated; instead it is
 * rate-limited. A forged client could otherwise teleport a ship to arbitrary (world-clamped)
 * coordinates every tick, churning Sable rigid-body physics and forcing chunk loads at distant
 * coordinates.
 */
public class TeleportShipPacket {
    /** Per-player minimum interval between ship teleports. */
    private static final int COOLDOWN_TICKS = 20;
    private static final Map<UUID, Integer> LAST_TELEPORT = new HashMap<>();

    private final double x;
    private final double y;
    private final double z;

    public TeleportShipPacket(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public TeleportShipPacket(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (onCooldown(player)) {
                return;
            }

            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                player.sendSystemMessage(Component.translatable("chat.xenopixelsmod.invalid_coords"));
                return;
            }

            double safeX = Math.max(-29_999_984.0, Math.min(29_999_984.0, x));
            double safeY = Math.max(player.serverLevel().getMinBuildHeight(),
                    Math.min(player.serverLevel().getMaxBuildHeight() - 1.0, y));
            double safeZ = Math.max(-29_999_984.0, Math.min(29_999_984.0, z));

            boolean success = VsShipHelper.teleportPlayerShip(player, safeX, safeY, safeZ);

            if (success) {
                player.sendSystemMessage(Component.translatable(
                        "chat.xenopixelsmod.ship_teleported",
                        String.format("%.1f", safeX),
                        String.format("%.1f", safeY),
                        String.format("%.1f", safeZ)));
            } else {
                player.sendSystemMessage(Component.translatable(
                        "chat.xenopixelsmod.no_ship_found"));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * @return true (and suppresses the teleport) while the player is within the cooldown. The map
     * is pruned lazily here rather than via a logout listener so this packet class stays a plain
     * handler; entries older than a few cooldowns are dropped on every call, keeping it bounded.
     */
    private static boolean onCooldown(ServerPlayer player) {
        if (player == null || player.level() == null) return true;
        MinecraftServer server = player.level().getServer();
        if (server == null) return true;
        int now = server.getTickCount();
        LAST_TELEPORT.entrySet().removeIf(e -> now - e.getValue() > COOLDOWN_TICKS * 4);
        Integer last = LAST_TELEPORT.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) return true;
        LAST_TELEPORT.put(player.getUUID(), now);
        return false;
    }
}
