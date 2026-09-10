package com.dragonminez.client.gui.hud;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.MainItems;
import com.dragonminez.common.init.entities.IBattlePower;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.DamageCurioC2S;
import com.dragonminez.common.quest.QuestUnlocks;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.util.CuriosUtil;
import com.dragonminez.compat.util.LazyOptional;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw.Layer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

@EventBusSubscriber(
   modid = "dragonminez",
   value = {Dist.CLIENT}
)
public class ScouterHUD {
   private static final ResourceLocation SCOUTER_GREEN = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/scouter/scouter_green.png");
   private static final ResourceLocation SCOUTER_RED = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/scouter/scouter_red.png");
   private static final ResourceLocation SCOUTER_BLUE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/scouter/scouter_blue.png");
   private static final ResourceLocation SCOUTER_PURPLE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/scouter/scouter_purple.png");
   private static boolean isRenderingInfo = false;
   private static int scanTimer = 0;
   private static int strongestEntityID = -1;
   private static double cachedBP = 0.0;
   private static final double SCAN_RANGE = 50.0;
   private static final int BP_LIMIT = 150000000;
   public static final Layer HUD_SCOUTER = (guiGraphics, deltaTracker) -> {
      float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
      int width = guiGraphics.guiWidth();
      int height = guiGraphics.guiHeight();
      Minecraft mc = Minecraft.getInstance();
      if (!mc.getDebugOverlay().showDebugScreen() && mc.player != null) {
         if (!ConfigManager.getUserConfig().getAlternativeHud()) {
            ItemStack scouterStack = getScouterStack(mc.player);
            if (!scouterStack.isEmpty() && scouterStack.getItem().getDescriptionId().contains("scouter")) {
               Item currentItem = scouterStack.getItem();
               ResourceLocation currentTexture = currentItem == MainItems.BLUE_SCOUTER.get()
                  ? SCOUTER_BLUE
                  : (
                     currentItem == MainItems.RED_SCOUTER.get()
                        ? SCOUTER_RED
                        : (currentItem == MainItems.PURPLE_SCOUTER.get() ? SCOUTER_PURPLE : SCOUTER_GREEN)
                  );
               guiGraphics.pose().pushPose();
               guiGraphics.pose().translate(0.0F, (float)height / 2.0F, 0.0F);
               guiGraphics.pose().scale(2.5F, 2.5F, 1.0F);
               renderScouterFrame(guiGraphics, currentTexture);
               if (isRenderingInfo) {
                  HitResult hit = mc.hitResult;
                  LivingEntity focusedEntity = null;
                  if (hit != null && hit.getType() == Type.ENTITY && ((EntityHitResult)hit).getEntity() instanceof LivingEntity living) {
                     focusedEntity = living;
                  }

                  double distToFocus = focusedEntity != null ? (double)mc.player.distanceTo(focusedEntity) : Double.MAX_VALUE;
                  if (focusedEntity != null && distToFocus <= 20.0) {
                     double bp = getEntityBP(focusedEntity);
                     if (bp > 1.5E8) {
                        damageScouter(mc.player);
                        guiGraphics.pose().popPose();
                        return;
                     }

                     if (QuestUnlocks.isCompleted(mc.player, "bulma_scouter_calibration")) {
                        renderCustomNumbers(guiGraphics, currentTexture, formatBP(bp));
                     }

                     renderEntityInfo(
                        guiGraphics, currentTexture, QuestUnlocks.isCompleted(mc.player, "bulma_scouter_bioscan"), focusedEntity instanceof Player
                     );
                  } else if (focusedEntity != null && distToFocus > 20.0 && distToFocus <= 50.0) {
                     renderDirectionIcon(guiGraphics, currentTexture, mc.player, focusedEntity, true);
                     renderEntityInfo(guiGraphics, currentTexture, false, focusedEntity instanceof Player);
                  } else if (mc.player.level().getEntity(strongestEntityID) instanceof LivingEntity livingStrongest
                     && livingStrongest.isAlive()
                     && (double)mc.player.distanceTo(livingStrongest) <= 50.0) {
                     renderDirectionIcon(guiGraphics, currentTexture, mc.player, livingStrongest, false);
                  }
               }

               guiGraphics.pose().popPose();
            }
         }
      }
   };

   private static ItemStack getScouterStack(Player player) {
      return CuriosUtil.getFirstStack(player, "head_tech");
   }

