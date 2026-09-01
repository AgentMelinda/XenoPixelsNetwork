package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bullettrain.xenopixelsmod.compat.sable.SableKiClip;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Target: {@code KiBlastEntity#fireHability} launch clip.
 * Reason: aim-at-block uses {@code Level.clip}; Sable skips ships unless the eye
 *         is already in the plot, so blasts launch through the hull.
 * Version: NeoForge 1.21.1 / DMZ 2.1.x / Sable 2.0.3. Gated by sable.
 * Side: common.
 */
@Mixin(value = KiBlastEntity.class, remap = false)
public abstract class KiBlastSableAimClipMixin {

    @WrapOperation(
            method = "fireHability",
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
