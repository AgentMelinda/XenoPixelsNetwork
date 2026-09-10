package com.dragonminez.client.gui.radial.nodes;

import com.dragonminez.client.gui.radial.RadialNode;
import com.dragonminez.common.stats.StatsData;
import java.util.List;
import net.minecraft.network.chat.Component;

public class MoreFormsNode extends CategoryNode {
   public MoreFormsNode() {
      super(Component.translatable("gui.dragonminez.radial.extraforms"), icon("godforms"));
   }

   @Override
   protected List<RadialNode> buildChildren(StatsData stats) {
      return RadialForms.moreForms(stats);
   }
}
