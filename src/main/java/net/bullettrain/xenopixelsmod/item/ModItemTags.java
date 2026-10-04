package net.bullettrain.xenopixelsmod.item;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModItemTags {
    public static final TagKey<Item> MISSILE_WARHEADS = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "missile_warheads"));
    public static final TagKey<Item> MISSILE_BODIES = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "missile_bodies"));

    private ModItemTags() {
    }
}
