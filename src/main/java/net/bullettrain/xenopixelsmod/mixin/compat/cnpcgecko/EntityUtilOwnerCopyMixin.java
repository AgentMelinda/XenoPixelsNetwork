package net.bullettrain.xenopixelsmod.mixin.compat.cnpcgecko;

import net.bullettrain.xenopixelsmod.client.compat.npc.NpcGeckoOwner;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "noppes.npcs.client.EntityUtil", remap = false)
public abstract class EntityUtilOwnerCopyMixin {
    @Inject(method = "Copy", at = @At("RETURN"))
    private static void xenopixels$rememberOwner(LivingEntity original, LivingEntity copy, CallbackInfo ci) {
        if (copy instanceof NpcGeckoOwner owner) {
            owner.xenopixels$setNpcOwner(original);
        }
    }
}
