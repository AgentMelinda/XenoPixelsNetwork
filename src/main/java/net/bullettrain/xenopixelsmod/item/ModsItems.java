package net.bullettrain.xenopixelsmod.item;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.item.custom.MetalDetectorItem;
import net.bullettrain.xenopixelsmod.item.custom.PanelConfiguratorItem;
import net.bullettrain.xenopixelsmod.item.custom.StrawBerrySenzuItem;
import net.bullettrain.xenopixelsmod.item.custom.TargetToolItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModsItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, XenoPixelsMod.MOD_ID);

    public static final DeferredHolder<Item, Item> SAPPHIRE = ITEMS.register("sapphire",
            () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> RAW_SAPPHIRE = ITEMS.register("raw_sapphire",
            () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> METAL_DETECTOR = ITEMS.register("metal_detector",
            () -> new MetalDetectorItem(new Item.Properties().durability(100)));

    public static final DeferredHolder<Item, Item> STRAWBERRY_SENZU = ITEMS.register("strawberry_senzu",
            () -> new StrawBerrySenzuItem(new Item.Properties()
                    .food(ModFoods.STRAWBERRY_SENZU)
                    .stacksTo(16)));

    /** Opens the XYZ Target GUI. Used for VS2 ship teleport / waypoint targeting. */
    public static final DeferredHolder<Item, Item> TARGET_TOOL = ITEMS.register("target_tool",
            () -> new TargetToolItem(new Item.Properties().stacksTo(1)));

    /** Cycles a wing panel's role (flap/pitch/roll/yaw/brake). Works while standing. */
    public static final DeferredHolder<Item, Item> PANEL_CONFIGURATOR = ITEMS.register("panel_configurator",
            () -> new PanelConfiguratorItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
