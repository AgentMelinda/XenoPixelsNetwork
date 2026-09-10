package com.dragonminez.common.init.item.weapons;

import com.dragonminez.common.init.item.tools.ToolTiers;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import org.jetbrains.annotations.NotNull;

public class WeaponItem extends SwordItem {
   private final String tag;
   private final int enchantability;

   public WeaponItem(int damageBase, float attackSpeed, int durability, int enchantability, String tag) {
      super(
         ToolTiers.BLANK_WEAPON_TIER,
         new Properties().durability(durability).fireResistant().attributes(SwordItem.createAttributes(ToolTiers.BLANK_WEAPON_TIER, damageBase, attackSpeed))
      );
      this.tag = tag;
      this.enchantability = enchantability;
   }

   public int getEnchantmentValue() {
      return this.enchantability;
   }

   public boolean isEnchantable(@NotNull ItemStack pStack) {
      return true;
   }

   @NotNull
   public Component getName(@NotNull ItemStack pStack) {
      return Component.translatable("item.dragonminez." + this.tag);
   }

   public void appendHoverText(
      @NotNull ItemStack pStack, @NotNull TooltipContext context, @NotNull List<Component> pTooltipComponents, @NotNull TooltipFlag pIsAdvanced
   ) {
      pTooltipComponents.add(Component.translatable("item.dragonminez." + this.tag + ".tooltip").withStyle(ChatFormatting.GRAY));
   }
}
