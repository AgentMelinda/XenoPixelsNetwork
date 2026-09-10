package com.dragonminez.client.init.menu.screens;

import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.menu.menutypes.GravityDeviceMenu;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.GravityDeviceUpdateC2S;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class GravityDeviceScreen extends AbstractContainerScreen<GravityDeviceMenu> {
   protected static final ResourceLocation DMZ_FONT = ResourceLocation.fromNamespaceAndPath("dragonminez", "smooth");
   private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/screen/gravity_device_gui.png");
   private EditBox gravityInput;
   private Button toggleButton;

   public GravityDeviceScreen(GravityDeviceMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
      super(pMenu, pPlayerInventory, pTitle);
   }

   protected void init() {
      super.init();
      int x = (this.width - this.imageWidth) / 2;
      int y = (this.height - this.imageHeight) / 2;
      this.gravityInput = new EditBox(this.font, x + 40, y + 20, 50, 16, Component.translatable("gui.dragonminez.gravity_device.gravity"));
      this.gravityInput.setMaxLength(4);
      this.gravityInput.setValue(String.valueOf(Math.max(1, ((GravityDeviceMenu)this.menu).getTargetGravity())));
      this.gravityInput.setFilter(s -> s.isEmpty() || s.matches("\\d{1,4}"));
      this.addRenderableWidget(this.gravityInput);
      this.addRenderableWidget(
         Button.builder(
               Component.translatable("gui.dragonminez.gravity_device.set").withStyle(Style.EMPTY.withFont(DMZ_FONT)),
               b -> this.sendUpdate(((GravityDeviceMenu)this.menu).isActive())
            )
            .bounds(x + 95, y + 19, 40, 18)
            .build()
      );
      this.toggleButton = Button.builder(
            this.toggleLabel(((GravityDeviceMenu)this.menu).isActive()), b -> this.sendUpdate(!((GravityDeviceMenu)this.menu).isActive())
         )
         .bounds(x + 40, y + 42, 95, 18)
         .build();
      this.addRenderableWidget(this.toggleButton);
   }

   private Component toggleLabel(boolean active) {
      return active
         ? Component.translatable("gui.dragonminez.gravity_device.turn_off").withStyle(Style.EMPTY.withFont(DMZ_FONT))
         : Component.translatable("gui.dragonminez.gravity_device.turn_on").withStyle(Style.EMPTY.withFont(DMZ_FONT));
   }

   private void sendUpdate(boolean active) {
      int gravity = this.parseGravity();
      NetworkHandler.sendToServer(new GravityDeviceUpdateC2S(((GravityDeviceMenu)this.menu).getBlockPos(), active, gravity));
   }

   private int parseGravity() {
      int max = ConfigManager.getServerConfig().getGravity().getDeviceMaxGravity();

      int value;
      try {
         value = Integer.parseInt(this.gravityInput.getValue());
      } catch (NumberFormatException var4) {
         value = 1;
      }

      return Math.max(1, Math.min(value, max));
   }

   protected void renderBg(GuiGraphics guiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.setShaderTexture(0, TEXTURE);
      int x = (this.width - this.imageWidth) / 2;
      int y = (this.height - this.imageHeight) / 2;
      guiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
      int energyHeight = ((GravityDeviceMenu)this.menu).getScaledEnergy();
      guiGraphics.blit(TEXTURE, x + 154, y + 21 + (52 - energyHeight), 177, 21 + (60 - energyHeight), 12, energyHeight);
   }

   public void render(GuiGraphics guiGraphics, int pMouseX, int pMouseY, float delta) {
      this.renderBackground(guiGraphics, pMouseX, pMouseY, delta);
      if (this.toggleButton != null) {
         this.toggleButton.setMessage(this.toggleLabel(((GravityDeviceMenu)this.menu).isActive()));
      }

      super.render(guiGraphics, pMouseX, pMouseY, delta);
      int x = (this.width - this.imageWidth) / 2;
      int y = (this.height - this.imageHeight) / 2;
      Component status;
      if (!((GravityDeviceMenu)this.menu).isRoomValid()) {
         status = Component.translatable("gui.dragonminez.gravity_device.no_room").withStyle(ChatFormatting.RED);
      } else if (((GravityDeviceMenu)this.menu).isRunning()) {
         status = Component.translatable("gui.dragonminez.gravity_device.running", new Object[]{((GravityDeviceMenu)this.menu).getTargetGravity()})
            .withStyle(ChatFormatting.GREEN);
      } else if (((GravityDeviceMenu)this.menu).isActive()) {
         status = Component.translatable("gui.dragonminez.gravity_device.no_energy").withStyle(ChatFormatting.GOLD);
      } else {
         status = Component.translatable("gui.dragonminez.gravity_device.idle").withStyle(ChatFormatting.GRAY);
      }

      Component var8 = status.copy().withStyle(Style.EMPTY.withFont(DMZ_FONT));
      TextUtil.drawCenteredStringWithBorder(guiGraphics, this.font, var8, x + this.imageWidth / 2, y + 64, 16777215);
      this.renderTooltip(guiGraphics, pMouseX, pMouseY);
   }

   protected void renderLabels(GuiGraphics guiGraphics, int pMouseX, int pMouseY) {
      Component title = this.title.copy().withStyle(Style.EMPTY.withFont(DMZ_FONT));
      TextUtil.drawCenteredStringWithBorder(guiGraphics, this.font, title, this.imageWidth / 2, this.titleLabelY, 16777215);
      guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
   }

   protected void renderTooltip(GuiGraphics guiGraphics, int pMouseX, int pMouseY) {
      super.renderTooltip(guiGraphics, pMouseX, pMouseY);
      int x = (this.width - this.imageWidth) / 2;
      int y = (this.height - this.imageHeight) / 2;
      if (pMouseX >= x + 154 && pMouseX <= x + 166 && pMouseY >= y + 16 && pMouseY <= y + 68) {
         guiGraphics.renderTooltip(
            this.font,
            Component.literal(((GravityDeviceMenu)this.menu).getEnergy() + " / " + ((GravityDeviceMenu)this.menu).getMaxEnergy() + " Star Energy"),
            pMouseX,
            pMouseY
         );
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.gravityInput.isFocused() || keyCode != 257 && keyCode != 335) {
         return this.gravityInput.isFocused() && keyCode != 256
            ? this.gravityInput.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers)
            : super.keyPressed(keyCode, scanCode, modifiers);
      } else {
         this.sendUpdate(((GravityDeviceMenu)this.menu).isActive());
         return true;
      }
   }
}
