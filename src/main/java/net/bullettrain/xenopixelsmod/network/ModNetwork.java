package net.bullettrain.xenopixelsmod.network;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.network.packet.GuidanceControlPacket;
import net.bullettrain.xenopixelsmod.network.packet.TeleportShipPacket;
import net.bullettrain.xenopixelsmod.network.packet.SetTargetToolPacket;
import net.bullettrain.xenopixelsmod.network.packet.FlightPlanRequestPacket;
import net.bullettrain.xenopixelsmod.network.packet.FlightPlanResultPacket;
import net.bullettrain.xenopixelsmod.network.packet.BodyCalibrationPacket;
import net.bullettrain.xenopixelsmod.network.packet.OpenGuidancePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Central SimpleChannel registration. Every packet class must be registered here
 * before {@link SimpleChannel#send} / {@link #sendToServer} or login will fail with
 * {@code Invalid message &lt;packet class&gt;}.
 */
public class ModNetwork {
    /** Bump when packet set or wire format changes. */
    private static final String PROTOCOL = "10";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(XenoPixelsMod.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private static int id = 0;
    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;

        // --- Server → Client ---
        CHANNEL.messageBuilder(SyncServerConfigPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncServerConfigPacket::decode)
                .encoder(SyncServerConfigPacket::encode)
                .consumerMainThread(SyncServerConfigPacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncDmzHudStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncDmzHudStatePacket::decode)
                .encoder(SyncDmzHudStatePacket::encode)
                .consumerMainThread(SyncDmzHudStatePacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncXenoStatsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncXenoStatsPacket::decode)
                .encoder(SyncXenoStatsPacket::encode)
                .consumerMainThread(SyncXenoStatsPacket::handle)
                .add();

        CHANNEL.messageBuilder(FlightPlanResultPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(FlightPlanResultPacket::new)
                .encoder(FlightPlanResultPacket::encode)
                .consumerMainThread(FlightPlanResultPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenGuidancePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenGuidancePacket::new)
                .encoder(OpenGuidancePacket::encode)
                .consumerMainThread(OpenGuidancePacket::handle)
                .add();

        // --- Client → Server ---
        CHANNEL.messageBuilder(TeleportShipPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(TeleportShipPacket::new)
                .encoder(TeleportShipPacket::encode)
                .consumerMainThread(TeleportShipPacket::handle)
                .add();

        CHANNEL.messageBuilder(GuidanceControlPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(GuidanceControlPacket::new)
                .encoder(GuidanceControlPacket::encode)
                .consumerMainThread(GuidanceControlPacket::handle)
                .add();

        CHANNEL.messageBuilder(SetTargetToolPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SetTargetToolPacket::new)
                .encoder(SetTargetToolPacket::encode)
                .consumerMainThread(SetTargetToolPacket::handle)
                .add();

        CHANNEL.messageBuilder(FlightPlanRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(FlightPlanRequestPacket::new)
                .encoder(FlightPlanRequestPacket::encode)
                .consumerMainThread(FlightPlanRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(BodyCalibrationPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(BodyCalibrationPacket::new)
                .encoder(BodyCalibrationPacket::encode)
                .consumerMainThread(BodyCalibrationPacket::handle)
                .add();

        CHANNEL.messageBuilder(Bt3CombatPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(Bt3CombatPacket::decode)
                .encoder(Bt3CombatPacket::encode)
                .consumerMainThread(Bt3CombatPacket::handle)
                .add();

        CHANNEL.messageBuilder(ChargeAnimPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(ChargeAnimPacket::decode)
                .encoder(ChargeAnimPacket::encode)
                .consumerMainThread(ChargeAnimPacket::handle)
                .add();

        XenoPixelsMod.LOGGER.info("ModNetwork: registered {} packet types (protocol {})", id, PROTOCOL);
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    public static void sendToPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToAll(Object msg) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), msg);
    }
}
