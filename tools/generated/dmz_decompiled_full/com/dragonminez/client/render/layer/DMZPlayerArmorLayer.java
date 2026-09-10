package com.dragonminez.client.render.layer;

import com.dragonminez.client.render.compat.CosmeticArmorCompat;
import com.dragonminez.client.util.ArmorTextureResolver;
import com.dragonminez.client.util.SkinGathererProvider;
import com.dragonminez.common.init.armor.DbzArmorItem;
import com.dragonminez.common.init.armor.DbzArmorTextured;
import com.dragonminez.common.init.armor.client.model.ArmorBaseModel;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorMaterial.Layer;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer;

public class DMZPlayerArmorLayer<T extends AbstractClientPlayer & GeoAnimatable> extends ItemArmorGeoLayer<T> {
   private ArmorBaseModel dmzArmorModel;

   public DMZPlayerArmorLayer(GeoRenderer<T> geoRenderer) {
      super(geoRenderer);
   }

   public void render(
      PoseStack poseStack,
      T animatable,
      BakedGeoModel bakedModel,
      RenderType renderType,
      MultiBufferSource bufferSource,
      VertexConsumer buffer,
      float partialTick,
      int packedLight,
      int packedOverlay
   ) {
      if (!animatable.isSpectator()) {
         super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
      }
   }

   public void renderForBone(
      PoseStack poseStack,
      T animatable,
      GeoBone bone,
      RenderType renderType,
      MultiBufferSource bufferSource,
      VertexConsumer buffer,
      float partialTick,
      int packedLight,
      int packedOverlay
   ) {
      ItemStack stack = this.getArmorItemForBone(bone, animatable);
      if (stack != null && stack.getItem() instanceof DbzArmorTextured) {
         EquipmentSlot slot = this.getEquipmentSlotForBone(bone, stack, animatable);
         HumanoidModel<?> armorModel = this.getModelForItem(bone, slot, stack, animatable);
         ModelPart armorPart = this.getModelPartForBone(bone, slot, stack, animatable, armorModel);
         if (!armorPart.isEmpty() && !bone.getCubes().isEmpty()) {
            poseStack.pushPose();
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            this.prepModelPartForRender(poseStack, bone, armorPart);
            VertexConsumer armorBuffer = this.getVanillaArmorBuffer(bufferSource, animatable, stack, slot, bone, null, packedLight, packedOverlay, false);
            armorPart.render(poseStack, armorBuffer, packedLight, packedOverlay, -1);
            if (stack.hasFoil()) {
               VertexConsumer glintBuffer = this.getVanillaArmorBuffer(bufferSource, animatable, stack, slot, bone, null, packedLight, packedOverlay, true);
               armorPart.render(poseStack, glintBuffer, packedLight, packedOverlay, -1);
            }

            poseStack.popPose();
         }
      } else {
         super.renderForBone(poseStack, animatable, bone, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
      }
   }

   @Nullable
   protected ItemStack getArmorItemForBone(GeoBone bone, T animatable) {
      String boneName = bone.getName();

      EquipmentSlot slot = switch (boneName) {
         case "armorHead", "armor_head" -> EquipmentSlot.HEAD;
         case "armorBody", "armor_body", "armorRightArm", "armor_right_arm", "armorLeftArm", "armor_left_arm" -> EquipmentSlot.CHEST;
         case "armorLeggingsBody", "armor_leggings_body", "armorLeftLeg", "armor_left_leg", "armorRightLeg", "armor_right_leg" -> EquipmentSlot.LEGS;
         case "armorRightBoot", "armor_right_boot", "armorLeftBoot", "armor_left_boot" -> EquipmentSlot.FEET;
         default -> null;
      };
      if (slot == null) {
         return null;
      } else {
         ItemStack stack = (ItemStack)animatable.getInventory().armor.get(slot.getIndex());
         if (CosmeticArmorCompat.isLoaded()) {
            ItemStack cosStack = CosmeticArmorCompat.getCosmeticStack(animatable, slot);
            if (cosStack != null) {
               if (cosStack.isEmpty()) {
                  return null;
               }

               stack = cosStack;
            }
         }

         if (stack.isEmpty()) {
            return null;
         } else if (!(stack.getItem() instanceof ArmorItem) && !(stack.getItem() instanceof DbzArmorItem)) {
            return null;
         } else if (!stack.canEquip(slot, animatable) && !(stack.getItem() instanceof DbzArmorItem)) {
            return null;
         } else {
            StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, animatable).orElse(null);
            if (stats != null) {
               Character character = stats.getCharacter();
               String race = character.getRaceName().toLowerCase();
               String gender = character.getGender().toLowerCase();
               int bodyType = character.getBodyType();
               String logicKey = character.getRenderLogicKey();
               if (logicKey.equals("candy")) {
                  return null;
               }

               if (boneName.equals("armorBody") || boneName.equals("armor_body")) {
                  boolean isArmored = character.getArmored();
                  boolean isMajin = logicKey.equals("majin");
                  boolean isFemaleHumanOrSaiyan = (race.equals("human") || race.equals("saiyan")) && gender.equals("female");
                  boolean isOozaru = character.isOozaruCached() || logicKey.contains("oozaru");
                  boolean isBuffed = logicKey.contains("buffed")
                     || logicKey.contains("frostdemon_fp")
                     || logicKey.contains("majin_ultra")
                     || logicKey.contains("namekian_orange")
                     || logicKey.contains("bioandroid_ultra")
                     || logicKey.contains("ssj4d")
                     || logicKey.contains("ssj4gt")
                     || logicKey.contains("frostdemon_fifth")
                     || logicKey.contains("frostdemon_metalcore")
                     || logicKey.contains("namekian_buffed")
                     || logicKey.contains("4arms")
                     || logicKey.contains("bioandroid_xeno")
                     || logicKey.contains("janemba_super");
                  boolean isDbzArmor = stack.getItem() instanceof DbzArmorTextured;
                  boolean isRestrictedMajin = isMajin && bodyType != 2 || logicKey.equals("janemba_fat");
                  boolean isCustomModel = SkinGathererProvider.modelFamily(logicKey).equals("custom");
                  if (!isRestrictedMajin && !isFemaleHumanOrSaiyan && !isOozaru) {
                     if ((isBuffed || isCustomModel) && isDbzArmor) {
                        return null;
                     }
                  } else if (!isArmored) {
                     return null;
                  }
               }
            }

            return stack;
         }
      }
   }

