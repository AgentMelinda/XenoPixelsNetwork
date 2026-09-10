package com.dragonminez.client.gui.config;

import com.dragonminez.client.gui.UnblurredScreen;
import com.dragonminez.client.util.ScrollbarState;
import com.dragonminez.common.config.ConfigManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DMZConfigEditScreen extends UnblurredScreen {
   private static final int ROW_HEIGHT = 18;
   private static final int LIST_TOP = 32;
   private static final int LIST_BOTTOM_MARGIN = 40;
   private static final long FEEDBACK_DURATION_MS = 2500L;
   private final Screen parent;
   private final String configPath;
   private final List<DMZConfigEditScreen.Field> fields = new ArrayList<>();
   private JsonObject root;
   private int scrollOffset = 0;
   private final ScrollbarState scrollBar = new ScrollbarState();
   private Component feedback;
   private int feedbackColor;
   private long feedbackUntil;

   public DMZConfigEditScreen(Screen parent, String configPath) {
      super(Component.literal(configPath));
      this.parent = parent;
      this.configPath = configPath;
   }

   private int listBottom() {
      return this.height - 40;
   }

   private int visibleRows() {
      return Math.max(1, (this.listBottom() - 32) / 18);
   }

   private int maxScroll() {
      return Math.max(0, this.fields.size() - this.visibleRows());
   }

   protected void init() {
      super.init();
      this.fields.clear();
      if (this.root == null) {
         String json = ConfigManager.getSpecificConfigJson(this.configPath);

         try {
            this.root = json != null ? JsonParser.parseString(json).getAsJsonObject() : new JsonObject();
         } catch (Exception var6) {
            this.root = new JsonObject();
         }
      }

      this.flatten(this.root, "");
      int boxX = this.width / 2 + 10;
      int boxW = Math.min(160, this.width / 2 - 20);

      for (DMZConfigEditScreen.Field field : this.fields) {
         EditBox box = new EditBox(this.font, boxX, 0, boxW, 14, Component.literal(field.label));
         box.setMaxLength(256);
         box.setValue(field.initialValue);
         box.visible = false;
         field.box = box;
         this.addWidget(box);
      }

      int bottomY = this.height - 28;
      boolean inGame = this.minecraft != null && this.minecraft.player != null;
      if (inGame) {
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.dragonminez.modconfig.save"), b -> this.save(false))
               .bounds(this.width / 2 - 154, bottomY, 100, 20)
               .build()
         );
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.dragonminez.modconfig.save_reload"), b -> this.save(true))
               .bounds(this.width / 2 - 50, bottomY, 104, 20)
               .build()
         );
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.dragonminez.modconfig.back"), b -> this.onClose()).bounds(this.width / 2 + 58, bottomY, 96, 20).build()
         );
      } else {
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.dragonminez.modconfig.save"), b -> this.save(false))
               .bounds(this.width / 2 - 154, bottomY, 150, 20)
               .build()
         );
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.dragonminez.modconfig.back"), b -> this.onClose()).bounds(this.width / 2 + 4, bottomY, 150, 20).build()
         );
      }
   }

   private void flatten(JsonElement element, String prefix) {
      if (element.isJsonObject()) {
         JsonObject obj = element.getAsJsonObject();

         for (Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            JsonElement child = entry.getValue();
            String label = prefix.isEmpty() ? key : prefix + "." + key;
            if (child.isJsonPrimitive()) {
               this.fields.add(new DMZConfigEditScreen.Field(label, child.getAsString(), child.getAsJsonPrimitive(), v -> obj.add(key, v)));
            } else if (child.isJsonObject() || child.isJsonArray()) {
               this.flatten(child, label);
            }
         }
      } else if (element.isJsonArray()) {
         JsonArray arr = element.getAsJsonArray();

         for (int i = 0; i < arr.size(); i++) {
            JsonElement child = arr.get(i);
            String label = prefix + "[" + i + "]";
            int index = i;
            if (child.isJsonPrimitive()) {
               this.fields.add(new DMZConfigEditScreen.Field(label, child.getAsString(), child.getAsJsonPrimitive(), v -> arr.set(index, v)));
            } else if (child.isJsonObject() || child.isJsonArray()) {
               this.flatten(child, label);
            }
         }
      }
   }

   private void save(boolean reload) {
      for (DMZConfigEditScreen.Field field : this.fields) {
         field.apply();
      }

      boolean ok = ConfigManager.saveRawConfig(this.configPath, this.root.toString());
      if (ok) {
         if (reload && this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.connection.sendCommand("dmzreload");
            this.onClose();
            return;
         }

         this.setFeedback(Component.translatable("gui.dragonminez.modconfig.saved"), -11141291);
      } else {
         this.setFeedback(Component.translatable("gui.dragonminez.modconfig.save_failed"), -43691);
      }
   }

   private void setFeedback(Component text, int color) {
      this.feedback = text;
      this.feedbackColor = color;
      this.feedbackUntil = System.currentTimeMillis() + 2500L;
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(graphics, mouseX, mouseY, partialTick);
      graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, -10496);
      int top = 32;
      int bottom = this.listBottom();
      int start = this.scrollOffset;
      int end = Math.min(this.fields.size(), start + this.visibleRows());
      graphics.enableScissor(0, top, this.width, bottom);

      for (int i = 0; i < this.fields.size(); i++) {
         DMZConfigEditScreen.Field field = this.fields.get(i);
         if (i >= start && i < end) {
            int y = top + (i - start) * 18;
            graphics.drawString(this.font, this.trim(field.label, this.width / 2 - 24), 14, y + 5, -1);
            field.box.visible = true;
            field.box.setY(y + 1);
            field.box.render(graphics, mouseX, mouseY, partialTick);
         } else {
            field.box.visible = false;
         }
      }

      graphics.disableScissor();
      if (this.fields.isEmpty()) {
         graphics.drawCenteredString(this.font, Component.translatable("gui.dragonminez.modconfig.no_fields"), this.width / 2, top + 10, -7829368);
      }

      this.scrollBar.update(this.width - 8, 3, top, bottom - top, (float)this.maxScroll());
      if (this.maxScroll() > 0) {
         int barX = this.width - 8;
         int trackH = bottom - top;
         graphics.fill(barX, top, barX + 3, bottom, -13421773);
         float pct = (float)this.scrollOffset / (float)this.maxScroll();
         int handleH = Math.max(20, (int)((float)trackH * ((float)this.visibleRows() / (float)this.fields.size())));
         int handleY = top + (int)((float)(trackH - handleH) * pct);
         graphics.fill(barX, handleY, barX + 3, handleY + handleH, -5592406);
      }

      if (this.feedback != null && System.currentTimeMillis() < this.feedbackUntil) {
         graphics.drawCenteredString(this.font, this.feedback, this.width / 2, this.height - 38, this.feedbackColor);
      }

      super.render(graphics, mouseX, mouseY, partialTick);
   }

   private String trim(String text, int maxWidth) {
      if (this.font.width(text) <= maxWidth) {
         return text;
      } else {
         while (text.length() > 1 && this.font.width(text + "...") > maxWidth) {
            text = text.substring(0, text.length() - 1);
         }

         return text + "...";
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      int max = this.maxScroll();
      if (max > 0 && mouseY >= 32.0 && mouseY < (double)this.listBottom()) {
         this.scrollOffset = Math.max(0, Math.min(max, this.scrollOffset - (int)Math.signum(scrollY)));
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (super.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else if (this.scrollBar.tryStartDrag(mouseX, mouseY)) {
         this.scrollOffset = Math.round(this.scrollBar.scrollFor(mouseY));
         return true;
      } else {
         return false;
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

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(this.parent);
      }
   }

   private static class Field {
      final String label;
      final String initialValue;
      final boolean wasBoolean;
      final boolean wasNumber;
      final Consumer<JsonElement> setter;
      EditBox box;

      Field(String label, String initialValue, JsonPrimitive original, Consumer<JsonElement> setter) {
         this.label = label;
         this.initialValue = initialValue;
         this.wasBoolean = original.isBoolean();
         this.wasNumber = original.isNumber();
         this.setter = setter;
      }

      void apply() {
         String value = this.box.getValue();
         this.setter.accept(this.coerce(value));
      }

      private JsonElement coerce(String value) {
         if (this.wasBoolean) {
            return new JsonPrimitive("true".equalsIgnoreCase(value.trim()));
         } else if (this.wasNumber) {
            try {
               return JsonParser.parseString(value.trim());
            } catch (Exception var3) {
               return new JsonPrimitive(this.initialValue);
            }
         } else if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            try {
               Double.parseDouble(value.trim());
               return JsonParser.parseString(value.trim());
            } catch (Exception var4) {
               return new JsonPrimitive(value);
            }
         } else {
            return new JsonPrimitive(Boolean.parseBoolean(value.toLowerCase(Locale.ROOT)));
         }
      }
   }
}
