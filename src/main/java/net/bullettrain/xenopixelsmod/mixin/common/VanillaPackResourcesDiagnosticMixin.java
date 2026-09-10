package net.bullettrain.xenopixelsmod.mixin.common;

import net.bullettrain.xenopixelsmod.util.XenoIdentifierDiagnostics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VanillaPackResources.class)
public abstract class VanillaPackResourcesDiagnosticMixin {
    @Inject(method = "getResource", at = @At("HEAD"))
    private void xenopixels$diagnoseEmptyPath(PackType type, ResourceLocation location,
                                              CallbackInfoReturnable<?> cir) {
        XenoIdentifierDiagnostics.reportEmpty(location, "VanillaPackResources type=" + type);
    }
}
