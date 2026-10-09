package net.bullettrain.xenopixelsmod.mixin.compat.linearreader;

import net.bullettrain.xenopixelsmod.compat.linearreader.LinearConversionPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;

/** Verified 1.3.0/1.3.1 candidate predicate; excludes disabled files from bulk progress totals as well. */
@Pseudo
@Mixin(targets = "com.bugfunbug.linearreader.linear.BulkMcaConverter", remap = false)
public abstract class BulkConversionPolicyMixin {
    @Inject(method = "lambda$doConvert$2(Ljava/nio/file/Path;)Z", at = @At("RETURN"), cancellable = true, require = 1)
    private static void xeno$candidate(Path path, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !LinearConversionPolicy.allowsConversion(path)) cir.setReturnValue(false);
    }
}
