package com.dragonminez.client.gui.tooltip;

import java.util.Optional;
import org.joml.Vector2i;
import org.joml.Vector2ic;

public class PrioritizeTooltipTopPositionModule implements TooltipPositionModule {
   @Override
   public Optional<Vector2ic> repositionTooltip(int x, int y, int width, int height, int mouseX, int mouseY, int screenWidth, int screenHeight) {
      return height <= screenHeight ? Optional.empty() : Optional.of(new Vector2i(x, 4));
   }
}
