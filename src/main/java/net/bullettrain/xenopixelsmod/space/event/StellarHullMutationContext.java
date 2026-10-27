package net.bullettrain.xenopixelsmod.space.event;

import java.util.function.BooleanSupplier;

/** Marks the synchronous Sable-backed hull edit so dependent-block cleanup avoids host-world loot/effects. */
public final class StellarHullMutationContext {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

    private StellarHullMutationContext() {}

    public static boolean active() { return Boolean.TRUE.equals(ACTIVE.get()); }

    public static boolean run(BooleanSupplier mutation) {
        Boolean previous = ACTIVE.get();
        ACTIVE.set(Boolean.TRUE);
        try {
            return mutation.getAsBoolean();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }
}
