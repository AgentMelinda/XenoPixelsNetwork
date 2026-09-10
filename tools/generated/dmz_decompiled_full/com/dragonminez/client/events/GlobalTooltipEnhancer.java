package com.dragonminez.client.events;

import com.dragonminez.client.gui.tooltip.dynamic.AttributeTooltipHandler;
import com.dragonminez.client.gui.tooltip.dynamic.EnchantmentColorHandler;
import com.dragonminez.client.gui.tooltip.dynamic.EnchantmentTooltipHandler;
import com.dragonminez.client.gui.tooltip.dynamic.TooltipPromptHandler;
import com.dragonminez.client.gui.tooltip.dynamic.WeaponRangeTooltipHandler;
import com.dragonminez.common.combat.logic.weapon.WeaponRegistry;
import com.dragonminez.common.combat.weapon.AttributesContainer;
import com.dragonminez.common.combat.weapon.WeaponAttributes;
import com.dragonminez.common.combat.weapon.WeaponAttributesHelper;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.GAME,
   value = {Dist.CLIENT}
)
public class GlobalTooltipEnhancer {
   @SubscribeEvent
   public static void onItemTooltip(ItemTooltipEvent event) {
      ItemStack itemStack = event.getItemStack();
      List<Component> lines = event.getToolTip();
      boolean needsShiftPrompt = false;
      EnchantmentColorHandler.colorizeEnchantmentNames(itemStack, lines);
      ItemEnchantments enchantments = (ItemEnchantments)itemStack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
      if (enchantments.isEmpty()) {
         enchantments = (ItemEnchantments)itemStack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
      }

      if (!enchantments.isEmpty()) {
         boolean isEnchantmentBook = itemStack.getItem() == Items.ENCHANTED_BOOK;
         boolean shouldShowDescriptions = Screen.hasShiftDown() || isEnchantmentBook;
         if (!isEnchantmentBook) {
            needsShiftPrompt = true;
         }

         if (shouldShowDescriptions) {
            for (int i = 0; i < lines.size(); i++) {
               Component line = lines.get(i);
               ComponentContents customAttrPrompt = line.getContents();
               if (customAttrPrompt instanceof TranslatableContents) {
                  TranslatableContents t = (TranslatableContents)customAttrPrompt;
                  if (t.getKey().startsWith("enchantment.")) {
                     for (Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
                        Holder<Enchantment> holder = (Holder<Enchantment>)entry.getKey();
                        String descId = holder.unwrapKey().map(key -> Util.makeDescriptionId("enchantment", key.location())).orElse("");
                        if (t.getKey().equals(descId)) {
                           List<Component> descList = new ArrayList<>();
                           EnchantmentTooltipHandler.insertDescription(holder, entry.getIntValue(), descList::add);
                           if (!descList.isEmpty()) {
                              lines.addAll(i + 1, descList);
                              i += descList.size();
                           }
                           break;
                        }
                     }
                  }
               }
            }
         }
      }

      int attributeInsertIndex = -1;
      List<Integer> linesToRemove = new ArrayList<>();

      for (int ix = 0; ix < lines.size(); ix++) {
         Component line = lines.get(ix);
         if (isVanillaAttributeLine(line)) {
            if (attributeInsertIndex == -1) {
               attributeInsertIndex = ix;
            }

            linesToRemove.add(ix);
         }
      }

      if (attributeInsertIndex == -1) {
         attributeInsertIndex = findAdvancedTooltipStart(lines, itemStack);
      }

      for (int ixx = linesToRemove.size() - 1; ixx >= 0; ixx--) {
         lines.remove(linesToRemove.get(ixx).intValue());
      }

      List<Component> attributeLines = new ArrayList<>();
      boolean attrPrompt = AttributeTooltipHandler.processVanillaAttributes(itemStack, attributeLines::add, event.getEntity());
      if (attrPrompt) {
         needsShiftPrompt = true;
      }

      WeaponAttributes weaponAttrs = WeaponRegistry.getAttributes(itemStack);
      if (weaponAttrs == null) {
         AttributesContainer container = WeaponAttributesHelper.getContainerFromNBT(itemStack);
         if (container != null) {
            weaponAttrs = container.attributes();
         }
      }

      boolean customAttrPrompt = WeaponRangeTooltipHandler.appendWeaponAttributes(weaponAttrs, attributeLines::add);
      if (customAttrPrompt) {
         needsShiftPrompt = true;
      }

      cleanRedundantHeaders(attributeLines);
      if (needsShiftPrompt && !Screen.hasShiftDown()) {
         attributeLines.add(0, TooltipPromptHandler.getExpandPrompt());
      }

      lines.addAll(attributeInsertIndex, attributeLines);
   }

   private static boolean isVanillaAttributeLine(Component line) {
      if (line.getContents() instanceof TranslatableContents t) {
         String key = t.getKey();
         if (key.startsWith("item.modifiers.") || key.startsWith("attribute.modifier.")) {
            return true;
         }
      }

      for (Component sibling : line.getSiblings()) {
         if (sibling.getContents() instanceof TranslatableContents tSibling) {
            String key = tSibling.getKey();
            if (key.startsWith("item.modifiers.") || key.startsWith("attribute.modifier.")) {
               return true;
            }
         }
      }

      return false;
   }

   private static int findAdvancedTooltipStart(List<Component> lines, ItemStack stack) {
      String registryName = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

      for (int i = 0; i < lines.size(); i++) {
         Component line = lines.get(i);
         if (line.getContents() instanceof TranslatableContents t && (t.getKey().equals("item.durability") || t.getKey().equals("item.nbt_tags"))) {
            return i;
         }

         if (line.getString().contains(registryName)) {
            return i;
         }
      }

      return lines.size();
   }

   private static void cleanRedundantHeaders(List<Component> lines) {
      int firstHeaderIndex = -1;
      List<Integer> headersToRemove = new ArrayList<>();

      for (int i = 0; i < lines.size(); i++) {
         ComponentContents var5 = lines.get(i).getContents();
         if (var5 instanceof TranslatableContents) {
            TranslatableContents translatable = (TranslatableContents)var5;
            if (translatable.getKey().startsWith("item.modifiers.")) {
               if (firstHeaderIndex == -1) {
                  firstHeaderIndex = i;
               } else {
                  headersToRemove.add(i);
               }
            }
         }
      }

      for (int ix = headersToRemove.size() - 1; ix >= 0; ix--) {
         lines.remove(headersToRemove.get(ix).intValue());
      }
   }
}
