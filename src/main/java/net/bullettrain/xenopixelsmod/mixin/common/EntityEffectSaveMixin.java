package net.bullettrain.xenopixelsmod.mixin.common;

import net.bullettrain.xenopixelsmod.compat.npc.NpcNegativeEffectPersistence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityEffectSaveMixin {
    @Inject(method = "saveWithoutId", at = @At("HEAD"))
    private void xenopixels$refreshEffects(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        NpcNegativeEffectPersistence.beforeSave((Entity) (Object) this);
    }
}
