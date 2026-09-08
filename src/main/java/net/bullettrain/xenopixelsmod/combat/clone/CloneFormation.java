package net.bullettrain.xenopixelsmod.combat.clone;

/**
 * Where a divided fighter's bodies stand, and what dividing costs them.
 *
 * <p>Minecraft-free so the geometry and the mastery ramp can be tested. Both matter to the feel of
 * the technique and neither needs a level to reason about.
 */
public final class CloneFormation {

    /** Mastery at which the split stops costing anything — Cell's perfected multi-form. */
    public static final int PERFECT_MASTERY = 1000;

    private CloneFormation() {
    }

    /**
     * Offset of a body's slot from the formation center, in the fighter's facing frame.
     *
     * <p>Slot 0 sits directly behind the fighter's facing, and the rest are spread evenly around
     * from there. The fighter takes slot 0 themselves — see {@link #backSlot} — so that after the
     * split the real body is the one furthest from whoever they were looking at.
     *
     * @return {@code {x, z}} offset in blocks
     */
    public static double[] slotOffset(float yawDegrees, int index, int total, double radius) {
        if (total <= 0) return new double[]{0.0, 0.0};
        // Minecraft yaw 0 faces +Z, and increases clockwise; this is the same basis the rest of
        // the combat code uses when it turns a yaw into a direction.
        double facing = Math.toRadians(yawDegrees);
        double angle = facing + Math.PI + (2.0 * Math.PI * index) / total;
        return new double[]{-Math.sin(angle) * radius, Math.cos(angle) * radius};
    }

    /** Offset from the owner in the back slot, not from the center of the ring. */
    public static double[] offsetFromOwner(float yawDegrees, int slot, int total, double radius) {
        double[] target = slotOffset(yawDegrees, slot, total, radius);
        double[] owner = slotOffset(yawDegrees, backSlot(total), total, radius);
        return new double[]{target[0] - owner[0], target[1] - owner[1]};
    }

    /**
     * A slot on a ring centred on some other entity, used by Zanzoken.
     *
     * <p>Distinct from {@link #slotOffset}, which orbits the fighter in their own facing frame.
     * Here the ring surrounds the attacker and the dodger stands in it alongside their images, so
     * the offsets are in world space and every slot — including the one the dodger takes — comes
     * from this one function. That is the whole point: if the real body were placed by different
     * maths it would sit differently, and position alone would give the technique away.
     *
     * @return {@code {x, z}} offset from the ring's centre, in blocks
     */
    public static double[] ringOffset(int index, int count, double radius) {
        int n = Math.max(1, count);
        double angle = (2.0 * Math.PI * Math.floorMod(index, n)) / n;
        return new double[]{Math.cos(angle) * radius, Math.sin(angle) * radius};
    }

    /**
     * The slot the fighter's own body takes: directly behind their facing.
     *
     * <p>Standing at the back is the whole point of the swap. An opponent in front sees the copies
     * first, and cannot tell from position alone which body is the one that can be hurt.
     */
    public static int backSlot(int total) {
        return 0;
    }

    /**
     * Eases a body outward from the fighter's position to its slot.
     *
     * @param progress 0 at the moment of the split, 1 when the body has arrived
     * @return fraction of the slot offset to apply
     */
    public static double travelEase(float progress) {
        float p = progress < 0f ? 0f : (progress > 1f ? 1f : progress);
        // Fast out of the body, settling into place — the bodies are thrown clear, not walked out.
        return 1.0 - Math.pow(1.0 - p, 3);
    }

    /**
     * Fraction of full power each body deals.
     *
     * <p>Dividing into four normally means four quarter-strength bodies. Mastery closes that gap,
     * and at {@link #PERFECT_MASTERY} the division costs nothing at all: every body hits full.
     */
    public static float damageShare(int bodies, int mastery) {
        int n = Math.max(1, bodies);
        float base = 1.0f / n;
        float m = Math.max(0, Math.min(PERFECT_MASTERY, mastery)) / (float) PERFECT_MASTERY;
        return base + (1.0f - base) * m;
    }

    /** True once the fighter no longer pays for dividing. */
    public static boolean isPerfected(int mastery) {
        return mastery >= PERFECT_MASTERY;
    }
}
