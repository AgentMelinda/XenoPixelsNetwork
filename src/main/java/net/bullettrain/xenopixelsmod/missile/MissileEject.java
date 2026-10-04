package net.bullettrain.xenopixelsmod.missile;

import net.minecraft.world.phys.Vec3;

/**
 * Silo-rail math for entity missiles. Kept free of {@code Level} so unit tests can cover
 * deep-silo clearance without a running server.
 */
public final class MissileEject {
    public static final int MIN_EJECT_TICKS = 8;
    public static final int MAX_EJECT_TICKS = 20 * 40;
    public static final int DEFAULT_CLEARANCE = 24;

    private MissileEject() {
    }

    public static int clampClearance(int blocks) {
        return Math.max(1, Math.min(128, blocks));
    }

    public static int clampLaunchWorldY(int y) {
        return Math.max(0, Math.min(16_000, y));
    }

    public static boolean finished(int phaseAge, Vec3 origin, Vec3 pos, Vec3 loftDir,
                                   double clearanceBlocks, int launchWorldY) {
        if (phaseAge >= MAX_EJECT_TICKS) return true;
        if (phaseAge < MIN_EJECT_TICKS) return false;
        Vec3 loft = loftDir.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : loftDir.normalize();
        double along = pos.subtract(origin).dot(loft);
        if (along < clearanceBlocks) return false;
        // Launch height is a climb target: only an upward rail can reach it. A sideways or
        // downward rail waiting for it ran the whole MAX_EJECT_TICKS (~5,600 blocks) instead.
        if (loft.y < 0.5) return true;
        return launchWorldY <= 0 || pos.y >= launchWorldY;
    }
}
