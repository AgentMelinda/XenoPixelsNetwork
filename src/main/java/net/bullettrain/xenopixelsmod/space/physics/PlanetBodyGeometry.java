package net.bullettrain.xenopixelsmod.space.physics;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;

/** Shared, pure geometry used to keep a physical body's HD sphere and far proxy the same size. */
public final class PlanetBodyGeometry {
    private PlanetBodyGeometry() {}

    /** Radius of the sphere enclosing the corners of Sable's inclusive voxel bounds. */
    public static double hullRadius(BoundingBox3ic box) {
        if (box == null) return 0;
        return hullRadius(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    /** Primitive overload so the exact sizing rule can be unit-tested without a Sable runtime. */
    public static double hullRadius(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        double centres=Math.max(maxX-minX,Math.max(maxY-minY,maxZ-minZ))/2.0;
        return centres+Math.sqrt(3.0)/2.0;
    }
}
