package net.bullettrain.xenopixelsmod.combat.v3.ki;

import net.minecraft.world.phys.Vec3;

/** Where a V3 ki shot is, how far a beam reaches and how an attack's damage is shared. Pure. */
public final class V3KiPath {
    /** Blocks a tick a beam's head travels. */
    public static final double BEAM_SPEED = 4.0;
    /** A beam that has not arrived by here fades without hitting. */
    public static final double BEAM_RANGE = 96.0;
    public static final double SEGMENT = net.bullettrain.xenopixelsmod.fx.ki.KiLook.SEGMENT;
    public static final int MAX_LENGTHS = net.bullettrain.xenopixelsmod.fx.ki.KiLook.MAX_LENGTHS;

    private V3KiPath() {}

    static Vec3 advance(Vec3 from, Vec3 to, double speed) {
        Vec3 gap = to.subtract(from);
        double distance = gap.length();
        if (!(distance > speed) || !(speed > 0)) return to;
        return from.add(gap.scale(speed / distance));
    }

    /** Whether this tick's step from {@code from} gets within {@code radius} of {@code to}. */
    static boolean reaches(Vec3 from, Vec3 to, double speed, double radius) {
        return from.distanceTo(to) <= speed + radius;
    }

    /** How many beam lengths cover {@code reach} blocks. */
    static int lengths(double reach, double spacing) {
        return net.bullettrain.xenopixelsmod.fx.ki.KiLook.lengths(reach, spacing);
    }

    static Vec3 lengthStart(Vec3 origin, Vec3 direction, int index, double spacing) {
        return origin.add(direction.scale(index * spacing));
    }

    /** The head's distance from the hands after one more tick, towards a target {@code distance} away. */
    static double beamReach(double reach, double distance) {
        return Math.min(reach + BEAM_SPEED, Math.min(distance, BEAM_RANGE));
    }

    /** Whether a sustained beam still physically reaches its moving target. */
    static boolean beamContact(double reach, double distance, double radius) {
        return Double.isFinite(reach) && Double.isFinite(distance) && Double.isFinite(radius)
                && reach >= 0.0 && distance >= 0.0 && radius >= 0.0 && distance <= reach + radius;
    }

    /** Ticks after the release beat that volley shot {@code index} leaves the hands. */
    static int volleyDelay(int index) {
        return Math.max(0, index) * 2;
    }

    /** -1 for a shot from the left hand, 1 from the right. */
    static int volleySide(int index) {
        return (index & 1) == 0 ? -1 : 1;
    }

    static float share(float total, int count) {
        return count <= 1 ? total : total / count;
    }
}
