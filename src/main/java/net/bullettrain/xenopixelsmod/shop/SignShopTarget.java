package net.bullettrain.xenopixelsmod.shop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;

/**
 * Resolves a sign-shop target to the item a buyer actually receives.
 *
 * <p>A target is a registry id, never a display name, so modded content needs no extra syntax.
 * Resolution order is ITEM first, then BLOCK via the block's own item form. Both registries are
 * {@code DefaultedRegistry} on the pinned NeoForge build, and {@code Registry.containsKey} is the
 * lookup guard that keeps an unknown id from resolving to the default entry.</p>
 *
 * <p>Examples that resolve: {@code minecraft:stone} (block), {@code minecraft:diamond} (item),
 * {@code xenopixelsmod:<any registered id>}, any third-party modded id.</p>
 */
public final class SignShopTarget {

    private SignShopTarget() {
    }

    /**
     * @return the item a buyer receives, or {@code null} when the id names no item and no block
     *         with an item form. Callers treat {@code null} as "this sign is not a valid shop".
     */
    @Nullable
    public static Item resolve(@Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            Item item = BuiltInRegistries.ITEM.get(id);
            return item == Items.AIR ? null : item;
        }
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            // Blocks with no item form (technical/fluid blocks) report AIR rather than a real item.
            Item item = block.asItem();
            return item == Items.AIR ? null : item;
        }
        return null;
    }

    /** True when {@code id} resolves to a tradable item. */
    public static boolean isResolvable(@Nullable ResourceLocation id) {
        return resolve(id) != null;
    }
}