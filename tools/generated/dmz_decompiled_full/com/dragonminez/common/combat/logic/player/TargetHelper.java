package com.dragonminez.common.combat.logic.player;

import com.dragonminez.common.alignment.NpcDispositionService;
import com.dragonminez.common.config.CombatConfig;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.entities.AllMastersEntity;
import com.dragonminez.common.init.entities.MastersEntity;
import com.dragonminez.common.quest.PartyManager;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.entity.PartEntity;

public class TargetHelper {
   public static Entity resolveHittable(Entity entity) {
      return entity instanceof PartEntity<?> part ? part.getParent() : entity;
   }

   public static Entity getEntityOrPart(Level level, int id) {
      return level instanceof ServerLevel serverLevel ? serverLevel.getEntityOrPart(id) : level.getEntity(id);
   }

   public static TargetHelper.Relation getRelation(Player attacker, Entity target) {
      target = resolveHittable(target);
      if (attacker == target) {
         return TargetHelper.Relation.FRIENDLY;
      } else if (!(target instanceof AllMastersEntity.MasterEnmaEntity)
         && !(target instanceof AllMastersEntity.MasterUranaiEntity)
         && !(target instanceof AllMastersEntity.MasterToribotEntity)) {
         if (target instanceof Player targetPlayer && PartyManager.areInSameParty(attacker, targetPlayer)) {
            return PartyManager.isPartyPvpEnabled(attacker) ? TargetHelper.Relation.HOSTILE : TargetHelper.Relation.FRIENDLY;
         }

         if (target instanceof TamableAnimal tameable) {
            LivingEntity owner = tameable.getOwner();
            if (owner != null) {
               return getRelation(attacker, owner);
            }
         }

         if (target instanceof HangingEntity) {
            return TargetHelper.Relation.NEUTRAL;
         } else {
            Optional<TargetHelper.Relation> interactiveRelation = NpcDispositionService.getInteractiveRelation(attacker, target);
            if (interactiveRelation.isPresent()) {
               return interactiveRelation.get();
            } else {
               CombatConfig config = ConfigManager.getCombatConfig();
               PlayerTeam casterTeam = attacker.getTeam();
               PlayerTeam targetTeam = target.getTeam();
               if (casterTeam != null && targetTeam != null) {
                  return attacker.isAlliedTo(target) ? TargetHelper.Relation.FRIENDLY : TargetHelper.Relation.HOSTILE;
               } else {
                  ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
                  TargetHelper.Relation mappedRelation = config.getPlayerRelations().get(id != null ? id.toString() : "");
                  if (mappedRelation != null) {
                     return mappedRelation;
                  } else if (target instanceof Animal) {
                     return TargetHelper.Relation.coalesce(config.getPlayerRelationToPassives(), TargetHelper.Relation.HOSTILE);
                  } else {
                     return target instanceof Monster
                        ? TargetHelper.Relation.coalesce(config.getPlayerRelationToHostiles(), TargetHelper.Relation.HOSTILE)
                        : TargetHelper.Relation.coalesce(config.getPlayerRelationToOther(), TargetHelper.Relation.HOSTILE);
                  }
               }
            }
         }
      } else {
         return TargetHelper.Relation.FRIENDLY;
      }
   }

   public static boolean isAttackableMount(Entity entity) {
      return !(entity instanceof Monster)
            && !isEntityHostileVehicle(
               BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()) != null ? BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString() : ""
            )
         ? ConfigManager.getCombatConfig().getAllowAttackingMount()
         : true;
   }

   public static boolean canAttack(Player attacker, Entity target, double maxRange) {
      target = resolveHittable(target);
      TargetHelper.Relation relation = getRelation(attacker, target);
      if (target instanceof MastersEntity) {
         return false;
      } else {
         return switch (relation) {
            case FRIENDLY -> false;
            case NEUTRAL -> isLookingAt(attacker, target, maxRange);
            case HOSTILE -> true;
         };
      }
   }

   public static void onSuccessfulAttack(Player attacker, Entity target, TargetHelper.Relation relation) {
      if (relation == TargetHelper.Relation.NEUTRAL && attacker instanceof ServerPlayer serverPlayer && NpcDispositionService.isInteractiveNpc(target)) {
         NpcDispositionService.markHostile(serverPlayer, target);
      }
   }

   private static boolean isLookingAt(Player attacker, Entity target, double maxRange) {
      if (attacker != null && target != null) {
         Vec3 eye = attacker.getEyePosition();
         Vec3 look = attacker.getLookAngle().normalize();
         AABB box = target.getBoundingBox().inflate(0.15);
         Optional<Vec3> hit = box.clip(eye, eye.add(look.scale(maxRange + 1.0)));
         return hit.isPresent();
      } else {
         return false;
      }
   }

   public static boolean isEntityHostileVehicle(String entityName) {
      return false;
   }

   public static enum Relation {
      FRIENDLY,
      NEUTRAL,
      HOSTILE;

      public static TargetHelper.Relation coalesce(TargetHelper.Relation value, TargetHelper.Relation fallback) {
         return value != null ? value : fallback;
      }
   }
}
