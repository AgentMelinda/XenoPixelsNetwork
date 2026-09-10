package com.dragonminez.common.init.item.weapons;

import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public class MerusLaserItem extends Item {
   public MerusLaserItem() {
      super(new Properties().stacksTo(1).durability(250));
   }

   public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pHand) {
      ItemStack itemstack = pPlayer.getItemInHand(pHand);
      if (!pLevel.isClientSide) {
         KiBlastEntity kiBlast = new KiBlastEntity(pLevel, pPlayer);
         kiBlast.setupKiBlast(pPlayer, 10.0F, 2.5F, 65535, 7929855, 1.0F, 5);
         itemstack.hurtAndBreak(1, pPlayer, pHand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
      }

      pLevel.playSound(null, pPlayer.getX(), pPlayer.getY(), pPlayer.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 2.0F);
      pPlayer.getCooldowns().addCooldown(this, 30);
      return InteractionResultHolder.sidedSuccess(itemstack, pLevel.isClientSide());
   }
}
