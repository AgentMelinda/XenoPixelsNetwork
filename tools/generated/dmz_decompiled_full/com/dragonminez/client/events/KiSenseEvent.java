package com.dragonminez.client.events;

import com.dragonminez.client.gui.hud.HudStatNumberAnimator;
import com.dragonminez.client.render.util.RenderBufferUtil;
import com.dragonminez.client.systems.kisense.CombatIndicators;
import com.dragonminez.client.systems.kisense.KiSenseScan;
import com.dragonminez.client.systems.kisense.KiSenseState;
import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import org.joml.Matrix4f;

@EventBusSubscriber(
   modid = "dragonminez",
   value = {Dist.CLIENT}
)
public class KiSenseEvent {
   private static final ResourceLocation HUD_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/hud/alternativehud.png");
   private static final ResourceLocation DMZ_FONT = ResourceLocation.fromNamespaceAndPath("dragonminez", "smooth");
   static NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
   private static final double LOD_DISTANCE = 24.0;
   private static final float BAR_MAX_WIDTH = 76.0F;
   private static final float BAR_STACK = 10.0F;
   private static final int DAMAGE_COLOR = 16733525;
   private static final int HEAL_COLOR = 5635925;
   private static final Map<Integer, HudStatNumberAnimator> healthAnimators = new HashMap<>();
   private static final Map<Integer, Float> lerpedHealthWidths = new HashMap<>();

