package com.dragonminez.client.gui;

import com.dragonminez.client.gui.buttons.ClippableTextureButton;
import com.dragonminez.client.gui.buttons.TexturedTextButton;
import com.dragonminez.client.gui.character.util.BaseMenuScreen;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.RaceCharacterConfig;
import com.dragonminez.common.config.SkillsConfig;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.UpdateSkillC2S;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.skills.Skill;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class MastersSkillsScreen extends BaseMenuScreen {
   private static final ResourceLocation MENU_BIG = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menubig.png");
   private static final ResourceLocation MENU_SMALL = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menusmall.png");
   private static final ResourceLocation BUTTONS_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/characterbuttons.png");
   private static final ResourceLocation DMZ_FONT = ResourceLocation.fromNamespaceAndPath("dragonminez", "smooth");
   private static final int SKILL_ITEM_HEIGHT = 20;
   private static final int MAX_VISIBLE_SKILLS = 8;
   private MastersSkillsScreen.SkillCategory currentCategory = MastersSkillsScreen.SkillCategory.SKILLS;
   private StatsData statsData;
   private int tickCount = 0;
   private String selectedSkill = null;
   private float targetScroll = 0.0F;
   private float currentScroll = 0.0F;
   private float maxScroll = 0.0F;
   private float targetDescScroll = 0.0F;
   private float currentDescScroll = 0.0F;
   private float maxDescScroll = 0.0F;
   private boolean isDraggingDescScroll = false;
   private final Map<MastersSkillsScreen.SkillCategory, ClippableTextureButton> categoryButtons = new LinkedHashMap<>();
   private final List<MastersSkillsScreen.SkillCategory> activeCategories = new ArrayList<>();
   private float buttonRevealProgress = 0.0F;
   private TexturedTextButton purchaseButton;
   private boolean isDraggingScroll = false;
   private final String masterName;
   private final LivingEntity masterEntity;

   public MastersSkillsScreen(String masterName, LivingEntity masterEntity) {
      super(Component.literal(masterName).withStyle(Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath("dragonminez", "smooth"))));
      this.masterName = masterName;
      this.masterEntity = masterEntity;
   }

   @Override
   protected void init() {
      super.init();
      this.updateStatsData();
      this.initDynamicButtons();
   }

   @Override
   protected void initNavigationButtons() {
   }

   @Override
   public void tick() {
      super.tick();
      this.tickCount++;
      if (this.tickCount >= 10) {
         this.tickCount = 0;
         this.updateStatsData();
         this.refreshButtons();
      }
   }

   private void updateStatsData() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> this.statsData = data);
      }
   }

   private int textureUForCategory(MastersSkillsScreen.SkillCategory category) {
      switch (category) {
         case SKILLS:
            return 142;
         case KI:
            return 170;
         case FORMS:
            return 198;
         case STRIKE:
            return 226;
         default:
            return 142;
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
      this.activeCategories.clear();

      for (MastersSkillsScreen.SkillCategory category : new MastersSkillsScreen.SkillCategory[]{
         MastersSkillsScreen.SkillCategory.SKILLS,
         MastersSkillsScreen.SkillCategory.KI,
         MastersSkillsScreen.SkillCategory.FORMS,
         MastersSkillsScreen.SkillCategory.STRIKE
      }) {
         if (!this.getSkillsForCategory(category).isEmpty()) {
            this.activeCategories.add(category);
         }
      }

      if (!this.activeCategories.isEmpty() && !this.activeCategories.contains(this.currentCategory)) {
         this.currentCategory = this.activeCategories.get(0);
      }

      this.categoryButtons.clear();
      int index = 0;

      for (MastersSkillsScreen.SkillCategory categoryx : this.activeCategories) {
         int u = this.textureUForCategory(categoryx);
         ClippableTextureButton button = new ClippableTextureButton.Builder()
            .position(hiddenX, buttonY + index * 32)
            .size(26, 32)
            .texture(MENU_BIG)
            .textureCoords(u, 44, u, 44)
            .clipping(true, scissorXScreen, scissorYScreen, scissorRight, scissorBottom)
            .onPress(btn -> {
               this.currentCategory = category;
               this.selectedSkill = null;
               this.targetScroll = 0.0F;
               this.currentScroll = 0.0F;
               this.refreshButtons();
            })
            .build();
         this.categoryButtons.put(categoryx, button);
         this.addRenderableWidget(button);
         index++;
      }
   }

   private List<String> getMasterSkills() {
      Map<String, List<String>> skillOfferings = ConfigManager.getSkillsConfig().getSkillOfferings();
      return skillOfferings.getOrDefault(this.masterName.toLowerCase(), skillOfferings.get("default"));
   }

   private List<String> getVisibleSkillNames() {
      return this.getSkillsForCategory(this.currentCategory);
   }

   private List<String> getSkillsForCategory(MastersSkillsScreen.SkillCategory category) {
      if (this.statsData == null) {
         return new ArrayList<>();
      } else {
         List<String> masterOfferings = this.getMasterSkills();
         if (masterOfferings == null) {
            return new ArrayList<>();
         } else {
            List<String> visibleSkills = new ArrayList<>();
            SkillsConfig skillsConfig = ConfigManager.getSkillsConfig();
            String playerRace = this.getPlayerRaceName();

            for (String skillId : masterOfferings) {
               boolean isKi = skillsConfig.getKiSkills().contains(skillId);
               boolean isFormSkill = skillsConfig.getFormSkills().contains(skillId);
               boolean isStackSkill = skillsConfig.getStackSkills().contains(skillId);
               boolean isStrike = skillsConfig.getStrikeSkills().contains(skillId);
               boolean isForm = isFormSkill || isStackSkill;
               if (isFormSkill) {
                  RaceCharacterConfig raceConfig = ConfigManager.getRaceCharacter(playerRace);
                  if (raceConfig == null || !raceConfig.hasFormSkill(skillId)) {
                     continue;
                  }
               } else if (!skillsConfig.isSkillAllowedForRace(skillId, playerRace)) {
                  continue;
               }

               switch (category) {
                  case SKILLS:
                     if (!isKi && !isForm && !isStrike) {
                        visibleSkills.add(skillId);
                     }
                     break;
                  case KI:
                     if (isKi) {
                        visibleSkills.add(skillId);
                     }
                     break;
                  case FORMS:
                     if (isForm) {
                        visibleSkills.add(skillId);
                     }
                     break;
                  case STRIKE:
                     if (isStrike) {
                        visibleSkills.add(skillId);
                     }
               }
            }

            return visibleSkills;
         }
      }
   }

   private String getPlayerRaceName() {
      if (this.statsData != null && this.statsData.getCharacter() != null) {
         String raceName = this.statsData.getCharacter().getRaceName();
         return raceName != null ? raceName.toLowerCase(Locale.ROOT) : "";
      } else {
         return "";
      }
   }

   private void refreshButtons() {
      this.clearWidgets();
      this.initDynamicButtons();
      this.initPurchaseButton();
   }

   private void initPurchaseButton() {
      if (this.selectedSkill != null && this.statsData != null) {
         if (ConfigManager.getSkillsConfig().isSkillAllowedForRace(this.selectedSkill, this.getPlayerRaceName())) {
            Skill skill = this.statsData.getSkills().getSkill(this.selectedSkill);
            if (skill == null) {
               skill = new Skill(this.selectedSkill, 0, false, 10);
            }

            int rightPanelX = this.getUiWidth() - 158;
            int centerY = this.getUiHeight() / 2;
            int rightPanelY = centerY - 105;
            if (!this.statsData.getSkills().hasSkill(this.selectedSkill) || skill.getLevel() == 0) {
               int cost = this.getUpgradeCost(this.selectedSkill, 0);
               float currentTPS = this.statsData.getResources().getTrainingPoints();
               boolean canAfford = currentTPS >= (float)cost;
               if (cost == -1 || cost == Integer.MAX_VALUE) {
                  return;
               }

               this.purchaseButton = new TexturedTextButton.Builder()
                  .position(rightPanelX + 35, rightPanelY + 196)
                  .size(74, 20)
                  .texture(BUTTONS_TEXTURE)
                  .textureCoords(0, 28, 0, 48)
                  .textureSize(74, 20)
                  .message(this.tr("gui.dragonminez.skills.purchase"))
                  .onPress(btn -> {
                     if (canAfford) {
                        NetworkHandler.INSTANCE.sendToServer(new UpdateSkillC2S(UpdateSkillC2S.SkillAction.PURCHASE, this.selectedSkill, cost));
                        this.updateStatsData();
                     }
                  })
                  .build();
               this.purchaseButton.active = canAfford;
               this.addRenderableWidget(this.purchaseButton);
            }
         }
      }
   }

   private int getUpgradeCost(String skillName, int currentLevel) {
      SkillsConfig skillConfig = ConfigManager.getSkillsConfig();
      if (skillConfig.getFormSkills().contains(skillName.toLowerCase())) {
         RaceCharacterConfig raceConfig = ConfigManager.getRaceCharacter(this.getPlayerRaceName());
         if (raceConfig != null) {
            Integer[] costs = raceConfig.getFormSkillTpCosts(skillName);
            if (costs != null && currentLevel >= 0 && currentLevel < costs.length) {
               return costs[currentLevel] != null ? costs[currentLevel] : Integer.MAX_VALUE;
            }
         }

         return Integer.MAX_VALUE;
      } else {
         SkillsConfig.SkillCosts skillData = skillConfig.getSkills().get(skillName);
         if (skillData != null && skillData.getCosts() != null) {
            List<Integer> costs = skillData.getCosts();
            if (currentLevel < costs.size()) {
               return costs.get(currentLevel) != null ? costs.get(currentLevel) : Integer.MAX_VALUE;
            }
         }

         return Integer.MAX_VALUE;
      }
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
      this.updateButtonAnimations(uiMouseX, uiMouseY, partialTick);
      this.renderMasterEntity(graphics, this.getUiWidth() / 2 + 5, this.getUiHeight() / 2 + 90, (float)uiMouseX, (float)uiMouseY);
      this.renderLeftPanel(graphics, uiMouseX, uiMouseY);
      this.renderRightPanel(graphics, uiMouseX, uiMouseY);
      super.render(graphics, uiMouseX, uiMouseY, partialTick);
      this.endUiScale(graphics);
   }

   private void updateButtonAnimations(int mouseX, int mouseY, float partialTick) {
      int leftPanelX = 12;
      int centerY = this.getUiHeight() / 2;
      int leftPanelY = centerY - 105;
      int hiddenX = leftPanelX + 122;
      int visibleX = leftPanelX + 141;
      int buttonWidth = 26;
      int panelWidth = 141;
      int panelHeight = 213;
      int hotZoneY = leftPanelY + 6;
      int hotZoneWidth = visibleX - hiddenX + buttonWidth;
      int hotZoneHeight = Math.max(1, this.categoryButtons.size()) * 32;
      boolean overPanel = mouseX >= leftPanelX && mouseX < leftPanelX + panelWidth && mouseY >= leftPanelY && mouseY < leftPanelY + panelHeight;
      boolean overHotZone = mouseX >= hiddenX && mouseX < hiddenX + hotZoneWidth && mouseY >= hotZoneY && mouseY < hotZoneY + hotZoneHeight;
      boolean shouldReveal = overPanel || overHotZone;
      float step = Math.max(0.01F, 0.07F + partialTick * 0.01F);
      this.buttonRevealProgress = this.approach01(this.buttonRevealProgress, shouldReveal ? 1.0F : 0.0F, step);
      float animProgress = this.easeInOutCubic(this.buttonRevealProgress);
      int newX = hiddenX + (int)((float)(visibleX - hiddenX) * animProgress);
      int scissorX = leftPanelX + 141;
      int scissorXScreen = this.toScreenCoord((double)scissorX);
      int scissorYScreen = this.toScreenCoord(0.0);
      int scissorRight = this.toScreenCoord((double)this.getUiWidth());
      int scissorBottom = this.toScreenCoord((double)this.getUiHeight());

      for (ClippableTextureButton button : this.categoryButtons.values()) {
         button.setX(newX);
         button.setScissorRect(scissorXScreen, scissorYScreen, scissorRight, scissorBottom);
      }
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

   private void renderLeftPanel(GuiGraphics graphics, int mouseX, int mouseY) {
      int leftPanelX = 12;
      int centerY = this.getUiHeight() / 2;
      int leftPanelY = centerY - 105;
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      graphics.blit(MENU_BIG, leftPanelX, leftPanelY, 0.0F, 0.0F, 141, 213, 256, 256);
      graphics.blit(MENU_BIG, 29, centerY - 95, 142.0F, 22.0F, 107, 21, 256, 256);
      this.renderSkillsList(graphics, leftPanelX, leftPanelY, mouseX, mouseY);
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
            if (this.currentCategory != MastersSkillsScreen.SkillCategory.KI && this.currentCategory != MastersSkillsScreen.SkillCategory.STRIKE) {
               displayName = Component.translatable("skill.dragonminez." + skillName).getString();
            } else {
               displayName = Component.translatable("technique.dragonminez." + skillName).getString();
            }

            TextUtil.drawStringWithBorder(graphics, this.font, this.txt(displayName), panelX + 15, itemY + 5, color);
            String levelText;
            if (skill != null && skill.getLevel() > 0) {
               levelText = String.valueOf(skill.getLevel());
            } else {
               levelText = "0";
            }

            int levelX = panelX + 130 - TextUtil.width(this.font, levelText, DMZ_FONT);
            TextUtil.drawStringWithBorder(graphics, this.font, this.txt(levelText), levelX, itemY + 5, color);
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
         graphics, this.font, this.tr(title).withStyle(style -> style.withBold(true)), 80, this.getUiHeight() / 2 - 88, 16499996
      );
   }

   private void renderRightPanel(GuiGraphics graphics, int mouseX, int mouseY) {
      int rightPanelX = this.getUiWidth() - 158;
      int centerY = this.getUiHeight() / 2;
      int rightPanelY = centerY - 105;
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      if (this.currentCategory != MastersSkillsScreen.SkillCategory.KI && this.currentCategory != MastersSkillsScreen.SkillCategory.STRIKE) {
         graphics.blit(MENU_SMALL, rightPanelX, rightPanelY, 0.0F, 0.0F, 141, 94, 256, 256);
         graphics.blit(MENU_BIG, this.getUiWidth() - 141, centerY - 95, 142.0F, 22.0F, 107, 21, 256, 256);
         graphics.blit(MENU_SMALL, rightPanelX, rightPanelY + 96, 0.0F, 0.0F, 141, 94, 256, 256);
         graphics.blit(MENU_SMALL, rightPanelX, rightPanelY + 190, 0.0F, 154.0F, 141, 32, 256, 256);
      } else {
         graphics.blit(MENU_BIG, rightPanelX, rightPanelY, 0.0F, 0.0F, 141, 213, 256, 256);
         graphics.blit(MENU_BIG, this.getUiWidth() - 141, centerY - 95, 142.0F, 22.0F, 107, 21, 256, 256);
      }

      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.character_stats.info").withStyle(style -> style.withBold(true)),
         rightPanelX + 70,
         rightPanelY + 16,
         -10496
      );
      if (this.selectedSkill != null && this.statsData != null) {
         if (this.currentCategory == MastersSkillsScreen.SkillCategory.KI) {
            this.renderKiTechniqueDetails(graphics, rightPanelX, rightPanelY);
         } else if (this.currentCategory == MastersSkillsScreen.SkillCategory.STRIKE) {
            this.renderStrikeTechniqueDetails(graphics, rightPanelX, rightPanelY);
         } else {
            this.renderSkillDetails(graphics, rightPanelX, rightPanelY);
         }
      }
   }

   private void renderKiTechniqueDetails(GuiGraphics graphics, int panelX, int panelY) {
      KiAttackData tech = PredefinedTechniques.REGISTRY.get(this.selectedSkill);
      if (tech != null) {
         int yOffset = panelY + 40;
         TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr(tech.getName()).withStyle(ChatFormatting.BOLD), panelX + 70, yOffset, -1);
         yOffset += 24;
         int scaledKiDamage = (int)(this.statsData.getKiDamage() * (double)tech.getDamageMultiplier() * (double)tech.getOutputMultiplier());
         String utilKey = tech.getUtility() == KiAttackData.Utility.HEAL ? "gui.dragonminez.technique.heal" : "gui.dragonminez.technique.damage";
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.tr("gui.dragonminez.technique.type").append(": ").append(this.tr("technique.type." + tech.getKiType().name().toLowerCase())),
            panelX + 15,
            yOffset,
            14540253
         );
         yOffset += 12;
         TextUtil.drawStringWithBorder(
            graphics, this.font, this.tr(utilKey).append(": ").append(this.txt(String.valueOf(scaledKiDamage))), panelX + 15, yOffset, 16777215
         );
         yOffset += 12;
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.tr("gui.dragonminez.technique.size").append(": ").append(this.txt(String.format(Locale.US, "%.1f", tech.getSize()))),
            panelX + 15,
            yOffset,
            16777215
         );
         yOffset += 12;
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.tr("gui.dragonminez.technique.speed").append(": ").append(this.txt(String.format(Locale.US, "%.1f", tech.getSpeed()))),
            panelX + 15,
            yOffset,
            16777215
         );
         yOffset += 12;
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.tr("gui.dragonminez.technique.armor_pen").append(": ").append(this.txt(String.valueOf(tech.getArmorPenetration()))),
            panelX + 15,
            yOffset,
            16777215
         );
         yOffset += 12;
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.tr("gui.dragonminez.technique.cast_time")
               .append(": ")
               .append(this.txt(String.format(Locale.US, "%.1fs", (float)tech.getActualCastTime() / 20.0F))),
            panelX + 15,
            yOffset,
            16777215
         );
         yOffset += 12;
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.tr("gui.dragonminez.technique.cooldown")
               .append(": ")
               .append(this.txt(String.format(Locale.US, "%.1fs", (float)tech.getActualCooldown() / 20.0F))),
            panelX + 15,
            yOffset,
            16777215
         );
         yOffset += 16;
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.tr("gui.dragonminez.technique.energy_cost")
               .append(": ")
               .append(this.txt(String.format(Locale.US, "%.1f", tech.getCalculatedCost(this.statsData)))),
            panelX + 15,
            yOffset,
            16755370
         );
         yOffset += 16;
         this.renderLearnedFooter(graphics, panelX, yOffset);
      }
   }

   private void renderStrikeTechniqueDetails(GuiGraphics graphics, int panelX, int panelY) {
      StrikeAttackData tech = PredefinedTechniques.STRIKE_REGISTRY.get(this.selectedSkill);
      if (tech != null) {
         int yOffset = panelY + 40;
         TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr(tech.getName()).withStyle(ChatFormatting.BOLD), panelX + 70, yOffset, -1);
         yOffset += 24;
         int scaledStrikeDamage = (int)(this.statsData.getStrikeDamage() * (double)tech.getDamageMultiplier());
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.tr("gui.dragonminez.technique.type").append(": ").append(this.tr("technique.type.strike")),
            panelX + 15,
            yOffset,
            14540253
         );
         yOffset += 12;
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.tr("gui.dragonminez.technique.damage").append(": ").append(this.txt(String.valueOf(scaledStrikeDamage))),
            panelX + 15,
            yOffset,
            16777215
         );
         yOffset += 12;
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.tr("gui.dragonminez.technique.cast_time")
               .append(": ")
               .append(this.txt(String.format(Locale.US, "%.1fs", (float)tech.getActualCastTime() / 20.0F))),
            panelX + 15,
            yOffset,
            16777215
         );
         yOffset += 12;
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.tr("gui.dragonminez.technique.cooldown")
               .append(": ")
               .append(this.txt(String.format(Locale.US, "%.1fs", (float)tech.getActualCooldown() / 20.0F))),
            panelX + 15,
            yOffset,
            16777215
         );
         yOffset += 16;
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.tr("gui.dragonminez.technique.energy_cost")
               .append(": ")
               .append(this.txt(String.format(Locale.US, "%.1f", tech.getCalculatedCost(this.statsData)))),
            panelX + 15,
            yOffset,
            16755370
         );
         yOffset += 16;
         this.renderLearnedFooter(graphics, panelX, yOffset);
      }
   }

   private void renderLearnedFooter(GuiGraphics graphics, int panelX, int yOffset) {
      boolean learned = this.statsData.getTechniques().getUnlockedTechniques().containsKey(this.selectedSkill);
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         learned ? this.tr("gui.dragonminez.skills.already_learned") : this.tr("gui.dragonminez.skills.not_learned"),
         panelX + 70,
         yOffset,
         learned ? -11163051 : -5592406
      );
      if (!learned) {
         int tpCost = this.getUpgradeCost(this.selectedSkill, 0);
         if (tpCost != Integer.MAX_VALUE && tpCost != -1) {
            TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(tpCost + " TPS"), panelX + 70, yOffset + 12, -5592406);
         }
      }
   }

   private void renderSkillDetails(GuiGraphics graphics, int panelX, int panelY) {
      Skill skill = this.statsData.getSkills().getSkill(this.selectedSkill);
      if (skill == null) {
         skill = new Skill(this.selectedSkill, 0, false, 10);
      }

      String displayName = Component.translatable("skill.dragonminez." + this.selectedSkill).getString();
      String description = Component.translatable("skill.dragonminez." + this.selectedSkill + ".desc").getString();
      int startY = panelY + 40;
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(displayName).withStyle(ChatFormatting.BOLD), panelX + 72, startY, -1);
      Component levelComp;
      if (skill.getLevel() > 0) {
         levelComp = this.tr("gui.dragonminez.skills.level", skill.getLevel(), skill.getMaxLevel());
      } else {
         levelComp = this.tr("gui.dragonminez.skills.not_learned");
      }

      TextUtil.drawCenteredStringWithBorder(graphics, this.font, levelComp, panelX + 72, startY + 12, -5592406);
      if (skill.getLevel() == 0) {
         int cost = this.getUpgradeCost(this.selectedSkill, 0);
         if (cost != Integer.MAX_VALUE && cost != -1) {
            TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt("%d TPS".formatted(cost)), panelX + 72, startY + 24, -5592406);
         }
      } else {
         TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("gui.dragonminez.skills.already_learned"), panelX + 72, startY + 24, -11163051);
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

   private List<String> wrapText(String text, int maxWidth) {
      return TextUtil.wrap(this.font, text, maxWidth, DMZ_FONT);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      double uiMouseX = this.toUiX(mouseX);
      double uiMouseY = this.toUiY(mouseY);
      int leftPanelX = 12;
      int centerY = this.getUiHeight() / 2;
      int leftPanelY = centerY - 105;
      int scrollAmount = (int)Math.signum(scrollY);
      if (uiMouseX >= (double)leftPanelX
         && uiMouseX <= (double)(leftPanelX + 184)
         && uiMouseY >= (double)(leftPanelY + 40)
         && uiMouseY <= (double)(leftPanelY + 239)) {
         this.targetScroll = Mth.clamp(this.targetScroll - (float)(scrollAmount * 20 * 2), 0.0F, this.maxScroll);
         return true;
      } else {
         int rightPanelX = this.getUiWidth() - 158;
         int descBoxX = rightPanelX + 10;
         int descBoxY = leftPanelY + 110;
         int descBoxW = 136;
         int descBoxH = 72;
         if (uiMouseX >= (double)descBoxX
            && uiMouseX <= (double)(descBoxX + descBoxW)
            && uiMouseY >= (double)descBoxY
            && uiMouseY <= (double)(descBoxY + descBoxH)) {
            this.targetDescScroll = Mth.clamp(this.targetDescScroll - (float)(scrollAmount * 12 * 2), 0.0F, this.maxDescScroll);
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
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      double uiMouseX = this.toUiX(mouseX);
      double uiMouseY = this.toUiY(mouseY);
      int leftPanelX = 12;
      int centerY = this.getUiHeight() / 2;
      int leftPanelY = centerY - 105;
      int startY = leftPanelY + 30;
      int viewHeight = 160;
      int scrollBarX = leftPanelX + 135;
      if (this.maxScroll > 0.0F
         && uiMouseX >= (double)(scrollBarX - 5)
         && uiMouseX <= (double)(scrollBarX + 10)
         && uiMouseY >= (double)startY
         && uiMouseY <= (double)(startY + viewHeight)) {
         this.isDraggingScroll = true;
         this.targetScroll = this.calculateScrollPercent(uiMouseY, startY, viewHeight) * this.maxScroll;
         return true;
      } else {
         int rightPanelX = this.getUiWidth() - 158;
         int descBoxY = leftPanelY + 110;
         int descBoxH = 72;
         int descScrollBarX = rightPanelX + 140;
         if (this.maxDescScroll > 0.0F
            && uiMouseX >= (double)(descScrollBarX - 5)
            && uiMouseX <= (double)(descScrollBarX + 10)
            && uiMouseY >= (double)descBoxY
            && uiMouseY <= (double)(descBoxY + descBoxH)) {
            this.isDraggingDescScroll = true;
            this.targetDescScroll = this.calculateScrollPercent(uiMouseY, descBoxY, descBoxH) * this.maxDescScroll;
            return true;
         } else {
            List<String> skillNames = this.getVisibleSkillNames();
            if (uiMouseX >= (double)(leftPanelX + 10)
               && uiMouseX <= (double)(leftPanelX + 100)
               && uiMouseY >= (double)startY
               && uiMouseY <= (double)(startY + viewHeight)) {
               int index = (int)((uiMouseY - (double)startY + (double)this.currentScroll) / 20.0);
               if (index >= 0 && index < skillNames.size()) {
                  this.selectedSkill = skillNames.get(index);
                  this.targetDescScroll = 0.0F;
                  this.currentDescScroll = 0.0F;
                  this.refreshButtons();
                  return true;
               }
            }

            return super.mouseClicked(mouseX, mouseY, button);
         }
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.isDraggingScroll && this.maxScroll > 0.0F) {
         double uiMouseY = this.toUiY(mouseY);
         int centerY = this.getUiHeight() / 2;
         int startY = centerY - 105 + 30;
         int viewHeight = 160;
         this.targetScroll = this.calculateScrollPercent(uiMouseY, startY, viewHeight) * this.maxScroll;
         return true;
      } else if (this.isDraggingDescScroll && this.maxDescScroll > 0.0F) {
         double uiMouseY = this.toUiY(mouseY);
         int descBoxY = this.getUiHeight() / 2 - 105 + 110;
         int descBoxH = 72;
         this.targetDescScroll = this.calculateScrollPercent(uiMouseY, descBoxY, descBoxH) * this.maxDescScroll;
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.isDraggingScroll) {
         this.isDraggingScroll = false;
         return true;
      } else if (this.isDraggingDescScroll) {
         this.isDraggingDescScroll = false;
         return true;
      } else {
         return super.mouseReleased(mouseX, mouseY, button);
      }
   }

   private void renderMasterEntity(GuiGraphics graphics, int x, int y, float mouseX, float mouseY) {
      if (this.masterEntity != null) {
         float xRotation = (float)Math.atan((double)((float)y - mouseY) / 40.0);
         float yRotation = (float)Math.atan((double)((float)x - mouseX) / 40.0);
         Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
         Quaternionf cameraOrientation = new Quaternionf().rotateX(xRotation * 20.0F * (float) (Math.PI / 180.0));
         pose.mul(cameraOrientation);
         float yBodyRotO = this.masterEntity.yBodyRot;
         float yRotO = this.masterEntity.getYRot();
         float xRotO = this.masterEntity.getXRot();
         float yHeadRotO = this.masterEntity.yHeadRotO;
         float yHeadRot = this.masterEntity.yHeadRot;
         this.masterEntity.yBodyRot = 180.0F + yRotation * 20.0F;
         this.masterEntity.setYRot(180.0F + yRotation * 40.0F);
         this.masterEntity.setXRot(-xRotation * 20.0F);
         this.masterEntity.yHeadRot = this.masterEntity.getYRot();
         this.masterEntity.yHeadRotO = this.masterEntity.getYRot();
         graphics.pose().pushPose();
         graphics.pose().translate(0.0, 0.0, 150.0);
         InventoryScreen.renderEntityInInventory(
            graphics, (float)x, (float)y, 100.0F, new Vector3f(0.0F, 0.0F, 0.0F), pose, cameraOrientation, this.masterEntity
         );
         graphics.pose().popPose();
         this.masterEntity.yBodyRot = yBodyRotO;
         this.masterEntity.setYRot(yRotO);
         this.masterEntity.setXRot(xRotO);
         this.masterEntity.yHeadRotO = yHeadRotO;
         this.masterEntity.yHeadRot = yHeadRot;
      }
   }

   @Override
   public MutableComponent tr(String key, Object... args) {
      return Component.translatable(key, args).withStyle(Style.EMPTY.withFont(DMZ_FONT));
   }

   @Override
   public MutableComponent txt(String text) {
      return Component.literal(text).withStyle(Style.EMPTY.withFont(DMZ_FONT));
   }

   private static enum SkillCategory {
      SKILLS,
      KI,
      FORMS,
      STRIKE;
   }
}
