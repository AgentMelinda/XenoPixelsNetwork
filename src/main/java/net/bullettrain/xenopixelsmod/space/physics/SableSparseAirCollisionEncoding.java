package net.bullettrain.xenopixelsmod.space.physics;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Scope and exact air-state predicate for the optional Sable 2.0.3 section-encoding fast path. */
public final class SableSparseAirCollisionEncoding {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState CAVE_AIR = Blocks.CAVE_AIR.defaultBlockState();
    private static final BlockState VOID_AIR = Blocks.VOID_AIR.defaultBlockState();
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final Scope DISABLED = new Scope(false);

    private SableSparseAirCollisionEncoding() {}

    /** Only the exact vanilla air states are skipped; modded air-like blocks retain Sable's full path. */
    public static boolean isVanillaAir(BlockState state) {
        return state == AIR || state == CAVE_AIR || state == VOID_AIR;
    }

    /** Activates the mixin fast path only around one generated-body section callback. */
    public static Scope enter(boolean enabled) {
        if (!enabled) return DISABLED;
        DEPTH.set(DEPTH.get() + 1);
        return new Scope(true);
    }

    public static boolean active() { return DEPTH.get() > 0; }

    public static final class Scope implements AutoCloseable {
        private final boolean entered;
        private boolean closed;

        private Scope(boolean entered) { this.entered = entered; }

        @Override public void close() {
            if (!entered || closed) return;
            closed = true;
            int depth = DEPTH.get() - 1;
            if (depth <= 0) DEPTH.remove();
            else DEPTH.set(depth);
        }
    }
}
