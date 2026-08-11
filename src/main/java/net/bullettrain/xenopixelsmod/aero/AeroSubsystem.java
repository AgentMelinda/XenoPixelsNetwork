package net.bullettrain.xenopixelsmod.aero;

/**
 * Optional controller subsystems that can be switched on and off independently.
 *
 * <p>Each carries the additional power draw it adds while enabled, in FE/tick. The baseline
 * controller draw is not represented here — see {@code AeroPowerBudget}. Ordering matters:
 * {@link AeroBus} sheds subsystems in declaration order as stored energy runs low, so the
 * least flight-critical entry must come first.
 */
public enum AeroSubsystem {
    /** Terrain / map rendering on the navigation console. */
    TERRAIN_MAP(600),
    /** Advanced cooling; permits sustained full-authority flight. */
    ADVANCED_COOLING(1_200),
    /** Engaged flight — thrust and stabilization authority. Shed last. */
    FLIGHT(2_400);

    private final int drawFePerTick;

    AeroSubsystem(int drawFePerTick) {
        this.drawFePerTick = drawFePerTick;
    }

    public int drawFePerTick() {
        return drawFePerTick;
    }
}
