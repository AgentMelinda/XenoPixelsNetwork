package com.dragonminez.client.events;

import com.dragonminez.client.crowdin.CrowdinManager;
import com.dragonminez.client.gui.SpacePodScreen;
import com.dragonminez.client.gui.UtilityMenuScreen;
import com.dragonminez.client.gui.character.CharacterCustomizationScreen;
import com.dragonminez.client.gui.character.CharacterStatsScreen;
import com.dragonminez.client.gui.character.QuestTreeScreen;
import com.dragonminez.client.gui.character.RaceSelectionScreen;
import com.dragonminez.client.gui.character.util.BaseMenuScreen;
import com.dragonminez.client.render.DMZRendererCache;
import com.dragonminez.client.render.shader.TransformationPostShaderManager;
import com.dragonminez.client.render.shader.UtilityMenuBlur;
import com.dragonminez.client.util.KeyBinds;
import com.dragonminez.client.util.TextureCounter;
import com.dragonminez.common.combat.logic.player.PlayerAttackHelper;
import com.dragonminez.common.combat.util.Minecraft_DMZ;
import com.dragonminez.common.compat.CameraAimHelper;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.entities.SpacePodEntity;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.SokidanControlC2S;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.mixin.client.MinecraftAccessor;
import com.dragonminez.mixin.common.LivingEntityAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.Clone;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.InputEvent.Key;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.GAME,
   value = {Dist.CLIENT}
)
public class ForgeClientEvents {
   public static boolean isHasCreatedCharacterCache = false;
   private static String lastLang = "";
   private static boolean pendingCharacterCreationReopen = false;
   private static int characterCreationOpenCooldownTicks = 0;
   private static final int CHARACTER_CREATION_OPEN_COOLDOWN = 8;
   private static final float VANILLA_SAFE_MAX_HEALTH = 200.0F;
   private static int tickCounter = 0;
   private static final int UPDATE_INTERVAL = 10;

