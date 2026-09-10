package com.dragonminez.client.gui.character.minigames;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.NPCActionC2S;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

public class UltimateChallenge {
   private static final int TARGET_LEVEL = 5;
   private final List<Supplier<BaseMinigameScreen>> stages = List.of(
      ControlGameScreen::new, GravityGameScreen::new, MemoryGameScreen::new, PrecisionGameScreen::new, RythmGameScreen::new
   );
   private int index = 0;

   public int targetLevel() {
      return 5;
   }

   public void start() {
      this.index = 0;
      this.openCurrent();
   }

   public void onStageComplete() {
      this.index++;
      if (this.index >= this.stages.size()) {
         NetworkHandler.sendToServer(new NPCActionC2S("oldkai", 1));
         Minecraft.getInstance().setScreen(null);
      } else {
         this.openCurrent();
      }
   }

   public void onFail() {
      this.index = 0;
      this.openCurrent();
   }

   private void openCurrent() {
      BaseMinigameScreen screen = this.stages.get(this.index).get();
      screen.setChallenge(this);
      Minecraft.getInstance().setScreen(screen);
   }
}
