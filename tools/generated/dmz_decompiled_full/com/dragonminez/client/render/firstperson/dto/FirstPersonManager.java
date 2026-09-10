package com.dragonminez.client.render.firstperson.dto;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.MapItem;
import org.joml.Vector3f;

public class FirstPersonManager {
   public static boolean shouldRenderFirstPerson(Player player) {
      if (player != Minecraft.getInstance().player) {
         return false;
      } else if (!ConfigManager.getUserConfig().getFirstPersonAnimated()) {
         return false;
      } else if (player.getMainHandItem().getItem() instanceof MapItem || player.getOffhandItem().getItem() instanceof MapItem) {
         return false;
      } else if (Minecraft.getInstance().screen instanceof ChatScreen) {
         return Minecraft.getInstance().options.getCameraType().isFirstPerson();
      } else {
         return Minecraft.getInstance().screen != null ? false : Minecraft.getInstance().options.getCameraType().isFirstPerson();
      }
   }

   public static Vector3f offsetFirstPersonView(Player player) {
      float BASE_OFFSET_Y = 0.1F;
      float[] BASE_OFFSET_Z = new float[]{0.3F};
      float BASE_SCALE = 0.9375F;
      float[][] scaling = new float[][]{{0.9375F, 0.9375F, 0.9375F}};
      StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
         Character character = data.getCharacter();
         Float[] modelScaling = character.getModelScaling();
         if (modelScaling != null && modelScaling.length >= 2) {
            scaling[0][0] = modelScaling[0];
            scaling[0][1] = modelScaling[1];
         }

         if (character.isOozaruCached()) {
            BASE_OFFSET_Z[0] = 1.3F;
         } else {
            Float[] resolved = character.getResolvedModelScaling();
            if (resolved != null && resolved.length >= 2) {
               scaling[0][0] = resolved[0];
               scaling[0][1] = resolved[1];
            }
         }
      });
      float BASE_EYE_FRACTION = 0.85F;
      float SHRINK_EYE_COMPENSATION = 1.5F;
      float eyeFraction = 0.85F + Math.max(0.0F, 0.9375F - scaling[0][1]) * 1.5F;
      float modelHeightInBlocks = scaling[0][1] * 1.8F;
      float eyeHeightInBlocks = modelHeightInBlocks * eyeFraction;
      float defaultEyeHeight = 1.42F;
      float adjustedOffsetY = 0.1F + (eyeHeightInBlocks - defaultEyeHeight);
      return new Vector3f(0.0F, adjustedOffsetY, BASE_OFFSET_Z[0]);
   }
}
