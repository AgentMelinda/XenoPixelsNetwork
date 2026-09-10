package com.dragonminez.client.gui.character;

import com.dragonminez.client.gui.buttons.CustomTextureButton;
import com.dragonminez.client.gui.buttons.TexturedTextButton;
import com.dragonminez.client.gui.character.minigames.ControlGameScreen;
import com.dragonminez.client.gui.character.minigames.GravityGameScreen;
import com.dragonminez.client.gui.character.minigames.MemoryGameScreen;
import com.dragonminez.client.gui.character.minigames.PrecisionGameScreen;
import com.dragonminez.client.gui.character.minigames.RythmGameScreen;
import com.dragonminez.client.gui.character.util.BaseMenuScreen;
import com.dragonminez.client.render.EntityPreviewRenderContext;
import com.dragonminez.client.util.ScrollbarState;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.TrainingConfig;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.SummonPlayerShadowDummyC2S;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class MinigamesScreen extends BaseMenuScreen {
   private static final ResourceLocation MENU_BIG = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menubig.png");
   private static final ResourceLocation BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/menubuttons.png");
   private static final ResourceLocation CHAR_BUTTONS = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/characterbuttons.png");
   private static final boolean DEBUG_UNLOCK_ALL = false;
   private static final String[] MINIGAMES = new String[]{"rhythm", "control", "memory", "precision", "gravity", "shadowdummy"};
   private static final int LIST_ITEM_HEIGHT = 20;
   private int selectedIndex = 0;
   private float descScrollY = 0.0F;
   private float targetDescScrollY = 0.0F;
   private int descContentHeight = 0;
   private int descViewportHeight = 0;
   private final ScrollbarState descBar = new ScrollbarState();
   private int shadowDummyPercent = 50;
   private TexturedTextButton playButton;
   private CustomTextureButton shadowDecBtn;
   private CustomTextureButton shadowIncBtn;

   public MinigamesScreen() {
      super(Component.literal("hub"));
   }

   @Override
   protected void init() {
      super.init();
      this.initPlayButton();
   }

   private void initPlayButton() {
      int rightPanelX = this.getUiWidth() - 158;
      int centerY = this.getUiHeight() / 2;
      int playY = centerY - 105 + 213 - 28;
      this.playButton = new TexturedTextButton.Builder()
         .position(rightPanelX + 18, playY)
         .size(105, 20)
         .texture(BUTTON_TEXTURE)
         .textureCoords(0, 50, 0, 50)
         .textureSize(105, 20)
         .message(this.tr("gui.dragonminez.minigames.play", new Object[0]))
         .onPress(b -> this.playSelected())
         .build();
      this.addRenderableWidget(this.playButton);
      this.shadowDecBtn = new CustomTextureButton.Builder()
         .position(rightPanelX + 14, centerY - 8)
         .size(14, 11)
         .texture(CHAR_BUTTONS)
         .textureCoords(32, 0, 32, 14)
         .textureSize(8, 14)
         .onPress(b -> this.adjustShadowPercent(-5))
         .build();
      this.addRenderableWidget(this.shadowDecBtn);
      this.shadowIncBtn = new CustomTextureButton.Builder()
         .position(rightPanelX + 113, centerY - 8)
         .size(14, 11)
         .texture(CHAR_BUTTONS)
         .textureCoords(20, 0, 20, 14)
         .textureSize(8, 14)
         .onPress(b -> this.adjustShadowPercent(5))
         .build();
      this.addRenderableWidget(this.shadowIncBtn);
      this.refreshPlayButton();
   }

   private void adjustShadowPercent(int delta) {
      this.shadowDummyPercent = Mth.clamp(this.shadowDummyPercent + delta, 25, 75);
   }

   private boolean isShadowDummyEntry(int index) {
      return "shadowdummy".equals(MINIGAMES[index]);
   }

   private boolean hasAccess(int index) {
      if (this.isShadowDummyEntry(index)) {
         return this.hasShadowDummyAccess();
      } else {
         String id = MINIGAMES[index];
         TrainingConfig.MinigameSettings settings = ConfigManager.getTrainingConfig().getSettings(id);
         if (settings.isUnlockedByDefault()) {
            return true;
         } else {
            Minecraft mc = Minecraft.getInstance();
            return mc.player == null
               ? false
               : StatsProvider.get(StatsCapability.INSTANCE, mc.player).map(d -> d.getCharacter().isMinigameKnown(id)).orElse(false);
         }
      }
   }

   private boolean hasShadowDummyAccess() {
      Minecraft mc = Minecraft.getInstance();
      return mc.player == null
         ? false
         : StatsProvider.get(StatsCapability.INSTANCE, mc.player)
            .map(d -> d.getStatus().getShadowDummyKillCount() > 0 && d.getSkills().hasSkill("kicontrol") && d.getSkills().getSkillLevel("kimanipulation") >= 5)
            .orElse(false);
   }

   private String master(int index) {
      return ConfigManager.getTrainingConfig().getSettings(MINIGAMES[index]).getMasterName();
   }

   private void refreshPlayButton() {
      boolean isShadow = this.isShadowDummyEntry(this.selectedIndex);
      boolean access = this.hasAccess(this.selectedIndex);
      if (this.playButton != null) {
         this.playButton.active = access;
         this.playButton.visible = access;
         if (isShadow) {
            this.playButton.setMessage(this.tr("gui.dragonminez.shadow_dummy.summon", new Object[0]));
         } else {
            this.playButton.setMessage(this.tr("gui.dragonminez.minigames.play", new Object[0]));
         }
      }

      if (this.shadowDecBtn != null) {
         this.shadowDecBtn.visible = isShadow && access;
      }

      if (this.shadowIncBtn != null) {
         this.shadowIncBtn.visible = isShadow && access;
      }
   }

   private void playSelected() {
      if (this.hasAccess(this.selectedIndex) && this.minecraft != null) {
         if (this.isShadowDummyEntry(this.selectedIndex)) {
            NetworkHandler.sendToServer(new SummonPlayerShadowDummyC2S(this.shadowDummyPercent));
         } else {
            String var1 = MINIGAMES[this.selectedIndex];
            switch (var1) {
               case "rhythm":
                  this.minecraft.setScreen(new RythmGameScreen());
                  break;
               case "control":
                  this.minecraft.setScreen(new ControlGameScreen());
                  break;
               case "memory":
                  this.minecraft.setScreen(new MemoryGameScreen());
                  break;
               case "precision":
                  this.minecraft.setScreen(new PrecisionGameScreen());
                  break;
               case "gravity":
                  this.minecraft.setScreen(new GravityGameScreen());
            }
         }
      }
   }

   @Override
   public void tick() {
      super.tick();
      this.descScrollY = Mth.lerp(0.5F, this.descScrollY, this.targetDescScrollY);
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      if (this.isNotAnimating()) {
         this.renderBackground(graphics, mouseX, mouseY, partialTick);
      }

      int uiMouseX = (int)Math.round(this.toUiX((double)mouseX));
      int uiMouseY = (int)Math.round(this.toUiY((double)mouseY));
      this.beginUiScale(graphics);
      this.applyZoom(graphics, partialTick);
      this.renderPlayerModel(graphics, this.getUiWidth() / 2 + 5, this.getUiHeight() / 2 + 70, 75, (float)uiMouseX, (float)uiMouseY);
      int leftOffset = this.getLeftPanelSwitchOffset(partialTick);
      graphics.pose().pushPose();
      graphics.pose().translate((float)leftOffset, 0.0F, 0.0F);
      this.renderLeftPanel(graphics, uiMouseX - leftOffset, uiMouseY);
      graphics.pose().popPose();
      int rightOffset = this.getRightPanelSwitchOffset(partialTick);
      graphics.pose().pushPose();
      graphics.pose().translate((float)rightOffset, 0.0F, 0.0F);
      this.renderRightPanel(graphics, uiMouseX - rightOffset, uiMouseY);
      graphics.pose().popPose();
      int rightBase = this.getUiWidth() - 158;
      if (this.playButton != null) {
         this.playButton.setX(rightBase + 18 + rightOffset);
      }

      if (this.shadowDecBtn != null) {
         this.shadowDecBtn.setX(rightBase + 14 + rightOffset);
      }

      if (this.shadowIncBtn != null) {
         this.shadowIncBtn.setX(rightBase + 113 + rightOffset);
      }

      super.render(graphics, uiMouseX, uiMouseY, partialTick);
      this.endUiScale(graphics);
   }

   private void renderLeftPanel(GuiGraphics graphics, int mouseX, int mouseY) {
      int leftPanelX = 12;
      int centerY = this.getUiHeight() / 2;
      int leftPanelY = centerY - 105;
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      graphics.blit(MENU_BIG, 12, centerY - 105, 0.0F, 0.0F, 141, 213, 256, 256);
      graphics.blit(MENU_BIG, 29, centerY - 95, 142.0F, 22.0F, 107, 21, 256, 256);
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.minigames.list", new Object[0]).withStyle(ChatFormatting.BOLD), leftPanelX + 70, leftPanelY + 17, -10496
      );
      int startY = leftPanelY + 38;

      for (int i = 0; i < MINIGAMES.length; i++) {
         int itemY = startY + i * 20;
         boolean selected = i == this.selectedIndex;
         boolean hovered = this.isOverListItem((double)mouseX, (double)mouseY, i);
         int color;
         if (selected) {
            color = -10496;
         } else if (hovered) {
            color = -8585770;
         } else if (this.hasAccess(i)) {
            color = -1;
         } else {
            color = -7829368;
         }

         Component name = this.tr("gui.dragonminez.minigame." + MINIGAMES[i], new Object[0]);
         TextUtil.drawStringWithBorder(graphics, this.font, name, leftPanelX + 20, itemY, color);
      }
   }

   private boolean isOverListItem(double uiMouseX, double uiMouseY, int index) {
      int leftPanelX = 12;
      int centerY = this.getUiHeight() / 2;
      int startY = centerY - 105 + 38;
      int itemY = startY + index * 20;
      return uiMouseX >= (double)(leftPanelX + 18)
         && uiMouseX <= (double)(leftPanelX + 123)
         && uiMouseY >= (double)(itemY - 2)
         && uiMouseY <= (double)(itemY + 10);
   }

   private void renderRightPanel(GuiGraphics graphics, int mouseX, int mouseY) {
      int rightPanelX = this.getUiWidth() - 158;
      int centerY = this.getUiHeight() / 2;
      int rightPanelY = centerY - 105;
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      graphics.blit(MENU_BIG, this.getUiWidth() - 158, centerY - 105, 0.0F, 0.0F, 141, 213, 256, 256);
      graphics.blit(MENU_BIG, this.getUiWidth() - 141, centerY - 95, 142.0F, 22.0F, 107, 21, 256, 256);
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.minigame." + MINIGAMES[this.selectedIndex], new Object[0]).withStyle(ChatFormatting.BOLD),
         rightPanelX + 70,
         rightPanelY + 17,
         -10496
      );
      if (this.isShadowDummyEntry(this.selectedIndex)) {
         this.renderShadowDummyPanel(graphics, rightPanelX, rightPanelY, centerY);
      } else {
         this.renderDescription(graphics, rightPanelX, rightPanelY);
         if (!this.hasAccess(this.selectedIndex)) {
            int hintY = rightPanelY + 213 - 28 + 6;
            TextUtil.drawCenteredStringWithBorder(
               graphics,
               this.font,
               this.tr("gui.dragonminez.minigames.learn", new Object[0])
                  .append(" ")
                  .append(this.tr("entity.dragonminez.questnpc." + this.master(this.selectedIndex), new Object[0])),
               rightPanelX + 70,
               hintY,
               -34953
            );
         }
      }
   }

   private void renderShadowDummyPanel(GuiGraphics graphics, int panelX, int panelY, int centerY) {
      boolean access = this.hasShadowDummyAccess();
      int hitColor = -11141291;
      int missColor = -43691;
      if (!access) {
         int y = panelY + 42;
         y = this.drawWrappedCentered(graphics, this.tr("gui.dragonminez.shadow_dummy.req_title", new Object[0]), panelX, y, -34953) + 4;
         y = this.drawWrappedCentered(
               graphics,
               this.tr("gui.dragonminez.shadow_dummy.req_kill", new Object[0]),
               panelX,
               y,
               this.clientShadowDummyKillCount() > 0 ? hitColor : missColor
            )
            + 3;
         y = this.drawWrappedCentered(
               graphics,
               this.tr("gui.dragonminez.shadow_dummy.req_kicontrol", new Object[0]),
               panelX,
               y,
               this.clientHasSkill("kicontrol") ? hitColor : missColor
            )
            + 3;
         this.drawWrappedCentered(
            graphics,
            this.tr("gui.dragonminez.shadow_dummy.req_kimanip", new Object[0]),
            panelX,
            y,
            this.clientSkillLevel("kimanipulation") >= 5 ? hitColor : missColor
         );
      } else {
         this.drawWrappedCentered(graphics, this.tr("gui.dragonminez.shadow_dummy.desc_short", new Object[0]), panelX, panelY + 40, -2039584);
         TextUtil.drawCenteredStringWithBorder(
            graphics, this.font, this.tr("gui.dragonminez.shadow_dummy.percent", new Object[]{this.shadowDummyPercent}), panelX + 70, centerY - 4, -10496
         );
      }
   }

   private int drawWrappedCentered(GuiGraphics graphics, FormattedText text, int panelX, int startY, int color) {
      int wrapWidth = 157;
      List<FormattedCharSequence> lines = this.font.split(text, wrapWidth);
      int cxScaled = (int)((float)(panelX + 70) / 0.75F);
      graphics.pose().pushPose();
      graphics.pose().scale(0.75F, 0.75F, 0.75F);
      int y = startY;

      for (FormattedCharSequence line : lines) {
         TextUtil.drawCenteredStringWithBorder(graphics, this.font, line, cxScaled, (int)((float)y / 0.75F), color);
         y += 9;
      }

      graphics.pose().popPose();
      return y;
   }

   private int clientShadowDummyKillCount() {
      Minecraft mc = Minecraft.getInstance();
      return mc.player == null ? 0 : StatsProvider.get(StatsCapability.INSTANCE, mc.player).map(d -> d.getStatus().getShadowDummyKillCount()).orElse(0);
   }

   private boolean clientHasSkill(String skill) {
      Minecraft mc = Minecraft.getInstance();
      return mc.player == null ? false : StatsProvider.get(StatsCapability.INSTANCE, mc.player).map(d -> d.getSkills().hasSkill(skill)).orElse(false);
   }

   private int clientSkillLevel(String skill) {
      Minecraft mc = Minecraft.getInstance();
      return mc.player == null ? 0 : StatsProvider.get(StatsCapability.INSTANCE, mc.player).map(d -> d.getSkills().getSkillLevel(skill)).orElse(0);
   }

   private void renderDescription(GuiGraphics graphics, int panelX, int panelY) {
      int textX = panelX + 12;
      int top = panelY + 40;
      this.descViewportHeight = 142;
      int wrapWidth = 157;
      FormattedText desc = this.tr("gui.dragonminez.minigame." + MINIGAMES[this.selectedIndex] + ".desc", new Object[0]);
      List<FormattedCharSequence> lines = this.font.split(desc, wrapWidth);
      int lineHeight = 9;
      this.descContentHeight = lines.size() * lineHeight;
      graphics.enableScissor(
         this.toScreenCoord((double)(textX - 2)),
         this.toScreenCoord((double)top),
         this.toScreenCoord((double)(panelX + 130)),
         this.toScreenCoord((double)(top + this.descViewportHeight))
      );
      graphics.pose().pushPose();
      graphics.pose().scale(0.75F, 0.75F, 0.75F);
      int drawY = top - (int)this.descScrollY;

      for (FormattedCharSequence line : lines) {
         TextUtil.drawStringWithBorder(graphics, this.font, line, (int)((float)textX / 0.75F), (int)((float)drawY / 0.75F), -2039584);
         drawY += lineHeight;
      }

      graphics.pose().popPose();
      graphics.disableScissor();
      int maxScroll = Math.max(0, this.descContentHeight - this.descViewportHeight);
      this.descBar.update(panelX + 130, 3, top, this.descViewportHeight, (float)maxScroll);
      if (maxScroll > 0) {
         int barX = panelX + 130;
         graphics.fill(barX, top, barX + 3, top + this.descViewportHeight, -13421773);
         float percent = Mth.clamp(this.descScrollY / (float)maxScroll, 0.0F, 1.0F);
         int indicatorH = Math.max(20, (int)((float)this.descViewportHeight * ((float)this.descViewportHeight / (float)this.descContentHeight)));
         int indicatorY = top + (int)((float)(this.descViewportHeight - indicatorH) * percent);
         graphics.fill(barX, indicatorY, barX + 3, indicatorY + indicatorH, -5592406);
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      double uiMouseX = this.toUiX(mouseX);
      double uiMouseY = this.toUiY(mouseY);
      if (this.descBar.tryStartDrag(uiMouseX, uiMouseY)) {
         this.targetDescScrollY = this.descBar.scrollFor(uiMouseY);
         return true;
      } else {
         for (int i = 0; i < MINIGAMES.length; i++) {
            if (this.isOverListItem(uiMouseX, uiMouseY, i)) {
               if (this.selectedIndex != i) {
                  this.selectedIndex = i;
                  this.targetDescScrollY = 0.0F;
                  this.descScrollY = 0.0F;
                  this.refreshPlayButton();
               }

               return true;
            }
         }

         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.descBar.isDragging()) {
         this.targetDescScrollY = this.descBar.scrollFor(this.toUiY(mouseY));
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.descBar.isDragging()) {
         this.descBar.stopDrag();
         return true;
      } else {
         return super.mouseReleased(mouseX, mouseY, button);
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      double uiMouseX = this.toUiX(mouseX);
      double uiMouseY = this.toUiY(mouseY);
      int rightPanelX = this.getUiWidth() - 158;
      int centerY = this.getUiHeight() / 2;
      int top = centerY - 105 + 35;
      if (uiMouseX >= (double)rightPanelX
         && uiMouseX <= (double)(rightPanelX + 141)
         && uiMouseY >= (double)top
         && uiMouseY <= (double)(top + this.descViewportHeight)) {
         int maxScroll = Math.max(0, this.descContentHeight - this.descViewportHeight);
         this.targetDescScrollY = Mth.clamp(this.targetDescScrollY - (float)(scrollY * 12.0), 0.0F, (float)maxScroll);
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
   }

   private void renderPlayerModel(GuiGraphics graphics, int x, int y, int scale, float mouseX, float mouseY) {
      LivingEntity player = this.minecraft.player;
      if (player != null) {
         int adjustedScale = this.getAdjustedModelScale(scale);
         float xRotation = (float)Math.atan((double)(((float)y - mouseY) / 40.0F));
         float yRotation = (float)Math.atan((double)(((float)x - mouseX) / 40.0F));
         Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
         Quaternionf cameraOrientation = new Quaternionf().rotateX(xRotation * 20.0F * (float) (Math.PI / 180.0));
         pose.mul(cameraOrientation);
         float yBodyRotO = player.yBodyRot;
         float yRotO = player.getYRot();
         float xRotO = player.getXRot();
         float yHeadRotO = player.yHeadRotO;
         float yHeadRot = player.yHeadRot;
         player.yBodyRot = 180.0F + yRotation * 20.0F;
         player.setYRot(180.0F + yRotation * 40.0F);
         player.setXRot(-xRotation * 20.0F);
         player.yHeadRot = player.getYRot();
         player.yHeadRotO = player.getYRot();
         graphics.pose().pushPose();
         graphics.pose().translate(0.0, 0.0, 150.0);
         EntityPreviewRenderContext.renderEntityInInventory(graphics, x, y, adjustedScale, new Vector3f(0.0F, 0.0F, 0.0F), pose, cameraOrientation, player);
         graphics.pose().popPose();
         player.yBodyRot = yBodyRotO;
         player.setYRot(yRotO);
         player.setXRot(xRotO);
         player.yHeadRotO = yHeadRotO;
         player.yHeadRot = yHeadRot;
      }
   }
}
