package net.bullettrain.xenopixelsmod.space.physics;

import org.joml.Vector3dc;

/** Bounded native replication reach for the one managed physical Earth. */
public final class PlanetTrackingRange {
    public static final double EARTH_METRES = 1024;
    private PlanetTrackingRange() {}

    public static boolean contains(double playerX,double playerY,double playerZ,
                                   double earthX,double earthY,double earthZ) {
        double dx=playerX-earthX,dy=playerY-earthY,dz=playerZ-earthZ;
        double distanceSquared=dx*dx+dy*dy+dz*dz;
        return Double.isFinite(distanceSquared) && distanceSquared<=EARTH_METRES*EARTH_METRES;
    }

    /** Sable may pass a copied Vector3d from the body's pose; compare the position value, not object identity. */
    public static boolean sameCentre(Vector3dc a, Vector3dc b) {
        return a != null && b != null && a.x() == b.x() && a.y() == b.y() && a.z() == b.z();
    }
}
