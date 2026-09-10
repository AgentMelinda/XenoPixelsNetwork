package com.dragonminez.mixin.client;

import com.dragonminez.common.combat.logic.player.PlayerAttackHelper;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.mixin.common.LivingEntityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({MultiPlayerGameMode.class})
public class MultiPlayerGameModeMixin {
   @Shadow
   @Final
   private Minecraft minecraft;

   @Inject(
      method = {"stopDestroyBlock"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;resetAttackStrengthTicker()V",
         shift = Shift.AFTER
      )}
   )
   private void dragonminez$stopDestroyBlock_FixAttackCD(CallbackInfo ci) {
      try {
         LocalPlayer player = this.minecraft.player;
         if (player == null) {
            return;
         }

         float cooldownLength = PlayerAttackHelper.getAttackCooldownTicksCapped(player);
         float typicalUpswing = 0.5F;
         float upswingMultiplier = ConfigManager.getCombatConfig().getUpswingMultiplier();
         int reducedCooldown = Math.round(cooldownLength * typicalUpswing * upswingMultiplier);
         ((LivingEntityAccessor)player).setLastHurtByPlayerTime(reducedCooldown);
      } catch (Exception var7) {
      }
   }
}
