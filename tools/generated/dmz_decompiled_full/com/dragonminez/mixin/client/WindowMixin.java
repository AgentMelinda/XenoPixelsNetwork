package com.dragonminez.mixin.client;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({Window.class})
public class WindowMixin {
   @ModifyVariable(
      method = {"setTitle"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private String dragonminez$appendTitle(String originalTitle) {
      String customPart = "DragonMine Z - End of Z v2.1.3";
      if (originalTitle == null) {
         originalTitle = "";
      }

      if (originalTitle.contains(customPart)) {
         return originalTitle;
      } else {
         String cleanTitle = originalTitle.replace("Minecraft* 1.20.1", "")
            .replace("Minecraft 1.20.1", "")
            .replace("Minecraft* Forge 1.20.1", "")
            .replace("Minecraft Forge 1.20.1", "")
            .trim();
         if (cleanTitle.startsWith("-")) {
            cleanTitle = cleanTitle.substring(1).trim();
         }

         if (cleanTitle.startsWith("|")) {
            cleanTitle = cleanTitle.substring(1).trim();
         }

         return cleanTitle.isEmpty() ? customPart : customPart + " | " + cleanTitle;
      }
   }
}
