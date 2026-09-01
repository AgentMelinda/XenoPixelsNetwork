package net.bullettrain.xenopixelsmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.bullettrain.xenopixelsmod.block.custom.PanelRole;
import net.bullettrain.xenopixelsmod.block.custom.WingFlapVerticalBlock;
import net.bullettrain.xenopixelsmod.block.custom.WingPanelBlock;
import net.bullettrain.xenopixelsmod.block.entity.WingPanelBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
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
 * "Wing" bone plus a separately-pivoted "Fin" bone) — decompiled and read this session as the
 * reference for this class's whole design. A single rigid slab swinging as one piece was the
 * mechanism this class used before, but it is the wrong shape: a real flap is a small trailing
 * portion of the surface, not the entire chord.
 *
 * <p>This is what makes in-between angles possible at all: a blockstate can only ever select one
 * of a finite set of baked models, so a surface driven from a blockstate snaps between fixed
 * positions no matter how smoothly the value behind it moves. Reading a partial-tick-interpolated
 * angle from {@link WingPanelBlockEntity} and rotating a baked model about its hinge every frame
 * is what makes in-between frames possible at all.
 *
 * <p><b>Only role-assigned panels reach here.</b> {@link WingPanelBlock#getRenderShape} keeps
 * {@link PanelRole#NONE} panels as {@code MODEL} so the structural skin of a hull stays baked
 * into the chunk mesh and costs nothing per frame; the handful of blocks a builder actually
 * designated as control surfaces become {@code ENTITYBLOCK_ANIMATED} and are drawn here. The
 * guard below is belt and braces for that split — if a panel's role is cleared while it is
 * loaded, the mesh takes it back and this must not draw a second copy in the meantime.
 *
 * <p>The base and flap models are tesselated directly rather than through
 * {@code BlockRenderDispatcher.renderSingleBlock}, which cannot be used here: that method
 * switches on the state's render shape, and for {@code ENTITYBLOCK_ANIMATED} it routes to the
 * item renderer instead of the block model. This mirrors its {@code MODEL} branch exactly.
 */
public class WingPanelBlockEntityRenderer implements BlockEntityRenderer<WingPanelBlockEntity> {

    /** Vanilla seeds model randomisation with 42 for single-block renders; matching it keeps
     * a panel's face selection identical to the baked version a role change swaps it back to. */
    private static final long MODEL_SEED = 42L;

    /**
     * The fixed-base and hinged-flap models — neither is a blockstate variant, so they are baked
     * as standalone models via {@code ClientModEvents.registerWingPanelModels} and looked back up
     * here the same way; {@code ModelResourceLocation.standalone} is the constructor built for
     * exactly this case (confirmed against the real compiled class, not guessed).
     */
    private static final ModelResourceLocation BASE_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "block/wing_panel_base"));
    private static final ModelResourceLocation FLAP_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "block/wing_panel_flap"));

    /** Where the flap starts, in raw [0,1] block space along the deflection hinge's Z axis —
     * matches {@code wing_panel_flap.json}'s {@code from} of 12/16. The base spans [0, this). */
    private static final double FLAP_HINGE = 0.75;

    private final RandomSource random = RandomSource.create();

    public WingPanelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WingPanelBlockEntity be, float partialTicks, PoseStack poseStack,
                        MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof WingPanelBlock)) return;
        PanelRole role = state.getValue(WingPanelBlock.ROLE);
        if (role == PanelRole.NONE) return;

        Direction.Axis axis = state.getValue(WingPanelBlock.AXIS);
        float deg = (float) be.getAnimatedDeflectDeg(partialTicks);

        var modelManager = Minecraft.getInstance().getModelManager();
        BakedModel baseModel = modelManager.getModel(BASE_MODEL);
        BakedModel flapModel = modelManager.getModel(FLAP_MODEL);

        // Base: axis-aligned, never deflects.
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        applyStaticTwist(poseStack);
        alignToAxis(poseStack, axis, state);
        poseStack.translate(-0.5, -0.5, -0.5);
        drawModel(poseStack, state, baseModel, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();

        // Flap: same axis alignment, plus the live deflection, hinged at the base/flap boundary
        // rather than the panel's outer edge.
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        applyStaticTwist(poseStack);
        alignToAxis(poseStack, axis, state);

        // PoseStack composes by POST-multiplying each call onto the current matrix (mulPose calls
        // Matrix4f.rotate(Quaternionfc), same for translate) — meaning a vertex is transformed by
        // the *last*-called line first and the *first*-called line last. Reading the block below
        // top-to-bottom therefore describes the pose in the OPPOSITE order it is actually applied
        // to a raw model vertex; every comment here describes vertex-application order, not
        // source order, to avoid re-making that mistake again.
        //
        // Vertex-application order (top = happens first):
        //   1. shift Z so FLAP_HINGE lands at Z=0, X/Y centred                (source line 3)
        //   2. rotate by deg about X — pivots exactly at that hinge, so the   (source line 2)
        //      flap swings from where it meets the fixed base, not from its
        //      own free (outer) edge — the same edge-hinge fix the whole
        //      panel used before this split, just moved to the new boundary.
        //   3. shift back so the hinge is at its usual centred-block Z        (source line 1)
        //      position, handing off to the axis alignment already applied
        //      above exactly as it always was.
        poseStack.translate(0.0, 0.0, FLAP_HINGE - 0.5);
        float liveDeg = WingPanelDebugRotation.deflectNegated ? -deg : deg;
        poseStack.mulPose(switch (WingPanelDebugRotation.deflectAxis) {
            case X -> Axis.XP.rotationDegrees(liveDeg);
            case Y -> Axis.YP.rotationDegrees(liveDeg);
            case Z -> Axis.ZP.rotationDegrees(liveDeg);
        });
        poseStack.translate(-0.5, -0.5, -FLAP_HINGE);

        drawModel(poseStack, state, flapModel, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    /**
     * wing_panel_base.json/wing_panel_flap.json both deliberately leave every role!=NONE
     * variant's model unrotated — this is the only place axis rotation happens for a drawn-here
     * panel. A role variant that also baked the axis rotation (like the role=none variant still
     * does, for the plain chunk-mesh path) would double it, since the baked model's quads would
     * already carry whatever rotation its blockstate variant specified.
     *
     * <p>Angles here are the <b>negative</b> of the matching blockstate "x"/"y" values — decompiled
     * {@code BlockModelRotation} this session (the vanilla class that actually applies those
     * fields) and confirmed it negates both before building its rotation quaternion. Every
     * previous version of this method used the JSON's own positive numbers directly, which was a
     * real, silent mirroring bug the whole session: a role-assigned panel's static orientation was
     * never actually matching what the same panel snapped back to at {@code role=NONE}.
     *
     * <p>{@link WingFlapVerticalBlock} keeps its own, separate rotation (matching its own
     * blockstate) rather than sharing WingPanelBlock/WingFlapHorizontalBlock's formula — the
     * latter two carry an extra 180° twist about X neither of them had originally that the
     * vertical block does not.
     */
    private static void alignToAxis(PoseStack poseStack, Direction.Axis axis, BlockState state) {
        // Order matters and is reversed from how it reads: PoseStack applies the *last*-called
        // rotation to the vertex first. Each branch is kept in lockstep with its block's own
        // baked (role=none) blockstate rotation, applied the normal top-to-bottom way blockstate
        // rotations read (X to the vertex before Y) — so a role-assigned panel's static
        // orientation always matches what it snaps back to when its role is cleared. To get that
        // same X-then-Y vertex order out of mulPose's reversed composition, whichever call needs
        // to land second in blockstate terms has to be *first* in source order here.
        if (state.getBlock() instanceof WingFlapVerticalBlock) {
            switch (axis) {
                case Y -> {
                }
                case Z -> poseStack.mulPose(Axis.XP.rotationDegrees(-90));
                case X -> {
                    poseStack.mulPose(Axis.YP.rotationDegrees(-90));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-90));
                }
            }
            return;
        }
        switch (axis) {
            case Y -> poseStack.mulPose(Axis.XP.rotationDegrees(-180));
            case Z -> poseStack.mulPose(Axis.XP.rotationDegrees(-270));
            case X -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(-90));
                poseStack.mulPose(Axis.XP.rotationDegrees(-270));
            }
        }
    }

    /**
     * Live-tunable extra rotation on top of the panel's normal mounting, called before
     * {@link #alignToAxis} so it lands outermost — applied to the already-aligned shape, not the
     * raw model — see {@link WingPanelDebugRotation#staticTwistXDegrees}/
     * {@code staticTwistYDegrees} and {@code /xenowing statictwist}.
     */
    private static void applyStaticTwist(PoseStack poseStack) {
        int x = WingPanelDebugRotation.staticTwistXDegrees;
        int y = WingPanelDebugRotation.staticTwistYDegrees;
        if (y != 0) poseStack.mulPose(Axis.YP.rotationDegrees(y));
        if (x != 0) poseStack.mulPose(Axis.XP.rotationDegrees(x));
    }

    private void drawModel(PoseStack poseStack, BlockState state, BakedModel model,
                            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        random.setSeed(MODEL_SEED);
        for (RenderType chunkType : model.getRenderTypes(state, random, ModelData.EMPTY)) {
            dispatcher.getModelRenderer().renderModel(
                    poseStack.last(),
                    bufferSource.getBuffer(RenderTypeHelper.getEntityRenderType(chunkType, false)),
                    state, model, 1.0f, 1.0f, 1.0f, packedLight, packedOverlay,
                    ModelData.EMPTY, chunkType);
        }
    }

    /** Inflated so a fully deflected surface is not culled by its own one-block footprint. */
    @Override
    public AABB getRenderBoundingBox(WingPanelBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(0.75);
    }
}
