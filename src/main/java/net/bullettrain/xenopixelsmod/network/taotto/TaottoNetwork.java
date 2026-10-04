package net.bullettrain.xenopixelsmod.network.taotto;

import com.dragonminez.compat.network.NetworkDirection;
import com.dragonminez.compat.network.NetworkEvent;
import com.dragonminez.compat.network.NetworkRegistry;
import com.dragonminez.compat.network.simple.SimpleChannel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.bullettrain.xenopixelsmod.features.taotto.TaottoDocument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Additive Taotto overlay sync. Own channel (not ModNetwork). DMZ tattooType is never written.
 */
public final class TaottoNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "taotto"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();
    private static boolean registered;

    private TaottoNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.messageBuilder(ApplyPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .decoder(ApplyPacket::new).encoder(ApplyPacket::encode)
                .consumerMainThread(ApplyPacket::handle).add();
        CHANNEL.messageBuilder(SyncPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncPacket::new).encoder(SyncPacket::encode)
                .consumerMainThread(SyncPacket::handle).add();
    }

    public static void apply(TaottoDocument document) {
        CompoundTag tag = new CompoundTag();
        if (document != null) {
            document.saveNbt(tag);
        }
        CHANNEL.sendToServer(new ApplyPacket(tag));
    }

    public static void sendTo(ServerPlayer viewer, ServerPlayer subject) {
        if (viewer == null || subject == null) {
            return;
        }
        XenoCapabilities.get(subject).ifPresent(data -> {
            CompoundTag tag = new CompoundTag();
            data.taotto().saveNbt(tag);
            CHANNEL.sendToPlayer(new SyncPacket(subject.getUUID(), tag), viewer);
        });
    }

    public static void broadcast(ServerPlayer subject) {
        if (subject == null || subject.getServer() == null) {
            return;
        }
        XenoCapabilities.get(subject).ifPresent(data -> {
            CompoundTag tag = new CompoundTag();
            data.taotto().saveNbt(tag);
            SyncPacket packet = new SyncPacket(subject.getUUID(), tag);
            CHANNEL.sendToPlayer(packet, subject);
            for (ServerPlayer viewer : subject.getServer().getPlayerList().getPlayers()) {
                if (viewer != subject && viewer.distanceToSqr(subject) < 128 * 128) {
                    CHANNEL.sendToPlayer(packet, viewer);
                }
            }
        });
    }

    public record ApplyPacket(CompoundTag tag) {
        public ApplyPacket(FriendlyByteBuf buffer) {
            this(readTag(buffer));
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeNbt(tag == null ? new CompoundTag() : tag);
        }

        public static void handle(ApplyPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) {
                    return;
                }
                TaottoDocument document = TaottoDocument.loadNbt(packet.tag);
                if (document.size() > TaottoDocument.MAX_SIZE) {
                    return;
                }
                XenoCapabilities.get(player).ifPresent(data -> data.taotto(document));
                broadcast(player);
            });
            context.setPacketHandled(true);
        }
    }

    public record SyncPacket(UUID playerId, CompoundTag tag) {
        public SyncPacket(FriendlyByteBuf buffer) {
            this(buffer.readUUID(), readTag(buffer));
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeUUID(playerId == null ? new UUID(0L, 0L) : playerId);
            buffer.writeNbt(tag == null ? new CompoundTag() : tag);
        }

        public static void handle(SyncPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> ClientScreens.receiveTaotto.accept(packet.playerId, packet.tag));
            context.setPacketHandled(true);
        }
    }

    private static CompoundTag readTag(FriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        return tag == null ? new CompoundTag() : tag;
    }
}
