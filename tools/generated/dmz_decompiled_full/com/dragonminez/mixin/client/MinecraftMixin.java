package com.dragonminez.mixin.client;

import com.dragonminez.client.animation.IPlayerAnimatable;
import com.dragonminez.client.collision.CollisionHelper;
import com.dragonminez.client.collision.TargetFinder;
import com.dragonminez.client.events.DMZClientEvent;
import com.dragonminez.common.combat.logic.player.PlayerAttackHelper;
import com.dragonminez.common.combat.logic.player.PlayerAttackProperties;
import com.dragonminez.common.combat.player.AttackHand;
import com.dragonminez.common.combat.util.Minecraft_DMZ;
import com.dragonminez.common.combat.util.SoundHelper;
import com.dragonminez.common.combat.weapon.WeaponAttributes;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.CombatAttackRequestC2S;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Minecraft.class})
public abstract class MinecraftMixin implements Minecraft_DMZ {
   @Shadow
   public LocalPlayer player;
   @Shadow
   public Screen screen;
   @Shadow
   public HitResult hitResult;
   @Unique
   private AttackHand upswingStack = null;
   @Unique
   private int upswingTicks = 0;
   @Unique
   private int lastAttacked = 0;
   @Unique
   private int lastSwingDuration = 0;
   @Unique
   private List<Entity> targetsInReach = null;
   @Unique
   private int itemUseCooldown = 0;
   @Unique
   private boolean isAttacking = false;
   @Unique
   private boolean isAwaitingUpswing = false;
   @Unique
   private boolean queuedAttack = false;
   @Unique
   private int queuedAttackTicks = 0;
   @Unique
   private static final float ATTACK_QUEUE_WINDOW_TICKS = 1.0F;
   @Unique
   private static final int ATTACK_QUEUE_EXPIRY_TICKS = 4;
   @Unique
   private static final float UPSWING_IMPACT_BIAS = 0.4F;
   @Unique
   private static final int BLOCK_MINE_ATTACK_GRACE = 5;
   @Unique
   private int lastBlockMineTick = -100;
   @Unique
   private static final double BLOCK_MINE_TARGET_BIAS = 0.25;

   @Shadow
   protected abstract boolean startAttack();

