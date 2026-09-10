package com.dragonminez.client.gui.character;

import com.dragonminez.client.gui.buttons.CustomTextureButton;
import com.dragonminez.client.gui.buttons.TexturedTextButton;
import com.dragonminez.client.gui.character.util.BaseMenuScreen;
import com.dragonminez.client.render.EntityPreviewRenderContext;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class PartyMenuScreen extends BaseMenuScreen {
   private static final ResourceLocation MENU_BIG = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menubig.png");
   private static final ResourceLocation BUTTONS_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/characterbuttons.png");
   private static final int ITEM_HEIGHT = 16;
   private static final int MAX_VISIBLE_ITEMS = 10;
   private PartyMenuScreen.Tab currentTab = PartyMenuScreen.Tab.SERVER;
   private List<PartyMenuScreen.PartyEntry> displayList = new ArrayList<>();
   private int selectedIndex = -1;
   private float targetScroll = 0.0F;
   private float currentScroll = 0.0F;
   private float maxScroll = 0.0F;
   private boolean isDraggingScroll = false;
   private TexturedTextButton actionBtn;
   private CustomTextureButton prevBtn;
   private CustomTextureButton nextBtn;

   public PartyMenuScreen() {
      super(Component.translatable("gui.dragonminez.party.title"));
   }

   @Override
   protected void init() {
      super.init();
      this.refreshPlayerList();
      this.initActionButtons();
   }

   @Override
   public void tick() {
      super.tick();
      if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getGameTime() % 40L == 0L) {
         this.refreshPlayerList();
      }
   }

   private void refreshPlayerList() {
      if (Minecraft.getInstance().getConnection() != null && Minecraft.getInstance().player != null) {
         UUID selectedId = null;
         if (this.selectedIndex >= 0 && this.selectedIndex < this.displayList.size()) {
            selectedId = this.displayList.get(this.selectedIndex).id();
         }

         List<PlayerInfo> onlinePlayers = new ArrayList<>(Minecraft.getInstance().getConnection().getOnlinePlayers());
         UUID localId = Minecraft.getInstance().player.getUUID();
         this.displayList.clear();
         if (this.currentTab == PartyMenuScreen.Tab.SERVER) {
            onlinePlayers.sort((p1, p2) -> {
               boolean isP1Local = p1.getProfile().getId().equals(localId);
               boolean isP2Local = p2.getProfile().getId().equals(localId);
               if (isP1Local && !isP2Local) {
                  return -1;
               } else {
                  return !isP1Local && isP2Local ? 1 : p1.getProfile().getName().compareToIgnoreCase(p2.getProfile().getName());
               }
            });

            for (PlayerInfo p : onlinePlayers) {
               this.displayList.add(new PartyMenuScreen.PartyEntry(p.getProfile().getId(), p.getProfile().getName(), true, false));
            }
         } else {
            StatsProvider.get(StatsCapability.INSTANCE, Minecraft.getInstance().player).ifPresent(data -> {
               List<UUID> partyIds = data.getPlayerQuestData().getPartyMemberIds();
               UUID leaderId = data.getPlayerQuestData().getPartyLeaderId();
               if (partyIds == null || partyIds.isEmpty()) {
                  partyIds = List.of(localId);
                  leaderId = localId;
               }

               for (UUID memberId : partyIds) {
                  PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(memberId);
                  boolean isOnline = info != null;
                  String name = isOnline ? info.getProfile().getName() : "Offline (" + memberId.toString().substring(0, 4) + ")";
                  this.displayList.add(new PartyMenuScreen.PartyEntry(memberId, name, isOnline, memberId.equals(leaderId)));
               }

               this.displayList.sort((e1, e2) -> {
                  if (e1.equals(localId)) {
                     return -1;
                  } else {
                     return e2.equals(localId) ? 1 : e1.name().compareToIgnoreCase(e2.name());
                  }
               });
            });
         }

         this.selectedIndex = -1;
         if (selectedId != null) {
            for (int i = 0; i < this.displayList.size(); i++) {
               PartyMenuScreen.PartyEntry entry = this.displayList.get(i);
               if (entry.equals(selectedId)) {
                  if (entry.isOnline()) {
                     this.selectedIndex = i;
                  }
                  break;
               }
            }
         }

         if (this.actionBtn != null) {
            this.refreshActionButtons();
         }
      }
   }

   private void initActionButtons() {
      int rightPanelX = this.getUiWidth() - 158 + this.getRightPanelSwitchOffset(1.0F);
      int centerY = this.getUiHeight() / 2;
      int rightPanelY = centerY - 105;
      this.prevBtn = new CustomTextureButton.Builder()
         .position(rightPanelX + 20, rightPanelY + 183)
         .size(15, 20)
         .texture(BUTTONS_TEXTURE)
         .textureCoords(32, 0, 32, 14)
         .textureSize(8, 14)
         .onPress(btn -> this.shiftSelection(-1))
         .build();
      this.nextBtn = new CustomTextureButton.Builder()
         .position(rightPanelX + 116, rightPanelY + 183)
         .size(15, 20)
         .texture(BUTTONS_TEXTURE)
         .textureCoords(20, 0, 20, 14)
         .textureSize(8, 14)
         .onPress(btn -> this.shiftSelection(1))
         .build();
      this.actionBtn = new TexturedTextButton.Builder()
         .position(rightPanelX + 35, rightPanelY + 180)
         .size(74, 20)
         .texture(BUTTONS_TEXTURE)
         .textureCoords(0, 28, 0, 48)
         .textureSize(74, 20)
         .message(this.tr("gui.dragonminez.party.invite", new Object[0]))
         .onPress(btn -> this.executePlayerAction())
         .build();
      this.addRenderableWidget(this.prevBtn);
      this.addRenderableWidget(this.nextBtn);
      this.addRenderableWidget(this.actionBtn);
      this.refreshActionButtons();
   }

   private void updatePanelWidgetOffsets(int rightOffset) {
      int rightPanelX = this.getUiWidth() - 158 + rightOffset;
      if (this.prevBtn != null) {
         this.prevBtn.setX(rightPanelX + 20);
      }

      if (this.nextBtn != null) {
         this.nextBtn.setX(rightPanelX + 116);
      }

      if (this.actionBtn != null) {
         this.actionBtn.setX(rightPanelX + 35);
      }
   }

   private void shiftSelection(int direction) {
      if (!this.displayList.isEmpty()) {
         this.selectedIndex += direction;
         if (this.selectedIndex < 0) {
            this.selectedIndex = this.displayList.size() - 1;
         }

         if (this.selectedIndex >= this.displayList.size()) {
            this.selectedIndex = 0;
         }

         this.refreshActionButtons();
      }
   }

   private void refreshActionButtons() {
      boolean validSelection = this.selectedIndex >= 0 && this.selectedIndex < this.displayList.size();
      this.prevBtn.active = validSelection && this.displayList.size() > 1;
      this.nextBtn.active = validSelection && this.displayList.size() > 1;
      if (validSelection && Minecraft.getInstance().player != null) {
         PartyMenuScreen.PartyEntry targetEntry = this.displayList.get(this.selectedIndex);
         boolean isSelf = targetEntry.equals(Minecraft.getInstance().player.getUUID());
         if (this.currentTab == PartyMenuScreen.Tab.SERVER) {
            this.actionBtn.visible = !isSelf;
            this.actionBtn.active = !isSelf && targetEntry.isOnline();
            this.actionBtn.setMessage(this.tr("gui.dragonminez.party.invite", new Object[0]));
         } else {
            this.actionBtn.visible = true;
            this.actionBtn.active = true;
            if (isSelf) {
               this.actionBtn.setMessage(this.tr("gui.dragonminez.party.leave", new Object[0]));
            } else {
               this.actionBtn.setMessage(this.tr("gui.dragonminez.party.kick", new Object[0]));
            }
         }
      } else {
         this.actionBtn.visible = false;
         this.actionBtn.active = false;
      }
   }

   private void executePlayerAction() {
      if (this.selectedIndex >= 0 && this.selectedIndex < this.displayList.size() && Minecraft.getInstance().player != null) {
         PartyMenuScreen.PartyEntry target = this.displayList.get(this.selectedIndex);
         boolean isSelf = target.equals(Minecraft.getInstance().player.getUUID());
         String name = target.name();
         if (this.currentTab == PartyMenuScreen.Tab.SERVER) {
            if (!isSelf && target.isOnline()) {
               Minecraft.getInstance().player.connection.sendCommand("dmzparty invite " + name);
            }
         } else if (isSelf) {
            Minecraft.getInstance().player.connection.sendCommand("dmzparty leave");
         } else {
            Minecraft.getInstance().player.connection.sendCommand("dmzparty kick " + name);
         }
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
      int leftOffset = this.getLeftPanelSwitchOffset(partialTick);
      int rightOffset = this.getRightPanelSwitchOffset(partialTick);
      this.updatePanelWidgetOffsets(rightOffset);
      int leftPanelX = 12 + leftOffset;
      int rightPanelX = this.getUiWidth() - 158 + rightOffset;
      int centerY = this.getUiHeight() / 2;
      int panelY = centerY - 105;
      this.renderPanels(graphics, leftPanelX, rightPanelX, panelY);
      this.renderPlayerList(graphics, leftPanelX, panelY, uiMouseX, uiMouseY);
      this.renderRightPanelDetails(graphics, rightPanelX, panelY);
      this.renderCentralModel(graphics, this.getUiWidth() / 2 + 5, this.getUiHeight() / 2 + 70, 75, (float)uiMouseX, (float)uiMouseY);
      super.render(graphics, uiMouseX, uiMouseY, partialTick);
      this.endUiScale(graphics);
   }

   private void renderPanels(GuiGraphics graphics, int leftX, int rightX, int panelY) {
      RenderSystem.enableBlend();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      graphics.blit(MENU_BIG, leftX, panelY, 0.0F, 0.0F, 141, 213, 256, 256);
      graphics.blit(MENU_BIG, leftX + 17, panelY + 10, 142.0F, 22.0F, 107, 21, 256, 256);
      graphics.blit(MENU_BIG, rightX, panelY, 0.0F, 0.0F, 141, 213, 256, 256);
      graphics.blit(MENU_BIG, rightX + 17, panelY + 10, 142.0F, 22.0F, 107, 21, 256, 256);
      graphics.blit(MENU_BIG, rightX + 31, panelY + 77, 142.0F, 0.0F, 79, 21, 256, 256);
      RenderSystem.disableBlend();
   }

   private void renderPlayerList(GuiGraphics graphics, int panelX, int panelY, int mouseX, int mouseY) {
      boolean isTabHovered = mouseX >= panelX + 17 && mouseX <= panelX + 124 && mouseY >= panelY + 10 && mouseY <= panelY + 31;
      int tabColor = isTabHovered ? 16777215 : -10496;
      Component tabText = Component.literal("< ")
         .append(this.tr(this.currentTab == PartyMenuScreen.Tab.SERVER ? "gui.dragonminez.party.server" : "gui.dragonminez.party.party", new Object[0]))
         .append(" >");
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, tabText.copy().withStyle(ChatFormatting.BOLD), panelX + 70, panelY + 16, tabColor, 0);
      int startY = panelY + 35;
      int viewHeight = 160;
      int totalHeight = this.displayList.size() * 16;
      this.maxScroll = (float)Math.max(0, totalHeight - viewHeight);
      this.targetScroll = Mth.clamp(this.targetScroll, 0.0F, this.maxScroll);
      this.currentScroll = Mth.lerp(Minecraft.getInstance().getTimer().getRealtimeDeltaTicks() * 0.4F, this.currentScroll, this.targetScroll);
      graphics.enableScissor(
         this.toScreenCoord((double)(panelX + 5)),
         this.toScreenCoord((double)startY),
         this.toScreenCoord((double)(panelX + 135)),
         this.toScreenCoord((double)(startY + viewHeight))
      );
      graphics.pose().pushPose();
      graphics.pose().translate(0.0F, -this.currentScroll, 0.0F);

      for (int i = 0; i < this.displayList.size(); i++) {
         PartyMenuScreen.PartyEntry entry = this.displayList.get(i);
         int itemY = startY + i * 16;
         if ((float)(itemY + 16) >= (float)startY + this.currentScroll && (float)itemY <= (float)(startY + viewHeight) + this.currentScroll) {
            boolean isSelected = i == this.selectedIndex;
            boolean isHovered = mouseX >= panelX + 10
               && mouseX <= panelX + 120
               && (float)mouseY >= (float)itemY - this.currentScroll
               && (float)mouseY <= (float)(itemY + 16) - this.currentScroll;
            int color;
            if (this.currentTab == PartyMenuScreen.Tab.SERVER) {
               color = isSelected ? -22016 : (isHovered ? -5592406 : -1);
            } else if (!entry.isOnline()) {
               color = isSelected ? -3355444 : (isHovered ? -4473925 : -5592406);
            } else if (entry.isLeader()) {
               color = isSelected ? -4438 : (isHovered ? -8090 : -10496);
            } else {
               color = isSelected ? -52 : (isHovered ? -86 : -171);
            }

            String displayText = entry.name() + (entry.isLeader() ? " ⭐" : "");
            TextUtil.drawStringWithBorder(graphics, this.font, this.txt(displayText), panelX + 15, itemY + 4, color);
         }
      }

      graphics.pose().popPose();
      graphics.disableScissor();
      if (this.maxScroll > 0.0F) {
         int scrollBarX = panelX + 130;
         graphics.fill(scrollBarX, startY, scrollBarX + 2, startY + viewHeight, -13421773);
         float scrollPercent = this.currentScroll / this.maxScroll;
         float visiblePercent = (float)viewHeight / (float)totalHeight;
         int indicatorHeight = Math.max(10, (int)((float)viewHeight * visiblePercent));
         int indicatorY = startY + (int)((float)(viewHeight - indicatorHeight) * scrollPercent);
         graphics.fill(scrollBarX, indicatorY, scrollBarX + 2, indicatorY + indicatorHeight, -5592406);
      }
   }

   private void renderRightPanelDetails(GuiGraphics graphics, int panelX, int panelY) {
      if (this.selectedIndex >= 0 && this.selectedIndex < this.displayList.size()) {
         PartyMenuScreen.PartyEntry targetEntry = this.displayList.get(this.selectedIndex);
         UUID targetId = targetEntry.id();
         String displayName = targetEntry.name() + (targetEntry.isLeader() ? " ⭐" : "");
         int headerColor = targetEntry.isOnline() ? -10496 : -5592406;
         TextUtil.drawCenteredStringWithBorder(
            graphics, this.font, this.txt(displayName).withStyle(ChatFormatting.BOLD), panelX + 70, panelY + 16, headerColor, 0
         );
         Player targetPlayer = Minecraft.getInstance().level.getPlayerByUUID(targetId);
         int startY = panelY + 36;
         if (targetPlayer != null && targetEntry.isOnline()) {
            StatsProvider.get(StatsCapability.INSTANCE, targetPlayer)
               .ifPresent(
                  data -> {
                     int labelX = panelX + 20;
                     int valueX = panelX + 65;
                     TextUtil.drawStringWithBorder(
                        graphics,
                        this.font,
                        this.tr("gui.dragonminez.character_stats.race", new Object[0]).withStyle(style -> style.withBold(true)),
                        labelX,
                        startY,
                        14155509,
                        0
                     );
                     TextUtil.drawStringWithBorder(
                        graphics, this.font, this.tr("race.dragonminez." + data.getCharacter().getRaceName(), new Object[0]), valueX, startY, 16777215, 0
                     );
                     TextUtil.drawStringWithBorder(
                        graphics,
                        this.font,
                        this.tr("gui.dragonminez.character_stats.class", new Object[0]).withStyle(style -> style.withBold(true)),
                        labelX,
                        startY + 11,
                        14155509,
                        0
                     );
                     TextUtil.drawStringWithBorder(
                        graphics,
                        this.font,
                        this.tr("class.dragonminez." + data.getCharacter().getCharacterClass(), new Object[0]),
                        valueX,
                        startY + 11,
                        16777215,
                        0
                     );
                     TextUtil.drawStringWithBorder(
                        graphics,
                        this.font,
                        this.tr("gui.dragonminez.character_stats.level", new Object[0]).withStyle(style -> style.withBold(true)),
                        labelX,
                        startY + 22,
                        14155509,
                        0
                     );
                     TextUtil.drawStringWithBorder(graphics, this.font, this.txt(String.valueOf(data.getLevel())), valueX, startY + 22, 16777215, 0);
                     int statsY = startY + 48;
                     TextUtil.drawCenteredStringWithBorder(
                        graphics,
                        this.font,
                        this.tr("gui.dragonminez.character_stats.stats", new Object[0]).withStyle(ChatFormatting.BOLD),
                        panelX + 70,
                        statsY,
                        6868223,
                        0
                     );
                     int r1 = statsY + 18;
                     int r2 = statsY + 30;
                     int r3 = statsY + 42;
                     int r4 = statsY + 54;
                     int r5 = statsY + 66;
                     int r6 = statsY + 78;
                     int statLabelX = panelX + 30;
                     int statValueX = panelX + 60;
                     TextUtil.drawStringWithBorder(
                        graphics,
                        this.font,
                        this.tr("gui.dragonminez.character_stats.str", new Object[0]).withStyle(style -> style.withBold(true)),
                        statLabelX,
                        r1,
                        14095410,
                        0
                     );
                     TextUtil.drawStringWithBorder(graphics, this.font, this.txt(String.valueOf(data.getStats().getStrength())), statValueX, r1, 16766891, 0);
                     TextUtil.drawStringWithBorder(
                        graphics,
                        this.font,
                        this.tr("gui.dragonminez.character_stats.skp", new Object[0]).withStyle(style -> style.withBold(true)),
                        statLabelX,
                        r2,
                        14095410,
                        0
                     );
                     TextUtil.drawStringWithBorder(graphics, this.font, this.txt(String.valueOf(data.getStats().getStrikePower())), statValueX, r2, 16766891, 0);
                     TextUtil.drawStringWithBorder(
                        graphics,
                        this.font,
                        this.tr("gui.dragonminez.character_stats.res", new Object[0]).withStyle(style -> style.withBold(true)),
                        statLabelX,
                        r3,
                        14095410,
                        0
                     );
                     TextUtil.drawStringWithBorder(graphics, this.font, this.txt(String.valueOf(data.getStats().getResistance())), statValueX, r3, 16766891, 0);
                     TextUtil.drawStringWithBorder(
                        graphics,
                        this.font,
                        this.tr("gui.dragonminez.character_stats.vit", new Object[0]).withStyle(style -> style.withBold(true)),
                        statLabelX,
                        r4,
                        14095410,
                        0
                     );
                     TextUtil.drawStringWithBorder(graphics, this.font, this.txt(String.valueOf(data.getStats().getVitality())), statValueX, r4, 16766891, 0);
                     TextUtil.drawStringWithBorder(
                        graphics,
                        this.font,
                        this.tr("gui.dragonminez.character_stats.pwr", new Object[0]).withStyle(style -> style.withBold(true)),
                        statLabelX,
                        r5,
                        14095410,
                        0
                     );
                     TextUtil.drawStringWithBorder(graphics, this.font, this.txt(String.valueOf(data.getStats().getKiPower())), statValueX, r5, 16766891, 0);
                     TextUtil.drawStringWithBorder(
                        graphics,
                        this.font,
                        this.tr("gui.dragonminez.character_stats.ene", new Object[0]).withStyle(style -> style.withBold(true)),
                        statLabelX,
                        r6,
                        14095410,
                        0
                     );
                     TextUtil.drawStringWithBorder(graphics, this.font, this.txt(String.valueOf(data.getStats().getEnergy())), statValueX, r6, 16766891, 0);
                  }
               );
         } else {
            TextUtil.drawCenteredStringWithBorder(
               graphics,
               this.font,
               this.tr("gui.dragonminez.party.unavailable", new Object[0]).withStyle(ChatFormatting.RED),
               panelX + 70,
               startY + 20,
               16733525,
               0
            );
            TextUtil.drawCenteredStringWithBorder(
               graphics,
               this.font,
               this.tr("gui.dragonminez.party.out_of_range", new Object[0]).withStyle(ChatFormatting.GRAY),
               panelX + 70,
               startY + 32,
               11184810,
               0
            );
         }
      } else {
         TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt("???").withStyle(ChatFormatting.BOLD), panelX + 70, panelY + 16, -10496, 0);
         TextUtil.drawCenteredStringWithBorder(
            graphics,
            this.font,
            this.tr("gui.dragonminez.character_stats.stats", new Object[0]).withStyle(ChatFormatting.BOLD),
            panelX + 70,
            panelY + 84,
            6868223,
            0
         );
      }
   }

   private void renderCentralModel(GuiGraphics graphics, int x, int y, int scale, float mouseX, float mouseY) {
      LivingEntity renderEntity = Minecraft.getInstance().player;
      if (this.selectedIndex >= 0 && this.selectedIndex < this.displayList.size()) {
         PartyMenuScreen.PartyEntry targetEntry = this.displayList.get(this.selectedIndex);
         if (targetEntry.isOnline()) {
            AbstractClientPlayer targetPlayer = (AbstractClientPlayer)Minecraft.getInstance().level.getPlayerByUUID(targetEntry.id());
            if (targetPlayer != null) {
               renderEntity = targetPlayer;
            }
         } else {
            renderEntity = null;
         }
      }

      if (renderEntity != null) {
         int adjustedScale = this.getAdjustedModelScale(scale);
         float xRotation = (float)Math.atan((double)((float)y - mouseY) / 40.0);
         float yRotation = (float)Math.atan((double)((float)x - mouseX) / 40.0);
         Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
         Quaternionf cameraOrientation = new Quaternionf().rotateX(xRotation * 20.0F * (float) (Math.PI / 180.0));
         pose.mul(cameraOrientation);
         float yBodyRotO = renderEntity.yBodyRot;
         float yRotO = renderEntity.getYRot();
         float xRotO = renderEntity.getXRot();
         float yHeadRotO = renderEntity.yHeadRotO;
         float yHeadRot = renderEntity.yHeadRot;
         renderEntity.yBodyRot = 180.0F + yRotation * 20.0F;
         renderEntity.setYRot(180.0F + yRotation * 40.0F);
         renderEntity.setXRot(-xRotation * 20.0F);
         renderEntity.yHeadRot = renderEntity.getYRot();
         renderEntity.yHeadRotO = renderEntity.getYRot();
         graphics.pose().pushPose();
         graphics.pose().translate(0.0, 0.0, 150.0);
         EntityPreviewRenderContext.renderEntityInInventory(
            graphics, x, y, adjustedScale, new Vector3f(0.0F, 0.0F, 0.0F), pose, cameraOrientation, renderEntity
         );
         graphics.pose().popPose();
         renderEntity.yBodyRot = yBodyRotO;
         renderEntity.setYRot(yRotO);
         renderEntity.setXRot(xRotO);
         renderEntity.yHeadRotO = yHeadRotO;
         renderEntity.yHeadRot = yHeadRot;
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (this.maxScroll > 0.0F) {
         this.targetScroll = Mth.clamp(this.targetScroll - (float)Math.signum(scrollY) * 16.0F * 2.0F, 0.0F, this.maxScroll);
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (super.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else {
         double uiMouseX = this.toUiX(mouseX);
         double uiMouseY = this.toUiY(mouseY);
         int leftPanelX = 12 + this.getLeftPanelSwitchOffset(1.0F);
         int centerY = this.getUiHeight() / 2;
         int panelY = centerY - 105;
         if (uiMouseX >= (double)(leftPanelX + 17)
            && uiMouseX <= (double)(leftPanelX + 124)
            && uiMouseY >= (double)(panelY + 10)
            && uiMouseY <= (double)(panelY + 31)) {
            this.currentTab = this.currentTab == PartyMenuScreen.Tab.SERVER ? PartyMenuScreen.Tab.PARTY : PartyMenuScreen.Tab.SERVER;
            this.selectedIndex = -1;
            this.targetScroll = 0.0F;
            this.refreshPlayerList();
            this.refreshActionButtons();
            if (Minecraft.getInstance().player != null) {
               Minecraft.getInstance().player.playSound((SoundEvent)MainSounds.UI_MENU_SWITCH.get());
            }

            return true;
         } else {
            int startY = panelY + 35;
            int viewHeight = 160;
            if (this.maxScroll > 0.0F && TextUtil.overScrollBar(uiMouseX, uiMouseY, leftPanelX + 130, 2, startY, viewHeight)) {
               this.isDraggingScroll = true;
               this.targetScroll = TextUtil.scrollFromBar(uiMouseY, startY, viewHeight, this.maxScroll);
               return true;
            } else {
               if (uiMouseX >= (double)(leftPanelX + 10)
                  && uiMouseX <= (double)(leftPanelX + 120)
                  && uiMouseY >= (double)startY
                  && uiMouseY <= (double)(startY + viewHeight)) {
                  int index = (int)((uiMouseY - (double)startY + (double)this.currentScroll) / 16.0);
                  if (index >= 0 && index < this.displayList.size()) {
                     this.selectedIndex = index;
                     this.refreshActionButtons();
                     return true;
                  }
               }

               return false;
            }
         }
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.isDraggingScroll && this.maxScroll > 0.0F) {
         int panelY = this.getUiHeight() / 2 - 105;
         int startY = panelY + 35;
         this.targetScroll = TextUtil.scrollFromBar(this.toUiY(mouseY), startY, 160, this.maxScroll);
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
      } else {
         return super.mouseReleased(mouseX, mouseY, button);
      }
   }

   private static record PartyEntry(UUID id, String name, boolean isOnline, boolean isLeader) {
   }

   private static enum Tab {
      SERVER,
      PARTY;
   }
}
