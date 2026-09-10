package com.dragonminez.client.gui;

import com.dragonminez.client.gui.buttons.TexturedTextButton;
import com.dragonminez.client.gui.character.util.ScaledScreen;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.TravelToPlanetC2S;
import com.dragonminez.common.spacepod.SpacePodDestinationDefinition;
import com.dragonminez.common.spacepod.SpacePodDestinationRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SpacePodScreen extends ScaledScreen {
   private static final ResourceLocation MENU_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menubig.png");
   private static final ResourceLocation BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/characterbuttons.png");
   private static final ResourceLocation ICONS_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/spaceshipicons.png");
   private static final int PANEL_WIDTH = 141;
   private static final int PANEL_HEIGHT = 213;
   private static final int ITEM_HEIGHT = 24;
   private static final int MAX_VISIBLE_ITEMS = 7;
   private static final int ICON_SIZE = 11;
   private static final int ICON_X_COLOR = 3;
   private static final int ICON_X_GRAY = 20;
   private static final int ICON_Y_START = 3;
   private static final int ICON_Y_STEP = 14;
   private final List<SpacePodScreen.PlanetDestination> destinations = new ArrayList<>();
   private int selectedIndex = -1;
   private int guiLeft;
   private int guiTop;
   private float targetScroll = 0.0F;
   private float currentScroll = 0.0F;
   private float maxScroll = 0.0F;
   private boolean isScrolling = false;
   private TexturedTextButton travelButton;

   public SpacePodScreen() {
      super(Component.literal("Space Pod").withStyle(Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath("dragonminez", "smooth"))));
   }

   protected void init() {
      super.init();
      this.guiLeft = (this.getUiWidth() - 141) / 2;
      this.guiTop = (this.getUiHeight() - 213) / 2;
      this.loadDestinations();
      this.travelButton = new TexturedTextButton.Builder()
         .position(this.guiLeft + 30, this.getUiHeight() - 30)
         .size(74, 20)
         .texture(BUTTON_TEXTURE)
         .textureCoords(0, 28, 0, 48)
         .textureSize(74, 20)
         .message(this.tr("gui.dragonminez.travel", new Object[0]))
         .onPress(btn -> this.initiateTravel())
         .build();
      this.travelButton.visible = false;
      this.addRenderableWidget(this.travelButton);
   }

   private void loadDestinations() {
      this.destinations.clear();
      if (this.minecraft != null && this.minecraft.player != null) {
         for (SpacePodDestinationDefinition definition : SpacePodDestinationRegistry.getClientDestinations()) {
            boolean unlocked = definition.unlockRules().test(this.minecraft.player);
            if (unlocked || definition.showWhenLocked()) {
               ResourceLocation iconTexture = definition.iconTexture() != null ? ResourceLocation.tryParse(definition.iconTexture()) : null;
               this.destinations
                  .add(
                     new SpacePodScreen.PlanetDestination(
                        definition.id(), definition.name(), definition.translate(), definition.dimension(), definition.iconIndex(), iconTexture, unlocked
                     )
                  );
            }
         }
      }
   }

   private void initiateTravel() {
      if (this.selectedIndex >= 0 && this.selectedIndex < this.destinations.size()) {
         SpacePodScreen.PlanetDestination dest = this.destinations.get(this.selectedIndex);
         if (dest.unlocked && dest.dimensionId != null && !dest.dimensionId.isBlank() && dest.id != null && !dest.id.isBlank()) {
            String currentDimensionId = this.minecraft.player.level().dimension().location().toString();
            if (!currentDimensionId.equals(dest.dimensionId)) {
               NetworkHandler.sendToServer(new TravelToPlanetC2S(dest.id));
               this.onClose();
            }
         }
      }
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(graphics, mouseX, mouseY, partialTick);
      int uiMouseX = (int)this.toUiX((double)mouseX);
      int uiMouseY = (int)this.toUiY((double)mouseY);
      this.beginUiScale(graphics);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      graphics.blit(MENU_TEXTURE, this.guiLeft, this.guiTop, 0.0F, 0.0F, 141, 213, 256, 256);
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.spacepod.title", new Object[0]), this.getUiWidth() / 2, this.guiTop + 18, -10496
      );
      this.renderPlanetList(graphics, uiMouseX, uiMouseY);
      super.render(graphics, uiMouseX, uiMouseY, partialTick);
      this.endUiScale(graphics);
   }

   private void renderPlanetList(GuiGraphics graphics, int uiMouseX, int uiMouseY) {
      int listLeft = this.guiLeft + 10;
      int listTop = this.guiTop + 35;
      int listWidth = 116;
      int viewHeight = 168;
      int totalHeight = this.destinations.size() * 24;
      this.maxScroll = (float)Math.max(0, totalHeight - viewHeight);
      this.targetScroll = Mth.clamp(this.targetScroll, 0.0F, this.maxScroll);
      float tickDelta = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
      this.currentScroll = Mth.lerp(tickDelta * 0.4F, this.currentScroll, this.targetScroll);
      int scLeft = this.toScreenCoord((double)listLeft);
      int scTop = this.toScreenCoord((double)listTop);
      int scRight = this.toScreenCoord((double)(listLeft + listWidth));
      int scBottom = this.toScreenCoord((double)(listTop + viewHeight));
      graphics.enableScissor(scLeft, scTop, scRight, scBottom);
      graphics.pose().pushPose();
      graphics.pose().translate(0.0F, -this.currentScroll, 0.0F);

      for (int i = 0; i < this.destinations.size(); i++) {
         int itemY = listTop + i * 24;
         if ((float)(itemY + 24) >= (float)listTop + this.currentScroll && (float)itemY <= (float)(listTop + viewHeight) + this.currentScroll) {
            SpacePodScreen.PlanetDestination dest = this.destinations.get(i);
            boolean isSelected = i == this.selectedIndex;
            if (!dest.unlocked) {
               graphics.fill(listLeft, itemY, listLeft + listWidth, itemY + 24, 805306368);
            } else {
               boolean isHovered = uiMouseX >= listLeft
                  && uiMouseX < listLeft + listWidth
                  && (float)uiMouseY >= (float)itemY - this.currentScroll
                  && (float)uiMouseY < (float)(itemY + 24) - this.currentScroll;
               int color = isSelected ? -2133545161 : (isHovered ? -2141891243 : 0);
               graphics.fill(listLeft, itemY, listLeft + listWidth, itemY + 24, color);
               if (isSelected) {
                  graphics.renderOutline(listLeft, itemY, listWidth, 24, -10496);
               }
            }

            int iconYCentered = itemY + 6;
            this.renderDestinationIcon(graphics, dest, listLeft + 5, iconYCentered);
            int textColor;
            Component textToDraw;
            if (dest.unlocked) {
               textToDraw = this.destinationName(dest).copy().withStyle(ChatFormatting.BOLD);
               textColor = 2154751;
            } else {
               textToDraw = this.txt("???").withStyle(ChatFormatting.BOLD);
               textColor = 7632504;
            }

            TextUtil.drawStringWithBorder(graphics, this.font, textToDraw, listLeft + 25, itemY + 8, textColor);
         }
      }

      graphics.pose().popPose();
      graphics.disableScissor();
      if (this.maxScroll > 0.0F) {
         this.renderScrollbar(graphics, listTop, viewHeight, totalHeight);
      }
   }

   private void renderDestinationIcon(GuiGraphics graphics, SpacePodScreen.PlanetDestination dest, int x, int y) {
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      if (dest.iconTexture != null) {
         graphics.blit(dest.iconTexture, x, y, 0.0F, 0.0F, 11, 11, 11, 11);
      } else {
         int iconIndex = dest.iconIndex != null ? dest.iconIndex : 0;
         int u = dest.unlocked ? 3 : 20;
         int v = 3 + iconIndex * 14;
         graphics.blit(ICONS_TEXTURE, x, y, (float)u, (float)v, 11, 11, 256, 256);
      }
   }

   private Component destinationName(SpacePodScreen.PlanetDestination destination) {
      return destination.translate ? this.tr(destination.name, new Object[0]) : this.txt(destination.name);
   }

   private void renderScrollbar(GuiGraphics graphics, int listTop, int viewHeight, int totalHeight) {
      int scrollBarX = this.guiLeft + 141 - 12;
      graphics.fill(scrollBarX, listTop, scrollBarX + 3, listTop + viewHeight, -13421773);
      float scrollPercent = this.currentScroll / this.maxScroll;
      float visiblePercent = (float)viewHeight / (float)totalHeight;
      int indicatorHeight = Math.max(20, (int)((float)viewHeight * visiblePercent));
      int indicatorY = listTop + (int)((float)(viewHeight - indicatorHeight) * scrollPercent);
      graphics.fill(scrollBarX, indicatorY, scrollBarX + 3, indicatorY + indicatorHeight, -5592406);
   }

   private float calculateScrollPercent(double uiY, int startY, int viewHeight) {
      float percent = (float)(uiY - (double)startY) / (float)viewHeight;
      return Mth.clamp(percent, 0.0F, 1.0F);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (super.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else {
         double uiX = this.toUiX(mouseX);
         double uiY = this.toUiY(mouseY);
         int listLeft = this.guiLeft + 10;
         int listTop = this.guiTop + 35;
         int listWidth = 116;
         int viewHeight = 168;
         if (this.maxScroll > 0.0F
            && uiX >= (double)(listLeft + listWidth)
            && uiX <= (double)(this.guiLeft + 141)
            && uiY >= (double)listTop
            && uiY <= (double)(listTop + viewHeight)) {
            this.isScrolling = true;
            this.targetScroll = this.calculateScrollPercent(uiY, listTop, viewHeight) * this.maxScroll;
            return true;
         } else {
            if (uiX >= (double)listLeft && uiX < (double)(listLeft + listWidth) && uiY >= (double)listTop && uiY <= (double)(listTop + viewHeight)) {
               int relativeY = (int)(uiY - (double)listTop + (double)this.currentScroll);
               int index = relativeY / 24;
               if (index >= 0 && index < this.destinations.size()) {
                  SpacePodScreen.PlanetDestination dest = this.destinations.get(index);
                  if (dest.unlocked) {
                     this.selectDestination(index);
                     Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI((SoundEvent)MainSounds.UI_MENU_SWITCH.get(), 1.0F));
                  }

                  return true;
               }
            }

            return false;
         }
      }
   }

   private void selectDestination(int index) {
      if (this.selectedIndex != index) {
         this.selectedIndex = index;
         this.travelButton.visible = true;
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (this.maxScroll > 0.0F) {
         this.targetScroll = (float)Mth.clamp((double)this.targetScroll - Math.signum(scrollY) * 24.0 * 2.0, 0.0, (double)this.maxScroll);
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.isScrolling = false;
      return super.mouseReleased(mouseX, mouseY, button);
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.isScrolling && this.maxScroll > 0.0F) {
         double uiY = this.toUiY(mouseY);
         int listTop = this.guiTop + 35;
         int viewHeight = 168;
         this.targetScroll = this.calculateScrollPercent(uiY, listTop, viewHeight) * this.maxScroll;
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static class PlanetDestination {
      String id;
      String name;
      boolean translate;
      String dimensionId;
      Integer iconIndex;
      ResourceLocation iconTexture;
      boolean unlocked;

      public PlanetDestination(String id, String name, boolean translate, String dimensionId, Integer iconIndex, ResourceLocation iconTexture, boolean unlocked) {
         this.id = id;
         this.name = name;
         this.translate = translate;
         this.dimensionId = dimensionId;
         this.iconIndex = iconIndex;
         this.iconTexture = iconTexture;
         this.unlocked = unlocked;
      }
   }
}
