package net.bullettrain.xenopixelsmod.network.packet;

import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.dragonminez.compat.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-authoritative snapshot used to open the guidance GUI. */
public final class OpenGuidancePacket {
    private final BlockPos computerPos;
    private final int x, y, z;
    private final String status;
    private final int pairedThrusters, speedLevel, apexY, cruiseY;
    private final int fleetChannel, salvoIntervalTicks;
    private final double gravitySi, dragCoefficient, guidanceStopDistance;
    private final BlockPos missileBase, missileCenter, missileNose;

    public OpenGuidancePacket(BlockPos computerPos, int x, int y, int z, String status,
                              int pairedThrusters, int speedLevel, int apexY, int cruiseY,
                              int fleetChannel, int salvoIntervalTicks,
                              double gravitySi, double dragCoefficient,
                              BlockPos missileBase, BlockPos missileCenter, BlockPos missileNose,
                              double guidanceStopDistance) {
        this.computerPos = computerPos;
        this.x = x; this.y = y; this.z = z;
        this.status = status == null ? "idle" : status;
        this.pairedThrusters = pairedThrusters;
        this.speedLevel = speedLevel;
        this.apexY = apexY;
        this.cruiseY = cruiseY;
        this.fleetChannel = fleetChannel;
        this.salvoIntervalTicks = salvoIntervalTicks;
        this.gravitySi = gravitySi;
        this.dragCoefficient = dragCoefficient;
        this.missileBase = missileBase;
        this.missileCenter = missileCenter;
        this.missileNose = missileNose;
        this.guidanceStopDistance = guidanceStopDistance;
    }

    public OpenGuidancePacket(FriendlyByteBuf buf) {
        computerPos = buf.readBlockPos();
        x = buf.readInt(); y = buf.readInt(); z = buf.readInt();
        status = buf.readUtf(512);
        pairedThrusters = buf.readVarInt();
        speedLevel = buf.readVarInt();
        apexY = buf.readInt();
        cruiseY = buf.readInt();
        fleetChannel = buf.readVarInt();
        salvoIntervalTicks = buf.readVarInt();
        gravitySi = buf.readDouble();
        dragCoefficient = buf.readDouble();
        missileBase = buf.readBlockPos();
        missileCenter = buf.readBlockPos();
        missileNose = buf.readBlockPos();
        guidanceStopDistance = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(computerPos);
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
        buf.writeUtf(status, 512);
        buf.writeVarInt(pairedThrusters);
        buf.writeVarInt(speedLevel);
        buf.writeInt(apexY);
        buf.writeInt(cruiseY);
        buf.writeVarInt(fleetChannel);
        buf.writeVarInt(salvoIntervalTicks);
        buf.writeDouble(gravitySi);
        buf.writeDouble(dragCoefficient);
        buf.writeBlockPos(missileBase);
        buf.writeBlockPos(missileCenter);
        buf.writeBlockPos(missileNose);
        buf.writeDouble(guidanceStopDistance);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> ClientScreens.openGuidance.accept(
                new ClientScreens.GuidanceOpenData(computerPos, x, y, z, status,
                        pairedThrusters, speedLevel, apexY, cruiseY,
                        fleetChannel, salvoIntervalTicks, gravitySi, dragCoefficient,
                        missileBase, missileCenter, missileNose, guidanceStopDistance)));
        context.setPacketHandled(true);
    }
}
