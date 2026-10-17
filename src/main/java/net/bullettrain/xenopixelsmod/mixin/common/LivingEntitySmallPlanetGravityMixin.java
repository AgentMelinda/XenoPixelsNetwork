package net.bullettrain.xenopixelsmod.mixin.common;

import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetGravity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the server-authoritative/client-predicted force at the only vanilla player gravity path. */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySmallPlanetGravityMixin {
    @Inject(method = "travel(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"))
    private void xenopixels$applySmallPlanetGravity(Vec3 travelVector, CallbackInfo ci) {
        SmallPlanetGravity.apply((LivingEntity) (Object) this);
    }
}
