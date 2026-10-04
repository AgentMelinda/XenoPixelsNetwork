package net.bullettrain.xenopixelsmod.network;

import com.dragonminez.compat.network.NetworkDirection;
import com.dragonminez.compat.network.NetworkEvent;
import com.dragonminez.compat.network.NetworkRegistry;
import com.dragonminez.compat.network.simple.SimpleChannel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.aero.AeroBus;
import net.bullettrain.xenopixelsmod.aero.AeroControlHost;
import net.bullettrain.xenopixelsmod.aero.GuidanceConfig;
import net.bullettrain.xenopixelsmod.aero.GuidanceVersion;
import net.bullettrain.xenopixelsmod.aero.v2.GuidanceV2Bus;
import net.bullettrain.xenopixelsmod.aero.v2.GuidanceV2SurfaceMode;
import net.bullettrain.xenopixelsmod.block.entity.PilotSeatBlockEntity;
import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Supplier;

/**
 * Own channel so {@link ModNetwork} protocol 68 stays frozen. Carries the server-wide
 * guidance version and per-host v2 surface mode.
 */
public final class GuidanceV2Network {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "guidance_v2"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();
    private static boolean registered;

    private GuidanceV2Network() {}

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.messageBuilder(VersionPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(VersionPacket::new).encoder(VersionPacket::encode)
                .consumerMainThread(VersionPacket::handle).add();
        CHANNEL.messageBuilder(SetSurfaceModePacket.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SetSurfaceModePacket::new).encoder(SetSurfaceModePacket::encode)
                .consumerMainThread(SetSurfaceModePacket::handle).add();
        CHANNEL.messageBuilder(SurfaceModePacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SurfaceModePacket::new).encoder(SurfaceModePacket::encode)
                .consumerMainThread(SurfaceModePacket::handle).add();
        CHANNEL.messageBuilder(RequestHostPacket.class, 3, NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestHostPacket::new).encoder(RequestHostPacket::encode)
                .consumerMainThread(RequestHostPacket::handle).add();
        CHANNEL.messageBuilder(SnapshotPacket.class, 4, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SnapshotPacket::new).encoder(SnapshotPacket::encode)
                .consumerMainThread(SnapshotPacket::handle).add();
    }

    public static void sendVersionTo(ServerPlayer player) {
        if (player != null) {
            CHANNEL.sendToPlayer(new VersionPacket(GuidanceConfig.version()), player);
        }
    }

    public static void broadcastVersion(Iterable<ServerPlayer> players) {
        VersionPacket packet = new VersionPacket(GuidanceConfig.version());
        for (ServerPlayer player : players) {
            CHANNEL.sendToPlayer(packet, player);
        }
    }

    public static void sendSurfaceModeTo(ServerPlayer player, BlockPos pos, GuidanceV2SurfaceMode mode) {
        if (player != null && pos != null) {
            CHANNEL.sendToPlayer(new SurfaceModePacket(pos, mode), player);
        }
    }

    public static void setSurfaceModeFromClient(BlockPos pos, GuidanceV2SurfaceMode mode) {
        CHANNEL.sendToServer(new SetSurfaceModePacket(pos, mode));
    }

    public static void requestHostFromClient(BlockPos pos) {
        CHANNEL.sendToServer(new RequestHostPacket(pos));
    }

    public static void sendSnapshotTo(ServerPlayer player, GuidanceV2Bus bus) {
        if (player != null && bus != null) {
            CHANNEL.sendToPlayer(new SnapshotPacket(bus), player);
        }
    }

    public static void sendHostSnapshot(ServerPlayer player, BlockEntity be) {
        if (be instanceof AeroControlHost host) {
            sendSnapshotTo(player, GuidanceV2Bus.of(host));
        }
    }

