package com.dragonminez.client.gui.character.util;

import com.dragonminez.client.gui.buttons.CustomTextureButton;
import com.dragonminez.client.gui.character.CharacterStatsScreen;
import com.dragonminez.client.gui.character.ConfigMenuScreen;
import com.dragonminez.client.gui.character.MinigamesScreen;
import com.dragonminez.client.gui.character.PartyMenuScreen;
import com.dragonminez.client.gui.character.QuestTreeScreen;
import com.dragonminez.client.gui.character.SkillsMenuScreen;
import com.dragonminez.client.util.KeyBinds;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class BaseMenuScreen extends ScaledScreen {
   public static final List<BaseMenuScreen.MenuTab> SECONDARY_TABS = List.of(
      new BaseMenuScreen.MenuTab(KeyBinds.STATS_TAB_PARTY, PartyMenuScreen::new),
      new BaseMenuScreen.MenuTab(KeyBinds.STATS_TAB_SKILLS, SkillsMenuScreen::new),
      new BaseMenuScreen.MenuTab(KeyBinds.STATS_TAB_QUESTS, QuestTreeScreen::new),
      new BaseMenuScreen.MenuTab(KeyBinds.STATS_TAB_MINIGAMES, MinigamesScreen::new),
      new BaseMenuScreen.MenuTab(KeyBinds.STATS_TAB_CONFIG, ConfigMenuScreen::new)
   );
   protected static boolean GLOBAL_SWITCHING = false;
   protected boolean isSwitchingMenu = false;
   private static final ResourceLocation SCREEN_BUTTONS = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/menubuttons.png");
   private static final long OPEN_ANIMATION_DURATION = 200L;
   private static final long PANEL_ENTER_ANIMATION_DURATION = 520L;
   private static final long PANEL_EXIT_ANIMATION_DURATION = 140L;
   private static final int PANEL_SWITCH_DISTANCE = 190;
   private static final int TOP_PANEL_SWITCH_DISTANCE = 90;
   private static final long STATS_MENU_REOPEN_COOLDOWN_MS = 450L;
   private static long statsMenuReopenBlockedUntilMs = 0L;
   private long animationStartTime;
   private BaseMenuScreen.TransitionState transitionState = BaseMenuScreen.TransitionState.NONE;
   private boolean suppressOpenAnimationOnce = false;
   private long panelSwitchAnimationStartTime;
   private BaseMenuScreen.PanelSwitchState panelSwitchState = BaseMenuScreen.PanelSwitchState.NONE;
   private Screen pendingSwitchScreen;
   protected float tooltipScrollY = 0.0F;
   protected float targetTooltipScrollY = 0.0F;

   protected BaseMenuScreen(Component title) {
      super(title);
   }

   protected void init() {
      super.init();
      if (GLOBAL_SWITCHING) {
         GLOBAL_SWITCHING = false;
         this.transitionState = BaseMenuScreen.TransitionState.NONE;
         this.startPanelEnterTransition();
      } else if (!this.suppressOpenAnimationOnce) {
         this.startOpenTransition();
      }

      this.initNavigationButtons();
   }

   public void tick() {
      super.tick();
      this.tooltipScrollY = Mth.lerp(0.5F, this.tooltipScrollY, this.targetTooltipScrollY);
      if (this.panelSwitchState == BaseMenuScreen.PanelSwitchState.ENTERING
         && this.getPanelSwitchProgress(this.getMinecraft().getTimer().getGameTimeDeltaPartialTick(false)) >= 1.0F) {
         this.panelSwitchState = BaseMenuScreen.PanelSwitchState.NONE;
      }

      if (this.panelSwitchState == BaseMenuScreen.PanelSwitchState.EXITING
         && this.getPanelSwitchProgress(this.getMinecraft().getTimer().getGameTimeDeltaPartialTick(false)) >= 1.0F) {
         this.panelSwitchState = BaseMenuScreen.PanelSwitchState.NONE;
         if (this.minecraft != null) {
            GLOBAL_SWITCHING = true;
            this.minecraft.setScreen(this.pendingSwitchScreen);
         }
      }

      if (this.transitionState == BaseMenuScreen.TransitionState.OPENING
         && this.getTransitionProgress(this.getMinecraft().getTimer().getGameTimeDeltaPartialTick(false)) >= 1.0F) {
         this.transitionState = BaseMenuScreen.TransitionState.NONE;
      }

      if (this.transitionState == BaseMenuScreen.TransitionState.CLOSING
         && this.getTransitionProgress(this.getMinecraft().getTimer().getGameTimeDeltaPartialTick(false)) >= 1.0F
         && this.minecraft != null) {
         this.minecraft.setScreen(null);
      }
   }

   protected void initNavigationButtons() {
      int centerX = this.getUiWidth() / 2;
      int bottomY = this.getUiHeight() - 30;
      this.addRenderableWidget(
         new CustomTextureButton.Builder()
            .position(centerX - 110, bottomY)
            .size(20, 20)
            .texture(SCREEN_BUTTONS)
            .textureSize(20, 20)
            .textureCoords(120, 0, 120, 20)
            .onPress(btn -> this.switchMenu(new PartyMenuScreen()))
            .sound((SoundEvent)MainSounds.UI_MENU_SWITCH.get())
            .build()
      );
      this.addRenderableWidget(
         new CustomTextureButton.Builder()
            .position(centerX - 70, bottomY)
            .size(20, 20)
            .texture(SCREEN_BUTTONS)
            .textureSize(20, 20)
            .textureCoords(0, 0, 0, 20)
            .onPress(btn -> this.switchMenu(new CharacterStatsScreen()))
            .sound((SoundEvent)MainSounds.UI_MENU_SWITCH.get())
            .build()
      );
      this.addRenderableWidget(
         new CustomTextureButton.Builder()
            .position(centerX - 30, bottomY)
            .size(20, 20)
            .texture(SCREEN_BUTTONS)
            .textureSize(20, 20)
            .textureCoords(20, 0, 20, 20)
            .onPress(btn -> this.switchMenu(new SkillsMenuScreen()))
            .sound((SoundEvent)MainSounds.UI_MENU_SWITCH.get())
            .build()
      );
      this.addRenderableWidget(
         new CustomTextureButton.Builder()
            .position(centerX + 10, bottomY)
            .size(20, 20)
            .texture(SCREEN_BUTTONS)
            .textureSize(20, 20)
            .textureCoords(60, 0, 60, 20)
            .onPress(btn -> this.switchMenu(new QuestTreeScreen()))
            .sound((SoundEvent)MainSounds.UI_MENU_SWITCH.get())
            .build()
      );
      this.addRenderableWidget(
         new CustomTextureButton.Builder()
            .position(centerX + 50, bottomY)
            .size(20, 20)
            .texture(SCREEN_BUTTONS)
            .textureSize(20, 20)
            .textureCoords(140, 0, 140, 20)
            .onPress(btn -> this.switchMenu(new MinigamesScreen()))
            .sound((SoundEvent)MainSounds.UI_MENU_SWITCH.get())
            .build()
      );
      this.addRenderableWidget(
         new CustomTextureButton.Builder()
            .position(centerX + 90, bottomY)
            .size(20, 20)
            .texture(SCREEN_BUTTONS)
            .textureSize(20, 20)
            .textureCoords(100, 0, 100, 20)
            .onPress(btn -> this.switchMenu(new ConfigMenuScreen()))
            .sound((SoundEvent)MainSounds.UI_MENU_SWITCH.get())
            .build()
      );
   }

   protected void switchMenu(Screen nextScreen) {
      if (this.minecraft != null && this.minecraft.screen != null && !this.minecraft.screen.getClass().equals(nextScreen.getClass())) {
         this.isSwitchingMenu = true;
         this.startPanelExitTransition(nextScreen);
      }
   }

   protected int calculateScrollOffset(double uiMouseY, int startY, int scrollBarHeight, int maxScrollValue) {
      float scrollPercent = (float)(uiMouseY - (double)startY) / (float)scrollBarHeight;
      scrollPercent = Mth.clamp(scrollPercent, 0.0F, 1.0F);
      return Math.round(scrollPercent * (float)maxScrollValue);
   }

   public boolean isPauseScreen() {
      return false;
   }

   public boolean isNotAnimating() {
      return this.transitionState == BaseMenuScreen.TransitionState.NONE
         || !(this.getTransitionProgress(this.getMinecraft().getTimer().getGameTimeDeltaPartialTick(false)) < 1.0F);
   }

   public static boolean isStatsMenuReopenBlocked() {
      return System.currentTimeMillis() < statsMenuReopenBlockedUntilMs;
   }

   protected void applyZoom(GuiGraphics graphics, float partialTick) {
      if (this.transitionState != BaseMenuScreen.TransitionState.NONE) {
         float progress = this.getTransitionProgress(partialTick);
         if (!(progress >= 1.0F)) {
            float scale = this.transitionState == BaseMenuScreen.TransitionState.OPENING ? this.easeOutBack(progress) : this.easeOutBack(1.0F - progress);
            scale = Math.max(0.001F, scale);
            PoseStack pose = graphics.pose();
            int uiWidth = this.getUiWidth();
            int uiHeight = this.getUiHeight();
            pose.translate((double)uiWidth / 2.0, (double)uiHeight / 2.0, 0.0);
            pose.scale(scale, scale, 1.0F);
            pose.translate((double)(-uiWidth) / 2.0, (double)(-uiHeight) / 2.0, 0.0);
         }
      }
   }

   protected int getLeftPanelSwitchOffset(float partialTick) {
      if (this.panelSwitchState == BaseMenuScreen.PanelSwitchState.NONE) {
         return 0;
      } else {
         float p = this.getPanelSwitchProgress(partialTick);
         if (this.panelSwitchState == BaseMenuScreen.PanelSwitchState.ENTERING) {
            float eased = this.easeOutBack(p);
            return Math.round((eased - 1.0F) * 190.0F);
         } else {
            float eased = this.easeInBack(p);
            return Math.round(-eased * 190.0F);
         }
      }
   }

   protected int getRightPanelSwitchOffset(float partialTick) {
      if (this.panelSwitchState == BaseMenuScreen.PanelSwitchState.NONE) {
         return 0;
      } else {
         float p = this.getPanelSwitchProgress(partialTick);
         if (this.panelSwitchState == BaseMenuScreen.PanelSwitchState.ENTERING) {
            float eased = this.easeOutBack(p);
            return Math.round((1.0F - eased) * 190.0F);
         } else {
            float eased = this.easeInBack(p);
            return Math.round(eased * 190.0F);
         }
      }
   }

   protected int getTopPanelSwitchOffset(float partialTick) {
      if (this.panelSwitchState == BaseMenuScreen.PanelSwitchState.NONE) {
         return 0;
      } else {
         float p = this.getPanelSwitchProgress(partialTick);
         if (this.panelSwitchState == BaseMenuScreen.PanelSwitchState.ENTERING) {
            float eased = this.easeOutBack(p);
            return Math.round((eased - 1.0F) * 90.0F);
         } else {
            float eased = this.easeInBack(p);
            return Math.round(-eased * 90.0F);
         }
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.panelSwitchState == BaseMenuScreen.PanelSwitchState.EXITING) {
         return true;
      } else if (this.transitionState == BaseMenuScreen.TransitionState.CLOSING) {
         return true;
      } else {
         int statsMenuKeyCode = KeyBinds.STATS_MENU.getKey().getValue();
         if (keyCode == statsMenuKeyCode) {
            this.onClose();
            return true;
         } else if (keyCode == 256) {
            this.onClose();
            return true;
         } else {
            Key pressed = InputConstants.getKey(keyCode, scanCode);

            for (BaseMenuScreen.MenuTab tab : SECONDARY_TABS) {
               if (tab.key().isActiveAndMatches(pressed)) {
                  this.switchMenu(tab.factory().get());
                  return true;
               }
            }

            return super.keyPressed(keyCode, scanCode, modifiers);
         }
      }
   }

   public void onClose() {
      this.startCloseTransition();
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      return this.panelSwitchState == BaseMenuScreen.PanelSwitchState.EXITING ? true : super.mouseClicked(mouseX, mouseY, button);
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      return this.panelSwitchState == BaseMenuScreen.PanelSwitchState.EXITING ? true : super.mouseReleased(mouseX, mouseY, button);
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      return this.panelSwitchState == BaseMenuScreen.PanelSwitchState.EXITING ? true : super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (this.panelSwitchState == BaseMenuScreen.PanelSwitchState.EXITING) {
         return true;
      } else if (Screen.hasAltDown()) {
         this.targetTooltipScrollY += (float)(scrollY * 15.0);
         if (this.targetTooltipScrollY > 0.0F) {
            this.targetTooltipScrollY = 0.0F;
         }

         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
   }

   private void startPanelEnterTransition() {
      this.panelSwitchState = BaseMenuScreen.PanelSwitchState.ENTERING;
      this.panelSwitchAnimationStartTime = System.currentTimeMillis();
      this.pendingSwitchScreen = null;
   }

   private void startPanelExitTransition(Screen nextScreen) {
      if (this.panelSwitchState != BaseMenuScreen.PanelSwitchState.EXITING) {
         this.pendingSwitchScreen = nextScreen;
         this.panelSwitchState = BaseMenuScreen.PanelSwitchState.EXITING;
         this.panelSwitchAnimationStartTime = System.currentTimeMillis();
      }
   }

   private void startOpenTransition() {
      this.transitionState = BaseMenuScreen.TransitionState.OPENING;
      this.animationStartTime = System.currentTimeMillis();
   }

   private void startCloseTransition() {
      if (this.transitionState != BaseMenuScreen.TransitionState.CLOSING) {
         long now = System.currentTimeMillis();
         this.transitionState = BaseMenuScreen.TransitionState.CLOSING;
         this.animationStartTime = now;
         statsMenuReopenBlockedUntilMs = Math.max(statsMenuReopenBlockedUntilMs, now + 450L);

         while (KeyBinds.STATS_MENU.consumeClick()) {
         }
      }
   }

   protected float getTransitionProgress(float partialTick) {
      long elapsed = System.currentTimeMillis() - this.animationStartTime;
      return Mth.clamp(((float)elapsed + partialTick * 50.0F) / 200.0F, 0.0F, 1.0F);
   }

   protected float getPanelSwitchProgress(float partialTick) {
      long elapsed = System.currentTimeMillis() - this.panelSwitchAnimationStartTime;
      long duration = this.panelSwitchState == BaseMenuScreen.PanelSwitchState.EXITING ? 140L : 520L;
      return Mth.clamp(((float)elapsed + partialTick * 50.0F) / (float)duration, 0.0F, 1.0F);
   }

   private float easeOutBack(float t) {
      float c1 = 1.70158F;
      float c3 = c1 + 1.0F;
      float p = t - 1.0F;
      return 1.0F + c3 * p * p * p + c1 * p * p;
   }

   private float easeInBack(float t) {
      float c1 = 1.70158F;
      float c3 = c1 + 1.0F;
      return c3 * t * t * t - c1 * t * t;
   }

   protected int getAdjustedModelScale(int baseScale) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         return baseScale;
      } else {
         float[] inverseScale = new float[]{1.0F};
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
            Character character = stats.getCharacter();
            Float[] resolved = character.getResolvedModelScaling();
            float currentScale = (resolved[0] + resolved[1]) / 2.0F;
            if (currentScale > 1.0F) {
               inverseScale[0] = 0.9375F / currentScale;
            }
         });
         return (int)((float)baseScale * inverseScale[0]);
      }
   }

   protected void rebuildWidgetsWithoutTransition() {
      this.suppressOpenAnimationOnce = true;
      this.rebuildWidgets();
      this.suppressOpenAnimationOnce = false;
   }

   public static record MenuTab(KeyMapping key, Supplier<Screen> factory) {
   }

   private static enum PanelSwitchState {
      NONE,
      ENTERING,
      EXITING;
   }

   private static enum TransitionState {
      NONE,
      OPENING,
      CLOSING;
   }
}
