package net.bullettrain.xenopixelsmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws nothing. The seat block carries the visuals; the entity exists only so a player can ride
 * it. A renderer is still required because the client resolves one for every registered entity
 * type the moment such an entity is spawned.
 */
public class PilotSeatRenderer extends EntityRenderer<XenoPilotSeatEntity> {

    public PilotSeatRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(XenoPilotSeatEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return false;
    }

    @Override
    public void render(XenoPilotSeatEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        // Intentionally empty.
    }

    @Override
    public ResourceLocation getTextureLocation(XenoPilotSeatEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}
