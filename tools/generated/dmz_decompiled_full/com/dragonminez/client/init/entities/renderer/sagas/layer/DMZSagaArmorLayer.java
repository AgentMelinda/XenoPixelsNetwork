package com.dragonminez.client.init.entities.renderer.sagas.layer;

import com.dragonminez.common.init.armor.DbzArmorItem;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import javax.annotation.Nullable;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer;

public class DMZSagaArmorLayer<T extends DBSagasEntity> extends ItemArmorGeoLayer<T> {
   public DMZSagaArmorLayer(GeoRenderer<T> geoRenderer) {
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
         ItemStack stack = animatable.getItemBySlot(slot);
         if (stack.isEmpty()) {
            return null;
         } else if (!(stack.getItem() instanceof ArmorItem) && !(stack.getItem() instanceof DbzArmorItem)) {
            return null;
         } else {
            return !stack.canEquip(slot, animatable) && !(stack.getItem() instanceof DbzArmorItem) ? null : stack;
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
}
