package net.bullettrain.xenopixelsmod.compat.sable;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;

/** Carries the initiating projectile only while DMZ constructs a queued terrain job. */
public final class KiTerrainProjectileContext {
    private static final ThreadLocal<AbstractKiProjectile> CURRENT = new ThreadLocal<>();
    private KiTerrainProjectileContext() {}
    public static AbstractKiProjectile current() { return CURRENT.get(); }
    public static AbstractKiProjectile enter(AbstractKiProjectile projectile) {
        AbstractKiProjectile previous = CURRENT.get();
        CURRENT.set(projectile);
        return previous;
    }
    public static void restore(AbstractKiProjectile previous) {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
