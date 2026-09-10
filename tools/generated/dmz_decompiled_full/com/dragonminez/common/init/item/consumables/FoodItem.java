package com.dragonminez.common.init.item.consumables;

import net.minecraft.world.food.FoodProperties.Builder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;

public class FoodItem extends Item {
   public FoodItem(int hunger, float saturation, int maxStack) {
      super(new Properties().stacksTo(maxStack).food(new Builder().nutrition(hunger).saturationModifier(saturation).alwaysEdible().build()));
   }
}
