package com.dragonminez.client.render.effects;

import com.dragonminez.client.render.camera.OverShoulderCamera;
import com.dragonminez.client.render.shader.DMZShaders;
import com.dragonminez.client.render.util.AuraMeshFactory;
import com.dragonminez.client.render.util.IrisCompat;
import com.dragonminez.client.render.util.ModRenderTypes;
import com.dragonminez.client.render.util.PlayerEffectQueue;
import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.compat.SableCompat;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.ActionMode;
import com.dragonminez.common.util.TransformationsHelper;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexBuffer.Usage;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import software.bernie.geckolib.cache.object.BakedGeoModel;

public class AuraRenderer {
   private static final float HALF_SQRT_3 = (float)(Math.sqrt(3.0) / 2.0);
   private static final float FADE_SPEED = 0.005F;
   private static final float PULSE_SPEED = 0.01F;
   private static final float SHOULDER_LEAN_DEG_PER_BLOCK = 3.0F;
   private static final float SHOULDER_LEAN_MAX_DEG = 6.0F;
   private static final Map<Integer, Long> FUSION_START_TIME = new ConcurrentHashMap<>();
   private static final Map<Integer, Boolean> WAS_FUSED_CACHE = new ConcurrentHashMap<>();
   private static final Map<Integer, Float> COLOR_PROGRESS_MAP = new ConcurrentHashMap<>();
   private static final Map<Integer, Long> COLOR_TICK_MAP = new ConcurrentHashMap<>();
   private static final Map<Integer, Float> PULSE_PROGRESS = new ConcurrentHashMap<>();
   private static final Map<Integer, Long> PULSE_LAST_RENDER_TIME = new ConcurrentHashMap<>();
   private static final Map<Integer, AuraRenderer.CachedAuraData> AURA_CACHE = new ConcurrentHashMap<>();
   private static final Map<Integer, Long> LAST_RENDER_TIME = new ConcurrentHashMap<>();
   private static VertexBuffer cachedLightningMesh;

   public static RenderType auraType(ResourceLocation texture) {
      return IrisCompat.isShaderPackInUse() ? ModRenderTypes.getCustomAuraCompat(texture) : ModRenderTypes.getCustomAura(texture);
   }

   private static RenderType auraType(ResourceLocation texture, boolean localThirdPerson) {
      return localThirdPerson ? ModRenderTypes.getCustomAuraCompat(texture) : auraType(texture);
   }

   public static RenderType lightningType(ResourceLocation texture) {
      return IrisCompat.isShaderPackInUse() ? ModRenderTypes.getCustomLightningCompat(texture) : ModRenderTypes.getCustomLightning(texture);
   }

   public static void customSetup(RenderType type, ResourceLocation texture, ShaderInstance shader) {
      if (IrisCompat.isShaderPackInUse()) {
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.enableDepthTest();
         RenderSystem.depthFunc(515);
         RenderSystem.depthMask(false);
         RenderSystem.disableCull();
         RenderSystem.setShaderTexture(0, texture);
         RenderSystem.setShader(() -> shader);
      } else {
         type.setupRenderState();
      }
   }

   public static void customClear(RenderType type) {
      if (IrisCompat.isShaderPackInUse()) {
         RenderSystem.enableDepthTest();
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.disableBlend();
      } else {
         type.clearRenderState();
      }
   }

   private static void applyAndDraw(
      VertexBuffer mesh,
      PoseStack poseStack,
      Matrix4f projectionMatrix,
      ShaderInstance shader,
      ResourceLocation texture,
      float[] color,
      float alpha,
      float speed,
      boolean ground,
      boolean ignoreSceneDepth
   ) {
      mesh.bind();
      boolean hadDepthTest = false;
      boolean hadStencilTest = false;
      if (ignoreSceneDepth) {
         hadDepthTest = GL11.glIsEnabled(2929);
         hadStencilTest = GL11.glIsEnabled(2960);
         GL11.glDisable(2929);
         GL11.glDisable(2960);
      }

      mesh.drawWithShader(poseStack.last().pose(), projectionMatrix, shader);
      if (ignoreSceneDepth) {
         if (hadDepthTest) {
            GL11.glEnable(2929);
            GL11.glDepthFunc(515);
         }

         if (hadStencilTest) {
            GL11.glEnable(2960);
         }
      }
   }

   public static void renderGuiAura(Player player, PoseStack poseStack, Matrix4f projectionMatrix, int x, int y, int scale, float partialTick, boolean guiMode) {
      StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
      if (stats != null) {
         List<AuraRenderer.AuraLayer> activeLayers = getAuraLayers(player, stats, partialTick);
         if (!activeLayers.isEmpty()) {
            ShaderInstance shader = DMZShaders.auraShader;
            if (shader != null) {
               float[] modelScale = getModelScale(stats);
               float[] auraScale = getAuraScale(stats, modelScale);
               float animSpeed = ((float)player.tickCount + partialTick) * 0.5F;
               VertexBuffer mesh = AuraMeshFactory.getBillboardQuad();
               RenderSystem.enableBlend();
               RenderSystem.defaultBlendFunc();
               RenderSystem.disableDepthTest();
               RenderSystem.depthMask(false);
               RenderSystem.disableCull();

               for (AuraRenderer.AuraLayer layer : activeLayers) {
                  float finalScaleX = auraScale[0] * (float)scale * 2.0F * (1.0F + (float)layer.layerId * 0.15F);
                  float finalScaleY = auraScale[1] * (float)scale * 2.0F * (1.0F + (float)layer.layerId * 0.15F);
                  poseStack.pushPose();
                  poseStack.translate((double)x, (double)((float)y - (finalScaleY - (float)scale * 0.45F)), 10.0);
                  poseStack.scale(finalScaleX, -finalScaleY, 1.0F);
                  String typeStr = layer.type != null && !layer.type.isEmpty() ? layer.type.toLowerCase() : "kakarot";
                  ResourceLocation mainTex = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/aura/" + typeStr + "_aura.png");
                  RenderSystem.setShaderTexture(0, mainTex);
                  RenderSystem.setShader(() -> shader);
                  shader.safeGetUniform("speed").set(animSpeed);
                  shader.safeGetUniform("ProjMat").set(projectionMatrix);
                  shader.safeGetUniform("modelMatrix").set(poseStack.last().pose());
                  shader.safeGetUniform("color1").set(layer.color[0] * 1.6F, layer.color[1] * 1.6F, layer.color[2] * 1.6F, 1.0F);
                  shader.safeGetUniform("color2").set(layer.color[0] * 1.3F, layer.color[1] * 1.3F, layer.color[2] * 1.3F, 1.0F);
                  shader.safeGetUniform("color3").set(layer.color[0] * 1.0F, layer.color[1] * 1.0F, layer.color[2] * 1.0F, 0.85F);
                  shader.safeGetUniform("color4").set(layer.color[0] * 0.75F, layer.color[1] * 0.75F, layer.color[2] * 0.75F, 0.65F);
                  shader.safeGetUniform("alp1").set(layer.alpha);
                  shader.apply();
                  mesh.bind();
                  mesh.drawWithShader(poseStack.last().pose(), projectionMatrix, shader);
                  poseStack.popPose();
               }

               VertexBuffer.unbind();
               shader.clear();
               RenderSystem.enableDepthTest();
               RenderSystem.depthMask(true);
               RenderSystem.enableCull();
               RenderSystem.disableBlend();
            }
         }
      }
   }

