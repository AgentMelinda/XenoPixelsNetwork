package net.bullettrain.xenopixelsmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.block.custom.WingPanelBlock;
import net.bullettrain.xenopixelsmod.block.custom.WingPanelDebugRotation;
import net.bullettrain.xenopixelsmod.block.custom.WingPanelPose;
import net.bullettrain.xenopixelsmod.block.entity.WingPanelBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;

/**
 * Draws a wing panel as two pieces at its live deflection angle, every frame: a fixed base and a
 * smaller hinged flap, the same split Warium's own {@code controlsurface.geo.json} uses (a static
 * "Wing" bone plus a separately-pivoted "Fin" bone) — decompiled and read as the reference for this
 * class's whole design. A single rigid slab swinging as one piece was the mechanism this class used
 * before, but it is the wrong shape: a real flap is a small trailing portion of the surface, not the
 * entire chord.
 *
 * <p>This is what makes in-between angles possible at all: a blockstate can only ever select one of
 * a finite set of baked models, so a surface driven from a blockstate snaps between fixed positions
 * no matter how smoothly the value behind it moves. Reading a partial-tick-interpolated angle from
 * {@link WingPanelBlockEntity} and rotating a baked model about its hinge every frame is what makes
 * in-between frames possible.
 *
 * <p>The actual pose math lives in {@link WingPanelPose} so the panel configurator's swing readout
 * measures exactly what is drawn here. This class only picks the models, feeds the angle in, and
 * tesselates.
 *
 * <p><b>Every wing panel reaches here</b>, role or not ({@link WingPanelBlock#getRenderShape} is
 * always {@code ENTITYBLOCK_ANIMATED}). A role-less panel simply renders at zero deflection. There
 * is deliberately no chunk-mesh path any more: switching a panel between a baked mesh and this
 * renderer on a role change made it visibly jolt, because the two never quite lined up.
 *
 * <p>The models are tesselated directly rather than through
 * {@code BlockRenderDispatcher.renderSingleBlock}, which cannot be used here: that method switches on
 * the state's render shape, and for {@code ENTITYBLOCK_ANIMATED} it routes to the item renderer
 * instead of the block model. This mirrors its {@code MODEL} branch exactly.
 */
public class WingPanelBlockEntityRenderer implements BlockEntityRenderer<WingPanelBlockEntity> {

    /** Vanilla seeds model randomisation with 42 for single-block renders; matching it keeps
     * a panel's face selection identical to the baked version a role change swaps it back to. */
    private static final long MODEL_SEED = 42L;

    /**
     * The fixed-base and hinged-flap models — neither is a blockstate variant, so they are baked as
     * standalone models via {@code ClientModEvents.registerWingPanelModels} and looked back up here
     * the same way; {@code ModelResourceLocation.standalone} is the constructor built for exactly
     * this case.
     */
    private static final ModelResourceLocation BASE_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "block/wing_panel_base"));
    private static final ModelResourceLocation FLAP_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "block/wing_panel_flap"));

    private final RandomSource random = RandomSource.create();

    public WingPanelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WingPanelBlockEntity be, float partialTicks, PoseStack poseStack,
                        MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof WingPanelBlock)) return;
        // Every panel renders here, role or not — a role-less panel just sits at zero deflection.
        // One render path so a configurator click never switches the panel between two.

        double testDeg = WingPanelDebugRotation.testDeflectDeg;
        float deg = (float) (Double.isNaN(testDeg) ? be.getAnimatedDeflectDeg(partialTicks) : testDeg);

        var modelManager = Minecraft.getInstance().getModelManager();
        // Historical model-name accident: wing_panel_flap.json is the SMALL fixed stub,
        // wing_panel_base.json is the LARGE moving control surface.
        BakedModel stubModel = modelManager.getModel(FLAP_MODEL);
        BakedModel surfaceModel = modelManager.getModel(BASE_MODEL);

        // Copycat wings carry a copied material; plain wings return ModelData.EMPTY (no-op).
        ModelData modelData = be.getModelData();
        // Light-15 copycat wings render full-bright so the copied skin isn't shaded dark.
        int light = state.getLightEmission() >= 15 ? LightTexture.FULL_BRIGHT : packedLight;
        // Tint the retextured quads by the copied block's colour (grass, redstone, foliage…).
        int tint = 0xFFFFFF;
        BlockState material = be.getMaterial();
        if (be.hasCustomMaterial() && be.getLevel() != null) {
            tint = Minecraft.getInstance().getBlockColors().getColor(material, be.getLevel(), be.getBlockPos(), 0);
        }
        float r = (tint >> 16 & 0xFF) / 255.0f;
        float g = (tint >> 8 & 0xFF) / 255.0f;
        float b = (tint & 0xFF) / 255.0f;

        // Fixed stub at the hinge line: mount orientation only, never deflects.
        poseStack.pushPose();
        poseStack.mulPose(WingPanelPose.fixedMatrix(state));
        drawModel(poseStack, state, stubModel, modelData, bufferSource, light, packedOverlay, r, g, b);
        poseStack.popPose();

        // Moving control surface: same orientation, plus the live deflection, pivoting on the
        // stub/surface seam so the hinge line itself stays put and only the big piece swings.
        poseStack.pushPose();
        poseStack.mulPose(WingPanelPose.hingedMatrix(state, deg));
        drawModel(poseStack, state, surfaceModel, modelData, bufferSource, light, packedOverlay, r, g, b);
        poseStack.popPose();
    }

    private void drawModel(PoseStack poseStack, BlockState state, BakedModel model, ModelData modelData,
                            MultiBufferSource bufferSource, int packedLight, int packedOverlay,
                            float r, float g, float b) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        random.setSeed(MODEL_SEED);
        for (RenderType chunkType : model.getRenderTypes(state, random, modelData)) {
            dispatcher.getModelRenderer().renderModel(
                    poseStack.last(),
                    bufferSource.getBuffer(RenderTypeHelper.getEntityRenderType(chunkType, false)),
                    state, model, r, g, b, packedLight, packedOverlay,
                    modelData, chunkType);
        }
    }

    /** Inflated so a fully deflected surface is not culled by its own one-block footprint. */
    @Override
    public AABB getRenderBoundingBox(WingPanelBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(0.75);
    }
}
