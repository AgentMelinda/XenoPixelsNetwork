package net.bullettrain.xenopixelsmod.mixin.common;

import net.bullettrain.xenopixelsmod.util.XenoIdentifierDiagnostics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PathPackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;

@Mixin(PathPackResources.class)
public abstract class PathPackResourcesDiagnosticMixin {
    @Inject(method = "getResource(Lnet/minecraft/resources/ResourceLocation;Ljava/nio/file/Path;)Lnet/minecraft/server/packs/resources/IoSupplier;",
            at = @At("HEAD"))
    private static void xenopixels$diagnoseEmptyPath(ResourceLocation location, Path root,
                                                      CallbackInfoReturnable<?> cir) {
        XenoIdentifierDiagnostics.reportEmpty(location, "PathPackResources root=" + root);
    }
}