   @SubscribeEvent
   public static void RenderHealthBar(Pre event) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         if (VanillaGuiLayers.PLAYER_HEALTH.equals(event.getName())) {
            if (isHasCreatedCharacterCache || player.getMaxHealth() + player.getAbsorptionAmount() > 200.0F) {
               event.setCanceled(true);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerClone(Clone event) {
      QuestTreeScreen.clearResummonCooldowns();
      CameraAimHelper.clearLocalSokidans();
   }

   @SubscribeEvent
   public static void onPlayerLogin(LoggingIn event) {
      TextureCounter.clearCache();
      CameraAimHelper.clearLocalSokidans();
      if (Minecraft.getInstance().player != null) {
         StatsProvider.get(StatsCapability.INSTANCE, Minecraft.getInstance().player)
            .ifPresent(data -> isHasCreatedCharacterCache = data.getStatus().isHasCreatedCharacter());
      }
   }

   @SubscribeEvent
   public static void onKeyInput(Key event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         if (KeyBinds.STATS_MENU.consumeClick()) {
            if (!(mc.screen instanceof BaseMenuScreen)) {
               if (mc.screen == null) {
                  if (!BaseMenuScreen.isStatsMenuReopenBlocked()) {
                     StatsProvider.get(StatsCapability.INSTANCE, mc.player).ifPresent(data -> {
                        if (data.getStatus().isHasCreatedCharacter()) {
                           mc.setScreen(new CharacterStatsScreen());
                        } else {
                           mc.setScreen(new RaceSelectionScreen(data.getCharacter()));
                        }

                        mc.player.playSound((SoundEvent)MainSounds.UI_MENU_SWITCH.get());
                     });
                  }
               }
            }
         } else if (mc.screen == null) {
            for (BaseMenuScreen.MenuTab tab : BaseMenuScreen.SECONDARY_TABS) {
               if (tab.key().consumeClick()) {
                  openMenuTab(mc, tab.factory().get());
                  return;
               }
            }

            if (KeyBinds.SPACEPOD_MENU.consumeClick() && mc.player.isPassenger() && mc.player.getVehicle() instanceof SpacePodEntity) {
               mc.setScreen(new SpacePodScreen());
               mc.player.playSound((SoundEvent)MainSounds.UI_MENU_SWITCH.get());
            }

            while (KeyBinds.SECOND_FUNCTION_KEY.consumeClick()) {
               Vec3 aim = cameraAim(mc);
               CameraAimHelper.store(mc.player, aim);
               NetworkHandler.sendToServer(SokidanControlC2S.toggle(aim));
            }
         }
      }
   }

   private static void openMenuTab(Minecraft mc, Screen screen) {
      if (!BaseMenuScreen.isStatsMenuReopenBlocked()) {
         StatsProvider.get(StatsCapability.INSTANCE, mc.player).ifPresent(data -> {
            if (data.getStatus().isHasCreatedCharacter()) {
               mc.setScreen(screen);
            } else {
               mc.setScreen(new RaceSelectionScreen(data.getCharacter()));
            }

            mc.player.playSound((SoundEvent)MainSounds.UI_MENU_SWITCH.get());
         });
      }
   }

   public static void requestCharacterCreationReopen() {
      if (ConfigManager.getServerConfig().getGameplay().getForceCharacterCreation()) {
         pendingCharacterCreationReopen = true;
      }
   }

   public static void markCharacterCreatedLocally() {
      isHasCreatedCharacterCache = true;
      pendingCharacterCreationReopen = false;
      characterCreationOpenCooldownTicks = 8;
   }

   private static boolean isCharacterCreationScreen(Screen screen) {
      return screen instanceof RaceSelectionScreen || screen instanceof CharacterCustomizationScreen;
   }

   private static boolean openCharacterCreationScreen(Minecraft mc) {
      if (mc.player == null) {
         return false;
      } else if (!ConfigManager.getServerConfig().getGameplay().getForceCharacterCreation()) {
         return false;
      } else if (isHasCreatedCharacterCache) {
         return false;
      } else if (characterCreationOpenCooldownTicks > 0) {
         return false;
      } else {
         boolean[] opened = new boolean[]{false};
         StatsProvider.get(StatsCapability.INSTANCE, mc.player).ifPresent(data -> {
            if (data.isDataLoaded()) {
               if (!data.getStatus().isHasCreatedCharacter()) {
                  if (!isCharacterCreationScreen(mc.screen)) {
                     if (!(mc.screen instanceof PauseScreen)) {
                        UtilityMenuBlur.stop();
                        mc.setScreen(new RaceSelectionScreen(data.getCharacter()));
                        characterCreationOpenCooldownTicks = 8;
                        opened[0] = true;
                     }
                  }
               }
            }
         });
         return opened[0];
      }
   }

   @SubscribeEvent
   public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null && CameraAimHelper.hasLocalSokidan(mc.level)) {
         Vec3 aim = cameraAim(mc);
         CameraAimHelper.store(mc.player, aim);
         NetworkHandler.sendToServer(SokidanControlC2S.aim(aim));
      }
   }

   @SubscribeEvent
   public static void onClientTick(Post event) {
      Minecraft mc = Minecraft.getInstance();
      TransformationPostShaderManager.tick();
      if (mc.player != null && mc.level != null) {
         if (characterCreationOpenCooldownTicks > 0) {
            characterCreationOpenCooldownTicks--;
         }

         handleUtilityMenuHold(mc);
         if (pendingCharacterCreationReopen && mc.screen == null) {
            if (isHasCreatedCharacterCache) {
               pendingCharacterCreationReopen = false;
            }

            StatsProvider.get(StatsCapability.INSTANCE, mc.player).ifPresent(data -> {
               if (data.getStatus().isHasCreatedCharacter()) {
                  isHasCreatedCharacterCache = true;
                  pendingCharacterCreationReopen = false;
               }
            });
            if (openCharacterCreationScreen(mc)) {
               pendingCharacterCreationReopen = false;
            }
         }

         if (mc.options.keyAttack.isDown()) {
            float cappedDelay = PlayerAttackHelper.getAttackCooldownTicksCapped(mc.player);
            int ticker = ((LivingEntityAccessor)mc.player).getAttackStrengthTicker();
            float cooldownProgress = Mth.clamp(((float)ticker + 0.5F) / cappedDelay, 0.0F, 1.0F);
            if (cooldownProgress >= 1.0F) {
               ((MinecraftAccessor)mc).setAttackCooldown(0);
            }
         }

         if (mc.screen == null) {
            openCharacterCreationScreen(mc);
         }

         tickCounter++;
         if (tickCounter >= 10) {
            tickCounter = 0;
            StatsProvider.get(StatsCapability.INSTANCE, mc.player).ifPresent(data -> {
               if (isHasCreatedCharacterCache != data.getStatus().isHasCreatedCharacter()) {
                  isHasCreatedCharacterCache = data.getStatus().isHasCreatedCharacter();
               }

               if (data.isDataLoaded()) {
                  if (!data.getStatus().isHasCreatedCharacter()) {
                     ;
                  }
               }
            });
         }

         String current = mc.options.languageCode;
         if (!current.equals(lastLang)) {
            lastLang = current;
            if (CrowdinManager.isLiveTranslationsEnabled()) {
               CrowdinManager.fetchLanguage(current);
            }
         }
      } else {
         CameraAimHelper.clearLocalSokidans();
      }
   }

   private static Vec3 cameraAim(Minecraft mc) {
      Vector3f look = mc.gameRenderer.getMainCamera().getLookVector();
      return new Vec3((double)look.x(), (double)look.y(), (double)look.z());
   }

   private static void handleUtilityMenuHold(Minecraft mc) {
      boolean utilityHeld = isUtilityMenuKeyHeld(mc);
      if (mc.screen instanceof UtilityMenuScreen utilityScreen) {
         if (!utilityHeld) {
            utilityScreen.startClosingAnimation();
         }
      } else if (utilityHeld) {
         if (mc.screen == null) {
            if (!UtilityMenuScreen.isUtilityMenuReopenBlocked()) {
               StatsProvider.get(StatsCapability.INSTANCE, mc.player).ifPresent(data -> {
                  if (data.getStatus().isHasCreatedCharacter()) {
                     mc.setScreen(new UtilityMenuScreen());
                     mc.player.playSound((SoundEvent)MainSounds.UI_MENU_SWITCH.get());
                  }
               });
            }
         }
      }
   }

   private static boolean isUtilityMenuKeyHeld(Minecraft mc) {
      if (mc != null && mc.getWindow() != null) {
         com.mojang.blaze3d.platform.InputConstants.Key key = KeyBinds.UTILITY_MENU.getKey();
         long window = mc.getWindow().getWindow();
         if (key.getType() == Type.KEYSYM) {
            return InputConstants.isKeyDown(window, key.getValue());
         } else {
            return key.getType() == Type.MOUSE ? GLFW.glfwGetMouseButton(window, key.getValue()) == 1 : false;
         }
      } else {
         return false;
      }
   }

   @SubscribeEvent
   public static void onRenderLevelStage(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_PARTICLES) {
         TransformationPostShaderManager.flushMaskAndApplyUniforms(
            event.getPartialTick().getGameTimeDeltaPartialTick(false), event.getPoseStack(), event.getCamera(), event.getFrustum()
         );
      }
   }

   @SubscribeEvent
   public static void onClientDisconnect(LoggingOut event) {
      CameraAimHelper.clearLocalSokidans();
      ConfigManager.clearServerSync();
      DMZRendererCache.clear();
      TextureCounter.clearCache();
      pendingCharacterCreationReopen = false;
      characterCreationOpenCooldownTicks = 0;
   }

   @SubscribeEvent
   public static void onPreRenderCrosshair(Pre event) {
      if (VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
         Minecraft client = Minecraft.getInstance();
         if (client != null && ((Minecraft_DMZ)client).hasTargetsInReach()) {
            RenderSystem.setShaderColor(1.0F, 0.0F, 0.0F, 1.0F);
         }
      }
   }

   @SubscribeEvent
   public static void onPostRenderCrosshair(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Post event) {
      if (VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      }
   }
}
