package com.dragonminez.client.events;

import com.dragonminez.client.systems.kisense.KiSenseScan;
import com.dragonminez.client.systems.taiyoken.TaiyokenBlindState;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.util.TransformationsHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.LayeredDraw.Layer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderFrameEvent.Pre;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import org.joml.Vector4f;

@EventBusSubscriber(
   modid = "dragonminez",
   value = {Dist.CLIENT}
)
public class LockOnEvent {
   private static final ResourceLocation LOCK_ICON = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/lock_on.png");
   private static LivingEntity lockedTarget = null;
   private static int scanTickCounter = 0;
   private static boolean markerVisible;
   private static float markerX;
   private static float markerY;
   private static float markerHalfSize = 16.0F;
   public static final Layer HUD_LOCK_ON = (gui, deltaTracker) -> {
      if (markerVisible && lockedTarget != null && lockedTarget.isAlive()) {
         long time = System.currentTimeMillis();
         boolean lod = Minecraft.getInstance().player != null && (double)Minecraft.getInstance().player.distanceTo(lockedTarget) > 24.0;
         float angle = lod ? 0.0F : (float)(time % 3600L) / 10.0F;
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.setShaderColor(0.0F, 1.0F, 1.0F, 0.9F);
         gui.pose().pushPose();
         gui.pose().translate(markerX, markerY, 0.0F);
         gui.pose().mulPose(Axis.ZP.rotationDegrees(angle));
         int size = Math.max(8, Math.round(markerHalfSize * 2.0F));
         gui.blit(LOCK_ICON, -size / 2, -size / 2, size, size, 0.0F, 0.0F, 64, 64, 64, 64);
         gui.pose().popPose();
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         RenderSystem.disableBlend();
      }
   };

