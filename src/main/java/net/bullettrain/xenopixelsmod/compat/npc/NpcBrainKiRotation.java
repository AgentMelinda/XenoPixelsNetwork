package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.stats.techniques.KiAttackData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Picks the next ready ki attack by type rather than always the first id in the list.
 *
 * <p>Order: WAVE, ball/blast, DISK, then named (laser/beam/barrage and everything else).
 * Generic {@code kiblast}/{@code kiwave}/{@code kienzan} fill in when the NPC has no ready
 * named technique of an allowed type.
 */
public final class NpcBrainKiRotation {
    public enum Band {
        WAVE,
        BLAST,
        DISK,
        NAMED
    }

    /** Same-height band: closer than this and the look is nearly 90° up. */
    public static final double LEVEL_DY = 2.0;
    /** Horizontal distance treated as stacked under/over the target. */
    public static final double STACKED_XZ = 3.0;
    /** Back away until about this far so pitch stays a diagonal. */
    public static final double STANDOFF_XZ = 5.0;
    /**
     * Search-fly speed, blocks per tick. Matches DragonMineZ saga NPC
     * {@code DBSagasEntity.getFlySpeed()} default (0.35), not player sprint-fly.
     */
    public static final double FLY_SPEED = 0.35;
    /** {@code DBSagasEntity.moveTowardsTargetInAir}: sprint-fly beyond this 3D range. */
    public static final double FAST_FLY_RANGE = 15.0;
    /** Drop sprint-fly once closer than this. */
    public static final double FAST_FLY_DROP = 7.0;
    public static final double FAST_FLY_MULT = 2.0;
    /** One block between normal-sized entity centers during an air chase. */
    public static final double AIR_HOVER = 1.0;
    /** Speed gain for the last few blocks of approach; prevents close-range overshoot. */
    public static final double AIR_ARRIVAL_GAIN = 0.65;
    /** Extra distance past the hover gap that still counts as "arrived". */
    static final double AIR_ARRIVAL_DEADBAND = 0.25;
    /** Slowest horizontal chase speed, so moving never degrades into a crawl at the boundary. */
    static final double AIR_MIN_CHASE = 0.06;
    /** DMZ saga takeoff threshold. Active flight ends when the target lands, not at matching height. */
    public static final double FLY_START_DY = 4.0;
    /** Aim a block above the target's feet, as {@code moveTowardsTargetInAir} does. */
    public static final double AIR_AIM_Y = 1.0;
    /** Shared cooldown for generic kiblast/kiwave. */
    public static final int GENERIC_KI_COOLDOWN = 50;
    public static final int CLASH_WAVE_LIFE = 120;

    private NpcBrainKiRotation() {}

