package com.dragonminez.common.init;

import com.dragonminez.common.compat.WorldGuardCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules.BooleanValue;
import net.minecraft.world.level.GameRules.Category;
import net.minecraft.world.level.GameRules.Key;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public class MainGameRules {
   private static final int MASTER_STRUCTURE_MARGIN = 2;
   public static final Key<BooleanValue> ALLOW_KI_GRIEFING_MOBS = GameRules.register("allowKiGriefingMobs", Category.PLAYER, BooleanValue.create(true));
   public static final Key<BooleanValue> ALLOW_KI_GRIEFING_PLAYERS = GameRules.register("allowKiGriefingPlayers", Category.PLAYER, BooleanValue.create(true));
   public static final Key<BooleanValue> ALLOW_KI_GRIEFING_MASTER_STRUCTURES = GameRules.register(
      "allowKiGriefingMasterStructures", Category.PLAYER, BooleanValue.create(false)
   );

   public static boolean canKiGrief(Level level, BlockPos pos, Entity source) {
      boolean gameruleAllows;
      if (!(source instanceof Player) && !(source instanceof ServerPlayer)) {
         gameruleAllows = level.getGameRules().getBoolean(ALLOW_KI_GRIEFING_MOBS);
      } else {
         gameruleAllows = level.getGameRules().getBoolean(ALLOW_KI_GRIEFING_PLAYERS);
      }

      if (!gameruleAllows) {
         return false;
      } else {
         return !level.getGameRules().getBoolean(ALLOW_KI_GRIEFING_MASTER_STRUCTURES) && isInMasterStructure(level, pos)
            ? false
            : WorldGuardCompat.canGrief(level, pos, source);
      }
   }

   private static boolean isInMasterStructure(Level level, BlockPos pos) {
      if (level instanceof ServerLevel serverLevel) {
         Registry registry = serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE);

         for (Structure structure : serverLevel.structureManager().getAllStructuresAt(pos).keySet()) {
            Holder<Structure> holder = registry.wrapAsHolder(structure);
            if (holder.is(MainTags.Structures.KI_GRIEFING_PROTECTED)) {
               StructureStart start = serverLevel.structureManager().getStructureAt(pos, structure);
               if (start.isValid() && start.getBoundingBox().inflatedBy(2).isInside(pos)) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static void register() {
   }
}
