package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.bullettrain.xenopixelsmod.compat.sable.SableSectionRenderCull;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: Sable {@code VanillaChunkedSubLevelRenderData.renderChunkedSubLevel}
 *         (sable-fork vanilla renderer; Sodium uses the same class via
 *         {@code ReachAroundSubLevelRenderDispatcher}).
 * Reason: that method draws every compiled section with no frustum test.
 *         {@code VanillaSubLevelRenderDispatcher.updateCulling} is {@code // TODO}.
 * Version: Sable 2.0.3. Flag is set at HEAD so sable-scale's scale≠1 rewrite
 *          (which cancels the body) still sees it when it calls {@code getCompiled}.
 * Side: client. Sable gated.
 */
@Mixin(
        targets = "dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData",
        remap = false,
        priority = 500
)
public abstract class SableChunkedRenderCullMixin {

    @Shadow
    @Final
    private ClientSubLevel subLevel;

    @Inject(method = "renderChunkedSubLevel", at = @At("HEAD"))
    private void xenopixels$beginSectionCull(RenderType layer, ShaderInstance shader, Matrix4f modelView,
                                             double camX, double camY, double camZ, CallbackInfo ci) {
        SableSectionRenderCull.begin(this.subLevel);
    }

    @Inject(method = "renderChunkedSubLevel", at = @At("RETURN"))
    private void xenopixels$endSectionCull(RenderType layer, ShaderInstance shader, Matrix4f modelView,
                                           double camX, double camY, double camZ, CallbackInfo ci) {
        SableSectionRenderCull.end();
    }
}
