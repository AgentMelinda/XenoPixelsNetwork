package net.bullettrain.xenopixelsmod.space.event;

import java.util.Locale;

/**
 * Pure §23.1 telegraph policy for chart stars: which lifecycle stages are flagged on the Galaxy Atlas, how long a
     * DYING star's ramp has left, and when a stage change is worth a server announcement. Derived only from the S1
 * stage snapshot; it adds no persistence and no packet.
 */
public final class StellarWarning {
    public enum Level { NONE, UNSTABLE, CRITICAL, SUPERNOVA }

    /**
     * @param ticksRemaining game ticks left in the DYING stage's visual ramp; 0 for every other level and once the ramp
     *                       is complete. The warning remains critical through the supernova phase.
     */
    public record Warning(Level level, long ticksRemaining) {}

    private StellarWarning() {}

    /**
     * @param stageAgeTicks ticks since the current stage began; negative values count as 0
     * @param rampTicks     ticks one stage takes to fully ramp; values below 1 count as 1
     */
    public static Warning of(StellarStage stage, long stageAgeTicks, int rampTicks) {
        if (stage == null) return new Warning(Level.NONE, 0L);
        long age = Math.max(0L, stageAgeTicks);
        long ramp = Math.max(1, rampTicks);
        return switch (stage) {
            case STABLE, SWELLING, REMNANT -> new Warning(Level.NONE, 0L);
            case RED_GIANT -> new Warning(Level.UNSTABLE, 0L);
            case DYING -> new Warning(Level.CRITICAL, Math.max(0L, ramp - age));
            case SUPERNOVA -> new Warning(Level.SUPERNOVA, 0L);
        };
    }

    /**
     * True only when a star newly enters an announced stage (RED_GIANT or DYING) from an earlier one. Re-setting the
     * same stage, or moving a star back toward STABLE, stays silent. An unknown previous stage counts as STABLE.
     */
    public static boolean shouldAnnounce(StellarStage previous, StellarStage next) {
        if (next != StellarStage.RED_GIANT && next != StellarStage.DYING
                && next != StellarStage.SUPERNOVA) return false;
        StellarStage from = previous == null ? StellarStage.STABLE : previous;
        return next.ordinal() > from.ordinal();
    }

    /**
     * A nominal 20-TPS display of game ticks: {@code m:ss}, or {@code h:mm:ss} from one hour up. It is not a
     * wall-clock promise; under server lag the same ticks take longer.
     */
    public static String countdown(long ticks) {
        long seconds = Math.max(0L, ticks) / 20L;
        long hours = seconds / 3600L, minutes = (seconds % 3600L) / 60L, rest = seconds % 60L;
        return hours > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
                : String.format(Locale.ROOT, "%d:%02d", minutes, rest);
    }

    /**
     * A readable label from a chart id, for clients that hold ids but not chart display names: the last path segment
     * with underscores as spaces and each word capitalised ({@code xenopixelsmod:sol} becomes {@code Sol}).
     */
    public static String label(String bodyId) {
        if (bodyId == null || bodyId.isBlank()) return "?";
        String path = bodyId.substring(bodyId.indexOf(':') + 1);
        String last = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder out = new StringBuilder();
        for (String word : last.split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? bodyId : out.toString();
    }
}
