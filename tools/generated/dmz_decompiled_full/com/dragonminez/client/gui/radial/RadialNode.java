package com.dragonminez.client.gui.radial;

import com.dragonminez.common.stats.StatsData;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public interface RadialNode {
   Component label(StatsData var1);

   ResourceLocation icon(StatsData var1);

   default String faceText(StatsData stats) {
      return null;
   }

   default int iconTint(StatsData stats) {
      return -1;
   }

   default int labelColor(StatsData stats) {
      return 16777215;
   }

   default boolean active(StatsData stats) {
      return false;
   }

   default boolean visible(StatsData stats) {
      return true;
   }

   default boolean interactive(StatsData stats) {
      return true;
   }

   default List<RadialNode> children(StatsData stats) {
      return List.of();
   }

   default boolean expandable(StatsData stats) {
      return !this.children(stats).isEmpty();
   }

   default void onSelect(StatsData stats) {
   }

   default void onDoubleSelect(StatsData stats) {
      this.onSelect(stats);
   }

   default String orderKey() {
      return "";
   }

   default FormPreview preview(StatsData stats) {
      return null;
   }
}
