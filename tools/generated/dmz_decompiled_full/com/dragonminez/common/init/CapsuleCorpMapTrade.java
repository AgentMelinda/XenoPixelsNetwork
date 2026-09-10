package com.dragonminez.common.init;

import com.dragonminez.server.world.structure.helper.StructureLocator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerTrades.ItemListing;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.component.MapDecorations.Entry;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

public class CapsuleCorpMapTrade implements ItemListing {
   public static final String STRUCTURE_TAG = "dmz_structure";
   private final ItemStack priceA;
   private final ItemStack priceB;
   private final ResourceKey<Structure> destination;
   private final String displayName;
   private final Holder<MapDecorationType> destinationType;
   private final int maxUses;
   private final int villagerXp;

   public CapsuleCorpMapTrade(
      ItemStack priceA,
      ItemStack priceB,
      ResourceKey<Structure> destination,
      String displayName,
      Holder<MapDecorationType> destinationType,
      int maxUses,
      int villagerXp
   ) {
      this.priceA = priceA;
      this.priceB = priceB;
      this.destination = destination;
      this.displayName = displayName;
      this.destinationType = destinationType;
      this.maxUses = maxUses;
      this.villagerXp = villagerXp;
   }

   @Nullable
   public MerchantOffer getOffer(Entity trader, RandomSource random) {
      if (trader.level() instanceof ServerLevel serverLevel) {
         if (!serverLevel.getServer().isSameThread()) {
            return null;
         } else {
            BlockPos pos = StructureLocator.locateStructure(serverLevel, this.destination, trader.blockPosition());
            if (pos == null) {
               return null;
            } else {
               ItemStack map = createMapStack(serverLevel, pos, this.destination, this.displayName, this.destinationType);
               ItemCost costA = toCost(this.priceA);
               Optional<ItemCost> costB = this.priceB.isEmpty() ? Optional.empty() : Optional.of(toCost(this.priceB));
               return new MerchantOffer(costA, costB, map, this.maxUses, this.villagerXp, 0.2F);
            }
         }
      } else {
         return null;
      }
   }

   private static ItemCost toCost(ItemStack stack) {
      return new ItemCost(stack.getItem().builtInRegistryHolder(), stack.getCount(), DataComponentPredicate.EMPTY);
   }

   private static ItemStack createMapStack(
      ServerLevel level, BlockPos pos, ResourceKey<Structure> destination, String displayName, Holder<MapDecorationType> destinationType
   ) {
      ItemStack map = MapItem.create(level, pos.getX(), pos.getZ(), (byte)2, true, true);
      MapItem.renderBiomePreviewMap(level, map);
      MapItemSavedData.addTargetDecoration(map, pos, "+", destinationType);
      map.set(DataComponents.CUSTOM_NAME, Component.translatable("filled_map." + displayName));
      CustomData.update(DataComponents.CUSTOM_DATA, map, tag -> tag.putString("dmz_structure", destination.location().toString()));
      return map;
   }

   public static void refreshStaleMapOffers(ServerLevel level, AbstractVillager merchant) {
      MerchantOffers offers = merchant.getOffers();

      for (int i = 0; i < offers.size(); i++) {
         MerchantOffer offer = (MerchantOffer)offers.get(i);
         ItemStack result = offer.getResult();
         if (result.is(Items.FILLED_MAP)) {
            ResourceKey<Structure> key = structureKeyFrom(level, result);
            if (key != null) {
               BlockPos pos = StructureLocator.locateStructure(level, key, merchant.blockPosition());
               if (pos != null && !matchesTarget(result, pos)) {
                  ItemStack fresh = createMapStack(level, pos, key, "dragonminez." + key.location().getPath(), MapDecorationTypes.RED_X);
                  offers.set(
                     i,
                     new MerchantOffer(
                        offer.getItemCostA(),
                        offer.getItemCostB(),
                        fresh,
                        offer.getUses(),
                        offer.getMaxUses(),
                        offer.getXp(),
                        offer.getPriceMultiplier(),
                        offer.getDemand()
                     )
                  );
               }
            }
         }
      }
   }

   @Nullable
   private static ResourceKey<Structure> structureKeyFrom(ServerLevel level, ItemStack map) {
      ResourceLocation id = null;
      CustomData custom = (CustomData)map.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
      if (custom.contains("dmz_structure")) {
         id = ResourceLocation.tryParse(custom.copyTag().getString("dmz_structure"));
      } else if (map.getHoverName().getContents() instanceof TranslatableContents translatable) {
         String prefix = "filled_map.dragonminez.";
         String translationKey = translatable.getKey();
         if (translationKey.startsWith(prefix)) {
            id = ResourceLocation.fromNamespaceAndPath("dragonminez", translationKey.substring(prefix.length()));
         }
      }

      if (id == null) {
         return null;
      } else {
         return !level.registryAccess().registryOrThrow(Registries.STRUCTURE).containsKey(id) ? null : ResourceKey.create(Registries.STRUCTURE, id);
      }
   }

   private static boolean matchesTarget(ItemStack map, BlockPos pos) {
      MapDecorations decorations = (MapDecorations)map.get(DataComponents.MAP_DECORATIONS);
      if (decorations == null) {
         return false;
      } else {
         Entry entry = (Entry)decorations.decorations().get("+");
         return entry == null ? false : (int)entry.x() == pos.getX() && (int)entry.z() == pos.getZ();
      }
   }
}
