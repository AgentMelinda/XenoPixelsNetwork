package com.dragonminez.common.passives;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.RaceStatsConfig;
import com.dragonminez.common.passives.handlers.BerserkerPassive;
import com.dragonminez.common.passives.handlers.ClericPassive;
import com.dragonminez.common.passives.handlers.MartialArtistPassive;
import com.dragonminez.common.passives.handlers.PaladinPassive;
import com.dragonminez.common.passives.handlers.SpiritualistPassive;
import com.dragonminez.common.passives.handlers.TankPassive;
import com.dragonminez.common.passives.handlers.WarriorPassive;
import com.dragonminez.common.stats.StatsData;
import java.util.HashMap;
import java.util.Map;

public final class ClassPassives {
   public static final IClassPassive NONE = () -> "";
   private static final Map<String, IClassPassive> REGISTRY = new HashMap<>();

   private ClassPassives() {
   }

   public static void register(IClassPassive passive) {
      if (passive != null && passive.classKey() != null && !passive.classKey().isEmpty()) {
         REGISTRY.put(passive.classKey().toLowerCase(), passive);
      }
   }

   public static IClassPassive get(StatsData data) {
      RaceStatsConfig.Passive cfg = configFor(data);
      if (cfg != null && cfg.isEnabled()) {
         String characterClass = data.getCharacter().getCharacterClass();
         if (characterClass == null) {
            return NONE;
         } else {
            IClassPassive passive = REGISTRY.get(characterClass.toLowerCase());
            return passive != null ? passive : NONE;
         }
      } else {
         return NONE;
      }
   }

   public static RaceStatsConfig.Passive configFor(StatsData data) {
      if (data != null && data.getCharacter() != null) {
         String race = data.getCharacter().getRaceName();
         String characterClass = data.getCharacter().getCharacterClass();
         if (race != null && characterClass != null) {
            RaceStatsConfig raceConfig = ConfigManager.getRaceStats(race);
            if (raceConfig == null) {
               return null;
            } else {
               RaceStatsConfig.ClassStats classStats = raceConfig.getClassStats(characterClass);
               return classStats == null ? null : classStats.getPassive();
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static double value(StatsData data, String key, double def) {
      RaceStatsConfig.Passive p = configFor(data);
      if (p != null && p.getValues() != null) {
         Double v = p.getValues().get(key);
         return v != null ? v : def;
      } else {
         return def;
      }
   }

   public static boolean is(StatsData data, String classKey) {
      return classKey != null && classKey.equalsIgnoreCase(get(data).classKey());
   }

   static {
      register(new WarriorPassive());
      register(new MartialArtistPassive());
      register(new SpiritualistPassive());
      register(new BerserkerPassive());
      register(new PaladinPassive());
      register(new TankPassive());
      register(new ClericPassive());
   }
}
