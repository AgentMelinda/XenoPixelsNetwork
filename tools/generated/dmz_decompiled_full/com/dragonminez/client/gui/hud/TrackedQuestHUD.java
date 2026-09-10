package com.dragonminez.client.gui.hud;

import com.dragonminez.client.util.LocalizationUtil;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.quest.Quest;
import com.dragonminez.common.quest.QuestObjective;
import com.dragonminez.common.quest.QuestRegistry;
import com.dragonminez.common.quest.QuestTextFormatter;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw.Layer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

public class TrackedQuestHUD {
   private static final int PANEL_WIDTH = 180;
   private static final int MAX_TEXT_WIDTH = 164;
   private static final ResourceLocation DMZ_FONT = ResourceLocation.fromNamespaceAndPath("dragonminez", "smooth");
   public static final Layer HUD_TRACKED_QUEST = (guiGraphics, deltaTracker) -> {
      float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
      int width = guiGraphics.guiWidth();
      int height = guiGraphics.guiHeight();
      Minecraft mc = Minecraft.getInstance();
      if (!mc.getDebugOverlay().showDebugScreen() && mc.player != null) {
         StatsProvider.get(StatsCapability.INSTANCE, mc.player).ifPresent(data -> {
            PlayerQuestData pqd = data.getPlayerQuestData();
            String trackedQuestId = pqd.getTrackedQuestId();
            if (trackedQuestId != null && !trackedQuestId.isBlank()) {
               if (pqd.isQuestAccepted(trackedQuestId) && !pqd.isQuestCompleted(trackedQuestId)) {
                  Quest quest = QuestRegistry.getClientQuest(trackedQuestId);
                  if (quest != null) {
                     renderPanel(guiGraphics, mc.font, pqd, trackedQuestId, quest, width);
                  }
               }
            }
         });
      }
   };

   private static void renderPanel(GuiGraphics guiGraphics, Font font, PlayerQuestData pqd, String questId, Quest quest, int screenWidth) {
      List<FormattedCharSequence> objectiveLines = buildObjectiveLines(font, pqd, questId, quest);
      List<FormattedCharSequence> wrappedTitle = font.split(toComponent(quest.getTitle()), 164);
      if (wrappedTitle.isEmpty()) {
         wrappedTitle = List.of(FormattedCharSequence.forward(questId, Style.EMPTY.withFont(DMZ_FONT)));
      }

      int lineHeight = 10;
      int lineCount = 1 + wrappedTitle.size() + objectiveLines.size();
      int panelHeight = 10 + lineCount * lineHeight + 6;
      int x = screenWidth - 180 - 10;
      int y = 10;
      guiGraphics.fill(x, y, x + 180, y + panelHeight, -1607848128);
      guiGraphics.fill(x, y, x + 180, y + 1, -865235713);
      guiGraphics.fill(x, y + panelHeight - 1, x + 180, y + panelHeight, 1711276032);
      int drawY = y + 5;
      guiGraphics.drawString(
         font, Component.translatable("gui.dragonminez.story.hud.tracked").withStyle(Style.EMPTY.withFont(DMZ_FONT)), x + 6, drawY, 15266047, false
      );
      drawY += lineHeight;

      for (FormattedCharSequence titleLine : wrappedTitle) {
         guiGraphics.drawString(font, titleLine, x + 6, drawY, 16777215, false);
         drawY += lineHeight;
      }

      for (FormattedCharSequence line : objectiveLines) {
         guiGraphics.drawString(font, line, x + 6, drawY, 13623807, false);
         drawY += lineHeight;
      }
   }

   private static List<FormattedCharSequence> buildObjectiveLines(Font font, PlayerQuestData pqd, String questId, Quest quest) {
      List<FormattedCharSequence> lines = new ArrayList<>();
      List<QuestObjective> objectives = quest.getObjectives();
      if (objectives.isEmpty()) {
         lines.add(
            FormattedCharSequence.forward(
               Component.translatable("gui.dragonminez.story.hud.no_objectives").withStyle(Style.EMPTY.withFont(DMZ_FONT)).getString(),
               Style.EMPTY.withFont(DMZ_FONT)
            )
         );
         return lines;
      } else {
         for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            int progress = pqd.getObjectiveProgress(questId, i);
            int required = quest.getObjectiveRequired(pqd, questId, i);
            if (!quest.isParallelObjectives()) {
               if (progress < required) {
                  lines.addAll(splitObjectiveLine(font, objective, progress, required));
                  return lines;
               }
            } else if (progress < required) {
               lines.addAll(splitObjectiveLine(font, objective, progress, required));
            }
         }

         if (lines.isEmpty()) {
            lines.add(
               FormattedCharSequence.forward(
                  Component.translatable("gui.dragonminez.quests.status.complete").withStyle(Style.EMPTY.withFont(DMZ_FONT)).getString(),
                  Style.EMPTY.withFont(DMZ_FONT)
               )
            );
         }

         return lines;
      }
   }

   private static List<FormattedCharSequence> splitObjectiveLine(Font font, QuestObjective objective, int progress, int required) {
      Component text = Component.literal("- ")
         .append(QuestTextFormatter.describeObjective(objective))
         .append(Component.literal(" (" + progress + "/" + required + ")"))
         .withStyle(Style.EMPTY.withFont(DMZ_FONT));
      return font.split(text, 164);
   }

   private static Component toComponent(String raw) {
      return LocalizationUtil.localizedOrReadable(raw).copy().withStyle(Style.EMPTY.withFont(DMZ_FONT));
   }
}
