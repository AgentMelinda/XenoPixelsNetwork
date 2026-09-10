package com.dragonminez.common.init.item.tools;

import com.dragonminez.common.init.MainItems;
import com.dragonminez.common.init.MainTags;
import com.dragonminez.common.init.item.weapons.BlankWeaponTier;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.SimpleTier;

public class ToolTiers {
   public static final Tier BLANK_WEAPON_TIER = new BlankWeaponTier(0, 0.0F, -1.0F, 0, 15, Ingredient.EMPTY);
   public static final Tier GETE_TIER = new SimpleTier(
      MainTags.Blocks.NEEDS_GETE_TOOL, 3250, 12.0F, 6.0F, 25, () -> Ingredient.of(new ItemLike[]{(ItemLike)MainItems.GETE_INGOT.get()})
   );
}
