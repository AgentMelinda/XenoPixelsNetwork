package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidField;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Bounded per-player description needed by the client to reproduce server small-planet exclusions. */
public record SmallPlanetFieldSnapshot(int revision, long fieldSeed, boolean enabled,
                                       List<SmallPlanetField.Sphere> chartedBodies,
                                       List<AsteroidField.Belt> asteroidBelts) {
    public static final int MAX_CHARTED_BODIES = 256;
    public static final int MAX_BELTS = 16;

    public SmallPlanetFieldSnapshot {
        if (revision < 0 || chartedBodies == null || chartedBodies.size() > MAX_CHARTED_BODIES
                || asteroidBelts == null || asteroidBelts.size() > MAX_BELTS)
            throw new IllegalArgumentException("invalid small-planet snapshot bounds");
        chartedBodies = List.copyOf(chartedBodies);
        asteroidBelts = List.copyOf(asteroidBelts);
        for (var belt : asteroidBelts)
            if (belt.id().length() > 256) throw new IllegalArgumentException("asteroid belt id is too long");
    }

    public static SmallPlanetFieldSnapshot disabled(int revision) {
        return new SmallPlanetFieldSnapshot(revision, 0L, false, List.of(), List.of());
    }

    /** Conservative triangle-inequality bound: query reach + candidate reach + exclusion clearance + sphere radius. */
    public static boolean chartSphereMayAffectQuery(double x, double y, double z, SmallPlanetField.Sphere sphere) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || sphere == null) return false;
        double reach = SmallPlanetField.MAX_NEARBY_RANGE + 2.0 * SmallPlanetField.MAX_RADIUS
                + SmallPlanetField.ORBITAL_CLEARANCE + sphere.radius();
        double dx = sphere.x() - x, dy = sphere.y() - y, dz = sphere.z() - z;
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    public static void encode(SmallPlanetFieldSnapshot packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.revision);
        buf.writeLong(packet.fieldSeed);
        buf.writeBoolean(packet.enabled);
        buf.writeVarInt(packet.chartedBodies.size());
        for (var sphere : packet.chartedBodies) {
            buf.writeDouble(sphere.x()); buf.writeDouble(sphere.y()); buf.writeDouble(sphere.z());
            buf.writeDouble(sphere.radius());
        }
        buf.writeVarInt(packet.asteroidBelts.size());
        for (var belt : packet.asteroidBelts) {
            buf.writeUtf(belt.id(), 256);
            buf.writeDouble(belt.centerX()); buf.writeDouble(belt.centerY()); buf.writeDouble(belt.centerZ());
            buf.writeDouble(belt.innerRadius()); buf.writeDouble(belt.outerRadius());
            buf.writeDouble(belt.halfThickness()); buf.writeBoolean(belt.icy());
        }
    }

    public static SmallPlanetFieldSnapshot decode(FriendlyByteBuf buf) {
        int revision = buf.readVarInt();
        long seed = buf.readLong();
        boolean enabled = buf.readBoolean();
        int sphereCount = buf.readVarInt();
        if (sphereCount < 0 || sphereCount > MAX_CHARTED_BODIES)
            throw new IllegalArgumentException("too many chart exclusion spheres: " + sphereCount);
        var spheres = new ArrayList<SmallPlanetField.Sphere>(sphereCount);
        for (int i = 0; i < sphereCount; i++)
            spheres.add(new SmallPlanetField.Sphere(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()));
        int beltCount = buf.readVarInt();
        if (beltCount < 0 || beltCount > MAX_BELTS)
            throw new IllegalArgumentException("too many asteroid belt exclusions: " + beltCount);
        var belts = new ArrayList<AsteroidField.Belt>(beltCount);
        for (int i = 0; i < beltCount; i++)
            belts.add(new AsteroidField.Belt(buf.readUtf(256), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean()));
        return new SmallPlanetFieldSnapshot(revision, seed, enabled, spheres, belts);
    }
}
