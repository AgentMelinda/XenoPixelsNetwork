package com.dragonminez.compat.common;

import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item.Properties;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

public class ForgeSpawnEggItem extends DeferredSpawnEggItem {
   public ForgeSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor, int highlightColor, Properties properties) {
      super(type, backgroundColor, highlightColor, properties);
   }
}
