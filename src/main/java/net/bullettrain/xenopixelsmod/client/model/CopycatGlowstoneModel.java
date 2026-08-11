package net.bullettrain.xenopixelsmod.client.model;

import net.bullettrain.xenopixelsmod.block.ModBlocks;
import net.bullettrain.xenopixelsmod.block.entity.CopycatGlowstoneBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Delegates the copycat's quads and render layers to its stored material model. */
public final class CopycatGlowstoneModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<ModelData> WRAPPED_DATA_PROPERTY = new ModelProperty<>();

    public CopycatGlowstoneModel(BakedModel fallback) {
        super(fallback);
    }

    @Nullable
    private static BlockState material(ModelData data) {
        BlockState state = data.get(CopycatGlowstoneBlockEntity.MATERIAL_MODEL_PROPERTY);
        return state == null || state.is(ModBlocks.COPYCAT_GLOWSTONE.get()) ? null : state;
    }

    private static BakedModel model(BlockState material) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(material);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        BlockState material = material(modelData);
        if (material == null) {
            return modelData;
        }
        // Create-style data gathering: the material model gets the real world once. Connected
        // texture mods see copied neighbours through CopycatGlowstoneBlock#getAppearance.
        ModelData sourceData = model(material).getModelData(level, pos, material, ModelData.EMPTY);
        return ModelData.builder()
                .with(CopycatGlowstoneBlockEntity.MATERIAL_MODEL_PROPERTY, material)
                .with(WRAPPED_DATA_PROPERTY, sourceData)
                .build();
    }

    private static ModelData wrapped(ModelData data) {
        ModelData wrapped = data.get(WRAPPED_DATA_PROPERTY);
        return wrapped == null ? ModelData.EMPTY : wrapped;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        BlockState material = material(data);
        if (material == null) {
            return originalModel.getQuads(state, side, random, data, renderType);
        }
        return model(material).getQuads(material, side, random, wrapped(data), renderType);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        BlockState material = material(data);
        if (material == null) {
            return originalModel.getRenderTypes(state, random, data);
        }
        return model(material).getRenderTypes(material, random, wrapped(data));
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        BlockState material = material(data);
        return material == null ? originalModel.getParticleIcon(data)
                : model(material).getParticleIcon(wrapped(data));
    }

    @Override
    public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        BlockState material = material(data);
        if (material == null) {
            return originalModel.useAmbientOcclusion(state, data, renderType);
        }
        return model(material).useAmbientOcclusion(material, wrapped(data), renderType);
    }
}
