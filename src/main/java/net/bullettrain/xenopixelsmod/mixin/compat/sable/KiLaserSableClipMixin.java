package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bullettrain.xenopixelsmod.compat.sable.SableKiClip;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Target: {@code KiLaserEntity#tick} {@code Level.clip}.
 * Reason: long beam clips start in the world, so Sable skips the ship plot.
 * Version: NeoForge 1.21.1 / DMZ 2.1.x / Sable 2.0.3. Gated by sable.
 * Side: common. Server-only clip.
 */
@Mixin(value = KiLaserEntity.class, remap = false)
public abstract class KiLaserSableClipMixin {

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;",
                    remap = true),
            require = 0)
    private BlockHitResult xenopixels$clipShipHull(Level level, ClipContext context,
                                                   Operation<BlockHitResult> original) {
        return SableKiClip.clip(level, context);
    }
}
