package com.dragonminez.common.init.armor;

import com.dragonminez.client.util.ArmorTextureResolver;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.ArmorMaterial.Layer;
import net.minecraft.world.item.Item.Properties;
import org.jetbrains.annotations.Nullable;

public class DbzArmorItem extends ArmorItem implements DbzArmorTextured {
   private final String modId;
   private final String itemId;

   public DbzArmorItem(Holder<ArmorMaterial> pMaterial, Type pType, Properties pProperties, String itemId) {
      this(pMaterial, pType, pProperties, "dragonminez", itemId);
   }

   public DbzArmorItem(Holder<ArmorMaterial> pMaterial, Type pType, Properties pProperties, String modId, String itemId) {
      super(pMaterial, pType, pProperties);
      this.modId = modId;
      this.itemId = itemId;
   }

   @Nullable
   public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, Layer layer, boolean innerModel) {
      return ArmorTextureResolver.resolve(this.modId, this.itemId, slot, stack);
   }

   public String getModId() {
      return this.modId;
   }

   @Override
   public String getItemId() {
      return this.itemId;
   }
}
