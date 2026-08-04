package net.bullettrain.xenopixelsmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.bullettrain.xenopixelsmod.missile.BallisticMissileEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BallisticMissileRenderer extends EntityRenderer<BallisticMissileEntity> {
    private static final ItemStack VISUAL = new ItemStack(Items.FIREWORK_ROCKET);
    private final ItemRenderer itemRenderer;

    public BallisticMissileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
        this.shadowRadius = 0.3f;
    }

    @Override
    public void render(BallisticMissileEntity entity, float entityYaw, float partialTicks,
                       PoseStack pose, MultiBufferSource buffer, int packedLight) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(entity.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(entity.getXRot() - 90f));
        pose.scale(1.4f, 1.4f, 1.4f);
        itemRenderer.renderStatic(VISUAL, ItemDisplayContext.GROUND,
                packedLight, OverlayTexture.NO_OVERLAY, pose, buffer, entity.level(), entity.getId());
        pose.popPose();
        super.render(entity, entityYaw, partialTicks, pose, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(BallisticMissileEntity entity) {
        return new ResourceLocation("minecraft", "textures/item/firework_rocket.png");
    }
}
