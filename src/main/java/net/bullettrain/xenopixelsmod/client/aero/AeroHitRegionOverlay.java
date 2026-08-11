package net.bullettrain.xenopixelsmod.client.aero;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.aero.AeroHitRegions;
import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Developer overlay drawing the flight controller's click regions in world.
 *
 * <p>The design notes asked for this alongside geometry-derived hit-testing, and the two go
 * together: once regions are generated rather than hand-tuned, the way you verify them is to
 * look at them. Each box is drawn where {@link AeroHitRegions} believes it is, including the
 * {@code FACING} rotation, so a mis-rotated panel is immediately obvious.
 *
 * <p>Off by default; toggled with {@code /xenoaero debug}.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class AeroHitRegionOverlay {
    /** Only draw for controllers near the camera; this is a debug aid, not a feature. */
    private static final double RANGE = 12.0;

    private static boolean enabled;

    private AeroHitRegionOverlay() {
    }

    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static boolean enabled() {
        return enabled;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!enabled) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;

        Vec3 camera = event.getCamera().getPosition();
        BlockPos origin = minecraft.player.blockPosition();
        int r = (int) RANGE;

        PoseStack poses = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            if (!(minecraft.level.getBlockEntity(pos) instanceof ShipVlsGuidanceBlockEntity)) continue;
            BlockState state = minecraft.level.getBlockState(pos);
            Direction facing = state.hasProperty(
                    net.bullettrain.xenopixelsmod.block.custom.ShipVlsGuidanceBlock.FACING)
                    ? state.getValue(net.bullettrain.xenopixelsmod.block.custom.ShipVlsGuidanceBlock.FACING)
                    : Direction.NORTH;

            for (AeroHitRegions.Region region : AeroHitRegions.all()) {
                // Regions are stored in model space; walk the corners back out through the
                // same rotation the hit-test uses so what is drawn is what is tested.
                Vec3 min = AeroHitRegions.toModelSpace(
                        new Vec3(region.min()[0], region.min()[1], region.min()[2]), inverse(facing));
                Vec3 max = AeroHitRegions.toModelSpace(
                        new Vec3(region.max()[0], region.max()[1], region.max()[2]), inverse(facing));
                AABB box = new AABB(
                        pos.getX() + Math.min(min.x, max.x) - camera.x,
                        pos.getY() + Math.min(min.y, max.y) - camera.y,
                        pos.getZ() + Math.min(min.z, max.z) - camera.z,
                        pos.getX() + Math.max(min.x, max.x) - camera.x,
                        pos.getY() + Math.max(min.y, max.y) - camera.y,
                        pos.getZ() + Math.max(min.z, max.z) - camera.z);
                int colour = region.name().startsWith("lever") ? 0xFFFF7043 : 0xFF4DD0E1;
                drawBox(poses, lines, box, colour);
            }
        }
        buffers.endBatch(RenderType.lines());
    }

    /**
     * {@link AeroHitRegions#toModelSpace} rotates world → model; drawing needs the opposite,
     * and for the four horizontal cases the inverse is just the mirrored direction.
     */
    private static Direction inverse(Direction facing) {
        return switch (facing) {
            case WEST -> Direction.EAST;
            case EAST -> Direction.WEST;
            default -> facing;
        };
    }

    private static void drawBox(PoseStack poses, VertexConsumer lines, AABB box, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        net.minecraft.client.renderer.LevelRenderer.renderLineBox(
                poses, lines, box, r, g, b, a);
    }
}
