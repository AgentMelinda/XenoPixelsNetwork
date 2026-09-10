package com.dragonminez.common.loot;

import com.dragonminez.common.init.MainItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;

public class AddMusicDiscModifier extends LootModifier {
   public static final MapCodec<AddMusicDiscModifier> CODEC = RecordCodecBuilder.mapCodec(
      inst -> codecStart(inst).and(Codec.FLOAT.fieldOf("chance").forGetter(m -> m.chance)).apply(inst, AddMusicDiscModifier::new)
   );
   private final float chance;

   protected AddMusicDiscModifier(LootItemCondition[] conditions, float chance) {
      super(conditions);
      this.chance = chance;
   }

   protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
      ResourceLocation tableId = context.getQueriedLootTableId();
      if (tableId == null || !tableId.getPath().startsWith("chests/")) {
         return loot;
      } else if (MainItems.MUSIC_DISCS.isEmpty()) {
         return loot;
      } else {
         boolean hasVanillaDisc = false;
         ObjectListIterator disc = loot.iterator();

         while (disc.hasNext()) {
            ItemStack stack = (ItemStack)disc.next();
            if (stack.has(DataComponents.JUKEBOX_PLAYABLE)) {
               hasVanillaDisc = true;
               break;
            }
         }

         if (!hasVanillaDisc) {
            return loot;
         } else if (context.getRandom().nextFloat() >= this.chance) {
            return loot;
         } else {
            DeferredHolder<Item, ? extends Item> discx = MainItems.MUSIC_DISCS.get(context.getRandom().nextInt(MainItems.MUSIC_DISCS.size()));
            loot.add(new ItemStack((ItemLike)discx.get()));
            return loot;
         }
      }
   }

   public MapCodec<? extends IGlobalLootModifier> codec() {
      return CODEC;
   }
}