   @Inject(
      method = {"startAttack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dragonminez$startAttack(CallbackInfoReturnable<Boolean> cir) {
      if (this.player != null && this.screen == null) {
         boolean[] isDmzBlocking = new boolean[]{false};
         StatsProvider.get(StatsCapability.INSTANCE, this.player).ifPresent(data -> isDmzBlocking[0] = data.getStatus().isBlocking());
         if (!this.player.isBlocking() && !isDmzBlocking[0] && !PlayerAttackHelper.isChargingTechnique(this.player)) {
            int comboCount = this.getComboCount();
            AttackHand hand = PlayerAttackHelper.getCurrentAttack(this.player, comboCount);
            if (hand != null && PlayerAttackHelper.canAttack(this.player)) {
               if (this.shouldUseCombatAttack(hand)) {
                  cir.cancel();
                  cir.setReturnValue(false);
                  if (this.itemUseCooldown <= 0 && !this.isAttacking && !this.isAwaitingUpswing) {
                     float cooldownProgress = this.player.getAttackStrengthScale(0.5F);
                     if (cooldownProgress < 1.0F) {
                        float remainingTicks = (1.0F - cooldownProgress) * this.player.getCurrentItemAttackStrengthDelay();
                        if (remainingTicks <= 1.0F) {
                           this.queuedAttack = true;
                           this.queuedAttackTicks = 0;
                        }
                     } else {
                        this.queuedAttack = false;
                        this.isAttacking = true;
                        this.isAwaitingUpswing = true;
                        this.upswingStack = hand;
                        float cooldownTicks = PlayerAttackHelper.getAttackCooldownTicksCapped(this.player);
                        int swingAnimTicks = this.meleeAnimTicks(this.meleeAnimSpeed(cooldownTicks));
                        this.upswingTicks = Math.max(1, Math.round((float)swingAnimTicks * (float)hand.upswingRate() * 0.4F));
                        this.lastSwingDuration = swingAnimTicks;
                        this.lastAttacked = 0;
                        ((MinecraftAccessor)this).setAttackCooldown(10000);
                        DMZClientEvent.PlayerAttackStart event = new DMZClientEvent.PlayerAttackStart(this.player, hand);
                        NeoForge.EVENT_BUS.post(event);
                        this.playLocalAttackFeedback(hand);
                     }
                  }
               }
            }
         } else {
            cir.cancel();
            cir.setReturnValue(false);
         }
      }
   }

   @Unique
   private float meleeAnimSpeed(float cooldownTicks) {
      float speed = 12.0F / Math.max(cooldownTicks, 0.001F);
      return Math.max(0.55F, Math.min(1.35F, speed));
   }

   @Unique
   private int meleeAnimTicks(float animSpeed) {
      return Math.max(8, Math.round(12.0F / Math.max(animSpeed, 0.1F)));
   }

   @Unique
   private void playLocalAttackFeedback(AttackHand hand) {
      if (hand.attack() != null) {
         float animSpeedMultiplier = this.meleeAnimSpeed(PlayerAttackHelper.getAttackCooldownTicksCapped(this.player));
         ((IPlayerAnimatable)this.player).dragonminez$playMeleeAnimation(hand.attack().animation(), hand.isOffHand(), animSpeedMultiplier);
         WeaponAttributes.Sound swingSound = hand.attack().swingSound();
         SoundEvent soundEvent = SoundHelper.resolveSoundEvent(swingSound);
         if (soundEvent != null) {
            this.player
               .level()
               .playLocalSound(
                  this.player.getX(),
                  this.player.getY(),
                  this.player.getZ(),
                  soundEvent,
                  SoundSource.PLAYERS,
                  swingSound.volume(),
                  SoundHelper.computePitch(swingSound),
                  false
               );
         }
      }
   }

   @Inject(
      method = {"continueAttack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dragonminez$continueAttack(boolean leftClick, CallbackInfo ci) {
      if (leftClick && this.player != null) {
         if (PlayerAttackHelper.isChargingTechnique(this.player)) {
            ci.cancel();
         } else {
            int comboCount = this.getComboCount();
            AttackHand hand = PlayerAttackHelper.getCurrentAttack(this.player, comboCount);
            boolean canUseCombat = hand != null && PlayerAttackHelper.canAttack(this.player) && this.shouldUseCombatAttack(hand);
            if (!canUseCombat) {
               if (this.hitResult != null && this.hitResult.getType() == Type.BLOCK) {
                  this.lastBlockMineTick = this.player.tickCount;
               }
            } else {
               ci.cancel();
               if (this.player.tickCount - this.lastBlockMineTick > 5) {
                  float cooldownProgress = this.player.getAttackStrengthScale(0.5F);
                  if (cooldownProgress >= 1.0F && !this.isAttacking && !this.isAwaitingUpswing) {
                     this.startAttack();
                  }
               }
            }
         }
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")}
   )
   private void dragonminez$tick(CallbackInfo info) {
      if (this.itemUseCooldown > 0) {
         this.itemUseCooldown--;
      }

      this.lastAttacked++;
      if (this.player != null) {
         this.resetComboIfNeeded();
         if (this.upswingStack != null) {
            if (this.upswingTicks > 0) {
               this.upswingTicks--;
            } else {
               this.executeAttack();
               this.upswingStack = null;
               this.isAwaitingUpswing = false;
            }
         } else {
            this.isAttacking = false;
         }

         this.fireQueuedAttackIfReady();
         if (this.player.tickCount % 2 == 0) {
            this.evaluateTargetsInReach();
         }
      }
   }

   @Unique
   private void fireQueuedAttackIfReady() {
      if (this.queuedAttack) {
         if (!this.isAttacking && !this.isAwaitingUpswing) {
            if (++this.queuedAttackTicks > 4) {
               this.queuedAttack = false;
            } else if (this.screen == null && !(this.player.getAttackStrengthScale(0.5F) < 1.0F)) {
               this.queuedAttack = false;
               this.startAttack();
            }
         } else {
            this.queuedAttack = false;
         }
      }
   }

   @Unique
   private void resetComboIfNeeded() {
      int comboCount = this.getComboCount();
      if (comboCount > 0) {
         if (!this.isAttacking && !this.isAwaitingUpswing) {
            int cooldownTicks = (int)Math.ceil((double)PlayerAttackHelper.getAttackCooldownTicksCapped(this.player));
            int comboResetWindow = cooldownTicks + 30;
            if (this.lastAttacked > comboResetWindow) {
               ((PlayerAttackProperties)this.player).setComboCount(0);
            }
         }
      }
   }

   @Unique
   private void executeAttack() {
      Entity cursorTarget = this.getCursorTarget();
      double attackRange = PlayerAttackHelper.getEffectiveAttackRange(this.player, this.upswingStack.attributes().attackRange());
      TargetFinder.TargetResult targetResult = TargetFinder.findAttackTargetResult(this.player, cursorTarget, this.upswingStack.attack(), attackRange);
      DMZClientEvent.PlayerAttackHit event = new DMZClientEvent.PlayerAttackHit(this.player, this.upswingStack, targetResult.entities, cursorTarget);
      NeoForge.EVENT_BUS.post(event);
      int[] entityIds = targetResult.entities.stream().mapToInt(Entity::getId).toArray();
      int comboCount = this.getComboCount();
      boolean sneaking = this.player.hasPose(Pose.CROUCHING);
      int slot = this.player.getInventory().selected;
      NetworkHandler.sendToServer(new CombatAttackRequestC2S(comboCount, sneaking, slot, entityIds));
      int nextComboCount = comboCount + 1;
      ((PlayerAttackProperties)this.player).setComboCount(nextComboCount);
      this.player.resetAttackStrengthTicker();
      this.setMiningCooldown(Math.max(2, Math.round(PlayerAttackHelper.getAttackCooldownTicksCapped(this.player))));
   }

   @Unique
   private void evaluateTargetsInReach() {
      int comboCount = this.getComboCount();
      AttackHand hand = PlayerAttackHelper.getCurrentAttack(this.player, comboCount);
      if (hand == null) {
         this.targetsInReach = null;
      } else {
         Entity cursorTarget = this.getCursorTarget();
         double attackRange = PlayerAttackHelper.getEffectiveAttackRange(this.player, hand.attributes().attackRange());
         TargetFinder.TargetResult targetResult = TargetFinder.findAttackTargetResult(this.player, cursorTarget, hand.attack(), attackRange);
         this.targetsInReach = targetResult.entities;
      }
   }

   @Unique
   private void setMiningCooldown(int ticks) {
      ((MinecraftAccessor)this).setAttackCooldown(ticks);
   }

   @Unique
   private List<Entity> collectAttackTargets(AttackHand hand) {
      if (this.hasTargetsInReach()) {
         return this.targetsInReach;
      } else {
         Entity cursorTarget = this.getCursorTarget();
         double attackRange = PlayerAttackHelper.getEffectiveAttackRange(this.player, hand.attributes().attackRange());
         return TargetFinder.findAttackTargetResult(this.player, cursorTarget, hand.attack(), attackRange).entities;
      }
   }

   @Unique
   private boolean hasTargetsForAttack(AttackHand hand) {
      return !this.collectAttackTargets(hand).isEmpty();
   }

   @Unique
   private boolean hasTargetInFrontOfBlock(AttackHand hand) {
      List<Entity> targets = this.collectAttackTargets(hand);
      if (targets.isEmpty()) {
         return false;
      } else {
         Vec3 eye = this.player.getEyePosition();
         double blockDistance = this.hitResult.getLocation().distanceTo(eye);

         for (Entity target : targets) {
            if (CollisionHelper.distance(eye, target.getBoundingBox()) + 0.25 < blockDistance) {
               return true;
            }
         }

         return false;
      }
   }

   @Unique
   private boolean shouldUseCombatAttack(AttackHand hand) {
      return this.hitResult != null && this.hitResult.getType() == Type.BLOCK ? this.hasTargetInFrontOfBlock(hand) : true;
   }

   @Override
   public int getComboCount() {
      return this.player != null ? ((PlayerAttackProperties)this.player).getComboCount() : 0;
   }

   @Override
   public boolean hasTargetsInReach() {
      return this.targetsInReach != null && !this.targetsInReach.isEmpty();
   }

   @Override
   public float getSwingProgress() {
      return this.lastAttacked <= this.lastSwingDuration && this.lastSwingDuration > 0 ? (float)this.lastAttacked / (float)this.lastSwingDuration : 1.0F;
   }

   @Override
   public int getUpswingTicks() {
      return this.upswingTicks;
   }

   @Override
   public void cancelUpswing() {
      this.upswingStack = null;
      this.queuedAttack = false;
      this.itemUseCooldown = 0;
      this.setMiningCooldown(0);
      this.isAwaitingUpswing = false;
      this.isAttacking = false;
   }
}
