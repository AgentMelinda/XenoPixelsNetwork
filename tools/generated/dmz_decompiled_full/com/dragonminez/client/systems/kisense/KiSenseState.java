package com.dragonminez.client.systems.kisense;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.UpdateSkillC2S;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.skills.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public final class KiSenseState {
   private static KiSenseState.Mode mode = KiSenseState.Mode.NONE;

   private KiSenseState() {
   }

   public static KiSenseState.Mode getMode() {
      return mode;
   }

   public static boolean isActive() {
      return mode != KiSenseState.Mode.NONE;
   }

   public static boolean isCombat() {
      return mode == KiSenseState.Mode.COMBAT;
   }

   public static boolean isSearch() {
      return mode == KiSenseState.Mode.SEARCH;
   }

   public static void cycle() {
      KiSenseState.Mode next = switch (mode) {
         case NONE -> KiSenseState.Mode.COMBAT;
         case COMBAT -> KiSenseState.Mode.SEARCH;
         case SEARCH -> KiSenseState.Mode.NONE;
      };
      set(next);
   }

   public static void set(KiSenseState.Mode newMode) {
      mode = newMode;
      KiSenseScan.forceRescan();
      syncActiveFlag();
   }

   public static void reset() {
      mode = KiSenseState.Mode.NONE;
   }

   private static void syncActiveFlag() {
      Player player = Minecraft.getInstance().player;
      if (player != null) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            Skill kiSense = data.getSkills().getSkill("kisense");
            if (kiSense != null && kiSense.getLevel() > 0) {
               boolean desired = mode != KiSenseState.Mode.NONE;
               if (desired != kiSense.isActive()) {
                  data.getSkills().setSkillActive("kisense", desired);
                  NetworkHandler.sendToServer(new UpdateSkillC2S(UpdateSkillC2S.SkillAction.TOGGLE, "kisense", 0));
               }
            }
         });
      }
   }

   public static enum Mode {
      NONE,
      COMBAT,
      SEARCH;
   }
}
