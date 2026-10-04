package net.bullettrain.xenopixelsmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.block.ModBlocks;
import net.bullettrain.xenopixelsmod.block.custom.MissileTubeBlock;
import net.bullettrain.xenopixelsmod.block.entity.MissileTubeBlockEntity;
import net.bullettrain.xenopixelsmod.item.custom.MissileItem;
import net.bullettrain.xenopixelsmod.missile.MissileSize;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class MissileTubeBlockEntityRenderer implements BlockEntityRenderer<MissileTubeBlockEntity> {
    private static final Vector3f NOSE = new Vector3f(0f, 1f, 0f);

    public MissileTubeBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MissileTubeBlockEntity tube, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        MissileSize size = MissileItem.sizeOf(tube.getMissile());
        if (size == null) return;
        Direction facing = tube.getBlockState().getValue(MissileTubeBlock.FACING);
        boolean fork = tube.getBlockState().is(ModBlocks.MISSILE_TUBE_FORK.get());

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(new Quaternionf().rotationTo(NOSE,
                new Vector3f(facing.getStepX(), facing.getStepY(), facing.getStepZ())));
        if (fork) {
            pose.translate(0.0, 0.5, 0.0);
        } else {
            float fit = 0.92f / size.visualLength();
            pose.translate(0.0, -0.5, 0.0);
            pose.scale(fit, fit, fit);
        }
        MissileModelDrawer.draw(pose, buffers, packedLight, size);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(MissileTubeBlockEntity tube) {
        AABB box = new AABB(tube.getBlockPos());
        MissileSize size = MissileItem.sizeOf(tube.getMissile());
        if (size == null) return box.inflate(1.0);
        Direction facing = tube.getBlockState().getValue(MissileTubeBlock.FACING);
        boolean fork = tube.getBlockState().is(ModBlocks.MISSILE_TUBE_FORK.get());
        double len = fork ? size.visualLength() + 0.6 : 1.5;
        double rad = fork ? Math.max(0.85, size.visualRadius() + 0.35) : 1.0;
        return box.inflate(rad).expandTowards(
                facing.getStepX() * len,
                facing.getStepY() * len,
                facing.getStepZ() * len);
    }

    @Override
    public boolean shouldRenderOffScreen(MissileTubeBlockEntity tube) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
