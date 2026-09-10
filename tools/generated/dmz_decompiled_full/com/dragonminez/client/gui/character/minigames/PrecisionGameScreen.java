package com.dragonminez.client.gui.character.minigames;

import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.TrainingConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;

public class PrecisionGameScreen extends BaseMinigameScreen {
   private TrainingConfig.PrecisionConfig cfg;
   private final Random random = new Random();
   private final List<PrecisionGameScreen.Circle> targets = new ArrayList<>();
   private final Deque<Boolean> outcomes = new ArrayDeque<>();
   private int score;
   private int nextThreshold;
   private int spawnTimer;

   public PrecisionGameScreen() {
      super("precision", "gui.dragonminez.minigame.precision");
   }

   protected void init() {
      super.init();
      this.cfg = ConfigManager.getTrainingConfig().getPrecision();
      this.score = this.cfg.getStartingScore();
      this.nextThreshold = this.cfg.getStartingScore() + this.cfg.getLevelUpScoreBase();
      this.spawnTimer = 0;
      this.targets.clear();
      this.outcomes.clear();
   }

   private double ringSpeed() {
      return this.cfg.getBaseRingSpeed() + (double)this.levelsCleared * this.cfg.getRingSpeedPerLevel();
   }

   @Override
   protected void tickGame() {
      if (--this.spawnTimer <= 0 && this.countActive() < this.cfg.getMaxCircles()) {
         this.spawnCircle();
         if (this.countActive() < this.cfg.getMaxCircles() && this.random.nextDouble() < this.cfg.getBurstChance()) {
            this.spawnCircle();
         }

         this.spawnTimer = this.cfg.getSpawnIntervalTicks();
      }

      Iterator<PrecisionGameScreen.Circle> it = this.targets.iterator();

      while (it.hasNext()) {
         PrecisionGameScreen.Circle c = it.next();
         if (c.fading) {
            if (--c.fadeRemaining <= 0) {
               it.remove();
            }
         } else {
            c.ringRadius = c.ringRadius - (float)this.ringSpeed();
            if (c.ringRadius <= (float)(this.cfg.getTargetRadius() - this.cfg.getGoodWindow())) {
               this.missCircle(c);
            }
         }
      }
   }

   private int countActive() {
      int n = 0;

      for (PrecisionGameScreen.Circle c : this.targets) {
         if (!c.fading) {
            n++;
         }
      }

      return n;
   }

   private void spawnCircle() {
      int marginX = Math.max(50, this.width / 6);
      int marginY = Math.max(50, this.height / 6);
      int x = marginX + this.random.nextInt(Math.max(1, this.width - 2 * marginX));
      int y = marginY + this.random.nextInt(Math.max(1, this.height - 2 * marginY));
      this.targets.add(new PrecisionGameScreen.Circle(x, y, (float)this.cfg.getOuterRingRadius()));
   }

   @Override
   protected boolean onMouseClick(double mouseX, double mouseY, int button) {
      if (button != 0) {
         return false;
      } else {
         PrecisionGameScreen.Circle target = null;
         double bestTiming = Double.MAX_VALUE;
         int half = this.cfg.getTargetRadius();

         for (PrecisionGameScreen.Circle c : this.targets) {
            if (!c.fading && Math.abs(mouseX - (double)c.x) <= (double)half && Math.abs(mouseY - (double)c.y) <= (double)half) {
               double timing = (double)Math.abs(c.ringRadius - (float)this.cfg.getTargetRadius());
               if (timing < bestTiming) {
                  bestTiming = timing;
                  target = c;
               }
            }
         }

         if (target == null) {
            return true;
         } else {
            if (bestTiming <= (double)this.cfg.getPerfectWindow()) {
               this.score = this.score + this.cfg.getPerfectPoints();
               this.playHit(true);
               this.targets.remove(target);
               this.recordOutcome(true);
               this.checkLevelUp();
            } else if (bestTiming <= (double)this.cfg.getGoodWindow()) {
               this.score = this.score + this.cfg.getGoodPoints();
               this.playHit(false);
               this.targets.remove(target);
               this.recordOutcome(true);
               this.checkLevelUp();
            } else {
               this.missCircle(target);
            }

            return true;
         }
      }
   }

   private void missCircle(PrecisionGameScreen.Circle c) {
      c.fading = true;
      c.fadeRemaining = this.cfg.getFadeOutTicks();
      this.score = this.score - this.cfg.getMissPenalty();
      this.playMiss();
      this.recordOutcome(false);
      if (this.score < 0) {
         this.endGame();
      }
   }

   private void checkLevelUp() {
      if (this.score >= this.nextThreshold) {
         this.levelCleared();
         this.nextThreshold = this.nextThreshold + this.cfg.getLevelUpScorePerLevel() * this.level();
      }
   }

   private void recordOutcome(boolean hit) {
      this.outcomes.addLast(hit);

      while (this.outcomes.size() > this.cfg.getLoseMissWindow()) {
         this.outcomes.removeFirst();
      }

      int misses = 0;

      for (boolean o : this.outcomes) {
         if (!o) {
            misses++;
         }
      }

      if (misses >= this.cfg.getLoseMissThreshold()) {
         this.endGame();
      }
   }

   @Override
   protected void renderGame(GuiGraphics graphics) {
      int cx = this.width / 2;
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.training.level", new Object[]{this.level()}), cx, 64, -10496);
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.training.score", new Object[]{this.score, this.nextThreshold}), cx, 76, -1
      );

      for (PrecisionGameScreen.Circle c : this.targets) {
         if (c.fading) {
            int alpha = (int)(255.0F * ((float)c.fadeRemaining / (float)this.cfg.getFadeOutTicks()));
            int red = alpha << 24 | 16724016;
            this.drawCircle(graphics, c.x, c.y, this.cfg.getTargetRadius(), red);
            this.drawCircle(graphics, c.x, c.y, Math.max(1, (int)c.ringRadius), red);
         } else {
            this.drawCircle(graphics, c.x, c.y, this.cfg.getTargetRadius(), -10496);
            this.drawCircle(graphics, c.x, c.y, Math.max(1, (int)c.ringRadius), -8585770);
         }
      }
   }

   private void drawCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
      int segments = Math.max(16, radius * 3);

      for (int i = 0; i < segments; i++) {
         double angle = (Math.PI * 2) * (double)i / (double)segments;
         int px = cx + (int)Math.round(Math.cos(angle) * (double)radius);
         int py = cy + (int)Math.round(Math.sin(angle) * (double)radius);
         graphics.fill(px, py, px + 2, py + 2, color);
      }
   }

   private static class Circle {
      final int x;
      final int y;
      float ringRadius;
      boolean fading;
      int fadeRemaining;

      Circle(int x, int y, float ringRadius) {
         this.x = x;
         this.y = y;
         this.ringRadius = ringRadius;
      }
   }
}
