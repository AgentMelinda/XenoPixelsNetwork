package com.dragonminez.common.init.item.entities;

import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.entities.FlyingNimbusEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class FlyingNimbusItem extends Item {
   public FlyingNimbusItem() {
      super(new Properties().stacksTo(1));
   }

   public InteractionResult useOn(UseOnContext pContext) {
      Player player = pContext.getPlayer();
      Level level = pContext.getLevel();
      BlockPos pos = pContext.getClickedPos();
      Direction direction = pContext.getClickedFace();
      BlockPos spawnPos = pos.above();
      if (player != null && level != null) {
         FlyingNimbusEntity nube = new FlyingNimbusEntity((EntityType<? extends Mob>)MainEntities.FLYING_NIMBUS.get(), level);
         nube.setPos((double)spawnPos.getX(), (double)spawnPos.getY(), (double)spawnPos.getZ());
         level.addFreshEntity(nube);
         pContext.getItemInHand().shrink(1);
         return InteractionResult.sidedSuccess(level.isClientSide);
      } else {
         return super.useOn(pContext);
      }
   }

   public void appendHoverText(@NotNull ItemStack pStack, @NotNull TooltipContext context, List<Component> pTooltipComponents, @NotNull TooltipFlag pIsAdvanced) {
      pTooltipComponents.add(Component.translatable("item.dragonminez.flying_nimbus.tooltip").withStyle(ChatFormatting.GRAY));
   }
}
