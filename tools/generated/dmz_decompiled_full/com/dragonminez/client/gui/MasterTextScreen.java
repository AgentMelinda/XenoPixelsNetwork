package com.dragonminez.client.gui;

import com.dragonminez.client.gui.buttons.TexturedTextButton;
import com.dragonminez.client.gui.character.minigames.RythmGameScreen;
import com.dragonminez.client.gui.character.minigames.UltimateChallenge;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.hair.HairManager;
import com.dragonminez.common.init.MainItems;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.NPCActionC2S;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.server.world.dimension.HTCDimension;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public class MasterTextScreen extends Screen {
   private static final ResourceLocation BUTTONS_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/characterbuttons.png");
   private static final ResourceLocation MENU_TEXT = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/textmenu.png");
   private static final ResourceLocation DMZ_FONT = ResourceLocation.fromNamespaceAndPath("dragonminez", "smooth");
   private final String masterName;
   private Component currentDialogue;
   private boolean secondFunc = false;
   private boolean thirdFunc = false;
   private EditBox weightBox;

   public MasterTextScreen(String masterName) {
      super(Component.literal(masterName).withStyle(Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath("dragonminez", "smooth"))));
      this.masterName = masterName;
      this.currentDialogue = this.tr("gui.dragonminez.lines." + masterName + ".main", Minecraft.getInstance().player.getName());
   }

   protected void init() {
      super.init();
      int buttonX = this.width / 2 - 120;
      int buttonY = this.height - 23;
      StatsProvider.get(StatsCapability.INSTANCE, Minecraft.getInstance().player).ifPresent(stats -> {
         String var4 = this.masterName;
         switch (var4) {
            case "karin":
               this.initKarin(buttonX, buttonY, stats);
               break;
            case "guru":
               this.initGuru(buttonX, buttonY, stats);
               break;
            case "dende":
               this.initDende(buttonX, buttonY, stats);
               break;
            case "enma":
               this.initEnma(buttonX, buttonY, stats);
               break;
            case "baba":
               this.initBaba(buttonX, buttonY, stats);
               break;
            case "popo":
               this.initPopo(buttonX, buttonY, stats);
               break;
            case "gero":
               this.initGero(buttonX, buttonY, stats);
               break;
            case "toribot":
               this.initToribot(buttonX, buttonY, stats);
               break;
            case "piccolo":
               this.initPiccolo(buttonX, buttonY, stats);
               break;
            case "roshi":
               this.initWeightService(buttonX, buttonY, "roshi");
               break;
            case "kingkai":
               this.initWeightService(buttonX, buttonY, "kingkai");
               break;
            case "oldkai":
               this.initOldKai(buttonX, buttonY, stats);
               break;
            case "babidi":
               this.initBabidi(buttonX, buttonY, stats);
         }
      });
   }

   private void initKarin(int x, int y, StatsData stats) {
      if (!Minecraft.getInstance().player.getInventory().contains(new ItemStack((ItemLike)MainItems.NUBE_ITEM.get()))
         && !Minecraft.getInstance().player.getInventory().contains(new ItemStack((ItemLike)MainItems.NUBE_NEGRA_ITEM.get()))) {
         this.addRenderableWidget(
            new TexturedTextButton.Builder()
               .position(x, y)
               .size(74, 20)
               .texture(BUTTONS_TEXTURE)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr("gui.dragonminez.button.karin.nimbus"))
               .onPress(b -> {
                  NetworkHandler.sendToServer(new NPCActionC2S("karin", 1));
                  this.onClose();
               })
               .build()
         );
      }

      if (!stats.getCooldowns().hasCooldown("SenzuKarin")) {
         this.addRenderableWidget(
            new TexturedTextButton.Builder()
               .position(x + 180, y)
               .size(74, 20)
               .texture(BUTTONS_TEXTURE)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr("gui.dragonminez.button.karin.senzu"))
               .onPress(b -> {
                  NetworkHandler.sendToServer(new NPCActionC2S("karin", 2));
                  this.onClose();
               })
               .build()
         );
      }
   }

   private void initGuru(int x, int y, StatsData stats) {
      this.addRenderableWidget(
         new TexturedTextButton.Builder()
            .position(x, y)
            .size(74, 20)
            .texture(BUTTONS_TEXTURE)
            .textureCoords(0, 28, 0, 48)
            .textureSize(74, 20)
            .message(this.tr("gui.dragonminez.button.guru.unlock_potential"))
            .onPress(b -> {
               if (stats.getResources().getAlignment() <= 50) {
                  this.currentDialogue = this.tr("gui.dragonminez.lines.guru.evil");
               } else if (stats.getSkills().getSkillLevel("potentialunlock") < 10) {
                  this.currentDialogue = this.tr("gui.dragonminez.lines.guru.level");
               } else if (stats.getSkills().getSkillLevel("potentialunlock") == 10) {
                  NetworkHandler.sendToServer(new NPCActionC2S("guru", 1));
                  this.onClose();
               }
            })
            .build()
      );
   }

   private void initDende(int x, int y, StatsData stats) {
      if (this.secondFunc) {
         this.addRenderableWidget(
            new TexturedTextButton.Builder()
               .position(x, y)
               .size(74, 20)
               .texture(BUTTONS_TEXTURE)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr("gui.dragonminez.button.dende.reset_confirm"))
               .onPress(b -> {
                  NetworkHandler.sendToServer(new NPCActionC2S("dende", 2));
                  this.secondFunc = false;
                  this.onClose();
               })
               .build()
         );
         this.addRenderableWidget(
            new TexturedTextButton.Builder()
               .position(x + 180, y)
               .size(74, 20)
               .texture(BUTTONS_TEXTURE)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr("gui.dragonminez.button.dende.reset_cancel"))
               .onPress(b -> {
                  this.secondFunc = false;
                  this.currentDialogue = this.tr("gui.dragonminez.lines.dende.main", Minecraft.getInstance().player.getName());
                  this.refreshButtons();
               })
               .build()
         );
      } else {
         this.addRenderableWidget(
            new TexturedTextButton.Builder()
               .position(x, y)
               .size(74, 20)
               .texture(BUTTONS_TEXTURE)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr("gui.dragonminez.button.dende.heal"))
               .onPress(b -> {
                  NetworkHandler.sendToServer(new NPCActionC2S("dende", 1));
                  this.onClose();
               })
               .build()
         );
         if (ConfigManager.getRaceCharacter(stats.getCharacter().getRace()).getHasSaiyanTail()) {
            this.addRenderableWidget(
               new TexturedTextButton.Builder()
                  .position(x + 90, y)
                  .size(74, 20)
                  .texture(BUTTONS_TEXTURE)
                  .textureCoords(0, 28, 0, 48)
                  .textureSize(74, 20)
                  .message(
                     this.tr(stats.getCharacter().isHasSaiyanTail() ? "gui.dragonminez.button.dende.remove_tail" : "gui.dragonminez.button.dende.grow_tail")
                  )
                  .onPress(b -> {
                     NetworkHandler.sendToServer(new NPCActionC2S("dende", 3));
                     this.onClose();
                  })
                  .build()
            );
         }

         this.addRenderableWidget(
            new TexturedTextButton.Builder()
               .position(x + 180, y)
               .size(74, 20)
               .texture(BUTTONS_TEXTURE)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr("gui.dragonminez.button.dende.reset"))
               .onPress(b -> {
                  this.secondFunc = true;
                  this.currentDialogue = this.tr("gui.dragonminez.lines.dende.reset_warning", Minecraft.getInstance().player.getName());
                  this.refreshButtons();
               })
               .build()
         );
      }
   }

   private void initEnma(int x, int y, StatsData stats) {
      int cdTime = (int)((float)stats.getCooldowns().getCooldown("Revive") / 20.0F);
      this.currentDialogue = this.tr("gui.dragonminez.lines.enma.main", Minecraft.getInstance().player.getName(), cdTime);
      this.addRenderableWidget(
         new TexturedTextButton.Builder()
            .position(x, y)
            .size(74, 20)
            .texture(BUTTONS_TEXTURE)
            .textureCoords(0, 28, 0, 48)
            .textureSize(74, 20)
            .message(this.tr("gui.dragonminez.button.enma.earth"))
            .onPress(b -> StatsProvider.get(StatsCapability.INSTANCE, Minecraft.getInstance().player).ifPresent(currentStats -> {
                  boolean hasCdNow = currentStats.getCooldowns().hasCooldown("Revive");
                  if (hasCdNow) {
                     int seconds = (int)((float)currentStats.getCooldowns().getCooldown("Revive") / 20.0F);
                     this.currentDialogue = this.tr("gui.dragonminez.lines.enma.revive", Minecraft.getInstance().player.getName(), seconds);
                  } else {
                     NetworkHandler.sendToServer(new NPCActionC2S("enma", 1));
                     this.onClose();
                  }
               }))
            .build()
      );
   }

   private void initBaba(int x, int y, StatsData stats) {
      boolean hasCd = stats.getCooldowns().hasCooldown("Revive");
      int cdTime = hasCd ? stats.getCooldowns().getCooldown("Revive") / 20 : 0;
      this.currentDialogue = this.tr("gui.dragonminez.lines.baba.main", Minecraft.getInstance().player.getName(), cdTime);
      TexturedTextButton babaButton = new TexturedTextButton.Builder()
         .position(x, y)
         .size(74, 20)
         .texture(BUTTONS_TEXTURE)
         .textureCoords(0, 28, 0, 48)
         .textureSize(74, 20)
         .message(this.tr("gui.dragonminez.button.baba.revive"))
         .onPress(b -> StatsProvider.get(StatsCapability.INSTANCE, Minecraft.getInstance().player).ifPresent(currentStats -> {
               if (!currentStats.getCooldowns().hasCooldown("Revive")) {
                  NetworkHandler.sendToServer(new NPCActionC2S("baba", 1));
                  this.onClose();
               } else {
                  int seconds = (int)((float)currentStats.getCooldowns().getCooldown("Revive") / 20.0F);
                  this.currentDialogue = this.tr("gui.dragonminez.lines.baba.main", Minecraft.getInstance().player.getName(), seconds);
                  this.refreshButtons();
               }
            }))
         .build();
      babaButton.active = !hasCd;
      this.addRenderableWidget(babaButton);
   }

   private void initPopo(int x, int y, StatsData stats) {
      boolean HTC = Minecraft.getInstance().player.level().dimension().equals(HTCDimension.HTC_KEY);
      if (this.secondFunc) {
         this.addRenderableWidget(
            new TexturedTextButton.Builder()
               .position(x, y)
               .size(74, 20)
               .texture(BUTTONS_TEXTURE)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr("gui.dragonminez.button.popo.shadow"))
               .onPress(btn -> {
                  NetworkHandler.sendToServer(new NPCActionC2S("popo", 1));
                  this.secondFunc = false;
                  this.onClose();
               })
               .build()
         );
         this.addRenderableWidget(
            new TexturedTextButton.Builder()
               .position(x + 180, y)
               .size(74, 20)
               .texture(BUTTONS_TEXTURE)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr("gui.dragonminez.button.popo.rythm"))
               .onPress(btn -> {
                  if (Minecraft.getInstance().player.level().isClientSide()) {
                     Minecraft.getInstance().setScreen(new RythmGameScreen());
                  }
               })
               .build()
         );
      } else if (HTC) {
         this.addRenderableWidget(
            new TexturedTextButton.Builder()
               .position(x, y)
               .size(74, 20)
               .texture(BUTTONS_TEXTURE)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr("gui.dragonminez.button.popo.train"))
               .onPress(btn -> {
                  this.secondFunc = true;
                  this.currentDialogue = this.tr("gui.dragonminez.lines.popo.training", Minecraft.getInstance().player.getName());
                  this.refreshButtons();
               })
               .build()
         );
      } else if (HairManager.canUseHair(stats.getCharacter())) {
         this.addRenderableWidget(
            new TexturedTextButton.Builder()
               .position(x, y)
               .size(74, 20)
               .texture(BUTTONS_TEXTURE)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr("gui.dragonminez.button.popo.haircut"))
               .onPress(btn -> {
                  if (Minecraft.getInstance().player.level().isClientSide()) {
                     Minecraft.getInstance().setScreen(new HairEditorScreen(null, stats.getCharacter()));
                  }
               })
               .build()
         );
      }
   }

   private void initGero(int x, int y, StatsData stats) {
      boolean canBeUpgraded = ConfigManager.getRaceCharacter(stats.getCharacter().getRaceName()).getFormSkillTpCosts("androidforms").length > 0;
      if (!canBeUpgraded) {
         this.currentDialogue = this.tr("gui.dragonminez.lines.gero.not_eligible", Minecraft.getInstance().player.getName());
         this.addRenderableWidget(
            new TexturedTextButton.Builder()
               .position(x + 180, y)
               .size(74, 20)
               .texture(BUTTONS_TEXTURE)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr("gui.dragonminez.button.gero.not_interested"))
               .onPress(btn -> this.onClose())
               .build()
         );
      } else {
         if (this.thirdFunc) {
            this.addRenderableWidget(
               new TexturedTextButton.Builder()
                  .position(x + 180, y)
                  .size(74, 20)
                  .texture(BUTTONS_TEXTURE)
                  .textureCoords(0, 28, 0, 48)
                  .textureSize(74, 20)
                  .message(this.tr("gui.dragonminez.button.gero.cancel"))
                  .onPress(btn -> {
                     this.thirdFunc = false;
                     this.secondFunc = false;
                     this.onClose();
                  })
                  .build()
            );
            this.addRenderableWidget(
               new TexturedTextButton.Builder()
                  .position(x, y)
                  .size(74, 20)
                  .texture(BUTTONS_TEXTURE)
                  .textureCoords(0, 28, 0, 48)
                  .textureSize(74, 20)
                  .message(this.tr("gui.dragonminez.button.gero.confirm"))
                  .onPress(btn -> {
                     NetworkHandler.sendToServer(new NPCActionC2S("gero", 1));
                     this.thirdFunc = false;
                     this.secondFunc = false;
                     this.onClose();
                  })
                  .build()
            );
         } else if (this.secondFunc) {
            this.addRenderableWidget(
               new TexturedTextButton.Builder()
                  .position(x + 180, y)
                  .size(74, 20)
                  .texture(BUTTONS_TEXTURE)
                  .textureCoords(0, 28, 0, 48)
                  .textureSize(74, 20)
                  .message(this.tr("gui.dragonminez.button.gero.not_interested"))
                  .onPress(btn -> {
                     this.thirdFunc = false;
                     this.secondFunc = false;
                     this.onClose();
                  })
                  .build()
            );
            this.addRenderableWidget(
               new TexturedTextButton.Builder()
                  .position(x, y)
                  .size(74, 20)
                  .texture(BUTTONS_TEXTURE)
                  .textureCoords(0, 28, 0, 48)
                  .textureSize(74, 20)
                  .message(this.tr("gui.dragonminez.button.gero.interest"))
                  .onPress(btn -> {
                     this.thirdFunc = true;
                     this.secondFunc = false;
                     this.currentDialogue = this.tr("gui.dragonminez.lines.gero.confirm", Minecraft.getInstance().player.getName());
                     this.refreshButtons();
                  })
                  .build()
            );
         } else {
            this.addRenderableWidget(
               new TexturedTextButton.Builder()
                  .position(x + 180, y)
                  .size(74, 20)
                  .texture(BUTTONS_TEXTURE)
                  .textureCoords(0, 28, 0, 48)
                  .textureSize(74, 20)
                  .message(this.tr("gui.dragonminez.button.gero.accept"))
                  .onPress(btn -> {
                     this.secondFunc = true;
                     this.currentDialogue = this.tr("gui.dragonminez.lines.gero.offer", Minecraft.getInstance().player.getName());
                     this.refreshButtons();
                  })
                  .build()
            );
         }
      }
   }

   private void initToribot(int x, int y, StatsData stats) {
   }

   private void initPiccolo(int x, int y, StatsData stats) {
      if (this.secondFunc) {
         this.weightBox = new EditBox(this.font, this.width / 2 - 60, y - 28, 120, 16, Component.empty());
         this.weightBox.setMaxLength(6);
         this.weightBox.setFilter(s -> s.matches("\\d*"));
         this.addRenderableWidget(this.weightBox);
         this.setInitialFocus(this.weightBox);
         this.addRenderableWidget(
            new TexturedTextButton.Builder()
               .position(x, y)
               .size(74, 20)
               .texture(BUTTONS_TEXTURE)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr("gui.dragonminez.button.piccolo.confirm"))
               .onPress(b -> {
                  int weight = this.parseWeight(this.weightBox.getValue());
                  if (weight > 0) {
                     NetworkHandler.sendToServer(new NPCActionC2S("piccolo", 2, weight));
                     this.onClose();
                  }
               })
               .build()
         );
      } else {
         this.addRenderableWidget(
            new TexturedTextButton.Builder()
               .position(x, y)
               .size(74, 20)
               .texture(BUTTONS_TEXTURE)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr("gui.dragonminez.button.piccolo.heal"))
               .onPress(b -> {
                  NetworkHandler.sendToServer(new NPCActionC2S("piccolo", 1));
                  this.onClose();
               })
               .build()
         );
         this.addRenderableWidget(
            new TexturedTextButton.Builder()
               .position(x + 180, y)
               .size(74, 20)
               .texture(BUTTONS_TEXTURE)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr("gui.dragonminez.button.piccolo.weight"))
               .onPress(b -> {
                  this.secondFunc = true;
                  this.currentDialogue = this.tr("gui.dragonminez.lines.piccolo.weight_prompt", Minecraft.getInstance().player.getName());
                  this.refreshButtons();
               })
               .build()
         );
      }
   }

   private void initWeightService(int x, int y, String name) {
      if (this.secondFunc) {
         this.weightBox = new EditBox(this.font, this.width / 2 - 60, y - 28, 120, 16, Component.empty());
         this.weightBox.setMaxLength(6);
         this.weightBox.setFilter(s -> s.matches("\\d*"));
         this.addRenderableWidget(this.weightBox);
         this.setInitialFocus(this.weightBox);
         this.addRenderableWidget(
            new TexturedTextButton.Builder()
               .position(x, y)
               .size(74, 20)
               .texture(BUTTONS_TEXTURE)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr("gui.dragonminez.button.weight.confirm"))
               .onPress(b -> {
                  int weight = this.parseWeight(this.weightBox.getValue());
                  if (weight > 0) {
                     NetworkHandler.sendToServer(new NPCActionC2S(name, 2, weight));
                     this.onClose();
                  }
               })
               .build()
         );
      } else {
         this.addRenderableWidget(
            new TexturedTextButton.Builder()
               .position(x, y)
               .size(74, 20)
               .texture(BUTTONS_TEXTURE)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr("gui.dragonminez.button.weight"))
               .onPress(b -> {
                  this.secondFunc = true;
                  this.currentDialogue = this.tr("gui.dragonminez.lines." + name + ".weight_prompt", Minecraft.getInstance().player.getName());
                  this.refreshButtons();
               })
               .build()
         );
      }
   }

   private int parseWeight(String value) {
      try {
         return Integer.parseInt(value.trim());
      } catch (NumberFormatException var3) {
         return 0;
      }
   }

   private void initOldKai(int x, int y, StatsData stats) {
      this.addRenderableWidget(
         new TexturedTextButton.Builder()
            .position(x, y)
            .size(74, 20)
            .texture(BUTTONS_TEXTURE)
            .textureCoords(0, 28, 0, 48)
            .textureSize(74, 20)
            .message(this.tr("gui.dragonminez.button.oldkai.unlock_ultimate"))
            .onPress(b -> {
               if (stats.getResources().getAlignment() <= 61) {
                  this.currentDialogue = this.tr("gui.dragonminez.lines.oldkai.evil");
               } else if (stats.getSkills().getSkillLevel("potentialunlock") < 10) {
                  this.currentDialogue = this.tr("gui.dragonminez.lines.oldkai.level");
               } else {
                  new UltimateChallenge().start();
               }
            })
            .build()
      );
   }

   private void initBabidi(int x, int y, StatsData stats) {
      this.addRenderableWidget(
         new TexturedTextButton.Builder()
            .position(x, y)
            .size(74, 20)
            .texture(BUTTONS_TEXTURE)
            .textureCoords(0, 28, 0, 48)
            .textureSize(74, 20)
            .message(this.tr("gui.dragonminez.button.babidi.mark"))
            .onPress(b -> {
               if (stats.getEffects().hasEffect("majin")) {
                  this.currentDialogue = this.tr("gui.dragonminez.lines.babidi.already");
               } else if (stats.getResources().getAlignment() >= 39) {
                  this.currentDialogue = this.tr("gui.dragonminez.lines.babidi.too_good");
               } else {
                  NetworkHandler.sendToServer(new NPCActionC2S("babidi", 1));
                  this.onClose();
               }
            })
            .build()
      );
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      int centerX = this.width / 2;
      int centerY = this.height;
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.enableDepthTest();
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      RenderSystem.setShaderTexture(0, MENU_TEXT);
      BufferBuilder buffer = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
      buffer.addVertex((float)(centerX - 140), (float)(centerY + 250), 0.0F).setUv(0.0F, 1.0F);
      buffer.addVertex((float)(centerX + 140), (float)(centerY + 250), 0.0F).setUv(1.0F, 1.0F);
      buffer.addVertex((float)(centerX + 140), (float)(centerY - 90), 0.0F).setUv(1.0F, 0.0F);
      buffer.addVertex((float)(centerX - 140), (float)(centerY - 90), 0.0F).setUv(0.0F, 0.0F);
      BufferUploader.drawWithShader(buffer.buildOrThrow());
      RenderSystem.disableBlend();
      TextUtil.drawStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.lines." + this.masterName + ".name").withStyle(ChatFormatting.BOLD),
         centerX - 120,
         centerY - 87,
         16777215
      );
      int maxTextWidth = 230;
      int textY = centerY - 74;

      for (FormattedCharSequence line : this.font.split(this.currentDialogue, maxTextWidth)) {
         TextUtil.drawStringWithBorder(graphics, this.font, line, centerX - 120, textY, 16777215);
         textY += 9 + 2;
      }

      for (Renderable renderable : this.renderables) {
         renderable.render(graphics, mouseX, mouseY, partialTick);
      }
   }

   private void refreshButtons() {
      this.clearWidgets();
      this.init();
   }

   public boolean isPauseScreen() {
      return false;
   }

   public MutableComponent tr(String key, Object... args) {
      return Component.translatable(key, args).withStyle(Style.EMPTY.withFont(DMZ_FONT));
   }

   public MutableComponent txt(String text) {
      return Component.literal(text).withStyle(Style.EMPTY.withFont(DMZ_FONT));
   }
}
