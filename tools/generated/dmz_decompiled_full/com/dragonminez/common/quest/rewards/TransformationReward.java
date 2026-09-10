package com.dragonminez.common.quest.rewards;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.quest.QuestReward;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.FormMasteries;
import com.dragonminez.common.util.TransformationsHelper;
import lombok.Generated;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class TransformationReward extends QuestReward {
   private final String formGroup;
   private final String formName;
   private final double mastery;
   private final boolean stack;

   public TransformationReward(String formGroup, String formName, double mastery, boolean stack) {
      super(QuestReward.RewardType.TRANSFORMATION);
      this.formGroup = formGroup;
      this.formName = formName;
      this.mastery = mastery;
      this.stack = stack;
   }

   @Override
   public void giveReward(ServerPlayer player) {
      StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
         Character character = data.getCharacter();
         this.grantFormSkill(data, character.getRaceName());
         FormMasteries masteries = this.stack ? character.getStackFormMasteries() : character.getFormMasteries();
         double current = masteries.getMastery(this.formGroup, this.formName);
         if (this.mastery > current) {
            masteries.setMastery(this.formGroup, this.formName, this.mastery, Double.MAX_VALUE);
         }

         this.grantRequisiteChain(character, character.getRaceName(), this.formGroup, this.formName);
      });
   }

   private void grantRequisiteChain(Character character, String raceName, String group, String form) {
      FormConfig config = ConfigManager.getFormGroup(raceName, group);
      if (config == null) {
         config = ConfigManager.getStackFormGroup(group);
      }

      if (config != null) {
         FormConfig.FormData formData = config.getForm(form);
         if (formData != null) {
            String req = formData.getFormRequisite();
            double need = formData.getUnlockOnMastery();
            if (!req.isEmpty() && !(need <= 0.0)) {
               for (String token : req.split(",")) {
                  String entry = token.trim();
                  int dot = entry.indexOf(46);
                  if (dot > 0 && dot < entry.length() - 1) {
                     String reqGroup = entry.substring(0, dot);
                     String reqForm = entry.substring(dot + 1);
                     FormMasteries reqMasteries = character.getFormMasteries();
                     if (reqMasteries.getMastery(reqGroup, reqForm) < need) {
                        reqMasteries.setMastery(reqGroup, reqForm, need, Double.MAX_VALUE);
                     }

                     this.grantRequisiteChain(character, raceName, reqGroup, reqForm);
                  }
               }
            }
         }
      }
   }

   private void grantFormSkill(StatsData data, String raceName) {
      FormConfig config = this.stack ? ConfigManager.getStackFormGroup(this.formGroup) : ConfigManager.getFormGroup(raceName, this.formGroup);
      if (config != null) {
         String formType = config.getFormType();
         String skillName = this.stack ? formType.toLowerCase() : TransformationsHelper.getSkillNameForType(formType);
         if (skillName != null && !skillName.isEmpty()) {
            FormConfig.FormData formData = config.getForm(this.formName);
            int targetLevel = formData != null && formData.getUnlockOnSkillLevel() != null ? formData.getUnlockOnSkillLevel() : 0;
            if (!data.getSkills().hasSkill(skillName) || data.getSkills().getSkillLevel(skillName) < targetLevel) {
               data.getSkills().setSkillLevel(skillName, targetLevel);
            }
         }
      }
   }

   @Override
   public Component getDescription() {
      return Component.translatable("gui.dragonminez.quests.rewards.transformation", new Object[]{Component.literal(this.formName.replace('_', ' '))});
   }

   @Generated
   public String getFormGroup() {
      return this.formGroup;
   }

   @Generated
   public String getFormName() {
      return this.formName;
   }

   @Generated
   public double getMastery() {
      return this.mastery;
   }

   @Generated
   public boolean isStack() {
      return this.stack;
   }
}
