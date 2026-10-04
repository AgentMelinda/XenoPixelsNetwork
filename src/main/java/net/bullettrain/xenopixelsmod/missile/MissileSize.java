package net.bullettrain.xenopixelsmod.missile;

/**
 * Physical size tiers for tube-launched entity missiles.
 * {@link #renderScale} drives hitbox/yield; {@link #visualLength} / {@link #visualRadius} are
 * block-space (1 block = 1 m) for the in-world model.
 */
public enum MissileSize {
    SMALL(0.6f, 0.75f, 64, 3.0f, 0.28f),
    MEDIUM(1.0f, 1.0f, 64, 6.0f, 0.40f),
    LARGE(1.6f, 1.5f, 64, 10.0f, 0.45f),
    MEGA(2.4f, 2.0f, 64, 16.0f, 0.80f);

    private static final int STACK = 64;

    private final float renderScale;
    private final float yieldScale;
    private final int stackSize;
    private final float visualLength;
    private final float visualRadius;

    MissileSize(float renderScale, float yieldScale, int stackSize, float visualLength, float visualRadius) {
        this.renderScale = renderScale;
        this.yieldScale = yieldScale;
        this.stackSize = stackSize;
        this.visualLength = visualLength;
        this.visualRadius = visualRadius;
    }

    public float renderScale() {
        return renderScale;
    }

    public float yieldScale() {
        return yieldScale;
    }

    public int stackSize() {
        return stackSize;
    }

    /** Length along the nose axis, in blocks. */
    public float visualLength() {
        return visualLength;
    }

    /** Fuselage half-width, in blocks. */
    public float visualRadius() {
        return visualRadius;
    }

    public float hitbox() {
        return 0.45f * renderScale;
    }

    public static int maxStackSize() {
        return STACK;
    }

    public static MissileSize byOrdinal(int ordinal) {
        MissileSize[] values = values();
        if (ordinal < 0 || ordinal >= values.length) return MEDIUM;
        return values[ordinal];
    }
}
