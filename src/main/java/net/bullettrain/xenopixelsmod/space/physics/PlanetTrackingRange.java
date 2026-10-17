package net.bullettrain.xenopixelsmod.space.physics;

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
}
