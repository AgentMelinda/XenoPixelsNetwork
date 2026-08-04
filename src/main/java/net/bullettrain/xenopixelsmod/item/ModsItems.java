package net.bullettrain.xenopixelsmod.item;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.item.custom.MetalDetectorItem;
import net.bullettrain.xenopixelsmod.item.custom.StrawBerrySenzuItem;
import net.bullettrain.xenopixelsmod.item.custom.TargetToolItem;
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
                    .food(ModFoods.STRAWBERRY_SENZU)
                    .stacksTo(16)));

    /** Opens the XYZ Target GUI. Used for VS2 ship teleport / waypoint targeting. */
    public static final RegistryObject<Item> TARGET_TOOL = ITEMS.register("target_tool",
            () -> new TargetToolItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
