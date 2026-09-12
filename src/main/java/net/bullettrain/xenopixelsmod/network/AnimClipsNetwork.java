package net.bullettrain.xenopixelsmod.network;

import com.dragonminez.compat.network.NetworkDirection;
import com.dragonminez.compat.network.NetworkEvent;
import com.dragonminez.compat.network.NetworkRegistry;
import com.dragonminez.compat.network.simple.SimpleChannel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.anim.XenoClipLibrary;
import net.bullettrain.xenopixelsmod.anim.XenoTechniqueAnimBindings;
import net.bullettrain.xenopixelsmod.command.XenoPermissions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * Carries the server's studio-clip library to every client that joins.
 *
 * <p>Its own channel, exactly like {@link HudPartsNetwork}, so {@code ModNetwork}'s protocol string
 * stays frozen. An operator pushes a clip with {@code /xenoanim global push}; the server validates
 * and stores it, then hands the whole library to each client on login. The client writes the files
 * into its own clip directory and re-bakes, after which the animation resolves for it the same way
 * a locally authored clip does.
 */
public final class AnimClipsNetwork {
    private static final String PROTOCOL = "2";

    /** Room for the whole library plus JSON overhead. */
    public static final int MAX_PAYLOAD = XenoClipLibrary.MAX_TOTAL_BYTES + 64 * 1024;

    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "anim_clips"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private static boolean registered;

    private AnimClipsNetwork() {}

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
        CHANNEL.messageBuilder(BindSlotPacket.class, 3, NetworkDirection.PLAY_TO_SERVER)
                .decoder(BindSlotPacket::new).encoder(BindSlotPacket::encode)
                .consumerMainThread(BindSlotPacket::handle).add();
        CHANNEL.messageBuilder(BindingsApplyPacket.class, 4, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(BindingsApplyPacket::new).encoder(BindingsApplyPacket::encode)
                .consumerMainThread(BindingsApplyPacket::handle).add();
    }

    public static void pushFromClient(String name, String json) {
        CHANNEL.sendToServer(new PushPacket(name == null ? "" : name, json == null ? "" : json));
    }

    public static void clearFromClient() {
        CHANNEL.sendToServer(new ClearPacket());
    }

    public static void bindFromClient(String slot, String clip) {
        CHANNEL.sendToServer(new BindSlotPacket(slot == null ? "" : slot, clip == null ? "" : clip));
    }

    public static void sendTo(ServerPlayer player) {
        if (player == null) return;
        sendBindings(player);
        if (!XenoClipLibrary.isEmpty()) {
            CHANNEL.sendToPlayer(new ApplyPacket(XenoClipLibrary.bundle()), player);
        }
    }

    public static void broadcast(Iterable<ServerPlayer> players) {
        ApplyPacket library = XenoClipLibrary.isEmpty() ? null : new ApplyPacket(XenoClipLibrary.bundle());
        BindingsApplyPacket bindings = new BindingsApplyPacket(XenoTechniqueAnimBindings.writeCurrent());
        for (ServerPlayer player : players) {
            if (library != null) CHANNEL.sendToPlayer(library, player);
            CHANNEL.sendToPlayer(bindings, player);
        }
    }

    public static void broadcastBindings(Iterable<ServerPlayer> players) {
        BindingsApplyPacket packet = new BindingsApplyPacket(XenoTechniqueAnimBindings.writeCurrent());
        for (ServerPlayer player : players) {
            CHANNEL.sendToPlayer(packet, player);
        }
    }

    public static void sendBindings(ServerPlayer player) {
        if (player == null) return;
        CHANNEL.sendToPlayer(new BindingsApplyPacket(XenoTechniqueAnimBindings.writeCurrent()), player);
    }

    public record PushPacket(String name, String json) {
        public PushPacket(FriendlyByteBuf buf) {
            this(buf.readUtf(64), buf.readUtf(MAX_PAYLOAD));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(name == null ? "" : name, 64);
            buf.writeUtf(json == null ? "" : json, MAX_PAYLOAD);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                if (!XenoPermissions.hasPermission(player.createCommandSourceStack(),
                        XenoPermissions.XENOANIM_GLOBAL)) {
                    player.displayClientMessage(Component.literal(
                            "§cNo permission to publish animation clips"), true);
                    return;
                }
                String problem = XenoClipLibrary.put(name, json);
                if (problem != null) {
                    player.displayClientMessage(Component.literal(
                            "§c" + name + " refused: " + problem), true);
                    return;
                }
                broadcast(player.getServer().getPlayerList().getPlayers());
                player.displayClientMessage(Component.literal(
                        "§aPublished §f" + name + " §7- joining players will receive it"), false);
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
                if (player == null || !XenoPermissions.hasPermission(
                        player.createCommandSourceStack(), XenoPermissions.XENOANIM_GLOBAL)) {
                    return;
                }
                XenoClipLibrary.clear();
                player.displayClientMessage(Component.literal(
                        "§7Animation library cleared - new joins receive nothing"), false);
            });
            context.setPacketHandled(true);
        }
    }

    public record ApplyPacket(String bundle) {
        public ApplyPacket(FriendlyByteBuf buf) {
            this(buf.readUtf(MAX_PAYLOAD));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(bundle == null ? "" : bundle, MAX_PAYLOAD);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() ->
                    net.bullettrain.xenopixelsmod.client.anim.XenoClipLibraryClient.apply(bundle));
            context.setPacketHandled(true);
        }
    }

    public record BindSlotPacket(String slot, String clip) {
        public BindSlotPacket(FriendlyByteBuf buf) {
            this(buf.readUtf(64), buf.readUtf(64));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(slot == null ? "" : slot, 64);
            buf.writeUtf(clip == null ? "" : clip, 64);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                if (!XenoPermissions.hasPermission(player.createCommandSourceStack(),
                        XenoPermissions.XENOANIM_GLOBAL)) {
                    player.displayClientMessage(Component.literal(
                            "§cNo permission to bind server animation slots"), true);
                    return;
                }
                String normalized = XenoTechniqueAnimBindings.normalizeSlot(slot);
                if (normalized == null) {
                    player.displayClientMessage(Component.literal(
                            "§cUnknown animation slot: " + slot), true);
                    return;
                }
                String name = XenoTechniqueAnimBindings.sanitize(clip);
                if (name.isBlank()) {
                    XenoTechniqueAnimBindings.unbind(normalized);
                    broadcastBindings(player.getServer().getPlayerList().getPlayers());
                    player.displayClientMessage(Component.literal(
                            "§7" + normalized + " back to the shipped clip"), false);
                    return;
                }
                XenoTechniqueAnimBindings.bind(normalized, name);
                broadcastBindings(player.getServer().getPlayerList().getPlayers());
                player.displayClientMessage(Component.literal(
                        "§a" + normalized + " §7now plays §f" + name
                                + " §7for everyone who joins"), false);
            });
            context.setPacketHandled(true);
        }
    }

    public record BindingsApplyPacket(String json) {
        public BindingsApplyPacket(FriendlyByteBuf buf) {
            this(buf.readUtf(MAX_PAYLOAD));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(json == null ? "{}" : json, MAX_PAYLOAD);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() ->
                    net.bullettrain.xenopixelsmod.client.anim.XenoTechniqueAnimBindingsClient.apply(
                            json == null ? "{}" : json));
            context.setPacketHandled(true);
        }
    }
}
