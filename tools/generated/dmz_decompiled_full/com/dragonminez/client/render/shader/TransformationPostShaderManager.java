package com.dragonminez.client.render.shader;

import com.dragonminez.client.render.util.IrisCompat;
import com.dragonminez.client.render.util.ModRenderTypes;
import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.mixin.client.PostChainAccessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public final class TransformationPostShaderManager {
   private static final ResourceLocation TRANSFORMATION_EFFECT = ResourceLocation.fromNamespaceAndPath(
      "dragonminez", "shaders/post/transformation_outline.json"
   );
   private static final String UNPACK_PASS_NAME = "dragonminez:transformation_unpack";
   private static final String BLUR_H_PASS_NAME = "dragonminez:transformation_blur_h";
   private static final String BLUR_V_PASS_NAME = "dragonminez:transformation_blur_v";
   private static final String COMPOSITE_PASS_NAME = "dragonminez:transformation_composite";
   private static final String MASK_TARGET = "entity_mask";
   private static final float FIXED_GLOW_STRENGTH = 1.35F;
   private static final float FIXED_BLOOM_STRENGTH = 0.95F;
   private static final Map<UUID, TransformationPostShaderManager.TrackedShaderState> TRACKED_PLAYERS = new HashMap<>();
   private static final Set<UUID> ACTIVE_MASK_PLAYERS = new HashSet<>();
   private static boolean loadedByManager = false;
   @Nullable
   private static TransformationPostShaderManager.ShaderUniformState activeUniformState;
   private static TransformationMaskBufferSource maskBufferSource = new TransformationMaskBufferSource();
   @Nullable
   private static PostChain shaderpackChain;
   private static int shaderpackChainWidth = -1;
   private static int shaderpackChainHeight = -1;
   private static volatile boolean shaderpackMainPass = false;

   public static void setShaderpackMainPass(boolean active) {
      shaderpackMainPass = active;
   }

   public static boolean isShaderpackMainPass() {
      return shaderpackMainPass;
   }

   private TransformationPostShaderManager() {
   }

   public static void tick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         updateTrackedPlayers(mc);
         if (IrisCompat.isShaderPackInUse()) {
            shutdownManagedShader(mc);
         } else if (!ACTIVE_MASK_PLAYERS.isEmpty() && activeUniformState != null && ModRenderTypes.hasTransformationMaskShader()) {
            ensureShaderLoaded(mc);
         } else {
            shutdownManagedShader(mc);
         }
      } else {
         clearState(mc, true);
      }
   }

   public static TransformationMaskBufferSource getMaskBufferSource() {
      return maskBufferSource;
   }

   @Nullable
   public static TransformationPostShaderManager.MaskData getEntityMaskData(Player player) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null && player != null) {
         UUID playerId = player.getUUID();
         if (!ACTIVE_MASK_PLAYERS.contains(playerId)) {
            return null;
         } else if (!ModRenderTypes.hasTransformationMaskShader()) {
            return null;
         } else if (!IrisCompat.isShaderPackInUse() && !isTransformationShaderActive(mc)) {
            return null;
         } else if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
            return null;
         } else {
            TransformationPostShaderManager.TrackedShaderState tracked = TRACKED_PLAYERS.get(playerId);
            if (tracked != null && tracked.uniformState != null) {
               TransformationPostShaderManager.ShaderUniformState uniforms = tracked.uniformState;
               return new TransformationPostShaderManager.MaskData(
                  uniforms.primaryR(), uniforms.primaryG(), uniforms.primaryB(), uniforms.secondaryR(), uniforms.secondaryG(), uniforms.secondaryB()
               );
            } else {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   public static void flushMaskAndApplyUniforms(float partialTicks, PoseStack poseStack, Camera camera, Frustum frustum) {
      if (activeUniformState != null && !ACTIVE_MASK_PLAYERS.isEmpty()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && mc.level != null && mc.gameRenderer != null && mc.levelRenderer != null) {
            PostChain postChain = mc.gameRenderer.currentEffect();
            if (postChain != null && TRANSFORMATION_EFFECT.toString().equals(postChain.getName())) {
               RenderTarget maskTarget = postChain.getTempTarget("entity_mask");
               if (maskTarget != null) {
                  maskTarget.clear(Minecraft.ON_OSX);
                  maskTarget.copyDepthFrom(mc.getMainRenderTarget());
                  TransformationMaskRenderState.setCurrentTargets(maskTarget);

                  try {
                     maskBufferSource.endMaskBatch();
                  } finally {
                     TransformationMaskRenderState.setCurrentTargets(null);
                     mc.getMainRenderTarget().bindWrite(false);
                  }

                  applyRuntimeUniforms(postChain, partialTicks, mc);
               }
            }
         }
      }
   }

   public static void processShaderpackOutline(float partialTicks) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null && mc.gameRenderer != null) {
         if (!ACTIVE_MASK_PLAYERS.isEmpty() && activeUniformState != null && ModRenderTypes.hasTransformationMaskShader()) {
            RenderTarget main = mc.getMainRenderTarget();
            PostChain chain = ensureShaderpackChain(mc, main);
            if (chain != null) {
               RenderTarget maskTarget = chain.getTempTarget("entity_mask");
               if (maskTarget != null) {
                  maskTarget.clear(Minecraft.ON_OSX);
                  maskTarget.copyDepthFrom(main);
                  TransformationMaskRenderState.setCurrentTargets(maskTarget);

                  try {
                     maskBufferSource.endMaskBatch();
                  } finally {
                     TransformationMaskRenderState.setCurrentTargets(null);
                     main.bindWrite(false);
                  }

                  applyRuntimeUniforms(chain, partialTicks, mc);
                  chain.process(partialTicks);
                  main.bindWrite(false);
               }
            }
         }
      }
   }

   @Nullable
   private static PostChain ensureShaderpackChain(Minecraft mc, RenderTarget main) {
      if (shaderpackChain != null && shaderpackChainWidth == main.width && shaderpackChainHeight == main.height) {
         return shaderpackChain;
      } else {
         try {
            if (shaderpackChain != null) {
               shaderpackChain.close();
            }

            shaderpackChain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, TRANSFORMATION_EFFECT);
            shaderpackChain.resize(main.width, main.height);
            shaderpackChainWidth = main.width;
            shaderpackChainHeight = main.height;
         } catch (Exception var3) {
            shaderpackChain = null;
            shaderpackChainWidth = -1;
            shaderpackChainHeight = -1;
         }

         return shaderpackChain;
      }
   }

   public static void reset() {
      clearState(Minecraft.getInstance(), true);
   }

   private static void updateTrackedPlayers(Minecraft mc) {
      ACTIVE_MASK_PLAYERS.clear();
      activeUniformState = null;
      if (!ConfigManager.getUserConfig().getTransformationOutlines()) {
         TRACKED_PLAYERS.clear();
      } else {
         long frameId = mc.level.getGameTime();
         UUID localPlayerId = mc.player.getUUID();
         boolean localFirstPerson = mc.options.getCameraType().isFirstPerson();
         TransformationPostShaderManager.ShaderUniformState fallbackUniform = null;

         for (Player player : mc.level.players()) {
            TransformationPostShaderManager.ResolvedShaderConfig resolvedConfig = resolveActiveShaderConfig(player);
            UUID playerId = player.getUUID();
            if (resolvedConfig == null) {
               TRACKED_PLAYERS.remove(playerId);
            } else {
               TransformationPostShaderManager.TrackedShaderState tracked = TRACKED_PLAYERS.get(playerId);
               if (tracked == null) {
                  tracked = new TransformationPostShaderManager.TrackedShaderState();
                  TRACKED_PLAYERS.put(playerId, tracked);
               }

               if (!resolvedConfig.signature().equals(tracked.signature)) {
                  tracked.signature = resolvedConfig.signature();
               }

               tracked.uniformState = resolvedConfig.uniformState();
               tracked.lastSeenFrame = frameId;
               if (!playerId.equals(localPlayerId) || !localFirstPerson) {
                  ACTIVE_MASK_PLAYERS.add(playerId);
                  if (fallbackUniform == null) {
                     fallbackUniform = tracked.uniformState;
                  }

                  if (playerId.equals(localPlayerId)) {
                     activeUniformState = tracked.uniformState;
                  }
               }
            }
         }

         TRACKED_PLAYERS.entrySet().removeIf(entry -> entry.getValue().lastSeenFrame != frameId);
         if (activeUniformState == null) {
            activeUniformState = fallbackUniform;
         }
      }
   }

   private static void applyRuntimeUniforms(PostChain postChain, float partialTicks, Minecraft mc) {
      if (activeUniformState != null) {
         float animationTime = mc.level != null ? ((float)mc.level.getGameTime() + partialTicks) / 20.0F : partialTicks / 20.0F;

         for (PostPass pass : ((PostChainAccessor)postChain).dragonminez$getPasses()) {
            EffectInstance effect = pass.getEffect();
            String passName = pass.getName();
            if ("dragonminez:transformation_unpack".equals(passName)) {
               applyUniform(effect, "AnimationTime", animationTime);
            } else if ("dragonminez:transformation_blur_h".equals(passName) || "dragonminez:transformation_blur_v".equals(passName)) {
               applyUniform(effect, "BloomRadius", outlineThicknessToBlurRadius(activeUniformState.outlineThickness()));
            } else if ("dragonminez:transformation_composite".equals(passName)) {
               applyUniform(effect, "BloomStrength", 0.95F);
               applyUniform(effect, "GlowStrength", 1.35F);
            }
         }
      }
   }

   private static void ensureShaderLoaded(Minecraft mc) {
      if (mc.gameRenderer != null) {
         if (isTransformationShaderActive(mc)) {
            loadedByManager = true;
         } else {
            mc.gameRenderer.loadEffect(TRANSFORMATION_EFFECT);
            loadedByManager = isTransformationShaderActive(mc);
         }
      }
   }

   private static void shutdownManagedShader(Minecraft mc) {
      if (mc.gameRenderer == null) {
         loadedByManager = false;
      } else {
         if ((loadedByManager || isTransformationShaderActive(mc)) && isTransformationShaderActive(mc)) {
            mc.gameRenderer.shutdownEffect();
         }

         loadedByManager = false;
      }
   }

   private static boolean isTransformationShaderActive(Minecraft mc) {
      if (mc.gameRenderer == null) {
         return false;
      } else {
         PostChain current = mc.gameRenderer.currentEffect();
         return current != null && TRANSFORMATION_EFFECT.toString().equals(current.getName());
      }
   }

   private static void clearState(Minecraft mc, boolean shutdownShader) {
      TRACKED_PLAYERS.clear();
      ACTIVE_MASK_PLAYERS.clear();
      activeUniformState = null;
      maskBufferSource = new TransformationMaskBufferSource();
      TransformationMaskRenderState.setCurrentTargets(null);
      if (shaderpackChain != null) {
         shaderpackChain.close();
         shaderpackChain = null;
         shaderpackChainWidth = -1;
         shaderpackChainHeight = -1;
      }

      if (shutdownShader) {
         shutdownManagedShader(mc);
      }
   }

   @Nullable
   private static TransformationPostShaderManager.ResolvedShaderConfig resolveActiveShaderConfig(Player player) {
      StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
      if (data == null) {
         return null;
      } else {
         Character character = data.getCharacter();
         if (character == null) {
            return null;
         } else {
            FormConfig.FormData activeFormData = character.getActiveFormData();
            FormConfig.FormData activeStackFormData = character.getActiveStackFormData();
            FormConfig.FormData.OutlineShaderConfig selectedConfig = null;
            String source = "";
            if (activeFormData != null) {
               FormConfig.FormData.OutlineShaderConfig formConfig = activeFormData.getOutlineShader();
               if (formConfig.isEnabled()) {
                  selectedConfig = formConfig;
                  source = "form";
               }
            }

            if (activeStackFormData != null) {
               FormConfig.FormData.OutlineShaderConfig stackConfig = activeStackFormData.getOutlineShader();
               if (stackConfig.isEnabled()) {
                  selectedConfig = stackConfig;
                  source = "stack";
               }
            }

            if (selectedConfig == null) {
               return null;
            } else {
               String signature = character.getRaceName()
                  + "|"
                  + safe(character.getActiveFormGroup())
                  + ":"
                  + safe(character.getActiveForm())
                  + "|"
                  + safe(character.getActiveStackFormGroup())
                  + ":"
                  + safe(character.getActiveStackForm())
                  + "|"
                  + source;
               return new TransformationPostShaderManager.ResolvedShaderConfig(
                  signature, TransformationPostShaderManager.ShaderUniformState.fromConfig(selectedConfig)
               );
            }
         }
      }
   }

   private static String safe(@Nullable String value) {
      return value != null ? value : "";
   }

   private static float outlineThicknessToBlurRadius(float outlineThickness) {
      return Math.max(2.0F, Math.min(14.0F, outlineThickness * 3.0F));
   }

   private static void applyUniform(EffectInstance effect, String uniformName, float value) {
      Uniform uniform = effect.getUniform(uniformName);
      if (uniform != null) {
         uniform.set(value);
      }
   }

   public static record MaskData(float primaryR, float primaryG, float primaryB, float secondaryR, float secondaryG, float secondaryB) {
   }

   private static record ResolvedShaderConfig(String signature, TransformationPostShaderManager.ShaderUniformState uniformState) {
   }

   private static record ShaderUniformState(
      float primaryR, float primaryG, float primaryB, float secondaryR, float secondaryG, float secondaryB, float outlineThickness
   ) {
      private static TransformationPostShaderManager.ShaderUniformState fromConfig(FormConfig.FormData.OutlineShaderConfig config) {
         float[] primary = ColorUtils.hexToRgb(config.getPrimaryColor());
         float[] secondary = ColorUtils.hexToRgb(config.getSecondaryColor());
         return new TransformationPostShaderManager.ShaderUniformState(
            primary[0], primary[1], primary[2], secondary[0], secondary[1], secondary[2], (float)config.getOutlineThickness()
         );
      }
   }

   private static final class TrackedShaderState {
      private String signature = "";
      private long lastSeenFrame = -1L;
      private TransformationPostShaderManager.ShaderUniformState uniformState;
   }
}
