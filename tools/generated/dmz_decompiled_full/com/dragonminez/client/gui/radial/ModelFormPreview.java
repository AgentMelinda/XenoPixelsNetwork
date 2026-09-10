package com.dragonminez.client.gui.radial;

import com.dragonminez.client.render.EntityPreviewRenderContext;
import com.dragonminez.client.render.layer.DMZSkinLayer;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.character.Status;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class ModelFormPreview {
   private ModelFormPreview() {
   }

   public static void render(GuiGraphics graphics, int x, int y, int scale, float mouseX, float mouseY, FormPreview preview) {
      LivingEntity player = Minecraft.getInstance().player;
      if (player != null) {
         ModelFormPreview.PreviewSwap swap = applyPreview(preview);
         int adjustedScale = adjustedScale(scale);
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
         DMZSkinLayer.PREVIEW_MODE = swap.applied();

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
            restorePreview(swap);
         }
      }
   }

   private static int adjustedScale(int baseScale) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         return baseScale;
      } else {
         float[] inverseScale = new float[]{1.0F};
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
            Float[] resolved = stats.getCharacter().getResolvedModelScaling();
            float currentScale = (resolved[0] + resolved[1]) / 2.0F;
            if (currentScale > 1.0F) {
               inverseScale[0] = 0.9375F / currentScale;
            }
         });
         return (int)((float)baseScale * inverseScale[0]);
      }
   }

   private static ModelFormPreview.PreviewSwap applyPreview(FormPreview preview) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && preview != null) {
         Optional<StatsData> cap = StatsProvider.get(StatsCapability.INSTANCE, player).resolve();
         if (cap.isEmpty()) {
            return empty();
         } else {
            Character character = cap.get().getCharacter();
            Status status = cap.get().getStatus();
            String formGroup = character.getActiveFormGroup();
            String form = character.getActiveForm();
            String stackGroup = character.getActiveStackFormGroup();
            String stackForm = character.getActiveStackForm();
            boolean androidUpgraded = status.isAndroidUpgraded();
            character.clearActiveForm();
            character.clearActiveStackForm();
            boolean stack = ConfigManager.getStackFormGroup(preview.group()) != null;
            if (stack) {
               character.setActiveStackForm(preview.group(), preview.form());
            } else {
               character.setActiveForm(preview.group(), preview.form());
            }

            boolean androidOverride = false;
            if ("androidforms".equals(preview.group()) && !androidUpgraded) {
               status.setAndroidUpgraded(true);
               androidOverride = true;
            }

            return new ModelFormPreview.PreviewSwap(character, status, formGroup, form, stackGroup, stackForm, androidUpgraded, androidOverride, true);
         }
      } else {
         return empty();
      }
   }

   private static void restorePreview(ModelFormPreview.PreviewSwap swap) {
      if (swap.applied() && swap.character() != null) {
         swap.character().clearActiveForm();
         swap.character().clearActiveStackForm();
         swap.character().setActiveForm(swap.formGroup(), swap.form());
         swap.character().setActiveStackForm(swap.stackGroup(), swap.stackForm());
         if (swap.androidOverride() && swap.status() != null) {
            swap.status().setAndroidUpgraded(swap.androidUpgraded());
         }
      }
   }

   private static ModelFormPreview.PreviewSwap empty() {
      return new ModelFormPreview.PreviewSwap(null, null, null, null, null, null, false, false, false);
   }

   private static record PreviewSwap(
      Character character,
      Status status,
      String formGroup,
      String form,
      String stackGroup,
      String stackForm,
      boolean androidUpgraded,
      boolean androidOverride,
      boolean applied
   ) {
   }
}
