package net.bullettrain.xenopixelsmod.network.packet;

import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.bullettrain.xenopixelsmod.missile.BallisticFlightPlan;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server-authoritative planner result and sampled graph payload. */
public final class FlightPlanResultPacket {
    private final BallisticFlightPlan.Result result;

    public FlightPlanResultPacket(BallisticFlightPlan.Result result) {
        this.result = result;
    }

    public FlightPlanResultPacket(FriendlyByteBuf buf) {
        int revision = buf.readVarInt();
        BallisticFlightPlan.Settings settings = BallisticFlightPlan.Settings.load(buf.readNbt());
        Vec3 launch = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 target = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        long shipId = buf.readLong();
        boolean feasible = buf.readBoolean();
        String status = buf.readUtf(512);
        double azimuth = buf.readDouble();
        double elevation = buf.readDouble();
        double required = buf.readDouble();
        double available = buf.readDouble();
        double eta = buf.readDouble();
        double apex = buf.readDouble();
        double impact = buf.readDouble();
        double miss = buf.readDouble();
        List<String> warnings = new ArrayList<>();
        int warningCount = Math.min(16, buf.readVarInt());
        for (int i = 0; i < warningCount; i++) warnings.add(buf.readUtf(512));
        List<BallisticFlightPlan.Sample> samples = new ArrayList<>();
        int sampleCount = Math.min(128, buf.readVarInt());
        for (int i = 0; i < sampleCount; i++) {
            samples.add(new BallisticFlightPlan.Sample(buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readEnum(BallisticFlightPlan.Phase.class),
                    buf.readBoolean()));
        }
        List<Vec3> waypoints = new ArrayList<>();
        int pointCount = Math.min(BallisticFlightPlan.MAX_CONTROLLER_WAYPOINTS, buf.readVarInt());
        for (int i = 0; i < pointCount; i++) {
            waypoints.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
        this.result = new BallisticFlightPlan.Result(revision, settings, launch, target, shipId,
                feasible, status, azimuth, elevation, required, available, eta, apex,
                impact, miss, warnings, samples, waypoints);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(result.revision());
        buf.writeNbt(result.settings().save());
        buf.writeDouble(result.launchPosition().x);
        buf.writeDouble(result.launchPosition().y);
        buf.writeDouble(result.launchPosition().z);
        buf.writeDouble(result.resolvedTarget().x);
        buf.writeDouble(result.resolvedTarget().y);
        buf.writeDouble(result.resolvedTarget().z);
        buf.writeLong(result.targetShipId());
        buf.writeBoolean(result.feasible());
        buf.writeUtf(result.status(), 512);
        buf.writeDouble(result.azimuthDeg());
        buf.writeDouble(result.elevationDeg());
        buf.writeDouble(result.requiredSpeedMps());
        buf.writeDouble(result.availableSpeedMps());
        buf.writeDouble(result.etaSeconds());
        buf.writeDouble(result.apexY());
        buf.writeDouble(result.impactSpeedMps());
        buf.writeDouble(result.predictedMiss());
        buf.writeVarInt(Math.min(16, result.warnings().size()));
        for (int i = 0; i < Math.min(16, result.warnings().size()); i++) {
            buf.writeUtf(result.warnings().get(i), 512);
        }
        buf.writeVarInt(Math.min(128, result.samples().size()));
        for (int i = 0; i < Math.min(128, result.samples().size()); i++) {
            BallisticFlightPlan.Sample sample = result.samples().get(i);
            buf.writeDouble(sample.fraction());
            buf.writeDouble(sample.x());
            buf.writeDouble(sample.y());
            buf.writeDouble(sample.z());
            buf.writeEnum(sample.phase());
            buf.writeBoolean(sample.terrainKnown());
        }
        buf.writeVarInt(Math.min(BallisticFlightPlan.MAX_CONTROLLER_WAYPOINTS, result.controllerWaypoints().size()));
        for (int i = 0; i < Math.min(BallisticFlightPlan.MAX_CONTROLLER_WAYPOINTS, result.controllerWaypoints().size()); i++) {
            Vec3 point = result.controllerWaypoints().get(i);
            buf.writeDouble(point.x); buf.writeDouble(point.y); buf.writeDouble(point.z);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ClientScreens.receiveFlightPlan.accept(result));
        ctx.setPacketHandled(true);
    }
}
