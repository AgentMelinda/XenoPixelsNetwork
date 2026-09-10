package com.dragonminez.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class EntityPreviewRenderContext {
   private static int renderDepth;
   private static int hudPortraitDepth;

   private EntityPreviewRenderContext() {
   }

   public static boolean isRendering() {
      return renderDepth > 0;
   }

   public static void renderEntityInInventory(
      GuiGraphics graphics, int x, int y, int scale, Vector3f translation, Quaternionf pose, Quaternionf cameraOrientation, LivingEntity entity
   ) {
      renderEntityInInventory(graphics, x, y, (float)scale, translation, pose, cameraOrientation, entity);
   }

   public static void renderEntityInInventory(
      GuiGraphics graphics, int x, int y, float scale, Vector3f translation, Quaternionf pose, Quaternionf cameraOrientation, LivingEntity entity
   ) {
      renderDepth++;

      try {
         InventoryScreen.renderEntityInInventory(graphics, (float)x, (float)y, scale, translation, pose, cameraOrientation, entity);
      } finally {
         renderDepth--;
      }
   }

   public static boolean isHudPortrait() {
      return hudPortraitDepth > 0;
   }

   public static void renderHudPortrait(
      GuiGraphics graphics, int x, int y, float scale, Vector3f translation, Quaternionf pose, Quaternionf cameraOrientation, LivingEntity entity
   ) {
      hudPortraitDepth++;

      try {
         renderEntityInInventory(graphics, x, y, scale, translation, pose, cameraOrientation, entity);
      } finally {
         hudPortraitDepth--;
      }
   }
}
