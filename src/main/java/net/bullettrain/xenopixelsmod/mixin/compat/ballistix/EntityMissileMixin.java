package net.bullettrain.xenopixelsmod.mixin.compat.ballistix;

import ballistix.common.entity.EntityMissile;
import net.bullettrain.xenopixelsmod.compat.ballistix.BallistixVs2Compat;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ballistix entity missile ↔ VS2: wake ships, fix shipyard spawn coords.
 * Applied only when ballistix+voltaic present ({@link net.bullettrain.xenopixelsmod.mixin.ConditionalMixinPlugin}).
 */
@Mixin(EntityMissile.class)
public abstract class EntityMissileMixin {
    /** Entity.tick — remapped (vanilla override). */
    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void xenopixels$missileTick(CallbackInfo ci) {
        BallistixVs2Compat.onMissileEntityTick((Entity) (Object) this);
    }
}
