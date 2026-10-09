package net.bullettrain.xenopixelsmod.combat.v2;

/**
 * What it takes for a target to be a fighter's lock-on. Minecraft-free.
 *
 * <p>XenoCombat v2 is a lock-on system: nothing in it works unless the fighter has a DragonMineZ
 * lock on a target, and every move is made at that target. The lock itself lives on the client
 * and DragonMineZ never tells the server about it, so a v2 input carries the locked target's id
 * and the server checks what it can: that this fighter could hold a lock on that entity at all.
 *
 * <p>These are DragonMineZ's own rules for keeping a lock (its {@code LockOnEvent} drops one that
 * breaks them within a quarter of a second), with the base range this mod already gives it. They
 * cannot prove the client really has the lock, but they do mean a forged input gets nothing a
 * real lock would not have given.
 */
public final class LockRules {

    /** Blocks of range each level of Ki Sense adds. DragonMineZ's figure. */
    public static final double RANGE_PER_LEVEL = 5.0;
    /** Extra range an upgraded android has. DragonMineZ's figure. */
    public static final double ANDROID_BONUS = 25.0;
    /**
     * Blocks allowed beyond the range before a move is refused. The client checks its lock four
     * times a second and the two sides see positions a few ticks apart, so a target at the very
     * edge is briefly further away here than the client thinks.
     */
    public static final double SLACK = 4.0;

    private LockRules() {}

    /** How far away a fighter can hold a lock. */
    public static double range(double baseRange, int kiSenseLevel, boolean androidUpgraded) {
        return baseRange + RANGE_PER_LEVEL * Math.max(0, kiSenseLevel) + (androidUpgraded ? ANDROID_BONUS : 0.0);
    }

    /** Lock-on is the Ki Sense skill: without a level in it there is no lock to have. */
    public static boolean canLock(int kiSenseLevel) {
        return kiSenseLevel > 0;
    }

    /** Whether a target {@code distance} away can be the lock of a fighter with this reach. */
    public static boolean inRange(double distance, double range) {
        return distance <= range + SLACK;
    }
}
