package com.dragonminez.mixin.client;

import com.dragonminez.client.clash.BeamClashCinematicCamera;
import com.dragonminez.client.flight.FlightRollHandler;
import com.dragonminez.client.flight.RollCamera;
import com.dragonminez.client.render.camera.OverShoulderCamera;
import com.dragonminez.client.render.firstperson.dto.DMZCameraBuffer;
import com.dragonminez.client.render.firstperson.dto.FirstPersonManager;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Camera.class})
public abstract class CameraMixin implements RollCamera {
   @Unique
   private float dragonminez$roll = 0.0F;
   @Unique
   private float dragonminez$lastRoll = 0.0F;
   @Unique
   private float dragonminez$tickDelta = 0.0F;

   @Shadow
   protected abstract void setPosition(Vec3 var1);

   @Shadow
   public abstract Vec3 getPosition();

   @Shadow
   protected abstract void setRotation(float var1, float var2);

   @Shadow
   protected abstract void move(float var1, float var2, float var3);

   @Inject(
      method = {"setup"},
      at = {@At("HEAD")}
   )
   private void dragonminez$captureTickDelta(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
      this.dragonminez$tickDelta = partialTick;
      this.dragonminez$lastRoll = this.dragonminez$roll;
   }

   @Inject(
      method = {"setup"},
      at = {@At("TAIL")}
   )
   private void dragonminez$modifyCamera(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
      if (FlightRollHandler.hasActiveRoll()) {
         float newRoll = FlightRollHandler.getRoll(partialTick);
         float delta = Mth.wrapDegrees(newRoll - this.dragonminez$lastRoll);
         this.dragonminez$roll = this.dragonminez$lastRoll + delta;
         this.dragonminez$rebaseRoll();
      } else {
         this.dragonminez$roll = Mth.lerp(0.1F, this.dragonminez$roll, 0.0F);
      }

      if (entity instanceof LocalPlayer clashPlayer && BeamClashCinematicCamera.isActive()) {
         BeamClashCinematicCamera.Shot shot = BeamClashCinematicCamera.computeShot(level, clashPlayer, partialTick);
         if (shot != null) {
            this.setPosition(shot.pos());
            this.setRotation(shot.yaw(), shot.pitch());
            return;
         }
      }

      if (!detached && entity instanceof LocalPlayer player && FirstPersonManager.shouldRenderFirstPerson(player)) {
         Vec3 baseEyePos = this.getPosition();
         Vector3f targetOffset = FirstPersonManager.offsetFirstPersonView(player);
         Vector3f smoothedOffset = DMZCameraBuffer.getSmoothedOffset(targetOffset, 0.6F);
         Vec3 forward = Vec3.directionFromRotation(0.0F, player.getViewYRot(partialTick));
         Vec3 left = Vec3.directionFromRotation(0.0F, player.getViewYRot(partialTick) - 90.0F);
         Vec3 movement = forward.scale((double)smoothedOffset.z()).add(left.scale((double)smoothedOffset.x()));
         double movementLen = movement.length();
         Vec3 appliedShift = Vec3.ZERO;
         if (movementLen > 0.001) {
            Vec3 direction = movement.normalize();
            double safeMargin = 0.35;
            Vec3 rayEnd = baseEyePos.add(direction.scale(movementLen + safeMargin));
            ClipContext context = new ClipContext(baseEyePos, rayEnd, Block.COLLIDER, Fluid.NONE, player);
            BlockHitResult hit = level.clip(context);
            if (hit.getType() != Type.MISS) {
               double hitDistance = hit.getLocation().distanceTo(baseEyePos);
               double allowedDistance = Math.max(0.0, hitDistance - safeMargin);
               if (allowedDistance < movementLen) {
                  appliedShift = direction.scale(allowedDistance);
               } else {
                  appliedShift = movement;
               }
            } else {
               appliedShift = movement;
            }
         }

         DMZCameraBuffer.setFirstPersonShift(appliedShift);
      }
   }

   @Redirect(
      method = {"setup"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/Camera;move(FFF)V",
         ordinal = 0
      )
   )
   private void dragonminez$shoulderSurf(
      Camera camera, float x, float y, float z, BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick
   ) {
      Vec3 move = OverShoulderCamera.computeMove(camera, level, entity, thirdPersonReverse, (double)x, (double)y, (double)z, partialTick);
      this.move((float)move.x, (float)move.y, (float)move.z);
   }

   @Override
   public float dragonminez$getRoll() {
      return Mth.lerp(this.dragonminez$tickDelta, this.dragonminez$lastRoll, this.dragonminez$roll);
   }

   @Unique
   private void dragonminez$rebaseRoll() {
      float wrapped = Mth.wrapDegrees(this.dragonminez$roll);
      float offset = this.dragonminez$roll - wrapped;
      if (offset != 0.0F) {
         this.dragonminez$roll = wrapped;
         this.dragonminez$lastRoll -= offset;
      }
   }
}
