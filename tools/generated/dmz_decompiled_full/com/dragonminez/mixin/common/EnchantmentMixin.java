package com.dragonminez.mixin.common;

import com.dragonminez.common.combat.logic.player.PlayerAttackHelper;
import com.dragonminez.common.combat.logic.player.PlayerAttackProperties;
import com.dragonminez.common.combat.player.AttackHand;
import java.util.Map;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Enchantment.class})
public class EnchantmentMixin {
   @Inject(
      method = {"getSlotItems"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void dragonminez$getEquipmentFix(LivingEntity entity, CallbackInfoReturnable<Map<EquipmentSlot, ItemStack>> cir) {
      if (entity instanceof Player player) {
         int comboCount = ((PlayerAttackProperties)player).getComboCount();
         AttackHand currentHand = PlayerAttackHelper.getCurrentAttack(player, comboCount);
         if (currentHand != null && currentHand.isOffHand()) {
            Map<EquipmentSlot, ItemStack> map = (Map<EquipmentSlot, ItemStack>)cir.getReturnValue();
            if (map.get(EquipmentSlot.MAINHAND) != null) {
               map.remove(EquipmentSlot.MAINHAND);
            }

            ItemStack offHandStack = player.getOffhandItem();
            if (!offHandStack.isEmpty()) {
               map.put(EquipmentSlot.OFFHAND, offHandStack);
            }

            cir.setReturnValue(map);
         }
      }
   }
}
