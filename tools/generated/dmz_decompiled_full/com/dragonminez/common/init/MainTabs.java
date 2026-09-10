package com.dragonminez.common.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MainTabs {
   public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS_REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "dragonminez");
   public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BLOQUES_TAB = CREATIVE_TABS_REGISTER.register(
      "dragonminez_blocks_tab",
      () -> CreativeModeTab.builder()
            .icon(() -> new ItemStack((ItemLike)MainBlocks.DBALL4_BLOCK.get()))
            .title(Component.translatable("itemGroup.dragonminez.blocks"))
            .displayItems(
               (parameters, output) -> MainBlocks.BLOCK_REGISTER
                     .getEntries()
                     .forEach(
                        block -> {
                           if (!block.getId().getPath().startsWith("namek_")
                              && !block.getId().getPath().startsWith("sacred_")
                              && !block.getId().getPath().endsWith("_flower")
                              && !block.getId().getPath().startsWith("potted_")
                              && !block.getId().getPath().contains("gete")
                              && !(block.get() instanceof LiquidBlock)
                              && !block.getId().getPath().contains("invisible")) {
                              output.accept(((Block)block.get()).asItem());
                           }
                        }
                     )
            )
            .build()
   );
   public static final DeferredHolder<CreativeModeTab, CreativeModeTab> NAMEK_TAB = CREATIVE_TABS_REGISTER.register(
      "dragonminez_namek_tab",
      () -> CreativeModeTab.builder()
            .icon(() -> new ItemStack((ItemLike)MainBlocks.NAMEK_GRASS_BLOCK.get()))
            .title(Component.translatable("itemGroup.dragonminez.namek"))
            .displayItems(
               (parameters, output) -> MainItems.ITEM_REGISTER
                     .getEntries()
                     .forEach(
                        item -> {
                           if (!item.getId().getPath().contains("bucket")) {
                              if (item.getId().getPath().startsWith("namek_") && !item.getId().getPath().contains("spawn_egg")) {
                                 output.accept(((Item)item.get()).asItem());
                              }

                              if (item.getId().getPath().startsWith("sacred_")
                                 && !item.getId().getPath().contains("sacred_planet")
                                 && !item.getId().getPath().endsWith("_flower")) {
                                 output.accept(((Item)item.get()).asItem());
                              }

                              if (item.getId().getPath().endsWith("_flower")
                                 && !item.getId().getPath().startsWith("potted_")
                                 && !item.getId().getPath().contains("lotus")) {
                                 output.accept(((Item)item.get()).asItem());
                              }
                           }
                        }
                     )
            )
            .build()
   );
   public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ITEMS_TAB = CREATIVE_TABS_REGISTER.register(
      "dragonminez_items_tab",
      () -> CreativeModeTab.builder()
            .icon(() -> new ItemStack((ItemLike)MainItems.POTHALA_RIGHT.get()))
            .title(Component.translatable("itemGroup.dragonminez.items"))
            .displayItems((parameters, output) -> MainItems.ITEM_REGISTER.getEntries().forEach(item -> {
                  if (!(item.get() instanceof BlockItem) && !item.getId().getPath().contains("_armor_")) {
                     output.accept(((Item)item.get()).asItem());
                  }
               }))
            .build()
   );
   public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ARMORS_TAB = CREATIVE_TABS_REGISTER.register(
      "dragonminez_armors_tab",
      () -> CreativeModeTab.builder()
            .icon(() -> new ItemStack((ItemLike)MainItems.BARDOCK_DBZ_ARMOR.get(Type.CHESTPLATE).get()))
            .title(Component.translatable("itemGroup.dragonminez.armors"))
            .displayItems((parameters, output) -> MainItems.ITEM_REGISTER.getEntries().forEach(item -> {
                  if (item.getId().getPath().contains("_armor_") && !item.getId().getPath().equals("kikono_armor_station")) {
                     output.accept(((Item)item.get()).asItem());
                  }
               }))
            .build()
   );
   public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ORES_TAB = CREATIVE_TABS_REGISTER.register(
      "dragonminez_ores_tab",
      () -> CreativeModeTab.builder()
            .icon(() -> new ItemStack((ItemLike)MainBlocks.GETE_ORE.get()))
            .title(Component.translatable("itemGroup.dragonminez.ores"))
            .displayItems((parameters, output) -> MainItems.ITEM_REGISTER.getEntries().forEach(item -> {
                  if (item.getId().getPath().contains("_ore")) {
                     output.accept((ItemLike)item.get());
                  }

                  if (item.getId().getPath().contains("gete") && !item.getId().getPath().contains("furnace")) {
                     output.accept((ItemLike)item.get());
                  }

                  if (item.getId().getPath().contains("kikono")) {
                     output.accept((ItemLike)item.get());
                  }
               }))
            .build()
   );

   public static void register(IEventBus bus) {
      CREATIVE_TABS_REGISTER.register(bus);
   }
}
