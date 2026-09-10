package com.dragonminez.client.gui;

import com.dragonminez.client.gui.buttons.TexturedTextButton;
import com.dragonminez.client.gui.character.util.ScaledScreen;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.GrantWishC2S;
import com.dragonminez.common.wish.Wish;
import com.dragonminez.common.wish.WishManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WishesScreen extends ScaledScreen {
   private static final ResourceLocation MENU_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menubig.png");
   private static final ResourceLocation BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/characterbuttons.png");
   private static final int PANEL_WIDTH = 141;
   private static final int PANEL_HEIGHT = 213;
   private static final int ITEM_HEIGHT = 20;
   private static final int MAX_VISIBLE_ITEMS = 8;
   private final String dragonType;
   private final int maxWishesToSelect;
   private final List<Wish> availableWishes;
   private final List<Integer> selectedIndices = new ArrayList<>();
   private int guiLeft;
   private int guiTop;
   private float targetScroll = 0.0F;
   private float currentScroll = 0.0F;
   private float maxScroll = 0.0F;
   private boolean isScrolling = false;
   private TexturedTextButton confirmButton;

   public WishesScreen(String dragonType, int wishCount) {
      super(Component.literal("Wishes").withStyle(Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath("dragonminez", "smooth"))));
      this.dragonType = dragonType;
      this.maxWishesToSelect = wishCount;
      this.availableWishes = WishManager.getClientWishes(dragonType);
   }

   protected void init() {
      super.init();
      this.guiLeft = (this.getUiWidth() - 141) / 2;
      this.guiTop = (this.getUiHeight() - 213) / 2;
      this.confirmButton = new TexturedTextButton.Builder()
         .position(this.guiLeft + 30, this.getUiHeight() - 30)
         .size(74, 20)
         .texture(BUTTON_TEXTURE)
         .textureCoords(0, 28, 0, 48)
         .textureSize(74, 20)
         .message(this.tr("gui.dragonminez.customization.select", new Object[0]))
         .onPress(btn -> this.confirmWishes())
         .build();
      this.confirmButton.visible = false;
      this.addRenderableWidget(this.confirmButton);
   }

   private void confirmWishes() {
      NetworkHandler.sendToServer(new GrantWishC2S(this.dragonType, this.selectedIndices));
      this.onClose();
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
         graphics,
         this.font,
         this.tr("gui.dragonminez.wishes_title", new Object[]{this.selectedIndices.size(), this.maxWishesToSelect}),
         this.getUiWidth() / 2,
         this.guiTop + 18,
         -10496
      );
      this.renderWishesList(graphics, uiMouseX, uiMouseY);
      super.render(graphics, uiMouseX, uiMouseY, partialTick);
      this.renderTooltip(graphics, uiMouseX, uiMouseY);
      this.endUiScale(graphics);
   }

   private void renderWishesList(GuiGraphics graphics, int uiMouseX, int uiMouseY) {
      int listLeft = this.guiLeft + 10;
      int listTop = this.guiTop + 35;
      int listWidth = 116;
      int viewHeight = 160;
      int totalHeight = this.availableWishes.size() * 20;
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

      for (int i = 0; i < this.availableWishes.size(); i++) {
         int itemY = listTop + i * 20;
         if ((float)(itemY + 20) >= (float)listTop + this.currentScroll && (float)itemY <= (float)(listTop + viewHeight) + this.currentScroll) {
            Wish wish = this.availableWishes.get(i);
            boolean isSelected = this.selectedIndices.contains(i);
            boolean isHovered = uiMouseX >= listLeft
               && uiMouseX < listLeft + listWidth
               && (float)uiMouseY >= (float)itemY - this.currentScroll
               && (float)uiMouseY < (float)(itemY + 20) - this.currentScroll;
            int color;
            if (isSelected) {
               color = -2133545161;
            } else if (isHovered) {
               color = -2141891243;
            } else {
               color = 0;
            }

            graphics.fill(listLeft, itemY, listLeft + listWidth, itemY + 20, color);
            TextUtil.drawStringWithBorder(graphics, this.font, this.tr(wish.getName(), new Object[0]), listLeft + 5, itemY + 6, 16777215);
            if (isSelected) {
               graphics.renderOutline(listLeft, itemY, listWidth, 20, -10496);
            }
         }
      }

      graphics.pose().popPose();
      graphics.disableScissor();
      if (this.maxScroll > 0.0F) {
         int scrollBarX = this.guiLeft + 141 - 12;
         graphics.fill(scrollBarX, listTop, scrollBarX + 3, listTop + viewHeight, -13421773);
         float scrollPercent = this.currentScroll / this.maxScroll;
         float visiblePercent = (float)viewHeight / (float)totalHeight;
         int indicatorHeight = Math.max(20, (int)((float)viewHeight * visiblePercent));
         int indicatorY = listTop + (int)((float)(viewHeight - indicatorHeight) * scrollPercent);
         graphics.fill(scrollBarX, indicatorY, scrollBarX + 3, indicatorY + indicatorHeight, -5592406);
      }
   }

   private void renderTooltip(GuiGraphics graphics, int uiMouseX, int uiMouseY) {
      int listLeft = this.guiLeft + 10;
      int listTop = this.guiTop + 35;
      int listWidth = 116;
      int viewHeight = 160;
      if (uiMouseX >= listLeft && uiMouseX < listLeft + listWidth && uiMouseY >= listTop && uiMouseY <= listTop + viewHeight) {
         int relativeY = (int)((float)(uiMouseY - listTop) + this.currentScroll);
         int index = relativeY / 20;
         if (index >= 0 && index < this.availableWishes.size()) {
            Wish wish = this.availableWishes.get(index);
            graphics.renderTooltip(this.font, this.tr(wish.getDescription(), new Object[0]), uiMouseX, uiMouseY);
         }
      }
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
         int viewHeight = 160;
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
               int index = relativeY / 20;
               if (index >= 0 && index < this.availableWishes.size()) {
                  this.toggleSelection(index);
                  Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                  return true;
               }
            }

            return false;
         }
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (this.maxScroll > 0.0F) {
         this.targetScroll = (float)Mth.clamp((double)this.targetScroll - Math.signum(scrollY) * 20.0 * 2.0, 0.0, (double)this.maxScroll);
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
         int viewHeight = 160;
         this.targetScroll = this.calculateScrollPercent(uiY, listTop, viewHeight) * this.maxScroll;
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   private void toggleSelection(int index) {
      if (this.selectedIndices.contains(index)) {
         this.selectedIndices.remove(Integer.valueOf(index));
      } else if (this.maxWishesToSelect == 1) {
         this.selectedIndices.clear();
         this.selectedIndices.add(index);
      } else if (this.selectedIndices.size() < this.maxWishesToSelect) {
         this.selectedIndices.add(index);
      }

      this.confirmButton.visible = this.selectedIndices.size() == this.maxWishesToSelect;
   }

   public boolean isPauseScreen() {
      return false;
   }
}
