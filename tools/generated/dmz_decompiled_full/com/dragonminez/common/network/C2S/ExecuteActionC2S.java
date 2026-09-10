package com.dragonminez.common.network.C2S;

import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.extras.ActionMode;
import com.dragonminez.common.util.TransformationsHelper;
import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class ExecuteActionC2S {
   private final ExecuteActionC2S.ActionType action;
   private final boolean rightClick;

   public ExecuteActionC2S(ExecuteActionC2S.ActionType action) {
      this(action, false);
   }

   public ExecuteActionC2S(ExecuteActionC2S.ActionType action, boolean rightClick) {
      this.action = action;
      this.rightClick = rightClick;
   }

   public ExecuteActionC2S(FriendlyByteBuf buffer) {
      this.action = (ExecuteActionC2S.ActionType)buffer.readEnum(ExecuteActionC2S.ActionType.class);
      this.rightClick = buffer.readBoolean();
   }

   public void encode(FriendlyByteBuf buffer) {
      buffer.writeEnum(this.action);
      buffer.writeBoolean(this.rightClick);
   }

   public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(
         () -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
               if (player.hasEffect(MainEffects.STUN)) {
                  return;
               }

               StatsProvider.get(StatsCapability.INSTANCE, player)
                  .ifPresent(
                     data -> {
                        boolean needsSync = false;
                        switch (this.action) {
                           case FORCE_DESCEND:
                              if (this.rightClick) {
                                 data.getCharacter().clearActiveStackForm(player);
                                 TransformationsHelper.revertToBaseForm(player, data);
                              } else {
                                 boolean activeStackForm = data.getCharacter().getActiveStackForm() != null
                                    && !data.getCharacter().getActiveStackForm().isEmpty();
                                 boolean activeForm = data.getCharacter().getActiveForm() != null && !data.getCharacter().getActiveForm().isEmpty();
                                 if (!activeForm && !activeStackForm
                                    || data.getStatus().isAndroidUpgraded() && "androidbase".equalsIgnoreCase(data.getCharacter().getActiveForm())) {
                                    data.getResources().setPowerRelease(0);
                                 }

                                 if (activeStackForm) {
                                    descendStackForm(player, data);
                                 } else if (activeForm) {
                                    descendForm(player, data);
                                 }

                                 needsSync = true;
                              }
                              break;
                           case INSTANT_TRANSFORM:
                              if (data.getStatus().getSelectedAction() == ActionMode.STACK) {
                                 needsSync = instantTransformStackForm(player, data);
                              } else {
                                 needsSync = instantTransformForm(player, data);
                              }
                              break;
                           case TOGGLE_TAIL:
                              data.getStatus().setTailVisible(!data.getStatus().isTailVisible());
                              needsSync = true;
                              break;
                           case TOGGLE_AURA:
                              if (data.getSkills().hasSkill("kicontrol")) {
                                 data.getStatus().setPermanentAura(!data.getStatus().isPermanentAura());
                                 needsSync = true;
                              }
                              break;
                           case TOGGLE_FRIENDLY_FIST:
                              if (data.getSkills().hasSkill("kicontrol")) {
                                 data.getStatus().setFriendlyFistEnabled(!data.getStatus().isFriendlyFistEnabled());
                              }
                              break;
                           case INSTANT_RELEASE:
                              int potentialUnlockLevel = data.getSkills().hasSkill("potentialunlock") ? data.getSkills().getSkillLevel("potentialunlock") : 0;
                              int maxRelease = 50 + potentialUnlockLevel * 5;
                              int releaseLimit = data.getResources().getReleaseLimit();
                              if (releaseLimit > 0) {
                                 maxRelease = Math.min(maxRelease, releaseLimit);
                              }

                              int currentRelease = data.getResources().getPowerRelease();
                              if (currentRelease < maxRelease) {
                                 int amountToIncrease = maxRelease - currentRelease;
                                 double percentageCost = (double)amountToIncrease / 5.0 * 0.01;
                                 int energyCost = (int)((double)data.getMaxEnergy() * percentageCost);
                                 if (data.getResources().getCurrentEnergy() >= (float)energyCost) {
                                    data.getResources().removeEnergy((float)energyCost);
                                    data.getResources().setPowerRelease(maxRelease);
                                    needsSync = true;
                                 }
                              }
                        }

                        player.refreshDimensions();
                        if (needsSync) {
                           NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
                        }
                     }
                  );
            }
         }
      );
      context.setPacketHandled(true);
   }

   private static boolean instantTransformForm(ServerPlayer player, StatsData data) {
      FormConfig.FormData nextForm = TransformationsHelper.getNextAvailableForm(data);
      if (nextForm == null) {
         return false;
      } else if (TransformationsHelper.isOozaruForm(nextForm)) {
         return false;
      } else {
         String group = TransformationsHelper.getTransformTargetGroup(data);
         if (TransformationsHelper.needsFreeTransformMastery(data) && !TransformationsHelper.meetsFreeTransformMastery(data)) {
            String jumpRace = data.getCharacter().getRaceName();
            Component targetName = Component.translatable("race.dragonminez." + jumpRace + ".form." + group + "." + nextForm.getName());
            player.displayClientMessage(
               Component.translatable(
                  "message.dragonminez.form.free_transform_mastery", new Object[]{(int)Math.round(nextForm.getAllowFreeTransformOnMastery()), targetName}
               ),
               true
            );
            return false;
         } else {
            if (data.getCharacter().hasActiveStackForm()) {
               FormConfig.FormData activeStackData = data.getCharacter().getActiveStackFormData();
               if (activeStackData != null) {
                  boolean isFormStackable = nextForm.getFormStackable();
                  boolean isStackStackable = activeStackData.getFormStackable();
                  double baseMastery = data.getCharacter().getFormMasteries().getMastery(group, nextForm.getName());
                  double stackMastery = data.getCharacter()
                     .getStackFormMasteries()
                     .getMastery(data.getCharacter().getActiveStackFormGroup(), data.getCharacter().getActiveStackForm());
                  boolean meetsStackMastery = baseMastery >= nextForm.getStackOnMastery() && stackMastery >= activeStackData.getStackOnMastery();
                  boolean compatible = TransformationsHelper.areFormsCompatible(nextForm, group, activeStackData, data.getCharacter().getActiveStackFormGroup());
                  if (!isFormStackable || !isStackStackable || !meetsStackMastery || !compatible) {
                     data.getCharacter().clearActiveStackForm(player);
                     player.removeEffect(MainEffects.STACK_TRANSFORMED);
                     player.sendSystemMessage(Component.translatable("message.dragonminez.form.stack_removed"));
                  }
               }
            }

            double mastery = data.getCharacter().getFormMasteries().getMastery(group, nextForm.getName());
            if (!player.isCreative() && mastery < nextForm.getInstantTransformOnMastery()) {
               return false;
            } else {
               int cost = (int)(data.getAdjustedEnergyDrain() * 4.0);
               if (player.isCreative()) {
                  cost = 0;
               }

               if (data.getResources().getCurrentEnergy() < (float)cost) {
                  player.displayClientMessage(Component.translatable("message.dragonminez.form.no_ki_instant", new Object[]{cost}), true);
                  return false;
               } else {
                  data.getResources().removeEnergy((float)cost);
                  float[] resourceSnapshot = data.snapshotMultiplierResources();
                  data.getCharacter().recordPreviousForm();
                  data.getCharacter().setActiveForm(group, nextForm.getName());
                  if (!data.getCharacter().getFormsUsedBefore().getFormGroup(group).contains(nextForm.getName())) {
                     data.getCharacter().getFormsUsedBefore().putForm(group, nextForm.getName());
                  }

                  data.restoreMultiplierGains(player, resourceSnapshot);
                  playFormSound(player, (SoundEvent)MainSounds.INSTA_FORM_ON.get());
                  return true;
               }
            }
         }
      }
   }

   private static boolean instantTransformStackForm(ServerPlayer player, StatsData data) {
      FormConfig.FormData nextForm = TransformationsHelper.getNextAvailableStackForm(data);
      if (nextForm == null) {
         return false;
      } else if (!nextForm.getFormStackable()) {
         player.displayClientMessage(Component.translatable("message.dragonminez.form.not_stackable"), true);
         return false;
      } else {
         String group = data.getCharacter().hasActiveStackForm()
            ? data.getCharacter().getActiveStackFormGroup()
            : data.getCharacter().getSelectedStackFormGroup();
         if (data.getCharacter().hasActiveForm()) {
            FormConfig.FormData activeFormData = data.getCharacter().getActiveFormData();
            if (activeFormData == null
               || !activeFormData.getFormStackable()
               || !TransformationsHelper.areFormsCompatible(activeFormData, data.getCharacter().getActiveFormGroup(), nextForm, group)) {
               player.displayClientMessage(Component.translatable("message.dragonminez.form.not_stackable"), true);
               return false;
            }

            double baseMastery = data.getCharacter()
               .getFormMasteries()
               .getMastery(data.getCharacter().getActiveFormGroup(), data.getCharacter().getActiveForm());
            double stackMastery = data.getCharacter().getStackFormMasteries().getMastery(group, nextForm.getName());
            if (baseMastery < activeFormData.getStackOnMastery() || stackMastery < nextForm.getStackOnMastery()) {
               player.displayClientMessage(Component.translatable("message.dragonminez.form.not_stackable"), true);
               return false;
            }
         }

         double mastery = data.getCharacter().getStackFormMasteries().getMastery(group, nextForm.getName());
         if (!player.isCreative() && mastery < nextForm.getInstantTransformOnMastery()) {
            return false;
         } else {
            int cost = (int)(data.getAdjustedEnergyDrain() * 4.0);
            if (player.isCreative()) {
               cost = 0;
            }

            if (data.getResources().getCurrentEnergy() < (float)cost) {
               player.displayClientMessage(Component.translatable("message.dragonminez.form.no_ki_instant", new Object[]{cost}), true);
               return false;
            } else {
               data.getResources().removeEnergy((float)cost);
               float[] resourceSnapshot = data.snapshotMultiplierResources();
               data.getCharacter().recordPreviousStackForm();
               data.getCharacter().setActiveStackForm(group, nextForm.getName());
               if (!data.getCharacter().getStackFormsUsedBefore().getFormGroup(group).contains(nextForm.getName())) {
                  data.getCharacter().getStackFormsUsedBefore().putForm(group, nextForm.getName());
               }

               data.restoreMultiplierGains(player, resourceSnapshot);
               playFormSound(player, (SoundEvent)MainSounds.INSTA_FORM_ON.get());
               if ("kaioken".equalsIgnoreCase(group)) {
                  playFormSound(player, (SoundEvent)MainSounds.STACK_FORM.get());
               }

               return true;
            }
         }
      }
   }

   private static void descendForm(ServerPlayer player, StatsData data) {
      if (data.getCharacter().isHasPreviousFormRecord()) {
         String previousGroup = data.getCharacter().getPreviousFormGroup();
         String previousForm = data.getCharacter().getPreviousForm();
         data.getCharacter().clearPreviousFormRecord();
         if (previousForm != null && !previousForm.isEmpty()) {
            data.getCharacter().setActiveForm(previousGroup, previousForm);
            playFormSound(player, (SoundEvent)MainSounds.INSTA_FORM_OFF.get());
         } else {
            TransformationsHelper.revertToBaseForm(player, data);
            player.removeEffect(MainEffects.TRANSFORMED);
         }
      } else {
         FormConfig.FormData previousForm = TransformationsHelper.getPreviousForm(data);
         if (previousForm != null) {
            data.getCharacter().setActiveForm(data.getCharacter().getActiveFormGroup(), previousForm.getName());
            playFormSound(player, (SoundEvent)MainSounds.INSTA_FORM_OFF.get());
         } else {
            TransformationsHelper.revertToBaseForm(player, data);
            player.removeEffect(MainEffects.TRANSFORMED);
         }
      }
   }

   private static void descendStackForm(ServerPlayer player, StatsData data) {
      if (data.getCharacter().isHasPreviousStackFormRecord()) {
         String previousGroup = data.getCharacter().getPreviousStackFormGroup();
         String previousForm = data.getCharacter().getPreviousStackForm();
         data.getCharacter().clearPreviousStackFormRecord();
         if (previousForm != null && !previousForm.isEmpty()) {
            data.getCharacter().setActiveStackForm(previousGroup, previousForm);
            playFormSound(player, (SoundEvent)MainSounds.INSTA_FORM_OFF.get());
         } else {
            data.getCharacter().clearActiveStackForm(player);
            player.removeEffect(MainEffects.STACK_TRANSFORMED);
         }
      } else {
         FormConfig.FormData previousForm = TransformationsHelper.getPreviousStackForm(data);
         if (previousForm != null) {
            data.getCharacter().setActiveStackForm(data.getCharacter().getActiveStackFormGroup(), previousForm.getName());
            playFormSound(player, (SoundEvent)MainSounds.INSTA_FORM_OFF.get());
         } else {
            data.getCharacter().clearActiveStackForm(player);
            player.removeEffect(MainEffects.STACK_TRANSFORMED);
         }
      }
   }

   private static void playFormSound(ServerPlayer player, SoundEvent sound) {
      player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
   }

   public static enum ActionType {
      FORCE_DESCEND,
      INSTANT_TRANSFORM,
      TOGGLE_TAIL,
      TOGGLE_AURA,
      TOGGLE_FRIENDLY_FIST,
      INSTANT_RELEASE;
   }
}
