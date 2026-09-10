package com.dragonminez.common.init.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.CustomData;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class WeightItem extends DMZCuriosItem implements GeoItem {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private final WeightItem.WeightType weightType;

   public WeightItem(Properties properties, WeightItem.WeightType weightType) {
      super(properties, DMZCuriosItem.CurioType.WEIGHTS);
      this.weightType = weightType;
   }

   public static int getWeight(ItemStack stack) {
      if (stack.isEmpty()) {
         return 0;
      } else {
         CustomData custom = (CustomData)stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
         return custom.copyTag().getInt("WeightValue");
      }
   }

   public static void setWeight(ItemStack stack, int weight) {
      CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("WeightValue", weight));
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      super.appendHoverText(stack, context, tooltip, flag);
      int weight = getWeight(stack);
      if (weight > 0) {
         tooltip.add(Component.translatable("item.dragonminez.weight.tooltip", new Object[]{weight}).withStyle(ChatFormatting.GOLD));
      }
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(
         new AnimationController(
            this,
            "controller",
            5,
            event -> event.isMoving()
                  ? event.setAndContinue(RawAnimation.begin().thenLoop("walk"))
                  : event.setAndContinue(RawAnimation.begin().thenLoop("idle"))
         )
      );
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   public WeightItem.WeightType getWeightType() {
      return this.weightType;
   }

   public static enum WeightType {
      TURTLE_SHELL,
      WORKOUT_WEIGHTS,
      PICCOLO_CAPE;
   }
}
