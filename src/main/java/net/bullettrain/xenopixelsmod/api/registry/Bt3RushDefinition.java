package net.bullettrain.xenopixelsmod.combat;

import java.util.Arrays;

/** Immutable timing and animation contract for one automatic cinematic rush. */
public record Bt3RushDefinition(
        String id,
        String animation,
        int durationTicks,
        int[] impactTicks) {

    public static final int DEFAULT_DURATION_TICKS = 28;
    private static final int[] DEFAULT_IMPACTS = {5, 10, 16, 23};

    public Bt3RushDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
        if (animation == null || animation.isBlank()) throw new IllegalArgumentException("animation");
        if (durationTicks <= 0) throw new IllegalArgumentException("durationTicks");
        impactTicks = impactTicks == null ? new int[0] : impactTicks.clone();
        int previous = 0;
        for (int tick : impactTicks) {
            if (tick <= previous || tick >= durationTicks) {
                throw new IllegalArgumentException("impact ticks must be ordered inside duration");
            }
            previous = tick;
        }
    }

    public static Bt3RushDefinition standard(String id, String animationSuffix) {
        return new Bt3RushDefinition(id, "combat.xeno_cinematic_rush_" + animationSuffix,
                DEFAULT_DURATION_TICKS, DEFAULT_IMPACTS);
    }

    @Override
    public int[] impactTicks() {
        return impactTicks.clone();
    }

    public int impactCount() {
        return impactTicks.length;
    }

    public int impactTick(int index) {
        return impactTicks[index];
    }

    public boolean isFinalImpact(int index) {
        return index == impactTicks.length - 1;
    }

    @Override
    public String toString() {
        return id + " " + animation + " " + durationTicks + " " + Arrays.toString(impactTicks);
    }
}
