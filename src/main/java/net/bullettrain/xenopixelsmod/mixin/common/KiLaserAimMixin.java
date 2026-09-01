package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import net.bullettrain.xenopixelsmod.combat.technique.KiFixedAim;
import net.minecraft.network.syncher.EntityDataAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Target: {@code KiLaserEntity} synched {@code FIXED_YAW}/{@code FIXED_PITCH}.
 * Reason: same dead static {@code @Accessor} as waves; a laser would crash next.
 * Version: NeoForge 1.21.1 / DMZ 2.1.3
 * Side: common.
 */
@Mixin(value = KiLaserEntity.class, remap = false)
public abstract class KiLaserAimMixin implements KiFixedAim {

    @Shadow
    @Final
    private static EntityDataAccessor<Float> FIXED_YAW;

    @Shadow
    @Final
    private static EntityDataAccessor<Float> FIXED_PITCH;

    @Override
    public void xenopixels$setFixedAim(float yaw, float pitch) {
        KiLaserEntity self = (KiLaserEntity) (Object) this;
        self.getEntityData().set(FIXED_YAW, yaw);
        self.getEntityData().set(FIXED_PITCH, pitch);
    }
}
