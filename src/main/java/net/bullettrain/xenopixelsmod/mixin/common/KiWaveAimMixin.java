package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import net.bullettrain.xenopixelsmod.combat.technique.KiFixedAim;
import net.minecraft.network.syncher.EntityDataAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Target: {@code KiWaveEntity} synched {@code FIXED_YAW}/{@code FIXED_PITCH}.
 * Reason: static {@code @Accessor} stubs never applied; Guidance then threw
 *         {@code AssertionError} every hold tick (errorcrashclient.txt).
 * Version: NeoForge 1.21.1 / DMZ 2.1.3
 * Side: common. Instance {@code @Shadow} of the static fields.
 */
@Mixin(value = KiWaveEntity.class, remap = false)
public abstract class KiWaveAimMixin implements KiFixedAim {

    @Shadow
    @Final
    private static EntityDataAccessor<Float> FIXED_YAW;

    @Shadow
    @Final
    private static EntityDataAccessor<Float> FIXED_PITCH;

    @Override
    public void xenopixels$setFixedAim(float yaw, float pitch) {
        KiWaveEntity self = (KiWaveEntity) (Object) this;
        self.getEntityData().set(FIXED_YAW, yaw);
        self.getEntityData().set(FIXED_PITCH, pitch);
    }
}
