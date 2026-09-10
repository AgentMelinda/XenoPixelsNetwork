package com.dragonminez.client.gui.character.minigames;

import com.dragonminez.client.gui.UnblurredScreen;
import com.dragonminez.client.gui.character.MinigamesScreen;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.TrainingConfig;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.TrainingAnimationC2S;
import com.dragonminez.common.network.C2S.TrainingRewardC2S;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

public abstract class BaseMinigameScreen extends UnblurredScreen {
   protected static final ResourceLocation DMZ_FONT = ResourceLocation.fromNamespaceAndPath("dragonminez", "smooth");
   private static final ResourceLocation MENU_NPC_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menunpc.png");
   private static final int PANEL_TEX_W = 346;
   private static final int PANEL_TEX_H = 94;
   protected final String minigameId;
   private final String howToKey;
   protected int levelsCleared = 0;
   protected BaseMinigameScreen.State stage = BaseMinigameScreen.State.READY;
   protected UltimateChallenge challenge = null;
   private int finalRewardDisplay = 0;
   private boolean trainingAnimActive = false;

   protected BaseMinigameScreen(String minigameId, String titleKey) {
      super(Component.translatable(titleKey).withStyle(Style.EMPTY.withFont(DMZ_FONT)));
      this.minigameId = minigameId;
      this.howToKey = "gui.dragonminez.minigame." + minigameId + ".howto";
   }

   protected boolean isPlaying() {
      return this.stage == BaseMinigameScreen.State.PLAYING;
   }

   protected int level() {
      return this.levelsCleared + 1;
   }

   protected void levelCleared() {
      this.levelsCleared++;
      this.playUi(SoundEvents.PLAYER_LEVELUP, 1.2F, 0.5F);
      if (this.challenge != null && this.levelsCleared >= this.challenge.targetLevel()) {
         this.stage = BaseMinigameScreen.State.FINISHED;
         this.challenge.onStageComplete();
      } else {
         this.onLevelCleared();
      }
   }

   protected void endGame() {
      if (this.stage == BaseMinigameScreen.State.PLAYING) {
         this.stage = BaseMinigameScreen.State.FINISHED;
         if (this.challenge != null) {
            this.stopTrainingAnimation();
            this.challenge.onFail();
         } else {
            if (this.levelsCleared > 0) {
               NetworkHandler.sendToServer(new TrainingRewardC2S(this.minigameId, this.levelsCleared));
            }

            this.finalRewardDisplay = this.computeRewardDisplay();
            this.stopTrainingAnimation();
         }
      }
   }

