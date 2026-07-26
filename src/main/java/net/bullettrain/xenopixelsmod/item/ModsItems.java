package net.bullettrain.xenopixelsmod.item;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.item.custom.MetalDetectorItem;
import net.bullettrain.xenopixelsmod.item.custom.StrawBerrySenzuItem;
import net.bullettrain.xenopixelsmod.item.custom.SuperSoulItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModsItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, XenoPixelsMod.MOD_ID);

    public static final RegistryObject<Item> SAPPHIRE = ITEMS.register("sapphire",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> RAW_SAPPHIRE = ITEMS.register("raw_sapphire",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> METAL_DETECTOR = ITEMS.register("metal_detector",
            () -> new MetalDetectorItem(new Item.Properties().durability(100)));

    public static final RegistryObject<Item> STRAWBERRY_SENZU = ITEMS.register("strawberry_senzu",
            () -> new StrawBerrySenzuItem(new Item.Properties()
                    .food(ModFoods.STRAWBERRY_SENZU) // <-- this is required
                    .stacksTo(16)));

    // --- Super Souls (Phase 3) ---
    public static final RegistryObject<Item> SUPER_SOUL_WARRIOR = ITEMS.register("super_soul_warrior",
            () -> new SuperSoulItem(new Item.Properties(), "warrior"));
    public static final RegistryObject<Item> SUPER_SOUL_IRON = ITEMS.register("super_soul_iron",
            () -> new SuperSoulItem(new Item.Properties(), "iron"));
    public static final RegistryObject<Item> SUPER_SOUL_SPARK = ITEMS.register("super_soul_spark",
            () -> new SuperSoulItem(new Item.Properties(), "spark"));
    public static final RegistryObject<Item> SUPER_SOUL_FINISHER = ITEMS.register("super_soul_finisher",
            () -> new SuperSoulItem(new Item.Properties(), "finisher"));
    public static final RegistryObject<Item> SUPER_SOUL_BALANCED = ITEMS.register("super_soul_balanced",
            () -> new SuperSoulItem(new Item.Properties(), "balanced"));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
