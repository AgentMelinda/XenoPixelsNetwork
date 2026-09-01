package net.bullettrain.xenopixelsmod.combat.overcharge;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;

/**
 * Shared "write this ki field only if it moved" helper.
 *
 * <p>{@code setSize} calls {@code refreshDimensions()} and is the expensive one, so both
 * beam surge and charge overcharge refuse to touch a value that has not actually changed.
 */
public final class KiProjectileApply {

    /** A hundredth of a block is below anything observable on a volumetric scan. */
    public static final float SIZE_EPSILON = 0.01f;
    public static final float DAMAGE_EPSILON = 0.05f;

    private KiProjectileApply() {
    }

    public static void size(AbstractKiProjectile ki, float size) {
        if (ki == null) return;
        float clamped = XenoServerConfig.clampKiSize(size);
        if (Math.abs(clamped - ki.getSize()) > SIZE_EPSILON) {
            ki.setSize(clamped);
        }
    }

    public static void damage(AbstractKiProjectile ki, float damage) {
        if (ki == null || !Float.isFinite(damage)) return;
        if (Math.abs(damage - ki.getKiDamage()) > DAMAGE_EPSILON) {
            ki.setKiDamage(damage);
        }
    }

    public static void speed(AbstractKiProjectile ki, float speed) {
        if (ki == null) return;
        float clamped = XenoServerConfig.clampKiSpeed(speed);
        if (clamped != ki.getKiSpeed()) {
            ki.setKiSpeed(clamped);
        }
    }
}
