package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.KiDiskEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@code KiDiskEntity#tick}
 * Reason: {@code KiBlastEntity#onKiTick} discards at {@code tickCount >= maxLife}.
 *         Disks never do. Charging uses 99999 so {@code tick} will not auto-fire.
 *         After {@code fireHability} the window is {@code 70 * charge} (max 140).
 *         Disks have their own {@code IS_FIRING}; use that, not the parent field.
 * Version: NeoForge 1.21.1 / DMZ 2.1.3
 * Side: common. No block-hit discard (slice stays).
 */
@Mixin(value = KiDiskEntity.class, remap = false)
public abstract class KiDiskLifetimeMixin {

    private static final int CHARGING_LIFE = 99999;
    /** {@code TechniqueDispatcher.resolvePlayerMaxLifeTicks}: DISK 70 * clamp 2.0. */
    private static final int STOCK_DISK_WINDOW = 140;

    @Inject(method = "tick", at = @At("TAIL"))
    private void xenopixels$expireDisk(CallbackInfo ci) {
        KiDiskEntity self = (KiDiskEntity) (Object) this;
        if (self.level().isClientSide || !self.isFiring()) return;
        int fireTick = self.getFireTick();
        int max = self.getMaxLife();
        int window;
        if (max != CHARGING_LIFE && fireTick >= 0) {
            window = Math.max(1, max - fireTick);
        } else if (max != CHARGING_LIFE) {
            window = Math.max(1, max - self.tickCount);
            if (self.tickCount >= max) {
                self.discard();
            }
            return;
        } else if (fireTick >= 0) {
            window = STOCK_DISK_WINDOW;
        } else {
            return;
        }
        int start = fireTick >= 0 ? fireTick : self.tickCount;
        if (self.tickCount >= start + window) {
            self.discard();
        }
    }
}
