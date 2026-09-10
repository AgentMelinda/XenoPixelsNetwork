package com.dragonminez.common.init.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public class DMZCuriosItem extends Item implements ICurioItem {
   private final DMZCuriosItem.CurioType curioType;

   public DMZCuriosItem(Properties properties, DMZCuriosItem.CurioType curioType) {
      super(properties);
      this.curioType = curioType;
   }

   public DMZCuriosItem.CurioType getCurioType() {
      return this.curioType;
   }

   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      String id = stack.getItem().getDescriptionId();
      if (id.contains("pothala_right")) {
         tooltip.add(Component.translatable("item.dragonminez.pothala.right.tooltip").withStyle(ChatFormatting.GRAY));
      } else if (id.contains("pothala_left")) {
         tooltip.add(Component.translatable("item.dragonminez.pothala.left.tooltip").withStyle(ChatFormatting.GRAY));
      }

      PothalaPairItem.appendPairIdTooltip(stack, tooltip);
      super.appendHoverText(stack, context, tooltip, flag);
   }

   public static enum CurioType {
      HEAD_TECH,
      WEIGHTS;
   }
}
