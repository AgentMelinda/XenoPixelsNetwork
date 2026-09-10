package com.dragonminez.client.events;

import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(
   modid = "dragonminez",
   value = {Dist.CLIENT}
)
public class EffectsEvents {
   private static boolean isBioAndroidDrainingCache = false;
   private static boolean isChargingFormCache = false;

   @SubscribeEvent
   public static void onClientTick(Post event) {
      Minecraft mc = Minecraft.getInstance();
      Player player = mc.player;
      if (player != null) {
         if (player.hasEffect(MainEffects.STUN)) {
            isBioAndroidDrainingCache = StatsProvider.get(StatsCapability.INSTANCE, player)
               .map(data -> "bioandroid".equals(data.getCharacter().getRaceName()) && data.getStatus().getDrainingTargetId() != -1)
               .orElse(false);
         } else {
            isBioAndroidDrainingCache = false;
         }

         isChargingFormCache = StatsProvider.get(StatsCapability.INSTANCE, player)
            .map(data -> data.getStatus().isAuraActive() && !data.getStatus().isPermanentAura())
            .orElse(false);
      }
   }

   @SubscribeEvent
   public static void onCameraSetup(ComputeCameraAngles event) {
      Minecraft mc = Minecraft.getInstance();
      Player player = mc.player;
      if (player != null && !mc.isPaused()) {
         double time = (double)player.level().getGameTime() + event.getPartialTick();
         double shakeRadius = 15.0;

         for (DBSagasEntity entity : player.level().getEntitiesOfClass(DBSagasEntity.class, player.getBoundingBox().inflate(shakeRadius))) {
            if (entity.isAlive() && entity.isCasting() && entity.getSkillType() == 7) {
               double distance = (double)player.distanceTo(entity);
               if (distance <= shakeRadius) {
                  float intensity = (float)(1.0 - distance / shakeRadius);
                  float shakePitch = (player.getRandom().nextFloat() - 0.5F) * 2.5F * intensity;
                  float shakeYaw = (player.getRandom().nextFloat() - 0.5F) * 2.5F * intensity;
                  float shakeRoll = (player.getRandom().nextFloat() - 0.5F) * 1.5F * intensity;
                  event.setPitch(event.getPitch() + shakePitch);
                  event.setYaw(event.getYaw() + shakeYaw);
                  event.setRoll(event.getRoll() + shakeRoll);
                  break;
               }
            }
         }

         double kiShakeRadius = 50.0;

         for (AbstractKiProjectile kiAttack : player.level().getEntitiesOfClass(AbstractKiProjectile.class, player.getBoundingBox().inflate(kiShakeRadius))) {
            boolean isActuallyFiring = false;
            if (kiAttack instanceof KiWaveEntity wave) {
               isActuallyFiring = wave.isFiring();
            } else if (kiAttack instanceof KiBlastEntity) {
               isActuallyFiring = true;
            }

            if (kiAttack.isAlive() && isActuallyFiring) {
               double distance = (double)player.distanceTo(kiAttack);
               if (distance <= kiShakeRadius) {
                  float intensity = (float)(1.0 - distance / kiShakeRadius);
                  float shakePitch = (player.getRandom().nextFloat() - 0.5F) * 0.5F * intensity;
                  float shakeYaw = (player.getRandom().nextFloat() - 0.5F) * 0.5F * intensity;
                  float shakeRoll = (player.getRandom().nextFloat() - 0.5F) * 0.1F * intensity;
                  event.setPitch(event.getPitch() + shakePitch);
                  event.setYaw(event.getYaw() + shakeYaw);
                  event.setRoll(event.getRoll() + shakeRoll);
                  break;
               }
            }
         }

         if (player.hasEffect(MainEffects.STAGGER)) {
            int amplifier = player.getEffect(MainEffects.STAGGER).getAmplifier();
            float baseIntensity = 1.5F + (float)amplifier * 0.8F;
            float yawShake = (float)(Math.sin(time * 0.5) * (double)baseIntensity * 0.8F + Math.sin(time * 1.2) * (double)baseIntensity * 0.4F);
            float pitchShake = (float)(Math.cos(time * 0.6) * (double)baseIntensity * 2.0 + Math.cos(time * 0.9) * (double)baseIntensity * 1.0);
            float rollShake = (float)(Math.sin(time * 0.7) * (double)baseIntensity * 0.6F);
            event.setYaw(event.getYaw() + yawShake);
            event.setPitch(event.getPitch() + pitchShake);
            event.setRoll(event.getRoll() + rollShake);
         }

         if (isChargingFormCache) {
            float globalshake = 0.05F;
            float sweepIntensity = 2.5F * globalshake;
            float sweepYaw = (float)(Math.sin(time * 1.5) * (double)sweepIntensity);
            float sweepPitch = (float)(Math.cos(time * 1.2) * (double)(sweepIntensity * 0.6F));
            float shakeIntensity = 0.2F * globalshake;
            float shakeYaw = (float)(Math.sin(time * 5.0) * (double)shakeIntensity);
            float shakePitch = (float)(Math.cos(time * 6.0) * (double)shakeIntensity);
            float rollIntensity = 1.5F * globalshake;
            float tfRoll = (float)(Math.sin(time * 2.0) * (double)rollIntensity);
            event.setYaw(event.getYaw() + sweepYaw + shakeYaw);
            event.setPitch(event.getPitch() + sweepPitch + shakePitch);
            event.setRoll(event.getRoll() + tfRoll);
         }
      }
   }

   @SubscribeEvent
   public static void renderStaggerOverlay(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Post event) {
      if (VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
         Minecraft mc = Minecraft.getInstance();
         Player player = mc.player;
         if (player != null && player.hasEffect(MainEffects.STAGGER)) {
            int amplifier = player.getEffect(MainEffects.STAGGER).getAmplifier();
            float blurStrength = 0.3F + (float)amplifier * 0.15F;
            GuiGraphics graphics = event.getGuiGraphics();
            int width = graphics.guiWidth();
            int height = graphics.guiHeight();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            int alpha = (int)(blurStrength * 80.0F);
            int color = alpha << 24 | 16777215;
            graphics.fill(0, 0, width, height, color);
            RenderSystem.disableBlend();
         }
      }
   }

   @SubscribeEvent
   public static void renderStunOverlay(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Post event) {
      if (VanillaGuiLayers.CAMERA_OVERLAYS.equals(event.getName())) {
         Minecraft mc = Minecraft.getInstance();
         Player player = mc.player;
         if (player != null && player.hasEffect(MainEffects.STUN)) {
            if (isBioAndroidDrainingCache) {
               return;
            }

            int amplifier = player.getEffect(MainEffects.STUN).getAmplifier();
            float blurStrength = 0.3F + (float)amplifier * 0.15F;
            GuiGraphics graphics = event.getGuiGraphics();
            int width = graphics.guiWidth();
            int height = graphics.guiHeight();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            int alpha = (int)(blurStrength * 80.0F);
            int color = alpha << 24 | 16777215;
            graphics.fill(0, 0, width, height, color);
            RenderSystem.disableBlend();
         }
      }
   }
}
