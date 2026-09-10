package com.dragonminez.client.gui.character.minigames;

import com.dragonminez.client.util.KeyBinds;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.TrainingConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class RythmGameScreen extends BaseMinigameScreen {
   private final Random random = new Random();
   private final List<RythmGameScreen.Note> arrows = new ArrayList<>();
   private final Deque<Boolean> outcomes = new ArrayDeque<>();
   private final Set<RythmGameScreen.Direction> holdLockedDirections = new HashSet<>();
   private final Set<Integer> downKeys = new HashSet<>();
   private TrainingConfig.RhythmConfig cfg;
   private double progress = 0.0;
   private float spawnTimer = 0.0F;
   private boolean spawnLeftLane = true;
   private int lineY;
   private int laneLeftX;
   private int laneRightX;

   public RythmGameScreen() {
      super("rhythm", "gui.dragonminez.minigame.rhythm");
   }

   protected void init() {
      super.init();
      this.cfg = ConfigManager.getTrainingConfig().getRhythm();
      this.lineY = this.height / 2;
      this.laneLeftX = this.width / 2 - 40;
      this.laneRightX = this.width / 2 + 40;
      this.progress = 0.0;
      this.spawnTimer = 0.0F;
      this.arrows.clear();
      this.outcomes.clear();
      this.holdLockedDirections.clear();
      this.downKeys.clear();
   }

   private float noteSpeed() {
      return (float)(this.cfg.getBaseNoteSpeed() + (double)this.levelsCleared * this.cfg.getNoteSpeedPerLevel());
   }

   private int spawnInterval() {
      int interval = this.cfg.getBaseSpawnIntervalTicks() - this.levelsCleared * this.cfg.getSpawnIntervalDecreasePerLevel();
      return Math.max(this.cfg.getMinSpawnIntervalTicks(), interval);
   }

   @Override
   protected void tickGame() {
      this.progress = Math.max(0.0, this.progress - this.cfg.getProgressDecayPerTick());
      if (this.spawnTimer-- <= 0.0F) {
         this.spawnNote();
         this.spawnTimer = (float)this.spawnInterval();
      }

      float movement = this.noteSpeed();
      int goodWindow = this.cfg.getGoodWindow();
      long window = Minecraft.getInstance().getWindow().getWindow();
      Iterator<RythmGameScreen.Note> it = this.arrows.iterator();

      while (it.hasNext()) {
         RythmGameScreen.Note note = it.next();
         int targetX = note.leftLane ? this.laneLeftX : this.laneRightX;
         if (note.activated) {
            if (this.isHeld(window, note.direction)) {
               note.holdRemaining--;
               if (note.holdRemaining <= 0) {
                  it.remove();
                  this.holdLockedDirections.remove(note.direction);
                  this.progress = this.progress + this.cfg.getProgressGainHold();
                  this.clampProgress();
                  this.recordOutcome(true);
                  this.playHit(true);
               }
            } else {
               it.remove();
               this.holdLockedDirections.remove(note.direction);
               this.handleMiss(true);
            }
         } else {
            if (note.leftLane) {
               note.x += movement;
            } else {
               note.x -= movement;
            }

            boolean passed = note.leftLane ? note.x > (float)(targetX + goodWindow) : note.x < (float)(targetX - goodWindow);
            if (passed) {
               it.remove();
               this.holdLockedDirections.remove(note.direction);
               this.handleMiss(true);
            }
         }
      }
   }

   private void spawnNote() {
      boolean leftLane = this.spawnLeftLane;
      this.spawnLeftLane = !this.spawnLeftLane;
      if (this.laneHasHold(leftLane)) {
         if (this.laneHasHold(!leftLane)) {
            return;
         }

         leftLane = !leftLane;
      }

      boolean wantHold = this.random.nextDouble() < this.cfg.getHoldNoteChance();
      RythmGameScreen.Direction dir = this.randomDirUnlocked();
      if (dir != null) {
         this.addNote(leftLane, dir, 0, wantHold);
         if (!wantHold && this.random.nextDouble() < this.cfg.getDoubleNoteChance()) {
            RythmGameScreen.Direction doubleDir = this.randomDirUnlocked();
            if (doubleDir != null) {
               this.addNote(leftLane, doubleDir, this.cfg.getDoubleNoteGap(), false);
            }
         }
      }
   }

   private boolean laneHasHold(boolean leftLane) {
      for (RythmGameScreen.Note n : this.arrows) {
         if (n.isHold && n.leftLane == leftLane) {
            return true;
         }
      }

      return false;
   }

   private RythmGameScreen.Direction randomDirUnlocked() {
      RythmGameScreen.Direction[] all = RythmGameScreen.Direction.values();
      int start = this.random.nextInt(all.length);

      for (int i = 0; i < all.length; i++) {
         RythmGameScreen.Direction d = all[(start + i) % all.length];
         if (!this.holdLockedDirections.contains(d)) {
            return d;
         }
      }

      return null;
   }

   private void addNote(boolean leftLane, RythmGameScreen.Direction dir, int behindOffset, boolean isHold) {
      int travel = this.cfg.getNoteTravelDistance();
      float baseX = leftLane ? (float)(this.laneLeftX - travel) : (float)(this.laneRightX + travel);
      float startX = leftLane ? baseX - (float)behindOffset : baseX + (float)behindOffset;
      this.arrows.add(new RythmGameScreen.Note(startX, leftLane, dir, isHold, this.cfg.getHoldDurationTicks()));
      if (isHold) {
         this.holdLockedDirections.add(dir);
      }
   }

   private void clampProgress() {
      if (this.progress >= this.cfg.getProgressMax()) {
         this.levelCleared();
      }
   }

   @Override
   protected void onLevelCleared() {
      this.progress = this.cfg.getProgressOnLevelUp();
   }

   private boolean isHeld(long window, RythmGameScreen.Direction dir) {
      if (GLFW.glfwGetKey(window, dir.arrowKey) == 1) {
         return true;
      } else {
         int mapped = this.mappedKey(dir);
         return mapped > 0 && GLFW.glfwGetKey(window, mapped) == 1;
      }
   }

   private int mappedKey(RythmGameScreen.Direction dir) {
      return switch (dir) {
         case LEFT -> KeyBinds.RHYTHM_LEFT.getKey().getValue();
         case DOWN -> KeyBinds.RHYTHM_DOWN.getKey().getValue();
         case UP -> KeyBinds.RHYTHM_UP.getKey().getValue();
         case RIGHT -> KeyBinds.RHYTHM_RIGHT.getKey().getValue();
      };
   }

   @Override
   protected boolean onKey(int keyCode) {
      RythmGameScreen.Direction dir = this.directionForKey(keyCode);
      if (dir == null) {
         return false;
      } else if (!this.downKeys.add(keyCode)) {
         return true;
      } else {
         this.checkInput(dir);
         return true;
      }
   }

   public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
      this.downKeys.remove(keyCode);
      return super.keyReleased(keyCode, scanCode, modifiers);
   }

   private RythmGameScreen.Direction directionForKey(int keyCode) {
      for (RythmGameScreen.Direction d : RythmGameScreen.Direction.values()) {
         if (keyCode == d.arrowKey || keyCode == this.mappedKey(d)) {
            return d;
         }
      }

      return null;
   }

   private void checkInput(RythmGameScreen.Direction dir) {
      RythmGameScreen.Note best = null;
      double minDist = Double.MAX_VALUE;

      for (RythmGameScreen.Note note : this.arrows) {
         if (!note.activated && note.direction == dir) {
            int targetX = note.leftLane ? this.laneLeftX : this.laneRightX;
            double dist = (double)Math.abs(note.x - (float)targetX);
            if (dist < (double)this.cfg.getGoodWindow() && dist < minDist) {
               minDist = dist;
               best = note;
            }
         }
      }

      if (best == null) {
         this.progress = Math.max(0.0, this.progress - this.cfg.getProgressLossOnMiss());
         this.playMiss();
      } else {
         boolean perfect = minDist <= (double)this.cfg.getPerfectWindow();
         this.progress = this.progress + (perfect ? this.cfg.getProgressGainPerfect() : this.cfg.getProgressGainGood());
         this.playHit(perfect);
         if (best.isHold) {
            best.activated = true;
            best.x = best.leftLane ? (float)this.laneLeftX : (float)this.laneRightX;
            best.holdRemaining = best.holdTicksTotal;
            this.recordOutcome(true);
         } else {
            this.holdLockedDirections.remove(best.direction);
            this.arrows.remove(best);
            this.recordOutcome(true);
         }

         this.clampProgress();
      }
   }

   private void handleMiss(boolean countNote) {
      this.progress = Math.max(0.0, this.progress - this.cfg.getProgressLossOnMiss());
      this.playMiss();
      if (countNote) {
         this.recordOutcome(false);
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
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.training.level", new Object[]{this.level()}), cx, this.lineY - 64, -10496
      );
      int pbLeft = cx - 90;
      int pbRight = cx + 90;
      int pbY = this.lineY - 50;
      graphics.fill(pbLeft, pbY, pbRight, pbY + 8, -13421773);
      float pct = (float)Math.min(1.0, this.progress / this.cfg.getProgressMax());
      graphics.fill(pbLeft, pbY, pbLeft + (int)((float)(pbRight - pbLeft) * pct), pbY + 8, -11141291);
      graphics.renderOutline(pbLeft - 1, pbY - 1, pbRight - pbLeft + 2, 10, -1);
      int travel = this.cfg.getNoteTravelDistance();
      int leftEnd = this.laneLeftX - travel - 10;
      int rightEnd = this.laneRightX + travel + 10;
      graphics.fill(leftEnd, this.lineY - 1, this.laneLeftX - 14, this.lineY + 1, 1090519039);
      graphics.fill(this.laneRightX + 14, this.lineY - 1, rightEnd, this.lineY + 1, 1090519039);
      this.drawTarget(graphics, this.laneLeftX);
      this.drawTarget(graphics, this.laneRightX);
      float speed = this.noteSpeed();

      for (RythmGameScreen.Note note : this.arrows) {
         if (note.isHold) {
            this.drawHoldTail(graphics, note, speed);
         }

         this.drawArrow(graphics, (int)note.x, this.lineY, note.direction.symbol, note.direction.color);
      }
   }

   private void drawHoldTail(GuiGraphics graphics, RythmGameScreen.Note note, float speed) {
      int remainingTicks = note.activated ? note.holdRemaining : note.holdTicksTotal;
      int tailLen = (int)((float)remainingTicks * speed);
      int headX = (int)note.x;
      int color = note.direction.color & 16777215 | -2147483648;
      if (note.leftLane) {
         graphics.fill(headX - tailLen, this.lineY - 4, headX, this.lineY + 4, color);
      } else {
         graphics.fill(headX, this.lineY - 4, headX + tailLen, this.lineY + 4, color);
      }
   }

   private void drawTarget(GuiGraphics graphics, int x) {
      graphics.fill(x - 1, this.lineY - 18, x + 1, this.lineY + 18, -1862270977);
      graphics.renderOutline(x - 14, this.lineY - 14, 28, 28, -2130706433);
   }

   private void drawArrow(GuiGraphics graphics, int x, int y, String symbol, int color) {
      float scale = this.cfg.getArrowScale();
      graphics.pose().pushPose();
      graphics.pose().translate((float)x, (float)y, 0.0F);
      graphics.pose().scale(scale, scale, 1.0F);
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, Component.literal(symbol), 0, -4, color);
      graphics.pose().popPose();
   }

   private static enum Direction {
      LEFT("←", -11141121, 263),
      DOWN("↓", -11141291, 264),
      UP("↑", -171, 265),
      RIGHT("→", -43521, 262);

      final String symbol;
      final int color;
      final int arrowKey;

      private Direction(String symbol, int color, int arrowKey) {
         this.symbol = symbol;
         this.color = color;
         this.arrowKey = arrowKey;
      }
   }

   private static class Note {
      float x;
      final boolean leftLane;
      final RythmGameScreen.Direction direction;
      final boolean isHold;
      final int holdTicksTotal;
      boolean activated;
      int holdRemaining;

      Note(float x, boolean leftLane, RythmGameScreen.Direction direction, boolean isHold, int holdTicksTotal) {
         this.x = x;
         this.leftLane = leftLane;
         this.direction = direction;
         this.isHold = isHold;
         this.holdTicksTotal = holdTicksTotal;
      }
   }
}
