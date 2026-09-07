package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import net.bullettrain.xenopixelsmod.compat.npc.NpcNegativeEffectPersistence;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "espi.mynpcs.entity.EntityNPCInterface", remap = false)
public abstract class NpcEffectResetMixin {
    @Inject(method = "reset", at = @At("HEAD"))
    private void xenopixels$beforeReset(CallbackInfo ci) {
        NpcNegativeEffectPersistence.beforeReset((Entity) (Object) this);
    }

    @Inject(method = "reset", at = @At("RETURN"))
    private void xenopixels$afterReset(CallbackInfo ci) {
        NpcNegativeEffectPersistence.afterReset((Entity) (Object) this);
    }
}
