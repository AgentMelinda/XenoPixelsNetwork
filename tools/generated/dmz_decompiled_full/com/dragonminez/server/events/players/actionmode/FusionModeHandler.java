package com.dragonminez.server.events.players.actionmode;

import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.extras.ActionMode;
import com.dragonminez.server.events.players.IActionModeHandler;
import com.dragonminez.server.util.FusionLogic;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class FusionModeHandler implements IActionModeHandler {
   @Override
   public boolean canCharge(ServerPlayer player, StatsData data) {
      return data.getSkills().hasSkill("fusion")
         && !data.getCooldowns().hasCooldown("CombatTimer")
         && !data.getCooldowns().hasCooldown("FusionCooldown")
         && !data.getStatus().isFused();
   }

   @Override
   public int handleActionCharge(ServerPlayer player, StatsData data) {
      return this.canCharge(player, data) ? 10 : 0;
   }

   @Override
   public boolean performAction(ServerPlayer player, StatsData data) {
      return attemptFusion(player, data);
   }

   private static boolean attemptFusion(ServerPlayer player, StatsData data) {
      for (ServerPlayer target : player.level().getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(5.0), p -> p != player)) {
         StatsProvider.get(StatsCapability.INSTANCE, target)
            .ifPresent(
               targetData -> {
                  if (targetData.getStatus().getSelectedAction() == ActionMode.FUSION
                     && targetData.getResources().getActionCharge() >= 50
                     && targetData.getStatus().isActionCharging()
                     && data.getResources().getActionCharge() >= 100
                     && FusionLogic.executeMetamoru(player, target, data, targetData)) {
                     data.getResources().setActionCharge(0);
                     targetData.getResources().setActionCharge(0);
                     player.level()
                        .playSound(null, player.getX(), player.getY(), player.getZ(), (SoundEvent)MainSounds.FUSION.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                  }
               }
            );
         if (data.getStatus().isFused()) {
            return true;
         }
      }

      return false;
   }
}
