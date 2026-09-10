package com.dragonminez.client.gui.radial.nodes;

import com.dragonminez.client.gui.radial.AbstractRadialNode;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.SetReleaseLimitC2S;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ReleaseLimitOptionNode extends AbstractRadialNode {
   private final int value;

   public ReleaseLimitOptionNode(int value) {
      this.value = value;
   }

   @Override
   public Component label(StatsData stats) {
      return Component.literal(this.value + "%");
   }

   @Override
   public ResourceLocation icon(StatsData stats) {
      return null;
   }

   @Override
   public boolean active(StatsData stats) {
      return stats.getResources().getReleaseLimit() == this.value;
   }

   @Override
   public int labelColor(StatsData stats) {
      return this.active(stats) ? 2883328 : 16777215;
   }

   @Override
   public void onSelect(StatsData stats) {
      NetworkHandler.sendToServer(new SetReleaseLimitC2S(this.value));
      this.playClick();
   }
}
