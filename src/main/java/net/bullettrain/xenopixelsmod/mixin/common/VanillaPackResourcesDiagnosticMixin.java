package net.bullettrain.xenopixelsmod.mixin.common;

import net.bullettrain.xenopixelsmod.util.XenoIdentifierDiagnostics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;

/** Same empty-path short-circuit as {@link PathPackResourcesDiagnosticMixin}. */
@Mixin(VanillaPackResources.class)
public abstract class VanillaPackResourcesDiagnosticMixin {
    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
    private void xenopixels$diagnoseEmptyPath(PackType type, ResourceLocation location,
                                              CallbackInfoReturnable<IoSupplier<InputStream>> cir) {
        if (XenoIdentifierDiagnostics.isBlankPath(location)) {
            XenoIdentifierDiagnostics.reportEmpty(location, "VanillaPackResources");
            cir.setReturnValue(null);
        }
    }
}
