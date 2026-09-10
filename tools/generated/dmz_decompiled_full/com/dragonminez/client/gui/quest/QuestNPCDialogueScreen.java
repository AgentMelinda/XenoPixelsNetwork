package com.dragonminez.client.gui.quest;

import com.dragonminez.client.gui.MasterTextScreen;
import com.dragonminez.client.gui.MastersSkillsScreen;
import com.dragonminez.client.gui.buttons.TexturedTextButton;
import com.dragonminez.client.gui.character.minigames.ControlGameScreen;
import com.dragonminez.client.gui.character.minigames.GravityGameScreen;
import com.dragonminez.client.gui.character.minigames.MemoryGameScreen;
import com.dragonminez.client.gui.character.minigames.PrecisionGameScreen;
import com.dragonminez.client.gui.character.minigames.RythmGameScreen;
import com.dragonminez.client.gui.character.util.ScaledScreen;
import com.dragonminez.client.util.ScrollbarState;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.NPCActionC2S;
import com.dragonminez.common.network.C2S.QuestActionC2S;
import com.dragonminez.common.quest.Difficulty;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.quest.Quest;
import com.dragonminez.common.quest.QuestObjective;
import com.dragonminez.common.quest.QuestRegistry;
import com.dragonminez.common.quest.QuestReward;
import com.dragonminez.common.quest.QuestTextFormatter;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class QuestNPCDialogueScreen extends ScaledScreen {
   private static final ResourceLocation DIALOGUE_BG = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menunpc.png");
   private static final ResourceLocation BUTTONS_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/characterbuttons.png");
   private static final Set<String> TEXT_MASTERS = Set.of("karin", "guru", "dende", "enma", "baba", "popo", "gero", "toribot", "babidi");
   private static final Set<String> SERVICE_MASTERS = Set.of("piccolo", "roshi", "kingkai", "oldkai", "babidi");
   private static final int MAX_VISIBLE = 7;
   private static final int ENTRY_HEIGHT = 18;
   private final String npcId;
   private final List<String> offerableQuestIds;
   private final List<String> turnInQuestIds;
   private final List<String> inProgressQuestIds;
   private final boolean masterNpc;
   private final int entityId;
   private final List<QuestNPCDialogueScreen.QuestEntry> questEntries = new ArrayList<>();
   private int selectedIndex = -1;
   private int panelX;
   private int panelY;
   private int panelW;
   private int panelH;
   private float dialogueScroll = 0.0F;
   private float dialogueTargetScroll = 0.0F;
   private float dialogueMaxScroll = 0.0F;
   private float listScroll = 0.0F;
   private float listTargetScroll = 0.0F;
   private float listMaxScroll = 0.0F;
   private float descScroll = 0.0F;
   private float descTargetScroll = 0.0F;
   private float descMaxScroll = 0.0F;
   private float objScroll = 0.0F;
   private float objTargetScroll = 0.0F;
   private float objMaxScroll = 0.0F;
   private float rewardScroll = 0.0F;
   private float rewardTargetScroll = 0.0F;
   private float rewardMaxScroll = 0.0F;
   private final ScrollbarState dialogueBar = new ScrollbarState();
   private final ScrollbarState listBar = new ScrollbarState();
   private final ScrollbarState descBar = new ScrollbarState();
   private final ScrollbarState objBar = new ScrollbarState();
   private final ScrollbarState rewardBar = new ScrollbarState();
   private boolean isTrainingMode = false;

   public QuestNPCDialogueScreen(String npcId, List<String> offerableQuestIds, List<String> turnInQuestIds, List<String> inProgressQuestIds) {
      this(npcId, offerableQuestIds, turnInQuestIds, inProgressQuestIds, false, -1);
   }

   public QuestNPCDialogueScreen(
      String npcId, List<String> offerableQuestIds, List<String> turnInQuestIds, List<String> inProgressQuestIds, boolean masterNpc, int entityId
   ) {
      super(Component.translatable("entity.dragonminez.questnpc." + npcId).withStyle(Style.EMPTY.withFont(DMZ_FONT)));
      this.npcId = npcId;
      this.offerableQuestIds = offerableQuestIds;
      this.turnInQuestIds = turnInQuestIds;
      this.inProgressQuestIds = inProgressQuestIds;
      this.masterNpc = masterNpc;
      this.entityId = entityId;
   }

   protected void init() {
      super.init();
      this.questEntries.clear();
      this.addEntries(this.offerableQuestIds, QuestNPCDialogueScreen.EntryType.OFFER);
      this.addEntries(this.turnInQuestIds, QuestNPCDialogueScreen.EntryType.TURN_IN);
      this.addEntries(this.inProgressQuestIds, QuestNPCDialogueScreen.EntryType.IN_PROGRESS);
      this.panelW = 345;
      this.panelH = 273;
      this.panelX = (this.getUiWidth() - this.panelW) / 2;
      this.panelY = (this.getUiHeight() - this.panelH) / 2;
      if (!this.questEntries.isEmpty() && this.selectedIndex == -1) {
         this.selectedIndex = 0;
      }

      this.initButtons();
   }

   private void addEntries(List<String> questIds, QuestNPCDialogueScreen.EntryType type) {
      for (String id : questIds) {
         Quest quest = QuestRegistry.getClientQuest(id);
         if (quest != null) {
            this.questEntries.add(new QuestNPCDialogueScreen.QuestEntry(id, quest, type));
         }
      }
   }

   private String getMinigameForNpc(String targetNpc) {
      for (String gameId : new String[]{"rhythm", "control", "memory", "precision", "gravity"}) {
         if (ConfigManager.getTrainingConfig().getSettings(gameId).getMasterName().equalsIgnoreCase(targetNpc)) {
            return gameId;
         }
      }

      return null;
   }

   private void openMinigameScreen(String minigameId) {
      switch (minigameId) {
         case "rhythm":
            Minecraft.getInstance().setScreen(new RythmGameScreen());
            break;
         case "control":
            Minecraft.getInstance().setScreen(new ControlGameScreen());
            break;
         case "memory":
            Minecraft.getInstance().setScreen(new MemoryGameScreen());
            break;
         case "precision":
            Minecraft.getInstance().setScreen(new PrecisionGameScreen());
            break;
         case "gravity":
            Minecraft.getInstance().setScreen(new GravityGameScreen());
      }
   }

   private void initButtons() {
      this.clearWidgets();
      int btnY = this.getUiHeight() - 28;
      this.addRenderableWidget(
         new TexturedTextButton.Builder()
            .position(this.panelX + this.panelW - 82, btnY)
            .size(74, 20)
            .texture(BUTTONS_TEXTURE)
            .textureCoords(0, 28, 0, 48)
            .textureSize(74, 20)
            .message(this.tr(this.isTrainingMode ? "gui.dragonminez.customization.back" : "gui.dragonminez.close", new Object[0]))
            .onPress(btn -> {
               if (this.isTrainingMode) {
                  this.isTrainingMode = false;
                  this.initButtons();
               } else {
                  this.onClose();
               }
            })
            .build()
      );
      if (this.masterNpc) {
         String minigameId = this.getMinigameForNpc(this.npcId);
         boolean isSkillMaster = !TEXT_MASTERS.contains(this.npcId);
         if (this.isTrainingMode && isSkillMaster) {
            if (minigameId != null) {
               this.addRenderableWidget(
                  new TexturedTextButton.Builder()
                     .position(this.panelX + 8, btnY)
                     .size(74, 20)
                     .texture(BUTTONS_TEXTURE)
                     .textureCoords(0, 28, 0, 48)
                     .textureSize(74, 20)
                     .message(this.tr("gui.dragonminez.button.popo.shadow", new Object[0]))
                     .onPress(btn -> {
                        NetworkHandler.sendToServer(new NPCActionC2S("popo", 1));
                        this.onClose();
                     })
                     .build()
               );
               this.addRenderableWidget(
                  new TexturedTextButton.Builder()
                     .position(this.getUiWidth() / 2 - 74, btnY)
                     .size(74, 20)
                     .texture(BUTTONS_TEXTURE)
                     .textureCoords(0, 28, 0, 48)
                     .textureSize(74, 20)
                     .message(this.tr("gui.dragonminez.minigame." + minigameId, new Object[0]))
                     .onPress(btn -> this.openMinigameScreen(minigameId))
                     .build()
               );
            } else {
               this.addRenderableWidget(
                  new TexturedTextButton.Builder()
                     .position(this.panelX + 8, btnY)
                     .size(74, 20)
                     .texture(BUTTONS_TEXTURE)
                     .textureCoords(0, 28, 0, 48)
                     .textureSize(74, 20)
                     .message(this.tr("gui.dragonminez.button.popo.shadow", new Object[0]))
                     .onPress(btn -> {
                        NetworkHandler.sendToServer(new NPCActionC2S("popo", 1));
                        this.onClose();
                     })
                     .build()
               );
            }
         } else if (!this.isTrainingMode) {
            if (isSkillMaster) {
               this.addRenderableWidget(
                  new TexturedTextButton.Builder()
                     .position(this.panelX + 8, btnY)
                     .size(74, 20)
                     .texture(BUTTONS_TEXTURE)
                     .textureCoords(0, 28, 0, 48)
                     .textureSize(74, 20)
                     .message(this.tr("gui.dragonminez.npc.skills", new Object[0]))
                     .onPress(btn -> this.openMasterScreen())
                     .build()
               );
               if (SERVICE_MASTERS.contains(this.npcId)) {
                  this.addRenderableWidget(
                     new TexturedTextButton.Builder()
                        .position(this.getUiWidth() / 2 - 74, btnY)
                        .size(74, 20)
                        .texture(BUTTONS_TEXTURE)
                        .textureCoords(0, 28, 0, 48)
                        .textureSize(74, 20)
                        .message(this.tr("gui.dragonminez.npc.services", new Object[0]))
                        .onPress(btn -> this.openServicesScreen())
                        .build()
                  );
               } else {
                  this.addRenderableWidget(
                     new TexturedTextButton.Builder()
                        .position(this.getUiWidth() / 2 - 74, btnY)
                        .size(74, 20)
                        .texture(BUTTONS_TEXTURE)
                        .textureCoords(0, 28, 0, 48)
                        .textureSize(74, 20)
                        .message(this.tr("gui.dragonminez.npc.train", new Object[0]))
                        .onPress(btn -> {
                           this.isTrainingMode = true;
                           this.initButtons();
                        })
                        .build()
                  );
               }
            } else {
               this.addRenderableWidget(
                  new TexturedTextButton.Builder()
                     .position(this.panelX + 8, btnY)
                     .size(74, 20)
                     .texture(BUTTONS_TEXTURE)
                     .textureCoords(0, 28, 0, 48)
                     .textureSize(74, 20)
                     .message(this.tr("gui.dragonminez.npc.services", new Object[0]))
                     .onPress(btn -> this.openMasterScreen())
                     .build()
               );
            }
         }
      }

      if (!this.isTrainingMode && this.selectedIndex >= 0 && this.selectedIndex < this.questEntries.size()) {
         QuestNPCDialogueScreen.QuestEntry entry = this.questEntries.get(this.selectedIndex);
         if (entry.type != QuestNPCDialogueScreen.EntryType.IN_PROGRESS) {
            Component buttonText = entry.type == QuestNPCDialogueScreen.EntryType.OFFER
               ? this.tr("gui.dragonminez.story.sidequests.accept", new Object[0])
               : this.tr("gui.dragonminez.sidequest.turn_in", new Object[0]);
            QuestNPCDialogueScreen.EntryType actionType = entry.type;
            String questId = entry.questId;
            this.addRenderableWidget(
               new TexturedTextButton.Builder()
                  .position(this.panelX + this.panelW - 78 - 74 - 13, btnY)
                  .size(74, 20)
                  .texture(BUTTONS_TEXTURE)
                  .textureCoords(0, 28, 0, 48)
                  .textureSize(74, 20)
                  .message(buttonText)
                  .onPress(btn -> this.handleQuestAction(actionType, questId))
                  .build()
            );
         }
      }
   }

   private void handleQuestAction(QuestNPCDialogueScreen.EntryType actionType, String questId) {
      if (actionType == QuestNPCDialogueScreen.EntryType.OFFER) {
         NetworkHandler.sendToServer(new QuestActionC2S(QuestActionC2S.ActionType.START, questId, ""));
      } else if (actionType == QuestNPCDialogueScreen.EntryType.TURN_IN) {
         NetworkHandler.sendToServer(new QuestActionC2S(QuestActionC2S.ActionType.TURN_IN, questId, this.npcId));
      }

      if (Minecraft.getInstance().player != null) {
         Minecraft.getInstance().player.playSound((SoundEvent)MainSounds.UI_MENU_SWITCH.get());
      }

      this.onClose();
   }

   @Override
   protected int getMinGuiWidth() {
      return 365;
   }

   @Override
   protected int getMinGuiHeight() {
      return 293;
   }

   @Override
   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      int uiMouseX = (int)Math.round(this.toUiX((double)mouseX));
      int uiMouseY = (int)Math.round(this.toUiY((double)mouseY));
      float tickDelta = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
      this.dialogueScroll = Mth.lerp(tickDelta * 0.4F, this.dialogueScroll, this.dialogueTargetScroll);
      this.listScroll = Mth.lerp(tickDelta * 0.4F, this.listScroll, this.listTargetScroll);
      this.descScroll = Mth.lerp(tickDelta * 0.4F, this.descScroll, this.descTargetScroll);
      this.objScroll = Mth.lerp(tickDelta * 0.4F, this.objScroll, this.objTargetScroll);
      this.rewardScroll = Mth.lerp(tickDelta * 0.4F, this.rewardScroll, this.rewardTargetScroll);
      this.beginUiScale(guiGraphics);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      guiGraphics.blit(DIALOGUE_BG, this.panelX, this.panelY, 0.0F, 0.0F, this.panelW, this.panelH, 512, 512);
      Component npcName = this.npcName().copy().withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD});
      TextUtil.drawCenteredStringWithBorder(guiGraphics, this.font, npcName, this.panelX + this.panelW / 2, this.panelY + 12, 16777215);
      this.renderDialogueSection(guiGraphics);
      this.renderQuestListSection(guiGraphics, uiMouseX, uiMouseY);
      this.renderQuestDetails(guiGraphics);
      super.render(guiGraphics, uiMouseX, uiMouseY, partialTick);
      this.endUiScale(guiGraphics);
   }

   private void renderDialogueSection(GuiGraphics guiGraphics) {
      int diagX = this.panelX + 14;
      int diagY = this.panelY + 28;
      int diagW = this.panelW - 28;
      int diagH = 55;
      List<FormattedCharSequence> diagLines = this.font.split(this.dialogueLine(), diagW - 10);
      this.dialogueMaxScroll = (float)Math.max(0, diagLines.size() * (9 + 2) - diagH);
      this.dialogueTargetScroll = Mth.clamp(this.dialogueTargetScroll, 0.0F, this.dialogueMaxScroll);
      this.renderScrollableFormatted(guiGraphics, this.dialogueBar, diagLines, diagX, diagY, diagW, diagH, this.dialogueScroll, this.dialogueMaxScroll);
   }

   private void renderQuestListSection(GuiGraphics guiGraphics, int uiMouseX, int uiMouseY) {
      int listY = this.panelY + 120;
      int listX = this.panelX + 14;
      int listW = Math.min(150, this.panelW - 20);
      int viewHeight = 126;
      TextUtil.drawStringWithBorder(
         guiGraphics,
         this.font,
         this.tr("gui.dragonminez.sidequest.available_quests", new Object[0]).withStyle(ChatFormatting.YELLOW).withStyle(ChatFormatting.BOLD),
         listX + 2,
         listY - 12,
         16777215
      );
      if (this.questEntries.isEmpty()) {
         TextUtil.drawStringWithBorder(
            guiGraphics,
            this.font,
            this.tr("gui.dragonminez.sidequest.no_quests", new Object[0]).withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.BOLD),
            listX + 4,
            listY + 4,
            -7829368
         );
      } else {
         this.listMaxScroll = (float)Math.max(0, this.questEntries.size() * 18 - viewHeight);
         this.listTargetScroll = Mth.clamp(this.listTargetScroll, 0.0F, this.listMaxScroll);
         guiGraphics.enableScissor(
            this.toScreenCoord((double)listX),
            this.toScreenCoord((double)listY),
            this.toScreenCoord((double)(listX + listW + 6)),
            this.toScreenCoord((double)(listY + viewHeight))
         );
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate(0.0F, -this.listScroll, 0.0F);

         for (int i = 0; i < this.questEntries.size(); i++) {
            int entryY = listY + i * 18;
            if ((float)(entryY + 18) >= (float)listY + this.listScroll && (float)entryY <= (float)(listY + viewHeight) + this.listScroll) {
               QuestNPCDialogueScreen.QuestEntry entry = this.questEntries.get(i);
               boolean isSelected = i == this.selectedIndex;
               boolean isHovered = uiMouseX >= listX
                  && uiMouseX <= listX + listW
                  && (float)uiMouseY >= (float)entryY - this.listScroll
                  && (float)uiMouseY < (float)(entryY + 18) - this.listScroll;
               MutableComponent titleComp = this.tr(entry.quest.getTitle(), new Object[0]);
               if (isSelected) {
                  titleComp.withStyle(ChatFormatting.YELLOW);
               } else if (isHovered) {
                  titleComp.withStyle(ChatFormatting.GRAY);
               } else {
                  titleComp.withStyle(ChatFormatting.WHITE);
               }

               Component questName = this.statusPrefix(entry.type).append(titleComp);
               TextUtil.drawStringWithBorder(guiGraphics, this.font, questName, listX + 4, entryY + 4, 16777215);
            }
         }

         guiGraphics.pose().popPose();
         guiGraphics.disableScissor();
         this.listBar.update(listX + listW, 2, listY, viewHeight, this.listMaxScroll);
         if (this.listMaxScroll > 0.0F) {
            int scrollBarX = listX + listW;
            guiGraphics.fill(scrollBarX, listY, scrollBarX + 2, listY + viewHeight, -13421773);
            float scrollPercent = this.listScroll / this.listMaxScroll;
            int indicatorHeight = Math.max(10, (int)((float)viewHeight / (float)(this.questEntries.size() * 18) * (float)viewHeight));
            int indicatorY = listY + (int)((float)(viewHeight - indicatorHeight) * scrollPercent);
            guiGraphics.fill(scrollBarX, indicatorY, scrollBarX + 2, indicatorY + indicatorHeight, -5592406);
         }
      }
   }

   private void renderQuestDetails(GuiGraphics guiGraphics) {
      if (this.selectedIndex >= 0 && this.selectedIndex < this.questEntries.size()) {
         int listX = this.panelX + 14;
         int listW = Math.min(150, this.panelW - 20);
         int detailX = listX + listW + 24;
         int detailW = this.panelX + this.panelW - detailX - 14;
         int detailY = this.panelY + 120;
         QuestNPCDialogueScreen.QuestEntry selected = this.questEntries.get(this.selectedIndex);
         List<FormattedCharSequence> titleLines = this.font
            .split(this.tr(selected.quest.getTitle(), new Object[0]).withStyle(ChatFormatting.GOLD).withStyle(ChatFormatting.BOLD), detailW);
         int titleY = detailY;

         for (FormattedCharSequence seq : titleLines) {
            TextUtil.drawCenteredStringWithBorder(guiGraphics, this.font, seq, detailX + detailW / 2 - 2, titleY, 16777215);
            titleY += 9 + 2;
         }

         int descY = detailY + 26;
         List<FormattedCharSequence> descLines = this.font
            .split(this.tr(selected.quest.getDescription(), new Object[0]).withStyle(ChatFormatting.GRAY), detailW - 8);
         this.descMaxScroll = (float)Math.max(0, descLines.size() * (9 + 2) - 33);
         this.descTargetScroll = Mth.clamp(this.descTargetScroll, 0.0F, this.descMaxScroll);
         this.renderScrollableFormatted(guiGraphics, this.descBar, descLines, detailX, descY, detailW, 33, this.descScroll, this.descMaxScroll);
         int objY = detailY + 63;
         List<FormattedCharSequence> objLines = new ArrayList<>();

         for (QuestObjective objective : selected.quest.getObjectives()) {
            Component objText = this.txt("- ")
               .withStyle(ChatFormatting.GRAY)
               .append(QuestTextFormatter.describeObjective(objective).copy().withStyle(ChatFormatting.WHITE));
            objLines.addAll(this.font.split(objText, detailW - 8));
         }

         this.objMaxScroll = (float)Math.max(0, objLines.size() * (9 + 2) - 33);
         this.objTargetScroll = Mth.clamp(this.objTargetScroll, 0.0F, this.objMaxScroll);
         this.renderScrollableFormatted(guiGraphics, this.objBar, objLines, detailX, objY, detailW, 33, this.objScroll, this.objMaxScroll);
         int rewTitleY = detailY + 100;
         TextUtil.drawStringWithBorder(
            guiGraphics, this.font, this.tr("gui.dragonminez.sidequest.rewards", new Object[0]).withStyle(ChatFormatting.GOLD), detailX, rewTitleY, 16777215
         );
         int rewY = rewTitleY + 11;
         List<FormattedCharSequence> rewLines = new ArrayList<>();
         PlayerQuestData questData = StatsProvider.get(StatsCapability.INSTANCE, Minecraft.getInstance().player)
            .map(StatsData::getPlayerQuestData)
            .orElse(null);
         Difficulty difficulty = questData != null ? questData.getDifficulty() : Difficulty.NORMAL;
         boolean tiered = QuestTextFormatter.hasRewardTiers(selected.quest.getRewards());

         for (QuestTextFormatter.RewardGroup group : QuestTextFormatter.groupRewardsByDifficulty(selected.quest.getRewards(), false)) {
            List<QuestReward> tierRewards = group.rewards();
            boolean tierLocked = !group.difficulties().contains(difficulty);
            if (tiered) {
               Component header = QuestTextFormatter.describeRewardDifficulties(group.difficulties())
                  .copy()
                  .withStyle(tierLocked ? ChatFormatting.DARK_GRAY : QuestTextFormatter.rewardDifficultyStyle(group.difficulties()));
               rewLines.addAll(this.font.split(header, detailW - 8));
            }

            for (QuestReward reward : tierRewards) {
               double rewardMultiplier = questData != null ? questData.rewardMultiplierFor(reward) : difficulty.questRewardMultiplier();
               Component rewText = this.txt("  ")
                  .append(reward.getDescription(rewardMultiplier))
                  .withStyle(tierLocked ? ChatFormatting.DARK_GRAY : ChatFormatting.GREEN);
               rewLines.addAll(this.font.split(rewText, detailW - 8));
            }
         }

         this.rewardMaxScroll = (float)Math.max(0, rewLines.size() * (9 + 2) - 33);
         this.rewardTargetScroll = Mth.clamp(this.rewardTargetScroll, 0.0F, this.rewardMaxScroll);
         this.renderScrollableFormatted(guiGraphics, this.rewardBar, rewLines, detailX, rewY, detailW, 33, this.rewardScroll, this.rewardMaxScroll);
      }
   }

   private void renderScrollableFormatted(
      GuiGraphics guiGraphics, ScrollbarState bar, List<FormattedCharSequence> lines, int x, int y, int width, int height, float currentScroll, float maxScroll
   ) {
      int lineHeight = 9 + 2;
      int totalContentHeight = lines.size() * lineHeight;
      bar.update(x + width - 4, 2, y, height, maxScroll);
      guiGraphics.enableScissor(
         this.toScreenCoord((double)x), this.toScreenCoord((double)y), this.toScreenCoord((double)(x + width)), this.toScreenCoord((double)(y + height))
      );
      guiGraphics.pose().pushPose();
      guiGraphics.pose().translate(0.0F, -currentScroll, 0.0F);

      for (int i = 0; i < lines.size(); i++) {
         float lineY = (float)(y + i * lineHeight);
         if (lineY + (float)lineHeight >= (float)y + currentScroll && lineY <= (float)(y + height) + currentScroll) {
            TextUtil.drawStringWithBorder(guiGraphics, this.font, lines.get(i), x, (int)lineY, 16777215);
         }
      }

      guiGraphics.pose().popPose();
      guiGraphics.disableScissor();
      if (maxScroll > 0.0F) {
         int scrollBarX = x + width - 4;
         guiGraphics.fill(scrollBarX, y, scrollBarX + 2, y + height, -13421773);
         float scrollPercent = maxScroll == 0.0F ? 0.0F : currentScroll / maxScroll;
         int indicatorHeight = Math.max(10, (int)((float)height / (float)totalContentHeight * (float)height));
         int indicatorY = y + (int)((float)(height - indicatorHeight) * scrollPercent);
         guiGraphics.fill(scrollBarX, indicatorY, scrollBarX + 2, indicatorY + indicatorHeight, -5592406);
      }
   }

   private MutableComponent statusPrefix(QuestNPCDialogueScreen.EntryType type) {
      return switch (type) {
         case OFFER -> this.txt("[!] ").withStyle(ChatFormatting.GREEN);
         case TURN_IN -> this.txt("[?] ").withStyle(ChatFormatting.AQUA);
         case IN_PROGRESS -> this.txt("[...] ").withStyle(ChatFormatting.YELLOW);
      };
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      double uiMouseX = this.toUiX(mouseX);
      double uiMouseY = this.toUiY(mouseY);
      if (this.dialogueBar.tryStartDrag(uiMouseX, uiMouseY)) {
         this.dialogueTargetScroll = this.dialogueBar.scrollFor(uiMouseY);
         return true;
      } else if (this.listBar.tryStartDrag(uiMouseX, uiMouseY)) {
         this.listTargetScroll = this.listBar.scrollFor(uiMouseY);
         return true;
      } else if (this.descBar.tryStartDrag(uiMouseX, uiMouseY)) {
         this.descTargetScroll = this.descBar.scrollFor(uiMouseY);
         return true;
      } else if (this.objBar.tryStartDrag(uiMouseX, uiMouseY)) {
         this.objTargetScroll = this.objBar.scrollFor(uiMouseY);
         return true;
      } else if (this.rewardBar.tryStartDrag(uiMouseX, uiMouseY)) {
         this.rewardTargetScroll = this.rewardBar.scrollFor(uiMouseY);
         return true;
      } else {
         int listY = this.panelY + 120;
         int listX = this.panelX + 14;
         int listW = Math.min(150, this.panelW - 20);
         int viewHeight = 126;
         if (uiMouseX >= (double)listX && uiMouseX <= (double)(listX + listW) && uiMouseY >= (double)listY && uiMouseY <= (double)(listY + viewHeight)) {
            int relativeY = (int)(uiMouseY - (double)listY + (double)this.listScroll);
            int index = relativeY / 18;
            if (index >= 0 && index < this.questEntries.size()) {
               this.selectedIndex = index;
               this.descTargetScroll = 0.0F;
               this.objTargetScroll = 0.0F;
               this.rewardTargetScroll = 0.0F;
               this.initButtons();
               return true;
            }
         }

         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      double uiMouseY = this.toUiY(mouseY);
      if (this.dialogueBar.isDragging()) {
         this.dialogueTargetScroll = this.dialogueBar.scrollFor(uiMouseY);
         return true;
      } else if (this.listBar.isDragging()) {
         this.listTargetScroll = this.listBar.scrollFor(uiMouseY);
         return true;
      } else if (this.descBar.isDragging()) {
         this.descTargetScroll = this.descBar.scrollFor(uiMouseY);
         return true;
      } else if (this.objBar.isDragging()) {
         this.objTargetScroll = this.objBar.scrollFor(uiMouseY);
         return true;
      } else if (this.rewardBar.isDragging()) {
         this.rewardTargetScroll = this.rewardBar.scrollFor(uiMouseY);
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      boolean wasDragging = this.dialogueBar.isDragging()
         || this.listBar.isDragging()
         || this.descBar.isDragging()
         || this.objBar.isDragging()
         || this.rewardBar.isDragging();
      this.dialogueBar.stopDrag();
      this.listBar.stopDrag();
      this.descBar.stopDrag();
      this.objBar.stopDrag();
      this.rewardBar.stopDrag();
      return wasDragging ? true : super.mouseReleased(mouseX, mouseY, button);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      double uiMouseX = this.toUiX(mouseX);
      double uiMouseY = this.toUiY(mouseY);
      int scrollAmount = (int)Math.signum(scrollY);
      if (uiMouseX >= (double)(this.panelX + 14)
         && uiMouseX <= (double)(this.panelX + this.panelW - 6)
         && uiMouseY >= (double)(this.panelY + 28)
         && uiMouseY <= (double)(this.panelY + 83)) {
         this.dialogueTargetScroll = Mth.clamp(this.dialogueTargetScroll - (float)(scrollAmount * 13), 0.0F, this.dialogueMaxScroll);
         return true;
      } else {
         int listY = this.panelY + 120;
         int listX = this.panelX + 14;
         int listW = Math.min(150, this.panelW - 20);
         if (uiMouseX >= (double)listX && uiMouseX <= (double)(listX + listW) && uiMouseY >= (double)listY && uiMouseY <= (double)(listY + 126)) {
            this.listTargetScroll = Mth.clamp(this.listTargetScroll - (float)(scrollAmount * 18), 0.0F, this.listMaxScroll);
            return true;
         } else {
            int detailX = listX + listW + 10;
            int detailW = this.panelX + this.panelW - detailX - 14;
            int detailY = this.panelY + 120;
            if (uiMouseX >= (double)detailX && uiMouseX <= (double)(detailX + detailW)) {
               int descY = detailY + 26;
               if (uiMouseY >= (double)descY && uiMouseY <= (double)(descY + 33)) {
                  this.descTargetScroll = Mth.clamp(this.descTargetScroll - (float)(scrollAmount * 13), 0.0F, this.descMaxScroll);
                  return true;
               }

               int objY = detailY + 63;
               if (uiMouseY >= (double)objY && uiMouseY <= (double)(objY + 33)) {
                  this.objTargetScroll = Mth.clamp(this.objTargetScroll - (float)(scrollAmount * 13), 0.0F, this.objMaxScroll);
                  return true;
               }

               int rewY = detailY + 111;
               if (uiMouseY >= (double)rewY && uiMouseY <= (double)(rewY + 33)) {
                  this.rewardTargetScroll = Mth.clamp(this.rewardTargetScroll - (float)(scrollAmount * 13), 0.0F, this.rewardMaxScroll);
                  return true;
               }
            }

            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
         }
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   private void openMasterScreen() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         if (TEXT_MASTERS.contains(this.npcId)) {
            mc.setScreen(new MasterTextScreen(this.npcId));
         } else {
            LivingEntity livingEntity = (this.entityId >= 0 ? mc.level.getEntity(this.entityId) : null) instanceof LivingEntity living ? living : null;
            mc.setScreen(new MastersSkillsScreen(this.npcId, livingEntity));
         }
      }
   }

   private void openServicesScreen() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         mc.setScreen(new MasterTextScreen(this.npcId));
      }
   }

   private MutableComponent npcName() {
      String questNpcKey = "entity.dragonminez.questnpc." + this.npcId;
      if (I18n.exists(questNpcKey)) {
         return this.tr(questNpcKey, new Object[0]);
      } else {
         String masterKey = "gui.dragonminez.lines." + this.npcId + ".name";
         return I18n.exists(masterKey) ? this.tr(masterKey, new Object[0]) : Component.literal(this.npcId).withStyle(Style.EMPTY.withFont(DMZ_FONT));
      }
   }

   private MutableComponent dialogueLine() {
      String stage = this.getDialogueStage();
      String npcLine = "dialogue.dragonminez.story.sidequest." + this.npcId + "." + stage;
      return I18n.exists(npcLine) ? this.tr(npcLine, new Object[0]) : this.tr("dialogue.dragonminez.story.sidequest.generic_npc." + stage, new Object[0]);
   }

   private String getDialogueStage() {
      if (!this.turnInQuestIds.isEmpty()) {
         return "complete";
      } else if (!this.offerableQuestIds.isEmpty()) {
         return "offer";
      } else {
         return !this.inProgressQuestIds.isEmpty() ? "in_progress" : "idle";
      }
   }

   private static enum EntryType {
      OFFER,
      TURN_IN,
      IN_PROGRESS;
   }

   private static record QuestEntry(String questId, Quest quest, QuestNPCDialogueScreen.EntryType type) {
   }
}