   public static void toggleLock() {
      Minecraft mc = Minecraft.getInstance();
      Player player = mc.player;
      if (player != null) {
         if (TaiyokenBlindState.isActive()) {
            unlock();
         } else if (lockedTarget != null) {
            unlock();
         } else {
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
               if (data.getSkills().hasSkill("kisense")) {
                  int level = data.getSkills().getSkillLevel("kisense");
                  if (level > 0) {
                     double range = 15.0 + 5.0 * (double)level;
                     if (data.getStatus().isAndroidUpgraded()) {
                        range += 25.0;
                     }

                     findTargetInFront(player, range, data).ifPresent(target -> {
                        lockedTarget = target;
                        player.playSound((SoundEvent)MainSounds.LOCKON.get());
                     });
                  }
               }
            });
         }
      }
   }

   public static void unlock() {
      lockedTarget = null;
      markerVisible = false;
   }

   @SubscribeEvent
   public static void onClientTick(Post event) {
      Minecraft mc = Minecraft.getInstance();
      Player player = mc.player;
      if (player != null && lockedTarget != null) {
         if (TaiyokenBlindState.isActive()) {
            unlock();
         } else {
            scanTickCounter++;
            if (scanTickCounter >= 5) {
               scanTickCounter = 0;
               if (!lockedTarget.isAlive()) {
                  unlock();
                  return;
               }

               StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
                  int level = data.getSkills().getSkillLevel("kisense");
                  if (level > 0 && data.getSkills().getSkill("kisense") != null) {
                     double maxRange = 15.0 + 5.0 * (double)level;
                     if (data.getStatus().isAndroidUpgraded()) {
                        maxRange += 25.0;
                     }

                     if ((double)player.distanceTo(lockedTarget) > maxRange) {
                        unlock();
                     } else if (!KiSenseScan.canTarget(lockedTarget, data)) {
                        unlock();
                     } else {
                        if (!player.hasLineOfSight(lockedTarget) && !data.getStatus().isAndroidUpgraded()) {
                           unlock();
                        }
                     }
                  } else {
                     unlock();
                  }
               });
            }
         }
      }
   }

   @SubscribeEvent
   public static void onRenderTick(Pre event) {
      Minecraft mc = Minecraft.getInstance();
      Player player = mc.player;
      if (player != null && lockedTarget != null) {
         float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
         double targetX = Mth.lerp((double)partialTick, lockedTarget.xo, lockedTarget.getX());
         double targetY = Mth.lerp((double)partialTick, lockedTarget.yo, lockedTarget.getY()) + (double)lockedTarget.getBbHeight() * 0.5;
         double targetZ = Mth.lerp((double)partialTick, lockedTarget.zo, lockedTarget.getZ());
         Vec3 targetPos = new Vec3(targetX, targetY, targetZ);
         Vec3 playerPos = player.getEyePosition(partialTick);
         double dX = targetPos.x - playerPos.x;
         double dY = targetPos.y - playerPos.y;
         double dZ = targetPos.z - playerPos.z;
         double dist = Math.sqrt(dX * dX + dZ * dZ);
         float targetYaw = (float)(Mth.atan2(dZ, dX) * (180.0 / Math.PI)) - 90.0F;
         float targetPitch = (float)(-(Mth.atan2(dY, dist) * (180.0 / Math.PI)));
         float smoothFactor = 0.15F;
         float newYaw = rotlerp(player.getYRot(), targetYaw, smoothFactor);
         float newPitch = rotlerp(player.getXRot(), targetPitch, smoothFactor);
         player.setYRot(newYaw);
         player.setXRot(newPitch);
      }
   }

   @SubscribeEvent
   public static void onRenderWorldLast(RenderLevelStageEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (event.getStage() == Stage.AFTER_LEVEL) {
         if (lockedTarget != null && lockedTarget.isAlive()) {
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            double lerpX = Mth.lerp((double)partialTick, lockedTarget.xo, lockedTarget.getX());
            double lerpY = Mth.lerp((double)partialTick, lockedTarget.yo, lockedTarget.getY());
            double lerpZ = Mth.lerp((double)partialTick, lockedTarget.zo, lockedTarget.getZ());
            Vec3 cameraPos = event.getCamera().getPosition();
            Vector4f view = new Vector4f(
                  (float)(lerpX - cameraPos.x), (float)(lerpY - cameraPos.y + (double)(lockedTarget.getBbHeight() * 0.5F)), (float)(lerpZ - cameraPos.z), 1.0F
               )
               .mul(event.getModelViewMatrix());
            Vector4f clip = new Vector4f(view).mul(event.getProjectionMatrix());
            if (clip.w <= 0.001F) {
               markerVisible = false;
            } else {
               float ndcX = clip.x / clip.w;
               float ndcY = clip.y / clip.w;
               float ndcZ = clip.z / clip.w;
               if (!(ndcZ < -1.0F) && !(ndcZ > 1.0F) && !(Math.abs(ndcX) > 1.15F) && !(Math.abs(ndcY) > 1.15F)) {
                  int guiWidth = mc.getWindow().getGuiScaledWidth();
                  int guiHeight = mc.getWindow().getGuiScaledHeight();
                  markerX = (ndcX * 0.5F + 0.5F) * (float)guiWidth;
                  markerY = (0.5F - ndcY * 0.5F) * (float)guiHeight;
                  Vector4f edge = new Vector4f(view.x + 0.64F, view.y, view.z, 1.0F).mul(event.getProjectionMatrix());
                  float edgeNdcX = edge.w > 0.001F ? edge.x / edge.w : ndcX;
                  markerHalfSize = Mth.clamp(Math.abs(edgeNdcX - ndcX) * (float)guiWidth * 0.5F, 8.0F, 64.0F);
                  markerVisible = true;
               } else {
                  markerVisible = false;
               }
            }
         } else {
            markerVisible = false;
         }
      }
   }

   private static Optional<LivingEntity> findTargetInFront(Player player, double range, StatsData data) {
      Vec3 eyePos = player.getEyePosition();
      Vec3 viewVec = player.getViewVector(1.0F);
      Vec3 endPos = eyePos.add(viewVec.scale(range));
      AABB searchBox = player.getBoundingBox().expandTowards(viewVec.scale(range)).inflate(1.0);
      List<LivingEntity> list = player.level()
         .getEntitiesOfClass(LivingEntity.class, searchBox, ex -> ex != player && ex.isAlive() && ex.isPickable() && canTarget(ex, data));
      LivingEntity closest = null;
      double closestDist = range * range;

      for (LivingEntity e : list) {
         AABB axisalignedbb = e.getBoundingBox().inflate((double)e.getPickRadius());
         Optional<Vec3> hit = axisalignedbb.clip(eyePos, endPos);
         if (!e.isInvisible() && !e.isInvisibleTo(player) && player.hasLineOfSight(e)) {
            if (axisalignedbb.contains(eyePos)) {
               if (closestDist >= 0.0) {
                  closest = e;
                  closestDist = 0.0;
               }
            } else if (hit.isPresent()) {
               double dist = eyePos.distanceToSqr(hit.get());
               if (dist < closestDist) {
                  closest = e;
                  closestDist = dist;
               }
            }
         }
      }

      return Optional.ofNullable(closest);
   }

   private static float rotlerp(float start, float target, float amount) {
      float f = Mth.wrapDegrees(target - start);
      if (f > 180.0F) {
         f -= 360.0F;
      }

      if (f < -180.0F) {
         f += 360.0F;
      }

      return start + amount * f;
   }

   public static boolean canTarget(LivingEntity target, StatsData myData) {
      if (target instanceof Player targetPlayer) {
         StatsData targetData = StatsProvider.get(StatsCapability.INSTANCE, targetPlayer).orElse(null);
         if (targetData == null) {
            return true;
         } else {
            return KiSenseScan.isCloaked(targetPlayer)
               ? false
               : !TransformationsHelper.hasGodFormActive(targetData) || myData.getSkills().getSkillLevel("godforms") > 0;
         }
      } else {
         return true;
      }
   }

   public static LivingEntity getLockedTarget() {
      return lockedTarget;
   }
}
