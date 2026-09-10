package com.dragonminez.client.render;

import com.dragonminez.client.model.KiWeaponModelLoader;
import com.dragonminez.client.render.compat.CosmeticArmorCompat;
import com.dragonminez.client.render.layer.AuraTintTracker;
import com.dragonminez.client.render.layer.BodyLayerFadeTracker;
import com.dragonminez.client.render.layer.DMZSkinLayer;
import com.dragonminez.client.render.util.ModRenderTypes;
import com.dragonminez.client.render.util.PlayerEffectQueue;
import com.dragonminez.client.util.ArmorTextureResolver;
import com.dragonminez.client.util.SkinGathererProvider;
import com.dragonminez.common.combat.logic.weapon.KiWeaponHelper;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.config.RaceCharacterConfig;
import com.dragonminez.common.init.armor.DbzArmorItem;
import com.dragonminez.common.init.armor.DbzArmorTextured;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.compat.util.LazyOptional;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

@OnlyIn(Dist.CLIENT)
public class DMZRenderHand extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
   private static final float KI_GEO_TX = -0.06F;
   private static final float KI_GEO_TY = -0.03F;
   private static final float KI_GEO_TZ = -0.1F;
   private static final float KI_GEO_RX = 0.0F;
   private static final float KI_GEO_RY = 15.0F;
   private static final float KI_GEO_RZ = 0.0F;
   private static final float[] WHITE_COLOR = new float[]{1.0F, 1.0F, 1.0F};
   private final float[] colorBuffer = new float[3];

   public DMZRenderHand(Context pContext, PlayerModel<AbstractClientPlayer> pModel) {
      super(pContext, new PlayerModel(pContext.bakeLayer(ModelLayers.PLAYER), false), 0.4F);
   }

   public void renderRightHand(PoseStack pPoseStack, MultiBufferSource pBuffer, int pCombinedLight, AbstractClientPlayer pPlayer) {
      StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, pPlayer).orElse(new StatsData(pPlayer));
      if (stats.getStatus().isBlocking()) {
         pPoseStack.pushPose();
         this.applyBlockingTransform(pPoseStack, 1.0F);
         this.renderHand(pPoseStack, pBuffer, pCombinedLight, pPlayer, ((PlayerModel)this.model).rightArm, ((PlayerModel)this.model).rightSleeve);
         pPoseStack.popPose();
         pPoseStack.pushPose();
         this.applyBlockingTransform(pPoseStack, -1.0F);
         this.renderHand(pPoseStack, pBuffer, pCombinedLight, pPlayer, ((PlayerModel)this.model).leftArm, ((PlayerModel)this.model).leftSleeve);
         pPoseStack.popPose();
      } else {
         pPoseStack.pushPose();
         this.renderHand(pPoseStack, pBuffer, pCombinedLight, pPlayer, ((PlayerModel)this.model).rightArm, ((PlayerModel)this.model).rightSleeve);
         pPoseStack.popPose();
      }

      this.renderKiWeapon(pPoseStack, pBuffer, pCombinedLight, pPlayer, stats, HumanoidArm.RIGHT);
      if ((stats.getStatus().isAuraActive() || stats.getStatus().isPermanentAura()) && !stats.getStatus().isAndroidUpgraded()) {
         this.queueFirstPersonAura(pPlayer, pPoseStack, pCombinedLight);
      }
   }

   public void renderLeftHand(PoseStack pPoseStack, MultiBufferSource pBuffer, int pCombinedLight, AbstractClientPlayer pPlayer) {
      LazyOptional<StatsData> statsCap = StatsProvider.get(StatsCapability.INSTANCE, pPlayer);
      StatsData stats = statsCap.orElse(new StatsData(pPlayer));
      if (stats.getStatus().isBlocking()) {
         pPoseStack.pushPose();
         this.applyBlockingTransform(pPoseStack, 1.0F);
         this.renderHand(pPoseStack, pBuffer, pCombinedLight, pPlayer, ((PlayerModel)this.model).rightArm, ((PlayerModel)this.model).rightSleeve);
         pPoseStack.popPose();
         pPoseStack.pushPose();
         this.applyBlockingTransform(pPoseStack, -1.0F);
         this.renderHand(pPoseStack, pBuffer, pCombinedLight, pPlayer, ((PlayerModel)this.model).leftArm, ((PlayerModel)this.model).leftSleeve);
         pPoseStack.popPose();
      } else {
         pPoseStack.pushPose();
         this.renderHand(pPoseStack, pBuffer, pCombinedLight, pPlayer, ((PlayerModel)this.model).leftArm, ((PlayerModel)this.model).leftSleeve);
         pPoseStack.popPose();
      }

      this.renderKiWeapon(pPoseStack, pBuffer, pCombinedLight, pPlayer, stats, HumanoidArm.LEFT);
   }

   private void applyBlockingTransform(PoseStack stack, float side) {
      stack.translate(side * -0.25F, -0.15F, -0.4F);
      stack.mulPose(Axis.XP.rotationDegrees(-20.0F));
      stack.mulPose(Axis.YP.rotationDegrees(100.0F));
      stack.mulPose(Axis.ZP.rotationDegrees(side * 330.0F));
   }

   private void renderHand(
      final PoseStack pPoseStack,
      final MultiBufferSource pBuffer,
      final int pCombinedLight,
      AbstractClientPlayer pPlayer,
      final ModelPart pRendererArm,
      ModelPart pRendererArmwear
   ) {
      StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, pPlayer).orElse(new StatsData(pPlayer));
      FormConfig.FormData tintForm = DMZSkinLayer.resolveTintForm(stats);
      final float[] formTintColor = tintForm != null ? tintForm.getRgbTintColor() : null;
      final float formTintIntensity = tintForm != null ? (float)tintForm.getTintIntensity() : 0.0F;
      ((PlayerModel)this.model).attackTime = 0.0F;
      ((PlayerModel)this.model).crouching = false;
      ((PlayerModel)this.model).swimAmount = 0.0F;
      ((PlayerModel)this.model).setupAnim(pPlayer, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
      pRendererArm.xRot = 0.0F;
      String raceName = stats.getCharacter().getRaceName().toLowerCase();
      RaceCharacterConfig raceConfig = ConfigManager.getRaceCharacter(raceName);
      final List<BodyLayerFadeTracker.FadingLayer> fadingLayers = new ArrayList<>();
      SkinGathererProvider.BodyLayerSink layerConsumer = new SkinGathererProvider.BodyLayerSink() {
         @Override
         public void base(ResourceLocation texture, float[] color) {
            DMZRenderHand.this.applyFormTint(color, formTintColor, formTintIntensity, DMZRenderHand.this.colorBuffer);
            DMZRenderHand.this.renderPart(pPoseStack, pBuffer, pCombinedLight, pRendererArm, texture, DMZRenderHand.this.colorBuffer);
         }

         @Override
         public void fading(String layerId, ResourceLocation texture, float[] color, float targetAlpha) {
            fadingLayers.add(new BodyLayerFadeTracker.FadingLayer(layerId, texture, color, targetAlpha));
         }
      };
      float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
      SkinGathererProvider.INSTANCE.gatherBodyLayers(pPlayer, stats, pt, layerConsumer);
      this.addSsj4HandFur(stats, fadingLayers);
      SkinGathererProvider.INSTANCE.gatherAndroidLayers(pPlayer, stats, pt, layerConsumer);
      SkinGathererProvider.INSTANCE.gatherTattooLayers(pPlayer, stats, pt, layerConsumer);
      SkinGathererProvider.INSTANCE.gatherEffectLayers(pPlayer, stats, pt, layerConsumer);
      this.renderFadingHandLayers(pPoseStack, pBuffer, pCombinedLight, pPlayer, pRendererArm, formTintColor, formTintIntensity, fadingLayers);
      this.renderDbzArmor(pPoseStack, pBuffer, pCombinedLight, pPlayer, pRendererArm);
   }

   private void renderKiWeapon(PoseStack ps, MultiBufferSource buffer, int light, AbstractClientPlayer player, StatsData stats, HumanoidArm arm) {
      if (stats.getSkills().isSkillActive("kimanipulation")) {
         String type = stats.getStatus().getKiWeaponType();
         if (type != null && !type.equalsIgnoreCase("none")) {
            float[] color = KiWeaponHelper.resolveColorForType(type, this.getKiColor(stats));
            String lower = type.toLowerCase();
            ModelPart geoPart = KiWeaponModelLoader.get(lower);
            if (geoPart != null) {
               ResourceLocation tex = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/weapons/kiweapon_" + lower + ".png");
               ps.pushPose();
               ps.translate(-0.06F, -0.03F, -0.1F);
               ps.mulPose(Axis.YP.rotationDegrees(15.0F));
               this.renderKiPartTex(ps, buffer, light, geoPart, color, tex);
               ps.popPose();
            }
         }
      }
   }

   private void renderDbzArmor(PoseStack ps, MultiBufferSource pBuffer, int pCombinedLight, AbstractClientPlayer player, ModelPart pRendererArm) {
      ItemStack chestStack = player.getItemBySlot(EquipmentSlot.CHEST);
      if (CosmeticArmorCompat.isLoaded()) {
         ItemStack cosmeticStack = CosmeticArmorCompat.getCosmeticStack(player, EquipmentSlot.CHEST);
         if (cosmeticStack != null) {
            if (cosmeticStack.isEmpty()) {
               return;
            }

            chestStack = cosmeticStack;
         }
      }

      if (!chestStack.isEmpty()) {
         String modId = "dragonminez";
         String itemId = null;
         if (chestStack.getItem() instanceof DbzArmorItem dbzItem) {
            modId = dbzItem.getModId();
            itemId = dbzItem.getItemId();
         } else if (chestStack.getItem() instanceof DbzArmorTextured textured) {
            itemId = textured.getItemId();
         }

         if (itemId != null) {
            ResourceLocation armorResource = ArmorTextureResolver.resolve(modId, itemId, EquipmentSlot.CHEST, chestStack);
            ps.pushPose();
            boolean isRightArm = pRendererArm == ((PlayerModel)this.model).rightArm;
            float armorInflation = 1.05F;
            ps.scale(armorInflation, armorInflation, armorInflation);
            ps.translate(isRightArm ? 0.02 : -0.01, -0.04, 0.0);
            this.renderPart(ps, pBuffer, pCombinedLight, pRendererArm, armorResource, WHITE_COLOR);
            ps.popPose();
         }
      }
   }

   private void addSsj4HandFur(StatsData stats, List<BodyLayerFadeTracker.FadingLayer> out) {
      DMZSkinLayer.Ssj4Overlay ssj4 = DMZSkinLayer.resolveSsj4Overlay(stats);
      if (ssj4 != null) {
         ResourceLocation tex = DMZSkinLayer.getSafeTexture(
            SkinGathererProvider.getCachedTexture("textures/entity/races/humansaiyan/" + ssj4.key() + "_layer1.png")
         );
         out.add(new BodyLayerFadeTracker.FadingLayer("ssj4fur", tex, ssj4.color(), ssj4.target()));
      }
   }

   private void renderFadingHandLayers(
      PoseStack ps,
      MultiBufferSource buffer,
      int light,
      AbstractClientPlayer player,
      ModelPart arm,
      float[] formTintColor,
      float formTintIntensity,
      List<BodyLayerFadeTracker.FadingLayer> active
   ) {
      int id = player.getId();
      long gameTime = player.level().getGameTime();

      for (BodyLayerFadeTracker.RenderEntry entry : BodyLayerFadeTracker.update(id, gameTime, active)) {
         if (!(entry.alpha() <= 0.001F)) {
            this.applyFormTint(entry.color(), formTintColor, formTintIntensity, this.colorBuffer);
            this.renderPart(ps, buffer, light, arm, entry.texture(), this.colorBuffer, entry.alpha());
         }
      }
   }

   private void renderPart(PoseStack stack, MultiBufferSource buffer, int light, ModelPart part, ResourceLocation texture, float[] rgb) {
      this.renderPart(stack, buffer, light, part, texture, rgb, 1.0F);
   }

   private void renderPart(PoseStack stack, MultiBufferSource buffer, int light, ModelPart part, ResourceLocation texture, float[] rgb, float alpha) {
      VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(texture));
      part.render(
         stack,
         vc,
         light,
         OverlayTexture.NO_OVERLAY,
         ARGB32.color((int)(alpha * 255.0F), (int)(rgb[0] * 255.0F), (int)(rgb[1] * 255.0F), (int)(rgb[2] * 255.0F))
      );
   }

   private ResourceLocation loc(String path) {
      return ResourceLocation.fromNamespaceAndPath("dragonminez", path);
   }

   private static ArmPose getArmPose(AbstractClientPlayer pPlayer, InteractionHand pHand) {
      ItemStack itemstack = pPlayer.getItemInHand(pHand);
      if (itemstack.isEmpty()) {
         return ArmPose.EMPTY;
      } else {
         if (pPlayer.getUsedItemHand() == pHand && pPlayer.getUseItemRemainingTicks() > 0) {
            UseAnim useanim = itemstack.getUseAnimation();
            if (useanim == UseAnim.BLOCK) {
               return ArmPose.BLOCK;
            }

            if (useanim == UseAnim.BOW) {
               return ArmPose.BOW_AND_ARROW;
            }

            if (useanim == UseAnim.SPEAR) {
               return ArmPose.THROW_SPEAR;
            }

            if (useanim == UseAnim.CROSSBOW && pHand == pPlayer.getUsedItemHand()) {
               return ArmPose.CROSSBOW_CHARGE;
            }

            if (useanim == UseAnim.SPYGLASS) {
               return ArmPose.SPYGLASS;
            }

            if (useanim == UseAnim.TOOT_HORN) {
               return ArmPose.TOOT_HORN;
            }

            if (useanim == UseAnim.BRUSH) {
               return ArmPose.BRUSH;
            }
         } else if (!pPlayer.swinging && itemstack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(itemstack)) {
            return ArmPose.CROSSBOW_HOLD;
         }

         ArmPose forgeArmPose = IClientItemExtensions.of(itemstack).getArmPose(pPlayer, pHand, itemstack);
         return forgeArmPose != null ? forgeArmPose : ArmPose.ITEM;
      }
   }

   protected void setupRotations(
      AbstractClientPlayer pEntityLiving, PoseStack pPoseStack, float pAgeInTicks, float pRotationYaw, float pPartialTicks, float scale
   ) {
      float f = pEntityLiving.getSwimAmount(pPartialTicks);
      if (pEntityLiving.isFallFlying()) {
         super.setupRotations(pEntityLiving, pPoseStack, pAgeInTicks, pRotationYaw, pPartialTicks, scale);
         float f1 = (float)pEntityLiving.getFallFlyingTicks() + pPartialTicks;
         float f2 = Mth.clamp(f1 * f1 / 100.0F, 0.0F, 1.0F);
         if (!pEntityLiving.isAutoSpinAttack()) {
            pPoseStack.mulPose(Axis.XP.rotationDegrees(f2 * (-90.0F - pEntityLiving.getXRot())));
         }

         Vec3 vec3 = pEntityLiving.getViewVector(pPartialTicks);
         Vec3 vec31 = pEntityLiving.getDeltaMovementLerped(pPartialTicks);
         double d0 = vec31.horizontalDistanceSqr();
         double d1 = vec3.horizontalDistanceSqr();
         if (d0 > 0.0 && d1 > 0.0) {
            double d2 = (vec31.x * vec3.x + vec31.z * vec3.z) / Math.sqrt(d0 * d1);
            double d3 = vec31.x * vec3.z - vec31.z * vec3.x;
            pPoseStack.mulPose(Axis.YP.rotation((float)(Math.signum(d3) * Math.acos(d2))));
         }
      } else if (f > 0.0F) {
         super.setupRotations(pEntityLiving, pPoseStack, pAgeInTicks, pRotationYaw, pPartialTicks, scale);
         float f3 = !pEntityLiving.isInWater() && !pEntityLiving.isInFluidType((fluidType, height) -> pEntityLiving.canSwimInFluidType(fluidType))
            ? -90.0F
            : -90.0F - pEntityLiving.getXRot();
         float f4 = Mth.lerp(f, 0.0F, f3);
         pPoseStack.mulPose(Axis.XP.rotationDegrees(f4));
         if (pEntityLiving.isVisuallySwimming()) {
            pPoseStack.translate(0.0F, -1.0F, 0.3F);
         }
      } else {
         super.setupRotations(pEntityLiving, pPoseStack, pAgeInTicks, pRotationYaw, pPartialTicks, scale);
      }
   }

   public ResourceLocation getTextureLocation(AbstractClientPlayer pEntity) {
      return pEntity.getSkin().texture();
   }

   private void renderKiPartTex(PoseStack ps, MultiBufferSource buffer, int light, ModelPart part, float[] color, ResourceLocation texture) {
      VertexConsumer vc = buffer.getBuffer(ModRenderTypes.kiblast(texture));
      part.render(ps, vc, light, OverlayTexture.NO_OVERLAY, ARGB32.color(216, (int)(color[0] * 255.0F), (int)(color[1] * 255.0F), (int)(color[2] * 255.0F)));
   }

   private float[] getKiColor(StatsData stats) {
      Character character = stats.getCharacter();
      float[] kiColor = character.getRgbAuraColor();
      if (character.hasActiveForm() && character.getActiveFormData() != null) {
         float[] formColor = character.getActiveFormData().getRgbAuraColor();
         if (formColor != null) {
            kiColor = formColor;
         }
      }

      return kiColor;
   }

   private void applyFormTint(float[] source, float[] tint, float intensity, float[] dest) {
      if (!(intensity <= 0.0F) && tint != null) {
         float i = Mth.clamp(intensity, 0.0F, 1.0F) * AuraTintTracker.darkTintScale(source);
         dest[0] = Mth.clamp(source[0] * (1.0F - i) + tint[0] * i, 0.0F, 1.0F);
         dest[1] = Mth.clamp(source[1] * (1.0F - i) + tint[1] * i, 0.0F, 1.0F);
         dest[2] = Mth.clamp(source[2] * (1.0F - i) + tint[2] * i, 0.0F, 1.0F);
      } else {
         dest[0] = source[0];
         dest[1] = source[1];
         dest[2] = source[2];
      }
   }

   private void queueFirstPersonAura(AbstractClientPlayer player, PoseStack poseStack, int packedLight) {
      float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
      PlayerEffectQueue.addFirstPersonAura(player, poseStack, partialTick, packedLight);
   }
}
