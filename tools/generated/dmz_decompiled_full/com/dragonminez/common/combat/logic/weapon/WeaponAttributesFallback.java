package com.dragonminez.common.combat.logic.weapon;

import com.dragonminez.common.combat.util.PatternMatching;
import com.dragonminez.common.combat.weapon.AttributesContainer;
import com.dragonminez.common.config.CombatConfig;
import com.dragonminez.common.config.ConfigManager;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class WeaponAttributesFallback {
   public static void initialize() {
      CombatConfig config = ConfigManager.getCombatConfig();

      for (ResourceLocation itemId : BuiltInRegistries.ITEM.keySet()) {
         Item item = (Item)BuiltInRegistries.ITEM.get(itemId);
         if (!PatternMatching.matches(itemId.toString(), config.getBlacklistItemIdRegex())) {
            List<CombatConfig.CompatibilitySpecifier> specifiers = null;
            if (hasAttributeModifier(item, Attributes.ATTACK_DAMAGE)) {
               specifiers = config.getFallbackCompatibility();
            }

            if (specifiers != null) {
               for (CombatConfig.CompatibilitySpecifier fallbackOption : specifiers) {
                  if (WeaponRegistry.getAttributes(itemId) == null && PatternMatching.matches(itemId.toString(), fallbackOption.getItem_id_regex())) {
                     AttributesContainer container = WeaponRegistry.containers.get(ResourceLocation.parse(fallbackOption.getWeapon_attributes()));
                     if (container != null) {
                        WeaponRegistry.resolveAndRegisterAttributes(itemId, container);
                        break;
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean hasAttributeModifier(Item item, Holder<Attribute> searchedAttribute) {
      if (item == null) {
         return false;
      } else {
         ItemStack stack = item.getDefaultInstance();
         boolean[] found = new boolean[]{false};
         stack.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.equals(searchedAttribute)) {
               found[0] = true;
            }
         });
         return found[0];
      }
   }
}
