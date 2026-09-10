package com.dragonminez.common.init.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class DMZEffect extends MobEffect {
   public DMZEffect() {
      super(MobEffectCategory.NEUTRAL, 11184810);
   }

   public DMZEffect(boolean beneficial) {
      super(beneficial ? MobEffectCategory.BENEFICIAL : MobEffectCategory.HARMFUL, beneficial ? 10017154 : 14254722);
   }

   public DMZEffect(MobEffectCategory category, int color) {
      super(category, color);
   }

   public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
      return true;
   }
}
