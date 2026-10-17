package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.init.entities.renderer.ki.KiWaveRenderer;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.bullettrain.xenopixelsmod.combat.v2.UltimateFinisherBeamAim;
import net.bullettrain.xenopixelsmod.combat.v2.UltimateFinisherKamehameha;
import net.bullettrain.xenopixelsmod.combat.v2.UltimateFinisherWaveTarget;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DMZ uses interpolated position but current yaw/pitch: use one render-time ray for owned waves. */
@Mixin(value = KiWaveRenderer.class, remap = false)
public abstract class FinisherWaveRenderAimMixin {
    @Inject(method = "render(Lcom/dragonminez/common/init/entities/ki/KiWaveEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void xenopixels$centerFinisher(KiWaveEntity wave, float yaw, float partialTick,
                                         PoseStack stack, MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (!wave.isFiring() || !UltimateFinisherKamehameha.owns(wave)
                || !(wave instanceof UltimateFinisherWaveTarget marked)
                || !(wave.getOwner() instanceof LivingEntity owner)
                || !(wave.level().getEntity(marked.xenopixels$finisherTarget()) instanceof LivingEntity target)) return;
        Vec3 center = target.getBoundingBox().getCenter().subtract(target.position()).add(target.getPosition(partialTick));
        var aim = UltimateFinisherBeamAim.toward(owner.getEyePosition(partialTick), center, owner.getLookAngle());
        Vec3 correction = aim.origin().subtract(wave.getPosition(partialTick));
        stack.translate(correction.x, correction.y, correction.z);
        Quaternionf desired = Axis.YP.rotationDegrees(-aim.yaw()).mul(Axis.XP.rotationDegrees(aim.pitch()));
        Quaternionf nativeRotation = Axis.YP.rotationDegrees(-wave.getYRot()).mul(Axis.XP.rotationDegrees(wave.getXRot()));
        stack.mulPose(desired.mul(nativeRotation.conjugate()));
    }
}
