package com.dragonminez.common.network.C2S;

import com.dragonminez.compat.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

public class DamageCurioC2S {
   private final String slotId;
   private final int slotIndex;
   private final int damageAmount;

   public DamageCurioC2S(String slotId, int slotIndex, int damageAmount) {
      this.slotId = slotId == null ? "" : slotId.trim();
      this.slotIndex = slotIndex;
      this.damageAmount = damageAmount;
   }

   public DamageCurioC2S(FriendlyByteBuf buf) {
      this.slotId = buf.readUtf(256);
      this.slotIndex = buf.readInt();
      this.damageAmount = buf.readInt();
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeUtf(this.slotId, 256);
      buf.writeInt(this.slotIndex);
      buf.writeInt(this.damageAmount);
   }

   public boolean handle(Supplier<NetworkEvent.Context> supplier) {
      NetworkEvent.Context context = supplier.get();
      context.enqueueWork(() -> {
         ServerPlayer player = context.getSender();
         if (player != null && !this.slotId.isEmpty()) {
            if (this.damageAmount > 0) {
               CuriosApi.getCuriosInventory(player).ifPresent(inv -> {
                  ICurioStacksHandler handler = (ICurioStacksHandler)inv.getCurios().get(this.slotId);
                  if (handler != null) {
                     if (this.slotIndex < 0 || this.slotIndex >= handler.getStacks().getSlots()) {
                        return;
                     }

                     ItemStack stack = handler.getStacks().getStackInSlot(this.slotIndex);
                     if (!stack.isEmpty() && stack.isDamageableItem()) {
                        stack.hurtAndBreak(this.damageAmount, player, EquipmentSlot.HEAD);
                        if (stack.isEmpty() || stack.getDamageValue() >= stack.getMaxDamage()) {
                           handler.getStacks().setStackInSlot(this.slotIndex, ItemStack.EMPTY);
                        }
                     }
                  }
               });
            }
         }
      });
      context.setPacketHandled(true);
      return true;
   }
}
