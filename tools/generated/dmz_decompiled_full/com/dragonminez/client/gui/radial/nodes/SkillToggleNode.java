package com.dragonminez.client.gui.radial.nodes;

import com.dragonminez.client.gui.radial.AbstractRadialNode;
import com.dragonminez.common.stats.StatsData;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class SkillToggleNode extends AbstractRadialNode {
   private final Component label;
   private final ResourceLocation icon;
   private final Predicate<StatsData> has;
   private final Predicate<StatsData> active;
   private final BiConsumer<StatsData, Boolean> toggle;

   public SkillToggleNode(Component label, ResourceLocation icon, Predicate<StatsData> has, Predicate<StatsData> active, BiConsumer<StatsData, Boolean> toggle) {
      this.label = label;
      this.icon = icon;
      this.has = has;
      this.active = active;
      this.toggle = toggle;
   }

   @Override
   public Component label(StatsData stats) {
      return this.label;
   }

   @Override
   public ResourceLocation icon(StatsData stats) {
      return this.icon;
   }

   @Override
   public boolean visible(StatsData stats) {
      return this.has.test(stats);
   }

   @Override
   public boolean active(StatsData stats) {
      return this.active.test(stats);
   }

   @Override
   public int labelColor(StatsData stats) {
      return this.active(stats) ? 2883328 : 16718592;
   }

   @Override
   public void onSelect(StatsData stats) {
      boolean wasActive = this.active.test(stats);
      this.toggle.accept(stats, wasActive);
      this.playToggle(!wasActive);
   }
}
