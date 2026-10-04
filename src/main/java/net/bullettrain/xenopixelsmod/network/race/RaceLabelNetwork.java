package net.bullettrain.xenopixelsmod.network.race;

import com.dragonminez.compat.network.NetworkDirection;
import com.dragonminez.compat.network.NetworkEvent;
import com.dragonminez.compat.network.NetworkRegistry;
import com.dragonminez.compat.network.simple.SimpleChannel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.command.XenoPermissions;
import net.bullettrain.xenopixelsmod.dmz.race.RaceLabelRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * Syncs operator-authored race picker literals. Dedicated-server clients never see
 * {@code config/dragonminez/races/<id>/xeno_labels.json} otherwise.
 */
public final class RaceLabelNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "race_labels"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();
    private static boolean registered;

    private RaceLabelNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.messageBuilder(SavePacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SavePacket::new).encoder(SavePacket::encode)
                .consumerMainThread(SavePacket::handle).add();
        CHANNEL.messageBuilder(SnapshotPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SnapshotPacket::new).encoder(SnapshotPacket::encode)
                .consumerMainThread(SnapshotPacket::handle).add();
    }

    public static void save(String raceId, String displayName, String description) {
        CHANNEL.sendToServer(new SavePacket(raceId, displayName, description));
    }

    public static void sendSnapshot(ServerPlayer player) {
        CHANNEL.sendToPlayer(new SnapshotPacket(RaceLabelRegistry.snapshotJson()), player);
    }

    public static void broadcast(ServerPlayer source) {
        if (source == null || source.getServer() == null) {
            return;
        }
        SnapshotPacket packet = new SnapshotPacket(RaceLabelRegistry.snapshotJson());
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            CHANNEL.sendToPlayer(packet, player);
        }
    }

    public static void broadcast(net.minecraft.server.MinecraftServer server) {
        if (server == null) {
            return;
        }
        SnapshotPacket packet = new SnapshotPacket(RaceLabelRegistry.snapshotJson());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CHANNEL.sendToPlayer(packet, player);
        }
    }

    public record SavePacket(String raceId, String displayName, String description) {
        public SavePacket(FriendlyByteBuf buffer) {
            this(buffer.readUtf(64), buffer.readUtf(RaceLabelRegistry.MAX_NAME),
                    buffer.readUtf(RaceLabelRegistry.MAX_DESC));
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(raceId == null ? "" : raceId, 64);
            buffer.writeUtf(displayName == null ? "" : displayName, RaceLabelRegistry.MAX_NAME);
            buffer.writeUtf(description == null ? "" : description, RaceLabelRegistry.MAX_DESC);
        }

        public static void handle(SavePacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) {
                    return;
                }
                if (!XenoPermissions.hasPermission(player, XenoPermissions.MAKER_RACE_CREATE)
                        && !XenoPermissions.hasPermission(player, XenoPermissions.RACE_EDIT)) {
                    return;
                }
                var result = RaceLabelRegistry.writeSidecar(
                        packet.raceId, packet.displayName, packet.description);
                if (result.ok()) {
                    broadcast(player);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record SnapshotPacket(String json) {
        public SnapshotPacket(FriendlyByteBuf buffer) {
            this(buffer.readUtf(RaceLabelRegistry.MAX_JSON_CHARS));
        }

        public void encode(FriendlyByteBuf buffer) {
            String payload = json == null ? "{}" : json;
            if (payload.length() > RaceLabelRegistry.MAX_JSON_CHARS) {
                payload = "{}";
            }
            buffer.writeUtf(payload, RaceLabelRegistry.MAX_JSON_CHARS);
        }

        public static void handle(SnapshotPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> RaceLabelRegistry.applySnapshot(packet.json));
            context.setPacketHandled(true);
        }
    }
}
