package com.dragonminez.client.events;

import com.dragonminez.common.combat.player.AttackHand;
import java.util.List;
import lombok.Generated;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

public class DMZClientEvent {
   public static class KiAttackCast extends Event {
      private final LocalPlayer player;
      private final int slot;

      public KiAttackCast(LocalPlayer player, int slot) {
         this.player = player;
         this.slot = slot;
      }

      @Generated
      public LocalPlayer getPlayer() {
         return this.player;
      }

      @Generated
      public int getSlot() {
         return this.slot;
      }
   }

   public static class KiAttackRelease extends Event {
      private final LocalPlayer player;

      public KiAttackRelease(LocalPlayer player) {
         this.player = player;
      }

      @Generated
      public LocalPlayer getPlayer() {
         return this.player;
      }
   }

   public static class PlayerAttackHit extends Event {
      private final LocalPlayer player;
      private final AttackHand attackHand;
      private final List<Entity> targets;
      @Nullable
      private final Entity cursorTarget;

      public PlayerAttackHit(LocalPlayer player, AttackHand attackHand, List<Entity> targets, @Nullable Entity cursorTarget) {
         this.player = player;
         this.attackHand = attackHand;
         this.targets = targets;
         this.cursorTarget = cursorTarget;
      }

      @Nullable
      public Entity getCursorTarget() {
         return this.cursorTarget;
      }

      @Generated
      public LocalPlayer getPlayer() {
         return this.player;
      }

      @Generated
      public AttackHand getAttackHand() {
         return this.attackHand;
      }

      @Generated
      public List<Entity> getTargets() {
         return this.targets;
      }
   }

   public static class PlayerAttackStart extends Event {
      private final LocalPlayer player;
      private final AttackHand attackHand;

      public PlayerAttackStart(LocalPlayer player, AttackHand attackHand) {
         this.player = player;
         this.attackHand = attackHand;
      }

      @Generated
      public LocalPlayer getPlayer() {
         return this.player;
      }

      @Generated
      public AttackHand getAttackHand() {
         return this.attackHand;
      }
   }

   public static class StrikeAttack extends Event {
      private final LocalPlayer player;
      private final int targetId;

      public StrikeAttack(LocalPlayer player, int targetId) {
         this.player = player;
         this.targetId = targetId;
      }

      @Generated
      public LocalPlayer getPlayer() {
         return this.player;
      }

      @Generated
      public int getTargetId() {
         return this.targetId;
      }
   }
}
