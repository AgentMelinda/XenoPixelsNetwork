package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Holds a sustained beam to the configured maximum reach.
 *
 * <p>Nothing in DragonMineZ bounds how long a beam may grow. {@code KiWaveEntity.tick} does
 * {@code targetLen = currentLen + currentSpeed} every tick for as long as the beam lives, so with
 * beam surge keeping it alive a wave reaches as far as its owner can afford to feed it. This is the
 * server's stop, applied to waves and lasers alike rather than only to surged ones.
 *
 * <p><b>Why a mixin rather than throttling speed.</b> The obvious alternative — dropping
 * {@code kiSpeed} to zero once the beam is long enough — destroys the beam instead of holding it,
 * because the same tick treats {@code currentSpeed < 0.05F} as the signal to explode and die. The
 * length field is the only place the cap can be applied without changing what the beam does when it
 * gets there.
 *
 * <p>Both target classes declare an identical {@code private void setBeamLength(float)} and feed it
 * from their own growth path, and both extend {@link AbstractKiProjectile}, so one mixin covers the
 * pair. Growth is server-side only (guarded by {@code !level().isClientSide}); clients receive the
 * already-capped value through the synched {@code BEAM_LENGTH} entity data.
 */
@Mixin(value = {KiWaveEntity.class, KiLaserEntity.class}, remap = false)
public abstract class KiBeamLengthMixin {

    @ModifyVariable(method = "setBeamLength", at = @At("HEAD"), argsOnly = true)
    private float xenopixels$capBeamLength(float length) {
        // A clash owns its beams' length outright and drives both ends to meet at the lock point.
        // Clamping that would fight BeamClash for the same field, which is the same reason
        // BeamSurgeManager skips clash-locked beams entirely.
        if (((AbstractKiProjectile) (Object) this).isClashLocked()) return length;
        return XenoServerConfig.clampBeamLength(length);
    }
}
