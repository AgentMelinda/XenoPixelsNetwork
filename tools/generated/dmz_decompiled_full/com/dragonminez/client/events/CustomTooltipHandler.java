package com.dragonminez.client.events;

import com.dragonminez.client.gui.tooltip.CustomTooltipNodes;
import com.dragonminez.client.gui.tooltip.TooltipDecor;
import com.dragonminez.client.util.ColorUtils;
import com.mojang.datafixers.util.Either;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderTooltipEvent.Color;
import net.neoforged.neoforge.client.event.RenderTooltipEvent.GatherComponents;

@EventBusSubscriber(
   modid = "dragonminez",
   value = {Dist.CLIENT},
   bus = Bus.GAME
)
public class CustomTooltipHandler {
   private static ItemStack lastTooltipItem = ItemStack.EMPTY;

   @SubscribeEvent
   public static void onClientTick(Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (!mc.isPaused()) {
         float deltaTime = mc.getTimer().getRealtimeDeltaTicks() / 50.0F;
         TooltipDecor.updateTimer(deltaTime);
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void onGatherComponents(GatherComponents event) {
      ItemStack stack = event.getItemStack();
      if (!stack.isEmpty()) {
         List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
         if (!elements.isEmpty()) {
            Either<FormattedText, TooltipComponent> first = elements.get(0);
            if (first.left().isPresent()) {
               elements.set(0, Either.right(new CustomTooltipNodes.HeaderNode(stack, (FormattedText)first.left().get())));
            }

            elements.add(1, Either.right(new CustomTooltipNodes.PaddingNode(6)));
            elements.add(2, Either.right(new CustomTooltipNodes.SeparatorNode()));
         }
      }
   }

   @SubscribeEvent
   public static void onTooltipColor(Color event) {
      int baseColor = ChatFormatting.WHITE.getColor();
      if (TooltipDecor.forceCustomBorder) {
         baseColor = TooltipDecor.forcedColor;
         TooltipDecor.hasSpecialBorder = true;
      } else {
         ItemStack stack = event.getItemStack();
         if (stack.isEmpty()) {
            TooltipDecor.hasSpecialBorder = false;
            return;
         }

         if (!ItemStack.isSameItemSameComponents(stack, lastTooltipItem)) {
            TooltipDecor.resetTimer();
            lastTooltipItem = stack.copy();
         }

         TextColor nameColor = stack.getHoverName().getStyle().getColor();
         Integer legacyColor = getLegacyFormattingColor(stack.getHoverName());
         if (nameColor != null) {
            baseColor = nameColor.getValue();
         } else if (legacyColor != null) {
            baseColor = legacyColor;
         } else if (stack.getRarity() != null && stack.getRarity().color() != null) {
            Integer rColor = stack.getRarity().color().getColor();
            if (rColor != null) {
               baseColor = rColor;
            }
         }

         if (baseColor == ChatFormatting.WHITE.getColor() || baseColor == ChatFormatting.GRAY.getColor()) {
            baseColor = 10070715;
         }

         TooltipDecor.hasSpecialBorder = true;
      }

      float[] hsv = ColorUtils.rgbToHsv(baseColor >> 16 & 0xFF, baseColor >> 8 & 0xFF, baseColor & 0xFF);
      float hue = hsv[0];
      boolean addHue = hue >= 62.0F && hue <= 240.0F;
      float startHue = (addHue ? hue - 4.0F : hue + 4.0F + 360.0F) % 360.0F;
      float endHue = (addHue ? hue + 18.0F : hue - 18.0F + 360.0F) % 360.0F;
      float startBGHue = (addHue ? hue - 3.0F : hue + 3.0F + 360.0F) % 360.0F;
      float endBGHue = (addHue ? hue + 13.0F : hue - 13.0F + 360.0F) % 360.0F;
      int[] startColorRGB = ColorUtils.hsvToRgb(startHue, hsv[1], hsv[2]);
      int[] endColorRGB = ColorUtils.hsvToRgb(endHue, hsv[1], hsv[2] * 0.95F);
      int[] startBgRGB = ColorUtils.hsvToRgb(startBGHue, hsv[1] * 0.9F, 14.0F);
      int[] endBgRGB = ColorUtils.hsvToRgb(endBGHue, hsv[1] * 0.8F, 18.0F);
      int borderStart = TooltipDecor.combineARGB(255, startColorRGB[0], startColorRGB[1], startColorRGB[2]);
      int borderEnd = TooltipDecor.combineARGB(255, endColorRGB[0], endColorRGB[1], endColorRGB[2]);
      int bgStart = TooltipDecor.combineARGB(228, startBgRGB[0], startBgRGB[1], startBgRGB[2]);
      int bgEnd = TooltipDecor.combineARGB(253, endBgRGB[0], endBgRGB[1], endBgRGB[2]);
      TooltipDecor.currentBorderStart = borderStart;
      TooltipDecor.currentBorderEnd = borderEnd;
      TooltipDecor.currentBackgroundStart = bgStart;
      TooltipDecor.currentBackgroundEnd = bgEnd;
      event.setBorderStart(borderStart);
      event.setBorderEnd(borderEnd);
      event.setBackgroundStart(bgStart);
      event.setBackgroundEnd(bgEnd);
   }

   private static Integer getLegacyFormattingColor(Component component) {
      String rawText = component.getString();

      for (int i = 0; i < rawText.length() - 1; i++) {
         if (rawText.charAt(i) == 167) {
            ChatFormatting format = ChatFormatting.getByCode(rawText.charAt(i + 1));
            if (format != null && format.isColor() && format.getColor() != null) {
               return format.getColor();
            }
         }
      }

      return null;
   }
}
