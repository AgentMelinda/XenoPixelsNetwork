package net.bullettrain.xenopixelsmod.mixin.client;

import net.bullettrain.xenopixelsmod.compat.sable.SableSectionRenderCull;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: vanilla {@code SectionRenderDispatcher.RenderSection#getCompiled}.
 * Reason: Sable's stock and sable-scale draw loops both skip empty compiled
 *         sections. Returning {@code EMPTY} drops off-camera ship sections
 *         without rewriting Sable's renderer.
 * Side: client. No-op unless {@link SableSectionRenderCull} is mid-draw.
 */
@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class SableRenderSectionCompiledMixin {

    @Inject(method = "getCompiled", at = @At("HEAD"), cancellable = true)
    private void xenopixels$cullSableSection(
            CallbackInfoReturnable<SectionRenderDispatcher.CompiledSection> cir) {
        if (SableSectionRenderCull.skip((SectionRenderDispatcher.RenderSection) (Object) this)) {
            cir.setReturnValue(SectionRenderDispatcher.CompiledSection.EMPTY);
        }
    }
}
