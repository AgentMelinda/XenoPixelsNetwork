package com.dragonminez.common.combat.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.Nullable;

public interface Minecraft_DMZ {
   int getComboCount();

   boolean hasTargetsInReach();

   @Nullable
   default Entity getCursorTarget() {
      Minecraft client = (Minecraft)this;
      return client.hitResult != null && client.hitResult.getType() == Type.ENTITY ? ((EntityHitResult)client.hitResult).getEntity() : null;
   }

   int getUpswingTicks();

   float getSwingProgress();

   default boolean isWeaponSwingInProgress() {
      return this.getSwingProgress() < 1.0F;
   }

   void cancelUpswing();
}