    public static Band bandOf(String id) {
        if (id == null || id.isBlank()) {
            return Band.NAMED;
        }
        String key = id.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "kiblast" -> Band.BLAST;
            case "kiwave", "kihame", "kamehame" -> Band.WAVE;
            case "kienzan", "kienzan_doble" -> Band.DISK;
            default -> {
                KiAttackData data = PredefinedTechniqueLookup.find(key);
                if (data == null) {
                    yield Band.NAMED;
                }
                yield switch (data.getKiType()) {
                    case WAVE -> Band.WAVE;
                    case SMALL_BALL, MEDIUM_BALL, GIANT_BALL -> Band.BLAST;
                    case DISK -> Band.DISK;
                    default -> Band.NAMED;
                };
            }
        };
    }

    public static boolean allows(NpcCombatProfile profile, Band band) {
        if (profile == null || band == null) {
            return false;
        }
        // The "fully DragonMineZ" brain has no sub-toggles at all, and these per-band flags are
        // read here rather than through NpcCombatProfile.allowBrainAction - so the bypass has to be
        // repeated. Without it V7 would still be silently bound by Brain-tab switches the editor no
        // longer shows, which is worse than a dead control: an invisible one.
        if (profile.brainVersion != null && !profile.brainVersion.honoursToggles()) {
            return true;
        }
        return switch (band) {
            case WAVE -> profile.brainKiWave && profile.brainChance("kiWave") > 0;
            case BLAST -> profile.brainKiBlast && profile.brainChance("kiBlast") > 0;
            case DISK -> profile.brainKiDisk && profile.brainChance("kiDisk") > 0;
            case NAMED -> profile.brainKiNamed && profile.brainChance("kiNamed") > 0;
        };
    }

    /**
     * Next ready technique id, or a generic fallback, or {@code null} when nothing is allowed.
     *
     * @param cursor incremented by the caller after a successful fire
     */
    public static String pick(NpcCombatProfile profile, int cursor, Predicate<String> ready) {
        return pick(profile, cursor, ready, null);
    }

    /**
     * As {@link #pick(NpcCombatProfile, int, Predicate)}, but tries {@code preferred} bands first.
     *
     * <p>This is what makes range matter. At mid range an NPC should be throwing blasts and disks;
     * out near the edge of {@code OUT_RANGE} it should be winding up waves and named attacks. Both
     * used to draw from one undifferentiated rotation, so distance changed nothing about what came
     * out. A preference rather than a restriction: an NPC that only knows waves still fires them up
     * close instead of standing there doing nothing.
     *
     * @param preferred bands to try first, or null/empty for the plain rotation
     */
    public static String pick(NpcCombatProfile profile, int cursor, Predicate<String> ready,
                              java.util.Set<Band> preferred) {
        if (profile == null) {
            return null;
        }
        if (preferred != null && !preferred.isEmpty()) {
            String inBand = rotate(profile, cursor, ready, preferred);
            if (inBand != null) {
                return inBand;
            }
        }
        String any = rotate(profile, cursor, ready, null);
        if (any != null) {
            return any;
        }
        if (allows(profile, Band.WAVE)) {
            return "kiwave";
        }
        if (allows(profile, Band.BLAST)) {
            return "kiblast";
        }
        if (allows(profile, Band.DISK)) {
            return "kienzan";
        }
        return null;
    }

    /**
     * The technique at {@code cursor} among those this profile allows and that are ready, optionally
     * narrowed to {@code bands}. Null when nothing qualifies.
     */
    private static String rotate(NpcCombatProfile profile, int cursor, Predicate<String> ready,
                                 java.util.Set<Band> bands) {
        List<String> allowed = new ArrayList<>();
        for (String technique : profile.techniques) {
            if (technique == null || technique.isBlank()) {
                continue;
            }
            if (PredefinedTechniqueLookup.findStrike(technique) != null) {
                continue;
            }
            Band band = bandOf(technique);
            if (!allows(profile, band)) {
                continue;
            }
            if (bands != null && !bands.contains(band)) {
                continue;
            }
            if (ready != null && !ready.test(technique)) {
                continue;
            }
            allowed.add(technique.toLowerCase(Locale.ROOT));
        }
        return allowed.isEmpty() ? null : allowed.get(Math.floorMod(cursor, allowed.size()));
    }

    /**
     * Melee-first special gating: a ready special fires only on roll {@code 0} of
     * {@code 0..meleeWeight} (weight 3 → 25% special / 75% melee).
     */
    public static boolean useSpecialThisTick(int readyCount, int meleeWeightRoll) {
        return readyCount > 0 && meleeWeightRoll == 0;
    }

    /** Fly when the brain fly flag is on and the target is airborne; land when they are grounded. */
    public static boolean shouldFly(boolean brainFly, boolean targetOnGround, boolean targetClearlyAbove) {
        if (!brainFly) {
            return false;
        }
        return !targetOnGround || targetClearlyAbove;
    }

    /** DMZ saga takeoff: start flying when an airborne target is at least 4 blocks above. */
    public static boolean shouldFlyV3(boolean brainFly, double dy) {
        return shouldFlyV3(brainFly, dy, false, false);
    }

    /**
     * Keeps an active air chase for as long as the target is airborne. Releasing at a small
     * height difference made the NPC repeatedly fall and take off again beside its target.
     * A grounded target acts like DMZ's flight key being switched off.
     */
    public static boolean shouldFlyV3(boolean brainFly, double dy, boolean alreadyFlying) {
        return shouldFlyV3(brainFly, dy, alreadyFlying, false);
    }

    public static boolean shouldFlyV3(boolean brainFly, double dy, boolean alreadyFlying,
                                      boolean targetOnGround) {
        if (!brainFly || targetOnGround || !Double.isFinite(dy)) {
            return false;
        }
        return alreadyFlying || dy >= FLY_START_DY;
    }

    /**
     * DMZ land: NPC is grounded and the target is no longer clearly above it.
     */
    public static boolean shouldLandV3(boolean onGround, double dy) {
        return onGround && (!Double.isFinite(dy) || dy < 1.0);
    }

    public static double horizontal(double dx, double dz) {
        return Math.hypot(dx, dz);
    }

    public static boolean needsClimb(double dy) {
        return Math.abs(dy) > LEVEL_DY;
    }

    public static boolean stackedForAim(double horizontal, double dy) {
        return needsClimb(dy) && horizontal < STACKED_XZ;
    }

    public static boolean canFight(double horizontal, double dy) {
        return !needsClimb(dy) && !stackedForAim(horizontal, dy);
    }

    public static boolean suppressTeleports(boolean brainFly, boolean steering, boolean stacked,
                                           boolean climbing) {
        return stacked || climbing || (brainFly && steering);
    }

    public static boolean mayFireKi(boolean climbing, boolean stacked) {
        return !climbing && !stacked;
    }

    /**
     * XZ velocity away from the target so pitch is a diagonal instead of 90° up.
     *
     * @return {@code {vx, vz}}
     */
    public static double[] standoffVelocity(double npcX, double npcZ, double targetX, double targetZ) {
        return standoffVelocity(npcX, npcZ, targetX, targetZ, FLY_SPEED);
    }

    public static double[] standoffVelocity(double npcX, double npcZ, double targetX, double targetZ,
                                            double speed) {
        double awayX = npcX - targetX;
        double awayZ = npcZ - targetZ;
        double horiz = Math.hypot(awayX, awayZ);
        double cap = Math.max(0.05, speed);
        if (horiz < 1.0e-4) {
            return new double[] {cap, 0.0};
        }
        double scale = cap / horiz;
        return new double[] {awayX * scale, awayZ * scale};
    }

    /**
     * Search-fly velocity along the 3D line to the target.
     *
     * @return {@code {vx, vy, vz}}
     */
    public static double[] climbVelocity(double dx, double dy, double dz) {
        return scaledToward(dx, dy, dz, FLY_SPEED);
    }

    public static double[] climbVelocity(double dx, double dy, double dz, double speed) {
        return scaledToward(dx, dy, dz, speed);
    }

    /** Level flight toward the target, holding height. */
    public static double[] approachVelocity(double dx, double dy, double dz) {
        return scaledToward(dx, dy, dz, FLY_SPEED);
    }

    public static double[] approachVelocity(double dx, double dy, double dz, double speed) {
        return scaledToward(dx, dy, dz, speed);
    }

    /**
     * Saga air chase: approach on XZ to a stable one-block standoff, and independently make small
     * vertical corrections toward {@code target.y + 1}. Splitting those axes prevents vertical
     * error from pulling the NPC sideways or making its horizontal approach reverse at close range.
     *
     * @return {@code {vx, vy, vz}}
     */
    public static double[] airChaseVelocity(double dx, double dyToFeet, double dz, double speed) {
        // Keep the historical standoff for other callers such as Multi-Form copies.
        return airChaseVelocity(dx, dyToFeet, dz, speed, 3.0);
    }

    /** Increase the gap only for hitboxes that cannot safely fit a one-block center gap. */
    public static double airHoverDistance(double npcWidth, double targetWidth) {
        return Math.max(AIR_HOVER, (Math.max(0.0, npcWidth) + Math.max(0.0, targetWidth)) * 0.5 + 0.1);
    }

    public static double[] airChaseVelocity(double dx, double dyToFeet, double dz, double speed,
                                            double hoverDistance) {
        double horizontal = Math.hypot(dx, dz);
        double dy = dyToFeet + AIR_AIM_Y;
        double distance = Math.hypot(horizontal, dy);
        double maxSpeed = flySpeedForDistance(distance, speed);
        double gap = Math.max(AIR_HOVER, hoverDistance);
        // A deadband past the gap plus a minimum cruise: with a bare linear ramp the NPC sat on the
        // boundary, creeping at near-zero speed and flipping between "moving" and "hovering" every
        // few ticks, which is what made the body swap between travel yaw and target yaw.
        double horizontalSpeed = horizontal <= gap + AIR_ARRIVAL_DEADBAND ? 0.0
                : Math.min(maxSpeed, Math.max(AIR_MIN_CHASE, (horizontal - gap) * AIR_ARRIVAL_GAIN));
        double vx = horizontal < 1.0e-6 ? 0.0 : dx / horizontal * horizontalSpeed;
        double vz = horizontal < 1.0e-6 ? 0.0 : dz / horizontal * horizontalSpeed;
        double vy = Math.max(-maxSpeed, Math.min(maxSpeed, dy * 0.25));
        double actualSpeed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (actualSpeed > maxSpeed && actualSpeed > 1.0e-9) {
            double scale = maxSpeed / actualSpeed;
            vx *= scale;
            vy *= scale;
            vz *= scale;
        }
        return new double[] {vx, vy, vz};
    }

    /** Sprint-fly beyond {@link #FAST_FLY_RANGE}, cruise inside {@link #FAST_FLY_DROP}. */
    public static double flySpeedForDistance(double distance, double base) {
        double cap = Math.max(0.05, base);
        if (distance > FAST_FLY_RANGE) {
            return cap * FAST_FLY_MULT;
        }
        return cap;
    }

    /**
     * Body/head yaw toward the target's XZ. Same formula as
     * {@code DBSagasEntity.rotateBodyToTarget}: {@code atan2(dz, dx) * 180/π - 90}.
     * Pitch stays 0 — DMZ does not pitch the saga NPC into a 90° climb pose.
     */
    public static float targetYaw(double dx, double dz) {
        return net.minecraft.util.Mth.wrapDegrees((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
    }

    /** Keep the current facing when the target is directly above/below or almost overlapping. */
    public static float targetYaw(double dx, double dz, float currentYaw) {
        return dx * dx + dz * dz <= 0.01 ? currentYaw : targetYaw(dx, dz);
    }

    public static double[] scaledToward(double dx, double dy, double dz, double speed) {
        double cap = Math.max(0.05, speed);
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-4) {
            return new double[] {0.0, cap, 0.0};
        }
        double scale = cap / len;
        return new double[] {dx * scale, dy * scale, dz * scale};
    }

    public static float travelYaw(double dx, double dz) {
        return net.minecraft.util.Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)));
    }

    /**
     * Turns {@code current} toward {@code target} by at most {@code maxStep} degrees the short
     * way round; the result is wrapped to [-180, 180).
     */
    public static float slew(float current, float target, float maxStep) {
        float delta = net.minecraft.util.Mth.wrapDegrees(target - current);
        float step = Math.max(-maxStep, Math.min(maxStep, delta));
        return net.minecraft.util.Mth.wrapDegrees(current + step);
    }

    public static float travelPitch(double dx, double dy, double dz) {
        double horiz = Math.hypot(dx, dz);
        return (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
    }

    public static boolean shouldAnswerBeam(boolean alreadyClashing, boolean brainKiWave,
                                           boolean victimHasMajorBeam) {
        return !alreadyClashing && brainKiWave && victimHasMajorBeam;
    }

    /** Named WAVE from the Techs list, else generic {@code kiwave}. Never a blast. */
    public static String clashAnswerId(NpcCombatProfile profile) {
        if (profile != null) {
            for (String technique : profile.techniques) {
                if (technique == null || technique.isBlank()) {
                    continue;
                }
                if (bandOf(technique) == Band.WAVE) {
                    return technique.toLowerCase(Locale.ROOT);
                }
            }
        }
        return "kiwave";
    }
}
