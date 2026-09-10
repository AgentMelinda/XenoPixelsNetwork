package com.dragonminez.client.gui.character.minigames;

import com.dragonminez.client.util.KeyBinds;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.TrainingConfig;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class GravityGameScreen extends BaseMinigameScreen {
   private TrainingConfig.GravityConfig cfg;
   private final Random random = new Random();
   private int barX;
   private int barTop;
   private int barBottom;
   private int controlLineY;
   private float indicatorY;
   private double holdProgress;
   private int neededSide = -1;
   private int wrongFlashSide = 0;
   private int wrongFlashTicks = 0;

   public GravityGameScreen() {
      super("gravity", "gui.dragonminez.minigame.gravity");
   }

   protected void init() {
      super.init();
      this.cfg = ConfigManager.getTrainingConfig().getGravity();
      this.barX = this.width / 2;
      this.barTop = this.height / 2 - this.cfg.getBarHeight() / 2;
      this.barBottom = this.height / 2 + this.cfg.getBarHeight() / 2;
      this.controlLineY = this.barTop + (int)((double)this.cfg.getBarHeight() * this.cfg.getControlLineFraction());
      this.indicatorY = (float)(this.barTop + 20);
      this.holdProgress = 0.0;
      this.neededSide = this.random.nextBoolean() ? -1 : 1;
      this.wrongFlashTicks = 0;
   }

   @Override
   protected void onStart() {
      this.neededSide = this.random.nextBoolean() ? -1 : 1;
   }

   private double gravity() {
      return this.cfg.getBaseGravity() + (double)this.levelsCleared * this.cfg.getGravityPerLevel();
   }

   @Override
   protected void tickGame() {
      if (this.wrongFlashTicks > 0) {
         this.wrongFlashTicks--;
      }

      this.indicatorY = this.indicatorY + (float)this.gravity();
      if (this.indicatorY >= (float)this.barBottom) {
         this.indicatorY = (float)this.barBottom;
         this.endGame();
      } else {
         if (this.indicatorY < (float)this.controlLineY) {
            this.holdProgress++;
            if (this.holdProgress >= (double)this.cfg.getHoldDurationTicks()) {
               this.levelCleared();
            }
         } else {
            this.holdProgress = Math.max(0.0, this.holdProgress - this.cfg.getProgressLossPerTick());
         }
      }
   }

   @Override
   protected boolean onKey(int keyCode) {
      int side = this.sideForKey(keyCode);
      if (side == 0) {
         return false;
      } else {
         this.pushSide(side);
         return true;
      }
   }

   @Override
   protected boolean onMouseClick(double mouseX, double mouseY, int button) {
      if (button == 0) {
         this.pushSide(-1);
         return true;
      } else if (button == 1) {
         this.pushSide(1);
         return true;
      } else {
         return false;
      }
   }

   private int sideForKey(int keyCode) {
      if (keyCode == 263 || keyCode == 65 || keyCode == KeyBinds.RHYTHM_LEFT.getKey().getValue()) {
         return -1;
      } else {
         return keyCode != 262 && keyCode != 68 && keyCode != KeyBinds.RHYTHM_RIGHT.getKey().getValue() ? 0 : 1;
      }
   }

   private void pushSide(int side) {
      if (side == this.neededSide) {
         this.indicatorY = this.indicatorY - (float)this.cfg.getRisePerTap();
         if (this.indicatorY < (float)this.barTop) {
            this.indicatorY = (float)this.barTop;
         }

         this.playHit(false);
         this.neededSide = this.random.nextBoolean() ? -1 : 1;
      } else {
         this.indicatorY = this.indicatorY + (float)(this.cfg.getWrongPressDescentMultiplier() * this.gravity());
         this.wrongFlashSide = side;
         this.wrongFlashTicks = 6;
         this.playMiss();
      }
   }

   @Override
   protected void onLevelCleared() {
      this.holdProgress = 0.0;
      this.indicatorY = (float)(this.barTop + 20);
      this.neededSide = this.random.nextBoolean() ? -1 : 1;
   }

   @Override
   protected void renderGame(GuiGraphics graphics) {
      int cx = this.width / 2;
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.training.level", new Object[]{this.level()}), cx, this.barTop - 26, -10496
      );
      graphics.fill(this.barX - 10, this.barTop, this.barX + 10, this.barBottom, -14671840);
      graphics.renderOutline(this.barX - 11, this.barTop - 1, 22, this.barBottom - this.barTop + 2, -1);
      graphics.fill(this.barX - 16, this.controlLineY - 1, this.barX + 16, this.controlLineY + 1, -10496);
      graphics.fill(this.barX - 9, this.barTop + 1, this.barX + 9, this.controlLineY, 808714069);
      boolean above = this.indicatorY < (float)this.controlLineY;
      int color = above ? -11141291 : -43691;
      graphics.fill(this.barX - 9, (int)this.indicatorY - 3, this.barX + 9, (int)this.indicatorY + 3, color);
      this.drawSideArrow(graphics, this.barX - 34, (this.barTop + this.barBottom) / 2, "←", -1);
      this.drawSideArrow(graphics, this.barX + 34, (this.barTop + this.barBottom) / 2, "→", 1);
      int pbLeft = cx - 60;
      int pbRight = cx + 60;
      int pbY = this.barBottom + 18;
      graphics.fill(pbLeft, pbY, pbRight, pbY + 6, -13421773);
      float pct = (float)Math.min(1.0, this.holdProgress / (double)this.cfg.getHoldDurationTicks());
      graphics.fill(pbLeft, pbY, pbLeft + (int)((float)(pbRight - pbLeft) * pct), pbY + 6, -11141291);
   }

   private void drawSideArrow(GuiGraphics graphics, int x, int y, String symbol, int side) {
      int color = -9408400;
      if (this.wrongFlashTicks > 0 && this.wrongFlashSide == side) {
         color = -53200;
      } else if (this.neededSide == side) {
         color = -8144;
      }

      graphics.pose().pushPose();
      graphics.pose().translate((float)x, (float)y, 0.0F);
      graphics.pose().scale(2.0F, 2.0F, 1.0F);
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, Component.literal(symbol), 0, -4, color);
      graphics.pose().popPose();
   }
}
