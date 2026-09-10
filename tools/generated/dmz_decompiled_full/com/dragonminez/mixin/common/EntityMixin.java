package com.dragonminez.mixin.common;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntity.class})
public abstract class EntityMixin {
   @Inject(
      method = {"wouldNotSuffocateAtTargetPose"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onCanEnterPose(Pose pose, CallbackInfoReturnable<Boolean> cir) {
      LivingEntity self = (LivingEntity)this;
      if (self instanceof Player player) {
         StatsProvider.get(StatsCapability.INSTANCE, player)
            .ifPresent(
               data -> {
                  Float[] scaling = data.getCharacter().getResolvedModelScaling();
                  float currentScaleY = scaling[1];
                  float BASE_SCALE = 0.9375F;
                  float ratioY = currentScaleY / 0.9375F;
                  if (Math.abs(ratioY - 1.0F) > 0.001F) {
                     float baseHeight = 1.8F;
                     float poseMultiplier = 1.0F;
                     if (pose == Pose.CROUCHING) {
                        poseMultiplier = 0.8333334F;
                     } else if (pose == Pose.SWIMMING || pose == Pose.FALL_FLYING || pose == Pose.SPIN_ATTACK) {
                        poseMultiplier = 0.33333334F;
                     }

                     float actualHeight = baseHeight * ratioY * poseMultiplier;
                     AABB currentBox = self.getBoundingBox();
                     AABB testBox = new AABB(
                        currentBox.minX, currentBox.minY, currentBox.minZ, currentBox.maxX, currentBox.minY + (double)actualHeight, currentBox.maxZ
                     );
                     boolean canFit = player.level().noCollision(player, testBox);
                     cir.setReturnValue(canFit);
                  }
               }
            );
      }
   }
}
