package com.dragonminez.mixin.client;

import com.dragonminez.common.combat.util.Minecraft_DMZ;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Gui.class})
public abstract class InGameHudMixin {
   private static final ResourceLocation DMZ$BLOCK_SHIELD = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/hud/block_shield.png");
   private static final int DMZ$SHIELD_SIZE = 18;

   @Inject(
      method = {"renderCrosshair"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void dragonminez$pre_renderCrosshair(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
      if (dragonminez$isBlocking() && dragonminez$crosshairVisible()) {
         int x = (guiGraphics.guiWidth() - 18) / 2;
         int y = (guiGraphics.guiHeight() - 18) / 2;
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         guiGraphics.blit(DMZ$BLOCK_SHIELD, x, y, 0.0F, 0.0F, 18, 18, 18, 18);
         RenderSystem.disableBlend();
         ci.cancel();
      } else {
         if (((Minecraft_DMZ)Minecraft.getInstance()).hasTargetsInReach()) {
            RenderSystem.setShaderColor(1.0F, 0.0F, 0.0F, 1.0F);
         }
      }
   }

   @Inject(
      method = {"renderCrosshair"},
      at = {@At("TAIL")}
   )
   private void dragonminez$post_renderCrosshair(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   private static boolean dragonminez$isBlocking() {
      LocalPlayer player = Minecraft.getInstance().player;
      return player == null ? false : StatsProvider.get(StatsCapability.INSTANCE, player).map(data -> data.getStatus().isBlocking()).orElse(false);
   }

   private static boolean dragonminez$crosshairVisible() {
      Minecraft mc = Minecraft.getInstance();
      return !mc.options.getCameraType().isFirstPerson() ? false : mc.gameMode == null || mc.gameMode.getPlayerMode() != GameType.SPECTATOR;
   }
}
