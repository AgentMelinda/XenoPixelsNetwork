package com.dragonminez.client.init.menu.screens;

import com.dragonminez.common.init.menu.menutypes.KikonoStationMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class KikonoStationScreen extends AbstractContainerScreen<KikonoStationMenu> {
   private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/screen/kikono_station_gui.png");

   public KikonoStationScreen(KikonoStationMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
      super(pMenu, pPlayerInventory, pTitle);
   }

   protected void init() {
      super.init();
   }

   protected void renderBg(GuiGraphics guiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.setShaderTexture(0, TEXTURE);
      int x = (this.width - this.imageWidth) / 2;
      int y = (this.height - this.imageHeight) / 2;
      guiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
      if (((KikonoStationMenu)this.menu).isCrafting()) {
         guiGraphics.blit(TEXTURE, x + 111, y + 35, 177, 2, ((KikonoStationMenu)this.menu).getScaledProgress(), 17);
      }

      int energyHeight = ((KikonoStationMenu)this.menu).getScaledEnergy();
      guiGraphics.blit(TEXTURE, x + 9, y + 21 + (52 - energyHeight), 177, 21 + (60 - energyHeight), 12, energyHeight);
   }

   public void render(GuiGraphics guiGraphics, int pMouseX, int pMouseY, float delta) {
      this.renderBackground(guiGraphics, pMouseX, pMouseY, delta);
      super.render(guiGraphics, pMouseX, pMouseY, delta);
      this.renderTooltip(guiGraphics, pMouseX, pMouseY);
   }

   protected void renderTooltip(GuiGraphics guiGraphics, int pMouseX, int pMouseY) {
      super.renderTooltip(guiGraphics, pMouseX, pMouseY);
      int x = (this.width - this.imageWidth) / 2;
      int y = (this.height - this.imageHeight) / 2;
      if (pMouseX >= x + 10 && pMouseX <= x + 18 && pMouseY >= y + 16 && pMouseY <= y + 68) {
         guiGraphics.renderTooltip(
            this.font,
            Component.literal(((KikonoStationMenu)this.menu).getEnergy() + " / " + ((KikonoStationMenu)this.menu).getMaxEnergy() + " Star Energy"),
            pMouseX,
            pMouseY
         );
      }
   }
}
