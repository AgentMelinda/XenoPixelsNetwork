package net.bullettrain.xenopixelsmod.api.network;

import com.dragonminez.compat.network.NetworkDirection;
import com.dragonminez.compat.network.NetworkEvent;
import com.dragonminez.compat.network.NetworkRegistry;
import com.dragonminez.compat.network.simple.SimpleChannel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Collision-safe packet registration for XenoPixels addons.
 *
 * <p>Register packets from the addon mod constructor. Registrations close when NeoForge fires its
 * payload-registration event. Packet ids are namespaced resource locations rather than numeric
 * slots; XenoPixels sorts them and assigns the DMZ shim's private numeric ids deterministically.
 * The sorted registry is hashed into the channel protocol, so different addon packet sets reject
 * the connection instead of decoding one another's payloads.
 *
 * <p>The public types in this package are XenoPixels-owned, but the current implementation rides
 * {@code com.dragonminez.compat.network.*}. Revalidate this class whenever the tracked DMZ jar is
 * replaced.
 */
public final class AddonNetwork {
    private static final String PROTOCOL_GENERATION = "1";
    private static final ResourceLocation CHANNEL_NAME =
            ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "addons");
    private static final Map<ResourceLocation, Registration<?>> BY_ID = new HashMap<>();
    private static final Map<Class<?>, Registration<?>> BY_TYPE = new HashMap<>();
    private static boolean frozen;
    private static SimpleChannel channel;
    private static String protocolVersion;

    private AddonNetwork() {}

    public static synchronized <MSG> void register(
            ResourceLocation packetId,
            Class<MSG> messageType,
            AddonPacketDirection direction,
            BiConsumer<MSG, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, MSG> decoder,
            BiConsumer<MSG, AddonPacketContext> handler) {
        if (frozen) {
            throw new IllegalStateException("Addon packet registration is closed: " + packetId);
        }
        Objects.requireNonNull(packetId, "packetId");
        Objects.requireNonNull(messageType, "messageType");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(encoder, "encoder");
        Objects.requireNonNull(decoder, "decoder");
        Objects.requireNonNull(handler, "handler");
        if (BY_ID.containsKey(packetId)) {
            throw new IllegalArgumentException("Duplicate addon packet id: " + packetId);
        }
        if (BY_TYPE.containsKey(messageType)) {
            throw new IllegalArgumentException("Duplicate addon packet class: " + messageType.getName());
        }
        Registration<MSG> registration = new Registration<>(
                packetId, messageType, direction, encoder, decoder, handler);
        BY_ID.put(packetId, registration);
        BY_TYPE.put(messageType, registration);
    }

    public static synchronized boolean isReady() {
        return channel != null;
    }

    /** The exact negotiated protocol, including the registered-packet fingerprint. */
    public static synchronized String protocolVersion() {
        if (protocolVersion == null) return protocolFor(sortedRegistrations());
        return protocolVersion;
    }

    public static void sendToServer(Object message) {
        requireDirection(message, AddonPacketDirection.SERVERBOUND);
        requireChannel().sendToServer(message);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        Objects.requireNonNull(player, "player");
        requireDirection(message, AddonPacketDirection.CLIENTBOUND);
        requireChannel().sendToPlayer(message, player);
    }

    public static void sendToAll(Object message) {
        requireDirection(message, AddonPacketDirection.CLIENTBOUND);
        requireChannel().sendToAllPlayers(message);
    }

    public static void sendToTrackingAndSelf(Entity entity, Object message) {
        Objects.requireNonNull(entity, "entity");
        requireDirection(message, AddonPacketDirection.CLIENTBOUND);
        requireChannel().sendToTrackingEntityAndSelf(message, entity);
    }

    public static void sendToTracking(Entity entity, Object message) {
        Objects.requireNonNull(entity, "entity");
        requireDirection(message, AddonPacketDirection.CLIENTBOUND);
        requireChannel().sendToTrackingEntity(message, entity);
    }

    static synchronized void registerPayloads() {
        if (frozen) return;
        frozen = true;
        List<Registration<?>> registrations = sortedRegistrations();
        protocolVersion = protocolFor(registrations);
        channel = NetworkRegistry.ChannelBuilder
                .named(CHANNEL_NAME)
                .networkProtocolVersion(() -> protocolVersion)
                .clientAcceptedVersions(protocolVersion::equals)
                .serverAcceptedVersions(protocolVersion::equals)
                .simpleChannel();

        int numericId = 0;
        for (Registration<?> registration : registrations) {
            registration.bind(channel, numericId++);
        }
        XenoPixelsMod.LOGGER.info("AddonNetwork: registered {} packet types (protocol {})",
                registrations.size(), protocolVersion);
    }

    private static synchronized SimpleChannel requireChannel() {
        if (channel == null) {
            throw new IllegalStateException("Addon network is not ready; send only after payload registration");
        }
        return channel;
    }

    private static synchronized void requireDirection(Object message, AddonPacketDirection expected) {
        Objects.requireNonNull(message, "message");
        Registration<?> registration = BY_TYPE.get(message.getClass());
        if (registration == null) {
            throw new IllegalArgumentException("Unregistered addon packet class: " + message.getClass().getName());
        }
        if (registration.direction() != expected) {
            throw new IllegalArgumentException("Packet " + registration.packetId() + " is "
                    + registration.direction() + ", not " + expected);
        }
    }

    private static List<Registration<?>> sortedRegistrations() {
        return BY_ID.values().stream()
                .sorted(Comparator.comparing(registration -> registration.packetId().toString()))
                .toList();
    }

    private static String protocolFor(List<Registration<?>> registrations) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Registration<?> registration : registrations) {
                String descriptor = registration.packetId() + "|" + registration.direction()
                        + "|" + registration.messageType().getName() + "\n";
                digest.update(descriptor.getBytes(StandardCharsets.UTF_8));
            }
            return PROTOCOL_GENERATION + "-" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    static synchronized void resetForTests() {
        BY_ID.clear();
        BY_TYPE.clear();
        frozen = false;
        channel = null;
        protocolVersion = null;
    }

    static synchronized void freezeForTests() {
        frozen = true;
    }

    private record Registration<MSG>(
            ResourceLocation packetId,
            Class<MSG> messageType,
            AddonPacketDirection direction,
            BiConsumer<MSG, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, MSG> decoder,
            BiConsumer<MSG, AddonPacketContext> handler) {

        void bind(SimpleChannel target, int numericId) {
            target.messageBuilder(messageType, numericId, networkDirection(direction))
                    .encoder(encoder)
                    .decoder(decoder)
                    .consumerMainThread(this::handle)
                    .add();
        }

        private void handle(MSG message, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context raw = supplier.get();
            try {
                handler.accept(message, new AddonPacketContext(direction, raw.getSender(), raw::enqueueWork));
            } finally {
                raw.setPacketHandled(true);
            }
        }

        private static NetworkDirection networkDirection(AddonPacketDirection direction) {
            return direction == AddonPacketDirection.SERVERBOUND
                    ? NetworkDirection.PLAY_TO_SERVER
                    : NetworkDirection.PLAY_TO_CLIENT;
        }
    }
}
