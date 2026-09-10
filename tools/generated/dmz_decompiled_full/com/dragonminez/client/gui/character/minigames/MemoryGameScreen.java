package com.dragonminez.client.gui.character.minigames;

import com.dragonminez.client.util.KeyBinds;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.TrainingConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class MemoryGameScreen extends BaseMinigameScreen {
   private TrainingConfig.MemoryConfig cfg;
   private final Random random = new Random();
   private final List<MemoryGameScreen.Direction> pattern = new ArrayList<>();
   private MemoryGameScreen.Phase memPhase = MemoryGameScreen.Phase.SHOWING;
   private int showTimer;
   private int patternIndex;

   public MemoryGameScreen() {
      super("memory", "gui.dragonminez.minigame.memory");
   }

   protected void init() {
      super.init();
      this.cfg = ConfigManager.getTrainingConfig().getMemory();
      this.buildSequence();
   }

   private int sequenceLength() {
      return this.cfg.getBaseSequenceLength() + this.levelsCleared / 2 * this.cfg.getSequenceLengthPerLevel();
   }

   private int showTicks() {
      return Math.max(this.cfg.getMinShowTicks(), this.cfg.getBaseShowTicks() - this.levelsCleared * this.cfg.getShowTicksDecreasePerLevel());
   }

   private void buildSequence() {
      this.pattern.clear();
      int len = this.sequenceLength();
      MemoryGameScreen.Direction[] all = MemoryGameScreen.Direction.values();

      for (int i = 0; i < len; i++) {
         this.pattern.add(all[this.random.nextInt(all.length)]);
      }

      this.memPhase = MemoryGameScreen.Phase.SHOWING;
      this.showTimer = this.showTicks();
      this.patternIndex = 0;
   }

   @Override
   protected void tickGame() {
      if (this.memPhase == MemoryGameScreen.Phase.SHOWING) {
         this.showTimer--;
         if (this.showTimer <= 0) {
            this.memPhase = MemoryGameScreen.Phase.INPUT;
         }
      }
   }

   @Override
   protected boolean onKey(int keyCode) {
      if (this.memPhase != MemoryGameScreen.Phase.INPUT) {
         return false;
      } else {
         MemoryGameScreen.Direction dir = this.directionForKey(keyCode);
         if (dir == null) {
            return false;
         } else {
            if (dir == this.pattern.get(this.patternIndex)) {
               this.patternIndex++;
               this.playHit(false);
               if (this.patternIndex >= this.pattern.size()) {
                  this.levelCleared();
               }
            } else {
               this.playMiss();
               this.endGame();
            }

            return true;
         }
      }
   }

   private MemoryGameScreen.Direction directionForKey(int keyCode) {
      if (keyCode == 263 || keyCode == KeyBinds.RHYTHM_LEFT.getKey().getValue()) {
         return MemoryGameScreen.Direction.LEFT;
      } else if (keyCode == 264 || keyCode == KeyBinds.RHYTHM_DOWN.getKey().getValue()) {
         return MemoryGameScreen.Direction.DOWN;
      } else if (keyCode == 265 || keyCode == KeyBinds.RHYTHM_UP.getKey().getValue()) {
         return MemoryGameScreen.Direction.UP;
      } else {
         return keyCode != 262 && keyCode != KeyBinds.RHYTHM_RIGHT.getKey().getValue() ? null : MemoryGameScreen.Direction.RIGHT;
      }
   }

   @Override
   protected void onLevelCleared() {
      this.buildSequence();
   }

   @Override
   protected void renderGame(GuiGraphics graphics) {
      int cx = this.width / 2;
      int cy = this.height / 2;
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.training.level", new Object[]{this.level()}), cx, cy - 50, -10496);
      Component prompt = this.memPhase == MemoryGameScreen.Phase.SHOWING
         ? this.tr("gui.dragonminez.minigame.memory.memorize", new Object[0])
         : this.tr("gui.dragonminez.minigame.memory.repeat", new Object[0]);
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, prompt, cx, cy - 36, -1);
      int spacing = 26;
      int rowSpacing = 28;
      int maxRowWidth = (int)((float)this.width * 0.75F);
      int perRow = Math.max(1, maxRowWidth / spacing);
      int rowCount = (int)Math.ceil((double)this.pattern.size() / (double)perRow);
      int gridTop = cy - (rowCount - 1) * rowSpacing / 2;

      for (int i = 0; i < this.pattern.size(); i++) {
         int row = i / perRow;
         int col = i % perRow;
         int notesInRow = Math.min(perRow, this.pattern.size() - row * perRow);
         int startX = cx - (notesInRow - 1) * spacing / 2;
         int x = startX + col * spacing;
         int y = gridTop + row * rowSpacing;
         MemoryGameScreen.Direction dir = this.pattern.get(i);
         if (this.memPhase == MemoryGameScreen.Phase.SHOWING) {
            this.drawArrow(graphics, x, y, dir.symbol, dir.color);
         } else if (i < this.patternIndex) {
            this.drawArrow(graphics, x, y, dir.symbol, -11141291);
         } else {
            this.drawArrow(graphics, x, y, "?", -8947849);
         }
      }

      if (this.memPhase == MemoryGameScreen.Phase.SHOWING) {
         int pbY = gridTop + (rowCount - 1) * rowSpacing + 30;
         int pbLeft = cx - 60;
         int pbRight = cx + 60;
         graphics.fill(pbLeft, pbY, pbRight, pbY + 4, -13421773);
         float pct = Math.min(1.0F, (float)this.showTimer / (float)this.showTicks());
         graphics.fill(pbLeft, pbY, pbLeft + (int)((float)(pbRight - pbLeft) * pct), pbY + 4, -10496);
      }
   }

   private void drawArrow(GuiGraphics graphics, int x, int y, String symbol, int color) {
      graphics.pose().pushPose();
      graphics.pose().translate((float)x, (float)y, 0.0F);
      graphics.pose().scale(2.0F, 2.0F, 1.0F);
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, Component.literal(symbol), 0, -4, color);
      graphics.pose().popPose();
   }

   private static enum Direction {
      LEFT("←", -11141121),
      DOWN("↓", -11141291),
      UP("↑", -171),
      RIGHT("→", -43521);

      final String symbol;
      final int color;

      private Direction(String symbol, int color) {
         this.symbol = symbol;
         this.color = color;
      }
   }

   private static enum Phase {
      SHOWING,
      INPUT;
   }
}
