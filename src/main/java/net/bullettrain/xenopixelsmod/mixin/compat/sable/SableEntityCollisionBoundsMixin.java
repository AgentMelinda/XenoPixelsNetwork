package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import net.bullettrain.xenopixelsmod.compat.sable.SableContraptionCull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Target: Sable {@code SubLevelEntityCollision.collide}
 *         (sable-fork {@code if (localBounds.volume() > 500 * 500 * 500)}).
 * Reason: a bad pose inverse on a big/scaled hull walks that many blocks
 *         through SAT (8 local-player substeps × 4 iterations). Stock only
 *         aborts at 125 million cells.
 * Version: Sable 2.0.3. Replaces each 500 so the product is {@code cap³}.
 * Side: common. Sable gated.
 */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision", remap = false)
public abstract class SableEntityCollisionBoundsMixin {

    @ModifyConstant(method = "collide", constant = @Constant(doubleValue = 500.0), require = 0)
    private static double xenopixels$tighterLocalBounds(double original) {
        return SableContraptionCull.collisionBoundCap();
    }
}
