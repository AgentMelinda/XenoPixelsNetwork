package com.dragonminez.client.gui.character.minigames;

import com.dragonminez.client.util.KeyBinds;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.TrainingConfig;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

public class ControlGameScreen extends BaseMinigameScreen {
   private static final int DIR_CHANGE_INTERVAL_TICKS = 30;
   private final Random random = new Random();
   private TrainingConfig.ControlConfig cfg;
   private int barLeft;
   private int barRight;
   private int barY;
   private float cursorX;
   private float zoneMid;
   private int zoneDir = 1;
   private int dirChangeTimer = 30;
   private int zoneWidth;
   private double holdProgress;
   private int levelTicksLeft;

   public ControlGameScreen() {
      super("control", "gui.dragonminez.minigame.control");
   }

   protected void init() {
      super.init();
      this.cfg = ConfigManager.getTrainingConfig().getControl();
      int cx = this.width / 2;
      this.barLeft = cx - this.cfg.getBarWidth() / 2;
      this.barRight = cx + this.cfg.getBarWidth() / 2;
      this.barY = this.height / 2;
      this.cursorX = (float)cx;
      this.zoneMid = (float)cx;
      this.zoneWidth = this.cfg.getBaseZoneWidth();
      this.holdProgress = 0.0;
      this.levelTicksLeft = this.cfg.getLevelTimeLimitTicks();
   }

   private double zoneSpeed() {
      return this.cfg.getBaseZoneSpeed() + (double)this.levelsCleared * this.cfg.getZoneSpeedPerLevel();
   }

   @Override
   protected void tickGame() {
      this.levelTicksLeft--;
      if (this.levelTicksLeft <= 0) {
         this.endGame();
      } else {
         if (--this.dirChangeTimer <= 0) {
            this.dirChangeTimer = 30;
            if (this.random.nextBoolean()) {
               this.zoneDir = -this.zoneDir;
            }
         }

         float half = (float)this.zoneWidth / 2.0F;
         this.zoneMid = this.zoneMid + (float)((double)this.zoneDir * this.zoneSpeed());
         if (this.zoneMid - half <= (float)this.barLeft) {
            this.zoneMid = (float)this.barLeft + half;
            this.zoneDir = 1;
         } else if (this.zoneMid + half >= (float)this.barRight) {
            this.zoneMid = (float)this.barRight - half;
            this.zoneDir = -1;
         }

         long window = Minecraft.getInstance().getWindow().getWindow();
         boolean left = this.isHeld(window, 263) || this.isHeld(window, 65) || this.isHeld(window, KeyBinds.RHYTHM_LEFT.getKey().getValue());
         boolean right = this.isHeld(window, 262) || this.isHeld(window, 68) || this.isHeld(window, KeyBinds.RHYTHM_RIGHT.getKey().getValue());
         if (left) {
            this.cursorX = this.cursorX - (float)this.cfg.getMarkerSpeed();
         }

         if (right) {
            this.cursorX = this.cursorX + (float)this.cfg.getMarkerSpeed();
         }

         this.cursorX = Math.max((float)this.barLeft, Math.min((float)this.barRight, this.cursorX));
         boolean inside = Math.abs(this.cursorX - this.zoneMid) <= half;
         if (inside) {
            this.holdProgress++;
            if (this.holdProgress >= (double)this.cfg.getHoldDurationTicks()) {
               this.levelCleared();
            }
         } else {
            double loss = this.cfg.getBaseProgressLossPerTick() + (double)this.levelsCleared * this.cfg.getProgressLossPerLevel();
            this.holdProgress = Math.max(0.0, this.holdProgress - loss);
         }
      }
   }

   private boolean isHeld(long window, int keyCode) {
      return keyCode > 0 && GLFW.glfwGetKey(window, keyCode) == 1;
   }

   @Override
   protected void onLevelCleared() {
      this.zoneWidth = Math.max(this.cfg.getMinZoneWidth(), this.cfg.getBaseZoneWidth() - this.levelsCleared * this.cfg.getZoneWidthDecreasePerLevel());
      this.holdProgress = 0.0;
      this.levelTicksLeft = this.cfg.getLevelTimeLimitTicks();
      this.zoneMid = (float)this.width / 2.0F;
      this.dirChangeTimer = 30;
   }

   @Override
   protected void renderGame(GuiGraphics graphics) {
      int cx = this.width / 2;
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.training.level", new Object[]{this.level()}), cx, this.barY - 50, -10496
      );
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.training.time", new Object[]{Math.max(0, this.levelTicksLeft) / 20 + 1}), cx, this.barY - 38, -1
      );
      graphics.fill(this.barLeft, this.barY - 8, this.barRight, this.barY + 8, -14671840);
      graphics.renderOutline(this.barLeft - 1, this.barY - 9, this.barRight - this.barLeft + 2, 18, -1);
      float half = (float)this.zoneWidth / 2.0F;
      graphics.fill((int)(this.zoneMid - half), this.barY - 8, (int)(this.zoneMid + half), this.barY + 8, -1875640491);
      boolean inside = Math.abs(this.cursorX - this.zoneMid) <= half;
      int markerColor = inside ? -1 : -43691;
      graphics.fill((int)this.cursorX - 2, this.barY - 14, (int)this.cursorX + 2, this.barY + 14, markerColor);
      int pbLeft = cx - 60;
      int pbRight = cx + 60;
      int pbY = this.barY + 30;
      graphics.fill(pbLeft, pbY, pbRight, pbY + 6, -13421773);
      float pct = Math.min(1.0F, (float)this.holdProgress / (float)this.cfg.getHoldDurationTicks());
      graphics.fill(pbLeft, pbY, pbLeft + (int)((float)(pbRight - pbLeft) * pct), pbY + 6, -11141291);
   }
}
