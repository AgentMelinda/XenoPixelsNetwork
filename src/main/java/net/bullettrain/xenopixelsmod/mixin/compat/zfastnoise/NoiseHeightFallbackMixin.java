package net.bullettrain.xenopixelsmod.mixin.compat.zfastnoise;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.bullettrain.xenopixelsmod.compat.worldgen.FastNoiseHeightCompat;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Keeps zfastnoise's optimized fill when the noise range fits the world, correcting its section
 * array base for expanded-height dimensions. Uses Minecraft's normal fill if noise falls outside
 * the world's build range.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseHeightFallbackMixin {
    @Shadow
    private ChunkAccess doFill(Blender blender, StructureManager structureManager,
                               RandomState randomState, ChunkAccess chunk,
                               int minimumCellY, int cellCount) {
        throw new AssertionError("shadow method");
    }

    @WrapMethod(method = "lambda$fillFromNoise$11", require = 0)
    private ChunkAccess xenopixels$useVanillaFillForNonMatchingHeight(
            ChunkAccess chunk, int cellCount, NoiseSettings generationShapeConfig,
            int minimumY, Blender blender, StructureManager structureManager,
            RandomState randomState, int minimumCellY, Operation<ChunkAccess> original) {
        var worldHeight = chunk.getHeightAccessorForGeneration();
        int worldMinY = worldHeight.getMinBuildHeight();
        if (!FastNoiseHeightCompat.canUseFastNoise(
                worldMinY, worldHeight.getHeight(),
                generationShapeConfig.minY(), generationShapeConfig.height())) {
            return doFill(blender, structureManager, randomState, chunk, minimumCellY, cellCount);
        }
        int sectionBaseY = FastNoiseHeightCompat.fastNoiseSectionBaseY(worldMinY);
        return original.call(chunk, cellCount, generationShapeConfig, sectionBaseY,
                blender, structureManager, randomState, minimumCellY);
    }
}