   @SubscribeEvent
   public static void onClientTick(Post event) {
      Minecraft mc = Minecraft.getInstance();
      Player player = mc.player;
      if (player != null && mc.level != null) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            if (!data.getStatus().isHasCreatedCharacter()) {
               KiSenseScan.clear();
               CombatIndicators.clear();
            } else {
               if (KiSenseState.isActive() && (!data.getSkills().isSkillActive("kisense") || data.getSkills().getSkillLevel("kisense") <= 0)) {
                  KiSenseState.reset();
               }

               if (KiSenseState.isActive() && KiSenseScan.hasScouter(player)) {
                  KiSenseState.set(KiSenseState.Mode.NONE);
               } else {
                  KiSenseScan.tick(player, data, KiSenseState.getMode());
                  if (KiSenseState.isCombat()) {
                     CombatIndicators.tick();
                  } else {
                     CombatIndicators.clear();
                  }
               }
            }
         });
         lerpedHealthWidths.keySet().retainAll(KiSenseScan.getCombatEntities());
         healthAnimators.keySet().retainAll(KiSenseScan.getCombatEntities());
      }
   }

   @SubscribeEvent
   public static void onRenderNameTag(RenderNameTagEvent event) {
      if (KiSenseState.isCombat()) {
         if (event.getEntity() instanceof LivingEntity entity) {
            if (KiSenseScan.getCombatEntities().contains(entity.getId())) {
               renderCombatOverlay(event.getPoseStack(), entity, event.getPartialTick());
            }
         }
      }
   }

   private static void renderCombatOverlay(PoseStack poseStack, LivingEntity entity, float partialTick) {
      Minecraft mc = Minecraft.getInstance();
      boolean lod = mc.player != null && (double)mc.player.distanceTo(entity) > 24.0;
      poseStack.pushPose();
      poseStack.translate(0.0, (double)entity.getBbHeight() + 0.8, 0.0);
      poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
      float scale = 0.025F;
      poseStack.scale(-scale, -scale, scale);
      RenderSystem.disableDepthTest();
      float topY;
      if (entity instanceof Player target) {
         StatsData data = StatsProvider.get(StatsCapability.INSTANCE, target).orElse(null);
         if (data != null) {
            drawStaminaBar(poseStack, data, 0.0F);
            drawKiBar(poseStack, data, -10.0F);
         }

         renderHealthBar(poseStack, entity, partialTick, lod, -20.0F);
         topY = -20.0F;
      } else {
         renderHealthBar(poseStack, entity, partialTick, lod, 0.0F);
         topY = 0.0F;
      }

      renderBPLabel(poseStack, entity, topY);
      renderIndicators(poseStack, entity, partialTick, lod, topY);
      RenderSystem.enableDepthTest();
      poseStack.popPose();
   }

   private static void drawKiBar(PoseStack poseStack, StatsData data, float baseY) {
      float maxKi = Math.max(1.0F, data.getMaxEnergy());
      float pct = Mth.clamp(data.getResources().getCurrentEnergy() / maxKi, 0.0F, 1.0F);
      int fillW = 7 + (int)(76.0F * pct);
      float x = -41.5F;
      poseStack.pushPose();
      poseStack.translate(0.0F, baseY, 0.0F);
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.setShaderTexture(0, HUD_TEXTURE);
      drawTexture(poseStack, x, 0.0F, 83, 9, 0, 44);
      float[] auraRgb = auraColorRgb(data);
      RenderSystem.setShaderColor(auraRgb[0], auraRgb[1], auraRgb[2], 1.0F);
      drawTexture(poseStack, x + 3.0F, 3.0F, fillW, 4, 3, 61);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      poseStack.popPose();
   }

   private static void drawStaminaBar(PoseStack poseStack, StatsData data, float baseY) {
      float maxStm = Math.max(1.0F, data.getMaxStamina());
      float pct = Mth.clamp(data.getResources().getCurrentStamina() / maxStm, 0.0F, 1.0F);
      int fillW = (int)(76.0F * pct) - 5;
      float x = -41.5F;
      poseStack.pushPose();
      poseStack.translate(0.0F, baseY, 0.0F);
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.setShaderTexture(0, HUD_TEXTURE);
      drawTexture(poseStack, x, 0.0F, 83, 9, 0, 72);
      if (fillW > 0) {
         drawTexture(poseStack, x + 2.0F, 3.0F, fillW, 4, 2, 90);
      }

      drawTexture(poseStack, x + 77.0F, 3.0F, 4, 4, 77, 90);
      poseStack.popPose();
   }

   private static float[] auraColorRgb(StatsData data) {
      Character character = data.getCharacter();
      String auraColor = character.getAuraColor();
      FormConfig.FormData formData = character.getActiveStackForm() != null && !character.getActiveStackForm().isEmpty()
         ? character.getActiveStackFormData()
         : (character.getActiveForm() != null && !character.getActiveForm().isEmpty() ? character.getActiveFormData() : null);
      if (formData != null && formData.getAuraColor() != null && !formData.getAuraColor().isEmpty()) {
         auraColor = formData.getAuraColor();
      }

      return ColorUtils.hexToRgb(auraColor != null ? auraColor : "#FFFFFF");
   }

   private static void renderHealthBar(PoseStack poseStack, LivingEntity entity, float partialTick, boolean lod, float baseY) {
      Minecraft mc = Minecraft.getInstance();
      float health = entity.getHealth();
      float maxHealth = entity.getMaxHealth();
      float healthPercent = Math.max(0.0F, Math.min(1.0F, health / maxHealth));
      poseStack.pushPose();
      poseStack.translate(0.0F, baseY, 0.0F);
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.setShaderTexture(0, HUD_TEXTURE);
      float width = 83.0F;
      float x = -width / 2.0F;
      float y = 0.0F;
      drawTexture(poseStack, x, y, (int)width, 9, 0, 0);
      float targetBarWidth = 76.0F * healthPercent;
      int entityId = entity.getId();
      float currentBarWidth;
      if (lod) {
         currentBarWidth = targetBarWidth;
      } else {
         currentBarWidth = lerpedHealthWidths.getOrDefault(entityId, targetBarWidth);
         currentBarWidth += (targetBarWidth - currentBarWidth) * 0.25F * partialTick;
         if (Math.abs(currentBarWidth - targetBarWidth) <= 0.5F) {
            currentBarWidth = targetBarWidth;
         }
      }

      lerpedHealthWidths.put(entityId, currentBarWidth);
      int fillV;
      if (healthPercent < 0.33F) {
         fillV = 33;
      } else if (healthPercent < 0.66F) {
         fillV = 22;
      } else {
         fillV = 11;
      }

      if (currentBarWidth > 0.0F) {
         drawTexture(poseStack, x + 2.0F, y + 3.0F, 7 + (int)currentBarWidth, 5, 2, fillV);
      }

      boolean showPercent = ConfigManager.getUserConfig().getAdvancedDescriptionPercentage();
      String textStr = showPercent
         ? String.format("%.0f", health / maxHealth * 100.0F) + "%"
         : numberFormat.format((double)health) + " / " + numberFormat.format((double)maxHealth);
      MutableComponent text = txt(textStr);
      poseStack.pushPose();
      float textScale = 0.6F;
      poseStack.scale(textScale, textScale, textScale);
      float textY = 3.0F;
      BufferSource bufferSource = mc.renderBuffers().bufferSource();
      float textWidth = (float)mc.font.width(text);
      float textX = -textWidth / 2.0F;
      if (lod) {
         drawText(poseStack, text, textX, textY, 16777215, 1.0F, bufferSource);
         bufferSource.endBatch();
      } else {
         HudStatNumberAnimator animator = healthAnimators.computeIfAbsent(
            entityId, id -> new HudStatNumberAnimator(HudStatNumberAnimator.StatKind.KISENSE_HEALTH)
         );
         float value = showPercent ? (float)Math.round(health / maxHealth * 100.0F) : (float)Math.round(health);
         HudStatNumberAnimator.RenderState state = animator.update(textStr, value, (float)mc.player.tickCount + partialTick);
         if (!state.isHidden()) {
            poseStack.translate(state.offsetX(), state.offsetY(), 0.0F);
            drawText(poseStack, text, textX, textY, state.rgbColor(), state.alpha(), bufferSource);
            bufferSource.endBatch();
         }
      }

      poseStack.popPose();
      poseStack.popPose();
   }

   private static void renderIndicators(PoseStack poseStack, LivingEntity entity, float partialTick, boolean lod, float topY) {
      Minecraft mc = Minecraft.getInstance();
      int id = entity.getId();
      float time = (float)mc.level.getGameTime() + partialTick;
      float maxHealth = entity.getMaxHealth();
      BufferSource bufferSource = mc.renderBuffers().bufferSource();
      poseStack.pushPose();
      poseStack.translate(0.0F, topY, 0.0F);
      float textScale = 1.05F;
      poseStack.scale(textScale, textScale, textScale);
      if (ConfigManager.getUserConfig().getShowAccumulativeDamage()) {
         float damage = CombatIndicators.getAccumDamage(id);
         float heal = CombatIndicators.getAccumHeal(id);
         if (damage > 0.0F) {
            float alpha = lod ? 1.0F : accumAlpha(time - (float)CombatIndicators.getDamageTick(id));
            drawIndicator(
               poseStack,
               formatDelta(damage, false, maxHealth),
               CombatIndicators.getDamageOffX(id),
               CombatIndicators.getDamageOffY(id),
               16733525,
               alpha,
               bufferSource
            );
         }

         if (heal > 0.0F) {
            float alpha = lod ? 1.0F : accumAlpha(time - (float)CombatIndicators.getHealTick(id));
            drawIndicator(
               poseStack, formatDelta(heal, true, maxHealth), CombatIndicators.getHealOffX(id), CombatIndicators.getHealOffY(id), 5635925, alpha, bufferSource
            );
         }
      } else {
         for (CombatIndicators.DamagePopup popup : CombatIndicators.getPopups(id)) {
            float age = time - (float)popup.bornTick();
            float life = Mth.clamp(age / 30.0F, 0.0F, 1.0F);
            float alpha = lod ? 1.0F : 1.0F - life;
            float rise = lod ? 0.0F : life * 10.0F;
            int color = popup.heal() ? 5635925 : 16733525;
            drawIndicator(poseStack, formatDelta(popup.value(), popup.heal(), maxHealth), popup.offX(), popup.offY() - rise, color, alpha, bufferSource);
         }
      }

      bufferSource.endBatch();
      poseStack.popPose();
   }

   private static String formatDelta(float amount, boolean heal, float maxHealth) {
      String sign = heal ? "+" : "-";
      return ConfigManager.getUserConfig().getAdvancedDescriptionPercentage() && maxHealth > 0.0F
         ? sign + String.format("%.0f", amount / maxHealth * 100.0F) + "%"
         : sign + Math.round(amount);
   }

   private static void renderBPLabel(PoseStack poseStack, LivingEntity entity, float topY) {
      Minecraft mc = Minecraft.getInstance();
      float bp = KiSenseScan.getCachedBP(entity.getId());
      boolean isPlayer = entity instanceof Player;
      boolean isMaxed = isPlayer ? bp >= Float.MAX_VALUE : bp >= 2.1474836E9F;
      String bpStr = isMaxed ? "BP: ???" : "BP: " + String.format("%,.0f", bp).replace(",", ".");
      MutableComponent text = txt(bpStr);
      BufferSource bufferSource = mc.renderBuffers().bufferSource();
      poseStack.pushPose();
      poseStack.translate(0.0F, topY, 0.0F);
      float textScale = 0.6F;
      poseStack.scale(textScale, textScale, textScale);
      float textWidth = (float)mc.font.width(text);
      drawText(poseStack, text, -textWidth / 2.0F, -8.0F, 16777215, 1.0F, bufferSource);
      bufferSource.endBatch();
      poseStack.popPose();
   }

   private static float accumAlpha(float ticksSinceChange) {
      float fadeStart = 25.0F;
      float fadeEnd = 40.0F;
      if (ticksSinceChange <= fadeStart) {
         return 1.0F;
      } else {
         return ticksSinceChange >= fadeEnd ? 0.0F : 1.0F - (ticksSinceChange - fadeStart) / (fadeEnd - fadeStart);
      }
   }

   private static void drawIndicator(PoseStack poseStack, String str, float centerX, float y, int rgb, float alpha, BufferSource bufferSource) {
      if (!(alpha <= 0.01F)) {
         Minecraft mc = Minecraft.getInstance();
         MutableComponent text = txt(str);
         float textWidth = (float)mc.font.width(text);
         drawText(poseStack, text, centerX - textWidth / 2.0F, y, rgb, alpha, bufferSource);
      }
   }

   private static void drawText(PoseStack poseStack, MutableComponent text, float textX, float textY, int rgb, float alpha, BufferSource bufferSource) {
      Minecraft mc = Minecraft.getInstance();
      int color = withAlpha(rgb, alpha);
      int borderColor = withAlpha(0, alpha);
      int light = 15728880;
      Matrix4f matrix = poseStack.last().pose();
      mc.font.drawInBatch(text, textX + 1.0F, textY, borderColor, false, matrix, bufferSource, DisplayMode.SEE_THROUGH, 0, light);
      mc.font.drawInBatch(text, textX - 1.0F, textY, borderColor, false, matrix, bufferSource, DisplayMode.SEE_THROUGH, 0, light);
      mc.font.drawInBatch(text, textX, textY + 1.0F, borderColor, false, matrix, bufferSource, DisplayMode.SEE_THROUGH, 0, light);
      mc.font.drawInBatch(text, textX, textY - 1.0F, borderColor, false, matrix, bufferSource, DisplayMode.SEE_THROUGH, 0, light);
      bufferSource.endBatch();
      mc.font.drawInBatch(text, textX, textY, color, false, matrix, bufferSource, DisplayMode.SEE_THROUGH, 0, light);
      bufferSource.endBatch();
   }

   private static int withAlpha(int rgb, float alpha) {
      int alphaChannel = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
      return alphaChannel << 24 | rgb & 16777215;
   }

   private static void drawTexture(PoseStack poseStack, float x, float y, int width, int height, int u, int v) {
      float textureSize = 128.0F;
      float minU = (float)u / textureSize;
      float maxU = (float)(u + width) / textureSize;
      float minV = (float)v / textureSize;
      float maxV = (float)(v + height) / textureSize;
      RenderBufferUtil.drawTexturedQuad(poseStack.last().pose(), x, y, x + (float)width, y + (float)height, 0.0F, minU, minV, maxU, maxV);
   }

   private static MutableComponent txt(String text) {
      return Component.literal(text).withStyle(Style.EMPTY.withFont(DMZ_FONT));
   }

   static {
      numberFormat.setMaximumFractionDigits(1);
      numberFormat.setMinimumFractionDigits(0);
   }
}
