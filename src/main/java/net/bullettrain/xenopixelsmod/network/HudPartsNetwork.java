package net.bullettrain.xenopixelsmod.network;

import com.dragonminez.compat.network.NetworkDirection;
import com.dragonminez.compat.network.NetworkEvent;
import com.dragonminez.compat.network.NetworkRegistry;
import com.dragonminez.compat.network.simple.SimpleChannel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.command.XenoPermissions;
import net.bullettrain.xenopixelsmod.hud.HudPartsBundle;
import net.bullettrain.xenopixelsmod.hud.HudPartsStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * Own channel so {@link ModNetwork} protocol 64 stays frozen. Carries the operator HUD/menu
 * part layout to every client that joins.
 */
public final class HudPartsNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "hud_parts"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();
    private static boolean registered;

    private HudPartsNetwork() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(PushPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .decoder(PushPacket::new).encoder(PushPacket::encode)
                .consumerMainThread(PushPacket::handle).add();
        CHANNEL.messageBuilder(ClearPacket.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .decoder(ClearPacket::new).encoder(ClearPacket::encode)
                .consumerMainThread(ClearPacket::handle).add();
        CHANNEL.messageBuilder(ApplyPacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ApplyPacket::new).encoder(ApplyPacket::encode)
                .consumerMainThread(ApplyPacket::handle).add();
    }

    public static void pushFromClient(String json) {
        CHANNEL.sendToServer(new PushPacket(json == null ? "" : json));
    }

    public static void clearFromClient() {
        CHANNEL.sendToServer(new ClearPacket());
    }

    public static void sendTo(ServerPlayer player) {
        if (player == null || !HudPartsStore.present()) return;
        CHANNEL.sendToPlayer(new ApplyPacket(HudPartsStore.json()), player);
    }

    public static void broadcast(Iterable<ServerPlayer> players) {
        if (!HudPartsStore.present()) return;
        ApplyPacket packet = new ApplyPacket(HudPartsStore.json());
        for (ServerPlayer player : players) {
            CHANNEL.sendToPlayer(packet, player);
        }
    }

    public record PushPacket(String json) {
        public PushPacket(FriendlyByteBuf buf) {
            this(buf.readUtf(HudPartsBundle.MAX_JSON));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(json == null ? "" : json, HudPartsBundle.MAX_JSON);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !XenoPermissions.hasPermission(player.createCommandSourceStack(),
                        XenoPermissions.XENOPARTS_GLOBAL)) {
                    if (player != null) {
                        player.displayClientMessage(Component.literal(
                                "§cNo permission to publish global HUD parts"), true);
                    }
                    return;
                }
                if (!HudPartsStore.save(json)) {
                    player.displayClientMessage(Component.literal(
                            "§cGlobal HUD parts JSON rejected"), true);
                    return;
                }
                broadcast(player.getServer().getPlayerList().getPlayers());
                player.displayClientMessage(Component.literal(
                        "§aGlobal HUD parts published — joining players will receive this layout"),
                        false);
            });
            context.setPacketHandled(true);
        }
    }

    public record ClearPacket() {
        public ClearPacket(FriendlyByteBuf buf) {
            this();
        }

        public void encode(FriendlyByteBuf buf) {
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !XenoPermissions.hasPermission(player.createCommandSourceStack(),
                        XenoPermissions.XENOPARTS_GLOBAL)) {
                    return;
                }
                HudPartsStore.clear();
                player.displayClientMessage(Component.literal(
                        "§7Global HUD parts cleared — new joins will not be overwritten"), false);
            });
            context.setPacketHandled(true);
        }
    }

    public record ApplyPacket(String json) {
        public ApplyPacket(FriendlyByteBuf buf) {
            this(buf.readUtf(HudPartsBundle.MAX_JSON));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(json == null ? "" : json, HudPartsBundle.MAX_JSON);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    net.bullettrain.xenopixelsmod.client.hud.HudPartsClient.apply(json));
            ctx.get().setPacketHandled(true);
        }
    }
}
