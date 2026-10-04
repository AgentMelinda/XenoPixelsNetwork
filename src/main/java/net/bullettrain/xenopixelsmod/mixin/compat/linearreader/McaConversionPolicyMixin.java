package net.bullettrain.xenopixelsmod.mixin.compat.linearreader;

import net.bullettrain.xenopixelsmod.compat.linearreader.LinearConversionPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import java.nio.file.Path;

/** Exact LinearReader 1.3.0 converter: covers lazy conversion and bulk/manual conversion. */
@Pseudo
@Mixin(targets = "com.bugfunbug.linearreader.linear.MCAConverter", remap = false)
public abstract class McaConversionPolicyMixin {
    @WrapMethod(method = "convertRegionIfNeeded(Ljava/nio/file/Path;II)V", require = 1)
    private static void xeno$conversion(Path folder, int x, int z, Operation<Void> original) {
        Path region = LinearConversionPolicy.regionPath(folder, x, z);
        var lock = LinearConversionPolicy.regionLock(region);
        lock.lock();
        try { if (LinearConversionPolicy.allowsConversion(region)) original.call(folder, x, z); }
        finally { lock.unlock(); }
    }

    @WrapMethod(method = "convertOne(Ljava/nio/file/Path;)V", require = 1)
    private static void xeno$singleConversion(Path region, Operation<Void> original) {
        var lock = LinearConversionPolicy.regionLock(region);
        lock.lock();
        try { if (LinearConversionPolicy.allowsConversion(region)) original.call(region); }
        finally { lock.unlock(); }
    }
}