   @NotNull
   protected EquipmentSlot getEquipmentSlotForBone(GeoBone bone, ItemStack stack, T animatable) {
      String boneName = bone.getName();

      return switch (boneName) {
         case "armorHead" -> EquipmentSlot.HEAD;
         case "armorBody", "armorRightArm", "armorLeftArm" -> EquipmentSlot.CHEST;
         case "armorLeggingsBody", "armorRightLeg", "armorLeftLeg" -> EquipmentSlot.LEGS;
         case "armorRightBoot", "armorLeftBoot" -> EquipmentSlot.FEET;
         default -> super.getEquipmentSlotForBone(bone, stack, animatable);
      };
   }

   @NotNull
   protected ModelPart getModelPartForBone(GeoBone bone, EquipmentSlot slot, ItemStack stack, T animatable, HumanoidModel<?> baseModel) {
      String boneName = bone.getName();

      return switch (boneName) {
         case "armorHead" -> baseModel.head;
         case "armorBody", "armorLeggingsBody" -> baseModel.body;
         case "armorRightArm" -> baseModel.rightArm;
         case "armorLeftArm" -> baseModel.leftArm;
         case "armorRightLeg", "armorRightBoot" -> baseModel.rightLeg;
         case "armorLeftLeg", "armorLeftBoot" -> baseModel.leftLeg;
         default -> super.getModelPartForBone(bone, slot, stack, animatable, baseModel);
      };
   }

   protected HumanoidModel<?> getModelForItem(GeoBone bone, EquipmentSlot slot, ItemStack stack, T animatable) {
      if (!(stack.getItem() instanceof DbzArmorTextured)) {
         return super.getModelForItem(bone, slot, stack, animatable);
      } else {
         if (this.dmzArmorModel == null) {
            this.dmzArmorModel = new ArmorBaseModel(Minecraft.getInstance().getEntityModels().bakeLayer(ArmorBaseModel.LAYER_LOCATION));
         }

         return this.dmzArmorModel;
      }
   }

   protected VertexConsumer getVanillaArmorBuffer(
      MultiBufferSource bufferSource,
      T animatable,
      ItemStack stack,
      EquipmentSlot slot,
      GeoBone bone,
      Layer layer,
      int packedLight,
      int packedOverlay,
      boolean glint
   ) {
      if (stack.getItem() instanceof DbzArmorTextured textured) {
         if (glint) {
            return bufferSource.getBuffer(RenderType.armorEntityGlint());
         } else {
            String namespace = "dragonminez";
            if (stack.getItem() instanceof DbzArmorItem dbzArmor) {
               namespace = dbzArmor.getModId();
            }

            ResourceLocation texture = ArmorTextureResolver.resolve(namespace, textured.getItemId(), slot, stack);
            return bufferSource.getBuffer(RenderType.armorCutoutNoCull(texture));
         }
      } else {
         return super.getVanillaArmorBuffer(bufferSource, animatable, stack, slot, bone, layer, packedLight, packedOverlay, glint);
      }
   }
}