   private int computeRewardDisplay() {
      if (this.levelsCleared <= 0) {
         return 0;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null) {
            return 0;
         } else {
            TrainingConfig cfg = ConfigManager.getTrainingConfig();
            TrainingConfig.MinigameSettings settings = cfg.getSettings(this.minigameId);
            return StatsProvider.get(StatsCapability.INSTANCE, mc.player).map(d -> {
               int tpc = d.getSingleStatCost(d.getStats().getTotalStats());
               float total = cfg.computeTpsPerLevel(tpc, settings) * (float)this.levelsCleared;
               float limit = settings.getTpsLimitPerGame();
               if (limit > 0.0F && total > limit) {
                  total = limit;
               }

               return (int)total;
            }).orElse(0);
         }
      }
   }

   private void quitToHub() {
      if (this.challenge != null) {
         Minecraft.getInstance().setScreen(null);
      } else {
         Minecraft.getInstance().setScreen(new MinigamesScreen());
      }
   }

   protected void startGame() {
      this.stage = BaseMinigameScreen.State.PLAYING;
      this.startTrainingAnimation();
      this.onStart();
   }

   private void startTrainingAnimation() {
      if (!this.trainingAnimActive) {
         this.trainingAnimActive = true;
         NetworkHandler.sendToServer(new TrainingAnimationC2S(true));
      }
   }

   private void stopTrainingAnimation() {
      if (this.trainingAnimActive) {
         this.trainingAnimActive = false;
         NetworkHandler.sendToServer(new TrainingAnimationC2S(false));
      }
   }

   public void removed() {
      this.stopTrainingAnimation();
      super.removed();
   }

   protected void playUi(SoundEvent sound, float pitch, float volume) {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
   }

   protected void playHit(boolean perfect) {
      this.playUi(SoundEvents.EXPERIENCE_ORB_PICKUP, perfect ? 1.5F : 1.0F, 0.3F);
   }

   protected void playMiss() {
      this.playUi((SoundEvent)SoundEvents.NOTE_BLOCK_BASS.value(), 0.5F, 0.4F);
   }

   public void tick() {
      super.tick();
      if (this.stage == BaseMinigameScreen.State.PLAYING) {
         this.tickGame();
      }
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(graphics, mouseX, mouseY, partialTick);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      this.renderGame(graphics);
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr(this.howToKey), this.width / 2, this.height - 16, -5197648);
      this.drawBigTitle(graphics);
      if (this.stage == BaseMinigameScreen.State.PLAYING) {
         this.drawRunningHud(graphics);
      }

      if (this.stage == BaseMinigameScreen.State.READY) {
         this.renderReadyOverlay(graphics);
      } else if (this.stage == BaseMinigameScreen.State.FINISHED) {
         this.renderFinishedOverlay(graphics);
      }

      super.render(graphics, mouseX, mouseY, partialTick);
   }

   private void drawBigTitle(GuiGraphics graphics) {
      MutableComponent title = this.getTitle().copy().withStyle(ChatFormatting.BOLD);
      int cx = this.width / 2;
      graphics.pose().pushPose();
      graphics.pose().translate((float)cx, 14.0F, 0.0F);
      graphics.pose().scale(3.0F, 3.0F, 1.0F);
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, title, 0, 0, -10496);
      graphics.pose().popPose();
   }

   private void drawRunningHud(GuiGraphics graphics) {
      if (this.finalRewardDisplay != 0 || this.levelsCleared != 0) {
         int tpsNow = this.computeRewardDisplay();
         if (tpsNow > 0) {
            TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.minigame.tps_so_far", tpsNow), this.width / 2, 48, -11141291);
         }
      }
   }

   private void drawNpcPanel(GuiGraphics graphics, int cx, int cy, int contentW, int contentH) {
      int padding = 16;
      int panelW = contentW + padding * 2;
      int panelH = contentH + padding * 2;
      float scaleX = (float)panelW / 346.0F;
      float scaleY = (float)panelH / 94.0F;
      int drawX = cx - panelW / 2;
      int drawY = cy - panelH / 2;
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      graphics.pose().pushPose();
      graphics.pose().translate((float)drawX, (float)drawY, 0.0F);
      graphics.pose().scale(scaleX, scaleY, 1.0F);
      graphics.blit(MENU_NPC_TEXTURE, 0, 0, 0.0F, 0.0F, 346, 94, 512, 512);
      graphics.pose().popPose();
   }

   private void renderReadyOverlay(GuiGraphics graphics) {
      graphics.fill(0, 0, this.width, this.height, -1157627904);
      int cx = this.width / 2;
      int cy = this.height / 2;
      int wrapWidth = 260;
      List<FormattedCharSequence> lines = this.font.split(this.tr(this.howToKey), wrapWidth);
      int textHeight = lines.size() * 10;
      this.drawNpcPanel(graphics, cx, cy, 280, 20 + textHeight);
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.minigame.start"), cx, cy - textHeight / 2 - 5, -1);
      int y = cy - textHeight / 2 + 10;

      for (FormattedCharSequence line : lines) {
         TextUtil.drawCenteredStringWithBorder(graphics, this.font, line, cx, y, -5197648);
         y += 10;
      }
   }

   private void renderFinishedOverlay(GuiGraphics graphics) {
      graphics.fill(0, 0, this.width, this.height, -1157627904);
      int cx = this.width / 2;
      int cy = this.height / 2;
      this.drawNpcPanel(graphics, cx, cy, 280, 90);
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.minigame.finished"), cx, cy - 38, -10496);
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.minigame.rounds_won", this.levelsCleared), cx, cy - 20, -1);
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.minigame.tps_won", this.finalRewardDisplay), cx, cy - 4, -11141291);
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.minigame.good_luck"), cx, cy + 14, -5197648);
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.minigame.continue"), cx, cy + 30, -8947849);
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      switch (this.stage) {
         case READY:
            if (keyCode == 256) {
               this.quitToHub();
            } else {
               this.startGame();
            }

            return true;
         case PLAYING:
            if (keyCode == 256) {
               this.endGame();
               return true;
            } else if (this.onKey(keyCode)) {
               return true;
            }
         default:
            return super.keyPressed(keyCode, scanCode, modifiers);
         case FINISHED:
            this.quitToHub();
            return true;
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      switch (this.stage) {
         case READY:
            this.startGame();
            return true;
         case PLAYING:
            if (this.onMouseClick(mouseX, mouseY, button)) {
               return true;
            }
         default:
            return super.mouseClicked(mouseX, mouseY, button);
         case FINISHED:
            this.quitToHub();
            return true;
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   protected MutableComponent tr(String key, Object... args) {
      return Component.translatable(key, args).withStyle(Style.EMPTY.withFont(DMZ_FONT));
   }

   protected abstract void tickGame();

   protected abstract void renderGame(GuiGraphics var1);

   protected boolean onKey(int keyCode) {
      return false;
   }

   protected boolean onMouseClick(double mouseX, double mouseY, int button) {
      return false;
   }

   protected void onStart() {
   }

   protected void onLevelCleared() {
   }

   public void setChallenge(UltimateChallenge challenge) {
      this.challenge = challenge;
   }

   protected static enum State {
      READY,
      PLAYING,
      FINISHED;
   }
}