    public record VersionPacket(GuidanceVersion version) {
        public VersionPacket(FriendlyByteBuf buf) {
            this(GuidanceVersion.byName(buf.readUtf(8)));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(version == null ? "v1" : version.name().toLowerCase(), 8);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    net.bullettrain.xenopixelsmod.client.guidance.GuidanceV2Client.acceptVersion(version));
            ctx.get().setPacketHandled(true);
        }
    }

    public record SetSurfaceModePacket(BlockPos pos, GuidanceV2SurfaceMode mode) {
        public SetSurfaceModePacket(FriendlyByteBuf buf) {
            this(buf.readBlockPos(), GuidanceV2SurfaceMode.byName(buf.readUtf(32)));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUtf(mode == null ? GuidanceV2SurfaceMode.THRUST_AND_FLAPS.name() : mode.name(), 32);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || pos == null || player.level() == null) {
                    return;
                }
                if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64 * 64) {
                    return;
                }
                BlockEntity be = player.level().getBlockEntity(pos);
                if (be instanceof ShipVlsGuidanceBlockEntity host) {
                    host.guidanceV2().setSurfaceMode(mode);
                    host.setChanged();
                    sendSurfaceModeTo(player, pos, host.guidanceV2().surfaceMode());
                    sendHostSnapshot(player, host);
                } else if (be instanceof PilotSeatBlockEntity host) {
                    host.guidanceV2().setSurfaceMode(mode);
                    host.setChanged();
                    sendSurfaceModeTo(player, pos, host.guidanceV2().surfaceMode());
                    sendHostSnapshot(player, host);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record SurfaceModePacket(BlockPos pos, GuidanceV2SurfaceMode mode) {
        public SurfaceModePacket(FriendlyByteBuf buf) {
            this(buf.readBlockPos(), GuidanceV2SurfaceMode.byName(buf.readUtf(32)));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUtf(mode == null ? GuidanceV2SurfaceMode.THRUST_AND_FLAPS.name() : mode.name(), 32);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    net.bullettrain.xenopixelsmod.client.guidance.GuidanceV2Client.acceptSurfaceMode(pos, mode));
            ctx.get().setPacketHandled(true);
        }
    }

    public record RequestHostPacket(BlockPos pos) {
        public RequestHostPacket(FriendlyByteBuf buf) {
            this(buf.readBlockPos());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || pos == null) {
                    return;
                }
                BlockEntity be = player.level().getBlockEntity(pos);
                GuidanceV2SurfaceMode mode = GuidanceV2SurfaceMode.THRUST_AND_FLAPS;
                if (be instanceof ShipVlsGuidanceBlockEntity host) {
                    mode = host.guidanceV2().surfaceMode();
                } else if (be instanceof PilotSeatBlockEntity host) {
                    mode = host.guidanceV2().surfaceMode();
                } else if (!(be instanceof AeroControlHost)) {
                    return;
                }
                sendSurfaceModeTo(player, pos, mode);
                sendHostSnapshot(player, be);
            });
            context.setPacketHandled(true);
        }
    }

    public record SnapshotPacket(GuidanceV2Bus bus) {
        public SnapshotPacket(FriendlyByteBuf buf) {
            this(new GuidanceV2Bus(
                    buf.readBlockPos(),
                    GuidanceV2SurfaceMode.byName(buf.readUtf(32)),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readEnum(AeroBus.PowerTier.class),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readVarInt()));
        }

        public void encode(FriendlyByteBuf buf) {
            GuidanceV2Bus value = bus;
            buf.writeBlockPos(value.hostPos());
            buf.writeUtf(value.mode() == null
                    ? GuidanceV2SurfaceMode.THRUST_AND_FLAPS.name() : value.mode().name(), 32);
            buf.writeDouble(value.requestedThrottle());
            buf.writeDouble(value.appliedThrottle());
            buf.writeDouble(value.yawDeg());
            buf.writeDouble(value.pitchDeg());
            buf.writeDouble(value.rollDeg());
            buf.writeDouble(value.flap());
            buf.writeDouble(value.flapTarget());
            buf.writeBoolean(value.autoFlap());
            buf.writeBoolean(value.airBrake());
            buf.writeEnum(value.powerTier() == null ? AeroBus.PowerTier.OFFLINE : value.powerTier());
            buf.writeVarInt(value.storedEnergy());
            buf.writeBoolean(value.hasPitch());
            buf.writeBoolean(value.hasRoll());
            buf.writeBoolean(value.hasYaw());
            buf.writeBoolean(value.hasBrake());
            buf.writeVarInt(value.linkedSurfaces());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    net.bullettrain.xenopixelsmod.client.guidance.GuidanceV2Client.acceptBus(bus));
            ctx.get().setPacketHandled(true);
        }
    }
}
