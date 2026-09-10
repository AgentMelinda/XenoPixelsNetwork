package com.dragonminez.client.gui.config;

import com.dragonminez.client.gui.UnblurredScreen;
import com.dragonminez.client.util.ScrollbarState;
import com.dragonminez.common.config.ConfigManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DMZModConfigScreen extends UnblurredScreen {
   private static final int ROW_HEIGHT = 12;
   private static final int LIST_TOP = 40;
   private static final int LIST_BOTTOM_MARGIN = 40;
   private final Screen parent;
   private final List<String> allFiles = new ArrayList<>();
   private final List<String> files = new ArrayList<>();
   private EditBox searchBox;
   private int scrollOffset = 0;
   private final ScrollbarState scrollBar = new ScrollbarState();

   public DMZModConfigScreen(Screen parent) {
      super(Component.translatable("gui.dragonminez.modconfig.title"));
      this.parent = parent;
   }

   private int listBottom() {
      return this.height - 40;
   }

   private int visibleRows() {
      return Math.max(1, (this.listBottom() - 40) / 12);
   }

   private int maxScroll() {
      return Math.max(0, this.files.size() - this.visibleRows());
   }

   protected void init() {
      super.init();
      this.allFiles.clear();
      this.allFiles.addAll(ConfigManager.getAvailableConfigFiles());
      this.allFiles.sort(String::compareToIgnoreCase);
      this.searchBox = new EditBox(this.font, this.width / 2 - 100, 20, 200, 14, Component.translatable("gui.dragonminez.modconfig.search"));
      this.searchBox.setHint(Component.translatable("gui.dragonminez.modconfig.search"));
      this.searchBox.setResponder(v -> {
         this.applyFilter(v);
         this.scrollOffset = 0;
      });
      this.addWidget(this.searchBox);
      this.applyFilter("");
      int bottomY = this.height - 28;
      if (this.minecraft != null && this.minecraft.player != null) {
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.dragonminez.modconfig.reload"), b -> this.runReload())
               .bounds(this.width / 2 - 154, bottomY, 150, 20)
               .build()
         );
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.dragonminez.modconfig.done"), b -> this.onClose()).bounds(this.width / 2 + 4, bottomY, 150, 20).build()
         );
      } else {
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.dragonminez.modconfig.done"), b -> this.onClose()).bounds(this.width / 2 - 75, bottomY, 150, 20).build()
         );
      }
   }

   private void applyFilter(String query) {
      this.files.clear();
      String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

      for (String f : this.allFiles) {
         if (q.isEmpty() || f.toLowerCase(Locale.ROOT).contains(q)) {
            this.files.add(f);
         }
      }
   }

   private void runReload() {
      if (this.minecraft != null && this.minecraft.player != null) {
         this.minecraft.player.connection.sendCommand("dmzreload");
         this.onClose();
      }
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(graphics, mouseX, mouseY, partialTick);
      graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 16777215);
      this.searchBox.render(graphics, mouseX, mouseY, partialTick);
      int top = 40;
      int bottom = this.listBottom();
      graphics.enableScissor(0, top, this.width, bottom);
      int start = this.scrollOffset;
      int end = Math.min(this.files.size(), start + this.visibleRows());

      for (int i = start; i < end; i++) {
         int y = top + (i - start) * 12;
         boolean hovered = mouseX >= this.width / 2 - 150 && mouseX <= this.width / 2 + 150 && mouseY >= y && mouseY < y + 12;
         int color = hovered ? -10496 : -3355444;
         graphics.drawString(this.font, this.files.get(i), this.width / 2 - 150, y + 2, color);
      }

      graphics.disableScissor();
      if (this.files.isEmpty()) {
         graphics.drawCenteredString(this.font, Component.translatable("gui.dragonminez.modconfig.empty"), this.width / 2, top + 10, -7829368);
      }

      this.scrollBar.update(this.width / 2 + 156, 3, top, bottom - top, (float)this.maxScroll());
      if (this.maxScroll() > 0) {
         int barX = this.width / 2 + 156;
         int trackH = bottom - top;
         graphics.fill(barX, top, barX + 3, bottom, -13421773);
         float pct = (float)this.scrollOffset / (float)this.maxScroll();
         int handleH = Math.max(20, (int)((float)trackH * ((float)this.visibleRows() / (float)this.files.size())));
         int handleY = top + (int)((float)(trackH - handleH) * pct);
         graphics.fill(barX, handleY, barX + 3, handleY + handleH, -5592406);
      }

      super.render(graphics, mouseX, mouseY, partialTick);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0 && this.scrollBar.tryStartDrag(mouseX, mouseY)) {
         this.scrollOffset = Math.round(this.scrollBar.scrollFor(mouseY));
         return true;
      } else {
         if (button == 0) {
            int top = 40;
            int bottom = this.listBottom();
            if (mouseX >= (double)this.width / 2.0 - 150.0 && mouseX <= (double)this.width / 2.0 + 150.0 && mouseY >= (double)top && mouseY < (double)bottom) {
               int index = this.scrollOffset + (int)((mouseY - (double)top) / 12.0);
               if (index >= 0 && index < this.files.size() && this.minecraft != null) {
                  this.minecraft.setScreen(new DMZConfigEditScreen(this, this.files.get(index)));
                  return true;
               }
            }
         }

         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.scrollBar.isDragging()) {
         this.scrollOffset = Math.round(this.scrollBar.scrollFor(mouseY));
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.scrollBar.isDragging()) {
         this.scrollBar.stopDrag();
         return true;
      } else {
         return super.mouseReleased(mouseX, mouseY, button);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      int max = this.maxScroll();
      if (max > 0) {
         this.scrollOffset = Math.max(0, Math.min(max, this.scrollOffset - (int)Math.signum(scrollY)));
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
   }

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(this.parent);
      }
   }
}
