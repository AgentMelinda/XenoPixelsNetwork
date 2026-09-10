package com.dragonminez.common.passives.handlers;

import com.dragonminez.common.passives.ClassPassives;
import com.dragonminez.common.passives.IClassPassive;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.techniques.KiAttackData;

public class ClericPassive implements IClassPassive {
   @Override
   public String classKey() {
      return "cleric";
   }

   private boolean isHealBuff(KiAttackData ki) {
      return ki != null && ki.getUtility() == KiAttackData.Utility.HEAL && ki.getSecondaryEffectType() == KiAttackData.SecondaryEffectType.BUFF;
   }

   @Override
   public double kiCooldownMultiplier(StatsData data, KiAttackData ki) {
      if (ki != null && ki.getUtility() == KiAttackData.Utility.HEAL) {
         double reduction = this.isHealBuff(ki) ? ClassPassives.value(data, "cdSecondary", 0.15) : ClassPassives.value(data, "cdPrimary", 0.2);
         return Math.max(0.0, 1.0 - reduction);
      } else {
         return 1.0;
      }
   }

   @Override
   public double secondaryDurationMultiplier(StatsData data, KiAttackData ki) {
      return this.isHealBuff(ki) ? 1.0 + ClassPassives.value(data, "durationBonus", 0.25) : 1.0;
   }
}
