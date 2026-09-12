package net.bullettrain.xenopixelsmod.network.packet;

import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.bullettrain.xenopixelsmod.missile.BallisticFlightPlan;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import com.dragonminez.compat.network.NetworkEvent;

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
        int revision = readBoundedCount(buf, Integer.MAX_VALUE, "revision");
        BallisticFlightPlan.Settings settings = BallisticFlightPlan.Settings.load(buf.readNbt());
        Vec3 launch = readFiniteVec3(buf, "launch");
        Vec3 target = readFiniteVec3(buf, "target");
        long shipId = buf.readLong();
        boolean feasible = buf.readBoolean();
        String status = buf.readUtf(512);
        double azimuth = readFinite(buf, "azimuth");
        double elevation = readFinite(buf, "elevation");
        double required = readFinite(buf, "required speed");
        double available = readFinite(buf, "available speed");
        double eta = readFinite(buf, "ETA");
        double apex = readFinite(buf, "apex");
        double impact = readFinite(buf, "impact speed");
        double miss = readFinite(buf, "predicted miss");
        List<String> warnings = new ArrayList<>();
        int warningCount = readBoundedCount(buf, 16, "warning");
        for (int i = 0; i < warningCount; i++) warnings.add(buf.readUtf(512));
        List<BallisticFlightPlan.Sample> samples = new ArrayList<>();
        int sampleCount = readBoundedCount(buf, 128, "sample");
        for (int i = 0; i < sampleCount; i++) {
            samples.add(new BallisticFlightPlan.Sample(readFinite(buf, "sample fraction"),
                    readFinite(buf, "sample x"), readFinite(buf, "sample y"),
                    readFinite(buf, "sample z"), buf.readEnum(BallisticFlightPlan.Phase.class),
                    buf.readBoolean()));
        }
        List<Vec3> waypoints = new ArrayList<>();
        int pointCount = readBoundedCount(buf, BallisticFlightPlan.MAX_CONTROLLER_WAYPOINTS,
                "controller waypoint");
        for (int i = 0; i < pointCount; i++) {
            waypoints.add(readFiniteVec3(buf, "controller waypoint"));
        }
        this.result = new BallisticFlightPlan.Result(revision, settings, launch, target, shipId,
                feasible, status, azimuth, elevation, required, available, eta, apex,
                impact, miss, warnings, samples, waypoints);
    }

    private static int readBoundedCount(FriendlyByteBuf buf, int maximum, String field) {
        int value = buf.readVarInt();
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException(field + " count outside 0-" + maximum + ": " + value);
        }
        return value;
    }

    private static double readFinite(FriendlyByteBuf buf, String field) {
        double value = buf.readDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException(field + " must be finite");
        return value;
    }

    private static Vec3 readFiniteVec3(FriendlyByteBuf buf, String field) {
        return new Vec3(readFinite(buf, field + " x"), readFinite(buf, field + " y"),
                readFinite(buf, field + " z"));
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
