package net.bullettrain.xenopixelsmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.client.render.MissileModelDrawer;
import net.bullettrain.xenopixelsmod.missile.BallisticMissileEntity;
import net.bullettrain.xenopixelsmod.missile.MissileSize;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class BallisticMissileRenderer extends EntityRenderer<BallisticMissileEntity> {
    private static final Vector3f NOSE = new Vector3f(0f, 1f, 0f);

    public BallisticMissileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.6f;
    }

    @Override
    public void render(BallisticMissileEntity entity, float entityYaw, float partialTicks,
                       PoseStack pose, MultiBufferSource buffer, int packedLight) {
        MissileSize size = entity.getMissileSize();
        Vector3f dir = direction(entity, partialTicks);
        pose.pushPose();
        pose.mulPose(new Quaternionf().rotationTo(NOSE, dir));
        MissileModelDrawer.draw(pose, buffer, packedLight, size);
        pose.popPose();
        super.render(entity, entityYaw, partialTicks, pose, buffer, packedLight);
    }

    private static Vector3f direction(BallisticMissileEntity entity, float partialTicks) {
        // The entity's pitch uses Minecraft's inverted camera convention. Render from
        // the authoritative velocity instead so the model nose cannot face backward.
        Vec3 vel = entity.getDeltaMovement();
        if (vel.lengthSqr() > 1.0e-8) {
            return new Vector3f((float) vel.x, (float) vel.y, (float) vel.z).normalize();
        }
        Vec3 look = entity.getViewVector(partialTicks);
        if (look.lengthSqr() > 1.0e-8) {
            return new Vector3f((float) look.x, (float) look.y, (float) look.z).normalize();
        }
        float yaw = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        Vec3 fallback = Vec3.directionFromRotation(pitch, yaw);
        if (fallback.lengthSqr() > 1.0e-8) {
            return new Vector3f((float) fallback.x, (float) fallback.y, (float) fallback.z).normalize();
        }
        return new Vector3f(0f, 1f, 0f);
    }

    @Override
    public ResourceLocation getTextureLocation(BallisticMissileEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}
