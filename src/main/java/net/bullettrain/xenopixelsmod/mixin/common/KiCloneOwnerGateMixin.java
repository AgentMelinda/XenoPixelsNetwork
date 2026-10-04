package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import net.bullettrain.xenopixelsmod.combat.clone.CloneCombatBridge;
import net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps an unloaded player-owned clone from falling through to the mob grief gamerule. */
@Mixin(value = AbstractKiProjectile.class, remap = false)
public abstract class KiCloneOwnerGateMixin {
    @Inject(method = "canKiDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void xenopixels$requireCloneOwner(CallbackInfoReturnable<Boolean> cir) {
        AbstractKiProjectile projectile = (AbstractKiProjectile) (Object) this;
        Entity owner = projectile.getOwner();
        if (owner instanceof XenoCloneEntity clone && CloneCombatBridge.owner(clone) == null) {
            cir.setReturnValue(false);
        }
    }
}
