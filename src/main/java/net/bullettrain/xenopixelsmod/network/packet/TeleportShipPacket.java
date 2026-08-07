package net.bullettrain.xenopixelsmod.network.packet;

import net.bullettrain.xenopixelsmod.vs.VsShipHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.dragonminez.compat.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server packet.
 * Asks the server to teleport the VS2 ship the player is currently standing on
 * to the given world coordinates.
 */
public class TeleportShipPacket {
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
}
