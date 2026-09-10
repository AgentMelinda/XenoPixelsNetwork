package com.dragonminez.common.passives.handlers;

import com.dragonminez.common.passives.ClassPassives;
import com.dragonminez.common.passives.IClassPassive;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.techniques.KiAttackData;

public class SpiritualistPassive implements IClassPassive {
   @Override
   public String classKey() {
      return "spiritualist";
   }

   private boolean isDamageDebuff(KiAttackData ki) {
      return ki != null && ki.getUtility() == KiAttackData.Utility.DAMAGE && ki.getSecondaryEffectType() == KiAttackData.SecondaryEffectType.DEBUFF;
   }

   @Override
   public double kiCooldownMultiplier(StatsData data, KiAttackData ki) {
      if (ki != null && ki.getUtility() == KiAttackData.Utility.DAMAGE) {
         double reduction = this.isDamageDebuff(ki) ? ClassPassives.value(data, "cdSecondary", 0.15) : ClassPassives.value(data, "cdPrimary", 0.2);
         return Math.max(0.0, 1.0 - reduction);
      } else {
         return 1.0;
      }
   }

   @Override
   public double secondaryDurationMultiplier(StatsData data, KiAttackData ki) {
      return this.isDamageDebuff(ki) ? 1.0 + ClassPassives.value(data, "durationBonus", 0.25) : 1.0;
   }
}
