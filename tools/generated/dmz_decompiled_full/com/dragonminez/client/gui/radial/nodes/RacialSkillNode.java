package com.dragonminez.client.gui.radial.nodes;

import com.dragonminez.client.gui.radial.AbstractRadialNode;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.ExecuteActionC2S;
import com.dragonminez.common.network.C2S.SwitchActionC2S;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.extras.ActionMode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class RacialSkillNode extends AbstractRadialNode {
   private boolean isTailRace(StatsData stats) {
      String race = stats.getCharacter().getRaceName();
      String form = stats.getCharacter().getActiveForm();
      return "saiyan".equals(race) || stats.getCharacter().isHasSaiyanTail() || form != null && form.contains("oozaru");
   }

   private String racialSkill(StatsData stats) {
      String race = stats.getCharacter().getRaceName();
      return ConfigManager.getRaceCharacter(race) == null ? "" : ConfigManager.getRaceCharacter(race).getRacialSkill();
   }

   private boolean isActionRacial(StatsData stats) {
      String skill = this.racialSkill(stats);
      return "namekian".equals(skill) || "bioandroid".equals(skill) || "majin".equals(skill);
   }

   @Override
   public Component label(StatsData stats) {
      if (this.isActionRacial(stats)) {
         return Component.translatable("gui.action.dragonminez.racial." + this.racialSkill(stats));
      } else {
         return this.isTailRace(stats) ? Component.translatable("gui.action.dragonminez.tail") : Component.empty();
      }
   }

   @Override
   public ResourceLocation icon(StatsData stats) {
      return icon("racial");
   }

   @Override
   public boolean visible(StatsData stats) {
      if (stats.getCharacter() == null) {
         return false;
      } else {
         String race = stats.getCharacter().getRaceName();
         return race != null && !race.isEmpty() ? this.isTailRace(stats) || this.isActionRacial(stats) : false;
      }
   }

   @Override
   public boolean active(StatsData stats) {
      if (this.isActionRacial(stats)) {
         return stats.getStatus().getSelectedAction() == ActionMode.RACIAL;
      } else {
         return this.isTailRace(stats) ? stats.getStatus().isTailVisible() : false;
      }
   }

   @Override
   public int labelColor(StatsData stats) {
      return this.active(stats) ? 2883328 : 16718592;
   }

   @Override
   public void onSelect(StatsData stats) {
      if (this.isActionRacial(stats)) {
         boolean wasActive = stats.getStatus().getSelectedAction() == ActionMode.RACIAL;
         NetworkHandler.sendToServer(new SwitchActionC2S(ActionMode.RACIAL));
         this.playToggle(!wasActive);
      } else if (this.isTailRace(stats)) {
         boolean wasActive = stats.getStatus().isTailVisible();
         NetworkHandler.sendToServer(new ExecuteActionC2S(ExecuteActionC2S.ActionType.TOGGLE_TAIL));
         this.playToggle(!wasActive);
      }
   }
}
