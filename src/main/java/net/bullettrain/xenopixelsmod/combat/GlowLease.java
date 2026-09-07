package net.bullettrain.xenopixelsmod.combat;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Owns only the glow this channel actually enabled; release is idempotent. */
final class GlowLease {
    private final Consumer<Boolean> setter;
    private boolean owned;

    GlowLease(BooleanSupplier getter, Consumer<Boolean> setter, boolean enabled) {
        this.setter = setter;
        owned = enabled && !getter.getAsBoolean();
        if (owned) setter.accept(true);
    }

    void release() {
        if (!owned) return;
        owned = false;
        setter.accept(false);
    }
}
