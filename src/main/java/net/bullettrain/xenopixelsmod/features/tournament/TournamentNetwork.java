package net.bullettrain.xenopixelsmod.features.tournament;

import com.dragonminez.compat.network.NetworkDirection;
import com.dragonminez.compat.network.NetworkEvent;
import com.dragonminez.compat.network.NetworkRegistry;
import com.dragonminez.compat.network.simple.SimpleChannel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Dedicated SimpleChannel for tournament UI sync. Protocol {@code "1"}; never ModNetwork
 * {@code "101"}.
 *
 * <p>S2C queue snapshot only. No client win / result packet — awards stay server-committed.
 */
public final class TournamentNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "tournament"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();
    private static boolean registered;

    /** Client-side last queue snapshot for UI (Task 5). */
    private static volatile List<UUID> CLIENT_QUEUE = List.of();
    private static volatile UUID CLIENT_KING;
    private static volatile String CLIENT_ACTIVE_MATCH = "";
    private static volatile boolean CLIENT_ENABLED;

    private TournamentNetwork() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(QueueSnapshotPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(QueueSnapshotPacket::new).encoder(QueueSnapshotPacket::encode)
                .consumerMainThread(QueueSnapshotPacket::handle).add();
        CHANNEL.messageBuilder(OpenQueueScreenPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenQueueScreenPacket::new).encoder(OpenQueueScreenPacket::encode)
                .consumerMainThread(OpenQueueScreenPacket::handle).add();
    }

    /** Asks the client to open the queue UI (same channel; not a second protocol). */
    public static void openQueueScreen(ServerPlayer player) {
        if (player == null) return;
        CHANNEL.sendToPlayer(new OpenQueueScreenPacket(), player);
    }

    public static void syncQueueTo(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        CHANNEL.sendToPlayer(snapshotOf(player.getServer()), player);
    }

    public static void broadcastQueue(MinecraftServer server) {
        if (server == null) return;
        QueueSnapshotPacket packet = snapshotOf(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CHANNEL.sendToPlayer(packet, player);
        }
    }

    private static QueueSnapshotPacket snapshotOf(MinecraftServer server) {
        TournamentSavedData data = TournamentSavedData.get(server);
        return new QueueSnapshotPacket(
                net.bullettrain.xenopixelsmod.config.XenoServerConfig.tournamentEnabled,
                data.king(),
                data.activeMatchId() == null ? "" : data.activeMatchId(),
                data.queuedSnapshot());
    }

    public static List<UUID> clientQueue() {
        return CLIENT_QUEUE;
    }

    public static UUID clientKing() {
        return CLIENT_KING;
    }

    public static String clientActiveMatch() {
        return CLIENT_ACTIVE_MATCH;
    }

    public static boolean clientEnabled() {
        return CLIENT_ENABLED;
    }

    /** Clears the cached S2C snapshot (connection login/logout). */
    public static void clearClientSnapshot() {
        CLIENT_ENABLED = false;
        CLIENT_KING = null;
        CLIENT_ACTIVE_MATCH = "";
        CLIENT_QUEUE = List.of();
    }

    public record QueueSnapshotPacket(
            boolean enabled,
            UUID king,
            String activeMatchId,
            List<UUID> queued
    ) {
        public QueueSnapshotPacket(FriendlyByteBuf buffer) {
            this(
                    buffer.readBoolean(),
                    buffer.readBoolean() ? buffer.readUUID() : null,
                    buffer.readUtf(64),
                    readUuidList(buffer));
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBoolean(enabled);
            if (king == null) {
                buffer.writeBoolean(false);
            } else {
                buffer.writeBoolean(true);
                buffer.writeUUID(king);
            }
            buffer.writeUtf(activeMatchId == null ? "" : activeMatchId, 64);
            writeUuidList(buffer, queued);
        }

        public static void handle(QueueSnapshotPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                CLIENT_ENABLED = packet.enabled;
                CLIENT_KING = packet.king;
                CLIENT_ACTIVE_MATCH = packet.activeMatchId == null ? "" : packet.activeMatchId;
                CLIENT_QUEUE = packet.queued == null
                        ? List.of()
                        : Collections.unmodifiableList(new ArrayList<>(packet.queued));
            });
            context.setPacketHandled(true);
        }

        private static List<UUID> readUuidList(FriendlyByteBuf buffer) {
            int size = Math.max(0, Math.min(TournamentService.MAX_QUEUED, buffer.readVarInt()));
            List<UUID> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(buffer.readUUID());
            }
            return list;
        }

        private static void writeUuidList(FriendlyByteBuf buffer, List<UUID> list) {
            List<UUID> safe = list == null ? List.of() : list;
            int size = Math.min(TournamentService.MAX_QUEUED, safe.size());
            buffer.writeVarInt(size);
            for (int i = 0; i < size; i++) {
                buffer.writeUUID(safe.get(i));
            }
        }
    }

    /**
     * Empty S2C cue to open {@code TournamentQueueScreen}. Same {@code tournament} channel
     * protocol {@code "1"}; not a client win / result path.
     */
    public record OpenQueueScreenPacket() {
        public OpenQueueScreenPacket(FriendlyByteBuf buffer) {
            this();
        }

        public void encode(FriendlyByteBuf buffer) {
        }

        public static void handle(OpenQueueScreenPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> ClientScreens.openTournamentQueue.run());
            context.setPacketHandled(true);
        }
    }
}
