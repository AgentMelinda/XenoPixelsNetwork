package net.bullettrain.xenopixelsmod.combat;

/**
 * Where a Zanzoken ring copy should look, kept Minecraft-free so it can be tested.
 *
 * <p>Default: copy the fighter's look. If they are staring into the ring (at a clone / the
 * centre), copies behind that look face the centre instead of the fighter's back.
 */
public final class ZanzokenLook {

    private ZanzokenLook() {}

    /**
     * @param lookYawDeg   fighter yaw in degrees (Minecraft: 0 = south)
     * @param lookPitchDeg fighter pitch in degrees
     * @param playerX, playerZ fighter position
     * @param cloneX, cloneZ   copy position
     * @param centerX, centerZ ring centre
     * @param radius           ring radius
     * @return {@code true} if this copy should face the centre rather than copy look
     */
    public static boolean faceCenter(double lookYawDeg, double playerX, double playerZ,
                                     double cloneX, double cloneZ,
                                     double centerX, double centerZ, double radius) {
        double yawRad = Math.toRadians(lookYawDeg);
        // Minecraft yaw: 0 looks +Z (south). Forward = (-sin(yaw), +cos(yaw)).
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);
        double toCenterX = centerX - playerX;
        double toCenterZ = centerZ - playerZ;
        double toCenterFlat = Math.hypot(toCenterX, toCenterZ);
        boolean lookingIntoRing = toCenterFlat < 1.0e-4
                || (lookX * toCenterX + lookZ * toCenterZ) / toCenterFlat > 0.25
                && toCenterFlat <= radius + 0.5;
        if (!lookingIntoRing) return false;
        double behindX = cloneX - playerX;
        double behindZ = cloneZ - playerZ;
        return behindX * lookX + behindZ * lookZ < 0.0;
    }

    public static float yawToward(double fromX, double fromZ, double toX, double toZ, float fallback) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        if (Math.hypot(dx, dz) < 1.0e-4) return fallback;
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }
}
