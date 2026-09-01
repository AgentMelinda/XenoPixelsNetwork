package net.bullettrain.xenopixelsmod.mixin.compat.cosmonautics;

import net.bullettrain.xenopixelsmod.compat.cosmonautics.CosmoWarpSections;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Target: Cosmonautics {@code SubLevelTemplate.load}
 *         (javap of cosmonautics-26.08.307, source line 210 = {@code sections[index] =}).
 * Reason: crash {@code Index 126 out of bounds for length 24} when a space-dim
 *         section index is written into an overworld plot chunk.
 * Version: rocketnautics / Cosmonautics 26.08.307. Keys are source indexes;
 *          remap via {@code MoveInfo.oldDimension/newDimension} section Y.
 * Side: server. Gated on rocketnautics.
 */
@Mixin(targets = "dev.egg.SubLevelTemplate", remap = false)
public abstract class CosmoSubLevelTemplateLoadMixin {

    @Inject(method = "load", at = @At("HEAD"))
    private static void xenopixels$begin(Object plot, Object tag, Object moveInfo, CallbackInfo ci) {
        CosmoWarpSections.beginLoad(moveInfo);
    }

    @Inject(method = "load", at = @At("RETURN"))
    private static void xenopixels$end(CallbackInfo ci) {
        CosmoWarpSections.endLoad();
    }

    @Redirect(
            method = "load",
            at = @At(value = "INVOKE", target = "Ljava/lang/Integer;parseInt(Ljava/lang/String;)I"),
            require = 0
    )
    private static int xenopixels$remapSectionKey(String key) {
        return CosmoWarpSections.remapSectionIndex(key);
    }

    @Redirect(
            method = "load",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/LevelChunk;getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;",
                    remap = true
            ),
            require = 0
    )
    private static LevelChunkSection[] xenopixels$discardOob(LevelChunk chunk) {
        return CosmoWarpSections.sectionsOrDiscard(chunk);
    }
}
