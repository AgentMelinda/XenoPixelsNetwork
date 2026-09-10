package com.dragonminez.client.gui.character;

import com.dragonminez.client.events.ForgeClientEvents;
import com.dragonminez.client.gui.buttons.CustomTextureButton;
import com.dragonminez.client.gui.buttons.TexturedTextButton;
import com.dragonminez.client.gui.character.util.ScaledScreen;
import com.dragonminez.client.render.EntityPreviewRenderContext;
import com.dragonminez.client.render.shader.UtilityMenuBlur;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.common.config.RaceCharacterConfig;
import com.dragonminez.common.hair.HairManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.StatsSyncC2S;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class RaceSelectionScreen extends ScaledScreen {
   private static final ResourceLocation BUTTONS_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/characterbuttons.png");
   private static final ResourceLocation MENU_BIG = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menubig.png");
   private final Map<String, PanoramaRenderer> panoramaCache = new HashMap<>();
   private PanoramaRenderer currentPanorama;
   private PanoramaRenderer previousPanorama;
   private float panoramaFade = 1.0F;
   private float carouselAnim = 0.0F;
   protected static boolean GLOBAL_SWITCHING = false;
   private final Character character;
   private int selectedRaceIndex = 0;
   private boolean isSwitchingMenu = false;
   private float playerRotation = 180.0F;
   private boolean isDraggingModel = false;
   private double lastMouseX = 0.0;
   private CustomTextureButton leftButton;
   private CustomTextureButton rightButton;
   private TexturedTextButton selectButton;
   private static final long OPEN_ANIMATION_DURATION = 200L;
   private static final long CLOSE_ANIMATION_DURATION = 120L;
   private long animationStartTime;
   private RaceSelectionScreen.TransitionState transitionState = RaceSelectionScreen.TransitionState.NONE;
   private Screen pendingScreen;
   private boolean closeCommitted;

   public RaceSelectionScreen(Character character) {
      super(Component.translatable("gui.dragonminez.character_creation.title"));
      this.character = character;
      List<String> races = this.getAvailableRaces();

      for (int i = 0; i < races.size(); i++) {
         if (races.get(i).equals(character.getRace())) {
            this.selectedRaceIndex = i;
            break;
         }
      }
   }

   private List<String> getAvailableRaces() {
      return ConfigManager.getLoadedRaces();
   }

   private PanoramaRenderer getPanorama(String raceName) {
      return this.panoramaCache
         .computeIfAbsent(
            raceName,
            k -> {
               ResourceLocation testLoc = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/background/" + k + "_panorama_0.png");
               boolean exists = false;
               if (Minecraft.getInstance().getResourceManager() != null) {
                  exists = Minecraft.getInstance().getResourceManager().getResource(testLoc).isPresent();
               }

               ResourceLocation baseLoc = exists
                  ? ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/background/" + k + "_panorama")
                  : ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/background/roshi");
               return new PanoramaRenderer(new CubeMap(baseLoc));
            }
         );
   }

   protected void init() {
      UtilityMenuBlur.stop();
      super.init();
      this.startOpenTransition();
      List<String> races = this.getAvailableRaces();
      if (!races.isEmpty()) {
         this.currentPanorama = this.getPanorama(races.get(this.selectedRaceIndex));
         this.previousPanorama = this.currentPanorama;
      }

      int centerX = this.getUiWidth() / 2;
      int centerY = this.getUiHeight() / 2;
      this.leftButton = new CustomTextureButton.Builder()
         .position(centerX - 60 - 25, centerY + 88)
         .size(20, 20)
         .texture(BUTTONS_TEXTURE)
         .textureCoords(32, 0, 32, 14)
         .textureSize(8, 14)
         .onPress(btn -> this.previousRace())
         .build();
      this.rightButton = new CustomTextureButton.Builder()
         .position(centerX - 60 + 138, centerY + 88)
         .size(20, 20)
         .texture(BUTTONS_TEXTURE)
         .textureCoords(20, 0, 20, 14)
         .textureSize(8, 14)
         .onPress(btn -> this.nextRace())
         .build();
      this.selectButton = new TexturedTextButton.Builder()
         .position(this.getUiWidth() - 85, this.getUiHeight() - 25)
         .size(74, 20)
         .texture(BUTTONS_TEXTURE)
         .textureCoords(0, 28, 0, 48)
         .textureSize(74, 20)
         .message(this.tr("gui.dragonminez.customization.select", new Object[0]))
         .onPress(btn -> this.selectRace())
         .build();
      this.addRenderableWidget(this.leftButton);
      this.addRenderableWidget(this.rightButton);
      this.addRenderableWidget(this.selectButton);
   }

   public void tick() {
      super.tick();
      if (this.transitionState == RaceSelectionScreen.TransitionState.OPENING && this.getTransitionProgress() >= 1.0F) {
         this.transitionState = RaceSelectionScreen.TransitionState.NONE;
      }

      if (this.transitionState == RaceSelectionScreen.TransitionState.CLOSING && !this.closeCommitted) {
         if (this.getTransitionProgress() >= 1.0F) {
            this.closeCommitted = true;
            if (this.minecraft != null) {
               this.minecraft.setScreen(this.pendingScreen);
            }
         }
      }
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      float tickDelta = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
      if (this.panoramaFade < 1.0F) {
         this.panoramaFade = Math.min(1.0F, this.panoramaFade + tickDelta * 0.05F);
      }

      if (Math.abs(this.carouselAnim) > 0.001F) {
         this.carouselAnim = Mth.lerp(tickDelta * 0.35F, this.carouselAnim, 0.0F);
      } else {
         this.carouselAnim = 0.0F;
      }

      this.renderPanorama(graphics, partialTick);
      this.renderCinematicBars(graphics);
      int uiMouseX = (int)Math.round(this.toUiX((double)mouseX));
      int uiMouseY = (int)Math.round(this.toUiY((double)mouseY));
      this.beginUiScale(graphics);
      this.renderCarouselElements(graphics, uiMouseX, uiMouseY);
      super.render(graphics, uiMouseX, uiMouseY, partialTick);
      this.renderRaceInfo(graphics);
      this.renderRacialInfo(graphics);
      this.endUiScale(graphics);
   }

   private void renderCarouselElements(GuiGraphics graphics, int mouseX, int mouseY) {
      List<String> races = this.getAvailableRaces();
      if (!races.isEmpty()) {
         int centerX = this.getUiWidth() / 2;
         int centerY = this.getUiHeight() / 2 + 92;
         int modelBaseY = this.getUiHeight() / 2 + 70;
         String originalRace = races.get(this.selectedRaceIndex);
         List<Integer> carouselIndices = new ArrayList<>(List.of(-2, -1, 0, 1, 2));
         carouselIndices.sort((a, b) -> {
            float absA = Math.abs((float)a.intValue() + this.carouselAnim);
            float absB = Math.abs((float)b.intValue() + this.carouselAnim);
            return Float.compare(absB, absA);
         });

         for (int i : carouselIndices) {
            float visualPos = (float)i + this.carouselAnim;
            float absPos = Math.abs(visualPos);
            if (!(absPos > 1.5F)) {
               float scale = Math.max(0.5F, 1.0F - absPos * 0.35F);
               float alpha = Math.max(0.0F, 1.0F - absPos * 0.6F);
               if (!(alpha <= 0.05F)) {
                  int raceIndex = (this.selectedRaceIndex + i) % races.size();
                  if (raceIndex < 0) {
                     raceIndex += races.size();
                  }

                  String raceName = races.get(raceIndex);
                  float xOffset = visualPos * 130.0F;
                  this.applyRaceDefaults(raceName);
                  int modelX = (int)((float)(centerX + 5) + xOffset);
                  RenderSystem.setShaderColor(alpha, alpha, alpha, 1.0F);
                  this.renderPlayerModel(graphics, modelX, modelBaseY, (int)(75.0F * scale), (float)mouseX, (float)mouseY);
                  RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                  graphics.pose().pushPose();
                  graphics.pose().translate((float)centerX + xOffset, (float)centerY, 0.0F);
                  graphics.pose().scale(scale, scale, 1.0F);
                  RenderSystem.enableBlend();
                  RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
                  graphics.blit(MENU_BIG, -74, -7, 0, 215, 149, 21);
                  RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                  RenderSystem.disableBlend();
                  int alphaHex = (int)(alpha * 255.0F) << 24;
                  int color = 8191446 | alphaHex;
                  TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.tr("race.dragonminez." + raceName, new Object[0]), 0, 0, color);
                  graphics.pose().popPose();
               }
            }
         }

         this.applyRaceDefaults(originalRace);
      }
   }

   private void renderCinematicBars(GuiGraphics guiGraphics) {
      int totalBarHeight = (int)((double)this.height * 0.12);
      int fadeSize = 60;
      if (totalBarHeight <= fadeSize) {
         totalBarHeight = fadeSize + 1;
      }

      int solidHeight = totalBarHeight - fadeSize;
      int colorSolid = -16777216;
      int colorTransparent = 0;
      guiGraphics.fill(0, 0, this.width, solidHeight, colorSolid);
      guiGraphics.fillGradient(0, solidHeight, this.width, solidHeight + fadeSize, colorSolid, colorTransparent);
      int bottomBarStartY = this.height - totalBarHeight;
      guiGraphics.fillGradient(0, bottomBarStartY, this.width, bottomBarStartY + fadeSize, colorTransparent, colorSolid);
      guiGraphics.fill(0, bottomBarStartY + fadeSize, this.width, this.height, colorSolid);
   }

   protected void renderPanorama(GuiGraphics graphics, float partialTick) {
      if (this.previousPanorama != null && this.panoramaFade < 1.0F) {
         this.previousPanorama.render(graphics, this.width, this.height, 1.0F, partialTick);
         if (this.currentPanorama != null) {
            this.currentPanorama.render(graphics, this.width, this.height, this.panoramaFade, partialTick);
         }
      } else if (this.currentPanorama != null) {
         this.currentPanorama.render(graphics, this.width, this.height, 1.0F, partialTick);
      }
   }

   private void renderRaceInfo(GuiGraphics graphics) {
      List<String> races = this.getAvailableRaces();
      if (!races.isEmpty()) {
         if (this.selectedRaceIndex >= races.size()) {
            this.selectedRaceIndex = 0;
         }

         String currentRace = races.get(this.selectedRaceIndex);
         int uiHeight = this.getUiHeight();
         int panelWidth = 130;
         int marginFromEdge = 10;
         int centerX = marginFromEdge + panelWidth / 2;
         int startY = uiHeight / 2 - 50;
         String rawName = Component.translatable("race.dragonminez." + currentRace).getString();
         String boldAndColoredName = "§l" + rawName.replaceAll("(?i)(§[0-9a-fr])", "$1§l");
         Component raceName = this.txt(boldAndColoredName);
         graphics.pose().pushPose();
         graphics.pose().translate(0.0, 0.0, 400.0);
         TextUtil.drawCenteredStringWithBorder(graphics, this.font, raceName, centerX, startY - 12, -8585770);
         Component description = this.tr("race.dragonminez." + currentRace + ".desc", new Object[0]);
         List<String> wrappedDesc = this.wrapText(description.getString(), panelWidth);
         int textY = startY;

         for (String line : wrappedDesc) {
            TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(line), centerX, textY, -3355444);
            textY += 12;
         }

         graphics.pose().popPose();
      }
   }

   private void renderRacialInfo(GuiGraphics graphics) {
      List<String> races = this.getAvailableRaces();
      if (!races.isEmpty()) {
         if (this.selectedRaceIndex >= races.size()) {
            this.selectedRaceIndex = 0;
         }

         String currentRace = races.get(this.selectedRaceIndex);
         if (ConfigManager.getRaceCharacter(currentRace) != null) {
            GeneralServerConfig.RacialSkillsConfig config = ConfigManager.getServerConfig().getRacialSkills();
            String racialSkill = ConfigManager.getRaceCharacter(currentRace).getRacialSkill();
            if (racialSkill != null && !racialSkill.isEmpty()) {
               String titleKey = "skill.dragonminez.racial_" + racialSkill;
               String descKey = "skill.dragonminez.racial_" + racialSkill + ".desc";
               Component titleComp = this.tr(titleKey, new Object[0]);
               String description = "";

               description = switch (racialSkill) {
                  case "human" -> {
                     int regen = (int)Math.round((config.getHumanKiRegenBoost() - 1.0) * 100.0);
                     yield this.tr(descKey, new Object[]{regen}).getString();
                  }
                  case "saiyan" -> {
                     int zenkaiHealth = (int)Math.round(config.getSaiyanZenkaiHealthRegen() * 100.0);
                     int zenkaiStat = (int)Math.round(config.getSaiyanZenkaiStatBoost() * 100.0);
                     int cooldown = config.getSaiyanZenkaiCooldownSeconds();
                     int maxUses = config.getSaiyanZenkaiAmount();
                     yield this.tr(descKey, new Object[]{zenkaiHealth, zenkaiStat, cooldown, maxUses}).getString();
                  }
                  case "namekian" -> {
                     int assimHealth = (int)Math.round(config.getNamekianAssimilationHealthRegen() * 100.0);
                     int assimStat = (int)Math.round(config.getNamekianAssimilationStatBoost() * 100.0);
                     int maxUses = config.getNamekianAssimilationAmount();
                     yield this.tr(descKey, new Object[]{assimHealth, assimStat, maxUses}).getString();
                  }
                  case "frostdemon" -> {
                     int tpBoost = (int)Math.round((config.getFrostDemonTPBoost() - 1.0) * 100.0);
                     yield this.tr(descKey, new Object[]{tpBoost}).getString();
                  }
                  case "bioandroid" -> {
                     int drainRatio = (int)Math.round(config.getBioAndroidDrainRatio() * 100.0);
                     int cooldown = config.getBioAndroidCooldownSeconds();
                     yield this.tr(descKey, new Object[]{drainRatio, cooldown}).getString();
                  }
                  case "majin" -> {
                     int absHealth = (int)Math.round(config.getMajinAbsorptionHealthRegen() * 100.0);
                     int absStat = (int)Math.round(config.getMajinAbsorptionStatCopy() * 100.0);
                     int maxUses = config.getMajinAbsorptionAmount();
                     yield this.tr(descKey, new Object[]{absHealth, absStat, maxUses}).getString();
                  }
                  default -> this.tr(descKey, new Object[0]).getString();
               };
               int uiWidth = this.getUiWidth();
               int uiHeight = this.getUiHeight();
               int panelWidth = 130;
               int marginFromEdge = 68;
               int boxStartX = uiWidth - marginFromEdge - panelWidth;
               int centerX = boxStartX + panelWidth / 2;
               int startY = uiHeight / 2 - 50;
               graphics.pose().pushPose();
               graphics.pose().translate(0.0, 0.0, 400.0);
               TextUtil.drawCenteredStringWithBorder(graphics, this.font, titleComp.copy().withStyle(ChatFormatting.BOLD), centerX + 60, startY - 12, -11141291);
               List<String> wrappedDesc = this.wrapText(description, panelWidth);
               int textY = startY;

               for (String line : wrappedDesc) {
                  TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(line), centerX + 60, textY, -3355444);
                  textY += 12;
               }

               graphics.pose().popPose();
            }
         }
      }
   }

   private void renderPlayerModel(GuiGraphics graphics, int x, int y, int scale, float mouseX, float mouseY) {
      LivingEntity player = Minecraft.getInstance().player;
      if (player != null) {
         int adjustedScale = this.getAdjustedModelScale(scale);
         Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
         Quaternionf cameraOrientation = new Quaternionf().rotateX(0.0F);
         pose.mul(cameraOrientation);
         float yBodyRotO = player.yBodyRot;
         float yRotO = player.getYRot();
         float xRotO = player.getXRot();
         float yHeadRotO = player.yHeadRotO;
         float yHeadRot = player.yHeadRot;
         player.yBodyRot = this.playerRotation;
         player.setYRot(this.playerRotation);
         player.setXRot(0.0F);
         player.yHeadRot = this.playerRotation;
         player.yHeadRotO = this.playerRotation;
         graphics.pose().pushPose();
         graphics.pose().translate(0.0, 0.0, 150.0);
         EntityPreviewRenderContext.renderEntityInInventory(graphics, x, y, adjustedScale, new Vector3f(0.0F, 0.0F, 0.0F), pose, cameraOrientation, player);
         graphics.pose().popPose();
         player.yBodyRot = yBodyRotO;
         player.setYRot(yRotO);
         player.setXRot(xRotO);
         player.yHeadRotO = yHeadRotO;
         player.yHeadRot = yHeadRot;
      }
   }

   protected int getAdjustedModelScale(int baseScale) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         return baseScale;
      } else {
         float[] inverseScale = new float[]{1.0F};
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
            Character character = stats.getCharacter();
            Float[] resolved = character.getResolvedModelScaling();
            float currentScale = (resolved[0] + resolved[1]) / 2.0F;
            if (currentScale > 1.0F) {
               inverseScale[0] = 0.9375F / currentScale;
            }
         });
         return (int)((float)baseScale * inverseScale[0]);
      }
   }

   private List<String> wrapText(String text, int maxWidth) {
      return TextUtil.wrap(this.font, text, maxWidth, DMZ_FONT);
   }

   private void applyRaceDefaults(String race) {
      this.character.setRace(race);
      RaceCharacterConfig config = ConfigManager.getRaceCharacter(race);
      if (config != null) {
         this.character.setBodyColor(config.getDefaultBodyColor());
         this.character.setBodyColor2(config.getDefaultBodyColor2());
         this.character.setBodyColor3(config.getDefaultBodyColor3());
         this.character.setHairColor(config.getDefaultHairColor());
         this.character.setEye1Color(config.getDefaultEye1Color());
         this.character.setEye2Color(config.getDefaultEye2Color());
         this.character.setAuraColor(config.getDefaultAuraColor());
         this.character.setBodyType(config.getDefaultBodyType());
         this.character.setHairId(config.getDefaultHairType());
         if (HairManager.canUseHair(this.character)) {
            this.character.setActiveHeadBone("hair");
            this.character.setRenderHairBase(true);
         } else if (config.getHeadBones() != null && config.getHeadBones().length > 0) {
            String firstExtraBone = "";
            if (this.character.areExtraHeadBonesEnabled()) {
               for (String bone : config.getHeadBones()) {
                  if (bone != null && !bone.isEmpty() && !bone.equals("hair")) {
                     firstExtraBone = bone;
                     break;
                  }
               }
            }

            this.character.setActiveHeadBone(firstExtraBone);
         } else {
            this.character.setActiveHeadBone("");
         }

         this.character.setEyesType(config.getDefaultEyesType());
         this.character.setNoseType(config.getDefaultNoseType());
         this.character.setMouthType(config.getDefaultMouthType());
         this.character.setTattooType(config.getDefaultTattooType());
      }
   }

   private void updateCharacterRace() {
      List<String> races = this.getAvailableRaces();
      if (!races.isEmpty()) {
         this.applyRaceDefaults(races.get(this.selectedRaceIndex));
         NetworkHandler.sendToServer(new StatsSyncC2S(this.character));
      }
   }

   private void previousRace() {
      List<String> races = this.getAvailableRaces();
      if (!races.isEmpty()) {
         this.previousPanorama = this.currentPanorama;
         this.panoramaFade = 0.0F;
         this.carouselAnim = -1.0F;
         this.selectedRaceIndex = (this.selectedRaceIndex - 1 + races.size()) % races.size();
         this.updateCharacterRace();
         this.currentPanorama = this.getPanorama(races.get(this.selectedRaceIndex));
      }
   }

   private void nextRace() {
      List<String> races = this.getAvailableRaces();
      if (!races.isEmpty()) {
         this.previousPanorama = this.currentPanorama;
         this.panoramaFade = 0.0F;
         this.carouselAnim = 1.0F;
         this.selectedRaceIndex = (this.selectedRaceIndex + 1) % races.size();
         this.updateCharacterRace();
         this.currentPanorama = this.getPanorama(races.get(this.selectedRaceIndex));
      }
   }

   private void selectRace() {
      List<String> races = this.getAvailableRaces();
      if (!races.isEmpty()) {
         String selectedRace = races.get(this.selectedRaceIndex);
         this.character.setRace(selectedRace);
         if (this.minecraft != null) {
            this.isSwitchingMenu = true;
            GLOBAL_SWITCHING = true;
            this.startCloseTransition(new CharacterCustomizationScreen(this, this.character));
         }

         NetworkHandler.sendToServer(new StatsSyncC2S(this.character));
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.transitionState == RaceSelectionScreen.TransitionState.CLOSING) {
         return true;
      } else {
         double uiMouseX = this.toUiX(mouseX);
         double uiMouseY = this.toUiY(mouseY);
         int centerX = this.getUiWidth() / 2 + 5;
         int centerY = this.getUiHeight() / 2 + 70;
         int modelRadius = 60;
         if (uiMouseX >= (double)(centerX - modelRadius)
            && uiMouseX <= (double)(centerX + modelRadius)
            && uiMouseY >= (double)(centerY - 100)
            && uiMouseY <= (double)(centerY + 20)) {
            this.isDraggingModel = true;
            this.lastMouseX = uiMouseX;
            return true;
         } else {
            return super.mouseClicked(mouseX, mouseY, button);
         }
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.transitionState == RaceSelectionScreen.TransitionState.CLOSING) {
         return true;
      } else {
         this.isDraggingModel = false;
         return super.mouseReleased(mouseX, mouseY, button);
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.transitionState == RaceSelectionScreen.TransitionState.CLOSING) {
         return true;
      } else if (this.isDraggingModel) {
         double uiMouseX = this.toUiX(mouseX);
         double deltaX = uiMouseX - this.lastMouseX;
         this.playerRotation -= (float)(deltaX * 0.8);
         this.lastMouseX = uiMouseX;
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.transitionState == RaceSelectionScreen.TransitionState.CLOSING) {
         return true;
      } else if (keyCode == 256 && this.minecraft != null) {
         if (ConfigManager.getServerConfig().getGameplay().getForceCharacterCreation()) {
            ForgeClientEvents.requestCharacterCreationReopen();
            this.minecraft.setScreen(new PauseScreen(true));
         } else {
            this.startCloseTransition(null);
         }

         return true;
      } else if (keyCode == 263) {
         this.previousRace();
         return true;
      } else if (keyCode == 262) {
         this.nextRace();
         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      this.startCloseTransition(null);
   }

   private void startOpenTransition() {
      this.transitionState = RaceSelectionScreen.TransitionState.OPENING;
      this.animationStartTime = System.currentTimeMillis();
      this.pendingScreen = null;
      this.closeCommitted = false;
   }

   private void startCloseTransition(Screen nextScreen) {
      if (this.transitionState != RaceSelectionScreen.TransitionState.CLOSING) {
         this.pendingScreen = nextScreen;
         this.transitionState = RaceSelectionScreen.TransitionState.CLOSING;
         this.animationStartTime = System.currentTimeMillis();
         this.closeCommitted = false;
      }
   }

   private float getTransitionProgress() {
      long duration = this.transitionState == RaceSelectionScreen.TransitionState.CLOSING ? 120L : 200L;
      if (duration <= 0L) {
         return 1.0F;
      } else {
         long elapsed = System.currentTimeMillis() - this.animationStartTime;
         return Mth.clamp((float)elapsed / (float)duration, 0.0F, 1.0F);
      }
   }

   private static enum TransitionState {
      NONE,
      OPENING,
      CLOSING;
   }
}
