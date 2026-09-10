package net.bullettrain.xenopixelsmod.combat;

/**
 * The ceiling XenoPixels puts on a DragonMineZ stat, overriding DMZ's configured one.
 *
 * <p>DragonMineZ caps every stat at {@code gameplay.maxValue}, which its own getter clamps into
 * {@code [1000, Integer.MAX_VALUE]}. Every cap in the mod funnels through that one getter --
 * {@code Stats.clampStatValue}, which is what the setters call, and {@code
 * StatsData.getConfiguredMaxValue}, which the {@code +} button and the total-points budget read --
 * so overriding it there raises all of them at once, and none of DragonMineZ's own arithmetic is
 * touched.
 *
 * <p><b>2,147,483,647 is a wall, not a policy.</b> DragonMineZ stores each stat in an {@code int}
 * field on {@code Stats} ({@code private int strength} and its five siblings). Nothing this mod can
 * configure raises that; a stat above it would silently wrap negative. So {@code off} means "as high
 * as DragonMineZ can physically hold", not "unlimited", and this class is the one place that says so.
 *
 * <p>Kept free of Minecraft and DragonMineZ types so the rule itself is unit tested rather than
 * eyeballed on a live server, where a wrong ceiling means either a cap that will not lift or a stat
 * that wraps.
 */
public final class XenoStatCeiling {

    /** DragonMineZ's own floor for the setting; below this its getter would raise it back anyway. */
    public static final int MIN = 1000;

    /**
     * The highest a DragonMineZ stat can physically be.
     *
     * <p>{@code Stats} holds each one in an {@code int}, so this is {@link Integer#MAX_VALUE} and
     * not a number anybody chose.
     */
    public static final int MAX = Integer.MAX_VALUE;

    /** The override is off: DragonMineZ's own configured maximum applies, untouched. */
    public static final int OFF = 0;

    private XenoStatCeiling() {
    }

    /** True when a stored setting means "leave DragonMineZ's configured maximum alone". */
    public static boolean deferring(int setting) {
        return setting <= OFF;
    }

    /**
     * The stored form of a requested ceiling.
     *
     * <p>Anything at or below zero stores as {@link #OFF} rather than as itself, so "off", "0" and a
     * hand-edited negative all mean the same thing and none of them can be read back as a real
     * ceiling of zero -- which would pin every stat to nothing.
     */
    public static int store(int requested) {
        if (requested <= OFF) return OFF;
        return clamp(requested);
    }

    /**
     * The ceiling to report to DragonMineZ, given the stored setting and what DMZ itself configured.
     *
     * @param setting  the stored Xeno override, or {@link #OFF}
     * @param dmzValue what DragonMineZ's own configuration says
     */
    public static int effective(int setting, int dmzValue) {
        return deferring(setting) ? dmzValue : clamp(setting);
    }

    /** A ceiling forced into the range DragonMineZ can actually hold. */
    public static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }
}
