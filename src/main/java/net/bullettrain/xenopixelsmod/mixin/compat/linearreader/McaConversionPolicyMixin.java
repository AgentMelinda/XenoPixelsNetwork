package net.bullettrain.xenopixelsmod.mixin.compat.linearreader;

import net.bullettrain.xenopixelsmod.compat.linearreader.LinearConversionPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

/** Exact LinearReader 1.3.0 converter: covers lazy conversion and bulk/manual conversion. */
@Pseudo
@Mixin(targets = "com.bugfunbug.linearreader.linear.MCAConverter", remap = false)
public abstract class McaConversionPolicyMixin {
    @Inject(method = "convertRegionIfNeeded(Ljava/nio/file/Path;II)V", at = @At("HEAD"), cancellable = true, require = 1)
    private static void xeno$conversion(Path folder, int x, int z, CallbackInfo ci) {
        if (!LinearConversionPolicy.allowsConversion(LinearConversionPolicy.regionPath(folder, x, z))) ci.cancel();
    }

    @Inject(method = "convertOne(Ljava/nio/file/Path;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private static void xeno$singleConversion(Path region, CallbackInfo ci) {
        if (!LinearConversionPolicy.allowsConversion(region)) ci.cancel();
    }
}
