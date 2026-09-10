package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import net.bullettrain.xenopixelsmod.compat.npc.NpcMeleeDamage;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Connects CustomNPCs' real native melee attempt to the configured XenoPixels punch clip. */
@Mixin(targets = "espi.mynpcs.entity.EntityNPCInterface", remap = false)
public abstract class EntityNpcMeleeAnimationMixin {

    @Inject(
            method = "doHurtTarget(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                    shift = At.Shift.BEFORE))
    private void xenopixels$animateCommittedMelee(Entity target,
                                                   CallbackInfoReturnable<Boolean> cir) {
        NpcMeleeDamage.onMeleeAttempt((LivingEntity) (Object) this);
    }
}
