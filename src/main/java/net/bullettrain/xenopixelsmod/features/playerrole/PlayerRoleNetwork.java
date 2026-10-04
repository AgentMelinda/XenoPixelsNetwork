package net.bullettrain.xenopixelsmod.features.playerrole;

import com.dragonminez.compat.network.NetworkDirection;
import com.dragonminez.compat.network.NetworkEvent;
import com.dragonminez.compat.network.NetworkRegistry;
import com.dragonminez.compat.network.simple.SimpleChannel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Dedicated SimpleChannel for player-role sync. Protocol {@code "1"}; never ModNetwork {@code "101"}.
 *
 * <p>S2C {@link RoleSyncPacket} on join and after grant/revoke. Client cache is UI-only.
 */
public final class PlayerRoleNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "player_roles"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();
    private static boolean registered;

    /** Client-side UUID → role id string for UI. Cleared/updated by S2C packets. */
    private static final Map<UUID, String> CLIENT_ROLES = new ConcurrentHashMap<>();

    private PlayerRoleNetwork() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(RoleSyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(RoleSyncPacket::new).encoder(RoleSyncPacket::encode)
                .consumerMainThread(RoleSyncPacket::handle).add();
    }

    public static void syncTo(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        PlayerRoleId role = PlayerRoleSavedData.get(player.getServer()).roleOf(player.getUUID());
        CHANNEL.sendToPlayer(new RoleSyncPacket(player.getUUID(), role.id()), player);
    }

    /** Last role id string received for {@code id}, or {@code "none"} if unknown. */
    public static String clientRoleOf(UUID id) {
        if (id == null) return PlayerRoleId.NONE.id();
        return CLIENT_ROLES.getOrDefault(id, PlayerRoleId.NONE.id());
    }

    public record RoleSyncPacket(UUID playerId, String roleId) {
        public RoleSyncPacket(FriendlyByteBuf buffer) {
            this(buffer.readUUID(), buffer.readUtf(32));
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeUUID(playerId);
            buffer.writeUtf(roleId == null ? PlayerRoleId.NONE.id() : roleId, 32);
        }

        public static void handle(RoleSyncPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                if (packet.playerId == null) return;
                String id = packet.roleId == null || packet.roleId.isBlank()
                        ? PlayerRoleId.NONE.id()
                        : packet.roleId.trim().toLowerCase(java.util.Locale.ROOT);
                if (PlayerRoleId.NONE.id().equals(id)) {
                    CLIENT_ROLES.remove(packet.playerId);
                } else {
                    CLIENT_ROLES.put(packet.playerId, id);
                }
            });
            context.setPacketHandled(true);
        }
    }
}
