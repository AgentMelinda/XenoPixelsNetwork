package net.bullettrain.xenopixelsmod.combat.combo;

/**
 * Knockback path: flattened XZ away plus explicit up, or steep look-down plus explicit down.
 *
 * <p>Minecraft pitch is negative when looking up. {@code verticalPitchDeg} is the absolute
 * threshold that collapses a steep look into a vertical line.
 */
public final class RushKnockbackPath {

    private RushKnockbackPath() {
    }

    /**
     * Horizontal away from the attacker plus an explicit up/down so the victim always leaves
     * on a diagonal. A steep look-down uses {@code down}; every other look, including steep
     * look-up, uses {@code up} so rush combo launches diagonally upward instead of flat or
     * straight vertical.
     *
     * @return {@code [x, y, z]} impulse
     */
    public static double[] impulse(double lookX, double lookY, double lookZ,
                                   double awayX, double awayY, double awayZ,
                                   double distance, double up, double down,
                                   double verticalPitchDeg, double pitchDeg) {
        double dist = Math.max(0.0, distance);
        double upMag = Math.max(0.0, up);
        double downMag = Math.max(0.0, down);
        double threshold = Math.max(0.0, Math.min(89.0, verticalPitchDeg));
        double ax = awayX;
        double az = awayZ;
        if (ax * ax + az * az < 1.0e-8) {
            ax = lookX;
            az = lookZ;
        }
        double horiz = Math.sqrt(ax * ax + az * az);
        double nx = horiz > 1.0e-8 ? ax / horiz : 0.0;
        double nz = horiz > 1.0e-8 ? az / horiz : 0.0;
        if (pitchDeg >= threshold) {
            return new double[]{nx * dist, -downMag, nz * dist};
        }
        return new double[]{nx * dist, upMag, nz * dist};
    }
}
