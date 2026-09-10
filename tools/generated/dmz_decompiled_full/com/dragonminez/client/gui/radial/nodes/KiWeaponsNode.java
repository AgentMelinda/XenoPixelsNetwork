package com.dragonminez.client.gui.radial.nodes;

import com.dragonminez.client.gui.radial.RadialNode;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.stats.StatsData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

public class KiWeaponsNode extends CategoryNode {
   public KiWeaponsNode() {
      super(Component.translatable("gui.dragonminez.radial.kiweapons"), icon("kiweapon"));
   }

   @Override
   public boolean visible(StatsData stats) {
      return stats.getSkills().hasSkill("kimanipulation") && stats.getSkills().hasSkill("kicontrol") ? !this.children(stats).isEmpty() : false;
   }

   @Override
   protected List<RadialNode> buildChildren(StatsData stats) {
      List<RadialNode> out = new ArrayList<>();

      for (String type : ConfigManager.getCombatConfig().getKiWeaponTypes()) {
         out.add(new KiWeaponNode(type));
      }

      return out;
   }
}
