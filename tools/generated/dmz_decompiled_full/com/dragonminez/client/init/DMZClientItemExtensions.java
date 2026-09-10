package com.dragonminez.client.init;

import com.dragonminez.common.init.MainItems;
import com.dragonminez.common.init.armor.DbzArmorCapeItem;
import com.dragonminez.common.init.armor.DbzArmorItem;
import com.dragonminez.common.init.armor.client.model.ArmorBaseModel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.NotNull;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.MOD,
   value = {Dist.CLIENT}
)
public final class DMZClientItemExtensions {
   private static final IClientItemExtensions DBZ_ARMOR_EXTENSIONS = new IClientItemExtensions() {
      private ArmorBaseModel model;

      @NotNull
      public HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
         if (this.model == null) {
            this.model = new ArmorBaseModel(Minecraft.getInstance().getEntityModels().bakeLayer(ArmorBaseModel.LAYER_LOCATION));
         }

         return this.model;
      }
   };

   private DMZClientItemExtensions() {
   }

   @SubscribeEvent
   public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
      List<Item> armorItems = new ArrayList<>();

      for (DeferredHolder<Item, ? extends Item> holder : MainItems.ITEM_REGISTER.getEntries()) {
         Item item = (Item)holder.get();
         if (item instanceof DbzArmorItem || item instanceof DbzArmorCapeItem) {
            armorItems.add(item);
         }
      }

      if (!armorItems.isEmpty()) {
         event.registerItem(DBZ_ARMOR_EXTENSIONS, armorItems.toArray(Item[]::new));
      }
   }
}
