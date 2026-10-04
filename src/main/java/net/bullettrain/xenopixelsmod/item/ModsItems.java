package net.bullettrain.xenopixelsmod.item;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.item.custom.MetalDetectorItem;
import net.bullettrain.xenopixelsmod.item.custom.PanelConfiguratorItem;
import net.bullettrain.xenopixelsmod.item.custom.StrawBerrySenzuItem;
import net.bullettrain.xenopixelsmod.item.custom.TargetToolItem;
import net.bullettrain.xenopixelsmod.item.custom.ZeniCashItem;
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

    public static final DeferredHolder<Item, Item> TARGET_TOOL_FORK = ITEMS.register("target_tool_fork",
            () -> new TargetToolItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, Item> MISSILE_RADAR = ITEMS.register("missile_radar",
            () -> new net.bullettrain.xenopixelsmod.item.custom.MissileRadarItem(
                    new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, Item> PANEL_CONFIGURATOR_FORK = ITEMS.register("panel_configurator_fork",
            () -> new net.bullettrain.xenopixelsmod.item.custom.PanelConfiguratorForkItem(
                    new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, Item> XENO_NPC_WAND = ITEMS.register("xeno_npc_wand",
            () -> new net.bullettrain.xenopixelsmod.item.custom.XenoNpcWandItem(
                    new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, Item> XENO_NPC_PATH_TOOL = ITEMS.register("xeno_npc_path_tool",
            () -> new net.bullettrain.xenopixelsmod.item.custom.XenoNpcPathToolItem(
                    new Item.Properties().stacksTo(1)));

    /** My NPCs' Mob Cloner: copy a configured NPC and place it again. */
    public static final DeferredHolder<Item, Item> XENO_NPC_CLONER = ITEMS.register("xeno_npc_cloner",
            () -> new net.bullettrain.xenopixelsmod.item.custom.XenoNpcClonerItem(
                    // One at a time: it carries a copied NPC, and a stack of sixty-four would each
                    // have to carry their own, which is not what anybody means by stacking a tool.
                    new Item.Properties().stacksTo(1)));

    /** My NPCs' NPC Jar: take an NPC out of the world and put it back somewhere else. */
    public static final DeferredHolder<Item, Item> XENO_NPC_JAR = ITEMS.register("xeno_npc_jar",
            () -> new net.bullettrain.xenopixelsmod.item.custom.XenoNpcJarItem(
                    new Item.Properties().stacksTo(1)));

    /** My NPCs' Mounter: sit an NPC on a mob, a boat, or on you. */
    public static final DeferredHolder<Item, Item> XENO_NPC_MOUNTER = ITEMS.register("xeno_npc_mounter",
            () -> new net.bullettrain.xenopixelsmod.item.custom.XenoNpcMounterItem(
                    new Item.Properties().stacksTo(1)));

    /** My NPCs' Teleporter: move an NPC, including across dimensions. */
    public static final DeferredHolder<Item, Item> XENO_NPC_TELEPORTER = ITEMS.register("xeno_npc_teleporter",
            () -> new net.bullettrain.xenopixelsmod.item.custom.XenoNpcTeleporterItem(
                    new Item.Properties().stacksTo(1)));

    /** Scripting tool: opens the My NPCs-style script screen bound to the NPC it is used on. */
    public static final DeferredHolder<Item, Item> XENO_NPC_SCRIPT_TOOL = ITEMS.register("xeno_npc_script_tool",
            () -> new net.bullettrain.xenopixelsmod.item.custom.XenoNpcScriptToolItem(
                    new Item.Properties().stacksTo(1)));

    private static DeferredHolder<Item, Item> zeni(String id, long value) {
        return ITEMS.register(id, () -> new ZeniCashItem(new Item.Properties().stacksTo(64), value));
    }

    public static final DeferredHolder<Item, Item> ZENI_1 = zeni("zeni_1", 1);
    public static final DeferredHolder<Item, Item> ZENI_10 = zeni("zeni_10", 10);
    public static final DeferredHolder<Item, Item> ZENI_25 = zeni("zeni_25", 25);
    public static final DeferredHolder<Item, Item> ZENI_50 = zeni("zeni_50", 50);
    public static final DeferredHolder<Item, Item> ZENI_100 = zeni("zeni_100", 100);
    public static final DeferredHolder<Item, Item> ZENI_200 = zeni("zeni_200", 200);
    public static final DeferredHolder<Item, Item> ZENI_250 = zeni("zeni_250", 250);
    public static final DeferredHolder<Item, Item> ZENI_500 = zeni("zeni_500", 500);
    public static final DeferredHolder<Item, Item> ZENI_1000 = zeni("zeni_1000", 1_000);
    public static final DeferredHolder<Item, Item> ZENI_NOTE_10000 = zeni("zeni_note_10000", 10_000);
    public static final DeferredHolder<Item, Item> ZENI_NOTE_100000 = zeni("zeni_note_100000", 100_000);
    public static final DeferredHolder<Item, Item> ZENI_NOTE_1000000 = zeni("zeni_note_1000000", 1_000_000);

    public static final DeferredHolder<Item, Item> SUPER_SOUL_WARRIOR = ITEMS.register("super_soul_warrior",
            () -> new net.bullettrain.xenopixelsmod.item.custom.SuperSoulItem(new Item.Properties(), "warrior"));
    public static final DeferredHolder<Item, Item> SUPER_SOUL_IRON = ITEMS.register("super_soul_iron",
            () -> new net.bullettrain.xenopixelsmod.item.custom.SuperSoulItem(new Item.Properties(), "iron"));
    public static final DeferredHolder<Item, Item> SUPER_SOUL_SPARK = ITEMS.register("super_soul_spark",
            () -> new net.bullettrain.xenopixelsmod.item.custom.SuperSoulItem(new Item.Properties(), "spark"));
    public static final DeferredHolder<Item, Item> SUPER_SOUL_FINISHER = ITEMS.register("super_soul_finisher",
            () -> new net.bullettrain.xenopixelsmod.item.custom.SuperSoulItem(new Item.Properties(), "finisher"));
    public static final DeferredHolder<Item, Item> SUPER_SOUL_BALANCED = ITEMS.register("super_soul_balanced",
            () -> new net.bullettrain.xenopixelsmod.item.custom.SuperSoulItem(new Item.Properties(), "balanced"));

    public static final DeferredHolder<Item, Item> MISSILE_SMALL = ITEMS.register("missile_small",
            () -> new net.bullettrain.xenopixelsmod.item.custom.MissileItem(
                    new Item.Properties(), net.bullettrain.xenopixelsmod.missile.MissileSize.SMALL));
    public static final DeferredHolder<Item, Item> MISSILE_MEDIUM = ITEMS.register("missile_medium",
            () -> new net.bullettrain.xenopixelsmod.item.custom.MissileItem(
                    new Item.Properties(), net.bullettrain.xenopixelsmod.missile.MissileSize.MEDIUM));
    public static final DeferredHolder<Item, Item> MISSILE_LARGE = ITEMS.register("missile_large",
            () -> new net.bullettrain.xenopixelsmod.item.custom.MissileItem(
                    new Item.Properties(), net.bullettrain.xenopixelsmod.missile.MissileSize.LARGE));
    public static final DeferredHolder<Item, Item> MISSILE_MEGA = ITEMS.register("missile_mega",
            () -> new net.bullettrain.xenopixelsmod.item.custom.MissileItem(
                    new Item.Properties(), net.bullettrain.xenopixelsmod.missile.MissileSize.MEGA));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
