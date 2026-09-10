package com.dragonminez.client.gui.tooltip;

import com.dragonminez.client.util.TooltipUtil;
import com.dragonminez.mixin.client.BundleTooltipComponentAccessor;
import com.dragonminez.mixin.client.ClientTextTooltipAccessor;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientBundleTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.util.Mth;

public class ScrollTracker {
   private static int targetVerticalScroll = 0;
   private static int targetHorizontalScroll = 0;
   private static float currentVerticalScroll = 0.0F;
   private static float currentHorizontalScroll = 0.0F;
   private static List<ClientTooltipComponent> trackedComponents = null;
   public static boolean renderedThisFrame = false;
   private static final int VERTICAL_SENSITIVITY = 15;
   private static final int HORIZONTAL_SENSITIVITY = 15;

   public static void addVerticalScroll(int amt) {
      targetVerticalScroll += -amt * 15;
   }

   public static void addHorizontalScroll(int amt) {
      targetHorizontalScroll += -amt * 15;
   }

   public static void updateTooltip(List<ClientTooltipComponent> components) {
      if (!isEqual(components, trackedComponents)) {
         reset();
      }

      trackedComponents = components;
   }

   public static void applyScroll(GuiGraphics graphics, int x, int y, int width, int height, int screenWidth, int screenHeight) {
      renderedThisFrame = true;
      if (height < screenHeight) {
         targetVerticalScroll = 0;
      }

      if (width < screenWidth) {
         targetHorizontalScroll = 0;
      }

      targetVerticalScroll = Mth.clamp(targetVerticalScroll, Math.min(screenHeight - (y + height) - 4, 0), Math.max(-y + 4, 0));
      targetHorizontalScroll = Mth.clamp(targetHorizontalScroll, Math.min(screenWidth - (x + width) - 4, 0), Math.max(-x + 4, 0));
      float tickDelta = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
      currentVerticalScroll = Mth.lerp(tickDelta * 0.5F, currentVerticalScroll, (float)targetVerticalScroll);
      currentHorizontalScroll = Mth.lerp(tickDelta * 0.5F, currentHorizontalScroll, (float)targetHorizontalScroll);
      graphics.pose().translate(currentHorizontalScroll, currentVerticalScroll, 0.0F);
   }

   public static void reset() {
      targetHorizontalScroll = 0;
      targetVerticalScroll = 0;
      currentHorizontalScroll = 0.0F;
      currentVerticalScroll = 0.0F;
   }

   private static boolean isEqual(List<ClientTooltipComponent> l1, List<ClientTooltipComponent> l2) {
      if (l1 != null && l2 != null) {
         Iterator<ClientTooltipComponent> iter1 = l1.iterator();
         Iterator<ClientTooltipComponent> iter2 = l2.iterator();

         while (iter1.hasNext() && iter2.hasNext()) {
            ClientTooltipComponent c1 = iter1.next();
            ClientTooltipComponent c2 = iter2.next();
            if (c1 != c2) {
               if (c1 instanceof ClientTextTooltip ot1 && c2 instanceof ClientTextTooltip ot2) {
                  if (!TooltipUtil.toText(((ClientTextTooltipAccessor)ot1).getText()).equals(TooltipUtil.toText(((ClientTextTooltipAccessor)ot2).getText()))) {
                     return false;
                  }
                  continue;
               }

               if (c1 instanceof ClientBundleTooltip bt1 && c2 instanceof ClientBundleTooltip bt2) {
                  if (!((BundleTooltipComponentAccessor)bt1).getContents().equals(((BundleTooltipComponentAccessor)bt2).getContents())) {
                     return false;
                  }
                  continue;
               }

               return false;
            }
         }

         return !iter1.hasNext() && !iter2.hasNext();
      } else {
         return false;
      }
   }
}
