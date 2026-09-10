package com.dragonminez.common.init.entities.goals;

import com.dragonminez.common.init.entities.namek.NamekWarriorEntity;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.entity.player.Player;

public class VillageAlertSystem {
   private static final Set<NamekWarriorEntity> warriors = new HashSet<>();

   public static void registerWarrior(NamekWarriorEntity warrior) {
      warriors.add(warrior);
   }

   public static void unregisterWarrior(NamekWarriorEntity warrior) {
      warriors.remove(warrior);
   }

   public static void alertAll(Player player) {
      for (NamekWarriorEntity warrior : warriors) {
         warrior.setTarget(player);
      }
   }
}
