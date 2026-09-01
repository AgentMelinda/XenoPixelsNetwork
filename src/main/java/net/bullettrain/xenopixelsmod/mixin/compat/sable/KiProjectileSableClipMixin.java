package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bullettrain.xenopixelsmod.compat.sable.SableKiClip;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

/**
 * Target: {@code AbstractKiProjectile#tick} → {@code ProjectileUtil.getHitResultOnMoveVector}.
 * Reason: Sable's clip skip makes world-fired KI fly through Aeronautics/Sable hulls.
 * Version: NeoForge 1.21.1 / DMZ 2.1.x / Sable 2.0.3. Gated by sable.
 * Side: common. The call is already server-only.
 */
@Mixin(value = AbstractKiProjectile.class, remap = false)
public abstract class KiProjectileSableClipMixin {

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getHitResultOnMoveVector(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;",
                    remap = true),
            require = 0)
    private HitResult xenopixels$clipShipHull(Entity entity, Predicate<Entity> filter,
                                              Operation<HitResult> original) {
        return SableKiClip.preferCloser(entity, original.call(entity, filter));
    }
}
