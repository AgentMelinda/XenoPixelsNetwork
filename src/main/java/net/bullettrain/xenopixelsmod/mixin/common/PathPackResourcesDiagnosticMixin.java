package net.bullettrain.xenopixelsmod.mixin.common;

import net.bullettrain.xenopixelsmod.util.XenoIdentifierDiagnostics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * My NPCs' {@code Model2DRenderer} looks up {@code minecraft:} (empty path) while building
 * headwear layers. Vanilla then logs {@code Invalid path minecraft:: Invalid path ''} for every
 * pack. Treat a blank path as missing instead of letting that walk continue.
 */
@Mixin(PathPackResources.class)
public abstract class PathPackResourcesDiagnosticMixin {
    @Inject(method = "getResource(Lnet/minecraft/resources/ResourceLocation;Ljava/nio/file/Path;)Lnet/minecraft/server/packs/resources/IoSupplier;",
            at = @At("HEAD"), cancellable = true)
    private static void xenopixels$diagnoseEmptyPath(ResourceLocation location, Path root,
                                                      CallbackInfoReturnable<IoSupplier<InputStream>> cir) {
        if (XenoIdentifierDiagnostics.isBlankPath(location)) {
            XenoIdentifierDiagnostics.reportEmpty(location, "PathPackResources");
            cir.setReturnValue(null);
        }
    }
}
