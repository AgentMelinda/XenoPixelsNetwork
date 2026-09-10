package com.dragonminez.client.gui.tooltip;

import java.util.Optional;
import net.minecraft.util.Mth;
import org.joml.Vector2i;
import org.joml.Vector2ic;

public class BedrockCenteringPositionModule implements TooltipPositionModule {
   @Override
   public Optional<Vector2ic> repositionTooltip(int x, int y, int width, int height, int mouseX, int mouseY, int screenWidth, int screenHeight) {
      int modX = x;
      int modY;
      if (x >= 4 && x + width <= screenWidth - 4) {
         if (y + height <= screenHeight + 2) {
            return Optional.empty();
         }

         modY = Math.max(screenHeight - height - 4, 4);
      } else {
         modX = Mth.clamp(mouseX - width / 2, 6, screenWidth - width - 6);
         modY = mouseY - height - 12;
         if (modY < 6) {
            int below = mouseY + 12;
            int belowObstruction = below + height - screenHeight;
            int aboveObstruction = -modY;
            if (belowObstruction < aboveObstruction) {
               modY = below;
            }
         }
      }

      return Optional.of(new Vector2i(modX, modY));
   }
}
