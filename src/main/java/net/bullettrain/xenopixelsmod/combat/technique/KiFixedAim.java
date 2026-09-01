package net.bullettrain.xenopixelsmod.combat.technique;

/**
 * Duck implemented by wave/laser mixins. Writes DMZ {@code FIXED_YAW}/{@code FIXED_PITCH}
 * synched data. Static Mixin accessors on those fields never applied and threw
 * {@code AssertionError} from {@code KiGuidance.applyBeamAim}.
 */
public interface KiFixedAim {
    void xenopixels$setFixedAim(float yaw, float pitch);
}