   public static void processFusionFlashes(Minecraft mc, long gameTime, float partialTick, PoseStack poseStack, BufferSource buffers) {
      for (Player player : mc.level.players()) {
         int playerId = player.getId();
         StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
         if (stats != null) {
            boolean isFused = stats.getStatus().isFused();
            boolean wasFused = WAS_FUSED_CACHE.getOrDefault(playerId, false);
            if (isFused && !wasFused) {
               FUSION_START_TIME.put(playerId, gameTime);
            }

            WAS_FUSED_CACHE.put(playerId, isFused);
            if (FUSION_START_TIME.containsKey(playerId)) {
               long timeSinceStart = gameTime - FUSION_START_TIME.get(playerId);
               if (timeSinceStart < 60L) {
                  List<AuraRenderer.AuraLayer> layers = getAuraLayers(player, stats, partialTick);
                  if (!layers.isEmpty()) {
                     float[] color = layers.get(layers.size() - 1).color;
                     int r = (int)(color[0] * 255.0F);
                     int g = (int)(color[1] * 255.0F);
                     int b = (int)(color[2] * 255.0F);
                     renderFusionFlash(player, (float)timeSinceStart + partialTick, poseStack, buffers, r, g, b);
                  }
               } else if (timeSinceStart > 80L) {
                  FUSION_START_TIME.remove(playerId);
               }
            }
         }
      }
   }

   public static void processThirdPersonAuras(
      Minecraft mc, PoseStack poseStack, Matrix4f projectionMatrix, Set<Integer> currentFramePlayers, boolean isFirstPerson, boolean isCameraColliding
   ) {
      for (PlayerEffectQueue.AuraRenderEntry entry : PlayerEffectQueue.getAndClearAuras()) {
         Player player = entry.player();
         boolean isLocalPlayer = player == mc.player;
         if (!isFirstPerson || isCameraColliding || !isLocalPlayer) {
            currentFramePlayers.add(player.getId());
            renderShaderAura(entry, poseStack, mc, projectionMatrix);
         }
      }
   }

