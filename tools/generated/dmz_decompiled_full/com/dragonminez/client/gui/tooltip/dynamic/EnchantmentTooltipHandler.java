package com.dragonminez.client.gui.tooltip.dynamic;

import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

public class EnchantmentTooltipHandler {
   private static final String[] KEY_TYPES = new String[]{"desc", "description", "info"};
   private static final int ENCHANT_DESC_COLOR = 8947848;

   public static void insertDescription(Enchantment enchantment, int level, Consumer<Component> lines) {
      insertDescriptionById(null, level, lines);
   }

   public static void insertDescription(Holder<Enchantment> holder, int level, Consumer<Component> lines) {
      ResourceLocation id = holder.unwrapKey().map(k -> k.location()).orElse(null);
      insertDescriptionById(id, level, lines);
   }

   public static void insertDescriptionById(ResourceLocation id, int level, Consumer<Component> lines) {
      if (id != null) {
         Component description = getDescription(id, level);
         if (description != null) {
            Style descriptionStyle = Style.EMPTY.withColor(8947848).withItalic(true);
            MutableComponent styledDescription = description.copy().withStyle(descriptionStyle);
            lines.accept(Component.literal(" ").append(styledDescription));
         }
      }
   }

   private static Component getDescription(ResourceLocation id, int level) {
      String baseKey = "enchantment." + id.getNamespace() + "." + id.getPath() + ".";
      Language lang = Language.getInstance();

      for (String keyType : KEY_TYPES) {
         String key = baseKey + keyType;
         if (lang.has(key)) {
            return Component.translatable(key);
         }

         key = key + "." + level;
         if (lang.has(key)) {
            return Component.translatable(key);
         }
      }

      return null;
   }
}
