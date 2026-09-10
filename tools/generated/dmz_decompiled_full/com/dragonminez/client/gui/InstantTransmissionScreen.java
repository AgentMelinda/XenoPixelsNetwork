package com.dragonminez.client.gui;

import com.dragonminez.client.gui.buttons.CustomTextureButton;
import com.dragonminez.client.gui.buttons.TexturedTextButton;
import com.dragonminez.client.gui.character.util.ScaledScreen;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.network.ITTargetEntry;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.DeleteMasterC2S;
import com.dragonminez.common.network.C2S.InstantTransmissionTravelC2S;
import com.dragonminez.common.network.C2S.InstantTransmissionTravelToPlayerC2S;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
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
public class InstantTransmissionScreen extends ScaledScreen {
   private static final ResourceLocation MENU_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menubig.png");
   private static final ResourceLocation BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/characterbuttons.png");
   private static final int PANEL_WIDTH = 141;
   private static final int PANEL_HEIGHT = 213;
   private static final int ITEM_HEIGHT = 24;
   private static final int MAX_VISIBLE_ITEMS = 7;
   private final List<InstantTransmissionScreen.MasterEntry> destinations = new ArrayList<>();
   private int selectedIndex = -1;
   private final int skillLevel;
   private final String currentDimension;
   private int guiLeft;
   private int guiTop;
   private float targetScroll = 0.0F;
   private float currentScroll = 0.0F;
   private float maxScroll = 0.0F;
   private boolean isScrolling = false;
   private TexturedTextButton travelButton;
   private CustomTextureButton deleteButton;

   public InstantTransmissionScreen(List<ITTargetEntry> entries, int skillLevel) {
      super(Component.literal("Instant Transmission").withStyle(Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath("dragonminez", "smooth"))));
      this.skillLevel = skillLevel;
      this.currentDimension = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.level().dimension().location().toString() : "";
      this.loadDestinations(entries);
   }

   private void loadDestinations(List<ITTargetEntry> entries) {
      this.destinations.clear();

      for (ITTargetEntry entry : entries) {
         this.destinations
            .add(new InstantTransmissionScreen.MasterEntry(entry.getType(), entry.getId(), entry.getName(), entry.getDimension(), entry.isReachable()));
      }

      this.destinations
         .sort(Comparator.<InstantTransmissionScreen.MasterEntry>comparingInt(e -> e.priority()).thenComparing(e -> e.name, String.CASE_INSENSITIVE_ORDER));
   }

   protected void init() {
      super.init();
      this.guiLeft = (this.getUiWidth() - 141) / 2;
      this.guiTop = (this.getUiHeight() - 213) / 2;
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
      this.deleteButton = new CustomTextureButton.Builder()
         .position(this.guiLeft + 141 - 28, this.getUiHeight() - 25)
         .size(14, 11)
         .texture(BUTTON_TEXTURE)
         .textureCoords(10, 0, 10, 10)
         .textureSize(10, 10)
         .onPress(btn -> this.deleteSelectedMaster())
         .build();
      this.deleteButton.visible = false;
      this.addRenderableWidget(this.deleteButton);
   }

   private void deleteSelectedMaster() {
      if (this.selectedIndex >= 0 && this.selectedIndex < this.destinations.size()) {
         InstantTransmissionScreen.MasterEntry dest = this.destinations.get(this.selectedIndex);
         if (dest.type == ITTargetEntry.Type.MASTER) {
            NetworkHandler.sendToServer(new DeleteMasterC2S(dest.id));
            this.destinations.remove(this.selectedIndex);
            this.selectedIndex = -1;
            this.travelButton.visible = false;
            this.deleteButton.visible = false;
         }
      }
   }

   private void initiateTravel() {
      if (this.selectedIndex >= 0 && this.selectedIndex < this.destinations.size()) {
         InstantTransmissionScreen.MasterEntry dest = this.destinations.get(this.selectedIndex);
         if (dest.reachable) {
            if (dest.type == ITTargetEntry.Type.MASTER) {
               NetworkHandler.sendToServer(new InstantTransmissionTravelC2S(dest.id));
            } else {
               NetworkHandler.sendToServer(new InstantTransmissionTravelToPlayerC2S(UUID.fromString(dest.id)));
            }

            this.onClose();
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
         graphics, this.font, this.tr("gui.dragonminez.transmission.title", new Object[0]), this.getUiWidth() / 2, this.guiTop + 18, -10496
      );
      this.renderMasterList(graphics, uiMouseX, uiMouseY);
      super.render(graphics, uiMouseX, uiMouseY, partialTick);
      this.endUiScale(graphics);
   }

   private void renderMasterList(GuiGraphics graphics, int uiMouseX, int uiMouseY) {
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
            InstantTransmissionScreen.MasterEntry dest = this.destinations.get(i);
            boolean isSelected = i == this.selectedIndex;
            if (!dest.reachable) {
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

            Component textToDraw = this.txt(dest.name).copy().withStyle(ChatFormatting.BOLD);
            int textColor = dest.reachable ? colorForType(dest.type) : 7632504;
            TextUtil.drawStringWithBorder(graphics, this.font, textToDraw, listLeft + 10, itemY + 8, textColor);
         }
      }

      graphics.pose().popPose();
      graphics.disableScissor();
      if (this.maxScroll > 0.0F) {
         this.renderScrollbar(graphics, listTop, viewHeight, totalHeight);
      }
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
      return Mth.clamp((float)(uiY - (double)startY) / (float)viewHeight, 0.0F, 1.0F);
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
               int index = (int)(uiY - (double)listTop + (double)this.currentScroll) / 24;
               if (index >= 0 && index < this.destinations.size()) {
                  InstantTransmissionScreen.MasterEntry dest = this.destinations.get(index);
                  if (dest.reachable) {
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
         this.deleteButton.visible = this.destinations.get(index).type == ITTargetEntry.Type.MASTER;
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
         this.targetScroll = this.calculateScrollPercent(uiY, this.guiTop + 35, 168) * this.maxScroll;
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static int colorForType(ITTargetEntry.Type type) {
      return switch (type) {
         case MASTER -> 2154751;
         case PARTY -> 16766720;
         case EXTERNAL -> 16777215;
      };
   }

   private static class MasterEntry {
      ITTargetEntry.Type type;
      String id;
      String name;
      String dimension;
      boolean reachable;

      public MasterEntry(ITTargetEntry.Type type, String id, String name, String dimension, boolean reachable) {
         this.type = type;
         this.id = id;
         this.name = name;
         this.dimension = dimension;
         this.reachable = reachable;
      }

      int priority() {
         return switch (this.type) {
            case MASTER -> 1;
            case PARTY -> 2;
            case EXTERNAL -> 3;
         };
      }
   }
}
