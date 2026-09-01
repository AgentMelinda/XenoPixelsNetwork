package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.bullettrain.xenopixelsmod.compat.sable.SableSectionRenderCull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: Sable {@code VanillaSubLevelRenderDispatcher.renderBlockEntities}.
 * Reason: that loop walks every section's block entities with no cull.
 *         Capture the current ship from the existing {@code renderPose()} call
 *         so {@code getCompiled} EMPTY-skip applies to BE draw too.
 * Version: Sable 2.0.3. {@code renderPose()} is invoked once per ship.
 * Side: client. Sable gated.
 */
@Mixin(
        targets = "dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher",
        remap = false
)
public abstract class SableVanillaBeRenderCullMixin {

    @Redirect(
            method = "renderBlockEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/ClientSubLevel;renderPose()Ldev/ryanhcode/sable/companion/math/Pose3dc;"
            ),
            require = 0
    )
    private Pose3dc xenopixels$beginBeCull(ClientSubLevel subLevel) {
        SableSectionRenderCull.begin(subLevel);
        return subLevel.renderPose();
    }

    @Inject(method = "renderBlockEntities", at = @At("RETURN"))
    private void xenopixels$endBeCull(CallbackInfo ci) {
        SableSectionRenderCull.end();
    }
}
