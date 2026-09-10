package com.dragonminez.server.events.players.actionmode;

import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.util.TransformationItemCostHelper;
import com.dragonminez.common.util.TransformationsHelper;
import com.dragonminez.server.events.players.IActionModeHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;

public class FormModeHandler implements IActionModeHandler {
   @Override
   public boolean canCharge(ServerPlayer player, StatsData data) {
      FormConfig.FormData nextForm = TransformationsHelper.getNextAvailableForm(data);
      if (nextForm == null) {
         return false;
      } else {
         return TransformationsHelper.isOozaruForm(nextForm) ? TransformationsHelper.shouldAutoChargeOozaru(player, data) : true;
      }
   }

   @Override
   public int handleActionCharge(ServerPlayer player, StatsData data) {
      FormConfig.FormData nextForm = TransformationsHelper.getNextAvailableForm(data);
      if (nextForm != null) {
         String group = TransformationsHelper.getTransformTargetGroup(data);
         int mastery = (int)data.getCharacter().getFormMasteries().getMastery(group, nextForm.getName());
         return 10 + Math.min(15, (int)((double)mastery * 0.2));
      } else {
         return 0;
      }
   }

   @Override
   public boolean performAction(ServerPlayer player, StatsData data) {
      attemptTransform(player, data);
      return true;
   }

   private static void attemptTransform(ServerPlayer player, StatsData data) {
      FormConfig.FormData nextForm = TransformationsHelper.getNextAvailableForm(data);
      if (nextForm != null) {
         String targetGroup = TransformationsHelper.getTransformTargetGroup(data);
         if (TransformationsHelper.needsFreeTransformMastery(data) && !TransformationsHelper.meetsFreeTransformMastery(data)) {
            String jumpRace = data.getCharacter().getRaceName();
            Component targetName = Component.translatable("race.dragonminez." + jumpRace + ".form." + targetGroup + "." + nextForm.getName());
            player.displayClientMessage(
               Component.translatable(
                  "message.dragonminez.form.free_transform_mastery", new Object[]{(int)Math.round(nextForm.getAllowFreeTransformOnMastery()), targetName}
               ),
               true
            );
         } else {
            if (data.getCharacter().hasActiveStackForm()) {
               FormConfig.FormData activeStackData = data.getCharacter().getActiveStackFormData();
               if (activeStackData != null) {
                  boolean isFormStackable = nextForm.getFormStackable();
                  boolean isStackStackable = activeStackData.getFormStackable();
                  double baseMastery = data.getCharacter().getFormMasteries().getMastery(targetGroup, nextForm.getName());
                  double stackMastery = data.getCharacter()
                     .getStackFormMasteries()
                     .getMastery(data.getCharacter().getActiveStackFormGroup(), data.getCharacter().getActiveStackForm());
                  boolean meetsStackMastery = baseMastery >= nextForm.getStackOnMastery() && stackMastery >= activeStackData.getStackOnMastery();
                  boolean compatible = TransformationsHelper.areFormsCompatible(
                     nextForm, targetGroup, activeStackData, data.getCharacter().getActiveStackFormGroup()
                  );
                  if (!isFormStackable || !isStackStackable || !meetsStackMastery || !compatible) {
                     data.getCharacter().clearActiveStackForm(player);
                     player.removeEffect(MainEffects.STACK_TRANSFORMED);
                     player.sendSystemMessage(Component.translatable("message.dragonminez.form.stack_removed"));
                  }
               }
            }

            int energyCost = (int)((double)data.getMaxEnergy() * 0.1 * nextForm.getEnergyDrain());
            int staminaCost = (int)((double)data.getMaxStamina() * 0.1 * nextForm.getStaminaDrain());
            int healthCost = (int)((double)data.getMaxHealth() * 0.1 * nextForm.getHealthDrain());
            boolean hasEnoughEnergy = data.getResources().getCurrentEnergy() >= (float)energyCost;
            boolean hasEnoughStamina = data.getResources().getCurrentStamina() >= (float)staminaCost;
            boolean hasEnoughHealth = data.getPlayer().getHealth() >= (float)healthCost;
            if (!hasEnoughEnergy) {
               player.displayClientMessage(Component.translatable("message.dragonminez.form.no_ki", new Object[]{energyCost}), true);
            }

            if (!hasEnoughStamina) {
               player.displayClientMessage(Component.translatable("message.dragonminez.form.no_stamina", new Object[]{staminaCost}), true);
            }

            if (!hasEnoughHealth) {
               player.displayClientMessage(Component.translatable("message.dragonminez.form.no_health", new Object[]{healthCost}), true);
            }

            if (hasEnoughEnergy && hasEnoughStamina && hasEnoughHealth) {
               if (!TransformationItemCostHelper.canAffordAndHandleTriggerCost(player, nextForm)) {
                  player.displayClientMessage(Component.translatable("message.dragonminez.form.no_trigger_item"), true);
                  return;
               }

               if (!data.getCharacter().getFormsUsedBefore().getFormGroup(targetGroup).contains(nextForm.getName())) {
                  data.getCharacter().getFormsUsedBefore().putForm(targetGroup, nextForm.getName());
               }

               float[] resourceSnapshot = data.snapshotMultiplierResources();
               data.getCharacter().recordPreviousForm();
               data.getCharacter().setActiveForm(targetGroup, nextForm.getName());
               data.restoreMultiplierGains(player, resourceSnapshot);
               TransformationItemCostHelper.clearFormDurationSecondsRemaining(player);
               player.refreshDimensions();
               player.level()
                  .playSound(null, player.getX(), player.getY(), player.getZ(), (SoundEvent)MainSounds.TRANSFORM_ON.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
               String race = data.getCharacter().getRaceName();
               Component translatedFormName = Component.translatable("race.dragonminez." + race + ".form." + targetGroup + "." + nextForm.getName());
               if (data.getCharacter().getActiveStackForm() != null && !data.getCharacter().getActiveStackForm().isEmpty()) {
                  Component translatedStackFormGroup = Component.translatable("race.dragonminez.stack.group." + data.getCharacter().getSelectedStackFormGroup());
                  Component translatedStackFormName = Component.translatable(
                     "race.dragonminez.stack.form." + data.getCharacter().getActiveStackFormGroup() + "." + data.getCharacter().getActiveStackForm()
                  );
                  translatedFormName = Component.empty()
                     .append(translatedFormName)
                     .append(Component.literal(" x "))
                     .append(translatedStackFormGroup)
                     .append(Component.literal(" "))
                     .append(translatedStackFormName);
               }

               if (!player.hasEffect(MainEffects.TRANSFORMED)) {
                  player.addEffect(new MobEffectInstance(MainEffects.TRANSFORMED, -1, 0, false, false, true));
               }

               player.sendSystemMessage(Component.translatable("message.dragonminez.transformation", new Object[]{translatedFormName}), true);
               player.refreshDimensions();
            }
         }
      }
   }
}
