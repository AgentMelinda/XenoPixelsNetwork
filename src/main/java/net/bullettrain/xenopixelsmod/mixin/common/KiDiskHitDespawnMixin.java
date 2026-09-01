package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiDiskEntity;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: {@code AbstractKiProjectile#onSuccessfulHit}
 * Reason: a ki disk never goes away when it hits something. Only disks are affected.
 * Version: NeoForge 1.21.1 / DMZ 2.1.3
 * Side: common; the body is server-only.
 *
 * <p>DragonMineZ gives a disk a hit budget — {@code getMaxHits()} — but uses it purely to divide
 * the attack's damage ({@code getDamagePerHit = kiDamage / maxHits}). Nothing counts hits and
 * nothing removes the entity on contact, so a Kienzan that has already delivered its whole damage
 * pool keeps hanging in the target for the rest of its lifetime window, dealing nothing. That
 * reads as an attack that failed to despawn.
 *
 * <p>Counting the same budget and ending the disk when it is spent adds no damage and removes
 * none: by DMZ's own accounting the attack is finished at that point. The lifetime window in
 * {@code KiDiskLifetimeMixin} still applies to a disk that never hits anything.
 */
@Mixin(value = AbstractKiProjectile.class, remap = false)
public abstract class KiDiskHitDespawnMixin {

    @Unique
    private int xenopixels$diskHits;

    @Inject(method = "onSuccessfulHit", at = @At("TAIL"))
    private void xenopixels$endSpentDisk(Entity victim, CallbackInfo ci) {
        AbstractKiProjectile self = (AbstractKiProjectile) (Object) this;
        if (!(self instanceof KiDiskEntity disk)) return;
        if (disk.level().isClientSide || !disk.isAlive()) return;
        if (!XenoServerConfig.kiDiskDespawnOnHitBudget) return;

        // A budget below one would end the disk on its first pulse regardless of the setting.
        int budget = Math.max(1, disk.getMaxHits());
        if (++xenopixels$diskHits >= budget) {
            disk.discard();
        }
    }
}
