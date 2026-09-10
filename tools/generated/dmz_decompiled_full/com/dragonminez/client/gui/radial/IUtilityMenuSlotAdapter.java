package com.dragonminez.client.gui.radial;

import com.dragonminez.client.gui.utilitymenu.ButtonInfo;
import com.dragonminez.client.gui.utilitymenu.IUtilityMenuSlot;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class IUtilityMenuSlotAdapter extends AbstractRadialNode {
   private final IUtilityMenuSlot slot;

   public IUtilityMenuSlotAdapter(IUtilityMenuSlot slot) {
      this.slot = slot;
   }

   @Override
   public Component label(StatsData stats) {
      ButtonInfo info = this.slot.render(stats);
      return (Component)(info != null ? info.getLine1() : Component.empty());
   }

   @Override
   public ResourceLocation icon(StatsData stats) {
      return PLACEHOLDER;
   }

   @Override
   public boolean visible(StatsData stats) {
      ButtonInfo info = this.slot.render(stats);
      return info != null && info.hasContent();
   }

   @Override
   public boolean active(StatsData stats) {
      ButtonInfo info = this.slot.render(stats);
      return info != null && info.isSelected();
   }

   @Override
   public int labelColor(StatsData stats) {
      return this.active(stats) ? 2883328 : 16777215;
   }

   @Override
   public void onSelect(StatsData stats) {
      this.slot.handle(stats, false);
      this.playClick();
   }
}
