package com.dragonminez.common.quest.objectives;

import com.dragonminez.common.quest.QuestObjective;
import lombok.Generated;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public class InteractObjective extends QuestObjective {
   private final String entityTypeId;
   private final String entityName;

   public InteractObjective(EntityType<?> entityType, String entityName) {
      super(QuestObjective.ObjectiveType.INTERACT, 1);
      this.entityTypeId = entityType != null ? BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString() : null;
      this.entityName = entityName;
   }

   @Override
   public boolean checkProgress(Object... params) {
      if (params.length > 0 && params[0] instanceof Entity entity) {
         EntityType<?> requiredType = this.entityTypeId != null
            ? (EntityType)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(this.entityTypeId))
            : null;
         if ((requiredType == null || entity.getType().equals(requiredType))
            && (this.entityName == null || entity.getName().getString().equals(this.entityName))) {
            this.setProgress(1);
            return true;
         }
      }

      return false;
   }

   @Generated
   public String getEntityTypeId() {
      return this.entityTypeId;
   }

   @Generated
   public String getEntityName() {
      return this.entityName;
   }
}
