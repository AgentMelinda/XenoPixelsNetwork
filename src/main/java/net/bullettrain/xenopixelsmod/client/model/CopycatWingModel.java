package net.bullettrain.xenopixelsmod.client.model;

import net.bullettrain.xenopixelsmod.block.custom.WingPanelBlock;
import net.bullettrain.xenopixelsmod.block.entity.WingPanelBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wraps the standalone {@code block/wing_panel_base} / {@code block/wing_panel_flap} models the
 * wing renderer tesselates. When the {@link WingPanelBlockEntity#MATERIAL_MODEL_PROPERTY} model
 * data carries a copied material (a copycat wing), the wing's own quads are re-textured onto that
 * material's particle sprite — keeping the thin wing silhouette but showing the copied block's
 * skin. Absent that data (a plain wing), it is a pure pass-through.
 *
 * <p>Unlike {@code CopycatGlowstoneModel}, which swaps the whole model, this keeps the geometry
 * and only rewrites each quad's UVs — a full-cube material model would otherwise obliterate the
 * wing shape.
 */
public final class CopycatWingModel extends BakedModelWrapper<BakedModel> {

    /** material state -> (side -> retextured quads); the {@code null} side lives under DIR_COUNT. */
    private final Map<BlockState, List<BakedQuad>[]> cache = new ConcurrentHashMap<>();
    private static final int NULL_SIDE = Direction.values().length;

    public CopycatWingModel(BakedModel fallback) {
        super(fallback);
    }

    @Nullable
    private static BlockState material(ModelData data) {
        BlockState m = data.get(WingPanelBlockEntity.MATERIAL_MODEL_PROPERTY);
        return m == null || m.getBlock() instanceof WingPanelBlock ? null : m;
    }

    private static BakedModel materialModel(BlockState material) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(material);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                    RandomSource random, ModelData data, @Nullable RenderType renderType) {
        BlockState material = material(data);
        if (material == null) {
            return originalModel.getQuads(state, side, random, data, renderType);
        }
        List<BakedQuad>[] bySide = cache.computeIfAbsent(material, m -> new List[NULL_SIDE + 1]);
        int idx = side == null ? NULL_SIDE : side.ordinal();
        List<BakedQuad> cached = bySide[idx];
        if (cached == null) {
            TextureAtlasSprite target = materialModel(material).getParticleIcon(ModelData.EMPTY);
            List<BakedQuad> src = originalModel.getQuads(state, side, random, ModelData.EMPTY, null);
            List<BakedQuad> out = new ArrayList<>(src.size());
            for (BakedQuad q : src) out.add(retexture(q, target));
            cached = out;
            bySide[idx] = cached;
        }
        return cached;
    }

    /** Rewrite a quad's per-vertex UVs from its own sprite's atlas span onto {@code target}'s. */
    private static BakedQuad retexture(BakedQuad q, TextureAtlasSprite target) {
        TextureAtlasSprite from = q.getSprite();
        if (from == target) return q;
        int[] verts = q.getVertices().clone();
        float uSpan = from.getU1() - from.getU0();
        float vSpan = from.getV1() - from.getV0();
        for (int v = 0; v < 4; v++) {
            int b = v * 8; // NeoForge DefaultVertexFormat.BLOCK stride = 8 ints, UV at 4/5
            float u = Float.intBitsToFloat(verts[b + 4]);
            float w = Float.intBitsToFloat(verts[b + 5]);
            float fu = uSpan == 0f ? 0f : (u - from.getU0()) / uSpan;
            float fw = vSpan == 0f ? 0f : (w - from.getV0()) / vSpan;
            verts[b + 4] = Float.floatToRawIntBits(target.getU0() + fu * (target.getU1() - target.getU0()));
            verts[b + 5] = Float.floatToRawIntBits(target.getV0() + fw * (target.getV1() - target.getV0()));
        }
        return new BakedQuad(verts, q.getTintIndex(), q.getDirection(), target, q.isShade());
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        BlockState material = material(data);
        if (material == null) {
            return originalModel.getRenderTypes(state, random, data);
        }
        return materialModel(material).getRenderTypes(material, random, ModelData.EMPTY);
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        BlockState material = material(data);
        return material == null ? originalModel.getParticleIcon(data)
                : materialModel(material).getParticleIcon(ModelData.EMPTY);
    }
}
