package com.dragonminez.common.init.item.consumables;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties.Builder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class MightTreeFruitItem extends Item {
   private static final int HUNGER = 2;
   private static final float SATURATION = 20.0F;
   private static final int EFFECT_DURATION_TICKS = 1200;

   public MightTreeFruitItem() {
      super(new Properties().stacksTo(6).food(new Builder().nutrition(2).saturationModifier(20.0F).alwaysEdible().build()));
   }

   @NotNull
   public Component getName(@NotNull ItemStack pStack) {
      return Component.translatable("item.dragonminez.might_tree_fruit");
   }

   public void appendHoverText(@NotNull ItemStack pStack, @NotNull TooltipContext context, List<Component> pTooltipComponents, @NotNull TooltipFlag pIsAdvanced) {
      pTooltipComponents.add(Component.translatable("item.dragonminez.might_tree_fruit.tooltip").withStyle(ChatFormatting.GRAY));
   }

   public ItemStack finishUsingItem(ItemStack pStack, Level pLevel, LivingEntity pLivingEntity) {
      if (!pLevel.isClientSide && pLivingEntity instanceof ServerPlayer player) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            double effectPower = ConfigManager.getServerConfig().getGameplay().getMightFruitPower();
            data.getEffects().addEffect("mightfruit", effectPower, 1200);
         });
         player.getFoodData().eat(2, 20.0F);
         player.displayClientMessage(Component.translatable("item.dragonminez.might_tree_fruit.use"), true);
         if (player.isCreative()) {
            pStack.shrink(0);
         } else {
            pStack.shrink(1);
         }
      }

      return pStack;
   }
}