   @SubscribeEvent
   public static void onClientTick(Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         ItemStack scouterStack = getScouterStack(mc.player);
         if (!scouterStack.isEmpty() && scouterStack.getDescriptionId().contains("scouter")) {
            if (isRenderingInfo && scanTimer++ >= 20) {
               scanTimer = 0;
               performSmartScan(mc.player);
            }
         } else {
            isRenderingInfo = false;
         }
      }
   }

   private static void performSmartScan(Player player) {
      boolean var10000;
      label38: {
         if (player.level().getEntity(strongestEntityID) instanceof LivingEntity living && living.isAlive()) {
            var10000 = true;
            break label38;
         }

         var10000 = false;
      }

      boolean cachedIsAlive = var10000;
      double thresholdBP = cachedIsAlive ? cachedBP : -1.0;
      AABB searchBox = player.getBoundingBox().inflate(50.0);
      List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, searchBox, e -> e != player && e.isAlive());
      LivingEntity newStrongest = null;
      double maxFoundBP = thresholdBP;

      for (LivingEntity entity : entities) {
         double bp = getEntityBP(entity);
         if (bp > 1.5E8) {
            damageScouter(player);
            return;
         }

         if (bp > maxFoundBP) {
            maxFoundBP = bp;
            newStrongest = entity;
         }
      }

      if (newStrongest != null) {
         strongestEntityID = newStrongest.getId();
         cachedBP = maxFoundBP;
      } else if (!cachedIsAlive) {
         strongestEntityID = -1;
         cachedBP = 0.0;
      }
   }

   private static boolean isCloaked(Player target) {
      return CuriosUtil.getFirstStack(target, "head_tech").getItem() == MainItems.ANTI_KI_CLOAK.get();
   }

   private static double getEntityBP(LivingEntity entity) {
      try {
         if (entity instanceof Player player) {
            if (isCloaked(player)) {
               return 0.0;
            }

            LazyOptional<StatsData> cap = StatsProvider.get(StatsCapability.INSTANCE, player);
            if (cap.isPresent()) {
               return (double)cap.map(StatsData::getBattlePower).orElse(0.0F).floatValue();
            }
         }

         if (entity instanceof IBattlePower bpEntity) {
            return (double)bpEntity.getBattlePower();
         }
      } catch (Exception var3) {
         LogUtil.error(Env.CLIENT, "Error calculating BP for entity ID " + entity.getId() + ": " + var3.getMessage());
      }

      return 0.0;
   }

   private static void damageScouter(Player player) {
      setRenderingInfo(false);
      NetworkHandler.sendToServer(new DamageCurioC2S("head_tech", 0, 1));
      CuriosApi.getCuriosInventory(player).ifPresent(inv -> {
         ICurioStacksHandler handler = (ICurioStacksHandler)inv.getCurios().get("head_tech");
         if (handler != null) {
            ItemStack stack = handler.getStacks().getStackInSlot(0);
            if (!stack.isEmpty() && stack.getDescriptionId().contains("scouter")) {
               stack.setDamageValue(stack.getDamageValue() + 1);
               if (stack.getDamageValue() >= stack.getMaxDamage()) {
                  player.playSound(SoundEvents.GLASS_BREAK, 1.0F, 1.0F);
                  handler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
               } else {
                  player.playSound(SoundEvents.GLASS_HIT, 0.5F, 1.0F);
               }
            }
         }
      });
   }

   private static void renderScouterFrame(GuiGraphics gui, ResourceLocation texture) {
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      gui.blit(texture, 0, -20, 0.0F, 15.0F, 7, 41, 128, 128);
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.65F);
      gui.blit(texture, 7, -20, 7.0F, 15.0F, 63, 41, 128, 128);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.disableBlend();
   }

   private static void renderCustomNumbers(GuiGraphics gui, ResourceLocation texture, String text) {
      int charWidth = 3;
      int spacing = 1;
      int currentTextWidth = text.length() * charWidth + (text.length() - 1) * spacing;
      int maxPossibleWidth = 4 * charWidth + 3 * spacing;
      int centerOffset = (maxPossibleWidth - currentTextWidth) / 2;
      int startX = 10 + centerOffset;
      int startY = -4;

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         int u = 2;
         int v = 64;
         switch (c) {
            case '.':
               u = 26;
               v = 64;
               break;
            case '0':
               u = 2;
               v = 64;
               break;
            case '1':
               u = 6;
               v = 64;
               break;
            case '2':
               u = 10;
               v = 64;
               break;
            case '3':
               u = 14;
               v = 64;
               break;
            case '4':
               u = 18;
               v = 64;
               break;
            case '5':
               u = 2;
               v = 68;
               break;
            case '6':
               u = 6;
               v = 68;
               break;
            case '7':
               u = 10;
               v = 68;
               break;
            case '8':
               u = 14;
               v = 68;
               break;
            case '9':
               u = 18;
               v = 68;
               break;
            case 'k':
               u = 22;
               v = 64;
               break;
            case 'm':
               u = 22;
               v = 68;
         }

         gui.blit(texture, startX + i * (charWidth + 1), startY, (float)u, (float)v, charWidth, 3, 128, 128);
      }
   }

   private static void renderDirectionIcon(GuiGraphics gui, ResourceLocation texture, Player player, LivingEntity target, boolean isCircleMode) {
      double dx = target.getX() - player.getX();
      double dz = target.getZ() - player.getZ();
      double angleToTarget = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
      double diff = -Mth.wrapDegrees(angleToTarget - (double)player.getYRot());
      int dir = 0;
      if (diff >= -22.5 && diff < 22.5) {
         dir = 0;
      } else if (diff >= -67.5 && diff < -22.5) {
         dir = 1;
      } else if (diff >= -112.5 && diff < -67.5) {
         dir = 2;
      } else if (diff >= -157.5 && diff < -112.5) {
         dir = 3;
      } else if (diff >= 157.5 || diff < -157.5) {
         dir = 4;
      } else if (diff >= 112.5 && diff < 157.5) {
         dir = 5;
      } else if (diff >= 67.5 && diff < 112.5) {
         dir = 6;
      } else if (diff >= 22.5 && diff < 67.5) {
         dir = 7;
      }

      switch (dir) {
         case 0:
            gui.blit(texture, 40, -16, 26.0F, 75.0F, 5, 5, 128, 128);
            break;
         case 1:
            gui.blit(texture, 40, -16, 26.0F, 75.0F, 5, 5, 128, 128);
            gui.blit(texture, 50, -8, 14.0F, 75.0F, 5, 5, 128, 128);
            break;
         case 2:
            gui.blit(texture, 50, -8, 14.0F, 75.0F, 5, 5, 128, 128);
            break;
         case 3:
            gui.blit(texture, 50, -8, 14.0F, 75.0F, 5, 5, 128, 128);
            gui.blit(texture, 40, 0, 34.0F, 75.0F, 5, 5, 128, 128);
            break;
         case 4:
            gui.blit(texture, 40, 0, 34.0F, 75.0F, 5, 5, 128, 128);
            break;
         case 5:
            gui.blit(texture, 40, 0, 34.0F, 75.0F, 5, 5, 128, 128);
            gui.blit(texture, 30, -8, 19.0F, 75.0F, 5, 5, 128, 128);
            break;
         case 6:
            gui.blit(texture, 30, -8, 19.0F, 75.0F, 5, 5, 128, 128);
            break;
         case 7:
            gui.blit(texture, 40, -16, 26.0F, 75.0F, 5, 5, 128, 128);
            gui.blit(texture, 30, -8, 19.0F, 75.0F, 5, 5, 128, 128);
      }
   }

   private static void renderEntityInfo(GuiGraphics gui, ResourceLocation texture, boolean extraInfo, boolean isPlayer) {
      gui.pose().pushPose();
      gui.pose().translate(40.0F, -30.0F, 0.0F);
      gui.pose().scale(2.0F, 2.0F, 1.0F);
      gui.blit(texture, 0, 0, 2.0F, 73.0F, 9, 9, 128, 128);
      gui.pose().popPose();
      if (extraInfo) {
         gui.blit(texture, 50, -10, 4.0F, 88.0F, 12, 5, 128, 128);
         int uX = isPlayer ? 3 : 20;
         int w = isPlayer ? 14 : 11;
         gui.blit(texture, 53, -15, (float)uX, 98.0F, w, 5, 128, 128);
      }
   }

   private static String formatBP(double bp) {
      if (bp < 10000.0) {
         return String.valueOf(bp);
      } else {
         return bp < 1000000.0 ? String.format("%.1fk", bp / 1000.0).replace(",", ".") : String.format("%.1fm", bp / 1000000.0).replace(",", ".");
      }
   }

   public static void setRenderingInfo(boolean render) {
      isRenderingInfo = render;
   }

   public static boolean isRenderingInfo() {
      return isRenderingInfo;
   }
}
