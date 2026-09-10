package com.dragonminez.common.quest;

import com.dragonminez.common.quest.objectives.BiomeObjective;
import com.dragonminez.common.quest.objectives.CoordsObjective;
import com.dragonminez.common.quest.objectives.DimensionObjective;
import com.dragonminez.common.quest.objectives.StructureObjective;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class QuestLocationHelper {
   private QuestLocationHelper() {
   }

   public static boolean isLocationObjective(QuestObjective objective) {
      return objective instanceof BiomeObjective
         || objective instanceof StructureObjective
         || objective instanceof DimensionObjective
         || objective instanceof CoordsObjective;
   }

   public static boolean isLocationConditionMet(ServerPlayer player, QuestObjective objective) {
      return player == null ? false : isLocationConditionMet(player, objective, false);
   }

   private static boolean isLocationConditionMet(Player player, QuestObjective objective, boolean allowUnknownStructureOnClient) {
      return player != null && objective != null
         ? isLocationConditionMet(player.level(), player.blockPosition(), objective, allowUnknownStructureOnClient)
         : false;
   }

   private static boolean isLocationConditionMet(Level level, BlockPos pos, QuestObjective objective, boolean allowUnknownStructureOnClient) {
      if (objective instanceof BiomeObjective biomeObj) {
         return matchesBiome(level, pos, biomeObj.getBiomeId());
      } else if (objective instanceof StructureObjective structObj) {
         return level instanceof ServerLevel serverLevel ? isInStructure(serverLevel, pos, structObj.getStructureId()) : allowUnknownStructureOnClient;
      } else if (objective instanceof DimensionObjective dimensionObj) {
         return isInDimension(level, dimensionObj.getDimensionId());
      } else if (objective instanceof CoordsObjective coordsObj) {
         double distSq = pos.distSqr(coordsObj.getTargetPos());
         double radiusSq = (double)coordsObj.getRadius() * (double)coordsObj.getRadius();
         return distSq <= radiusSq;
      } else {
         return false;
      }
   }

   public static boolean matchesBiome(Level level, BlockPos pos, String targetBiome) {
      if (level != null && pos != null && targetBiome != null && !targetBiome.isBlank()) {
         try {
            Holder<Biome> biomeHolder = level.getBiome(pos);
            if (targetBiome.startsWith("#")) {
               String tagId = targetBiome.substring(1);
               if (!tagId.contains(":")) {
                  return false;
               } else {
                  ResourceLocation tagRL = ResourceLocation.parse(tagId);
                  TagKey<Biome> tagKey = TagKey.create(Registries.BIOME, tagRL);
                  return biomeHolder.is(tagKey);
               }
            } else {
               return !targetBiome.contains(":") ? false : biomeHolder.is(ResourceLocation.parse(targetBiome));
            }
         } catch (Exception var7) {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean isInStructure(ServerLevel level, BlockPos pos, String targetStructure) {
      if (level != null && pos != null && targetStructure != null && !targetStructure.isBlank() && targetStructure.contains(":")) {
         try {
            ResourceLocation structRL = ResourceLocation.parse(targetStructure);
            Structure structure = (Structure)level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structRL);
            return structure != null && level.structureManager().getStructureWithPieceAt(pos, structure).isValid();
         } catch (Exception var5) {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean isInDimension(Level level, String targetDimension) {
      if (level != null && targetDimension != null && !targetDimension.isBlank() && targetDimension.contains(":")) {
         try {
            ResourceLocation dimensionRL = ResourceLocation.parse(targetDimension);
            return level.dimension().location().equals(dimensionRL);
         } catch (Exception var3) {
            return false;
         }
      } else {
         return false;
      }
   }
}
