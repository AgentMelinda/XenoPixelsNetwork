package com.dragonminez.common.quest.objectives;

import com.dragonminez.common.quest.QuestObjective;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import lombok.Generated;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public class KillObjective extends QuestObjective {
   private final String entityId;
   private final int count;
   private final double health;
   private final double meleeDamage;
   private final double kiDamage;
   private final KillObjective.SpawnMode spawnMode;
   private final KillObjective.CountMode countMode;
   private final int textureVariant;
   private final int aiTier;
   private final boolean canTransform;
   private final Double transformHealth;
   private final Double transformMeleeDamage;
   private final Double transformKiDamage;
   private final Double transformHealthMultiplier;
   private final Double transformMeleeMultiplier;
   private final Double transformKiMultiplier;
   private final Double transformTriggerPercent;

   public KillObjective(
      String entityId,
      int count,
      double health,
      double meleeDamage,
      double kiDamage,
      KillObjective.SpawnMode spawnMode,
      KillObjective.CountMode countMode,
      int textureVariant,
      int aiTier,
      boolean canTransform,
      Double transformHealth,
      Double transformMeleeDamage,
      Double transformKiDamage,
      Double transformHealthMultiplier,
      Double transformMeleeMultiplier,
      Double transformKiMultiplier,
      Double transformTriggerPercent
   ) {
      super(QuestObjective.ObjectiveType.KILL, count);
      this.entityId = entityId;
      this.count = count;
      this.health = health;
      this.meleeDamage = meleeDamage;
      this.kiDamage = kiDamage;
      this.spawnMode = spawnMode != null ? spawnMode : KillObjective.SpawnMode.QUEST;
      this.countMode = countMode != null ? countMode : KillObjective.CountMode.QUEST_SPAWNED_ONLY;
      this.textureVariant = textureVariant;
      this.aiTier = aiTier;
      this.canTransform = canTransform;
      this.transformHealth = transformHealth;
      this.transformMeleeDamage = transformMeleeDamage;
      this.transformKiDamage = transformKiDamage;
      this.transformHealthMultiplier = transformHealthMultiplier;
      this.transformMeleeMultiplier = transformMeleeMultiplier;
      this.transformKiMultiplier = transformKiMultiplier;
      this.transformTriggerPercent = transformTriggerPercent;
   }

   @Override
   public boolean checkProgress(Object... params) {
      if (params.length > 0 && params[0] instanceof Entity entity && this.matches(entity.getType())) {
         this.addProgress(1);
         return this.isCompleted();
      } else {
         return false;
      }
   }

   public boolean isTag() {
      return this.entityId != null && this.entityId.startsWith("#");
   }

   public boolean matches(EntityType<?> type) {
      if (type != null && this.entityId != null) {
         try {
            if (this.isTag()) {
               TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(this.entityId.substring(1)));
               return type.builtInRegistryHolder().is(tag);
            } else {
               return type.equals(BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(this.entityId)));
            }
         } catch (Exception var3) {
            return false;
         }
      } else {
         return false;
      }
   }

   @Nullable
   public EntityType<?> resolveEntityType() {
      try {
         if (!this.isTag()) {
            return (EntityType<?>)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(this.entityId));
         } else {
            TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(this.entityId.substring(1)));
            List<EntityType<?>> members = new ArrayList<>();

            for (Holder<EntityType<?>> holder : BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(tag)) {
               members.add((EntityType<?>)holder.value());
            }

            return members.isEmpty() ? null : members.get(ThreadLocalRandom.current().nextInt(members.size()));
         }
      } catch (Exception var5) {
         return null;
      }
   }

   @Generated
   public String getEntityId() {
      return this.entityId;
   }

   @Generated
   public int getCount() {
      return this.count;
   }

   @Generated
   public double getHealth() {
      return this.health;
   }

   @Generated
   public double getMeleeDamage() {
      return this.meleeDamage;
   }

   @Generated
   public double getKiDamage() {
      return this.kiDamage;
   }

   @Generated
   public KillObjective.SpawnMode getSpawnMode() {
      return this.spawnMode;
   }

   @Generated
   public KillObjective.CountMode getCountMode() {
      return this.countMode;
   }

   @Generated
   public int getTextureVariant() {
      return this.textureVariant;
   }

   @Generated
   public int getAiTier() {
      return this.aiTier;
   }

   @Generated
   public boolean isCanTransform() {
      return this.canTransform;
   }

   @Generated
   public Double getTransformHealth() {
      return this.transformHealth;
   }

   @Generated
   public Double getTransformMeleeDamage() {
      return this.transformMeleeDamage;
   }

   @Generated
   public Double getTransformKiDamage() {
      return this.transformKiDamage;
   }

   @Generated
   public Double getTransformHealthMultiplier() {
      return this.transformHealthMultiplier;
   }

   @Generated
   public Double getTransformMeleeMultiplier() {
      return this.transformMeleeMultiplier;
   }

   @Generated
   public Double getTransformKiMultiplier() {
      return this.transformKiMultiplier;
   }

   @Generated
   public Double getTransformTriggerPercent() {
      return this.transformTriggerPercent;
   }

   public static enum CountMode {
      QUEST_SPAWNED_ONLY,
      ANY_MATCHING;
   }

   public static enum SpawnMode {
      QUEST,
      NATURAL;
   }
}
