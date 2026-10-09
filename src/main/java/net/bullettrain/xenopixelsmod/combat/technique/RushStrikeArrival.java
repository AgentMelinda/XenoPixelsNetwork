package net.bullettrain.xenopixelsmod.combat.technique;

/**
 * Where a Xeno rush strike puts its user. Minecraft-free.
 *
 * <p>DragonMineZ opens every strike by teleporting the attacker to a point 1.3 blocks from the
 * target, on the side the target is <em>not</em> looking toward, and then turning the attacker's
 * view to face it. Against anything that is looking at the attacker, which is most things being
 * fought, that is the far side: the attacker is carried past the target and their view is spun
 * half a turn to find it again.
 *
 * <p>The rush strikes land on the attacker's own side instead: the same distance from the target,
 * on the line the attacker was already on. The target is where it was in the view, only closer,
 * so there is nothing for the camera to find.
 */
public final class RushStrikeArrival {

    /** How far from the target a strike plants its user. DragonMineZ's own figure. */
    public static final double DISTANCE = 1.3;

    /** Distances tried in turn when the spot at the usual one is inside a block. */
    public static final double[] DISTANCES = {DISTANCE, 1.0, 0.7};

    private RushStrikeArrival() {}

    /**
     * The point {@code distance} from the target, on the attacker's side of it.
     *
     * @param attackerYawDeg used only when the attacker is standing in the target and there is no
     *                       side to speak of: they back off the way they are not facing, so they
     *                       end up looking at it
     * @return {@code [x, z]}
     */
    public static double[] nearSide(double attackerX, double attackerZ, double targetX, double targetZ,
                                    float attackerYawDeg, double distance) {
        double dx = attackerX - targetX;
        double dz = attackerZ - targetZ;
        double len = Math.hypot(dx, dz);
        if (len < 1.0e-4) {
            // Minecraft yaw: facing is (-sin, cos), so the way behind the attacker is (sin, -cos).
            double yaw = Math.toRadians(attackerYawDeg);
            dx = Math.sin(yaw);
            dz = -Math.cos(yaw);
            len = 1.0;
        }
        return new double[]{targetX + dx / len * distance, targetZ + dz / len * distance};
    }

    /**
     * Whether the attacker is already standing where a strike would put them, so moving them
     * would only be a visible hop for nothing.
     *
     * @param flatDistance horizontal distance between attacker and target
     * @param heightGap    attacker's feet above (+) or below (-) the target's
     */
    public static boolean alreadyInPlace(double flatDistance, double heightGap) {
        return flatDistance >= 0.9 && flatDistance <= DISTANCE + 0.3 && Math.abs(heightGap) <= 0.25;
    }
}
