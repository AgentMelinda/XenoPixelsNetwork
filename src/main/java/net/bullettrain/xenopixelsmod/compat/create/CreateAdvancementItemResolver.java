package net.bullettrain.xenopixelsmod.compat.create;

import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Resolves Create advancement items only after the item registry is bound. */
public final class CreateAdvancementItemResolver {
    private CreateAdvancementItemResolver() {
    }

    public static ItemStack stack(HolderLookup.Provider registries, ResourceLocation id) {
        return new ItemStack(item(registries, id));
    }

    public static Criterion<?> collected(HolderLookup.Provider registries, ResourceLocation id) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(item(registries, id));
    }

    private static Item item(HolderLookup.Provider registries, ResourceLocation id) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return registries.lookupOrThrow(Registries.ITEM)
                .get(key)
                .orElseThrow(() -> new IllegalStateException(
                        "Create advancement item is not registered: " + id))
                .value();
    }
}
