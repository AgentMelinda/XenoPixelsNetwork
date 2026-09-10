package com.dragonminez.client.gui.hud;

import com.dragonminez.client.util.KeyBinds;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.TechniqueData;
import com.dragonminez.common.stats.techniques.Techniques;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw.Layer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.settings.KeyModifier;

public class TechniqueHotbarHUD {
   private static final ResourceLocation DMZ_FONT = ResourceLocation.fromNamespaceAndPath("dragonminez", "smooth");
   private static final int SLOTS = 8;
   private static final int BAR_SLOTS = 4;
   private static final int ROW_HEIGHT = 13;
   private static final int BADGE_SIZE = 11;
   private static final int GAP = 5;
   private static final int MARGIN_X = 12;
   private static final int MARGIN_BOTTOM = 34;
   private static final int COLOR_NAME = -1;
   private static final int COLOR_NAME_CD = -7697782;
   private static final int COLOR_CD = -11776;
   private static final int COLOR_BADGE_BG = -1342177280;
   private static final int COLOR_BADGE_BORDER = 1728053247;
   private static final int COLOR_BADGE_TEXT = -1;
   private static final int[] cdLastTicks = new int[8];
   private static final long[] cdLastUpdateMs = new long[8];
   private static final String[] cdLastId = new String[8];
   public static final Layer HUD_TECHNIQUES = (guiGraphics, deltaTracker) -> {
      float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
      int width = guiGraphics.guiWidth();
      int height = guiGraphics.guiHeight();
      Minecraft mc = Minecraft.getInstance();
      if (!mc.getDebugOverlay().showDebugScreen() && mc.player != null) {
         KeyModifier bar1Mod = KeyBinds.TECHNIQUE_SLOTS[0].getKeyModifier();
         KeyModifier bar2Mod = KeyBinds.TECHNIQUE_SLOTS[4].getKeyModifier();
         boolean bar1Held = KeyBinds.isBarModifierActive(bar1Mod);
         boolean bar2Held = KeyBinds.isBarModifierActive(bar2Mod);
         int baseSlot;
         if (bar2Held && bar2Mod != bar1Mod) {
            baseSlot = 4;
         } else if (bar1Held) {
            baseSlot = 0;
         } else {
            if (!bar2Held) {
               return;
            }

            baseSlot = 4;
         }

         StatsProvider.get(StatsCapability.INSTANCE, mc.player).ifPresent(data -> {
            if (data.getStatus().isHasCreatedCharacter()) {
               Techniques techniques = data.getTechniques();
               String[] slots = techniques.getEquippedSlots();
               boolean rightSide = ConfigManager.getUserConfig().getTechniqueHotbarRightSide();
               Font font = mc.font;
               float hudScale = 1.5F;
               int sw = Math.round((float)width / hudScale);
               int sh = Math.round((float)height / hudScale);
               guiGraphics.pose().pushPose();
               guiGraphics.pose().scale(hudScale, hudScale, 1.0F);
               int totalHeight = 52;
               int startY = sh - 34 - totalHeight;

               for (int j = 0; j < 4; j++) {
                  int i = baseSlot + j;
                  int rowY = startY + j * 13;
                  int textY = rowY + (13 - 9) / 2;
                  int badgeY = rowY + 1;
                  String keyLabel = KeyBinds.TECHNIQUE_SLOTS[i].getKey().getDisplayName().getString();
                  String id = slots[i];
                  TechniqueData tech = id != null && !id.isEmpty() ? techniques.getUnlockedTechniques().get(id) : null;
                  int syncedCd = tech != null ? data.getCooldowns().getCooldown("TechniqueCooldown_" + id) : 0;
                  boolean onCooldown = syncedCd > 0;
                  float displaySeconds = interpolateCooldownSeconds(i, id, syncedCd);
                  MutableComponent name = tech != null ? techniqueName(tech.getName()) : null;
                  MutableComponent cd = onCooldown && displaySeconds > 0.05F ? styled(String.format(Locale.US, "%.1fs", displaySeconds)) : null;
                  int nameWidth = name != null ? font.width(name) : 0;
                  int cdWidth = cd != null ? font.width(cd) : 0;
                  if (!rightSide) {
                     int badgeX = 12;
                     drawBadge(guiGraphics, font, badgeX, badgeY, keyLabel);
                     int textX = badgeX + 11 + 5;
                     if (name != null) {
                        guiGraphics.drawString(font, name, textX, textY, onCooldown ? -7697782 : -1, false);
                        if (cd != null) {
                           guiGraphics.drawString(font, cd, textX + nameWidth + 5, textY, -11776, false);
                        }
                     }
                  } else {
                     int badgeX = sw - 12 - 11;
                     drawBadge(guiGraphics, font, badgeX, badgeY, keyLabel);
                     if (name != null) {
                        int nameX = badgeX - 5 - nameWidth;
                        guiGraphics.drawString(font, name, nameX, textY, onCooldown ? -7697782 : -1, false);
                        if (cd != null) {
                           guiGraphics.drawString(font, cd, nameX - 5 - cdWidth, textY, -11776, false);
                        }
                     }
                  }
               }

               guiGraphics.pose().popPose();
            }
         });
      }
   };

   private static void drawBadge(GuiGraphics guiGraphics, Font font, int x, int y, String label) {
      guiGraphics.fill(x, y, x + 11, y + 11, -1342177280);
      guiGraphics.renderOutline(x, y, 11, 11, 1728053247);
      MutableComponent text = styled(label);
      int textWidth = font.width(text);
      int textX = x + (11 - textWidth) / 2 + 1;
      int textY = y + (11 - 9) / 2 + 1;
      guiGraphics.drawString(font, text, textX, textY, -1, false);
   }

   private static float interpolateCooldownSeconds(int slot, String id, int syncedCd) {
      long now = System.currentTimeMillis();
      if (!Objects.equals(id, cdLastId[slot]) || syncedCd != cdLastTicks[slot]) {
         cdLastId[slot] = id;
         cdLastTicks[slot] = syncedCd;
         cdLastUpdateMs[slot] = now;
      }

      if (syncedCd <= 0) {
         return 0.0F;
      } else {
         float elapsedSec = (float)(now - cdLastUpdateMs[slot]) / 1000.0F;
         return Math.max(0.0F, (float)syncedCd / 20.0F - elapsedSec);
      }
   }

   private static MutableComponent styled(String text) {
      return Component.literal(text).withStyle(Style.EMPTY.withFont(DMZ_FONT));
   }

   private static MutableComponent techniqueName(String name) {
      if (name != null && !name.isEmpty()) {
         MutableComponent base = name.contains(".") ? Component.translatable(name) : Component.literal(name);
         return base.withStyle(Style.EMPTY.withFont(DMZ_FONT));
      } else {
         return styled("");
      }
   }
}
