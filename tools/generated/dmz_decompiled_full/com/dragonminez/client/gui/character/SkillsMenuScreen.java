package com.dragonminez.client.gui.character;

import com.dragonminez.client.gui.buttons.ClippableTextureButton;
import com.dragonminez.client.gui.buttons.CustomTextureButton;
import com.dragonminez.client.gui.buttons.TexturedTextButton;
import com.dragonminez.client.gui.character.util.BaseMenuScreen;
import com.dragonminez.client.render.EntityPreviewRenderContext;
import com.dragonminez.client.render.layer.DMZSkinLayer;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.common.config.RaceCharacterConfig;
import com.dragonminez.common.config.SkillsConfig;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.DeleteTechniqueC2S;
import com.dragonminez.common.network.C2S.EquipTechniqueC2S;
import com.dragonminez.common.network.C2S.ImportTechniqueC2S;
import com.dragonminez.common.network.C2S.UpdateSkillC2S;
import com.dragonminez.common.network.C2S.UpgradeTechniqueC2S;
import com.dragonminez.common.network.S2C.TechniqueImportResultS2C;
import com.dragonminez.common.quest.Quest;
import com.dragonminez.common.quest.QuestRegistry;
import com.dragonminez.common.quest.QuestReward;
import com.dragonminez.common.quest.Saga;
import com.dragonminez.common.quest.rewards.SkillReward;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.character.Status;
import com.dragonminez.common.stats.skills.Skill;
import com.dragonminez.common.stats.skills.Skills;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import com.dragonminez.common.stats.techniques.TechniqueData;
import com.dragonminez.common.util.TransformationsHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class SkillsMenuScreen extends BaseMenuScreen {
   private static final ResourceLocation STAT_BUTTONS = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/characterbuttons.png");
   private static final ResourceLocation MENU_BIG = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menubig.png");
   private static final ResourceLocation MENU_SMALL = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menusmall.png");
   private static final ResourceLocation BUTTONS_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/characterbuttons.png");
   private static final ResourceLocation EXCLAMATION_MARK = ResourceLocation.fromNamespaceAndPath(
      "dragonminez", "textures/gui/quest/exclamation_mark_quest.png"
   );
   private static final int SKILL_ITEM_HEIGHT = 20;
   private static final int MAX_VISIBLE_SKILLS = 8;
   private static final int TECHNIQUE_BIND_SLOT_COUNT = 8;
   private static final String NEW_SKILL_ENTRY = "__new_skill__";
   private static final String CLASS_PASSIVE_ENTRY = "__class_passive__";
   private static final List<String> PREVIEW_FORM_TYPE_ORDER = List.of("superforms", "androidforms", "legendaryforms", "godforms");
   private SkillsMenuScreen.SkillCategory currentCategory = SkillsMenuScreen.SkillCategory.SKILLS;
   private StatsData statsData;
   private int tickCount = 0;
   private String selectedSkill = null;
   private float targetScroll = 0.0F;
   private float currentScroll = 0.0F;
   private float maxScroll = 0.0F;
   private float targetDescScroll = 0.0F;
   private float currentDescScroll = 0.0F;
   private float maxDescScroll = 0.0F;
   private boolean isDraggingMainScroll = false;
   private boolean isDraggingDescScroll = false;
   private boolean isBinding = false;
   private boolean isImportingTechnique = false;
   private ClippableTextureButton skillsButton;
   private ClippableTextureButton kiButton;
   private ClippableTextureButton formsButton;
   private ClippableTextureButton stacksButton;
   private CustomTextureButton btnDmg;
   private CustomTextureButton btnSize;
   private CustomTextureButton btnSpeed;
   private CustomTextureButton btnPen;
   private CustomTextureButton btnCast;
   private CustomTextureButton btnCd;
   private float buttonRevealProgress = 0.0F;
   private float formsTransitionProgress = 0.0F;
   private float leftPanelHoverProgress = 0.0F;
   private int currentLeftX = 12;
   private int currentRightX = 0;
   private EditBox techniqueImportBox;
   private Component actionStatusText = Component.empty();
   private int actionStatusTimer = 0;
   private int actionStatusColor = 16777215;
   private static Component pendingImportStatusText = null;
   private static int pendingImportStatusColor = 16777215;
   private static boolean pendingImportSuccess = false;
   private TexturedTextButton upgradeButton;
   private float formsPanX = 0.0F;
   private float formsPanY = 0.0F;
   private float formsZoom = 1.0F;
   private boolean isDraggingForms = false;
   private double dragFormStartX;
   private double dragFormStartY;
   private float dragFormStartPanX;
   private float dragFormStartPanY;
   private String selectedFormGroup = null;
   private String selectedFormName = null;
   private long lastFormClickTime = 0L;
   private final List<SkillsMenuScreen.FormNode> formNodes = new ArrayList<>();

   public SkillsMenuScreen() {
      super(Component.literal("Skills"));
   }

   @Override
   protected void init() {
      super.init();
      this.updateStatsData();
      this.initDynamicButtons();
      if (this.currentCategory == SkillsMenuScreen.SkillCategory.FORMS) {
         this.buildFormsTree();
      }
   }

   @Override
   public void tick() {
      super.tick();
      this.tickCount++;
      if (this.actionStatusTimer > 0) {
         this.actionStatusTimer--;
      }

      this.consumePendingImportStatus();
      if (this.tickCount >= 10) {
         this.tickCount = 0;
         this.updateStatsData();
         if (!this.isBinding && !this.isImportingTechnique) {
            this.refreshButtons();
         }
      }
   }

   private void updateStatsData() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> this.statsData = data);
      }
   }

   private void buildFormsTree() {
      this.formNodes.clear();
      if (this.statsData != null) {
         String race = this.statsData.getCharacter().getRaceName().toLowerCase(Locale.ROOT);
         SkillsConfig skillsConfig = ConfigManager.getSkillsConfig();
         List<TransformationsHelper.OrderedFormEntry> orderedForms = TransformationsHelper.getOrderedFormsForRace(race, PREVIEW_FORM_TYPE_ORDER);
         Map<String, List<FormConfig.FormData>> groupedForms = new LinkedHashMap<>();
         Map<String, String> groupTypes = new LinkedHashMap<>();

         for (TransformationsHelper.OrderedFormEntry entry : orderedForms) {
            if (entry.getFormData() != null && skillsConfig.isSkillAllowedForRace(TransformationsHelper.getSkillNameForType(entry.getFormType()), race)) {
               groupedForms.computeIfAbsent(entry.getGroupName(), k -> new ArrayList<>()).add(entry.getFormData());
               groupTypes.putIfAbsent(entry.getGroupName(), entry.getFormType());
            }
         }

         for (String stackSkill : skillsConfig.getStackSkills()) {
            if (skillsConfig.isSkillAllowedForRace(stackSkill, race)) {
               FormConfig stackGroup = ConfigManager.getStackFormGroup(stackSkill);
               if (stackGroup != null) {
                  List<FormConfig.FormData> stackForms = new ArrayList<>(stackGroup.getForms().values());
                  if (!stackForms.isEmpty()) {
                     groupedForms.computeIfAbsent(stackGroup.getGroupName(), k -> new ArrayList<>()).addAll(stackForms);
                     groupTypes.putIfAbsent(stackGroup.getGroupName(), stackGroup.getFormType());
                  }
               }
            }
         }

         int groupY = -(groupedForms.size() * 80) / 2;

         for (Entry<String, List<FormConfig.FormData>> group : groupedForms.entrySet()) {
            int formX = -(group.getValue().size() * 80) / 2;
            String groupName = group.getKey();
            String type = groupTypes.getOrDefault(groupName, "superforms");

            for (FormConfig.FormData form : group.getValue()) {
               this.formNodes.add(new SkillsMenuScreen.FormNode(groupName, type, form, formX, groupY));
               formX += 80;
            }

            groupY += 80;
         }
      }
   }

   private void initDynamicButtons() {
      int leftPanelX = 12;
      int centerY = this.getUiHeight() / 2;
      int leftPanelY = centerY - 105;
      int buttonY = leftPanelY + 6;
      int hiddenX = leftPanelX + 122;
      int scissorX = leftPanelX + 141;
      int scissorXScreen = this.toScreenCoord((double)scissorX);
      int scissorYScreen = this.toScreenCoord(0.0);
      int scissorRight = this.toScreenCoord((double)this.getUiWidth());
      int scissorBottom = this.toScreenCoord((double)this.getUiHeight());
      this.skillsButton = new ClippableTextureButton.Builder()
         .position(hiddenX, buttonY)
         .size(26, 32)
         .texture(MENU_BIG)
         .textureCoords(142, 44, 142, 44)
         .clipping(true, scissorXScreen, scissorYScreen, scissorRight, scissorBottom)
         .onPress(btn -> {
            this.currentCategory = SkillsMenuScreen.SkillCategory.SKILLS;
            this.selectedSkill = null;
            this.targetScroll = 0.0F;
            this.currentScroll = 0.0F;
            this.targetDescScroll = 0.0F;
            this.currentDescScroll = 0.0F;
            this.refreshButtons();
         })
         .build();
      this.kiButton = new ClippableTextureButton.Builder()
         .position(hiddenX, buttonY + 32)
         .size(26, 32)
         .texture(MENU_BIG)
         .textureCoords(170, 44, 170, 44)
         .clipping(true, scissorXScreen, scissorYScreen, scissorRight, scissorBottom)
         .onPress(btn -> {
            this.currentCategory = SkillsMenuScreen.SkillCategory.KI;
            this.selectedSkill = null;
            this.targetScroll = 0.0F;
            this.currentScroll = 0.0F;
            this.targetDescScroll = 0.0F;
            this.currentDescScroll = 0.0F;
            this.refreshButtons();
         })
         .build();
      this.formsButton = new ClippableTextureButton.Builder()
         .position(hiddenX, buttonY + 64)
         .size(26, 32)
         .texture(MENU_BIG)
         .textureCoords(198, 44, 198, 44)
         .clipping(true, scissorXScreen, scissorYScreen, scissorRight, scissorBottom)
         .onPress(btn -> {
            this.currentCategory = SkillsMenuScreen.SkillCategory.FORMS;
            this.selectedSkill = null;
            this.selectedFormName = null;
            this.selectedFormGroup = null;
            this.buildFormsTree();
            this.targetScroll = 0.0F;
            this.currentScroll = 0.0F;
            this.targetDescScroll = 0.0F;
            this.currentDescScroll = 0.0F;
            this.refreshButtons();
         })
         .build();
      this.stacksButton = new ClippableTextureButton.Builder()
         .position(hiddenX, buttonY + 96)
         .size(26, 32)
         .texture(MENU_BIG)
         .textureCoords(226, 44, 226, 44)
         .clipping(true, scissorXScreen, scissorYScreen, scissorRight, scissorBottom)
         .onPress(btn -> {
            this.currentCategory = SkillsMenuScreen.SkillCategory.STRIKE;
            this.selectedSkill = null;
            this.targetScroll = 0.0F;
            this.currentScroll = 0.0F;
            this.targetDescScroll = 0.0F;
            this.currentDescScroll = 0.0F;
            this.refreshButtons();
         })
         .build();
      this.addRenderableWidget(this.skillsButton);
      this.addRenderableWidget(this.kiButton);
      this.addRenderableWidget(this.formsButton);
      this.addRenderableWidget(this.stacksButton);
   }

   private List<String> getVisibleSkillNames() {
      if (this.statsData == null) {
         return new ArrayList<>();
      } else {
         Skills skills = this.statsData.getSkills();
         List<String> skillNames = new ArrayList<>();
         SkillsConfig skillsConfig = ConfigManager.getSkillsConfig();
         String race = this.statsData.getCharacter().getRaceName();
         switch (this.currentCategory) {
            case SKILLS:
               skills.getAllSkills()
                  .forEach(
                     (name, skill) -> {
                        if (!skillsConfig.getKiSkills().contains(name)
                           && !skillsConfig.getStrikeSkills().contains(name)
                           && !skillsConfig.getStackSkills().contains(name)
                           && !skillsConfig.getFormSkills().contains(name)
                           && skillsConfig.isSkillAllowedForRace(name, race)) {
                           skillNames.add(name);
                        }
                     }
                  );
               break;
            case KI:
               skillNames.add("__new_skill__");
               this.statsData.getTechniques().getUnlockedTechniques().forEach((id, technique) -> {
                  if (technique instanceof KiAttackData && skillsConfig.isSkillAllowedForRace(id, race)) {
                     skillNames.add(id);
                  }
               });
               break;
            case FORMS:
               skills.getAllSkills()
                  .forEach(
                     (name, skill) -> {
                        if ((skillsConfig.getFormSkills().contains(name) || skillsConfig.getStackSkills().contains(name))
                           && skillsConfig.isSkillAllowedForRace(name, race)) {
                           skillNames.add(name);
                        }
                     }
                  );
               break;
            case STRIKE:
               this.statsData.getTechniques().getUnlockedTechniques().forEach((id, technique) -> {
                  if (technique instanceof StrikeAttackData && skillsConfig.isSkillAllowedForRace(id, race)) {
                     skillNames.add(id);
                  }
               });
         }

         skillNames.sort((a, b) -> this.getDisplayNameForEntry(a).compareToIgnoreCase(this.getDisplayNameForEntry(b)));
         if (skillNames.remove("__new_skill__")) {
            skillNames.add(0, "__new_skill__");
         }

         if (this.currentCategory == SkillsMenuScreen.SkillCategory.SKILLS) {
            int classPassiveIndex = 0;
            String raceLower = race.toLowerCase();
            if (!raceLower.isEmpty() && !ConfigManager.getRaceCharacter(raceLower).getRacialSkill().isEmpty()) {
               skillNames.add(0, "racial_" + ConfigManager.getRaceCharacter(raceLower).getRacialSkill());
               classPassiveIndex = 1;
            }

            skillNames.add(classPassiveIndex, "__class_passive__");
         }

         return skillNames;
      }
   }

   private void refreshButtons() {
      this.clearWidgets();
      if (this.upgradeButton != null) {
         this.removeWidget(this.upgradeButton);
      }

      if (this.btnDmg != null) {
         this.removeWidget(this.btnDmg);
      }

      if (this.btnSize != null) {
         this.removeWidget(this.btnSize);
      }

      if (this.btnSpeed != null) {
         this.removeWidget(this.btnSpeed);
      }

      if (this.btnPen != null) {
         this.removeWidget(this.btnPen);
      }

      if (this.btnCast != null) {
         this.removeWidget(this.btnCast);
      }

      if (this.btnCd != null) {
         this.removeWidget(this.btnCd);
      }

      this.isBinding = false;
      this.techniqueImportBox = null;
      this.upgradeButton = null;
      this.btnDmg = null;
      this.btnSize = null;
      this.btnSpeed = null;
      this.btnPen = null;
      this.btnCast = null;
      this.btnCd = null;
      this.initDynamicButtons();
      this.initNavigationButtons();
      if (this.currentCategory != SkillsMenuScreen.SkillCategory.FORMS) {
         this.initUpgradeButton();
         this.initCreateSkillButton();
         this.initBindButtons();
         this.initTechniqueUpgradeButtons();
      }
   }

   private void initTechniqueUpgradeButtons() {
      if (this.selectedSkill != null
         && this.statsData != null
         && (this.currentCategory == SkillsMenuScreen.SkillCategory.KI || this.currentCategory == SkillsMenuScreen.SkillCategory.STRIKE)) {
         if (!"__new_skill__".equals(this.selectedSkill)) {
            TechniqueData tech = this.statsData.getTechniques().getUnlockedTechniques().get(this.selectedSkill);
            if (tech != null) {
               int rightPanelX = this.getUiWidth() - 158;
               int centerY = this.getUiHeight() / 2;
               int rightPanelY = centerY - 105;
               int btnX = rightPanelX + 115;
               int yOffset = rightPanelY + 80;
               if (tech instanceof KiAttackData) {
                  if (this.shouldShowTechniqueUpgradeButton(tech, "damage")) {
                     this.btnDmg = this.createUpgradeBtn(btnX, yOffset, "damage", true);
                     this.addRenderableWidget(this.btnDmg);
                  }

                  yOffset += 12;
                  if (this.shouldShowTechniqueUpgradeButton(tech, "size")) {
                     this.btnSize = this.createUpgradeBtn(btnX, yOffset, "size", true);
                     this.addRenderableWidget(this.btnSize);
                  }

                  yOffset += 12;
                  if (this.shouldShowTechniqueUpgradeButton(tech, "speed")) {
                     this.btnSpeed = this.createUpgradeBtn(btnX, yOffset, "speed", true);
                     this.addRenderableWidget(this.btnSpeed);
                  }

                  yOffset += 12;
                  if (this.shouldShowTechniqueUpgradeButton(tech, "armor_pen")) {
                     this.btnPen = this.createUpgradeBtn(btnX, yOffset, "armor_pen", true);
                     this.addRenderableWidget(this.btnPen);
                  }

                  yOffset += 12;
               } else {
                  if (this.shouldShowTechniqueUpgradeButton(tech, "damage")) {
                     this.btnDmg = this.createUpgradeBtn(btnX, yOffset, "damage", true);
                     this.addRenderableWidget(this.btnDmg);
                  }

                  yOffset += 12;
               }

               if (this.shouldShowTechniqueUpgradeButton(tech, "cooldown")) {
                  this.btnCd = this.createUpgradeBtn(btnX, yOffset, "cooldown", true);
                  this.addRenderableWidget(this.btnCd);
               }
            }
         }
      }
   }

   private CustomTextureButton createUpgradeBtn(int x, int y, String statName, boolean active) {
      CustomTextureButton btn = new CustomTextureButton.Builder()
         .position(x, y - 1)
         .size(14, 11)
         .texture(BUTTONS_TEXTURE)
         .textureCoords(0, 0, 0, 10)
         .textureSize(10, 10)
         .onPress(button -> NetworkHandler.INSTANCE.sendToServer(new UpgradeTechniqueC2S(this.selectedSkill, statName)))
         .build();
      btn.active = active;
      return btn;
   }

   private boolean shouldShowTechniqueUpgradeButton(TechniqueData tech, String statName) {
      if (!this.hasEnoughTechniqueXpForUpgrade(tech, statName)) {
         return false;
      } else {
         return tech instanceof KiAttackData kiAttackData ? kiAttackData.canUpgradeStat(statName) : "damage".equals(statName) || "cooldown".equals(statName);
      }
   }

   private boolean hasEnoughTechniqueXpForUpgrade(TechniqueData tech, String statName) {
      return tech.getExperience() >= this.getTechniqueUpgradeXpCost(tech, statName);
   }

   private int getTechniqueUpgradeXpCost(TechniqueData tech, String statName) {
      if (tech instanceof KiAttackData kiAttackData) {
         return kiAttackData.getUpgradeXpCost(statName);
      } else {
         return tech instanceof StrikeAttackData strikeAttackData ? strikeAttackData.getUpgradeXpCost(statName) : 100;
      }
   }

   private void initBindButtons() {
      if (this.selectedSkill != null
         && this.statsData != null
         && (this.currentCategory == SkillsMenuScreen.SkillCategory.KI || this.currentCategory == SkillsMenuScreen.SkillCategory.STRIKE)) {
         if (!"__new_skill__".equals(this.selectedSkill)) {
            int rightPanelX = this.getUiWidth() - 158;
            int centerY = this.getUiHeight() / 2;
            int rightPanelY = centerY - 105;
            TechniqueData tech = this.statsData.getTechniques().getUnlockedTechniques().get(this.selectedSkill);
            boolean isKi = tech instanceof KiAttackData;
            boolean canShare = isKi && !PredefinedTechniques.isPredefinedTechniqueId(this.selectedSkill);
            if (!this.isBinding) {
               int yPos = rightPanelY + 185;
               if (canShare) {
                  CustomTextureButton importButton = new CustomTextureButton.Builder()
                     .position(rightPanelX + 11, yPos)
                     .size(20, 20)
                     .texture(BUTTONS_TEXTURE)
                     .textureCoords(162, 0, 162, 20)
                     .textureSize(20, 20)
                     .message(Component.empty())
                     .onPress(btn -> {
                        if (!this.isImportingTechnique) {
                           this.isImportingTechnique = true;
                           this.refreshButtons();
                        } else {
                           this.attemptTechniqueImport();
                        }
                     })
                     .build();
                  this.addRenderableWidget(importButton);
               }

               TexturedTextButton bindButton = new TexturedTextButton.Builder()
                  .position(rightPanelX + 35, yPos)
                  .size(74, 20)
                  .texture(BUTTONS_TEXTURE)
                  .textureCoords(0, 28, 0, 48)
                  .textureSize(74, 20)
                  .message(this.tr("gui.dragonminez.skills.bind_to_slot", new Object[0]))
                  .onPress(btn -> {
                     this.isBinding = true;
                     this.clearWidgets();
                     this.initDynamicButtons();
                     this.initNavigationButtons();
                     this.initUpgradeButton();
                     this.initBindButtons();
                  })
                  .build();
               this.addRenderableWidget(bindButton);
               if (canShare) {
                  CustomTextureButton exportButton = new CustomTextureButton.Builder()
                     .position(rightPanelX + 114, yPos)
                     .size(20, 20)
                     .texture(BUTTONS_TEXTURE)
                     .textureCoords(182, 0, 182, 20)
                     .textureSize(20, 20)
                     .message(Component.empty())
                     .onPress(btn -> {
                        if (tech instanceof KiAttackData kiAttackData) {
                           String code = kiAttackData.generateExportCode();
                           if (code != null && !code.isEmpty()) {
                              Minecraft.getInstance().keyboardHandler.setClipboard(code);
                              this.setActionStatus(this.tr("gui.dragonminez.skills.status.copied", new Object[0]), 5635925);
                           } else {
                              this.setActionStatus(this.tr("gui.dragonminez.skills.status.invalid_code", new Object[0]), 16733525);
                           }
                        }
                     })
                     .build();
                  this.addRenderableWidget(exportButton);
               }

               if (canShare && this.isImportingTechnique) {
                  this.techniqueImportBox = new EditBox(this.font, rightPanelX + 7, yPos + 22, 123, 12, Component.empty());
                  this.techniqueImportBox.setMaxLength(65536);
                  this.addRenderableWidget(this.techniqueImportBox);
               }

               this.addRenderableWidget(
                  new CustomTextureButton.Builder()
                     .position(rightPanelX + 119, yPos - 14)
                     .size(14, 11)
                     .texture(STAT_BUTTONS)
                     .textureCoords(10, 0, 10, 10)
                     .textureSize(10, 10)
                     .onPress(btn -> {
                        if (this.selectedSkill != null && !"__new_skill__".equals(this.selectedSkill)) {
                           NetworkHandler.sendToServer(new DeleteTechniqueC2S(this.selectedSkill));
                           this.selectedSkill = null;
                           this.isImportingTechnique = false;
                           this.techniqueImportBox = null;
                           this.refreshButtons();
                           this.selectedSkill = null;
                        }
                     })
                     .build()
               );
            } else {
               for (int i = 0; i < 8; i++) {
                  int slotIndex = i;
                  int slotX = i < 4 ? i * 12 : (i - 4) * 12;
                  int slotY = i < 4 ? rightPanelY + 175 : rightPanelY + 186;
                  TexturedTextButton slotBtn = new TexturedTextButton.Builder()
                     .position(rightPanelX + 50 + slotX, slotY)
                     .size(10, 10)
                     .texture(BUTTONS_TEXTURE)
                     .textureCoords(152, 0, 152, 10)
                     .textureSize(10, 10)
                     .message(Component.literal(String.valueOf(i + 1)))
                     .onPress(btn -> {
                        NetworkHandler.INSTANCE.sendToServer(new EquipTechniqueC2S(slotIndex, this.selectedSkill));
                        this.isBinding = false;
                        this.isImportingTechnique = false;
                        this.refreshButtons();
                     })
                     .build();
                  this.addRenderableWidget(slotBtn);
               }
            }
         }
      }
   }

   private void initCreateSkillButton() {
      if (this.statsData != null && this.currentCategory == SkillsMenuScreen.SkillCategory.KI && "__new_skill__".equals(this.selectedSkill)) {
         int rightPanelX = this.getUiWidth() - 158;
         int centerY = this.getUiHeight() / 2;
         int rightPanelY = centerY - 105;
         TexturedTextButton createButton = new TexturedTextButton.Builder()
            .position(rightPanelX + 35, rightPanelY + 185)
            .size(74, 20)
            .texture(BUTTONS_TEXTURE)
            .textureCoords(0, 28, 0, 48)
            .textureSize(74, 20)
            .message(this.tr("gui.dragonminez.skills.create_skill", new Object[0]))
            .onPress(btn -> this.openTechniqueCreator())
            .build();
         this.addRenderableWidget(createButton);
      }
   }

   private void initUpgradeButton() {
      if (this.selectedSkill != null && this.statsData != null) {
         Skill skill = this.statsData.getSkills().getSkill(this.selectedSkill);
         if (skill != null) {
            int rightPanelX = this.getUiWidth() - 158;
            int centerY = this.getUiHeight() / 2;
            int rightPanelY = centerY - 105;
            int cost = this.getUpgradeCost(this.selectedSkill, skill.getLevel());
            float currentTPS = this.statsData.getResources().getTrainingPoints();
            boolean canUpgrade = !skill.isMaxLevel() && currentTPS >= (float)cost;
            if (cost != -1 && cost != Integer.MAX_VALUE) {
               if (!skill.isMaxLevel()) {
                  this.upgradeButton = new TexturedTextButton.Builder()
                     .position(rightPanelX + 35, rightPanelY + 196)
                     .size(74, 20)
                     .texture(BUTTONS_TEXTURE)
                     .textureCoords(0, 28, 0, 48)
                     .textureSize(74, 20)
                     .message(this.tr("gui.dragonminez.skills.upgrade", new Object[0]))
                     .onPress(btn -> {
                        if (canUpgrade) {
                           NetworkHandler.INSTANCE.sendToServer(new UpdateSkillC2S(UpdateSkillC2S.SkillAction.UPGRADE, this.selectedSkill, cost));
                           this.updateStatsData();
                        }
                     })
                     .build();
                  this.upgradeButton.active = canUpgrade;
                  this.addRenderableWidget(this.upgradeButton);
               }
            }
         }
      }
   }

   private int getUpgradeCost(String skillName, int currentLevel) {
      return this.getUpgradeCostForTargetLevel(skillName, currentLevel);
   }

   private int getUpgradeCostForTargetLevel(String skillName, int targetLevel) {
      if (skillName != null && !skillName.isEmpty()) {
         skillName = skillName.toLowerCase(Locale.ROOT);
         boolean isForm = ConfigManager.getSkillsConfig().getFormSkills().contains(skillName);
         boolean isStack = ConfigManager.getSkillsConfig().getStackSkills().contains(skillName);
         if (isForm) {
            RaceCharacterConfig raceConfig = ConfigManager.getRaceCharacter(this.statsData.getCharacter().getRaceName());
            if (raceConfig == null) {
               return Integer.MAX_VALUE;
            } else {
               Integer[] costs = raceConfig.getFormSkillTpCosts(skillName);
               if (costs != null && targetLevel >= 0 && targetLevel < costs.length) {
                  Integer cost = costs[targetLevel];
                  return cost != null ? cost : Integer.MAX_VALUE;
               } else {
                  return Integer.MAX_VALUE;
               }
            }
         } else if (isStack) {
            SkillsConfig.SkillCosts skillData = ConfigManager.getSkillsConfig().getSkillCosts(skillName);
            if (skillData != null && skillData.getCosts() != null) {
               List<Integer> costs = skillData.getCosts();
               if (targetLevel >= 0 && targetLevel < costs.size()) {
                  Integer cost = costs.get(targetLevel);
                  return cost != null ? cost : Integer.MAX_VALUE;
               }
            }

            return Integer.MAX_VALUE;
         } else {
            SkillsConfig.SkillCosts skillData = ConfigManager.getSkillsConfig().getSkills().get(skillName);
            if (skillData != null && skillData.getCosts() != null) {
               List<Integer> costs = skillData.getCosts();
               if (targetLevel >= 0 && targetLevel < costs.size()) {
                  Integer cost = costs.get(targetLevel);
                  return cost != null ? cost : Integer.MAX_VALUE;
               }
            }

            return Integer.MAX_VALUE;
         }
      } else {
         return Integer.MAX_VALUE;
      }
   }

   private boolean isMasterOnlyFirstFormLevel(String formType, int targetLevel) {
      if (targetLevel == 0 && formType != null && this.statsData != null) {
         String lower = formType.toLowerCase(Locale.ROOT);
         if (ConfigManager.getSkillsConfig().getStackSkills().contains(lower)) {
            return false;
         } else {
            RaceCharacterConfig raceConfig = ConfigManager.getRaceCharacter(this.statsData.getCharacter().getRaceName());
            return raceConfig != null && raceConfig.isFormSkillBuyFromMaster(lower);
         }
      } else {
         return false;
      }
   }

   private List<Component> getQuestsGrantingForm(String formType, int requiredLevel) {
      List<Component> refs = new ArrayList<>();
      if (formType != null && !formType.isEmpty()) {
         for (Saga saga : QuestRegistry.getClientSagas().values()) {
            for (Quest quest : saga.getQuests()) {
               if (this.questGrantsFormAtLevel(quest, formType, requiredLevel)) {
                  Component ref = this.tr("gui.dragonminez.skills.quest_ref", new Object[]{this.tr(saga.getName(), new Object[0]), quest.getId()});
                  if (refs.stream().noneMatch(r -> r.getString().equals(ref.getString()))) {
                     refs.add(ref);
                  }
               }
            }
         }

         for (Quest questx : QuestRegistry.getClientQuests().values()) {
            if (questx.getType() != Quest.QuestType.SAGA
               && questx.getTitle() != null
               && !questx.getTitle().isEmpty()
               && this.questGrantsFormAtLevel(questx, formType, requiredLevel)) {
               Component ref = this.tr(questx.getTitle(), new Object[0]);
               if (refs.stream().noneMatch(r -> r.getString().equals(ref.getString()))) {
                  refs.add(ref);
               }
            }
         }

         return refs;
      } else {
         return refs;
      }
   }

   private boolean questGrantsFormAtLevel(Quest quest, String formType, int requiredLevel) {
      for (QuestReward reward : quest.getRewards()) {
         if (reward instanceof SkillReward skillReward && formType.equalsIgnoreCase(skillReward.getSkill()) && skillReward.getLevel() == requiredLevel) {
            return true;
         }
      }

      return false;
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      if (this.isNotAnimating()) {
         this.renderBackground(graphics, mouseX, mouseY, partialTick);
      }

      int uiMouseX = (int)Math.round(this.toUiX((double)mouseX));
      int uiMouseY = (int)Math.round(this.toUiY((double)mouseY));
      this.beginUiScale(graphics);
      this.applyZoom(graphics, partialTick);
      float step = Math.max(0.01F, 0.07F + partialTick * 0.01F);
      this.formsTransitionProgress = this.approach01(
         this.formsTransitionProgress, this.currentCategory == SkillsMenuScreen.SkillCategory.FORMS ? 1.0F : 0.0F, step
      );
      int baseLeftOffset = this.getLeftPanelSwitchOffset(partialTick);
      boolean nearLeftEdge = uiMouseX <= 36;
      boolean overLeftPanel = uiMouseX >= this.currentLeftX
         && uiMouseX < this.currentLeftX + 141 + 42
         && uiMouseY >= this.getUiHeight() / 2 - 105
         && uiMouseY < this.getUiHeight() / 2 + 108;
      float leftTarget = !nearLeftEdge && !overLeftPanel ? 0.0F : 1.0F;
      this.leftPanelHoverProgress = this.approach01(this.leftPanelHoverProgress, leftTarget, step);
      int hiddenTravel = 117;
      int extraLeftOffset = (int)(
         (float)(-hiddenTravel) * (1.0F - this.easeInOutCubic(this.leftPanelHoverProgress)) * this.easeInOutCubic(this.formsTransitionProgress)
      );
      this.currentLeftX = 12 + baseLeftOffset + extraLeftOffset;
      int rightOffset = this.getRightPanelSwitchOffset(partialTick);
      int extraRightOffset = (int)(200.0F * this.easeInOutCubic(this.formsTransitionProgress));
      this.currentRightX = this.getUiWidth() - 158 + rightOffset + extraRightOffset;
      this.updateButtonAnimations(uiMouseX, uiMouseY, partialTick);
      if (this.formsTransitionProgress > 0.0F) {
         RenderSystem.enableBlend();
         this.renderFormsTree(graphics, uiMouseX, uiMouseY);
         RenderSystem.disableBlend();
      }

      float currentModelX = Mth.lerp(this.easeInOutCubic(this.formsTransitionProgress), (float)(this.getUiWidth() / 2 + 5), (float)(this.getUiWidth() - 80));
      this.renderPlayerModel(
         graphics, (int)currentModelX, this.getUiHeight() / 2 + 70, 75, (float)uiMouseX, (float)uiMouseY, this.formsTransitionProgress > 0.5F
      );
      this.renderLeftPanel(graphics, this.currentLeftX, this.getUiHeight() / 2 - 105, uiMouseX, uiMouseY);
      if (this.formsTransitionProgress < 1.0F) {
         this.renderRightPanel(graphics, this.currentRightX, this.getUiHeight() / 2 - 105, uiMouseX, uiMouseY);
      }

      super.render(graphics, uiMouseX, uiMouseY, partialTick);
      this.endUiScale(graphics);
   }

   private void renderFormsTree(GuiGraphics graphics, int mouseX, int mouseY) {
      int alpha = (int)(this.formsTransitionProgress * 255.0F);
      int gridColor = alpha << 24 | 2236996;
      int spacing = 20;
      int offsetX = (int)this.formsPanX % spacing;
      int offsetY = (int)this.formsPanY % spacing;

      for (int x = offsetX - spacing; x < this.getUiWidth(); x += spacing) {
         graphics.fill(x, 0, x + 1, this.getUiHeight(), gridColor);
      }

      for (int y = offsetY - spacing; y < this.getUiHeight(); y += spacing) {
         graphics.fill(0, y, this.getUiWidth(), y + 1, gridColor);
      }

      graphics.pose().pushPose();
      graphics.pose().translate((float)this.getUiWidth() / 2.0F + this.formsPanX, (float)this.getUiHeight() / 2.0F + this.formsPanY, 0.0F);

      for (int i = 0; i < this.formNodes.size() - 1; i++) {
         SkillsMenuScreen.FormNode current = this.formNodes.get(i);
         SkillsMenuScreen.FormNode next = this.formNodes.get(i + 1);
         if (current.group.equals(next.group)) {
            int cx = (int)((float)(current.x + 16) * this.formsZoom);
            int cy = (int)((float)(current.y + 16) * this.formsZoom);
            int nx = (int)((float)(next.x + 16) * this.formsZoom);
            int ny = (int)((float)(next.y + 16) * this.formsZoom);
            this.drawThickLine(graphics, cx, cy, nx, ny, Math.max(1, (int)(3.0F * this.formsZoom)), alpha << 24 | 5592405);
         }
      }

      SkillsMenuScreen.FormNode hovered = null;

      for (SkillsMenuScreen.FormNode node : this.formNodes) {
         int nx = (int)((float)node.x * this.formsZoom);
         int ny = (int)((float)node.y * this.formsZoom);
         int size = (int)(32.0F * this.formsZoom);
         int screenNx = (int)((float)this.getUiWidth() / 2.0F + this.formsPanX + (float)nx);
         int screenNy = (int)((float)this.getUiHeight() / 2.0F + this.formsPanY + (float)ny);
         boolean isHovered = mouseX >= screenNx && mouseX <= screenNx + size && mouseY >= screenNy && mouseY <= screenNy + size;
         if (isHovered) {
            hovered = node;
         }

         Skill skill = this.statsData.getSkills().getSkill(node.formType);
         int currentLevel = skill != null ? skill.getLevel() : 0;
         int requiredLevel = node.data.getUnlockOnSkillLevel();
         boolean unlocked = skill != null && skill.isUnlockedAt(requiredLevel);
         boolean canPurchaseLevel = requiredLevel == currentLevel + 1;
         boolean selected = node.group.equalsIgnoreCase(this.selectedFormGroup) && node.data.getName().equalsIgnoreCase(this.selectedFormName);
         int borderColor = selected ? alpha << 24 | 16776960 : (unlocked ? alpha << 24 | 43520 : alpha << 24 | 3355443);
         int bgColor = alpha << 24 | 1118481;
         graphics.fill(nx - 2, ny - 2, nx + size + 2, ny + size + 2, borderColor);
         graphics.fill(nx, ny, nx + size, ny + size, bgColor);
         ResourceLocation icon = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/icons/" + node.formType.toLowerCase(Locale.ROOT) + ".png");
         if (unlocked) {
            float[] rgb = node.data.getRgbAuraColor();
            if (rgb != null && rgb.length >= 3) {
               RenderSystem.setShaderColor(rgb[0], rgb[1], rgb[2], this.formsTransitionProgress);
            } else {
               RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, this.formsTransitionProgress);
            }
         } else {
            RenderSystem.setShaderColor(0.4F, 0.4F, 0.4F, this.formsTransitionProgress);
         }

         graphics.blit(
            icon,
            nx + (int)(4.0F * this.formsZoom),
            ny + (int)(4.0F * this.formsZoom),
            0.0F,
            0.0F,
            (int)(24.0F * this.formsZoom),
            (int)(24.0F * this.formsZoom),
            (int)(24.0F * this.formsZoom),
            (int)(24.0F * this.formsZoom)
         );
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         int targetLevel = Math.max(0, requiredLevel - 1);
         int cost = this.getUpgradeCostForTargetLevel(node.formType, targetLevel);
         boolean isStack = ConfigManager.getSkillsConfig().getStackSkills().contains(node.formType.toLowerCase(Locale.ROOT));
         boolean isFirstStackLevel = isStack && targetLevel == 0;
         boolean isMasterOnly = this.isMasterOnlyFirstFormLevel(node.formType, targetLevel);
         if (!unlocked
            && canPurchaseLevel
            && !isFirstStackLevel
            && !isMasterOnly
            && cost != -1
            && cost != Integer.MAX_VALUE
            && this.statsData.getResources().getTrainingPoints() >= (float)cost) {
            int exX = nx + size - (int)(6.0F * this.formsZoom);
            int exY = ny - (int)(10.0F * this.formsZoom);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, this.formsTransitionProgress);
            graphics.blit(EXCLAMATION_MARK, exX, exY, (int)(6.0F * this.formsZoom), (int)(15.0F * this.formsZoom), 0.0F, 0.0F, 97, 250, 97, 250);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         }
      }

      graphics.pose().popPose();
      if (hovered != null && this.formsTransitionProgress > 0.8F) {
         List<Component> lines = new ArrayList<>();
         String race = this.statsData.getCharacter().getRaceName().toLowerCase(Locale.ROOT);
         boolean isStack = ConfigManager.getSkillsConfig().getStackSkills().contains(hovered.formType.toLowerCase(Locale.ROOT));
         if (isStack) {
            lines.add(
               Component.translatable("race.dragonminez.stack.group." + hovered.group)
                  .withStyle(new ChatFormatting[]{ChatFormatting.BLUE, ChatFormatting.BOLD})
            );
            lines.add(Component.translatable("race.dragonminez.stack.form." + hovered.group + "." + hovered.data.getName()).withStyle(ChatFormatting.AQUA));
         } else {
            lines.add(
               Component.translatable("race.dragonminez." + race + ".group." + hovered.group)
                  .withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD})
            );
            lines.add(
               Component.translatable("race.dragonminez." + race + ".form." + hovered.group + "." + hovered.data.getName()).withStyle(ChatFormatting.YELLOW)
            );
         }

         Skill skillx = this.statsData.getSkills().getSkill(hovered.formType);
         int currentLevelx = skillx != null ? skillx.getLevel() : 0;
         int requiredLevelx = hovered.data.getUnlockOnSkillLevel();
         boolean unlockedx = skillx != null && skillx.isUnlockedAt(requiredLevelx);
         boolean canPurchaseLevelx = requiredLevelx == currentLevelx + 1;
         lines.add(
            Component.translatable("skill.dragonminez." + hovered.formType)
               .withStyle(ChatFormatting.GRAY)
               .append(": " + requiredLevelx)
               .withStyle(ChatFormatting.GRAY)
         );
         int targetLevel = Math.max(0, requiredLevelx - 1);
         int cost = this.getUpgradeCostForTargetLevel(hovered.formType, targetLevel);
         boolean isFirstStackLevel = isStack && targetLevel == 0;
         boolean isMasterOnly = this.isMasterOnlyFirstFormLevel(hovered.formType, targetLevel);
         if (unlockedx) {
            lines.add(Component.translatable("gui.dragonminez.skills.purchased").withStyle(ChatFormatting.GREEN));
         } else if (cost != -1 && cost != Integer.MAX_VALUE) {
            lines.add(Component.translatable("gui.dragonminez.quests.rewards.tps", new Object[]{cost}).withStyle(ChatFormatting.AQUA));
            if (isMasterOnly) {
               lines.add(
                  Component.translatable("gui.dragonminez.skills.unlocked_by_master")
                     .withStyle(new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC})
               );
            } else if (canPurchaseLevelx && !isFirstStackLevel && this.statsData.getResources().getTrainingPoints() >= (float)cost) {
               lines.add(
                  Component.translatable("gui.dragonminez.skills.doubleclick_buy")
                     .withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.ITALIC})
               );
            }
         } else {
            List<Component> questTitles = this.getQuestsGrantingForm(hovered.formType, requiredLevelx);
            if (questTitles.isEmpty()) {
               lines.add(Component.translatable("gui.dragonminez.skills.priceless").withStyle(ChatFormatting.DARK_RED));
            } else {
               lines.add(Component.translatable("gui.dragonminez.skills.unlocked_by_quest").withStyle(ChatFormatting.LIGHT_PURPLE));

               for (Component title : questTitles) {
                  lines.add(title.copy().withStyle(ChatFormatting.GRAY));
               }
            }
         }

         TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), null, lines, null, 16777215);
      }
   }

   private void drawThickLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int thickness, int color) {
      int half = Math.max(0, thickness / 2);
      if (y1 == y2) {
         int left = Math.min(x1, x2);
         int right = Math.max(x1, x2);
         graphics.fill(left - half, y1 - half, right + half + 1, y1 + half + 1, color);
      } else if (x1 == x2) {
         int top = Math.min(y1, y2);
         int bottom = Math.max(y1, y2);
         graphics.fill(x1 - half, top - half, x1 + half + 1, bottom + half + 1, color);
      } else {
         int dx = Math.abs(x2 - x1);
         int dy = Math.abs(y2 - y1);
         int steps = Math.max(1, Math.max(dx, dy));

         for (int i = 0; i <= steps; i++) {
            float t = (float)i / (float)steps;
            int x = Math.round((float)x1 + (float)(x2 - x1) * t);
            int y = Math.round((float)y1 + (float)(y2 - y1) * t);
            graphics.fill(x - half, y - half, x + half + 1, y + half + 1, color);
         }
      }
   }

   private SkillsMenuScreen.FormNode getHoveredFormNode(double uiMouseX, double uiMouseY) {
      for (SkillsMenuScreen.FormNode node : this.formNodes) {
         int nx = (int)((float)node.x * this.formsZoom);
         int ny = (int)((float)node.y * this.formsZoom);
         int size = (int)(32.0F * this.formsZoom);
         int screenNx = (int)((float)this.getUiWidth() / 2.0F + this.formsPanX + (float)nx);
         int screenNy = (int)((float)this.getUiHeight() / 2.0F + this.formsPanY + (float)ny);
         if (uiMouseX >= (double)screenNx && uiMouseX <= (double)(screenNx + size) && uiMouseY >= (double)screenNy && uiMouseY <= (double)(screenNy + size)) {
            return node;
         }
      }

      return null;
   }

   private void updateButtonAnimations(int mouseX, int mouseY, float partialTick) {
      int centerY = this.getUiHeight() / 2;
      int leftPanelY = centerY - 105;
      int hiddenX = this.currentLeftX + 122;
      int visibleX = this.currentLeftX + 141;
      int buttonWidth = 26;
      int panelWidth = 141;
      int panelHeight = 213;
      int hotZoneY = leftPanelY + 6;
      int hotZoneWidth = visibleX - hiddenX + buttonWidth;
      int hotZoneHeight = 133;
      boolean overPanel = mouseX >= this.currentLeftX && mouseX < this.currentLeftX + panelWidth && mouseY >= leftPanelY && mouseY < leftPanelY + panelHeight;
      boolean overHotZone = mouseX >= hiddenX && mouseX < hiddenX + hotZoneWidth && mouseY >= hotZoneY && mouseY < hotZoneY + hotZoneHeight;
      boolean shouldReveal = overPanel || overHotZone;
      float step = Math.max(0.01F, 0.07F + partialTick * 0.01F);
      this.buttonRevealProgress = this.approach01(this.buttonRevealProgress, shouldReveal ? 1.0F : 0.0F, step);
      float animProgress = this.easeInOutCubic(this.buttonRevealProgress);
      int newX = hiddenX + (int)((float)(visibleX - hiddenX) * animProgress);
      this.skillsButton.setX(newX);
      this.kiButton.setX(newX);
      this.formsButton.setX(newX);
      this.stacksButton.setX(newX);
      int scissorXScreen = this.toScreenCoord((double)(this.currentLeftX + 141));
      int scissorYScreen = this.toScreenCoord(0.0);
      int scissorRight = this.toScreenCoord((double)this.getUiWidth());
      int scissorBottom = this.toScreenCoord((double)this.getUiHeight());
      this.skillsButton.setScissorRect(scissorXScreen, scissorYScreen, scissorRight, scissorBottom);
      this.kiButton.setScissorRect(scissorXScreen, scissorYScreen, scissorRight, scissorBottom);
      this.formsButton.setScissorRect(scissorXScreen, scissorYScreen, scissorRight, scissorBottom);
      this.stacksButton.setScissorRect(scissorXScreen, scissorYScreen, scissorRight, scissorBottom);
   }

   private float approach01(float current, float target, float step) {
      if (current < target) {
         return Math.min(target, current + step);
      } else {
         return current > target ? Math.max(target, current - step) : current;
      }
   }

   private float easeInOutCubic(float t) {
      if (t <= 0.0F) {
         return 0.0F;
      } else if (t >= 1.0F) {
         return 1.0F;
      } else {
         return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float)Math.pow((double)(-2.0F * t + 2.0F), 3.0) / 2.0F;
      }
   }

   private void renderLeftPanel(GuiGraphics graphics, int panelX, int panelY, int mouseX, int mouseY) {
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      graphics.blit(MENU_BIG, panelX, panelY, 0.0F, 0.0F, 141, 213, 256, 256);
      graphics.blit(MENU_BIG, panelX + 17, panelY + 10, 142.0F, 22.0F, 107, 21, 256, 256);
      this.renderSkillsList(graphics, panelX, panelY, mouseX, mouseY);
   }

   private void renderSkillsList(GuiGraphics graphics, int panelX, int panelY, int mouseX, int mouseY) {
      List<String> skillNames = this.getVisibleSkillNames();
      int startY = panelY + 30;
      int viewHeight = 160;
      int totalHeight = skillNames.size() * 20;
      this.maxScroll = (float)Math.max(0, totalHeight - viewHeight);
      this.targetScroll = Mth.clamp(this.targetScroll, 0.0F, this.maxScroll);
      float tickDelta = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
      this.currentScroll = Mth.lerp(tickDelta * 0.4F, this.currentScroll, this.targetScroll);
      graphics.enableScissor(
         this.toScreenCoord((double)(panelX + 5)),
         this.toScreenCoord((double)startY),
         this.toScreenCoord((double)(panelX + 179)),
         this.toScreenCoord((double)(startY + viewHeight))
      );
      graphics.pose().pushPose();
      graphics.pose().translate(0.0F, -this.currentScroll, 0.0F);

      for (int i = 0; i < skillNames.size(); i++) {
         String skillName = skillNames.get(i);
         int itemY = startY + i * 20;
         if ((float)(itemY + 20) >= (float)startY + this.currentScroll && (float)itemY <= (float)(startY + viewHeight) + this.currentScroll) {
            boolean isSelected = skillName.equals(this.selectedSkill);
            boolean isHovered = mouseX >= panelX + 10
               && mouseX <= panelX + 100
               && (float)mouseY >= (float)itemY - this.currentScroll
               && (float)mouseY <= (float)(itemY + 20) - this.currentScroll;
            int color = isSelected ? -22016 : (isHovered ? -5592406 : -1);
            Skill skill = this.statsData.getSkills().getSkill(skillName);
            String displayName;
            if (this.currentCategory == SkillsMenuScreen.SkillCategory.KI || this.currentCategory == SkillsMenuScreen.SkillCategory.STRIKE) {
               displayName = this.getDisplayNameForEntry(skillName);
            } else if ("__class_passive__".equals(skillName)) {
               displayName = this.getClassPassiveTitle();
            } else {
               displayName = this.tr("skill.dragonminez." + skillName, new Object[0]).getString();
            }

            TextUtil.drawStringWithBorder(graphics, this.font, this.txt(displayName), panelX + 15, itemY + 5, color);
            if (this.currentCategory != SkillsMenuScreen.SkillCategory.KI && this.currentCategory != SkillsMenuScreen.SkillCategory.STRIKE) {
               if (skill != null) {
                  String levelText = String.valueOf(skill.getLevel());
                  int levelX = panelX + 130 - TextUtil.width(this.font, levelText, DMZ_FONT);
                  TextUtil.drawStringWithBorder(graphics, this.font, this.txt(levelText), levelX, itemY + 5, color);
               }
            } else {
               TechniqueData technique = "__new_skill__".equals(skillName) ? null : this.statsData.getTechniques().getUnlockedTechniques().get(skillName);
               if (technique != null) {
                  String xpText = String.valueOf(technique.getExperience());
                  int xpX = panelX + 130 - TextUtil.width(this.font, xpText, DMZ_FONT);
                  TextUtil.drawStringWithBorder(graphics, this.font, this.txt(xpText), xpX, itemY + 5, color);
               }
            }
         }
      }

      graphics.pose().popPose();
      graphics.disableScissor();
      if (this.maxScroll > 0.0F) {
         int scrollBarX = panelX + 135;
         graphics.fill(scrollBarX, startY, scrollBarX + 3, startY + viewHeight, -13421773);
         float scrollPercent = this.currentScroll / this.maxScroll;
         float visiblePercent = (float)viewHeight / (float)totalHeight;
         int indicatorHeight = Math.max(20, (int)((float)viewHeight * visiblePercent));
         int indicatorY = startY + (int)((float)(viewHeight - indicatorHeight) * scrollPercent);
         graphics.fill(scrollBarX, indicatorY, scrollBarX + 3, indicatorY + indicatorHeight, -5592406);
      }

      String title = "";
      switch (this.currentCategory) {
         case SKILLS:
            title = "gui.dragonminez.skills.tab.skills";
            break;
         case KI:
            title = "gui.dragonminez.skills.tab.kiattacks";
            break;
         case FORMS:
            title = "gui.dragonminez.skills.tab.forms";
            break;
         case STRIKE:
            title = "gui.dragonminez.skills.tab.strikeattacks";
      }

      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.tr(title, new Object[0]).withStyle(style -> style.withBold(true)), panelX + 68, this.getUiHeight() / 2 - 88, 16499996
      );
   }

   private void renderRightPanel(GuiGraphics graphics, int panelX, int panelY, int mouseX, int mouseY) {
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      if (this.currentCategory != SkillsMenuScreen.SkillCategory.KI && this.currentCategory != SkillsMenuScreen.SkillCategory.STRIKE) {
         graphics.blit(MENU_SMALL, panelX, panelY, 0.0F, 0.0F, 141, 94, 256, 256);
         graphics.blit(MENU_BIG, panelX + 17, panelY + 10, 142.0F, 22.0F, 107, 21, 256, 256);
         graphics.blit(MENU_SMALL, panelX, panelY + 96, 0.0F, 0.0F, 141, 94, 256, 256);
         graphics.blit(MENU_SMALL, panelX, panelY + 190, 0.0F, 154.0F, 141, 32, 256, 256);
      } else {
         graphics.blit(MENU_BIG, panelX, panelY, 0.0F, 0.0F, 141, 213, 256, 256);
         graphics.blit(MENU_BIG, panelX + 17, panelY + 10, 142.0F, 22.0F, 107, 21, 256, 256);
      }

      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.character_stats.info", new Object[0]).withStyle(style -> style.withBold(true)),
         panelX + 70,
         panelY + 16,
         -10496
      );
      if (this.selectedSkill != null && this.statsData != null) {
         if (this.currentCategory == SkillsMenuScreen.SkillCategory.KI && "__new_skill__".equals(this.selectedSkill)) {
            this.renderNewSkillPlaceholder(graphics, panelX, panelY);
         } else if (this.currentCategory != SkillsMenuScreen.SkillCategory.KI && this.currentCategory != SkillsMenuScreen.SkillCategory.STRIKE) {
            this.renderSkillDetails(graphics, panelX, panelY);
         } else {
            this.renderTechniqueDetails(graphics, panelX, panelY, mouseX, mouseY);
         }
      }
   }

   private void renderNewSkillPlaceholder(GuiGraphics graphics, int panelX, int panelY) {
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.skills.new_skill", new Object[0]).withStyle(ChatFormatting.BOLD), panelX + 70, panelY + 48, -1
      );
   }

   private void renderTechniqueDetails(GuiGraphics graphics, int panelX, int panelY, int mouseX, int mouseY) {
      TechniqueData tech = this.statsData.getTechniques().getUnlockedTechniques().get(this.selectedSkill);
      if (tech != null) {
         int yOffset = panelY + 40;
         int xpReq = 100;
         int cooldownTicks = tech.getCooldown();
         TextUtil.drawCenteredStringWithBorder(
            graphics, this.font, this.tr(tech.getName(), new Object[0]).withStyle(ChatFormatting.BOLD), panelX + 70, yOffset, -1
         );
         yOffset += 12;
         TextUtil.drawCenteredStringWithBorder(
            graphics, this.font, this.tr("gui.dragonminez.technique.xp", new Object[]{tech.getExperience()}), panelX + 70, yOffset, -11141291
         );
         yOffset += 16;
         if (tech instanceof KiAttackData ki) {
            xpReq = ki.getUpgradeXpCost("damage");
            cooldownTicks = ki.getActualCooldown();
            int scaledKiDamage = (int)(
               this.statsData.getKiDamage()
                  * (double)ki.getActualDamageMultiplier()
                  * (double)ki.getConfiguredDamageMultiplier()
                  * (double)ki.getOutputMultiplier()
            );
            TextUtil.drawStringWithBorder(
               graphics,
               this.font,
               this.tr("gui.dragonminez.technique.type", new Object[0])
                  .append(": ")
                  .append(this.tr("technique.type." + ki.getKiType().name().toLowerCase(), new Object[0])),
               panelX + 15,
               yOffset,
               14540253
            );
            yOffset += 12;
            String utilKey = ki.getEffectiveUtility() == KiAttackData.Utility.HEAL ? "gui.dragonminez.technique.heal" : "gui.dragonminez.technique.damage";
            TextUtil.drawStringWithBorder(
               graphics,
               this.font,
               this.tr(utilKey, new Object[0]).append(": ").append(this.txt(String.valueOf(scaledKiDamage))),
               panelX + 15,
               yOffset,
               16777215
            );
            yOffset += 12;
            TextUtil.drawStringWithBorder(
               graphics,
               this.font,
               this.tr("gui.dragonminez.technique.size", new Object[0]).append(": ").append(this.txt(String.format(Locale.US, "%.1f", ki.getSize()))),
               panelX + 15,
               yOffset,
               16777215
            );
            yOffset += 12;
            TextUtil.drawStringWithBorder(
               graphics,
               this.font,
               this.tr("gui.dragonminez.technique.speed", new Object[0]).append(": ").append(this.txt(String.format(Locale.US, "%.1f", ki.getSpeed()))),
               panelX + 15,
               yOffset,
               16777215
            );
            yOffset += 12;
            TextUtil.drawStringWithBorder(
               graphics,
               this.font,
               this.tr("gui.dragonminez.technique.armor_pen", new Object[0]).append(": ").append(this.txt(String.valueOf(ki.getArmorPenetration()))),
               panelX + 15,
               yOffset,
               16777215
            );
            yOffset += 12;
         } else if (tech instanceof StrikeAttackData st) {
            cooldownTicks = st.getActualCooldown();
            int scaledStrikeDamage = (int)(this.statsData.getStrikeDamage() * (double)st.getDamageMultiplier());
            TextUtil.drawStringWithBorder(
               graphics,
               this.font,
               this.tr("gui.dragonminez.technique.type", new Object[0]).append(": ").append(this.tr("technique.type.strike", new Object[0])),
               panelX + 15,
               yOffset,
               14540253
            );
            yOffset += 12;
            TextUtil.drawStringWithBorder(
               graphics,
               this.font,
               this.tr("gui.dragonminez.technique.damage", new Object[0]).append(": ").append(this.txt(String.valueOf(scaledStrikeDamage))),
               panelX + 15,
               yOffset,
               16777215
            );
            yOffset += 12;
         }

         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.tr("gui.dragonminez.technique.cooldown", new Object[0])
               .append(": ")
               .append(this.txt(String.format(Locale.US, "%.1fs", (float)cooldownTicks / 20.0F))),
            panelX + 15,
            yOffset,
            16777215
         );
         yOffset += 16;
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.tr("gui.dragonminez.technique.energy_cost", new Object[0])
               .append(": ")
               .append(this.txt(String.format(Locale.US, "%.1f", tech.getCalculatedCost(this.statsData)))),
            panelX + 15,
            yOffset,
            16755370
         );
         yOffset += 16;
         TextUtil.drawStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.technique.req_xp", new Object[]{xpReq}), panelX + 15, yOffset, -5592406);
         this.renderActionStatus(graphics, panelX, panelY);
      }
   }

   private void attemptTechniqueImport() {
      if (this.statsData != null && this.techniqueImportBox != null) {
         String code = this.techniqueImportBox.getValue() != null ? this.techniqueImportBox.getValue().trim() : "";
         if (code.isEmpty()) {
            this.setActionStatus(this.tr("gui.dragonminez.skills.status.invalid_code", new Object[0]), 16733525);
         } else {
            NetworkHandler.INSTANCE.sendToServer(new ImportTechniqueC2S(code));
         }
      }
   }

   private void setActionStatus(Component text, int color) {
      this.actionStatusText = text;
      this.actionStatusColor = color;
      this.actionStatusTimer = 60;
   }

   private void renderActionStatus(GuiGraphics graphics, int panelX, int panelY) {
      if (this.actionStatusTimer > 0) {
         int alpha = (int)Math.max(0.0F, Math.min(255.0F, (float)this.actionStatusTimer / 60.0F * 255.0F));
         if (alpha > 0) {
            int colorWithAlpha = alpha << 24 | this.actionStatusColor & 16777215;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            int yPos = panelY + 208;
            TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.actionStatusText, panelX + 70, yPos, colorWithAlpha);
            RenderSystem.disableBlend();
         }
      }
   }

   private void consumePendingImportStatus() {
      if (pendingImportStatusText != null) {
         this.setActionStatus(pendingImportStatusText, pendingImportStatusColor);
         pendingImportStatusText = null;
         if (pendingImportSuccess) {
            pendingImportSuccess = false;
            this.isImportingTechnique = false;
            this.techniqueImportBox = null;
            this.refreshButtons();
         }
      }
   }

   public static void handleTechniqueImportResult(TechniqueImportResultS2C.Status status, int value) {
      switch (status) {
         case INVALID:
            pendingImportStatusText = Component.translatable("gui.dragonminez.skills.status.invalid_code");
            pendingImportStatusColor = 16733525;
            break;
         case NOT_ENOUGH_TP:
            pendingImportStatusText = Component.translatable("gui.dragonminez.skills.status.not_enough_tp", new Object[]{value});
            pendingImportStatusColor = 16733525;
            break;
         case IMPORTED:
            pendingImportStatusText = Component.translatable("gui.dragonminez.skills.status.imported_used_tp", new Object[]{value});
            pendingImportStatusColor = 5635925;
            pendingImportSuccess = true;
      }
   }

   private void renderSkillDetails(GuiGraphics graphics, int panelX, int panelY) {
      Skill skill = this.statsData.getSkills().getSkill(this.selectedSkill);
      boolean isClassPassive = this.selectedSkill.equals("__class_passive__");
      if (skill != null || this.selectedSkill.startsWith("racial_") || isClassPassive) {
         GeneralServerConfig.RacialSkillsConfig config = ConfigManager.getServerConfig().getRacialSkills();
         String displayName = isClassPassive ? this.getClassPassiveTitle() : this.tr("skill.dragonminez." + this.selectedSkill, new Object[0]).getString();
         String description = "";
         if (isClassPassive) {
            description = this.tr("class.dragonminez." + this.statsData.getCharacter().getCharacterClass() + ".passive.desc", new Object[0]).getString();
         } else if (this.selectedSkill.startsWith("racial_")) {
            String startY = this.selectedSkill;
            switch (startY) {
               case "racial_human":
                  int regen = (int)Math.round((config.getHumanKiRegenBoost() - 1.0) * 100.0);
                  description = this.tr("skill.dragonminez.racial_human.desc", new Object[]{regen}).getString();
                  break;
               case "racial_saiyan": {
                  int zenkaiHealth = (int)Math.round(config.getSaiyanZenkaiHealthRegen() * 100.0);
                  int zenkaiStat = (int)Math.round(config.getSaiyanZenkaiStatBoost() * 100.0);
                  int cooldown = config.getSaiyanZenkaiCooldownSeconds();
                  int maxUses = config.getSaiyanZenkaiAmount();
                  int minLevel = config.getSaiyanZenkaiMinLevel();
                  description = this.tr("skill.dragonminez.racial_saiyan.desc", new Object[]{zenkaiHealth, zenkaiStat, cooldown, maxUses, minLevel})
                     .getString();
                  break;
               }
               case "racial_namekian": {
                  int assimHealth = (int)Math.round(config.getNamekianAssimilationHealthRegen() * 100.0);
                  int assimStat = (int)Math.round(config.getNamekianAssimilationStatBoost() * 100.0);
                  int maxUses = config.getNamekianAssimilationAmount();
                  description = this.tr("skill.dragonminez.racial_namekian.desc", new Object[]{assimHealth, assimStat, maxUses}).getString();
                  break;
               }
               case "racial_frostdemon":
                  int tpBoost = (int)Math.round((config.getFrostDemonTPBoost() - 1.0) * 100.0);
                  description = this.tr("skill.dragonminez.racial_frostdemon.desc", new Object[]{tpBoost}).getString();
                  break;
               case "racial_bioandroid": {
                  int drainRatio = (int)Math.round(config.getBioAndroidDrainRatio() * 100.0);
                  int cooldown = config.getBioAndroidCooldownSeconds();
                  description = this.tr("skill.dragonminez.racial_bioandroid.desc", new Object[]{drainRatio, cooldown}).getString();
                  break;
               }
               case "racial_majin": {
                  int absHealth = (int)Math.round(config.getMajinAbsorptionHealthRegen() * 100.0);
                  int absStat = (int)Math.round(config.getMajinAbsorptionStatCopy() * 100.0);
                  int maxUses = config.getMajinAbsorptionAmount();
                  description = this.tr("skill.dragonminez.racial_majin.desc", new Object[]{absHealth, absStat, maxUses}).getString();
               }
            }
         } else {
            description = this.tr("skill.dragonminez." + this.selectedSkill + ".desc", new Object[0]).getString();
         }

         int startY = panelY + 40;
         TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(displayName).withStyle(ChatFormatting.BOLD), panelX + 72, startY, -1);
         if (skill != null) {
            TextUtil.drawCenteredStringWithBorder(
               graphics,
               this.font,
               this.tr("gui.dragonminez.skills.level", new Object[]{skill.getLevel(), skill.getMaxLevel()}),
               panelX + 72,
               startY + 12,
               -5592406
            );
            int upgradeCost = this.getUpgradeCost(this.selectedSkill, skill.getLevel());
            if (upgradeCost != Integer.MAX_VALUE && upgradeCost > 0) {
               TextUtil.drawCenteredStringWithBorder(
                  graphics,
                  this.font,
                  this.txt("%d TPS".formatted(this.getUpgradeCost(this.selectedSkill, skill.getLevel()))),
                  panelX + 72,
                  startY + 24,
                  -5592406
               );
            }
         } else if (isClassPassive) {
            TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("class.dragonminez.passive", new Object[0]), panelX + 72, startY + 12, -11776);
         } else {
            TextUtil.drawCenteredStringWithBorder(
               graphics, this.font, this.tr("gui.dragonminez.skills.racial", new Object[0]), panelX + 72, startY + 12, -11141291
            );
         }

         List<String> wrappedDesc = this.wrapText(description, 120);
         int descY = startY + 70;
         int boxX = panelX + 13;
         int boxW = 130;
         int lineHeight = 9 + 2;
         int viewHeight = 6 * lineHeight;
         int totalContentHeight = wrappedDesc.size() * lineHeight;
         this.maxDescScroll = (float)Math.max(0, totalContentHeight - viewHeight);
         this.targetDescScroll = Mth.clamp(this.targetDescScroll, 0.0F, this.maxDescScroll);
         float tickDelta = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
         this.currentDescScroll = Mth.lerp(tickDelta * 0.4F, this.currentDescScroll, this.targetDescScroll);
         TextUtil.renderScrollableText(graphics, this.font, wrappedDesc, boxX, descY, boxW, viewHeight, this.currentDescScroll, this.maxDescScroll, -3355444);
      }
   }

   private List<String> wrapText(String text, int maxWidth) {
      return TextUtil.wrap(this.font, text, maxWidth, DMZ_FONT);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      double uiMouseX = this.toUiX(mouseX);
      double uiMouseY = this.toUiY(mouseY);
      int centerY = this.getUiHeight() / 2;
      int leftPanelY = centerY - 105;
      int scrollAmount = (int)Math.signum(scrollY);
      if (uiMouseX >= (double)this.currentLeftX
         && uiMouseX <= (double)(this.currentLeftX + 141)
         && uiMouseY >= (double)leftPanelY
         && uiMouseY <= (double)(leftPanelY + 213)) {
         this.targetScroll = Mth.clamp(this.targetScroll - (float)(scrollAmount * 20 * 2), 0.0F, this.maxScroll);
         return true;
      } else {
         if (this.formsTransitionProgress < 1.0F) {
            int descBoxX = this.currentRightX + 10;
            int descBoxY = centerY - 105 + 110;
            int descBoxW = 136;
            int descBoxH = 72;
            if (uiMouseX >= (double)descBoxX
               && uiMouseX <= (double)(descBoxX + descBoxW)
               && uiMouseY >= (double)descBoxY
               && uiMouseY <= (double)(descBoxY + descBoxH)) {
               this.targetDescScroll = Mth.clamp(this.targetDescScroll - (float)(scrollAmount * 12 * 2), 0.0F, this.maxDescScroll);
               return true;
            }

            if (uiMouseX >= (double)this.currentRightX
               && uiMouseX <= (double)(this.currentRightX + 141)
               && uiMouseY >= (double)leftPanelY
               && uiMouseY <= (double)(leftPanelY + 213)) {
               return true;
            }
         }

         if (this.currentCategory == SkillsMenuScreen.SkillCategory.FORMS) {
            this.formsZoom = Math.max(0.25F, Math.min(2.0F, this.formsZoom + (float)scrollAmount * 0.1F));
            return true;
         } else {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
         }
      }
   }

   private float calculateScrollPercent(double uiMouseY, int startY, int scrollBarHeight) {
      float percent = (float)(uiMouseY - (double)startY) / (float)scrollBarHeight;
      return Mth.clamp(percent, 0.0F, 1.0F);
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      return this.techniqueImportBox == null
            || !this.techniqueImportBox.isFocused()
            || keyCode == 256
            || !this.techniqueImportBox.keyPressed(keyCode, scanCode, modifiers) && !this.techniqueImportBox.canConsumeInput()
         ? super.keyPressed(keyCode, scanCode, modifiers)
         : true;
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (super.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else {
         double uiMouseX = this.toUiX(mouseX);
         double uiMouseY = this.toUiY(mouseY);
         int centerY = this.getUiHeight() / 2;
         int leftPanelY = centerY - 105;
         int startY = leftPanelY + 30;
         int scrollBarHeight = 160;
         int scrollBarX = this.currentLeftX + 135;
         if (this.maxScroll > 0.0F
            && uiMouseX >= (double)(scrollBarX - 5)
            && uiMouseX <= (double)(scrollBarX + 10)
            && uiMouseY >= (double)startY
            && uiMouseY <= (double)(startY + scrollBarHeight)) {
            this.isDraggingMainScroll = true;
            this.targetScroll = this.calculateScrollPercent(uiMouseY, startY, scrollBarHeight) * this.maxScroll;
            return true;
         } else {
            List<String> skillNames = this.getVisibleSkillNames();
            if (uiMouseX >= (double)(this.currentLeftX + 10)
               && uiMouseX <= (double)(this.currentLeftX + 100)
               && uiMouseY >= (double)startY
               && uiMouseY <= (double)(startY + scrollBarHeight)) {
               int index = (int)((uiMouseY - (double)startY + (double)this.currentScroll) / 20.0);
               if (index >= 0 && index < skillNames.size()) {
                  this.selectedSkill = skillNames.get(index);
                  if (this.currentCategory == SkillsMenuScreen.SkillCategory.FORMS) {
                     this.selectedFormName = this.selectedSkill;

                     for (SkillsMenuScreen.FormNode fn : this.formNodes) {
                        if (fn.data.getName().equals(this.selectedFormName)) {
                           this.selectedFormGroup = fn.group;
                           this.formsPanX = (float)(-fn.x);
                           this.formsPanY = (float)(-fn.y);
                           break;
                        }
                     }
                  } else {
                     this.targetDescScroll = 0.0F;
                     this.currentDescScroll = 0.0F;
                  }

                  this.refreshButtons();
                  return true;
               }
            }

            if (uiMouseX >= (double)this.currentLeftX
               && uiMouseX <= (double)(this.currentLeftX + 141)
               && uiMouseY >= (double)leftPanelY
               && uiMouseY <= (double)(leftPanelY + 213)) {
               return true;
            } else {
               if (this.formsTransitionProgress < 1.0F) {
                  int descBoxY = centerY - 105 + 110;
                  int descBoxH = 72;
                  int descScrollBarX = this.currentRightX + 140;
                  if (this.maxDescScroll > 0.0F
                     && uiMouseX >= (double)(descScrollBarX - 5)
                     && uiMouseX <= (double)(descScrollBarX + 10)
                     && uiMouseY >= (double)descBoxY
                     && uiMouseY <= (double)(descBoxY + descBoxH)) {
                     this.isDraggingDescScroll = true;
                     this.targetDescScroll = this.calculateScrollPercent(uiMouseY, descBoxY, descBoxH) * this.maxDescScroll;
                     return true;
                  }

                  if (uiMouseX >= (double)this.currentRightX
                     && uiMouseX <= (double)(this.currentRightX + 141)
                     && uiMouseY >= (double)leftPanelY
                     && uiMouseY <= (double)(leftPanelY + 213)) {
                     return true;
                  }
               }

               if (this.currentCategory == SkillsMenuScreen.SkillCategory.FORMS && button == 0) {
                  SkillsMenuScreen.FormNode clicked = this.getHoveredFormNode(uiMouseX, uiMouseY);
                  if (clicked == null) {
                     this.isDraggingForms = true;
                     this.dragFormStartX = uiMouseX;
                     this.dragFormStartY = uiMouseY;
                     this.dragFormStartPanX = this.formsPanX;
                     this.dragFormStartPanY = this.formsPanY;
                     return true;
                  } else {
                     long now = System.currentTimeMillis();
                     boolean sameSelection = clicked.group.equalsIgnoreCase(this.selectedFormGroup)
                        && clicked.data.getName().equalsIgnoreCase(this.selectedFormName);
                     if (sameSelection && now - this.lastFormClickTime < 500L) {
                        Skill skill = this.statsData.getSkills().getSkill(clicked.formType);
                        int currentLevel = skill != null ? skill.getLevel() : 0;
                        int requiredLevel = clicked.data.getUnlockOnSkillLevel();
                        boolean canPurchaseLevel = requiredLevel == currentLevel + 1;
                        int targetLevel = Math.max(0, requiredLevel - 1);
                        int cost = this.getUpgradeCostForTargetLevel(clicked.formType, targetLevel);
                        boolean isStack = ConfigManager.getSkillsConfig().getStackSkills().contains(clicked.formType.toLowerCase(Locale.ROOT));
                        boolean isFirstStackLevel = isStack && targetLevel == 0;
                        if (canPurchaseLevel
                           && !isFirstStackLevel
                           && !this.isMasterOnlyFirstFormLevel(clicked.formType, targetLevel)
                           && cost != -1
                           && cost != Integer.MAX_VALUE
                           && this.statsData.getResources().getTrainingPoints() >= (float)cost) {
                           NetworkHandler.INSTANCE.sendToServer(new UpdateSkillC2S(UpdateSkillC2S.SkillAction.UPGRADE, clicked.formType, cost));
                           this.updateStatsData();
                        }
                     } else {
                        this.selectedFormName = clicked.data.getName();
                        this.selectedFormGroup = clicked.group;
                        this.selectedSkill = this.selectedFormName;
                        this.refreshButtons();
                     }

                     this.lastFormClickTime = now;
                     return true;
                  }
               } else {
                  return false;
               }
            }
         }
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      double uiMouseY = this.toUiY(mouseY);
      double uiMouseX = this.toUiX(mouseX);
      if (this.isDraggingForms && button == 0) {
         this.formsPanX = this.dragFormStartPanX + (float)(uiMouseX - this.dragFormStartX);
         this.formsPanY = this.dragFormStartPanY + (float)(uiMouseY - this.dragFormStartY);
         return true;
      } else if (this.isDraggingMainScroll && this.maxScroll > 0.0F) {
         int centerY = this.getUiHeight() / 2;
         int startY = centerY - 105 + 30;
         int scrollBarHeight = 160;
         this.targetScroll = this.calculateScrollPercent(uiMouseY, startY, scrollBarHeight) * this.maxScroll;
         return true;
      } else if (this.isDraggingDescScroll && this.maxDescScroll > 0.0F) {
         int centerY = this.getUiHeight() / 2;
         int descBoxY = centerY - 105 + 110;
         int descBoxH = 72;
         this.targetDescScroll = this.calculateScrollPercent(uiMouseY, descBoxY, descBoxH) * this.maxDescScroll;
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.isDraggingForms) {
         this.isDraggingForms = false;
         return true;
      } else if (!this.isDraggingMainScroll && !this.isDraggingDescScroll) {
         return super.mouseReleased(mouseX, mouseY, button);
      } else {
         this.isDraggingMainScroll = false;
         this.isDraggingDescScroll = false;
         return true;
      }
   }

   private String getDisplayNameForEntry(String entryId) {
      if ("__new_skill__".equals(entryId)) {
         return this.tr("gui.dragonminez.skills.new_skill", new Object[0]).getString();
      } else if (this.statsData == null) {
         return entryId;
      } else {
         TechniqueData technique = this.statsData.getTechniques().getUnlockedTechniques().get(entryId);
         if (technique != null && technique.getName() != null && !technique.getName().isEmpty()) {
            String rawName = technique.getName();
            return rawName.contains(".") ? this.tr(rawName, new Object[0]).getString() : rawName;
         } else {
            return entryId;
         }
      }
   }

   private String getClassPassiveTitle() {
      String characterClass = this.statsData.getCharacter().getCharacterClass();
      String rawTitle = this.tr("class.dragonminez." + characterClass, new Object[0]).getString()
         + " "
         + this.tr("class.dragonminez.passive", new Object[0]).getString();
      return ChatFormatting.stripFormatting(rawTitle);
   }

   private void openTechniqueCreator() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(new TechniqueCreatorScreen(this));
      }
   }

   private void renderPlayerModel(GuiGraphics graphics, int x, int y, int scale, float mouseX, float mouseY, boolean isFormPreview) {
      LivingEntity player = Minecraft.getInstance().player;
      if (player != null) {
         String activeFormGroupO = null;
         String activeFormO = null;
         String activeStackFormGroupO = null;
         String activeStackFormO = null;
         Character character = null;
         Status status = null;
         boolean androidUpgradedO = false;
         boolean androidUpgradedOverridden = false;
         if (isFormPreview && this.selectedFormName != null && this.selectedFormGroup != null) {
            StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
            if (stats != null) {
               character = stats.getCharacter();
               activeFormGroupO = character.getActiveFormGroup();
               activeFormO = character.getActiveForm();
               activeStackFormGroupO = character.getActiveStackFormGroup();
               activeStackFormO = character.getActiveStackForm();
               character.clearActiveForm();
               character.clearActiveStackForm();
               if (ConfigManager.getStackFormGroup(this.selectedFormGroup) != null) {
                  character.setActiveStackForm(this.selectedFormGroup, this.selectedFormName);
               } else {
                  character.setActiveForm(this.selectedFormGroup, this.selectedFormName);
               }

               status = stats.getStatus();
               androidUpgradedO = status.isAndroidUpgraded();
               if ("androidforms".equals(this.selectedFormGroup) && !androidUpgradedO) {
                  status.setAndroidUpgraded(true);
                  androidUpgradedOverridden = true;
               }
            }
         }

         int adjustedScale = this.getAdjustedModelScale(scale);
         float xRotation = (float)Math.atan((double)((float)y - mouseY) / 40.0);
         float yRotation = (float)Math.atan((double)((float)x - mouseX) / 40.0);
         Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
         Quaternionf cameraOrientation = new Quaternionf().rotateX(xRotation * 20.0F * (float) (Math.PI / 180.0));
         pose.mul(cameraOrientation);
         float yBodyRotO = player.yBodyRot;
         float yRotO = player.getYRot();
         float xRotO = player.getXRot();
         float yHeadRotO = player.yHeadRotO;
         float yHeadRot = player.yHeadRot;
         player.yBodyRot = 180.0F + yRotation * 20.0F;
         player.setYRot(180.0F + yRotation * 40.0F);
         player.setXRot(-xRotation * 20.0F);
         player.yHeadRot = player.getYRot();
         player.yHeadRotO = player.getYRot();
         graphics.pose().pushPose();
         graphics.pose().translate(0.0, 0.0, 150.0);
         DMZSkinLayer.PREVIEW_MODE = character != null;

         try {
            EntityPreviewRenderContext.renderEntityInInventory(graphics, x, y, adjustedScale, new Vector3f(0.0F, 0.0F, 0.0F), pose, cameraOrientation, player);
         } finally {
            DMZSkinLayer.PREVIEW_MODE = false;
            graphics.pose().popPose();
            player.yBodyRot = yBodyRotO;
            player.setYRot(yRotO);
            player.setXRot(xRotO);
            player.yHeadRotO = yHeadRotO;
            player.yHeadRot = yHeadRot;
            if (character != null) {
               character.clearActiveForm();
               character.clearActiveStackForm();
               character.setActiveForm(activeFormGroupO, activeFormO);
               character.setActiveStackForm(activeStackFormGroupO, activeStackFormO);
               if (androidUpgradedOverridden) {
                  status.setAndroidUpgraded(androidUpgradedO);
               }
            }
         }
      }
   }

   private static class FormNode {
      String group;
      String formType;
      FormConfig.FormData data;
      int x;
      int y;

      FormNode(String group, String formType, FormConfig.FormData data, int x, int y) {
         this.group = group;
         this.formType = formType;
         this.data = data;
         this.x = x;
         this.y = y;
      }
   }

   private static enum SkillCategory {
      SKILLS,
      KI,
      FORMS,
      STRIKE;
   }
}