   public static void processFirstPersonAuras(
      Minecraft mc, PoseStack poseStack, Matrix4f projectionMatrix, float partialTick, Set<Integer> currentFramePlayers, boolean isFirstPerson
   ) {
      for (PlayerEffectQueue.FirstPersonAuraEntry entry : PlayerEffectQueue.getAndClearFirstPersonAuras()) {
         Player player = entry.player();
         if (!currentFramePlayers.contains(player.getId())) {
            currentFramePlayers.add(player.getId());
            renderShaderFirstPersonAura(player, entry.partialTick(), poseStack, mc, projectionMatrix);
         }
      }

      Player localPlayer = mc.player;
      if (isFirstPerson && localPlayer != null && !currentFramePlayers.contains(localPlayer.getId())) {
         StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, localPlayer).orElse(null);
         if (stats != null) {
            boolean isAuraActive = stats.getStatus().isAuraActive() || stats.getStatus().isPermanentAura();
            Character character = stats.getCharacter();
            boolean hasLightning = false;
            if (character.hasActiveStackForm() && character.getActiveStackFormData() != null) {
               hasLightning = character.getActiveStackFormData().getHasLightnings();
            } else if (character.hasActiveForm() && character.getActiveFormData() != null) {
               hasLightning = character.getActiveFormData().getHasLightnings();
            }

            if (isAuraActive || hasLightning) {
               currentFramePlayers.add(localPlayer.getId());
               if (isAuraActive) {
                  renderShaderFirstPersonAura(localPlayer, partialTick, poseStack, mc, projectionMatrix);
               }

               if (hasLightning) {
                  renderSparksImpl(localPlayer, poseStack, projectionMatrix, partialTick, true);
               }
            }
         }
      }
   }

   public static void processGhostAuras(Minecraft mc, PoseStack poseStack, Matrix4f projectionMatrix, float partialTick, Set<Integer> currentFramePlayers) {
      Iterator<Entry<Integer, AuraRenderer.CachedAuraData>> it = AURA_CACHE.entrySet().iterator();

      while (it.hasNext()) {
         Entry<Integer, AuraRenderer.CachedAuraData> entry = it.next();
         int playerId = entry.getKey();
         AuraRenderer.CachedAuraData data = entry.getValue();
         if (!currentFramePlayers.contains(playerId)) {
            Entity stats = mc.level.getEntity(playerId);
            if (stats instanceof Player) {
               Player player = (Player)stats;
               if (player.isAlive()) {
                  StatsData statsx = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
                  boolean isAuraActive = statsx != null && (statsx.getStatus().isAuraActive() || statsx.getStatus().isPermanentAura());
                  if (!isAuraActive) {
                     boolean stillVisible = renderShaderGhostAura(player, data, poseStack, mc, partialTick, projectionMatrix);
                     if (!stillVisible) {
                        it.remove();
                     }
                  }
                  continue;
               }
            }

            it.remove();
         }
      }
   }

   public static void processSparks(PoseStack poseStack, Matrix4f projectionMatrix, boolean isFirstPerson) {
      List<PlayerEffectQueue.SparkRenderEntry> sparks = PlayerEffectQueue.getAndClearSparks();
      if (sparks != null && !sparks.isEmpty()) {
         for (PlayerEffectQueue.SparkRenderEntry entry : sparks) {
            if (entry != null) {
               boolean isFirstLocal = isFirstPerson && entry.player() == Minecraft.getInstance().player;
               renderSparksImpl(entry.player(), poseStack, projectionMatrix, entry.partialTick(), isFirstLocal);
            }
         }
      }
   }

   public static void cleanCaches(Set<Integer> currentFramePlayers) {
      RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
      if (!mainTarget.isStencilEnabled()) {
         mainTarget.enableStencil();
      }

      mainTarget.bindWrite(false);
      RenderSystem.stencilMask(255);
      RenderSystem.clear(1024, Minecraft.ON_OSX);
      RenderSystem.stencilMask(0);
      LAST_RENDER_TIME.keySet().removeIf(id -> !currentFramePlayers.contains(id) && !AURA_CACHE.containsKey(id));
      COLOR_PROGRESS_MAP.keySet().removeIf(id -> !currentFramePlayers.contains(id) && !AURA_CACHE.containsKey(id));
      COLOR_TICK_MAP.keySet().removeIf(id -> !currentFramePlayers.contains(id) && !AURA_CACHE.containsKey(id));
      PULSE_LAST_RENDER_TIME.keySet().removeIf(id -> !currentFramePlayers.contains(id) && !AURA_CACHE.containsKey(id));
      PULSE_PROGRESS.keySet().removeIf(id -> !currentFramePlayers.contains(id) && !AURA_CACHE.containsKey(id));
   }

   private static float[] getModelScale(StatsData stats) {
      Float[] resolved = stats.getCharacter().getResolvedModelScaling();
      return new float[]{resolved[0], resolved[1], resolved[2]};
   }

   private static float[] getBodyScale(StatsData stats) {
      float[] modelScale = getModelScale(stats);
      float sX = modelScale[0];
      float sY = modelScale[1];
      float sZ = modelScale[2];
      Character character = stats.getCharacter();
      String currentForm = character.getActiveForm() != null ? character.getActiveForm().toLowerCase() : "";
      if (currentForm.contains("ozaru")) {
         sX = Math.max(0.1F, sX - 2.8F);
         sY = Math.max(0.1F, sY - 2.8F);
         sZ = Math.max(0.1F, sZ - 2.8F);
      }

      return new float[]{sX, sY, sZ};
   }

   private static float[] getAuraScale(StatsData stats, float[] modelScale) {
      float baseScale = 1.05F;
      Character character = stats.getCharacter();
      String currentForm = character.getActiveForm() != null ? character.getActiveForm().toLowerCase() : "";
      if (character.hasActiveStackForm() && character.getActiveStackFormData() != null) {
         baseScale += 0.1F;
      }

      if (character.hasActiveForm() && character.getActiveFormData() != null) {
         baseScale += 0.1F;
      }

      if (currentForm.contains("oozaru")) {
         baseScale = 1.2F;
      }

      if (currentForm.contains("supersaiyan2") || currentForm.contains("supersaiyan3") || currentForm.contains("ultra") || currentForm.contains("superperfect")
         )
       {
         baseScale += 0.2F;
      }

      return new float[]{baseScale * modelScale[0], baseScale * modelScale[1], baseScale * modelScale[2]};
   }

   private static List<AuraRenderer.AuraLayer> getAuraLayers(Player player, StatsData stats, float partialTick) {
      Character character = stats.getCharacter();
      int entityId = player.getId();
      FormConfig.FormData nextForm = null;
      boolean chargingNormal = false;
      boolean chargingStack = false;
      if (stats.getStatus().isActionCharging()) {
         if (stats.getStatus().getSelectedAction() == ActionMode.STACK) {
            nextForm = TransformationsHelper.getNextAvailableStackForm(stats);
            chargingStack = nextForm != null;
         } else if (stats.getStatus().getSelectedAction() == ActionMode.FORM && !character.hasActiveStackForm()) {
            nextForm = TransformationsHelper.getNextAvailableForm(stats);
            chargingNormal = nextForm != null;
         }
      }

      float chargeProgress = 0.0F;
      if (!chargingNormal && !chargingStack) {
         COLOR_PROGRESS_MAP.put(entityId, 0.0F);
      } else {
         int mastery;
         if (chargingStack) {
            String mGroup = character.hasActiveStackForm() ? character.getActiveStackFormGroup() : character.getSelectedStackFormGroup();
            mastery = (int)character.getStackFormMasteries().getMastery(mGroup, nextForm.getName());
         } else {
            String mGroup = character.hasActiveForm() ? character.getActiveFormGroup() : character.getSelectedFormGroup();
            mastery = (int)character.getFormMasteries().getMastery(mGroup, nextForm.getName());
         }

         float ratePerTick = (float)(5 + Math.min(20, (int)((double)mastery * 0.2))) / 2000.0F;
         float lastProgress = COLOR_PROGRESS_MAP.getOrDefault(entityId, 0.0F);
         long lastTick = COLOR_TICK_MAP.getOrDefault(entityId, 0L);
         long currentTick = (long)player.tickCount;
         if (currentTick != lastTick) {
            long ticksElapsed = lastTick == 0L ? 1L : Math.max(1L, currentTick - lastTick);
            lastProgress = Math.min(1.0F, lastProgress + ratePerTick * (float)ticksElapsed);
            COLOR_TICK_MAP.put(entityId, currentTick);
            COLOR_PROGRESS_MAP.put(entityId, lastProgress);
         }

         chargeProgress = Math.max(0.0F, Math.min(1.0F, lastProgress + ratePerTick * partialTick));
      }

      Map<Integer, AuraRenderer.AuraLayer> layerMap = new HashMap<>();
      String normalHex = character.getAuraColor();
      float[] normalColor = character.getRgbAuraColor();
      String normalType = ConfigManager.getRaceCharacter(character.getRace()) != null
         ? ConfigManager.getRaceCharacter(character.getRace()).getAuraType()
         : "kakarot";
      int normalLayerId = 0;
      if (character.hasActiveForm() && character.getActiveFormData() != null) {
         FormConfig.FormData fd = character.getActiveFormData();
         if (fd.getAuraColor() != null && !fd.getAuraColor().isEmpty()) {
            normalHex = fd.getAuraColor();
            normalColor = fd.getRgbAuraColor();
         }

         if (fd.getAuraType() != null && !fd.getAuraType().isEmpty()) {
            normalType = fd.getAuraType();
         }

         normalLayerId = fd.getAuraLayer() != null ? fd.getAuraLayer() : 0;
      }

      AuraRenderer.AuraLayer chargeLayer = null;
      if (chargingNormal && nextForm != null) {
         String targetHex = nextForm.getAuraColor() != null && !nextForm.getAuraColor().isEmpty() ? nextForm.getAuraColor() : normalHex;
         int targetLayer = nextForm.getAuraLayer() != null ? nextForm.getAuraLayer() : normalLayerId;
         if (targetLayer == normalLayerId) {
            normalColor = interpolateColor(normalHex, targetHex, chargeProgress);
         } else {
            String targetType = nextForm.getAuraType() != null && !nextForm.getAuraType().isEmpty() ? nextForm.getAuraType() : normalType;
            chargeLayer = new AuraRenderer.AuraLayer(targetType, targetLayer, ColorUtils.hexToRgb(targetHex), chargeProgress);
         }
      }

      putLayer(layerMap, normalLayerId, new AuraRenderer.AuraLayer(normalType, normalLayerId, normalColor));
      if (chargeLayer != null) {
         putLayer(layerMap, chargeLayer.layerId, chargeLayer);
      }

      if (character.hasActiveForm() && character.getActiveFormData() != null && character.getActiveFormData().hasExtraAura()) {
         FormConfig.FormData fdx = character.getActiveFormData();
         putShifting(layerMap, fdx.getExtraAuraLayer(), new AuraRenderer.AuraLayer(fdx.getExtraAuraType(), fdx.getExtraAuraLayer(), fdx.getRgbExtraAuraColor()));
      }

      if (character.hasActiveStackForm() && character.getActiveStackFormData() != null) {
         FormConfig.FormData fdx = character.getActiveStackFormData();
         String stackHex = fdx.getAuraColor() != null && !fdx.getAuraColor().isEmpty() ? fdx.getAuraColor() : "#FFFFFF";
         String stackType = fdx.getAuraType() != null && !fdx.getAuraType().isEmpty() ? fdx.getAuraType() : "kakarot";
         int stackLayerId = fdx.getAuraLayer() != null ? fdx.getAuraLayer() : 1;
         float[] stackColor = fdx.getAuraColor() != null && !fdx.getAuraColor().isEmpty() ? fdx.getRgbAuraColor() : ColorUtils.hexToRgb(stackHex);
         if (chargingStack && nextForm != null) {
            String targetHex = nextForm.getAuraColor() != null && !nextForm.getAuraColor().isEmpty() ? nextForm.getAuraColor() : stackHex;
            stackColor = interpolateColor(stackHex, targetHex, chargeProgress);
         }

         putLayer(layerMap, stackLayerId, new AuraRenderer.AuraLayer(stackType, stackLayerId, stackColor));
         if (fdx.hasExtraAura()) {
            putShifting(
               layerMap, fdx.getExtraAuraLayer(), new AuraRenderer.AuraLayer(fdx.getExtraAuraType(), fdx.getExtraAuraLayer(), fdx.getRgbExtraAuraColor())
            );
         }
      } else if (chargingStack && nextForm != null) {
         String targetHex = nextForm.getAuraColor() != null && !nextForm.getAuraColor().isEmpty() ? nextForm.getAuraColor() : "#FFFFFF";
         String stackTypex = nextForm.getAuraType() != null && !nextForm.getAuraType().isEmpty() ? nextForm.getAuraType() : "kakarot";
         int stackLayerIdx = nextForm.getAuraLayer() != null ? nextForm.getAuraLayer() : 1;
         if (stackLayerIdx == normalLayerId) {
            AuraRenderer.AuraLayer base = layerMap.get(normalLayerId);
            if (base != null) {
               base.color = interpolateColor(normalHex, targetHex, chargeProgress);
            }
         } else {
            putLayer(layerMap, stackLayerIdx, new AuraRenderer.AuraLayer(stackTypex, stackLayerIdx, ColorUtils.hexToRgb(targetHex), chargeProgress));
         }
      }

      List<AuraRenderer.AuraLayer> activeLayers = new ArrayList<>(layerMap.values());
      activeLayers.sort(Comparator.comparingInt(l -> l.layerId));
      return activeLayers;
   }

   private static void putLayer(Map<Integer, AuraRenderer.AuraLayer> layerMap, int layerId, AuraRenderer.AuraLayer layer) {
      if (layerId >= 0) {
         layerId = Mth.clamp(layerId, 0, 6);
         layer.layerId = layerId;
         layerMap.put(layerId, layer);
      }
   }

   private static void putShifting(Map<Integer, AuraRenderer.AuraLayer> layerMap, int layerId, AuraRenderer.AuraLayer layer) {
      if (layerId >= 0) {
         layerId = Mth.clamp(layerId, 0, 6);

         while (layerMap.containsKey(layerId) && layerId < 6) {
            layerId++;
         }

         layer.layerId = layerId;
         layerMap.put(layerId, layer);
      }
   }

   private static float[] interpolateColor(String hexFrom, String hexTo, float factor) {
      float[] rgbFrom = ColorUtils.hexToRgb(hexFrom);
      float[] rgbTo = ColorUtils.hexToRgb(hexTo);
      float r = Mth.lerp(factor, rgbFrom[0], rgbTo[0]);
      float g = Mth.lerp(factor, rgbFrom[1], rgbTo[1]);
      float b = Mth.lerp(factor, rgbFrom[2], rgbTo[2]);
      return new float[]{r, g, b};
   }

   private static PoseStack shaderpackViewStack(Minecraft mc) {
      PoseStack stack = new PoseStack();
      Camera cam = mc.gameRenderer.getMainCamera();
      stack.mulPose(Axis.XP.rotationDegrees(cam.getXRot()));
      stack.mulPose(Axis.YP.rotationDegrees(cam.getYRot() + 180.0F));
      return stack;
   }

   public static void renderShaderFirstPersonAura(Player player, float partialTick, PoseStack poseStack, Minecraft mc, Matrix4f projectionMatrix) {
      StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
      if (stats != null) {
         int playerId = player.getId();
         AuraRenderer.CachedAuraData data = AURA_CACHE.computeIfAbsent(playerId, k -> new AuraRenderer.CachedAuraData());
         long gameTime = player.level().getGameTime();
         if (gameTime - LAST_RENDER_TIME.getOrDefault(playerId, 0L) > 2L) {
            data.alphaProgress = 0.0F;
         }

         LAST_RENDER_TIME.put(playerId, gameTime);
         if (data.alphaProgress < 1.0F) {
            data.alphaProgress += 0.005F;
            if (data.alphaProgress > 1.0F) {
               data.alphaProgress = 1.0F;
            }
         }

         float[] modelScale = getModelScale(stats);
         float[] body = getBodyScale(stats);
         float[] auraScale = getAuraScale(stats, modelScale);
         data.modelScaleX = modelScale[0];
         data.modelScaleY = modelScale[1];
         data.modelScaleZ = modelScale[2];
         data.bodyScaleX = body[0];
         data.bodyScaleY = body[1];
         data.bodyScaleZ = body[2];
         data.auraScaleX = auraScale[0];
         data.auraScaleY = auraScale[1];
         data.auraScaleZ = auraScale[2];
         List<AuraRenderer.AuraLayer> activeLayers = getAuraLayers(player, stats, partialTick);
         if (!activeLayers.isEmpty()) {
            data.lastLayers = activeLayers;
            if (IrisCompat.isShaderPackInUse()) {
               poseStack = shaderpackViewStack(mc);
            }

            Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
            double lerpX = Mth.lerp((double)partialTick, player.xo, player.getX());
            double lerpY = Mth.lerp((double)partialTick, player.yo, player.getY());
            double lerpZ = Mth.lerp((double)partialTick, player.zo, player.getZ());
            if (player.onGround()) {
               AuraRenderer.AuraLayer topLayer = activeLayers.get(activeLayers.size() - 1);
               poseStack.pushPose();
               poseStack.translate(lerpX - cameraPos.x, lerpY - cameraPos.y + 0.05, lerpZ - cameraPos.z);
               renderShaderPulseAura(player, data, topLayer, poseStack, mc, projectionMatrix, partialTick, data.alphaProgress);
               poseStack.popPose();
            }

            for (AuraRenderer.AuraLayer layer : activeLayers) {
               poseStack.pushPose();
               poseStack.translate(lerpX - cameraPos.x, lerpY - cameraPos.y, lerpZ - cameraPos.z);
               executeAuraShaderDraw(player, data, layer, poseStack, mc, projectionMatrix, partialTick, data.alphaProgress, true);
               poseStack.popPose();
            }
         }
      }
   }

   private static void renderShaderAura(PlayerEffectQueue.AuraRenderEntry entry, PoseStack poseStack, Minecraft mc, Matrix4f projectionMatrix) {
      AbstractClientPlayer player = entry.player();
      StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
      if (stats != null) {
         int playerId = player.getId();
         AuraRenderer.CachedAuraData data = AURA_CACHE.computeIfAbsent(playerId, k -> new AuraRenderer.CachedAuraData());
         long gameTime = player.level().getGameTime();
         if (gameTime - LAST_RENDER_TIME.getOrDefault(playerId, 0L) > 2L) {
            data.alphaProgress = 0.0F;
         }

         LAST_RENDER_TIME.put(playerId, gameTime);
         if (data.alphaProgress < 1.0F) {
            data.alphaProgress += 0.005F;
            if (data.alphaProgress > 1.0F) {
               data.alphaProgress = 1.0F;
            }
         }

         float[] modelScale = getModelScale(stats);
         float[] body = getBodyScale(stats);
         float[] auraScale = getAuraScale(stats, modelScale);
         data.modelScaleX = modelScale[0];
         data.modelScaleY = modelScale[1];
         data.modelScaleZ = modelScale[2];
         data.bodyScaleX = body[0];
         data.bodyScaleY = body[1];
         data.bodyScaleZ = body[2];
         data.auraScaleX = auraScale[0];
         data.auraScaleY = auraScale[1];
         data.auraScaleZ = auraScale[2];
         data.playerModel = entry.playerModel();
         List<AuraRenderer.AuraLayer> activeLayers = getAuraLayers(player, stats, entry.partialTick());
         if (!activeLayers.isEmpty()) {
            data.lastLayers = activeLayers;
            if (IrisCompat.isShaderPackInUse()) {
               poseStack = shaderpackViewStack(mc);
            }

            Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
            double lerpX = Mth.lerp((double)entry.partialTick(), player.xo, player.getX());
            double lerpY = Mth.lerp((double)entry.partialTick(), player.yo, player.getY());
            double lerpZ = Mth.lerp((double)entry.partialTick(), player.zo, player.getZ());
            if (player.onGround()) {
               AuraRenderer.AuraLayer topLayer = activeLayers.get(activeLayers.size() - 1);
               poseStack.pushPose();
               poseStack.translate(lerpX - cameraPos.x, lerpY - cameraPos.y + 0.05, lerpZ - cameraPos.z);
               renderShaderPulseAura(player, data, topLayer, poseStack, mc, projectionMatrix, entry.partialTick(), data.alphaProgress);
               poseStack.popPose();
            }

            for (AuraRenderer.AuraLayer layer : activeLayers) {
               poseStack.pushPose();
               poseStack.translate(lerpX - cameraPos.x, lerpY - cameraPos.y, lerpZ - cameraPos.z);
               executeAuraShaderDraw(player, data, layer, poseStack, mc, projectionMatrix, entry.partialTick(), data.alphaProgress, false);
               poseStack.popPose();
            }
         }
      }
   }

   private static boolean renderShaderGhostAura(
      Player player, AuraRenderer.CachedAuraData data, PoseStack poseStack, Minecraft mc, float partialTick, Matrix4f projectionMatrix
   ) {
      if (data.alphaProgress > 0.0F) {
         data.alphaProgress -= 0.005F;
         if (data.alphaProgress < 0.0F) {
            data.alphaProgress = 0.0F;
         }
      }

      if (data.alphaProgress <= 0.001F) {
         return false;
      } else {
         StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
         if (stats == null) {
            return false;
         } else {
            List<AuraRenderer.AuraLayer> activeLayers = data.lastLayers;
            if (activeLayers != null && !activeLayers.isEmpty()) {
               if (IrisCompat.isShaderPackInUse()) {
                  poseStack = shaderpackViewStack(mc);
               }

               Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
               double lerpX = Mth.lerp((double)partialTick, player.xo, player.getX());
               double lerpY = Mth.lerp((double)partialTick, player.yo, player.getY());
               double lerpZ = Mth.lerp((double)partialTick, player.zo, player.getZ());
               if (player.onGround()) {
                  AuraRenderer.AuraLayer topLayer = activeLayers.get(activeLayers.size() - 1);
                  poseStack.pushPose();
                  poseStack.translate(lerpX - cameraPos.x, lerpY - cameraPos.y + 0.05, lerpZ - cameraPos.z);
                  renderShaderPulseAura(player, data, topLayer, poseStack, mc, projectionMatrix, partialTick, data.alphaProgress);
                  poseStack.popPose();
               }

               boolean isLocalPlayer = player == mc.player;
               boolean isFirstPerson = isLocalPlayer && mc.options.getCameraType().isFirstPerson();

               for (AuraRenderer.AuraLayer layer : activeLayers) {
                  poseStack.pushPose();
                  poseStack.translate(lerpX - cameraPos.x, lerpY - cameraPos.y, lerpZ - cameraPos.z);
                  executeAuraShaderDraw(player, data, layer, poseStack, mc, projectionMatrix, partialTick, data.alphaProgress, isFirstPerson);
                  poseStack.popPose();
               }

               return true;
            } else {
               return false;
            }
         }
      }
   }

   private static void executeAuraShaderDraw(
      Player player,
      AuraRenderer.CachedAuraData data,
      AuraRenderer.AuraLayer layer,
      PoseStack poseStack,
      Minecraft mc,
      Matrix4f projectionMatrix,
      float partialTick,
      float alphaMultiplier,
      boolean isFirstPerson
   ) {
      ShaderInstance shader = DMZShaders.auraShader;
      if (shader != null && !(alphaMultiplier <= 0.001F)) {
         boolean isLocalPlayer = player == mc.player;
         boolean localThirdPerson = isLocalPlayer && !isFirstPerson;
         float maxAlpha = isLocalPlayer && isFirstPerson ? 0.5F : 1.0F;
         float finalAlpha = maxAlpha * alphaMultiplier * layer.alpha;
         String typeStr = layer.type != null && !layer.type.isEmpty() ? layer.type.toLowerCase() : "kakarot";
         ResourceLocation mainTex = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/aura/" + typeStr + "_aura.png");
         ResourceLocation crossTex = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/aura/" + typeStr + "_cross.png");
         ResourceLocation sparkingTex = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/aura/sparking_effects.png");
         float animSpeed = ((float)player.tickCount + partialTick) * 0.5F;
         shader.safeGetUniform("speed").set(animSpeed);
         shader.safeGetUniform("ProjMat").set(projectionMatrix);
         shader.safeGetUniform("color1").set(layer.color[0] * 1.6F, layer.color[1] * 1.6F, layer.color[2] * 1.6F, 1.0F);
         shader.safeGetUniform("color2").set(layer.color[0] * 1.3F, layer.color[1] * 1.3F, layer.color[2] * 1.3F, 1.0F);
         shader.safeGetUniform("color3").set(layer.color[0] * 1.0F, layer.color[1] * 1.0F, layer.color[2] * 1.0F, 0.85F);
         shader.safeGetUniform("color4").set(layer.color[0] * 0.75F, layer.color[1] * 0.75F, layer.color[2] * 0.75F, 0.65F);
         float baseMultiplier = 2.2F;
         float finalScaleX = data.auraScaleX * baseMultiplier * (1.0F + (float)layer.layerId * 0.15F);
         float finalScaleY = data.auraScaleY * baseMultiplier * (1.0F + (float)layer.layerId * 0.15F);
         float finalScaleZ = data.auraScaleZ * baseMultiplier * (1.0F + (float)layer.layerId * 0.15F);
         VertexBuffer mesh = AuraMeshFactory.getBillboardQuad();
         if (isLocalPlayer && isFirstPerson) {
            poseStack.pushPose();
            poseStack.last().pose().identity();
            poseStack.last().normal().identity();
            poseStack.translate(0.0, -0.6, -0.7);
            float normalizedScaleX = finalScaleX / (data.modelScaleX > 0.0F ? data.modelScaleX : 1.0F);
            float normalizedScaleY = finalScaleY / (data.modelScaleY > 0.0F ? data.modelScaleY : 1.0F);
            poseStack.scale(normalizedScaleX * 3.0F, normalizedScaleY * 3.0F, 1.0F);
            shader.safeGetUniform("alp1").set(finalAlpha * 0.45F);
            shader.safeGetUniform("modelMatrix").set(poseStack.last().pose());
            RenderType mainRender = auraType(mainTex, localThirdPerson);
            customSetup(mainRender, mainTex, shader);
            applyAndDraw(mesh, poseStack, projectionMatrix, shader, mainTex, layer.color, finalAlpha * 0.45F, animSpeed, false, false);
            customClear(mainRender);
            RenderType sparkingRender = auraType(sparkingTex, localThirdPerson);
            customSetup(sparkingRender, sparkingTex, shader);
            poseStack.scale(0.6F, 0.45F, 0.6F);
            shader.safeGetUniform("modelMatrix").set(poseStack.last().pose());
            applyAndDraw(mesh, poseStack, projectionMatrix, shader, sparkingTex, layer.color, finalAlpha * 0.45F, animSpeed, false, false);
            customClear(sparkingRender);
            poseStack.popPose();
            VertexBuffer.unbind();
            shader.clear();
         } else {
            float cameraPitch = mc.gameRenderer.getMainCamera().getXRot();
            float absPitch = Math.abs(cameraPitch);
            float crossFactor = 0.0F;
            float pitchSquash = 1.0F;
            if (absPitch > 45.0F && !isFirstPerson) {
               crossFactor = (float)Math.pow((double)((absPitch - 45.0F) / 45.0F), 2.0);
               pitchSquash = 1.0F - crossFactor * 0.5F;
            }

            if (crossFactor < 1.0F) {
               poseStack.pushPose();
               poseStack.translate(0.0, 0.05, 0.0);
               poseStack.mulPose(mc.gameRenderer.getMainCamera().rotation());
               poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
               if (OverShoulderCamera.isRunning()) {
                  float shoulderLean = (float)Mth.clamp(-OverShoulderCamera.getCurrentSide() * 3.0, -6.0, 6.0);
                  poseStack.mulPose(Axis.ZP.rotationDegrees(shoulderLean));
               }

               poseStack.scale(finalScaleX, finalScaleY * pitchSquash, finalScaleZ);
               poseStack.translate(0.0, 0.7, 0.0);
               shader.safeGetUniform("alp1").set((1.0F - crossFactor) * finalAlpha);
               shader.safeGetUniform("modelMatrix").set(poseStack.last().pose());
               RenderType mainRender = auraType(mainTex, localThirdPerson);
               customSetup(mainRender, mainTex, shader);
               applyAndDraw(
                  mesh, poseStack, projectionMatrix, shader, mainTex, layer.color, (1.0F - crossFactor) * finalAlpha, animSpeed, false, localThirdPerson
               );
               customClear(mainRender);
               poseStack.pushPose();
               float sparkingPulse = 1.0F + (float)Math.sin((double)(((float)player.tickCount + partialTick) * 0.2F)) * 0.05F;
               poseStack.scale(0.8F * sparkingPulse, 0.65F * sparkingPulse, 0.8F * sparkingPulse);
               poseStack.translate(0.0, -0.25, 0.0);
               RenderType sparkingRender = auraType(sparkingTex, localThirdPerson);
               customSetup(sparkingRender, sparkingTex, shader);
               shader.safeGetUniform("alp1").set((1.0F - crossFactor) * finalAlpha * 0.8F);
               shader.safeGetUniform("modelMatrix").set(poseStack.last().pose());
               applyAndDraw(
                  mesh,
                  poseStack,
                  projectionMatrix,
                  shader,
                  sparkingTex,
                  layer.color,
                  (1.0F - crossFactor) * finalAlpha * 0.8F,
                  animSpeed,
                  false,
                  localThirdPerson
               );
               customClear(sparkingRender);
               poseStack.popPose();
               poseStack.popPose();
            }

            if (crossFactor > 0.0F) {
               poseStack.pushPose();
               poseStack.translate(0.0, 0.05, 0.0);
               poseStack.mulPose(Axis.YP.rotationDegrees(-mc.gameRenderer.getMainCamera().getYRot()));
               if (cameraPitch < 0.0F) {
                  poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
               }

               poseStack.scale(finalScaleX, 1.0F, finalScaleZ);
               shader.safeGetUniform("alp1").set(crossFactor * finalAlpha);
               shader.safeGetUniform("modelMatrix").set(poseStack.last().pose());
               RenderType crossRender = auraType(crossTex, localThirdPerson);
               customSetup(crossRender, crossTex, shader);
               VertexBuffer groundMesh = AuraMeshFactory.getGroundQuad();
               applyAndDraw(groundMesh, poseStack, projectionMatrix, shader, crossTex, layer.color, crossFactor * finalAlpha, animSpeed, true, localThirdPerson);
               customClear(crossRender);
               poseStack.popPose();
            }

            VertexBuffer.unbind();
            shader.clear();
         }
      }
   }

   private static void renderShaderPulseAura(
      Player player,
      AuraRenderer.CachedAuraData data,
      AuraRenderer.AuraLayer topLayer,
      PoseStack poseStack,
      Minecraft mc,
      Matrix4f projectionMatrix,
      float partialTick,
      float alphaMultiplier
   ) {
      int playerId = player.getId();
      long gameTime = player.level().getGameTime();
      if (gameTime - PULSE_LAST_RENDER_TIME.getOrDefault(playerId, 0L) > 2L) {
         PULSE_PROGRESS.put(playerId, 0.0F);
      }

      PULSE_LAST_RENDER_TIME.put(playerId, gameTime);
      float currentProgress = PULSE_PROGRESS.getOrDefault(playerId, 0.0F);
      if (!mc.isPaused()) {
         currentProgress += 0.01F;
         if (currentProgress >= 1.0F) {
            currentProgress--;
         }

         PULSE_PROGRESS.put(playerId, currentProgress);
      }

      drawSinglePulseInstance(player, data, topLayer, poseStack, mc, projectionMatrix, partialTick, alphaMultiplier, currentProgress);
      float progressPhase2 = (currentProgress + 0.5F) % 1.0F;
      drawSinglePulseInstance(player, data, topLayer, poseStack, mc, projectionMatrix, partialTick, alphaMultiplier, progressPhase2);
   }

   private static void drawSinglePulseInstance(
      Player player,
      AuraRenderer.CachedAuraData data,
      AuraRenderer.AuraLayer topLayer,
      PoseStack poseStack,
      Minecraft mc,
      Matrix4f projectionMatrix,
      float partialTick,
      float alphaMultiplier,
      float progress
   ) {
      float expansion = 1.0F + 6.0F * progress;
      float alphaCurve = (float)Math.sin((double)progress * Math.PI);
      String typeStr = topLayer.type != null && !topLayer.type.isEmpty() ? topLayer.type.toLowerCase() : "kakarot";
      ResourceLocation crossTex = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/aura/" + typeStr + "_cross.png");
      ShaderInstance shader = DMZShaders.auraShader;
      if (shader != null) {
         poseStack.pushPose();
         float cameraPitch = mc.gameRenderer.getMainCamera().getXRot();
         if (cameraPitch < 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
         }

         float layerScaleBoost = 1.0F + (float)topLayer.layerId * 0.15F;
         float scaleMultiplier = 0.5F;
         float sX = data.auraScaleX * expansion * scaleMultiplier * layerScaleBoost;
         float sZ = data.auraScaleZ * expansion * scaleMultiplier * layerScaleBoost;
         poseStack.scale(sX, 1.0F, sZ);
         float animSpeed = ((float)player.tickCount + partialTick) * 0.5F;
         shader.safeGetUniform("speed").set(animSpeed);
         shader.safeGetUniform("ProjMat").set(projectionMatrix);
         shader.safeGetUniform("modelMatrix").set(poseStack.last().pose());
         shader.safeGetUniform("color1").set(topLayer.color[0] * 1.6F, topLayer.color[1] * 1.6F, topLayer.color[2] * 1.6F, 1.0F);
         shader.safeGetUniform("color2").set(topLayer.color[0] * 1.3F, topLayer.color[1] * 1.3F, topLayer.color[2] * 1.3F, 1.0F);
         shader.safeGetUniform("color3").set(topLayer.color[0] * 1.0F, topLayer.color[1] * 1.0F, topLayer.color[2] * 1.0F, 0.85F);
         shader.safeGetUniform("color4").set(topLayer.color[0] * 0.75F, topLayer.color[1] * 0.75F, topLayer.color[2] * 0.75F, 0.65F);
         shader.safeGetUniform("alp1").set(alphaCurve * 0.6F * alphaMultiplier * topLayer.alpha);
         boolean localThirdPerson = player == mc.player && !mc.options.getCameraType().isFirstPerson();
         RenderType pulseRender = auraType(crossTex, localThirdPerson);
         customSetup(pulseRender, crossTex, shader);
         VertexBuffer mesh = AuraMeshFactory.getGroundQuad();
         applyAndDraw(
            mesh, poseStack, projectionMatrix, shader, crossTex, topLayer.color, alphaCurve * 0.6F * alphaMultiplier, animSpeed, true, localThirdPerson
         );
         customClear(pulseRender);
         VertexBuffer.unbind();
         shader.clear();
         poseStack.popPose();
      }
   }

   public static VertexBuffer getLightningMesh() {
      if (cachedLightningMesh == null) {
         cachedLightningMesh = new VertexBuffer(Usage.STATIC);
         Tesselator tesselator = Tesselator.getInstance();
         BufferBuilder builder = tesselator.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_NORMAL);
         float w = 0.15F;
         float h = 3.0F;
         int segments = 20;
         float segHeight = h / (float)segments;

         for (int i = 0; i < segments; i++) {
            float y1 = (float)i * segHeight;
            float y2 = (float)(i + 1) * segHeight;
            builder.addVertex(-w, y1, 0.0F).setColor(255, 255, 255, 255).setNormal(0.0F, 0.0F, 1.0F);
            builder.addVertex(w, y1, 0.0F).setColor(255, 255, 255, 255).setNormal(0.0F, 0.0F, 1.0F);
            builder.addVertex(w, y2, 0.0F).setColor(255, 255, 255, 255).setNormal(0.0F, 0.0F, 1.0F);
            builder.addVertex(-w, y2, 0.0F).setColor(255, 255, 255, 255).setNormal(0.0F, 0.0F, 1.0F);
            builder.addVertex(0.0F, y1, -w).setColor(255, 255, 255, 255).setNormal(1.0F, 0.0F, 0.0F);
            builder.addVertex(0.0F, y1, w).setColor(255, 255, 255, 255).setNormal(1.0F, 0.0F, 0.0F);
            builder.addVertex(0.0F, y2, w).setColor(255, 255, 255, 255).setNormal(1.0F, 0.0F, 0.0F);
            builder.addVertex(0.0F, y2, -w).setColor(255, 255, 255, 255).setNormal(1.0F, 0.0F, 0.0F);
         }

         cachedLightningMesh.bind();
         cachedLightningMesh.upload(builder.buildOrThrow());
         VertexBuffer.unbind();
      }

      return cachedLightningMesh;
   }

   private static void renderSparksImpl(Player player, PoseStack poseStack, Matrix4f projectionMatrix, float partialTick, boolean isFirstPersonLocal) {
      StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
      if (stats != null) {
         Character character = stats.getCharacter();
         boolean hasLightning = false;
         String lightningColorHex = "";
         if (character.hasActiveStackForm() && character.getActiveStackFormData() != null && character.getActiveStackFormData().getHasLightnings()) {
            hasLightning = true;
            lightningColorHex = character.getActiveStackFormData().getLightningColor();
         } else if (character.hasActiveForm() && character.getActiveFormData() != null && character.getActiveFormData().getHasLightnings()) {
            hasLightning = true;
            lightningColorHex = character.getActiveFormData().getLightningColor();
         }

         if (hasLightning) {
            ShaderInstance shader = DMZShaders.lightningShader;
            if (shader != null) {
               boolean isAuraActive = stats.getStatus().isAuraActive() || stats.getStatus().isPermanentAura();
               float speedMod = isAuraActive ? 1.0F : 0.2F;
               int maxBranches = isAuraActive ? 5 : 3;
               float maxScale = isAuraActive ? 0.5F : 0.25F;
               float[] colorRgb = ColorUtils.hexToRgb(lightningColorHex);
               float time = ((float)player.tickCount + partialTick) / 20.0F;
               shader.safeGetUniform("projectionMatrix").set(projectionMatrix);
               shader.safeGetUniform("time").set(time);
               shader.safeGetUniform("speedModifier").set(speedMod);
               boolean isLocalPlayer = player == Minecraft.getInstance().player;
               boolean isFirstPerson = isLocalPlayer && Minecraft.getInstance().options.getCameraType().isFirstPerson();
               float cameraAlpha = isLocalPlayer && isFirstPerson ? 0.25F : 1.0F;
               shader.safeGetUniform("color1").set(Mth.lerp(0.8F, colorRgb[0], 1.0F), Mth.lerp(0.8F, colorRgb[1], 1.0F), Mth.lerp(0.8F, colorRgb[2], 1.0F));
               shader.safeGetUniform("color2").set(colorRgb[0], colorRgb[1], colorRgb[2]);
               shader.safeGetUniform("alp1").set(cameraAlpha);
               shader.safeGetUniform("alp2").set(0.1F * cameraAlpha);
               shader.safeGetUniform("power").set(3.0F);
               shader.safeGetUniform("divis").set(1.0F);
               ResourceLocation lightningTex = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/null.png");
               RenderType renderType = lightningType(lightningTex);
               customSetup(renderType, lightningTex, shader);
               shader.apply();
               VertexBuffer mesh = getLightningMesh();
               mesh.bind();
               poseStack.pushPose();
               if (isFirstPersonLocal) {
                  poseStack.last().pose().identity();
                  poseStack.last().normal().identity();
                  poseStack.translate(0.0, -0.65, -0.8);
               } else {
                  Minecraft mc = Minecraft.getInstance();
                  Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
                  Vec3 playerPos = new Vec3(
                     Mth.lerp((double)partialTick, player.xo, player.getX()),
                     Mth.lerp((double)partialTick, player.yo, player.getY()),
                     Mth.lerp((double)partialTick, player.zo, player.getZ())
                  );
                  playerPos = SableCompat.projectToWorld(player.level(), playerPos);
                  poseStack.translate(playerPos.x - cameraPos.x, playerPos.y - cameraPos.y, playerPos.z - cameraPos.z);
               }

               long tickInterval = isAuraActive ? 2L : 20L;
               long timeHash = player.level().getGameTime() / tickInterval;
               Random seededRand = new Random((long)player.getId() + timeHash);
               float[] bScale = getBodyScale(stats);
               float bbHeight = player.getBbHeight() * bScale[1];

               for (int i = 0; i < maxBranches; i++) {
                  poseStack.pushPose();
                  float spread = isAuraActive ? 1.8F : 1.2F;
                  float randomY = seededRand.nextFloat() * bbHeight;
                  if (isFirstPersonLocal) {
                     randomY *= 0.4F;
                     poseStack.translate(0.0, -0.15F, 0.0);
                  }

                  poseStack.translate((seededRand.nextFloat() - 0.5F) * spread, randomY, (seededRand.nextFloat() - 0.5F) * spread);
                  poseStack.mulPose(Axis.YP.rotationDegrees(seededRand.nextFloat() * 360.0F));
                  poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F + (seededRand.nextFloat() - 0.5F) * 40.0F));
                  float scale = 0.15F + seededRand.nextFloat() * maxScale;
                  poseStack.scale(scale, scale, scale);
                  shader.safeGetUniform("modelMatrix").set(poseStack.last().pose());
                  shader.safeGetUniform("normalMatrix").set(poseStack.last().normal());
                  shader.apply();
                  mesh.drawWithShader(poseStack.last().pose(), projectionMatrix, shader);
                  poseStack.popPose();
               }

               poseStack.popPose();
               VertexBuffer.unbind();
               shader.clear();
               customClear(renderType);
            }
         }
      }
   }

   private static void renderFusionFlash(Player player, float time, PoseStack poseStack, MultiBufferSource buffer, int r, int g, int b) {
      float rotationTime = time * 0.01F;
      float rawSin = Mth.sin(time * 0.1F);
      float normalizedFade = (rawSin + 1.0F) / 2.0F;
      float fade = 0.4F + normalizedFade * 0.6F;
      float intensity = 0.6F;
      RandomSource randomsource = RandomSource.create(432L);
      VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.lightning());
      poseStack.pushPose();
      Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
      poseStack.translate(player.getX() - cameraPos.x, player.getY() + 1.0 - cameraPos.y, player.getZ() - cameraPos.z);
      poseStack.scale(1.0F, 1.0F, 1.0F);

      for (int i = 0; (float)i < (intensity + intensity * intensity) / 2.0F * 60.0F; i++) {
         poseStack.mulPose(Axis.XP.rotationDegrees(randomsource.nextFloat() * 360.0F));
         poseStack.mulPose(Axis.YP.rotationDegrees(randomsource.nextFloat() * 360.0F));
         poseStack.mulPose(Axis.ZP.rotationDegrees(randomsource.nextFloat() * 360.0F));
         poseStack.mulPose(Axis.XP.rotationDegrees(randomsource.nextFloat() * 360.0F));
         poseStack.mulPose(Axis.YP.rotationDegrees(randomsource.nextFloat() * 360.0F));
         poseStack.mulPose(Axis.ZP.rotationDegrees(randomsource.nextFloat() * 360.0F + rotationTime * 90.0F));
         float width = randomsource.nextFloat() * 5.0F + 4.0F;
         float length = randomsource.nextFloat() + 0.5F;
         Matrix4f matrix4f = poseStack.last().pose();
         int alpha = (int)(255.0F * fade);
         vertex01(vertexconsumer, matrix4f, alpha, r, g, b);
         vertex2(vertexconsumer, matrix4f, width, length, r, g, b, alpha);
         vertex3(vertexconsumer, matrix4f, width, length, r, g, b, alpha);
         vertex01(vertexconsumer, matrix4f, alpha, r, g, b);
         vertex3(vertexconsumer, matrix4f, width, length, r, g, b, alpha);
         vertex4(vertexconsumer, matrix4f, width, length, r, g, b, alpha);
         vertex01(vertexconsumer, matrix4f, alpha, r, g, b);
         vertex4(vertexconsumer, matrix4f, width, length, r, g, b, alpha);
         vertex2(vertexconsumer, matrix4f, width, length, r, g, b, alpha);
      }

      poseStack.popPose();
   }

   private static void vertex01(VertexConsumer pConsumer, Matrix4f pMatrix, int pAlpha, int r, int g, int b) {
      pConsumer.addVertex(pMatrix, 0.0F, 0.0F, 0.0F).setColor(r, g, b, pAlpha);
   }

   private static void vertex2(VertexConsumer pConsumer, Matrix4f pMatrix, float pWidth, float pLength, int r, int g, int b, int alpha) {
      pConsumer.addVertex(pMatrix, -HALF_SQRT_3 * pLength, pWidth, -0.5F * pLength).setColor(r, g, b, alpha);
   }

   private static void vertex3(VertexConsumer pConsumer, Matrix4f pMatrix, float pWidth, float pLength, int r, int g, int b, int alpha) {
      pConsumer.addVertex(pMatrix, HALF_SQRT_3 * pLength, pWidth, -0.5F * pLength).setColor(r, g, b, alpha);
   }

   private static void vertex4(VertexConsumer pConsumer, Matrix4f pMatrix, float pWidth, float pLength, int r, int g, int b, int alpha) {
      pConsumer.addVertex(pMatrix, 0.0F, pWidth, pLength).setColor(r, g, b, alpha);
   }

   public static class AuraLayer {
      public String type;
      public int layerId;
      public float[] color;
      public float alpha;

      public AuraLayer(String type, int layerId, float[] color) {
         this(type, layerId, color, 1.0F);
      }

      public AuraLayer(String type, int layerId, float[] color, float alpha) {
         this.type = type;
         this.layerId = layerId;
         this.color = color;
         this.alpha = alpha;
      }
   }

   private static class CachedAuraData {
      float auraScaleX;
      float auraScaleY;
      float auraScaleZ;
      float bodyScaleX;
      float bodyScaleY;
      float bodyScaleZ;
      float modelScaleX;
      float modelScaleY;
      float modelScaleZ;
      float alphaProgress;
      BakedGeoModel playerModel;
      List<AuraRenderer.AuraLayer> lastLayers;
   }
}
