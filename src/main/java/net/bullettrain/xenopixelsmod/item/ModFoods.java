package net.bullettrain.xenopixelsmod.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;

public class ModFoods {

    public static final FoodProperties STRAWBERRY_SENZU = new FoodProperties.Builder().nutrition(2).fast()
            .saturationMod(0.2f).alwaysEat()
            .effect(() -> new MobEffectInstance(MobEffects.HEAL, 200, 10), 1.0f).build();
}